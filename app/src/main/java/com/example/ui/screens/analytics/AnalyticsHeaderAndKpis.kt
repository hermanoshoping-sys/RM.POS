package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun AnalyticsTopHeader(
    onMenuClick: () -> Unit,
    onPrinterClick: () -> Unit,
    onThermalPrintClick: () -> Unit,
    onPdfExportClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF0D1730)).border(1.dp, Color(0xFF1E2B4D), CircleShape)
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = onPrinterClick,
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF0D1730)).border(1.dp, Color(0xFF1E2B4D), CircleShape)
            ) {
                Icon(Icons.Filled.Print, contentDescription = "Printer Settings", tint = Color(0xFF00B0FF), modifier = Modifier.size(19.dp))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text("التقارير والتحليلات", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, textAlign = TextAlign.Center)
            Text("نظرة شاملة على أداء متجرك", color = Color(0xFF94A3B8), fontSize = 11.sp, textAlign = TextAlign.Center)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D1730))
                    .border(1.2.dp, Color(0xFF22C55E), RoundedCornerShape(8.dp))
                    .clickable { onThermalPrintClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Print, contentDescription = "Thermal Print", tint = Color(0xFF22C55E), modifier = Modifier.size(22.dp))
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.size(width = 34.dp, height = 38.dp).clickable { onPdfExportClick() }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(2.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF", tint = Color(0xFFDC2626), modifier = Modifier.size(19.dp))
                    Text("PDF", color = Color(0xFFDC2626), fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
internal fun AnalyticsDateAndFilterSection(
    selectedDate: String,
    selectedFilter: String,
    onDateClick: () -> Unit,
    onFilterSelect: (String) -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onDateClick,
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0D1730),
                border = BorderStroke(1.dp, AnalyticsCardBorderLuminous)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(selectedDate, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }

            val filters = listOf("اليوم", "الأسبوع الماضي", "هذا الشهر", "الشهر الماضي", "هذه السنة", "السنة الماضية", "من .. إلى", "كافة الفترات")
            filters.forEach { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    onClick = { onFilterSelect(filter) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) AnalyticsAccentBlue else Color(0xFF0B1428),
                    border = BorderStroke(1.dp, if (isSelected) AnalyticsAccentCyan.copy(alpha = 0.6f) else AnalyticsCardBorderLuminous),
                    shadowElevation = if (isSelected) 4.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Today, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                        Text(filter, color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AnalyticsSixStatGrid(
    sales: Double,
    netProfit: Double,
    expenses: Double,
    purchases: Double,
    supplierPayments: Double,
    debts: Double,
    onSalesClick: () -> Unit,
    onProfitClick: () -> Unit,
    onExpensesClick: () -> Unit,
    onPurchasesClick: () -> Unit = {},
    onSupplierPaymentsClick: () -> Unit = {},
    onDebtsClick: () -> Unit = {}
) {
    Surface(
        color = Color.White,
        border = BorderStroke(1.5.dp, Color.White),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(modifier = Modifier.fillMaxWidth().padding(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReportStatBox("مجموع المبيعات", String.format(Locale.US, "%.2f", sales), Color(0xFFFFD54F), Modifier.weight(1f), onSalesClick)
                    ReportStatBox("الأرباح", String.format(Locale.US, "%.2f", netProfit), Color(0xFF8CC63F), Modifier.weight(1f), onProfitClick)
                    ReportStatBox("المصاريف", String.format(Locale.US, "%.2f", expenses), Color(0xFFFFB300), Modifier.weight(1f), onExpensesClick)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReportStatBox("مجموع المشتريات", String.format(Locale.US, "%.2f", purchases), Color(0xFF00B0FF), Modifier.weight(1f), onPurchasesClick)
                    ReportStatBox("مدفوعات الموردين", String.format(Locale.US, "%.2f", supplierPayments), Color(0xFFF5A67B), Modifier.weight(1f), onSupplierPaymentsClick)
                    ReportStatBox("مجموع الديون", String.format(Locale.US, "%.2f", debts), Color(0xFFFF0000), Modifier.weight(1f), onDebtsClick)
                }
            }
        }
    }
}

@Composable
internal fun ReportStatBox(
    title: String,
    value: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        color = backgroundColor,
        border = BorderStroke(1.5.dp, Color.Black),
        shape = RoundedCornerShape(0.dp),
        modifier = modifier.height(56.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
            HorizontalDivider(color = Color.Black, thickness = 1.dp)
            Text(value, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.5.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun CompactMetricCard(
    icon: ImageVector,
    iconBg: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF060B18),
        border = BorderStroke(1.dp, Color(0xFF1E2B4D)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)).background(iconBg.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconBg, modifier = Modifier.size(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Color(0xFF94A3B8), fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
