package com.example.ui.screens.stocktaking

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.InventoryEntity
import com.example.data.InventoryItemEntity
import com.example.domain.stocktaking.StocktakingReportBuilder
import com.example.ui.screens.PdfReportExporter
import com.example.ui.theme.Bilingual
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Android file export & print facade for Stocktaking (Inventaire) reports and Inventory Cart.
 * Supports A4 PDF export, system/in-app printing, genuine OpenXML Excel (.xlsx), and UTF-8 BOM CSV.
 */
object StocktakingReportExporter {

    private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    // --- FULL REPORT PDF / PRINT ---
    fun generateReportPdfFile(
        context: Context,
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = "",
        discrepanciesOnly: Boolean = false
    ): File {
        val payload = StocktakingReportBuilder.buildPdfReportPayload(
            inventory = inventory,
            items = items,
            currency = currency,
            lang = lang,
            discrepanciesOnly = discrepanciesOnly
        )
        return PdfReportExporter.generateTableDocumentPdf(
            context = context,
            docTitle = payload.docTitle,
            subtitle = payload.subtitle,
            headers = payload.headers,
            rows = payload.rows,
            summaryLines = payload.summaryLines,
            shopName = shopName
        )
    }

    fun exportReportPdf(
        context: Context,
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = "",
        discrepanciesOnly: Boolean = false
    ) {
        val payload = StocktakingReportBuilder.buildPdfReportPayload(
            inventory = inventory,
            items = items,
            currency = currency,
            lang = lang,
            discrepanciesOnly = discrepanciesOnly
        )
        PdfReportExporter.exportTableDocument(
            context = context,
            docTitle = payload.docTitle,
            subtitle = payload.subtitle,
            headers = payload.headers,
            rows = payload.rows,
            summaryLines = payload.summaryLines,
            asPdf = true,
            shopName = shopName
        )
    }

    fun printReportPdf(
        context: Context,
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = "",
        discrepanciesOnly: Boolean = false
    ) {
        val payload = StocktakingReportBuilder.buildPdfReportPayload(
            inventory = inventory,
            items = items,
            currency = currency,
            lang = lang,
            discrepanciesOnly = discrepanciesOnly
        )
        PdfReportExporter.printTableDocument(
            context = context,
            docTitle = payload.docTitle,
            subtitle = payload.subtitle,
            headers = payload.headers,
            rows = payload.rows,
            summaryLines = payload.summaryLines,
            shopName = shopName
        )
    }

    // --- INVENTORY CART (COUNTED ITEMS ONLY) PDF / PRINT ---
    fun exportCartPdf(
        context: Context,
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = ""
    ) {
        val payload = StocktakingReportBuilder.buildCartPdfPayload(
            inventory = inventory,
            items = items,
            currency = currency,
            lang = lang
        )
        PdfReportExporter.exportTableDocument(
            context = context,
            docTitle = payload.docTitle,
            subtitle = payload.subtitle,
            headers = payload.headers,
            rows = payload.rows,
            summaryLines = payload.summaryLines,
            asPdf = true,
            shopName = shopName
        )
    }

    fun printCartPdf(
        context: Context,
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = ""
    ) {
        val payload = StocktakingReportBuilder.buildCartPdfPayload(
            inventory = inventory,
            items = items,
            currency = currency,
            lang = lang
        )
        PdfReportExporter.printTableDocument(
            context = context,
            docTitle = payload.docTitle,
            subtitle = payload.subtitle,
            headers = payload.headers,
            rows = payload.rows,
            summaryLines = payload.summaryLines,
            shopName = shopName
        )
    }

    // --- GENUINE EXCEL (.xlsx) EXPORT (FULL REPORT OR INVENTORY CART) ---
    fun generateReportXlsxFile(
        context: Context,
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = "",
        cartOnly: Boolean = false
    ): File {
        val sharedDir = ensureSharedCacheDir(context)
        val safeRef = inventory.reference.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (cartOnly) "Panier_Inventaire" else "Inventaire"
        val outFile = File(sharedDir, "${prefix}_${safeRef}_$stamp.xlsx")

        val sheetName = if (cartOnly) {
            Bilingual.tr(lang, "سلة الجرد", "Panier Inventaire")
        } else {
            Bilingual.tr(lang, "الجرد", "Inventaire")
        }
        val worksheetXml = StocktakingReportBuilder.buildXlsxWorksheetXml(
            inventory = inventory,
            items = items,
            currency = currency,
            lang = lang,
            shopName = shopName,
            cartOnly = cartOnly
        )

        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${StocktakingReportBuilder.escapeXml(sheetName)}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/styles.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><color rgb="FF000000"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF0F172A"/><name val="Calibri"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF107C41"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFEF08A"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(worksheetXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        return outFile
    }

    fun exportReportXlsx(
        context: Context,
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = "",
        cartOnly: Boolean = false
    ) {
        try {
            val file = generateReportXlsxFile(
                context = context,
                inventory = inventory,
                items = items,
                currency = currency,
                lang = lang,
                shopName = shopName,
                cartOnly = cartOnly
            )
            val subject = if (cartOnly) {
                Bilingual.tr(lang, "سلة الجرد ${inventory.reference}", "Panier d'inventaire ${inventory.reference}")
            } else {
                Bilingual.tr(lang, "تقرير الجرد ${inventory.reference}", "Rapport d'inventaire ${inventory.reference}")
            }
            shareFile(
                context = context,
                file = file,
                mimeType = XLSX_MIME,
                subject = subject,
                chooserTitle = Bilingual.tr(lang, "تصدير ملف Excel (.xlsx)", "Exporter en Excel (.xlsx)")
            )
        } catch (e: Exception) {
            Toast.makeText(
                context,
                Bilingual.tr(
                    lang,
                    "تعذّر تصدير ملف Excel: ${e.localizedMessage ?: ""}",
                    "Échec de l'export Excel : ${e.localizedMessage ?: ""}"
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // --- CSV (UTF-8 BOM) EXPORT ---
    fun generateReportCsvFile(
        context: Context,
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = ""
    ): File {
        val csvContent = StocktakingReportBuilder.buildCsvReport(
            inventory = inventory,
            items = items,
            currency = currency,
            lang = lang,
            shopName = shopName
        )
        val sharedDir = ensureSharedCacheDir(context)
        val safeRef = inventory.reference.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(sharedDir, "Inventaire_${safeRef}_$stamp.csv")
        outFile.writeText(csvContent, Charsets.UTF_8)
        return outFile
    }

    fun exportReportCsv(
        context: Context,
        inventory: InventoryEntity,
        items: List<InventoryItemEntity>,
        currency: String,
        lang: String,
        shopName: String = ""
    ) {
        try {
            val file = generateReportCsvFile(context, inventory, items, currency, lang, shopName)
            shareFile(
                context = context,
                file = file,
                mimeType = "text/csv",
                subject = Bilingual.tr(lang, "تقرير الجرد ${inventory.reference}", "Rapport d'inventaire ${inventory.reference}"),
                chooserTitle = Bilingual.tr(lang, "تصدير ملف CSV", "Exporter fichier CSV")
            )
        } catch (e: Exception) {
            Toast.makeText(
                context,
                Bilingual.tr(
                    lang,
                    "تعذّر تصدير ملف CSV: ${e.localizedMessage ?: ""}",
                    "Échec de l'export CSV : ${e.localizedMessage ?: ""}"
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun ensureSharedCacheDir(context: Context): File {
        val sharedDir = File(context.cacheDir, "shared_files")
        if (!sharedDir.exists()) {
            sharedDir.mkdirs()
        }
        return sharedDir
    }

    private fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        subject: String,
        chooserTitle: String
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }
}
