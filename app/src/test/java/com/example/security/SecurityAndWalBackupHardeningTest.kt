package com.example.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
class SecurityAndWalBackupHardeningTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PasswordHasher.resetClockProvidersForTesting()
        PasswordHasher.init(context)
        PasswordHasher.resetFailedAttempts(context)
    }

    @After
    fun tearDown() {
        PasswordHasher.resetClockProvidersForTesting()
        PasswordHasher.resetFailedAttempts(context)
    }

    @Test
    fun testLicenseClockGuardDetectsRollbackWithElapsedRealtimeAndSuspendsWithoutDeletingToken() {
        val prefs = context.getSharedPreferences("test_clock_guard_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val elapsed = AtomicLong(200_000L)
        val bootCount = AtomicInteger(3)
        val guard = LicenseClockGuard(
            context = context,
            prefs = prefs,
            elapsedRealtimeProvider = { elapsed.get() },
            bootCountProvider = { bootCount.get() }
        )

        val t0 = 1_795_000_000_000L
        val issuedAt = t0
        guard.recordActivationAnchor(issuedAtMillis = issuedAt)

        // Advance genuine elapsed time by 10 minutes
        elapsed.addAndGet(600_000L)
        val normalEval = guard.evaluateWallClock(nowMillis = t0 + 600_000L)
        assertEquals(LicenseClockState.VALID, normalEval.state)
        assertFalse(normalEval.isRollbackDetected)
        guard.recordVerifiedSessionTick(normalEval, issuedAtMillis = issuedAt)

        // Roll wall clock backward by 5 minutes while elapsedRealtime continues forward
        elapsed.addAndGet(10_000L)
        val rollbackEval = guard.evaluateWallClock(nowMillis = t0 + 300_000L)
        assertEquals(LicenseClockState.ROLLBACK_SUSPENDED, rollbackEval.state)
        assertTrue(rollbackEval.isRollbackDetected)
        assertTrue("Guard must mandate preserving stored token during rollback", rollbackEval.shouldPreserveStoredToken)

        // Forward wall-clock jump (+365 days) must NOT poison monotonic floor or trusted watermark
        elapsed.addAndGet(10_000L)
        val forwardJump = t0 + 365L * 86_400_000L
        val forwardEval = guard.evaluateWallClock(nowMillis = forwardJump)
        assertEquals(LicenseClockState.VALID, forwardEval.state)
        guard.recordVerifiedSessionTick(forwardEval, issuedAtMillis = issuedAt)
        assertEquals(
            "Trusted watermark must remain at signed issuedAt",
            issuedAt,
            prefs.getLong(LicenseClockGuard.KEY_TRUSTED_WATERMARK, 0L)
        )
        assertTrue(
            "Monotonic floor must only advance by genuine hardware elapsed time, never +365 days",
            prefs.getLong(LicenseClockGuard.KEY_MONOTONIC_FLOOR, 0L) <= t0 + 700_000L
        )

        // Restoring wall clock to real time recovers cleanly without rollback lockout
        elapsed.addAndGet(10_000L)
        val recoveredEval = guard.evaluateWallClock(nowMillis = t0 + 630_000L)
        assertEquals(LicenseClockState.VALID, recoveredEval.state)
        assertFalse(recoveredEval.isRollbackDetected)

        // Authoritative reconcileWithTrustedTime CAN lower an erroneously elevated floor
        prefs.edit().putLong(LicenseClockGuard.KEY_MONOTONIC_FLOOR, t0 + 365L * 86_400_000L).commit()
        assertEquals(LicenseClockState.ROLLBACK_SUSPENDED, guard.evaluateWallClock(nowMillis = t0 + 640_000L).state)
        guard.reconcileWithTrustedTime(t0 + 640_000L)
        assertEquals(LicenseClockState.VALID, guard.evaluateWallClock(nowMillis = t0 + 640_000L).state)
    }

    @Test
    fun testPasswordHasherLockoutUsesMonotonicElapsedRealtimeAndSurvivesReboot() {
        val elapsed = AtomicLong(100_000L)
        val boot = AtomicInteger(1)
        PasswordHasher.elapsedRealtimeProvider = { elapsed.get() }
        PasswordHasher.bootCountProvider = { boot.get() }
        PasswordHasher.init(context)
        PasswordHasher.resetFailedAttempts(context)

        // 5 failed attempts trigger a 30-second monotonic lockout
        repeat(5) { PasswordHasher.recordFailedAttempt(context) }
        assertTrue(PasswordHasher.isLockedOut(context))
        assertEquals(30L, PasswordHasher.getRemainingLockoutSeconds(context))

        // Wall-clock manipulation has zero effect because PasswordHasher never reads currentTimeMillis
        elapsed.addAndGet(10_000L) // +10s elapsed
        assertTrue(PasswordHasher.isLockedOut(context))
        assertEquals(20L, PasswordHasher.getRemainingLockoutSeconds(context))

        // Device reboot while locked (boot increments & elapsed resets to 5s) must NOT bypass lockout
        boot.set(2)
        elapsed.set(5_000L)
        assertTrue("Rebooting device while locked must re-arm lockout", PasswordHasher.isLockedOut(context))
        assertEquals(30L, PasswordHasher.getRemainingLockoutSeconds(context))

        // Genuine 31s of monotonic elapsed time clears lockout automatically
        elapsed.addAndGet(31_000L)
        assertFalse("Lockout must clear once monotonic elapsed time expires", PasswordHasher.isLockedOut(context))
        assertEquals(0L, PasswordHasher.getRemainingLockoutSeconds(context))
    }

    @Test
    fun testPreOpenMigrationBackupCopiesMainDbWithWalAndShmCompanionsAndPrunesThemTogether() {
        val dbFile = context.getDatabasePath("preopen_wal_backup_test.db")
        val parent = dbFile.parentFile!!
        val backupDir = File(parent, "migration_backups").apply { deleteRecursively() }

        // Build a synthetic SQLite header with user_version = 21 at byte offset 60
        val headerBytes = ByteArray(128)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(headerBytes, 0)
        ByteBuffer.wrap(headerBytes, 60, 4).putInt(21)
        dbFile.writeBytes(headerBytes)

        val walFile = File(parent, "${dbFile.name}-wal").apply { writeBytes("WAL_RECENT_TX_PAYLOAD".toByteArray()) }
        val shmFile = File(parent, "${dbFile.name}-shm").apply { writeBytes("SHM_INDEX_BYTES".toByteArray()) }

        try {
            assertEquals(21, AppDatabase.readPlaintextSqliteUserVersion(dbFile))

            val backupDb = AppDatabase.backupDatabaseBeforeOpenIfMigrationNeeded(
                context = context,
                dbFile = dbFile,
                targetVersion = AppDatabase.CURRENT_SCHEMA_VERSION
            )
            assertNotNull("Pre-open backup must be created for v21 DB targeting v22", backupDb)
            assertTrue(backupDb!!.exists())
            assertArrayEquals(headerBytes, backupDb.readBytes())

            val backupWal = File(backupDb.parentFile, "${backupDb.name}-wal")
            val backupShm = File(backupDb.parentFile, "${backupDb.name}-shm")
            assertTrue("WAL companion file MUST be backed up before Room opens", backupWal.exists())
            assertTrue("SHM companion file MUST be backed up before Room opens", backupShm.exists())
            assertArrayEquals(walFile.readBytes(), backupWal.readBytes())
            assertArrayEquals(shmFile.readBytes(), backupShm.readBytes())

            // When DB is already at target version 22, pre-open backup is skipped
            ByteBuffer.wrap(headerBytes, 60, 4).putInt(AppDatabase.CURRENT_SCHEMA_VERSION)
            dbFile.writeBytes(headerBytes)
            assertNull(
                "Already up-to-date DB should not trigger redundant pre-open backup",
                AppDatabase.backupDatabaseBeforeOpenIfMigrationNeeded(context, dbFile, AppDatabase.CURRENT_SCHEMA_VERSION)
            )

            // Pruning expired backups must also delete companion -wal and -shm files
            val deleted = AppDatabase.pruneMigrationBackups(
                dbFile = dbFile,
                maxBackupsToKeep = 0,
                maxAgeDays = 7L
            )
            assertEquals(1, deleted)
            assertFalse(backupDb.exists())
            assertFalse("Companion -wal backup must be pruned alongside .db", backupWal.exists())
            assertFalse("Companion -shm backup must be pruned alongside .db", backupShm.exists())
        } finally {
            dbFile.delete()
            walFile.delete()
            shmFile.delete()
            backupDir.deleteRecursively()
        }
    }

    @Test
    fun testProductionMainSourceContainsZeroRobolectricClassSniffing() {
        val mainDirCandidates = listOf(
            File("src/main/java"),
            File("app/src/main/java")
        )
        val mainDir = mainDirCandidates.firstOrNull { it.exists() && it.isDirectory }
        assertNotNull("Could not locate app/src/main/java directory", mainDir)

        val offenders = mutableListOf<String>()
        mainDir!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText(Charsets.UTF_8)
                if (text.contains("org.robolectric") || text.contains("isUnitTestEnvironment")) {
                    offenders.add(file.relativeTo(mainDir).path)
                }
            }
        assertTrue(
            "Production src/main must never sniff Robolectric or contain isUnitTestEnvironment: $offenders",
            offenders.isEmpty()
        )
    }
}
