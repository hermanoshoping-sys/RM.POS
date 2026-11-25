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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.stocktaking.CategoryValuationRow
import com.example.domain.stocktaking.InventoryDashboardStats
import com.example.domain.stocktaking.StockAlertType
import com.example.domain.stocktaking.StockMonitoringItem
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.domain.stocktaking.SupplierValuationRow
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils

private enum class ValuationMonitoringTab { VALUATION, MONITORING }
private enum class ValuationGroupBy { CATEGORY, SUPPLIER }

@Composable
internal fun StocktakingValuationMonitoringScreen(
    stats: InventoryDashboardStats,
    categoryValuations: List<CategoryValuationRow>,
    supplierValuations: List<SupplierValuationRow>,
    monitoringItems: List<StockMonitoringItem>,
    slowMovingDays: Int,
    currency: String,
    lang: String,
    onBack: () -> Unit,
    onChangeSlowMovingDays: (Int) -> Unit,
    onStartPartialInventoryForProducts: (title: String, productIds: List<Long>) -> Unit
) {
    var selectedTab by remember { mutableStateOf(ValuationMonitoringTab.VALUATION) }
    var groupBy by remember { mutableStateOf(ValuationGroupBy.CATEGORY) }
    var alertFilter by remember { mutableStateOf<StockAlertType?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val outCount = remember(monitoringItems) { monitoringItems.count { it.alertType == StockAlertType.OUT_OF_STOCK } }
    val lowCount = remember(monitoringItems) { monitoringItems.count { it.alertType == StockAlertType.LOW_STOCK } }
    val slowCount = remember(monitoringItems) { monitoringItems.count { it.alertType == StockAlertType.SLOW_MOVING } }

    val filteredAlerts = remember(monitoringItems, alertFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        monitoringItems.filter { item ->
            val typeOk = alertFilter == null || item.alertType == alertFilter
            val searchOk = q.isEmpty() ||
                item.productName.lowercase().contains(q) ||
                item.barcode.lowercase().contains(q) ||
                item.categoryName.lowercase().contains(q)
            typeOk && searchOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StocktakingPalette.PageBg)
    ) {
        StocktakingHeaderBar(
            title = Bilingual.tr(lang, "التقييم ومراقبة المخزون", "Valorisation & Surveillance"),
            subtitle = Bilingual.tr(
                lang,
                "تقييم فوري بسعر الشراء والبيع • رصد النواقص والركود",
                "Valorisation achat/vente en direct • Alertes rupture & rotation"
            ),
            onBack = onBack,
            backTestTag = "valuation_monitoring_back_btn"
        )

        // Top Segmented Tab Row
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = StocktakingPalette.CardBg,
            contentColor = StocktakingPalette.AccentBlue
        ) {
            Tab(
                selected = selectedTab == ValuationMonitoringTab.VALUATION,
                onClick = { selectedTab = ValuationMonitoringTab.VALUATION },
                modifier = Modifier.testTag("valuation_tab_btn"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(Bilingual.tr(lang, "التقييم المالي", "Valorisation"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )
            Tab(
                selected = selectedTab == ValuationMonitoringTab.MONITORING,
                onClick = { selectedTab = ValuationMonitoringTab.MONITORING },
                modifier = Modifier.testTag("monitoring_tab_btn"),
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            Bilingual.tr(lang, "المراقبة (${monitoringItems.size})", "Alertes (${monitoringItems.size})"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            )
        }

        if (selectedTab == ValuationMonitoringTab.VALUATION) {
            ValuationTabContent(
                stats = stats,
                groupBy = groupBy,
                onGroupByChange = { groupBy = it },
                categoryValuations = categoryValuations,
                supplierValuations = supplierValuations,
                currency = currency,
                lang = lang
            )
        } else {
            MonitoringTabContent(
                allCount = monitoringItems.size,
                outCount = outCount,
                lowCount = lowCount,
                slowCount = slowCount,
                filteredAlerts = filteredAlerts,
                alertFilter = alertFilter,
                onFilterChange = { alertFilter = it },
                slowMovingDays = slowMovingDays,
                onChangeSlowMovingDays = onChangeSlowMovingDays,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                currency = currency,
                lang = lang,
                onStartPartialInventoryForProducts = onStartPartialInventoryForProducts
            )
        }
    }
}

@Composable
private fun ValuationTabContent(
    stats: InventoryDashboardStats,
    groupBy: ValuationGroupBy,
    onGroupByChange: (ValuationGroupBy) -> Unit,
    categoryValuations: List<CategoryValuationRow>,
    supplierValuations: List<SupplierValuationRow>,
    currency: String,
    lang: String
) {
    val marginPct = if (stats.totalSellValueCents > 0L) {
        (stats.potentialGrossMarginCents.toDouble() / stats.totalSellValueCents.toDouble() * 100.0).toInt()
    } else 0
    val totalPurchaseBase = stats.totalPurchaseValueCents.coerceAtLeast(1L)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Global Valuation Hero Card
        item(key = "valuation_hero") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = StocktakingPalette.NavyDeep,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E3A8A))))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = Bilingual.tr(lang, "إجمالي قيمة المخزون (سعر الشراء)", "Valeur totale du stock (Coût d'achat)"),
                                    color = Color(0xFFBAE6FD),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = MoneyUtils.format(stats.totalPurchaseValueCents, currency),
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                )
                            }
                            Surface(
                                color = Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = Bilingual.tr(
                                        lang,
                                        "${stats.totalActiveProducts} منتج\n${StocktakingCalculator.formatQuantity(stats.totalUnitsInStock)} وحدة",
                                        "${stats.totalActiveProducts} prod.\n${StocktakingCalculator.formatQuantity(stats.totalUnitsInStock)} u."
                                    ),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = Bilingual.tr(lang, "القيمة بسعر البيع", "Valeur au prix de vente"),
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = MoneyUtils.format(stats.totalSellValueCents, currency),
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Bilingual.tr(lang, "هامش الربح المتوقع ($marginPct%)", "Marge brute est. ($marginPct%)"),
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = MoneyUtils.format(stats.potentialGrossMarginCents, currency),
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Group-By Chips
        item(key = "valuation_group_toggle") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = groupBy == ValuationGroupBy.CATEGORY,
                    onClick = { onGroupByChange(ValuationGroupBy.CATEGORY) },
                    leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(Bilingual.tr(lang, "حسب الفئة (${categoryValuations.size})", "Par Catégorie (${categoryValuations.size})"), fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f).testTag("valuation_by_category_chip")
                )
                FilterChip(
                    selected = groupBy == ValuationGroupBy.SUPPLIER,
                    onClick = { onGroupByChange(ValuationGroupBy.SUPPLIER) },
                    leadingIcon = { Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(Bilingual.tr(lang, "حسب المورد (${supplierValuations.size})", "Par Fournisseur (${supplierValuations.size})"), fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f).testTag("valuation_by_supplier_chip")
                )
            }
        }

        if (groupBy == ValuationGroupBy.CATEGORY) {
            if (categoryValuations.isEmpty()) {
                item(key = "empty_cat_val") {
                    EmptyMonitoringCard(
                        text = Bilingual.tr(lang, "لا توجد منتجات نشطة للتقييم", "Aucun produit actif à valoriser")
                    )
                }
            } else {
                items(categoryValuations, key = { "cat_val_${it.categoryId}" }) { row ->
                    val share = (row.purchaseValueCents.toFloat() / totalPurchaseBase.toFloat()).coerceIn(0f, 1f)
                    ValuationBreakdownCard(
                        name = row.categoryName.ifBlank { Bilingual.tr(lang, "بدون فئة", "Sans catégorie") },
                        productCount = row.productCount,
                        totalUnits = row.totalUnits,
                        purchaseValueCents = row.purchaseValueCents,
                        sellValueCents = row.sellValueCents,
                        marginCents = row.potentialMarginCents,
                        share = share,
                        currency = currency,
                        lang = lang
                    )
                }
            }
        } else {
            if (supplierValuations.isEmpty()) {
                item(key = "empty_sup_val") {
                    EmptyMonitoringCard(
                        text = Bilingual.tr(
                            lang,
                            "لم تُسجّل بعد فواتير شراء مرتبطة بموردين لحساب التقييم حسب المورد",
                            "Aucune facture d'achat liée à un fournisseur pour calculer la valorisation par fournisseur"
                        )
                    )
                }
            } else {
                items(supplierValuations, key = { "sup_val_${it.supplierId}" }) { row ->
                    val share = (row.purchaseValueCents.toFloat() / totalPurchaseBase.toFloat()).coerceIn(0f, 1f)
                    ValuationBreakdownCard(
                        name = row.supplierName,
                        productCount = row.productCount,
                        totalUnits = row.totalUnits,
                        purchaseValueCents = row.purchaseValueCents,
                        sellValueCents = row.sellValueCents,
                        marginCents = row.potentialMarginCents,
                        share = share,
                        currency = currency,
                        lang = lang
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ValuationBreakdownCard(
    name: String,
    productCount: Int,
    totalUnits: Double,
    purchaseValueCents: Long,
    sellValueCents: Long,
    marginCents: Long,
    share: Float,
    currency: String,
    lang: String
) {
    val sharePct = (share * 100f).toInt()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = StocktakingPalette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "$productCount منتج • ${StocktakingCalculator.formatQuantity(totalUnits)} وحدة",
                            "$productCount produit(s) • ${StocktakingCalculator.formatQuantity(totalUnits)} unités"
                        ),
                        color = StocktakingPalette.TextMuted,
                        fontSize = 11.5.sp
                    )
                }
                Surface(
                    color = StocktakingPalette.IndigoSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$sharePct%",
                        color = StocktakingPalette.Indigo,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { share },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = StocktakingPalette.AccentBlue,
                trackColor = StocktakingPalette.SlateSoft
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(Bilingual.tr(lang, "سعر الشراء", "Coût achat"), color = StocktakingPalette.TextMuted, fontSize = 11.sp)
                    Text(MoneyUtils.format(purchaseValueCents, currency), color = StocktakingPalette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Bilingual.tr(lang, "سعر البيع", "Prix vente"), color = StocktakingPalette.TextMuted, fontSize = 11.sp)
                    Text(MoneyUtils.format(sellValueCents, currency), color = StocktakingPalette.AccentSky, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Bilingual.tr(lang, "الهامش", "Marge"), color = StocktakingPalette.TextMuted, fontSize = 11.sp)
                    Text(MoneyUtils.format(marginCents, currency), color = StocktakingPalette.Emerald, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Composable
private fun MonitoringTabContent(
    allCount: Int,
    outCount: Int,
    lowCount: Int,
    slowCount: Int,
    filteredAlerts: List<StockMonitoringItem>,
    alertFilter: StockAlertType?,
    onFilterChange: (StockAlertType?) -> Unit,
    slowMovingDays: Int,
    onChangeSlowMovingDays: (Int) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    currency: String,
    lang: String,
    onStartPartialInventoryForProducts: (title: String, productIds: List<Long>) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "monitoring_controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(Bilingual.tr(lang, "بحث في التنبيهات...", "Rechercher dans les alertes...")) },
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
                    FilterChip(
                        selected = alertFilter == null,
                        onClick = { onFilterChange(null) },
                        label = { Text(Bilingual.tr(lang, "الكل ($allCount)", "Tous ($allCount)"), fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = alertFilter == StockAlertType.OUT_OF_STOCK,
                        onClick = { onFilterChange(StockAlertType.OUT_OF_STOCK) },
                        label = { Text(Bilingual.tr(lang, "نافد ($outCount)", "Rupture ($outCount)"), fontSize = 12.sp) },
                        modifier = Modifier.testTag("monitoring_filter_out")
                    )
                    FilterChip(
                        selected = alertFilter == StockAlertType.LOW_STOCK,
                        onClick = { onFilterChange(StockAlertType.LOW_STOCK) },
                        label = { Text(Bilingual.tr(lang, "منخفض ($lowCount)", "Bas ($lowCount)"), fontSize = 12.sp) },
                        modifier = Modifier.testTag("monitoring_filter_low")
                    )
                    FilterChip(
                        selected = alertFilter == StockAlertType.SLOW_MOVING,
                        onClick = { onFilterChange(StockAlertType.SLOW_MOVING) },
                        label = { Text(Bilingual.tr(lang, "بطيء ($slowCount)", "Lent ($slowCount)"), fontSize = 12.sp) },
                        modifier = Modifier.testTag("monitoring_filter_slow")
                    )
                }

                // Slow-moving period chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = Bilingual.tr(lang, "فترة الركود:", "Période lente :"),
                        color = StocktakingPalette.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    listOf(15, 30, 60, 90).forEach { days ->
                        FilterChip(
                            selected = slowMovingDays == days,
                            onClick = { onChangeSlowMovingDays(days) },
                            label = { Text(Bilingual.tr(lang, "$days ي", "${days}j"), fontSize = 11.5.sp) }
                        )
                    }
                }

                if (filteredAlerts.isNotEmpty()) {
                    Button(
                        onClick = {
                            val title = when (alertFilter) {
                                StockAlertType.OUT_OF_STOCK -> Bilingual.tr(lang, "جرد الأصناف النافدة", "Inventaire ruptures")
                                StockAlertType.LOW_STOCK -> Bilingual.tr(lang, "جرد المخزون المنخفض", "Inventaire stock bas")
                                StockAlertType.SLOW_MOVING -> Bilingual.tr(lang, "جرد الأصناف البطيئة ($slowMovingDays يوم)", "Inventaire rotation lente (${slowMovingDays}j)")
                                null -> Bilingual.tr(lang, "جرد أصناف التنبيهات", "Inventaire alertes stock")
                            }
                            onStartPartialInventoryForProducts(title, filteredAlerts.map { it.productId })
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.AccentBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("monitoring_create_partial_btn")
                    ) {
                        Icon(Icons.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "إنشاء جرد جزئي لهذه الأصناف (${filteredAlerts.size})",
                                "Lancer un inventaire partiel (${filteredAlerts.size} produits)"
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (filteredAlerts.isEmpty()) {
            item(key = "empty_monitoring") {
                EmptyMonitoringCard(
                    text = Bilingual.tr(lang, "لا توجد تنبيهات مخزون في هذا القسم — المخزون في حالة ممتازة!", "Aucune alerte dans cette catégorie — votre stock est sain !")
                )
            }
        } else {
            items(filteredAlerts, key = { "alert_${it.productId}" }) { item ->
                MonitoringAlertCard(item = item, slowMovingDays = slowMovingDays, currency = currency, lang = lang)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MonitoringAlertCard(
    item: StockMonitoringItem,
    slowMovingDays: Int,
    currency: String,
    lang: String
) {
    val (badgeBg, badgeFg, badgeLabel) = when (item.alertType) {
        StockAlertType.OUT_OF_STOCK -> Triple(
            StocktakingPalette.RoseSoft,
            StocktakingPalette.Rose,
            Bilingual.tr(lang, "نفاد المخزون", "Rupture")
        )
        StockAlertType.LOW_STOCK -> Triple(
            StocktakingPalette.AmberSoft,
            StocktakingPalette.Amber,
            Bilingual.tr(lang, "مخزون منخفض", "Stock bas")
        )
        StockAlertType.SLOW_MOVING -> Triple(
            StocktakingPalette.IndigoSoft,
            StocktakingPalette.Indigo,
            Bilingual.tr(lang, "بطيء الحركة", "Rotation lente")
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = StocktakingPalette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.barcode} • ${item.categoryName}",
                        color = StocktakingPalette.TextMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = badgeLabel,
                        color = badgeFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "الحالي: ${StocktakingCalculator.formatQuantity(item.currentStock)} / الحد: ${StocktakingCalculator.formatQuantity(item.minStock)} ${item.unit}",
                        "Stock: ${StocktakingCalculator.formatQuantity(item.currentStock)} / Min: ${StocktakingCalculator.formatQuantity(item.minStock)} ${item.unit}"
                    ),
                    color = if (item.alertType == StockAlertType.OUT_OF_STOCK) StocktakingPalette.Rose else StocktakingPalette.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Text(
                    text = Bilingual.tr(
                        lang,
                        "شراء: ${MoneyUtils.format(item.purchasePriceCents, currency)}",
                        "Achat: ${MoneyUtils.format(item.purchasePriceCents, currency)}"
                    ),
                    color = StocktakingPalette.TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (item.alertType == StockAlertType.SLOW_MOVING) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "المبيعات آخر $slowMovingDays يوماً: ${StocktakingCalculator.formatQuantity(item.soldQtyRecentWindow)} • آخر بيع: ${formatStocktakingShortDate(item.lastSoldAt)}",
                        "Ventes (${slowMovingDays}j) : ${StocktakingCalculator.formatQuantity(item.soldQtyRecentWindow)} • Dernière vente : ${formatStocktakingShortDate(item.lastSoldAt)}"
                    ),
                    color = StocktakingPalette.Indigo,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EmptyMonitoringCard(text: String) {
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
            Icon(Icons.Filled.Verified, contentDescription = null, tint = StocktakingPalette.Emerald, modifier = Modifier.size(38.dp))
            Text(text = text, color = StocktakingPalette.TextSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}
