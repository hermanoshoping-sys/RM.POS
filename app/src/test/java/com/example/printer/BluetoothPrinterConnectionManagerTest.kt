package com.example.printer

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.GlobalSettingsEntity
import com.example.data.ProductEntity
import com.example.ui.viewmodels.CartItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import kotlinx.coroutines.delay

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BluetoothPrinterConnectionManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BluetoothPrinterConnectionManager.resetForTesting()
    }

    @After
    fun tearDown() {
        BluetoothPrinterConnectionManager.resetForTesting()
    }

    @Test
    fun `required permissions match Android SDK version`() {
        val android12PlusConnect = BluetoothPrinterConnectionManager.requiredConnectPermissions(Build.VERSION_CODES.S)
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), android12PlusConnect)

        val legacyConnect = BluetoothPrinterConnectionManager.requiredConnectPermissions(Build.VERSION_CODES.R)
        assertTrue(legacyConnect.isEmpty())

        val android12PlusScan = BluetoothPrinterConnectionManager.requiredDiscoveryPermissions(Build.VERSION_CODES.TIRAMISU)
        assertTrue(android12PlusScan.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(android12PlusScan.contains(Manifest.permission.BLUETOOTH_CONNECT))

        val legacyScan = BluetoothPrinterConnectionManager.requiredDiscoveryPermissions(Build.VERSION_CODES.Q)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), legacyScan)
    }

    @Test
    fun `connect without Bluetooth permission transitions cleanly to Error and disconnect resets state`() = runBlocking {
        assertTrue(BluetoothPrinterConnectionManager.connectionState.value is BtPrinterConnectionState.Disconnected)
        assertFalse(BluetoothPrinterConnectionManager.isSocketConnected())

        val result = BluetoothPrinterConnectionManager.connect(
            context = context,
            deviceAddressOrName = "00:11:22:33:44:55",
            lang = "EN"
        )
        assertTrue(result.isFailure)
        val errState = BluetoothPrinterConnectionManager.connectionState.value
        assertTrue(errState is BtPrinterConnectionState.Error)
        assertFalse((errState as BtPrinterConnectionState.Error).message.isBlank())

        BluetoothPrinterConnectionManager.disconnect("Closed by cashier")
        val afterDisconnect = BluetoothPrinterConnectionManager.connectionState.value
        assertTrue(afterDisconnect is BtPrinterConnectionState.Disconnected)
        assertEquals("Closed by cashier", (afterDisconnect as BtPrinterConnectionState.Disconnected).reason)
    }

    @Test
    fun `unexpected disconnect triggers exponential auto-reconnect and recovers to Connected`() = runBlocking {
        val testPrinter = PrinterDeviceInfo(name = "RPP-300", address = "00:11:22:33:44:55", isBonded = true)
        val attempts = java.util.concurrent.atomic.AtomicInteger(0)
        BluetoothPrinterConnectionManager.reconnectDelaysMs = longArrayOf(20L, 40L, 60L)
        BluetoothPrinterConnectionManager.connectOverrideForTest = { _, _, _ ->
            val current = attempts.incrementAndGet()
            if (current >= 2) {
                Result.success(testPrinter)
            } else {
                Result.failure(RuntimeException("Transient RFCOMM drop"))
            }
        }
        BluetoothPrinterConnectionManager.seedTrackedDeviceForTesting(testPrinter, connected = true)

        BluetoothPrinterConnectionManager.handleUnexpectedDisconnect(
            address = testPrinter.address,
            reason = "ACL link dropped",
            context = context,
            lang = "EN"
        )
        assertTrue(BluetoothPrinterConnectionManager.connectionState.value is BtPrinterConnectionState.Reconnecting)

        // Wait for async retries to succeed on attempt 2
        val deadline = System.currentTimeMillis() + 3000L
        while (System.currentTimeMillis() < deadline &&
            (attempts.get() < 2 || BluetoothPrinterConnectionManager.connectionState.value !is BtPrinterConnectionState.Connected)
        ) {
            delay(20L)
        }

        assertTrue(
            "Expected Connected after auto-reconnect, got ${BluetoothPrinterConnectionManager.connectionState.value}",
            BluetoothPrinterConnectionManager.connectionState.value is BtPrinterConnectionState.Connected
        )
        assertEquals(2, attempts.get())
    }

    @Test
    fun `auto-reconnect transitions to Error after max retry attempts fail`() = runBlocking {
        val testPrinter = PrinterDeviceInfo(name = "POS-80", address = "AA:BB:CC:DD:EE:FF", isBonded = true)
        var attempts = 0
        BluetoothPrinterConnectionManager.reconnectDelaysMs = longArrayOf(15L, 15L, 15L)
        BluetoothPrinterConnectionManager.connectOverrideForTest = { _, _, _ ->
            attempts++
            Result.failure(RuntimeException("Printer powered off"))
        }
        BluetoothPrinterConnectionManager.seedTrackedDeviceForTesting(testPrinter, connected = true)

        BluetoothPrinterConnectionManager.handleUnexpectedDisconnect(
            address = testPrinter.address,
            reason = "Link lost",
            context = context,
            lang = "AR"
        )

        val deadline = System.currentTimeMillis() + 3000L
        while (System.currentTimeMillis() < deadline &&
            BluetoothPrinterConnectionManager.connectionState.value !is BtPrinterConnectionState.Error
        ) {
            delay(15L)
        }

        val state = BluetoothPrinterConnectionManager.connectionState.value
        assertTrue("Expected Error state after max retries, got $state", state is BtPrinterConnectionState.Error)
        assertEquals(3, attempts)
        assertEquals(testPrinter, (state as BtPrinterConnectionState.Error).lastDevice)
    }

    @Test
    fun `manual disconnect prevents unexpected disconnect auto-reconnect until explicit reconnect`() = runBlocking {
        val testPrinter = PrinterDeviceInfo(name = "MTP-II", address = "11:22:33:44:55:66", isBonded = true)
        var connectCalls = 0
        BluetoothPrinterConnectionManager.reconnectDelaysMs = longArrayOf(15L, 15L)
        BluetoothPrinterConnectionManager.connectOverrideForTest = { _, _, _ ->
            connectCalls++
            Result.success(testPrinter)
        }
        BluetoothPrinterConnectionManager.seedTrackedDeviceForTesting(testPrinter, connected = true)

        BluetoothPrinterConnectionManager.disconnect("Cashier disconnected")
        BluetoothPrinterConnectionManager.handleUnexpectedDisconnect(
            address = testPrinter.address,
            reason = "ACL disconnect broadcast",
            context = context,
            lang = "EN"
        )
        delay(60L)
        assertEquals(0, connectCalls)
        assertTrue(BluetoothPrinterConnectionManager.connectionState.value is BtPrinterConnectionState.Disconnected)

        // Manual reconnect resumes connection cleanly
        val reconnectRes = BluetoothPrinterConnectionManager.reconnectLastPrinter(context, lang = "EN")
        assertTrue(reconnectRes.isSuccess)
        assertEquals(1, connectCalls)
        assertTrue(BluetoothPrinterConnectionManager.connectionState.value is BtPrinterConnectionState.Connected)
    }

    @Test
    fun `adapter state OFF pauses and STATE_ON automatically triggers reconnect`() = runBlocking {
        val testPrinter = PrinterDeviceInfo(name = "XPrinter-58", address = "22:33:44:55:66:77", isBonded = true)
        var reconnectCalls = 0
        BluetoothPrinterConnectionManager.reconnectDelaysMs = longArrayOf(15L)
        BluetoothPrinterConnectionManager.connectOverrideForTest = { _, _, _ ->
            reconnectCalls++
            Result.success(testPrinter)
        }
        BluetoothPrinterConnectionManager.seedTrackedDeviceForTesting(testPrinter, connected = true)

        BluetoothPrinterConnectionManager.onBluetoothBroadcast(
            context,
            Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.STATE_OFF
            )
        )
        assertTrue(BluetoothPrinterConnectionManager.connectionState.value is BtPrinterConnectionState.Error)

        BluetoothPrinterConnectionManager.onBluetoothBroadcast(
            context,
            Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.STATE_ON
            )
        )

        val deadline = System.currentTimeMillis() + 2000L
        while (System.currentTimeMillis() < deadline &&
            BluetoothPrinterConnectionManager.connectionState.value !is BtPrinterConnectionState.Connected
        ) {
            delay(15L)
        }

        assertTrue(BluetoothPrinterConnectionManager.connectionState.value is BtPrinterConnectionState.Connected)
        assertEquals(1, reconnectCalls)
    }

    @Test
    fun `writeBytes fails gracefully when printer cannot connect`() = runBlocking {
        val sampleData = PosPrintServiceImpl.generateThermalReceiptBytes(
            receipt = ThermalReceiptData(
                invoiceNumber = "SUM-101010",
                items = listOf(ThermalReceiptItem("Milk 1L", 2.0, 10.50, 21.00)),
                subtotal = 21.00,
                grandTotal = 21.00,
                paidAmount = 50.00,
                changeAmount = 29.00,
                documentTitle = "TRANSACTION SUMMARY"
            )
        )
        val (ok, message) = BluetoothPrinterConnectionManager.writeBytes(
            context = context,
            deviceAddressOrName = "00:11:22:33:44:55",
            data = sampleData,
            jobDescription = "Transaction Summary",
            lang = "FR"
        )
        assertFalse(ok)
        assertTrue(message.isNotBlank())
        assertTrue(BluetoothPrinterConnectionManager.connectionState.value is BtPrinterConnectionState.Error)
    }

    @Test
    fun `buildTransactionSummaryReceipt generates complete checkout summary with Western digits`() {
        val p1 = ProductEntity(
            id = 1L,
            barcode = "61110001",
            name = "Coffee Beans",
            categoryId = 1L,
            categoryName = "Beverages",
            unit = "PCS",
            purchasePrice = 3000L,
            sellPrice = 4500L, // 45.00
            currentStock = 20.0,
            minStock = 2.0
        )
        val p2 = ProductEntity(
            id = 2L,
            barcode = "61110002",
            name = "Mineral Water",
            categoryId = 1L,
            categoryName = "Beverages",
            unit = "PCS",
            purchasePrice = 200L,
            sellPrice = 500L, // 5.00
            currentStock = 50.0,
            minStock = 5.0
        )
        val cartItems = listOf(
            CartItem(product = p1, quantity = 2.0),
            CartItem(product = p2, quantity = 3.0)
        )
        val settings = GlobalSettingsEntity(
            shopName = "Atlas Market",
            shopAddress = "Casablanca",
            shopPhone = "0600112233",
            receiptFooter = "Merci"
        )

        val summary = BluetoothPrinterConnectionManager.buildTransactionSummaryReceipt(
            cartItems = cartItems,
            subtotal = 105.00,
            discountAmount = 5.00,
            netTotal = 100.00,
            paymentMethod = "CASH",
            receivedAmount = 120.00,
            changeDue = 20.00,
            cashierName = "Karim",
            customerName = "Ahmed",
            settings = settings,
            lang = "EN",
            invoiceNumberOverride = "SUM-120000",
            isDraftSummary = true
        )

        assertEquals("TRANSACTION SUMMARY", summary.documentTitle)
        assertEquals("Atlas Market", summary.storeName)
        assertEquals("SUM-120000", summary.invoiceNumber)
        assertEquals(2, summary.items.size)
        assertEquals(105.00, summary.subtotal, 0.001)
        assertEquals(5.00, summary.discountAmount, 0.001)
        assertEquals(100.00, summary.grandTotal, 0.001)
        assertEquals(120.00, summary.paidAmount, 0.001)
        assertEquals(20.00, summary.changeAmount, 0.001)

        val plainText = PosPrintServiceImpl.generatePlainTextReceipt(
            summary,
            ThermalPrinterConfig(paperWidthMm = 58)
        )
        assertTrue(plainText.contains("=== TRANSACTION SUMMARY ==="))
        assertTrue(plainText.contains("Atlas Market"))
        assertTrue(plainText.contains("105.00"))
        assertTrue(plainText.contains("-5.00"))
        assertTrue(plainText.contains("100.00"))
        assertTrue(plainText.contains("120.00"))
        assertTrue(plainText.contains("20.00"))

        val escPosBytes = PosPrintServiceImpl.generateThermalReceiptBytes(
            summary,
            ThermalPrinterConfig(paperWidthMm = 58, cutPaper = true, printQrCode = false)
        )
        // Starts with ESC @ (0x1B, 0x40)
        assertArrayEquals(byteArrayOf(0x1B, 0x40), escPosBytes.copyOfRange(0, 2))
        // Ends with GS V B 16 (0x1D, 0x56, 0x42, 0x10)
        assertArrayEquals(
            byteArrayOf(0x1D, 0x56, 0x42, 0x10),
            escPosBytes.copyOfRange(escPosBytes.size - 4, escPosBytes.size)
        )
    }
}
