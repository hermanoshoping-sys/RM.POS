package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.domain.FinancialCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NegativeSaleAndSupplierReturnTest {

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

    // ------------------------------------------------------------------------
    // 1. FinancialCalculator — Customer Negative Return & Supplier Return Math
    // ------------------------------------------------------------------------

    @Test
    fun computeCartSummary_negativeReturn_cashAndCreditAndPartial() {
        // Full cash refund of -50.00
        val cashRefund = FinancialCalculator.computeCartSummary(
            subtotalCents = -5000L,
            discountType = "VALUE",
            discountValue = 0.0,
            paymentMethod = "CASH",
            receivedCash = -50.0
        )
        assertEquals(-5000L, cashRefund.netTotalCents)
        assertEquals(-5000L, cashRefund.paidCents)
        assertEquals(0L, cashRefund.remainingDebtCents)
        assertEquals(0L, cashRefund.customerCreditCents)
        assertEquals(0L, cashRefund.signedBalanceDeltaCents)
        assertEquals(0L, cashRefund.changeDueCents)
        assertEquals("CASH", cashRefund.finalPaymentMethod)

        // Full credit to customer debt (deduct 50.00 from balance)
        val creditReturn = FinancialCalculator.computeCartSummary(
            subtotalCents = -5000L,
            discountType = "VALUE",
            discountValue = 0.0,
            paymentMethod = "CREDIT",
            receivedCash = 0.0
        )
        assertEquals(-5000L, creditReturn.netTotalCents)
        assertEquals(0L, creditReturn.paidCents)
        assertEquals(0L, creditReturn.remainingDebtCents)
        assertEquals(5000L, creditReturn.customerCreditCents)
        assertEquals(-5000L, creditReturn.signedBalanceDeltaCents)
        assertEquals("CREDIT", creditReturn.finalPaymentMethod)

        // Partial cash refund (-20.00 cash, 30.00 deducted from customer debt)
        val partialRefund = FinancialCalculator.computeCartSummary(
            subtotalCents = -5000L,
            discountType = "VALUE",
            discountValue = 0.0,
            paymentMethod = "CASH",
            receivedCash = -20.0
        )
        assertEquals(-5000L, partialRefund.netTotalCents)
        assertEquals(-2000L, partialRefund.paidCents)
        assertEquals(3000L, partialRefund.customerCreditCents)
        assertEquals(-3000L, partialRefund.signedBalanceDeltaCents)
        assertEquals("CREDIT", partialRefund.finalPaymentMethod)
    }

    @Test
    fun computePaymentSettlement_negativeSupplierReturn_cashAndDebtCredit() {
        // Full cash refund from supplier (-300.00)
        val cashRefund = FinancialCalculator.computePaymentSettlement(
            totalCents = -30000L,
            paidInputCents = -30000L
        )
        assertEquals(-30000L, cashRefund.paidCents)
        assertEquals(0L, cashRefund.remainingDebtCents)
        assertEquals(0L, cashRefund.supplierCreditCents)
        assertEquals(0L, cashRefund.signedBalanceDeltaCents)

        // Full credit deducted from supplier debt (paid = 0)
        val debtCredit = FinancialCalculator.computePaymentSettlement(
            totalCents = -30000L,
            paidInputCents = 0L
        )
        assertEquals(0L, debtCredit.paidCents)
        assertEquals(0L, debtCredit.remainingDebtCents)
        assertEquals(30000L, debtCredit.supplierCreditCents)
        assertEquals(-30000L, debtCredit.signedBalanceDeltaCents)

        // Partial cash refund (-100.00 cash, 200.00 deducted from supplier debt)
        val partial = FinancialCalculator.computePaymentSettlement(
            totalCents = -30000L,
            paidInputCents = -10000L
        )
        assertEquals(-10000L, partial.paidCents)
        assertEquals(0L, partial.remainingDebtCents)
        assertEquals(20000L, partial.supplierCreditCents)
        assertEquals(-20000L, partial.signedBalanceDeltaCents)
    }

    @Test
    fun ledgerSummaries_handleNegativeSalesAndPurchasesAccurately() {
        val now = System.currentTimeMillis()
        val sales = listOf(
            SaleEntity(
                id = 1L, invoiceNumber = "S-1", timestamp = now, cashierName = "Admin",
                totalAmount = 10000L, discountAmount = 0L, discountType = "VALUE",
                paidAmount = 10000L, changeAmount = 0L, paymentMethod = "CASH"
            ),
            SaleEntity(
                id = 2L, invoiceNumber = "S-2", timestamp = now, cashierName = "Admin",
                totalAmount = -4000L, discountAmount = 0L, discountType = "VALUE",
                paidAmount = -4000L, changeAmount = 0L, paymentMethod = "CASH"
            ),
            SaleEntity(
                id = 3L, invoiceNumber = "S-3", timestamp = now, cashierName = "Admin",
                totalAmount = -2000L, discountAmount = 0L, discountType = "VALUE",
                paidAmount = 0L, changeAmount = 0L, paymentMethod = "CREDIT"
            )
        )
        val salesLedger = FinancialCalculator.computeSalesLedgerSummary(sales)
        assertEquals(4000L, salesLedger.totalAmountCents) // 100 - 40 - 20 = 40
        assertEquals(6000L, salesLedger.totalPaidCents)   // 100 - 40 + 0 = 60

        val purchases = listOf(
            PurchaseEntity(
                id = 1L, timestamp = now, supplierId = 1L, supplierName = "Sup",
                totalAmount = 50000L, paidAmount = 30000L
            ),
            PurchaseEntity(
                id = 2L, timestamp = now, supplierId = 1L, supplierName = "Sup",
                totalAmount = -10000L, paidAmount = 0L
            )
        )
        val purchaseLedger = FinancialCalculator.computePurchasesLedgerSummary(purchases)
        assertEquals(40000L, purchaseLedger.totalAmountCents)
        assertEquals(30000L, purchaseLedger.totalPaidCents)
        assertEquals(10000L, purchaseLedger.totalDebtCents)
    }

    // ------------------------------------------------------------------------
    // 2. PosRepository — POS Customer Negative Return End-to-End
    // ------------------------------------------------------------------------

    @Test
    fun posCustomerNegativeSale_cashRefund_restoresStockAndPreservesBalance() = runBlocking {
        val product = ProductEntity(
            id = 101L,
            barcode = "RET-101",
            name = "شاي أخضر",
            categoryId = 1L,
            categoryName = "مشروبات",
            unit = "علبة",
            purchasePrice = 15.0,
            sellPrice = 25.0,
            currentStock = 10.0,
            minStock = 2.0
        )
        appDao.insertProduct(product)

        val customer = CustomerEntity(
            id = 201L,
            name = "كريم العمري",
            phone = "0611111111",
            balance = 100.0,
            totalPurchases = 200.0
        )
        appDao.insertCustomer(customer)

        val sale = SaleEntity(
            invoiceNumber = "INV-RET-1",
            timestamp = System.currentTimeMillis(),
            cashierName = "Admin",
            customerId = customer.id,
            customerName = customer.name,
            totalAmount = -5000L,
            discountAmount = 0L,
            discountType = "VALUE",
            paidAmount = -5000L,
            changeAmount = 0L,
            paymentMethod = "CASH"
        )
        val items = listOf(
            SaleItemEntity(
                saleId = 0L,
                productId = product.id,
                productName = product.name,
                quantity = -2.0,
                unitPrice = 2500L,
                totalLinePrice = -5000L,
                unitCost = 1500L
            )
        )

        val manager = UserEntity(id = 1L, username = "admin", passwordHash = "hash", role = UserRole.MANAGER)
        val saved = repository.recordSaleInvoice(sale, items, manager)
        assertTrue(saved.id > 0L)

        // Stock increased by +2 (10 -> 12)
        val afterProduct = appDao.getProductById(product.id)!!
        assertEquals(12.0, afterProduct.currentStock, 0.001)

        // Cash refund does NOT change customer debt balance
        val afterCustomer = appDao.getCustomerById(customer.id)!!
        assertEquals(10000L, afterCustomer.balance)

        // Stock movement recorded as CUSTOMER_RETURN with +2.0
        val movements = appDao.getAllStockMovements().first().filter { it.productId == product.id }
        assertTrue(movements.any { it.type == "CUSTOMER_RETURN" && it.quantity == 2.0 })

        // Voiding reverses stock back to 10 and keeps debt unchanged
        repository.voidSaleInvoice(saved.id)
        assertEquals(10.0, appDao.getProductById(product.id)!!.currentStock, 0.001)
        assertEquals(10000L, appDao.getCustomerById(customer.id)!!.balance)
    }

    @Test
    fun posCustomerNegativeSale_creditReturn_reducesCustomerDebtAndReversesOnVoidAndDelete() = runBlocking {
        val product = ProductEntity(
            id = 102L,
            barcode = "RET-102",
            name = "قهوة ممتازة",
            categoryId = 1L,
            categoryName = "مشروبات",
            unit = "علبة",
            purchasePrice = 20.0,
            sellPrice = 30.0,
            currentStock = 10.0,
            minStock = 2.0
        )
        appDao.insertProduct(product)

        val customer = CustomerEntity(
            id = 202L,
            name = "سعيد بنعلي",
            phone = "0622222222",
            balance = 100.0,
            totalPurchases = 300.0
        )
        appDao.insertCustomer(customer)

        // Return -2 units @ 30.00 = -60.00 credited to customer debt (paidAmount = 0)
        val sale = SaleEntity(
            invoiceNumber = "INV-RET-2",
            timestamp = System.currentTimeMillis(),
            cashierName = "Admin",
            customerId = customer.id,
            customerName = customer.name,
            totalAmount = -6000L,
            discountAmount = 0L,
            discountType = "VALUE",
            paidAmount = 0L,
            changeAmount = 0L,
            paymentMethod = "CREDIT"
        )
        val items = listOf(
            SaleItemEntity(
                saleId = 0L,
                productId = product.id,
                productName = product.name,
                quantity = -2.0,
                unitPrice = 3000L,
                totalLinePrice = -6000L,
                unitCost = 2000L
            )
        )

        val manager = UserEntity(id = 1L, username = "admin", passwordHash = "hash", role = UserRole.MANAGER)
        val saved = repository.recordSaleInvoice(sale, items, manager)

        // Stock: 10 -> 12; Customer balance: 100.00 -> 40.00 (4000 cents)
        assertEquals(12.0, appDao.getProductById(product.id)!!.currentStock, 0.001)
        assertEquals(4000L, appDao.getCustomerById(customer.id)!!.balance)

        // Voiding restores stock to 10 and customer debt back to 100.00
        repository.voidSaleInvoice(saved.id)
        assertEquals(10.0, appDao.getProductById(product.id)!!.currentStock, 0.001)
        assertEquals(10000L, appDao.getCustomerById(customer.id)!!.balance)

        // Record again and verify deleteSaleInvoice also reverses cleanly
        val saved2 = repository.recordSaleInvoice(sale.copy(id = 0L, invoiceNumber = "INV-RET-3"), items, manager)
        assertEquals(12.0, appDao.getProductById(product.id)!!.currentStock, 0.001)
        assertEquals(4000L, appDao.getCustomerById(customer.id)!!.balance)

        repository.deleteSaleInvoice(saved2.id, manager)
        assertEquals(10.0, appDao.getProductById(product.id)!!.currentStock, 0.001)
        assertEquals(10000L, appDao.getCustomerById(customer.id)!!.balance)
    }

    @Test
    fun persistentCart_preservesNegativeQuantitiesAndSignedTotals() = runBlocking {
        val product = ProductEntity(
            id = 103L,
            barcode = "RET-103",
            name = "سكر 1Kg",
            categoryId = 1L,
            categoryName = "مواد",
            unit = "كغ",
            purchasePrice = 5.0,
            sellPrice = 7.5,
            currentStock = 30.0,
            minStock = 5.0
        )
        appDao.insertProduct(product)

        repository.addToPersistentCart(product = product, quantity = -2.0)
        val cartAfterAdd = repository.getCartItemsList("ACTIVE")
        assertEquals(1, cartAfterAdd.size)
        assertEquals(-2.0, cartAfterAdd[0].quantity, 0.001)
        assertEquals(-1500L, cartAfterAdd[0].totalLinePrice)

        repository.updatePersistentCartQuantity(cartAfterAdd[0].id, -4.0, 7.5, 0.0)
        val cartAfterUpdate = repository.getCartItemsList("ACTIVE")
        assertEquals(1, cartAfterUpdate.size)
        assertEquals(-4.0, cartAfterUpdate[0].quantity, 0.001)
        assertEquals(-3000L, cartAfterUpdate[0].totalLinePrice)

        // Updating to 0.0 removes item
        repository.updatePersistentCartQuantity(cartAfterUpdate[0].id, 0.0, 7.5, 0.0)
        assertTrue(repository.getCartItemsList("ACTIVE").isEmpty())
    }

    // ------------------------------------------------------------------------
    // 3. PosRepository — Supplier Negative Return End-to-End
    // ------------------------------------------------------------------------

    @Test
    fun supplierNegativeReturn_deductsStockAndDebtAndReversesOnVoidAndDelete() = runBlocking {
        val product = ProductEntity(
            id = 301L,
            barcode = "SUP-301",
            name = "دقيق ممتاز 25Kg",
            categoryId = 2L,
            categoryName = "مواد غذائية",
            unit = "كيس",
            purchasePrice = 40.0,
            sellPrice = 55.0,
            currentStock = 20.0,
            minStock = 5.0
        )
        appDao.insertProduct(product)

        val supplier = SupplierEntity(
            id = 401L,
            name = "مطاحن الأطلس",
            phone = "0522111111",
            debtBalance = 500.0
        )
        appDao.insertSupplier(supplier)

        // Supplier return of -3 bags @ 40.00 = -120.00 credited against supplier debt (paidAmount = 0)
        val returnPurchase = PurchaseEntity(
            timestamp = System.currentTimeMillis(),
            supplierId = supplier.id,
            supplierName = supplier.name,
            totalAmount = -12000L,
            paidAmount = 0L
        )
        val returnItems = listOf(
            PurchaseItemEntity(
                purchaseId = 0L,
                productId = product.id,
                productName = product.name,
                quantity = -3.0,
                unitPrice = 4000L
            )
        )

        val manager = UserEntity(id = 1L, username = "admin", passwordHash = "hash", role = UserRole.MANAGER)
        val savedId = repository.recordPurchaseInvoice(returnPurchase, returnItems, manager)
        assertTrue(savedId > 0L)

        // Stock decreased: 20 -> 17
        assertEquals(17.0, appDao.getProductById(product.id)!!.currentStock, 0.001)
        // Supplier debt reduced: 500.00 -> 380.00 (38000 cents)
        assertEquals(38000L, appDao.getSupplierById(supplier.id)!!.debtBalance)

        // Movement recorded as SUPPLIER_RETURN with -3.0
        val movements = appDao.getAllStockMovements().first().filter { it.productId == product.id }
        assertTrue(movements.any { it.type == "SUPPLIER_RETURN" && it.quantity == -3.0 })

        // Voiding restores stock back to 20 and supplier debt back to 500.00
        repository.voidPurchaseInvoice(savedId)
        assertEquals(20.0, appDao.getProductById(product.id)!!.currentStock, 0.001)
        assertEquals(50000L, appDao.getSupplierById(supplier.id)!!.debtBalance)

        // Cash refund from supplier (-120.00 total, -120.00 cash received): stock drops 20 -> 17, debt stays 500.00
        val cashRefundPurchase = returnPurchase.copy(id = 0L, paidAmount = -12000L)
        val savedCashId = repository.recordPurchaseInvoice(cashRefundPurchase, returnItems, manager)
        assertEquals(17.0, appDao.getProductById(product.id)!!.currentStock, 0.001)
        assertEquals(50000L, appDao.getSupplierById(supplier.id)!!.debtBalance)

        // Deleting unvoided cash refund return restores stock 17 -> 20 and keeps debt 500.00
        repository.deletePurchaseInvoice(savedCashId, manager)
        assertEquals(20.0, appDao.getProductById(product.id)!!.currentStock, 0.001)
        assertEquals(50000L, appDao.getSupplierById(supplier.id)!!.debtBalance)
    }
}
