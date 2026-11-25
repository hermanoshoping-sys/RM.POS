package com.example.ui.screens.pos

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.printer.BluetoothDiscoveryManager
import com.example.printer.BluetoothPrinterConnectionManager
import com.example.printer.BluetoothPrinterService
import com.example.printer.BtPrinterConnectionState
import com.example.printer.DiscoveredPrinter
import com.example.printer.PrinterDeviceInfo
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

/**
 * In-Checkout Bluetooth thermal printer selector, connection manager, and discovery dialog.
 */
@Composable
fun CheckoutBluetoothPrinterPickerDialog(
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit,
    onConnectedAndReady: ((PrinterDeviceInfo) -> Unit)? = null
) {
    val context = LocalContext.current
    val btState by viewModel.btConnectionState.collectAsStateWithLifecycle()
    val paperWidth by viewModel.printerPaperWidth.collectAsStateWithLifecycle()
    val savedMac by viewModel.printerMac.collectAsStateWithLifecycle()
    val savedName by viewModel.printerName.collectAsStateWithLifecycle()

    var hasConnectPerm by remember {
        mutableStateOf(BluetoothPrinterConnectionManager.hasConnectPermission(context))
    }
    var hasScanPerm by remember {
        mutableStateOf(BluetoothPrinterConnectionManager.hasDiscoveryPermission(context))
    }
    var btEnabled by remember {
        mutableStateOf(BluetoothPrinterService.isBluetoothEnabled(context))
    }
    var pairedDevices by remember {
        mutableStateOf(
            if (hasConnectPerm) BluetoothPrinterConnectionManager.getPairedDevicesSafe(context) else emptyList()
        )
    }
    var discoveredDevices by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }

    fun refreshDevices() {
        hasConnectPerm = BluetoothPrinterConnectionManager.hasConnectPermission(context)
        hasScanPerm = BluetoothPrinterConnectionManager.hasDiscoveryPermission(context)
        btEnabled = BluetoothPrinterService.isBluetoothEnabled(context)
        pairedDevices = if (hasConnectPerm) {
            BluetoothPrinterConnectionManager.getPairedDevicesSafe(context)
        } else {
            emptyList()
        }
    }

    val connectPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshDevices()
    }

    val scanPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshDevices()
        if (results.values.all { it }) {
            isScanning = true
            discoveredDevices = emptyList()
            BluetoothDiscoveryManager.startDiscovery(
                context = context,
                onDeviceFound = { printer ->
                    if (discoveredDevices.none { it.address.equals(printer.address, ignoreCase = true) }) {
                        discoveredDevices = discoveredDevices + printer
                    }
                },
                onFinished = {
                    isScanning = false
                    refreshDevices()
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            BluetoothDiscoveryManager.stopDiscovery(context)
        }
    }

    Dialog(onDismissRequest = {
        BluetoothDiscoveryManager.stopDiscovery(context)
        onDismiss()
    }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CheckoutPrinterCardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CheckoutPrinterBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, tint = PrinterBlue, modifier = Modifier.size(22.dp))
                        Text(
                            text = Bilingual.tr(lang, "ربط طابعة البلوتوث الحرارية", "Connexion Imprimante Thermique BT", "Bluetooth Thermal Printer"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            BluetoothDiscoveryManager.stopDiscovery(context)
                            onDismiss()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Bilingual.tr(lang, "عرض الورق:", "Papier :", "Paper:"),
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    listOf(58, 80).forEach { mm ->
                        val selected = paperWidth == mm
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updatePrinterPaperWidth(mm) },
                            label = { Text("${mm}mm", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("checkout_bt_paper_${mm}_chip"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                val connectedState = btState as? BtPrinterConnectionState.Connected
                if (connectedState != null) {
                    Surface(
                        color = PrinterGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PrinterGreen.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.BluetoothConnected, contentDescription = null, tint = PrinterGreen)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(connectedState.device.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(connectedState.device.address, color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.disconnectBluetoothPrinter { _, _ ->
                                            feedbackMsg = Bilingual.tr(lang, "تم قطع الاتصال بالطابعة", "Imprimante déconnectée", "Printer disconnected")
                                        }
                                    },
                                    border = BorderStroke(1.dp, PrinterRed.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("checkout_bt_disconnect_button")
                                ) {
                                    Text(
                                        text = Bilingual.tr(lang, "قطع الاتصال", "Déconnecter", "Disconnect"),
                                        color = PrinterRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.printTestBluetoothPage(context) { _, msg ->
                                            feedbackMsg = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrinterGreen),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("checkout_bt_test_print_button")
                                ) {
                                    Text(Bilingual.tr(lang, "اختبار", "Tester", "Test Print"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (!hasConnectPerm) {
                    Button(
                        onClick = {
                            val perms = BluetoothPrinterConnectionManager.requiredConnectPermissions()
                            if (perms.isNotEmpty()) connectPermLauncher.launch(perms.toTypedArray()) else refreshDevices()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrinterAmber),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            Bilingual.tr(lang, "منح إذن البلوتوث", "Autoriser le Bluetooth", "Grant Bluetooth Permission"),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (!btEnabled) {
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrinterAmber),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.BluetoothDisabled, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            Bilingual.tr(lang, "البلوتوث متوقف - افتح الإعدادات", "Bluetooth désactivé - Ouvrir paramètres", "Bluetooth Off - Open Settings"),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { refreshDevices() },
                        border = BorderStroke(1.dp, CheckoutPrinterBorder),
                        modifier = Modifier.weight(0.8f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Bilingual.tr(lang, "تحديث", "Actualiser", "Refresh"), color = Color.White, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val perms = BluetoothPrinterConnectionManager.requiredDiscoveryPermissions()
                            if (!hasScanPerm && perms.isNotEmpty()) {
                                scanPermLauncher.launch(perms.toTypedArray())
                            } else if (isScanning) {
                                BluetoothDiscoveryManager.stopDiscovery(context)
                                isScanning = false
                            } else {
                                isScanning = true
                                discoveredDevices = emptyList()
                                BluetoothDiscoveryManager.startDiscovery(
                                    context = context,
                                    onDeviceFound = { printer ->
                                        if (discoveredDevices.none { it.address.equals(printer.address, ignoreCase = true) }) {
                                            discoveredDevices = discoveredDevices + printer
                                        }
                                    },
                                    onFinished = {
                                        isScanning = false
                                        refreshDevices()
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 48.dp)
                            .testTag("checkout_bt_scan_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Bilingual.tr(lang, "إيقاف البحث", "Arrêter", "Stop Scan"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Bilingual.tr(lang, "بحث عن طابعات", "Rechercher", "Scan Nearby"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 120.dp, max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pairedDevices.isEmpty() && discoveredDevices.isEmpty()) {
                        item {
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "لا توجد طابعات بلوتوث مقترنة. تأكد من تشغيل الطابعة أو اضغط بحث عن طابعات.",
                                    "Aucune imprimante Bluetooth appairée trouvée.",
                                    "No paired Bluetooth printers found. Tap Scan Nearby or pair in Android Settings."
                                ),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    items(pairedDevices, key = { it.address }) { dev ->
                        val isSaved = dev.address.equals(savedMac, ignoreCase = true) || dev.name.equals(savedName, ignoreCase = true)
                        val isConnectedDev = connectedState?.device?.address.equals(dev.address, ignoreCase = true)
                        CheckoutPairedPrinterItemRow(
                            device = dev,
                            isSaved = isSaved,
                            isConnectedDev = isConnectedDev,
                            btState = btState,
                            lang = lang,
                            onClick = {
                                feedbackMsg = null
                                viewModel.connectBluetoothPrinter(context, dev) { ok, msg ->
                                    feedbackMsg = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (ok) onConnectedAndReady?.invoke(dev)
                                }
                            }
                        )
                    }

                    items(
                        discoveredDevices.filter { disc ->
                            pairedDevices.none { it.address.equals(disc.address, ignoreCase = true) }
                        },
                        key = { "disc_${it.address}" }
                    ) { disc ->
                        val asDeviceInfo = PrinterDeviceInfo(disc.name, disc.address, disc.isBonded)
                        CheckoutDiscoveredPrinterItemRow(
                            printer = disc,
                            lang = lang,
                            onClick = {
                                if (!disc.isBonded) BluetoothDiscoveryManager.pairDevice(disc.address, context)
                                viewModel.connectBluetoothPrinter(context, asDeviceInfo) { ok, msg ->
                                    feedbackMsg = msg
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (ok) onConnectedAndReady?.invoke(asDeviceInfo)
                                }
                            }
                        )
                    }
                }

                feedbackMsg?.takeIf { it.isNotBlank() }?.let { msgText ->
                    Text(
                        text = msgText,
                        color = PrinterBlue,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
