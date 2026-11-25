package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.printer.PrintJobRecord
import com.example.printer.PrintJobStatus
import com.example.printer.PrinterTransport
import com.example.printer.ReceiptTextDirection
import com.example.printer.ThermalFontSize
import com.example.printer.ThermalPrinterDevice
import com.example.ui.theme.Bilingual
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun UsbPrinterModeSection(
    usbDevices: List<ThermalPrinterDevice>,
    activePrinter: ThermalPrinterDevice?,
    selectedAddress: String,
    isScanning: Boolean,
    onScanUsb: () -> Unit,
    onConnectUsb: (ThermalPrinterDevice) -> Unit,
    onDisconnect: () -> Unit,
    lang: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Bilingual.tr(
                    lang,
                    "طابعات USB الحرارية المتصلة (OTG):",
                    "Imprimantes thermiques USB (OTG) :",
                    "Connected USB Thermal Printers (OTG):"
                ),
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onScanUsb,
                enabled = !isScanning,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("usb_scan_printers_button")
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        color = PrinterAccentCyan,
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.Usb,
                        contentDescription = null,
                        tint = PrinterAccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = Bilingual.tr(lang, "فحص USB", "Scanner USB", "Scan USB"),
                    color = PrinterAccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (usbDevices.isEmpty()) {
            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.65f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PrinterBorderLuminous),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.UsbOff,
                        contentDescription = null,
                        tint = PrinterAccentGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "لم يتم اكتشاف أي طابعة USB حرارية",
                                "Aucune imprimante USB détectée",
                                "No USB thermal printer detected"
                            ),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "قم بتوصيل الطابعة بكابل USB OTG ثم اضغط «فحص USB» لمنح الإذن والربط.",
                                "Branchez l'imprimante via câble USB OTG puis appuyez sur « Scanner USB ».",
                                "Plug in your printer via USB OTG cable and tap 'Scan USB'."
                            ),
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                usbDevices.forEach { device ->
                    val isActive = activePrinter?.transport == PrinterTransport.USB &&
                        activePrinter.address == device.address
                    val isSelected = isActive || selectedAddress == device.address
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) PrinterAccentBlue.copy(alpha = 0.18f) else Color(0xFF1E293B),
                        border = BorderStroke(
                            1.dp,
                            if (isActive) PrinterAccentEmerald else if (isSelected) PrinterAccentCyan else PrinterBorderLuminous
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Filled.Usb,
                                    contentDescription = null,
                                    tint = if (isActive) PrinterAccentEmerald else PrinterAccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = device.name,
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = device.details.ifBlank { device.address },
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isActive) {
                                OutlinedButton(
                                    onClick = onDisconnect,
                                    border = BorderStroke(1.dp, PrinterAccentRed.copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("usb_printer_disconnect_button")
                                ) {
                                    Text(
                                        Bilingual.tr(lang, "فصل", "Déconnecter", "Disconnect"),
                                        color = PrinterAccentRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { onConnectUsb(device) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrinterAccentBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("usb_printer_connect_button")
                                ) {
                                    Text(
                                        Bilingual.tr(lang, "اتصال", "Connecter", "Connect"),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PrintJobHistorySection(
    history: List<PrintJobRecord>,
    retryingJobId: String?,
    onRetryJob: (String) -> Unit,
    onClearHistory: () -> Unit,
    lang: String
) {
    val timeFormatter = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.US) }

    Surface(
        color = Color(0xFF0B1326),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PrinterBorderLuminous.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = PrinterAccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "سجل عمليات الطباعة (${history.size})",
                            "Historique d'impression (${history.size})",
                            "Print Job Log (${history.size})"
                        ),
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (history.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("print_history_clear_button")
                    ) {
                        Text(
                            text = Bilingual.tr(lang, "مسح السجل", "Effacer", "Clear"),
                            color = PrinterAccentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (history.isEmpty()) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "لا توجد عمليات طباعة مسجلة بعد.",
                        "Aucune tâche d'impression enregistrée.",
                        "No print jobs recorded yet."
                    ),
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                history.take(15).forEach { job ->
                    val statusColor = when (job.status) {
                        PrintJobStatus.SUCCESS -> PrinterAccentEmerald
                        PrintJobStatus.FAILED -> PrinterAccentRed
                        PrintJobStatus.PRINTING, PrintJobStatus.PENDING -> PrinterAccentGold
                    }
                    val statusLabel = when (job.status) {
                        PrintJobStatus.SUCCESS -> Bilingual.tr(lang, "نجاح", "Succès", "Success")
                        PrintJobStatus.FAILED -> Bilingual.tr(lang, "فشل", "Échec", "Failed")
                        PrintJobStatus.PRINTING -> Bilingual.tr(lang, "جاري", "En cours", "Printing")
                        PrintJobStatus.PENDING -> Bilingual.tr(lang, "قيد الانتظار", "En attente", "Pending")
                    }

                    Surface(
                        color = Color(0xFF1E293B).copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = job.title.ifBlank { job.invoiceNumber },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = statusColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(statusColor)
                                        )
                                        Text(
                                            text = statusLabel,
                                            color = statusColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "${job.transport.name} • ${job.paperWidthMm}mm • ${job.language} • ${timeFormatter.format(Date(job.timestamp))} • #${job.attemptCount}",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )

                            if (!job.errorMessage.isNullOrBlank()) {
                                Text(
                                    text = job.errorMessage,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 10.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (job.status == PrintJobStatus.FAILED) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { onRetryJob(job.id) },
                                        enabled = retryingJobId != job.id,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("print_job_retry_button")
                                    ) {
                                        if (retryingJobId == job.id) {
                                            CircularProgressIndicator(
                                                color = PrinterAccentCyan,
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                Icons.Filled.Refresh,
                                                contentDescription = null,
                                                tint = PrinterAccentCyan,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = Bilingual.tr(lang, "إعادة المحاولة", "Réessayer", "Retry"),
                                            color = PrinterAccentCyan,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
