package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryEntity
import com.example.domain.stocktaking.InventoryDashboardStats
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils

private enum class DashboardSessionFilter { ALL, ACTIVE, COMPLETED, CANCELLED }

@Composable
internal fun StocktakingDashboardView(
    stats: InventoryDashboardStats,
    allInventories: List<InventoryEntity>,
    activeInventories: List<InventoryEntity>,
    isManager: Boolean = true,
    currency: String,
    lang: String,
    onBack: () -> Unit,
    onStartGeneral: () -> Unit,
    onStartPartial: () -> Unit,
    onOpenValuationMonitoring: () -> Unit,
    onOpenAdjustments: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSession: (InventoryEntity) -> Unit,
    onOpenSessionReport: (InventoryEntity) -> Unit,
    onEditSession: (InventoryEntity) -> Unit = {},
    onDeleteSession: (InventoryEntity) -> Unit = {}
) {
    var filter by remember { mutableStateOf(DashboardSessionFilter.ALL) }

    val filteredInventories = remember(allInventories, filter) {
        when (filter) {
            DashboardSessionFilter.ALL -> allInventories
            DashboardSessionFilter.ACTIVE -> allInventories.filter {
                val st = InventoryStatus.fromCode(it.status)
                st == InventoryStatus.DRAFT || st == InventoryStatus.IN_PROGRESS
            }
            DashboardSessionFilter.COMPLETED -> allInventories.filter {
                InventoryStatus.fromCode(it.status) == InventoryStatus.COMPLETED
            }
            DashboardSessionFilter.CANCELLED -> allInventories.filter {
                InventoryStatus.fromCode(it.status) == InventoryStatus.CANCELLED
            }
        }
    }

    val lastCompletedSession = remember(allInventories, stats.lastCompletedReference) {
        stats.lastCompletedReference?.let { ref ->
            allInventories.firstOrNull { it.reference == ref }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StocktakingPalette.PageBg)
    ) {
        StocktakingHeaderBar(
            title = Bilingual.tr(lang, "الجرد وإدارة المخزون", "Inventaire & Stock"),
            subtitle = Bilingual.tr(
                lang,
                "${stats.totalActiveProducts} منتج نشط • ${StocktakingCalculator.formatQuantity(stats.totalUnitsInStock)} وحدة",
                "${stats.totalActiveProducts} produits actifs • ${StocktakingCalculator.formatQuantity(stats.totalUnitsInStock)} unités"
            ),
            onBack = onBack,
            backTestTag = "stocktaking_dashboard_back_btn"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Primary Action Banner (New General / New Partial)
            item(key = "hero_actions") {
                DashboardHeroActionCard(
                    stats = stats,
                    currency = currency,
                    lang = lang,
                    onStartGeneral = onStartGeneral,
                    onStartPartial = onStartPartial
                )
            }

            // 1b. Quick Tools Row: Valuation & Monitoring + Adjustments Audit Trail
            item(key = "quick_tools_row") {
                val alertCount = stats.outOfStockCount + stats.lowStockCount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardQuickToolCard(
                        title = Bilingual.tr(lang, "التقييم والمراقبة", "Valorisation & Alertes"),
                        subtitle = Bilingual.tr(
                            lang,
                            if (alertCount > 0) "$alertCount تنبيه مخزون" else "تقييم فوري وتنبيهات",
                            if (alertCount > 0) "$alertCount alerte(s) stock" else "Valorisation & alertes"
                        ),
                        badgeText = if (alertCount > 0) "$alertCount" else null,
                        badgeColor = if (stats.outOfStockCount > 0) StocktakingPalette.Rose else StocktakingPalette.Amber,
                        icon = Icons.Filled.Assessment,
                        accentColor = StocktakingPalette.AccentBlue,
                        testTag = "stocktaking_open_valuation_btn",
                        onClick = onOpenValuationMonitoring,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardQuickToolCard(
                        title = Bilingual.tr(lang, "سجل التسويات", "Ajustements"),
                        subtitle = Bilingual.tr(
                            lang,
                            "${stats.totalAdjustmentsCount} تسوية موثّقة",
                            "${stats.totalAdjustmentsCount} ajustement(s)"
                        ),
                        badgeText = if (stats.totalAdjustmentsCount > 0) "${stats.totalAdjustmentsCount}" else null,
                        badgeColor = StocktakingPalette.Indigo,
                        icon = Icons.Filled.History,
                        accentColor = StocktakingPalette.Indigo,
                        testTag = "stocktaking_open_adjustments_btn",
                        onClick = onOpenAdjustments,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 1c. Reports & Complete History Quick Tool Banner
            item(key = "quick_tool_history_reports") {
                DashboardQuickToolCard(
                    title = Bilingual.tr(lang, "التقارير والأرشيف الكامل", "Rapports & Historique complet"),
                    subtitle = Bilingual.tr(
                        lang,
                        "تصدير PDF و CSV والطباعة وتحليل دقة جميع جلسات الجرد (${allInventories.size})",
                        "Export PDF, CSV, impression et analyses de toutes les sessions (${allInventories.size})"
                    ),
                    badgeText = if (stats.completedInventoriesCount > 0) "${stats.completedInventoriesCount}" else null,
                    badgeColor = StocktakingPalette.Emerald,
                    icon = Icons.Filled.Description,
                    accentColor = StocktakingPalette.Emerald,
                    testTag = "stocktaking_open_history_btn",
                    onClick = onOpenHistory,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Real-Time KPI Grid (2x2)
            item(key = "kpi_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StocktakingKpiCard(
                            label = Bilingual.tr(lang, "قيمة المخزون (شراء)", "Valeur Stock (Achat)"),
                            value = MoneyUtils.format(stats.totalPurchaseValueCents, currency),
                            subtitle = Bilingual.tr(
                                lang,
                                "بيع: ${MoneyUtils.format(stats.totalSellValueCents, currency)}",
                                "Vente: ${MoneyUtils.format(stats.totalSellValueCents, currency)}"
                            ),
                            icon = Icons.Filled.AccountBalanceWallet,
                            accentColor = StocktakingPalette.AccentBlue,
                            modifier = Modifier.weight(1f)
                        )
                        StocktakingKpiCard(
                            label = Bilingual.tr(lang, "هامش الربح المتوقع", "Marge Brute Est."),
                            value = MoneyUtils.format(stats.potentialGrossMarginCents, currency),
                            subtitle = Bilingual.tr(
                                lang,
                                "${stats.totalActiveProducts} منتج نشط",
                                "${stats.totalActiveProducts} produits actifs"
                            ),
                            icon = Icons.Filled.TrendingUp,
                            accentColor = StocktakingPalette.Emerald,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StocktakingKpiCard(
                            label = Bilingual.tr(lang, "تنبيهات المخزون", "Alertes Stock"),
                            value = "${stats.outOfStockCount + stats.lowStockCount}",
                            subtitle = Bilingual.tr(
                                lang,
                                "نفاد: ${stats.outOfStockCount} • منخفض: ${stats.lowStockCount}",
                                "Rupture: ${stats.outOfStockCount} • Bas: ${stats.lowStockCount}"
                            ),
                            icon = Icons.Filled.NotificationsActive,
                            accentColor = if (stats.outOfStockCount > 0) StocktakingPalette.Rose else StocktakingPalette.Amber,
                            modifier = Modifier.weight(1f)
                        )
                        StocktakingKpiCard(
                            label = Bilingual.tr(lang, "جلسات الجرد", "Sessions Inventaire"),
                            value = "${stats.activeInventoriesCount} / ${stats.completedInventoriesCount}",
                            subtitle = Bilingual.tr(
                                lang,
                                "نشطة: ${stats.activeInventoriesCount} • مكتملة: ${stats.completedInventoriesCount}",
                                "Actives: ${stats.activeInventoriesCount} • Clôturées: ${stats.completedInventoriesCount}"
                            ),
                            icon = Icons.Filled.FactCheck,
                            accentColor = StocktakingPalette.Indigo,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Last Completed Inventory Strip (if exists) — clickable to open its full report
            if (stats.lastCompletedReference != null) {
                item(key = "last_completed_banner") {
                    val netCents = stats.lastNetDiscrepancyCents
                    val netColor = when {
                        netCents < 0L -> StocktakingPalette.Rose
                        netCents > 0L -> StocktakingPalette.Emerald
                        else -> StocktakingPalette.AccentSky
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StocktakingPalette.CardBg,
                        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = lastCompletedSession != null) {
                                lastCompletedSession?.let(onOpenSessionReport)
                            }
                            .testTag("stocktaking_last_completed_report_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Bilingual.tr(lang, "آخر جرد مكتمل • اضغط لعرض التقرير", "Dernier inventaire clôturé • Voir rapport"),
                                    color = StocktakingPalette.AccentBlue,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = "${stats.lastCompletedReference} • ${formatStocktakingShortDate(stats.lastCompletedAt)}",
                                    color = StocktakingPalette.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Bilingual.tr(lang, "صافي الفارق", "Écart net"),
                                    color = StocktakingPalette.TextMuted,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = MoneyUtils.format(netCents, currency),
                                    color = netColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // 4. Active Sessions Priority Section
            if (activeInventories.isNotEmpty()) {
                item(key = "active_header") {
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "جلسات الجرد الجارية (${activeInventories.size})",
                            "Inventaires en cours (${activeInventories.size})"
                        ),
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(activeInventories, key = { "active_${it.id}" }) { session ->
                    InventorySessionCard(
                        session = session,
                        isManager = isManager,
                        currency = currency,
                        lang = lang,
                        isHighlighted = true,
                        onClick = { onOpenSession(session) },
                        onOpenReport = { onOpenSessionReport(session) },
                        onEdit = { onEditSession(session) },
                        onDelete = { onDeleteSession(session) }
                    )
                }
            }

            // 5. All Sessions List + Filter Chips
            item(key = "all_sessions_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = Bilingual.tr(lang, "سجل جلسات الجرد", "Sessions d'inventaire"),
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val chips = listOf(
                            DashboardSessionFilter.ALL to Bilingual.tr(lang, "الكل (${allInventories.size})", "Tous (${allInventories.size})"),
                            DashboardSessionFilter.ACTIVE to Bilingual.tr(lang, "نشط (${activeInventories.size})", "Actifs (${activeInventories.size})"),
                            DashboardSessionFilter.COMPLETED to Bilingual.tr(lang, "مكتمل (${stats.completedInventoriesCount})", "Clôturés (${stats.completedInventoriesCount})"),
                            DashboardSessionFilter.CANCELLED to Bilingual.tr(lang, "ملغى", "Annulés")
                        )
                        chips.forEach { (chipFilter, label) ->
                            FilterChip(
                                selected = filter == chipFilter,
                                onClick = { filter = chipFilter },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("stocktaking_filter_${chipFilter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (filteredInventories.isEmpty()) {
                item(key = "empty_sessions") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = StocktakingPalette.CardBg,
                        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Inventory,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "لا توجد جلسات جرد في هذا القسم بعد",
                                    "Aucune session d'inventaire dans cette catégorie"
                                ),
                                color = StocktakingPalette.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "ابدأ جرداً شاملاً للمتجر أو جرداً جزئياً لفئة محددة للتحقق من المخزون الفعلي بدقة.",
                                    "Démarrez un inventaire général ou partiel pour contrôler votre stock physique avec précision."
                                ),
                                color = StocktakingPalette.TextMuted,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredInventories, key = { "all_${it.id}" }) { session ->
                    InventorySessionCard(
                        session = session,
                        isManager = isManager,
                        currency = currency,
                        lang = lang,
                        isHighlighted = false,
                        onClick = { onOpenSession(session) },
                        onOpenReport = { onOpenSessionReport(session) },
                        onEdit = { onEditSession(session) },
                        onDelete = { onDeleteSession(session) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DashboardHeroActionCard(
    stats: InventoryDashboardStats,
    currency: String,
    lang: String,
    onStartGeneral: () -> Unit,
    onStartPartial: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = StocktakingPalette.NavyDeep,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF0369A1))
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Bilingual.tr(lang, "بدء عملية جرد جديدة", "Nouvelle opération d'inventaire"),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "جرد دون توقف المبيعات — مزامنة ذكية للفوارق بسعر الشراء",
                                "Comptage sans arrêter la caisse — réconciliation intelligente au coût d'achat"
                            ),
                            color = Color(0xFFBAE6FD),
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = MoneyUtils.format(stats.totalPurchaseValueCents, currency),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartGeneral,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = StocktakingPalette.NavyDeep
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("stocktaking_new_general_btn")
                    ) {
                        Icon(Icons.Filled.Inventory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(lang, "جرد شامل", "Inventaire général"),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onStartPartial,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.5.dp, Color(0xFF7DD3FC)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("stocktaking_new_partial_btn")
                    ) {
                        Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(lang, "جرد جزئي", "Inventaire partiel"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventorySessionCard(
    session: InventoryEntity,
    isManager: Boolean,
    currency: String,
    lang: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    onOpenReport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val status = InventoryStatus.fromCode(session.status)
    val type = InventoryType.fromCode(session.type)
    val progress = if (session.totalItemsCount > 0) {
        (session.countedItemsCount.toFloat() / session.totalItemsCount.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val progressPct = (progress * 100f).toInt()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(
            width = if (isHighlighted) 1.5.dp else 1.dp,
            color = if (isHighlighted) StocktakingPalette.AccentBlue else StocktakingPalette.CardBorder
        ),
        shadowElevation = if (isHighlighted) 2.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("stocktaking_session_card_${session.id}")
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
                        text = session.title.ifBlank { session.reference },
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${session.reference} • ${inventoryTypeLabel(type, lang)} • ${formatStocktakingShortDate(session.createdAt)}",
                        color = StocktakingPalette.TextMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                StocktakingStatusBadge(status = status, lang = lang)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "تم عدّ ${session.countedItemsCount} / ${session.totalItemsCount} منتج ($progressPct%)",
                            "Comptés : ${session.countedItemsCount} / ${session.totalItemsCount} ($progressPct%)"
                        ),
                        color = StocktakingPalette.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (session.discrepancyItemsCount > 0) {
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "${session.discrepancyItemsCount} فوارق",
                                "${session.discrepancyItemsCount} écarts"
                            ),
                            color = StocktakingPalette.Rose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (status == InventoryStatus.COMPLETED) StocktakingPalette.Emerald else StocktakingPalette.AccentBlue,
                    trackColor = StocktakingPalette.SlateSoft
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val netCents = session.netDiscrepancyValueCents
                val netColor = when {
                    netCents < 0L -> StocktakingPalette.Rose
                    netCents > 0L -> StocktakingPalette.Emerald
                    else -> StocktakingPalette.TextSecondary
                }
                Text(
                    text = Bilingual.tr(
                        lang,
                        "صافي الفارق: ${MoneyUtils.format(netCents, currency)}",
                        "Écart net : ${MoneyUtils.format(netCents, currency)}"
                    ),
                    color = netColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (status.isEditable) {
                        Bilingual.tr(lang, "متابعة ←", "Continuer →")
                    } else {
                        Bilingual.tr(lang, "التفاصيل ←", "Détails →")
                    },
                    color = StocktakingPalette.AccentBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenReport,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("stocktaking_session_report_btn_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = Bilingual.tr(lang, "التقرير", "Rapport"),
                        tint = StocktakingPalette.AccentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = Bilingual.tr(lang, "عرض التقرير", "Rapport"),
                        color = StocktakingPalette.AccentBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isManager || status.isEditable) {
                    OutlinedIconButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("stocktaking_session_edit_btn_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = Bilingual.tr(lang, "تعديل الجرد", "Modifier"),
                            tint = StocktakingPalette.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (isManager) {
                    OutlinedIconButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StocktakingPalette.Rose.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("stocktaking_session_delete_btn_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = Bilingual.tr(lang, "حذف الجرد", "Supprimer"),
                            tint = StocktakingPalette.Rose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardQuickToolCard(
    title: String,
    subtitle: String,
    badgeText: String?,
    badgeColor: Color,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        shadowElevation = 1.dp,
        modifier = modifier
            .heightIn(min = 74.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (badgeText != null) {
                        Surface(
                            color = badgeColor,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    color = StocktakingPalette.TextMuted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
