package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerEntity
import com.example.data.CustomerPaymentEntity
import com.example.data.SupplierEntity
import com.example.data.SupplierPaymentEntity
import com.example.domain.FinancialCalculator
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun PaymentsScreenDialogs(
    viewModel: PosViewModel,
    lang: String,
    currency: String,
    selectedTab: Int,
    selectedDateFilter: String,
    dateFormatter: SimpleDateFormat,
    customersList: List<CustomerEntity>,
    suppliersList: List<SupplierEntity>,
    filteredCustomerPayments: List<CustomerPaymentEntity>,
    filteredSupplierPayments: List<SupplierPaymentEntity>,
    showAddCustomerPaymentDialog: Boolean,
    onDismissAddCustomerPayment: () -> Unit,
    showAddSupplierPaymentDialog: Boolean,
    onDismissAddSupplierPayment: () -> Unit,
    viewingCustomerReceipt: CustomerPaymentEntity?,
    onViewCustomerReceipt: (CustomerPaymentEntity?) -> Unit,
    viewingSupplierReceipt: SupplierPaymentEntity?,
    onViewSupplierReceipt: (SupplierPaymentEntity?) -> Unit,
    paymentToEditCustomer: CustomerPaymentEntity?,
    onEditCustomerPaymentDismiss: () -> Unit,
    paymentToDeleteCustomer: CustomerPaymentEntity?,
    onDeleteCustomerPaymentDismiss: () -> Unit,
    paymentToEditSupplier: SupplierPaymentEntity?,
    onEditSupplierPaymentDismiss: () -> Unit,
    paymentToDeleteSupplier: SupplierPaymentEntity?,
    onDeleteSupplierPaymentDismiss: () -> Unit,
    showBatchPrintDialog: Boolean,
    onDismissBatchPrint: () -> Unit
) {
    if (showAddCustomerPaymentDialog) {
        RecordCustomerPaymentDialog(
            customers = customersList,
            lang = lang,
            currency = currency,
            onDismiss = onDismissAddCustomerPayment,
            onConfirm = { customerId, amount, method, notes, printReceipt ->
                viewModel.recordCustomerPayment(customerId = customerId, amount = amount, paymentMethod = method, notes = notes) { created ->
                    onDismissAddCustomerPayment()
                    if (printReceipt && created != null) onViewCustomerReceipt(created)
                }
            }
        )
    }

    if (showAddSupplierPaymentDialog) {
        RecordSupplierPaymentDialog(
            suppliers = suppliersList,
            lang = lang,
            currency = currency,
            onDismiss = onDismissAddSupplierPayment,
            onConfirm = { supplierId, amount, method, notes, printReceipt ->
                viewModel.recordSupplierPayment(supplierId = supplierId, amount = amount, paymentMethod = method, notes = notes) { created ->
                    onDismissAddSupplierPayment()
                    if (printReceipt && created != null) onViewSupplierReceipt(created)
                }
            }
        )
    }

    if (viewingCustomerReceipt != null) {
        ThermalCustomerPaymentReceiptDialog(
            payment = viewingCustomerReceipt,
            viewModel = viewModel,
            lang = lang,
            onDismiss = { onViewCustomerReceipt(null) }
        )
    }

    if (viewingSupplierReceipt != null) {
        ThermalSupplierPaymentReceiptDialog(
            payment = viewingSupplierReceipt,
            viewModel = viewModel,
            lang = lang,
            onDismiss = { onViewSupplierReceipt(null) }
        )
    }

    if (paymentToEditCustomer != null) {
        EditCustomerPaymentDialog(
            payment = paymentToEditCustomer,
            lang = lang,
            currency = currency,
            onDismiss = onEditCustomerPaymentDismiss,
            onConfirm = { newAmt, newMethod, newNotes ->
                viewModel.updateCustomerPayment(
                    paymentId = paymentToEditCustomer.id,
                    newAmount = newAmt,
                    newPaymentMethod = newMethod,
                    notes = newNotes
                ) {
                    onEditCustomerPaymentDismiss()
                }
            }
        )
    }

    if (paymentToDeleteCustomer != null) {
        DeleteCustomerPaymentConfirmDialog(
            payment = paymentToDeleteCustomer,
            lang = lang,
            currency = currency,
            onDismiss = onDeleteCustomerPaymentDismiss,
            onConfirm = {
                viewModel.deleteCustomerPayment(paymentToDeleteCustomer)
                onDeleteCustomerPaymentDismiss()
            }
        )
    }

    if (paymentToEditSupplier != null) {
        EditSupplierPaymentDialog(
            payment = paymentToEditSupplier,
            lang = lang,
            currency = currency,
            onDismiss = onEditSupplierPaymentDismiss,
            onConfirm = { newAmt, newMethod, newNotes ->
                viewModel.updateSupplierPayment(
                    paymentId = paymentToEditSupplier.id,
                    newAmount = newAmt,
                    newPaymentMethod = newMethod,
                    notes = newNotes
                ) {
                    onEditSupplierPaymentDismiss()
                }
            }
        )
    }

    if (paymentToDeleteSupplier != null) {
        DeleteSupplierPaymentConfirmDialog(
            payment = paymentToDeleteSupplier,
            lang = lang,
            currency = currency,
            onDismiss = onDeleteSupplierPaymentDismiss,
            onConfirm = {
                viewModel.deleteSupplierPayment(paymentToDeleteSupplier)
                onDeleteSupplierPaymentDismiss()
            }
        )
    }

    if (showBatchPrintDialog) {
        val title = if (selectedTab == 0) {
            if (lang == "AR") "كشف مدفوعات الزبناء" else "RELEVÉ DES RÈGLEMENTS CLIENTS"
        } else {
            if (lang == "AR") "كشف مدفوعات الموردين" else "RELEVÉ DES PAIEMENTS FOURNISSEURS"
        }
        val subtitle = when (selectedDateFilter) {
            "TODAY" -> if (lang == "AR") "مدفوعات اليوم" else "Paiements d'aujourd'hui"
            "MONTH" -> if (lang == "AR") "مدفوعات هذا الشهر" else "Paiements du mois"
            else -> if (lang == "AR") "كافة المدفوعات المسجلة" else "Tous les paiements enregistrés"
        }
        val headers = if (selectedTab == 0) {
            listOf(if (lang == "AR") "رقم" else "N°", if (lang == "AR") "التاريخ" else "Date", if (lang == "AR") "الزبون" else "Client", if (lang == "AR") "المبلغ" else "Montant")
        } else {
            listOf(if (lang == "AR") "رقم" else "N°", if (lang == "AR") "التاريخ" else "Date", if (lang == "AR") "المورد" else "Fournisseur", if (lang == "AR") "المبلغ" else "Montant")
        }
        val rows = if (selectedTab == 0) {
            filteredCustomerPayments.map { p -> listOf("#${p.id}", dateFormatter.format(Date(p.timestamp)), p.customerName, MoneyUtils.format(p.amount, currency)) }
        } else {
            filteredSupplierPayments.map { p -> listOf("#${p.id}", dateFormatter.format(Date(p.timestamp)), p.supplierName, MoneyUtils.format(p.amount, currency)) }
        }
        val totalSum = if (selectedTab == 0) {
            FinancialCalculator.sumCents(filteredCustomerPayments.map { it.amount })
        } else {
            FinancialCalculator.sumCents(filteredSupplierPayments.map { it.amount })
        }
        val summaryLines = listOf(
            Pair(if (lang == "AR") "عدد الدفعات:" else "Nombre:", "${rows.size}"),
            Pair(if (lang == "AR") "المجموع العام:" else "Total:", MoneyUtils.format(totalSum, currency))
        )
        UniversalPrintListDialog(
            title = title,
            subtitle = subtitle,
            headers = headers,
            rows = rows,
            summaryLines = summaryLines,
            viewModel = viewModel,
            lang = lang,
            onDismiss = onDismissBatchPrint
        )
    }
}

@Composable
internal fun PaymentsKpiBanner(
    lang: String,
    currency: String,
    selectedTab: Int,
    debtRemainingCents: Long,
    totalPaidCents: Long
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Surface(color = Color.White, border = BorderStroke(2.dp, Color.Black), shape = RoundedCornerShape(2.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().height(68.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFE50914)).border(1.dp, Color.Black)) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                        Text(if (lang == "AR") "الديون المتبقية" else "Dettes Restantes", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.Center)
                        HorizontalDivider(color = Color.Black, thickness = 1.dp)
                        Text(MoneyUtils.format(debtRemainingCents, currency), color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF90C855)).border(1.dp, Color.Black)) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                        Text(
                            text = if (selectedTab == 0) (if (lang == "AR") "اجمالي المقبوضات" else "Total Encaissé") else (if (lang == "AR") "اجمالي المدفوعات" else "Total Payé"),
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        HorizontalDivider(color = Color.Black, thickness = 1.dp)
                        Text(MoneyUtils.format(totalPaidCents, currency), color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PaymentsSubTabsRow(
    lang: String,
    selectedTab: Int,
    customerCount: Int,
    supplierCount: Int,
    onSelectTab: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(Color(0xFF0F172A)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable { onSelectTab(0) }.testTag("tab_customer_payments").padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.People, contentDescription = null, tint = if (selectedTab == 0) Color(0xFF34D399) else Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == "AR") "مدفوعات الزبناء" else "Paiements Clients", color = if (selectedTab == 0) Color.White else Color(0xFF94A3B8), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(20.dp).background(if (selectedTab == 0) Color(0xFF10B981) else Color(0xFF334155), CircleShape), contentAlignment = Alignment.Center) {
                    Text("$customerCount", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth(0.9f).height(3.dp).background(if (selectedTab == 0) Color(0xFF10B981) else Color.Transparent, RoundedCornerShape(2.dp)))
        }
        Column(
            modifier = Modifier.weight(1f).clickable { onSelectTab(1) }.testTag("tab_supplier_payments").padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = if (selectedTab == 1) Color(0xFF60A5FA) else Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == "AR") "مدفوعات الموردين" else "Paiements Frs", color = if (selectedTab == 1) Color.White else Color(0xFF94A3B8), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(20.dp).background(if (selectedTab == 1) Color(0xFF3B82F6) else Color(0xFF334155), CircleShape), contentAlignment = Alignment.Center) {
                    Text("$supplierCount", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth(0.9f).height(3.dp).background(if (selectedTab == 1) Color(0xFF3B82F6) else Color.Transparent, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
internal fun PaymentsLoadMoreRow(loaded: Int, total: Int, lang: String, onLoadMore: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = onLoadMore,
            modifier = Modifier.testTag("payments_load_more_button")
        ) {
            Text(
                text = if (lang == "AR") "تحميل المزيد ($loaded / $total)" else "Charger plus ($loaded / $total)",
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
