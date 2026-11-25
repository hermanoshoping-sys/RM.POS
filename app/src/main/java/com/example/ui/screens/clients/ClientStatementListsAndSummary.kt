package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerPaymentEntity
import com.example.data.SaleEntity
import com.example.domain.FinancialCalculator
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun StatementSalesTabContent(
    clientSales: List<SaleEntity>,
    currency: String,
    lang: String,
    rowDateTimeFormat: SimpleDateFormat,
    onPrintSale: (SaleEntity) -> Unit,
    onDeleteSale: (SaleEntity) -> Unit
) {
    val reversedSales = remember(clientSales) { clientSales.reversed() }
    if (reversedSales.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (lang == "AR") "لا توجد فواتير مسجلة لهذا الزبون" else "Aucune facture enregistrée",
                color = Color.Gray
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(reversedSales, key = { it.id }, contentType = { "statement_sale" }) { sale ->
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("فاتورة #${sale.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(rowDateTimeFormat.format(Date(sale.timestamp)), color = Color.Gray, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "المدفوع: ${MoneyUtils.format(sale.paidAmountDouble, currency)}",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp
                                )
                                if (sale.remainingDueCents > 0L) {
                                    Text(
                                        "الباقي: ${MoneyUtils.format(sale.remainingDueDouble, currency)}",
                                        color = Color(0xFFEF4444),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                MoneyUtils.format(sale.totalAmountDouble, currency),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            IconButton(
                                onClick = { onPrintSale(sale) },
                                modifier = Modifier.size(28.dp).background(Color(0xFF334155), CircleShape)
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = "طباعة الفاتورة", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onDeleteSale(sale) },
                                modifier = Modifier.size(28.dp).background(Color(0xFFEF4444), CircleShape)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف الفاتورة", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatementPaymentsTabContent(
    clientPayments: List<CustomerPaymentEntity>,
    currency: String,
    lang: String,
    rowDateTimeFormat: SimpleDateFormat,
    onViewPaymentReceipt: (CustomerPaymentEntity) -> Unit,
    onEditPayment: (CustomerPaymentEntity) -> Unit,
    onDeletePayment: (CustomerPaymentEntity) -> Unit
) {
    val reversedPayments = remember(clientPayments) { clientPayments.reversed() }
    if (reversedPayments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (lang == "AR") "لا توجد دفعات مسجلة لهذا الزبون" else "Aucun paiement enregistré",
                color = Color.Gray
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(reversedPayments, key = { it.id }, contentType = { "statement_payment" }) { pay ->
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("وصل تسديد #${pay.id}", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 13.sp)
                            Text(rowDateTimeFormat.format(Date(pay.timestamp)), color = Color.Gray, fontSize = 11.sp)
                            if (pay.notes.isNotBlank()) {
                                Text(pay.notes, color = Color.LightGray, fontSize = 11.sp)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "-${MoneyUtils.format(pay.amountDouble, currency)}",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                fontSize = 14.sp
                            )
                            IconButton(
                                onClick = { onViewPaymentReceipt(pay) },
                                modifier = Modifier.size(28.dp).background(Color(0xFF10B981), CircleShape)
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = "طباعة الوصل", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onEditPayment(pay) },
                                modifier = Modifier.size(28.dp).background(Color(0xFF3B82F6), CircleShape)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "تعديل التسديد", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { onDeletePayment(pay) },
                                modifier = Modifier.size(28.dp).background(Color(0xFFEF4444), CircleShape)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف التسديد", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatementMergedTabContent(
    mergedRows: List<CustomerStatementRowItem>,
    currency: String,
    lang: String,
    rowDateTimeFormat: SimpleDateFormat,
    onPrintSale: (SaleEntity) -> Unit,
    onViewPaymentReceipt: (CustomerPaymentEntity) -> Unit,
    onEditPayment: (CustomerPaymentEntity) -> Unit,
    onDeletePayment: (CustomerPaymentEntity) -> Unit,
    onDeleteSale: (SaleEntity) -> Unit
) {
    if (mergedRows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (lang == "AR") "لا توجد حركات مسجلة" else "Aucun mouvement",
                color = Color.Gray
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                mergedRows,
                key = { (if (it.isInvoice) "S_" else "P_") + it.id },
                contentType = { if (it.isInvoice) "merged_sale" else "merged_payment" }
            ) { item ->
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (item.isInvoice) Color(0xFF38BDF8).copy(alpha = 0.3f) else Color(0xFF10B981).copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = if (item.isInvoice) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (item.isInvoice) "فاتورة (+)" else "تسديد (-)",
                                        color = if (item.isInvoice) Color(0xFF38BDF8) else Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(rowDateTimeFormat.format(Date(item.timestamp)), color = Color.Gray, fontSize = 11.sp)
                            Text(
                                "الرصيد بعد الحركة: ${MoneyUtils.format(item.progressiveBalance, currency)}",
                                color = Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${if (item.isInvoice) "+" else "-"}${MoneyUtils.format(item.amount, currency)}",
                                fontWeight = FontWeight.Bold,
                                color = if (item.isInvoice) Color.White else Color(0xFF10B981),
                                fontSize = 13.sp
                            )
                            IconButton(
                                onClick = {
                                    if (item.isInvoice && item.rawSale != null) {
                                        onPrintSale(item.rawSale)
                                    } else if (!item.isInvoice && item.rawPayment != null) {
                                        onViewPaymentReceipt(item.rawPayment)
                                    }
                                },
                                modifier = Modifier.size(28.dp).background(
                                    if (item.isInvoice) Color(0xFF334155) else Color(0xFF10B981),
                                    CircleShape
                                )
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = "طباعة مفردة", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            if (!item.isInvoice && item.rawPayment != null) {
                                IconButton(
                                    onClick = { onEditPayment(item.rawPayment) },
                                    modifier = Modifier.size(28.dp).background(Color(0xFF3B82F6), CircleShape)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "تعديل التسديد", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { onDeletePayment(item.rawPayment) },
                                    modifier = Modifier.size(28.dp).background(Color(0xFFEF4444), CircleShape)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف التسديد", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                            if (item.isInvoice && item.rawSale != null) {
                                IconButton(
                                    onClick = { onDeleteSale(item.rawSale) },
                                    modifier = Modifier.size(28.dp).background(Color(0xFFEF4444), CircleShape)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف الفاتورة", tint = Color.White, modifier = Modifier.size(14.dp))
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
internal fun StatementTotalsSummaryCard(
    statementTotals: FinancialCalculator.StatementLedgerTotals,
    currency: String,
    lang: String
) {
    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (lang == "AR") "مجموع الفواتير:" else "Total Factures:", color = Color.LightGray, fontSize = 12.sp)
                Text(MoneyUtils.format(statementTotals.totalInvoices, currency), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (lang == "AR") "مجموع التسديدات:" else "Total Paiements:", color = Color.LightGray, fontSize = 12.sp)
                Text("-${MoneyUtils.format(statementTotals.totalPayments, currency)}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (lang == "AR") "الفرق (الرصيد المستحق):" else "Différence (Solde):",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    MoneyUtils.format(statementTotals.absNetDifference, currency),
                    color = when {
                        statementTotals.customerOwesStore -> Color(0xFFEF4444)
                        statementTotals.storeOwesCustomer -> Color(0xFF38BDF8)
                        else -> Color(0xFF10B981)
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
