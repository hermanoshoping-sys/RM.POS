package com.example.ui.screens.catalog

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.pos.POSCartLedgerRow
import com.example.ui.viewmodels.CartItem
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.util.Locale

/**
 * Authentic POS Cart Dialog:
 * - Header: سلة البيع المباشرة
 * - Banner: إجمالي الفاتورة الحالي | خطوط منتجات
 * - POS Ledger Table with Green Header + Gold Total + Giant Lime-Green "تأكيد البيع" Button
 */
@Composable
internal fun AuthenticPOSCartDialog(
    viewModel: PosViewModel,
    cart: List<CartItem>,
    cartTotalCount: Double,
    cartTotalPrice: Double,
    lang: String,
    onDismiss: () -> Unit,
    onConfirmSale: () -> Unit
) {
    val context = LocalContext.current
    val storeCurrency by viewModel.currency.collectAsStateWithLifecycle()
    var editingCartItem by remember { mutableStateOf<CartItem?>(null) }
    var editQuantity by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 320.dp, max = 540.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.5.dp, Color(0xFF1E293B)),
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (lang == "AR") "سلة البيع المباشرة" else "Panier Direct POS",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "${if (lang == "AR") "عنصر محدد للفوترة" else "articles:"} ${String.format(Locale.US, "%.0f", cartTotalCount)}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF334155), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (lang == "AR") "إجمالي الفاتورة الحالي" else "Total Actuel",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = MoneyUtils.format(cartTotalPrice, storeCurrency),
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }

                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "${if (lang == "AR") "خطوط منتجات" else "Lignes"} ${cart.size}",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color.Black)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            POSCartLedgerRow(
                                colTotal = if (lang == "AR") "المجموع" else "Total",
                                colQty = if (lang == "AR") "كمية" else "Qté",
                                colUnitPrice = if (lang == "AR") "ثمنه" else "Prix U",
                                colProduct = if (lang == "AR") "المنتج" else "Produit",
                                isHeader = true
                            )

                            val emptyRowsCount = (14 - cart.size).coerceAtLeast(0)
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(cart, key = { it.product.id }, contentType = { "cart_row" }) { item ->
                                    val qtyFormatted = if (item.quantity % 1.0 == 0.0) {
                                        String.format(Locale.US, "%.0f", item.quantity)
                                    } else {
                                        String.format(Locale.US, "%.2f", item.quantity)
                                    }
                                    POSCartLedgerRow(
                                        colTotal = String.format(Locale.US, "%.2f", item.totalAfterDiscount),
                                        colQty = qtyFormatted,
                                        colUnitPrice = String.format(Locale.US, "%.2f", item.unitPrice),
                                        colProduct = item.product.name,
                                        isHeader = false,
                                        isReturn = item.quantity < 0,
                                        onClick = {
                                            editingCartItem = item
                                            editQuantity = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString()
                                            editPrice = String.format(Locale.US, "%.2f", item.unitPrice)
                                            showEditDialog = true
                                        }
                                    )
                                }

                                items(emptyRowsCount, contentType = { "filler_row" }) {
                                    POSCartLedgerRow(
                                        colTotal = "",
                                        colQty = "",
                                        colUnitPrice = "",
                                        colProduct = "",
                                        isHeader = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = MoneyUtils.format(cartTotalPrice, storeCurrency),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "AR") "المجموع" else "Total",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (cart.isNotEmpty()) {
                                onConfirmSale()
                            } else {
                                Toast.makeText(
                                    context,
                                    if (lang == "AR") "السلة فارغة، يرجى اختيار منتجات للبيع" else "Le panier est vide",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("catalog_confirm_sale_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF70C038))
                    ) {
                        Text(
                            text = if (lang == "AR") "تأكيد البيع" else "Confirmer la vente",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color.White
                        )
                    }

                    if (cart.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.clearCart()
                                    Toast.makeText(
                                        context,
                                        if (lang == "AR") "تم إفراغ السلة" else "Panier vidé",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("catalog_clear_cart_button")
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (lang == "AR") "إفراغ السلة" else "Vider le panier",
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val currentItem = editingCartItem
    if (showEditDialog && currentItem != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = "${if (lang == "AR") "تعديل: " else "Modifier: "} ${currentItem.product.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editQuantity,
                        onValueChange = { editQuantity = it },
                        label = { Text(if (lang == "AR") "الكمية" else "Quantité") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { editPrice = it },
                        label = { Text(if (lang == "AR") "سعر الوحدة ($storeCurrency)" else "Prix unitaire ($storeCurrency)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = editQuantity.toDoubleOrNull() ?: currentItem.quantity
                        val p = editPrice.toDoubleOrNull() ?: currentItem.unitPrice
                        viewModel.updateCartQuantity(currentItem.product.id, q)
                        viewModel.updateCartPrice(currentItem.product.id, p)
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text(if (lang == "AR") "حفظ" else "Enregistrer")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.removeFromCart(currentItem.product.id)
                            showEditDialog = false
                        }
                    ) {
                        Text(if (lang == "AR") "حذف من السلة" else "Supprimer", color = Color(0xFFEF4444))
                    }
                    TextButton(onClick = { showEditDialog = false }) {
                        Text(if (lang == "AR") "إلغاء" else "Annuler")
                    }
                }
            }
        )
    }
}
