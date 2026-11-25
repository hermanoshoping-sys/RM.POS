package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.PurchaseEntity
import com.example.data.PurchaseItemEntity
import com.example.util.DocumentDesignHelper
import com.example.util.MoneyUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PURCHASE_A4_WIDTH_PT = 595
private const val PURCHASE_A4_HEIGHT_PT = 842
private const val PURCHASE_MARGIN_SIDE_PT = 28.35f // 1 cm
private const val PURCHASE_MARGIN_TOP_PT = 14.17f // 0.5 cm
private const val PURCHASE_MARGIN_BOTTOM_PT = 28.35f // 1 cm

private fun generatePurchaseReceiptA4Pdf(
    context: Context,
    file: File,
    purchase: PurchaseEntity,
    items: List<PurchaseItemEntity>,
    shopName: String,
    currency: String
) {
    val activeSettings = DocumentDesignHelper.resolveSettings(context)
    val fontScale = DocumentDesignHelper.fontScale(activeSettings)
    val contentW = PURCHASE_A4_WIDTH_PT - (PURCHASE_MARGIN_SIDE_PT * 2f)
    val logoStyled = DocumentDesignHelper.loadLogoForDocument(context, activeSettings, contentW)
    val headerStyled = DocumentDesignHelper.loadHeaderImageForDocument(context, activeSettings, contentW)
    val footerStyled = DocumentDesignHelper.loadFooterImageForDocument(context, activeSettings, contentW)

    val paintText = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = (13.5f * fontScale).coerceIn(9f, 21f)
        isAntiAlias = true
    }
    val paintBold = Paint(paintText).apply {
        isFakeBoldText = true
        textSize = (15f * fontScale).coerceIn(10f, 22f)
    }
    val paintTitle = Paint(paintBold).apply {
        textSize = (21f * fontScale).coerceIn(14f, 30f)
        textAlign = Paint.Align.CENTER
    }
    val paintLine = Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 1.2f
        isAntiAlias = true
    }

    val centerX = PURCHASE_A4_WIDTH_PT / 2f
    val leftX = PURCHASE_MARGIN_SIDE_PT
    val rightX = PURCHASE_A4_WIDTH_PT - PURCHASE_MARGIN_SIDE_PT
    val bottomLimit = PURCHASE_A4_HEIGHT_PT - PURCHASE_MARGIN_BOTTOM_PT
    val itemRowH = (26f * fontScale).coerceIn(20f, 38f)
    val lineStep = (22f * fontScale).coerceIn(17f, 32f)

    val pdfDoc = PdfDocument()
    try {
        var pageNum = 1
        var page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(PURCHASE_A4_WIDTH_PT, PURCHASE_A4_HEIGHT_PT, pageNum).create())
        var canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)

        fun drawItemsHeader(targetCanvas: Canvas, startY: Float): Float {
            paintBold.textAlign = Paint.Align.LEFT
            targetCanvas.drawText("Article / الصنف", leftX, startY, paintBold)
            paintBold.textAlign = Paint.Align.RIGHT
            targetCanvas.drawText("Total / المجموع", rightX, startY, paintBold)
            paintBold.textAlign = Paint.Align.LEFT
            targetCanvas.drawLine(leftX, startY + 8f, rightX, startY + 8f, paintLine)
            return startY + itemRowH
        }

        var y = DocumentDesignHelper.drawHeaderBranding(
            canvas = canvas,
            centerX = centerX,
            startY = PURCHASE_MARGIN_TOP_PT,
            settings = activeSettings,
            headerStyled = headerStyled,
            logoStyled = logoStyled
        )
        val effectiveShop = shopName.ifBlank { activeSettings.shopName }
        if (activeSettings.showShopName && effectiveShop.isNotBlank()) {
            y += -paintTitle.ascent()
            canvas.drawText(effectiveShop, centerX, y, paintTitle)
            y += lineStep + 4f
        } else {
            y += -paintBold.ascent()
        }
        paintBold.textAlign = Paint.Align.CENTER
        canvas.drawText("سند استلام بضاعة ومشتريات", centerX, y, paintBold)
        y += lineStep
        canvas.drawText("Bon de Réception - Achats", centerX, y, paintBold)
        y += lineStep

        paintText.textAlign = Paint.Align.CENTER
        canvas.drawText("قسم المشتريات والمخازن", centerX, y, paintText)
        y += 12f
        canvas.drawLine(leftX, y, rightX, y, paintLine)
        y += lineStep

        paintText.textAlign = Paint.Align.LEFT
        canvas.drawText("Purchase ID / رقم السند: ${purchase.displayInvoiceNumber}", leftX, y, paintText)
        y += lineStep
        canvas.drawText("Date: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(purchase.timestamp))}", leftX, y, paintText)
        y += lineStep
        canvas.drawText("Fournisseur / المورد: ${purchase.supplierName}", leftX, y, paintText)
        y += 12f
        canvas.drawLine(leftX, y, rightX, y, paintLine)
        y += lineStep

        y = drawItemsHeader(canvas, y)

        for (item in items) {
            if (y + itemRowH > bottomLimit) {
                pdfDoc.finishPage(page)
                pageNum++
                page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(PURCHASE_A4_WIDTH_PT, PURCHASE_A4_HEIGHT_PT, pageNum).create())
                canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                y = drawItemsHeader(canvas, PURCHASE_MARGIN_TOP_PT - paintBold.ascent())
            }
            val qty = String.format(Locale.US, "%.1f", item.quantity)
            val tot = String.format(Locale.US, "%.2f", item.quantity * item.unitPriceDouble)
            paintText.textAlign = Paint.Align.LEFT
            canvas.drawText("$qty x ${item.productName}", leftX, y, paintText)
            paintText.textAlign = Paint.Align.RIGHT
            canvas.drawText(tot, rightX, y, paintText)
            paintText.textAlign = Paint.Align.LEFT
            y += itemRowH
        }

        val customFooterH = DocumentDesignHelper.estimateFooterBrandingHeight(
            activeSettings,
            footerStyled,
            "تمت مطابقة واستلام البضاعة بالمستودع\nMarchandise réceptionnée et vérifiée"
        )
        val summaryNeededH = (lineStep * 4.5f) + customFooterH + 40f
        if (y + summaryNeededH > bottomLimit) {
            pdfDoc.finishPage(page)
            pageNum++
            page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(PURCHASE_A4_WIDTH_PT, PURCHASE_A4_HEIGHT_PT, pageNum).create())
            canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            y = PURCHASE_MARGIN_TOP_PT + 10f
        }

        canvas.drawLine(leftX, y, rightX, y, paintLine)
        y += lineStep + 4f

        paintBold.textAlign = Paint.Align.LEFT
        canvas.drawText("Total Achat / الإجمالي:", leftX, y, paintBold)
        paintBold.textAlign = Paint.Align.RIGHT
        canvas.drawText(MoneyUtils.format(purchase.totalAmountDouble, currency), rightX, y, paintBold)
        paintBold.textAlign = Paint.Align.LEFT
        y += lineStep + 2f

        paintText.textAlign = Paint.Align.LEFT
        canvas.drawText("Payé / المدفوع:", leftX, y, paintText)
        paintText.textAlign = Paint.Align.RIGHT
        canvas.drawText(MoneyUtils.format(purchase.paidAmountDouble, currency), rightX, y, paintText)
        paintText.textAlign = Paint.Align.LEFT
        y += lineStep + 10f

        DocumentDesignHelper.drawFooterBranding(
            canvas = canvas,
            centerX = centerX,
            startY = y,
            bottomLimit = bottomLimit,
            settings = activeSettings,
            footerStyled = footerStyled,
            fallbackText = "تمت مطابقة واستلام البضاعة بالمستودع\nMarchandise réceptionnée et vérifiée",
            textColor = android.graphics.Color.BLACK
        )

        pdfDoc.finishPage(page)
        FileOutputStream(file).use { out ->
            pdfDoc.writeTo(out)
            out.flush()
        }
    } finally {
        try {
            pdfDoc.close()
        } catch (closeErr: Throwable) {
            Log.w("ReportsScreen", "Error closing purchase PdfDocument", closeErr)
        }
        runCatching { logoStyled?.bitmap?.recycle() }
        runCatching { headerStyled?.bitmap?.recycle() }
        runCatching { footerStyled?.bitmap?.recycle() }
    }
}

fun sharePurchaseReceipt(
    context: Context,
    purchase: PurchaseEntity,
    items: List<PurchaseItemEntity>,
    asPdf: Boolean,
    shopName: String = "",
    currency: String = "MAD"
) {
    try {
        val fileName = if (asPdf) "purchase_${purchase.id}.pdf" else "purchase_${purchase.id}.png"
        val cacheDir = File(context.cacheDir, "shared_files")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val file = File(cacheDir, fileName)

        if (asPdf) {
            generatePurchaseReceiptA4Pdf(context, file, purchase, items, shopName, currency)
        } else {
            val activeSettings = DocumentDesignHelper.resolveSettings(context)
            val fontScale = DocumentDesignHelper.fontScale(activeSettings)
            val width = PURCHASE_A4_WIDTH_PT
            val height = (520 + (items.size * (32 * fontScale).toInt()) + activeSettings.logoHeight + activeSettings.headerHeight + activeSettings.footerHeight)
                .coerceAtLeast(PURCHASE_A4_HEIGHT_PT)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                drawPurchaseReceiptContent(canvas, purchase, items, width, shopName, currency, context)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
            } finally {
                bitmap.recycle()
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (asPdf) "application/pdf" else "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserTitle = if (asPdf) "Share PDF / مشاركة كـ PDF" else "Share Image / مشاركة كصورة"
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: Exception) {
        Log.e("ReportsScreen", "Error sharing purchase receipt", e)
    }
}

fun drawPurchaseReceiptContent(
    canvas: Canvas,
    purchase: PurchaseEntity,
    items: List<PurchaseItemEntity>,
    width: Int,
    shopName: String = "",
    currency: String = "MAD",
    context: Context? = null
) {
    val activeSettings = context?.let { DocumentDesignHelper.resolveSettings(it) } ?: com.example.data.GlobalSettingsEntity()
    val fontScale = DocumentDesignHelper.fontScale(activeSettings)
    val contentW = width - (PURCHASE_MARGIN_SIDE_PT * 2f)
    val logoStyled = context?.let { DocumentDesignHelper.loadLogoForDocument(it, activeSettings, contentW) }
    val headerStyled = context?.let { DocumentDesignHelper.loadHeaderImageForDocument(it, activeSettings, contentW) }
    val footerStyled = context?.let { DocumentDesignHelper.loadFooterImageForDocument(it, activeSettings, contentW) }

    try {
        val paintText = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = (14.5f * fontScale).coerceIn(9.5f, 22f)
            isAntiAlias = true
        }
        val paintBold = Paint(paintText).apply {
            isFakeBoldText = true
            textSize = (16f * fontScale).coerceIn(10.5f, 23f)
        }
        val paintTitle = Paint(paintBold).apply {
            textSize = (23f * fontScale).coerceIn(15f, 30f)
            textAlign = Paint.Align.CENTER
        }
        val paintLine = Paint().apply {
            color = android.graphics.Color.BLACK
            strokeWidth = 1.2f
            isAntiAlias = true
        }

        val centerX = width / 2f
        val leftX = PURCHASE_MARGIN_SIDE_PT
        val rightX = width - PURCHASE_MARGIN_SIDE_PT
        val lineStep = (26f * fontScale).coerceIn(20f, 36f)

        var y = DocumentDesignHelper.drawHeaderBranding(
            canvas = canvas,
            centerX = centerX,
            startY = PURCHASE_MARGIN_TOP_PT,
            settings = activeSettings,
            headerStyled = headerStyled,
            logoStyled = logoStyled
        )

        val effectiveShop = shopName.ifBlank { activeSettings.shopName }
        if (activeSettings.showShopName && effectiveShop.isNotBlank()) {
            y += -paintTitle.ascent()
            canvas.drawText(effectiveShop, centerX, y, paintTitle)
            y += lineStep + 4f
        } else {
            y += -paintBold.ascent()
        }
        paintBold.textAlign = Paint.Align.CENTER
        canvas.drawText("سند استلام بضاعة ومشتريات", centerX, y, paintBold)
        y += lineStep
        canvas.drawText("Bon de Réception - Achats", centerX, y, paintBold)
        y += lineStep

        paintText.textAlign = Paint.Align.CENTER
        canvas.drawText("قسم المشتريات والمخازن", centerX, y, paintText)
        y += 16f

        canvas.drawLine(leftX, y, rightX, y, paintLine)
        y += lineStep

        paintText.textAlign = Paint.Align.LEFT
        canvas.drawText("Purchase ID / رقم السند: ${purchase.displayInvoiceNumber}", leftX, y, paintText)
        y += lineStep
        canvas.drawText("Date: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(purchase.timestamp))}", leftX, y, paintText)
        y += lineStep
        canvas.drawText("Fournisseur / المورد: ${purchase.supplierName}", leftX, y, paintText)
        y += 16f

        canvas.drawLine(leftX, y, rightX, y, paintLine)
        y += lineStep

        paintBold.textAlign = Paint.Align.LEFT
        canvas.drawText("Article / الصنف", leftX, y, paintBold)
        paintBold.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total / المجموع", rightX, y, paintBold)
        paintBold.textAlign = Paint.Align.LEFT
        y += lineStep

        for (item in items) {
            val qty = String.format(Locale.US, "%.1f", item.quantity)
            val tot = String.format(Locale.US, "%.2f", item.quantity * item.unitPriceDouble)
            canvas.drawText("$qty x ${item.productName}", leftX, y, paintText)
            paintText.textAlign = Paint.Align.RIGHT
            canvas.drawText(tot, rightX, y, paintText)
            paintText.textAlign = Paint.Align.LEFT
            y += lineStep
        }

        canvas.drawLine(leftX, y, rightX, y, paintLine)
        y += lineStep + 4f

        canvas.drawText("Total Achat / الإجمالي:", leftX, y, paintBold)
        paintBold.textAlign = Paint.Align.RIGHT
        canvas.drawText(MoneyUtils.format(purchase.totalAmountDouble, currency), rightX, y, paintBold)
        paintBold.textAlign = Paint.Align.LEFT
        y += lineStep + 2f

        canvas.drawText("Payé / المدفوع:", leftX, y, paintText)
        paintText.textAlign = Paint.Align.RIGHT
        canvas.drawText(MoneyUtils.format(purchase.paidAmountDouble, currency), rightX, y, paintText)
        paintText.textAlign = Paint.Align.LEFT
        y += lineStep + 10f

        DocumentDesignHelper.drawFooterBranding(
            canvas = canvas,
            centerX = centerX,
            startY = y,
            bottomLimit = canvas.height - PURCHASE_MARGIN_BOTTOM_PT,
            settings = activeSettings,
            footerStyled = footerStyled,
            fallbackText = "تمت مطابقة واستلام البضاعة بالمستودع\nMarchandise réceptionnée et vérifiée",
            textColor = android.graphics.Color.BLACK
        )
    } finally {
        runCatching { logoStyled?.bitmap?.recycle() }
        runCatching { headerStyled?.bitmap?.recycle() }
        runCatching { footerStyled?.bitmap?.recycle() }
    }
}

fun shareDocument(
    context: Context,
    docTitle: String,
    subtitle: String,
    headers: List<String>,
    rows: List<List<String>>,
    summaryLines: List<Pair<String, String>> = emptyList(),
    asPdf: Boolean = true,
    shopName: String = ""
) {
    PdfReportExporter.exportTableDocument(
        context = context,
        docTitle = docTitle,
        subtitle = subtitle,
        headers = headers,
        rows = rows,
        summaryLines = summaryLines,
        asPdf = asPdf,
        shopName = shopName
    )
}

fun printDocument(
    context: Context,
    docTitle: String,
    subtitle: String,
    headers: List<String>,
    rows: List<List<String>>,
    summaryLines: List<Pair<String, String>> = emptyList(),
    shopName: String = ""
) {
    PdfReportExporter.printTableDocument(
        context = context,
        docTitle = docTitle,
        subtitle = subtitle,
        headers = headers,
        rows = rows,
        summaryLines = summaryLines,
        shopName = shopName
    )
}
