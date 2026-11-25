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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PurchaseEntity
import com.example.data.SupplierEntity
import com.example.domain.FinancialCalculator
import com.example.domain.PosOperationResult
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@Composable
internal fun PurchaseInvoicesModalsHost(
    context: Context,
    viewModel: PosViewModel,
    lang: String,
    currency: String,
    canEditInvoices: Boolean,
    canDeleteInvoices: Boolean,
    suppliers: List<SupplierEntity>,
    filteredPurchases: List<PurchaseEntity>,
    filterStatus: String,
    actionDateFormat: SimpleDateFormat,
    printDateFormat: SimpleDateFormat,
    selectedPurchaseForAction: PurchaseEntity?,
    onDismissAction: () -> Unit,
    onEditPurchaseSelected: (PurchaseEntity) -> Unit,
    onShowReceiptSelected: (PurchaseEntity) -> Unit,
    onDeletePurchaseSelected: (PurchaseEntity) -> Unit,
    onEditInvoiceNumberSelected: (PurchaseEntity) -> Unit,
    onEditSupplierSelected: (PurchaseEntity) -> Unit,
    onPartialPaymentSelected: (PurchaseEntity) -> Unit,
    purchaseForPartialPayment: PurchaseEntity?,
    onDismissPartialPayment: () -> Unit,
    editingInvoiceNumberPurchase: PurchaseEntity?,
    onDismissEditInvoiceNumber: () -> Unit,
    editingSupplierPurchase: PurchaseEntity?,
    onDismissEditSupplier: () -> Unit,
    purchaseToDelete: PurchaseEntity?,
    onDismissDeletePurchase: () -> Unit,
    purchaseToReturn: PurchaseEntity?,
    onDismissReturnPurchase: () -> Unit,
    showPurchaseReceipt: PurchaseEntity?,
    onDismissPurchaseReceipt: () -> Unit,
    purchaseToEdit: PurchaseEntity?,
    onDismissEditPurchase: () -> Unit,
    onNavigate: ((String) -> Unit)?,
    showPrintListDialog: Boolean,
    onDismissPrintList: () -> Unit
) {
    selectedPurchaseForAction?.let { sel ->
        val remainingDebt = sel.remainingDebtDouble
        val paymentMethodStr = when {
            sel.paidAmount >= sel.totalAmount -> if (lang == "AR") "مسدد بالكامل" else "Payé"
            sel.paidAmount <= 0L -> if (lang == "AR") "دين مورد (آجل)" else "À Crédit"
            else -> if (lang == "AR") "دفعة جزئية (باقي: ${MoneyUtils.format(remainingDebt, currency)})" else "Partiel"
        }
        InvoiceActionDialog(
            isSale = false,
            partyName = sel.supplierName.ifBlank { if (lang == "AR") "مورد عام" else "Fournisseur" },
            invoiceNumber = sel.displayInvoiceNumber,
            dateStr = actionDateFormat.format(Date(sel.timestamp)),
            totalStr = MoneyUtils.format(sel.totalAmountDouble, currency),
            paidStr = MoneyUtils.format(sel.paidAmountDouble, currency),
            paymentMethodStr = paymentMethodStr,
            onEdit = {
                if (!canEditInvoices) {
                    Toast.makeText(
                        context,
                        if (lang == "AR") "عذراً، صلاحية تعديل الفواتير معطلة لحسابك. يرجى مراجعة المسؤول." else "Modification des factures désactivée.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onEditPurchaseSelected(sel)
                    onDismissAction()
                }
            },
            onValidate = {
                viewModel.settlePurchaseInvoice(sel) { res ->
                    when (res) {
                        is PosOperationResult.Success -> {
                            onDismissAction()
                            Toast.makeText(
                                context,
                                res.message.ifBlank { if (lang == "AR") "تم تسديد وتأكيد فاتورة المشتريات للمورد" else "Facture validée" },
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is PosOperationResult.Failure -> Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onConvertToDebt = {
                viewModel.markPurchaseAsDebt(sel) { res ->
                    when (res) {
                        is PosOperationResult.Success -> {
                            onDismissAction()
                            Toast.makeText(
                                context,
                                res.message.ifBlank { if (lang == "AR") "تم تحويل الفاتورة إلى دين للمورد" else "Facture marquée à crédit" },
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
                    onDeletePurchaseSelected(sel)
                    onDismissAction()
                }
            },
            onDismiss = onDismissAction,
            onEditDate = {
                if (!canEditInvoices) {
                    Toast.makeText(
                        context,
                        if (lang == "AR") "عذراً، تعديل تاريخ الفاتورة معطل لحسابك" else "Modification de date non autorisée",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@InvoiceActionDialog
                }
                val cal = Calendar.getInstance().apply { timeInMillis = sel.timestamp }
                DatePickerDialog(context, { _, y, m, d ->
                    val timeCal = Calendar.getInstance().apply { timeInMillis = sel.timestamp }
                    TimePickerDialog(context, { _, h, min ->
                        val newCal = Calendar.getInstance().apply { set(y, m, d, h, min, 0) }
                        viewModel.updatePurchaseDate(sel.id, newCal.timeInMillis) { res ->
                            when (res) {
                                is PosOperationResult.Success -> {
                                    onDismissAction()
                                    Toast.makeText(
                                        context,
                                        res.message.ifBlank { if (lang == "AR") "تم تحديث تاريخ فاتورة الشراء بنجاح" else "Date de facture mise à jour" },
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
                        if (lang == "AR") "عذراً، تعديل رقم فاتورة الشراء معطل لحسابك" else "Modification du numéro non autorisée",
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
                        if (lang == "AR") "عذراً، تبديل مورد الفاتورة معطل لحسابك" else "Modification du fournisseur non autorisée",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onEditSupplierSelected(sel)
                    onDismissAction()
                }
            },
            onPartialPay = if (sel.remainingDebtCents > 0L) {
                {
                    onPartialPaymentSelected(sel)
                    onDismissAction()
                }
            } else null,
            remainingAmountDouble = sel.remainingDebtDouble
        )
    }

    purchaseForPartialPayment?.let { target ->
        PartialInvoicePaymentDialog(
            isSale = false,
            partyName = target.supplierName.ifBlank { if (lang == "AR") "مورد عام" else "Fournisseur" },
            invoiceNumber = target.displayInvoiceNumber,
            totalAmountDouble = target.totalAmountDouble,
            paidAmountDouble = target.paidAmountDouble,
            remainingAmountDouble = target.remainingDebtDouble,
            currency = currency,
            lang = lang,
            onConfirm = { amount, method, notes ->
                viewModel.settlePurchaseInvoice(
                    purchase = target,
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
                                    if (lang == "AR") "تم تسجيل الدفعة في كشف حساب المورد بنجاح"
                                    else "Paiement enregistré dans le relevé fournisseur"
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

    editingInvoiceNumberPurchase?.let { target ->
        EditPurchaseInvoiceNumberDialog(target, viewModel, lang, onDismissEditInvoiceNumber)
    }

    editingSupplierPurchase?.let { target ->
        SwitchPurchaseSupplierDialog(target, suppliers, viewModel, lang, onDismissEditSupplier)
    }

    purchaseToDelete?.let { target ->
        PurchaseDeleteConfirmDialog(target, viewModel, lang, onDismissDeletePurchase)
    }

    purchaseToReturn?.let { target ->
        PurchaseReturnDialog(target, viewModel, lang, onDismissReturnPurchase)
    }

    val printedPurchase by viewModel.printedPurchase.collectAsStateWithLifecycle()
    val printedPurchaseItems by viewModel.printedPurchaseItems.collectAsStateWithLifecycle()

    showPurchaseReceipt?.let { currentReceipt ->
        LaunchedEffect(currentReceipt) {
            viewModel.showPurchaseDetails(currentReceipt)
        }
        val currentPrinted = printedPurchase
        if (currentPrinted != null && currentPrinted.id == currentReceipt.id) {
            ThermalPurchaseReceiptDialog(
                viewModel = viewModel,
                purchase = currentPrinted,
                items = printedPurchaseItems,
                lang = lang,
                onDismiss = {
                    onDismissPurchaseReceipt()
                    viewModel.dismissPurchaseReceipt()
                }
            )
        }
    }

    purchaseToEdit?.let { targetPurchase ->
        Dialog(
            onDismissRequest = onDismissEditPurchase,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                EditPurchaseInvoiceScreen(
                    purchase = targetPurchase,
                    onDismiss = onDismissEditPurchase,
                    onNavigate = onNavigate,
                    viewModel = viewModel,
                    lang = lang
                )
            }
        }
    }

    if (showPrintListDialog) {
        val rows = filteredPurchases.map { p ->
            listOf(
                p.displayInvoiceNumber,
                printDateFormat.format(Date(p.timestamp)),
                p.supplierName,
                MoneyUtils.format(p.totalAmountDouble, currency)
            )
        }
        val printSummary = FinancialCalculator.computePurchasesLedgerSummary(filteredPurchases)
        val summaryLines = listOf(
            Pair(if (lang == "AR") "إجمالي عدد الفواتير:" else "Total Factures:", "${filteredPurchases.size}"),
            Pair(if (lang == "AR") "مجموع الفواتير:" else "Total Achats:", MoneyUtils.format(printSummary.totalAmount, currency)),
            Pair(if (lang == "AR") "مجموع الفواتير المدينة:" else "Total Dettes Fournisseurs:", MoneyUtils.format(printSummary.totalDebt, currency))
        )
        UniversalPrintListDialog(
            title = if (lang == "AR") "سجل فواتير الشراء والموردين" else "JOURNAL DES ACHATS",
            subtitle = when (filterStatus) {
                "PAID" -> if (lang == "AR") "الفواتير المسددة فقط" else "Factures payées"
                "DEBT" -> if (lang == "AR") "الفواتير المدينة فقط" else "Factures à crédit"
                else -> if (lang == "AR") "جميع الفواتير" else "Toutes factures"
            },
            headers = listOf(
                if (lang == "AR") "الفاتورة" else "Facture",
                if (lang == "AR") "التاريخ" else "Date",
                if (lang == "AR") "المورد" else "Fournisseur",
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
