package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.security.BackupEncryptionManager
import com.example.security.PasswordHasher
import com.example.ui.viewmodels.BackupSyncViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EncryptedBackupRestoreTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: AppDao
    private lateinit var repository: PosRepository
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() = runBlocking(Dispatchers.IO) {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(context)
        dao = db.appDao()
        repository = PosRepository(dao, db)
        prefs = context.getSharedPreferences("test_backup_sync_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        db.clearAllTables()
        cleanupBackupDirs()
    }

    @After
    fun tearDown() = runBlocking(Dispatchers.IO) {
        db.clearAllTables()
        prefs.edit().clear().commit()
        cleanupBackupDirs()
    }

    private fun cleanupBackupDirs() {
        listOf("backups", "auto_backups", "safety_backups").forEach { dirName ->
            File(context.filesDir, dirName).deleteRecursively()
        }
    }

    @Test
    fun `passphrase backup roundtrip succeeds for authenticated manager and creates encrypted safety backup`() = runBlocking(Dispatchers.IO) {
        val managerPass = "ManagerSecret#2026"
        val managerHash = PasswordHasher.hashAsync(managerPass, iterations = 10_000)
        val manager = UserEntity(
            id = 1L,
            username = "manager_admin",
            passwordHash = managerHash,
            role = UserRole.MANAGER,
            canSell = true,
            canDeleteInvoice = true,
            canManageSettings = true
        )
        dao.insertUser(manager)
        dao.insertSettings(GlobalSettingsEntity(id = 1, shopName = "متجر الأمان", currency = "USD"))
        dao.insertCategory(CategoryEntity(id = 1L, name = "بقالة"))
        dao.insertProduct(
            ProductEntity(
                id = 10L,
                barcode = "61100010",
                name = "زيت زيتون ممتاز",
                categoryId = 1L,
                unit = "قارورة",
                purchasePrice = 45.0,
                sellPrice = 65.5,
                currentStock = 25.0,
                minStock = 3.0
            )
        )
        dao.insertCustomer(
            CustomerEntity(
                id = 5L,
                name = "خالد الزبون",
                phone = "0600112233",
                balance = 12550L,
                totalPurchases = 25000L,
                customerNumber = 1L,
                barcode = "store-1"
            )
        )
        dao.setInvoiceSequence(InvoiceSequenceEntity(id = 1, lastInvoiceNumber = 12L))

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = BackupSyncViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = testScope
        )

        try {
            val exportedDeferred = CompletableDeferred<File>()
            viewModel.performGoogleDriveBackup(
                encryptionPassword = managerPass,
                onReadyToShare = { file -> exportedDeferred.complete(file) }
            )
            val exportedFile = exportedDeferred.await()
            assertTrue("Exported encrypted backup file must exist", exportedFile.exists())

            val encryptedText = exportedFile.readText(Charsets.UTF_8)
            assertTrue(
                "Exported backup must use RMPOS_ENCRYPTED_BACKUP_V1 header",
                BackupEncryptionManager.isEncryptedBackup(encryptedText)
            )
            assertFalse(
                "Encrypted backup file must not leak plaintext product names",
                encryptedText.contains("زيت زيتون ممتاز")
            )

            // Mutate live database to simulate disaster / partial wipe before restore
            dao.clearAllProducts()
            dao.insertProduct(
                ProductEntity(
                    id = 999L,
                    barcode = "TEMP-999",
                    name = "بيانات مؤقتة قبل الاستعادة",
                    categoryId = 1L,
                    unit = "قطعة",
                    purchasePrice = 1.0,
                    sellPrice = 2.0,
                    currentStock = 1.0,
                    minStock = 0.0
                )
            )

            // Restore with authenticated manager and exact passphrase
            val restoreDeferred = CompletableDeferred<RestoreResult>()
            viewModel.restoreFromJson(
                backupString = encryptedText,
                decryptionPassword = managerPass,
                currentManager = manager,
                onResult = { result -> restoreDeferred.complete(result) }
            )
            val result = restoreDeferred.await()
            assertTrue("Restore must succeed: ${result.message}", result.success)
            assertEquals(1, result.productsCount)
            assertEquals(1, result.customersCount)

            val restoredProd = dao.getProductById(10L)
            assertNotNull("Original product must be restored", restoredProd)
            assertEquals("زيت زيتون ممتاز", restoredProd!!.name)
            assertEquals(6550L, restoredProd.sellPrice)
            assertEquals("USD", dao.getSettings()?.currency)
            assertEquals(12L, dao.getLastInvoiceNumber())

            // Verify pre-restore safety backup in safety_backups/ is Keystore-encrypted and readable
            val safetyDir = File(context.filesDir, "safety_backups")
            val safetyFiles = safetyDir.listFiles()?.toList().orEmpty()
            assertTrue("Pre-restore safety backup must be created", safetyFiles.isNotEmpty())
            val safetyFile = safetyFiles.first()
            assertTrue(safetyFile.name.endsWith(".enc.json"))
            val safetyCipherText = safetyFile.readText(Charsets.UTF_8)
            assertTrue(
                "Safety backup must carry Keystore header",
                BackupEncryptionManager.isEncryptedSystemBackup(safetyCipherText)
            )
            assertFalse(
                "Safety backup must not contain plaintext",
                safetyCipherText.contains("بيانات مؤقتة قبل الاستعادة")
            )
            val decryptedSafety = BackupEncryptionManager.decryptSystemBackup(context, safetyCipherText)
            assertTrue(decryptedSafety is BackupEncryptionManager.DecryptionResult.Success)
            val safetyJson = JSONObject((decryptedSafety as BackupEncryptionManager.DecryptionResult.Success).jsonContent)
            assertEquals(AppDatabase.CURRENT_SCHEMA_VERSION, safetyJson.getJSONObject("metadata").getInt("schemaVersion"))
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun `restore rejects wrong passphrase tampered ciphertext plaintext and non-manager user`() = runBlocking(Dispatchers.IO) {
        val rightPass = "RightPassword#123"
        val manager = UserEntity(
            id = 1L,
            username = "manager",
            passwordHash = PasswordHasher.hashAsync(rightPass, iterations = 10_000),
            role = UserRole.MANAGER
        )
        val cashier = UserEntity(
            id = 2L,
            username = "cashier",
            passwordHash = PasswordHasher.hashAsync(rightPass, iterations = 10_000),
            role = UserRole.CASHIER
        )

        val plainJson = DatabaseBackupManager.createBackupJson(db, "", "")
        val encrypted = BackupEncryptionManager.encryptBackup(plainJson, rightPass)

        // 1. Direct decryption failure with wrong passphrase
        val wrongDec = BackupEncryptionManager.decryptBackup(encrypted, "WrongPassword!999")
        assertTrue(wrongDec is BackupEncryptionManager.DecryptionResult.Error)

        // 2. Tampered ciphertext rejected by AES-GCM tag verification
        val lines = encrypted.lines().toMutableList()
        val cipherChars = lines[2].toCharArray()
        cipherChars[cipherChars.size / 2] = if (cipherChars[cipherChars.size / 2] == 'A') 'B' else 'A'
        lines[2] = String(cipherChars)
        val tampered = lines.joinToString("\n")
        val tamperedDec = BackupEncryptionManager.decryptBackup(tampered, rightPass)
        assertTrue("Tampered ciphertext must fail AEAD tag check", tamperedDec is BackupEncryptionManager.DecryptionResult.Error)

        // 3. Plaintext JSON backup rejected
        val plaintextDec = BackupEncryptionManager.decryptBackup(plainJson, rightPass)
        assertTrue("Plaintext JSON must be rejected", plaintextDec is BackupEncryptionManager.DecryptionResult.Error)

        // 4. BackupSyncViewModel enforces manager role, password verification, and encrypted format
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = BackupSyncViewModel(repository, context, prefs, externalScope = testScope)
        try {
            val cashierDeferred = CompletableDeferred<RestoreResult>()
            viewModel.restoreFromJson(encrypted, rightPass, cashier) { cashierDeferred.complete(it) }
            assertFalse("Cashier cannot restore backups", cashierDeferred.await().success)

            val wrongPassVmDeferred = CompletableDeferred<RestoreResult>()
            viewModel.restoreFromJson(encrypted, "WrongPass", manager) { wrongPassVmDeferred.complete(it) }
            assertFalse("Wrong manager password must fail", wrongPassVmDeferred.await().success)

            val plainVmDeferred = CompletableDeferred<RestoreResult>()
            viewModel.restoreFromJson(plainJson, rightPass, manager) { plainVmDeferred.complete(it) }
            assertFalse("Plaintext backup restore must be blocked", plainVmDeferred.await().success)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun `auto backup writes encrypted enc_json without plaintext leak and decrypts cleanly`() = runBlocking(Dispatchers.IO) {
        dao.insertCategory(CategoryEntity(id = 1L, name = "مشروبات"))
        dao.insertProduct(
            ProductEntity(
                id = 77L,
                barcode = "7770001",
                name = "عصير رمان طبيعي 1L",
                categoryId = 1L,
                unit = "قطعة",
                purchasePrice = 10.0,
                sellPrice = 15.0,
                currentStock = 12.0,
                minStock = 2.0
            )
        )

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = BackupSyncViewModel(repository, context, prefs, externalScope = testScope)

        try {
            val autoDeferred = CompletableDeferred<Pair<Boolean, String>>()
            viewModel.performAutoBackup(context) { ok, msg -> autoDeferred.complete(ok to msg) }
            val (ok, msg) = autoDeferred.await()
            assertTrue("Auto backup should succeed: $msg", ok)

            val autoDir = File(context.filesDir, "auto_backups")
            val files = autoDir.listFiles()?.toList().orEmpty()
            assertEquals(1, files.size)
            val autoFile = files.first()
            assertTrue("Auto backup filename must end with .enc.json", autoFile.name.endsWith(".enc.json"))

            val cipherText = autoFile.readText(Charsets.UTF_8)
            assertTrue(
                "Auto backup must start with Keystore encrypted header",
                BackupEncryptionManager.isEncryptedSystemBackup(cipherText)
            )
            assertFalse("Auto backup must not leak raw JSON braces", cipherText.trimStart().startsWith("{"))
            assertFalse("Auto backup must not contain plaintext product names", cipherText.contains("عصير رمان طبيعي 1L"))

            val decrypted = BackupEncryptionManager.decryptSystemBackup(context, cipherText)
            assertTrue(decrypted is BackupEncryptionManager.DecryptionResult.Success)
            val root = JSONObject((decrypted as BackupEncryptionManager.DecryptionResult.Success).jsonContent)
            assertEquals(AppDatabase.CURRENT_SCHEMA_VERSION, root.getJSONObject("metadata").getInt("schemaVersion"))
            val products = root.getJSONArray("products")
            assertEquals(1, products.length())
            assertEquals("عصير رمان طبيعي 1L", products.getJSONObject(0).getString("name"))
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun `auto backup mirrors encrypted file to configured off-device SAF folder and reports status honestly`() = runBlocking(Dispatchers.IO) {
        dao.insertCategory(CategoryEntity(id = 1L, name = "مخبوزات"))
        dao.insertProduct(
            ProductEntity(
                id = 88L,
                barcode = "8880001",
                name = "خبز كامل",
                categoryId = 1L,
                unit = "قطعة",
                purchasePrice = 2.0,
                sellPrice = 3.0,
                currentStock = 40.0,
                minStock = 5.0
            )
        )

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = BackupSyncViewModel(repository, context, prefs, externalScope = testScope)
        val capturedExternalWrites = mutableListOf<Triple<String, String, ByteArray>>()

        try {
            assertFalse("driveConnected/hasExternalBackupFolder must be false before folder selection", viewModel.hasExternalBackupFolder.value)
            assertFalse(viewModel.driveConnected.value)

            val fakeTreeUri = android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ARM_POS_Backups")
            assertTrue(viewModel.configureExternalBackupFolder(fakeTreeUri))
            assertTrue(viewModel.hasExternalBackupFolder.value)
            assertTrue(viewModel.driveConnected.value)
            assertEquals("RM_POS_Backups", viewModel.externalBackupFolderLabel.value)

            ExternalSafBackupManager.testWriteOverride = { uri, fileName, bytes ->
                capturedExternalWrites.add(Triple(uri.toString(), fileName, bytes))
                ExternalBackupWriteResult(configured = true, success = true, fileName = fileName)
            }

            val autoDeferred = CompletableDeferred<Pair<Boolean, String>>()
            viewModel.performAutoBackup(context) { ok, msg -> autoDeferred.complete(ok to msg) }
            val (ok, msg) = autoDeferred.await()
            assertTrue("Auto backup with SAF folder should succeed: $msg", ok)
            assertTrue("Status message must report external folder mirror: $msg", msg.contains("RM_POS_Backups"))
            assertEquals("Encrypted auto backup must be written to SAF folder", 1, capturedExternalWrites.size)
            val (_, extName, extBytes) = capturedExternalWrites.first()
            assertTrue(extName.endsWith(".enc.json"))
            val extText = String(extBytes, Charsets.UTF_8)
            assertTrue(BackupEncryptionManager.isEncryptedSystemBackup(extText))
            assertFalse("External SAF auto-backup must never contain plaintext", extText.contains("خبز كامل"))
            assertTrue("Last external sync timestamp must be recorded", viewModel.lastExternalAutoBackupTime.value > 0L)

            viewModel.clearExternalBackupFolder()
            assertFalse(viewModel.hasExternalBackupFolder.value)
            assertEquals("", viewModel.externalBackupFolderLabel.value)
        } finally {
            ExternalSafBackupManager.testWriteOverride = null
            testScope.cancel()
        }
    }

    @Test
    fun `external export reminder cycle tracks overdue snooze and export completion`() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = BackupSyncViewModel(repository, context, prefs, externalScope = testScope)

        try {
            // Fresh install with no prior export -> reminder due, days = -1
            assertTrue(viewModel.isExternalExportReminderDue.value)
            assertEquals(-1, viewModel.daysSinceLastExternalExport.value)

            val t0 = 1_750_000_000_000L
            viewModel.markExternalExportCompleted(t0)
            assertFalse(viewModel.isExternalExportReminderDue.value)
            assertEquals(0, viewModel.daysSinceLastExternalExport.value)

            // 3 days later -> still within 7-day reminder window
            viewModel.refreshExternalExportReminder(t0 + 3L * BackupSyncViewModel.MILLIS_PER_DAY)
            assertFalse(viewModel.isExternalExportReminderDue.value)
            assertEquals(3, viewModel.daysSinceLastExternalExport.value)

            // 7 days later -> overdue
            val dueTime = t0 + 7L * BackupSyncViewModel.MILLIS_PER_DAY
            viewModel.refreshExternalExportReminder(dueTime)
            assertTrue(viewModel.isExternalExportReminderDue.value)
            assertEquals(7, viewModel.daysSinceLastExternalExport.value)

            // Dismiss for today snoozes for 24h
            viewModel.dismissExternalExportReminderForToday(dueTime)
            assertFalse(viewModel.isExternalExportReminderDue.value)

            // 25 hours later -> due again
            viewModel.refreshExternalExportReminder(dueTime + BackupSyncViewModel.MILLIS_PER_DAY + 3_600_000L)
            assertTrue(viewModel.isExternalExportReminderDue.value)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun `dao search escapes SQL LIKE percent and underscore wildcards`() = runBlocking(Dispatchers.IO) {
        assertEquals("50\\% off\\_now\\\\end", escapeSqlLike("50% off_now\\end"))

        dao.insertCategory(CategoryEntity(id = 1L, name = "عام"))
        dao.insertProduct(
            ProductEntity(
                id = 1L,
                barcode = "B-50%",
                name = "خصم 50% خاص",
                categoryId = 1L,
                unit = "قطعة",
                purchasePrice = 10.0,
                sellPrice = 15.0,
                currentStock = 10.0,
                minStock = 1.0
            )
        )
        dao.insertProduct(
            ProductEntity(
                id = 2L,
                barcode = "B-500",
                name = "خصم 500 عام",
                categoryId = 1L,
                unit = "قطعة",
                purchasePrice = 10.0,
                sellPrice = 15.0,
                currentStock = 10.0,
                minStock = 1.0
            )
        )
        dao.insertProduct(
            ProductEntity(
                id = 3L,
                barcode = "ITEM_1",
                name = "item_1 literal",
                categoryId = 1L,
                unit = "قطعة",
                purchasePrice = 10.0,
                sellPrice = 15.0,
                currentStock = 10.0,
                minStock = 1.0
            )
        )
        dao.insertProduct(
            ProductEntity(
                id = 4L,
                barcode = "ITEMX1",
                name = "itemA1 wildcard",
                categoryId = 1L,
                unit = "قطعة",
                purchasePrice = 10.0,
                sellPrice = 15.0,
                currentStock = 10.0,
                minStock = 1.0
            )
        )

        val percentMatches = dao.getProductsFilteredPaged("50%", null, 50, 0).first()
        assertEquals(listOf("خصم 50% خاص"), percentMatches.map { it.name })

        val underscoreMatches = dao.searchProducts("item_1").first()
        assertEquals(listOf("item_1 literal"), underscoreMatches.map { it.name })

        dao.insertCustomer(CustomerEntity(id = 1L, name = "شركة A_B", phone = "0600", customerNumber = 1L))
        dao.insertCustomer(CustomerEntity(id = 2L, name = "شركة AXB", phone = "0601", customerNumber = 2L))
        val custMatches = dao.getCustomersFilteredPaged("A_B", 50, 0).first()
        assertEquals(listOf("شركة A_B"), custMatches.map { it.name })
    }

    @Test
    fun `atomic invoice sequence table increments monotonically and survives voiding and deletion`() = runBlocking {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val inMemoryDao = inMemoryDb.appDao()
            val inMemoryRepo = PosRepository(inMemoryDao, inMemoryDb)

            inMemoryDao.insertProduct(
                ProductEntity(
                    id = 1L,
                    barcode = "SEQ-1",
                    name = "منتج تسلسل",
                    categoryId = 1L,
                    unit = "قطعة",
                    purchasePrice = 10.0,
                    sellPrice = 20.0,
                    currentStock = 100.0,
                    minStock = 5.0
                )
            )
            val manager = UserEntity(
                username = "admin",
                passwordHash = "hash",
                role = UserRole.MANAGER,
                canSell = true,
                canDeleteInvoice = true
            )

            val s1 = inMemoryRepo.recordSaleInvoice(
                SaleEntity(invoiceNumber = "", timestamp = 1000L, cashierName = "admin", totalAmount = 2000L, discountAmount = 0L, discountType = "VALUE", paidAmount = 2000L, changeAmount = 0L, paymentMethod = "CASH"),
                listOf(SaleItemEntity(saleId = 0L, productId = 1L, productName = "منتج تسلسل", quantity = 1.0, unitPrice = 20.0, totalLinePrice = 20.0)),
                manager
            )
            val s2 = inMemoryRepo.recordSaleInvoice(
                SaleEntity(invoiceNumber = "", timestamp = 2000L, cashierName = "admin", totalAmount = 2000L, discountAmount = 0L, discountType = "VALUE", paidAmount = 2000L, changeAmount = 0L, paymentMethod = "CASH"),
                listOf(SaleItemEntity(saleId = 0L, productId = 1L, productName = "منتج تسلسل", quantity = 1.0, unitPrice = 20.0, totalLinePrice = 20.0)),
                manager
            )
            assertEquals("0001", s1.invoiceNumber)
            assertEquals("0002", s2.invoiceNumber)
            assertEquals(2L, inMemoryDao.getLastInvoiceNumber())

            // Voiding and deleting s2 must preserve counter at 2 so s3 receives 0003
            inMemoryRepo.deleteSaleInvoice(s2.id, manager)
            assertEquals(2L, inMemoryDao.getLastInvoiceNumber())

            val s3 = inMemoryRepo.recordSaleInvoice(
                SaleEntity(invoiceNumber = "", timestamp = 3000L, cashierName = "admin", totalAmount = 2000L, discountAmount = 0L, discountType = "VALUE", paidAmount = 2000L, changeAmount = 0L, paymentMethod = "CASH"),
                listOf(SaleItemEntity(saleId = 0L, productId = 1L, productName = "منتج تسلسل", quantity = 1.0, unitPrice = 20.0, totalLinePrice = 20.0)),
                manager
            )
            assertEquals("0003", s3.invoiceNumber)
            assertEquals(3L, inMemoryDao.getLastInvoiceNumber())
        } finally {
            inMemoryDb.close()
        }
    }
}
