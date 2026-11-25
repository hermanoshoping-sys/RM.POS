package com.example.security

import android.content.Context
import android.provider.Settings
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityPhase1UnitTest {

    private lateinit var runtimePrivateKeyB64: String
    private lateinit var runtimePublicKeyB64: String
    private lateinit var runtimeKeyRing: Map<String, String>

    @Before
    fun setUp() {
        PasswordHasher.resetFailedAttempts()
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()
        runtimePrivateKeyB64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        runtimePublicKeyB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        runtimeKeyRing = mapOf(LicenseSecurityManager.DEFAULT_KEY_ID to runtimePublicKeyB64)
    }

    // =========================================================================
    // 1. PasswordHasher Tests
    // =========================================================================

    @Test
    fun testPasswordHashingAndVerification() = runBlocking {
        val rawPassword = "StrongAdminPass#2026"
        val hash = PasswordHasher.hashAsync(rawPassword)

        val parts = hash.split(":")
        assertEquals(3, parts.size)
        assertEquals("600000", parts[0])

        assertTrue(PasswordHasher.verifyAsync(rawPassword, hash))
        assertFalse(PasswordHasher.verifyAsync("WrongPass123", hash))
        assertFalse(PasswordHasher.verifyAsync("", hash))
        assertFalse(
            "يجب رفض مقارنة كلمة المرور بالنص الصريح دون تجزئة",
            PasswordHasher.verifyAsync(rawPassword, rawPassword)
        )
    }

    @Test
    fun testSaltUniqueness() = runBlocking {
        val password = "MySecretPassword123"
        val hash1 = PasswordHasher.hashAsync(password)
        val hash2 = PasswordHasher.hashAsync(password)

        assertNotEquals(hash1, hash2)
        assertTrue(PasswordHasher.verifyAsync(password, hash1))
        assertTrue(PasswordHasher.verifyAsync(password, hash2))
    }

    @Test
    fun testLegacyPasswordCompatibilityAndRehashNeeded() = runBlocking {
        val legacyPassword = "OldLegacyPass123"
        val legacyHash = PasswordHasher.hashAsync(legacyPassword, iterations = 10000)
        val parts = legacyHash.split(":")
        val rawLegacy = "${parts[1]}:${parts[2]}"

        assertTrue(PasswordHasher.verifyAsync(legacyPassword, rawLegacy))
        assertTrue(PasswordHasher.needsRehash(rawLegacy))

        val modernHash = PasswordHasher.hashAsync("ModernPass123")
        assertFalse(PasswordHasher.needsRehash(modernHash))
    }

    @Test
    fun testBruteForceLockoutProtection() {
        assertFalse(PasswordHasher.isLockedOut())

        for (i in 1..4) {
            PasswordHasher.recordFailedAttempt()
            assertFalse(PasswordHasher.isLockedOut())
        }

        PasswordHasher.recordFailedAttempt()
        assertTrue(PasswordHasher.isLockedOut())
        assertTrue(PasswordHasher.getRemainingLockoutSeconds() > 0)

        PasswordHasher.resetFailedAttempts()
        assertFalse(PasswordHasher.isLockedOut())
        assertEquals(0L, PasswordHasher.getRemainingLockoutSeconds())
    }

    @Test
    fun testPersistentLockoutAcrossRestarts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PasswordHasher.init(context)
        PasswordHasher.resetFailedAttempts(context)
        assertFalse(PasswordHasher.isLockedOut(context))

        repeat(5) { PasswordHasher.recordFailedAttempt(context) }
        assertTrue(PasswordHasher.isLockedOut(context))

        PasswordHasher.init(context)
        assertTrue("يجب أن تبقى حالة القفل محفوظة بعد إعادة تشغيل التطبيق", PasswordHasher.isLockedOut(context))
        assertTrue(PasswordHasher.getRemainingLockoutSeconds(context) > 0)

        PasswordHasher.resetFailedAttempts(context)
        assertFalse(PasswordHasher.isLockedOut(context))
    }

    // =========================================================================
    // 2. LicenseSecurityManager Tests (Strict ECDSA + kid + Grace + Device ID)
    // =========================================================================

    @Test
    fun testCompromisedK2026V1RemovedAndK2026V2ActiveInProductionKeyRing() {
        assertEquals("k2026-v2", LicenseSecurityManager.DEFAULT_KEY_ID)
        assertTrue(
            "يجب وجود المفتاح الجديد k2026-v2 في حلقة المفاتيح المعتمدة",
            LicenseSecurityManager.TRUSTED_PUBLIC_KEYS.containsKey("k2026-v2")
        )
        assertFalse(
            "يجب إزالة المفتاح المكشوف k2026-v1 نهائياً من TRUSTED_PUBLIC_KEYS لإبطال الرموز المزوّرة",
            LicenseSecurityManager.TRUSTED_PUBLIC_KEYS.containsKey("k2026-v1")
        )

        val now = 1_795_000_000_000L
        val deviceId = "B194663012B2867D"
        val forgedOrRetiredV1Token = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = now + 365L * 86_400_000L,
            privateKeyBase64 = runtimePrivateKeyB64,
            keyId = "k2026-v1",
            graceUntilTimestamp = now + 30L * 86_400_000L,
            issuedAt = now
        )
        val res = LicenseSecurityManager.verifyLicenseToken(
            tokenString = forgedOrRetiredV1Token,
            currentDeviceId = deviceId,
            nowMillis = now
        )
        assertFalse("يجب رفض أي رمز موقّع بمعرّف المفتاح المُلغى k2026-v1", res.isValid)
    }

    @Test
    fun testProductionLicenseSecurityManagerContainsNoPrivateKeySigningMethods() {
        val methodNames = LicenseSecurityManager::class.java.declaredMethods.map { it.name }.toSet()
        assertFalse("يجب إزالة getPrivateKey من كود الإنتاج", "getPrivateKey" in methodNames)
        assertFalse("يجب إزالة generateSignedToken من كود الإنتاج", "generateSignedToken" in methodNames)
        assertFalse("يجب إزالة generateSignedRecoveryToken من كود الإنتاج", "generateSignedRecoveryToken" in methodNames)
    }

    @Test
    fun testProjectDirectoryContainsZeroPrivateKeysOrReleaseKeystores() {
        val projectRoot = if (File("../settings.gradle.kts").exists()) {
            File("..").canonicalFile
        } else {
            File(".").canonicalFile
        }
        val skipDirs = setOf(".git", ".gradle", "build", ".kotlin", ".build-outputs", ".idea")
        val allowRelPaths = setOf(
            "debug.keystore",
            "debug.keystore.base64",
            "license_public_key.pem",
            ".gitleaks.toml",
            "tools/check_secrets_precommit.py"
        )
        val pemPrivRe = Regex("-----BEGIN " + "(?:EC |RSA |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----")
        val pkcs8Re = Regex("MIGHAgEA" + "MBMGByqGSM49AgEGCCqGSM49AwEH[A-Za-z0-9+/=]{60,}")
        val sec1Re = Regex("MHcCA" + "QEEI[A-Za-z0-9+/=]{50,}")

        val violations = mutableListOf<String>()
        projectRoot.walkTopDown()
            .onEnter { dir -> dir.name !in skipDirs }
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(projectRoot).invariantSeparatorsPath
                if (rel in allowRelPaths) return@forEach
                if (file.name.endsWith(".jks", ignoreCase = true) ||
                    (file.name.contains("private", ignoreCase = true) && file.name.endsWith(".pem", ignoreCase = true))
                ) {
                    violations.add("ملف مفتاح خاص ممنوع داخل المشروع: $rel")
                    return@forEach
                }
                if (file.length() <= 2_000_000L) {
                    val bytes = file.readBytes()
                    val sampleLen = minOf(bytes.size, 4096)
                    val isBinary = (0 until sampleLen).any { bytes[it] == 0.toByte() }
                    if (!isBinary) {
                        val content = bytes.toString(Charsets.UTF_8)
                        if (pemPrivRe.containsMatchIn(content) ||
                            pkcs8Re.containsMatchIn(content) ||
                            sec1Re.containsMatchIn(content)
                        ) {
                            violations.add("مفتاح خاص مسرّب داخل الملف: $rel")
                        }
                    }
                }
            }
        assertTrue("يجب خلو مجلد المشروع والأرشيف من أي مفاتيح خاصة: $violations", violations.isEmpty())
    }

    @Test
    fun testGetDeviceIdIsDeterministic16HexWithoutSharedStaticFallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ANDROID_ID, "")
        context.getSharedPreferences("rmpos_device_identity_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val id1 = LicenseSecurityManager.getDeviceId(context)
        val id2 = LicenseSecurityManager.getDeviceId(context)

        assertEquals(id1, id2)
        assertTrue("Device ID يجب أن يكون 16 حرفاً سداسي عشر وليس $id1", id1.matches(Regex("^[0-9A-F]{16}$")))
        assertNotEquals("RM_POS_DEFAULT_DEVICE", id1)
        assertNotEquals("RMPOS-DEVICE-01", id1)

        // Simulating a second stripped device (cleared installation entropy) must NOT produce the same ID
        context.getSharedPreferences("rmpos_device_identity_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val secondDeviceId = LicenseSecurityManager.getDeviceId(context)
        assertNotEquals("يجب ألا تشترك الأجهزة الفارغة من ANDROID_ID في معرّف ثابت واحد", id1, secondDeviceId)
    }

    @Test
    fun testRootPemMatchesEmbeddedPublicKey() {
        val candidates = listOf(File("../license_public_key.pem"), File("license_public_key.pem"))
        val pemFile = candidates.firstOrNull { it.exists() }
        assertTrue("ملف license_public_key.pem يجب أن يكون موجوداً في جذر المشروع", pemFile != null)
        val pemBase64 = pemFile!!.readLines()
            .filterNot { it.startsWith("-----") }
            .joinToString("") { it.trim() }
        assertEquals(
            "يجب تطابق license_public_key.pem مع المفتاح المضمّن في التطبيق",
            LicenseSecurityManager.PUBLIC_KEY_BASE64,
            pemBase64
        )
    }

    @Test
    fun testUnsignedFormattedKeysAndPlaintextAreStrictlyRejected() {
        val sampleDeviceId = "BF60CC26FB79ED85"
        val unsignedAttempts = listOf(
            "RM.POS@BFCCFBED@60267985-365-20261118",
            "RM.POS@BFCCFBED@60267985-0-20261118",
            "RM.POS@BFCCFBED@٦٠٢٦٧٩٨٥-٣٠-٢٠٢٦١١١٨",
            "RM.POS-2026-11-18-365-RM.POS",
            "RMPOS-PRO-2026",
            "RMPOS-2026-PRO",
            "12345678",
            "ADMIN-2026",
            "RMPOS-RESET-2026",
            "RM.POS-BBD@60@194663@"
        )
        for (unsignedKey in unsignedAttempts) {
            val res = LicenseSecurityManager.verifyLicenseToken(unsignedKey, sampleDeviceId)
            assertFalse("يجب رفض الرموز غير الموقّعة نهائياً: $unsignedKey", res.isValid)

            val recRes = LicenseSecurityManager.verifyRecoveryToken(unsignedKey, sampleDeviceId)
            assertFalse("يجب رفض رموز الاسترجاع غير الموقّعة نهائياً: $unsignedKey", recRes.isValid)
        }
    }

    @Test
    fun testValidSignedLicenseTokenWithKidAndOfflineGrace() {
        val now = 1_795_000_000_000L
        val deviceId = "B194663012B2867D"
        val expires = now + 365L * 86_400_000L
        val graceUntil = now + 30L * 86_400_000L

        val signedToken = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = expires,
            privateKeyBase64 = runtimePrivateKeyB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = graceUntil,
            issuedAt = now
        )

        val res = LicenseSecurityManager.verifyLicenseToken(
            tokenString = signedToken,
            currentDeviceId = deviceId,
            nowMillis = now + 5L * 86_400_000L,
            trustedKeyRing = runtimeKeyRing
        )
        assertTrue("يجب قبول رمز التفعيل الموقّع بـ ECDSA ضمن فترة السماح", res.isValid)
        assertEquals("PRO", res.licenseType)
        assertEquals(deviceId, res.deviceId)
        assertEquals(LicenseSecurityManager.DEFAULT_KEY_ID, res.keyId)
        assertEquals(expires, res.expiryTimestamp)
        assertEquals(graceUntil, res.graceUntilTimestamp)

        // After offline grace_until elapses without online renewal, token must be rejected
        val afterGrace = LicenseSecurityManager.verifyLicenseToken(
            tokenString = signedToken,
            currentDeviceId = deviceId,
            nowMillis = graceUntil + 60_000L,
            trustedKeyRing = runtimeKeyRing
        )
        assertFalse("يجب رفض العمل دون إنترنت بعد انتهاء فترة السماح الموقّعة", afterGrace.isValid)
        assertTrue(afterGrace.needsOnlineRenewal)

        // Verify clipboard corruption (WhatsApp RTL marks \u200F, \u200E, zero-width spaces, line wraps) is sanitized cleanly
        val clipboardWrapped = "\u200F\u2066  ${signedToken.substring(0, 24)}\r\n${signedToken.substring(24)} \u2069\u200E\u200B"
        val sanitizedRes = LicenseSecurityManager.verifyLicenseToken(
            tokenString = clipboardWrapped,
            currentDeviceId = deviceId,
            nowMillis = now + 1_000L,
            trustedKeyRing = runtimeKeyRing
        )
        assertTrue("يجب قبول الرمز الموقّع حتى مع محارف الاتجاه الخفية أو الأسطر المنسوخة", sanitizedRes.isValid)
    }

    @Test
    fun testKeyRotationWithKidAcceptsActiveKeysAndRejectsRetiredKeys() {
        val now = 1_795_000_000_000L
        val deviceId = "4F3A8B1C9D2E0F5A"

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val rotatedKeyPair = kpg.generateKeyPair()
        val rotatedPrivB64 = Base64.encodeToString(rotatedKeyPair.private.encoded, Base64.NO_WRAP)
        val rotatedPubB64 = Base64.encodeToString(rotatedKeyPair.public.encoded, Base64.NO_WRAP)

        val tokenV3 = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = now + 90L * 86_400_000L,
            privateKeyBase64 = rotatedPrivB64,
            keyId = "k2027-v3",
            graceUntilTimestamp = now + 30L * 86_400_000L,
            issuedAt = now
        )

        // Unknown kid in default keyring -> rejected
        val rejectedUnknownKid = LicenseSecurityManager.verifyLicenseToken(
            tokenString = tokenV3,
            currentDeviceId = deviceId,
            nowMillis = now
        )
        assertFalse("يجب رفض رمز موقّع بمعرّف مفتاح غير معتمد", rejectedUnknownKid.isValid)

        // Accepted when k2027-v3 is in trustedKeyRing
        val multiKeyRing = mapOf(
            LicenseSecurityManager.DEFAULT_KEY_ID to LicenseSecurityManager.PUBLIC_KEY_BASE64,
            "k2027-v3" to rotatedPubB64
        )
        val acceptedRotated = LicenseSecurityManager.verifyLicenseToken(
            tokenString = tokenV3,
            currentDeviceId = deviceId,
            nowMillis = now,
            trustedKeyRing = multiKeyRing
        )
        assertTrue("يجب قبول الرمز عند تفعيل المفتاح الجديد في حلقة المفاتيح", acceptedRotated.isValid)
        assertEquals("k2027-v3", acceptedRotated.keyId)
    }

    @Test
    fun testSignedLicenseToken_DeviceMismatchAndZeroExpiryAndWildcard_Rejected() {
        val now = System.currentTimeMillis()
        val deviceA = "B194663012B2867D"
        val deviceB = "4F3A8B1C9D2E0F5A"

        val signedTokenForA = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceA,
            licenseType = "PRO",
            expiryTimestamp = now + 30L * 86_400_000L,
            privateKeyBase64 = runtimePrivateKeyB64
        )
        val mismatchRes = LicenseSecurityManager.verifyLicenseToken(
            tokenString = signedTokenForA,
            currentDeviceId = deviceB,
            nowMillis = now,
            trustedKeyRing = runtimeKeyRing
        )
        assertFalse("يجب رفض رمز التفعيل إذا كان معرّف الجهاز غير مطابق", mismatchRes.isValid)

        var zeroExpiryThrown = false
        try {
            TestLicenseTokenSigner.generateSignedToken(
                deviceId = deviceA,
                licenseType = "PRO",
                expiryTimestamp = 0L,
                privateKeyBase64 = runtimePrivateKeyB64
            )
        } catch (_: IllegalArgumentException) {
            zeroExpiryThrown = true
        }
        assertTrue("يجب حظر إصدار رمز بدون تاريخ انتهاء موجب (expires <= 0)", zeroExpiryThrown)

        var wildcardThrown = false
        try {
            TestLicenseTokenSigner.generateSignedToken(
                deviceId = "*",
                licenseType = "PRO",
                expiryTimestamp = now + 86_400_000L,
                privateKeyBase64 = runtimePrivateKeyB64
            )
        } catch (_: IllegalArgumentException) {
            wildcardThrown = true
        }
        assertTrue("يجب حظر إصدار رمز بجهاز عام *", wildcardThrown)
    }

    @Test
    fun testExpiredSignedLicenseToken_Rejected() {
        val now = System.currentTimeMillis()
        val deviceId = "B194663012B2867D"
        val expiredTime = now - 100_000L

        val expiredToken = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "SUBSCRIPTION_30D",
            expiryTimestamp = expiredTime,
            privateKeyBase64 = runtimePrivateKeyB64,
            issuedAt = expiredTime - 86_400_000L
        )

        val res = LicenseSecurityManager.verifyLicenseToken(
            tokenString = expiredToken,
            currentDeviceId = deviceId,
            nowMillis = now,
            trustedKeyRing = runtimeKeyRing
        )
        assertFalse("يجب رفض رمز التفعيل منتهي الصلاحية", res.isValid)
    }

    @Test
    fun testRecoveryTokenReplayPrevention() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyToken = "eyJkZXZpY2VfaWQiOiJURVNUIiwidHlwZSI6IlBSTyJ9.dummySig123"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = Base64.encodeToString(md.digest(dummyToken.toByteArray()), Base64.NO_WRAP)

        assertFalse(LicenseSecurityManager.isRecoveryTokenConsumed(context, hash))
        LicenseSecurityManager.consumeRecoveryToken(context, dummyToken)
        assertTrue(LicenseSecurityManager.isRecoveryTokenConsumed(context, hash))
    }

    // =========================================================================
    // 3. BackupEncryptionManager Tests (AES-256-GCM with >= 600,000 iterations)
    // =========================================================================

    @Test
    fun testBackupEncryptionAndDecryptionSuccess() = runBlocking {
        val sampleJson = """{"database":"POS","invoices":[{"id":101,"total":4500.5}],"users":["admin"]}"""
        val passphrase = "SafeBackupPass#987"

        val encrypted = BackupEncryptionManager.encryptBackup(sampleJson, passphrase)
        assertTrue(BackupEncryptionManager.isEncryptedBackup(encrypted))
        assertTrue(encrypted.startsWith(BackupEncryptionManager.MAGIC_HEADER_PREFIX))

        val result = BackupEncryptionManager.decryptBackup(encrypted, passphrase)
        assertTrue(result is BackupEncryptionManager.DecryptionResult.Success)
        val success = result as BackupEncryptionManager.DecryptionResult.Success
        assertEquals(sampleJson, success.jsonContent)
    }

    @Test
    fun testBackupDecryptionWithWrongPasswordFails() = runBlocking {
        val sampleJson = """{"secret_data": "top_secret_pos_records"}"""
        val encrypted = BackupEncryptionManager.encryptBackup(sampleJson, "CorrectPassword123")
        val result = BackupEncryptionManager.decryptBackup(encrypted, "WrongPassword456")

        assertTrue(result is BackupEncryptionManager.DecryptionResult.Error)
        assertTrue((result as BackupEncryptionManager.DecryptionResult.Error).message.isNotEmpty())
    }

    @Test
    fun testBackupDecryptionWithCorruptedCiphertextFails() = runBlocking {
        val sampleJson = """{"data": "pos_records"}"""
        val pass = "Password123"
        val encrypted = BackupEncryptionManager.encryptBackup(sampleJson, pass)
        val lines = encrypted.lines().toMutableList()
        lines[2] = lines[2].reversed()

        val result = BackupEncryptionManager.decryptBackup(lines.joinToString("\n"), pass)
        assertTrue(result is BackupEncryptionManager.DecryptionResult.Error)
    }

    @Test
    fun testUnencryptedPlaintextBackupIsRejected() = runBlocking {
        val legacyPlainJson = """{"version":1,"products":[{"id":1,"name":"Milk"}]}"""
        assertFalse(BackupEncryptionManager.isEncryptedBackup(legacyPlainJson))

        val result = BackupEncryptionManager.decryptBackup(legacyPlainJson, "any_pass")
        assertTrue(
            "يجب رفض استعادة النسخ غير المشفرة بالنص العادي",
            result is BackupEncryptionManager.DecryptionResult.Error
        )
    }

    // =========================================================================
    // 4. HTML Escaping Tests (XSS Prevention in Invoices)
    // =========================================================================

    @Test
    fun testHtmlEscaping() {
        val xssInput = "<script>alert('XSS')</script> & \"Milk\" / 'Bread'"
        val escaped = com.example.printer.BluetoothPrinterService.escapeHtml(xssInput)

        assertFalse(escaped.contains("<"))
        assertFalse(escaped.contains(">"))
        assertFalse(escaped.contains("\""))
        assertTrue(escaped.contains("&lt;script&gt;"))
        assertTrue(escaped.contains("&amp;"))
        assertTrue(escaped.contains("&quot;Milk&quot;"))
        assertTrue(escaped.contains("&#x27;Bread&#x27;"))
    }
}
