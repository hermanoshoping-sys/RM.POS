package com.example.ui.viewmodels

import android.content.Context
import android.util.Log
import com.example.data.GlobalSettingsEntity
import com.example.printer.BluetoothPrinterConnectionManager
import com.example.printer.BluetoothPrinterService
import com.example.printer.CashDrawerManager
import com.example.printer.CashDrawerPin
import com.example.printer.PosPrintServiceImpl
import com.example.printer.PrinterDeviceInfo
import com.example.printer.ThermalPrinterConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Modular delegate handling legacy Bluetooth connection helpers, cash drawer pulses,
 * and the built-in POS calculator for SettingsHardwareViewModel.
 */
internal class HardwareControlsDelegate(
    private val scope: CoroutineScope,
    private val languageProvider: () -> String,
    private val settingsProvider: () -> GlobalSettingsEntity,
    private val connectionTypeProvider: () -> String,
    private val printerNameProvider: () -> String,
    private val printerMacProvider: () -> String,
    private val printerIpProvider: () -> String,
    private val printerPortProvider: () -> Int,
    private val paperWidthProvider: () -> Int,
    private val cashDrawerPinProvider: () -> String
) {
    private val _calcInput = MutableStateFlow("")
    val calcInput: StateFlow<String> = _calcInput.asStateFlow()

    private val _calcOutput = MutableStateFlow("0")
    val calcOutput: StateFlow<String> = _calcOutput.asStateFlow()

    fun connectBluetoothPrinter(
        targetContext: Context,
        device: PrinterDeviceInfo,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        scope.launch {
            val result = BluetoothPrinterConnectionManager.connect(
                context = targetContext,
                deviceAddressOrName = device.address.ifBlank { device.name },
                lang = languageProvider()
            )
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val connected = result.getOrNull() ?: device
                    val msg = BluetoothPrinterConnectionManager.lastStatusMessage.value ?: connected.name
                    onResult?.invoke(true, msg)
                } else {
                    val errMsg = BluetoothPrinterConnectionManager.lastStatusMessage.value
                        ?: result.exceptionOrNull()?.localizedMessage
                        ?: "Connection failed"
                    onResult?.invoke(false, errMsg)
                }
            }
        }
    }

    fun reconnectBluetoothPrinter(
        targetContext: Context,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val fallback = printerMacProvider().ifBlank { printerNameProvider() }
        scope.launch {
            val result = BluetoothPrinterConnectionManager.reconnectLastPrinter(
                context = targetContext,
                fallbackDeviceAddressOrName = fallback,
                lang = languageProvider()
            )
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val connected = result.getOrNull()
                    val msg = BluetoothPrinterConnectionManager.lastStatusMessage.value ?: connected?.name.orEmpty()
                    onResult?.invoke(true, msg)
                } else {
                    val errMsg = BluetoothPrinterConnectionManager.lastStatusMessage.value
                        ?: result.exceptionOrNull()?.localizedMessage
                        ?: "Reconnect failed"
                    onResult?.invoke(false, errMsg)
                }
            }
        }
    }

    fun disconnectBluetoothPrinter(onResult: ((Boolean, String) -> Unit)? = null) {
        scope.launch {
            BluetoothPrinterConnectionManager.disconnect()
            withContext(Dispatchers.Main) { onResult?.invoke(true, "") }
        }
    }

    fun printTestBluetoothPage(
        targetContext: Context,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val lang = languageProvider()
                val active = BluetoothPrinterConnectionManager.activeDevice.value
                val target = printerMacProvider().ifBlank { printerNameProvider().ifBlank { active?.address.orEmpty() } }
                val paperMm = paperWidthProvider()
                val receipt = BluetoothPrinterConnectionManager.buildTestPrintReceipt(
                    printerNameOrMac = active?.name ?: target,
                    paperWidthMm = paperMm,
                    settings = settingsProvider(),
                    lang = lang
                )
                val bytes = PosPrintServiceImpl.generateThermalReceiptBytes(
                    receipt = receipt,
                    config = ThermalPrinterConfig(paperWidthMm = paperMm, printQrCode = false)
                )
                val (ok, msg) = BluetoothPrinterConnectionManager.writeBytes(
                    context = targetContext,
                    deviceAddressOrName = target,
                    data = bytes,
                    jobDescription = "Test Page",
                    lang = lang
                )
                withContext(Dispatchers.Main) { onResult?.invoke(ok, msg) }
            } catch (t: Throwable) {
                Log.e("SettingsHardwareVM", "Failed printing Bluetooth test page", t)
                val errMsg = t.localizedMessage ?: "فشل طباعة صفحة الاختبار"
                withContext(Dispatchers.Main) { onResult?.invoke(false, errMsg) }
            }
        }
    }

    fun triggerCashDrawer(
        targetContext: Context,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        try {
            val pin = if (cashDrawerPinProvider() == "PIN_5") CashDrawerPin.PIN_5 else CashDrawerPin.PIN_2
            val target = printerMacProvider().ifBlank { printerNameProvider() }
            val isBt = connectionTypeProvider().equals("BLUETOOTH", ignoreCase = true)
            if (isBt && !BluetoothPrinterService.isBluetoothEnabled(targetContext)) {
                onResult?.invoke(false, "البلوتوث غير مفعّل على الجهاز")
                return
            }
            CashDrawerManager.openCashDrawer(
                context = targetContext,
                connectionType = connectionTypeProvider(),
                printerNameOrMac = target,
                ip = printerIpProvider(),
                port = printerPortProvider(),
                pin = pin,
                pulseOnTimeMs = 100,
                onResult = onResult
            )
        } catch (t: Throwable) {
            Log.e("SettingsHardwareVM", "Failed triggering cash drawer", t)
            onResult?.invoke(false, t.localizedMessage ?: "تعذر فتح درج النقود")
        }
    }

    fun onCalcKey(key: String) {
        when (key) {
            "C" -> { _calcInput.value = ""; _calcOutput.value = "0" }
            "⌫" -> if (_calcInput.value.isNotEmpty()) _calcInput.value = _calcInput.value.dropLast(1)
            "=" -> evaluateCalcExpression()
            else -> _calcInput.value += key
        }
    }

    private fun evaluateCalcExpression() {
        val exp = _calcInput.value
        if (exp.isEmpty()) return
        try {
            val valResult = when {
                exp.contains("+") -> exp.split("+").let { if (it.size == 2) (it[0].toDoubleOrNull() ?: 0.0) + (it[1].toDoubleOrNull() ?: 0.0) else 0.0 }
                exp.contains("-") -> exp.split("-").let { if (it.size == 2) (it[0].toDoubleOrNull() ?: 0.0) - (it[1].toDoubleOrNull() ?: 0.0) else 0.0 }
                exp.contains("*") -> exp.split("*").let { if (it.size == 2) (it[0].toDoubleOrNull() ?: 0.0) * (it[1].toDoubleOrNull() ?: 0.0) else 0.0 }
                exp.contains("/") -> exp.split("/").let {
                    if (it.size == 2) {
                        val d = it[1].toDoubleOrNull() ?: 0.0
                        if (d != 0.0) (it[0].toDoubleOrNull() ?: 0.0) / d else 0.0
                    } else 0.0
                }
                else -> exp.toDoubleOrNull() ?: 0.0
            }
            _calcOutput.value = String.format(Locale.US, "%.2f", valResult)
        } catch (e: Exception) {
            Log.w("SettingsHardwareVM", "Calculator expression error: $exp", e)
            _calcOutput.value = "Error"
        }
    }
}
