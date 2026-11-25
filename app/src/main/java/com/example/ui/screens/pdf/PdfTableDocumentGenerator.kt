package com.example.ui.screens

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.widget.Toast
import com.example.util.DocumentDesignHelper
import java.io.File
import java.io.FileOutputStream

internal object PdfTableDocumentGenerator {
    fun generateTableDocumentPdf(
        context: Context,
        docTitle: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryLines: List<Pair<String, String>> = emptyList(),
        shopName: String = ""
    ): File {
        val safeDocTitle = docTitle.ifBlank { "Document" }
        val safeHeaders = headers.ifEmpty { listOf("البيان") }
        val safeName = safeDocTitle.replace("[^a-zA-Z0-9ء-ي]".toRegex(), "_").take(25).ifBlank { "document" }
        val file = PdfPrintAndShareUtils.createCacheFile(context, "${safeName}_${System.currentTimeMillis()}.pdf")

        val activeSettings = DocumentDesignHelper.resolveSettings(context)
        val fontScale = DocumentDesignHelper.fontScale(activeSettings)

        val isWideTable = safeHeaders.size >= 5 || safeDocTitle.contains("مبيعات") || safeDocTitle.contains("Ventes")
        val pageWidth = if (isWideTable) 842 else 595
        val pageHeight = if (isWideTable) 595 else 842
        val leftMargin = 28.35f // 1 cm
        val rightMargin = pageWidth - 28.35f // 1 cm
        val topMargin = 14.17f // 0.5 cm
        val bottomMargin = 28.35f // 1 cm
        val tableWidth = rightMargin - leftMargin

        val isRtl = safeHeaders.any { it.any { c -> c in '\u0600'..'\u06FF' } }
        val isSalesJournal = safeDocTitle.contains("مبيعات") || safeHeaders.any { it.contains("ثمن البيع") || it.contains("سعر البيع") }

        val colWeights = FloatArray(safeHeaders.size) { i ->
            val h = safeHeaders[i].trim()
            when {
                h.contains("المنتج") || h.contains("البيان") || h.contains("Produit") || h.contains("Description") -> 2.8f
                h.contains("الزبون") || h.contains("المورد") || h.contains("Client") || h.contains("Fournisseur") || h.contains("compte", ignoreCase = true) || h.contains("الطرف") || h.contains("الحساب") -> 2.3f
                h.contains("التاريخ") || h.contains("Date") -> 1.10f
                h.contains("الفاتورة") || h.contains("السند") || h.contains("Facture") || h.contains("N°") -> 0.85f
                h.contains("الكمية") || h.contains("Qté") || h.contains("العدد") -> 0.80f
                h.contains("الشراء") || h.contains("Achat") -> 0.95f
                h.contains("البيع") || h.contains("Vente") || h.contains("المبلغ") || h.contains("Montant") -> 0.95f
                h.contains("الربح") || h.contains("Benefice") || h.contains("Gain") -> 0.95f
                else -> 1.0f
            }
        }
        val totalWeight = colWeights.sum().coerceAtLeast(1f)
        val colWidths = colWeights.map { (it / totalWeight) * tableWidth }

        val colLefts = FloatArray(safeHeaders.size)
        val colRights = FloatArray(safeHeaders.size)
        if (isRtl) {
            var currentR = rightMargin
            for (i in safeHeaders.indices) {
                colRights[i] = currentR
                colLefts[i] = currentR - colWidths[i]
                currentR -= colWidths[i]
            }
        } else {
            var currentL = leftMargin
            for (i in safeHeaders.indices) {
                colLefts[i] = currentL
                colRights[i] = currentL + colWidths[i]
                currentL += colWidths[i]
            }
        }

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = (10f * fontScale).coerceIn(7.5f, 16f) }
        val paintBold = Paint(paintText).apply { isFakeBoldText = true; textSize = (10.5f * fontScale).coerceIn(8f, 17f) }
        val paintTitle = Paint(paintBold).apply { textSize = (20f * fontScale).coerceIn(14f, 28f); textAlign = Paint.Align.CENTER }
        val paintSub = Paint(paintText).apply { color = Color.rgb(71, 85, 105); textSize = (10.5f * fontScale).coerceIn(8f, 16f); textAlign = Paint.Align.CENTER }
        val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 0.9f }
        val paintThickBorder = Paint(paintBorder).apply { strokeWidth = 1.8f }

        val headerBgColor = if (isSalesJournal) Color.rgb(255, 0, 0) else Color.rgb(220, 38, 38)
        val headerTextColor = if (isSalesJournal) Color.BLACK else Color.WHITE
        val paintHeaderBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = headerBgColor; style = Paint.Style.FILL }
        val paintHeaderText = Paint(paintBold).apply { color = headerTextColor; textSize = (11.2f * fontScale).coerceIn(8f, 17f); textAlign = Paint.Align.CENTER }

        val rowHeight = (24f * fontScale).coerceIn(20f, 36f)
        val headerHeight = (30f * fontScale).coerceIn(25f, 42f)

        val logoStyled = DocumentDesignHelper.loadLogoForDocument(context, activeSettings, tableWidth)
        val headerStyled = DocumentDesignHelper.loadHeaderImageForDocument(context, activeSettings, tableWidth)
        val footerStyled = DocumentDesignHelper.loadFooterImageForDocument(context, activeSettings, tableWidth)

        val extraTopH = ((logoStyled?.drawHeightPt ?: 0f) + (headerStyled?.drawHeightPt ?: 0f)).coerceAtMost(260f)
        val usableRowsHeight = (pageHeight - topMargin - bottomMargin - 160f - extraTopH).coerceAtLeast(rowHeight * 4f)
        val rowsPerPage = (usableRowsHeight / rowHeight).toInt().coerceIn(5, if (isWideTable) 14 else 20)
        val totalPages = ((rows.size + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1)
        val pdfDoc = PdfDocument()

        val resolvedShop = if (shopName.isNotBlank() && shopName != "الشركة" && shopName != "My Application") {
            shopName
        } else {
            activeSettings.shopName.takeIf { it.isNotBlank() && it != "My Application" } ?: ""
        }
        val storeDisplay = resolvedShop.ifBlank { if (isRtl) "المتجر" else "Entreprise" }

        try {
            for (pageIndex in 0 until totalPages) {
                val page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
                val canvas = page.canvas
                var y = if (pageIndex == 0) {
                    DocumentDesignHelper.drawHeaderBranding(
                        canvas = canvas,
                        centerX = pageWidth / 2f,
                        startY = topMargin,
                        settings = activeSettings,
                        headerStyled = headerStyled,
                        logoStyled = logoStyled
                    )
                } else {
                    topMargin
                }

                if (activeSettings.showShopName) {
                    y += -paintTitle.ascent()
                    canvas.drawText(storeDisplay, pageWidth / 2f, y, paintTitle)
                    y += (22f * fontScale).coerceIn(18f, 30f)
                } else {
                    y += 18f
                }

                val docBannerW = 360f
                val bannerLeft = (pageWidth - docBannerW) / 2f
                val bannerRect = RectF(bannerLeft, y - 16f, bannerLeft + docBannerW, y + 10f)
                val bannerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 245, 249); style = Paint.Style.FILL }
                canvas.drawRoundRect(bannerRect, 6f, 6f, bannerBg)
                canvas.drawRoundRect(bannerRect, 6f, 6f, paintBorder)

                val paintDocTitle = Paint(paintBold).apply { color = Color.rgb(15, 23, 42); textSize = (13.5f * fontScale).coerceIn(10f, 19f); textAlign = Paint.Align.CENTER }
                canvas.drawText(safeDocTitle, pageWidth / 2f, y, paintDocTitle)
                y += (18f * fontScale).coerceIn(15f, 24f)

                if (subtitle.isNotBlank()) {
                    canvas.drawText(subtitle, pageWidth / 2f, y, paintSub)
                    y += (16f * fontScale).coerceIn(13f, 22f)
                }

                canvas.drawText("تاريخ وتوقيت الاستخراج: ${PdfPrintAndShareUtils.formatTimestamp()}", pageWidth / 2f, y, paintSub)
                y += 14f

                canvas.drawRect(leftMargin, y, rightMargin, y + headerHeight, paintHeaderBg)
                canvas.drawRect(leftMargin, y, rightMargin, y + headerHeight, paintThickBorder)
                val headerBase = y + (headerHeight * 0.66f)
                for (i in safeHeaders.indices) {
                    val cx = (colLefts[i] + colRights[i]) / 2f
                    canvas.drawLine(colLefts[i], y, colLefts[i], y + headerHeight, paintBorder)
                    canvas.drawLine(colRights[i], y, colRights[i], y + headerHeight, paintBorder)
                    canvas.drawText(safeHeaders[i], cx, headerBase, paintHeaderText)
                }
                y += headerHeight

                val startRow = pageIndex * rowsPerPage
                val endRow = (startRow + rowsPerPage).coerceAtMost(rows.size)
                val cellTextSize = (10f * fontScale).coerceIn(7.5f, 15.5f)
                val cellBoldSize = (10.5f * fontScale).coerceIn(8f, 16f)
                val footerReserve = if (pageIndex == totalPages - 1) 18f else 18f
                val rowLimitY = pageHeight - bottomMargin - footerReserve

                for (r in startRow until endRow) {
                    if (y + rowHeight > rowLimitY) break
                    val rowData = rows[r]
                    val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (r % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(leftMargin, y, rightMargin, y + rowHeight, rowBgPaint)
                    canvas.drawRect(leftMargin, y, rightMargin, y + rowHeight, paintBorder)
                    val rowBase = y + (rowHeight * 0.67f)

                    for (c in safeHeaders.indices) {
                        val cLeft = colLefts[c]
                        val cRight = colRights[c]
                        val cWidth = colWidths[c]
                        canvas.drawLine(cLeft, y, cLeft, y + rowHeight, paintBorder)
                        canvas.drawLine(cRight, y, cRight, y + rowHeight, paintBorder)

                        val cellText = if (c < rowData.size) rowData[c] else ""
                        val headerName = safeHeaders[c]
                        val isTextColumn = headerName.contains("المنتج") || headerName.contains("البيان") ||
                            headerName.contains("الزبون") || headerName.contains("المورد") ||
                            headerName.contains("compte", ignoreCase = true) || headerName.contains("Client") ||
                            headerName.contains("Fournisseur") || headerName.contains("Description")

                        canvas.save()
                        canvas.clipRect(cLeft + 1f, y, cRight - 1f, y + rowHeight)
                        if (isTextColumn) {
                            val pCell = Paint(paintText)
                            var size = cellTextSize
                            pCell.textSize = size
                            while (pCell.measureText(cellText) > (cWidth - 6f) && size > 5.5f) {
                                size -= 0.4f
                                pCell.textSize = size
                            }
                            if (isRtl) {
                                pCell.textAlign = Paint.Align.RIGHT
                                canvas.drawText(cellText, cRight - 4f, rowBase, pCell)
                            } else {
                                pCell.textAlign = Paint.Align.LEFT
                                canvas.drawText(cellText, cLeft + 4f, rowBase, pCell)
                            }
                        } else {
                            val isBoldCell = headerName.contains("بيع") || headerName.contains("الكمية") ||
                                headerName.contains("الفاتورة") || headerName.contains("الربح")
                            val p = Paint(if (isBoldCell) paintBold else paintText)
                            var size = if (isBoldCell) cellBoldSize else cellTextSize
                            p.textSize = size
                            while (p.measureText(cellText) > (cWidth - 6f) && size > 6.5f) {
                                size -= 0.5f
                                p.textSize = size
                            }
                            p.textAlign = Paint.Align.CENTER
                            canvas.drawText(cellText, (cLeft + cRight) / 2f, rowBase, p)
                        }
                        canvas.restore()
                    }
                    y += rowHeight
                }

                val currentPageRows = endRow - startRow
                val emptyFillCount = (rowsPerPage - currentPageRows).coerceAtLeast(0)
                val summaryReserve = if (isSalesJournal) 115f else (summaryLines.size * 22f + 90f)
                if (pageIndex == totalPages - 1 && emptyFillCount in 1..6) {
                    repeat(emptyFillCount) {
                        if (y + rowHeight + summaryReserve <= pageHeight - bottomMargin) {
                            canvas.drawRect(leftMargin, y, rightMargin, y + rowHeight, paintBorder)
                            for (c in safeHeaders.indices) {
                                canvas.drawLine(colLefts[c], y, colLefts[c], y + rowHeight, paintBorder)
                            }
                            y += rowHeight
                        }
                    }
                }

                if (pageIndex == totalPages - 1) {
                    y = drawTableSummaryAndSignatures(
                        canvas = canvas,
                        startY = y,
                        pageHeight = pageHeight,
                        leftMargin = leftMargin,
                        rightMargin = rightMargin,
                        tableWidth = tableWidth,
                        headers = safeHeaders,
                        colLefts = colLefts,
                        colRights = colRights,
                        colWidths = colWidths,
                        isRtl = isRtl,
                        isSalesJournal = isSalesJournal,
                        summaryLines = summaryLines,
                        paintBold = paintBold,
                        paintBorder = paintBorder,
                        paintThickBorder = paintThickBorder
                    )
                    val customFooterY = (y + 8f).coerceAtMost(pageHeight - bottomMargin - 16f)
                    DocumentDesignHelper.drawFooterBranding(
                        canvas = canvas,
                        centerX = pageWidth / 2f,
                        startY = customFooterY,
                        bottomLimit = pageHeight - bottomMargin - 12f,
                        settings = activeSettings,
                        footerStyled = footerStyled
                    )
                }

                val footerPaint = Paint(paintText).apply { color = Color.GRAY; textSize = (9f * fontScale).coerceIn(7.5f, 13f); textAlign = Paint.Align.CENTER }
                canvas.drawText("صفحة ${pageIndex + 1} من $totalPages • نظام نقطة البيع وإدارة المتجر", pageWidth / 2f, pageHeight - bottomMargin, footerPaint)
                pdfDoc.finishPage(page)
            }

            PdfPrintAndShareUtils.writePdfDocument(pdfDoc, file)
        } catch (t: Throwable) {
            Log.e("PdfTableGen", "Failed generating table PDF document", t)
            try {
                pdfDoc.close()
            } catch (closeErr: Throwable) {
                Log.w("PdfTableGen", "Error closing PdfDocument after failure", closeErr)
            }
            throw t
        } finally {
            runCatching { logoStyled?.bitmap?.recycle() }
            runCatching { headerStyled?.bitmap?.recycle() }
            runCatching { footerStyled?.bitmap?.recycle() }
        }
        return file
    }

    private fun drawTableSummaryAndSignatures(
        canvas: Canvas,
        startY: Float,
        pageHeight: Int,
        leftMargin: Float,
        rightMargin: Float,
        tableWidth: Float,
        headers: List<String>,
        colLefts: FloatArray,
        colRights: FloatArray,
        colWidths: List<Float>,
        isRtl: Boolean,
        isSalesJournal: Boolean,
        summaryLines: List<Pair<String, String>>,
        paintBold: Paint,
        paintBorder: Paint,
        paintThickBorder: Paint
    ): Float {
        var y = startY
        if (isSalesJournal && headers.size >= 5) {
            val yellowHeight = 30f
            val paintYellowBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 255, 0); style = Paint.Style.FILL }
            canvas.drawRect(leftMargin, y, rightMargin, y + yellowHeight, paintYellowBg)
            canvas.drawRect(leftMargin, y, rightMargin, y + yellowHeight, paintThickBorder)
            val paintYellowText = Paint(paintBold).apply { color = Color.BLACK; textSize = 11f; textAlign = Paint.Align.CENTER }

            val hasProfitCol = headers.any { it.contains("الربح") || it.contains("Benefice") }
            val mergeCols = if (hasProfitCol && headers.size == 8) 4 else (headers.size - 3).coerceAtLeast(1)

            if (isRtl) {
                val mergedRight = colRights[0]
                val mergedLeft = colLefts[mergeCols - 1]
                canvas.drawText("المجموع", (mergedLeft + mergedRight) / 2f, y + 19f, paintYellowText)
                canvas.drawLine(mergedLeft, y, mergedLeft, y + yellowHeight, paintThickBorder)
                for (c in mergeCols until headers.size) {
                    val cLeft = colLefts[c]; val cRight = colRights[c]; val cWidth = colWidths[c]
                    canvas.drawLine(cLeft, y, cLeft, y + yellowHeight, paintThickBorder)
                    val hName = headers[c]
                    val sumVal = when {
                        hName.contains("الكمية") || hName.contains("Qté") -> summaryLines.find { it.first.contains("الكمية") || it.first.contains("quantité") }?.second ?: ""
                        hName.contains("الشراء") || hName.contains("Achat") -> summaryLines.find { it.first.contains("الشراء") || it.first.contains("Achat") }?.second ?: ""
                        hName.contains("البيع") || hName.contains("Vente") -> summaryLines.find { it.first.contains("البيع") || it.first.contains("VENTES") }?.second ?: ""
                        hName.contains("الربح") || hName.contains("Benefice") -> summaryLines.find { it.first.contains("الربح") || it.first.contains("BENEFICE") }?.second ?: ""
                        else -> ""
                    }
                    val p = Paint(paintYellowText)
                    var s = 11f; p.textSize = s
                    while (p.measureText(sumVal) > cWidth - 6f && s > 7.5f) { s -= 0.5f; p.textSize = s }
                    canvas.drawText(sumVal, (cLeft + cRight) / 2f, y + 19f, p)
                }
            } else {
                val mergedLeft = colLefts[0]
                val mergedRight = colRights[mergeCols - 1]
                canvas.drawText("TOTAL", (mergedLeft + mergedRight) / 2f, y + 19f, paintYellowText)
                canvas.drawLine(mergedRight, y, mergedRight, y + yellowHeight, paintThickBorder)
                for (c in mergeCols until headers.size) {
                    val cLeft = colLefts[c]; val cRight = colRights[c]; val cWidth = colWidths[c]
                    canvas.drawLine(cRight, y, cRight, y + yellowHeight, paintThickBorder)
                    val hName = headers[c]
                    val sumVal = when {
                        hName.contains("Qté") || hName.contains("الكمية") -> summaryLines.find { it.first.contains("Qté") || it.first.contains("الكمية") }?.second ?: ""
                        hName.contains("Achat") || hName.contains("الشراء") -> summaryLines.find { it.first.contains("Achat") || it.first.contains("الشراء") }?.second ?: ""
                        hName.contains("Vente") || hName.contains("البيع") -> summaryLines.find { it.first.contains("Vente") || it.first.contains("البيع") }?.second ?: ""
                        hName.contains("Benefice") || hName.contains("الربح") -> summaryLines.find { it.first.contains("Benefice") || it.first.contains("الربح") }?.second ?: ""
                        else -> ""
                    }
                    val p = Paint(paintYellowText)
                    var s = 11f; p.textSize = s
                    while (p.measureText(sumVal) > cWidth - 6f && s > 7.5f) { s -= 0.5f; p.textSize = s }
                    canvas.drawText(sumVal, (cLeft + cRight) / 2f, y + 19f, p)
                }
            }
            y += yellowHeight + 12f
        } else if (summaryLines.isNotEmpty()) {
            y += 8f
            val summaryBoxW = (tableWidth * 0.6f).coerceAtMost(440f)
            val summaryBoxLeft = if (isRtl) leftMargin else rightMargin - summaryBoxW
            val summaryBoxRight = summaryBoxLeft + summaryBoxW
            val summaryBoxH = (summaryLines.size * 22f) + 14f
            val sumBoxBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(254, 240, 138); style = Paint.Style.FILL }
            canvas.drawRect(summaryBoxLeft, y, summaryBoxRight, y + summaryBoxH, sumBoxBg)
            canvas.drawRect(summaryBoxLeft, y, summaryBoxRight, y + summaryBoxH, paintThickBorder)
            var sy = y + 18f
            summaryLines.forEach { (lbl, v) ->
                if (isRtl) {
                    paintBold.textAlign = Paint.Align.RIGHT; canvas.drawText(lbl, summaryBoxRight - 12f, sy, paintBold)
                    paintBold.textAlign = Paint.Align.LEFT; canvas.drawText(v, summaryBoxLeft + 12f, sy, paintBold)
                } else {
                    paintBold.textAlign = Paint.Align.LEFT; canvas.drawText(lbl, summaryBoxLeft + 12f, sy, paintBold)
                    paintBold.textAlign = Paint.Align.RIGHT; canvas.drawText(v, summaryBoxRight - 12f, sy, paintBold)
                }
                sy += 22f
            }
            y += summaryBoxH + 12f
        }

        if (y + 55f < pageHeight - 28.35f - 14f) {
            val sigPaint = Paint(paintBold).apply { color = Color.rgb(30, 41, 59); textSize = 11f; textAlign = Paint.Align.CENTER }
            val sig1X = leftMargin + (tableWidth * 0.25f)
            val sig2X = leftMargin + (tableWidth * 0.75f)
            canvas.drawText("توقيع المحاسب / الإدارة", sig1X, y + 16f, sigPaint)
            canvas.drawLine(sig1X - 75f, y + 44f, sig1X + 75f, y + 44f, paintBorder)
            canvas.drawText("توقيع المستلم / العميل", sig2X, y + 16f, sigPaint)
            canvas.drawLine(sig2X - 75f, y + 44f, sig2X + 75f, y + 44f, paintBorder)
        }
        return y
    }

    fun exportTableDocument(
        context: Context,
        docTitle: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryLines: List<Pair<String, String>> = emptyList(),
        asPdf: Boolean = true,
        shopName: String = ""
    ) {
        val appCtx = context.applicationContext ?: context
        try {
            if (asPdf) {
                val file = generateTableDocumentPdf(context, docTitle, subtitle, headers, rows, summaryLines, shopName)
                PdfPrintAndShareUtils.sharePdfFile(context, file, docTitle)
            } else {
                val imgWidth = if (headers.size >= 5) 842 else 595
                val cappedRows = rows.take(120)
                val totalH = (180 + (cappedRows.size * 26) + (summaryLines.size * 26) + 120).coerceIn(600, 4200)
                val bitmap = Bitmap.createBitmap(imgWidth, totalH, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    val safeName = docTitle.replace("[^a-zA-Z0-9ء-ي]".toRegex(), "_").take(25).ifBlank { "document" }
                    val file = PdfPrintAndShareUtils.createCacheFile(context, "${safeName}_${System.currentTimeMillis()}.png")
                    val pTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
                    var y = 35f
                    canvas.drawText(shopName, imgWidth / 2f, y, pTitle)
                    y += 24f; pTitle.textSize = 15f
                    canvas.drawText(docTitle, imgWidth / 2f, y, pTitle)
                    y += 20f
                    if (subtitle.isNotBlank()) {
                        val pSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 11f; textAlign = Paint.Align.CENTER }
                        canvas.drawText(subtitle, imgWidth / 2f, y, pSub)
                    }

                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.flush()
                    }
                    PdfPrintAndShareUtils.shareImageFile(context, file, docTitle)
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (t: Throwable) {
            Log.e("PdfTableGen", "Failed exporting table document", t)
            Toast.makeText(appCtx, "فشل تصدير الوثيقة: ${t.localizedMessage ?: "خطأ"}", Toast.LENGTH_SHORT).show()
        }
    }
}
