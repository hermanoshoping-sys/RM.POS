package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.printer.WifiNetworkSocketHelper
import com.example.ui.theme.Bilingual

@Composable
internal fun WifiTargetControlsCard(
    ip: String,
    port: Int,
    localIp: String?,
    isTestingConnection: Boolean,
    isPrintingTest: Boolean,
    wifiFeedbackMessage: String?,
    wifiFeedbackIsError: Boolean,
    onTestConnection: ((ip: String, port: Int) -> Unit)?,
    onTestPrintWifi: ((ip: String, port: Int) -> Unit)?,
    onFallbackAndroidPrint: (() -> Unit)?,
    lang: String
) {
    Surface(
        color = Color(0xFF0B1326),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, PrinterBorderLuminous),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = PrinterAccentCyan, modifier = Modifier.size(16.dp))
                    Text(
                        text = Bilingual.tr(lang, "طابعة الواي فاي المحددة:", "Imprimante Wi-Fi :", "Target Wi-Fi Printer:"),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = if (ip.isNotBlank()) "${ip.trim()}:$port" else Bilingual.tr(lang, "غير محدد", "Non défini", "Not set"),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onTestConnection != null) {
                    OutlinedButton(
                        onClick = { onTestConnection(ip.trim(), port) },
                        enabled = ip.isNotBlank() && !isTestingConnection && !isPrintingTest,
                        border = BorderStroke(1.dp, PrinterAccentCyan.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("wifi_test_connection_button")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(color = PrinterAccentCyan, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.NetworkCheck, contentDescription = null, tint = PrinterAccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = Bilingual.tr(lang, "فحص الاتصال", "Tester lien", "Test Link"),
                                color = PrinterAccentCyan,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (onTestPrintWifi != null) {
                    Button(
                        onClick = { onTestPrintWifi(ip.trim(), port) },
                        enabled = ip.isNotBlank() && !isPrintingTest,
                        colors = ButtonDefaults.buttonColors(containerColor = PrinterAccentEmerald),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("wifi_test_print_button")
                    ) {
                        if (isPrintingTest) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = Bilingual.tr(lang, "طباعة تجريبية Wi-Fi", "Test Wi-Fi", "Wi-Fi Test Print"),
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            val subnetMismatch = !localIp.isNullOrBlank() &&
                ip.isNotBlank() &&
                !WifiNetworkSocketHelper.isSameSubnet24(localIp, ip)
            if (subnetMismatch) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "تنبيه: الهاتف على ($localIp) والطابعة على ($ip) — اضغط «بحث في الشبكة» أو أدخل عنوان IP بنفس الشبكة.",
                        "Attention : téléphone ($localIp) et imprimante ($ip) sur des sous-réseaux différents.",
                        "Note: phone ($localIp) and printer ($ip) are on different subnets."
                    ),
                    color = PrinterAccentGold,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            if (!wifiFeedbackMessage.isNullOrBlank()) {
                val bannerColor = if (wifiFeedbackIsError) PrinterAccentRed else PrinterAccentEmerald
                Surface(
                    color = bannerColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().testTag("wifi_feedback_banner")
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (wifiFeedbackIsError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = bannerColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(text = wifiFeedbackMessage, color = Color(0xFFE2E8F0), fontSize = 11.5.sp, lineHeight = 16.sp)
                        }

                        if (wifiFeedbackIsError && onFallbackAndroidPrint != null) {
                            OutlinedButton(
                                onClick = onFallbackAndroidPrint,
                                enabled = !isPrintingTest,
                                border = BorderStroke(1.dp, PrinterAccentCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("wifi_fallback_android_print_button")
                            ) {
                                Icon(Icons.Filled.Smartphone, contentDescription = null, tint = PrinterAccentCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Bilingual.tr(
                                        lang,
                                        "طباعة تجريبية عبر طابعات أندرويد (Wi-Fi / Mopria)",
                                        "Tester via impression Android (Wi-Fi / Mopria)",
                                        "Test Print via Android Print (Wi-Fi / Mopria)"
                                    ),
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

@Composable
internal fun WifiManualIpDialog(
    manualIpInput: String,
    manualPortInput: String,
    localIp: String?,
    isPrintingTest: Boolean,
    onIpInputChange: (String) -> Unit,
    onPortInputChange: (String) -> Unit,
    onSave: (String, Int) -> Unit,
    onTestPrintWifi: ((String, Int) -> Unit)?,
    onDismiss: () -> Unit,
    lang: String
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, PrinterBorderLuminous),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = Bilingual.tr(lang, "التعيين اليدوي لعنوان الطابعة (IP)", "Configuration manuelle de l'IP", "Manual Printer IP Setup"),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = manualIpInput,
                    onValueChange = onIpInputChange,
                    label = {
                        Text(Bilingual.tr(lang, "عنوان IP للطابعة (مثل 192.168.1.100)", "Adresse IP Imprimante", "Printer IP Address"), color = Color.Gray, fontSize = 11.sp)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PrinterAccentCyan,
                        unfocusedBorderColor = PrinterBorderLuminous
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = manualPortInput,
                    onValueChange = onPortInputChange,
                    label = {
                        Text(Bilingual.tr(lang, "المنفذ (الافتراضي 9100)", "Port (Défaut 9100)", "Port (Default 9100)"), color = Color.Gray, fontSize = 11.sp)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PrinterAccentCyan,
                        unfocusedBorderColor = PrinterBorderLuminous
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (localIp != null) {
                    Text(
                        text = Bilingual.tr(lang, "IP الهاتف المتصل: $localIp", "IP du téléphone: $localIp", "Device IP: $localIp"),
                        color = PrinterAccentCyan,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"), color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (onTestPrintWifi != null) {
                        OutlinedButton(
                            onClick = {
                                val targetIp = manualIpInput.trim()
                                val targetPort = manualPortInput.toIntOrNull() ?: 9100
                                if (targetIp.isNotBlank()) {
                                    onSave(targetIp, targetPort)
                                    onDismiss()
                                    onTestPrintWifi(targetIp, targetPort)
                                }
                            },
                            enabled = manualIpInput.isNotBlank() && !isPrintingTest,
                            border = BorderStroke(1.dp, PrinterAccentEmerald),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("wifi_manual_test_print_button")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, tint = PrinterAccentEmerald, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Bilingual.tr(lang, "طباعة تجريبية", "Test", "Test"), color = PrinterAccentEmerald, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            if (manualIpInput.isNotBlank()) {
                                val targetIp = manualIpInput.trim()
                                val targetPort = manualPortInput.toIntOrNull() ?: 9100
                                onSave(targetIp, targetPort)
                                Toast.makeText(
                                    context,
                                    Bilingual.tr(
                                        lang,
                                        "تم حفظ عنوان الطابعة: $targetIp:$targetPort",
                                        "Adresse IP enregistrée : $targetIp:$targetPort",
                                        "Printer IP saved: $targetIp:$targetPort"
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrinterAccentBlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(Bilingual.tr(lang, "حفظ وتعيين", "Enregistrer", "Save & Apply"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
