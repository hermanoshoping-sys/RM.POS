package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CustomerEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.domain.FinancialCalculator
import com.example.domain.PosOperationResult
import com.example.ui.viewmodels.PosViewModel
import com.example.util.GeoLocationHelper
import com.example.util.MoneyUtils
import com.example.util.cents
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun EditSaleDialog(
    sale: SaleEntity,
    customers: List<CustomerEntity>,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var itemsList by remember { mutableStateOf<List<SaleItemEntity>?>(null) }
    var selectedCustomerId by remember { mutableStateOf(sale.customerId) }
    var selectedPaymentMethod by remember { mutableStateOf(sale.paymentMethod) }
    var paidAmountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", sale.paidAmountDouble)) }

    LaunchedEffect(sale.id) {
        itemsList = viewModel.getSaleItems(sale.id)
    }

    val currentCustomerName = if (selectedCustomerId <= 0L) {
        if (lang == "AR") "زبون عادي" else "Client de passage"
    } else {
        customers.find { it.id == selectedCustomerId }?.name ?: sale.customerName
    }

    val parsedPaid = paidAmountText.toDoubleOrNull() ?: 0.0
    val editSummary = remember(itemsList, sale.totalAmount, parsedPaid) {
        val totalCents = itemsList?.let { FinancialCalculator.computeSaleItemsTotalCents(it) } ?: sale.totalAmount
        FinancialCalculator.computePaymentSettlement(totalCents, parsedPaid.cents)
    }
    val computedTotal = editSummary.totalAmount
    val changeAmount = editSummary.changeDue

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (lang == "AR") "تعديل فاتورة مبيعات" else "Modifier Vente",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(sale.invoiceNumber, fontSize = 12.sp, color = Color.Gray)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(if (lang == "AR") "الزبون:" else "Client:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                var customerExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { customerExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(currentCustomerName, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = customerExpanded,
                        onDismissRequest = { customerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (lang == "AR") "زبون عادي / غير مسجل" else "Client de passage") },
                            onClick = {
                                selectedCustomerId = 0L
                                customerExpanded = false
                            }
                        )
                        customers.forEach { c ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (c.isGeoLocationRequired && c.hasGeoLocation) {
                                            Text("📍", fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
                                        }
                                        Text(c.name)
                                    }
                                },
                                onClick = {
                                    if (currentUser?.enforceCustomerGeoLocation == true && c.isGeoLocationRequired && c.hasGeoLocation) {
                                        coroutineScope.launch {
                                            val check = GeoLocationHelper.verifyCustomerLocationForInvoice(
                                                context = context,
                                                customer = c,
                                                currentUser = currentUser,
                                                lang = lang
                                            )
                                            if (check.isAllowed) {
                                                selectedCustomerId = c.id
                                                customerExpanded = false
                                            } else {
                                                customerExpanded = false
                                                Toast.makeText(
                                                    context,
                                                    check.errorMessage ?: "لا يمكن اختيار الزبون: خارج الموقع المحدد",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    } else {
                                        selectedCustomerId = c.id
                                        customerExpanded = false
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (lang == "AR") "الأصناف المباعة:" else "Articles vendus:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                itemsList?.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "${String.format(Locale.US, "%.1f", item.quantity)} × ${MoneyUtils.format(item.unitPriceDouble, currency)}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Text(
                                MoneyUtils.format(item.quantity * item.unitPriceDouble, currency),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = paidAmountText,
                    onValueChange = { paidAmountText = it },
                    label = { Text(if (lang == "AR") "المبلغ المستلم / المدفوع ($currency)" else "Montant reçu ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = if (parsedPaid < computedTotal) Color(0xFFEF4444).copy(alpha = 0.1f) else Color(0xFF10B981).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (parsedPaid < computedTotal) {
                                if (lang == "AR") "المتبقي كدين على الزبون:" else "Reste (Crédit):"
                            } else {
                                if (lang == "AR") "الفكة المسترجعة:" else "Monnaie:"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (parsedPaid < computedTotal) Color(0xFFEF4444) else Color(0xFF10B981)
                        )
                        Text(
                            text = if (parsedPaid < computedTotal) {
                                MoneyUtils.format(computedTotal - parsedPaid, currency)
                            } else {
                                MoneyUtils.format(changeAmount, currency)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (parsedPaid < computedTotal) Color(0xFFEF4444) else Color(0xFF10B981)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val currentItems = itemsList ?: emptyList()
                        val updated = sale.copy(
                            customerId = selectedCustomerId,
                            customerName = currentCustomerName,
                            totalAmount = editSummary.totalCents,
                            paidAmount = editSummary.paidCents,
                            changeAmount = editSummary.changeDueCents,
                            paymentMethod = selectedPaymentMethod
                        )
                        viewModel.updateSale(updated, currentItems) { res ->
                            when (res) {
                                is PosOperationResult.Success -> {
                                    Toast.makeText(
                                        context,
                                        res.message.ifBlank { if (lang == "AR") "تم تحديث الفاتورة بنجاح" else "Facture mise à jour" },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }
                                is PosOperationResult.Failure -> {
                                    Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "AR") "حفظ التعديلات" else "Enregistrer", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

