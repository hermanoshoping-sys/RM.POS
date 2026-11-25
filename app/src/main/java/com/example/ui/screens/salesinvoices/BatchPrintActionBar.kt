package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Bilingual

private val ActionBarNavyBg = Color(0xFF0A1224)
private val ActionBarCyan = Color(0xFF38BDF8)
private val ActionBarEmerald = Color(0xFF10B981)

/**
 * Sticky bottom batch-selection bar rendered in the Invoice History screen when multi-select mode is active.
 */
@Composable
fun BatchPrintActionBar(
    selectedCount: Int,
    totalSelectableCount: Int,
    lang: String,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onStartBatchPrint: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allSelected = totalSelectableCount > 0 && selectedCount >= totalSelectableCount
    Surface(
        color = ActionBarNavyBg,
        border = BorderStroke(1.dp, ActionBarCyan.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        tonalElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("batch_print_action_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = ActionBarCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, ActionBarCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "تم تحديد $selectedCount / $totalSelectableCount",
                                "$selectedCount / $totalSelectableCount sélectionné(s)",
                                "$selectedCount / $totalSelectableCount selected"
                            ),
                            color = ActionBarCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = if (allSelected) onClearSelection else onSelectAll,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(if (allSelected) "batch_clear_selection_button" else "batch_select_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DoneAll,
                            contentDescription = null,
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (allSelected) {
                                Bilingual.tr(lang, "إلغاء الكل", "Tout désél.", "Clear All")
                            } else {
                                Bilingual.tr(lang, "تحديد الكل", "Tout sél.", "Select All")
                            },
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = onExitSelectionMode,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("batch_exit_select_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = Bilingual.tr(lang, "إغلاق التحديد", "Fermer sélection", "Exit selection"),
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Button(
                onClick = onStartBatchPrint,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ActionBarEmerald,
                    disabledContainerColor = Color(0xFF334155)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("batch_print_start_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Print,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Bilingual.tr(
                        lang,
                        "طباعة الفواتير المحددة في قائمة الانتظار ($selectedCount)",
                        "Imprimer la file ($selectedCount factures)",
                        "Print Selected Queue ($selectedCount)"
                    ),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
