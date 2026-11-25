package com.example.ui.screens.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.printer.PrinterDeviceInfo
import com.example.printer.BtPrinterConnectionState
import com.example.printer.DiscoveredPrinter
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

internal val CheckoutPrinterCardBg = Color(0xFF0F172A)
internal val CheckoutPrinterPanelBg = Color(0xFF1E293B)
internal val CheckoutPrinterBorder = Color(0xFF334155)
internal val PrinterGreen = Color(0xFF10B981)
internal val PrinterBlue = Color(0xFF38BDF8)
internal val PrinterAmber = Color(0xFFF59E0B)
internal val PrinterRed = Color(0xFFEF4444)

/**
 * Compact live Bluetooth printer status & direct transaction summary print card
 * embedded directly in the Checkout screen.
 */
@Composable
fun CheckoutPrinterStatusCard(
    viewModel: PosViewModel,
    lang: String,
    isPrintingSummary: Boolean,
    printStatusMessage: String?,
    printStatusIsError: Boolean,
    onOpenPrinterPicker: () -> Unit,
    onPrintSummaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val btState by viewModel.btConnectionState.collectAsStateWithLifecycle()
    val savedPrinterName by viewModel.printerName.collectAsStateWithLifecycle()
    val savedPrinterMac by viewModel.printerMac.collectAsStateWithLifecycle()
    val paperWidth by viewModel.printerPaperWidth.collectAsStateWithLifecycle()
    val autoPrint by viewModel.autoPrintReceipt.collectAsStateWithLifecycle()
    val savedLabel = savedPrinterName.ifBlank { savedPrinterMac }

    val (dotColor, statusTitle, deviceSubtitle) = when (val s = btState) {
        is BtPrinterConnectionState.Connected -> Triple(
            PrinterGreen,
            Bilingual.tr(lang, "طابعة بلوتوث متصلة", "Imprimante BT connectée", "BT Printer Connected"),
            "${s.device.name} (${s.device.address})"
        )
        is BtPrinterConnectionState.Printing -> Triple(
            PrinterBlue,
            Bilingual.tr(lang, "جاري الطباعة...", "Impression...", "Printing..."),
            "${s.device.name} • ${s.jobDescription}"
        )
        is BtPrinterConnectionState.Connecting -> Triple(
            PrinterAmber,
            Bilingual.tr(lang, "جاري الاتصال...", "Connexion...", "Connecting..."),
            "${s.deviceName} (${s.address})"
        )
        is BtPrinterConnectionState.Reconnecting -> Triple(
            PrinterAmber,
            Bilingual.tr(
                lang,
                "إعادة الاتصال (${s.attempt}/${s.maxAttempts})...",
                "Reconnexion (${s.attempt}/${s.maxAttempts})...",
                "Reconnecting (${s.attempt}/${s.maxAttempts})..."
            ),
            "${s.device.name} • ${s.reason ?: s.device.address}"
        )
        is BtPrinterConnectionState.Error -> Triple(
            PrinterRed,
            Bilingual.tr(lang, "خطأ في الاتصال", "Erreur connexion", "Connection Error"),
            s.message
        )
        is BtPrinterConnectionState.Disconnected -> {
            val sub = if (savedLabel.isNotBlank()) {
                Bilingual.tr(
                    lang,
                    "المحفوظة: $savedLabel (غير متصلة بعد)",
                    "Enregistrée : $savedLabel (en veille)",
                    "Saved: $savedLabel (standby)"
                )
            } else {
                Bilingual.tr(
                    lang,
                    "لم يتم اختيار طابعة بلوتوث",
                    "Aucune imprimante Bluetooth sélectionnée",
                    "No Bluetooth printer selected"
                )
            }
            Triple(
                if (savedLabel.isNotBlank()) PrinterAmber else Color(0xFF94A3B8),
                Bilingual.tr(lang, "طابعة البلوتوث", "Imprimante Bluetooth", "Bluetooth Printer"),
                sub
            )
        }
    }

    val canQuickReconnect = savedLabel.isNotBlank() &&
        (btState is BtPrinterConnectionState.Error || btState is BtPrinterConnectionState.Disconnected)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CheckoutPrinterCardBg),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (btState is BtPrinterConnectionState.Reconnecting || btState is BtPrinterConnectionState.Connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = PrinterAmber,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = statusTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = CheckoutPrinterPanelBg,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, CheckoutPrinterBorder)
                            ) {
                                Text(
                                    text = "${paperWidth}mm",
                                    color = PrinterBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = deviceSubtitle,
                            color = if (btState is BtPrinterConnectionState.Error) PrinterRed else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onOpenPrinterPicker,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("checkout_bt_connect_button")
                ) {
                    Icon(
                        imageVector = if (btState is BtPrinterConnectionState.Connected) {
                            Icons.Filled.BluetoothConnected
                        } else {
                            Icons.Filled.BluetoothSearching
                        },
                        contentDescription = Bilingual.tr(
                            lang,
                            "إعداد طابعة البلوتوث",
                            "Configurer imprimante Bluetooth",
                            "Configure Bluetooth printer"
                        ),
                        tint = dotColor
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CheckoutPrinterPanelBg)
                    .clickable { viewModel.updateAutoPrintReceipt(!autoPrint) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "طباعة تلقائية عند تأكيد البيع",
                        "Impression auto à la validation",
                        "Auto-print on sale confirmation"
                    ),
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = autoPrint,
                    onCheckedChange = { viewModel.updateAutoPrintReceipt(it) },
                    modifier = Modifier.testTag("checkout_autoprint_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrinterGreen
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (canQuickReconnect) {
                            viewModel.reconnectBluetoothPrinter(context)
                        } else {
                            onOpenPrinterPicker()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (canQuickReconnect) PrinterAmber.copy(alpha = 0.75f) else PrinterBlue.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .weight(0.95f)
                        .heightIn(min = 48.dp)
                        .testTag(if (canQuickReconnect) "checkout_bt_reconnect_button" else "checkout_bt_switch_button")
                ) {
                    Icon(
                        if (canQuickReconnect) Icons.Filled.Refresh else Icons.Filled.Bluetooth,
                        contentDescription = Bilingual.tr(
                            lang,
                            if (canQuickReconnect) "إعادة الاتصال" else "ربط طابعة",
                            if (canQuickReconnect) "Reconnecter" else "Connecter",
                            if (canQuickReconnect) "Reconnect" else "Connect Printer"
                        ),
                        tint = if (canQuickReconnect) PrinterAmber else PrinterBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            canQuickReconnect -> Bilingual.tr(lang, "إعادة الاتصال", "Reconnecter", "Reconnect")
                            btState is BtPrinterConnectionState.Connected -> Bilingual.tr(lang, "تغيير الطابعة", "Changer", "Switch BT")
                            else -> Bilingual.tr(lang, "ربط طابعة", "Connecter BT", "Connect BT")
                        },
                        color = if (canQuickReconnect) PrinterAmber else PrinterBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onPrintSummaryClick,
                    enabled = !isPrintingSummary && btState !is BtPrinterConnectionState.Connecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .heightIn(min = 48.dp)
                        .testTag("checkout_print_summary_button")
                ) {
                    if (isPrintingSummary || btState is BtPrinterConnectionState.Printing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Filled.ReceiptLong,
                            contentDescription = Bilingual.tr(
                                lang,
                                "طباعة ملخص المعاملة",
                                "Imprimer résumé",
                                "Print transaction summary"
                            ),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "طباعة الملخص",
                            "Imprimer résumé",
                            "Print Summary"
                        ),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!printStatusMessage.isNullOrBlank()) {
                Surface(
                    color = (if (printStatusIsError) PrinterRed else PrinterGreen).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        (if (printStatusIsError) PrinterRed else PrinterGreen).copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = printStatusMessage,
                        color = if (printStatusIsError) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CheckoutPairedPrinterItemRow(
    device: PrinterDeviceInfo,
    isSaved: Boolean,
    isConnectedDev: Boolean,
    btState: BtPrinterConnectionState,
    lang: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isConnectedDev) PrinterGreen.copy(alpha = 0.16f) else CheckoutPrinterPanelBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            when {
                isConnectedDev -> PrinterGreen
                isSaved -> PrinterBlue
                else -> CheckoutPrinterBorder
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("checkout_bt_device_${device.address}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isConnectedDev) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = if (isConnectedDev) PrinterGreen else PrinterBlue
                )
                Column {
                    Text(
                        text = device.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.address,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
            Text(
                text = when {
                    isConnectedDev -> Bilingual.tr(lang, "متصل", "Connectée", "Connected")
                    btState is BtPrinterConnectionState.Connecting &&
                        btState.address.equals(device.address, ignoreCase = true) ->
                        Bilingual.tr(lang, "اتصال...", "En cours...", "Connecting...")
                    else -> Bilingual.tr(lang, "اتصال", "Connecter", "Connect")
                },
                color = if (isConnectedDev) PrinterGreen else PrinterBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
internal fun CheckoutDiscoveredPrinterItemRow(
    printer: DiscoveredPrinter,
    lang: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = CheckoutPrinterPanelBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, PrinterAmber.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(printer.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${printer.address} • ${printer.details}", color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
            Text(
                text = Bilingual.tr(lang, "ربط واتصال", "Appairer", "Pair & Connect"),
                color = PrinterAmber,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
