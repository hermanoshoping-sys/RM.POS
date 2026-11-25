package com.example.printer

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class ThermalReceiptItem(
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val total: Double,
    val barcode: String? = null
)

data class ThermalReceiptData(
    val storeName: String = "SUPERMARKET POS",
    val storeAddress: String = "",
    val storePhone: String = "",
    val taxId: String = "",
    val invoiceNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "",
    val customerName: String? = null,
    val items: List<ThermalReceiptItem>,
    val subtotal: Double,
    val taxAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val grandTotal: Double,
    val paymentMethod: String = "CASH",
    val paidAmount: Double = grandTotal,
    val changeAmount: Double = 0.0,
    val footerMessage: String = "شكراً لزيارتكم - Merci de votre visite",
    val qrCodeData: String? = null,
    val openCashDrawer: Boolean = false,
    val documentTitle: String? = null
)

data class ThermalPrinterConfig(
    val paperWidthMm: Int = 58,
    val charactersPerLine: Int = if (paperWidthMm >= 72) 48 else 32,
    val charset: String = "UTF-8",
    val cutPaper: Boolean = true,
    val openCashDrawer: Boolean = false,
    val drawerPin: CashDrawerPin = CashDrawerPin.PIN_2,
    val printQrCode: Boolean = true,
    val language: String = "AR",
    val textDirection: ReceiptTextDirection = ReceiptTextDirection.AUTO,
    val fontSize: ThermalFontSize = ThermalFontSize.NORMAL,
    val headerText: String = "",
    val footerText: String = "",
    val showLogo: Boolean = true,
    val useRasterRendering: Boolean = false
)

interface PosPrintService {
    fun generateThermalReceiptBytes(
        receipt: ThermalReceiptData,
        config: ThermalPrinterConfig = ThermalPrinterConfig()
    ): ByteArray

    fun generatePlainTextReceipt(
        receipt: ThermalReceiptData,
        config: ThermalPrinterConfig = ThermalPrinterConfig()
    ): String

    fun sendToBluetoothPrinter(
        context: Context,
        deviceAddressOrName: String,
        data: ByteArray,
        onResult: ((Boolean, String) -> Unit)? = null
    )

    fun sendToUsbPrinter(
        context: Context,
        deviceAddressOrName: String,
        data: ByteArray,
        onResult: ((Boolean, String) -> Unit)? = null
    )

    fun sendToNetworkPrinter(
        context: Context,
        ip: String,
        port: Int = 9100,
        data: ByteArray,
        onResult: ((Boolean, String) -> Unit)? = null
    )

    fun printUnified(
        context: Context,
        connectionType: String,
        printerNameOrMac: String,
        receipt: ThermalReceiptData,
        config: ThermalPrinterConfig = ThermalPrinterConfig(),
        ip: String = "192.168.1.100",
        port: Int = 9100,
        jobTitle: String = "POS Receipt",
        onResult: ((Boolean, String) -> Unit)? = null
    )

    fun triggerCashDrawer(
        context: Context,
        connectionType: String,
        printerNameOrMac: String,
        ip: String = "192.168.1.100",
        port: Int = 9100,
        pin: CashDrawerPin = CashDrawerPin.PIN_2,
        onResult: ((Boolean, String) -> Unit)? = null
    )
}

/**
 * Standard PosPrintService implementation supporting Bluetooth, USB, Wi-Fi RAW TCP, and Android System Print.
 * Delegates unified thermal printing and print-job logging to [PosThermalPrinterServiceProvider].
 */
object PosPrintServiceImpl : PosPrintService {
    private const val TAG = "PosPrintService"

    override fun generateThermalReceiptBytes(receipt: ThermalReceiptData, config: ThermalPrinterConfig): ByteArray =
        EscPosReceiptEncoder.generateThermalReceiptBytes(receipt, config)

    override fun generatePlainTextReceipt(receipt: ThermalReceiptData, config: ThermalPrinterConfig): String =
        EscPosReceiptEncoder.generatePlainTextReceipt(receipt, config)

    @SuppressLint("MissingPermission")
    override fun sendToBluetoothPrinter(
        context: Context,
        deviceAddressOrName: String,
        data: ByteArray,
        onResult: ((Boolean, String) -> Unit)?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (success, msg) = BluetoothPrinterConnectionManager.writeBytes(
                    context = context,
                    deviceAddressOrName = deviceAddressOrName,
                    data = data,
                    jobDescription = "Thermal Receipt"
                )
                postResult(context, onResult, success, msg)
            } catch (t: Throwable) {
                Log.e(TAG, "Bluetooth print error", t)
                postResult(context, onResult, false, "فشل الطباعة عبر البلوتوث: ${t.localizedMessage ?: "خطأ غير متوقع"}")
            }
        }
    }

    override fun sendToUsbPrinter(
        context: Context,
        deviceAddressOrName: String,
        data: ByteArray,
        onResult: ((Boolean, String) -> Unit)?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (success, msg) = UsbPrinterConnectionManager.writeBytes(
                    context = context,
                    deviceAddressOrName = deviceAddressOrName,
                    data = data,
                    jobDescription = "USB Thermal Receipt"
                )
                postResult(context, onResult, success, msg)
            } catch (t: Throwable) {
                Log.e(TAG, "USB print error", t)
                postResult(context, onResult, false, "فشل الطباعة عبر USB: ${t.localizedMessage ?: "خطأ غير متوقع"}")
            }
        }
    }

    override fun sendToNetworkPrinter(
        context: Context,
        ip: String,
        port: Int,
        data: ByteArray,
        onResult: ((Boolean, String) -> Unit)?
    ) {
        val trimmedIp = ip.trim()
        if (trimmedIp.isBlank() || port !in 1..65535) {
            val diag = WifiNetworkSocketHelper.diagnoseWifiFailure(context, trimmedIp, port, null)
            postResult(context, onResult, false, diag.message)
            return
        }
        Thread {
            var socket: Socket? = null
            try {
                socket = WifiNetworkSocketHelper.openSocket(context, trimmedIp, port)
                WifiNetworkSocketHelper.writeChunked(socket, data)
                postResult(context, onResult, true, "تم إرسال بيانات الفاتورة إلى طابعة الشبكة ($trimmedIp:$port)")
            } catch (t: Throwable) {
                Log.e(TAG, "Network printer error", t)
                val diag = WifiNetworkSocketHelper.diagnoseWifiFailure(context, trimmedIp, port, t)
                postResult(context, onResult, false, diag.message)
            } finally {
                try {
                    socket?.close()
                } catch (e: IOException) {
                    Log.w(TAG, "Network socket close warning", e)
                }
            }
        }.start()
    }

    override fun printUnified(
        context: Context,
        connectionType: String,
        printerNameOrMac: String,
        receipt: ThermalReceiptData,
        config: ThermalPrinterConfig,
        ip: String,
        port: Int,
        jobTitle: String,
        onResult: ((Boolean, String) -> Unit)?
    ) {
        val transport = PrinterTransport.fromString(connectionType)
        val targetDevice = when (transport) {
            PrinterTransport.WIFI -> ThermalPrinterDevice(
                id = "$ip:$port",
                name = "Wi-Fi Printer ($ip)",
                address = ip.trim(),
                transport = PrinterTransport.WIFI,
                port = port
            )
            PrinterTransport.SYSTEM -> ThermalPrinterDevice(
                id = "android_system_print",
                name = "Android System Print",
                address = "SYSTEM",
                transport = PrinterTransport.SYSTEM
            )
            PrinterTransport.USB -> ThermalPrinterDevice(
                id = printerNameOrMac.ifBlank { "usb_auto" },
                name = printerNameOrMac.ifBlank { "USB Thermal Printer" },
                address = printerNameOrMac,
                transport = PrinterTransport.USB
            )
            PrinterTransport.BLUETOOTH -> ThermalPrinterDevice(
                id = printerNameOrMac.ifBlank { "bt_auto" },
                name = printerNameOrMac.ifBlank { "Bluetooth Thermal Printer" },
                address = printerNameOrMac,
                transport = PrinterTransport.BLUETOOTH
            )
        }
        val profile = ThermalPrintProfile(
            paperWidth = ThermalPaperWidth.fromMm(config.paperWidthMm),
            language = config.language,
            textDirection = config.textDirection,
            fontSize = config.fontSize,
            headerText = config.headerText,
            footerText = config.footerText,
            showLogo = config.showLogo,
            useRasterRendering = config.useRasterRendering,
            cutPaper = config.cutPaper,
            printQrCode = config.printQrCode,
            openCashDrawer = config.openCashDrawer,
            drawerPin = config.drawerPin
        )

        CoroutineScope(Dispatchers.IO).launch {
            val result = PosThermalPrinterServiceProvider.instance.printReceipt(
                context = context,
                receipt = receipt,
                profile = profile,
                target = targetDevice,
                jobTitle = jobTitle.ifBlank { "وصل_${receipt.invoiceNumber}" }
            )
            postResult(context, onResult, result.success, result.message)
        }
    }

    override fun triggerCashDrawer(
        context: Context,
        connectionType: String,
        printerNameOrMac: String,
        ip: String,
        port: Int,
        pin: CashDrawerPin,
        onResult: ((Boolean, String) -> Unit)?
    ) {
        CashDrawerManager.openCashDrawer(context, connectionType, printerNameOrMac, ip, port, pin, onResult = onResult)
    }

    private fun postResult(
        context: Context,
        callback: ((Boolean, String) -> Unit)?,
        success: Boolean,
        message: String
    ) {
        val appCtx = context.applicationContext ?: context
        Handler(Looper.getMainLooper()).post {
            try {
                callback?.invoke(success, message)
            } catch (t: Throwable) {
                Log.e(TAG, "Callback failed in postResult", t)
            }
            try {
                Toast.makeText(appCtx, message, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Log.e(TAG, "Toast failed in postResult", t)
            }
        }
    }
}
