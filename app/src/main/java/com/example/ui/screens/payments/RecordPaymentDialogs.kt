package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.example.domain.FinancialCalculator
import com.example.util.MoneyUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCustomerPaymentDialog(
    customers: List<CustomerEntity>,
    lang: String,
    currency: String = "MAD",
    onDismiss: () -> Unit,
    onConfirm: (customerId: Long, amount: Double, method: String, notes: String, printReceipt: Boolean) -> Unit
) {
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(customers.firstOrNull { it.balance > 0 } ?: customers.firstOrNull()) }
    var customerSearch by remember { mutableStateOf("") }
    var isCustomerDropdownExpanded by remember { mutableStateOf(false) }

    var amountInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var notesInput by remember { mutableStateOf("") }
    var printAfterSave by remember { mutableStateOf(true) }

    val filteredCustomers = remember(customers, customerSearch) {
        if (customerSearch.isBlank()) customers.take(80)
        else customers.filter { it.name.contains(customerSearch, ignoreCase = true) || it.phone.contains(customerSearch) }.take(80)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp).shadow(16.dp, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp).background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (lang == "AR") "تسجيل دفعة زبون (سداد دين)" else "Enregistrer Règlement Client",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (lang == "AR") "اختر الزبون المدين:" else "Sélectionner le Client:",
                    color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = isCustomerDropdownExpanded,
                    onExpandedChange = { isCustomerDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCustomer?.name ?: (if (lang == "AR") "اختر زبوناً..." else "Sélectionner..."),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCustomerDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )

                    ExposedDropdownMenu(
                        expanded = isCustomerDropdownExpanded,
                        onDismissRequest = { isCustomerDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        OutlinedTextField(
                            value = customerSearch,
                            onValueChange = { customerSearch = it },
                            placeholder = { Text(if (lang == "AR") "بحث بالاسم..." else "Recherche...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        )
                        filteredCustomers.forEach { cust ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(cust.name, color = Color.White, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = MoneyUtils.format(cust.balanceDouble, currency),
                                            color = if (cust.balance > 0L) Color(0xFFF87171) else Color(0xFF34D399),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                onClick = { selectedCustomer = cust; isCustomerDropdownExpanded = false }
                            )
                        }
                    }
                }

                val currentCustomer = selectedCustomer
                if (currentCustomer != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (lang == "AR") "الدين الحالي على الزبون:" else "Dette actuelle du client:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(
                                text = MoneyUtils.format(currentCustomer.balanceDouble, currency),
                                color = if (currentCustomer.balance > 0L) Color(0xFFEF4444) else Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(if (lang == "AR") "المبلغ المدفوع ($currency):" else "Montant Réglé ($currency):", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("0.00", color = Color(0xFF64748B)) },
                    trailingIcon = {
                        val custForFull = selectedCustomer
                        if (custForFull != null && custForFull.balance > 0L) {
                            TextButton(onClick = { amountInput = String.format(Locale.US, "%.2f", custForFull.balanceDouble) }) {
                                Text(if (lang == "AR") "كامل الدين" else "Tout solder", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981), unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A), unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val enteredCustomerAmt = amountInput.toDoubleOrNull() ?: 0.0
                if (enteredCustomerAmt > 0.0 && currentCustomer != null) {
                    val preview = FinancialCalculator.computeDebtPaymentPreview(currentCustomer.balance, enteredCustomerAmt)
                    Spacer(modifier = Modifier.height(10.dp))
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
                                        text = if (lang == "AR") "⚠️ تنبيه: المبلغ المدفوع أكبر من دين الزبون!" else "⚠️ Montant supérieur à la dette!",
                                        color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val overFmt = MoneyUtils.format(preview.overpaymentAmount, currency)
                                    Text(
                                        text = if (lang == "AR") "الفارق: +$overFmt (فائض / الزبون دائن بهذا المبلغ)" else "Différence: +$overFmt (Crédit client)",
                                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                preview.isPartialPayment -> {
                                    val remFmt = MoneyUtils.format(preview.remainingDebtAfter, currency)
                                    Text(
                                        text = if (lang == "AR") "المتبقي من دين الزبون بعد التسديد: $remFmt" else "Reste dû: $remFmt",
                                        color = Color(0xFFF59E0B), fontSize = 13.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                else -> {
                                    val zeroFmt = MoneyUtils.format(0.0, currency)
                                    Text(
                                        text = if (lang == "AR") "✅ سيتم تصفية دين الزبون بالكامل ($zeroFmt)" else "✅ Dette totalement réglée ($zeroFmt)",
                                        color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(if (lang == "AR") "طريقة الأداء:" else "Mode de Paiement:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "CASH" to (if (lang == "AR") "نقداً" else "Espèces"),
                        "CHECK" to (if (lang == "AR") "شيك" else "Chèque"),
                        "TRANSFER" to (if (lang == "AR") "تحويل" else "Virement")
                    ).forEach { (mCode, mLabel) ->
                        FilterChip(
                            selected = paymentMethod == mCode,
                            onClick = { paymentMethod = mCode },
                            label = { Text(mLabel, fontSize = 11.sp, fontWeight = if (paymentMethod == mCode) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981), selectedLabelColor = Color.White,
                                containerColor = Color(0xFF0F172A), labelColor = Color(0xFF94A3B8)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text(if (lang == "AR") "ملاحظات / رقم الشيك / مرجع" else "Notes / N° Chèque / Réf", color = Color(0xFF94A3B8)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981), unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A), unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = printAfterSave, onCheckedChange = { printAfterSave = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981)))
                    Text(if (lang == "AR") "طباعة وصل الاستلام فور الحفظ" else "Imprimer reçu immédiatement", color = Color.White, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss, border = BorderStroke(1.dp, Color(0xFF334155)),
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text(if (lang == "AR") "إلغاء" else "Annuler", color = Color.White) }

                    Button(
                        onClick = {
                            val cust = selectedCustomer
                            val amt = amountInput.toDoubleOrNull() ?: 0.0
                            if (cust != null && amt > 0.0) onConfirm(cust.id, amt, paymentMethod, notesInput.trim(), printAfterSave)
                        },
                        enabled = selectedCustomer != null && (amountInput.toDoubleOrNull() ?: 0.0) > 0.0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == "AR") "تأكيد واستلام" else "Valider Reçu", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

