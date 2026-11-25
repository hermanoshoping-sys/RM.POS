package com.example.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CustomerPaymentEntity
import com.example.data.SupplierPaymentEntity
import com.example.ui.components.rememberBluetoothAwarePrintLauncher
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ThermalCustomerPaymentReceiptDialog(
    payment: CustomerPaymentEntity,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val shopName = settings.shopName.ifBlank { if (lang == "AR") "الشركة" else "Entreprise" }
    val shopPhone = settings.shopPhone
    val taxNumber = settings.taxNumber
    val shopAddress = settings.shopAddress
    val currency = settings.currency.ifBlank { "MAD" }
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    val launchCustomerPrint = rememberBluetoothAwarePrintLauncher(lang = lang) {
        val printerString = generateCustomerPaymentReceiptText(payment, shopName, shopPhone, taxNumber, currency)
        val pType = viewModel.printerConnectionType.value
        val pTarget = viewModel.printerMac.value.ifBlank { viewModel.printerName.value }
        com.example.printer.BluetoothPrinterService.printUnified(
            context = context, connectionType = pType, printerNameOrMac = pTarget,
            textToPrint = printerString, ip = viewModel.printerIp.value, port = viewModel.printerPort.value,
            jobTitle = "Receipt PAY-C-${payment.id}"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(340.dp).verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = shopName, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
                if (taxNumber.isNotBlank()) Text(text = taxNumber, color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                if (shopAddress.isNotBlank()) Text(text = shopAddress, color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                if (shopPhone.isNotBlank()) Text(text = "هاتف / Tél: $shopPhone", color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(6.dp))
                Text("-".repeat(38), color = Color.Gray)
                Text(
                    text = if (lang == "AR") "وصل استلام دفعة زبون" else "REÇU DE RÈGLEMENT CLIENT",
                    color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Text(text = "سداد دين / Reçu de Paiement", color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                Text("-".repeat(38), color = Color.Gray)

                ReceiptPaymentFactRow("رقم الوصل / Reçu N°:", "#PAY-C-${payment.id}")
                ReceiptPaymentFactRow("التاريخ / Date:", dateFormatter.format(Date(payment.timestamp)))
                ReceiptPaymentFactRow("اسم الزبون / Client:", payment.customerName)
                ReceiptPaymentFactRow("أمين الصندوق / Caissier:", payment.cashierName)
                ReceiptPaymentFactRow(
                    "طريقة الدفع / Mode:",
                    when (payment.paymentMethod) {
                        "CHECK" -> "شيك / Chèque"
                        "TRANSFER" -> "تحويل / Virement"
                        else -> "نقداً / Espèces"
                    }
                )
                if (payment.notes.isNotBlank()) ReceiptPaymentFactRow("مرجع / Notes:", payment.notes)
                Text("-".repeat(38), color = Color.Gray)

                ReceiptPaymentAmountRow(
                    label = if (lang == "AR") "الرصيد السابق (الدين القديم)" else "Solde Précédent (Dette)",
                    value = MoneyUtils.format(payment.previousBalanceDouble, currency),
                    isBold = false
                )
                ReceiptPaymentAmountRow(
                    label = if (lang == "AR") "المبلغ المستلم (المسدد)" else "Montant Réglé (Reçu)",
                    value = MoneyUtils.format(payment.amountDouble, currency),
                    isBold = true,
                    highlightColor = Color(0xFF15803D)
                )
                ReceiptPaymentAmountRow(
                    label = if (lang == "AR") "الرصيد المتبقي بذمته" else "Nouveau Solde Restant",
                    value = MoneyUtils.format(payment.newBalanceDouble, currency),
                    isBold = true,
                    highlightColor = if (payment.newBalanceDouble > 0) Color(0xFFDC2626) else Color(0xFF15803D)
                )

                Text("-".repeat(38), color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text("تم استلام المبلغ المذكور أعلاه كاملاً\nMerci pour votre confiance", color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("توقيع الزبون / Client:", color = Color.Gray, fontSize = 9.sp)
                    Text("توقيع المستلم / Caissier:", color = Color.Gray, fontSize = 9.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            launchCustomerPrint(viewModel.printerConnectionType.value)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == "AR") "طباعة بلوتوث" else "Imprimer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = {
                            sharePaymentPdf(
                                context, isCustomer = true, paymentId = payment.id, name = payment.customerName,
                                amount = payment.amountDouble, prev = payment.previousBalanceDouble, curr = payment.newBalanceDouble,
                                method = payment.paymentMethod, notes = payment.notes, cashier = payment.cashierName,
                                timestamp = payment.timestamp, shopName = shopName, phone = shopPhone, currency = currency
                            )
                        },
                        modifier = Modifier.size(48.dp).background(Color(0xFFDC2626), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).background(Color(0xFF64748B), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ThermalSupplierPaymentReceiptDialog(
    payment: SupplierPaymentEntity,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val shopName = settings.shopName.ifBlank { if (lang == "AR") "الشركة" else "Entreprise" }
    val shopPhone = settings.shopPhone
    val taxNumber = settings.taxNumber
    val shopAddress = settings.shopAddress
    val currency = settings.currency.ifBlank { "MAD" }
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US) }
    val launchSupplierPrint = rememberBluetoothAwarePrintLauncher(lang = lang) {
        val printerString = generateSupplierPaymentReceiptText(payment, shopName, shopPhone, taxNumber, currency)
        val pType = viewModel.printerConnectionType.value
        val pTarget = viewModel.printerMac.value.ifBlank { viewModel.printerName.value }
        com.example.printer.BluetoothPrinterService.printUnified(
            context = context, connectionType = pType, printerNameOrMac = pTarget,
            textToPrint = printerString, ip = viewModel.printerIp.value, port = viewModel.printerPort.value,
            jobTitle = "Receipt PAY-S-${payment.id}"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(340.dp).verticalScroll(rememberScrollState()).padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = shopName, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 17.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
                if (taxNumber.isNotBlank()) Text(text = taxNumber, color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                if (shopAddress.isNotBlank()) Text(text = shopAddress, color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                if (shopPhone.isNotBlank()) Text(text = "هاتف / Tél: $shopPhone", color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)

                Spacer(modifier = Modifier.height(6.dp))
                Text("-".repeat(38), color = Color.Gray)
                Text(
                    text = if (lang == "AR") "سند صرف / أداء دفعة مورد" else "BON DE PAIEMENT FOURNISSEUR",
                    color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Text(text = "تسوية مستحقات / Bon de Décaissement", color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                Text("-".repeat(38), color = Color.Gray)

                ReceiptPaymentFactRow("رقم السند / Bon N°:", "#PAY-S-${payment.id}")
                ReceiptPaymentFactRow("التاريخ / Date:", dateFormatter.format(Date(payment.timestamp)))
                ReceiptPaymentFactRow("اسم المورد / Fournisseur:", payment.supplierName)
                ReceiptPaymentFactRow("المسؤول / Caissier:", payment.cashierName)
                ReceiptPaymentFactRow(
                    "طريقة الأداء / Mode:",
                    when (payment.paymentMethod) {
                        "CHECK" -> "شيك / Chèque"
                        "TRANSFER" -> "تحويل / Virement"
                        "TRAITE" -> "كمبيالة / Traite"
                        else -> "نقداً / Espèces"
                    }
                )
                if (payment.notes.isNotBlank()) ReceiptPaymentFactRow("مرجع / Notes:", payment.notes)
                Text("-".repeat(38), color = Color.Gray)

                ReceiptPaymentAmountRow(
                    label = if (lang == "AR") "المستحق السابق للمورد" else "Dette Précédente Due",
                    value = MoneyUtils.format(payment.previousDebtDouble, currency),
                    isBold = false
                )
                ReceiptPaymentAmountRow(
                    label = if (lang == "AR") "المبلغ المسدد له" else "Montant Réglé au Fournisseur",
                    value = MoneyUtils.format(payment.amountDouble, currency),
                    isBold = true,
                    highlightColor = Color(0xFF2563EB)
                )
                ReceiptPaymentAmountRow(
                    label = if (lang == "AR") "المتبقي له في الذمة" else "Reste Dû au Fournisseur",
                    value = MoneyUtils.format(payment.newDebtDouble, currency),
                    isBold = true,
                    highlightColor = if (payment.newDebtDouble > 0) Color(0xFFDC2626) else Color(0xFF15803D)
                )

                Text("-".repeat(38), color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text("تم تسليم المبلغ للمورد ومطابقة الرصيد\nRèglement effectué avec succès", color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("توقيع المورد / Fournisseur:", color = Color.Gray, fontSize = 9.sp)
                    Text("توقيع المحاسب / Direction:", color = Color.Gray, fontSize = 9.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            launchSupplierPrint(viewModel.printerConnectionType.value)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == "AR") "طباعة بلوتوث" else "Imprimer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = {
                            sharePaymentPdf(
                                context, isCustomer = false, paymentId = payment.id, name = payment.supplierName,
                                amount = payment.amountDouble, prev = payment.previousDebtDouble, curr = payment.newDebtDouble,
                                method = payment.paymentMethod, notes = payment.notes, cashier = payment.cashierName,
                                timestamp = payment.timestamp, shopName = shopName, phone = shopPhone, currency = currency
                            )
                        },
                        modifier = Modifier.size(48.dp).background(Color(0xFFDC2626), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).background(Color(0xFF64748B), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptPaymentFactRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.DarkGray, fontSize = 11.sp)
        Text(text = value, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun ReceiptPaymentAmountRow(label: String, value: String, isBold: Boolean = false, highlightColor: Color = Color.Black) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = Color.Black, fontSize = 11.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
        Text(text = value, color = highlightColor, fontWeight = if (isBold) FontWeight.Black else FontWeight.Normal, fontSize = if (isBold) 13.sp else 11.sp)
    }
}

fun generateCustomerPaymentReceiptText(
    payment: CustomerPaymentEntity,
    shopName: String,
    shopPhone: String,
    taxNumber: String,
    currency: String = "MAD"
): String {
    val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(payment.timestamp))
    return buildString {
        appendLine(shopName)
        if (taxNumber.isNotBlank()) appendLine(taxNumber)
        if (shopPhone.isNotBlank()) appendLine("Tel: $shopPhone")
        appendLine("--------------------------------")
        appendLine("وصل استلام دفعة زبون")
        appendLine("RECU REGLEMENT CLIENT")
        appendLine("--------------------------------")
        appendLine("Recu N: #PAY-C-${payment.id}")
        appendLine("Date: $dateStr")
        appendLine("Client: ${payment.customerName}")
        appendLine("Mode: ${payment.paymentMethod}")
        if (payment.notes.isNotBlank()) appendLine("Ref: ${payment.notes}")
        appendLine("--------------------------------")
        appendLine("Dette Prec: ${MoneyUtils.format(payment.previousBalanceDouble, currency)}")
        appendLine("Montant Regle: ${MoneyUtils.format(payment.amountDouble, currency)}")
        appendLine("Reste Du: ${MoneyUtils.format(payment.newBalanceDouble, currency)}")
        appendLine("--------------------------------")
        appendLine("Caissier: ${payment.cashierName}")
        appendLine("Merci pour votre confiance")
        appendLine("\n\n\n")
    }
}

fun generateSupplierPaymentReceiptText(
    payment: SupplierPaymentEntity,
    shopName: String,
    shopPhone: String,
    taxNumber: String,
    currency: String = "MAD"
): String {
    val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(payment.timestamp))
    return buildString {
        appendLine(shopName)
        if (taxNumber.isNotBlank()) appendLine(taxNumber)
        if (shopPhone.isNotBlank()) appendLine("Tel: $shopPhone")
        appendLine("--------------------------------")
        appendLine("سند صرف / أداء دفعة مورد")
        appendLine("BON DE PAIEMENT FOURNISSEUR")
        appendLine("--------------------------------")
        appendLine("Bon N: #PAY-S-${payment.id}")
        appendLine("Date: $dateStr")
        appendLine("Fournisseur: ${payment.supplierName}")
        appendLine("Mode: ${payment.paymentMethod}")
        if (payment.notes.isNotBlank()) appendLine("Ref: ${payment.notes}")
        appendLine("--------------------------------")
        appendLine("Dette Prec: ${MoneyUtils.format(payment.previousDebtDouble, currency)}")
        appendLine("Montant Paye: ${MoneyUtils.format(payment.amountDouble, currency)}")
        appendLine("Reste Du: ${MoneyUtils.format(payment.newDebtDouble, currency)}")
        appendLine("--------------------------------")
        appendLine("Direction: ${payment.cashierName}")
        appendLine("Paiement effectue")
        appendLine("\n\n\n")
    }
}

fun sharePaymentPdf(
    context: Context,
    isCustomer: Boolean,
    paymentId: Long,
    name: String,
    amount: Double,
    prev: Double,
    curr: Double,
    method: String,
    notes: String,
    cashier: String,
    timestamp: Long,
    shopName: String,
    phone: String,
    currency: String = "MAD"
) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(400, 600, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, 400f, 600f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 18f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(shopName, 200f, 40f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        if (phone.isNotBlank()) canvas.drawText("Tél: $phone", 200f, 60f, paint)

        val docTitle = if (isCustomer) "وصل استلام دفعة زبون / REÇU CLIENT" else "سند صرف دفعة مورد / BON FOURNISSEUR"
        paint.textSize = 14f
        paint.isFakeBoldText = true
        paint.color = if (isCustomer) android.graphics.Color.parseColor("#15803D") else android.graphics.Color.parseColor("#1D4ED8")
        canvas.drawText(docTitle, 200f, 90f, paint)

        paint.color = android.graphics.Color.GRAY
        paint.strokeWidth = 1f
        canvas.drawLine(30f, 105f, 370f, 105f, paint)

        val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(timestamp))
        val refPrefix = if (isCustomer) "#PAY-C-" else "#PAY-S-"

        paint.color = android.graphics.Color.BLACK
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT

        var y = 130f
        canvas.drawText("الرقم / Réf:", 35f, y, paint)
        paint.isFakeBoldText = true; canvas.drawText("$refPrefix$paymentId", 180f, y, paint); paint.isFakeBoldText = false
        y += 24f; canvas.drawText("التاريخ / Date:", 35f, y, paint); canvas.drawText(dateStr, 180f, y, paint)
        y += 24f
        canvas.drawText(if (isCustomer) "الزبون / Client:" else "المورد / Fournisseur:", 35f, y, paint)
        paint.isFakeBoldText = true; canvas.drawText(name, 180f, y, paint); paint.isFakeBoldText = false
        y += 24f; canvas.drawText("طريقة الدفع / Mode:", 35f, y, paint); canvas.drawText(method, 180f, y, paint)
        if (notes.isNotBlank()) {
            y += 24f; canvas.drawText("ملاحظات / Notes:", 35f, y, paint); canvas.drawText(notes, 180f, y, paint)
        }
        y += 15f; paint.color = android.graphics.Color.GRAY; canvas.drawLine(30f, y, 370f, y, paint)
        y += 25f; paint.color = android.graphics.Color.BLACK; canvas.drawText("الرصيد السابق:", 35f, y, paint)
        canvas.drawText(MoneyUtils.format(prev, currency), 260f, y, paint)
        y += 25f; paint.isFakeBoldText = true
        paint.color = if (isCustomer) android.graphics.Color.parseColor("#15803D") else android.graphics.Color.parseColor("#1D4ED8")
        canvas.drawText("المبلغ المسدد:", 35f, y, paint)
        canvas.drawText(MoneyUtils.format(amount, currency), 260f, y, paint)
        paint.isFakeBoldText = false
        y += 25f; paint.color = android.graphics.Color.BLACK; canvas.drawText("الرصيد المتبقي:", 35f, y, paint)
        paint.isFakeBoldText = true; canvas.drawText(MoneyUtils.format(curr, currency), 260f, y, paint)
        paint.isFakeBoldText = false
        y += 15f; paint.color = android.graphics.Color.GRAY; canvas.drawLine(30f, y, 370f, y, paint)
        y += 30f; paint.textSize = 10f; paint.color = android.graphics.Color.DKGRAY
        canvas.drawText("المسؤول / Caissier: $cashier", 35f, y, paint)
        y += 30f; canvas.drawText("توقيع الطرف الأول: ....................", 35f, y, paint)
        canvas.drawText("توقيع الطرف الثاني: ....................", 220f, y, paint)
        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "receipt_${refPrefix.replace("#", "")}$paymentId.pdf")
        FileOutputStream(file).use { pdfDocument.writeTo(it) }
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Receipt PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
