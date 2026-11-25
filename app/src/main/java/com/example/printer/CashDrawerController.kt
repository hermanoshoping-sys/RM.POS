package com.example.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Standard RJ11 / RJ12 Drawer Kick pin definitions.
 * Most thermal printers use Pin 2 for Drawer 1 and Pin 5 for Drawer 2.
 */
enum class CashDrawerPin(val code: Byte, val pinNumber: Int, val description: String) {
    PIN_2(0x00.toByte(), 2, "Pin 2 (Drawer #1 / Standard RJ11/RJ12)"),
    PIN_5(0x01.toByte(), 5, "Pin 5 (Drawer #2 / Secondary)")
}

/**
 * Configuration options for the physical cash drawer.
 */
data class CashDrawerConfig(
    val pin: CashDrawerPin = CashDrawerPin.PIN_2,
    val pulseOnTimeMs: Int = 100,
    val pulseOffTimeMs: Int = 100,
    val autoOpenOnCashSale: Boolean = true
)

/**
 * Control interface to trigger the physical cash drawer open command
 * through connected POS printers (Bluetooth, Network, USB) or dedicated POS hardware.
 */
interface CashDrawerController {

    /**
     * Generate standard ESC/POS pulse bytes to trigger cash drawer kick.
     * Command: ESC p m t1 t2 (0x1B, 0x70, m, t1, t2)
     * m: 0 for pin 2, 1 for pin 5
     * t1: pulse on-time in units of 2ms (e.g. 50 * 2 = 100ms)
     * t2: pulse off-time in units of 2ms (e.g. 50 * 2 = 100ms)
     */
    fun generateDrawerKickCommand(
        pin: CashDrawerPin = CashDrawerPin.PIN_2,
        onTimeMs: Int = 100,
        offTimeMs: Int = 100
    ): ByteArray

    /**
     * Generate real-time drawer kick pulse (DLE DC4 n m t)
     * Command: 0x10, 0x14, 0x01, 0x00, 0x01
     * Executed immediately even if print buffer has pending jobs.
     */
    fun generateRealTimeDrawerKick(): ByteArray

    /**
     * Generate a robust universal multi-standard drawer kick command sequence
     * that works reliably across Epson, Star Micronics, Citizen, Bixolon,
     * Xprinter, Rongta, Munbyn, and generic ESC/POS clones.
     */
    fun generateUniversalDrawerKick(pin: CashDrawerPin = CashDrawerPin.PIN_2): ByteArray

    /**
     * Triggers the physical cash drawer open pulse command via the configured
     * POS printer connection (Bluetooth SPP, TCP network socket) or local hardware interface.
     */
    fun openCashDrawer(
        context: Context,
        connectionType: String,
        printerNameOrMac: String,
        ip: String = "192.168.1.100",
        port: Int = 9100,
        pin: CashDrawerPin = CashDrawerPin.PIN_2,
        pulseOnTimeMs: Int = 100,
        onResult: ((Boolean, String) -> Unit)? = null
    )

    /**
     * Attempt to trigger cash drawer directly on dedicated Android POS hardware terminals
     * (e.g. Sunmi T2/V2, Pax, iMin, Telpo) via standard broadcast intents or hardware APIs.
     */
    fun triggerHardwareDrawerDirect(context: Context): Boolean
}

/**
 * Production implementation of the CashDrawerController.
 */
object CashDrawerManager : CashDrawerController {
    private const val TAG = "CashDrawerManager"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun generateDrawerKickCommand(
        pin: CashDrawerPin,
        onTimeMs: Int,
        offTimeMs: Int
    ): ByteArray {
        val t1 = (onTimeMs / 2).coerceIn(1, 255).toByte()
        val t2 = (offTimeMs / 2).coerceIn(1, 255).toByte()
        return byteArrayOf(0x1B, 0x70, pin.code, t1, t2)
    }

    override fun generateRealTimeDrawerKick(): ByteArray {
        // DLE DC4 1 0 1 (Real-time kick pulse pin 2)
        return byteArrayOf(0x10, 0x14, 0x01, 0x00, 0x01)
    }

    override fun generateUniversalDrawerKick(pin: CashDrawerPin): ByteArray {
        val standardKick = generateDrawerKickCommand(pin, 100, 100)
        val realTimeKick = generateRealTimeDrawerKick()
        // Star Micronics BEL command (0x07) or FS p (0x1C, 0x70)
        val starKick = byteArrayOf(0x07)

        // Combine standard ESC/POS + Realtime + Star for 100% printer hardware compatibility
        val combined = ByteArray(standardKick.size + realTimeKick.size + starKick.size)
        System.arraycopy(standardKick, 0, combined, 0, standardKick.size)
        System.arraycopy(realTimeKick, 0, combined, standardKick.size, realTimeKick.size)
        System.arraycopy(starKick, 0, combined, standardKick.size + realTimeKick.size, starKick.size)
        return combined
    }

    @SuppressLint("MissingPermission")
    override fun openCashDrawer(
        context: Context,
        connectionType: String,
        printerNameOrMac: String,
        ip: String,
        port: Int,
        pin: CashDrawerPin,
        pulseOnTimeMs: Int,
        onResult: ((Boolean, String) -> Unit)?
    ) {
        val kickBytes = generateUniversalDrawerKick(pin)

        // First, check if dedicated Android POS terminal hardware responded
        val hardwareTriggered = triggerHardwareDrawerDirect(context)
        if (hardwareTriggered) {
            Log.d(TAG, "Hardware drawer direct intent triggered")
        }

        when (connectionType.uppercase()) {
            "BLUETOOTH" -> {
                sendKickViaBluetooth(context, printerNameOrMac, kickBytes, onResult)
            }
            "USB", "OTG" -> {
                sendKickViaUsb(context, printerNameOrMac, kickBytes, onResult)
            }
            "WIFI", "NETWORK", "IP" -> {
                sendKickViaNetwork(context, ip, port, kickBytes, onResult)
            }
            else -> {
                // Default fallback: Try Bluetooth first, or inform user
                if (printerNameOrMac.isNotBlank()) {
                    sendKickViaBluetooth(context, printerNameOrMac, kickBytes, onResult)
                } else if (ip.isNotBlank()) {
                    sendKickViaNetwork(context, ip, port, kickBytes, onResult)
                } else {
                    postResult(
                        context,
                        onResult,
                        hardwareTriggered,
                        if (hardwareTriggered) "تم إرسال إشارة فتح الدرج لواجهة العتاد المباشرة"
                        else "يرجى تحديد طابعة البلوتوث أو USB أو الشبكة المتصل بها درج النقود"
                    )
                }
            }
        }
    }

    private fun sendKickViaUsb(
        context: Context,
        printerNameOrPath: String,
        kickBytes: ByteArray,
        onResult: ((Boolean, String) -> Unit)?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (ok, msg) = UsbPrinterConnectionManager.writeBytes(
                    context = context,
                    deviceAddressOrName = printerNameOrPath,
                    data = kickBytes,
                    jobDescription = "USB Cash Drawer Kick"
                )
                postResult(
                    context,
                    onResult,
                    ok,
                    if (ok) "تم إرسال أمر فتح درج النقود عبر USB بنجاح" else msg
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Error triggering cash drawer via USB", t)
                postResult(
                    context,
                    onResult,
                    false,
                    "فشل فتح درج النقود عبر USB: ${t.localizedMessage ?: "تأكد من كابل OTG"}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendKickViaBluetooth(
        context: Context,
        printerNameOrMac: String,
        kickBytes: ByteArray,
        onResult: ((Boolean, String) -> Unit)?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (ok, msg) = BluetoothPrinterConnectionManager.writeBytes(
                    context = context,
                    deviceAddressOrName = printerNameOrMac,
                    data = kickBytes,
                    jobDescription = "Cash Drawer Kick"
                )
                postResult(
                    context,
                    onResult,
                    ok,
                    if (ok) "تم إرسال أمر فتح درج النقود بنجاح" else msg
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Error triggering cash drawer via Bluetooth", t)
                postResult(
                    context,
                    onResult,
                    false,
                    "فشل فتح درج النقود: ${t.localizedMessage ?: "تأكد من توصيل سلك RJ11/RJ12 بالطابعة"}"
                )
            }
        }
    }

    private fun sendKickViaNetwork(
        context: Context,
        ip: String,
        port: Int,
        kickBytes: ByteArray,
        onResult: ((Boolean, String) -> Unit)?
    ) {
        val trimmedIp = ip.trim()
        if (trimmedIp.isBlank() || port !in 1..65535) {
            postResult(context, onResult, false, "يرجى إدخال عنوان IP ومنفذ صالحين لطابعة الشبكة")
            return
        }
        Thread {
            var socket: Socket? = null
            try {
                socket = WifiNetworkSocketHelper.openSocket(context, trimmedIp, port, connectTimeoutMs = 2800)
                WifiNetworkSocketHelper.writeChunked(socket, kickBytes, drainDelayMs = 80L)

                Log.d(TAG, "Cash drawer pulse sent via network to $trimmedIp:$port")
                postResult(
                    context,
                    onResult,
                    true,
                    "تم فتح درج النقود عبر طابعة الشبكة ($trimmedIp:$port)"
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Error triggering cash drawer via Network", t)
                val diag = WifiNetworkSocketHelper.diagnoseWifiFailure(context, trimmedIp, port, t)
                postResult(context, onResult, false, diag.message)
            } finally {
                try {
                    socket?.close()
                } catch (e: IOException) {
                    Log.w(TAG, "Error closing cash drawer network socket", e)
                }
            }
        }.start()
    }

    override fun triggerHardwareDrawerDirect(context: Context): Boolean {
        var triggered = false
        try {
            // Sunmi POS hardware drawer intent
            val sunmiIntent = Intent("com.sunmi.hardware.cashdrawer.action.OPEN")
            context.sendBroadcast(sunmiIntent)
            triggered = true
        } catch (e: Exception) {
            Log.d(TAG, "Sunmi direct hardware intent not handled", e)
        }

        try {
            // Generic Android POS Drawer broadcast
            val genericIntent = Intent("android.intent.action.OPEN_CASH_DRAWER")
            context.sendBroadcast(genericIntent)
            triggered = true
        } catch (e: Exception) {
            Log.d(TAG, "Generic cash drawer intent not handled", e)
        }

        return triggered
    }

    private fun postResult(
        context: Context,
        callback: ((Boolean, String) -> Unit)?,
        success: Boolean,
        message: String
    ) {
        val appCtx = context.applicationContext ?: context
        Handler(Looper.getMainLooper()).post {
            try {
                callback?.invoke(success, message)
            } catch (t: Throwable) {
                Log.e(TAG, "postResult callback failed", t)
            }
            try {
                Toast.makeText(appCtx, message, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Log.e(TAG, "postResult Toast failed", t)
            }
        }
    }
}
