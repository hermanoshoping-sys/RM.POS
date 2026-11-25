package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryItemEntity
import com.example.data.ProductEntity
import com.example.domain.stocktaking.InventoryItemStatus
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils
import kotlin.math.abs

private val CNT_COL_NAME = 148.dp
private val CNT_COL_SYS = 58.dp
private val CNT_COL_COUNTED = 66.dp
private val CNT_COL_DIFF = 56.dp
private val CNT_COL_IMPACT = 82.dp
private val CNT_COL_STATUS = 68.dp
private val CNT_COL_QUICK = 114.dp

@Composable
internal fun CountingProductCard(
    item: InventoryItemEntity,
    liveSystemStock: Double,
    isHighlighted: Boolean,
    isEditable: Boolean,
    currency: String,
    lang: String,
    onOpenExactDialog: () -> Unit,
    onQuickDelta: (Double) -> Unit,
    onQuickMatchExpected: () -> Unit
) {
    val itemStatus = InventoryItemStatus.fromCode(item.status)
    val baseline = if (item.countedQuantity != null) item.effectiveExpectedQuantity else liveSystemStock
    val concurrentShift = StocktakingCalculator.normalizeQuantity(liveSystemStock - baseline)
    val hasLiveMovement = item.countedQuantity != null && abs(concurrentShift) > StocktakingCalculator.QUANTITY_EPSILON

    val statusAccent = when (itemStatus) {
        InventoryItemStatus.SHORTAGE -> StocktakingPalette.Rose
        InventoryItemStatus.SURPLUS -> StocktakingPalette.Emerald
        InventoryItemStatus.MATCHING -> StocktakingPalette.AccentSky
        InventoryItemStatus.UNCOUNTED -> StocktakingPalette.CardBorder
    }
    val diffColor = when (itemStatus) {
        InventoryItemStatus.SHORTAGE -> StocktakingPalette.Rose
        InventoryItemStatus.SURPLUS -> StocktakingPalette.Emerald
        InventoryItemStatus.MATCHING -> StocktakingPalette.AccentSky
        InventoryItemStatus.UNCOUNTED -> StocktakingPalette.TextMuted
    }
    val signedImpact = when {
        item.countedQuantity == null -> "—"
        item.discrepancyValueCents > 0L -> "+${MoneyUtils.format(item.discrepancyValueCents, currency)}"
        item.discrepancyValueCents < 0L -> MoneyUtils.format(item.discrepancyValueCents, currency)
        else -> MoneyUtils.format(0L, currency)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isHighlighted) StocktakingPalette.SelectedCardBg else StocktakingPalette.CardBg,
        border = BorderStroke(
            width = if (isHighlighted) 2.dp else 1.dp,
            color = if (isHighlighted) StocktakingPalette.AccentBlue else StocktakingPalette.CardBorder
        ),
        shadowElevation = if (isHighlighted) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("counting_item_card_${item.productId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left/Start Status Accent Stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (isHighlighted) StocktakingPalette.AccentBlue else statusAccent)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Top Row: Photo + Details + Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StocktakingProductThumbnail(
                        imageUri = item.imageUri,
                        contentDescription = item.productName,
                        size = 44.dp
                    )

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.productName,
                            color = StocktakingPalette.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val metaParts = buildList {
                            if (item.barcode.isNotBlank()) add(item.barcode)
                            if (item.sku.isNotBlank()) add("SKU:${item.sku}")
                            if (item.categoryName.isNotBlank()) add(item.categoryName)
                        }
                        if (metaParts.isNotEmpty()) {
                            Text(
                                text = metaParts.joinToString(" • "),
                                color = StocktakingPalette.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    StocktakingItemStatusBadge(status = itemStatus, lang = lang)
                }

                // Structured 4-Column Mini Ledger Strip
                Surface(
                    color = StocktakingPalette.SurfaceSubtle,
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, StocktakingPalette.GridLine),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniLedgerCell(
                            label = Bilingual.tr(lang, "النظام", "Système"),
                            value = StocktakingCalculator.formatQuantity(baseline),
                            valueColor = StocktakingPalette.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        VerticalDivider(thickness = 0.75.dp, color = StocktakingPalette.GridLine)
                        MiniLedgerCell(
                            label = Bilingual.tr(lang, "المجرود", "Compté"),
                            value = item.countedQuantity?.let { StocktakingCalculator.formatQuantity(it) } ?: "—",
                            valueColor = if (item.countedQuantity != null) StocktakingPalette.AccentBlue else StocktakingPalette.TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        VerticalDivider(thickness = 0.75.dp, color = StocktakingPalette.GridLine)
                        MiniLedgerCell(
                            label = Bilingual.tr(lang, "الفارق", "Écart"),
                            value = if (item.countedQuantity != null) StocktakingCalculator.formatSignedQuantity(item.difference) else "—",
                            valueColor = diffColor,
                            modifier = Modifier.weight(0.9f)
                        )
                        VerticalDivider(thickness = 0.75.dp, color = StocktakingPalette.GridLine)
                        MiniLedgerCell(
                            label = Bilingual.tr(lang, "الأثر", "Impact"),
                            value = signedImpact,
                            valueColor = diffColor,
                            modifier = Modifier.weight(1.25f)
                        )
                    }
                }

                if (hasLiveMovement) {
                    Surface(
                        color = StocktakingPalette.AmberSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null, tint = StocktakingPalette.Amber, modifier = Modifier.size(15.dp))
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "حركة متزامنة بعد العدّ (${StocktakingCalculator.formatSignedQuantity(concurrentShift)}) — محفوظة تلقائياً",
                                    "Mouvement après comptage (${StocktakingCalculator.formatSignedQuantity(concurrentShift)}) — préservé"
                                ),
                                color = StocktakingPalette.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Quick Physical Counting Controls (Auto-save, >= 48dp touch targets)
                if (isEditable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { onQuickDelta(-1.0) },
                            enabled = (item.countedQuantity ?: 0.0) > 0.0,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("counting_minus_btn_${item.productId}")
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "-1")
                        }

                        OutlinedButton(
                            onClick = onOpenExactDialog,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                width = 1.25.dp,
                                color = if (item.countedQuantity != null) StocktakingPalette.AccentBlue else StocktakingPalette.InputBorder
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (item.countedQuantity != null) StocktakingPalette.SelectedCardBg else Color.Transparent,
                                contentColor = if (item.countedQuantity != null) StocktakingPalette.AccentBlue else StocktakingPalette.TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("counting_edit_qty_btn_${item.productId}")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = item.countedQuantity?.let {
                                    "${StocktakingCalculator.formatQuantity(it)} ${item.unit}".trim()
                                } ?: Bilingual.tr(lang, "إدخال الكمية", "Saisir qté"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { onQuickDelta(1.0) },
                            shape = RoundedCornerShape(10.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = StocktakingPalette.AccentBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("counting_plus_btn_${item.productId}")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "+1")
                        }

                        FilledTonalButton(
                            onClick = onQuickMatchExpected,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = StocktakingPalette.EmeraldSoft,
                                contentColor = StocktakingPalette.Emerald
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("counting_match_btn_${item.productId}")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = Bilingual.tr(lang, "مطابقة النظام", "Conforme"), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniLedgerCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = StocktakingPalette.TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun CountingLedgerTableView(
    items: List<InventoryItemEntity>,
    liveProductsById: Map<Long, ProductEntity>,
    highlightedProductId: Long?,
    isEditable: Boolean,
    listState: LazyListState,
    currency: String,
    lang: String,
    onOpenExactDialog: (InventoryItemEntity) -> Unit,
    onQuickDelta: (InventoryItemEntity, Double) -> Unit,
    onQuickMatchExpected: (InventoryItemEntity, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val hScroll = rememberScrollState()
    val baseWidth = CNT_COL_NAME + CNT_COL_SYS + CNT_COL_COUNTED + CNT_COL_DIFF +
        CNT_COL_IMPACT + CNT_COL_STATUS + if (isEditable) CNT_COL_QUICK else 0.dp

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.GridLine),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tableWidth = maxOf(maxWidth, baseWidth)
            val effectiveNameWidth = CNT_COL_NAME + (tableWidth - baseWidth).coerceAtLeast(0.dp)

            Column(modifier = Modifier.fillMaxSize()) {
                // Sticky Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(hScroll)
                        .background(StocktakingPalette.TableHeaderBg)
                        .height(IntrinsicSize.Min)
                ) {
                    CountingTableHeaderCell(Bilingual.tr(lang, "اسم المنتج", "Produit"), effectiveNameWidth, TextAlign.Start)
                    CountingTableHeaderCell(Bilingual.tr(lang, "النظام", "Système"), CNT_COL_SYS)
                    CountingTableHeaderCell(Bilingual.tr(lang, "المجرود", "Compté"), CNT_COL_COUNTED)
                    CountingTableHeaderCell(Bilingual.tr(lang, "الفارق", "Écart"), CNT_COL_DIFF)
                    CountingTableHeaderCell(Bilingual.tr(lang, "الأثر", "Impact"), CNT_COL_IMPACT)
                    CountingTableHeaderCell(Bilingual.tr(lang, "الحالة", "Statut"), CNT_COL_STATUS, showEndDivider = isEditable)
                    if (isEditable) {
                        CountingTableHeaderCell(Bilingual.tr(lang, "سريع", "Rapide"), CNT_COL_QUICK, showEndDivider = false)
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = StocktakingPalette.GridLine)

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("counting_table_list")
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                        val liveStock = liveProductsById[item.productId]?.currentStock ?: item.effectiveExpectedQuantity
                        val baseline = if (item.countedQuantity != null) item.effectiveExpectedQuantity else liveStock
                        val st = InventoryItemStatus.fromCode(item.status)
                        val isHi = highlightedProductId == item.productId
                        val rowBg = when {
                            isHi -> StocktakingPalette.SelectedCardBg
                            index % 2 == 0 -> StocktakingPalette.CardBg
                            else -> StocktakingPalette.ZebraRowBg
                        }
                        val diffColor = when (st) {
                            InventoryItemStatus.SHORTAGE -> StocktakingPalette.Rose
                            InventoryItemStatus.SURPLUS -> StocktakingPalette.Emerald
                            InventoryItemStatus.MATCHING -> StocktakingPalette.AccentSky
                            InventoryItemStatus.UNCOUNTED -> StocktakingPalette.TextMuted
                        }
                        val signedImpact = when {
                            item.countedQuantity == null -> "—"
                            item.discrepancyValueCents > 0L -> "+${MoneyUtils.format(item.discrepancyValueCents, currency)}"
                            item.discrepancyValueCents < 0L -> MoneyUtils.format(item.discrepancyValueCents, currency)
                            else -> MoneyUtils.format(0L, currency)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("counting_item_card_${item.productId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(hScroll)
                                    .background(rowBg)
                                    .clickable(enabled = isEditable) { onOpenExactDialog(item) }
                                    .height(IntrinsicSize.Min)
                                    .defaultMinSize(minHeight = 46.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CountingTableCell(effectiveNameWidth, Alignment.CenterStart) {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = item.productName,
                                            color = StocktakingPalette.TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.barcode.isNotBlank()) {
                                            Text(
                                                text = item.barcode,
                                                color = StocktakingPalette.TextMuted,
                                                fontSize = 9.5.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                CountingTableCell(CNT_COL_SYS) {
                                    Text(
                                        text = StocktakingCalculator.formatQuantity(baseline),
                                        color = StocktakingPalette.TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                CountingTableCell(CNT_COL_COUNTED) {
                                    Surface(
                                        color = if (item.countedQuantity != null) StocktakingPalette.SelectedCardBg else StocktakingPalette.SurfaceSubtle,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (item.countedQuantity != null) StocktakingPalette.AccentBlue else StocktakingPalette.InputBorder
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(enabled = isEditable) { onOpenExactDialog(item) }
                                            .testTag("counting_edit_qty_btn_${item.productId}")
                                    ) {
                                        Text(
                                            text = item.countedQuantity?.let { StocktakingCalculator.formatQuantity(it) } ?: "—",
                                            color = if (item.countedQuantity != null) StocktakingPalette.AccentBlue else StocktakingPalette.TextMuted,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                CountingTableCell(CNT_COL_DIFF) {
                                    Text(
                                        text = if (item.countedQuantity != null) StocktakingCalculator.formatSignedQuantity(item.difference) else "—",
                                        color = diffColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                CountingTableCell(CNT_COL_IMPACT) {
                                    Text(
                                        text = signedImpact,
                                        color = diffColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                CountingTableCell(CNT_COL_STATUS, showEndDivider = isEditable) {
                                    StocktakingItemStatusBadge(status = st, lang = lang)
                                }
                                if (isEditable) {
                                    CountingTableCell(CNT_COL_QUICK, showEndDivider = false) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            IconButton(
                                                onClick = { onQuickDelta(item, -1.0) },
                                                enabled = (item.countedQuantity ?: 0.0) > 0.0,
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .testTag("counting_minus_btn_${item.productId}")
                                            ) {
                                                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "-1", modifier = Modifier.size(19.dp))
                                            }
                                            IconButton(
                                                onClick = { onQuickDelta(item, 1.0) },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .testTag("counting_plus_btn_${item.productId}")
                                            ) {
                                                Icon(Icons.Filled.AddCircleOutline, contentDescription = "+1", tint = StocktakingPalette.AccentBlue, modifier = Modifier.size(19.dp))
                                            }
                                            IconButton(
                                                onClick = { onQuickMatchExpected(item, liveStock) },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .testTag("counting_match_btn_${item.productId}")
                                            ) {
                                                Icon(Icons.Filled.CheckCircleOutline, contentDescription = "Match", tint = StocktakingPalette.Emerald, modifier = Modifier.size(19.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(thickness = 0.75.dp, color = StocktakingPalette.GridLine)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountingTableHeaderCell(
    text: String,
    width: Dp,
    align: TextAlign = TextAlign.Center,
    showEndDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp, vertical = 9.dp)
        ) {
            Text(
                text = text,
                color = StocktakingPalette.TableHeaderText,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showEndDivider) {
            VerticalDivider(thickness = 0.75.dp, color = Color.White.copy(alpha = 0.18f))
        }
    }
}

@Composable
private fun CountingTableCell(
    width: Dp,
    alignment: Alignment = Alignment.Center,
    showEndDivider: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = alignment,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            content = content
        )
        if (showEndDivider) {
            VerticalDivider(thickness = 0.75.dp, color = StocktakingPalette.GridLine)
        }
    }
}
