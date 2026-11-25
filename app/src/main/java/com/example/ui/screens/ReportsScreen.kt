package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.FinancialCalculator
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSPrimary
import com.example.ui.theme.POSSuccess
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(viewModel: PosViewModel, lang: String) {
    val allExpenses by viewModel.expenses.collectAsStateWithLifecycle()
    val topProductsList by viewModel.topProducts.collectAsStateWithLifecycle()
    val salesHistory by viewModel.sales.collectAsStateWithLifecycle()
    val productSalesHistory by viewModel.productSalesHistory.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    var selectedPeriodTab by remember { mutableStateOf(0) }
    var showDailyReportDialog by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }

    val (periodStart, periodEnd) = remember(selectedPeriodTab) {
        val cal = Calendar.getInstance()
        when (selectedPeriodTab) {
            0 -> {
                val s = cal.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val e = cal.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                Pair(s, e)
            }
            1 -> {
                val e = cal.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                val s = cal.apply {
                    add(Calendar.DAY_OF_YEAR, -6)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(s, e)
            }
            2 -> {
                val e = cal.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                val s = cal.apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Pair(s, e)
            }
            else -> Pair(0L, Long.MAX_VALUE)
        }
    }

    val periodSales = remember(salesHistory, periodStart, periodEnd) {
        salesHistory.filter { it.timestamp in periodStart..periodEnd && !it.isVoided }
    }
    val periodProductSales = remember(productSalesHistory, periodStart, periodEnd) {
        productSalesHistory.filter { it.timestamp in periodStart..periodEnd }
    }
    val periodExpenses = remember(allExpenses, periodStart, periodEnd) {
        allExpenses.filter { it.timestamp in periodStart..periodEnd }
    }

    val metrics = remember(periodSales, periodExpenses, periodProductSales) {
        FinancialCalculator.computeAnalyticsPeriodMetrics(
            sales = periodSales,
            expenses = periodExpenses,
            purchases = emptyList(),
            customers = emptyList(),
            salesHistory = periodProductSales
        )
    }

    val salesToday = metrics.totalSales
    val profitToday = metrics.grossProfit
    val expensesToday = metrics.expenses
    val invoicesToday = metrics.invoiceCount
    val cashToday = metrics.cashAmount
    val cardToday = metrics.cardAmount
    val netProfitToday = metrics.netProfit
    val marginPercent = metrics.profitMarginPercent
    val avgTicket = metrics.avgTicket

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        POSMasterHeader(titleKey = "reports_btn", lang = lang, viewModel = viewModel)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedPeriodTab == 0,
                        onClick = { selectedPeriodTab = 0 },
                        label = { Text(if (lang == "AR") "اليوم" else "Aujourd'hui") },
                        leadingIcon = { Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("reports_period_today")
                    )
                    FilterChip(
                        selected = selectedPeriodTab == 1,
                        onClick = { selectedPeriodTab = 1 },
                        label = { Text(if (lang == "AR") "آخر 7 أيام" else "7 derniers jours") },
                        leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("reports_period_7d")
                    )
                    FilterChip(
                        selected = selectedPeriodTab == 2,
                        onClick = { selectedPeriodTab = 2 },
                        label = { Text(if (lang == "AR") "هذا الشهر" else "Ce mois") },
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("reports_period_month")
                    )
                }

                Button(
                    onClick = { showDailyReportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp).testTag("reports_print_z_button")
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (lang == "AR") "طباعة تقرير الإغلاق (Z-Report)" else "Imprimer Rapport Z",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            ReportsKpiSummaryRow(
                lang = lang,
                currency = currency,
                selectedPeriodTab = selectedPeriodTab,
                salesToday = salesToday,
                profitToday = profitToday,
                expensesToday = expensesToday,
                netProfitToday = netProfitToday,
                invoicesToday = invoicesToday,
                avgTicket = avgTicket,
                marginPercent = marginPercent,
                expensesCount = periodExpenses.size
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (lang == "AR") "توزيع المبيعات وطرق الدفع" else "Répartition des paiements", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = POSPrimary, modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Payments, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (lang == "AR") "نقداً (كاش)" else "Espèces (Cash)", fontSize = 13.sp)
                                }
                                Text(MoneyUtils.format(cashToday, currency), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF10B981))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val cashFraction = if (salesToday > 0) (cashToday / salesToday).toFloat().coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { cashFraction },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF10B981),
                                trackColor = Color(0xFF334155)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CreditCard, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (lang == "AR") "بطاقة بنكية" else "Carte bancaire", fontSize = 13.sp)
                                }
                                Text(MoneyUtils.format(cardToday, currency), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF3B82F6))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val cardFraction = if (salesToday > 0) (cardToday / salesToday).toFloat().coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { cardFraction },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF3B82F6),
                                trackColor = Color(0xFF334155)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(if (lang == "AR") "إجمالي المقبوض" else "Total Encaissé", color = Color.Gray, fontSize = 11.sp)
                                Text(MoneyUtils.format(salesToday, currency), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (lang == "AR") "السيولة الصافية" else "Liquidité Nette", color = Color.Gray, fontSize = 11.sp)
                                Text(MoneyUtils.format((cashToday - expensesToday).coerceAtLeast(0.0), currency), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = POSSuccess)
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (lang == "AR") "تفاصيل مصاريف اليوم (${periodExpenses.size})" else "Dépenses du jour (${periodExpenses.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Text(MoneyUtils.format(expensesToday, currency), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (periodExpenses.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF0F172A).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.CheckCircleOutline, contentDescription = null, tint = POSSuccess, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(if (lang == "AR") "لا توجد مصاريف مسجلة اليوم" else "Aucune dépense enregistrée aujourd'hui", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 200.dp)
                            ) {
                                itemsIndexed(periodExpenses, key = { _, e -> e.id }, contentType = { _, _ -> "expense_row" }) { _, exp ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(8.dp)).padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(color = Color(0xFFEF4444).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                    Text(exp.category, color = Color(0xFFFCA5A5), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(timeFormatter.format(Date(exp.timestamp)), color = Color.Gray, fontSize = 11.sp)
                                            }
                                            if (exp.title.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(exp.title, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                                            }
                                        }
                                        Text("-${MoneyUtils.format(exp.amount, currency)}", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().height(360.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (lang == "AR") "تحليل مبيعات آخر 7 أيام" else "Analyse des ventes (7j)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Icon(Icons.Filled.BarChart, contentDescription = null, tint = POSPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        ReportsChartsPane(salesHistory = salesHistory, lang = lang, modifier = Modifier.fillMaxSize())
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(Bilingual.get("best_sellers", lang), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (topProductsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (lang == "AR") "لا توجد مبيعات بعد" else "Aucune vente", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(topProductsList, key = { index, rec -> "${index}_${rec.productName}" }, contentType = { _, _ -> "top_product_row" }) { index, rec ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(8.dp)).padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Surface(
                                                color = when (index) {
                                                    0 -> Color(0xFFF59E0B)
                                                    1 -> Color(0xFF94A3B8)
                                                    2 -> Color(0xFFB45309)
                                                    else -> Color(0xFF334155)
                                                },
                                                shape = CircleShape,
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("${index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(rec.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                                Text("${rec.totalQty} Units", color = Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                        Text(MoneyUtils.format(rec.totalRevenue, currency), color = POSSuccess, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDailyReportDialog) {
        DailyReportThermalDialog(
            viewModel = viewModel,
            sales = salesToday,
            grossProfit = profitToday,
            expenses = expensesToday,
            netProfit = netProfitToday,
            invoiceCount = invoicesToday,
            cashSales = cashToday,
            cardSales = cardToday,
            todayExpenses = periodExpenses,
            lang = lang,
            onDismiss = { showDailyReportDialog = false }
        )
    }
}
