package com.example.printer

import android.content.Context
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.ui.theme.Bilingual
import com.example.util.DocumentDesignHelper
import java.io.IOException
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transport dispatch and receipt-model translation helper for [DefaultPosThermalPrinterService].
 */
internal object ThermalTransportDispatcher {

    fun buildSaleReceiptData(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity,
        lang: String = settings.language,
        openCashDrawer: Boolean = false
    ): ThermalReceiptData {
        val effectiveLang = lang.ifBlank { "AR" }.uppercase(Locale.ROOT)
        val defaultStore = Bilingual.tr(effectiveLang, "متجر RM.POS", "MAGASIN RM.POS", "RM.POS STORE")
        return ThermalReceiptData(
            documentTitle = Bilingual.tr(effectiveLang, "فاتورة بيع", "TICKET DE VENTE", "SALES RECEIPT"),
            storeName = if (settings.showShopName) settings.shopName.ifBlank { defaultStore } else "",
            storeAddress = if (settings.showAddress) settings.shopAddress else "",
            storePhone = if (settings.showPhone) settings.shopPhone else "",
            taxId = if (settings.showTaxNumber) settings.taxNumber else "",
            invoiceNumber = sale.invoiceNumber.ifBlank {
                "INV-${SimpleDateFormat("HHmmss", Locale.US).format(Date(sale.timestamp))}"
            },
            timestamp = sale.timestamp,
            cashierName = if (settings.showCashierName) sale.cashierName.trim() else "",
            customerName = if (settings.showCustomerInfo) sale.customerName.takeIf { it.isNotBlank() } else null,
            items = items.map { line ->
                ThermalReceiptItem(
                    name = line.productName,
                    quantity = line.quantity,
                    unitPrice = line.unitPriceDouble,
                    total = line.totalLinePriceDouble
                )
            },
            subtotal = sale.totalAmountDouble + sale.discountAmountDouble,
            taxAmount = 0.0,
            discountAmount = sale.discountAmountDouble,
            grandTotal = sale.totalAmountDouble,
            paymentMethod = sale.paymentMethod,
            paidAmount = sale.paidAmountDouble,
            changeAmount = sale.changeAmountDouble,
            footerMessage = if (DocumentDesignHelper.footerShowsText(settings)) settings.receiptFooter else "",
            qrCodeData = if (settings.showBarcode) sale.invoiceNumber else null,
            openCashDrawer = openCashDrawer
        )
    }

    fun buildEnrichedTestReceipt(
        resolvedTarget: ThermalPrinterDevice?,
        settings: GlobalSettingsEntity,
        profile: ThermalPrintProfile,
        effectiveLang: String
    ): Pair<ThermalReceiptData, String> {
        val transport = resolvedTarget?.transport ?: PrinterTransport.BLUETOOTH
        val printerLabel = when {
            resolvedTarget == null -> "RM.POS Thermal"
            transport == PrinterTransport.WIFI -> "${resolvedTarget.address}:${resolvedTarget.port} (Wi-Fi)"
            else -> "${resolvedTarget.name.ifBlank { resolvedTarget.address }} (${transport.name})"
        }
        val baseTestReceipt = ThermalReceiptSummaryBuilder.buildTestPrintReceipt(
            printerNameOrMac = printerLabel,
            paperWidthMm = profile.paperWidth.mm,
            settings = settings,
            lang = effectiveLang,
            transport = transport
        )
        val dirLabel = if (profile.isRtl) "RTL" else "LTR"
        val enrichedTestReceipt = baseTestReceipt.copy(
            items = baseTestReceipt.items + ThermalReceiptItem(
                name = Bilingual.tr(
                    effectiveLang,
                    "الخط ${profile.fontSize.name} • اتجاه $dirLabel",
                    "Police ${profile.fontSize.name} • $dirLabel",
                    "Font ${profile.fontSize.name} • $dirLabel"
                ),
                quantity = 1.0,
                unitPrice = 0.0,
                total = 0.0
            ),
            footerMessage = profile.footerText.trim().ifBlank { baseTestReceipt.footerMessage }
        )

        val jobTitle = when (transport) {
            PrinterTransport.WIFI -> Bilingual.tr(effectiveLang, "إيصال تجريبي (Wi-Fi)", "Ticket de test (Wi-Fi)", "Test Receipt (Wi-Fi)")
            PrinterTransport.USB -> Bilingual.tr(effectiveLang, "إيصال تجريبي (USB)", "Ticket de test (USB)", "Test Receipt (USB)")
            else -> Bilingual.tr(effectiveLang, "إيصال تجريبي", "Ticket de test", "Test Receipt")
        }
        return enrichedTestReceipt to jobTitle
    }

    suspend fun dispatchToTransport(
        context: Context,
        target: ThermalPrinterDevice,
        bytes: ByteArray,
        plainText: String,
        jobTitle: String,
        lang: String,
        testOverride: (suspend (Context, ThermalPrinterDevice, ByteArray, String, String) -> Pair<Boolean, String>)?
    ): Triple<Boolean, String, Boolean> {
        testOverride?.let { override ->
            val (ok, msg) = override.invoke(context, target, bytes, jobTitle, lang)
            return Triple(ok, msg, false)
        }

        return when (target.transport) {
            PrinterTransport.BLUETOOTH -> {
                val (ok, msg) = BluetoothPrinterConnectionManager.writeBytes(
                    context = context,
                    deviceAddressOrName = target.address.ifBlank { target.name },
                    data = bytes,
                    jobDescription = jobTitle,
                    lang = lang
                )
                Triple(ok, msg, false)
            }

            PrinterTransport.USB -> {
                val (ok, msg) = UsbPrinterConnectionManager.writeBytes(
                    context = context,
                    deviceAddressOrName = target.address.ifBlank { target.name },
                    data = bytes,
                    jobDescription = jobTitle,
                    lang = lang
                )
                Triple(ok, msg, false)
            }

            PrinterTransport.WIFI -> {
                sendRawTcp(context, target.address.trim(), target.port, bytes, lang)
            }

            PrinterTransport.SYSTEM -> {
                try {
                    BluetoothPrinterService.printReceiptSystem(
                        context = context,
                        jobTitle = jobTitle,
                        textToPrint = plainText
                    )
                    Triple(
                        true,
                        Bilingual.tr(
                            lang,
                            "تم فتح شاشة الطباعة الخاصة بهاتفك",
                            "Écran d'impression Android ouvert",
                            "Android print dialog opened"
                        ),
                        false
                    )
                } catch (t: Throwable) {
                    Triple(false, t.localizedMessage ?: "System print error", true)
                }
            }
        }
    }

    private fun sendRawTcp(
        context: Context,
        ip: String,
        port: Int,
        bytes: ByteArray,
        lang: String
    ): Triple<Boolean, String, Boolean> {
        if (ip.isBlank() || port !in 1..65535 || port == 631) {
            val diag = WifiNetworkSocketHelper.diagnoseWifiFailure(context, ip, port, null, lang)
            return Triple(false, diag.message, true)
        }
        var socket: Socket? = null
        return try {
            socket = WifiNetworkSocketHelper.openSocket(
                context = context,
                ip = ip,
                port = port,
                connectTimeoutMs = WifiNetworkSocketHelper.DEFAULT_CONNECT_TIMEOUT_MS,
                readTimeoutMs = WifiNetworkSocketHelper.DEFAULT_READ_TIMEOUT_MS
            )
            WifiNetworkSocketHelper.writeChunked(socket, bytes)
            Triple(
                true,
                Bilingual.tr(
                    lang,
                    "تمت الطباعة بنجاح عبر الشبكة ($ip:$port)",
                    "Impression réseau réussie ($ip:$port)",
                    "Printed via network ($ip:$port)"
                ),
                false
            )
        } catch (t: Throwable) {
            val diag = WifiNetworkSocketHelper.diagnoseWifiFailure(context, ip, port, t, lang)
            Triple(false, diag.message, diag.isPermanentFailure)
        } finally {
            try {
                socket?.close()
            } catch (_: IOException) {
            }
        }
    }
}
