package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.printer.BluetoothPrinterService
import com.example.data.ExpenseEntity
import com.example.ui.components.rememberBluetoothAwarePrintLauncher
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.*


@Composable
fun ExpenseInvoiceDialogImage2(
    expense: ExpenseEntity,
    currency: String,
    lang: String,
    shopName: String,
    shopPhone: String,
    taxNumber: String,
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFormatted = remember(expense.timestamp) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(expense.timestamp))
    }
    val invoiceNumber = "DEP-${expense.id}"
    val launchThermalExpensePrint = rememberBluetoothAwarePrintLauncher(lang = lang) {
        try {
            val pType = viewModel.printerConnectionType.value
            val pTarget = viewModel.printerMac.value.ifBlank { viewModel.printerName.value }
            val thermalText = generateSingleExpenseThermalString(expense, lang, shopName, currency)
            BluetoothPrinterService.printUnified(
                context = context,
                connectionType = pType,
                printerNameOrMac = pTarget,
                textToPrint = thermalText,
                ip = viewModel.printerIp.value,
                port = viewModel.printerPort.value,
                jobTitle = "Expense Voucher"
            )
            Toast.makeText(context.applicationContext ?: context, if (lang == "AR") "جاري الطباعة الحرارية..." else "Impression...", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(context.applicationContext ?: context, "فشل الطباعة: ${t.localizedMessage ?: "خطأ"}", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, Color.Black),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .imePadding()
                .padding(vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (lang == "AR") "فاتورة المصروف" else "FACTURE DE DÉPENSE",
                    color = Color(0xFFFF0000),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (shopName.isNotBlank() && shopName != "My Application") shopName else "الهيدر",
                    color = Color.Black,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                if (shopPhone.isNotBlank() || taxNumber.isNotBlank()) {
                    val sub = listOfNotNull(shopPhone.takeIf { it.isNotBlank() }, taxNumber.takeIf { it.isNotBlank() }).joinToString(" • ")
                    Text(
                        text = sub,
                        color = Color.DarkGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.5.dp, Color.Black))
                ) {
                    ExpenseMetadataRow(
                        valueText = invoiceNumber,
                        labelText = if (lang == "AR") "فاترة رقم :" else "N° Facture :",
                        heightDp = 42
                    )
                    ExpenseMetadataRow(
                        valueText = expense.title,
                        labelText = if (lang == "AR") "نوع المصروف :" else "Type Dépense :",
                        heightDp = 44,
                        maxLines = 2,
                        valueFontSize = 13
                    )
                    ExpenseMetadataRow(
                        valueText = dateFormatted,
                        labelText = if (lang == "AR") "التاريخ :" else "Date :",
                        heightDp = 42
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color(0xFFFF0000))
                        .border(BorderStroke(1.5.dp, Color.Black)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(BorderStroke(0.75.dp, Color.Black)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = MoneyUtils.format(expense.amountDouble, currency),
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(BorderStroke(0.75.dp, Color.Black)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lang == "AR") "ثمن المصروف" else "MONTANT",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            PdfReportExporter.printExpenseVoucher(
                                context = context,
                                expense = expense,
                                currency = currency,
                                shopName = shopName,
                                shopPhone = shopPhone,
                                taxNumber = taxNumber
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "AR") "طباعة السند" else "Imprimer A4", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            PdfReportExporter.exportExpenseVoucherPdf(
                                context = context,
                                expense = expense,
                                currency = currency,
                                shopName = shopName,
                                shopPhone = shopPhone,
                                taxNumber = taxNumber
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "AR") "مشاركة PDF" else "Partager PDF", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            launchThermalExpensePrint(viewModel.printerConnectionType.value)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                    ) {
                        Icon(Icons.Filled.Receipt, contentDescription = "Thermal Receipt", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "AR") "حذف المصروف" else "Supprimer", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(if (lang == "AR") "إغلاق" else "Fermer", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseMetadataRow(
    valueText: String,
    labelText: String,
    heightDp: Int,
    maxLines: Int = 1,
    valueFontSize: Int = 14
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight()
                .border(BorderStroke(0.75.dp, Color.Black))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = valueText,
                color = Color.Black,
                fontSize = valueFontSize.sp,
                fontWeight = FontWeight.Bold,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(BorderStroke(0.75.dp, Color.Black))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = labelText,
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
