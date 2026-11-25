package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.*

internal data class EditablePurchaseLineItem(
    val productId: Long,
    val productName: String,
    val barcode: String,
    var quantity: Double,
    var unitPrice: Double
)

@Composable
internal fun EditPurchaseTopHeader(
    lang: String,
    currentSupplierName: String,
    currentInvoiceNumber: String,
    onDismiss: () -> Unit,
    onOpenPurchasesLog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF1E3A8A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = "Cart", tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(
                text = if (lang == "AR") "تعديل فواتير الموردين" else "Modifier Factures Fournisseurs",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
            val suppName = currentSupplierName.ifBlank { if (lang == "AR") "مورد غير محدد" else "Fournisseur standard" }
            Text(
                text = "${if (lang == "AR") "المورد:" else "Fournisseur:"} $suppName  (#$currentInvoiceNumber)",
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, Color(0xFF8CC63F)),
                modifier = Modifier.clickable(onClick = onOpenPurchasesLog)
            ) {
                Text(
                    text = if (lang == "AR") "سجل الفواتير" else "Journal Factures",
                    color = Color(0xFF8CC63F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3B0764)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
internal fun EditPurchaseMetaCards(
    lang: String,
    canEditInvoices: Boolean,
    currentInvoiceNumber: String,
    currentSupplierName: String,
    invoiceDateMillis: Long,
    dateFormat: SimpleDateFormat,
    onEditInvoiceNumber: () -> Unit,
    onEditSupplier: () -> Unit,
    onDateChanged: (Long) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (canEditInvoices) Color(0xFF38BDF8) else Color.Gray),
            modifier = Modifier
                .weight(1f)
                .clickable {
                    if (canEditInvoices) onEditInvoiceNumber()
                    else Toast.makeText(context, if (lang == "AR") "عذراً، تعديل رقم الفاتورة معطل لحسابك" else "Modification du numéro non autorisée", Toast.LENGTH_SHORT).show()
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (lang == "AR") "رقم الفاتورة" else "N° Facture", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    Text(currentInvoiceNumber, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (canEditInvoices) Text("✏️", fontSize = 11.sp)
                else Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
            }
        }

        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (canEditInvoices) Color(0xFF38BDF8) else Color.Gray),
            modifier = Modifier
                .weight(1.2f)
                .clickable {
                    if (canEditInvoices) onEditSupplier()
                    else Toast.makeText(context, if (lang == "AR") "عذراً، تبديل مورد الفاتورة معطل لحسابك" else "Modification du fournisseur non autorisée", Toast.LENGTH_SHORT).show()
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (lang == "AR") "المورد" else "Fournisseur", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    Text(
                        text = currentSupplierName.ifBlank { if (lang == "AR") "مورد عام" else "Fournisseur standard" },
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("✏️", fontSize = 11.sp)
            }
        }
    }

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable {
                val cal = Calendar.getInstance().apply { timeInMillis = invoiceDateMillis }
                DatePickerDialog(context, { _, y, m, d ->
                    val timeCal = Calendar.getInstance().apply { timeInMillis = invoiceDateMillis }
                    TimePickerDialog(context, { _, h, min ->
                        val newCal = Calendar.getInstance().apply { set(y, m, d, h, min, 0) }
                        onDateChanged(newCal.timeInMillis)
                    }, timeCal.get(Calendar.HOUR_OF_DAY), timeCal.get(Calendar.MINUTE), true).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Event, contentDescription = "Edit Date", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${if (lang == "AR") "تاريخ الفاتورة:" else "Date facture:"} ${dateFormat.format(Date(invoiceDateMillis))}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }
            Surface(color = Color(0xFF0284C7), shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = if (lang == "AR") "تعديل التاريخ ✏️" else "Modifier ✏️",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
internal fun EditPurchaseBottomSummarySection(
    totalAmount: Double,
    canEditInvoices: Boolean,
    lang: String,
    currency: String = "MAD",
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                color = Color(0xFFCBD5E1),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = MoneyUtils.format(totalAmount, currency),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(1f, 1f), 3f))
                    )
                }
            }

            Surface(
                color = Color(0xFFCBD5E1),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                modifier = Modifier.weight(0.7f).height(38.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (lang == "AR") "المجموع" else "TOTAL",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(1f, 1f), 3f))
                    )
                }
            }
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("edit_purchase_confirm_button"),
            shape = RoundedCornerShape(10.dp),
            enabled = canEditInvoices,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (canEditInvoices) Color(0xFF00A0E9) else Color(0xFF475569),
                disabledContainerColor = Color(0xFF475569)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            if (!canEditInvoices) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == "AR") "تعديل الفواتير معطل" else "Modification désactivée", color = Color.LightGray, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(if (lang == "AR") "تأكيد الفاتورة" else "Confirmer la Facture", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }

        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF030712)),
            border = BorderStroke(1.dp, Color(0xFF1F2937)),
            modifier = Modifier.fillMaxWidth().height(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1F2937), Color(0xFF0F172A), Color(0xFF030712)))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_pos_asset),
                    contentDescription = "RM.POS",
                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
