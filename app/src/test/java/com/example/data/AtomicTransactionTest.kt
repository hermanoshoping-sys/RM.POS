package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبار العمليات الذرية (Atomic Transactions Test):
 * يثبت أنه عند حدوث أي خطأ أو استثناء أثناء تسجيل فاتورة الشراء، يتم التراجع الكامل (Rollback)
 * ولا يتغير المخزون ولا دين المورد ولا يُحفظ شيء في قاعدة البيانات.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AtomicTransactionTest {

    private lateinit var database: AppDatabase
    private lateinit var appDao: AppDao
    private lateinit var repository: PosRepository

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
    fun testPurchaseInvoiceRollbackOnFailure() = runBlocking {
        // 1. تجهيز منتج ومورد بحالة ابتدائية معلومة
        val initialStock = 10.0
        val initialDebt = 0.0

        val product = ProductEntity(
            id = 100L,
            barcode = "6190001",
            name = "زيت المائدة 1 لتر",
            description = "زيت نباتي",
            categoryId = 1L,
            categoryName = "مواد غذائية",
            unit = "قطعة",
            purchasePrice = 120.0,
            sellPrice = 140.0,
            wholesalePrice = 135.0,
            specialPrice = 130.0,
            currentStock = initialStock,
            minStock = 5.0
        )
        appDao.insertProduct(product)

        val supplier = SupplierEntity(
            id = 200L,
            name = "شركة التوزيع الكبرى",
            phone = "0555000000",
            email = "dist@example.com",
            address = "المنطقة الصناعية",
            debtBalance = initialDebt
        )
        appDao.insertSupplier(supplier)

        // التحقق من القيم الابتدائية
        val beforeProduct = appDao.getProductById(100L)
        assertNotNull(beforeProduct)
        assertEquals(initialStock, beforeProduct!!.currentStock, 0.001)

        val beforeSupplier = appDao.getAllSuppliersList().find { it.id == 200L }
        assertNotNull(beforeSupplier)
        assertEquals(initialDebt, beforeSupplier!!.debtBalanceDouble, 0.001)

        // 2. محاولة تنفيذ عملية شراء تفشل في المنتصف داخل Transaction
        var exceptionThrown = false
        try {
            repository.runInTransaction {
                // خطوة أ: حفظ الفاتورة
                val purchase = PurchaseEntity(
                    id = 0L,
                    timestamp = System.currentTimeMillis(),
                    supplierId = 200L,
                    supplierName = supplier.name,
                    totalAmount = 1200.0,
                    paidAmount = 0.0,
                    isVoided = false
                )
                val purchaseId = appDao.insertPurchase(purchase)

                // خطوة ب: إضافة بند الفاتورة وتعديل المخزون
                val item = PurchaseItemEntity(
                    id = 0L,
                    purchaseId = purchaseId,
                    productId = 100L,
                    productName = product.name,
                    quantity = 10.0,
                    unitPrice = 120.0
                )
                appDao.insertPurchaseItem(item)
                appDao.adjustProductStock(100L, 10.0)

                // خطوة ج: تعديل دين المورد
                appDao.adjustSupplierDebt(200L, 1200.0)

                // خطوة د: محاكاة خطأ فادح غير متوقع (انقطاع/استثناء) قبل اكتمال العملية
                throw IllegalStateException("خطأ محاكاة فادح في منتصف العملية المالية!")
            }
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }

        assertTrue("يجب أن يتم رمي الاستثناء والتقاطه", exceptionThrown)

        // 3. التحقق القاطع من التراجع الكامل (Atomic Rollback):
        // المخزون لم يتغير
        val afterProduct = appDao.getProductById(100L)
        assertNotNull(afterProduct)
        assertEquals("يجب ألا يتغير المخزون إطلاقاً بعد فشل العملية", initialStock, afterProduct!!.currentStock, 0.001)

        // دين المورد لم يتغير
        val afterSupplier = appDao.getAllSuppliersList().find { it.id == 200L }
        assertNotNull(afterSupplier)
        assertEquals("يجب ألا يتغير دين المورد إطلاقاً بعد فشل العملية", initialDebt, afterSupplier!!.debtBalanceDouble, 0.001)

        // الفاتورة لم تُحفظ
        val allPurchases = appDao.getAllPurchasesList()
        assertTrue("يجب ألا تُحفظ الفاتورة في قاعدة البيانات عند فشل العملية", allPurchases.isEmpty())

        // بنود الفاتورة لم تُحفظ
        val allItems = appDao.getPurchaseItems(1L)
        assertTrue("يجب ألا يُحفظ أي بند شراء", allItems.isEmpty())
    }
}
