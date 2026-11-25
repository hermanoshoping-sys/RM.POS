package com.example.security

import android.content.Context
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise Backup Encryption & Decryption using AES-256-GCM.
 *
 * Supports two encrypted formats:
 * 1. User passphrase-protected external backups (`RMPOS_ENCRYPTED_BACKUP_V1`, PBKDF2-HMAC-SHA256 600k iters + AES-256-GCM).
 * 2. Device Keystore-protected automatic & safety backups (`RMPOS_KEYSTORE_BACKUP_V1`, AES-256-GCM wrapped by Android Keystore MasterKey).
 */
object BackupEncryptionManager {
    const val MAGIC_HEADER_PREFIX = "RMPOS_ENCRYPTED_BACKUP_V1"
    const val KEYSTORE_MAGIC_HEADER_PREFIX = "RMPOS_KEYSTORE_BACKUP_V1"

    private const val PBKDF2_ITERATIONS = 600000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private const val SYS_BACKUP_PREFS = "rmpos_sys_backup_key_prefs"
    private const val KEY_WRAPPED_DATA_KEY = "wrapped_sys_backup_key"
    private const val KEY_WRAP_IV = "wrapped_sys_backup_iv"
    private const val KEY_ALIAS = "RMPOS_SYS_BACKUP_MASTER_KEY"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun isEncryptedBackup(rawBytes: ByteArray): Boolean {
        if (rawBytes.size < MAGIC_HEADER_PREFIX.length) return false
        val prefix = String(rawBytes, 0, MAGIC_HEADER_PREFIX.length, StandardCharsets.UTF_8)
        return prefix == MAGIC_HEADER_PREFIX
    }

    fun isEncryptedBackup(rawString: String): Boolean {
        return rawString.trimStart().startsWith(MAGIC_HEADER_PREFIX)
    }

    fun isEncryptedSystemBackup(rawString: String): Boolean {
        return rawString.trimStart().startsWith(KEYSTORE_MAGIC_HEADER_PREFIX)
    }

    fun isAnyEncryptedBackup(rawString: String): Boolean {
        val trimmed = rawString.trimStart()
        return trimmed.startsWith(MAGIC_HEADER_PREFIX) || trimmed.startsWith(KEYSTORE_MAGIC_HEADER_PREFIX)
    }

    /**
     * Encrypts plaintext JSON database backup using AES-256-GCM and a user-provided passphrase.
     */
    suspend fun encryptBackup(plainJson: String, password: CharSequence): String = withContext(Dispatchers.Default) {
        val passStr = password.toString()
        require(passStr.isNotBlank()) { "كلمة مرور النسخة الاحتياطية لا يمكن أن تكون فارغة" }

        val salt = ByteArray(SALT_LENGTH_BYTES)
        val iv = ByteArray(IV_LENGTH_BYTES)
        val random = SecureRandom()
        random.nextBytes(salt)
        random.nextBytes(iv)

        val keySpec = PBEKeySpec(passStr.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val ciphertext = cipher.doFinal(plainJson.toByteArray(StandardCharsets.UTF_8))

        val meta = JSONObject().apply {
            put("version", 1)
            put("cipher", "AES-256-GCM")
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", PBKDF2_ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("timestamp", System.currentTimeMillis())
        }
        val metaB64 = Base64.encodeToString(meta.toString().toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        "$MAGIC_HEADER_PREFIX\n$metaB64\n$cipherB64"
    }

    /**
     * Encrypts unattended internal automatic backups & safety backups using AES-256-GCM
     * with a 256-bit data key protected by Android Keystore.
     */
    suspend fun encryptSystemBackup(context: Context, plainJson: String): String = withContext(Dispatchers.IO) {
        val dataKeyBytes = getOrCreateSystemBackupKey(context)
        val secretKey = SecretKeySpec(dataKeyBytes, "AES")
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plainJson.toByteArray(StandardCharsets.UTF_8))

        val meta = JSONObject().apply {
            put("version", 1)
            put("cipher", "AES-256-GCM")
            put("kdf", "ANDROID_KEYSTORE")
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("timestamp", System.currentTimeMillis())
        }
        val metaB64 = Base64.encodeToString(meta.toString().toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        "$KEYSTORE_MAGIC_HEADER_PREFIX\n$metaB64\n$cipherB64"
    }

    /**
     * Decrypts internal automatic/safety backup encrypted via [encryptSystemBackup].
     * Strictly rejects plaintext JSON backups.
     */
    suspend fun decryptSystemBackup(context: Context, encryptedContent: String): DecryptionResult = withContext(Dispatchers.IO) {
        val trimmed = encryptedContent.trim()
        if (!isEncryptedSystemBackup(trimmed)) {
            return@withContext DecryptionResult.Error("تم رفض النسخة التلقائية: النسخ غير المشفرة غير مدعومة لأسباب أمنية")
        }
        try {
            val lines = trimmed.lines().filter { it.isNotBlank() }
            if (lines.size < 3) {
                return@withContext DecryptionResult.Error("ملف النسخة الاحتياطية التلقائية تالف أو غير مكتمل")
            }
            val metaJsonStr = String(Base64.decode(lines[1], Base64.NO_WRAP), StandardCharsets.UTF_8)
            val meta = JSONObject(metaJsonStr)
            val iv = Base64.decode(meta.getString("iv"), Base64.NO_WRAP)
            val cipherBytes = Base64.decode(lines[2], Base64.NO_WRAP)

            val dataKeyBytes = getOrCreateSystemBackupKey(context)
            val secretKey = SecretKeySpec(dataKeyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            val decryptedBytes = cipher.doFinal(cipherBytes)
            DecryptionResult.Success(String(decryptedBytes, StandardCharsets.UTF_8))
        } catch (e: javax.crypto.AEADBadTagException) {
            DecryptionResult.Error("فشل التحقق من سلامة النسخة التلقائية أو تم تعديلها")
        } catch (e: Exception) {
            DecryptionResult.Error("فشل فك تشفير النسخة التلقائية: ${e.localizedMessage}")
        }
    }

    @Synchronized
    private fun getOrCreateSystemBackupKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(SYS_BACKUP_PREFS, Context.MODE_PRIVATE)
        val storedB64 = prefs.getString(KEY_WRAPPED_DATA_KEY, null)
        val storedIvB64 = prefs.getString(KEY_WRAP_IV, null)

        val useTestFallback = SecurityRuntimeConfig.isJvmTestMode() ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M

        if (storedB64 != null) {
            return if (!useTestFallback && storedIvB64 != null) {
                val masterKey = getOrCreateBackupMasterKey()
                val iv = Base64.decode(storedIvB64, Base64.NO_WRAP)
                val encrypted = Base64.decode(storedB64, Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                cipher.doFinal(encrypted)
            } else if (useTestFallback && storedIvB64 == null) {
                Base64.decode(storedB64, Base64.NO_WRAP)
            } else {
                // Attempt Keystore unwrap even if environment flags differ
                try {
                    val masterKey = getOrCreateBackupMasterKey()
                    val iv = Base64.decode(storedIvB64, Base64.NO_WRAP)
                    val encrypted = Base64.decode(storedB64, Base64.NO_WRAP)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                    cipher.doFinal(encrypted)
                } catch (_: Exception) {
                    Base64.decode(storedB64, Base64.NO_WRAP)
                }
            }
        }

        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        if (!useTestFallback) {
            val masterKey = getOrCreateBackupMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(rawKey)
            prefs.edit()
                .putString(KEY_WRAPPED_DATA_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_WRAP_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .commit()
        } else {
            prefs.edit()
                .putString(KEY_WRAPPED_DATA_KEY, Base64.encodeToString(rawKey, Base64.NO_WRAP))
                .remove(KEY_WRAP_IV)
                .commit()
        }
        return rawKey
    }

    private fun getOrCreateBackupMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }
        val keyGen = KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_LENGTH_BITS)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    sealed class DecryptionResult {
        data class Success(val jsonContent: String) : DecryptionResult()
        data class Error(val message: String) : DecryptionResult()
    }

    /**
     * Decrypts encrypted backup using the user-provided passphrase.
     * Strictly rejects plaintext unencrypted backups and enforces user passphrase.
     */
    suspend fun decryptBackup(encryptedContent: String, password: CharSequence): DecryptionResult = withContext(Dispatchers.Default) {
        val trimmed = encryptedContent.trim()
        if (!isEncryptedBackup(trimmed)) {
            return@withContext DecryptionResult.Error("تم رفض استعادة النسخة: النسخ غير المشفرة بالنص العادي غير مقبولة لأسباب أمنية")
        }

        val passStr = password.toString()
        if (passStr.isBlank()) {
            return@withContext DecryptionResult.Error("كلمة مرور فك تشفير النسخة الاحتياطية إلزامية")
        }

        try {
            val lines = trimmed.lines().filter { it.isNotBlank() }
            if (lines.size < 3) {
                return@withContext DecryptionResult.Error("ملف النسخة الاحتياطية تالف أو غير مكتمل")
            }

            val metaJsonStr = String(Base64.decode(lines[1], Base64.NO_WRAP), StandardCharsets.UTF_8)
            val meta = JSONObject(metaJsonStr)
            val iterations = meta.optInt("iterations", PBKDF2_ITERATIONS)
            val salt = Base64.decode(meta.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(meta.getString("iv"), Base64.NO_WRAP)
            val cipherBytes = Base64.decode(lines[2], Base64.NO_WRAP)

            val keySpec = PBEKeySpec(passStr.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(keySpec).encoded
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            val decryptedBytes = cipher.doFinal(cipherBytes)
            val plainJson = String(decryptedBytes, StandardCharsets.UTF_8)
            DecryptionResult.Success(plainJson)
        } catch (e: javax.crypto.AEADBadTagException) {
            DecryptionResult.Error("كلمة مرور النسخة الاحتياطية غير صحيحة، أو الملف تالف")
        } catch (e: Exception) {
            DecryptionResult.Error("فشل فك تشفير النسخة الاحتياطية: ${e.localizedMessage}")
        }
    }
}
