package com.example.printer

import com.example.data.GlobalSettingsEntity
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.CartItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds structured ThermalReceiptData payloads for checkout summaries and printer test pages.
 */
internal object ThermalReceiptSummaryBuilder {

    fun buildTransactionSummaryReceipt(
        cartItems: List<CartItem>,
        subtotal: Double,
        discountAmount: Double,
        netTotal: Double,
        paymentMethod: String,
        receivedAmount: Double,
        changeDue: Double,
        cashierName: String,
        customerName: String?,
        settings: GlobalSettingsEntity,
        lang: String,
        invoiceNumberOverride: String? = null,
        isDraftSummary: Boolean = true,
        timestamp: Long = System.currentTimeMillis()
    ): ThermalReceiptData {
        val summaryRef = invoiceNumberOverride?.takeIf { it.isNotBlank() } ?: run {
            val timePart = SimpleDateFormat("HHmmss", Locale.US).format(Date(timestamp))
            "SUM-$timePart"
        }

        val docTitle = if (isDraftSummary) {
            Bilingual.tr(lang, "ملخص المعاملة (قبل التأكيد)", "RÉSUMÉ TRANSACTION", "TRANSACTION SUMMARY")
        } else {
            Bilingual.tr(lang, "وصل البيع", "TICKET DE VENTE", "SALES RECEIPT")
        }

        val defaultStore = Bilingual.tr(lang, "متجر نقاط البيع", "MAGASIN POS", "POS STORE")
        val defaultFooter = Bilingual.tr(
            lang,
            "شكراً لزيارتكم - ملخص المعاملة",
            "Merci de votre visite - Résumé transaction",
            "Thank you for your business"
        )

        val items = cartItems.map { item ->
            ThermalReceiptItem(
                name = item.product.name,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                total = item.totalAfterDiscount,
                barcode = item.product.barcode.takeIf { it.isNotBlank() }
            )
        }

        return ThermalReceiptData(
            documentTitle = docTitle,
            storeName = if (settings.showShopName) settings.shopName.ifBlank { defaultStore } else "",
            storeAddress = if (settings.showAddress) settings.shopAddress else "",
            storePhone = if (settings.showPhone) settings.shopPhone else "",
            taxId = if (settings.showTaxNumber) settings.taxNumber else "",
            invoiceNumber = summaryRef,
            timestamp = timestamp,
            cashierName = if (settings.showCashierName) cashierName.trim() else "",
            customerName = if (settings.showCustomerInfo) customerName else null,
            items = items,
            subtotal = subtotal.coerceAtLeast(0.0),
            taxAmount = 0.0,
            discountAmount = discountAmount.coerceAtLeast(0.0),
            grandTotal = netTotal.coerceAtLeast(0.0),
            paymentMethod = "",
            paidAmount = receivedAmount.coerceAtLeast(0.0),
            changeAmount = changeDue.coerceAtLeast(0.0),
            footerMessage = if (com.example.util.DocumentDesignHelper.footerShowsText(settings)) settings.receiptFooter.ifBlank { defaultFooter } else "",
            qrCodeData = if (settings.showBarcode) summaryRef else null,
            openCashDrawer = false
        )
    }

    fun buildTestPrintReceipt(
        printerNameOrMac: String,
        paperWidthMm: Int,
        settings: GlobalSettingsEntity,
        lang: String,
        transport: PrinterTransport = PrinterTransport.BLUETOOTH
    ): ThermalReceiptData {
        val label = when (transport) {
            PrinterTransport.WIFI -> Bilingual.tr(lang, "اختبار الطابعة عبر Wi-Fi", "TEST IMPRIMANTE WI-FI", "WI-FI PRINTER TEST")
            PrinterTransport.USB -> Bilingual.tr(lang, "اختبار الطابعة عبر USB", "TEST IMPRIMANTE USB", "USB PRINTER TEST")
            PrinterTransport.SYSTEM -> Bilingual.tr(lang, "اختبار طباعة أندرويد", "TEST IMPRESSION ANDROID", "ANDROID PRINT TEST")
            PrinterTransport.BLUETOOTH -> Bilingual.tr(lang, "اختبار الطابعة الحرارية", "TEST IMPRIMANTE THERMIQUE", "THERMAL PRINTER TEST")
        }
        val connectionCheckLabel = when (transport) {
            PrinterTransport.WIFI -> Bilingual.tr(lang, "فحص اتصال Wi-Fi / IP", "Test connexion Wi-Fi", "Wi-Fi Connection Check")
            PrinterTransport.USB -> Bilingual.tr(lang, "فحص اتصال USB OTG", "Test connexion USB", "USB Connection Check")
            PrinterTransport.SYSTEM -> Bilingual.tr(lang, "فحص خدمة طباعة أندرويد", "Test service Android", "Android Print Check")
            PrinterTransport.BLUETOOTH -> Bilingual.tr(lang, "فحص اتصال البلوتوث", "Test connexion BT", "BT Connection Check")
        }
        return ThermalReceiptData(
            documentTitle = label,
            storeName = settings.shopName.ifBlank { "RM.POS THERMAL" },
            storeAddress = settings.shopAddress,
            storePhone = settings.shopPhone,
            taxId = settings.taxNumber,
            invoiceNumber = "TEST-${SimpleDateFormat("HHmmss", Locale.US).format(Date())}",
            timestamp = System.currentTimeMillis(),
            cashierName = "System Test",
            customerName = printerNameOrMac.ifBlank { transport.name },
            items = listOf(
                ThermalReceiptItem(
                    name = connectionCheckLabel,
                    quantity = 1.0,
                    unitPrice = 1.0,
                    total = 1.0
                ),
                ThermalReceiptItem(
                    name = Bilingual.tr(lang, "توافق الأوامر ${paperWidthMm}mm", "Format ${paperWidthMm}mm", "${paperWidthMm}mm Paper Layout"),
                    quantity = 1.0,
                    unitPrice = 0.0,
                    total = 0.0
                )
            ),
            subtotal = 1.0,
            discountAmount = 0.0,
            grandTotal = 1.0,
            paymentMethod = "CASH",
            paidAmount = 1.0,
            changeAmount = 0.0,
            footerMessage = Bilingual.tr(
                lang,
                "الطابعة متصلة وجاهزة للطباعة",
                "Imprimante prête à imprimer",
                "Printer connected & ready"
            ),
            qrCodeData = null,
            openCashDrawer = false
        )
    }
}
