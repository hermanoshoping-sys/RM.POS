package com.example.ui.screens.clients

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CustomerEntity
import com.example.data.UserEntity
import com.example.domain.FinancialCalculator
import com.example.ui.screens.*
import com.example.ui.viewmodels.PosViewModel
import com.example.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun ClientsActionAndManagementDialogs(
    context: Context,
    coroutineScope: CoroutineScope,
    viewModel: PosViewModel,
    lang: String,
    storeCurrency: String,
    currentUser: UserEntity?,
    selectedClientForAction: CustomerEntity?,
    onDismissAction: () -> Unit,
    onEditCustomer: (CustomerEntity) -> Unit,
    onOpenStatement: (CustomerEntity) -> Unit,
    onOpenPayDebt: (CustomerEntity) -> Unit,
    onRequestDeleteCustomer: (CustomerEntity) -> Unit,
    onOpenRouteTracking: (CustomerEntity) -> Unit,
    onNavigate: (String) -> Unit,
    clientToDelete: CustomerEntity?,
    onDismissDelete: () -> Unit,
    showPrintListDialog: Boolean,
    onDismissPrintList: () -> Unit,
    searchVal: String,
    allClientsForPrint: List<CustomerEntity>,
    sortedClients: List<CustomerEntity>,
    geoBlockedResult: GeoFenceResult?,
    geoBlockedCustomerName: String,
    geoBlockedCustomer: CustomerEntity?,
    onUpdateGeoBlocked: (GeoFenceResult?) -> Unit,
    customerForRouteTracking: CustomerEntity?,
    onDismissRouteTracking: () -> Unit,
    showBulkDeleteConfirm: Boolean,
    selectedCustomerIds: Set<Long>,
    onBulkDeleteComplete: () -> Unit,
    onDismissBulkDelete: () -> Unit
) {
    if (selectedClientForAction != null) {
        val client = selectedClientForAction
        PartnerActionDialog(
            isClient = true,
            partyName = client.name,
            address = client.address,
            phone = client.phone,
            balanceStr = String.format(Locale.US, "%.2f", client.balanceDouble),
            isDebt = client.balance > 0L,
            locationStr = if (client.isGeoLocationRequired && client.hasGeoLocation) {
                "📍 محدد (${client.geoRadiusMeters.toInt()}م)"
            } else if (client.hasGeoLocation) {
                "محدد (اختياري)"
            } else {
                "غير محدد"
            },
            customerNumber = client.customerNumber,
            barcode = client.barcode,
            canEdit = currentUser?.hasEditCustomersAccess() ?: true,
            onEdit = {
                val canEdit = currentUser?.hasEditCustomersAccess() ?: true
                if (!canEdit) {
                    Toast.makeText(context, if (lang == "AR") "🔒 عذراً، ليس لديك صلاحية تعديل بيانات العملاء بعد إنشائهم." else "🔒 Permission refusée.", Toast.LENGTH_LONG).show()
                } else {
                    onEditCustomer(client)
                    onDismissAction()
                }
            },
            onStatement = { onOpenStatement(client); onDismissAction() },
            onPayDebt = { onOpenPayDebt(client); onDismissAction() },
            onInvoicesHistory = { onOpenStatement(client); onDismissAction() },
            onDelete = {
                val canEdit = currentUser?.hasEditCustomersAccess() ?: true
                if (!canEdit) {
                    Toast.makeText(context, if (lang == "AR") "🔒 عذراً، ليس لديك صلاحية حذف العملاء." else "🔒 Permission refusée.", Toast.LENGTH_LONG).show()
                } else {
                    onRequestDeleteCustomer(client)
                    onDismissAction()
                }
            },
            onPrintBarcode = { PdfReportExporter.printCustomerBarcodeBadge(context, client, viewModel.settings.value.shopName) },
            onNavigateRoute = { onOpenRouteTracking(client); onDismissAction() },
            onNavigate = onNavigate,
            currentUser = currentUser,
            onDismiss = onDismissAction
        )
    }

    if (clientToDelete != null) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(if (lang == "AR") "تأكيد حذف الزبون" else "Confirmer la suppression", fontWeight = FontWeight.Bold) },
            text = { Text(if (lang == "AR") "هل أنت متأكد من حذف الزبون (${clientToDelete.name}) نهائياً من قاعدة البيانات؟" else "Voulez-vous vraiment supprimer (${clientToDelete.name}) ?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(clientToDelete) { success, message ->
                            Toast.makeText(context, message, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                        }
                        onDismissDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text(if (lang == "AR") "حذف نهائي" else "Supprimer") }
            },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text(if (lang == "AR") "إلغاء" else "Annuler") } }
        )
    }

    if (showPrintListDialog) {
        val printTargetClients = if (searchVal.isBlank()) allClientsForPrint else sortedClients
        val rows = printTargetClients.map { c ->
            listOf(c.name, c.phone.ifBlank { "-" }, String.format(Locale.US, "%.2f", c.balanceDouble))
        }
        val totalDebtsDouble = FinancialCalculator.sumPositiveBalancesCents(printTargetClients.map { it.balance }).moneyDouble
        UniversalPrintListDialog(
            title = if (lang == "AR") "سجل وقائمة العملاء" else "REGISTRE DES CLIENTS",
            subtitle = if (lang == "AR") "قائمة وسجل العملاء" else "Tous les clients",
            headers = listOf(if (lang == "AR") "العميل" else "Client", if (lang == "AR") "الهاتف" else "Téléphone", if (lang == "AR") "الرصيد" else "Solde"),
            rows = rows,
            summaryLines = listOf(
                Pair(if (lang == "AR") "إجمالي عدد العملاء:" else "Total Clients:", "${printTargetClients.size}"),
                Pair(if (lang == "AR") "إجمالي الديون المستحقة:" else "Total Créances:", MoneyUtils.format(totalDebtsDouble, storeCurrency))
            ),
            viewModel = viewModel,
            lang = lang,
            onDismiss = onDismissPrintList
        )
    }

    if (geoBlockedResult != null) {
        CustomerGeoBlockedDialog(
            customerName = geoBlockedCustomerName,
            result = geoBlockedResult,
            lang = lang,
            onRetry = {
                val target = geoBlockedCustomer
                if (target != null) {
                    coroutineScope.launch {
                        val check = GeoLocationHelper.verifyCustomerLocationForInvoice(context, target, currentUser, lang)
                        if (check.isAllowed) {
                            onUpdateGeoBlocked(null)
                            viewModel.selectedPartnerClientId.value = target.id
                            onNavigate("POS")
                        } else {
                            onUpdateGeoBlocked(check)
                        }
                    }
                } else {
                    onUpdateGeoBlocked(null)
                }
            },
            onDismiss = { onUpdateGeoBlocked(null) }
        )
    }

    if (customerForRouteTracking != null) {
        val targetClient = customerForRouteTracking
        CustomerRouteTrackingDialog(
            customer = targetClient,
            lang = lang,
            onUpdateLocation = { lat, lng, addr ->
                viewModel.saveCustomer(
                    id = targetClient.id, name = targetClient.name, phone = targetClient.phone, address = addr,
                    balance = targetClient.balanceDouble, latitude = lat, longitude = lng,
                    geoRadiusMeters = targetClient.geoRadiusMeters, isGeoLocationRequired = true,
                    customerNumber = targetClient.customerNumber, barcode = targetClient.barcode
                )
            },
            onDismiss = onDismissRouteTracking
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = onDismissBulkDelete,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "AR") "تأكيد الحذف المتعدد للزبائن" else "Suppression multiple", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    if (lang == "AR") {
                        "هل أنت متأكد من حذف ${selectedCustomerIds.size} زبون محدد نهائياً؟\n\nملاحظة للحفاظ على سلامة الحسابات: الزبائن الذين لديهم ديون متبقية أو فواتير مبيعات نشطة لن يتم حذفهم."
                    } else {
                        "Êtes-vous sûr de supprimer les ${selectedCustomerIds.size} clients sélectionnés ?\nLes clients avec des dettes ou des factures actives seront préservés."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomersBatch(selectedCustomerIds.toList()) { count, warnings ->
                            onBulkDeleteComplete()
                            val msg = if (warnings.isEmpty()) {
                                if (lang == "AR") "تم حذف $count زبون بنجاح" else "$count clients supprimés"
                            } else {
                                if (lang == "AR") "تم حذف $count زبون. بعض الزبائن تعذر حذفهم لوجود ديون أو فواتير." else "$count supprimés, d'autres ont des dettes."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text(if (lang == "AR") "حذف (${selectedCustomerIds.size})" else "Supprimer") }
            },
            dismissButton = { TextButton(onClick = onDismissBulkDelete) { Text(if (lang == "AR") "إلغاء" else "Annuler") } }
        )
    }
}

@Composable
internal fun ClientsPaymentAndStatementDialogs(
    viewModel: PosViewModel,
    lang: String,
    customerPayments: List<com.example.data.CustomerPaymentEntity>,
    clientToPay: CustomerEntity?,
    onDismissPay: () -> Unit,
    clientToStatement: CustomerEntity?,
    onDismissStatement: () -> Unit,
    viewingSaleReceipt: com.example.data.SaleEntity?,
    viewingSaleItems: List<com.example.data.SaleItemEntity>,
    onShowSaleReceipt: (com.example.data.SaleEntity, List<com.example.data.SaleItemEntity>) -> Unit,
    onDismissSaleReceipt: () -> Unit,
    viewingPaymentReceipt: com.example.data.CustomerPaymentEntity?,
    onShowPaymentReceipt: (com.example.data.CustomerPaymentEntity) -> Unit,
    onDismissPaymentReceipt: () -> Unit,
    paymentToEdit: com.example.data.CustomerPaymentEntity?,
    onOpenEditPayment: (com.example.data.CustomerPaymentEntity) -> Unit,
    onDismissEditPayment: () -> Unit,
    paymentToDelete: com.example.data.CustomerPaymentEntity?,
    onOpenDeletePayment: (com.example.data.CustomerPaymentEntity) -> Unit,
    onDismissDeletePayment: () -> Unit,
    saleToDeleteFromStatement: com.example.data.SaleEntity?,
    onOpenDeleteSale: (com.example.data.SaleEntity) -> Unit,
    onDismissDeleteSale: () -> Unit
) {
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    if (clientToPay != null) {
        ClientDebtPaymentDialog(
            targetClient = clientToPay,
            lang = lang,
            currency = currency,
            onDismiss = onDismissPay,
            onConfirm = { amt ->
                viewModel.recordCustomerPayment(
                    customerId = clientToPay.id,
                    amount = amt,
                    paymentMethod = "CASH",
                    notes = if (lang == "AR") "تسديد دين عبر شاشة الزبائن" else "Règlement dette via écran clients"
                ) { createdPayment ->
                    if (createdPayment != null) onShowPaymentReceipt(createdPayment)
                }
                onDismissPay()
            }
        )
    }

    if (clientToStatement != null) {
        ClientStatementDialog(
            targetClient = clientToStatement,
            customerPayments = customerPayments,
            viewModel = viewModel,
            lang = lang,
            onDismiss = onDismissStatement,
            onViewSaleReceipt = onShowSaleReceipt,
            onViewPaymentReceipt = onShowPaymentReceipt,
            onEditPayment = onOpenEditPayment,
            onDeletePayment = onOpenDeletePayment,
            onDeleteSale = onOpenDeleteSale
        )
    }

    if (viewingSaleReceipt != null) {
        com.example.ui.screens.pos.ThermalReceiptDialog(
            viewModel = viewModel,
            sale = viewingSaleReceipt,
            items = viewingSaleItems,
            lang = lang,
            onDismiss = onDismissSaleReceipt
        )
    }

    if (viewingPaymentReceipt != null) {
        ThermalCustomerPaymentReceiptDialog(
            payment = viewingPaymentReceipt,
            lang = lang,
            viewModel = viewModel,
            onDismiss = onDismissPaymentReceipt
        )
    }

    if (paymentToEdit != null) {
        EditCustomerPaymentDialog(
            payment = paymentToEdit,
            lang = lang,
            currency = currency,
            onDismiss = onDismissEditPayment,
            onConfirm = { newAmt, newMethod, newNotes ->
                viewModel.updateCustomerPayment(paymentToEdit.id, newAmt, newMethod, newNotes) { onDismissEditPayment() }
            }
        )
    }

    if (paymentToDelete != null) {
        DeleteCustomerPaymentConfirmDialog(
            payment = paymentToDelete,
            lang = lang,
            currency = currency,
            onDismiss = onDismissDeletePayment,
            onConfirm = {
                viewModel.deleteCustomerPayment(paymentToDelete)
                onDismissDeletePayment()
            }
        )
    }

    if (saleToDeleteFromStatement != null) {
        ClientDeleteStatementSaleDialog(
            targetSale = saleToDeleteFromStatement,
            lang = lang,
            currency = currency,
            onDismiss = onDismissDeleteSale,
            onConfirm = {
                viewModel.deleteSale(saleToDeleteFromStatement.id)
                onDismissDeleteSale()
            }
        )
    }
}
