package com.example.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Universal representation of a discovered printer device (Bluetooth or Wi-Fi Network).
 */
data class DiscoveredPrinter(
    val id: String, // MAC or IP:Port
    val name: String,
    val address: String, // MAC address or IP
    val port: Int = 9100,
    val connectionType: String, // "BLUETOOTH" or "WIFI"
    val isBonded: Boolean = false,
    val details: String = ""
)

/**
 * Handles Bluetooth discovery for searching nearby receipt and POS thermal printers.
 */
object BluetoothDiscoveryManager {
    private const val TAG = "BtDiscovery"
    private var discoveryReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    @SuppressLint("MissingPermission")
    fun startDiscovery(
        context: Context,
        onDeviceFound: (DiscoveredPrinter) -> Unit,
        onFinished: () -> Unit
    ) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: run {
            onFinished()
            return
        }

        if (!adapter.isEnabled) {
            onFinished()
            return
        }

        stopDiscovery(context)

        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let { dev ->
                            try {
                                val devName = dev.name ?: "طابعة بلوتوث (${dev.address.takeLast(5)})"
                                val isBonded = dev.bondState == BluetoothDevice.BOND_BONDED
                                val printer = DiscoveredPrinter(
                                    id = dev.address,
                                    name = devName,
                                    address = dev.address,
                                    connectionType = "BLUETOOTH",
                                    isBonded = isBonded,
                                    details = if (isBonded) "مقترن مسبقاً (Paired)" else "جهاز جديد متاح للربط"
                                )
                                onDeviceFound(printer)
                            } catch (e: SecurityException) {
                                Log.e(TAG, "Missing bluetooth scan permission", e)
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        stopDiscovery(context)
                        onFinished()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(discoveryReceiver, filter)
            }
            isReceiverRegistered = true

            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Bluetooth discovery", e)
            stopDiscovery(context)
            onFinished()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery(context: Context) {
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.let {
                if (it.isDiscovering) {
                    it.cancelDiscovery()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling Bluetooth discovery", e)
        }

        if (isReceiverRegistered && discoveryReceiver != null) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            discoveryReceiver = null
            isReceiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(deviceAddress: String, context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter ?: return false
            val device = adapter.getRemoteDevice(deviceAddress)
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                true
            } else {
                device.createBond()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pairing with device $deviceAddress", e)
            false
        }
    }
}

/**
 * Handles Wi-Fi Local Subnet and Network Service Discovery (mDNS)
 * for searching network thermal POS printers (ESC/POS Port 9100, 515, 631).
 */
object NetworkPrinterDiscoveryManager {
    private const val TAG = "NetPrinterDiscovery"
    private var scanJob: Job? = null
    private var nsdManager: NsdManager? = null
    private val nsdDiscoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

    /**
     * Get the device's current Wi-Fi IP address (e.g. "192.168.1.45")
     */
    fun getLocalWifiIpAddress(context: Context): String? {
        return WifiNetworkSocketHelper.getLocalWifiIpv4(context)
    }

    /**
     * Start discovering Wi-Fi network printers:
     * 1) Fast parallel local subnet scanner probing standard ESC/POS Port 9100
     * 2) mDNS / NSD network service discovery
     */
    fun startScan(
        context: Context,
        scope: CoroutineScope,
        onPrinterFound: (DiscoveredPrinter) -> Unit,
        onProgress: (Int, Int) -> Unit, // current, total
        onFinished: () -> Unit
    ) {
        stopScan(context)

        val localIp = getLocalWifiIpAddress(context) ?: "192.168.1.100"
        val parts = localIp.split(".")
        val subnetPrefix = if (parts.size >= 4) {
            "${parts[0]}.${parts[1]}.${parts[2]}."
        } else {
            "192.168.1."
        }

        val discoveredIps = mutableSetOf<String>()

        // 1. Try NSD (Network Service Discovery) across RAW PDL (9100), LPD (515), and IPP (631)
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            val serviceTypes = listOf("_pdl-datastream._tcp", "_printer._tcp", "_ipp._tcp")
            for (serviceType in serviceTypes) {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                        Log.w(TAG, "NSD start failed for $serviceType (code=$errorCode)")
                    }
                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                    override fun onDiscoveryStarted(serviceType: String?) {}
                    override fun onDiscoveryStopped(serviceType: String?) {}
                    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                        serviceInfo?.let { info ->
                            runCatching {
                                @Suppress("DEPRECATION")
                                nsdManager?.resolveService(info, object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                        @Suppress("DEPRECATION")
                                        resolvedInfo?.host?.hostAddress?.let { ip ->
                                            val resolvedPort = if (resolvedInfo.port > 0) resolvedInfo.port else 9100
                                            if (discoveredIps.add("$ip:$resolvedPort")) {
                                                val p = DiscoveredPrinter(
                                                    id = "$ip:$resolvedPort",
                                                    name = resolvedInfo.serviceName ?: "طابعة شبكة (NSD)",
                                                    address = ip,
                                                    port = resolvedPort,
                                                    connectionType = "WIFI",
                                                    details = if (resolvedPort == 631) "IPP/Mopria (Android Print)" else "طابعة معرّفة عبر mDNS / Bonjour"
                                                )
                                                scope.launch(Dispatchers.Main) {
                                                    onPrinterFound(p)
                                                }
                                            }
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
                nsdDiscoveryListeners.add(listener)
                runCatching {
                    nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NSD setup skipped or unsupported: ${e.message}")
        }

        // 2. Multi-threaded Subnet Scanner on standard POS Port 9100 & 515
        scanJob = scope.launch(Dispatchers.IO) {
            val totalHosts = 254
            var completedCount = 0

            // Common POS printer ports to probe: 9100 is ESC/POS Raw standard
            val targetPorts = listOf(9100, 515, 631)

            // Scan in batches of 32 concurrent requests for speed and responsiveness
            val ipRange = (1..254).toList()
            val chunkSize = 32

            for (chunk in ipRange.chunked(chunkSize)) {
                if (!isActive) break

                val deferreds = chunk.map { hostNum ->
                    async {
                        val targetIp = "$subnetPrefix$hostNum"
                        var foundPort: Int? = null

                        for (port in targetPorts) {
                            if (!isActive) break
                            try {
                                WifiNetworkSocketHelper.openSocket(
                                    context = context,
                                    ip = targetIp,
                                    port = port,
                                    connectTimeoutMs = 240,
                                    readTimeoutMs = 300
                                ).use {
                                    foundPort = port
                                }
                                break // Found working port!
                            } catch (e: Exception) {
                                if (com.example.BuildConfig.DEBUG) {
                                    Log.d(TAG, "Port $port not open on $targetIp: ${e.message}")
                                }
                            }
                        }

                        if (foundPort != null && discoveredIps.add("$targetIp:$foundPort")) {
                            // Try resolving host name
                            var hostName = "طابعة POS ($targetIp)"
                            try {
                                val inet = InetAddress.getByName(targetIp)
                                val canonical = inet.canonicalHostName
                                if (canonical != targetIp && canonical.isNotBlank()) {
                                    hostName = canonical
                                }
                            } catch (e: Exception) {
                                if (com.example.BuildConfig.DEBUG) {
                                    Log.w("PrinterDiscovery", "Failed to resolve host name for $targetIp", e)
                                }
                            }

                            val printer = DiscoveredPrinter(
                                id = "$targetIp:$foundPort",
                                name = hostName,
                                address = targetIp,
                                port = foundPort,
                                connectionType = "WIFI",
                                details = "منفذ الطباعة الحرارية المفتوح: $foundPort (ESC/POS)"
                            )
                            withContext(Dispatchers.Main) {
                                onPrinterFound(printer)
                            }
                        }

                        synchronized(this@NetworkPrinterDiscoveryManager) {
                            completedCount++
                        }
                    }
                }

                deferreds.awaitAll()
                withContext(Dispatchers.Main) {
                    onProgress(completedCount, totalHosts)
                }
            }

            withContext(Dispatchers.Main) {
                onFinished()
            }
        }
    }

    fun stopScan(context: Context) {
        scanJob?.cancel()
        scanJob = null

        val manager = nsdManager
        if (manager != null) {
            nsdDiscoveryListeners.forEach { listener ->
                try {
                    manager.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    if (com.example.BuildConfig.DEBUG) {
                        Log.w("PrinterDiscovery", "Failed to stop NSD service discovery", e)
                    }
                }
            }
        }
        nsdDiscoveryListeners.clear()
        nsdManager = null
    }
}
