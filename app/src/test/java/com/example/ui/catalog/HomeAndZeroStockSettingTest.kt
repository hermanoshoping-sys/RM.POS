package com.example.ui.catalog

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CategoryEntity
import com.example.data.GlobalSettingsEntity
import com.example.data.PosRepository
import com.example.data.ProductEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.data.UserEntity
import com.example.data.UserRole
import com.example.ui.screens.MainHomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodels.PosViewModel
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class HomeAndZeroStockSettingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: PosRepository
    private lateinit var viewModel: PosViewModel
    private val testManager = UserEntity(
        id = 1L,
        username = "admin",
        passwordHash = "hash",
        role = UserRole.MANAGER
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE).edit().clear().commit()
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

    @Test
    fun testMainHomeScreenHidesLowStockDashboardBanner() {
        viewModel.updateLoginPasswordSecurity(false)
        viewModel.authSecurityViewModel.autoLoginIfPasswordNotRequired(listOf(testManager))
        composeRule.setContent {
            MainHomeScreen(
                viewModel = viewModel,
                lang = "AR",
                onNavigate = {},
                onOpenDrawer = {}
            )
        }
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("low_stock_dashboard_widget").assertDoesNotExist()
        composeRule.onNodeWithTag("home_tile_pos").assertIsDisplayed()
        composeRule.onNodeWithTag("home_tile_inventory").assertIsDisplayed()
    }

    @Test
    fun testZeroStockSaleSettingSwitchControlsCheckoutWithZeroStock() {
        val productId = runBlocking {
            repository.saveSettings(GlobalSettingsEntity(allowNegativeStock = false))
            val catId = repository.saveCategory(CategoryEntity(name = "مشروبات"))
            database.appDao().insertProduct(
                ProductEntity(
                    barcode = "ZERO-001",
                    name = "منتج بدون مخزون",
                    categoryId = catId,
                    categoryName = "مشروبات",
                    unit = "قطعة",
                    purchasePrice = 500L,
                    sellPrice = 800L,
                    currentStock = 0.0,
                    minStock = 2.0
                )
            )
        }

        // 1. With allowNegativeStock = false, selling a 0-stock product is blocked
        val blockedResult = runCatching {
            runBlocking {
                repository.recordSaleInvoice(
                    sale = SaleEntity(
                        invoiceNumber = "INV-Z1",
                        timestamp = System.currentTimeMillis(),
                        cashierName = "كاشير",
                        customerId = 1L,
                        customerName = "عميل عام",
                        totalAmount = 1600L,
                        discountAmount = 0L,
                        discountType = "VALUE",
                        paidAmount = 1600L,
                        changeAmount = 0L,
                        paymentMethod = "CASH"
                    ),
                    items = listOf(
                        SaleItemEntity(
                            saleId = 0L,
                            productId = productId,
                            productName = "منتج بدون مخزون",
                            quantity = 2.0,
                            unitPrice = 800L,
                            totalLinePrice = 1600L
                        )
                    ),
                    currentUser = testManager
                )
            }
        }
        assertTrue("Expected zero-stock sale to be blocked when setting is OFF", blockedResult.isFailure)

        // 2. Toggle switch ON in SettingsScreen -> updates state and DB
        composeRule.setContent {
            SettingsScreen(viewModel = viewModel, lang = "AR", onNavigate = {})
        }
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        assertFalse(viewModel.settings.value.allowNegativeStock)
        composeRule.onNodeWithTag("settings_allow_zero_stock_switch")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()

        assertTrue(viewModel.settings.value.allowNegativeStock)

        // 3. Selling the 0-stock product now succeeds and deducts stock to -2.0
        runBlocking {
            val persistedSettings = repository.getSettings()
            assertTrue("Room settings must persist allowNegativeStock=true", persistedSettings.allowNegativeStock)

            repository.recordSaleInvoice(
                sale = SaleEntity(
                    invoiceNumber = "INV-Z2",
                    timestamp = System.currentTimeMillis(),
                    cashierName = "كاشير",
                    customerId = 1L,
                    customerName = "عميل عام",
                    totalAmount = 1600L,
                    discountAmount = 0L,
                    discountType = "VALUE",
                    paidAmount = 1600L,
                    changeAmount = 0L,
                    paymentMethod = "CASH"
                ),
                items = listOf(
                    SaleItemEntity(
                        saleId = 0L,
                        productId = productId,
                        productName = "منتج بدون مخزون",
                        quantity = 2.0,
                        unitPrice = 800L,
                        totalLinePrice = 1600L
                    )
                ),
                currentUser = testManager
            )
            val updatedProduct = repository.getProductById(productId)!!
            assertEquals(-2.0, updatedProduct.currentStock, 0.001)
        }
    }
}
