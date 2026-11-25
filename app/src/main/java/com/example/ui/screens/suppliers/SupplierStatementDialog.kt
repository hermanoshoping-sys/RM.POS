package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PurchaseEntity
import com.example.data.SupplierEntity
import com.example.data.SupplierPaymentEntity
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import com.example.util.moneyDouble
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private data class SupStatementRowItem(
    val uniqueKey: String,
    val id: Long,
    val timestamp: Long,
    val isPurchase: Boolean,
    val title: String,
    val amount: Double,
    val progressiveBalance: Double,
    val rawPurchase: PurchaseEntity? = null,
    val rawPayment: SupplierPaymentEntity? = null
)

@Composable
internal fun SupplierStatementDialog(
    supplier: SupplierEntity,
    purchasesList: List<PurchaseEntity>,
    supplierPayments: List<SupplierPaymentEntity>,
    lang: String,
    viewModel: PosViewModel,
    onPrintPurchase: (PurchaseEntity) -> Unit,
    onViewPaymentReceipt: (SupplierPaymentEntity) -> Unit,
    onEditPayment: (SupplierPaymentEntity) -> Unit,
    onDeletePayment: (SupplierPaymentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var statementFilter by remember(supplier.id) { mutableStateOf(2) }
    var showStatementPrintDialog by remember { mutableStateOf(false) }

    val dateTimeFmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    val dateShortFmt = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US) }

    val supPurchases = remember(purchasesList, supplier.id) {
        purchasesList.filter { it.supplierId == supplier.id }.sortedBy { it.timestamp }
    }
    val supPayments = remember(supplierPayments, supplier.id) {
        supplierPayments.filter { it.supplierId == supplier.id }.sortedBy { it.timestamp }
    }

    val totalPurchasesCents = remember(supPurchases) { supPurchases.sumOf { it.totalAmount } }
    val totalPaymentsCents = remember(supPayments) { supPayments.sumOf { it.amount } }
    val netDifferenceCents = totalPurchasesCents - totalPaymentsCents
    val totalPurchasesAmount = totalPurchasesCents.moneyDouble
    val totalPaymentsAmount = totalPaymentsCents.moneyDouble
    val netDifference = netDifferenceCents.moneyDouble

    val mergedRows = remember(supPurchases, supPayments) {
        val allEvents = buildList {
            supPurchases.forEach { add(it.timestamp to it) }
            supPayments.forEach { add(it.timestamp to it) }
        }.sortedBy { it.first }

        var runningCents = 0L
        allEvents.map { (_, item) ->
            if (item is PurchaseEntity) {
                runningCents += item.totalAmount
                SupStatementRowItem(
                    uniqueKey = "p_${item.id}",
                    id = item.id,
                    timestamp = item.timestamp,
                    isPurchase = true,
                    title = "فاتورة شراء #${item.id}",
                    amount = item.totalAmountDouble,
                    progressiveBalance = runningCents.moneyDouble,
                    rawPurchase = item
                )
            } else {
                val p = item as SupplierPaymentEntity
                runningCents -= p.amount
                SupStatementRowItem(
                    uniqueKey = "pay_${p.id}",
                    id = p.id,
                    timestamp = p.timestamp,
                    isPurchase = false,
                    title = "دفعة تسديد #${p.id}",
                    amount = p.amountDouble,
                    progressiveBalance = runningCents.moneyDouble,
                    rawPayment = p
                )
            }
        }.reversed()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(8.dp)
                .shadow(12.dp, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(if (lang == "AR") "كشف حساب المورد" else "Relevé Fournisseur", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(supplier.name, color = Color(0xFF38BDF8), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { showStatementPrintDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "AR") "طباعة الكشف" else "Imprimer", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = statementFilter == 0,
                        onClick = { statementFilter = 0 },
                        label = { Text(if (lang == "AR") "حساب الفاتورات (${supPurchases.size})" else "Factures (${supPurchases.size})", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = statementFilter == 1,
                        onClick = { statementFilter = 1 },
                        label = { Text(if (lang == "AR") "التسديدات (${supPayments.size})" else "Paiements (${supPayments.size})", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = statementFilter == 2,
                        onClick = { statementFilter = 2 },
                        label = { Text(if (lang == "AR") "معاً (${mergedRows.size})" else "Ensemble (${mergedRows.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFF59E0B), selectedLabelColor = Color.Black)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (statementFilter) {
                        0 -> {
                            val reversedPurchases = remember(supPurchases) { supPurchases.reversed() }
                            if (reversedPurchases.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (lang == "AR") "لا توجد فواتير شراء لهذا المورد" else "Aucune facture d'achat", color = Color.Gray)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(items = reversedPurchases, key = { it.id }, contentType = { "sup_stmt_purchase" }) { p ->
                                        Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF334155))) {
                                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("فاتورة شراء #${p.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(dateTimeFmt.format(Date(p.timestamp)), color = Color.Gray, fontSize = 11.sp)
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Text("المدفوع: ${MoneyUtils.format(p.paidAmountDouble, currency)}", color = Color(0xFF10B981), fontSize = 11.sp)
                                                        if (p.remainingDebtDouble > 0.001) {
                                                            Text("الباقي: ${MoneyUtils.format(p.remainingDebtDouble, currency)}", color = Color(0xFFEF4444), fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(MoneyUtils.format(p.totalAmountDouble, currency), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    IconButton(onClick = { onPrintPurchase(p) }, modifier = Modifier.size(32.dp).background(Color(0xFF334155), CircleShape)) {
                                                        Icon(Icons.Filled.Print, contentDescription = "طباعة الفاتورة", tint = Color.White, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            val reversedPayments = remember(supPayments) { supPayments.reversed() }
                            if (reversedPayments.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (lang == "AR") "لا توجد دفعات مسجلة لهذا المورد" else "Aucun paiement enregistré", color = Color.Gray)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(items = reversedPayments, key = { it.id }, contentType = { "sup_stmt_payment" }) { pay ->
                                        Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))) {
                                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("سند تسديد #${pay.id}", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 13.sp)
                                                    Text(dateTimeFmt.format(Date(pay.timestamp)), color = Color.Gray, fontSize = 11.sp)
                                                    if (pay.notes.isNotBlank()) Text(pay.notes, color = Color.LightGray, fontSize = 11.sp)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text("-${MoneyUtils.format(pay.amountDouble, currency)}", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 14.sp)
                                                    IconButton(onClick = { onViewPaymentReceipt(pay) }, modifier = Modifier.size(28.dp).background(Color(0xFF10B981), CircleShape)) {
                                                        Icon(Icons.Filled.Print, contentDescription = "طباعة السند", tint = Color.White, modifier = Modifier.size(14.dp))
                                                    }
                                                    IconButton(onClick = { onEditPayment(pay) }, modifier = Modifier.size(28.dp).background(Color(0xFF3B82F6), CircleShape)) {
                                                        Icon(Icons.Filled.Edit, contentDescription = "تعديل التسديد", tint = Color.White, modifier = Modifier.size(14.dp))
                                                    }
                                                    IconButton(onClick = { onDeletePayment(pay) }, modifier = Modifier.size(28.dp).background(Color(0xFFEF4444), CircleShape)) {
                                                        Icon(Icons.Filled.Delete, contentDescription = "حذف التسديد", tint = Color.White, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            if (mergedRows.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (lang == "AR") "لا توجد حركات مسجلة" else "Aucun mouvement", color = Color.Gray)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(items = mergedRows, key = { it.uniqueKey }, contentType = { "sup_stmt_merged" }) { item ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.background,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, if (item.isPurchase) Color(0xFF38BDF8).copy(alpha = 0.3f) else Color(0xFF10B981).copy(alpha = 0.4f))
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Surface(
                                                            color = if (item.isPurchase) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = if (item.isPurchase) "شراء (+)" else "تسديد (-)",
                                                                color = if (item.isPurchase) Color(0xFF38BDF8) else Color(0xFF10B981),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(dateTimeFmt.format(Date(item.timestamp)), color = Color.Gray, fontSize = 11.sp)
                                                    Text(
                                                        "الرصيد بعد الحركة: ${MoneyUtils.format(item.progressiveBalance, currency)}",
                                                        color = Color(0xFFF59E0B),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = "${if (item.isPurchase) "+" else "-"}${MoneyUtils.format(item.amount, currency)}",
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (item.isPurchase) Color.White else Color(0xFF10B981),
                                                        fontSize = 13.sp
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            if (item.isPurchase && item.rawPurchase != null) onPrintPurchase(item.rawPurchase)
                                                            else if (!item.isPurchase && item.rawPayment != null) onViewPaymentReceipt(item.rawPayment)
                                                        },
                                                        modifier = Modifier.size(28.dp).background(if (item.isPurchase) Color(0xFF334155) else Color(0xFF10B981), CircleShape)
                                                    ) {
                                                        Icon(Icons.Filled.Print, contentDescription = "طباعة مفردة", tint = Color.White, modifier = Modifier.size(14.dp))
                                                    }
                                                    if (!item.isPurchase && item.rawPayment != null) {
                                                        IconButton(onClick = { onEditPayment(item.rawPayment) }, modifier = Modifier.size(28.dp).background(Color(0xFF3B82F6), CircleShape)) {
                                                            Icon(Icons.Filled.Edit, contentDescription = "تعديل التسديد", tint = Color.White, modifier = Modifier.size(14.dp))
                                                        }
                                                        IconButton(onClick = { onDeletePayment(item.rawPayment) }, modifier = Modifier.size(28.dp).background(Color(0xFFEF4444), CircleShape)) {
                                                            Icon(Icons.Filled.Delete, contentDescription = "حذف التسديد", tint = Color.White, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (lang == "AR") "مجموع فواتير الشراء:" else "Total Achats:", color = Color.LightGray, fontSize = 12.sp)
                            Text(MoneyUtils.format(totalPurchasesAmount, currency), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (lang == "AR") "مجموع التسديدات:" else "Total Paiements:", color = Color.LightGray, fontSize = 12.sp)
                            Text("-${MoneyUtils.format(totalPaymentsAmount, currency)}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (lang == "AR") "الفرق (الرصيد المستحق):" else "Différence (Solde):",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                MoneyUtils.format(abs(netDifference), currency),
                                color = if (netDifference > 0.001) Color(0xFFEF4444) else if (netDifference < -0.001) Color(0xFF38BDF8) else Color(0xFF10B981),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStatementPrintDialog) {
        UniversalPrintListDialog(
            title = if (lang == "AR") "كشف حساب المورد: ${supplier.name}" else "RELEVÉ FOURNISSEUR: ${supplier.name}",
            subtitle = if (lang == "AR") "سجل فواتير التوريد والتسديدات" else "Factures & Règlements",
            headers = listOf(if (lang == "AR") "البيان" else "Libellé", if (lang == "AR") "التاريخ" else "Date", if (lang == "AR") "المبلغ" else "Montant", if (lang == "AR") "الرصيد" else "Solde"),
            rows = mergedRows.reversed().map { item ->
                listOf(item.title, dateShortFmt.format(Date(item.timestamp)), "${if (item.isPurchase) "+" else "-"}${String.format(Locale.US, "%.2f", item.amount)}", String.format(Locale.US, "%.2f", item.progressiveBalance))
            },
            summaryLines = listOf(
                Pair(if (lang == "AR") "إجمالي فواتير الشراء:" else "Total Achats:", MoneyUtils.format(totalPurchasesAmount, currency)),
                Pair(if (lang == "AR") "إجمالي التسديدات:" else "Total Paiements:", MoneyUtils.format(totalPaymentsAmount, currency)),
                Pair(if (lang == "AR") "الرصيد النهائي (الفرق):" else "Solde Final (Différence):", MoneyUtils.format(netDifference, currency))
            ),
            viewModel = viewModel,
            lang = lang,
            onDismiss = { showStatementPrintDialog = false }
        )
    }
}
