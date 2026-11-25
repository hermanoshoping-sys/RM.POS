package com.example.printer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatchPrintQueueAndWifiTest {

    private lateinit var context: Context
    private lateinit var testScope: CoroutineScope

    private val testSettings = GlobalSettingsEntity(
        shopName = "RM.POS Test",
        language = "AR",
        printerPaperWidth = 58
    )

    private val testProfile = ThermalPrintProfile(
        paperWidth = ThermalPaperWidth.MM_58,
        language = "AR",
        useRasterRendering = false,
        maxRetryAttempts = 0,
        retryDelayMs = 0L
    )

    private val testBtPrinter = ThermalPrinterDevice(
        id = "AA:BB:CC:11:22:33",
        name = "Thermal BT-58",
        address = "AA:BB:CC:11:22:33",
        transport = PrinterTransport.BLUETOOTH
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        DefaultPosThermalPrinterService.resetForTesting(context)
        DefaultPosThermalPrinterService.ioDispatcher = Dispatchers.Unconfined
        BatchPrintQueueManager.resetForTesting()
        BatchPrintQueueManager.workerDispatcher = Dispatchers.Unconfined
        BatchPrintQueueManager.defaultInterJobDelayMs = 0L
    }

    @After
    fun tearDown() {
        BatchPrintQueueManager.resetForTesting()
        DefaultPosThermalPrinterService.resetForTesting(context)
        testScope.cancel()
    }

    private fun sampleSale(id: Long, number: String, totalCents: Long): Pair<SaleEntity, List<SaleItemEntity>> {
        val sale = SaleEntity(
            id = id,
            invoiceNumber = number,
            timestamp = 1_700_000_000_000L + id * 1000L,
            cashierName = "Admin",
            customerName = "Client $id",
            totalAmount = totalCents,
            discountAmount = 0L,
            discountType = "FIXED",
            paidAmount = totalCents,
            changeAmount = 0L,
            paymentMethod = "CASH"
        )
        val items = listOf(
            SaleItemEntity(
                saleId = id,
                productId = 100L + id,
                productName = "Item $id",
                quantity = 2.0,
                unitPrice = totalCents / 2,
                totalLinePrice = totalCents
            )
        )
        return sale to items
    }

    @Test
    fun batchQueue_printsSelectedInvoicesSequentiallyInOrder() = runBlocking {
        val printedTitles = CopyOnWriteArrayList<String>()
        DefaultPosThermalPrinterService.transportSendOverrideForTest = { _, _, _, jobTitle, _ ->
            printedTitles.add(jobTitle)
            true to "OK"
        }

        val entries = listOf(
            sampleSale(1L, "INV-001", 10000L),
            sampleSale(2L, "INV-002", 25000L),
            sampleSale(3L, "INV-003", 40000L)
        )

        BatchPrintQueueManager.startBatch(
            scope = testScope,
            context = context,
            entries = entries,
            settings = testSettings,
            profile = testProfile,
            target = testBtPrinter,
            interJobDelayMs = 0L
        )

        val state = BatchPrintQueueManager.state.value
        assertTrue(state.isActive)
        assertFalse(state.isRunning)
        assertEquals(3, state.totalCount)
        assertEquals(3, state.successCount)
        assertEquals(0, state.failedCount)
        assertEquals(1f, state.progressFraction, 0.001f)
        assertTrue(state.items.all { it.status == BatchPrintItemStatus.SUCCESS })
        assertEquals(3, printedTitles.size)
        assertTrue(printedTitles[0].contains("INV-001"))
        assertTrue(printedTitles[1].contains("INV-002"))
        assertTrue(printedTitles[2].contains("INV-003"))
        assertEquals(3, DefaultPosThermalPrinterService.printHistory.value.size)
    }

    @Test
    fun batchQueue_partialFailureAndRetryOnlyReprintsFailedInvoices() = runBlocking {
        var shouldFailSecond = true
        val printedTitles = CopyOnWriteArrayList<String>()
        DefaultPosThermalPrinterService.transportSendOverrideForTest = { _, _, _, jobTitle, _ ->
            printedTitles.add(jobTitle)
            if (shouldFailSecond && jobTitle.contains("INV-102")) {
                false to "Paper out"
            } else {
                true to "OK"
            }
        }

        val entries = listOf(
            sampleSale(11L, "INV-101", 5000L),
            sampleSale(12L, "INV-102", 8000L),
            sampleSale(13L, "INV-103", 12000L)
        )

        BatchPrintQueueManager.startBatch(
            scope = testScope,
            context = context,
            entries = entries,
            settings = testSettings,
            profile = testProfile,
            target = testBtPrinter,
            interJobDelayMs = 0L
        )

        val afterFirstRun = BatchPrintQueueManager.state.value
        assertEquals(2, afterFirstRun.successCount)
        assertEquals(1, afterFirstRun.failedCount)
        assertTrue(afterFirstRun.hasRetryableItems)
        assertEquals(BatchPrintItemStatus.FAILED, afterFirstRun.items[1].status)
        assertEquals("Paper out", afterFirstRun.items[1].errorMessage)

        // Now fix the printer and retry only failed items
        shouldFailSecond = false
        printedTitles.clear()
        BatchPrintQueueManager.retryFailed(
            scope = testScope,
            context = context,
            settings = testSettings,
            profile = testProfile,
            target = testBtPrinter,
            interJobDelayMs = 0L
        )

        val afterRetry = BatchPrintQueueManager.state.value
        assertEquals(3, afterRetry.successCount)
        assertEquals(0, afterRetry.failedCount)
        assertFalse(afterRetry.hasRetryableItems)
        assertEquals(1, printedTitles.size)
        assertTrue(printedTitles[0].contains("INV-102"))
    }

    @Test
    fun batchQueue_cancelMarksRemainingItemsCancelled() = runBlocking {
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        BatchPrintQueueManager.workerDispatcher = Dispatchers.Default
        DefaultPosThermalPrinterService.ioDispatcher = Dispatchers.Default
        try {
            DefaultPosThermalPrinterService.transportSendOverrideForTest = { _, _, _, _, _ ->
                delay(120L)
                true to "OK"
            }

            val entries = listOf(
                sampleSale(21L, "INV-201", 1000L),
                sampleSale(22L, "INV-202", 2000L),
                sampleSale(23L, "INV-203", 3000L),
                sampleSale(24L, "INV-204", 4000L)
            )

            BatchPrintQueueManager.startBatch(
                scope = ioScope,
                context = context,
                entries = entries,
                settings = testSettings,
                profile = testProfile,
                target = testBtPrinter,
                interJobDelayMs = 80L
            )

            delay(40L)
            BatchPrintQueueManager.cancelQueue()

            val state = BatchPrintQueueManager.state.value
            assertFalse(state.isRunning)
            assertTrue("Expected cancelled items after cancelQueue()", state.cancelledCount >= 2)
            assertTrue(state.hasRetryableItems)
        } finally {
            ioScope.cancel()
        }
    }

    @Test
    fun wifiDiagnostics_providesActionableGuidanceForAllCommonFailureModes() {
        val noWifi = WifiNetworkSocketHelper.diagnoseWifiFailure(
            context = null,
            ip = "192.168.1.100",
            port = 9100,
            throwable = SocketTimeoutException("connect timed out"),
            lang = "AR",
            wifiConnectedOverride = false,
            localIpOverride = null
        )
        assertTrue(noWifi.isPermanentFailure)
        assertTrue(noWifi.suggestAndroidPrintFallback)
        assertTrue(noWifi.message.contains("Wi-Fi"))

        val subnetMismatch = WifiNetworkSocketHelper.diagnoseWifiFailure(
            context = null,
            ip = "192.168.1.100",
            port = 9100,
            throwable = SocketTimeoutException("timeout"),
            lang = "AR",
            wifiConnectedOverride = true,
            localIpOverride = "10.0.2.15"
        )
        assertTrue(subnetMismatch.isPermanentFailure)
        assertTrue(subnetMismatch.message.contains("10.0.2.15"))
        assertTrue(subnetMismatch.suggestAndroidPrintFallback)

        val port631 = WifiNetworkSocketHelper.diagnoseWifiFailure(
            context = null,
            ip = "192.168.1.50",
            port = 631,
            throwable = null,
            lang = "FR",
            wifiConnectedOverride = true,
            localIpOverride = "192.168.1.20"
        )
        assertTrue(port631.isPermanentFailure)
        assertTrue(port631.message.contains("631"))
        assertTrue(port631.suggestAndroidPrintFallback)

        val refused = WifiNetworkSocketHelper.diagnoseWifiFailure(
            context = null,
            ip = "192.168.1.50",
            port = 9100,
            throwable = ConnectException("Connection refused"),
            lang = "AR",
            wifiConnectedOverride = true,
            localIpOverride = "192.168.1.20"
        )
        assertTrue(refused.isPermanentFailure)
        assertTrue(refused.message.contains("9100"))
        assertTrue(refused.suggestAndroidPrintFallback)
    }

    @Test
    fun wifiSocketHelper_sendsChunkedPayloadIntactOverLoopbackTcp() {
        val payload = ByteArray(10_500) { idx -> (idx % 251).toByte() }
        val received = ByteArrayOutputStream()

        ServerSocket(0).use { server ->
            val port = server.localPort
            val readerThread = Thread {
                server.accept().use { client ->
                    client.getInputStream().copyTo(received)
                }
            }
            readerThread.start()

            WifiNetworkSocketHelper.openSocket(
                context = context,
                ip = "127.0.0.1",
                port = port,
                connectTimeoutMs = 2000,
                readTimeoutMs = 2000
            ).use { socket ->
                WifiNetworkSocketHelper.writeChunked(
                    socket = socket,
                    data = payload,
                    chunkSize = 2048,
                    drainDelayMs = 20L
                )
            }

            readerThread.join(3000L)
            assertArrayEquals(payload, received.toByteArray())
        }
    }
}
