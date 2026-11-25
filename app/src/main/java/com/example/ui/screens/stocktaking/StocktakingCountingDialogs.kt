package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryItemEntity
import com.example.domain.stocktaking.BarcodeScanOutcome
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils

@Composable
internal fun ExactQuantityCountDialog(
    item: InventoryItemEntity,
    liveSystemStock: Double,
    currency: String,
    lang: String,
    onDismiss: () -> Unit,
    onSave: (quantity: Double, notes: String) -> Unit,
    onClearCount: () -> Unit
) {
    var qtyInput by remember(item.id) {
        mutableStateOf(
            item.countedQuantity?.let { StocktakingCalculator.formatQuantity(it) } ?: ""
        )
    }
    var notesInput by remember(item.id) { mutableStateOf(item.notes) }

    val parsedQty = qtyInput.trim().replace(',', '.').toDoubleOrNull()
    val isValid = parsedQty != null && parsedQty.isFinite() && parsedQty >= 0.0
    val previewDiff = if (isValid) {
        StocktakingCalculator.computeDifference(parsedQty!!, liveSystemStock)
    } else null
    val previewImpactCents = previewDiff?.let {
        StocktakingCalculator.computeDiscrepancyValueCents(item.unitCostCents, it)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StocktakingPalette.CardBg,
        titleContentColor = StocktakingPalette.TextPrimary,
        textContentColor = StocktakingPalette.TextPrimary,
        modifier = Modifier.testTag("stocktaking_exact_qty_dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.productName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = StocktakingPalette.TextPrimary
                )
                Text(
                    text = "${item.barcode} • ${item.categoryName} (${item.unit})",
                    color = StocktakingPalette.TextMuted,
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = StocktakingPalette.SurfaceSubtle,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = Bilingual.tr(lang, "المخزون النظري الحالي", "Stock théorique actuel"),
                                color = StocktakingPalette.TextMuted,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${StocktakingCalculator.formatQuantity(liveSystemStock)} ${item.unit}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = StocktakingPalette.TextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = Bilingual.tr(lang, "سعر الشراء", "Prix d'achat"),
                                color = StocktakingPalette.TextMuted,
                                fontSize = 11.sp
                            )
                            Text(
                                text = MoneyUtils.format(item.unitCostCents, currency),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = StocktakingPalette.AccentBlue
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = qtyInput,
                    onValueChange = { qtyInput = it },
                    label = {
                        Text(Bilingual.tr(lang, "الكمية الفعلية المعدودة (${item.unit})", "Quantité physique (${item.unit})"))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = qtyInput.isNotBlank() && !isValid,
                    colors = stocktakingTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stocktaking_exact_qty_input")
                )

                // Quick Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { qtyInput = StocktakingCalculator.formatQuantity(liveSystemStock.coerceAtLeast(0.0)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1.3f).heightIn(min = 40.dp)
                    ) {
                        Text(Bilingual.tr(lang, "= المتوقع", "= Théor."), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { qtyInput = "0" },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(0.7f).heightIn(min = 40.dp)
                    ) {
                        Text("0", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            val base = parsedQty ?: 0.0
                            qtyInput = StocktakingCalculator.formatQuantity(base + 1.0)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(0.8f).heightIn(min = 40.dp)
                    ) {
                        Text("+1", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            val base = parsedQty ?: 0.0
                            qtyInput = StocktakingCalculator.formatQuantity(base + 10.0)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(0.9f).heightIn(min = 40.dp)
                    ) {
                        Text("+10", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Live Discrepancy Preview (strictly valued at purchasePrice)
                if (previewDiff != null && previewImpactCents != null) {
                    val diffColor = when {
                        previewDiff < -StocktakingCalculator.QUANTITY_EPSILON -> StocktakingPalette.Rose
                        previewDiff > StocktakingCalculator.QUANTITY_EPSILON -> StocktakingPalette.Emerald
                        else -> StocktakingPalette.AccentSky
                    }
                    Surface(
                        color = diffColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "الفارق: ${StocktakingCalculator.formatSignedQuantity(previewDiff)} ${item.unit}",
                                    "Écart : ${StocktakingCalculator.formatSignedQuantity(previewDiff)} ${item.unit}"
                                ),
                                color = diffColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = MoneyUtils.format(previewImpactCents, currency),
                                color = diffColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text(Bilingual.tr(lang, "ملاحظة حول الصنف (اختياري)", "Note produit (optionnel)")) },
                    maxLines = 2,
                    colors = stocktakingTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (item.countedQuantity != null) {
                    TextButton(
                        onClick = onClearCount,
                        colors = ButtonDefaults.textButtonColors(contentColor = StocktakingPalette.Rose),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Bilingual.tr(lang, "إعادة تعيين كغير معدود", "Réinitialiser comme non compté"), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isValid) onSave(parsedQty!!, notesInput) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.AccentBlue),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("stocktaking_exact_qty_save_btn")
            ) {
                Text(Bilingual.tr(lang, "حفظ الكمية", "Enregistrer"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(Bilingual.tr(lang, "إلغاء", "Annuler"))
            }
        }
    )
}

@Composable
internal fun DuplicateScanPromptDialog(
    prompt: BarcodeScanOutcome.PromptDuplicate,
    lang: String,
    onIncrement: () -> Unit,
    onReplace: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var replaceInput by remember(prompt.item.id) {
        mutableStateOf(StocktakingCalculator.formatQuantity(prompt.incrementDelta))
    }
    val parsedReplace = replaceInput.trim().replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StocktakingPalette.CardBg,
        titleContentColor = StocktakingPalette.TextPrimary,
        textContentColor = StocktakingPalette.TextPrimary,
        modifier = Modifier.testTag("stocktaking_duplicate_scan_dialog"),
        icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = StocktakingPalette.Amber) },
        title = {
            Text(
                text = Bilingual.tr(lang, "منتج ممسوح مسبقاً", "Produit déjà scanné"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = prompt.item.productName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = StocktakingPalette.TextPrimary
                )
                Text(
                    text = Bilingual.tr(
                        lang,
                        "الكمية المعدودة حالياً: ${StocktakingCalculator.formatQuantity(prompt.existingCountedQuantity)} ${prompt.item.unit}",
                        "Quantité déjà comptée : ${StocktakingCalculator.formatQuantity(prompt.existingCountedQuantity)} ${prompt.item.unit}"
                    ),
                    color = StocktakingPalette.TextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = replaceInput,
                    onValueChange = { replaceInput = it },
                    label = { Text(Bilingual.tr(lang, "قيمة الاستبدال (اختياري)", "Nouvelle valeur (remplacement)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = stocktakingTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (parsedReplace != null && parsedReplace >= 0.0) onReplace(parsedReplace)
                    },
                    enabled = parsedReplace != null && parsedReplace >= 0.0,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("duplicate_scan_replace_btn")
                ) {
                    Text(Bilingual.tr(lang, "استبدال", "Remplacer"), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onIncrement,
                    colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.Emerald),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("duplicate_scan_increment_btn")
                ) {
                    Text(
                        Bilingual.tr(
                            lang,
                            "+${StocktakingCalculator.formatQuantity(prompt.incrementDelta)} إضافة",
                            "+${StocktakingCalculator.formatQuantity(prompt.incrementDelta)} Ajouter"
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Bilingual.tr(lang, "إلغاء", "Annuler"))
            }
        }
    )
}

@Composable
internal fun NotInSessionScanDialog(
    notInSession: BarcodeScanOutcome.NotInSession,
    lang: String,
    onAddAndCount: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StocktakingPalette.CardBg,
        titleContentColor = StocktakingPalette.TextPrimary,
        textContentColor = StocktakingPalette.TextPrimary,
        modifier = Modifier.testTag("stocktaking_not_in_session_dialog"),
        icon = { Icon(Icons.Filled.AddBox, contentDescription = null, tint = StocktakingPalette.AccentBlue) },
        title = {
            Text(
                text = Bilingual.tr(lang, "منتج خارج نطاق الجرد", "Produit hors périmètre"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = Bilingual.tr(
                    lang,
                    "المنتج \"${notInSession.catalogProductName}\" (${notInSession.barcode}) موجود في المتجر لكنه غير مدرج في هذه الجلسة الجزئية. هل تريد إضافته وعدّه (+1)؟",
                    "Le produit \"${notInSession.catalogProductName}\" (${notInSession.barcode}) existe au catalogue mais ne figure pas dans cet inventaire partiel. Voulez-vous l'ajouter et compter (+1) ?"
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onAddAndCount,
                colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.AccentBlue),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("not_in_session_add_btn")
            ) {
                Text(Bilingual.tr(lang, "إضافة وعدّ (+1)", "Ajouter (+1)"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Bilingual.tr(lang, "تجاهل", "Ignorer"))
            }
        }
    )
}

@Composable
internal fun DuplicateScanModeSelectorDialog(
    currentMode: DuplicateScanMode,
    lang: String,
    onSelect: (DuplicateScanMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StocktakingPalette.CardBg,
        titleContentColor = StocktakingPalette.TextPrimary,
        textContentColor = StocktakingPalette.TextPrimary,
        title = {
            Text(
                text = Bilingual.tr(lang, "سلوك تكرار مسح الباركود", "Mode de scan répété"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DuplicateScanMode.entries.forEach { mode ->
                    val selected = mode == currentMode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) StocktakingPalette.SelectedCardBg else StocktakingPalette.SurfaceSubtle,
                        border = BorderStroke(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) StocktakingPalette.AccentBlue else StocktakingPalette.CardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(selected = selected, onClick = { onSelect(mode) })
                            Text(
                                text = duplicateScanModeLabel(mode, lang),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = StocktakingPalette.TextPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Bilingual.tr(lang, "إغلاق", "Fermer"))
            }
        }
    )
}
