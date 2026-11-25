package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

/**
 * Clock-integrity and suspension state produced by [LicenseClockGuard].
 *
 * Notice that [ROLLBACK_SUSPENDED] and [LOCAL_EXPIRY_SUSPENDED] explicitly instruct callers to
 * suspend license access WITHOUT deleting the stored ECDSA-signed token, enabling automatic
 * recovery as soon as the device date/time is corrected.
 */
enum class LicenseClockState {
    VALID,
    ROLLBACK_SUSPENDED,
    LOCAL_EXPIRY_SUSPENDED,
    CONFIRMED_EXPIRED
}

data class LicenseClockEvaluation(
    val state: LicenseClockState,
    val isRollbackDetected: Boolean,
    val trustedWatermarkMillis: Long,
    val monotonicFloorMillis: Long,
    val expectedSameBootWallMillis: Long?,
    val hasValidSameBootAnchor: Boolean,
    val nowElapsedMillis: Long,
    val nowBootCount: Int,
    val shouldPreserveStoredToken: Boolean
)

/**
 * Monotonic anti-rollback & non-destructive suspension guard for ECDSA-signed licenses.
 *
 * Design guarantees:
 * 1. **Zero untrusted wall-clock elevation**:
 *    Raw `System.currentTimeMillis()` NEVER raises `KEY_TRUSTED_WATERMARK`,
 *    `KEY_MONOTONIC_FLOOR`, or `KEY_ANCHOR_WALL_TIME`. Booting or rebooting with a future date
 *    (+10 days, +2 years, etc.) can never poison the guard or lock the device after correcting
 *    the clock.
 * 2. **Trusted base + hardware-elapsed progression (`SystemClock.elapsedRealtime()` + `BOOT_COUNT`)**:
 *    Same-boot anchors and cross-reboot floors start exclusively from confirmed trusted time
 *    (signed token `issued_at`, verified license-server time, or signed `CLOCK_RESET`) and
 *    advance solely by genuine hardware-elapsed `elapsedRealtime()` deltas.
 * 3. **Verified lowering escape hatches**:
 *    [reconcileWithTrustedTime] allows a verified online server timestamp or a one-time
 *    ECDSA-signed `CLOCK_RESET` token to lower/reset a previously elevated floor and re-anchor
 *    the clock cleanly.
 * 4. **Suspension instead of token deletion**:
 *    Clock rollback and unconfirmed local-clock expiry transition into suspension states while
 *    preserving the signed token so correcting the device clock restores access automatically.
 */
class LicenseClockGuard(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val bootCountProvider: () -> Int = { readSystemBootCount(context) },
    private val rollbackToleranceMs: Long = CLOCK_ROLLBACK_TOLERANCE_MS
) {
    companion object {
        const val CLOCK_ROLLBACK_TOLERANCE_MS = 60_000L

        const val KEY_TRUSTED_WATERMARK = "last_observed_system_time"
        const val KEY_MONOTONIC_FLOOR = "license_monotonic_floor_time"
        const val KEY_ANCHOR_BOOT_COUNT = "license_anchor_boot_count"
        const val KEY_ANCHOR_WALL_TIME = "license_anchor_wall_time"
        const val KEY_ANCHOR_ELAPSED_TIME = "license_anchor_elapsed_time"

        fun readSystemBootCount(context: Context): Int = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        }.getOrDefault(0)
    }

    /**
     * Evaluates wall-clock integrity against the same-boot `elapsedRealtime()` anchor, the
     * hardware-elapsed monotonic floor, and the trusted-time watermark.
     */
    @Synchronized
    fun evaluateWallClock(nowMillis: Long = System.currentTimeMillis()): LicenseClockEvaluation {
        val trustedWatermark = prefs.getLong(KEY_TRUSTED_WATERMARK, 0L)
        val monotonicFloor = prefs.getLong(KEY_MONOTONIC_FLOOR, 0L)
        val nowElapsed = elapsedRealtimeProvider()
        val nowBoot = bootCountProvider()

        val savedAnchorWall = prefs.getLong(KEY_ANCHOR_WALL_TIME, 0L)
        val savedAnchorElapsed = prefs.getLong(KEY_ANCHOR_ELAPSED_TIME, -1L)
        val savedAnchorBoot = prefs.getInt(KEY_ANCHOR_BOOT_COUNT, -1)
        val hasValidSameBootAnchor = savedAnchorWall > 0L &&
            savedAnchorElapsed >= 0L &&
            savedAnchorBoot == nowBoot &&
            nowElapsed >= savedAnchorElapsed

        val expectedWall = if (hasValidSameBootAnchor) {
            savedAnchorWall + (nowElapsed - savedAnchorElapsed)
        } else {
            null
        }

        val isTrustedRollback = trustedWatermark > 0L &&
            nowMillis < (trustedWatermark - rollbackToleranceMs)
        val isMonotonicFloorRollback = monotonicFloor > 0L &&
            nowMillis < (monotonicFloor - rollbackToleranceMs)
        val isSameBootRollback = expectedWall != null &&
            nowMillis < (expectedWall - rollbackToleranceMs)

        val rollbackDetected = isTrustedRollback || isMonotonicFloorRollback || isSameBootRollback
        return LicenseClockEvaluation(
            state = if (rollbackDetected) LicenseClockState.ROLLBACK_SUSPENDED else LicenseClockState.VALID,
            isRollbackDetected = rollbackDetected,
            trustedWatermarkMillis = trustedWatermark,
            monotonicFloorMillis = monotonicFloor,
            expectedSameBootWallMillis = expectedWall,
            hasValidSameBootAnchor = hasValidSameBootAnchor,
            nowElapsedMillis = nowElapsed,
            nowBootCount = nowBoot,
            shouldPreserveStoredToken = true
        )
    }

    /**
     * Combines wall-clock rollback evaluation with a cryptographic token verification result to
     * classify the exact clock/suspension state (`VALID`, `ROLLBACK_SUSPENDED`,
     * `LOCAL_EXPIRY_SUSPENDED`, or `CONFIRMED_EXPIRED`).
     */
    @Synchronized
    fun evaluateAgainstVerification(
        wallClockEval: LicenseClockEvaluation,
        verification: LicenseSecurityManager.LicenseVerificationResult
    ): LicenseClockState {
        if (wallClockEval.isRollbackDetected || verification.isClockBeforeIssuedAt) {
            return LicenseClockState.ROLLBACK_SUSPENDED
        }
        val confirmedExpiredByTrustedTime = verification.expiryTimestamp != null &&
            wallClockEval.trustedWatermarkMillis > 0L &&
            wallClockEval.trustedWatermarkMillis > verification.expiryTimestamp
        if (confirmedExpiredByTrustedTime) {
            return LicenseClockState.CONFIRMED_EXPIRED
        }
        if (verification.isExpired) {
            return LicenseClockState.LOCAL_EXPIRY_SUSPENDED
        }
        return LicenseClockState.VALID
    }

    /**
     * Authoritatively reconciles (raising OR lowering) the persisted trusted watermark,
     * hardware-elapsed monotonic floor, and same-boot `elapsedRealtime()` anchor from an
     * authoritative trusted time source (verified online license server time or one-time
     * ECDSA-signed `CLOCK_RESET` token). Never call with raw untrusted device wall clock.
     */
    @Synchronized
    fun reconcileWithTrustedTime(trustedTimeMillis: Long) {
        if (trustedTimeMillis <= 0L) return
        val nowElapsed = elapsedRealtimeProvider()
        val nowBoot = bootCountProvider()
        prefs.edit()
            .putLong(KEY_TRUSTED_WATERMARK, trustedTimeMillis)
            .putLong(KEY_MONOTONIC_FLOOR, trustedTimeMillis)
            .putInt(KEY_ANCHOR_BOOT_COUNT, nowBoot)
            .putLong(KEY_ANCHOR_WALL_TIME, trustedTimeMillis)
            .putLong(KEY_ANCHOR_ELAPSED_TIME, nowElapsed)
            .apply()
    }

    /**
     * Records a live server-verified timestamp, reconciling the trusted watermark, floor,
     * and same-boot anchor.
     */
    @Synchronized
    fun recordTrustedTime(trustedTimeMillis: Long) {
        reconcileWithTrustedTime(trustedTimeMillis)
    }

    /**
     * Advances ONLY the persisted trusted watermark and monotonic floor from a signed static
     * timestamp (such as a verified token's `issued_at`), never lowering either value.
     */
    @Synchronized
    fun advanceTrustedWatermarkOnly(trustedTimeMillis: Long?) {
        if (trustedTimeMillis == null || trustedTimeMillis <= 0L) return
        val prevTrusted = prefs.getLong(KEY_TRUSTED_WATERMARK, 0L)
        val prevFloor = prefs.getLong(KEY_MONOTONIC_FLOOR, 0L)
        if (trustedTimeMillis > prevTrusted || trustedTimeMillis > prevFloor) {
            prefs.edit()
                .putLong(KEY_TRUSTED_WATERMARK, maxOf(prevTrusted, trustedTimeMillis))
                .putLong(KEY_MONOTONIC_FLOOR, maxOf(prevFloor, trustedTimeMillis))
                .apply()
        }
    }

    /**
     * Anchors the monotonic clock reference when a license is freshly activated using ONLY
     * trusted time (`issued_at` / existing trusted floor), never raw untrusted device wall clock.
     */
    @Synchronized
    fun recordActivationAnchor(issuedAtMillis: Long?) {
        advanceTrustedWatermarkOnly(issuedAtMillis)
        val trustedBase = maxOf(
            prefs.getLong(KEY_TRUSTED_WATERMARK, 0L),
            prefs.getLong(KEY_MONOTONIC_FLOOR, 0L)
        )
        if (trustedBase > 0L) {
            persistAnchor(
                bootCount = bootCountProvider(),
                wallTimeMillis = trustedBase,
                elapsedMillis = elapsedRealtimeProvider()
            )
        }
    }

    /**
     * Establishes the same-boot anchor (if absent) strictly from confirmed trusted time and
     * advances the cross-reboot monotonic floor strictly by genuine hardware-elapsed
     * `elapsedRealtime()` deltas — never by raw device wall clock readings.
     */
    @Synchronized
    fun recordVerifiedSessionTick(
        wallClockEval: LicenseClockEvaluation,
        issuedAtMillis: Long?
    ) {
        advanceTrustedWatermarkOnly(issuedAtMillis)
        val trustedBase = maxOf(
            prefs.getLong(KEY_TRUSTED_WATERMARK, 0L),
            prefs.getLong(KEY_MONOTONIC_FLOOR, 0L)
        )
        if (trustedBase <= 0L) return

        if (!wallClockEval.hasValidSameBootAnchor) {
            persistAnchor(
                bootCount = wallClockEval.nowBootCount,
                wallTimeMillis = trustedBase,
                elapsedMillis = wallClockEval.nowElapsedMillis
            )
        } else {
            val expectedWall = wallClockEval.expectedSameBootWallMillis ?: return
            val nextFloor = maxOf(trustedBase, expectedWall)
            val prevFloor = prefs.getLong(KEY_MONOTONIC_FLOOR, 0L)
            if (nextFloor > prevFloor) {
                prefs.edit().putLong(KEY_MONOTONIC_FLOOR, nextFloor).apply()
            }
        }
    }

    private fun persistAnchor(bootCount: Int, wallTimeMillis: Long, elapsedMillis: Long) {
        prefs.edit()
            .putInt(KEY_ANCHOR_BOOT_COUNT, bootCount)
            .putLong(KEY_ANCHOR_WALL_TIME, wallTimeMillis)
            .putLong(KEY_ANCHOR_ELAPSED_TIME, elapsedMillis)
            .apply()
    }
}
