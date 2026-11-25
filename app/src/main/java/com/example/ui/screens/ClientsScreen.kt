package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CustomerEntity
import com.example.data.CustomerPaymentEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.ui.screens.clients.ClientsActionAndManagementDialogs
import com.example.ui.screens.clients.ClientsPaymentAndStatementDialogs
import com.example.ui.screens.clients.ClientsTopControlsAndFilters
import com.example.ui.viewmodels.PosViewModel
import com.example.util.*
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

@Composable
fun ClientsScreen(viewModel: PosViewModel, lang: String, onNavigate: (String) -> Unit) {
    val pagedClients by viewModel.filteredPagedCustomers.collectAsStateWithLifecycle()
    val totalFilteredClientsCount by viewModel.filteredCustomersCount.collectAsStateWithLifecycle()
    val allClientsForPrint by viewModel.customers.collectAsStateWithLifecycle()
    val customerPayments by viewModel.customerPayments.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scanner = remember {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    var searchVal by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPrintListDialog by remember { mutableStateOf(false) }

    var editingCustomerId by remember { mutableStateOf<Long?>(null) }
    var cName by remember { mutableStateOf("") }
    var cPhone by remember { mutableStateOf("") }
    var cAddress by remember { mutableStateOf("") }
    var cBalance by remember { mutableStateOf("0.0") }
    var cLatitude by remember { mutableStateOf<Double?>(null) }
    var cLongitude by remember { mutableStateOf<Double?>(null) }
    var cGeoRadius by remember { mutableStateOf(150.0) }
    var cIsGeoRequired by remember { mutableStateOf(false) }
    var cCustomerNumber by remember { mutableStateOf(0L) }
    var cBarcode by remember { mutableStateOf("") }
    var isCapturingGps by remember { mutableStateOf(false) }
    var showMapAddressPicker by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedCustomerIds by remember { mutableStateOf(setOf<Long>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var customerSortState by remember { mutableStateOf(ActiveSort(CustomerSortCol.NUMBER, SortDirection.ASCENDING)) }
    var customerForRouteTracking by remember { mutableStateOf<CustomerEntity?>(null) }

    var geoBlockedResult by remember { mutableStateOf<GeoFenceResult?>(null) }
    var geoBlockedCustomerName by remember { mutableStateOf("") }
    var geoBlockedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    var clientToPay by remember { mutableStateOf<CustomerEntity?>(null) }
    var clientToStatement by remember { mutableStateOf<CustomerEntity?>(null) }
    var selectedClientForAction by remember { mutableStateOf<CustomerEntity?>(null) }
    var clientToDelete by remember { mutableStateOf<CustomerEntity?>(null) }
    var viewingSaleReceipt by remember { mutableStateOf<SaleEntity?>(null) }
    var viewingSaleItems by remember { mutableStateOf<List<SaleItemEntity>>(emptyList()) }
    var viewingPaymentReceipt by remember { mutableStateOf<CustomerPaymentEntity?>(null) }
    var paymentToEdit by remember { mutableStateOf<CustomerPaymentEntity?>(null) }
    var paymentToDelete by remember { mutableStateOf<CustomerPaymentEntity?>(null) }
    var saleToDeleteFromStatement by remember { mutableStateOf<SaleEntity?>(null) }

    LaunchedEffect(searchVal) {
        viewModel.setCustomerSearchQuery(searchVal)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            coroutineScope.launch {
                isCapturingGps = true
                val loc = GeoLocationHelper.getCurrentLocation(context)
                isCapturingGps = false
                if (loc != null) {
                    cLatitude = loc.latitude
                    cLongitude = loc.longitude
                    Toast.makeText(context, if (lang == "AR") "تم التقاط موقع الزبون بنجاح!" else "Position enregistrée !", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, if (lang == "AR") "تعذر تحديد الموقع. تأكد من تفعيل GPS" else "GPS indisponible", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(context, if (lang == "AR") "يلزم إذن الوصول إلى الموقع لالتقاط الإحداثيات" else "Permission GPS requise", Toast.LENGTH_SHORT).show()
        }
    }

    val sortedClients = remember(pagedClients, customerSortState) {
        val sorted = when (customerSortState.column) {
            CustomerSortCol.NUMBER -> pagedClients.sortedWith { c1, c2 ->
                val n1 = if (c1.customerNumber > 0) c1.customerNumber else c1.id
                val n2 = if (c2.customerNumber > 0) c2.customerNumber else c2.id
                SortingComparators.compareLongs(n1, n2)
            }
            CustomerSortCol.NAME -> pagedClients.sortedWith { c1, c2 ->
                SortingComparators.compareNames(c1.name, c2.name)
            }
            CustomerSortCol.BALANCE -> pagedClients.sortedWith { c1, c2 ->
                c1.balance.compareTo(c2.balance)
            }
            CustomerSortCol.BARCODE -> pagedClients.sortedWith { c1, c2 ->
                SortingComparators.compareBarcodes(c1.barcode, c2.barcode)
            }
        }
        if (customerSortState.direction == SortDirection.DESCENDING) sorted.reversed() else sorted
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        POSMasterHeader(titleKey = "customers_btn", lang = lang, viewModel = viewModel)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .imePadding()
        ) {
            ClientsTopControlsAndFilters(
                lang = lang,
                isSearchOpen = isSearchOpen,
                onToggleSearch = { isSearchOpen = !isSearchOpen },
                searchVal = searchVal,
                onSearchChange = { searchVal = it },
                scanner = scanner,
                customerSortState = customerSortState,
                onSortChange = { customerSortState = it },
                isMultiSelectMode = isMultiSelectMode,
                onToggleMultiSelect = {
                    isMultiSelectMode = !isMultiSelectMode
                    if (!isMultiSelectMode) selectedCustomerIds = emptySet()
                },
                selectedCount = selectedCustomerIds.size,
                allSelected = selectedCustomerIds.size == sortedClients.size && sortedClients.isNotEmpty(),
                onToggleSelectAll = {
                    selectedCustomerIds = if (selectedCustomerIds.size == sortedClients.size && sortedClients.isNotEmpty()) emptySet()
                    else sortedClients.map { it.id }.toSet()
                },
                onBulkDeleteClick = { showBulkDeleteConfirm = true },
                onPrintClick = { showPrintListDialog = true },
                onAddClientClick = {
                    editingCustomerId = null
                    cName = ""; cPhone = ""; cAddress = ""; cBalance = "0.0"
                    cLatitude = null; cLongitude = null; cGeoRadius = 150.0; cIsGeoRequired = false
                    cCustomerNumber = 0L; cBarcode = ""
                    showAddDialog = true
                }
            )

            // Spreadsheet Ledger Grid Table Container
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
                            val nameArrow = if (customerSortState.column == CustomerSortCol.NAME) customerSortState.direction.symbol else ""
                            val numArrow = if (customerSortState.column == CustomerSortCol.NUMBER) customerSortState.direction.symbol else ""

                            PartnerLedgerTableRow(
                                colPos = "POS$numArrow",
                                colStatement = if (lang == "AR") "كشف الحساب" else "Relevé",
                                colPayDebt = if (lang == "AR") "تسديد دين" else "Régler",
                                colName = (if (lang == "AR") "الزبون" else "Client") + nameArrow,
                                isHeader = true,
                                statementHeaderColor = Color(0xFFF9CF58),
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = selectedCustomerIds.size == sortedClients.size && sortedClients.isNotEmpty(),
                                onToggleSelect = {
                                    selectedCustomerIds = if (selectedCustomerIds.size == sortedClients.size && sortedClients.isNotEmpty()) emptySet()
                                    else sortedClients.map { it.id }.toSet()
                                },
                                onNameHeaderClick = { customerSortState = customerSortState.toggleOrSwitch(CustomerSortCol.NAME) },
                                onNumberHeaderClick = { customerSortState = customerSortState.toggleOrSwitch(CustomerSortCol.NUMBER) }
                            )

                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                items(sortedClients, key = { it.id }, contentType = { "client_row" }) { client ->
                                    PartnerLedgerTableRow(
                                        colPos = "POS",
                                        colStatement = if (lang == "AR") "كشف" else "Relevé",
                                        colPayDebt = if (lang == "AR") "تسديد" else "Payer",
                                        colName = if (client.customerNumber > 0L) "#${client.customerNumber} ${client.name}" else client.name,
                                        hasGeoLocation = client.isGeoLocationRequired && client.hasGeoLocation,
                                        isHeader = false,
                                        isMultiSelectMode = isMultiSelectMode,
                                        isSelected = selectedCustomerIds.contains(client.id),
                                        onToggleSelect = {
                                            selectedCustomerIds = if (selectedCustomerIds.contains(client.id)) selectedCustomerIds - client.id else selectedCustomerIds + client.id
                                        },
                                        onPosClick = {
                                            if (currentUser?.enforceCustomerGeoLocation == true && client.isGeoLocationRequired && client.hasGeoLocation) {
                                                coroutineScope.launch {
                                                    val check = GeoLocationHelper.verifyCustomerLocationForInvoice(context, client, currentUser, lang)
                                                    if (check.isAllowed) {
                                                        viewModel.selectedPartnerClientId.value = client.id
                                                        onNavigate("POS")
                                                    } else {
                                                        geoBlockedResult = check; geoBlockedCustomerName = client.name; geoBlockedCustomer = client
                                                    }
                                                }
                                            } else {
                                                viewModel.selectedPartnerClientId.value = client.id
                                                onNavigate("POS")
                                            }
                                        },
                                        onStatementClick = { clientToStatement = client },
                                        onPayDebtClick = { clientToPay = client },
                                        onRowClick = {
                                            if (isMultiSelectMode) {
                                                selectedCustomerIds = if (selectedCustomerIds.contains(client.id)) selectedCustomerIds - client.id else selectedCustomerIds + client.id
                                            } else {
                                                selectedClientForAction = client
                                            }
                                        }
                                    )
                                }

                                if (sortedClients.size < totalFilteredClientsCount) {
                                    item(key = "load_more_clients", contentType = "load_more") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC)).padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            TextButton(
                                                onClick = { viewModel.loadMoreCustomers(60) },
                                                modifier = Modifier.height(48.dp).testTag("clients_load_more_button")
                                            ) {
                                                Text(
                                                    text = if (lang == "AR") "عرض المزيد (${sortedClients.size} / $totalFilteredClientsCount)" else "Charger plus (${sortedClients.size} / $totalFilteredClientsCount)",
                                                    color = Color(0xFF2563EB),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                val emptyRowCount = (20 - sortedClients.size).coerceAtLeast(6)
                                items(emptyRowCount, contentType = { "empty_row" }) {
                                    PartnerLedgerTableRow()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val storeCurrency by viewModel.currency.collectAsStateWithLifecycle()

    ClientsActionAndManagementDialogs(
        context = context,
        coroutineScope = coroutineScope,
        viewModel = viewModel,
        lang = lang,
        storeCurrency = storeCurrency,
        currentUser = currentUser,
        selectedClientForAction = selectedClientForAction,
        onDismissAction = { selectedClientForAction = null },
        onEditCustomer = { client ->
            editingCustomerId = client.id; cName = client.name; cPhone = client.phone; cAddress = client.address
            cBalance = client.balanceDouble.toString(); cLatitude = client.latitude; cLongitude = client.longitude
            cGeoRadius = client.geoRadiusMeters; cIsGeoRequired = client.isGeoLocationRequired
            cCustomerNumber = client.customerNumber; cBarcode = client.barcode
            showAddDialog = true
        },
        onOpenStatement = { clientToStatement = it },
        onOpenPayDebt = { clientToPay = it },
        onRequestDeleteCustomer = { clientToDelete = it },
        onOpenRouteTracking = { customerForRouteTracking = it },
        onNavigate = onNavigate,
        clientToDelete = clientToDelete,
        onDismissDelete = { clientToDelete = null },
        showPrintListDialog = showPrintListDialog,
        onDismissPrintList = { showPrintListDialog = false },
        searchVal = searchVal,
        allClientsForPrint = allClientsForPrint,
        sortedClients = sortedClients,
        geoBlockedResult = geoBlockedResult,
        geoBlockedCustomerName = geoBlockedCustomerName,
        geoBlockedCustomer = geoBlockedCustomer,
        onUpdateGeoBlocked = { geoBlockedResult = it },
        customerForRouteTracking = customerForRouteTracking,
        onDismissRouteTracking = { customerForRouteTracking = null },
        showBulkDeleteConfirm = showBulkDeleteConfirm,
        selectedCustomerIds = selectedCustomerIds,
        onBulkDeleteComplete = { showBulkDeleteConfirm = false; isMultiSelectMode = false; selectedCustomerIds = emptySet() },
        onDismissBulkDelete = { showBulkDeleteConfirm = false }
    )

    ClientsPaymentAndStatementDialogs(
        viewModel = viewModel,
        lang = lang,
        customerPayments = customerPayments,
        clientToPay = clientToPay,
        onDismissPay = { clientToPay = null },
        clientToStatement = clientToStatement,
        onDismissStatement = { clientToStatement = null },
        viewingSaleReceipt = viewingSaleReceipt,
        viewingSaleItems = viewingSaleItems,
        onShowSaleReceipt = { sale, itemsList -> viewingSaleItems = itemsList; viewingSaleReceipt = sale },
        onDismissSaleReceipt = { viewingSaleReceipt = null },
        viewingPaymentReceipt = viewingPaymentReceipt,
        onShowPaymentReceipt = { viewingPaymentReceipt = it },
        onDismissPaymentReceipt = { viewingPaymentReceipt = null },
        paymentToEdit = paymentToEdit,
        onOpenEditPayment = { paymentToEdit = it },
        onDismissEditPayment = { paymentToEdit = null },
        paymentToDelete = paymentToDelete,
        onOpenDeletePayment = { paymentToDelete = it },
        onDismissDeletePayment = { paymentToDelete = null },
        saleToDeleteFromStatement = saleToDeleteFromStatement,
        onOpenDeleteSale = { saleToDeleteFromStatement = it },
        onDismissDeleteSale = { saleToDeleteFromStatement = null }
    )

    if (showAddDialog) {
        ClientFormDialog(
            editingCustomerId = editingCustomerId,
            cName = cName, onNameChange = { cName = it },
            cPhone = cPhone, onPhoneChange = { cPhone = it },
            cAddress = cAddress, onAddressChange = { cAddress = it },
            cBalance = cBalance, onBalanceChange = { cBalance = it },
            cLatitude = cLatitude, onLatitudeChange = { cLatitude = it },
            cLongitude = cLongitude, onLongitudeChange = { cLongitude = it },
            cGeoRadius = cGeoRadius, onGeoRadiusChange = { cGeoRadius = it },
            cIsGeoRequired = cIsGeoRequired, onIsGeoRequiredChange = { cIsGeoRequired = it },
            cCustomerNumber = cCustomerNumber, cBarcode = cBarcode,
            isCapturingGps = isCapturingGps, onCapturingGpsChange = { isCapturingGps = it },
            currentUser = currentUser, lang = lang,
            onRequestLocationPermission = {
                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            onOpenMapPicker = { showMapAddressPicker = true },
            onSave = {
                viewModel.saveCustomer(
                    id = editingCustomerId ?: 0L,
                    name = cName, phone = cPhone, address = cAddress,
                    balance = cBalance.toDoubleOrNull() ?: 0.0,
                    latitude = cLatitude, longitude = cLongitude,
                    geoRadiusMeters = cGeoRadius, isGeoLocationRequired = cIsGeoRequired,
                    customerNumber = cCustomerNumber, barcode = cBarcode
                )
                editingCustomerId = null; showAddDialog = false
            },
            onDismiss = { showAddDialog = false; editingCustomerId = null }
        )
    }

    if (showMapAddressPicker) {
        MapAddressPickerDialog(
            initialLatitude = cLatitude, initialLongitude = cLongitude, initialAddress = cAddress, lang = lang,
            onAddressConfirmed = { addr, lat, lon -> cAddress = addr; cLatitude = lat; cLongitude = lon; cIsGeoRequired = true; showMapAddressPicker = false },
            onDismiss = { showMapAddressPicker = false }
        )
    }
}
