package com.example.ui.screens

import com.example.data.ExpenseEntity
import com.example.data.GlobalSettingsEntity
import com.example.data.PurchaseEntity
import com.example.data.PurchaseItemEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun padRight(s: String, n: Int): String {
    return if (s.length >= n) s.substring(0, n) else s + " ".repeat(n - s.length)
}

fun padLeft(s: String, n: Int): String {
    return if (s.length >= n) s.substring(0, n) else " ".repeat(n - s.length) + s
}

fun padCenter(s: String, n: Int): String {
    if (s.length >= n) return s.substring(0, n)
    val padding = n - s.length
    val left = padding / 2
    val right = padding - left
    return " ".repeat(left) + s + " ".repeat(right)
}

fun padBetween(left: String, right: String, n: Int): String {
    val totalLen = left.length + right.length
    if (totalLen >= n) return "$left $right"
    return left + " ".repeat(n - totalLen) + right
}

/**
 * Generates an ESC/POS compatible fixed-width text string for thermal printers (32 chars line width).
 */
fun generateDailyReportThermalString(
    dateStr: String,
    cashierName: String,
    sales: Double,
    grossProfit: Double,
    expenses: Double,
    netProfit: Double,
    invoiceCount: Int,
    cashSales: Double,
    cardSales: Double,
    todayExpenses: List<ExpenseEntity>,
    lang: String,
    shopName: String = "",
    currency: String = "MAD"
): String {
    val width = 32
    val sb = StringBuilder()

    if (shopName.isNotBlank()) {
        sb.appendLine(padCenter(shopName, width))
    }
    sb.appendLine(padCenter("RAPPORT DE CLOTURE (Z)", width))
    sb.appendLine(padCenter("تقرير الإغلاق المالي اليومي", width))
    sb.appendLine("=".repeat(width))
    sb.appendLine("DATE: $dateStr")
    sb.appendLine("UTILISATEUR: $cashierName")
    sb.appendLine("=".repeat(width))

    sb.appendLine(padBetween(if (lang == "AR") "عدد الفواتير:" else "Nb Factures:", "$invoiceCount", width))
    sb.appendLine(padBetween(if (lang == "AR") "إجمالي المبيعات:" else "Total Ventes:", MoneyUtils.format(sales, currency), width))
    sb.appendLine("-".repeat(width))
    sb.appendLine(padBetween(if (lang == "AR") "  - نقداً (كاش):" else "  - Espèces:", MoneyUtils.format(cashSales, currency), width))
    sb.appendLine(padBetween(if (lang == "AR") "  - بطاقة بنكية:" else "  - Carte:", MoneyUtils.format(cardSales, currency), width))
    sb.appendLine("-".repeat(width))
    sb.appendLine(padBetween(if (lang == "AR") "إجمالي الأرباح:" else "Bénéfice Brut:", MoneyUtils.format(grossProfit, currency), width))
    sb.appendLine(padBetween(if (lang == "AR") "إجمالي المصاريف:" else "Total Dépenses:", MoneyUtils.format(expenses, currency), width))
    sb.appendLine("=".repeat(width))
    sb.appendLine(padBetween(if (lang == "AR") "صافي الربح النهائي:" else "BÉNÉFICE NET:", MoneyUtils.format(netProfit, currency), width))
    sb.appendLine("=".repeat(width))

    if (todayExpenses.isNotEmpty()) {
        sb.appendLine(padCenter(if (lang == "AR") "--- تفاصيل المصاريف ---" else "--- DETAIL DEPENSES ---", width))
        todayExpenses.forEach { exp ->
            val title = if (exp.title.isNotBlank()) exp.title else exp.category
            val safeTitle = if (title.length > 18) title.substring(0, 18) else title
            sb.appendLine(padBetween(safeTitle, MoneyUtils.format(exp.amountDouble, currency), width))
        }
        sb.appendLine("-".repeat(width))
    }

    sb.appendLine(padCenter(if (lang == "AR") "تم الإغلاق بنجاح" else "Clôture validée", width))
    if (shopName.isNotBlank()) {
        sb.appendLine(padCenter(shopName, width))
    }
    sb.appendLine("\n\n\n")

    return sb.toString()
}

fun generateThermalPrinterString(
    sale: SaleEntity,
    items: List<SaleItemEntity>,
    settings: GlobalSettingsEntity = GlobalSettingsEntity()
): String {
    val width = 32
    val sb = StringBuilder()

    if (settings.shopName.isNotBlank()) {
        sb.appendLine(padCenter(settings.shopName, width))
    }
    if (settings.shopAddress.isNotBlank()) {
        sb.appendLine(padCenter(settings.shopAddress, width))
    }
    if (settings.shopPhone.isNotBlank()) {
        sb.appendLine(padCenter("Tel: ${settings.shopPhone}", width))
    }
    sb.appendLine("-".repeat(width))

    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
    sb.appendLine("INV: " + sale.invoiceNumber)
    sb.appendLine("DATE: " + sdf.format(Date(sale.timestamp)))
    sb.appendLine("CSH: " + sale.cashierName)
    sb.appendLine("-".repeat(width))

    sb.appendLine(padRight("ITEM", 16) + padLeft("QTY", 6) + padLeft("TOTAL", 10))
    sb.appendLine("-".repeat(width))

    for (item in items) {
        val qty = String.format(Locale.US, "%.0f", item.quantity)
        val tot = String.format(Locale.US, "%.2f", item.totalLinePriceDouble)
        sb.append(padRight(item.productName, 16))
        sb.append(padLeft(qty, 6))
        sb.append(padLeft(tot, 10))
        sb.appendLine()
    }

    sb.appendLine("-".repeat(width))
    sb.appendLine(padRight("TOTAL:", 16) + padLeft(String.format(Locale.US, "%.2f", sale.totalAmountDouble), 16))
    sb.appendLine(padRight("PAID:", 16) + padLeft(String.format(Locale.US, "%.2f", sale.paidAmountDouble), 16))
    sb.appendLine(padRight("CHANGE:", 16) + padLeft(String.format(Locale.US, "%.2f", sale.changeAmountDouble), 16))
    sb.appendLine("-".repeat(width))

    sb.appendLine(padCenter("THANK YOU FOR YOUR VISIT", width))
    sb.appendLine()
    sb.appendLine()

    return sb.toString()
}

fun generateThermalPrinterStringForPurchase(
    purchase: PurchaseEntity,
    items: List<PurchaseItemEntity>,
    shopName: String = ""
): String {
    val width = 32
    val sb = StringBuilder()

    if (shopName.isNotBlank()) {
        sb.appendLine(padCenter(shopName, width))
    }
    sb.appendLine(padCenter("BON DE RECEPTION / ACHAT", width))
    sb.appendLine("-".repeat(width))

    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
    sb.appendLine("BON NO: ${purchase.displayInvoiceNumber}")
    sb.appendLine("DATE: " + sdf.format(Date(purchase.timestamp)))
    sb.appendLine("FOURNISSEUR: " + purchase.supplierName)
    sb.appendLine("-".repeat(width))

    sb.appendLine(padRight("ARTICLE", 16) + padLeft("QTE", 6) + padLeft("TOTAL", 10))
    sb.appendLine("-".repeat(width))

    for (item in items) {
        val qty = String.format(Locale.US, "%.0f", item.quantity)
        val tot = String.format(Locale.US, "%.2f", item.quantity * item.unitPriceDouble)
        sb.append(padRight(item.productName, 16))
        sb.append(padLeft(qty, 6))
        sb.append(padLeft(tot, 10))
        sb.appendLine()
    }

    sb.appendLine("-".repeat(width))
    sb.appendLine(padRight("TOTAL ACHAT:", 16) + padLeft(String.format(Locale.US, "%.2f", purchase.totalAmountDouble), 16))
    sb.appendLine(padRight("PAYE:", 16) + padLeft(String.format(Locale.US, "%.2f", purchase.paidAmountDouble), 16))
    sb.appendLine("-".repeat(width))

    sb.appendLine(padCenter("RECEPTION CONFIRMEE", width))
    sb.appendLine()
    sb.appendLine()

    return sb.toString()
}

fun generateListThermalString(
    title: String,
    subtitle: String,
    headers: List<String>,
    rows: List<List<String>>,
    summaryLines: List<Pair<String, String>> = emptyList(),
    shopName: String = ""
): String {
    val width = 32
    val sb = StringBuilder()
    if (shopName.isNotBlank()) {
        sb.appendLine(padCenter(shopName, width))
    }
    sb.appendLine(padCenter(title, width))
    if (subtitle.isNotBlank()) sb.appendLine(padCenter(subtitle, width))
    sb.appendLine("DATE: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date()))
    sb.appendLine("=".repeat(width))

    if (headers.size == 2) {
        sb.appendLine(padBetween(headers[0], headers[1], width))
        sb.appendLine("-".repeat(width))
        rows.forEach { row ->
            val col0 = if (row.isNotEmpty()) (if (row[0].length > 18) row[0].substring(0, 18) else row[0]) else ""
            val col1 = if (row.size > 1) row[1] else ""
            sb.appendLine(padBetween(col0, col1, width))
        }
    } else if (headers.size == 4) {
        val h0 = if (headers[0].length > 7) headers[0].substring(0, 7) else headers[0]
        val h1 = if (headers[1].length > 8) headers[1].substring(0, 8) else headers[1]
        val h2 = if (headers[2].length > 8) headers[2].substring(0, 8) else headers[2]
        val h3 = if (headers[3].length > 7) headers[3].substring(0, 7) else headers[3]
        sb.appendLine(padRight(h0, 7) + padRight(h1, 9) + padRight(h2, 9) + padLeft(h3, 7))
        sb.appendLine("-".repeat(width))
        rows.forEach { row ->
            val c0 = if (row.isNotEmpty()) (if (row[0].length > 7) row[0].substring(0, 7) else row[0]) else ""
            val rawDate = if (row.size > 1) row[1] else ""
            val c1 = if (rawDate.length == 10 && (rawDate.contains("/") || rawDate.contains("-"))) {
                val parts = if (rawDate.contains("/")) rawDate.split("/") else rawDate.split("-")
                if (parts.size == 3 && parts[0].length == 4) {
                    "${parts[2]}/${parts[1]}/${parts[0].takeLast(2)}"
                } else if (parts.size == 3 && parts[2].length == 4) {
                    "${parts[0]}/${parts[1]}/${parts[2].takeLast(2)}"
                } else if (rawDate.length > 8) rawDate.take(8) else rawDate
            } else if (rawDate.length > 8) rawDate.take(8) else rawDate

            val c2 = if (row.size > 2) (if (row[2].length > 8) row[2].substring(0, 8) else row[2]) else ""
            val c3 = if (row.size > 3) row[3] else ""
            sb.appendLine(padRight(c0, 7) + padRight(c1, 9) + padRight(c2, 9) + padLeft(c3, 7))
        }
    } else if (headers.size >= 3) {
        val h0 = if (headers[0].length > 14) headers[0].substring(0, 14) else headers[0]
        val h1 = if (headers[1].length > 6) headers[1].substring(0, 6) else headers[1]
        val h2 = if (headers[2].length > 9) headers[2].substring(0, 9) else headers[2]
        sb.appendLine(padRight(h0, 15) + padLeft(h1, 7) + padLeft(h2, 10))
        sb.appendLine("-".repeat(width))
        rows.forEach { row ->
            val c0 = if (row.isNotEmpty()) (if (row[0].length > 14) row[0].substring(0, 14) else row[0]) else ""
            val c1 = if (row.size > 1) row[1] else ""
            val c2 = if (row.size > 2) row[2] else ""
            sb.appendLine(padRight(c0, 15) + padLeft(c1, 7) + padLeft(c2, 10))
        }
    }

    if (summaryLines.isNotEmpty()) {
        sb.appendLine("=".repeat(width))
        summaryLines.forEach { (label, value) ->
            sb.appendLine(padBetween(label, value, width))
        }
    }

    sb.appendLine("-".repeat(width))
    sb.appendLine(padCenter("FIN DU DOCUMENT", width))
    sb.appendLine()
    sb.appendLine()
    return sb.toString()
}

fun generateSingleExpenseThermalString(
    exp: ExpenseEntity,
    lang: String,
    shopName: String = "",
    currency: String = "MAD"
): String {
    val width = 32
    val sb = StringBuilder()
    if (shopName.isNotBlank()) {
        sb.appendLine(padCenter(shopName, width))
    }
    sb.appendLine(padCenter(if (lang == "AR") "سند صرف مصروفات" else "BON DE DEPENSE", width))
    sb.appendLine("-".repeat(width))
    sb.appendLine("REF NO: EXP-${exp.id}")
    sb.appendLine("DATE: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(exp.timestamp)))
    sb.appendLine("-".repeat(width))
    sb.appendLine(padBetween(if (lang == "AR") "البند / البيان:" else "Intitule:", if (exp.title.length > 16) exp.title.substring(0, 16) else exp.title, width))
    sb.appendLine(padBetween(if (lang == "AR") "الفئة:" else "Categorie:", if (exp.category.length > 16) exp.category.substring(0, 16) else exp.category, width))
    sb.appendLine("=".repeat(width))
    sb.appendLine(padBetween(if (lang == "AR") "المبلغ المصروف:" else "MONTANT TOTAL:", MoneyUtils.format(exp.amountDouble, currency), width))
    sb.appendLine("=".repeat(width))
    sb.appendLine()
    sb.appendLine(padCenter(if (lang == "AR") "توقيع المستلم / المحاسب" else "Signature caissier / Visa", width))
    sb.appendLine()
    sb.appendLine()
    return sb.toString()
}
