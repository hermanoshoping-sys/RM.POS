package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SupplierPaymentEntity
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
fun EditSupplierPaymentDialog(
    payment: SupplierPaymentEntity,
    lang: String,
    currency: String = "MAD",
    onDismiss: () -> Unit,
    onConfirm: (newAmount: Double, newMethod: String, newNotes: String) -> Unit
) {
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", payment.amountDouble)) }
    var selectedMethod by remember { mutableStateOf(payment.paymentMethod) }
    var notesText by remember { mutableStateOf(payment.notes) }
    var isError by remember { mutableStateOf(false) }

    val methods = listOf(
        Pair("CASH", if (lang == "AR") "نقد / كاش" else "Espèces"),
        Pair("CHECK", if (lang == "AR") "شيك" else "Chèque"),
        Pair("TRANSFER", if (lang == "AR") "تحويل بنكي" else "Virement"),
        Pair("TRAITE", if (lang == "AR") "كمبيالة" else "Traite"),
        Pair("OTHER", if (lang == "AR") "أخرى" else "Autre")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = Color(0xFF3B82F6))
                Text(
                    text = if (lang == "AR") "تعديل سند تسديد #${payment.id}" else "Modifier Règlement #${payment.id}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${if (lang == "AR") "المورد:" else "Fournisseur:"} ${payment.supplierName}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.LightGray
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        isError = false
                    },
                    label = { Text(if (lang == "AR") "المبلغ المسدد ($currency)" else "Montant réglé ($currency)") },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (lang == "AR") "طريقة الدفع:" else "Mode de paiement:",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    methods.forEach { (code, label) ->
                        FilterChip(
                            selected = selectedMethod == code,
                            onClick = { selectedMethod = code },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(if (lang == "AR") "ملاحظات إضافية" else "Notes") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountText.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) {
                        onConfirm(parsed, selectedMethod, notesText)
                    } else {
                        isError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text(if (lang == "AR") "حفظ التعديل" else "Enregistrer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(if (lang == "AR") "إلغاء" else "Annuler")
            }
        }
    )
}

@Composable
fun DeleteSupplierPaymentConfirmDialog(
    payment: SupplierPaymentEntity,
    lang: String,
    currency: String = "MAD",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
        },
        title = {
            Text(
                text = if (lang == "AR") "تأكيد حذف سند التسديد #${payment.id}" else "Confirmer la suppression #${payment.id}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (lang == "AR")
                        "هل أنت متأكد من رغبتك في حذف هذا التسديد للمورد ${payment.supplierName} بمبلغ ${MoneyUtils.format(payment.amountDouble, currency)}؟"
                    else
                        "Êtes-vous sûr de vouloir supprimer ce paiement de ${MoneyUtils.format(payment.amountDouble, currency)} pour ${payment.supplierName} ?",
                    fontSize = 13.sp
                )
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (lang == "AR")
                            "تنبيه: سيتم إلغاء عملية الدفع وإعادة إضافة المبلغ بالكامل إلى رصيد دين المورد تلقائياً."
                        else
                            "Attention: Le montant sera réajouté à la dette du fournisseur automatiquement.",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text(if (lang == "AR") "تأكيد الحذف" else "Supprimer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(if (lang == "AR") "إلغاء" else "Annuler")
            }
        }
    )
}
