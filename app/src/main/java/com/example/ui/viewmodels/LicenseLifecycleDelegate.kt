package com.example.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import com.example.security.LicenseClockGuard
import com.example.security.LicenseClockState
import com.example.security.LicenseSecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dedicated delegate managing ECDSA-signed license verification on boot, on resume,
 * and on a periodic runtime schedule. Delegates monotonic `SystemClock.elapsedRealtime()`
 * anti-rollback detection and non-destructive suspension classification to [LicenseClockGuard]
 * in `com.example.security`.
 */
class LicenseLifecycleDelegate(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val languageProvider: () -> String,
    private val trustedKeyRing: Map<String, String> = LicenseSecurityManager.TRUSTED_PUBLIC_KEYS,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val bootCountProvider: () -> Int = { LicenseClockGuard.readSystemBootCount(context) },
    private val onlineVerifier: suspend (String, String, String, Map<String, String>) -> LicenseSecurityManager.LicenseVerificationResult =
        { deviceId, key, url, ring -> LicenseSecurityManager.verifyOnlineSubscription(deviceId, key, url, ring) }
) {
    companion object {
        private const val TAG = "LicenseLifecycle"
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        private const val AUTO_RENEW_THRESHOLD_DAYS = 7L
        const val DEFAULT_PERIODIC_CHECK_INTERVAL_MS = 5L * 60L * 1000L
    }

    private val clockGuard = LicenseClockGuard(
        context = context,
        prefs = prefs,
        elapsedRealtimeProvider = elapsedRealtimeProvider,
        bootCountProvider = bootCountProvider
    )

    private val _isLicenseActivated = MutableStateFlow(false)
    val isLicenseActivated: StateFlow<Boolean> = _isLicenseActivated.asStateFlow()

    private val _isClockRollbackSuspended = MutableStateFlow(false)
    val isClockRollbackSuspended: StateFlow<Boolean> = _isClockRollbackSuspended.asStateFlow()

    private val _isExpirySuspended = MutableStateFlow(false)
    val isExpirySuspended: StateFlow<Boolean> = _isExpirySuspended.asStateFlow()

    private val _licenseKey = MutableStateFlow(prefs.getString("license_number", "") ?: "")
    val licenseKey: StateFlow<String> = _licenseKey.asStateFlow()

    private val _licenseExpires = MutableStateFlow(prefs.getLong("license_expires", 0L))
    val licenseExpires: StateFlow<Long> = _licenseExpires.asStateFlow()

    private val _licenseGraceUntil = MutableStateFlow(prefs.getLong("license_grace_until", 0L))
    val licenseGraceUntil: StateFlow<Long> = _licenseGraceUntil.asStateFlow()

    private val _licenseKeyId = MutableStateFlow(prefs.getString("license_kid", "") ?: "")
    val licenseKeyId: StateFlow<String> = _licenseKeyId.asStateFlow()

    private val _licenseType = MutableStateFlow(prefs.getString("license_type", "STANDARD") ?: "STANDARD")
    val licenseType: StateFlow<String> = _licenseType.asStateFlow()

    private val _needsOnlineRenewal = MutableStateFlow(false)
    val needsOnlineRenewal: StateFlow<Boolean> = _needsOnlineRenewal.asStateFlow()

    private val _licenseError = MutableStateFlow<String?>(null)
    val licenseError: StateFlow<String?> = _licenseError.asStateFlow()

    @Volatile
    private var periodicMonitorJob: Job? = null

    fun getDeviceId(): String = LicenseSecurityManager.getDeviceId(context)

    fun getRemainingLicenseDays(nowMillis: Long = System.currentTimeMillis()): Long {
        val exp = _licenseExpires.value
        if (exp <= 0L) return 0L
        val diff = exp - nowMillis
        return if (diff > 0L) (diff / DAY_MILLIS) + 1L else 0L
    }

    fun getRemainingGraceDays(nowMillis: Long = System.currentTimeMillis()): Long {
        val grace = _licenseGraceUntil.value
        if (grace <= 0L) return 0L
        val diff = grace - nowMillis
        return if (diff > 0L) (diff / DAY_MILLIS) + 1L else 0L
    }

    /**
     * Records a server/cryptographically verified timestamp into [LicenseClockGuard].
     */
    @Synchronized
    fun recordTrustedTime(trustedTimeMillis: Long) {
        clockGuard.recordTrustedTime(trustedTimeMillis)
    }

    private fun suspendForClockRollback(storedToken: String, customMessage: String? = null) {
        prefs.edit().putBoolean("license_activated", false).apply()
        _isLicenseActivated.value = false
        _isClockRollbackSuspended.value = true
        _isExpirySuspended.value = false
        if (storedToken.isNotEmpty()) {
            _licenseKey.value = storedToken
        }
        _licenseError.value = customMessage ?: when (languageProvider().uppercase()) {
            "FR" -> "Horloge système reculée. Corrigez la date et l'heure de l'appareil pour reprendre automatiquement votre licence sans nouveau code."
            "EN" -> "System clock rollback detected. Correct the device date & time to resume your license automatically without a new activation code."
            else -> "تم رصد تراجع في وقت وتاريخ الجهاز. يرجى تصحيح التاريخ والوقت في إعدادات الجهاز وسيُستأنف الترخيص تلقائياً دون الحاجة لرمز تفعيل جديد."
        }
    }

    fun startPeriodicLicenseMonitor(
        intervalMs: Long = DEFAULT_PERIODIC_CHECK_INTERVAL_MS,
        nowProvider: () -> Long = System::currentTimeMillis
    ) {
        if (periodicMonitorJob?.isActive == true) return
        val safeInterval = intervalMs.coerceAtLeast(100L)
        periodicMonitorJob = scope.launch {
            while (isActive) {
                delay(safeInterval)
                reverifyStoredLicenseOnBoot(nowMillis = nowProvider())
                triggerSilentGraceRenewalIfDue()
            }
        }
    }

    fun stopPeriodicLicenseMonitor() {
        periodicMonitorJob?.cancel()
        periodicMonitorJob = null
    }

    fun deactivateLicense() {
        prefs.edit()
            .putBoolean("license_activated", false)
            .remove("license_number")
            .remove("license_type")
            .remove("license_expires")
            .remove("license_grace_until")
            .remove("license_kid")
            .apply()
        _isLicenseActivated.value = false
        _isClockRollbackSuspended.value = false
        _isExpirySuspended.value = false
        _licenseKey.value = ""
        _licenseExpires.value = 0L
        _licenseGraceUntil.value = 0L
        _licenseKeyId.value = ""
        _licenseType.value = "STANDARD"
        _needsOnlineRenewal.value = false
    }

    /**
     * Re-verifies the stored cryptographic token on boot, resume, restart, and periodic checks
     * using [LicenseClockGuard] for monotonic `elapsedRealtime()` rollback detection and
     * non-destructive suspension.
     */
    @Synchronized
    fun reverifyStoredLicenseOnBoot(nowMillis: Long = System.currentTimeMillis()) {
        val storedToken = prefs.getString("license_number", "")?.trim().orEmpty()
        val clockEval = clockGuard.evaluateWallClock(nowMillis = nowMillis)

        if (clockEval.state == LicenseClockState.ROLLBACK_SUSPENDED) {
            Log.w(
                TAG,
                "Clock rollback detected (now=$nowMillis, trusted=${clockEval.trustedWatermarkMillis}, floor=${clockEval.monotonicFloorMillis}, expected=${clockEval.expectedSameBootWallMillis}); suspending without deleting token"
            )
            suspendForClockRollback(storedToken)
            return
        }

        _isClockRollbackSuspended.value = false

        val hadActivatedFlag = prefs.getBoolean("license_activated", false)
        if (storedToken.isEmpty()) {
            if (hadActivatedFlag) {
                Log.w(TAG, "Stored license_activated=true without signed token; revoking unsigned state")
                deactivateLicense()
                _licenseError.value = "يلزم إعادة تفعيل البرنامج برمز رقمي موقّع (ECDSA)"
            } else {
                _isLicenseActivated.value = false
                _isExpirySuspended.value = false
            }
            return
        }

        val verification = LicenseSecurityManager.verifyLicenseToken(
            tokenString = storedToken,
            currentDeviceId = getDeviceId(),
            trustedKeyRing = trustedKeyRing,
            nowMillis = nowMillis
        )
        val clockState = clockGuard.evaluateAgainstVerification(clockEval, verification)

        when {
            verification.isValid && clockState == LicenseClockState.VALID -> {
                val exp = verification.expiryTimestamp ?: 0L
                val grace = verification.graceUntilTimestamp ?: 0L
                val kid = verification.keyId ?: ""
                val lType = verification.licenseType ?: "PRO"

                clockGuard.recordVerifiedSessionTick(
                    wallClockEval = clockEval,
                    issuedAtMillis = verification.issuedAtTimestamp
                )

                prefs.edit()
                    .putBoolean("license_activated", true)
                    .putString("license_number", storedToken)
                    .putString("license_type", lType)
                    .putLong("license_expires", exp)
                    .putLong("license_grace_until", grace)
                    .putString("license_kid", kid)
                    .apply()

                _licenseKey.value = storedToken
                _licenseType.value = lType
                _licenseExpires.value = exp
                _licenseGraceUntil.value = grace
                _licenseKeyId.value = kid
                _isLicenseActivated.value = true
                _isClockRollbackSuspended.value = false
                _isExpirySuspended.value = false
                _needsOnlineRenewal.value = false
                _licenseError.value = null
            }

            clockState == LicenseClockState.ROLLBACK_SUSPENDED -> {
                clockGuard.advanceTrustedWatermarkOnly(verification.issuedAtTimestamp)
                suspendForClockRollback(storedToken, verification.errorMessage)
            }

            clockState == LicenseClockState.CONFIRMED_EXPIRED -> {
                Log.w(
                    TAG,
                    "Stored license expiry confirmed by trusted watermark (${clockEval.trustedWatermarkMillis} > ${verification.expiryTimestamp})"
                )
                deactivateLicense()
                _licenseError.value = when (languageProvider().uppercase()) {
                    "FR" -> "La licence a expiré (confirmé par une source temporelle fiable)."
                    "EN" -> "License has expired (confirmed by trusted time)."
                    else -> "انتهت صلاحية الترخيص بشكل مؤكد. يرجى إدخال رمز تجديد موقّع."
                }
            }

            clockState == LicenseClockState.LOCAL_EXPIRY_SUSPENDED -> {
                Log.w(TAG, "Local clock exceeds token expiry; suspending access while preserving signed token")
                prefs.edit().putBoolean("license_activated", false).apply()
                _isLicenseActivated.value = false
                _isExpirySuspended.value = true
                _needsOnlineRenewal.value = false
                _licenseKey.value = storedToken
                _licenseType.value = verification.licenseType ?: _licenseType.value
                _licenseExpires.value = verification.expiryTimestamp ?: 0L
                _licenseGraceUntil.value = verification.graceUntilTimestamp ?: 0L
                _licenseKeyId.value = verification.keyId ?: ""
                _licenseError.value = when (languageProvider().uppercase()) {
                    "FR" -> "Licence suspendue : date de l'appareil postérieure à l'expiration. Corrigez la date si elle est erronée ou vérifiez votre abonnement en ligne."
                    "EN" -> "License suspended: device date is past expiry. Correct the device date if wrong, or renew online."
                    else -> "تم تعليق الترخيص مؤقتاً لأن تاريخ الجهاز يتجاوز تاريخ الانتهاء. إذا كان التاريخ خاطئاً فصحّحه ليستأنف الترخيص تلقائياً دون رمز جديد، أو جدّد الاشتراك عبر الإنترنت."
                }
            }

            verification.needsOnlineRenewal -> {
                prefs.edit().putBoolean("license_activated", false).apply()
                _isLicenseActivated.value = false
                _isExpirySuspended.value = false
                _licenseKey.value = storedToken
                _licenseExpires.value = verification.expiryTimestamp ?: 0L
                _licenseGraceUntil.value = verification.graceUntilTimestamp ?: 0L
                _licenseKeyId.value = verification.keyId ?: ""
                _needsOnlineRenewal.value = true
                _licenseError.value = verification.errorMessage
            }

            else -> {
                Log.w(TAG, "Stored license rejected on cryptographic/binding verification: ${verification.errorMessage}")
                deactivateLicense()
                _licenseError.value = verification.errorMessage
            }
        }
    }

    fun triggerSilentGraceRenewalIfDue(
        serverUrl: String = LicenseSecurityManager.DEFAULT_LICENSE_SERVER_URL
    ) {
        if (_isClockRollbackSuspended.value) return
        val token = _licenseKey.value.trim()
        if (token.isEmpty()) return
        val graceDays = getRemainingGraceDays()
        val shouldRenew = _needsOnlineRenewal.value || (_isLicenseActivated.value && graceDays in 0..AUTO_RENEW_THRESHOLD_DAYS)
        if (!shouldRenew || !isInternetAvailable()) return

        verifyOnlineSubscription(serverUrl = serverUrl) { ok, msg ->
            if (!ok) {
                Log.w(TAG, "Silent offline-grace renewal did not complete: $msg")
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query network state for license grace renewal", e)
            false
        }
    }

    fun verifyOnlineSubscription(
        serverUrl: String = LicenseSecurityManager.DEFAULT_LICENSE_SERVER_URL,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        scope.launch {
            val token = _licenseKey.value.trim().ifEmpty {
                prefs.getString("license_number", "")?.trim().orEmpty()
            }
            val deviceId = getDeviceId()
            if (token.isBlank()) {
                onResult(false, "لا يوجد رمز ترخيص مسجل للتحقق عبر الإنترنت")
                return@launch
            }
            val res = onlineVerifier(deviceId, token, serverUrl, trustedKeyRing)
            val trustedServerTime = res.serverTimeMillis
            if (trustedServerTime != null) {
                clockGuard.reconcileWithTrustedTime(trustedServerTime)
            } else if (clockGuard.evaluateWallClock().isRollbackDetected) {
                val errMsg = when (languageProvider().uppercase()) {
                    "FR" -> "Impossible de joindre le serveur pour synchroniser l'heure. Corrigez la date ou appliquez un jeton CLOCK_RESET signé."
                    "EN" -> "Unable to reach server to sync trusted time. Fix device date or apply a signed CLOCK_RESET token."
                    else -> "تعذّر الاتصال بالخادم لمزامنة الوقت الموثوق. يرجى تصحيح تاريخ الجهاز أو إدخال رمز إعادة ضبط الساعة (CLOCK_RESET)."
                }
                _licenseError.value = errMsg
                onResult(false, errMsg)
                return@launch
            }
            if (res.isValid) {
                val effectiveToken = res.renewedSignedToken?.takeIf { it.isNotBlank() } ?: token
                val exp = res.expiryTimestamp ?: 0L
                val grace = res.graceUntilTimestamp ?: 0L
                val kid = res.keyId ?: _licenseKeyId.value
                val lType = res.licenseType ?: _licenseType.value
                if (trustedServerTime == null) {
                    clockGuard.advanceTrustedWatermarkOnly(res.issuedAtTimestamp)
                }
                prefs.edit()
                    .putBoolean("license_activated", true)
                    .putString("license_number", effectiveToken)
                    .putLong("license_expires", exp)
                    .putLong("license_grace_until", grace)
                    .putString("license_kid", kid)
                    .putString("license_type", lType)
                    .apply()
                _licenseKey.value = effectiveToken
                _licenseExpires.value = exp
                _licenseGraceUntil.value = grace
                _licenseKeyId.value = kid
                _licenseType.value = lType
                _isLicenseActivated.value = true
                _isClockRollbackSuspended.value = false
                _isExpirySuspended.value = false
                _needsOnlineRenewal.value = false
                _licenseError.value = null
                onResult(true, "تم التحقق ومزامنة الوقت وتجديد فترة السماح الموقّعة بنجاح!")
            } else {
                when {
                    res.revokedByServer || (res.isExpired && trustedServerTime != null) -> {
                        deactivateLicense()
                    }
                    res.isExpired -> {
                        prefs.edit().putBoolean("license_activated", false).apply()
                        _isLicenseActivated.value = false
                        _isExpirySuspended.value = true
                    }
                    res.needsOnlineRenewal -> {
                        prefs.edit().putBoolean("license_activated", false).apply()
                        _isLicenseActivated.value = false
                        _needsOnlineRenewal.value = true
                    }
                }
                val errMsg = res.errorMessage ?: "فشل التحقق من الاشتراك عبر الإنترنت"
                _licenseError.value = errMsg
                onResult(false, errMsg)
            }
        }
    }

    /**
     * Verifies and applies a one-time ECDSA-signed `CLOCK_RESET` token, lowering any previously
     * elevated watermark/floor to the signed reference timestamp and re-evaluating the preserved
     * license automatically.
     */
    @Synchronized
    fun applyClockResetToken(
        resetToken: String,
        nowMillis: Long = System.currentTimeMillis(),
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        val clean = resetToken.trim()
        val result = LicenseSecurityManager.verifyClockResetToken(
            tokenString = clean,
            currentDeviceId = getDeviceId(),
            context = context,
            trustedKeyRing = trustedKeyRing,
            nowMillis = nowMillis
        )
        val signedResetTime = result.resetTimeMillis
        if (!result.isValid || signedResetTime == null) {
            val err = result.errorMessage ?: "رمز إعادة ضبط الساعة غير صالح"
            _licenseError.value = err
            onError(err)
            return false
        }
        LicenseSecurityManager.consumeRecoveryToken(context, clean)
        clockGuard.reconcileWithTrustedTime(signedResetTime)
        reverifyStoredLicenseOnBoot(nowMillis = nowMillis)
        val okMsg = when (languageProvider().uppercase()) {
            "FR" -> "Horloge réinitialisée avec succès via jeton signé CLOCK_RESET."
            "EN" -> "Clock guard reset successfully via signed CLOCK_RESET token."
            else -> "تمت إعادة ضبط مرجع الساعة بنجاح عبر رمز CLOCK_RESET الموقّع."
        }
        onSuccess(okMsg)
        return true
    }

    @Synchronized
    fun activateLicense(
        activationCode: String,
        onSuccess: (isMasterReset: Boolean) -> Unit = {},
        onError: (String) -> Unit = {},
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val token = activationCode.trim()
        val deviceId = getDeviceId()
        val result = LicenseSecurityManager.verifyLicenseToken(
            tokenString = token,
            currentDeviceId = deviceId,
            trustedKeyRing = trustedKeyRing,
            nowMillis = nowMillis
        )
        return if (result.isValid) {
            val exp = result.expiryTimestamp ?: 0L
            val grace = result.graceUntilTimestamp ?: 0L
            val kid = result.keyId ?: LicenseSecurityManager.DEFAULT_KEY_ID
            val lType = result.licenseType ?: "PRO"
            clockGuard.recordActivationAnchor(
                issuedAtMillis = result.issuedAtTimestamp
            )

            prefs.edit()
                .putBoolean("license_activated", true)
                .putString("license_number", token)
                .putString("license_type", lType)
                .putLong("license_expires", exp)
                .putLong("license_grace_until", grace)
                .putString("license_kid", kid)
                .apply()
            _licenseKey.value = token
            _licenseType.value = lType
            _licenseExpires.value = exp
            _licenseGraceUntil.value = grace
            _licenseKeyId.value = kid
            _isLicenseActivated.value = true
            _isClockRollbackSuspended.value = false
            _isExpirySuspended.value = false
            _needsOnlineRenewal.value = false
            _licenseError.value = null
            onSuccess(false)
            true
        } else {
            val msg = result.errorMessage ?: if (languageProvider() == "AR") {
                "رمز التفعيل غير صحيح أو التوقيع الرقمي غير معتمد لهذا الجهاز"
            } else {
                "Code d'activation invalide pour cet appareil"
            }
            _needsOnlineRenewal.value = result.needsOnlineRenewal
            _licenseError.value = msg
            onError(msg)
            false
        }
    }
}
