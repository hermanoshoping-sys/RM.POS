package com.example.data

import androidx.test.core.app.ApplicationProvider
import com.example.domain.stocktaking.InventoryItemStatus
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import com.example.domain.stocktaking.StocktakingReportBuilder
import com.example.ui.screens.stocktaking.StocktakingReportExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StocktakingReportBuilderTest {

    private fun sampleInventory(
        id: Long = 1L,
        ref: String = "INV-20261123-0001",
        status: String = InventoryStatus.COMPLETED.code,
        total: Int = 4,
        counted: Int = 3,
        discrepancies: Int = 2,
        shortageCents: Long = 2000L,
        surplusCents: Long = 500L,
        netCents: Long = -1500L
    ) = InventoryEntity(
        id = id,
        reference = ref,
        title = "جرد نهاية الشهر",
        type = InventoryType.GENERAL.code,
        status = status,
        createdBy = "Admin",
        approvedBy = if (status == InventoryStatus.COMPLETED.code) "Manager" else null,
        completedAt = if (status == InventoryStatus.COMPLETED.code) 1_700_000_000_000L else null,
        totalItemsCount = total,
        countedItemsCount = counted,
        discrepancyItemsCount = discrepancies,
        totalShortageValueCents = shortageCents,
        totalSurplusValueCents = surplusCents,
        netDiscrepancyValueCents = netCents,
        notes = "مراجعة رفوف المشروبات"
    )

    private fun sampleItems(): List<InventoryItemEntity> = listOf(
        // Beverages: shortage of 2 units @ 10.00 cost (unitSellPrice = 99.00 MUST NOT be used!)
        InventoryItemEntity(
            id = 1L,
            inventoryId = 1L,
            productId = 101L,
            productName = "Juice, \"Orange\" 1L",
            barcode = "6110001",
            categoryId = 1L,
            categoryName = "Beverages",
            unit = "btl",
            expectedQuantity = 10.0,
            systemStockAtCount = 10.0,
            countedQuantity = 8.0,
            difference = -2.0,
            unitCostCents = 1000L,
            unitSellPriceCents = 9900L,
            discrepancyValueCents = -2000L,
            status = InventoryItemStatus.SHORTAGE.code,
            notes = "Damaged bottle, leaky cap"
        ),
        // Beverages: matching item
        InventoryItemEntity(
            id = 2L,
            inventoryId = 1L,
            productId = 102L,
            productName = "Water 1.5L",
            barcode = "6110002",
            categoryId = 1L,
            categoryName = "Beverages",
            unit = "btl",
            expectedQuantity = 20.0,
            systemStockAtCount = 20.0,
            countedQuantity = 20.0,
            difference = 0.0,
            unitCostCents = 300L,
            unitSellPriceCents = 500L,
            discrepancyValueCents = 0L,
            status = InventoryItemStatus.MATCHING.code
        ),
        // Dairy: surplus of +1 unit @ 5.00 cost
        InventoryItemEntity(
            id = 3L,
            inventoryId = 1L,
            productId = 103L,
            productName = "Milk 1L",
            barcode = "6110003",
            categoryId = 2L,
            categoryName = "Dairy",
            unit = "pack",
            expectedQuantity = 5.0,
            systemStockAtCount = 5.0,
            countedQuantity = 6.0,
            difference = 1.0,
            unitCostCents = 500L,
            unitSellPriceCents = 900L,
            discrepancyValueCents = 500L,
            status = InventoryItemStatus.SURPLUS.code
        ),
        // Uncategorized & uncounted
        InventoryItemEntity(
            id = 4L,
            inventoryId = 1L,
            productId = 104L,
            productName = "Promo Item",
            barcode = "6110004",
            categoryId = 0L,
            categoryName = "",
            unit = "pcs",
            expectedQuantity = 3.0,
            systemStockAtCount = null,
            countedQuantity = null,
            difference = 0.0,
            unitCostCents = 1500L,
            unitSellPriceCents = 2500L,
            discrepancyValueCents = 0L,
            status = InventoryItemStatus.UNCOUNTED.code
        )
    )

    @Test
    fun buildCategoryBreakdown_calculatesPurchaseCostCentsAndSortsByImpact() {
        val breakdown = StocktakingReportBuilder.buildCategoryBreakdown(
            items = sampleItems(),
            uncategorizedLabel = "Other"
        )

        assertEquals(3, breakdown.size)
        // Highest absolute impact first: Beverages (-2000 cents)
        val bev = breakdown[0]
        assertEquals("Beverages", bev.categoryName)
        assertEquals(2, bev.totalItemsCount)
        assertEquals(2, bev.countedItemsCount)
        assertEquals(1, bev.matchingItemsCount)
        assertEquals(1, bev.shortageItemsCount)
        assertEquals(0, bev.surplusItemsCount)
        assertEquals(2000L, bev.shortageValueCents)
        assertEquals(0L, bev.surplusValueCents)
        assertEquals(-2000L, bev.netDiscrepancyValueCents)

        // Second: Dairy (+500 cents)
        val dairy = breakdown[1]
        assertEquals("Dairy", dairy.categoryName)
        assertEquals(500L, dairy.surplusValueCents)
        assertEquals(500L, dairy.netDiscrepancyValueCents)

        // Third: Other (uncounted -> 0 impact)
        val other = breakdown[2]
        assertEquals("Other", other.categoryName)
        assertEquals(1, other.totalItemsCount)
        assertEquals(0, other.countedItemsCount)
        assertEquals(0L, other.netDiscrepancyValueCents)
    }

    @Test
    fun buildHistoryAnalytics_aggregatesOnlyCompletedFinancialsAndMeanAccuracy() {
        val completed1 = sampleInventory(
            id = 1L,
            ref = "INV-1",
            status = InventoryStatus.COMPLETED.code,
            counted = 10,
            discrepancies = 2, // 80% accuracy
            shortageCents = 3000L,
            surplusCents = 1000L,
            netCents = -2000L
        )
        val completed2 = sampleInventory(
            id = 2L,
            ref = "INV-2",
            status = InventoryStatus.COMPLETED.code,
            counted = 20,
            discrepancies = 0, // 100% accuracy
            shortageCents = 0L,
            surplusCents = 500L,
            netCents = 500L
        )
        val active = sampleInventory(
            id = 3L,
            ref = "INV-3",
            status = InventoryStatus.IN_PROGRESS.code,
            counted = 5,
            discrepancies = 5,
            shortageCents = 99999L,
            surplusCents = 0L,
            netCents = -99999L
        )
        val cancelled = sampleInventory(
            id = 4L,
            ref = "INV-4",
            status = InventoryStatus.CANCELLED.code,
            counted = 4,
            discrepancies = 4,
            shortageCents = 88888L,
            surplusCents = 0L,
            netCents = -88888L
        )

        val stats = StocktakingReportBuilder.buildHistoryAnalytics(
            listOf(completed1, completed2, active, cancelled)
        )

        assertEquals(4, stats.totalSessions)
        assertEquals(2, stats.completedSessions)
        assertEquals(1, stats.activeSessions)
        assertEquals(1, stats.cancelledSessions)
        assertEquals(90.0f, stats.averageAccuracyPercent, 0.01f)
        assertEquals(30, stats.totalItemsAudited)
        assertEquals(3000L, stats.cumulativeShortageValueCents)
        assertEquals(1500L, stats.cumulativeSurplusValueCents)
        assertEquals(-1500L, stats.cumulativeNetDiscrepancyCents)
    }

    @Test
    fun buildCsvReport_includesUtf8BomEscapesRfc4180AndSupportsArAndFr() {
        val inv = sampleInventory()
        val items = sampleItems()

        val csvAr = StocktakingReportBuilder.buildCsvReport(inv, items, "DA", "AR", "سوبرماركت النور")
        assertTrue(csvAr.startsWith(StocktakingReportBuilder.UTF8_BOM))
        assertTrue(csvAr.contains("\"Juice, \"\"Orange\"\" 1L\""))
        assertTrue(csvAr.contains("\"Damaged bottle, leaky cap\""))
        assertTrue(csvAr.contains("-20.00"))
        assertTrue(csvAr.contains("+5.00"))
        assertTrue(csvAr.contains("-15.00"))
        assertTrue(csvAr.contains("المنتج"))

        val csvFr = StocktakingReportBuilder.buildCsvReport(inv, items, "DA", "FR", "Supermarché Nour")
        assertTrue(csvFr.startsWith(StocktakingReportBuilder.UTF8_BOM))
        assertTrue(csvFr.contains("Produit,Code-barres"))
        assertTrue(csvFr.contains("Impact Net,-15.00"))
    }

    @Test
    fun buildPdfReportPayload_filtersDiscrepanciesOnlyWhenRequested() {
        val inv = sampleInventory()
        val items = sampleItems()

        val fullPayload = StocktakingReportBuilder.buildPdfReportPayload(inv, items, "DA", "FR", discrepanciesOnly = false)
        assertEquals(4, fullPayload.rows.size)
        assertTrue(fullPayload.summaryLines.any { it.first.contains("Impact financier net") })

        val discPayload = StocktakingReportBuilder.buildPdfReportPayload(inv, items, "DA", "AR", discrepanciesOnly = true)
        assertEquals(2, discPayload.rows.size)
        assertTrue(discPayload.docTitle.contains("فوارق"))
    }

    @Test
    fun reportExporter_generatesValidCsvFileOnDiskAndCompletePdfPayload() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inv = sampleInventory()
        val items = sampleItems()

        val csvFile = StocktakingReportExporter.generateReportCsvFile(
            context = context,
            inventory = inv,
            items = items,
            currency = "DA",
            lang = "FR",
            shopName = "RM.POS Store"
        )
        assertTrue("CSV file should exist", csvFile.exists())
        assertTrue("CSV file should be non-empty", csvFile.length() > 100L)
        assertTrue("CSV should be placed in shared_files cache dir", csvFile.parentFile?.name == "shared_files")
        val csvText = csvFile.readText(Charsets.UTF_8)
        assertTrue(csvText.startsWith(StocktakingReportBuilder.UTF8_BOM))
        assertTrue(csvText.contains(inv.reference))
        assertTrue(csvText.contains("RM.POS Store"))

        val pdfPayloadAr = StocktakingReportBuilder.buildPdfReportPayload(
            inventory = inv,
            items = items,
            currency = "DA",
            lang = "AR",
            discrepanciesOnly = false
        )
        assertEquals(8, pdfPayloadAr.headers.size)
        assertEquals(4, pdfPayloadAr.rows.size)
        // Highest abs discrepancy (-2000 cents) must appear first in PDF table
        assertTrue(pdfPayloadAr.rows.first()[0].contains("Juice"))
        assertTrue(pdfPayloadAr.summaryLines.any { it.second.contains("20.00") })
        assertTrue(pdfPayloadAr.summaryLines.any { it.second.contains("-15.00") })
    }

    @Test
    fun buildCartPdfPayload_includesOnlyCountedItemsAndComputesPurchaseValue() {
        val inv = sampleInventory()
        val items = sampleItems()

        // 8 * 1000 + 20 * 300 + 6 * 500 = 8000 + 6000 + 3000 = 17000 cents (170.00)
        assertEquals(17000L, StocktakingReportBuilder.computeCountedPurchaseValueCents(items))

        val cartPdfAr = StocktakingReportBuilder.buildCartPdfPayload(inv, items, "DA", "AR")
        assertTrue(cartPdfAr.docTitle.contains("سلة الجرد"))
        assertEquals(8, cartPdfAr.headers.size)
        // Only the 3 counted items must appear; uncounted Promo Item (id=4) must be excluded
        assertEquals(3, cartPdfAr.rows.size)
        assertTrue(cartPdfAr.rows.none { it[0].contains("Promo Item") })
        assertTrue(cartPdfAr.summaryLines.any { it.second.contains("3 / 4") })
        assertTrue(cartPdfAr.summaryLines.any { it.second.contains("34") })
        assertTrue(cartPdfAr.summaryLines.any { it.second.contains("170.00") })
        assertTrue(cartPdfAr.summaryLines.any { it.second.contains("-15.00") })

        val cartPdfFr = StocktakingReportBuilder.buildCartPdfPayload(inv, items, "DA", "FR")
        assertTrue(cartPdfFr.docTitle.contains("Panier d'inventaire"))
        assertEquals(3, cartPdfFr.rows.size)
    }

    @Test
    fun reportExporter_generatesGenuineOpenXmlXlsxForCartAndFullReport() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inv = sampleInventory()
        val items = sampleItems()

        // 1. Cart-only Excel (.xlsx) in Arabic (RTL)
        val cartXlsx = StocktakingReportExporter.generateReportXlsxFile(
            context = context,
            inventory = inv,
            items = items,
            currency = "DA",
            lang = "AR",
            shopName = "سوبرماركت النور",
            cartOnly = true
        )
        assertTrue("Cart XLSX should exist", cartXlsx.exists())
        assertTrue("Cart XLSX should have .xlsx extension", cartXlsx.name.endsWith(".xlsx"))
        assertTrue("Cart XLSX should be in shared_files", cartXlsx.parentFile?.name == "shared_files")

        java.util.zip.ZipFile(cartXlsx).use { zip ->
            val requiredEntries = listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/_rels/workbook.xml.rels",
                "xl/workbook.xml",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml"
            )
            for (entryName in requiredEntries) {
                assertTrue("Missing OpenXML entry: $entryName", zip.getEntry(entryName) != null)
            }
            val sheetXml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
                .bufferedReader(Charsets.UTF_8)
                .readText()
            assertTrue("Arabic worksheet should set rightToLeft=1", sheetXml.contains("rightToLeft=\"1\""))
            assertTrue("Should XML-escape product quotes", sheetXml.contains("Juice, &quot;Orange&quot; 1L"))
            assertTrue("Should exclude uncounted item in cartOnly mode", !sheetXml.contains("Promo Item"))
            assertTrue("Should include counted units total 34", sheetXml.contains("<v>34</v>"))
            assertTrue("Should include counted purchase value 170.00", sheetXml.contains("<v>170.00</v>"))
            assertTrue("Should include net discrepancy -15.00", sheetXml.contains("<v>-15.00</v>"))
        }

        // 2. Full Report Excel (.xlsx) in French (LTR) includes uncounted items too
        val fullXlsx = StocktakingReportExporter.generateReportXlsxFile(
            context = context,
            inventory = inv,
            items = items,
            currency = "DA",
            lang = "FR",
            shopName = "RM.POS Store",
            cartOnly = false
        )
        assertTrue("Full XLSX should exist", fullXlsx.exists())
        java.util.zip.ZipFile(fullXlsx).use { zip ->
            val sheetXml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
                .bufferedReader(Charsets.UTF_8)
                .readText()
            assertTrue("French worksheet should set rightToLeft=0", sheetXml.contains("rightToLeft=\"0\""))
            assertTrue("Full report must include uncounted Promo Item", sheetXml.contains("Promo Item"))
            assertTrue("Full report must include reference", sheetXml.contains(inv.reference))
        }
    }
}
