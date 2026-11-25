package com.example.ui.catalog

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CategoryEntity
import com.example.data.GlobalSettingsEntity
import com.example.data.PosRepository
import com.example.data.ProductEntity
import com.example.data.UserEntity
import com.example.ui.screens.LowStockDashboardWidget
import com.example.ui.screens.catalog.CatalogPassToSaleDialog
import com.example.ui.viewmodels.CartViewModel
import com.example.ui.viewmodels.InventoryViewModel
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CatalogAndLowStockTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: PosRepository
    private lateinit var testScope: CoroutineScope
    private lateinit var cartViewModel: CartViewModel
    private lateinit var inventoryViewModel: InventoryViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(database.appDao(), database)
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        cartViewModel = CartViewModel(repository = repository, externalScope = testScope)
        inventoryViewModel = InventoryViewModel(repository = repository, externalScope = testScope)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        database.close()
    }

    // =========================================================================
    // 1. Catalog Sale Logic Tests (Quantity, Custom Price, Default Price Update, Checkout)
    // =========================================================================

    @Test
    fun testCatalogPassToSaleQuantityCustomPriceOverrideAndCheckout() = runBlocking {
        val catId = repository.saveCategory(CategoryEntity(name = "الألبان والأجبان"))
        val prodId = database.appDao().insertProduct(
            ProductEntity(
                barcode = "CAT-001",
                name = "جبن أحمر هولندي",
                categoryId = catId,
                categoryName = "الألبان والأجبان",
                unit = "كيلو",
                purchasePrice = 8000L, // 80.00
                sellPrice = 11000L,    // 110.00
                wholesalePrice = 9800L,
                currentStock = 10.0,
                minStock = 3.0
            )
        )
        val product = repository.getProductById(prodId)!!

        // Pass from catalog with decimal quantity (2.5) and custom negotiated price (100.00)
        cartViewModel.addOrUpdateCartItem(
            product = product,
            quantity = 2.5,
            customPrice = 100.0,
            replaceExisting = true
        )
        delay(100L)

        val cartItems = cartViewModel.cart.value
        assertEquals(1, cartItems.size)
        assertEquals(2.5, cartItems[0].quantity, 0.001)
        assertEquals(100.0, cartItems[0].unitPrice, 0.001)
        assertEquals(25000L, cartViewModel.currentSubtotalCents) // 2.5 * 100.00 = 250.00

        // Re-open catalog pass-to-sale and update quantity to 4.0 while restoring default price
        cartViewModel.addOrUpdateCartItem(
            product = product,
            quantity = 4.0,
            customPrice = null,
            replaceExisting = true
        )
        delay(100L)

        val updatedItem = cartViewModel.cart.value.single()
        assertEquals(4.0, updatedItem.quantity, 0.001)
        assertNull("Custom price should reset to null when reverted to default", updatedItem.customPrice)
        assertEquals(110.0, updatedItem.unitPrice, 0.001)
        assertEquals(44000L, cartViewModel.currentSubtotalCents) // 4 * 110.00 = 440.00

        // Override price once more (105.00) and execute checkout -> verifies Sale, SaleItem, & stock deduction
        cartViewModel.addOrUpdateCartItem(
            product = product,
            quantity = 8.0,
            customPrice = 105.0,
            replaceExisting = true
        )
        cartViewModel.receivedCash.value = 900.0
        val checkoutDone = CompletableDeferred<Unit>()
        var checkoutErr: String? = null
        cartViewModel.executeCheckout(
            appContext = context,
            currentUser = UserEntity(id = 1, username = "بائع الكتالوج", passwordHash = "h", role = "MANAGER"),
            language = "AR",
            settings = GlobalSettingsEntity(id = 1, shopName = "متجر الكتالوج"),
            printerConnectionType = "NONE",
            printerName = "",
            printerMac = "",
            printerIp = "",
            printerPort = 9100,
            printerPaperWidth = 58,
            autoPrintReceipt = false,
            onError = { checkoutErr = it },
            onComplete = { checkoutDone.complete(Unit) }
        )
        withTimeout(10_000L) { checkoutDone.await() }

        assertNull("Checkout should succeed without error: $checkoutErr", checkoutErr)
        assertTrue("Cart should be cleared after checkout", cartViewModel.cart.value.isEmpty())

        // Verify persisted Sale + SaleItem with overridden price (8 * 105.00 = 840.00)
        val sales = database.appDao().getAllSalesList()
        assertEquals(1, sales.size)
        assertEquals(84000L, sales[0].totalAmount)
        assertEquals("بائع الكتالوج", sales[0].cashierName)

        val saleItems = repository.getSaleItems(sales[0].id)
        assertEquals(1, saleItems.size)
        assertEquals(8.0, saleItems[0].quantity, 0.001)
        assertEquals(10500L, saleItems[0].unitPrice)
        assertEquals(84000L, saleItems[0].totalLinePrice)

        // Stock deducted from 10.0 -> 2.0 (which is now <= minStock 3.0 -> enters Low Stock!)
        val afterSaleProduct = repository.getProductById(prodId)!!
        assertEquals(2.0, afterSaleProduct.currentStock, 0.001)
        val lowStockList = repository.lowStockProducts.first()
        assertTrue(
            "Product whose stock fell to 2.0 (<= minStock 3.0) after catalog sale must appear in lowStockProducts",
            lowStockList.any { it.id == prodId }
        )
    }

    @Test
    fun testCatalogPassToSaleDialogUiModifiesQuantityPriceAndSaveAsDefaultFlag() {
        val sampleProduct = ProductEntity(
            id = 42L,
            barcode = "CAT-UI-42",
            name = "زيت زيتون بكر 1L",
            categoryId = 1L,
            categoryName = "المواد الغذائية",
            unit = "قنينة",
            purchasePrice = 5500L,
            sellPrice = 7500L,
            wholesalePrice = 6800L,
            specialPrice = 7000L,
            currentStock = 18.0,
            minStock = 4.0
        )

        var confirmedQty = 0.0
        var confirmedPrice = 0.0
        var confirmedSaveDefault = false

        composeRule.setContent {
            CatalogPassToSaleDialog(
                product = sampleProduct,
                existingCartQuantity = 0.0,
                existingCartPrice = null,
                canModifyPrice = true,
                currency = "DH",
                lang = "AR",
                onDismiss = {},
                onConfirm = { qty, price, saveDefault ->
                    confirmedQty = qty
                    confirmedPrice = price
                    confirmedSaveDefault = saveDefault
                }
            )
        }
        composeRule.waitForIdle()

        // Increase quantity from 1 -> 3 using '+' stepper twice
        composeRule.onNodeWithTag("catalog_pass_qty_plus")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("catalog_pass_qty_plus")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Unlock price editing, enter custom price 69.50, and check "save as default"
        composeRule.onNodeWithTag("catalog_toggle_edit_price_button")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("catalog_pass_price_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("catalog_pass_price_input").performTextInput("69.50")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("catalog_save_new_price_checkbox")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Confirm pass to sale
        composeRule.onNodeWithTag("catalog_pass_confirm_button")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(3.0, confirmedQty, 0.001)
        assertEquals(69.50, confirmedPrice, 0.001)
        assertTrue("Expected saveAsDefaultPrice to be true", confirmedSaveDefault)
    }

    @Test
    fun testCatalogPassToSaleDialogLocksPriceWhenCashierCannotModifyPrice() {
        val sampleProduct = ProductEntity(
            id = 43L,
            barcode = "CAT-UI-43",
            name = "قهوة محمصة",
            categoryId = 1L,
            categoryName = "المشروبات",
            unit = "علبة",
            purchasePrice = 2000L,
            sellPrice = 3200L,
            currentStock = 12.0,
            minStock = 3.0
        )

        var confirmedPrice = 0.0
        var confirmedSaveDefault = true

        composeRule.setContent {
            CatalogPassToSaleDialog(
                product = sampleProduct,
                existingCartQuantity = 0.0,
                existingCartPrice = null,
                canModifyPrice = false,
                currency = "DH",
                lang = "AR",
                onDismiss = {},
                onConfirm = { _, price, saveDefault ->
                    confirmedPrice = price
                    confirmedSaveDefault = saveDefault
                }
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("catalog_toggle_edit_price_button").assertDoesNotExist()
        composeRule.onNodeWithTag("catalog_pass_confirm_button")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(32.0, confirmedPrice, 0.001)
        assertFalse(confirmedSaveDefault)
    }

    // =========================================================================
    // 2. Low Stock Monitoring & Dashboard Widget Tests
    // =========================================================================

    @Test
    fun testLowStockRepositoryQueryPendingFilterAndQuickRestockRemoval() = runBlocking {
        val catId = repository.saveCategory(CategoryEntity(name = "المخبوزات"))

        val outId = database.appDao().insertProduct(
            ProductEntity(
                barcode = "LS-001",
                name = "دقيق ممتاز",
                categoryId = catId,
                categoryName = "المخبوزات",
                unit = "كيس",
                purchasePrice = 4000L,
                sellPrice = 5000L,
                currentStock = 0.0,
                minStock = 5.0
            )
        )
        val lowId = database.appDao().insertProduct(
            ProductEntity(
                barcode = "LS-002",
                name = "خميرة فورية",
                categoryId = catId,
                categoryName = "المخبوزات",
                unit = "علبة",
                purchasePrice = 300L,
                sellPrice = 500L,
                currentStock = 3.0,
                minStock = 5.0
            )
        )
        val healthyId = database.appDao().insertProduct(
            ProductEntity(
                barcode = "LS-003",
                name = "سكر أبيض",
                categoryId = catId,
                categoryName = "المخبوزات",
                unit = "كيلو",
                purchasePrice = 600L,
                sellPrice = 800L,
                currentStock = 40.0,
                minStock = 10.0
            )
        )

        val lowList = repository.lowStockProducts.first()
        val lowIds = lowList.map { it.id }.toSet()
        assertTrue("Out-of-stock product must be in lowStockProducts", outId in lowIds)
        assertTrue("Below-min product must be in lowStockProducts", lowId in lowIds)
        assertFalse("Healthy-stock product must NOT be in lowStockProducts", healthyId in lowIds)

        // One-shot pendingLowStockFilter navigation flag
        assertFalse(inventoryViewModel.consumePendingLowStockFilter())
        inventoryViewModel.requestOpenLowStockFilter()
        assertTrue(inventoryViewModel.pendingLowStockFilter.value)
        assertTrue(inventoryViewModel.consumePendingLowStockFilter())
        assertFalse(inventoryViewModel.pendingLowStockFilter.value)

        // Quick restock updates stock above minStock and clears item from lowStockProducts
        val restockTarget = repository.getProductById(outId)!!
        inventoryViewModel.saveProduct(restockTarget.copy(currentStock = 25.0))
        delay(100L)

        val updatedLowIds = repository.lowStockProducts.first().map { it.id }.toSet()
        assertFalse("Restocked item must disappear from lowStockProducts", outId in updatedLowIds)
        assertTrue("Unrestocked low item must remain in lowStockProducts", lowId in updatedLowIds)
    }

    @Test
    fun testLowStockDashboardWidgetThresholdFilteringAndQuickRestockDialog() {
        val outProduct = ProductEntity(
            id = 101L,
            barcode = "W-101",
            name = "حليب طازج",
            categoryId = 1L,
            categoryName = "الألبان",
            unit = "لتر",
            purchasePrice = 600L,
            sellPrice = 800L,
            currentStock = 0.0,
            minStock = 4.0
        )
        val warningProduct = ProductEntity(
            id = 102L,
            barcode = "W-102",
            name = "ياغورت طبيعي",
            categoryId = 1L,
            categoryName = "الألبان",
            unit = "علبة",
            purchasePrice = 200L,
            sellPrice = 300L,
            currentStock = 4.0,
            minStock = 6.0
        )
        val mediumProduct = ProductEntity(
            id = 103L,
            barcode = "W-103",
            name = "شاي أخضر",
            categoryId = 1L,
            categoryName = "المشروبات",
            unit = "علبة",
            purchasePrice = 1000L,
            sellPrice = 1500L,
            currentStock = 8.0,
            minStock = 3.0
        )
        val allProducts = listOf(outProduct, warningProduct, mediumProduct)
        val defaultLow = listOf(outProduct, warningProduct)

        val posVm = PosViewModel(context.applicationContext as android.app.Application, repository)
        var openInventoryClicks = 0

        composeRule.setContent {
            LowStockDashboardWidget(
                products = allProducts,
                lowStockProducts = defaultLow,
                lang = "AR",
                viewModel = posVm,
                onOpenInventoryLowStock = { openInventoryClicks++ }
            )
        }
        composeRule.waitForIdle()

        // Default mode ("Item Min"): shows outProduct & warningProduct, hides mediumProduct (stock 8 > min 3)
        composeRule.onNodeWithTag("low_stock_dashboard_widget").assertIsDisplayed()
        composeRule.onNodeWithTag("low_stock_item_101").assertIsDisplayed()
        composeRule.onNodeWithTag("low_stock_item_102").assertIsDisplayed()
        composeRule.onNodeWithTag("low_stock_item_103").assertDoesNotExist()

        // Filter: Out of stock only (0) -> shows only 101
        composeRule.onNodeWithTag("low_stock_threshold_chip_0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("low_stock_item_101").assertIsDisplayed()
        composeRule.onNodeWithTag("low_stock_item_102").assertDoesNotExist()

        // Filter: <= 10 -> shows all 3 items including mediumProduct (stock 8)
        composeRule.onNodeWithTag("low_stock_threshold_chip_10").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("low_stock_item_101").assertIsDisplayed()
        composeRule.onNodeWithTag("low_stock_item_102").assertIsDisplayed()
        composeRule.onNodeWithTag("low_stock_item_103").assertIsDisplayed()

        // Open Quick Restock dialog for out-of-stock item 101
        composeRule.onNodeWithTag("low_stock_quick_restock_101").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_restock_quantity_input").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_restock_threshold_input").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_restock_confirm_button").assertIsDisplayed()
        composeRule.onAllNodesWithText("حليب طازج").assertCountEquals(2)

        // Close dialog & tap "Inventory" button
        composeRule.onNodeWithTag("quick_restock_confirm_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("low_stock_view_all_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, openInventoryClicks)
    }
}
