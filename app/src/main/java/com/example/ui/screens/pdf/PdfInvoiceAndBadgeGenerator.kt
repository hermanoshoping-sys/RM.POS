package com.example.ui.screens

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.data.CustomerEntity
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.util.BarcodeGenerator
import com.example.util.DocumentDesignHelper
import java.io.File
import java.util.Locale

internal object PdfInvoiceAndBadgeGenerator {
    fun generateSalesInvoicePdf(
        context: Context,
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity? = null
    ): File = generateSaleReceiptPdfFile(
        context = context,
        sale = sale,
        items = items,
        settings = settings
    )

    fun generateCustomerBarcodeBadgePdf(
        context: Context,
        customer: CustomerEntity,
        shopName: String = "RM POS"
    ): File {
        val file = File(context.cacheDir, "customer_badge_${customer.customerNumber}_${System.currentTimeMillis()}.pdf")
        val pdfDoc = PdfDocument()
        val pageWidth = 380
        val pageHeight = 280
        val page = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas

        val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        val borderPaint = Paint().apply { color = Color.parseColor("#CBD5E1"); strokeWidth = 2f; style = Paint.Style.STROKE }
        canvas.drawRoundRect(6f, 6f, pageWidth - 6f, pageHeight - 6f, 12f, 12f, borderPaint)

        val headerPaint = Paint().apply { color = Color.parseColor("#0F172A"); style = Paint.Style.FILL }
        canvas.drawRoundRect(RectF(6f, 6f, pageWidth - 6f, 54f), 12f, 12f, headerPaint)
        canvas.drawRect(6f, 35f, pageWidth - 6f, 54f, headerPaint)

        val shopTitlePaint = Paint().apply { color = Color.WHITE; textSize = 15f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        canvas.drawText(shopName.ifBlank { "RM POS" }, pageWidth / 2f, 28f, shopTitlePaint)

        val subTitlePaint = Paint().apply { color = Color.parseColor("#38BDF8"); textSize = 10f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        canvas.drawText("بطاقة الزبون • CARTE CLIENT FIDÉLITÉ", pageWidth / 2f, 44f, subTitlePaint)

        val namePaint = Paint().apply { color = Color.parseColor("#0F172A"); textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        canvas.drawText(customer.name, pageWidth / 2f, 82f, namePaint)

        val metaPaint = Paint().apply { color = Color.parseColor("#475569"); textSize = 11.5f; textAlign = Paint.Align.CENTER }
        val numStr = if (customer.customerNumber > 0) "#${customer.customerNumber}" else "#${customer.id}"
        val phoneStr = if (customer.phone.isNotBlank()) "  |  هاتف: ${customer.phone}" else ""
        canvas.drawText("رقم الزبون: $numStr$phoneStr", pageWidth / 2f, 102f, metaPaint)

        if (customer.address.isNotBlank()) {
            val addrPaint = Paint().apply { color = Color.parseColor("#64748B"); textSize = 9.5f; textAlign = Paint.Align.CENTER }
            canvas.drawText("العنوان: ${customer.address}", pageWidth / 2f, 118f, addrPaint)
        }

        val barcodeString = customer.barcode.ifBlank { "CUST-${customer.customerNumber}" }
        val barcodeBitmap = BarcodeGenerator.generateCode128Bitmap(content = barcodeString, width = 500, height = 140, showText = true)
        val barcodeTop = if (customer.address.isNotBlank()) 128f else 118f
        val left = (pageWidth - 320f) / 2f
        try {
            canvas.drawBitmap(barcodeBitmap, null, RectF(left, barcodeTop, left + 320f, barcodeTop + 100f), null)
        } finally {
            barcodeBitmap.recycle()
        }

        val dividerPaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 1f }
        canvas.drawLine(20f, pageHeight - 34f, pageWidth - 20f, pageHeight - 34f, dividerPaint)

        val footerPaint = Paint().apply { color = Color.parseColor("#94A3B8"); textSize = 8.5f; textAlign = Paint.Align.CENTER }
        canvas.drawText("مسح هذا الباركود في شاشة المبيعات يحدد الزبون تلقائياً لتسجيل المشتريات والديون", pageWidth / 2f, pageHeight - 18f, footerPaint)
        pdfDoc.finishPage(page)

        PdfPrintAndShareUtils.writePdfDocument(pdfDoc, file)
        return file
    }
}
