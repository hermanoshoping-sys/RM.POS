package com.example.domain.stocktaking

import com.example.data.InventoryEntity
import com.example.data.InventoryItemEntity
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Aggregated discrepancy metrics for a single product category inside an inventory session.
 * All monetary fields are strictly calculated at purchase cost (`unitCostCents`) in exact integer cents.
 */
data class CategoryDiscrepancyReportRow(
    val categoryName: String,
    val totalItemsCount: Int,
    val countedItemsCount: Int,
    val matchingItemsCount: Int,
    val shortageItemsCount: Int,
    val surplusItemsCount: Int,
    val shortageValueCents: Long,
    val surplusValueCents: Long,
    val netDiscrepancyValueCents: Long
)

/**
 * Multi-session historical analytics across all recorded inventory sessions.
 */
data class HistoryAnalyticsSummary(
    val totalSessions: Int = 0,
    val completedSessions: Int = 0,
    val activeSessions: Int = 0,
    val cancelledSessions: Int = 0,
    val averageAccuracyPercent: Float = 100f,
    val totalItemsAudited: Int = 0,
    val cumulativeShortageValueCents: Long = 0L,
    val cumulativeSurplusValueCents: Long = 0L,
    val cumulativeNetDiscrepancyCents: Long = 0L
)

/**
 * Structured table payload consumed by the A4 PDF report generator and system printer.
 */
data class StocktakingPdfReportPayload(
    val docTitle: String,
    val subtitle: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val summaryLines: List<Pair<String, String>>
)

/**
 * Pure deterministic report aggregator and serializer (PDF table payload + UTF-8 BOM CSV)
 * for the RM.POS Stocktaking / Inventory Management module.
 */
object StocktakingReportBuilder {

    const val UTF8_BOM: String = "\uFEFF"

    fun computeSessionAccuracyPercent(inventory: InventoryEntity): Float {
        if (inventory.countedItemsCount <= 0) return 100f
        val matching = (inventory.countedItemsCount - inventory.discrepancyItemsCount).coerceAtLeast(0)
        return ((matching.toFloat() / inventory.countedItemsCount.toFloat()) * 100f).coerceIn(0f, 100f)
    }

    /**
     * Groups inventory items by category and computes exact purchase-cost shortage/surplus/net cents.
     * Sorted by absolute financial impact descending, then total gross discrepancy descending, then name.
     */
    fun buildCategoryBreakdown(
        items: List<InventoryItemEntity>,
        uncategorizedLabel: String = "Uncategorized"
    ): List<CategoryDiscrepancyReportRow> {
        if (items.isEmpty()) return emptyList()

        return items
            .groupBy { it.categoryName.trim().ifBlank { uncategorizedLabel } }
            .map { (catName, group) ->
                val summary = StocktakingCalculator.computeSessionSummary(
                    group.map {
                        StocktakingCalculator.ItemSummaryInput(
                            countedQuantity = it.countedQuantity,
                            expectedQuantity = it.effectiveExpectedQuantity,
                            unitCostCents = it.unitCostCents
                        )
                    }
                )
                CategoryDiscrepancyReportRow(
                    categoryName = catName,
                    totalItemsCount = summary.totalItemsCount,
                    countedItemsCount = summary.countedItemsCount,
                    matchingItemsCount = summary.matchingItemsCount,
                    shortageItemsCount = summary.shortageItemsCount,
                    surplusItemsCount = summary.surplusItemsCount,
                    shortageValueCents = summary.totalShortageValueCents,
                    surplusValueCents = summary.totalSurplusValueCents,
                    netDiscrepancyValueCents = summary.netDiscrepancyValueCents
                )
            }
            .sortedWith(
                compareByDescending<CategoryDiscrepancyReportRow> { abs(it.netDiscrepancyValueCents) }
                    .thenByDescending { it.shortageValueCents + it.surplusValueCents }
                    .thenBy { it.categoryName.lowercase(Locale.ROOT) }
            )
    }

    /**
     * Computes multi-session historical KPIs across all inventory sessions.
     * Financial totals and average accuracy are aggregated across COMPLETED sessions.
     */
    fun buildHistoryAnalytics(inventories: List<InventoryEntity>): HistoryAnalyticsSummary {
        if (inventories.isEmpty()) return HistoryAnalyticsSummary()

        var completed = 0
        var active = 0
        var cancelled = 0
        var accuracySum = 0.0
        var accuracyCount = 0
        var totalAudited = 0
        var cumulativeShortage = 0L
        var cumulativeSurplus = 0L
        var cumulativeNet = 0L

        for (inv in inventories) {
            when (InventoryStatus.fromCode(inv.status)) {
                InventoryStatus.COMPLETED -> {
                    completed++
                    totalAudited += inv.countedItemsCount.coerceAtLeast(0)
                    cumulativeShortage += inv.totalShortageValueCents
                    cumulativeSurplus += inv.totalSurplusValueCents
                    cumulativeNet += inv.netDiscrepancyValueCents
                    if (inv.countedItemsCount > 0) {
                        accuracySum += computeSessionAccuracyPercent(inv).toDouble()
                        accuracyCount++
                    }
                }
                InventoryStatus.DRAFT,
                InventoryStatus.IN_PROGRESS -> active++
                InventoryStatus.CANCELLED -> cancelled++
            }
        }

        val avgAccuracy = if (accuracyCount > 0) {
            (accuracySum / accuracyCount.toDouble()).toFloat().coerceIn(0f, 100f)
        } else {
            100f
        }

        return HistoryAnalyticsSummary(
            totalSessions = inventories.size,
            completedSessions = completed,
            activeSessions = active,
            cancelledSessions = cancelled,
            averageAccuracyPercent = avgAccuracy,
            totalItemsAudited = totalAudited,
            cumulativeShortageValueCents = cumulativeShortage,
            cumulativeSurplusValueCents = cumulativeSurplus,
            cumulativeNetDiscrepancyCents = cumulativeNet
        )
    }

    /**
     * Builds the localized A4 PDF table report payload for an inventory session.
     */
    fun buildPdfReportPayload(
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        discrepanciesOnly: Boolean = false
    ): StocktakingPdfReportPayload {
        val status = InventoryStatus.fromCode(inventory.status)
        val type = InventoryType.fromCode(inventory.type)
        val summary = StocktakingCalculator.computeSessionSummary(
            items.map {
                StocktakingCalculator.ItemSummaryInput(
                    countedQuantity = it.countedQuantity,
                    expectedQuantity = it.effectiveExpectedQuantity,
                    unitCostCents = it.unitCostCents
                )
            }
        )

        val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        val createdStr = dateFmt.format(Date(inventory.createdAt))
        val completedStr = inventory.completedAt?.let { dateFmt.format(Date(it)) } ?: "—"

        val docTitle = if (discrepanciesOnly) {
            Bilingual.tr(lang, "تقرير فوارق الجرد - ${inventory.reference}", "Rapport des écarts - ${inventory.reference}")
        } else {
            Bilingual.tr(lang, "تقرير الجرد الكامل - ${inventory.reference}", "Rapport d'inventaire - ${inventory.reference}")
        }

        val sessionTitlePart = inventory.title.trim().takeIf { it.isNotEmpty() && it != inventory.reference }
            ?.let { "$it | " } ?: ""
        val subtitle = Bilingual.tr(
            lang,
            "${sessionTitlePart}${localizedType(type, lang)} | ${localizedStatus(status, lang)} | $createdStr",
            "${sessionTitlePart}${localizedType(type, lang)} | ${localizedStatus(status, lang)} | $createdStr"
        )

        val headers = listOf(
            Bilingual.tr(lang, "المنتج", "Produit"),
            Bilingual.tr(lang, "الفئة", "Catégorie"),
            Bilingual.tr(lang, "النظامي", "Théor."),
            Bilingual.tr(lang, "الفعلي", "Phys."),
            Bilingual.tr(lang, "الفارق", "Écart"),
            Bilingual.tr(lang, "سعر الشراء", "Coût Unit."),
            Bilingual.tr(lang, "الأثر المالي", "Impact"),
            Bilingual.tr(lang, "الحالة", "Statut")
        )

        val filteredItems = if (discrepanciesOnly) {
            items.filter {
                val st = InventoryItemStatus.fromCode(it.status)
                st == InventoryItemStatus.SHORTAGE || st == InventoryItemStatus.SURPLUS
            }
        } else {
            items
        }.sortedWith(
            compareByDescending<InventoryItemEntity> { abs(it.discrepancyValueCents) }
                .thenBy { it.productName.lowercase(Locale.ROOT) }
        )

        val rows = filteredItems.map { item ->
            val st = InventoryItemStatus.fromCode(item.status)
            val expectedStr = StocktakingCalculator.formatQuantity(item.effectiveExpectedQuantity)
            val countedStr = item.countedQuantity?.let { StocktakingCalculator.formatQuantity(it) } ?: "—"
            val diffStr = if (item.countedQuantity != null) {
                StocktakingCalculator.formatSignedQuantity(item.difference)
            } else {
                "—"
            }
            val impactStr = if (item.countedQuantity != null) {
                formatSignedMoney(item.discrepancyValueCents, currency)
            } else {
                "—"
            }
            val labelName = if (item.barcode.isNotBlank()) {
                "${item.productName} (${item.barcode})"
            } else {
                item.productName
            }
            listOf(
                labelName,
                item.categoryName.ifBlank { "—" },
                expectedStr,
                countedStr,
                diffStr,
                MoneyUtils.format(item.unitCostCents, currency),
                impactStr,
                localizedItemStatus(st, lang)
            )
        }

        val accuracyText = String.format(Locale.US, "%.1f%%", summary.accuracyPercent)
        val summaryLines = mutableListOf(
            Bilingual.tr(lang, "المسؤول / المعتمد", "Responsable / Validé par") to
                "${inventory.createdBy.ifBlank { "—" }} / ${inventory.approvedBy?.ifBlank { "—" } ?: "—"} ($completedStr)",
            Bilingual.tr(lang, "تغطية العدّ والدقة", "Couverture & Précision") to
                "${summary.countedItemsCount}/${summary.totalItemsCount} ($accuracyText)",
            Bilingual.tr(lang, "إجمالي العجز (شراء)", "Total Manquants (Achat)") to
                "${summary.shortageItemsCount} • ${MoneyUtils.format(summary.totalShortageValueCents, currency)}",
            Bilingual.tr(lang, "إجمالي الفائض (شراء)", "Total Surplus (Achat)") to
                "${summary.surplusItemsCount} • ${MoneyUtils.format(summary.totalSurplusValueCents, currency)}",
            Bilingual.tr(lang, "صافي الأثر المالي (شراء)", "Impact financier net (Achat)") to
                formatSignedMoney(summary.netDiscrepancyValueCents, currency)
        )

        if (inventory.notes.isNotBlank()) {
            summaryLines.add(
                Bilingual.tr(lang, "ملاحظات", "Notes") to inventory.notes
            )
        }

        summaryLines.add(
            Bilingual.tr(lang, "التوقيعات (المسؤول / المدير)", "Signatures (Auditeur / Resp.)") to
                "______________ / ______________"
        )

        return StocktakingPdfReportPayload(
            docTitle = docTitle,
            subtitle = subtitle,
            headers = headers,
            rows = rows,
            summaryLines = summaryLines
        )
    }

    /**
     * Generates a UTF-8 with BOM RFC-4180 compliant CSV report suitable for Microsoft Excel,
     * LibreOffice Calc, and Google Sheets with full Arabic/French Unicode support.
     */
    fun buildCsvReport(
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = ""
    ): String {
        val status = InventoryStatus.fromCode(inventory.status)
        val type = InventoryType.fromCode(inventory.type)
        val summary = StocktakingCalculator.computeSessionSummary(
            items.map {
                StocktakingCalculator.ItemSummaryInput(
                    countedQuantity = it.countedQuantity,
                    expectedQuantity = it.effectiveExpectedQuantity,
                    unitCostCents = it.unitCostCents
                )
            }
        )
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val sb = StringBuilder()
        sb.append(UTF8_BOM)

        // Metadata section
        if (shopName.isNotBlank()) {
            sb.appendCsvRow(listOf(Bilingual.tr(lang, "المتجر", "Magasin"), shopName))
        }
        sb.appendCsvRow(listOf(Bilingual.tr(lang, "المرجع", "Référence"), inventory.reference))
        sb.appendCsvRow(listOf(Bilingual.tr(lang, "العنوان", "Titre"), inventory.title.ifBlank { inventory.reference }))
        sb.appendCsvRow(listOf(Bilingual.tr(lang, "النوع", "Type"), localizedType(type, lang)))
        sb.appendCsvRow(listOf(Bilingual.tr(lang, "الحالة", "Statut"), localizedStatus(status, lang)))
        sb.appendCsvRow(listOf(Bilingual.tr(lang, "تاريخ الإنشاء", "Créé le"), dateFmt.format(Date(inventory.createdAt))))
        sb.appendCsvRow(
            listOf(
                Bilingual.tr(lang, "تاريخ الاعتماد", "Clôturé le"),
                inventory.completedAt?.let { dateFmt.format(Date(it)) } ?: ""
            )
        )
        sb.appendCsvRow(listOf(Bilingual.tr(lang, "المسؤول", "Responsable"), inventory.createdBy))
        sb.appendCsvRow(listOf(Bilingual.tr(lang, "المعتمد", "Validé par"), inventory.approvedBy.orEmpty()))
        sb.appendCsvRow(listOf(Bilingual.tr(lang, "العملة", "Devise"), currency))
        sb.append("\r\n")

        // Table headers
        sb.appendCsvRow(
            listOf(
                Bilingual.tr(lang, "المنتج", "Produit"),
                Bilingual.tr(lang, "الباركود", "Code-barres"),
                Bilingual.tr(lang, "SKU", "SKU"),
                Bilingual.tr(lang, "الفئة", "Catégorie"),
                Bilingual.tr(lang, "الوحدة", "Unité"),
                Bilingual.tr(lang, "الكمية النظامية", "Qté Théorique"),
                Bilingual.tr(lang, "الكمية الفعلية", "Qté Physique"),
                Bilingual.tr(lang, "الفارق", "Écart"),
                Bilingual.tr(lang, "سعر الشراء", "Coût Achat"),
                Bilingual.tr(lang, "الأثر المالي", "Impact Financier"),
                Bilingual.tr(lang, "الحالة", "Statut"),
                Bilingual.tr(lang, "ملاحظات", "Notes")
            )
        )

        val sortedItems = items.sortedWith(
            compareByDescending<InventoryItemEntity> { abs(it.discrepancyValueCents) }
                .thenBy { it.productName.lowercase(Locale.ROOT) }
        )

        for (item in sortedItems) {
            val st = InventoryItemStatus.fromCode(item.status)
            sb.appendCsvRow(
                listOf(
                    item.productName,
                    item.barcode,
                    item.sku,
                    item.categoryName,
                    item.unit,
                    StocktakingCalculator.formatQuantity(item.effectiveExpectedQuantity),
                    item.countedQuantity?.let { StocktakingCalculator.formatQuantity(it) } ?: "",
                    if (item.countedQuantity != null) StocktakingCalculator.formatSignedQuantity(item.difference) else "",
                    formatPlainAmount(item.unitCostCents),
                    if (item.countedQuantity != null) formatSignedPlainAmount(item.discrepancyValueCents) else "",
                    localizedItemStatus(st, lang),
                    item.notes
                )
            )
        }

        // Totals footer
        sb.append("\r\n")
        sb.appendCsvRow(
            listOf(
                Bilingual.tr(lang, "إجمالي المنتجات", "Total Produits"),
                summary.totalItemsCount.toString(),
                Bilingual.tr(lang, "تم عدّها", "Comptés"),
                summary.countedItemsCount.toString(),
                Bilingual.tr(lang, "نسبة الدقة", "Précision"),
                String.format(Locale.US, "%.1f%%", summary.accuracyPercent)
            )
        )
        sb.appendCsvRow(
            listOf(
                Bilingual.tr(lang, "إجمالي العجز (شراء)", "Total Manquants (Achat)"),
                formatPlainAmount(summary.totalShortageValueCents),
                Bilingual.tr(lang, "إجمالي الفائض (شراء)", "Total Surplus (Achat)"),
                formatPlainAmount(summary.totalSurplusValueCents),
                Bilingual.tr(lang, "صافي الأثر المالي", "Impact Net"),
                formatSignedPlainAmount(summary.netDiscrepancyValueCents)
            )
        )

        return sb.toString()
    }

    /**
     * Computes total purchase-cost value (in exact Long cents) of physically counted units in the cart.
     */
    fun computeCountedPurchaseValueCents(countedItems: List<InventoryItemEntity>): Long {
        var total = 0L
        for (item in countedItems) {
            val qty = item.countedQuantity ?: continue
            if (qty > 0.0 && item.unitCostCents > 0L) {
                total += MoneyUtils.multiplyCents(item.unitCostCents, qty)
            }
        }
        return total
    }

    /**
     * Builds a dedicated A4 PDF table payload strictly for the Inventory Cart (counted items only),
     * ordered by most recently counted first.
     */
    fun buildCartPdfPayload(
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String
    ): StocktakingPdfReportPayload {
        val countedItems = items
            .filter { it.countedQuantity != null }
            .sortedWith(
                compareByDescending<InventoryItemEntity> { it.countedAt ?: 0L }
                    .thenBy { it.productName.lowercase(Locale.ROOT) }
            )

        val summary = StocktakingCalculator.computeSessionSummary(
            countedItems.map {
                StocktakingCalculator.ItemSummaryInput(
                    countedQuantity = it.countedQuantity,
                    expectedQuantity = it.effectiveExpectedQuantity,
                    unitCostCents = it.unitCostCents
                )
            }
        )
        val totalCountedUnits = StocktakingCalculator.normalizeQuantity(
            countedItems.sumOf { it.countedQuantity ?: 0.0 }
        )
        val totalCountedPurchaseValueCents = computeCountedPurchaseValueCents(countedItems)
        val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        val dateStr = dateFmt.format(Date(inventory.updatedAt))

        val docTitle = Bilingual.tr(
            lang,
            "سلة الجرد (المنتجات المجرودة) - ${inventory.reference}",
            "Panier d'inventaire (Produits comptés) - ${inventory.reference}"
        )
        val sessionTitlePart = inventory.title.trim().takeIf { it.isNotEmpty() && it != inventory.reference }
            ?.let { "$it | " } ?: ""
        val subtitle = "${sessionTitlePart}${Bilingual.tr(lang, "${countedItems.size} منتج مجرود", "${countedItems.size} produit(s) compté(s)")} | $dateStr"

        val headers = listOf(
            Bilingual.tr(lang, "المنتج", "Produit"),
            Bilingual.tr(lang, "الفئة", "Catégorie"),
            Bilingual.tr(lang, "النظامي", "Théor."),
            Bilingual.tr(lang, "المجرود", "Compté"),
            Bilingual.tr(lang, "الفارق", "Écart"),
            Bilingual.tr(lang, "سعر الشراء", "Coût Unit."),
            Bilingual.tr(lang, "الأثر المالي", "Impact"),
            Bilingual.tr(lang, "الحالة", "Statut")
        )

        val rows = countedItems.map { item ->
            val st = InventoryItemStatus.fromCode(item.status)
            val labelName = if (item.barcode.isNotBlank()) "${item.productName} (${item.barcode})" else item.productName
            listOf(
                labelName,
                item.categoryName.ifBlank { "—" },
                StocktakingCalculator.formatQuantity(item.effectiveExpectedQuantity),
                StocktakingCalculator.formatQuantity(item.countedQuantity ?: 0.0),
                StocktakingCalculator.formatSignedQuantity(item.difference),
                MoneyUtils.format(item.unitCostCents, currency),
                formatSignedMoney(item.discrepancyValueCents, currency),
                localizedItemStatus(st, lang)
            )
        }

        val summaryLines = listOf(
            Bilingual.tr(lang, "عدد المنتجات المجرودة", "Produits comptés") to
                "${countedItems.size} / ${items.size}",
            Bilingual.tr(lang, "إجمالي الوحدات المجرودة", "Total unités comptées") to
                StocktakingCalculator.formatQuantity(totalCountedUnits),
            Bilingual.tr(lang, "قيمة البضاعة المجرودة (شراء)", "Valeur comptée (Achat)") to
                MoneyUtils.format(totalCountedPurchaseValueCents, currency),
            Bilingual.tr(lang, "مطابق / عجز / فائض", "Conformes / Manq. / Surplus") to
                "${summary.matchingItemsCount} / ${summary.shortageItemsCount} / ${summary.surplusItemsCount}",
            Bilingual.tr(lang, "صافي الأثر المالي (شراء)", "Impact financier net (Achat)") to
                formatSignedMoney(summary.netDiscrepancyValueCents, currency)
        )

        return StocktakingPdfReportPayload(
            docTitle = docTitle,
            subtitle = subtitle,
            headers = headers,
            rows = rows,
            summaryLines = summaryLines
        )
    }

    /**
     * Builds the OpenXML worksheet XML (`xl/worksheets/sheet1.xml`) for an Excel `.xlsx` workbook
     * containing either the Inventory Cart (counted items only) or the full inventory report.
     */
    fun buildXlsxWorksheetXml(
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = "",
        cartOnly: Boolean = false
    ): String {
        val targetItems = if (cartOnly) {
            items
                .filter { it.countedQuantity != null }
                .sortedWith(
                    compareByDescending<InventoryItemEntity> { it.countedAt ?: 0L }
                        .thenBy { it.productName.lowercase(Locale.ROOT) }
                )
        } else {
            items.sortedWith(
                compareByDescending<InventoryItemEntity> { abs(it.discrepancyValueCents) }
                    .thenBy { it.productName.lowercase(Locale.ROOT) }
            )
        }

        val summary = StocktakingCalculator.computeSessionSummary(
            targetItems.map {
                StocktakingCalculator.ItemSummaryInput(
                    countedQuantity = it.countedQuantity,
                    expectedQuantity = it.effectiveExpectedQuantity,
                    unitCostCents = it.unitCostCents
                )
            }
        )
        val totalCountedUnits = StocktakingCalculator.normalizeQuantity(
            targetItems.sumOf { it.countedQuantity ?: 0.0 }
        )
        val totalCountedPurchaseValueCents = computeCountedPurchaseValueCents(targetItems)

        val headers = listOf(
            Bilingual.tr(lang, "الباركود", "Code-barres"),
            Bilingual.tr(lang, "المنتج", "Produit"),
            Bilingual.tr(lang, "الفئة", "Catégorie"),
            Bilingual.tr(lang, "الوحدة", "Unité"),
            Bilingual.tr(lang, "الكمية النظامية", "Qté Théorique"),
            Bilingual.tr(lang, "الكمية المجرودة", "Qté Comptée"),
            Bilingual.tr(lang, "الفارق", "Écart"),
            Bilingual.tr(lang, "سعر الشراء ($currency)", "Coût Achat ($currency)"),
            Bilingual.tr(lang, "قيمة المجرود ($currency)", "Valeur Comptée ($currency)"),
            Bilingual.tr(lang, "الأثر المالي ($currency)", "Impact ($currency)"),
            Bilingual.tr(lang, "الحالة", "Statut"),
            Bilingual.tr(lang, "ملاحظات", "Notes")
        )

        val colLetters = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L")
        val sb = StringBuilder()
        sb.append(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView rightToLeft="${if (lang == "AR") "1" else "0"}" workbookViewId="0"/>
  </sheetViews>
  <cols>
    <col min="1" max="1" width="18" customWidth="1"/>
    <col min="2" max="2" width="30" customWidth="1"/>
    <col min="3" max="3" width="20" customWidth="1"/>
    <col min="4" max="4" width="12" customWidth="1"/>
    <col min="5" max="5" width="15" customWidth="1"/>
    <col min="6" max="6" width="15" customWidth="1"/>
    <col min="7" max="7" width="13" customWidth="1"/>
    <col min="8" max="8" width="16" customWidth="1"/>
    <col min="9" max="9" width="18" customWidth="1"/>
    <col min="10" max="10" width="16" customWidth="1"/>
    <col min="11" max="11" width="14" customWidth="1"/>
    <col min="12" max="12" width="24" customWidth="1"/>
  </cols>
  <sheetData>
"""
        )

        var rowIndex = 1
        // Row 1: Title / Shop header
        val titlePrefix = if (cartOnly) {
            Bilingual.tr(lang, "سلة الجرد (المنتجات المجرودة)", "Panier d'inventaire (Produits comptés)")
        } else {
            Bilingual.tr(lang, "تقرير الجرد الكامل", "Rapport d'inventaire")
        }
        val titleText = buildString {
            if (shopName.isNotBlank()) append("$shopName — ")
            append("$titlePrefix — ${inventory.reference}")
            if (inventory.title.isNotBlank() && inventory.title != inventory.reference) {
                append(" (${inventory.title})")
            }
        }
        sb.append("    <row r=\"$rowIndex\" ht=\"26\" customHeight=\"1\">\n")
        sb.append("      <c r=\"A$rowIndex\" t=\"inlineStr\" s=\"2\"><is><t>${escapeXml(titleText)}</t></is></c>\n")
        sb.append("    </row>\n")
        rowIndex++

        // Row 2: Column headers
        sb.append("    <row r=\"$rowIndex\" ht=\"26\" customHeight=\"1\">\n")
        headers.forEachIndexed { idx, h ->
            sb.append("      <c r=\"${colLetters[idx]}$rowIndex\" t=\"inlineStr\" s=\"1\"><is><t>${escapeXml(h)}</t></is></c>\n")
        }
        sb.append("    </row>\n")
        rowIndex++

        // Data rows
        for (item in targetItems) {
            val st = InventoryItemStatus.fromCode(item.status)
            val countedQty = item.countedQuantity
            val countedValueCents = if (countedQty != null && countedQty > 0.0 && item.unitCostCents > 0L) {
                MoneyUtils.multiplyCents(item.unitCostCents, countedQty)
            } else {
                0L
            }
            sb.append("    <row r=\"$rowIndex\" ht=\"20\" customHeight=\"1\">\n")
            sb.append("      <c r=\"A$rowIndex\" t=\"inlineStr\"><is><t>${escapeXml(item.barcode)}</t></is></c>\n")
            sb.append("      <c r=\"B$rowIndex\" t=\"inlineStr\"><is><t>${escapeXml(item.productName)}</t></is></c>\n")
            sb.append("      <c r=\"C$rowIndex\" t=\"inlineStr\"><is><t>${escapeXml(item.categoryName)}</t></is></c>\n")
            sb.append("      <c r=\"D$rowIndex\" t=\"inlineStr\"><is><t>${escapeXml(item.unit)}</t></is></c>\n")
            sb.append("      <c r=\"E$rowIndex\"><v>${formatXmlNumber(item.effectiveExpectedQuantity)}</v></c>\n")
            if (countedQty != null) {
                sb.append("      <c r=\"F$rowIndex\"><v>${formatXmlNumber(countedQty)}</v></c>\n")
                sb.append("      <c r=\"G$rowIndex\"><v>${formatXmlNumber(item.difference)}</v></c>\n")
            } else {
                sb.append("      <c r=\"F$rowIndex\" t=\"inlineStr\"><is><t></t></is></c>\n")
                sb.append("      <c r=\"G$rowIndex\" t=\"inlineStr\"><is><t></t></is></c>\n")
            }
            sb.append("      <c r=\"H$rowIndex\"><v>${formatPlainAmount(item.unitCostCents)}</v></c>\n")
            sb.append("      <c r=\"I$rowIndex\"><v>${formatPlainAmount(countedValueCents)}</v></c>\n")
            if (countedQty != null) {
                sb.append("      <c r=\"J$rowIndex\"><v>${formatSignedXmlAmount(item.discrepancyValueCents)}</v></c>\n")
            } else {
                sb.append("      <c r=\"J$rowIndex\" t=\"inlineStr\"><is><t></t></is></c>\n")
            }
            sb.append("      <c r=\"K$rowIndex\" t=\"inlineStr\"><is><t>${escapeXml(localizedItemStatus(st, lang))}</t></is></c>\n")
            sb.append("      <c r=\"L$rowIndex\" t=\"inlineStr\"><is><t>${escapeXml(item.notes)}</t></is></c>\n")
            sb.append("    </row>\n")
            rowIndex++
        }

        // Totals summary row
        sb.append("    <row r=\"$rowIndex\" ht=\"24\" customHeight=\"1\">\n")
        sb.append("      <c r=\"A$rowIndex\" t=\"inlineStr\" s=\"2\"><is><t>${escapeXml(Bilingual.tr(lang, "المجموع", "TOTAL"))}</t></is></c>\n")
        sb.append("      <c r=\"B$rowIndex\" t=\"inlineStr\" s=\"2\"><is><t>${escapeXml("${targetItems.size}")}</t></is></c>\n")
        sb.append("      <c r=\"F$rowIndex\" s=\"2\"><v>${formatXmlNumber(totalCountedUnits)}</v></c>\n")
        sb.append("      <c r=\"I$rowIndex\" s=\"2\"><v>${formatPlainAmount(totalCountedPurchaseValueCents)}</v></c>\n")
        sb.append("      <c r=\"J$rowIndex\" s=\"2\"><v>${formatSignedXmlAmount(summary.netDiscrepancyValueCents)}</v></c>\n")
        sb.append("    </row>\n")

        sb.append("  </sheetData>\n</worksheet>")
        return sb.toString()
    }

    fun escapeXml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun formatXmlNumber(value: Double): String =
        StocktakingCalculator.formatQuantity(value)

    private fun formatSignedXmlAmount(cents: Long): String =
        String.format(Locale.US, "%.2f", cents / 100.0)

    fun escapeCsvField(raw: String): String {
        val needsQuoting = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            "\"${raw.replace("\"", "\"\"")}\""
        } else {
            raw
        }
    }

    private fun StringBuilder.appendCsvRow(fields: List<String>) {
        fields.forEachIndexed { index, value ->
            if (index > 0) append(',')
            append(escapeCsvField(value))
        }
        append("\r\n")
    }

    private fun formatPlainAmount(cents: Long): String =
        String.format(Locale.US, "%.2f", cents / 100.0)

    private fun formatSignedPlainAmount(cents: Long): String {
        val formatted = formatPlainAmount(abs(cents))
        return when {
            cents > 0L -> "+$formatted"
            cents < 0L -> "-$formatted"
            else -> "0.00"
        }
    }

    private fun formatSignedMoney(cents: Long, currency: String): String {
        return when {
            cents < 0L -> MoneyUtils.format(cents, currency)
            cents > 0L -> {
                val base = MoneyUtils.format(cents, currency)
                if (base.startsWith("\u2066")) "\u2066+${base.removePrefix("\u2066")}" else "+$base"
            }
            else -> MoneyUtils.format(0L, currency)
        }
    }

    private fun localizedStatus(status: InventoryStatus, lang: String): String = when (status) {
        InventoryStatus.DRAFT -> Bilingual.tr(lang, "مسودة / متوقف", "Brouillon / Pause")
        InventoryStatus.IN_PROGRESS -> Bilingual.tr(lang, "قيد الجرد", "En cours")
        InventoryStatus.COMPLETED -> Bilingual.tr(lang, "مكتمل ومعتمد", "Clôturé & Validé")
        InventoryStatus.CANCELLED -> Bilingual.tr(lang, "ملغى", "Annulé")
    }

    private fun localizedType(type: InventoryType, lang: String): String = when (type) {
        InventoryType.GENERAL -> Bilingual.tr(lang, "جرد شامل", "Inventaire général")
        InventoryType.PARTIAL_CATEGORY -> Bilingual.tr(lang, "جزئي (فئات)", "Partiel (Catégories)")
        InventoryType.PARTIAL_PRODUCTS -> Bilingual.tr(lang, "جزئي (منتجات)", "Partiel (Produits)")
        InventoryType.PARTIAL_SUPPLIER -> Bilingual.tr(lang, "جزئي (موردون)", "Partiel (Fournisseurs)")
    }

    private fun localizedItemStatus(status: InventoryItemStatus, lang: String): String = when (status) {
        InventoryItemStatus.UNCOUNTED -> Bilingual.tr(lang, "غير معدود", "Non compté")
        InventoryItemStatus.MATCHING -> Bilingual.tr(lang, "مطابق", "Conforme")
        InventoryItemStatus.SHORTAGE -> Bilingual.tr(lang, "عجز", "Manquant")
        InventoryItemStatus.SURPLUS -> Bilingual.tr(lang, "فائض", "Surplus")
    }
}
