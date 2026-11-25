package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import com.example.domain.stocktaking.StocktakingReportBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StocktakingEditDeleteAndEndToEndTest {

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

    private suspend fun seedProducts(): Pair<Long, Long> {
        val catId = db.appDao().insertCategory(CategoryEntity(name = "مشروبات"))
        val p1 = db.appDao().insertProduct(
            ProductEntity(
                name = "عصير برتقال",
                barcode = "611001",
                categoryId = catId,
                categoryName = "مشروبات",
                unit = "قارورة",
                purchasePrice = 1000L, // 10.00
                sellPrice = 1500L,
                currentStock = 20.0,
                minStock = 5.0,
                isActive = true
            )
        )
        val p2 = db.appDao().insertProduct(
            ProductEntity(
                name = "ماء معدني",
                barcode = "611002",
                categoryId = catId,
                categoryName = "مشروبات",
                unit = "قارورة",
                purchasePrice = 400L, // 4.00
                sellPrice = 700L,
                currentStock = 10.0,
                minStock = 3.0,
                isActive = true
            )
        )
        return p1 to p2
    }

    @Test
    fun editActiveSession_updatesTitleNotesAndScanMode_andWritesAuditLog() = runBlocking {
        seedProducts()
        val session = repo.createInventorySession(
            type = InventoryType.GENERAL,
            title = "جرد أولي",
            notes = "ملاحظة قديمة",
            duplicateScanMode = DuplicateScanMode.INCREMENT,
            currentUser = inventoryClerkUser
        )

        val updated = repo.updateInventorySession(
            inventoryId = session.id,
            title = "جرد أسبوعي معدّل",
            notes = "ملاحظة محدثة",
            duplicateScanMode = DuplicateScanMode.PROMPT,
            currentUser = inventoryClerkUser
        )

        assertEquals("جرد أسبوعي معدّل", updated.title)
        assertEquals("ملاحظة محدثة", updated.notes)
        assertEquals(DuplicateScanMode.PROMPT.code, updated.duplicateScanMode)

        val reloaded = db.stocktakingDao().getInventoryById(session.id)
        assertNotNull(reloaded)
        assertEquals("جرد أسبوعي معدّل", reloaded!!.title)
        assertEquals(DuplicateScanMode.PROMPT.code, reloaded.duplicateScanMode)

        val logs = db.appDao().getAllAuditLogs().first()
        assertTrue(logs.any { it.actionType == "INVENTORY_UPDATE" && it.entityId == session.reference })
    }

    @Test
    fun editCompletedSession_managerUpdatesMetadataOnly_andNonManagerBlocked() = runBlocking {
        val (p1, _) = seedProducts()
        val session = repo.createInventorySession(
            type = InventoryType.GENERAL,
            title = "جرد شهري",
            duplicateScanMode = DuplicateScanMode.INCREMENT,
            currentUser = managerUser
        )
        repo.recordInventoryItemCount(session.id, p1, 18.0, currentUser = managerUser)
        repo.approveAndApplyInventory(session.id, reason = "اعتماد", currentUser = managerUser)

        // Non-manager cannot edit locked/completed session metadata
        try {
            repo.updateInventorySession(
                inventoryId = session.id,
                title = "تعديل غير مصرح",
                notes = "",
                duplicateScanMode = DuplicateScanMode.REPLACE,
                currentUser = inventoryClerkUser
            )
            fail("Expected SecurityException for non-manager editing completed inventory")
        } catch (_: SecurityException) {
            // expected
        }

        // Manager can update title & notes, but duplicateScanMode remains locked on completed sessions
        val updated = repo.updateInventorySession(
            inventoryId = session.id,
            title = "جرد شهر نوفمبر المعتمد",
            notes = "أرشفة نهائية",
            duplicateScanMode = DuplicateScanMode.REPLACE,
            currentUser = managerUser
        )
        assertEquals("جرد شهر نوفمبر المعتمد", updated.title)
        assertEquals("أرشفة نهائية", updated.notes)
        assertEquals(DuplicateScanMode.INCREMENT.code, updated.duplicateScanMode)
        assertEquals(18.0, db.appDao().getProductById(p1)!!.currentStock, 0.001)
    }

    @Test
    fun deleteActiveSession_cascadesItemsAndBlocksNonManager() = runBlocking {
        val (p1, _) = seedProducts()
        val session = repo.createInventorySession(
            type = InventoryType.GENERAL,
            title = "مسودة للحذف",
            currentUser = managerUser
        )
        repo.recordInventoryItemCount(session.id, p1, 15.0, currentUser = managerUser)

        // Non-manager cannot delete
        try {
            repo.deleteInventorySession(session.id, currentUser = inventoryClerkUser)
            fail("Expected SecurityException for non-manager deleting inventory")
        } catch (_: SecurityException) {
            // expected
        }

        val reverted = repo.deleteInventorySession(
            inventoryId = session.id,
            revertStockAdjustments = false,
            reason = "جلسة تجريبية",
            currentUser = managerUser
        )

        assertEquals(0, reverted)
        assertNull(db.stocktakingDao().getInventoryById(session.id))
        assertTrue(db.stocktakingDao().getItemsForInventoryList(session.id).isEmpty())
        // Product stock untouched
        assertEquals(20.0, db.appDao().getProductById(p1)!!.currentStock, 0.001)

        val logs = db.appDao().getAllAuditLogs().first()
        assertTrue(logs.any { it.actionType == "INVENTORY_DELETE" && it.entityId == session.reference && it.isCritical })
    }

    @Test
    fun deleteCompletedSession_archiveOnly_keepsCurrentStockIntact() = runBlocking {
        val (p1, p2) = seedProducts()
        val session = repo.createInventorySession(type = InventoryType.GENERAL, currentUser = managerUser)
        repo.recordInventoryItemCount(session.id, p1, 17.0, currentUser = managerUser) // -3
        repo.recordInventoryItemCount(session.id, p2, 12.0, currentUser = managerUser) // +2
        repo.approveAndApplyInventory(session.id, reason = "اعتماد", currentUser = managerUser)

        assertEquals(17.0, db.appDao().getProductById(p1)!!.currentStock, 0.001)
        assertEquals(12.0, db.appDao().getProductById(p2)!!.currentStock, 0.001)
        assertEquals(2, db.stocktakingDao().getAdjustmentsForInventoryList(session.id).size)

        val reverted = repo.deleteInventorySession(
            inventoryId = session.id,
            revertStockAdjustments = false,
            reason = "تنظيف الأرشيف",
            currentUser = managerUser
        )

        assertEquals(0, reverted)
        assertNull(db.stocktakingDao().getInventoryById(session.id))
        assertTrue(db.stocktakingDao().getItemsForInventoryList(session.id).isEmpty())
        assertTrue(db.stocktakingDao().getAdjustmentsForInventoryList(session.id).isEmpty())
        // Stock remains at adjusted values
        assertEquals(17.0, db.appDao().getProductById(p1)!!.currentStock, 0.001)
        assertEquals(12.0, db.appDao().getProductById(p2)!!.currentStock, 0.001)
    }

    @Test
    fun deleteCompletedSession_withReversal_restoresStockAndPreservesPostApprovalSales() = runBlocking {
        val (p1, p2) = seedProducts()
        val session = repo.createInventorySession(type = InventoryType.GENERAL, currentUser = managerUser)
        repo.recordInventoryItemCount(session.id, p1, 16.0, currentUser = managerUser) // diff = -4 (20 -> 16)
        repo.recordInventoryItemCount(session.id, p2, 13.0, currentUser = managerUser) // diff = +3 (10 -> 13)
        repo.approveAndApplyInventory(session.id, reason = "اعتماد خاطئ", currentUser = managerUser)

        assertEquals(16.0, db.appDao().getProductById(p1)!!.currentStock, 0.001)
        assertEquals(13.0, db.appDao().getProductById(p2)!!.currentStock, 0.001)

        // Simulate a concurrent sale of 2 units of p1 AFTER approval (16 -> 14)
        db.appDao().adjustProductStock(p1, -2.0)
        assertEquals(14.0, db.appDao().getProductById(p1)!!.currentStock, 0.001)

        // Delete with stock reversal:
        // p1 should be 14 + 4 = 18 (NOT 20 — the post-approval sale of 2 units is preserved!)
        // p2 should be 13 - 3 = 10
        val reverted = repo.deleteInventorySession(
            inventoryId = session.id,
            revertStockAdjustments = true,
            reason = "تصحيح خطأ في الجرد",
            currentUser = managerUser
        )

        assertEquals(2, reverted)
        assertNull(db.stocktakingDao().getInventoryById(session.id))
        assertTrue(db.stocktakingDao().getItemsForInventoryList(session.id).isEmpty())
        assertTrue(db.stocktakingDao().getAdjustmentsForInventoryList(session.id).isEmpty())
        assertEquals(18.0, db.appDao().getProductById(p1)!!.currentStock, 0.001)
        assertEquals(10.0, db.appDao().getProductById(p2)!!.currentStock, 0.001)

        val movements = db.appDao().getAllStockMovements().first()
        val reversals = movements.filter { it.type == "INVENTORY_REVERSAL" }
        assertEquals(2, reversals.size)
        assertEquals(4.0, reversals.first { it.productId == p1 }.quantity, 0.001)
        assertEquals(-3.0, reversals.first { it.productId == p2 }.quantity, 0.001)

        val logs = db.appDao().getAllAuditLogs().first()
        assertTrue(logs.any { it.actionType == "INVENTORY_DELETE_REVERT" && it.entityId == session.reference && it.isCritical })
    }

    @Test
    fun endToEndTwoCompletedSessions_computesHistoricalComparisonAndCategoryBreakdown() = runBlocking {
        val (p1, p2) = seedProducts()

        // Session 1: 1 shortage (p1: 20 -> 18, -2000 cents), 1 matching (p2: 10 -> 10) => 50% accuracy
        val s1 = repo.createInventorySession(type = InventoryType.GENERAL, title = "جرد 1", currentUser = managerUser)
        repo.recordInventoryItemCount(s1.id, p1, 18.0, currentUser = managerUser)
        repo.recordInventoryItemCount(s1.id, p2, 10.0, currentUser = managerUser)
        val completed1 = repo.approveAndApplyInventory(s1.id, reason = "جرد 1", currentUser = managerUser)

        // Session 2: both matching (p1: 18 -> 18, p2: 10 -> 10) => 100% accuracy
        val s2 = repo.createInventorySession(type = InventoryType.GENERAL, title = "جرد 2", currentUser = managerUser)
        repo.recordInventoryItemCount(s2.id, p1, 18.0, currentUser = managerUser)
        repo.recordInventoryItemCount(s2.id, p2, 10.0, currentUser = managerUser)
        val completed2 = repo.approveAndApplyInventory(s2.id, reason = "جرد 2", currentUser = managerUser)

        val comparison = repo.getInventoryHistoricalComparison(completed2.id)
        assertNotNull(comparison)
        assertEquals(completed1.id, comparison!!.previousInventory?.id)
        assertEquals(-1, comparison.discrepancyCountDelta)
        assertEquals(2000L, comparison.netValueDeltaCents)
        assertEquals(100f, comparison.currentAccuracyPercent, 0.01f)
        assertEquals(50f, comparison.previousAccuracyPercent!!, 0.01f)

        val items1 = db.stocktakingDao().getItemsForInventoryList(completed1.id)
        val breakdown = StocktakingReportBuilder.buildCategoryBreakdown(items1)
        assertEquals(1, breakdown.size)
        assertEquals("مشروبات", breakdown.first().categoryName)
        assertEquals(-2000L, breakdown.first().netDiscrepancyValueCents)

        val allCompleted = db.stocktakingDao().getAllInventoriesList()
        val historyStats = StocktakingReportBuilder.buildHistoryAnalytics(allCompleted)
        assertEquals(2, historyStats.completedSessions)
        assertEquals(75f, historyStats.averageAccuracyPercent, 0.01f)
        assertEquals(-2000L, historyStats.cumulativeNetDiscrepancyCents)
    }
}
