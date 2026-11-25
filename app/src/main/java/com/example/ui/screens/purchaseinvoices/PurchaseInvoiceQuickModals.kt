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
import androidx.compose.material.icons.filled.Check
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PurchaseEntity
import com.example.data.PurchaseItemEntity
import com.example.data.SupplierEntity
import com.example.domain.FinancialCalculator
import com.example.domain.PosOperationResult
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
internal fun EditPurchaseInvoiceNumberDialog(
    target: PurchaseEntity,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var invNumText by remember { mutableStateOf(target.displayInvoiceNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (lang == "AR") "تعديل رقم فاتورة الشراء" else "Modifier le numéro de facture d'achat",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    if (lang == "AR") "أدخل رقم الفاتورة أو السند الجديد للمورد:" else "Entrez le nouveau numéro de facture / bon:",
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = invNumText,
                    onValueChange = { invNumText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = invNumText.trim()
                    viewModel.updatePurchaseInvoiceNumber(target.id, trimmed) {
                        onDismiss()
                        Toast.makeText(
                            context,
                            if (lang == "AR") "تم تعديل رقم الفاتورة بنجاح" else "Numéro de facture modifié avec succès",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text(if (lang == "AR") "حفظ" else "Enregistrer", color = Color.White)
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
internal fun SwitchPurchaseSupplierDialog(
    target: PurchaseEntity,
    suppliers: List<SupplierEntity>,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var selectedSuppId by remember { mutableStateOf(target.supplierId) }
    var selectedSuppName by remember { mutableStateOf(target.supplierName) }
    var customSuppInput by remember { mutableStateOf("") }
    var suppSearch by remember { mutableStateOf("") }

    val filteredSupps = remember(suppliers, suppSearch) {
        if (suppSearch.isBlank()) suppliers
        else suppliers.filter { it.name.contains(suppSearch, ignoreCase = true) || it.phone.contains(suppSearch) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (lang == "AR") "تبديل اسم المورد للفاتورة (#${target.displayInvoiceNumber})" else "Changer le fournisseur de la facture",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Text(
                    text = "${if (lang == "AR") "المورد الحالي:" else "Fournisseur actuel:"} ${if (target.supplierName.isNotBlank()) target.supplierName else (if (lang == "AR") "مورد عام" else "Fournisseur standard")}",
                    fontSize = 13.sp,
                    color = Color(0xFF0284C7),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = suppSearch,
                    onValueChange = { suppSearch = it },
                    placeholder = { Text(if (lang == "AR") "بحث في قائمة الموردين..." else "Rechercher fournisseur...", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(
                        items = filteredSupps,
                        key = { it.id },
                        contentType = { "supplier_option" }
                    ) { s ->
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(s.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    if (s.phone.isNotBlank()) {
                                        Text(s.phone, fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                if (s.debtBalance > 0L) {
                                    Text(
                                        MoneyUtils.format(s.debtBalanceDouble, currency),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (customSuppInput.isNotBlank()) customSuppInput.trim() else selectedSuppName
                    viewModel.updatePurchaseSupplier(target.id, selectedSuppId, finalName) { res ->
                        when (res) {
                            is PosOperationResult.Success -> {
                                onDismiss()
                                Toast.makeText(
                                    context,
                                    res.message.ifBlank { if (lang == "AR") "تم تبديل اسم المورد بنجاح" else "Fournisseur mis à jour avec succès" },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is PosOperationResult.Failure -> {
                                Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text(if (lang == "AR") "تأكيد التبديل" else "Confirmer", color = Color.White)
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
internal fun PurchaseDeleteConfirmDialog(
    target: PurchaseEntity,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lang == "AR") "تأكيد حذف فاتورة الشراء" else "Confirmer la suppression", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (lang == "AR") {
                    "هل أنت متأكد من حذف فاتورة الشراء (#${target.id})؟\nسيتم إنقاص قيمتها في حساب المورد، وإنقاص مخزون المنتجات تلقائياً، وإزالتها من سجل المشتريات والمبيعات."
                } else {
                    "Voulez-vous vraiment supprimer la facture d'achat (#${target.id}) ?\nLe solde fournisseur et le stock seront réduits automatiquement."
                }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.deletePurchase(target.id) { res ->
                        when (res) {
                            is PosOperationResult.Success -> {
                                onDismiss()
                                Toast.makeText(
                                    context,
                                    res.message.ifBlank { if (lang == "AR") "تم حذف فاتورة الشراء، وإنقاص الحساب والمخزون، وإزالتها من السجل" else "Facture d'achat supprimée avec succès" },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is PosOperationResult.Failure -> {
                                onDismiss()
                                Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
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
internal fun PurchaseReturnDialog(
    target: PurchaseEntity,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var purchaseItems by remember { mutableStateOf<List<PurchaseItemEntity>>(emptyList()) }
    var selectedItemToReturn by remember { mutableStateOf<PurchaseItemEntity?>(null) }
    var returnQuantityText by remember { mutableStateOf("1") }

    LaunchedEffect(target.id) {
        purchaseItems = viewModel.getPurchaseItems(target.id)
        if (purchaseItems.isNotEmpty()) {
            selectedItemToReturn = purchaseItems.first()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "${if (lang == "AR") "تسجيل مرتجع مشتريات للمورد" else "Retour fournisseur"} - #${target.id}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    if (lang == "AR") "اختر الصنف المراد إرجاعه للمورد:" else "Sélectionnez l'article :",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (purchaseItems.isEmpty()) {
                    Text(if (lang == "AR") "جاري تحميل الأصناف..." else "Chargement...", color = Color.Gray)
                } else {
                    purchaseItems.forEach { item ->
                        val isSelected = selectedItemToReturn?.id == item.id
                        Surface(
                            color = if (isSelected) Color(0xFF2563EB).copy(alpha = 0.2f) else MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFF334155)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedItemToReturn = item
                                    returnQuantityText = "1"
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("الكمية المشتراة: ${item.quantity} | تكلفة الشراء: ${MoneyUtils.format(item.unitPriceDouble, currency)}", fontSize = 11.sp, color = Color.Gray)
                                }
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = returnQuantityText,
                    onValueChange = { returnQuantityText = it },
                    label = { Text(if (lang == "AR") "الكمية المرجعة للمورد" else "Quantité à retourner") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(if (lang == "AR") "إلغاء" else "Annuler")
                    }

                    Button(
                        onClick = {
                            val curItem = selectedItemToReturn
                            val qty = returnQuantityText.toDoubleOrNull() ?: 1.0
                            if (curItem != null && qty > 0) {
                                val returnAmt = FinancialCalculator.computeReturnAmountDouble(curItem.unitPriceDouble, qty)
                                viewModel.recordSupplierReturn(
                                    purchaseId = target.id,
                                    productId = curItem.productId,
                                    productName = curItem.productName,
                                    quantity = qty,
                                    returnAmount = returnAmt,
                                    supplierId = target.supplierId
                                ) { res ->
                                    when (res) {
                                        is PosOperationResult.Success -> {
                                            onDismiss()
                                            Toast.makeText(
                                                context,
                                                res.message.ifBlank { if (lang == "AR") "تم تسجيل مرتجع المورد وخصم المخزون بنجاح" else "Retour fournisseur enregistré avec succès" },
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        is PosOperationResult.Failure -> {
                                            Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(if (lang == "AR") "تأكيد المرتجع" else "Confirmer", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}
