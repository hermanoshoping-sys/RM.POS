package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.CustomerEntity
import com.example.data.CustomerPaymentEntity
import com.example.data.SaleEntity
import com.example.domain.FinancialCalculator
import com.example.ui.theme.POSDanger
import com.example.ui.theme.POSSuccess
import com.example.util.GeoFenceResult
import com.example.util.GeoLocationHelper
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
fun ClientDebtPaymentDialog(
    targetClient: CustomerEntity,
    lang: String,
    currency: String = "MAD",
    onDismiss: () -> Unit,
    onConfirm: (enteredAmount: Double) -> Unit
) {
    var payAmountInput by remember(targetClient.id) { mutableStateOf("") }
    val currentBalance = targetClient.balanceDouble
    val enteredAmt = payAmountInput.toDoubleOrNull() ?: 0.0
    val preview = remember(targetClient.balance, enteredAmt) {
        FinancialCalculator.computeDebtPaymentPreview(targetClient.balance, enteredAmt)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "${if (lang == "AR") "تسديد ديون" else "Règlement dette"} - ${targetClient.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (lang == "AR") "الرصيد المستحق الحالي:" else "Solde actuel:",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        MoneyUtils.format(currentBalance, currency),
                        color = if (targetClient.balance > 0L) POSDanger else POSSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = payAmountInput,
                    onValueChange = { payAmountInput = it },
                    label = { Text(if (lang == "AR") "المبلغ المدفوع ($currency)" else "Montant payé ($currency)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = POSSuccess,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (enteredAmt > 0.0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = if (preview.isOverpayment) Color(0xFF0284C7).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (preview.isOverpayment) Color(0xFF0284C7).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            when {
                                preview.isOverpayment -> {
                                    Text(
                                        text = if (lang == "AR") "⚠️ تنبيه: المبلغ أكبر من دين الزبون!" else "⚠️ Montant supérieur à la dette!",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (lang == "AR") "الفارق: +${MoneyUtils.format(preview.overpaymentAmount, currency)} (فائض / الزبون دائن بهذا المبلغ)" else "Différence: +${MoneyUtils.format(preview.overpaymentAmount, currency)} (Crédit client)",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                preview.isPartialPayment -> {
                                    Text(
                                        text = if (lang == "AR") "المتبقي من دين الزبون بعد الدفع: ${MoneyUtils.format(preview.remainingDebtAfter, currency)}" else "Reste dû: ${MoneyUtils.format(preview.remainingDebtAfter, currency)}",
                                        color = Color(0xFFF59E0B),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                else -> {
                                    Text(
                                        text = if (lang == "AR") "✅ سيتم تصفية الدين بالكامل (${MoneyUtils.format(0.0, currency)})" else "✅ Dette totalement réglée (${MoneyUtils.format(0.0, currency)})",
                                        color = Color(0xFF10B981),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(if (lang == "AR") "إلغاء" else "Annuler")
                    }
                    Button(
                        onClick = { if (enteredAmt > 0.0) onConfirm(enteredAmt) },
                        enabled = enteredAmt > 0.0,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = POSSuccess),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(if (lang == "AR") "تأكيد التسديد" else "Confirmer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ClientDeleteStatementSaleDialog(
    targetSale: SaleEntity,
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
                text = if (lang == "AR") "تأكيد حذف الفاتورة #${targetSale.invoiceNumber}" else "Confirmer la suppression #${targetSale.invoiceNumber}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (lang == "AR")
                        "هل أنت متأكد من حذف فاتورة البيع #${targetSale.invoiceNumber} بمبلغ ${MoneyUtils.format(targetSale.totalAmountDouble, currency)}؟"
                    else
                        "Êtes-vous sûr de supprimer la facture #${targetSale.invoiceNumber} d'un montant de ${MoneyUtils.format(targetSale.totalAmountDouble, currency)} ?",
                    fontSize = 13.sp
                )
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (lang == "AR")
                            "تنبيه: سيتم خصم مبلغ الفاتورة كاملاً (${MoneyUtils.format(targetSale.totalAmountDouble, currency)}) تلقائياً من دين الزبون واسترجاع المنتجات المباعة إلى المخزون."
                        else
                            "Attention: Le montant total de la facture sera déduit de la dette du client et le stock sera restauré.",
                        fontSize = 11.sp,
                        color = Color(0xFF10B981),
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
                Text(if (lang == "AR") "حذف الفاتورة" else "Supprimer", fontWeight = FontWeight.Bold)
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
fun CustomerGeoBlockedDialog(
    customerName: String,
    result: GeoFenceResult,
    lang: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📍", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (lang == "AR") "تنبيه: خارج موقع الزبون!" else "Hors zone client !",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFFDC2626)
                )
            }
        },
        text = {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = result.errorMessage ?: (
                        if (lang == "AR") "لا يمكن فتح فاتورة باسم ($customerName) لأنك غير متواجد في موقعه الجغرافي المحدد."
                        else "Impossible d'ouvrir une facture pour ($customerName) en dehors de sa zone."
                    ),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                if (result.distanceMeters > 0.0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = if (lang == "AR")
                                    "المسافة الحالية عن الزبون: ${GeoLocationHelper.formatDistance(result.distanceMeters)}"
                                else
                                    "Distance actuelle: ${GeoLocationHelper.formatDistance(result.distanceMeters)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFDC2626)
                            )
                            Text(
                                text = if (lang == "AR")
                                    "أقصى نطاق مسموح به: ${GeoLocationHelper.formatDistance(result.radiusMeters)}"
                                else
                                    "Rayon maximum toléré: ${GeoLocationHelper.formatDistance(result.radiusMeters)}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text(if (lang == "AR") "إعادة فحص الموقع" else "Réessayer")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(if (lang == "AR") "إغلاق" else "Fermer")
            }
        }
    )
}

@Composable
fun EditCustomerPaymentDialog(
    payment: CustomerPaymentEntity,
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
        Pair("OTHER", if (lang == "AR") "أخرى" else "Autre")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = Color(0xFF3B82F6))
                Text(
                    text = if (lang == "AR") "تعديل وصل تسديد #${payment.id}" else "Modifier Paiement #${payment.id}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${if (lang == "AR") "الزبون:" else "Client:"} ${payment.customerName}",
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
                    label = { Text(if (lang == "AR") "المبلغ المستلم ($currency)" else "Montant reçu ($currency)") },
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    methods.forEach { (code, label) ->
                        FilterChip(
                            selected = selectedMethod == code,
                            onClick = { selectedMethod = code },
                            label = { Text(label, fontSize = 11.sp) }
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
fun DeleteCustomerPaymentConfirmDialog(
    payment: CustomerPaymentEntity,
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
                text = if (lang == "AR") "تأكيد حذف وصل التسديد #${payment.id}" else "Confirmer la suppression #${payment.id}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (lang == "AR")
                        "هل أنت متأكد من رغبتك في حذف هذا التسديد للزبون ${payment.customerName} بمبلغ ${MoneyUtils.format(payment.amountDouble, currency)}؟"
                    else
                        "Êtes-vous sûr de vouloir supprimer ce paiement de ${MoneyUtils.format(payment.amountDouble, currency)} pour ${payment.customerName} ?",
                    fontSize = 13.sp
                )
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (lang == "AR")
                            "تنبيه: سيتم إلغاء عملية الدفع وإعادة إضافة المبلغ بالكامل إلى رصيد دين الزبون تلقائياً."
                        else
                            "Attention: Le montant sera réajouté à la dette du client automatiquement.",
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
