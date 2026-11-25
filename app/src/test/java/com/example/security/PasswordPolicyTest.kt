package com.example.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.GlobalSettingsEntity
import com.example.data.PosRepository
import com.example.data.SaleEntity
import com.example.printer.EscPosReceiptEncoder
import com.example.printer.HtmlInvoiceAndReceiptFormatter
import com.example.printer.ThermalPrinterConfig
import com.example.printer.ThermalReceiptData
import com.example.printer.ThermalReceiptItem
import com.example.printer.ThermalReceiptSummaryBuilder
import com.example.ui.screens.buildMapUserAgent
import com.example.ui.screens.resolveMapTileProvider
import com.example.ui.viewmodels.AuthSecurityViewModel
import com.example.ui.viewmodels.BackupSyncViewModel
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PasswordPolicyTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: PosRepository
    private lateinit var mainScope: CoroutineScope
    private lateinit var ioDispatcher: kotlinx.coroutines.ExecutorCoroutineDispatcher
    private lateinit var mainDispatcher: kotlinx.coroutines.ExecutorCoroutineDispatcher

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("test_pw_policy_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        PasswordHasher.init(context)
        PasswordHasher.resetFailedAttempts(context)

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(database.appDao(), database)

        ioDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        mainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        mainScope = CoroutineScope(SupervisorJob() + mainDispatcher)
    }

    @After
    fun tearDown() {
        mainScope.cancel()
        database.close()
        ioDispatcher.close()
        mainDispatcher.close()
    }

    @Test
    fun testUnifiedPasswordPolicyRulesAcrossLanguages() {
        assertEquals(8, PasswordPolicy.MIN_LENGTH)

        // Empty / blank
        val emptyRes = PasswordPolicy.validate("   ", lang = "AR")
        assertFalse(emptyRes.isValid)
        assertEquals(PasswordPolicy.FailureReason.EMPTY, emptyRes.failureReason)
        assertNotNull(emptyRes.errorMessage)

        // Too short (< 8 chars, including old 4-7 char passwords)
        for (shortPw in listOf("1", "123", "4928", "ab89x", "749205", "Store#2")) {
            val res = PasswordPolicy.validate(shortPw, lang = "FR")
            assertFalse("Expected $shortPw to fail min length 8", res.isValid)
            assertEquals(PasswordPolicy.FailureReason.TOO_SHORT, res.failureReason)
            assertTrue("Error message must derive MIN_LENGTH=8", res.errorMessage!!.contains("8"))
        }

        // Common weak, repeated, ascending/descending sequences (8+ chars)
        for (weakPw in listOf("12345678", "87654321", "00000000", "99999999", "admin123", "qwerty123", "azerty123", "23456789", "abcdefgh", "password")) {
            val res = PasswordPolicy.validate(weakPw, lang = "EN")
            assertFalse("Expected $weakPw to be rejected as weak", res.isValid)
            assertEquals(PasswordPolicy.FailureReason.TOO_WEAK, res.failureReason)
            assertTrue(PasswordPolicy.isWeak(weakPw))
        }

        // Confirmation mismatch
        val mismatchRes = PasswordPolicy.validate("Store#2026", "Store#2027", lang = "AR")
        assertFalse(mismatchRes.isValid)
        assertEquals(PasswordPolicy.FailureReason.MISMATCH, mismatchRes.failureReason)

        // Valid strong passwords (8+ non-trivial numeric and alphanumeric)
        for (validPw in listOf("74920581", "Store#2026", "RmPos@8899")) {
            val res = PasswordPolicy.validate(validPw, validPw, lang = "AR")
            assertTrue("Expected $validPw to be valid", res.isValid)
            assertNull(res.failureReason)
            assertNull(res.errorMessage)
        }
    }

    @Test
    fun testAuthSecurityViewModelEnforcesUnifiedPolicyAcrossAllFlows() = runBlocking {
        val prefs = context.getSharedPreferences("test_pw_policy_prefs", Context.MODE_PRIVATE)
        val vm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            ioDispatcher = ioDispatcher
        )

        // 1. setupInitialAdmin rejects short (< 8) and weak (12345678) passwords
        var shortSetupSucceeded = false
        vm.setupInitialAdmin("كريم", "749205", onSuccess = { shortSetupSucceeded = true })
        delay(200L)
        assertFalse("setupInitialAdmin must reject 6-char password", shortSetupSucceeded)
        assertNull(vm.currentUser.value)
        assertNotNull(vm.loginError.value)

        var weakSetupSucceeded = false
        vm.setupInitialAdmin("كريم", "12345678", onSuccess = { weakSetupSucceeded = true })
        delay(200L)
        assertFalse("setupInitialAdmin must reject weak password 12345678", weakSetupSucceeded)
        assertNull(vm.currentUser.value)

        // Valid 8+ setup succeeds
        val setupOk = CompletableDeferred<Unit>()
        vm.setupInitialAdmin("كريم", "RmPos#2026", onSuccess = { setupOk.complete(Unit) })
        withTimeout(15_000L) { setupOk.await() }
        assertEquals("كريم", vm.currentUser.value?.username)

        // 2. setProgramLoginPassword enforces 8+ and non-weak
        val loginShort = CompletableDeferred<Boolean>()
        vm.setProgramLoginPassword("Ab48291", "Ab48291") { ok, _ -> loginShort.complete(ok) }
        assertFalse(withTimeout(5_000L) { loginShort.await() })

        val loginSeq = CompletableDeferred<Boolean>()
        vm.setProgramLoginPassword("23456789", "23456789") { ok, _ -> loginSeq.complete(ok) }
        assertFalse(withTimeout(5_000L) { loginSeq.await() })

        // 3. changePassword enforces 8+ AND rejects weak passwords
        val changeShort = CompletableDeferred<Boolean>()
        vm.changePassword("RmPos#2026", "482910", "482910") { ok, _ -> changeShort.complete(ok) }
        assertFalse(withTimeout(5_000L) { changeShort.await() })

        val changeWeak = CompletableDeferred<Boolean>()
        vm.changePassword("RmPos#2026", "11111111", "11111111") { ok, _ -> changeWeak.complete(ok) }
        assertFalse(withTimeout(5_000L) { changeWeak.await() })

        val changeValid = CompletableDeferred<Boolean>()
        vm.changePassword("RmPos#2026", "Secure#789", "Secure#789") { ok, _ -> changeValid.complete(ok) }
        assertTrue(withTimeout(15_000L) { changeValid.await() })

        // 4. Delete & Edit action passwords enforce unified 8+ policy when non-blank
        val deleteShortErr = CompletableDeferred<String>()
        vm.updateDeleteSecurity(
            require = true,
            newPass = "Del#99",
            onError = { deleteShortErr.complete(it) }
        )
        assertTrue(withTimeout(5_000L) { deleteShortErr.await() }.contains("8"))

        val editSeqErr = CompletableDeferred<String>()
        vm.updateEditSecurity(
            require = true,
            newPass = "87654321",
            onError = { editSeqErr.complete(it) }
        )
        assertTrue(withTimeout(5_000L) { editSeqErr.await() }.isNotBlank())

        val editValidDone = CompletableDeferred<Unit>()
        vm.updateEditSecurity(
            require = true,
            newPass = "EditGuard#99",
            onComplete = { editValidDone.complete(Unit) }
        )
        withTimeout(15_000L) { editValidDone.await() }

        val verifyEdit = CompletableDeferred<Boolean>()
        vm.verifyEditPassword("EditGuard#99", emptyList()) { verifyEdit.complete(it) }
        assertTrue(withTimeout(15_000L) { verifyEdit.await() })
    }

    @Test
    fun testBackupSyncViewModelEnforcesUnifiedPasswordPolicy() = runBlocking {
        val prefs = context.getSharedPreferences("test_pw_policy_prefs", Context.MODE_PRIVATE)
        val backupVm = BackupSyncViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            languageProvider = { "AR" }
        )

        val shortRes = CompletableDeferred<Pair<Boolean, String>>()
        backupVm.performGoogleDriveBackup(
            encryptionPassword = "Abc#123",
            onComplete = { ok, msg -> shortRes.complete(ok to msg) }
        )
        val (shortOk, shortMsg) = withTimeout(5_000L) { shortRes.await() }
        assertFalse("Backup creation must reject < 8 char password", shortOk)
        assertTrue("Error should explain 8-char requirement", shortMsg.contains("8"))

        val weakRes = CompletableDeferred<Pair<Boolean, String>>()
        backupVm.performGoogleDriveBackup(
            encryptionPassword = "12345678",
            onComplete = { ok, msg -> weakRes.complete(ok to msg) }
        )
        val (weakOk, _) = withTimeout(5_000L) { weakRes.await() }
        assertFalse("Backup creation must reject weak 8-char password", weakOk)
    }

    @Test
    fun testReceiptsHandleBlankCashierAndMapUserAgentReadsBuildConfigVersion() {
        val config = ThermalPrinterConfig(paperWidthMm = 80, cutPaper = false, printQrCode = false)
        val blankCashierReceipt = ThermalReceiptData(
            storeName = "متجر النور",
            invoiceNumber = "INV-100",
            cashierName = "",
            items = listOf(ThermalReceiptItem("منتج 1", 1.0, 10.0, 10.0)),
            subtotal = 10.0,
            grandTotal = 10.0,
            paidAmount = 10.0
        )

        val plainBlank = EscPosReceiptEncoder.generatePlainTextReceipt(blankCashierReceipt, config)
        assertFalse("Plain receipt must omit empty VENDEUR label", plainBlank.contains("VENDEUR:"))
        assertFalse("Plain receipt must omit empty Arabic cashier label", plainBlank.contains("البائع:"))

        val bytesBlankText = String(EscPosReceiptEncoder.generateThermalReceiptBytes(blankCashierReceipt, config), Charsets.UTF_8)
        assertFalse("ESC/POS receipt must omit empty cashier line", bytesBlankText.contains("البائع:"))

        val namedCashierReceipt = blankCashierReceipt.copy(cashierName = "أحمد")
        val plainNamedAr = EscPosReceiptEncoder.generatePlainTextReceipt(namedCashierReceipt, config)
        assertTrue("Plain Arabic receipt must include cashier when present", plainNamedAr.contains("البائع: أحمد"))
        val plainNamedFr = EscPosReceiptEncoder.generatePlainTextReceipt(namedCashierReceipt, config.copy(language = "FR"))
        assertTrue("Plain French receipt must include cashier when present", plainNamedFr.contains("Vendeur: أحمد"))

        val saleBlankCashier = SaleEntity(
            invoiceNumber = "0005",
            timestamp = 1_770_000_000_000L,
            cashierName = "",
            totalAmount = 1000L,
            discountAmount = 0L,
            discountType = "NONE",
            paidAmount = 1000L,
            changeAmount = 0L,
            paymentMethod = "CASH"
        )
        val htmlBlank = HtmlInvoiceAndReceiptFormatter.buildHtmlInvoice(
            sale = saleBlankCashier,
            items = emptyList(),
            settings = GlobalSettingsEntity(id = 1, shopName = "متجر النور", showCashierName = true),
            context = context
        )
        assertFalse("HTML invoice must omit empty cashier row", htmlBlank.contains("<tr><td>البائع :</td></tr>"))

        val htmlNamed = HtmlInvoiceAndReceiptFormatter.buildHtmlInvoice(
            sale = saleBlankCashier.copy(cashierName = "أحمد"),
            items = emptyList(),
            settings = GlobalSettingsEntity(id = 1, shopName = "متجر النور", showCashierName = true),
            context = context
        )
        assertTrue("HTML invoice must render cashier row when non-blank", htmlNamed.contains("<tr><td>البائع :</td></tr>"))
        assertTrue(htmlNamed.contains("<tr><td>أحمد</td></tr>"))

        val summaryBlank = ThermalReceiptSummaryBuilder.buildTransactionSummaryReceipt(
            cartItems = emptyList(),
            subtotal = 10.0,
            discountAmount = 0.0,
            netTotal = 10.0,
            paymentMethod = "CASH",
            receivedAmount = 10.0,
            changeDue = 0.0,
            cashierName = "   ",
            customerName = null,
            settings = GlobalSettingsEntity(id = 1, shopName = "متجر النور", showCashierName = true),
            lang = "AR"
        )
        assertEquals("", summaryBlank.cashierName)
        assertFalse(
            "Summary receipt with blank cashier must not print VENDEUR",
            EscPosReceiptEncoder.generatePlainTextReceipt(summaryBlank, config).contains("VENDEUR:")
        )

        val userAgent = buildMapUserAgent()
        assertTrue("Map User-Agent must read BuildConfig.VERSION_NAME", userAgent.contains("RMPOS-Android/${BuildConfig.VERSION_NAME}"))
        assertTrue("Map User-Agent must include BuildConfig.APPLICATION_ID", userAgent.contains(BuildConfig.APPLICATION_ID))

        assertNull(
            "Empty MAP_TILE_URL_TEMPLATE must NOT fall back to public OSM tile server",
            resolveMapTileProvider(configuredUrl = "", configuredAttribution = "")
        )
        assertNull(
            "Public tile.openstreetmap.org must be rejected to comply with OSM Tile Usage Policy",
            resolveMapTileProvider(
                configuredUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                configuredAttribution = "&copy; OpenStreetMap contributors"
            )
        )
        assertNull(
            "Subdomain public OSM tile URLs must also be rejected",
            resolveMapTileProvider(
                configuredUrl = "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
                configuredAttribution = ""
            )
        )

        val commercialProvider = resolveMapTileProvider(
            configuredUrl = "https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=TEST_KEY",
            configuredAttribution = "&copy; MapTiler &copy; OpenStreetMap contributors"
        )
        assertNotNull("Licensed commercial HTTPS tile provider must be accepted", commercialProvider)
        val (commercialUrl, commercialAttr) = commercialProvider!!
        assertEquals("https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=TEST_KEY", commercialUrl)
        assertTrue(commercialAttr.contains("MapTiler"))
    }
}
