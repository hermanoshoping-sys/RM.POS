package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.GlobalSettingsEntity
import com.example.ui.viewmodels.SettingsPrefsPersistence
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class StyledDocumentBitmap(
    val bitmap: Bitmap,
    val drawWidthPt: Float,
    val drawHeightPt: Float
)

/**
 * Centralized helper for document visual customization:
 * - Logo dimensions & quality control
 * - Header & Footer content modes (TEXT / IMAGE / BOTH / NONE) and dimensions
 * - Document font size scaling across all PDF, HTML, and Compose invoice renderers
 */
object DocumentDesignHelper {
    private const val TAG = "DocumentDesignHelper"

    const val LOGO_FILE_NAME = "company_logo.png"
    const val HEADER_IMAGE_FILE_NAME = "header_image.png"
    const val FOOTER_IMAGE_FILE_NAME = "footer_image.png"

    const val MODE_TEXT = "TEXT"
    const val MODE_IMAGE = "IMAGE"
    const val MODE_BOTH = "BOTH"
    const val MODE_NONE = "NONE"

    fun resolveSettings(
        context: Context,
        explicitSettings: GlobalSettingsEntity? = null
    ): GlobalSettingsEntity {
        if (explicitSettings != null) return explicitSettings
        return try {
            val prefs = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
            SettingsPrefsPersistence.loadFromPrefs(prefs) ?: GlobalSettingsEntity()
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to read persisted document design settings", t)
            GlobalSettingsEntity()
        }
    }

    fun fontScale(settings: GlobalSettingsEntity): Float =
        settings.documentFontSize.coerceIn(8, 20) / 12f

    fun scaledFont(basePt: Float, settings: GlobalSettingsEntity, minPt: Float = 7f, maxPt: Float = 32f): Float =
        (basePt * fontScale(settings)).coerceIn(minPt, maxPt)

    fun normalizeContentType(raw: String?): String = when (raw?.trim()?.uppercase(Locale.ROOT)) {
        MODE_IMAGE -> MODE_IMAGE
        MODE_BOTH -> MODE_BOTH
        MODE_NONE -> MODE_NONE
        else -> MODE_TEXT
    }

    fun headerShowsText(settings: GlobalSettingsEntity): Boolean {
        val mode = normalizeContentType(settings.headerContentType)
        return (mode == MODE_TEXT || mode == MODE_BOTH) && settings.receiptHeader.isNotBlank()
    }

    fun headerShowsImage(settings: GlobalSettingsEntity): Boolean {
        val mode = normalizeContentType(settings.headerContentType)
        return mode == MODE_IMAGE || mode == MODE_BOTH
    }

    fun footerShowsText(settings: GlobalSettingsEntity): Boolean {
        if (!settings.showReceiptFooter) return false
        val mode = normalizeContentType(settings.footerContentType)
        return mode == MODE_TEXT || mode == MODE_BOTH
    }

    fun footerShowsImage(settings: GlobalSettingsEntity): Boolean {
        if (!settings.showReceiptFooter) return false
        val mode = normalizeContentType(settings.footerContentType)
        return mode == MODE_IMAGE || mode == MODE_BOTH
    }

    fun savePickedImage(
        context: Context,
        uri: Uri,
        fileName: String,
        quality: Int = 95
    ): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 1800 || bounds.outHeight / sampleSize > 1800) {
                sampleSize *= 2
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sampleSize }
                )
            } ?: return null

            val targetFile = File(context.filesDir, fileName)
            try {
                FileOutputStream(targetFile).use { out ->
                    decoded.compress(Bitmap.CompressFormat.PNG, quality.coerceIn(50, 100), out)
                    out.flush()
                }
            } finally {
                decoded.recycle()
            }
            Uri.fromFile(targetFile).toString()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed saving picked image to $fileName", t)
            null
        }
    }

    fun deleteInternalImage(context: Context, fileName: String) {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) file.delete()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed deleting internal image $fileName", t)
        }
    }

    fun decodeUiBitmap(context: Context, uriString: String?, fallbackFileName: String): Bitmap? {
        return try {
            val file = resolveImageFile(context, uriString, fallbackFileName)
            if (file != null && file.exists()) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
                    sample *= 2
                }
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            } else if (!uriString.isNullOrBlank()) {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to decode UI bitmap for $fallbackFileName", t)
            null
        }
    }

    fun loadLogoForDocument(
        context: Context,
        settings: GlobalSettingsEntity,
        maxPageWidthPt: Float = 538.3f
    ): StyledDocumentBitmap? {
        if (!settings.showLogo) return null
        val prefs = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("company_logo_uri", null)
        val targetW = settings.logoWidth.coerceIn(40, 320).toFloat().coerceAtMost(maxPageWidthPt)
        val targetH = settings.logoHeight.coerceIn(30, 220).toFloat()
        return loadScaledDocumentBitmap(
            context = context,
            uriString = uriStr,
            fallbackFileName = LOGO_FILE_NAME,
            maxWidthPt = targetW,
            maxHeightPt = targetH,
            qualityPercent = settings.logoQuality.coerceIn(40, 100)
        )
    }

    fun loadHeaderImageForDocument(
        context: Context,
        settings: GlobalSettingsEntity,
        maxPageWidthPt: Float = 538.3f
    ): StyledDocumentBitmap? {
        if (!headerShowsImage(settings)) return null
        val prefs = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("header_image_uri", null)
        val targetW = settings.headerWidth.coerceIn(80, maxPageWidthPt.toInt()).toFloat()
        val targetH = settings.headerHeight.coerceIn(24, 220).toFloat()
        return loadScaledDocumentBitmap(
            context = context,
            uriString = uriStr,
            fallbackFileName = HEADER_IMAGE_FILE_NAME,
            maxWidthPt = targetW,
            maxHeightPt = targetH,
            qualityPercent = settings.logoQuality.coerceIn(40, 100)
        )
    }

    fun loadFooterImageForDocument(
        context: Context,
        settings: GlobalSettingsEntity,
        maxPageWidthPt: Float = 538.3f
    ): StyledDocumentBitmap? {
        if (!footerShowsImage(settings)) return null
        val prefs = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("footer_image_uri", null)
        val targetW = settings.footerWidth.coerceIn(80, maxPageWidthPt.toInt()).toFloat()
        val targetH = settings.footerHeight.coerceIn(24, 220).toFloat()
        return loadScaledDocumentBitmap(
            context = context,
            uriString = uriStr,
            fallbackFileName = FOOTER_IMAGE_FILE_NAME,
            maxWidthPt = targetW,
            maxHeightPt = targetH,
            qualityPercent = settings.logoQuality.coerceIn(40, 100)
        )
    }

    fun loadEncodedImageHtml(
        context: Context?,
        fileName: String,
        prefKey: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
        qualityPercent: Int,
        alt: String
    ): String {
        if (context == null) return ""
        return try {
            val prefs = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
            val uriStr = prefs.getString(prefKey, null)
            val styled = loadScaledDocumentBitmap(
                context = context,
                uriString = uriStr,
                fallbackFileName = fileName,
                maxWidthPt = maxWidthPx.toFloat(),
                maxHeightPt = maxHeightPx.toFloat(),
                qualityPercent = qualityPercent.coerceIn(40, 100)
            ) ?: return ""

            val q = qualityPercent.coerceIn(40, 100)
            val usePng = q >= 95 || styled.bitmap.hasAlpha()
            val mimeType = if (usePng) "image/png" else "image/jpeg"
            val encodedBytes = try {
                val out = ByteArrayOutputStream()
                if (usePng) {
                    styled.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    styled.bitmap.compress(Bitmap.CompressFormat.JPEG, q, out)
                }
                out.toByteArray()
            } finally {
                styled.bitmap.recycle()
            }

            if (encodedBytes.isNotEmpty() && encodedBytes.size <= 768_000) {
                val b64 = Base64.encodeToString(encodedBytes, Base64.NO_WRAP)
                val wPx = styled.drawWidthPt.toInt().coerceAtLeast(24)
                val hPx = styled.drawHeightPt.toInt().coerceAtLeast(20)
                """<div style="text-align: center; margin: 4px 0;"><img src="data:$mimeType;base64,$b64" style="width: ${wPx}px; height: ${hPx}px; max-width: 100%; object-fit: contain;" alt="$alt" /></div>"""
            } else {
                ""
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to encode $fileName for HTML document", t)
            ""
        }
    }

    fun drawStyledBitmapCentered(
        canvas: Canvas,
        centerX: Float,
        topY: Float,
        styled: StyledDocumentBitmap
    ): Float {
        val paintImg = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        val left = centerX - (styled.drawWidthPt / 2f)
        val dst = RectF(left, topY, left + styled.drawWidthPt, topY + styled.drawHeightPt)
        canvas.drawBitmap(styled.bitmap, null, dst, paintImg)
        return topY + styled.drawHeightPt
    }

    /**
     * Draws custom header image (if any), company logo, and custom header text below the logo,
     * returning the updated Y coordinate below the rendered header branding.
     */
    fun drawHeaderBranding(
        canvas: Canvas,
        centerX: Float,
        startY: Float,
        settings: GlobalSettingsEntity,
        headerStyled: StyledDocumentBitmap?,
        logoStyled: StyledDocumentBitmap?,
        textColor: Int = android.graphics.Color.rgb(30, 41, 59)
    ): Float {
        var y = startY
        if (headerStyled != null) {
            y = drawStyledBitmapCentered(canvas, centerX, y, headerStyled) + 6f
        }
        if (settings.showLogo && logoStyled != null) {
            y = drawStyledBitmapCentered(canvas, centerX, y, logoStyled) + 8f
        }
        if (headerShowsText(settings)) {
            val textStartY = y
            val pHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                isFakeBoldText = true
                textSize = settings.headerFontSize.coerceIn(9, 26).toFloat()
                textAlign = Paint.Align.CENTER
            }
            val lines = settings.receiptHeader.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            val lineStep = (-pHeader.ascent() + pHeader.descent() + 4f)
            for (line in lines) {
                y += -pHeader.ascent()
                canvas.drawText(line, centerX, y, pHeader)
                y += pHeader.descent() + 4f
            }
            val minBottom = textStartY + settings.headerHeight.coerceIn(24, 220).toFloat()
            if (headerStyled == null && y < minBottom && lines.isNotEmpty()) {
                y = minBottom.coerceAtMost(textStartY + (lines.size * lineStep) + 20f)
            }
        }
        return y
    }

    fun estimateFooterBrandingHeight(
        settings: GlobalSettingsEntity,
        footerStyled: StyledDocumentBitmap?,
        fallbackText: String = ""
    ): Float {
        val scale = fontScale(settings)
        val lineStep = (15f * scale).coerceIn(12f, 24f)
        var h = 0f
        if (footerShowsText(settings)) {
            val txt = settings.receiptFooter.ifBlank { fallbackText }
            val lines = txt.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                h += lines.size * lineStep + 6f
            }
        }
        if (footerStyled != null) {
            h += footerStyled.drawHeightPt + 8f
        }
        return h
    }

    /**
     * Draws custom document footer (text and/or image) constrained above bottomLimit.
     */
    fun drawFooterBranding(
        canvas: Canvas,
        centerX: Float,
        startY: Float,
        bottomLimit: Float,
        settings: GlobalSettingsEntity,
        footerStyled: StyledDocumentBitmap?,
        fallbackText: String = "",
        textColor: Int = android.graphics.Color.GRAY
    ): Float {
        var y = startY
        val scale = fontScale(settings)
        val pFoot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = (10f * scale).coerceIn(8f, 16f)
            textAlign = Paint.Align.CENTER
        }
        val lineStep = (-pFoot.ascent() + pFoot.descent() + 4f)
        if (footerShowsText(settings)) {
            val txt = settings.receiptFooter.ifBlank { fallbackText }
            val lines = txt.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            for (line in lines) {
                val baseline = (y - pFoot.ascent()).coerceAtMost(bottomLimit)
                canvas.drawText(line, centerX, baseline, pFoot)
                y = baseline + pFoot.descent() + 4f
            }
        }
        if (footerStyled != null && y + footerStyled.drawHeightPt <= bottomLimit + 2f) {
            y = drawStyledBitmapCentered(canvas, centerX, y, footerStyled) + 4f
        }
        return y
    }

    private fun resolveImageFile(context: Context, uriString: String?, fallbackFileName: String): File? {
        if (!uriString.isNullOrBlank()) {
            val parsed = Uri.parse(uriString)
            val parsedPath = parsed.path
            if (parsed.scheme == "file" && !parsedPath.isNullOrBlank()) {
                val f = File(parsedPath)
                if (f.exists()) return f
            }
        }
        val fallback = File(context.filesDir, fallbackFileName)
        return fallback.takeIf { it.exists() }
    }

    private fun loadScaledDocumentBitmap(
        context: Context,
        uriString: String?,
        fallbackFileName: String,
        maxWidthPt: Float,
        maxHeightPt: Float,
        qualityPercent: Int
    ): StyledDocumentBitmap? {
        return try {
            val file = resolveImageFile(context, uriString, fallbackFileName) ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            val aspectScale = minOf(maxWidthPt / srcW.toFloat(), maxHeightPt / srcH.toFloat())
            val drawW = (srcW * aspectScale).coerceAtLeast(16f)
            val drawH = (srcH * aspectScale).coerceAtLeast(16f)

            // Higher quality => sharper supersampling for print; lower quality => smaller raster
            val q = qualityPercent.coerceIn(40, 100)
            val resolutionMultiplier = 0.5f + ((q - 40f) / 60f) * 1.5f
            val targetPxW = (drawW * resolutionMultiplier).toInt().coerceIn(24, 1600)
            val targetPxH = (drawH * resolutionMultiplier).toInt().coerceIn(24, 1200)

            var sampleSize = 1
            while ((srcW / (sampleSize * 2)) >= targetPxW && (srcH / (sampleSize * 2)) >= targetPxH) {
                sampleSize *= 2
            }

            val decoded = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: return null

            val scaled = if (decoded.width != targetPxW || decoded.height != targetPxH) {
                val res = Bitmap.createScaledBitmap(decoded, targetPxW, targetPxH, q >= 65)
                if (res !== decoded) decoded.recycle()
                res
            } else {
                decoded
            }

            // Apply optional JPEG quality re-encoding for opaque bitmaps when quality < 85
            val finalBitmap = if (q < 85 && !scaled.hasAlpha()) {
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, q, baos)
                val redecoded = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size())
                if (redecoded != null) {
                    scaled.recycle()
                    redecoded
                } else {
                    scaled
                }
            } else {
                scaled
            }

            StyledDocumentBitmap(finalBitmap, drawW, drawH)
        } catch (t: Throwable) {
            Log.e(TAG, "Error loading styled document bitmap ($fallbackFileName)", t)
            null
        }
    }
}
