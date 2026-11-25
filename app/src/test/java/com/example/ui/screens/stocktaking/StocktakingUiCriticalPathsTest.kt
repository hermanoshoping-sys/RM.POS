package com.example.ui.screens.stocktaking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.data.InventoryEntity
import com.example.data.InventoryItemEntity
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.InventoryComparisonSummary
import com.example.domain.stocktaking.InventoryDashboardStats
import com.example.domain.stocktaking.InventoryItemStatus
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import com.example.ui.theme.RmPosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StocktakingUiCriticalPathsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleSession(
        id: Long = 10L,
        ref: String = "INV-20261123-0010",
        status: InventoryStatus = InventoryStatus.IN_PROGRESS
    ) = InventoryEntity(
        id = id,
        reference = ref,
        title = "جرد المشروبات",
        type = InventoryType.GENERAL.code,
        status = status.code,
        createdBy = "Admin",
        approvedBy = if (status == InventoryStatus.COMPLETED) "Manager" else null,
        completedAt = if (status == InventoryStatus.COMPLETED) 1_700_000_000_000L else null,
        totalItemsCount = 10,
        countedItemsCount = 8,
        discrepancyItemsCount = 2,
        totalShortageValueCents = 2500L,
        totalSurplusValueCents = 500L,
        netDiscrepancyValueCents = -2000L
    )

    private fun sampleItems(inventoryId: Long = 10L): List<InventoryItemEntity> = listOf(
        InventoryItemEntity(
            id = 1L,
            inventoryId = inventoryId,
            productId = 101L,
            productName = "عصير برتقال",
            barcode = "611001",
            categoryName = "مشروبات",
            unit = "قارورة",
            expectedQuantity = 10.0,
            systemStockAtCount = 10.0,
            countedQuantity = 8.0,
            difference = -2.0,
            unitCostCents = 1000L,
            discrepancyValueCents = -2000L,
            status = InventoryItemStatus.SHORTAGE.code
        ),
        InventoryItemEntity(
            id = 2L,
            inventoryId = inventoryId,
            productId = 102L,
            productName = "ماء معدني",
            barcode = "611002",
            categoryName = "مشروبات",
            unit = "قارورة",
            expectedQuantity = 5.0,
            systemStockAtCount = 5.0,
            countedQuantity = 5.0,
            difference = 0.0,
            unitCostCents = 300L,
            discrepancyValueCents = 0L,
            status = InventoryItemStatus.MATCHING.code
        )
    )

    @Test
    fun editInventorySessionDialog_updatesTitleNotesAndScanMode() {
        val session = sampleSession(status = InventoryStatus.IN_PROGRESS)
        var capturedTitle = ""
        var capturedNotes = ""
        var capturedMode: DuplicateScanMode? = null

        composeRule.setContent {
            RmPosTheme(darkTheme = false) {
                EditInventorySessionDialog(
                    inventory = session,
                    isBusy = false,
                    lang = "AR",
                    onDismiss = {},
                    onConfirm = { t, n, m ->
                        capturedTitle = t
                        capturedNotes = n
                        capturedMode = m
                    }
                )
            }
        }

        composeRule.onNodeWithTag("edit_inventory_title_input").performTextClearance()
        composeRule.onNodeWithTag("edit_inventory_title_input").performTextInput("جرد معدل")
        composeRule.onNodeWithTag("edit_inventory_notes_input").performTextInput("تمت المراجعة")
        composeRule.onNodeWithTag("edit_inventory_scan_mode_prompt").performScrollTo().performClick()
        composeRule.onNodeWithTag("edit_inventory_confirm_btn").performClick()

        assertEquals("جرد معدل", capturedTitle)
        assertEquals("تمت المراجعة", capturedNotes)
        assertEquals(DuplicateScanMode.PROMPT, capturedMode)
    }

    @Test
    fun deleteInventorySessionDialog_completedSessionTogglesReversalAndConfirms() {
        val completed = sampleSession(status = InventoryStatus.COMPLETED)
        var capturedRevert = false
        var capturedReason = ""
        var confirmed = false

        composeRule.setContent {
            RmPosTheme(darkTheme = false) {
                DeleteInventorySessionDialog(
                    inventory = completed,
                    isBusy = false,
                    lang = "AR",
                    onDismiss = {},
                    onConfirmDelete = { revert, reason ->
                        confirmed = true
                        capturedRevert = revert
                        capturedReason = reason
                    }
                )
            }
        }

        composeRule.onNodeWithTag("delete_inventory_revert_stock_checkbox").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("delete_inventory_reason_input").performTextInput("خطأ إدخال")
        composeRule.onNodeWithTag("delete_inventory_confirm_btn").performClick()

        assertTrue(confirmed)
        assertTrue(capturedRevert)
        assertEquals("خطأ إدخال", capturedReason)
    }

    @Test
    fun historyScreen_triggersReportEditAndDeleteCallbacks() {
        val session = sampleSession(id = 42L, status = InventoryStatus.COMPLETED)
        var openedReportId: Long? = null
        var editedId: Long? = null
        var deletedId: Long? = null

        composeRule.setContent {
            RmPosTheme(darkTheme = false) {
                StocktakingHistoryScreen(
                    allInventories = listOf(session),
                    isManager = true,
                    currency = "DA",
                    lang = "AR",
                    onBack = {},
                    onOpenReport = { openedReportId = it.id },
                    onOpenSession = {},
                    onEditSession = { editedId = it.id },
                    onDeleteSession = { deletedId = it.id }
                )
            }
        }

        composeRule.onNodeWithTag("stocktaking_history_list")
            .performScrollToNode(hasTestTag("history_session_card_42"))
        composeRule.onNodeWithTag("history_open_report_42").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("history_edit_session_42").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("history_delete_session_42").assertIsDisplayed().performClick()

        assertEquals(42L, openedReportId)
        assertEquals(42L, editedId)
        assertEquals(42L, deletedId)
    }

    @Test
    fun reportScreen_rendersExportActionsHistoricalComparisonAndHeaderEditDelete() {
        val prev = sampleSession(id = 9L, ref = "INV-20261101-0009", status = InventoryStatus.COMPLETED)
        val curr = sampleSession(id = 10L, ref = "INV-20261123-0010", status = InventoryStatus.COMPLETED)
        val comparison = InventoryComparisonSummary(
            currentInventory = curr,
            previousInventory = prev,
            discrepancyCountDelta = -1,
            netValueDeltaCents = 1000L,
            currentAccuracyPercent = 90f,
            previousAccuracyPercent = 80f
        )
        var editClicked = false
        var deleteClicked = false

        composeRule.setContent {
            RmPosTheme(darkTheme = false) {
                StocktakingReportScreen(
                    inventory = curr,
                    items = sampleItems(curr.id),
                    historicalComparison = comparison,
                    currency = "DA",
                    lang = "AR",
                    shopName = "RM.POS",
                    onBack = {},
                    onOpenAdjustments = {},
                    onEditSession = { editClicked = true },
                    onDeleteSession = { deleteClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("stocktaking_report_export_pdf_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("stocktaking_report_print_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("stocktaking_report_export_excel_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("stocktaking_report_export_csv_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("stocktaking_report_edit_btn").performClick()
        composeRule.onNodeWithTag("stocktaking_report_delete_btn").performClick()

        assertTrue(editClicked)
        assertTrue(deleteClicked)
    }

    @Test
    fun dashboardView_displaysQuickHistoryToolAndSessionActions() {
        val completed = sampleSession(id = 77L, ref = "INV-20261123-0077", status = InventoryStatus.COMPLETED)
        val stats = InventoryDashboardStats(
            totalActiveProducts = 12,
            totalUnitsInStock = 100.0,
            totalPurchaseValueCents = 50000L,
            totalSellValueCents = 80000L,
            potentialGrossMarginCents = 30000L,
            activeInventoriesCount = 0,
            completedInventoriesCount = 1,
            lastCompletedAt = completed.completedAt,
            lastCompletedReference = completed.reference,
            lastNetDiscrepancyCents = completed.netDiscrepancyValueCents
        )
        var historyOpened = false

        composeRule.setContent {
            RmPosTheme(darkTheme = false) {
                StocktakingDashboardView(
                    stats = stats,
                    allInventories = listOf(completed),
                    activeInventories = emptyList(),
                    isManager = true,
                    currency = "DA",
                    lang = "AR",
                    onBack = {},
                    onStartGeneral = {},
                    onStartPartial = {},
                    onOpenValuationMonitoring = {},
                    onOpenAdjustments = {},
                    onOpenHistory = { historyOpened = true },
                    onOpenSession = {},
                    onOpenSessionReport = {},
                    onEditSession = {},
                    onDeleteSession = {}
                )
            }
        }

        composeRule.onNodeWithTag("stocktaking_open_history_btn").assertIsDisplayed().performClick()
        assertTrue(historyOpened)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun cartScreen_showsOnlyCountedItemsWithExportAndQuantityControls() {
        val session = sampleSession(id = 10L, status = InventoryStatus.IN_PROGRESS)
        val uncounted = InventoryItemEntity(
            id = 3L,
            inventoryId = 10L,
            productId = 103L,
            productName = "منتج غير مجرود",
            barcode = "611003",
            categoryName = "أخرى",
            unit = "قطعة",
            expectedQuantity = 4.0,
            systemStockAtCount = null,
            countedQuantity = null,
            difference = 0.0,
            unitCostCents = 500L,
            discrepancyValueCents = 0L,
            status = InventoryItemStatus.UNCOUNTED.code
        )
        val recordedCalls = mutableListOf<Pair<Long, Double>>()
        val clearedIds = mutableListOf<Long>()

        composeRule.setContent {
            RmPosTheme(darkTheme = false) {
                StocktakingCartScreen(
                    inventory = session,
                    items = sampleItems(10L) + uncounted,
                    liveProductsById = emptyMap(),
                    currency = "DA",
                    lang = "AR",
                    shopName = "RM.POS",
                    onBack = {},
                    onRecordItemCount = { pid, qty, _ -> recordedCalls.add(pid to qty) },
                    onClearItemCount = { pid -> clearedIds.add(pid) },
                    onOpenDiscrepancies = {}
                )
            }
        }

        composeRule.onNodeWithTag("stocktaking_cart_print_btn").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("stocktaking_cart_pdf_btn").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("stocktaking_cart_excel_btn").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("stocktaking_cart_scan_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("cart_filter_all").assertIsDisplayed()
        composeRule.onNodeWithTag("cart_filter_matching").assertIsDisplayed()
        composeRule.onNodeWithTag("cart_filter_shortage").assertIsDisplayed()
        composeRule.onNodeWithTag("cart_filter_surplus").assertIsDisplayed()
        composeRule.onNodeWithTag("cart_item_card_103").assertDoesNotExist()

        composeRule.onNodeWithTag("stocktaking_cart_list")
            .performScrollToNode(hasTestTag("cart_item_card_101"))
        composeRule.onNodeWithTag("cart_item_card_101").assertIsDisplayed()
        composeRule.onNodeWithTag("cart_inc_101").performScrollTo().performClick()
        composeRule.onNodeWithTag("cart_dec_101").performScrollTo().performClick()
        composeRule.onNodeWithTag("cart_remove_101").performScrollTo().performClick()

        assertEquals(listOf(101L to 9.0, 101L to 7.0), recordedCalls)
        assertEquals(listOf(101L), clearedIds)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun cartScreen_emptyStateDisablesExportsWhenNothingCounted() {
        val session = sampleSession(id = 10L, status = InventoryStatus.IN_PROGRESS)
        val uncountedOnly = listOf(
            InventoryItemEntity(
                id = 3L,
                inventoryId = 10L,
                productId = 103L,
                productName = "منتج غير مجرود",
                barcode = "611003",
                categoryName = "أخرى",
                unit = "قطعة",
                expectedQuantity = 4.0,
                countedQuantity = null,
                status = InventoryItemStatus.UNCOUNTED.code
            )
        )

        composeRule.setContent {
            RmPosTheme(darkTheme = false) {
                StocktakingCartScreen(
                    inventory = session,
                    items = uncountedOnly,
                    liveProductsById = emptyMap(),
                    currency = "DA",
                    lang = "AR",
                    shopName = "RM.POS",
                    onBack = {},
                    onRecordItemCount = { _, _, _ -> },
                    onClearItemCount = {},
                    onOpenDiscrepancies = {}
                )
            }
        }

        composeRule.onNodeWithTag("stocktaking_cart_print_btn").assertIsNotEnabled()
        composeRule.onNodeWithTag("stocktaking_cart_pdf_btn").assertIsNotEnabled()
        composeRule.onNodeWithTag("stocktaking_cart_excel_btn").assertIsNotEnabled()
        composeRule.onNodeWithTag("stocktaking_cart_empty_state").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun countingScreen_togglesCardsAndTableModeAndOpensCart() {
        val session = sampleSession(id = 10L, status = InventoryStatus.IN_PROGRESS)
        var openedCart = false
        val recorded = mutableListOf<Pair<Long, Double>>()

        composeRule.setContent {
            RmPosTheme(darkTheme = false) {
                StocktakingCountingScreen(
                    inventory = session,
                    items = sampleItems(10L),
                    liveProductsById = emptyMap(),
                    highlightedProductId = null,
                    pendingDuplicateScan = null,
                    pendingNotInSessionScan = null,
                    currency = "DA",
                    lang = "AR",
                    onBack = {},
                    onTogglePauseResume = {},
                    onUpdateScanMode = {},
                    onRecordItemCount = { pid, qty, _ -> recorded.add(pid to qty) },
                    onClearItemCount = {},
                    onBarcodeScanned = {},
                    onResolveDuplicateScan = { _, _ -> },
                    onDismissDuplicateScan = {},
                    onAddNotInSessionProduct = {},
                    onDismissNotInSessionScan = {},
                    onOpenDiscrepancies = {},
                    onOpenCart = { openedCart = true }
                )
            }
        }

        // Default Cards mode: verify product card & quick +1 works
        composeRule.onNodeWithTag("counting_item_card_101").assertIsDisplayed()
        composeRule.onNodeWithTag("counting_plus_btn_101").performClick()
        assertEquals(listOf(101L to 9.0), recorded)

        // Toggle to Compact Ledger Table mode
        composeRule.onNodeWithTag("counting_view_mode_btn").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("counting_table_list").assertIsDisplayed()
        composeRule.onNodeWithTag("counting_item_card_101").assertIsDisplayed()

        // Open Inventory Cart from header pill
        composeRule.onNodeWithTag("counting_open_cart_btn").performClick()
        assertTrue(openedCart)
    }
}
