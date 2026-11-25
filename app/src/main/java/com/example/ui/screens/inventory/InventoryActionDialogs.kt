package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ProductEntity
import com.example.ui.viewmodels.PosViewModel

@Composable
internal fun InventoryStockAdjustDialog(
    prod: ProductEntity,
    lang: String,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    var adjustStockInput by remember(prod.id) { mutableStateOf(prod.currentStock.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lang == "AR") "تعديل مخزون المنتج" else "Ajuster Stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(prod.name, fontWeight = FontWeight.Bold)
                Text("${if (lang == "AR") "المخزون الحالي:" else "Stock actuel:"} ${prod.currentStock}")
                OutlinedTextField(
                    value = adjustStockInput,
                    onValueChange = { adjustStockInput = it },
                    label = { Text(if (lang == "AR") "الكمية الجديدة" else "Nouvelle quantité") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newQty = adjustStockInput.toDoubleOrNull()
                    if (newQty != null) {
                        viewModel.saveProduct(
                            id = prod.id,
                            barcode = prod.barcode,
                            name = prod.name,
                            description = prod.description,
                            categoryId = prod.categoryId,
                            categoryName = prod.categoryName,
                            unit = prod.unit,
                            purchasePrice = prod.purchasePriceDouble,
                            sellPrice = prod.sellPriceDouble,
                            wholesalePrice = prod.wholesalePriceDouble,
                            specialPrice = prod.specialPriceDouble,
                            currentStock = newQty,
                            minStock = prod.minStock
                        )
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text(if (lang == "AR") "حفظ" else "Enregistrer")
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
internal fun InventoryDeleteProductDialog(
    prod: ProductEntity,
    lang: String,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val requirePassDelete by viewModel.requirePasswordOnDelete.collectAsStateWithLifecycle()
    var deletePassEntered by remember(prod.id) { mutableStateOf("") }
    var isPassError by remember(prod.id) { mutableStateOf(false) }
    var isVerifyingDeletePass by remember(prod.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = Color(0xFFEF4444))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == "AR") "تأكيد حذف المنتج" else "Supprimer Produit")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (lang == "AR") "هل أنت متأكد من حذف المنتج \"${prod.name}\" نهائياً؟" else "Êtes-vous sûr de supprimer ce produit ?")
                if (requirePassDelete) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (lang == "AR") "🔒 ميزة الأمان مفعلة: يرجى إدخال كلمة سر الحذف:" else "Mot de passe de suppression requis :",
                        fontSize = 12.sp,
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = deletePassEntered,
                        onValueChange = {
                            deletePassEntered = it
                            isPassError = false
                        },
                        label = { Text(if (lang == "AR") "كلمة سر الحذف" else "Mot de passe") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = isPassError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isPassError) {
                        Text(if (lang == "AR") "كلمة السر غير صحيحة!" else "Mot de passe incorrect", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isVerifyingDeletePass,
                onClick = {
                    if (requirePassDelete) {
                        isVerifyingDeletePass = true
                        viewModel.verifyDeletePassword(deletePassEntered) { isValid ->
                            isVerifyingDeletePass = false
                            if (isValid) {
                                viewModel.deleteProduct(prod)
                                onDismiss()
                            } else {
                                isPassError = true
                            }
                        }
                    } else {
                        viewModel.deleteProduct(prod)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text(if (lang == "AR") "حذف" else "Supprimer")
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
internal fun InventoryBulkDeleteDialog(
    selectedProductIds: Set<Long>,
    lang: String,
    viewModel: PosViewModel,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val requirePassDelete by viewModel.requirePasswordOnDelete.collectAsStateWithLifecycle()
    var deletePassEntered by remember { mutableStateOf("") }
    var isPassError by remember { mutableStateOf(false) }
    var isVerifyingBulkPass by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == "AR") "تأكيد الحذف المتعدد للمنتجات" else "Suppression multiple", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (lang == "AR") "هل أنت متأكد من حذف ${selectedProductIds.size} منتج محدد نهائياً من قاعدة البيانات؟"
                    else "Êtes-vous sûr de supprimer les ${selectedProductIds.size} produits sélectionnés ?"
                )
                if (requirePassDelete) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (lang == "AR") "🔒 ميزة الأمان مفعلة: يرجى إدخال كلمة سر الحذف:" else "Mot de passe de suppression requis :",
                        fontSize = 12.sp,
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = deletePassEntered,
                        onValueChange = {
                            deletePassEntered = it
                            isPassError = false
                        },
                        label = { Text(if (lang == "AR") "كلمة سر الحذف" else "Mot de passe") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = isPassError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isPassError) {
                        Text(if (lang == "AR") "كلمة السر غير صحيحة!" else "Mot de passe incorrect", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isVerifyingBulkPass,
                onClick = {
                    val proceedWithDelete = {
                        viewModel.deleteProductsBatch(selectedProductIds.toList()) { count ->
                            Toast.makeText(context, if (lang == "AR") "تم حذف $count منتج بنجاح" else "$count produits supprimés", Toast.LENGTH_SHORT).show()
                            onDeleted()
                        }
                    }
                    if (!requirePassDelete) {
                        proceedWithDelete()
                    } else {
                        isVerifyingBulkPass = true
                        viewModel.verifyDeletePassword(deletePassEntered) { isValid ->
                            isVerifyingBulkPass = false
                            if (isValid) proceedWithDelete() else isPassError = true
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text(if (lang == "AR") "حذف (${selectedProductIds.size})" else "Supprimer")
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
internal fun InventoryEditPasswordDialog(
    prod: ProductEntity,
    lang: String,
    viewModel: PosViewModel,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var editPasswordInput by remember(prod.id) { mutableStateOf("") }
    var editPasswordError by remember(prod.id) { mutableStateOf(false) }
    var isVerifyingEditPass by remember(prod.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFF59E0B))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == "AR") "تأكيد كلمة سر التعديل" else "Mot de passe de modification")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (lang == "AR") "🔒 ميزة الأمان مفعلة: أدخل كلمة سر التعديل لتعديل بيانات المنتج \"${prod.name}\":" else "Veuillez entrer le mot de passe pour modifier ce produit :")
                OutlinedTextField(
                    value = editPasswordInput,
                    onValueChange = {
                        editPasswordInput = it
                        editPasswordError = false
                    },
                    label = { Text(if (lang == "AR") "كلمة سر التعديل" else "Mot de passe") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = editPasswordError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (editPasswordError) {
                    Text(if (lang == "AR") "كلمة السر غير صحيحة!" else "Mot de passe incorrect", color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isVerifyingEditPass,
                onClick = {
                    isVerifyingEditPass = true
                    viewModel.verifyEditPassword(editPasswordInput) { isValid ->
                        isVerifyingEditPass = false
                        if (isValid) onVerified() else editPasswordError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text(if (lang == "AR") "متابعة التعديل" else "Continuer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "AR") "إلغاء" else "Annuler")
            }
        }
    )
}
