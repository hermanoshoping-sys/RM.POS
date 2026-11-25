package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.printer.BatchPrintItemStatus
import com.example.printer.BatchPrintQueueItem
import com.example.printer.BatchPrintQueueState
import com.example.printer.PrinterTransport
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BatchNavyBg = Color(0xFF0A1224)
private val BatchSurfaceCard = Color(0xFF111C34)
private val BatchBorder = Color(0xFF1E3A5F)
private val BatchCyan = Color(0xFF38BDF8)
private val BatchEmerald = Color(0xFF10B981)
private val BatchAmber = Color(0xFFF59E0B)
private val BatchRed = Color(0xFFEF4444)

/**
 * Live progress & control dialog for the sequential Batch Print Queue.
 */
@Composable
fun BatchPrintQueueDialog(
    state: BatchPrintQueueState,
    currency: String,
    lang: String,
    onCancelQueue: () -> Unit,
    onRetryFailed: () -> Unit,
    onFallbackAndroidPrint: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.US)

    Dialog(
        onDismissRequest = { if (!state.isRunning) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !state.isRunning,
            dismissOnClickOutside = !state.isRunning,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = BatchNavyBg),
            border = BorderStroke(1.dp, BatchBorder),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .heightIn(max = 640.dp)
                .testTag("batch_print_queue_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF0284C7), BatchCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Print,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = Bilingual.tr(lang, "قائمة انتظار الطباعة المجمّعة", "File d'impression groupée", "Batch Print Queue"),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.targetPrinterName} • ${state.transport.name}",
                                color = BatchCyan,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val (pillColor, pillLabel) = when {
                        state.isRunning -> BatchCyan to Bilingual.tr(lang, "جاري الطباعة", "En cours", "Printing")
                        state.failedCount > 0 -> BatchRed to Bilingual.tr(lang, "اكتمل مع أخطاء", "Erreurs", "With Errors")
                        state.cancelledCount > 0 -> BatchAmber to Bilingual.tr(lang, "متوقف", "Annulé", "Cancelled")
                        else -> BatchEmerald to Bilingual.tr(lang, "اكتمل", "Terminé", "Completed")
                    }

                    Surface(
                        color = pillColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, pillColor.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(pillColor)
                            )
                            Text(
                                text = pillLabel,
                                color = pillColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Progress bar + KPIs
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "التقدم: ${state.completedCount} من ${state.totalCount} فاتورة",
                                "Progression : ${state.completedCount} / ${state.totalCount}",
                                "Progress: ${state.completedCount} / ${state.totalCount}"
                            ),
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${(state.progressFraction * 100).toInt()}%",
                            color = BatchCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (state.failedCount > 0 && !state.isRunning) BatchAmber else BatchEmerald,
                        trackColor = Color(0xFF1E293B)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QueueMetricChip(
                            label = Bilingual.tr(lang, "نجحت: ${state.successCount}", "Réussies: ${state.successCount}", "Done: ${state.successCount}"),
                            color = BatchEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        QueueMetricChip(
                            label = Bilingual.tr(lang, "فشلت: ${state.failedCount}", "Échecs: ${state.failedCount}", "Failed: ${state.failedCount}"),
                            color = if (state.failedCount > 0) BatchRed else Color(0xFF64748B),
                            modifier = Modifier.weight(1f)
                        )
                        QueueMetricChip(
                            label = Bilingual.tr(lang, "متبقي: ${state.pendingCount}", "Restant: ${state.pendingCount}", "Pending: ${state.pendingCount}"),
                            color = BatchCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Scrollable queue list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 120.dp, max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = state.items,
                        key = { idx, item -> "${item.sale.id}_$idx" }
                    ) { idx, item ->
                        BatchQueueRowCard(
                            index = idx + 1,
                            item = item,
                            dateStr = dateFormat.format(Date(item.sale.timestamp)),
                            amountStr = MoneyUtils.format(item.sale.totalAmountDouble, currency),
                            lang = lang
                        )
                    }
                }

                // Fallback Android Print option if thermal/Wi-Fi jobs failed
                if (!state.isRunning && state.failedCount > 0 && state.transport != PrinterTransport.SYSTEM) {
                    OutlinedButton(
                        onClick = onFallbackAndroidPrint,
                        border = BorderStroke(1.dp, BatchCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("batch_queue_android_fallback_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Smartphone,
                            contentDescription = null,
                            tint = BatchCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "طباعة الفواتير الفاشلة عبر نظام أندرويد (Wi-Fi / PDF)",
                                "Imprimer les échecs via Android Print (Wi-Fi / PDF)",
                                "Print Failed Invoices via Android Print (Wi-Fi / PDF)"
                            ),
                            color = BatchCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isRunning) {
                        Button(
                            onClick = onCancelQueue,
                            colors = ButtonDefaults.buttonColors(containerColor = BatchRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("batch_queue_cancel_button")
                        ) {
                            Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Bilingual.tr(lang, "إيقاف قائمة الانتظار", "Arrêter la file", "Cancel Queue"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        if (state.hasRetryableItems) {
                            OutlinedButton(
                                onClick = onRetryFailed,
                                border = BorderStroke(1.dp, BatchAmber),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("batch_queue_retry_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = BatchAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = Bilingual.tr(lang, "إعادة الفاشلة", "Réessayer", "Retry Failed"),
                                    color = BatchAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("batch_queue_close_button")
                        ) {
                            Text(
                                text = if (state.failedCount == 0 && state.cancelledCount == 0) {
                                    Bilingual.tr(lang, "تم بنجاح", "Terminé", "Done")
                                } else {
                                    Bilingual.tr(lang, "إغلاق", "Fermer", "Close")
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueMetricChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BatchQueueRowCard(
    index: Int,
    item: BatchPrintQueueItem,
    dateStr: String,
    amountStr: String,
    lang: String
) {
    val borderColor = when (item.status) {
        BatchPrintItemStatus.PRINTING -> BatchCyan
        BatchPrintItemStatus.SUCCESS -> BatchEmerald.copy(alpha = 0.5f)
        BatchPrintItemStatus.FAILED -> BatchRed.copy(alpha = 0.6f)
        BatchPrintItemStatus.CANCELLED -> BatchAmber.copy(alpha = 0.5f)
        BatchPrintItemStatus.QUEUED -> Color(0xFF1E293B)
    }

    Surface(
        color = BatchSurfaceCard,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("batch_queue_item_${item.sale.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (item.status) {
                    BatchPrintItemStatus.PRINTING -> CircularProgressIndicator(
                        color = BatchCyan,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    BatchPrintItemStatus.SUCCESS -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = BatchEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    BatchPrintItemStatus.FAILED -> Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = BatchRed,
                        modifier = Modifier.size(18.dp)
                    )
                    BatchPrintItemStatus.CANCELLED -> Icon(
                        Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = BatchAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    BatchPrintItemStatus.QUEUED -> Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#$index • ${item.sale.invoiceNumber}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.sale.customerName.ifBlank { Bilingual.tr(lang, "زبون عادي", "Client Comptoir", "Walk-in") }} • $dateStr",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = amountStr,
                    color = BatchCyan,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (!item.errorMessage.isNullOrBlank()) {
                Text(
                    text = item.errorMessage,
                    color = Color(0xFFFCA5A5),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
