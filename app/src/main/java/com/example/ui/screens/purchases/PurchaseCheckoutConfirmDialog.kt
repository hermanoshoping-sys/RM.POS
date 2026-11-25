package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PurchaseEntity
import com.example.data.PurchaseItemEntity
import com.example.data.SupplierEntity
import com.example.domain.FinancialCalculator
import com.example.domain.PosOperationResult
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import com.example.util.cents

@Composable
internal fun PurchaseCheckoutConfirmDialog(
    selectedSupplier: SupplierEntity?,
    purchaseCart: List<PurchaseCartItem>,
    totalAmountCents: Long,
    totalAmount: Double,
    initialPaidStr: String,
    cyanPrimary: Color,
    lang: String,
    viewModel: PosViewModel,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val isReturnInvoice = totalAmountCents < 0L
    val accentColor = if (isReturnInvoice) Color(0xFFEF4444) else cyanPrimary
    var paidAmountStr by remember(initialPaidStr) { mutableStateOf(initialPaidStr) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, accentColor),
            modifier = Modifier.fillMaxWidth(0.94f).padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isReturnInvoice) {
                            if (lang == "AR") "تأكيد فاتورة استرجاع للمورد (-)" else "Confirmation Retour Fournisseur (-)"
                        } else {
                            if (lang == "AR") "تأكيد وتسجيل فاتورة المشتريات" else "Confirmation Facture d'Achat"
                        },
                        color = if (isReturnInvoice) Color(0xFFFCA5A5) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.Gray)
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (lang == "AR") "المورد:" else "Fournisseur :", color = Color.Gray, fontSize = 13.sp)
                    Text(selectedSupplier?.name ?: if (lang == "AR") "مورد عام" else "Fournisseur Comptant", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (lang == "AR") "عدد الأصناف:" else "Articles :", color = Color.Gray, fontSize = 13.sp)
                    Text(if (lang == "AR") "${purchaseCart.size} منتجات" else "${purchaseCart.size} produits", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Surface(
                    color = if (isReturnInvoice) Color(0xFF450A0A) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = if (isReturnInvoice) BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isReturnInvoice) {
                                if (lang == "AR") "إجمالي الاسترجاع:" else "Total Retour :"
                            } else {
                                if (lang == "AR") "المجموع الإجمالي:" else "Total Général :"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = MoneyUtils.format(totalAmount, currency),
                            color = if (isReturnInvoice) Color(0xFFF87171) else Color(0xFF38BDF8),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isReturnInvoice) {
                            if (lang == "AR") "المبلغ المسترد نقداً من المورد ($currency):" else "Montant remboursé en espèces ($currency) :"
                        } else {
                            if (lang == "AR") "المبلغ المدفوع للمورد حالياً ($currency):" else "Montant payé au fournisseur ($currency) :"
                        },
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = paidAmountStr,
                        onValueChange = { raw ->
                            val normalized = raw.replace(',', '.').replace('−', '-').replace('–', '-')
                            val sb = StringBuilder()
                            var hasDot = false
                            for (ch in normalized) {
                                when {
                                    ch == '-' && sb.isEmpty() && isReturnInvoice -> sb.append('-')
                                    ch.isDigit() -> sb.append(ch)
                                    ch == '.' && !hasDot -> {
                                        sb.append('.')
                                        hasDot = true
                                    }
                                }
                            }
                            paidAmountStr = sb.toString()
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isReturnInvoice) KeyboardType.Text else KeyboardType.Decimal
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purchase_checkout_paid_input")
                    )

                    if (isReturnInvoice) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { paidAmountStr = "0.00" },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6EE7B7)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("purchase_return_credit_debt_btn")
                            ) {
                                Text(
                                    text = if (lang == "AR") "خصم من دين المورد" else "Déduire dette",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    paidAmountStr = String.format(java.util.Locale.US, "%.2f", totalAmount)
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("purchase_return_cash_refund_btn")
                            ) {
                                Text(
                                    text = if (lang == "AR") "استرجاع نقدي كامل" else "Remb. espèces",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                val rawPaidVal = paidAmountStr.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
                val effectivePaidVal = if (isReturnInvoice && rawPaidVal > 0.0) -rawPaidVal else rawPaidVal
                val paymentSummary = FinancialCalculator.computePaymentSettlement(totalAmountCents, effectivePaidVal.cents)
                val remainDebt = paymentSummary.remainingDebt

                if (remainDebt > 0) {
                    Surface(
                        color = Color(0xFF7F1D1D).copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (lang == "AR") "المتبقي كدين على المتجر:" else "Reste dû au fournisseur :", color = Color.White, fontSize = 13.sp)
                            Text(MoneyUtils.format(remainDebt, currency), color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                if (paymentSummary.supplierCreditCents > 0L) {
                    Surface(
                        color = Color(0xFF064E3B).copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purchase_supplier_credit_notice")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (lang == "AR") "سيُخصم من دين المورد:" else "Déduit de la dette fournisseur :",
                                color = Color(0xFFA7F3D0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = MoneyUtils.format(paymentSummary.supplierCreditCents, currency),
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val sup = selectedSupplier ?: SupplierEntity(
                            name = if (lang == "AR") "مورد عام / شراء نقدي" else "Fournisseur Comptant",
                            phone = "-",
                            email = "",
                            address = "",
                            debtBalance = 0.0
                        )
                        val purchase = PurchaseEntity(
                            timestamp = System.currentTimeMillis(),
                            supplierId = sup.id,
                            supplierName = sup.name,
                            totalAmount = totalAmountCents,
                            paidAmount = paymentSummary.paidCents
                        )
                        val pItems = purchaseCart.map {
                            PurchaseItemEntity(
                                purchaseId = 0,
                                productId = it.product.id,
                                productName = it.product.name,
                                quantity = it.quantity,
                                unitPrice = it.costPrice.cents
                            )
                        }
                        viewModel.recordSupplierInvoiceBulk(purchase, pItems) { res ->
                            when (res) {
                                is PosOperationResult.Success -> {
                                    val fallbackMsg = if (isReturnInvoice) {
                                        if (lang == "AR") "تم تسجيل استرجاع المورد بنجاح وتحديث المخزون!" else "Retour fournisseur enregistré !"
                                    } else {
                                        if (lang == "AR") "تم تسجيل فاتورة الشراء بنجاح وتحديث المخزون!" else "Facture enregistrée!"
                                    }
                                    Toast.makeText(
                                        context,
                                        res.message.ifBlank { fallbackMsg },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onSuccess()
                                }
                                is PosOperationResult.Failure -> Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReturnInvoice) Color(0xFFDC2626) else cyanPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("purchases_finalize_confirm_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isReturnInvoice) {
                            if (lang == "AR") "تأكيد استرجاع المورد" else "Confirmer le Retour"
                        } else {
                            if (lang == "AR") "تأكيد وتسجيل الفاتورة" else "Confirmer et Enregistrer"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun PurchaseQuickAddSupplierDialog(
    lang: String,
    cyanPrimary: Color,
    onSave: (SupplierEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var newSupName by remember { mutableStateOf("") }
    var newSupPhone by remember { mutableStateOf("") }
    var newSupEmail by remember { mutableStateOf("") }
    var newSupAddress by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
            modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (lang == "AR") "إضافة مورد جديد للفاتورة" else "Nouveau Fournisseur",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                OutlinedTextField(
                    value = newSupName,
                    onValueChange = { newSupName = it },
                    label = { Text(if (lang == "AR") "اسم المورد *" else "Nom *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = newSupPhone,
                    onValueChange = { newSupPhone = it },
                    label = { Text(if (lang == "AR") "رقم الهاتف" else "Téléphone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = newSupEmail,
                    onValueChange = { newSupEmail = it },
                    label = { Text(if (lang == "AR") "البريد الإلكتروني" else "Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = newSupAddress,
                    onValueChange = { newSupAddress = it },
                    label = { Text(if (lang == "AR") "العنوان" else "Adresse") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(if (lang == "AR") "إلغاء" else "Annuler", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newSupName.isNotBlank()) {
                                onSave(
                                    SupplierEntity(
                                        name = newSupName.trim(),
                                        phone = newSupPhone.trim(),
                                        email = newSupEmail.trim(),
                                        address = newSupAddress.trim(),
                                        debtBalance = 0.0
                                    )
                                )
                            }
                        },
                        enabled = newSupName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = cyanPrimary)
                    ) {
                        Text(if (lang == "AR") "حفظ وبدء الفاتورة" else "Valider")
                    }
                }
            }
        }
    }
}
