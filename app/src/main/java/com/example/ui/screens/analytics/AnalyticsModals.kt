package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ExpenseEntity
import com.example.data.TopProductRecord
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AddExpenseModalDialog(
    currency: String = "MAD",
    onAdd: (String, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("عام") }
    val categories = remember { listOf("عام", "فواتير كهرباء/ماء", "إيجار", "رواتب", "صيانة", "نقل وتوصيل") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AnalyticsCardDarkNavy),
            border = BorderStroke(1.dp, AnalyticsCardBorderLuminous),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("تسجيل مصروف جديد", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("بيان المصروف (الوصف)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AnalyticsAccentCyan, unfocusedBorderColor = AnalyticsCardBorderLuminous),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    label = { Text("المبلغ ($currency)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AnalyticsAccentCyan, unfocusedBorderColor = AnalyticsCardBorderLuminous),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("التصنيف:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat, fontSize = 11.sp) })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = Color.Gray) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0) onAdd(title.ifBlank { "مصروف متجر" }, amt, selectedCategory)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AnalyticsAccentRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ المصروف", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExpensesListModalDialog(
    expenses: List<ExpenseEntity>,
    currency: String = "MAD",
    onAddNew: () -> Unit,
    onDelete: (ExpenseEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val timeDateFormat = remember { SimpleDateFormat("HH:mm dd/MM", Locale.US) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AnalyticsCardDarkNavy),
            border = BorderStroke(1.dp, AnalyticsCardBorderLuminous),
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("قائمة المصاريف المسجلة (${expenses.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = onAddNew) { Icon(Icons.Filled.AddCircle, contentDescription = "Add", tint = AnalyticsAccentCyan) }
                }

                if (expenses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد مصاريف مسجلة في هذه الفترة", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(expenses, key = { it.id }, contentType = { "expense_row" }) { exp ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF081024), RoundedCornerShape(10.dp)).border(1.dp, AnalyticsCardBorderLuminous, RoundedCornerShape(10.dp)).padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exp.title.ifBlank { exp.category }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(exp.category, color = AnalyticsAccentCyan, fontSize = 10.sp)
                                        Text(timeDateFormat.format(Date(exp.timestamp)), color = Color.Gray, fontSize = 10.sp)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("-${MoneyUtils.format(exp.amountDouble, currency)}", color = AnalyticsAccentRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    IconButton(onClick = { onDelete(exp) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إغلاق", color = Color(0xFF94A3B8)) }
                }
            }
        }
    }
}

@Composable
internal fun AllTopProductsModalDialog(
    topProducts: List<TopProductRecord>,
    currency: String = "MAD",
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AnalyticsCardDarkNavy),
            border = BorderStroke(1.dp, AnalyticsCardBorderLuminous),
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("ترتيب المنتجات الأكثر مبيعاً 👑", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray) }
                }

                if (topProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد مبيعات مسجلة بعد", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(topProducts.withIndex().toList(), key = { it.value.productId }, contentType = { "top_product" }) { (idx, item) ->
                            val rank = idx + 1
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF081024), RoundedCornerShape(8.dp)).padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("#$rank", color = when (rank) { 1 -> AnalyticsAccentGold; 2 -> Color(0xFF94A3B8); else -> Color(0xFFD97706) }, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                    Column {
                                        Text(item.productName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${item.totalQty.toInt()} وحدة مباعة", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                    }
                                }
                                Text(MoneyUtils.format(item.totalRevenue, currency), color = AnalyticsAccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إغلاق", color = AnalyticsAccentCyan) }
                }
            }
        }
    }
}

@Composable
internal fun SalesSummaryModalDialog(
    sales: Double,
    invoices: Int,
    profit: Double,
    netProfit: Double,
    margin: Double,
    avgTicket: Double,
    currency: String = "MAD",
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AnalyticsCardDarkNavy),
            border = BorderStroke(1.dp, AnalyticsCardBorderLuminous),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("ملخص مؤشرات المبيعات والأرباح", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF081024), RoundedCornerShape(10.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnalyticsSummaryRow("إجمالي المبيعات", MoneyUtils.format(sales, currency), AnalyticsAccentBlue)
                    AnalyticsSummaryRow("عدد الفواتير الصادرة", "$invoices فاتورة", Color.White)
                    AnalyticsSummaryRow("متوسط قيمة الفاتورة", MoneyUtils.format(avgTicket, currency), AnalyticsAccentCyan)
                    HorizontalDivider(color = AnalyticsCardBorderLuminous, thickness = 0.5.dp)
                    AnalyticsSummaryRow("إجمالي الربح الحقيقي", MoneyUtils.format(profit, currency), AnalyticsAccentPurple)
                    AnalyticsSummaryRow("هامش الربح", String.format(Locale.US, "%.1f%%", margin), AnalyticsAccentEmerald)
                    AnalyticsSummaryRow("صافي الربح النهائي", MoneyUtils.format(netProfit, currency), AnalyticsAccentEmerald)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إغلاق", color = AnalyticsAccentCyan) }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsSummaryRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 12.sp)
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
internal fun ReportPrintOptionsModalDialog(
    lang: String,
    onPrintA4: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenZReport: () -> Unit,
    onOpenPrinterSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
            modifier = Modifier.width(340.dp).padding(10.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (lang == "AR") "خيارات طباعة وتصدير التقرير" else "Options d'impression", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.Gray)
                    }
                }
                HorizontalDivider(color = Color(0xFF1E293B))
                Button(onClick = onPrintA4, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "AR") "طباعة التقرير الشامل (A4 / طابعة)" else "Imprimer Rapport A4", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onExportPdf, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "AR") "مشاركة التقرير بصيغة PDF" else "Exporter en PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onOpenZReport, modifier = Modifier.fillMaxWidth().height(48.dp), border = BorderStroke(1.dp, Color(0xFF22C55E)), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "AR") "طباعة تقرير الإغلاق (Z-Report)" else "Imprimer Z-Report", color = Color.White, fontSize = 12.sp)
                }
                OutlinedButton(onClick = onOpenPrinterSettings, modifier = Modifier.fillMaxWidth().height(48.dp), border = BorderStroke(1.dp, Color(0xFF38BDF8)), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Filled.SettingsBluetooth, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "AR") "إعدادات الطابعة والاتصال" else "Paramètres Imprimante", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
