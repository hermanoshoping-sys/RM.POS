package com.example.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.CategoryEntity
import com.example.data.PosRepository
import com.example.data.ProductEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.security.LicenseSecurityManager
import com.example.security.TestLicenseTokenSigner
import com.example.ui.components.SecureLicenseManagerCard
import com.example.ui.screens.settings.BackupDataSettingsScreen
import com.example.ui.theme.RmPosTheme
import com.example.ui.viewmodels.PosViewModel
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoreScreensAndDialogsUiTest {

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
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(db.appDao(), db)
        viewModel = PosViewModel(app, repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBackupDataSettingsScreenDisplaysOffDeviceSafWarningAndUpdatesWhenFolderConfigured() {
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

        composeRule.onNodeWithTag("external_saf_backup_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("external_saf_local_warning").assertIsDisplayed()
        composeRule.onNodeWithTag("select_external_backup_folder_button").assertIsDisplayed()
        assertFalse(viewModel.hasExternalBackupFolder.value)

        // Simulate user selecting an external SAF folder via OpenDocumentTree
        val fakeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AGoogleDrive_RMPOS")
        composeRule.runOnIdle {
            assertTrue(viewModel.configureExternalBackupFolder(fakeUri))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("external_saf_folder_label")
            .assertIsDisplayed()
            .assertTextContains("GoogleDrive_RMPOS", substring = true)
        composeRule.onNodeWithTag("clear_external_backup_folder_button")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertFalse(viewModel.hasExternalBackupFolder.value)
        composeRule.onNodeWithTag("external_saf_local_warning").assertIsDisplayed()
    }

    @Test
    fun testSaleReturnDialogDisplaysDiscountedNetRefundPreview() {
        val saleId = runBlocking {
            val dao = db.appDao()
            dao.insertCategory(CategoryEntity(id = 1L, name = "مشروبات"))
            dao.insertProduct(
                ProductEntity(
                    id = 10L,
                    barcode = "61110010",
                    name = "قهوة محمصة",
                    categoryId = 1L,
                    unit = "علبة",
                    purchasePrice = 50.0,
                    sellPrice = 100.0,
                    currentStock = 20.0,
                    minStock = 2.0
                )
            )
            // 2 units * 100.00 = 200.00, line discount = 20.00 -> line net 180.00,
            // invoice-level discount = 18.00 -> net paid = 162.00 (81.00 per unit)
            val id = dao.insertSale(
                SaleEntity(
                    invoiceNumber = "0101",
                    timestamp = 1_750_000_000_000L,
                    cashierName = "admin",
                    customerId = 0L,
                    customerName = "",
                    totalAmount = 16200L,
                    discountAmount = 1800L,
                    discountType = "VALUE",
                    paidAmount = 16200L,
                    changeAmount = 0L,
                    paymentMethod = "CASH"
                )
            )
            dao.insertSaleItem(
                SaleItemEntity(
                    saleId = id,
                    productId = 10L,
                    productName = "قهوة محمصة",
                    quantity = 2.0,
                    unitPrice = 100.0,
                    discountValue = 20.0,
                    totalLinePrice = 180.0
                )
            )
            id
        }

        val sale = runBlocking { db.appDao().getSaleById(saleId)!! }

        composeRule.setContent {
            RmPosTheme {
                SaleReturnDialog(
                    target = sale,
                    viewModel = viewModel,
                    lang = "AR",
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sale_return_qty_input").assertIsDisplayed()
        composeRule.onNodeWithTag("sale_return_remaining_qty_text")
            .assertIsDisplayed()
            .assertTextContains("2", substring = true)
        // Default return qty = 1 unit -> net discounted refund = 81.00 (NOT gross 100.00)
        composeRule.onNodeWithTag("sale_return_net_refund_text")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("81.00", substring = true)
        composeRule.onNodeWithTag("sale_return_confirm_button")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun testSaleReturnDialogUnitByUnitPartialReturnsSumExactlyToLineNet() {
        data class ReturnScenario(
            val label: String,
            val units: Int,
            val lineNetCents: Long,
            val expectedRefundsCents: List<Long>
        )

        val scenarios = listOf(
            // 2 units @ 10.01 MAD -> 5.01 + 5.00 = 10.01
            ReturnScenario("2_units_10_01", units = 2, lineNetCents = 1001L, expectedRefundsCents = listOf(501L, 500L)),
            // 3 units @ 10.01 MAD -> 3.34 + 3.33 + 3.34 = 10.01
            ReturnScenario("3_units_10_01", units = 3, lineNetCents = 1001L, expectedRefundsCents = listOf(334L, 333L, 334L)),
            // 3 units @ 10.00 MAD -> 3.33 + 3.34 + 3.33 = 10.00
            ReturnScenario("3_units_10_00", units = 3, lineNetCents = 1000L, expectedRefundsCents = listOf(333L, 334L, 333L))
        )

        val dialogTargetState = androidx.compose.runtime.mutableStateOf<SaleEntity?>(null)
        val dialogGeneration = androidx.compose.runtime.mutableStateOf(0)

        composeRule.setContent {
            RmPosTheme {
                val activeSale = dialogTargetState.value
                val gen = dialogGeneration.value
                if (activeSale != null) {
                    androidx.compose.runtime.key(activeSale.id, gen) {
                        SaleReturnDialog(
                            target = activeSale,
                            viewModel = viewModel,
                            lang = "AR",
                            onDismiss = { dialogTargetState.value = null }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val dao = db.appDao()
        runBlocking { dao.insertCategory(CategoryEntity(id = 1L, name = "عام")) }

        scenarios.forEachIndexed { idx, scenario ->
            val customerId = runBlocking {
                dao.insertCustomer(
                    com.example.data.CustomerEntity(
                        name = "زبون ${scenario.label}",
                        phone = "06000010$idx",
                        balance = scenario.lineNetCents,
                        totalPurchases = scenario.lineNetCents
                    )
                )
            }
            val productId = runBlocking {
                dao.insertProduct(
                    ProductEntity(
                        barcode = "PARTIAL-$idx",
                        name = "منتج ${scenario.label}",
                        categoryId = 1L,
                        unit = "قطعة",
                        purchasePrice = 100L,
                        sellPrice = 600L,
                        currentStock = 10.0,
                        minStock = 1.0
                    )
                )
            }
            val grossCents = 600L * scenario.units
            val lineDiscountCents = grossCents - scenario.lineNetCents
            val saleId = runBlocking {
                val id = dao.insertSale(
                    SaleEntity(
                        invoiceNumber = "RET-$idx",
                        timestamp = 1_750_000_000_000L + idx * 1_000L,
                        cashierName = "admin",
                        customerId = customerId,
                        customerName = "زبون ${scenario.label}",
                        totalAmount = scenario.lineNetCents,
                        discountAmount = 0L,
                        discountType = "VALUE",
                        paidAmount = 0L,
                        changeAmount = 0L,
                        paymentMethod = "DEBT"
                    )
                )
                dao.insertSaleItem(
                    SaleItemEntity(
                        saleId = id,
                        productId = productId,
                        productName = "منتج ${scenario.label}",
                        quantity = scenario.units.toDouble(),
                        unitPrice = 600L,
                        discountValue = lineDiscountCents,
                        totalLinePrice = scenario.lineNetCents
                    )
                )
                id
            }
            val sale = runBlocking { dao.getSaleById(saleId)!! }

            var runningBalance = scenario.lineNetCents
            var totalRefundedCents = 0L

            for (step in 0 until scenario.units) {
                val expectedRemaining = scenario.units - step
                val expectedStepCents = scenario.expectedRefundsCents[step]
                val expectedStepText = String.format(java.util.Locale.US, "%.2f", expectedStepCents / 100.0)

                composeRule.runOnIdle {
                    dialogGeneration.value += 1
                    dialogTargetState.value = sale
                }
                composeRule.waitUntil(timeoutMillis = 5_000L) {
                    runCatching {
                        composeRule.onNodeWithTag("sale_return_remaining_qty_text").assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                }

                // Dialog must display the exact remaining returnable quantity and exact cumulative-diff preview
                composeRule.onNodeWithTag("sale_return_remaining_qty_text")
                    .performScrollTo()
                    .assertTextContains(expectedRemaining.toString(), substring = true)
                composeRule.onNodeWithTag("sale_return_net_refund_text")
                    .performScrollTo()
                    .assertTextContains(expectedStepText, substring = true)

                // Submit return of 1 unit through the dialog itself
                composeRule.onNodeWithTag("sale_return_confirm_button")
                    .performScrollTo()
                    .performClick()
                composeRule.waitUntil(timeoutMillis = 5_000L) {
                    dialogTargetState.value == null
                }
                composeRule.waitForIdle()

                val newBalance = runBlocking { dao.getCustomerById(customerId)!!.balance }
                val stepDelta = runningBalance - newBalance
                org.junit.Assert.assertEquals(
                    "Step ${step + 1} for ${scenario.label} must deduct exact cent refund",
                    expectedStepCents,
                    stepDelta
                )
                runningBalance = newBalance
                totalRefundedCents += stepDelta
            }

            // Sum of unit-by-unit returns through the dialog MUST equal exact line net down to 1 cent
            org.junit.Assert.assertEquals(
                "Total unit-by-unit refund for ${scenario.label} must equal line net",
                scenario.lineNetCents,
                totalRefundedCents
            )
            org.junit.Assert.assertEquals(0L, runBlocking { dao.getCustomerById(customerId)!!.balance })
            org.junit.Assert.assertEquals(
                10.0 + scenario.units,
                runBlocking { dao.getProductById(productId)!!.currentStock },
                0.001
            )
        }
    }

    @Test
    fun testSecureLicenseManagerCardRendersActivationControlsAndOnlineSyncDuringClockSuspension() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()
        val privB64 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
        val deviceId = viewModel.getDeviceId()
        val sampleToken = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = System.currentTimeMillis() + 30L * 86_400_000L,
            privateKeyBase64 = privB64
        )
        assertTrue(sampleToken.contains("."))

        // Trigger genuine clock rollback suspension (stored floor in future)
        val prefs = app.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        val futureFloor = System.currentTimeMillis() + 30L * 86_400_000L
        prefs.edit()
            .putString("license_number", sampleToken)
            .putLong(com.example.security.LicenseClockGuard.KEY_MONOTONIC_FLOOR, futureFloor)
            .commit()
        viewModel.checkLicenseExpirationWithAntiRollback()
        assertTrue("ViewModel must enter clock rollback suspension", viewModel.isClockRollbackSuspended.value)

        composeRule.setContent {
            RmPosTheme {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    SecureLicenseManagerCard(
                        viewModel = viewModel,
                        lang = "AR"
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("license_manager_card").assertIsDisplayed()
        composeRule.onNodeWithTag("clock_rollback_recovery_box").performScrollTo().assertIsDisplayed()
        // Online verification button MUST be visible during clock rollback suspension
        composeRule.onNodeWithTag("renew_license_grace_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("activation_key_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("activate_license_button").performScrollTo().assertIsDisplayed()
        assertFalse(LicenseSecurityManager.verifyLicenseToken("INVALID", deviceId).isValid)
    }
}
