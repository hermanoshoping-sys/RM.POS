package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.example.data.CustomerEntity
import com.example.data.CustomerPaymentEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.domain.FinancialCalculator
import com.example.ui.theme.POSPrimary
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import com.example.util.moneyDouble
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class CustomerStatementRowItem(
    val id: Long,
    val timestamp: Long,
    val isInvoice: Boolean,
    val title: String,
    val amount: Double,
    val progressiveBalance: Double,
    val rawSale: SaleEntity? = null,
    val rawPayment: CustomerPaymentEntity? = null
)

@Composable
fun ClientStatementDialog(
    targetClient: CustomerEntity,
    customerPayments: List<CustomerPaymentEntity>,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit,
    onViewSaleReceipt: (SaleEntity, List<SaleItemEntity>) -> Unit,
    onViewPaymentReceipt: (CustomerPaymentEntity) -> Unit,
    onEditPayment: (CustomerPaymentEntity) -> Unit,
    onDeletePayment: (CustomerPaymentEntity) -> Unit,
    onDeleteSale: (SaleEntity) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var statementFilter by remember { mutableIntStateOf(2) }
    var showStatementPrintDialog by remember { mutableStateOf(false) }

    val rowDateTimeFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    val printDateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US) }

    val clientSales by remember(targetClient.id) {
        viewModel.getSalesForCustomerPaged(targetClient.id, 500, 0)
    }.collectAsStateWithLifecycle(emptyList())

    val clientPayments = remember(customerPayments, targetClient.id) {
        customerPayments.filter { it.customerId == targetClient.id }.sortedBy { it.timestamp }
    }

    val statementTotals = remember(clientSales, clientPayments) {
        FinancialCalculator.computeStatementTotals(
            invoiceAmountsCents = clientSales.map { it.totalAmount },
            paymentAmountsCents = clientPayments.map { it.amount }
        )
    }

    val mergedRows = remember(clientSales, clientPayments) {
        val allEvents = mutableListOf<Pair<Long, Any>>()
        clientSales.forEach { allEvents.add(Pair(it.timestamp, it)) }
        clientPayments.forEach { allEvents.add(Pair(it.timestamp, it)) }
        allEvents.sortBy { it.first }

        var runningCents = 0L
        allEvents.map { (_, item) ->
            if (item is SaleEntity) {
                runningCents += item.totalAmount
                CustomerStatementRowItem(
                    id = item.id,
                    timestamp = item.timestamp,
                    isInvoice = true,
                    title = "فاتورة بيع #${item.invoiceNumber}",
                    amount = item.totalAmountDouble,
                    progressiveBalance = runningCents.moneyDouble,
                    rawSale = item
                )
            } else {
                val p = item as CustomerPaymentEntity
                runningCents -= p.amount
                CustomerStatementRowItem(
                    id = p.id,
                    timestamp = p.timestamp,
                    isInvoice = false,
                    title = "دفعة تسديد #${p.id}",
                    amount = p.amountDouble,
                    progressiveBalance = runningCents.moneyDouble,
                    rawPayment = p
                )
            }
        }.reversed()
    }

    val onPrintSale: (SaleEntity) -> Unit = { sale ->
        coroutineScope.launch {
            val itemsList = viewModel.getSaleItems(sale.id)
            onViewSaleReceipt(sale, itemsList)
        }
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
                        Text(
                            if (lang == "AR") "كشف حساب الزبون" else "Relevé de compte",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            targetClient.name,
                            color = POSPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = statementFilter == 0,
                        onClick = { statementFilter = 0 },
                        label = { Text(if (lang == "AR") "حساب الفاتورات (${clientSales.size})" else "Factures (${clientSales.size})", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = statementFilter == 1,
                        onClick = { statementFilter = 1 },
                        label = { Text(if (lang == "AR") "التسديدات (${clientPayments.size})" else "Paiements (${clientPayments.size})", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = statementFilter == 2,
                        onClick = { statementFilter = 2 },
                        label = { Text(if (lang == "AR") "معاً (${mergedRows.size})" else "Ensemble (${mergedRows.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF59E0B),
                            selectedLabelColor = Color.Black
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (statementFilter) {
                        0 -> StatementSalesTabContent(
                            clientSales = clientSales,
                            currency = currency,
                            lang = lang,
                            rowDateTimeFormat = rowDateTimeFormat,
                            onPrintSale = onPrintSale,
                            onDeleteSale = onDeleteSale
                        )
                        1 -> StatementPaymentsTabContent(
                            clientPayments = clientPayments,
                            currency = currency,
                            lang = lang,
                            rowDateTimeFormat = rowDateTimeFormat,
                            onViewPaymentReceipt = onViewPaymentReceipt,
                            onEditPayment = onEditPayment,
                            onDeletePayment = onDeletePayment
                        )
                        else -> StatementMergedTabContent(
                            mergedRows = mergedRows,
                            currency = currency,
                            lang = lang,
                            rowDateTimeFormat = rowDateTimeFormat,
                            onPrintSale = onPrintSale,
                            onViewPaymentReceipt = onViewPaymentReceipt,
                            onEditPayment = onEditPayment,
                            onDeletePayment = onDeletePayment,
                            onDeleteSale = onDeleteSale
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                StatementTotalsSummaryCard(
                    statementTotals = statementTotals,
                    currency = currency,
                    lang = lang
                )
            }
        }
    }

    if (showStatementPrintDialog) {
        val statementHeaders = listOf(
            if (lang == "AR") "البيان" else "Libellé",
            if (lang == "AR") "التاريخ" else "Date",
            if (lang == "AR") "المبلغ" else "Montant",
            if (lang == "AR") "الرصيد" else "Solde"
        )
        val statementRows = mergedRows.reversed().map { item ->
            listOf(
                item.title,
                printDateFormat.format(Date(item.timestamp)),
                "${if (item.isInvoice) "+" else "-"}${String.format(Locale.US, "%.2f", item.amount)}",
                String.format(Locale.US, "%.2f", item.progressiveBalance)
            )
        }
        val statementSummaryLines = listOf(
            Pair(if (lang == "AR") "إجمالي الفواتير:" else "Total Factures:", MoneyUtils.format(statementTotals.totalInvoices, currency)),
            Pair(if (lang == "AR") "إجمالي التسديدات:" else "Total Paiements:", MoneyUtils.format(statementTotals.totalPayments, currency)),
            Pair(if (lang == "AR") "الرصيد النهائي (الفرق):" else "Solde Final (Différence):", MoneyUtils.format(statementTotals.netDifference, currency))
        )

        UniversalPrintListDialog(
            title = if (lang == "AR") "كشف حساب الزبون: ${targetClient.name}" else "RELEVÉ CLIENT: ${targetClient.name}",
            subtitle = if (lang == "AR") "سجل الفواتير والدفعات المتسلسلة" else "Historique Factures & Règlements",
            headers = statementHeaders,
            rows = statementRows,
            summaryLines = statementSummaryLines,
            viewModel = viewModel,
            lang = lang,
            onDismiss = { showStatementPrintDialog = false }
        )
    }
}
