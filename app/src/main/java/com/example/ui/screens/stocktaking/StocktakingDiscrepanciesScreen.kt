package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.data.InventoryEntity
import com.example.data.InventoryItemEntity
import com.example.data.ProductEntity
import com.example.domain.stocktaking.DiscrepancyFilter
import com.example.domain.stocktaking.DiscrepancySort
import com.example.domain.stocktaking.InventoryApprovalPreview
import com.example.domain.stocktaking.InventoryItemStatus
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils
import kotlin.math.abs

@Composable
internal fun StocktakingDiscrepanciesScreen(
    inventory: InventoryEntity,
    items: List<InventoryItemEntity>,
    liveProductsById: Map<Long, ProductEntity>,
    approvalPreview: InventoryApprovalPreview?,
    isManager: Boolean,
    isBusy: Boolean,
    currency: String,
    lang: String,
    onBack: () -> Unit,
    onRecordItemCount: (productId: Long, qty: Double, notes: String?) -> Unit,
    onClearItemCount: (productId: Long) -> Unit,
    onRequestApprovalPreview: () -> Unit,
    onConfirmApprove: (reason: String) -> Unit,
    onDismissApprovalPreview: () -> Unit,
    onConfirmCancelSession: (reason: String) -> Unit,
    onOpenReport: (() -> Unit)? = null
) {
    val status = InventoryStatus.fromCode(inventory.status)
    val isEditable = status.isEditable

    var filter by remember { mutableStateOf(DiscrepancyFilter.DISCREPANCIES_ONLY) }
    var sort by remember { mutableStateOf(DiscrepancySort.ABS_VALUE_DESC) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var itemToRecount by remember { mutableStateOf<InventoryItemEntity?>(null) }

    val summary = remember(items) {
        StocktakingCalculator.computeSessionSummary(
            items.map {
                StocktakingCalculator.ItemSummaryInput(
                    countedQuantity = it.countedQuantity,
                    expectedQuantity = it.effectiveExpectedQuantity,
                    unitCostCents = it.unitCostCents
                )
            }
        )
    }

    val displayedItems = remember(items, filter, sort, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val filtered = items.filter { item ->
            val st = InventoryItemStatus.fromCode(item.status)
            val matchFilter = when (filter) {
                DiscrepancyFilter.ALL -> true
                DiscrepancyFilter.DISCREPANCIES_ONLY -> st == InventoryItemStatus.SHORTAGE || st == InventoryItemStatus.SURPLUS
                DiscrepancyFilter.SHORTAGE -> st == InventoryItemStatus.SHORTAGE
                DiscrepancyFilter.SURPLUS -> st == InventoryItemStatus.SURPLUS
                DiscrepancyFilter.MATCHING -> st == InventoryItemStatus.MATCHING
                DiscrepancyFilter.UNCOUNTED -> st == InventoryItemStatus.UNCOUNTED
            }
            val matchSearch = q.isEmpty() ||
                item.productName.lowercase().contains(q) ||
                item.barcode.lowercase().contains(q) ||
                item.categoryName.lowercase().contains(q)
            matchFilter && matchSearch
        }

        when (sort) {
            DiscrepancySort.ABS_VALUE_DESC -> filtered.sortedByDescending { abs(it.discrepancyValueCents) }
            DiscrepancySort.DIFFERENCE_ASC -> filtered.sortedBy { it.difference }
            DiscrepancySort.DIFFERENCE_DESC -> filtered.sortedByDescending { it.difference }
            DiscrepancySort.NAME_ASC -> filtered.sortedBy { it.productName.lowercase() }
            DiscrepancySort.CATEGORY_ASC -> filtered.sortedWith(
                compareBy<InventoryItemEntity> { it.categoryName.lowercase() }.thenBy { it.productName.lowercase() }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StocktakingPalette.PageBg)
    ) {
        StocktakingHeaderBar(
            title = Bilingual.tr(lang, "مراجعة الفوارق والتسوية", "Écarts & Validation"),
            subtitle = "${inventory.reference} • ${inventoryStatusLabel(status, lang)}",
            onBack = onBack,
            backTestTag = "discrepancies_back_btn"
        ) {
            if (onOpenReport != null) {
                IconButton(
                    onClick = onOpenReport,
                    modifier = Modifier.size(48.dp).testTag("discrepancies_open_report_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = Bilingual.tr(lang, "تقرير الجرد", "Rapport d'inventaire"),
                        tint = Color(0xFF38BDF8)
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.size(48.dp).testTag("discrepancies_sort_btn")
                ) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    val sortOptions = listOf(
                        DiscrepancySort.ABS_VALUE_DESC to Bilingual.tr(lang, "الأعلى أثراً مالياً", "Plus grand impact financier"),
                        DiscrepancySort.DIFFERENCE_ASC to Bilingual.tr(lang, "الأكثر عجزاً أولاً", "Plus grands manquants"),
                        DiscrepancySort.DIFFERENCE_DESC to Bilingual.tr(lang, "الأكثر فائضاً أولاً", "Plus grands surplus"),
                        DiscrepancySort.CATEGORY_ASC to Bilingual.tr(lang, "حسب الفئة", "Par catégorie"),
                        DiscrepancySort.NAME_ASC to Bilingual.tr(lang, "الاسم (أ - ي)", "Nom (A - Z)")
                    )
                    sortOptions.forEach { (opt, label) ->
                        DropdownMenuItem(
                            text = { Text(label, fontWeight = if (sort == opt) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                sort = opt
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Financial Discrepancy Summary Card (Purchase Cost Only)
            item(key = "discrepancy_summary_card") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StocktakingPalette.CardBg,
                    border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = Bilingual.tr(lang, "الملخص المالي للفوارق (بسعر الشراء)", "Synthèse financière (au coût d'achat)"),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = StocktakingPalette.TextPrimary
                            )
                            Surface(
                                color = StocktakingPalette.IndigoSoft,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = Bilingual.tr(
                                        lang,
                                        "دقة ${summary.accuracyPercent.toInt()}%",
                                        "Précision ${summary.accuracyPercent.toInt()}%"
                                    ),
                                    color = StocktakingPalette.Indigo,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DiscrepancyStatTile(
                                title = Bilingual.tr(lang, "قيمة العجز", "Manquants"),
                                value = MoneyUtils.format(-summary.totalShortageValueCents, currency),
                                countLabel = "${summary.shortageItemsCount}",
                                bg = StocktakingPalette.RoseSoft,
                                accent = StocktakingPalette.Rose,
                                modifier = Modifier.weight(1f)
                            )
                            DiscrepancyStatTile(
                                title = Bilingual.tr(lang, "قيمة الفائض", "Surplus"),
                                value = MoneyUtils.format(summary.totalSurplusValueCents, currency),
                                countLabel = "${summary.surplusItemsCount}",
                                bg = StocktakingPalette.EmeraldSoft,
                                accent = StocktakingPalette.Emerald,
                                modifier = Modifier.weight(1f)
                            )
                            DiscrepancyStatTile(
                                title = Bilingual.tr(lang, "الصافي", "Net"),
                                value = MoneyUtils.format(summary.netDiscrepancyValueCents, currency),
                                countLabel = "${summary.discrepancyItemsCount}",
                                bg = StocktakingPalette.SelectedCardBg,
                                accent = if (summary.netDiscrepancyValueCents < 0L) StocktakingPalette.Rose else StocktakingPalette.AccentBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = Bilingual.tr(lang, "معدود: ${summary.countedItemsCount}/${summary.totalItemsCount}", "Comptés: ${summary.countedItemsCount}/${summary.totalItemsCount}"),
                                color = StocktakingPalette.TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = Bilingual.tr(lang, "غير معدود: ${summary.uncountedItemsCount}", "Non comptés: ${summary.uncountedItemsCount}"),
                                color = if (summary.uncountedItemsCount > 0) StocktakingPalette.Amber else StocktakingPalette.TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = Bilingual.tr(lang, "مطابق: ${summary.matchingItemsCount}", "Conformes: ${summary.matchingItemsCount}"),
                                color = StocktakingPalette.AccentSky,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 2. Search & Filter Row
            item(key = "discrepancy_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(Bilingual.tr(lang, "تصفية بالاسم أو الباركود...", "Filtrer par nom ou code-barres...")) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        colors = stocktakingTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val chips = listOf(
                            DiscrepancyFilter.DISCREPANCIES_ONLY to Bilingual.tr(lang, "الفوارق فقط (${summary.discrepancyItemsCount})", "Écarts (${summary.discrepancyItemsCount})"),
                            DiscrepancyFilter.SHORTAGE to Bilingual.tr(lang, "عجز (${summary.shortageItemsCount})", "Manquants (${summary.shortageItemsCount})"),
                            DiscrepancyFilter.SURPLUS to Bilingual.tr(lang, "فائض (${summary.surplusItemsCount})", "Surplus (${summary.surplusItemsCount})"),
                            DiscrepancyFilter.UNCOUNTED to Bilingual.tr(lang, "غير معدود (${summary.uncountedItemsCount})", "Non comptés (${summary.uncountedItemsCount})"),
                            DiscrepancyFilter.MATCHING to Bilingual.tr(lang, "مطابق (${summary.matchingItemsCount})", "Conformes (${summary.matchingItemsCount})"),
                            DiscrepancyFilter.ALL to Bilingual.tr(lang, "الكل (${summary.totalItemsCount})", "Tous (${summary.totalItemsCount})")
                        )
                        chips.forEach { (flt, label) ->
                            FilterChip(
                                selected = filter == flt,
                                onClick = { filter = flt },
                                label = { Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("discrepancies_filter_${flt.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // 3. Discrepancy Rows
            if (displayedItems.isEmpty()) {
                item(key = "empty_discrepancies") {
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
                            Icon(
                                imageVector = Icons.Filled.TaskAlt,
                                contentDescription = null,
                                tint = StocktakingPalette.Emerald,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = Bilingual.tr(lang, "لا توجد عناصر في هذا الفلتر", "Aucun élément dans ce filtre"),
                                color = StocktakingPalette.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(displayedItems, key = { it.id }) { item ->
                    val liveStock = liveProductsById[item.productId]?.currentStock ?: item.effectiveExpectedQuantity
                    DiscrepancyItemCard(
                        item = item,
                        liveSystemStock = liveStock,
                        isEditable = isEditable,
                        currency = currency,
                        lang = lang,
                        onRecount = { itemToRecount = item }
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }

        // 4. Bottom Manager Approval / RBAC Bar (only for editable sessions)
        if (isEditable) {
            Surface(
                color = StocktakingPalette.CardBg,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isManager) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            enabled = !isBusy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StocktakingPalette.Rose),
                            border = BorderStroke(1.dp, StocktakingPalette.Rose.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 50.dp)
                                .testTag("stocktaking_open_cancel_btn")
                        ) {
                            Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(Bilingual.tr(lang, "إلغاء", "Annuler"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = onRequestApprovalPreview,
                            enabled = !isBusy && summary.countedItemsCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.Emerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .testTag("stocktaking_open_approval_btn")
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = Bilingual.tr(lang, "اعتماد وتطبيق التسوية", "Valider & Ajuster Stock"),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StocktakingPalette.AmberSoft)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = StocktakingPalette.Amber)
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "اعتماد تسوية المخزون وإلغاء الجلسة مقتصران حصرياً على حساب المدير.",
                                "La validation des ajustements de stock est réservée exclusivement au gérant."
                            ),
                            color = StocktakingPalette.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            modifier = Modifier.testTag("stocktaking_manager_only_notice")
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    itemToRecount?.let { target ->
        val liveStock = liveProductsById[target.productId]?.currentStock ?: target.effectiveExpectedQuantity
        ExactQuantityCountDialog(
            item = target,
            liveSystemStock = liveStock,
            currency = currency,
            lang = lang,
            onDismiss = { itemToRecount = null },
            onSave = { qty, notes ->
                onRecordItemCount(target.productId, qty, notes)
                itemToRecount = null
            },
            onClearCount = {
                onClearItemCount(target.productId)
                itemToRecount = null
            }
        )
    }

    approvalPreview?.let { preview ->
        StocktakingApprovalDialog(
            preview = preview,
            currency = currency,
            isBusy = isBusy,
            lang = lang,
            onConfirmApprove = onConfirmApprove,
            onDismiss = onDismissApprovalPreview
        )
    }

    if (showCancelDialog) {
        StocktakingCancelDialog(
            inventoryReference = inventory.reference,
            isBusy = isBusy,
            lang = lang,
            onConfirmCancel = { reason ->
                showCancelDialog = false
                onConfirmCancelSession(reason)
            },
            onDismiss = { showCancelDialog = false }
        )
    }
}

@Composable
private fun DiscrepancyStatTile(
    title: String,
    value: String,
    countLabel: String,
    bg: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(text = "$title ($countLabel)", color = StocktakingPalette.TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DiscrepancyItemCard(
    item: InventoryItemEntity,
    liveSystemStock: Double,
    isEditable: Boolean,
    currency: String,
    lang: String,
    onRecount: () -> Unit
) {
    val st = InventoryItemStatus.fromCode(item.status)
    val baseline = item.effectiveExpectedQuantity
    val postCountDelta = StocktakingCalculator.normalizeQuantity(liveSystemStock - baseline)
    val hasConcurrent = item.countedQuantity != null && abs(postCountDelta) > StocktakingCalculator.QUANTITY_EPSILON
    val projectedStock = StocktakingCalculator.normalizeQuantity(liveSystemStock + item.difference)

    val accentColor = when (st) {
        InventoryItemStatus.SHORTAGE -> StocktakingPalette.Rose
        InventoryItemStatus.SURPLUS -> StocktakingPalette.Emerald
        InventoryItemStatus.MATCHING -> StocktakingPalette.AccentSky
        InventoryItemStatus.UNCOUNTED -> StocktakingPalette.TextMuted
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discrepancy_item_card_${item.productId}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = StocktakingPalette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.barcode} • ${item.categoryName} • ${Bilingual.tr(lang, "شراء", "Achat")}: ${MoneyUtils.format(item.unitCostCents, currency)}",
                        color = StocktakingPalette.TextMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StocktakingItemStatusBadge(status = st, lang = lang)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = Bilingual.tr(lang, "النظام / المعدود", "Système / Compté"),
                        color = StocktakingPalette.TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${StocktakingCalculator.formatQuantity(baseline)}  →  ${item.countedQuantity?.let { StocktakingCalculator.formatQuantity(it) } ?: "—"} ${item.unit}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = StocktakingPalette.TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (item.countedQuantity != null) {
                            "${StocktakingCalculator.formatSignedQuantity(item.difference)} ${item.unit}"
                        } else "—",
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (item.countedQuantity != null) MoneyUtils.format(item.discrepancyValueCents, currency) else "—",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            if (hasConcurrent) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StocktakingPalette.SelectedCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "المخزون الحي الآن: ${StocktakingCalculator.formatQuantity(liveSystemStock)} (حركة ${StocktakingCalculator.formatSignedQuantity(postCountDelta)}) ← الرصيد النهائي بعد التسوية: ${StocktakingCalculator.formatQuantity(projectedStock)}",
                            "Stock actuel : ${StocktakingCalculator.formatQuantity(liveSystemStock)} (mvt ${StocktakingCalculator.formatSignedQuantity(postCountDelta)}) → Stock final projeté : ${StocktakingCalculator.formatQuantity(projectedStock)}"
                        ),
                        color = StocktakingPalette.AccentBlue,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            if (isEditable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onRecount,
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("discrepancy_recount_btn_${item.productId}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Bilingual.tr(lang, "إعادة العدّ / تعديل", "Recompter / Modifier"), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}
