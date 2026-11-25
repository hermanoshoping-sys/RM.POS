package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.printer.BluetoothPrinterService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object PdfPrintAndShareUtils {
    private const val TAG = "PdfPrintAndShareUtils"

    private val dateFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
    }

    private fun showToastSafe(context: Context, message: String) {
        val appCtx = context.applicationContext ?: context
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                Toast.makeText(appCtx, message, Toast.LENGTH_SHORT).show()
            } catch (toastErr: Throwable) {
                Log.w(TAG, "Unable to show main-thread Toast: $message", toastErr)
            }
        } else {
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(appCtx, message, Toast.LENGTH_SHORT).show()
                } catch (toastErr: Throwable) {
                    Log.w(TAG, "Unable to show posted Toast: $message", toastErr)
                }
            }
        }
    }

    fun formatTimestamp(timestampMs: Long = System.currentTimeMillis()): String =
        (dateFormatter.get() ?: SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)).format(Date(timestampMs))

    fun createCacheFile(context: Context, fileName: String): File {
        val cacheDir = File(context.cacheDir, "shared_files")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return File(cacheDir, fileName)
    }

    fun writePdfDocument(pdfDoc: PdfDocument, file: File) {
        try {
            FileOutputStream(file).use { out ->
                pdfDoc.writeTo(out)
                out.flush()
            }
        } finally {
            try {
                pdfDoc.close()
            } catch (closeErr: Throwable) {
                Log.w(TAG, "Error closing PdfDocument", closeErr)
            }
        }
    }

    fun printPdfFile(
        context: Context,
        file: File,
        jobTitle: String,
        isLandscape: Boolean = false,
        fallbackText: String = ""
    ) {
        val safeJobTitle = jobTitle.ifBlank { "RM_POS_Document" }
        try {
            if (!file.exists() || file.length() <= 0L) {
                showToastSafe(context, "تعذر تجهيز ملف الطباعة PDF")
                return
            }
            val activity = BluetoothPrinterService.resolvePrintActivity(context)
            if (activity == null || !com.example.printer.InAppPrintPreviewManager.isSystemPrintAvailable(activity)) {
                Log.i(TAG, "Opening built-in print preview window for $safeJobTitle (system print unavailable or non-Activity context)")
                com.example.printer.InAppPrintPreviewManager.openPdfPrintWindow(
                    context = context,
                    file = file,
                    jobTitle = safeJobTitle,
                    isLandscape = isLandscape,
                    fallbackText = fallbackText
                )
                return
            }

            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                com.example.printer.InAppPrintPreviewManager.openPdfPrintWindow(
                    context = context,
                    file = file,
                    jobTitle = safeJobTitle,
                    isLandscape = isLandscape,
                    fallbackText = fallbackText
                )
                return
            }

            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    try {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }
                        val pfd = PrintDocumentInfo.Builder(file.name.ifBlank { "document.pdf" })
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .build()
                        callback?.onLayoutFinished(pfd, true)
                    } catch (t: Throwable) {
                        Log.e(TAG, "PDF onLayout error", t)
                        try {
                            callback?.onLayoutFailed(t.localizedMessage ?: "Layout error")
                        } catch (cbErr: Throwable) {
                            Log.w(TAG, "onLayoutFailed callback threw", cbErr)
                        }
                    }
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                            return
                        }
                        val fd = destination?.fileDescriptor
                        if (fd == null) {
                            callback?.onWriteFailed("Missing destination file descriptor")
                            return
                        }
                        FileInputStream(file).use { input ->
                            FileOutputStream(fd).use { output ->
                                input.copyTo(output)
                                output.flush()
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (t: Throwable) {
                        Log.e(TAG, "PDF onWrite error", t)
                        try {
                            callback?.onWriteFailed(t.localizedMessage ?: "Write error")
                        } catch (cbErr: Throwable) {
                            Log.w(TAG, "onWriteFailed callback threw", cbErr)
                        }
                    }
                }
            }

            val mediaSize = if (isLandscape) {
                PrintAttributes.MediaSize.ISO_A4.asLandscape()
            } else {
                PrintAttributes.MediaSize.ISO_A4.asPortrait()
            }

            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            val printJob = printManager.print(safeJobTitle, printAdapter, printAttributes)
            @Suppress("SENSELESS_COMPARISON")
            if (printJob == null) {
                Log.w(TAG, "PrintManager.print returned null job; opening built-in print window")
                com.example.printer.InAppPrintPreviewManager.openPdfPrintWindow(
                    context = context,
                    file = file,
                    jobTitle = safeJobTitle,
                    isLandscape = isLandscape,
                    fallbackText = fallbackText
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "printPdfFile falling back to built-in print preview", t)
            if (file.exists() && file.length() > 0L) {
                com.example.printer.InAppPrintPreviewManager.openPdfPrintWindow(
                    context = context,
                    file = file,
                    jobTitle = safeJobTitle,
                    isLandscape = isLandscape,
                    fallbackText = fallbackText
                )
            } else {
                showToastSafe(context, "خطأ في تشغيل الطباعة: ${t.localizedMessage ?: "خطأ غير متوقع"}")
            }
        }
    }

    fun sharePdfFile(context: Context, file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                if (BluetoothPrinterService.findActivity(context) == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        } catch (t: Throwable) {
            Log.e(TAG, "sharePdfFile failed", t)
            showToastSafe(context, "خطأ في مشاركة الملف: ${t.localizedMessage ?: "خطأ"}")
        }
    }

    fun shareImageFile(context: Context, file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                if (BluetoothPrinterService.findActivity(context) == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        } catch (t: Throwable) {
            Log.e(TAG, "shareImageFile failed", t)
            showToastSafe(context, "خطأ في مشاركة الصورة: ${t.localizedMessage ?: "خطأ"}")
        }
    }
}
