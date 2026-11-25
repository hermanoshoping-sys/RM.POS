package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.UUID

/**
 * Low-level Bluetooth permission, bonded device resolution, and RFCOMM socket helper
 * extracted to keep BluetoothPrinterConnectionManager focused and under 500 lines.
 */
internal object BluetoothRfcommSocketHelper {
    private const val TAG = "BtRfcommHelper"
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun requiredConnectPermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
        if (sdkInt >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()

    fun requiredDiscoveryPermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
        if (sdkInt >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasConnectPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        requiredConnectPermissions(sdkInt).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun hasDiscoveryPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        requiredDiscoveryPermissions(sdkInt).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    fun getPairedDevicesSafe(context: Context): List<PrinterDeviceInfo> {
        if (!hasConnectPermission(context)) return emptyList()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return emptyList()
        return try {
            if (!adapter.isEnabled) return emptyList()
            adapter.bondedDevices.map { dev ->
                PrinterDeviceInfo(
                    name = dev.name?.takeIf { it.isNotBlank() } ?: "BT Printer (${dev.address.takeLast(5)})",
                    address = dev.address,
                    isBonded = dev.bondState == BluetoothDevice.BOND_BONDED
                )
            }.sortedBy { it.name.lowercase(Locale.US) }
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing Bluetooth connect permission", se)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error querying bonded devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun resolveBluetoothDevice(adapter: BluetoothAdapter, deviceAddressOrName: String): BluetoothDevice? {
        val bondedList = try {
            adapter.bondedDevices.toList()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to list bonded devices", e)
            emptyList()
        }
        val trimmed = deviceAddressOrName.trim()
        if (trimmed.isNotBlank()) {
            bondedList.find {
                it.address.equals(trimmed, ignoreCase = true) || (it.name != null && it.name.equals(trimmed, ignoreCase = true))
            }?.let { return it }

            if (BluetoothAdapter.checkBluetoothAddress(trimmed.uppercase(Locale.US))) {
                return try {
                    adapter.getRemoteDevice(trimmed.uppercase(Locale.US))
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid remote Bluetooth device address: $trimmed", e)
                    null
                }
            }
        }
        return bondedList.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    fun openSppSocketWithFallback(device: BluetoothDevice): BluetoothSocket {
        var primaryError: Exception? = null
        try {
            val secureSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            secureSocket.connect()
            return secureSocket
        } catch (e: Exception) {
            primaryError = e
            Log.w(TAG, "Secure RFCOMM failed for ${device.address}, trying insecure fallback", e)
        }
        return try {
            val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            insecureSocket.connect()
            insecureSocket
        } catch (fallbackEx: Exception) {
            throw fallbackEx.initCause(primaryError) as? Exception ?: fallbackEx
        }
    }
}
