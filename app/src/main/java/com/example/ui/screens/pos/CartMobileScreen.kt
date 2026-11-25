package com.example.ui.screens.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.viewmodels.CartItem
import com.example.ui.theme.POSPrimary
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.*

@Composable
fun CartMobileScreen(viewModel: PosViewModel, lang: String, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val afterDiscount by viewModel.cartTotalAfterGlobalDiscount.collectAsStateWithLifecycle()
    val beforeDiscount by viewModel.cartTotalBeforeGlobalDiscount.collectAsStateWithLifecycle()
    val globalDiscountVal by viewModel.globalDiscountValue.collectAsStateWithLifecycle()
    val globalDiscountType by viewModel.globalDiscountType.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val totalDiscountAmount = if (globalDiscountType == "PERCENT") (beforeDiscount * globalDiscountVal / 100.0) else globalDiscountVal

    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showSuspendedSales by remember { mutableStateOf(false) }

    var editingCartItem by remember { mutableStateOf<CartItem?>(null) }
    var editQuantity by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var editDiscount by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.clearCart() }) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear")
            }
            Text(stringResource(R.string.cart_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val options = GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                        .enableAutoZoom()
                        .build()
                    val scanner = GmsBarcodeScanning.getClient(context, options)
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            val rawValue = barcode.rawValue
                            if (!rawValue.isNullOrBlank()) {
                                viewModel.onBarcodeScanned(rawValue)
                            }
                        }
                }) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan Barcode", tint = POSPrimary)
                }
                IconButton(onClick = { onNavigate("POS") }) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Back")
                }
            }
        }

        HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

        // Basket list
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(cart) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.LocalMall, contentDescription = null, tint = Color.LightGray)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                editingCartItem = item
                                editQuantity = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                                editPrice = String.format(Locale.US, "%.2f", item.unitPrice)
                                editDiscount = if (item.lineDiscount > 0.0) item.lineDiscount.toString() else ""
                                showEditDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (item.quantity < 0) {
                                Surface(
                                    color = Color(0xFFDC2626),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (lang == "AR") "استرجاع / مرتجع" else "Retour",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(MoneyUtils.format(item.product.sellPriceDouble, currency), color = Color.Gray, fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(28.dp).background(Color(0xFF2563EB), RoundedCornerShape(8.dp)).clickable {
                                        viewModel.updateCartQuantity(item.product.id, item.quantity - 1.0)
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                }

                                Text(
                                    text = String.format(Locale.US, "%.0f", item.quantity),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (item.quantity < 0) Color(0xFFEF4444) else Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )

                                Box(
                                    modifier = Modifier.size(28.dp).background(Color(0xFF2563EB), RoundedCornerShape(8.dp)).clickable {
                                        viewModel.updateCartQuantity(item.product.id, item.quantity + 1.0)
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (item.quantity < 0) Color(0xFFDC2626).copy(alpha = 0.25f) else Color(0xFF334155),
                                    border = BorderStroke(1.dp, if (item.quantity < 0) Color(0xFFEF4444) else Color(0xFF64748B)),
                                    modifier = Modifier.clickable {
                                        viewModel.toggleCartItemReturn(item.product.id)
                                    }
                                ) {
                                    Text(
                                        text = "+ / -",
                                        color = if (item.quantity < 0) Color(0xFFFCA5A5) else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = MoneyUtils.format(item.totalAfterDiscount, currency),
                                fontWeight = FontWeight.Bold,
                                color = if (item.quantity < 0) Color(0xFFEF4444) else Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
            }
        }

        // Summary View at Bottom
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.cart_discount_label), color = Color.Gray, fontSize = 14.sp)
                Text(
                    text = if (totalDiscountAmount > 0.001) "- ${MoneyUtils.format(totalDiscountAmount, currency)}" else MoneyUtils.format(0.0, currency),
                    color = Color(0xFF16A34A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.cart_subtotal_label), color = Color.Gray, fontSize = 14.sp)
                Text(MoneyUtils.format(beforeDiscount, currency), fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.cart_total_label), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(MoneyUtils.format(afterDiscount, currency), fontWeight = FontWeight.Black, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { viewModel.suspendCurrentSale(); onNavigate("POS") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text(stringResource(R.string.cart_suspend_label), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (cart.isNotEmpty()) {
                            viewModel.receivedCash.value = afterDiscount
                            showCheckoutDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text(stringResource(R.string.cart_pay_label), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CartToolbarAction(icon = Icons.Filled.Menu, label = stringResource(R.string.cart_action_more)) { }
                CartToolbarAction(icon = Icons.Filled.LocalOffer, label = stringResource(R.string.cart_discount_label)) { showDiscountDialog = true }
                CartToolbarAction(icon = Icons.Filled.Calculate, label = stringResource(R.string.cart_action_calc)) { showCalculator = true }
                CartToolbarAction(icon = Icons.Filled.ReceiptLong, label = stringResource(R.string.cart_action_hold)) { showSuspendedSales = true }
            }
        }
    }

    if (showCheckoutDialog) {
        CheckoutPayDialog(
            viewModel = viewModel,
            grandTotal = afterDiscount,
            lang = lang,
            onDismiss = {
                showCheckoutDialog = false
                if (cart.isEmpty()) onNavigate("POS")
            }
        )
    }
    if (showCalculator) {
        CalculatorSheet(viewModel = viewModel, lang = lang, onDismiss = { showCalculator = false })
    }
    if (showDiscountDialog) {
        DiscountDialog(viewModel = viewModel, lang = lang, onDismiss = { showDiscountDialog = false })
    }
    if (showSuspendedSales) {
        SuspendedSalesDialog(viewModel = viewModel, lang = lang, onDismiss = { showSuspendedSales = false })
    }

    if (showEditDialog) {
        editingCartItem?.let { currentItem ->
            val isNegative = (editQuantity.toDoubleOrNull() ?: currentItem.quantity) < 0
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = {
                    Text(
                        text = "${if (lang == "AR") "تعديل: " else "Modifier: "} ${currentItem.product.name}",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isNegative) {
                                    if (lang == "AR") "الحالة: استرجاع زبون (سالب -) ⚠️" else "Mode: Retour Client (Négatif -)"
                                } else {
                                    if (lang == "AR") "الحالة: بيع عادي (+) | انقر للتحويل لمرتجع" else "Mode: Vente Normale (+) | Cliquer pour Retour"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
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
                                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Minus", tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
                            }

                            Text(
                                text = editQuantity,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNegative) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )

                            IconButton(
                                onClick = {
                                    val currentQ = editQuantity.toDoubleOrNull() ?: currentItem.quantity
                                    val newQ = currentQ + 1.0
                                    editQuantity = if (newQ % 1.0 == 0.0) newQ.toInt().toString() else String.format(Locale.US, "%.2f", newQ)
                                }
                            ) {
                                Icon(Icons.Filled.AddCircleOutline, contentDescription = "Plus", tint = Color(0xFF10B981), modifier = Modifier.size(28.dp))
                            }
                        }

                        OutlinedTextField(
                            value = editQuantity,
                            onValueChange = { editQuantity = it },
                            label = { Text(if (lang == "AR") "الكمية (يمكن إدخال سالب - للاسترجاع)" else "Quantité (négatif pour retour)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Price field
                        val parsedPrice = editPrice.toDoubleOrNull() ?: currentItem.unitPrice
                        OutlinedTextField(
                            value = editPrice,
                            onValueChange = { editPrice = it },
                            label = { Text(if (lang == "AR") "ثمن البيع للوحدة ($currency)" else "Prix de Vente Unitaire ($currency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            trailingIcon = {
                                if (editPrice.toDoubleOrNull() != null && editPrice.toDoubleOrNull() != currentItem.product.sellPriceDouble) {
                                    TextButton(onClick = { editPrice = String.format(Locale.US, "%.2f", currentItem.product.sellPriceDouble) }) {
                                        Text(if (lang == "AR") "السعر الأصلي" else "Original", fontSize = 11.sp, color = Color(0xFF38BDF8))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        val parsedQ = editQuantity.toDoubleOrNull() ?: currentItem.quantity
                        val lineTotalPreview = parsedQ * parsedPrice
                        Text(
                            text = "${if (lang == "AR") "إجمالي البند: " else "Total Ligne: "}${MoneyUtils.format(lineTotalPreview, currency)}",
                            color = if (parsedQ < 0) Color(0xFFEF4444) else Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = editDiscount,
                            onValueChange = { editDiscount = it },
                            label = { Text(if (lang == "AR") "خصم بالمبلغ ($currency)" else "Remise ($currency)") },
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
                            viewModel.updateCartQuantity(currentItem.product.id, q)
                            viewModel.updateCartPrice(currentItem.product.id, p)
                            viewModel.updateCartLineDiscount(currentItem.product.id, d)
                            showEditDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text(if (lang == "AR") "تحديث السلة" else "Mettre à jour")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.removeFromCart(currentItem.product.id)
                            showEditDialog = false
                        }
                    ) {
                        Text(if (lang == "AR") "حذف من السلة" else "Supprimer", color = Color(0xFFEF4444))
                    }
                }
            )
        }
    }
}
