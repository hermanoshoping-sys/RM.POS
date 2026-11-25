package com.example.security

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import org.json.JSONObject

/**
 * Test-only ECDSA P-256 signing helper for license & emergency recovery tokens.
 * Kept strictly under `src/test` so no private-key signing routines ship in production APKs.
 */
internal object TestLicenseTokenSigner {

    fun getPrivateKey(privateKeyBase64: String): PrivateKey? {
        return try {
            val keyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
            val spec = PKCS8EncodedKeySpec(keyBytes)
            KeyFactory.getInstance("EC").generatePrivate(spec)
        } catch (_: Exception) {
            null
        }
    }

    fun generateSignedToken(
        deviceId: String,
        licenseType: String = "PRO",
        expiryTimestamp: Long,
        privateKeyBase64: String,
        keyId: String = LicenseSecurityManager.DEFAULT_KEY_ID,
        issuedAt: Long = System.currentTimeMillis(),
        graceUntilTimestamp: Long = if (expiryTimestamp > 0L) {
            minOf(expiryTimestamp, issuedAt + LicenseSecurityManager.DEFAULT_OFFLINE_GRACE_MILLIS)
        } else {
            0L
        }
    ): String {
        val cleanDevice = deviceId.trim()
        require(cleanDevice.isNotEmpty() && cleanDevice != "*") {
            "معرف الجهاز إلزامي ولا يمكن استخدام الرمز العام *"
        }
        require(keyId.isNotBlank()) { "معرّف المفتاح (kid) إلزامي" }
        require(expiryTimestamp > 0L) { "تاريخ انتهاء الرخصة (expires) إلزامي ويجب أن يكون أكبر من الصفر" }
        require(graceUntilTimestamp in 1..expiryTimestamp) {
            "فترة السماح الموقّعة (grace_until) يجب أن تكون موجبة ولا تتجاوز تاريخ انتهاء الرخصة"
        }

        val privateKey = getPrivateKey(privateKeyBase64)
            ?: throw IllegalArgumentException("مفتاح التوقيع الخاص غير صالح")

        val payload = JSONObject().apply {
            put("kid", keyId.trim())
            put("device_id", cleanDevice)
            put("type", licenseType)
            put("issued_at", issuedAt)
            put("expires", expiryTimestamp)
            put("grace_until", graceUntilTimestamp)
        }
        val payloadBytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
        val payloadB64 = Base64.encodeToString(payloadBytes, Base64.NO_WRAP)

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(payloadBytes)
        val sigB64 = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)

        return "$payloadB64.$sigB64"
    }

    fun generateSignedRecoveryToken(
        deviceId: String,
        targetUser: String = "admin",
        expiryTimestamp: Long,
        privateKeyBase64: String,
        keyId: String = LicenseSecurityManager.DEFAULT_KEY_ID,
        issuedAt: Long = System.currentTimeMillis()
    ): String {
        val cleanDevice = deviceId.trim()
        require(cleanDevice.isNotEmpty() && cleanDevice != "*") {
            "معرف الجهاز إلزامي ولا يمكن استخدام الرمز العام *"
        }
        require(keyId.isNotBlank()) { "معرّف المفتاح (kid) إلزامي" }
        require(expiryTimestamp > 0L) { "تاريخ انتهاء رمز الاسترجاع إلزامي" }

        val privateKey = getPrivateKey(privateKeyBase64)
            ?: throw IllegalArgumentException("مفتاح التوقيع الخاص غير صالح")

        val payload = JSONObject().apply {
            put("kid", keyId.trim())
            put("action", "RECOVERY")
            put("device_id", cleanDevice)
            put("target_user", targetUser)
            put("issued_at", issuedAt)
            put("expires", expiryTimestamp)
        }
        val payloadBytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
        val payloadB64 = Base64.encodeToString(payloadBytes, Base64.NO_WRAP)

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(payloadBytes)
        val sigB64 = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)

        return "$payloadB64.$sigB64"
    }

    fun generateSignedClockResetToken(
        deviceId: String,
        resetTimeMillis: Long,
        expiryTimestamp: Long,
        privateKeyBase64: String,
        keyId: String = LicenseSecurityManager.DEFAULT_KEY_ID,
        issuedAt: Long = resetTimeMillis
    ): String {
        val cleanDevice = deviceId.trim()
        require(cleanDevice.isNotEmpty() && cleanDevice != "*") {
            "معرف الجهاز إلزامي ولا يمكن استخدام الرمز العام *"
        }
        require(keyId.isNotBlank()) { "معرّف المفتاح (kid) إلزامي" }
        require(resetTimeMillis > 0L) { "الطابع الزمني لإعادة الضبط إلزامي" }
        require(expiryTimestamp >= resetTimeMillis) { "تاريخ انتهاء الرمز يجب أن يكون بعد الطابع الزمني" }

        val privateKey = getPrivateKey(privateKeyBase64)
            ?: throw IllegalArgumentException("مفتاح التوقيع الخاص غير صالح")

        val payload = JSONObject().apply {
            put("kid", keyId.trim())
            put("action", "CLOCK_RESET")
            put("device_id", cleanDevice)
            put("reset_time", resetTimeMillis)
            put("issued_at", issuedAt)
            put("expires", expiryTimestamp)
        }
        val payloadBytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
        val payloadB64 = Base64.encodeToString(payloadBytes, Base64.NO_WRAP)

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(payloadBytes)
        val sigB64 = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)

        return "$payloadB64.$sigB64"
    }

    fun generateSignedRevocationToken(
        deviceId: String,
        revokedAtMillis: Long,
        expiryTimestamp: Long = revokedAtMillis + 7L * 86_400_000L,
        reason: String = "تم إلغاء الاشتراك من الإدارة",
        privateKeyBase64: String,
        keyId: String = LicenseSecurityManager.DEFAULT_KEY_ID
    ): String {
        val cleanDevice = deviceId.trim()
        require(cleanDevice.isNotEmpty() && cleanDevice != "*") {
            "معرف الجهاز إلزامي ولا يمكن استخدام الرمز العام *"
        }
        require(keyId.isNotBlank()) { "معرّف المفتاح (kid) إلزامي" }
        val privateKey = getPrivateKey(privateKeyBase64)
            ?: throw IllegalArgumentException("مفتاح التوقيع الخاص غير صالح")

        val payload = JSONObject().apply {
            put("kid", keyId.trim())
            put("action", "REVOKE")
            put("device_id", cleanDevice)
            put("revoked_at", revokedAtMillis)
            put("issued_at", revokedAtMillis)
            put("expires", expiryTimestamp)
            put("reason", reason)
        }
        val payloadBytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
        val payloadB64 = Base64.encodeToString(payloadBytes, Base64.NO_WRAP)

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(payloadBytes)
        val sigB64 = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)

        return "$payloadB64.$sigB64"
    }
}
