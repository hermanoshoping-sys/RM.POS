package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.example.data.SupplierEntity
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
internal fun EditPurchaseItemModal(
    currentItem: EditablePurchaseLineItem,
    lang: String,
    onDelete: () -> Unit,
    onSave: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var editQtyText by remember(currentItem) { mutableStateOf(String.format(Locale.US, "%.1f", currentItem.quantity)) }
    var editCostText by remember(currentItem) { mutableStateOf(String.format(Locale.US, "%.2f", currentItem.unitPrice)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(currentItem.productName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)

                val isNeg = (editQtyText.toDoubleOrNull() ?: currentItem.quantity) < 0
                Button(
                    onClick = {
                        val cur = editQtyText.toDoubleOrNull() ?: currentItem.quantity
                        val nxt = if (cur == 0.0) -1.0 else -cur
                        editQtyText = if (nxt % 1.0 == 0.0) nxt.toInt().toString() else String.format(Locale.US, "%.2f", nxt)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isNeg) Color(0xFFDC2626) else Color(0xFF00A0E9)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isNeg) {
                            if (lang == "AR") "الحالة: استرجاع للمورد (سالب -) ⚠️" else "Mode: Retour Fournisseur (Négatif -)"
                        } else {
                            if (lang == "AR") "الحالة: شراء عادي (+) | انقر للتحويل لمرتجع مورد" else "Mode: Achat (+) | Cliquer pour Retour"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedTextField(
                    value = editQtyText,
                    onValueChange = { editQtyText = it },
                    label = { Text(if (lang == "AR") "الكمية (يمكن إدخال سالب - للاسترجاع)" else "Quantité (négatif pour retour)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00A0E9),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editCostText,
                    onValueChange = { editCostText = it },
                    label = { Text(if (lang == "AR") "سعر الشراء" else "Prix d'achat", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00A0E9),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))) {
                        Text(if (lang == "AR") "حذف الصنف" else "Supprimer")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text(if (lang == "AR") "إلغاء" else "Annuler", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                val newQty = editQtyText.toDoubleOrNull() ?: currentItem.quantity
                                val newCost = editCostText.toDoubleOrNull() ?: currentItem.unitPrice
                                onSave(newQty, newCost)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A0E9))
                        ) {
                            Text(if (lang == "AR") "حفظ" else "Valider", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SwitchEditPurchaseSupplierModal(
    suppliersList: List<SupplierEntity>,
    currentSupplierId: Long,
    currentSupplierName: String,
    lang: String,
    currency: String = "",
    onConfirm: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSuppId by remember { mutableStateOf(currentSupplierId) }
    var selectedSuppName by remember { mutableStateOf(currentSupplierName) }
    var customSuppInput by remember { mutableStateOf("") }
    var suppSearch by remember { mutableStateOf("") }

    val filteredSupps = remember(suppliersList, suppSearch) {
        if (suppSearch.isBlank()) suppliersList
        else suppliersList.filter { it.name.contains(suppSearch, ignoreCase = true) || it.phone.contains(suppSearch) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (lang == "AR") "تبديل اسم المورد للفاتورة" else "Changer le fournisseur de la facture",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Text(
                    text = "${if (lang == "AR") "المورد الحالي:" else "Fournisseur actuel:"} ${if (currentSupplierName.isNotBlank()) currentSupplierName else (if (lang == "AR") "مورد عام" else "Fournisseur standard")}",
                    fontSize = 13.sp,
                    color = Color(0xFF0284C7),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = suppSearch,
                    onValueChange = { suppSearch = it },
                    placeholder = { Text(if (lang == "AR") "بحث في قائمة الموردين..." else "Rechercher fournisseur...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(items = filteredSupps, key = { it.id }, contentType = { "supplier_option" }) { s ->
                        val isSelected = selectedSuppId == s.id && customSuppInput.isBlank()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    selectedSuppId = s.id
                                    selectedSuppName = s.name
                                    customSuppInput = ""
                                },
                            color = if (isSelected) Color(0xFFE0F2FE) else Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF0284C7) else Color.LightGray)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(s.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    if (s.phone.isNotBlank()) Text(s.phone, fontSize = 11.sp, color = Color.Gray)
                                }
                                if (s.debtBalance > 0L) {
                                    Text(
                                        text = if (currency.isNotBlank()) MoneyUtils.format(s.debtBalance, currency) else String.format(Locale.US, "%.2f", s.debtBalanceDouble),
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customSuppInput,
                    onValueChange = {
                        customSuppInput = it
                        if (it.isNotBlank()) {
                            selectedSuppId = 0L
                            selectedSuppName = it
                        }
                    },
                    placeholder = { Text(if (lang == "AR") "أو أدخل اسم مورد آخر يدوياً..." else "Ou nom personnalisé...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (customSuppInput.isNotBlank()) customSuppInput.trim() else selectedSuppName
                    onConfirm(selectedSuppId, finalName)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text(if (lang == "AR") "تأكيد" else "Confirmer", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "AR") "إلغاء" else "Annuler")
            }
        }
    )
}
