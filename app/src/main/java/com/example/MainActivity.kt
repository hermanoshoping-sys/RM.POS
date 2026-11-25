package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.DatabaseInitResult
import com.example.di.PosViewModelFactory
import com.example.printer.BluetoothPrinterService
import com.example.ui.screens.DatabaseRecoveryScreen
import com.example.ui.screens.MainPOSApplication
import com.example.ui.theme.Bilingual
import com.example.ui.theme.RmPosTheme
import com.example.ui.viewmodels.PosViewModel
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var cachedPosViewModel: PosViewModel? = null

    private fun getOrCreatePosViewModel(): PosViewModel {
        cachedPosViewModel?.let { return it }
        val factory = PosViewModelFactory(
            application,
            (application as PosApp).appContainer
        )
        return ViewModelProvider(this, factory)[PosViewModel::class.java].also {
            cachedPosViewModel = it
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_language", Bilingual.AR) ?: Bilingual.AR
        val locale = Bilingual.localeFor(savedLang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(newBase)
        try {
            applyOverrideConfiguration(config)
        } catch (t: Throwable) {
            Log.w("MainActivity", "applyOverrideConfiguration skipped", t)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BluetoothPrinterService.attachForegroundActivity(this)
        val prefs = getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_language", Bilingual.AR) ?: Bilingual.AR
        val savedDarkMode = prefs.getBoolean("dark_mode", false)
        Locale.setDefault(Bilingual.localeFor(savedLang))

        try {
            val stockMonitorRequest = PeriodicWorkRequestBuilder<StockMonitorWorker>(2, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "StockMonitorWork",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                stockMonitorRequest
            )
        } catch (t: Throwable) {
            Log.e("MainActivity", "Failed to enqueue StockMonitorWorker", t)
        }

        val appContainer = (application as PosApp).appContainer

        enableEdgeToEdge()
        setContent {
            val dbInitState by appContainer.databaseInitState.collectAsStateWithLifecycle()
            when (val state = dbInitState) {
                is DatabaseInitResult.Ready -> {
                    val posViewModel = remember { getOrCreatePosViewModel() }
                    MainPosReadyContent(viewModel = posViewModel)
                }
                is DatabaseInitResult.Failed -> {
                    var isRetrying by remember { mutableStateOf(false) }
                    RmPosTheme(darkTheme = savedDarkMode) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            DatabaseRecoveryScreen(
                                failure = state,
                                lang = savedLang,
                                isRetrying = isRetrying,
                                onRetry = {
                                    isRetrying = true
                                    appContainer.retryDatabaseInitialization()
                                    isRetrying = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        BluetoothPrinterService.attachForegroundActivity(this)
        cachedPosViewModel?.checkLicenseExpirationWithAntiRollback()
    }

    override fun onDestroy() {
        BluetoothPrinterService.detachForegroundActivity(this)
        super.onDestroy()
    }
}

@Composable
private fun MainPosReadyContent(viewModel: PosViewModel) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    RmPosTheme(darkTheme = isDarkMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainPOSApplication(viewModel = viewModel)
        }
    }
}
