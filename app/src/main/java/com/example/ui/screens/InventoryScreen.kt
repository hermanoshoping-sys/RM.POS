package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodels.PosViewModel
import com.example.util.ActiveSort
import com.example.util.SortDirection
import com.example.util.SortingComparators
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Locale

@Composable
fun InventoryScreen(viewModel: PosViewModel, lang: String, onNavigate: (String) -> Unit) {
    BackHandler { onNavigate("DASHBOARD") }

    val productsList by viewModel.products.collectAsStateWithLifecycle()
    val pagedProducts by viewModel.filteredPagedProducts.collectAsStateWithLifecycle()
    val filteredCount by viewModel.filteredProductsCount.collectAsStateWithLifecycle()
    val hasMoreProducts by viewModel.hasMoreFilteredProducts.collectAsStateWithLifecycle()
    val catsList by viewModel.categories.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scanner = remember {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    val pendingLowStock by viewModel.pendingLowStockFilter.collectAsStateWithLifecycle()
    var searchVal by remember {
        mutableStateOf(if (viewModel.consumePendingLowStockFilter()) "!low_stock" else "")
    }
    LaunchedEffect(pendingLowStock) {
        if (pendingLowStock && viewModel.consumePendingLowStockFilter()) {
            searchVal = "!low_stock"
        }
    }
    LaunchedEffect(searchVal) {
        viewModel.setProductSearchQuery(if (searchVal == "!low_stock") "" else searchVal)
    }

    var productBeingEdited by remember { mutableStateOf<com.example.data.ProductEntity?>(null) }
    var isFormDialogVisible by remember { mutableStateOf(false) }
    var showPrintListDialog by remember { mutableStateOf(false) }
    var showExcelDialog by remember { mutableStateOf(false) }
    var showCatalogView by remember { mutableStateOf(false) }

    var selectedProductForAction by remember { mutableStateOf<com.example.data.ProductEntity?>(null) }
    var productToAdjustStock by remember { mutableStateOf<com.example.data.ProductEntity?>(null) }
    var productToDelete by remember { mutableStateOf<com.example.data.ProductEntity?>(null) }
    var productPendingEdit by remember { mutableStateOf<com.example.data.ProductEntity?>(null) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedProductIds by remember { mutableStateOf(setOf<Long>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var productSortState by remember { mutableStateOf(ActiveSort(ProductSortCol.NAME, SortDirection.ASCENDING)) }

    val baseSource = remember(productsList, pagedProducts, searchVal) {
        if (searchVal == "!low_stock") {
            productsList.filter { it.currentStock <= it.minStock }
        } else {
            pagedProducts
        }
    }

    val sortedProducts = remember(baseSource, productSortState) {
        val sorted = when (productSortState.column) {
            ProductSortCol.BARCODE -> baseSource.sortedWith { p1, p2 -> SortingComparators.compareBarcodes(p1.barcode, p2.barcode) }
            ProductSortCol.NAME -> baseSource.sortedWith { p1, p2 -> SortingComparators.compareNames(p1.name, p2.name) }
            ProductSortCol.PRICE -> baseSource.sortedWith { p1, p2 -> SortingComparators.compareLongs(p1.sellPrice, p2.sellPrice) }
            ProductSortCol.STOCK -> baseSource.sortedWith { p1, p2 -> SortingComparators.compareDoubles(p1.currentStock, p2.currentStock) }
            ProductSortCol.ID -> baseSource.sortedWith { p1, p2 -> SortingComparators.compareLongs(p1.id, p2.id) }
        }
        if (productSortState.direction == SortDirection.DESCENDING) sorted.reversed() else sorted
    }

    val lowStockCount = remember(productsList) { productsList.count { it.currentStock <= it.minStock } }
    val displayCount = if (searchVal == "!low_stock") sortedProducts.size else filteredCount

    Scaffold(
        containerColor = Color(0xFF0F172A),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    productBeingEdited = null
                    isFormDialogVisible = true
                },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("inventory_add_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Product", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0F172A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onNavigate("DASHBOARD") },
                    modifier = Modifier.size(48.dp).background(Color(0xFF1E293B), CircleShape).testTag("inventory_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                Text(
                    text = if (lang == "AR") "المنتجات والمخزون" else "Stock & Produits",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text(
                        text = "$displayCount " + if (lang == "AR") "منتج" else "prod.",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            InventoryQuickActionsRow(
                lang = lang,
                productSortState = productSortState,
                isMultiSelectMode = isMultiSelectMode,
                lowStockCount = lowStockCount,
                isFilteringLowStock = searchVal == "!low_stock",
                onShowPrint = { showPrintListDialog = true },
                onShowExcel = { showExcelDialog = true },
                onShowCatalog = { showCatalogView = true },
                onNavigate = onNavigate,
                onSortChange = { productSortState = it },
                onToggleMultiSelect = {
                    isMultiSelectMode = !isMultiSelectMode
                    if (!isMultiSelectMode) selectedProductIds = emptySet()
                },
                onToggleLowStockFilter = {
                    searchVal = if (searchVal == "!low_stock") "" else "!low_stock"
                }
            )

            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color(0xFF94A3B8), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = searchVal,
                        onValueChange = { searchVal = it },
                        modifier = Modifier.weight(1f).testTag("inventory_search_input"),
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        decorationBox = { innerTextField ->
                            if (searchVal.isEmpty()) {
                                Text(if (lang == "AR") "ابحث عن منتج" else "Rechercher...", color = Color(0xFF94A3B8), fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )

                    if (searchVal.isNotEmpty()) {
                        IconButton(onClick = { searchVal = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    BarcodeScannerSearchBadge(onClick = {
                        scanner.startScan().addOnSuccessListener { barcode ->
                            barcode.rawValue?.let { searchVal = it }
                        }
                    })
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isMultiSelectMode) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (lang == "AR") "المحدد: ${selectedProductIds.size}" else "Sélectionnés : ${selectedProductIds.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    selectedProductIds = if (selectedProductIds.size == sortedProducts.size && sortedProducts.isNotEmpty()) emptySet() else sortedProducts.map { it.id }.toSet()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = if (selectedProductIds.size == sortedProducts.size && sortedProducts.isNotEmpty()) (if (lang == "AR") "إلغاء الكل" else "Désélectionner") else (if (lang == "AR") "تحديد الكل" else "Tout sélectionner"),
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { showBulkDeleteConfirm = true },
                                enabled = selectedProductIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), disabledContainerColor = Color(0xFF475569)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (lang == "AR") "حذف (${selectedProductIds.size})" else "Supprimer (${selectedProductIds.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { isMultiSelectMode = false; selectedProductIds = emptySet() }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = Color.White,
                border = BorderStroke(1.dp, Color.Black)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val minTableWidth = 460.dp
                    val tableWidth = if (maxWidth > minTableWidth) maxWidth else minTableWidth
                    val horizontalScroll = rememberScrollState()
                    Box(modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScroll)) {
                        Column(modifier = Modifier.width(tableWidth).fillMaxHeight()) {
                            val stockArrow = if (productSortState.column == ProductSortCol.STOCK) productSortState.direction.symbol else ""
                            val priceArrow = if (productSortState.column == ProductSortCol.PRICE) productSortState.direction.symbol else ""
                            val nameArrow = if (productSortState.column == ProductSortCol.NAME) productSortState.direction.symbol else ""
                            val barcodeArrow = if (productSortState.column == ProductSortCol.BARCODE) productSortState.direction.symbol else ""

                            InventoryLedgerTableRow(
                                colStock = (if (lang == "AR") "المخزون" else "Stock") + stockArrow,
                                colPrice = (if (lang == "AR") "ثمن" else "Prix") + priceArrow,
                                colProduct = (if (lang == "AR") "المنتج" else "Produit") + nameArrow,
                                colBarcode = (if (lang == "AR") "كود بار" else "Code-barres") + barcodeArrow,
                                isHeader = true,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = selectedProductIds.size == sortedProducts.size && sortedProducts.isNotEmpty(),
                                onToggleSelect = {
                                    selectedProductIds = if (selectedProductIds.size == sortedProducts.size && sortedProducts.isNotEmpty()) emptySet() else sortedProducts.map { it.id }.toSet()
                                },
                                onStockClick = { productSortState = productSortState.toggleOrSwitch(ProductSortCol.STOCK) },
                                onPriceClick = { productSortState = productSortState.toggleOrSwitch(ProductSortCol.PRICE) },
                                onProductClick = { productSortState = productSortState.toggleOrSwitch(ProductSortCol.NAME) },
                                onBarcodeClick = { productSortState = productSortState.toggleOrSwitch(ProductSortCol.BARCODE) }
                            )

                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                items(sortedProducts, key = { it.id }, contentType = { "inventory_product_row" }) { prod ->
                                    val stockStr = if (prod.currentStock % 1.0 == 0.0) {
                                        String.format(Locale.US, "%.0f", prod.currentStock)
                                    } else {
                                        String.format(Locale.US, "%.2f", prod.currentStock)
                                    }
                                    val priceStr = String.format(Locale.US, "%.2f", prod.sellPriceDouble)

                                    InventoryLedgerTableRow(
                                        colStock = stockStr,
                                        colPrice = priceStr,
                                        colProduct = prod.name,
                                        colBarcode = prod.barcode,
                                        isHeader = false,
                                        isNegativeStock = prod.currentStock < 0.0,
                                        isMultiSelectMode = isMultiSelectMode,
                                        isSelected = selectedProductIds.contains(prod.id),
                                        onToggleSelect = {
                                            selectedProductIds = if (selectedProductIds.contains(prod.id)) selectedProductIds - prod.id else selectedProductIds + prod.id
                                        },
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                selectedProductIds = if (selectedProductIds.contains(prod.id)) selectedProductIds - prod.id else selectedProductIds + prod.id
                                            } else {
                                                selectedProductForAction = prod
                                            }
                                        }
                                    )
                                }

                                if (hasMoreProducts && searchVal != "!low_stock") {
                                    item(key = "inventory_load_more", contentType = "load_more_row") {
                                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F5F9)).padding(8.dp), contentAlignment = Alignment.Center) {
                                            OutlinedButton(
                                                onClick = { viewModel.loadMoreFilteredProducts() },
                                                modifier = Modifier.height(48.dp).testTag("inventory_load_more_button")
                                            ) {
                                                Icon(Icons.Filled.ExpandMore, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    if (lang == "AR") "عرض المزيد (${sortedProducts.size} / $filteredCount)"
                                                    else "Charger plus (${sortedProducts.size} / $filteredCount)"
                                                )
                                            }
                                        }
                                    }
                                }

                                val emptyRowCount = (35 - sortedProducts.size).coerceAtLeast(15)
                                items(emptyRowCount, contentType = { "inventory_empty_row" }) {
                                    InventoryLedgerTableRow(
                                        colStock = "", colPrice = "", colProduct = "", colBarcode = "", isHeader = false,
                                        onClick = {
                                            productBeingEdited = null
                                            isFormDialogVisible = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    InventoryScreenDialogs(
        viewModel = viewModel,
        lang = lang,
        currency = currency,
        productsList = productsList,
        catsList = catsList,
        scanner = scanner,
        searchVal = searchVal,
        selectedProductForAction = selectedProductForAction,
        onSelectProductForAction = { selectedProductForAction = it },
        productToAdjustStock = productToAdjustStock,
        onSelectProductToAdjustStock = { productToAdjustStock = it },
        productToDelete = productToDelete,
        onSelectProductToDelete = { productToDelete = it },
        showBulkDeleteConfirm = showBulkDeleteConfirm,
        selectedProductIds = selectedProductIds,
        onDismissBulkDelete = { showBulkDeleteConfirm = false },
        onBulkDeleted = {
            showBulkDeleteConfirm = false
            isMultiSelectMode = false
            selectedProductIds = emptySet()
        },
        productPendingEdit = productPendingEdit,
        onSelectProductPendingEdit = { productPendingEdit = it },
        isFormDialogVisible = isFormDialogVisible,
        productBeingEdited = productBeingEdited,
        onOpenEditForm = { prod ->
            productBeingEdited = prod
            isFormDialogVisible = true
        },
        onDismissForm = { isFormDialogVisible = false },
        showPrintListDialog = showPrintListDialog,
        onDismissPrintList = { showPrintListDialog = false },
        showExcelDialog = showExcelDialog,
        onDismissExcel = { showExcelDialog = false },
        showCatalogView = showCatalogView,
        onDismissCatalog = { showCatalogView = false },
        onNavigate = onNavigate
    )
}
