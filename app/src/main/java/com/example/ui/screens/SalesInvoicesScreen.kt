package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.SaleEntity
import com.example.domain.PosOperationResult
import com.example.printer.PrinterTransport
import com.example.printer.ThermalPrinterDevice
import com.example.ui.components.rememberBluetoothAwarePrintLauncher
import com.example.ui.screens.pos.ThermalReceiptDialog
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import com.example.util.ActiveSort
import com.example.util.MoneyUtils
import com.example.util.SortDirection
import com.example.util.SortingComparators
import java.text.SimpleDateFormat
import java.util.*

enum class SalesSortCol {
    NUMBER, DATE, CLIENT, SELLER, TOTAL
}

@Composable
fun SalesInvoicesScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: ((String) -> Unit)? = null
) {
    val filteredSales by viewModel.filteredPagedSales.collectAsStateWithLifecycle()
    val sqlSummary by viewModel.filteredSalesSummary.collectAsStateWithLifecycle()
    val sellerCountRecords by viewModel.salesCountBySeller.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val usersList by viewModel.users.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var selectedSeller by remember { mutableStateOf("ALL") }

    val sellerCounts = remember(sellerCountRecords) {
        sellerCountRecords.associate { it.seller to it.invoiceCount }
    }
    val totalAllSalesCount = remember(sellerCountRecords) {
        sellerCountRecords.sumOf { it.invoiceCount }
    }

    val allSellers = remember(sellerCountRecords, usersList) {
        val list = mutableListOf<String>()
        sellerCountRecords.map { it.seller.trim() }.filter { it.isNotBlank() }.distinct().forEach { list.add(it) }
        usersList.map { it.username.trim() }.filter { it.isNotBlank() }.distinct().forEach {
            if (!list.contains(it)) list.add(it)
        }
        list.sorted()
    }

    var showReceipt by remember { mutableStateOf<SaleEntity?>(null) }
    var saleToEdit by remember { mutableStateOf<SaleEntity?>(null) }
    var selectedSaleForAction by remember { mutableStateOf<SaleEntity?>(null) }
    var saleToDelete by remember { mutableStateOf<SaleEntity?>(null) }
    var saleToReturn by remember { mutableStateOf<SaleEntity?>(null) }
    var editingInvoiceNumberSale by remember { mutableStateOf<SaleEntity?>(null) }
    var editingCustomerSale by remember { mutableStateOf<SaleEntity?>(null) }
    var saleForPartialPayment by remember { mutableStateOf<SaleEntity?>(null) }
    var showPrintListDialog by remember { mutableStateOf(false) }
    var isBatchSelectMode by remember { mutableStateOf(false) }
    var selectedSaleIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf("ALL") }
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val printerConnectionType by viewModel.printerConnectionType.collectAsStateWithLifecycle()
    val batchQueueState by viewModel.batchPrintQueueState.collectAsStateWithLifecycle()
    val canEditInvoices = currentUser?.hasEditInvoiceAccess() ?: true
    val canDeleteInvoices = currentUser?.hasDeleteInvoiceAccess() ?: true
    val context = LocalContext.current

    LaunchedEffect(searchQuery, filterStatus, selectedSeller) {
        viewModel.setSalesFilters(query = searchQuery, statusFilter = filterStatus, seller = selectedSeller)
    }

    var salesSortState by remember { mutableStateOf(ActiveSort(SalesSortCol.DATE, SortDirection.DESCENDING)) }
    val rowDateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }
    val actionDateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    val printDateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US) }

    val sortedSales = remember(filteredSales, salesSortState) {
        if (salesSortState.column == SalesSortCol.DATE && salesSortState.direction == SortDirection.DESCENDING) {
            filteredSales
        } else {
            filteredSales.sortedWith { a, b ->
                val cmp = when (salesSortState.column) {
                    SalesSortCol.NUMBER -> SortingComparators.compareBarcodes(a.invoiceNumber, b.invoiceNumber)
                    SalesSortCol.DATE -> SortingComparators.compareTimestamps(a.timestamp, b.timestamp)
                    SalesSortCol.CLIENT -> SortingComparators.compareNames(a.customerName, b.customerName)
                    SalesSortCol.SELLER -> SortingComparators.compareNames(a.cashierName, b.cashierName)
                    SalesSortCol.TOTAL -> a.totalAmount.compareTo(b.totalAmount)
                }
                if (salesSortState.direction == SortDirection.ASCENDING) cmp else -cmp
            }
        }
    }

    val selectedSalesInOrder = remember(sortedSales, selectedSaleIds) {
        sortedSales.filter { it.id in selectedSaleIds }
    }

    val launchBatchPrint = rememberBluetoothAwarePrintLauncher(lang = lang) {
        if (selectedSalesInOrder.isEmpty()) {
            Toast.makeText(
                context,
                Bilingual.tr(lang, "اختر فاتورة واحدة على الأقل للطباعة", "Sélectionnez au moins une facture", "Select at least one invoice"),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.startBatchPrintSales(context, selectedSalesInOrder)
            isBatchSelectMode = false
            selectedSaleIds = emptySet()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        POSMasterHeader(titleKey = "sales_invoices_title", lang = lang, viewModel = viewModel)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .imePadding()
        ) {
            LedgerScreenTopHeader(
                title = stringResource(R.string.sales_title_clients),
                onPrintList = { showPrintListDialog = true },
                leadingAction = {
                    Surface(
                        onClick = {
                            isBatchSelectMode = !isBatchSelectMode
                            if (!isBatchSelectMode) selectedSaleIds = emptySet()
                        },
                        color = if (isBatchSelectMode) Color(0xFF0284C7) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isBatchSelectMode) Color(0xFF38BDF8) else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .heightIn(min = 48.dp)
                            .testTag("invoice_history_batch_select_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Checklist,
                                contentDescription = Bilingual.tr(lang, "طباعة مجمّعة", "Impression groupée", "Batch Print"),
                                tint = if (isBatchSelectMode) Color.White else Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isBatchSelectMode) {
                                    Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel")
                                } else {
                                    Bilingual.tr(lang, "طباعة متعددة", "Lot", "Batch")
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )

            SalesInvoicesKpiRow(
                lang = lang,
                currency = currency,
                selectedSeller = selectedSeller,
                totalAmount = sqlSummary.totalAmount,
                totalPaid = sqlSummary.totalPaid,
                totalDebt = sqlSummary.totalDebt
            )

            SalesInvoicesFiltersSection(
                lang = lang,
                isSearchOpen = isSearchOpen,
                onToggleSearch = { isSearchOpen = !isSearchOpen },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onClearAndCloseSearch = {
                    searchQuery = ""
                    isSearchOpen = false
                },
                filterStatus = filterStatus,
                onFilterStatusChange = { filterStatus = it },
                selectedSeller = selectedSeller,
                onSelectedSellerChange = { selectedSeller = it },
                allSellers = allSellers,
                sellerCounts = sellerCounts,
                totalAllSalesCount = totalAllSalesCount
            )

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
                    val tableWidth = if (maxWidth > 480.dp) maxWidth else 480.dp
                    val allVisibleSelected = sortedSales.isNotEmpty() && sortedSales.all { it.id in selectedSaleIds }
                    Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        Column(modifier = Modifier.width(tableWidth).fillMaxHeight()) {
                            SalesLedgerTableRow(
                                col1 = "Numéro",
                                col2 = "date",
                                col3 = if (lang == "AR") "الزبون" else "Clients",
                                col4 = if (lang == "AR") "البائع" else "Vendeur",
                                col5 = if (lang == "AR") "المجموع" else "total",
                                isHeader = true,
                                selectionMode = isBatchSelectMode,
                                isSelected = allVisibleSelected,
                                onToggleSelect = if (isBatchSelectMode) {
                                    {
                                        selectedSaleIds = if (allVisibleSelected) {
                                            emptySet()
                                        } else {
                                            sortedSales.map { it.id }.toSet()
                                        }
                                    }
                                } else null,
                                activeSortCol = salesSortState.column,
                                sortArrow = salesSortState.direction.symbol,
                                onSortCol1 = { salesSortState = salesSortState.toggleOrSwitch(SalesSortCol.NUMBER) },
                                onSortCol2 = { salesSortState = salesSortState.toggleOrSwitch(SalesSortCol.DATE) },
                                onSortCol3 = { salesSortState = salesSortState.toggleOrSwitch(SalesSortCol.CLIENT) },
                                onSortCol4 = { salesSortState = salesSortState.toggleOrSwitch(SalesSortCol.SELLER) },
                                onSortCol5 = { salesSortState = salesSortState.toggleOrSwitch(SalesSortCol.TOTAL) }
                            )

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(
                                    items = sortedSales,
                                    key = { it.id },
                                    contentType = { "sale_invoice_row" }
                                ) { sale ->
                                    val isDebt = sale.remainingDueCents > 0L
                                    val isSelected = sale.id in selectedSaleIds
                                    SalesLedgerTableRow(
                                        col1 = sale.invoiceNumber,
                                        col2 = rowDateFormat.format(Date(sale.timestamp)),
                                        col3 = sale.customerName.ifBlank { if (lang == "AR") "زبون عادي" else "Client Comptoir" },
                                        col4 = sale.cashierName.ifBlank { currentUser?.username.orEmpty() },
                                        col5 = if (isDebt) String.format(Locale.US, "%.2f (دين)", sale.totalAmountDouble) else String.format(Locale.US, "%.2f", sale.totalAmountDouble),
                                        isHeader = false,
                                        isDebt = isDebt,
                                        isVoided = sale.isVoided,
                                        selectionMode = isBatchSelectMode,
                                        isSelected = isSelected,
                                        rowTestTag = "invoice_row_${sale.id}",
                                        onToggleSelect = {
                                            selectedSaleIds = if (isSelected) {
                                                selectedSaleIds - sale.id
                                            } else {
                                                selectedSaleIds + sale.id
                                            }
                                        },
                                        onClick = { selectedSaleForAction = sale },
                                        onLongClick = {
                                            if (!isBatchSelectMode) {
                                                isBatchSelectMode = true
                                                selectedSaleIds = setOf(sale.id)
                                            }
                                        }
                                    )
                                }

                                if (sortedSales.size < sqlSummary.count) {
                                    item(key = "load_more_sales", contentType = "load_more") {
                                        TextButton(
                                            onClick = { viewModel.loadMoreSales() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("sales_load_more_button")
                                        ) {
                                            Text(
                                                text = if (lang == "AR") "تحميل المزيد (${sortedSales.size} / ${sqlSummary.count})"
                                                else "Charger plus (${sortedSales.size} / ${sqlSummary.count})",
                                                color = Color(0xFF0284C7),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                val emptyRowsCount = (20 - sortedSales.size).coerceAtLeast(0)
                                items(
                                    count = emptyRowsCount,
                                    key = { "empty_sale_$it" },
                                    contentType = { "empty_row" }
                                ) {
                                    SalesLedgerTableRow(
                                        "", "", "", "", "",
                                        isHeader = false,
                                        isDebt = false,
                                        selectionMode = isBatchSelectMode,
                                        onClick = null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isBatchSelectMode) {
                BatchPrintActionBar(
                    selectedCount = selectedSalesInOrder.size,
                    totalSelectableCount = sortedSales.size,
                    lang = lang,
                    onSelectAll = { selectedSaleIds = sortedSales.map { it.id }.toSet() },
                    onClearSelection = { selectedSaleIds = emptySet() },
                    onExitSelectionMode = {
                        isBatchSelectMode = false
                        selectedSaleIds = emptySet()
                    },
                    onStartBatchPrint = { launchBatchPrint(printerConnectionType) }
                )
            }
        }
    }

    SalesInvoicesModalsHost(
        context = context,
        viewModel = viewModel,
        lang = lang,
        currency = currency,
        currentUser = currentUser,
        canEditInvoices = canEditInvoices,
        canDeleteInvoices = canDeleteInvoices,
        customers = customers,
        filteredSales = filteredSales,
        sqlSummaryCount = sqlSummary.count,
        sqlSummaryTotalAmount = sqlSummary.totalAmount,
        sqlSummaryTotalDebt = sqlSummary.totalDebt,
        selectedSeller = selectedSeller,
        filterStatus = filterStatus,
        actionDateFormat = actionDateFormat,
        printDateFormat = printDateFormat,
        batchQueueState = batchQueueState,
        selectedSaleForAction = selectedSaleForAction,
        onDismissAction = { selectedSaleForAction = null },
        onEditSaleSelected = { saleToEdit = it },
        onShowReceiptSelected = { showReceipt = it },
        onDeleteSaleSelected = { saleToDelete = it },
        onEditInvoiceNumberSelected = { editingInvoiceNumberSale = it },
        onEditCustomerSelected = { editingCustomerSale = it },
        onPartialPaymentSelected = { saleForPartialPayment = it },
        saleForPartialPayment = saleForPartialPayment,
        onDismissPartialPayment = { saleForPartialPayment = null },
        editingInvoiceNumberSale = editingInvoiceNumberSale,
        onDismissEditInvoiceNumber = { editingInvoiceNumberSale = null },
        editingCustomerSale = editingCustomerSale,
        onDismissEditCustomer = { editingCustomerSale = null },
        saleToDelete = saleToDelete,
        onDismissDeleteSale = { saleToDelete = null },
        saleToReturn = saleToReturn,
        onDismissReturnSale = { saleToReturn = null },
        showReceipt = showReceipt,
        onDismissReceipt = { showReceipt = null },
        saleToEdit = saleToEdit,
        onDismissEditSale = { saleToEdit = null },
        onNavigate = onNavigate,
        showPrintListDialog = showPrintListDialog,
        onDismissPrintList = { showPrintListDialog = false }
    )
}
