package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.SupplierEntity
import com.example.domain.FinancialCalculator
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSPrimary
import com.example.util.MoneyUtils
import com.example.util.cents
import java.util.Locale

@Composable
internal fun SupplierDeleteConfirmDialog(
    supplier: SupplierEntity,
    lang: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lang == "AR") "تأكيد حذف المورد" else "Confirmer la suppression", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (lang == "AR") "هل أنت متأكد من حذف المورد (${supplier.name}) نهائياً من قاعدة البيانات؟"
                else "Voulez-vous vraiment supprimer définitivement le fournisseur (${supplier.name}) ?"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text(if (lang == "AR") "حذف نهائي" else "Supprimer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "AR") "إلغاء" else "Annuler")
            }
        }
    )
}

@Composable
internal fun SupplierPayDebtDialog(
    supplier: SupplierEntity,
    lang: String,
    currency: String = "MAD",
    onConfirmPayment: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var payAmountInput by remember(supplier.id) { mutableStateOf("") }
    val currentDebt = supplier.debtBalanceDouble
    val enteredAmt = payAmountInput.toDoubleOrNull() ?: 0.0
    val settlement = FinancialCalculator.computePaymentSettlement(supplier.debtBalance, enteredAmt.cents)
    val diff = enteredAmt - currentDebt

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
                    "${if (lang == "AR") "تسديد دين المورد" else "Paiement dette"} - ${supplier.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (lang == "AR") "الدين الحالي:" else "Dette actuelle:", fontSize = 14.sp, color = Color.Gray)
                    Text(
                        if (currentDebt < -0.001) {
                            "${if (lang == "AR") "المورد مدين لي: " else "Crédit fournisseur: "}${MoneyUtils.format(-currentDebt, currency)}"
                        } else {
                            MoneyUtils.format(currentDebt, currency)
                        },
                        color = if (currentDebt > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = payAmountInput,
                    onValueChange = { payAmountInput = it },
                    label = { Text(if (lang == "AR") "المبلغ المدفوع ($currency)" else "Montant payé ($currency)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("supplier_pay_amount_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (enteredAmt > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = if (diff > 0.001) Color(0xFF0284C7).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (diff > 0.001) {
                                Text(
                                    text = if (lang == "AR") "⚠️ تنبيه: المبلغ المدفوع (${MoneyUtils.format(enteredAmt, currency)}) أكبر من الدين!\nالفارق: ${MoneyUtils.format(diff, currency)} (المورد مدين لي بهذا المبلغ)"
                                    else "⚠️ Montant supérieur à la dette!\nDifférence: ${MoneyUtils.format(diff, currency)} (Le fournisseur vous doit cette somme)",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 17.sp
                                )
                            } else if (settlement.remainingDebtCents > 0L) {
                                Text(
                                    text = if (lang == "AR") "المتبقي بعد الدفع: ${MoneyUtils.format(settlement.remainingDebt, currency)} (أنا ما زلت مديناً للمورد)"
                                    else "Reste à payer: ${MoneyUtils.format(settlement.remainingDebt, currency)} (Vous devez encore au fournisseur)",
                                    color = Color(0xFFF59E0B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = if (lang == "AR") "تم تسديد كامل الدين تماماً (الرصيد: ${MoneyUtils.format(0.0, currency)})"
                                    else "Dette soldée en totalité (${MoneyUtils.format(0.0, currency)})",
                                    color = Color(0xFF10B981),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                        onClick = { if (enteredAmt > 0) onConfirmPayment(enteredAmt) },
                        enabled = enteredAmt > 0,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("supplier_pay_confirm_button")
                    ) {
                        Text(if (lang == "AR") "تأكيد الدفع" else "Confirmer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SupplierAddEditDialog(
    editingSupplier: SupplierEntity?,
    lang: String,
    onSave: (id: Long, name: String, phone: String, email: String, address: String, debt: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var sName by remember(editingSupplier) { mutableStateOf(editingSupplier?.name ?: "") }
    var sPhone by remember(editingSupplier) { mutableStateOf(editingSupplier?.phone ?: "") }
    var sEmail by remember(editingSupplier) { mutableStateOf(editingSupplier?.email ?: "") }
    var sAddress by remember(editingSupplier) { mutableStateOf(editingSupplier?.address ?: "") }
    var sDebt by remember(editingSupplier) { mutableStateOf(editingSupplier?.debtBalanceDouble?.toString() ?: "0.0") }
    var showSupplierMapPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(360.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (editingSupplier != null) (if (lang == "AR") "تعديل المورد" else "Modifier Fournisseur")
                    else Bilingual.get("add_supplier", lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(value = sName, onValueChange = { sName = it }, label = { Text(Bilingual.get("name_ar", lang)) }, modifier = Modifier.fillMaxWidth().testTag("supplier_form_name_input"))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = sPhone, onValueChange = { sPhone = it }, label = { Text(Bilingual.get("phone", lang)) }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = sEmail, onValueChange = { sEmail = it }, label = { Text(Bilingual.get("email", lang)) }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sAddress,
                    onValueChange = { sAddress = it },
                    label = { Text(Bilingual.get("address", lang)) },
                    trailingIcon = {
                        IconButton(onClick = { showSupplierMapPicker = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Map, contentDescription = "Pick on map", tint = Color(0xFF0284C7))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = { showSupplierMapPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7)),
                        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "AR") "إدخال العنوان من الخريطة 🗺️" else "Depuis la carte 🗺️", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = sDebt, onValueChange = { sDebt = it }, label = { Text(Bilingual.get("supplier_debt", lang)) }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(if (lang == "AR") "إلغاء" else "Annuler")
                    }
                    Button(
                        onClick = {
                            onSave(editingSupplier?.id ?: 0L, sName, sPhone, sEmail, sAddress, sDebt.toDoubleOrNull() ?: 0.0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
                        modifier = Modifier.weight(1f).testTag("supplier_form_save_button")
                    ) {
                        Text(Bilingual.get("save", lang), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showSupplierMapPicker) {
        MapAddressPickerDialog(
            initialAddress = sAddress,
            lang = lang,
            onAddressConfirmed = { addr, _, _ ->
                sAddress = addr
                showSupplierMapPicker = false
            },
            onDismiss = { showSupplierMapPicker = false }
        )
    }
}
