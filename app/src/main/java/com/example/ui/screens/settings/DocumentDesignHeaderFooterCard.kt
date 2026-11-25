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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Bilingual
import com.example.util.DocumentDesignHelper

@Composable
internal fun HeaderFooterDesignCard(
    lang: String,
    title: String,
    accentColor: Color,
    icon: ImageVector,
    contentType: String,
    onContentTypeChange: (String) -> Unit,
    textValue: String,
    onTextValueChange: (String) -> Unit,
    textFieldLabel: String,
    heightValue: Float,
    onHeightChange: (Float) -> Unit,
    widthValue: Float,
    onWidthChange: (Float) -> Unit,
    fontSizeValue: Float? = null,
    onFontSizeChange: ((Float) -> Unit)? = null,
    imageBitmap: Bitmap?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    tagPrefix: String
) {
    val normalizedMode = DocumentDesignHelper.normalizeContentType(contentType)
    val showsText = normalizedMode == DocumentDesignHelper.MODE_TEXT || normalizedMode == DocumentDesignHelper.MODE_BOTH
    val showsImage = normalizedMode == DocumentDesignHelper.MODE_IMAGE || normalizedMode == DocumentDesignHelper.MODE_BOTH

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
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
                    Icon(icon, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
                if (normalizedMode != DocumentDesignHelper.MODE_NONE) {
                    Surface(
                        color = accentColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${widthValue.toInt()}×${heightValue.toInt()} pt",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = Bilingual.tr(lang, "محتوى القسم (كتابة أو صورة):", "Contenu (Texte ou Image) :", "Section Content (Text or Image):"),
                color = Color.LightGray,
                fontSize = 12.5.sp
            )

            val modeOptions = listOf(
                Triple(DocumentDesignHelper.MODE_TEXT, Bilingual.tr(lang, "كتابة", "Texte", "Text"), Icons.Filled.TextFields),
                Triple(DocumentDesignHelper.MODE_IMAGE, Bilingual.tr(lang, "صورة", "Image", "Image"), Icons.Filled.Image),
                Triple(DocumentDesignHelper.MODE_BOTH, Bilingual.tr(lang, "كلاهما", "Les deux", "Both"), Icons.Filled.AutoAwesomeMosaic),
                Triple(DocumentDesignHelper.MODE_NONE, Bilingual.tr(lang, "إخفاء", "Masquer", "None"), Icons.Filled.VisibilityOff)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                modeOptions.forEach { (modeKey, modeLabel, modeIcon) ->
                    val selected = normalizedMode == modeKey
                    Surface(
                        onClick = { onContentTypeChange(modeKey) },
                        color = if (selected) accentColor.copy(alpha = 0.22f) else Color(0xFF0F172A),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.2.dp, if (selected) accentColor else Color(0xFF334155)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("${tagPrefix}_mode_${modeKey.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = modeLabel,
                                tint = if (selected) accentColor else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = modeLabel,
                                color = if (selected) Color.White else Color.LightGray,
                                fontSize = 10.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (showsText) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = onTextValueChange,
                    label = { Text(textFieldLabel) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${tagPrefix}_text_input")
                )
            }

            if (showsImage) {
                val previewH = (heightValue * 0.7f).coerceIn(36f, 120f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap.asImageBitmap(),
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxWidth((widthValue / 538f).coerceIn(0.25f, 0.96f))
                                .height(previewH.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, accentColor, RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = Bilingual.tr(lang, "لم يتم اختيار صورة بعد", "Aucune image sélectionnée", "No image selected yet"),
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickImage,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                        border = BorderStroke(1.dp, accentColor),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("${tagPrefix}_pick_image_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(lang, "اختيار صورة", "Choisir image", "Pick Image"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (imageBitmap != null) {
                        OutlinedButton(
                            onClick = onRemoveImage,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                            border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.7f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("${tagPrefix}_remove_image_button")
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = Bilingual.tr(lang, "حذف الصورة", "Supprimer", "Remove"),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (normalizedMode != DocumentDesignHelper.MODE_NONE) {
                DesignSliderRow(
                    label = Bilingual.tr(lang, "ارتفاع القسم", "Hauteur de section", "Section Height"),
                    valueText = "${heightValue.toInt()} pt",
                    value = heightValue,
                    onValueChange = onHeightChange,
                    valueRange = 24f..220f,
                    accentColor = accentColor,
                    testTag = "${tagPrefix}_height_slider"
                )

                if (showsImage) {
                    DesignSliderRow(
                        label = Bilingual.tr(lang, "عرض الصورة", "Largeur de l'image", "Image Width"),
                        valueText = "${widthValue.toInt()} pt",
                        value = widthValue,
                        onValueChange = onWidthChange,
                        valueRange = 120f..538f,
                        accentColor = accentColor,
                        testTag = "${tagPrefix}_width_slider"
                    )
                }

                if (showsText && fontSizeValue != null && onFontSizeChange != null) {
                    DesignSliderRow(
                        label = Bilingual.tr(lang, "حجم خط النص", "Taille du texte", "Text Font Size"),
                        valueText = "${fontSizeValue.toInt()} pt",
                        value = fontSizeValue,
                        onValueChange = onFontSizeChange,
                        valueRange = 9f..26f,
                        accentColor = accentColor,
                        testTag = "${tagPrefix}_font_size_slider"
                    )
                }
            }
        }
    }
}

@Composable
internal fun DesignSliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    testTag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.LightGray, fontSize = 12.5.sp)
            Text(valueText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0xFF334155)
            )
        )
    }
}
