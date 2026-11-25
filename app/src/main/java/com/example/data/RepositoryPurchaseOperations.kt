package com.example.data

import com.example.util.MoneyUtils
import java.util.Locale

internal class RepositoryPurchaseOperations(
    private val appDao: AppDao,
    private val runInTransaction: suspend (suspend () -> Any?) -> Any?,
    private val getStoreCurrency: suspend () -> String,
    private val recordAuditLog: suspend (String, String, String, String, String, String, Boolean) -> Long
) {
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> tx(block: suspend () -> T): T = runInTransaction { block() } as T

    private fun signedDebtImpactCents(purchase: PurchaseEntity): Long {
        return if (purchase.totalAmount >= 0L) {
            (purchase.totalAmount - purchase.paidAmount).coerceAtLeast(0L)
        } else {
            (purchase.totalAmount - purchase.paidAmount).coerceAtMost(0L)
        }
    }

    suspend fun recordPurchaseInvoice(
        purchase: PurchaseEntity,
        items: List<PurchaseItemEntity>,
        currentUser: UserEntity? = null
    ) = tx {
        if (currentUser == null || !currentUser.hasPurchaseAccess()) {
            throw SecurityException("صلاحية الشراء غير ممنوحة لهذا المستخدم (${currentUser?.username ?: "غير مصادق"})")
        }
        val supplierBefore = if (purchase.supplierId > 0L) appDao.getSupplierById(purchase.supplierId) else null
        val prevSupplierDebt = supplierBefore?.debtBalance ?: 0L
        val purchaseId = appDao.insertPurchase(purchase)
        for (item in items) {
            appDao.insertPurchaseItem(item.copy(purchaseId = purchaseId))
            appDao.adjustProductStock(item.productId, item.quantity)
            val pType = if (item.quantity < 0) "SUPPLIER_RETURN" else "PURCHASE"
            val pNote = if (item.quantity < 0) "استرجاع للمورد فاتورة رقم / Retour fournisseur: ID-$purchaseId" else "فاتورة شراء رقم / Facture achat: ID-$purchaseId"
            appDao.insertStockMovement(
                StockMovementEntity(
                    productId = item.productId,
                    productName = item.productName,
                    type = pType,
                    quantity = item.quantity,
                    timestamp = System.currentTimeMillis(),
                    notes = pNote,
                    referenceType = "PURCHASE",
                    referenceId = purchaseId
                )
            )
        }
        val debtImpact = signedDebtImpactCents(purchase)
        if (purchase.supplierId > 0L && debtImpact != 0L) {
            appDao.adjustSupplierDebt(purchase.supplierId, debtImpact)
        }
        val gap = purchase.remainingDebtCents
        if (purchase.supplierId > 0L && purchase.paidAmount > 0L && purchase.paidAmount < purchase.totalAmount) {
            val invLabel = purchase.invoiceNumber.ifBlank { purchaseId.toString() }
            appDao.insertSupplierPayment(
                SupplierPaymentEntity(
                    purchaseId = purchaseId,
                    supplierId = purchase.supplierId,
                    supplierName = purchase.supplierName.ifBlank { supplierBefore?.name ?: "مورد" },
                    amount = purchase.paidAmount,
                    previousDebt = prevSupplierDebt + purchase.totalAmount,
                    newDebt = prevSupplierDebt + gap,
                    paymentMethod = "CASH",
                    notes = "دفعة جزئية لفاتورة شراء #$invLabel",
                    timestamp = purchase.timestamp + 1L,
                    cashierName = currentUser?.username ?: ""
                )
            )
        }
        val currency = getStoreCurrency()
        recordAuditLog(
            "PURCHASE_CREATE",
            "PURCHASE",
            purchase.invoiceNumber.ifBlank { purchaseId.toString() },
            "إنشاء فاتورة شراء #${purchase.invoiceNumber.ifBlank { purchaseId.toString() }} للمورد ${purchase.supplierName} بإجمالي ${MoneyUtils.format(purchase.totalAmount, currency)}",
            currentUser.username,
            currentUser.roleName,
            false
        )
        purchaseId
    }

    suspend fun voidPurchaseInvoice(purchaseId: Long) = tx {
        val purchase = appDao.getPurchaseById(purchaseId) ?: return@tx
        if (purchase.isVoided) return@tx
        appDao.voidPurchase(purchaseId)
        val items = appDao.getPurchaseItems(purchaseId)
        for (item in items) {
            appDao.adjustProductStock(item.productId, -item.quantity)
            appDao.insertStockMovement(
                StockMovementEntity(
                    productId = item.productId,
                    productName = item.productName,
                    type = "PURCHASE_VOID",
                    quantity = -item.quantity,
                    timestamp = System.currentTimeMillis(),
                    notes = "إلغاء فاتورة شراء / Annulation achat: ID $purchaseId"
                )
            )
        }
        val debtImpact = signedDebtImpactCents(purchase)
        if (purchase.supplierId > 0L && debtImpact != 0L) {
            appDao.adjustSupplierDebt(purchase.supplierId, -debtImpact)
        }
    }

    suspend fun updatePurchaseInvoice(
        updatedPurchase: PurchaseEntity,
        updatedItems: List<PurchaseItemEntity>,
        recordPaymentDelta: Boolean = false
    ) = tx {
        val oldPurchase = appDao.getPurchaseById(updatedPurchase.id) ?: return@tx
        val oldItems = appDao.getPurchaseItems(updatedPurchase.id)

        val oldQtyByProduct = mutableMapOf<Long, Pair<String, Double>>()
        if (!oldPurchase.isVoided) {
            for (oldItem in oldItems) {
                appDao.adjustProductStock(oldItem.productId, -oldItem.quantity)
                val prev = oldQtyByProduct[oldItem.productId]?.second ?: 0.0
                oldQtyByProduct[oldItem.productId] = oldItem.productName to (prev + oldItem.quantity)
            }
        }

        appDao.deletePurchaseItemsByPurchaseId(updatedPurchase.id)
        val newQtyByProduct = mutableMapOf<Long, Pair<String, Double>>()
        for (newItem in updatedItems) {
            appDao.insertPurchaseItem(newItem.copy(purchaseId = updatedPurchase.id))
            if (!updatedPurchase.isVoided) {
                appDao.adjustProductStock(newItem.productId, newItem.quantity)
                val prev = newQtyByProduct[newItem.productId]?.second ?: 0.0
                newQtyByProduct[newItem.productId] = newItem.productName to (prev + newItem.quantity)
            }
        }

        val affectedProductIds = (oldQtyByProduct.keys + newQtyByProduct.keys).distinct()
        val movementNow = System.currentTimeMillis()
        for (productId in affectedProductIds) {
            val oldQty = oldQtyByProduct[productId]?.second ?: 0.0
            val newQty = newQtyByProduct[productId]?.second ?: 0.0
            val netDelta = newQty - oldQty
            if (kotlin.math.abs(netDelta) > 1e-6) {
                val pName = newQtyByProduct[productId]?.first ?: oldQtyByProduct[productId]?.first.orEmpty()
                appDao.insertStockMovement(
                    StockMovementEntity(
                        productId = productId,
                        productName = pName,
                        type = "PURCHASE_EDIT",
                        quantity = netDelta,
                        timestamp = movementNow,
                        notes = "تعديل فاتورة شراء / Modification achat: ID-${updatedPurchase.id}",
                        referenceType = "PURCHASE",
                        referenceId = updatedPurchase.id
                    )
                )
            }
        }

        if (oldPurchase.supplierId != updatedPurchase.supplierId) {
            val oldImpact = if (!oldPurchase.isVoided) signedDebtImpactCents(oldPurchase) else 0L
            if (oldPurchase.supplierId > 0 && oldImpact != 0L) {
                appDao.adjustSupplierDebt(oldPurchase.supplierId, -oldImpact)
            }
            val newImpact = if (!updatedPurchase.isVoided) signedDebtImpactCents(updatedPurchase) else 0L
            if (updatedPurchase.supplierId > 0 && newImpact != 0L) {
                appDao.adjustSupplierDebt(updatedPurchase.supplierId, newImpact)
            }
        } else if (updatedPurchase.supplierId > 0) {
            val oldImpact = if (!oldPurchase.isVoided) signedDebtImpactCents(oldPurchase) else 0L
            val newImpact = if (!updatedPurchase.isVoided) signedDebtImpactCents(updatedPurchase) else 0L
            val debtDiff = newImpact - oldImpact
            if (debtDiff != 0L) {
                appDao.adjustSupplierDebt(updatedPurchase.supplierId, debtDiff)
            }
        }

        appDao.updatePurchase(updatedPurchase)

        if (recordPaymentDelta && !updatedPurchase.isVoided && updatedPurchase.supplierId > 0L && oldPurchase.supplierId == updatedPurchase.supplierId) {
            val paidDelta = updatedPurchase.paidAmount - oldPurchase.paidAmount
            val involvesDebt = oldPurchase.remainingDebtCents > 0L || updatedPurchase.remainingDebtCents > 0L
            if (paidDelta > 0L && involvesDebt) {
                val supAfter = appDao.getSupplierById(updatedPurchase.supplierId)
                val debtAfter = supAfter?.debtBalance ?: 0L
                val invLabel = updatedPurchase.invoiceNumber.ifBlank { updatedPurchase.id.toString() }
                val note = if (updatedPurchase.remainingDebtCents == 0L) {
                    "تسديد فاتورة شراء #$invLabel"
                } else {
                    "تسديد جزئي لفاتورة شراء #$invLabel"
                }
                appDao.insertSupplierPayment(
                    SupplierPaymentEntity(
                        purchaseId = updatedPurchase.id,
                        supplierId = updatedPurchase.supplierId,
                        supplierName = updatedPurchase.supplierName.ifBlank { supAfter?.name ?: "مورد" },
                        amount = paidDelta,
                        previousDebt = debtAfter + paidDelta,
                        newDebt = debtAfter,
                        paymentMethod = "CASH",
                        notes = note,
                        timestamp = System.currentTimeMillis(),
                        cashierName = ""
                    )
                )
            }
        }

        val currency = getStoreCurrency()
        recordAuditLog(
            "INVOICE_EDIT",
            "PURCHASE",
            updatedPurchase.id.toString(),
            "تعديل فاتورة شراء #${updatedPurchase.id}: المبلغ السابق=${MoneyUtils.format(oldPurchase.totalAmount, currency)}، الجديد=${MoneyUtils.format(updatedPurchase.totalAmount, currency)}",
            "Admin",
            "MANAGER",
            false
        )
    }

    suspend fun settlePurchaseInvoice(
        purchaseId: Long,
        amountCents: Long? = null,
        paymentMethod: String = "CASH",
        notes: String = "",
        cashierName: String = ""
    ): SupplierPaymentEntity = tx {
        val purchase = appDao.getPurchaseById(purchaseId)
            ?: throw IllegalArgumentException("فاتورة الشراء غير موجودة")
        val remainingCents = purchase.remainingDebtCents
        if (remainingCents <= 0L) throw IllegalArgumentException("الفاتورة مسددة بالفعل")
        val requestedCents = amountCents ?: remainingCents
        if (requestedCents <= 0L) throw IllegalArgumentException("المبلغ غير صالح")
        val appliedCents = requestedCents.coerceAtMost(remainingCents)
        val newPaid = (purchase.paidAmount + appliedCents).coerceAtMost(purchase.totalAmount)
        val isFullSettlement = newPaid >= purchase.totalAmount
        val normalizedMethod = when (paymentMethod.uppercase(Locale.ROOT)) {
            "BANK", "TRANSFER", "VIREMENT", "CARD" -> "TRANSFER"
            "CHECK", "CHEQUE" -> "CHECK"
            else -> "CASH"
        }

        val supplierBefore = if (purchase.supplierId > 0L) appDao.getSupplierById(purchase.supplierId) else null
        val prevDebt = supplierBefore?.debtBalance ?: 0L
        if (purchase.supplierId > 0L) {
            appDao.adjustSupplierDebt(purchase.supplierId, -appliedCents)
        }
        val newDebt = if (purchase.supplierId > 0L) {
            appDao.getSupplierById(purchase.supplierId)?.debtBalance ?: (prevDebt - appliedCents)
        } else {
            prevDebt - appliedCents
        }

        appDao.updatePurchase(purchase.copy(paidAmount = newPaid))

        val invLabel = purchase.invoiceNumber.ifBlank { purchase.id.toString() }
        val defaultNote = if (isFullSettlement) {
            "تسديد وتأكيد فاتورة شراء #$invLabel"
        } else {
            "تسديد جزئي لفاتورة شراء #$invLabel"
        }
        val payment = SupplierPaymentEntity(
            purchaseId = purchase.id,
            supplierId = purchase.supplierId,
            supplierName = purchase.supplierName.ifBlank { supplierBefore?.name ?: "مورد" },
            amount = appliedCents,
            previousDebt = prevDebt,
            newDebt = newDebt,
            paymentMethod = normalizedMethod,
            notes = notes.trim().ifBlank { defaultNote },
            timestamp = System.currentTimeMillis(),
            cashierName = cashierName
        )
        val paymentId = appDao.insertSupplierPayment(payment)
        val currency = getStoreCurrency()
        recordAuditLog(
            "PURCHASE_SETTLE",
            "PURCHASE",
            invLabel,
            "$defaultNote: ${MoneyUtils.format(appliedCents, currency)} (${payment.supplierName})",
            cashierName.ifBlank { "Admin" },
            "MANAGER",
            false
        )
        payment.copy(id = paymentId)
    }

    suspend fun deletePurchaseInvoice(purchaseId: Long, currentUser: UserEntity? = null) = tx {
        if (currentUser == null || !currentUser.hasDeleteInvoiceAccess()) {
            throw SecurityException("صلاحية حذف الفواتير غير ممنوحة لهذا المستخدم (${currentUser?.username ?: "غير مصادق"})")
        }
        val purchase = appDao.getPurchaseById(purchaseId) ?: return@tx
        val items = appDao.getPurchaseItems(purchaseId)

        if (!purchase.isVoided) {
            for (item in items) {
                appDao.adjustProductStock(item.productId, -item.quantity)
            }
        }
        if (purchase.supplierId > 0 && !purchase.isVoided) {
            val debtImpact = signedDebtImpactCents(purchase)
            if (debtImpact != 0L) {
                appDao.adjustSupplierDebt(purchase.supplierId, -debtImpact)
            }
        }

        appDao.deleteStockMovementsByReference("PURCHASE", purchaseId)
        appDao.deleteStockMovementsByKeyword("ID-$purchaseId")
        appDao.deleteStockMovementsByKeyword("ID $purchaseId")
        appDao.deleteStockMovementsByKeyword("PUR-$purchaseId")
        appDao.deleteStockMovementsByKeyword("PUR-#$purchaseId")
        if (purchaseId > 0L) {
            appDao.deleteSupplierPaymentsByPurchaseId(purchaseId)
        }
        appDao.deletePurchaseItemsByPurchaseId(purchaseId)
        appDao.deletePurchase(purchaseId)

        val currency = getStoreCurrency()
        recordAuditLog(
            "INVOICE_DELETE",
            "PURCHASE",
            purchase.invoiceNumber.ifBlank { purchaseId.toString() },
            "حذف فاتورة مورد #${purchase.invoiceNumber.ifBlank { purchaseId.toString() }} للمورد ${purchase.supplierName} بإجمالي ${MoneyUtils.format(purchase.totalAmount, currency)} مع إنقاص حساب المورد وإنقاص مخزون المنتجات تلقائياً وزوالها من سجل بيع وشراء المنتجات وبقاء المنتجات في الكاتالوج",
            currentUser?.username ?: "Admin",
            currentUser?.roleName ?: "MANAGER",
            true
        )
    }

    suspend fun recordSupplierReturn(
        purchaseId: Long,
        productId: Long,
        productName: String,
        quantity: Double,
        returnAmount: Double,
        supplierId: Long
    ) = tx {
        val roundedQty = kotlin.math.abs(quantity)
        val roundedAmount = MoneyUtils.roundMoney(kotlin.math.abs(returnAmount))
        appDao.adjustProductStock(productId, -roundedQty)
        appDao.insertStockMovement(
            StockMovementEntity(
                productId = productId,
                productName = productName,
                type = "SUPPLIER_RETURN",
                quantity = -roundedQty,
                timestamp = System.currentTimeMillis(),
                notes = "إرجاع بضاعة لمورد / Retour achat: PUR-$purchaseId"
            )
        )
        if (supplierId > 0) {
            appDao.adjustSupplierDebt(supplierId, -roundedAmount)
        }
        val currency = getStoreCurrency()
        recordAuditLog(
            "RETURN_REFUND",
            "PURCHASE",
            purchaseId.toString(),
            "إرجاع مشتريات لمورد: $productName كمية $roundedQty بمبلغ ${MoneyUtils.format(roundedAmount, currency)}",
            "Admin",
            "MANAGER",
            false
        )
    }

    suspend fun updatePurchaseTimestamp(purchaseId: Long, newTimestamp: Long) = tx {
        appDao.updatePurchaseTimestamp(purchaseId, newTimestamp)
        recordAuditLog("INVOICE_DATE_EDIT", "PURCHASE", purchaseId.toString(), "تعديل تاريخ فاتورة الشراء", "Admin", "MANAGER", false)
    }

    suspend fun updatePurchaseInvoiceNumber(purchaseId: Long, newInvoiceNumber: String): Boolean = tx {
        val trimmed = newInvoiceNumber.trim()
        appDao.updatePurchaseInvoiceNumber(purchaseId, trimmed)
        recordAuditLog("INVOICE_NUMBER_EDIT", "PURCHASE", purchaseId.toString(), "تعديل رقم فاتورة الشراء إلى: $trimmed", "Admin", "MANAGER", false)
        true
    }

    suspend fun updatePurchaseSupplier(purchaseId: Long, newSupplierId: Long, newSupplierName: String) = tx {
        val purchase = appDao.getPurchaseById(purchaseId) ?: return@tx
        if (purchase.supplierId != newSupplierId && !purchase.isVoided) {
            val debtImpact = signedDebtImpactCents(purchase)
            if (purchase.supplierId > 0 && debtImpact != 0L) {
                appDao.adjustSupplierDebt(purchase.supplierId, -debtImpact)
            }
            if (newSupplierId > 0 && debtImpact != 0L) {
                appDao.adjustSupplierDebt(newSupplierId, debtImpact)
            }
        }
        appDao.updatePurchaseSupplier(purchaseId, newSupplierId, newSupplierName.trim())
        recordAuditLog(
            "INVOICE_SUPPLIER_EDIT",
            "PURCHASE",
            purchase.invoiceNumber.ifEmpty { purchaseId.toString() },
            "تبديل مورد الفاتورة من '${purchase.supplierName}' إلى '$newSupplierName'",
            "Admin",
            "MANAGER",
            false
        )
    }
}
