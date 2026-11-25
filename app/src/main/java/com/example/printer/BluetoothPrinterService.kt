package com.example.printer

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.ui.screens.PdfPrintAndShareUtils
import com.example.ui.screens.generateSaleReceiptPdfFile
import com.example.util.MoneyUtils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class PrinterDeviceInfo(
    val name: String,
    val address: String,
    val isBonded: Boolean = true
)

/**
 * Unified POS Printer facade consolidated inside `com.example.printer`.
 * Coordinates Bluetooth SPP, Wi-Fi RAW TCP 9100, System PrintManager, and In-App PDF preview.
 */
object BluetoothPrinterService {
    private const val TAG = "BluetoothPrinterService"

    @Volatile
    private var foregroundActivityRef: WeakReference<Activity>? = null

    fun attachForegroundActivity(activity: Activity) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            foregroundActivityRef = WeakReference(activity)
        }
    }

    fun detachForegroundActivity(activity: Activity) {
        if (foregroundActivityRef?.get() === activity) {
            foregroundActivityRef = null
        }
    }

    fun findActivity(context: Context?): Activity? {
        var current = context
        var depth = 0
        while (current is ContextWrapper && depth < 16) {
            if (current is Activity) {
                return if (!current.isFinishing && !current.isDestroyed) current else null
            }
            current = current.baseContext
            depth++
        }
        return (current as? Activity)?.takeIf { !it.isFinishing && !it.isDestroyed }
    }

    fun resolvePrintActivity(context: Context?): Activity? {
        return findActivity(context)
            ?: foregroundActivityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    }

    internal fun clearForegroundActivityForTesting() {
        foregroundActivityRef = null
    }

    fun isBluetoothAvailable(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter != null
        } catch (t: Throwable) {
            Log.e(TAG, "Error checking Bluetooth hardware availability", t)
            false
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.isEnabled == true
        } catch (t: Throwable) {
            Log.e(TAG, "Error checking Bluetooth enabled state", t)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(context: Context): List<PrinterDeviceInfo> {
        return BluetoothPrinterConnectionManager.getPairedDevicesSafe(context)
    }

    @SuppressLint("MissingPermission")
    fun printReceipt(
        context: Context,
        printerNameOrMac: String,
        textToPrint: String,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val payload = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x1B, 0x40))
            write(byteArrayOf(0x1B, 0x74, 0x00))
            write(textToPrint.toByteArray(Charsets.UTF_8))
            write("\n\n\n\n".toByteArray(Charsets.UTF_8))
            write(byteArrayOf(0x1D, 0x56, 0x41, 0x10))
        }.toByteArray()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (ok, msg) = BluetoothPrinterConnectionManager.writeBytes(
                    context = context,
                    deviceAddressOrName = printerNameOrMac,
                    data = payload,
                    jobDescription = "Receipt Text"
                )
                postResult(context, onResult, ok, msg)
            } catch (t: Throwable) {
                Log.e(TAG, "Bluetooth printReceipt error", t)
                postResult(context, onResult, false, "فشل الطباعة عبر البلوتوث: ${t.localizedMessage ?: "خطأ غير متوقع"}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun printReceiptBytes(
        context: Context,
        printerNameOrMac: String,
        data: ByteArray,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (ok, msg) = BluetoothPrinterConnectionManager.writeBytes(
                    context = context,
                    deviceAddressOrName = printerNameOrMac,
                    data = data,
                    jobDescription = "Raw ESC/POS"
                )
                postResult(context, onResult, ok, msg)
            } catch (t: Throwable) {
                Log.e(TAG, "Bluetooth printReceiptBytes error", t)
                postResult(context, onResult, false, "فشل الطباعة عبر البلوتوث: ${t.localizedMessage ?: "خطأ غير متوقع"}")
            }
        }
    }

    fun printReceiptNetwork(
        context: Context,
        ip: String,
        port: Int = 9100,
        textToPrint: String,
        onResult: ((Boolean, String) -> Unit)? = null
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
                val payload = ByteArrayOutputStream().apply {
                    write(byteArrayOf(0x1B, 0x40))
                    write(textToPrint.toByteArray(Charsets.UTF_8))
                    write("\n\n\n\n".toByteArray(Charsets.UTF_8))
                    write(byteArrayOf(0x1D, 0x56, 0x41, 0x10))
                }.toByteArray()
                WifiNetworkSocketHelper.writeChunked(socket, payload)
                postResult(context, onResult, true, "تم إرسال أمر الطباعة بنجاح إلى $trimmedIp:$port")
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

    fun escapeHtml(text: String?): String = HtmlInvoiceAndReceiptFormatter.escapeHtml(text)

    fun buildHtmlInvoice(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity = GlobalSettingsEntity(),
        context: Context? = null
    ): String = HtmlInvoiceAndReceiptFormatter.buildHtmlInvoice(sale, items, settings, context)

    fun printReceiptSystem(
        context: Context,
        jobTitle: String,
        textToPrint: String
    ) = WebViewSystemPrintExecutor.printReceiptSystem(context, jobTitle, textToPrint)

    fun printUnified(
        context: Context,
        connectionType: String,
        printerNameOrMac: String,
        textToPrint: String,
        ip: String = "192.168.1.100",
        port: Int = 9100,
        jobTitle: String = "POS Receipt",
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        when (connectionType.uppercase(Locale.ROOT)) {
            "BLUETOOTH" -> printReceipt(context, printerNameOrMac, textToPrint, onResult)
            "USB", "OTG" -> {
                val payload = ByteArrayOutputStream().apply {
                    write(byteArrayOf(0x1B, 0x40))
                    write(textToPrint.toByteArray(Charsets.UTF_8))
                    write("\n\n\n\n".toByteArray(Charsets.UTF_8))
                    write(byteArrayOf(0x1D, 0x56, 0x41, 0x10))
                }.toByteArray()
                PosPrintServiceImpl.sendToUsbPrinter(context, printerNameOrMac, payload, onResult)
            }
            "WIFI", "NETWORK", "IP" -> printReceiptNetwork(context, ip, port, textToPrint, onResult)
            "SYSTEM", "ANDROID", "CABLE", "PDF" -> {
                printReceiptSystem(context, jobTitle, textToPrint)
                onResult?.invoke(true, "تم فتح شاشة الطباعة الخاصة بهاتفك")
            }
            else -> printReceipt(context, printerNameOrMac, textToPrint, onResult)
        }
    }

    fun printTestReceipt(
        context: Context,
        connectionType: String,
        printerNameOrMac: String,
        ip: String = "192.168.1.100",
        port: Int = 9100,
        paperWidth: Int = 58,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(Date())
        val width = if (paperWidth == 80) 48 else 32
        val testText = buildString {
            appendLine(padCenter("=== اختبار الطباعة بالهاتف ===", width))
            appendLine(padCenter("SUPERMARKET POS SYSTEM", width))
            appendLine("--------------------------------")
            appendLine("الهاتف: متصل بنجاح")
            appendLine("النوع: $connectionType")
            appendLine("الهدف: ${if (connectionType == "WIFI") "$ip:$port" else printerNameOrMac}")
            appendLine("التاريخ: $dateStr")
            appendLine("عرض الورق: ${paperWidth}mm")
            appendLine("--------------------------------")
            appendLine("1x فحص الاتصال الحراري      1.00")
            appendLine("1x توافق الأوامر ESC/POS    1.00")
            appendLine("--------------------------------")
            appendLine("النتيجة: جاهز للعمل والبيع")
            appendLine(padCenter("*** شكراً لكم ***", width))
        }
        printUnified(context, connectionType, printerNameOrMac, testText, ip, port, "اختبار الطابعة", onResult)
    }

    fun openCashDrawer(
        context: Context,
        connectionType: String,
        printerNameOrMac: String,
        ip: String = "192.168.1.100",
        port: Int = 9100,
        pin: CashDrawerPin = CashDrawerPin.PIN_2,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        CashDrawerManager.openCashDrawer(context, connectionType, printerNameOrMac, ip, port, pin, onResult = onResult)
    }

    fun printThermalReceipt(
        context: Context,
        connectionType: String,
        printerNameOrMac: String,
        receipt: ThermalReceiptData,
        config: ThermalPrinterConfig = ThermalPrinterConfig(),
        ip: String = "192.168.1.100",
        port: Int = 9100,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        PosPrintServiceImpl.printUnified(context, connectionType, printerNameOrMac, receipt, config, ip, port, onResult = onResult)
    }

    fun printInvoiceSystem(
        context: Context,
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity = GlobalSettingsEntity()
    ) {
        val appCtx = context.applicationContext ?: context
        val jobTitle = "فاتورة_${sale.invoiceNumber}"
        try {
            val currency = settings.currency.ifBlank { "MAD" }
            val readableFallback = buildString {
                if (settings.shopName.isNotBlank()) appendLine(settings.shopName)
                appendLine("فاتورة رقم: ${sale.invoiceNumber}")
                appendLine("الزبون: ${sale.customerName.ifBlank { "زبون عادي" }}")
                appendLine("التاريخ: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(sale.timestamp))}")
                appendLine("--------------------------------")
                for (item in items) {
                    appendLine("${item.productName}  x${item.quantity}  = ${MoneyUtils.format(item.totalLinePriceDouble, currency)}")
                }
                appendLine("--------------------------------")
                appendLine("المجموع: ${MoneyUtils.format(sale.totalAmountDouble, currency)}")
            }
            val invoicePdf = runCatching {
                generateSaleReceiptPdfFile(appCtx, sale, items, settings)
            }.onFailure { err ->
                Log.w(TAG, "Direct invoice PDF generation fell back to HTML print", err)
            }.getOrNull()

            if (invoicePdf != null && invoicePdf.exists() && invoicePdf.length() > 0L) {
                PdfPrintAndShareUtils.printPdfFile(
                    context = context,
                    file = invoicePdf,
                    jobTitle = jobTitle,
                    isLandscape = false,
                    fallbackText = readableFallback
                )
            } else {
                val html = buildHtmlInvoice(sale, items, settings, context)
                printReceiptSystem(context, jobTitle, html)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error building/printing invoice; opening built-in preview", t)
            InAppPrintPreviewManager.openTextOrHtmlPrintWindow(
                context = appCtx,
                jobTitle = jobTitle,
                rawTextOrHtml = "فاتورة رقم: ${sale.invoiceNumber}"
            )
        }
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
                Log.e(TAG, "postResult callback failed", t)
            }
            try {
                Toast.makeText(appCtx, message, Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                Log.e(TAG, "postResult Toast failed", t)
            }
        }
    }

    private fun padCenter(text: String, width: Int): String {
        if (text.length >= width) return text
        val pad = (width - text.length) / 2
        return " ".repeat(pad) + text
    }
}
