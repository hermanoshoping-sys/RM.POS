package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SupplierEntity
import com.example.domain.FinancialCalculator
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
internal fun PurchaseSupplierPickerDialog(
    suppliersList: List<SupplierEntity>,
    selectedSupplier: SupplierEntity?,
    lang: String,
    cyanPrimary: Color,
    onSelectSupplier: (SupplierEntity) -> Unit,
    onOpenAddSupplier: () -> Unit,
    onDismiss: () -> Unit
) {
    var supplierSearchQuery by remember { mutableStateOf("") }
    val filteredSuppliers = remember(suppliersList, supplierSearchQuery) {
        if (supplierSearchQuery.isBlank()) suppliersList
        else suppliersList.filter { it.name.contains(supplierSearchQuery, ignoreCase = true) || it.phone.contains(supplierSearchQuery) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
            modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "AR") "اختيار مورد الفاتورة" else "Choisir Fournisseur",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.Gray)
                    }
                }

                OutlinedTextField(
                    value = supplierSearchQuery,
                    onValueChange = { supplierSearchQuery = it },
                    placeholder = { Text(if (lang == "AR") "بحث عن مورد بالاسم أو الهاتف..." else "Recherche...", color = Color.Gray, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = cyanPrimary,
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    color = Color(0xFF10B981),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val generic = suppliersList.find { it.name.contains("نقدي") || it.name.contains("عام") }
                                ?: SupplierEntity(
                                    name = if (lang == "AR") "مورد عام / شراء نقدي" else "Fournisseur Comptant",
                                    phone = "-",
                                    email = "",
                                    address = "",
                                    debtBalance = 0.0
                                )
                            onSelectSupplier(generic)
                            onDismiss()
                        }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FlashOn, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (lang == "AR") "شراء نقدي مباشر (بدون مورد محدد)" else "Achat Comptant Direct", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(items = filteredSuppliers, key = { it.id }, contentType = { "picker_supplier" }) { sup ->
                        Surface(
                            color = if (selectedSupplier?.id == sup.id) cyanPrimary.copy(alpha = 0.2f) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selectedSupplier?.id == sup.id) cyanPrimary else Color(0xFF334155)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    onSelectSupplier(sup)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(sup.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    Text(if (sup.phone.isNotBlank()) sup.phone else "-", color = Color.Gray, fontSize = 11.sp)
                                }
                                if (selectedSupplier?.id == sup.id) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = cyanPrimary)
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onOpenAddSupplier) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "AR") "+ مورد جديد" else "+ Nouveau", color = Color(0xFF38BDF8))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(if (lang == "AR") "إغلاق" else "Fermer", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PurchaseEditCartItemDialog(
    item: PurchaseCartItem,
    lang: String,
    cyanPrimary: Color,
    currency: String = "MAD",
    onDelete: () -> Unit,
    onSave: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var editQty by remember(item) { mutableStateOf(item.quantity.toString()) }
    var editCost by remember(item) { mutableStateOf(item.costPrice.toString()) }

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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("تعديل: ${item.product.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                val isNegative = (editQty.toDoubleOrNull() ?: item.quantity) < 0
                Button(
                    onClick = {
                        val currentQ = editQty.toDoubleOrNull() ?: item.quantity
                        val newQ = if (currentQ == 0.0) -1.0 else -currentQ
                        editQty = if (newQ % 1.0 == 0.0) newQ.toInt().toString() else String.format(Locale.US, "%.2f", newQ)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isNegative) Color(0xFFDC2626) else cyanPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isNegative) {
                            if (lang == "AR") "الحالة: استرجاع للمورد (سالب -) ⚠️" else "Mode: Retour Fournisseur (Négatif -)"
                        } else {
                            if (lang == "AR") "الحالة: شراء عادي (+) | انقر للتحويل لمرتجع مورد" else "Mode: Achat (+) | Cliquer pour Retour"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedTextField(
                    value = editQty,
                    onValueChange = { editQty = it },
                    label = { Text("الكمية (يمكن إدخال سالب مثل -5 لاسترجاع للمورد)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = cyanPrimary,
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val parsedQ = editQty.toDoubleOrNull() ?: item.quantity
                val parsedCost = editCost.toDoubleOrNull() ?: item.costPrice
                val lineTotalPreview = FinancialCalculator.computeLineTotalDouble(parsedCost, parsedQ)
                Text(
                    text = "إجمالي البند: ${MoneyUtils.format(lineTotalPreview, currency)}",
                    color = if (parsedQ < 0) Color(0xFFEF4444) else Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = editCost,
                    onValueChange = { editCost = it },
                    label = { Text("سعر الشراء الفردي (التكلفة)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = cyanPrimary,
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حذف من السلة")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("إلغاء", color = Color.Gray) }
                        Button(
                            onClick = {
                                val q = editQty.toDoubleOrNull() ?: item.quantity
                                val c = editCost.toDoubleOrNull() ?: item.costPrice
                                onSave(q, c)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = cyanPrimary)
                        ) {
                            Text("حفظ التعديل")
                        }
                    }
                }
            }
        }
    }
}

