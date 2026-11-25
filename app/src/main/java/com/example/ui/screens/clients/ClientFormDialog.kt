package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.UserEntity
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSPrimary
import java.util.Locale

@Composable
fun ClientFormDialog(
    editingCustomerId: Long?,
    cName: String,
    onNameChange: (String) -> Unit,
    cPhone: String,
    onPhoneChange: (String) -> Unit,
    cAddress: String,
    onAddressChange: (String) -> Unit,
    cBalance: String,
    onBalanceChange: (String) -> Unit,
    cLatitude: Double?,
    onLatitudeChange: (Double?) -> Unit,
    cLongitude: Double?,
    onLongitudeChange: (Double?) -> Unit,
    cGeoRadius: Double,
    onGeoRadiusChange: (Double) -> Unit,
    cIsGeoRequired: Boolean,
    onIsGeoRequiredChange: (Boolean) -> Unit,
    cCustomerNumber: Long,
    cBarcode: String,
    isCapturingGps: Boolean,
    onCapturingGpsChange: (Boolean) -> Unit,
    currentUser: UserEntity?,
    lang: String,
    onRequestLocationPermission: () -> Unit,
    onOpenMapPicker: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val isEditingCustomer = editingCustomerId != null
                val canEditThisCustomer = if (isEditingCustomer) (currentUser?.hasEditCustomersAccess() ?: true) else (currentUser?.hasClientsAccess() ?: true)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (editingCustomerId == null) (if (lang == "AR") "إضافة عميل جديد" else "Nouveau Client")
                        else (if (lang == "AR") "تعديل بيانات العميل" else "Modifier Client"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (isEditingCustomer && !canEditThisCustomer) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = if (lang == "AR") "🔒 عرض فقط" else "🔒 Lecture seule",
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (isEditingCustomer && !canEditThisCustomer) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (lang == "AR") "ليس لديك صلاحية لتعديل بيانات هذا العميل بعد إنشائه. يمكنك فقط الاطلاع على بياناته."
                                else "Vous n'avez pas l'autorisation de modifier ce client après sa création.",
                                fontSize = 11.5.sp,
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (editingCustomerId != null && (cCustomerNumber > 0L || cBarcode.isNotBlank())) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (cCustomerNumber > 0L) {
                                Text(
                                    text = "رقم الزبون: #$cCustomerNumber",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            if (cBarcode.isNotBlank()) {
                                Text(
                                    text = "الباركود: $cBarcode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = cName,
                    onValueChange = { if (canEditThisCustomer) onNameChange(it) },
                    enabled = canEditThisCustomer,
                    label = { Text(Bilingual.tr(lang, "الاسم", "Nom", "Name"), color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = POSPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        disabledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        disabledTextColor = Color.LightGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = cPhone,
                    onValueChange = { if (canEditThisCustomer) onPhoneChange(it) },
                    enabled = canEditThisCustomer,
                    label = { Text(Bilingual.tr(lang, "رقم الهاتف", "Téléphone", "Phone"), color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = POSPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        disabledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        disabledTextColor = Color.LightGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = cAddress,
                    onValueChange = { if (canEditThisCustomer) onAddressChange(it) },
                    enabled = canEditThisCustomer,
                    label = { Text(Bilingual.tr(lang, "العنوان", "Adresse", "Address"), color = Color.Gray) },
                    trailingIcon = {
                        IconButton(
                            onClick = { if (canEditThisCustomer) onOpenMapPicker() },
                            enabled = canEditThisCustomer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Map,
                                contentDescription = "Map Address Picker",
                                tint = if (canEditThisCustomer) Color(0xFF0284C7) else Color.Gray
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = POSPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        disabledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        disabledTextColor = Color.LightGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cLatitude != null && cLongitude != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📍", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format(Locale.US, "%.4f, %.4f", cLatitude, cLongitude),
                                fontSize = 10.5.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = if (lang == "AR") "يمكنك إدخال العنوان عبر الخريطة" else "Saisie d'adresse via carte",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }

                    OutlinedButton(
                        onClick = { if (canEditThisCustomer) onOpenMapPicker() },
                        enabled = canEditThisCustomer,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF0284C7),
                            disabledContentColor = Color.Gray
                        ),
                        border = BorderStroke(1.dp, if (canEditThisCustomer) Color(0xFF0284C7).copy(alpha = 0.6f) else Color.DarkGray),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (lang == "AR") "إدخال العنوان من الخريطة 🗺️" else "Depuis la carte 🗺️",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = cBalance,
                    onValueChange = { if (canEditThisCustomer) onBalanceChange(it) },
                    enabled = canEditThisCustomer,
                    label = { Text(Bilingual.tr(lang, "الرصيد الافتتاحي", "Solde initial", "Initial Balance"), color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = POSPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        disabledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        disabledTextColor = Color.LightGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                ClientGeoFenceSection(
                    cName = cName,
                    cAddress = cAddress,
                    onAddressChange = onAddressChange,
                    cLatitude = cLatitude,
                    onLatitudeChange = onLatitudeChange,
                    cLongitude = cLongitude,
                    onLongitudeChange = onLongitudeChange,
                    cGeoRadius = cGeoRadius,
                    onGeoRadiusChange = onGeoRadiusChange,
                    cIsGeoRequired = cIsGeoRequired,
                    onIsGeoRequiredChange = onIsGeoRequiredChange,
                    isCapturingGps = isCapturingGps,
                    onCapturingGpsChange = onCapturingGpsChange,
                    canEditThisCustomer = canEditThisCustomer,
                    lang = lang,
                    coroutineScope = coroutineScope,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onOpenMapPicker = onOpenMapPicker
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(if (lang == "AR") "إلغاء" else "Annuler")
                    }
                    Button(
                        onClick = {
                            if (!canEditThisCustomer) {
                                Toast.makeText(
                                    context,
                                    if (lang == "AR") "🔒 عذراً، ليس لديك صلاحية تعديل بيانات العملاء بعد إنشائهم."
                                    else "🔒 Permission refusée : Modification des clients interdite.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            onSave()
                        },
                        enabled = canEditThisCustomer,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canEditThisCustomer) POSPrimary else Color(0xFF64748B),
                            disabledContainerColor = Color(0xFF334155),
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        if (!canEditThisCustomer) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (lang == "AR") "تعديل محظور" else "Modif bloquée", fontWeight = FontWeight.Bold)
                        } else {
                            Text(Bilingual.get("save", lang), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
