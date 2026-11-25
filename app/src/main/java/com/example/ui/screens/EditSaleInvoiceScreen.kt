package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.domain.FinancialCalculator
import com.example.domain.PosOperationResult
import com.example.ui.viewmodels.PosViewModel
import com.example.util.cents
import com.example.util.moneyDouble
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Full-screen interface for editing sales invoices.
 */
@Composable
fun EditSaleInvoiceScreen(
    sale: SaleEntity,
    onDismiss: () -> Unit,
    onNavigate: ((String) -> Unit)? = null,
    viewModel: PosViewModel,
    lang: String
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val canEditInvoices = currentUser?.hasEditInvoiceAccess() ?: true
    val filteredProducts by viewModel.filteredPagedProducts.collectAsStateWithLifecycle()
    val filteredProductsCount by viewModel.filteredProductsCount.collectAsStateWithLifecycle()
    val hasMoreProducts by viewModel.hasMoreFilteredProducts.collectAsStateWithLifecycle()
    val categoriesList by viewModel.categories.collectAsStateWithLifecycle()
    val customersList by viewModel.customers.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(searchQuery) { viewModel.setProductSearchQuery(searchQuery) }
    LaunchedEffect(selectedCatId) { viewModel.setProductCategoryFilter(selectedCatId) }

    val invoiceItems = remember { mutableStateListOf<EditableSaleLineItem>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sale.id) {
        val originalItems = viewModel.getSaleItems(sale.id)
        val prodMap = if (originalItems.isNotEmpty()) {
            viewModel.repository.getProductsByIds(originalItems.map { it.productId }).associateBy { it.id }
        } else {
            emptyMap()
        }
        invoiceItems.clear()
        originalItems.forEach { item ->
            invoiceItems.add(
                EditableSaleLineItem(
                    productId = item.productId,
                    productName = item.productName,
                    barcode = prodMap[item.productId]?.barcode ?: "",
                    quantity = item.quantity,
                    unitPrice = item.unitPriceDouble
                )
            )
        }
    }

    var itemToEdit by remember { mutableStateOf<EditableSaleLineItem?>(null) }
    val scanner = remember {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    var invoiceDateMillis by remember { mutableStateOf(sale.timestamp) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    var currentInvoiceNumber by remember { mutableStateOf(sale.invoiceNumber) }
    var currentCustomerId by remember { mutableStateOf(sale.customerId) }
    var currentCustomerName by remember { mutableStateOf(sale.customerName) }
    var showEditInvoiceNumberDialog by remember { mutableStateOf(false) }
    var showSwitchCustomerDialog by remember { mutableStateOf(false) }

    val totalAmountCents = remember(invoiceItems.toList()) {
        FinancialCalculator.computeLinesTotalCents(invoiceItems.map { it.unitPrice.cents to it.quantity })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        EditSaleTopHeader(
            lang = lang,
            currentCustomerName = currentCustomerName,
            currentInvoiceNumber = currentInvoiceNumber,
            onDismiss = onDismiss,
            onOpenSalesLog = {
                onDismiss()
                onNavigate?.invoke("SALES_INVOICES")
            }
        )

        EditSaleMetaCards(
            lang = lang,
            canEditInvoices = canEditInvoices,
            currentInvoiceNumber = currentInvoiceNumber,
            currentCustomerName = currentCustomerName,
            invoiceDateMillis = invoiceDateMillis,
            dateFormat = dateFormat,
            onEditInvoiceNumber = { showEditInvoiceNumberDialog = true },
            onEditCustomer = { showSwitchCustomerDialog = true },
            onDateChanged = { invoiceDateMillis = it }
        )

        EditSaleSearchAndCategoriesBar(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            selectedCatId = selectedCatId,
            onCategorySelected = { selectedCatId = it },
            categoriesList = categoriesList,
            lang = lang,
            onScanBarcode = {
                try {
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            val scannedVal = barcode.rawValue ?: ""
                            if (scannedVal.isNotBlank()) {
                                coroutineScope.launch {
                                    val match = viewModel.repository.getProductByBarcode(scannedVal)
                                    if (match != null) {
                                        val idx = invoiceItems.indexOfFirst { it.productId == match.id }
                                        if (idx >= 0) {
                                            val existing = invoiceItems[idx]
                                            invoiceItems[idx] = existing.copy(quantity = existing.quantity + 1.0)
                                        } else {
                                            invoiceItems.add(
                                                EditableSaleLineItem(match.id, match.name, match.barcode, 1.0, match.sellPriceDouble)
                                            )
                                        }
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
                } catch (_: Exception) {
                    Toast.makeText(context, "الماسح غير متاح", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // Available Products Table (Table 1)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(Color.White)
                .border(BorderStroke(1.5.dp, Color.Black))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(32.dp).background(Color(0xFF00A0E9)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableCell(if (lang == "AR") "إضافة" else "Ajout", Modifier.width(55.dp), isHeader = true)
                    TableCell(if (lang == "AR") "ثمن" else "Prix", Modifier.width(75.dp), isHeader = true)
                    TableCell(if (lang == "AR") "المنتجات" else "Produits", Modifier.weight(1f), isHeader = true)
                    TableCell(if (lang == "AR") "كود بار" else "Code-barres", Modifier.width(90.dp), isHeader = true)
                }

                HorizontalDivider(color = Color.Black, thickness = 1.dp)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = filteredProducts,
                        key = { it.id },
                        contentType = { "edit_sale_product_row" }
                    ) { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(Color.White)
                                .clickable {
                                    val idx = invoiceItems.indexOfFirst { it.productId == product.id }
                                    if (idx >= 0) {
                                        val existing = invoiceItems[idx]
                                        invoiceItems[idx] = existing.copy(quantity = existing.quantity + 1.0)
                                    } else {
                                        invoiceItems.add(
                                            EditableSaleLineItem(product.id, product.name, product.barcode, 1.0, product.sellPriceDouble)
                                        )
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(55.dp).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(22.dp).background(Color(0xFF00A0E9), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            TableCell(String.format(Locale.US, "%.2f", product.sellPriceDouble), Modifier.width(75.dp))
                            TableCell(product.name, Modifier.weight(1f), align = TextAlign.Start)
                            TableCell(product.barcode.takeLast(8), Modifier.width(90.dp))
                        }
                        HorizontalDivider(color = Color.Black, thickness = 0.5.dp)
                    }

                    if (hasMoreProducts) {
                        item(key = "load_more_edit_sale_products", contentType = "load_more") {
                            LaunchedEffect(filteredProducts.size) { viewModel.loadMoreFilteredProducts() }
                            TextButton(
                                onClick = { viewModel.loadMoreFilteredProducts() },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("edit_sale_load_more_products_button")
                            ) {
                                Text(
                                    text = if (lang == "AR") "تحميل المزيد (${filteredProducts.size} / $filteredProductsCount)"
                                    else "Charger plus (${filteredProducts.size} / $filteredProductsCount)",
                                    color = Color(0xFF00A0E9),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    val emptyRowsCount = (3 - filteredProducts.size).coerceAtLeast(0)
                    items(count = emptyRowsCount, key = { "empty_prod_$it" }, contentType = { "empty_row" }) {
                        Row(modifier = Modifier.fillMaxWidth().height(29.dp).background(Color.White)) {
                            Box(modifier = Modifier.width(55.dp).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                            Box(modifier = Modifier.width(75.dp).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                            Box(modifier = Modifier.width(90.dp).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                        }
                        HorizontalDivider(color = Color.Black, thickness = 0.5.dp)
                    }
                }
            }
        }

        // Cart Header Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                Text(if (lang == "AR") "سلة المبيعات" else "Panier des Ventes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        if (invoiceItems.isNotEmpty()) {
                            invoiceItems.clear()
                            Toast.makeText(context, if (lang == "AR") "تم إفراغ السلة" else "Panier vidé", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("edit_sale_clear_cart_button")
            ) {
                Text(if (lang == "AR") "مسح الكل" else "Tout effacer", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(16.dp).background(Color(0xFFEF4444), RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }

        // Invoice Items Table (Table 2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .background(Color.White)
                .border(BorderStroke(1.5.dp, Color.Black))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(30.dp).background(Color(0xFF8CC63F)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableCell(if (lang == "AR") "المجموع" else "Total", Modifier.width(80.dp), isHeader = true)
                    TableCell(if (lang == "AR") "كمية" else "Qté", Modifier.width(65.dp), isHeader = true)
                    TableCell(if (lang == "AR") "ثمنه" else "Prix", Modifier.width(75.dp), isHeader = true)
                    TableCell(if (lang == "AR") "المنتج" else "Produit", Modifier.weight(1f), isHeader = true)
                }

                HorizontalDivider(color = Color.Black, thickness = 1.dp)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = invoiceItems,
                        key = { "${it.productId}_${it.productName}" },
                        contentType = { "edit_sale_cart_row" }
                    ) { item ->
                        val itemTotal = FinancialCalculator.computeLineTotalDouble(item.unitPrice, item.quantity)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .background(Color.White)
                                .clickable { itemToEdit = item },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableCell(String.format(Locale.US, "%.2f", itemTotal), Modifier.width(80.dp), isBold = true)
                            TableCell(String.format(Locale.US, "%.1f", item.quantity), Modifier.width(65.dp))
                            TableCell(String.format(Locale.US, "%.2f", item.unitPrice), Modifier.width(75.dp))
                            TableCell(item.productName, Modifier.weight(1f), align = TextAlign.Start)
                        }
                        HorizontalDivider(color = Color.Black, thickness = 0.5.dp)
                    }

                    val emptyRowsCount = (3 - invoiceItems.size).coerceAtLeast(0)
                    items(count = emptyRowsCount, key = { "empty_cart_$it" }, contentType = { "empty_row" }) {
                        Row(modifier = Modifier.fillMaxWidth().height(30.dp).background(Color.White)) {
                            Box(modifier = Modifier.width(80.dp).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                            Box(modifier = Modifier.width(65.dp).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                            Box(modifier = Modifier.width(75.dp).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                        }
                        HorizontalDivider(color = Color.Black, thickness = 0.5.dp)
                    }
                }
            }
        }

        EditSaleBottomSummarySection(
            totalAmount = totalAmountCents.moneyDouble,
            canEditInvoices = canEditInvoices,
            lang = lang,
            currency = currency,
            onConfirm = {
                if (!canEditInvoices) {
                    Toast.makeText(context, if (lang == "AR") "عذراً، صلاحية تعديل الفواتير معطلة لحسابك. راجع المسؤول." else "Modification des factures désactivée.", Toast.LENGTH_SHORT).show()
                    return@EditSaleBottomSummarySection
                }
                if (invoiceItems.isEmpty()) {
                    Toast.makeText(context, if (lang == "AR") "الفاتورة فارغة!" else "La facture est vide!", Toast.LENGTH_SHORT).show()
                    return@EditSaleBottomSummarySection
                }
                val editSummary = FinancialCalculator.computeEditedInvoiceSummary(
                    newTotalCents = totalAmountCents,
                    originalTotalCents = sale.totalAmount,
                    originalPaidCents = sale.paidAmount
                )
                val updatedSale = sale.copy(
                    invoiceNumber = currentInvoiceNumber.trim().ifEmpty { sale.invoiceNumber },
                    customerId = currentCustomerId,
                    customerName = currentCustomerName.trim(),
                    timestamp = invoiceDateMillis,
                    totalAmount = editSummary.totalCents,
                    paidAmount = editSummary.paidCents,
                    changeAmount = editSummary.changeDueCents
                )
                val updatedItems = invoiceItems.map { itm ->
                    SaleItemEntity(
                        saleId = sale.id,
                        productId = itm.productId,
                        productName = itm.productName,
                        quantity = itm.quantity,
                        unitPrice = itm.unitPrice.cents,
                        totalLinePrice = FinancialCalculator.computeLineTotalCents(itm.unitPrice.cents, itm.quantity)
                    )
                }
                viewModel.updateSale(updatedSale, updatedItems) { res ->
                    when (res) {
                        is PosOperationResult.Success -> {
                            Toast.makeText(context, res.message.ifBlank { if (lang == "AR") "تم تحديث وحفظ فاتورة الزبون بنجاح!" else "Facture client mise à jour avec succès!" }, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                        is PosOperationResult.Failure -> Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    itemToEdit?.let { currentItem ->
        EditSaleItemModal(
            currentItem = currentItem,
            lang = lang,
            currency = currency,
            onDelete = {
                invoiceItems.remove(currentItem)
                itemToEdit = null
            },
            onSave = { newQty, newPrice ->
                val idx = invoiceItems.indexOf(currentItem)
                if (idx >= 0) {
                    invoiceItems[idx] = currentItem.copy(quantity = newQty, unitPrice = newPrice)
                }
                itemToEdit = null
            },
            onDismiss = { itemToEdit = null }
        )
    }

    if (showEditInvoiceNumberDialog) {
        EditSaleInvoiceNumberModal(
            initialNumber = currentInvoiceNumber,
            lang = lang,
            onConfirm = {
                currentInvoiceNumber = it
                showEditInvoiceNumberDialog = false
            },
            onDismiss = { showEditInvoiceNumberDialog = false }
        )
    }

    if (showSwitchCustomerDialog) {
        SwitchEditSaleCustomerModal(
            customersList = customersList,
            currentCustomerId = currentCustomerId,
            currentCustomerName = currentCustomerName,
            currentUser = currentUser,
            lang = lang,
            currency = currency,
            onConfirm = { id, name ->
                currentCustomerId = id
                currentCustomerName = name
                showSwitchCustomerDialog = false
            },
            onDismiss = { showSwitchCustomerDialog = false }
        )
    }
}
