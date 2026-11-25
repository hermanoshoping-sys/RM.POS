package com.example.ui.screens.catalog

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ProductEntity
import com.example.domain.FinancialCalculator
import com.example.ui.screens.pos.CheckoutPayDialog
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils

private const val CATALOG_PAGE_SIZE = 120

/**
 * Product Catalog Component with Direct POS Passing,
 * Multi-photo Swiper (1080x1350 ratio),
 * Permanent Bottom 4-Column Summary Table, and Authentic POS Cart Ledger Modal.
 */
@Composable
fun ProductCatalogDialog(
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit,
    onProductDetailsClick: ((ProductEntity) -> Unit)? = null,
    onNavigate: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val productsList by viewModel.products.collectAsState()
    val catsList by viewModel.categories.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val currency by viewModel.currency.collectAsState()

    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var visibleLimit by remember(selectedCategoryId, searchQuery) { mutableStateOf(CATALOG_PAGE_SIZE) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showCartPreviewDialog by remember { mutableStateOf(false) }

    var targetProductForImage by remember { mutableStateOf<ProductEntity?>(null) }
    var productForOptionsDialog by remember { mutableStateOf<ProductEntity?>(null) }
    var productForPassToSale by remember { mutableStateOf<ProductEntity?>(null) }
    val canModifyPrice = remember(currentUser) {
        currentUser == null || currentUser?.isManager == true || currentUser?.canModifyPrice == true
    }

    val productPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        val targetProd = targetProductForImage
        if (uris.isNotEmpty() && targetProd != null) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (se: SecurityException) {
                    Log.w("ProductCatalog", "Persistable URI permission not granted by picker for $uri", se)
                } catch (e: Exception) {
                    Log.w("ProductCatalog", "Failed taking persistable URI permission for $uri", e)
                }
            }
            val existing = targetProd.imageList
            val combined = (existing + uris.map { it.toString() }).distinct()
            val finalImageUri = if (combined.isEmpty()) null else combined.joinToString("||")
            viewModel.updateProductImage(targetProd.id, finalImageUri)
            Toast.makeText(
                context,
                if (lang == "AR") "تم تحديث صور المنتج (1080x1350) بنجاح ✅" else "Photos du produit (1080x1350) enregistrées ✅",
                Toast.LENGTH_SHORT
            ).show()
        }
        targetProductForImage = null
    }

    val cartTotalCount = remember(cart) { cart.sumOf { it.quantity } }
    val cartTotalPrice = remember(cart) {
        MoneyUtils.fromCents(FinancialCalculator.sumCents(cart.map { MoneyUtils.toCents(it.totalAfterDiscount) }))
    }
    val cartByProductId = remember(cart) { cart.associateBy { it.product.id } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070D1E)),
            color = Color(0xFF070D1E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                CatalogTopHeaderBar(
                    selectedCategoryId = selectedCategoryId,
                    catsList = catsList,
                    productsList = productsList,
                    cartTotalCount = cartTotalCount,
                    hasCartItems = cart.isNotEmpty(),
                    lang = lang,
                    onBackToCategories = { selectedCategoryId = null },
                    onOpenCartPreview = { showCartPreviewDialog = true },
                    onDismiss = onDismiss
                )

                CatalogSearchBarRow(
                    searchQuery = searchQuery,
                    selectedCategoryId = selectedCategoryId,
                    lang = lang,
                    onSearchQueryChange = { searchQuery = it },
                    onResetFilters = {
                        selectedCategoryId = null
                        searchQuery = ""
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (selectedCategoryId == null && searchQuery.isBlank()) {
                        CatalogCategoriesGrid(
                            catsList = catsList,
                            productsList = productsList,
                            viewModel = viewModel,
                            lang = lang,
                            onSelectCategory = { catId -> selectedCategoryId = catId },
                            onSelectAllProducts = { selectedCategoryId = -1L }
                        )
                    } else {
                        val displayedProducts = remember(productsList, selectedCategoryId, searchQuery) {
                            productsList.filter { prod ->
                                val matchesCat = selectedCategoryId == null || selectedCategoryId == -1L || prod.categoryId == selectedCategoryId
                                val matchesSearch = searchQuery.isBlank() ||
                                    prod.name.contains(searchQuery, ignoreCase = true) ||
                                    prod.barcode.contains(searchQuery, ignoreCase = true) ||
                                    prod.categoryName.contains(searchQuery, ignoreCase = true)
                                matchesCat && matchesSearch
                            }
                        }

                        if (displayedProducts.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(54.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (lang == "AR") "لا توجد منتجات مطابقة في هذا التصنيف" else "Aucun produit trouvé",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        } else {
                            val visibleProducts = remember(displayedProducts, visibleLimit) {
                                if (displayedProducts.size > visibleLimit) displayedProducts.take(visibleLimit) else displayedProducts
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 170.dp),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 168.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(visibleProducts, key = { it.id }, contentType = { "catalog_product_card" }) { product ->
                                    val cartItem = cartByProductId[product.id]
                                    CatalogProductCard(
                                        product = product,
                                        cartQuantity = cartItem?.quantity ?: 0.0,
                                        cartPrice = cartItem?.customPrice,
                                        lang = lang,
                                        onPassToSaleClick = { productForPassToSale = product },
                                        onAddToCart = { viewModel.addToCart(product) },
                                        onDecrementCart = { viewModel.decrementCartQuantity(product.id) },
                                        onDetailsClick = {
                                            if (onProductDetailsClick != null) {
                                                onProductDetailsClick.invoke(product)
                                            } else {
                                                productForPassToSale = product
                                            }
                                        },
                                        onEditPhotos = {
                                            if (product.imageList.isEmpty()) {
                                                targetProductForImage = product
                                                productPhotoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            } else {
                                                productForOptionsDialog = product
                                            }
                                        },
                                        currency = currency
                                    )
                                }

                                if (displayedProducts.size > visibleProducts.size) {
                                    item(
                                        key = "catalog_load_more",
                                        span = { GridItemSpan(maxLineSpan) },
                                        contentType = "load_more"
                                    ) {
                                        OutlinedButton(
                                            onClick = { visibleLimit += CATALOG_PAGE_SIZE },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                                .testTag("catalog_load_more_button"),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                                            border = BorderStroke(1.dp, Color(0xFF38BDF8))
                                        ) {
                                            Icon(Icons.Filled.ExpandMore, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (lang == "AR")
                                                    "عرض المزيد (${visibleProducts.size} / ${displayedProducts.size})"
                                                else
                                                    "Afficher plus (${visibleProducts.size} / ${displayedProducts.size})"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    CatalogBottomStructuredTable(
                        cartTotalCount = cartTotalCount,
                        cartTotalPrice = cartTotalPrice,
                        cartLinesCount = cart.size,
                        lang = lang,
                        onOpenCart = { showCartPreviewDialog = true },
                        onExtractInvoice = {
                            if (cart.isNotEmpty()) {
                                showCheckoutDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    if (lang == "AR") "السلة فارغة، مرر المنتجات للبيع أولاً" else "Le panier est vide",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    if (showCheckoutDialog) {
        CheckoutPayDialog(
            viewModel = viewModel,
            grandTotal = cartTotalPrice,
            lang = lang,
            onDismiss = { showCheckoutDialog = false }
        )
    }

    if (showCartPreviewDialog) {
        AuthenticPOSCartDialog(
            viewModel = viewModel,
            cart = cart,
            cartTotalCount = cartTotalCount,
            cartTotalPrice = cartTotalPrice,
            lang = lang,
            onDismiss = { showCartPreviewDialog = false },
            onConfirmSale = {
                showCartPreviewDialog = false
                showCheckoutDialog = true
            }
        )
    }

    productForPassToSale?.let { prod ->
        val existingCartItem = cartByProductId[prod.id]
        CatalogPassToSaleDialog(
            product = prod,
            existingCartQuantity = existingCartItem?.quantity ?: 0.0,
            existingCartPrice = existingCartItem?.customPrice,
            canModifyPrice = canModifyPrice,
            currency = currency,
            lang = lang,
            onDismiss = { productForPassToSale = null },
            onConfirm = { qty, unitPrice, saveAsDefaultPrice ->
                val isCustomPrice = kotlin.math.abs(unitPrice - prod.sellPriceDouble) > 0.001
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
                productForPassToSale = null
                Toast.makeText(
                    context,
                    if (lang == "AR") {
                        if (saveAsDefaultPrice && isCustomPrice) "تم تمرير المنتج للسلة وتحديث ثمنه بنجاح ✅"
                        else "تم تمرير المنتج للسلة بنجاح ✅"
                    } else {
                        "Produit ajouté au panier ✅"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    productForOptionsDialog?.let { prod ->
        ProductImageOptionsDialog(
            product = prod,
            lang = lang,
            onDismiss = { productForOptionsDialog = null },
            onChangePhotos = {
                targetProductForImage = prod
                productForOptionsDialog = null
                productPhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearPhotos = {
                viewModel.updateProductImage(prod.id, null)
                productForOptionsDialog = null
                Toast.makeText(
                    context,
                    if (lang == "AR") "تمت إزالة صور المنتج بنجاح" else "Photos supprimées",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}
