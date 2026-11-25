package com.example.ui.components

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.printer.BluetoothPrinterConnectionManager
import com.example.ui.theme.Bilingual

/**
 * Reusable Compose helper that ensures Android 12+ BLUETOOTH_CONNECT runtime permission
 * is granted before dispatching Bluetooth thermal print jobs, while passing WIFI/SYSTEM
 * modes straight through.
 */
@Composable
fun rememberBluetoothAwarePrintLauncher(
    lang: String,
    onDenied: (() -> Unit)? = null,
    onProceed: () -> Unit
): (connectionType: String) -> Unit {
    val context = LocalContext.current
    val currentOnProceed by rememberUpdatedState(onProceed)
    val currentOnDenied by rememberUpdatedState(onDenied)
    var pendingInvoke by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val shouldProceed = pendingInvoke
        pendingInvoke = false
        if (results.values.all { it }) {
            if (shouldProceed) currentOnProceed()
        } else {
            currentOnDenied?.invoke()
            val appCtx = context.applicationContext ?: context
            val msg = Bilingual.tr(
                lang,
                "إذن البلوتوث مطلوب للطباعة",
                "Autorisation Bluetooth requise pour imprimer",
                "Bluetooth permission is required to print"
            )
            try {
                Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Log.w("BtPermHelper", "Unable to display Bluetooth permission Toast", t)
            }
        }
    }

    return remember(context, lang) {
        { connectionType ->
            val normalized = connectionType.uppercase()
            val needsBt = normalized == "BLUETOOTH" ||
                normalized !in setOf("USB", "OTG", "WIFI", "NETWORK", "IP", "SYSTEM", "ANDROID", "CABLE", "PDF")
            if (needsBt && !BluetoothPrinterConnectionManager.hasConnectPermission(context)) {
                val perms = BluetoothPrinterConnectionManager.requiredConnectPermissions()
                if (perms.isNotEmpty()) {
                    pendingInvoke = true
                    launcher.launch(perms.toTypedArray())
                } else {
                    currentOnProceed()
                }
            } else {
                currentOnProceed()
            }
        }
    }
}
