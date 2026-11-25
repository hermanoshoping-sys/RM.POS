package com.example.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.util.DocumentDesignHelper
import com.example.util.MoneyUtils
import com.example.util.StyledDocumentBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val A4_PAGE_WIDTH_PT = 595
internal const val A4_PAGE_HEIGHT_PT = 842
internal const val A4_MARGIN_LEFT_PT = 28.35f // 1 cm
internal const val A4_MARGIN_RIGHT_PT = 28.35f // 1 cm
internal const val A4_MARGIN_TOP_PT = 14.17f // 0.5 cm
internal const val A4_MARGIN_BOTTOM_PT = 28.35f // 1 cm

internal data class ReceiptPaints(
    val text: Paint,
    val bold: Paint,
    val title: Paint,
    val headerCustom: Paint,
    val border: Paint,
    val thickLine: Paint,
    val headerBg: Paint,
    val totalBg: Paint,
    val fontScale: Float
)

internal fun createReceiptPaints(settings: GlobalSettingsEntity): ReceiptPaints {
    val scale = DocumentDesignHelper.fontScale(settings)
    val paintText = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = (13f * scale).coerceIn(8.5f, 21f)
        isAntiAlias = true
    }
    val paintBold = Paint(paintText).apply {
        isFakeBoldText = true
        textSize = (13.5f * scale).coerceIn(9f, 22f)
    }
    val paintTitle = Paint(paintBold).apply {
        textSize = (21f * scale).coerceIn(14f, 30f)
        textAlign = Paint.Align.CENTER
    }
    val paintHeaderCustom = Paint(paintBold).apply {
        textSize = settings.headerFontSize.coerceIn(9, 26).toFloat()
        textAlign = Paint.Align.CENTER
    }
    val paintBorder = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
        isAntiAlias = true
    }
    val paintThickLine = Paint().apply {
        color = android.graphics.Color.rgb(30, 41, 59)
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        isAntiAlias = true
    }
    val paintHeaderBg = Paint().apply {
        color = android.graphics.Color.rgb(207, 212, 218)
        style = Paint.Style.FILL
    }
    val paintTotalBg = Paint().apply {
        color = android.graphics.Color.rgb(214, 220, 229)
        style = Paint.Style.FILL
    }
    return ReceiptPaints(
        paintText,
        paintBold,
        paintTitle,
        paintHeaderCustom,
        paintBorder,
        paintThickLine,
        paintHeaderBg,
        paintTotalBg,
        scale
    )
}

internal data class ReceiptColumns(
    val leftX: Float,
    val col1: Float,
    val col2: Float,
    val col3: Float,
    val col4: Float,
    val rightX: Float
)

internal fun computeReceiptColumns(width: Int): ReceiptColumns {
    val leftX = A4_MARGIN_LEFT_PT
    val rightX = width - A4_MARGIN_RIGHT_PT
    val contentW = (rightX - leftX).coerceAtLeast(100f)
    val col1 = leftX + (contentW * 0.14f) // المجموع (14%)
    val col2 = col1 + (contentW * 0.12f)  // ثمن (12%)
    val col3 = col2 + (contentW * 0.12f)  // وحدة (12%)
    val col4 = col3 + (contentW * 0.14f)  // كمية (14%), remaining 48% المنتج
    return ReceiptColumns(leftX, col1, col2, col3, col4, rightX)
}

internal fun drawStyledBitmapCentered(
    canvas: Canvas,
    centerX: Float,
    topY: Float,
    styled: StyledDocumentBitmap
): Float {
    val paintImg = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }
    val left = centerX - (styled.drawWidthPt / 2f)
    val dst = RectF(left, topY, left + styled.drawWidthPt, topY + styled.drawHeightPt)
    canvas.drawBitmap(styled.bitmap, null, dst, paintImg)
    return topY + styled.drawHeightPt
}

internal fun drawReceiptHeaderAndMeta(
    canvas: Canvas,
    sale: SaleEntity,
    width: Int,
    cols: ReceiptColumns,
    paints: ReceiptPaints,
    logoStyled: StyledDocumentBitmap?,
    headerStyled: StyledDocumentBitmap?,
    settings: GlobalSettingsEntity
): Float {
    var y = A4_MARGIN_TOP_PT
    val centerX = width / 2f
    val leftX = cols.leftX
    val rightX = cols.rightX

    if (headerStyled != null) {
        y = drawStyledBitmapCentered(canvas, centerX, y, headerStyled) + 6f
    }

    if (settings.showLogo && logoStyled != null) {
        y = drawStyledBitmapCentered(canvas, centerX, y, logoStyled) + 8f
    }

    if (DocumentDesignHelper.headerShowsText(settings)) {
        val textStartY = y
        val headerLines = settings.receiptHeader.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val lineStep = (-paints.headerCustom.ascent() + paints.headerCustom.descent() + 4f)
        for (line in headerLines) {
            y += -paints.headerCustom.ascent()
            canvas.drawText(line, centerX, y, paints.headerCustom)
            y += paints.headerCustom.descent() + 4f
        }
        val minHeaderBottom = textStartY + settings.headerHeight.coerceIn(24, 220).toFloat()
        if (headerStyled == null && y < minHeaderBottom && headerLines.isNotEmpty()) {
            y = minHeaderBottom.coerceAtMost(textStartY + (headerLines.size * lineStep) + 24f)
        }
    }

    if (settings.showShopName && settings.shopName.isNotBlank()) {
        y += -paints.title.ascent()
        canvas.drawText(settings.shopName, centerX, y, paints.title)
        y += 8f
    }
    val storeDetails = mutableListOf<String>()
    if (settings.showPhone && settings.shopPhone.isNotBlank()) storeDetails.add("الهاتف: ${settings.shopPhone}")
    if (settings.showAddress && settings.shopAddress.isNotBlank()) storeDetails.add(settings.shopAddress)
    if (storeDetails.isNotEmpty()) {
        paints.text.textAlign = Paint.Align.CENTER
        y += -paints.text.ascent() + 3f
        canvas.drawText(storeDetails.joinToString(" • "), centerX, y, paints.text)
        y += 5f
    }
    if (settings.showTaxNumber && settings.taxNumber.isNotBlank()) {
        paints.text.textAlign = Paint.Align.CENTER
        y += -paints.text.ascent() + 3f
        canvas.drawText("الرقم الضريبي: ${settings.taxNumber}", centerX, y, paints.text)
        y += 5f
    }
    if (y > A4_MARGIN_TOP_PT) {
        y += 6f
        canvas.drawLine(leftX, y, rightX, y, paints.thickLine)
        y += 8f
    }

    val paintInvoiceTitle = Paint(paints.bold).apply {
        textSize = (20f * paints.fontScale).coerceIn(15f, 28f)
        textAlign = Paint.Align.RIGHT
    }
    y += -paintInvoiceTitle.ascent()
    canvas.drawText("الفاتورة", rightX - 2f, y, paintInvoiceTitle)
    y += 10f

    val metaRowH = (23f * paints.fontScale).coerceIn(20f, 32f)
    val metaBoxTop = y
    val metaBoxH = metaRowH * 4f
    val metaBoxBottom = y + metaBoxH
    val metaRightW = (100f * paints.fontScale).coerceIn(90f, 136f)
    val metaRightLeft = rightX - metaRightW
    val metaLeftRight = metaRightLeft - 14f
    val metaBaselineOffset = (metaRowH * 0.68f)

    canvas.drawRect(metaRightLeft, metaBoxTop, rightX, metaBoxBottom, paints.border)
    for (r in 1..3) {
        val lineY = metaBoxTop + (metaRowH * r)
        canvas.drawLine(metaRightLeft, lineY, rightX, lineY, paints.border)
    }

    paints.bold.textAlign = Paint.Align.RIGHT
    canvas.drawText("فاتورة رقم :", rightX - 6f, metaBoxTop + metaBaselineOffset, paints.bold)
    canvas.drawText("الزبون :", rightX - 6f, metaBoxTop + metaRowH + metaBaselineOffset, paints.bold)
    canvas.drawText("البائع :", rightX - 6f, metaBoxTop + (metaRowH * 2f) + metaBaselineOffset, paints.bold)
    canvas.drawText("التاريخ :", rightX - 6f, metaBoxTop + (metaRowH * 3f) + metaBaselineOffset, paints.bold)

    canvas.drawRect(leftX, metaBoxTop, metaLeftRight, metaBoxBottom, paints.border)
    for (r in 1..3) {
        val lineY = metaBoxTop + (metaRowH * r)
        canvas.drawLine(leftX, lineY, metaLeftRight, lineY, paints.border)
    }

    paints.text.textAlign = Paint.Align.RIGHT
    val clientName = if (settings.showCustomerInfo) {
        if (!sale.customerName.isNullOrBlank()) sale.customerName else "زبون عادي"
    } else ""
    val cashierName = if (settings.showCashierName) sale.cashierName.trim() else ""
    val dateStr = if (settings.showDateTime) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(sale.timestamp))
    } else ""

    canvas.drawText(sale.invoiceNumber, metaLeftRight - 8f, metaBoxTop + metaBaselineOffset, paints.bold)
    canvas.drawText(clientName, metaLeftRight - 8f, metaBoxTop + metaRowH + metaBaselineOffset, paints.text)
    canvas.drawText(cashierName, metaLeftRight - 8f, metaBoxTop + (metaRowH * 2f) + metaBaselineOffset, paints.text)
    canvas.drawText(dateStr, metaLeftRight - 8f, metaBoxTop + (metaRowH * 3f) + metaBaselineOffset, paints.text)

    return metaBoxBottom + 10f
}

internal fun drawReceiptTableHeader(
    canvas: Canvas,
    startY: Float,
    cols: ReceiptColumns,
    paints: ReceiptPaints,
    headerH: Float
): Float {
    val baseline = startY + (headerH * 0.68f)
    canvas.drawRect(cols.leftX, startY, cols.rightX, startY + headerH, paints.headerBg)
    canvas.drawRect(cols.leftX, startY, cols.rightX, startY + headerH, paints.border)
    canvas.drawLine(cols.col1, startY, cols.col1, startY + headerH, paints.border)
    canvas.drawLine(cols.col2, startY, cols.col2, startY + headerH, paints.border)
    canvas.drawLine(cols.col3, startY, cols.col3, startY + headerH, paints.border)
    canvas.drawLine(cols.col4, startY, cols.col4, startY + headerH, paints.border)

    paints.bold.textAlign = Paint.Align.CENTER
    canvas.drawText("المجموع", (cols.leftX + cols.col1) / 2f, baseline, paints.bold)
    canvas.drawText("ثمن", (cols.col1 + cols.col2) / 2f, baseline, paints.bold)
    canvas.drawText("وحدة", (cols.col2 + cols.col3) / 2f, baseline, paints.bold)
    canvas.drawText("كمية", (cols.col3 + cols.col4) / 2f, baseline, paints.bold)
    canvas.drawText("المنتج", (cols.col4 + cols.rightX) / 2f, baseline, paints.bold)
    return startY + headerH
}

internal fun drawReceiptTableRow(
    canvas: Canvas,
    rowY: Float,
    rowH: Float,
    cols: ReceiptColumns,
    paints: ReceiptPaints,
    item: SaleItemEntity?
) {
    val rBottom = rowY + rowH
    val baseline = rowY + (rowH * 0.68f)
    canvas.drawLine(cols.leftX, rBottom, cols.rightX, rBottom, paints.border)
    canvas.drawLine(cols.leftX, rowY, cols.leftX, rBottom, paints.border)
    canvas.drawLine(cols.col1, rowY, cols.col1, rBottom, paints.border)
    canvas.drawLine(cols.col2, rowY, cols.col2, rBottom, paints.border)
    canvas.drawLine(cols.col3, rowY, cols.col3, rBottom, paints.border)
    canvas.drawLine(cols.col4, rowY, cols.col4, rBottom, paints.border)
    canvas.drawLine(cols.rightX, rowY, cols.rightX, rBottom, paints.border)

    if (item == null) return

    paints.text.textAlign = Paint.Align.CENTER
    canvas.drawText(String.format(Locale.US, "%.2f", item.totalLinePriceDouble), (cols.leftX + cols.col1) / 2f, baseline, paints.text)
    canvas.drawText(String.format(Locale.US, "%.2f", item.unitPriceDouble), (cols.col1 + cols.col2) / 2f, baseline, paints.text)
    canvas.drawText("قطعة", (cols.col2 + cols.col3) / 2f, baseline, paints.text)
    val qStr = if (item.quantity % 1.0 == 0.0) String.format(Locale.US, "%.0f", item.quantity) else String.format(Locale.US, "%.2f", item.quantity)
    canvas.drawText(qStr, (cols.col3 + cols.col4) / 2f, baseline, paints.text)

    val pName = item.productName.trim()
    val maxW = (cols.rightX - cols.col4) - 10f
    val baseProductSize = (12f * paints.fontScale).coerceIn(8.5f, 19f)
    val pPaint = Paint(paints.text).apply {
        textAlign = Paint.Align.RIGHT
        textSize = baseProductSize
    }
    val textW = pPaint.measureText(pName)
    if (textW <= maxW) {
        canvas.drawText(pName, cols.rightX - 5f, baseline, pPaint)
    } else {
        pPaint.textSize = (baseProductSize * (maxW / textW)).coerceIn(8.5f, baseProductSize)
        if (pPaint.measureText(pName) <= maxW) {
            canvas.drawText(pName, cols.rightX - 5f, baseline, pPaint)
        } else {
            val words = pName.split(" ")
            val smallSz = (baseProductSize * 0.78f).coerceIn(8f, 13f)
            pPaint.textSize = smallSz
            if (words.size > 2) {
                val mid = words.size / 2
                canvas.drawText(words.take(mid).joinToString(" "), cols.rightX - 5f, rowY + (rowH * 0.42f), pPaint)
                canvas.drawText(words.drop(mid).joinToString(" "), cols.rightX - 5f, rowY + (rowH * 0.84f), pPaint)
            } else {
                canvas.drawText(pName, cols.rightX - 5f, baseline, pPaint)
            }
        }
    }
}

/**
 * Optional settlement details shown when there is partial payment or cash change.
 * Remaining debt is intentionally omitted from printed invoices.
 */
internal fun buildReceiptSettlementDetails(sale: SaleEntity, settings: GlobalSettingsEntity): List<String> {
    val receiptCurrency = settings.currency.ifBlank { "MAD" }
    val details = mutableListOf<String>()
    val remainingDebt = sale.remainingDueDouble
    val hasPartialOrChange = sale.paymentMethod.uppercase() == "CREDIT" || remainingDebt > 0 || sale.changeAmountDouble > 0
    if (hasPartialOrChange) {
        if (sale.paidAmount > 0L) details.add("المدفوع: ${MoneyUtils.format(sale.paidAmountDouble, receiptCurrency)}")
        if (sale.changeAmountDouble > 0) details.add("الباقي: ${MoneyUtils.format(sale.changeAmountDouble, receiptCurrency)}")
    }
    return details
}

internal fun estimateReceiptSummaryHeight(
    sale: SaleEntity,
    settings: GlobalSettingsEntity,
    footerStyled: StyledDocumentBitmap?,
    fontScale: Float
): Float {
    val lineStep = (18f * fontScale).coerceIn(15f, 26f)
    val totalBoxH = (26f * fontScale).coerceIn(22f, 36f)
    var h = 10f + totalBoxH + 18f + 16f
    if (buildReceiptSettlementDetails(sale, settings).isNotEmpty()) h += lineStep
    if (DocumentDesignHelper.footerShowsText(settings)) {
        val footerText = settings.receiptFooter.ifBlank { "شكراً لزيارتكم - يرجى الاحتفاظ بالفاتورة" }
        val lines = footerText.split('\n').size.coerceAtLeast(1)
        h += maxOf(lines * lineStep, settings.footerHeight.coerceIn(24, 220).toFloat().coerceAtMost(lines * lineStep + 18f))
    }
    if (footerStyled != null) {
        h += footerStyled.drawHeightPt + 8f
    }
    if (settings.showBarcode) h += lineStep
    return h + 6f
}

internal fun drawReceiptSummaryAndFooter(
    canvas: Canvas,
    startY: Float,
    sale: SaleEntity,
    width: Int,
    cols: ReceiptColumns,
    paints: ReceiptPaints,
    footerStyled: StyledDocumentBitmap?,
    settings: GlobalSettingsEntity
): Float {
    var y = startY + 10f
    val centerX = width / 2f
    val contentW = cols.rightX - cols.leftX
    val lineStep = (18f * paints.fontScale).coerceIn(15f, 26f)

    val totalBoxW = (contentW * 0.62f).coerceIn(240f, contentW * 0.72f)
    val totalSplit = cols.leftX + (totalBoxW * 0.58f)
    val totalRight = cols.leftX + totalBoxW
    val totalBoxH = (26f * paints.fontScale).coerceIn(22f, 36f)

    canvas.drawRect(cols.leftX, y, totalRight, y + totalBoxH, paints.totalBg)
    canvas.drawRect(cols.leftX, y, totalRight, y + totalBoxH, paints.border)
    canvas.drawLine(totalSplit, y, totalSplit, y + totalBoxH, paints.border)

    paints.bold.textAlign = Paint.Align.CENTER
    val totBaseline = y + (totalBoxH * 0.68f)
    canvas.drawText("المجموع", (totalSplit + totalRight) / 2f, totBaseline, paints.bold)
    val receiptCurrency = settings.currency.ifBlank { "MAD" }
    canvas.drawText(MoneyUtils.format(sale.totalAmountDouble, receiptCurrency), (cols.leftX + totalSplit) / 2f, totBaseline, paints.bold)

    y += totalBoxH + 14f

    val settlementDetails = buildReceiptSettlementDetails(sale, settings)
    if (settlementDetails.isNotEmpty()) {
        paints.text.textAlign = Paint.Align.CENTER
        canvas.drawText(settlementDetails.joinToString(" • "), centerX, y + 6f, paints.text)
        y += lineStep
    }

    canvas.drawLine(cols.leftX, y, cols.rightX, y, paints.thickLine)
    y += lineStep

    if (DocumentDesignHelper.footerShowsText(settings)) {
        paints.text.textAlign = Paint.Align.CENTER
        val footerText = if (settings.receiptFooter.isNotBlank()) settings.receiptFooter else "شكراً لزيارتكم - يرجى الاحتفاظ بالفاتورة"
        val footerLines = footerText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        for (line in footerLines) {
            canvas.drawText(line, centerX, y, paints.text)
            y += lineStep
        }
    }

    if (footerStyled != null) {
        y = drawStyledBitmapCentered(canvas, centerX, y - (lineStep * 0.45f), footerStyled) + lineStep
    }

    if (settings.showBarcode) {
        paints.bold.textAlign = Paint.Align.CENTER
        canvas.drawText("* ${sale.invoiceNumber} *", centerX, y, paints.bold)
    }
    return y
}
