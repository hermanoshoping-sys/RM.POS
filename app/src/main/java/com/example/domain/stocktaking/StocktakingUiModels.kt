package com.example.domain.stocktaking

import com.example.data.InventoryEntity
import com.example.data.InventoryItemEntity

/**
 * Aggregated real-database metrics shown on the Inventory Management dashboard.
 */
data class InventoryDashboardStats(
    val totalActiveProducts: Int = 0,
    val totalUnitsInStock: Double = 0.0,
    val totalPurchaseValueCents: Long = 0L,
    val totalSellValueCents: Long = 0L,
    val potentialGrossMarginCents: Long = 0L,
    val activeInventoriesCount: Int = 0,
    val completedInventoriesCount: Int = 0,
    val lastCompletedAt: Long? = null,
    val lastCompletedReference: String? = null,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val totalAdjustmentsCount: Int = 0,
    val lastNetDiscrepancyCents: Long = 0L
)

/**
 * Valuation breakdown grouped by product category.
 */
data class CategoryValuationRow(
    val categoryId: Long,
    val categoryName: String,
    val productCount: Int,
    val totalUnits: Double,
    val purchaseValueCents: Long,
    val sellValueCents: Long,
    val potentialMarginCents: Long
)

/**
 * Valuation breakdown grouped by supplier (derived from non-voided purchase history).
 */
data class SupplierValuationRow(
    val supplierId: Long,
    val supplierName: String,
    val productCount: Int,
    val totalUnits: Double,
    val purchaseValueCents: Long,
    val sellValueCents: Long,
    val potentialMarginCents: Long
)

/**
 * Real-time stock monitoring alert item (Out of Stock, Low Stock, or Slow-Moving).
 */
data class StockMonitoringItem(
    val productId: Long,
    val productName: String,
    val barcode: String,
    val categoryName: String,
    val unit: String,
    val currentStock: Double,
    val minStock: Double,
    val purchasePriceCents: Long,
    val sellPriceCents: Long,
    val soldQtyRecentWindow: Double,
    val lastSoldAt: Long?,
    val alertType: StockAlertType
)

/**
 * Live reconciliation preview row shown to the manager before approving stock adjustments.
 */
data class InventoryApprovalPreviewItem(
    val item: InventoryItemEntity,
    val liveSystemStock: Double,
    val preCountMovementDelta: Double,
    val postCountMovementDelta: Double,
    val projectedAdjustedStock: Double,
    val hasConcurrentMovement: Boolean
)

/**
 * Full approval review summary computed live against current `products.currentStock`.
 */
data class InventoryApprovalPreview(
    val inventory: InventoryEntity,
    val itemsToAdjust: List<InventoryApprovalPreviewItem>,
    val uncountedItemsCount: Int,
    val concurrentMovementItemsCount: Int,
    val totalShortageValueCents: Long,
    val totalSurplusValueCents: Long,
    val netDiscrepancyValueCents: Long
)

/**
 * Side-by-side comparison between a completed inventory session and the previous completed session.
 */
data class InventoryComparisonSummary(
    val currentInventory: InventoryEntity,
    val previousInventory: InventoryEntity?,
    val discrepancyCountDelta: Int,
    val netValueDeltaCents: Long,
    val currentAccuracyPercent: Float,
    val previousAccuracyPercent: Float?
)

/**
 * Result of scanning a barcode inside an active physical counting session.
 */
sealed class BarcodeScanOutcome {
    data class Updated(
        val item: InventoryItemEntity,
        val previousCountedQuantity: Double?,
        val newCountedQuantity: Double
    ) : BarcodeScanOutcome()

    data class PromptDuplicate(
        val item: InventoryItemEntity,
        val existingCountedQuantity: Double,
        val incrementDelta: Double
    ) : BarcodeScanOutcome()

    data class NotInSession(
        val barcode: String,
        val catalogProductId: Long,
        val catalogProductName: String
    ) : BarcodeScanOutcome()

    data class ProductNotFound(val barcode: String) : BarcodeScanOutcome()

    data class SessionReadOnly(val status: InventoryStatus) : BarcodeScanOutcome()
}
