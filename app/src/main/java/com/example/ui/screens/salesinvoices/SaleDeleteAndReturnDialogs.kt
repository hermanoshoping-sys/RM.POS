package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.domain.PosOperationResult
import com.example.domain.ReturnRefundCalculator
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils

@Composable
internal fun SaleDeleteConfirmDialog(
    target: SaleEntity,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (lang == "AR") "تأكيد حذف فاتورة المبيعات" else "Confirmer la suppression",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                if (lang == "AR") {
                    "هل أنت متأكد من حذف الفاتورة (#${target.invoiceNumber})؟\nسيتم إنقاص مبلغ الفاتورة كاملاً من دين الزبون، واسترجاع مخزون المنتجات تلقائياً، وإزالتها من سجل بيع المنتجات."
                } else {
                    "Voulez-vous vraiment supprimer la facture (#${target.invoiceNumber}) ?\nLe montant de la facture sera déduit de la dette du client et le stock sera restauré."
                }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.deleteSale(target.id) { res ->
                        when (res) {
                            is PosOperationResult.Success -> {
                                onDismiss()
                                Toast.makeText(
                                    context,
                                    res.message.ifBlank { if (lang == "AR") "تم حذف الفاتورة وإنقاص دين الزبون واسترجاع المخزون بنجاح" else "Facture supprimée avec succès" },
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
internal fun SaleReturnDialog(
    target: SaleEntity,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var saleItems by remember { mutableStateOf<List<SaleItemEntity>>(emptyList()) }
    var returnedQtyByProduct by remember { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    var selectedItemToReturn by remember { mutableStateOf<SaleItemEntity?>(null) }
    var returnQuantityText by remember { mutableStateOf("1") }
    var isCreditRefund by remember { mutableStateOf(target.customerId > 0) }

    fun formatQty(q: Double): String = if (kotlin.math.abs(q - q.toLong()) < 1e-6) {
        q.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", q).trimEnd('0').trimEnd('.')
    }

    LaunchedEffect(target.id) {
        val loadedItems = viewModel.getSaleItems(target.id)
        val loadedReturned = viewModel.getReturnedQuantitiesForSale(target.id)
        saleItems = loadedItems
        returnedQtyByProduct = loadedReturned
        if (loadedItems.isNotEmpty()) {
            val firstReturnable = loadedItems.firstOrNull { item ->
                val soldTotal = loadedItems.filter { it.productId == item.productId }.sumOf { it.quantity }
                val prior = loadedReturned[item.productId] ?: 0.0
                (soldTotal - prior) > 1e-6
            } ?: loadedItems.first()
            selectedItemToReturn = firstReturnable
            val soldTotal = loadedItems.filter { it.productId == firstReturnable.productId }.sumOf { it.quantity }
            val rem = (soldTotal - (loadedReturned[firstReturnable.productId] ?: 0.0)).coerceAtLeast(0.0)
            returnQuantityText = if (rem > 1e-6) formatQty(minOf(1.0, rem)) else "0"
        }
    }

    val selectedPriorReturned = selectedItemToReturn?.let { returnedQtyByProduct[it.productId] ?: 0.0 } ?: 0.0
    val selectedOriginalSold = selectedItemToReturn?.let { sel ->
        saleItems.filter { it.productId == sel.productId }.sumOf { it.quantity }
    } ?: 0.0
    val selectedRemainingQty = (selectedOriginalSold - selectedPriorReturned).coerceAtLeast(0.0)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "${if (lang == "AR") "تسجيل مرتجع مبيعات" else "Retour de vente"} - #${target.invoiceNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    if (lang == "AR") "اختر الصنف المراد إرجاعه:" else "Sélectionnez l'article :",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (saleItems.isEmpty()) {
                    Text(if (lang == "AR") "جاري تحميل الأصناف..." else "Chargement...", color = Color.Gray)
                } else {
                    saleItems.forEach { item ->
                        val isSelected = selectedItemToReturn?.id == item.id
                        val priorReturned = returnedQtyByProduct[item.productId] ?: 0.0
                        val soldTotal = saleItems.filter { it.productId == item.productId }.sumOf { it.quantity }
                        val remainingForItem = (soldTotal - priorReturned).coerceAtLeast(0.0)
                        val sampleQty = if (remainingForItem > 1e-6) minOf(1.0, remainingForItem) else minOf(1.0, item.quantity)
                        val unitPreview = ReturnRefundCalculator.computeLineReturnRefundOrNull(
                            sale = target,
                            saleItems = saleItems,
                            productId = item.productId,
                            returnQuantity = sampleQty,
                            alreadyReturnedQuantity = if (remainingForItem > 1e-6) priorReturned else 0.0
                        )
                        val netUnitCents = unitPreview?.effectiveUnitNetCents ?: item.unitPrice
                        val hasDiscount = unitPreview?.hasDiscountsApplied == true
                        Surface(
                            color = if (isSelected) Color(0xFF2563EB).copy(alpha = 0.2f) else MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFF334155)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedItemToReturn = item
                                    returnQuantityText = if (remainingForItem > 1e-6) formatQty(minOf(1.0, remainingForItem)) else "0"
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
                                    Text(
                                        if (hasDiscount) {
                                            if (lang == "AR") {
                                                "المباع: ${formatQty(item.quantity)} | المتبقي للإرجاع: ${formatQty(remainingForItem)} | الصافي للوحدة: ${MoneyUtils.format(netUnitCents, currency)}"
                                            } else {
                                                "Vendu : ${formatQty(item.quantity)} | Restant : ${formatQty(remainingForItem)} | Net/u : ${MoneyUtils.format(netUnitCents, currency)}"
                                            }
                                        } else {
                                            if (lang == "AR") {
                                                "المباع: ${formatQty(item.quantity)} | المتبقي للإرجاع: ${formatQty(remainingForItem)} | السعر: ${MoneyUtils.format(item.unitPriceDouble, currency)}"
                                            } else {
                                                "Vendu : ${formatQty(item.quantity)} | Restant : ${formatQty(remainingForItem)} | Prix : ${MoneyUtils.format(item.unitPriceDouble, currency)}"
                                            }
                                        },
                                        fontSize = 11.sp,
                                        color = if (remainingForItem > 1e-6) Color.Gray else Color(0xFFF87171),
                                        modifier = Modifier.testTag("sale_return_item_remaining_${item.productId}")
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedItemToReturn != null) {
                    Text(
                        text = if (lang == "AR") {
                            "الكمية المتبقية القابلة للإرجاع: ${formatQty(selectedRemainingQty)} (تم إرجاع ${formatQty(selectedPriorReturned)} سابقاً)"
                        } else {
                            "Quantité restante retournable : ${formatQty(selectedRemainingQty)} (déjà retourné : ${formatQty(selectedPriorReturned)})"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedRemainingQty > 1e-6) Color(0xFF38BDF8) else Color(0xFFF87171),
                        modifier = Modifier.testTag("sale_return_remaining_qty_text")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                OutlinedTextField(
                    value = returnQuantityText,
                    onValueChange = { returnQuantityText = it },
                    label = { Text(if (lang == "AR") "الكمية المرتجعة" else "Quantité retournée") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sale_return_qty_input")
                )

                val parsedQty = returnQuantityText.replace(",", ".").toDoubleOrNull() ?: 0.0
                val currentRefundPreview = remember(selectedItemToReturn, saleItems, parsedQty, selectedPriorReturned) {
                    val curItem = selectedItemToReturn
                    if (curItem != null && parsedQty > 0.0) {
                        ReturnRefundCalculator.computeLineReturnRefundOrNull(
                            sale = target,
                            saleItems = saleItems,
                            productId = curItem.productId,
                            returnQuantity = parsedQty,
                            alreadyReturnedQuantity = selectedPriorReturned
                        )
                    } else null
                }

                if (currentRefundPreview != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (lang == "AR") {
                                    "المبلغ الصافي المسترد: ${MoneyUtils.format(currentRefundPreview.maxRefundCents, currency)}"
                                } else {
                                    "Montant net à rembourser : ${MoneyUtils.format(currentRefundPreview.maxRefundCents, currency)}"
                                },
                                color = Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.testTag("sale_return_net_refund_text")
                            )
                            if (currentRefundPreview.hasDiscountsApplied) {
                                Text(
                                    text = if (lang == "AR") {
                                        "محتسب بعد خصم السطر وحصة خصم الفاتورة"
                                    } else {
                                        "Calculé après remise ligne et part de remise facture"
                                    },
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else if (selectedItemToReturn != null && (selectedRemainingQty <= 1e-6 || parsedQty > selectedRemainingQty + 1e-6)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (selectedRemainingQty <= 1e-6) {
                            if (lang == "AR") "تم إرجاع كامل كمية هذا الصنف مسبقاً" else "Toute la quantité de cet article a déjà été retournée"
                        } else {
                            if (lang == "AR") "الكمية المطلوبة تتجاوز المتبقي القابل للإرجاع (${formatQty(selectedRemainingQty)})" else "Quantité supérieure au reste retournable (${formatQty(selectedRemainingQty)})"
                        },
                        color = Color(0xFFF87171),
                        fontSize = 11.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (target.customerId > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = isCreditRefund, onCheckedChange = { isCreditRefund = it })
                        Text(
                            text = if (lang == "AR") "إرجاع كرصيد في حساب الزبون (خصم من الدين)" else "Rembourser sous forme de crédit client",
                            fontSize = 12.sp
                        )
                    }
                }

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
                            val qty = returnQuantityText.replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (curItem != null && qty > 0.0) {
                                val refundPlan = ReturnRefundCalculator.computeLineReturnRefundOrNull(
                                    sale = target,
                                    saleItems = saleItems,
                                    productId = curItem.productId,
                                    returnQuantity = qty,
                                    alreadyReturnedQuantity = selectedPriorReturned
                                )
                                if (refundPlan == null) {
                                    Toast.makeText(
                                        context,
                                        if (lang == "AR") "الكمية المرتجعة تتجاوز الكمية المتبقية القابلة للإرجاع" else "Quantité supérieure à la quantité restante retournable",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@Button
                                }
                                // Delegate final refund calculation strictly to Repository (single source of truth)
                                viewModel.recordCustomerReturn(
                                    saleId = target.id,
                                    productId = curItem.productId,
                                    productName = curItem.productName,
                                    quantity = qty,
                                    refundAmount = null,
                                    isCreditRefund = isCreditRefund,
                                    customerId = target.customerId
                                ) { res ->
                                    when (res) {
                                        is PosOperationResult.Success -> {
                                            onDismiss()
                                            Toast.makeText(
                                                context,
                                                res.message.ifBlank { if (lang == "AR") "تم تسجيل المرتجع واسترجاع المخزون بنجاح" else "Retour enregistré avec succès" },
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
                        enabled = currentRefundPreview != null && selectedRemainingQty > 1e-6,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sale_return_confirm_button")
                    ) {
                        Text(if (lang == "AR") "تأكيد المرتجع" else "Confirmer", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}
