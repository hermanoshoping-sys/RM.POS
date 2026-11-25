package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CustomerPaymentEntity
import com.example.data.SupplierPaymentEntity
import com.example.domain.FinancialCalculator
import com.example.ui.viewmodels.PosViewModel
import com.example.util.ActiveSort
import com.example.util.SortDirection
import com.example.util.SortingComparators
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun PaymentsScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Customers, 1: Suppliers
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateFilter by remember { mutableStateOf("ALL") } // ALL, TODAY, MONTH

    var showAddCustomerPaymentDialog by remember { mutableStateOf(false) }
    var showAddSupplierPaymentDialog by remember { mutableStateOf(false) }
    var viewingCustomerReceipt by remember { mutableStateOf<CustomerPaymentEntity?>(null) }
    var viewingSupplierReceipt by remember { mutableStateOf<SupplierPaymentEntity?>(null) }
    var showBatchPrintDialog by remember { mutableStateOf(false) }
    var paymentToDeleteCustomer by remember { mutableStateOf<CustomerPaymentEntity?>(null) }
    var paymentToDeleteSupplier by remember { mutableStateOf<SupplierPaymentEntity?>(null) }
    var paymentToEditCustomer by remember { mutableStateOf<CustomerPaymentEntity?>(null) }
    var paymentToEditSupplier by remember { mutableStateOf<SupplierPaymentEntity?>(null) }

    val customerPayments by viewModel.customerPayments.collectAsStateWithLifecycle()
    val pagedCustomerPayments by viewModel.pagedCustomerPayments.collectAsStateWithLifecycle()
    val supplierPayments by viewModel.supplierPayments.collectAsStateWithLifecycle()
    val pagedSupplierPayments by viewModel.pagedSupplierPayments.collectAsStateWithLifecycle()
    val customersList by viewModel.customers.collectAsStateWithLifecycle()
    val suppliersList by viewModel.suppliers.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US) }

    val (startOfToday, startOfMonth) = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, 1)
        today to cal.timeInMillis
    }

    var paymentSortState by remember { mutableStateOf(ActiveSort(PaymentSortCol.DATE, SortDirection.DESCENDING)) }
    val isDefaultUnfiltered = searchQuery.isBlank() &&
        selectedDateFilter == "ALL" &&
        paymentSortState.column == PaymentSortCol.DATE &&
        paymentSortState.direction == SortDirection.DESCENDING

    val filteredCustomerPayments = remember(customerPayments, searchQuery, selectedDateFilter) {
        customerPayments.filter { p ->
            val matchesSearch = searchQuery.isBlank() ||
                p.customerName.contains(searchQuery, ignoreCase = true) ||
                p.notes.contains(searchQuery, ignoreCase = true) ||
                "${p.id}".contains(searchQuery)
            val matchesDate = when (selectedDateFilter) {
                "TODAY" -> p.timestamp >= startOfToday
                "MONTH" -> p.timestamp >= startOfMonth
                else -> true
            }
            matchesSearch && matchesDate
        }
    }

    val filteredSupplierPayments = remember(supplierPayments, searchQuery, selectedDateFilter) {
        supplierPayments.filter { p ->
            val matchesSearch = searchQuery.isBlank() ||
                p.supplierName.contains(searchQuery, ignoreCase = true) ||
                p.notes.contains(searchQuery, ignoreCase = true) ||
                "${p.id}".contains(searchQuery)
            val matchesDate = when (selectedDateFilter) {
                "TODAY" -> p.timestamp >= startOfToday
                "MONTH" -> p.timestamp >= startOfMonth
                else -> true
            }
            matchesSearch && matchesDate
        }
    }

    val sortedCustomerPayments = remember(filteredCustomerPayments, pagedCustomerPayments, paymentSortState, isDefaultUnfiltered) {
        if (isDefaultUnfiltered) pagedCustomerPayments
        else filteredCustomerPayments.sortedWith { a, b ->
            val cmp = when (paymentSortState.column) {
                PaymentSortCol.NUMBER -> SortingComparators.compareLongs(a.id, b.id)
                PaymentSortCol.DATE -> SortingComparators.compareTimestamps(a.timestamp, b.timestamp)
                PaymentSortCol.PARTY -> SortingComparators.compareNames(a.customerName, b.customerName)
                PaymentSortCol.AMOUNT -> a.amount.compareTo(b.amount)
            }
            if (paymentSortState.direction == SortDirection.ASCENDING) cmp else -cmp
        }
    }

    val sortedSupplierPayments = remember(filteredSupplierPayments, pagedSupplierPayments, paymentSortState, isDefaultUnfiltered) {
        if (isDefaultUnfiltered) pagedSupplierPayments
        else filteredSupplierPayments.sortedWith { a, b ->
            val cmp = when (paymentSortState.column) {
                PaymentSortCol.NUMBER -> SortingComparators.compareLongs(a.id, b.id)
                PaymentSortCol.DATE -> SortingComparators.compareTimestamps(a.timestamp, b.timestamp)
                PaymentSortCol.PARTY -> SortingComparators.compareNames(a.supplierName, b.supplierName)
                PaymentSortCol.AMOUNT -> a.amount.compareTo(b.amount)
            }
            if (paymentSortState.direction == SortDirection.ASCENDING) cmp else -cmp
        }
    }

    val totalCustomerPaymentsCollected = remember(customerPayments) {
        FinancialCalculator.sumCents(customerPayments.map { it.amount })
    }
    val totalCustomerDebtRemaining = remember(customersList) {
        FinancialCalculator.sumPositiveBalancesCents(customersList.map { it.balance })
    }
    val totalSupplierPaymentsPaid = remember(supplierPayments) {
        FinancialCalculator.sumCents(supplierPayments.map { it.amount })
    }
    val totalSupplierDebtRemaining = remember(suppliersList) {
        FinancialCalculator.sumPositiveBalancesCents(suppliersList.map { it.debtBalance })
    }

    val hasMoreCustomers = isDefaultUnfiltered && pagedCustomerPayments.size < customerPayments.size
    val hasMoreSuppliers = isDefaultUnfiltered && pagedSupplierPayments.size < supplierPayments.size

    BackHandler { onNavigate("DASHBOARD") }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { onNavigate("DASHBOARD") },
                    modifier = Modifier.size(36.dp).testTag("payments_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
                }

                Text(
                    text = if (lang == "AR") "المدفوعات والمستحقات" else "Règlements & Créances",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp, textAlign = TextAlign.Center
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (selectedTab == 0) showAddCustomerPaymentDialog = true else showAddSupplierPaymentDialog = true },
                        modifier = Modifier.size(36.dp).testTag("add_payment_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Payment", tint = Color(0xFF4ADE80), modifier = Modifier.size(28.dp))
                    }
                    IconButton(
                        onClick = { showBatchPrintDialog = true },
                        modifier = Modifier.size(36.dp).testTag("batch_print_payments_button")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = "Print List", tint = Color(0xFF4ADE80), modifier = Modifier.size(26.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            PaymentsKpiBanner(
                lang = lang,
                currency = currency,
                selectedTab = selectedTab,
                debtRemainingCents = if (selectedTab == 0) totalCustomerDebtRemaining else totalSupplierDebtRemaining,
                totalPaidCents = if (selectedTab == 0) totalCustomerPaymentsCollected else totalSupplierPaymentsPaid
            )

            Spacer(modifier = Modifier.height(10.dp))

            PaymentsSubTabsRow(
                lang = lang,
                selectedTab = selectedTab,
                customerCount = customerPayments.size,
                supplierCount = supplierPayments.size,
                onSelectTab = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentFilterChipButton(
                        text = if (lang == "AR") "الكل" else "Tout",
                        isSelected = selectedDateFilter == "ALL",
                        activeColor = Color(0xFF10B981),
                        onClick = { selectedDateFilter = "ALL" }
                    )
                    PaymentFilterChipButton(
                        text = if (lang == "AR") "اليوم" else "Jour",
                        isSelected = selectedDateFilter == "TODAY",
                        activeColor = Color(0xFF10B981),
                        onClick = { selectedDateFilter = "TODAY" }
                    )
                    PaymentFilterChipButton(
                        text = if (lang == "AR") "الشهر" else "Mois",
                        isSelected = selectedDateFilter == "MONTH",
                        activeColor = Color(0xFF10B981),
                        onClick = { selectedDateFilter = "MONTH" }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (selectedTab == 0) {
                                    if (lang == "AR") "بحث باسم الزبون..." else "Recherche..."
                                } else {
                                    if (lang == "AR") "بحث باسم المورد..." else "Recherche..."
                                },
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("payments_search_input")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (lang == "AR") "سجل الدفعات" else "REGISTRE DES PAIEMENTS",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 10.dp)) {
                Surface(color = Color.White, border = BorderStroke(1.5.dp, Color.Black), modifier = Modifier.fillMaxSize()) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val minTableWidth = 450.dp
                        val tableWidth = if (maxWidth > minTableWidth) maxWidth else minTableWidth
                        Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                            Column(modifier = Modifier.width(tableWidth).fillMaxHeight()) {
                                val amountTitle = if (lang == "AR") "ثمن الدفع" else "Montant"
                                val partyTitle = if (selectedTab == 0) (if (lang == "AR") "الزبون" else "Client") else (if (lang == "AR") "المورد" else "Fournisseur")
                                val dateTitle = if (lang == "AR") "التاريخ" else "Date"
                                val numberTitle = if (lang == "AR") "رقم" else "N°"

                                val dispAmount = if (paymentSortState.column == PaymentSortCol.AMOUNT) "$amountTitle ${paymentSortState.direction.symbol}" else amountTitle
                                val dispParty = if (paymentSortState.column == PaymentSortCol.PARTY) "$partyTitle ${paymentSortState.direction.symbol}" else partyTitle
                                val dispDate = if (paymentSortState.column == PaymentSortCol.DATE) "$dateTitle ${paymentSortState.direction.symbol}" else dateTitle
                                val dispNumber = if (paymentSortState.column == PaymentSortCol.NUMBER) "$numberTitle ${paymentSortState.direction.symbol}" else numberTitle

                                Row(
                                    modifier = Modifier.fillMaxWidth().height(32.dp).background(Color(0xFFEAB308)).border(1.dp, Color.Black),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (lang == "AR") "إجراءات" else "Actions", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(75.dp).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically))
                                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                    Text(dispAmount, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(88.dp).fillMaxHeight().clickable { paymentSortState = paymentSortState.toggleOrSwitch(PaymentSortCol.AMOUNT) }.wrapContentHeight(Alignment.CenterVertically))
                                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                    Text(dispParty, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).fillMaxHeight().clickable { paymentSortState = paymentSortState.toggleOrSwitch(PaymentSortCol.PARTY) }.wrapContentHeight(Alignment.CenterVertically))
                                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                    Text(dispDate, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(96.dp).fillMaxHeight().clickable { paymentSortState = paymentSortState.toggleOrSwitch(PaymentSortCol.DATE) }.wrapContentHeight(Alignment.CenterVertically))
                                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                    Text(dispNumber, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp).fillMaxHeight().clickable { paymentSortState = paymentSortState.toggleOrSwitch(PaymentSortCol.NUMBER) }.wrapContentHeight(Alignment.CenterVertically))
                                }

                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    if (selectedTab == 0) {
                                        itemsIndexed(
                                            items = sortedCustomerPayments,
                                            key = { _, p -> "cust_${p.id}" },
                                            contentType = { _, _ -> "payment_row" }
                                        ) { _, payment ->
                                            PaymentLedgerTableRow(
                                                indexNumber = "${payment.id}",
                                                dateString = dateFormatter.format(Date(payment.timestamp)),
                                                partyName = payment.customerName,
                                                amountString = String.format(Locale.US, "%.2f", payment.amountDouble),
                                                hasActions = true,
                                                onPrint = { viewingCustomerReceipt = payment },
                                                onEdit = { paymentToEditCustomer = payment },
                                                onDelete = { paymentToDeleteCustomer = payment }
                                            )
                                        }
                                        val blankCount = (20 - sortedCustomerPayments.size).coerceAtLeast(0)
                                        items(blankCount, key = { idx -> "blank_c_$idx" }, contentType = { "blank_row" }) {
                                            PaymentLedgerTableRow(indexNumber = "", dateString = "", partyName = "", amountString = "", hasActions = false)
                                        }
                                        if (hasMoreCustomers) {
                                            item(key = "load_more_cust", contentType = "load_more") {
                                                PaymentsLoadMoreRow(
                                                    loaded = pagedCustomerPayments.size,
                                                    total = customerPayments.size,
                                                    lang = lang,
                                                    onLoadMore = { viewModel.loadMoreCustomerPayments() }
                                                )
                                            }
                                        }
                                    } else {
                                        itemsIndexed(
                                            items = sortedSupplierPayments,
                                            key = { _, p -> "sup_${p.id}" },
                                            contentType = { _, _ -> "payment_row" }
                                        ) { _, payment ->
                                            PaymentLedgerTableRow(
                                                indexNumber = "${payment.id}",
                                                dateString = dateFormatter.format(Date(payment.timestamp)),
                                                partyName = payment.supplierName,
                                                amountString = String.format(Locale.US, "%.2f", payment.amountDouble),
                                                hasActions = true,
                                                onPrint = { viewingSupplierReceipt = payment },
                                                onEdit = { paymentToEditSupplier = payment },
                                                onDelete = { paymentToDeleteSupplier = payment }
                                            )
                                        }
                                        val blankCount = (20 - sortedSupplierPayments.size).coerceAtLeast(0)
                                        items(blankCount, key = { idx -> "blank_s_$idx" }, contentType = { "blank_row" }) {
                                            PaymentLedgerTableRow(indexNumber = "", dateString = "", partyName = "", amountString = "", hasActions = false)
                                        }
                                        if (hasMoreSuppliers) {
                                            item(key = "load_more_sup", contentType = "load_more") {
                                                PaymentsLoadMoreRow(
                                                    loaded = pagedSupplierPayments.size,
                                                    total = supplierPayments.size,
                                                    lang = lang,
                                                    onLoadMore = { viewModel.loadMoreSupplierPayments() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    PaymentsScreenDialogs(
        viewModel = viewModel,
        lang = lang,
        currency = currency,
        selectedTab = selectedTab,
        selectedDateFilter = selectedDateFilter,
        dateFormatter = dateFormatter,
        customersList = customersList,
        suppliersList = suppliersList,
        filteredCustomerPayments = filteredCustomerPayments,
        filteredSupplierPayments = filteredSupplierPayments,
        showAddCustomerPaymentDialog = showAddCustomerPaymentDialog,
        onDismissAddCustomerPayment = { showAddCustomerPaymentDialog = false },
        showAddSupplierPaymentDialog = showAddSupplierPaymentDialog,
        onDismissAddSupplierPayment = { showAddSupplierPaymentDialog = false },
        viewingCustomerReceipt = viewingCustomerReceipt,
        onViewCustomerReceipt = { viewingCustomerReceipt = it },
        viewingSupplierReceipt = viewingSupplierReceipt,
        onViewSupplierReceipt = { viewingSupplierReceipt = it },
        paymentToEditCustomer = paymentToEditCustomer,
        onEditCustomerPaymentDismiss = { paymentToEditCustomer = null },
        paymentToDeleteCustomer = paymentToDeleteCustomer,
        onDeleteCustomerPaymentDismiss = { paymentToDeleteCustomer = null },
        paymentToEditSupplier = paymentToEditSupplier,
        onEditSupplierPaymentDismiss = { paymentToEditSupplier = null },
        paymentToDeleteSupplier = paymentToDeleteSupplier,
        onDeleteSupplierPaymentDismiss = { paymentToDeleteSupplier = null },
        showBatchPrintDialog = showBatchPrintDialog,
        onDismissBatchPrint = { showBatchPrintDialog = false }
    )
}
