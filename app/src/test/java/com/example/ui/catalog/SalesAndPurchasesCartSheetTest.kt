package com.example.ui.catalog

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CategoryEntity
import com.example.data.PosRepository
import com.example.data.ProductEntity
import com.example.ui.screens.POSRegisterScreen
import com.example.ui.screens.ProductFamiliesScreen
import com.example.ui.screens.PurchasesScreen
import com.example.ui.viewmodels.PosViewModel
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SalesAndPurchasesCartSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: PosRepository
    private lateinit var viewModel: PosViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = Executor { runnable -> runnable.run() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        repository = PosRepository(database.appDao(), database)
        viewModel = PosViewModel(ApplicationProvider.getApplicationContext<Application>(), repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun waitForTag(tag: String, maxAttempts: Int = 60) {
        repeat(maxAttempts) {
            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
            composeRule.waitForIdle()
            if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
            Thread.sleep(20)
        }
        val tree = composeRule.onRoot().printToString()
        throw AssertionError(
            "Tag '$tag' not found. products=${viewModel.products.value.size}, " +
                "filteredPaged=${viewModel.filteredPagedProducts.value.size}, " +
                "categories=${viewModel.categories.value.size}\n$tree"
        )
    }

    @Test
    fun testSalesScreenProductTapOpensQuantityPriceStockDialogAndAddsToSalesCartSheet() {
        val productId = runBlocking {
            val catId = repository.saveCategory(CategoryEntity(name = "مشروبات"))
            database.appDao().insertProduct(
                ProductEntity(
                    barcode = "6111222333444",
                    name = "عصير برتقال طبيعي",
                    categoryId = catId,
                    categoryName = "مشروبات",
                    unit = "قنينة",
                    purchasePrice = 600L,
                    sellPrice = 950L,
                    currentStock = 25.0,
                    minStock = 5.0
                )
            )
        }

        composeRule.setContent {
            POSRegisterScreen(viewModel = viewModel, lang = "AR", onNavigate = {})
        }

        waitForTag("pos_product_row_$productId")

        // Screen 1: 3-column table + green cart button visible
        composeRule.onNodeWithText("كود بار").assertIsDisplayed()
        composeRule.onNodeWithText("المنتجات").assertIsDisplayed()
        composeRule.onNodeWithText("ثمن").assertIsDisplayed()
        composeRule.onNodeWithTag("pos_green_cart_button").assertIsDisplayed()

        // Tap product -> opens quantity, price, editable price & stock dialog
        composeRule.onNodeWithTag("pos_product_row_$productId").assertIsDisplayed().performClick()
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("catalog_pass_stock_badge").assertIsDisplayed()
        composeRule.onNodeWithText("25 قنينة").assertIsDisplayed()

        // Increase quantity 1 -> 2 and modify price to 11.00
        composeRule.onNodeWithTag("catalog_pass_qty_plus")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("catalog_toggle_edit_price_button")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("catalog_pass_price_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("catalog_pass_price_input").performTextInput("11.00")
        composeRule.waitForIdle()

        // Confirm adding to cart
        composeRule.onNodeWithTag("catalog_pass_confirm_button")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        // Verify cart state reflects modified quantity & price
        val addedCartItem = viewModel.cart.value.single()
        assertEquals(2.0, addedCartItem.quantity, 0.001)
        assertEquals(11.0, addedCartItem.unitPrice, 0.001)

        // Tap green cart button -> opens dedicated Sales Cart Sheet ("سلة المبيعات")
        composeRule.onNodeWithTag("pos_green_cart_button").performClick()
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("سلة المبيعات").assertIsDisplayed()
        composeRule.onNodeWithTag("pos_cart_row_$productId").assertIsDisplayed()
        composeRule.onNodeWithTag("pos_confirm_sale_button").assertIsDisplayed()

        // Tap back arrow -> returns to 3-column Sales catalog view
        composeRule.onNodeWithTag("pos_cart_back_button").performClick()
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pos_product_row_$productId").assertIsDisplayed()
    }

    @Test
    fun testPurchasesScreenProductTapOpensQuantityPriceStockDialogAndAddsToPurchasesCartSheet() {
        val productId = runBlocking {
            val catId = repository.saveCategory(CategoryEntity(name = "بقالة"))
            database.appDao().insertProduct(
                ProductEntity(
                    barcode = "6222333444555",
                    name = "زيت نباتي 1 لتر",
                    categoryId = catId,
                    categoryName = "بقالة",
                    unit = "قنينة",
                    purchasePrice = 1400L,
                    sellPrice = 1800L,
                    currentStock = 12.0,
                    minStock = 4.0
                )
            )
        }

        composeRule.setContent {
            PurchasesScreen(viewModel = viewModel, lang = "AR", onNavigate = {})
        }

        waitForTag("purchase_product_row_$productId")

        // Screen 1: 3-column purchases table + green cart button
        composeRule.onNodeWithText("كود بار").assertIsDisplayed()
        composeRule.onNodeWithText("المنتجات").assertIsDisplayed()
        composeRule.onNodeWithText("ثمن الشراء").assertIsDisplayed()
        composeRule.onNodeWithTag("purchases_green_cart_button").assertIsDisplayed()

        // Tap product -> opens purchase mode dialog showing stock, qty & editable purchase price
        composeRule.onNodeWithTag("purchase_product_row_$productId").assertIsDisplayed().performClick()
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("catalog_pass_stock_badge").assertIsDisplayed()
        composeRule.onNodeWithText("12 قنينة").assertIsDisplayed()

        // Increase quantity 1 -> 2 and modify purchase price to 15.50
        composeRule.onNodeWithTag("catalog_pass_qty_plus")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("catalog_toggle_edit_price_button")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("catalog_pass_price_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("catalog_pass_price_input").performTextInput("15.50")
        composeRule.waitForIdle()

        // Confirm adding to purchase cart
        composeRule.onNodeWithTag("catalog_pass_confirm_button")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        // Tap green cart button -> opens dedicated Purchases Cart Sheet ("سلة المشتريات")
        composeRule.onNodeWithTag("purchases_green_cart_button").performClick()
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("سلة المشتريات").assertIsDisplayed()
        composeRule.onNodeWithTag("purchase_cart_row_$productId").assertIsDisplayed()
        composeRule.onNodeWithText("15.50").assertIsDisplayed()
        composeRule.onNodeWithText("31.00").assertIsDisplayed()
        composeRule.onNodeWithTag("purchases_confirm_invoice_button").assertIsDisplayed()

        // Tap back arrow -> returns to Purchases 3-column product table
        composeRule.onNodeWithTag("purchases_cart_back_button").performClick()
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("purchase_product_row_$productId").assertIsDisplayed()
    }

    @Test
    fun testDeleteProductFamilyUnlinksProductsAndRemovesFamily() {
        val (catId, productId) = runBlocking {
            val cid = repository.saveCategory(CategoryEntity(name = "ألبان للحذف"))
            val pid = database.appDao().insertProduct(
                ProductEntity(
                    barcode = "6333444555666",
                    name = "حليب كامل الدسم",
                    categoryId = cid,
                    categoryName = "ألبان للحذف",
                    unit = "لتر",
                    purchasePrice = 600L,
                    sellPrice = 800L,
                    currentStock = 15.0,
                    minStock = 3.0
                )
            )
            cid to pid
        }
        viewModel.selectCategoryFilter(catId)

        composeRule.setContent {
            ProductFamiliesScreen(viewModel = viewModel, lang = "AR", onNavigate = {})
        }

        waitForTag("delete_family_button_$catId")

        // Tap delete icon in the family table row -> opens confirmation dialog
        composeRule.onNodeWithTag("delete_family_button_$catId")
            .performSemanticsAction(SemanticsActions.OnClick)
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        // Confirm permanent deletion
        composeRule.onNodeWithTag("confirm_delete_family_button")
            .assertIsDisplayed()
            .performClick()

        repeat(20) {
            ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
            composeRule.waitForIdle()
            val remaining = runBlocking { repository.categories.first() }
            if (remaining.none { it.id == catId }) return@repeat
            Thread.sleep(20)
        }

        val remainingCategories = runBlocking { repository.categories.first() }
        assertTrue(
            "Deleted product family must be removed from Room",
            remainingCategories.none { it.id == catId }
        )

        val preservedProduct = runBlocking { repository.getProductById(productId) }
        assertNotNull("Product belonging to deleted family must stay preserved in inventory", preservedProduct)
        assertEquals(0L, preservedProduct!!.categoryId)
        assertEquals("", preservedProduct.categoryName)
        assertEquals(null, viewModel.selectedCategoryIdForFilter.value)
    }
}
