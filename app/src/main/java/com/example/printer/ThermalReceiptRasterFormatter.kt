package com.example.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.example.data.GlobalSettingsEntity
import com.example.ui.theme.Bilingual
import com.example.util.DocumentDesignHelper
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats POS receipts into monochrome 1-bit ESC/POS GS v 0 raster byte streams
 * for 58mm (384 dots) and 80mm (576 dots) thermal heads.
 *
 * Rendering Arabic (RTL) and French/English (LTR) via Android Canvas ensures
 * properly connected Arabic glyphs, exact RTL/LTR layout, custom font scaling,
 * and crisp monochrome store logo printing across all Bluetooth & USB thermal printers.
 */
internal object ThermalReceiptRasterFormatter {
    private const val TAG = "ThermalRasterFmt"
    private const val MAX_BAND_HEIGHT_PX = 220

    private val CMD_INIT = byteArrayOf(0x1B, 0x40)
    private val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val CMD_FEED_LINES = byteArrayOf(0x1B, 0x64, 0x04)
    private val CMD_CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x42, 0x10)

    fun renderReceiptToEscPosRaster(
        receipt: ThermalReceiptData,
        profile: ThermalPrintProfile,
        context: Context? = null
    ): ByteArray? {
        val widthPx = profile.paperWidth.dotsWidth
        val bitmap = renderReceiptBitmap(receipt, profile, context) ?: return null
        return try {
            val stream = ByteArrayOutputStream()
            stream.write(CMD_INIT)
            if (profile.openCashDrawer || receipt.openCashDrawer) {
                stream.write(CashDrawerManager.generateUniversalDrawerKick(profile.drawerPin))
            }
            stream.write(CMD_ALIGN_CENTER)
            appendBitmapAsEscPosBands(stream, bitmap, widthPx)
            val qrPayload = receipt.qrCodeData ?: receipt.invoiceNumber
            if (profile.printQrCode && qrPayload.isNotBlank()) {
                EscPosReceiptEncoder.appendQrToStream(stream, qrPayload)
                stream.write("\n".toByteArray(Charsets.UTF_8))
            }
            stream.write(CMD_FEED_LINES)
            if (profile.cutPaper) {
                stream.write(CMD_CUT_PAPER)
            }
            stream.toByteArray()
        } catch (t: Throwable) {
            Log.w(TAG, "Raster ESC/POS encoding failed; falling back to ESC/POS text", t)
            null
        } finally {
            runCatching { bitmap.recycle() }
        }
    }

    internal fun renderReceiptBitmap(
        receipt: ThermalReceiptData,
        profile: ThermalPrintProfile,
        context: Context? = null
    ): Bitmap? {
        return try {
            val widthPx = profile.paperWidth.dotsWidth
            val scale = profile.fontSize.scale * if (profile.paperWidth == ThermalPaperWidth.MM_80) 1.15f else 1.0f
            val lang = profile.language.uppercase(Locale.ROOT)
            val isRtl = profile.isRtl
            val padX = if (profile.paperWidth == ThermalPaperWidth.MM_80) 14f else 10f
            val contentW = widthPx - (padX * 2f)

            val logoBitmap = if (profile.showLogo && context != null) {
                runCatching {
                    val settings = GlobalSettingsEntity(
                        showLogo = true,
                        logoWidth = profile.logoMaxWidthPx.coerceIn(60, widthPx - 24),
                        logoHeight = 110
                    )
                    DocumentDesignHelper.loadLogoForDocument(context, settings, contentW)?.bitmap
                }.getOrNull()
            } else null

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = (24f * scale).coerceIn(18f, 36f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = (17f * scale).coerceIn(13f, 26f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = (16f * scale).coerceIn(12f, 24f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val bodyBoldPaint = Paint(bodyPaint).apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = (22f * scale).coerceIn(16f, 32f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val linePaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = 2f
            }

            val lineH = (bodyPaint.textSize * 1.42f)
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(receipt.timestamp))

            val headerLines = profile.headerText.trim().takeIf { it.isNotBlank() }?.lines()?.filter { it.isNotBlank() }.orEmpty()
            val effectiveFooter = profile.footerText.trim().ifBlank { receipt.footerMessage.trim() }
            val footerLines = effectiveFooter.takeIf { it.isNotBlank() }?.lines()?.filter { it.isNotBlank() }.orEmpty()

            // Calculate exact bitmap height
            var estimatedH = 18f
            if (logoBitmap != null) estimatedH += logoBitmap.height.coerceAtMost(130) + 12f
            if (!receipt.documentTitle.isNullOrBlank()) estimatedH += headerPaint.textSize * 1.5f
            if (receipt.storeName.isNotBlank()) estimatedH += titlePaint.textSize * 1.55f
            if (receipt.storeAddress.isNotBlank()) estimatedH += lineH
            if (receipt.storePhone.isNotBlank()) estimatedH += lineH
            if (receipt.taxId.isNotBlank()) estimatedH += lineH
            estimatedH += headerLines.size * lineH
            estimatedH += 16f // divider

            // Metadata rows
            estimatedH += lineH * 2 // invoice + date
            if (receipt.cashierName.isNotBlank()) estimatedH += lineH
            if (!receipt.customerName.isNullOrBlank()) estimatedH += lineH
            if (receipt.paymentMethod.isNotBlank()) estimatedH += lineH
            estimatedH += 18f // divider + table header
            estimatedH += lineH + 10f

            // Items (each item gets up to 2 lines if long on 58mm)
            estimatedH += (receipt.items.size.coerceAtLeast(1)) * (lineH * 1.45f)
            estimatedH += 16f // divider

            // Totals
            if (receipt.discountAmount > 0.0) estimatedH += lineH * 2
            if (receipt.taxAmount > 0.0) estimatedH += lineH
            estimatedH += totalPaint.textSize * 1.8f + 12f
            estimatedH += lineH * 2 // paid + change (no remaining debt per user requirement)
            estimatedH += 16f

            // Footer
            if (receipt.cashierName.isNotBlank()) estimatedH += lineH
            estimatedH += footerLines.size * lineH
            estimatedH += lineH + 26f

            val heightPx = estimatedH.toInt().coerceIn(180, 4800)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            var y = 14f
            val centerX = widthPx / 2f
            val leftX = padX
            val rightX = widthPx - padX

            fun drawDivider() {
                y += 4f
                canvas.drawLine(leftX, y, rightX, y, linePaint)
                y += 10f
            }

            fun drawPairRow(label: String, value: String, paint: Paint = bodyPaint) {
                y += paint.textSize
                if (isRtl) {
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(label, rightX, y, paint)
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(value, leftX, y, paint)
                } else {
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(label, leftX, y, paint)
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(value, rightX, y, paint)
                }
                y += (paint.textSize * 0.42f)
            }

            // 1. Logo
            if (logoBitmap != null) {
                val maxW = profile.logoMaxWidthPx.coerceIn(60, widthPx - 24).toFloat()
                val maxH = 120f
                val ratio = minOf(maxW / logoBitmap.width.coerceAtLeast(1), maxH / logoBitmap.height.coerceAtLeast(1), 1f)
                val dstW = (logoBitmap.width * ratio).toInt().coerceAtLeast(1)
                val dstH = (logoBitmap.height * ratio).toInt().coerceAtLeast(1)
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, dstW, dstH, true)
                canvas.drawBitmap(scaledLogo, (widthPx - dstW) / 2f, y, null)
                if (scaledLogo !== logoBitmap) scaledLogo.recycle()
                runCatching { logoBitmap.recycle() }
                y += dstH + 10f
            }

            // 2. Header & Store Info
            if (!receipt.documentTitle.isNullOrBlank()) {
                y += headerPaint.textSize
                canvas.drawText(receipt.documentTitle, centerX, y, headerPaint)
                y += 6f
            }
            if (receipt.storeName.isNotBlank()) {
                y += titlePaint.textSize
                canvas.drawText(receipt.storeName, centerX, y, titlePaint)
                y += 8f
            }
            bodyPaint.textAlign = Paint.Align.CENTER
            if (receipt.storeAddress.isNotBlank()) {
                y += bodyPaint.textSize
                canvas.drawText(receipt.storeAddress, centerX, y, bodyPaint)
                y += 4f
            }
            if (receipt.storePhone.isNotBlank()) {
                y += bodyPaint.textSize
                val telPrefix = Bilingual.tr(lang, "الهاتف:", "Tél:", "Tel:")
                canvas.drawText("$telPrefix ${receipt.storePhone}", centerX, y, bodyPaint)
                y += 4f
            }
            if (receipt.taxId.isNotBlank()) {
                y += bodyPaint.textSize
                canvas.drawText("ICE/IF: ${receipt.taxId}", centerX, y, bodyPaint)
                y += 4f
            }
            for (hLine in headerLines) {
                y += bodyPaint.textSize
                canvas.drawText(hLine, centerX, y, bodyPaint)
                y += 4f
            }
            drawDivider()

            // 3. Metadata
            drawPairRow(Bilingual.tr(lang, "رقم الفاتورة:", "Facture N°:", "Invoice #:"), receipt.invoiceNumber, bodyBoldPaint)
            drawPairRow(Bilingual.tr(lang, "التاريخ:", "Date:", "Date:"), dateStr)
            if (receipt.cashierName.isNotBlank()) {
                drawPairRow(Bilingual.tr(lang, "البائع:", "Vendeur:", "Cashier:"), receipt.cashierName)
            }
            if (!receipt.customerName.isNullOrBlank()) {
                drawPairRow(Bilingual.tr(lang, "الزبون:", "Client:", "Customer:"), receipt.customerName)
            }
            if (receipt.paymentMethod.isNotBlank()) {
                drawPairRow(
                    Bilingual.tr(lang, "الدفع:", "Paiement:", "Payment:"),
                    EscPosReceiptEncoder.formatPaymentMethodLocalized(receipt.paymentMethod, lang)
                )
            }
            drawDivider()

            // 4. Items table header
            val lblItem = Bilingual.tr(lang, "المنتج", "Article", "Item")
            val lblQty = Bilingual.tr(lang, "كمية", "Qté", "Qty")
            val lblTotal = Bilingual.tr(lang, "المجموع", "Total", "Total")
            val qtyColX = if (isRtl) leftX + contentW * 0.34f else rightX - contentW * 0.30f

            y += bodyBoldPaint.textSize
            if (isRtl) {
                bodyBoldPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(lblItem, rightX, y, bodyBoldPaint)
                bodyBoldPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(lblQty, qtyColX, y, bodyBoldPaint)
                bodyBoldPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(lblTotal, leftX, y, bodyBoldPaint)
            } else {
                bodyBoldPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(lblItem, leftX, y, bodyBoldPaint)
                bodyBoldPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(lblQty, qtyColX, y, bodyBoldPaint)
                bodyBoldPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(lblTotal, rightX, y, bodyBoldPaint)
            }
            y += 6f
            drawDivider()

            // 5. Items rows
            val maxNameChars = if (profile.paperWidth == ThermalPaperWidth.MM_80) 26 else 17
            for (item in receipt.items) {
                val qtyStr = if (item.quantity % 1.0 == 0.0) {
                    String.format(Locale.US, "%.0f", item.quantity)
                } else {
                    String.format(Locale.US, "%.2f", item.quantity)
                }
                val totalStr = String.format(Locale.US, "%.2f", item.total)
                val safeName = if (item.name.length > maxNameChars) item.name.take(maxNameChars - 1) + "…" else item.name

                y += bodyPaint.textSize
                if (isRtl) {
                    bodyPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(safeName, rightX, y, bodyPaint)
                    bodyPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(qtyStr, qtyColX, y, bodyPaint)
                    bodyPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(totalStr, leftX, y, bodyPaint)
                } else {
                    bodyPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(safeName, leftX, y, bodyPaint)
                    bodyPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(qtyStr, qtyColX, y, bodyPaint)
                    bodyPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(totalStr, rightX, y, bodyPaint)
                }
                y += (bodyPaint.textSize * 0.44f)
            }
            drawDivider()

            // 6. Totals (without remaining debt line on printed invoices)
            if (receipt.discountAmount > 0.0) {
                drawPairRow(
                    Bilingual.tr(lang, "المجموع الفرعي:", "Sous-total:", "Subtotal:"),
                    String.format(Locale.US, "%.2f", receipt.subtotal)
                )
                drawPairRow(
                    Bilingual.tr(lang, "الخصم:", "Remise:", "Discount:"),
                    String.format(Locale.US, "-%.2f", receipt.discountAmount)
                )
            }
            if (receipt.taxAmount > 0.0) {
                drawPairRow(
                    Bilingual.tr(lang, "الضريبة (TVA):", "TVA:", "Tax:"),
                    String.format(Locale.US, "%.2f", receipt.taxAmount)
                )
            }
            drawPairRow(
                Bilingual.tr(lang, "الإجمالي:", "TOTAL:", "TOTAL:"),
                String.format(Locale.US, "%.2f", receipt.grandTotal),
                totalPaint
            )
            drawDivider()
            drawPairRow(
                Bilingual.tr(lang, "المدفوع:", "Payé:", "Paid:"),
                String.format(Locale.US, "%.2f", receipt.paidAmount)
            )
            if (receipt.changeAmount > 0.0) {
                drawPairRow(
                    Bilingual.tr(lang, "الباقي:", "Monnaie:", "Change:"),
                    String.format(Locale.US, "%.2f", receipt.changeAmount)
                )
            }
            drawDivider()

            // 7. Footer
            bodyPaint.textAlign = Paint.Align.CENTER
            for (fLine in footerLines) {
                y += bodyPaint.textSize
                canvas.drawText(fLine, centerX, y, bodyPaint)
                y += 4f
            }
            val thanks = Bilingual.tr(lang, "*** شكراً لزيارتكم ***", "*** Merci de votre visite ***", "*** Thank you ***")
            y += bodyBoldPaint.textSize
            bodyBoldPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(thanks, centerX, y, bodyBoldPaint)
            y += 14f

            val croppedH = y.toInt().coerceIn(80, bitmap.height)
            if (croppedH < bitmap.height) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, croppedH)
                bitmap.recycle()
                cropped
            } else {
                bitmap
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed rendering thermal receipt bitmap", t)
            null
        }
    }

    /**
     * Converts an ARGB Bitmap into ESC/POS GS v 0 monochrome raster bands (<= 220 rows each).
     */
    internal fun appendBitmapAsEscPosBands(
        stream: ByteArrayOutputStream,
        source: Bitmap,
        targetWidthPx: Int
    ) {
        val alignedWidth = (targetWidthPx / 8) * 8
        val scaled = if (source.width != alignedWidth) {
            val newH = ((source.height.toFloat() * alignedWidth) / source.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, alignedWidth, newH, true)
        } else {
            source
        }
        try {
            val widthBytes = alignedWidth / 8
            val xL = (widthBytes and 0xFF).toByte()
            val xH = ((widthBytes shr 8) and 0xFF).toByte()
            val totalHeight = scaled.height
            val pixels = IntArray(alignedWidth * minOf(MAX_BAND_HEIGHT_PX, totalHeight))

            var startY = 0
            while (startY < totalHeight) {
                val bandH = minOf(MAX_BAND_HEIGHT_PX, totalHeight - startY)
                scaled.getPixels(pixels, 0, alignedWidth, 0, startY, alignedWidth, bandH)

                val yL = (bandH and 0xFF).toByte()
                val yH = ((bandH shr 8) and 0xFF).toByte()
                // GS v 0 m xL xH yL yH
                stream.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

                val bandBytes = ByteArray(widthBytes * bandH)
                var byteIdx = 0
                for (row in 0 until bandH) {
                    val rowOffset = row * alignedWidth
                    for (colByte in 0 until widthBytes) {
                        var packed = 0
                        val baseX = colByte * 8
                        for (bit in 0 until 8) {
                            val pixel = pixels[rowOffset + baseX + bit]
                            val r = (pixel shr 16) and 0xFF
                            val g = (pixel shr 8) and 0xFF
                            val b = pixel and 0xFF
                            val luma = (r * 299 + g * 587 + b * 114) / 1000
                            if (luma < 165) {
                                packed = packed or (1 shl (7 - bit))
                            }
                        }
                        bandBytes[byteIdx++] = packed.toByte()
                    }
                }
                stream.write(bandBytes)
                startY += bandH
            }
        } finally {
            if (scaled !== source) {
                runCatching { scaled.recycle() }
            }
        }
    }
}
