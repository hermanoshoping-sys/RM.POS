package com.example.ui.viewmodels

import com.example.data.*
import com.example.domain.OperationErrorCode
import com.example.domain.OperationType
import com.example.domain.PosOperationError
import com.example.domain.PosOperationResult
import com.example.ui.components.PosErrorUiTranslator
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils
import com.example.util.cents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles Supplier Purchase Invoices, Purchase Returns, and Supplier Debt Settlement
 * operations on behalf of SalesViewModel (< 500 lines).
 */
internal class PurchaseLedgerOperationsDelegate(
    private val repository: PosRepository,
    private val scope: CoroutineScope,
    private val onBeep: () -> Unit,
    private val onSuccessMessage: (String) -> Unit,
    private val onError: (PosOperationError) -> Unit,
    private val runSalesOperation: (
        operationType: OperationType,
        lang: String,
        successMessage: String,
        beepOnSuccess: Boolean,
        onResult: (PosOperationResult<Unit>) -> Unit,
        block: suspend () -> Unit
    ) -> Unit
) {

    fun deletePurchase(
        purchaseId: Long,
        currentUser: UserEntity? = null,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {},
        onErrorCallback: (Throwable) -> Unit = {}
    ) {
        val okMsg = Bilingual.tr(lang, "تم حذف فاتورة الشراء، وإنقاص الحساب والمخزون، وإزالتها من السجل", "Facture d'achat supprimée avec succès", "Purchase invoice deleted")
        runSalesOperation(OperationType.PURCHASE_DELETE, lang, okMsg, false, { res ->
            onOperationResult(res)
            if (res is PosOperationResult.Failure) {
                onErrorCallback(res.error.cause ?: IllegalStateException(res.error.message))
            }
        }) {
            repository.deletePurchaseInvoice(purchaseId, currentUser)
        }
    }

    fun voidPurchase(
        purchase: PurchaseEntity,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val okMsg = Bilingual.tr(lang, "تم إلغاء فاتورة الشراء", "Achat annulé", "Purchase voided")
        runSalesOperation(OperationType.PURCHASE_VOID, lang, okMsg, true, onOperationResult) {
            repository.voidPurchaseInvoice(purchase.id)
        }
    }

    fun updatePurchase(
        purchase: PurchaseEntity,
        items: List<PurchaseItemEntity>,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val okMsg = Bilingual.tr(lang, "تم تحديث وحفظ فاتورة المورد بنجاح", "Facture fournisseur mise à jour avec succès", "Purchase invoice updated")
        runSalesOperation(OperationType.PURCHASE_UPDATE, lang, okMsg, true, onOperationResult) {
            repository.updatePurchaseInvoice(purchase, items, recordPaymentDelta = true)
        }
    }

    fun updatePurchaseDate(
        purchaseId: Long,
        newTimestamp: Long,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val okMsg = Bilingual.tr(lang, "تم تحديث تاريخ فاتورة الشراء بنجاح", "Date de facture mise à jour", "Purchase date updated")
        runSalesOperation(OperationType.PURCHASE_DATE_UPDATE, lang, okMsg, true, onOperationResult) {
            repository.updatePurchaseTimestamp(purchaseId, newTimestamp)
        }
    }

    fun updatePurchaseInvoiceNumber(
        purchaseId: Long,
        newInvoiceNumber: String,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {},
        onResult: (Boolean) -> Unit = {}
    ) {
        scope.launch {
            try {
                val ok = repository.updatePurchaseInvoiceNumber(purchaseId, newInvoiceNumber)
                if (ok) {
                    onBeep()
                    val msg = Bilingual.tr(lang, "تم تعديل رقم الفاتورة بنجاح", "Numéro de facture modifié avec succès", "Purchase number updated")
                    onSuccessMessage(msg)
                    onOperationResult(PosOperationResult.Success(Unit, msg))
                    onResult(true)
                } else {
                    val failure = PosErrorUiTranslator.failure(OperationErrorCode.INVALID_AMOUNT, OperationType.PURCHASE_NUMBER_UPDATE, lang)
                    onError(failure.error)
                    onOperationResult(failure)
                    onResult(false)
                }
            } catch (e: Exception) {
                val err = PosErrorUiTranslator.fromThrowable(e, OperationType.PURCHASE_NUMBER_UPDATE, lang)
                onError(err)
                onOperationResult(PosOperationResult.Failure(err))
                onResult(false)
            }
        }
    }

    fun updatePurchaseSupplier(
        purchaseId: Long,
        newSupplierId: Long,
        newSupplierName: String,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val okMsg = Bilingual.tr(lang, "تم تبديل اسم المورد بنجاح", "Fournisseur mis à jour avec succès", "Supplier updated")
        runSalesOperation(OperationType.PURCHASE_SUPPLIER_UPDATE, lang, okMsg, true, onOperationResult) {
            repository.updatePurchaseSupplier(purchaseId, newSupplierId, newSupplierName)
        }
    }

    fun recordSupplierReturn(
        purchaseId: Long,
        productId: Long,
        productName: String,
        quantity: Double,
        returnAmount: Double,
        supplierId: Long,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val okMsg = Bilingual.tr(lang, "تم تسجيل مرتجع المورد وخصم المخزون بنجاح", "Retour fournisseur enregistré avec succès", "Supplier return recorded")
        runSalesOperation(OperationType.PURCHASE_RETURN, lang, okMsg, true, onOperationResult) {
            if (quantity <= 0.0 || returnAmount < 0.0) {
                throw IllegalArgumentException(PosErrorUiTranslator.defaultMessage(OperationErrorCode.INVALID_AMOUNT, lang))
            }
            repository.recordSupplierReturn(purchaseId, productId, productName, quantity, returnAmount, supplierId)
        }
    }

    fun settlePurchaseInvoice(
        purchase: PurchaseEntity,
        amount: Double? = null,
        cashierName: String = "",
        lang: String = "AR",
        paymentMethod: String = "CASH",
        notes: String = "",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val remainingCents = purchase.remainingDebtCents
        val isPartial = amount != null && amount.cents < remainingCents
        val okMsg = if (isPartial) {
            Bilingual.tr(lang, "تم تسجيل الدفعة الجزئية في كشف حساب المورد بنجاح", "Paiement partiel enregistré dans le relevé fournisseur", "Partial payment recorded in supplier statement")
        } else {
            Bilingual.tr(lang, "تم تسديد وتأكيد فاتورة المشتريات للمورد", "Facture d'achat réglée", "Purchase invoice settled")
        }
        runSalesOperation(OperationType.PURCHASE_SETTLE, lang, okMsg, true, onOperationResult) {
            val payCents = amount?.cents ?: remainingCents
            if (payCents <= 0L) {
                throw IllegalArgumentException(
                    Bilingual.tr(lang, "الفاتورة مسددة بالفعل أو المبلغ غير صالح", "Facture déjà réglée ou montant invalide", "Invoice already settled or invalid amount")
                )
            }
            repository.settlePurchaseInvoice(
                purchaseId = purchase.id,
                amountCents = payCents,
                paymentMethod = paymentMethod,
                notes = notes,
                cashierName = cashierName
            )
        }
    }

    fun markPurchaseAsDebt(
        purchase: PurchaseEntity,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val okMsg = Bilingual.tr(lang, "تم تحويل الفاتورة إلى دين للمورد", "Facture marquée à crédit", "Purchase marked as debt")
        runSalesOperation(OperationType.PURCHASE_MARK_DEBT, lang, okMsg, true, onOperationResult) {
            repository.updatePurchaseInvoice(purchase.copy(paidAmount = 0L), repository.getPurchaseItems(purchase.id))
        }
    }

    fun recordSupplierInvoiceBulk(
        purchase: PurchaseEntity,
        items: List<PurchaseItemEntity>,
        currentUser: UserEntity? = null,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val okMsg = Bilingual.tr(lang, "تم تسجيل فاتورة الشراء بنجاح وتحديث المخزون!", "Facture enregistrée avec succès!", "Purchase invoice recorded!")
        runSalesOperation(OperationType.PURCHASE_CREATE, lang, okMsg, true, onOperationResult) {
            if (items.isEmpty()) throw IllegalArgumentException(PosErrorUiTranslator.defaultMessage(OperationErrorCode.EMPTY_ITEMS, lang))
            if (purchase.paidAmount < 0L) throw IllegalArgumentException(PosErrorUiTranslator.defaultMessage(OperationErrorCode.INVALID_AMOUNT, lang))
            repository.recordPurchaseInvoice(purchase, items, currentUser)
        }
    }

    fun recordSupplierInvoice(
        supplierId: Long,
        supplierName: String,
        productId: Long,
        productName: String,
        qty: Double,
        cost: Double,
        paid: Double,
        currentUser: UserEntity? = null,
        lang: String = "AR",
        onOperationResult: (PosOperationResult<Unit>) -> Unit = {}
    ) {
        val okMsg = Bilingual.tr(lang, "تم تسجيل فاتورة الشراء بنجاح", "Facture enregistrée", "Purchase recorded")
        runSalesOperation(OperationType.PURCHASE_CREATE, lang, okMsg, true, onOperationResult) {
            if (qty <= 0.0 || cost < 0.0 || paid < 0.0) {
                throw IllegalArgumentException(PosErrorUiTranslator.defaultMessage(OperationErrorCode.INVALID_AMOUNT, lang))
            }
            val unitCents = cost.cents
            val purchase = PurchaseEntity(
                timestamp = System.currentTimeMillis(),
                supplierId = supplierId,
                supplierName = supplierName,
                totalAmount = MoneyUtils.multiplyCents(unitCents, qty),
                paidAmount = paid.cents
            )
            val items = listOf(PurchaseItemEntity(purchaseId = 0, productId = productId, productName = productName, quantity = qty, unitPrice = unitCents))
            repository.recordPurchaseInvoice(purchase, items, currentUser)
        }
    }
}
