package com.example.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDao
import com.example.data.AppDatabase
import com.example.data.CustomerEntity
import com.example.data.PosRepository
import com.example.data.ProductEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReturnRefundEnforcementTest {

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
    fun testLineDiscountReturnRefundsNetPaidAndRejectsUndiscountedOverRefund() = runBlocking {
        // Exact reported scenario: 2 units x 10.00 MAD = 20.00 MAD, line discount 3.00 MAD -> paid 17.00 MAD (8.50 MAD/unit)
        val customerId = appDao.insertCustomer(
            CustomerEntity(name = "زبون تجريبي", phone = "0600000001", balance = 1_700L, totalPurchases = 1_700L)
        )
        val productId = appDao.insertProduct(
            ProductEntity(
                barcode = "RET-LINE-01",
                name = "منتج بخصم سطر",
                categoryId = 1L,
                unit = "pcs",
                purchasePrice = 600L,
                sellPrice = 1_000L,
                currentStock = 8.0,
                minStock = 1.0
            )
        )
        val saleId = appDao.insertSale(
            SaleEntity(
                invoiceNumber = "FAC-RET-01",
                timestamp = 1_700_000_000_000L,
                cashierName = "admin",
                customerId = customerId,
                customerName = "زبون تجريبي",
                totalAmount = 1_700L,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 0L,
                changeAmount = 0L,
                paymentMethod = "DEBT"
            )
        )
        appDao.insertSaleItem(
            SaleItemEntity(
                saleId = saleId,
                productId = productId,
                productName = "منتج بخصم سطر",
                quantity = 2.0,
                unitPrice = 1_000L,       // 10.00 MAD before discount
                discountValue = 300L,     // 3.00 MAD line discount
                totalLinePrice = 1_700L,  // 17.00 MAD net paid
                unitCost = 600L
            )
        )

        val sale = appDao.getSaleById(saleId)!!
        val items = appDao.getSaleItems(saleId)
        val computed = ReturnRefundCalculator.computeLineReturnRefund(
            sale = sale,
            saleItems = items,
            productId = productId,
            returnQuantity = 1.0
        )
        assertEquals(850L, computed.maxRefundCents)
        assertEquals(300L, computed.lineDiscountCents)
        assertEquals(0L, computed.invoiceDiscountShareCents)
        assertTrue(computed.hasDiscountsApplied)

        // Data layer MUST reject attempting to refund the undiscounted 10.00 MAD
        try {
            repository.recordCustomerReturn(
                saleId = saleId,
                productId = productId,
                productName = "منتج بخصم سطر",
                quantity = 1.0,
                refundAmount = 10.0,
                isCreditRefund = true,
                customerId = customerId
            )
            fail("Repository must reject return refund exceeding net paid amount after line discount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().isNotBlank())
        }

        // Stock and customer balance must remain completely untouched after rejected over-refund
        assertEquals(8.0, appDao.getProductById(productId)!!.currentStock, 0.001)
        assertEquals(1_700L, appDao.getCustomerById(customerId)!!.balance)

        // Exact net refund (8.50 MAD) succeeds and deducts exactly 8.50 MAD from customer debt
        repository.recordCustomerReturn(
            saleId = saleId,
            productId = productId,
            productName = "منتج بخصم سطر",
            quantity = 1.0,
            refundAmount = 8.50,
            isCreditRefund = true,
            customerId = customerId
        )

        assertEquals(9.0, appDao.getProductById(productId)!!.currentStock, 0.001)
        assertEquals(850L, appDao.getCustomerById(customerId)!!.balance)
        assertEquals(850L, appDao.getCustomerById(customerId)!!.totalPurchases)
    }

    @Test
    fun testInvoiceLevelDiscountDistributesProRataAcrossLinesAndRejectsOverRefund() = runBlocking {
        // Line A: 2 x 30 MAD = 60 MAD (60% share). Line B: 2 x 20 MAD = 40 MAD (40% share).
        // Subtotal = 100 MAD, invoice discount = 20 MAD -> net total = 80 MAD.
        // Line A effective paid = 60 - 12 = 48 MAD (24 MAD/unit).
        val customerId = appDao.insertCustomer(
            CustomerEntity(name = "زبون خصم فاتورة", phone = "0600000002", balance = 8_000L, totalPurchases = 8_000L)
        )
        val prodAId = appDao.insertProduct(
            ProductEntity(
                barcode = "RET-INV-A",
                name = "صنف أ",
                categoryId = 1L,
                unit = "pcs",
                purchasePrice = 1_500L,
                sellPrice = 3_000L,
                currentStock = 10.0,
                minStock = 1.0
            )
        )
        val prodBId = appDao.insertProduct(
            ProductEntity(
                barcode = "RET-INV-B",
                name = "صنف ب",
                categoryId = 1L,
                unit = "pcs",
                purchasePrice = 1_000L,
                sellPrice = 2_000L,
                currentStock = 10.0,
                minStock = 1.0
            )
        )
        val saleId = appDao.insertSale(
            SaleEntity(
                invoiceNumber = "FAC-RET-02",
                timestamp = 1_700_000_010_000L,
                cashierName = "admin",
                customerId = customerId,
                customerName = "زبون خصم فاتورة",
                totalAmount = 8_000L,
                discountAmount = 2_000L, // 20.00 MAD invoice discount
                discountType = "VALUE",
                paidAmount = 0L,
                changeAmount = 0L,
                paymentMethod = "DEBT"
            )
        )
        appDao.insertSaleItem(
            SaleItemEntity(
                saleId = saleId,
                productId = prodAId,
                productName = "صنف أ",
                quantity = 2.0,
                unitPrice = 3_000L,
                discountValue = 0L,
                totalLinePrice = 6_000L,
                unitCost = 1_500L
            )
        )
        appDao.insertSaleItem(
            SaleItemEntity(
                saleId = saleId,
                productId = prodBId,
                productName = "صنف ب",
                quantity = 2.0,
                unitPrice = 2_000L,
                discountValue = 0L,
                totalLinePrice = 4_000L,
                unitCost = 1_000L
            )
        )

        val sale = appDao.getSaleById(saleId)!!
        val items = appDao.getSaleItems(saleId)
        val planA = ReturnRefundCalculator.computeLineReturnRefund(sale, items, prodAId, returnQuantity = 1.0)
        assertEquals(2_400L, planA.maxRefundCents)
        assertEquals(1_200L, planA.invoiceDiscountShareCents)

        // Undiscounted 30.00 MAD refund MUST be rejected
        try {
            repository.recordCustomerReturn(
                saleId = saleId,
                productId = prodAId,
                productName = "صنف أ",
                quantity = 1.0,
                refundAmount = 30.0,
                isCreditRefund = true,
                customerId = customerId
            )
            fail("Repository must reject over-refund ignoring invoice-level discount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().isNotBlank())
        }

        // Net pro-rata refund (24.00 MAD) succeeds
        repository.recordCustomerReturn(
            saleId = saleId,
            productId = prodAId,
            productName = "صنف أ",
            quantity = 1.0,
            refundAmount = 24.0,
            isCreditRefund = true,
            customerId = customerId
        )

        assertEquals(11.0, appDao.getProductById(prodAId)!!.currentStock, 0.001)
        assertEquals(5_600L, appDao.getCustomerById(customerId)!!.balance)
    }

    @Test
    fun testPartialReturnsWithCombinedDiscountsEnforceExactCumulativeCap() = runBlocking {
        // Line 1: 3 x 10 MAD = 30 MAD, line discount = 3 MAD -> line net = 27 MAD.
        // Line 2: 1 x 23 MAD = 23 MAD -> subtotal = 50 MAD.
        // Invoice discount = 5 MAD (10%) -> Line 1 share = 2.70 MAD -> Line 1 effective paid = 24.30 MAD (8.10 MAD/unit).
        val customerId = appDao.insertCustomer(
            CustomerEntity(name = "زبون مرتجع جزئي", phone = "0600000003", balance = 4_500L, totalPurchases = 4_500L)
        )
        val prod1Id = appDao.insertProduct(
            ProductEntity(
                barcode = "RET-PART-1",
                name = "منتج جزئي",
                categoryId = 1L,
                unit = "pcs",
                purchasePrice = 500L,
                sellPrice = 1_000L,
                currentStock = 20.0,
                minStock = 1.0
            )
        )
        val prod2Id = appDao.insertProduct(
            ProductEntity(
                barcode = "RET-PART-2",
                name = "منتج ثاني",
                categoryId = 1L,
                unit = "pcs",
                purchasePrice = 1_000L,
                sellPrice = 2_300L,
                currentStock = 20.0,
                minStock = 1.0
            )
        )
        val saleId = appDao.insertSale(
            SaleEntity(
                invoiceNumber = "FAC-RET-03",
                timestamp = 1_700_000_020_000L,
                cashierName = "admin",
                customerId = customerId,
                customerName = "زبون مرتجع جزئي",
                totalAmount = 4_500L,
                discountAmount = 500L,
                discountType = "VALUE",
                paidAmount = 0L,
                changeAmount = 0L,
                paymentMethod = "DEBT"
            )
        )
        appDao.insertSaleItem(
            SaleItemEntity(
                saleId = saleId,
                productId = prod1Id,
                productName = "منتج جزئي",
                quantity = 3.0,
                unitPrice = 1_000L,
                discountValue = 300L,
                totalLinePrice = 2_700L,
                unitCost = 500L
            )
        )
        appDao.insertSaleItem(
            SaleItemEntity(
                saleId = saleId,
                productId = prod2Id,
                productName = "منتج ثاني",
                quantity = 1.0,
                unitPrice = 2_300L,
                discountValue = 0L,
                totalLinePrice = 2_300L,
                unitCost = 1_000L
            )
        )

        // First partial return: 1 unit -> 8.10 MAD
        repository.recordCustomerReturn(
            saleId = saleId,
            productId = prod1Id,
            productName = "منتج جزئي",
            quantity = 1.0,
            refundAmount = 8.10,
            isCreditRefund = true,
            customerId = customerId
        )
        assertEquals(21.0, appDao.getProductById(prod1Id)!!.currentStock, 0.001)
        assertEquals(3_690L, appDao.getCustomerById(customerId)!!.balance)

        // Second partial return: remaining 2 units -> 16.20 MAD (total refunded = 24.30 MAD)
        repository.recordCustomerReturn(
            saleId = saleId,
            productId = prod1Id,
            productName = "منتج جزئي",
            quantity = 2.0,
            refundAmount = 16.20,
            isCreditRefund = true,
            customerId = customerId
        )
        assertEquals(23.0, appDao.getProductById(prod1Id)!!.currentStock, 0.001)
        assertEquals(2_070L, appDao.getCustomerById(customerId)!!.balance)

        // Third return attempt exceeds original sold quantity (3) -> MUST be rejected
        try {
            repository.recordCustomerReturn(
                saleId = saleId,
                productId = prod1Id,
                productName = "منتج جزئي",
                quantity = 1.0,
                refundAmount = 8.10,
                isCreditRefund = true,
                customerId = customerId
            )
            fail("Repository must reject returning more units than originally sold")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().isNotBlank())
        }

        // Also verify non-divisible 3-way partial split sums with zero cent drift (10.00 MAD / 3 = 3.33 + 3.34 + 3.33)
        val splitSale = SaleEntity(
            id = 99L,
            invoiceNumber = "SPLIT-3",
            timestamp = 1L,
            cashierName = "admin",
            totalAmount = 1_000L,
            discountAmount = 0L,
            discountType = "VALUE",
            paidAmount = 1_000L,
            changeAmount = 0L,
            paymentMethod = "CASH"
        )
        val splitItem = SaleItemEntity(
            saleId = 99L,
            productId = 77L,
            productName = "Split Item",
            quantity = 3.0,
            unitPrice = 400L,
            discountValue = 200L, // 12.00 - 2.00 = 10.00 MAD
            totalLinePrice = 1_000L
        )
        val r1 = ReturnRefundCalculator.computeLineReturnRefund(splitSale, listOf(splitItem), 77L, 1.0, 0.0).maxRefundCents
        val r2 = ReturnRefundCalculator.computeLineReturnRefund(splitSale, listOf(splitItem), 77L, 1.0, 1.0).maxRefundCents
        val r3 = ReturnRefundCalculator.computeLineReturnRefund(splitSale, listOf(splitItem), 77L, 1.0, 2.0).maxRefundCents
        assertEquals(1_000L, r1 + r2 + r3)
    }

    @Test
    fun testLegacyCustomerReturnsAreBackfilledAndCountedAgainstRemainingQty() = runBlocking {
        val customerId = appDao.insertCustomer(
            CustomerEntity(name = "زبون قديم", phone = "0600000009", balance = 1_700L, totalPurchases = 1_700L)
        )
        val productId = appDao.insertProduct(
            ProductEntity(
                barcode = "RET-LEGACY-1",
                name = "منتج مرتجع قديم",
                categoryId = 1L,
                unit = "pcs",
                purchasePrice = 500L,
                sellPrice = 1_000L,
                currentStock = 9.0,
                minStock = 1.0
            )
        )
        val saleId = appDao.insertSale(
            SaleEntity(
                invoiceNumber = "FAC-LEG-01",
                timestamp = 1_700_000_050_000L,
                cashierName = "admin",
                customerId = customerId,
                customerName = "زبون قديم",
                totalAmount = 1_700L,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 0L,
                changeAmount = 0L,
                paymentMethod = "DEBT"
            )
        )
        appDao.insertSaleItem(
            SaleItemEntity(
                saleId = saleId,
                productId = productId,
                productName = "منتج مرتجع قديم",
                quantity = 2.0,
                unitPrice = 1_000L,
                discountValue = 300L,
                totalLinePrice = 1_700L,
                unitCost = 500L
            )
        )

        // Insert a historical legacy return row WITHOUT referenceType/referenceId (pre-migration format)
        appDao.insertStockMovement(
            com.example.data.StockMovementEntity(
                productId = productId,
                productName = "منتج مرتجع قديم",
                type = "CUSTOMER_RETURN",
                quantity = 1.0,
                timestamp = 1_700_000_060_000L,
                notes = "إرجاع بضاعة من زبون / Retour vente: Facture #$saleId",
                referenceType = "",
                referenceId = 0L
            )
        )

        // Attempting to return 2 units must fail because 1 unit was already returned in the legacy row
        try {
            repository.recordCustomerReturn(
                saleId = saleId,
                productId = productId,
                productName = "منتج مرتجع قديم",
                quantity = 2.0,
                refundAmount = 17.0,
                isCreditRefund = true,
                customerId = customerId
            )
            fail("Must reject returning 2 units when 1 unit was already returned in a legacy stock_movements row")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().isNotBlank())
        }

        // Returning the single remaining unit at exact net price (8.50 MAD) succeeds and backfills legacy row
        repository.recordCustomerReturn(
            saleId = saleId,
            productId = productId,
            productName = "منتج مرتجع قديم",
            quantity = 1.0,
            refundAmount = 8.50,
            isCreditRefund = true,
            customerId = customerId
        )

        val linkedReturns = appDao.getStockMovementsByReference("SALE_RETURN", saleId)
        assertEquals("Both legacy and new returns must now carry SALE_RETURN reference", 2, linkedReturns.size)
        assertEquals(2.0, linkedReturns.sumOf { it.quantity }, 0.001)
        assertEquals(850L, appDao.getCustomerById(customerId)!!.balance)
    }
}
