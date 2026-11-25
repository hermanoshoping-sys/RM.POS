package com.example.ui.viewmodels

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CategoryEntity
import com.example.data.PosRepository
import com.example.data.ProductEntity
import com.example.data.UserEntity
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StocktakingViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: PosRepository

    private val managerUser = UserEntity(
        id = 1,
        username = "manager",
        passwordHash = "hash",
        role = "MANAGER"
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(db.appDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `viewmodel creates session handles barcode modes pause resume and updates live stats`() = runBlocking {
        val dao = db.appDao()
        val catId = dao.insertCategory(CategoryEntity(name = "مشروبات"))
        val p1Id = dao.insertProduct(
            ProductEntity(
                barcode = "6110001112221",
                name = "عصير برتقال",
                categoryId = catId,
                categoryName = "مشروبات",
                unit = "PCS",
                purchasePrice = 500L,
                sellPrice = 800L,
                currentStock = 10.0,
                minStock = 3.0
            )
        )
        val p2Id = dao.insertProduct(
            ProductEntity(
                barcode = "6110001112222",
                name = "ماء معدني",
                categoryId = catId,
                categoryName = "مشروبات",
                unit = "PCS",
                purchasePrice = 200L,
                sellPrice = 400L,
                currentStock = 20.0,
                minStock = 5.0
            )
        )

        var beepCount = 0
        val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val vm = StocktakingViewModel(
                repository = repository,
                scope = vmScope,
                currentUserProvider = { managerUser },
                languageProvider = { "AR" },
                onBeep = { beepCount++ }
            )

            // 1. Create partial session with only p1 and PROMPT duplicate scan mode
            var createdId = 0L
            vm.createInventorySession(
                type = InventoryType.PARTIAL_PRODUCTS,
                title = "جرد المشروبات",
                productIds = listOf(p1Id),
                duplicateScanMode = DuplicateScanMode.PROMPT,
                startImmediately = true
            ) { created ->
                createdId = created.id
            }
            waitUntil { createdId > 0L }

            val stats = repository.getInventoryDashboardStatsFlow().first()
            assertEquals(2, stats.totalActiveProducts)
            assertEquals(1, stats.activeInventoriesCount)

            // 2. Scan p1 once -> auto-counts to 1.0
            vm.handleBarcodeScan(createdId, "6110001112221")
            waitUntil {
                runBlocking {
                    repository.getInventoryItemsFlow(createdId).first().firstOrNull { it.productId == p1Id }?.countedQuantity == 1.0
                }
            }
            assertEquals(1, beepCount)

            // 3. Scan p1 again in PROMPT mode -> triggers pendingDuplicateScan
            vm.handleBarcodeScan(createdId, "6110001112221")
            waitUntil { vm.pendingDuplicateScan.value != null }
            val prompt = vm.pendingDuplicateScan.value!!
            assertEquals(1.0, prompt.existingCountedQuantity, 0.0001)

            // Resolve duplicate scan by replacing with 9.0
            vm.resolveDuplicateScan(createdId, prompt.item, 9.0)
            waitUntil {
                runBlocking {
                    repository.getInventoryItemsFlow(createdId).first().firstOrNull { it.productId == p1Id }?.countedQuantity == 9.0
                }
            }
            assertNull(vm.pendingDuplicateScan.value)

            // 4. Scan p2 (not in partial session) -> triggers pendingNotInSessionScan -> add & count
            vm.handleBarcodeScan(createdId, "6110001112222")
            waitUntil { vm.pendingNotInSessionScan.value != null }
            val notInSession = vm.pendingNotInSessionScan.value!!
            assertEquals(p2Id, notInSession.catalogProductId)

            vm.addNotInSessionProductAndCount(createdId, p2Id, initialQuantity = 20.0)
            waitUntil {
                runBlocking {
                    repository.getInventoryItemsFlow(createdId).first().any { it.productId == p2Id && it.countedQuantity == 20.0 }
                }
            }
            assertNull(vm.pendingNotInSessionScan.value)

            // 5. Pause session -> transitions from IN_PROGRESS to DRAFT
            val currentSession = repository.getInventoryByIdFlow(createdId).first()
            assertNotNull(currentSession)
            assertEquals(InventoryStatus.IN_PROGRESS.code, currentSession!!.status)

            vm.togglePauseResumeSession(currentSession)
            waitUntil {
                runBlocking {
                    repository.getInventoryByIdFlow(createdId).first()?.status == InventoryStatus.DRAFT.code
                }
            }
            assertTrue(vm.feedback.value?.message?.isNotBlank() == true)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun `viewmodel builds approval preview enforces manager RBAC preserves concurrent movements and supports cancel and slow moving window`() = runBlocking {
        val dao = db.appDao()
        val catId = dao.insertCategory(CategoryEntity(name = "ألبان"))
        val p1Id = dao.insertProduct(
            ProductEntity(
                barcode = "6119990001111",
                name = "حليب طازج",
                categoryId = catId,
                categoryName = "ألبان",
                unit = "PCS",
                purchasePrice = 600L, // 6.00
                sellPrice = 900L,     // 9.00
                currentStock = 10.0,
                minStock = 2.0
            )
        )
        val p2Id = dao.insertProduct(
            ProductEntity(
                barcode = "6119990002222",
                name = "جبن أبيض",
                categoryId = catId,
                categoryName = "ألبان",
                unit = "PCS",
                purchasePrice = 1200L,
                sellPrice = 1800L,
                currentStock = 5.0,
                minStock = 1.0
            )
        )

        val cashierUser = UserEntity(
            id = 2,
            username = "cashier",
            passwordHash = "hash",
            role = "CASHIER",
            canManageInventory = true
        )
        var activeUser: UserEntity = cashierUser

        val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val vm = StocktakingViewModel(
                repository = repository,
                scope = vmScope,
                currentUserProvider = { activeUser },
                languageProvider = { "AR" }
            )

            // 1. Slow-moving threshold clamps and updates
            vm.setSlowMovingDays(60)
            assertEquals(60, vm.slowMovingDays.value)
            vm.setSlowMovingDays(1)
            assertEquals(7, vm.slowMovingDays.value)

            // 2. Cashier creates session and counts p1 = 8.0 (expected 10.0 => diff -2.0, cost -1200 cents), leaving p2 uncounted
            var invId = 0L
            vm.createInventorySession(
                type = InventoryType.GENERAL,
                title = "جرد الألبان",
                startImmediately = true
            ) { created -> invId = created.id }
            waitUntil { invId > 0L }

            vm.recordItemCount(invId, p1Id, 8.0)
            waitUntil {
                runBlocking {
                    repository.getInventoryItemsFlow(invId).first().first { it.productId == p1Id }.countedQuantity == 8.0
                }
            }

            // Simulate concurrent sale of 3 units of p1 AFTER counting (live stock drops from 10.0 -> 7.0)
            dao.adjustProductStock(p1Id, -3.0)

            // 3. Load approval preview -> must show projected stock 7.0 + (-2.0) = 5.0, concurrent count = 1, uncounted = 1
            vm.loadApprovalPreview(invId)
            waitUntil { vm.approvalPreview.value != null }
            val preview = vm.approvalPreview.value!!
            assertEquals(1, preview.itemsToAdjust.size)
            assertEquals(1, preview.uncountedItemsCount)
            assertEquals(1, preview.concurrentMovementItemsCount)
            assertEquals(7.0, preview.itemsToAdjust.first().liveSystemStock, 0.0001)
            assertEquals(5.0, preview.itemsToAdjust.first().projectedAdjustedStock, 0.0001)
            assertEquals(1200L, preview.totalShortageValueCents)
            assertEquals(-1200L, preview.netDiscrepancyValueCents)

            // 4. Cashier attempts approval -> MUST FAIL (RBAC) and leave stock untouched
            vm.clearFeedback()
            vm.approveAndApplyInventory(invId, "محاولة كاشير")
            waitUntil { vm.feedback.value?.type == StocktakingFeedbackType.ERROR }
            assertEquals(7.0, dao.getProductById(p1Id)!!.currentStock, 0.0001)
            assertEquals(InventoryStatus.IN_PROGRESS.code, repository.getInventoryByIdFlow(invId).first()!!.status)

            // 5. Switch to Manager -> approval succeeds atomically, adjusts p1 to 5.0, leaves uncounted p2 at 5.0, writes audit trail
            activeUser = managerUser
            var approvedDone = false
            vm.approveAndApplyInventory(invId, "اعتماد المدير") { approvedDone = true }
            waitUntil { approvedDone }

            assertNull(vm.approvalPreview.value)
            assertEquals(5.0, dao.getProductById(p1Id)!!.currentStock, 0.0001)
            assertEquals(5.0, dao.getProductById(p2Id)!!.currentStock, 0.0001)
            val completedSession = repository.getInventoryByIdFlow(invId).first()!!
            assertEquals(InventoryStatus.COMPLETED.code, completedSession.status)
            assertEquals("manager", completedSession.approvedBy)

            val adjustments = repository.getInventoryAdjustmentsFlow(invId).first()
            assertEquals(1, adjustments.size)
            assertEquals(7.0, adjustments.first().previousQuantity, 0.0001)
            assertEquals(5.0, adjustments.first().adjustedQuantity, 0.0001)
            assertEquals(-3.0, adjustments.first().concurrentDeltaAfterCount, 0.0001)
            assertEquals(-1200L, adjustments.first().financialImpactCents)

            // 6. Create second session and cancel it as manager -> stock untouched, status = CANCELLED
            var inv2Id = 0L
            vm.createInventorySession(
                type = InventoryType.PARTIAL_PRODUCTS,
                title = "جلسة تجريبية للإلغاء",
                productIds = listOf(p2Id),
                startImmediately = true
            ) { created -> inv2Id = created.id }
            waitUntil { inv2Id > 0L }

            var cancelDone = false
            vm.cancelInventorySession(inv2Id, "إلغاء تجريبي") { cancelDone = true }
            waitUntil { cancelDone }
            assertEquals(InventoryStatus.CANCELLED.code, repository.getInventoryByIdFlow(inv2Id).first()!!.status)
            assertEquals(5.0, dao.getProductById(p2Id)!!.currentStock, 0.0001)
        } finally {
            vmScope.cancel()
        }
    }

    private suspend fun waitUntil(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!predicate()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
            }
            delay(25L)
        }
    }
}
