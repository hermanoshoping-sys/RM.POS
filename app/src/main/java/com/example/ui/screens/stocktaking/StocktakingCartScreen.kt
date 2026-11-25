package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryEntity
import com.example.data.InventoryItemEntity
import com.example.data.ProductEntity
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

private enum class CartItemFilter { ALL, MATCHING, SHORTAGE, SURPLUS }

private val COL_NAME = 148.dp
private val COL_SYS = 60.dp
private val COL_COUNTED = 66.dp
private val COL_VALUE = 86.dp
private val COL_DIFF = 56.dp
private val COL_IMPACT = 86.dp
private val COL_STATUS = 70.dp
private val COL_ACTIONS = 114.dp

@Composable
internal fun StocktakingCartScreen(
    inventory: InventoryEntity,
    items: List<InventoryItemEntity>,
    liveProductsById: Map<Long, ProductEntity>,
    currency: String,
    lang: String,
    shopName: String,
    onBack: () -> Unit,
    onRecordItemCount: (productId: Long, qty: Double, notes: String?) -> Unit,
    onClearItemCount: (productId: Long) -> Unit,
    onOpenDiscrepancies: () -> Unit
) {
    val context = LocalContext.current
    val status = InventoryStatus.fromCode(inventory.status)
    val isEditable = status.isEditable

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(CartItemFilter.ALL) }
    var itemEditingExactQty by remember { mutableStateOf<InventoryItemEntity?>(null) }

    val countedItems = remember(items) {
        items
            .filter { it.countedQuantity != null }
            .sortedWith(
                compareByDescending<InventoryItemEntity> { it.countedAt ?: 0L }
                    .thenBy { it.productName.lowercase(Locale.ROOT) }
            )
    }

    val cartSummary = remember(countedItems) {
        StocktakingCalculator.computeSessionSummary(
            countedItems.map {
                StocktakingCalculator.ItemSummaryInput(
                    countedQuantity = it.countedQuantity,
                    expectedQuantity = it.effectiveExpectedQuantity,
                    unitCostCents = it.unitCostCents
                )
            }
        )
    }
    val totalCountedUnits = remember(countedItems) {
        StocktakingCalculator.normalizeQuantity(countedItems.sumOf { it.countedQuantity ?: 0.0 })
    }
    val totalCountedPurchaseCents = remember(countedItems) {
        StocktakingReportBuilder.computeCountedPurchaseValueCents(countedItems)
    }

    val filteredCartItems = remember(countedItems, searchQuery, activeFilter) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        countedItems.filter { item ->
            val st = InventoryItemStatus.fromCode(item.status)
            val statusMatch = when (activeFilter) {
                CartItemFilter.ALL -> true
                CartItemFilter.MATCHING -> st == InventoryItemStatus.MATCHING
                CartItemFilter.SHORTAGE -> st == InventoryItemStatus.SHORTAGE
                CartItemFilter.SURPLUS -> st == InventoryItemStatus.SURPLUS
            }
            val queryMatch = q.isEmpty() ||
                item.productName.lowercase(Locale.ROOT).contains(q) ||
                item.barcode.lowercase(Locale.ROOT).contains(q) ||
                item.categoryName.lowercase(Locale.ROOT).contains(q)
            statusMatch && queryMatch
        }
    }

    val filteredTotals = remember(filteredCartItems) {
        val sysSum = StocktakingCalculator.normalizeQuantity(
            filteredCartItems.sumOf { it.effectiveExpectedQuantity }
        )
        val cntSum = StocktakingCalculator.normalizeQuantity(
            filteredCartItems.sumOf { it.countedQuantity ?: 0.0 }
        )
        val diffSum = StocktakingCalculator.normalizeQuantity(
            filteredCartItems.sumOf { it.difference }
        )
        val valCents = StocktakingReportBuilder.computeCountedPurchaseValueCents(filteredCartItems)
        val netCents = filteredCartItems.sumOf { it.discrepancyValueCents }
        CartTableTotals(sysSum, cntSum, valCents, diffSum, netCents)
    }

    val hasCartItems = countedItems.isNotEmpty()
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StocktakingPalette.PageBg)
    ) {
        // 1. Compact Header with Back + Title + Ref/Date + Direct Excel / PDF / Print buttons
        CartCompactHeaderBar(
            inventory = inventory,
            hasCartItems = hasCartItems,
            lang = lang,
            onBack = onBack,
            onExportExcel = {
                StocktakingReportExporter.exportReportXlsx(
                    context = context,
                    inventory = inventory,
                    items = countedItems,
                    currency = currency,
                    lang = lang,
                    shopName = shopName,
                    cartOnly = true
                )
            },
            onExportPdf = {
                StocktakingReportExporter.exportCartPdf(
                    context = context,
                    inventory = inventory,
                    items = countedItems,
                    currency = currency,
                    lang = lang,
                    shopName = shopName
                )
            },
            onPrint = {
                StocktakingReportExporter.printCartPdf(
                    context = context,
                    inventory = inventory,
                    items = countedItems,
                    currency = currency,
                    lang = lang,
                    shopName = shopName
                )
            }
        )

        // 2. Two-Column KPI Summary Table Strip (Value at Purchase Cost | Net Cart Discrepancy)
        CartKpiSummaryTable(
            countedCount = countedItems.size,
            totalCountedUnits = totalCountedUnits,
            totalCountedPurchaseCents = totalCountedPurchaseCents,
            netDiscrepancyCents = cartSummary.netDiscrepancyValueCents,
            currency = currency,
            lang = lang,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )

        // 3. Sleek Slim Search Bar with Inline Barcode Scanner Button (44dp)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = StocktakingPalette.InputBg,
            border = BorderStroke(1.dp, StocktakingPalette.InputBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
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
                            text = Bilingual.tr(lang, "بحث في سلة الجرد...", "Rechercher dans le panier..."),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stocktaking_cart_search_input")
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = StocktakingPalette.TextMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        runCatching {
                            val options = GmsBarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                                .enableAutoZoom()
                                .build()
                            GmsBarcodeScanning.getClient(context, options)
                                .startScan()
                                .addOnSuccessListener { barcode ->
                                    barcode.rawValue?.let { code -> searchQuery = code }
                                }
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("stocktaking_cart_scan_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = Bilingual.tr(lang, "مسح باركود", "Scanner code-barres"),
                        tint = StocktakingPalette.AccentSky,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 4. Four Vibrant Color-Coded Filter Pills (All / Matching / Shortage / Surplus)
        CartColorFilterBar(
            activeFilter = activeFilter,
            totalCount = countedItems.size,
            matchingCount = cartSummary.matchingItemsCount,
            shortageCount = cartSummary.shortageItemsCount,
            surplusCount = cartSummary.surplusItemsCount,
            lang = lang,
            onSelect = { activeFilter = it },
            modifier = Modifier.padding(horizontal = 10.dp)
        )

        Spacer(Modifier.height(6.dp))

        // 5. Full-Height Professional Ledger Table (or Empty State)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = StocktakingPalette.CardBg,
            border = BorderStroke(1.dp, StocktakingPalette.GridLine),
            shadowElevation = 1.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            if (!hasCartItems) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .testTag("stocktaking_cart_list")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .padding(16.dp)
                            .testTag("stocktaking_cart_empty_state")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(StocktakingPalette.SurfaceSubtle)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ShoppingCart,
                                contentDescription = null,
                                tint = StocktakingPalette.TextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            text = Bilingual.tr(lang, "سلة الجرد فارغة حالياً", "Le panier d'inventaire est vide"),
                            color = StocktakingPalette.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "كل منتج تقوم بمسحه أو إدخال كميته يظهر هنا جاهزاً للطباعة والتصدير.",
                                "Chaque produit compté apparaît ici prêt à imprimer ou exporter."
                            ),
                            color = StocktakingPalette.TextMuted,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val baseTableWidth = COL_NAME + COL_SYS + COL_COUNTED + COL_VALUE +
                    COL_DIFF + COL_IMPACT + COL_STATUS + if (isEditable) COL_ACTIONS else 0.dp

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val tableWidth = maxOf(maxWidth, baseTableWidth)
                    val extraNameWidth = (tableWidth - baseTableWidth).coerceAtLeast(0.dp)
                    val effectiveNameWidth = COL_NAME + extraNameWidth

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sticky Ledger Table Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(horizontalScrollState)
                                .background(StocktakingPalette.TableHeaderBg)
                                .height(IntrinsicSize.Min)
                        ) {
                            LedgerHeaderCell(
                                text = Bilingual.tr(lang, "اسم المنتج", "Produit"),
                                width = effectiveNameWidth,
                                align = TextAlign.Start
                            )
                            LedgerHeaderCell(
                                text = Bilingual.tr(lang, "العدد في النظام", "Système"),
                                width = COL_SYS
                            )
                            LedgerHeaderCell(
                                text = Bilingual.tr(lang, "عدد الجرد", "Compté"),
                                width = COL_COUNTED
                            )
                            LedgerHeaderCell(
                                text = Bilingual.tr(lang, "قيمة الجرد", "Valeur"),
                                width = COL_VALUE
                            )
                            LedgerHeaderCell(
                                text = Bilingual.tr(lang, "الفارق", "Écart"),
                                width = COL_DIFF
                            )
                            LedgerHeaderCell(
                                text = Bilingual.tr(lang, "الأثر المالي", "Impact"),
                                width = COL_IMPACT
                            )
                            LedgerHeaderCell(
                                text = Bilingual.tr(lang, "الحالة", "Statut"),
                                width = COL_STATUS,
                                showEndDivider = isEditable
                            )
                            if (isEditable) {
                                LedgerHeaderCell(
                                    text = Bilingual.tr(lang, "تعديل", "Action"),
                                    width = COL_ACTIONS,
                                    showEndDivider = false
                                )
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = StocktakingPalette.GridLine)

                        // Scrollable Table Body Rows
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("stocktaking_cart_list")
                        ) {
                            if (filteredCartItems.isEmpty()) {
                                item(key = "cart_no_filter_match") {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(28.dp)
                                    ) {
                                        Text(
                                            text = Bilingual.tr(
                                                lang,
                                                "لا توجد منتجات مطابقة للبحث أو الفلتر",
                                                "Aucun produit correspondant"
                                            ),
                                            color = StocktakingPalette.TextMuted,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = filteredCartItems,
                                    key = { _, item -> "cart_item_${item.id}" }
                                ) { index, item ->
                                    CartLedgerDataRow(
                                        item = item,
                                        index = index,
                                        nameWidth = effectiveNameWidth,
                                        isEditable = isEditable,
                                        horizontalScrollState = horizontalScrollState,
                                        currency = currency,
                                        lang = lang,
                                        onOpenExactDialog = { if (isEditable) itemEditingExactQty = item },
                                        onQuickDelta = { delta ->
                                            if (isEditable) {
                                                val next = ((item.countedQuantity ?: 0.0) + delta).coerceAtLeast(0.0)
                                                onRecordItemCount(item.productId, next, null)
                                            }
                                        },
                                        onRemoveFromCart = {
                                            if (isEditable) onClearItemCount(item.productId)
                                        }
                                    )
                                }
                            }
                        }

                        // Sticky Ledger Totals Footer Row
                        if (filteredCartItems.isNotEmpty()) {
                            HorizontalDivider(thickness = 1.2.dp, color = StocktakingPalette.GridLine)
                            CartLedgerTotalsRow(
                                count = filteredCartItems.size,
                                totals = filteredTotals,
                                nameWidth = effectiveNameWidth,
                                isEditable = isEditable,
                                horizontalScrollState = horizontalScrollState,
                                currency = currency,
                                lang = lang
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 6. Sticky Bottom Action Bar: Continue Counting (Slate) | Discrepancies & Approve (Green)
        Surface(
            color = StocktakingPalette.CardBg,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStocktakingDarkTheme()) Color(0xFF334155) else Color(0xFF64748B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("stocktaking_cart_continue_btn")
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = Bilingual.tr(lang, "متابعة الجرد", "Continuer"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    enabled = hasCartItems,
                    onClick = onOpenDiscrepancies,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("stocktaking_cart_discrepancies_btn")
                ) {
                    Icon(Icons.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = Bilingual.tr(lang, "الفوارق والاعتماد", "Écarts & Valider"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

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
}

private data class CartTableTotals(
    val systemQty: Double,
    val countedQty: Double,
    val countedValueCents: Long,
    val diffQty: Double,
    val netDiscrepancyCents: Long
)

@Composable
private fun CartCompactHeaderBar(
    inventory: InventoryEntity,
    hasCartItems: Boolean,
    lang: String,
    onBack: () -> Unit,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onPrint: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(StocktakingPalette.NavyDeep, Color(0xFF172554))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(StocktakingPalette.NavySurface)
                    .testTag("stocktaking_cart_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Bilingual.tr(lang, "رجوع", "Retour"),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Bilingual.tr(lang, "سلة الجرد", "Panier d'inventaire"),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${inventory.reference} • ${formatStocktakingShortDate(inventory.createdAt)}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Direct Excel / PDF / Print buttons right in the header bar
            HeaderExportBadgeButton(
                enabled = hasCartItems,
                containerColor = Color(0xFF107C41),
                icon = Icons.Filled.TableChart,
                shortLabel = "XLS",
                contentDescription = Bilingual.tr(lang, "تصدير Excel", "Exporter Excel"),
                testTag = "stocktaking_cart_excel_btn",
                onClick = onExportExcel
            )
            HeaderExportBadgeButton(
                enabled = hasCartItems,
                containerColor = Color(0xFFDC2626),
                icon = Icons.Filled.PictureAsPdf,
                shortLabel = "PDF",
                contentDescription = Bilingual.tr(lang, "تصدير PDF", "Exporter PDF"),
                testTag = "stocktaking_cart_pdf_btn",
                onClick = onExportPdf
            )
            HeaderExportBadgeButton(
                enabled = hasCartItems,
                containerColor = Color(0xFF0284C7),
                icon = Icons.Filled.Print,
                shortLabel = Bilingual.tr(lang, "طباعة", "Impr."),
                contentDescription = Bilingual.tr(lang, "طباعة سلة الجرد", "Imprimer le panier"),
                testTag = "stocktaking_cart_print_btn",
                onClick = onPrint
            )
        }
    }
}

@Composable
private fun HeaderExportBadgeButton(
    enabled: Boolean,
    containerColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    shortLabel: String,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (enabled) containerColor else StocktakingPalette.NavySurface.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) Color.White.copy(alpha = 0.25f) else StocktakingPalette.NavyBorder
        ),
        modifier = Modifier
            .size(width = 48.dp, height = 48.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = shortLabel,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CartKpiSummaryTable(
    countedCount: Int,
    totalCountedUnits: Double,
    totalCountedPurchaseCents: Long,
    netDiscrepancyCents: Long,
    currency: String,
    lang: String,
    modifier: Modifier = Modifier
) {
    val netColor = when {
        netDiscrepancyCents < 0L -> StocktakingPalette.Rose
        netDiscrepancyCents > 0L -> StocktakingPalette.Emerald
        else -> StocktakingPalette.AccentSky
    }
    val signedNet = when {
        netDiscrepancyCents > 0L -> "+${MoneyUtils.format(netDiscrepancyCents, currency)}"
        netDiscrepancyCents < 0L -> MoneyUtils.format(netDiscrepancyCents, currency)
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
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StocktakingPalette.SurfaceSubtle)
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = Bilingual.tr(lang, "قيمة المجرود (شراء)", "Valeur comptée (Achat)"),
                        color = StocktakingPalette.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                VerticalDivider(thickness = 1.dp, color = StocktakingPalette.GridLine)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = Bilingual.tr(lang, "صافي فوارق السلة", "Écart net panier"),
                        color = StocktakingPalette.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = StocktakingPalette.GridLine)

            // Value row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = MoneyUtils.format(totalCountedPurchaseCents, currency),
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "$countedCount منتج • ${StocktakingCalculator.formatQuantity(totalCountedUnits)} وحدة",
                            "$countedCount art. • ${StocktakingCalculator.formatQuantity(totalCountedUnits)} u."
                        ),
                        color = StocktakingPalette.TextMuted,
                        fontSize = 9.5.sp,
                        maxLines = 1
                    )
                }
                VerticalDivider(thickness = 1.dp, color = StocktakingPalette.GridLine)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = signedNet,
                        color = netColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = Bilingual.tr(lang, "بسعر الشراء", "Au coût d'achat"),
                        color = StocktakingPalette.TextMuted,
                        fontSize = 9.5.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CartColorFilterBar(
    activeFilter: CartItemFilter,
    totalCount: Int,
    matchingCount: Int,
    shortageCount: Int,
    surplusCount: Int,
    lang: String,
    onSelect: (CartItemFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        Triple(CartItemFilter.ALL, Bilingual.tr(lang, "الكل", "Tous") to totalCount, Color(0xFF64748B)),
        Triple(CartItemFilter.MATCHING, Bilingual.tr(lang, "مطابق", "Conf.") to matchingCount, Color(0xFF0284C7)),
        Triple(CartItemFilter.SHORTAGE, Bilingual.tr(lang, "عجز", "Manq.") to shortageCount, Color(0xFFDC2626)),
        Triple(CartItemFilter.SURPLUS, Bilingual.tr(lang, "فائض", "Surp.") to surplusCount, Color(0xFF059669))
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        entries.forEach { (filter, labelAndCount, baseColor) ->
            val selected = activeFilter == filter
            val (label, count) = labelAndCount
            Surface(
                onClick = { onSelect(filter) },
                shape = RoundedCornerShape(9.dp),
                color = if (selected) baseColor else baseColor.copy(alpha = 0.14f),
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) baseColor else baseColor.copy(alpha = 0.45f)
                ),
                shadowElevation = if (selected) 2.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .testTag("cart_filter_${filter.name.lowercase(Locale.ROOT)}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$label ($count)",
                        color = if (selected) Color.White else if (isStocktakingDarkTheme()) Color.White else baseColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerHeaderCell(
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showEndDivider) {
            VerticalDivider(thickness = 0.75.dp, color = Color.White.copy(alpha = 0.18f))
        }
    }
}

@Composable
private fun CartLedgerDataRow(
    item: InventoryItemEntity,
    index: Int,
    nameWidth: Dp,
    isEditable: Boolean,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    currency: String,
    lang: String,
    onOpenExactDialog: () -> Unit,
    onQuickDelta: (Double) -> Unit,
    onRemoveFromCart: () -> Unit
) {
    val st = InventoryItemStatus.fromCode(item.status)
    val countedQty = item.countedQuantity ?: 0.0
    val countedValueCents = if (countedQty > 0.0 && item.unitCostCents > 0L) {
        MoneyUtils.multiplyCents(item.unitCostCents, countedQty)
    } else 0L
    val rowBg = if (index % 2 == 0) StocktakingPalette.CardBg else StocktakingPalette.ZebraRowBg
    val impactColor = when (st) {
        InventoryItemStatus.SHORTAGE -> StocktakingPalette.Rose
        InventoryItemStatus.SURPLUS -> StocktakingPalette.Emerald
        InventoryItemStatus.MATCHING -> StocktakingPalette.AccentSky
        InventoryItemStatus.UNCOUNTED -> StocktakingPalette.TextMuted
    }
    val signedImpact = when {
        item.discrepancyValueCents > 0L -> "+${MoneyUtils.format(item.discrepancyValueCents, currency)}"
        item.discrepancyValueCents < 0L -> MoneyUtils.format(item.discrepancyValueCents, currency)
        else -> MoneyUtils.format(0L, currency)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cart_item_card_${item.productId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
                .background(rowBg)
                .clickable(enabled = isEditable, onClick = onOpenExactDialog)
                .height(IntrinsicSize.Min)
                .defaultMinSize(minHeight = 46.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Product Name
            LedgerCellContainer(width = nameWidth, alignment = Alignment.CenterStart) {
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. System Qty
            LedgerCellContainer(width = COL_SYS) {
                Text(
                    text = StocktakingCalculator.formatQuantity(item.effectiveExpectedQuantity),
                    color = StocktakingPalette.TextSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            // 3. Counted Qty (highlighted tap-to-edit pill)
            LedgerCellContainer(width = COL_COUNTED) {
                Surface(
                    color = StocktakingPalette.SelectedCardBg,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, StocktakingPalette.AccentBlue.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = isEditable, onClick = onOpenExactDialog)
                        .testTag("cart_qty_btn_${item.productId}")
                ) {
                    Text(
                        text = StocktakingCalculator.formatQuantity(countedQty),
                        color = StocktakingPalette.AccentBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 4. Counted Purchase Value
            LedgerCellContainer(width = COL_VALUE) {
                Text(
                    text = MoneyUtils.format(countedValueCents, currency),
                    color = StocktakingPalette.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 5. Difference
            LedgerCellContainer(width = COL_DIFF) {
                Text(
                    text = StocktakingCalculator.formatSignedQuantity(item.difference),
                    color = impactColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            // 6. Financial Impact
            LedgerCellContainer(width = COL_IMPACT) {
                Text(
                    text = signedImpact,
                    color = impactColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 7. Status Pill
            LedgerCellContainer(width = COL_STATUS, showEndDivider = isEditable) {
                StocktakingItemStatusBadge(status = st, lang = lang)
            }

            // 8. Quick Row Actions (- / + / Delete) when editable
            if (isEditable) {
                LedgerCellContainer(width = COL_ACTIONS, showEndDivider = false) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { onQuickDelta(-1.0) },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("cart_dec_${item.productId}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RemoveCircleOutline,
                                contentDescription = "-1",
                                tint = StocktakingPalette.TextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = { onQuickDelta(1.0) },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("cart_inc_${item.productId}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddCircleOutline,
                                contentDescription = "+1",
                                tint = StocktakingPalette.AccentBlue,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = onRemoveFromCart,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("cart_remove_${item.productId}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = Bilingual.tr(lang, "إزالة من السلة", "Retirer du panier"),
                                tint = StocktakingPalette.Rose,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.75.dp, color = StocktakingPalette.GridLine)
    }
}

@Composable
private fun CartLedgerTotalsRow(
    count: Int,
    totals: CartTableTotals,
    nameWidth: Dp,
    isEditable: Boolean,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    currency: String,
    lang: String
) {
    val netColor = when {
        totals.netDiscrepancyCents < 0L -> StocktakingPalette.Rose
        totals.netDiscrepancyCents > 0L -> StocktakingPalette.Emerald
        else -> StocktakingPalette.AccentSky
    }
    val signedNet = when {
        totals.netDiscrepancyCents > 0L -> "+${MoneyUtils.format(totals.netDiscrepancyCents, currency)}"
        totals.netDiscrepancyCents < 0L -> MoneyUtils.format(totals.netDiscrepancyCents, currency)
        else -> MoneyUtils.format(0L, currency)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
            .background(StocktakingPalette.SurfaceSubtle)
            .height(IntrinsicSize.Min)
            .defaultMinSize(minHeight = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedgerCellContainer(width = nameWidth, alignment = Alignment.CenterStart) {
            Text(
                text = Bilingual.tr(lang, "المجموع ($count منتج)", "TOTAL ($count art.)"),
                color = StocktakingPalette.TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        LedgerCellContainer(width = COL_SYS) {
            Text(
                text = StocktakingCalculator.formatQuantity(totals.systemQty),
                color = StocktakingPalette.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }
        LedgerCellContainer(width = COL_COUNTED) {
            Text(
                text = StocktakingCalculator.formatQuantity(totals.countedQty),
                color = StocktakingPalette.AccentBlue,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
        LedgerCellContainer(width = COL_VALUE) {
            Text(
                text = MoneyUtils.format(totals.countedValueCents, currency),
                color = StocktakingPalette.TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LedgerCellContainer(width = COL_DIFF) {
            Text(
                text = StocktakingCalculator.formatSignedQuantity(totals.diffQty),
                color = netColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }
        LedgerCellContainer(width = COL_IMPACT) {
            Text(
                text = signedNet,
                color = netColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LedgerCellContainer(width = COL_STATUS, showEndDivider = isEditable) {
            Text(text = "—", color = StocktakingPalette.TextMuted, fontSize = 11.sp)
        }
        if (isEditable) {
            LedgerCellContainer(width = COL_ACTIONS, showEndDivider = false) {
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}

@Composable
private fun LedgerCellContainer(
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
