package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * =========================================================================================
 * دليل إرشادي لكتابة وتطبيق Migration جديدة عند تعديل الـ Entities في قاعدة بيانات Room:
 * =========================================================================================
 * 1. رفع رقم الإصدار (version):
 *    - عند إضافة جدول جديد أو عمود جديد أو تعديل أي Entity، قم بزيادة `CURRENT_SCHEMA_VERSION`
 *      في التعليمة `@Database(..., version = AppDatabase.CURRENT_SCHEMA_VERSION, exportSchema = true)`.
 *
 * 2. كتابة كائن الهجرة (Migration Object):
 *    - عرّف كائناً جديداً يرث من `Migration(oldVersion, newVersion)`.
 *    - تُؤخذ نسخة احتياطية كاملة (الملف الرئيسي + `-wal` + `-shm`) قبل فتح Room تلقائياً في
 *      `backupDatabaseBeforeOpenIfMigrationNeeded`، بالإضافة إلى `backupDatabaseBeforeMigration`.
 *
 * 3. تسجيل الهجرة في Database Builder:
 *    - أضف كائن الهجرة الجديد إلى `AppDatabaseMigrationsRecent.ALL_MIGRATIONS`.
 *
 * 4. قاعدة صارمة لمنع ضياع البيانات:
 *    - يُمنع منعاً باتاً استخدام `.fallbackToDestructiveMigration()` حفاظاً على بيانات المتجر والمخزون.
 *    - يجب دائماً الإبقاء على `exportSchema = true` لتوليد ملفات JSON الخاصة بمخطط قاعدة البيانات تلقائياً.
 * =========================================================================================
 */
@Database(
    entities = [
        UserEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        CustomerEntity::class,
        SupplierEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        StockMovementEntity::class,
        ExpenseEntity::class,
        GlobalSettingsEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        CartItemEntity::class,
        CustomerPaymentEntity::class,
        SupplierPaymentEntity::class,
        AuditLogEntity::class,
        CustomerSequenceEntity::class,
        InvoiceSequenceEntity::class,
        InventoryEntity::class,
        InventoryItemEntity::class,
        InventoryAdjustmentEntity::class,
        InventorySequenceEntity::class
    ],
    version = AppDatabase.CURRENT_SCHEMA_VERSION,
    exportSchema = true
)
@androidx.room.TypeConverters(RoleConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun orderDao(): OrderDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun cartDao(): CartDao
    abstract fun stocktakingDao(): StocktakingDao

    companion object {
        private const val TAG = "AppDatabase"
        const val CURRENT_SCHEMA_VERSION = 23

        private const val DB_META_PREFS = "rmpos_db_schema_meta"
        internal const val KEY_LAST_OPENED_SCHEMA_VERSION = "last_opened_schema_version"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val MIGRATION_BACKUP_DIR = "migration_backups"
        internal const val DEFAULT_MAX_MIGRATION_BACKUPS = 1
        internal const val DEFAULT_MIGRATION_BACKUP_MAX_AGE_DAYS = 7L
        private val WAL_COMPANION_SUFFIXES = listOf("-wal", "-shm", "-journal")

        private fun copyFileWithFsync(source: File, destination: File): Boolean {
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
                Log.e(TAG, "فشل نسخ الملف الاحتياطي مع fsync (${source.name}): ${e.message}", e)
                if (destination.exists()) destination.delete()
                false
            }
        }

        /**
         * ينسخ ملف قاعدة البيانات الرئيسي بالإضافة إلى ملفات SQLite الجانبية (`-wal`، `-shm`، `-journal`)
         * لضمان عدم فقدان أحدث العمليات غير المدمجة في وضع WAL.
         */
        internal fun copyDatabaseWithWalCompanions(sourceDbFile: File, targetDbFile: File): Boolean {
            if (!sourceDbFile.exists()) return false
            if (!copyFileWithFsync(sourceDbFile, targetDbFile)) return false

            val sourceParent = sourceDbFile.parentFile ?: return true
            val targetParent = targetDbFile.parentFile ?: return true
            for (suffix in WAL_COMPANION_SUFFIXES) {
                val srcSidecar = File(sourceParent, "${sourceDbFile.name}$suffix")
                if (srcSidecar.exists() && srcSidecar.isFile) {
                    val dstSidecar = File(targetParent, "${targetDbFile.name}$suffix")
                    if (!copyFileWithFsync(srcSidecar, dstSidecar)) {
                        Log.w(TAG, "تعذر نسخ الملف الجانبي $suffix بجانب النسخة الاحتياطية")
                    }
                }
            }
            return true
        }

        /**
         * يقرأ رقم `PRAGMA user_version` مباشرة من ترويسة ملف SQLite (الإزاحة 60، 4 بايت Big-Endian)
         * دون فتح قاعدة البيانات. يُرجع `null` لملفات SQLCipher المشفّرة أو غير الصالحة.
         */
        internal fun readPlaintextSqliteUserVersion(dbFile: File): Int? {
            if (!dbFile.exists() || dbFile.length() < 64L) return null
            return try {
                val header = ByteArray(64)
                val read = FileInputStream(dbFile).use { it.read(header) }
                if (read < 64) return null
                val magic = String(header, 0, 15, Charsets.US_ASCII)
                if (magic != "SQLite format 3") return null
                ((header[60].toInt() and 0xFF) shl 24) or
                    ((header[61].toInt() and 0xFF) shl 16) or
                    ((header[62].toInt() and 0xFF) shl 8) or
                    (header[63].toInt() and 0xFF)
            } catch (_: Throwable) {
                null
            }
        }

        /**
         * يأخذ نسخة احتياطية كاملة (الملف الرئيسي + `-wal` + `-shm`) **قبل** فتح Room أو بدء معاملة
         * الهجرة إذا كانت القاعدة القائمة على إصدار أقدم من [targetVersion].
         * يغطي ذلك كلّاً من القواعد غير المشفّرة (عبر ترويسة SQLite) والمشفّرة بـ SQLCipher
         * (عبر آخر إصدار مخطط مسجّل في التفضيلات).
         */
        internal fun backupDatabaseBeforeOpenIfMigrationNeeded(
            context: Context,
            dbFile: File,
            targetVersion: Int = CURRENT_SCHEMA_VERSION
        ): File? {
            if (!dbFile.exists() || dbFile.length() <= 0L) return null
            val parent = dbFile.parentFile ?: return null

            val plaintextVersion = readPlaintextSqliteUserVersion(dbFile)
            val persistedVersion = context.applicationContext
                .getSharedPreferences(DB_META_PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_LAST_OPENED_SCHEMA_VERSION, 0)

            val detectedVersion = when {
                plaintextVersion != null && plaintextVersion in 1 until targetVersion -> plaintextVersion
                plaintextVersion == null && persistedVersion in 1 until targetVersion -> persistedVersion
                else -> return null
            }

            return try {
                val backupDir = File(parent, MIGRATION_BACKUP_DIR).apply { if (!exists()) mkdirs() }
                val timestamp = System.currentTimeMillis()
                val backupFile = File(backupDir, "db_backup_preopen_v${detectedVersion}_to_v${targetVersion}_$timestamp.db")
                if (copyDatabaseWithWalCompanions(dbFile, backupFile)) {
                    pruneMigrationBackups(dbFile)
                    Log.i(
                        TAG,
                        "تم حفظ نسخة احتياطية كاملة مع ملفات WAL قبل فتح Room للهجرة (v$detectedVersion -> v$targetVersion): ${backupFile.absolutePath}"
                    )
                    backupFile
                } else {
                    null
                }
            } catch (e: Throwable) {
                Log.e(TAG, "فشل حفظ النسخة الاحتياطية قبل فتح Room: ${e.message}", e)
                null
            }
        }

        /**
         * تقليم نسخ مجلد `migration_backups`:
         * يحتفظ بآخر [maxBackupsToKeep] نسخة فقط (الافتراضي: 1)، ويحذف أيضاً أي نسخة
         * أقدم من [maxAgeDays] أيام (الافتراضي: 7 أيام) مع ملفاتها الجانبية (`-wal` / `-shm` / `-journal`).
         */
        internal fun pruneMigrationBackups(
            dbFile: File,
            maxBackupsToKeep: Int = DEFAULT_MAX_MIGRATION_BACKUPS,
            maxAgeDays: Long = DEFAULT_MIGRATION_BACKUP_MAX_AGE_DAYS,
            nowMs: Long = System.currentTimeMillis()
        ): Int {
            val backupDir = File(dbFile.parentFile ?: return 0, MIGRATION_BACKUP_DIR)
            if (!backupDir.exists() || !backupDir.isDirectory) return 0

            val backupFiles = backupDir.listFiles { file ->
                file.isFile && file.name.startsWith("db_backup_") && file.name.endsWith(".db")
            }?.sortedWith(
                compareByDescending<File> { it.lastModified() }
                    .thenByDescending { it.name }
            ) ?: return 0

            val maxAgeMs = maxAgeDays.coerceAtLeast(0L) * 24L * 60L * 60L * 1000L
            val keepLimit = maxBackupsToKeep.coerceAtLeast(0)
            var deletedCount = 0

            backupFiles.forEachIndexed { index, file ->
                val isOverCountLimit = index >= keepLimit
                val isExpired = maxAgeMs > 0L && (nowMs - file.lastModified()) > maxAgeMs
                if (isOverCountLimit || isExpired) {
                    if (file.delete()) {
                        deletedCount++
                    }
                    for (suffix in WAL_COMPANION_SUFFIXES) {
                        val sidecar = File(backupDir, "${file.name}$suffix")
                        if (sidecar.exists()) sidecar.delete()
                    }
                }
            }
            if (deletedCount > 0) {
                Log.i(TAG, "تم تقليم $deletedCount نسخة قديمة من $MIGRATION_BACKUP_DIR")
            }
            return deletedCount
        }

        /**
         * إنشاء نسخة احتياطية محلية لملف قاعدة البيانات وملفات WAL المرافقة قبل تطبيق الهجرة.
         */
        fun backupDatabaseBeforeMigration(db: SupportSQLiteDatabase, fromVersion: Int, toVersion: Int) {
            try {
                val dbPath = db.path ?: return
                val originalFile = File(dbPath)
                if (originalFile.exists()) {
                    val backupDir = File(originalFile.parentFile, MIGRATION_BACKUP_DIR).apply {
                        if (!exists()) mkdirs()
                    }
                    val timestamp = System.currentTimeMillis()
                    val backupFile = File(backupDir, "db_backup_v${fromVersion}_to_v${toVersion}_$timestamp.db")
                    if (copyDatabaseWithWalCompanions(originalFile, backupFile)) {
                        pruneMigrationBackups(originalFile)
                        Log.i(TAG, "تم بنجاح حفظ نسخة احتياطية محلية مع ملفات WAL قبل الهجرة: ${backupFile.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "فشل حفظ النسخة الاحتياطية قبل الهجرة: ${e.message}", e)
            }
        }

        val MIGRATION_7_8: Migration get() = AppDatabaseMigrationsLegacy.MIGRATION_7_8
        val MIGRATION_8_9: Migration get() = AppDatabaseMigrationsLegacy.MIGRATION_8_9
        val MIGRATION_9_10: Migration get() = AppDatabaseMigrationsLegacy.MIGRATION_9_10
        val MIGRATION_10_11: Migration get() = AppDatabaseMigrationsLegacy.MIGRATION_10_11
        val MIGRATION_11_12: Migration get() = AppDatabaseMigrationsLegacy.MIGRATION_11_12
        val MIGRATION_12_13: Migration get() = AppDatabaseMigrationsLegacy.MIGRATION_12_13
        val MIGRATION_13_14: Migration get() = AppDatabaseMigrationsLegacy.MIGRATION_13_14
        val MIGRATION_14_15: Migration get() = AppDatabaseMigrationsLegacy.MIGRATION_14_15
        val MIGRATION_15_16: Migration get() = AppDatabaseMigrationsLegacy.MIGRATION_15_16
        val MIGRATION_16_17: Migration get() = AppDatabaseMigrationsFinancial.MIGRATION_16_17
        val MIGRATION_17_18: Migration get() = AppDatabaseMigrationsFinancial.MIGRATION_17_18
        val MIGRATION_18_19: Migration get() = AppDatabaseMigrationsFinancial.MIGRATION_18_19
        val MIGRATION_19_20: Migration get() = AppDatabaseMigrationsRecent.MIGRATION_19_20
        val MIGRATION_20_21: Migration get() = AppDatabaseMigrationsRecent.MIGRATION_20_21
        val MIGRATION_21_22: Migration get() = AppDatabaseMigrationsRecent.MIGRATION_21_22
        val MIGRATION_22_23: Migration get() = AppDatabaseMigrationsRecent.MIGRATION_22_23

        @Volatile
        internal var testForcedInitFailure: (() -> Throwable?)? = null

        @Synchronized
        internal fun resetInstanceForTesting() {
            try {
                INSTANCE?.close()
            } catch (_: Throwable) {
            }
            INSTANCE = null
            testForcedInitFailure = null
        }

        /**
         * تهيئة قاعدة البيانات بشكل آمن يُرجع [DatabaseInitResult.Ready] أو [DatabaseInitResult.Failed]
         * بدلاً من رمي استثناء يُغلق التطبيق عند الإقلاع، مع:
         * 1. استرداد أي ترحيل SQLCipher منقطع أولاً.
         * 2. أخذ نسخة احتياطية كاملة (الملف الرئيسي + `-wal` + `-shm`) قبل فتح Room إن كانت الهجرة لازمة.
         * 3. تفعيل SQLCipher AES-256 دائماً في الإنتاج (بدون أي كشف لبيئة اختبار في كود الإنتاج).
         */
        fun initializeDatabase(context: Context): DatabaseInitResult {
            INSTANCE?.let { return DatabaseInitResult.Ready(it) }
            return synchronized(this) {
                INSTANCE?.let { return@synchronized DatabaseInitResult.Ready(it) }
                val appContext = context.applicationContext
                val dbFile = appContext.getDatabasePath("supermarket_pos_database")

                try {
                    testForcedInitFailure?.invoke()?.let { forced ->
                        throw forced
                    }

                    // 1. استرداد أي ترحيل تشفير منقطع قبل لمس القاعدة
                    com.example.security.DatabaseEncryptionManager.recoverInterruptedMigrationIfNeeded(dbFile)

                    // 2. أخذ نسخة احتياطية WAL-safe قبل فتح Room أو بدء أي هجرة
                    backupDatabaseBeforeOpenIfMigrationNeeded(appContext, dbFile, CURRENT_SCHEMA_VERSION)

                    val builder = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        "supermarket_pos_database"
                    )
                        .addMigrations(*AppDatabaseMigrationsRecent.ALL_MIGRATIONS)

                    // 3. تشفير قاعدة البيانات بنظام AES-256 عبر SQLCipher في الإنتاج
                    if (!com.example.security.SecurityRuntimeConfig.isJvmTestMode()) {
                        val passphrase = com.example.security.DatabaseEncryptionManager.getOrCreatePassphrase(appContext)

                        if (dbFile.exists() && !com.example.security.DatabaseEncryptionManager.isDatabaseFileEncrypted(dbFile)) {
                            val migrated = com.example.security.DatabaseEncryptionManager.migrateUnencryptedDatabaseToEncrypted(
                                appContext,
                                dbFile,
                                passphrase
                            )
                            if (!migrated) {
                                return@synchronized DatabaseInitResult.Failed(
                                    reasonCode = "SQLCIPHER_MIGRATION_FAILED",
                                    messageAr = "تعذر إكمال تشفير وترقية قاعدة البيانات إلى SQLCipher (AES-256). بقيت قاعدة البيانات الأصلية سليمة دون تعديل.",
                                    messageFr = "Impossible de terminer le chiffrement SQLCipher (AES-256) de la base de données. Le fichier d'origine est resté intact.",
                                    messageEn = "Failed to complete SQLCipher (AES-256) database encryption migration. Your original database file remains untouched.",
                                    technicalDetails = "migrateUnencryptedDatabaseToEncrypted returned false",
                                    dbFilePath = dbFile.absolutePath
                                )
                            }
                        }

                        val factory = com.example.security.DatabaseEncryptionManager.createSupportFactory(appContext)
                        if (factory != null) {
                            builder.openHelperFactory(factory)
                            Log.i(TAG, "تم بنجاح تفعيل تشفير قاعدة البيانات بمفتاح أمان عالي AES-256 (SQLCipher)")
                        } else {
                            return@synchronized DatabaseInitResult.Failed(
                                reasonCode = "SQLCIPHER_ENGINE_UNAVAILABLE",
                                messageAr = "تعذر تحميل محرك تشفير قاعدة البيانات (SQLCipher) على هذا الجهاز. تم إيقاف فتح القاعدة لحماية بياناتك.",
                                messageFr = "Impossible de charger le moteur de chiffrement SQLCipher sur cet appareil. L'ouverture est suspendue pour protéger vos données.",
                                messageEn = "Unable to initialize the SQLCipher encryption engine on this device. Opening was halted to protect your data.",
                                technicalDetails = "DatabaseEncryptionManager.createSupportFactory returned null",
                                dbFilePath = dbFile.absolutePath
                            )
                        }
                    }

                    val instance = builder.build()
                    INSTANCE = instance
                    appContext.getSharedPreferences(DB_META_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_LAST_OPENED_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                        .apply()
                    pruneMigrationBackups(dbFile)
                    DatabaseInitResult.Ready(instance)
                } catch (t: Throwable) {
                    Log.e(TAG, "فشل تهيئة قاعدة البيانات بشكل آمن (تم منع الانهيار): ${t.message}", t)
                    DatabaseInitResult.Failed(
                        reasonCode = "DB_INIT_EXCEPTION",
                        messageAr = "حدث خطأ غير متوقع أثناء تهيئة أو ترحيل قاعدة البيانات. لم يتم حذف أو تعديل ملفاتك الأصلية.",
                        messageFr = "Une erreur inattendue s'est produite lors de l'initialisation ou de la migration de la base de données. Vos fichiers d'origine sont intacts.",
                        messageEn = "An unexpected error occurred during database initialization or migration. Your original files remain untouched.",
                        technicalDetails = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                        dbFilePath = dbFile.absolutePath
                    )
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return when (val result = initializeDatabase(context)) {
                is DatabaseInitResult.Ready -> result.database
                is DatabaseInitResult.Failed -> throw SecurityException(
                    "${result.reasonCode}: ${result.messageAr} (${result.technicalDetails})"
                )
            }
        }
    }
}

/**
 * نتيجة تهيئة قاعدة البيانات عند الإقلاع لتفادي انهيار التطبيق وعرض شاشة استرداد آمنة.
 */
sealed class DatabaseInitResult {
    data class Ready(val database: AppDatabase) : DatabaseInitResult()
    data class Failed(
        val reasonCode: String,
        val messageAr: String,
        val messageFr: String,
        val messageEn: String,
        val technicalDetails: String,
        val dbFilePath: String
    ) : DatabaseInitResult()
}
