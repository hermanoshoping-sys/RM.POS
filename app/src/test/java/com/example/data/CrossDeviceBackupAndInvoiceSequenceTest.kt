package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.security.BackupEncryptionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبار النسخ الاحتياطي ونقله إلى جهاز ثانٍ، واختبار عدم إعادة استخدام أرقام الفواتير بعد الحذف الجزئي أو الكلي.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrossDeviceBackupAndInvoiceSequenceTest {

    private lateinit var deviceADb: AppDatabase
    private lateinit var deviceADao: AppDao
    private lateinit var deviceARepo: PosRepository

    private lateinit var deviceBDb: AppDatabase
    private lateinit var deviceBDao: AppDao
    private lateinit var deviceBRepo: PosRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. محاكاة قاعدة بيانات الجهاز الأول (Device A)
        deviceADb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deviceADao = deviceADb.appDao()
        deviceARepo = PosRepository(deviceADao, deviceADb)

        // 2. محاكاة قاعدة بيانات الجهاز الثاني (Device B) فارغة ونظيفة
        deviceBDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deviceBDao = deviceBDb.appDao()
        deviceBRepo = PosRepository(deviceBDao, deviceBDb)
    }

    @After
    fun tearDown() {
        deviceADb.close()
        deviceBDb.close()
    }

    @Test
    fun testCrossDeviceBackupExportAndRestoreOnSecondDevice() = runBlocking {
        // --- خطوة 1: تهيئة بيانات متكاملة على الجهاز الأول (Device A) ---
        val cat = CategoryEntity(id = 1L, name = "مشروبات")
        deviceADao.insertCategory(cat)

        val prod1 = ProductEntity(
            id = 10L,
            barcode = "111111",
            name = "عصير برتقال 1L",
            categoryId = 1L,
            unit = "قطعة",
            purchasePrice = 100.0,
            sellPrice = 150.0,
            currentStock = 50.0,
            minStock = 5.0
        )
        val prod2 = ProductEntity(
            id = 20L,
            barcode = "222222",
            name = "ماء معدني 0.5L",
            categoryId = 1L,
            unit = "قطعة",
            purchasePrice = 12.34,
            sellPrice = 19.75,
            currentStock = 100.0,
            minStock = 10.0
        )
        deviceADao.insertProduct(prod1)
        deviceADao.insertProduct(prod2)

        val cust = CustomerEntity(
            id = 100L,
            name = "سعيد التاجر",
            phone = "0612345678",
            balance = 50000L, // دين سابق 500 DA (50000 سنت)
            totalPurchases = 150000L
        )
        deviceADao.insertCustomer(cust)

        val sup = SupplierEntity(
            id = 200L,
            name = "موزع المشروبات المركزية",
            phone = "0522112233",
            debtBalance = 2000.0
        )
        deviceADao.insertSupplier(sup)

        // إنشاء فاتورة بيع مع تكلفة وحدة مسجلة وقيم عشرية دقيقة
        val sale = SaleEntity(
            id = 1L,
            invoiceNumber = "0001",
            timestamp = System.currentTimeMillis(),
            cashierName = "admin",
            customerId = 100L,
            customerName = "سعيد التاجر",
            totalAmount = 33851L, // 300.00 + 38.51 = 338.51 DA
            discountAmount = 99L, // 0.99 DA
            discountType = "VALUE",
            paidAmount = 10000L, // دفع 100.0 وبقي دين 238.51
            changeAmount = 0L,
            paymentMethod = "DEBT"
        )
        val saleItems = listOf(
            SaleItemEntity(
                id = 1L,
                saleId = 1L,
                productId = 10L,
                productName = "عصير برتقال 1L",
                quantity = 2.0,
                unitPrice = 150.0,
                unitCost = 100.0,
                totalLinePrice = 300.0
            ),
            SaleItemEntity(
                id = 2L,
                saleId = 1L,
                productId = 20L,
                productName = "ماء معدني 0.5L",
                quantity = 2.0,
                unitPrice = 19.75,
                discountValue = 0.99,
                unitCost = 12.34,
                totalLinePrice = 38.51
            )
        )
        deviceADao.insertSale(sale)
        deviceADao.insertSaleItemsBatch(saleItems)

        // --- خطوة 2: تصدير النسخة وتشفيرها بالمفتاح المحمول للنقل بين الأجهزة ---
        val rawJson = DatabaseBackupManager.createBackupJson(
            db = deviceADb,
            linkedEmail = "store@example.com",
            linkedPhone = "0600000000"
        )
        assertNotNull(rawJson)
        assertTrue(rawJson.contains("عصير برتقال 1L"))

        val userPassphrase = "EnterpriseUserSecurePass#2026"
        val encryptedBackup = BackupEncryptionManager.encryptBackup(
            rawJson,
            userPassphrase
        )
        assertTrue(BackupEncryptionManager.isEncryptedBackup(encryptedBackup))

        // --- خطوة 3: استعادة النسخة وفك تشفيرها على الجهاز الثاني (Device B) ---
        val decResult = BackupEncryptionManager.decryptBackup(
            encryptedBackup,
            userPassphrase
        )
        assertTrue("يجب أن ينجح فك التشفير على الجهاز الثاني", decResult is BackupEncryptionManager.DecryptionResult.Success)
        val plainJsonOnDeviceB = (decResult as BackupEncryptionManager.DecryptionResult.Success).jsonContent

        val restoreResult = DatabaseBackupManager.restoreDatabaseFromJson(deviceBDb, plainJsonOnDeviceB)
        assertTrue("يجب أن تنجح الاستعادة الذرية على الجهاز الثاني", restoreResult.success)
        assertEquals(2, restoreResult.productsCount)
        assertEquals(1, restoreResult.customersCount)
        assertEquals(1, restoreResult.salesCount)

        // التحقق من سلامة البيانات في قاعدة بيانات الجهاز الثاني
        val bProducts = deviceBDao.getAllProductsList()
        assertEquals(2, bProducts.size)
        val bProd2 = deviceBDao.getProductById(20L)!!
        assertEquals(1234L, bProd2.purchasePrice)
        assertEquals(1975L, bProd2.sellPrice)
        assertEquals(19.75, bProd2.sellPriceDouble, 0.001)

        val bCust = deviceBDao.getCustomerById(100L)
        assertNotNull(bCust)
        assertEquals("سعيد التاجر", bCust?.name)

        val bSale = deviceBDao.getSaleById(1L)
        assertNotNull(bSale)
        assertEquals("0001", bSale?.invoiceNumber)
        assertEquals(33851L, bSale?.totalAmount)

        val bSaleItems = deviceBDao.getSaleItems(1L)
        assertEquals(2, bSaleItems.size)
        assertEquals(10000L, bSaleItems[0].unitCost)
        assertEquals(100.0, bSaleItems[0].unitCostDouble, 0.001)
        assertEquals(1975L, bSaleItems[1].unitPrice)
        assertEquals(1234L, bSaleItems[1].unitCost)
        assertEquals(99L, bSaleItems[1].discountValue)
        assertEquals(3851L, bSaleItems[1].totalLinePrice)
    }

    @Test
    fun testInvoiceNumberNeverReusedAfterDeletion() = runBlocking {
        // تجهيز منتج للبيع
        val prod = ProductEntity(
            id = 1L,
            barcode = "999",
            name = "منتج تجريبي",
            categoryId = 1L,
            unit = "قطعة",
            purchasePrice = 50.0,
            sellPrice = 100.0,
            currentStock = 100.0,
            minStock = 5.0
        )
        deviceADao.insertProduct(prod)

        val adminUser = UserEntity(
            username = "admin",
            passwordHash = "hash123",
            role = UserRole.MANAGER,
            canSell = true,
            canDeleteInvoice = true
        )

        // 1. تسجيل الفاتورة الأولى (تأخذ تلقائياً الرقم 0001)
        val sale1 = deviceARepo.recordSaleInvoice(
            SaleEntity(
                invoiceNumber = "",
                timestamp = System.currentTimeMillis(),
                cashierName = "admin",
                totalAmount = 10000L,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 10000L,
                changeAmount = 0L,
                paymentMethod = "CASH"
            ),
            listOf(SaleItemEntity(saleId = 0L, productId = 1L, productName = "منتج تجريبي", quantity = 1.0, unitPrice = 100.0)),
            adminUser
        )
        assertEquals("0001", sale1.invoiceNumber)

        // 2. تسجيل الفاتورة الثانية (تأخذ تلقائياً الرقم 0002)
        val sale2 = deviceARepo.recordSaleInvoice(
            SaleEntity(
                invoiceNumber = "",
                timestamp = System.currentTimeMillis(),
                cashierName = "admin",
                totalAmount = 20000L,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 20000L,
                changeAmount = 0L,
                paymentMethod = "CASH"
            ),
            listOf(SaleItemEntity(saleId = 0L, productId = 1L, productName = "منتج تجريبي", quantity = 2.0, unitPrice = 100.0)),
            adminUser
        )
        assertEquals("0002", sale2.invoiceNumber)

        // 3. حذف الفاتورة الثانية (0002)
        deviceARepo.deleteSaleInvoice(sale2.id, adminUser)
        assertNull("يجب أن تُحذف الفاتورة 0002", deviceADao.getSaleById(sale2.id))

        // 4. تسجيل فاتورة جديدة بعد الحذف: يجب ألا يعاد استخدام الرقم 0002 إطلاقاً، بل تأخذ 0003
        val sale3 = deviceARepo.recordSaleInvoice(
            SaleEntity(
                invoiceNumber = "",
                timestamp = System.currentTimeMillis(),
                cashierName = "admin",
                totalAmount = 10000L,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 10000L,
                changeAmount = 0L,
                paymentMethod = "CASH"
            ),
            listOf(SaleItemEntity(saleId = 0L, productId = 1L, productName = "منتج تجريبي", quantity = 1.0, unitPrice = 100.0)),
            adminUser
        )
        assertEquals("يجب ألا يعاد استخدام رقم الفاتورة المحذوفة أبداً بل يتبع التسلسل الصارم", "0003", sale3.invoiceNumber)
    }

    @Test
    fun testPartialInvoiceDeletionDebtAndStockIntegrity() = runBlocking {
        val prod = ProductEntity(
            id = 50L,
            barcode = "555",
            name = "أرز فاخر 5 كغ",
            categoryId = 1L,
            unit = "قطعة",
            purchasePrice = 300.0,
            sellPrice = 450.0,
            currentStock = 20.0,
            minStock = 2.0
        )
        deviceADao.insertProduct(prod)

        val cust = CustomerEntity(
            id = 500L,
            name = "أحمد زبون دين",
            phone = "0699887766",
            balance = 0L,
            totalPurchases = 0L
        )
        deviceADao.insertCustomer(cust)

        val manager = UserEntity(
            username = "admin",
            passwordHash = "hash123",
            role = UserRole.MANAGER,
            canSell = true,
            canDeleteInvoice = true
        )

        // بيع فاتورة بمبلغ 900.0 DA (كمية 2 أرز بسعر 450.0) مع دفع جزئي 300.0 DA ودين متبقي 600.0 DA
        val sale = deviceARepo.recordSaleInvoice(
            SaleEntity(
                invoiceNumber = "",
                timestamp = System.currentTimeMillis(),
                cashierName = "admin",
                customerId = 500L,
                customerName = "أحمد زبون دين",
                totalAmount = 90000L, // 900.0 DA
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 30000L,  // 300.0 DA دفع جزئي
                changeAmount = 0L,
                paymentMethod = "DEBT"
            ),
            listOf(
                SaleItemEntity(saleId = 0L, productId = 50L, productName = "أرز فاخر 5 كغ", quantity = 2.0, unitPrice = 450.0, totalLinePrice = 900.0)
            ),
            manager
        )

        // التحقق من إنقاص المخزون وزيادة دين الزبون بالمبلغ المتبقي فقط (600 DA)
        val prodAfterSale = deviceADao.getProductById(50L)!!
        assertEquals(18.0, prodAfterSale.currentStock, 0.001)

        val custAfterSale = deviceADao.getCustomerById(500L)!!
        assertEquals(60000L, custAfterSale.balance) // دين 600 DA
        assertEquals(90000L, custAfterSale.totalPurchases)

        // الآن: حذف الفاتورة ذات الدفع الجزئي
        deviceARepo.deleteSaleInvoice(sale.id, manager)

        // 1. التحقق من استرجاع المخزون بالكامل إلى 20.0
        val prodAfterDelete = deviceADao.getProductById(50L)!!
        assertEquals(20.0, prodAfterDelete.currentStock, 0.001)

        // 2. التحقق من خصم الدين المتبقي فقط (600 DA) وعودة رصيد الزبون إلى 0 DA
        val custAfterDelete = deviceADao.getCustomerById(500L)!!
        assertEquals(0L, custAfterDelete.balance)
        assertEquals(0L, custAfterDelete.totalPurchases)

        // 3. التحقق من عدم بقاء الفاتورة في السجل
        assertNull(deviceADao.getSaleById(sale.id))
    }

    @Test
    fun testExternalSafOffDeviceBackupAndCleanDeviceRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("saf_clean_restore_test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Seed Device A with rich store state (settings, category, product, customer, supplier, expense, invoice)
        deviceADao.insertSettings(
            GlobalSettingsEntity(
                id = 1,
                shopName = "متجر الأطلس السحابي",
                currency = "MAD",
                printerPaperWidth = 80
            )
        )
        deviceADao.insertCategory(CategoryEntity(id = 7L, name = "مخبوزات"))
        deviceADao.insertProduct(
            ProductEntity(
                id = 77L,
                barcode = "CLEAN-77",
                name = "خبز القمح الكامل",
                categoryId = 7L,
                unit = "قطعة",
                purchasePrice = 300L,
                sellPrice = 550L,
                currentStock = 42.0,
                minStock = 5.0
            )
        )
        deviceADao.insertCustomer(
            CustomerEntity(
                id = 701L,
                name = "كريم الزبون",
                phone = "0611223344",
                balance = 12500L,
                totalPurchases = 45000L
            )
        )
        deviceADao.insertSupplier(
            SupplierEntity(
                id = 801L,
                name = "مطاحن المغرب",
                phone = "0522334455",
                debtBalance = 80000L
            )
        )
        deviceADao.insertExpense(
            ExpenseEntity(
                id = 901L,
                title = "فاتورة الكهرباء",
                amount = 32000L,
                timestamp = 1_750_000_000_000L,
                category = "فواتير"
            )
        )
        deviceADao.insertSale(
            SaleEntity(
                id = 55L,
                invoiceNumber = "0042",
                timestamp = 1_750_000_000_000L,
                cashierName = "admin",
                customerId = 701L,
                customerName = "كريم الزبون",
                totalAmount = 1100L,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 1100L,
                changeAmount = 0L,
                paymentMethod = "CASH"
            )
        )
        deviceADao.insertSaleItem(
            SaleItemEntity(
                id = 551L,
                saleId = 55L,
                productId = 77L,
                productName = "خبز القمح الكامل",
                quantity = 2.0,
                unitPrice = 550L,
                totalLinePrice = 1100L
            )
        )

        // Capture encrypted bytes written to external SAF folder
        var offDeviceBytes: ByteArray? = null
        var writtenFileName: String? = null
        ExternalSafBackupManager.testWriteOverride = { _, fileName, contentBytes ->
            writtenFileName = fileName
            offDeviceBytes = contentBytes
            ExternalBackupWriteResult(configured = true, success = true, fileName = fileName)
        }
        try {
            val externalUri = android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ARMPOS_Backups")
            ExternalSafBackupManager.saveTreeUri(context, prefs, externalUri)

            val rawJson = DatabaseBackupManager.createBackupJson(deviceADb, "owner@store.ma", "0600112233")
            val passphrase = "CleanDeviceRestore#2026"
            val encryptedPayload = BackupEncryptionManager.encryptBackup(rawJson, passphrase)
            val writeRes = ExternalSafBackupManager.writeEncryptedBackup(
                context = context,
                prefs = prefs,
                fileName = "pos_backup_clean_device.enc.json",
                encryptedContent = encryptedPayload
            )
            assertTrue("External SAF write must succeed", writeRes.configured && writeRes.success)
            assertEquals("pos_backup_clean_device.enc.json", writtenFileName)
            assertNotNull("Off-device backup bytes must be persisted", offDeviceBytes)

            // Confirm Device B is completely clean (zero rows in every table)
            assertTrue(deviceBDao.getAllProductsList().isEmpty())
            assertTrue(deviceBDao.getAllCustomersList().isEmpty())
            assertTrue(deviceBDao.getAllSalesList().isEmpty())
            assertNull(deviceBDao.getSettings())

            // Restore from off-device encrypted file on Clean Device B
            val offDeviceEncryptedText = String(offDeviceBytes!!, Charsets.UTF_8)
            val decrypted = BackupEncryptionManager.decryptBackup(offDeviceEncryptedText, passphrase)
            assertTrue(decrypted is BackupEncryptionManager.DecryptionResult.Success)
            val plainRestoredJson = (decrypted as BackupEncryptionManager.DecryptionResult.Success).jsonContent

            val restoreResult = DatabaseBackupManager.restoreDatabaseFromJson(deviceBDb, plainRestoredJson)
            assertTrue("Clean device restore must succeed: ${restoreResult.message}", restoreResult.success)
            assertEquals(AppDatabase.CURRENT_SCHEMA_VERSION, org.json.JSONObject(plainRestoredJson).getJSONObject("metadata").getInt("schemaVersion"))
            assertEquals("متجر الأطلس السحابي", deviceBDao.getSettings()?.shopName)
            assertEquals(80, deviceBDao.getSettings()?.printerPaperWidth)
            assertEquals(550L, deviceBDao.getProductById(77L)?.sellPrice)
            assertEquals(42.0, deviceBDao.getProductById(77L)?.currentStock ?: 0.0, 0.001)
            assertEquals(12500L, deviceBDao.getCustomerById(701L)?.balance)
            assertEquals(80000L, deviceBDao.getSupplierById(801L)?.debtBalance)
            assertEquals("0042", deviceBDao.getSaleById(55L)?.invoiceNumber)
            assertEquals(42L, restoreResult.lastInvoiceSequence)
        } finally {
            ExternalSafBackupManager.testWriteOverride = null
            prefs.edit().clear().commit()
        }
    }
}
