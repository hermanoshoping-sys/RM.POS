package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeoLocationHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        GeoLocationHelper.clearMemoryCache()
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("rmpos_geocache_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testOfflineForwardAndReverseFallbackReturnsSafeDefaults() = runBlocking {
        val coords = GeoLocationHelper.searchCoordinatesFromQuery("Casablanca, Morocco", context)
        assertNull("بدون وجود نتيجة مخبأة أو خدمة Geocoder محلية يجب إرجاع null دون إرسال طلب لـ Nominatim", coords)

        val address = GeoLocationHelper.getAddressFromLocation(context, 33.5731, -7.5898, lang = "AR")
        assertTrue(address.startsWith("الموقع:"))
    }

    @Test
    fun testLocalGeoCacheServesResultsOffline() = runBlocking {
        // Pre-populate persistent local cache
        val cachePrefs = context.getSharedPreferences("rmpos_geocache_prefs", Context.MODE_PRIVATE)
        cachePrefs.edit()
            .putString("rev_AR_33.5731_-7.5898", "شارع الحسن الثاني، الدار البيضاء")
            .putString("fwd_rabat center", "34.0209,-6.8416")
            .commit()

        val resolvedAddr = GeoLocationHelper.getAddressFromLocation(context, 33.5731, -7.5898, lang = "AR")
        assertEquals("شارع الحسن الثاني، الدار البيضاء", resolvedAddr)

        val resolvedCoords = GeoLocationHelper.searchCoordinatesFromQuery("Rabat Center", context)
        assertEquals(34.0209, resolvedCoords!!.first, 0.0001)
        assertEquals(-6.8416, resolvedCoords.second, 0.0001)
    }
}
