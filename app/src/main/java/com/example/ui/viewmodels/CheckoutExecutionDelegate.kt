package com.example.ui.viewmodels

import android.content.Context
import android.util.Log
import com.example.data.*
import com.example.domain.FinancialCalculator
import com.example.domain.OperationErrorCode
import com.example.domain.OperationType
import com.example.domain.PosOperationError
import com.example.printer.BluetoothPrinterConnectionManager
import com.example.printer.BluetoothPrinterService
import com.example.printer.PosThermalPrinterServiceProvider
import com.example.printer.PrinterTransport
import com.example.printer.ThermalPrintProfile
import com.example.printer.ThermalPrinterDevice
import com.example.ui.components.PosErrorUiTranslator
import com.example.ui.theme.Bilingual
import com.example.util.GeoLocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles atomic POS checkout execution, geofence verification, transaction persistence,
 * and thermal receipt printing on behalf of CartViewModel.
 */
internal class CheckoutExecutionDelegate(
    private val repository: PosRepository,
    private val scope: CoroutineScope,
    private val onBeep: () -> Unit
) {
    companion object {
        private const val TAG = "CheckoutExecDelegate"
    }

    fun executeCheckout(
        appContext: Context,
        currentUser: UserEntity?,
        language: String,
        settings: GlobalSettingsEntity,
        invoiceItems: List<CartItem>,
        subtotalCents: Long,
        discountType: String,
        discountValue: Double,
        paymentMethod: String,
        receivedCash: Double,
        selectedClientId: Long,
        printerConnectionType: String,
        printerName: String,
        printerMac: String,
        printerIp: String,
        printerPort: Int,
        printerPaperWidth: Int,
        autoPrintReceipt: Boolean,
        uiContext: Context? = null,
        printProfile: ThermalPrintProfile? = null,
        onSavedReceipt: (SaleEntity, List<SaleItemEntity>) -> Unit,
        onClearCart: () -> Unit,
        onFailure: (PosOperationError) -> Unit,
        onComplete: () -> Unit
    ) {
        scope.launch {
            try {
                if (currentUser == null || !currentUser.hasSellAccess()) {
                    val msg = Bilingual.tr(
                        language,
                        "عذراً، ليس لديك صلاحية إجراء عمليات البيع (canSell)!",
                        "Accès refusé : vous n'avez pas la permission de vendre !",
                        "Access denied: selling permission required!"
                    )
                    onFailure(PosOperationError(OperationErrorCode.PERMISSION_DENIED, msg, OperationType.SALE_CHECKOUT))
                    return@launch
                }

                if (invoiceItems.isEmpty()) {
                    val emptyFailure = PosErrorUiTranslator.failure(
                        code = OperationErrorCode.EMPTY_ITEMS,
                        operationType = OperationType.SALE_CHECKOUT,
                        lang = language,
                        customMessage = Bilingual.tr(
                            language,
                            "سلة المبيعات فارغة، أضف منتجات أولاً",
                            "Le panier est vide, ajoutez des articles d'abord",
                            "Cart is empty, add products first"
                        )
                    )
                    onFailure(emptyFailure.error)
                    return@launch
                }

                val summary = FinancialCalculator.computeCartSummary(
                    subtotalCents = subtotalCents,
                    discountType = discountType,
                    discountValue = discountValue,
                    paymentMethod = paymentMethod,
                    receivedCash = receivedCash
                )

                var clientName = "عميل عام / Walk-In Client"
                val customer = repository.getCustomerById(selectedClientId)
                if (customer != null) {
                    clientName = customer.name
                    if (customer.isGeoLocationRequired && currentUser?.enforceCustomerGeoLocation == true) {
                        val geoCheck = GeoLocationHelper.verifyCustomerLocationForInvoice(
                            context = appContext,
                            customer = customer,
                            currentUser = currentUser,
                            lang = language
                        )
                        if (!geoCheck.isAllowed) {
                            val msg = geoCheck.errorMessage ?: PosErrorUiTranslator.defaultMessage(OperationErrorCode.GEO_FENCE_BLOCKED, language)
                            onFailure(PosOperationError(OperationErrorCode.GEO_FENCE_BLOCKED, msg, OperationType.SALE_CHECKOUT))
                            return@launch
                        }
                    }
                }

                val sale = SaleEntity(
                    invoiceNumber = "",
                    timestamp = System.currentTimeMillis(),
                    cashierName = currentUser?.username?.trim().orEmpty(),
                    customerId = selectedClientId,
                    customerName = clientName,
                    totalAmount = summary.netTotalCents,
                    discountAmount = summary.discountCents,
                    discountType = discountType,
                    paidAmount = summary.paidCents,
                    changeAmount = summary.changeDueCents,
                    paymentMethod = summary.finalPaymentMethod
                )

                val saleItems = invoiceItems.map { item ->
                    SaleItemEntity(
                        saleId = 0,
                        productId = item.product.id,
                        productName = item.product.name,
                        quantity = item.quantity,
                        unitPrice = item.unitPriceCents,
                        discountValue = item.lineDiscountCents,
                        totalLinePrice = item.totalAfterDiscountCents,
                        unitCost = item.product.purchasePrice
                    )
                }

                val savedInvoice = repository.recordSaleInvoice(sale, saleItems, currentUser)
                val txNumber = savedInvoice.invoiceNumber.ifBlank { "TX-${System.currentTimeMillis()}" }
                val transaction = TransactionEntity(
                    transactionNumber = txNumber,
                    timestamp = savedInvoice.timestamp,
                    cashierName = savedInvoice.cashierName,
                    customerId = savedInvoice.customerId,
                    customerName = savedInvoice.customerName,
                    subtotal = summary.netTotalCents + sale.discountAmount,
                    discountAmount = sale.discountAmount,
                    discountType = sale.discountType,
                    taxAmount = 0L,
                    totalAmount = summary.netTotalCents,
                    paidAmount = sale.paidAmount,
                    changeAmount = sale.changeAmount,
                    paymentMethod = sale.paymentMethod,
                    type = "SALE",
                    status = "COMPLETED",
                    isSynced = false
                )
                val txItems = invoiceItems.map { item ->
                    TransactionItemEntity(
                        transactionId = 0,
                        productId = item.product.id,
                        barcode = item.product.barcode,
                        productName = item.product.name,
                        quantity = item.quantity,
                        unitPrice = item.unitPriceCents,
                        purchasePrice = item.product.purchasePrice,
                        discountAmount = item.lineDiscountCents,
                        taxRate = item.product.taxRate,
                        totalPrice = item.totalAfterDiscountCents
                    )
                }
                repository.recordTransaction(transaction, txItems)

                val effectiveCtx = uiContext ?: appContext
                val transport = PrinterTransport.fromString(printerConnectionType)
                val isBtActive = if (transport == PrinterTransport.BLUETOOTH) {
                    BluetoothPrinterService.isBluetoothEnabled(effectiveCtx) &&
                        BluetoothPrinterConnectionManager.hasConnectPermission(effectiveCtx)
                } else {
                    true
                }

                if (autoPrintReceipt && isBtActive) {
                    val targetDevice = resolveTargetDevice(
                        transport = transport,
                        printerName = printerName,
                        printerMac = printerMac,
                        printerIp = printerIp,
                        printerPort = printerPort
                    )
                    val resolvedProfile = printProfile ?: ThermalPrintProfile.fromSettings(
                        settings = settings,
                        paperWidthMm = printerPaperWidth,
                        language = language
                    )
                    scope.launch(Dispatchers.IO) {
                        PosThermalPrinterServiceProvider.instance.printSaleInvoice(
                            context = effectiveCtx,
                            sale = savedInvoice,
                            items = saleItems,
                            settings = settings,
                            lang = language,
                            target = targetDevice,
                            profileOverride = resolvedProfile
                        )
                    }
                }

                onSavedReceipt(savedInvoice, saleItems)
                onClearCart()
                onBeep()
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Checkout execution failed", e)
                onFailure(PosErrorUiTranslator.fromThrowable(e, OperationType.SALE_CHECKOUT, language))
            }
        }
    }

    fun printCheckoutTransactionSummary(
        context: Context,
        currentUser: UserEntity?,
        language: String,
        settings: GlobalSettingsEntity,
        invoiceItems: List<CartItem>,
        selectedClientId: Long,
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
        isDraftSummary: Boolean,
        invoiceNumberOverride: String?,
        printProfile: ThermalPrintProfile? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        scope.launch {
            try {
                if (invoiceItems.isEmpty()) {
                    val emptyMsg = Bilingual.tr(language, "السلة فارغة، لا توجد عناصر للطباعة", "Le panier est vide, rien à imprimer", "Cart is empty, nothing to print")
                    withContext(Dispatchers.Main) { onResult(false, emptyMsg) }
                    return@launch
                }

                val customer = if (selectedClientId > 1L) repository.getCustomerById(selectedClientId) else null
                val receiptData = BluetoothPrinterConnectionManager.buildTransactionSummaryReceipt(
                    cartItems = invoiceItems,
                    subtotal = subtotal,
                    discountAmount = discountAmount,
                    netTotal = netTotal,
                    paymentMethod = paymentMethod,
                    receivedAmount = receivedAmount,
                    changeDue = changeDue,
                    cashierName = currentUser?.username?.trim().orEmpty(),
                    customerName = customer?.name,
                    settings = settings,
                    lang = language,
                    invoiceNumberOverride = invoiceNumberOverride,
                    isDraftSummary = isDraftSummary
                )

                val transport = PrinterTransport.fromString(printerConnectionType)
                val targetDevice = resolveTargetDevice(
                    transport = transport,
                    printerName = printerName,
                    printerMac = printerMac,
                    printerIp = printerIp,
                    printerPort = printerPort
                )
                val resolvedProfile = printProfile ?: ThermalPrintProfile.fromSettings(
                    settings = settings,
                    paperWidthMm = printerPaperWidth,
                    language = language
                )
                val execResult = PosThermalPrinterServiceProvider.instance.printReceipt(
                    context = context,
                    receipt = receiptData,
                    profile = resolvedProfile,
                    target = targetDevice,
                    jobTitle = Bilingual.tr(language, "ملخص المعاملة", "Résumé transaction", "Transaction Summary")
                )
                withContext(Dispatchers.Main) { onResult(execResult.success, execResult.message) }
            } catch (t: Throwable) {
                Log.e(TAG, "Error printing checkout transaction summary", t)
                val errMsg = t.localizedMessage ?: "خطأ غير متوقع أثناء الطباعة"
                withContext(Dispatchers.Main) { onResult(false, errMsg) }
            }
        }
    }

    private fun resolveTargetDevice(
        transport: PrinterTransport,
        printerName: String,
        printerMac: String,
        printerIp: String,
        printerPort: Int
    ): ThermalPrinterDevice {
        val activeServicePrinter = PosThermalPrinterServiceProvider.instance.activePrinter.value
        if (activeServicePrinter != null && activeServicePrinter.transport == transport) {
            return activeServicePrinter
        }
        val activeBt = BluetoothPrinterConnectionManager.activeDevice.value
        val fallbackAddr = printerMac.ifBlank { printerName.ifBlank { activeBt?.address.orEmpty() } }
        return when (transport) {
            PrinterTransport.WIFI -> ThermalPrinterDevice(
                id = "$printerIp:$printerPort",
                name = "Wi-Fi ($printerIp)",
                address = printerIp.trim(),
                transport = PrinterTransport.WIFI,
                port = printerPort
            )
            PrinterTransport.SYSTEM -> ThermalPrinterDevice(
                id = "android_system_print",
                name = "Android System Print",
                address = "SYSTEM",
                transport = PrinterTransport.SYSTEM
            )
            PrinterTransport.USB -> ThermalPrinterDevice(
                id = fallbackAddr.ifBlank { "usb_auto" },
                name = printerName.ifBlank { "USB Thermal Printer" },
                address = fallbackAddr,
                transport = PrinterTransport.USB
            )
            PrinterTransport.BLUETOOTH -> ThermalPrinterDevice(
                id = fallbackAddr.ifBlank { "bt_auto" },
                name = printerName.ifBlank { activeBt?.name ?: "Bluetooth Printer" },
                address = fallbackAddr,
                transport = PrinterTransport.BLUETOOTH
            )
        }
    }
}
