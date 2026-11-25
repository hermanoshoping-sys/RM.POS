package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.CustomerEntity
import com.example.data.UserEntity
import com.example.domain.FinancialCalculator
import com.example.util.GeoLocationHelper
import com.example.util.MoneyUtils
import kotlinx.coroutines.launch
import java.util.Locale

internal data class EditableSaleLineItem(
    val productId: Long,
    val productName: String,
    val barcode: String,
    var quantity: Double,
    var unitPrice: Double
)

@Composable
internal fun EditSaleItemModal(
    currentItem: EditableSaleLineItem,
    lang: String,
    currency: String = "MAD",
    onDelete: () -> Unit,
    onSave: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var editQtyText by remember(currentItem) { mutableStateOf(String.format(Locale.US, "%.1f", currentItem.quantity)) }
    var editPriceText by remember(currentItem) { mutableStateOf(String.format(Locale.US, "%.2f", currentItem.unitPrice)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = currentItem.productName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                val isItemNegative = (editQtyText.toDoubleOrNull() ?: currentItem.quantity) < 0
                Button(
                    onClick = {
                        val curQ = editQtyText.toDoubleOrNull() ?: currentItem.quantity
                        val toggled = if (curQ == 0.0) -1.0 else -curQ
                        editQtyText = if (toggled % 1.0 == 0.0) toggled.toInt().toString() else String.format(Locale.US, "%.2f", toggled)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isItemNegative) Color(0xFFDC2626) else Color(0xFF2563EB)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text(
                        text = if (isItemNegative) {
                            if (lang == "AR") "الحالة: استرجاع زبون (سالب -) ⚠️" else "Mode: Retour Client (Négatif -)"
                        } else {
                            if (lang == "AR") "الحالة: بيع عادي (+) | انقر للتحويل لمرتجع" else "Mode: Vente Normale (+) | Cliquer pour Retour"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
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
                        focusedBorderColor = Color(0xFF8CC63F),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val parsedQ = editQtyText.toDoubleOrNull() ?: currentItem.quantity
                val parsedP = editPriceText.toDoubleOrNull() ?: currentItem.unitPrice
                Text(
                    text = "${if (lang == "AR") "إجمالي البند: " else "Total Ligne: "}${MoneyUtils.format(FinancialCalculator.computeLineTotalDouble(parsedP, parsedQ), currency)}",
                    color = if (parsedQ < 0) Color(0xFFEF4444) else Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = editPriceText,
                    onValueChange = { editPriceText = it },
                    label = { Text(if (lang == "AR") "سعر البيع" else "Prix de vente", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8CC63F),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Text(if (lang == "AR") "حذف الصنف" else "Supprimer")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text(if (lang == "AR") "إلغاء" else "Annuler", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                val newQty = editQtyText.toDoubleOrNull() ?: currentItem.quantity
                                val newPrice = editPriceText.toDoubleOrNull() ?: currentItem.unitPrice
                                onSave(newQty, newPrice)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8CC63F))
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
internal fun EditSaleInvoiceNumberModal(
    initialNumber: String,
    lang: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempInvNum by remember { mutableStateOf(initialNumber) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (lang == "AR") "تعديل رقم الفاتورة" else "Modifier le numéro de facture",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    if (lang == "AR") "أدخل رقم الفاتورة الجديد:" else "Entrez le nouveau numéro de facture:",
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tempInvNum,
                    onValueChange = { tempInvNum = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = tempInvNum.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirm(trimmed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text(if (lang == "AR") "تأكيد" else "Valider", color = Color.White)
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
internal fun SwitchEditSaleCustomerModal(
    customersList: List<CustomerEntity>,
    currentCustomerId: Long,
    currentCustomerName: String,
    currentUser: UserEntity?,
    lang: String,
    currency: String = "MAD",
    onConfirm: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedCustId by remember { mutableStateOf(currentCustomerId) }
    var selectedCustName by remember { mutableStateOf(currentCustomerName) }
    var customNameInput by remember { mutableStateOf("") }
    var custSearch by remember { mutableStateOf("") }

    val filteredCusts = remember(customersList, custSearch) {
        if (custSearch.isBlank()) customersList
        else customersList.filter { it.name.contains(custSearch, ignoreCase = true) || it.phone.contains(custSearch) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (lang == "AR") "تبديل اسم الزبون للفاتورة" else "Changer le client de la facture",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                Text(
                    text = "${if (lang == "AR") "الزبون الحالي:" else "Client actuel:"} ${if (currentCustomerName.isNotBlank()) currentCustomerName else (if (lang == "AR") "زبون عادي" else "Client de passage")}",
                    fontSize = 13.sp,
                    color = Color(0xFF0284C7),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = custSearch,
                    onValueChange = { custSearch = it },
                    placeholder = { Text(if (lang == "AR") "بحث في قائمة الزبائن..." else "Rechercher client...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    item(key = "walk_in_cust", contentType = "walk_in") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    selectedCustId = 0L
                                    selectedCustName = if (lang == "AR") "زبون عادي" else "Client de passage"
                                    customNameInput = ""
                                },
                            color = if (selectedCustId == 0L && customNameInput.isBlank()) Color(0xFFE0F2FE) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (selectedCustId == 0L && customNameInput.isBlank()) Color(0xFF0284C7) else Color.LightGray)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (lang == "AR") "زبون عادي (بدون حساب)" else "Client de passage",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    items(
                        items = filteredCusts,
                        key = { it.id },
                        contentType = { "customer_option" }
                    ) { c ->
                        val isSelected = selectedCustId == c.id && customNameInput.isBlank()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    if (currentUser?.enforceCustomerGeoLocation == true && c.isGeoLocationRequired && c.hasGeoLocation) {
                                        coroutineScope.launch {
                                            val check = GeoLocationHelper.verifyCustomerLocationForInvoice(
                                                context = context,
                                                customer = c,
                                                currentUser = currentUser,
                                                lang = lang
                                            )
                                            if (check.isAllowed) {
                                                selectedCustId = c.id
                                                selectedCustName = c.name
                                                customNameInput = ""
                                            } else {
                                                Toast.makeText(context, check.errorMessage ?: "لا يمكن اختيار الزبون: خارج موقعه المحدد", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        selectedCustId = c.id
                                        selectedCustName = c.name
                                        customNameInput = ""
                                    }
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (c.isGeoLocationRequired && c.hasGeoLocation) {
                                            Text("📍", fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
                                        }
                                        Text(c.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    }
                                    if (c.phone.isNotBlank()) {
                                        Text(c.phone, fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                if (c.balance > 0L) {
                                    Text(
                                        MoneyUtils.format(c.balanceDouble, currency),
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
                    value = customNameInput,
                    onValueChange = {
                        customNameInput = it
                        if (it.isNotBlank()) {
                            selectedCustId = 0L
                            selectedCustName = it
                        }
                    },
                    placeholder = { Text(if (lang == "AR") "أو أدخل اسم زبون آخر يدوياً..." else "Ou nom personnalisé...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (customNameInput.isNotBlank()) customNameInput.trim() else selectedCustName
                    onConfirm(selectedCustId, finalName)
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
