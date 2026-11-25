package com.example.ui.screens

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.ExpenseEntity
import com.example.data.GlobalSettingsEntity
import com.example.util.DocumentDesignHelper
import java.io.File
import java.util.Locale

internal object PdfExpenseAndAnalyticsGenerator {
    fun generateExpenseVoucherPdf(
        context: Context,
        expense: ExpenseEntity,
        currency: String,
        shopName: String = "",
        shopPhone: String = "",
        taxNumber: String = ""
    ): File {
        val file = PdfPrintAndShareUtils.createCacheFile(
            context,
            "Facture_Depense_${expense.id}_${System.currentTimeMillis()}.pdf"
        )
        val activeSettings = DocumentDesignHelper.resolveSettings(context)
        val fontScale = DocumentDesignHelper.fontScale(activeSettings)
        val pageWidth = 595
        val pageHeight = 842
        val leftMargin = 28.35f // 1 cm
        val rightMargin = pageWidth - 28.35f // 1 cm
        val topMargin = 14.17f // 0.5 cm
        val bottomMargin = 28.35f // 1 cm
        val contentWidth = rightMargin - leftMargin

        val logoStyled = DocumentDesignHelper.loadLogoForDocument(context, activeSettings, contentWidth)
        val headerStyled = DocumentDesignHelper.loadHeaderImageForDocument(context, activeSettings, contentWidth)
        val footerStyled = DocumentDesignHelper.loadFooterImageForDocument(context, activeSettings, contentWidth)

        val pdfDoc = PdfDocument()
        try {
            val page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
            val canvas = page.canvas

            val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = (13f * fontScale).coerceIn(9f, 20f) }
            val paintBold = Paint(paintText).apply { isFakeBoldText = true; textSize = (13.5f * fontScale).coerceIn(9.5f, 21f) }
            val paintTitle = Paint(paintBold).apply { textSize = (22f * fontScale).coerceIn(15f, 30f); textAlign = Paint.Align.CENTER }
            val paintRedTitle = Paint(paintBold).apply { color = Color.rgb(220, 38, 38); textSize = (19f * fontScale).coerceIn(14f, 26f); textAlign = Paint.Align.CENTER }
            val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.3f }
            val paintThickBorder = Paint(paintBorder).apply { strokeWidth = 2.2f }

            var y = DocumentDesignHelper.drawHeaderBranding(
                canvas = canvas,
                centerX = pageWidth / 2f,
                startY = topMargin,
                settings = activeSettings,
                headerStyled = headerStyled,
                logoStyled = logoStyled
            )
            y += -paintRedTitle.ascent()
            canvas.drawText("فاتورة المصروف", pageWidth / 2f, y, paintRedTitle)
            y += (28f * fontScale).coerceIn(22f, 36f)
            val displayShop = shopName.ifBlank { activeSettings.shopName.ifBlank { "الشركة" } }
            if (activeSettings.showShopName) {
                canvas.drawText(displayShop, pageWidth / 2f, y, paintTitle)
                y += (22f * fontScale).coerceIn(18f, 30f)
            }

            val effPhone = shopPhone.ifBlank { activeSettings.shopPhone }
            val effTax = taxNumber.ifBlank { activeSettings.taxNumber }
            val storeMeta = buildList {
                if (activeSettings.showPhone && effPhone.isNotBlank()) add("الهاتف: $effPhone")
                if (activeSettings.showTaxNumber && effTax.isNotBlank()) add("الرقم الضريبي: $effTax")
                add("سند محاسبي معتمد")
            }
            val paintSub = Paint(paintText).apply { color = Color.DKGRAY; textSize = (11.5f * fontScale).coerceIn(8.5f, 17f); textAlign = Paint.Align.CENTER }
            canvas.drawText(storeMeta.joinToString(" • "), pageWidth / 2f, y, paintSub)
            y += 18f
            canvas.drawLine(leftMargin, y, rightMargin, y, paintBorder)
            y += 16f

            val metaFields = listOf(
                "رقم السند :" to "DEP-${expense.id}",
                "نوع وبيان المصروف :" to expense.title,
                "الفئة والتصنيف :" to expense.category,
                "تاريخ وتسجيل السند :" to PdfPrintAndShareUtils.formatTimestamp(expense.timestamp)
            )
            val rowH = (34f * fontScale).coerceIn(26f, 44f)
            val splitX = rightMargin - (160f * fontScale).coerceIn(145f, 220f)
            for ((label, value) in metaFields) {
                val baseY = y + (rowH * 0.65f)
                canvas.drawRect(leftMargin, y, rightMargin, y + rowH, paintBorder)
                canvas.drawLine(splitX, y, splitX, y + rowH, paintBorder)
                paintBold.textAlign = Paint.Align.RIGHT
                canvas.drawText(label, rightMargin - 12f, baseY, paintBold)
                paintText.textAlign = Paint.Align.RIGHT
                canvas.drawText(value, splitX - 12f, baseY, paintText)
                y += rowH
            }
            y += 22f

            val bannerH = (48f * fontScale).coerceIn(40f, 64f)
            val bannerSplitX = leftMargin + (contentWidth / 2f)
            val redBannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(239, 68, 68); style = Paint.Style.FILL }
            canvas.drawRect(leftMargin, y, rightMargin, y + bannerH, redBannerPaint)
            canvas.drawRect(leftMargin, y, rightMargin, y + bannerH, paintThickBorder)
            canvas.drawLine(bannerSplitX, y, bannerSplitX, y + bannerH, paintThickBorder)

            val paintBannerText = Paint(paintBold).apply { color = Color.BLACK; textSize = (16f * fontScale).coerceIn(12f, 22f); textAlign = Paint.Align.CENTER }
            val paintBannerAmount = Paint(paintBold).apply { color = Color.BLACK; textSize = (20f * fontScale).coerceIn(14f, 26f); textAlign = Paint.Align.CENTER }
            val bannerBase = y + (bannerH * 0.65f)
            canvas.drawText("ثمن المصروف", (bannerSplitX + rightMargin) / 2f, bannerBase, paintBannerText)
            canvas.drawText(String.format(Locale.US, "%.2f %s", expense.amountDouble, currency), (leftMargin + bannerSplitX) / 2f, bannerBase, paintBannerAmount)
            y += bannerH + 22f

            paintText.textAlign = Paint.Align.RIGHT
            canvas.drawText("طريقة الصرف: نقداً من صندوق المحل (الصندوق اليومي)", rightMargin - 6f, y, paintBold)
            y += (22f * fontScale).coerceIn(18f, 30f)
            canvas.drawText("الحالة: سند محاسبي معتمد ومصروف بالكامل", rightMargin - 6f, y, paintText)
            y += (22f * fontScale).coerceIn(18f, 30f)

            val sigBoxW = (contentWidth - 24f) / 2f
            val sigBoxH = 74f
            val sig2Left = leftMargin + sigBoxW + 24f
            canvas.drawRect(leftMargin, y, leftMargin + sigBoxW, y + sigBoxH, paintBorder)
            paintBold.textAlign = Paint.Align.CENTER
            canvas.drawText("توقيع المستلم", leftMargin + (sigBoxW / 2f), y + 22f, paintBold)
            canvas.drawLine(leftMargin + 20f, y + sigBoxH - 16f, leftMargin + sigBoxW - 20f, y + sigBoxH - 16f, paintBorder)

            canvas.drawRect(sig2Left, y, sig2Left + sigBoxW, y + sigBoxH, paintBorder)
            canvas.drawText("توقيع المحاسب / الإدارة", sig2Left + (sigBoxW / 2f), y + 22f, paintBold)
            canvas.drawLine(sig2Left + 20f, y + sigBoxH - 16f, sig2Left + sigBoxW - 20f, y + sigBoxH - 16f, paintBorder)
            y += sigBoxH + 18f

            DocumentDesignHelper.drawFooterBranding(
                canvas = canvas,
                centerX = pageWidth / 2f,
                startY = y,
                bottomLimit = pageHeight - bottomMargin,
                settings = activeSettings,
                footerStyled = footerStyled,
                fallbackText = "شكراً لتعاملكم • وثيقة محاسبية معتمدة وصادرة عبر نظام نقطة البيع"
            )
            pdfDoc.finishPage(page)

            PdfPrintAndShareUtils.writePdfDocument(pdfDoc, file)
        } finally {
            runCatching { logoStyled?.bitmap?.recycle() }
            runCatching { headerStyled?.bitmap?.recycle() }
            runCatching { footerStyled?.bitmap?.recycle() }
        }
        return file
    }

    fun generateAnalyticsReportPdf(
        context: Context,
        dateText: String,
        totalSales: Double,
        netProfit: Double,
        expenses: Double,
        totalPurchases: Double,
        supplierPayments: Double,
        totalDebts: Double,
        invoiceCount: Int,
        totalSoldUnits: Int,
        avgTicket: Double,
        currency: String = "MAD",
        settings: GlobalSettingsEntity? = null
    ): File {
        val file = PdfPrintAndShareUtils.createCacheFile(context, "Rapport_Analytique_${System.currentTimeMillis()}.pdf")
        val activeSettings = DocumentDesignHelper.resolveSettings(context, settings)
        val fontScale = DocumentDesignHelper.fontScale(activeSettings)
        val pageWidth = 595
        val pageHeight = 842
        val leftMargin = 28.35f // 1 cm
        val rightMargin = pageWidth - 28.35f // 1 cm
        val topMargin = 14.17f // 0.5 cm
        val bottomMargin = 28.35f // 1 cm
        val contentWidth = rightMargin - leftMargin

        val logoStyled = DocumentDesignHelper.loadLogoForDocument(context, activeSettings, contentWidth)
        val headerStyled = DocumentDesignHelper.loadHeaderImageForDocument(context, activeSettings, contentWidth)
        val footerStyled = DocumentDesignHelper.loadFooterImageForDocument(context, activeSettings, contentWidth)

        val pdfDoc = PdfDocument()
        try {
            val page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
            val canvas = page.canvas

            val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = (12f * fontScale).coerceIn(8.5f, 18f) }
            val paintBold = Paint(paintText).apply { isFakeBoldText = true; textSize = (13f * fontScale).coerceIn(9f, 19f) }
            val paintTitle = Paint(paintBold).apply { textSize = (21f * fontScale).coerceIn(14f, 28f); textAlign = Paint.Align.CENTER }
            val paintSub = Paint(paintText).apply { color = Color.rgb(71, 85, 105); textSize = (11f * fontScale).coerceIn(8f, 16f); textAlign = Paint.Align.CENTER }
            val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1.2f }
            val paintThickBorder = Paint(paintBorder).apply { strokeWidth = 2f }

            var y = DocumentDesignHelper.drawHeaderBranding(
                canvas = canvas,
                centerX = pageWidth / 2f,
                startY = topMargin,
                settings = activeSettings,
                headerStyled = headerStyled,
                logoStyled = logoStyled
            )
            if (activeSettings.showShopName) {
                y += -paintTitle.ascent()
                canvas.drawText(activeSettings.shopName.ifBlank { "الشركة" }, pageWidth / 2f, y, paintTitle)
                y += (22f * fontScale).coerceIn(18f, 28f)
            } else {
                y += 14f
            }

            val paintRepTitle = Paint(paintBold).apply { color = Color.rgb(15, 23, 42); textSize = (16f * fontScale).coerceIn(12f, 22f); textAlign = Paint.Align.CENTER }
            canvas.drawText("التقارير والتحليلات المالية الشاملة", pageWidth / 2f, y, paintRepTitle)
            y += (18f * fontScale).coerceIn(15f, 24f)
            canvas.drawText("فترة التقرير: $dateText • تاريخ الاستخراج: ${PdfPrintAndShareUtils.formatTimestamp()}", pageWidth / 2f, y, paintSub)
            y += 18f
            canvas.drawLine(leftMargin, y, rightMargin, y, paintBorder)
            y += 14f

            val cardGap = 8f
            val cardW = (contentWidth - (cardGap * 2)) / 3f
            val cardH = (56f * fontScale).coerceIn(48f, 70f)
            data class StatBoxItem(val title: String, val value: String, val bgColor: Int)

            val row1 = listOf(
                StatBoxItem("المصاريف", String.format(Locale.US, "%.2f", expenses), Color.rgb(255, 179, 0)),
                StatBoxItem("الأرباح المحققة", String.format(Locale.US, "%.2f", netProfit), Color.rgb(140, 198, 63)),
                StatBoxItem("مجموع المبيعات", String.format(Locale.US, "%.2f", totalSales), Color.rgb(255, 213, 79))
            )
            val row2 = listOf(
                StatBoxItem("مجموع الديون", String.format(Locale.US, "%.2f", totalDebts), Color.rgb(255, 0, 0)),
                StatBoxItem("مدفوعات الموردين", String.format(Locale.US, "%.2f", supplierPayments), Color.rgb(245, 166, 123)),
                StatBoxItem("مجموع المشتريات", String.format(Locale.US, "%.2f", totalPurchases), Color.rgb(0, 176, 255))
            )

            fun drawStatRow(items: List<StatBoxItem>, startY: Float) {
                val splitH = cardH * 0.43f
                for (i in items.indices) {
                    val boxLeft = leftMargin + (i * (cardW + cardGap))
                    val item = items[i]
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = item.bgColor; style = Paint.Style.FILL }
                    canvas.drawRect(boxLeft, startY, boxLeft + cardW, startY + cardH, bgPaint)
                    canvas.drawRect(boxLeft, startY, boxLeft + cardW, startY + cardH, paintThickBorder)
                    canvas.drawLine(boxLeft, startY + splitH, boxLeft + cardW, startY + splitH, paintBorder)

                    val paintBoxTitle = Paint(paintBold).apply { color = Color.BLACK; textSize = (11.5f * fontScale).coerceIn(8.5f, 16f); textAlign = Paint.Align.CENTER }
                    val paintBoxValue = Paint(paintBold).apply { color = Color.BLACK; textSize = (13.5f * fontScale).coerceIn(10f, 19f); textAlign = Paint.Align.CENTER }
                    canvas.drawText(item.title, boxLeft + (cardW / 2f), startY + (splitH * 0.72f), paintBoxTitle)
                    canvas.drawText("${item.value} $currency", boxLeft + (cardW / 2f), startY + splitH + ((cardH - splitH) * 0.68f), paintBoxValue)
                }
            }

            drawStatRow(row1, y)
            y += cardH + cardGap
            drawStatRow(row2, y)
            y += cardH + 22f

            paintBold.textAlign = Paint.Align.RIGHT
            canvas.drawText("مؤشرات الأداء والحركة التجارية العامة", rightMargin, y, paintBold)
            y += 12f

            val kpis = listOf(
                "متوسط قيمة الفاتورة (السلة المتوسطة)" to String.format(Locale.US, "%.2f %s", avgTicket, currency),
                "عدد فواتير البيع المنفذة" to "$invoiceCount فاتورة",
                "إجمالي عدد الوحدات المباعة" to "$totalSoldUnits قطعة",
                "هامش الربح الإجمالي المحقق" to "${String.format(Locale.US, "%.1f", if (totalSales > 0) (netProfit / totalSales) * 100 else 0.0)} %"
            )
            val kpiRowH = (28f * fontScale).coerceIn(22f, 38f)
            for ((kpiLabel, kpiVal) in kpis) {
                val baseY = y + (kpiRowH * 0.68f)
                canvas.drawRect(leftMargin, y, rightMargin, y + kpiRowH, paintBorder)
                canvas.drawLine(rightMargin - 200f, y, rightMargin - 200f, y + kpiRowH, paintBorder)
                paintBold.textAlign = Paint.Align.RIGHT
                canvas.drawText(kpiLabel, rightMargin - 12f, baseY, paintBold)
                paintText.textAlign = Paint.Align.RIGHT
                canvas.drawText(kpiVal, rightMargin - 212f, baseY, paintText)
                y += kpiRowH
            }
            y += 28f

            val sig1X = leftMargin + (contentWidth * 0.25f)
            val sig2X = leftMargin + (contentWidth * 0.75f)
            paintBold.textAlign = Paint.Align.CENTER
            canvas.drawText("توقيع المحاسب المعتمد", sig1X, y, paintBold)
            canvas.drawLine(sig1X - 75f, y + 32f, sig1X + 75f, y + 32f, paintBorder)
            canvas.drawText("توقيع الإدارة / المدير المسؤول", sig2X, y, paintBold)
            canvas.drawLine(sig2X - 75f, y + 32f, sig2X + 75f, y + 32f, paintBorder)
            y += 50f

            DocumentDesignHelper.drawFooterBranding(
                canvas = canvas,
                centerX = pageWidth / 2f,
                startY = y,
                bottomLimit = pageHeight - bottomMargin,
                settings = activeSettings,
                footerStyled = footerStyled,
                fallbackText = "تم استخراج التقرير آلياً عبر نظام إدارة المبيعات ونقاط البيع • وثيقة رسمية معتمدة"
            )
            pdfDoc.finishPage(page)

            PdfPrintAndShareUtils.writePdfDocument(pdfDoc, file)
        } finally {
            runCatching { logoStyled?.bitmap?.recycle() }
            runCatching { headerStyled?.bitmap?.recycle() }
            runCatching { footerStyled?.bitmap?.recycle() }
        }
        return file
    }
}
