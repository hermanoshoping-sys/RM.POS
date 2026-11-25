package com.example.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CategoryEntity
import com.example.data.PosRepository
import com.example.data.ProductEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.data.UserEntity
import com.example.data.UserRole
import com.example.security.PasswordHasher
import com.example.ui.screens.pos.CheckoutPayDialog
import com.example.ui.screens.pos.ThermalReceiptDialog
import com.example.ui.screens.settings.BackupDataSettingsScreen
import com.example.ui.theme.RmPosTheme
import com.example.ui.viewmodels.BackupSyncViewModel
import com.example.ui.viewmodels.PosViewModel
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end Compose UI tests covering the remaining critical user journeys:
 * 1. Sale -> Payment (CheckoutPayDialog) -> Receipt (ThermalReceiptDialog)
 * 2. Login & Monotonic Lockout (LoginScreen + PasswordHasher elapsedRealtime)
 * 3. Backup Stale Off-Device Reminder & Restore/Export UI (BackupDataSettingsScreen)
 *
 * Paired with [CoreScreensAndDialogsUiTest] (Partial Returns unit-by-unit + License Screen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CriticalUserJourneysUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: PosRepository
    private lateinit var viewModel: PosViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("security_lockout_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        PasswordHasher.init(app)
        PasswordHasher.resetFailedAttempts(app)
        PasswordHasher.resetClockProvidersForTesting()

        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(db.appDao(), db)
        viewModel = PosViewModel(app, repository)
    }

    @After
    fun tearDown() {
        PasswordHasher.resetFailedAttempts(app)
        PasswordHasher.resetClockProvidersForTesting()
        db.close()
    }

    @Test
    fun testSaleCheckoutPaymentAndThermalReceiptDialogJourney() {
        val product = runBlocking {
            val dao = db.appDao()
            dao.insertCategory(CategoryEntity(id = 1L, name = "بقالة"))
            val p = ProductEntity(
                id = 101L,
                barcode = "POS-101",
                name = "زيت زيتون ممتاز 1L",
                categoryId = 1L,
                unit = "قنينة",
                purchasePrice = 4500L,
                sellPrice = 6500L, // 65.00 MAD
                currentStock = 15.0,
                minStock = 2.0
            )
            dao.insertProduct(p)
            p
        }

        // Authenticate an authorized manager user (fail-closed checkout requires a signed-in user)
        val testManager = UserEntity(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            role = UserRole.MANAGER
        )
        viewModel.updateLoginPasswordSecurity(false)
        viewModel.authSecurityViewModel.autoLoginIfPasswordNotRequired(listOf(testManager))

        // Put 2 units in cart (130.00 MAD total)
        viewModel.addToCart(product)
        viewModel.addToCart(product)

        val showCheckout = mutableStateOf(true)
        val completedSaleState = mutableStateOf<Pair<SaleEntity, List<SaleItemEntity>>?>(null)

        composeRule.setContent {
            RmPosTheme {
                if (showCheckout.value) {
                    CheckoutPayDialog(
                        viewModel = viewModel,
                        grandTotal = 130.0,
                        lang = "AR",
                        onDismiss = { showCheckout.value = false }
                    )
                }
                completedSaleState.value?.let { (sale, items) ->
                    ThermalReceiptDialog(
                        viewModel = viewModel,
                        sale = sale,
                        items = items,
                        lang = "AR",
                        onDismiss = { completedSaleState.value = null }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Confirm sale from CheckoutPayDialog
        composeRule.onNodeWithTag("checkout_confirm_sale_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) { !showCheckout.value }
        composeRule.waitForIdle()

        // Verify persisted sale, stock deduction, and items
        val savedSales = runBlocking { db.appDao().getAllSalesList() }
        assertEquals("Exactly one sale must be persisted", 1, savedSales.size)
        val savedSale = savedSales.first()
        assertEquals(13000L, savedSale.totalAmount)
        assertEquals(13000L, savedSale.paidAmount)
        assertEquals("CASH", savedSale.paymentMethod)
        assertEquals(13.0, runBlocking { db.appDao().getProductById(101L)!!.currentStock }, 0.001)

        val savedItems = runBlocking { db.appDao().getSaleItems(savedSale.id) }
        assertEquals(1, savedItems.size)

        // Render ThermalReceiptDialog for the completed sale and verify receipt contents
        composeRule.runOnIdle {
            completedSaleState.value = savedSale to savedItems
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(savedSale.invoiceNumber, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("زيت زيتون ممتاز 1L", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("130.00", substring = true).assertIsDisplayed()
    }

    @Test
    fun testLoginScreenTriggersMonotonicLockoutAndRecoversAfterElapsedTime() {
        val elapsedClock = AtomicLong(10_000L)
        PasswordHasher.elapsedRealtimeProvider = { elapsedClock.get() }
        PasswordHasher.bootCountProvider = { 1 }

        val validPass = "Manager#2026!"
        runBlocking {
            val hash = PasswordHasher.hashAsync(validPass, iterations = 10_000)
            db.appDao().insertUser(
                UserEntity(
                    id = 1L,
                    username = "admin",
                    passwordHash = hash,
                    role = UserRole.MANAGER
                )
            )
        }

        composeRule.setContent {
            RmPosTheme {
                LoginScreen(
                    viewModel = viewModel,
                    lang = "AR"
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("login_username_input").performScrollTo().performTextInput("admin")
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("WrongPass#1")

        // 5 failed attempts trigger 30-second monotonic lockout
        repeat(5) { attempt ->
            composeRule.onNodeWithTag("login_confirm_button").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                if (attempt == 4) PasswordHasher.isLockedOut(app) else viewModel.loginError.value != null
            }
        }

        assertTrue("Monotonic lockout must be active after 5 failed attempts", PasswordHasher.isLockedOut(app))
        composeRule.onNodeWithTag("login_error_banner").performScrollTo().assertIsDisplayed()

        // Even entering the RIGHT password during lockout must remain blocked
        composeRule.onNodeWithTag("login_password_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("login_password_input").performTextInput(validPass)
        composeRule.onNodeWithTag("login_confirm_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Locked-out user must not be signed in", null, viewModel.currentUser.value)

        // Advance genuine monotonic elapsedRealtime past 30s lockout -> valid login succeeds
        elapsedClock.addAndGet(31_000L)
        assertFalse(PasswordHasher.isLockedOut(app))
        composeRule.onNodeWithTag("login_confirm_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            viewModel.currentUser.value?.username == "admin"
        }
        assertNotNull(viewModel.currentUser.value)
        assertEquals("admin", viewModel.currentUser.value?.username)
    }

    @Test
    fun testBackupSettingsScreenDisplaysStaleExternalBackupAlertAndDismissalFlow() {
        val prefs = app.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        val tenDaysAgo = System.currentTimeMillis() - 10L * BackupSyncViewModel.MILLIS_PER_DAY
        prefs.edit()
            .putLong(BackupSyncViewModel.KEY_LAST_EXTERNAL_EXPORT_TS, tenDaysAgo)
            .remove(BackupSyncViewModel.KEY_EXPORT_REMINDER_SNOOZE_UNTIL)
            .commit()
        viewModel.refreshExternalExportReminder()
        assertTrue(viewModel.isExternalExportReminderDue.value)
        assertTrue(viewModel.daysSinceLastExternalExport.value >= 10)

        composeRule.setContent {
            RmPosTheme {
                BackupDataSettingsScreen(
                    viewModel = viewModel,
                    lang = "AR",
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Stale external backup reminder card must alert the user with day count
        composeRule.onNodeWithTag("external_export_reminder_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("10", substring = true).assertIsDisplayed()

        // Marking external export completed clears the stale reminder card
        composeRule.runOnIdle {
            viewModel.markExternalExportCompleted()
        }
        composeRule.waitForIdle()
        assertFalse(viewModel.isExternalExportReminderDue.value)
    }
}
