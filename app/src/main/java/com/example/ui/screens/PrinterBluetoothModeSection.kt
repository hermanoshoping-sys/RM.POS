package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.printer.PrinterDeviceInfo
import com.example.printer.BluetoothDiscoveryManager
import com.example.printer.DiscoveredPrinter
import com.example.ui.theme.Bilingual

internal val PrinterBgDarkNavy = Color(0xFF0F172A)
internal val PrinterBorderLuminous = Color(0xFF334155)
internal val PrinterAccentBlue = Color(0xFF2563EB)
internal val PrinterAccentCyan = Color(0xFF38BDF8)
internal val PrinterAccentEmerald = Color(0xFF10B981)
internal val PrinterAccentRed = Color(0xFFEF4444)
internal val PrinterAccentGold = Color(0xFFF59E0B)

@Composable
internal fun BluetoothModeSection(
    pairedDevices: List<PrinterDeviceInfo>,
    selectedMac: String,
    selectedName: String,
    isBluetoothEnabled: Boolean,
    onSelectDevice: (PrinterDeviceInfo) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    lang: String
) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var discoveredDevices by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val scanGranted = perms[Manifest.permission.BLUETOOTH_SCAN] ?: true
        val connectGranted = perms[Manifest.permission.BLUETOOTH_CONNECT] ?: true
        if (scanGranted && connectGranted) {
            isScanning = true
            discoveredDevices = emptyList()
            BluetoothDiscoveryManager.startDiscovery(
                context = context,
                onDeviceFound = { dev ->
                    if (discoveredDevices.none { it.address == dev.address }) {
                        discoveredDevices = discoveredDevices + dev
                    }
                },
                onFinished = {
                    isScanning = false
                }
            )
        } else {
            Toast.makeText(
                context,
                Bilingual.tr(lang, "يلزم إذن البلوتوث للبحث عن الأجهزة", "Permission Bluetooth requise pour la recherche", "Bluetooth permission required for scanning"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            BluetoothDiscoveryManager.stopDiscovery(context)
        }
    }

    fun triggerScan() {
        if (!isBluetoothEnabled) {
            Toast.makeText(
                context,
                Bilingual.tr(lang, "يرجى تشغيل البلوتوث أولاً", "Veuillez activer le Bluetooth", "Please turn on Bluetooth first"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!hasScan || !hasConnect) {
                permissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
                return
            }
        }

        if (isScanning) {
            BluetoothDiscoveryManager.stopDiscovery(context)
            isScanning = false
        } else {
            isScanning = true
            discoveredDevices = emptyList()
            BluetoothDiscoveryManager.startDiscovery(
                context = context,
                onDeviceFound = { dev ->
                    if (discoveredDevices.none { it.address == dev.address }) {
                        discoveredDevices = discoveredDevices + dev
                    }
                },
                onFinished = {
                    isScanning = false
                }
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, PrinterBorderLuminous),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Bilingual.tr(lang, "البحث المباشر عن طابعات بلوتوث", "Recherche Bluetooth directe", "Direct Bluetooth Printer Scan"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "كشف الطابعات الحرارية القريبة تلقائياً بدون مغادرة التطبيق",
                                "Détecter les imprimantes à proximité sans quitter l'app",
                                "Detect nearby thermal printers without leaving the app"
                            ),
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }

                    Button(
                        onClick = { triggerScan() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) PrinterAccentRed else PrinterAccentBlue
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Bilingual.tr(lang, "إيقاف", "Arrêter", "Stop"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Bilingual.tr(lang, "بحث الآن", "Rechercher", "Scan Now"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isScanning) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = PrinterAccentCyan,
                        trackColor = Color(0xFF334155)
                    )
                }

                if (discoveredDevices.isNotEmpty()) {
                    Text(
                        text = Bilingual.tr(lang, "الأجهزة المكتشفة حديثاً (${discoveredDevices.size}):", "Nouveaux appareils trouvés (${discoveredDevices.size}):", "Discovered devices (${discoveredDevices.size}):"),
                        color = PrinterAccentEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        discoveredDevices.forEach { dev ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0F172A),
                                border = BorderStroke(1.dp, PrinterAccentEmerald.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = dev.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "${dev.address} • ${dev.details}", color = Color.Gray, fontSize = 10.sp)
                                    }
                                    Button(
                                        onClick = {
                                            BluetoothDiscoveryManager.pairDevice(dev.address, context)
                                            onSelectDevice(PrinterDeviceInfo(dev.name, dev.address))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrinterAccentEmerald),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(Bilingual.tr(lang, "ربط واختيار", "Associer", "Pair & Select"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Bilingual.tr(lang, "الأجهزة المقترنة بالهاتف (${pairedDevices.size}):", "Appareils jumelés (${pairedDevices.size}):", "Paired devices (${pairedDevices.size}):"),
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onRefresh,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = PrinterAccentCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(Bilingual.tr(lang, "تحديث", "Actualiser", "Refresh"), color = PrinterAccentCyan, fontSize = 11.sp)
                }

                TextButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.SettingsBluetooth, contentDescription = null, tint = PrinterAccentEmerald, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(Bilingual.tr(lang, "إعدادات الهاتف", "Paramètres OS", "OS Settings"), color = PrinterAccentEmerald, fontSize = 11.sp)
                }
            }
        }

        if (!isBluetoothEnabled) {
            Surface(
                color = PrinterAccentRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, PrinterAccentRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.BluetoothDisabled, contentDescription = null, tint = PrinterAccentRed, modifier = Modifier.size(20.dp))
                    Text(
                        text = Bilingual.tr(lang, "البلوتوث متوقف على هاتفك! يرجى تشغيل البلوتوث.", "Bluetooth désactivé sur votre téléphone!", "Bluetooth is disabled on your device!"),
                        color = PrinterAccentRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (pairedDevices.isEmpty() && discoveredDevices.isEmpty()) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, PrinterBorderLuminous),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.PrintDisabled, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                    Text(
                        text = Bilingual.tr(lang, "لم يتم العثور على أجهزة مقترنة", "Aucune imprimante jumelée trouvée", "No paired printers found"),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "اضغط على زر (بحث الآن) أعلاه للبحث عن طابعات البلوتوث القريبة مباشرة.",
                            "Appuyez sur Rechercher pour détecter les imprimantes Bluetooth.",
                            "Tap Scan Now above to detect nearby Bluetooth printers."
                        ),
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pairedDevices.forEach { dev ->
                    val isChosen = (selectedMac.isNotBlank() && selectedMac == dev.address) ||
                        (selectedMac.isBlank() && selectedName.isNotBlank() && selectedName == dev.name)

                    Surface(
                        onClick = { onSelectDevice(dev) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isChosen) PrinterAccentBlue.copy(alpha = 0.2f) else Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (isChosen) PrinterAccentCyan else PrinterBorderLuminous),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isChosen) PrinterAccentBlue else Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Print,
                                        contentDescription = null,
                                        tint = if (isChosen) Color.White else Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = dev.name,
                                        color = Color.White,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(text = dev.address, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                            }

                            if (isChosen) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = Bilingual.tr(lang, "نشطة حالياً", "Active", "Active"),
                                        color = PrinterAccentEmerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = PrinterAccentEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = Bilingual.tr(lang, "اختر", "Choisir", "Select"),
                                    color = PrinterAccentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SystemModeSection(lang: String) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, PrinterBorderLuminous),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.CheckCircleOutline, contentDescription = null, tint = PrinterAccentEmerald, modifier = Modifier.size(16.dp))
                Text(
                    text = Bilingual.tr(lang, "الطباعة الشاملة عبر نظام أندرويد", "Impression universelle Android", "Android System Printing"),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = Bilingual.tr(
                    lang,
                    "يتيح هذا الوضع فتح شاشة الطباعة الافتراضية لهاتفك للطباعة على أي طابعة مدعومة (Mopria، كابل USB OTG، طابعات HP/Canon/Epson) أو حفظ الوصل كملف PDF على ذاكرة الهاتف.",
                    "Permet d'utiliser le gestionnaire d'impression Android natif pour imprimer sur n'importe quelle imprimante ou enregistrer en PDF.",
                    "Use Android's native print manager for Mopria, USB OTG printers, or saving receipts as PDF."
                ),
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}
