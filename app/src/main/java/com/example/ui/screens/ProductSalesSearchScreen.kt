package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProductEntity
import com.example.data.SupplierEntity
import com.example.ui.viewmodels.PosViewModel
import com.example.util.ActiveSort
import com.example.util.MoneyUtils
import com.example.util.SortDirection
import com.example.util.SortingComparators
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSalesSearchScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: ((String) -> Unit)? = null
) {
    BackHandler { onNavigate?.invoke("DASHBOARD") }

    val context = LocalContext.current
    val salesHistory by viewModel.productSalesHistory.collectAsStateWithLifecycle()
    val stockMovements by viewModel.recentStockMovements.collectAsStateWithLifecycle()
    val productsList by viewModel.products.collectAsStateWithLifecycle()
    val suppliersList by viewModel.suppliers.collectAsStateWithLifecycle()
    val purchasesList by viewModel.purchases.collectAsStateWithLifecycle()
    val purchaseItemsList by viewModel.purchaseItems.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val storeSettings by viewModel.settings.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPeriodFilter by remember { mutableStateOf("ALL") }
    var selectedProductFilter by remember { mutableStateOf<ProductEntity?>(null) }
    var showProductFilterDialog by remember { mutableStateOf(false) }
    var selectedSupplierFilter by remember { mutableStateOf<SupplierEntity?>(null) }
    var showSupplierFilterDialog by remember { mutableStateOf(false) }
    var selectedEntryForDetail by remember { mutableStateOf<SalesJournalEntry?>(null) }
    var selectedProductForAction by remember { mutableStateOf<ProductEntity?>(null) }
    var showPrintListDialog by remember { mutableStateOf(false) }
    var visibleLimit by remember { mutableIntStateOf(120) }

    val (todayStart, yesterdayStart, weekStart, monthStart, days30Start) = remember {
        val now = System.currentTimeMillis()
        val cToday = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val tStart = cToday.timeInMillis
        val mStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        listOf(tStart, tStart - 86_400_000L, tStart - 6L * 86_400_000L, mStart, now - 30L * 86_400_000L)
    }

    val scanner = remember {
        GmsBarcodeScanning.getClient(context, GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).enableAutoZoom().build())
    }

    val allJournalEntries = remember(salesHistory, stockMovements, productsList, suppliersList, purchasesList, purchaseItemsList) {
        buildSalesJournalEntries(salesHistory, stockMovements, productsList, suppliersList, purchasesList, purchaseItemsList)
    }

    val filteredEntries = remember(allJournalEntries, searchQuery, selectedPeriodFilter, selectedProductFilter, selectedSupplierFilter) {
        val q = searchQuery.trim().lowercase()
        val prodFilter = selectedProductFilter
        val supFilter = selectedSupplierFilter
        allJournalEntries.filter { entry ->
            if (prodFilter != null) {
                val matchesProduct = entry.productId == prodFilter.id || entry.productName.equals(prodFilter.name, ignoreCase = true)
                if (!matchesProduct) return@filter false
            }
            if (supFilter != null) {
                val supName = supFilter.name.lowercase()
                val isSupplierMatched = entry.type == MovementType.SUPPLIER_RETURN &&
                    (entry.accountName.lowercase().contains(supName) || supName.contains(entry.accountName.lowercase().replace("مورد:", "").trim()))
                if (!isSupplierMatched) return@filter false
            }
            val periodMatch = when (selectedPeriodFilter) {
                "TODAY" -> entry.timestamp >= todayStart
                "YESTERDAY" -> entry.timestamp in yesterdayStart until todayStart
                "WEEK" -> entry.timestamp >= weekStart
                "MONTH" -> entry.timestamp >= monthStart
                "DAYS_30" -> entry.timestamp >= days30Start
                else -> true
            }
            if (!periodMatch) return@filter false
            q.isBlank() || entry.productName.lowercase().contains(q) || entry.invoiceNumber.lowercase().contains(q) || entry.accountName.lowercase().contains(q)
        }
    }

    var journalSortState by remember { mutableStateOf(ActiveSort(SalesJournalSortCol.DATE, SortDirection.DESCENDING)) }

    val sortedJournalEntries = remember(filteredEntries, journalSortState) {
        filteredEntries.sortedWith { a, b ->
            val cmp = when (journalSortState.column) {
                SalesJournalSortCol.DATE -> SortingComparators.compareTimestamps(a.timestamp, b.timestamp)
                SalesJournalSortCol.PRODUCT -> SortingComparators.compareNames(a.productName, b.productName)
                SalesJournalSortCol.ACCOUNT -> SortingComparators.compareNames(a.accountName, b.accountName)
                SalesJournalSortCol.INVOICE -> SortingComparators.compareBarcodes(a.invoiceNumber, b.invoiceNumber)
                SalesJournalSortCol.QTY -> SortingComparators.compareDoubles(a.displayQuantity, b.displayQuantity)
                SalesJournalSortCol.PURCHASE_PRICE -> SortingComparators.compareDoubles(a.purchasePrice, b.purchasePrice)
                SalesJournalSortCol.SELL_PRICE -> SortingComparators.compareDoubles(a.sellPrice, b.sellPrice)
                SalesJournalSortCol.PROFIT -> SortingComparators.compareDoubles(a.profit, b.profit)
            }
            if (journalSortState.direction == SortDirection.ASCENDING) cmp else -cmp
        }
    }

    val visibleEntries = remember(sortedJournalEntries, visibleLimit) {
        if (sortedJournalEntries.size > visibleLimit) sortedJournalEntries.take(visibleLimit) else sortedJournalEntries
    }

    val totals = remember(filteredEntries) { computeSalesJournalTotals(filteredEntries) }
    val singleProductStats = remember(filteredEntries, selectedProductFilter) { computeSingleProductStats(filteredEntries, selectedProductFilter) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }
    val tableDateFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }
    val exportDateFormatter = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.US) }

    val periodSubtitle = when (selectedPeriodFilter) {
        "TODAY" -> "اليوم"; "YESTERDAY" -> "أمس"; "WEEK" -> "آخر 7 أيام"; "MONTH" -> "هذا الشهر"; "DAYS_30" -> "آخر 30 يوم"; else -> "الكل"
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF070D18)).imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1E293B)).clickable { onNavigate?.invoke("DASHBOARD") }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(if (lang == "AR") "سجل المبيعات" else "Journal des Ventes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (lang == "AR") "الربح • ثمن البيع والشراء • الكمية • رقم الفاتورة • compte • المنتج • التاريخ" else "Audit & Mouvements Ventes", color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E293B)).border(BorderStroke(1.2.dp, Color(0xFF22C55E)), RoundedCornerShape(8.dp)).clickable {
                        if (filteredEntries.isNotEmpty()) showPrintListDialog = true else Toast.makeText(context, if (lang == "AR") "لا توجد بيانات للطباعة" else "Aucune vente", Toast.LENGTH_SHORT).show()
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Print, contentDescription = "Print", tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp)) }

                Surface(
                    shape = RoundedCornerShape(6.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.size(width = 34.dp, height = 36.dp).clickable {
                        if (filteredEntries.isEmpty()) Toast.makeText(context, if (lang == "AR") "لا توجد بيانات للمشاركة" else "Aucune donnée", Toast.LENGTH_SHORT).show()
                        else shareDocument(
                            context = context, docTitle = if (lang == "AR") "سجل المبيعات" else "Journal_Ventes",
                            subtitle = "سجل المبيعات - $periodSubtitle" + (selectedProductFilter?.let { " (${it.name})" } ?: "") + (selectedSupplierFilter?.let { " [المورد: ${it.name}]" } ?: ""),
                            headers = listOf("التاريخ", "المنتج", "compte", "رقم الفاتورة", "الكمية", "ثمن الشراء", "ثمن البيع", "الربح"),
                            rows = filteredEntries.map {
                                listOf(exportDateFormatter.format(Date(it.timestamp)), it.productName, it.accountName, it.invoiceNumber, formatQtyString(it.displayQuantity), String.format(Locale.US, "%.2f", it.purchasePrice), String.format(Locale.US, "%.2f", it.sellPrice), formatMoneyString(it.profit))
                            },
                            summaryLines = listOf(
                                (if (lang == "AR") "إجمالي العمليات:" else "Nombre d'opérations:") to "${filteredEntries.size}",
                                (if (lang == "AR") "مجموع الكمية المحسوبة:" else "Total quantité:") to formatQtyString(totals.totalCalculatedQty),
                                (if (lang == "AR") "مجموع ثمن الشراء:" else "Coût d'achat:") to String.format(Locale.US, "%.2f %s", totals.totalPurchaseCost, currency),
                                (if (lang == "AR") "مجموع ثمن البيع:" else "TOTAL VENTES:") to String.format(Locale.US, "%.2f %s", totals.totalSalesAmount, currency),
                                (if (lang == "AR") "صافي الربح الإجمالي:" else "TOTAL BENEFICE:") to String.format(Locale.US, "%s%.2f %s", if (totals.totalProfitAmount >= 0) "+" else "", totals.totalProfitAmount, currency)
                            ),
                            asPdf = true, shopName = storeSettings.shopName
                        )
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().padding(2.dp)) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF", tint = Color(0xFFDC2626), modifier = Modifier.size(17.dp))
                        Text("PDF", color = Color(0xFFDC2626), fontSize = 7.5.sp, fontWeight = FontWeight.Black)
                    }
                }

                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF107C41)).clickable { exportSalesJournalToCsv(context, filteredEntries, dateFormat) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("X", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(1.dp))
                        Icon(Icons.Filled.TableChart, contentDescription = "Excel", tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it; visibleLimit = 120 },
                modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(10.dp),
                placeholder = { Text(if (lang == "AR") "ابحث بالمنتج، رقم الفاتورة، الزبون/compte، الباركود..." else "Recherche produit, facture, compte...", fontSize = 11.5.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color(0xFF38BDF8), modifier = Modifier.size(19.dp)) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Box(
                            modifier = Modifier.size(width = 40.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)).background(Color.White).border(BorderStroke(1.5.dp, Color(0xFF2563EB)), RoundedCornerShape(6.dp)).clickable {
                                runCatching {
                                    scanner.startScan().addOnSuccessListener { barcode ->
                                        barcode.rawValue?.takeIf { it.isNotBlank() }?.let { searchQuery = it }
                                    }.addOnFailureListener { Toast.makeText(context, "فشل المسح: ${it.message}", Toast.LENGTH_SHORT).show() }
                                }.onFailure { Toast.makeText(context, "الماسح غير متاح", Toast.LENGTH_SHORT).show() }
                            },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan Barcode", tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp)) }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E293B), unfocusedContainerColor = Color(0xFF1E293B), focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = Color(0xFF334155), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                singleLine = true
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            val activeProdFilter = selectedProductFilter
            Surface(
                color = if (activeProdFilter != null) Color(0xFF166534) else Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.2.dp, if (activeProdFilter != null) Color(0xFF22C55E) else Color(0xFF38BDF8)),
                modifier = Modifier.clickable { showProductFilterDialog = true }
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Inventory2, contentDescription = null, tint = if (activeProdFilter != null) Color(0xFF4ADE80) else Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                    Text(if (activeProdFilter != null) "منتج: ${activeProdFilter.name.take(14)}" else (if (lang == "AR") "اختيار منتوج لحساب كميته ▾" else "Filtrer Produit ▾"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    if (activeProdFilter != null) Icon(Icons.Filled.Close, contentDescription = "Clear Product", tint = Color.White, modifier = Modifier.size(14.dp).clickable { selectedProductFilter = null })
                }
            }

            val activeSupFilter = selectedSupplierFilter
            Surface(
                color = if (activeSupFilter != null) Color(0xFF78350F) else Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.2.dp, if (activeSupFilter != null) Color(0xFFF59E0B) else Color(0xFFF59E0B).copy(alpha = 0.6f)),
                modifier = Modifier.clickable { showSupplierFilterDialog = true }
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = if (activeSupFilter != null) Color(0xFFFDE047) else Color(0xFFF59E0B), modifier = Modifier.size(15.dp))
                    Text(if (activeSupFilter != null) "المورد: ${activeSupFilter.name.take(14)}" else (if (lang == "AR") "الموردون ▾" else "Fournisseurs ▾"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    if (activeSupFilter != null) Icon(Icons.Filled.Close, contentDescription = "Clear Supplier", tint = Color.White, modifier = Modifier.size(14.dp).clickable { selectedSupplierFilter = null })
                }
            }

            val periodOptions = listOf(
                "ALL" to if (lang == "AR") "الكل" else "Tout",
                "TODAY" to if (lang == "AR") "اليوم" else "Aujourd'hui",
                "YESTERDAY" to if (lang == "AR") "أمس" else "Hier",
                "WEEK" to if (lang == "AR") "آخر 7 أيام" else "7 jours",
                "MONTH" to if (lang == "AR") "هذا الشهر" else "Ce mois",
                "DAYS_30" to if (lang == "AR") "30 يوم" else "30 jours"
            )
            periodOptions.forEach { (key, title) ->
                val isSelected = selectedPeriodFilter == key
                Surface(
                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)),
                    modifier = Modifier.clickable { selectedPeriodFilter = key; visibleLimit = 120 }
                ) {
                    Text(title, color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }

        val bannerProd = selectedProductFilter
        if (singleProductStats != null && bannerProd != null) {
            SingleProductStatsBanner(bannerProd, singleProductStats, onClear = { selectedProductFilter = null })
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SalesStatBox(if (lang == "AR") "صافي الربح" else "Bénéfice Net", String.format(Locale.US, "%s%.2f", if (totals.totalProfitAmount >= 0) "+" else "", totals.totalProfitAmount), Color(0xFF8CC63F), Modifier.weight(1f))
                SalesStatBox(if (lang == "AR") "تكلفة الشراء" else "Coût d'Achat", String.format(Locale.US, "%.2f", totals.totalPurchaseCost), Color(0xFF00B0FF), Modifier.weight(1f))
                SalesStatBox(if (lang == "AR") "اجمالي المبيعات" else "Total Ventes", String.format(Locale.US, "%.2f", totals.totalSalesAmount), Color(0xFFF5A67B), Modifier.weight(1f))
            }
        }

        MovementLegendRow()

        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp, vertical = 2.dp).background(Color.White).border(BorderStroke(1.5.dp, Color.Black))) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val minTableWidth = 680.dp
                    val tableWidth = if (maxWidth > minTableWidth) maxWidth else minTableWidth
                    Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        Column(modifier = Modifier.width(tableWidth).fillMaxHeight()) {
                            val dateTitle = if (lang == "AR") "التاريخ" else "Date"
                            val productTitle = if (lang == "AR") "المنتج" else "Produit"
                            val accountTitle = "compte"
                            val invoiceTitle = if (lang == "AR") "رقم الفاتورة" else "N° Fact."
                            val qtyTitle = if (lang == "AR") "الكمية" else "Qté"
                            val buyPriceTitle = if (lang == "AR") "ثمن الشراء" else "P. Achat"
                            val sellPriceTitle = if (lang == "AR") "ثمن البيع" else "P. Vente"
                            val profitTitle = if (lang == "AR") "الربح" else "Bénéfice"

                            Row(modifier = Modifier.fillMaxWidth().height(32.dp).background(Color(0xFFFF0000)), verticalAlignment = Alignment.CenterVertically) {
                                LedgerHeaderCell(if (journalSortState.column == SalesJournalSortCol.DATE) "$dateTitle ${journalSortState.direction.symbol}" else dateTitle, Modifier.weight(0.85f)) { journalSortState = journalSortState.toggleOrSwitch(SalesJournalSortCol.DATE) }
                                LedgerHeaderCell(if (journalSortState.column == SalesJournalSortCol.PRODUCT) "$productTitle ${journalSortState.direction.symbol}" else productTitle, Modifier.weight(1.80f)) { journalSortState = journalSortState.toggleOrSwitch(SalesJournalSortCol.PRODUCT) }
                                LedgerHeaderCell(if (journalSortState.column == SalesJournalSortCol.ACCOUNT) "$accountTitle ${journalSortState.direction.symbol}" else accountTitle, Modifier.weight(1.45f)) { journalSortState = journalSortState.toggleOrSwitch(SalesJournalSortCol.ACCOUNT) }
                                LedgerHeaderCell(if (journalSortState.column == SalesJournalSortCol.INVOICE) "$invoiceTitle ${journalSortState.direction.symbol}" else invoiceTitle, Modifier.weight(0.70f)) { journalSortState = journalSortState.toggleOrSwitch(SalesJournalSortCol.INVOICE) }
                                LedgerHeaderCell(if (journalSortState.column == SalesJournalSortCol.QTY) "$qtyTitle ${journalSortState.direction.symbol}" else qtyTitle, Modifier.weight(0.65f)) { journalSortState = journalSortState.toggleOrSwitch(SalesJournalSortCol.QTY) }
                                LedgerHeaderCell(if (journalSortState.column == SalesJournalSortCol.PURCHASE_PRICE) "$buyPriceTitle ${journalSortState.direction.symbol}" else buyPriceTitle, Modifier.weight(0.85f)) { journalSortState = journalSortState.toggleOrSwitch(SalesJournalSortCol.PURCHASE_PRICE) }
                                LedgerHeaderCell(if (journalSortState.column == SalesJournalSortCol.SELL_PRICE) "$sellPriceTitle ${journalSortState.direction.symbol}" else sellPriceTitle, Modifier.weight(0.85f)) { journalSortState = journalSortState.toggleOrSwitch(SalesJournalSortCol.SELL_PRICE) }
                                LedgerHeaderCell(if (journalSortState.column == SalesJournalSortCol.PROFIT) "$profitTitle ${journalSortState.direction.symbol}" else profitTitle, Modifier.weight(0.85f)) { journalSortState = journalSortState.toggleOrSwitch(SalesJournalSortCol.PROFIT) }
                            }

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(visibleEntries, key = { it.id }, contentType = { "sales_journal_row" }) { entry ->
                                    SalesJournalTableRow(
                                        entry = entry,
                                        dateText = tableDateFormatter.format(Date(entry.timestamp)),
                                        onClick = { selectedEntryForDetail = entry }
                                    )
                                }
                                if (sortedJournalEntries.size > visibleEntries.size) {
                                    item(key = "load_more_journal", contentType = "load_more") {
                                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            OutlinedButton(onClick = { visibleLimit += 120 }, modifier = Modifier.testTag("sales_search_load_more_button")) {
                                                Text(if (lang == "AR") "تحميل المزيد (${visibleEntries.size} / ${sortedJournalEntries.size})" else "Charger plus (${visibleEntries.size} / ${sortedJournalEntries.size})", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                                val emptyRowsCount = (16 - visibleEntries.size).coerceAtLeast(0)
                                items(emptyRowsCount, key = { idx -> "empty_row_$idx" }, contentType = { "empty_row" }) {
                                    Row(modifier = Modifier.fillMaxWidth().height(30.dp).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.weight(0.85f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                        Box(modifier = Modifier.weight(1.80f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                        Box(modifier = Modifier.weight(1.45f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                        Box(modifier = Modifier.weight(0.70f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                        Box(modifier = Modifier.weight(0.65f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                        Box(modifier = Modifier.weight(0.85f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                        Box(modifier = Modifier.weight(0.85f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                        Box(modifier = Modifier.weight(0.85f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(4.80f).fillMaxHeight().background(Color(0xFFFFFF00)).border(BorderStroke(1.dp, Color.Black)), contentAlignment = Alignment.Center) {
                                    val totalLabel = selectedProductFilter?.let { "مجموع كمية: ${it.name}" } ?: if (lang == "AR") "المجموع" else "TOTAL"
                                    Text(totalLabel, fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Box(modifier = Modifier.weight(0.65f).fillMaxHeight().background(Color(0xFFFFFF00)).border(BorderStroke(1.dp, Color.Black)), contentAlignment = Alignment.Center) {
                                    Text(formatQtyString(totals.totalCalculatedQty), fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Black)
                                }
                                Box(modifier = Modifier.weight(0.85f).fillMaxHeight().background(Color(0xFFFFFF00)).border(BorderStroke(1.dp, Color.Black)), contentAlignment = Alignment.Center) {
                                    Text(String.format(Locale.US, "%.2f", totals.totalPurchaseCost), fontWeight = FontWeight.Black, fontSize = 10.sp, color = Color.Black)
                                }
                                Box(modifier = Modifier.weight(0.85f).fillMaxHeight().background(Color(0xFFFFFF00)).border(BorderStroke(1.dp, Color.Black)), contentAlignment = Alignment.Center) {
                                    Text(String.format(Locale.US, "%.2f", totals.totalSalesAmount), fontWeight = FontWeight.Black, fontSize = 10.sp, color = Color.Black)
                                }
                                Box(modifier = Modifier.weight(0.85f).fillMaxHeight().background(Color(0xFFFFFF00)).border(BorderStroke(1.dp, Color.Black)), contentAlignment = Alignment.Center) {
                                    Text(String.format(Locale.US, "%s%.2f", if (totals.totalProfitAmount >= 0) "+" else "", totals.totalProfitAmount), fontWeight = FontWeight.Black, fontSize = 10.sp, color = if (totals.totalProfitAmount >= 0) Color(0xFF1D4ED8) else Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProductFilterDialog) {
        SalesJournalProductFilterDialog(
            productsList = productsList, selectedProduct = selectedProductFilter,
            onSelect = { selectedProductFilter = it; showProductFilterDialog = false },
            onDismiss = { showProductFilterDialog = false }
        )
    }

    if (showSupplierFilterDialog) {
        SalesJournalSupplierFilterDialog(
            suppliersList = suppliersList, selectedSupplier = selectedSupplierFilter, currency = currency, lang = lang,
            onSelect = { selectedSupplierFilter = it; showSupplierFilterDialog = false },
            onDismiss = { showSupplierFilterDialog = false }
        )
    }

    selectedEntryForDetail?.let { detailEntry ->
        SalesJournalEntryDetailDialog(
            entry = detailEntry, productsList = productsList, suppliersList = suppliersList,
            dateFormat = dateFormat, currency = currency, lang = lang,
            onFilterProduct = { selectedProductFilter = it; selectedEntryForDetail = null },
            onInspectProductActions = { selectedProductForAction = it; selectedEntryForDetail = null },
            onFilterSupplier = { selectedSupplierFilter = it; selectedEntryForDetail = null },
            onOpenInvoices = { isPurchase -> selectedEntryForDetail = null; onNavigate?.invoke(if (isPurchase) "PURCHASE_INVOICES" else "SALES_INVOICES") },
            onDismiss = { selectedEntryForDetail = null }
        )
    }

    if (showPrintListDialog) {
        UniversalPrintListDialog(
            title = if (lang == "AR") "سجل المبيعات" else "Journal de Vente des Produits",
            subtitle = periodSubtitle + (selectedProductFilter?.let { " - ${it.name}" } ?: "") + (selectedSupplierFilter?.let { " - المورد: ${it.name}" } ?: ""),
            headers = listOf("التاريخ", "المنتج", "compte", "رقم الفاتورة", "الكمية", "ثمن الشراء", "ثمن البيع", "الربح"),
            rows = filteredEntries.map { s ->
                listOf(exportDateFormatter.format(Date(s.timestamp)), s.productName, s.accountName, s.invoiceNumber, formatQtyString(s.displayQuantity), String.format(Locale.US, "%.2f", s.purchasePrice), String.format(Locale.US, "%.2f", s.sellPrice), formatMoneyString(s.profit))
            },
            summaryLines = listOf(
                (if (lang == "AR") "إجمالي عدد العمليات:" else "Nombre d'opérations:") to "${filteredEntries.size}",
                (if (lang == "AR") "مجموع الكمية المحسوبة:" else "Total quantité:") to formatQtyString(totals.totalCalculatedQty),
                (if (lang == "AR") "مجموع ثمن الشراء:" else "Coût d'achat:") to String.format(Locale.US, "%.2f %s", totals.totalPurchaseCost, currency),
                (if (lang == "AR") "مجموع ثمن البيع:" else "TOTAL VENTES:") to String.format(Locale.US, "%.2f %s", totals.totalSalesAmount, currency),
                (if (lang == "AR") "صافي الربح الإجمالي:" else "TOTAL BENEFICE:") to String.format(Locale.US, "%s%.2f %s", if (totals.totalProfitAmount >= 0) "+" else "", totals.totalProfitAmount, currency)
            ),
            viewModel = viewModel, lang = lang, onDismiss = { showPrintListDialog = false }
        )
    }

    selectedProductForAction?.let { prod ->
        ProductActionDialog(
            productName = prod.name, barcode = prod.barcode.ifBlank { "-" },
            sellPriceStr = MoneyUtils.format(prod.sellPriceDouble, currency),
            costPriceStr = MoneyUtils.format(prod.purchasePriceDouble, currency),
            currentStockStr = "${prod.currentStock} ${prod.unit}",
            categoryName = prod.categoryName.ifBlank { "-" },
            onEdit = { selectedProductForAction = null; onNavigate?.invoke("INVENTORY") },
            onSellOrAdd = { viewModel.addToCart(prod); selectedProductForAction = null; onNavigate?.invoke("POS") },
            onStockAdjust = { selectedProductForAction = null; onNavigate?.invoke("INVENTORY") },
            onAuditHistory = { selectedProductFilter = prod; selectedProductForAction = null },
            onDelete = { viewModel.deleteProduct(prod); selectedProductForAction = null },
            onDismiss = { selectedProductForAction = null }
        )
    }
}
