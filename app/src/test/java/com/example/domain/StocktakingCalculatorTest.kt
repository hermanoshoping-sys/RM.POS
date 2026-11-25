package com.example.domain

import com.example.domain.stocktaking.InventoryItemStatus
import com.example.domain.stocktaking.StocktakingCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class StocktakingCalculatorTest {

    @Test
    fun `discrepancy difference and status classification handle shortage surplus matching uncounted and decimals`() {
        assertEquals(InventoryItemStatus.UNCOUNTED, StocktakingCalculator.classifyItemStatus(null, 10.0))
        assertEquals(InventoryItemStatus.MATCHING, StocktakingCalculator.classifyItemStatus(10.0, 10.0))
        assertEquals(InventoryItemStatus.SHORTAGE, StocktakingCalculator.classifyItemStatus(7.5, 10.0))
        assertEquals(InventoryItemStatus.SURPLUS, StocktakingCalculator.classifyItemStatus(12.25, 10.0))

        assertEquals(-2.5, StocktakingCalculator.computeDifference(7.5, 10.0), 1e-6)
        assertEquals(2.25, StocktakingCalculator.computeDifference(12.25, 10.0), 1e-6)
        // Floating-point noise protection (0.3 - 0.1 - 0.2)
        assertEquals(0.0, StocktakingCalculator.computeDifference(0.1 + 0.2, 0.3), 1e-6)
    }

    @Test
    fun `discrepancy financial value uses strictly purchasePrice cents and never sellPrice`() {
        val purchaseCostCents = 1550L // 15.50
        // Shortage of 2.5 units -> -3875 cents (-38.75)
        assertEquals(-3875L, StocktakingCalculator.computeDiscrepancyValueCents(purchaseCostCents, -2.5))
        // Surplus of 3 units -> +4650 cents (+46.50)
        assertEquals(4650L, StocktakingCalculator.computeDiscrepancyValueCents(purchaseCostCents, 3.0))
        // Zero difference -> 0 cents
        assertEquals(0L, StocktakingCalculator.computeDiscrepancyValueCents(purchaseCostCents, 0.0))
    }

    @Test
    fun `session summary aggregates shortage surplus net value progress and accuracy`() {
        val items = listOf(
            StocktakingCalculator.ItemSummaryInput(countedQuantity = 10.0, expectedQuantity = 10.0, unitCostCents = 1000L), // match
            StocktakingCalculator.ItemSummaryInput(countedQuantity = 8.0, expectedQuantity = 10.0, unitCostCents = 1500L),  // shortage -2 => -3000c
            StocktakingCalculator.ItemSummaryInput(countedQuantity = 7.0, expectedQuantity = 5.0, unitCostCents = 2000L),   // surplus +2 => +4000c
            StocktakingCalculator.ItemSummaryInput(countedQuantity = null, expectedQuantity = 4.0, unitCostCents = 500L)    // uncounted
        )
        val summary = StocktakingCalculator.computeSessionSummary(items)

        assertEquals(4, summary.totalItemsCount)
        assertEquals(3, summary.countedItemsCount)
        assertEquals(1, summary.uncountedItemsCount)
        assertEquals(1, summary.matchingItemsCount)
        assertEquals(1, summary.shortageItemsCount)
        assertEquals(1, summary.surplusItemsCount)
        assertEquals(2, summary.discrepancyItemsCount)
        assertEquals(2.0, summary.totalShortageQuantity, 1e-6)
        assertEquals(2.0, summary.totalSurplusQuantity, 1e-6)
        assertEquals(3000L, summary.totalShortageValueCents)
        assertEquals(4000L, summary.totalSurplusValueCents)
        assertEquals(1000L, summary.netDiscrepancyValueCents) // +4000 - 3000 = +1000
        assertEquals(75f, summary.progressPercent, 0.01f)
        assertEquals(33.33f, summary.accuracyPercent, 0.05f)
    }

    @Test
    fun `product valuation computes purchase value sell value gross margin and clamps negative stock`() {
        val metrics = StocktakingCalculator.computeProductValuation(
            purchasePriceCents = 1000L,
            sellPriceCents = 1500L,
            currentStock = 4.5
        )
        assertEquals(4500L, metrics.purchaseValueCents)
        assertEquals(6750L, metrics.sellValueCents)
        assertEquals(2250L, metrics.potentialGrossMarginCents)
        assertEquals(33.333, metrics.marginPercent, 0.01)

        val negativeStockMetrics = StocktakingCalculator.computeProductValuation(
            purchasePriceCents = 1000L,
            sellPriceCents = 1500L,
            currentStock = -3.0
        )
        assertEquals(0L, negativeStockMetrics.purchaseValueCents)
        assertEquals(0L, negativeStockMetrics.sellValueCents)
        assertEquals(0L, negativeStockMetrics.potentialGrossMarginCents)
    }

    @Test
    fun `quantity formatting removes redundant trailing zeros`() {
        assertEquals("5", StocktakingCalculator.formatQuantity(5.0))
        assertEquals("2.5", StocktakingCalculator.formatQuantity(2.500))
        assertEquals("1.375", StocktakingCalculator.formatQuantity(1.375))
        assertEquals("+2.5", StocktakingCalculator.formatSignedQuantity(2.5))
        assertEquals("-3", StocktakingCalculator.formatSignedQuantity(-3.0))
        assertEquals("0", StocktakingCalculator.formatSignedQuantity(0.0))
    }
}
