package com.example.domain

import com.example.data.CustomerEntity
import com.example.data.ExpenseEntity
import com.example.data.ProductSaleHistoryRecord
import com.example.data.PurchaseEntity
import com.example.data.PurchaseItemEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.util.MoneyUtils
import com.example.util.cents
import com.example.util.moneyDouble

/**
 * Pure Kotlin domain calculator centralizing all POS financial math in exact Long cents.
 * Keeps Jetpack Compose screens free of inline monetary formulas and floating-point drift.
 */
object FinancialCalculator {

    data class CartFinancialSummary(
        val subtotalCents: Long,
        val discountCents: Long,
        val netTotalCents: Long,
        val receivedCents: Long,
        val paidCents: Long,
        val changeDueCents: Long,
        val remainingDebtCents: Long,
        val finalPaymentMethod: String
    ) {
        val subtotal: Double get() = subtotalCents.moneyDouble
        val discountAmount: Double get() = discountCents.moneyDouble
        val netTotal: Double get() = netTotalCents.moneyDouble
        val receivedAmount: Double get() = receivedCents.moneyDouble
        val paidAmount: Double get() = paidCents.moneyDouble
        val changeDue: Double get() = changeDueCents.moneyDouble
        val remainingDebt: Double get() = remainingDebtCents.moneyDouble
        /** Signed change applied to customer's debt balance (>0 adds debt; <0 credits/reduces debt). */
        val signedBalanceDeltaCents: Long
            get() = if (netTotalCents >= 0L) {
                (netTotalCents - paidCents).coerceAtLeast(0L)
            } else {
                (netTotalCents - paidCents).coerceAtMost(0L)
            }
        /** Positive magnitude credited to customer account on a negative return invoice. */
        val customerCreditCents: Long
            get() = if (netTotalCents < 0L) (paidCents - netTotalCents).coerceAtLeast(0L) else 0L
        val customerCredit: Double get() = customerCreditCents.moneyDouble
    }

    data class InvoiceTotalsSummary(
        val totalCents: Long,
        val paidCents: Long,
        val remainingDebtCents: Long,
        val changeDueCents: Long
    ) {
        val totalAmount: Double get() = totalCents.moneyDouble
        val paidAmount: Double get() = paidCents.moneyDouble
        val remainingDebt: Double get() = remainingDebtCents.moneyDouble
        val changeDue: Double get() = changeDueCents.moneyDouble
        /** Signed change applied to partner balance (>0 adds debt; <0 reduces debt on negative return). */
        val signedBalanceDeltaCents: Long
            get() = if (totalCents >= 0L) {
                (totalCents - paidCents).coerceAtLeast(0L)
            } else {
                (totalCents - paidCents).coerceAtMost(0L)
            }
        /** Positive magnitude deducted from supplier/customer debt on a negative return invoice. */
        val supplierCreditCents: Long
            get() = if (totalCents < 0L) (paidCents - totalCents).coerceAtLeast(0L) else 0L
        val supplierCredit: Double get() = supplierCreditCents.moneyDouble
    }

    data class LedgerSummary(
        val count: Int,
        val totalAmountCents: Long,
        val totalPaidCents: Long,
        val totalDebtCents: Long
    ) {
        val totalAmount: Double get() = totalAmountCents.moneyDouble
        val totalPaid: Double get() = totalPaidCents.moneyDouble
        val totalDebt: Double get() = totalDebtCents.moneyDouble
    }

    data class ProfitMarginSummary(
        val revenueCents: Long,
        val costCents: Long,
        val grossProfitCents: Long,
        val marginPercent: Double
    ) {
        val revenue: Double get() = revenueCents.moneyDouble
        val cost: Double get() = costCents.moneyDouble
        val grossProfit: Double get() = grossProfitCents.moneyDouble
    }

    fun computeDiscountCents(
        subtotalCents: Long,
        discountType: String,
        discountValue: Double
    ): Long {
        if (subtotalCents <= 0L || discountValue <= 0.0) return 0L
        val rawDiscount = if (discountType.equals("PERCENT", ignoreCase = true)) {
            MoneyUtils.percentageCents(subtotalCents, discountValue.coerceIn(0.0, 100.0))
        } else {
            discountValue.cents.coerceAtLeast(0L)
        }
        return rawDiscount.coerceAtMost(subtotalCents)
    }

    fun computeNetAfterDiscountCents(
        subtotalCents: Long,
        discountType: String,
        discountValue: Double
    ): Long {
        if (subtotalCents <= 0L) return subtotalCents
        val discountCents = computeDiscountCents(subtotalCents, discountType, discountValue)
        return (subtotalCents - discountCents).coerceAtLeast(0L)
    }

    fun computeCartSummary(
        subtotalCents: Long,
        discountType: String,
        discountValue: Double,
        paymentMethod: String,
        receivedCash: Double
    ): CartFinancialSummary {
        val discountCents = computeDiscountCents(subtotalCents, discountType, discountValue)
        val netTotalCents = if (subtotalCents >= 0L) {
            (subtotalCents - discountCents).coerceAtLeast(0L)
        } else {
            subtotalCents
        }
        val receivedCents = receivedCash.cents
        val methodUpper = paymentMethod.trim().uppercase()
        val isDebtMethod = methodUpper == "CREDIT" || methodUpper == "DEBT"

        val paidCents = when {
            netTotalCents < 0L && isDebtMethod -> receivedCents.coerceIn(netTotalCents, 0L)
            netTotalCents < 0L && receivedCents < 0L -> receivedCents.coerceIn(netTotalCents, 0L)
            netTotalCents <= 0L -> netTotalCents
            isDebtMethod -> receivedCents.coerceIn(0L, netTotalCents)
            receivedCents > 0L -> receivedCents.coerceAtMost(netTotalCents)
            else -> netTotalCents
        }

        val changeDueCents = if (netTotalCents >= 0L) {
            (receivedCents - netTotalCents).coerceAtLeast(0L)
        } else {
            0L
        }

        val remainingDebtCents = if (netTotalCents > 0L) {
            (netTotalCents - paidCents).coerceAtLeast(0L)
        } else {
            0L
        }

        val hasRemainingDebt = netTotalCents > 0L && paidCents < netTotalCents
        val hasCustomerCredit = netTotalCents < 0L && paidCents > netTotalCents
        val finalMethod = when {
            hasRemainingDebt -> "DEBT"
            hasCustomerCredit -> "CREDIT"
            methodUpper == "BANK" || methodUpper == "TRANSFER" || methodUpper == "VIREMENT" -> "TRANSFER"
            methodUpper == "CHECK" || methodUpper == "CHEQUE" -> "CHECK"
            else -> "CASH"
        }

        return CartFinancialSummary(
            subtotalCents = subtotalCents,
            discountCents = discountCents,
            netTotalCents = netTotalCents,
            receivedCents = receivedCents,
            paidCents = paidCents,
            changeDueCents = changeDueCents,
            remainingDebtCents = remainingDebtCents,
            finalPaymentMethod = finalMethod
        )
    }

    fun computeLineTotalCents(
        unitPriceCents: Long,
        quantity: Double,
        lineDiscountCents: Long = 0L
    ): Long {
        val gross = MoneyUtils.multiplyCents(unitPriceCents, quantity)
        return if (quantity < 0.0) {
            gross
        } else {
            (gross - lineDiscountCents.coerceAtLeast(0L)).coerceAtLeast(0L)
        }
    }

    fun computeLineTotalDouble(
        unitPrice: Double,
        quantity: Double,
        lineDiscount: Double = 0.0
    ): Double {
        return computeLineTotalCents(
            unitPriceCents = unitPrice.cents,
            quantity = quantity,
            lineDiscountCents = lineDiscount.cents
        ).moneyDouble
    }

    fun computeLinesTotalCents(pairs: Iterable<Pair<Long, Double>>): Long {
        var total = 0L
        for ((unitCents, qty) in pairs) {
            total += MoneyUtils.multiplyCents(unitCents, qty)
        }
        return total
    }

    fun computeSaleItemsTotalCents(items: List<SaleItemEntity>): Long {
        return items.sumOf { item ->
            val gross = MoneyUtils.multiplyCents(item.unitPrice, item.quantity)
            if (item.quantity < 0.0) gross else (gross - item.discountValue).coerceAtLeast(0L)
        }
    }

    fun computePurchaseItemsTotalCents(items: List<PurchaseItemEntity>): Long {
        return items.sumOf { item -> MoneyUtils.multiplyCents(item.unitPrice, item.quantity) }
    }

    fun computeEditedInvoiceSummary(
        newTotalCents: Long,
        originalTotalCents: Long,
        originalPaidCents: Long,
        overridePaidCents: Long? = null
    ): InvoiceTotalsSummary {
        val effectivePaidCents = when {
            overridePaidCents != null -> overridePaidCents
            originalPaidCents >= originalTotalCents -> newTotalCents
            else -> originalPaidCents.coerceAtMost(newTotalCents.coerceAtLeast(0L))
        }
        val remainingDebtCents = (newTotalCents - effectivePaidCents).coerceAtLeast(0L)
        val changeDueCents = if (newTotalCents >= 0L) {
            (effectivePaidCents - newTotalCents).coerceAtLeast(0L)
        } else {
            0L
        }
        return InvoiceTotalsSummary(
            totalCents = newTotalCents,
            paidCents = effectivePaidCents,
            remainingDebtCents = remainingDebtCents,
            changeDueCents = changeDueCents
        )
    }

    fun computePaymentSettlement(
        totalCents: Long,
        paidInputCents: Long
    ): InvoiceTotalsSummary {
        val normalizedPaid = if (totalCents < 0L) {
            paidInputCents.coerceIn(totalCents, 0L)
        } else {
            paidInputCents
        }
        val remainingDebt = if (totalCents >= 0L) {
            (totalCents - normalizedPaid).coerceAtLeast(0L)
        } else {
            0L
        }
        val changeDue = if (totalCents >= 0L) (normalizedPaid - totalCents).coerceAtLeast(0L) else 0L
        return InvoiceTotalsSummary(
            totalCents = totalCents,
            paidCents = normalizedPaid,
            remainingDebtCents = remainingDebt,
            changeDueCents = changeDue
        )
    }

    fun computeReturnAmountCents(unitPriceCents: Long, quantity: Double): Long {
        return MoneyUtils.multiplyCents(unitPriceCents, kotlin.math.abs(quantity)).coerceAtLeast(0L)
    }

    fun computeReturnAmountDouble(unitPrice: Double, quantity: Double): Double {
        return computeReturnAmountCents(unitPrice.cents, quantity).moneyDouble
    }

    fun computeSalesLedgerSummary(sales: List<SaleEntity>): LedgerSummary {
        var total = 0L
        var paid = 0L
        var debt = 0L
        for (sale in sales) {
            total += sale.totalAmount
            val effectivePaid = if (sale.totalAmount >= 0L) {
                sale.paidAmount.coerceAtMost(sale.totalAmount)
            } else {
                sale.paidAmount.coerceIn(sale.totalAmount, 0L)
            }
            paid += effectivePaid
            debt += if (sale.totalAmount >= 0L) {
                (sale.totalAmount - effectivePaid).coerceAtLeast(0L)
            } else {
                (sale.totalAmount - effectivePaid).coerceAtMost(0L)
            }
        }
        return LedgerSummary(
            count = sales.size,
            totalAmountCents = total,
            totalPaidCents = paid,
            totalDebtCents = debt
        )
    }

    fun computePurchasesLedgerSummary(purchases: List<PurchaseEntity>): LedgerSummary {
        var total = 0L
        var paid = 0L
        var debt = 0L
        for (purchase in purchases) {
            total += purchase.totalAmount
            val effectivePaid = if (purchase.totalAmount >= 0L) {
                purchase.paidAmount.coerceAtMost(purchase.totalAmount)
            } else {
                purchase.paidAmount.coerceIn(purchase.totalAmount, 0L)
            }
            paid += effectivePaid
            debt += if (purchase.totalAmount >= 0L) {
                (purchase.totalAmount - effectivePaid).coerceAtLeast(0L)
            } else {
                (purchase.totalAmount - effectivePaid).coerceAtMost(0L)
            }
        }
        return LedgerSummary(
            count = purchases.size,
            totalAmountCents = total,
            totalPaidCents = paid,
            totalDebtCents = debt
        )
    }

    data class DebtPaymentPreview(
        val currentDebtCents: Long,
        val enteredAmountCents: Long,
        val deltaCents: Long
    ) {
        val isOverpayment: Boolean get() = deltaCents > 0L
        val isExactSettlement: Boolean get() = deltaCents == 0L && enteredAmountCents > 0L
        val isPartialPayment: Boolean get() = deltaCents < 0L
        val overpaymentAmount: Double get() = deltaCents.coerceAtLeast(0L).moneyDouble
        val remainingDebtAfter: Double get() = (-deltaCents).coerceAtLeast(0L).moneyDouble
    }

    data class StatementLedgerTotals(
        val totalInvoicesCents: Long,
        val totalPaymentsCents: Long,
        val netDifferenceCents: Long
    ) {
        val totalInvoices: Double get() = totalInvoicesCents.moneyDouble
        val totalPayments: Double get() = totalPaymentsCents.moneyDouble
        val netDifference: Double get() = netDifferenceCents.moneyDouble
        val absNetDifference: Double get() = kotlin.math.abs(netDifferenceCents).moneyDouble
        val customerOwesStore: Boolean get() = netDifferenceCents > 0L
        val storeOwesCustomer: Boolean get() = netDifferenceCents < 0L
    }

    fun computeDebtPaymentPreview(
        currentDebtCents: Long,
        enteredAmount: Double
    ): DebtPaymentPreview {
        val enteredCents = enteredAmount.coerceAtLeast(0.0).cents
        return DebtPaymentPreview(
            currentDebtCents = currentDebtCents,
            enteredAmountCents = enteredCents,
            deltaCents = enteredCents - currentDebtCents
        )
    }

    fun computeStatementTotals(
        invoiceAmountsCents: Iterable<Long>,
        paymentAmountsCents: Iterable<Long>
    ): StatementLedgerTotals {
        var totalInv = 0L
        for (v in invoiceAmountsCents) totalInv += v
        var totalPay = 0L
        for (v in paymentAmountsCents) totalPay += v
        return StatementLedgerTotals(
            totalInvoicesCents = totalInv,
            totalPaymentsCents = totalPay,
            netDifferenceCents = totalInv - totalPay
        )
    }

    fun sumPositiveBalancesCents(balancesCents: Iterable<Long>): Long {
        var sum = 0L
        for (b in balancesCents) {
            if (b > 0L) sum += b
        }
        return sum
    }

    fun sumCents(values: Iterable<Long>): Long {
        var sum = 0L
        for (v in values) sum += v
        return sum
    }

    fun computeProfitMargin(revenueCents: Long, costCents: Long): ProfitMarginSummary {
        val profit = revenueCents - costCents
        val marginPercent = if (revenueCents > 0L) {
            MoneyUtils.roundMoney((profit.toDouble() / revenueCents.toDouble()) * 100.0)
        } else {
            0.0
        }
        return ProfitMarginSummary(
            revenueCents = revenueCents,
            costCents = costCents,
            grossProfitCents = profit,
            marginPercent = marginPercent
        )
    }

    data class AnalyticsPeriodMetrics(
        val totalSalesCents: Long,
        val invoiceCount: Int,
        val expensesCents: Long,
        val grossProfitCents: Long,
        val netProfitCents: Long,
        val totalPurchasesCents: Long,
        val supplierPaymentsCents: Long,
        val totalDebtsCents: Long,
        val cashAmountCents: Long,
        val cardAmountCents: Long,
        val bankAmountCents: Long,
        val creditAmountCents: Long,
        val cashPercent: Int,
        val cardPercent: Int,
        val bankPercent: Int,
        val creditPercent: Int,
        val avgTicketCents: Long,
        val profitMarginPercent: Double
    ) {
        val totalSales: Double get() = totalSalesCents.moneyDouble
        val expenses: Double get() = expensesCents.moneyDouble
        val grossProfit: Double get() = grossProfitCents.moneyDouble
        val netProfit: Double get() = netProfitCents.moneyDouble
        val totalPurchases: Double get() = totalPurchasesCents.moneyDouble
        val supplierPayments: Double get() = supplierPaymentsCents.moneyDouble
        val totalDebts: Double get() = totalDebtsCents.moneyDouble
        val cashAmount: Double get() = cashAmountCents.moneyDouble
        val cardAmount: Double get() = cardAmountCents.moneyDouble
        val bankAmount: Double get() = bankAmountCents.moneyDouble
        val creditAmount: Double get() = creditAmountCents.moneyDouble
        val avgTicket: Double get() = avgTicketCents.moneyDouble
    }

    fun computeAnalyticsPeriodMetrics(
        sales: List<SaleEntity>,
        expenses: List<ExpenseEntity>,
        purchases: List<PurchaseEntity>,
        customers: List<CustomerEntity>,
        salesHistory: List<ProductSaleHistoryRecord>
    ): AnalyticsPeriodMetrics {
        var totalSalesCents = 0L
        var cashCents = 0L
        var cardCents = 0L
        var bankCents = 0L
        var creditCents = 0L
        for (s in sales) {
            totalSalesCents += s.totalAmount
            when (s.paymentMethod) {
                "CASH" -> cashCents += s.totalAmount
                "CARD" -> cardCents += s.totalAmount
                "BANK", "TRANSFER" -> bankCents += s.totalAmount
                "CREDIT", "DEFERRED", "DEBT" -> creditCents += s.totalAmount
            }
        }
        val expensesCents = sumCents(expenses.map { it.amount })
        var purchasesCents = 0L
        var supplierPayCents = 0L
        for (p in purchases) {
            purchasesCents += p.totalAmount
            supplierPayCents += p.paidAmount
        }
        val debtsCents = sumPositiveBalancesCents(customers.map { it.balance })
        val grossProfitCents = salesHistory.sumOf { it.profit.cents }
        val netProfitCents = grossProfitCents - expensesCents

        val marginPercent = if (totalSalesCents > 0L) {
            MoneyUtils.roundMoney((grossProfitCents.toDouble() / totalSalesCents.toDouble()) * 100.0)
        } else 0.0
        val avgTicketCents = if (sales.isNotEmpty()) totalSalesCents / sales.size else 0L

        val totalPay = (cashCents + cardCents + bankCents + creditCents).coerceAtLeast(1L)
        fun pct(part: Long): Int = if (totalSalesCents > 0L) ((part * 100L) / totalPay).toInt() else 0

        return AnalyticsPeriodMetrics(
            totalSalesCents = totalSalesCents,
            invoiceCount = sales.size,
            expensesCents = expensesCents,
            grossProfitCents = grossProfitCents,
            netProfitCents = netProfitCents,
            totalPurchasesCents = purchasesCents,
            supplierPaymentsCents = supplierPayCents,
            totalDebtsCents = debtsCents,
            cashAmountCents = cashCents,
            cardAmountCents = cardCents,
            bankAmountCents = bankCents,
            creditAmountCents = creditCents,
            cashPercent = pct(cashCents),
            cardPercent = pct(cardCents),
            bankPercent = pct(bankCents),
            creditPercent = pct(creditCents),
            avgTicketCents = avgTicketCents,
            profitMarginPercent = marginPercent
        )
    }

    fun computeMonthlyAnalyticsSeries(
        allSales: List<SaleEntity>,
        allExpenses: List<ExpenseEntity>,
        salesHistory: List<ProductSaleHistoryRecord>,
        metricTab: String,
        fallbackCurrentTotal: Double
    ): List<Float> {
        val cal = java.util.Calendar.getInstance()
        val salesByMonth = DoubleArray(12)
        for (s in allSales) {
            if (!s.isVoided) {
                cal.timeInMillis = s.timestamp
                salesByMonth[cal.get(java.util.Calendar.MONTH)] += s.totalAmountDouble
            }
        }
        val profitByMonth = DoubleArray(12)
        if (metricTab == "الأرباح") {
            for (h in salesHistory) {
                cal.timeInMillis = h.timestamp
                profitByMonth[cal.get(java.util.Calendar.MONTH)] += h.profit
            }
        }
        val expensesByMonth = DoubleArray(12)
        if (metricTab == "المصاريف") {
            for (e in allExpenses) {
                cal.timeInMillis = e.timestamp
                expensesByMonth[cal.get(java.util.Calendar.MONTH)] += e.amountDouble
            }
        }
        val months = MutableList(12) { idx ->
            val monthIndex = 11 - idx // months 12 down to 1
            when (metricTab) {
                "الأرباح" -> profitByMonth[monthIndex].toFloat()
                "المصاريف" -> expensesByMonth[monthIndex].toFloat()
                else -> salesByMonth[monthIndex].toFloat()
            }
        }
        if (months.all { it == 0f } && fallbackCurrentTotal > 0.0) {
            months[months.lastIndex] = fallbackCurrentTotal.toFloat()
        }
        return months
    }
}
