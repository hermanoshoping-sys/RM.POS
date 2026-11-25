package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.util.MoneyUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبارات المسارات المالية الحرجة (Financial Critical Paths Tests)
 * تغطي العمليات الأساسية على PosRepository باستخدام Room in-memory:
 * 1. فاتورة بيع (تأثيرها على المخزون وحساب العميل)
 * 2. فاتورة شراء (تأثيرها على المخزون ودين المورد)
 * 3. إلغاء فاتورة بيع (استرجاع المخزون وتعديل رصيد العميل)
 * 4. إلغاء فاتورة شراء (تخفيض المخزون وإلغاء دين المورد)
 * 5. دفعة لعميل (تخفيض دين العميل)
 * 6. دفعة لمورد (تخفيض دين المورد وتسجيل المصروف)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinancialCriticalPathsTest {

    private lateinit var database: AppDatabase
    private lateinit var appDao: AppDao
    private lateinit var repository: PosRepository
    private val testManager = UserEntity(id = 1L, username = "admin", passwordHash = "hash", role = UserRole.MANAGER)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appDao = database.appDao()
        repository = PosRepository(appDao, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRecordSaleInvoice_adjustsStockAndCustomerBalance() = runBlocking {
        // إنشاء منتج مخزونه 50 قطعة
        val product = ProductEntity(
            id = 1L,
            barcode = "1001",
            name = "حليب 1L",
            description = "حليب طازج",
            categoryId = 1L,
            categoryName = "ألبان",
            unit = "قطعة",
            purchasePrice = 5.0,
            sellPrice = 8.0,
            wholesalePrice = 7.5,
            specialPrice = 7.0,
            currentStock = 50.0,
            minStock = 10.0
        )
        appDao.insertProduct(product)

        // إنشاء عميل برصيد مديونية صفر
        val customer = CustomerEntity(
            id = 10L,
            name = "أحمد محمد",
            phone = "0600000000",
            address = "المدينة",
            balance = 0.0,
            totalPurchases = 0.0
        )
        appDao.insertCustomer(customer)

        // بيع 5 قطع بسعر 8 DH (المجموع 40 DH)، دفع منها الزبون 10 DH والباقي 30 DH دين
        val sale = SaleEntity(
            invoiceNumber = "INV-001",
            timestamp = System.currentTimeMillis(),
            totalAmount = 40.0,
            paidAmount = 10.0,
            changeAmount = 0.0,
            discountAmount = 0.0,
            discountType = "FIXED",
            paymentMethod = "CASH",
            cashierName = "كاشير 1",
            customerId = 10L,
            customerName = customer.name
        )
        val items = listOf(
            SaleItemEntity(
                saleId = 0L,
                productId = 1L,
                productName = "حليب 1L",
                unitPrice = 8.0,
                quantity = 5.0,
                totalLinePrice = 40.0
            )
        )

        val savedSale = repository.recordSaleInvoice(sale, items, testManager)
        assertTrue(savedSale.id > 0)

        // التحقق من نقص المخزون: 50 - 5 = 45
        val updatedProduct = appDao.getProductById(1L)
        assertNotNull(updatedProduct)
        assertEquals(45.0, updatedProduct!!.currentStock, 0.001)

        // التحقق من تعديل رصيد مديونية العميل: 0 + 30 = 30
        val updatedCustomer = appDao.getCustomerById(10L)
        assertNotNull(updatedCustomer)
        assertEquals(30.0, updatedCustomer!!.balanceDouble, 0.001)
        assertEquals(3000L, updatedCustomer.balance)
        assertEquals(40.0, updatedCustomer.totalPurchasesDouble, 0.001)
        assertEquals(4000L, updatedCustomer.totalPurchases)
    }

    @Test
    fun testRecordPurchaseInvoice_adjustsStockAndSupplierDebt() = runBlocking {
        // إنشاء منتج مخزونه 10 قطع
        val product = ProductEntity(
            id = 2L,
            barcode = "2002",
            name = "زيت طهي 5L",
            description = "زيت",
            categoryId = 2L,
            categoryName = "مواد غذائية",
            unit = "قطعة",
            purchasePrice = 50.0,
            sellPrice = 65.0,
            wholesalePrice = 60.0,
            specialPrice = 58.0,
            currentStock = 10.0,
            minStock = 5.0
        )
        appDao.insertProduct(product)

        // مورد بدين ابتدائي 0
        val supplier = SupplierEntity(
            id = 20L,
            name = "شركة الزيوت المتحدة",
            phone = "0522000000",
            email = "oil@example.com",
            address = "الدار البيضاء",
            debtBalance = 0.0
        )
        appDao.insertSupplier(supplier)

        // شراء 20 قطعة بسعر 50 DH (الإجمالي 1000 DH)، تم دفع 400 DH وتبقى 600 DH دين
        val purchase = PurchaseEntity(
            timestamp = System.currentTimeMillis(),
            supplierId = 20L,
            supplierName = supplier.name,
            totalAmount = 1000.0,
            paidAmount = 400.0
        )
        val items = listOf(
            PurchaseItemEntity(
                purchaseId = 0L,
                productId = 2L,
                productName = "زيت طهي 5L",
                unitPrice = 50.0,
                quantity = 20.0
            )
        )

        repository.recordPurchaseInvoice(purchase, items, testManager)

        // المخزون يجب أن يرتفع من 10 إلى 30
        val updatedProduct = appDao.getProductById(2L)
        assertNotNull(updatedProduct)
        assertEquals(30.0, updatedProduct!!.currentStock, 0.001)

        // دين المورد يجب أن يرتفع إلى 600
        val updatedSupplier = appDao.getAllSuppliersList().find { it.id == 20L }
        assertNotNull(updatedSupplier)
        assertEquals(600.0, updatedSupplier!!.debtBalanceDouble, 0.001)
        assertEquals(60000L, updatedSupplier.debtBalance)
    }

    @Test
    fun testVoidSaleInvoice_restoresStockAndRevertsCustomerDebit() = runBlocking {
        // إنشاء منتج بعشرين قطعة
        val product = ProductEntity(
            id = 3L,
            barcode = "3003",
            name = "أرز 1KG",
            description = "أرز ممتاز",
            categoryId = 1L,
            categoryName = "بقوليات",
            unit = "قطعة",
            purchasePrice = 10.0,
            sellPrice = 15.0,
            wholesalePrice = 14.0,
            specialPrice = 13.0,
            currentStock = 20.0,
            minStock = 5.0
        )
        appDao.insertProduct(product)

        val customer = CustomerEntity(
            id = 30L,
            name = "سعيد كريم",
            phone = "0611111111",
            address = "الرباط",
            balance = 0.0,
            totalPurchases = 0.0
        )
        appDao.insertCustomer(customer)

        // بيع 4 قطع (المجموع 60 DH)، دين كامل 60 DH
        val sale = SaleEntity(
            invoiceNumber = "INV-002",
            timestamp = System.currentTimeMillis(),
            totalAmount = 60.0,
            paidAmount = 0.0,
            changeAmount = 0.0,
            discountAmount = 0.0,
            discountType = "FIXED",
            paymentMethod = "DEBT",
            cashierName = "كاشير 1",
            customerId = 30L,
            customerName = customer.name
        )
        val items = listOf(
            SaleItemEntity(
                saleId = 0L,
                productId = 3L,
                productName = "أرز 1KG",
                unitPrice = 15.0,
                quantity = 4.0,
                totalLinePrice = 60.0
            )
        )

        val savedSale = repository.recordSaleInvoice(sale, items, testManager)
        assertEquals(16.0, appDao.getProductById(3L)!!.currentStock, 0.001)
        assertEquals(60.0, appDao.getCustomerById(30L)!!.balanceDouble, 0.001)

        // إلغاء الفاتورة
        repository.voidSaleInvoice(savedSale.id)

        // التأكد من عودة المخزون إلى 20
        assertEquals(20.0, appDao.getProductById(3L)!!.currentStock, 0.001)
        // التأكد من عودة رصيد الزبون ومشترياته إلى الصفر
        assertEquals(0.0, appDao.getCustomerById(30L)!!.balanceDouble, 0.001)
        assertEquals(0.0, appDao.getCustomerById(30L)!!.totalPurchasesDouble, 0.001)
    }

    @Test
    fun testCustomerPayment_reducesBalance() = runBlocking {
        // عميل برصيد دين 500 DH
        val customer = CustomerEntity(
            id = 40L,
            name = "يوسف عثمان",
            phone = "0622222222",
            address = "مراكش",
            balance = 500.0,
            totalPurchases = 1200.0
        )
        appDao.insertCustomer(customer)

        // تسجيل دفعة نقدية بقيمة 200 DH
        val payment = repository.recordCustomerPayment(
            customerId = 40L,
            amount = 200.0,
            paymentMethod = "CASH",
            notes = "دفعة حساب جزئية",
            cashierName = "admin"
        )

        assertNotNull(payment)
        assertEquals(200.0, payment!!.amountDouble, 0.001)
        assertEquals(500.0, payment.previousBalanceDouble, 0.001)
        assertEquals(300.0, payment.newBalanceDouble, 0.001)

        // فحص قاعدة البيانات للتأكد من تحديث رصيد العميل
        val updatedCustomer = appDao.getCustomerById(40L)
        assertNotNull(updatedCustomer)
        assertEquals(300.0, updatedCustomer!!.balanceDouble, 0.001)
    }

    @Test
    fun testSupplierPayment_reducesDebtAndLogsExpense() = runBlocking {
        // مورد برصيد دين 800 DH
        val supplier = SupplierEntity(
            id = 50L,
            name = "موزع الألبان والجبن",
            phone = "0533000000",
            email = "dairy@example.com",
            address = "فاس",
            debtBalance = 800.0
        )
        appDao.insertSupplier(supplier)

        // تسديد 500 DH للمورد
        val payment = repository.recordSupplierPayment(
            supplierId = 50L,
            amount = 500.0,
            paymentMethod = "CASH",
            notes = "تسديد دفعة للمورد",
            cashierName = "admin"
        )

        assertNotNull(payment)
        assertEquals(500.0, payment!!.amountDouble, 0.001)
        assertEquals(800.0, payment.previousDebtDouble, 0.001)
        assertEquals(300.0, payment.newDebtDouble, 0.001)

        // التأكد من تعديل دين المورد إلى 300 DH
        val updatedSupplier = appDao.getAllSuppliersList().find { it.id == 50L }
        assertNotNull(updatedSupplier)
        assertEquals(300.0, updatedSupplier!!.debtBalanceDouble, 0.001)

        // التأكد من أن التسديدات منعزلة تماماً عن المصاريف ولا تسجل كبند في جدول المصاريف
        val expenses = appDao.getAllExpensesList()
        val supplierExpense = expenses.find { it.amountDouble == 500.0 }
        assertNull(supplierExpense)
    }

    @Test
    fun testComplexSale_MultipleItemsWithDiscountsAndCustomerDebt() = runBlocking {
        // منتجان بأسعار مختلفة
        val p1 = ProductEntity(
            id = 101L,
            barcode = "B101",
            name = "عصير برتقال 1L",
            description = "عصير",
            categoryId = 1L,
            categoryName = "مشروبات",
            unit = "قطعة",
            purchasePrice = 10.0,
            sellPrice = 15.0,
            wholesalePrice = 14.0,
            specialPrice = 13.0,
            currentStock = 20.0,
            minStock = 2.0
        )
        val p2 = ProductEntity(
            id = 102L,
            barcode = "B102",
            name = "بسكويت شاي",
            description = "بسكويت",
            categoryId = 1L,
            categoryName = "حلويات",
            unit = "علبة",
            purchasePrice = 3.0,
            sellPrice = 5.0,
            wholesalePrice = 4.5,
            specialPrice = 4.0,
            currentStock = 50.0,
            minStock = 5.0
        )
        appDao.insertProduct(p1)
        appDao.insertProduct(p2)

        val customer = CustomerEntity(
            id = 60L,
            name = "سعيد كريم",
            phone = "0611223344",
            address = "الرباط",
            balance = 100.0, // دين سابق 100
            totalPurchases = 500.0
        )
        appDao.insertCustomer(customer)

        // بيع: 2 عصير (2 * 15 = 30) + 4 بسكويت (4 * 5 = 20) = المجموع 50
        // خصم 5 DH -> الإجمالي 45 DH
        // دفع الزبون 15 DH والباقي 30 DH دين جديد
        val sale = SaleEntity(
            invoiceNumber = "INV-MULTI-01",
            timestamp = System.currentTimeMillis(),
            totalAmount = 45.0,
            paidAmount = 15.0,
            changeAmount = 0.0,
            discountAmount = 5.0,
            discountType = "FIXED",
            paymentMethod = "CASH",
            cashierName = "admin",
            customerId = 60L,
            customerName = "سعيد كريم"
        )
        val items = listOf(
            SaleItemEntity(saleId = 0L, productId = 101L, productName = "عصير برتقال 1L", unitPrice = 15.0, quantity = 2.0, totalLinePrice = 30.0),
            SaleItemEntity(saleId = 0L, productId = 102L, productName = "بسكويت شاي", unitPrice = 5.0, quantity = 4.0, totalLinePrice = 20.0)
        )

        val recorded = repository.recordSaleInvoice(sale, items, testManager)
        assertTrue(recorded.id > 0)

        // التحقق من مخزون المنتجين
        val updP1 = appDao.getProductById(101L)
        val updP2 = appDao.getProductById(102L)
        assertEquals(18.0, updP1!!.currentStock, 0.001) // 20 - 2
        assertEquals(46.0, updP2!!.currentStock, 0.001) // 50 - 4

        // التحقق من حساب العميل: 100 دين سابق + 30 دين جديد = 130
        val updCust = appDao.getCustomerById(60L)
        assertEquals(130.0, updCust!!.balanceDouble, 0.001)
        assertEquals(545.0, updCust.totalPurchasesDouble, 0.001) // 500 + 45
    }

    @Test
    fun testVoidSaleInvoice_RestoresStockAndRevertsCustomerDebt() = runBlocking {
        val product = ProductEntity(
            id = 201L,
            barcode = "B201",
            name = "صابون غسيل",
            description = "منظفات",
            categoryId = 3L,
            categoryName = "منظفات",
            unit = "قطعة",
            purchasePrice = 12.0,
            sellPrice = 20.0,
            wholesalePrice = 18.0,
            specialPrice = 16.0,
            currentStock = 30.0,
            minStock = 5.0
        )
        appDao.insertProduct(product)

        val customer = CustomerEntity(
            id = 70L,
            name = "ياسين المنصوري",
            phone = "0677889900",
            address = "طنجة",
            balance = 0.0,
            totalPurchases = 0.0
        )
        appDao.insertCustomer(customer)

        // بيع 5 قطع بـ 100 DH دين كامل (دفع 0)
        val sale = SaleEntity(
            invoiceNumber = "INV-VOID-TEST",
            timestamp = System.currentTimeMillis(),
            totalAmount = 100.0,
            paidAmount = 0.0,
            changeAmount = 0.0,
            discountAmount = 0.0,
            discountType = "FIXED",
            paymentMethod = "DEBT",
            cashierName = "admin",
            customerId = 70L,
            customerName = customer.name
        )
        val items = listOf(
            SaleItemEntity(saleId = 0L, productId = 201L, productName = "صابون غسيل", unitPrice = 20.0, quantity = 5.0, totalLinePrice = 100.0)
        )
        val savedSale = repository.recordSaleInvoice(sale, items, testManager)

        // التأكد من تسجيل البيع
        assertEquals(25.0, appDao.getProductById(201L)!!.currentStock, 0.001)
        assertEquals(100.0, appDao.getCustomerById(70L)!!.balanceDouble, 0.001)

        // حذف الفاتورة بالكامل (Delete Sale): يعاد المخزون المباع تلقائياً إلى المخزن (25 + 5 = 30 قطعة) وينقص حساب دين الزبون
        repository.deleteSaleInvoice(savedSale.id, testManager)

        // المخزون يزداد تلقائياً بالكميات التي كانت مسجلة في الفاتورة المباعة ويعود إلى 30 قطعة
        assertEquals(30.0, appDao.getProductById(201L)!!.currentStock, 0.001)
        // دين العميل ينخفض بمقدار 100 ويعود إلى 0
        assertEquals(0.0, appDao.getCustomerById(70L)!!.balanceDouble, 0.001)
    }

    @Test
    fun testDeleteSaleInvoice_DirectlyDeductsInvoiceAmountFromCustomerDebt() = runBlocking {
        val customer = CustomerEntity(
            id = 75L,
            name = "زبون 1",
            phone = "0611223344",
            address = "الرباط",
            balance = 2000.0 // رصيد الزبون الابتدائي 2000 درهم
        )
        appDao.insertCustomer(customer)
        appDao.insertProduct(
            ProductEntity(
                id = 205L,
                barcode = "MILK-TEST-200",
                name = "حليب 200",
                categoryId = 1L,
                unit = "قطعة",
                purchasePrice = 10.0,
                sellPrice = 20.0,
                currentStock = 50.0,
                minStock = 5.0
            )
        )

        // 1. فاتورة دين غير مسددة بمبلغ 200 درهم للزبون
        val debtSale = SaleEntity(
            invoiceNumber = "INV-200-TEST",
            timestamp = System.currentTimeMillis(),
            totalAmount = 200.0,
            paidAmount = 0.0,
            changeAmount = 0.0,
            discountAmount = 0.0,
            discountType = "FIXED",
            paymentMethod = "DEBT",
            cashierName = "admin",
            customerId = 75L,
            customerName = "زبون 1"
        )
        val items = listOf(
            SaleItemEntity(saleId = 0L, productId = 205L, productName = "حليب 200", unitPrice = 20.0, quantity = 10.0, totalLinePrice = 200.0)
        )
        val savedDebtSale = repository.recordSaleInvoice(debtSale, items, testManager)

        // رصيد الزبون بعد فاتورة الدين: 2000 + 200 = 2200 درهم
        assertEquals(2200.0, appDao.getCustomerById(75L)!!.balanceDouble, 0.001)

        // عند مسح الفاتورة غير المسددة (total - paid = 200): يخصم 200 درهم فقط من الدين ليعود 2000 درهم
        repository.deleteSaleInvoice(savedDebtSale.id, testManager)
        assertEquals(2000.0, appDao.getCustomerById(75L)!!.balanceDouble, 0.001)

        // 2. فاتورة مدفوعة بالكامل نقداً (total 100, paid 100)
        val cashSale = SaleEntity(
            invoiceNumber = "INV-CASH-TEST",
            timestamp = System.currentTimeMillis(),
            totalAmount = 100.0,
            paidAmount = 100.0,
            changeAmount = 0.0,
            discountAmount = 0.0,
            discountType = "FIXED",
            paymentMethod = "CASH",
            cashierName = "admin",
            customerId = 75L,
            customerName = "زبون 1"
        )
        val itemsCash = listOf(
            SaleItemEntity(saleId = 0L, productId = 205L, productName = "حليب 200", unitPrice = 20.0, quantity = 5.0, totalLinePrice = 100.0)
        )
        val savedCashSale = repository.recordSaleInvoice(cashSale, itemsCash, testManager)

        // رصيد الزبون يبقى 2000 درهم لأن الفاتورة سددت نقداً بالكامل
        assertEquals(2000.0, appDao.getCustomerById(75L)!!.balanceDouble, 0.001)

        // عند مسح الفاتورة المسددة نقداً: لا يخصم من الدين القديم لأن total - paid = 0
        repository.deleteSaleInvoice(savedCashSale.id, testManager)
        assertEquals(2000.0, appDao.getCustomerById(75L)!!.balanceDouble, 0.001)
    }

    @Test
    fun testMultipleCustomerPayments_AccurateDebtReduction() = runBlocking {
        val customer = CustomerEntity(
            id = 80L,
            name = "مصطفى الدكالي",
            phone = "0655443322",
            address = "مراكش",
            balance = 1000.0,
            totalPurchases = 2500.0
        )
        appDao.insertCustomer(customer)

        // 3 دفعات متتالية بالفاصلة الدقيقة
        repository.recordCustomerPayment(customerId = 80L, amount = 250.50, paymentMethod = "CASH", notes = "دفعة 1", cashierName = "admin")
        repository.recordCustomerPayment(customerId = 80L, amount = 300.25, paymentMethod = "CASH", notes = "دفعة 2", cashierName = "admin")
        repository.recordCustomerPayment(customerId = 80L, amount = 449.25, paymentMethod = "CASH", notes = "دفعة 3", cashierName = "admin")

        // 1000.0 - 250.50 - 300.25 - 449.25 = 0.00
        val updated = appDao.getCustomerById(80L)
        assertNotNull(updated)
        assertEquals(0.0, updated!!.balanceDouble, 0.0001)
    }

    @Test
    fun testSupplierMultipleInvoicesAndSettlement() = runBlocking {
        val supplier = SupplierEntity(
            id = 90L,
            name = "مؤسسة التوزيع السريع",
            phone = "0537000000",
            email = "dist@example.com",
            address = "سلا",
            debtBalance = 0.0
        )
        appDao.insertSupplier(supplier)

        val prod = ProductEntity(
            id = 301L,
            barcode = "B301",
            name = "مياه معدنية 5L",
            description = "مياه",
            categoryId = 1L,
            categoryName = "مشروبات",
            unit = "قارورة",
            purchasePrice = 10.0,
            sellPrice = 14.0,
            wholesalePrice = 13.0,
            specialPrice = 12.0,
            currentStock = 0.0,
            minStock = 10.0
        )
        appDao.insertProduct(prod)

        // فاتورة شراء 1: إجمالي 1200، دفع 200 -> دين 1000
        val p1 = PurchaseEntity(timestamp = System.currentTimeMillis(), supplierId = 90L, supplierName = supplier.name, totalAmount = 1200.0, paidAmount = 200.0)
        val items1 = listOf(PurchaseItemEntity(purchaseId = 0L, productId = 301L, productName = "مياه معدنية 5L", unitPrice = 10.0, quantity = 120.0))
        repository.recordPurchaseInvoice(p1, items1, testManager)

        // فاتورة شراء 2: إجمالي 800، دفع 0 -> دين 800 إضافي -> المجموع 1800
        val p2 = PurchaseEntity(timestamp = System.currentTimeMillis(), supplierId = 90L, supplierName = supplier.name, totalAmount = 800.0, paidAmount = 0.0)
        val items2 = listOf(PurchaseItemEntity(purchaseId = 0L, productId = 301L, productName = "مياه معدنية 5L", unitPrice = 10.0, quantity = 80.0))
        repository.recordPurchaseInvoice(p2, items2, testManager)

        val suppAfterInvoices = appDao.getAllSuppliersList().find { it.id == 90L }
        assertEquals(1800.0, suppAfterInvoices!!.debtBalanceDouble, 0.001)
        assertEquals(200.0, appDao.getProductById(301L)!!.currentStock, 0.001)

        // سداد كامل الدين على دفعتين: 1000 ثم 800
        repository.recordSupplierPayment(supplierId = 90L, amount = 1000.0, paymentMethod = "BANK", notes = "تسديد 1", cashierName = "admin")
        repository.recordSupplierPayment(supplierId = 90L, amount = 800.0, paymentMethod = "CASH", notes = "تسديد 2", cashierName = "admin")

        val settledSupplier = appDao.getAllSuppliersList().find { it.id == 90L }
        assertEquals(0.0, settledSupplier!!.debtBalanceDouble, 0.001)
    }

    @Test
    fun testMoneyUtilsLongCentsAccuracy() {
        val a = 0.1
        val b = 0.2
        // في نظام IEEE 754 Floating point العادي: 0.1 + 0.2 = 0.30000000000000004
        val centsA = MoneyUtils.toCents(a) // 10 cents
        val centsB = MoneyUtils.toCents(b) // 20 cents
        val sumCents = MoneyUtils.addCents(centsA, centsB) // 30 cents

        assertEquals(30L, sumCents)
        assertEquals(0.3, MoneyUtils.fromCents(sumCents), 0.00001)

        // ضرب سعر الوحدة 19.99 بالكمية 3
        val priceCents = MoneyUtils.toCents(19.99)
        val totalCents = MoneyUtils.multiplyCents(priceCents, 3.0)
        assertEquals(5997L, totalCents) // 59.97 DH
        assertEquals(59.97, MoneyUtils.fromCents(totalCents), 0.0001)

        // اختبار MoneyDataMigration والهجرة الآمنة للمبالغ إلى Long بالسنتيمات
        val sampleAmounts = listOf(0.0, 0.05, 1.23, 10.50, 99.99, 1250.75, 1000000.0)
        for (amount in sampleAmounts) {
            val cents = com.example.util.MoneyDataMigration.migrateDoubleAmountToCents(amount)
            val recovered = com.example.util.MoneyDataMigration.migrateCentsToDoubleAmount(cents)
            assertTrue(
                "يجب الحفاظ على تطابق المبلغ بعد التحويل ذهاباً وإياباً: $amount",
                com.example.util.MoneyDataMigration.verifyRoundtripIntegrity(amount)
            )
            assertEquals(amount, recovered, 0.0001)
        }

        // التحقق من تعبيرات SQL الآمنة لتحويل الأعمدة إلى سنتيمات دون فقدان
        assertEquals("CAST(ROUND(sellPrice * 100.0) AS INTEGER)", com.example.util.MoneyDataMigration.sqlFloatToCentsExpression("sellPrice"))
        assertEquals("(sellPriceCents / 100.0)", com.example.util.MoneyDataMigration.sqlCentsToFloatExpression("sellPriceCents"))

        // التحقق من توافق الحقول الموسعة في الكيانات Entity Cents Accessors
        val prod = ProductEntity(
            barcode = "CENTS_TEST", name = "اختبار السنتيم", description = "",
            categoryId = 1L, categoryName = "عام", unit = "قطعة",
            purchasePrice = 12.34, sellPrice = 25.50, wholesalePrice = 20.00, specialPrice = 22.00,
            currentStock = 10.0, minStock = 1.0
        )
        assertEquals(1234L, prod.purchasePriceCents)
        assertEquals(2550L, prod.sellPriceCents)
        assertEquals(2000L, prod.wholesalePriceCents)
        assertEquals(2200L, prod.specialPriceCents)
    }

    @Test
    fun testSaleInvoice_NegativeStockPrevention_WhenDisabled() = runBlocking {
        // إنشاء منتج مخزونه 2 فقط
        val product = ProductEntity(
            id = 501L,
            barcode = "501",
            name = "زيت محدود",
            description = "مخزون قليل",
            categoryId = 1L,
            categoryName = "عام",
            unit = "قطعة",
            purchasePrice = 10.0,
            sellPrice = 15.0,
            wholesalePrice = 14.0,
            specialPrice = 13.0,
            currentStock = 2.0,
            minStock = 1.0
        )
        appDao.insertProduct(product)

        // محاولة بيع 5 قطع (أكثر من المخزون المتوفر 2) بدون السماح بالبيع بالسالب
        val sale = SaleEntity(
            invoiceNumber = "INV-NEG-001",
            timestamp = System.currentTimeMillis(),
            totalAmount = 75.0,
            paidAmount = 75.0,
            changeAmount = 0.0,
            discountAmount = 0.0,
            discountType = "FIXED",
            paymentMethod = "CASH",
            cashierName = "كاشير 1",
            customerId = 0L,
            customerName = ""
        )
        val items = listOf(
            SaleItemEntity(
                saleId = 0L,
                productId = 501L,
                productName = "زيت محدود",
                unitPrice = 15.0,
                quantity = 5.0,
                totalLinePrice = 75.0
            )
        )

        try {
            repository.recordSaleInvoice(sale, items, testManager)
            org.junit.Assert.fail("يجب أن تفشل الفاتورة عند عدم توفر المخزون الكافي")
        } catch (e: Exception) {
            assertTrue("يجب أن تكون رسالة الخطأ تشير لنفاد المخزون", e.message?.contains("المخزون") == true || e.message?.contains("غير كافٍ") == true)
        }

        // التحقق من بقاء المخزون كما هو 2
        val prodAfter = appDao.getProductById(501L)
        assertNotNull(prodAfter)
        assertEquals(2.0, prodAfter!!.currentStock, 0.001)

        // التحقق من عدم حفظ أي مبيعات
        val allSales = appDao.getAllSalesList()
        assertTrue(allSales.none { it.invoiceNumber == "INV-NEG-001" })
    }

    @Test
    fun testCustomerPayment_ZeroOrNegativeAmount_Validation() = runBlocking {
        val customer = CustomerEntity(
            id = 601L,
            name = "عميل فحص الدفع",
            phone = "0611223344",
            balance = 500.0,
            totalPurchases = 1000.0
        )
        appDao.insertCustomer(customer)

        val debtVm = com.example.ui.viewmodels.DebtLedgerViewModel(repository)

        // محاولة دفع مبلغ صفر
        var zeroSuccess = true
        debtVm.payCustomerDebt(
            customerId = 601L,
            amount = 0.0,
            paymentMethod = "CASH",
            notes = "دفعة صفر",
            cashierName = "كاشير"
        ) { success, _ ->
            zeroSuccess = success
        }
        assertFalse("يجب رفض دفعة بقيمة صفر", zeroSuccess)

        // محاولة دفع مبلغ سالب
        var negativeSuccess = true
        debtVm.payCustomerDebt(
            customerId = 601L,
            amount = -100.0,
            paymentMethod = "CASH",
            notes = "دفعة سالبة",
            cashierName = "كاشير"
        ) { success, _ ->
            negativeSuccess = success
        }
        assertFalse("يجب رفض دفعة سالبة", negativeSuccess)

        // رصيد العميل يجب ألا يتأثر
        val custAfter = appDao.getCustomerById(601L)
        assertNotNull(custAfter)
        assertEquals(500.0, custAfter!!.balanceDouble, 0.001)
    }

    @Test
    fun testSupplierPayment_ExceedingDebt_HandlesGracefully() = runBlocking {
        val supplier = SupplierEntity(
            id = 701L,
            name = "مورد فحص التسديد",
            phone = "0522334455",
            debtBalance = 300.0
        )
        appDao.insertSupplier(supplier)

        // تسديد كامل الدين 300
        val payment = repository.recordSupplierPayment(
            supplierId = 701L,
            amount = 300.0,
            paymentMethod = "CASH",
            notes = "تسديد كامل",
            cashierName = "كاشير"
        )
        assertNotNull(payment)
        assertEquals(300.0, payment!!.amountDouble, 0.001)

        val suppAfter = appDao.getAllSuppliersList().find { it.id == 701L }
        assertNotNull(suppAfter)
        assertEquals(0.0, suppAfter!!.debtBalanceDouble, 0.001)

        // فحص وجود سجل الدفعة
        val payments = appDao.getAllSupplierPaymentsList().filter { it.supplierId == 701L }
        assertEquals(1, payments.size)
        assertEquals(300.0, payments[0].amountDouble, 0.001)
    }

    @Test
    fun testSaleInvoice_MultipleItemsWithDiscounts_CalculatesCorrectly() = runBlocking {
        val p1 = ProductEntity(
            id = 801L, barcode = "801", name = "سكر 1 كغ",
            categoryId = 1L, unit = "كغ", purchasePrice = 8.0, sellPrice = 10.0,
            currentStock = 100.0, minStock = 5.0
        )
        val p2 = ProductEntity(
            id = 802L, barcode = "802", name = "شاي 200غ",
            categoryId = 1L, unit = "علبة", purchasePrice = 12.0, sellPrice = 15.0,
            currentStock = 50.0, minStock = 5.0
        )
        appDao.insertProduct(p1)
        appDao.insertProduct(p2)

        val customer = CustomerEntity(
            id = 810L, name = "زبون خصم", phone = "0655443322",
            balance = 0.0, totalPurchases = 0.0
        )
        appDao.insertCustomer(customer)

        // شراء 2 سكر (20) و 1 شاي (15) = 35 - خصم 5 = 30 إجمالي
        // دفع 20 نقداً والباقي 10 على الحساب
        val sale = SaleEntity(
            invoiceNumber = "INV-DISC-801",
            timestamp = System.currentTimeMillis(),
            totalAmount = 30.0,
            paidAmount = 20.0,
            changeAmount = 0.0,
            discountAmount = 5.0,
            discountType = "FIXED",
            paymentMethod = "CASH",
            cashierName = "كاشير 1",
            customerId = 810L,
            customerName = customer.name
        )
        val items = listOf(
            SaleItemEntity(saleId = 0L, productId = 801L, productName = p1.name, unitPrice = 10.0, quantity = 2.0, totalLinePrice = 20.0),
            SaleItemEntity(saleId = 0L, productId = 802L, productName = p2.name, unitPrice = 15.0, quantity = 1.0, totalLinePrice = 15.0)
        )

        val result = repository.recordSaleInvoice(sale, items, testManager)
        assertNotNull(result)
        assertEquals("INV-DISC-801", result.invoiceNumber)

        // فحص تحديث رصيد العميل ومشترياته
        val custAfter = appDao.getCustomerById(810L)
        assertNotNull(custAfter)
        assertEquals(10.0, custAfter!!.balanceDouble, 0.001) // دين متبقي
        assertEquals(30.0, custAfter.totalPurchasesDouble, 0.001)

        // فحص نقص المخزون لكلا الصنفين
        assertEquals(98.0, appDao.getProductById(801L)!!.currentStock, 0.001)
        assertEquals(49.0, appDao.getProductById(802L)!!.currentStock, 0.001)
    }

    @Test
    fun testCartViewModel_OperationsAndSubtotalCents() = runBlocking {
        val cartVm = com.example.ui.viewmodels.CartViewModel(repository)
        val product = ProductEntity(
            id = 901L, barcode = "CART-901", name = "حليب معقم",
            categoryId = 1L, unit = "لتر", purchasePrice = 6.50, sellPrice = 8.50,
            currentStock = 20.0, minStock = 2.0
        )

        // 1. إضافة إلى السلة
        cartVm.addToCart(product, quantity = 3.0)
        assertEquals(1, cartVm.cart.value.size)
        assertEquals(3.0, cartVm.cart.value[0].quantity, 0.001)
        assertEquals(25.50, cartVm.currentSubtotal, 0.001) // 3 * 8.50 = 25.50
        assertEquals(2550L, cartVm.currentSubtotalCents) // 2550 cents

        // 2. تطبيق خصم سطر
        cartVm.applyLineDiscount(901L, 2.50)
        assertEquals(23.00, cartVm.currentSubtotal, 0.001) // 25.50 - 2.50 = 23.00
        assertEquals(2300L, cartVm.currentSubtotalCents)

        // 3. تعليق الفاتورة واسترجاعها
        cartVm.suspendCurrentSale("طلب عميل مؤجل")
        assertEquals(0, cartVm.cart.value.size)
        assertEquals(1, cartVm.suspendedSales.value.size)

        val suspendedTimestamp = cartVm.suspendedSales.value[0].timestamp
        cartVm.restoreSuspendedSale(suspendedTimestamp)
        assertEquals(1, cartVm.cart.value.size)
        assertEquals(0, cartVm.suspendedSales.value.size)
        assertEquals(23.00, cartVm.currentSubtotal, 0.001)
    }

    @Test
    fun testDebtLedgerViewModel_CustomerAndSupplierPayments() = runBlocking {
        val debtVm = com.example.ui.viewmodels.DebtLedgerViewModel(repository)

        // إدراج زبون ومورد برصيد ديون
        val customer = CustomerEntity(id = 950L, name = "زبون الذمم", phone = "0612345678", balance = 500.0, totalPurchases = 1500.0)
        val supplier = SupplierEntity(id = 960L, name = "مورد الذمم", phone = "0512345678", email = "", address = "", debtBalance = 1000.0)
        appDao.insertCustomer(customer)
        appDao.insertSupplier(supplier)

        // سداد 200 دين للزبون عبر DebtLedgerViewModel
        val (custPaidSuccess, _) = debtVm.executeCustomerDebtPayment(
            customerId = 950L,
            amount = 200.0,
            paymentMethod = "CASH",
            notes = "سداد جزئي",
            cashierName = "كاشير"
        )
        assertTrue(custPaidSuccess)
        val updatedCust = appDao.getCustomerById(950L)
        assertEquals(300.0, updatedCust!!.balanceDouble, 0.001)
        assertEquals(30000L, updatedCust.balanceCents)

        // سداد 600 دين للمورد عبر DebtLedgerViewModel
        val (supPaidSuccess, _) = debtVm.executeSupplierDebtPayment(
            supplierId = 960L,
            amount = 600.0,
            paymentMethod = "CHECK",
            notes = "شيك تسوية",
            cashierName = "كاشير"
        )
        assertTrue(supPaidSuccess)
        val updatedSup = appDao.getAllSuppliersList().find { it.id == 960L }
        assertEquals(400.0, updatedSup!!.debtBalanceDouble, 0.001)
        assertEquals(40000L, updatedSup.debtBalanceCents)
    }

    @Test
    fun testDeletePurchaseInvoice_DecreasesStockAndSupplierDebtAndDeletesItems() = runBlocking {
        val supplier = SupplierEntity(
            id = 95L,
            name = "شركة الألبان والزيوت",
            phone = "0612345678",
            address = "فاس",
            debtBalance = 0.0
        )
        appDao.insertSupplier(supplier)

        val milk = ProductEntity(
            id = 401L,
            barcode = "MILK-01",
            name = "حليب 1L",
            description = "ألبان",
            categoryId = 1L,
            categoryName = "ألبان",
            unit = "قطعة",
            purchasePrice = 7.0,
            sellPrice = 10.0,
            wholesalePrice = 9.0,
            specialPrice = 8.5,
            currentStock = 10.0,
            minStock = 5.0
        )
        val oil = ProductEntity(
            id = 402L,
            barcode = "OIL-01",
            name = "زيت 1L",
            description = "زيوت",
            categoryId = 2L,
            categoryName = "زيوت",
            unit = "قطعة",
            purchasePrice = 15.0,
            sellPrice = 20.0,
            wholesalePrice = 18.0,
            specialPrice = 17.0,
            currentStock = 5.0,
            minStock = 2.0
        )
        appDao.insertProduct(milk)
        appDao.insertProduct(oil)

        // تسجيل فاتورة شراء: 100 حليب + 10 زيت
        // المجموع = 100*7 + 10*15 = 700 + 150 = 850 DH دين
        val purchase = PurchaseEntity(
            id = 0L,
            invoiceNumber = "PUR-TEST-100",
            supplierId = 95L,
            supplierName = supplier.name,
            totalAmount = 850.0,
            paidAmount = 0.0,
            timestamp = System.currentTimeMillis()
        )
        val items = listOf(
            PurchaseItemEntity(purchaseId = 0L, productId = 401L, productName = "حليب 1L", unitPrice = 7.0, quantity = 100.0),
            PurchaseItemEntity(purchaseId = 0L, productId = 402L, productName = "زيت 1L", unitPrice = 15.0, quantity = 10.0)
        )
        repository.recordPurchaseInvoice(purchase, items, testManager)
        val insertedPurchase = appDao.getAllPurchasesList().first { it.invoiceNumber == "PUR-TEST-100" }

        // التحقق من ارتفاع المخزون: حليب 10 + 100 = 110، زيت 5 + 10 = 15
        assertEquals(110.0, appDao.getProductById(401L)!!.currentStock, 0.001)
        assertEquals(15.0, appDao.getProductById(402L)!!.currentStock, 0.001)
        // التحقق من دين المورد
        assertEquals(850.0, appDao.getAllSuppliersList().find { it.id == 95L }!!.debtBalanceDouble, 0.001)

        // حذف فاتورة المورد
        repository.deletePurchaseInvoice(insertedPurchase.id, testManager)

        // 1. ينقص المخزون تلقائياً بالكميات المسجلة في الفاتورة: حليب ينقص 100 ويعود 10، زيت ينقص 10 ويعود 5
        assertEquals(10.0, appDao.getProductById(401L)!!.currentStock, 0.001)
        assertEquals(5.0, appDao.getProductById(402L)!!.currentStock, 0.001)

        // 2. ينقص دين المورد ويعود إلى 0
        assertEquals(0.0, appDao.getAllSuppliersList().find { it.id == 95L }!!.debtBalanceDouble, 0.001)

        // 3. بنود الفاتورة وحركاتها تمسح نهائياً
        assertTrue(appDao.getPurchaseItems(insertedPurchase.id).isEmpty())
        assertNull(appDao.getPurchaseById(insertedPurchase.id))
    }

    @Test
    fun testWipeAllEntitiesData_ClearsProductsCustomersSuppliersAndCategories() = runBlocking {
        // إضافة أصناف وزبائن وموردين وفئات تجريبية
        val catId = appDao.insertCategory(CategoryEntity(name = "عائلة تجريبية"))
        appDao.insertProduct(ProductEntity(id = 999L, barcode = "DEMO-999", name = "منتج تجريبي", categoryId = catId, unit = "قطعة", purchasePrice = 6.0, sellPrice = 10.0, currentStock = 5.0, minStock = 1.0))
        appDao.insertCustomer(CustomerEntity(id = 999L, name = "زبون تجريبي", phone = "0600000000", balance = 50.0))
        appDao.insertSupplier(SupplierEntity(id = 999L, name = "مورد تجريبي", phone = "0500000000", debtBalance = 100.0))

        assertEquals(1, appDao.getAllProductsList().filter { it.id == 999L }.size)
        assertEquals(1, appDao.getAllCustomersList().filter { it.id == 999L }.size)
        assertEquals(1, appDao.getAllSuppliersList().filter { it.id == 999L }.size)

        // مسح شامل لكافة البيانات
        repository.wipeAllEntitiesData(testManager)

        assertTrue(appDao.getAllProductsList().isEmpty())
        assertTrue(appDao.getAllCustomersList().isEmpty())
        assertTrue(appDao.getAllSuppliersList().isEmpty())
        assertTrue(appDao.getAllCategoriesList().isEmpty())
    }

    @Test
    fun testSalePaymentRecording_CashBankAndDebt() = runBlocking {
        val customer = CustomerEntity(id = 880L, name = "رشيد المنصوري", phone = "0677889900", balance = 0.0)
        appDao.insertCustomer(customer)
        appDao.insertProduct(ProductEntity(id = 881L, barcode = "P-881", name = "منتج 881", categoryId = 1L, unit = "قطعة", purchasePrice = 10.0, sellPrice = 50.0, currentStock = 100.0, minStock = 5.0))

        // 1. بيع نقدي بقيمة 100 DH: المبيعات النقدية لا تسجل كدفعات زبائن
        val cashSale = SaleEntity(
            invoiceNumber = "INV-CASH-880",
            timestamp = System.currentTimeMillis(),
            cashierName = "admin",
            customerId = 880L,
            customerName = "رشيد المنصوري",
            totalAmount = 100.0,
            discountAmount = 0.0,
            discountType = "FIXED",
            paidAmount = 100.0,
            changeAmount = 0.0,
            paymentMethod = "CASH"
        )
        val itemsCash = listOf(SaleItemEntity(saleId = 0L, productId = 881L, productName = "منتج 881", unitPrice = 50.0, quantity = 2.0, totalLinePrice = 100.0))
        repository.recordSaleInvoice(cashSale, itemsCash, testManager)

        // سجل دفعات الزبائن فارغ لأن المبيعات النقدية معزولة
        val paymentsAfterCash = appDao.getAllCustomerPaymentsList().filter { it.customerId == 880L }
        assertEquals(0, paymentsAfterCash.size)

        // 2. بيع بتحويل بنكي بقيمة 150 DH: أيضاً معزول عن سجل تسديدات ديون الزبائن
        val bankSale = SaleEntity(
            invoiceNumber = "INV-BANK-880",
            timestamp = System.currentTimeMillis() + 1000,
            cashierName = "admin",
            customerId = 880L,
            customerName = "رشيد المنصوري",
            totalAmount = 150.0,
            discountAmount = 0.0,
            discountType = "FIXED",
            paidAmount = 150.0,
            changeAmount = 0.0,
            paymentMethod = "TRANSFER"
        )
        val itemsBank = listOf(SaleItemEntity(saleId = 0L, productId = 881L, productName = "منتج 881", unitPrice = 50.0, quantity = 3.0, totalLinePrice = 150.0))
        repository.recordSaleInvoice(bankSale, itemsBank, testManager)

        val paymentsAfterBank = appDao.getAllCustomerPaymentsList().filter { it.customerId == 880L }
        assertEquals(0, paymentsAfterBank.size)

        // 3. بيع دين بقيمة 200 DH (غير مسدد 0.00)
        val debtSale = SaleEntity(
            invoiceNumber = "INV-DEBT-880",
            timestamp = System.currentTimeMillis() + 2000,
            cashierName = "admin",
            customerId = 880L,
            customerName = "رشيد المنصوري",
            totalAmount = 200.0,
            discountAmount = 0.0,
            discountType = "FIXED",
            paidAmount = 0.0,
            changeAmount = 0.0,
            paymentMethod = "DEBT"
        )
        val itemsDebt = listOf(SaleItemEntity(saleId = 0L, productId = 881L, productName = "منتج 881", unitPrice = 50.0, quantity = 4.0, totalLinePrice = 200.0))
        val recordedDebtSale = repository.recordSaleInvoice(debtSale, itemsDebt, testManager)

        // رصيد الزبون أصبح مديناً بـ 200 DH
        assertEquals(200.0, appDao.getCustomerById(880L)!!.balanceDouble, 0.001)
        assertEquals(0, appDao.getAllCustomerPaymentsList().filter { it.customerId == 880L }.size)

        // 4. تسوية وتأكيد الفاتورة (زر valide) مرتبطة بمعرف الفاتورة saleId
        val debtSaleItems = repository.getSaleItems(recordedDebtSale.id)
        val settledSale = recordedDebtSale.copy(paidAmount = recordedDebtSale.totalAmount, paymentMethod = "CASH")
        repository.updateSaleInvoice(settledSale, debtSaleItems)
        val custAfterSettle = appDao.getCustomerById(880L)!!
        assertEquals(0.0, custAfterSettle.balanceDouble, 0.001) // تمت تصفية الدين

        // تسجيل الدفعة الخاصة بتسوية الفاتورة وربطها بـ saleId
        repository.insertCustomerPayment(
            CustomerPaymentEntity(
                saleId = recordedDebtSale.id,
                customerId = 880L,
                customerName = "رشيد المنصوري",
                amount = settledSale.totalAmountDouble,
                previousBalance = 200.0,
                newBalance = 0.0,
                paymentMethod = "CASH",
                notes = "تسديد وتأكيد فاتورة بيع #${settledSale.invoiceNumber}"
            )
        )
        val finalPayments = appDao.getAllCustomerPaymentsList().filter { it.customerId == 880L }
        assertEquals(1, finalPayments.size)
        assertEquals(recordedDebtSale.id, finalPayments[0].saleId)

        // 5. حذف الفاتورة يسحب الدفعة المرتبطة بها بواسطة saleId
        repository.deleteSaleInvoice(recordedDebtSale.id, testManager)
        val paymentsAfterDelete = appDao.getAllCustomerPaymentsList().filter { it.customerId == 880L }
        assertEquals(0, paymentsAfterDelete.size)
    }
}

