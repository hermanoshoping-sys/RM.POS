package com.example.domain

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.components.PosErrorUiTranslator
import com.example.ui.viewmodels.CartViewModel
import com.example.ui.viewmodels.SalesViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinancialAndErrorHandlingTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var appDao: AppDao
    private lateinit var repository: PosRepository
    private lateinit var vmScope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appDao = database.appDao()
        repository = PosRepository(appDao, database)
        vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        vmScope.cancel()
        database.close()
    }

    // --- 1. FinancialCalculator Pure Domain Math ---

    @Test
    fun computeCartSummary_handlesPercentAndFixedDiscountsAndChange() {
        val percentSummary = FinancialCalculator.computeCartSummary(
            subtotalCents = 25_000L, // 250.00
            discountType = "PERCENT",
            discountValue = 20.0,
            paymentMethod = "CASH",
            receivedCash = 300.0
        )
        assertEquals(5_000L, percentSummary.discountCents)
        assertEquals(20_000L, percentSummary.netTotalCents)
        assertEquals(20_000L, percentSummary.paidCents)
        assertEquals(10_000L, percentSummary.changeDueCents)
        assertEquals(0L, percentSummary.remainingDebtCents)
        assertEquals("CASH", percentSummary.finalPaymentMethod)

        val cappedFixedSummary = FinancialCalculator.computeCartSummary(
            subtotalCents = 10_000L, // 100.00
            discountType = "VALUE",
            discountValue = 500.0, // exceeds subtotal -> capped at subtotal
            paymentMethod = "CASH",
            receivedCash = 0.0
        )
        assertEquals(10_000L, cappedFixedSummary.discountCents)
        assertEquals(0L, cappedFixedSummary.netTotalCents)
        assertEquals(0L, cappedFixedSummary.paidCents)
        assertEquals(0L, cappedFixedSummary.remainingDebtCents)
    }

    @Test
    fun computeCartSummary_handlesCreditPartialAndTransferMethods() {
        val partialCredit = FinancialCalculator.computeCartSummary(
            subtotalCents = 50_000L,
            discountType = "VALUE",
            discountValue = 50.0,
            paymentMethod = "CREDIT",
            receivedCash = 150.0
        )
        assertEquals(5_000L, partialCredit.discountCents)
        assertEquals(45_000L, partialCredit.netTotalCents)
        assertEquals(15_000L, partialCredit.paidCents)
        assertEquals(30_000L, partialCredit.remainingDebtCents)
        assertEquals(0L, partialCredit.changeDueCents)
        assertEquals("DEBT", partialCredit.finalPaymentMethod)

        // Partial cash payment automatically converts to DEBT
        val partialCash = FinancialCalculator.computeCartSummary(
            subtotalCents = 50_000L,
            discountType = "VALUE",
            discountValue = 0.0,
            paymentMethod = "CASH",
            receivedCash = 200.0
        )
        assertEquals(50_000L, partialCash.netTotalCents)
        assertEquals(20_000L, partialCash.paidCents)
        assertEquals(30_000L, partialCash.remainingDebtCents)
        assertEquals(0L, partialCash.changeDueCents)
        assertEquals("DEBT", partialCash.finalPaymentMethod)

        val fullCreditPaid = FinancialCalculator.computeCartSummary(
            subtotalCents = 20_000L,
            discountType = "VALUE",
            discountValue = 0.0,
            paymentMethod = "DEBT",
            receivedCash = 200.0
        )
        assertEquals(20_000L, fullCreditPaid.paidCents)
        assertEquals(0L, fullCreditPaid.remainingDebtCents)
        assertEquals("CASH", fullCreditPaid.finalPaymentMethod)

        val transfer = FinancialCalculator.computeCartSummary(
            subtotalCents = 12_000L,
            discountType = "VALUE",
            discountValue = 0.0,
            paymentMethod = "VIREMENT",
            receivedCash = 0.0
        )
        assertEquals("TRANSFER", transfer.finalPaymentMethod)
        assertEquals(12_000L, transfer.paidCents)
    }

    @Test
    fun computeEditedInvoiceReturnAndProfitMargin_maintainExactCents() {
        // Previously fully paid invoice edited to a higher total -> still considered fully paid unless overridden
        val paidUp = FinancialCalculator.computeEditedInvoiceSummary(
            newTotalCents = 18_500L,
            originalTotalCents = 15_000L,
            originalPaidCents = 15_000L
        )
        assertEquals(18_500L, paidUp.paidCents)
        assertEquals(0L, paidUp.remainingDebtCents)

        // Partial credit invoice edited downward below original paid -> paid capped cleanly
        val creditEdited = FinancialCalculator.computeEditedInvoiceSummary(
            newTotalCents = 8_000L,
            originalTotalCents = 15_000L,
            originalPaidCents = 10_000L
        )
        assertEquals(8_000L, creditEdited.paidCents)
        assertEquals(0L, creditEdited.remainingDebtCents)

        // Return amount with fractional quantity
        assertEquals(3_750L, FinancialCalculator.computeReturnAmountCents(1_500L, -2.5))
        assertEquals(37.50, FinancialCalculator.computeReturnAmountDouble(15.0, 2.5), 0.001)

        // Profit margin
        val margin = FinancialCalculator.computeProfitMargin(revenueCents = 20_000L, costCents = 15_000L)
        assertEquals(5_000L, margin.grossProfitCents)
        assertEquals(25.0, margin.marginPercent, 0.01)
    }

    @Test
    fun computeLedgersSummary_aggregatesSalesAndPurchasesSeparatelyFromUi() {
        val sales = listOf(
            SaleEntity(id = 1L, invoiceNumber = "FAC-1", timestamp = 1L, cashierName = "a", totalAmount = 100_00L, discountAmount = 0L, discountType = "VALUE", paidAmount = 100_00L, changeAmount = 0L, paymentMethod = "CASH"),
            SaleEntity(id = 2L, invoiceNumber = "FAC-2", timestamp = 2L, cashierName = "a", totalAmount = 250_00L, discountAmount = 0L, discountType = "VALUE", paidAmount = 50_00L, changeAmount = 0L, paymentMethod = "DEBT")
        )
        val salesLedger = FinancialCalculator.computeSalesLedgerSummary(sales)
        assertEquals(2, salesLedger.count)
        assertEquals(350_00L, salesLedger.totalAmountCents)
        assertEquals(150_00L, salesLedger.totalPaidCents)
        assertEquals(200_00L, salesLedger.totalDebtCents)

        val purchases = listOf(
            PurchaseEntity(id = 1L, timestamp = 1L, supplierId = 1L, supplierName = "S1", totalAmount = 400_00L, paidAmount = 100_00L),
            PurchaseEntity(id = 2L, timestamp = 2L, supplierId = 1L, supplierName = "S1", totalAmount = 100_00L, paidAmount = 100_00L)
        )
        val purLedger = FinancialCalculator.computePurchasesLedgerSummary(purchases)
        assertEquals(2, purLedger.count)
        assertEquals(500_00L, purLedger.totalAmountCents)
        assertEquals(200_00L, purLedger.totalPaidCents)
        assertEquals(300_00L, purLedger.totalDebtCents)
    }

    // --- 2. PosErrorMapper (Pure Domain) & PosErrorUiTranslator (UI Localization) ---

    @Test
    fun errorMapper_mapsAllDomainExceptionsAndTranslatesInUiLayer() = runBlocking {
        val perm = PosErrorMapper.fromThrowable(SecurityException("No permission"), OperationType.SALE_DELETE)
        assertEquals(OperationErrorCode.PERMISSION_DENIED, perm.code)

        val stock = PosErrorMapper.fromThrowable(IllegalStateException("insufficient stock"), OperationType.SALE_CHECKOUT)
        assertEquals(OperationErrorCode.INSUFFICIENT_STOCK, stock.code)

        val geo = PosErrorMapper.fromThrowable(IllegalArgumentException("Customer out of geo fence"), OperationType.SALE_CHECKOUT)
        assertEquals(OperationErrorCode.GEO_FENCE_BLOCKED, geo.code)

        val dup = PosErrorMapper.fromThrowable(IllegalStateException("duplicate invoice"), OperationType.SALE_NUMBER_UPDATE)
        assertEquals(OperationErrorCode.DUPLICATE_INVOICE_NUMBER, dup.code)

        val constraint = PosErrorMapper.fromThrowable(DomainDatabaseConstraintException("FK violation"), OperationType.PURCHASE_CREATE)
        assertEquals(OperationErrorCode.DATABASE_CONSTRAINT, constraint.code)

        // Repository layer translates SQLiteConstraintException into pure-domain DomainDatabaseConstraintException
        var caughtDomainEx: Throwable? = null
        try {
            repository.runInTransaction {
                throw SQLiteConstraintException("UNIQUE constraint failed")
            }
        } catch (t: Throwable) {
            caughtDomainEx = t
        }
        assertTrue(caughtDomainEx is DomainDatabaseConstraintException)

        val emptyEn = PosErrorUiTranslator.defaultMessage(OperationErrorCode.EMPTY_ITEMS, "EN")
        val emptyFr = PosErrorUiTranslator.defaultMessage(OperationErrorCode.EMPTY_ITEMS, "FR")
        val emptyAr = PosErrorUiTranslator.defaultMessage(OperationErrorCode.EMPTY_ITEMS, "AR")
        assertNotEquals(emptyEn, emptyFr)
        assertNotEquals(emptyEn, emptyAr)
        assertNotEquals(PosErrorUiTranslator.localizeError(constraint, "FR").message, PosErrorUiTranslator.localizeError(constraint, "AR").message)
    }

    // --- 3. CartViewModel & SalesViewModel Unified Error Handling ---

    @Test
    fun cartCheckout_failsCleanlyWithStructuredErrorWhenCartEmptyOrUnauthorized() = runBlocking {
        val reportedErrors = mutableListOf<PosOperationError>()
        val cartVm = CartViewModel(
            repository = repository,
            externalScope = vmScope,
            onOperationError = { reportedErrors.add(it) }
        )

        // Case A: Empty cart checkout (with authenticated manager user)
        val authorizedManager = UserEntity(
            username = "admin",
            passwordHash = "hash",
            role = UserRole.MANAGER,
            canSell = true
        )
        val emptyDone = CompletableDeferred<String>()
        cartVm.executeCheckout(
            appContext = context,
            currentUser = authorizedManager,
            language = "EN",
            settings = GlobalSettingsEntity(),
            printerConnectionType = "BLUETOOTH",
            printerName = "",
            printerMac = "",
            printerIp = "",
            printerPort = 9100,
            printerPaperWidth = 80,
            autoPrintReceipt = false,
            onError = { emptyDone.complete(it) },
            onComplete = { fail("Empty cart must not complete checkout") }
        )
        val emptyErrMsg = withTimeout(5_000L) { emptyDone.await() }
        assertTrue(emptyErrMsg.isNotBlank())
        assertEquals(OperationErrorCode.EMPTY_ITEMS, cartVm.latestCheckoutError.value?.code)
        assertEquals(1, reportedErrors.size)

        // Case B: Unauthorized cashier (canSell = false)
        val cashierNoSell = UserEntity(
            username = "viewer",
            passwordHash = "hash",
            role = UserRole.CASHIER,
            canSell = false
        )
        val prod = ProductEntity(
            id = 10L,
            barcode = "1001",
            name = "Item 1",
            categoryId = 1L,
            unit = "pcs",
            purchasePrice = 500L,
            sellPrice = 1000L,
            currentStock = 10.0,
            minStock = 1.0
        )
        appDao.insertProduct(prod)
        cartVm.addToCart(prod, 1.0)

        val authDone = CompletableDeferred<String>()
        cartVm.executeCheckout(
            appContext = context,
            currentUser = cashierNoSell,
            language = "FR",
            settings = GlobalSettingsEntity(),
            printerConnectionType = "BLUETOOTH",
            printerName = "",
            printerMac = "",
            printerIp = "",
            printerPort = 9100,
            printerPaperWidth = 80,
            autoPrintReceipt = false,
            onError = { authDone.complete(it) },
            onComplete = { fail("Unauthorized user must not complete checkout") }
        )
        withTimeout(5_000L) { authDone.await() }
        assertEquals(OperationErrorCode.PERMISSION_DENIED, cartVm.latestCheckoutError.value?.code)
    }

    @Test
    fun salesViewModel_emitsUnifiedPosOperationResultsForInvalidInputsDuplicatesAndStockErrors() = runBlocking {
        val salesVm = SalesViewModel(repository = repository, externalScope = vmScope)

        val testManager = UserEntity(id = 1L, username = "admin", passwordHash = "hash", role = UserRole.MANAGER)
        // 1. Record sale with empty items -> EMPTY_ITEMS failure
        val emptySaleRes = CompletableDeferred<PosOperationResult<Unit>>()
        salesVm.recordSale(
            sale = SaleEntity(invoiceNumber = "", timestamp = 1L, cashierName = "admin", totalAmount = 0L, discountAmount = 0L, discountType = "VALUE", paidAmount = 0L, changeAmount = 0L, paymentMethod = "CASH"),
            items = emptyList(),
            currentUser = testManager,
            lang = "EN",
            onOperationResult = { emptySaleRes.complete(it) }
        )
        val res1 = withTimeout(5_000L) { emptySaleRes.await() }
        assertTrue(res1 is PosOperationResult.Failure)
        assertEquals(OperationErrorCode.EMPTY_ITEMS, res1.errorOrNull()?.code)
        assertEquals(OperationType.SALE_CREATE, salesVm.latestOperationError.value?.operationType)

        // 2. Customer return with <= 0 quantity -> INVALID_AMOUNT failure
        val invalidReturnRes = CompletableDeferred<PosOperationResult<Unit>>()
        salesVm.recordCustomerReturn(
            saleId = 1L,
            productId = 1L,
            productName = "Test",
            quantity = 0.0,
            refundAmount = 10.0,
            isCreditRefund = false,
            customerId = 1L,
            lang = "EN",
            onOperationResult = { invalidReturnRes.complete(it) }
        )
        val res2 = withTimeout(5_000L) { invalidReturnRes.await() }
        assertTrue(res2 is PosOperationResult.Failure)
        assertEquals(OperationErrorCode.INVALID_AMOUNT, res2.errorOrNull()?.code)

        // 3. Insufficient stock on sale record -> INSUFFICIENT_STOCK failure
        val lowProductId = appDao.insertProduct(
            ProductEntity(
                barcode = "LOW-01",
                name = "Scarce Item",
                categoryId = 1L,
                unit = "pcs",
                purchasePrice = 500L,
                sellPrice = 900L,
                currentStock = 2.0,
                minStock = 1.0
            )
        )
        val stockFailRes = CompletableDeferred<PosOperationResult<Unit>>()
        salesVm.recordSale(
            sale = SaleEntity(invoiceNumber = "FAC-900", timestamp = 100L, cashierName = "admin", totalAmount = 4500L, discountAmount = 0L, discountType = "VALUE", paidAmount = 4500L, changeAmount = 0L, paymentMethod = "CASH"),
            items = listOf(SaleItemEntity(saleId = 0L, productId = lowProductId, productName = "Scarce Item", quantity = 5.0, unitPrice = 900L, discountValue = 0L, totalLinePrice = 4500L)),
            currentUser = testManager,
            lang = "AR",
            onOperationResult = { stockFailRes.complete(it) }
        )
        val res3 = withTimeout(5_000L) { stockFailRes.await() }
        assertTrue(res3 is PosOperationResult.Failure)
        assertEquals(OperationErrorCode.INSUFFICIENT_STOCK, res3.errorOrNull()?.code)

        // 4. Duplicate invoice number update -> DUPLICATE_INVOICE_NUMBER failure
        val saleA = appDao.insertSale(
            SaleEntity(invoiceNumber = "INV-100", timestamp = 100L, cashierName = "admin", totalAmount = 1000L, discountAmount = 0L, discountType = "VALUE", paidAmount = 1000L, changeAmount = 0L, paymentMethod = "CASH")
        )
        val saleB = appDao.insertSale(
            SaleEntity(invoiceNumber = "INV-200", timestamp = 200L, cashierName = "admin", totalAmount = 1000L, discountAmount = 0L, discountType = "VALUE", paidAmount = 1000L, changeAmount = 0L, paymentMethod = "CASH")
        )
        assertTrue(saleA > 0L && saleB > 0L)

        val dupRes = CompletableDeferred<PosOperationResult<Unit>>()
        salesVm.updateSaleInvoiceNumber(
            saleId = saleB,
            newInvoiceNumber = "INV-100",
            lang = "EN",
            onOperationResult = { dupRes.complete(it) }
        )
        val res4 = withTimeout(5_000L) { dupRes.await() }
        assertTrue(res4 is PosOperationResult.Failure)
        assertEquals(OperationErrorCode.DUPLICATE_INVOICE_NUMBER, res4.errorOrNull()?.code)
    }
}
