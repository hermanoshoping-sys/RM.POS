package com.example.data

import com.example.util.MoneyUtils
import com.example.util.cents
import java.util.Locale

internal class RepositoryPartyAndFinanceOperations(
    private val appDao: AppDao,
    private val runInTransaction: suspend (suspend () -> Any?) -> Any?,
    private val getStoreCurrency: suspend () -> String,
    private val recordAuditLog: suspend (String, String, String, String, String, String, Boolean) -> Long
) {
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> tx(block: suspend () -> T): T = runInTransaction { block() } as T

    suspend fun getNextCustomerNumber(): Long = tx {
        val lastSeq = appDao.getLastCustomerNumber() ?: 0L
        val maxInDb = appDao.getMaxCustomerNumberFromCustomers() ?: 0L
        val nextNum = maxOf(lastSeq, maxInDb) + 1L
        appDao.setCustomerSequence(CustomerSequenceEntity(id = 1, lastCustomerNumber = nextNum))
        nextNum
    }

    fun generateCustomerBarcode(shopName: String, customerNumber: Long): String {
        val cleanShop = shopName
            .trim()
            .replace("\\s+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .ifBlank { "rmpos" }
        return "$cleanShop-$customerNumber"
    }

    suspend fun saveCustomer(customer: CustomerEntity): Long = tx {
        val existing = if (customer.id != 0L) appDao.getCustomerById(customer.id) else null
        val customerNum = if (existing != null && existing.customerNumber > 0L) {
            existing.customerNumber
        } else if (customer.customerNumber > 0L) {
            customer.customerNumber
        } else {
            getNextCustomerNumber()
        }

        val barcode = if (existing != null && existing.barcode.isNotBlank()) {
            existing.barcode
        } else if (customer.barcode.isNotBlank()) {
            customer.barcode
        } else {
            val settings = appDao.getSettings()
            val shopName = settings?.shopName ?: "rmpos"
            generateCustomerBarcode(shopName, customerNum)
        }

        val finalCustomer = customer.copy(customerNumber = customerNum, barcode = barcode)
        appDao.insertCustomer(finalCustomer)
    }

    suspend fun deleteCustomer(customerId: Long): Pair<Boolean, String> = tx {
        val cust = appDao.getCustomerById(customerId) ?: return@tx Pair(false, "الزبون غير موجود")
        if (MoneyUtils.isPositive(cust.balance)) {
            val currency = getStoreCurrency()
            return@tx Pair(false, "لا يمكن حذف الزبون (${cust.name}) لوجود رصيد دين مستحق غير مسدد: ${MoneyUtils.format(cust.balance, currency)}")
        }
        val salesCount = appDao.getCustomerActiveSalesCount(customerId)
        if (salesCount > 0) {
            return@tx Pair(false, "لا يمكن حذف الزبون (${cust.name}) لوجود $salesCount فاتورة مبيعات مرتبطة به في النظام")
        }
        val paymentsCount = appDao.getCustomerPaymentsCount(customerId)
        if (paymentsCount > 0) {
            return@tx Pair(false, "لا يمكن حذف الزبون (${cust.name}) لوجود $paymentsCount عمليات سداد مرتبطة بسجلاته المالية")
        }
        appDao.deleteCustomer(customerId)
        recordAuditLog("CUSTOMER_DELETE", "CUSTOMER", customerId.toString(), "حذف الزبون: ${cust.name} (رقم $customerId)", "Admin", "MANAGER", false)
        Pair(true, "تم حذف الزبون بنجاح")
    }

    suspend fun deleteCustomersBatch(customerIds: List<Long>): Pair<Int, List<String>> = tx {
        if (customerIds.isEmpty()) return@tx Pair(0, emptyList())
        val undeletableWarnings = mutableListOf<String>()
        val eligibleIds = mutableListOf<Long>()

        val currency = getStoreCurrency()
        for (id in customerIds) {
            val cust = appDao.getCustomerById(id) ?: continue
            if (MoneyUtils.isPositive(cust.balance)) {
                undeletableWarnings.add("تعذر حذف (${cust.name}): عليه دين مستحق بقيمة ${MoneyUtils.format(cust.balance, currency)}")
                continue
            }
            val salesCount = appDao.getCustomerActiveSalesCount(id)
            if (salesCount > 0) {
                undeletableWarnings.add("تعذر حذف (${cust.name}): لديه $salesCount فاتورة مبيعات")
                continue
            }
            val paymentsCount = appDao.getCustomerPaymentsCount(id)
            if (paymentsCount > 0) {
                undeletableWarnings.add("تعذر حذف (${cust.name}): لديه $paymentsCount عملية سداد")
                continue
            }
            eligibleIds.add(id)
        }

        if (eligibleIds.isNotEmpty()) {
            appDao.deleteCustomersBatch(eligibleIds)
            recordAuditLog("BATCH_CUSTOMER_DELETE", "CUSTOMER", eligibleIds.joinToString(","), "حذف جماعي لـ ${eligibleIds.size} زبون", "Admin", "MANAGER", false)
        }
        Pair(eligibleIds.size, undeletableWarnings)
    }

    suspend fun deleteSupplier(supplierId: Long): Pair<Boolean, String> = tx {
        val sup = appDao.getAllSuppliersList().find { it.id == supplierId }
            ?: return@tx Pair(false, "المورد غير موجود")
        if (MoneyUtils.isPositive(sup.debtBalance)) {
            val currency = getStoreCurrency()
            return@tx Pair(false, "لا يمكن حذف المورد (${sup.name}) لوجود ذمة مالية/ديون مستحقة: ${MoneyUtils.format(sup.debtBalance, currency)}")
        }
        val purchasesCount = appDao.getSupplierActivePurchasesCount(supplierId)
        if (purchasesCount > 0) {
            return@tx Pair(false, "لا يمكن حذف المورد (${sup.name}) لوجود $purchasesCount فواتير شراء مسجلة باسمه")
        }
        val paymentsCount = appDao.getSupplierPaymentsCount(supplierId)
        if (paymentsCount > 0) {
            return@tx Pair(false, "لا يمكن حذف المورد (${sup.name}) لوجود $paymentsCount دفعات مالية مسجلة باسمه")
        }
        appDao.deleteSupplier(supplierId)
        recordAuditLog("SUPPLIER_DELETE", "SUPPLIER", supplierId.toString(), "حذف المورد: ${sup.name} (رقم $supplierId)", "Admin", "MANAGER", false)
        Pair(true, "تم حذف المورد بنجاح")
    }

    suspend fun recordCustomerPayment(
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        notes: String,
        cashierName: String
    ): CustomerPaymentEntity? = tx {
        val cust = appDao.getCustomerById(customerId) ?: return@tx null
        val prev = cust.balance
        val amountCents = amount.cents
        val newBal = (prev - amountCents).coerceAtLeast(0L)
        appDao.insertCustomer(cust.copy(balance = newBal))
        val payment = CustomerPaymentEntity(
            customerId = customerId,
            customerName = cust.name,
            amount = amountCents,
            previousBalance = prev,
            newBalance = newBal,
            paymentMethod = paymentMethod,
            notes = notes,
            timestamp = System.currentTimeMillis(),
            cashierName = cashierName
        )
        val id = appDao.insertCustomerPayment(payment)
        payment.copy(id = id)
    }

    suspend fun deleteCustomerPayment(payment: CustomerPaymentEntity) = tx {
        val cust = appDao.getCustomerById(payment.customerId)
        if (cust != null) {
            appDao.insertCustomer(cust.copy(balance = cust.balance + payment.amount))
        }
        if (payment.saleId > 0L) {
            val sale = appDao.getSaleById(payment.saleId)
            if (sale != null) {
                appDao.updateSale(sale.copy(paidAmount = (sale.paidAmount - payment.amount).coerceAtLeast(0L)))
            }
        }
        appDao.deleteCustomerPayment(payment.id)
        val currency = getStoreCurrency()
        recordAuditLog(
            "PAYMENT_DELETE",
            "CUSTOMER_PAYMENT",
            payment.id.toString(),
            "حذف تسديد زبون #${payment.id} بمبلغ ${MoneyUtils.format(payment.amount, currency)} وإعادة الدين للزبون ${payment.customerName}",
            "Admin",
            "MANAGER",
            true
        )
    }

    suspend fun updateCustomerPayment(
        paymentId: Long,
        newAmount: Double,
        newPaymentMethod: String,
        newNotes: String
    ) = tx {
        val oldPayment = appDao.getCustomerPaymentById(paymentId) ?: return@tx
        val cust = appDao.getCustomerById(oldPayment.customerId) ?: return@tx
        val newAmountCents = newAmount.cents
        val delta = newAmountCents - oldPayment.amount
        val newCustBalance = (cust.balance - delta).coerceAtLeast(0L)
        appDao.insertCustomer(cust.copy(balance = newCustBalance))

        val updated = oldPayment.copy(
            amount = newAmountCents,
            newBalance = newCustBalance,
            paymentMethod = newPaymentMethod,
            notes = newNotes
        )
        appDao.updateCustomerPayment(updated)
        val currency = getStoreCurrency()
        recordAuditLog(
            "PAYMENT_EDIT",
            "CUSTOMER_PAYMENT",
            paymentId.toString(),
            "تعديل تسديد زبون #${paymentId} (${cust.name}): من ${MoneyUtils.format(oldPayment.amount, currency)} إلى ${MoneyUtils.format(newAmountCents, currency)}",
            "Admin",
            "MANAGER",
            false
        )
    }

    suspend fun recordSupplierPayment(
        supplierId: Long,
        amount: Double,
        paymentMethod: String,
        notes: String,
        cashierName: String
    ): SupplierPaymentEntity? = tx {
        val sup = appDao.getAllSuppliersList().find { it.id == supplierId } ?: return@tx null
        val prev = sup.debtBalance
        val amountCents = amount.cents
        val newDebt = prev - amountCents
        appDao.insertSupplier(sup.copy(debtBalance = newDebt))
        val payment = SupplierPaymentEntity(
            supplierId = supplierId,
            supplierName = sup.name,
            amount = amountCents,
            previousDebt = prev,
            newDebt = newDebt,
            paymentMethod = paymentMethod,
            notes = notes,
            timestamp = System.currentTimeMillis(),
            cashierName = cashierName
        )
        val id = appDao.insertSupplierPayment(payment)
        payment.copy(id = id)
    }

    suspend fun deleteSupplierPayment(payment: SupplierPaymentEntity) = tx {
        val sup = appDao.getAllSuppliersList().find { it.id == payment.supplierId }
        if (sup != null) {
            appDao.insertSupplier(sup.copy(debtBalance = sup.debtBalance + payment.amount))
        }
        if (payment.purchaseId > 0L) {
            val purchase = appDao.getPurchaseById(payment.purchaseId)
            if (purchase != null) {
                appDao.updatePurchase(purchase.copy(paidAmount = (purchase.paidAmount - payment.amount).coerceAtLeast(0L)))
            }
        }
        appDao.deleteSupplierPayment(payment.id)
        val currency = getStoreCurrency()
        recordAuditLog(
            "PAYMENT_DELETE",
            "SUPPLIER_PAYMENT",
            payment.id.toString(),
            "حذف تسديد مورد #${payment.id} بمبلغ ${MoneyUtils.format(payment.amount, currency)} وإعادة الدين للمورد ${payment.supplierName}",
            "Admin",
            "MANAGER",
            true
        )
    }

    suspend fun updateSupplierPayment(
        paymentId: Long,
        newAmount: Double,
        newPaymentMethod: String,
        newNotes: String
    ) = tx {
        val oldPayment = appDao.getSupplierPaymentById(paymentId) ?: return@tx
        val sup = appDao.getAllSuppliersList().find { it.id == oldPayment.supplierId } ?: return@tx
        val newAmountCents = newAmount.cents
        val delta = newAmountCents - oldPayment.amount
        val newDebt = sup.debtBalance - delta
        appDao.insertSupplier(sup.copy(debtBalance = newDebt))

        val updated = oldPayment.copy(
            amount = newAmountCents,
            newDebt = newDebt,
            paymentMethod = newPaymentMethod,
            notes = newNotes
        )
        appDao.updateSupplierPayment(updated)
        val currency = getStoreCurrency()
        recordAuditLog(
            "PAYMENT_EDIT",
            "SUPPLIER_PAYMENT",
            paymentId.toString(),
            "تعديل تسديد مورد #${paymentId} (${sup.name}): من ${MoneyUtils.format(oldPayment.amount, currency)} إلى ${MoneyUtils.format(newAmountCents, currency)}",
            "Admin",
            "MANAGER",
            false
        )
    }
}
