package com.example.security

import androidx.annotation.VisibleForTesting

/**
 * Runtime storage-security mode configuration.
 *
 * Production builds ALWAYS run with [isJvmTestMode] == `false` (enforcing SQLCipher AES-256 and
 * Android Keystore with zero reflection or classpath sniffing). Only the JVM unit-test harness
 * (`app/src/test/java/com/example/TestPosApp.kt`) toggles [enableJvmTestModeForTests] when native
 * Android JNI libraries (`libsqlcipher.so`) and hardware Keystore are unavailable on host JVMs.
 */
object SecurityRuntimeConfig {
    @Volatile
    private var jvmTestMode: Boolean = false

    fun isJvmTestMode(): Boolean = jvmTestMode

    @VisibleForTesting
    internal fun enableJvmTestModeForTests(enabled: Boolean) {
        jvmTestMode = enabled
    }
}
