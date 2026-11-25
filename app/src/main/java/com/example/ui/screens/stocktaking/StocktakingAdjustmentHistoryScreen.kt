package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryAdjustmentEntity
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils
import kotlin.math.abs

private enum class AdjustmentDirectionFilter { ALL, SHORTAGE, SURPLUS }

@Composable
internal fun StocktakingAdjustmentHistoryScreen(
    adjustments: List<InventoryAdjustmentEntity>,
    currency: String,
    lang: String,
    subtitle: String? = null,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var directionFilter by remember { mutableStateOf(AdjustmentDirectionFilter.ALL) }

    val filtered = remember(adjustments, searchQuery, directionFilter) {
        val q = searchQuery.trim().lowercase()
        adjustments.filter { adj ->
            val dirOk = when (directionFilter) {
                AdjustmentDirectionFilter.ALL -> true
                AdjustmentDirectionFilter.SHORTAGE -> adj.difference < 0.0
                AdjustmentDirectionFilter.SURPLUS -> adj.difference > 0.0
            }
            val searchOk = q.isEmpty() ||
                adj.productName.lowercase().contains(q) ||
                adj.barcode.lowercase().contains(q) ||
                adj.approvedBy.lowercase().contains(q) ||
                adj.reason.lowercase().contains(q)
            dirOk && searchOk
        }
    }

    val totals = remember(filtered) {
        var shortage = 0L
        var surplus = 0L
        for (adj in filtered) {
            if (adj.financialImpactCents < 0L) shortage += abs(adj.financialImpactCents)
            else surplus += adj.financialImpactCents
        }
        Triple(shortage, surplus, surplus - shortage)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StocktakingPalette.PageBg)
    ) {
        StocktakingHeaderBar(
            title = Bilingual.tr(lang, "سجل تسويات المخزون", "Historique des ajustements"),
            subtitle = subtitle ?: Bilingual.tr(
                lang,
                "سجل تدقيق دائم غير قابل للتعديل (${adjustments.size} تسوية)",
                "Journal d'audit immuable (${adjustments.size} ajustements)"
            ),
            onBack = onBack,
            backTestTag = "adjustments_history_back_btn"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Financial Audit Summary
            item(key = "adjustments_summary") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StocktakingPalette.CardBg,
                    border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(Bilingual.tr(lang, "إجمالي العجز", "Total manquants"), color = StocktakingPalette.TextMuted, fontSize = 11.sp)
                            Text(MoneyUtils.format(-totals.first, currency), color = StocktakingPalette.Rose, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Bilingual.tr(lang, "إجمالي الفائض", "Total surplus"), color = StocktakingPalette.TextMuted, fontSize = 11.sp)
                            Text(MoneyUtils.format(totals.second, currency), color = StocktakingPalette.Emerald, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Bilingual.tr(lang, "الصافي (شراء)", "Impact net"), color = StocktakingPalette.TextMuted, fontSize = 11.sp)
                            val netColor = if (totals.third < 0L) StocktakingPalette.Rose else StocktakingPalette.AccentBlue
                            Text(MoneyUtils.format(totals.third, currency), color = netColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                        }
                    }
                }
            }

            // Search & Direction Filter
            item(key = "adjustments_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(Bilingual.tr(lang, "بحث بالمنتج، الباركود، المدير، السبب...", "Produit, code-barres, gérant, motif...")) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        colors = stocktakingTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = directionFilter == AdjustmentDirectionFilter.ALL,
                            onClick = { directionFilter = AdjustmentDirectionFilter.ALL },
                            label = { Text(Bilingual.tr(lang, "الكل (${adjustments.size})", "Tous (${adjustments.size})"), fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = directionFilter == AdjustmentDirectionFilter.SHORTAGE,
                            onClick = { directionFilter = AdjustmentDirectionFilter.SHORTAGE },
                            label = { Text(Bilingual.tr(lang, "عجز (-)", "Manquants (-)"), fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = directionFilter == AdjustmentDirectionFilter.SURPLUS,
                            onClick = { directionFilter = AdjustmentDirectionFilter.SURPLUS },
                            label = { Text(Bilingual.tr(lang, "فائض (+)", "Surplus (+)"), fontSize = 12.sp) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item(key = "empty_adjustments") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StocktakingPalette.CardBg,
                        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(40.dp))
                            Text(
                                text = Bilingual.tr(lang, "لا توجد تسويات مخزون مسجلة بعد", "Aucun ajustement d'inventaire enregistré"),
                                color = StocktakingPalette.TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { adj ->
                    AdjustmentAuditCard(adj = adj, currency = currency, lang = lang)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AdjustmentAuditCard(
    adj: InventoryAdjustmentEntity,
    currency: String,
    lang: String
) {
    val isShortage = adj.difference < 0.0
    val accentColor = if (isShortage) StocktakingPalette.Rose else StocktakingPalette.Emerald
    val badgeBg = if (isShortage) StocktakingPalette.RoseSoft else StocktakingPalette.EmeraldSoft

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("adjustment_card_${adj.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = adj.productName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = StocktakingPalette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${adj.barcode} • ${formatStocktakingShortDate(adj.createdAt)}",
                        color = StocktakingPalette.TextMuted,
                        fontSize = 11.5.sp
                    )
                }
                Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "${StocktakingCalculator.formatSignedQuantity(adj.difference)} ${adj.unit}",
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                color = StocktakingPalette.SurfaceSubtle,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "قبل: ${StocktakingCalculator.formatQuantity(adj.previousQuantity)} ← بعد: ${StocktakingCalculator.formatQuantity(adj.adjustedQuantity)}",
                            "Avant : ${StocktakingCalculator.formatQuantity(adj.previousQuantity)} → Après : ${StocktakingCalculator.formatQuantity(adj.adjustedQuantity)}"
                        ),
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = MoneyUtils.format(adj.financialImpactCents, currency),
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp
                    )
                }
            }

            if (abs(adj.concurrentDeltaAfterCount) > StocktakingCalculator.QUANTITY_EPSILON) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "شملت التسوية حركة متزامنة بعد العدّ: ${StocktakingCalculator.formatSignedQuantity(adj.concurrentDeltaAfterCount)} (المعدود: ${StocktakingCalculator.formatQuantity(adj.countedQuantity)} مقابل النظام وقت العدّ: ${StocktakingCalculator.formatQuantity(adj.systemStockAtCount)})",
                        "Mouvement post-comptage intégré : ${StocktakingCalculator.formatSignedQuantity(adj.concurrentDeltaAfterCount)} (Compté : ${StocktakingCalculator.formatQuantity(adj.countedQuantity)} vs Système : ${StocktakingCalculator.formatQuantity(adj.systemStockAtCount)})"
                    ),
                    color = StocktakingPalette.AccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = adj.reason,
                    color = StocktakingPalette.TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = StocktakingPalette.Emerald, modifier = Modifier.size(14.dp))
                    Text(
                        text = adj.approvedBy,
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }
}
