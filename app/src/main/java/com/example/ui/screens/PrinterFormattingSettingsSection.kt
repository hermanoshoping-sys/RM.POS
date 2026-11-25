package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.printer.ReceiptTextDirection
import com.example.printer.ThermalFontSize
import com.example.ui.theme.Bilingual

@Composable
internal fun ThermalFormattingSettingsSection(
    fontSize: ThermalFontSize,
    textDirection: ReceiptTextDirection,
    rasterEnabled: Boolean,
    showLogo: Boolean,
    receiptHeader: String,
    receiptFooter: String,
    onFontSizeChange: (ThermalFontSize) -> Unit,
    onDirectionChange: (ReceiptTextDirection) -> Unit,
    onRasterToggle: (Boolean) -> Unit,
    onSaveHeaderFooterLogo: (header: String, footer: String, showLogo: Boolean) -> Unit,
    lang: String
) {
    var headerDraft by remember(receiptHeader) { mutableStateOf(receiptHeader) }
    var footerDraft by remember(receiptFooter) { mutableStateOf(receiptFooter) }
    var logoDraft by remember(showLogo) { mutableStateOf(showLogo) }

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.FormatSize, contentDescription = null, tint = PrinterAccentCyan, modifier = Modifier.size(18.dp))
                Text(
                    text = Bilingual.tr(
                        lang,
                        "تنسيق الوصل (الخط، الاتجاه، الشعار، الترويسة والتذييل)",
                        "Mise en forme du ticket (police, sens, logo, en-tête/pied)",
                        "Receipt Formatting (Font, Direction, Logo, Header/Footer)"
                    ),
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Bilingual.tr(lang, "حجم الخط:", "Taille police:", "Font size:"),
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.5.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple(ThermalFontSize.SMALL, Bilingual.tr(lang, "صغير", "Petit", "Small"), "printer_font_small"),
                        Triple(ThermalFontSize.NORMAL, Bilingual.tr(lang, "عادي", "Normal", "Normal"), "printer_font_normal"),
                        Triple(ThermalFontSize.LARGE, Bilingual.tr(lang, "كبير", "Grand", "Large"), "printer_font_large")
                    ).forEach { (sizeVal, label, tag) ->
                        val selected = fontSize == sizeVal
                        Surface(
                            onClick = { onFontSizeChange(sizeVal) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) PrinterAccentBlue.copy(alpha = 0.25f) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (selected) PrinterAccentCyan else PrinterBorderLuminous),
                            modifier = Modifier.heightIn(min = 40.dp).testTag(tag)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = if (selected) PrinterAccentCyan else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
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
                    text = Bilingual.tr(lang, "اتجاه الكتابة:", "Direction:", "Direction:"),
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.5.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple(ReceiptTextDirection.AUTO, Bilingual.tr(lang, "تلقائي", "Auto", "Auto"), "printer_dir_auto"),
                        Triple(ReceiptTextDirection.RTL, "AR (RTL)", "printer_dir_rtl"),
                        Triple(ReceiptTextDirection.LTR, "FR (LTR)", "printer_dir_ltr")
                    ).forEach { (dirVal, label, tag) ->
                        val selected = textDirection == dirVal
                        Surface(
                            onClick = { onDirectionChange(dirVal) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) PrinterAccentBlue.copy(alpha = 0.25f) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (selected) PrinterAccentCyan else PrinterBorderLuminous),
                            modifier = Modifier.heightIn(min = 40.dp).testTag(tag)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = if (selected) PrinterAccentCyan else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
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
                    text = Bilingual.tr(lang, "طباعة الشعار أعلى الوصل", "Imprimer le logo en haut", "Print logo on receipt"),
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = logoDraft,
                    onCheckedChange = {
                        logoDraft = it
                        onSaveHeaderFooterLogo(headerDraft, footerDraft, it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrinterAccentBlue),
                    modifier = Modifier.testTag("printer_show_logo_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "طباعة عربية عالية الدقة (Raster ESC/POS)",
                        "Mode graphique arabe haute précision (Raster)",
                        "High-clarity Arabic / Logo raster mode"
                    ),
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = rasterEnabled,
                    onCheckedChange = onRasterToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrinterAccentEmerald),
                    modifier = Modifier.testTag("printer_raster_mode_switch")
                )
            }

            OutlinedTextField(
                value = headerDraft,
                onValueChange = { headerDraft = it },
                label = { Text(Bilingual.tr(lang, "ترويسة الوصل (Header)", "En-tête du ticket", "Receipt Header"), fontSize = 11.sp) },
                singleLine = false,
                maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = PrinterAccentCyan,
                    unfocusedBorderColor = PrinterBorderLuminous,
                    focusedLabelColor = PrinterAccentCyan,
                    unfocusedLabelColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier.fillMaxWidth().testTag("printer_header_input")
            )

            OutlinedTextField(
                value = footerDraft,
                onValueChange = { footerDraft = it },
                label = { Text(Bilingual.tr(lang, "تذييل الوصل (Footer)", "Pied de page du ticket", "Receipt Footer"), fontSize = 11.sp) },
                singleLine = false,
                maxLines = 2,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = PrinterAccentCyan,
                    unfocusedBorderColor = PrinterBorderLuminous,
                    focusedLabelColor = PrinterAccentCyan,
                    unfocusedLabelColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier.fillMaxWidth().testTag("printer_footer_input")
            )

            Button(
                onClick = { onSaveHeaderFooterLogo(headerDraft, footerDraft, logoDraft) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("printer_save_format_button")
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = Bilingual.tr(lang, "حفظ إعدادات الترويسة والتذييل", "Enregistrer en-tête et pied", "Save Header & Footer"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
