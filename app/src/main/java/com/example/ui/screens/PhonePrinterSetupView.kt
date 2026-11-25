package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.printer.BluetoothPrinterService
import com.example.printer.BtPrinterConnectionState
import com.example.printer.PrinterConnectionStatus
import com.example.printer.PrinterDeviceInfo
import com.example.printer.PrinterTransport
import com.example.printer.ThermalPrinterDevice
import com.example.ui.components.rememberBluetoothAwarePrintLauncher
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

/**
 * Reusable full-featured Phone Printer Settings Card powered by [PosThermalPrinterService].
 * Configures Bluetooth, USB OTG, Network Wi-Fi, and Android Print Spooler connections,
 * 58mm/80mm paper width, font size, Arabic RTL / French LTR direction, header/footer/logo,
 * test receipt printing, and print job history with retry.
 */
@Composable
fun PhonePrinterSettingsCard(
    viewModel: PosViewModel,
    lang: String = "AR",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val printerConnectionType by viewModel.printerConnectionType.collectAsStateWithLifecycle()
    val printerName by viewModel.printerName.collectAsStateWithLifecycle()
    val printerMac by viewModel.printerMac.collectAsStateWithLifecycle()
    val printerIp by viewModel.printerIp.collectAsStateWithLifecycle()
    val printerPort by viewModel.printerPort.collectAsStateWithLifecycle()
    val printerPaperWidth by viewModel.printerPaperWidth.collectAsStateWithLifecycle()
    val autoPrintReceipt by viewModel.autoPrintReceipt.collectAsStateWithLifecycle()
    val btState by viewModel.btConnectionState.collectAsStateWithLifecycle()
    val serviceStatus by viewModel.printerConnectionStatus.collectAsStateWithLifecycle()
    val activePrinter by viewModel.activeThermalPrinter.collectAsStateWithLifecycle()
    val printHistory by viewModel.printJobHistory.collectAsStateWithLifecycle()
    val thermalFontSize by viewModel.thermalFontSize.collectAsStateWithLifecycle()
    val receiptTextDirection by viewModel.receiptTextDirection.collectAsStateWithLifecycle()
    val thermalRasterEnabled by viewModel.thermalRasterEnabled.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var isPrintingTest by remember { mutableStateOf(false) }
    var isTestingWifiConnection by remember { mutableStateOf(false) }
    var wifiFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var wifiFeedbackIsError by remember { mutableStateOf(false) }
    var isScanningUsb by remember { mutableStateOf(false) }
    var retryingJobId by remember { mutableStateOf<String?>(null) }
    var pairedDevices by remember { mutableStateOf<List<PrinterDeviceInfo>>(emptyList()) }
    var usbDevices by remember { mutableStateOf<List<ThermalPrinterDevice>>(emptyList()) }
    var bluetoothEnabled by remember { mutableStateOf(BluetoothPrinterService.isBluetoothEnabled(context)) }

    val launchTestPrint = rememberBluetoothAwarePrintLauncher(
        lang = lang,
        onDenied = { isPrintingTest = false }
    ) {
        isPrintingTest = true
        viewModel.printThermalTestReceipt(context) { res ->
            isPrintingTest = false
            if (printerConnectionType.equals("WIFI", ignoreCase = true)) {
                wifiFeedbackMessage = res.message
                wifiFeedbackIsError = !res.success
            }
            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
        }
    }

    fun refreshBluetoothDevices() {
        bluetoothEnabled = BluetoothPrinterService.isBluetoothEnabled(context)
        pairedDevices = BluetoothPrinterService.getPairedDevices(context)
    }

    fun refreshUsbPrinters() {
        isScanningUsb = true
        viewModel.discoverThermalPrinters(context, PrinterTransport.USB) { found ->
            usbDevices = found
            isScanningUsb = false
        }
    }

    LaunchedEffect(Unit) {
        refreshBluetoothDevices()
        refreshUsbPrinters()
    }

    val isCurrentlyConnected = serviceStatus is PrinterConnectionStatus.Connected ||
        btState is BtPrinterConnectionState.Connected

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PrinterBgDarkNavy),
        border = BorderStroke(1.dp, PrinterBorderLuminous)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PrinterSetupStatusHeader(
                serviceStatus = serviceStatus,
                btState = btState,
                printerConnectionType = printerConnectionType,
                printerIp = printerIp,
                printerMac = printerMac,
                printerName = printerName,
                lang = lang
            )

            if (isCurrentlyConnected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrinterAccentEmerald.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activePrinter?.name?.ifBlank { printerName } ?: printerName.ifBlank { "Thermal Printer" },
                        color = PrinterAccentEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            viewModel.disconnectThermalPrinter { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("printer_disconnect_active_button")
                    ) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null, tint = PrinterAccentRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = Bilingual.tr(lang, "فصل الاتصال", "Déconnecter", "Disconnect"),
                            color = PrinterAccentRed,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

            Text(
                text = Bilingual.tr(lang, "نوع الاتصال بالطابعة:", "Mode de connexion:", "Connection mode:"),
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            PrinterTransportModeSelector(
                selectedMode = printerConnectionType,
                onSelectMode = { key ->
                    viewModel.updatePrinterConnectionType(key)
                    if (key == "USB") refreshUsbPrinters()
                },
                lang = lang
            )

            when (printerConnectionType.uppercase()) {
                "BLUETOOTH" -> BluetoothModeSection(
                    pairedDevices = pairedDevices,
                    selectedMac = printerMac,
                    selectedName = printerName,
                    isBluetoothEnabled = bluetoothEnabled,
                    onSelectDevice = { dev ->
                        val thermalDev = ThermalPrinterDevice(
                            id = dev.address,
                            name = dev.name.ifBlank { dev.address },
                            address = dev.address,
                            transport = PrinterTransport.BLUETOOTH,
                            isPairedOrAuthorized = dev.isBonded
                        )
                        viewModel.connectThermalPrinter(context, thermalDev) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRefresh = { refreshBluetoothDevices() },
                    onOpenSettings = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                Bilingual.tr(lang, "تعذر فتح إعدادات البلوتوث", "Impossible d'ouvrir Bluetooth", "Unable to open Bluetooth settings"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    lang = lang
                )

                "USB", "OTG" -> UsbPrinterModeSection(
                    usbDevices = usbDevices,
                    activePrinter = activePrinter,
                    selectedAddress = printerMac,
                    isScanning = isScanningUsb,
                    onScanUsb = { refreshUsbPrinters() },
                    onConnectUsb = { dev ->
                        viewModel.connectThermalPrinter(context, dev) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDisconnect = {
                        viewModel.disconnectThermalPrinter { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    lang = lang
                )

                "WIFI" -> WifiModeSection(
                    ip = printerIp,
                    port = printerPort,
                    onIpChange = {
                        wifiFeedbackMessage = null
                        viewModel.updatePrinterIp(it)
                    },
                    onPortChange = {
                        wifiFeedbackMessage = null
                        viewModel.updatePrinterPort(it)
                    },
                    isTestingConnection = isTestingWifiConnection,
                    isPrintingTest = isPrintingTest,
                    wifiFeedbackMessage = wifiFeedbackMessage,
                    wifiFeedbackIsError = wifiFeedbackIsError,
                    onTestConnection = { targetIp, targetPort ->
                        isTestingWifiConnection = true
                        wifiFeedbackMessage = null
                        viewModel.testWifiPrinterConnection(context, targetIp, targetPort) { ok, msg ->
                            isTestingWifiConnection = false
                            wifiFeedbackMessage = msg
                            wifiFeedbackIsError = !ok
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onTestPrintWifi = { targetIp, targetPort ->
                        isPrintingTest = true
                        wifiFeedbackMessage = null
                        viewModel.printWifiTestReceipt(context, targetIp, targetPort) { res ->
                            isPrintingTest = false
                            wifiFeedbackMessage = res.message
                            wifiFeedbackIsError = !res.success
                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onFallbackAndroidPrint = {
                        isPrintingTest = true
                        viewModel.printAndroidSystemTestReceipt(context) { res ->
                            isPrintingTest = false
                            wifiFeedbackMessage = res.message
                            wifiFeedbackIsError = !res.success
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    lang = lang
                )

                "SYSTEM" -> SystemModeSection(lang = lang)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Bilingual.tr(lang, "عرض الورق الحراري:", "Largeur papier:", "Thermal paper width:"),
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple(58, Bilingual.tr(lang, "58mm (قياسي)", "58mm (Std)", "58mm (Std)"), "printer_paper_58"),
                        Triple(80, Bilingual.tr(lang, "80mm (عريض)", "80mm (Large)", "80mm (Wide)"), "printer_paper_80")
                    ).forEach { (widthVal, widthLabel, tag) ->
                        val isSel = printerPaperWidth == widthVal
                        Surface(
                            onClick = { viewModel.updatePrinterPaperWidth(widthVal) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) PrinterAccentBlue.copy(alpha = 0.25f) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (isSel) PrinterAccentCyan else PrinterBorderLuminous),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag(tag)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = widthLabel,
                                    color = if (isSel) PrinterAccentCyan else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B).copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Bilingual.tr(lang, "طباعة الوصل تلقائياً عند إنهاء البيع", "Imprimer automatiquement après la vente", "Auto-print receipt after checkout"),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = autoPrintReceipt,
                    onCheckedChange = { viewModel.updateAutoPrintReceipt(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrinterAccentBlue
                    ),
                    modifier = Modifier.testTag("printer_auto_print_switch")
                )
            }

            ThermalFormattingSettingsSection(
                fontSize = thermalFontSize,
                textDirection = receiptTextDirection,
                rasterEnabled = thermalRasterEnabled,
                showLogo = settings.showLogo,
                receiptHeader = settings.receiptHeader,
                receiptFooter = settings.receiptFooter,
                onFontSizeChange = { viewModel.updateThermalFontSize(it) },
                onDirectionChange = { viewModel.updateReceiptTextDirection(it) },
                onRasterToggle = { viewModel.updateThermalRasterEnabled(it) },
                onSaveHeaderFooterLogo = { h, f, logo ->
                    viewModel.updateReceiptHeaderFooter(h, f, logo)
                    Toast.makeText(
                        context,
                        Bilingual.tr(lang, "تم حفظ تنسيق الوصل بنجاح", "Mise en forme enregistrée", "Receipt format saved"),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                lang = lang
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { launchTestPrint(printerConnectionType) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("printer_test_print_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PrinterAccentBlue),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isPrintingTest
                ) {
                    if (isPrintingTest) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        val testPrintLabel = when (printerConnectionType.uppercase()) {
                            "WIFI", "NETWORK", "IP" -> Bilingual.tr(lang, "طباعة تجريبية عبر Wi-Fi", "Ticket test Wi-Fi", "Wi-Fi Test Receipt")
                            "USB", "OTG" -> Bilingual.tr(lang, "طباعة تجريبية عبر USB", "Ticket test USB", "USB Test Receipt")
                            "SYSTEM", "ANDROID" -> Bilingual.tr(lang, "طباعة تجريبية (أندرويد)", "Test Android Print", "Android Test Print")
                            else -> Bilingual.tr(lang, "طباعة إيصال تجريبي", "Imprimer ticket test", "Print Test Receipt")
                        }
                        Text(
                            text = testPrintLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                if (printerConnectionType == "BLUETOOTH") {
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    Bilingual.tr(lang, "تعذر فتح إعدادات البلوتوث", "Impossible d'ouvrir Bluetooth", "Unable to open Bluetooth settings"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PrinterBorderLuminous)
                    ) {
                        Icon(
                            Icons.Filled.SettingsBluetooth,
                            contentDescription = Bilingual.tr(lang, "إعدادات البلوتوث", "Paramètres Bluetooth", "Bluetooth settings"),
                            tint = PrinterAccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            PrintJobHistorySection(
                history = printHistory,
                retryingJobId = retryingJobId,
                onRetryJob = { jobId ->
                    retryingJobId = jobId
                    viewModel.retryPrintJob(context, jobId) { res ->
                        retryingJobId = null
                        Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                    }
                },
                onClearHistory = { viewModel.clearPrintHistory(context) },
                lang = lang
            )
        }
    }
}
