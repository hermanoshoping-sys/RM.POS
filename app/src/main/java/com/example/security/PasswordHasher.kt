package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.annotation.VisibleForTesting
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Enterprise Cryptographic utility providing salted PBKDF2WithHmacSHA256 password hashing.
 * Modern Format: "<iterations>:<salt_base64>:<hash_base64>" (>= 210,000 iterations).
 * Backward compatible with legacy formats: 120,000 and 10,000 iterations.
 *
 * Includes monotonic brute-force rate-limiting powered by `SystemClock.elapsedRealtime()` +
 * `Settings.Global.BOOT_COUNT` so changing the device wall clock (forward or backward) or
 * rebooting the device cannot bypass an active lockout.
 */
object PasswordHasher {
    const val CURRENT_ITERATIONS = 600000
    private const val LEGACY_ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    // Rate-limiting / brute-force protection (monotonic elapsedRealtime)
    private const val PREFS_NAME = "security_lockout_prefs"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCK_UNTIL_ELAPSED = "lock_until_elapsed_ms"
    private const val KEY_LOCK_STARTED_ELAPSED = "lock_started_elapsed_ms"
    private const val KEY_LOCK_BOOT_COUNT = "lock_boot_count"
    private const val KEY_LOCK_DURATION_MS = "lock_duration_ms"

    private var appContextRef: Context? = null
    private var persistentPrefs: SharedPreferences? = null
    private var failedAttempts = 0
    private var lockUntilElapsedMs: Long = 0L
    private var lockStartedElapsedMs: Long = 0L
    private var lockBootCount: Int = -1
    private var lockDurationMs: Long = 0L

    @Volatile
    internal var elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }

    @Volatile
    internal var bootCountProvider: () -> Int = { readSystemBootCount(appContextRef) }

    private fun readSystemBootCount(context: Context?): Int {
        if (context == null) return 0
        return runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        }.getOrDefault(0)
    }

    @VisibleForTesting
    @Synchronized
    internal fun resetClockProvidersForTesting() {
        elapsedRealtimeProvider = { SystemClock.elapsedRealtime() }
        bootCountProvider = { readSystemBootCount(appContextRef) }
    }

    @Synchronized
    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContextRef = appCtx
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        persistentPrefs = prefs
        syncFromPrefs(prefs)
    }

    private fun ensurePrefs(context: Context? = null): SharedPreferences? {
        if (context != null && (persistentPrefs == null || appContextRef == null)) {
            init(context)
        }
        return persistentPrefs
    }

    private fun syncFromPrefs(prefs: SharedPreferences) {
        failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        lockUntilElapsedMs = prefs.getLong(KEY_LOCK_UNTIL_ELAPSED, 0L)
        lockStartedElapsedMs = prefs.getLong(KEY_LOCK_STARTED_ELAPSED, 0L)
        lockBootCount = prefs.getInt(KEY_LOCK_BOOT_COUNT, -1)
        lockDurationMs = prefs.getLong(KEY_LOCK_DURATION_MS, 0L)
    }

    private fun persistLockoutState(prefs: SharedPreferences?) {
        prefs?.edit()
            ?.putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
            ?.putLong(KEY_LOCK_UNTIL_ELAPSED, lockUntilElapsedMs)
            ?.putLong(KEY_LOCK_STARTED_ELAPSED, lockStartedElapsedMs)
            ?.putInt(KEY_LOCK_BOOT_COUNT, lockBootCount)
            ?.putLong(KEY_LOCK_DURATION_MS, lockDurationMs)
            ?.apply()
    }

    /**
     * Computes remaining monotonic lockout milliseconds.
     * - Immune to wall-clock changes (`System.currentTimeMillis()` is never consulted).
     * - If the device rebooted while an active lockout was in force, re-anchors the active
     *   lockout duration on the new boot so rebooting cannot bypass rate-limiting.
     */
    private fun computeRemainingLockoutMsLocked(prefs: SharedPreferences?): Long {
        if (prefs != null) {
            syncFromPrefs(prefs)
        }
        if (lockDurationMs <= 0L || lockUntilElapsedMs <= 0L) return 0L

        val nowElapsed = elapsedRealtimeProvider().coerceAtLeast(0L)
        val nowBoot = bootCountProvider()
        val sameBoot = lockBootCount == nowBoot && nowElapsed >= lockStartedElapsedMs

        if (!sameBoot) {
            // Device rebooted while lockout was still active: re-arm duration on the new boot
            lockBootCount = nowBoot
            lockStartedElapsedMs = nowElapsed
            lockUntilElapsedMs = nowElapsed + lockDurationMs
            persistLockoutState(prefs)
            return lockDurationMs
        }

        val remaining = lockUntilElapsedMs - nowElapsed
        if (remaining <= 0L) {
            lockUntilElapsedMs = 0L
            lockStartedElapsedMs = 0L
            lockDurationMs = 0L
            persistLockoutState(prefs)
            return 0L
        }
        return remaining
    }

    @Synchronized
    fun isLockedOut(context: Context? = null): Boolean {
        val prefs = ensurePrefs(context)
        return computeRemainingLockoutMsLocked(prefs) > 0L
    }

    @Synchronized
    fun getRemainingLockoutSeconds(context: Context? = null): Long {
        val prefs = ensurePrefs(context)
        val diff = computeRemainingLockoutMsLocked(prefs)
        return if (diff > 0L) ((diff + 999L) / 1000L) else 0L
    }

    @Synchronized
    fun recordFailedAttempt(context: Context? = null) {
        val prefs = ensurePrefs(context)
        if (prefs != null) {
            syncFromPrefs(prefs)
        }
        failedAttempts++
        if (failedAttempts >= 5) {
            // Escalating monotonic lockout: 5 attempts = 30s, 6 = 60s, 7+ = 300s
            val durationMs = when {
                failedAttempts >= 7 -> 300_000L // 5 minutes
                failedAttempts == 6 -> 60_000L  // 1 minute
                else -> 30_000L                 // 30 seconds
            }
            val nowElapsed = elapsedRealtimeProvider().coerceAtLeast(0L)
            val nowBoot = bootCountProvider()
            lockDurationMs = durationMs
            lockStartedElapsedMs = nowElapsed
            lockUntilElapsedMs = nowElapsed + durationMs
            lockBootCount = nowBoot
        }
        persistLockoutState(prefs)
    }

    @Synchronized
    fun resetFailedAttempts(context: Context? = null) {
        failedAttempts = 0
        lockUntilElapsedMs = 0L
        lockStartedElapsedMs = 0L
        lockBootCount = -1
        lockDurationMs = 0L
        persistLockoutState(ensurePrefs(context))
    }

    suspend fun hashAsync(
        password: String,
        iterations: Int = CURRENT_ITERATIONS,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
    ): String = kotlinx.coroutines.withContext(dispatcher) {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        "$iterations:$saltB64:$hashB64"
    }

    /**
     * Constant-time asynchronous verification of password against storedHash on the provided dispatcher.
     * Supports both modern ("iterations:salt:hash") and legacy ("salt:hash") formats.
     * Strictly rejects plaintext passwords.
     */
    suspend fun verifyAsync(
        password: String,
        storedHash: String?,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
    ): Boolean = kotlinx.coroutines.withContext(dispatcher) {
        if (storedHash.isNullOrBlank()) return@withContext false
        val parts = storedHash.split(":")

        when (parts.size) {
            3 -> {
                try {
                    val iterations = parts[0].toIntOrNull() ?: return@withContext false
                    val salt = Base64.decode(parts[1], Base64.NO_WRAP)
                    val expectedHash = Base64.decode(parts[2], Base64.NO_WRAP)
                    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val actualHash = factory.generateSecret(spec).encoded
                    MessageDigest.isEqual(actualHash, expectedHash)
                } catch (_: Exception) {
                    false
                }
            }
            2 -> {
                try {
                    val salt = Base64.decode(parts[0], Base64.NO_WRAP)
                    val expectedHash = Base64.decode(parts[1], Base64.NO_WRAP)
                    val spec = PBEKeySpec(password.toCharArray(), salt, LEGACY_ITERATIONS, KEY_LENGTH)
                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val actualHash = factory.generateSecret(spec).encoded
                    MessageDigest.isEqual(actualHash, expectedHash)
                } catch (_: Exception) {
                    false
                }
            }
            else -> false
        }
    }

    /**
     * Checks if a stored hash needs to be upgraded to the current iteration count.
     */
    fun needsRehash(storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return true
        val parts = storedHash.split(":")
        if (parts.size != 3) return true
        val iterations = parts[0].toIntOrNull() ?: return true
        return iterations < CURRENT_ITERATIONS
    }

    fun isHashed(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val parts = value.split(":")
        return (parts.size == 3 && (parts[0].toIntOrNull() ?: 0) > 0) ||
            (parts.size == 2 && parts[0].length >= 20 && parts[1].length >= 30)
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(aBytes, bBytes)
    }
}
