package com.example.ui.screens.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSPrimary
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
fun ProductCardUI(product: ProductEntity, lang: String, currency: String = "MAD", onAdd: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onAdd),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = POSPrimary, modifier = Modifier.size(24.dp))
                val isNeg = product.currentStock < 0.0
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isNeg) Color(0xFFDC2626) else if (product.currentStock <= product.minStock) Color(0xFFEF4444) else Color(0xFF10B981),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    val qStr = if (product.currentStock % 1.0 == 0.0) {
                        String.format(Locale.US, "%.0f", product.currentStock)
                    } else {
                        String.format(Locale.US, "%.1f", product.currentStock)
                    }
                    Text(qStr, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp))
                }
            }
            Text(product.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(MoneyUtils.format(product.sellPriceDouble, currency), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(POSPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun POSCatalogLedgerRow(
    colAdd: String,
    colPrice: String,
    colProduct: String,
    colBarcode: String,
    isHeader: Boolean = false,
    onClick: (() -> Unit)? = null,
    onSubtract: (() -> Unit)? = null
) {
    val rowHeight = if (isHeader) 32.dp else 30.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (isHeader) Color(0xFFBAE6FD) else Color.White)
            .then(if (onClick != null && onSubtract == null) Modifier.clickable { onClick() } else Modifier)
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
        // Col 1 (Leftmost): إضافة / ناقص
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(
                        color = Color.Black,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isHeader) {
                Text(
                    text = colAdd,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            } else if (onSubtract != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minus Button (ناقص)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.18f),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onSubtract() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Remove, contentDescription = "Subtract", tint = Color(0xFFDC2626), modifier = Modifier.size(15.dp))
                        }
                    }
                    // Plus Button (زائد)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onClick?.invoke() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color(0xFF047857), modifier = Modifier.size(15.dp))
                        }
                    }
                }
            } else if (colAdd.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color(0xFF047857), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Col 2: ثمن
        Box(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(
                        color = Color.Black,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colPrice,
                color = Color.Black,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium,
                fontSize = if (isHeader) 12.sp else 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // Col 3: المنتجات
        Box(
            modifier = Modifier
                .weight(2.4f)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(
                        color = Color.Black,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colProduct,
                color = Color.Black,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isHeader) 12.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // Col 4 (Rightmost): كود بار
        Box(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colBarcode,
                color = Color.Black,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isHeader) 12.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun POSCartLedgerRow(
    colTotal: String,
    colQty: String,
    colUnitPrice: String,
    colProduct: String,
    isHeader: Boolean = false,
    isReturn: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val rowHeight = if (isHeader) 32.dp else 28.dp
    val rowBg = when {
        isHeader -> Color(0xFF86EFAC)
        isReturn -> Color(0xFFFEE2E2)
        else -> Color.White
    }
    val textColor = when {
        isReturn -> Color(0xFFDC2626)
        else -> Color.Black
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(rowBg)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
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
        // Col 1 (Leftmost): المجموع
        Box(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(
                        color = Color.Black,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colTotal,
                color = textColor,
                fontWeight = if (isHeader || isReturn) FontWeight.Bold else FontWeight.Medium,
                fontSize = if (isHeader) 12.sp else 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // Col 2: كمية
        Box(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(
                        color = Color.Black,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colQty,
                color = textColor,
                fontWeight = if (isHeader || isReturn) FontWeight.Bold else FontWeight.Bold,
                fontSize = if (isHeader) 12.sp else 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // Col 3: ثمنه
        Box(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(
                        color = Color.Black,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colUnitPrice,
                color = if (isReturn) Color(0xFFB91C1C) else Color.Black,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium,
                fontSize = if (isHeader) 12.sp else 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        // Col 4 (Rightmost): المنتج
        Box(
            modifier = Modifier
                .weight(2.4f)
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colProduct,
                color = if (isReturn) Color(0xFF991B1B) else Color.Black,
                fontWeight = if (isHeader || isReturn) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isHeader) 12.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CartToolbarAction(
    icon: ImageVector,
    label: String,
    badgeCount: Int = 0,
    containerColor: Color = Color(0xFF1E293B),
    contentColor: Color = Color(0xFF94A3B8),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(16.dp))
            Text(text = label, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            if (badgeCount > 0) {
                Surface(shape = CircleShape, color = Color(0xFFEF4444)) {
                    Text(
                        text = "$badgeCount",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
