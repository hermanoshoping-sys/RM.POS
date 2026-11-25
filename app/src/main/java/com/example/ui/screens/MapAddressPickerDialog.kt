package com.example.ui.screens

import android.Manifest
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSPrimary
import com.example.util.GeoLocationHelper
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MapAddressPickerDialog(
    initialLatitude: Double? = null,
    initialLongitude: Double? = null,
    initialAddress: String = "",
    lang: String = "AR",
    onAddressConfirmed: (address: String, latitude: Double, longitude: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val defaultLat = (initialLatitude ?: 33.5731).coerceIn(-90.0, 90.0)
    val defaultLon = (initialLongitude ?: -7.5898).coerceIn(-180.0, 180.0)

    var pickedLatitude by remember { mutableStateOf(defaultLat) }
    var pickedLongitude by remember { mutableStateOf(defaultLon) }
    var addressInput by remember { mutableStateOf(initialAddress) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isResolvingAddress by remember { mutableStateOf(false) }
    var isLocatingGps by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        if (addressInput.isBlank()) {
            isResolvingAddress = true
            val resolved = GeoLocationHelper.getAddressFromLocation(context, pickedLatitude, pickedLongitude, lang)
            isResolvingAddress = false
            if (resolved.isNotBlank() && !resolved.startsWith("الموقع:")) {
                addressInput = resolved
            }
        }
    }

    fun updatePickedCoordinates(lat: Double, lon: Double) {
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        pickedLatitude = lat
        pickedLongitude = lon
        coroutineScope.launch {
            isResolvingAddress = true
            val resolved = GeoLocationHelper.getAddressFromLocation(context, lat, lon, lang)
            isResolvingAddress = false
            if (resolved.isNotBlank()) {
                addressInput = resolved
            }
        }
    }

    fun flyMapTo(lat: Double, lon: Double) {
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        webViewRef?.evaluateJavascript("if (typeof flyToLocation === 'function') { flyToLocation($lat, $lon); }", null)
        updatePickedCoordinates(lat, lon)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            coroutineScope.launch {
                isLocatingGps = true
                val loc = GeoLocationHelper.getCurrentLocation(context)
                isLocatingGps = false
                if (loc != null) {
                    flyMapTo(loc.latitude, loc.longitude)
                    Toast.makeText(
                        context,
                        Bilingual.tr(lang, "تم تحديد موقعك الحالي!", "Position actuelle trouvée !", "Current location found!"),
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
                Bilingual.tr(lang, "يلزم إذن الوصول إلى الموقع", "Permission GPS requise", "Location permission required"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun runAddressSearch() {
        if (searchQuery.isBlank()) return
        coroutineScope.launch {
            isSearching = true
            val coords = GeoLocationHelper.searchCoordinatesFromQuery(searchQuery, context)
            isSearching = false
            if (coords != null) {
                flyMapTo(coords.first, coords.second)
            } else {
                val msg = Bilingual.tr(
                    lang,
                    "لم يعثر الباحث على نتائج؛ حدد الموقع مباشرة على الخريطة",
                    "Aucun résultat local; choisissez directement sur la carte",
                    "No results; pick location directly on the map"
                )
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF0284C7).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = Bilingual.tr(lang, "تحديد العنوان من الخريطة", "Choisir l'adresse sur la carte", "Pick Address on Map"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "انقر على الخريطة أو اسحب المؤشر لاختيار المكان",
                                    "Cliquez ou glissez le curseur sur la carte",
                                    "Tap or drag the marker on the map"
                                ),
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Surface(color = Color(0xFF1E293B), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    Bilingual.tr(lang, "ابحث عن شارع، حي أو مدينة...", "Rechercher une adresse, ville...", "Search street or city..."),
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { runAddressSearch() }),
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0284C7),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        )

                        Button(
                            onClick = { runAddressSearch() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isSearching && searchQuery.isNotBlank(),
                            modifier = Modifier.height(48.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(Bilingual.tr(lang, "بحث", "Chercher", "Search"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0A0F1D))
                ) {
                    LeafletMapWebView(
                        defaultLat = defaultLat,
                        defaultLon = defaultLon,
                        lang = lang,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("map_picker_webview"),
                        onCoordinatesPicked = { lat, lon -> updatePickedCoordinates(lat, lon) },
                        onWebViewReady = { webViewRef = it }
                    )

                    FloatingActionButton(
                        onClick = {
                            if (!GeoLocationHelper.hasLocationPermission(context)) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                coroutineScope.launch {
                                    isLocatingGps = true
                                    val loc = GeoLocationHelper.getCurrentLocation(context)
                                    isLocatingGps = false
                                    if (loc != null) {
                                        flyMapTo(loc.latitude, loc.longitude)
                                        Toast.makeText(
                                            context,
                                            Bilingual.tr(lang, "تم تحديد موقعك الحالي!", "Position actuelle !", "Current location!"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            Bilingual.tr(lang, "تعذر تحديد الموقع الحالي", "GPS indisponible", "GPS unavailable"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        containerColor = Color(0xFF0F172A),
                        contentColor = Color(0xFF38BDF8),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(48.dp)
                    ) {
                        if (isLocatingGps) {
                            CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.MyLocation, contentDescription = "Current Location", modifier = Modifier.size(22.dp))
                        }
                    }
                }

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.5f, %.5f", pickedLatitude, pickedLongitude),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )

                            if (isResolvingAddress) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        Bilingual.tr(lang, "جارٍ جلب العنوان...", "Recherche adresse...", "Resolving address..."),
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            label = {
                                Text(
                                    Bilingual.tr(lang, "العنوان المحدد (قابل للتعديل)", "Adresse sélectionnée", "Selected address"),
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0284C7),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Filled.Place, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF475569)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"), color = Color(0xFFCBD5E1))
                            }

                            Button(
                                onClick = {
                                    val finalAddress = if (addressInput.isNotBlank()) {
                                        addressInput.trim()
                                    } else {
                                        String.format(Locale.US, "GPS: %.5f, %.5f", pickedLatitude, pickedLongitude)
                                    }
                                    onAddressConfirmed(finalAddress, pickedLatitude, pickedLongitude)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
                                modifier = Modifier
                                    .weight(1.6f)
                                    .height(48.dp)
                                    .testTag("map_confirm_address_button")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Bilingual.tr(lang, "تأكيد واختيار هذا العنوان", "Confirmer cette adresse", "Confirm Address"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
