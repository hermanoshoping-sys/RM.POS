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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
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
import com.example.data.SupplierEntity
import com.example.domain.FinancialCalculator
import com.example.util.MoneyUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSupplierPaymentDialog(
    suppliers: List<SupplierEntity>,
    lang: String,
    currency: String = "MAD",
    onDismiss: () -> Unit,
    onConfirm: (supplierId: Long, amount: Double, method: String, notes: String, printReceipt: Boolean) -> Unit
) {
    var selectedSupplier by remember {
        mutableStateOf<SupplierEntity?>(suppliers.firstOrNull { it.debtBalance > 0 } ?: suppliers.firstOrNull())
    }
    var supplierSearch by remember { mutableStateOf("") }
    var isSupplierDropdownExpanded by remember { mutableStateOf(false) }

    var amountInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var notesInput by remember { mutableStateOf("") }
    var printAfterSave by remember { mutableStateOf(true) }

    val filteredSuppliers = remember(suppliers, supplierSearch) {
        if (supplierSearch.isBlank()) suppliers.take(80)
        else suppliers.filter { it.name.contains(supplierSearch, ignoreCase = true) || it.phone.contains(supplierSearch) }.take(80)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF3B82F6).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.LocalShipping,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (lang == "AR") "تسجيل دفعة مورد (أداء مستحقات)" else "Enregistrer Paiement Fournisseur",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    if (lang == "AR") "اختر المورد:" else "Sélectionner le Fournisseur:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = isSupplierDropdownExpanded,
                    onExpandedChange = { isSupplierDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSupplier?.name ?: (if (lang == "AR") "اختر مورداً..." else "Sélectionner..."),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSupplierDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )

                    ExposedDropdownMenu(
                        expanded = isSupplierDropdownExpanded,
                        onDismissRequest = { isSupplierDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        OutlinedTextField(
                            value = supplierSearch,
                            onValueChange = { supplierSearch = it },
                            placeholder = { Text(if (lang == "AR") "بحث بالاسم..." else "Recherche...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                        filteredSuppliers.forEach { sup ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(sup.name, color = Color.White, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = MoneyUtils.format(sup.debtBalanceDouble, currency),
                                            color = if (sup.debtBalance > 0L) Color(0xFFF87171) else Color(0xFF34D399),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                onClick = {
                                    selectedSupplier = sup
                                    isSupplierDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                val currentSupplier = selectedSupplier
                if (currentSupplier != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
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
                                if (lang == "AR") "الدين المستحق للمورد:" else "Dette due au fournisseur:",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Text(
                                text = MoneyUtils.format(currentSupplier.debtBalanceDouble, currency),
                                color = if (currentSupplier.debtBalance > 0L) Color(0xFFEF4444) else Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    if (lang == "AR") "المبلغ المسدد ($currency):" else "Montant Payé ($currency):",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("0.00", color = Color(0xFF64748B)) },
                    trailingIcon = {
                        val supForFull = selectedSupplier
                        if (supForFull != null && supForFull.debtBalance > 0L) {
                            TextButton(
                                onClick = {
                                    amountInput = String.format(Locale.US, "%.2f", supForFull.debtBalanceDouble)
                                }
                            ) {
                                Text(
                                    if (lang == "AR") "كامل المستحق" else "Tout régler",
                                    color = Color(0xFF60A5FA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val enteredSupplierAmt = amountInput.toDoubleOrNull() ?: 0.0
                if (enteredSupplierAmt > 0.0 && currentSupplier != null) {
                    val preview = FinancialCalculator.computeDebtPaymentPreview(currentSupplier.debtBalance, enteredSupplierAmt)
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = if (preview.isOverpayment) Color(0xFF0284C7).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (preview.isOverpayment) Color(0xFF0284C7).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            when {
                                preview.isOverpayment -> {
                                    Text(
                                        text = if (lang == "AR") "⚠️ تنبيه: المبلغ المسدد أكبر من دين المورد!" else "⚠️ Montant supérieur à la dette!",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val overFmt = MoneyUtils.format(preview.overpaymentAmount, currency)
                                    Text(
                                        text = if (lang == "AR") "الفارق: +$overFmt (فائض / المورد مدين لك بهذا المبلغ)" else "Différence: +$overFmt (Crédit fournisseur)",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                preview.isPartialPayment -> {
                                    val remFmt = MoneyUtils.format(preview.remainingDebtAfter, currency)
                                    Text(
                                        text = if (lang == "AR") "المتبقي من دين المورد بعد التسديد: $remFmt" else "Reste à payer: $remFmt",
                                        color = Color(0xFFF59E0B),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                else -> {
                                    val zeroFmt = MoneyUtils.format(0.0, currency)
                                    Text(
                                        text = if (lang == "AR") "✅ سيتم تصفية مستحقات المورد بالكامل ($zeroFmt)" else "✅ Dette totalement réglée ($zeroFmt)",
                                        color = Color(0xFF10B981),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    if (lang == "AR") "طريقة الأداء:" else "Mode de Paiement:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "CASH" to (if (lang == "AR") "نقداً" else "Espèces"),
                        "CHECK" to (if (lang == "AR") "شيك" else "Chèque"),
                        "TRANSFER" to (if (lang == "AR") "تحويل" else "Virement"),
                        "TRAITE" to (if (lang == "AR") "كمبيالة" else "Traite")
                    ).forEach { (mCode, mLabel) ->
                        FilterChip(
                            selected = paymentMethod == mCode,
                            onClick = { paymentMethod = mCode },
                            label = {
                                Text(
                                    mLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (paymentMethod == mCode) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF3B82F6),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color(0xFF94A3B8)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text(if (lang == "AR") "ملاحظات / رقم الشيك / رقم الكمبيالة" else "Notes / N° Chèque / Traite", color = Color(0xFF94A3B8)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = printAfterSave,
                        onCheckedChange = { printAfterSave = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF3B82F6))
                    )
                    Text(
                        if (lang == "AR") "طباعة سند الصرف فور الحفظ" else "Imprimer bon de décaissement",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(if (lang == "AR") "إلغاء" else "Annuler", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val sup = selectedSupplier
                            val amt = amountInput.toDoubleOrNull() ?: 0.0
                            if (sup != null && amt > 0.0) {
                                onConfirm(sup.id, amt, paymentMethod, notesInput.trim(), printAfterSave)
                            }
                        },
                        enabled = selectedSupplier != null && (amountInput.toDoubleOrNull() ?: 0.0) > 0.0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == "AR") "تأكيد وسداد" else "Confirmer Paiement", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
