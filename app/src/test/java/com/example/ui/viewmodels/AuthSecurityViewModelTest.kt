package com.example.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PosRepository
import com.example.security.LicenseSecurityManager
import com.example.security.PasswordHasher
import com.example.security.TestLicenseTokenSigner
import com.example.ui.theme.Bilingual
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.text.NumberFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.ContinuationInterceptor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthSecurityViewModelTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var database: AppDatabase
    private lateinit var repository: PosRepository
    private lateinit var mainScope: CoroutineScope
    private lateinit var trackedIoExecutor: java.util.concurrent.ExecutorService
    private lateinit var trackedIoDispatcher: kotlinx.coroutines.ExecutorCoroutineDispatcher
    private lateinit var mainExecutor: java.util.concurrent.ExecutorService
    private lateinit var mainDispatcher: kotlinx.coroutines.ExecutorCoroutineDispatcher

    private val ioThreadPrefix = "auth-io-worker-"
    private val mainThreadPrefix = "auth-main-thread-"
    private val ioCounter = AtomicInteger(0)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("test_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        PasswordHasher.init(context)
        PasswordHasher.resetFailedAttempts(context)

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(database.appDao(), database)

        trackedIoExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "$ioThreadPrefix${ioCounter.incrementAndGet()}")
        }
        trackedIoDispatcher = trackedIoExecutor.asCoroutineDispatcher()

        mainExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "${mainThreadPrefix}1")
        }
        mainDispatcher = mainExecutor.asCoroutineDispatcher()
        mainScope = CoroutineScope(SupervisorJob() + mainDispatcher)
    }

    @After
    fun tearDown() {
        mainScope.cancel()
        database.close()
        trackedIoDispatcher.close()
        mainDispatcher.close()
    }

    @Test
    fun testSetupAdminAndLoginRunHashingAndVerifyOnIoDispatcherAndCallbackOnScopeDispatcher() = runBlocking {
        val observedCallbackThreads = mutableListOf<String>()
        val ioDispatchCount = AtomicInteger(0)

        // Wrap trackedIoDispatcher to count dispatches directly through ioDispatcher
        val countingIoDispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                ioDispatchCount.incrementAndGet()
                trackedIoDispatcher.dispatch(context, block)
            }
        }

        val vm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            ioDispatcher = countingIoDispatcher
        )

        val setupDone = CompletableDeferred<Unit>()
        vm.setupInitialAdmin(
            username = "manager1",
            pin = "StrongPin#987",
            onSuccess = {
                observedCallbackThreads.add(Thread.currentThread().name)
                setupDone.complete(Unit)
            }
        )
        withTimeout(15_000L) { setupDone.await() }

        assertNotNull(vm.currentUser.value)
        assertEquals("manager1", vm.currentUser.value?.username)
        assertTrue("Expected ioDispatcher dispatches during init + setupInitialAdmin", ioDispatchCount.get() >= 2)
        assertTrue(
            "Expected onSuccess callback on caller/main dispatcher, was ${observedCallbackThreads.last()}",
            observedCallbackThreads.last().startsWith(mainThreadPrefix)
        )

        vm.logout()
        assertNull(vm.currentUser.value)

        val dispatchesBeforeLogin = ioDispatchCount.get()
        val loginDone = CompletableDeferred<Unit>()
        vm.login("manager1", "StrongPin#987") {
            observedCallbackThreads.add(Thread.currentThread().name)
            loginDone.complete(Unit)
        }
        withTimeout(15_000L) { loginDone.await() }

        assertNotNull(vm.currentUser.value)
        assertNull(vm.loginError.value)
        assertTrue(
            "Expected login to offload verify onto ioDispatcher",
            ioDispatchCount.get() > dispatchesBeforeLogin
        )
        assertTrue(
            "Expected login onSuccess callback on caller/main dispatcher",
            observedCallbackThreads.last().startsWith(mainThreadPrefix)
        )
    }

    @Test
    fun testChangePasswordAndActionSecurityRunOnIoDispatcher() = runBlocking {
        val ioDispatchCount = AtomicInteger(0)
        val countingIoDispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                ioDispatchCount.incrementAndGet()
                trackedIoDispatcher.dispatch(context, block)
            }
        }

        val vm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            ioDispatcher = countingIoDispatcher
        )

        val setupDone = CompletableDeferred<Unit>()
        vm.setupInitialAdmin("admin_sec", "InitialPass#1", onSuccess = { setupDone.complete(Unit) })
        withTimeout(15_000L) { setupDone.await() }

        val dispatchesBeforeChange = ioDispatchCount.get()
        val changeDone = CompletableDeferred<Boolean>()
        vm.changePassword("WrongPass", "NewPass#2", "NewPass#2") { ok, _ ->
            changeDone.complete(ok)
        }
        assertFalse(withTimeout(15_000L) { changeDone.await() })
        assertTrue(ioDispatchCount.get() > dispatchesBeforeChange)

        val changeOkDone = CompletableDeferred<Boolean>()
        vm.changePassword("InitialPass#1", "NewPass#2", "NewPass#2") { ok, _ ->
            changeOkDone.complete(ok)
        }
        assertTrue(withTimeout(15_000L) { changeOkDone.await() })

        val backupVerifyDone = CompletableDeferred<Boolean>()
        vm.verifyBackupPassword("NewPass#2") { backupVerifyDone.complete(it) }
        assertTrue(withTimeout(15_000L) { backupVerifyDone.await() })

        val deleteSecDone = CompletableDeferred<Unit>()
        vm.updateDeleteSecurity(true, "DelSecret#99") { deleteSecDone.complete(Unit) }
        withTimeout(15_000L) { deleteSecDone.await() }

        val verifyDelValid = CompletableDeferred<Boolean>()
        vm.verifyDeletePassword("DelSecret#99", emptyList()) { verifyDelValid.complete(it) }
        assertTrue(withTimeout(15_000L) { verifyDelValid.await() })

        val verifyDelInvalid = CompletableDeferred<Boolean>()
        vm.verifyDeletePassword("WrongDel", emptyList()) { verifyDelInvalid.complete(it) }
        assertFalse(withTimeout(15_000L) { verifyDelInvalid.await() })
    }

    @Test
    fun testProgramLoginPasswordToggleAndSetPasswordAndAutoLogin() = runBlocking {
        val vm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            ioDispatcher = trackedIoDispatcher
        )

        val setupDone = CompletableDeferred<Unit>()
        vm.setupInitialAdmin("admin", "StrongPass#2026", onSuccess = { setupDone.complete(Unit) })
        withTimeout(15_000L) { setupDone.await() }

        // Default is true
        assertTrue(vm.requirePasswordOnLogin.value)

        // Disable login password requirement and recreate VM simulating cold start
        vm.updateLoginPasswordSecurity(false)
        assertFalse(prefs.getBoolean("sec_require_pass_login", true))

        val coldStartVm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            ioDispatcher = trackedIoDispatcher
        )
        assertFalse(coldStartVm.requirePasswordOnLogin.value)
        val usersList = repository.getAllUsersList()
        coldStartVm.autoLoginIfPasswordNotRequired(usersList)
        assertNotNull("Expected auto-login when password on login is disabled", coldStartVm.currentUser.value)
        assertEquals("admin", coldStartVm.currentUser.value?.username)

        // Explicit logout must not immediately auto-login again in same session
        coldStartVm.logout()
        assertNull(coldStartVm.currentUser.value)
        coldStartVm.autoLoginIfPasswordNotRequired(usersList)
        assertNull("Explicit logout should stay on login screen", coldStartVm.currentUser.value)

        // Reject weak password and mismatch
        val weakResult = CompletableDeferred<Boolean>()
        coldStartVm.setProgramLoginPassword("1234", "1234") { ok, _ -> weakResult.complete(ok) }
        assertFalse(withTimeout(15_000L) { weakResult.await() })

        val mismatchResult = CompletableDeferred<Boolean>()
        coldStartVm.setProgramLoginPassword("NewLogin#99", "Different#99") { ok, _ -> mismatchResult.complete(ok) }
        assertFalse(withTimeout(15_000L) { mismatchResult.await() })

        // Valid setProgramLoginPassword updates hash and re-enables requirePasswordOnLogin
        val setOk = CompletableDeferred<Boolean>()
        coldStartVm.setProgramLoginPassword("NewLogin#99", "NewLogin#99") { ok, _ -> setOk.complete(ok) }
        assertTrue(withTimeout(15_000L) { setOk.await() })
        assertTrue(coldStartVm.requirePasswordOnLogin.value)
        assertTrue(prefs.getBoolean("sec_require_pass_login", false))

        coldStartVm.logout()
        val loginNewDone = CompletableDeferred<Unit>()
        coldStartVm.login("admin", "NewLogin#99") { loginNewDone.complete(Unit) }
        withTimeout(15_000L) { loginNewDone.await() }
        assertNotNull(coldStartVm.currentUser.value)
    }

    @Test
    fun testSettingsThreeLanguageCycleAndPersistenceAcrossRestart() = runBlocking {
        val settingsVm = SettingsHardwareViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope
        )
        assertEquals("AR", settingsVm.currentLanguage.value)

        settingsVm.toggleLanguage()
        assertEquals("FR", settingsVm.currentLanguage.value)

        settingsVm.toggleLanguage()
        assertEquals("EN", settingsVm.currentLanguage.value)

        // Wait briefly for coroutine write to Room to complete
        kotlinx.coroutines.delay(250L)

        // Recreate SettingsHardwareViewModel simulating app restart
        val restartedVm = SettingsHardwareViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope
        )
        restartedVm.initializeSettings()
        assertEquals("EN", restartedVm.currentLanguage.value)

        restartedVm.toggleLanguage()
        assertEquals("AR", restartedVm.currentLanguage.value)
    }

    @Test
    fun testArabicLocaleKeepsWesternDigitsOnly() {
        val arLocale = Bilingual.localeFor("AR")
        val formattedDecimal = String.format(arLocale, "%.2f", 1234.56)
        val formattedNumber = NumberFormat.getNumberInstance(arLocale).format(98765)

        val arabicIndicRange = '\u0660'..'\u0669'
        val extendedArabicIndicRange = '\u06F0'..'\u06F9'
        assertFalse("Expected Western digits only in formatted decimal: $formattedDecimal", formattedDecimal.any { it in arabicIndicRange || it in extendedArabicIndicRange })
        assertFalse("Expected Western digits only in NumberFormat: $formattedNumber", formattedNumber.any { it in arabicIndicRange || it in extendedArabicIndicRange })
        assertTrue(formattedDecimal.contains("1234"))
        assertTrue(formattedNumber.contains("98") && formattedNumber.contains("765"))

        assertEquals("2026-11-15", Bilingual.toWesternDigits("٢٠٢٦-١١-١٥"))
        assertEquals("123.45", Bilingual.formatNumber(123.45, 2))
    }

    @Test
    fun testLicenseVerificationRejectsUnsignedKeysWithoutLeakingFormatTemplate() {
        val result = LicenseSecurityManager.verifyLicenseToken(
            "RM.POS@BFCCFBED@60267985-365-20261118",
            "BF60CC26FB79ED85"
        )
        assertFalse(result.isValid)
        val err = result.errorMessage.orEmpty()
        assertFalse("Error should not expose YYYYMMDD template: $err", err.contains("YYYY"))
        assertFalse("Error should not expose RM.POS@ prefix template: $err", err.contains("RM.POS@"))
    }

    @Test
    fun testBootReverificationRevokesUnsignedLicenseAndAcceptsValidSignedToken() {
        // 1. Tampered SharedPreferences with license_activated=true + unsigned RM.POS@... key must be revoked on boot
        prefs.edit()
            .putBoolean("license_activated", true)
            .putString("license_number", "RM.POS@BFCCFBED@60267985-0-20261118")
            .commit()

        val revokedVm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            ioDispatcher = trackedIoDispatcher
        )
        assertFalse("يجب إبطال حالة التفعيل غير الموقّعة فوراً عند إقلاع التطبيق", revokedVm.isLicenseActivated.value)
        assertFalse(prefs.getBoolean("license_activated", true))

        // 2. Ephemeral runtime ECDSA P-256 keypair injected via trustedKeyRing (no hardcoded private key)
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()
        val runtimePrivB64 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
        val runtimePubB64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        val runtimeKeyRing = mapOf(LicenseSecurityManager.DEFAULT_KEY_ID to runtimePubB64)

        val deviceId = LicenseSecurityManager.getDeviceId(context)
        val now = System.currentTimeMillis()

        // Retired k2026-v1 token must be revoked on boot when only k2026-v2 is trusted
        val retiredV1Token = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = now + 180L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = "k2026-v1",
            graceUntilTimestamp = now + 30L * 86_400_000L,
            issuedAt = now
        )
        prefs.edit()
            .putBoolean("license_activated", true)
            .putString("license_number", retiredV1Token)
            .commit()
        val retiredBootVm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            ioDispatcher = trackedIoDispatcher,
            trustedKeyRing = runtimeKeyRing
        )
        assertFalse("يجب إبطال التفعيل الصادر بالمفتاح القديم k2026-v1 عند الإقلاع", retiredBootVm.isLicenseActivated.value)

        // Valid k2026-v2 signed token bound to this device's ID remains active across boot re-verification
        val validSignedToken = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = now + 180L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = now + 30L * 86_400_000L,
            issuedAt = now
        )
        prefs.edit()
            .putBoolean("license_activated", true)
            .putString("license_number", validSignedToken)
            .commit()

        val activeVm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            ioDispatcher = trackedIoDispatcher,
            trustedKeyRing = runtimeKeyRing
        )
        assertTrue("يجب الإبقاء على التفعيل عند نجاح التحقق التشفيري عند الإقلاع", activeVm.isLicenseActivated.value)
        assertEquals(LicenseSecurityManager.DEFAULT_KEY_ID, activeVm.licenseKeyId.value)
        assertTrue(activeVm.licenseGraceUntil.value > now)
    }

    @Test
    fun testBilingualResolvesStringsXmlResourcesAcrossArFrEn() {
        Bilingual.init(context)
        assertEquals("مبيعات اليوم", Bilingual.get("sales_today", "AR"))
        assertEquals("Ventes du Jour", Bilingual.get("sales_today", "FR"))
        assertEquals("Today's Sales", Bilingual.get("sales_today", "EN"))
    }

    @Test
    fun testCustomUsernameSetupAndRenamePropagatesToSellerRecords() = runBlocking {
        val vm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            ioDispatcher = trackedIoDispatcher
        )

        // Blank username must be rejected instead of falling back to "admin"
        var blankSucceeded = false
        vm.setupInitialAdmin(
            username = "   ",
            pin = "StrongPin#2026",
            onSuccess = { blankSucceeded = true }
        )
        kotlinx.coroutines.delay(200L)
        assertFalse(blankSucceeded)
        assertNull(vm.currentUser.value)
        assertNotNull(vm.loginError.value)

        // Custom username succeeds and preserves real DB row ID
        val setupDone = CompletableDeferred<Unit>()
        vm.setupInitialAdmin(
            username = "أحمد",
            pin = "StrongPin#2026",
            onSuccess = { setupDone.complete(Unit) }
        )
        withTimeout(15_000L) { setupDone.await() }

        val initialUser = vm.currentUser.value
        assertNotNull(initialUser)
        assertEquals("أحمد", initialUser?.username)
        assertTrue("Expected real DB row ID > 0", (initialUser?.id ?: 0L) > 0L)

        // Record a sale under the initial seller name
        val saleId = database.appDao().insertSale(
            com.example.data.SaleEntity(
                invoiceNumber = "INV-TEST-001",
                timestamp = System.currentTimeMillis(),
                cashierName = initialUser!!.username,
                customerId = 0L,
                customerName = "عميل عام",
                totalAmount = 15000L,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 15000L,
                changeAmount = 0L,
                paymentMethod = "CASH"
            )
        )

        // Rename active user and verify both active session and existing sale seller name update
        val renameResult = CompletableDeferred<Pair<Boolean, String>>()
        vm.updateCurrentUsername("كريم") { ok, msg -> renameResult.complete(ok to msg) }
        val (renameOk, _) = withTimeout(15_000L) { renameResult.await() }

        assertTrue(renameOk)
        assertEquals("كريم", vm.currentUser.value?.username)
        val updatedSale = database.appDao().getSaleById(saleId)
        assertEquals("كريم", updatedSale?.cashierName)
    }
}

