package com.example.ui.screens.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ProductEntity
import com.example.util.MoneyUtils
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun CatalogPassHeaderAndStockBanner(
    product: ProductEntity,
    isAr: Boolean,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            val thumb = product.imageList.firstOrNull()
            if (!thumb.isNullOrBlank()) {
                AsyncImage(
                    model = thumb,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.ShoppingBag,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = product.unit,
                        color = Color(0xFF93C5FD),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "${if (isAr) "المخزون:" else "Stock:"} ${formatCleanQty(product.currentStock)}",
                    color = if (product.currentStock <= product.minStock) Color(0xFFF87171) else Color(0xFF34D399),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF1E293B), CircleShape)
                .testTag("catalog_pass_close_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = if (isAr) "إغلاق" else "Close",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    val isOutOfStock = product.currentStock <= 0.0
    val isLowStock = !isOutOfStock && product.currentStock <= product.minStock
    val stockAccent = when {
        isOutOfStock -> Color(0xFFEF4444)
        isLowStock -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }
    val stockBg = when {
        isOutOfStock -> Color(0xFF7F1D1D).copy(alpha = 0.35f)
        isLowStock -> Color(0xFF78350F).copy(alpha = 0.35f)
        else -> Color(0xFF064E3B).copy(alpha = 0.4f)
    }
    Surface(
        color = stockBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, stockAccent.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog_pass_stock_badge")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = stockAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isAr) "مخزون المنتوج المتوفر" else "Stock disponible",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${formatCleanQty(product.currentStock)} ${product.unit}",
                    color = stockAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                if (isOutOfStock || isLowStock) {
                    Surface(
                        color = stockAccent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when {
                                isOutOfStock && isAr -> "نفد"
                                isOutOfStock -> "Rupture"
                                isAr -> "منخفض"
                                else -> "Bas"
                            },
                            color = stockAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CatalogPassQuantitySection(
    isAr: Boolean,
    isPurchaseMode: Boolean,
    quantityText: String,
    parsedQty: Double,
    onQuantityTextChange: (String) -> Unit
) {
    val isReturnMode = parsedQty < 0.0 || quantityText.trim().startsWith("-")
    val activeAccent = if (isReturnMode) Color(0xFFEF4444) else Color(0xFF38BDF8)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isReturnMode) {
                    if (isPurchaseMode) {
                        if (isAr) "الكمية المسترجعة للمورد (-)" else "Qté retournée fournisseur (-)"
                    } else {
                        if (isAr) "الكمية المسترجعة من الزبون (-)" else "Qté retournée client (-)"
                    }
                } else {
                    if (isAr) "الكمية" else "Quantité"
                },
                color = if (isReturnMode) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
        }

        // Mode toggle: Normal Sale/Purchase (+) vs Return (-)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val normalLabel = if (isPurchaseMode) {
                if (isAr) "شراء (+)" else "Achat (+)"
            } else {
                if (isAr) "بيع (+)" else "Vente (+)"
            }
            val returnLabel = if (isPurchaseMode) {
                if (isAr) "استرجاع للمورد (-)" else "Retour fourn. (-)"
            } else {
                if (isAr) "استرجاع زبون (-)" else "Retour client (-)"
            }

            Surface(
                onClick = {
                    val mag = abs(parsedQty).let { if (it < 0.0001) 1.0 else it }
                    onQuantityTextChange(formatCleanQty(mag))
                },
                color = if (!isReturnMode) Color(0xFF065F46) else Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.2.dp,
                    if (!isReturnMode) Color(0xFF34D399) else Color(0xFF334155)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("catalog_pass_normal_mode_toggle")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = normalLabel,
                        color = if (!isReturnMode) Color.White else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                onClick = {
                    val mag = abs(parsedQty).let { if (it < 0.0001) 1.0 else it }
                    onQuantityTextChange(formatCleanQty(-mag))
                },
                color = if (isReturnMode) Color(0xFF991B1B) else Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.2.dp,
                    if (isReturnMode) Color(0xFFEF4444) else Color(0xFF334155)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("catalog_pass_return_mode_toggle")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = returnLabel,
                        color = if (isReturnMode) Color.White else Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val candidate = parsedQty - 1.0
                        val nextQty = if (abs(candidate) < 0.0001) -1.0 else candidate
                        onQuantityTextChange(formatCleanQty(nextQty))
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF7F1D1D).copy(alpha = 0.55f),
                        contentColor = Color(0xFFFCA5A5)
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("catalog_pass_qty_minus")
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = if (isAr) "إنقاص الكمية" else "Decrease")
                }

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { raw ->
                        onQuantityTextChange(sanitizeSignedDecimalInput(raw))
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        textDirection = TextDirection.Ltr,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (isReturnMode) Color(0xFFFCA5A5) else Color.White
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeAccent,
                        unfocusedBorderColor = if (isReturnMode) Color(0xFFDC2626) else Color(0xFF334155),
                        focusedContainerColor = if (isReturnMode) Color(0xFF2A0B0E) else Color(0xFF0B1324),
                        unfocusedContainerColor = if (isReturnMode) Color(0xFF2A0B0E) else Color(0xFF0B1324),
                        focusedTextColor = if (isReturnMode) Color(0xFFFCA5A5) else Color.White,
                        unfocusedTextColor = if (isReturnMode) Color(0xFFFCA5A5) else Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("catalog_pass_qty_input")
                )

                FilledTonalIconButton(
                    onClick = {
                        val candidate = parsedQty + 1.0
                        val nextQty = if (abs(candidate) < 0.0001) 1.0 else candidate
                        onQuantityTextChange(formatCleanQty(nextQty))
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF065F46).copy(alpha = 0.55f),
                        contentColor = Color(0xFF6EE7B7)
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("catalog_pass_qty_plus")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = if (isAr) "زيادة الكمية" else "Increase")
                }
            }

            val quickChoices = if (isReturnMode) {
                listOf(-1.0, -2.0, -5.0, -10.0)
            } else {
                listOf(1.0, 2.0, 5.0, 10.0)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickChoices.forEach { quickQty ->
                    val isSelected = abs(parsedQty - quickQty) < 0.001
                    Surface(
                        color = when {
                            isSelected && isReturnMode -> Color(0xFFDC2626)
                            isSelected -> Color(0xFF0284C7)
                            else -> Color(0xFF1E293B)
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSelected && isReturnMode -> Color(0xFFFCA5A5)
                                isSelected -> Color(0xFF38BDF8)
                                isReturnMode -> Color(0xFF7F1D1D)
                                else -> Color(0xFF334155)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable { onQuantityTextChange(formatCleanQty(quickQty)) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = formatCleanQty(quickQty),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun sanitizeSignedDecimalInput(raw: String): String {
    val normalized = raw.replace(',', '.').replace('−', '-').replace('–', '-')
    val sb = StringBuilder()
    var hasDot = false
    for (ch in normalized) {
        when {
            ch == '-' && sb.isEmpty() -> sb.append('-')
            ch.isDigit() -> sb.append(ch)
            ch == '.' && !hasDot -> {
                sb.append('.')
                hasDot = true
            }
        }
    }
    return sb.toString()
}

internal fun formatCleanQty(value: Double): String {
    val clean = if (abs(value) < 0.000001) 0.0 else value
    return if (clean % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", clean)
    } else {
        String.format(Locale.US, "%.2f", clean).trimEnd('0').trimEnd('.')
    }
}
