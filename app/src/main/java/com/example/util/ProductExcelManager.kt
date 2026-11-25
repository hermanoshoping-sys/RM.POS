package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Xml
import androidx.core.content.FileProvider
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Universal Excel (.xlsx) and CSV (.csv) Manager for Supermarket POS Products.
 * 
 * Strict Column Order as specified by user:
 * Column A (0): كود بار (Barcode)
 * Column B (1): اسم المنتج (Product Name)
 * Column C (2): عائلة المنتج (Product Family / Category)
 * Column D (3): الوحدة (Unit)
 * Column E (4): سعر الشراء (Purchase / Cost Price)
 * Column F (5): ثمن البيع (Sale Price)
 */
object ProductExcelManager {

    data class ProductExcelRow(
        val barcode: String,
        val name: String,
        val familyName: String,
        val unit: String,
        val purchasePrice: Double,
        val sellPrice: Double
    )

    enum class ExportFormat {
        XLSX,
        CSV
    }

    private val HEADERS_AR = listOf("كود بار", "اسم المنتج", "عائلة المنتج", "الوحدة", "سعر الشراء", "ثمن البيع")
    private val HEADERS_FR = listOf("Code-barres", "Nom du produit", "Famille", "Unité", "Prix d'achat", "Prix de vente")

    fun getHeaders(lang: String): List<String> = if (lang == "FR") HEADERS_FR else HEADERS_AR

    // =========================================================================
    // 1. EXPORT TO EXCEL (.xlsx) - Genuine OpenXML Spreadsheet
    // =========================================================================
    fun exportToXlsx(
        context: Context,
        products: List<ProductEntity>,
        categoriesMap: Map<Long, String>,
        lang: String,
        isTemplate: Boolean = false
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = if (isTemplate) "products_template_$timeStamp.xlsx" else "products_inventory_$timeStamp.xlsx"
        val outFile = File(context.cacheDir, fileName)

        val headers = getHeaders(lang)
        val rows = if (isTemplate) {
            listOf(
                ProductExcelRow("6111234567890", if (lang == "AR") "زيت المائدة 1 لتر" else "Huile de Table 1L", if (lang == "AR") "زيوت ومواد غذائية" else "Huiles", if (lang == "AR") "لتر / Litre" else "Litre", 15.50, 18.00),
                ProductExcelRow("6119876543210", if (lang == "AR") "سكر بودرة 1 كغ" else "Sucre en poudre 1kg", if (lang == "AR") "البقالة العامة" else "Épicerie", if (lang == "AR") "كيلو / Kg" else "Kg", 5.20, 6.50)
            )
        } else {
            products.map { p ->
                val family = if (p.categoryName.isNotBlank()) p.categoryName else (categoriesMap[p.categoryId] ?: (if (lang == "AR") "عام" else "Général"))
                ProductExcelRow(
                    barcode = p.barcode,
                    name = p.name,
                    familyName = family,
                    unit = p.unit,
                    purchasePrice = p.purchasePriceDouble,
                    sellPrice = p.sellPriceDouble
                )
            }
        }

        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            // 1. [Content_Types].xml
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

            // 2. _rels/.rels
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 3. xl/_rels/workbook.xml.rels
            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 4. xl/workbook.xml
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${if (lang == "AR") "المنتجات" else "Produits"}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 5. xl/styles.xml
            zip.putNextEntry(ZipEntry("xl/styles.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><color rgb="FF000000"/><name val="Calibri"/></font>
    <font><b/><sz val="12"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF107C41"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
</styleSheet>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 6. xl/worksheets/sheet1.xml
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheetBuilder = StringBuilder()
            sheetBuilder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews>
    <sheetView rightToLeft="${if (lang == "AR") "1" else "0"}" workbookViewId="0"/>
  </sheetViews>
  <cols>
    <col min="1" max="1" width="18" customWidth="1"/>
    <col min="2" max="2" width="30" customWidth="1"/>
    <col min="3" max="3" width="22" customWidth="1"/>
    <col min="4" max="4" width="16" customWidth="1"/>
    <col min="5" max="5" width="16" customWidth="1"/>
    <col min="6" max="6" width="16" customWidth="1"/>
  </cols>
  <sheetData>
""")

            // Row 1: Headers
            sheetBuilder.append("""    <row r="1" ht="28" customHeight="1">
      <c r="A1" t="inlineStr" s="1"><is><t>${escapeXml(headers[0])}</t></is></c>
      <c r="B1" t="inlineStr" s="1"><is><t>${escapeXml(headers[1])}</t></is></c>
      <c r="C1" t="inlineStr" s="1"><is><t>${escapeXml(headers[2])}</t></is></c>
      <c r="D1" t="inlineStr" s="1"><is><t>${escapeXml(headers[3])}</t></is></c>
      <c r="E1" t="inlineStr" s="1"><is><t>${escapeXml(headers[4])}</t></is></c>
      <c r="F1" t="inlineStr" s="1"><is><t>${escapeXml(headers[5])}</t></is></c>
    </row>
""")

            // Data rows
            rows.forEachIndexed { idx, row ->
                val rNum = idx + 2
                sheetBuilder.append("    <row r=\"$rNum\" ht=\"20\" customHeight=\"1\">\n")
                sheetBuilder.append("      <c r=\"A$rNum\" t=\"inlineStr\"><is><t>${escapeXml(row.barcode)}</t></is></c>\n")
                sheetBuilder.append("      <c r=\"B$rNum\" t=\"inlineStr\"><is><t>${escapeXml(row.name)}</t></is></c>\n")
                sheetBuilder.append("      <c r=\"C$rNum\" t=\"inlineStr\"><is><t>${escapeXml(row.familyName)}</t></is></c>\n")
                sheetBuilder.append("      <c r=\"D$rNum\" t=\"inlineStr\"><is><t>${escapeXml(row.unit)}</t></is></c>\n")
                sheetBuilder.append("      <c r=\"E$rNum\"><v>${String.format(Locale.US, "%.2f", row.purchasePrice)}</v></c>\n")
                sheetBuilder.append("      <c r=\"F$rNum\"><v>${String.format(Locale.US, "%.2f", row.sellPrice)}</v></c>\n")
                sheetBuilder.append("    </row>\n")
            }

            sheetBuilder.append("""  </sheetData>
</worksheet>""")

            zip.write(sheetBuilder.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        return outFile
    }

    // =========================================================================
    // 2. EXPORT TO CSV (.csv) - UTF-8 with BOM for Excel Compatibility
    // =========================================================================
    fun exportToCsv(
        context: Context,
        products: List<ProductEntity>,
        categoriesMap: Map<Long, String>,
        lang: String,
        isTemplate: Boolean = false
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = if (isTemplate) "products_template_$timeStamp.csv" else "products_inventory_$timeStamp.csv"
        val outFile = File(context.cacheDir, fileName)

        val headers = getHeaders(lang)
        val rows = if (isTemplate) {
            listOf(
                ProductExcelRow("6111234567890", if (lang == "AR") "زيت المائدة 1 لتر" else "Huile de Table 1L", if (lang == "AR") "زيوت ومواد غذائية" else "Huiles", if (lang == "AR") "لتر / Litre" else "Litre", 15.50, 18.00),
                ProductExcelRow("6119876543210", if (lang == "AR") "سكر بودرة 1 كغ" else "Sucre en poudre 1kg", if (lang == "AR") "البقالة العامة" else "Épicerie", if (lang == "AR") "كيلو / Kg" else "Kg", 5.20, 6.50)
            )
        } else {
            products.map { p ->
                val family = if (p.categoryName.isNotBlank()) p.categoryName else (categoriesMap[p.categoryId] ?: (if (lang == "AR") "عام" else "Général"))
                ProductExcelRow(
                    barcode = p.barcode,
                    name = p.name,
                    familyName = family,
                    unit = p.unit,
                    purchasePrice = p.purchasePriceDouble,
                    sellPrice = p.sellPriceDouble
                )
            }
        }

        outFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            // Write UTF-8 BOM so Microsoft Excel automatically detects Arabic/UTF-8 correctly
            writer.write("\uFEFF")
            writer.write(headers.joinToString(",") { escapeCsv(it) })
            writer.write("\n")

            rows.forEach { row ->
                val line = listOf(
                    escapeCsv(row.barcode),
                    escapeCsv(row.name),
                    escapeCsv(row.familyName),
                    escapeCsv(row.unit),
                    String.format(Locale.US, "%.2f", row.purchasePrice),
                    String.format(Locale.US, "%.2f", row.sellPrice)
                ).joinToString(",")
                writer.write(line)
                writer.write("\n")
            }
        }

        return outFile
    }

    // =========================================================================
    // 3. READ & PARSE (Supports both .xlsx and .csv files from Uri)
    // =========================================================================
    fun parseProductsFromUri(context: Context, uri: Uri): List<ProductExcelRow> {
        val stream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        val bufferedStream = stream.buffered()

        // Peek first 4 bytes to check if it's a Zip / XLSX file (PK\x03\x04 = 0x50 0x4B 0x03 0x04)
        bufferedStream.mark(8)
        val headerBytes = ByteArray(4)
        val readBytes = bufferedStream.read(headerBytes, 0, 4)
        bufferedStream.reset()

        val isZip = readBytes == 4 && headerBytes[0] == 0x50.toByte() && headerBytes[1] == 0x4B.toByte()

        return if (isZip) {
            parseXlsx(bufferedStream)
        } else {
            parseCsv(bufferedStream)
        }
    }

    // --- XLSX Parser ---
    private fun parseXlsx(inputStream: InputStream): List<ProductExcelRow> {
        val sharedStrings = mutableListOf<String>()
        var sheetBytes: ByteArray? = null

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name.equals("xl/sharedStrings.xml", ignoreCase = true) -> {
                        sharedStrings.addAll(parseSharedStrings(zip))
                    }
                    entry.name.equals("xl/worksheets/sheet1.xml", ignoreCase = true) ||
                            (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") && sheetBytes == null) -> {
                        sheetBytes = zip.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val bytes = sheetBytes ?: return emptyList()
        return parseSheetXml(bytes.inputStream(), sharedStrings)
    }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        var currentText = StringBuilder()
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("t", ignoreCase = true)) {
                        insideT = true
                    } else if (parser.name.equals("si", ignoreCase = true)) {
                        currentText = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideT) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("t", ignoreCase = true)) {
                        insideT = false
                    } else if (parser.name.equals("si", ignoreCase = true)) {
                        list.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseSheetXml(stream: InputStream, sharedStrings: List<String>): List<ProductExcelRow> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, "UTF-8")

        val rawRows = mutableListOf<Map<Int, String>>()
        var currentRowMap = mutableMapOf<Int, String>()
        var currentColIndex = 0
        var currentCellColIndex = 0
        var currentCellType = ""
        var currentCellValue = StringBuilder()
        var insideCell = false
        var insideValue = false
        var insideInlineT = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    if (name.equals("row", ignoreCase = true)) {
                        currentRowMap = mutableMapOf()
                        currentColIndex = 0
                    } else if (name.equals("c", ignoreCase = true)) {
                        insideCell = true
                        currentCellValue = StringBuilder()
                        currentCellType = parser.getAttributeValue(null, "t") ?: ""
                        val cellRef = parser.getAttributeValue(null, "r")
                        currentCellColIndex = if (cellRef != null) {
                            columnLetterToIndex(cellRef)
                        } else {
                            currentColIndex
                        }
                    } else if (name.equals("v", ignoreCase = true)) {
                        insideValue = true
                    } else if (name.equals("t", ignoreCase = true)) {
                        insideInlineT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideValue || insideInlineT) {
                        currentCellValue.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    if (name.equals("v", ignoreCase = true)) {
                        insideValue = false
                    } else if (name.equals("t", ignoreCase = true)) {
                        insideInlineT = false
                    } else if (name.equals("c", ignoreCase = true)) {
                        insideCell = false
                        val text = if (currentCellType.equals("s", ignoreCase = true)) {
                            val idx = currentCellValue.toString().toIntOrNull()
                            if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else currentCellValue.toString()
                        } else {
                            currentCellValue.toString()
                        }
                        currentRowMap[currentCellColIndex] = text.trim()
                        currentColIndex = currentCellColIndex + 1
                    } else if (name.equals("row", ignoreCase = true)) {
                        if (currentRowMap.isNotEmpty()) {
                            rawRows.add(currentRowMap)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return convertRawRowsToProductRows(rawRows)
    }

    // --- CSV Parser ---
    private fun parseCsv(stream: InputStream): List<ProductExcelRow> {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val lines = reader.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        // Strip UTF-8 BOM if present on first line
        val firstLine = lines[0].removePrefix("\uFEFF")
        val delimiter = detectDelimiter(firstLine)

        val rawRows = lines.mapIndexed { idx, line ->
            val cleanLine = if (idx == 0) firstLine else line
            parseCsvLine(cleanLine, delimiter)
        }

        val mapRows = rawRows.map { tokens ->
            tokens.mapIndexed { colIdx, text -> colIdx to text }.toMap()
        }

        return convertRawRowsToProductRows(mapRows)
    }

    private fun detectDelimiter(header: String): Char {
        val commaCount = header.count { it == ',' }
        val semiCount = header.count { it == ';' }
        val tabCount = header.count { it == '\t' }
        return when {
            tabCount > commaCount && tabCount > semiCount -> '\t'
            semiCount > commaCount -> ';'
            else -> ','
        }
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++ // skip escaped quote
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    // --- Column mapping & Row conversion ---
    private fun convertRawRowsToProductRows(rawRows: List<Map<Int, String>>): List<ProductExcelRow> {
        if (rawRows.isEmpty()) return emptyList()

        val headerRow = rawRows.first()

        // Default standard positional mapping matching user's exact specification:
        // Column A (0): كود بار (Barcode)
        // Column B (1): اسم المنتج (Product Name)
        // Column C (2): عائلة المنتج (Product Family / Category)
        // Column D (3): الوحدة (Unit)
        // Column E (4): سعر الشراء (Purchase / Cost Price)
        // Column F (5): ثمن البيع (Sale Price)
        var colBarcode = 0
        var colName = 1
        var colFamily = 2
        var colUnit = 3
        var colCost = 4
        var colPrice = 5

        var isFirstRowHeader = false

        // Check if row 0 contains text headers
        val row0Values = headerRow.values.map { it.lowercase(Locale.ROOT).trim() }
        val headerKeywords = listOf(
            "كود", "بار", "barcode", "code", "ean", "sku",
            "اسم", "منتج", "produit", "name", "article", "designation", "désignation", "libelle",
            "عائل", "عائلة", "فئة", "تصنيف", "قسم", "famille", "catégorie", "categorie", "category",
            "وحد", "وحدة", "unite", "unité", "unit",
            "شراء", "تكلف", "achat", "cost", "cout", "coût",
            "بيع", "ثمن", "سعر", "vente", "prix", "price"
        )
        val hasHeaders = row0Values.any { text -> headerKeywords.any { kw -> text.contains(kw) } }

        if (hasHeaders) {
            isFirstRowHeader = true

            val assignedIndices = mutableSetOf<Int>()
            val detected = mutableMapOf<String, Int>()

            // PASS 1: Identify specific columns that have unique keywords
            // 1. FAMILY / CATEGORY:
            // CRITICAL: MUST be checked BEFORE Product Name because "عائلة المنتج" contains the substring "منتج"!
            for ((idx, raw) in headerRow) {
                val t = raw.lowercase(Locale.ROOT).trim()
                if (t.contains("عائل") || t.contains("فئة") || t.contains("تصنيف") || 
                    t.contains("قسم") || t.contains("famille") || t.contains("catégorie") || 
                    t.contains("categorie") || t.contains("category") || t.contains("groupe")) {
                    detected["FAMILY"] = idx
                    assignedIndices.add(idx)
                    break
                }
            }

            // 2. BARCODE:
            for ((idx, raw) in headerRow) {
                if (idx in assignedIndices) continue
                val t = raw.lowercase(Locale.ROOT).trim()
                if (t.contains("كود") || t.contains("بار") || t.contains("barcode") || 
                    t.contains("code-barre") || t.contains("code barre") || t.contains("ean") || 
                    t.contains("sku") || (t.contains("code") && !t.contains("postal"))) {
                    detected["BARCODE"] = idx
                    assignedIndices.add(idx)
                    break
                }
            }

            // 3. PURCHASE / COST PRICE:
            for ((idx, raw) in headerRow) {
                if (idx in assignedIndices) continue
                val t = raw.lowercase(Locale.ROOT).trim()
                if (t.contains("شراء") || t.contains("تكلف") || t.contains("achat") || 
                    t.contains("cost") || t.contains("cout") || t.contains("coût") || t.contains("fournisseur")) {
                    detected["COST"] = idx
                    assignedIndices.add(idx)
                    break
                }
            }

            // 4. UNIT:
            for ((idx, raw) in headerRow) {
                if (idx in assignedIndices) continue
                val t = raw.lowercase(Locale.ROOT).trim()
                if (t.contains("وحد") || t.contains("unite") || t.contains("unité") || 
                    t.contains("unit") || t.contains("mesure") || t.contains("cond")) {
                    detected["UNIT"] = idx
                    assignedIndices.add(idx)
                    break
                }
            }

            // 5. SALE PRICE:
            for ((idx, raw) in headerRow) {
                if (idx in assignedIndices) continue
                val t = raw.lowercase(Locale.ROOT).trim()
                if (t.contains("بيع") || t.contains("vente") || t.contains("public") || 
                    t.contains("sell") || (t.contains("prix") && !t.contains("achat")) || 
                    (t.contains("price") && !t.contains("cost"))) {
                    detected["PRICE"] = idx
                    assignedIndices.add(idx)
                    break
                }
            }

            // PASS 2: Identify PRODUCT NAME from remaining unassigned columns
            for ((idx, raw) in headerRow) {
                if (idx in assignedIndices) continue
                val t = raw.lowercase(Locale.ROOT).trim()
                if (t.contains("اسم") || t.contains("منتج") || t.contains("produit") || 
                    t.contains("designation") || t.contains("désignation") || t.contains("name") || 
                    t.contains("article") || t.contains("libelle") || t.contains("libellé") || 
                    t.contains("سلع") || t.contains("ماد") || t.contains("item")) {
                    detected["NAME"] = idx
                    assignedIndices.add(idx)
                    break
                }
            }

            // If PRODUCT NAME is still unassigned, pick the first unassigned column or fallback to column 1
            if (detected["NAME"] == null) {
                val candidate = headerRow.keys.firstOrNull { it !in assignedIndices } ?: 1
                detected["NAME"] = candidate
                assignedIndices.add(candidate)
            }

            // Apply all detected column indices
            detected["BARCODE"]?.let { colBarcode = it }
            detected["NAME"]?.let { colName = it }
            detected["FAMILY"]?.let { colFamily = it }
            detected["UNIT"]?.let { colUnit = it }
            detected["COST"]?.let { colCost = it }
            detected["PRICE"]?.let { colPrice = it }

            // ABSOLUTE INTEGRITY GUARD:
            // Ensure colName and colFamily NEVER point to the same index under any circumstance!
            if (colName == colFamily) {
                if (colFamily == 2) {
                    colName = 1
                } else if (colName == 1) {
                    colFamily = 2
                } else {
                    colName = 1
                    colFamily = 2
                }
            }
        }

        val dataRows = if (isFirstRowHeader) rawRows.drop(1) else rawRows
        val result = mutableListOf<ProductExcelRow>()

        for (row in dataRows) {
            val name = row[colName]?.trim() ?: ""
            if (name.isBlank()) continue // Skip empty rows

            val barcode = row[colBarcode]?.trim() ?: ""
            val family = row[colFamily]?.trim() ?: ""
            val unit = row[colUnit]?.trim().let { if (it.isNullOrBlank()) "قطعة / Unité" else it }

            val rawCost = row[colCost]?.replace(",", ".")?.replace(Regex("[^0-9.]"), "") ?: "0"
            val cost = rawCost.toDoubleOrNull() ?: 0.0

            val rawPrice = row[colPrice]?.replace(",", ".")?.replace(Regex("[^0-9.]"), "") ?: "0"
            val price = rawPrice.toDoubleOrNull() ?: 0.0

            result.add(
                ProductExcelRow(
                    barcode = barcode,
                    name = name,
                    familyName = family,
                    unit = unit,
                    purchasePrice = cost,
                    sellPrice = price
                )
            )
        }

        return result
    }

    private fun columnLetterToIndex(cellRef: String): Int {
        var index = 0
        for (char in cellRef.uppercase(Locale.ROOT)) {
            if (char in 'A'..'Z') {
                index = index * 26 + (char - 'A' + 1)
            } else {
                break
            }
        }
        return (index - 1).coerceAtLeast(0)
    }

    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeCsv(str: String): String {
        val containsSpecial = str.contains(',') || str.contains('"') || str.contains('\n') || str.contains(';')
        return if (containsSpecial) {
            "\"" + str.replace("\"", "\"\"") + "\""
        } else {
            str
        }
    }

    // =========================================================================
    // 4. SHARING AND OPENING INTENT
    // =========================================================================
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
