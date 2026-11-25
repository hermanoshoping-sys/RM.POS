package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.printer.BluetoothPrinterService
import com.example.data.ExpenseEntity
import com.example.data.SaleEntity
import com.example.ui.components.rememberBluetoothAwarePrintLauncher
import com.example.ui.viewmodels.PosViewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DailyReportThermalDialog(
    viewModel: PosViewModel,
    sales: Double,
    grossProfit: Double,
    expenses: Double,
    netProfit: Double,
    invoiceCount: Int,
    cashSales: Double,
    cardSales: Double,
    todayExpenses: List<ExpenseEntity>,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val storeSettings by viewModel.settings.collectAsStateWithLifecycle()
    val cashierName = currentUser?.username.orEmpty()
    val dateStr = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date()) }

    val currency = storeSettings.currency.ifBlank { "MAD" }
    val reportText = remember(storeSettings.shopName, currency, dateStr, cashierName, sales, grossProfit, expenses, netProfit, invoiceCount, cashSales, cardSales, todayExpenses, lang) {
        generateDailyReportThermalString(
            dateStr = dateStr,
            cashierName = cashierName,
            sales = sales,
            grossProfit = grossProfit,
            expenses = expenses,
            netProfit = netProfit,
            invoiceCount = invoiceCount,
            cashSales = cashSales,
            cardSales = cardSales,
            todayExpenses = todayExpenses,
            lang = lang,
            shopName = storeSettings.shopName,
            currency = currency
        )
    }
    val launchZReportPrint = rememberBluetoothAwarePrintLauncher(lang = lang) {
        val pType = viewModel.printerConnectionType.value
        val pTarget = viewModel.printerMac.value.ifBlank { viewModel.printerName.value }
        BluetoothPrinterService.printUnified(
            context = context,
            connectionType = pType,
            printerNameOrMac = pTarget,
            textToPrint = reportText,
            ip = viewModel.printerIp.value,
            port = viewModel.printerPort.value,
            jobTitle = "Daily POS Report - $dateStr"
        )
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .width(360.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val companyName = storeSettings.shopName.ifBlank { if (lang == "AR") "الشركة" else "Entreprise" }
                Text(
                    text = companyName,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif
                )
                Text(
                    text = if (lang == "AR") "تقرير الإغلاق المالي اليومي (Z-Report)" else "Rapport Financier Journalier (Z)",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateStr,
                    color = Color.DarkGray,
                    fontSize = 11.sp
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray)

                Text(
                    text = reportText,
                    color = Color.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.beep()
                            launchZReportPrint(viewModel.printerConnectionType.value)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("z_report_print_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "AR") "طباعة" else "Imprimer", color = Color.White, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Daily POS Report - $dateStr")
                                putExtra(Intent.EXTRA_TEXT, reportText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Report"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("z_report_share_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "AR") "مشاركة" else "Partager", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (lang == "AR") "إغلاق" else "Fermer", color = Color.Gray)
                }
            }
        }
    }
}

/**
 * Universal Dialog for Previewing, Bluetooth Printing, and Sharing (PDF/Image) any Store List or Report.
 */
@Composable
fun UniversalPrintListDialog(
    title: String,
    subtitle: String,
    headers: List<String>,
    rows: List<List<String>>,
    summaryLines: List<Pair<String, String>> = emptyList(),
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val storeSettings by viewModel.settings.collectAsStateWithLifecycle()
    val companyName = storeSettings.shopName
    val thermalString = remember(title, subtitle, headers, rows, summaryLines, companyName) {
        generateListThermalString(title, subtitle, headers, rows, summaryLines, shopName = companyName)
    }
    val launchThermalListPrint = rememberBluetoothAwarePrintLauncher(lang = lang) {
        val pType = viewModel.printerConnectionType.value
        val pTarget = viewModel.printerMac.value.ifBlank { viewModel.printerName.value }
        BluetoothPrinterService.printUnified(
            context = context,
            connectionType = pType,
            printerNameOrMac = pTarget,
            textToPrint = thermalString,
            ip = viewModel.printerIp.value,
            port = viewModel.printerPort.value,
            jobTitle = title
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .width(360.dp)
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                        if (subtitle.isNotBlank()) {
                            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = thermalString,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF0F172A),
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            shareDocument(context, title, subtitle, headers, rows, summaryLines, asPdf = false, shopName = companyName)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF334155), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = "Share Image", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            shareDocument(context, title, subtitle, headers, rows, summaryLines, asPdf = true, shopName = companyName)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFDC2626), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Share PDF", tint = Color.White)
                    }
                    Button(
                        onClick = {
                            printDocument(context, title, subtitle, headers, rows, summaryLines, shopName = companyName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "AR") "طباعة الوثيقة" else "Imprimer A4", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = {
                            launchThermalListPrint(viewModel.printerConnectionType.value)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF16A34A), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.Receipt, contentDescription = "Thermal Receipt", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsChartsPane(salesHistory: List<SaleEntity>, lang: String, modifier: Modifier = Modifier) {
    val (entries, dayLabels) = remember(salesHistory) {
        val cal = Calendar.getInstance()
        val todayStart = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        val days = 7
        val dailyTotals = FloatArray(days) { 0f }
        val labels = Array(days) { "" }

        val format = SimpleDateFormat("EEE", Locale.US)
        for (i in 0 until days) {
            cal.timeInMillis = todayStart - ((days - 1 - i) * 86400000L)
            labels[i] = format.format(cal.time)
        }

        salesHistory.forEach { sale ->
            if (!sale.isVoided) {
                val daysAgo = ((todayStart + 86400000L - sale.timestamp) / 86400000L).toInt()
                if (daysAgo in 1..days) {
                    dailyTotals[days - daysAgo] += sale.totalAmountDouble.toFloat()
                }
            }
        }

        val floatEntries = dailyTotals.mapIndexed { index, value -> FloatEntry(index.toFloat(), value) }
        Pair(floatEntries, labels)
    }

    Column(modifier = modifier) {
        Text(if (lang == "AR") "إيرادات 7 أيام (Bar Chart)" else "Revenus sur 7 jours", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(16.dp))
        if (entries.isNotEmpty()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Chart(
                    chart = columnChart(),
                    model = entryModelOf(entries),
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(valueFormatter = { value, _ ->
                        dayLabels.getOrElse(value.toInt()) { "" }
                    })
                )
            }
        }
    }
}
