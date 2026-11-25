package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StocktakingDao {

    // --- SEQUENCE ---
    @Query("SELECT lastInventoryNumber FROM inventory_sequence WHERE id = 1 LIMIT 1")
    suspend fun getLastInventoryNumber(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setInventorySequence(sequence: InventorySequenceEntity)

    // --- INVENTORIES ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventory(inventory: InventoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoriesBatch(inventories: List<InventoryEntity>)

    @Update
    suspend fun updateInventory(inventory: InventoryEntity)

    @Query("SELECT * FROM inventories ORDER BY createdAt DESC, id DESC")
    fun getAllInventoriesFlow(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventories ORDER BY createdAt DESC, id DESC")
    suspend fun getAllInventoriesList(): List<InventoryEntity>

    @Query("SELECT * FROM inventories WHERE status IN ('DRAFT', 'IN_PROGRESS') ORDER BY updatedAt DESC, id DESC")
    fun getActiveInventoriesFlow(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventories WHERE status = 'COMPLETED' ORDER BY completedAt DESC, id DESC")
    fun getCompletedInventoriesFlow(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventories WHERE status = 'COMPLETED' ORDER BY completedAt DESC, id DESC")
    suspend fun getCompletedInventoriesList(): List<InventoryEntity>

    @Query("SELECT * FROM inventories WHERE id = :inventoryId LIMIT 1")
    fun getInventoryByIdFlow(inventoryId: Long): Flow<InventoryEntity?>

    @Query("SELECT * FROM inventories WHERE id = :inventoryId LIMIT 1")
    suspend fun getInventoryById(inventoryId: Long): InventoryEntity?

    @Query("SELECT * FROM inventories WHERE reference = :reference LIMIT 1")
    suspend fun getInventoryByReference(reference: String): InventoryEntity?

    @Query("SELECT COUNT(*) FROM inventories WHERE status IN ('DRAFT', 'IN_PROGRESS')")
    fun getActiveInventoriesCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM inventories WHERE status = 'COMPLETED'")
    fun getCompletedInventoriesCountFlow(): Flow<Int>

    @Query("DELETE FROM inventories WHERE id = :inventoryId")
    suspend fun deleteInventoryById(inventoryId: Long)

    @Query("DELETE FROM inventories")
    suspend fun clearAllInventories()

    // --- INVENTORY ITEMS ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventoryItem(item: InventoryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItemsBatch(items: List<InventoryItemEntity>)

    @Update
    suspend fun updateInventoryItem(item: InventoryItemEntity)

    @Query("SELECT * FROM inventory_items WHERE inventoryId = :inventoryId ORDER BY productName ASC, id ASC")
    fun getItemsForInventoryFlow(inventoryId: Long): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE inventoryId = :inventoryId ORDER BY productName ASC, id ASC")
    suspend fun getItemsForInventoryList(inventoryId: Long): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items ORDER BY id ASC")
    suspend fun getAllInventoryItemsList(): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items WHERE inventoryId = :inventoryId AND productId = :productId LIMIT 1")
    suspend fun getInventoryItem(inventoryId: Long, productId: Long): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE inventoryId = :inventoryId AND barcode = :barcode LIMIT 1")
    suspend fun getInventoryItemByBarcode(inventoryId: Long, barcode: String): InventoryItemEntity?

    @Query("DELETE FROM inventory_items WHERE inventoryId = :inventoryId AND productId = :productId")
    suspend fun deleteInventoryItem(inventoryId: Long, productId: Long)

    @Query("DELETE FROM inventory_items WHERE inventoryId = :inventoryId")
    suspend fun deleteItemsForInventory(inventoryId: Long)

    @Query("DELETE FROM inventory_items")
    suspend fun clearAllInventoryItems()

    // --- INVENTORY ADJUSTMENTS ---
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAdjustment(adjustment: InventoryAdjustmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustmentsBatch(adjustments: List<InventoryAdjustmentEntity>)

    @Query("SELECT * FROM inventory_adjustments ORDER BY createdAt DESC, id DESC")
    fun getAllAdjustmentsFlow(): Flow<List<InventoryAdjustmentEntity>>

    @Query("SELECT * FROM inventory_adjustments ORDER BY createdAt DESC, id DESC")
    suspend fun getAllAdjustmentsList(): List<InventoryAdjustmentEntity>

    @Query("SELECT * FROM inventory_adjustments WHERE inventoryId = :inventoryId ORDER BY createdAt DESC, id DESC")
    fun getAdjustmentsForInventoryFlow(inventoryId: Long): Flow<List<InventoryAdjustmentEntity>>

    @Query("SELECT * FROM inventory_adjustments WHERE inventoryId = :inventoryId ORDER BY createdAt DESC, id DESC")
    suspend fun getAdjustmentsForInventoryList(inventoryId: Long): List<InventoryAdjustmentEntity>

    @Query("SELECT * FROM inventory_adjustments WHERE productId = :productId ORDER BY createdAt DESC, id DESC")
    fun getAdjustmentsForProductFlow(productId: Long): Flow<List<InventoryAdjustmentEntity>>

    @Query("SELECT COUNT(*) FROM inventory_adjustments WHERE inventoryId = :inventoryId")
    suspend fun getAdjustmentsCountForInventory(inventoryId: Long): Int

    @Query("SELECT COUNT(*) FROM inventory_adjustments")
    fun getTotalAdjustmentsCountFlow(): Flow<Int>

    @Query("DELETE FROM inventory_adjustments WHERE inventoryId = :inventoryId")
    suspend fun deleteAdjustmentsForInventory(inventoryId: Long)

    @Query("DELETE FROM inventory_adjustments")
    suspend fun clearAllAdjustments()

    // --- ANALYTICS, SUPPLIER LINKS & VELOCITY PROJECTIONS ---
    @Query(
        """
        SELECT p.supplierId AS supplierId,
               p.supplierName AS supplierName,
               pi.productId AS productId,
               MAX(p.timestamp) AS lastPurchasedAt
        FROM purchase_items pi
        INNER JOIN purchases p ON p.id = pi.purchaseId
        WHERE p.isVoided = 0 AND p.supplierId > 0
        GROUP BY p.supplierId, pi.productId
        """
    )
    fun getSupplierProductLinksFlow(): Flow<List<SupplierProductLinkRecord>>

    @Query(
        """
        SELECT DISTINCT pi.productId
        FROM purchase_items pi
        INNER JOIN purchases p ON p.id = pi.purchaseId
        WHERE p.isVoided = 0 AND p.supplierId IN (:supplierIds)
        """
    )
    suspend fun getProductIdsBySuppliers(supplierIds: List<Long>): List<Long>

    @Query(
        """
        SELECT si.productId AS productId,
               COALESCE(SUM(CASE WHEN s.timestamp >= :sinceTimestamp THEN si.quantity ELSE 0.0 END), 0.0) AS totalSoldQty,
               MAX(s.timestamp) AS lastSoldAt
        FROM sale_items si
        INNER JOIN sales s ON s.id = si.saleId
        WHERE s.isVoided = 0 AND si.quantity > 0
        GROUP BY si.productId
        """
    )
    fun getProductSalesVelocityFlow(sinceTimestamp: Long): Flow<List<ProductSalesVelocityRecord>>
}
