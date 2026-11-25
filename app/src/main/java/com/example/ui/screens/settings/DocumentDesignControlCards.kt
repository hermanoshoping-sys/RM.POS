package com.example.ui.screens.settings

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Bilingual
import com.example.util.DocumentDesignHelper

@Composable
internal fun LogoDimensionsAndQualityCard(
    lang: String,
    logoBitmap: Bitmap?,
    logoWidth: Float,
    onLogoWidthChange: (Float) -> Unit,
    logoHeight: Float,
    onLogoHeightChange: (Float) -> Unit,
    logoQuality: Float,
    onLogoQualityChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Bilingual.tr(lang, "أبعاد وجودة الشعار", "Dimensions & Qualité du Logo", "Logo Dimensions & Quality"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
                Surface(
                    color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${logoWidth.toInt()}×${logoHeight.toInt()} • ${logoQuality.toInt()}%",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Live scaled logo preview
            val previewW = (logoWidth * 0.85f).coerceIn(36f, 240f)
            val previewH = (logoHeight * 0.85f).coerceIn(28f, 124f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap.asImageBitmap(),
                        contentDescription = Bilingual.tr(lang, "معاينة الشعار", "Aperçu du logo", "Logo Preview"),
                        modifier = Modifier
                            .width(previewW.dp)
                            .height(previewH.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(previewW.dp)
                            .height(previewH.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Storefront, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                            Text(Bilingual.tr(lang, "معاينة الشعار", "Aperçu du logo", "Logo Preview"), color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Width Slider
            DesignSliderRow(
                label = Bilingual.tr(lang, "عرض الشعار", "Largeur du Logo", "Logo Width"),
                valueText = "${logoWidth.toInt()} " + Bilingual.tr(lang, "نقطة", "pt", "pt"),
                value = logoWidth,
                onValueChange = onLogoWidthChange,
                valueRange = 40f..320f,
                accentColor = Color(0xFF38BDF8),
                testTag = "logo_width_slider"
            )

            // Height Slider
            DesignSliderRow(
                label = Bilingual.tr(lang, "ارتفاع الشعار", "Hauteur du Logo", "Logo Height"),
                valueText = "${logoHeight.toInt()} " + Bilingual.tr(lang, "نقطة", "pt", "pt"),
                value = logoHeight,
                onValueChange = onLogoHeightChange,
                valueRange = 30f..220f,
                accentColor = Color(0xFF10B981),
                testTag = "logo_height_slider"
            )

            // Quality Slider
            val qualityBadge = when {
                logoQuality >= 95f -> Bilingual.tr(lang, "فائقة", "Ultra", "Ultra")
                logoQuality >= 80f -> Bilingual.tr(lang, "عالية", "Haute", "High")
                logoQuality >= 60f -> Bilingual.tr(lang, "متوسطة", "Moyenne", "Medium")
                else -> Bilingual.tr(lang, "خفيفة", "Légère", "Light")
            }
            DesignSliderRow(
                label = Bilingual.tr(lang, "جودة طباعة الشعار", "Qualité du Logo", "Logo Print Quality"),
                valueText = "${logoQuality.toInt()}% ($qualityBadge)",
                value = logoQuality,
                onValueChange = onLogoQualityChange,
                valueRange = 40f..100f,
                accentColor = Color(0xFFF59E0B),
                testTag = "logo_quality_slider"
            )

            // Quick Size Presets
            Text(Bilingual.tr(lang, "أبعاد سريعة:", "Tailles rapides :", "Quick Size Presets:"), color = Color.Gray, fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(Bilingual.tr(lang, "صغير", "Petit", "Small"), 70f, 45f),
                    Triple(Bilingual.tr(lang, "قياسي", "Standard", "Standard"), 110f, 70f),
                    Triple(Bilingual.tr(lang, "كبير", "Grand", "Large"), 160f, 100f),
                    Triple(Bilingual.tr(lang, "عريض", "Large+", "Wide"), 220f, 75f)
                ).forEach { (label, w, h) ->
                    val selected = logoWidth.toInt() == w.toInt() && logoHeight.toInt() == h.toInt()
                    OutlinedButton(
                        onClick = {
                            onLogoWidthChange(w)
                            onLogoHeightChange(h)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selected) Color(0xFF38BDF8) else Color(0xFF334155))
                    ) {
                        Text(label, fontSize = 11.sp, color = if (selected) Color(0xFF38BDF8) else Color.White, maxLines = 1)
                    }
                }
            }

            // Quick Quality Presets
            Text(Bilingual.tr(lang, "جودة سريعة:", "Qualité rapide :", "Quick Quality:"), color = Color.Gray, fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair(Bilingual.tr(lang, "50% خفيفة", "50%", "50%"), 50f),
                    Pair(Bilingual.tr(lang, "75% متوسطة", "75%", "75%"), 75f),
                    Pair(Bilingual.tr(lang, "90% عالية", "90%", "90%"), 90f),
                    Pair(Bilingual.tr(lang, "100% فائقة", "100%", "100%"), 100f)
                ).forEach { (label, q) ->
                    val selected = logoQuality.toInt() == q.toInt()
                    OutlinedButton(
                        onClick = { onLogoQualityChange(q) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selected) Color(0xFFF59E0B) else Color(0xFF334155))
                    ) {
                        Text(label, fontSize = 10.5.sp, color = if (selected) Color(0xFFF59E0B) else Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DocumentFontSizeCard(
    lang: String,
    documentFontSize: Float,
    onDocumentFontSizeChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FormatSize, contentDescription = null, tint = Color(0xFFA78BFA))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Bilingual.tr(lang, "حجم خط الوثائق والفواتير", "Taille de police des documents", "Document Font Size"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
                Surface(
                    color = Color(0xFFA78BFA).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${documentFontSize.toInt()} pt",
                        color = Color(0xFFC4B5FD),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Live font preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = Bilingual.tr(lang, "معاينة خط الفاتورة والوثائق", "Aperçu du texte de facture", "Invoice & Document Preview"),
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold,
                        fontSize = documentFontSize.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Bilingual.tr(lang, "المنتج التجريبي × 2  =  150.00", "Article test × 2  =  150.00", "Sample Item × 2  =  150.00"),
                        color = Color(0xFF334155),
                        fontSize = (documentFontSize * 0.92f).coerceIn(8f, 20f).sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            DesignSliderRow(
                label = Bilingual.tr(lang, "حجم الخط الأساسي", "Taille de base", "Base Font Size"),
                valueText = "${documentFontSize.toInt()} pt",
                value = documentFontSize,
                onValueChange = onDocumentFontSizeChange,
                valueRange = 8f..20f,
                accentColor = Color(0xFFA78BFA),
                testTag = "document_font_size_slider"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Pair(Bilingual.tr(lang, "صغير", "Petit", "Small"), 10f),
                    Pair(Bilingual.tr(lang, "قياسي", "Normal", "Normal"), 12f),
                    Pair(Bilingual.tr(lang, "متوسط", "Moyen", "Medium"), 14f),
                    Pair(Bilingual.tr(lang, "كبير", "Grand", "Large"), 16f),
                    Pair(Bilingual.tr(lang, "ضخم", "XL", "XL"), 18f)
                ).forEach { (label, size) ->
                    val selected = documentFontSize.toInt() == size.toInt()
                    OutlinedButton(
                        onClick = { onDocumentFontSizeChange(size) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selected) Color(0xFFA78BFA) else Color(0xFF334155))
                    ) {
                        Text(label, fontSize = 10.5.sp, color = if (selected) Color(0xFFC4B5FD) else Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}
