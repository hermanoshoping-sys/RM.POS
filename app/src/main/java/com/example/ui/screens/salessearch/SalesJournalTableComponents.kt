package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import java.util.Locale

internal fun formatQtyString(qty: Double): String =
    if (qty % 1.0 == 0.0) String.format(Locale.US, "%.0f", qty) else String.format(Locale.US, "%.1f", qty)

internal fun formatMoneyString(amount: Double): String =
    if (amount >= 0) String.format(Locale.US, "+%.2f", amount) else String.format(Locale.US, "%.2f", amount)

@Composable
internal fun SingleProductStatsBanner(
    selectedProduct: ProductEntity,
    stats: SingleProductJournalStats,
    onClear: () -> Unit
) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.2.dp, Color(0xFF22C55E)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                        Text("حساب كمية المنتج: ${selectedProduct.name}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF4ADE80))
                    }
                    TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), modifier = Modifier.height(24.dp)) {
                        Text("إلغاء التصفية ✕", color = Color(0xFFF87171), fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("المباع: ${formatQtyString(stats.soldQty)}", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("استرجاع زبون: +${formatQtyString(stats.returnCustQty)}", color = Color(0xFFF87171), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    if (stats.returnSuppQty != 0.0) {
                        Text("استرجاع مورد: ${formatQtyString(stats.returnSuppQty)}", color = Color(0xFFFACC15), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    if (stats.purchasedQty != 0.0) {
                        Text("المشترى: +${formatQtyString(stats.purchasedQty)}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Text("الصافي: ${formatQtyString(stats.netQuantity)}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text("المخزون: ${formatQtyString(stats.currentStock)}", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
internal fun MovementLegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF2563EB)))
            Text("أزرق: بيع (-كمية/+ربح)", color = Color(0xFF93C5FD), fontSize = 9.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFDC2626)))
            Text("أحمر: استرجاع زبون", color = Color(0xFFFCA5A5), fontSize = 9.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFD97706)))
            Text("أصفر: استرجاع للمورد", color = Color(0xFFFDE047), fontSize = 9.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF16A34A)))
            Text("أخضر: مشتريات (+كمية)", color = Color(0xFF86EFAC), fontSize = 9.sp)
        }
    }
}

@Composable
internal fun SalesStatBox(title: String, value: String, backgroundColor: Color, modifier: Modifier = Modifier) {
    Surface(
        color = backgroundColor,
        border = BorderStroke(1.5.dp, Color.Black),
        shape = RoundedCornerShape(0.dp),
        modifier = modifier.height(46.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = Color.Black, thickness = 1.dp)
            Text(value, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.5.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun LedgerHeaderCell(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier = modifier.fillMaxHeight().border(BorderStroke(0.7.dp, Color.Black)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun LedgerBodyCell(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    isBold: Boolean = false,
    align: TextAlign = TextAlign.Center,
    maxLines: Int = 1
) {
    Box(
        modifier = modifier.fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)).padding(horizontal = 1.5.dp),
        contentAlignment = when (align) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Text(
            text = text, color = textColor, fontSize = if (maxLines > 1) 8.5.sp else 9.sp,
            lineHeight = 10.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun SalesJournalTableRow(
    entry: SalesJournalEntry,
    dateText: String,
    onClick: () -> Unit
) {
    val qStr = formatQtyString(entry.displayQuantity)
    val profitStr = if (entry.type == MovementType.SUPPLIER_RETURN || entry.type == MovementType.PURCHASE) "0.00" else formatMoneyString(entry.profit)
    val (rowTextColor, rowBgColor) = when (entry.type) {
        MovementType.CUSTOMER_RETURN -> Color(0xFFDC2626) to Color(0xFFFEF2F2)
        MovementType.SALE -> Color(0xFF1D4ED8) to Color(0xFFEFF6FF)
        MovementType.SUPPLIER_RETURN -> Color(0xFFB45309) to Color(0xFFFEFCE8)
        MovementType.PURCHASE -> Color(0xFF15803D) to Color(0xFFF0FDF4)
    }

    Row(modifier = Modifier.fillMaxWidth().height(30.dp).background(rowBgColor).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        LedgerBodyCell(dateText, rowTextColor, Modifier.weight(0.85f))
        LedgerBodyCell(entry.productName, rowTextColor, Modifier.weight(1.80f), isBold = true, align = TextAlign.Start, maxLines = 2)
        LedgerBodyCell(entry.accountName, rowTextColor, Modifier.weight(1.45f), align = TextAlign.Start, maxLines = 2)
        LedgerBodyCell(entry.invoiceNumber, rowTextColor, Modifier.weight(0.70f), isBold = true)
        LedgerBodyCell(qStr, rowTextColor, Modifier.weight(0.65f), isBold = true)
        LedgerBodyCell(String.format(Locale.US, "%.2f", entry.purchasePrice), rowTextColor, Modifier.weight(0.85f))
        LedgerBodyCell(String.format(Locale.US, "%.2f", entry.sellPrice), rowTextColor, Modifier.weight(0.85f), isBold = true)
        LedgerBodyCell(profitStr, rowTextColor, Modifier.weight(0.85f), isBold = true)
    }
}
