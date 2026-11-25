package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProductEntity
import com.example.data.SupplierEntity
import com.example.domain.FinancialCalculator
import com.example.ui.screens.catalog.CatalogPassToSaleDialog
import com.example.ui.screens.pos.CartSheetRowData
import com.example.ui.screens.pos.POSCartSheetView
import com.example.ui.screens.pos.POSProductSheetRow
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import com.example.util.cents
import com.example.util.moneyDouble
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

data class PurchaseCartItem(
    val product: ProductEntity,
    var quantity: Double,
    var costPrice: Double
)

@Composable
fun PurchasesScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val suppliersList by viewModel.suppliers.collectAsStateWithLifecycle()
    val categoriesList by viewModel.categories.collectAsStateWithLifecycle()
    val filteredProducts by viewModel.filteredPagedProducts.collectAsStateWithLifecycle()
    val filteredProductsCount by viewModel.filteredProductsCount.collectAsStateWithLifecycle()
    val hasMoreProducts by viewModel.hasMoreFilteredProducts.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    var selectedSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var showPurchaseCartView by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (showPurchaseCartView) {
            showPurchaseCartView = false
        } else {
            onNavigate?.invoke("DASHBOARD")
        }
    }

    LaunchedEffect(searchQuery, selectedCategoryId) {
        viewModel.setProductCategoryFilter(selectedCategoryId)
        viewModel.setProductSearchQuery(searchQuery)
    }

    var purchaseCart by remember { mutableStateOf(listOf<PurchaseCartItem>()) }
    var showSupplierPicker by remember { mutableStateOf(false) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var paidAmountStr by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<PurchaseCartItem?>(null) }
    var selectedProductForPurchase by remember { mutableStateOf<ProductEntity?>(null) }

    val focusRequester = remember { FocusRequester() }
    val scanner = remember {
        runCatching {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .enableAutoZoom()
                .build()
            GmsBarcodeScanning.getClient(context, options)
        }.getOrNull()
    }

    val totalAmountCents = remember(purchaseCart) {
        FinancialCalculator.computeLinesTotalCents(purchaseCart.map { it.costPrice.cents to it.quantity })
    }
    val totalAmount = totalAmountCents.moneyDouble

    val purchaseByProductId = remember(purchaseCart) {
        purchaseCart.associateBy { it.product.id }
    }

    val addOrIncrementProduct: (ProductEntity) -> Unit = { product ->
        val existing = purchaseCart.find { it.product.id == product.id }
        purchaseCart = if (existing != null) {
            purchaseCart.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1) else it }
        } else {
            purchaseCart + PurchaseCartItem(product, 1.0, product.purchasePriceDouble)
        }
    }

    val bgDark = Color(0xFF070E1A)
    val cyanPrimary = Color(0xFF00A0E9)
    val searchBg = Color(0xFF0F172A)

    if (showPurchaseCartView) {
        val cartRows = remember(purchaseCart, lang) {
            purchaseCart.map { item ->
                val isItemReturn = item.quantity < 0.0
                CartSheetRowData(
                    id = item.product.id,
                    productName = if (isItemReturn) {
                        "${item.product.name} (${Bilingual.tr(lang, "مرتجع مورد", "Retour fourn.", "Supplier Return")})"
                    } else {
                        item.product.name
                    },
                    unitPrice = item.costPrice,
                    quantity = item.quantity,
                    lineTotal = FinancialCalculator.computeLineTotalDouble(item.costPrice, item.quantity),
                    isReturn = isItemReturn,
                    testTag = "purchase_cart_row_${item.product.id}"
                )
            }
        }

        POSCartSheetView(
            title = Bilingual.tr(lang, "سلة المشتريات", "Panier d'Achat", "Purchase Cart"),
            colProductHeader = Bilingual.tr(lang, "المنتج", "Produit", "Product"),
            colUnitPriceHeader = Bilingual.tr(lang, "ثمنه", "Prix", "Cost"),
            colQtyHeader = Bilingual.tr(lang, "كمية", "Qté", "Qty"),
            colTotalHeader = Bilingual.tr(lang, "المجموع", "Total", "Total"),
            totalLabel = if (totalAmount < 0.0) {
                Bilingual.tr(lang, "المسترجع", "À récupérer", "Refund")
            } else {
                Bilingual.tr(lang, "المجموع", "Total", "Total")
            },
            confirmButtonLabel = if (totalAmount < 0.0) {
                Bilingual.tr(lang, "تأكيد استرجاع المورد", "Confirmer le Retour", "Confirm Supplier Return")
            } else {
                Bilingual.tr(lang, "تأكيد الفاتورة", "Confirmer la Facture", "Confirm Invoice")
            },
            totalAmount = totalAmount,
            currency = currency,
            lang = lang,
            items = cartRows,
            onBack = { showPurchaseCartView = false },
            onClearCart = { purchaseCart = emptyList() },
            onRowClick = { row ->
                editingItem = purchaseCart.find { it.product.id == row.id }
            },
            onConfirmClick = {
                if (purchaseCart.isEmpty()) {
                    Toast.makeText(
                        context,
                        Bilingual.tr(lang, "سلة المشتريات فارغة!", "Panier vide!", "Purchase cart is empty!"),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    paidAmountStr = String.format(Locale.US, "%.2f", totalAmount)
                    if (selectedSupplier == null) {
                        selectedSupplier = suppliersList.firstOrNull() ?: SupplierEntity(
                            name = Bilingual.tr(lang, "مورد عام / شراء نقدي", "Fournisseur Comptant", "Cash Supplier"),
                            phone = "-",
                            email = "",
                            address = "",
                            debtBalance = 0.0
                        )
                    }
                    showCheckoutDialog = true
                }
            },
            backButtonTestTag = "purchases_cart_back_button",
            confirmButtonTestTag = "purchases_confirm_invoice_button",
            clearButtonTestTag = "purchases_clear_cart_button"
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgDark)
        ) {
            PurchasesTopHeaderBar(
                lang = lang,
                onBack = { onNavigate?.invoke("DASHBOARD") },
                onToggleLang = { viewModel.setLanguage(if (lang == "AR") "FR" else "AR") }
            )

            PurchasesSupplierSelectionRow(
                selectedSupplier = selectedSupplier,
                cyanPrimary = cyanPrimary,
                lang = lang,
                onOpenSupplierPicker = { showSupplierPicker = true },
                onOpenAddSupplier = { showAddSupplierDialog = true },
                onOpenInvoicesLog = { onNavigate?.invoke("PURCHASE_INVOICES") }
            )

            PurchasesSearchAndScanRow(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                focusRequester = focusRequester,
                cyanPrimary = cyanPrimary,
                searchBg = searchBg,
                cartItemCount = purchaseCart.size,
                lang = lang,
                onScanBarcode = {
                    try {
                        val activeScanner = scanner
                        if (activeScanner == null) {
                            Toast.makeText(context, "الماسح غير متاح", Toast.LENGTH_SHORT).show()
                        } else {
                            activeScanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    val scannedVal = barcode.rawValue ?: ""
                                    if (scannedVal.isNotBlank()) {
                                        coroutineScope.launch {
                                            val match = viewModel.repository.getProductByBarcode(scannedVal)
                                            if (match != null) {
                                                addOrIncrementProduct(match)
                                                Toast.makeText(context, "${match.name} +1", Toast.LENGTH_SHORT).show()
                                            } else {
                                                searchQuery = scannedVal
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "فشل المسح: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "الماسح غير متاح", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenCart = { showPurchaseCartView = true }
            )

            // Category filter pills ("الكل" + categories) matching the Sales catalog layout
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "purchase_all_cats") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedCategoryId == null) Color(0xFF2563EB) else Color(0xFF0F172A),
                        border = BorderStroke(1.dp, if (selectedCategoryId == null) Color(0xFF2563EB) else Color(0xFF1E293B)),
                        modifier = Modifier.clickable { selectedCategoryId = null }
                    ) {
                        Text(
                            text = Bilingual.tr(lang, "الكل", "Tous", "All"),
                            color = Color.White,
                            fontWeight = if (selectedCategoryId == null) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
                items(categoriesList, key = { it.id }) { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B)),
                        modifier = Modifier.clickable { selectedCategoryId = cat.id }
                    ) {
                        Text(
                            text = cat.name,
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Full-height 3-column Purchases Products Table: كود بار | المنتجات | ثمن الشراء
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .border(1.dp, Color.Black)
                    .background(Color.White)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    POSProductSheetRow(
                        colBarcode = Bilingual.tr(lang, "كود بار", "Code-barres", "Barcode"),
                        colProduct = Bilingual.tr(lang, "المنتجات", "Produits", "Products"),
                        colPrice = Bilingual.tr(lang, "ثمن الشراء", "Prix Achat", "Cost"),
                        lang = lang,
                        isHeader = true
                    )

                    val productsListState = rememberLazyListState()
                    LaunchedEffect(selectedCategoryId, searchQuery, filteredProducts.firstOrNull()?.id) {
                        if (filteredProducts.isNotEmpty() && productsListState.firstVisibleItemIndex > 0) {
                            productsListState.scrollToItem(0)
                        }
                    }

                    LazyColumn(
                        state = productsListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredProducts,
                            key = { it.id },
                            contentType = { "purchase_product_row" }
                        ) { product ->
                            POSProductSheetRow(
                                colBarcode = product.barcode,
                                colProduct = product.name,
                                colPrice = String.format(Locale.US, "%.2f", product.purchasePriceDouble),
                                lang = lang,
                                isHeader = false,
                                inCartQty = purchaseByProductId[product.id]?.quantity ?: 0.0,
                                onClick = { selectedProductForPurchase = product },
                                modifier = Modifier.testTag("purchase_product_row_${product.id}")
                            )
                        }

                        if (hasMoreProducts) {
                            item(key = "load_more_purchase_products", contentType = "load_more") {
                                LaunchedEffect(filteredProducts.size) { viewModel.loadMoreFilteredProducts() }
                                TextButton(
                                    onClick = { viewModel.loadMoreFilteredProducts() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("purchases_load_more_products_button")
                                ) {
                                    Text(
                                        text = Bilingual.tr(
                                            lang,
                                            "تحميل المزيد (${filteredProducts.size} / $filteredProductsCount)",
                                            "Charger plus (${filteredProducts.size} / $filteredProductsCount)",
                                            "Load more (${filteredProducts.size} / $filteredProductsCount)"
                                        ),
                                        color = cyanPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        val emptyRowsCount = (24 - filteredProducts.size).coerceAtLeast(0)
                        items(
                            count = emptyRowsCount,
                            contentType = { "empty_product_row" }
                        ) {
                            POSProductSheetRow(
                                colBarcode = "",
                                colProduct = "",
                                colPrice = "",
                                lang = lang,
                                isHeader = false
                            )
                        }
                    }
                }
            }
        }
    }

    PurchasesScreenModalsHost(
        context = context,
        viewModel = viewModel,
        lang = lang,
        currency = currency,
        cyanPrimary = cyanPrimary,
        suppliersList = suppliersList,
        selectedSupplier = selectedSupplier,
        onSelectSupplier = { selectedSupplier = it },
        purchaseCart = purchaseCart,
        purchaseByProductId = purchaseByProductId,
        onUpdateCart = { purchaseCart = it },
        totalAmountCents = totalAmountCents,
        totalAmount = totalAmount,
        paidAmountStr = paidAmountStr,
        showSupplierPicker = showSupplierPicker,
        onDismissSupplierPicker = { showSupplierPicker = false },
        onOpenAddSupplierFromPicker = {
            showSupplierPicker = false
            showAddSupplierDialog = true
        },
        editingItem = editingItem,
        onDismissEditingItem = { editingItem = null },
        showCheckoutDialog = showCheckoutDialog,
        onCheckoutSuccess = {
            purchaseCart = emptyList()
            showCheckoutDialog = false
            showPurchaseCartView = false
        },
        onDismissCheckoutDialog = { showCheckoutDialog = false },
        showAddSupplierDialog = showAddSupplierDialog,
        onDismissAddSupplierDialog = { showAddSupplierDialog = false },
        selectedProductForPurchase = selectedProductForPurchase,
        onDismissSelectedProduct = { selectedProductForPurchase = null }
    )
}
