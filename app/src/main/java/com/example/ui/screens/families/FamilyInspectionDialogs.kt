package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import com.example.ui.theme.Bilingual
import java.util.Locale

@Composable
internal fun FamilyProductsInspectionModal(
    category: CategoryEntity,
    products: List<ProductEntity>,
    currency: String,
    lang: String,
    onDismiss: () -> Unit,
    onAddProduct: () -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredProducts = remember(products, query) {
        if (query.isBlank()) products
        else {
            val q = query.trim().lowercase()
            products.filter { it.name.lowercase().contains(q) || it.barcode.lowercase().contains(q) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(category.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = if (lang == "AR") "إجمالي: ${products.size} منتج تابع لهذه العائلة" else "${products.size} produits au total",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).background(Color(0xFF1E293B), CircleShape)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(if (lang == "AR") "ابحث داخل العائلة..." else "Filtrer...", color = Color(0xFF64748B), fontSize = 12.sp)
                                }
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onAddProduct,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (lang == "AR") "+ منتج" else "+ Produit", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (lang == "AR") "لا توجد منتجات مسجلة بهذه العائلة" else "Aucun produit dans cette famille",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredProducts, key = { it.id }, contentType = { "family_product_row" }) { prod ->
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            text = Bilingual.tr(lang, "الباركود: ${prod.barcode}", "Code : ${prod.barcode}", "Barcode: ${prod.barcode}"),
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = String.format(Locale.US, "%.2f %s", prod.sellPriceDouble, currency),
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )

                                        val isNegative = prod.currentStock < 0.0
                                        val isLow = prod.currentStock <= prod.minStock
                                        Surface(
                                            color = if (isNegative) Color(0xFFDC2626) else if (isLow) Color(0x33EF4444) else Color(0x3310B981),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = String.format(Locale.US, "%.0f %s", prod.currentStock, Bilingual.tr(lang, "قطعة", "pcs", "pcs")),
                                                color = if (isNegative) Color.White else if (isLow) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        IconButton(onClick = { onDeleteProduct(prod) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
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
}

@Composable
internal fun QuickAddProductToFamilyDialog(
    category: CategoryEntity,
    lang: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, barcode: String, purchasePrice: Double, sellPrice: Double, stock: Double, minStock: Double, unit: String, familyName: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var familyName by remember { mutableStateOf(category.name) }
    var barcode by remember { mutableStateOf("${System.currentTimeMillis() % 1000000000L}") }
    var sellPrice by remember { mutableStateOf("10.0") }
    var purchasePrice by remember { mutableStateOf("8.0") }
    var stock by remember { mutableStateOf("20.0") }
    var minStock by remember { mutableStateOf("3.0") }
    var unit by remember(lang) { mutableStateOf(Bilingual.tr(lang, "قطعة", "Unité", "Piece")) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (lang == "AR") "إضافة منتج تابع لعائلة: ${category.name}" else "Ajouter un produit à : ${category.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(if (lang == "AR") "اسم المنتج" else "Nom du produit", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF2563EB), unfocusedBorderColor = Color(0xFF475569)),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = familyName,
                    onValueChange = { familyName = it },
                    label = { Text(if (lang == "AR") "عائلة المنتج" else "Famille du produit", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF2563EB), unfocusedBorderColor = Color(0xFF475569)),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text(if (lang == "AR") "الباركود" else "Code-barres", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF2563EB), unfocusedBorderColor = Color(0xFF475569)),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = sellPrice,
                        onValueChange = { sellPrice = it },
                        label = { Text(if (lang == "AR") "سعر البيع" else "Prix Vente", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF2563EB), unfocusedBorderColor = Color(0xFF475569)),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text(if (lang == "AR") "سعر الشراء" else "Prix Achat", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF2563EB), unfocusedBorderColor = Color(0xFF475569)),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it },
                        label = { Text(if (lang == "AR") "المخزون الحالي" else "Stock", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF2563EB), unfocusedBorderColor = Color(0xFF475569)),
                        modifier = Modifier.weight(1f)
                    )
                }

                error?.let { errText ->
                    Text(text = errText, color = Color(0xFFEF4444), fontSize = 12.sp)
                }

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                    ) {
                        Text(if (lang == "AR") "إلغاء" else "Annuler")
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                error = if (lang == "AR") "يرجى إدخال اسم المنتج" else "Le nom est obligatoire"
                            } else {
                                onConfirm(
                                    name.trim(),
                                    barcode.trim(),
                                    purchasePrice.toDoubleOrNull() ?: 0.0,
                                    sellPrice.toDoubleOrNull() ?: 0.0,
                                    stock.toDoubleOrNull() ?: 0.0,
                                    minStock.toDoubleOrNull() ?: 2.0,
                                    unit,
                                    familyName.trim()
                                )
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Text(if (lang == "AR") "حفظ المنتج" else "Valider", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
