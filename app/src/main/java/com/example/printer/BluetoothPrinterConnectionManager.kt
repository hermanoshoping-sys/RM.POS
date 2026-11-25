package com.example.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.GlobalSettingsEntity
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.CartItem
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed class BtPrinterConnectionState {
    data class Disconnected(val reason: String? = null) : BtPrinterConnectionState()
    data class Connecting(val deviceName: String, val address: String) : BtPrinterConnectionState()
    data class Connected(val device: PrinterDeviceInfo, val connectedAtMs: Long) : BtPrinterConnectionState()
    data class Printing(val device: PrinterDeviceInfo, val jobDescription: String) : BtPrinterConnectionState()
    data class Reconnecting(
        val device: PrinterDeviceInfo,
        val attempt: Int,
        val maxAttempts: Int,
        val reason: String? = null
    ) : BtPrinterConnectionState()
    data class Error(val message: String, val lastDevice: PrinterDeviceInfo? = null) : BtPrinterConnectionState()
}

/**
 * Stateful connection manager for Bluetooth SPP thermal receipt printers.
 * Maintains a reusable RFCOMM socket with serialized writes, insecure fallback,
 * auto-reconnect on unexpected disconnect, and live StateFlow status.
 */
object BluetoothPrinterConnectionManager {
    private const val TAG = "BtPrinterConnMgr"
    val SPP_UUID: UUID get() = BluetoothRfcommSocketHelper.SPP_UUID

    private val mutex = Mutex()
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var activeSocket: BluetoothSocket? = null
    private var activeOutStream: OutputStream? = null

    @Volatile private var lastConnectedDevice: PrinterDeviceInfo? = null
    @Volatile private var manualDisconnectRequested: Boolean = false
    @Volatile var autoReconnectEnabled: Boolean = true
    @Volatile private var lastLanguage: String = "AR"
    @Volatile private var appContextRef: Context? = null

    private var reconnectJob: Job? = null
    private val receiverRegistered = AtomicBoolean(false)

    internal var reconnectDelaysMs: LongArray = longArrayOf(1500L, 3000L, 6000L)
    internal var connectOverrideForTest: ((Context, String, String) -> Result<PrinterDeviceInfo>)? = null

    private val _connectionState = MutableStateFlow<BtPrinterConnectionState>(BtPrinterConnectionState.Disconnected())
    val connectionState: StateFlow<BtPrinterConnectionState> = _connectionState.asStateFlow()

    private val _activeDevice = MutableStateFlow<PrinterDeviceInfo?>(null)
    val activeDevice: StateFlow<PrinterDeviceInfo?> = _activeDevice.asStateFlow()

    private val _lastStatusMessage = MutableStateFlow<String?>(null)
    val lastStatusMessage: StateFlow<String?> = _lastStatusMessage.asStateFlow()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent != null) onBluetoothBroadcast(context, intent)
        }
    }

    fun startConnectionMonitoring(context: Context, lang: String = lastLanguage) {
        val appCtx = context.applicationContext ?: context
        appContextRef = appCtx
        lastLanguage = lang
        if (receiverRegistered.compareAndSet(false, true)) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            try {
                ContextCompat.registerReceiver(appCtx, bluetoothStateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            } catch (e: Exception) {
                receiverRegistered.set(false)
                Log.w(TAG, "Failed to register Bluetooth monitor receiver", e)
            }
        }
    }

    fun stopConnectionMonitoring(context: Context? = null) {
        reconnectJob?.cancel()
        reconnectJob = null
        val targetCtx = context?.applicationContext ?: context ?: appContextRef
        if (receiverRegistered.compareAndSet(true, false) && targetCtx != null) {
            try {
                targetCtx.unregisterReceiver(bluetoothStateReceiver)
            } catch (e: Exception) {
                Log.d(TAG, "Bluetooth monitor receiver already unregistered", e)
            }
        }
    }

    @Suppress("DEPRECATION")
    internal fun onBluetoothBroadcast(context: Context, intent: Intent) {
        appContextRef = context.applicationContext ?: context
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val trackedAddress = _activeDevice.value?.address ?: lastConnectedDevice?.address
                if (device?.address != null && trackedAddress != null && device.address.equals(trackedAddress, ignoreCase = true)) {
                    val reason = Bilingual.tr(lastLanguage, "انقطع اتصال البلوتوث بشكل غير متوقع", "Connexion Bluetooth interrompue", "Bluetooth link lost unexpectedly")
                    handleUnexpectedDisconnect(device.address, reason, context, lastLanguage)
                }
            }
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                        reconnectJob?.cancel()
                        reconnectJob = null
                        closeSocketInternal()
                        val known = _activeDevice.value ?: lastConnectedDevice
                        if (known != null) {
                            val msg = Bilingual.tr(lastLanguage, "تم إيقاف تشغيل البلوتوث", "Bluetooth désactivé", "Bluetooth was turned off")
                            _connectionState.value = BtPrinterConnectionState.Error(msg, known)
                            _lastStatusMessage.value = msg
                        } else {
                            _connectionState.value = BtPrinterConnectionState.Disconnected()
                        }
                    }
                    BluetoothAdapter.STATE_ON -> {
                        val known = lastConnectedDevice ?: _activeDevice.value
                        if (!manualDisconnectRequested && autoReconnectEnabled && known != null && !isSocketConnected()) {
                            val reason = Bilingual.tr(lastLanguage, "إعادة الاتصال بعد تشغيل البلوتوث", "Reconnexion après activation Bluetooth", "Bluetooth turned back on")
                            scheduleAutoReconnect(context, known, reason, lastLanguage)
                        }
                    }
                }
            }
        }
    }

    internal fun handleUnexpectedDisconnect(
        address: String?,
        reason: String,
        context: Context? = appContextRef,
        lang: String = lastLanguage
    ) {
        closeSocketInternal()
        val tracked = _activeDevice.value ?: lastConnectedDevice
        if (tracked == null || (!address.isNullOrBlank() && !tracked.address.equals(address, ignoreCase = true))) {
            _connectionState.value = BtPrinterConnectionState.Disconnected(reason)
            _lastStatusMessage.value = reason
            return
        }
        if (manualDisconnectRequested || !autoReconnectEnabled || context == null) {
            _connectionState.value = BtPrinterConnectionState.Disconnected(reason)
            _lastStatusMessage.value = reason
            return
        }
        scheduleAutoReconnect(context, tracked, reason, lang)
    }

    private fun scheduleAutoReconnect(context: Context, device: PrinterDeviceInfo, reason: String?, lang: String) {
        reconnectJob?.cancel()
        val appCtx = context.applicationContext ?: context
        val delays = reconnectDelaysMs
        val totalAttempts = delays.size
        if (totalAttempts == 0) {
            val msg = Bilingual.tr(lang, "انقطع الاتصال بالطابعة (${device.name})", "Connexion interrompue (${device.name})", "Connection lost (${device.name})")
            _connectionState.value = BtPrinterConnectionState.Error(msg, device)
            _lastStatusMessage.value = msg
            return
        }

        _connectionState.value = BtPrinterConnectionState.Reconnecting(device, 1, totalAttempts, reason)
        _lastStatusMessage.value = Bilingual.tr(lang, "جاري إعادة الاتصال بالطابعة ${device.name} (1/$totalAttempts)...", "Reconnexion à ${device.name} (1/$totalAttempts)...", "Reconnecting to ${device.name} (1/$totalAttempts)...")

        reconnectJob = CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            for ((index, waitMs) in delays.withIndex()) {
                if (!isActive || manualDisconnectRequested) return@launch
                val attemptNum = index + 1
                _connectionState.value = BtPrinterConnectionState.Reconnecting(device, attemptNum, totalAttempts, reason)
                _lastStatusMessage.value = Bilingual.tr(lang, "جاري إعادة الاتصال بالطابعة ${device.name} ($attemptNum/$totalAttempts)...", "Reconnexion à ${device.name} ($attemptNum/$totalAttempts)...", "Reconnecting to ${device.name} ($attemptNum/$totalAttempts)...")
                delay(waitMs)
                if (!isActive || manualDisconnectRequested) return@launch
                val res = mutex.withLock {
                    connectInternal(appCtx, device.address.ifBlank { device.name }, lang, emitConnectingState = false)
                }
                if (res.isSuccess) return@launch
            }
            if (isActive && !manualDisconnectRequested) {
                val exhaustedMsg = Bilingual.tr(lang, "تعذر إعادة الاتصال بالطابعة (${device.name}). اضغط إعادة الاتصال للمحاولة يدوياً", "Impossible de reconnecter (${device.name}). Réessayez manuellement", "Could not reconnect to ${device.name}. Tap Reconnect to try again")
                _connectionState.value = BtPrinterConnectionState.Error(exhaustedMsg, device)
                _lastStatusMessage.value = exhaustedMsg
            }
        }
    }

    fun requiredConnectPermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
        BluetoothRfcommSocketHelper.requiredConnectPermissions(sdkInt)

    fun requiredDiscoveryPermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
        BluetoothRfcommSocketHelper.requiredDiscoveryPermissions(sdkInt)

    fun hasConnectPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        BluetoothRfcommSocketHelper.hasConnectPermission(context, sdkInt)

    fun hasDiscoveryPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        BluetoothRfcommSocketHelper.hasDiscoveryPermission(context, sdkInt)

    fun isSocketConnected(): Boolean = try {
        activeSocket?.isConnected == true && activeOutStream != null
    } catch (e: Exception) {
        Log.d(TAG, "Socket connectivity check failed", e)
        false
    }

    fun getPairedDevicesSafe(context: Context): List<PrinterDeviceInfo> =
        BluetoothRfcommSocketHelper.getPairedDevicesSafe(context)

    @SuppressLint("MissingPermission")
    suspend fun connect(context: Context, deviceAddressOrName: String, lang: String = "AR"): Result<PrinterDeviceInfo> = withContext(ioDispatcher) {
        lastLanguage = lang
        appContextRef = context.applicationContext ?: context
        manualDisconnectRequested = false
        reconnectJob?.cancel()
        reconnectJob = null
        mutex.withLock { connectInternal(context, deviceAddressOrName, lang, emitConnectingState = true) }
    }

    suspend fun reconnectLastPrinter(
        context: Context,
        fallbackDeviceAddressOrName: String = "",
        lang: String = lastLanguage
    ): Result<PrinterDeviceInfo> = withContext(ioDispatcher) {
        lastLanguage = lang
        appContextRef = context.applicationContext ?: context
        manualDisconnectRequested = false
        reconnectJob?.cancel()
        reconnectJob = null
        val target = lastConnectedDevice?.address?.ifBlank { lastConnectedDevice?.name.orEmpty() }?.takeIf { it.isNotBlank() }
            ?: _activeDevice.value?.address?.takeIf { it.isNotBlank() }
            ?: fallbackDeviceAddressOrName
        mutex.withLock { connectInternal(context, target, lang, emitConnectingState = true) }
    }

    @SuppressLint("MissingPermission")
    private fun connectInternal(
        context: Context,
        deviceAddressOrName: String,
        lang: String,
        emitConnectingState: Boolean = true
    ): Result<PrinterDeviceInfo> {
        connectOverrideForTest?.let { override ->
            val overrideResult = override.invoke(context, deviceAddressOrName, lang)
            val dev = overrideResult.getOrNull()
            if (overrideResult.isSuccess && dev != null) {
                lastConnectedDevice = dev
                _activeDevice.value = dev
                _connectionState.value = BtPrinterConnectionState.Connected(dev, System.currentTimeMillis())
                _lastStatusMessage.value = Bilingual.tr(lang, "تم الاتصال بالطابعة: ${dev.name}", "Connecté à l'imprimante : ${dev.name}", "Connected to printer: ${dev.name}")
            }
            return overrideResult
        }

        if (!hasConnectPermission(context)) {
            val msg = Bilingual.tr(lang, "يرجى منح إذن البلوتوث للاتصال بالطابعة", "Veuillez accorder l'autorisation Bluetooth", "Please grant Bluetooth permission")
            _connectionState.value = BtPrinterConnectionState.Error(msg, _activeDevice.value ?: lastConnectedDevice)
            _lastStatusMessage.value = msg
            return Result.failure(SecurityException(msg))
        }

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) {
            val msg = Bilingual.tr(lang, "هذا الجهاز لا يدعم البلوتوث", "Bluetooth indisponible sur cet appareil", "Bluetooth is not supported on this device")
            _connectionState.value = BtPrinterConnectionState.Error(msg, _activeDevice.value ?: lastConnectedDevice)
            _lastStatusMessage.value = msg
            return Result.failure(IllegalStateException(msg))
        }
        if (!adapter.isEnabled) {
            val msg = Bilingual.tr(lang, "البلوتوث غير مفعّل. يرجى تفعيله للاتصال بالطابعة", "Le Bluetooth est désactivé", "Bluetooth is turned off")
            _connectionState.value = BtPrinterConnectionState.Error(msg, _activeDevice.value ?: lastConnectedDevice)
            _lastStatusMessage.value = msg
            return Result.failure(IllegalStateException(msg))
        }

        val target = BluetoothRfcommSocketHelper.resolveBluetoothDevice(adapter, deviceAddressOrName)
        if (target == null) {
            val msg = if (deviceAddressOrName.isBlank()) {
                Bilingual.tr(lang, "لم يتم العثور على طابعة بلوتوث مقترنة. يرجى إقران الطابعة أولاً", "Aucune imprimante Bluetooth appairée trouvée", "No paired Bluetooth printer found")
            } else {
                Bilingual.tr(lang, "تعذر العثور على الطابعة المقترنة: $deviceAddressOrName", "Imprimante appairée introuvable : $deviceAddressOrName", "Paired printer not found: $deviceAddressOrName")
            }
            _connectionState.value = BtPrinterConnectionState.Error(msg, _activeDevice.value ?: lastConnectedDevice)
            _lastStatusMessage.value = msg
            return Result.failure(IllegalStateException(msg))
        }

        val resolvedInfo = PrinterDeviceInfo(
            name = target.name?.takeIf { it.isNotBlank() } ?: target.address,
            address = target.address,
            isBonded = target.bondState == BluetoothDevice.BOND_BONDED
        )
        lastConnectedDevice = resolvedInfo

        if (isSocketConnected() && activeSocket?.remoteDevice?.address.equals(target.address, ignoreCase = true)) {
            _activeDevice.value = resolvedInfo
            _connectionState.value = BtPrinterConnectionState.Connected(resolvedInfo, System.currentTimeMillis())
            return Result.success(resolvedInfo)
        }

        closeSocketInternal()
        if (emitConnectingState) {
            _connectionState.value = BtPrinterConnectionState.Connecting(resolvedInfo.name, resolvedInfo.address)
        }

        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
        } catch (e: Exception) {
            Log.d(TAG, "Unable to cancel Bluetooth discovery prior to connect", e)
        }

        return try {
            val socket = BluetoothRfcommSocketHelper.openSppSocketWithFallback(target)
            activeSocket = socket
            activeOutStream = socket.outputStream
            _activeDevice.value = resolvedInfo
            lastConnectedDevice = resolvedInfo
            _connectionState.value = BtPrinterConnectionState.Connected(resolvedInfo, System.currentTimeMillis())
            val okMsg = Bilingual.tr(lang, "تم الاتصال بالطابعة: ${resolvedInfo.name}", "Connecté à l'imprimante : ${resolvedInfo.name}", "Connected to printer: ${resolvedInfo.name}")
            _lastStatusMessage.value = okMsg
            Result.success(resolvedInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect Bluetooth printer ${resolvedInfo.address}", e)
            closeSocketInternal()
            val errDetail = e.localizedMessage ?: "SPP RFCOMM error"
            val msg = Bilingual.tr(lang, "فشل الاتصال بالطابعة (${resolvedInfo.name}): $errDetail", "Échec de connexion à (${resolvedInfo.name}) : $errDetail", "Failed to connect to (${resolvedInfo.name}): $errDetail")
            _connectionState.value = BtPrinterConnectionState.Error(msg, resolvedInfo)
            _lastStatusMessage.value = msg
            Result.failure(e)
        }
    }

    suspend fun disconnect(reason: String? = null) = withContext(ioDispatcher) {
        manualDisconnectRequested = true
        reconnectJob?.cancel()
        reconnectJob = null
        mutex.withLock {
            closeSocketInternal()
            _connectionState.value = BtPrinterConnectionState.Disconnected(reason)
            _lastStatusMessage.value = reason
        }
    }

    internal fun resetForTesting() {
        stopConnectionMonitoring()
        closeSocketInternal()
        manualDisconnectRequested = false
        autoReconnectEnabled = true
        lastLanguage = "AR"
        lastConnectedDevice = null
        connectOverrideForTest = null
        reconnectDelaysMs = longArrayOf(1500L, 3000L, 6000L)
        _activeDevice.value = null
        _connectionState.value = BtPrinterConnectionState.Disconnected()
        _lastStatusMessage.value = null
    }

    internal fun seedTrackedDeviceForTesting(device: PrinterDeviceInfo, connected: Boolean = true) {
        manualDisconnectRequested = false
        lastConnectedDevice = device
        _activeDevice.value = device
        _connectionState.value = if (connected) BtPrinterConnectionState.Connected(device, System.currentTimeMillis()) else BtPrinterConnectionState.Disconnected()
    }

    private fun closeSocketInternal() {
        try { activeOutStream?.close() } catch (e: IOException) { Log.d(TAG, "Closing BT output stream warning", e) }
        try { activeSocket?.close() } catch (e: IOException) { Log.d(TAG, "Closing BT socket warning", e) }
        activeOutStream = null
        activeSocket = null
    }

    suspend fun writeBytes(
        context: Context,
        deviceAddressOrName: String,
        data: ByteArray,
        jobDescription: String = "Receipt",
        lang: String = "AR"
    ): Pair<Boolean, String> = withContext(ioDispatcher) {
        lastLanguage = lang
        appContextRef = context.applicationContext ?: context
        manualDisconnectRequested = false
        reconnectJob?.cancel()
        reconnectJob = null

        mutex.withLock {
            val targetLookup = deviceAddressOrName.ifBlank { _activeDevice.value?.address ?: lastConnectedDevice?.address.orEmpty() }
            val needsConnect = !isSocketConnected() ||
                (targetLookup.isNotBlank() &&
                    !activeSocket?.remoteDevice?.address.equals(targetLookup, ignoreCase = true) &&
                    !_activeDevice.value?.name.equals(targetLookup, ignoreCase = true))

            if (needsConnect) {
                val connRes = connectInternal(context, targetLookup, lang)
                if (connRes.isFailure) {
                    val errMsg = (_connectionState.value as? BtPrinterConnectionState.Error)?.message
                        ?: connRes.exceptionOrNull()?.localizedMessage ?: "Printer connection error"
                    return@withLock false to errMsg
                }
            }

            val device = _activeDevice.value ?: lastConnectedDevice ?: PrinterDeviceInfo("Thermal Printer", targetLookup)
            _connectionState.value = BtPrinterConnectionState.Printing(device, jobDescription)

            return@withLock try {
                val stream = activeOutStream ?: throw IOException("Bluetooth output stream closed")
                stream.write(data)
                stream.flush()
                _connectionState.value = BtPrinterConnectionState.Connected(device, System.currentTimeMillis())
                val okMsg = Bilingual.tr(lang, "تمت الطباعة بنجاح على ${device.name}", "Impression réussie sur ${device.name}", "Printed successfully on ${device.name}")
                _lastStatusMessage.value = okMsg
                true to okMsg
            } catch (firstErr: Exception) {
                Log.w(TAG, "Write failed on active BT socket; attempting immediate reconnect", firstErr)
                closeSocketInternal()
                val retryConn = connectInternal(context, device.address.ifBlank { targetLookup }, lang)
                if (retryConn.isSuccess) {
                    try {
                        val stream = activeOutStream ?: throw IOException("Stream unavailable after reconnect")
                        stream.write(data)
                        stream.flush()
                        val reconnectedDev = retryConn.getOrNull() ?: device
                        _connectionState.value = BtPrinterConnectionState.Connected(reconnectedDev, System.currentTimeMillis())
                        val okMsg = Bilingual.tr(lang, "تمت الطباعة بنجاح على ${reconnectedDev.name}", "Impression réussie sur ${reconnectedDev.name}", "Printed successfully on ${reconnectedDev.name}")
                        _lastStatusMessage.value = okMsg
                        return@withLock true to okMsg
                    } catch (secondErr: Exception) {
                        Log.e(TAG, "Second BT write attempt failed after reconnect", secondErr)
                        closeSocketInternal()
                        val errMsg = Bilingual.tr(lang, "فشل إرسال البيانات للطابعة: ${secondErr.localizedMessage ?: "تأكد من تشغيل الطابعة"}", "Échec d'envoi à l'imprimante", "Failed to send print job")
                        _connectionState.value = BtPrinterConnectionState.Error(errMsg, device)
                        _lastStatusMessage.value = errMsg
                        return@withLock false to errMsg
                    }
                } else {
                    val errMsg = (_connectionState.value as? BtPrinterConnectionState.Error)?.message
                        ?: Bilingual.tr(lang, "فشل إعادة الاتصال بالطابعة: ${firstErr.localizedMessage ?: ""}", "Échec de reconnexion", "Reconnect failed")
                    _connectionState.value = BtPrinterConnectionState.Error(errMsg, device)
                    _lastStatusMessage.value = errMsg
                    false to errMsg
                }
            }
        }
    }

    fun buildTransactionSummaryReceipt(
        cartItems: List<CartItem>, subtotal: Double, discountAmount: Double, netTotal: Double,
        paymentMethod: String, receivedAmount: Double, changeDue: Double, cashierName: String,
        customerName: String?, settings: GlobalSettingsEntity, lang: String,
        invoiceNumberOverride: String? = null, isDraftSummary: Boolean = true, timestamp: Long = System.currentTimeMillis()
    ): ThermalReceiptData = ThermalReceiptSummaryBuilder.buildTransactionSummaryReceipt(
        cartItems, subtotal, discountAmount, netTotal, paymentMethod, receivedAmount, changeDue,
        cashierName, customerName, settings, lang, invoiceNumberOverride, isDraftSummary, timestamp
    )

    fun buildTestPrintReceipt(
        printerNameOrMac: String, paperWidthMm: Int, settings: GlobalSettingsEntity, lang: String
    ): ThermalReceiptData = ThermalReceiptSummaryBuilder.buildTestPrintReceipt(printerNameOrMac, paperWidthMm, settings, lang)
}
