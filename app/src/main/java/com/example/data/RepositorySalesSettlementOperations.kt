package com.example.data

import com.example.domain.ReturnRefundCalculator
import com.example.util.MoneyUtils
import java.util.Locale

internal class RepositorySalesSettlementOperations(
    private val appDao: AppDao,
    private val runInTransaction: suspend (suspend () -> Any?) -> Any?,
    private val getStoreCurrency: suspend () -> String,
    private val recordAuditLog: suspend (String, String, String, String, String, String, Boolean) -> Long
) {
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> tx(block: suspend () -> T): T = runInTransaction { block() } as T

    private fun signedBalanceImpactCents(sale: SaleEntity): Long {
        return if (sale.totalAmount >= 0L) {
            (sale.totalAmount - sale.paidAmount).coerceAtLeast(0L)
        } else {
            (sale.totalAmount - sale.paidAmount).coerceAtMost(0L)
        }
    }

    suspend fun voidSaleInvoice(saleId: Long) = tx {
        val sale = appDao.getSaleById(saleId) ?: return@tx
        if (sale.isVoided) return@tx
        appDao.voidSale(saleId)
        val items = appDao.getSaleItems(saleId)
        for (item in items) {
            appDao.adjustProductStock(item.productId, item.quantity)
            appDao.insertStockMovement(
                StockMovementEntity(
                    productId = item.productId,
                    productName = item.productName,
                    type = "VOID",
                    quantity = item.quantity,
                    timestamp = System.currentTimeMillis(),
                    notes = "إلغاء فاتورة / Annulation vente: Sale-ID $saleId"
                )
            )
        }
        if (sale.customerId > 0) {
            val impact = signedBalanceImpactCents(sale)
            if (impact != 0L) {
                appDao.adjustCustomerBalance(sale.customerId, -impact)
            }
            appDao.addToCustomerTotalPurchases(sale.customerId, -sale.totalAmount)
        }
    }

    suspend fun updateSaleInvoice(
        updatedSale: SaleEntity,
        updatedItems: List<SaleItemEntity>,
        recordPaymentDelta: Boolean = false
    ) = tx {
        val oldSale = appDao.getSaleById(updatedSale.id) ?: return@tx
        val oldItems = appDao.getSaleItems(updatedSale.id)

        if (!oldSale.isVoided) {
            for (oldItem in oldItems) {
                appDao.adjustProductStock(oldItem.productId, oldItem.quantity)
            }
        }

        appDao.deleteSaleItemsBySaleId(updatedSale.id)
        appDao.deleteStockMovementsByReference("SALE", updatedSale.id)
        for (newItem in updatedItems) {
            appDao.insertSaleItem(newItem.copy(saleId = updatedSale.id))
            if (!updatedSale.isVoided) {
                appDao.adjustProductStock(newItem.productId, -newItem.quantity)
                appDao.insertStockMovement(
                    StockMovementEntity(
                        productId = newItem.productId,
                        productName = newItem.productName,
                        type = "SALE",
                        quantity = -newItem.quantity,
                        timestamp = updatedSale.timestamp,
                        notes = "فاتورة بيع #${updatedSale.invoiceNumber}",
                        referenceType = "SALE",
                        referenceId = updatedSale.id
                    )
                )
            }
        }

        if (oldSale.customerId != updatedSale.customerId) {
            val oldImpact = if (!oldSale.isVoided) signedBalanceImpactCents(oldSale) else 0L
            if (oldSale.customerId > 0 && oldImpact != 0L) {
                appDao.adjustCustomerBalance(oldSale.customerId, -oldImpact)
            }
            val newImpact = if (!updatedSale.isVoided) signedBalanceImpactCents(updatedSale) else 0L
            if (updatedSale.customerId > 0 && newImpact != 0L) {
                appDao.adjustCustomerBalance(updatedSale.customerId, newImpact)
            }
        } else if (updatedSale.customerId > 0) {
            val oldImpact = if (!oldSale.isVoided) signedBalanceImpactCents(oldSale) else 0L
            val newImpact = if (!updatedSale.isVoided) signedBalanceImpactCents(updatedSale) else 0L
            val balanceDiff = newImpact - oldImpact
            if (balanceDiff != 0L) {
                appDao.adjustCustomerBalance(updatedSale.customerId, balanceDiff)
            }
        }

        appDao.updateSale(updatedSale)

        if (recordPaymentDelta && !updatedSale.isVoided && updatedSale.customerId > 0L && oldSale.customerId == updatedSale.customerId) {
            val paidDelta = updatedSale.paidAmount - oldSale.paidAmount
            val involvesDebt = oldSale.remainingDueCents > 0L || updatedSale.remainingDueCents > 0L
            if (paidDelta > 0L && involvesDebt) {
                val custAfter = appDao.getCustomerById(updatedSale.customerId)
                val balAfter = custAfter?.balance ?: 0L
                val invLabel = updatedSale.invoiceNumber.ifBlank { updatedSale.id.toString() }
                val normalizedMethod = when (updatedSale.paymentMethod.uppercase(Locale.ROOT)) {
                    "BANK", "TRANSFER", "VIREMENT", "CARD" -> "TRANSFER"
                    "CHECK", "CHEQUE" -> "CHECK"
                    else -> "CASH"
                }
                val note = if (updatedSale.remainingDueCents == 0L) {
                    "تسديد فاتورة بيع #$invLabel"
                } else {
                    "تسديد جزئي لفاتورة بيع #$invLabel"
                }
                appDao.insertCustomerPayment(
                    CustomerPaymentEntity(
                        saleId = updatedSale.id,
                        customerId = updatedSale.customerId,
                        customerName = updatedSale.customerName.ifBlank { custAfter?.name ?: "زبون" },
                        amount = paidDelta,
                        previousBalance = balAfter + paidDelta,
                        newBalance = balAfter,
                        paymentMethod = normalizedMethod,
                        notes = note,
                        timestamp = System.currentTimeMillis(),
                        cashierName = updatedSale.cashierName
                    )
                )
            }
        }

        val currency = getStoreCurrency()
        recordAuditLog(
            "INVOICE_EDIT",
            "SALE",
            updatedSale.invoiceNumber.ifEmpty { updatedSale.id.toString() },
            "تعديل فاتورة بيع #${updatedSale.invoiceNumber}: الإجمالي السابق=${MoneyUtils.format(oldSale.totalAmount, currency)}، الجديد=${MoneyUtils.format(updatedSale.totalAmount, currency)}",
            "Admin",
            "MANAGER",
            false
        )
    }

    suspend fun settleSaleInvoice(
        saleId: Long,
        amountCents: Long? = null,
        paymentMethod: String = "CASH",
        notes: String = "",
        cashierName: String = ""
    ): CustomerPaymentEntity = tx {
        val sale = appDao.getSaleById(saleId) ?: throw IllegalArgumentException("الفاتورة غير موجودة")
        val remainingCents = sale.remainingDueCents
        if (remainingCents <= 0L) throw IllegalArgumentException("الفاتورة مسددة بالفعل")
        val requestedCents = amountCents ?: remainingCents
        if (requestedCents <= 0L) throw IllegalArgumentException("المبلغ غير صالح")
        val appliedCents = requestedCents.coerceAtMost(remainingCents)
        val newPaid = (sale.paidAmount + appliedCents).coerceAtMost(sale.totalAmount)
        val isFullSettlement = newPaid >= sale.totalAmount
        val normalizedMethod = when (paymentMethod.uppercase(Locale.ROOT)) {
            "BANK", "TRANSFER", "VIREMENT", "CARD" -> "TRANSFER"
            "CHECK", "CHEQUE" -> "CHECK"
            else -> "CASH"
        }
        val updatedSaleMethod = when {
            isFullSettlement -> when (normalizedMethod) {
                "TRANSFER" -> "BANK"
                else -> "CASH"
            }
            else -> sale.paymentMethod
        }

        val customerBefore = if (sale.customerId > 0L) appDao.getCustomerById(sale.customerId) else null
        val prevBalance = customerBefore?.balance ?: 0L
        if (sale.customerId > 0L) {
            appDao.adjustCustomerBalance(sale.customerId, -appliedCents)
        }
        val newBalance = if (sale.customerId > 0L) {
            appDao.getCustomerById(sale.customerId)?.balance ?: (prevBalance - appliedCents).coerceAtLeast(0L)
        } else {
            (prevBalance - appliedCents).coerceAtLeast(0L)
        }

        appDao.updateSale(sale.copy(paidAmount = newPaid, paymentMethod = updatedSaleMethod))

        val invLabel = sale.invoiceNumber.ifBlank { sale.id.toString() }
        val defaultNote = if (isFullSettlement) {
            "تسديد وتأكيد فاتورة بيع #$invLabel"
        } else {
            "تسديد جزئي لفاتورة بيع #$invLabel"
        }
        val payment = CustomerPaymentEntity(
            saleId = sale.id,
            customerId = sale.customerId,
            customerName = sale.customerName.ifBlank { customerBefore?.name ?: "زبون" },
            amount = appliedCents,
            previousBalance = prevBalance,
            newBalance = newBalance,
            paymentMethod = normalizedMethod,
            notes = notes.trim().ifBlank { defaultNote },
            timestamp = System.currentTimeMillis(),
            cashierName = cashierName
        )
        val paymentId = appDao.insertCustomerPayment(payment)
        val currency = getStoreCurrency()
        recordAuditLog(
            "SALE_SETTLE",
            "SALE",
            invLabel,
            "$defaultNote: ${MoneyUtils.format(appliedCents, currency)} (${payment.customerName})",
            cashierName.ifBlank { "Admin" },
            "CASHIER",
            false
        )
        payment.copy(id = paymentId)
    }

    suspend fun updateSaleTimestamp(saleId: Long, newTimestamp: Long) = tx {
        appDao.updateSaleTimestamp(saleId, newTimestamp)
        recordAuditLog("INVOICE_DATE_EDIT", "SALE", saleId.toString(), "تعديل تاريخ فاتورة البيع", "Admin", "MANAGER", false)
    }

    suspend fun updateSaleInvoiceNumber(saleId: Long, newInvoiceNumber: String): Boolean = tx {
        val trimmed = newInvoiceNumber.trim()
        if (trimmed.isEmpty()) return@tx false
        val count = appDao.isSaleInvoiceNumberTaken(trimmed, saleId)
        if (count > 0) return@tx false
        appDao.updateSaleInvoiceNumber(saleId, trimmed)
        recordAuditLog("INVOICE_NUMBER_EDIT", "SALE", saleId.toString(), "تعديل رقم فاتورة البيع إلى: $trimmed", "Admin", "MANAGER", false)
        true
    }

    suspend fun updateSaleCustomer(saleId: Long, newCustomerId: Long, newCustomerName: String) = tx {
        val sale = appDao.getSaleById(saleId) ?: return@tx
        if (sale.customerId != newCustomerId && !sale.isVoided) {
            val impact = signedBalanceImpactCents(sale)
            if (sale.customerId > 0 && impact != 0L) {
                appDao.adjustCustomerBalance(sale.customerId, -impact)
            }
            if (newCustomerId > 0 && impact != 0L) {
                appDao.adjustCustomerBalance(newCustomerId, impact)
            }
        }
        appDao.updateSaleCustomer(saleId, newCustomerId, newCustomerName.trim())
        recordAuditLog(
            "INVOICE_CUSTOMER_EDIT",
            "SALE",
            sale.invoiceNumber.ifEmpty { saleId.toString() },
            "تبديل زبون الفاتورة من '${sale.customerName}' إلى '$newCustomerName'",
            "Admin",
            "MANAGER",
            false
        )
    }

    suspend fun deleteSaleInvoice(saleId: Long, currentUser: UserEntity? = null) = tx {
        if (currentUser == null || !currentUser.hasDeleteInvoiceAccess()) {
            throw SecurityException("صلاحية حذف الفواتير غير ممنوحة لهذا المستخدم (${currentUser?.username ?: "غير مصادق"})")
        }
        val sale = appDao.getSaleById(saleId) ?: return@tx
        val items = appDao.getSaleItems(saleId)

        if (sale.customerId > 0 && !sale.isVoided) {
            val impact = signedBalanceImpactCents(sale)
            if (impact != 0L) {
                appDao.adjustCustomerBalance(sale.customerId, -impact)
            }
            appDao.addToCustomerTotalPurchases(sale.customerId, -sale.totalAmount)
        }

        if (!sale.isVoided) {
            for (item in items) {
                appDao.adjustProductStock(item.productId, item.quantity)
                appDao.insertStockMovement(
                    StockMovementEntity(
                        productId = item.productId,
                        productName = item.productName,
                        type = "STOCK_RESTORE_INVOICE_DELETE",
                        quantity = item.quantity,
                        timestamp = System.currentTimeMillis(),
                        notes = "زيادة واسترجاع المخزون تلقائياً عند حذف فاتورة البيع #${sale.invoiceNumber}",
                        referenceType = "SALE_RESTORE",
                        referenceId = saleId
                    )
                )
            }
        }

        appDao.deleteStockMovementsByReference("SALE", saleId)
        if (sale.invoiceNumber.isNotBlank()) {
            appDao.deleteStockMovementsByKeyword("Invoice: ${sale.invoiceNumber}")
        }
        if (saleId > 0L) {
            appDao.deleteStockMovementsByKeyword("Sale-ID $saleId")
            appDao.deleteCustomerPaymentsBySaleId(saleId)
        }
        appDao.deleteSaleItemsBySaleId(saleId)
        appDao.deleteSale(saleId)

        val currency = getStoreCurrency()
        recordAuditLog(
            "INVOICE_DELETE",
            "SALE",
            sale.invoiceNumber.ifEmpty { saleId.toString() },
            "حذف فاتورة بيع #${sale.invoiceNumber} للزبون ${sale.customerName} بإجمالي ${MoneyUtils.format(sale.totalAmount, currency)} مع إنقاص حساب الزبون وزيادة المخزون تلقائياً بالكميات المسترجعة وزوالها من سجل بيع المنتجات وبقاء المنتجات في الكاتالوج",
            currentUser?.username ?: "Admin",
            currentUser?.roleName ?: "MANAGER",
            true
        )
    }

    suspend fun getReturnedQuantitiesForSale(saleId: Long): Map<Long, Double> = tx {
        appDao.backfillLegacyCustomerReturnReferences()
        appDao.getStockMovementsByReference("SALE_RETURN", saleId)
            .filter { it.type == "CUSTOMER_RETURN" }
            .groupBy { it.productId }
            .mapValues { (_, rows) -> rows.sumOf { kotlin.math.abs(it.quantity) } }
    }

    suspend fun recordCustomerReturn(
        saleId: Long,
        productId: Long,
        productName: String,
        quantity: Double,
        refundAmount: Double? = null,
        isCreditRefund: Boolean,
        customerId: Long
    ) = tx {
        if (!quantity.isFinite() || quantity <= 0.0) {
            throw IllegalArgumentException("الكمية المرتجعة يجب أن تكون أكبر من صفر")
        }
        if (refundAmount != null && (!refundAmount.isFinite() || refundAmount < 0.0)) {
            throw IllegalArgumentException("مبلغ الاسترجاع غير صالح")
        }

        val sale = appDao.getSaleById(saleId)
            ?: throw IllegalArgumentException("الفاتورة الأصلية غير موجودة (#$saleId)")
        if (sale.isVoided) {
            throw IllegalArgumentException("لا يمكن تسجيل مرتجع لفاتورة ملغاة")
        }
        val saleItems = appDao.getSaleItems(saleId)
        // Migrate any historical CUSTOMER_RETURN rows lacking referenceType/referenceId
        appDao.backfillLegacyCustomerReturnReferences()
        val priorReturnQty = appDao.getStockMovementsByReference("SALE_RETURN", saleId)
            .filter { it.type == "CUSTOMER_RETURN" && it.productId == productId }
            .sumOf { kotlin.math.abs(it.quantity) }

        val roundedQty = kotlin.math.abs(quantity)
        val refundPlan = ReturnRefundCalculator.computeLineReturnRefund(
            sale = sale,
            saleItems = saleItems,
            productId = productId,
            returnQuantity = roundedQty,
            alreadyReturnedQuantity = priorReturnQty
        )

        val currency = getStoreCurrency()
        val effectiveRefundCents: Long = if (refundAmount == null) {
            refundPlan.maxRefundCents
        } else {
            val requestedRefundCents = MoneyUtils.toCents(refundAmount)
            if (requestedRefundCents > refundPlan.maxRefundCents) {
                throw IllegalArgumentException(
                    "مبلغ الاسترجاع المطلوب (${MoneyUtils.format(requestedRefundCents, currency)}) يتجاوز القيمة الصافية المدفوعة فعلياً لهذا السطر (${MoneyUtils.format(refundPlan.maxRefundCents, currency)})"
                )
            }
            requestedRefundCents
        }

        appDao.adjustProductStock(productId, roundedQty)
        appDao.insertStockMovement(
            StockMovementEntity(
                productId = productId,
                productName = productName,
                type = "CUSTOMER_RETURN",
                quantity = roundedQty,
                timestamp = System.currentTimeMillis(),
                notes = "إرجاع بضاعة من زبون / Retour vente: Facture #$saleId",
                referenceType = "SALE_RETURN",
                referenceId = saleId
            )
        )
        val effectiveCustomerId = if (customerId > 0L) customerId else sale.customerId
        if (effectiveCustomerId > 0L && effectiveRefundCents > 0L) {
            if (isCreditRefund) {
                appDao.adjustCustomerBalance(effectiveCustomerId, -effectiveRefundCents)
            }
            appDao.addToCustomerTotalPurchases(effectiveCustomerId, -effectiveRefundCents)
        }
        recordAuditLog(
            "RETURN_REFUND",
            "SALE",
            sale.invoiceNumber.ifEmpty { saleId.toString() },
            "استرجاع مبيعات من زبون: $productName كمية $roundedQty بمبلغ مسترجع ${MoneyUtils.format(effectiveRefundCents, currency)} (الحد الصافي بعد الخصومات: ${MoneyUtils.format(refundPlan.maxRefundCents, currency)})",
            "Admin",
            "MANAGER",
            false
        )
    }
}
