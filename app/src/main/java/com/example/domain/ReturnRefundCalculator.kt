package com.example.domain

import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.util.MoneyUtils
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Result of computing the exact net paid refundable amount for a customer return,
 * accounting for both line-level discounts (`SaleItemEntity.discountValue`) and
 * proportional invoice-level discounts (`SaleEntity.discountAmount`).
 */
data class ReturnRefundComputation(
    val originalSoldQty: Double,
    val alreadyReturnedQty: Double,
    val remainingReturnableQty: Double,
    val lineGrossBeforeDiscountsCents: Long,
    val lineDiscountCents: Long,
    val invoiceDiscountShareCents: Long,
    val effectiveLinePaidCents: Long,
    val maxRefundCents: Long,
    val effectiveUnitNetCents: Long
) {
    val hasDiscountsApplied: Boolean
        get() = lineDiscountCents > 0L || invoiceDiscountShareCents > 0L
}

/**
 * Pure domain calculator enforcing exact cent-level return refund math for discounted sales.
 *
 * Formulas:
 * 1. Line net after line discount = `max(0, unitPrice * quantity - discountValue)`
 *    (bounded above by `totalLinePrice` when explicitly persisted).
 * 2. Invoice-level discount (`sale.discountAmount`, or implicit `subtotal - sale.totalAmount`)
 *    is distributed proportionally across all positive-quantity lines by their line net share.
 * 3. Partial return refund uses cumulative-difference rounding:
 *    `round(effectiveLinePaid * (alreadyReturned + returnQty) / originalQty) - round(effectiveLinePaid * alreadyReturned / originalQty)`
 *    so repeated partial returns sum to `effectiveLinePaid` exactly with zero cent drift and zero over-refund.
 */
object ReturnRefundCalculator {

    private const val QTY_EPSILON = 1e-6

    fun computeLineNetBeforeInvoiceDiscountCents(item: SaleItemEntity): Long {
        if (item.quantity <= 0.0) return 0L
        val gross = MoneyUtils.multiplyCents(item.unitPrice, item.quantity).coerceAtLeast(0L)
        val afterLineDiscount = (gross - item.discountValue.coerceAtLeast(0L)).coerceAtLeast(0L)
        return if (item.totalLinePrice > 0L) {
            minOf(afterLineDiscount, item.totalLinePrice)
        } else {
            afterLineDiscount
        }
    }

    /**
     * Computes the exact maximum refundable amount in cents for returning `returnQuantity`
     * of `productId` from `sale`, throwing [IllegalArgumentException] if the item is not
     * present in the original sale or if `returnQuantity` exceeds the remaining returnable quantity.
     */
    fun computeLineReturnRefund(
        sale: SaleEntity,
        saleItems: List<SaleItemEntity>,
        productId: Long,
        returnQuantity: Double,
        alreadyReturnedQuantity: Double = 0.0
    ): ReturnRefundComputation {
        val qty = abs(returnQuantity)
        if (!qty.isFinite() || qty <= 0.0) {
            throw IllegalArgumentException("الكمية المرتجعة غير صالحة")
        }
        val priorReturned = abs(alreadyReturnedQuantity).let { if (it.isFinite()) it else 0.0 }

        val positiveItems = saleItems.filter { it.quantity > 0.0 }
        val matchingItems = positiveItems.filter { it.productId == productId }
        if (matchingItems.isEmpty()) {
            throw IllegalArgumentException("الصنف المحدد غير موجود في بنود الفاتورة الأصلية")
        }

        val originalSoldQty = matchingItems.sumOf { it.quantity }
        val remainingQty = (originalSoldQty - priorReturned).coerceAtLeast(0.0)
        if (qty > remainingQty + QTY_EPSILON) {
            throw IllegalArgumentException(
                "الكمية المرتجعة ($qty) تتجاوز الكمية المتبقية القابلة للإرجاع من الفاتورة ($remainingQty)"
            )
        }

        val subtotalAfterLineDiscountsCents = positiveItems.sumOf { computeLineNetBeforeInvoiceDiscountCents(it) }
        val targetGrossCents = matchingItems.sumOf {
            MoneyUtils.multiplyCents(it.unitPrice, it.quantity).coerceAtLeast(0L)
        }
        val targetLineNetBeforeInvoiceCents = matchingItems.sumOf { computeLineNetBeforeInvoiceDiscountCents(it) }
        val targetLineDiscountCents = (targetGrossCents - targetLineNetBeforeInvoiceCents).coerceAtLeast(0L)

        val implicitInvoiceDiscount = (subtotalAfterLineDiscountsCents - sale.totalAmount.coerceAtLeast(0L))
            .coerceAtLeast(0L)
        val effectiveInvoiceDiscountCents = maxOf(
            sale.discountAmount.coerceAtLeast(0L),
            implicitInvoiceDiscount
        ).coerceAtMost(subtotalAfterLineDiscountsCents)

        val invoiceDiscountShareBd = if (subtotalAfterLineDiscountsCents > 0L && effectiveInvoiceDiscountCents > 0L) {
            BigDecimal.valueOf(effectiveInvoiceDiscountCents)
                .multiply(BigDecimal.valueOf(targetLineNetBeforeInvoiceCents))
                .divide(BigDecimal.valueOf(subtotalAfterLineDiscountsCents), 8, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val effectiveLinePaidBd = BigDecimal.valueOf(targetLineNetBeforeInvoiceCents)
            .subtract(invoiceDiscountShareBd)
            .max(BigDecimal.ZERO)

        val origQtyBd = BigDecimal.valueOf(originalSoldQty)
        val clampedPriorBd = BigDecimal.valueOf(priorReturned.coerceAtMost(originalSoldQty))
        val clampedAfterBd = BigDecimal.valueOf((priorReturned + qty).coerceAtMost(originalSoldQty))

        val cumulativeBeforeCents = effectiveLinePaidBd
            .multiply(clampedPriorBd)
            .divide(origQtyBd, 0, RoundingMode.HALF_UP)
            .toLong()
            .coerceAtLeast(0L)

        val cumulativeAfterCents = effectiveLinePaidBd
            .multiply(clampedAfterBd)
            .divide(origQtyBd, 0, RoundingMode.HALF_UP)
            .toLong()
            .coerceAtLeast(0L)

        val maxRefundCents = (cumulativeAfterCents - cumulativeBeforeCents).coerceAtLeast(0L)
        val effectiveLinePaidCents = effectiveLinePaidBd.setScale(0, RoundingMode.HALF_UP).toLong().coerceAtLeast(0L)
        val invoiceDiscountShareCents = invoiceDiscountShareBd.setScale(0, RoundingMode.HALF_UP).toLong().coerceAtLeast(0L)
        val effectiveUnitNetCents = effectiveLinePaidBd
            .divide(origQtyBd, 0, RoundingMode.HALF_UP)
            .toLong()
            .coerceAtLeast(0L)

        return ReturnRefundComputation(
            originalSoldQty = originalSoldQty,
            alreadyReturnedQty = priorReturned,
            remainingReturnableQty = remainingQty,
            lineGrossBeforeDiscountsCents = targetGrossCents,
            lineDiscountCents = targetLineDiscountCents,
            invoiceDiscountShareCents = invoiceDiscountShareCents,
            effectiveLinePaidCents = effectiveLinePaidCents,
            maxRefundCents = maxRefundCents,
            effectiveUnitNetCents = effectiveUnitNetCents
        )
    }

    /**
     * Null-safe wrapper for UI previews when user is typing intermediate quantities.
     */
    fun computeLineReturnRefundOrNull(
        sale: SaleEntity,
        saleItems: List<SaleItemEntity>,
        productId: Long,
        returnQuantity: Double,
        alreadyReturnedQuantity: Double = 0.0
    ): ReturnRefundComputation? = runCatching {
        computeLineReturnRefund(sale, saleItems, productId, returnQuantity, alreadyReturnedQuantity)
    }.getOrNull()
}
