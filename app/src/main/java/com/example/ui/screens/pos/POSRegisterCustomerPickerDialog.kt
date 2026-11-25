package com.example.ui.screens.pos

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerEntity
import com.example.data.UserEntity
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSPrimary
import com.example.ui.viewmodels.PosViewModel
import com.example.util.GeoFenceResult
import com.example.util.GeoLocationHelper
import com.example.util.MoneyUtils
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

@Composable
fun POSCustomerPickerDialog(
    viewModel: PosViewModel,
    customersList: List<CustomerEntity>,
    selectedPartnerId: Long,
    currentUser: UserEntity?,
    storeCurrency: String,
    lang: String,
    onGeoBlocked: (customer: CustomerEntity, result: GeoFenceResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var custSearch by remember { mutableStateOf("") }
    val customerScanner = remember {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    val filteredCusts = remember(customersList, custSearch) {
        val q = custSearch.trim()
        if (q.isBlank()) customersList
        else customersList.filter { cust ->
            cust.name.contains(q, ignoreCase = true) ||
                cust.phone.contains(q) ||
                cust.barcode.contains(q, ignoreCase = true) ||
                cust.customerNumber.toString() == q ||
                "#${cust.customerNumber}" == q
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Bilingual.tr(lang, "اختيار الزبون للفاتورة", "Sélectionner le Client", "Select Invoice Customer"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Text(
                        text = "${filteredCusts.size}",
                        color = Color(0xFF1D4ED8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                OutlinedTextField(
                    value = custSearch,
                    onValueChange = { custSearch = it },
                    placeholder = {
                        Text(
                            Bilingual.tr(lang, "بحث بالاسم، الهاتف أو كودبار الزبون...", "Recherche nom, tél ou code-barres...", "Search name, phone or barcode..."),
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (custSearch.isNotEmpty()) {
                                IconButton(onClick = { custSearch = "" }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(
                                onClick = {
                                    customerScanner.startScan()
                                        .addOnSuccessListener { scannedBarcode ->
                                            val raw = scannedBarcode.rawValue?.trim() ?: ""
                                            if (raw.isNotBlank()) {
                                                val matched = customersList.firstOrNull { it.barcode.equals(raw, ignoreCase = true) }
                                                if (matched != null) {
                                                    val isGeo = matched.isGeoLocationRequired && matched.hasGeoLocation
                                                    if (currentUser?.enforceCustomerGeoLocation == true && isGeo) {
                                                        coroutineScope.launch {
                                                            val check = GeoLocationHelper.verifyCustomerLocationForInvoice(
                                                                context = context,
                                                                customer = matched,
                                                                currentUser = currentUser,
                                                                lang = lang
                                                            )
                                                            if (check.isAllowed) {
                                                                viewModel.selectedPartnerClientId.value = matched.id
                                                                onDismiss()
                                                            } else {
                                                                onGeoBlocked(matched, check)
                                                            }
                                                        }
                                                    } else {
                                                        viewModel.selectedPartnerClientId.value = matched.id
                                                        onDismiss()
                                                    }
                                                } else {
                                                    custSearch = raw
                                                    Toast.makeText(
                                                        context,
                                                        Bilingual.tr(lang, "تم مسح: $raw", "Scanné: $raw", "Scanned: $raw"),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(
                                                context,
                                                Bilingual.tr(lang, "تعذر مسح الباركود", "Échec du scan", "Scan failed"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                },
                                modifier = Modifier.size(34.dp).padding(end = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = "Scan Customer Barcode",
                                    tint = POSPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    item(key = "walk_in_guest") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    viewModel.selectedPartnerClientId.value = 1L
                                    onDismiss()
                                },
                            color = if (selectedPartnerId <= 1L) Color(0xFFE0F2FE) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selectedPartnerId <= 1L) Color(0xFF0284C7) else Color(0xFFCBD5E1))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    Bilingual.tr(lang, "عميل عام / زبون عادي (بدون حساب)", "Client de passage", "Walk-in Customer"),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    items(filteredCusts.filter { it.id > 1L }, key = { it.id }) { cust ->
                        val isSelected = selectedPartnerId == cust.id
                        val isGeo = cust.isGeoLocationRequired && cust.hasGeoLocation
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    if (currentUser?.enforceCustomerGeoLocation == true && isGeo) {
                                        coroutineScope.launch {
                                            val check = GeoLocationHelper.verifyCustomerLocationForInvoice(
                                                context = context,
                                                customer = cust,
                                                currentUser = currentUser,
                                                lang = lang
                                            )
                                            if (check.isAllowed) {
                                                viewModel.selectedPartnerClientId.value = cust.id
                                                onDismiss()
                                            } else {
                                                onGeoBlocked(cust, check)
                                            }
                                        }
                                    } else {
                                        viewModel.selectedPartnerClientId.value = cust.id
                                        onDismiss()
                                    }
                                },
                            color = if (isSelected) Color(0xFFE0F2FE) else Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF0284C7) else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (cust.customerNumber > 0L) {
                                            Surface(
                                                color = Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(0.8.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Text(
                                                    text = "#${cust.customerNumber}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF0F172A),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        if (isGeo) {
                                            Text("📍", fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
                                        }
                                        Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color.Black)
                                    }
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (cust.barcode.isNotBlank()) {
                                            Text(
                                                text = "🏷️ ${cust.barcode}",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF0284C7),
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                        }
                                        if (cust.phone.isNotBlank()) {
                                            Text(cust.phone, fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }
                                if (cust.balance > 0L) {
                                    Text(
                                        MoneyUtils.format(cust.balance, storeCurrency),
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"))
            }
        }
    )
}
