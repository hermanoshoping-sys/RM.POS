package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.data.CustomerEntity
import com.example.ui.theme.Bilingual
import com.example.util.GeoLocationHelper
import kotlinx.coroutines.launch
import java.util.Locale

// =============================================================================
// GOOGLE MAPS NAVIGATION & ROUTE TRACKING UTILITIES
// =============================================================================

fun launchGoogleMapsNavigation(context: Context, latitude: Double, longitude: Double) {
    try {
        val navUri = Uri.parse("google.navigation:q=$latitude,$longitude&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val fallbackUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude")
            context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
        }
    } catch (e: Exception) {
        val fallbackUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude")
        context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
    }
}

fun launchGoogleMapsView(context: Context, latitude: Double, longitude: Double, label: String = "Client") {
    try {
        val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        val fallbackUri = Uri.parse("https://maps.google.com/?q=$latitude,$longitude")
        context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CustomerRouteTrackingDialog(
    customer: CustomerEntity,
    lang: String = "AR",
    onUpdateLocation: ((lat: Double, lng: Double, address: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var distanceMeters by remember { mutableStateOf<Double?>(null) }
    var isCheckingGps by remember { mutableStateOf(false) }
    var showMapPicker by remember { mutableStateOf(false) }

    // Fetch current user GPS to calculate route distance
    LaunchedEffect(customer.latitude, customer.longitude) {
        val custLat = customer.latitude
        val custLon = customer.longitude
        if (customer.hasGeoLocation && custLat != null && custLon != null) {
            isCheckingGps = true
            val myLoc = GeoLocationHelper.getCurrentLocation(context)
            isCheckingGps = false
            if (myLoc != null) {
                distanceMeters = GeoLocationHelper.calculateDistanceMeters(
                    myLoc.latitude,
                    myLoc.longitude,
                    custLat,
                    custLon
                )
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            coroutineScope.launch {
                isCheckingGps = true
                val loc = GeoLocationHelper.getCurrentLocation(context)
                isCheckingGps = false
                if (loc != null) {
                    val addr = GeoLocationHelper.getAddressFromLocation(context, loc.latitude, loc.longitude, lang)
                    onUpdateLocation?.invoke(loc.latitude, loc.longitude, addr)
                    Toast.makeText(
                        context,
                        Bilingual.tr(lang, "تم تحديث موقع الزبون بنجاح!", "Position enregistrée !", "Location saved!"),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        Bilingual.tr(lang, "تعذر تحديد الموقع. تأكد من تشغيل GPS", "GPS indisponible", "GPS unavailable"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Toast.makeText(
                context,
                Bilingual.tr(lang, "يلزم إذن الوصول إلى الموقع", "Permission requise", "Location permission required"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (showMapPicker) {
        MapAddressPickerDialog(
            initialLatitude = customer.latitude,
            initialLongitude = customer.longitude,
            initialAddress = customer.address,
            lang = lang,
            onAddressConfirmed = { addr, lat, lon ->
                onUpdateLocation?.invoke(lat, lon, addr)
                showMapPicker = false
            },
            onDismiss = { showMapPicker = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF0284C7).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = Bilingual.tr(lang, "موقع ومسار الزبون", "Itinéraire & GPS Client", "Customer Route & GPS"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${customer.name} (${customer.displayCustomerNumber})",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val custLat = customer.latitude
                val custLon = customer.longitude
                if (customer.hasGeoLocation && custLat != null && custLon != null) {
                    val lat = custLat
                    val lon = custLon

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Place, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (customer.address.isNotBlank()) customer.address else "GPS: ${String.format(Locale.US, "%.5f, %.5f", lat, lon)}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            val distVal = distanceMeters
                            if (distVal != null) {
                                val distStr = if (distVal >= 1000.0) {
                                    String.format(Locale.US, "%.2f km", distVal / 1000.0)
                                } else {
                                    "${distVal.toInt()} m"
                                }
                                Surface(
                                    color = Color(0xFF065F46).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Route, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = Bilingual.tr(
                                                lang,
                                                "المسافة من موقعك الحالي: $distStr",
                                                "Distance de votre position : $distStr",
                                                "Distance from your location: $distStr"
                                            ),
                                            color = Color(0xFFA7F3D0),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { launchGoogleMapsNavigation(context, lat, lon) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Filled.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Bilingual.tr(lang, "تتبع الطريق في الخريطة (GPS Navigation)", "Lancer Itinéraire GPS (Maps)", "Start GPS Navigation"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { launchGoogleMapsView(context, lat, lon, customer.name) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Bilingual.tr(lang, "عرض الموقع في خرائط Google", "Voir sur Google Maps", "View on Google Maps"),
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { showMapPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.EditLocationAlt, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(lang, "تعديل أو اختيار موقع جديد للزبون", "Modifier l'emplacement", "Edit Location"),
                            color = Color(0xFF94A3B8),
                            fontSize = 12.5.sp
                        )
                    }
                } else {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.LocationOff, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(36.dp))
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "لم يتم حفظ الإحداثيات الجغرافية لهذا الزبون بعد",
                                    "Aucune position GPS enregistrée pour ce client",
                                    "No GPS coordinates saved for this customer yet"
                                ),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "يمكنك تحديد موقع الزبون بدقة عبر GPS أو من الخريطة لتتبع المسار نحوه بسهولة أثناء التوصيل.",
                                    "Enregistrez les coordonnées GPS pour lancer l'itinéraire lors des livraisons.",
                                    "Save GPS coordinates to easily navigate during deliveries."
                                ),
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Bilingual.tr(lang, "التقاط موقع الزبون الآن عبر GPS", "Capturer la position GPS actuelle", "Capture Current GPS Location"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showMapPicker = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Bilingual.tr(lang, "تحديد موقع الزبون على الخريطة", "Choisir sur la carte", "Choose on Map"),
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    if (customer.address.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = {
                                val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(customer.address)}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Directions, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "البحث بالعنوان في خرائط Google: ${customer.address}",
                                    "Chercher par adresse sur Maps: ${customer.address}",
                                    "Search address on Maps: ${customer.address}"
                                ),
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
