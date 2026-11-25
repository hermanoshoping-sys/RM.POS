package com.example.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.printer.BatchPrintQueueManager
import com.example.printer.BatchPrintQueueState
import com.example.printer.PosThermalPrinterService
import com.example.printer.PosThermalPrinterServiceProvider
import com.example.printer.PrintExecutionResult
import com.example.printer.PrintJobHistoryStore
import com.example.printer.PrintJobRecord
import com.example.printer.PrinterConnectionStatus
import com.example.printer.PrinterTransport
import com.example.printer.ReceiptTextDirection
import com.example.printer.ThermalFontSize
import com.example.printer.ThermalPaperWidth
import com.example.printer.ThermalPrintProfile
import com.example.printer.ThermalPrinterDevice
import com.example.printer.UsbPrinterConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modular delegate bridging [SettingsHardwareViewModel] with the standalone [PosThermalPrinterService].
 * Manages thermal print formatting preferences (font size, RTL/LTR direction, raster mode),
 * printer discovery (Bluetooth & USB), connection lifecycle, test printing, invoice printing, and job retries.
 */
class ThermalPrinterServiceDelegate(
    private val prefs: SharedPreferences,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val serviceProvider: () -> PosThermalPrinterService = { PosThermalPrinterServiceProvider.instance },
    private val settingsProvider: () -> GlobalSettingsEntity,
    private val languageProvider: () -> String,
    private val connectionTypeProvider: () -> String,
    private val printerNameProvider: () -> String,
    private val printerMacProvider: () -> String,
    private val printerIpProvider: () -> String,
    private val printerPortProvider: () -> Int,
    private val paperWidthProvider: () -> Int,
    private val onPrinterSelected: (transport: String, name: String, address: String) -> Unit
) {
    companion object {
        private const val TAG = "ThermalPrinterDelegate"
        private const val KEY_FONT_SIZE = "thermal_font_size"
        private const val KEY_TEXT_DIRECTION = "receipt_text_direction"
        private const val KEY_RASTER_ENABLED = "thermal_raster_enabled"
    }

    private val _thermalFontSize = MutableStateFlow(
        ThermalFontSize.fromString(prefs.getString(KEY_FONT_SIZE, ThermalFontSize.NORMAL.name))
    )
    val thermalFontSize: StateFlow<ThermalFontSize> = _thermalFontSize.asStateFlow()

    private val _receiptTextDirection = MutableStateFlow(
        ReceiptTextDirection.fromString(prefs.getString(KEY_TEXT_DIRECTION, ReceiptTextDirection.AUTO.name))
    )
    val receiptTextDirection: StateFlow<ReceiptTextDirection> = _receiptTextDirection.asStateFlow()

    private val _thermalRasterEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_RASTER_ENABLED, true)
    )
    val thermalRasterEnabled: StateFlow<Boolean> = _thermalRasterEnabled.asStateFlow()

    val printerService: PosThermalPrinterService
        get() = serviceProvider()

    val connectionStatus: StateFlow<PrinterConnectionStatus>
        get() = printerService.connectionStatus

    val activePrinter: StateFlow<ThermalPrinterDevice?>
        get() = printerService.activePrinter

    val discoveredPrinters: StateFlow<List<ThermalPrinterDevice>>
        get() = printerService.discoveredPrinters

    val printJobHistory: StateFlow<List<PrintJobRecord>>
        get() = printerService.printHistory

    val batchPrintQueueState: StateFlow<BatchPrintQueueState>
        get() = BatchPrintQueueManager.state

    fun initializeMonitoring(context: Context = appContext) {
        PrintJobHistoryStore.ensureLoaded(context)
        UsbPrinterConnectionManager.startMonitoring(context)
    }

    fun updateThermalFontSize(size: ThermalFontSize) {
        _thermalFontSize.value = size
        prefs.edit().putString(KEY_FONT_SIZE, size.name).apply()
    }

    fun updateReceiptTextDirection(direction: ReceiptTextDirection) {
        _receiptTextDirection.value = direction
        prefs.edit().putString(KEY_TEXT_DIRECTION, direction.name).apply()
    }

    fun updateThermalRasterEnabled(enabled: Boolean) {
        _thermalRasterEnabled.value = enabled
        prefs.edit().putBoolean(KEY_RASTER_ENABLED, enabled).apply()
    }

    fun buildActivePrintProfile(langOverride: String? = null): ThermalPrintProfile {
        val currentSettings = settingsProvider()
        val lang = (langOverride ?: languageProvider()).ifBlank { "AR" }
        return ThermalPrintProfile.fromSettings(
            settings = currentSettings,
            paperWidthMm = paperWidthProvider(),
            language = lang,
            fontSize = _thermalFontSize.value,
            textDirection = _receiptTextDirection.value,
            useRasterRendering = _thermalRasterEnabled.value
        ).copy(
            paperWidth = ThermalPaperWidth.fromMm(paperWidthProvider())
        )
    }

    fun buildActiveTargetDevice(): ThermalPrinterDevice {
        val transport = PrinterTransport.fromString(connectionTypeProvider())
        val active = activePrinter.value
        return when (transport) {
            PrinterTransport.WIFI -> {
                val ip = printerIpProvider().trim()
                val port = printerPortProvider()
                ThermalPrinterDevice(
                    id = "$ip:$port",
                    name = "Wi-Fi ($ip:$port)",
                    address = ip,
                    transport = PrinterTransport.WIFI,
                    port = port
                )
            }
            PrinterTransport.SYSTEM -> ThermalPrinterDevice(
                id = "android_system_print",
                name = "Android System Print",
                address = "SYSTEM",
                transport = PrinterTransport.SYSTEM
            )
            PrinterTransport.USB -> {
                val addr = printerMacProvider().ifBlank { printerNameProvider() }
                if (active != null && active.transport == PrinterTransport.USB && (addr.isBlank() || active.address == addr)) {
                    active
                } else {
                    ThermalPrinterDevice(
                        id = addr.ifBlank { "usb_auto" },
                        name = printerNameProvider().ifBlank { "USB Thermal Printer" },
                        address = addr,
                        transport = PrinterTransport.USB
                    )
                }
            }
            PrinterTransport.BLUETOOTH -> {
                val addr = printerMacProvider().ifBlank { printerNameProvider() }
                if (active != null && active.transport == PrinterTransport.BLUETOOTH && (addr.isBlank() || active.address == addr)) {
                    active
                } else {
                    ThermalPrinterDevice(
                        id = addr.ifBlank { "bt_auto" },
                        name = printerNameProvider().ifBlank { addr.ifBlank { "Bluetooth Thermal Printer" } },
                        address = addr,
                        transport = PrinterTransport.BLUETOOTH
                    )
                }
            }
        }
    }

    fun discoverPrinters(
        context: Context,
        transport: PrinterTransport? = null,
        onResult: ((List<ThermalPrinterDevice>) -> Unit)? = null
    ) {
        scope.launch {
            val found = runCatching { printerService.discoverPrinters(context, transport) }
                .onFailure { Log.e(TAG, "Printer discovery failed", it) }
                .getOrDefault(emptyList())
            withContext(Dispatchers.Main) { onResult?.invoke(found) }
        }
    }

    fun connectPrinter(
        context: Context,
        device: ThermalPrinterDevice,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        if (device.transport != PrinterTransport.WIFI) {
            onPrinterSelected(device.transport.name, device.name, device.address)
        }
        scope.launch {
            val res = printerService.connect(context, device, languageProvider())
            withContext(Dispatchers.Main) {
                res.fold(
                    onSuccess = { connected ->
                        onResult?.invoke(true, "تم الاتصال بالطابعة: ${connected.name}")
                    },
                    onFailure = { err ->
                        onResult?.invoke(false, err.localizedMessage ?: "تعذر الاتصال بالطابعة")
                    }
                )
            }
        }
    }

    fun disconnectPrinter(onResult: ((Boolean, String) -> Unit)? = null) {
        scope.launch {
            printerService.disconnect("تم فصل الطابعة من الإعدادات")
            withContext(Dispatchers.Main) { onResult?.invoke(true, "تم فصل الاتصال بالطابعة") }
        }
    }

    fun printTestReceipt(
        context: Context,
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) {
        scope.launch {
            val result = printerService.printTestReceipt(
                context = context,
                settings = settingsProvider(),
                profile = buildActivePrintProfile(),
                target = buildActiveTargetDevice()
            )
            withContext(Dispatchers.Main) { onResult?.invoke(result) }
        }
    }

    fun printWifiTestReceipt(
        context: Context,
        ip: String = printerIpProvider(),
        port: Int = printerPortProvider(),
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) {
        val cleanIp = ip.trim()
        val target = ThermalPrinterDevice(
            id = "$cleanIp:$port",
            name = "Wi-Fi ($cleanIp:$port)",
            address = cleanIp,
            transport = PrinterTransport.WIFI,
            port = port
        )
        scope.launch {
            val result = printerService.printTestReceipt(
                context = context,
                settings = settingsProvider(),
                profile = buildActivePrintProfile(),
                target = target
            )
            withContext(Dispatchers.Main) { onResult?.invoke(result) }
        }
    }

    fun testWifiConnection(
        context: Context,
        ip: String = printerIpProvider(),
        port: Int = printerPortProvider(),
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val cleanIp = ip.trim()
        val target = ThermalPrinterDevice(
            id = "$cleanIp:$port",
            name = "Wi-Fi ($cleanIp:$port)",
            address = cleanIp,
            transport = PrinterTransport.WIFI,
            port = port
        )
        connectPrinter(context, target, onResult)
    }

    fun printSaleInvoice(
        context: Context,
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) {
        scope.launch {
            val result = printerService.printSaleInvoice(
                context = context,
                sale = sale,
                items = items,
                settings = settingsProvider(),
                lang = languageProvider(),
                target = buildActiveTargetDevice(),
                profileOverride = buildActivePrintProfile()
            )
            withContext(Dispatchers.Main) { onResult?.invoke(result) }
        }
    }

    fun printAndroidSystemTestReceipt(
        context: Context,
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) {
        val target = ThermalPrinterDevice(
            id = "android_system_print",
            name = "Android Wi-Fi / Mopria Print",
            address = "SYSTEM",
            transport = PrinterTransport.SYSTEM
        )
        scope.launch {
            val result = printerService.printTestReceipt(
                context = context,
                settings = settingsProvider(),
                profile = buildActivePrintProfile(),
                target = target
            )
            withContext(Dispatchers.Main) { onResult?.invoke(result) }
        }
    }

    fun startBatchPrintSales(
        context: Context,
        sales: List<SaleEntity>,
        itemsLoader: suspend (Long) -> List<SaleItemEntity>,
        targetOverride: ThermalPrinterDevice? = null
    ) {
        if (sales.isEmpty()) return
        val target = targetOverride ?: buildActiveTargetDevice()
        val settings = settingsProvider()
        val profile = buildActivePrintProfile()
        scope.launch {
            val entries = withContext(Dispatchers.IO) {
                sales.map { sale ->
                    val lineItems = runCatching { itemsLoader(sale.id) }.getOrDefault(emptyList())
                    sale to lineItems
                }
            }
            BatchPrintQueueManager.startBatch(
                scope = scope,
                context = context,
                entries = entries,
                settings = settings,
                profile = profile,
                target = target
            )
        }
    }

    fun retryFailedBatchPrint(
        context: Context,
        targetOverride: ThermalPrinterDevice? = null
    ) {
        BatchPrintQueueManager.retryFailed(
            scope = scope,
            context = context,
            settings = settingsProvider(),
            profile = buildActivePrintProfile(),
            target = targetOverride ?: buildActiveTargetDevice()
        )
    }

    fun cancelBatchPrintQueue() {
        BatchPrintQueueManager.cancelQueue()
    }

    fun dismissBatchPrintQueue() {
        BatchPrintQueueManager.dismissQueue()
    }

    fun retryPrintJob(
        context: Context,
        jobId: String,
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) {
        scope.launch {
            val result = printerService.retryPrintJob(context, jobId)
            withContext(Dispatchers.Main) { onResult?.invoke(result) }
        }
    }

    fun clearPrintHistory(context: Context = appContext) {
        printerService.clearPrintHistory(context)
    }
}
