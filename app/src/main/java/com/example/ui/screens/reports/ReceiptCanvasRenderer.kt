package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.util.DocumentDesignHelper
import com.example.util.StyledDocumentBitmap
import java.io.File
import java.io.FileOutputStream

fun generateSaleReceiptPdfFile(
    context: Context,
    sale: SaleEntity,
    items: List<SaleItemEntity>,
    settings: GlobalSettingsEntity? = null
): File {
    val safeInv = sale.invoiceNumber.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { sale.id.toString() }
    val cacheDir = File(context.cacheDir, "shared_files")
    if (!cacheDir.exists()) cacheDir.mkdirs()
    val file = File(cacheDir, "receipt_$safeInv.pdf")

    val activeSettings = DocumentDesignHelper.resolveSettings(context, settings)
    val contentW = A4_PAGE_WIDTH_PT - A4_MARGIN_LEFT_PT - A4_MARGIN_RIGHT_PT
    val logoStyled = DocumentDesignHelper.loadLogoForDocument(context, activeSettings, contentW)
    val headerStyled = DocumentDesignHelper.loadHeaderImageForDocument(context, activeSettings, contentW)
    val footerStyled = DocumentDesignHelper.loadFooterImageForDocument(context, activeSettings, contentW)

    val paints = createReceiptPaints(activeSettings)
    val cols = computeReceiptColumns(A4_PAGE_WIDTH_PT)
    val headerH = (24f * paints.fontScale).coerceIn(22f, 36f)
    val rowH = (22f * paints.fontScale).coerceIn(19f, 34f)
    val bottomLimit = A4_PAGE_HEIGHT_PT - A4_MARGIN_BOTTOM_PT
    val summaryH = estimateReceiptSummaryHeight(sale, activeSettings, footerStyled, paints.fontScale)

    val pdfDoc = PdfDocument()
    try {
        var pageNum = 1
        var page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(A4_PAGE_WIDTH_PT, A4_PAGE_HEIGHT_PT, pageNum).create())
        var canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)

        var y = drawReceiptHeaderAndMeta(canvas, sale, A4_PAGE_WIDTH_PT, cols, paints, logoStyled, headerStyled, activeSettings)
        y = drawReceiptTableHeader(canvas, y, cols, paints, headerH)

        for (item in items) {
            if (y + rowH > bottomLimit) {
                pdfDoc.finishPage(page)
                pageNum++
                page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(A4_PAGE_WIDTH_PT, A4_PAGE_HEIGHT_PT, pageNum).create())
                canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                y = drawReceiptTableHeader(canvas, A4_MARGIN_TOP_PT, cols, paints, headerH)
            }
            drawReceiptTableRow(canvas, y, rowH, cols, paints, item)
            y += rowH
        }

        while (y + rowH + summaryH <= bottomLimit) {
            drawReceiptTableRow(canvas, y, rowH, cols, paints, null)
            y += rowH
        }

        if (y + summaryH > bottomLimit) {
            pdfDoc.finishPage(page)
            pageNum++
            page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(A4_PAGE_WIDTH_PT, A4_PAGE_HEIGHT_PT, pageNum).create())
            canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            y = A4_MARGIN_TOP_PT
        }

        drawReceiptSummaryAndFooter(canvas, y, sale, A4_PAGE_WIDTH_PT, cols, paints, footerStyled, activeSettings)
        pdfDoc.finishPage(page)

        FileOutputStream(file).use { out ->
            pdfDoc.writeTo(out)
            out.flush()
        }
    } finally {
        try {
            pdfDoc.close()
        } catch (closeErr: Throwable) {
            Log.w("ReportsScreen", "Error closing PdfDocument", closeErr)
        }
        runCatching { logoStyled?.bitmap?.recycle() }
        runCatching { headerStyled?.bitmap?.recycle() }
        runCatching { footerStyled?.bitmap?.recycle() }
    }
    return file
}

fun shareReceipt(
    context: Context,
    sale: SaleEntity,
    items: List<SaleItemEntity>,
    asPdf: Boolean,
    settings: GlobalSettingsEntity? = null
) {
    try {
        val activeSettings = DocumentDesignHelper.resolveSettings(context, settings)
        val file = if (asPdf) {
            generateSaleReceiptPdfFile(context, sale, items, activeSettings)
        } else {
            val safeInv = sale.invoiceNumber.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { sale.id.toString() }
            val cacheDir = File(context.cacheDir, "shared_files")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val imgFile = File(cacheDir, "receipt_$safeInv.png")
            val contentW = A4_PAGE_WIDTH_PT - A4_MARGIN_LEFT_PT - A4_MARGIN_RIGHT_PT
            val logoStyled = DocumentDesignHelper.loadLogoForDocument(context, activeSettings, contentW)
            val headerStyled = DocumentDesignHelper.loadHeaderImageForDocument(context, activeSettings, contentW)
            val footerStyled = DocumentDesignHelper.loadFooterImageForDocument(context, activeSettings, contentW)
            val scale = DocumentDesignHelper.fontScale(activeSettings)
            val extraImgH = ((logoStyled?.drawHeightPt ?: 0f) + (headerStyled?.drawHeightPt ?: 0f) + (footerStyled?.drawHeightPt ?: 0f)).toInt() + 40
            val width = A4_PAGE_WIDTH_PT
            val minGridRows = maxOf(items.size, 20)
            val rowH = (22f * scale).coerceIn(19f, 34f)
            val summaryH = estimateReceiptSummaryHeight(sale, activeSettings, footerStyled, scale).toInt()
            val height = (340 + (minGridRows * rowH.toInt()) + summaryH + extraImgH).coerceAtLeast(A4_PAGE_HEIGHT_PT)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                val paints = createReceiptPaints(activeSettings)
                val cols = computeReceiptColumns(width)
                val headerH = (24f * paints.fontScale).coerceIn(22f, 36f)
                var y = drawReceiptHeaderAndMeta(canvas, sale, width, cols, paints, logoStyled, headerStyled, activeSettings)
                y = drawReceiptTableHeader(canvas, y, cols, paints, headerH)
                for (r in 0 until minGridRows) {
                    drawReceiptTableRow(canvas, y, rowH, cols, paints, items.getOrNull(r))
                    y += rowH
                }
                drawReceiptSummaryAndFooter(canvas, y, sale, width, cols, paints, footerStyled, activeSettings)
                FileOutputStream(imgFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
            } finally {
                bitmap.recycle()
                runCatching { logoStyled?.bitmap?.recycle() }
                runCatching { headerStyled?.bitmap?.recycle() }
                runCatching { footerStyled?.bitmap?.recycle() }
            }
            imgFile
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (asPdf) "application/pdf" else "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(
            intent,
            if (asPdf) "Share PDF / مشاركة كـ PDF" else "Share Image / مشاركة كصورة"
        ).apply {
            if (com.example.printer.BluetoothPrinterService.findActivity(context) == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Log.e("ReportsScreen", "Error sharing receipt PDF or Image", e)
        val appCtx = context.applicationContext ?: context
        Toast.makeText(
            appCtx,
            "تعذر مشاركة الفاتورة: ${e.localizedMessage ?: "خطأ"}",
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun drawReceiptContent(
    canvas: Canvas,
    sale: SaleEntity,
    items: List<SaleItemEntity>,
    width: Int,
    logoBitmap: Bitmap? = null,
    settings: GlobalSettingsEntity = GlobalSettingsEntity()
) {
    val paints = createReceiptPaints(settings)
    val cols = computeReceiptColumns(width)
    val headerH = (24f * paints.fontScale).coerceIn(22f, 36f)
    val rowH = (22f * paints.fontScale).coerceIn(19f, 34f)
    val logoStyled = logoBitmap?.let {
        val targetW = settings.logoWidth.coerceIn(40, 320).toFloat()
        val targetH = settings.logoHeight.coerceIn(30, 220).toFloat()
        val aspect = minOf(targetW / it.width.coerceAtLeast(1), targetH / it.height.coerceAtLeast(1))
        StyledDocumentBitmap(it, it.width * aspect, it.height * aspect)
    }

    var y = drawReceiptHeaderAndMeta(canvas, sale, width, cols, paints, logoStyled, null, settings)
    y = drawReceiptTableHeader(canvas, y, cols, paints, headerH)

    val minRows = maxOf(items.size, 20)
    for (r in 0 until minRows) {
        drawReceiptTableRow(canvas, y, rowH, cols, paints, items.getOrNull(r))
        y += rowH
    }

    drawReceiptSummaryAndFooter(canvas, y, sale, width, cols, paints, null, settings)
}
