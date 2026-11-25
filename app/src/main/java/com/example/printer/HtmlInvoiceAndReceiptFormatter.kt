package com.example.printer

import android.content.Context
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.util.DocumentDesignHelper
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sanitizes and formats HTML receipts and structured A4/Thermal invoices
 * for Android PrintManager / WebView rendering.
 */
internal object HtmlInvoiceAndReceiptFormatter {

    fun escapeHtml(text: String?): String {
        if (text == null) return ""
        val out = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '&' -> out.append("&amp;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#x27;")
                '/' -> out.append("&#x2F;")
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    fun buildHtmlReceipt(title: String, text: String, fontSizePt: Int = 12): String {
        val safeTitle = escapeHtml(title)
        val safeBody = escapeHtml(text)
        val bodyPx = fontSizePt.coerceIn(8, 20)
        return """
            <!DOCTYPE html>
            <html dir="rtl" lang="ar">
            <head>
                <meta charset="utf-8">
                <style>
                    @page { size: A4 portrait; margin: 5mm 10mm 10mm 10mm; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Cairo", monospace;
                        font-size: ${bodyPx}px;
                        color: #111;
                        padding: 0;
                        margin: 0;
                        direction: rtl;
                    }
                    .receipt-box {
                        width: 100%;
                        box-sizing: border-box;
                        margin: 0;
                        padding: 10px;
                        border: 1px dashed #bbb;
                        background: #fff;
                    }
                    h2 { text-align: center; margin: 4px 0 10px 0; font-size: ${(bodyPx + 4)}px; }
                    pre {
                        font-family: "Courier New", Courier, monospace;
                        white-space: pre-wrap;
                        word-break: break-all;
                        font-size: ${bodyPx}px;
                        line-height: 1.4;
                        margin: 0;
                    }
                </style>
            </head>
            <body>
                <div class="receipt-box">
                    <h2>$safeTitle</h2>
                    <pre>$safeBody</pre>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun buildHtmlInvoice(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity = GlobalSettingsEntity(),
        context: Context? = null
    ): String {
        val dateStr = escapeHtml(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(sale.timestamp)))
        val rawClientName = sale.customerName.ifBlank { "زبون عادي" }
        val clientName = escapeHtml(rawClientName)
        val rawShopName = if (settings.showShopName) settings.shopName.ifBlank { "الشركة" } else ""
        val shopName = escapeHtml(rawShopName)
        val shopPhone = escapeHtml(settings.shopPhone)
        val shopAddress = escapeHtml(settings.shopAddress)
        val taxNumber = escapeHtml(settings.taxNumber)
        val invoiceNum = escapeHtml(sale.invoiceNumber)

        val quality = settings.logoQuality.coerceIn(40, 100)
        val logoImgHtml = if (settings.showLogo) {
            DocumentDesignHelper.loadEncodedImageHtml(
                context = context,
                fileName = DocumentDesignHelper.LOGO_FILE_NAME,
                prefKey = "company_logo_uri",
                maxWidthPx = settings.logoWidth.coerceIn(40, 320),
                maxHeightPx = settings.logoHeight.coerceIn(30, 220),
                qualityPercent = quality,
                alt = "Logo"
            )
        } else ""

        val headerImgHtml = if (DocumentDesignHelper.headerShowsImage(settings)) {
            DocumentDesignHelper.loadEncodedImageHtml(
                context = context,
                fileName = DocumentDesignHelper.HEADER_IMAGE_FILE_NAME,
                prefKey = "header_image_uri",
                maxWidthPx = settings.headerWidth.coerceIn(120, 538),
                maxHeightPx = settings.headerHeight.coerceIn(24, 220),
                qualityPercent = quality,
                alt = "Header"
            )
        } else ""

        val headerTextHtml = if (DocumentDesignHelper.headerShowsText(settings)) {
            val safeHeader = escapeHtml(settings.receiptHeader).replace("\n", "<br/>")
            val headerFontPx = settings.headerFontSize.coerceIn(9, 26)
            """<div class="custom-header-text" style="font-size: ${headerFontPx}px;">$safeHeader</div>"""
        } else ""

        val footerImgHtml = if (DocumentDesignHelper.footerShowsImage(settings)) {
            DocumentDesignHelper.loadEncodedImageHtml(
                context = context,
                fileName = DocumentDesignHelper.FOOTER_IMAGE_FILE_NAME,
                prefKey = "footer_image_uri",
                maxWidthPx = settings.footerWidth.coerceIn(120, 538),
                maxHeightPx = settings.footerHeight.coerceIn(24, 220),
                qualityPercent = quality,
                alt = "Footer"
            )
        } else ""

        val baseFontPx = settings.documentFontSize.coerceIn(8, 20)
        val tableFontPx = (baseFontPx - 1).coerceAtLeast(8)
        val shopNamePx = (baseFontPx + 6).coerceAtLeast(14)

        val remainingDebt = sale.remainingDueDouble
        val cashierName = if (settings.showCashierName) escapeHtml(sale.cashierName.trim()) else ""
        val displayClient = if (settings.showCustomerInfo) clientName else ""
        val displayDate = if (settings.showDateTime) dateStr else ""

        val rowsBuilder = StringBuilder()
        val minRows = maxOf(items.size, 22)
        for (i in 0 until minRows) {
            if (i < items.size) {
                val it = items[i]
                val qStr = if (it.quantity % 1.0 == 0.0) {
                    String.format(Locale.US, "%.0f", it.quantity)
                } else {
                    String.format(Locale.US, "%.2f", it.quantity)
                }
                val safeProductName = escapeHtml(it.productName)
                rowsBuilder.append(
                    """
                    <tr>
                        <td style="text-align: center; font-weight: bold; width: 14%;">${String.format(Locale.US, "%.2f", it.totalLinePriceDouble)}</td>
                        <td style="text-align: center; width: 12%;">${String.format(Locale.US, "%.2f", it.unitPriceDouble)}</td>
                        <td style="text-align: center; width: 12%;">قطعة</td>
                        <td style="text-align: center; width: 14%;">$qStr</td>
                        <td style="text-align: right; width: 48%; padding-right: 6px; word-break: break-word;">$safeProductName</td>
                    </tr>
                    """.trimIndent()
                )
            } else {
                rowsBuilder.append(
                    """<tr style="height: 20px;"><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td></tr>"""
                )
            }
        }

        val curr = settings.currency.ifBlank { "MAD" }

        // Optional settlement details (paid / cash change only; remaining debt omitted from printed invoices)
        val settlementParts = mutableListOf<String>()
        val hasPartialOrChange = sale.paymentMethod.uppercase(Locale.ROOT) == "CREDIT" || remainingDebt > 0 || sale.changeAmountDouble > 0
        if (hasPartialOrChange) {
            if (sale.paidAmountDouble > 0) settlementParts.add("المدفوع: ${MoneyUtils.format(sale.paidAmountDouble, curr)}")
            if (sale.changeAmountDouble > 0) settlementParts.add("الباقي: ${MoneyUtils.format(sale.changeAmountDouble, curr)}")
        }
        val settlementHtml = if (settlementParts.isNotEmpty()) {
            """<div class="footer-info" style="margin-bottom: 8px;">${settlementParts.joinToString(" • ")}</div>"""
        } else ""

        val footerNoteHtml = if (DocumentDesignHelper.footerShowsText(settings)) {
            val safeFooter = escapeHtml(
                settings.receiptFooter.ifBlank { "شكراً لزيارتكم - يرجى الاحتفاظ بالفاتورة" }
            ).replace("\n", "<br/>")
            """<div class="footer-info" style="margin-top: 6px; min-height: ${settings.footerHeight.coerceIn(24, 220)}px;">$safeFooter</div>"""
        } else ""

        val barcodeHtml = if (settings.showBarcode) {
            """<div class="barcode-box">* $invoiceNum *</div>"""
        } else ""

        val hasHeaderContent = headerImgHtml.isNotBlank() || logoImgHtml.isNotBlank() || headerTextHtml.isNotBlank() ||
            shopName.isNotBlank() || (settings.showPhone && settings.shopPhone.isNotBlank()) ||
            (settings.showAddress && settings.shopAddress.isNotBlank()) || (settings.showTaxNumber && settings.taxNumber.isNotBlank())

        return """
            <!DOCTYPE html>
            <html dir="rtl" lang="ar">
            <head>
                <meta charset="utf-8">
                <style>
                    @page { size: A4 portrait; margin: 5mm 10mm 10mm 10mm; }
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Cairo", "Tahoma", sans-serif; font-size: ${baseFontPx}px; color: #000; margin: 0; padding: 0; direction: rtl; }
                    .invoice-container { width: 100%; max-width: 100%; margin: 0; background: #fff; padding: 0; box-sizing: border-box; }
                    .custom-header-text { text-align: center; font-weight: 700; color: #111; margin-bottom: 4px; }
                    .header-center { text-align: center; margin-top: 0; margin-bottom: 8px; }
                    .shop-name { font-size: ${shopNamePx}px; font-weight: 900; margin: 0 0 3px 0; }
                    .shop-sub { font-size: ${tableFontPx}px; color: #333; margin: 2px 0; }
                    .section-divider { height: 2px; background-color: #1e293b; width: 100%; margin: 8px 0; }
                    .invoice-heading { text-align: right; font-size: ${(baseFontPx + 7).coerceAtLeast(16)}px; font-weight: 900; color: #000; margin: 4px 0 6px 0; }
                    .meta-row { display: flex; flex-direction: row; direction: ltr; gap: 12px; margin-bottom: 10px; width: 100%; }
                    .meta-values-table { flex: 1; border-collapse: collapse; }
                    .meta-labels-table { width: 112px; border-collapse: collapse; }
                    .meta-values-table td, .meta-labels-table td { border: 1px solid #000; padding: 4px 8px; font-size: ${tableFontPx}px; height: 21px; vertical-align: middle; text-align: right; }
                    .meta-labels-table td { font-weight: bold; background: #fff; }
                    .items-table { width: 100%; border-collapse: collapse; margin-bottom: 10px; direction: ltr; }
                    .items-table th { background-color: #CFD4DA; border: 1px solid #000; padding: 5px 2px; font-size: ${tableFontPx}px; font-weight: bold; text-align: center; }
                    .items-table td { border: 1px solid #000; padding: 3px 3px; font-size: ${tableFontPx}px; }
                    .total-box-wrapper { display: flex; justify-content: flex-start; direction: ltr; margin-bottom: 12px; }
                    .total-box { display: inline-table; border-collapse: collapse; width: 62%; border: 1px solid #000; background-color: #D6DCE5; direction: ltr; }
                    .total-box td { border: 1px solid #000; padding: 6px 8px; font-size: ${baseFontPx}px; font-weight: bold; text-align: center; }
                    .footer-info { text-align: center; font-size: ${tableFontPx}px; color: #111; margin-bottom: 4px; }
                    .barcode-box { text-align: center; font-weight: bold; font-size: ${(baseFontPx + 1)}px; letter-spacing: 2px; margin-top: 6px; }
                </style>
            </head>
            <body>
                <div class="invoice-container">
                    ${if (hasHeaderContent) """
                    <div class="header-center">
                        $headerImgHtml
                        $logoImgHtml
                        $headerTextHtml
                        ${if (shopName.isNotBlank()) "<div class='shop-name'>$shopName</div>" else ""}
                        ${if (settings.showPhone && settings.shopPhone.isNotBlank()) "<div class='shop-sub'>الهاتف: $shopPhone</div>" else ""}
                        ${if (settings.showAddress && settings.shopAddress.isNotBlank()) "<div class='shop-sub'>$shopAddress</div>" else ""}
                        ${if (settings.showTaxNumber && settings.taxNumber.isNotBlank()) "<div class='shop-sub'>الرقم الضريبي: $taxNumber</div>" else ""}
                    </div>
                    <div class="section-divider"></div>
                    """ else ""}
                    <div class="invoice-heading">الفاتورة</div>
                    <div class="meta-row">
                        <table class="meta-values-table">
                            <tr><td style="font-weight: bold;">$invoiceNum</td></tr>
                            <tr><td>$displayClient</td></tr>
                            ${if (cashierName.isNotBlank()) "<tr><td>$cashierName</td></tr>" else ""}
                            <tr><td>$displayDate</td></tr>
                        </table>
                        <table class="meta-labels-table">
                            <tr><td>فاتورة رقم :</td></tr>
                            <tr><td>الزبون :</td></tr>
                            ${if (cashierName.isNotBlank()) "<tr><td>البائع :</td></tr>" else ""}
                            <tr><td>التاريخ :</td></tr>
                        </table>
                    </div>
                    <table class="items-table">
                        <thead><tr><th>المجموع</th><th>ثمن</th><th>وحدة</th><th>كمية</th><th>المنتج</th></tr></thead>
                        <tbody>$rowsBuilder</tbody>
                    </table>
                    <div class="total-box-wrapper">
                        <table class="total-box">
                            <tr>
                                <td style="width: 58%; font-weight: 900;">${MoneyUtils.format(sale.totalAmountDouble, curr)}</td>
                                <td style="width: 42%; font-weight: 900;">المجموع</td>
                            </tr>
                        </table>
                    </div>
                    $settlementHtml
                    <div class="section-divider"></div>
                    $footerNoteHtml
                    $footerImgHtml
                    $barcodeHtml
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
