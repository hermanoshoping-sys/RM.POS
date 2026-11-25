package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.printer.DiscoveredPrinter
import com.example.printer.NetworkPrinterDiscoveryManager
import com.example.printer.WifiNetworkSocketHelper
import com.example.ui.theme.Bilingual

@Composable
internal fun WifiModeSection(
    ip: String,
    port: Int,
    onIpChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    isTestingConnection: Boolean = false,
    isPrintingTest: Boolean = false,
    wifiFeedbackMessage: String? = null,
    wifiFeedbackIsError: Boolean = false,
    onTestConnection: ((ip: String, port: Int) -> Unit)? = null,
    onTestPrintWifi: ((ip: String, port: Int) -> Unit)? = null,
    onFallbackAndroidPrint: (() -> Unit)? = null,
    lang: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isScanningWifi by remember { mutableStateOf(false) }
    var discoveredWifiPrinters by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }
    var scanProgress by remember { mutableStateOf(0 to 254) }
    val localIp = remember { NetworkPrinterDiscoveryManager.getLocalWifiIpAddress(context) }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf(ip) }
    var manualPortInput by remember { mutableStateOf(port.toString()) }

    DisposableEffect(Unit) {
        onDispose {
            NetworkPrinterDiscoveryManager.stopScan(context)
        }
    }

    fun startWifiScan() {
        if (isScanningWifi) {
            NetworkPrinterDiscoveryManager.stopScan(context)
            isScanningWifi = false
        } else {
            isScanningWifi = true
            discoveredWifiPrinters = emptyList()
            NetworkPrinterDiscoveryManager.startScan(
                context = context,
                scope = coroutineScope,
                onPrinterFound = { printer ->
                    if (discoveredWifiPrinters.none { it.address == printer.address }) {
                        discoveredWifiPrinters = discoveredWifiPrinters + printer
                    }
                },
                onProgress = { current, total ->
                    scanProgress = current to total
                },
                onFinished = {
                    isScanningWifi = false
                }
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Surface(
            onClick = { startWifiScan() },
            color = Color(0xFF00A8E8),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = Bilingual.tr(lang, "بحث في الشبكة", "Recherche réseau", "Network Search"),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )

                if (isScanningWifi) {
                    Spacer(modifier = Modifier.width(12.dp))
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = Bilingual.tr(lang, "بحث في الشبكة", "Recherche sur le réseau", "Network Search"),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
            border = BorderStroke(1.5.dp, Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Color(0xFFFF0000))
                        .border(BorderStroke(1.dp, Color.Black)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Bilingual.tr(lang, "الطابعات", "Imprimantes", "Printers"),
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                if (isScanningWifi) {
                    LinearProgressIndicator(
                        progress = {
                            if (scanProgress.second > 0) scanProgress.first.toFloat() / scanProgress.second.toFloat() else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFF00A8E8),
                        trackColor = Color(0xFFE2E8F0)
                    )
                }

                val totalRows = maxOf(16, discoveredWifiPrinters.size)
                for (rowIndex in 0 until totalRows) {
                    val printer = discoveredWifiPrinters.getOrNull(rowIndex)
                    val isSelected = printer != null && ip == printer.address && port == printer.port

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .background(
                                if (isSelected) Color(0xFFBAE6FD)
                                else if (rowIndex % 2 == 1 && printer != null) Color(0xFFF1F5F9)
                                else Color.White
                            )
                            .clickable {
                                if (printer != null) {
                                    onIpChange(printer.address)
                                    onPortChange(printer.port)
                                    Toast.makeText(
                                        context,
                                        Bilingual.tr(
                                            lang,
                                            "تم اختيار الطابعة: ${printer.name} (${printer.address}:${printer.port})",
                                            "Imprimante sélectionnée : ${printer.name}",
                                            "Selected printer: ${printer.name}"
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    manualIpInput = ip
                                    manualPortInput = port.toString()
                                    showManualIpDialog = true
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (printer != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Print,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF0284C7) else Color(0xFF334155),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = printer.name,
                                        color = Color(0xFF0F172A),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${printer.address}:${printer.port}",
                                        color = Color(0xFF334155),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = Bilingual.tr(lang, "مختارة ✓", "Active ✓", "Active ✓"),
                                            color = Color(0xFF059669),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        } else if (rowIndex == 0 && discoveredWifiPrinters.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isScanningWifi) {
                                        Bilingual.tr(
                                            lang,
                                            "جاري فحص الشبكة... (${scanProgress.first}/${scanProgress.second})",
                                            "Scan en cours... (${scanProgress.first}/${scanProgress.second})",
                                            "Scanning network... (${scanProgress.first}/${scanProgress.second})"
                                        )
                                    } else {
                                        Bilingual.tr(
                                            lang,
                                            "اضغط على (بحث في الشبكة) أعلاه لاكتشاف الطابعات",
                                            "Appuyez sur Rechercher pour détecter les imprimantes",
                                            "Tap Network Search above to discover printers"
                                        )
                                    },
                                    color = if (isScanningWifi) Color(0xFF475569) else Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Color.Black, thickness = 1.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (localIp != null) {
                Text(
                    text = Bilingual.tr(lang, "IP الهاتف: $localIp", "IP tél: $localIp", "Device IP: $localIp"),
                    color = PrinterAccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            TextButton(
                onClick = {
                    manualIpInput = ip
                    manualPortInput = port.toString()
                    showManualIpDialog = true
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = PrinterAccentCyan, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = Bilingual.tr(lang, "إدخال يدوي لعنوان IP للطابعة", "Entrer IP manuellement", "Enter IP Manually"),
                    color = PrinterAccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        WifiTargetControlsCard(
            ip = ip,
            port = port,
            localIp = localIp,
            isTestingConnection = isTestingConnection,
            isPrintingTest = isPrintingTest,
            wifiFeedbackMessage = wifiFeedbackMessage,
            wifiFeedbackIsError = wifiFeedbackIsError,
            onTestConnection = onTestConnection,
            onTestPrintWifi = onTestPrintWifi,
            onFallbackAndroidPrint = onFallbackAndroidPrint,
            lang = lang
        )
    }

    if (showManualIpDialog) {
        WifiManualIpDialog(
            manualIpInput = manualIpInput,
            manualPortInput = manualPortInput,
            localIp = localIp,
            isPrintingTest = isPrintingTest,
            onIpInputChange = { manualIpInput = it },
            onPortInputChange = { manualPortInput = it },
            onSave = { targetIp, targetPort ->
                onIpChange(targetIp)
                onPortChange(targetPort)
            },
            onTestPrintWifi = onTestPrintWifi,
            onDismiss = { showManualIpDialog = false },
            lang = lang
        )
    }
}
