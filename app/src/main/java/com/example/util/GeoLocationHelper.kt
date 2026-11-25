package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.example.data.CustomerEntity
import com.example.data.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class GeoFenceResult(
    val isAllowed: Boolean,
    val isEnforced: Boolean = false,
    val distanceMeters: Double = 0.0,
    val radiusMeters: Double = 150.0,
    val isPermissionMissing: Boolean = false,
    val isGpsDisabled: Boolean = false,
    val errorMessage: String? = null
)

object GeoLocationHelper {
    private const val TAG = "GeoLocationHelper"
    private const val PREFS_GEOCACHE = "rmpos_geocache_prefs"
    private const val MAX_CACHE_ENTRIES = 200

    private val reverseMemoryCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    private val forwardMemoryCache = object : LinkedHashMap<String, Pair<Double, Double>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Double, Double>>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    fun clearMemoryCache() {
        synchronized(reverseMemoryCache) { reverseMemoryCache.clear() }
        synchronized(forwardMemoryCache) { forwardMemoryCache.clear() }
    }

    private fun reverseCacheKey(latitude: Double, longitude: Double, lang: String): String {
        return String.format(java.util.Locale.US, "rev_%s_%.4f_%.4f", lang, latitude, longitude)
    }

    private fun forwardCacheKey(query: String): String {
        return "fwd_${query.trim().lowercase(java.util.Locale.ROOT)}"
    }

    private fun getCachedReverseAddress(context: Context, key: String): String? {
        synchronized(reverseMemoryCache) {
            reverseMemoryCache[key]?.let { return it }
        }
        val persisted = context.getSharedPreferences(PREFS_GEOCACHE, Context.MODE_PRIVATE)
            .getString(key, null)
            ?.takeIf { it.isNotBlank() }
        if (persisted != null) {
            synchronized(reverseMemoryCache) {
                reverseMemoryCache[key] = persisted
            }
        }
        return persisted
    }

    private fun cacheReverseAddress(context: Context, key: String, address: String) {
        if (address.isBlank()) return
        synchronized(reverseMemoryCache) {
            reverseMemoryCache[key] = address
        }
        val prefs = context.getSharedPreferences(PREFS_GEOCACHE, Context.MODE_PRIVATE)
        if (prefs.all.size >= MAX_CACHE_ENTRIES) {
            val keysToRemove = prefs.all.keys.take(20)
            prefs.edit().apply {
                keysToRemove.forEach { remove(it) }
                putString(key, address)
            }.apply()
        } else {
            prefs.edit().putString(key, address).apply()
        }
    }

    private fun getCachedForwardCoords(context: Context, key: String): Pair<Double, Double>? {
        synchronized(forwardMemoryCache) {
            forwardMemoryCache[key]?.let { return it }
        }
        val raw = context.getSharedPreferences(PREFS_GEOCACHE, Context.MODE_PRIVATE)
            .getString(key, null)
            ?.trim()
            ?: return null
        val parts = raw.split(",")
        if (parts.size == 2) {
            val lat = parts[0].toDoubleOrNull()
            val lon = parts[1].toDoubleOrNull()
            if (lat != null && lon != null) {
                val pair = Pair(lat, lon)
                synchronized(forwardMemoryCache) {
                    forwardMemoryCache[key] = pair
                }
                return pair
            }
        }
        return null
    }

    private fun cacheForwardCoords(context: Context, key: String, coords: Pair<Double, Double>) {
        synchronized(forwardMemoryCache) {
            forwardMemoryCache[key] = coords
        }
        val prefs = context.getSharedPreferences(PREFS_GEOCACHE, Context.MODE_PRIVATE)
        val serialized = "${coords.first},${coords.second}"
        if (prefs.all.size >= MAX_CACHE_ENTRIES) {
            val keysToRemove = prefs.all.keys.take(20)
            prefs.edit().apply {
                keysToRemove.forEach { remove(it) }
                putString(key, serialized)
            }.apply()
        } else {
            prefs.edit().putString(key, serialized).apply()
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (com.example.BuildConfig.DEBUG) {
            android.util.Log.e(TAG, message, throwable)
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    fun isLocationServiceEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        val gps = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
        val net = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }
        return gps || net
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            "${meters.roundToInt()} م"
        } else {
            String.format(java.util.Locale.US, "%.1f كم", meters / 1000.0)
        }
    }

    fun openInGoogleMaps(context: Context, latitude: Double, longitude: Double, label: String = "الزبون") {
        try {
            val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
                val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (ex: Exception) {
                logError("Failed to open browser maps intent", ex)
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context, timeoutMs: Long = 7000L): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null

        // 1. Check recent last known location from providers
        var bestLastKnown: Location? = null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (provider in providers) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLastKnown == null || loc.time > bestLastKnown.time) {
                            bestLastKnown = loc
                        }
                    }
                }
            } catch (ex: Exception) {
                logError("Failed querying location provider: $provider", ex)
            }
        }

        // If last known location was recorded within the last 45 seconds, use it directly
        val now = System.currentTimeMillis()
        if (bestLastKnown != null && (now - bestLastKnown.time) < 45_000L) {
            return@withContext bestLastKnown
        }

        // 2. Fetch fresh live fix with timeout
        val freshLocation = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancellationSignal = CancellationSignal()
                    continuation.invokeOnCancellation { cancellationSignal.cancel() }
                    val executor = Executors.newSingleThreadExecutor()

                    val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        LocationManager.GPS_PROVIDER
                    } else {
                        LocationManager.NETWORK_PROVIDER
                    }

                    try {
                        lm.getCurrentLocation(
                            provider,
                            cancellationSignal,
                            executor
                        ) { loc ->
                            executor.shutdown()
                            if (continuation.isActive) {
                                continuation.resume(loc)
                            }
                        }
                    } catch (e: Exception) {
                        executor.shutdown()
                        if (continuation.isActive) {
                            continuation.resume(bestLastKnown)
                        }
                    }
                } else {
                    // Fallback for API < 30
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            try { lm.removeUpdates(this) } catch (e: Exception) { logError("Failed to remove updates", e) }
                            if (continuation.isActive) continuation.resume(location)
                        }
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    continuation.invokeOnCancellation {
                        try { lm.removeUpdates(listener) } catch (e: Exception) { logError("Failed to remove updates on cancellation", e) }
                    }

                    try {
                        val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            LocationManager.GPS_PROVIDER
                        } else {
                            LocationManager.NETWORK_PROVIDER
                        }
                        lm.requestSingleUpdate(provider, listener, null)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resume(bestLastKnown)
                    }
                }
            }
        }

        freshLocation ?: bestLastKnown
    }

    /**
     * Checks whether the current user is allowed to open / create an invoice for the given customer.
     * If user's enforceCustomerGeoLocation == true AND customer has isGeoLocationRequired == true,
     * verifies that the device is physically located within customer.geoRadiusMeters of (lat, lon).
     */
    suspend fun verifyCustomerLocationForInvoice(
        context: Context,
        customer: CustomerEntity,
        currentUser: UserEntity?,
        lang: String = "AR"
    ): GeoFenceResult = withContext(Dispatchers.Default) {
        // If user does not have geofence enforcement turned on, allow unconditionally
        val isEnforced = currentUser?.enforceCustomerGeoLocation == true
        if (!isEnforced) {
            return@withContext GeoFenceResult(isAllowed = true, isEnforced = false)
        }

        // If customer does not require geofencing or doesn't have coordinates set, allow
        if (!customer.isGeoLocationRequired || customer.latitude == null || customer.longitude == null) {
            return@withContext GeoFenceResult(isAllowed = true, isEnforced = false)
        }

        val targetLat = customer.latitude
        val targetLon = customer.longitude
        val allowedRadius = if (customer.geoRadiusMeters > 0.0) customer.geoRadiusMeters else 150.0

        // 1. Permission Check
        if (!hasLocationPermission(context)) {
            val msg = if (lang == "AR") {
                "⚠️ يلزم منح إذن الوصول إلى الموقع الجغرافي (GPS) للتحقق من التواجد في مقر الزبون (${customer.name}) قبل فتح الفاتورة."
            } else {
                "⚠️ L'autorisation de géolocalisation (GPS) est requise pour vérifier la présence chez le client (${customer.name})."
            }
            return@withContext GeoFenceResult(
                isAllowed = false,
                isEnforced = true,
                radiusMeters = allowedRadius,
                isPermissionMissing = true,
                errorMessage = msg
            )
        }

        // 2. Location Services Check
        if (!isLocationServiceEnabled(context)) {
            val msg = if (lang == "AR") {
                "⚠️ خدمة تحديد الموقع الجغرافي (GPS) معطلة على هذا الجهاز. يرجى تفعيل الموقع للتأكد من التواجد بمقر الزبون (${customer.name})."
            } else {
                "⚠️ Le GPS est désactivé sur cet appareil. Veuillez activer la localisation pour vérifier la présence chez (${customer.name})."
            }
            return@withContext GeoFenceResult(
                isAllowed = false,
                isEnforced = true,
                radiusMeters = allowedRadius,
                isGpsDisabled = true,
                errorMessage = msg
            )
        }

        // 3. Acquire Device Location
        val deviceLoc = getCurrentLocation(context)
        if (deviceLoc == null) {
            val msg = if (lang == "AR") {
                "⚠️ تعذر التقاط إشارة GPS الحالية للتحقق من موقع الزبون (${customer.name}). يرجى التأكد من اتصال GPS والمحاولة ثانية."
            } else {
                "⚠️ Impossible de capter le signal GPS actuel pour valider la position de (${customer.name})."
            }
            return@withContext GeoFenceResult(
                isAllowed = false,
                isEnforced = true,
                radiusMeters = allowedRadius,
                errorMessage = msg
            )
        }

        // 4. Distance Calculation
        val distance = calculateDistanceMeters(
            deviceLoc.latitude,
            deviceLoc.longitude,
            targetLat,
            targetLon
        )

        if (distance <= allowedRadius) {
            // Inside allowed geofence!
            return@withContext GeoFenceResult(
                isAllowed = true,
                isEnforced = true,
                distanceMeters = distance,
                radiusMeters = allowedRadius
            )
        } else {
            // Outside allowed geofence!
            val distanceStr = formatDistance(distance)
            val radiusStr = formatDistance(allowedRadius)
            val msg = if (lang == "AR") {
                "🚫 لا يمكن فتح فاتورة باسم الزبون (${customer.name})!\n\n" +
                "تم تفعيل ميزة إلزام التواجد الجغرافي لحسابك، وموقعك الحالي يبعد بمقدار $distanceStr عن موقع الزبون المحدد.\n" +
                "الحد الأقصى المسموح به: $radiusStr.\n\n" +
                "يرجى التواجد في مقر الزبون لإتمام فتح الفاتورة."
            } else {
                "🚫 Impossible d'ouvrir une facture pour (${customer.name})!\n\n" +
                "Votre compte exige la présence sur place. Vous êtes actuellement à $distanceStr du client.\n" +
                "Rayon autorisé: $radiusStr.\n\n" +
                "Vous devez être sur le lieu du client pour facturer."
            }

            return@withContext GeoFenceResult(
                isAllowed = false,
                isEnforced = true,
                distanceMeters = distance,
                radiusMeters = allowedRadius,
                errorMessage = msg
            )
        }
    }

    /**
     * Resolves human-readable street address from coordinates using persistent local cache
     * and Android native Geocoder, with formatted coordinate fallback.
     */
    suspend fun getAddressFromLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        lang: String = "AR"
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = reverseCacheKey(latitude, longitude, lang)
        getCachedReverseAddress(context, cacheKey)?.let { cached ->
            return@withContext cached
        }

        // 1. Try Android Native Geocoder with bounded timeout
        try {
            if (android.location.Geocoder.isPresent()) {
                val locale = if (lang == "AR") java.util.Locale("ar") else java.util.Locale("fr")
                val geocoder = android.location.Geocoder(context, locale)
                val addresses = withTimeoutOrNull(2500L) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine<List<android.location.Address>?> { cont ->
                            geocoder.getFromLocation(latitude, longitude, 1, object : android.location.Geocoder.GeocodeListener {
                                override fun onGeocode(addrs: MutableList<android.location.Address>) {
                                    if (cont.isActive) cont.resume(addrs)
                                }
                                override fun onError(errorMessage: String?) {
                                    if (cont.isActive) cont.resume(null)
                                }
                            })
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)
                    }
                }
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val parts = mutableListOf<String>()
                    val thoroughfare = addr.thoroughfare // Street name
                    val subThoroughfare = addr.subThoroughfare // Building number
                    val subLocality = addr.subLocality // District / Neighborhood
                    val locality = addr.locality // City
                    val adminArea = addr.adminArea // Region/State
                    val country = addr.countryName

                    if (!thoroughfare.isNullOrBlank()) {
                        if (!subThoroughfare.isNullOrBlank()) {
                            parts.add("$thoroughfare $subThoroughfare")
                        } else {
                            parts.add(thoroughfare)
                        }
                    }
                    if (!subLocality.isNullOrBlank() && !parts.contains(subLocality)) {
                        parts.add(subLocality)
                    }
                    if (!locality.isNullOrBlank() && !parts.contains(locality)) {
                        parts.add(locality)
                    } else if (!adminArea.isNullOrBlank() && !parts.contains(adminArea)) {
                        parts.add(adminArea)
                    }
                    if (!country.isNullOrBlank() && !parts.contains(country)) {
                        parts.add(country)
                    }

                    if (parts.isNotEmpty()) {
                        val resolved = parts.joinToString("، ")
                        cacheReverseAddress(context, cacheKey, resolved)
                        return@withContext resolved
                    }

                    val line = addr.getAddressLine(0)
                    if (!line.isNullOrBlank()) {
                        cacheReverseAddress(context, cacheKey, line)
                        return@withContext line
                    }
                }
            }
        } catch (e: Exception) {
            logError("Native geocoder reverse lookup failed", e)
        }

        // Fallback formatted coordinates (not cached so native Geocoder can resolve later)
        return@withContext String.format(java.util.Locale.US, "الموقع: %.5f, %.5f", latitude, longitude)
    }

    /**
     * Searches coordinates for an address query using persistent local cache and Android native Geocoder.
     */
    suspend fun searchCoordinatesFromQuery(query: String, context: Context? = null): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null

        val cacheKey = forwardCacheKey(query)
        if (context != null) {
            getCachedForwardCoords(context, cacheKey)?.let { cached ->
                return@withContext cached
            }
        }

        // Android Native Geocoder (no third-party Nominatim HTTP calls)
        if (context != null) {
            try {
                if (android.location.Geocoder.isPresent()) {
                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                    val trimmedQuery = query.trim()
                    val list = withTimeoutOrNull(2500L) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            suspendCancellableCoroutine<List<android.location.Address>?> { cont ->
                                geocoder.getFromLocationName(trimmedQuery, 1, object : android.location.Geocoder.GeocodeListener {
                                    override fun onGeocode(addrs: MutableList<android.location.Address>) {
                                        if (cont.isActive) cont.resume(addrs)
                                    }
                                    override fun onError(errorMessage: String?) {
                                        if (cont.isActive) cont.resume(null)
                                    }
                                })
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocationName(trimmedQuery, 1)
                        }
                    }
                    if (!list.isNullOrEmpty()) {
                        val first = list[0]
                        val coords = Pair(first.latitude, first.longitude)
                        cacheForwardCoords(context, cacheKey, coords)
                        return@withContext coords
                    }
                }
            } catch (e: Exception) {
                logError("Native geocoder forward search failed", e)
            }
        }

        null
    }
}
