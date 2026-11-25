package com.example.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.util.GeoFenceResult
import com.example.util.GeoLocationHelper
import com.example.util.cents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Modular ViewModel for Customers, Suppliers, Debt Ledgers, Payments, and Expenses.
 */
class DebtLedgerViewModel(
    private val repository: PosRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    val customers: StateFlow<List<CustomerEntity>> = repository.customers
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val suppliers: StateFlow<List<SupplierEntity>> = repository.suppliers
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val customerPayments: StateFlow<List<CustomerPaymentEntity>> = repository.customerPayments
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val supplierPayments: StateFlow<List<SupplierPaymentEntity>> = repository.supplierPayments
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val expenses: StateFlow<List<ExpenseEntity>> = repository.expenses
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerSearchQuery = MutableStateFlow("")
    private val _visibleCustomerLimit = MutableStateFlow(60)
    val visibleCustomerLimit: StateFlow<Int> = _visibleCustomerLimit.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val debouncedCustomerQuery: Flow<String> = customerSearchQuery
        .map { it.trim() }
        .debounce(200L)
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredCustomersCount: StateFlow<Int> = debouncedCustomerQuery
        .flatMapLatest { q -> repository.getCustomersFilteredCount(q) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredPagedCustomers: StateFlow<List<CustomerEntity>> = combine(
        debouncedCustomerQuery,
        _visibleCustomerLimit
    ) { q, limit -> q to limit }
        .flatMapLatest { (q, limit) ->
            repository.getCustomersFilteredPaged(q, limit = limit, offset = 0)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setCustomerSearchQuery(query: String) {
        customerSearchQuery.value = query
        _visibleCustomerLimit.value = 60
    }

    fun loadMoreCustomers(step: Int = 60) {
        _visibleCustomerLimit.value += step
    }

    private val _visibleCustomerPaymentsLimit = MutableStateFlow(80)
    private val _visibleSupplierPaymentsLimit = MutableStateFlow(80)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedCustomerPayments: StateFlow<List<CustomerPaymentEntity>> = _visibleCustomerPaymentsLimit
        .flatMapLatest { limit -> repository.getCustomerPaymentsPaged(limit, 0) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedSupplierPayments: StateFlow<List<SupplierPaymentEntity>> = _visibleSupplierPaymentsLimit
        .flatMapLatest { limit -> repository.getSupplierPaymentsPaged(limit, 0) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadMoreCustomerPayments(step: Int = 80) {
        _visibleCustomerPaymentsLimit.value += step
    }

    fun loadMoreSupplierPayments(step: Int = 80) {
        _visibleSupplierPaymentsLimit.value += step
    }

    fun payCustomerDebt(
        customerId: Long,
        amount: Double,
        paymentMethod: String = "CASH",
        notes: String = "",
        cashierName: String = "",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        scope.launch {
            val (success, msg) = executeCustomerDebtPayment(customerId, amount, paymentMethod, notes, cashierName)
            onComplete(success, msg)
        }
    }

    suspend fun executeCustomerDebtPayment(
        customerId: Long,
        amount: Double,
        paymentMethod: String = "CASH",
        notes: String = "",
        cashierName: String = ""
    ): Pair<Boolean, String> {
        return try {
            if (amount <= 0.0) {
                Pair(false, "المبلغ يجب أن يكون أكبر من الصفر")
            } else {
                repository.recordCustomerPayment(
                    customerId = customerId,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    notes = notes,
                    cashierName = cashierName
                )
                Pair(true, "تم تسجيل دفعة العميل بنجاح")
            }
        } catch (e: Exception) {
            Log.e("DebtLedgerViewModel", "Failed customer debt payment", e)
            Pair(false, e.localizedMessage ?: "حدث خطأ أثناء تسجيل الدفعة")
        }
    }

    fun paySupplierDebt(
        supplierId: Long,
        amount: Double,
        paymentMethod: String = "CASH",
        notes: String = "",
        cashierName: String = "",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        scope.launch {
            val (success, msg) = executeSupplierDebtPayment(supplierId, amount, paymentMethod, notes, cashierName)
            onComplete(success, msg)
        }
    }

    suspend fun executeSupplierDebtPayment(
        supplierId: Long,
        amount: Double,
        paymentMethod: String = "CASH",
        notes: String = "",
        cashierName: String = ""
    ): Pair<Boolean, String> {
        return try {
            if (amount <= 0.0) {
                Pair(false, "المبلغ يجب أن يكون أكبر من الصفر")
            } else {
                repository.recordSupplierPayment(
                    supplierId = supplierId,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    notes = notes,
                    cashierName = cashierName
                )
                Pair(true, "تم تسجيل دفعة المورد بنجاح")
            }
        } catch (e: Exception) {
            Log.e("DebtLedgerViewModel", "Failed supplier debt payment", e)
            Pair(false, e.localizedMessage ?: "حدث خطأ أثناء تسجيل الدفعة")
        }
    }

    fun recordCustomerPayment(
        customerId: Long,
        amount: Double,
        paymentMethod: String = "CASH",
        notes: String = "",
        cashierName: String = "",
        onComplete: (CustomerPaymentEntity?) -> Unit = {}
    ) {
        scope.launch {
            onComplete(repository.recordCustomerPayment(customerId, amount, paymentMethod, notes, cashierName))
        }
    }

    fun deleteCustomerPayment(payment: CustomerPaymentEntity) {
        scope.launch { repository.deleteCustomerPayment(payment) }
    }

    fun updateCustomerPayment(
        paymentId: Long,
        newAmount: Double,
        newPaymentMethod: String = "CASH",
        notes: String = "",
        onComplete: () -> Unit = {}
    ) {
        scope.launch {
            repository.updateCustomerPayment(paymentId, newAmount, newPaymentMethod, notes)
            onComplete()
        }
    }

    fun recordSupplierPayment(
        supplierId: Long,
        amount: Double,
        paymentMethod: String = "CASH",
        notes: String = "",
        cashierName: String = "",
        onComplete: (SupplierPaymentEntity?) -> Unit = {}
    ) {
        scope.launch {
            onComplete(repository.recordSupplierPayment(supplierId, amount, paymentMethod, notes, cashierName))
        }
    }

    fun deleteSupplierPayment(payment: SupplierPaymentEntity) {
        scope.launch { repository.deleteSupplierPayment(payment) }
    }

    fun updateSupplierPayment(
        paymentId: Long,
        newAmount: Double,
        newPaymentMethod: String = "CASH",
        notes: String = "",
        onComplete: () -> Unit = {}
    ) {
        scope.launch {
            repository.updateSupplierPayment(paymentId, newAmount, newPaymentMethod, notes)
            onComplete()
        }
    }

    fun saveCustomer(
        id: Long = 0,
        name: String,
        phone: String,
        address: String,
        balance: Double,
        latitude: Double? = null,
        longitude: Double? = null,
        geoRadiusMeters: Double = 150.0,
        isGeoLocationRequired: Boolean = false,
        customerNumber: Long = 0L,
        barcode: String = "",
        currentUser: UserEntity? = null,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        if (id != 0L && (currentUser == null || !currentUser.hasEditCustomersAccess())) {
            onResult?.invoke(false, "عذراً، ليس لديك صلاحية تعديل بيانات العملاء بعد إنشائهم")
            return
        }
        scope.launch {
            repository.saveCustomer(
                CustomerEntity(
                    id = id,
                    name = name,
                    phone = phone,
                    address = address,
                    balance = balance.cents,
                    latitude = latitude,
                    longitude = longitude,
                    geoRadiusMeters = geoRadiusMeters,
                    isGeoLocationRequired = isGeoLocationRequired,
                    customerNumber = customerNumber,
                    barcode = barcode
                )
            )
            onResult?.invoke(true, if (id == 0L) "تمت إضافة العميل بنجاح" else "تم حفظ تعديلات العميل بنجاح")
        }
    }

    fun saveCustomer(customer: CustomerEntity, currentUser: UserEntity? = null) {
        if (customer.id != 0L && (currentUser == null || !currentUser.hasEditCustomersAccess())) return
        scope.launch { repository.saveCustomer(customer) }
    }

    fun deleteCustomer(customer: CustomerEntity, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            val (ok, msg) = repository.deleteCustomer(customer.id)
            onResult(ok, msg)
        }
    }

    fun deleteCustomersBatch(customerIds: List<Long>, onResult: (deletedCount: Int, warnings: List<String>) -> Unit) {
        scope.launch {
            val (count, warnings) = repository.deleteCustomersBatch(customerIds)
            onResult(count, warnings)
        }
    }

    suspend fun findCustomerByBarcode(barcode: String): CustomerEntity? = repository.getCustomerByBarcode(barcode)

    suspend fun verifyCustomerLocation(
        context: Context,
        clientId: Long,
        currentUser: UserEntity?,
        lang: String
    ): GeoFenceResult {
        val customer = repository.getCustomerById(clientId) ?: return GeoFenceResult(isAllowed = true)
        return GeoLocationHelper.verifyCustomerLocationForInvoice(context, customer, currentUser, lang)
    }

    fun saveSupplier(id: Long = 0, name: String, phone: String, email: String, address: String, debt: Double) {
        scope.launch {
            repository.saveSupplier(
                SupplierEntity(id = id, name = name, phone = phone, email = email, address = address, debtBalance = debt.cents)
            )
        }
    }

    fun saveSupplier(supplier: SupplierEntity) {
        scope.launch { repository.saveSupplier(supplier) }
    }

    fun deleteSupplier(supplier: SupplierEntity, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            val (ok, msg) = repository.deleteSupplier(supplier.id)
            onResult(ok, msg)
        }
    }

    fun saveExpense(title: String, amount: Double, category: String) {
        scope.launch {
            repository.saveExpense(ExpenseEntity(title = title, amount = amount.cents, timestamp = System.currentTimeMillis(), category = category))
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        scope.launch { repository.deleteExpense(expense) }
    }
}
