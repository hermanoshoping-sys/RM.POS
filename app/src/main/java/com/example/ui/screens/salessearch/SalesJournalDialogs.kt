package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.data.ProductEntity
import com.example.data.SupplierEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SalesJournalProductFilterDialog(
    productsList: List<ProductEntity>,
    selectedProduct: ProductEntity?,
    onSelect: (ProductEntity?) -> Unit,
    onDismiss: () -> Unit
) {
    var prodSearchQuery by remember { mutableStateOf("") }
    val filteredProductsList = remember(productsList, prodSearchQuery) {
        if (prodSearchQuery.isBlank()) productsList
        else productsList.filter {
            it.name.contains(prodSearchQuery, ignoreCase = true) ||
                it.barcode.contains(prodSearchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f).padding(10.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("اختر منتوجاً لحساب كميته وإحصائياته", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    OutlinedTextField(
                        value = prodSearchQuery,
                        onValueChange = { prodSearchQuery = it },
                        placeholder = { Text("ابحث باسم المنتج أو الباركود...", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFF0F172A), unfocusedContainerColor = Color(0xFF0F172A), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Surface(
                        color = if (selectedProduct == null) Color(0xFF0284C7) else Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }
                    ) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("جميع المنتجات (إلغاء التصفية)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (selectedProduct == null) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredProductsList, key = { it.id }, contentType = { "product_filter_row" }) { product ->
                            val isSelected = selectedProduct?.id == product.id
                            Surface(
                                color = if (isSelected) Color(0xFF166534) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF22C55E) else Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(product) }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(product.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("المخزون الحالي: ${product.currentStock} قطعة • سعر البيع: ${product.sellPriceDouble}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    if (isSelected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SalesJournalSupplierFilterDialog(
    suppliersList: List<SupplierEntity>,
    selectedSupplier: SupplierEntity?,
    currency: String,
    lang: String,
    onSelect: (SupplierEntity?) -> Unit,
    onDismiss: () -> Unit
) {
    var supSearchQuery by remember { mutableStateOf("") }
    val filteredSuppliersList = remember(suppliersList, supSearchQuery) {
        if (supSearchQuery.isBlank()) suppliersList
        else suppliersList.filter { it.name.contains(supSearchQuery, ignoreCase = true) || it.phone.contains(supSearchQuery) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f).padding(10.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            Text(if (lang == "AR") "دمج وتصفية الموردين في سجل المبيعات" else "Filtrer par Fournisseur", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    OutlinedTextField(
                        value = supSearchQuery,
                        onValueChange = { supSearchQuery = it },
                        placeholder = { Text(if (lang == "AR") "ابحث باسم المورد أو رقم الهاتف..." else "Recherche fournisseur...", color = Color.Gray, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color(0xFF0F172A), unfocusedContainerColor = Color(0xFF0F172A), focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFF59E0B))
                    )

                    Surface(
                        color = if (selectedSupplier == null) Color(0xFFD97706) else Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }
                    ) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (lang == "AR") "جميع الموردين والعمليات (إلغاء التصفية)" else "Tous les fournisseurs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (selectedSupplier == null) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredSuppliersList, key = { it.id }, contentType = { "supplier_filter_row" }) { supplier ->
                            val isSelected = selectedSupplier?.id == supplier.id
                            Surface(
                                color = if (isSelected) Color(0xFF78350F) else Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(supplier) }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(supplier.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        val phoneInfo = if (supplier.phone.isNotBlank()) "هاتف: ${supplier.phone} • " else ""
                                        Text("${phoneInfo}رصيد الديون: ${String.format(Locale.US, "%.2f", supplier.debtBalanceDouble)} $currency", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    if (isSelected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFFFDE047), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SalesJournalEntryDetailDialog(
    entry: SalesJournalEntry,
    productsList: List<ProductEntity>,
    suppliersList: List<SupplierEntity>,
    dateFormat: SimpleDateFormat,
    currency: String,
    lang: String,
    onFilterProduct: (ProductEntity) -> Unit,
    onInspectProductActions: (ProductEntity) -> Unit,
    onFilterSupplier: (SupplierEntity) -> Unit,
    onOpenInvoices: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFF8CC63F)),
            modifier = Modifier.fillMaxWidth(0.92f).imePadding().padding(12.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("تفاصيل عملية سجل المبيعات", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF8CC63F))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    val typeTitle = when (entry.type) {
                        MovementType.SALE -> "عملية بيع (أزرق)"
                        MovementType.CUSTOMER_RETURN -> "استرجاع زبون (أحمر)"
                        MovementType.SUPPLIER_RETURN -> "استرجاع للمورد (أصفر)"
                        MovementType.PURCHASE -> "فاتورة مشتريات من المورد (أخضر)"
                    }
                    val typeBadgeColor = when (entry.type) {
                        MovementType.SALE -> Color(0xFF2563EB)
                        MovementType.CUSTOMER_RETURN -> Color(0xFFDC2626)
                        MovementType.SUPPLIER_RETURN -> Color(0xFFD97706)
                        MovementType.PURCHASE -> Color(0xFF16A34A)
                    }

                    Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.productName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                Surface(color = typeBadgeColor, shape = RoundedCornerShape(4.dp)) {
                                    Text(typeTitle, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("رقم الفاتورة: ${entry.invoiceNumber}", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            Text("التاريخ: ${dateFormat.format(Date(entry.timestamp))}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الطرف / الحساب (compte):", fontSize = 13.sp, color = Color.Gray)
                            Text(entry.accountName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الكمية المحسوبة في السجل:", fontSize = 13.sp, color = Color.Gray)
                            Text(formatQtyString(entry.displayQuantity), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = typeBadgeColor)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("سعر الشراء للوحدة:", fontSize = 13.sp, color = Color.Gray)
                            Text(String.format(Locale.US, "%.2f %s", entry.purchasePrice, currency), fontSize = 13.sp, color = Color(0xFFF59E0B))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("سعر البيع للوحدة:", fontSize = 13.sp, color = Color.Gray)
                            Text(String.format(Locale.US, "%.2f %s", entry.sellPrice, currency), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF38BDF8))
                        }
                        HorizontalDivider(color = Color(0xFF334155))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الربح في العملية:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            val pColor = if (entry.profit >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                            Text(String.format(Locale.US, "%s%.2f %s", if (entry.profit >= 0) "+" else "", entry.profit, currency), fontWeight = FontWeight.Black, fontSize = 15.sp, color = pColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val matchedProd = productsList.find { it.id == entry.productId || it.name == entry.productName }
                    if (matchedProd != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onFilterProduct(matchedProd) }, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFF22C55E)), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Calculate, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حساب كميات (${matchedProd.name.take(10)})", color = Color(0xFF22C55E), fontSize = 11.sp)
                            }
                            Button(onClick = { onInspectProductActions(matchedProd) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A0E9)), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("خيارات المنتج ⚙️", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    if (entry.type == MovementType.SUPPLIER_RETURN || entry.type == MovementType.PURCHASE) {
                        val cleanSupName = entry.accountName.replace("مورد:", "").trim()
                        val matchedSup = suppliersList.find { it.name.equals(cleanSupName, ignoreCase = true) || cleanSupName.contains(it.name) }
                        if (matchedSup != null) {
                            OutlinedButton(onClick = { onFilterSupplier(matchedSup) }, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFF59E0B)), modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تصفية حركات هذا المورد فقط (${matchedSup.name})", color = Color(0xFFF59E0B), fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = { onOpenInvoices(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = if (entry.type == MovementType.PURCHASE) Color(0xFF16A34A) else Color(0xFFD97706)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (lang == "AR") "الانتقال إلى فواتير المشتريات والموردين" else "Voir la facture fournisseur", color = Color.White)
                        }
                    } else {
                        Button(onClick = { onOpenInvoices(false) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (lang == "AR") "الانتقال إلى فواتير المبيعات" else "Voir la facture complète")
                        }
                    }
                }
            }
        }
    }
}

internal fun exportSalesJournalToCsv(context: Context, entries: List<SalesJournalEntry>, dateFormat: SimpleDateFormat) {
    try {
        val sb = StringBuilder()
        sb.appendLine("التاريخ,المنتج,compte,رقم الفاتورة,الكمية,ثمن الشراء,ثمن البيع,الربح")
        entries.forEach { r ->
            val d = dateFormat.format(Date(r.timestamp))
            val q = formatQtyString(r.displayQuantity)
            val p = String.format(Locale.US, "%.2f", r.profit)
            sb.appendLine("\"$d\",\"${r.productName}\",\"${r.accountName}\",\"${r.invoiceNumber}\",$q,${r.purchasePrice},${r.sellPrice},$p")
        }
        val cacheDir = File(context.cacheDir, "shared_files")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val csvFile = File(cacheDir, "sales_journal.csv")
        csvFile.writeText(sb.toString(), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "تصدير سجل المبيعات"))
    } catch (e: Exception) {
        Toast.makeText(context, "خطأ في التصدير: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
