package com.example.printer

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.ui.screens.PdfPrintAndShareUtils
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes Android System PrintManager jobs backed by hardened WebView or generated PDF,
 * falling back cleanly to InAppPrintPreviewManager when system spooling is unavailable.
 */
internal object WebViewSystemPrintExecutor {
    private const val TAG = "WebViewPrintExec"

    private val activePrintWebViews: MutableSet<WebView> =
        Collections.synchronizedSet(mutableSetOf())

    private fun releasePrintWebView(webView: WebView?) {
        if (webView == null) return
        activePrintWebViews.remove(webView)
        Handler(Looper.getMainLooper()).post {
            try {
                webView.stopLoading()
                webView.destroy()
            } catch (t: Throwable) {
                Log.w(TAG, "Non-fatal error releasing print WebView", t)
            }
        }
    }

    fun printReceiptSystem(
        context: Context,
        jobTitle: String,
        textToPrint: String
    ) {
        val appCtx = context.applicationContext ?: context
        val safeJobTitle = jobTitle.ifBlank { "RM_POS_Print" }
        val executeOnMain = Runnable {
            val trimmed = textToPrint.trimStart()
            val isRawHtml = trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true)

            if (!isRawHtml) {
                val generatedPdf = InAppPrintPreviewManager.generatePlainTextReceiptPdf(
                    context = appCtx,
                    jobTitle = safeJobTitle,
                    plainText = textToPrint
                )
                if (generatedPdf != null) {
                    PdfPrintAndShareUtils.printPdfFile(
                        context = context,
                        file = generatedPdf,
                        jobTitle = safeJobTitle,
                        isLandscape = false,
                        fallbackText = textToPrint
                    )
                    return@Runnable
                }
            }

            val activity = BluetoothPrinterService.resolvePrintActivity(context)
            if (activity == null || !InAppPrintPreviewManager.isSystemPrintAvailable(activity)) {
                Log.i(TAG, "printReceiptSystem opening built-in print preview window")
                InAppPrintPreviewManager.openTextOrHtmlPrintWindow(appCtx, safeJobTitle, textToPrint)
                return@Runnable
            }

            var createdWebView: WebView? = null
            try {
                val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager == null) {
                    InAppPrintPreviewManager.openTextOrHtmlPrintWindow(appCtx, safeJobTitle, textToPrint)
                    return@Runnable
                }

                val webView = WebView(activity)
                createdWebView = webView
                activePrintWebViews.add(webView)
                webView.settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                }
                val htmlContent = if (isRawHtml) {
                    textToPrint
                } else {
                    val docFontSize = com.example.util.DocumentDesignHelper.resolveSettings(appCtx).documentFontSize
                    HtmlInvoiceAndReceiptFormatter.buildHtmlReceipt(safeJobTitle, textToPrint, docFontSize)
                }

                val printStarted = AtomicBoolean(false)
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (!printStarted.compareAndSet(false, true)) return
                        try {
                            if (activity.isFinishing || activity.isDestroyed) {
                                releasePrintWebView(webView)
                                InAppPrintPreviewManager.openTextOrHtmlPrintWindow(appCtx, safeJobTitle, textToPrint)
                                return
                            }
                            val delegateAdapter = webView.createPrintDocumentAdapter(safeJobTitle)
                            val lifecycleSafeAdapter = object : PrintDocumentAdapter() {
                                override fun onStart() {
                                    try {
                                        delegateAdapter.onStart()
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "PrintDocumentAdapter.onStart failed", t)
                                    }
                                }

                                override fun onLayout(
                                    oldAttributes: PrintAttributes?,
                                    newAttributes: PrintAttributes?,
                                    cancellationSignal: CancellationSignal?,
                                    callback: LayoutResultCallback?,
                                    extras: Bundle?
                                ) {
                                    try {
                                        delegateAdapter.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "PrintDocumentAdapter.onLayout failed", t)
                                        try {
                                            callback?.onLayoutFailed(t.localizedMessage)
                                        } catch (cbErr: Throwable) {
                                            Log.w(TAG, "Failed reporting layout error to print callback", cbErr)
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
                                        delegateAdapter.onWrite(pages, destination, cancellationSignal, callback)
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "PrintDocumentAdapter.onWrite failed", t)
                                        try {
                                            callback?.onWriteFailed(t.localizedMessage)
                                        } catch (cbErr: Throwable) {
                                            Log.w(TAG, "Failed reporting write error to print callback", cbErr)
                                        }
                                    }
                                }

                                override fun onFinish() {
                                    try {
                                        delegateAdapter.onFinish()
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "PrintDocumentAdapter.onFinish failed", t)
                                    } finally {
                                        releasePrintWebView(webView)
                                    }
                                }
                            }
                            @Suppress("SENSELESS_COMPARISON")
                            val printJob = printManager.print(
                                safeJobTitle,
                                lifecycleSafeAdapter,
                                PrintAttributes.Builder()
                                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asPortrait())
                                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                    .build()
                            )
                            @Suppress("SENSELESS_COMPARISON")
                            if (printJob == null) {
                                releasePrintWebView(webView)
                                InAppPrintPreviewManager.openTextOrHtmlPrintWindow(appCtx, safeJobTitle, textToPrint)
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Async error launching system PrintManager from WebView; opening built-in preview", t)
                            releasePrintWebView(webView)
                            InAppPrintPreviewManager.openTextOrHtmlPrintWindow(appCtx, safeJobTitle, textToPrint)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true && printStarted.compareAndSet(false, true)) {
                            Log.w(TAG, "WebView main-frame error while rendering print HTML")
                            releasePrintWebView(webView)
                            InAppPrintPreviewManager.openTextOrHtmlPrintWindow(appCtx, safeJobTitle, textToPrint)
                        }
                    }

                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                        Log.e(TAG, "Print WebView render process exited unexpectedly")
                        releasePrintWebView(webView)
                        return true
                    }
                }

                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in printReceiptSystem; opening built-in preview", t)
                releasePrintWebView(createdWebView)
                InAppPrintPreviewManager.openTextOrHtmlPrintWindow(appCtx, safeJobTitle, textToPrint)
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            executeOnMain.run()
        } else {
            Handler(Looper.getMainLooper()).post(executeOnMain)
        }
    }
}
