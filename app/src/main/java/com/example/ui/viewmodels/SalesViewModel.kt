package com.example.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.OperationErrorCode
import com.example.domain.OperationType
import com.example.domain.PosOperationError
import com.example.domain.PosOperationResult
import com.example.ui.components.PosErrorUiTranslator
import com.example.ui.theme.Bilingual
import com.example.util.cents
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Modular ViewModel for Sales, Purchases, Orders, Transactions, Returns, and Invoice Settlement (< 500 lines).
 */
class SalesViewModel(
    private val repository: PosRepository,
    externalScope: CoroutineScope? = null,
    private val onBeep: () -> Unit = {}
) : ViewModel() {

    companion object {
        private const val TAG = "SalesViewModel"
    }

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val _latestOperationError = MutableStateFlow<PosOperationError?>(null)
    val latestOperationError: StateFlow<PosOperationError?> = _latestOperationError.asStateFlow()

    private val _latestOperationSuccess = MutableStateFlow<String?>(null)
    val latestOperationSuccess: StateFlow<String?> = _latestOperationSuccess.asStateFlow()

    private val purchaseDelegate = PurchaseLedgerOperationsDelegate(
        repository = repository,
        scope = scope,
        onBeep = onBeep,
        onSuccessMessage = { msg ->
            _latestOperationError.value = null
            _latestOperationSuccess.value = msg
        },
        onError = { err -> _latestOperationError.value = err },
        runSalesOperation = ::runSalesOperation
    )

    val sales: StateFlow<List<SaleEntity>> = repository.sales
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val purchases: StateFlow<List<PurchaseEntity>> = repository.purchases
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val purchaseItems: StateFlow<List<PurchaseItemEntity>> = repository.purchaseItems
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val productSalesHistory: StateFlow<List<ProductSaleHistoryRecord>> = repository.productSalesHistory
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val orders: StateFlow<List<OrderEntity>> = repository.orders
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ordersWithItems: StateFlow<List<OrderWithItems>> = repository.ordersWithItems
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = repository.transactions
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactionsWithItems: StateFlow<List<TransactionWithItems>> = repository.transactionsWithItems
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unsyncedTransactions: StateFlow<List<TransactionEntity>> = repository.unsyncedTransactions
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSales: StateFlow<List<SaleEntity>> = repository.getRecentSales(10)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeSalesCount: StateFlow<Int> = repository.getActiveSalesCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)
    val totalSalesSum: StateFlow<Double> = repository.getTotalSalesSum()
        .map { it ?: 0.0 }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _salesPage = MutableStateFlow(0)
    val salesPage: StateFlow<Int> = _salesPage.asStateFlow()
    val salesPageSize: Int = 50

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedSales: StateFlow<List<SaleEntity>> = _salesPage.flatMapLatest { page ->
        repository.getSalesPaged(limit = salesPageSize, offset = page * salesPageSize)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun nextSalesPage() { _salesPage.value += 1 }
    fun prevSalesPage() { if (_salesPage.value > 0) _salesPage.value -= 1 }
    fun resetSalesPage() { _salesPage.value = 0 }

    fun getCustomerSalesPaged(customerId: Long, limit: Int = 20, offset: Int = 0): Flow<List<SaleEntity>> =
        repository.getSalesForCustomerPaged(customerId, limit, offset)

    val salesSearchQuery = MutableStateFlow("")
    val salesStatusFilter = MutableStateFlow("ALL")
    val salesSellerFilter = MutableStateFlow("ALL")
    private val _visibleSalesLimit = MutableStateFlow(80)
    val visibleSalesLimit: StateFlow<Int> = _visibleSalesLimit.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val debouncedSalesCriteria = combine(
        salesSearchQuery.map { it.trim() }.debounce(200L).distinctUntilChanged(),
        salesStatusFilter,
        salesSellerFilter
    ) { q, status, seller -> Triple(q, status, seller) }

    private data class SalesPageQuery(val query: String, val statusFilter: String, val seller: String, val limit: Int)

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredPagedSales: StateFlow<List<SaleEntity>> = combine(debouncedSalesCriteria, _visibleSalesLimit) { (q, s, sel), limit ->
        SalesPageQuery(q, s, sel, limit)
    }.flatMapLatest { p ->
        repository.getSalesFilteredPaged(p.query, p.statusFilter, p.seller, limit = p.limit, offset = 0)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredSalesSummary: StateFlow<SalesLedgerSqlSummary> = debouncedSalesCriteria
        .flatMapLatest { (q, status, seller) -> repository.getSalesFilteredSummary(q, status, seller) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), SalesLedgerSqlSummary(0, 0L, 0L, 0L))

    val salesCountBySeller: StateFlow<List<SellerSalesCountRecord>> = repository.getSalesCountBySeller()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSalesFilters(query: String? = null, statusFilter: String? = null, seller: String? = null) {
        if (query != null) salesSearchQuery.value = query
        if (statusFilter != null) salesStatusFilter.value = statusFilter
        if (seller != null) salesSellerFilter.value = seller
        _visibleSalesLimit.value = 80
    }

    fun loadMoreSales(step: Int = 80) { _visibleSalesLimit.value += step }
    fun resetSalesWindow() { _visibleSalesLimit.value = 80 }

    val purchasesSearchQuery = MutableStateFlow("")
    val purchasesStatusFilter = MutableStateFlow("ALL")
    private val _visiblePurchasesLimit = MutableStateFlow(80)
    val visiblePurchasesLimit: StateFlow<Int> = _visiblePurchasesLimit.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val debouncedPurchasesCriteria = combine(
        purchasesSearchQuery.map { it.trim() }.debounce(200L).distinctUntilChanged(),
        purchasesStatusFilter
    ) { q, status -> q to status }

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredPagedPurchases: StateFlow<List<PurchaseEntity>> = combine(debouncedPurchasesCriteria, _visiblePurchasesLimit) { (q, s), limit ->
        Triple(q, s, limit)
    }.flatMapLatest { (q, status, limit) ->
        repository.getPurchasesFilteredPaged(q, status, limit = limit, offset = 0)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredPurchasesSummary: StateFlow<PurchaseLedgerSqlSummary> = debouncedPurchasesCriteria
        .flatMapLatest { (q, status) -> repository.getPurchasesFilteredSummary(q, status) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), PurchaseLedgerSqlSummary(0, 0L, 0L, 0L))

    fun setPurchasesFilters(query: String? = null, statusFilter: String? = null) {
        if (query != null) purchasesSearchQuery.value = query
        if (statusFilter != null) purchasesStatusFilter.value = statusFilter
        _visiblePurchasesLimit.value = 80
    }

    fun loadMorePurchases(step: Int = 80) { _visiblePurchasesLimit.value += step }
    fun resetPurchasesWindow() { _visiblePurchasesLimit.value = 80 }

    fun clearOperationError() { _latestOperationError.value = null }
    fun clearOperationSuccess() { _latestOperationSuccess.value = null }
    fun reportExternalError(error: PosOperationError) { _latestOperationError.value = error }

    private fun runSalesOperation(
        operationType: OperationType,
        lang: String = "AR",
        successMessage: String = "",
        beepOnSuccess: Boolean = false,
        onResult: (PosOperationResult<Unit>) -> Unit = {},
        block: suspend () -> Unit
    ) {
        scope.launch {
            try {
                block()
                if (beepOnSuccess) onBeep()
                _latestOperationError.value = null
                if (successMessage.isNotBlank()) _latestOperationSuccess.value = successMessage
                onResult(PosOperationResult.Success(Unit, successMessage))
            } catch (e: Exception) {
                val mapped = PosErrorUiTranslator.fromThrowable(e, operationType, lang)
                Log.e(TAG, "Operation $operationType failed: ${mapped.message}", e)
                _latestOperationError.value = mapped
                onResult(PosOperationResult.Failure(mapped))
            }
        }
    }

    fun recordSale(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        currentUser: UserEntity? = null,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {},
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val okMsg = Bilingual.tr(lang, "تم تسجيل فاتورة البيع بنجاح", "Facture de vente enregistrée", "Sale invoice recorded")
        runSalesOperation(OperationType.SALE_CREATE, lang, okMsg, true, { res ->
            onOperationResult(res)
            when (res) {
                is PosOperationResult.Success -> onResult(true, okMsg)
                is PosOperationResult.Failure -> onResult(false, res.error.message)
            }
        }) {
            if (items.isEmpty()) throw IllegalArgumentException(PosErrorUiTranslator.defaultMessage(OperationErrorCode.EMPTY_ITEMS, lang))
            repository.recordSaleInvoice(sale, items, currentUser)
        }
    }

    fun voidSale(
        saleId: Long,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {},
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val okMsg = Bilingual.tr(lang, "تم إلغاء الفاتورة بنجاح", "Facture annulée", "Sale invoice voided")
        runSalesOperation(OperationType.SALE_VOID, lang, okMsg, true, { res ->
            onOperationResult(res)
            when (res) {
                is PosOperationResult.Success -> onResult(true, okMsg)
                is PosOperationResult.Failure -> onResult(false, res.error.message)
            }
        }) {
            repository.voidSaleInvoice(saleId)
        }
    }

    fun deleteSaleInvoice(
        saleId: Long,
        currentUser: UserEntity? = null,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {},
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val okMsg = Bilingual.tr(lang, "تم حذف الفاتورة واسترجاع المخزون بنجاح", "Facture supprimée avec succès", "Invoice deleted successfully")
        runSalesOperation(OperationType.SALE_DELETE, lang, okMsg, false, { res ->
            onOperationResult(res)
            when (res) {
                is PosOperationResult.Success -> onResult(true, okMsg)
                is PosOperationResult.Failure -> onResult(false, res.error.message)
            }
        }) {
            repository.deleteSaleInvoice(saleId, currentUser)
        }
    }

    fun updateSale(sale: SaleEntity, items: List<SaleItemEntity>, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) {
        val okMsg = Bilingual.tr(lang, "تم تحديث وحفظ فاتورة الزبون بنجاح", "Facture client mise à jour avec succès", "Sale invoice updated")
        runSalesOperation(OperationType.SALE_UPDATE, lang, okMsg, true, onOperationResult) { repository.updateSaleInvoice(sale, items, recordPaymentDelta = true) }
    }

    fun updateSaleDate(saleId: Long, newTimestamp: Long, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) {
        val okMsg = Bilingual.tr(lang, "تم تحديث تاريخ الفاتورة بنجاح", "Date mise à jour", "Invoice date updated")
        runSalesOperation(OperationType.SALE_DATE_UPDATE, lang, okMsg, true, onOperationResult) { repository.updateSaleTimestamp(saleId, newTimestamp) }
    }

    fun updateSaleInvoiceNumber(
        saleId: Long,
        newInvoiceNumber: String,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {},
        onResult: (Boolean) -> Unit = {}
    ) {
        scope.launch {
            try {
                val ok = repository.updateSaleInvoiceNumber(saleId, newInvoiceNumber)
                if (ok) {
                    onBeep()
                    _latestOperationError.value = null
                    val msg = Bilingual.tr(lang, "تم تعديل رقم الفاتورة بنجاح", "Numéro de facture modifié avec succès", "Invoice number updated")
                    _latestOperationSuccess.value = msg
                    onOperationResult(PosOperationResult.Success(Unit, msg))
                    onResult(true)
                } else {
                    val failure = PosErrorUiTranslator.failure(OperationErrorCode.DUPLICATE_INVOICE_NUMBER, OperationType.SALE_NUMBER_UPDATE, lang)
                    _latestOperationError.value = failure.error
                    onOperationResult(failure)
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateSaleInvoiceNumber failed", e)
                val err = PosErrorUiTranslator.fromThrowable(e, OperationType.SALE_NUMBER_UPDATE, lang)
                _latestOperationError.value = err
                onOperationResult(PosOperationResult.Failure(err))
                onResult(false)
            }
        }
    }

    fun updateSaleCustomer(saleId: Long, newCustomerId: Long, newCustomerName: String, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) {
        val okMsg = Bilingual.tr(lang, "تم تبديل اسم الزبون بنجاح", "Client mis à jour avec succès", "Customer updated")
        runSalesOperation(OperationType.SALE_CUSTOMER_UPDATE, lang, okMsg, true, onOperationResult) {
            repository.updateSaleCustomer(saleId, newCustomerId, newCustomerName)
        }
    }

    suspend fun getSaleItems(saleId: Long): List<SaleItemEntity> = repository.getSaleItems(saleId)
    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItemEntity> = repository.getPurchaseItems(purchaseId)

    fun createOrder(
        order: OrderEntity,
        items: List<OrderItemEntity>,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<OrderEntity>) -> Unit = {},
        onComplete: (OrderEntity) -> Unit = {}
    ) {
        scope.launch {
            try {
                if (items.isEmpty()) throw IllegalArgumentException(PosErrorUiTranslator.defaultMessage(OperationErrorCode.EMPTY_ITEMS, lang))
                val created = repository.createOrder(order, items)
                onBeep()
                _latestOperationError.value = null
                onOperationResult(PosOperationResult.Success(created))
                onComplete(created)
            } catch (e: Exception) {
                Log.e(TAG, "createOrder failed", e)
                val err = PosErrorUiTranslator.fromThrowable(e, OperationType.ORDER_CREATE, lang)
                _latestOperationError.value = err
                onOperationResult(PosOperationResult.Failure(err))
            }
        }
    }

    fun voidOrder(orderId: Long, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) {
        runSalesOperation(OperationType.ORDER_VOID, lang, "", false, onOperationResult) { repository.voidOrder(orderId) }
    }

    suspend fun getReturnedQuantitiesForSale(saleId: Long): Map<Long, Double> =
        repository.getReturnedQuantitiesForSale(saleId)

    fun recordCustomerReturn(
        saleId: Long, productId: Long, productName: String, quantity: Double,
        refundAmount: Double? = null, isCreditRefund: Boolean, customerId: Long,
        lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val okMsg = Bilingual.tr(lang, "تم تسجيل المرتجع واسترجاع المخزون بنجاح", "Retour enregistré avec succès", "Return recorded")
        runSalesOperation(OperationType.SALE_RETURN, lang, okMsg, true, onOperationResult) {
            if (quantity <= 0.0 || (refundAmount != null && refundAmount < 0.0)) {
                throw IllegalArgumentException(PosErrorUiTranslator.defaultMessage(OperationErrorCode.INVALID_AMOUNT, lang))
            }
            repository.recordCustomerReturn(saleId, productId, productName, quantity, refundAmount, isCreditRefund, customerId)
        }
    }

    fun settleSaleInvoice(
        sale: SaleEntity,
        amount: Double? = null,
        cashierName: String = "",
        lang: String = "AR",
        paymentMethod: String = "CASH",
        notes: String = "",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val isPartial = amount != null && amount.cents < sale.remainingDueCents
        val okMsg = if (isPartial) {
            Bilingual.tr(lang, "تم تسجيل الدفعة الجزئية في كشف حساب الزبون بنجاح", "Paiement partiel enregistré dans le relevé client", "Partial payment recorded in customer statement")
        } else {
            Bilingual.tr(lang, "تم تسديد وتأكيد الفاتورة بنجاح", "Facture validée avec succès", "Invoice settled successfully")
        }
        runSalesOperation(OperationType.SALE_SETTLE, lang, okMsg, true, onOperationResult) {
            val payCents = amount?.cents ?: sale.remainingDueCents
            if (payCents <= 0L) throw IllegalArgumentException(Bilingual.tr(lang, "الفاتورة مسددة بالفعل أو المبلغ غير صالح", "Facture déjà réglée ou montant invalide", "Invoice already settled or invalid amount"))
            repository.settleSaleInvoice(
                saleId = sale.id,
                amountCents = payCents,
                paymentMethod = paymentMethod,
                notes = notes,
                cashierName = cashierName
            )
        }
    }

    fun markSaleAsDebt(sale: SaleEntity, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) {
        val okMsg = Bilingual.tr(lang, "تم تحويل الفاتورة إلى دين بنجاح", "Facture marquée à crédit", "Invoice marked as credit")
        runSalesOperation(OperationType.SALE_MARK_DEBT, lang, okMsg, true, onOperationResult) {
            repository.updateSaleInvoice(sale.copy(paidAmount = 0L, paymentMethod = "DEBT"), repository.getSaleItems(sale.id))
        }
    }

    // Delegated Purchase Operations
    fun deletePurchase(purchaseId: Long, currentUser: UserEntity? = null, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}, onError: (Throwable) -> Unit = {}) =
        purchaseDelegate.deletePurchase(purchaseId, currentUser, lang, onOperationResult, onError)
    fun voidPurchase(purchase: PurchaseEntity, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) =
        purchaseDelegate.voidPurchase(purchase, lang, onOperationResult)
    fun updatePurchase(purchase: PurchaseEntity, items: List<PurchaseItemEntity>, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) =
        purchaseDelegate.updatePurchase(purchase, items, lang, onOperationResult)
    fun updatePurchaseDate(purchaseId: Long, newTimestamp: Long, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) =
        purchaseDelegate.updatePurchaseDate(purchaseId, newTimestamp, lang, onOperationResult)
    fun updatePurchaseInvoiceNumber(purchaseId: Long, newInvoiceNumber: String, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}, onResult: (Boolean) -> Unit = {}) =
        purchaseDelegate.updatePurchaseInvoiceNumber(purchaseId, newInvoiceNumber, lang, onOperationResult, onResult)
    fun updatePurchaseSupplier(purchaseId: Long, newSupplierId: Long, newSupplierName: String, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) =
        purchaseDelegate.updatePurchaseSupplier(purchaseId, newSupplierId, newSupplierName, lang, onOperationResult)
    fun recordSupplierReturn(purchaseId: Long, productId: Long, productName: String, quantity: Double, returnAmount: Double, supplierId: Long, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) =
        purchaseDelegate.recordSupplierReturn(purchaseId, productId, productName, quantity, returnAmount, supplierId, lang, onOperationResult)
    fun settlePurchaseInvoice(
        purchase: PurchaseEntity,
        amount: Double? = null,
        cashierName: String = "",
        lang: String = "AR",
        paymentMethod: String = "CASH",
        notes: String = "",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) = purchaseDelegate.settlePurchaseInvoice(purchase, amount, cashierName, lang, paymentMethod, notes, onOperationResult)
    fun markPurchaseAsDebt(purchase: PurchaseEntity, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) =
        purchaseDelegate.markPurchaseAsDebt(purchase, lang, onOperationResult)
    fun recordSupplierInvoiceBulk(purchase: PurchaseEntity, items: List<PurchaseItemEntity>, currentUser: UserEntity? = null, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) =
        purchaseDelegate.recordSupplierInvoiceBulk(purchase, items, currentUser, lang, onOperationResult)
    fun recordSupplierInvoice(supplierId: Long, supplierName: String, productId: Long, productName: String, qty: Double, cost: Double, paid: Double, currentUser: UserEntity? = null, lang: String = "AR", onOperationResult: (PosOperationResult<Unit>) -> Unit = {}) =
        purchaseDelegate.recordSupplierInvoice(supplierId, supplierName, productId, productName, qty, cost, paid, currentUser, lang, onOperationResult)
}
