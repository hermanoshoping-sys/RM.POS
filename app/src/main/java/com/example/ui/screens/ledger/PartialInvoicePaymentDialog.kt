package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
fun PartialInvoicePaymentDialog(
    isSale: Boolean,
    partyName: String,
    invoiceNumber: String,
    totalAmountDouble: Double,
    paidAmountDouble: Double,
    remainingAmountDouble: Double,
    currency: String,
    lang: String,
    onConfirm: (amount: Double, paymentMethod: String, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember(invoiceNumber, remainingAmountDouble) {
        mutableStateOf("")
    }
    var selectedMethod by remember { mutableStateOf("CASH") }
    var notesText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val parsedAmount = remember(amountText) {
        amountText.trim().replace(',', '.').toDoubleOrNull()
    }
    val remainingAfter = remember(parsedAmount, remainingAmountDouble) {
        val pay = parsedAmount ?: 0.0
        (remainingAmountDouble - pay).coerceAtLeast(0.0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFF059669).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Payments,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSale) {
                                    if (lang == "AR") "تسديد جزء من فاتورة الزبون" else "Paiement partiel (Client)"
                                } else {
                                    if (lang == "AR") "تسديد جزء من فاتورة المورد" else "Paiement partiel (Fournisseur)"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$partyName • #$invoiceNumber",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Invoice summary box
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PartialSummaryRow(
                                label = if (lang == "AR") "إجمالي الفاتورة:" else "Total facture:",
                                value = MoneyUtils.format(totalAmountDouble, currency),
                                valueColor = Color.White
                            )
                            PartialSummaryRow(
                                label = if (lang == "AR") "المسدد سابقاً:" else "Déjà payé:",
                                value = MoneyUtils.format(paidAmountDouble, currency),
                                valueColor = Color(0xFF34D399)
                            )
                            HorizontalDivider(color = Color(0xFF334155))
                            PartialSummaryRow(
                                label = if (lang == "AR") "المتبقي للتسديد:" else "Reste à payer:",
                                value = MoneyUtils.format(remainingAmountDouble, currency),
                                valueColor = Color(0xFFF87171),
                                bold = true
                            )
                        }
                    }

                    // Statement link indicator
                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isSale) {
                                    if (lang == "AR") "سيتم تسجيل هذه الدفعة تلقائياً في كشف حساب الزبون"
                                    else "Ce paiement sera enregistré dans le relevé client"
                                } else {
                                    if (lang == "AR") "سيتم تسجيل هذه الدفعة تلقائياً في كشف حساب المورد"
                                    else "Ce paiement sera enregistré dans le relevé fournisseur"
                                },
                                color = Color(0xFFBAE6FD),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Quick percentage chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            "25%" to (remainingAmountDouble * 0.25),
                            "50%" to (remainingAmountDouble * 0.50),
                            "75%" to (remainingAmountDouble * 0.75),
                            (if (lang == "AR") "الكل" else "100%") to remainingAmountDouble
                        )
                        presets.forEach { (label, value) ->
                            OutlinedButton(
                                onClick = {
                                    amountText = String.format(Locale.US, "%.2f", value)
                                    errorMessage = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF475569)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = Color(0xFFE2E8F0),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Amount input
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            errorMessage = null
                        },
                        label = {
                            Text(
                                text = if (lang == "AR") "المبلغ المراد تسديده ($currency)" else "Montant à payer ($currency)",
                                color = Color(0xFF94A3B8)
                            )
                        },
                        placeholder = {
                            Text(
                                text = String.format(Locale.US, "%.2f", remainingAmountDouble),
                                color = Color(0xFF64748B)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = errorMessage != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF34D399),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("partial_pay_amount_input")
                    )

                    if (parsedAmount != null && parsedAmount > 0.0 && parsedAmount <= remainingAmountDouble + 0.001) {
                        Text(
                            text = if (lang == "AR") {
                                "المتبقي على الفاتورة بعد هذه الدفعة: ${MoneyUtils.format(remainingAfter, currency)}"
                            } else {
                                "Reste sur la facture après paiement: ${MoneyUtils.format(remainingAfter, currency)}"
                            },
                            color = Color(0xFF34D399),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    errorMessage?.let { err ->
                        Text(
                            text = err,
                            color = Color(0xFFF87171),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Payment method selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val methods = listOf(
                            "CASH" to (if (lang == "AR") "نقداً" else "Espèces"),
                            "TRANSFER" to (if (lang == "AR") "تحويل" else "Virement"),
                            "CHECK" to (if (lang == "AR") "شيك" else "Chèque")
                        )
                        methods.forEach { (code, label) ->
                            val isSelected = selectedMethod == code
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedMethod = code },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color(0xFFCBD5E1),
                                    selectedContainerColor = Color(0xFF0284C7),
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("partial_pay_method_${code.lowercase(Locale.US)}")
                            )
                        }
                    }

                    // Optional notes
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = {
                            Text(
                                text = if (lang == "AR") "ملاحظة (اختياري)" else "Note (optionnel)",
                                color = Color(0xFF94A3B8)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("partial_pay_notes_input")
                    )

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF64748B)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("partial_pay_cancel_button")
                        ) {
                            Text(
                                text = if (lang == "AR") "إلغاء" else "Annuler",
                                color = Color(0xFFE2E8F0),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = {
                                val entered = amountText.trim().replace(',', '.').toDoubleOrNull()
                                when {
                                    entered == null || entered <= 0.0 -> {
                                        errorMessage = if (lang == "AR") "يرجى إدخال مبلغ صحيح أكبر من 0" else "Veuillez saisir un montant valide"
                                    }
                                    entered > remainingAmountDouble + 0.01 -> {
                                        errorMessage = if (lang == "AR") {
                                            "المبلغ يتجاوز المتبقي على الفاتورة (${MoneyUtils.format(remainingAmountDouble, currency)})"
                                        } else {
                                            "Le montant dépasse le reste dû (${MoneyUtils.format(remainingAmountDouble, currency)})"
                                        }
                                    }
                                    else -> {
                                        onConfirm(entered.coerceAtMost(remainingAmountDouble), selectedMethod, notesText.trim())
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                                .testTag("partial_pay_confirm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (lang == "AR") "تسجيل الدفعة" else "Enregistrer",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartialSummaryRow(
    label: String,
    value: String,
    valueColor: Color,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = if (bold) 15.sp else 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
