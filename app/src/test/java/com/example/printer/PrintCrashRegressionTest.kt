package com.example.printer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.CustomerEntity
import com.example.data.ExpenseEntity
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.ui.screens.PdfReportExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrintCrashRegressionTest {

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        BluetoothPrinterConnectionManager.resetForTesting()
        BluetoothPrinterService.clearForegroundActivityForTesting()
        InAppPrintPreviewManager.resetForTesting()
    }

    @Test
    fun `findActivity unwraps nested ContextWrapper and MainActivity preserves Activity baseContext for PrintManager`() {
        assertNull(BluetoothPrinterService.findActivity(appContext))
        assertNull(BluetoothPrinterService.findActivity(null))

        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val wrapped = ContextWrapper(ContextWrapper(activity))
        assertSame(activity, BluetoothPrinterService.findActivity(wrapped))

        BluetoothPrinterService.attachForegroundActivity(activity)
        assertSame(activity, BluetoothPrinterService.resolvePrintActivity(appContext))

        activity.finish()
        assertNull(BluetoothPrinterService.findActivity(wrapped))
        assertNull(BluetoothPrinterService.resolvePrintActivity(appContext))

        val mainController = Robolectric.buildActivity(com.example.MainActivity::class.java).create()
        val mainActivity = mainController.get()
        assertSame(mainActivity, BluetoothPrinterService.findActivity(mainActivity))
        // Verify MainActivity baseContext outerContext was NOT replaced by a non-Activity configuration context
        val printService = mainActivity.getSystemService(Context.PRINT_SERVICE)
        assertNotNull(printService)
        mainActivity.finish()
        mainController.destroy()
    }

    @Test
    fun `printReceiptSystem and printInvoiceSystem open InAppPrintPreviewManager instead of failing when PrintSpooler is unavailable`() {
        val sale = SaleEntity(
            id = 42L,
            invoiceNumber = "INV-2026-042",
            timestamp = 1_700_000_000_000L,
            cashierName = "Cashier",
            customerName = "Test <Client>",
            totalAmount = 15000L,
            discountAmount = 0L,
            discountType = "VALUE",
            paidAmount = 15000L,
            changeAmount = 0L,
            paymentMethod = "CASH"
        )
        val items = listOf(
            SaleItemEntity(
                id = 1L,
                saleId = 42L,
                productId = 10L,
                productName = "Item 1",
                quantity = 2.0,
                unitPrice = 7500L,
                totalLinePrice = 15000L
            )
        )

        BluetoothPrinterService.printReceiptSystem(appContext, "وصل اختبار", "Test receipt content <script>")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val receiptReq = InAppPrintPreviewManager.activeRequest.value
        assertNotNull(receiptReq)
        assertEquals("وصل اختبار", receiptReq?.jobTitle)
        assertTrue(receiptReq?.plainTextPreview?.contains("Test receipt content") == true)

        InAppPrintPreviewManager.dismiss()
        assertNull(InAppPrintPreviewManager.activeRequest.value)

        BluetoothPrinterService.printInvoiceSystem(appContext, sale, items, GlobalSettingsEntity())
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val invoiceReq = InAppPrintPreviewManager.activeRequest.value
        assertNotNull(invoiceReq)
        assertEquals("فاتورة_INV-2026-042", invoiceReq?.jobTitle)
        assertTrue(invoiceReq?.plainTextPreview?.contains("INV-2026-042") == true)
    }

    @Test
    fun `PdfReportExporter printPdfFile opens InAppPrintPreviewManager and savePdfToDownloads writes PDF copy`() {
        val validPdfFile = File(appContext.cacheDir, "sample_valid.pdf").apply {
            writeBytes("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".toByteArray())
        }
        PdfReportExporter.printPdfFile(appContext, validPdfFile, "Valid PDF")
        val pdfReq = InAppPrintPreviewManager.activeRequest.value
        assertNotNull(pdfReq)
        assertEquals("Valid PDF", pdfReq?.jobTitle)
        assertNotNull(pdfReq?.pdfFile)

        val saveResult = InAppPrintPreviewManager.savePdfToDownloads(
            context = appContext,
            sourceFile = validPdfFile,
            jobTitle = "Valid PDF",
            copies = 2
        )
        assertTrue(saveResult.isSuccess)
        assertTrue(saveResult.getOrNull().orEmpty().contains("Valid_PDF_x2"))

        val missingFile = File(appContext.cacheDir, "non_existent_print_file.pdf")
        PdfReportExporter.printPdfFile(appContext, missingFile, "Missing")

        PdfReportExporter.printTableDocument(
            context = appContext,
            docTitle = "",
            subtitle = "",
            headers = emptyList(),
            rows = listOf(listOf("row1")),
            shopName = "Shop"
        )
        PdfReportExporter.exportTableDocument(
            context = appContext,
            docTitle = "",
            subtitle = "Sub",
            headers = emptyList(),
            rows = emptyList(),
            asPdf = false,
            shopName = "Shop"
        )

        PdfReportExporter.printSalesInvoicePdf(
            context = appContext,
            sale = SaleEntity(
                id = 1L,
                invoiceNumber = "INV-1",
                timestamp = 1_700_000_000_000L,
                cashierName = "Admin",
                totalAmount = 1000L,
                discountAmount = 0L,
                discountType = "VALUE",
                paidAmount = 1000L,
                changeAmount = 0L,
                paymentMethod = "CASH"
            ),
            items = emptyList()
        )
        PdfReportExporter.printExpenseVoucher(
            context = appContext,
            expense = ExpenseEntity(
                id = 7L,
                title = "Electricity",
                amount = 5000L,
                timestamp = 1_700_000_000_000L,
                category = "Bills"
            ),
            currency = "MAD"
        )
        PdfReportExporter.printAnalyticsReport(
            context = appContext,
            dateText = "2026-11-18",
            totalSales = 100.0,
            netProfit = 40.0,
            expenses = 10.0,
            totalPurchases = 50.0,
            supplierPayments = 20.0,
            totalDebts = 5.0,
            invoiceCount = 2,
            totalSoldUnits = 5,
            avgTicket = 50.0
        )
        PdfReportExporter.printCustomerBarcodeBadge(
            context = appContext,
            customer = CustomerEntity(
                id = 3L,
                name = "Ali",
                phone = "",
                balance = 0L,
                customerNumber = 12,
                barcode = "سيارات-123"
            )
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `Bluetooth and WiFi unified print paths report failure safely without throwing`() {
        val btLatch = CountDownLatch(1)
        var btSuccess = true
        var btMessage = ""
        BluetoothPrinterService.printUnified(
            context = appContext,
            connectionType = "BLUETOOTH",
            printerNameOrMac = "00:11:22:33:44:55",
            textToPrint = "Test line",
            onResult = { ok, msg ->
                btSuccess = ok
                btMessage = msg
                btLatch.countDown()
            }
        )
        val deadline = System.currentTimeMillis() + 3000L
        while (btLatch.count > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertEquals(0L, btLatch.count)
        assertFalse(btSuccess)
        assertFalse(btMessage.isBlank())

        val wifiLatch = CountDownLatch(1)
        var wifiOk = true
        var wifiMsg = ""
        PosPrintServiceImpl.printUnified(
            context = appContext,
            connectionType = "WIFI",
            printerNameOrMac = "",
            receipt = ThermalReceiptData(
                invoiceNumber = "INV-99",
                items = listOf(ThermalReceiptItem("Product", 1.0, 50.0, 50.0)),
                subtotal = 50.0,
                grandTotal = 50.0
            ),
            ip = "   ",
            port = 9100,
            onResult = { ok, msg ->
                wifiOk = ok
                wifiMsg = msg
                wifiLatch.countDown()
            }
        )
        val wifiDeadline = System.currentTimeMillis() + 3000L
        while (wifiLatch.count > 0 && System.currentTimeMillis() < wifiDeadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertEquals(0L, wifiLatch.count)
        assertFalse(wifiOk)
        assertNotNull(wifiMsg)
        assertFalse(wifiMsg.isBlank())
    }

    @Test
    fun `Bluetooth availability checks are defensive and never throw`() {
        BluetoothPrinterService.isBluetoothAvailable(appContext)
        assertFalse(BluetoothPrinterService.isBluetoothEnabled(appContext))
    }
}
