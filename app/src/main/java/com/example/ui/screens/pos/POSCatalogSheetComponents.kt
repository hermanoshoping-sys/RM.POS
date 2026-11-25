package com.example.ui.screens.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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

private val ProductTableHeaderBlue = Color(0xFF9EC5E8)
private val GreenCartButtonBg = Color(0xFF7CB342)
private val GreenCartIconTint = Color(0xFF1D4ED8)

/**
 * Green cart action tile displayed beside the barcode scanner button on both
 * the Sales and Purchases product catalog screens. Shows a live count badge
 * whenever items are in the cart.
 */
@Composable
fun GreenCartActionButton(
    itemCount: Int,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(48.dp)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = GreenCartButtonBg,
            border = BorderStroke(1.dp, Color(0xFF9CCC65)),
            modifier = Modifier
                .fillMaxSize()
                .testTag(testTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.ShoppingCart,
                    contentDescription = contentDescription,
                    tint = GreenCartIconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        if (itemCount > 0) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFDC2626),
                border = BorderStroke(1.5.dp, Color.White),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (itemCount > 99) "99+" else "$itemCount",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 3-column spreadsheet-style catalog row used by both Sales and Purchases main screens.
 * RTL (Arabic): Right = Barcode | Center = Product (+ optional cart qty badge) | Left = Price
 * LTR (FR/EN): Left = Barcode | Center = Product (+ optional cart qty badge) | Right = Price
 */
@Composable
fun POSProductSheetRow(
    colBarcode: String,
    colProduct: String,
    colPrice: String,
    lang: String,
    isHeader: Boolean = false,
    inCartQty: Double = 0.0,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rowBg = when {
        isHeader -> ProductTableHeaderBlue
        inCartQty < 0.0 -> Color(0xFFFEF2F2)
        inCartQty > 0.0 -> Color(0xFFF0FDF4)
        else -> Color.White
    }
    val dir = if (lang == "AR") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(32.dp)
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
            // Col 1 (Start -> Right in AR): كود بار / Code-barres
            SheetCellBox(
                modifier = Modifier.weight(1.25f),
                drawEndDivider = true,
                isRtl = dir == LayoutDirection.Rtl
            ) {
                Text(
                    text = colBarcode,
                    color = Color.Black,
                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                    fontSize = if (isHeader) 12.sp else 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Col 2 (Center): المنتجات / Produits
            SheetCellBox(
                modifier = Modifier.weight(2.6f),
                drawEndDivider = true,
                isRtl = dir == LayoutDirection.Rtl
            ) {
                if (isHeader || colProduct.isEmpty()) {
                    Text(
                        text = colProduct,
                        color = Color.Black,
                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (isHeader) 12.sp else 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = colProduct,
                            color = Color.Black,
                            fontWeight = if (inCartQty != 0.0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                        if (inCartQty != 0.0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val qtyLabel = if (inCartQty % 1.0 == 0.0) {
                                String.format(Locale.US, "%.0f", inCartQty)
                            } else {
                                String.format(Locale.US, "%.1f", inCartQty)
                            }
                            Surface(
                                color = if (inCartQty < 0.0) Color(0xFFDC2626) else Color(0xFF16A34A),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = "×$qtyLabel",
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Col 3 (End -> Left in AR): ثمن / Prix
            SheetCellBox(
                modifier = Modifier.weight(1.05f),
                drawEndDivider = false,
                isRtl = dir == LayoutDirection.Rtl
            ) {
                Text(
                    text = colPrice,
                    color = Color.Black,
                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = if (isHeader) 12.sp else 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun SheetCellBox(
    modifier: Modifier,
    drawEndDivider: Boolean,
    isRtl: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (drawEndDivider) {
                    Modifier.drawBehind {
                        val x = if (isRtl) 0f else size.width
                        drawLine(
                            color = Color.Black,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
        content = content
    )
}
