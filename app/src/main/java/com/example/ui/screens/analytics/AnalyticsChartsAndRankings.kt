package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TopProductRecord
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
internal fun AnalyticsSalesChartCard(
    selectedTab: String,
    chartValues: List<Float>,
    avgTicket: Double,
    invoicesCount: Int,
    soldUnits: Int,
    currency: String = "MAD",
    onTabChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF091226)),
        border = BorderStroke(1.dp, Color(0xFF1E2B4D))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF060B18)).border(1.dp, Color(0xFF1E2B4D), RoundedCornerShape(8.dp)).padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("المبيعات", "الأرباح", "المصاريف").forEach { tab ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isSelected) Color(0xFF2563EB) else Color.Transparent).clickable { onTabChange(tab) }.padding(horizontal = 9.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tab, color = if (isSelected) Color.White else Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("المبيعات لكل شهر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = Color(0xFF00B0FF), modifier = Modifier.size(20.dp))
                }
            }

            MonthlySalesAreaChart(modifier = Modifier.fillMaxWidth().height(200.dp), values = chartValues)

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactMetricCard(Icons.Filled.Receipt, Color(0xFF00B0FF), "متوسط قيمة الفاتورة", MoneyUtils.format(avgTicket, currency), Modifier.weight(1f))
                    CompactMetricCard(Icons.Filled.Description, Color(0xFF2563EB), "عدد الفواتير", "$invoicesCount", Modifier.weight(1f))
                    CompactMetricCard(Icons.Filled.Inventory2, Color(0xFF6366F1), "المنتجات", "$soldUnits", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun MonthlySalesAreaChart(modifier: Modifier = Modifier, values: List<Float>) {
    val monthsLabels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
    val orderedValues = values.reversed()
    val rawMax = orderedValues.maxOrNull() ?: 300f
    val maxVal = if (rawMax > 0) (rawMax * 1.15f) else 339f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val leftPadding = 48f
        val bottomPadding = 30f
        val topPadding = 26f
        val chartHeight = h - bottomPadding - topPadding
        val chartWidth = w - leftPadding

        listOf(1.0f, 0.75f, 0.5f, 0.25f, 0f).forEach { frac ->
            val lvl = (maxVal * frac).toInt()
            val y = topPadding + chartHeight * (1f - frac)
            drawLine(color = Color(0xFF1E2B4D).copy(alpha = 0.5f), start = Offset(leftPadding, y), end = Offset(w, y), strokeWidth = 1f)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#64748B")
                textSize = 20f
                textAlign = android.graphics.Paint.Align.RIGHT
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(lvl.toString(), leftPadding - 8f, y + 6f, paint)
        }

        val count = monthsLabels.size
        val slotWidth = chartWidth / count
        val points = mutableListOf<Offset>()
        val monthPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 20f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        for (i in 0 until count) {
            val cx = leftPadding + (i + 0.5f) * slotWidth
            val v = if (i in orderedValues.indices) orderedValues[i] else 0f
            val cy = topPadding + (chartHeight - ((v / maxVal) * chartHeight).coerceIn(0f, chartHeight))
            points.add(Offset(cx, cy))
            drawContext.canvas.nativeCanvas.drawText(monthsLabels[i], cx, h - 6f, monthPaint)
        }

        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }
            val fillPath = Path().apply {
                addPath(path)
                lineTo(points.last().x, topPadding + chartHeight)
                lineTo(points.first().x, topPadding + chartHeight)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF00B0FF).copy(alpha = 0.65f), Color(0xFF0075FF).copy(alpha = 0.35f), Color(0xFF0044B3).copy(alpha = 0.05f)),
                    startY = topPadding,
                    endY = topPadding + chartHeight
                )
            )
            drawPath(path = path, color = Color(0xFF38BDF8), style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            val peakPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 20f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
            points.forEachIndexed { idx, p ->
                val v = if (idx in orderedValues.indices) orderedValues[idx] else 0f
                if (v > 0f) {
                    drawCircle(color = Color(0xFF00B0FF).copy(alpha = 0.5f), radius = 8f, center = p)
                    drawCircle(color = Color.White, radius = 4.5f, center = p)
                    drawContext.canvas.nativeCanvas.drawText(v.toInt().toString(), p.x, p.y - 10f, peakPaint)
                } else {
                    drawCircle(color = Color(0xFF38BDF8).copy(alpha = 0.6f), radius = 2.5f, center = p)
                }
            }
        }
    }
}

@Composable
internal fun PaymentMethodsDonutCard(
    totalAmount: Double,
    cash: Double,
    card: Double,
    bank: Double,
    credit: Double,
    cashPercent: Int,
    cardPercent: Int,
    bankPercent: Int,
    creditPercent: Int,
    currency: String = "MAD"
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AnalyticsCardDarkNavy),
        border = BorderStroke(1.dp, AnalyticsCardBorderLuminous)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("طرق الدفع", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Icon(Icons.Filled.CreditCard, contentDescription = null, tint = AnalyticsAccentCyan, modifier = Modifier.size(18.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                    val totalP = (cash + card + bank + credit).coerceAtLeast(1.0)
                    DonutChartCanvas(
                        modifier = Modifier.fillMaxSize(),
                        slices = listOf(
                            DonutSlice((cash / totalP).toFloat().coerceAtLeast(0.01f), AnalyticsAccentBlue),
                            DonutSlice((card / totalP).toFloat().coerceAtLeast(0.01f), AnalyticsAccentEmerald),
                            DonutSlice((bank / totalP).toFloat().coerceAtLeast(0.01f), AnalyticsAccentGold),
                            DonutSlice((credit / totalP).toFloat().coerceAtLeast(0.01f), AnalyticsAccentPurple)
                        )
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format(Locale.US, "%.2f", totalAmount), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text(currency, color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaymentRow(AnalyticsAccentBlue, "نقدي", MoneyUtils.format(cash, currency), "$cashPercent%")
                    PaymentRow(AnalyticsAccentEmerald, "بطاقة", MoneyUtils.format(card, currency), "$cardPercent%")
                    PaymentRow(AnalyticsAccentGold, "تحويل بنكي", MoneyUtils.format(bank, currency), "$bankPercent%")
                    PaymentRow(AnalyticsAccentPurple, "آجل", MoneyUtils.format(credit, currency), "$creditPercent%")
                }
            }
        }
    }
}

private data class DonutSlice(val fraction: Float, val color: Color)

@Composable
private fun DonutChartCanvas(modifier: Modifier = Modifier, slices: List<DonutSlice>) {
    Canvas(modifier = modifier) {
        val strokeWidth = 24f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = slice.fraction * 360f
            drawArc(
                color = slice.color, startAngle = startAngle, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun PaymentRow(dotColor: Color, title: String, amount: String, percent: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(amount, color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(percent, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}

private enum class ProductThumbnailType { COCA_COLA, WATER_BOTTLE, MILK }

@Composable
internal fun BestSellingProductsCard(
    topProducts: List<TopProductRecord>,
    currency: String = "MAD",
    onViewAllClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AnalyticsCardDarkNavy),
        border = BorderStroke(1.dp, AnalyticsCardBorderLuminous)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("الأكثر مبيعاً", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("👑", fontSize = 15.sp)
                }
                Text("عرض الكل", color = AnalyticsAccentCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onViewAllClick() })
            }

            if (topProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد مبيعات مسجلة بعد", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            } else {
                val maxQty = topProducts.firstOrNull()?.totalQty?.toFloat() ?: 1f
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    topProducts.take(3).forEachIndexed { idx, p ->
                        val rank = idx + 1
                        val rankColor = when (rank) { 1 -> Color(0xFFF59E0B); 2 -> Color(0xFF94A3B8); else -> Color(0xFFD97706) }
                        val prog = if (maxQty > 0f) (p.totalQty.toFloat() / maxQty) else 1.0f
                        val type = when {
                            p.productName.contains("Coca", ignoreCase = true) || p.productName.contains("كولا") -> ProductThumbnailType.COCA_COLA
                            p.productName.contains("ماء", ignoreCase = true) || p.productName.contains("eau", ignoreCase = true) -> ProductThumbnailType.WATER_BOTTLE
                            else -> ProductThumbnailType.MILK
                        }
                        RankedProductRow(rank, rankColor, p.productName, "${p.totalQty.toInt()} وحدة", MoneyUtils.format(p.totalRevenue, currency), prog, type)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankedProductRow(
    rank: Int,
    rankColor: Color,
    name: String,
    units: String,
    revenue: String,
    progress: Float,
    itemType: ProductThumbnailType
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(rankColor.copy(alpha = 0.15f)).border(1.dp, rankColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(when (rank) { 1 -> "👑"; 2 -> "🥈"; else -> "🥉" }, fontSize = 14.sp)
        }

        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F1B38)).border(1.dp, AnalyticsCardBorderLuminous, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            when (itemType) {
                ProductThumbnailType.COCA_COLA -> Box(
                    modifier = Modifier.width(18.dp).height(26.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFDC2626)),
                    contentAlignment = Alignment.Center
                ) { Text("Coke", color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Black) }
                ProductThumbnailType.WATER_BOTTLE -> Box(
                    modifier = Modifier.width(14.dp).height(28.dp).clip(RoundedCornerShape(5.dp)).background(Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7)))),
                    contentAlignment = Alignment.TopCenter
                ) { Box(modifier = Modifier.width(8.dp).height(4.dp).background(Color(0xFF0369A1))) }
                ProductThumbnailType.MILK -> Box(
                    modifier = Modifier.width(16.dp).height(26.dp).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Color(0xFF60A5FA), Color(0xFFE2E8F0)))),
                    contentAlignment = Alignment.Center
                ) { Text("حليب", color = Color(0xFF1E3A8A), fontSize = 6.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(units, color = Color(0xFF94A3B8), fontSize = 10.sp)
                }
                Text(revenue, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF132347))) {
                Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(AnalyticsAccentBlue, AnalyticsAccentCyan))))
            }
        }
    }
}
