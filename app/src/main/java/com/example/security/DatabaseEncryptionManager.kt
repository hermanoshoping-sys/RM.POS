package com.example.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * =========================================================================================
 * مدير تشفير قاعدة البيانات (DatabaseEncryptionManager)
 * يوفر تشفيراً شفافاً وشاملاً لقاعدة بيانات Room على مستوى الصفحات والملف (AES-256 via SQLCipher).
 * يدير مفاتيح التشفير بأمان باستخدام Android Keystore، مع استبدال ذري آمن واسترداد تلقائي
 * في حال انقطاع الترحيل دون تعريض قاعدة البيانات الأصلية للفقدان.
 * =========================================================================================
 */
object DatabaseEncryptionManager {

    private const val TAG = "DatabaseEncryption"
    private const val PREFS_NAME = "rmpos_db_security_prefs"
    private const val KEY_ENCRYPTED_PASSPHRASE = "enc_db_passphrase"
    private const val KEY_IV = "enc_db_iv"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "RMPOS_DB_MASTER_KEY"

    internal const val ENCRYPTED_TEMP_SUFFIX = "_encrypted_temp"
    internal const val BACKUP_PLAIN_SUFFIX = "_unencrypted_migrated_backup"

    @Volatile
    private var isNativeLibraryLoaded = false

    enum class StartupRecoveryAction {
        NONE,
        RESTORED_FROM_BACKUP,
        PROMOTED_VERIFIED_TEMP,
        CLEANED_INVALID_OR_ORPHAN_TEMP,
        CLEANED_POST_SWAP_BACKUP
    }

    /**
     * التحقق من صلاحية ملف قاعدة بيانات مشفّر قبل حذفه أو اعتماده.
     */
    internal fun interface EncryptedFileVerifier {
        fun isValidEncryptedDatabase(file: File): Boolean
    }

    /**
     * خطافات داخلية لاختبار انقطاع الترحيل بعد كل خطوة حرجة والتأكد من سلامة البيانات عند إعادة التشغيل.
     */
    internal interface MigrationStepHook {
        fun onAfterExport(plainDbFile: File, encryptedTempFile: File) {}
        fun onAfterBackupCopy(plainDbFile: File, backupPlainFile: File, encryptedTempFile: File) {}
        fun onAfterSidecarDelete(plainDbFile: File, backupPlainFile: File, encryptedTempFile: File) {}
        fun onAfterSwapBeforeCleanup(dbFile: File, backupPlainFile: File) {}
    }

    internal val defaultEncryptedVerifier = EncryptedFileVerifier { file ->
        file.exists() && file.length() >= 16L && isDatabaseFileEncrypted(file)
    }

    /**
     * تحميل مكتبة SQLCipher C/C++ (libsqlcipher.so) إلى الذاكرة بأمان
     */
    @Synchronized
    fun loadNativeLibrary(): Boolean {
        if (isNativeLibraryLoaded) return true
        if (SecurityRuntimeConfig.isJvmTestMode()) return false
        return try {
            System.loadLibrary("sqlcipher")
            isNativeLibraryLoaded = true
            Log.i(TAG, "تم تحميل مكتبة SQLCipher الأصلية (libsqlcipher.so) بنجاح")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "تعذر تحميل مكتبة SQLCipher الأصلية: ${t.message}", t)
            false
        }
    }

    fun isNativeLoaded(): Boolean = isNativeLibraryLoaded

    /**
     * جلب أو إنشاء مفتاح تشفير عشوائي آمن بطول 256 بت (32 بايت)
     * مشفر ومحمي داخل Android KeyStore.
     */
    @Synchronized
    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedPassphraseBase64 = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
        val ivBase64 = prefs.getString(KEY_IV, null)

        if (encryptedPassphraseBase64 != null && ivBase64 != null) {
            try {
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val encryptedBytes = Base64.decode(encryptedPassphraseBase64, Base64.NO_WRAP)
                val secretKey = getOrCreateMasterKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                return cipher.doFinal(encryptedBytes)
            } catch (e: Exception) {
                Log.e(TAG, "فشل فك تشفير المفتاح المخزن في KeyStore: ${e.message}", e)
                throw IllegalStateException(
                    "فشل أمني حرج: تعذر فك تشفير مفتاح قاعدة البيانات من Android KeyStore. تم إيقاف العملية لحماية البيانات ومنع تلفها أو استبدال المفتاح: ${e.message}",
                    e
                )
            }
        }

        // إنشاء مفتاح جديد قوي بطول 32 بايت (256 بت)
        val rawPassphrase = ByteArray(32)
        SecureRandom().nextBytes(rawPassphrase)

        try {
            val secretKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(rawPassphrase)

            val committed = prefs.edit()
                .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .commit()

            if (!committed) {
                throw IllegalStateException("فشل أمني حرج: تعذر حفظ مفتاح تشفير قاعدة البيانات في التخزين الدائم (commit failed)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "تحذير: فشل حفظ المفتاح في KeyStore: ${e.message}", e)
            throw e
        }

        return rawPassphrase
    }

    /**
     * إنشاء أو جلب المفتاح الرئيسي من Android KeyStore
     */
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
            val keyGenSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: throw IllegalStateException("تعذر جلب المفتاح الرئيسي من KeyStore")
    }

    /**
     * إنشاء مصنع فتح قاعدة البيانات المشفرة لـ Room باستخدام SQLCipher
     */
    fun createSupportFactory(context: Context): SupportSQLiteOpenHelper.Factory? {
        if (!loadNativeLibrary()) {
            Log.w(TAG, "مكتبة SQLCipher الأصلية غير محملة، تعذر إنشاء مصنع التشفير")
            return null
        }
        return try {
            val passphrase = getOrCreatePassphrase(context)
            SupportOpenHelperFactory(passphrase)
        } catch (t: Throwable) {
            Log.e(TAG, "فشل تهيئة SupportOpenHelperFactory: ${t.message}", t)
            null
        }
    }

    /**
     * فحص هل ملف قاعدة البيانات مشفر أم غير مشفر
     */
    fun isDatabaseFileEncrypted(dbFile: File): Boolean {
        if (!dbFile.exists() || dbFile.length() < 16L) return false
        return try {
            val header = ByteArray(16)
            val readCount = dbFile.inputStream().use { it.read(header) }
            if (readCount < 16) return false
            val headerString = String(header, Charsets.US_ASCII)
            // الملفات غير المشفرة في SQLite تبدأ دائماً بـ "SQLite format 3\u0000"
            !headerString.startsWith("SQLite format 3")
        } catch (_: Exception) {
            false
        }
    }

    internal fun encryptedTempFileFor(dbFile: File): File =
        File(dbFile.parentFile, "${dbFile.name}$ENCRYPTED_TEMP_SUFFIX")

    internal fun backupPlainFileFor(dbFile: File): File =
        File(dbFile.parentFile, "${dbFile.name}$BACKUP_PLAIN_SUFFIX")

    /**
     * استرداد حالة الإقلاع قبل إنشاء أو فتح أي قاعدة بيانات جديدة:
     * 1. إذا غاب ملف القاعدة الرئيسي ووُجدت نسخة الاحتياط، تُستعاد النسخة الاحتياطية فوراً قبل إنشاء أي قاعدة فارغة.
     * 2. إذا وُجد ملف مشفّر مؤقت (_encrypted_temp)، يتم فحصه والتحقق منه قبل حذفه أو ترقيته إذا غاب الملف الرئيسي.
     * 3. إذا كان الملف الرئيسي موجوداً ومشفّراً وبقيت نسخة احتياطية قديمة (انقطاع بعد التبديل وقبل التنظيف)، تُحذف النسخة الاحتياطية بأمان.
     */
    fun recoverInterruptedMigrationIfNeeded(dbFile: File): StartupRecoveryAction =
        recoverInterruptedMigrationIfNeeded(dbFile, defaultEncryptedVerifier)

    internal fun recoverInterruptedMigrationIfNeeded(
        dbFile: File,
        verifier: EncryptedFileVerifier = defaultEncryptedVerifier
    ): StartupRecoveryAction {
        if (dbFile.parentFile == null) return StartupRecoveryAction.NONE
        val backupPlainFile = backupPlainFileFor(dbFile)
        val encryptedTempFile = encryptedTempFileFor(dbFile)

        // 1. غياب الملف الرئيسي مع وجود النسخة الاحتياطية: استعادة فورية قبل إنشاء أي قاعدة جديدة
        if (!dbFile.exists() && backupPlainFile.exists() && backupPlainFile.length() > 0L) {
            deleteSqliteSidecarFiles(dbFile)
            val restored = backupPlainFile.renameTo(dbFile) || copyFileWithSync(backupPlainFile, dbFile)
            if (restored) {
                if (backupPlainFile.exists()) backupPlainFile.delete()
                deleteSqliteSidecarFiles(backupPlainFile)
                // فحص الملف المؤقت قبل حذفه حتى لا يبقى ملف يتيم
                if (encryptedTempFile.exists()) {
                    val tempValid = verifier.isValidEncryptedDatabase(encryptedTempFile)
                    Log.w(TAG, "تم فحص الملف المشفّر المؤقت بعد استعادة الاحتياطي (valid=$tempValid) وسيُزال لتفادي التعارض")
                    encryptedTempFile.delete()
                    deleteSqliteSidecarFiles(encryptedTempFile)
                }
                Log.w(TAG, "تمت استعادة ملف قاعدة البيانات من النسخة الاحتياطية عند الإقلاع بنجاح")
                return StartupRecoveryAction.RESTORED_FROM_BACKUP
            }
        }

        // 2. وجود ملف مشفّر مؤقت متبقٍ من محاولة سابقة منقطعة: التحقق منه قبل أي حذف
        if (encryptedTempFile.exists()) {
            val isTempValid = verifier.isValidEncryptedDatabase(encryptedTempFile)
            if (!dbFile.exists() && isTempValid) {
                deleteSqliteSidecarFiles(dbFile)
                if (encryptedTempFile.renameTo(dbFile)) {
                    deleteSqliteSidecarFiles(encryptedTempFile)
                    if (backupPlainFile.exists()) {
                        backupPlainFile.delete()
                        deleteSqliteSidecarFiles(backupPlainFile)
                    }
                    Log.w(TAG, "تمت ترقية الملف المشفّر المؤقت الصالح إلى قاعدة البيانات الرئيسية عند الإقلاع")
                    return StartupRecoveryAction.PROMOTED_VERIFIED_TEMP
                }
            } else {
                Log.w(
                    TAG,
                    "وُجد ملف مشفّر مؤقت متبقٍ عند الإقلاع (dbExists=${dbFile.exists()}, tempValid=$isTempValid)؛ سيتم حذفه بأمان مع الحفاظ على القاعدة الأصلية"
                )
                encryptedTempFile.delete()
                deleteSqliteSidecarFiles(encryptedTempFile)
                if (dbFile.exists() && backupPlainFile.exists() && !isDatabaseFileEncrypted(dbFile)) {
                    backupPlainFile.delete()
                    deleteSqliteSidecarFiles(backupPlainFile)
                }
                return StartupRecoveryAction.CLEANED_INVALID_OR_ORPHAN_TEMP
            }
        }

        // 3. انقطاع سابق حدث مباشرة بعد نجاح التبديل وقبل حذف النسخة الاحتياطية
        if (dbFile.exists() && isDatabaseFileEncrypted(dbFile) && backupPlainFile.exists()) {
            backupPlainFile.delete()
            deleteSqliteSidecarFiles(backupPlainFile)
            Log.i(TAG, "تم تنظيف النسخة الاحتياطية المؤقتة المتبقية بعد اكتمال التشفير في تشغيل سابق")
            return StartupRecoveryAction.CLEANED_POST_SWAP_BACKUP
        }

        return StartupRecoveryAction.NONE
    }

    /**
     * نسخ موثوق مع مزامنة القرص (fsync) والتحقق من تطابق الحجم.
     */
    private fun copyFileWithSync(source: File, destination: File): Boolean {
        return try {
            if (destination.exists()) destination.delete()
            FileInputStream(source).use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            destination.exists() && destination.length() == source.length()
        } catch (e: Throwable) {
            Log.e(TAG, "فشل نسخ الملف الاحتياطي بأمان: ${e.message}", e)
            if (destination.exists()) destination.delete()
            false
        }
    }

    /**
     * واجهة داخلية لتجريد خطوة التصدير الفعلي بـ SQLCipher لإتاحة اختبار تدفق الترقية بالكامل
     * وحقن انقطاع متعمد بعد كل خطوة.
     */
    internal fun interface SqlCipherExporter {
        fun exportPlaintextToEncrypted(
            plainDbFile: File,
            encryptedTempFile: File,
            hexKey: String
        )
    }

    private val defaultSqlCipherExporter = SqlCipherExporter { plainDbFile, encryptedTempFile, hexKey ->
        val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
            plainDbFile.absolutePath,
            null,
            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE
        )
        try {
            // 1. تفريغ سجل WAL داخل الملف الرئيسي لضمان عدم ضياع أي معاملات حديثة
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).use { it.moveToFirst() }

            // 2. قراءة رقم إصدار Room الحالي لأن sqlcipher_export لا ينقل PRAGMA user_version تلقائياً
            val sourceUserVersion = db.rawQuery("PRAGMA user_version;", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }

            // 3. تصدير كافة الجداول والفهارس والبيانات إلى قاعدة البيانات المشفرة AES-256
            val escapedTargetPath = encryptedTempFile.absolutePath.replace("'", "''")
            db.rawExecSQL("ATTACH DATABASE '$escapedTargetPath' AS encrypted KEY \"x'$hexKey'\";")
            try {
                db.rawExecSQL("SELECT sqlcipher_export('encrypted');")
                if (sourceUserVersion > 0) {
                    db.rawExecSQL("PRAGMA encrypted.user_version = $sourceUserVersion;")
                }
            } finally {
                db.rawExecSQL("DETACH DATABASE encrypted;")
            }
        } finally {
            db.close()
        }
    }

    /**
     * حذف ملفات SQLite الجانبية (-wal / -shm / -journal) لتجنب تلف القاعدة المشفرة الجديدة
     * أو بقاء آثار غير مشفرة على القرص.
     */
    internal fun deleteSqliteSidecarFiles(dbFile: File) {
        val parent = dbFile.parentFile ?: return
        val baseName = dbFile.name
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            val sidecar = File(parent, "$baseName$suffix")
            if (sidecar.exists()) {
                sidecar.delete()
            }
        }
    }

    /**
     * ترحيل قاعدة البيانات القديمة غير المشفرة إلى قاعدة بيانات مشفرة بنظام AES-256 (SQLCipher):
     * - لا يتم نقل الملف الأصلي بعيداً قبل التبديل مطلقاً.
     * - يُنسخ الملف الأصلي احتياطياً مع fsync.
     * - تُحذف ملفات -wal و -shm الخاصة بالقاعدة القديمة قبل التبديل.
     * - يُستبدل الملف المشفّر فوق الأصلي بعملية renameTo ذرية واحدة.
     */
    fun migrateUnencryptedDatabaseToEncrypted(
        context: Context,
        plainDbFile: File,
        passphrase: ByteArray
    ): Boolean = migrateUnencryptedDatabaseToEncrypted(
        plainDbFile = plainDbFile,
        passphrase = passphrase,
        exporter = defaultSqlCipherExporter
    )

    internal fun migrateUnencryptedDatabaseToEncrypted(
        plainDbFile: File,
        passphrase: ByteArray,
        exporter: SqlCipherExporter = defaultSqlCipherExporter,
        verifier: EncryptedFileVerifier = defaultEncryptedVerifier,
        stepHook: MigrationStepHook? = null
    ): Boolean {
        // استرداد أي حالة منقطعة من تشغيل سابق قبل الشروع في الترحيل
        recoverInterruptedMigrationIfNeeded(plainDbFile, verifier)

        if (!plainDbFile.exists() || isDatabaseFileEncrypted(plainDbFile)) return true
        if (plainDbFile.parentFile == null) return false

        val encryptedTempFile = encryptedTempFileFor(plainDbFile)
        val backupPlainFile = backupPlainFileFor(plainDbFile)
        if (encryptedTempFile.exists()) encryptedTempFile.delete()
        deleteSqliteSidecarFiles(encryptedTempFile)

        return try {
            // 1. تصدير البيانات إلى ملف مؤقت مشفر
            val hexKey = passphrase.joinToString("") { "%02x".format(it) }
            exporter.exportPlaintextToEncrypted(plainDbFile, encryptedTempFile, hexKey)
            stepHook?.onAfterExport(plainDbFile, encryptedTempFile)

            // 2. التحقق الصارم من صلاحية وتشفير الملف المؤقت قبل لمس القاعدة الأصلية
            if (!verifier.isValidEncryptedDatabase(encryptedTempFile)) {
                Log.e(TAG, "فشل التحقق من أن الملف المرحّل مشفر فعلياً؛ تم إلغاء التبديل حفاظاً على البيانات")
                if (encryptedTempFile.exists()) encryptedTempFile.delete()
                deleteSqliteSidecarFiles(encryptedTempFile)
                return false
            }

            // 3. نسخ الملف الأصلي احتياطياً (دون نقله بعيداً أبداً) مع مزامنة القرص
            if (!copyFileWithSync(plainDbFile, backupPlainFile)) {
                Log.e(TAG, "تعذر إنشاء نسخة احتياطية مطابقة لقاعدة البيانات الأصلية قبل التبديل")
                if (encryptedTempFile.exists()) encryptedTempFile.delete()
                deleteSqliteSidecarFiles(encryptedTempFile)
                return false
            }
            stepHook?.onAfterBackupCopy(plainDbFile, backupPlainFile, encryptedTempFile)

            // 4. حذف ملفات -wal و -shm و -journal الخاصة بالقاعدة القديمة قبل التبديل مباشرة
            //    لأن بقاءها بجانب القاعدة المشفّرة الجديدة قد يفسدها
            deleteSqliteSidecarFiles(plainDbFile)
            stepHook?.onAfterSidecarDelete(plainDbFile, backupPlainFile, encryptedTempFile)

            // 5. استبدال الملف المشفّر فوق الملف الأصلي بعملية renameTo ذرية واحدة
            val swapped = encryptedTempFile.renameTo(plainDbFile)
            if (!swapped) {
                Log.e(TAG, "فشل استبدال ملف قاعدة البيانات بعملية renameTo؛ القاعدة الأصلية لا تزال محفوظة")
                if (!plainDbFile.exists() && backupPlainFile.exists()) {
                    backupPlainFile.renameTo(plainDbFile)
                }
                if (encryptedTempFile.exists()) encryptedTempFile.delete()
                deleteSqliteSidecarFiles(encryptedTempFile)
                if (plainDbFile.exists() && !isDatabaseFileEncrypted(plainDbFile) && backupPlainFile.exists()) {
                    backupPlainFile.delete()
                }
                return false
            }

            stepHook?.onAfterSwapBeforeCleanup(plainDbFile, backupPlainFile)

            // 6. التنظيف النهائي بعد تأكيد نجاح التبديل فقط
            deleteSqliteSidecarFiles(encryptedTempFile)
            if (backupPlainFile.exists()) {
                backupPlainFile.delete()
                deleteSqliteSidecarFiles(backupPlainFile)
            }
            Log.i(TAG, "تم بنجاح ترحيل وتشفير قاعدة البيانات بنظام AES-256 عبر استبدال ذري آمن")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "فشل ترحيل وتشفير قاعدة البيانات: ${e.message}", e)
            // إذا كان التبديل قد تم بالفعل قبل الاستثناء، نحافظ على القاعدة المشفّرة وننظّف النسخة الاحتياطية فقط
            if (plainDbFile.exists() && isDatabaseFileEncrypted(plainDbFile)) {
                if (backupPlainFile.exists()) {
                    backupPlainFile.delete()
                    deleteSqliteSidecarFiles(backupPlainFile)
                }
                deleteSqliteSidecarFiles(encryptedTempFile)
                return true
            }
            // في حال حدوث فشل قبل اكتمال التبديل: نضمن بقاء القاعدة الأصلية سليمة
            if (!plainDbFile.exists() && backupPlainFile.exists()) {
                backupPlainFile.renameTo(plainDbFile)
            }
            if (encryptedTempFile.exists()) encryptedTempFile.delete()
            deleteSqliteSidecarFiles(encryptedTempFile)
            if (plainDbFile.exists() && backupPlainFile.exists()) {
                backupPlainFile.delete()
                deleteSqliteSidecarFiles(backupPlainFile)
            }
            false
        }
    }
}
