package com.example.printer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PrintManager
import android.provider.MediaStore
import android.text.Html
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

data class InAppPrintRequest(
    val id: Long,
    val jobTitle: String,
    val pdfFile: File?,
    val plainTextPreview: String,
    val isLandscape: Boolean = false
)

/**
 * Manages the built-in Print Preview & PDF Save/Share window when Android's native
 * PrintSpooler is unavailable, disabled, or cannot be launched from the current context.
 */
object InAppPrintPreviewManager {
    private const val TAG = "InAppPrintPreviewMgr"
    private val nextRequestId = AtomicLong(1L)

    private val _activeRequest = MutableStateFlow<InAppPrintRequest?>(null)
    val activeRequest: StateFlow<InAppPrintRequest?> = _activeRequest.asStateFlow()

    fun dismiss() {
        _activeRequest.value = null
    }

    internal fun resetForTesting() {
        _activeRequest.value = null
    }

    fun isSystemPrintAvailable(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            if (!pm.hasSystemFeature(PackageManager.FEATURE_PRINTING)) {
                return false
            }
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            printManager != null
        } catch (t: Throwable) {
            Log.w(TAG, "System print availability check failed", t)
            false
        }
    }

    fun openPdfPrintWindow(
        context: Context,
        file: File,
        jobTitle: String,
        isLandscape: Boolean = false,
        fallbackText: String = ""
    ) {
        val safeTitle = jobTitle.ifBlank { "RM_POS_Document" }
        val previewText = fallbackText.ifBlank {
            "$safeTitle\n\n(${file.name})"
        }
        _activeRequest.value = InAppPrintRequest(
            id = nextRequestId.getAndIncrement(),
            jobTitle = safeTitle,
            pdfFile = file.takeIf { it.exists() && it.length() > 0L },
            plainTextPreview = previewText,
            isLandscape = isLandscape
        )
    }

    fun openTextOrHtmlPrintWindow(
        context: Context,
        jobTitle: String,
        rawTextOrHtml: String
    ) {
        val safeTitle = jobTitle.ifBlank { "RM_POS_Print" }
        val readableText = extractReadableText(rawTextOrHtml).ifBlank { safeTitle }
        val generatedPdf = generatePlainTextReceiptPdf(context, safeTitle, readableText)
        _activeRequest.value = InAppPrintRequest(
            id = nextRequestId.getAndIncrement(),
            jobTitle = safeTitle,
            pdfFile = generatedPdf,
            plainTextPreview = readableText,
            isLandscape = false
        )
    }

    fun extractReadableText(rawTextOrHtml: String): String {
        val trimmed = rawTextOrHtml.trim()
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
        ) {
            return try {
                val withoutStyle = trimmed.replace(
                    Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE),
                    ""
                )
                Html.fromHtml(withoutStyle, Html.FROM_HTML_MODE_LEGACY)
                    .toString()
                    .lines()
                    .map { it.trimEnd() }
                    .chunked(1)
                    .flatten()
                    .joinToString("\n")
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()
            } catch (_: Throwable) {
                trimmed
            }
        }
        return rawTextOrHtml
    }

    fun generatePlainTextReceiptPdf(
        context: Context,
        jobTitle: String,
        plainText: String
    ): File? {
        return try {
            val cacheDir = File(context.cacheDir, "shared_files")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val sanitized = sanitizeFileName(jobTitle)
            val outFile = File(cacheDir, "${sanitized}_${System.currentTimeMillis()}.pdf")

            val pageWidth = 595
            val pageHeight = 842
            val marginH = 28.35f // 1 cm left & right
            val topMargin = 14.17f // 0.5 cm top
            val bottomMargin = 28.35f // 1 cm bottom
            val docSettings = com.example.util.DocumentDesignHelper.resolveSettings(context)
            val fontScale = com.example.util.DocumentDesignHelper.fontScale(docSettings)
            val lineHeight = (18f * fontScale).coerceIn(13f, 28f)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = (15f * fontScale).coerceIn(11f, 24f)
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = docSettings.documentFontSize.coerceIn(8, 20).toFloat()
                typeface = Typeface.MONOSPACE
                textAlign = Paint.Align.RIGHT
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(203, 213, 225)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            val wrappedLines = wrapLines(plainText, bodyPaint, pageWidth - (marginH * 2f))
            val titleBlockHeight = (-titlePaint.ascent()) + 14f + 22f
            val firstLineOffset = -bodyPaint.ascent()
            val linesPerFirstPage = ((pageHeight - topMargin - bottomMargin - titleBlockHeight) / lineHeight).toInt().coerceAtLeast(10)
            val linesPerNextPage = ((pageHeight - topMargin - bottomMargin - firstLineOffset) / lineHeight).toInt().coerceAtLeast(10)

            val pdfDoc = PdfDocument()
            try {
                var index = 0
                var pageNum = 1
                while (index < wrappedLines.size || pageNum == 1) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    val page = pdfDoc.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    var y = topMargin
                    if (pageNum == 1) {
                        y += -titlePaint.ascent()
                        canvas.drawText(jobTitle, pageWidth / 2f, y, titlePaint)
                        y += 14f
                        canvas.drawLine(marginH, y, pageWidth - marginH, y, borderPaint)
                        y += 22f
                    } else {
                        y += firstLineOffset
                    }

                    val limit = if (pageNum == 1) linesPerFirstPage else linesPerNextPage
                    var drawn = 0
                    while (index < wrappedLines.size && drawn < limit) {
                        canvas.drawText(wrappedLines[index], pageWidth - marginH, y, bodyPaint)
                        y += lineHeight
                        index++
                        drawn++
                    }
                    pdfDoc.finishPage(page)
                    pageNum++
                    if (index >= wrappedLines.size) break
                }

                FileOutputStream(outFile).use { out ->
                    pdfDoc.writeTo(out)
                    out.flush()
                }
            } finally {
                try {
                    pdfDoc.close()
                } catch (t: Throwable) {
                    Log.w(TAG, "Error closing PdfDocument in plain text generator", t)
                }
            }
            outFile.takeIf { it.exists() && it.length() > 0L }
        } catch (t: Throwable) {
            Log.w(TAG, "Plain text PDF generation unavailable in current runtime", t)
            null
        }
    }

    private fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        for (rawLine in text.replace("\r\n", "\n").split('\n')) {
            if (rawLine.isEmpty()) {
                result.add("")
                continue
            }
            if (paint.measureText(rawLine) <= maxWidth) {
                result.add(rawLine)
                continue
            }
            var start = 0
            while (start < rawLine.length) {
                val count = paint.breakText(rawLine, start, rawLine.length, true, maxWidth, null)
                    .coerceAtLeast(1)
                result.add(rawLine.substring(start, start + count))
                start += count
            }
        }
        return result
    }

    fun savePdfToDownloads(
        context: Context,
        sourceFile: File,
        jobTitle: String,
        copies: Int = 1
    ): Result<String> {
        return runCatching {
            require(sourceFile.exists() && sourceFile.length() > 0L) {
                "ملف PDF غير متاح للحفظ"
            }
            val baseName = sanitizeFileName(jobTitle)
            val suffix = if (copies > 1) "_x$copies" else ""
            val fileName = "${baseName}${suffix}_${System.currentTimeMillis()}.pdf"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RM_POS")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(output)
                            output.flush()
                        }
                    } ?: error("تعذر فتح مجرى الحفظ في التنزيلات")
                    val doneValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, doneValues, null, null)
                    return@runCatching "Downloads/RM_POS/$fileName"
                }
            }

            val fallbackRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            val targetDir = File(fallbackRoot, "RM_POS")
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, fileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            targetFile.absolutePath
        }
    }

    fun openPdfInExternalApp(context: Context, file: File, jobTitle: String): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(viewIntent, jobTitle).apply {
                if (BluetoothPrinterService.findActivity(context) == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "openPdfInExternalApp failed", t)
            false
        }
    }

    fun sharePdfToPrinterOrApps(context: Context, file: File, jobTitle: String): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, jobTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, jobTitle).apply {
                if (BluetoothPrinterService.findActivity(context) == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "sharePdfToPrinterOrApps failed", t)
            false
        }
    }

    fun renderPdfPreviewPages(pdf: File?, maxPages: Int = 10): Pair<Int, List<Bitmap>> {
        if (pdf == null || !pdf.exists() || pdf.length() <= 0L) {
            return 0 to emptyList()
        }
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val outBitmaps = mutableListOf<Bitmap>()
        return try {
            pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            val limit = minOf(count, maxPages)
            for (idx in 0 until limit) {
                renderer.openPage(idx).use { page ->
                    val scale = (1100f / maxOf(page.width, 1)).coerceIn(1.2f, 2.5f)
                    val bmpW = maxOf(1, (page.width * scale).toInt())
                    val bmpH = maxOf(1, (page.height * scale).toInt())
                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    outBitmaps.add(bitmap)
                }
            }
            count to outBitmaps
        } catch (_: Throwable) {
            outBitmaps.forEach { runCatching { it.recycle() } }
            0 to emptyList()
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .trim('_')
        return cleaned.ifBlank { "RM_POS_Print" }.take(60)
    }
}
