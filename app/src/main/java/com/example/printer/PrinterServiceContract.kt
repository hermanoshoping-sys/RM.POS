package com.example.printer

import android.content.Context
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class PrinterTransport {
    BLUETOOTH,
    USB,
    WIFI,
    SYSTEM;

    companion object {
        fun fromString(raw: String?): PrinterTransport = when (raw?.trim()?.uppercase(Locale.ROOT)) {
            "USB", "OTG" -> USB
            "WIFI", "NETWORK", "IP", "WIFI_NETWORK" -> WIFI
            "SYSTEM", "ANDROID", "CABLE", "PDF" -> SYSTEM
            else -> BLUETOOTH
        }
    }
}

enum class ThermalPaperWidth(val mm: Int, val charsPerLine: Int, val dotsWidth: Int) {
    MM_58(58, 32, 384),
    MM_80(80, 48, 576);

    companion object {
        fun fromMm(mm: Int): ThermalPaperWidth = if (mm >= 72) MM_80 else MM_58
    }
}

enum class ThermalFontSize(val scale: Float, val escPosModeByte: Byte) {
    SMALL(0.85f, 0x01),
    NORMAL(1.0f, 0x00),
    LARGE(1.22f, 0x10);

    companion object {
        fun fromString(raw: String?): ThermalFontSize = when (raw?.trim()?.uppercase(Locale.ROOT)) {
            "SMALL" -> SMALL
            "LARGE" -> LARGE
            else -> NORMAL
        }
    }
}

enum class ReceiptTextDirection {
    AUTO,
    RTL,
    LTR;

    fun resolveRtl(language: String): Boolean = when (this) {
        RTL -> true
        LTR -> false
        AUTO -> language.trim().uppercase(Locale.ROOT) == "AR"
    }

    companion object {
        fun fromString(raw: String?): ReceiptTextDirection = when (raw?.trim()?.uppercase(Locale.ROOT)) {
            "RTL" -> RTL
            "LTR" -> LTR
            else -> AUTO
        }
    }
}

enum class PrintJobStatus {
    PENDING,
    PRINTING,
    SUCCESS,
    FAILED
}

data class ThermalPrinterDevice(
    val id: String,
    val name: String,
    val address: String,
    val transport: PrinterTransport,
    val isPairedOrAuthorized: Boolean = true,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val port: Int = 9100,
    val details: String = ""
)

sealed class PrinterConnectionStatus {
    data class Disconnected(val reason: String? = null) : PrinterConnectionStatus()
    data class Discovering(val transport: PrinterTransport) : PrinterConnectionStatus()
    data class Connecting(val device: ThermalPrinterDevice) : PrinterConnectionStatus()
    data class Connected(val device: ThermalPrinterDevice, val connectedAtMs: Long) : PrinterConnectionStatus()
    data class Printing(val device: ThermalPrinterDevice, val jobTitle: String) : PrinterConnectionStatus()
    data class Reconnecting(
        val device: ThermalPrinterDevice,
        val attempt: Int,
        val maxAttempts: Int,
        val reason: String? = null
    ) : PrinterConnectionStatus()
    data class Error(val message: String, val lastDevice: ThermalPrinterDevice? = null) : PrinterConnectionStatus()
}

data class ThermalPrintProfile(
    val paperWidth: ThermalPaperWidth = ThermalPaperWidth.MM_58,
    val language: String = "AR",
    val textDirection: ReceiptTextDirection = ReceiptTextDirection.AUTO,
    val fontSize: ThermalFontSize = ThermalFontSize.NORMAL,
    val headerText: String = "",
    val footerText: String = "",
    val showLogo: Boolean = true,
    val logoMaxWidthPx: Int = 240,
    val useRasterRendering: Boolean = true,
    val cutPaper: Boolean = true,
    val printQrCode: Boolean = true,
    val openCashDrawer: Boolean = false,
    val drawerPin: CashDrawerPin = CashDrawerPin.PIN_2,
    val maxRetryAttempts: Int = 2,
    val retryDelayMs: Long = 750L
) {
    val isRtl: Boolean
        get() = textDirection.resolveRtl(language)

    fun toLegacyConfig(): ThermalPrinterConfig = ThermalPrinterConfig(
        paperWidthMm = paperWidth.mm,
        charactersPerLine = paperWidth.charsPerLine,
        cutPaper = cutPaper,
        openCashDrawer = openCashDrawer,
        drawerPin = drawerPin,
        printQrCode = printQrCode,
        language = language,
        textDirection = textDirection,
        fontSize = fontSize,
        headerText = headerText,
        footerText = footerText,
        showLogo = showLogo,
        useRasterRendering = useRasterRendering
    )

    companion object {
        fun fromSettings(
            settings: GlobalSettingsEntity,
            paperWidthMm: Int = settings.printerPaperWidth,
            language: String = settings.language,
            fontSize: ThermalFontSize = ThermalFontSize.NORMAL,
            textDirection: ReceiptTextDirection = ReceiptTextDirection.AUTO,
            useRasterRendering: Boolean = true
        ): ThermalPrintProfile = ThermalPrintProfile(
            paperWidth = ThermalPaperWidth.fromMm(paperWidthMm),
            language = language.ifBlank { "AR" },
            textDirection = textDirection,
            fontSize = fontSize,
            headerText = if (settings.headerContentType.uppercase(Locale.ROOT) != "IMAGE") settings.receiptHeader else "",
            footerText = if (settings.showReceiptFooter && settings.footerContentType.uppercase(Locale.ROOT) != "IMAGE") settings.receiptFooter else "",
            showLogo = settings.showLogo,
            logoMaxWidthPx = settings.logoWidth.coerceIn(80, ThermalPaperWidth.fromMm(paperWidthMm).dotsWidth - 24),
            useRasterRendering = useRasterRendering,
            cutPaper = true,
            printQrCode = settings.showBarcode,
            openCashDrawer = false
        )
    }
}

data class PrintJobRecord(
    val id: String,
    val timestamp: Long,
    val title: String,
    val invoiceNumber: String,
    val transport: PrinterTransport,
    val printerName: String,
    val printerAddress: String,
    val paperWidthMm: Int,
    val language: String,
    val status: PrintJobStatus,
    val attemptCount: Int,
    val errorMessage: String? = null,
    val byteSize: Int = 0
)

data class PrintExecutionResult(
    val jobId: String,
    val success: Boolean,
    val message: String,
    val attempts: Int,
    val errorCause: String? = null
)

/**
 * Dedicated standalone POS Thermal Printer Service interface.
 * Reusable across all RM.POS screens (Sales, Purchases, Payments, Reports, Settings)
 * supporting Bluetooth and USB thermal printers (plus Wi-Fi / Android System print).
 */
interface PosThermalPrinterService {
    val connectionStatus: StateFlow<PrinterConnectionStatus>
    val activePrinter: StateFlow<ThermalPrinterDevice?>
    val discoveredPrinters: StateFlow<List<ThermalPrinterDevice>>
    val printHistory: StateFlow<List<PrintJobRecord>>

    /**
     * Discovers available thermal printers (paired Bluetooth devices, connected USB POS printers, or both).
     */
    suspend fun discoverPrinters(
        context: Context,
        transport: PrinterTransport? = null
    ): List<ThermalPrinterDevice>

    /**
     * Connects to the specified Bluetooth or USB thermal printer device.
     */
    suspend fun connect(
        context: Context,
        device: ThermalPrinterDevice,
        lang: String = "AR"
    ): Result<ThermalPrinterDevice>

    /**
     * Disconnects the active Bluetooth or USB thermal printer session.
     */
    suspend fun disconnect(reason: String? = null)

    /**
     * Formats structured receipt data into raw ESC/POS bytes adapted for 58mm/80mm paper,
     * Arabic RTL / French LTR direction, font size, logo, header, and footer.
     */
    fun formatReceiptBytes(
        receipt: ThermalReceiptData,
        profile: ThermalPrintProfile = ThermalPrintProfile(),
        context: Context? = null
    ): ByteArray

    /**
     * Formats structured receipt data into plain text adapted for 58mm/80mm width and language.
     */
    fun formatPlainTextReceipt(
        receipt: ThermalReceiptData,
        profile: ThermalPrintProfile = ThermalPrintProfile()
    ): String

    /**
     * Prints a formatted receipt with automatic error handling, retries, and print job history tracking.
     */
    suspend fun printReceipt(
        context: Context,
        receipt: ThermalReceiptData,
        profile: ThermalPrintProfile = ThermalPrintProfile(),
        target: ThermalPrinterDevice? = null,
        jobTitle: String = "Receipt"
    ): PrintExecutionResult

    /**
     * Convenience method to build and print a completed SaleEntity invoice immediately after checkout.
     */
    suspend fun printSaleInvoice(
        context: Context,
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity,
        lang: String = settings.language,
        target: ThermalPrinterDevice? = null,
        profileOverride: ThermalPrintProfile? = null
    ): PrintExecutionResult

    /**
     * Prints a diagnostic test receipt verifying paper width, language/direction, logo/header/footer, and link.
     */
    suspend fun printTestReceipt(
        context: Context,
        settings: GlobalSettingsEntity = GlobalSettingsEntity(),
        profile: ThermalPrintProfile = ThermalPrintProfile(),
        target: ThermalPrinterDevice? = null
    ): PrintExecutionResult

    /**
     * Retries a previously failed (or recent) print job from the print history log.
     */
    suspend fun retryPrintJob(
        context: Context,
        jobId: String
    ): PrintExecutionResult

    /**
     * Clears persisted print job history entries.
     */
    fun clearPrintHistory(context: Context? = null)
}
