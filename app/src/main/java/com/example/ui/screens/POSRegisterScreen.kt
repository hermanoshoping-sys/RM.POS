package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CustomerEntity
import com.example.data.ProductEntity
import com.example.ui.screens.catalog.CatalogPassToSaleDialog
import com.example.ui.screens.catalog.ProductCatalogDialog
import com.example.ui.screens.pos.*
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.CartItem
import com.example.ui.viewmodels.PosViewModel
import com.example.util.GeoFenceResult
import com.example.util.GeoLocationHelper
import com.example.util.MoneyUtils
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

// --- 4. POS REGISTER CASHIER SCREEN ---
@Composable
fun POSRegisterScreen(viewModel: PosViewModel, lang: String, onNavigate: (String) -> Unit) {
    val itemsList by viewModel.products.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scanner = remember {
        runCatching {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .enableAutoZoom()
                .build()
            GmsBarcodeScanning.getClient(context, options)
        }.getOrNull()
    }

    val catsList by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCatId by viewModel.selectedCategoryIdForFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val afterDiscount by viewModel.cartTotalAfterGlobalDiscount.collectAsStateWithLifecycle()
    val storeCurrency by viewModel.currency.collectAsStateWithLifecycle()

    var showSalesCartView by rememberSaveable { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showCatalogModal by remember { mutableStateOf(false) }
    var editingCartItem by remember { mutableStateOf<CartItem?>(null) }
    var selectedProductForSale by remember { mutableStateOf<ProductEntity?>(null) }

    val unrecognizedBarcode by viewModel.unrecognizedScannedBarcode.collectAsStateWithLifecycle()
    var showManualBarcodeInput by remember { mutableStateOf(false) }

    val customersList by viewModel.customers.collectAsStateWithLifecycle()
    val selectedPartnerId by viewModel.selectedPartnerClientId.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var showCustomerPickerDialog by remember { mutableStateOf(false) }
    var posGeoBlockedResult by remember { mutableStateOf<GeoFenceResult?>(null) }
    var posGeoBlockedCustomerName by remember { mutableStateOf("") }
    var posGeoBlockedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    BackHandler(enabled = showSalesCartView) {
        showSalesCartView = false
    }

    val filteredProducts = remember(itemsList, selectedCatId, searchQuery) {
        itemsList.filter { p ->
            val matchCat = if (selectedCatId == null) true else p.categoryId == selectedCatId
            val matchSearch = if (searchQuery.isEmpty()) true else {
                p.name.contains(searchQuery, ignoreCase = true) || p.barcode.contains(searchQuery)
            }
            matchCat && matchSearch
        }
    }

    val cartByProductId = remember(cart) {
        cart.associateBy { it.product.id }
    }
    val canModifyPrice = remember(currentUser) {
        currentUser == null || currentUser?.isManager == true || currentUser?.canModifyPrice == true
    }

    val selectedCustomer = customersList.find { it.id == selectedPartnerId }

    if (showSalesCartView) {
        val cartRows = remember(cart, lang) {
            cart.map { item ->
                val isItemReturn = item.quantity < 0
                CartSheetRowData(
                    id = item.product.id,
                    productName = if (isItemReturn) {
                        "${item.product.name} (${Bilingual.tr(lang, "مرتجع", "Retour", "Return")})"
                    } else {
                        item.product.name
                    },
                    unitPrice = item.unitPrice,
                    quantity = item.quantity,
                    lineTotal = item.totalAfterDiscount,
                    isReturn = isItemReturn,
                    testTag = "pos_cart_row_${item.product.id}"
                )
            }
        }

        POSCartSheetView(
            title = Bilingual.tr(lang, "سلة المبيعات", "Panier de Vente", "Sales Cart"),
            colProductHeader = Bilingual.tr(lang, "المنتج", "Produit", "Product"),
            colUnitPriceHeader = Bilingual.tr(lang, "ثمنه", "Prix U", "Unit"),
            colQtyHeader = Bilingual.tr(lang, "كمية", "Qté", "Qty"),
            colTotalHeader = Bilingual.tr(lang, "المجموع", "Total", "Total"),
            totalLabel = if (afterDiscount < 0.0) {
                Bilingual.tr(lang, "المسترجع", "À rembourser", "Refund")
            } else {
                Bilingual.tr(lang, "المجموع", "Total", "Total")
            },
            confirmButtonLabel = if (afterDiscount < 0.0) {
                Bilingual.tr(lang, "تأكيد الاسترجاع", "Confirmer le Retour", "Confirm Return")
            } else {
                Bilingual.tr(lang, "تأكيد البيع", "Confirmer la Vente", "Confirm Sale")
            },
            totalAmount = afterDiscount,
            currency = storeCurrency,
            lang = lang,
            items = cartRows,
            onBack = { showSalesCartView = false },
            onClearCart = { viewModel.clearCart() },
            onRowClick = { row ->
                editingCartItem = cart.find { it.product.id == row.id }
            },
            onConfirmClick = {
                if (cart.isNotEmpty()) {
                    viewModel.receivedCash.value = afterDiscount
                    showCheckoutDialog = true
                } else {
                    Toast.makeText(context, context.getString(R.string.empty_cart), Toast.LENGTH_SHORT).show()
                }
            },
            backButtonTestTag = "pos_cart_back_button",
            confirmButtonTestTag = "pos_confirm_sale_button",
            clearButtonTestTag = "pos_clear_cart_button"
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070E1A))
        ) {
            POSRegisterTopHeader(
                onOpenCatalog = { showCatalogModal = true },
                onOpenInvoices = { onNavigate("SALES_INVOICES") },
                onBackDashboard = { onNavigate("DASHBOARD") }
            )

            POSRegisterCustomerAndFilterSection(
                selectedCustomer = selectedCustomer,
                searchQuery = searchQuery,
                catsList = catsList,
                selectedCatId = selectedCatId,
                cartItemCount = cart.size,
                scanner = scanner,
                lang = lang,
                onChangeCustomerClick = { showCustomerPickerDialog = true },
                onSearchChange = { viewModel.updateSearchQuery(it) },
                onBarcodeScanned = { viewModel.onBarcodeScanned(it) },
                onOpenManualBarcode = { showManualBarcodeInput = true },
                onOpenCart = { showSalesCartView = true },
                onSelectCategory = { viewModel.selectCategoryFilter(it) },
                onOpenFamilies = { onNavigate("PRODUCT_FAMILIES") }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Full-height 3-column Catalog Products Table: كود بار | المنتجات | ثمن
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .border(1.dp, Color.Black)
                    .background(Color.White)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    POSProductSheetRow(
                        colBarcode = Bilingual.tr(lang, "كود بار", "Code-barres", "Barcode"),
                        colProduct = Bilingual.tr(lang, "المنتجات", "Produits", "Products"),
                        colPrice = Bilingual.tr(lang, "ثمن", "Prix", "Price"),
                        lang = lang,
                        isHeader = true
                    )

                    val listState = rememberLazyListState()
                    LaunchedEffect(selectedCatId, searchQuery, filteredProducts.firstOrNull()?.id) {
                        if (filteredProducts.isNotEmpty() && listState.firstVisibleItemIndex > 0) {
                            listState.scrollToItem(0)
                        }
                    }

                    val emptyRowsCount = (24 - filteredProducts.size).coerceAtLeast(0)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProducts, key = { it.id }) { prod ->
                            POSProductSheetRow(
                                colBarcode = prod.barcode,
                                colProduct = prod.name,
                                colPrice = String.format(Locale.US, "%.2f", prod.sellPriceDouble),
                                lang = lang,
                                isHeader = false,
                                inCartQty = cartByProductId[prod.id]?.quantity ?: 0.0,
                                onClick = { selectedProductForSale = prod },
                                modifier = Modifier.testTag("pos_product_row_${prod.id}")
                            )
                        }
                        items(count = emptyRowsCount) {
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

    if (showCheckoutDialog) {
        CheckoutPayDialog(
            viewModel = viewModel,
            grandTotal = afterDiscount,
            lang = lang,
            onDismiss = {
                showCheckoutDialog = false
                if (cart.isEmpty()) {
                    showSalesCartView = false
                }
            }
        )
    }

    if (showCatalogModal) {
        ProductCatalogDialog(
            viewModel = viewModel,
            lang = lang,
            onDismiss = { showCatalogModal = false },
            onNavigate = onNavigate
        )
    }

    if (showCustomerPickerDialog) {
        POSCustomerPickerDialog(
            viewModel = viewModel,
            customersList = customersList,
            selectedPartnerId = selectedPartnerId,
            currentUser = currentUser,
            storeCurrency = storeCurrency,
            lang = lang,
            onGeoBlocked = { customer, result ->
                posGeoBlockedResult = result
                posGeoBlockedCustomerName = customer.name
                posGeoBlockedCustomer = customer
            },
            onDismiss = { showCustomerPickerDialog = false }
        )
    }

    posGeoBlockedResult?.let { blockedResult ->
        CustomerGeoBlockedDialog(
            customerName = posGeoBlockedCustomerName,
            result = blockedResult,
            lang = lang,
            onRetry = {
                val target = posGeoBlockedCustomer
                if (target != null) {
                    coroutineScope.launch {
                        val check = GeoLocationHelper.verifyCustomerLocationForInvoice(
                            context = context,
                            customer = target,
                            currentUser = currentUser,
                            lang = lang
                        )
                        if (check.isAllowed) {
                            posGeoBlockedResult = null
                            viewModel.selectedPartnerClientId.value = target.id
                            showCustomerPickerDialog = false
                        } else {
                            posGeoBlockedResult = check
                        }
                    }
                } else {
                    posGeoBlockedResult = null
                }
            },
            onDismiss = { posGeoBlockedResult = null }
        )
    }

    editingCartItem?.let { currentItem ->
        POSCartItemEditDialog(
            currentItem = currentItem,
            storeCurrency = storeCurrency,
            lang = lang,
            onUpdateCart = { q, p, d ->
                viewModel.updateCartQuantity(currentItem.product.id, q)
                viewModel.updateCartPrice(currentItem.product.id, p)
                viewModel.updateCartLineDiscount(currentItem.product.id, d)
            },
            onRemoveFromCart = { viewModel.removeFromCart(currentItem.product.id) },
            onDismiss = { editingCartItem = null }
        )
    }

    if (showManualBarcodeInput) {
        POSManualBarcodeDialog(
            lang = lang,
            onSubmitBarcode = { viewModel.onBarcodeScanned(it) },
            onDismiss = { showManualBarcodeInput = false }
        )
    }

    unrecognizedBarcode?.let { barcode ->
        POSUnrecognizedBarcodeDialog(
            barcode = barcode,
            lang = lang,
            onAddToInventory = {
                viewModel.clearUnrecognizedBarcode()
                onNavigate("INVENTORY")
            },
            onDismiss = { viewModel.clearUnrecognizedBarcode() }
        )
    }

    selectedProductForSale?.let { prod ->
        val existingCartItem = cartByProductId[prod.id]
        CatalogPassToSaleDialog(
            product = prod,
            existingCartQuantity = existingCartItem?.quantity ?: 0.0,
            existingCartPrice = existingCartItem?.customPrice,
            canModifyPrice = canModifyPrice,
            currency = storeCurrency,
            lang = lang,
            onDismiss = { selectedProductForSale = null },
            onConfirm = { qty, unitPrice, saveAsDefaultPrice ->
                val isCustomPrice = abs(unitPrice - prod.sellPriceDouble) > 0.001
                val updatedProduct = if (saveAsDefaultPrice && isCustomPrice) {
                    viewModel.saveProduct(
                        id = prod.id,
                        barcode = prod.barcode,
                        name = prod.name,
                        description = prod.description,
                        categoryId = prod.categoryId,
                        categoryName = prod.categoryName,
                        unit = prod.unit,
                        purchasePrice = prod.purchasePriceDouble,
                        sellPrice = unitPrice,
                        wholesalePrice = prod.wholesalePriceDouble,
                        specialPrice = prod.specialPriceDouble,
                        currentStock = prod.currentStock,
                        minStock = prod.minStock,
                        imageUri = prod.imageUri
                    )
                    prod.copy(sellPrice = MoneyUtils.toCents(unitPrice))
                } else {
                    prod
                }

                viewModel.addOrUpdateCartItem(
                    product = updatedProduct,
                    quantity = qty,
                    customPrice = if (!saveAsDefaultPrice && isCustomPrice) unitPrice else null,
                    replaceExisting = true
                )
                selectedProductForSale = null
                Toast.makeText(
                    context,
                    when {
                        qty < 0.0 -> Bilingual.tr(
                            lang,
                            "تمت إضافة المنتج كمرتجع زبون (-) في السلة",
                            "Produit ajouté en retour client (-)",
                            "Added as customer return (-)"
                        )
                        lang == "AR" -> {
                            if (saveAsDefaultPrice && isCustomPrice) "تمت إضافة المنتج للسلة وتحديث ثمنه بنجاح"
                            else "تمت إضافة المنتج للسلة بنجاح"
                        }
                        else -> {
                            if (saveAsDefaultPrice && isCustomPrice) "Ajouté au panier et prix mis à jour"
                            else "Ajouté au panier"
                        }
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}
