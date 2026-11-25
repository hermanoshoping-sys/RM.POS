package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.GeoLocationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun ClientGeoFenceSection(
    cName: String,
    cAddress: String,
    onAddressChange: (String) -> Unit,
    cLatitude: Double?,
    onLatitudeChange: (Double?) -> Unit,
    cLongitude: Double?,
    onLongitudeChange: (Double?) -> Unit,
    cGeoRadius: Double,
    onGeoRadiusChange: (Double) -> Unit,
    cIsGeoRequired: Boolean,
    onIsGeoRequiredChange: (Boolean) -> Unit,
    isCapturingGps: Boolean,
    onCapturingGpsChange: (Boolean) -> Unit,
    canEditThisCustomer: Boolean,
    lang: String,
    coroutineScope: CoroutineScope,
    onRequestLocationPermission: () -> Unit,
    onOpenMapPicker: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (cIsGeoRequired) Color(0xFF10B981) else Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("📍", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = if (lang == "AR") "ربط الزبون بالموقع الجغرافي (GPS)" else "Lier au lieu géographique (GPS)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (lang == "AR") "منع فتح فاتورة إلا عند التواجد في موقعه" else "Interdire facturation hors lieu",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
                Switch(
                    checked = cIsGeoRequired,
                    onCheckedChange = { if (canEditThisCustomer) onIsGeoRequiredChange(it) },
                    enabled = canEditThisCustomer
                )
            }

            if (cIsGeoRequired) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (cLatitude != null && cLongitude != null)
                                String.format(Locale.US, "العرض: %.5f\nالطول: %.5f", cLatitude, cLongitude)
                            else
                                if (lang == "AR") "لم يتم تحديد الإحداثيات بعد" else "Position non définie",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (cLatitude != null) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { if (canEditThisCustomer) onOpenMapPicker() },
                            enabled = canEditThisCustomer,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF0284C7),
                                disabledContentColor = Color.Gray
                            ),
                            border = BorderStroke(1.dp, if (canEditThisCustomer) Color(0xFF0284C7) else Color.DarkGray),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (lang == "AR") "الخريطة" else "Carte", fontSize = 10.5.sp)
                        }

                        Button(
                            onClick = {
                                if (!GeoLocationHelper.hasLocationPermission(context)) {
                                    onRequestLocationPermission()
                                } else {
                                    coroutineScope.launch {
                                        onCapturingGpsChange(true)
                                        val loc = GeoLocationHelper.getCurrentLocation(context)
                                        onCapturingGpsChange(false)
                                        if (loc != null) {
                                            onLatitudeChange(loc.latitude)
                                            onLongitudeChange(loc.longitude)
                                            if (cAddress.isBlank()) {
                                                val resolved = GeoLocationHelper.getAddressFromLocation(context, loc.latitude, loc.longitude, lang)
                                                if (resolved.isNotBlank() && !resolved.startsWith("الموقع:")) {
                                                    onAddressChange(resolved)
                                                }
                                            }
                                            Toast.makeText(context, if (lang == "AR") "تم التقاط الموقع بنجاح!" else "Position capturée !", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, if (lang == "AR") "تعذر جلب الموقع. تأكد من تشغيل GPS" else "Échec GPS", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = canEditThisCustomer && !isCapturingGps,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (isCapturingGps) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (lang == "AR") "جارٍ الجلب..." else "Recherche...", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (lang == "AR") "موقعي الآن" else "Ma position", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (lang == "AR") "نطاق السماح (Rayon): ${cGeoRadius.toInt()} متر" else "Rayon toléré: ${cGeoRadius.toInt()} m",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2.0, 5.0, 10.0, 15.0, 20.0, 25.0, 50.0, 100.0, 150.0, 300.0, 500.0, 1000.0).forEach { r ->
                        FilterChip(
                            selected = cGeoRadius == r,
                            onClick = { if (canEditThisCustomer) onGeoRadiusChange(r) },
                            enabled = canEditThisCustomer,
                            label = { Text("${r.toInt()} م", fontSize = 10.sp, fontWeight = if (cGeoRadius == r) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                if (cLatitude != null && cLongitude != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { GeoLocationHelper.openInGoogleMaps(context, cLatitude, cLongitude, cName) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "AR") "عرض في الخريطة" else "Voir carte", fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (canEditThisCustomer) {
                                    onLatitudeChange(null)
                                    onLongitudeChange(null)
                                }
                            },
                            enabled = canEditThisCustomer,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFEF4444),
                                disabledContentColor = Color.Gray
                            ),
                            border = BorderStroke(1.dp, if (canEditThisCustomer) Color(0xFFEF4444) else Color.DarkGray),
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "AR") "حذف الإحداثيات" else "Effacer", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
