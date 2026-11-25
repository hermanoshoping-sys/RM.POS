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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryEntity
import com.example.data.InventoryItemEntity
import com.example.domain.stocktaking.CategoryDiscrepancyReportRow
import com.example.domain.stocktaking.DiscrepancyFilter
import com.example.domain.stocktaking.InventoryComparisonSummary
import com.example.domain.stocktaking.InventoryItemStatus
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.domain.stocktaking.StocktakingReportBuilder
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun StocktakingReportScreen(
    inventory: InventoryEntity,
    items: List<InventoryItemEntity>,
    historicalComparison: InventoryComparisonSummary?,
    currency: String,
    lang: String,
    shopName: String,
    onBack: () -> Unit,
    onOpenAdjustments: (() -> Unit)? = null,
    onEditSession: (() -> Unit)? = null,
    onDeleteSession: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val status = InventoryStatus.fromCode(inventory.status)
    val type = InventoryType.fromCode(inventory.type)

    var pdfDiscrepanciesOnly by remember { mutableStateOf(false) }
    var itemFilter by remember { mutableStateOf(DiscrepancyFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

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

    val uncategorizedLabel = Bilingual.tr(lang, "غير مصنف", "Sans catégorie")
    val categoryBreakdown = remember(items, uncategorizedLabel) {
        StocktakingReportBuilder.buildCategoryBreakdown(items, uncategorizedLabel)
    }
    val maxCategoryImpact = remember(categoryBreakdown) {
        categoryBreakdown.maxOfOrNull { maxOf(it.shortageValueCents, it.surplusValueCents, abs(it.netDiscrepancyValueCents)) }
            ?.coerceAtLeast(1L) ?: 1L
    }

    val filteredItems = remember(items, itemFilter, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        items.filter { item ->
            val st = InventoryItemStatus.fromCode(item.status)
            val filterMatch = when (itemFilter) {
                DiscrepancyFilter.ALL -> true
                DiscrepancyFilter.DISCREPANCIES_ONLY -> st == InventoryItemStatus.SHORTAGE || st == InventoryItemStatus.SURPLUS
                DiscrepancyFilter.SHORTAGE -> st == InventoryItemStatus.SHORTAGE
                DiscrepancyFilter.SURPLUS -> st == InventoryItemStatus.SURPLUS
                DiscrepancyFilter.MATCHING -> st == InventoryItemStatus.MATCHING
                DiscrepancyFilter.UNCOUNTED -> st == InventoryItemStatus.UNCOUNTED
            }
            val queryMatch = q.isEmpty() ||
                item.productName.lowercase(Locale.ROOT).contains(q) ||
                item.barcode.lowercase(Locale.ROOT).contains(q) ||
                item.categoryName.lowercase(Locale.ROOT).contains(q)
            filterMatch && queryMatch
        }.sortedWith(
            compareByDescending<InventoryItemEntity> { abs(it.discrepancyValueCents) }
                .thenBy { it.productName.lowercase(Locale.ROOT) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StocktakingPalette.PageBg)
    ) {
        StocktakingHeaderBar(
            title = Bilingual.tr(lang, "تقرير الجرد الكامل", "Rapport d'inventaire"),
            subtitle = "${inventory.reference} • ${inventoryStatusLabel(status, lang)}",
            onBack = onBack,
            backTestTag = "stocktaking_report_back_btn"
        ) {
            IconButton(
                onClick = {
                    StocktakingReportExporter.printReportPdf(
                        context = context,
                        inventory = inventory,
                        items = items,
                        currency = currency,
                        lang = lang,
                        shopName = shopName,
                        discrepanciesOnly = pdfDiscrepanciesOnly
                    )
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("stocktaking_report_header_print_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.Print,
                    contentDescription = Bilingual.tr(lang, "طباعة التقرير", "Imprimer le rapport"),
                    tint = Color.White
                )
            }
            if (onEditSession != null) {
                IconButton(
                    onClick = onEditSession,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("stocktaking_report_edit_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = Bilingual.tr(lang, "تعديل الجرد", "Modifier l'inventaire"),
                        tint = Color.White
                    )
                }
            }
            if (onDeleteSession != null) {
                IconButton(
                    onClick = onDeleteSession,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("stocktaking_report_delete_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = Bilingual.tr(lang, "حذف الجرد", "Supprimer l'inventaire"),
                        tint = Color(0xFFFCA5A5)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Export & Print Action Card
            item(key = "export_actions") {
                ReportExportToolbarCard(
                    status = status,
                    lang = lang,
                    pdfDiscrepanciesOnly = pdfDiscrepanciesOnly,
                    onTogglePdfDiscrepanciesOnly = { pdfDiscrepanciesOnly = it },
                    onExportPdf = {
                        StocktakingReportExporter.exportReportPdf(
                            context = context,
                            inventory = inventory,
                            items = items,
                            currency = currency,
                            lang = lang,
                            shopName = shopName,
                            discrepanciesOnly = pdfDiscrepanciesOnly
                        )
                    },
                    onPrintPdf = {
                        StocktakingReportExporter.printReportPdf(
                            context = context,
                            inventory = inventory,
                            items = items,
                            currency = currency,
                            lang = lang,
                            shopName = shopName,
                            discrepanciesOnly = pdfDiscrepanciesOnly
                        )
                    },
                    onExportCsv = {
                        StocktakingReportExporter.exportReportCsv(
                            context = context,
                            inventory = inventory,
                            items = items,
                            currency = currency,
                            lang = lang,
                            shopName = shopName
                        )
                    },
                    onExportExcel = {
                        StocktakingReportExporter.exportReportXlsx(
                            context = context,
                            inventory = inventory,
                            items = items,
                            currency = currency,
                            lang = lang,
                            shopName = shopName,
                            cartOnly = false
                        )
                    },
                    onOpenAdjustments = if (status == InventoryStatus.COMPLETED) onOpenAdjustments else null
                )
            }

            // 2. Session Metadata Header Card
            item(key = "session_meta") {
                ReportSessionMetadataCard(
                    inventory = inventory,
                    status = status,
                    type = type,
                    lang = lang
                )
            }

            // 3. Executive Financial & Accuracy KPI Grid
            item(key = "executive_kpis") {
                ReportExecutiveKpiSection(
                    summary = summary,
                    currency = currency,
                    lang = lang
                )
            }

            // 4. Historical Comparison vs Previous Completed Session
            if (historicalComparison != null && historicalComparison.previousInventory != null) {
                item(key = "historical_comparison") {
                    HistoricalComparisonCard(
                        comparison = historicalComparison,
                        currency = currency,
                        lang = lang
                    )
                }
            }

            // 5. Category Discrepancy Breakdown
            if (categoryBreakdown.isNotEmpty()) {
                item(key = "category_breakdown_header") {
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "توزيع الفوارق حسب الفئة (${categoryBreakdown.size})",
                            "Répartition des écarts par catégorie (${categoryBreakdown.size})"
                        ),
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
                items(categoryBreakdown, key = { "cat_${it.categoryName}" }) { row ->
                    CategoryBreakdownReportCard(
                        row = row,
                        maxImpactCents = maxCategoryImpact,
                        currency = currency,
                        lang = lang
                    )
                }
            }

            // 6. Itemized Report Table Header + Search + Filters
            item(key = "items_filter_bar") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "تفاصيل المنتجات (${filteredItems.size} / ${items.size})",
                            "Détail des produits (${filteredItems.size} / ${items.size})"
                        ),
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                Bilingual.tr(lang, "بحث بالاسم أو الباركود أو الفئة...", "Rechercher produit, code-barres, catégorie..."),
                                fontSize = 13.sp
                            )
                        },
                        colors = stocktakingTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stocktaking_report_search_input")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val chips = listOf(
                            DiscrepancyFilter.ALL to Bilingual.tr(lang, "الكل (${items.size})", "Tous (${items.size})"),
                            DiscrepancyFilter.DISCREPANCIES_ONLY to Bilingual.tr(lang, "الفوارق (${summary.discrepancyItemsCount})", "Écarts (${summary.discrepancyItemsCount})"),
                            DiscrepancyFilter.SHORTAGE to Bilingual.tr(lang, "عجز (${summary.shortageItemsCount})", "Manquants (${summary.shortageItemsCount})"),
                            DiscrepancyFilter.SURPLUS to Bilingual.tr(lang, "فائض (${summary.surplusItemsCount})", "Surplus (${summary.surplusItemsCount})"),
                            DiscrepancyFilter.MATCHING to Bilingual.tr(lang, "مطابق (${summary.matchingItemsCount})", "Conformes (${summary.matchingItemsCount})"),
                            DiscrepancyFilter.UNCOUNTED to Bilingual.tr(lang, "غير معدود (${summary.uncountedItemsCount})", "Non comptés (${summary.uncountedItemsCount})")
                        )
                        chips.forEach { (flt, label) ->
                            FilterChip(
                                selected = itemFilter == flt,
                                onClick = { itemFilter = flt },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("report_filter_${flt.name.lowercase(Locale.ROOT)}")
                            )
                        }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item(key = "report_empty_items") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StocktakingPalette.CardBg,
                        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = Bilingual.tr(lang, "لا توجد منتجات مطابقة لهذا الفلتر", "Aucun produit correspondant à ce filtre"),
                            color = StocktakingPalette.TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(filteredItems, key = { "rep_item_${it.id}" }) { item ->
                    ReportLineItemCard(item = item, currency = currency, lang = lang)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ReportExportToolbarCard(
    status: InventoryStatus,
    lang: String,
    pdfDiscrepanciesOnly: Boolean,
    onTogglePdfDiscrepanciesOnly: (Boolean) -> Unit,
    onExportPdf: () -> Unit,
    onPrintPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onExportExcel: () -> Unit,
    onOpenAdjustments: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExportPdf,
                    colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.AccentBlue),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("stocktaking_report_export_pdf_btn")
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                }

                Button(
                    onClick = onExportExcel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C41)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("stocktaking_report_export_excel_btn")
                ) {
                    Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Excel", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onPrintPdf,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("stocktaking_report_print_btn")
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        Bilingual.tr(lang, "طباعة", "Imprimer"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .heightIn(min = 48.dp)
                        .testTag("stocktaking_report_export_csv_btn")
                ) {
                    Text("CSV", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = pdfDiscrepanciesOnly,
                        onCheckedChange = onTogglePdfDiscrepanciesOnly,
                        modifier = Modifier.testTag("stocktaking_report_pdf_discrepancies_switch")
                    )
                    Text(
                        text = Bilingual.tr(lang, "تصدير الفوارق فقط في PDF", "PDF : écarts uniquement"),
                        color = StocktakingPalette.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (status == InventoryStatus.COMPLETED && onOpenAdjustments != null) {
                    TextButton(
                        onClick = onOpenAdjustments,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("stocktaking_report_adjustments_btn")
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            Bilingual.tr(lang, "سجل التسويات", "Ajustements"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSessionMetadataCard(
    inventory: InventoryEntity,
    status: InventoryStatus,
    type: InventoryType,
    lang: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = inventory.title.ifBlank { inventory.reference },
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp
                    )
                    Text(
                        text = "${inventory.reference} • ${inventoryTypeLabel(type, lang)}",
                        color = StocktakingPalette.TextMuted,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                StocktakingStatusBadge(status = status, lang = lang)
            }

            HorizontalDivider(color = StocktakingPalette.CardBorder)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetadataItem(
                    label = Bilingual.tr(lang, "تاريخ الإنشاء", "Créé le"),
                    value = formatStocktakingDate(inventory.createdAt, lang),
                    modifier = Modifier.weight(1f)
                )
                MetadataItem(
                    label = Bilingual.tr(lang, "تاريخ الاعتماد", "Clôturé le"),
                    value = formatStocktakingDate(inventory.completedAt, lang),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetadataItem(
                    label = Bilingual.tr(lang, "المسؤول عن الجرد", "Responsable"),
                    value = inventory.createdBy.ifBlank { "—" },
                    modifier = Modifier.weight(1f)
                )
                MetadataItem(
                    label = Bilingual.tr(lang, "اعتماد المدير", "Validé par"),
                    value = inventory.approvedBy?.ifBlank { "—" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
            if (inventory.scopeSummary.isNotBlank()) {
                MetadataItem(
                    label = Bilingual.tr(lang, "نطاق الجرد", "Périmètre"),
                    value = inventory.scopeSummary
                )
            }
            if (inventory.notes.isNotBlank()) {
                MetadataItem(
                    label = Bilingual.tr(lang, "ملاحظات", "Notes"),
                    value = inventory.notes
                )
            }
        }
    }
}

@Composable
private fun MetadataItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = StocktakingPalette.TextMuted, fontSize = 11.sp)
        Text(
            text = value,
            color = StocktakingPalette.TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReportExecutiveKpiSection(
    summary: StocktakingCalculator.InventorySessionSummary,
    currency: String,
    lang: String
) {
    val accuracyStr = String.format(Locale.US, "%.1f%%", summary.accuracyPercent)
    val netColor = when {
        summary.netDiscrepancyValueCents < 0L -> StocktakingPalette.Rose
        summary.netDiscrepancyValueCents > 0L -> StocktakingPalette.Emerald
        else -> StocktakingPalette.AccentSky
    }
    val signedNetStr = if (summary.netDiscrepancyValueCents > 0L) {
        "+${MoneyUtils.format(summary.netDiscrepancyValueCents, currency)}"
    } else if (summary.netDiscrepancyValueCents < 0L) {
        "-${MoneyUtils.format(abs(summary.netDiscrepancyValueCents), currency)}"
    } else {
        MoneyUtils.format(0L, currency)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StocktakingKpiCard(
                label = Bilingual.tr(lang, "نسبة الدقة", "Précision Stock"),
                value = accuracyStr,
                subtitle = Bilingual.tr(
                    lang,
                    "مطابق: ${summary.matchingItemsCount} / ${summary.countedItemsCount}",
                    "Conformes : ${summary.matchingItemsCount} / ${summary.countedItemsCount}"
                ),
                icon = Icons.Filled.Verified,
                accentColor = StocktakingPalette.AccentBlue,
                modifier = Modifier.weight(1f)
            )
            StocktakingKpiCard(
                label = Bilingual.tr(lang, "تغطية العدّ", "Couverture"),
                value = "${summary.countedItemsCount} / ${summary.totalItemsCount}",
                subtitle = Bilingual.tr(
                    lang,
                    "غير معدود: ${summary.uncountedItemsCount}",
                    "Non comptés : ${summary.uncountedItemsCount}"
                ),
                icon = Icons.Filled.Checklist,
                accentColor = StocktakingPalette.Indigo,
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StocktakingKpiCard(
                label = Bilingual.tr(lang, "قيمة العجز (شراء)", "Manquants (Achat)"),
                value = MoneyUtils.format(summary.totalShortageValueCents, currency),
                subtitle = Bilingual.tr(
                    lang,
                    "${summary.shortageItemsCount} منتج • -${StocktakingCalculator.formatQuantity(summary.totalShortageQuantity)}",
                    "${summary.shortageItemsCount} art. • -${StocktakingCalculator.formatQuantity(summary.totalShortageQuantity)}"
                ),
                icon = Icons.Filled.TrendingDown,
                accentColor = StocktakingPalette.Rose,
                modifier = Modifier.weight(1f)
            )
            StocktakingKpiCard(
                label = Bilingual.tr(lang, "قيمة الفائض (شراء)", "Surplus (Achat)"),
                value = MoneyUtils.format(summary.totalSurplusValueCents, currency),
                subtitle = Bilingual.tr(
                    lang,
                    "${summary.surplusItemsCount} منتج • +${StocktakingCalculator.formatQuantity(summary.totalSurplusQuantity)}",
                    "${summary.surplusItemsCount} art. • +${StocktakingCalculator.formatQuantity(summary.totalSurplusQuantity)}"
                ),
                icon = Icons.Filled.TrendingUp,
                accentColor = StocktakingPalette.Emerald,
                modifier = Modifier.weight(1f)
            )
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = StocktakingPalette.CardBg,
            border = BorderStroke(1.5.dp, netColor.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Bilingual.tr(lang, "صافي الأثر المالي (بسعر الشراء)", "Impact financier net (coût d'achat)"),
                        color = StocktakingPalette.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "${summary.discrepancyItemsCount} منتج بفارق إجمالي",
                            "${summary.discrepancyItemsCount} produit(s) en écart"
                        ),
                        color = StocktakingPalette.TextMuted,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = signedNetStr,
                    color = netColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun HistoricalComparisonCard(
    comparison: InventoryComparisonSummary,
    currency: String,
    lang: String
) {
    val prev = comparison.previousInventory ?: return
    val prevAcc = comparison.previousAccuracyPercent ?: 100f
    val accDelta = comparison.currentAccuracyPercent - prevAcc
    val accColor = if (accDelta >= 0f) StocktakingPalette.Emerald else StocktakingPalette.Rose
    val netDeltaColor = if (comparison.netValueDeltaCents >= 0L) StocktakingPalette.Emerald else StocktakingPalette.Rose

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stocktaking_historical_comparison_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.CompareArrows, contentDescription = null, tint = StocktakingPalette.Indigo, modifier = Modifier.size(20.dp))
                Column {
                    Text(
                        text = Bilingual.tr(lang, "مقارنة مع الجرد المكتمل السابق", "Comparaison avec l'inventaire précédent"),
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = "${prev.reference} • ${formatStocktakingShortDate(prev.completedAt ?: prev.createdAt)}",
                        color = StocktakingPalette.TextMuted,
                        fontSize = 11.5.sp
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(Bilingual.tr(lang, "تغيّر الدقة", "Delta précision"), color = StocktakingPalette.TextMuted, fontSize = 11.sp)
                    Text(
                        text = String.format(Locale.US, "%+.1f%%", accDelta),
                        color = accColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(Bilingual.tr(lang, "تغيّر عدد الفوارق", "Delta écarts"), color = StocktakingPalette.TextMuted, fontSize = 11.sp)
                    Text(
                        text = String.format(Locale.US, "%+d", comparison.discrepancyCountDelta),
                        color = if (comparison.discrepancyCountDelta <= 0) StocktakingPalette.Emerald else StocktakingPalette.Rose,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
                Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.End) {
                    Text(Bilingual.tr(lang, "تغيّر الصافي", "Delta net"), color = StocktakingPalette.TextMuted, fontSize = 11.sp)
                    val prefix = if (comparison.netValueDeltaCents > 0L) "+" else if (comparison.netValueDeltaCents < 0L) "-" else ""
                    Text(
                        text = "$prefix${MoneyUtils.format(abs(comparison.netValueDeltaCents), currency)}",
                        color = netDeltaColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownReportCard(
    row: CategoryDiscrepancyReportRow,
    maxImpactCents: Long,
    currency: String,
    lang: String
) {
    val netColor = when {
        row.netDiscrepancyValueCents < 0L -> StocktakingPalette.Rose
        row.netDiscrepancyValueCents > 0L -> StocktakingPalette.Emerald
        else -> StocktakingPalette.AccentSky
    }
    val impactRatio = (abs(row.netDiscrepancyValueCents).toFloat() / maxImpactCents.toFloat()).coerceIn(0f, 1f)
    val signedNet = when {
        row.netDiscrepancyValueCents > 0L -> "+${MoneyUtils.format(row.netDiscrepancyValueCents, currency)}"
        row.netDiscrepancyValueCents < 0L -> "-${MoneyUtils.format(abs(row.netDiscrepancyValueCents), currency)}"
        else -> MoneyUtils.format(0L, currency)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        modifier = Modifier.fillMaxWidth()
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
                Text(
                    text = row.categoryName,
                    color = StocktakingPalette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = signedNet,
                    color = netColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp
                )
            }
            Text(
                text = Bilingual.tr(
                    lang,
                    "معدود: ${row.countedItemsCount}/${row.totalItemsCount} • مطابق: ${row.matchingItemsCount} • عجز: ${row.shortageItemsCount} • فائض: ${row.surplusItemsCount}",
                    "Comptés : ${row.countedItemsCount}/${row.totalItemsCount} • Conformes : ${row.matchingItemsCount} • Manquants : ${row.shortageItemsCount} • Surplus : ${row.surplusItemsCount}"
                ),
                color = StocktakingPalette.TextMuted,
                fontSize = 11.5.sp
            )
            LinearProgressIndicator(
                progress = { impactRatio },
                color = netColor,
                trackColor = StocktakingPalette.SlateSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun ReportLineItemCard(
    item: InventoryItemEntity,
    currency: String,
    lang: String
) {
    val st = InventoryItemStatus.fromCode(item.status)
    val impactColor = when (st) {
        InventoryItemStatus.SHORTAGE -> StocktakingPalette.Rose
        InventoryItemStatus.SURPLUS -> StocktakingPalette.Emerald
        InventoryItemStatus.MATCHING -> StocktakingPalette.AccentSky
        InventoryItemStatus.UNCOUNTED -> StocktakingPalette.TextMuted
    }
    val impactText = if (item.countedQuantity == null) {
        "—"
    } else if (item.discrepancyValueCents > 0L) {
        "+${MoneyUtils.format(item.discrepancyValueCents, currency)}"
    } else if (item.discrepancyValueCents < 0L) {
        "-${MoneyUtils.format(abs(item.discrepancyValueCents), currency)}"
    } else {
        MoneyUtils.format(0L, currency)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        modifier = Modifier.fillMaxWidth()
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
                        text = item.productName,
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = buildString {
                        if (item.categoryName.isNotBlank()) append(item.categoryName)
                        if (item.barcode.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(item.barcode)
                        }
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            color = StocktakingPalette.TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StocktakingItemStatusBadge(status = st, lang = lang)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "نظامي: ${StocktakingCalculator.formatQuantity(item.effectiveExpectedQuantity)} | فعلي: ${item.countedQuantity?.let { StocktakingCalculator.formatQuantity(it) } ?: "—"} | فارق: ${if (item.countedQuantity != null) StocktakingCalculator.formatSignedQuantity(item.difference) else "—"}",
                        "Théor. : ${StocktakingCalculator.formatQuantity(item.effectiveExpectedQuantity)} | Phys. : ${item.countedQuantity?.let { StocktakingCalculator.formatQuantity(it) } ?: "—"} | Écart : ${if (item.countedQuantity != null) StocktakingCalculator.formatSignedQuantity(item.difference) else "—"}"
                    ),
                    color = StocktakingPalette.TextSecondary,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = impactText,
                    color = impactColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.5.sp
                )
            }
        }
    }
}
