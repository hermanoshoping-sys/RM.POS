package com.example.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.printer.InAppPrintPreviewManager
import com.example.printer.InAppPrintRequest
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InAppPrintDialogHost(lang: String) {
    val activeRequest by InAppPrintPreviewManager.activeRequest.collectAsStateWithLifecycle()
    val request = activeRequest ?: return
    InAppPrintDialog(
        request = request,
        lang = lang,
        onDismiss = { InAppPrintPreviewManager.dismiss() }
    )
}

@Composable
fun InAppPrintDialog(
    request: InAppPrintRequest,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copies by remember(request.id) { mutableIntStateOf(1) }
    var isRendering by remember(request.id) { mutableStateOf(true) }
    var renderedPages by remember(request.id) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var totalPdfPages by remember(request.id) { mutableIntStateOf(0) }
    var savedBannerMessage by remember(request.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(request.id) {
        isRendering = true
        val (pageCount, pages) = withContext(Dispatchers.IO) {
            InAppPrintPreviewManager.renderPdfPreviewPages(request.pdfFile)
        }
        totalPdfPages = pageCount
        renderedPages = pages
        isRendering = false
    }

    DisposableEffect(request.id) {
        onDispose {
            renderedPages.forEach { bmp ->
                if (!bmp.isRecycled) {
                    runCatching { bmp.recycle() }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 580.dp)
                .heightIn(min = 420.dp, max = 760.dp)
                .testTag("in_app_print_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = POSPrimary.copy(alpha = 0.14f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Print,
                                    contentDescription = null,
                                    tint = POSPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "نافذة الطباعة والمعاينة",
                                    "Aperçu et impression",
                                    "Print & Preview"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = request.jobTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("in_app_print_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = Bilingual.tr(lang, "إغلاق", "Fermer", "Close"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Copies & Format Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        val pageBadge = if (totalPdfPages > 0) {
                            Bilingual.tr(
                                lang,
                                "PDF • $totalPdfPages صفحة",
                                "PDF • $totalPdfPages page(s)",
                                "PDF • $totalPdfPages page(s)"
                            )
                        } else {
                            "PDF • A4"
                        }
                        Text(
                            text = pageBadge,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = Bilingual.tr(lang, "النسخ:", "Copies :", "Copies:"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { if (copies > 1) copies-- },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = Bilingual.tr(lang, "إنقاص النسخ", "Moins", "Decrease copies"),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = copies.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { if (copies < 20) copies++ },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = Bilingual.tr(lang, "زيادة النسخ", "Plus", "Increase copies"),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Preview Canvas Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFE2E8F0))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isRendering -> {
                            CircularProgressIndicator(color = POSPrimary)
                        }
                        renderedPages.isNotEmpty() -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                itemsIndexed(renderedPages) { index, bmp ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        shadowElevation = 3.dp,
                                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Page ${index + 1}",
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        shadowElevation = 2.dp,
                                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = request.plainTextPreview,
                                            color = Color(0xFF0F172A),
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Saved Confirmation Banner
                savedBannerMessage?.let { savedMsg ->
                    Surface(
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = savedMsg,
                                color = Color(0xFF14532D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Footer Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val activePdf = request.pdfFile
                    Button(
                        onClick = {
                            scope.launch {
                                val pdfToSave = withContext(Dispatchers.IO) {
                                    activePdf?.takeIf { it.exists() && it.length() > 0L }
                                        ?: InAppPrintPreviewManager.generatePlainTextReceiptPdf(
                                            context,
                                            request.jobTitle,
                                            request.plainTextPreview
                                        )
                                }
                                if (pdfToSave == null) {
                                    Toast.makeText(
                                        context.applicationContext ?: context,
                                        Bilingual.tr(lang, "تعذر إنشاء ملف PDF للحفظ", "Impossible de créer le PDF", "Unable to build PDF file"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                                val result = withContext(Dispatchers.IO) {
                                    InAppPrintPreviewManager.savePdfToDownloads(
                                        context = context,
                                        sourceFile = pdfToSave,
                                        jobTitle = request.jobTitle,
                                        copies = copies
                                    )
                                }
                                result.onSuccess { savedPath ->
                                    val msg = Bilingual.tr(
                                        lang,
                                        "تم حفظ ملف PDF بنجاح: $savedPath",
                                        "PDF enregistré : $savedPath",
                                        "PDF saved: $savedPath"
                                    )
                                    savedBannerMessage = msg
                                    Toast.makeText(context.applicationContext ?: context, msg, Toast.LENGTH_LONG).show()
                                }.onFailure { err ->
                                    Toast.makeText(
                                        context.applicationContext ?: context,
                                        Bilingual.tr(lang, "تعذر الحفظ: ${err.localizedMessage ?: ""}", "Erreur d'enregistrement", "Save failed"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("in_app_print_save_pdf_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "حفظ كملف PDF في الجهاز",
                                "Enregistrer en PDF",
                                "Save as PDF"
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val file = activePdf ?: InAppPrintPreviewManager.generatePlainTextReceiptPdf(
                                    context,
                                    request.jobTitle,
                                    request.plainTextPreview
                                )
                                if (file != null) {
                                    InAppPrintPreviewManager.sharePdfToPrinterOrApps(context, file, request.jobTitle)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("in_app_print_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Bilingual.tr(lang, "إرسال لطابعة / مشاركة", "Imprimer / Partager", "Print App / Share"),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val file = activePdf ?: InAppPrintPreviewManager.generatePlainTextReceiptPdf(
                                    context,
                                    request.jobTitle,
                                    request.plainTextPreview
                                )
                                if (file != null) {
                                    InAppPrintPreviewManager.openPdfInExternalApp(context, file, request.jobTitle)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("in_app_print_open_pdf_button")
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Bilingual.tr(lang, "فتح بعارض خارجي", "Ouvrir le PDF", "Open PDF"),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
