package com.example.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.FinancialCalculator
import com.example.domain.PosOperationError
import com.example.printer.ThermalPrintProfile
import com.example.util.moneyDouble
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Modular ViewModel for Cart State, Persistent Cart Recovery, Suspended Sales,
 * Barcode Scanning, and delegating Checkout Execution (< 500 lines).
 */
class CartViewModel(
    private val repository: PosRepository,
    externalScope: CoroutineScope? = null,
    private val onBeep: () -> Unit = {},
    private val onOperationError: (PosOperationError) -> Unit = {}
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope
    private val checkoutDelegate = CheckoutExecutionDelegate(repository, scope, onBeep)

    private val _latestCheckoutError = MutableStateFlow<PosOperationError?>(null)
    val latestCheckoutError: StateFlow<PosOperationError?> = _latestCheckoutError.asStateFlow()

    fun clearCheckoutError() {
        _latestCheckoutError.value = null
    }

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _suspendedSales = MutableStateFlow<List<SuspendedSale>>(emptyList())
    val suspendedSales: StateFlow<List<SuspendedSale>> = _suspendedSales.asStateFlow()

    val searchQuery = MutableStateFlow("")
    val selectedCategoryIdForFilter = MutableStateFlow<Long?>(null)

    val globalDiscountType = MutableStateFlow("VALUE")
    val globalDiscountValue = MutableStateFlow(0.0)
    val selectedPaymentMethod = MutableStateFlow("CASH")
    val receivedCash = MutableStateFlow(0.0)
    val selectedPartnerClientId = MutableStateFlow<Long>(1L)

    private val _printedPurchase = MutableStateFlow<PurchaseEntity?>(null)
    val printedPurchase: StateFlow<PurchaseEntity?> = _printedPurchase.asStateFlow()

    private val _printedPurchaseItems = MutableStateFlow<List<PurchaseItemEntity>>(emptyList())
    val printedPurchaseItems: StateFlow<List<PurchaseItemEntity>> = _printedPurchaseItems.asStateFlow()

    private val _printedReceipt = MutableStateFlow<SaleEntity?>(null)
    val printedReceipt: StateFlow<SaleEntity?> = _printedReceipt.asStateFlow()

    private val _printedReceiptItems = MutableStateFlow<List<SaleItemEntity>>(emptyList())
    val printedReceiptItems: StateFlow<List<SaleItemEntity>> = _printedReceiptItems.asStateFlow()

    private val _scanFeedbackMessage = MutableStateFlow<String?>(null)
    val scanFeedbackMessage: StateFlow<String?> = _scanFeedbackMessage.asStateFlow()

    private val _unrecognizedScannedBarcode = MutableStateFlow<String?>(null)
    val unrecognizedScannedBarcode: StateFlow<String?> = _unrecognizedScannedBarcode.asStateFlow()

    val persistentCartItems: StateFlow<List<CartItemEntity>> = repository.activeCartItems
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSubtotalCents: Long get() = _cart.value.sumOf { it.totalAfterDiscountCents }
    val currentSubtotal: Double get() = currentSubtotalCents.moneyDouble

    val subtotalCents: StateFlow<Long> = _cart.map { items ->
        items.sumOf { it.totalAfterDiscountCents }
    }.stateIn(scope, SharingStarted.Eagerly, 0L)

    val subtotal: StateFlow<Double> = subtotalCents.map { it.moneyDouble }
        .stateIn(scope, SharingStarted.Eagerly, 0.0)

    val itemCount: StateFlow<Int> = _cart.map { items ->
        items.sumOf { it.quantity.toInt() }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    val cartTotalBeforeGlobalDiscountCents: StateFlow<Long> = _cart.map { list ->
        list.sumOf { it.totalAfterDiscountCents }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), 0L)

    val cartTotalBeforeGlobalDiscount: StateFlow<Double> = cartTotalBeforeGlobalDiscountCents.map { it.moneyDouble }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cartTotalAfterGlobalDiscountCents: StateFlow<Long> = combine(
        cartTotalBeforeGlobalDiscountCents,
        globalDiscountType,
        globalDiscountValue
    ) { baseCents, type, value ->
        FinancialCalculator.computeNetAfterDiscountCents(baseCents, type, value)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), 0L)

    val cartTotalAfterGlobalDiscount: StateFlow<Double> = cartTotalAfterGlobalDiscountCents.map { it.moneyDouble }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun updateSearchQuery(query: String) { searchQuery.value = query }
    fun selectCategoryFilter(catId: Long?) { selectedCategoryIdForFilter.value = catId }

    suspend fun restorePersistentCartIfEmpty() {
        val savedCartItems = repository.getCartItemsList("ACTIVE")
        if (savedCartItems.isNotEmpty() && _cart.value.isEmpty()) {
            val restored = mutableListOf<CartItem>()
            for (ci in savedCartItems) {
                val prod = repository.getProductById(ci.productId)
                if (prod != null) {
                    restored.add(
                        CartItem(
                            product = prod,
                            quantity = ci.quantity,
                            customPrice = ci.customPriceDouble,
                            lineDiscount = ci.lineDiscountDouble
                        )
                    )
                }
            }
            if (restored.isNotEmpty()) _cart.value = restored
        }
    }

    fun addToCart(product: ProductEntity, quantity: Double = 1.0, customPrice: Double? = null) {
        addOrUpdateCartItem(
            product = product,
            quantity = quantity,
            customPrice = customPrice,
            replaceExisting = false
        )
    }

    fun addOrUpdateCartItem(
        product: ProductEntity,
        quantity: Double = 1.0,
        customPrice: Double? = null,
        replaceExisting: Boolean = false
    ) {
        if (quantity == 0.0) {
            removeFromCart(product.id)
            return
        }
        val existing = _cart.value.find { it.product.id == product.id }
        if (existing != null) {
            val targetQty = if (replaceExisting) quantity else existing.quantity + quantity
            val targetPrice = if (replaceExisting) customPrice else (customPrice ?: existing.customPrice)
            if (targetQty == 0.0) {
                removeFromCart(product.id)
                return
            }
            _cart.value = _cart.value.map {
                if (it.product.id == product.id) {
                    it.copy(product = product, quantity = targetQty, customPrice = targetPrice)
                } else it
            }
            scope.launch {
                val dbItem = repository.getCartItemByProduct(product.id, "ACTIVE")
                val effectivePrice = targetPrice ?: product.sellPriceDouble
                if (dbItem != null) {
                    repository.updatePersistentCartQuantity(dbItem.id, targetQty, effectivePrice, existing.lineDiscount)
                } else {
                    repository.addToPersistentCart(product, quantity = targetQty, customPrice = targetPrice, lineDiscount = existing.lineDiscount)
                }
            }
            onBeep()
        } else {
            _cart.value = _cart.value + CartItem(product = product, quantity = quantity, customPrice = customPrice)
            scope.launch { repository.addToPersistentCart(product, quantity = quantity, customPrice = customPrice) }
            onBeep()
        }
    }

    fun addReturnToCart(product: ProductEntity, quantity: Double = -1.0) {
        val existing = _cart.value.find { it.product.id == product.id }
        val targetQty = if (quantity > 0) -quantity else quantity
        if (existing != null) {
            updateCartQuantity(product.id, existing.quantity + targetQty)
        } else {
            _cart.value = _cart.value + CartItem(product = product, quantity = targetQty)
            scope.launch { repository.addToPersistentCart(product, quantity = targetQty) }
            onBeep()
        }
    }

    fun toggleCartItemReturn(productId: Long) {
        val existing = _cart.value.find { it.product.id == productId } ?: return
        updateCartQuantity(productId, -existing.quantity)
    }

    fun decrementCartQuantity(productId: Long) {
        val existing = _cart.value.find { it.product.id == productId } ?: return
        updateCartQuantity(productId, existing.quantity - 1.0)
    }

    fun updateQuantity(productId: Long, quantity: Double) = updateCartQuantity(productId, quantity)

    fun updateCartQuantity(productId: Long, qty: Double) {
        if (qty == 0.0) {
            removeFromCart(productId)
            return
        }
        _cart.value = _cart.value.map { if (it.product.id == productId) it.copy(quantity = qty) else it }
        scope.launch {
            val item = _cart.value.find { it.product.id == productId }
            if (item != null) {
                val dbItem = repository.getCartItemByProduct(productId, "ACTIVE")
                if (dbItem != null) {
                    repository.updatePersistentCartQuantity(dbItem.id, qty, item.unitPrice, item.lineDiscount)
                } else {
                    repository.addToPersistentCart(item.product, quantity = qty, customPrice = item.customPrice, lineDiscount = item.lineDiscount)
                }
            }
        }
    }

    fun updateCartPrice(productId: Long, price: Double) {
        _cart.value = _cart.value.map { if (it.product.id == productId) it.copy(customPrice = price) else it }
        scope.launch {
            val item = _cart.value.find { it.product.id == productId }
            if (item != null) {
                val dbItem = repository.getCartItemByProduct(productId, "ACTIVE")
                if (dbItem != null) repository.updatePersistentCartQuantity(dbItem.id, item.quantity, price, item.lineDiscount)
            }
        }
    }

    fun applyLineDiscount(productId: Long, discountValue: Double) =
        updateCartLineDiscount(productId, discountValue.coerceAtLeast(0.0))

    fun updateCartLineDiscount(productId: Long, discount: Double) {
        _cart.value = _cart.value.map { if (it.product.id == productId) it.copy(lineDiscount = discount) else it }
        scope.launch {
            val item = _cart.value.find { it.product.id == productId }
            if (item != null) {
                val dbItem = repository.getCartItemByProduct(productId, "ACTIVE")
                if (dbItem != null) repository.updatePersistentCartQuantity(dbItem.id, item.quantity, item.unitPrice, discount)
            }
        }
    }

    fun removeFromCart(productId: Long) {
        _cart.value = _cart.value.filter { it.product.id != productId }
        scope.launch { repository.deleteCartItemByProduct(productId, "ACTIVE") }
    }

    fun clearCart() {
        _cart.value = emptyList()
        globalDiscountValue.value = 0.0
        receivedCash.value = 0.0
        scope.launch { repository.clearPersistentCart("ACTIVE") }
    }

    fun clearUnrecognizedBarcode() { _unrecognizedScannedBarcode.value = null }

    fun onBarcodeScanned(barcode: String, onResult: (found: Boolean, name: String) -> Unit = { _, _ -> }) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return
        scope.launch {
            val prod = repository.getProductByBarcode(cleanBarcode)
            if (prod != null) {
                addToCart(prod)
                onBeep()
                _scanFeedbackMessage.value = "تمت إضافة: ${prod.name} للسلة ✓"
                _unrecognizedScannedBarcode.value = null
                onResult(true, prod.name)
            } else {
                _scanFeedbackMessage.value = "الباركود غير مسجل: $cleanBarcode"
                _unrecognizedScannedBarcode.value = cleanBarcode
                onResult(false, "")
            }
            delay(2500)
            if (_scanFeedbackMessage.value?.contains(cleanBarcode) == true ||
                _scanFeedbackMessage.value?.startsWith("تمت إضافة") == true
            ) {
                _scanFeedbackMessage.value = null
            }
        }
    }

    fun suspendCurrentSale(description: String = "") {
        val currentItems = _cart.value
        if (currentItems.isEmpty()) return
        val desc = description.ifBlank {
            "فاتورة معلقة تحتوي على ${currentItems.size} أصناف / ${SimpleDateFormat("HH:mm", Locale.US).format(Date())}"
        }
        _suspendedSales.value = _suspendedSales.value + SuspendedSale(System.currentTimeMillis(), currentItems, desc)
        clearCart()
        onBeep()
    }

    fun recallSuspendedSale(index: Int) {
        val list = _suspendedSales.value.toMutableList()
        if (index in list.indices) {
            val recalled = list.removeAt(index)
            _cart.value = recalled.items
            _suspendedSales.value = list
            onBeep()
        }
    }

    fun restoreSuspendedSale(timestamp: Long) {
        val suspended = _suspendedSales.value.find { it.timestamp == timestamp } ?: return
        _cart.value = suspended.items
        _suspendedSales.value = _suspendedSales.value.filterNot { it.timestamp == timestamp }
    }

    fun showReceiptDetails(sale: SaleEntity) {
        scope.launch {
            _printedReceipt.value = sale
            _printedReceiptItems.value = repository.getSaleItems(sale.id)
        }
    }

    fun showPurchaseDetails(purchase: PurchaseEntity) {
        scope.launch {
            _printedPurchase.value = purchase
            _printedPurchaseItems.value = repository.getPurchaseItems(purchase.id)
        }
    }

    fun dismissReceipt() {
        _printedReceipt.value = null
        _printedReceiptItems.value = emptyList()
    }

    fun dismissPurchase() {
        _printedPurchase.value = null
        _printedPurchaseItems.value = emptyList()
    }

    fun executeCheckout(
        appContext: Context,
        currentUser: UserEntity?,
        language: String,
        settings: GlobalSettingsEntity,
        printerConnectionType: String,
        printerName: String,
        printerMac: String,
        printerIp: String,
        printerPort: Int,
        printerPaperWidth: Int,
        autoPrintReceipt: Boolean,
        uiContext: Context? = null,
        onError: (String) -> Unit = {},
        onComplete: () -> Unit,
        printProfile: ThermalPrintProfile? = null
    ) {
        checkoutDelegate.executeCheckout(
            appContext = appContext,
            currentUser = currentUser,
            language = language,
            settings = settings,
            invoiceItems = _cart.value,
            subtotalCents = currentSubtotalCents,
            discountType = globalDiscountType.value,
            discountValue = globalDiscountValue.value,
            paymentMethod = selectedPaymentMethod.value,
            receivedCash = receivedCash.value,
            selectedClientId = selectedPartnerClientId.value,
            printerConnectionType = printerConnectionType,
            printerName = printerName,
            printerMac = printerMac,
            printerIp = printerIp,
            printerPort = printerPort,
            printerPaperWidth = printerPaperWidth,
            autoPrintReceipt = autoPrintReceipt,
            uiContext = uiContext,
            printProfile = printProfile,
            onSavedReceipt = { sale, items ->
                _latestCheckoutError.value = null
                _printedReceipt.value = sale
                _printedReceiptItems.value = items
            },
            onClearCart = ::clearCart,
            onFailure = { err ->
                _latestCheckoutError.value = err
                onOperationError(err)
                onError(err.message)
            },
            onComplete = onComplete
        )
    }

    fun printCheckoutTransactionSummary(
        context: Context,
        currentUser: UserEntity?,
        language: String,
        settings: GlobalSettingsEntity,
        subtotal: Double,
        discountAmount: Double,
        netTotal: Double,
        paymentMethod: String,
        receivedAmount: Double,
        changeDue: Double,
        printerConnectionType: String,
        printerName: String,
        printerMac: String,
        printerIp: String,
        printerPort: Int,
        printerPaperWidth: Int,
        isDraftSummary: Boolean = true,
        invoiceNumberOverride: String? = null,
        onResult: (Boolean, String) -> Unit,
        printProfile: ThermalPrintProfile? = null
    ) {
        checkoutDelegate.printCheckoutTransactionSummary(
            context = context,
            currentUser = currentUser,
            language = language,
            settings = settings,
            invoiceItems = _cart.value,
            selectedClientId = selectedPartnerClientId.value,
            subtotal = subtotal,
            discountAmount = discountAmount,
            netTotal = netTotal,
            paymentMethod = paymentMethod,
            receivedAmount = receivedAmount,
            changeDue = changeDue,
            printerConnectionType = printerConnectionType,
            printerName = printerName,
            printerMac = printerMac,
            printerIp = printerIp,
            printerPort = printerPort,
            printerPaperWidth = printerPaperWidth,
            isDraftSummary = isDraftSummary,
            invoiceNumberOverride = invoiceNumberOverride,
            printProfile = printProfile,
            onResult = onResult
        )
    }
}
