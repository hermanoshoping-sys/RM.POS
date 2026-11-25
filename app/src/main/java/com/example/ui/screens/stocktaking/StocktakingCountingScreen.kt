package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryEntity
import com.example.data.InventoryItemEntity
import com.example.data.ProductEntity
import com.example.domain.stocktaking.BarcodeScanOutcome
import com.example.domain.stocktaking.DiscrepancyFilter
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.InventoryItemStatus
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.domain.stocktaking.StocktakingReportBuilder
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Locale

@Composable
internal fun StocktakingCountingScreen(
    inventory: InventoryEntity,
    items: List<InventoryItemEntity>,
    liveProductsById: Map<Long, ProductEntity>,
    highlightedProductId: Long?,
    pendingDuplicateScan: BarcodeScanOutcome.PromptDuplicate?,
    pendingNotInSessionScan: BarcodeScanOutcome.NotInSession?,
    currency: String,
    lang: String,
    onBack: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onUpdateScanMode: (DuplicateScanMode) -> Unit,
    onRecordItemCount: (productId: Long, qty: Double, notes: String?) -> Unit,
    onClearItemCount: (productId: Long) -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onResolveDuplicateScan: (item: InventoryItemEntity, newQty: Double) -> Unit,
    onDismissDuplicateScan: () -> Unit,
    onAddNotInSessionProduct: (productId: Long) -> Unit,
    onDismissNotInSessionScan: () -> Unit,
    onOpenDiscrepancies: () -> Unit,
    onOpenSessionAdjustments: (() -> Unit)? = null,
    onOpenReport: (() -> Unit)? = null,
    onOpenCart: (() -> Unit)? = null,
    onEditSession: (() -> Unit)? = null,
    onDeleteSession: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val status = InventoryStatus.fromCode(inventory.status)
    val isEditable = status.isEditable
    val scanMode = DuplicateScanMode.fromCode(inventory.duplicateScanMode)

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(DiscrepancyFilter.ALL) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showScanModeDialog by remember { mutableStateOf(false) }
    var useTableView by rememberSaveable { mutableStateOf(false) }
    var itemEditingExactQty by remember { mutableStateOf<InventoryItemEntity?>(null) }

    val availableCategories = remember(items) {
        items.mapNotNull { it.categoryName.takeIf { name -> name.isNotBlank() } }.distinct().sorted()
    }

    val countsByStatus = remember(items) {
        var uncounted = 0
        var matching = 0
        var shortage = 0
        var surplus = 0
        for (it in items) {
            when (InventoryItemStatus.fromCode(it.status)) {
                InventoryItemStatus.UNCOUNTED -> uncounted++
                InventoryItemStatus.MATCHING -> matching++
                InventoryItemStatus.SHORTAGE -> shortage++
                InventoryItemStatus.SURPLUS -> surplus++
            }
        }
        intArrayOf(uncounted, matching, shortage, surplus)
    }

    val countedPurchaseCents = remember(items) {
        StocktakingReportBuilder.computeCountedPurchaseValueCents(items)
    }

    val filteredItems = remember(items, searchQuery, activeFilter, selectedCategoryFilter) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        items.filter { item ->
            val st = InventoryItemStatus.fromCode(item.status)
            val statusMatches = when (activeFilter) {
                DiscrepancyFilter.ALL -> true
                DiscrepancyFilter.UNCOUNTED -> st == InventoryItemStatus.UNCOUNTED
                DiscrepancyFilter.MATCHING -> st == InventoryItemStatus.MATCHING
                DiscrepancyFilter.SHORTAGE -> st == InventoryItemStatus.SHORTAGE
                DiscrepancyFilter.SURPLUS -> st == InventoryItemStatus.SURPLUS
                DiscrepancyFilter.DISCREPANCIES_ONLY -> st == InventoryItemStatus.SHORTAGE || st == InventoryItemStatus.SURPLUS
            }
            val categoryMatches = selectedCategoryFilter == null || item.categoryName == selectedCategoryFilter
            val queryMatches = q.isEmpty() ||
                item.productName.lowercase(Locale.ROOT).contains(q) ||
                item.barcode.lowercase(Locale.ROOT).contains(q) ||
                item.sku.lowercase(Locale.ROOT).contains(q) ||
                item.categoryName.lowercase(Locale.ROOT).contains(q)
            statusMatches && categoryMatches && queryMatches
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(highlightedProductId, filteredItems.size) {
        if (highlightedProductId != null) {
            val idx = filteredItems.indexOfFirst { it.productId == highlightedProductId }
            if (idx >= 0) {
                listState.animateScrollToItem(idx)
            }
        }
    }

    val progress = if (inventory.totalItemsCount > 0) {
        (inventory.countedItemsCount.toFloat() / inventory.totalItemsCount.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val progressPct = (progress * 100f).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StocktakingPalette.PageBg)
    ) {
        // 1. Clean, Uncluttered Executive Header Bar
        StocktakingHeaderBar(
            title = inventory.title.ifBlank { inventory.reference },
            subtitle = "${inventory.reference} • ${formatStocktakingShortDate(inventory.createdAt)}",
            onBack = onBack,
            backTestTag = "counting_back_btn"
        ) {
            // Prominent Inventory Cart Button with Live Badge
            if (onOpenCart != null) {
                Surface(
                    onClick = onOpenCart,
                    color = StocktakingPalette.AccentBlue,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("counting_open_cart_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = Bilingual.tr(lang, "سلة الجرد", "Panier d'inventaire"),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${inventory.countedItemsCount}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Toggle Cards vs Compact Ledger Table View
            IconButton(
                onClick = { useTableView = !useTableView },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StocktakingPalette.NavySurface)
                    .testTag("counting_view_mode_btn")
            ) {
                Icon(
                    imageVector = if (useTableView) Icons.Filled.ViewAgenda else Icons.Filled.TableRows,
                    contentDescription = Bilingual.tr(lang, "تبديل العرض (بطاقات / جدول)", "Basculer vue (Cartes / Tableau)"),
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Overflow Menu for Secondary Actions (Scan Mode, Pause/Resume, Report, Edit, Delete)
            val hasMenuItems = isEditable || onOpenReport != null || onEditSession != null || onDeleteSession != null
            if (hasMenuItems) {
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StocktakingPalette.NavySurface)
                            .testTag("counting_more_menu_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = Bilingual.tr(lang, "خيارات الجرد", "Options d'inventaire"),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        if (isEditable) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        Bilingual.tr(
                                            lang,
                                            "وضع المسح: ${duplicateScanModeShortLabel(scanMode, lang)}",
                                            "Mode scan : ${duplicateScanModeShortLabel(scanMode, lang)}"
                                        )
                                    )
                                },
                                leadingIcon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showScanModeDialog = true
                                },
                                modifier = Modifier.testTag("counting_scan_mode_btn")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (status == InventoryStatus.IN_PROGRESS) {
                                            Bilingual.tr(lang, "إيقاف مؤقت للجلسة", "Mettre en pause")
                                        } else {
                                            Bilingual.tr(lang, "استئناف الجرد", "Reprendre le comptage")
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (status == InventoryStatus.IN_PROGRESS) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onTogglePauseResume()
                                },
                                modifier = Modifier.testTag("counting_pause_resume_btn")
                            )
                        }
                        if (onOpenReport != null) {
                            DropdownMenuItem(
                                text = { Text(Bilingual.tr(lang, "تقرير الجرد والتصدير", "Rapport & Export")) },
                                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenReport()
                                },
                                modifier = Modifier.testTag("counting_open_report_btn")
                            )
                        }
                        if (onEditSession != null) {
                            DropdownMenuItem(
                                text = { Text(Bilingual.tr(lang, "تعديل بيانات الجرد", "Modifier la session")) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onEditSession()
                                },
                                modifier = Modifier.testTag("counting_edit_session_btn")
                            )
                        }
                        if (onDeleteSession != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        Bilingual.tr(lang, "حذف جلسة الجرد", "Supprimer la session"),
                                        color = StocktakingPalette.Rose,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = StocktakingPalette.Rose)
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onDeleteSession()
                                },
                                modifier = Modifier.testTag("counting_delete_session_btn")
                            )
                        }
                    }
                }
            }
        }

        // 2. Read-only Banner if Completed or Cancelled
        if (!isEditable) {
            Surface(
                color = if (status == InventoryStatus.COMPLETED) StocktakingPalette.EmeraldSoft else StocktakingPalette.RoseSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (status == InventoryStatus.COMPLETED) StocktakingPalette.Emerald else StocktakingPalette.Rose,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (status == InventoryStatus.COMPLETED) {
                            Bilingual.tr(
                                lang,
                                "جرد مغلق ومعتمد بواسطة ${inventory.approvedBy ?: "—"} بتاريخ ${formatStocktakingShortDate(inventory.completedAt)}",
                                "Inventaire clôturé par ${inventory.approvedBy ?: "—"} le ${formatStocktakingShortDate(inventory.completedAt)}"
                            )
                        } else {
                            Bilingual.tr(lang, "جلسة جرد ملغاة (للقراءة فقط)", "Session d'inventaire annulée (lecture seule)")
                        },
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // 3. Executive Compact Progress + 2-Column KPI Summary Card
        CountingExecutiveSummaryCard(
            inventory = inventory,
            progress = progress,
            progressPct = progressPct,
            countedPurchaseCents = countedPurchaseCents,
            scanMode = scanMode,
            isEditable = isEditable,
            currency = currency,
            lang = lang,
            onClickScanMode = { showScanModeDialog = true },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )

        // 4. Sleek Slim Search Bar + Prominent Barcode Scan Action (44dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StocktakingPalette.InputBg,
                border = BorderStroke(1.dp, StocktakingPalette.InputBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = StocktakingPalette.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = Bilingual.tr(lang, "بحث بالاسم، الباركود، SKU...", "Nom, code-barres, SKU..."),
                                color = StocktakingPalette.TextMuted,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = StocktakingPalette.InputText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(StocktakingPalette.AccentBlue),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val trimmed = searchQuery.trim()
                                    if (isEditable && trimmed.isNotEmpty() && items.any { it.barcode.equals(trimmed, ignoreCase = true) }) {
                                        onBarcodeScanned(trimmed)
                                        searchQuery = ""
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("counting_search_input")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = StocktakingPalette.TextMuted, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }

            if (isEditable) {
                Button(
                    onClick = {
                        runCatching {
                            val options = GmsBarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                                .enableAutoZoom()
                                .build()
                            GmsBarcodeScanning.getClient(context, options)
                                .startScan()
                                .addOnSuccessListener { barcode ->
                                    barcode.rawValue?.let { code -> onBarcodeScanned(code) }
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.AccentBlue),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("counting_scan_barcode_btn")
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(Bilingual.tr(lang, "مسح", "Scan"), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 5. Vibrant Color-Coded Filter Pills (Matching Inventory Cart Aesthetic) + Category Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusPills = listOf(
                Triple(DiscrepancyFilter.ALL, Bilingual.tr(lang, "الكل (${items.size})", "Tous (${items.size})"), Color(0xFF64748B)),
                Triple(DiscrepancyFilter.UNCOUNTED, Bilingual.tr(lang, "غير معدود (${countsByStatus[0]})", "Restants (${countsByStatus[0]})"), Color(0xFFD97706)),
                Triple(DiscrepancyFilter.MATCHING, Bilingual.tr(lang, "مطابق (${countsByStatus[1]})", "Conformes (${countsByStatus[1]})"), Color(0xFF0284C7)),
                Triple(DiscrepancyFilter.SHORTAGE, Bilingual.tr(lang, "عجز (${countsByStatus[2]})", "Manquants (${countsByStatus[2]})"), Color(0xFFDC2626)),
                Triple(DiscrepancyFilter.SURPLUS, Bilingual.tr(lang, "فائض (${countsByStatus[3]})", "Surplus (${countsByStatus[3]})"), Color(0xFF059669)),
                Triple(DiscrepancyFilter.DISCREPANCIES_ONLY, Bilingual.tr(lang, "فوارق (${countsByStatus[2] + countsByStatus[3]})", "Écarts (${countsByStatus[2] + countsByStatus[3]})"), Color(0xFF4F46E5))
            )

            statusPills.forEach { (flt, label, baseColor) ->
                val selected = activeFilter == flt
                Surface(
                    onClick = { activeFilter = flt },
                    shape = RoundedCornerShape(9.dp),
                    color = if (selected) baseColor else baseColor.copy(alpha = 0.14f),
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) baseColor else baseColor.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("counting_filter_${flt.name.lowercase(Locale.ROOT)}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.White else if (isStocktakingDarkTheme()) Color.White else baseColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            if (availableCategories.size > 1) {
                Box {
                    Surface(
                        onClick = { showCategoryMenu = true },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selectedCategoryFilter != null) StocktakingPalette.AccentBlue else StocktakingPalette.CardBg,
                        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Category,
                                contentDescription = null,
                                tint = if (selectedCategoryFilter != null) Color.White else StocktakingPalette.TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = selectedCategoryFilter ?: Bilingual.tr(lang, "الفئة", "Catégorie"),
                                color = if (selectedCategoryFilter != null) Color.White else StocktakingPalette.TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(Bilingual.tr(lang, "كل الفئات", "Toutes les catégories")) },
                            onClick = {
                                selectedCategoryFilter = null
                                showCategoryMenu = false
                            }
                        )
                        availableCategories.forEach { catName ->
                            DropdownMenuItem(
                                text = { Text(catName) },
                                onClick = {
                                    selectedCategoryFilter = catName
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 6. Product Counting List (Cards Mode OR Compact Ledger Table Mode)
        if (filteredItems.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.SearchOff, contentDescription = null, tint = StocktakingPalette.TextMuted, modifier = Modifier.size(36.dp))
                        Text(
                            text = Bilingual.tr(lang, "لا توجد منتجات مطابقة للتصفية الحالية", "Aucun produit correspondant au filtre"),
                            color = StocktakingPalette.TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        } else if (useTableView) {
            CountingLedgerTableView(
                items = filteredItems,
                liveProductsById = liveProductsById,
                highlightedProductId = highlightedProductId,
                isEditable = isEditable,
                listState = listState,
                currency = currency,
                lang = lang,
                onOpenExactDialog = { target -> if (isEditable) itemEditingExactQty = target },
                onQuickDelta = { target, delta ->
                    if (isEditable) {
                        val next = ((target.countedQuantity ?: 0.0) + delta).coerceAtLeast(0.0)
                        onRecordItemCount(target.productId, next, null)
                    }
                },
                onQuickMatchExpected = { target, liveStock ->
                    if (isEditable) {
                        onRecordItemCount(target.productId, liveStock.coerceAtLeast(0.0), null)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.height(8.dp))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredItems, key = { it.id }, contentType = { "counting_item" }) { item ->
                    val liveStock = liveProductsById[item.productId]?.currentStock ?: item.effectiveExpectedQuantity
                    CountingProductCard(
                        item = item,
                        liveSystemStock = liveStock,
                        isHighlighted = highlightedProductId == item.productId,
                        isEditable = isEditable,
                        currency = currency,
                        lang = lang,
                        onOpenExactDialog = { if (isEditable) itemEditingExactQty = item },
                        onQuickDelta = { delta ->
                            if (isEditable) {
                                val next = ((item.countedQuantity ?: 0.0) + delta).coerceAtLeast(0.0)
                                onRecordItemCount(item.productId, next, null)
                            }
                        },
                        onQuickMatchExpected = {
                            if (isEditable) {
                                onRecordItemCount(item.productId, liveStock.coerceAtLeast(0.0), null)
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(10.dp)) }
            }
        }

        // 7. Sticky Bottom Action Bar with System Navigation Bar Safe Insets
        Surface(
            color = StocktakingPalette.CardBg,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (isEditable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onOpenCart != null) {
                        Button(
                            onClick = onOpenCart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StocktakingPalette.AccentBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("counting_bottom_cart_btn")
                        ) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "سلة الجرد (${inventory.countedItemsCount})",
                                    "Panier (${inventory.countedItemsCount})"
                                ),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = onOpenDiscrepancies,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF16A34A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("counting_open_discrepancies_btn")
                    ) {
                        Icon(Icons.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(lang, "الفوارق والاعتماد", "Écarts & Valider"),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onOpenDiscrepancies,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("counting_open_discrepancies_btn")
                    ) {
                        Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(lang, "ملخص الفوارق", "Résumé écarts"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    if (status == InventoryStatus.COMPLETED && onOpenSessionAdjustments != null) {
                        Button(
                            onClick = onOpenSessionAdjustments,
                            colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.Indigo),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("counting_open_adjustments_btn")
                        ) {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = Bilingual.tr(lang, "سجل التسويات", "Ajustements"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    itemEditingExactQty?.let { target ->
        val liveStock = liveProductsById[target.productId]?.currentStock ?: target.effectiveExpectedQuantity
        ExactQuantityCountDialog(
            item = target,
            liveSystemStock = liveStock,
            currency = currency,
            lang = lang,
            onDismiss = { itemEditingExactQty = null },
            onSave = { qty, notes ->
                onRecordItemCount(target.productId, qty, notes)
                itemEditingExactQty = null
            },
            onClearCount = {
                onClearItemCount(target.productId)
                itemEditingExactQty = null
            }
        )
    }

    pendingDuplicateScan?.let { prompt ->
        DuplicateScanPromptDialog(
            prompt = prompt,
            lang = lang,
            onIncrement = {
                onResolveDuplicateScan(
                    prompt.item,
                    StocktakingCalculator.normalizeQuantity(prompt.existingCountedQuantity + prompt.incrementDelta)
                )
            },
            onReplace = { newQty -> onResolveDuplicateScan(prompt.item, newQty) },
            onDismiss = onDismissDuplicateScan
        )
    }

    pendingNotInSessionScan?.let { notInSession ->
        NotInSessionScanDialog(
            notInSession = notInSession,
            lang = lang,
            onAddAndCount = { onAddNotInSessionProduct(notInSession.catalogProductId) },
            onDismiss = onDismissNotInSessionScan
        )
    }

    if (showScanModeDialog) {
        DuplicateScanModeSelectorDialog(
            currentMode = scanMode,
            lang = lang,
            onSelect = { mode ->
                onUpdateScanMode(mode)
                showScanModeDialog = false
            },
            onDismiss = { showScanModeDialog = false }
        )
    }
}

@Composable
private fun CountingExecutiveSummaryCard(
    inventory: InventoryEntity,
    progress: Float,
    progressPct: Int,
    countedPurchaseCents: Long,
    scanMode: DuplicateScanMode,
    isEditable: Boolean,
    currency: String,
    lang: String,
    onClickScanMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val netCents = inventory.netDiscrepancyValueCents
    val netColor = when {
        netCents < 0L -> StocktakingPalette.Rose
        netCents > 0L -> StocktakingPalette.Emerald
        else -> StocktakingPalette.AccentSky
    }
    val signedNet = when {
        netCents > 0L -> "+${MoneyUtils.format(netCents, currency)}"
        netCents < 0L -> MoneyUtils.format(netCents, currency)
        else -> MoneyUtils.format(0L, currency)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.GridLine),
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top progress row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StocktakingPalette.SurfaceSubtle)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "تقدّم الجرد: ${inventory.countedItemsCount} / ${inventory.totalItemsCount} ($progressPct%)",
                            "Progression : ${inventory.countedItemsCount} / ${inventory.totalItemsCount} ($progressPct%)"
                        ),
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    if (isEditable) {
                        Surface(
                            color = StocktakingPalette.SelectedCardBg,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, StocktakingPalette.AccentBlue.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onClickScanMode)
                                .testTag("counting_scan_mode_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Filled.QrCode2, contentDescription = null, tint = StocktakingPalette.AccentBlue, modifier = Modifier.size(13.dp))
                                Text(
                                    text = duplicateScanModeShortLabel(scanMode, lang),
                                    color = StocktakingPalette.AccentBlue,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = StocktakingPalette.AccentBlue,
                    trackColor = StocktakingPalette.SlateSoft
                )
            }

            HorizontalDivider(thickness = 1.dp, color = StocktakingPalette.GridLine)

            // Bottom 2-column KPI strip matching the Cart's crisp table style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = Bilingual.tr(lang, "قيمة المجرود (شراء)", "Valeur comptée (Achat)"),
                        color = StocktakingPalette.TextMuted,
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                    Text(
                        text = MoneyUtils.format(countedPurchaseCents, currency),
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                VerticalDivider(thickness = 1.dp, color = StocktakingPalette.GridLine)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = Bilingual.tr(lang, "صافي الفوارق (شراء)", "Écart net (Achat)"),
                        color = StocktakingPalette.TextMuted,
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                    Text(
                        text = signedNet,
                        color = netColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
