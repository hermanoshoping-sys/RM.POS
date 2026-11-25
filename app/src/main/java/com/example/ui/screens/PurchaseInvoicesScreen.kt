package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PurchaseEntity
import com.example.domain.FinancialCalculator
import com.example.domain.PosOperationResult
import com.example.ui.viewmodels.PosViewModel
import com.example.util.ActiveSort
import com.example.util.MoneyUtils
import com.example.util.SortDirection
import com.example.util.SortingComparators
import java.text.SimpleDateFormat
import java.util.*

enum class PurchaseSortCol {
    NUMBER, DATE, PARTY, TOTAL
}

@Composable
fun PurchaseInvoicesScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: ((String) -> Unit)? = null
) {
    val filteredPurchases by viewModel.filteredPagedPurchases.collectAsStateWithLifecycle()
    val sqlSummary by viewModel.filteredPurchasesSummary.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var showPurchaseReceipt by remember { mutableStateOf<PurchaseEntity?>(null) }
    var purchaseToEdit by remember { mutableStateOf<PurchaseEntity?>(null) }
    var selectedPurchaseForAction by remember { mutableStateOf<PurchaseEntity?>(null) }
    var purchaseToDelete by remember { mutableStateOf<PurchaseEntity?>(null) }
    var purchaseToReturn by remember { mutableStateOf<PurchaseEntity?>(null) }
    var editingInvoiceNumberPurchase by remember { mutableStateOf<PurchaseEntity?>(null) }
    var editingSupplierPurchase by remember { mutableStateOf<PurchaseEntity?>(null) }
    var purchaseForPartialPayment by remember { mutableStateOf<PurchaseEntity?>(null) }
    var showPrintListDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf("ALL") }
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val canEditInvoices = currentUser?.hasEditInvoiceAccess() ?: true
    val canDeleteInvoices = currentUser?.hasDeleteInvoiceAccess() ?: true
    val context = LocalContext.current

    LaunchedEffect(searchQuery, filterStatus) {
        viewModel.setPurchasesFilters(query = searchQuery, statusFilter = filterStatus)
    }

    var purchaseSortState by remember { mutableStateOf(ActiveSort(PurchaseSortCol.DATE, SortDirection.DESCENDING)) }
    val rowDateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }
    val actionDateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    val printDateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }

    val sortedPurchases = remember(filteredPurchases, purchaseSortState) {
        if (purchaseSortState.column == PurchaseSortCol.DATE && purchaseSortState.direction == SortDirection.DESCENDING) {
            filteredPurchases
        } else {
            filteredPurchases.sortedWith { a, b ->
                val cmp = when (purchaseSortState.column) {
                    PurchaseSortCol.NUMBER -> SortingComparators.compareBarcodes(a.displayInvoiceNumber, b.displayInvoiceNumber)
                    PurchaseSortCol.DATE -> SortingComparators.compareTimestamps(a.timestamp, b.timestamp)
                    PurchaseSortCol.PARTY -> SortingComparators.compareNames(a.supplierName, b.supplierName)
                    PurchaseSortCol.TOTAL -> a.totalAmount.compareTo(b.totalAmount)
                }
                if (purchaseSortState.direction == SortDirection.ASCENDING) cmp else -cmp
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        POSMasterHeader(titleKey = "purchases_btn", lang = lang, viewModel = viewModel)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .imePadding()
        ) {
            LedgerScreenTopHeader(
                title = if (lang == "AR") "فواتير الموردين" else "Factures Fournisseurs",
                onPrintList = { showPrintListDialog = true }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .height(56.dp)
            ) {
                LedgerKpiBlock(
                    modifier = Modifier.weight(1f),
                    title = if (lang == "AR") "مجموع المشتريات" else "Total Achats",
                    value = MoneyUtils.format(sqlSummary.totalAmount, currency),
                    backgroundColor = Color(0xFF00A0E9)
                )
                LedgerKpiBlock(
                    modifier = Modifier.weight(1f),
                    title = if (lang == "AR") "الفواتير المسددة" else "Factures Réglées",
                    value = MoneyUtils.format(sqlSummary.totalPaid, currency),
                    backgroundColor = Color(0xFF8CC63F)
                )
                LedgerKpiBlock(
                    modifier = Modifier.weight(1f),
                    title = if (lang == "AR") "الفواتير المدينة (للموردين)" else "Factures à Crédit",
                    value = MoneyUtils.format(sqlSummary.totalDebt, currency),
                    backgroundColor = Color(0xFFE50012)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isSearchOpen = !isSearchOpen },
                    modifier = Modifier.size(48.dp).testTag("purchases_search_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = if (isSearchOpen || searchQuery.isNotEmpty()) Color(0xFF38BDF8) else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerFilterButton(if (lang == "AR") "الكل" else "Tous", Color(0xFF5A6B82), filterStatus == "ALL") { filterStatus = "ALL" }
                    LedgerFilterButton(if (lang == "AR") "مسددة" else "Complètes", Color(0xFF7CB342), filterStatus == "PAID") { filterStatus = "PAID" }
                    LedgerFilterButton(if (lang == "AR") "مدينة" else "À Crédit (Dettes)", Color(0xFFE53935), filterStatus == "DEBT") { filterStatus = "DEBT" }
                }
            }

            if (isSearchOpen || searchQuery.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (lang == "AR") "بحث باسم المورد أو رقم السند..." else "Recherche...", fontSize = 12.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearchOpen = false
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .height(48.dp)
                        .testTag("purchases_search_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, Color(0xFF166534)),
                shape = RoundedCornerShape(2.dp)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val tableWidth = if (maxWidth > 460.dp) maxWidth else 460.dp
                    Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        Column(modifier = Modifier.width(tableWidth).fillMaxHeight()) {
                            LedgerTableRow(
                                col1 = "Numéro",
                                col2 = "date",
                                col3 = if (lang == "AR") "المورد" else "Fournisseurs",
                                col4 = if (lang == "AR") "المجموع" else "total",
                                isHeader = true,
                                activeSortCol = purchaseSortState.column.name,
                                sortArrow = purchaseSortState.direction.symbol,
                                onSortCol1 = { purchaseSortState = purchaseSortState.toggleOrSwitch(PurchaseSortCol.NUMBER) },
                                onSortCol2 = { purchaseSortState = purchaseSortState.toggleOrSwitch(PurchaseSortCol.DATE) },
                                onSortCol3 = { purchaseSortState = purchaseSortState.toggleOrSwitch(PurchaseSortCol.PARTY) },
                                onSortCol4 = { purchaseSortState = purchaseSortState.toggleOrSwitch(PurchaseSortCol.TOTAL) }
                            )

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(
                                    items = sortedPurchases,
                                    key = { it.id },
                                    contentType = { "purchase_invoice_row" }
                                ) { pur ->
                                    val isDebt = pur.remainingDebtCents > 0L
                                    LedgerTableRow(
                                        col1 = pur.displayInvoiceNumber,
                                        col2 = rowDateFormat.format(Date(pur.timestamp)),
                                        col3 = pur.supplierName.ifBlank { if (lang == "AR") "مورد عام" else "Fournisseur" },
                                        col4 = if (isDebt) String.format(Locale.US, "%.2f (دين)", pur.totalAmountDouble) else String.format(Locale.US, "%.2f", pur.totalAmountDouble),
                                        isHeader = false,
                                        isDebt = isDebt,
                                        onClick = { selectedPurchaseForAction = pur }
                                    )
                                }

                                if (sortedPurchases.size < sqlSummary.count) {
                                    item(key = "load_more_purchases", contentType = "load_more") {
                                        TextButton(
                                            onClick = { viewModel.loadMorePurchases() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("purchases_load_more_button")
                                        ) {
                                            Text(
                                                text = if (lang == "AR") "تحميل المزيد (${sortedPurchases.size} / ${sqlSummary.count})"
                                                else "Charger plus (${sortedPurchases.size} / ${sqlSummary.count})",
                                                color = Color(0xFF00A0E9),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                val emptyRowsCount = (20 - sortedPurchases.size).coerceAtLeast(0)
                                items(
                                    count = emptyRowsCount,
                                    key = { "empty_purchase_$it" },
                                    contentType = { "empty_row" }
                                ) {
                                    LedgerTableRow("", "", "", "", isHeader = false, isDebt = false, onClick = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    PurchaseInvoicesModalsHost(
        context = context,
        viewModel = viewModel,
        lang = lang,
        currency = currency,
        canEditInvoices = canEditInvoices,
        canDeleteInvoices = canDeleteInvoices,
        suppliers = suppliers,
        filteredPurchases = filteredPurchases,
        filterStatus = filterStatus,
        actionDateFormat = actionDateFormat,
        printDateFormat = printDateFormat,
        selectedPurchaseForAction = selectedPurchaseForAction,
        onDismissAction = { selectedPurchaseForAction = null },
        onEditPurchaseSelected = { purchaseToEdit = it },
        onShowReceiptSelected = { showPurchaseReceipt = it },
        onDeletePurchaseSelected = { purchaseToDelete = it },
        onEditInvoiceNumberSelected = { editingInvoiceNumberPurchase = it },
        onEditSupplierSelected = { editingSupplierPurchase = it },
        onPartialPaymentSelected = { purchaseForPartialPayment = it },
        purchaseForPartialPayment = purchaseForPartialPayment,
        onDismissPartialPayment = { purchaseForPartialPayment = null },
        editingInvoiceNumberPurchase = editingInvoiceNumberPurchase,
        onDismissEditInvoiceNumber = { editingInvoiceNumberPurchase = null },
        editingSupplierPurchase = editingSupplierPurchase,
        onDismissEditSupplier = { editingSupplierPurchase = null },
        purchaseToDelete = purchaseToDelete,
        onDismissDeletePurchase = { purchaseToDelete = null },
        purchaseToReturn = purchaseToReturn,
        onDismissReturnPurchase = { purchaseToReturn = null },
        showPurchaseReceipt = showPurchaseReceipt,
        onDismissPurchaseReceipt = { showPurchaseReceipt = null },
        purchaseToEdit = purchaseToEdit,
        onDismissEditPurchase = { purchaseToEdit = null },
        onNavigate = onNavigate,
        showPrintListDialog = showPrintListDialog,
        onDismissPrintList = { showPrintListDialog = false }
    )
}
