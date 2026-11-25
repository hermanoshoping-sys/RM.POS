package com.example.data

import com.example.domain.stocktaking.BarcodeScanOutcome
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.InventoryApprovalPreview
import com.example.domain.stocktaking.InventoryApprovalPreviewItem
import com.example.domain.stocktaking.InventoryComparisonSummary
import com.example.domain.stocktaking.InventoryDashboardStats
import com.example.domain.stocktaking.InventoryItemStatus
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import com.example.domain.stocktaking.StockAlertType
import com.example.domain.stocktaking.StockMonitoringItem
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.domain.stocktaking.CategoryValuationRow
import com.example.domain.stocktaking.SupplierValuationRow
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.abs

/**
 * Transactional domain operations for physical stocktaking (`Inventaire`),
 * concurrent stock movement reconciliation, controlled manager-approved adjustments,
 * valuation analytics, and stock monitoring.
 */
internal class RepositoryStocktakingOperations(
    private val appDao: AppDao,
    private val stocktakingDao: StocktakingDao,
    private val runInTransaction: suspend (suspend () -> Any?) -> Any?,
    private val getStoreCurrency: suspend () -> String,
    private val recordAuditLog: suspend (String, String, String, String, String, String, Boolean) -> Long
) {
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> tx(block: suspend () -> T): T = runInTransaction { block() } as T

    // --- REACTIVE STREAMS ---
    val allInventories: Flow<List<InventoryEntity>> = stocktakingDao.getAllInventoriesFlow()
    val activeInventories: Flow<List<InventoryEntity>> = stocktakingDao.getActiveInventoriesFlow()
    val completedInventories: Flow<List<InventoryEntity>> = stocktakingDao.getCompletedInventoriesFlow()
    val allAdjustments: Flow<List<InventoryAdjustmentEntity>> = stocktakingDao.getAllAdjustmentsFlow()

    fun getInventoryByIdFlow(inventoryId: Long): Flow<InventoryEntity?> =
        stocktakingDao.getInventoryByIdFlow(inventoryId)

    fun getItemsForInventoryFlow(inventoryId: Long): Flow<List<InventoryItemEntity>> =
        stocktakingDao.getItemsForInventoryFlow(inventoryId)

    fun getAdjustmentsForInventoryFlow(inventoryId: Long): Flow<List<InventoryAdjustmentEntity>> =
        stocktakingDao.getAdjustmentsForInventoryFlow(inventoryId)

    fun getAdjustmentsForProductFlow(productId: Long): Flow<List<InventoryAdjustmentEntity>> =
        stocktakingDao.getAdjustmentsForProductFlow(productId)

    fun getDashboardStatsFlow(): Flow<InventoryDashboardStats> = combine(
        appDao.getAllProducts(),
        stocktakingDao.getAllInventoriesFlow(),
        stocktakingDao.getTotalAdjustmentsCountFlow()
    ) { products, inventories, totalAdjustments ->
        val activeProducts = products.filter { it.isActive }
        var totalUnits = 0.0
        var purchaseValueCents = 0L
        var sellValueCents = 0L
        var lowStock = 0
        var outOfStock = 0

        for (p in activeProducts) {
            totalUnits += p.currentStock.coerceAtLeast(0.0)
            val valMetrics = StocktakingCalculator.computeProductValuation(
                purchasePriceCents = p.purchasePrice,
                sellPriceCents = p.sellPrice,
                currentStock = p.currentStock
            )
            purchaseValueCents += valMetrics.purchaseValueCents
            sellValueCents += valMetrics.sellValueCents
            if (p.currentStock <= 0.0) {
                outOfStock++
            } else if (p.currentStock <= p.minStock) {
                lowStock++
            }
        }

        val activeCount = inventories.count {
            val st = InventoryStatus.fromCode(it.status)
            st == InventoryStatus.DRAFT || st == InventoryStatus.IN_PROGRESS
        }
        val completed = inventories
            .filter { InventoryStatus.fromCode(it.status) == InventoryStatus.COMPLETED }
            .sortedByDescending { it.completedAt ?: it.updatedAt }
        val latestCompleted = completed.firstOrNull()

        InventoryDashboardStats(
            totalActiveProducts = activeProducts.size,
            totalUnitsInStock = StocktakingCalculator.normalizeQuantity(totalUnits),
            totalPurchaseValueCents = purchaseValueCents,
            totalSellValueCents = sellValueCents,
            potentialGrossMarginCents = sellValueCents - purchaseValueCents,
            activeInventoriesCount = activeCount,
            completedInventoriesCount = completed.size,
            lastCompletedAt = latestCompleted?.completedAt,
            lastCompletedReference = latestCompleted?.reference,
            lowStockCount = lowStock,
            outOfStockCount = outOfStock,
            totalAdjustmentsCount = totalAdjustments,
            lastNetDiscrepancyCents = latestCompleted?.netDiscrepancyValueCents ?: 0L
        )
    }

    fun getCategoryValuationsFlow(): Flow<List<CategoryValuationRow>> = combine(
        appDao.getAllProducts(),
        appDao.getAllCategories()
    ) { products, categories ->
        val catNamesById = categories.associate { it.id to it.name }
        products
            .filter { it.isActive }
            .groupBy { it.categoryId }
            .map { (categoryId, catProducts) ->
                val resolvedName = catNamesById[categoryId]
                    ?: catProducts.firstOrNull { it.categoryName.isNotBlank() }?.categoryName
                    ?: ""
                var totalUnits = 0.0
                var purchaseValue = 0L
                var sellValue = 0L
                for (p in catProducts) {
                    totalUnits += p.currentStock.coerceAtLeast(0.0)
                    val m = StocktakingCalculator.computeProductValuation(p.purchasePrice, p.sellPrice, p.currentStock)
                    purchaseValue += m.purchaseValueCents
                    sellValue += m.sellValueCents
                }
                CategoryValuationRow(
                    categoryId = categoryId,
                    categoryName = resolvedName,
                    productCount = catProducts.size,
                    totalUnits = StocktakingCalculator.normalizeQuantity(totalUnits),
                    purchaseValueCents = purchaseValue,
                    sellValueCents = sellValue,
                    potentialMarginCents = sellValue - purchaseValue
                )
            }
            .sortedByDescending { it.purchaseValueCents }
    }

    fun getSupplierValuationsFlow(): Flow<List<SupplierValuationRow>> = combine(
        appDao.getAllProducts(),
        appDao.getAllSuppliers(),
        stocktakingDao.getSupplierProductLinksFlow()
    ) { products, suppliers, links ->
        val productsById = products.filter { it.isActive }.associateBy { it.id }
        val suppliersById = suppliers.associateBy { it.id }
        // Associate each product with its most recent supplier to avoid double-counting inventory across suppliers
        val latestSupplierPerProduct = links
            .groupBy { it.productId }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.lastPurchasedAt } }

        latestSupplierPerProduct.values
            .filterNotNull()
            .groupBy { it.supplierId }
            .mapNotNull { (supplierId, supplierLinks) ->
                val scopedProducts = supplierLinks.mapNotNull { productsById[it.productId] }
                if (scopedProducts.isEmpty()) return@mapNotNull null
                val name = suppliersById[supplierId]?.name
                    ?: supplierLinks.first().supplierName
                var totalUnits = 0.0
                var purchaseValue = 0L
                var sellValue = 0L
                for (p in scopedProducts) {
                    totalUnits += p.currentStock.coerceAtLeast(0.0)
                    val m = StocktakingCalculator.computeProductValuation(p.purchasePrice, p.sellPrice, p.currentStock)
                    purchaseValue += m.purchaseValueCents
                    sellValue += m.sellValueCents
                }
                SupplierValuationRow(
                    supplierId = supplierId,
                    supplierName = name,
                    productCount = scopedProducts.size,
                    totalUnits = StocktakingCalculator.normalizeQuantity(totalUnits),
                    purchaseValueCents = purchaseValue,
                    sellValueCents = sellValue,
                    potentialMarginCents = sellValue - purchaseValue
                )
            }
            .sortedByDescending { it.purchaseValueCents }
    }

    fun getStockMonitoringFlow(
        slowMovingDays: Int = 30,
        nowMillis: Long = System.currentTimeMillis()
    ): Flow<List<StockMonitoringItem>> {
        val windowStart = nowMillis - (slowMovingDays.coerceAtLeast(1) * 24L * 60L * 60L * 1000L)
        return combine(
            appDao.getAllProducts(),
            stocktakingDao.getProductSalesVelocityFlow(windowStart)
        ) { products, velocities ->
            val velocityByProduct = velocities.associateBy { it.productId }
            val result = mutableListOf<StockMonitoringItem>()
            for (p in products) {
                if (!p.isActive) continue
                val vel = velocityByProduct[p.id]
                val soldQty = vel?.totalSoldQty ?: 0.0
                val lastSold = vel?.lastSoldAt

                val alertType = when {
                    p.currentStock <= 0.0 -> StockAlertType.OUT_OF_STOCK
                    p.currentStock <= p.minStock -> StockAlertType.LOW_STOCK
                    p.currentStock > 0.0 && soldQty <= StocktakingCalculator.QUANTITY_EPSILON -> StockAlertType.SLOW_MOVING
                    else -> null
                }

                if (alertType != null) {
                    result.add(
                        StockMonitoringItem(
                            productId = p.id,
                            productName = p.name,
                            barcode = p.barcode,
                            categoryName = p.categoryName,
                            unit = p.unit,
                            currentStock = p.currentStock,
                            minStock = p.minStock,
                            purchasePriceCents = p.purchasePrice,
                            sellPriceCents = p.sellPrice,
                            soldQtyRecentWindow = StocktakingCalculator.normalizeQuantity(soldQty),
                            lastSoldAt = lastSold,
                            alertType = alertType
                        )
                    )
                }
            }
            result.sortedWith(
                compareBy<StockMonitoringItem> {
                    when (it.alertType) {
                        StockAlertType.OUT_OF_STOCK -> 0
                        StockAlertType.LOW_STOCK -> 1
                        StockAlertType.SLOW_MOVING -> 2
                    }
                }.thenBy { it.currentStock }.thenBy { it.productName.lowercase(Locale.ROOT) }
            )
        }
    }

    // --- SESSION CREATION & MANAGEMENT ---
    suspend fun createInventorySession(
        type: InventoryType,
        title: String = "",
        notes: String = "",
        categoryIds: List<Long> = emptyList(),
        productIds: List<Long> = emptyList(),
        supplierIds: List<Long> = emptyList(),
        duplicateScanMode: DuplicateScanMode = DuplicateScanMode.INCREMENT,
        startImmediately: Boolean = true,
        currentUser: UserEntity? = null
    ): InventoryEntity = tx {
        requireInventoryAccess(currentUser)

        val allActiveProducts = appDao.getAllProductsList().filter { it.isActive }
        val scopedProducts: List<ProductEntity> = when (type) {
            InventoryType.GENERAL -> allActiveProducts
            InventoryType.PARTIAL_CATEGORY -> {
                val catSet = categoryIds.toSet()
                allActiveProducts.filter { it.categoryId in catSet }
            }
            InventoryType.PARTIAL_PRODUCTS -> {
                val prodSet = productIds.toSet()
                allActiveProducts.filter { it.id in prodSet }
            }
            InventoryType.PARTIAL_SUPPLIER -> {
                if (supplierIds.isEmpty()) {
                    emptyList()
                } else {
                    val linkedIds = stocktakingDao.getProductIdsBySuppliers(supplierIds).toSet()
                    allActiveProducts.filter { it.id in linkedIds }
                }
            }
        }

        if (scopedProducts.isEmpty()) {
            throw IllegalArgumentException("لا توجد منتجات مطابقة لنطاق الجرد المحدد / Aucun produit trouvé pour le périmètre sélectionné")
        }

        val now = System.currentTimeMillis()
        val reference = generateNextUniqueReference(now)
        val scopeSummary = buildScopeSummary(type, categoryIds, supplierIds, scopedProducts.size)
        val initialStatus = if (startImmediately) InventoryStatus.IN_PROGRESS else InventoryStatus.DRAFT

        val entity = InventoryEntity(
            reference = reference,
            title = title.trim().ifBlank { reference },
            type = type.code,
            status = initialStatus.code,
            scopeSummary = scopeSummary,
            notes = notes.trim(),
            createdBy = currentUser?.username?.ifBlank { "Admin" } ?: "Admin",
            createdAt = now,
            updatedAt = now,
            duplicateScanMode = duplicateScanMode.code,
            totalItemsCount = scopedProducts.size,
            countedItemsCount = 0,
            discrepancyItemsCount = 0,
            totalShortageValueCents = 0L,
            totalSurplusValueCents = 0L,
            netDiscrepancyValueCents = 0L
        )

        val inventoryId = stocktakingDao.insertInventory(entity)
        val items = scopedProducts.map { prod ->
            InventoryItemEntity(
                inventoryId = inventoryId,
                productId = prod.id,
                productName = prod.name,
                barcode = prod.barcode,
                sku = prod.sku,
                categoryId = prod.categoryId,
                categoryName = prod.categoryName,
                unit = prod.unit,
                imageUri = prod.imageUri,
                expectedQuantity = StocktakingCalculator.normalizeQuantity(prod.currentStock),
                systemStockAtCount = null,
                countedQuantity = null,
                difference = 0.0,
                unitCostCents = prod.purchasePrice,
                unitSellPriceCents = prod.sellPrice,
                discrepancyValueCents = 0L,
                status = InventoryItemStatus.UNCOUNTED.code,
                notes = "",
                countedAt = null,
                countedBy = null,
                updatedAt = now
            )
        }
        stocktakingDao.insertInventoryItemsBatch(items)

        recordAuditLog(
            "INVENTORY_CREATE",
            "INVENTORY",
            reference,
            "إنشاء جلسة جرد $reference (${type.code}) بعدد ${scopedProducts.size} منتج",
            currentUser?.username ?: "Admin",
            currentUser?.roleName ?: "MANAGER",
            false
        )

        entity.copy(id = inventoryId)
    }

    suspend fun addProductToActiveInventory(
        inventoryId: Long,
        productId: Long,
        currentUser: UserEntity? = null
    ): InventoryItemEntity = tx {
        requireInventoryAccess(currentUser)
        val inventory = requireEditableInventory(inventoryId)
        val existing = stocktakingDao.getInventoryItem(inventoryId, productId)
        if (existing != null) return@tx existing

        val product = appDao.getProductById(productId)
            ?: throw IllegalArgumentException("المنتج غير موجود / Produit introuvable")
        val now = System.currentTimeMillis()
        val newItem = InventoryItemEntity(
            inventoryId = inventoryId,
            productId = product.id,
            productName = product.name,
            barcode = product.barcode,
            sku = product.sku,
            categoryId = product.categoryId,
            categoryName = product.categoryName,
            unit = product.unit,
            imageUri = product.imageUri,
            expectedQuantity = StocktakingCalculator.normalizeQuantity(product.currentStock),
            unitCostCents = product.purchasePrice,
            unitSellPriceCents = product.sellPrice,
            status = InventoryItemStatus.UNCOUNTED.code,
            updatedAt = now
        )
        val itemId = stocktakingDao.insertInventoryItem(newItem)
        recalculateAndPersistInventorySummary(inventory, now)
        newItem.copy(id = itemId)
    }

    suspend fun updateSessionStatus(
        inventoryId: Long,
        newStatus: InventoryStatus,
        currentUser: UserEntity? = null
    ): InventoryEntity = tx {
        requireInventoryAccess(currentUser)
        val inventory = requireEditableInventory(inventoryId)
        if (newStatus == InventoryStatus.COMPLETED || newStatus == InventoryStatus.CANCELLED) {
            throw IllegalArgumentException("استخدم دالة الاعتماد أو الإلغاء لإنهاء الجرد")
        }
        val updated = inventory.copy(status = newStatus.code, updatedAt = System.currentTimeMillis())
        stocktakingDao.updateInventory(updated)
        updated
    }

    suspend fun updateDuplicateScanMode(
        inventoryId: Long,
        mode: DuplicateScanMode,
        currentUser: UserEntity? = null
    ) = tx {
        requireInventoryAccess(currentUser)
        val inventory = requireEditableInventory(inventoryId)
        stocktakingDao.updateInventory(
            inventory.copy(duplicateScanMode = mode.code, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun updateInventoryNotes(
        inventoryId: Long,
        notes: String,
        currentUser: UserEntity? = null
    ) = tx {
        requireInventoryAccess(currentUser)
        val inventory = requireEditableInventory(inventoryId)
        stocktakingDao.updateInventory(
            inventory.copy(notes = notes.trim(), updatedAt = System.currentTimeMillis())
        )
    }

    /**
     * Updates inventory session metadata (title, notes, and duplicateScanMode for editable sessions).
     * Editing metadata of a locked (COMPLETED/CANCELLED) session is permitted for managers without
     * mutating historical item counts or applied stock adjustments.
     */
    suspend fun updateInventorySession(
        inventoryId: Long,
        title: String,
        notes: String,
        duplicateScanMode: DuplicateScanMode? = null,
        currentUser: UserEntity? = null
    ): InventoryEntity = tx {
        requireInventoryAccess(currentUser)
        val inventory = stocktakingDao.getInventoryById(inventoryId)
            ?: throw IllegalArgumentException("جلسة الجرد غير موجودة / Inventaire introuvable")
        val status = InventoryStatus.fromCode(inventory.status)
        if (!status.isEditable) {
            requireManagerAccess(currentUser)
        }

        val now = System.currentTimeMillis()
        val resolvedTitle = title.trim().ifBlank { inventory.reference }
        val resolvedNotes = notes.trim()
        val resolvedScanMode = if (status.isEditable && duplicateScanMode != null) {
            duplicateScanMode.code
        } else {
            inventory.duplicateScanMode
        }

        val updated = inventory.copy(
            title = resolvedTitle,
            notes = resolvedNotes,
            duplicateScanMode = resolvedScanMode,
            updatedAt = now
        )
        stocktakingDao.updateInventory(updated)

        recordAuditLog(
            "INVENTORY_UPDATE",
            "INVENTORY",
            inventory.reference,
            "تعديل بيانات الجرد ${inventory.reference}: العنوان='$resolvedTitle'",
            currentUser?.username ?: "Admin",
            currentUser?.roleName ?: "MANAGER",
            false
        )
        updated
    }

    /**
     * Atomically deletes an inventory session and its lines/adjustments.
     * Strictly restricted to managers.
     *
     * When deleting a COMPLETED session with [revertStockAdjustments] = true, every applied
     * [InventoryAdjustmentEntity] is reversed using a relative delta (`-difference`) so concurrent
     * post-approval sales/purchases are preserved, and compensating `INVENTORY_REVERSAL` stock
     * movements are recorded.
     */
    suspend fun deleteInventorySession(
        inventoryId: Long,
        revertStockAdjustments: Boolean = false,
        reason: String = "",
        currentUser: UserEntity?
    ): Int = tx {
        requireManagerAccess(currentUser)
        val inventory = stocktakingDao.getInventoryById(inventoryId)
            ?: throw IllegalArgumentException("جلسة الجرد غير موجودة / Inventaire introuvable")
        val status = InventoryStatus.fromCode(inventory.status)
        val now = System.currentTimeMillis()
        val cleanReason = reason.trim()
        var revertedCount = 0

        if (status == InventoryStatus.COMPLETED && revertStockAdjustments) {
            val adjustments = stocktakingDao.getAdjustmentsForInventoryList(inventoryId)
            for (adj in adjustments) {
                val reverseDelta = StocktakingCalculator.normalizeQuantity(-adj.difference)
                if (abs(reverseDelta) <= StocktakingCalculator.QUANTITY_EPSILON) continue
                val productExists = appDao.getProductById(adj.productId) != null
                if (!productExists) continue

                appDao.adjustProductStock(adj.productId, reverseDelta)
                appDao.insertStockMovement(
                    StockMovementEntity(
                        productId = adj.productId,
                        productName = adj.productName,
                        type = "INVENTORY_REVERSAL",
                        quantity = reverseDelta,
                        timestamp = now,
                        notes = "عكس تسوية الجرد المحذوف ${inventory.reference}: ${StocktakingCalculator.formatSignedQuantity(reverseDelta)}${if (cleanReason.isNotBlank()) " ($cleanReason)" else ""}",
                        referenceType = "INVENTORY",
                        referenceId = inventoryId
                    )
                )
                revertedCount++
            }
        }

        // Delete explicitly inside transaction (in addition to FK CASCADE) to guarantee zero orphan rows
        stocktakingDao.deleteAdjustmentsForInventory(inventoryId)
        stocktakingDao.deleteItemsForInventory(inventoryId)
        stocktakingDao.deleteInventoryById(inventoryId)

        val action = if (revertedCount > 0) "INVENTORY_DELETE_REVERT" else "INVENTORY_DELETE"
        val details = buildString {
            append("حذف جلسة الجرد ${inventory.reference} (الحالة: ${inventory.status})")
            if (revertedCount > 0) {
                append(" مع التراجع عن $revertedCount تسوية مخزون")
            }
            if (cleanReason.isNotBlank()) {
                append(" | السبب: $cleanReason")
            }
        }
        recordAuditLog(
            action,
            "INVENTORY",
            inventory.reference,
            details,
            currentUser?.username ?: "Admin",
            currentUser?.roleName ?: "MANAGER",
            true
        )
        revertedCount
    }

    // --- PHYSICAL COUNTING & BARCODE SCANNING (NEVER MUTATES products.currentStock) ---
    suspend fun recordItemCount(
        inventoryId: Long,
        productId: Long,
        countedQuantity: Double,
        notes: String? = null,
        currentUser: UserEntity? = null
    ): InventoryItemEntity = tx {
        requireInventoryAccess(currentUser)
        if (!countedQuantity.isFinite() || countedQuantity < 0.0) {
            throw IllegalArgumentException("الكمية الفعلية يجب أن تكون رقماً غير سالب / La quantité physique doit être positive ou nulle")
        }

        val inventory = requireEditableInventory(inventoryId)
        val item = stocktakingDao.getInventoryItem(inventoryId, productId)
            ?: throw IllegalArgumentException("المنتج غير مدرج في جلسة الجرد / Produit absent de cet inventaire")

        val liveProduct = appDao.getProductById(productId)
        val now = System.currentTimeMillis()
        val normalizedCounted = StocktakingCalculator.normalizeQuantity(countedQuantity)
        // Capture live system stock at count time so concurrent pre-count sales/purchases never cause false shortages.
        val systemBaseline = StocktakingCalculator.normalizeQuantity(
            liveProduct?.currentStock ?: item.effectiveExpectedQuantity
        )
        val diff = StocktakingCalculator.computeDifference(normalizedCounted, systemBaseline)
        val status = StocktakingCalculator.classifyItemStatus(normalizedCounted, systemBaseline)
        val costCents = liveProduct?.purchasePrice ?: item.unitCostCents
        val sellCents = liveProduct?.sellPrice ?: item.unitSellPriceCents
        val discrepancyValueCents = StocktakingCalculator.computeDiscrepancyValueCents(costCents, diff)

        val updatedItem = item.copy(
            systemStockAtCount = systemBaseline,
            countedQuantity = normalizedCounted,
            difference = diff,
            unitCostCents = costCents,
            unitSellPriceCents = sellCents,
            discrepancyValueCents = discrepancyValueCents,
            status = status.code,
            notes = notes?.trim() ?: item.notes,
            countedAt = now,
            countedBy = currentUser?.username?.ifBlank { "Admin" } ?: "Admin",
            updatedAt = now
        )
        stocktakingDao.updateInventoryItem(updatedItem)

        val activeInventory = if (InventoryStatus.fromCode(inventory.status) == InventoryStatus.DRAFT) {
            inventory.copy(status = InventoryStatus.IN_PROGRESS.code)
        } else {
            inventory
        }
        recalculateAndPersistInventorySummary(activeInventory, now)
        updatedItem
    }

    suspend fun clearItemCount(
        inventoryId: Long,
        productId: Long,
        currentUser: UserEntity? = null
    ): InventoryItemEntity = tx {
        requireInventoryAccess(currentUser)
        val inventory = requireEditableInventory(inventoryId)
        val item = stocktakingDao.getInventoryItem(inventoryId, productId)
            ?: throw IllegalArgumentException("المنتج غير مدرج في جلسة الجرد")
        val now = System.currentTimeMillis()
        val resetItem = item.copy(
            systemStockAtCount = null,
            countedQuantity = null,
            difference = 0.0,
            discrepancyValueCents = 0L,
            status = InventoryItemStatus.UNCOUNTED.code,
            countedAt = null,
            countedBy = null,
            updatedAt = now
        )
        stocktakingDao.updateInventoryItem(resetItem)
        recalculateAndPersistInventorySummary(inventory, now)
        resetItem
    }

    suspend fun handleBarcodeScan(
        inventoryId: Long,
        rawBarcode: String,
        incrementBy: Double = 1.0,
        overrideMode: DuplicateScanMode? = null,
        currentUser: UserEntity? = null
    ): BarcodeScanOutcome = tx {
        requireInventoryAccess(currentUser)
        val trimmedBarcode = rawBarcode.trim()
        if (trimmedBarcode.isEmpty()) return@tx BarcodeScanOutcome.ProductNotFound(trimmedBarcode)

        val inventory = stocktakingDao.getInventoryById(inventoryId)
            ?: throw IllegalArgumentException("جلسة الجرد غير موجودة")
        val status = InventoryStatus.fromCode(inventory.status)
        if (!status.isEditable) {
            return@tx BarcodeScanOutcome.SessionReadOnly(status)
        }

        val item = stocktakingDao.getInventoryItemByBarcode(inventoryId, trimmedBarcode)
        if (item == null) {
            val catalogProduct = appDao.getProductByBarcode(trimmedBarcode)
            return@tx if (catalogProduct != null) {
                BarcodeScanOutcome.NotInSession(
                    barcode = trimmedBarcode,
                    catalogProductId = catalogProduct.id,
                    catalogProductName = catalogProduct.name
                )
            } else {
                BarcodeScanOutcome.ProductNotFound(trimmedBarcode)
            }
        }

        val delta = StocktakingCalculator.normalizeQuantity(incrementBy.coerceAtLeast(0.001))
        val existingCount = item.countedQuantity
        val mode = overrideMode ?: DuplicateScanMode.fromCode(inventory.duplicateScanMode)

        if (existingCount == null) {
            val updated = recordItemCountInternal(inventory, item, delta, currentUser)
            return@tx BarcodeScanOutcome.Updated(updated, null, delta)
        }

        when (mode) {
            DuplicateScanMode.INCREMENT -> {
                val nextQty = StocktakingCalculator.normalizeQuantity(existingCount + delta)
                val updated = recordItemCountInternal(inventory, item, nextQty, currentUser)
                BarcodeScanOutcome.Updated(updated, existingCount, nextQty)
            }
            DuplicateScanMode.REPLACE -> {
                val updated = recordItemCountInternal(inventory, item, delta, currentUser)
                BarcodeScanOutcome.Updated(updated, existingCount, delta)
            }
            DuplicateScanMode.PROMPT -> {
                BarcodeScanOutcome.PromptDuplicate(
                    item = item,
                    existingCountedQuantity = existingCount,
                    incrementDelta = delta
                )
            }
        }
    }

    // --- APPROVAL PREVIEW & CONTROLLED ATOMIC ADJUSTMENT ---
    suspend fun buildApprovalPreview(inventoryId: Long): InventoryApprovalPreview {
        val inventory = stocktakingDao.getInventoryById(inventoryId)
            ?: throw IllegalArgumentException("جلسة الجرد غير موجودة")
        val items = stocktakingDao.getItemsForInventoryList(inventoryId)
        val productIds = items.map { it.productId }.distinct()
        val liveProductsMap = if (productIds.isNotEmpty()) {
            appDao.getProductsByIds(productIds).associateBy { it.id }
        } else {
            emptyMap()
        }

        val previewAdjustments = mutableListOf<InventoryApprovalPreviewItem>()
        var uncountedCount = 0
        var concurrentCount = 0
        var totalShortageCents = 0L
        var totalSurplusCents = 0L

        for (item in items) {
            if (item.countedQuantity == null) {
                uncountedCount++
                continue
            }
            val liveStock = StocktakingCalculator.normalizeQuantity(
                liveProductsMap[item.productId]?.currentStock ?: item.effectiveExpectedQuantity
            )
            val baselineAtCount = StocktakingCalculator.normalizeQuantity(item.effectiveExpectedQuantity)
            val preCountDelta = StocktakingCalculator.normalizeQuantity(baselineAtCount - item.expectedQuantity)
            val postCountDelta = StocktakingCalculator.normalizeQuantity(liveStock - baselineAtCount)
            val hasConcurrent = abs(preCountDelta) > StocktakingCalculator.QUANTITY_EPSILON ||
                abs(postCountDelta) > StocktakingCalculator.QUANTITY_EPSILON
            if (hasConcurrent) concurrentCount++

            val diff = StocktakingCalculator.computeDifference(item.countedQuantity, baselineAtCount)
            if (abs(diff) > StocktakingCalculator.QUANTITY_EPSILON) {
                val projected = StocktakingCalculator.normalizeQuantity(liveStock + diff)
                val impact = StocktakingCalculator.computeDiscrepancyValueCents(item.unitCostCents, diff)
                if (impact < 0L) totalShortageCents += abs(impact) else totalSurplusCents += impact
                previewAdjustments.add(
                    InventoryApprovalPreviewItem(
                        item = item,
                        liveSystemStock = liveStock,
                        preCountMovementDelta = preCountDelta,
                        postCountMovementDelta = postCountDelta,
                        projectedAdjustedStock = projected,
                        hasConcurrentMovement = hasConcurrent
                    )
                )
            }
        }

        return InventoryApprovalPreview(
            inventory = inventory,
            itemsToAdjust = previewAdjustments.sortedByDescending { abs(it.item.discrepancyValueCents) },
            uncountedItemsCount = uncountedCount,
            concurrentMovementItemsCount = concurrentCount,
            totalShortageValueCents = totalShortageCents,
            totalSurplusValueCents = totalSurplusCents,
            netDiscrepancyValueCents = totalSurplusCents - totalShortageCents
        )
    }

    /**
     * Atomically applies reconciled relative stock adjustments for all counted discrepancies,
     * locks the inventory as immutable `COMPLETED`, writes `inventory_adjustments`,
     * `stock_movements`, and a critical `audit_logs` record.
     *
     * Requires Manager permission (`currentUser.isManager == true`).
     */
    suspend fun approveAndApplyInventory(
        inventoryId: Long,
        reason: String = "",
        currentUser: UserEntity?
    ): InventoryEntity = tx {
        requireManagerAccess(currentUser)
        val inventory = stocktakingDao.getInventoryById(inventoryId)
            ?: throw IllegalArgumentException("جلسة الجرد غير موجودة / Inventaire introuvable")
        val status = InventoryStatus.fromCode(inventory.status)
        if (!status.isEditable) {
            throw IllegalStateException("لا يمكن اعتماد جرد مكتمل أو ملغى مسبقاً / Cet inventaire est déjà clôturé ou annulé")
        }

        val existingAdjustmentsCount = stocktakingDao.getAdjustmentsCountForInventory(inventoryId)
        if (existingAdjustmentsCount > 0) {
            throw IllegalStateException("تم تطبيق تسويات هذا الجرد مسبقاً / Les ajustements ont déjà été appliqués")
        }

        val items = stocktakingDao.getItemsForInventoryList(inventoryId)
        val countedItems = items.filter { it.countedQuantity != null }
        if (countedItems.isEmpty()) {
            throw IllegalStateException("يجب جرد منتج واحد على الأقل قبل الاعتماد / Veuillez compter au moins un produit avant de valider")
        }

        val now = System.currentTimeMillis()
        val approver = currentUser?.username?.ifBlank { "Admin" } ?: "Admin"
        val cleanReason = reason.trim().ifBlank { "تسوية الجرد الفعلي / Ajustement d'inventaire ${inventory.reference}" }
        var appliedCount = 0

        for (item in countedItems) {
            val countedQty = item.countedQuantity ?: continue
            val baselineAtCount = StocktakingCalculator.normalizeQuantity(item.effectiveExpectedQuantity)
            val diff = StocktakingCalculator.computeDifference(countedQty, baselineAtCount)
            if (abs(diff) <= StocktakingCalculator.QUANTITY_EPSILON) continue

            val liveProduct = appDao.getProductById(item.productId) ?: continue
            val liveStockBefore = StocktakingCalculator.normalizeQuantity(liveProduct.currentStock)
            val postCountDelta = StocktakingCalculator.normalizeQuantity(liveStockBefore - baselineAtCount)
            val adjustedStockAfter = StocktakingCalculator.normalizeQuantity(liveStockBefore + diff)
            val impactCents = StocktakingCalculator.computeDiscrepancyValueCents(item.unitCostCents, diff)

            // Apply relative delta (NOT blind overwrite) so post-count concurrent sales/purchases are preserved!
            appDao.adjustProductStock(item.productId, diff)

            stocktakingDao.insertAdjustment(
                InventoryAdjustmentEntity(
                    inventoryId = inventoryId,
                    productId = item.productId,
                    productName = item.productName,
                    barcode = item.barcode,
                    unit = item.unit,
                    snapshotExpectedQuantity = item.expectedQuantity,
                    systemStockAtCount = baselineAtCount,
                    countedQuantity = countedQty,
                    previousQuantity = liveStockBefore,
                    adjustedQuantity = adjustedStockAfter,
                    difference = diff,
                    concurrentDeltaAfterCount = postCountDelta,
                    unitCostCents = item.unitCostCents,
                    financialImpactCents = impactCents,
                    reason = cleanReason,
                    approvedBy = approver,
                    createdAt = now
                )
            )

            appDao.insertStockMovement(
                StockMovementEntity(
                    productId = item.productId,
                    productName = item.productName,
                    type = "INVENTORY_ADJUSTMENT",
                    quantity = diff,
                    timestamp = now,
                    notes = "تسوية جرد ${inventory.reference}: ${StocktakingCalculator.formatSignedQuantity(diff)} ($cleanReason)",
                    referenceType = "INVENTORY",
                    referenceId = inventoryId
                )
            )
            appliedCount++
        }

        val summary = StocktakingCalculator.computeSessionSummary(
            items.map {
                StocktakingCalculator.ItemSummaryInput(
                    countedQuantity = it.countedQuantity,
                    expectedQuantity = it.effectiveExpectedQuantity,
                    unitCostCents = it.unitCostCents
                )
            }
        )

        val completedInventory = inventory.copy(
            status = InventoryStatus.COMPLETED.code,
            completedAt = now,
            approvedBy = approver,
            updatedAt = now,
            totalItemsCount = summary.totalItemsCount,
            countedItemsCount = summary.countedItemsCount,
            discrepancyItemsCount = summary.discrepancyItemsCount,
            totalShortageValueCents = summary.totalShortageValueCents,
            totalSurplusValueCents = summary.totalSurplusValueCents,
            netDiscrepancyValueCents = summary.netDiscrepancyValueCents
        )
        stocktakingDao.updateInventory(completedInventory)

        val currency = getStoreCurrency()
        recordAuditLog(
            "INVENTORY_ADJUSTMENT_APPROVE",
            "INVENTORY",
            inventory.reference,
            "اعتماد الجرد ${inventory.reference}: تسوية $appliedCount منتج | العجز=${MoneyUtils.format(summary.totalShortageValueCents, currency)} | الفائض=${MoneyUtils.format(summary.totalSurplusValueCents, currency)} | الصافي=${MoneyUtils.format(summary.netDiscrepancyValueCents, currency)}",
            approver,
            currentUser?.roleName ?: "MANAGER",
            true
        )

        completedInventory
    }

    suspend fun cancelInventorySession(
        inventoryId: Long,
        reason: String = "",
        currentUser: UserEntity?
    ): InventoryEntity = tx {
        requireManagerAccess(currentUser)
        val inventory = stocktakingDao.getInventoryById(inventoryId)
            ?: throw IllegalArgumentException("جلسة الجرد غير موجودة")
        val status = InventoryStatus.fromCode(inventory.status)
        if (!status.isEditable) {
            throw IllegalStateException("لا يمكن إلغاء جرد مكتمل أو ملغى مسبقاً")
        }
        val now = System.currentTimeMillis()
        val updatedNotes = if (reason.isNotBlank()) {
            listOf(inventory.notes, reason.trim()).filter { it.isNotBlank() }.joinToString(" | ")
        } else {
            inventory.notes
        }
        val cancelled = inventory.copy(
            status = InventoryStatus.CANCELLED.code,
            notes = updatedNotes,
            updatedAt = now
        )
        stocktakingDao.updateInventory(cancelled)

        recordAuditLog(
            "INVENTORY_CANCEL",
            "INVENTORY",
            inventory.reference,
            "إلغاء جلسة الجرد ${inventory.reference}${if (reason.isNotBlank()) ": ${reason.trim()}" else ""}",
            currentUser?.username ?: "Admin",
            currentUser?.roleName ?: "MANAGER",
            true
        )
        cancelled
    }

    suspend fun getHistoricalComparison(inventoryId: Long): InventoryComparisonSummary? {
        val current = stocktakingDao.getInventoryById(inventoryId) ?: return null
        val completed = stocktakingDao.getCompletedInventoriesList()
            .filter { it.id != current.id && (it.completedAt ?: it.createdAt) <= (current.completedAt ?: current.createdAt) }
        val previous = completed.firstOrNull()

        val currentItems = stocktakingDao.getItemsForInventoryList(current.id)
        val currentSummary = StocktakingCalculator.computeSessionSummary(
            currentItems.map {
                StocktakingCalculator.ItemSummaryInput(it.countedQuantity, it.effectiveExpectedQuantity, it.unitCostCents)
            }
        )
        val prevSummary = previous?.let { prev ->
            val prevItems = stocktakingDao.getItemsForInventoryList(prev.id)
            StocktakingCalculator.computeSessionSummary(
                prevItems.map {
                    StocktakingCalculator.ItemSummaryInput(it.countedQuantity, it.effectiveExpectedQuantity, it.unitCostCents)
                }
            )
        }

        return InventoryComparisonSummary(
            currentInventory = current,
            previousInventory = previous,
            discrepancyCountDelta = if (previous != null) current.discrepancyItemsCount - previous.discrepancyItemsCount else 0,
            netValueDeltaCents = if (previous != null) current.netDiscrepancyValueCents - previous.netDiscrepancyValueCents else 0L,
            currentAccuracyPercent = currentSummary.accuracyPercent,
            previousAccuracyPercent = prevSummary?.accuracyPercent
        )
    }

    // --- INTERNAL HELPERS ---
    private suspend fun recordItemCountInternal(
        inventory: InventoryEntity,
        item: InventoryItemEntity,
        countedQuantity: Double,
        currentUser: UserEntity?
    ): InventoryItemEntity {
        val liveProduct = appDao.getProductById(item.productId)
        val now = System.currentTimeMillis()
        val normalizedCounted = StocktakingCalculator.normalizeQuantity(countedQuantity)
        val systemBaseline = StocktakingCalculator.normalizeQuantity(
            liveProduct?.currentStock ?: item.effectiveExpectedQuantity
        )
        val diff = StocktakingCalculator.computeDifference(normalizedCounted, systemBaseline)
        val status = StocktakingCalculator.classifyItemStatus(normalizedCounted, systemBaseline)
        val costCents = liveProduct?.purchasePrice ?: item.unitCostCents
        val sellCents = liveProduct?.sellPrice ?: item.unitSellPriceCents
        val discrepancyValueCents = StocktakingCalculator.computeDiscrepancyValueCents(costCents, diff)

        val updatedItem = item.copy(
            systemStockAtCount = systemBaseline,
            countedQuantity = normalizedCounted,
            difference = diff,
            unitCostCents = costCents,
            unitSellPriceCents = sellCents,
            discrepancyValueCents = discrepancyValueCents,
            status = status.code,
            countedAt = now,
            countedBy = currentUser?.username?.ifBlank { "Admin" } ?: "Admin",
            updatedAt = now
        )
        stocktakingDao.updateInventoryItem(updatedItem)
        val activeInventory = if (InventoryStatus.fromCode(inventory.status) == InventoryStatus.DRAFT) {
            inventory.copy(status = InventoryStatus.IN_PROGRESS.code)
        } else {
            inventory
        }
        recalculateAndPersistInventorySummary(activeInventory, now)
        return updatedItem
    }

    private suspend fun recalculateAndPersistInventorySummary(inventory: InventoryEntity, now: Long) {
        val items = stocktakingDao.getItemsForInventoryList(inventory.id)
        val summary = StocktakingCalculator.computeSessionSummary(
            items.map {
                StocktakingCalculator.ItemSummaryInput(
                    countedQuantity = it.countedQuantity,
                    expectedQuantity = it.effectiveExpectedQuantity,
                    unitCostCents = it.unitCostCents
                )
            }
        )
        stocktakingDao.updateInventory(
            inventory.copy(
                updatedAt = now,
                totalItemsCount = summary.totalItemsCount,
                countedItemsCount = summary.countedItemsCount,
                discrepancyItemsCount = summary.discrepancyItemsCount,
                totalShortageValueCents = summary.totalShortageValueCents,
                totalSurplusValueCents = summary.totalSurplusValueCents,
                netDiscrepancyValueCents = summary.netDiscrepancyValueCents
            )
        )
    }

    private suspend fun generateNextUniqueReference(nowMillis: Long): String {
        val dateStamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMillis))
        var nextNum = (stocktakingDao.getLastInventoryNumber() ?: 0L) + 1L
        var candidate = String.format(Locale.US, "INV-%s-%04d", dateStamp, nextNum)
        while (stocktakingDao.getInventoryByReference(candidate) != null) {
            nextNum++
            candidate = String.format(Locale.US, "INV-%s-%04d", dateStamp, nextNum)
        }
        stocktakingDao.setInventorySequence(InventorySequenceEntity(id = 1, lastInventoryNumber = nextNum))
        return candidate
    }

    private suspend fun buildScopeSummary(
        type: InventoryType,
        categoryIds: List<Long>,
        supplierIds: List<Long>,
        productCount: Int
    ): String {
        return when (type) {
            InventoryType.GENERAL -> "ALL ($productCount)"
            InventoryType.PARTIAL_CATEGORY -> {
                val cats = appDao.getAllCategoriesList().filter { it.id in categoryIds }.map { it.name }
                if (cats.isNotEmpty()) cats.joinToString(", ") else "$productCount"
            }
            InventoryType.PARTIAL_SUPPLIER -> {
                val sups = appDao.getAllSuppliersList().filter { it.id in supplierIds }.map { it.name }
                if (sups.isNotEmpty()) sups.joinToString(", ") else "$productCount"
            }
            InventoryType.PARTIAL_PRODUCTS -> "$productCount"
        }
    }

    private suspend fun requireEditableInventory(inventoryId: Long): InventoryEntity {
        val inventory = stocktakingDao.getInventoryById(inventoryId)
            ?: throw IllegalArgumentException("جلسة الجرد غير موجودة / Inventaire introuvable")
        if (!InventoryStatus.fromCode(inventory.status).isEditable) {
            throw IllegalStateException("جلسة الجرد مغلقة ولا يمكن تعديلها / Cet inventaire est verrouillé")
        }
        return inventory
    }

    private fun requireInventoryAccess(currentUser: UserEntity?) {
        if (currentUser == null || !currentUser.hasInventoryAccess()) {
            throw SecurityException("صلاحية الجرد والمخزون غير ممنوحة لهذا المستخدم (${currentUser?.username ?: "غير مصادق"})")
        }
    }

    private fun requireManagerAccess(currentUser: UserEntity?) {
        if (currentUser == null || !currentUser.isManager) {
            throw SecurityException("اعتماد تسويات المخزون وإلغاء الجرد مقتصران حصرياً على المدير / Réservé exclusivement au gérant")
        }
    }
}
