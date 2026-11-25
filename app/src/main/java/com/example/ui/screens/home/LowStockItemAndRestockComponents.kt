package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import java.util.Locale
import kotlin.math.max

@Composable
internal fun SeverityPill(
    text: String,
    bgColor: Color,
    textColor: Color,
    borderColor: Color
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
internal fun ThresholdFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = if (selected) Color(0xFF0C325B) else Color(0xFFF1F5F9),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) Color(0xFF0C325B) else Color(0xFFCBD5E1))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Color(0xFF334155)
            )
        }
    }
}

@Composable
internal fun LowStockProductItemCard(
    product: ProductEntity,
    effectiveThreshold: Double,
    lang: String,
    onItemClick: () -> Unit,
    onQuickRestock: () -> Unit
) {
    val isOut = product.currentStock <= 0.0
    val safeThreshold = max(effectiveThreshold, 1.0)
    val stockRatio = (product.currentStock / safeThreshold).toFloat().coerceIn(0f, 1f)

    val accentColor = if (isOut) Color(0xFFDC2626) else Color(0xFFD97706)
    val cardBg = if (isOut) Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
    val cardBorder = if (isOut) Color(0xFFFECACA) else Color(0xFFFDE68A)

    val formattedCurrent = formatLowStockQty(product.currentStock)
    val formattedMin = formatLowStockQty(effectiveThreshold)
    val shortUnit = product.unit.substringBefore("/").trim().ifBlank {
        Bilingual.tr(lang, "قطعة", "Unité", "Unit")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onItemClick)
            .testTag("low_stock_item_${product.id}"),
        color = cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColor)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = accentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isOut) {
                                Bilingual.tr(lang, "نفد المخزون", "Rupture", "Out of Stock")
                            } else {
                                Bilingual.tr(lang, "تحت الحد", "Sous le seuil", "Below Min")
                            },
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(if (isOut) 0.04f else stockRatio.coerceAtLeast(0.1f))
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }

                    Text(
                        text = Bilingual.tr(
                            lang,
                            "المتبقي: $formattedCurrent / الحد: $formattedMin $shortUnit",
                            "Reste: $formattedCurrent / Seuil: $formattedMin",
                            "Stock: $formattedCurrent / Min: $formattedMin"
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                }
            }

            FilledTonalIconButton(
                onClick = onQuickRestock,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFF0C325B),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("low_stock_quick_restock_${product.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = Bilingual.tr(
                        lang,
                        "تزويد سريع للمخزون",
                        "Réapprovisionnement rapide",
                        "Quick restock"
                    ),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun LowStockQuickRestockDialog(
    product: ProductEntity,
    lang: String,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val recommendedStock = max(product.minStock + 5.0, product.currentStock + 10.0)
    var newStockText by remember(product.id) { mutableStateOf(formatLowStockQty(recommendedStock)) }
    var newMinThresholdText by remember(product.id) { mutableStateOf(formatLowStockQty(product.minStock)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = Color(0xFF0F172A),
        textContentColor = Color(0xFF334155),
        icon = {
            Icon(
                imageVector = Icons.Filled.Inventory,
                contentDescription = null,
                tint = Color(0xFF0C325B),
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = Bilingual.tr(
                    lang,
                    "تزويد سريع وضبط حد التنبيه",
                    "Réapprovisionnement & Seuil",
                    "Quick Restock & Threshold"
                ),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = Bilingual.tr(
                        lang,
                        "المخزون الحالي: ${formatLowStockQty(product.currentStock)} • الحد الأدنى الحالي: ${formatLowStockQty(product.minStock)}",
                        "Stock actuel: ${formatLowStockQty(product.currentStock)} • Seuil min: ${formatLowStockQty(product.minStock)}",
                        "Current stock: ${formatLowStockQty(product.currentStock)} • Min threshold: ${formatLowStockQty(product.minStock)}"
                    ),
                    fontSize = 12.5.sp,
                    color = Color(0xFFB91C1C),
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5.0, 10.0, 20.0, 50.0).forEach { addAmount ->
                        OutlinedButton(
                            onClick = {
                                val base = product.currentStock.coerceAtLeast(0.0)
                                newStockText = formatLowStockQty(base + addAmount)
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "+${formatLowStockQty(addAmount)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = newStockText,
                    onValueChange = { newStockText = it },
                    label = {
                        Text(
                            Bilingual.tr(
                                lang,
                                "الكمية الجديدة بالمخزن",
                                "Nouvelle quantité en stock",
                                "New Stock Quantity"
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_restock_quantity_input")
                )

                OutlinedTextField(
                    value = newMinThresholdText,
                    onValueChange = { newMinThresholdText = it },
                    label = {
                        Text(
                            Bilingual.tr(
                                lang,
                                "حد التنبيه الأدنى (Threshold)",
                                "Seuil d'alerte minimum",
                                "Low Stock Threshold"
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_restock_threshold_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedStock = newStockText.toDoubleOrNull() ?: product.currentStock
                    val updatedMin = newMinThresholdText.toDoubleOrNull() ?: product.minStock
                    viewModel.saveProduct(
                        id = product.id,
                        barcode = product.barcode,
                        name = product.name,
                        description = product.description,
                        categoryId = product.categoryId,
                        categoryName = product.categoryName,
                        unit = product.unit,
                        purchasePrice = product.purchasePriceDouble,
                        sellPrice = product.sellPriceDouble,
                        wholesalePrice = product.wholesalePriceDouble,
                        specialPrice = product.specialPriceDouble,
                        currentStock = updatedStock,
                        minStock = updatedMin,
                        imageUri = product.imageUri
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                modifier = Modifier.testTag("quick_restock_confirm_button")
            ) {
                Text(Bilingual.tr(lang, "حفظ التحديث", "Enregistrer", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"))
            }
        }
    )
}

internal fun formatLowStockQty(value: Double): String {
    return if (value % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}
