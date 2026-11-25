package com.example.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.data.CustomerEntity
import com.example.data.ExpenseEntity
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import java.io.File

/**
 * Unified facade for generating, sharing, and printing A4 PDFs, vouchers, and customer badges.
 */
object PdfReportExporter {
    private const val TAG = "PdfReportExporter"

    private inline fun runSafely(context: Context, errorPrefix: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, errorPrefix, t)
            val appCtx = context.applicationContext ?: context
            try {
                Toast.makeText(
                    appCtx,
                    "$errorPrefix: ${t.localizedMessage ?: "خطأ غير متوقع"}",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (toastErr: Throwable) {
                Log.w(TAG, "Unable to display error Toast for $errorPrefix", toastErr)
            }
        }
    }

    fun printPdfFile(
        context: Context,
        file: File,
        jobTitle: String,
        isLandscape: Boolean = false
    ) = PdfPrintAndShareUtils.printPdfFile(context, file, jobTitle, isLandscape)

    fun generateTableDocumentPdf(
        context: Context,
        docTitle: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryLines: List<Pair<String, String>> = emptyList(),
        shopName: String = ""
    ): File = PdfTableDocumentGenerator.generateTableDocumentPdf(
        context, docTitle, subtitle, headers, rows, summaryLines, shopName
    )

    fun exportTableDocument(
        context: Context,
        docTitle: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryLines: List<Pair<String, String>> = emptyList(),
        asPdf: Boolean = true,
        shopName: String = ""
    ) = PdfTableDocumentGenerator.exportTableDocument(
        context, docTitle, subtitle, headers, rows, summaryLines, asPdf, shopName
    )

    fun printTableDocument(
        context: Context,
        docTitle: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>,
        summaryLines: List<Pair<String, String>> = emptyList(),
        shopName: String = ""
    ) {
        runSafely(context, "خطأ في طباعة الجدول") {
            val file = generateTableDocumentPdf(context, docTitle, subtitle, headers, rows, summaryLines, shopName)
            val isWideTable = headers.size >= 5 || docTitle.contains("مبيعات")
            printPdfFile(context, file, docTitle, isLandscape = isWideTable)
        }
    }

    fun generateExpenseVoucherPdf(
        context: Context,
        expense: ExpenseEntity,
        currency: String,
        shopName: String = "",
        shopPhone: String = "",
        taxNumber: String = ""
    ): File = PdfExpenseAndAnalyticsGenerator.generateExpenseVoucherPdf(
        context, expense, currency, shopName, shopPhone, taxNumber
    )

    fun exportExpenseVoucherPdf(
        context: Context,
        expense: ExpenseEntity,
        currency: String,
        shopName: String = "",
        shopPhone: String = "",
        taxNumber: String = ""
    ) {
        runSafely(context, "خطأ في تصدير سند المصروف") {
            val file = generateExpenseVoucherPdf(context, expense, currency, shopName, shopPhone, taxNumber)
            PdfPrintAndShareUtils.sharePdfFile(context, file, "فاتورة المصروف DEP-${expense.id}")
        }
    }

    fun printExpenseVoucher(
        context: Context,
        expense: ExpenseEntity,
        currency: String,
        shopName: String = "",
        shopPhone: String = "",
        taxNumber: String = ""
    ) {
        runSafely(context, "خطأ في طباعة سند المصروف") {
            val file = generateExpenseVoucherPdf(context, expense, currency, shopName, shopPhone, taxNumber)
            printPdfFile(context, file, "فاتورة المصروف DEP-${expense.id}", isLandscape = false)
        }
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
    ): File = PdfExpenseAndAnalyticsGenerator.generateAnalyticsReportPdf(
        context, dateText, totalSales, netProfit, expenses, totalPurchases,
        supplierPayments, totalDebts, invoiceCount, totalSoldUnits, avgTicket, currency, settings
    )

    fun exportAnalyticsReportPdf(
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
    ) {
        runSafely(context, "خطأ في تصدير التقرير المالي") {
            val file = generateAnalyticsReportPdf(
                context, dateText, totalSales, netProfit, expenses, totalPurchases,
                supplierPayments, totalDebts, invoiceCount, totalSoldUnits, avgTicket, currency, settings
            )
            PdfPrintAndShareUtils.sharePdfFile(context, file, "تقرير التحليلات المالية الشاملة")
        }
    }

    fun printAnalyticsReport(
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
    ) {
        runSafely(context, "خطأ في طباعة التقرير المالي") {
            val file = generateAnalyticsReportPdf(
                context, dateText, totalSales, netProfit, expenses, totalPurchases,
                supplierPayments, totalDebts, invoiceCount, totalSoldUnits, avgTicket, currency, settings
            )
            printPdfFile(context, file, "التقرير المالي - $dateText", isLandscape = false)
        }
    }

    fun generateSalesInvoicePdf(
        context: Context,
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity? = null
    ): File = PdfInvoiceAndBadgeGenerator.generateSalesInvoicePdf(context, sale, items, settings)

    fun exportSalesInvoicePdf(
        context: Context,
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity? = null
    ) {
        runSafely(context, "خطأ في تصدير الفاتورة") {
            val file = generateSalesInvoicePdf(context, sale, items, settings)
            PdfPrintAndShareUtils.sharePdfFile(context, file, "فاتورة_${sale.invoiceNumber}")
        }
    }

    fun printSalesInvoicePdf(
        context: Context,
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        settings: GlobalSettingsEntity? = null
    ) {
        runSafely(context, "خطأ في طباعة الفاتورة") {
            val file = generateSalesInvoicePdf(context, sale, items, settings)
            printPdfFile(context, file, "فاتورة_${sale.invoiceNumber}", isLandscape = false)
        }
    }

    fun generateCustomerBarcodeBadgePdf(
        context: Context,
        customer: CustomerEntity,
        shopName: String = "RM POS"
    ): File = PdfInvoiceAndBadgeGenerator.generateCustomerBarcodeBadgePdf(context, customer, shopName)

    fun printCustomerBarcodeBadge(
        context: Context,
        customer: CustomerEntity,
        shopName: String = "RM POS"
    ) {
        runSafely(context, "خطأ في طباعة بطاقة الزبون") {
            val file = generateCustomerBarcodeBadgePdf(context, customer, shopName)
            val numStr = if (customer.customerNumber > 0) customer.customerNumber.toString() else customer.id.toString()
            printPdfFile(context, file, "Customer_Card_$numStr", isLandscape = false)
        }
    }

    fun shareCustomerBarcodeBadge(
        context: Context,
        customer: CustomerEntity,
        shopName: String = "RM POS"
    ) {
        runSafely(context, "خطأ في مشاركة بطاقة الزبون") {
            val file = generateCustomerBarcodeBadgePdf(context, customer, shopName)
            val numStr = if (customer.customerNumber > 0) customer.customerNumber.toString() else customer.id.toString()
            PdfPrintAndShareUtils.sharePdfFile(context, file, "بطاقة_زبون_${customer.name}_$numStr")
        }
    }
}
