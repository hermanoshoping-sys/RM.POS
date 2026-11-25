package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.viewmodels.PosViewModel
import com.example.util.ActiveSort
import com.example.util.MoneyUtils
import com.example.util.SortDirection
import com.example.util.SortingComparators
import kotlinx.coroutines.launch
import java.util.Locale

enum class SupplierSortCol {
    NUMBER, NAME
}

@Composable
fun SuppliersScreen(viewModel: PosViewModel, lang: String, onNavigate: ((String) -> Unit)? = null) {
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val purchasesList by viewModel.purchases.collectAsStateWithLifecycle()
    val supplierPayments by viewModel.supplierPayments.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var showFormDialog by remember { mutableStateOf(false) }
    var editingSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var showPrintListDialog by remember { mutableStateOf(false) }

    var supplierToPay by remember { mutableStateOf<SupplierEntity?>(null) }
    var supplierToStatement by remember { mutableStateOf<SupplierEntity?>(null) }
    var selectedSupplierForAction by remember { mutableStateOf<SupplierEntity?>(null) }
    var supplierToDelete by remember { mutableStateOf<SupplierEntity?>(null) }
    var viewingPurchaseReceipt by remember { mutableStateOf<PurchaseEntity?>(null) }
    var viewingPurchaseItems by remember { mutableStateOf<List<PurchaseItemEntity>>(emptyList()) }
    var viewingSupplierPaymentReceipt by remember { mutableStateOf<SupplierPaymentEntity?>(null) }
    var supplierPaymentToEdit by remember { mutableStateOf<SupplierPaymentEntity?>(null) }
    var supplierPaymentToDelete by remember { mutableStateOf<SupplierPaymentEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var supplierSortState by remember { mutableStateOf(ActiveSort(SupplierSortCol.NAME, SortDirection.ASCENDING)) }

    val filtered = remember(suppliers, searchQuery) {
        if (searchQuery.isBlank()) suppliers
        else suppliers.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery) }
    }

    val sortedSuppliers = remember(filtered, supplierSortState) {
        filtered.sortedWith { a, b ->
            val cmp = when (supplierSortState.column) {
                SupplierSortCol.NUMBER -> SortingComparators.compareLongs(a.id, b.id)
                SupplierSortCol.NAME -> SortingComparators.compareNames(a.name, b.name)
            }
            if (supplierSortState.direction == SortDirection.ASCENDING) cmp else -cmp
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        POSMasterHeader(titleKey = "suppliers_btn", lang = lang, viewModel = viewModel)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        editingSupplier = null
                        showFormDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(44.dp).testTag("suppliers_add_button"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (lang == "AR") "إضافة مورد" else "Ajouter Fournisseur", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Box(
                    modifier = Modifier.size(48.dp).clickable { isSearchOpen = !isSearchOpen }.testTag("suppliers_search_toggle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(28.dp))
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .clickable { showPrintListDialog = true }
                        .testTag("suppliers_print_list_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Print, contentDescription = "Print List", tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                }
            }

            AnimatedVisibility(visible = isSearchOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (lang == "AR") "ابحث عن مورد بالاسم أو الهاتف..." else "Recherche...", color = Color.Gray, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, tint = Color.Gray, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, tint = Color.Gray, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("suppliers_search_input")
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color(0xFF65A30D)),
                color = Color.White
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val tableWidth = if (maxWidth > 340.dp) maxWidth else 340.dp
                    Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        Column(modifier = Modifier.width(tableWidth).fillMaxHeight()) {
                            val nameTitle = if (lang == "AR") "المورد" else "Fournisseur"
                            val numberTitle = "POS/#"

                            val dispName = if (supplierSortState.column == SupplierSortCol.NAME) "$nameTitle ${supplierSortState.direction.symbol}" else nameTitle
                            val dispNumber = if (supplierSortState.column == SupplierSortCol.NUMBER) "$numberTitle ${supplierSortState.direction.symbol}" else numberTitle

                            PartnerLedgerTableRow(
                                colPos = dispNumber,
                                colStatement = if (lang == "AR") "كشف الحساب" else "Relevé",
                                colPayDebt = if (lang == "AR") "تسديد دين" else "Régler",
                                colName = dispName,
                                isHeader = true,
                                statementHeaderColor = Color(0xFFE50012),
                                onNameHeaderClick = { supplierSortState = supplierSortState.toggleOrSwitch(SupplierSortCol.NAME) },
                                onNumberHeaderClick = { supplierSortState = supplierSortState.toggleOrSwitch(SupplierSortCol.NUMBER) }
                            )

                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                items(items = sortedSuppliers, key = { it.id }, contentType = { "supplier_row" }) { sup ->
                                    PartnerLedgerTableRow(
                                        colPos = "POS",
                                        colStatement = if (lang == "AR") "كشف" else "Relevé",
                                        colPayDebt = if (lang == "AR") "تسديد" else "Payer",
                                        colName = sup.name,
                                        isHeader = false,
                                        onPosClick = { onNavigate?.invoke("PURCHASES") },
                                        onStatementClick = { supplierToStatement = sup },
                                        onPayDebtClick = { supplierToPay = sup },
                                        onRowClick = { selectedSupplierForAction = sup }
                                    )
                                }

                                val emptyRowCount = (25 - sortedSuppliers.size).coerceAtLeast(10)
                                items(count = emptyRowCount, key = { "empty_sup_$it" }, contentType = { "empty_supplier_row" }) {
                                    PartnerLedgerTableRow(colPos = "", colStatement = "", colPayDebt = "", colName = "", isHeader = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedSupplierForAction?.let { sup ->
        PartnerActionDialog(
            isClient = false,
            partyName = sup.name,
            address = sup.address,
            phone = sup.phone,
            balanceStr = String.format(Locale.US, "%.2f", sup.debtBalanceDouble),
            isDebt = sup.debtBalance > 0L,
            onEdit = {
                editingSupplier = sup
                showFormDialog = true
                selectedSupplierForAction = null
            },
            onStatement = { supplierToStatement = sup; selectedSupplierForAction = null },
            onPayDebt = { supplierToPay = sup; selectedSupplierForAction = null },
            onInvoicesHistory = { supplierToStatement = sup; selectedSupplierForAction = null },
            onDelete = { supplierToDelete = sup; selectedSupplierForAction = null },
            onNavigate = onNavigate,
            currentUser = currentUser,
            onDismiss = { selectedSupplierForAction = null }
        )
    }

    supplierToDelete?.let { target ->
        SupplierDeleteConfirmDialog(
            supplier = target,
            lang = lang,
            onConfirm = {
                viewModel.deleteSupplier(target) { success, message ->
                    Toast.makeText(context, message, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                }
                supplierToDelete = null
            },
            onDismiss = { supplierToDelete = null }
        )
    }

    supplierToPay?.let { sup ->
        SupplierPayDebtDialog(
            supplier = sup,
            lang = lang,
            currency = currency,
            onConfirmPayment = { enteredAmt ->
                viewModel.recordSupplierPayment(
                    supplierId = sup.id,
                    amount = enteredAmt,
                    paymentMethod = "CASH",
                    notes = if (lang == "AR") "تسديد دين عبر شاشة الموردين" else "Règlement dette via écran fournisseurs"
                ) { createdPayment ->
                    if (createdPayment != null) viewingSupplierPaymentReceipt = createdPayment
                }
                supplierToPay = null
            },
            onDismiss = { supplierToPay = null }
        )
    }

    supplierToStatement?.let { sup ->
        SupplierStatementDialog(
            supplier = sup,
            purchasesList = purchasesList,
            supplierPayments = supplierPayments,
            lang = lang,
            viewModel = viewModel,
            onPrintPurchase = { p ->
                coroutineScope.launch {
                    viewingPurchaseItems = viewModel.getPurchaseItems(p.id)
                    viewingPurchaseReceipt = p
                }
            },
            onViewPaymentReceipt = { viewingSupplierPaymentReceipt = it },
            onEditPayment = { supplierPaymentToEdit = it },
            onDeletePayment = { supplierPaymentToDelete = it },
            onDismiss = { supplierToStatement = null }
        )
    }

    viewingPurchaseReceipt?.let { p ->
        ThermalPurchaseReceiptDialog(
            viewModel = viewModel,
            purchase = p,
            items = viewingPurchaseItems,
            lang = lang,
            onDismiss = { viewingPurchaseReceipt = null }
        )
    }

    viewingSupplierPaymentReceipt?.let { pay ->
        ThermalSupplierPaymentReceiptDialog(payment = pay, lang = lang, viewModel = viewModel, onDismiss = { viewingSupplierPaymentReceipt = null })
    }

    supplierPaymentToEdit?.let { pay ->
        EditSupplierPaymentDialog(
            payment = pay,
            lang = lang,
            currency = currency,
            onDismiss = { supplierPaymentToEdit = null },
            onConfirm = { newAmt, newMethod, newNotes ->
                viewModel.updateSupplierPayment(pay.id, newAmt, newMethod, newNotes) { supplierPaymentToEdit = null }
            }
        )
    }

    supplierPaymentToDelete?.let { pay ->
        DeleteSupplierPaymentConfirmDialog(
            payment = pay,
            lang = lang,
            currency = currency,
            onDismiss = { supplierPaymentToDelete = null },
            onConfirm = {
                viewModel.deleteSupplierPayment(pay)
                supplierPaymentToDelete = null
            }
        )
    }

    if (showPrintListDialog) {
        val totalSupplierDebt = filtered.sumOf { it.debtBalanceDouble }
        UniversalPrintListDialog(
            title = if (lang == "AR") "سجل وقائمة الموردين" else "REGISTRE DES FOURNISSEURS",
            subtitle = if (lang == "AR") "الموردين والديون المستحقة" else "Fournisseurs et dettes",
            headers = listOf(if (lang == "AR") "المورد" else "Fournisseur", if (lang == "AR") "الهاتف" else "Téléphone", if (lang == "AR") "الدين المستحق" else "Dette"),
            rows = filtered.map { s -> listOf(s.name, s.phone.ifBlank { "-" }, String.format(Locale.US, "%.2f", s.debtBalanceDouble)) },
            summaryLines = listOf(
                Pair(if (lang == "AR") "إجمالي عدد الموردين:" else "Total Fournisseurs:", "${filtered.size}"),
                Pair(if (lang == "AR") "إجمالي ديون الموردين:" else "Total Dettes Fournisseurs:", MoneyUtils.format(totalSupplierDebt, currency))
            ),
            viewModel = viewModel,
            lang = lang,
            onDismiss = { showPrintListDialog = false }
        )
    }

    if (showFormDialog) {
        SupplierAddEditDialog(
            editingSupplier = editingSupplier,
            lang = lang,
            onSave = { id, name, phone, email, address, debt ->
                viewModel.saveSupplier(id = id, name = name, phone = phone, email = email, address = address, debt = debt)
                editingSupplier = null
                showFormDialog = false
            },
            onDismiss = {
                editingSupplier = null
                showFormDialog = false
            }
        )
    }
}
