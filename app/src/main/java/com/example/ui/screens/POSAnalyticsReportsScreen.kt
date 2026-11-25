package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.FinancialCalculator
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun POSAnalyticsReportsScreen(
    viewModel: PosViewModel,
    lang: String = "AR",
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val scrollState = rememberScrollState()
        val todayStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date()) }
        val monthYearFormat = remember { SimpleDateFormat("MM/yyyy", Locale.US) }

        var selectedFilter by remember { mutableStateOf("اليوم") }
        var selectedChartTab by remember { mutableStateOf("المبيعات") }
        var showMenuDropdown by remember { mutableStateOf(false) }
        var showDateDialog by remember { mutableStateOf(false) }
        var selectedDateText by remember { mutableStateOf("اليوم ($todayStr)") }
        var customStartTimestamp by remember {
            mutableLongStateOf(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis)
        }
        var customEndTimestamp by remember {
            mutableLongStateOf(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis)
        }

        var showZReportDialog by remember { mutableStateOf(false) }
        var showAddExpenseDialog by remember { mutableStateOf(false) }
        var showExpensesListDialog by remember { mutableStateOf(false) }
        var showAllTopProductsDialog by remember { mutableStateOf(false) }
        var showSalesSummaryDialog by remember { mutableStateOf(false) }
        var showPhonePrinterDialog by remember { mutableStateOf(false) }
        var showReportPrintOptionsDialog by remember { mutableStateOf(false) }

        val allSales by viewModel.sales.collectAsStateWithLifecycle()
        val allExpenses by viewModel.expenses.collectAsStateWithLifecycle()
        val allPurchases by viewModel.purchases.collectAsStateWithLifecycle()
        val allCustomers by viewModel.customers.collectAsStateWithLifecycle()
        val topProductsList by viewModel.topProducts.collectAsStateWithLifecycle()
        val productSalesHistory by viewModel.productSalesHistory.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val currency by viewModel.currency.collectAsStateWithLifecycle()

        val (startTime, endTime) = remember(selectedFilter, customStartTimestamp, customEndTimestamp) {
            resolveAnalyticsWindow(selectedFilter, customStartTimestamp, customEndTimestamp)
        }

        val filteredSales = remember(allSales, startTime, endTime) {
            allSales.filter { it.timestamp in startTime..endTime && !it.isVoided }
        }
        val filteredExpenses = remember(allExpenses, startTime, endTime) {
            allExpenses.filter { it.timestamp in startTime..endTime }
        }
        val filteredPurchases = remember(allPurchases, startTime, endTime) {
            allPurchases.filter { it.timestamp in startTime..endTime }
        }
        val filteredSalesHistory = remember(productSalesHistory, startTime, endTime) {
            productSalesHistory.filter { it.timestamp in startTime..endTime }
        }

        val metrics = remember(filteredSales, filteredExpenses, filteredPurchases, allCustomers, filteredSalesHistory) {
            FinancialCalculator.computeAnalyticsPeriodMetrics(
                sales = filteredSales,
                expenses = filteredExpenses,
                purchases = filteredPurchases,
                customers = allCustomers,
                salesHistory = filteredSalesHistory
            )
        }

        val totalSoldUnits = remember(topProductsList) { topProductsList.sumOf { it.totalQty }.toInt() }

        val chartValues = remember(allSales, allExpenses, productSalesHistory, selectedChartTab, metrics.totalSales) {
            FinancialCalculator.computeMonthlyAnalyticsSeries(
                allSales = allSales,
                allExpenses = allExpenses,
                salesHistory = productSalesHistory,
                metricTab = selectedChartTab,
                fallbackCurrentTotal = metrics.totalSales
            )
        }

        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF081226), Color(0xFF060B18), Color(0xFF050813)))
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnalyticsTopHeader(
                    onMenuClick = { showMenuDropdown = true },
                    onPrinterClick = { showReportPrintOptionsDialog = true },
                    onThermalPrintClick = { showZReportDialog = true },
                    onPdfExportClick = {
                        PdfReportExporter.exportAnalyticsReportPdf(
                            context = context, dateText = selectedDateText, totalSales = metrics.totalSales,
                            netProfit = metrics.netProfit, expenses = metrics.expenses, totalPurchases = metrics.totalPurchases,
                            supplierPayments = metrics.supplierPayments, totalDebts = metrics.totalDebts,
                            invoiceCount = metrics.invoiceCount, totalSoldUnits = totalSoldUnits, avgTicket = metrics.avgTicket,
                            currency = currency, settings = settings
                        )
                    }
                )

                AnalyticsDateAndFilterSection(
                    selectedDate = selectedDateText,
                    selectedFilter = selectedFilter,
                    onDateClick = { showDateDialog = true },
                    onFilterSelect = { filter ->
                        selectedFilter = filter
                        selectedDateText = when (filter) {
                            "اليوم" -> "اليوم ($todayStr)"
                            "الأسبوع الماضي" -> "الأسبوع الماضي"
                            "هذا الشهر" -> "هذا الشهر (${monthYearFormat.format(Date())})"
                            "الشهر الماضي" -> {
                                val prevMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                                "الشهر الماضي (${monthYearFormat.format(prevMonthCal.time)})"
                            }
                            "هذه السنة" -> "هذه السنة (${Calendar.getInstance().get(Calendar.YEAR)})"
                            "السنة الماضية" -> "السنة الماضية (${Calendar.getInstance().get(Calendar.YEAR) - 1})"
                            "من .. إلى", "مخصص" -> { showDateDialog = true; selectedDateText }
                            else -> "كافة الفترات"
                        }
                    }
                )

                AnalyticsSixStatGrid(
                    sales = metrics.totalSales, netProfit = metrics.netProfit, expenses = metrics.expenses,
                    purchases = metrics.totalPurchases, supplierPayments = metrics.supplierPayments, debts = metrics.totalDebts,
                    onSalesClick = { showSalesSummaryDialog = true },
                    onProfitClick = { showSalesSummaryDialog = true },
                    onExpensesClick = { showExpensesListDialog = true },
                    onPurchasesClick = { runCatching { onNavigate("PURCHASE_INVOICES") }.onFailure { showSalesSummaryDialog = true } },
                    onSupplierPaymentsClick = { runCatching { onNavigate("SUPPLIERS") }.onFailure { showSalesSummaryDialog = true } },
                    onDebtsClick = { runCatching { onNavigate("CLIENTS") }.onFailure { showSalesSummaryDialog = true } }
                )

                AnalyticsSalesChartCard(
                    selectedTab = selectedChartTab, chartValues = chartValues, avgTicket = metrics.avgTicket,
                    invoicesCount = metrics.invoiceCount, soldUnits = totalSoldUnits, currency = currency,
                    onTabChange = { selectedChartTab = it }
                )

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth > 650.dp) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                PaymentMethodsDonutCard(
                                    metrics.totalSales, metrics.cashAmount, metrics.cardAmount, metrics.bankAmount, metrics.creditAmount,
                                    metrics.cashPercent, metrics.cardPercent, metrics.bankPercent, metrics.creditPercent,
                                    currency = currency
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                BestSellingProductsCard(topProducts = topProductsList, currency = currency, onViewAllClick = { showAllTopProductsDialog = true })
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            PaymentMethodsDonutCard(
                                metrics.totalSales, metrics.cashAmount, metrics.cardAmount, metrics.bankAmount, metrics.creditAmount,
                                metrics.cashPercent, metrics.cardPercent, metrics.bankPercent, metrics.creditPercent,
                                currency = currency
                            )
                            BestSellingProductsCard(topProducts = topProductsList, currency = currency, onViewAllClick = { showAllTopProductsDialog = true })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (showMenuDropdown) {
                DropdownMenu(
                    expanded = showMenuDropdown,
                    onDismissRequest = { showMenuDropdown = false },
                    modifier = Modifier.background(AnalyticsCardDarkNavy).border(1.dp, AnalyticsCardBorderLuminous, RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("ربط الطابعة بالهاتف (إعدادات واختبار)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        onClick = { showMenuDropdown = false; showPhonePrinterDialog = true },
                        leadingIcon = { Icon(Icons.Filled.SettingsBluetooth, contentDescription = null, tint = AnalyticsAccentCyan) }
                    )
                    DropdownMenuItem(
                        text = { Text("طباعة تقرير الإغلاق (Z-Report)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        onClick = { showMenuDropdown = false; showZReportDialog = true },
                        leadingIcon = { Icon(Icons.Filled.Print, contentDescription = null, tint = AnalyticsAccentCyan) }
                    )
                    DropdownMenuItem(
                        text = { Text("تسجيل مصروف جديد", color = Color.White, fontSize = 13.sp) },
                        onClick = { showMenuDropdown = false; showAddExpenseDialog = true },
                        leadingIcon = { Icon(Icons.Filled.AddCard, contentDescription = null, tint = AnalyticsAccentRed) }
                    )
                    DropdownMenuItem(
                        text = { Text("عرض قائمة المصاريف (${filteredExpenses.size})", color = Color.White, fontSize = 13.sp) },
                        onClick = { showMenuDropdown = false; showExpensesListDialog = true },
                        leadingIcon = { Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = AnalyticsAccentGold) }
                    )
                    DropdownMenuItem(
                        text = { Text("تصدير ومشاركة التقرير الشامل", color = Color.White, fontSize = 13.sp) },
                        onClick = {
                            showMenuDropdown = false
                            val shareText = buildString {
                                appendLine("=== تقرير المبيعات والإغلاق المالي ===")
                                appendLine("التاريخ: $selectedDateText")
                                appendLine("إجمالي المبيعات: ${MoneyUtils.format(metrics.totalSales, currency)}")
                                appendLine("عدد الفواتير: ${metrics.invoiceCount}")
                                appendLine("إجمالي المصاريف: ${MoneyUtils.format(metrics.expenses, currency)}")
                                appendLine("صافي الربح: ${MoneyUtils.format(metrics.netProfit, currency)}")
                                appendLine("نقداً (كاش): ${MoneyUtils.format(metrics.cashAmount, currency)}")
                                appendLine("بطاقة بنكية: ${MoneyUtils.format(metrics.cardAmount, currency)}")
                                appendLine("تحويل بنكي: ${MoneyUtils.format(metrics.bankAmount, currency)}")
                                appendLine("=======================================")
                            }
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "تقرير متجري - $selectedDateText")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }, "مشاركة تقرير المبيعات"))
                        },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null, tint = AnalyticsAccentBlue) }
                    )
                    DropdownMenuItem(
                        text = { Text("تحديث البيانات (Refresh)", color = Color.White, fontSize = 13.sp) },
                        onClick = { showMenuDropdown = false; viewModel.beep(); Toast.makeText(context, "تم تحديث بيانات التقارير بنجاح", Toast.LENGTH_SHORT).show() },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = AnalyticsAccentEmerald) }
                    )
                }
            }

            if (showDateDialog) {
                DateSelectorModalDialog(
                    selectedDateText = selectedDateText, initialStartTs = customStartTimestamp, initialEndTs = customEndTimestamp,
                    onSelectRange = { label, sTs, eTs, filterName ->
                        selectedDateText = label; customStartTimestamp = sTs; customEndTimestamp = eTs; selectedFilter = filterName; showDateDialog = false
                    },
                    onDismiss = { showDateDialog = false }
                )
            }

            if (showZReportDialog) {
                DailyReportThermalDialog(
                    viewModel = viewModel, sales = metrics.totalSales, grossProfit = metrics.grossProfit,
                    expenses = metrics.expenses, netProfit = metrics.netProfit, invoiceCount = metrics.invoiceCount,
                    cashSales = metrics.cashAmount, cardSales = metrics.cardAmount, todayExpenses = filteredExpenses,
                    lang = lang, onDismiss = { showZReportDialog = false }
                )
            }

            if (showAddExpenseDialog) {
                AddExpenseModalDialog(
                    currency = currency,
                    onAdd = { title, amount, category ->
                        viewModel.saveExpense(title = title, amount = amount, category = category)
                        Toast.makeText(context, "تم تسجيل المصروف بنجاح", Toast.LENGTH_SHORT).show()
                        showAddExpenseDialog = false
                    },
                    onDismiss = { showAddExpenseDialog = false }
                )
            }

            if (showExpensesListDialog) {
                ExpensesListModalDialog(
                    expenses = filteredExpenses,
                    currency = currency,
                    onAddNew = { showExpensesListDialog = false; showAddExpenseDialog = true },
                    onDelete = { exp -> viewModel.deleteExpense(exp); Toast.makeText(context, "تم حذف المصروف", Toast.LENGTH_SHORT).show() },
                    onDismiss = { showExpensesListDialog = false }
                )
            }

            if (showAllTopProductsDialog) {
                AllTopProductsModalDialog(topProducts = topProductsList, currency = currency, onDismiss = { showAllTopProductsDialog = false })
            }

            if (showSalesSummaryDialog) {
                SalesSummaryModalDialog(
                    sales = metrics.totalSales, invoices = metrics.invoiceCount, profit = metrics.grossProfit,
                    netProfit = metrics.netProfit, margin = metrics.profitMarginPercent, avgTicket = metrics.avgTicket,
                    currency = currency,
                    onDismiss = { showSalesSummaryDialog = false }
                )
            }

            if (showPhonePrinterDialog) {
                PhonePrinterSetupDialog(viewModel = viewModel, lang = lang, onDismiss = { showPhonePrinterDialog = false })
            }

            if (showReportPrintOptionsDialog) {
                ReportPrintOptionsModalDialog(
                    lang = lang,
                    onPrintA4 = {
                        showReportPrintOptionsDialog = false
                        PdfReportExporter.printAnalyticsReport(
                            context = context, dateText = selectedDateText, totalSales = metrics.totalSales,
                            netProfit = metrics.netProfit, expenses = metrics.expenses, totalPurchases = metrics.totalPurchases,
                            supplierPayments = metrics.supplierPayments, totalDebts = metrics.totalDebts,
                            invoiceCount = metrics.invoiceCount, totalSoldUnits = totalSoldUnits, avgTicket = metrics.avgTicket,
                            currency = currency, settings = settings
                        )
                    },
                    onExportPdf = {
                        showReportPrintOptionsDialog = false
                        PdfReportExporter.exportAnalyticsReportPdf(
                            context = context, dateText = selectedDateText, totalSales = metrics.totalSales,
                            netProfit = metrics.netProfit, expenses = metrics.expenses, totalPurchases = metrics.totalPurchases,
                            supplierPayments = metrics.supplierPayments, totalDebts = metrics.totalDebts,
                            invoiceCount = metrics.invoiceCount, totalSoldUnits = totalSoldUnits, avgTicket = metrics.avgTicket,
                            currency = currency, settings = settings
                        )
                    },
                    onOpenZReport = { showReportPrintOptionsDialog = false; showZReportDialog = true },
                    onOpenPrinterSettings = { showReportPrintOptionsDialog = false; showPhonePrinterDialog = true },
                    onDismiss = { showReportPrintOptionsDialog = false }
                )
            }
        }
    }
}

private fun resolveAnalyticsWindow(selectedFilter: String, customStart: Long, customEnd: Long): Pair<Long, Long> {
    return when (selectedFilter) {
        "اليوم" -> {
            val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
            start to end
        }
        "الأسبوع الماضي" -> {
            val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
            val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            start to end
        }
        "هذا الشهر" -> {
            val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
            val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            start to end
        }
        "الشهر الماضي" -> {
            val start = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val end = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
            start to end
        }
        "هذه السنة" -> {
            val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
            start to end
        }
        "السنة الماضية" -> {
            val start = Calendar.getInstance().apply { add(Calendar.YEAR, -1); set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, -1); set(Calendar.DAY_OF_YEAR, getActualMaximum(Calendar.DAY_OF_YEAR)); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
            start to end
        }
        "من .. إلى", "مخصص" -> minOf(customStart, customEnd) to maxOf(customStart, customEnd)
        else -> 0L to Long.MAX_VALUE
    }
}
