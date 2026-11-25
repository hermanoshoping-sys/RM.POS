package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import com.example.util.CrashHandler

/**
 * Base Application class maintaining the Dependency Injection container lifecycle
 * and registering global error handling and diagnostics.
 */
open class PosApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.example.ui.theme.Bilingual.init(this)
        com.example.security.DatabaseEncryptionManager.loadNativeLibrary()
        CrashHandler.initialize(this)
        appContainer = DefaultAppContainer(this)
    }
}
