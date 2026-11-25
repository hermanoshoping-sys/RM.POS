package com.example.printer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.ui.theme.Bilingual
import java.io.IOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

data class WifiDiagnosticResult(
    val message: String,
    val isPermanentFailure: Boolean = false,
    val suggestAndroidPrintFallback: Boolean = false
)

/**
 * Reliable Wi-Fi LAN TCP socket helper for thermal POS printers.
 *
 * On Android 10+, when mobile data (4G/5G) is enabled alongside a local Wi-Fi router or a
 * printer's Wi-Fi Direct/AP hotspot (which has no internet), plain `Socket()` connections are
 * frequently routed over cellular data, causing immediate `ENETUNREACH` / timeout errors.
 * Binding sockets explicitly to the active `TRANSPORT_WIFI` [Network] fixes this routing issue.
 */
object WifiNetworkSocketHelper {
    private const val TAG = "WifiSocketHelper"
    const val DEFAULT_CONNECT_TIMEOUT_MS = 2800
    const val DEFAULT_READ_TIMEOUT_MS = 4000
    const val DEFAULT_CHUNK_SIZE = 4096
    const val DEFAULT_DRAIN_DELAY_MS = 160L

    /**
     * Finds the active Android [Network] using [NetworkCapabilities.TRANSPORT_WIFI], if any.
     */
    fun findWifiNetwork(context: Context?): Network? {
        if (context == null) return null
        return try {
            val cm = context.applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val activeNet = cm.activeNetwork
            if (activeNet != null && hasWifiTransport(cm, activeNet)) {
                return activeNet
            }
            @Suppress("DEPRECATION")
            val allNetworks = cm.allNetworks ?: return null
            allNetworks.firstOrNull { net -> hasWifiTransport(cm, net) }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to query Wi-Fi network from ConnectivityManager: ${t.message}")
            null
        }
    }

    private fun hasWifiTransport(cm: ConnectivityManager, network: Network): Boolean {
        return try {
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns true when the device currently has an active Wi-Fi network interface.
     */
    fun isWifiConnected(context: Context?): Boolean {
        if (findWifiNetwork(context) != null) return true
        return getLocalWifiIpv4(context) != null
    }

    /**
     * Resolves the device's local IPv4 address on Wi-Fi using modern [ConnectivityManager.getLinkProperties]
     * first, then falling back to [WifiManager] and [NetworkInterface] inspection.
     */
    fun getLocalWifiIpv4(context: Context?): String? {
        if (context == null) return null
        try {
            val cm = context.applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifiNet = findWifiNetwork(context)
            if (cm != null && wifiNet != null) {
                val linkProps = cm.getLinkProperties(wifiNet)
                val v4 = linkProps?.linkAddresses
                    ?.mapNotNull { it.address }
                    ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
                if (!v4.isNullOrBlank() && v4 != "0.0.0.0") {
                    return v4
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "LinkProperties Wi-Fi IP lookup failed: ${t.message}")
        }

        try {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (t: Throwable) {
            Log.d(TAG, "WifiManager IP lookup failed: ${t.message}")
        }

        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var fallbackPrivateIp: String? = null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val ifaceName = iface.name?.lowercase(Locale.ROOT).orEmpty()
                val isLikelyWifi = ifaceName.startsWith("wlan") || ifaceName.startsWith("wifi") || ifaceName.startsWith("ap")
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (isLikelyWifi) return ip
                        if (fallbackPrivateIp == null && (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))) {
                            fallbackPrivateIp = ip
                        }
                    }
                }
            }
            fallbackPrivateIp
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Opens a TCP socket bound to the active Wi-Fi [Network] when available so local LAN/AP
     * thermal printers remain reachable even when cellular data is enabled.
     */
    @Throws(IOException::class)
    fun openSocket(
        context: Context?,
        ip: String,
        port: Int,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS
    ): Socket {
        val cleanIp = ip.trim()
        val wifiNetwork = findWifiNetwork(context)
        val address = InetSocketAddress(cleanIp, port)

        if (wifiNetwork != null) {
            val wifiSocket = try {
                wifiNetwork.socketFactory.createSocket()
            } catch (t: Throwable) {
                Log.w(TAG, "Wi-Fi socketFactory.createSocket() fallback: ${t.message}")
                null
            }
            if (wifiSocket != null) {
                try {
                    wifiSocket.tcpNoDelay = true
                    wifiSocket.soTimeout = readTimeoutMs
                    wifiSocket.connect(address, connectTimeoutMs)
                    return wifiSocket
                } catch (wifiErr: IOException) {
                    runCatching { wifiSocket.close() }
                    // Retry once on default route in case the printer is reachable over Ethernet/VPN/emulator bridge
                    try {
                        val fallback = Socket()
                        fallback.tcpNoDelay = true
                        fallback.soTimeout = readTimeoutMs
                        fallback.connect(address, (connectTimeoutMs / 2).coerceAtLeast(1000))
                        return fallback
                    } catch (_: IOException) {
                        throw wifiErr
                    }
                }
            }
        }

        val socket = Socket()
        socket.tcpNoDelay = true
        socket.soTimeout = readTimeoutMs
        socket.connect(address, connectTimeoutMs)
        return socket
    }

    /**
     * Writes ESC/POS payload bytes in small chunks with explicit flushing and a brief drain wait
     * so Wi-Fi thermal printer receive buffers don't overflow or truncate raster receipts.
     */
    @Throws(IOException::class)
    fun writeChunked(
        socket: Socket,
        data: ByteArray,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        drainDelayMs: Long = DEFAULT_DRAIN_DELAY_MS
    ) {
        val out: OutputStream = socket.getOutputStream()
        val effectiveChunk = chunkSize.coerceAtLeast(512)
        var offset = 0
        while (offset < data.size) {
            val len = minOf(effectiveChunk, data.size - offset)
            out.write(data, offset, len)
            out.flush()
            offset += len
            if (offset < data.size) {
                try {
                    Thread.sleep(12L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        out.flush()
        if (drainDelayMs > 0L) {
            try {
                Thread.sleep(drainDelayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Checks whether two IPv4 strings share the same /24 subnet (first 3 octets).
     */
    fun isSameSubnet24(ipA: String?, ipB: String?): Boolean {
        if (ipA.isNullOrBlank() || ipB.isNullOrBlank()) return true
        val partsA = ipA.trim().split(".")
        val partsB = ipB.trim().split(".")
        if (partsA.size != 4 || partsB.size != 4) return true
        return partsA[0] == partsB[0] && partsA[1] == partsB[1] && partsA[2] == partsB[2]
    }

    /**
     * Produces a clear, localized diagnostic message explaining why a Wi-Fi print attempt failed
     * and whether Android Print (Mopria/Wi-Fi) fallback should be offered.
     */
    fun diagnoseWifiFailure(
        context: Context?,
        ip: String,
        port: Int,
        throwable: Throwable?,
        lang: String = "AR",
        wifiConnectedOverride: Boolean? = null,
        localIpOverride: String? = null
    ): WifiDiagnosticResult {
        val cleanIp = ip.trim()
        val effectiveLang = lang.ifBlank { "AR" }.uppercase(Locale.ROOT)
        val wifiConnected = wifiConnectedOverride ?: isWifiConnected(context)
        val localIp = localIpOverride ?: getLocalWifiIpv4(context)
        val cause = throwable?.cause ?: throwable
        val rawMsg = (throwable?.message ?: cause?.message ?: "").lowercase(Locale.ROOT)

        if (cleanIp.isBlank() || port !in 1..65535) {
            return WifiDiagnosticResult(
                message = Bilingual.tr(
                    effectiveLang,
                    "عنوان IP أو المنفذ غير صالح. يرجى إدخال عنوان الطابعة (مثلاً 192.168.1.100) والمنفذ 9100.",
                    "Adresse IP ou port invalide. Saisissez l'IP imprimante (ex: 192.168.1.100) et le port 9100.",
                    "Invalid IP or port. Enter the printer IP (e.g. 192.168.1.100) and port 9100."
                ),
                isPermanentFailure = true,
                suggestAndroidPrintFallback = false
            )
        }

        if (!wifiConnected) {
            return WifiDiagnosticResult(
                message = Bilingual.tr(
                    effectiveLang,
                    "فشل الطباعة عبر الشبكة: الهاتف غير متصل بشبكة Wi-Fi. يرجى تفعيل الواي فاي والاتصال بنفس شبكة الطابعة.",
                    "Échec réseau : le téléphone n'est pas connecté au Wi-Fi. Connectez-vous au réseau de l'imprimante.",
                    "Network print failed: device is not connected to Wi-Fi. Connect to the printer's Wi-Fi network."
                ),
                isPermanentFailure = true,
                suggestAndroidPrintFallback = true
            )
        }

        if (port == 631) {
            return WifiDiagnosticResult(
                message = Bilingual.tr(
                    effectiveLang,
                    "المنفذ 631 خاص بطابعات IPP/Mopria المكتبية ولا يدعم أوامر ESC/POS الخام. استخدم «طباعة أندرويد (Wi-Fi)» أو المنفذ 9100 للطابعات الحرارية.",
                    "Le port 631 est réservé à IPP/Mopria (non ESC/POS brut). Utilisez l'impression Android (Wi-Fi) ou le port 9100.",
                    "Port 631 is for IPP/Mopria printers, not raw ESC/POS. Use Android Print (Wi-Fi) or port 9100."
                ),
                isPermanentFailure = true,
                suggestAndroidPrintFallback = true
            )
        }

        if (!localIp.isNullOrBlank() && !isSameSubnet24(localIp, cleanIp)) {
            return WifiDiagnosticResult(
                message = Bilingual.tr(
                    effectiveLang,
                    "فشل الاتصال ($cleanIp:$port): الهاتف على الشبكة ($localIp) والطابعة على نطاق مختلف. اضغط «بحث في الشبكة» أو أدخل IP الصحيح، أو اطبع عبر نظام أندرويد.",
                    "Échec ($cleanIp:$port) : téléphone sur ($localIp), sous-réseau différent. Lancez la recherche réseau ou utilisez Android Print.",
                    "Failed ($cleanIp:$port): device IP ($localIp) is on a different subnet. Run Network Search or use Android Print."
                ),
                isPermanentFailure = true,
                suggestAndroidPrintFallback = true
            )
        }

        if (throwable is ConnectException || cause is ConnectException || rawMsg.contains("econnrefused") || rawMsg.contains("connection refused")) {
            return WifiDiagnosticResult(
                message = Bilingual.tr(
                    effectiveLang,
                    "رفضت الطابعة ($cleanIp) الاتصال على المنفذ $port. تأكد أن الطابعة تدعم منفذ 9100 أو استخدم زر «طباعة عبر أندرويد (Wi-Fi/Mopria)».",
                    "L'imprimante ($cleanIp) a refusé le port $port. Vérifiez le port 9100 ou utilisez l'impression Android (Wi-Fi/Mopria).",
                    "Printer ($cleanIp) refused port $port. Verify RAW port 9100 or use Android Print (Wi-Fi/Mopria)."
                ),
                isPermanentFailure = true,
                suggestAndroidPrintFallback = true
            )
        }

        if (throwable is UnknownHostException || cause is UnknownHostException) {
            return WifiDiagnosticResult(
                message = Bilingual.tr(
                    effectiveLang,
                    "عنوان IP غير معروف ($cleanIp). تحقق من كتابة العنوان بشكل صحيح.",
                    "Adresse IP introuvable ($cleanIp). Vérifiez l'adresse saisie.",
                    "Unknown printer host ($cleanIp). Please verify the IP address."
                ),
                isPermanentFailure = true,
                suggestAndroidPrintFallback = false
            )
        }

        if (throwable is SocketTimeoutException || cause is SocketTimeoutException ||
            throwable is NoRouteToHostException || cause is NoRouteToHostException ||
            rawMsg.contains("timed out") || rawMsg.contains("enetunreach") || rawMsg.contains("ehostunreach")
        ) {
            val subnetInfo = if (!localIp.isNullOrBlank()) " (IP: $localIp)" else ""
            return WifiDiagnosticResult(
                message = Bilingual.tr(
                    effectiveLang,
                    "تعذر الوصول إلى الطابعة ($cleanIp:$port)$subnetInfo. تأكد أن الطابعة مشغّلة ومتصلة بنفس الواي فاي، أو جرّب «بحث في الشبكة» أو الطباعة عبر أندرويد.",
                    "Imprimante ($cleanIp:$port) injoignable$subnetInfo. Vérifiez qu'elle est allumée sur le même Wi-Fi ou essayez Android Print.",
                    "Printer ($cleanIp:$port) unreachable$subnetInfo. Ensure it is powered on the same Wi-Fi, or try Android Print."
                ),
                isPermanentFailure = false,
                suggestAndroidPrintFallback = true
            )
        }

        val detail = throwable?.localizedMessage?.takeIf { it.isNotBlank() } ?: "تعذر الاتصال"
        return WifiDiagnosticResult(
            message = Bilingual.tr(
                effectiveLang,
                "فشل الطباعة عبر الشبكة ($cleanIp:$port): $detail",
                "Échec réseau ($cleanIp:$port) : $detail",
                "Network print failed ($cleanIp:$port): $detail"
            ),
            isPermanentFailure = false,
            suggestAndroidPrintFallback = true
        )
    }

    /**
     * Convenience wrapper to avoid leaking Android version checks at call sites.
     */
    fun apiLevel(): Int = Build.VERSION.SDK_INT
}
