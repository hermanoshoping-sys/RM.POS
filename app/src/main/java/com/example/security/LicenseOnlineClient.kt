package com.example.security

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Network client for online subscription verification and trusted server-time retrieval.
 * - Requires an explicit configured HTTPS server URL (`BuildConfig.LICENSE_SERVER_URL`).
 * - Accepts positive renewals ONLY with a cryptographically valid ECDSA `signed_token`.
 * - Accepts revocations (`valid: false`) ONLY with a cryptographically valid ECDSA
 *   `revocation_token` (`action == "REVOKE"`, matching `device_id`, trusted `kid`),
 *   preventing unsigned MITM / captive-portal / spoofed-domain license deletion.
 */
internal object LicenseOnlineClient {
    private const val TAG = "LicenseOnlineClient"

    suspend fun verifyOnlineSubscription(
        deviceId: String,
        licenseKey: String,
        serverUrl: String,
        trustedKeyRing: Map<String, String>
    ): LicenseSecurityManager.LicenseVerificationResult = withContext(Dispatchers.IO) {
        val trimmedUrl = serverUrl.trim()
        if (trimmedUrl.isEmpty() || !trimmedUrl.startsWith("https://", ignoreCase = true)) {
            Log.w(TAG, "Online license server URL is not configured with HTTPS; skipping remote call")
            return@withContext LicenseSecurityManager.LicenseVerificationResult(
                isValid = false,
                errorMessage = "خادم التحقق عبر الإنترنت غير مهيّأ في هذه النسخة؛ يرجى ضبط LICENSE_SERVER_URL أو استخدام رمز CLOCK_RESET الموقّع.",
                revokedByServer = false,
                serverTimeMillis = null
            )
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(trimmedUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val requestBody = JSONObject().apply {
                put("device_id", deviceId)
                put("license_key", licenseKey)
                put("timestamp", System.currentTimeMillis())
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(StandardCharsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseStr = BufferedReader(
                    InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)
                ).use { it.readText() }
                parseServerResponse(
                    responseStr = responseStr,
                    httpDateMillis = connection.date.takeIf { it > 0L },
                    deviceId = deviceId,
                    trustedKeyRing = trustedKeyRing
                )
            } else {
                Log.w(TAG, "License server returned HTTP $responseCode; checking offline signed grace window")
                LicenseSecurityManager.verifyLicenseToken(licenseKey, deviceId, trustedKeyRing = trustedKeyRing)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online license renewal unreachable; verifying signed offline grace token", e)
            LicenseSecurityManager.verifyLicenseToken(licenseKey, deviceId, trustedKeyRing = trustedKeyRing)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parses and cryptographically validates a JSON response from the license verification server.
     * Both renewals (`valid == true`) and revocations (`valid == false`) require a valid ECDSA
     * signature under [trustedKeyRing] bound to [deviceId].
     */
    internal fun parseServerResponse(
        responseStr: String,
        httpDateMillis: Long? = null,
        deviceId: String,
        trustedKeyRing: Map<String, String> = LicenseSecurityManager.TRUSTED_PUBLIC_KEYS,
        nowMillisProvider: () -> Long = { System.currentTimeMillis() }
    ): LicenseSecurityManager.LicenseVerificationResult {
        val respJson = JSONObject(responseStr)
        val trustedServerTime = respJson.optLong("server_time", 0L).takeIf { it > 0L }
            ?: respJson.optLong("timestamp", 0L).takeIf { it > 0L }
            ?: httpDateMillis
        val effectiveNow = trustedServerTime ?: nowMillisProvider()
        val isValid = respJson.optBoolean("valid", false)

        if (!isValid) {
            val revocationToken = respJson.optString("revocation_token", "").trim()
                .ifEmpty { respJson.optString("signed_token", "").trim() }
            if (revocationToken.isEmpty()) {
                Log.w(TAG, "Ignoring unsigned server revocation response (valid=false without revocation_token)")
                return LicenseSecurityManager.LicenseVerificationResult(
                    isValid = false,
                    errorMessage = "تم رفض رد إلغاء غير موقّع رقمياً من الخادم لحماية الترخيص المحلي.",
                    revokedByServer = false,
                    serverTimeMillis = null
                )
            }
            val revCheck = LicenseSecurityManager.verifyRevocationToken(
                tokenString = revocationToken,
                currentDeviceId = deviceId,
                trustedKeyRing = trustedKeyRing,
                nowMillis = effectiveNow
            )
            if (!revCheck.isValid) {
                Log.w(TAG, "Rejecting forged/invalid revocation_token: ${revCheck.errorMessage}")
                return LicenseSecurityManager.LicenseVerificationResult(
                    isValid = false,
                    errorMessage = revCheck.errorMessage ?: "توقيع إلغاء الترخيص غير معتمد؛ لم يتم إلغاء الترخيص المحلي.",
                    revokedByServer = false,
                    serverTimeMillis = null
                )
            }
            val errMsg = revCheck.reason
                ?: respJson.optString("message", "").takeIf { it.isNotBlank() }
                ?: "تم إلغاء الاشتراك رسمياً بتوقيع رقمي موثّق من الخادم"
            return LicenseSecurityManager.LicenseVerificationResult(
                isValid = false,
                errorMessage = errMsg,
                revokedByServer = true,
                keyId = revCheck.keyId,
                serverTimeMillis = revCheck.revokedAtMillis ?: trustedServerTime
            )
        }

        val signedToken = respJson.optString("signed_token", "").trim()
        if (signedToken.isEmpty()) {
            return LicenseSecurityManager.LicenseVerificationResult(
                isValid = false,
                errorMessage = "استجابة الخادم تفتقر إلى رمز التجديد الموقّع رقمياً (signed_token)",
                revokedByServer = false,
                serverTimeMillis = null
            )
        }
        val verified = LicenseSecurityManager.verifyLicenseToken(
            tokenString = signedToken,
            currentDeviceId = deviceId,
            trustedKeyRing = trustedKeyRing,
            nowMillis = effectiveNow
        )
        return if (verified.isValid) {
            verified.copy(
                renewedSignedToken = signedToken,
                serverTimeMillis = trustedServerTime
            )
        } else {
            verified.copy(
                revokedByServer = false,
                serverTimeMillis = if (verified.keyId != null) trustedServerTime else null
            )
        }
    }
}
