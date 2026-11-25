package com.example.ui.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.PosOperationError
import com.example.domain.PosOperationResult
import com.example.printer.BatchPrintQueueState
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
import com.example.security.LicenseSecurityManager
import com.example.util.GeoFenceResult
import com.example.util.ProductExcelManager
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Thin Coordinator ViewModel in `com.example.ui.viewmodels` delegating to focused ViewModels. */
class PosViewModel(
    application: Application,
    val repository: PosRepository = PosRepository(AppDatabase.getDatabase(application).appDao(), AppDatabase.getDatabase(application))
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)

    val settingsHardwareViewModel: SettingsHardwareViewModel by lazy {
        SettingsHardwareViewModel(repository, getApplication(), prefs, viewModelScope)
    }
    val authSecurityViewModel: AuthSecurityViewModel by lazy {
        AuthSecurityViewModel(
            repository, application, prefs, viewModelScope,
            languageProvider = { settingsHardwareViewModel.currentLanguage.value },
            onBeep = ::beep
        )
    }
    val cartViewModel: CartViewModel by lazy {
        CartViewModel(repository, viewModelScope, ::beep) { err -> salesViewModel.reportExternalError(err) }
    }
    val salesViewModel: SalesViewModel by lazy { SalesViewModel(repository, viewModelScope, ::beep) }
    val inventoryViewModel: InventoryViewModel by lazy { InventoryViewModel(repository, viewModelScope) }
    val debtLedgerViewModel: DebtLedgerViewModel by lazy { DebtLedgerViewModel(repository, viewModelScope) }
    val backupSyncViewModel: BackupSyncViewModel by lazy {
        BackupSyncViewModel(
            repository, getApplication(), prefs, viewModelScope,
            languageProvider = { settingsHardwareViewModel.currentLanguage.value },
            onBeep = ::beep
        )
    }
    val analyticsDashboardViewModel: AnalyticsDashboardViewModel by lazy {
        AnalyticsDashboardViewModel(repository, viewModelScope)
    }
    val stocktakingViewModel: StocktakingViewModel by lazy {
        StocktakingViewModel(
            repository = repository,
            scope = viewModelScope,
            currentUserProvider = { authSecurityViewModel.currentUser.value },
            languageProvider = { settingsHardwareViewModel.currentLanguage.value },
            onBeep = ::beep
        )
    }

    init {
        authSecurityViewModel.checkLicenseExpirationWithAntiRollback()
        settingsHardwareViewModel.startBluetoothMonitoring(getApplication())
        viewModelScope.launch {
            repository.verifyAndSeedDatabase()
            settingsHardwareViewModel.initializeSettings()
            settingsHardwareViewModel.observeSettingsChanges()
            try {
                cartViewModel.restorePersistentCartIfEmpty()
            } catch (e: Exception) {
                logError("Failed to restore cart state", e)
            }
        }
    }

    // --- SETTINGS & HARDWARE ---
    val isDarkMode: StateFlow<Boolean> get() = settingsHardwareViewModel.isDarkMode
    val currentLanguage: StateFlow<String> get() = settingsHardwareViewModel.currentLanguage
    val currency: StateFlow<String> get() = settingsHardwareViewModel.currency
    val receiptPaperSize: StateFlow<String> get() = settingsHardwareViewModel.receiptPaperSize
    val printerConnectionType: StateFlow<String> get() = settingsHardwareViewModel.printerConnectionType
    val printerName: StateFlow<String> get() = settingsHardwareViewModel.printerName
    val printerMac: StateFlow<String> get() = settingsHardwareViewModel.printerMac
    val printerIp: StateFlow<String> get() = settingsHardwareViewModel.printerIp
    val printerPort: StateFlow<Int> get() = settingsHardwareViewModel.printerPort
    val printerPaperWidth: StateFlow<Int> get() = settingsHardwareViewModel.printerPaperWidth
    val autoPrintReceipt: StateFlow<Boolean> get() = settingsHardwareViewModel.autoPrintReceipt
    val autoOpenCashDrawer: StateFlow<Boolean> get() = settingsHardwareViewModel.autoOpenCashDrawer
    val cashDrawerPin: StateFlow<String> get() = settingsHardwareViewModel.cashDrawerPin
    val settings: StateFlow<GlobalSettingsEntity> get() = settingsHardwareViewModel.settings
    val companyLogoUri: StateFlow<String?> get() = settingsHardwareViewModel.companyLogoUri
    val headerImageUri: StateFlow<String?> get() = settingsHardwareViewModel.headerImageUri
    val footerImageUri: StateFlow<String?> get() = settingsHardwareViewModel.footerImageUri
    val calcInput: StateFlow<String> get() = settingsHardwareViewModel.calcInput
    val calcOutput: StateFlow<String> get() = settingsHardwareViewModel.calcOutput
    val btConnectionState: StateFlow<BtPrinterConnectionState> get() = settingsHardwareViewModel.btConnectionState
    val btActiveDevice: StateFlow<PrinterDeviceInfo?> get() = settingsHardwareViewModel.btActiveDevice
    val printerService: PosThermalPrinterService get() = settingsHardwareViewModel.printerService
    val printerConnectionStatus: StateFlow<PrinterConnectionStatus> get() = settingsHardwareViewModel.printerConnectionStatus
    val activeThermalPrinter: StateFlow<ThermalPrinterDevice?> get() = settingsHardwareViewModel.activeThermalPrinter
    val discoveredThermalPrinters: StateFlow<List<ThermalPrinterDevice>> get() = settingsHardwareViewModel.discoveredThermalPrinters
    val printJobHistory: StateFlow<List<PrintJobRecord>> get() = settingsHardwareViewModel.printJobHistory
    val batchPrintQueueState: StateFlow<BatchPrintQueueState> get() = settingsHardwareViewModel.batchPrintQueueState
    val thermalFontSize: StateFlow<ThermalFontSize> get() = settingsHardwareViewModel.thermalFontSize
    val receiptTextDirection: StateFlow<ReceiptTextDirection> get() = settingsHardwareViewModel.receiptTextDirection
    val thermalRasterEnabled: StateFlow<Boolean> get() = settingsHardwareViewModel.thermalRasterEnabled

    fun toggleTheme() = settingsHardwareViewModel.toggleTheme()
    fun toggleLanguage() = settingsHardwareViewModel.toggleLanguage()
    fun setLanguage(lang: String) = settingsHardwareViewModel.setLanguage(lang)
    fun setCurrency(curr: String) = settingsHardwareViewModel.setCurrency(curr)
    fun setReceiptPaperSize(size: String) = settingsHardwareViewModel.setReceiptPaperSize(size)
    fun updateSettings(newSettings: GlobalSettingsEntity) = settingsHardwareViewModel.updateSettings(newSettings)
    fun updateAllowNegativeStock(allowed: Boolean) = settingsHardwareViewModel.updateAllowNegativeStock(allowed)
    fun updateShopDetails(name: String, address: String, phone: String, taxNumber: String) =
        settingsHardwareViewModel.updateShopDetails(name, address, phone, taxNumber)
    fun updateReceiptCustomization(
        showShopName: Boolean, showAddress: Boolean, showPhone: Boolean, showTaxNumber: Boolean,
        showLogo: Boolean, showCashierName: Boolean, showCustomerInfo: Boolean, showDateTime: Boolean,
        showBarcode: Boolean, showPaymentMethod: Boolean, showReceiptFooter: Boolean,
        receiptHeader: String, receiptFooter: String,
        logoWidth: Int = settings.value.logoWidth, logoHeight: Int = settings.value.logoHeight,
        headerHeight: Int = settings.value.headerHeight, headerFontSize: Int = settings.value.headerFontSize
    ) = settingsHardwareViewModel.updateReceiptCustomization(
        showShopName, showAddress, showPhone, showTaxNumber, showLogo, showCashierName,
        showCustomerInfo, showDateTime, showBarcode, showPaymentMethod, showReceiptFooter,
        receiptHeader, receiptFooter, logoWidth, logoHeight, headerHeight, headerFontSize
    )
    fun updateCompanyLogo(uriString: String?) = settingsHardwareViewModel.updateCompanyLogo(uriString)
    fun updateHeaderImage(uriString: String?) = settingsHardwareViewModel.updateHeaderImage(uriString)
    fun updateFooterImage(uriString: String?) = settingsHardwareViewModel.updateFooterImage(uriString)
    fun updateLogoDimensions(width: Int, height: Int, quality: Int = settings.value.logoQuality) =
        settingsHardwareViewModel.updateLogoDimensions(width, height, quality)
    fun updatePrinterConnectionType(type: String) = settingsHardwareViewModel.updatePrinterConnectionType(type)
    fun updatePrinterName(name: String) = settingsHardwareViewModel.updatePrinterName(name)
    fun updatePrinterMac(mac: String) = settingsHardwareViewModel.updatePrinterMac(mac)
    fun updatePrinterIp(ip: String) = settingsHardwareViewModel.updatePrinterIp(ip)
    fun updatePrinterPort(port: Int) = settingsHardwareViewModel.updatePrinterPort(port)
    fun updatePrinterPaperWidth(width: Int) = settingsHardwareViewModel.updatePrinterPaperWidth(width)
    fun updateAutoPrintReceipt(enabled: Boolean) = settingsHardwareViewModel.updateAutoPrintReceipt(enabled)
    fun updateAutoOpenCashDrawer(enabled: Boolean) = settingsHardwareViewModel.updateAutoOpenCashDrawer(enabled)
    fun updateCashDrawerPin(pin: String) = settingsHardwareViewModel.updateCashDrawerPin(pin)
    fun selectPrinter(name: String, mac: String) = settingsHardwareViewModel.selectPrinter(name, mac)
    fun connectBluetoothPrinter(context: Context, device: PrinterDeviceInfo, onResult: ((Boolean, String) -> Unit)? = null) =
        settingsHardwareViewModel.connectBluetoothPrinter(context, device, onResult)
    fun reconnectBluetoothPrinter(context: Context = getApplication(), onResult: ((Boolean, String) -> Unit)? = null) =
        settingsHardwareViewModel.reconnectBluetoothPrinter(context, onResult)
    fun disconnectBluetoothPrinter(onResult: ((Boolean, String) -> Unit)? = null) =
        settingsHardwareViewModel.disconnectBluetoothPrinter(onResult)
    fun printTestBluetoothPage(context: Context, onResult: ((Boolean, String) -> Unit)? = null) =
        settingsHardwareViewModel.printTestBluetoothPage(context, onResult)
    fun updateThermalFontSize(size: ThermalFontSize) = settingsHardwareViewModel.updateThermalFontSize(size)
    fun updateReceiptTextDirection(direction: ReceiptTextDirection) = settingsHardwareViewModel.updateReceiptTextDirection(direction)
    fun updateThermalRasterEnabled(enabled: Boolean) = settingsHardwareViewModel.updateThermalRasterEnabled(enabled)
    fun updateReceiptHeaderFooter(header: String, footer: String, showLogo: Boolean) =
        settingsHardwareViewModel.updateReceiptHeaderFooter(header, footer, showLogo)
    fun buildActivePrintProfile(langOverride: String? = null): ThermalPrintProfile =
        settingsHardwareViewModel.buildActivePrintProfile(langOverride)
    fun discoverThermalPrinters(context: Context, transport: PrinterTransport? = null, onResult: ((List<ThermalPrinterDevice>) -> Unit)? = null) =
        settingsHardwareViewModel.discoverThermalPrinters(context, transport, onResult)
    fun connectThermalPrinter(context: Context, device: ThermalPrinterDevice, onResult: ((Boolean, String) -> Unit)? = null) =
        settingsHardwareViewModel.connectThermalPrinter(context, device, onResult)
    fun disconnectThermalPrinter(onResult: ((Boolean, String) -> Unit)? = null) =
        settingsHardwareViewModel.disconnectThermalPrinter(onResult)
    fun printThermalTestReceipt(context: Context, onResult: ((PrintExecutionResult) -> Unit)? = null) =
        settingsHardwareViewModel.printThermalTestReceipt(context, onResult)
    fun printWifiTestReceipt(
        context: Context,
        ip: String = printerIp.value,
        port: Int = printerPort.value,
        onResult: ((PrintExecutionResult) -> Unit)? = null
    ) = settingsHardwareViewModel.printWifiTestReceipt(context, ip, port, onResult)
    fun testWifiPrinterConnection(
        context: Context,
        ip: String = printerIp.value,
        port: Int = printerPort.value,
        onResult: ((Boolean, String) -> Unit)? = null
    ) = settingsHardwareViewModel.testWifiPrinterConnection(context, ip, port, onResult)
    fun printAndroidSystemTestReceipt(context: Context, onResult: ((PrintExecutionResult) -> Unit)? = null) =
        settingsHardwareViewModel.printAndroidSystemTestReceipt(context, onResult)
    fun startBatchPrintSales(
        context: Context,
        sales: List<SaleEntity>,
        targetOverride: ThermalPrinterDevice? = null
    ) = settingsHardwareViewModel.startBatchPrintSales(
        targetContext = context,
        sales = sales,
        itemsLoader = { saleId -> salesViewModel.getSaleItems(saleId) },
        targetOverride = targetOverride
    )
    fun retryFailedBatchPrint(
        context: Context,
        targetOverride: ThermalPrinterDevice? = null
    ) = settingsHardwareViewModel.retryFailedBatchPrint(context, targetOverride)
    fun cancelBatchPrintQueue() = settingsHardwareViewModel.cancelBatchPrintQueue()
    fun dismissBatchPrintQueue() = settingsHardwareViewModel.dismissBatchPrintQueue()
    fun printSaleViaService(context: Context, sale: SaleEntity, items: List<SaleItemEntity>, onResult: ((PrintExecutionResult) -> Unit)? = null) =
        settingsHardwareViewModel.printSaleViaService(context, sale, items, onResult)
    fun retryPrintJob(context: Context, jobId: String, onResult: ((PrintExecutionResult) -> Unit)? = null) =
        settingsHardwareViewModel.retryPrintJob(context, jobId, onResult)
    fun clearPrintHistory(context: Context = getApplication()) =
        settingsHardwareViewModel.clearPrintHistory(context)
    fun triggerCashDrawer(context: Context, onResult: ((Boolean, String) -> Unit)? = null) =
        settingsHardwareViewModel.triggerCashDrawer(context, onResult)
    fun onCalcKey(key: String) = settingsHardwareViewModel.onCalcKey(key)

    // --- AUTH & LICENSE ---
    val currentUser: StateFlow<UserEntity?> get() = authSecurityViewModel.currentUser
    val loginError: StateFlow<String?> get() = authSecurityViewModel.loginError
    val isLicenseActivated: StateFlow<Boolean> get() = authSecurityViewModel.isLicenseActivated
    val isClockRollbackSuspended: StateFlow<Boolean> get() = authSecurityViewModel.isClockRollbackSuspended
    val isExpirySuspended: StateFlow<Boolean> get() = authSecurityViewModel.isExpirySuspended
    val licenseKey: StateFlow<String> get() = authSecurityViewModel.licenseKey
    val licenseExpires: StateFlow<Long> get() = authSecurityViewModel.licenseExpires
    val licenseGraceUntil: StateFlow<Long> get() = authSecurityViewModel.licenseGraceUntil
    val licenseKeyId: StateFlow<String> get() = authSecurityViewModel.licenseKeyId
    val licenseType: StateFlow<String> get() = authSecurityViewModel.licenseType
    val needsOnlineRenewal: StateFlow<Boolean> get() = authSecurityViewModel.needsOnlineRenewal
    val licenseError: StateFlow<String?> get() = authSecurityViewModel.licenseError
    val requirePasswordOnLogin: StateFlow<Boolean> get() = authSecurityViewModel.requirePasswordOnLogin
    val requirePasswordOnDelete: StateFlow<Boolean> get() = authSecurityViewModel.requirePasswordOnDelete
    val requirePasswordOnEdit: StateFlow<Boolean> get() = authSecurityViewModel.requirePasswordOnEdit

    fun getDeviceId(): String = authSecurityViewModel.getDeviceId()
    fun getRemainingLicenseDays(): Long = authSecurityViewModel.getRemainingLicenseDays()
    fun getRemainingGraceDays(): Long = authSecurityViewModel.getRemainingGraceDays()
    fun deactivateLicense() = authSecurityViewModel.deactivateLicense()
    fun checkLicenseExpirationWithAntiRollback(nowMillis: Long = System.currentTimeMillis()) =
        authSecurityViewModel.checkLicenseExpirationWithAntiRollback(nowMillis)
    fun verifyOnlineSubscription(serverUrl: String = LicenseSecurityManager.DEFAULT_LICENSE_SERVER_URL, onResult: (Boolean, String) -> Unit = { _, _ -> }) =
        authSecurityViewModel.verifyOnlineSubscription(serverUrl, onResult)
    fun applyClockResetToken(resetToken: String, nowMillis: Long = System.currentTimeMillis(), onSuccess: (String) -> Unit = {}, onError: (String) -> Unit = {}): Boolean =
        authSecurityViewModel.applyClockResetToken(resetToken, nowMillis, onSuccess, onError)
    fun activateLicense(activationCode: String, onSuccess: (Boolean) -> Unit = {}, onError: (String) -> Unit = {}) =
        authSecurityViewModel.activateLicense(activationCode, onSuccess, onError)
    fun activateLicense(licenseNumber: String, activationPassword: String, onSuccess: (Boolean) -> Unit = {}, onError: (String) -> Unit = {}) =
        authSecurityViewModel.activateLicense(licenseNumber, activationPassword, onSuccess, onError)
    fun isWeakPassword(pass: String) = authSecurityViewModel.isWeakPassword(pass)
    fun login(username: String, pin: String, onSuccess: () -> Unit) = authSecurityViewModel.login(username, pin, onSuccess)
    fun logout() = authSecurityViewModel.logout()
    fun updateLoginPasswordSecurity(require: Boolean) = authSecurityViewModel.updateLoginPasswordSecurity(require)
    fun autoLoginIfPasswordNotRequired(users: List<UserEntity>) = authSecurityViewModel.autoLoginIfPasswordNotRequired(users)
    fun setProgramLoginPassword(newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) =
        authSecurityViewModel.setProgramLoginPassword(newPass, confirmPass, onResult)
    fun executeEmergencyRecovery(recoveryToken: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) =
        authSecurityViewModel.executeEmergencyRecovery(recoveryToken, newPassword, onSuccess, onError)
    fun verifyBackupPassword(password: String, onResult: (Boolean) -> Unit) =
        authSecurityViewModel.verifyBackupPassword(password, onResult)
    fun setupInitialAdmin(
        username: String, pin: String, storeName: String = "", phone: String = "",
        address: String = "", backupEmail: String = "", onSuccess: () -> Unit = {}
    ) {
        if (backupEmail.isNotBlank() || phone.isNotBlank()) {
            updateLinkedAccount(
                email = if (backupEmail.isNotBlank()) backupEmail.trim() else linkedEmail.value,
                phone = if (phone.isNotBlank()) phone.trim() else linkedPhone.value
            )
        }
        authSecurityViewModel.setupInitialAdmin(username, pin, storeName, phone, address, settings.value, ::updateSettings, onSuccess)
    }
    fun changePassword(currentPass: String, newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) =
        authSecurityViewModel.changePassword(currentPass, newPass, confirmPass, onResult)
    fun updateCurrentUsername(newUsername: String, onResult: (Boolean, String) -> Unit) =
        authSecurityViewModel.updateCurrentUsername(newUsername, onResult)
    fun updateDeleteSecurity(
        require: Boolean,
        newPass: String,
        onError: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) = authSecurityViewModel.updateDeleteSecurity(require, newPass, onError, onComplete)
    fun updateEditSecurity(
        require: Boolean,
        newPass: String,
        onError: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) = authSecurityViewModel.updateEditSecurity(require, newPass, onError, onComplete)
    fun verifyDeletePassword(input: String, onResult: (Boolean) -> Unit) =
        authSecurityViewModel.verifyDeletePassword(input, users.value, onResult)
    fun verifyEditPassword(input: String, onResult: (Boolean) -> Unit) =
        authSecurityViewModel.verifyEditPassword(input, users.value, onResult)

    // --- INVENTORY & CATEGORIES ---
    val categories: StateFlow<List<CategoryEntity>> get() = inventoryViewModel.categories
    val products: StateFlow<List<ProductEntity>> get() = inventoryViewModel.products
    val productsPage: StateFlow<Int> get() = inventoryViewModel.productsPage
    val productsPageSize: Int get() = inventoryViewModel.productsPageSize
    val pagedProducts: StateFlow<List<ProductEntity>> get() = inventoryViewModel.pagedProducts
    val filteredPagedProducts: StateFlow<List<ProductEntity>> get() = inventoryViewModel.filteredPagedProducts
    val filteredProductsCount: StateFlow<Int> get() = inventoryViewModel.filteredProductsCount
    val totalProductsCount: StateFlow<Int> get() = inventoryViewModel.totalProductsCount
    val hasMoreFilteredProducts: StateFlow<Boolean> get() = inventoryViewModel.hasMoreFilteredProducts
    val lowStockProducts: StateFlow<List<ProductEntity>> get() = inventoryViewModel.lowStockProducts
    val pendingLowStockFilter: StateFlow<Boolean> get() = inventoryViewModel.pendingLowStockFilter
    val stockMovements: StateFlow<List<StockMovementEntity>> get() = inventoryViewModel.stockMovements
    val stockMovementsPage: StateFlow<Int> get() = inventoryViewModel.stockMovementsPage
    val stockMovementsPageSize: Int get() = inventoryViewModel.stockMovementsPageSize
    val pagedStockMovements: StateFlow<List<StockMovementEntity>> get() = inventoryViewModel.pagedStockMovements
    val recentStockMovements: StateFlow<List<StockMovementEntity>> get() = inventoryViewModel.recentStockMovements
    val stockMovementsCount: StateFlow<Int> get() = inventoryViewModel.stockMovementsCount

    fun nextProductsPage() = inventoryViewModel.nextProductsPage()
    fun prevProductsPage() = inventoryViewModel.prevProductsPage()
    fun resetProductsPage() = inventoryViewModel.resetProductsPage()
    fun setProductSearchQuery(query: String) = inventoryViewModel.setProductSearchQuery(query)
    fun setProductCategoryFilter(categoryId: Long?) = inventoryViewModel.setProductCategoryFilter(categoryId)
    fun loadMoreFilteredProducts(step: Int = 60) = inventoryViewModel.loadMoreFilteredProducts(step)
    fun resetFilteredProductsWindow() = inventoryViewModel.resetFilteredProductsWindow()
    fun requestOpenLowStockFilter() = inventoryViewModel.requestOpenLowStockFilter()
    fun consumePendingLowStockFilter(): Boolean = inventoryViewModel.consumePendingLowStockFilter()
    fun nextStockMovementsPage() = inventoryViewModel.nextStockMovementsPage()
    fun prevStockMovementsPage() = inventoryViewModel.prevStockMovementsPage()
    fun resetStockMovementsPage() = inventoryViewModel.resetStockMovementsPage()
    fun addNewCategory(name: String, imageUri: String? = null) = inventoryViewModel.addNewCategory(name, imageUri)
    fun updateCategory(id: Long, name: String, imageUri: String? = null) = inventoryViewModel.updateCategory(id, name, imageUri)
    fun updateCategoryImage(id: Long, imageUri: String?) = inventoryViewModel.updateCategoryImage(id, imageUri)
    fun updateProductImage(id: Long, imageUri: String?) = inventoryViewModel.updateProductImage(id, imageUri)
    fun deleteCategory(category: CategoryEntity) {
        if (selectedCategoryIdForFilter.value == category.id) selectCategoryFilter(null)
        if (inventoryViewModel.productCategoryFilter.value == category.id) setProductCategoryFilter(null)
        inventoryViewModel.deleteCategory(category)
    }
    fun seedDefaultCategories() = inventoryViewModel.seedDefaultCategories()
    fun saveProduct(
        id: Long = 0, barcode: String, name: String, description: String, categoryId: Long,
        categoryName: String, unit: String, purchasePrice: Double, sellPrice: Double,
        wholesalePrice: Double, specialPrice: Double, currentStock: Double, minStock: Double, imageUri: String? = null
    ) = inventoryViewModel.saveProduct(
        id, barcode, name, description, categoryId, categoryName, unit,
        purchasePrice, sellPrice, wholesalePrice, specialPrice, currentStock, minStock, imageUri
    )
    fun deleteProduct(product: ProductEntity) { inventoryViewModel.deleteProduct(product) }
    fun deleteProductsBatch(productIds: List<Long>, onComplete: (Int) -> Unit = {}) = inventoryViewModel.deleteProductsBatch(productIds, onComplete)
    fun importProductsFromExcel(rows: List<ProductExcelManager.ProductExcelRow>, updateExisting: Boolean, onResult: (PosRepository.ImportProductsResult) -> Unit) =
        inventoryViewModel.importProductsFromExcel(rows, updateExisting, onResult)

    // --- CUSTOMERS, SUPPLIERS, PAYMENTS & EXPENSES ---
    val customers: StateFlow<List<CustomerEntity>> get() = debtLedgerViewModel.customers
    val filteredPagedCustomers: StateFlow<List<CustomerEntity>> get() = debtLedgerViewModel.filteredPagedCustomers
    val filteredCustomersCount: StateFlow<Int> get() = debtLedgerViewModel.filteredCustomersCount
    val suppliers: StateFlow<List<SupplierEntity>> get() = debtLedgerViewModel.suppliers
    val customerPayments: StateFlow<List<CustomerPaymentEntity>> get() = debtLedgerViewModel.customerPayments
    val pagedCustomerPayments: StateFlow<List<CustomerPaymentEntity>> get() = debtLedgerViewModel.pagedCustomerPayments
    val supplierPayments: StateFlow<List<SupplierPaymentEntity>> get() = debtLedgerViewModel.supplierPayments
    val pagedSupplierPayments: StateFlow<List<SupplierPaymentEntity>> get() = debtLedgerViewModel.pagedSupplierPayments
    val expenses: StateFlow<List<ExpenseEntity>> get() = debtLedgerViewModel.expenses

    fun setCustomerSearchQuery(query: String) = debtLedgerViewModel.setCustomerSearchQuery(query)
    fun loadMoreCustomers(step: Int = 60) = debtLedgerViewModel.loadMoreCustomers(step)
    fun loadMoreCustomerPayments(step: Int = 80) = debtLedgerViewModel.loadMoreCustomerPayments(step)
    fun loadMoreSupplierPayments(step: Int = 80) = debtLedgerViewModel.loadMoreSupplierPayments(step)
    fun paySupplierDebt(supplierId: Long, amount: Double, paymentMethod: String = "CASH", notes: String = "") =
        recordSupplierPayment(supplierId, amount, paymentMethod, notes)
    fun payCustomerDebt(customerId: Long, amount: Double, paymentMethod: String = "CASH", notes: String = "") =
        recordCustomerPayment(customerId, amount, paymentMethod, notes)
    fun recordCustomerPayment(customerId: Long, amount: Double, paymentMethod: String = "CASH", notes: String = "", onComplete: (CustomerPaymentEntity?) -> Unit = {}) =
        debtLedgerViewModel.recordCustomerPayment(customerId, amount, paymentMethod, notes, currentUser.value?.username.orEmpty(), onComplete)
    fun deleteCustomerPayment(payment: CustomerPaymentEntity) = debtLedgerViewModel.deleteCustomerPayment(payment)
    fun updateCustomerPayment(paymentId: Long, newAmount: Double, newPaymentMethod: String = "CASH", notes: String = "", onComplete: () -> Unit = {}) =
        debtLedgerViewModel.updateCustomerPayment(paymentId, newAmount, newPaymentMethod, notes, onComplete)
    fun recordSupplierPayment(supplierId: Long, amount: Double, paymentMethod: String = "CASH", notes: String = "", onComplete: (SupplierPaymentEntity?) -> Unit = {}) =
        debtLedgerViewModel.recordSupplierPayment(supplierId, amount, paymentMethod, notes, currentUser.value?.username.orEmpty(), onComplete)
    fun deleteSupplierPayment(payment: SupplierPaymentEntity) = debtLedgerViewModel.deleteSupplierPayment(payment)
    fun updateSupplierPayment(paymentId: Long, newAmount: Double, newPaymentMethod: String = "CASH", notes: String = "", onComplete: () -> Unit = {}) =
        debtLedgerViewModel.updateSupplierPayment(paymentId, newAmount, newPaymentMethod, notes, onComplete)
    fun saveCustomer(
        id: Long = 0, name: String, phone: String, address: String, balance: Double,
        latitude: Double? = null, longitude: Double? = null, geoRadiusMeters: Double = 150.0,
        isGeoLocationRequired: Boolean = false, customerNumber: Long = 0L, barcode: String = "",
        onResult: ((Boolean, String) -> Unit)? = null
    ) = debtLedgerViewModel.saveCustomer(
        id, name, phone, address, balance, latitude, longitude, geoRadiusMeters,
        isGeoLocationRequired, customerNumber, barcode, currentUser.value, onResult
    )
    fun saveCustomer(customer: CustomerEntity) = debtLedgerViewModel.saveCustomer(customer, currentUser.value)
    fun deleteCustomer(customer: CustomerEntity, onResult: (Boolean, String) -> Unit = { _, _ -> }) = debtLedgerViewModel.deleteCustomer(customer, onResult)
    fun deleteCustomersBatch(customerIds: List<Long>, onResult: (Int, List<String>) -> Unit) = debtLedgerViewModel.deleteCustomersBatch(customerIds, onResult)
    suspend fun findCustomerByBarcode(barcode: String): CustomerEntity? = debtLedgerViewModel.findCustomerByBarcode(barcode)
    suspend fun verifyCustomerLocationForCurrentSale(context: Context, lang: String = "AR"): GeoFenceResult =
        debtLedgerViewModel.verifyCustomerLocation(context, selectedPartnerClientId.value, currentUser.value, lang)
    fun saveSupplier(id: Long = 0, name: String, phone: String, email: String, address: String, debt: Double) =
        debtLedgerViewModel.saveSupplier(id, name, phone, email, address, debt)
    fun saveSupplier(supplier: SupplierEntity) = debtLedgerViewModel.saveSupplier(supplier)
    fun deleteSupplier(supplier: SupplierEntity, onResult: (Boolean, String) -> Unit = { _, _ -> }) = debtLedgerViewModel.deleteSupplier(supplier, onResult)
    fun saveExpense(title: String, amount: Double, category: String) = debtLedgerViewModel.saveExpense(title, amount, category)
    fun deleteExpense(expense: ExpenseEntity) = debtLedgerViewModel.deleteExpense(expense)

    // --- SALES, PURCHASES, ORDERS & TRANSACTIONS ---
    val sales: StateFlow<List<SaleEntity>> get() = salesViewModel.sales
    val recentSales: StateFlow<List<SaleEntity>> get() = salesViewModel.recentSales
    val activeSalesCount: StateFlow<Int> get() = salesViewModel.activeSalesCount
    val totalSalesSum: StateFlow<Double> get() = salesViewModel.totalSalesSum
    val salesPage: StateFlow<Int> get() = salesViewModel.salesPage
    val salesPageSize: Int get() = salesViewModel.salesPageSize
    val pagedSales: StateFlow<List<SaleEntity>> get() = salesViewModel.pagedSales
    val filteredPagedSales: StateFlow<List<SaleEntity>> get() = salesViewModel.filteredPagedSales
    val filteredSalesSummary: StateFlow<SalesLedgerSqlSummary> get() = salesViewModel.filteredSalesSummary
    val salesCountBySeller: StateFlow<List<SellerSalesCountRecord>> get() = salesViewModel.salesCountBySeller
    val productSalesHistory: StateFlow<List<ProductSaleHistoryRecord>> get() = salesViewModel.productSalesHistory
    val purchases: StateFlow<List<PurchaseEntity>> get() = salesViewModel.purchases
    val filteredPagedPurchases: StateFlow<List<PurchaseEntity>> get() = salesViewModel.filteredPagedPurchases
    val filteredPurchasesSummary: StateFlow<PurchaseLedgerSqlSummary> get() = salesViewModel.filteredPurchasesSummary
    val purchaseItems: StateFlow<List<PurchaseItemEntity>> get() = salesViewModel.purchaseItems
    val orders: StateFlow<List<OrderEntity>> get() = salesViewModel.orders
    val ordersWithItems: StateFlow<List<OrderWithItems>> get() = salesViewModel.ordersWithItems
    val transactions: StateFlow<List<TransactionEntity>> get() = salesViewModel.transactions
    val transactionsWithItems: StateFlow<List<TransactionWithItems>> get() = salesViewModel.transactionsWithItems
    val unsyncedTransactions: StateFlow<List<TransactionEntity>> get() = salesViewModel.unsyncedTransactions
    val latestOperationError: StateFlow<PosOperationError?> get() = salesViewModel.latestOperationError
    val latestOperationSuccess: StateFlow<String?> get() = salesViewModel.latestOperationSuccess

    fun clearOperationError() = salesViewModel.clearOperationError()
    fun clearOperationSuccess() = salesViewModel.clearOperationSuccess()
    fun getSalesForCustomerPaged(customerId: Long, limit: Int = 20, offset: Int = 0): Flow<List<SaleEntity>> =
        salesViewModel.getCustomerSalesPaged(customerId, limit, offset)
    fun setSalesFilters(query: String? = null, statusFilter: String? = null, seller: String? = null) = salesViewModel.setSalesFilters(query, statusFilter, seller)
    fun loadMoreSales(step: Int = 80) = salesViewModel.loadMoreSales(step)
    fun setPurchasesFilters(query: String? = null, statusFilter: String? = null) = salesViewModel.setPurchasesFilters(query, statusFilter)
    fun loadMorePurchases(step: Int = 80) = salesViewModel.loadMorePurchases(step)
    fun nextSalesPage() = salesViewModel.nextSalesPage()
    fun prevSalesPage() = salesViewModel.prevSalesPage()
    fun resetSalesPage() = salesViewModel.resetSalesPage()
    fun deleteSale(saleId: Long, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.deleteSaleInvoice(saleId, currentUser.value, currentLanguage.value, onResult)
    fun deletePurchase(purchaseId: Long, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.deletePurchase(purchaseId, currentUser.value, currentLanguage.value, onResult) { logError("Failed to delete purchase invoice", it) }
    fun voidSale(sale: SaleEntity, onResult: (PosOperationResult<Unit>) -> Unit = {}) = salesViewModel.voidSale(sale.id, currentLanguage.value, onResult)
    fun voidPurchase(purchase: PurchaseEntity, onResult: (PosOperationResult<Unit>) -> Unit = {}) = salesViewModel.voidPurchase(purchase, currentLanguage.value, onResult)
    fun updateSale(sale: SaleEntity, items: List<SaleItemEntity>, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.updateSale(sale, items, currentLanguage.value, onResult)
    fun updatePurchase(purchase: PurchaseEntity, items: List<PurchaseItemEntity>, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.updatePurchase(purchase, items, currentLanguage.value, onResult)
    fun updateSaleDate(saleId: Long, newTimestamp: Long, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.updateSaleDate(saleId, newTimestamp, currentLanguage.value, onResult)
    fun updatePurchaseDate(purchaseId: Long, newTimestamp: Long, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.updatePurchaseDate(purchaseId, newTimestamp, currentLanguage.value, onResult)
    fun updateSaleInvoiceNumber(saleId: Long, newInvoiceNumber: String, onOperationResult: (PosOperationResult<Unit>) -> Unit = {}, onResult: (Boolean) -> Unit = {}) =
        salesViewModel.updateSaleInvoiceNumber(saleId, newInvoiceNumber, currentLanguage.value, onOperationResult, onResult)
    fun updatePurchaseInvoiceNumber(purchaseId: Long, newInvoiceNumber: String, onOperationResult: (PosOperationResult<Unit>) -> Unit = {}, onResult: (Boolean) -> Unit = {}) =
        salesViewModel.updatePurchaseInvoiceNumber(purchaseId, newInvoiceNumber, currentLanguage.value, onOperationResult, onResult)
    fun updateSaleCustomer(saleId: Long, newCustomerId: Long, newCustomerName: String, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.updateSaleCustomer(saleId, newCustomerId, newCustomerName, currentLanguage.value, onResult)
    fun updatePurchaseSupplier(purchaseId: Long, newSupplierId: Long, newSupplierName: String, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.updatePurchaseSupplier(purchaseId, newSupplierId, newSupplierName, currentLanguage.value, onResult)
    suspend fun getSaleItems(saleId: Long): List<SaleItemEntity> = salesViewModel.getSaleItems(saleId)
    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItemEntity> = salesViewModel.getPurchaseItems(purchaseId)
    fun createOrder(order: OrderEntity, items: List<OrderItemEntity>, onOperationResult: (PosOperationResult<OrderEntity>) -> Unit = {}, onComplete: (OrderEntity) -> Unit = {}) =
        salesViewModel.createOrder(order, items, currentLanguage.value, onOperationResult, onComplete)
    fun voidOrder(orderId: Long, onResult: (PosOperationResult<Unit>) -> Unit = {}) = salesViewModel.voidOrder(orderId, currentLanguage.value, onResult)
    suspend fun getReturnedQuantitiesForSale(saleId: Long): Map<Long, Double> =
        salesViewModel.getReturnedQuantitiesForSale(saleId)
    fun recordCustomerReturn(saleId: Long, productId: Long, productName: String, quantity: Double, refundAmount: Double? = null, isCreditRefund: Boolean, customerId: Long, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.recordCustomerReturn(saleId, productId, productName, quantity, refundAmount, isCreditRefund, customerId, currentLanguage.value, onResult)
    fun recordSupplierReturn(purchaseId: Long, productId: Long, productName: String, quantity: Double, returnAmount: Double, supplierId: Long, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.recordSupplierReturn(purchaseId, productId, productName, quantity, returnAmount, supplierId, currentLanguage.value, onResult)
    fun settleSaleInvoice(
        sale: SaleEntity,
        amount: Double? = null,
        paymentMethod: String = "CASH",
        notes: String = "",
        onResult: (PosOperationResult<Unit>) -> Unit = {}
    ) = salesViewModel.settleSaleInvoice(sale, amount, currentUser.value?.username.orEmpty(), currentLanguage.value, paymentMethod, notes, onResult)
    fun markSaleAsDebt(sale: SaleEntity, onResult: (PosOperationResult<Unit>) -> Unit = {}) = salesViewModel.markSaleAsDebt(sale, currentLanguage.value, onResult)
    fun settlePurchaseInvoice(
        purchase: PurchaseEntity,
        amount: Double? = null,
        paymentMethod: String = "CASH",
        notes: String = "",
        onResult: (PosOperationResult<Unit>) -> Unit = {}
    ) = salesViewModel.settlePurchaseInvoice(purchase, amount, currentUser.value?.username.orEmpty(), currentLanguage.value, paymentMethod, notes, onResult)
    fun markPurchaseAsDebt(purchase: PurchaseEntity, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.markPurchaseAsDebt(purchase, currentLanguage.value, onResult)
    fun recordSupplierInvoiceBulk(purchase: PurchaseEntity, items: List<PurchaseItemEntity>, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.recordSupplierInvoiceBulk(purchase, items, currentUser.value, currentLanguage.value, onResult)
    fun recordSupplierInvoice(supplierId: Long, supplierName: String, productId: Long, productName: String, qty: Double, cost: Double, paid: Double, onResult: (PosOperationResult<Unit>) -> Unit = {}) =
        salesViewModel.recordSupplierInvoice(supplierId, supplierName, productId, productName, qty, cost, paid, currentUser.value, currentLanguage.value, onResult)

    // --- ANALYTICS, USERS & AUDIT ---
    val users: StateFlow<List<UserEntity>> get() = analyticsDashboardViewModel.users
    val auditLogs: StateFlow<List<AuditLogEntity>> get() = analyticsDashboardViewModel.auditLogs
    val profitTodaySum: StateFlow<Double?> get() = analyticsDashboardViewModel.profitTodaySum
    val salesTodaySum: StateFlow<Double?> get() = analyticsDashboardViewModel.salesTodaySum
    val invoiceTodayCount: StateFlow<Int?> get() = analyticsDashboardViewModel.invoiceTodayCount
    val cashTodaySum: StateFlow<Double?> get() = analyticsDashboardViewModel.cashTodaySum
    val cardTodaySum: StateFlow<Double?> get() = analyticsDashboardViewModel.cardTodaySum
    val expensesTodaySum: StateFlow<Double?> get() = analyticsDashboardViewModel.expensesTodaySum
    val lowStockAlertCount: StateFlow<Int?> get() = analyticsDashboardViewModel.lowStockAlertCount
    val topProducts: StateFlow<List<TopProductRecord>> get() = analyticsDashboardViewModel.topProducts
    fun saveUser(user: UserEntity) = analyticsDashboardViewModel.saveUser(user, currentUser.value) { savedUser, oldUsername ->
        authSecurityViewModel.syncCurrentUser(savedUser, oldUsername)
    }
    fun deleteUser(user: UserEntity) = analyticsDashboardViewModel.deleteUser(user, currentUser.value)

    // --- CART & CHECKOUT ---
    val cart: StateFlow<List<CartItem>> get() = cartViewModel.cart
    val persistentCartItems: StateFlow<List<CartItemEntity>> get() = cartViewModel.persistentCartItems
    val searchQuery: StateFlow<String> get() = cartViewModel.searchQuery
    val selectedCategoryIdForFilter: StateFlow<Long?> get() = cartViewModel.selectedCategoryIdForFilter
    val globalDiscountType: MutableStateFlow<String> get() = cartViewModel.globalDiscountType
    val globalDiscountValue: MutableStateFlow<Double> get() = cartViewModel.globalDiscountValue
    val selectedPaymentMethod: MutableStateFlow<String> get() = cartViewModel.selectedPaymentMethod
    val receivedCash: MutableStateFlow<Double> get() = cartViewModel.receivedCash
    val selectedPartnerClientId: MutableStateFlow<Long> get() = cartViewModel.selectedPartnerClientId
    val printedPurchase: StateFlow<PurchaseEntity?> get() = cartViewModel.printedPurchase
    val printedPurchaseItems: StateFlow<List<PurchaseItemEntity>> get() = cartViewModel.printedPurchaseItems
    val printedReceipt: StateFlow<SaleEntity?> get() = cartViewModel.printedReceipt
    val printedReceiptItems: StateFlow<List<SaleItemEntity>> get() = cartViewModel.printedReceiptItems
    val cartTotalBeforeGlobalDiscountCents: StateFlow<Long> get() = cartViewModel.cartTotalBeforeGlobalDiscountCents
    val cartTotalBeforeGlobalDiscount: StateFlow<Double> get() = cartViewModel.cartTotalBeforeGlobalDiscount
    val cartTotalAfterGlobalDiscountCents: StateFlow<Long> get() = cartViewModel.cartTotalAfterGlobalDiscountCents
    val cartTotalAfterGlobalDiscount: StateFlow<Double> get() = cartViewModel.cartTotalAfterGlobalDiscount
    val scanFeedbackMessage: StateFlow<String?> get() = cartViewModel.scanFeedbackMessage
    val unrecognizedScannedBarcode: StateFlow<String?> get() = cartViewModel.unrecognizedScannedBarcode
    val suspendedSales: StateFlow<List<SuspendedSale>> get() = cartViewModel.suspendedSales
    private val _isDrawerToastVisible = MutableStateFlow(false)
    val isDrawerToastVisible: StateFlow<Boolean> = _isDrawerToastVisible.asStateFlow()

    fun updateSearchQuery(query: String) = cartViewModel.updateSearchQuery(query)
    fun selectCategoryFilter(catId: Long?) = cartViewModel.selectCategoryFilter(catId)
    fun showReceiptDetails(sale: SaleEntity) = cartViewModel.showReceiptDetails(sale)
    fun showPurchaseDetails(purchase: PurchaseEntity) = cartViewModel.showPurchaseDetails(purchase)
    fun dismissPurchase() = cartViewModel.dismissPurchase()
    fun dismissPurchaseReceipt() = cartViewModel.dismissPurchase()
    fun dismissReceipt() = cartViewModel.dismissReceipt()
    fun addToCart(product: ProductEntity) = cartViewModel.addToCart(product)
    fun addOrUpdateCartItem(
        product: ProductEntity,
        quantity: Double = 1.0,
        customPrice: Double? = null,
        replaceExisting: Boolean = false
    ) = cartViewModel.addOrUpdateCartItem(product, quantity, customPrice, replaceExisting)
    fun addReturnToCart(product: ProductEntity, quantity: Double = -1.0) = cartViewModel.addReturnToCart(product, quantity)
    fun toggleCartItemReturn(productId: Long) = cartViewModel.toggleCartItemReturn(productId)
    fun decrementCartQuantity(productId: Long) = cartViewModel.decrementCartQuantity(productId)
    fun updateCartQuantity(productId: Long, qty: Double) = cartViewModel.updateCartQuantity(productId, qty)
    fun updateCartPrice(productId: Long, price: Double) = cartViewModel.updateCartPrice(productId, price)
    fun updateCartLineDiscount(productId: Long, discount: Double) = cartViewModel.updateCartLineDiscount(productId, discount)
    fun removeFromCart(productId: Long) = cartViewModel.removeFromCart(productId)
    fun clearCart() = cartViewModel.clearCart()
    fun clearUnrecognizedBarcode() = cartViewModel.clearUnrecognizedBarcode()
    fun onBarcodeScanned(barcode: String, onResult: (found: Boolean, name: String) -> Unit = { _, _ -> }) = cartViewModel.onBarcodeScanned(barcode, onResult)
    fun simulateScannerInput(barcode: String) = onBarcodeScanned(barcode)
    fun suspendCurrentSale() = cartViewModel.suspendCurrentSale()
    fun recallSuspendedSale(index: Int) = cartViewModel.recallSuspendedSale(index)
    fun simulateOpenDrawer(context: Context? = null) {}
    fun beep() {}

    fun executeCheckout(context: Context? = null, onError: (String) -> Unit = {}, onComplete: () -> Unit) =
        cartViewModel.executeCheckout(
            getApplication(), currentUser.value, currentLanguage.value, settings.value,
            printerConnectionType.value, printerName.value, printerMac.value, printerIp.value,
            printerPort.value, printerPaperWidth.value, autoPrintReceipt.value, context, onError, onComplete,
            printProfile = settingsHardwareViewModel.buildActivePrintProfile()
        )
    fun executeCheckout(context: Context? = null, onComplete: () -> Unit) = executeCheckout(
        context = context,
        onError = { err -> context?.let { Toast.makeText(it, err, Toast.LENGTH_LONG).show() } },
        onComplete = onComplete
    )
    fun printCheckoutTransactionSummary(
        context: Context, subtotal: Double, discountAmount: Double, netTotal: Double,
        paymentMethod: String, receivedAmount: Double, changeDue: Double,
        isDraftSummary: Boolean = true, invoiceNumberOverride: String? = null,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) = cartViewModel.printCheckoutTransactionSummary(
        context, currentUser.value, currentLanguage.value, settings.value, subtotal, discountAmount,
        netTotal, paymentMethod, receivedAmount, changeDue, printerConnectionType.value,
        printerName.value, printerMac.value, printerIp.value, printerPort.value,
        printerPaperWidth.value, isDraftSummary, invoiceNumberOverride, onResult,
        printProfile = settingsHardwareViewModel.buildActivePrintProfile()
    )

    // --- BACKUP, RESTORE, AUTO-BACKUP & WIPE ---
    val backupStatus: StateFlow<String?> get() = backupSyncViewModel.backupStatus
    val linkedEmail: StateFlow<String> get() = backupSyncViewModel.linkedEmail
    val linkedPhone: StateFlow<String> get() = backupSyncViewModel.linkedPhone
    val isCloudSyncEnabled: StateFlow<Boolean> get() = backupSyncViewModel.isCloudSyncEnabled
    val lastBackupTime: StateFlow<Long> get() = backupSyncViewModel.lastBackupTime
    val lastBackupFileName: StateFlow<String> get() = backupSyncViewModel.lastBackupFileName
    val driveConnected: StateFlow<Boolean> get() = backupSyncViewModel.driveConnected
    val autoBackupEnabled: StateFlow<Boolean> get() = backupSyncViewModel.autoBackupEnabled
    val autoBackupFrequency: StateFlow<String> get() = backupSyncViewModel.autoBackupFrequency
    val lastAutoBackupTime: StateFlow<Long> get() = backupSyncViewModel.lastAutoBackupTime
    val hasExternalBackupFolder: StateFlow<Boolean> get() = backupSyncViewModel.hasExternalBackupFolder
    val externalBackupFolderLabel: StateFlow<String> get() = backupSyncViewModel.externalBackupFolderLabel
    val lastExternalAutoBackupTime: StateFlow<Long> get() = backupSyncViewModel.lastExternalAutoBackupTime
    val lastExternalAutoBackupError: StateFlow<String?> get() = backupSyncViewModel.lastExternalAutoBackupError
    val lastExternalExportTime: StateFlow<Long> get() = backupSyncViewModel.lastExternalExportTime
    val isExternalExportReminderDue: StateFlow<Boolean> get() = backupSyncViewModel.isExternalExportReminderDue
    val daysSinceLastExternalExport: StateFlow<Int> get() = backupSyncViewModel.daysSinceLastExternalExport

    fun updateLinkedAccount(email: String, phone: String) = backupSyncViewModel.updateLinkedAccount(email, phone)
    fun toggleCloudSync(enabled: Boolean) = backupSyncViewModel.toggleCloudSync(enabled)
    fun configureExternalBackupFolder(treeUri: android.net.Uri): Boolean = backupSyncViewModel.configureExternalBackupFolder(treeUri)
    fun clearExternalBackupFolder() = backupSyncViewModel.clearExternalBackupFolder()
    fun performGoogleDriveBackup(encryptionPassword: String, onReadyToShare: ((File) -> Unit)? = null, onComplete: ((Boolean, String) -> Unit)? = null) =
        backupSyncViewModel.performGoogleDriveBackup(encryptionPassword, onReadyToShare, onComplete)
    fun restoreFromJson(backupString: String, decryptionPassword: String? = null, onResult: (RestoreResult) -> Unit) =
        backupSyncViewModel.restoreFromJson(backupString, decryptionPassword, currentUser.value, onResult)
    fun createLocalBackup(encryptionPassword: String) = backupSyncViewModel.createLocalBackup(encryptionPassword)
    fun wipeAllEntitiesData(onComplete: () -> Unit = {}) = backupSyncViewModel.wipeAllEntitiesData(currentUser.value, onComplete)
    fun updateAutoBackupConfig(enabled: Boolean, freq: String) = backupSyncViewModel.updateAutoBackupConfig(enabled, freq)
    fun performAutoBackup(context: Context, onComplete: ((Boolean, String) -> Unit)? = null) = backupSyncViewModel.performAutoBackup(context, onComplete)
    fun markExternalExportCompleted() = backupSyncViewModel.markExternalExportCompleted()
    fun dismissExternalExportReminderForToday() = backupSyncViewModel.dismissExternalExportReminderForToday()
    fun refreshExternalExportReminder() = backupSyncViewModel.refreshExternalExportReminder()

    companion object {
        private const val TAG = "PosViewModel"
        private fun logError(message: String, throwable: Throwable? = null) { Log.e(TAG, message, throwable) }
    }
}
