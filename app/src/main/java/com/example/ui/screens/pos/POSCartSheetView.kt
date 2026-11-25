package com.example.ui.screens.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val SheetDarkBg = Color(0xFF070E1A)
private val SheetHeaderDark = Color(0xFF0B1324)
private val CartTableHeaderGreen = Color(0xFF8CC63F)
private val TotalAmberBg = Color(0xFFF0B400)
private val ConfirmGreenBg = Color(0xFF84C341)

data class CartSheetRowData(
    val id: Long,
    val productName: String,
    val unitPrice: Double,
    val quantity: Double,
    val lineTotal: Double,
    val isReturn: Boolean = false,
    val testTag: String = ""
)

/**
 * Full-screen tabular cart view opened when tapping the green cart icon
 * in Sales ("سلة المبيعات") or Purchases ("سلة المشتريات").
 */
@Composable
fun POSCartSheetView(
    title: String,
    colProductHeader: String,
    colUnitPriceHeader: String,
    colQtyHeader: String,
    colTotalHeader: String,
    totalLabel: String,
    confirmButtonLabel: String,
    totalAmount: Double,
    currency: String,
    lang: String,
    items: List<CartSheetRowData>,
    onBack: () -> Unit,
    onClearCart: () -> Unit,
    onRowClick: (CartSheetRowData) -> Unit,
    onConfirmClick: () -> Unit,
    backButtonTestTag: String,
    confirmButtonTestTag: String,
    clearButtonTestTag: String
) {
    val dir = if (lang == "AR") LayoutDirection.Rtl else LayoutDirection.Ltr
    val formattedTotal = "${String.format(Locale.US, "%.2f", totalAmount)} $currency"

    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SheetDarkBg)
        ) {
            // Top Header: Circular blue cart icon | Centered Title | Back Arrow (+ Clear when non-empty)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SheetHeaderDark)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(42.dp)
                        .background(Color(0xFF1E3A8A).copy(alpha = 0.65f), CircleShape)
                        .border(1.5.dp, Color(0xFF3B82F6), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 92.dp)
                )

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (items.isNotEmpty()) {
                        IconButton(
                            onClick = onClearCart,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag(clearButtonTestTag)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = if (lang == "AR") "إفراغ السلة" else "Vider le panier",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(backButtonTestTag)
                    ) {
                        Icon(
                            imageVector = if (lang == "AR") {
                                Icons.AutoMirrored.Filled.ArrowForward
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = if (lang == "AR") "رجوع" else "Retour",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Full-height green-headed ledger table with bottom docked amber total & green confirm button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .border(1.dp, Color.Black)
                    .background(Color.White)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    CartSheetLedgerRow(
                        colProduct = colProductHeader,
                        colUnitPrice = colUnitPriceHeader,
                        colQty = colQtyHeader,
                        colTotal = colTotalHeader,
                        isHeader = true,
                        isRtl = dir == LayoutDirection.Rtl
                    )

                    val fillerCount = (22 - items.size).coerceAtLeast(0)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 134.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            val qtyFormatted = if (item.quantity % 1.0 == 0.0) {
                                String.format(Locale.US, "%.0f", item.quantity)
                            } else {
                                String.format(Locale.US, "%.2f", item.quantity)
                            }
                            CartSheetLedgerRow(
                                colProduct = item.productName,
                                colUnitPrice = String.format(Locale.US, "%.2f", item.unitPrice),
                                colQty = qtyFormatted,
                                colTotal = String.format(Locale.US, "%.2f", item.lineTotal),
                                isHeader = false,
                                isReturn = item.isReturn,
                                isRtl = dir == LayoutDirection.Rtl,
                                onClick = { onRowClick(item) },
                                modifier = if (item.testTag.isNotEmpty()) Modifier.testTag(item.testTag) else Modifier
                            )
                        }

                        items(count = fillerCount) {
                            CartSheetLedgerRow(
                                colProduct = "",
                                colUnitPrice = "",
                                colQty = "",
                                colTotal = "",
                                isHeader = false,
                                isRtl = dir == LayoutDirection.Rtl
                            )
                        }
                    }
                }

                // Bottom Total + Confirm Action (Red accent when net total is negative/return)
                val isNegativeTotal = totalAmount < 0.0
                val totalPillBg = if (isNegativeTotal) Color(0xFFDC2626) else TotalAmberBg
                val confirmBtnBg = if (isNegativeTotal) Color(0xFFB91C1C) else ConfirmGreenBg

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.34f)
                                .height(48.dp)
                                .shadow(3.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(totalPillBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = totalLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(0.66f)
                                .height(48.dp)
                                .shadow(3.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(totalPillBg),
                            contentAlignment = Alignment.Center
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = formattedTotal,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onConfirmClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(3.dp, RoundedCornerShape(12.dp))
                            .testTag(confirmButtonTestTag),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = confirmBtnBg)
                    ) {
                        Text(
                            text = confirmButtonLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartSheetLedgerRow(
    colProduct: String,
    colUnitPrice: String,
    colQty: String,
    colTotal: String,
    isHeader: Boolean,
    isReturn: Boolean = false,
    isRtl: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rowHeight = if (isHeader) 32.dp else 30.dp
    val rowBg = when {
        isHeader -> CartTableHeaderGreen
        isReturn -> Color(0xFFFEE2E2)
        else -> Color.White
    }
    val textColor = if (isReturn) Color(0xFFDC2626) else Color.Black

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(rowBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Col 1 (Start -> Right in AR): المنتج
        SheetCellBox(
            modifier = Modifier.weight(2.4f),
            drawEndDivider = true,
            isRtl = isRtl
        ) {
            Text(
                text = colProduct,
                color = textColor,
                fontWeight = if (isHeader || isReturn) FontWeight.Bold else FontWeight.Medium,
                fontSize = if (isHeader) 12.sp else 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // Col 2: ثمنه
        SheetCellBox(
            modifier = Modifier.weight(1.05f),
            drawEndDivider = true,
            isRtl = isRtl
        ) {
            Text(
                text = colUnitPrice,
                color = textColor,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium,
                fontSize = if (isHeader) 12.sp else 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // Col 3: كمية
        SheetCellBox(
            modifier = Modifier.weight(0.85f),
            drawEndDivider = true,
            isRtl = isRtl
        ) {
            Text(
                text = colQty,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = if (isHeader) 12.sp else 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // Col 4 (End -> Left in AR): المجموع
        SheetCellBox(
            modifier = Modifier.weight(1.15f),
            drawEndDivider = false,
            isRtl = isRtl
        ) {
            Text(
                text = colTotal,
                color = textColor,
                fontWeight = if (isHeader || isReturn) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = if (isHeader) 12.sp else 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}
