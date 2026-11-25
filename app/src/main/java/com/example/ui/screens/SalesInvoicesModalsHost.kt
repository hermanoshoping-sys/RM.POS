package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CustomerEntity
import com.example.data.SaleEntity
import com.example.data.UserEntity
import com.example.domain.PosOperationResult
import com.example.printer.BatchPrintQueueState
import com.example.printer.PrinterTransport
import com.example.printer.ThermalPrinterDevice
import com.example.ui.screens.pos.ThermalReceiptDialog
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@Composable
internal fun SalesInvoicesModalsHost(
    context: Context,
    viewModel: PosViewModel,
    lang: String,
    currency: String,
    currentUser: UserEntity?,
    canEditInvoices: Boolean,
    canDeleteInvoices: Boolean,
    customers: List<CustomerEntity>,
    filteredSales: List<SaleEntity>,
    sqlSummaryCount: Int,
    sqlSummaryTotalAmount: Double,
    sqlSummaryTotalDebt: Double,
    selectedSeller: String,
    filterStatus: String,
    actionDateFormat: SimpleDateFormat,
    printDateFormat: SimpleDateFormat,
    batchQueueState: BatchPrintQueueState,
    selectedSaleForAction: SaleEntity?,
    onDismissAction: () -> Unit,
    onEditSaleSelected: (SaleEntity) -> Unit,
    onShowReceiptSelected: (SaleEntity) -> Unit,
    onDeleteSaleSelected: (SaleEntity) -> Unit,
    onEditInvoiceNumberSelected: (SaleEntity) -> Unit,
    onEditCustomerSelected: (SaleEntity) -> Unit,
    onPartialPaymentSelected: (SaleEntity) -> Unit,
    saleForPartialPayment: SaleEntity?,
    onDismissPartialPayment: () -> Unit,
    editingInvoiceNumberSale: SaleEntity?,
    onDismissEditInvoiceNumber: () -> Unit,
    editingCustomerSale: SaleEntity?,
    onDismissEditCustomer: () -> Unit,
    saleToDelete: SaleEntity?,
    onDismissDeleteSale: () -> Unit,
    saleToReturn: SaleEntity?,
    onDismissReturnSale: () -> Unit,
    showReceipt: SaleEntity?,
    onDismissReceipt: () -> Unit,
    saleToEdit: SaleEntity?,
    onDismissEditSale: () -> Unit,
    onNavigate: ((String) -> Unit)?,
    showPrintListDialog: Boolean,
    onDismissPrintList: () -> Unit
) {
    if (batchQueueState.isActive) {
        BatchPrintQueueDialog(
            state = batchQueueState,
            currency = currency,
            lang = lang,
            onCancelQueue = { viewModel.cancelBatchPrintQueue() },
            onRetryFailed = { viewModel.retryFailedBatchPrint(context) },
            onFallbackAndroidPrint = {
                viewModel.retryFailedBatchPrint(
                    context = context,
                    targetOverride = ThermalPrinterDevice(
                        id = "android_system_print",
                        name = "Android Wi-Fi / Mopria Print",
                        address = "SYSTEM",
                        transport = PrinterTransport.SYSTEM
                    )
                )
            },
            onDismiss = { viewModel.dismissBatchPrintQueue() }
        )
    }

    selectedSaleForAction?.let { sel ->
        val paymentMethodStr = when (sel.paymentMethod) {
            "CASH" -> stringResource(R.string.sales_payment_cash)
            "CARD" -> stringResource(R.string.sales_payment_card)
            "DEBT" -> stringResource(R.string.sales_payment_debt)
            "BANK" -> stringResource(R.string.sales_payment_bank)
            else -> sel.paymentMethod
        }
        InvoiceActionDialog(
            isSale = true,
            partyName = sel.customerName.ifBlank { if (lang == "AR") "زبون عادي" else "Client Comptoir" },
            invoiceNumber = sel.invoiceNumber,
            dateStr = actionDateFormat.format(Date(sel.timestamp)),
            totalStr = MoneyUtils.format(sel.totalAmountDouble, currency),
            paidStr = MoneyUtils.format(sel.paidAmountDouble, currency),
            paymentMethodStr = paymentMethodStr,
            sellerName = sel.cashierName.ifBlank { currentUser?.username.orEmpty() },
            onEdit = {
                if (!canEditInvoices) {
                    Toast.makeText(context, context.getString(R.string.sales_permission_edit_denied), Toast.LENGTH_SHORT).show()
                } else {
                    onEditSaleSelected(sel)
                    onDismissAction()
                }
            },
            onValidate = {
                viewModel.settleSaleInvoice(sel) { res ->
                    when (res) {
                        is PosOperationResult.Success -> {
                            onDismissAction()
                            Toast.makeText(
                                context,
                                res.message.ifBlank { if (lang == "AR") "تم تسديد وتأكيد الفاتورة بنجاح" else "Facture validée avec succès" },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is PosOperationResult.Failure -> Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onConvertToDebt = {
                viewModel.markSaleAsDebt(sel) { res ->
                    when (res) {
                        is PosOperationResult.Success -> {
                            onDismissAction()
                            Toast.makeText(
                                context,
                                res.message.ifBlank { if (lang == "AR") "تم تحويل الفاتورة إلى دين بنجاح" else "Facture marquée à crédit" },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is PosOperationResult.Failure -> Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onPrint = {
                onShowReceiptSelected(sel)
                onDismissAction()
            },
            onDelete = {
                if (!canDeleteInvoices) {
                    Toast.makeText(
                        context,
                        if (lang == "AR") "عذراً، ليس لديك صلاحية حذف الفواتير. مقتصر على المدير." else "Suppression de facture non autorisée",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onDeleteSaleSelected(sel)
                    onDismissAction()
                }
            },
            onDismiss = onDismissAction,
            onEditDate = {
                if (!canEditInvoices) {
                    Toast.makeText(
                        context,
                        if (lang == "AR") "عذراً، تعديل تاريخ الفاتورة معطل لحسابك" else "Modification de la date non autorisée",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@InvoiceActionDialog
                }
                val cal = Calendar.getInstance().apply { timeInMillis = sel.timestamp }
                DatePickerDialog(context, { _, y, m, d ->
                    val timeCal = Calendar.getInstance().apply { timeInMillis = sel.timestamp }
                    TimePickerDialog(context, { _, h, min ->
                        val newCal = Calendar.getInstance().apply { set(y, m, d, h, min, 0) }
                        viewModel.updateSaleDate(sel.id, newCal.timeInMillis) { res ->
                            when (res) {
                                is PosOperationResult.Success -> {
                                    onDismissAction()
                                    Toast.makeText(
                                        context,
                                        res.message.ifBlank { if (lang == "AR") "تم تحديث تاريخ الفاتورة بنجاح" else "Date mise à jour" },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                is PosOperationResult.Failure -> Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }, timeCal.get(Calendar.HOUR_OF_DAY), timeCal.get(Calendar.MINUTE), true).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            },
            onEditInvoiceNumber = {
                if (!canEditInvoices) {
                    Toast.makeText(
                        context,
                        if (lang == "AR") "عذراً، تعديل رقم الفاتورة معطل لحسابك" else "Modification du numéro non autorisée",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onEditInvoiceNumberSelected(sel)
                    onDismissAction()
                }
            },
            onEditParty = {
                if (!canEditInvoices) {
                    Toast.makeText(
                        context,
                        if (lang == "AR") "عذراً، تبديل اسم الزبون معطل لحسابك" else "Modification du client non autorisée",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onEditCustomerSelected(sel)
                    onDismissAction()
                }
            },
            onPartialPay = if (sel.remainingDueCents > 0L) {
                {
                    onPartialPaymentSelected(sel)
                    onDismissAction()
                }
            } else null,
            remainingAmountDouble = sel.remainingDueDouble
        )
    }

    saleForPartialPayment?.let { target ->
        PartialInvoicePaymentDialog(
            isSale = true,
            partyName = target.customerName.ifBlank { if (lang == "AR") "زبون عادي" else "Client Comptoir" },
            invoiceNumber = target.invoiceNumber,
            totalAmountDouble = target.totalAmountDouble,
            paidAmountDouble = target.paidAmountDouble,
            remainingAmountDouble = target.remainingDueDouble,
            currency = currency,
            lang = lang,
            onConfirm = { amount, method, notes ->
                viewModel.settleSaleInvoice(
                    sale = target,
                    amount = amount,
                    paymentMethod = method,
                    notes = notes
                ) { res ->
                    when (res) {
                        is PosOperationResult.Success -> {
                            onDismissPartialPayment()
                            Toast.makeText(
                                context,
                                res.message.ifBlank {
                                    if (lang == "AR") "تم تسجيل الدفعة في كشف حساب الزبون بنجاح"
                                    else "Paiement enregistré dans le relevé client"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is PosOperationResult.Failure -> Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = onDismissPartialPayment
        )
    }

    editingInvoiceNumberSale?.let { target ->
        EditSaleInvoiceNumberDialog(target, viewModel, lang, onDismissEditInvoiceNumber)
    }

    editingCustomerSale?.let { target ->
        SwitchSaleCustomerDialog(target, customers, viewModel, lang, onDismissEditCustomer)
    }

    saleToDelete?.let { target ->
        SaleDeleteConfirmDialog(target, viewModel, lang, onDismissDeleteSale)
    }

    saleToReturn?.let { target ->
        SaleReturnDialog(target, viewModel, lang, onDismissReturnSale)
    }

    val printedReceipt by viewModel.printedReceipt.collectAsStateWithLifecycle()
    val printedReceiptItems by viewModel.printedReceiptItems.collectAsStateWithLifecycle()

    showReceipt?.let { currentReceipt ->
        LaunchedEffect(currentReceipt) {
            viewModel.showReceiptDetails(currentReceipt)
        }
        val currentPrinted = printedReceipt
        if (currentPrinted != null && currentPrinted.id == currentReceipt.id) {
            ThermalReceiptDialog(
                viewModel = viewModel,
                sale = currentPrinted,
                items = printedReceiptItems,
                lang = lang,
                onDismiss = {
                    onDismissReceipt()
                    viewModel.dismissReceipt()
                }
            )
        }
    }

    saleToEdit?.let { currentSaleToEdit ->
        Dialog(
            onDismissRequest = onDismissEditSale,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                EditSaleInvoiceScreen(
                    sale = currentSaleToEdit,
                    onDismiss = onDismissEditSale,
                    onNavigate = onNavigate,
                    viewModel = viewModel,
                    lang = lang
                )
            }
        }
    }

    if (showPrintListDialog) {
        val rows = filteredSales.map { s ->
            listOf(
                s.invoiceNumber,
                printDateFormat.format(Date(s.timestamp)),
                s.customerName.ifBlank { if (lang == "AR") "زبون عادي" else "Client Comptoir" },
                s.cashierName.ifBlank { currentUser?.username.orEmpty() },
                MoneyUtils.format(s.totalAmountDouble, currency)
            )
        }
        val summaryLines = listOf(
            Pair(if (lang == "AR") "إجمالي عدد الفواتير:" else "Total Factures:", "$sqlSummaryCount"),
            Pair(if (lang == "AR") "مجموع الفواتير:" else "Total Ventes:", MoneyUtils.format(sqlSummaryTotalAmount, currency)),
            Pair(if (lang == "AR") "مجموع الفواتير المدينة:" else "Total Créances (Dettes):", MoneyUtils.format(sqlSummaryTotalDebt, currency)),
            Pair(
                if (lang == "AR") "البائع المحدد:" else "Vendeur filtré:",
                if (selectedSeller == "ALL") (if (lang == "AR") "جميع البائعين" else "Tous") else selectedSeller
            )
        )
        UniversalPrintListDialog(
            title = if (lang == "AR") "سجل فواتير المبيعات" else "JOURNAL DES VENTES",
            subtitle = if (selectedSeller != "ALL") {
                if (lang == "AR") "فواتير البائع: $selectedSeller" else "Factures du vendeur : $selectedSeller"
            } else when (filterStatus) {
                "PAID" -> if (lang == "AR") "الفواتير المسددة فقط" else "Factures payées"
                "DEBT" -> if (lang == "AR") "الفواتير المدينة فقط" else "Factures à crédit"
                else -> if (lang == "AR") "جميع الفواتير" else "Toutes factures"
            },
            headers = listOf(
                if (lang == "AR") "الفاتورة" else "Facture",
                if (lang == "AR") "التاريخ" else "Date",
                if (lang == "AR") "الزبون" else "Client",
                if (lang == "AR") "البائع" else "Vendeur",
                if (lang == "AR") "المبلغ" else "Montant"
            ),
            rows = rows,
            summaryLines = summaryLines,
            viewModel = viewModel,
            lang = lang,
            onDismiss = onDismissPrintList
        )
    }
}
