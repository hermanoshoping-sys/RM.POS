package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LargeTablePerformanceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var appDao: AppDao
    private lateinit var repository: PosRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
    fun sqlProductPagingAndFiltering_scalesAcross3500Products() = runBlocking {
        val products = (1..3500).map { i ->
            val catId = ((i - 1) % 5) + 1L
            ProductEntity(
                barcode = "61900${i.toString().padStart(5, '0')}",
                name = if (i % 50 == 0) "Premium Coffee $i" else "Product Item ${i.toString().padStart(4, '0')}",
                categoryId = catId,
                categoryName = "Category $catId",
                unit = "pcs",
                purchasePrice = 1000L + (i * 10L),
                sellPrice = 1500L + (i * 10L),
                currentStock = 50.0,
                minStock = 5.0
            )
        }
        appDao.insertProductsBatch(products)

        assertEquals(3500, repository.getProductsTotalCount().first())

        lateinit var page1: List<ProductEntity>
        lateinit var page2: List<ProductEntity>
        lateinit var coffeeMatches: List<ProductEntity>
        var coffeeCount = 0

        val elapsedMs = measureTimeMillis {
            page1 = repository.getProductsFilteredPaged("", null, limit = 60, offset = 0).first()
            page2 = repository.getProductsFilteredPaged("", null, limit = 60, offset = 60).first()
            coffeeCount = repository.getProductsFilteredCount("Coffee", null).first()
            coffeeMatches = repository.getProductsFilteredPaged("Coffee", null, limit = 100, offset = 0).first()
        }

        assertEquals(60, page1.size)
        assertEquals(60, page2.size)
        assertTrue((page1.map { it.id } intersect page2.map { it.id }.toSet()).isEmpty())
        assertEquals(70, coffeeCount) // 3500 / 50
        assertEquals(70, coffeeMatches.size)
        assertTrue(coffeeMatches.all { it.name.contains("Coffee") })

        val cat3Count = repository.getProductsFilteredCount("", categoryId = 3L).first()
        assertEquals(700, cat3Count)
        assertTrue("Expected paged SQL queries under 1000ms, was ${elapsedMs}ms", elapsedMs < 1000L)
    }

    @Test
    fun sqlSalesAndPurchasesFilteringAndSummaries_exactAcrossThousandsOfInvoices() = runBlocking {
        val sales = (1..2500).map { i ->
            val isDebt = i % 4 == 0
            val total = 10_000L + (i % 10) * 500L
            val paid = if (isDebt) total - 2_500L else total
            val seller = if (i % 3 == 0) "cashier_b" else "cashier_a"
            SaleEntity(
                invoiceNumber = "FAC-${i.toString().padStart(5, '0')}",
                timestamp = 1_700_000_000_000L + i * 1000L,
                cashierName = seller,
                customerId = 1L,
                customerName = if (i % 100 == 0) "VIP Client $i" else "Customer $i",
                totalAmount = total,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = paid,
                changeAmount = 0L,
                paymentMethod = if (isDebt) "DEBT" else "CASH"
            )
        }
        appDao.insertSalesBatch(sales)

        val expectedAllTotal = sales.sumOf { it.totalAmount }
        val expectedAllPaid = sales.sumOf { it.paidAmount }
        val expectedAllDebt = sales.sumOf { it.remainingDueCents }

        lateinit var allSummary: SalesLedgerSqlSummary
        lateinit var debtSummary: SalesLedgerSqlSummary
        lateinit var vipPage: List<SaleEntity>
        lateinit var sellerCounts: List<SellerSalesCountRecord>

        val salesElapsedMs = measureTimeMillis {
            allSummary = repository.getSalesFilteredSummary("", "ALL", "ALL").first()
            debtSummary = repository.getSalesFilteredSummary("", "DEBT", "ALL").first()
            vipPage = repository.getSalesFilteredPaged("VIP Client", "ALL", "ALL", limit = 80, offset = 0).first()
            sellerCounts = repository.getSalesCountBySeller().first()
        }

        assertEquals(2500, allSummary.count)
        assertEquals(expectedAllTotal, allSummary.totalCents)
        assertEquals(expectedAllPaid, allSummary.paidCents)
        assertEquals(expectedAllDebt, allSummary.debtCents)

        assertEquals(625, debtSummary.count) // 2500 / 4
        assertEquals(625L * 2_500L, debtSummary.debtCents)
        assertEquals(25, vipPage.size)
        // Ordered newest first
        assertTrue(vipPage.first().timestamp > vipPage.last().timestamp)
        assertEquals(2500, sellerCounts.sumOf { it.invoiceCount })
        assertTrue("Expected sales SQL queries under 1000ms, was ${salesElapsedMs}ms", salesElapsedMs < 1000L)

        // Purchases: 2,000 rows
        val purchases = (1..2000).map { i ->
            val isDebt = i % 5 == 0
            val total = 20_000L
            val paid = if (isDebt) 5_000L else total
            PurchaseEntity(
                timestamp = 1_700_100_000_000L + i * 1000L,
                supplierId = 1L,
                supplierName = if (i % 40 == 0) "Mega Distributor $i" else "Supplier $i",
                totalAmount = total,
                paidAmount = paid,
                invoiceNumber = "SUP-${i.toString().padStart(5, '0')}"
            )
        }
        appDao.insertPurchasesBatch(purchases)

        lateinit var purAllSummary: PurchaseLedgerSqlSummary
        lateinit var purDebtPage: List<PurchaseEntity>
        val purElapsedMs = measureTimeMillis {
            purAllSummary = repository.getPurchasesFilteredSummary("", "ALL").first()
            purDebtPage = repository.getPurchasesFilteredPaged("Mega Distributor", "DEBT", limit = 50, offset = 0).first()
        }

        assertEquals(2000, purAllSummary.count)
        assertEquals(2000L * 20_000L, purAllSummary.totalCents)
        assertEquals(400L * 15_000L, purAllSummary.debtCents)
        // Multiple of 40 is also multiple of 5 (DEBT), so all 50 Mega Distributors are DEBT
        assertEquals(50, purDebtPage.size)
        assertTrue("Expected purchase SQL queries under 1000ms, was ${purElapsedMs}ms", purElapsedMs < 1000L)
    }

    @Test
    fun sqlCustomersAndPaymentsPaging_returnsCorrectSlices() = runBlocking {
        val customers = (1..300).map { i ->
            CustomerEntity(
                name = if (i % 30 == 0) "Wholesale Buyer $i" else "Client $i",
                phone = "0600${i.toString().padStart(6, '0')}",
                balance = i * 100L,
                customerNumber = i.toLong(),
                barcode = "CUST-${i.toString().padStart(4, '0')}"
            )
        }
        appDao.insertCustomersBatch(customers)

        assertEquals(300, repository.getCustomersFilteredCount("").first())
        assertEquals(10, repository.getCustomersFilteredCount("Wholesale Buyer").first())
        val first60 = repository.getCustomersFilteredPaged("", limit = 60, offset = 0).first()
        assertEquals(60, first60.size)

        val custPayments = (1..120).map { i ->
            CustomerPaymentEntity(
                customerId = 1L,
                customerName = "Client 1",
                amount = 1000L,
                previousBalance = 5000L,
                newBalance = 4000L,
                timestamp = i * 1000L,
                cashierName = "admin"
            )
        }
        appDao.insertCustomerPaymentsBatch(custPayments)
        val first80Payments = repository.getCustomerPaymentsPaged(limit = 80, offset = 0).first()
        assertEquals(80, first80Payments.size)
        assertTrue(first80Payments.first().timestamp > first80Payments.last().timestamp)
    }
}
