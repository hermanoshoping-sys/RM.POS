package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import com.example.domain.FinancialCalculator
import com.example.ui.screens.catalog.ProductCatalogDialog
import com.example.ui.viewmodels.PosViewModel
import com.example.util.ActiveSort
import com.example.util.MoneyUtils
import com.example.util.SortDirection
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import java.util.Locale

@Composable
internal fun InventoryQuickActionsRow(
    lang: String,
    productSortState: ActiveSort<ProductSortCol>,
    isMultiSelectMode: Boolean,
    lowStockCount: Int,
    isFilteringLowStock: Boolean,
    onShowPrint: () -> Unit,
    onShowExcel: () -> Unit,
    onShowCatalog: () -> Unit,
    onNavigate: (String) -> Unit,
    onSortChange: (ActiveSort<ProductSortCol>) -> Unit,
    onToggleMultiSelect: () -> Unit,
    onToggleLowStockFilter: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0xFF0F766E),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF2DD4BF)),
            modifier = Modifier.height(36.dp).clickable { onNavigate("STOCKTAKING") }.testTag("inventory_open_stocktaking_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.FactCheck, contentDescription = "Stocktaking", tint = Color.White, modifier = Modifier.size(18.dp))
                Text(if (lang == "AR") "جرد المخزون" else "Inventaire", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(
            color = Color(0xFF1E3A8A),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF3B82F6)),
            modifier = Modifier.height(36.dp).clickable(onClick = onShowPrint).testTag("inventory_print_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Print, contentDescription = "Print", tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
                Text(if (lang == "AR") "طباعة القائمة" else "Imprimer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        ExcelIconBadge(onClick = onShowExcel, modifier = Modifier.testTag("inventory_excel_button"))

        Surface(
            color = Color(0xFFD97706),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFFBBF24)),
            modifier = Modifier.height(36.dp).clickable(onClick = onShowCatalog).testTag("inventory_catalog_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.AutoStories, contentDescription = "Catalog", tint = Color.White, modifier = Modifier.size(17.dp))
                Text(if (lang == "AR") "كاتالوج" else "Catalogue", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(
            color = Color(0xFF6D28D9),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF8B5CF6)),
            modifier = Modifier.height(36.dp).clickable { onNavigate("PRODUCT_FAMILIES") }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Category, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(if (lang == "AR") "عائلة المنتجات" else "Familles", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Surface(
            color = Color(0xFF0284C7),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
            modifier = Modifier.height(36.dp).clickable { onNavigate("PRODUCT_SALES_SEARCH") }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.ManageSearch, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(if (lang == "AR") "سجل المبيعات" else "Audit Ventes", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                modifier = Modifier.height(36.dp).clickable { showSortMenu = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                    Text(
                        if (lang == "AR") "ترتيب: ${productSortState.column.name}" else "Tri: ${productSortState.column.name}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                modifier = Modifier.background(Color(0xFF1E293B))
            ) {
                ProductSortOption.values().forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(if (lang == "AR") opt.titleAr else opt.titleFr, color = Color.White) },
                        onClick = {
                            onSortChange(
                                when (opt) {
                                    ProductSortOption.ID_ASC -> ActiveSort(ProductSortCol.ID, SortDirection.ASCENDING)
                                    ProductSortOption.ID_DESC -> ActiveSort(ProductSortCol.ID, SortDirection.DESCENDING)
                                    ProductSortOption.NAME_ASC -> ActiveSort(ProductSortCol.NAME, SortDirection.ASCENDING)
                                    ProductSortOption.NAME_DESC -> ActiveSort(ProductSortCol.NAME, SortDirection.DESCENDING)
                                    ProductSortOption.PRICE_ASC -> ActiveSort(ProductSortCol.PRICE, SortDirection.ASCENDING)
                                    ProductSortOption.PRICE_DESC -> ActiveSort(ProductSortCol.PRICE, SortDirection.DESCENDING)
                                    ProductSortOption.BARCODE_ASC -> ActiveSort(ProductSortCol.BARCODE, SortDirection.ASCENDING)
                                    ProductSortOption.STOCK_ASC -> ActiveSort(ProductSortCol.STOCK, SortDirection.ASCENDING)
                                }
                            )
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        Surface(
            color = if (isMultiSelectMode) Color(0xFF2563EB) else Color(0xFF065F46),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (isMultiSelectMode) Color(0xFF60A5FA) else Color(0xFF10B981)),
            modifier = Modifier.height(36.dp).clickable(onClick = onToggleMultiSelect)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Checklist, contentDescription = "Multi Select", tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    text = if (isMultiSelectMode) (if (lang == "AR") "إنهاء التحديد" else "Annuler sélection")
                    else (if (lang == "AR") "تحديد متعدد" else "Sélection"),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (lowStockCount > 0) {
            Surface(
                color = if (isFilteringLowStock) Color(0xFFB91C1C) else Color(0xFF7F1D1D),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                modifier = Modifier.height(36.dp).clickable(onClick = onToggleLowStockFilter)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(16.dp))
                    Text(
                        if (lang == "AR") "نواقص ($lowStockCount)" else "Alertes ($lowStockCount)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun InventoryScreenDialogs(
    viewModel: PosViewModel,
    lang: String,
    currency: String,
    productsList: List<ProductEntity>,
    catsList: List<CategoryEntity>,
    scanner: GmsBarcodeScanner,
    searchVal: String,
    selectedProductForAction: ProductEntity?,
    onSelectProductForAction: (ProductEntity?) -> Unit,
    productToAdjustStock: ProductEntity?,
    onSelectProductToAdjustStock: (ProductEntity?) -> Unit,
    productToDelete: ProductEntity?,
    onSelectProductToDelete: (ProductEntity?) -> Unit,
    showBulkDeleteConfirm: Boolean,
    selectedProductIds: Set<Long>,
    onDismissBulkDelete: () -> Unit,
    onBulkDeleted: () -> Unit,
    productPendingEdit: ProductEntity?,
    onSelectProductPendingEdit: (ProductEntity?) -> Unit,
    isFormDialogVisible: Boolean,
    productBeingEdited: ProductEntity?,
    onOpenEditForm: (ProductEntity) -> Unit,
    onDismissForm: () -> Unit,
    showPrintListDialog: Boolean,
    onDismissPrintList: () -> Unit,
    showExcelDialog: Boolean,
    onDismissExcel: () -> Unit,
    showCatalogView: Boolean,
    onDismissCatalog: () -> Unit,
    onNavigate: (String) -> Unit
) {
    selectedProductForAction?.let { prod ->
        val categoryNameStr = prod.categoryName.ifBlank { catsList.find { it.id == prod.categoryId }?.name ?: "-" }
        ProductActionDialog(
            productName = prod.name,
            barcode = prod.barcode.ifBlank { "-" },
            sellPriceStr = MoneyUtils.format(prod.sellPrice, currency),
            costPriceStr = MoneyUtils.format(prod.purchasePrice, currency),
            currentStockStr = "${prod.currentStock} ${prod.unit}",
            categoryName = categoryNameStr,
            onEdit = {
                if (viewModel.requirePasswordOnEdit.value) {
                    onSelectProductPendingEdit(prod)
                } else {
                    onOpenEditForm(prod)
                }
                onSelectProductForAction(null)
            },
            onSellOrAdd = {
                viewModel.addToCart(prod)
                onSelectProductForAction(null)
                onNavigate("POS")
            },
            onStockAdjust = {
                onSelectProductToAdjustStock(prod)
                onSelectProductForAction(null)
            },
            onAuditHistory = {
                onSelectProductForAction(null)
                onNavigate("PRODUCT_SALES_SEARCH")
            },
            onDelete = {
                onSelectProductToDelete(prod)
                onSelectProductForAction(null)
            },
            onDismiss = { onSelectProductForAction(null) }
        )
    }

    productToAdjustStock?.let { prod ->
        InventoryStockAdjustDialog(prod = prod, lang = lang, viewModel = viewModel, onDismiss = { onSelectProductToAdjustStock(null) })
    }
    productToDelete?.let { prod ->
        InventoryDeleteProductDialog(prod = prod, lang = lang, viewModel = viewModel, onDismiss = { onSelectProductToDelete(null) })
    }
    if (showBulkDeleteConfirm) {
        InventoryBulkDeleteDialog(
            selectedProductIds = selectedProductIds,
            lang = lang,
            viewModel = viewModel,
            onDeleted = onBulkDeleted,
            onDismiss = onDismissBulkDelete
        )
    }
    productPendingEdit?.let { prod ->
        InventoryEditPasswordDialog(
            prod = prod,
            lang = lang,
            viewModel = viewModel,
            onVerified = {
                onOpenEditForm(prod)
                onSelectProductPendingEdit(null)
            },
            onDismiss = { onSelectProductPendingEdit(null) }
        )
    }
    if (isFormDialogVisible) {
        InventoryProductFormDialog(
            initialProduct = productBeingEdited,
            catsList = catsList,
            scanner = scanner,
            lang = lang,
            viewModel = viewModel,
            onDismiss = onDismissForm
        )
    }

    if (showPrintListDialog) {
        val exportList = remember(productsList, searchVal) {
            when {
                searchVal == "!low_stock" -> productsList.filter { it.currentStock <= it.minStock }
                searchVal.isBlank() -> productsList
                else -> productsList.filter { it.name.contains(searchVal, ignoreCase = true) || it.barcode.contains(searchVal, ignoreCase = true) }
            }
        }
        val rows = exportList.map { p ->
            listOf(p.name, String.format(Locale.US, "%.0f", p.currentStock), String.format(Locale.US, "%.2f", p.sellPriceDouble))
        }
        val totalValuationCents = FinancialCalculator.sumCents(exportList.map { MoneyUtils.multiplyCents(it.sellPrice, it.currentStock) })
        val summaryLines = listOf(
            Pair(if (lang == "AR") "إجمالي الأصناف:" else "Total Articles:", "${exportList.size}"),
            Pair(if (lang == "AR") "إجمالي قيمة المخزون:" else "Valeur du stock:", MoneyUtils.format(totalValuationCents, currency))
        )

        UniversalPrintListDialog(
            title = if (lang == "AR") "تقرير جرد المنتجات والمخزون" else "INVENTAIRE DU STOCK",
            subtitle = if (lang == "AR") "جميع المنتجات" else "Tous les produits",
            headers = listOf(if (lang == "AR") "الصنف" else "Article", if (lang == "AR") "الكمية" else "Qte", if (lang == "AR") "السعر" else "Prix"),
            rows = rows,
            summaryLines = summaryLines,
            viewModel = viewModel,
            lang = lang,
            onDismiss = onDismissPrintList
        )
    }

    if (showExcelDialog) {
        val exportList = remember(productsList, searchVal) {
            when {
                searchVal == "!low_stock" -> productsList.filter { it.currentStock <= it.minStock }
                searchVal.isBlank() -> productsList
                else -> productsList.filter { it.name.contains(searchVal, ignoreCase = true) || it.barcode.contains(searchVal, ignoreCase = true) }
            }
        }
        ExcelImportExportDialog(
            viewModel = viewModel,
            productsList = productsList,
            filteredList = exportList,
            categoriesList = catsList,
            lang = lang,
            onDismiss = onDismissExcel
        )
    }

    if (showCatalogView) {
        ProductCatalogDialog(
            viewModel = viewModel,
            lang = lang,
            onDismiss = onDismissCatalog,
            onProductDetailsClick = { prod -> onSelectProductForAction(prod) },
            onNavigate = onNavigate
        )
    }
}
