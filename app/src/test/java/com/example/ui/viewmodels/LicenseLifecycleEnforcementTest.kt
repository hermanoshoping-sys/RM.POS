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
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
class LicenseLifecycleEnforcementTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var database: AppDatabase
    private lateinit var repository: PosRepository
    private lateinit var mainScope: CoroutineScope
    private lateinit var mainDispatcher: ExecutorCoroutineDispatcher
    private lateinit var runtimePrivB64: String
    private lateinit var runtimeKeyRing: Map<String, String>
    private lateinit var deviceId: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("test_license_lifecycle_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        PasswordHasher.init(context)

        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(database.appDao(), database)

        mainDispatcher = Executors.newSingleThreadExecutor { r ->
            Thread(r, "license-lifecycle-test-thread")
        }.asCoroutineDispatcher()
        mainScope = CoroutineScope(SupervisorJob() + mainDispatcher)

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()
        runtimePrivB64 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
        val runtimePubB64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        runtimeKeyRing = mapOf(LicenseSecurityManager.DEFAULT_KEY_ID to runtimePubB64)
        deviceId = LicenseSecurityManager.getDeviceId(context)
    }

    @After
    fun tearDown() {
        mainScope.cancel()
        database.close()
        mainDispatcher.close()
    }

    @Test
    fun testClockRollbackSuspendsWithoutDeletingTokenAndRecoversAutomatically() {
        val t0 = System.currentTimeMillis()
        val elapsedClock = AtomicLong(100_000L)
        val issuedAt = t0 - 60_000L
        val validToken = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 180L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = issuedAt
        )

        val delegate = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsedClock.get() }
        )

        assertTrue(delegate.activateLicense(validToken, nowMillis = t0))
        assertTrue(delegate.isLicenseActivated.value)
        assertFalse(delegate.isClockRollbackSuspended.value)
        // Persisted trusted watermark only records signed `iat`, NOT raw device wall clock
        assertEquals(issuedAt, prefs.getLong("last_observed_system_time", 0L))

        // Simulate device clock jumping backwards by 5 minutes while elapsedRealtime moves forward +5s
        elapsedClock.addAndGet(5_000L)
        val rolledBackTime = t0 - 300_000L
        delegate.reverifyStoredLicenseOnBoot(nowMillis = rolledBackTime)

        assertFalse("License must be suspended when clock rolls back", delegate.isLicenseActivated.value)
        assertTrue("Clock rollback suspension flag must be true", delegate.isClockRollbackSuspended.value)
        assertNotNull("User-facing recovery error must be populated", delegate.licenseError.value)
        assertEquals(
            "Signed token MUST be preserved in SharedPreferences during clock rollback",
            validToken,
            prefs.getString("license_number", "")
        )
        assertEquals(
            "Trusted watermark MUST NOT be lowered during clock rollback",
            issuedAt,
            prefs.getLong("last_observed_system_time", 0L)
        )

        // Simulate user fixing device time back to real time (+15s since start)
        elapsedClock.addAndGet(10_000L)
        val recoveredTime = t0 + 15_000L
        delegate.reverifyStoredLicenseOnBoot(nowMillis = recoveredTime)

        assertTrue("License must auto-recover once clock is restored", delegate.isLicenseActivated.value)
        assertFalse("Clock rollback suspension flag must clear on recovery", delegate.isClockRollbackSuspended.value)
        assertNull("Error message must clear after automatic recovery", delegate.licenseError.value)
        // Raw wall-clock reading still must NOT overwrite trusted watermark
        assertEquals(issuedAt, prefs.getLong("last_observed_system_time", 0L))
    }

    @Test
    fun testForwardClockJumpThenCorrectionDoesNotLockLicense() {
        val t0 = System.currentTimeMillis()
        val elapsedClock = AtomicLong(50_000L)
        val issuedAt = t0 - 60_000L
        val validToken = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 365L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = issuedAt
        )

        val delegate = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsedClock.get() }
        )

        assertTrue(delegate.activateLicense(validToken, nowMillis = t0))
        assertTrue(delegate.isLicenseActivated.value)
        // Record trusted server time at t0
        delegate.recordTrustedTime(t0)
        assertEquals(t0, prefs.getLong("last_observed_system_time", 0L))

        // 1. Accidental forward jump by +10 days (still within grace) while elapsed advances only 10s
        elapsedClock.addAndGet(10_000L)
        val jumpedForward10Days = t0 + 10L * 86_400_000L
        delegate.reverifyStoredLicenseOnBoot(nowMillis = jumpedForward10Days)
        assertEquals(
            "Untrusted forward clock jump MUST NOT poison persisted watermark",
            t0,
            prefs.getLong("last_observed_system_time", 0L)
        )

        // 2. User corrects device date back to real time (t0 + 20s)
        elapsedClock.addAndGet(10_000L)
        val correctedRealTime = t0 + 20_000L
        delegate.reverifyStoredLicenseOnBoot(nowMillis = correctedRealTime)

        assertTrue(
            "Correcting clock after a forward jump MUST NOT lock the license",
            delegate.isLicenseActivated.value
        )
        assertFalse(
            "Correcting clock after a forward jump MUST NOT trigger rollback suspension",
            delegate.isClockRollbackSuspended.value
        )
        assertNull(delegate.licenseError.value)
        assertEquals(validToken, prefs.getString("license_number", ""))
    }

    @Test
    fun testForwardClockJumpPastExpirySuspendsWithoutDeletingTokenAndRecovers() {
        val t0 = System.currentTimeMillis()
        val elapsedClock = AtomicLong(25_000L)
        val expiry = t0 + 180L * 86_400_000L
        val validToken = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = expiry,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = t0 - 60_000L
        )

        val delegate = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsedClock.get() }
        )

        assertTrue(delegate.activateLicense(validToken, nowMillis = t0))
        assertTrue(delegate.isLicenseActivated.value)

        // Device clock jumps +2 years into the future (well past token expiry)
        elapsedClock.addAndGet(10_000L)
        val jumpedPastExpiry = t0 + 730L * 86_400_000L
        delegate.reverifyStoredLicenseOnBoot(nowMillis = jumpedPastExpiry)

        assertFalse("Access must be suspended while clock exceeds expiry", delegate.isLicenseActivated.value)
        assertTrue("Local expiry suspension flag must be active", delegate.isExpirySuspended.value)
        assertEquals(
            "Signed token MUST NOT be deleted solely on untrusted local-clock expiry",
            validToken,
            prefs.getString("license_number", "")
        )
        assertTrue(
            "Trusted watermark must not be raised to the future date",
            prefs.getLong("last_observed_system_time", 0L) <= t0
        )

        // User corrects device date back to real time -> license recovers automatically without a new code
        elapsedClock.addAndGet(10_000L)
        delegate.reverifyStoredLicenseOnBoot(nowMillis = t0 + 20_000L)

        assertTrue("License must auto-recover when future clock is corrected back before expiry", delegate.isLicenseActivated.value)
        assertFalse(delegate.isExpirySuspended.value)
        assertFalse(delegate.isClockRollbackSuspended.value)
        assertNull(delegate.licenseError.value)
        assertEquals(validToken, prefs.getString("license_number", ""))

        // Contrast: once a TRUSTED time source confirms expiry, permanent deactivation occurs
        delegate.recordTrustedTime(expiry + 10_000L)
        delegate.reverifyStoredLicenseOnBoot(nowMillis = expiry + 15_000L)
        assertFalse(delegate.isLicenseActivated.value)
        assertEquals(
            "Confirmed trusted-time expiry should clear stored expired token",
            "",
            prefs.getString("license_number", "")
        )
    }

    @Test
    fun testAppRestartAndRebootAfterClockRollbackSuspendsWithoutDeletingTokenAndRecovers() {
        val t0 = System.currentTimeMillis()
        val elapsedClock = AtomicLong(100_000L)
        var bootCount = 1
        val issuedAt = t0 - 3_600_000L // issued 1 hour ago
        val validToken = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 180L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = issuedAt
        )

        // Session 1: Activate at t0 and run legitimately for 10 minutes
        val delegate1 = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsedClock.get() },
            bootCountProvider = { bootCount }
        )
        assertTrue(delegate1.activateLicense(validToken, nowMillis = t0))
        delegate1.recordTrustedTime(t0)
        elapsedClock.addAndGet(600_000L) // +10 minutes genuine elapsed time
        delegate1.reverifyStoredLicenseOnBoot(nowMillis = t0 + 600_000L)
        assertTrue(delegate1.isLicenseActivated.value)

        // App restarts on SAME boot, but user rolled clock back 5 minutes (still > issuedAt!)
        elapsedClock.addAndGet(5_000L)
        val rolledBackAfterRestart = t0 + 300_000L // 5 min behind expected (t0 + 605s)
        val delegate2 = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsedClock.get() },
            bootCountProvider = { bootCount }
        )
        delegate2.reverifyStoredLicenseOnBoot(nowMillis = rolledBackAfterRestart)

        assertFalse("Rollback after app restart must suspend license", delegate2.isLicenseActivated.value)
        assertTrue("Rollback flag must be set after app restart", delegate2.isClockRollbackSuspended.value)
        assertEquals("Signed token must be preserved across restart rollback", validToken, prefs.getString("license_number", ""))

        // Device REBOOTS (bootCount increments, elapsedRealtime resets), clock still rolled back
        bootCount = 2
        elapsedClock.set(5_000L)
        val delegate3 = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsedClock.get() },
            bootCountProvider = { bootCount }
        )
        delegate3.reverifyStoredLicenseOnBoot(nowMillis = rolledBackAfterRestart)
        assertFalse("Rollback after device reboot must still be caught by hardware-elapsed floor", delegate3.isLicenseActivated.value)
        assertTrue(delegate3.isClockRollbackSuspended.value)
        assertEquals(validToken, prefs.getString("license_number", ""))

        // User restores clock to valid current time -> auto-recovers cleanly
        delegate3.reverifyStoredLicenseOnBoot(nowMillis = t0 + 620_000L)
        assertTrue("License must auto-recover after restoring valid time", delegate3.isLicenseActivated.value)
        assertFalse(delegate3.isClockRollbackSuspended.value)
        assertNull(delegate3.licenseError.value)

        // Direct token verification also rejects clock set earlier than signed `issued_at`
        val beforeIssued = LicenseSecurityManager.verifyLicenseToken(
            tokenString = validToken,
            currentDeviceId = deviceId,
            trustedKeyRing = runtimeKeyRing,
            nowMillis = issuedAt - 120_000L
        )
        assertFalse(beforeIssued.isValid)
        assertTrue("Clock before signed issued_at must set isClockBeforeIssuedAt", beforeIssued.isClockBeforeIssuedAt)
    }

    @Test
    fun testResumeAndPeriodicMonitorRevokeExpiredLicenseWhileRunning() = runBlocking {
        val t0 = System.currentTimeMillis()
        val shortExpiry = t0 + 60_000L
        val shortLivedToken = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = shortExpiry,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = shortExpiry,
            issuedAt = t0 - 5_000L
        )

        val vm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            trustedKeyRing = runtimeKeyRing,
            enablePeriodicLicenseCheck = false
        )

        assertTrue(vm.activateLicense(shortLivedToken))
        assertTrue(vm.isLicenseActivated.value)

        // 1. Simulate MainActivity.onResume() after expiry without recreating ViewModel
        vm.checkLicenseExpirationWithAntiRollback(nowMillis = shortExpiry + 10_000L)
        assertFalse("onResume re-check must suspend expired license immediately", vm.isLicenseActivated.value)
        assertTrue("Local expiry suspension preserves signed token", vm.isExpirySuspended.value)
        assertEquals(shortLivedToken, prefs.getString("license_number", ""))
        assertNotNull(vm.licenseError.value)

        // 2. Re-activate fresh short-lived token and verify periodic background monitor catches expiry
        prefs.edit().clear().commit()
        val now2 = System.currentTimeMillis()
        val expiry2 = now2 + 120_000L
        val token2 = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = expiry2,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = expiry2,
            issuedAt = now2 - 5_000L
        )
        assertTrue(vm.activateLicense(token2))
        assertTrue(vm.isLicenseActivated.value)

        val simulatedClock = AtomicLong(now2 + 1_000L)
        vm.startPeriodicLicenseMonitor(intervalMs = 100L, nowProvider = { simulatedClock.get() })

        // Still valid before expiry
        delay(180L)
        assertTrue("License should remain active while simulated clock is before expiry", vm.isLicenseActivated.value)

        // Advance simulated clock past expiry while app remains open
        simulatedClock.set(expiry2 + 5_000L)
        delay(260L)
        assertFalse("Periodic runtime monitor must suspend expired license without app restart", vm.isLicenseActivated.value)
        vm.stopPeriodicLicenseMonitor()
    }

    @Test
    fun testRuntimeOfflineGraceExpirySuspendsButPreservesTokenForOnlineRenewal() {
        val t0 = System.currentTimeMillis()
        val graceEnd = t0 + 120_000L
        val token = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 365L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = graceEnd,
            issuedAt = t0 - 5_000L
        )

        val vm = AuthSecurityViewModel(
            repository = repository,
            context = context,
            prefs = prefs,
            externalScope = mainScope,
            trustedKeyRing = runtimeKeyRing,
            enablePeriodicLicenseCheck = false
        )

        assertTrue(vm.activateLicense(token))
        assertTrue(vm.isLicenseActivated.value)

        vm.checkLicenseExpirationWithAntiRollback(nowMillis = graceEnd + 5_000L)
        assertFalse("Expired offline grace must suspend active session", vm.isLicenseActivated.value)
        assertTrue("needsOnlineRenewal must be flagged when offline grace expires", vm.needsOnlineRenewal.value)
        assertEquals(
            "Signed token must be retained to allow online grace renewal",
            token,
            prefs.getString("license_number", "")
        )
    }

    @Test
    fun testForwardClockCase1_BootWithForwardClockAndSecondCheckThenCorrectionDoesNotLock() {
        val t0 = System.currentTimeMillis()
        val elapsed = AtomicLong(20_000L)
        val bootCount = 1
        val issuedAt = t0 - 60_000L
        val token = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 365L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = issuedAt
        )
        // Stored license from earlier install; boot starts with clock +10 days ahead (within grace)
        prefs.edit()
            .putBoolean("license_activated", true)
            .putString("license_number", token)
            .commit()

        val delegate = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { bootCount }
        )

        val forwardClock = t0 + 10L * 86_400_000L
        // Check 1 on boot with forward clock (+10 days)
        delegate.reverifyStoredLicenseOnBoot(nowMillis = forwardClock)
        assertTrue(delegate.isLicenseActivated.value)

        // Check 2 (+5 minutes later, clock STILL +10 days ahead)
        elapsed.addAndGet(300_000L)
        delegate.reverifyStoredLicenseOnBoot(nowMillis = forwardClock + 300_000L)
        assertTrue(delegate.isLicenseActivated.value)
        assertTrue(
            "Anchor wall time must NEVER be elevated from raw forward clock",
            prefs.getLong(com.example.security.LicenseClockGuard.KEY_ANCHOR_WALL_TIME, 0L) <= t0
        )
        assertTrue(
            "Monotonic floor must only advance by genuine hardware elapsed time (+5m), never +10 days",
            prefs.getLong(com.example.security.LicenseClockGuard.KEY_MONOTONIC_FLOOR, 0L) <= t0 + 300_000L
        )

        // User corrects clock back to real time (t0 + 6 min) -> MUST NOT lock
        elapsed.addAndGet(60_000L)
        delegate.reverifyStoredLicenseOnBoot(nowMillis = t0 + 360_000L)
        assertTrue("Correcting forward clock after second check must keep license active", delegate.isLicenseActivated.value)
        assertFalse(delegate.isClockRollbackSuspended.value)
        assertNull(delegate.licenseError.value)
    }

    @Test
    fun testForwardClockCase2_BootWithForwardClockCorrectedBeforeSecondCheckDoesNotLock() {
        val t0 = System.currentTimeMillis()
        val elapsed = AtomicLong(15_000L)
        val bootCount = 1
        val issuedAt = t0 - 60_000L
        val token = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 365L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = issuedAt
        )
        prefs.edit().putBoolean("license_activated", true).putString("license_number", token).commit()

        val delegate = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { bootCount }
        )

        // Check 1 on boot with forward clock (+10 days)
        delegate.reverifyStoredLicenseOnBoot(nowMillis = t0 + 10L * 86_400_000L)
        assertTrue(delegate.isLicenseActivated.value)

        // User fixes clock back to real time BEFORE any second forward-clock check
        elapsed.addAndGet(15_000L)
        delegate.reverifyStoredLicenseOnBoot(nowMillis = t0 + 15_000L)
        assertTrue("Fixing clock before second check must not lock license", delegate.isLicenseActivated.value)
        assertFalse(delegate.isClockRollbackSuspended.value)
        assertNull(delegate.licenseError.value)
    }

    @Test
    fun testForwardClockCase3_DeviceRebootWithForwardClockThenCorrectionDoesNotLock() {
        val t0 = System.currentTimeMillis()
        val elapsed = AtomicLong(50_000L)
        var bootCount = 1
        val issuedAt = t0 - 60_000L
        val token = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 365L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = issuedAt
        )

        // Boot 1: Normal activation & 10 minutes of real operation
        val delegateBoot1 = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { bootCount }
        )
        assertTrue(delegateBoot1.activateLicense(token, nowMillis = t0))
        elapsed.addAndGet(600_000L)
        delegateBoot1.reverifyStoredLicenseOnBoot(nowMillis = t0 + 600_000L)
        assertTrue(delegateBoot1.isLicenseActivated.value)

        // Boot 2: Device reboots with clock jumped +10 days ahead, runs Check 1 and Check 2
        bootCount = 2
        elapsed.set(5_000L)
        val delegateBoot2 = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { bootCount }
        )
        val forwardAfterReboot = t0 + 10L * 86_400_000L
        delegateBoot2.reverifyStoredLicenseOnBoot(nowMillis = forwardAfterReboot)
        assertTrue(delegateBoot2.isLicenseActivated.value)
        elapsed.addAndGet(300_000L)
        delegateBoot2.reverifyStoredLicenseOnBoot(nowMillis = forwardAfterReboot + 300_000L)
        assertTrue(delegateBoot2.isLicenseActivated.value)

        // User corrects clock back to real time (t0 + 20m) on Boot 2 AND after Boot 3 -> MUST NOT lock
        elapsed.addAndGet(60_000L)
        delegateBoot2.reverifyStoredLicenseOnBoot(nowMillis = t0 + 1_200_000L)
        assertTrue("Correcting clock after forward-clock reboot must stay active", delegateBoot2.isLicenseActivated.value)
        assertFalse(delegateBoot2.isClockRollbackSuspended.value)

        bootCount = 3
        elapsed.set(4_000L)
        val delegateBoot3 = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { bootCount }
        )
        delegateBoot3.reverifyStoredLicenseOnBoot(nowMillis = t0 + 1_260_000L)
        assertTrue("Subsequent normal reboot must remain active without lockout", delegateBoot3.isLicenseActivated.value)
        assertFalse(delegateBoot3.isClockRollbackSuspended.value)
    }

    @Test
    fun testForwardClockCase4_InSessionForwardJumpWithinGraceAndPastExpiryRecoversOnCorrection() {
        val t0 = System.currentTimeMillis()
        val elapsed = AtomicLong(40_000L)
        val token = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 180L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = t0 - 60_000L
        )
        val delegate = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { 1 }
        )
        assertTrue(delegate.activateLicense(token, nowMillis = t0))

        // In-session jump +10 days (within grace)
        elapsed.addAndGet(30_000L)
        delegate.reverifyStoredLicenseOnBoot(nowMillis = t0 + 10L * 86_400_000L)
        assertTrue(delegate.isLicenseActivated.value)

        // In-session jump +2 years (past expiry -> suspended, token preserved)
        elapsed.addAndGet(30_000L)
        delegate.reverifyStoredLicenseOnBoot(nowMillis = t0 + 730L * 86_400_000L)
        assertFalse(delegate.isLicenseActivated.value)
        assertTrue(delegate.isExpirySuspended.value)
        assertEquals(token, prefs.getString("license_number", ""))

        // Correction back to real time (t0 + 90s) -> auto-recovers
        elapsed.addAndGet(30_000L)
        delegate.reverifyStoredLicenseOnBoot(nowMillis = t0 + 90_000L)
        assertTrue(delegate.isLicenseActivated.value)
        assertFalse(delegate.isExpirySuspended.value)
        assertFalse(delegate.isClockRollbackSuspended.value)
    }

    @Test
    fun testForwardClockCase5_RealClockRollbackStillDetectedInSessionRestartAndReboot() {
        val t0 = System.currentTimeMillis()
        val elapsed = AtomicLong(100_000L)
        var bootCount = 1
        val issuedAt = t0
        val token = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 365L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = issuedAt
        )

        val delegate1 = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { bootCount }
        )
        assertTrue(delegate1.activateLicense(token, nowMillis = t0))
        // Genuine 10-minute run
        elapsed.addAndGet(600_000L)
        delegate1.reverifyStoredLicenseOnBoot(nowMillis = t0 + 600_000L)
        assertTrue(delegate1.isLicenseActivated.value)

        // Genuine rollback: user sets clock backward 5 minutes to t0 + 5m (STILL > issuedAt!)
        elapsed.addAndGet(5_000L)
        val rolledBack = t0 + 300_000L
        delegate1.reverifyStoredLicenseOnBoot(nowMillis = rolledBack)
        assertFalse("In-session rollback must suspend access", delegate1.isLicenseActivated.value)
        assertTrue(delegate1.isClockRollbackSuspended.value)
        assertEquals(token, prefs.getString("license_number", ""))

        // App restart on same boot: still caught
        val delegate2 = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { bootCount }
        )
        delegate2.reverifyStoredLicenseOnBoot(nowMillis = rolledBack)
        assertFalse("Post-restart rollback must remain suspended", delegate2.isLicenseActivated.value)
        assertTrue(delegate2.isClockRollbackSuspended.value)

        // Device reboot: still caught by hardware-elapsed monotonic floor
        bootCount = 2
        elapsed.set(5_000L)
        val delegate3 = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { bootCount }
        )
        delegate3.reverifyStoredLicenseOnBoot(nowMillis = rolledBack)
        assertFalse("Post-reboot rollback must remain suspended", delegate3.isLicenseActivated.value)
        assertTrue(delegate3.isClockRollbackSuspended.value)

        // Restoring accurate time resumes automatically
        delegate3.reverifyStoredLicenseOnBoot(nowMillis = t0 + 620_000L)
        assertTrue(delegate3.isLicenseActivated.value)
        assertFalse(delegate3.isClockRollbackSuspended.value)
    }

    @Test
    fun testEscapeHatch_TrustedServerTimeAndSignedClockResetLowerElevatedFloor() = runBlocking {
        val t0 = System.currentTimeMillis()
        val elapsed = AtomicLong(80_000L)
        val token = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 365L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = t0 - 60_000L
        )

        // Simulate legacy poisoned floor (+1 year in future) locking a valid device at real time t0
        val poisonedFuture = t0 + 365L * 86_400_000L
        prefs.edit()
            .putBoolean("license_activated", false)
            .putString("license_number", token)
            .putLong(com.example.security.LicenseClockGuard.KEY_TRUSTED_WATERMARK, poisonedFuture)
            .putLong(com.example.security.LicenseClockGuard.KEY_MONOTONIC_FLOOR, poisonedFuture)
            .commit()

        var serverReturnsTime = false
        val delegate = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { 1 },
            onlineVerifier = { _, key, _, _ ->
                if (serverReturnsTime) {
                    LicenseSecurityManager.verifyLicenseToken(key, deviceId, trustedKeyRing = runtimeKeyRing, nowMillis = t0)
                        .copy(renewedSignedToken = key, serverTimeMillis = t0)
                } else {
                    // Offline fallback: serverTimeMillis is null
                    LicenseSecurityManager.verifyLicenseToken(key, deviceId, trustedKeyRing = runtimeKeyRing, nowMillis = t0)
                }
            }
        )

        delegate.reverifyStoredLicenseOnBoot(nowMillis = t0)
        assertFalse(delegate.isLicenseActivated.value)
        assertTrue(delegate.isClockRollbackSuspended.value)

        // 1. Offline fallback (no trusted server time) MUST NOT bypass rollback suspension
        val offlineRes = kotlinx.coroutines.CompletableDeferred<Boolean>()
        delegate.verifyOnlineSubscription { ok, _ -> offlineRes.complete(ok) }
        assertFalse("Offline fallback must not clear rollback suspension", offlineRes.await())
        assertFalse(delegate.isLicenseActivated.value)
        assertTrue(delegate.isClockRollbackSuspended.value)

        // 2. Authoritative online server time LOWERS the poisoned floor & resumes license
        serverReturnsTime = true
        val onlineRes = kotlinx.coroutines.CompletableDeferred<Boolean>()
        delegate.verifyOnlineSubscription { ok, _ -> onlineRes.complete(ok) }
        assertTrue("Verified server time must lower floor and resume license", onlineRes.await())
        assertTrue(delegate.isLicenseActivated.value)
        assertFalse(delegate.isClockRollbackSuspended.value)
        assertEquals(t0, prefs.getLong(com.example.security.LicenseClockGuard.KEY_MONOTONIC_FLOOR, 0L))

        // 3. Re-poison floor and verify one-time ECDSA-signed CLOCK_RESET token lowers it and rejects replay
        prefs.edit()
            .putLong(com.example.security.LicenseClockGuard.KEY_TRUSTED_WATERMARK, poisonedFuture)
            .putLong(com.example.security.LicenseClockGuard.KEY_MONOTONIC_FLOOR, poisonedFuture)
            .commit()
        delegate.reverifyStoredLicenseOnBoot(nowMillis = t0)
        assertTrue(delegate.isClockRollbackSuspended.value)

        val clockResetToken = TestLicenseTokenSigner.generateSignedClockResetToken(
            deviceId = deviceId,
            resetTimeMillis = t0,
            expiryTimestamp = t0 + 86_400_000L,
            privateKeyBase64 = runtimePrivB64
        )
        // Must NEVER be accepted as a regular license activation token
        assertFalse(LicenseSecurityManager.verifyLicenseToken(clockResetToken, deviceId, trustedKeyRing = runtimeKeyRing, nowMillis = t0).isValid)

        // Applying signed CLOCK_RESET lowers floor and resumes preserved license
        assertTrue(delegate.applyClockResetToken(clockResetToken, nowMillis = t0))
        assertTrue("Signed CLOCK_RESET must restore license access", delegate.isLicenseActivated.value)
        assertFalse(delegate.isClockRollbackSuspended.value)
        assertEquals(t0, prefs.getLong(com.example.security.LicenseClockGuard.KEY_MONOTONIC_FLOOR, 0L))

        // Replaying the same CLOCK_RESET token must be rejected (one-time anti-replay)
        assertFalse("Consumed CLOCK_RESET token must be rejected on replay", delegate.applyClockResetToken(clockResetToken, nowMillis = t0))
    }

    @Test
    fun testServerRevocationRequiresValidEcdsaSignatureAndRejectsUnsignedOrForgedRevoke() = runBlocking {
        val t0 = System.currentTimeMillis()
        val token = TestLicenseTokenSigner.generateSignedToken(
            deviceId = deviceId,
            licenseType = "PRO",
            expiryTimestamp = t0 + 365L * 86_400_000L,
            privateKeyBase64 = runtimePrivB64,
            keyId = LicenseSecurityManager.DEFAULT_KEY_ID,
            graceUntilTimestamp = t0 + 30L * 86_400_000L,
            issuedAt = t0 - 60_000L
        )

        // Generate an untrusted attacker ECDSA keypair
        val attackerKpg = java.security.KeyPairGenerator.getInstance("EC")
        attackerKpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val attackerPrivB64 = android.util.Base64.encodeToString(attackerKpg.generateKeyPair().private.encoded, android.util.Base64.NO_WRAP)

        var serverJsonResponse = """{"valid": false, "message": "Spoofed MITM revocation"}"""
        val delegate = LicenseLifecycleDelegate(
            context = context,
            prefs = prefs,
            scope = mainScope,
            languageProvider = { "AR" },
            trustedKeyRing = runtimeKeyRing,
            onlineVerifier = { devId, _, _, keyRing ->
                com.example.security.LicenseOnlineClient.parseServerResponse(
                    responseStr = serverJsonResponse,
                    deviceId = devId,
                    trustedKeyRing = keyRing,
                    nowMillisProvider = { t0 }
                )
            }
        )

        assertTrue(delegate.activateLicense(token, nowMillis = t0))
        assertTrue(delegate.isLicenseActivated.value)

        // 1. Unsigned {"valid": false} MUST NOT revoke or delete the local license
        val unsignedRes = kotlinx.coroutines.CompletableDeferred<Boolean>()
        delegate.verifyOnlineSubscription("https://example.invalid/verify") { ok, _ -> unsignedRes.complete(ok) }
        assertFalse(unsignedRes.await())
        assertTrue("Unsigned valid:false must NEVER deactivate license", delegate.isLicenseActivated.value)
        assertEquals("Unsigned valid:false must NEVER delete stored token", token, prefs.getString("license_number", ""))

        // 2. Forged-key revocation_token MUST NOT revoke the license
        val forgedRevoke = TestLicenseTokenSigner.generateSignedRevocationToken(
            deviceId = deviceId,
            revokedAtMillis = t0,
            privateKeyBase64 = attackerPrivB64
        )
        serverJsonResponse = """{"valid": false, "revocation_token": "$forgedRevoke"}"""
        val forgedRes = kotlinx.coroutines.CompletableDeferred<Boolean>()
        delegate.verifyOnlineSubscription("https://example.invalid/verify") { ok, _ -> forgedRes.complete(ok) }
        assertFalse(forgedRes.await())
        assertTrue("Forged revocation signature must NEVER deactivate license", delegate.isLicenseActivated.value)
        assertEquals(token, prefs.getString("license_number", ""))

        // 3. Valid ECDSA revocation_token bound to a DIFFERENT device MUST NOT revoke this device
        val otherDeviceRevoke = TestLicenseTokenSigner.generateSignedRevocationToken(
            deviceId = "DEADBEEF00001111",
            revokedAtMillis = t0,
            privateKeyBase64 = runtimePrivB64
        )
        serverJsonResponse = """{"valid": false, "revocation_token": "$otherDeviceRevoke"}"""
        val otherDevRes = kotlinx.coroutines.CompletableDeferred<Boolean>()
        delegate.verifyOnlineSubscription("https://example.invalid/verify") { ok, _ -> otherDevRes.complete(ok) }
        assertFalse(otherDevRes.await())
        assertTrue("Revocation signed for another device must NEVER deactivate this device", delegate.isLicenseActivated.value)

        // 4. Valid ECDSA revocation_token signed by trusted key for THIS device DOES revoke & clear license
        val validRevoke = TestLicenseTokenSigner.generateSignedRevocationToken(
            deviceId = deviceId,
            revokedAtMillis = t0,
            reason = "إلغاء رسمي موقّع",
            privateKeyBase64 = runtimePrivB64
        )
        // Must NEVER be accepted as a regular activation token
        assertFalse(LicenseSecurityManager.verifyLicenseToken(validRevoke, deviceId, trustedKeyRing = runtimeKeyRing, nowMillis = t0).isValid)

        serverJsonResponse = """{"valid": false, "revocation_token": "$validRevoke"}"""
        val validRevokeRes = kotlinx.coroutines.CompletableDeferred<Boolean>()
        delegate.verifyOnlineSubscription("https://example.invalid/verify") { ok, _ -> validRevokeRes.complete(ok) }
        assertFalse(validRevokeRes.await())
        assertFalse("Trusted ECDSA-signed revocation must deactivate license", delegate.isLicenseActivated.value)
        assertEquals("", prefs.getString("license_number", ""))
    }
}
