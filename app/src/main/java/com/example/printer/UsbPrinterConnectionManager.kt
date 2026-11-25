package com.example.printer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ui.theme.Bilingual
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed class UsbPrinterConnectionState {
    data class Disconnected(val reason: String? = null) : UsbPrinterConnectionState()
    data class Connecting(val deviceName: String, val deviceKey: String) : UsbPrinterConnectionState()
    data class Connected(val device: ThermalPrinterDevice, val connectedAtMs: Long) : UsbPrinterConnectionState()
    data class Printing(val device: ThermalPrinterDevice, val jobDescription: String) : UsbPrinterConnectionState()
    data class Error(val message: String, val lastDevice: ThermalPrinterDevice? = null) : UsbPrinterConnectionState()
}

/**
 * Dedicated Android USB Host connection manager for ESC/POS USB thermal printers.
 * Supports device discovery, USB runtime permission requests, bulk OUT transfers,
 * detach detection, and safe resource cleanup.
 */
object UsbPrinterConnectionManager {
    private const val TAG = "UsbPrinterConnMgr"
    private const val ACTION_USB_PERMISSION = "com.example.printer.ACTION_USB_PRINTER_PERMISSION"
    private const val BULK_CHUNK_SIZE = 4096
    private const val BULK_TIMEOUT_MS = 4000
    private const val PERMISSION_TIMEOUT_MS = 12000L

    /** Common POS thermal printer USB Vendor IDs (Epson, Star, Citizen, Bixolon, Xprinter, Gprinter, Rongta, Posiflex, Sunmi, etc.) */
    private val KNOWN_POS_VENDOR_IDS = setOf(
        0x04B8, // Seiko Epson
        0x0519, // Star Micronics
        0x1D90, // Citizen
        0x1504, // Bixolon
        0x0483, // STMicroelectronics (many Xprinter/Rongta boards)
        0x0FE6, // ICS Advent / Kontron / generic POS58/POS80
        0x1FC9, // NXP (Xprinter)
        0x28E9, // GD32 (thermal POS printers)
        0x0416, // Winbond (POS thermal)
        0x0DD4, // Custom Engineering
        0x0456, // Analog Devices / thermal bridges
        0x1A86, // QinHeng CH340/CH341 USB-to-Serial POS printers
        0x067B, // Prolific PL2303
        0x0403, // FTDI
        0x20D1, // Rongta
        0x0493, // MAG / POS
        0x6868, // Generic 58mm/80mm thermal
        0x0525  // Netchip / Sunmi USB printer gadget
    )

    private val mutex = Mutex()
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var activeConnection: UsbDeviceConnection? = null
    private var activeInterface: UsbInterface? = null
    private var activeOutEndpoint: UsbEndpoint? = null
    private var activeUsbDevice: UsbDevice? = null

    private val detachReceiverRegistered = AtomicBoolean(false)
    @Volatile private var appContextRef: Context? = null

    internal var discoverOverrideForTest: ((Context) -> List<ThermalPrinterDevice>)? = null
    internal var connectOverrideForTest: ((Context, String, String) -> Result<ThermalPrinterDevice>)? = null
    internal var writeOverrideForTest: ((Context, String, ByteArray, String) -> Pair<Boolean, String>)? = null

    private val _connectionState = MutableStateFlow<UsbPrinterConnectionState>(UsbPrinterConnectionState.Disconnected())
    val connectionState: StateFlow<UsbPrinterConnectionState> = _connectionState.asStateFlow()

    private val _activeDevice = MutableStateFlow<ThermalPrinterDevice?>(null)
    val activeDevice: StateFlow<ThermalPrinterDevice?> = _activeDevice.asStateFlow()

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            val detached = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val current = activeUsbDevice
            if (detached != null && current != null && detached.deviceName == current.deviceName) {
                closeInternal()
                val msg = "تم فصل كابل طابعة USB (${current.productName ?: current.deviceName})"
                _connectionState.value = UsbPrinterConnectionState.Error(msg, _activeDevice.value)
            }
        }
    }

    fun startMonitoring(context: Context) {
        val appCtx = context.applicationContext ?: context
        appContextRef = appCtx
        if (detachReceiverRegistered.compareAndSet(false, true)) {
            val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
            try {
                ContextCompat.registerReceiver(appCtx, detachReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            } catch (e: Exception) {
                detachReceiverRegistered.set(false)
                Log.w(TAG, "Unable to register USB detach receiver", e)
            }
        }
    }

    fun stopMonitoring(context: Context? = null) {
        val ctx = context?.applicationContext ?: context ?: appContextRef
        if (detachReceiverRegistered.compareAndSet(true, false) && ctx != null) {
            try {
                ctx.unregisterReceiver(detachReceiver)
            } catch (e: Exception) {
                Log.d(TAG, "USB detach receiver already unregistered", e)
            }
        }
    }

    fun isConnected(): Boolean = activeConnection != null && activeOutEndpoint != null

    fun discoverUsbPrinters(context: Context): List<ThermalPrinterDevice> {
        discoverOverrideForTest?.let { return it.invoke(context) }
        return try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
            val devices = usbManager.deviceList?.values ?: return emptyList()
            devices.mapNotNull { usb ->
                val pair = findPrinterBulkOutEndpoint(usb)
                val isPrinterClass = usb.deviceClass == UsbConstants.USB_CLASS_PRINTER ||
                    pair?.first?.interfaceClass == UsbConstants.USB_CLASS_PRINTER
                val isKnownVendor = KNOWN_POS_VENDOR_IDS.contains(usb.vendorId)
                if (pair == null && !isPrinterClass) return@mapNotNull null
                if (!isPrinterClass && !isKnownVendor && pair == null) return@mapNotNull null

                val label = usb.productName?.takeIf { it.isNotBlank() }
                    ?: usb.manufacturerName?.takeIf { it.isNotBlank() }?.let { "$it USB Printer" }
                    ?: "USB Thermal Printer (${String.format("%04X:%04X", usb.vendorId, usb.productId)})"
                val hasPerm = usbManager.hasPermission(usb)
                ThermalPrinterDevice(
                    id = usb.deviceName,
                    name = label,
                    address = usb.deviceName,
                    transport = PrinterTransport.USB,
                    isPairedOrAuthorized = hasPerm,
                    vendorId = usb.vendorId,
                    productId = usb.productId,
                    details = "VID:${String.format("%04X", usb.vendorId)} PID:${String.format("%04X", usb.productId)}" +
                        if (hasPerm) " • جاهز" else " • يتطلب إذن USB"
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error discovering USB thermal printers", t)
            emptyList()
        }
    }

    suspend fun connect(
        context: Context,
        deviceAddressOrName: String,
        lang: String = "AR"
    ): Result<ThermalPrinterDevice> = withContext(ioDispatcher) {
        startMonitoring(context)
        mutex.withLock { connectInternal(context, deviceAddressOrName, lang) }
    }

    suspend fun disconnect(reason: String? = null) = withContext(ioDispatcher) {
        mutex.withLock {
            closeInternal()
            _connectionState.value = UsbPrinterConnectionState.Disconnected(reason)
        }
    }

    suspend fun writeBytes(
        context: Context,
        deviceAddressOrName: String,
        data: ByteArray,
        jobDescription: String = "Receipt",
        lang: String = "AR"
    ): Pair<Boolean, String> = withContext(ioDispatcher) {
        writeOverrideForTest?.let { override ->
            return@withContext override.invoke(context, deviceAddressOrName, data, lang)
        }
        startMonitoring(context)
        mutex.withLock {
            val target = deviceAddressOrName.ifBlank { _activeDevice.value?.address.orEmpty() }
            if (!isConnected() || (target.isNotBlank() && activeUsbDevice?.deviceName != target && _activeDevice.value?.name != target)) {
                val connRes = connectInternal(context, target, lang)
                if (connRes.isFailure) {
                    val err = (_connectionState.value as? UsbPrinterConnectionState.Error)?.message
                        ?: connRes.exceptionOrNull()?.localizedMessage
                        ?: Bilingual.tr(lang, "تعذر الاتصال بطابعة USB", "Échec de connexion USB", "USB printer connection failed")
                    return@withLock false to err
                }
            }

            val devInfo = _activeDevice.value ?: ThermalPrinterDevice(
                id = target.ifBlank { "usb" },
                name = "USB Printer",
                address = target,
                transport = PrinterTransport.USB
            )
            _connectionState.value = UsbPrinterConnectionState.Printing(devInfo, jobDescription)

            return@withLock try {
                val conn = activeConnection ?: throw IOException("USB connection closed")
                val endpoint = activeOutEndpoint ?: throw IOException("USB bulk OUT endpoint unavailable")
                sendChunked(conn, endpoint, data)
                _connectionState.value = UsbPrinterConnectionState.Connected(devInfo, System.currentTimeMillis())
                true to Bilingual.tr(
                    lang,
                    "تمت الطباعة بنجاح عبر USB على ${devInfo.name}",
                    "Impression USB réussie sur ${devInfo.name}",
                    "Printed successfully via USB on ${devInfo.name}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "USB bulk transfer failed", e)
                closeInternal()
                val errMsg = Bilingual.tr(
                    lang,
                    "فشل إرسال البيانات عبر USB (${devInfo.name}): ${e.localizedMessage ?: "تأكد من كابل OTG"}",
                    "Échec d'envoi USB (${devInfo.name}) : ${e.localizedMessage ?: "Vérifiez le câble OTG"}",
                    "USB send failed (${devInfo.name}): ${e.localizedMessage ?: "Check OTG cable"}"
                )
                _connectionState.value = UsbPrinterConnectionState.Error(errMsg, devInfo)
                false to errMsg
            }
        }
    }

    private suspend fun connectInternal(
        context: Context,
        deviceAddressOrName: String,
        lang: String
    ): Result<ThermalPrinterDevice> {
        connectOverrideForTest?.let { override ->
            val res = override.invoke(context, deviceAddressOrName, lang)
            res.getOrNull()?.let { dev ->
                _activeDevice.value = dev
                _connectionState.value = UsbPrinterConnectionState.Connected(dev, System.currentTimeMillis())
            } ?: run {
                val msg = res.exceptionOrNull()?.localizedMessage ?: "USB connection failed"
                _connectionState.value = UsbPrinterConnectionState.Error(msg, _activeDevice.value)
            }
            return res
        }

        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        if (usbManager == null) {
            val msg = Bilingual.tr(lang, "هذا الجهاز لا يدعم وضع USB Host / OTG", "USB Host / OTG non supporté", "USB Host / OTG not supported")
            _connectionState.value = UsbPrinterConnectionState.Error(msg, _activeDevice.value)
            return Result.failure(IllegalStateException(msg))
        }

        val candidates = usbManager.deviceList?.values?.toList().orEmpty()
        val matchedUsb = resolveTargetUsbDevice(candidates, deviceAddressOrName)
        if (matchedUsb == null) {
            val msg = Bilingual.tr(
                lang,
                "لم يتم العثور على طابعة USB متصلة. تأكد من توصيل كابل OTG وتشغيل الطابعة",
                "Aucune imprimante USB détectée. Vérifiez le câble OTG et l'alimentation",
                "No USB thermal printer detected. Check OTG cable and power"
            )
            _connectionState.value = UsbPrinterConnectionState.Error(msg, _activeDevice.value)
            return Result.failure(IllegalStateException(msg))
        }

        val resolvedName = matchedUsb.productName?.takeIf { it.isNotBlank() } ?: matchedUsb.deviceName
        val devInfo = ThermalPrinterDevice(
            id = matchedUsb.deviceName,
            name = resolvedName,
            address = matchedUsb.deviceName,
            transport = PrinterTransport.USB,
            isPairedOrAuthorized = true,
            vendorId = matchedUsb.vendorId,
            productId = matchedUsb.productId
        )

        if (isConnected() && activeUsbDevice?.deviceName == matchedUsb.deviceName) {
            _activeDevice.value = devInfo
            _connectionState.value = UsbPrinterConnectionState.Connected(devInfo, System.currentTimeMillis())
            return Result.success(devInfo)
        }

        closeInternal()
        _connectionState.value = UsbPrinterConnectionState.Connecting(resolvedName, matchedUsb.deviceName)

        if (!usbManager.hasPermission(matchedUsb)) {
            val granted = requestUsbPermissionSuspend(context, usbManager, matchedUsb)
            if (!granted) {
                val msg = Bilingual.tr(
                    lang,
                    "تم رفض إذن الوصول إلى طابعة USB ($resolvedName)",
                    "Autorisation USB refusée pour ($resolvedName)",
                    "USB permission denied for ($resolvedName)"
                )
                _connectionState.value = UsbPrinterConnectionState.Error(msg, devInfo)
                return Result.failure(SecurityException(msg))
            }
        }

        val ifaceAndEndpoint = findPrinterBulkOutEndpoint(matchedUsb)
        if (ifaceAndEndpoint == null) {
            val msg = Bilingual.tr(
                lang,
                "جهاز USB المحدد لا يحتوي على منفذ طباعة حرارية (Bulk OUT)",
                "Ce périphérique USB ne dispose pas d'un port d'impression (Bulk OUT)",
                "Selected USB device has no Bulk OUT printer endpoint"
            )
            _connectionState.value = UsbPrinterConnectionState.Error(msg, devInfo)
            return Result.failure(IllegalStateException(msg))
        }

        val connection = usbManager.openDevice(matchedUsb)
        if (connection == null) {
            val msg = Bilingual.tr(lang, "تعذر فتح الاتصال بجهاز USB ($resolvedName)", "Impossible d'ouvrir le périphérique USB", "Unable to open USB device")
            _connectionState.value = UsbPrinterConnectionState.Error(msg, devInfo)
            return Result.failure(IOException(msg))
        }

        val (usbIface, outEndpoint) = ifaceAndEndpoint
        if (!connection.claimInterface(usbIface, true)) {
            connection.close()
            val msg = Bilingual.tr(lang, "تعذر حجز واجهة طابعة USB ($resolvedName)", "Impossible de réclamer l'interface USB", "Unable to claim USB printer interface")
            _connectionState.value = UsbPrinterConnectionState.Error(msg, devInfo)
            return Result.failure(IOException(msg))
        }

        activeConnection = connection
        activeInterface = usbIface
        activeOutEndpoint = outEndpoint
        activeUsbDevice = matchedUsb
        _activeDevice.value = devInfo
        _connectionState.value = UsbPrinterConnectionState.Connected(devInfo, System.currentTimeMillis())
        return Result.success(devInfo)
    }

    private fun resolveTargetUsbDevice(devices: List<UsbDevice>, query: String): UsbDevice? {
        if (devices.isEmpty()) return null
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            devices.firstOrNull {
                it.deviceName.equals(trimmed, ignoreCase = true) ||
                    it.productName?.equals(trimmed, ignoreCase = true) == true
            }?.let { return it }
        }
        return devices.firstOrNull { it.deviceClass == UsbConstants.USB_CLASS_PRINTER || findPrinterBulkOutEndpoint(it)?.first?.interfaceClass == UsbConstants.USB_CLASS_PRINTER }
            ?: devices.firstOrNull { KNOWN_POS_VENDOR_IDS.contains(it.vendorId) && findPrinterBulkOutEndpoint(it) != null }
            ?: devices.firstOrNull { findPrinterBulkOutEndpoint(it) != null }
    }

    private fun findPrinterBulkOutEndpoint(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        var fallback: Pair<UsbInterface, UsbEndpoint>? = null
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
                    if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                        return iface to ep
                    }
                    if (fallback == null) fallback = iface to ep
                }
            }
        }
        return fallback
    }

    private fun sendChunked(connection: UsbDeviceConnection, endpoint: UsbEndpoint, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val len = minOf(BULK_CHUNK_SIZE, data.size - offset)
            val chunk = if (offset == 0 && len == data.size) data else data.copyOfRange(offset, offset + len)
            val written = connection.bulkTransfer(endpoint, chunk, len, BULK_TIMEOUT_MS)
            if (written < 0) {
                throw IOException("USB bulkTransfer returned $written at offset $offset")
            }
            offset += written.coerceAtLeast(len)
        }
    }

    private suspend fun requestUsbPermissionSuspend(
        context: Context,
        usbManager: UsbManager,
        device: UsbDevice
    ): Boolean {
        val appCtx = context.applicationContext ?: context
        val deferred = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent?) {
                if (intent?.action == ACTION_USB_PERMISSION) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    deferred.complete(granted)
                }
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        return try {
            ContextCompat.registerReceiver(appCtx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appCtx,
                device.deviceId,
                Intent(ACTION_USB_PERMISSION).setPackage(appCtx.packageName),
                flags
            )
            usbManager.requestPermission(device, pendingIntent)
            withTimeoutOrNull(PERMISSION_TIMEOUT_MS) { deferred.await() } ?: usbManager.hasPermission(device)
        } catch (e: Exception) {
            Log.e(TAG, "USB permission request error", e)
            false
        } finally {
            runCatching { appCtx.unregisterReceiver(receiver) }
        }
    }

    private fun closeInternal() {
        try {
            activeInterface?.let { activeConnection?.releaseInterface(it) }
        } catch (e: Exception) {
            Log.d(TAG, "USB releaseInterface warning", e)
        }
        try {
            activeConnection?.close()
        } catch (e: Exception) {
            Log.d(TAG, "USB close warning", e)
        }
        activeConnection = null
        activeInterface = null
        activeOutEndpoint = null
        activeUsbDevice = null
    }

    internal fun resetForTesting() {
        stopMonitoring()
        closeInternal()
        discoverOverrideForTest = null
        connectOverrideForTest = null
        writeOverrideForTest = null
        _activeDevice.value = null
        _connectionState.value = UsbPrinterConnectionState.Disconnected()
    }
}
