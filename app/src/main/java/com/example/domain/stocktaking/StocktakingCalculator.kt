package com.example.domain.stocktaking

import com.example.util.MoneyUtils
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Pure deterministic calculator for stocktaking discrepancies, purchase-cost valuation,
 * retail margin estimation, and session progress metrics.
 *
 * Financial policy:
 * - All discrepancy losses/gains MUST be valued at purchase cost (`unitCostCents`), never `sellPrice`.
 * - All monetary amounts are represented as exact integer cents (`Long`).
 */
object StocktakingCalculator {

    const val QUANTITY_EPSILON: Double = 1e-6

    data class ItemSummaryInput(
        val countedQuantity: Double?,
        val expectedQuantity: Double,
        val unitCostCents: Long
    )

    data class InventorySessionSummary(
        val totalItemsCount: Int,
        val countedItemsCount: Int,
        val uncountedItemsCount: Int,
        val matchingItemsCount: Int,
        val shortageItemsCount: Int,
        val surplusItemsCount: Int,
        val discrepancyItemsCount: Int,
        val totalShortageQuantity: Double,
        val totalSurplusQuantity: Double,
        val totalShortageValueCents: Long,
        val totalSurplusValueCents: Long,
        val netDiscrepancyValueCents: Long,
        val progressPercent: Float,
        val accuracyPercent: Float
    )

    data class ProductValuationMetrics(
        val purchaseValueCents: Long,
        val sellValueCents: Long,
        val potentialGrossMarginCents: Long,
        val marginPercent: Double
    )

    /**
     * Rounds quantity to 3 decimal places to prevent floating-point noise (e.g. 0.30000000000000004)
     * and avoids signed negative zero (`-0.0`).
     */
    fun normalizeQuantity(quantity: Double): Double {
        if (!quantity.isFinite()) return 0.0
        val rounded = (quantity * 1000.0).roundToLong() / 1000.0
        return if (abs(rounded) < QUANTITY_EPSILON) 0.0 else rounded
    }

    /**
     * Computes `actual - expected`. Negative = shortage, positive = surplus, 0 = matching.
     */
    fun computeDifference(countedQuantity: Double, expectedQuantity: Double): Double {
        return normalizeQuantity(countedQuantity - expectedQuantity)
    }

    fun classifyItemStatus(countedQuantity: Double?, expectedQuantity: Double): InventoryItemStatus {
        if (countedQuantity == null) return InventoryItemStatus.UNCOUNTED
        val diff = computeDifference(countedQuantity, expectedQuantity)
        return when {
            diff < -QUANTITY_EPSILON -> InventoryItemStatus.SHORTAGE
            diff > QUANTITY_EPSILON -> InventoryItemStatus.SURPLUS
            else -> InventoryItemStatus.MATCHING
        }
    }

    /**
     * Computes signed discrepancy financial impact in cents based strictly on `unitCostCents` (purchasePrice).
     * Negative value = financial shortage loss; positive value = financial surplus gain.
     */
    fun computeDiscrepancyValueCents(unitCostCents: Long, difference: Double): Long {
        val cleanDiff = normalizeQuantity(difference)
        if (abs(cleanDiff) < QUANTITY_EPSILON || unitCostCents == 0L) return 0L
        return MoneyUtils.multiplyCents(unitCostCents, cleanDiff)
    }

    fun computeSessionSummary(items: List<ItemSummaryInput>): InventorySessionSummary {
        var counted = 0
        var matching = 0
        var shortage = 0
        var surplus = 0
        var totalShortageQty = 0.0
        var totalSurplusQty = 0.0
        var totalShortageValueCents = 0L
        var totalSurplusValueCents = 0L

        for (item in items) {
            val countedQty = item.countedQuantity ?: continue
            counted++
            val diff = computeDifference(countedQty, item.expectedQuantity)
            val impactCents = computeDiscrepancyValueCents(item.unitCostCents, diff)
            when {
                diff < -QUANTITY_EPSILON -> {
                    shortage++
                    totalShortageQty += abs(diff)
                    totalShortageValueCents += abs(impactCents)
                }
                diff > QUANTITY_EPSILON -> {
                    surplus++
                    totalSurplusQty += diff
                    totalSurplusValueCents += abs(impactCents)
                }
                else -> matching++
            }
        }

        val total = items.size
        val uncounted = (total - counted).coerceAtLeast(0)
        val discrepancyCount = shortage + surplus
        val netValueCents = totalSurplusValueCents - totalShortageValueCents
        val progress = if (total > 0) (counted.toFloat() / total.toFloat()) * 100f else 0f
        val accuracy = if (counted > 0) (matching.toFloat() / counted.toFloat()) * 100f else 100f

        return InventorySessionSummary(
            totalItemsCount = total,
            countedItemsCount = counted,
            uncountedItemsCount = uncounted,
            matchingItemsCount = matching,
            shortageItemsCount = shortage,
            surplusItemsCount = surplus,
            discrepancyItemsCount = discrepancyCount,
            totalShortageQuantity = normalizeQuantity(totalShortageQty),
            totalSurplusQuantity = normalizeQuantity(totalSurplusQty),
            totalShortageValueCents = totalShortageValueCents,
            totalSurplusValueCents = totalSurplusValueCents,
            netDiscrepancyValueCents = netValueCents,
            progressPercent = progress.coerceIn(0f, 100f),
            accuracyPercent = accuracy.coerceIn(0f, 100f)
        )
    }

    /**
     * Computes purchase valuation, retail valuation, and potential gross margin for a product's stock.
     * Negative stocks are clamped to 0 for positive asset valuation to avoid distorting total store inventory assets.
     */
    fun computeProductValuation(
        purchasePriceCents: Long,
        sellPriceCents: Long,
        currentStock: Double
    ): ProductValuationMetrics {
        val positiveStock = currentStock.coerceAtLeast(0.0)
        val purchaseValue = MoneyUtils.multiplyCents(purchasePriceCents.coerceAtLeast(0L), positiveStock)
        val sellValue = MoneyUtils.multiplyCents(sellPriceCents.coerceAtLeast(0L), positiveStock)
        val grossMargin = sellValue - purchaseValue
        val marginPct = if (sellValue > 0L) {
            (grossMargin.toDouble() / sellValue.toDouble()) * 100.0
        } else {
            0.0
        }
        return ProductValuationMetrics(
            purchaseValueCents = purchaseValue,
            sellValueCents = sellValue,
            potentialGrossMarginCents = grossMargin,
            marginPercent = marginPct
        )
    }

    fun formatQuantity(quantity: Double): String {
        val clean = normalizeQuantity(quantity)
        return if (abs(clean - clean.roundToLong().toDouble()) < QUANTITY_EPSILON) {
            String.format(Locale.US, "%d", clean.roundToLong())
        } else {
            String.format(Locale.US, "%.3f", clean).trimEnd('0').trimEnd('.')
        }
    }

    fun formatSignedQuantity(difference: Double): String {
        val clean = normalizeQuantity(difference)
        val formatted = formatQuantity(abs(clean))
        return when {
            clean > QUANTITY_EPSILON -> "+$formatted"
            clean < -QUANTITY_EPSILON -> "-$formatted"
            else -> "0"
        }
    }
}
