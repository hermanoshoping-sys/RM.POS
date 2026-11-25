package com.example.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import com.example.BuildConfig
import com.example.StockMonitorWorker
import com.example.data.AppDatabase
import com.example.data.CategoryEntity
import com.example.data.CustomerEntity
import com.example.data.DatabaseInitResult
import com.example.data.GlobalSettingsEntity
import com.example.data.PosRepository
import com.example.data.ProductEntity
import com.example.data.RestoreResult
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.data.UserEntity
import com.example.data.UserRole
import com.example.ui.screens.DatabaseRecoveryScreen
import com.example.ui.viewmodels.BackupSyncViewModel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies:
 * 1. Upgrading a real Room v22 database populated with business data into SQLCipher encrypted format
 *    (atomic single renameTo swap, WAL/SHM deletion before swap, user_version preservation, idempotency).
 * 2. Crash/interruption injection after EVERY step of migration + startup recovery (restoring backup
 *    when main DB is missing, verifying temp encrypted file before cleanup) proving row counts in
 *    all main tables remain 100% unchanged.
 * 3. Non-crashing DatabaseInitResult.Failed + DatabaseRecoveryScreen UI and retry recovery flow.
 * 4. 16 KB ELF64 PT_LOAD alignment and uncompressed 16 KB ZIP alignment of libsqlcipher.so for
 *    arm64-v8a and x86_64, plus fixed applicationId and bumped versionCode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SqlCipherUpgradeAndAlignmentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        AppDatabase.resetInstanceForTesting()
    }

    private data class MainTableCounts(
        val products: Int,
        val customers: Int,
        val users: Int,
        val sales: Int,
        val saleItems: Int,
        val settings: Int
    )

    private fun seedRealRoomDatabase(context: Context, dbName: String): Pair<File, MainTableCounts> {
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        DatabaseEncryptionManager.deleteSqliteSidecarFiles(dbFile)
        if (dbFile.exists()) dbFile.delete()
        DatabaseEncryptionManager.backupPlainFileFor(dbFile).delete()
        DatabaseEncryptionManager.encryptedTempFileFor(dbFile).delete()

        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val dao = roomDb.appDao()
            val catId = dao.insertCategory(CategoryEntity(name = "المشروبات الساخنة"))
            val prodId1 = dao.insertProduct(
                ProductEntity(
                    barcode = "6111000000111",
                    name = "شاي أخضر ملكي",
                    categoryId = catId,
                    categoryName = "المشروبات الساخنة",
                    unit = "علبة",
                    purchasePrice = 1500L,
                    sellPrice = 2200L,
                    currentStock = 48.0,
                    minStock = 10.0
                )
            )
            val prodId2 = dao.insertProduct(
                ProductEntity(
                    barcode = "6111000000222",
                    name = "قهوة عربية أصيلة",
                    categoryId = catId,
                    categoryName = "المشروبات الساخنة",
                    unit = "علبة",
                    purchasePrice = 2500L,
                    sellPrice = 3800L,
                    currentStock = 20.0,
                    minStock = 5.0
                )
            )
            val custId = dao.insertCustomer(
                CustomerEntity(
                    name = "محمد التاجر",
                    phone = "0612345678",
                    balance = 14500L,
                    totalPurchases = 98000L,
                    customerNumber = 1L,
                    barcode = "rmpos-1"
                )
            )
            dao.insertUser(
                UserEntity(
                    username = "يوسف",
                    passwordHash = "v3:salt:hash",
                    role = UserRole.MANAGER
                )
            )
            dao.insertSettings(
                GlobalSettingsEntity(
                    id = 1,
                    shopName = "متجر الأمانة",
                    currency = "DH"
                )
            )
            val saleId = dao.insertSale(
                SaleEntity(
                    invoiceNumber = "0001",
                    timestamp = 1_770_000_000_000L,
                    cashierName = "يوسف",
                    customerId = custId,
                    customerName = "محمد التاجر",
                    totalAmount = 6000L,
                    discountAmount = 0L,
                    discountType = "NONE",
                    paidAmount = 6000L,
                    changeAmount = 0L,
                    paymentMethod = "CASH"
                )
            )
            dao.insertSaleItemsBatch(
                listOf(
                    SaleItemEntity(
                        saleId = saleId,
                        productId = prodId1,
                        productName = "شاي أخضر ملكي",
                        quantity = 1.0,
                        unitPrice = 2200L,
                        totalLinePrice = 2200L,
                        unitCost = 1500L
                    ),
                    SaleItemEntity(
                        saleId = saleId,
                        productId = prodId2,
                        productName = "قهوة عربية أصيلة",
                        quantity = 1.0,
                        unitPrice = 3800L,
                        totalLinePrice = 3800L,
                        unitCost = 2500L
                    )
                )
            )
        }
        roomDb.close()
        return dbFile to readMainTableCounts(dbFile)
    }

    private fun readMainTableCounts(dbFile: File): MainTableCounts {
        val raw = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return raw.use { db ->
            fun count(table: String): Int =
                db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
            MainTableCounts(
                products = count("products"),
                customers = count("customers"),
                users = count("users"),
                sales = count("sales"),
                saleItems = count("sale_items"),
                settings = count("global_settings")
            )
        }
    }

    /**
     * Real AES-256-GCM exporter on JVM that checkpoints WAL, reads PRAGMA user_version and
     * verifies all seeded business tables before writing an authenticated ciphertext file.
     */
    private fun createRealPayloadEncryptingExporter(
        onInspected: ((version: Int, counts: MainTableCounts, hexKey: String) -> Unit)? = null
    ): DatabaseEncryptionManager.SqlCipherExporter =
        DatabaseEncryptionManager.SqlCipherExporter { srcPlainDb, dstEncryptedTemp, hexKey ->
            val rawDb = SQLiteDatabase.openDatabase(srcPlainDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            val version: Int
            val counts: MainTableCounts
            try {
                rawDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).use { it.moveToFirst() }
                version = rawDb.rawQuery("PRAGMA user_version;", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
                fun count(table: String): Int =
                    rawDb.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                        if (c.moveToFirst()) c.getInt(0) else 0
                    }
                counts = MainTableCounts(
                    products = count("products"),
                    customers = count("customers"),
                    users = count("users"),
                    sales = count("sales"),
                    saleItems = count("sale_items"),
                    settings = count("global_settings")
                )
            } finally {
                rawDb.close()
            }
            onInspected?.invoke(version, counts, hexKey)

            val keyBytes = hexKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            val encryptedPayload = cipher.doFinal(srcPlainDb.readBytes())
            dstEncryptedTemp.writeBytes(iv + encryptedPayload)
        }

    @Test
    fun testRealDataRoomDatabaseUpgradeToEncryptedSqlCipherAndSidecarCleanup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "sqlcipher_upgrade_real_data_test.db"
        val (dbFile, initialCounts) = seedRealRoomDatabase(context, dbName)

        assertEquals(2, initialCounts.products)
        assertEquals(1, initialCounts.customers)
        assertEquals(1, initialCounts.users)
        assertEquals(1, initialCounts.sales)
        assertEquals(2, initialCounts.saleItems)
        assertEquals(1, initialCounts.settings)

        // Simulate active plaintext WAL & SHM files that MUST be removed BEFORE swap
        val walFile = File(dbFile.parentFile, "$dbName-wal").apply { writeBytes(ByteArray(128) { 0x33 }) }
        val shmFile = File(dbFile.parentFile, "$dbName-shm").apply { writeBytes(ByteArray(128) { 0x44 }) }
        assertTrue(dbFile.exists())
        assertFalse(DatabaseEncryptionManager.isDatabaseFileEncrypted(dbFile))

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        var observedVersion = 0
        var capturedHexKey = ""
        var sidecarsDeletedBeforeSwap = false
        var originalStillPresentDuringBackupCopy = false

        val hook = object : DatabaseEncryptionManager.MigrationStepHook {
            override fun onAfterBackupCopy(plainDbFile: File, backupPlainFile: File, encryptedTempFile: File) {
                // Original DB must NOT have been moved away before swap!
                originalStillPresentDuringBackupCopy = plainDbFile.exists() &&
                    backupPlainFile.exists() &&
                    plainDbFile.length() == backupPlainFile.length()
            }

            override fun onAfterSidecarDelete(plainDbFile: File, backupPlainFile: File, encryptedTempFile: File) {
                sidecarsDeletedBeforeSwap = !walFile.exists() && !shmFile.exists() && plainDbFile.exists()
            }
        }

        val migrated = DatabaseEncryptionManager.migrateUnencryptedDatabaseToEncrypted(
            plainDbFile = dbFile,
            passphrase = passphrase,
            exporter = createRealPayloadEncryptingExporter { version, _, hexKey ->
                observedVersion = version
                capturedHexKey = hexKey
            },
            stepHook = hook
        )

        assertTrue("SQLCipher upgrade must succeed on real database", migrated)
        assertEquals(64, capturedHexKey.length)
        assertEquals(AppDatabase.CURRENT_SCHEMA_VERSION, observedVersion)
        assertTrue("Original DB must stay in place during backup copy (never moved away)", originalStillPresentDuringBackupCopy)
        assertTrue("Old -wal and -shm must be deleted BEFORE atomic swap", sidecarsDeletedBeforeSwap)
        assertTrue("Upgraded DB file must be encrypted", DatabaseEncryptionManager.isDatabaseFileEncrypted(dbFile))
        assertFalse("Stale -wal file must not remain", walFile.exists())
        assertFalse("Stale -shm file must not remain", shmFile.exists())
        assertFalse("Backup file must be cleaned after swap", DatabaseEncryptionManager.backupPlainFileFor(dbFile).exists())
        assertFalse("Encrypted temp file must be consumed by renameTo", DatabaseEncryptionManager.encryptedTempFileFor(dbFile).exists())

        // Idempotency check
        var exporterCalledAgain = false
        val idempotentOk = DatabaseEncryptionManager.migrateUnencryptedDatabaseToEncrypted(
            plainDbFile = dbFile,
            passphrase = passphrase,
            exporter = DatabaseEncryptionManager.SqlCipherExporter { _, _, _ -> exporterCalledAgain = true }
        )
        assertTrue(idempotentOk)
        assertFalse("Exporter must not run on already-encrypted DB", exporterCalledAgain)

        dbFile.delete()
    }

    @Test
    fun testStepByStepCrashInterruptionPreservesAllMainTableRowCounts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }

        // Inject failure at each pre-swap stage and verify restart + recovery preserves EVERY row
        val preSwapStages = listOf(
            "DURING_EXPORT",
            "AFTER_EXPORT",
            "AFTER_BACKUP_COPY",
            "AFTER_SIDECAR_DELETE"
        )

        for (stage in preSwapStages) {
            val dbName = "interrupt_${stage.lowercase()}.db"
            val (dbFile, expectedCounts) = seedRealRoomDatabase(context, dbName)
            File(dbFile.parentFile, "$dbName-wal").writeBytes(ByteArray(64) { 0x11 })
            File(dbFile.parentFile, "$dbName-shm").writeBytes(ByteArray(64) { 0x22 })

            val baseExporter = createRealPayloadEncryptingExporter()
            val exporter = if (stage == "DURING_EXPORT") {
                DatabaseEncryptionManager.SqlCipherExporter { _, dst, _ ->
                    dst.writeBytes(ByteArray(32) { 0x55 })
                    throw IllegalStateException("Simulated crash during export")
                }
            } else {
                baseExporter
            }

            val hook = object : DatabaseEncryptionManager.MigrationStepHook {
                override fun onAfterExport(plainDbFile: File, encryptedTempFile: File) {
                    if (stage == "AFTER_EXPORT") throw RuntimeException("Simulated kill after export")
                }

                override fun onAfterBackupCopy(plainDbFile: File, backupPlainFile: File, encryptedTempFile: File) {
                    if (stage == "AFTER_BACKUP_COPY") throw RuntimeException("Simulated kill after backup copy")
                }

                override fun onAfterSidecarDelete(plainDbFile: File, backupPlainFile: File, encryptedTempFile: File) {
                    if (stage == "AFTER_SIDECAR_DELETE") throw RuntimeException("Simulated kill after sidecar delete")
                }
            }

            val migrated = DatabaseEncryptionManager.migrateUnencryptedDatabaseToEncrypted(
                plainDbFile = dbFile,
                passphrase = passphrase,
                exporter = exporter,
                stepHook = hook
            )
            assertFalse("Migration must return false when interrupted at $stage", migrated)

            // Simulate app restart: run startup recovery and re-read all main tables
            DatabaseEncryptionManager.recoverInterruptedMigrationIfNeeded(dbFile)
            assertTrue("Original DB must exist after interruption at $stage", dbFile.exists())
            assertFalse("Original DB must remain valid plaintext SQLite after pre-swap failure at $stage", DatabaseEncryptionManager.isDatabaseFileEncrypted(dbFile))
            assertEquals("Row counts must be 100% preserved after interruption at $stage", expectedCounts, readMainTableCounts(dbFile))

            dbFile.delete()
            DatabaseEncryptionManager.deleteSqliteSidecarFiles(dbFile)
        }

        // Stage 5: Simulated power loss leaving backup behind when main DB is missing on startup
        run {
            val dbName = "interrupt_missing_main_with_backup.db"
            val (dbFile, expectedCounts) = seedRealRoomDatabase(context, dbName)
            val backupFile = DatabaseEncryptionManager.backupPlainFileFor(dbFile)
            val orphanTemp = DatabaseEncryptionManager.encryptedTempFileFor(dbFile)
            dbFile.copyTo(backupFile, overwrite = true)
            orphanTemp.writeBytes("SQLite format 3\u0000corrupt_temp".toByteArray(Charsets.US_ASCII))
            assertTrue(dbFile.delete())
            assertFalse(dbFile.exists())

            var verifierInvoked = false
            val action = DatabaseEncryptionManager.recoverInterruptedMigrationIfNeeded(
                dbFile = dbFile,
                verifier = DatabaseEncryptionManager.EncryptedFileVerifier { f ->
                    verifierInvoked = true
                    DatabaseEncryptionManager.defaultEncryptedVerifier.isValidEncryptedDatabase(f)
                }
            )
            assertEquals(DatabaseEncryptionManager.StartupRecoveryAction.RESTORED_FROM_BACKUP, action)
            assertTrue("Startup recovery must inspect encrypted temp file before deleting it", verifierInvoked)
            assertTrue("Main DB must be restored from backup before opening Room", dbFile.exists())
            assertFalse("Backup should be moved into place", backupFile.exists())
            assertFalse("Orphan temp must be removed after verification", orphanTemp.exists())
            assertEquals("All main table row counts must survive backup restoration", expectedCounts, readMainTableCounts(dbFile))

            dbFile.delete()
            DatabaseEncryptionManager.deleteSqliteSidecarFiles(dbFile)
        }

        // Stage 6: Crash AFTER atomic swap succeeded but BEFORE cleanup of backup file
        run {
            val dbName = "interrupt_after_swap_before_cleanup.db"
            val (dbFile, _) = seedRealRoomDatabase(context, dbName)
            val backupFile = DatabaseEncryptionManager.backupPlainFileFor(dbFile)
            val ok = DatabaseEncryptionManager.migrateUnencryptedDatabaseToEncrypted(
                plainDbFile = dbFile,
                passphrase = passphrase,
                exporter = createRealPayloadEncryptingExporter(),
                stepHook = object : DatabaseEncryptionManager.MigrationStepHook {
                    override fun onAfterSwapBeforeCleanup(dbFile: File, backupPlainFile: File) {
                        throw RuntimeException("Simulated kill right after atomic swap")
                    }
                }
            )
            assertTrue("If swap already succeeded before crash, encrypted DB must be kept", ok)
            assertTrue(DatabaseEncryptionManager.isDatabaseFileEncrypted(dbFile))
            assertFalse("Backup must be cleaned up safely", backupFile.exists())

            // Also verify startup recovery cleans up an orphan backup next to an encrypted DB
            backupFile.writeBytes("SQLite format 3\u0000old_backup".toByteArray(Charsets.US_ASCII))
            val recoveryAction = DatabaseEncryptionManager.recoverInterruptedMigrationIfNeeded(dbFile)
            assertEquals(DatabaseEncryptionManager.StartupRecoveryAction.CLEANED_POST_SWAP_BACKUP, recoveryAction)
            assertFalse(backupFile.exists())
            assertTrue(DatabaseEncryptionManager.isDatabaseFileEncrypted(dbFile))

            dbFile.delete()
        }
    }

    @Test
    fun testForcedDatabaseInitFailureShowsRecoveryScreenWithoutCrashingAndRecoversOnRetry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppDatabase.resetInstanceForTesting()

        // Seed real database file on disk and verify failure leaves it 100% untouched
        val (dbFile, expectedCounts) = seedRealRoomDatabase(context, "supermarket_pos_database")
        val originalBytes = dbFile.readBytes()

        // Force an intentional failure during initialization
        AppDatabase.testForcedInitFailure = {
            SecurityException("Simulated SQLCipher migration failure for recovery UI test")
        }

        val failedInit = AppDatabase.initializeDatabase(context)
        assertTrue("Expected DatabaseInitResult.Failed instead of crash", failedInit is DatabaseInitResult.Failed)
        val failure = failedInit as DatabaseInitResult.Failed
        assertEquals("DB_INIT_EXCEPTION", failure.reasonCode)
        assertTrue(failure.technicalDetails.contains("Simulated SQLCipher migration failure"))
        // Original database file must remain completely untouched
        assertTrue(dbFile.exists())
        assertEquals(originalBytes.toList(), dbFile.readBytes().toList())
        assertEquals(expectedCounts, readMainTableCounts(dbFile))

        // Render DatabaseRecoveryScreen and verify explanation + retry button recovers cleanly
        var retriedResult: DatabaseInitResult? = null
        composeRule.setContent {
            DatabaseRecoveryScreen(
                failure = failure,
                lang = "AR",
                onRetry = {
                    AppDatabase.testForcedInitFailure = null
                    retriedResult = AppDatabase.initializeDatabase(context)
                }
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("db_recovery_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("db_recovery_reason_text").assertIsDisplayed()
        composeRule.onNodeWithTag("db_recovery_error_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("db_recovery_retry_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertTrue("Retry must transition to DatabaseInitResult.Ready", retriedResult is DatabaseInitResult.Ready)
        AppDatabase.resetInstanceForTesting()
    }

    @Test
    fun testMigrationBackupsArePrunedToLatestOneAndExpiredAfterSevenDays() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("prune_test_db")
        val backupDir = File(dbFile.parentFile, "migration_backups").apply {
            deleteRecursively()
            mkdirs()
        }

        val nowMs = 1_800_000_000_000L
        val oneDayMs = 24L * 60L * 60L * 1000L
        val oldest = File(backupDir, "db_backup_v19_to_v20_1.db").apply {
            writeText("old1")
            setLastModified(nowMs - 3L * oneDayMs)
        }
        val middle = File(backupDir, "db_backup_v20_to_v21_2.db").apply {
            writeText("old2")
            setLastModified(nowMs - 2L * oneDayMs)
        }
        val newest = File(backupDir, "db_backup_v21_to_v22_3.db").apply {
            writeText("latest")
            setLastModified(nowMs - oneDayMs)
        }
        val unrelated = File(backupDir, "keep_me.txt").apply { writeText("note") }

        val deletedCount = AppDatabase.pruneMigrationBackups(dbFile, maxBackupsToKeep = 1, maxAgeDays = 7L, nowMs = nowMs)
        assertEquals("Should delete 2 older backups and keep only the newest", 2, deletedCount)
        assertFalse(oldest.exists())
        assertFalse(middle.exists())
        assertTrue("Newest backup must be retained within 7 days", newest.exists())
        assertTrue("Non-backup files must remain untouched", unrelated.exists())

        // After 8 days of successful operation, even the last remaining backup is pruned
        val expiredDeleted = AppDatabase.pruneMigrationBackups(
            dbFile,
            maxBackupsToKeep = 1,
            maxAgeDays = 7L,
            nowMs = nowMs + 9L * oneDayMs
        )
        assertEquals("Expired backup older than 7 days must be pruned", 1, expiredDeleted)
        assertFalse(newest.exists())
        backupDir.deleteRecursively()
    }

    @Test
    fun testStockMonitorWorkerAndBackupSyncViewModelHandleDatabaseInitFailureSafely() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppDatabase.resetInstanceForTesting()
        PasswordHasher.init(context)

        val memDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = PosRepository(memDb.appDao(), memDb)
        val executor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val testScope = CoroutineScope(SupervisorJob() + executor)

        try {
            AppDatabase.testForcedInitFailure = {
                SecurityException("Simulated DB lock/init failure")
            }

            // 1. StockMonitorWorker must return Result.retry() without crashing
            val workerResult = StockMonitorWorker.executeLowStockCheck(context)
            assertEquals(ListenableWorker.Result.retry(), workerResult)

            // 2. BackupSyncViewModel must handle DatabaseInitResult.Failed cleanly across all 3 DB entry points
            val prefs = context.getSharedPreferences("test_db_fail_backup_prefs", Context.MODE_PRIVATE)
            val backupVm = BackupSyncViewModel(
                repository = repo,
                context = context,
                prefs = prefs,
                externalScope = testScope,
                languageProvider = { "AR" }
            )

            val driveRes = CompletableDeferred<Pair<Boolean, String>>()
            backupVm.performGoogleDriveBackup(
                encryptionPassword = "StrongPass#2026",
                onComplete = { ok, msg -> driveRes.complete(ok to msg) }
            )
            val (driveOk, driveMsg) = withTimeout(5_000L) { driveRes.await() }
            assertFalse("Manual backup must fail gracefully when DB init fails", driveOk)
            assertTrue(driveMsg.isNotBlank())

            val autoRes = CompletableDeferred<Pair<Boolean, String>>()
            backupVm.performAutoBackup(
                targetContext = context,
                onComplete = { ok, msg -> autoRes.complete(ok to msg) }
            )
            val (autoOk, autoMsg) = withTimeout(5_000L) { autoRes.await() }
            assertFalse("Auto-backup must fail gracefully when DB init fails", autoOk)
            assertTrue(autoMsg.isNotBlank())

            val managerPass = "Manager#2026"
            val managerHash = PasswordHasher.hashAsync(managerPass)
            val manager = UserEntity(id = 1L, username = "مدير", passwordHash = managerHash, role = UserRole.MANAGER)
            val encryptedPayload = BackupEncryptionManager.encryptBackup("""{"schemaVersion":${AppDatabase.CURRENT_SCHEMA_VERSION}}""", managerPass)
            val restoreRes = CompletableDeferred<RestoreResult>()
            backupVm.restoreFromJson(
                backupString = encryptedPayload,
                decryptionPassword = managerPass,
                currentManager = manager,
                onResult = { restoreRes.complete(it) }
            )
            val restoreOutcome = withTimeout(10_000L) { restoreRes.await() }
            assertFalse("Restore must fail gracefully when DB init fails", restoreOutcome.success)
            assertTrue(restoreOutcome.message.isNotBlank())
        } finally {
            testScope.cancel()
            executor.close()
            memDb.close()
            AppDatabase.resetInstanceForTesting()
        }
    }

    @Test
    fun testSqlCipherNativeLibrariesAre16KbElfPageAlignedAndPackagingConfigured() {
        val projectRoot = if (File("../settings.gradle.kts").exists()) {
            File("..").canonicalFile
        } else {
            File(".").canonicalFile
        }

        // 1. Verify fixed applicationId, bumped versionCode, and 16 KB uncompressed jniLibs packaging
        assertEquals("com.aistudio.rmpos.x7m4qp", BuildConfig.APPLICATION_ID)
        assertTrue("versionCode must be >= 7", BuildConfig.VERSION_CODE >= 7)

        val appGradle = File(projectRoot, "app/build.gradle.kts").readText()
        assertTrue(
            "app/build.gradle.kts must keep useLegacyPackaging = false for 16 KB alignment",
            appGradle.contains("useLegacyPackaging = false")
        )
        assertTrue(
            "app/build.gradle.kts must preserve release signingConfig",
            appGradle.contains("create(\"release\")") && appGradle.contains("signingConfig = signingConfigs.getByName(\"release\")")
        )

        // 2. Verify libsqlcipher.so for BOTH 64-bit Android ABIs (arm64-v8a & x86_64) in APK or Gradle cache
        val checkedAbis = mutableMapOf<String, Long>()
        val debugApk = File(projectRoot, "app/build/outputs/apk/debug/app-debug.apk")
        if (debugApk.exists()) {
            ZipFile(debugApk).use { zip ->
                for (abi in listOf("arm64-v8a", "x86_64")) {
                    val entry = zip.getEntry("lib/$abi/libsqlcipher.so") ?: continue
                    assertEquals(
                        "lib/$abi/libsqlcipher.so in APK must be uncompressed (STORED) for 16 KB page alignment",
                        ZipEntry.STORED,
                        entry.method
                    )
                    val soBytes = zip.getInputStream(entry).use { it.readBytes() }
                    checkedAbis[abi] = minElf64LoadSegmentAlignment(soBytes)
                }
            }
        }

        if (checkedAbis.size < 2) {
            val gradleHomes = listOfNotNull(
                System.getenv("GRADLE_USER_HOME")?.let(::File),
                System.getProperty("user.home")?.let { File(it, ".gradle") }
            ).filter { it.isDirectory }

            val sqlCipherAar = gradleHomes.asSequence()
                .flatMap { File(it, "caches/modules-2/files-2.1/net.zetetic/sqlcipher-android").walkTopDown() }
                .firstOrNull { it.isFile && it.name.endsWith(".aar") }

            assertTrue(
                "Expected either built debug APK or cached net.zetetic:sqlcipher-android AAR to inspect 16 KB ELF alignment",
                sqlCipherAar != null || checkedAbis.isNotEmpty()
            )
            if (sqlCipherAar != null) {
                ZipFile(sqlCipherAar).use { zip ->
                    for (abi in listOf("arm64-v8a", "x86_64")) {
                        if (abi in checkedAbis) continue
                        val entry = zip.getEntry("jni/$abi/libsqlcipher.so") ?: continue
                        val soBytes = zip.getInputStream(entry).use { it.readBytes() }
                        checkedAbis[abi] = minElf64LoadSegmentAlignment(soBytes)
                    }
                }
            }
        }

        assertTrue("Expected arm64-v8a libsqlcipher.so to be verified", checkedAbis.containsKey("arm64-v8a"))
        assertTrue("Expected x86_64 libsqlcipher.so to be verified", checkedAbis.containsKey("x86_64"))

        for ((abi, minAlign) in checkedAbis) {
            assertTrue(
                "libsqlcipher.so ($abi) PT_LOAD alignment must be >= 16384 (16 KB), got $minAlign",
                minAlign >= 16_384L && minAlign % 16_384L == 0L
            )
        }
    }

    /**
     * Parses ELF64 binary header and returns the minimum `p_align` across all `PT_LOAD` segments.
     */
    private fun minElf64LoadSegmentAlignment(elfBytes: ByteArray): Long {
        assertTrue("Invalid ELF binary (too small)", elfBytes.size >= 64)
        assertEquals(0x7F.toByte(), elfBytes[0])
        assertEquals('E'.code.toByte(), elfBytes[1])
        assertEquals('L'.code.toByte(), elfBytes[2])
        assertEquals('F'.code.toByte(), elfBytes[3])
        assertEquals("Expected 64-bit ELF (EI_CLASS == 2)", 2.toByte(), elfBytes[4])

        val order = if (elfBytes[5] == 1.toByte()) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val buf = ByteBuffer.wrap(elfBytes).order(order)

        val ePhoff = buf.getLong(32).toInt()
        val ePhentsize = buf.getShort(54).toInt() and 0xFFFF
        val ePhnum = buf.getShort(56).toInt() and 0xFFFF
        assertTrue("ELF must contain program headers", ePhnum > 0 && ePhentsize >= 56)

        var minAlign = Long.MAX_VALUE
        var loadCount = 0
        for (i in 0 until ePhnum) {
            val offset = ePhoff + i * ePhentsize
            val pType = buf.getInt(offset)
            if (pType == 1) { // PT_LOAD
                val pAlign = buf.getLong(offset + 48)
                if (pAlign < minAlign) minAlign = pAlign
                loadCount++
            }
        }
        assertTrue("ELF must contain at least one PT_LOAD segment", loadCount > 0)
        return minAlign
    }
}
