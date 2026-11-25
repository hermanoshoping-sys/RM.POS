package com.example.printer

import android.content.Context
import android.util.Log
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.ui.theme.Bilingual
import com.example.util.DocumentDesignHelper
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Global provider for the dedicated [PosThermalPrinterService] so every RM.POS module
 * (Sales, Purchases, Receipts, Reports, Settings) reuses a single printer service instance.
 */
object PosThermalPrinterServiceProvider {
    @Volatile
    var instance: PosThermalPrinterService = DefaultPosThermalPrinterService
}

/**
 * Production implementation of [PosThermalPrinterService].
 * Coordinates Bluetooth SPP, Android USB Host (OTG), Wi-Fi TCP 9100, and Android System Print
 * with 58mm/80mm formatting, Arabic RTL / French LTR rendering, automatic retries, and print job history.
 */
object DefaultPosThermalPrinterService : PosThermalPrinterService {
    private const val TAG = "PosThermalPrinterSvc"

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** Optional hook for deterministic JVM/Robolectric tests without touching physical sockets/USB hardware. */
    internal var transportSendOverrideForTest: (suspend (
        context: Context,
        target: ThermalPrinterDevice,
        bytes: ByteArray,
        jobTitle: String,
        lang: String
    ) -> Pair<Boolean, String>)? = null

    private val _connectionStatus = MutableStateFlow<PrinterConnectionStatus>(
        PrinterConnectionStatus.Disconnected()
    )
    override val connectionStatus: StateFlow<PrinterConnectionStatus> = _connectionStatus.asStateFlow()

    private val _activePrinter = MutableStateFlow<ThermalPrinterDevice?>(null)
    override val activePrinter: StateFlow<ThermalPrinterDevice?> = _activePrinter.asStateFlow()

    private val _discoveredPrinters = MutableStateFlow<List<ThermalPrinterDevice>>(emptyList())
    override val discoveredPrinters: StateFlow<List<ThermalPrinterDevice>> = _discoveredPrinters.asStateFlow()

    override val printHistory: StateFlow<List<PrintJobRecord>> = PrintJobHistoryStore.history

    override suspend fun discoverPrinters(
        context: Context,
        transport: PrinterTransport?
    ): List<ThermalPrinterDevice> = withContext(ioDispatcher) {
        PrintJobHistoryStore.ensureLoaded(context)
        val previousStatus = _connectionStatus.value
        _connectionStatus.value = PrinterConnectionStatus.Discovering(transport ?: PrinterTransport.BLUETOOTH)

        val discovered = mutableListOf<ThermalPrinterDevice>()

        if (transport == null || transport == PrinterTransport.BLUETOOTH) {
            val pairedBt = BluetoothPrinterConnectionManager.getPairedDevicesSafe(context)
            discovered.addAll(
                pairedBt.map { bt ->
                    ThermalPrinterDevice(
                        id = bt.address,
                        name = bt.name.ifBlank { bt.address },
                        address = bt.address,
                        transport = PrinterTransport.BLUETOOTH,
                        isPairedOrAuthorized = bt.isBonded,
                        details = "Bluetooth SPP • ${bt.address}"
                    )
                }
            )
        }

        if (transport == null || transport == PrinterTransport.USB) {
            val usbDevices = UsbPrinterConnectionManager.discoverUsbPrinters(context)
            discovered.addAll(usbDevices)
        }

        _discoveredPrinters.value = discovered
        if (_connectionStatus.value is PrinterConnectionStatus.Discovering) {
            _connectionStatus.value = previousStatus
        }
        discovered
    }

    override suspend fun connect(
        context: Context,
        device: ThermalPrinterDevice,
        lang: String
    ): Result<ThermalPrinterDevice> = withContext(ioDispatcher) {
        PrintJobHistoryStore.ensureLoaded(context)
        _connectionStatus.value = PrinterConnectionStatus.Connecting(device)

        when (device.transport) {
            PrinterTransport.BLUETOOTH -> {
                val res = BluetoothPrinterConnectionManager.connect(
                    context = context,
                    deviceAddressOrName = device.address.ifBlank { device.name },
                    lang = lang
                )
                res.fold(
                    onSuccess = { btInfo ->
                        val connected = device.copy(
                            id = btInfo.address,
                            name = btInfo.name.ifBlank { device.name },
                            address = btInfo.address,
                            isPairedOrAuthorized = btInfo.isBonded
                        )
                        _activePrinter.value = connected
                        _connectionStatus.value = PrinterConnectionStatus.Connected(connected, System.currentTimeMillis())
                        Result.success(connected)
                    },
                    onFailure = { err ->
                        val msg = err.localizedMessage ?: Bilingual.tr(
                            lang,
                            "فشل الاتصال بطابعة البلوتوث",
                            "Échec de connexion Bluetooth",
                            "Bluetooth printer connection failed"
                        )
                        _connectionStatus.value = PrinterConnectionStatus.Error(msg, device)
                        Result.failure(err)
                    }
                )
            }

            PrinterTransport.USB -> {
                val res = UsbPrinterConnectionManager.connect(
                    context = context,
                    deviceAddressOrName = device.address.ifBlank { device.name },
                    lang = lang
                )
                res.fold(
                    onSuccess = { usbDev ->
                        _activePrinter.value = usbDev
                        _connectionStatus.value = PrinterConnectionStatus.Connected(usbDev, System.currentTimeMillis())
                        Result.success(usbDev)
                    },
                    onFailure = { err ->
                        val msg = err.localizedMessage ?: Bilingual.tr(
                            lang,
                            "فشل الاتصال بطابعة USB",
                            "Échec de connexion USB",
                            "USB printer connection failed"
                        )
                        _connectionStatus.value = PrinterConnectionStatus.Error(msg, device)
                        Result.failure(err)
                    }
                )
            }

            PrinterTransport.WIFI -> {
                val trimmedIp = device.address.trim()
                if (trimmedIp.isBlank() || device.port !in 1..65535) {
                    val diag = WifiNetworkSocketHelper.diagnoseWifiFailure(context, trimmedIp, device.port, null, lang)
                    _connectionStatus.value = PrinterConnectionStatus.Error(diag.message, device)
                    return@withContext Result.failure(IllegalArgumentException(diag.message))
                }
                if (device.port == 631) {
                    val diag = WifiNetworkSocketHelper.diagnoseWifiFailure(context, trimmedIp, device.port, null, lang)
                    _connectionStatus.value = PrinterConnectionStatus.Error(diag.message, device)
                    return@withContext Result.failure(IllegalStateException(diag.message))
                }
                try {
                    WifiNetworkSocketHelper.openSocket(
                        context = context,
                        ip = trimmedIp,
                        port = device.port,
                        connectTimeoutMs = WifiNetworkSocketHelper.DEFAULT_CONNECT_TIMEOUT_MS
                    ).use { /* TCP handshake verified */ }
                    _activePrinter.value = device
                    _connectionStatus.value = PrinterConnectionStatus.Connected(device, System.currentTimeMillis())
                    Result.success(device)
                } catch (t: Throwable) {
                    val diag = WifiNetworkSocketHelper.diagnoseWifiFailure(context, trimmedIp, device.port, t, lang)
                    _connectionStatus.value = PrinterConnectionStatus.Error(diag.message, device)
                    Result.failure(IOException(diag.message, t))
                }
            }

            PrinterTransport.SYSTEM -> {
                _activePrinter.value = device
                _connectionStatus.value = PrinterConnectionStatus.Connected(device, System.currentTimeMillis())
                Result.success(device)
            }
        }
    }

    override suspend fun disconnect(reason: String?) = withContext(ioDispatcher) {
        runCatching { BluetoothPrinterConnectionManager.disconnect(reason) }
        runCatching { UsbPrinterConnectionManager.disconnect(reason) }
        _activePrinter.value = null
        _connectionStatus.value = PrinterConnectionStatus.Disconnected(reason)
    }

    override fun formatReceiptBytes(
        receipt: ThermalReceiptData,
        profile: ThermalPrintProfile,
        context: Context?
    ): ByteArray {
        val shouldRaster = profile.useRasterRendering && (profile.isRtl || profile.showLogo)
        if (shouldRaster) {
            val raster = ThermalReceiptRasterFormatter.renderReceiptToEscPosRaster(receipt, profile, context)
            if (raster != null && raster.isNotEmpty()) {
                return raster
            }
        }
        return EscPosReceiptEncoder.generateThermalReceiptBytes(receipt, profile.toLegacyConfig())
    }

    override fun formatPlainTextReceipt(
        receipt: ThermalReceiptData,
        profile: ThermalPrintProfile
    ): String = EscPosReceiptEncoder.generatePlainTextReceipt(receipt, profile.toLegacyConfig())

    override suspend fun printReceipt(
        context: Context,
        receipt: ThermalReceiptData,
        profile: ThermalPrintProfile,
        target: ThermalPrinterDevice?,
        jobTitle: String
    ): PrintExecutionResult = withContext(ioDispatcher) {
        PrintJobHistoryStore.ensureLoaded(context)
        val lang = profile.language.ifBlank { "AR" }.uppercase(Locale.ROOT)
        val resolvedTarget = target ?: _activePrinter.value ?: ThermalPrinterDevice(
            id = "bt_default",
            name = "Thermal Printer",
            address = "",
            transport = PrinterTransport.BLUETOOTH
        )

        val jobId = "PJ-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6).uppercase(Locale.ROOT)}"
        val plainText = formatPlainTextReceipt(receipt, profile)
        val payloadBytes = if (resolvedTarget.transport == PrinterTransport.SYSTEM) {
            plainText.toByteArray(Charsets.UTF_8)
        } else {
            formatReceiptBytes(receipt, profile, context)
        }

        val initialRecord = PrintJobRecord(
            id = jobId,
            timestamp = System.currentTimeMillis(),
            title = jobTitle.ifBlank { receipt.documentTitle ?: "Receipt" },
            invoiceNumber = receipt.invoiceNumber,
            transport = resolvedTarget.transport,
            printerName = resolvedTarget.name.ifBlank { resolvedTarget.address.ifBlank { resolvedTarget.transport.name } },
            printerAddress = resolvedTarget.address,
            paperWidthMm = profile.paperWidth.mm,
            language = lang,
            status = PrintJobStatus.PRINTING,
            attemptCount = 1,
            errorMessage = null,
            byteSize = payloadBytes.size
        )

        val retryPayload = CachedRetryPayload(
            jobRecord = initialRecord,
            bytes = payloadBytes,
            target = resolvedTarget,
            plainTextFallback = plainText
        )
        PrintJobHistoryStore.upsertRecord(context, initialRecord, retryPayload)

        val maxAttempts = (1 + profile.maxRetryAttempts.coerceAtLeast(0)).coerceAtMost(5)
        var lastMessage = ""
        var succeeded = false
        var attemptsUsed = 0

        for (attempt in 1..maxAttempts) {
            attemptsUsed = attempt
            if (attempt == 1) {
                _connectionStatus.value = PrinterConnectionStatus.Printing(resolvedTarget, initialRecord.title)
            } else {
                _connectionStatus.value = PrinterConnectionStatus.Reconnecting(
                    device = resolvedTarget,
                    attempt = attempt - 1,
                    maxAttempts = maxAttempts - 1,
                    reason = lastMessage.takeIf { it.isNotBlank() }
                )
                if (profile.retryDelayMs > 0L) {
                    delay(profile.retryDelayMs)
                }
                _connectionStatus.value = PrinterConnectionStatus.Printing(resolvedTarget, initialRecord.title)
            }

            val (ok, msg, skipRetries) = ThermalTransportDispatcher.dispatchToTransport(
                context = context,
                target = resolvedTarget,
                bytes = payloadBytes,
                plainText = plainText,
                jobTitle = initialRecord.title,
                lang = lang,
                testOverride = transportSendOverrideForTest
            )
            lastMessage = msg
            if (ok) {
                succeeded = true
                break
            }
            Log.w(TAG, "Print attempt $attempt/$maxAttempts failed on ${resolvedTarget.transport}: $msg")
            if (skipRetries) {
                break
            }
        }

        val finalRecord = initialRecord.copy(
            status = if (succeeded) PrintJobStatus.SUCCESS else PrintJobStatus.FAILED,
            attemptCount = attemptsUsed,
            errorMessage = if (succeeded) null else lastMessage
        )
        PrintJobHistoryStore.upsertRecord(context, finalRecord, retryPayload)

        if (succeeded) {
            _activePrinter.value = resolvedTarget
            _connectionStatus.value = PrinterConnectionStatus.Connected(resolvedTarget, System.currentTimeMillis())
        } else {
            _connectionStatus.value = PrinterConnectionStatus.Error(lastMessage, resolvedTarget)
        }

        PrintExecutionResult(
            jobId = jobId,
            success = succeeded,
            message = lastMessage,
            attempts = attemptsUsed,
            errorCause = if (succeeded) null else lastMessage
        )
    }

    override suspend fun printSaleInvoice(
        context: Context,
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity,
        lang: String,
        target: ThermalPrinterDevice?,
        profileOverride: ThermalPrintProfile?
    ): PrintExecutionResult {
        val effectiveLang = lang.ifBlank { settings.language.ifBlank { "AR" } }.uppercase(Locale.ROOT)
        val profile = profileOverride ?: ThermalPrintProfile.fromSettings(
            settings = settings,
            paperWidthMm = settings.printerPaperWidth,
            language = effectiveLang
        )

        val receipt = buildSaleReceiptData(
            sale = sale,
            items = items,
            settings = settings,
            lang = effectiveLang,
            openCashDrawer = profile.openCashDrawer
        )

        val title = Bilingual.tr(
            effectiveLang,
            "فاتورة بيع #${sale.invoiceNumber}",
            "Facture #${sale.invoiceNumber}",
            "Sale Invoice #${sale.invoiceNumber}"
        )

        return printReceipt(
            context = context,
            receipt = receipt,
            profile = profile,
            target = target,
            jobTitle = title
        )
    }

    override suspend fun printTestReceipt(
        context: Context,
        settings: GlobalSettingsEntity,
        profile: ThermalPrintProfile,
        target: ThermalPrinterDevice?
    ): PrintExecutionResult {
        val effectiveLang = profile.language.ifBlank { settings.language.ifBlank { "AR" } }.uppercase(Locale.ROOT)
        val resolvedTarget = target ?: _activePrinter.value
        val (enrichedTestReceipt, jobTitle) = ThermalTransportDispatcher.buildEnrichedTestReceipt(
            resolvedTarget = resolvedTarget,
            settings = settings,
            profile = profile,
            effectiveLang = effectiveLang
        )
        return printReceipt(
            context = context,
            receipt = enrichedTestReceipt,
            profile = profile,
            target = resolvedTarget,
            jobTitle = jobTitle
        )
    }

    override suspend fun retryPrintJob(
        context: Context,
        jobId: String
    ): PrintExecutionResult = withContext(ioDispatcher) {
        PrintJobHistoryStore.ensureLoaded(context)
        val cached = PrintJobHistoryStore.getRetryPayload(jobId)
        if (cached == null) {
            val msg = "لا تتوفر بيانات إعادة المحاولة لهذه العملية في الذاكرة المؤقتة"
            return@withContext PrintExecutionResult(
                jobId = jobId,
                success = false,
                message = msg,
                attempts = 0,
                errorCause = msg
            )
        }

        val record = cached.jobRecord
        val nextAttempt = record.attemptCount + 1
        _connectionStatus.value = PrinterConnectionStatus.Printing(cached.target, record.title)
        PrintJobHistoryStore.upsertRecord(
            context = context,
            record = record.copy(status = PrintJobStatus.PRINTING, attemptCount = nextAttempt, errorMessage = null),
            retryPayload = cached
        )

        val (ok, msg, _) = ThermalTransportDispatcher.dispatchToTransport(
            context = context,
            target = cached.target,
            bytes = cached.bytes,
            plainText = cached.plainTextFallback,
            jobTitle = record.title,
            lang = record.language,
            testOverride = transportSendOverrideForTest
        )

        val updated = record.copy(
            timestamp = System.currentTimeMillis(),
            status = if (ok) PrintJobStatus.SUCCESS else PrintJobStatus.FAILED,
            attemptCount = nextAttempt,
            errorMessage = if (ok) null else msg
        )
        PrintJobHistoryStore.upsertRecord(context, updated, cached)

        if (ok) {
            _activePrinter.value = cached.target
            _connectionStatus.value = PrinterConnectionStatus.Connected(cached.target, System.currentTimeMillis())
        } else {
            _connectionStatus.value = PrinterConnectionStatus.Error(msg, cached.target)
        }

        PrintExecutionResult(
            jobId = jobId,
            success = ok,
            message = msg,
            attempts = nextAttempt,
            errorCause = if (ok) null else msg
        )
    }

    override fun clearPrintHistory(context: Context?) {
        PrintJobHistoryStore.clear(context)
    }

    /**
     * Reusable builder translating a persisted [SaleEntity] + [SaleItemEntity] list into [ThermalReceiptData]
     * without duplicating formatting across POS screens (and intentionally omitting remaining-debt lines).
     */
    fun buildSaleReceiptData(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity,
        lang: String = settings.language,
        openCashDrawer: Boolean = false
    ): ThermalReceiptData = ThermalTransportDispatcher.buildSaleReceiptData(sale, items, settings, lang, openCashDrawer)

    internal fun resetForTesting(context: Context? = null) {
        transportSendOverrideForTest = null
        BluetoothPrinterConnectionManager.resetForTesting()
        UsbPrinterConnectionManager.resetForTesting()
        PrintJobHistoryStore.clear(context)
        _activePrinter.value = null
        _discoveredPrinters.value = emptyList()
        _connectionStatus.value = PrinterConnectionStatus.Disconnected()
    }
}
