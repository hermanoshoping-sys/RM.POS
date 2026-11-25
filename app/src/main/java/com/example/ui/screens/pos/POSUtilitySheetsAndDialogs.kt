package com.example.ui.screens.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSDanger
import com.example.ui.theme.POSPrimary
import com.example.ui.theme.POSSuccess
import com.example.ui.viewmodels.PosViewModel
import java.util.Locale

@Composable
fun DiscountDialog(viewModel: PosViewModel, lang: String, onDismiss: () -> Unit) {
    val currentVal by viewModel.globalDiscountValue.collectAsStateWithLifecycle()
    val currentType by viewModel.globalDiscountType.collectAsStateWithLifecycle()
    val storeCurrency by viewModel.currency.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(currentType) }
    var discountInput by remember {
        mutableStateOf(
            if (currentVal > 0) String.format(Locale.US, "%.2f", currentVal).trimEnd('0').trimEnd('.') else ""
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = Bilingual.tr(lang, "إضافة خصم على الفاتورة", "Ajouter une remise", "Add Invoice Discount"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == "PERCENT",
                        onClick = { selectedType = "PERCENT" },
                        label = { Text(Bilingual.tr(lang, "نسبة مئوية (%)", "Pourcentage (%)", "Percentage (%)"), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = POSPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedType == "FIXED",
                        onClick = { selectedType = "FIXED" },
                        label = { Text(Bilingual.tr(lang, "مبلغ نقدي ($storeCurrency)", "Montant fixe ($storeCurrency)", "Fixed ($storeCurrency)"), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = POSPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = discountInput,
                    onValueChange = { discountInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = {
                        Text(
                            text = if (selectedType == "PERCENT") {
                                Bilingual.tr(lang, "نسبة الخصم (%)", "Remise (%)", "Discount (%)")
                            } else {
                                Bilingual.tr(lang, "مبلغ الخصم ($storeCurrency)", "Montant remise ($storeCurrency)", "Discount ($storeCurrency)")
                            },
                            color = Color.Gray
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = POSPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentVal > 0) {
                        TextButton(
                            onClick = {
                                viewModel.globalDiscountValue.value = 0.0
                                onDismiss()
                            }
                        ) {
                            Text(Bilingual.tr(lang, "مسح الخصم", "Supprimer", "Clear Discount"), color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"), color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                val d = discountInput.toDoubleOrNull() ?: 0.0
                                viewModel.globalDiscountType.value = selectedType
                                viewModel.globalDiscountValue.value = d.coerceAtLeast(0.0)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(Bilingual.tr(lang, "تطبيق", "Appliquer", "Apply"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuspendedSalesDialog(viewModel: PosViewModel, lang: String, onDismiss: () -> Unit) {
    val suspended by viewModel.suspendedSales.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(Bilingual.tr(lang, "فواتير انتظار", "En attente", "Suspended Sales"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                if (suspended.isEmpty()) {
                    Text(
                        Bilingual.tr(lang, "لا توجد فواتير", "Aucune facture", "No suspended invoices"),
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(suspended) { index, s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(s.description.ifEmpty { "فاتورة ${index + 1}" }, fontWeight = FontWeight.Bold)
                                    Text("${s.items.size} " + Bilingual.tr(lang, "عناصر", "articles", "items"), color = Color.Gray, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        viewModel.recallSuspendedSale(index)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = POSPrimary)
                                ) {
                                    Text(Bilingual.tr(lang, "استرجاع", "Reprendre", "Recall"))
                                }
                            }
                            if (index < suspended.size - 1) HorizontalDivider(color = Color(0xFF1E293B))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(Bilingual.tr(lang, "إغلاق", "Fermer", "Close"), color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorSheet(viewModel: PosViewModel, lang: String, onDismiss: () -> Unit) {
    val input by viewModel.calcInput.collectAsStateWithLifecycle()
    val output by viewModel.calcOutput.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(320.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(Bilingual.get("calc_title", lang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(input, color = Color.LightGray, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            Text(output, color = POSSuccess, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val keys = listOf(
                        listOf("7", "8", "9", "/"),
                        listOf("4", "5", "6", "*"),
                        listOf("1", "2", "3", "-"),
                        listOf("0", "C", "⌫", "+"),
                        listOf("=")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        keys.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { k ->
                                    Button(
                                        onClick = { viewModel.onCalcKey(k) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = when (k) {
                                                "=" -> POSPrimary
                                                "C", "⌫" -> POSDanger
                                                "+", "-", "*", "/" -> Color(0xFF475569)
                                                else -> Color(0xFF334155)
                                            }
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .weight(if (k == "=") 4f else 1f)
                                            .height(45.dp)
                                    ) {
                                        Text(k, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckoutSummaryRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Surface(
            color = color.copy(alpha = 0.2f),
            shape = RoundedCornerShape(8.dp),
            contentColor = color
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun PaymentMethodBtn(mCode: String, mLabel: String, activeCode: String, modifier: Modifier, onClick: (String) -> Unit) {
    val active = mCode == activeCode
    Button(
        onClick = { onClick(mCode) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) POSPrimary else Color(0xFF334155),
            contentColor = if (active) Color.White else Color.LightGray
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(50.dp)
    ) {
        Text(mLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
