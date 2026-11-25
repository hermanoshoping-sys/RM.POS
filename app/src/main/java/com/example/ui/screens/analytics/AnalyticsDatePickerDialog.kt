package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class DatePresetOption(val label: String, val filterName: String, val start: Long, val end: Long)

@Composable
internal fun DateSelectorModalDialog(
    selectedDateText: String,
    initialStartTs: Long,
    initialEndTs: Long,
    onSelectRange: (label: String, startTs: Long, endTs: Long, filterName: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var startTs by remember { mutableStateOf(initialStartTs) }
    var endTs by remember { mutableStateOf(initialEndTs) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }

    val presets = remember {
        val now = System.currentTimeMillis()
        val calTodayStart = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val calTodayEnd = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
        val lastWeekStart = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val thisMonthStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val lastMonthStart = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val lastMonthEnd = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
        val thisYearStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val lastYearStart = Calendar.getInstance().apply { add(Calendar.YEAR, -1); set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val lastYearEnd = Calendar.getInstance().apply { add(Calendar.YEAR, -1); set(Calendar.DAY_OF_YEAR, getActualMaximum(Calendar.DAY_OF_YEAR)); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }

        listOf(
            DatePresetOption("اليوم (${dateFormat.format(Date(now))})", "اليوم", calTodayStart.timeInMillis, calTodayEnd.timeInMillis),
            DatePresetOption("الأسبوع الماضي", "الأسبوع الماضي", lastWeekStart.timeInMillis, calTodayEnd.timeInMillis),
            DatePresetOption("هذا الشهر (${SimpleDateFormat("MM/yyyy", Locale.US).format(Date(now))})", "هذا الشهر", thisMonthStart.timeInMillis, calTodayEnd.timeInMillis),
            DatePresetOption("الشهر الماضي", "الشهر الماضي", lastMonthStart.timeInMillis, lastMonthEnd.timeInMillis),
            DatePresetOption("هذه السنة (${Calendar.getInstance().get(Calendar.YEAR)})", "هذه السنة", thisYearStart.timeInMillis, calTodayEnd.timeInMillis),
            DatePresetOption("السنة الماضية (${Calendar.getInstance().get(Calendar.YEAR) - 1})", "السنة الماضية", lastYearStart.timeInMillis, lastYearEnd.timeInMillis),
            DatePresetOption("كافة الفترات (الكل)", "كافة الفترات", 0L, Long.MAX_VALUE)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AnalyticsCardDarkNavy),
            border = BorderStroke(1.dp, AnalyticsCardBorderLuminous),
            modifier = Modifier.fillMaxWidth().padding(10.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("📅 اختيار فترة أو تاريخ محدد", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AnalyticsBgDarkMidnight),
                    border = BorderStroke(1.5.dp, AnalyticsAccentCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تحديد تاريخ محدد (من .. إلى):", color = AnalyticsAccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val c = Calendar.getInstance().apply { timeInMillis = startTs }
                                    android.app.DatePickerDialog(context, { _, y, m, d ->
                                        startTs = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, AnalyticsAccentCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Filled.DateRange, contentDescription = null, tint = AnalyticsAccentCyan, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("من: ${dateFormat.format(Date(startTs))}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    val c = Calendar.getInstance().apply { timeInMillis = endTs }
                                    android.app.DatePickerDialog(context, { _, y, m, d ->
                                        endTs = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
                                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, AnalyticsAccentCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Filled.DateRange, contentDescription = null, tint = AnalyticsAccentCyan, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إلى: ${dateFormat.format(Date(endTs))}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = {
                                val finalStart = minOf(startTs, endTs)
                                val finalEnd = maxOf(startTs, endTs)
                                onSelectRange("من ${dateFormat.format(Date(finalStart))} إلى ${dateFormat.format(Date(finalEnd))}", finalStart, finalEnd, "من .. إلى")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AnalyticsAccentBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("تطبيق الفترة المحددة (من .. إلى)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                Text("فترات جاهزة وسريعة:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                presets.forEach { opt ->
                    val isCurrent = selectedDateText == opt.label || selectedDateText == opt.filterName
                    Surface(
                        onClick = { onSelectRange(opt.label, opt.start, opt.end, opt.filterName) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) AnalyticsAccentBlue.copy(alpha = 0.25f) else Color(0xFF081024),
                        border = BorderStroke(1.dp, if (isCurrent) AnalyticsAccentCyan else AnalyticsCardBorderLuminous),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = if (isCurrent) AnalyticsAccentCyan else Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            Text(opt.label, color = Color.White, fontSize = 12.5.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء", color = Color(0xFF94A3B8)) }
                }
            }
        }
    }
}
