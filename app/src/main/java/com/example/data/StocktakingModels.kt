package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Physical stocktaking (Inventaire) session header.
 * Monetary totals are stored strictly in integer cents (`Long`) valued at product `purchasePrice`.
 */
@Entity(
    tableName = "inventories",
    indices = [
        Index(value = ["reference"], unique = true),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class InventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val reference: String,
    val title: String = "",
    val type: String = "GENERAL", // GENERAL, PARTIAL_CATEGORY, PARTIAL_PRODUCTS, PARTIAL_SUPPLIER
    val status: String = "DRAFT", // DRAFT, IN_PROGRESS, COMPLETED, CANCELLED
    val scopeSummary: String = "",
    val notes: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val approvedBy: String? = null,
    val duplicateScanMode: String = "INCREMENT", // INCREMENT, PROMPT, REPLACE
    val totalItemsCount: Int = 0,
    val countedItemsCount: Int = 0,
    val discrepancyItemsCount: Int = 0,
    val totalShortageValueCents: Long = 0L,
    val totalSurplusValueCents: Long = 0L,
    val netDiscrepancyValueCents: Long = 0L
)

/**
 * Individual product line inside a stocktaking session.
 * Captures both the initial session snapshot (`expectedQuantity`) and the live system stock
 * at count time (`systemStockAtCount`) so concurrent sales/purchases never cause false shortages.
 */
@Entity(
    tableName = "inventory_items",
    foreignKeys = [
        ForeignKey(
            entity = InventoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["inventoryId", "productId"], unique = true),
        Index(value = ["inventoryId", "status"]),
        Index(value = ["inventoryId", "barcode"]),
        Index(value = ["productId"])
    ]
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val inventoryId: Long,
    val productId: Long,
    val productName: String,
    val barcode: String = "",
    val sku: String = "",
    val categoryId: Long = 0L,
    val categoryName: String = "",
    val unit: String = "",
    val imageUri: String? = null,
    val expectedQuantity: Double = 0.0,
    val systemStockAtCount: Double? = null,
    val countedQuantity: Double? = null,
    val difference: Double = 0.0,
    val unitCostCents: Long = 0L,
    val unitSellPriceCents: Long = 0L,
    val discrepancyValueCents: Long = 0L,
    val status: String = "UNCOUNTED", // UNCOUNTED, MATCHING, SHORTAGE, SURPLUS
    val notes: String = "",
    val countedAt: Long? = null,
    val countedBy: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Effective expected baseline used for discrepancy comparison (reconciled live stock at count time when counted).
     */
    val effectiveExpectedQuantity: Double
        get() = systemStockAtCount ?: expectedQuantity
}

/**
 * Immutable audit record for every controlled stock adjustment applied when an inventory is approved.
 * The unique index `(inventoryId, productId)` enforces DB-level idempotency and prevents duplicate application.
 */
@Entity(
    tableName = "inventory_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = InventoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["inventoryId", "productId"], unique = true),
        Index(value = ["productId"]),
        Index(value = ["createdAt"])
    ]
)
data class InventoryAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val inventoryId: Long,
    val productId: Long,
    val productName: String,
    val barcode: String = "",
    val unit: String = "",
    val snapshotExpectedQuantity: Double = 0.0,
    val systemStockAtCount: Double = 0.0,
    val countedQuantity: Double = 0.0,
    val previousQuantity: Double = 0.0,
    val adjustedQuantity: Double = 0.0,
    val difference: Double = 0.0,
    val concurrentDeltaAfterCount: Double = 0.0,
    val unitCostCents: Long = 0L,
    val financialImpactCents: Long = 0L,
    val reason: String = "",
    val approvedBy: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Monotonic single-row sequence counter guaranteeing unique offline inventory references (INV-YYYYMMDD-XXXX).
 */
@Entity(tableName = "inventory_sequence")
data class InventorySequenceEntity(
    @PrimaryKey val id: Int = 1,
    val lastInventoryNumber: Long = 0L
)

/**
 * Room SQL projection for supplier-to-product mapping derived from non-voided purchase invoices.
 */
data class SupplierProductLinkRecord(
    val supplierId: Long,
    val supplierName: String,
    val productId: Long,
    val lastPurchasedAt: Long
)

/**
 * Room SQL projection for recent product sales velocity used in slow-moving stock monitoring.
 */
data class ProductSalesVelocityRecord(
    val productId: Long,
    val totalSoldQty: Double,
    val lastSoldAt: Long?
)
