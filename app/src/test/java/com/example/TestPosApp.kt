package com.example

import com.example.security.SecurityRuntimeConfig

/**
 * Robolectric-only Application subclass (`src/test`) that enables JVM storage test mode before
 * [PosApp.onCreate] initializes Room and BackupEncryptionManager.
 *
 * This completely isolates test-environment behavior inside `src/test` so zero test-framework
 * detection code ships in production APKs.
 */
class TestPosApp : PosApp() {
    override fun onCreate() {
        SecurityRuntimeConfig.enableJvmTestModeForTests(true)
        super.onCreate()
    }
}
