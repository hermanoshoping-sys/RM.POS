package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryEntity
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.InventoryStatus
import com.example.ui.theme.Bilingual

@Composable
internal fun EditInventorySessionDialog(
    inventory: InventoryEntity,
    isBusy: Boolean,
    lang: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, notes: String, scanMode: DuplicateScanMode?) -> Unit
) {
    val status = InventoryStatus.fromCode(inventory.status)
    var title by remember(inventory.id) { mutableStateOf(inventory.title) }
    var notes by remember(inventory.id) { mutableStateOf(inventory.notes) }
    var scanMode by remember(inventory.id) {
        mutableStateOf(DuplicateScanMode.fromCode(inventory.duplicateScanMode))
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = StocktakingPalette.CardBg,
        titleContentColor = StocktakingPalette.TextPrimary,
        textContentColor = StocktakingPalette.TextSecondary,
        shape = RoundedCornerShape(18.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = StocktakingPalette.AccentBlue
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Bilingual.tr(lang, "تعديل بيانات الجرد", "Modifier l'inventaire"),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = StocktakingPalette.TextPrimary
                )
                Text(
                    text = inventory.reference,
                    fontSize = 12.sp,
                    color = StocktakingPalette.TextMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!status.isEditable) {
                    Surface(
                        color = StocktakingPalette.AmberSoft,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "هذا الجرد مغلق؛ يمكنك تعديل العنوان والملاحظات فقط دون التأثير على الكميات أو المخزون.",
                                "Cet inventaire est clôturé ; seuls le titre et les notes peuvent être modifiés sans affecter le stock."
                            ),
                            color = StocktakingPalette.TextPrimary,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(Bilingual.tr(lang, "عنوان الجرد", "Titre de l'inventaire")) },
                    colors = stocktakingTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_inventory_title_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    minLines = 2,
                    maxLines = 4,
                    label = { Text(Bilingual.tr(lang, "ملاحظات", "Notes")) },
                    colors = stocktakingTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_inventory_notes_input")
                )

                if (status.isEditable) {
                    Text(
                        text = Bilingual.tr(lang, "سلوك المسح المتكرر للباركود", "Mode de scan répété"),
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DuplicateScanMode.entries.forEach { mode ->
                            val selected = scanMode == mode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) StocktakingPalette.SelectedCardBg else StocktakingPalette.SurfaceSubtle,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) StocktakingPalette.AccentBlue else StocktakingPalette.CardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { scanMode = mode }
                                    .testTag("edit_inventory_scan_mode_${mode.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { scanMode = mode }
                                    )
                                    Text(
                                        text = duplicateScanModeLabel(mode, lang),
                                        color = StocktakingPalette.TextPrimary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    onConfirm(
                        title,
                        notes,
                        if (status.isEditable) scanMode else null
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.AccentBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("edit_inventory_confirm_btn")
            ) {
                Text(
                    text = Bilingual.tr(lang, "حفظ التعديل", "Enregistrer"),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("edit_inventory_cancel_btn")
            ) {
                Text(
                    text = Bilingual.tr(lang, "إلغاء", "Annuler"),
                    color = StocktakingPalette.TextSecondary
                )
            }
        }
    )
}

@Composable
internal fun DeleteInventorySessionDialog(
    inventory: InventoryEntity,
    isBusy: Boolean,
    lang: String,
    onDismiss: () -> Unit,
    onConfirmDelete: (revertStockAdjustments: Boolean, reason: String) -> Unit
) {
    val status = InventoryStatus.fromCode(inventory.status)
    val isCompleted = status == InventoryStatus.COMPLETED
    var revertStock by remember(inventory.id) { mutableStateOf(false) }
    var reason by remember(inventory.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = StocktakingPalette.CardBg,
        titleContentColor = StocktakingPalette.TextPrimary,
        textContentColor = StocktakingPalette.TextSecondary,
        shape = RoundedCornerShape(18.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = null,
                tint = StocktakingPalette.Rose,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Bilingual.tr(lang, "حذف جلسة الجرد", "Supprimer l'inventaire"),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = StocktakingPalette.TextPrimary
                )
                Text(
                    text = "${inventory.title.ifBlank { inventory.reference }} (${inventory.reference})",
                    fontSize = 12.sp,
                    color = StocktakingPalette.TextMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = StocktakingPalette.RoseSoft,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isCompleted) {
                            Bilingual.tr(
                                lang,
                                "تنبيه: هذا الجرد مكتمل ومعتمد. حذفه سيزيل سجله نهائياً من الأرشيف.",
                                "Attention : cet inventaire est clôturé. Sa suppression effacera définitivement son archive."
                            )
                        } else {
                            Bilingual.tr(
                                lang,
                                "هل أنت متأكد من حذف جلسة الجرد هذه نهائياً؟ لا يمكن التراجع عن هذا الإجراء.",
                                "Voulez-vous vraiment supprimer définitivement cette session d'inventaire ? Cette action est irréversible."
                            )
                        },
                        color = StocktakingPalette.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (revertStock) StocktakingPalette.AmberSoft else StocktakingPalette.SurfaceSubtle,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (revertStock) StocktakingPalette.Amber else StocktakingPalette.CardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { revertStock = !revertStock }
                            .testTag("delete_inventory_revert_stock_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = revertStock,
                                onCheckedChange = { revertStock = it },
                                modifier = Modifier.testTag("delete_inventory_revert_stock_checkbox")
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Bilingual.tr(
                                        lang,
                                        "التراجع عن تسويات المخزون المطبّقة",
                                        "Annuler aussi les ajustements de stock"
                                    ),
                                    color = StocktakingPalette.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                                Text(
                                    text = Bilingual.tr(
                                        lang,
                                        "يعيد الكميات بمقدار الفارق المعاكس مع الحفاظ على المبيعات والمشتريات اللاحقة.",
                                        "Inverse chaque ajustement appliqué tout en préservant les ventes et achats ultérieurs."
                                    ),
                                    color = StocktakingPalette.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    singleLine = true,
                    label = { Text(Bilingual.tr(lang, "سبب الحذف (اختياري)", "Motif de suppression (optionnel)")) },
                    colors = stocktakingTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_inventory_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = { onConfirmDelete(revertStock, reason) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StocktakingPalette.Rose,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("delete_inventory_confirm_btn")
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = Bilingual.tr(lang, "تأكيد الحذف", "Supprimer"),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("delete_inventory_cancel_btn")
            ) {
                Text(
                    text = Bilingual.tr(lang, "إلغاء", "Annuler"),
                    color = StocktakingPalette.TextSecondary
                )
            }
        }
    )
}
