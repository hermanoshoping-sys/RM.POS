package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.printer.BluetoothPrinterService
import com.example.data.PurchaseEntity
import com.example.data.PurchaseItemEntity
import com.example.data.SupplierEntity
import com.example.domain.FinancialCalculator
import com.example.domain.PosOperationResult
import com.example.ui.components.rememberBluetoothAwarePrintLauncher
import com.example.ui.screens.pos.ReceiptAggregateRow
import com.example.ui.screens.pos.ReceiptFactRow
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import com.example.util.cents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ThermalPurchaseReceiptDialog(
    viewModel: PosViewModel,
    purchase: PurchaseEntity,
    items: List<PurchaseItemEntity>,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val storeSettings by viewModel.settings.collectAsStateWithLifecycle()
    val companyName = storeSettings.shopName.ifBlank { if (lang == "AR") "الشركة" else "Entreprise" }
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    val launchPurchasePrint = rememberBluetoothAwarePrintLauncher(lang = lang) {
        val printerString = generateThermalPrinterStringForPurchase(purchase, items, shopName = companyName)
        val pType = viewModel.printerConnectionType.value
        val pTarget = viewModel.printerMac.value.ifBlank { viewModel.printerName.value }
        BluetoothPrinterService.printUnified(
            context = context,
            connectionType = pType,
            printerNameOrMac = pTarget,
            textToPrint = printerString,
            ip = viewModel.printerIp.value,
            port = viewModel.printerPort.value,
            jobTitle = "Purchase Order #${purchase.id}"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .width(340.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = companyName,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif
                )
                val curr = storeSettings.currency.ifBlank { "MAD" }
                Text(
                    text = Bilingual.tr(lang, "فاتورة مشتريات", "Bon de Réception", "Purchase Receipt"),
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = Bilingual.tr(lang, "قسم المشتريات والمخازن", "Achats & Stock", "Purchasing & Inventory"),
                    color = Color.DarkGray,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Text("-".repeat(40), color = Color.Gray)

                ReceiptFactRow(Bilingual.tr(lang, "رقم السند:", "Bon ID :", "Receipt ID:"), purchase.displayInvoiceNumber)
                ReceiptFactRow(Bilingual.tr(lang, "التاريخ:", "Date :", "Date:"), dateFormatter.format(Date(purchase.timestamp)))
                ReceiptFactRow(Bilingual.tr(lang, "المورد:", "Fournisseur :", "Supplier:"), purchase.supplierName)

                Text("-".repeat(40), color = Color.Gray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Bilingual.tr(lang, "الصنف", "Article", "Item"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.8f))
                    Text(Bilingual.tr(lang, "الكمية", "Qté", "Qty"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text(Bilingual.tr(lang, "المجموع", "Total", "Total"), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }

                Spacer(modifier = Modifier.height(4.dp))

                items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(item.productName, color = Color.Black, fontSize = 11.sp, modifier = Modifier.weight(1.8f))
                        Text(String.format(Locale.US, "%.1f", item.quantity), color = Color.Black, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(String.format(Locale.US, "%.1f %s", item.quantity * item.unitPriceDouble, curr), color = Color.Black, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                }

                Text("-".repeat(40), color = Color.Gray)

                ReceiptAggregateRow(Bilingual.tr(lang, "إجمالي الشراء", "Total Achat", "Purchase Total"), MoneyUtils.format(purchase.totalAmountDouble, curr))
                ReceiptAggregateRow(Bilingual.tr(lang, "المبلغ المدفوع", "Montant Payé", "Paid Amount"), MoneyUtils.format(purchase.paidAmountDouble, curr))

                Text("-".repeat(40), color = Color.Gray)

                Text(
                    text = Bilingual.tr(lang, "تمت مطابقة واستلام البضاعة بالمستودع", "Réception conforme en magasin", "Goods received and verified"),
                    color = Color.Black,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { sharePurchaseReceipt(context, purchase, items, asPdf = false, shopName = companyName, currency = curr) },
                        modifier = Modifier.size(48.dp).background(Color(0xFF334155), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = "Share Image", tint = Color.White)
                    }
                    IconButton(
                        onClick = { sharePurchaseReceipt(context, purchase, items, asPdf = true, shopName = companyName, currency = curr) },
                        modifier = Modifier.size(48.dp).background(Color(0xFFDC2626), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Share PDF", tint = Color.White)
                    }
                    Button(
                        onClick = {
                            launchPurchasePrint(viewModel.printerConnectionType.value)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Bilingual.tr(lang, "طباعة الفاتورة", "Imprimer", "Print Receipt"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditPurchaseDialog(
    purchase: PurchaseEntity,
    suppliers: List<SupplierEntity>,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var itemsList by remember { mutableStateOf<List<PurchaseItemEntity>?>(null) }
    var selectedSupplierId by remember { mutableStateOf(purchase.supplierId) }
    var invoiceNumberText by remember { mutableStateOf(purchase.displayInvoiceNumber) }
    var paidAmountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", purchase.paidAmountDouble)) }

    LaunchedEffect(purchase.id) {
        itemsList = viewModel.getPurchaseItems(purchase.id)
    }

    val currentSupplierName = suppliers.find { it.id == selectedSupplierId }?.name ?: purchase.supplierName
    val parsedPaid = paidAmountText.toDoubleOrNull() ?: 0.0
    val editSummary = remember(itemsList, purchase.totalAmount, parsedPaid) {
        val totalCents = itemsList?.let { FinancialCalculator.computePurchaseItemsTotalCents(it) } ?: purchase.totalAmount
        FinancialCalculator.computePaymentSettlement(totalCents, parsedPaid.cents)
    }
    val remainingDebt = editSummary.remainingDebt

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
                            if (lang == "AR") "تعديل فاتورة مشتريات" else "Modifier Facture Achat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(purchase.displayInvoiceNumber, fontSize = 12.sp, color = Color.Gray)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(if (lang == "AR") "رقم الفاتورة / السند:" else "Numéro de facture / Bon:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = invoiceNumberText,
                    onValueChange = { invoiceNumberText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(if (lang == "AR") "المورد:" else "Fournisseur:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                var supplierExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { supplierExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(currentSupplierName, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = supplierExpanded,
                        onDismissRequest = { supplierExpanded = false }
                    ) {
                        suppliers.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = {
                                    selectedSupplierId = s.id
                                    supplierExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (lang == "AR") "الأصناف المسجلة في السند:" else "Articles reçus:",
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
                    label = { Text(if (lang == "AR") "المبلغ المدفوع للمورد ($currency)" else "Montant payé ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = if (remainingDebt > 0.01) Color(0xFFEF4444).copy(alpha = 0.1f) else Color(0xFF10B981).copy(alpha = 0.1f),
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
                            text = if (remainingDebt > 0.01) {
                                if (lang == "AR") "المتبقي كدين للمورد:" else "Dette restante:"
                            } else {
                                if (lang == "AR") "الحالة:" else "Statut:"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingDebt > 0.01) Color(0xFFEF4444) else Color(0xFF10B981)
                        )
                        Text(
                            text = if (remainingDebt > 0.01) {
                                MoneyUtils.format(remainingDebt, currency)
                            } else {
                                if (lang == "AR") "مسددة بالكامل" else "Entièrement payée"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingDebt > 0.01) Color(0xFFEF4444) else Color(0xFF10B981)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val currentItems = itemsList ?: emptyList()
                        val updated = purchase.copy(
                            invoiceNumber = invoiceNumberText.trim(),
                            supplierId = selectedSupplierId,
                            supplierName = currentSupplierName,
                            totalAmount = editSummary.totalCents,
                            paidAmount = editSummary.paidCents
                        )
                        viewModel.updatePurchase(updated, currentItems) { res ->
                            when (res) {
                                is PosOperationResult.Success -> {
                                    Toast.makeText(
                                        context,
                                        res.message.ifBlank { if (lang == "AR") "تم تحديث فاتورة الشراء بنجاح" else "Facture achat mise à jour" },
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
