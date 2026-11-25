package com.example.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GlobalSettingsEntity
import com.example.data.PosRepository
import com.example.data.UserEntity
import com.example.security.LicenseSecurityManager
import com.example.security.PasswordHasher
import com.example.security.PasswordPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modular ViewModel handling Authentication, Emergency Recovery, Password Management,
 * Delete/Edit Action Security, and delegating ECDSA License Lifecycle enforcement
 * (on boot, on resume, and on a periodic runtime loop).
 */
class AuthSecurityViewModel(
    private val repository: PosRepository,
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE),
    externalScope: CoroutineScope? = null,
    private val languageProvider: () -> String = { "AR" },
    private val onBeep: () -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val trustedKeyRing: Map<String, String> = LicenseSecurityManager.TRUSTED_PUBLIC_KEYS,
    private val enablePeriodicLicenseCheck: Boolean = true,
    private val elapsedRealtimeProvider: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val bootCountProvider: () -> Int = {
        runCatching {
            android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 0)
        }.getOrDefault(0)
    }
) : ViewModel() {

    companion object {
        private const val TAG = "AuthSecurityViewModel"
    }

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val licenseDelegate = LicenseLifecycleDelegate(
        context = context,
        prefs = prefs,
        scope = scope,
        languageProvider = languageProvider,
        trustedKeyRing = trustedKeyRing,
        elapsedRealtimeProvider = elapsedRealtimeProvider,
        bootCountProvider = bootCountProvider
    )

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    val isLicenseActivated: StateFlow<Boolean> get() = licenseDelegate.isLicenseActivated
    val isClockRollbackSuspended: StateFlow<Boolean> get() = licenseDelegate.isClockRollbackSuspended
    val isExpirySuspended: StateFlow<Boolean> get() = licenseDelegate.isExpirySuspended
    val licenseKey: StateFlow<String> get() = licenseDelegate.licenseKey
    val licenseExpires: StateFlow<Long> get() = licenseDelegate.licenseExpires
    val licenseGraceUntil: StateFlow<Long> get() = licenseDelegate.licenseGraceUntil
    val licenseKeyId: StateFlow<String> get() = licenseDelegate.licenseKeyId
    val licenseType: StateFlow<String> get() = licenseDelegate.licenseType
    val needsOnlineRenewal: StateFlow<Boolean> get() = licenseDelegate.needsOnlineRenewal
    val licenseError: StateFlow<String?> get() = licenseDelegate.licenseError

    private val _requirePasswordOnLogin = MutableStateFlow(prefs.getBoolean("sec_require_pass_login", true))
    val requirePasswordOnLogin: StateFlow<Boolean> = _requirePasswordOnLogin.asStateFlow()

    @Volatile
    private var explicitlyLoggedOut = false

    private val actionSecurityDelegate = ActionPasswordSecurityDelegate(
        prefs = prefs,
        scope = scope,
        ioDispatcher = ioDispatcher,
        currentUserProvider = { _currentUser.value },
        languageProvider = languageProvider
    )

    private val credentialsDelegate = AccountCredentialsDelegate(
        repository = repository,
        context = context,
        prefs = prefs,
        scope = scope,
        ioDispatcher = ioDispatcher,
        languageProvider = languageProvider,
        currentUserProvider = { _currentUser.value },
        onCurrentUserUpdated = { _currentUser.value = it },
        onLoginError = { _loginError.value = it },
        onRequireLoginEnabled = { updateLoginPasswordSecurity(true) },
        onBeep = onBeep,
        deviceIdProvider = { getDeviceId() }
    )

    val requirePasswordOnDelete: StateFlow<Boolean> get() = actionSecurityDelegate.requirePasswordOnDelete
    val requirePasswordOnEdit: StateFlow<Boolean> get() = actionSecurityDelegate.requirePasswordOnEdit

    init {
        PasswordHasher.init(context)
        // Re-verify stored ECDSA token on every boot (never trust boolean flag alone)
        licenseDelegate.reverifyStoredLicenseOnBoot()
        licenseDelegate.triggerSilentGraceRenewalIfDue()
        if (enablePeriodicLicenseCheck) {
            licenseDelegate.startPeriodicLicenseMonitor()
        }
    }

    override fun onCleared() {
        licenseDelegate.stopPeriodicLicenseMonitor()
        super.onCleared()
    }

    fun getDeviceId(): String = licenseDelegate.getDeviceId()
    fun getRemainingLicenseDays(): Long = licenseDelegate.getRemainingLicenseDays()
    fun getRemainingGraceDays(): Long = licenseDelegate.getRemainingGraceDays()
    fun deactivateLicense() = licenseDelegate.deactivateLicense()

    fun startPeriodicLicenseMonitor(
        intervalMs: Long = LicenseLifecycleDelegate.DEFAULT_PERIODIC_CHECK_INTERVAL_MS,
        nowProvider: () -> Long = System::currentTimeMillis
    ) = licenseDelegate.startPeriodicLicenseMonitor(intervalMs, nowProvider)

    fun stopPeriodicLicenseMonitor() = licenseDelegate.stopPeriodicLicenseMonitor()

    fun checkLicenseExpirationWithAntiRollback(nowMillis: Long = System.currentTimeMillis()) {
        licenseDelegate.reverifyStoredLicenseOnBoot(nowMillis = nowMillis)
        licenseDelegate.triggerSilentGraceRenewalIfDue()
    }

    fun recordTrustedLicenseTime(trustedTimeMillis: Long) =
        licenseDelegate.recordTrustedTime(trustedTimeMillis)

    fun verifyOnlineSubscription(
        serverUrl: String = LicenseSecurityManager.DEFAULT_LICENSE_SERVER_URL,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) = licenseDelegate.verifyOnlineSubscription(serverUrl, onResult)

    fun applyClockResetToken(
        resetToken: String,
        nowMillis: Long = System.currentTimeMillis(),
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean = licenseDelegate.applyClockResetToken(resetToken, nowMillis, onSuccess, onError)

    fun activateLicense(
        activationCode: String,
        onSuccess: (isMasterReset: Boolean) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean = licenseDelegate.activateLicense(activationCode, onSuccess, onError)

    fun activateLicense(
        licenseNumber: String,
        activationPassword: String,
        onSuccess: (isMasterReset: Boolean) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Boolean {
        val codeToTest = activationPassword.trim().ifEmpty { licenseNumber.trim() }
        return activateLicense(codeToTest, onSuccess, onError)
    }

    fun isWeakPassword(pass: String): Boolean = PasswordPolicy.isWeak(pass)
    fun isLockedOut(): Boolean = PasswordHasher.isLockedOut(context)
    fun getRemainingLockoutSeconds(): Long = PasswordHasher.getRemainingLockoutSeconds(context)

    private sealed class LoginOutcome {
        data class AlreadyLocked(val remainingSeconds: Long) : LoginOutcome()
        data class Success(val user: UserEntity) : LoginOutcome()
        data class Failed(val lockedNow: Boolean, val remainingSeconds: Long?) : LoginOutcome()
    }

    fun login(username: String, pin: String, onSuccess: () -> Unit) {
        scope.launch {
            try {
                val trimmedUser = username.trim()
                val trimmedPin = pin.trim()
                if (trimmedUser.isBlank() || trimmedPin.isBlank()) {
                    _loginError.value = "invalid_credentials"
                    return@launch
                }

                val outcome = withContext(ioDispatcher) {
                    if (PasswordHasher.isLockedOut(context)) {
                        return@withContext LoginOutcome.AlreadyLocked(
                            PasswordHasher.getRemainingLockoutSeconds(context)
                        )
                    }
                    val user = repository.authenticate(trimmedUser, trimmedPin, ioDispatcher)
                    if (user != null) {
                        PasswordHasher.resetFailedAttempts(context)
                        return@withContext LoginOutcome.Success(user)
                    }

                    PasswordHasher.recordFailedAttempt(context)
                    repository.recordAuditLog(
                        actionType = "FAILED_LOGIN",
                        entityType = "AUTH",
                        entityId = trimmedUser,
                        details = "محاولة تسجيل دخول فاشلة للمستخدم: $trimmedUser",
                        username = trimmedUser,
                        userRole = "UNKNOWN",
                        isCritical = true
                    )
                    val lockedNow = PasswordHasher.isLockedOut(context)
                    val remaining = if (lockedNow) PasswordHasher.getRemainingLockoutSeconds(context) else null
                    LoginOutcome.Failed(lockedNow, remaining)
                }

                when (outcome) {
                    is LoginOutcome.AlreadyLocked -> {
                        _loginError.value = if (languageProvider() == "AR") {
                            "تم قفل المحاولات مؤقتاً لأسباب أمنية. يرجى الانتظار ${outcome.remainingSeconds} ثانية"
                        } else {
                            "Trop de tentatives échouées. Veuillez patienter ${outcome.remainingSeconds} s"
                        }
                    }
                    is LoginOutcome.Success -> {
                        explicitlyLoggedOut = false
                        _currentUser.value = outcome.user
                        _loginError.value = null
                        onSuccess()
                    }
                    is LoginOutcome.Failed -> {
                        _loginError.value = if (outcome.lockedNow && outcome.remainingSeconds != null) {
                            if (languageProvider() == "AR") {
                                "محاولات خاطئة متكررة! تم القفل المؤقت لمدة ${outcome.remainingSeconds} ثانية"
                            } else {
                                "Compte temporairement bloqué pendant ${outcome.remainingSeconds} s"
                            }
                        } else {
                            "invalid_credentials"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                _loginError.value = e.message ?: "invalid_credentials"
            }
        }
    }

    fun updateLoginPasswordSecurity(require: Boolean) {
        _requirePasswordOnLogin.value = require
        prefs.edit().putBoolean("sec_require_pass_login", require).apply()
    }

    fun autoLoginIfPasswordNotRequired(users: List<UserEntity>) {
        if (_requirePasswordOnLogin.value || _currentUser.value != null || explicitlyLoggedOut || users.isEmpty()) return
        val manager = users.firstOrNull { it.isManager } ?: users.firstOrNull()
        if (manager != null) {
            _currentUser.value = manager
            _loginError.value = null
        }
    }

    fun logout() {
        explicitlyLoggedOut = true
        _currentUser.value = null
    }

    fun syncCurrentUser(updatedUser: UserEntity, previousUsername: String? = null) {
        val current = _currentUser.value ?: return
        val matchesById = updatedUser.id > 0L && current.id == updatedUser.id
        val matchesByOldName = !previousUsername.isNullOrBlank() &&
            current.username.trim().equals(previousUsername.trim(), ignoreCase = true)
        if (matchesById || matchesByOldName) {
            _currentUser.value = updatedUser
        }
    }

    fun setProgramLoginPassword(newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) =
        credentialsDelegate.setProgramLoginPassword(newPass, confirmPass, onResult)

    fun updateCurrentUsername(newUsername: String, onResult: (Boolean, String) -> Unit) =
        credentialsDelegate.updateCurrentUsername(newUsername, onResult)

    fun executeEmergencyRecovery(
        recoveryToken: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = credentialsDelegate.executeEmergencyRecovery(recoveryToken, newPassword, onSuccess, onError)

    fun verifyBackupPassword(password: String, onResult: (Boolean) -> Unit) =
        credentialsDelegate.verifyBackupPassword(password, onResult)

    fun setupInitialAdmin(
        username: String,
        pin: String,
        storeName: String = "",
        phone: String = "",
        address: String = "",
        currentSettings: GlobalSettingsEntity = GlobalSettingsEntity(),
        onSaveSettings: (GlobalSettingsEntity) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) = credentialsDelegate.setupInitialAdmin(username, pin, storeName, phone, address, currentSettings, onSaveSettings, onSuccess)

    fun changePassword(currentPass: String, newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) =
        credentialsDelegate.changePassword(currentPass, newPass, confirmPass, onResult)

    fun updateDeleteSecurity(
        require: Boolean,
        newPass: String,
        onError: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) = actionSecurityDelegate.updateDeleteSecurity(require, newPass, onError, onComplete)

    fun updateEditSecurity(
        require: Boolean,
        newPass: String,
        onError: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) = actionSecurityDelegate.updateEditSecurity(require, newPass, onError, onComplete)

    fun verifyDeletePassword(input: String, allUsers: List<UserEntity>, onResult: (Boolean) -> Unit) =
        actionSecurityDelegate.verifyDeletePassword(input, allUsers, onResult)

    fun verifyEditPassword(input: String, allUsers: List<UserEntity>, onResult: (Boolean) -> Unit) =
        actionSecurityDelegate.verifyEditPassword(input, allUsers, onResult)
}
