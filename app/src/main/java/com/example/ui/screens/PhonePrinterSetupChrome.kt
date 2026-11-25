package com.example.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.printer.BtPrinterConnectionState
import com.example.printer.PrinterConnectionStatus
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

@Composable
internal fun PrinterSetupStatusHeader(
    serviceStatus: PrinterConnectionStatus,
    btState: BtPrinterConnectionState,
    printerConnectionType: String,
    printerIp: String,
    printerMac: String,
    printerName: String,
    lang: String
) {
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
                    .background(Brush.linearGradient(listOf(PrinterAccentBlue, Color(0xFF1D4ED8)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }

            Column {
                Text(
                    text = Bilingual.tr(lang, "إدارة الطابعات الحرارية", "Gestion Imprimante POS", "POS Thermal Printer"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = Bilingual.tr(lang, "بلوتوث، USB OTG، شبكة Wi-Fi، أو أندرويد", "Bluetooth, USB OTG, Wi-Fi ou Android", "Bluetooth, USB OTG, Wi-Fi, or Android"),
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }

        val (pillColor, pillText) = when (val st = serviceStatus) {
            is PrinterConnectionStatus.Connected ->
                PrinterAccentEmerald to Bilingual.tr(lang, "متصل (${st.device.transport.name})", "Connecté (${st.device.transport.name})", "Connected (${st.device.transport.name})")
            is PrinterConnectionStatus.Printing ->
                PrinterAccentCyan to Bilingual.tr(lang, "جاري الطباعة...", "Impression...", "Printing...")
            is PrinterConnectionStatus.Reconnecting ->
                PrinterAccentGold to Bilingual.tr(lang, "إعادة المحاولة ${st.attempt}/${st.maxAttempts}", "Reconnexion ${st.attempt}/${st.maxAttempts}", "Retry ${st.attempt}/${st.maxAttempts}")
            is PrinterConnectionStatus.Connecting ->
                PrinterAccentCyan to Bilingual.tr(lang, "جاري الاتصال", "Connexion...", "Connecting...")
            is PrinterConnectionStatus.Error ->
                PrinterAccentRed to Bilingual.tr(lang, "خطأ اتصال", "Erreur lien", "Link Error")
            else -> when {
                printerConnectionType == "BLUETOOTH" && btState is BtPrinterConnectionState.Connected ->
                    PrinterAccentEmerald to Bilingual.tr(lang, "متصل الآن", "Connecté", "Connected")
                printerConnectionType == "WIFI" && printerIp.isNotBlank() ->
                    PrinterAccentEmerald to Bilingual.tr(lang, "جاهز Wi-Fi", "Prêt Wi-Fi", "Wi-Fi Ready")
                printerConnectionType == "SYSTEM" ->
                    PrinterAccentEmerald to Bilingual.tr(lang, "جاهز", "Prêt", "Ready")
                printerMac.isNotBlank() || printerName.isNotBlank() ->
                    PrinterAccentGold to Bilingual.tr(lang, "في الانتظار", "En veille", "Standby")
                else -> Color.Gray to Bilingual.tr(lang, "غير متصل", "Déconnecté", "Disconnected")
            }
        }

        Surface(
            color = pillColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, pillColor.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(pillColor))
                Text(text = pillText, color = pillColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class PrinterTransportTabItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
internal fun PrinterTransportModeSelector(
    selectedMode: String,
    onSelectMode: (String) -> Unit,
    lang: String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val types = listOf(
            PrinterTransportTabItem("BLUETOOTH", Bilingual.tr(lang, "بلوتوث", "Bluetooth", "Bluetooth"), Icons.Filled.Bluetooth, "printer_mode_bluetooth"),
            PrinterTransportTabItem("USB", Bilingual.tr(lang, "USB OTG", "USB OTG", "USB OTG"), Icons.Filled.Usb, "printer_mode_usb"),
            PrinterTransportTabItem("WIFI", Bilingual.tr(lang, "Wi-Fi / IP", "Wi-Fi / IP", "Wi-Fi / IP"), Icons.Filled.Wifi, "printer_mode_wifi"),
            PrinterTransportTabItem("SYSTEM", Bilingual.tr(lang, "أندرويد", "Android", "Android"), Icons.Filled.Smartphone, "printer_mode_system")
        )

        types.forEach { tab ->
            val isSelected = selectedMode.equals(tab.key, ignoreCase = true)
            Surface(
                onClick = { onSelectMode(tab.key) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) PrinterAccentBlue else Color(0xFF1E293B),
                border = BorderStroke(1.dp, if (isSelected) PrinterAccentCyan.copy(alpha = 0.7f) else PrinterBorderLuminous),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag(tab.tag)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(tab.icon, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    Text(
                        text = tab.label,
                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Dialog wrapper for easy access from anywhere (e.g. Reports screen or Checkout).
 */
@Composable
fun PhonePrinterSetupDialog(
    viewModel: PosViewModel,
    lang: String = "AR",
    onDismiss: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides if (lang == "AR") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF070D1E)),
                border = BorderStroke(1.dp, Color(0xFF1E2B4D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Bilingual.tr(lang, "إعدادات ربط الطابعة الحرارية", "Configuration Imprimante POS", "Thermal Printer Setup"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    PhonePrinterSettingsCard(viewModel = viewModel, lang = lang)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = PrinterAccentBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(Bilingual.tr(lang, "حفظ وإغلاق", "Enregistrer et fermer", "Save & Close"), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
