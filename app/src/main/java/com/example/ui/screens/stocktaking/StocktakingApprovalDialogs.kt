package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.stocktaking.InventoryApprovalPreview
import com.example.domain.stocktaking.InventoryApprovalPreviewItem
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils
import kotlin.math.abs

@Composable
internal fun StocktakingApprovalDialog(
    preview: InventoryApprovalPreview,
    currency: String,
    isBusy: Boolean,
    lang: String,
    onConfirmApprove: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember(preview.inventory.id) { mutableStateOf("") }
    var isConfirmed by remember(preview.inventory.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = StocktakingPalette.CardBg,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
                .testTag("stocktaking_approval_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = StocktakingPalette.NavyDeep,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(26.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Bilingual.tr(lang, "مراجعة واعتماد تسوية المخزون", "Validation & Ajustement du Stock"),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${preview.inventory.reference} • ${preview.inventory.title}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { if (!isBusy) onDismiss() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }

                // Scrollable Body
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Financial Impact Summary
                    item(key = "summary_box") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = StocktakingPalette.SurfaceSubtle,
                            border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    SummaryStatPill(
                                        label = Bilingual.tr(lang, "أصناف ستُسوّى", "À ajuster"),
                                        value = "${preview.itemsToAdjust.size}",
                                        color = StocktakingPalette.AccentBlue
                                    )
                                    SummaryStatPill(
                                        label = Bilingual.tr(lang, "العجز (شراء)", "Manquant"),
                                        value = MoneyUtils.format(-preview.totalShortageValueCents, currency),
                                        color = StocktakingPalette.Rose
                                    )
                                    SummaryStatPill(
                                        label = Bilingual.tr(lang, "الفائض (شراء)", "Surplus"),
                                        value = MoneyUtils.format(preview.totalSurplusValueCents, currency),
                                        color = StocktakingPalette.Emerald
                                    )
                                }
                                HorizontalDivider(color = StocktakingPalette.CardBorder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = Bilingual.tr(lang, "صافي الأثر المالي (سعر الشراء)", "Impact financier net (prix achat)"),
                                        color = StocktakingPalette.TextSecondary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                    val netColor = when {
                                        preview.netDiscrepancyValueCents < 0L -> StocktakingPalette.Rose
                                        preview.netDiscrepancyValueCents > 0L -> StocktakingPalette.Emerald
                                        else -> StocktakingPalette.AccentSky
                                    }
                                    Text(
                                        text = MoneyUtils.format(preview.netDiscrepancyValueCents, currency),
                                        color = netColor,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // Uncounted Items Warning
                    if (preview.uncountedItemsCount > 0) {
                        item(key = "uncounted_warning") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = StocktakingPalette.AmberSoft,
                                border = BorderStroke(1.dp, StocktakingPalette.Amber.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = StocktakingPalette.Amber, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = Bilingual.tr(
                                            lang,
                                            "يوجد ${preview.uncountedItemsCount} منتج غير معدود — لن يتم تعديل مخزونها أو تصفيرها عند الاعتماد.",
                                            "${preview.uncountedItemsCount} produit(s) non compté(s) — leur stock ne sera ni modifié ni remis à zéro."
                                        ),
                                        color = StocktakingPalette.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Concurrent Movement Preservation Notice
                    if (preview.concurrentMovementItemsCount > 0) {
                        item(key = "concurrent_notice") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = StocktakingPalette.SelectedCardBg,
                                border = BorderStroke(1.dp, StocktakingPalette.AccentBlue.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Sync, contentDescription = null, tint = StocktakingPalette.AccentBlue, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = Bilingual.tr(
                                            lang,
                                            "تم رصد حركات متزامنة (مبيعات/مشتريات) على ${preview.concurrentMovementItemsCount} منتج؛ سيُطبَّق الفارق النسبي بدقة دون فقدان أي عملية.",
                                            "Mouvements simultanés détectés sur ${preview.concurrentMovementItemsCount} produit(s) ; l'écart relatif sera appliqué sans écraser les ventes/achats."
                                        ),
                                        color = StocktakingPalette.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Preview Items List
                    if (preview.itemsToAdjust.isEmpty()) {
                        item(key = "no_diff_items") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = StocktakingPalette.EmeraldSoft,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StocktakingPalette.Emerald)
                                    Text(
                                        text = Bilingual.tr(
                                            lang,
                                            "جميع المنتجات المعدودة مطابقة تماماً للمخزون النظري! سيُغلق الجرد كمكتمل دون تعديل كميات.",
                                            "Tous les produits comptés correspondent exactement au stock théorique ! L'inventaire sera clôturé sans ajustement."
                                        ),
                                        color = StocktakingPalette.TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        item(key = "items_header") {
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "تفاصيل التسويات التي ستُطبّق (${preview.itemsToAdjust.size})",
                                    "Détail des ajustements à appliquer (${preview.itemsToAdjust.size})"
                                ),
                                color = StocktakingPalette.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                        items(preview.itemsToAdjust, key = { it.item.id }) { row ->
                            ApprovalPreviewRowCard(row = row, currency = currency, lang = lang)
                        }
                    }

                    // Reason & Confirmation Checkbox
                    item(key = "reason_and_confirm") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = reason,
                                onValueChange = { reason = it },
                                label = {
                                    Text(Bilingual.tr(lang, "سبب التسوية / ملاحظة المدير (اختياري)", "Motif d'ajustement / Note gérant (optionnel)"))
                                },
                                maxLines = 2,
                                colors = stocktakingTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("stocktaking_approval_reason_input")
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isConfirmed) StocktakingPalette.EmeraldSoft else StocktakingPalette.SurfaceSubtle,
                                border = BorderStroke(
                                    1.2.dp,
                                    if (isConfirmed) StocktakingPalette.Emerald else StocktakingPalette.CardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isConfirmed = !isConfirmed }
                                    .testTag("stocktaking_approval_confirm_checkbox")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isConfirmed,
                                        onCheckedChange = { isConfirmed = it }
                                    )
                                    Text(
                                        text = Bilingual.tr(
                                            lang,
                                            "أؤكد مراجعة الفوارق والموافقة النهائية على تسوية المخزون وإغلاق الجرد بشكل دائم.",
                                            "Je confirme avoir vérifié les écarts et valide définitivement l'ajustement et la clôture de cet inventaire."
                                        ),
                                        color = StocktakingPalette.TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Sticky Action Footer
                HorizontalDivider(color = StocktakingPalette.CardBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(Bilingual.tr(lang, "إلغاء", "Annuler"), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onConfirmApprove(reason) },
                        enabled = isConfirmed && !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.Emerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.5f)
                            .heightIn(min = 48.dp)
                            .testTag("stocktaking_approval_confirm_btn")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = Bilingual.tr(lang, "اعتماد وتطبيق", "Valider & Appliquer"),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatPill(label: String, value: String, color: Color) {
    Column {
        Text(text = label, color = StocktakingPalette.TextMuted, fontSize = 11.sp)
        Text(text = value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
    }
}

@Composable
private fun ApprovalPreviewRowCard(
    row: InventoryApprovalPreviewItem,
    currency: String,
    lang: String
) {
    val diff = row.item.difference
    val impactColor = if (diff < 0.0) StocktakingPalette.Rose else StocktakingPalette.Emerald
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StocktakingPalette.SurfaceSubtle,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.item.productName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = StocktakingPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = MoneyUtils.format(row.item.discrepancyValueCents, currency),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.5.sp,
                    color = impactColor
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "الحالي: ${StocktakingCalculator.formatQuantity(row.liveSystemStock)} ← الجديد: ${StocktakingCalculator.formatQuantity(row.projectedAdjustedStock)} ${row.item.unit}",
                        "Actuel: ${StocktakingCalculator.formatQuantity(row.liveSystemStock)} → Nouveau: ${StocktakingCalculator.formatQuantity(row.projectedAdjustedStock)} ${row.item.unit}"
                    ),
                    color = StocktakingPalette.TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = StocktakingCalculator.formatSignedQuantity(diff),
                    color = impactColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            if (abs(row.postCountMovementDelta) > StocktakingCalculator.QUANTITY_EPSILON) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "يشمل حركة متزامنة بعد العدّ: ${StocktakingCalculator.formatSignedQuantity(row.postCountMovementDelta)}",
                        "Inclut mouvement post-comptage : ${StocktakingCalculator.formatSignedQuantity(row.postCountMovementDelta)}"
                    ),
                    color = StocktakingPalette.AccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun StocktakingCancelDialog(
    inventoryReference: String,
    isBusy: Boolean,
    lang: String,
    onConfirmCancel: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = StocktakingPalette.CardBg,
        titleContentColor = StocktakingPalette.TextPrimary,
        textContentColor = StocktakingPalette.TextPrimary,
        modifier = Modifier.testTag("stocktaking_cancel_dialog"),
        icon = { Icon(Icons.Filled.Cancel, contentDescription = null, tint = StocktakingPalette.Rose) },
        title = {
            Text(
                text = Bilingual.tr(lang, "إلغاء جلسة الجرد", "Annuler la session d'inventaire"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "هل أنت متأكد من إلغاء الجلسة $inventoryReference؟ لن يتم تعديل أي كمية في المخزون وستصبح الجلسة للقراءة فقط.",
                        "Voulez-vous annuler la session $inventoryReference ? Aucun stock ne sera modifié et la session deviendra en lecture seule."
                    ),
                    fontSize = 13.sp,
                    color = StocktakingPalette.TextSecondary
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(Bilingual.tr(lang, "سبب الإلغاء (اختياري)", "Motif d'annulation (optionnel)")) },
                    maxLines = 2,
                    colors = stocktakingTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stocktaking_cancel_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCancel(reason) },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.Rose),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("stocktaking_cancel_confirm_btn")
            ) {
                Text(Bilingual.tr(lang, "تأكيد الإلغاء", "Confirmer l'annulation"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(Bilingual.tr(lang, "تراجع", "Retour"))
            }
        }
    )
}
