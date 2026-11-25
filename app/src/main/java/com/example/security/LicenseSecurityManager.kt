package com.example.security

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import org.json.JSONObject

/**
 * Cryptographic License & Recovery Manager using ECDSA P-256 (SHA256withECDSA).
 * - Rejects all unsigned or formatted keys (such as RM.POS@...).
 * - Enforces key-id (`kid`) lookup for zero-downtime key rotation (legacy unrotated key removed).
 * - Requires strict `device_id` binding, positive `expires` timestamp, and signed `grace_until`
 *   offline lease window renewed periodically via signed server tokens.
 * - Eliminates shared fallback Device IDs so distinct devices never collide.
 */
object LicenseSecurityManager {
    private const val TAG = "LicenseSecurityManager"

    const val DEFAULT_KEY_ID = "k2026-v2"

    /**
     * Active rotated ECDSA P-256 Public Key in X.509 SubjectPublicKeyInfo Base64 format (kid = k2026-v2).
     * Matches `/license_public_key.pem` in project root. `k2026-v1` has been permanently revoked and removed.
     */
    const val PUBLIC_KEY_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE0KlJFIbcVllxExVzkjaw2POdxnY+i6vmtpqGyUSm59MZC1YKjqkbk2JtSk575BZZ+C+MQ73CMzZUosTLKGm08A=="

    /**
     * Trusted public key ring indexed by `kid`. Compromised/retired keys (`k2026-v1`) are excluded.
     */
    val TRUSTED_PUBLIC_KEYS: Map<String, String> = mapOf(
        DEFAULT_KEY_ID to PUBLIC_KEY_BASE64
    )

    val DEFAULT_LICENSE_SERVER_URL: String = com.example.BuildConfig.LICENSE_SERVER_URL
    const val DEFAULT_OFFLINE_GRACE_MILLIS = 30L * 24L * 60L * 60L * 1000L // 30 days

    private const val DEVICE_IDENTITY_PREFS = "rmpos_device_identity_prefs"
    private const val KEY_INSTALLATION_ENTROPY = "installation_entropy_id"
    private const val BROKEN_ANDROID_ID = "9774d56d682e549c"

    private const val RECOVERY_PREFS_NAME = "security_recovery_prefs"
    private const val KEY_CONSUMED_TOKENS = "consumed_recovery_tokens"

    /**
     * Derives a 16-character uppercase hex Device Identifier:
     * SHA-256(hardwareOrInstallId + ":" + packageName).
     * Never falls back to a shared constant; if ANDROID_ID is unavailable on a stripped ROM,
     * generates and persists a per-installation 128-bit cryptographic random seed.
     */
    fun getDeviceId(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals(BROKEN_ANDROID_ID, ignoreCase = true) }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read ANDROID_ID; falling back to per-install cryptographic seed", e)
            null
        }

        val uniqueSeed = androidId ?: getOrCreateInstallationEntropy(context)
        val raw = "$uniqueSeed:${context.packageName}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))

        val hex = StringBuilder(16)
        for (i in 0 until 8) {
            hex.append(String.format(Locale.US, "%02X", digest[i]))
        }
        return hex.toString()
    }

    @Synchronized
    private fun getOrCreateInstallationEntropy(context: Context): String {
        val prefs = context.getSharedPreferences(DEVICE_IDENTITY_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_INSTALLATION_ENTROPY, null)
        if (!existing.isNullOrBlank()) return existing

        val randomBytes = ByteArray(16)
        SecureRandom().nextBytes(randomBytes)
        val generated = buildString(32) {
            for (b in randomBytes) {
                append(String.format(Locale.US, "%02x", b))
            }
        }
        prefs.edit().putString(KEY_INSTALLATION_ENTROPY, generated).commit()
        return generated
    }

    fun getPublicKey(publicKeyBase64: String = PUBLIC_KEY_BASE64): PublicKey? {
        return try {
            val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val spec = X509EncodedKeySpec(keyBytes)
            KeyFactory.getInstance("EC").generatePublic(spec)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ECDSA public key", e)
            null
        }
    }

    /**
     * Strips whitespace, line wraps, and invisible Unicode BiDi/zero-width marks
     * commonly injected when copying tokens across WhatsApp or RTL Android clipboards.
     */
    fun sanitizePastedToken(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch.isWhitespace() ||
                ch == '\u00A0' ||
                ch in '\u200B'..'\u200F' ||
                ch in '\u202A'..'\u202E' ||
                ch in '\u2060'..'\u206F' ||
                ch == '\uFEFF'
            ) {
                continue
            }
            sb.append(ch)
        }
        return sb.toString()
    }

    fun verifySignature(payloadBytes: ByteArray, signatureBase64: String, publicKey: PublicKey?): Boolean {
        val cleanSig = sanitizePastedToken(signatureBase64)
        if (publicKey == null || cleanSig.isEmpty()) return false
        return try {
            val sigBytes = Base64.decode(cleanSig, Base64.NO_WRAP)
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(payloadBytes)
            sig.verify(sigBytes)
        } catch (e: Exception) {
            Log.w(TAG, "ECDSA signature verification failed", e)
            false
        }
    }

    data class LicenseVerificationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val deviceId: String? = null,
        val licenseType: String? = null,
        val expiryTimestamp: Long? = null,
        val graceUntilTimestamp: Long? = null,
        val keyId: String? = null,
        val needsOnlineRenewal: Boolean = false,
        val revokedByServer: Boolean = false,
        val renewedSignedToken: String? = null,
        val isExpired: Boolean = false,
        val issuedAtTimestamp: Long? = null,
        val serverTimeMillis: Long? = null,
        val isClockBeforeIssuedAt: Boolean = false
    )

    /**
     * Strictly verifies an ECDSA-signed activation token (`<payloadBase64>.<signatureBase64>`).
     * Unsigned formats (including `RM.POS@...`), wildcard device IDs, missing `kid`,
     * timeless `expires <= 0`, and expired offline grace windows are all rejected.
     */
    fun verifyLicenseToken(
        tokenString: String,
        currentDeviceId: String,
        publicKeyOverride: PublicKey? = null,
        trustedKeyRing: Map<String, String> = TRUSTED_PUBLIC_KEYS,
        nowMillis: Long = System.currentTimeMillis()
    ): LicenseVerificationResult {
        val trimmed = sanitizePastedToken(tokenString)
        if (trimmed.isEmpty()) {
            return LicenseVerificationResult(false, "يرجى إدخال رمز التفعيل الرقمي الموقّع")
        }
        if (trimmed.uppercase(Locale.US).startsWith("RM.POS@")) {
            return LicenseVerificationResult(
                isValid = false,
                errorMessage = "الرموز النصية غير الموقّعة لم تعد مقبولة؛ يتطلب النظام رمزاً رقمياً موقّعاً (ECDSA)"
            )
        }

        val parts = trimmed.split(".")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return LicenseVerificationResult(false, "صيغة رمز التفعيل الرقمي غير صالحة")
        }

        return try {
            val payloadBytes = Base64.decode(parts[0], Base64.NO_WRAP)
            val signatureBase64 = parts[1]
            val payloadJson = JSONObject(String(payloadBytes, StandardCharsets.UTF_8))

            val kid = payloadJson.optString("kid", "").trim()
            if (kid.isEmpty()) {
                return LicenseVerificationResult(false, "رمز التفعيل يفتقر إلى معرّف المفتاح (kid)")
            }

            val resolvedPublicKey = publicKeyOverride ?: run {
                val pubB64 = trustedKeyRing[kid]
                    ?: return LicenseVerificationResult(false, "معرّف مفتاح التوقيع ($kid) غير معتمد أو تم تدويره")
                getPublicKey(pubB64)
            }

            if (!verifySignature(payloadBytes, signatureBase64, resolvedPublicKey)) {
                return LicenseVerificationResult(false, "رمز التفعيل غير صالح أو التوقيع الرقمي غير معتمد")
            }

            val tokenAction = payloadJson.optString("action", "").trim()
            if (tokenAction.equals("RECOVERY", ignoreCase = true)) {
                return LicenseVerificationResult(false, "لا يمكن استخدام رمز الاسترجاع كرمز تفعيل للبرنامج")
            }
            if (tokenAction.equals("CLOCK_RESET", ignoreCase = true)) {
                return LicenseVerificationResult(false, "هذا رمز لإعادة ضبط الساعة (CLOCK_RESET) وليس رمز تفعيل")
            }
            if (tokenAction.equals("REVOKE", ignoreCase = true)) {
                return LicenseVerificationResult(false, "هذا رمز إلغاء ترخيص (REVOKE) وليس رمز تفعيل")
            }

            val tokenDeviceId = payloadJson.optString("device_id", "").trim()
            if (tokenDeviceId.isEmpty() ||
                tokenDeviceId == "*" ||
                currentDeviceId.isBlank() ||
                !tokenDeviceId.equals(currentDeviceId.trim(), ignoreCase = true)
            ) {
                return LicenseVerificationResult(
                    isValid = false,
                    errorMessage = "رمز التفعيل لا يتطابق مع معرف هذا الجهاز ($currentDeviceId)"
                )
            }

            val issuedAt = payloadJson.optLong("issued_at", 0L).takeIf { it > 0L }
                ?: payloadJson.optLong("iat", 0L).takeIf { it > 0L }
            val licenseType = payloadJson.optString("type", "PRO").ifBlank { "PRO" }

            val expiry = payloadJson.optLong("expires", 0L)
            if (expiry <= 0L) {
                return LicenseVerificationResult(false, "رمز التفعيل غير صالح لعدم تضمّنه تاريخ انتهاء موقّع")
            }
            if (issuedAt != null && issuedAt > expiry) {
                return LicenseVerificationResult(false, "تاريخ إصدار رمز التفعيل غير متسق مع تاريخ الانتهاء")
            }
            val graceUntil = payloadJson.optLong("grace_until", 0L)
            if (issuedAt != null && nowMillis < (issuedAt - 60_000L)) {
                return LicenseVerificationResult(
                    isValid = false,
                    errorMessage = "وقت وتاريخ الجهاز أقدم من تاريخ إصدار الترخيص. يرجى تصحيح تاريخ الجهاز ليستأنف الترخيص تلقائياً.",
                    deviceId = tokenDeviceId,
                    licenseType = licenseType,
                    expiryTimestamp = expiry,
                    graceUntilTimestamp = graceUntil.takeIf { it > 0L },
                    keyId = kid,
                    issuedAtTimestamp = issuedAt,
                    isClockBeforeIssuedAt = true
                )
            }
            if (nowMillis > expiry) {
                return LicenseVerificationResult(
                    isValid = false,
                    errorMessage = "انتهت صلاحية رمز التفعيل (أو أن تاريخ الجهاز مضبوط على تاريخ مستقبلي خاطئ)",
                    deviceId = tokenDeviceId,
                    licenseType = licenseType,
                    expiryTimestamp = expiry,
                    graceUntilTimestamp = graceUntil.takeIf { it > 0L },
                    keyId = kid,
                    isExpired = true,
                    issuedAtTimestamp = issuedAt
                )
            }

            if (graceUntil <= 0L || graceUntil > expiry) {
                return LicenseVerificationResult(false, "فترة السماح للعمل دون إنترنت في الرمز غير صالحة")
            }
            if (nowMillis > graceUntil) {
                return LicenseVerificationResult(
                    isValid = false,
                    errorMessage = "انتهت فترة السماح للعمل دون إنترنت؛ يرجى الاتصال بالإنترنت لتجديد التحقق من الرخصة",
                    deviceId = tokenDeviceId,
                    licenseType = licenseType,
                    expiryTimestamp = expiry,
                    graceUntilTimestamp = graceUntil,
                    keyId = kid,
                    needsOnlineRenewal = true,
                    issuedAtTimestamp = issuedAt
                )
            }

            LicenseVerificationResult(
                isValid = true,
                deviceId = tokenDeviceId,
                licenseType = licenseType,
                expiryTimestamp = expiry,
                graceUntilTimestamp = graceUntil,
                keyId = kid,
                issuedAtTimestamp = issuedAt
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode/verify license token", e)
            LicenseVerificationResult(false, "فشل فك شفرة رمز التفعيل الرقمي")
        }
    }

    data class RecoveryVerificationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val targetUser: String? = null,
        val keyId: String? = null
    )

    data class ClockResetVerificationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val resetTimeMillis: Long? = null,
        val keyId: String? = null
    )

    data class RevocationVerificationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val revokedAtMillis: Long? = null,
        val reason: String? = null,
        val keyId: String? = null
    )

    fun isRecoveryTokenConsumed(context: Context, tokenHash: String): Boolean {
        val prefs = context.getSharedPreferences(RECOVERY_PREFS_NAME, Context.MODE_PRIVATE)
        val consumed = prefs.getStringSet(KEY_CONSUMED_TOKENS, emptySet()) ?: emptySet()
        return consumed.contains(tokenHash)
    }

    fun consumeRecoveryToken(context: Context, tokenString: String) {
        val cleanToken = sanitizePastedToken(tokenString)
        val md = MessageDigest.getInstance("SHA-256")
        val hash = Base64.encodeToString(
            md.digest(cleanToken.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
        val prefs = context.getSharedPreferences(RECOVERY_PREFS_NAME, Context.MODE_PRIVATE)
        val consumed = prefs.getStringSet(KEY_CONSUMED_TOKENS, emptySet())?.toMutableSet() ?: mutableSetOf()
        consumed.add(hash)
        prefs.edit().putStringSet(KEY_CONSUMED_TOKENS, consumed).apply()
    }

    private data class ActionTokenCheck(
        val payload: JSONObject? = null,
        val kid: String? = null,
        val errorMessage: String? = null
    )

    private fun verifyOneTimeActionToken(
        tokenString: String,
        expectedAction: String,
        currentDeviceId: String,
        context: Context?,
        publicKeyOverride: PublicKey?,
        trustedKeyRing: Map<String, String>,
        nowMillis: Long,
        labelAr: String
    ): ActionTokenCheck {
        val trimmed = sanitizePastedToken(tokenString)
        val parts = trimmed.split(".")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return ActionTokenCheck(errorMessage = "صيغة $labelAr غير صالحة")
        }
        return try {
            val payloadBytes = Base64.decode(parts[0], Base64.NO_WRAP)
            val payloadJson = JSONObject(String(payloadBytes, StandardCharsets.UTF_8))
            val kid = payloadJson.optString("kid", "").trim()
            if (kid.isEmpty()) return ActionTokenCheck(errorMessage = "$labelAr يفتقر إلى معرّف المفتاح (kid)")
            val resolvedKey = publicKeyOverride ?: run {
                val pubB64 = trustedKeyRing[kid] ?: return ActionTokenCheck(errorMessage = "معرّف مفتاح التوقيع ($kid) غير معتمد")
                getPublicKey(pubB64)
            }
            if (!verifySignature(payloadBytes, parts[1], resolvedKey)) {
                return ActionTokenCheck(errorMessage = "$labelAr غير صالح أو التوقيع الرقمي غير معتمد")
            }
            if (context != null) {
                val hash = Base64.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(trimmed.toByteArray(StandardCharsets.UTF_8)),
                    Base64.NO_WRAP
                )
                if (isRecoveryTokenConsumed(context, hash)) {
                    return ActionTokenCheck(errorMessage = "تم استخدام $labelAr هذا مسبقاً، لا يمكن إعادة استخدامه")
                }
            }
            if (!payloadJson.optString("action", "").equals(expectedAction, ignoreCase = true)) {
                return ActionTokenCheck(errorMessage = "هذا الرمز ليس $labelAr معتمداً")
            }
            val tokenDeviceId = payloadJson.optString("device_id", "").trim()
            if (tokenDeviceId.isEmpty() || tokenDeviceId == "*" || currentDeviceId.isBlank() ||
                !tokenDeviceId.equals(currentDeviceId.trim(), ignoreCase = true)
            ) {
                return ActionTokenCheck(errorMessage = "$labelAr مخصص لجهاز آخر ($tokenDeviceId)")
            }
            val expiry = payloadJson.optLong("expires", 0L)
            if (expiry <= 0L || nowMillis > expiry) {
                return ActionTokenCheck(errorMessage = "انتهت صلاحية $labelAr")
            }
            ActionTokenCheck(payload = payloadJson, kid = kid)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to verify $expectedAction token", e)
            ActionTokenCheck(errorMessage = "فشل التحقق من $labelAr")
        }
    }

    /**
     * Verifies a one-time ECDSA emergency recovery token with `kid`, anti-replay, and device binding.
     */
    fun verifyRecoveryToken(
        tokenString: String,
        currentDeviceId: String,
        context: Context? = null,
        publicKeyOverride: PublicKey? = null,
        trustedKeyRing: Map<String, String> = TRUSTED_PUBLIC_KEYS,
        nowMillis: Long = System.currentTimeMillis()
    ): RecoveryVerificationResult {
        val check = verifyOneTimeActionToken(
            tokenString, "RECOVERY", currentDeviceId, context, publicKeyOverride, trustedKeyRing, nowMillis, "رمز الاسترجاع"
        )
        val payload = check.payload ?: return RecoveryVerificationResult(false, check.errorMessage)
        val targetUser = payload.optString("target_user", "admin").ifBlank { "admin" }
        return RecoveryVerificationResult(true, null, targetUser, check.kid)
    }

    /**
     * Verifies a one-time ECDSA-signed `CLOCK_RESET` token capable of lowering an elevated
     * clock floor/watermark after fixing a forward-clock error.
     */
    fun verifyClockResetToken(
        tokenString: String,
        currentDeviceId: String,
        context: Context? = null,
        publicKeyOverride: PublicKey? = null,
        trustedKeyRing: Map<String, String> = TRUSTED_PUBLIC_KEYS,
        nowMillis: Long = System.currentTimeMillis()
    ): ClockResetVerificationResult {
        val check = verifyOneTimeActionToken(
            tokenString, "CLOCK_RESET", currentDeviceId, context, publicKeyOverride, trustedKeyRing, nowMillis, "رمز إعادة ضبط الساعة"
        )
        val payload = check.payload ?: return ClockResetVerificationResult(false, check.errorMessage)
        val resetTime = payload.optLong("reset_time", 0L).takeIf { it > 0L }
            ?: payload.optLong("issued_at", 0L).takeIf { it > 0L }
            ?: payload.optLong("iat", 0L).takeIf { it > 0L }
            ?: return ClockResetVerificationResult(false, "رمز إعادة ضبط الساعة يفتقر إلى طابع زمني موقّع صالح")
        val expiry = payload.optLong("expires", 0L)
        if (resetTime > expiry) {
            return ClockResetVerificationResult(false, "الطابع الزمني لرمز إعادة ضبط الساعة غير متسق مع تاريخ الانتهاء")
        }
        return ClockResetVerificationResult(true, null, resetTime, check.kid)
    }

    /**
     * Verifies an ECDSA-signed server revocation token (`action == "REVOKE"`).
     * Unsigned `valid: false` server responses are never allowed to deactivate a stored license.
     */
    fun verifyRevocationToken(
        tokenString: String,
        currentDeviceId: String,
        publicKeyOverride: PublicKey? = null,
        trustedKeyRing: Map<String, String> = TRUSTED_PUBLIC_KEYS,
        nowMillis: Long = System.currentTimeMillis()
    ): RevocationVerificationResult {
        val check = verifyOneTimeActionToken(
            tokenString, "REVOKE", currentDeviceId, null, publicKeyOverride, trustedKeyRing, nowMillis, "رمز إلغاء الترخيص"
        )
        val payload = check.payload ?: return RevocationVerificationResult(false, check.errorMessage)
        val revokedAt = payload.optLong("revoked_at", 0L).takeIf { it > 0L }
            ?: payload.optLong("issued_at", 0L).takeIf { it > 0L }
            ?: payload.optLong("iat", 0L).takeIf { it > 0L }
            ?: return RevocationVerificationResult(false, "رمز إلغاء الترخيص يفتقر إلى طابع زمني موقّع صالح")
        val reason = payload.optString("reason", "").trim().takeIf { it.isNotEmpty() }
        return RevocationVerificationResult(true, null, revokedAt, reason, check.kid)
    }

    /**
     * Lightweight payload action detector used by UI entry forms to route `CLOCK_RESET` tokens
     * (including expired/replayed ones) through `applyClockResetToken` so accurate errors are shown.
     */
    fun isClockResetTokenPayload(tokenString: String): Boolean {
        val parts = sanitizePastedToken(tokenString).split(".")
        if (parts.size != 2 || parts[0].isBlank()) return false
        return runCatching {
            val payloadBytes = Base64.decode(parts[0], Base64.NO_WRAP)
            JSONObject(String(payloadBytes, StandardCharsets.UTF_8))
                .optString("action", "")
                .equals("CLOCK_RESET", ignoreCase = true)
        }.getOrDefault(false)
    }

    /**
     * Verifies and renews the signed offline grace window against the license server.
     * - Server responses are accepted ONLY when accompanied by a cryptographically valid `signed_token`.
     * - On network failure, falls back strictly to offline ECDSA verification of `licenseKey`
     *   (which still enforces `kid`, signature, `device_id`, `expires`, and `grace_until`).
     */
    suspend fun verifyOnlineSubscription(
        deviceId: String,
        licenseKey: String,
        serverUrl: String = DEFAULT_LICENSE_SERVER_URL,
        trustedKeyRing: Map<String, String> = TRUSTED_PUBLIC_KEYS
    ): LicenseVerificationResult = LicenseOnlineClient.verifyOnlineSubscription(
        deviceId = deviceId,
        licenseKey = licenseKey,
        serverUrl = serverUrl,
        trustedKeyRing = trustedKeyRing
    )
}
