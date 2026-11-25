package com.example.printer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PosThermalPrinterServiceTest {

    private lateinit var context: Context
    private val service: PosThermalPrinterService = DefaultPosThermalPrinterService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DefaultPosThermalPrinterService.resetForTesting(context)
    }

    @After
    fun tearDown() {
        DefaultPosThermalPrinterService.resetForTesting(context)
    }

    @Test
    fun `discoverPrinters returns USB thermal printers and connect disconnect updates service state`() = runBlocking {
        val usbPrinter = ThermalPrinterDevice(
            id = "/dev/bus/usb/001/002",
            name = "Xprinter XP-80C",
            address = "/dev/bus/usb/001/002",
            transport = PrinterTransport.USB,
            isPairedOrAuthorized = true,
            vendorId = 0x0483,
            productId = 0x5743,
            details = "VID:0483 PID:5743"
        )
        UsbPrinterConnectionManager.discoverOverrideForTest = { listOf(usbPrinter) }
        UsbPrinterConnectionManager.connectOverrideForTest = { _, _, _ -> Result.success(usbPrinter) }

        val discovered = service.discoverPrinters(context, PrinterTransport.USB)
        assertEquals(1, discovered.size)
        assertEquals("Xprinter XP-80C", discovered.first().name)
        assertEquals(PrinterTransport.USB, discovered.first().transport)

        val connectRes = service.connect(context, usbPrinter, lang = "AR")
        assertTrue(connectRes.isSuccess)
        assertEquals(usbPrinter, service.activePrinter.value)
        assertTrue(service.connectionStatus.value is PrinterConnectionStatus.Connected)

        service.disconnect("Cashier ended shift")
        assertNull(service.activePrinter.value)
        val status = service.connectionStatus.value
        assertTrue(status is PrinterConnectionStatus.Disconnected)
        assertEquals("Cashier ended shift", (status as PrinterConnectionStatus.Disconnected).reason)
    }

    @Test
    fun `formatPlainTextReceipt and formatReceiptBytes support 58mm and 80mm widths in AR RTL and FR LTR`() {
        val sampleReceipt = ThermalReceiptData(
            documentTitle = "فاتورة بيع",
            storeName = "Atlas Market",
            storeAddress = "Casablanca",
            storePhone = "0600112233",
            invoiceNumber = "INV-2026-001",
            cashierName = "Karim",
            customerName = "Youssef",
            items = listOf(
                ThermalReceiptItem("قهوة عربية", 2.0, 45.00, 90.00),
                ThermalReceiptItem("ماء معدني", 1.0, 5.00, 5.00)
            ),
            subtotal = 95.00,
            discountAmount = 5.00,
            grandTotal = 90.00,
            paymentMethod = "CASH",
            paidAmount = 100.00,
            changeAmount = 10.00,
            footerMessage = "شكراً لزيارتكم"
        )

        // 1. 58mm French LTR plain text + raw ESC/POS
        val fr58Profile = ThermalPrintProfile(
            paperWidth = ThermalPaperWidth.MM_58,
            language = "FR",
            textDirection = ReceiptTextDirection.LTR,
            fontSize = ThermalFontSize.SMALL,
            headerText = "BIENVENUE CHEZ ATLAS",
            footerText = "A BIENTOT",
            showLogo = false,
            useRasterRendering = false
        )
        val fr58Text = service.formatPlainTextReceipt(sampleReceipt, fr58Profile)
        assertTrue(fr58Text.contains("-".repeat(32)))
        assertFalse(fr58Text.contains("-".repeat(48)))
        assertTrue(fr58Text.contains("Facture N°:"))
        assertTrue(fr58Text.contains("BIENVENUE CHEZ ATLAS"))
        assertTrue(fr58Text.contains("A BIENTOT"))
        assertTrue(fr58Text.contains("90.00"))
        assertFalse(fr58Text.contains("المتبقي في الذمة"))
        assertFalse(fr58Text.contains("Reste dû"))

        val fr58Bytes = service.formatReceiptBytes(sampleReceipt, fr58Profile, context)
        // ESC @ init + ESC M 1 (Font B for SMALL)
        assertEquals(0x1B.toByte(), fr58Bytes[0])
        assertEquals(0x40.toByte(), fr58Bytes[1])
        assertTrue(containsSubsequence(fr58Bytes, byteArrayOf(0x1B, 0x4D, 0x01)))

        // 2. 80mm Arabic RTL plain text + ESC/POS raster GS v 0
        val ar80Profile = ThermalPrintProfile(
            paperWidth = ThermalPaperWidth.MM_80,
            language = "AR",
            textDirection = ReceiptTextDirection.RTL,
            fontSize = ThermalFontSize.LARGE,
            headerText = "أهلاً بكم في متجرنا",
            footerText = "زيارتكم تشرفنا",
            showLogo = true,
            useRasterRendering = true
        )
        val ar80Text = service.formatPlainTextReceipt(sampleReceipt, ar80Profile)
        assertTrue(ar80Text.contains("-".repeat(48)))
        assertTrue(ar80Text.contains("فاتورة رقم:"))
        assertTrue(ar80Text.contains("أهلاً بكم في متجرنا"))
        assertTrue(ar80Text.contains("زيارتكم تشرفنا"))

        val ar80RasterBytes = service.formatReceiptBytes(sampleReceipt, ar80Profile, context)
        // Must start with ESC @ and include GS v 0 0x00 raster band command
        assertEquals(0x1B.toByte(), ar80RasterBytes[0])
        assertEquals(0x40.toByte(), ar80RasterBytes[1])
        assertTrue(
            "Expected ESC/POS GS v 0 raster command in Arabic RTL output",
            containsSubsequence(ar80RasterBytes, byteArrayOf(0x1D, 0x76, 0x30, 0x00))
        )
    }

    @Test
    fun `printReceipt automatically retries transient transport error and records SUCCESS in history`() = runBlocking {
        val btPrinter = ThermalPrinterDevice(
            id = "00:11:22:33:44:55",
            name = "POS-58 BT",
            address = "00:11:22:33:44:55",
            transport = PrinterTransport.BLUETOOTH
        )
        val attempts = AtomicInteger(0)
        DefaultPosThermalPrinterService.transportSendOverrideForTest = { _, _, _, _, _ ->
            val current = attempts.incrementAndGet()
            if (current == 1) {
                false to "Transient RFCOMM broken pipe"
            } else {
                true to "تمت الطباعة بنجاح"
            }
        }

        val receipt = ThermalReceiptData(
            invoiceNumber = "INV-RETRY-01",
            items = listOf(ThermalReceiptItem("Bread", 1.0, 2.0, 2.0)),
            subtotal = 2.0,
            grandTotal = 2.0
        )
        val profile = ThermalPrintProfile(
            paperWidth = ThermalPaperWidth.MM_58,
            language = "AR",
            maxRetryAttempts = 2,
            retryDelayMs = 10L
        )

        val result = service.printReceipt(context, receipt, profile, btPrinter, "Sale #INV-RETRY-01")
        assertTrue(result.success)
        assertEquals(2, result.attempts)
        assertEquals(2, attempts.get())
        assertNull(result.errorCause)

        val history = service.printHistory.value
        assertEquals(1, history.size)
        val job = history.first()
        assertEquals(result.jobId, job.id)
        assertEquals(PrintJobStatus.SUCCESS, job.status)
        assertEquals(2, job.attemptCount)
        assertEquals(PrinterTransport.BLUETOOTH, job.transport)
    }

    @Test
    fun `failed print job is logged as FAILED and manual retryPrintJob recovers to SUCCESS`() = runBlocking {
        val usbPrinter = ThermalPrinterDevice(
            id = "/dev/bus/usb/001/005",
            name = "Epson TM-T20III",
            address = "/dev/bus/usb/001/005",
            transport = PrinterTransport.USB
        )
        var shouldFail = true
        DefaultPosThermalPrinterService.transportSendOverrideForTest = { _, _, _, _, _ ->
            if (shouldFail) false to "USB cable unplugged" else true to "USB printed successfully"
        }

        val sale = SaleEntity(
            id = 10L,
            invoiceNumber = "FAC-2026-009",
            timestamp = 1_700_000_000_000L,
            cashierName = "Salma",
            customerId = 1L,
            customerName = "Client Walk-In",
            totalAmount = 15000L, // 150.00
            discountAmount = 0L,
            discountType = "AMOUNT",
            paidAmount = 15000L,
            changeAmount = 0L,
            paymentMethod = "CASH"
        )
        val saleItems = listOf(
            SaleItemEntity(
                saleId = 10L,
                productId = 1L,
                productName = "Olive Oil 1L",
                quantity = 2.0,
                unitPrice = 7500L,
                totalLinePrice = 15000L,
                unitCost = 5000L
            )
        )
        val settings = GlobalSettingsEntity(
            shopName = "RM.POS Market",
            receiptHeader = "Merci de votre confiance",
            receiptFooter = "A bientôt",
            printerPaperWidth = 80
        )

        val initialResult = service.printSaleInvoice(
            context = context,
            sale = sale,
            items = saleItems,
            settings = settings,
            lang = "FR",
            target = usbPrinter,
            profileOverride = ThermalPrintProfile(
                paperWidth = ThermalPaperWidth.MM_80,
                language = "FR",
                maxRetryAttempts = 0,
                retryDelayMs = 0L
            )
        )

        assertFalse(initialResult.success)
        assertEquals(1, initialResult.attempts)
        assertNotNull(initialResult.errorCause)
        assertTrue(service.connectionStatus.value is PrinterConnectionStatus.Error)

        val failedRecord = service.printHistory.value.first { it.id == initialResult.jobId }
        assertEquals(PrintJobStatus.FAILED, failedRecord.status)
        assertEquals("USB cable unplugged", failedRecord.errorMessage)
        assertEquals(80, failedRecord.paperWidthMm)

        // Plug cable back in and manually retry from print log
        shouldFail = false
        val retryResult = service.retryPrintJob(context, initialResult.jobId)
        assertTrue(retryResult.success)
        assertEquals(2, retryResult.attempts)
        assertTrue(service.connectionStatus.value is PrinterConnectionStatus.Connected)

        val recoveredRecord = service.printHistory.value.first { it.id == initialResult.jobId }
        assertEquals(PrintJobStatus.SUCCESS, recoveredRecord.status)
        assertEquals(2, recoveredRecord.attemptCount)
        assertNull(recoveredRecord.errorMessage)
    }

    @Test
    fun `printTestReceipt sends diagnostic payload and clearPrintHistory empties log`() = runBlocking {
        var capturedBytes: ByteArray? = null
        val btDevice = ThermalPrinterDevice(
            id = "AA:BB:CC:DD:EE:FF",
            name = "RPP-300",
            address = "AA:BB:CC:DD:EE:FF",
            transport = PrinterTransport.BLUETOOTH
        )
        DefaultPosThermalPrinterService.transportSendOverrideForTest = { _, _, bytes, _, _ ->
            capturedBytes = bytes
            true to "Test OK"
        }

        val result = service.printTestReceipt(
            context = context,
            settings = GlobalSettingsEntity(shopName = "RM.POS Shop"),
            profile = ThermalPrintProfile(
                paperWidth = ThermalPaperWidth.MM_58,
                language = "AR",
                fontSize = ThermalFontSize.NORMAL,
                headerText = "اختبار الترويسة",
                footerText = "اختبار التذييل",
                useRasterRendering = false
            ),
            target = btDevice
        )

        assertTrue(result.success)
        assertNotNull(capturedBytes)
        val text = String(capturedBytes!!, Charsets.UTF_8)
        assertTrue(text.contains("RM.POS Shop"))
        assertTrue(text.contains("اختبار الترويسة"))
        assertTrue(text.contains("اختبار التذييل"))
        assertEquals(1, service.printHistory.value.size)

        service.clearPrintHistory(context)
        assertTrue(service.printHistory.value.isEmpty())
    }

    @Test
    fun `printTestReceipt over WIFI sends Wi-Fi diagnostic payload and logs WIFI transport`() = runBlocking {
        var capturedTarget: ThermalPrinterDevice? = null
        var capturedBytes: ByteArray? = null
        val wifiPrinter = ThermalPrinterDevice(
            id = "192.168.1.150:9100",
            name = "Wi-Fi (192.168.1.150:9100)",
            address = "192.168.1.150",
            transport = PrinterTransport.WIFI,
            port = 9100
        )
        DefaultPosThermalPrinterService.transportSendOverrideForTest = { _, target, bytes, _, _ ->
            capturedTarget = target
            capturedBytes = bytes
            true to "تمت الطباعة بنجاح عبر الشبكة (192.168.1.150:9100)"
        }

        val result = service.printTestReceipt(
            context = context,
            settings = GlobalSettingsEntity(shopName = "RM.POS Wi-Fi Shop"),
            profile = ThermalPrintProfile(
                paperWidth = ThermalPaperWidth.MM_80,
                language = "AR",
                useRasterRendering = false
            ),
            target = wifiPrinter
        )

        assertTrue(result.success)
        assertEquals(PrinterTransport.WIFI, capturedTarget?.transport)
        assertEquals("192.168.1.150", capturedTarget?.address)
        assertEquals(9100, capturedTarget?.port)
        assertNotNull(capturedBytes)
        val decoded = String(capturedBytes!!, Charsets.UTF_8)
        assertTrue(decoded.contains("اختبار الطابعة عبر Wi-Fi"))
        assertTrue(decoded.contains("192.168.1.150:9100"))
        assertTrue(decoded.contains("فحص اتصال Wi-Fi / IP"))

        val record = service.printHistory.value.first { it.id == result.jobId }
        assertEquals(PrinterTransport.WIFI, record.transport)
        assertEquals(PrintJobStatus.SUCCESS, record.status)
        assertEquals(80, record.paperWidthMm)
    }

    private fun containsSubsequence(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }
}
