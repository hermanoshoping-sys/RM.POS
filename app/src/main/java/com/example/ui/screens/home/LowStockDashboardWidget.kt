package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

/**
 * Threshold filter mode for the Low Stock Dashboard Widget.
 */
enum class LowStockThresholdMode(val customLimit: Double?) {
    PRODUCT_DEFAULT(null),
    OUT_OF_STOCK(0.0),
    THRESHOLD_5(5.0),
    THRESHOLD_10(10.0)
}

/**
 * Interactive 'Low Stock' Dashboard Widget that visually highlights products whose inventory
 * has fallen at or below a predefined threshold, with severity indicators, stock progress bars,
 * threshold filter chips, and one-tap quick restocking.
 */
@Composable
fun LowStockDashboardWidget(
    products: List<ProductEntity>,
    lowStockProducts: List<ProductEntity>,
    lang: String,
    viewModel: PosViewModel,
    onOpenInventoryLowStock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thresholdMode by rememberSaveable { mutableStateOf(LowStockThresholdMode.PRODUCT_DEFAULT) }
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var showAllItems by rememberSaveable { mutableStateOf(false) }
    var productForQuickRestock by remember { mutableStateOf<ProductEntity?>(null) }

    val activeSource = remember(products, lowStockProducts, thresholdMode) {
        val baseList = when (thresholdMode) {
            LowStockThresholdMode.PRODUCT_DEFAULT -> {
                if (lowStockProducts.isNotEmpty()) lowStockProducts
                else products.filter { it.currentStock <= it.minStock }
            }
            LowStockThresholdMode.OUT_OF_STOCK -> {
                products.filter { it.currentStock <= 0.0 }
            }
            LowStockThresholdMode.THRESHOLD_5 -> {
                products.filter { it.currentStock <= 5.0 }
            }
            LowStockThresholdMode.THRESHOLD_10 -> {
                products.filter { it.currentStock <= 10.0 }
            }
        }
        baseList.sortedWith(
            compareBy<ProductEntity> { it.currentStock <= 0.0 }.reversed()
                .thenBy {
                    val threshold = thresholdMode.customLimit ?: it.minStock.coerceAtLeast(1.0)
                    it.currentStock / threshold.coerceAtLeast(1.0)
                }
                .thenBy { it.currentStock }
        )
    }

    val defaultLowCount = remember(products, lowStockProducts) {
        if (lowStockProducts.isNotEmpty()) lowStockProducts.size
        else products.count { it.currentStock <= it.minStock }
    }
    val outOfStockCount = remember(activeSource) { activeSource.count { it.currentStock <= 0.0 } }
    val lowWarningCount = remember(activeSource, outOfStockCount) { (activeSource.size - outOfStockCount).coerceAtLeast(0) }
    val hasAlerts = activeSource.isNotEmpty() || defaultLowCount > 0

    val layoutDir = if (lang == "AR") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = if (hasAlerts) Color(0x33DC2626) else Color(0x1A0F2942),
                    spotColor = if (hasAlerts) Color(0x33EA580C) else Color(0x200F2942)
                )
                .clip(RoundedCornerShape(18.dp))
                .testTag("low_stock_dashboard_widget"),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                width = 1.5.dp,
                color = when {
                    outOfStockCount > 0 -> Color(0xFFFCA5A5)
                    hasAlerts -> Color(0xFFFCD34D)
                    else -> Color(0xFFBBF7D0)
                }
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Header Banner with gradient accent
                val headerBrush = when {
                    outOfStockCount > 0 -> Brush.horizontalGradient(
                        listOf(Color(0xFFFEF2F2), Color(0xFFFFF7ED))
                    )
                    hasAlerts -> Brush.horizontalGradient(
                        listOf(Color(0xFFFFFBEB), Color(0xFFFEF3C7))
                    )
                    else -> Brush.horizontalGradient(
                        listOf(Color(0xFFF0FDF4), Color(0xFFECFDF5))
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBrush)
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("low_stock_widget_header"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        outOfStockCount > 0 -> Color(0xFFDC2626)
                                        hasAlerts -> Color(0xFFD97706)
                                        else -> Color(0xFF16A34A)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    outOfStockCount > 0 -> Icons.Filled.ErrorOutline
                                    hasAlerts -> Icons.Filled.WarningAmber
                                    else -> Icons.Filled.Inventory2
                                },
                                contentDescription = Bilingual.tr(
                                    lang,
                                    "مؤشر نقص المخزون",
                                    "Indicateur de stock faible",
                                    "Low stock indicator"
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = Bilingual.tr(
                                        lang,
                                        "مراقبة نقص المخزون",
                                        "Suivi de Stock Faible",
                                        "Low Stock Monitor"
                                    ),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Surface(
                                    color = when {
                                        outOfStockCount > 0 -> Color(0xFFDC2626)
                                        hasAlerts -> Color(0xFFD97706)
                                        else -> Color(0xFF16A34A)
                                    },
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "${activeSource.size}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (activeSource.isEmpty()) {
                                    Text(
                                        text = Bilingual.tr(
                                            lang,
                                            "جميع المنتجات فوق حد التنبيه المحدد",
                                            "Tous les produits sont au-dessus du seuil",
                                            "All items are above the predefined threshold"
                                        ),
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF15803D),
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    if (outOfStockCount > 0) {
                                        SeverityPill(
                                            text = Bilingual.tr(
                                                lang,
                                                "نفد: $outOfStockCount",
                                                "Rupture: $outOfStockCount",
                                                "Out: $outOfStockCount"
                                            ),
                                            bgColor = Color(0xFFFEE2E2),
                                            textColor = Color(0xFFB91C1C),
                                            borderColor = Color(0xFFFCA5A5)
                                        )
                                    }
                                    if (lowWarningCount > 0) {
                                        SeverityPill(
                                            text = Bilingual.tr(
                                                lang,
                                                "منخفض: $lowWarningCount",
                                                "Faible: $lowWarningCount",
                                                "Low: $lowWarningCount"
                                            ),
                                            bgColor = Color(0xFFFEF3C7),
                                            textColor = Color(0xFFB45309),
                                            borderColor = Color(0xFFFCD34D)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = onOpenInventoryLowStock,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("low_stock_view_all_button")
                        ) {
                            Text(
                                text = Bilingual.tr(lang, "المخزن", "Stock", "Inventory"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("low_stock_widget_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = Bilingual.tr(
                                    lang,
                                    "طي أو توسيع تنبيهات المخزون",
                                    "Développer ou réduire",
                                    "Expand or collapse low stock widget"
                                ),
                                tint = Color(0xFF334155)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Threshold Filter Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = Bilingual.tr(lang, "حد التنبيه:", "Seuil:", "Threshold:"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )

                            ThresholdFilterChip(
                                label = Bilingual.tr(lang, "حد المنتج", "Seuil produit", "Item Min"),
                                selected = thresholdMode == LowStockThresholdMode.PRODUCT_DEFAULT,
                                onClick = { thresholdMode = LowStockThresholdMode.PRODUCT_DEFAULT },
                                testTag = "low_stock_threshold_chip_default"
                            )
                            ThresholdFilterChip(
                                label = Bilingual.tr(lang, "نفد (0)", "Rupture (0)", "Out (0)"),
                                selected = thresholdMode == LowStockThresholdMode.OUT_OF_STOCK,
                                onClick = { thresholdMode = LowStockThresholdMode.OUT_OF_STOCK },
                                testTag = "low_stock_threshold_chip_0"
                            )
                            ThresholdFilterChip(
                                label = "≤ 5",
                                selected = thresholdMode == LowStockThresholdMode.THRESHOLD_5,
                                onClick = { thresholdMode = LowStockThresholdMode.THRESHOLD_5 },
                                testTag = "low_stock_threshold_chip_5"
                            )
                            ThresholdFilterChip(
                                label = "≤ 10",
                                selected = thresholdMode == LowStockThresholdMode.THRESHOLD_10,
                                onClick = { thresholdMode = LowStockThresholdMode.THRESHOLD_10 },
                                testTag = "low_stock_threshold_chip_10"
                            )
                        }

                        if (activeSource.isEmpty()) {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = Bilingual.tr(
                                            lang,
                                            "لا توجد منتجات تحت هذا الحد حالياً. مخزونك في وضع آمن.",
                                            "Aucun produit sous ce seuil actuellement.",
                                            "No items below this threshold right now."
                                        ),
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF334155)
                                    )
                                }
                            }
                        } else {
                            val displayedItems = if (showAllItems) activeSource.take(12) else activeSource.take(4)

                            displayedItems.forEach { product ->
                                val effectiveThreshold = thresholdMode.customLimit ?: product.minStock
                                LowStockProductItemCard(
                                    product = product,
                                    effectiveThreshold = effectiveThreshold,
                                    lang = lang,
                                    onItemClick = onOpenInventoryLowStock,
                                    onQuickRestock = { productForQuickRestock = product }
                                )
                            }

                            if (activeSource.size > 4) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { showAllItems = !showAllItems },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text(
                                            text = if (showAllItems) {
                                                Bilingual.tr(lang, "عرض أقل", "Voir moins", "Show less")
                                            } else {
                                                Bilingual.tr(
                                                    lang,
                                                    "عرض المزيد (${activeSource.size - 4}+)",
                                                    "Voir plus (${activeSource.size - 4}+)",
                                                    "Show more (${activeSource.size - 4}+)"
                                                )
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF475569)
                                        )
                                    }

                                    TextButton(
                                        onClick = onOpenInventoryLowStock,
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text(
                                            text = Bilingual.tr(
                                                lang,
                                                "فتح كل النواقص في المخزن",
                                                "Ouvrir toutes les alertes",
                                                "Open all in Inventory"
                                            ),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    productForQuickRestock?.let { product ->
        LowStockQuickRestockDialog(
            product = product,
            lang = lang,
            viewModel = viewModel,
            onDismiss = { productForQuickRestock = null }
        )
    }
}
