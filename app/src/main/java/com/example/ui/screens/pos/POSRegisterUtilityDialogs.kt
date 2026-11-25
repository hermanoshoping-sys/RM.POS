package com.example.ui.screens.pos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.CartItem
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
fun POSCartItemEditDialog(
    currentItem: CartItem,
    storeCurrency: String,
    lang: String,
    onUpdateCart: (quantity: Double, price: Double, discount: Double) -> Unit,
    onRemoveFromCart: () -> Unit,
    onDismiss: () -> Unit
) {
    var editQuantity by remember(currentItem.product.id) {
        mutableStateOf(
            if (currentItem.quantity % 1.0 == 0.0) currentItem.quantity.toInt().toString()
            else currentItem.quantity.toString()
        )
    }
    var editPrice by remember(currentItem.product.id) {
        mutableStateOf(String.format(Locale.US, "%.2f", currentItem.unitPrice))
    }
    var editDiscount by remember(currentItem.product.id) {
        mutableStateOf(if (currentItem.lineDiscount > 0.0) currentItem.lineDiscount.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${Bilingual.tr(lang, "تعديل: ", "Modifier: ", "Edit: ")}${currentItem.product.name}",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val isNegative = (editQuantity.toDoubleOrNull() ?: currentItem.quantity) < 0
                Button(
                    onClick = {
                        val currentQ = editQuantity.toDoubleOrNull() ?: currentItem.quantity
                        val newQ = if (currentQ == 0.0) -1.0 else -currentQ
                        editQuantity = if (newQ % 1.0 == 0.0) newQ.toInt().toString() else String.format(Locale.US, "%.2f", newQ)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNegative) Color(0xFFDC2626) else Color(0xFF2563EB)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isNegative) {
                            Bilingual.tr(lang, "الحالة: استرجاع زبون (سالب -) ⚠️", "Mode: Retour Client (Négatif -)", "Mode: Customer Return (-)")
                        } else {
                            Bilingual.tr(lang, "الحالة: بيع عادي (+) | انقر للتحويل لمرتجع", "Mode: Vente Normale (+) | Cliquer pour Retour", "Mode: Normal Sale (+) | Tap for Return")
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val currentQ = editQuantity.toDoubleOrNull() ?: currentItem.quantity
                            val newQ = currentQ - 1.0
                            editQuantity = if (newQ % 1.0 == 0.0) newQ.toInt().toString() else String.format(Locale.US, "%.2f", newQ)
                        }
                    ) {
                        Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Minus", tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
                    }

                    Text(
                        text = editQuantity,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isNegative) Color(0xFFEF4444) else Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    IconButton(
                        onClick = {
                            val currentQ = editQuantity.toDoubleOrNull() ?: currentItem.quantity
                            val newQ = currentQ + 1.0
                            editQuantity = if (newQ % 1.0 == 0.0) newQ.toInt().toString() else String.format(Locale.US, "%.2f", newQ)
                        }
                    ) {
                        Icon(Icons.Filled.AddCircleOutline, contentDescription = "Plus", tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                    }
                }

                OutlinedTextField(
                    value = editQuantity,
                    onValueChange = { editQuantity = it },
                    label = {
                        Text(Bilingual.tr(lang, "الكمية (يمكن إدخال سالب مثل -5 للاسترجاع)", "Quantité (ex: -5 pour retour)", "Quantity (e.g. -5 for return)"))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )

                val parsedPrice = editPrice.toDoubleOrNull() ?: currentItem.unitPrice
                OutlinedTextField(
                    value = editPrice,
                    onValueChange = { editPrice = it },
                    label = {
                        Text(Bilingual.tr(lang, "ثمن البيع للوحدة ($storeCurrency)", "Prix de Vente Unitaire ($storeCurrency)", "Unit Sale Price ($storeCurrency)"))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = {
                        if (editPrice.toDoubleOrNull() != null && editPrice.toDoubleOrNull() != currentItem.product.sellPriceDouble) {
                            TextButton(onClick = { editPrice = String.format(Locale.US, "%.2f", currentItem.product.sellPriceDouble) }) {
                                Text(Bilingual.tr(lang, "السعر الأصلي", "Original", "Original"), fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                val parsedQ = editQuantity.toDoubleOrNull() ?: currentItem.quantity
                val lineTotalPreview = parsedQ * parsedPrice
                Text(
                    text = "${Bilingual.tr(lang, "إجمالي البند: ", "Total Ligne: ", "Line Total: ")}${MoneyUtils.format(lineTotalPreview, storeCurrency)}",
                    color = if (parsedQ < 0) Color(0xFFEF4444) else Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = editDiscount,
                    onValueChange = { editDiscount = it },
                    label = {
                        Text(Bilingual.tr(lang, "خصم ($storeCurrency)", "Remise ($storeCurrency)", "Discount ($storeCurrency)"))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = editQuantity.toDoubleOrNull() ?: currentItem.quantity
                    val p = editPrice.toDoubleOrNull() ?: currentItem.unitPrice
                    val d = editDiscount.toDoubleOrNull() ?: currentItem.lineDiscount
                    onUpdateCart(q, p, d)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text(Bilingual.tr(lang, "تحديث السلة", "Mettre à jour", "Update Cart"))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = {
                        onRemoveFromCart()
                        onDismiss()
                    }
                ) {
                    Text(Bilingual.tr(lang, "حذف من السلة", "Supprimer", "Remove"), color = Color(0xFFEF4444))
                }
                TextButton(onClick = onDismiss) {
                    Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"), color = Color.Gray)
                }
            }
        },
        containerColor = Color(0xFF1E293B),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun POSManualBarcodeDialog(
    lang: String,
    onSubmitBarcode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var manualBarcodeValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color(0xFF2563EB))
                Spacer(Modifier.width(8.dp))
                Text(Bilingual.tr(lang, "إدخال الباركود السريع", "Saisie rapide code-barres", "Quick Barcode Entry"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = Bilingual.tr(lang, "أدخل رقم الباركود لإضافته مباشرة إلى السلة:", "Entrez le code-barres pour l'ajouter au panier:", "Enter barcode to add directly to cart:"),
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = manualBarcodeValue,
                    onValueChange = { manualBarcodeValue = it },
                    placeholder = { Text("611100000001", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (manualBarcodeValue.isNotBlank()) {
                            onSubmitBarcode(manualBarcodeValue)
                            onDismiss()
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (manualBarcodeValue.isNotBlank()) {
                        onSubmitBarcode(manualBarcodeValue)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text(Bilingual.tr(lang, "إضافة للسلة", "Ajouter au panier", "Add to Cart"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"), color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E293B),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun POSUnrecognizedBarcodeDialog(
    barcode: String,
    lang: String,
    onAddToInventory: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFF59E0B))
                Spacer(Modifier.width(8.dp))
                Text(Bilingual.tr(lang, "المنتج غير مسجل", "Produit non enregistré", "Unregistered Product"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "الباركود الممسوح غير مسجل في قاعدة البيانات:\n$barcode",
                        "Code-barres scanné introuvable dans la base de données:\n$barcode",
                        "Scanned barcode not found in database:\n$barcode"
                    ),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Bilingual.tr(lang, "هل ترغب في الانتقال لإدارة المخزون لإضافته الآن؟", "Voulez-vous aller à l'inventaire pour l'ajouter maintenant ?", "Would you like to go to inventory to add it now?"),
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAddToInventory,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text(Bilingual.tr(lang, "إضافة للمخزون", "Ajouter à l'inventaire", "Add to Inventory"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"), color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E293B),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
