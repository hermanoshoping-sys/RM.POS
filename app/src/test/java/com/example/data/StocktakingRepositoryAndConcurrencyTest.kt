package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.domain.stocktaking.BarcodeScanOutcome
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.InventoryItemStatus
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import com.example.domain.stocktaking.StockAlertType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StocktakingRepositoryAndConcurrencyTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: PosRepository

    private val managerUser = UserEntity(
        id = 1L,
        username = "manager",
        passwordHash = "hash",
        role = UserRole.MANAGER.name,
        canManageInventory = true,
        canSell = true,
        canPurchase = true,
        canReports = true
    )

    private val inventoryClerkUser = UserEntity(
        id = 2L,
        username = "clerk",
        passwordHash = "hash",
        role = UserRole.CASHIER.name,
        canManageInventory = true,
        canSell = true,
        canPurchase = false,
        canReports = false
    )

    private val unauthorizedCashier = UserEntity(
        id = 3L,
        username = "cashier_only",
        passwordHash = "hash",
        role = UserRole.CASHIER.name,
        canManageInventory = false,
        canSell = true
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PosRepository(db.appDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedCatalog(): Triple<Long, Long, Long> {
        val catDrinksId = db.appDao().insertCategory(CategoryEntity(name = "مشروبات"))
        val catFoodId = db.appDao().insertCategory(CategoryEntity(name = "مواد غذائية"))

        val p1 = db.appDao().insertProduct(
            ProductEntity(
                barcode = "6110001",
                name = "عصير برتقال",
                categoryId = catDrinksId,
                categoryName = "مشروبات",
                unit = "قطعة",
                purchasePrice = 1000L, // 10.00
                sellPrice = 1500L,     // 15.00
                currentStock = 10.0,
                minStock = 3.0,
                isActive = true
            )
        )
        val p2 = db.appDao().insertProduct(
            ProductEntity(
                barcode = "6110002",
                name = "زيت زيتون",
                categoryId = catFoodId,
                categoryName = "مواد غذائية",
                unit = "لتر",
                purchasePrice = 5000L, // 50.00
                sellPrice = 7000L,     // 70.00
                currentStock = 4.5,
                minStock = 5.0,        // low stock
                isActive = true
            )
        )
        val p3 = db.appDao().insertProduct(
            ProductEntity(
                barcode = "6110003",
                name = "سكر 1كغ",
                categoryId = catFoodId,
                categoryName = "مواد غذائية",
                unit = "كغ",
                purchasePrice = 600L,
                sellPrice = 800L,
                currentStock = 0.0,    // out of stock
                minStock = 2.0,
                isActive = true
            )
        )
        return Triple(p1, p2, p3)
    }

    @Test
    fun `concurrent sales before and after count are reconciled accurately without overwriting stock`() = runBlocking {
        val (p1, _, _) = seedCatalog()
        // Initial stock of p1 = 10.0, purchasePrice = 1000c (10.00), sellPrice = 1500c (15.00)

        // 1. Start general inventory -> snapshot expectedQuantity = 10.0
        val session = repo.createInventorySession(
            type = InventoryType.GENERAL,
            title = "جرد شهري",
            currentUser = inventoryClerkUser
        )
        assertTrue(session.reference.startsWith("INV-"))
        assertEquals(InventoryStatus.IN_PROGRESS.code, session.status)

        // 2. BEFORE clerk counts p1, cashier sells 2 units -> live stock becomes 8.0
        repo.recordSaleInvoice(
            sale = SaleEntity(
                invoiceNumber = "0001",
                timestamp = System.currentTimeMillis(),
                cashierName = "cashier",
                totalAmount = 3000L,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 3000L,
                changeAmount = 0L,
                paymentMethod = "CASH"
            ),
            items = listOf(
                SaleItemEntity(
                    saleId = 0L,
                    productId = p1,
                    productName = "عصير برتقال",
                    quantity = 2.0,
                    unitPrice = 1500L,
                    unitCost = 1000L,
                    totalLinePrice = 3000L
                )
            ),
            currentUser = managerUser
        )
        assertEquals(8.0, db.appDao().getProductById(p1)!!.currentStock, 1e-6)

        // 3. Clerk physically counts 7.0 units on shelf (true physical shortage = 7 - 8 = -1, NOT -3!)
        val countedItem = repo.recordInventoryItemCount(
            inventoryId = session.id,
            productId = p1,
            countedQuantity = 7.0,
            notes = "علبة تالفة",
            currentUser = inventoryClerkUser
        )
        assertEquals(10.0, countedItem.expectedQuantity, 1e-6)
        assertEquals(8.0, countedItem.systemStockAtCount!!, 1e-6)
        assertEquals(-1.0, countedItem.difference, 1e-6)
        assertEquals(InventoryItemStatus.SHORTAGE.code, countedItem.status)
        // Strictly valued at purchasePrice (1000c), NOT sellPrice (1500c)
        assertEquals(-1000L, countedItem.discrepancyValueCents)
        // Counting MUST NOT modify product stock!
        assertEquals(8.0, db.appDao().getProductById(p1)!!.currentStock, 1e-6)

        // 4. AFTER count and BEFORE manager approval, cashier sells 3 more units AND a purchase adds 5 units
        // Live stock: 8 - 3 + 5 = 10.0; true shelf quantity: 7 - 3 + 5 = 9.0
        repo.recordSaleInvoice(
            sale = SaleEntity(
                invoiceNumber = "0002",
                timestamp = System.currentTimeMillis(),
                cashierName = "cashier",
                totalAmount = 4500L,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 4500L,
                changeAmount = 0L,
                paymentMethod = "CASH"
            ),
            items = listOf(
                SaleItemEntity(
                    saleId = 0L,
                    productId = p1,
                    productName = "عصير برتقال",
                    quantity = 3.0,
                    unitPrice = 1500L,
                    unitCost = 1000L,
                    totalLinePrice = 4500L
                )
            ),
            currentUser = managerUser
        )
        val supId = db.appDao().insertSupplier(SupplierEntity(name = "مورد العصائر", phone = "0600000001", debtBalance = 0L))
        repo.recordPurchaseInvoice(
            purchase = PurchaseEntity(
                timestamp = System.currentTimeMillis(),
                supplierId = supId,
                supplierName = "مورد العصائر",
                totalAmount = 5000L,
                paidAmount = 5000L
            ),
            items = listOf(
                PurchaseItemEntity(
                    purchaseId = 0L,
                    productId = p1,
                    productName = "عصير برتقال",
                    quantity = 5.0,
                    unitPrice = 1000L
                )
            ),
            currentUser = managerUser
        )
        assertEquals(10.0, db.appDao().getProductById(p1)!!.currentStock, 1e-6)

        // 5. Preview shows exact reconciliation deltas
        val preview = repo.buildInventoryApprovalPreview(session.id)
        assertEquals(1, preview.itemsToAdjust.size)
        val p1Preview = preview.itemsToAdjust.first()
        assertEquals(-2.0, p1Preview.preCountMovementDelta, 1e-6)
        assertEquals(2.0, p1Preview.postCountMovementDelta, 1e-6) // -3 + 5 = +2
        assertEquals(10.0, p1Preview.liveSystemStock, 1e-6)
        assertEquals(9.0, p1Preview.projectedAdjustedStock, 1e-6)
        assertTrue(p1Preview.hasConcurrentMovement)

        // 6. Cashier/clerk CANNOT approve adjustments -> SecurityException
        var clerkBlocked = false
        try {
            repo.approveAndApplyInventory(session.id, "محاولة غير مصرح بها", inventoryClerkUser)
        } catch (_: SecurityException) {
            clerkBlocked = true
        }
        assertTrue("Non-manager must be rejected when approving adjustments", clerkBlocked)
        assertEquals(10.0, db.appDao().getProductById(p1)!!.currentStock, 1e-6)

        // 7. Manager approves atomically -> stock becomes 10 + (-1) = 9.0!
        val completed = repo.approveAndApplyInventory(session.id, "اعتماد الجرد الشهري", managerUser)
        assertEquals(InventoryStatus.COMPLETED.code, completed.status)
        assertEquals("manager", completed.approvedBy)
        assertNotNull(completed.completedAt)
        assertEquals(9.0, db.appDao().getProductById(p1)!!.currentStock, 1e-6)

        // 8. Verify adjustment audit row & stock movement row & audit log
        val adjustments = db.stocktakingDao().getAdjustmentsForInventoryList(session.id)
        assertEquals(1, adjustments.size)
        val adj = adjustments.first()
        assertEquals(10.0, adj.previousQuantity, 1e-6)
        assertEquals(9.0, adj.adjustedQuantity, 1e-6)
        assertEquals(-1.0, adj.difference, 1e-6)
        assertEquals(2.0, adj.concurrentDeltaAfterCount, 1e-6)
        assertEquals(-1000L, adj.financialImpactCents)

        val movements = db.appDao().getStockMovementsByReference("INVENTORY", session.id)
        assertEquals(1, movements.size)
        assertEquals("INVENTORY_ADJUSTMENT", movements.first().type)
        assertEquals(-1.0, movements.first().quantity, 1e-6)

        val auditLogs = db.appDao().getAllAuditLogs().first()
        assertTrue(auditLogs.any { it.actionType == "INVENTORY_ADJUSTMENT_APPROVE" && it.isCritical })

        // 9. Immutability & idempotency: re-approving or editing completed inventory MUST fail
        var duplicateBlocked = false
        try {
            repo.approveAndApplyInventory(session.id, "محاولة تكرار", managerUser)
        } catch (_: IllegalStateException) {
            duplicateBlocked = true
        }
        assertTrue("Duplicate adjustment approval must be prevented", duplicateBlocked)
        assertEquals(9.0, db.appDao().getProductById(p1)!!.currentStock, 1e-6)

        var editBlocked = false
        try {
            repo.recordInventoryItemCount(session.id, p1, 5.0, currentUser = managerUser)
        } catch (_: IllegalStateException) {
            editBlocked = true
        }
        assertTrue("Completed inventory must be immutable", editBlocked)
    }

    @Test
    fun `barcode scan supports increment prompt replace not-in-session and decimal counts`() = runBlocking {
        val (p1, p2, _) = seedCatalog()
        // Partial inventory containing only p1
        val session = repo.createInventorySession(
            type = InventoryType.PARTIAL_PRODUCTS,
            productIds = listOf(p1),
            duplicateScanMode = DuplicateScanMode.INCREMENT,
            currentUser = inventoryClerkUser
        )

        // First scan of p1 -> sets count to 1.0
        val scan1 = repo.handleInventoryBarcodeScan(session.id, "6110001", incrementBy = 1.0, currentUser = inventoryClerkUser)
        assertTrue(scan1 is BarcodeScanOutcome.Updated)
        assertEquals(1.0, (scan1 as BarcodeScanOutcome.Updated).newCountedQuantity, 1e-6)

        // Second scan in INCREMENT mode -> becomes 2.5
        val scan2 = repo.handleInventoryBarcodeScan(session.id, "6110001", incrementBy = 1.5, currentUser = inventoryClerkUser)
        assertTrue(scan2 is BarcodeScanOutcome.Updated)
        assertEquals(2.5, (scan2 as BarcodeScanOutcome.Updated).newCountedQuantity, 1e-6)

        // Switch to PROMPT mode -> returns PromptDuplicate without modifying count
        repo.updateInventoryDuplicateScanMode(session.id, DuplicateScanMode.PROMPT, inventoryClerkUser)
        val scan3 = repo.handleInventoryBarcodeScan(session.id, "6110001", incrementBy = 1.0, currentUser = inventoryClerkUser)
        assertTrue(scan3 is BarcodeScanOutcome.PromptDuplicate)
        assertEquals(2.5, (scan3 as BarcodeScanOutcome.PromptDuplicate).existingCountedQuantity, 1e-6)

        // Override mode REPLACE -> replaces count with 4.0
        val scan4 = repo.handleInventoryBarcodeScan(
            session.id,
            "6110001",
            incrementBy = 4.0,
            overrideMode = DuplicateScanMode.REPLACE,
            currentUser = inventoryClerkUser
        )
        assertTrue(scan4 is BarcodeScanOutcome.Updated)
        assertEquals(4.0, (scan4 as BarcodeScanOutcome.Updated).newCountedQuantity, 1e-6)

        // Scan product in catalog but NOT in partial session -> NotInSession
        val notInSession = repo.handleInventoryBarcodeScan(session.id, "6110002", currentUser = inventoryClerkUser)
        assertTrue(notInSession is BarcodeScanOutcome.NotInSession)
        assertEquals(p2, (notInSession as BarcodeScanOutcome.NotInSession).catalogProductId)

        // Scan unknown barcode -> ProductNotFound
        val unknown = repo.handleInventoryBarcodeScan(session.id, "99999999", currentUser = inventoryClerkUser)
        assertTrue(unknown is BarcodeScanOutcome.ProductNotFound)
    }

    @Test
    fun `partial inventory by supplier valuation monitoring unique references and backup restore work end to end`() = runBlocking {
        val (p1, p2, _) = seedCatalog()
        val supId = db.appDao().insertSupplier(SupplierEntity(name = "مورد الزيوت", phone = "0600000002", debtBalance = 0L))
        repo.recordPurchaseInvoice(
            purchase = PurchaseEntity(
                timestamp = System.currentTimeMillis(),
                supplierId = supId,
                supplierName = "مورد الزيوت",
                totalAmount = 5000L,
                paidAmount = 5000L
            ),
            items = listOf(
                PurchaseItemEntity(purchaseId = 0L, productId = p2, productName = "زيت زيتون", quantity = 1.0, unitPrice = 5000L)
            ),
            currentUser = managerUser
        )
        // p2 stock is now 5.5

        // Supplier partial inventory includes only p2
        val supplierInv = repo.createInventorySession(
            type = InventoryType.PARTIAL_SUPPLIER,
            supplierIds = listOf(supId),
            currentUser = managerUser
        )
        val generalInv = repo.createInventorySession(
            type = InventoryType.GENERAL,
            currentUser = managerUser
        )
        assertNotEquals(supplierInv.reference, generalInv.reference)
        assertEquals(1, db.stocktakingDao().getItemsForInventoryList(supplierInv.id).size)

        // Unauthorized cashier cannot create inventory
        var denied = false
        try {
            repo.createInventorySession(type = InventoryType.GENERAL, currentUser = unauthorizedCashier)
        } catch (_: SecurityException) {
            denied = true
        }
        assertTrue(denied)

        // Cancel generalInv without touching stock
        repo.recordInventoryItemCount(generalInv.id, p1, 1.0, currentUser = managerUser)
        val cancelled = repo.cancelInventorySession(generalInv.id, "إلغاء تجريبي", managerUser)
        assertEquals(InventoryStatus.CANCELLED.code, cancelled.status)
        assertEquals(10.0, db.appDao().getProductById(p1)!!.currentStock, 1e-6)

        // Complete supplierInv with surplus (+1.5 -> 7.0)
        repo.recordInventoryItemCount(supplierInv.id, p2, 7.0, currentUser = managerUser)
        repo.approveAndApplyInventory(supplierInv.id, "تأكيد مورد", managerUser)
        assertEquals(7.0, db.appDao().getProductById(p2)!!.currentStock, 1e-6)

        // Verify dashboard stats, category valuation, supplier valuation, and monitoring
        val stats = repo.getInventoryDashboardStatsFlow().first()
        assertEquals(3, stats.totalActiveProducts)
        assertEquals(1, stats.completedInventoriesCount)
        assertEquals(1, stats.outOfStockCount) // p3 has 0 stock
        assertEquals(1, stats.totalAdjustmentsCount)
        assertTrue(stats.totalSellValueCents > stats.totalPurchaseValueCents)

        val supValuations = repo.getSupplierValuationsFlow().first()
        assertEquals(1, supValuations.size)
        assertEquals(supId, supValuations.first().supplierId)
        assertEquals(35000L, supValuations.first().purchaseValueCents) // 7.0 * 5000c

        val monitoring = repo.getStockMonitoringFlow(slowMovingDays = 30).first()
        assertTrue(monitoring.any { it.alertType == StockAlertType.OUT_OF_STOCK })
        assertTrue(monitoring.any { it.alertType == StockAlertType.SLOW_MOVING })

        // Backup & restore roundtrip preserves inventories, items, and adjustments
        val json = DatabaseBackupManager.createBackupJson(db, "test@pos.ma", "0600000000")
        val targetDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val restoreResult = DatabaseBackupManager.restoreDatabaseFromJson(targetDb, json, context)
            assertTrue("Restore must succeed: ${restoreResult.message}", restoreResult.success)
            assertEquals(2, targetDb.stocktakingDao().getAllInventoriesList().size)
            assertEquals(1, targetDb.stocktakingDao().getAllAdjustmentsList().size)
            assertEquals(2L, targetDb.stocktakingDao().getLastInventoryNumber())
        } finally {
            targetDb.close()
        }
    }
}
