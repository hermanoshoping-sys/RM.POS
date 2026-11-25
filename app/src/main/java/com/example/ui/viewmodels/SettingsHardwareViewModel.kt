package com.example.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GlobalSettingsEntity
import com.example.data.PosRepository
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.printer.BatchPrintQueueState
import com.example.printer.BluetoothPrinterConnectionManager
import com.example.printer.BtPrinterConnectionState
import com.example.printer.PosThermalPrinterService
import com.example.printer.PrintExecutionResult
import com.example.printer.PrintJobRecord
import com.example.printer.PrinterConnectionStatus
import com.example.printer.PrinterDeviceInfo
import com.example.printer.PrinterTransport
import com.example.printer.ReceiptTextDirection
import com.example.printer.ThermalFontSize
import com.example.printer.ThermalPrintProfile
import com.example.printer.ThermalPrinterDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Modular ViewModel for Store Settings, Language, Currency, Logo, Printer, and Hardware Controls.
 */
class SettingsHardwareViewModel(
    private val repository: PosRepository,
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE),
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _currentLanguage = MutableStateFlow(prefs.getString("app_language", "AR") ?: "AR")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _currency = MutableStateFlow(prefs.getString("app_currency", "MAD") ?: "MAD")
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _receiptPaperSize = MutableStateFlow(prefs.getString("receipt_paper_size", "80mm") ?: "80mm")
    val receiptPaperSize: StateFlow<String> = _receiptPaperSize.asStateFlow()

    private val _printerConnectionType = MutableStateFlow(prefs.getString("printer_connection", "BLUETOOTH") ?: "BLUETOOTH")
    val printerConnectionType: StateFlow<String> = _printerConnectionType.asStateFlow()

    private val _printerName = MutableStateFlow(prefs.getString("printer_name", "") ?: "")
    val printerName: StateFlow<String> = _printerName.asStateFlow()

    private val _printerMac = MutableStateFlow(prefs.getString("printer_mac", "") ?: "")
    val printerMac: StateFlow<String> = _printerMac.asStateFlow()

    private val _printerIp = MutableStateFlow(prefs.getString("printer_ip", "192.168.1.100") ?: "192.168.1.100")
    val printerIp: StateFlow<String> = _printerIp.asStateFlow()

    private val _printerPort = MutableStateFlow(prefs.getInt("printer_port", 9100))
    val printerPort: StateFlow<Int> = _printerPort.asStateFlow()

    private val _printerPaperWidth = MutableStateFlow(prefs.getInt("printer_paper_width", 58))
    val printerPaperWidth: StateFlow<Int> = _printerPaperWidth.asStateFlow()

    private val _autoPrintReceipt = MutableStateFlow(prefs.getBoolean("auto_print_receipt", true))
    val autoPrintReceipt: StateFlow<Boolean> = _autoPrintReceipt.asStateFlow()

    private val _autoOpenCashDrawer = MutableStateFlow(prefs.getBoolean("auto_open_cash_drawer", false))
    val autoOpenCashDrawer: StateFlow<Boolean> = _autoOpenCashDrawer.asStateFlow()

    private val _cashDrawerPin = MutableStateFlow(prefs.getString("cash_drawer_pin", "PIN_2") ?: "PIN_2")
    val cashDrawerPin: StateFlow<String> = _cashDrawerPin.asStateFlow()

    private val _companyLogoUri = MutableStateFlow<String?>(prefs.getString("company_logo_uri", null))
    val companyLogoUri: StateFlow<String?> = _companyLogoUri.asStateFlow()

    private val _headerImageUri = MutableStateFlow<String?>(prefs.getString("header_image_uri", null))
    val headerImageUri: StateFlow<String?> = _headerImageUri.asStateFlow()

    private val _footerImageUri = MutableStateFlow<String?>(prefs.getString("footer_image_uri", null))
    val footerImageUri: StateFlow<String?> = _footerImageUri.asStateFlow()

    private val _settings = MutableStateFlow(GlobalSettingsEntity())
    val settings: StateFlow<GlobalSettingsEntity> = _settings.asStateFlow()

    private val hardwareControlsDelegate = HardwareControlsDelegate(
        scope = scope,
        languageProvider = { _currentLanguage.value },
        settingsProvider = { _settings.value },
        connectionTypeProvider = { _printerConnectionType.value },
        printerNameProvider = { _printerName.value },
        printerMacProvider = { _printerMac.value },
        printerIpProvider = { _printerIp.value },
        printerPortProvider = { _printerPort.value },
        paperWidthProvider = { _printerPaperWidth.value },
        cashDrawerPinProvider = { _cashDrawerPin.value }
    )

    val calcInput: StateFlow<String> get() = hardwareControlsDelegate.calcInput
    val calcOutput: StateFlow<String> get() = hardwareControlsDelegate.calcOutput

    val btConnectionState: StateFlow<BtPrinterConnectionState>
        get() = BluetoothPrinterConnectionManager.connectionState

    val btActiveDevice: StateFlow<PrinterDeviceInfo?>
        get() = BluetoothPrinterConnectionManager.activeDevice

    val thermalPrinterDelegate: ThermalPrinterServiceDelegate = ThermalPrinterServiceDelegate(
        prefs = prefs,
        appContext = context,
        scope = scope,
        settingsProvider = { _settings.value },
        languageProvider = { _currentLanguage.value },
        connectionTypeProvider = { _printerConnectionType.value },
        printerNameProvider = { _printerName.value },
        printerMacProvider = { _printerMac.value },
        printerIpProvider = { _printerIp.value },
        printerPortProvider = { _printerPort.value },
        paperWidthProvider = { _printerPaperWidth.value },
        onPrinterSelected = { transport, name, address ->
            updatePrinterConnectionType(transport)
            selectPrinter(name, address)
        }
    )

    val printerService: PosThermalPrinterService get() = thermalPrinterDelegate.printerService
    val printerConnectionStatus: StateFlow<PrinterConnectionStatus> get() = thermalPrinterDelegate.connectionStatus
    val activeThermalPrinter: StateFlow<ThermalPrinterDevice?> get() = thermalPrinterDelegate.activePrinter
    val discoveredThermalPrinters: StateFlow<List<ThermalPrinterDevice>> get() = thermalPrinterDelegate.discoveredPrinters
    val printJobHistory: StateFlow<List<PrintJobRecord>> get() = thermalPrinterDelegate.printJobHistory
    val batchPrintQueueState: StateFlow<BatchPrintQueueState> get() = thermalPrinterDelegate.batchPrintQueueState
    val thermalFontSize: StateFlow<ThermalFontSize> get() = thermalPrinterDelegate.thermalFontSize
    val receiptTextDirection: StateFlow<ReceiptTextDirection> get() = thermalPrinterDelegate.receiptTextDirection
    val thermalRasterEnabled: StateFlow<Boolean> get() = thermalPrinterDelegate.thermalRasterEnabled

    fun toggleTheme() { _isDarkMode.value = !_isDarkMode.value }

    fun toggleLanguage() {
        val next = when (_currentLanguage.value.uppercase()) {
            "AR" -> "FR"
            "FR" -> "EN"
            else -> "AR"
        }
        setLanguage(next)
    }

    fun setLanguage(lang: String) {
        val normalized = when (lang.trim().uppercase()) {
            "AR", "FR", "EN" -> lang.trim().uppercase()
            else -> "AR"
        }
        _currentLanguage.value = normalized
        prefs.edit()
            .putString("app_language", normalized)
            .putString("saved_language", normalized)
            .apply()
        val updatedSettings = _settings.value.copy(language = normalized)
        _settings.value = updatedSettings
        scope.launch { repository.saveSettings(updatedSettings) }
    }

    fun setCurrency(curr: String) {
        val clean = curr.trim().ifBlank { "MAD" }
        _currency.value = clean
        prefs.edit()
            .putString("app_currency", clean)
            .putString("saved_currency", clean)
            .apply()
        val updatedSettings = _settings.value.copy(currency = clean)
        _settings.value = updatedSettings
        scope.launch { repository.saveSettings(updatedSettings) }
    }

    fun setReceiptPaperSize(size: String) {
        _receiptPaperSize.value = size
        prefs.edit().putString("receipt_paper_size", size).apply()
    }

    fun saveSettingsToPrefs(settings: GlobalSettingsEntity) = SettingsPrefsPersistence.saveToPrefs(prefs, settings)
    fun getSettingsFromPrefs(): GlobalSettingsEntity? = SettingsPrefsPersistence.loadFromPrefs(prefs)

    suspend fun initializeSettings() {
        val prefsSet = getSettingsFromPrefs()
        var set = repository.getSettings()
        if (prefsSet != null) {
            if (SettingsPrefsPersistence.hasSignificantDiff(set, prefsSet)) {
                repository.saveSettings(prefsSet)
                set = prefsSet
            }
        } else {
            saveSettingsToPrefs(set)
        }
        applySettingsState(set, includeLanguage = true)
    }

    fun observeSettingsChanges() {
        scope.launch {
            repository.getSettingsFlow().collect { s ->
                applySettingsState(s, includeLanguage = false)
            }
        }
    }

    private fun applySettingsState(s: GlobalSettingsEntity, includeLanguage: Boolean) {
        _settings.value = s
        if (includeLanguage) _currentLanguage.value = s.language
        if (s.currency.isNotBlank()) _currency.value = s.currency
        if (s.printerName.isNotBlank()) _printerName.value = s.printerName
        if (s.printerMac.isNotBlank()) _printerMac.value = s.printerMac
        _printerConnectionType.value = s.printerConnectionType
        _printerIp.value = s.printerIp
        _printerPort.value = s.printerPort
        _printerPaperWidth.value = s.printerPaperWidth
        _autoPrintReceipt.value = s.autoPrintReceipt
    }

    fun saveSettings(settings: GlobalSettingsEntity) = updateSettings(settings)

    fun updateSettings(newSettings: GlobalSettingsEntity) {
        _settings.value = newSettings
        saveSettingsToPrefs(newSettings)
        scope.launch { repository.saveSettings(newSettings) }
    }

    fun updateAllowNegativeStock(allowed: Boolean) = updateSettings(_settings.value.copy(allowNegativeStock = allowed))

    fun updateShopDetails(name: String, address: String, phone: String, taxNumber: String) {
        updateSettings(_settings.value.copy(shopName = name, shopAddress = address, shopPhone = phone, taxNumber = taxNumber))
    }

    fun updateReceiptCustomization(
        showShopName: Boolean, showAddress: Boolean, showPhone: Boolean, showTaxNumber: Boolean,
        showLogo: Boolean, showCashierName: Boolean, showCustomerInfo: Boolean, showDateTime: Boolean,
        showBarcode: Boolean, showPaymentMethod: Boolean, showReceiptFooter: Boolean,
        receiptHeader: String, receiptFooter: String,
        logoWidth: Int = _settings.value.logoWidth, logoHeight: Int = _settings.value.logoHeight,
        headerHeight: Int = _settings.value.headerHeight, headerFontSize: Int = _settings.value.headerFontSize
    ) {
        updateSettings(
            _settings.value.copy(
                showShopName = showShopName, showAddress = showAddress, showPhone = showPhone,
                showTaxNumber = showTaxNumber, showLogo = showLogo, showCashierName = showCashierName,
                showCustomerInfo = showCustomerInfo, showDateTime = showDateTime, showBarcode = showBarcode,
                showPaymentMethod = showPaymentMethod, showReceiptFooter = showReceiptFooter,
                receiptHeader = receiptHeader, receiptFooter = receiptFooter, logoWidth = logoWidth,
                logoHeight = logoHeight, headerHeight = headerHeight, headerFontSize = headerFontSize
            )
        )
    }

    fun updateCompanyLogo(uriString: String?) {
        _companyLogoUri.value = uriString
        if (uriString != null) prefs.edit().putString("company_logo_uri", uriString).apply()
        else prefs.edit().remove("company_logo_uri").apply()
    }

    fun updateHeaderImage(uriString: String?) {
        _headerImageUri.value = uriString
        if (uriString != null) prefs.edit().putString("header_image_uri", uriString).apply()
        else prefs.edit().remove("header_image_uri").apply()
    }

    fun updateFooterImage(uriString: String?) {
        _footerImageUri.value = uriString
        if (uriString != null) prefs.edit().putString("footer_image_uri", uriString).apply()
        else prefs.edit().remove("footer_image_uri").apply()
    }

    fun updateLogoDimensions(width: Int, height: Int, quality: Int = _settings.value.logoQuality) {
        updateSettings(
            _settings.value.copy(
                logoWidth = width.coerceIn(40, 320),
                logoHeight = height.coerceIn(30, 220),
                logoQuality = quality.coerceIn(40, 100)
            )
        )
    }

    fun updatePrinterConnectionType(type: String) {
        _printerConnectionType.value = type
        prefs.edit().putString("printer_connection", type).apply()
        savePrinterSetting { it.copy(printerConnectionType = type) }
    }

    fun updatePrinterName(name: String) {
        _printerName.value = name
        prefs.edit().putString("printer_name", name).apply()
        savePrinterSetting { it.copy(printerName = name) }
    }

    fun updatePrinterMac(mac: String) {
        _printerMac.value = mac
        prefs.edit().putString("printer_mac", mac).apply()
        savePrinterSetting { it.copy(printerMac = mac) }
    }

    fun updatePrinterIp(ip: String) {
        _printerIp.value = ip
        prefs.edit().putString("printer_ip", ip).apply()
        savePrinterSetting { it.copy(printerIp = ip) }
    }

    fun updatePrinterPort(port: Int) {
        _printerPort.value = port
        prefs.edit().putInt("printer_port", port).apply()
        savePrinterSetting { it.copy(printerPort = port) }
    }

    fun updatePrinterPaperWidth(width: Int) {
        _printerPaperWidth.value = width
        prefs.edit().putInt("printer_paper_width", width).apply()
        savePrinterSetting { it.copy(printerPaperWidth = width) }
    }

    fun updateAutoPrintReceipt(enabled: Boolean) {
        _autoPrintReceipt.value = enabled
        prefs.edit().putBoolean("auto_print_receipt", enabled).apply()
        savePrinterSetting { it.copy(autoPrintReceipt = enabled) }
    }

    private fun savePrinterSetting(transform: (GlobalSettingsEntity) -> GlobalSettingsEntity) {
        scope.launch {
            val updated = transform(_settings.value)
            repository.saveSettings(updated)
            _settings.value = updated
        }
    }

    fun updateAutoOpenCashDrawer(enabled: Boolean) {
        _autoOpenCashDrawer.value = false
        prefs.edit().putBoolean("auto_open_cash_drawer", false).apply()
    }

    fun updateCashDrawerPin(pin: String) {
        _cashDrawerPin.value = pin
        prefs.edit().putString("cash_drawer_pin", pin).apply()
    }

    fun selectPrinter(name: String, mac: String) {
        _printerName.value = name
        _printerMac.value = mac
        prefs.edit().putString("printer_name", name).putString("printer_mac", mac).apply()
        savePrinterSetting { it.copy(printerName = name, printerMac = mac) }
    }

    fun startBluetoothMonitoring(targetContext: Context = context) {
        BluetoothPrinterConnectionManager.startConnectionMonitoring(targetContext, _currentLanguage.value)
        thermalPrinterDelegate.initializeMonitoring(targetContext)
    }

    fun updateThermalFontSize(size: ThermalFontSize) = thermalPrinterDelegate.updateThermalFontSize(size)
    fun updateReceiptTextDirection(direction: ReceiptTextDirection) = thermalPrinterDelegate.updateReceiptTextDirection(direction)
    fun updateThermalRasterEnabled(enabled: Boolean) = thermalPrinterDelegate.updateThermalRasterEnabled(enabled)
    fun buildActivePrintProfile(langOverride: String? = null): ThermalPrintProfile =
        thermalPrinterDelegate.buildActivePrintProfile(langOverride)
    fun buildActiveTargetDevice(): ThermalPrinterDevice = thermalPrinterDelegate.buildActiveTargetDevice()

    fun updateReceiptHeaderFooter(header: String, footer: String, showLogo: Boolean) {
        updateSettings(
            _settings.value.copy(
                receiptHeader = header,
                receiptFooter = footer,
                showLogo = showLogo,
                showReceiptFooter = footer.isNotBlank() || _settings.value.showReceiptFooter
            )
        )
    }

    fun discoverThermalPrinters(
        targetContext: Context,
        transport: PrinterTransport? = null,
        onResult: ((List<ThermalPrinterDevice>) -> Unit)? = null
    ) = thermalPrinterDelegate.discoverPrinters(targetContext, transport, onResult)

    fun connectThermalPrinter(
        targetContext: Context,
        device: ThermalPrinterDevice,
        onResult: ((Boolean, String) -> Unit)? = null
    ) = thermalPrinterDelegate.connectPrinter(targetContext, device, onResult)

    fun disconnectThermalPrinter(onResult: ((Boolean, String) -> Unit)? = null) =
        thermalPrinterDelegate.disconnectPrinter(onResult)

    fun printThermalTestReceipt(
        targetContext: Context,
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) = thermalPrinterDelegate.printTestReceipt(targetContext, onResult)

    fun printWifiTestReceipt(
        targetContext: Context,
        ip: String = _printerIp.value,
        port: Int = _printerPort.value,
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) {
        val cleanIp = ip.trim()
        if (cleanIp.isNotBlank()) {
            updatePrinterConnectionType("WIFI")
            updatePrinterIp(cleanIp)
            updatePrinterPort(port)
        }
        thermalPrinterDelegate.printWifiTestReceipt(targetContext, cleanIp, port, onResult)
    }

    fun testWifiPrinterConnection(
        targetContext: Context,
        ip: String = _printerIp.value,
        port: Int = _printerPort.value,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val cleanIp = ip.trim()
        if (cleanIp.isNotBlank()) {
            updatePrinterConnectionType("WIFI")
            updatePrinterIp(cleanIp)
            updatePrinterPort(port)
        }
        thermalPrinterDelegate.testWifiConnection(targetContext, cleanIp, port, onResult)
    }

    fun printAndroidSystemTestReceipt(
        targetContext: Context,
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) = thermalPrinterDelegate.printAndroidSystemTestReceipt(targetContext, onResult)

    fun startBatchPrintSales(
        targetContext: Context,
        sales: List<SaleEntity>,
        itemsLoader: suspend (Long) -> List<SaleItemEntity>,
        targetOverride: ThermalPrinterDevice? = null
    ) = thermalPrinterDelegate.startBatchPrintSales(targetContext, sales, itemsLoader, targetOverride)

    fun retryFailedBatchPrint(
        targetContext: Context,
        targetOverride: ThermalPrinterDevice? = null
    ) = thermalPrinterDelegate.retryFailedBatchPrint(targetContext, targetOverride)

    fun cancelBatchPrintQueue() = thermalPrinterDelegate.cancelBatchPrintQueue()
    fun dismissBatchPrintQueue() = thermalPrinterDelegate.dismissBatchPrintQueue()

    fun printSaleViaService(
        targetContext: Context,
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) = thermalPrinterDelegate.printSaleInvoice(targetContext, sale, items, onResult)

    fun retryPrintJob(
        targetContext: Context,
        jobId: String,
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) = thermalPrinterDelegate.retryPrintJob(targetContext, jobId, onResult)

    fun clearPrintHistory(targetContext: Context = context) =
        thermalPrinterDelegate.clearPrintHistory(targetContext)

    fun connectBluetoothPrinter(
        targetContext: Context,
        device: PrinterDeviceInfo,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        updatePrinterConnectionType("BLUETOOTH")
        selectPrinter(device.name, device.address)
        startBluetoothMonitoring(targetContext)
        hardwareControlsDelegate.connectBluetoothPrinter(targetContext, device, onResult)
    }

    fun reconnectBluetoothPrinter(
        targetContext: Context = context,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        startBluetoothMonitoring(targetContext)
        hardwareControlsDelegate.reconnectBluetoothPrinter(targetContext, onResult)
    }

    fun disconnectBluetoothPrinter(onResult: ((Boolean, String) -> Unit)? = null) =
        hardwareControlsDelegate.disconnectBluetoothPrinter(onResult)

    fun printTestBluetoothPage(targetContext: Context, onResult: ((Boolean, String) -> Unit)? = null) =
        hardwareControlsDelegate.printTestBluetoothPage(targetContext, onResult)

    fun triggerCashDrawer(targetContext: Context, onResult: ((Boolean, String) -> Unit)? = null) =
        hardwareControlsDelegate.triggerCashDrawer(targetContext, onResult)

    fun onCalcKey(key: String) = hardwareControlsDelegate.onCalcKey(key)
}
