package com.example.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.Bilingual
import java.io.ByteArrayInputStream

private const val TAG = "MapLeafletWebView"
private const val MAP_ASSET_BASE_URL = "https://appassets.androidplatform.net/"
private const val MAP_ASSET_HOST = "appassets.androidplatform.net"
private const val MAP_PICK_SCHEME = "rmpos-map"

internal const val DEFAULT_OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val DEFAULT_OPENFREEMAP_ATTRIBUTION =
    "&copy; <a href=\"https://openfreemap.org\">OpenFreeMap</a> &copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors"

private const val DEFAULT_COMMERCIAL_MAP_ATTRIBUTION =
    "&copy; Map Provider &copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors"

private val PUBLIC_OSM_TILE_SERVER_REGEX = Regex(
    "^https://([a-c]\\.)?tile\\.openstreetmap\\.org(/|$)",
    RegexOption.IGNORE_CASE
)

internal enum class MapTileEngine {
    OPENFREEMAP_VECTOR,
    CUSTOM_RASTER_LEAFLET
}

internal data class ResolvedMapProvider(
    val engine: MapTileEngine,
    val url: String,
    val attribution: String
)

/**
 * Resolves the optional custom licensed raster tile provider injected via `MAP_TILE_URL_TEMPLATE`
 * (e.g., MapTiler, Stadia, Mapbox).
 *
 * Returns `null` when unset, non-HTTPS, or pointing to OpenStreetMap's public tile servers,
 * in compliance with the OSM Tile Usage Policy.
 */
internal fun resolveMapTileProvider(
    configuredUrl: String = com.example.BuildConfig.MAP_TILE_URL_TEMPLATE,
    configuredAttribution: String = com.example.BuildConfig.MAP_TILE_ATTRIBUTION
): Pair<String, String>? {
    val trimmedUrl = configuredUrl.trim()
    val trimmedAttribution = configuredAttribution.trim()
    val isHttps = trimmedUrl.startsWith("https://", ignoreCase = true)
    if (!isHttps || PUBLIC_OSM_TILE_SERVER_REGEX.containsMatchIn(trimmedUrl)) {
        return null
    }
    return trimmedUrl to trimmedAttribution.ifBlank { DEFAULT_COMMERCIAL_MAP_ATTRIBUTION }
}

/**
 * Resolves the active map provider for rendering:
 * - Uses the custom commercial raster tile URL (via bundled Leaflet 1.9.4) when a valid
 *   non-OSM HTTPS `MAP_TILE_URL_TEMPLATE` is configured.
 * - Otherwise uses OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`, MIT / free for
 *   commercial use, no API key required, independent of public OSM tile servers) via bundled
 *   MapLibre GL JS 4.7.1.
 */
internal fun resolveActiveMapProvider(
    configuredUrl: String = com.example.BuildConfig.MAP_TILE_URL_TEMPLATE,
    configuredAttribution: String = com.example.BuildConfig.MAP_TILE_ATTRIBUTION
): ResolvedMapProvider {
    val custom = resolveMapTileProvider(configuredUrl, configuredAttribution)
    if (custom != null) {
        return ResolvedMapProvider(
            engine = MapTileEngine.CUSTOM_RASTER_LEAFLET,
            url = custom.first,
            attribution = custom.second
        )
    }
    return ResolvedMapProvider(
        engine = MapTileEngine.OPENFREEMAP_VECTOR,
        url = DEFAULT_OPENFREEMAP_STYLE_URL,
        attribution = DEFAULT_OPENFREEMAP_ATTRIBUTION
    )
}

/**
 * Builds the compliant application User-Agent dynamically from BuildConfig.VERSION_NAME.
 */
internal fun buildMapUserAgent(): String {
    return "RMPOS-Android/${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.APPLICATION_ID}; Retail POS Address Picker)"
}

/**
 * Builds local Leaflet HTML using bundled `assets/leaflet/leaflet.{js,css}` and the
 * configured licensed commercial raster tile provider.
 */
private fun buildLeafletMapHtml(
    defaultLat: Double,
    defaultLon: Double,
    tileUrl: String,
    attribution: String
): String {
    val escapedUrl = tileUrl.replace("\\", "\\\\").replace("'", "\\'")
    val escapedAttribution = attribution.replace("\\", "\\\\").replace("'", "\\'")
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <link rel="stylesheet" href="https://$MAP_ASSET_HOST/assets/leaflet/leaflet.css" />
          <script src="https://$MAP_ASSET_HOST/assets/leaflet/leaflet.js"></script>
          <style>
            html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #0f172a; }
            .leaflet-container { background: #0f172a !important; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
            .leaflet-control-attribution {
              font-size: 10px !important;
              opacity: 0.94 !important;
              background: rgba(15, 23, 42, 0.88) !important;
              color: #e2e8f0 !important;
              padding: 2px 6px !important;
              border-radius: 4px 0 0 0;
            }
            .leaflet-control-attribution a { color: #38bdf8 !important; text-decoration: underline; }
            .leaflet-bar a { background-color: #1e293b !important; color: #ffffff !important; border-color: #334155 !important; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            var initialLat = $defaultLat;
            var initialLon = $defaultLon;
            var map = L.map('map', { zoomControl: true, attributionControl: true }).setView([initialLat, initialLon], 16);

            var baseTileLayer = L.tileLayer('$escapedUrl', {
              maxZoom: 19,
              attribution: '$escapedAttribution'
            });
            baseTileLayer.addTo(map);

            var customIcon = L.divIcon({
              className: 'map-custom-pin',
              html: '<div style="position:relative;width:32px;height:32px;display:flex;align-items:center;justify-content:center;"><div style="background-color:#EF4444;width:24px;height:24px;border-radius:50% 50% 50% 0;transform:rotate(-45deg);border:2.5px solid #FFFFFF;box-shadow:0 3px 8px rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;"><div style="width:7px;height:7px;background:#FFFFFF;border-radius:50%;transform:rotate(45deg);"></div></div></div>',
              iconSize: [32, 32],
              iconAnchor: [16, 30]
            });

            var marker = L.marker([initialLat, initialLon], { draggable: true, icon: customIcon }).addTo(map);

            function notifyLocationPicked(lat, lng) {
              window.location.href = '$MAP_PICK_SCHEME://pick?lat=' + encodeURIComponent(lat) + '&lon=' + encodeURIComponent(lng);
            }

            map.on('click', function(e) {
              marker.setLatLng(e.latlng);
              notifyLocationPicked(e.latlng.lat, e.latlng.lng);
            });

            marker.on('dragend', function() {
              var latlng = marker.getLatLng();
              notifyLocationPicked(latlng.lat, latlng.lng);
            });

            function flyToLocation(lat, lon) {
              map.flyTo([lat, lon], 17);
              marker.setLatLng([lat, lon]);
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Builds local MapLibre GL JS HTML using bundled `assets/maplibre/maplibre-gl.{js,css}` and
 * OpenFreeMap vector tiles (commercial-friendly, zero API key, no public OSM tile server usage).
 */
private fun buildOpenFreeMapHtml(
    defaultLat: Double,
    defaultLon: Double,
    styleUrl: String,
    lang: String
): String {
    val escapedStyleUrl = styleUrl.replace("\\", "\\\\").replace("'", "\\'")
    val errorText = Bilingual.tr(
        lang,
        "تعذّر تحميل الخريطة — تحقق من اتصال الإنترنت. يمكنك الاستمرار باستخدام البحث أو GPS.",
        "Impossible de charger la carte — vérifiez la connexion Internet. La recherche et le GPS restent disponibles.",
        "Unable to load map tiles — check your internet connection. Search and GPS remain available."
    ).replace("\\", "\\\\").replace("'", "\\'")

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <link rel="stylesheet" href="https://$MAP_ASSET_HOST/assets/maplibre/maplibre-gl.css" />
          <script src="https://$MAP_ASSET_HOST/assets/maplibre/maplibre-gl.js"></script>
          <style>
            html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #0f172a; overflow: hidden; }
            .maplibregl-ctrl-attrib {
              font-size: 10px !important;
              background: rgba(15, 23, 42, 0.88) !important;
              color: #e2e8f0 !important;
            }
            .maplibregl-ctrl-attrib a { color: #38bdf8 !important; }
            .maplibregl-ctrl-group { background: #1e293b !important; border: 1px solid #334155 !important; }
            .maplibregl-ctrl-group button { filter: invert(1); }
            .custom-pin-el {
              width: 32px;
              height: 32px;
              display: flex;
              align-items: center;
              justify-content: center;
              cursor: grab;
            }
            .custom-pin-drop {
              background-color: #EF4444;
              width: 24px;
              height: 24px;
              border-radius: 50% 50% 50% 0;
              transform: rotate(-45deg);
              border: 2.5px solid #FFFFFF;
              box-shadow: 0 3px 8px rgba(0, 0, 0, 0.5);
              display: flex;
              align-items: center;
              justify-content: center;
            }
            .custom-pin-dot {
              width: 7px;
              height: 7px;
              background: #FFFFFF;
              border-radius: 50%;
              transform: rotate(45deg);
            }
            #map-error-banner {
              display: none;
              position: absolute;
              top: 12px;
              left: 12px;
              right: 12px;
              z-index: 999;
              background: rgba(30, 41, 59, 0.94);
              color: #e2e8f0;
              border: 1px solid #38bdf8;
              border-radius: 12px;
              padding: 10px 14px;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
              font-size: 12.5px;
              text-align: center;
            }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <div id="map-error-banner">$errorText</div>
          <script>
            var initialLat = $defaultLat;
            var initialLon = $defaultLon;
            var mapInstance = null;
            var markerInstance = null;

            function notifyLocationPicked(lat, lng) {
              window.location.href = '$MAP_PICK_SCHEME://pick?lat=' + encodeURIComponent(lat) + '&lon=' + encodeURIComponent(lng);
            }

            function showMapError() {
              var el = document.getElementById('map-error-banner');
              if (el) el.style.display = 'block';
            }

            function hideMapError() {
              var el = document.getElementById('map-error-banner');
              if (el) el.style.display = 'none';
            }

            try {
              mapInstance = new maplibregl.Map({
                container: 'map',
                style: '$escapedStyleUrl',
                center: [initialLon, initialLat],
                zoom: 15,
                attributionControl: true
              });

              mapInstance.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-left');

              var pinEl = document.createElement('div');
              pinEl.className = 'custom-pin-el';
              pinEl.innerHTML = '<div class="custom-pin-drop"><div class="custom-pin-dot"></div></div>';

              markerInstance = new maplibregl.Marker({
                element: pinEl,
                draggable: true,
                anchor: 'bottom'
              })
                .setLngLat([initialLon, initialLat])
                .addTo(mapInstance);

              mapInstance.on('load', function() {
                hideMapError();
              });

              mapInstance.on('error', function(e) {
                if (e && e.error && !mapInstance.isStyleLoaded()) {
                  showMapError();
                }
              });

              mapInstance.on('click', function(e) {
                markerInstance.setLngLat([e.lngLat.lng, e.lngLat.lat]);
                notifyLocationPicked(e.lngLat.lat, e.lngLat.lng);
              });

              markerInstance.on('dragend', function() {
                var lngLat = markerInstance.getLngLat();
                notifyLocationPicked(lngLat.lat, lngLat.lng);
              });
            } catch (err) {
              showMapError();
            }

            function flyToLocation(lat, lon) {
              if (mapInstance) {
                mapInstance.flyTo({ center: [lon, lat], zoom: 16.5 });
              }
              if (markerInstance) {
                markerInstance.setLngLat([lon, lat]);
              }
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LeafletMapWebView(
    defaultLat: Double,
    defaultLon: Double,
    lang: String,
    modifier: Modifier = Modifier,
    onCoordinatesPicked: (Double, Double) -> Unit,
    onWebViewReady: (WebView) -> Unit
) {
    val activeProvider = remember { resolveActiveMapProvider() }

    val mapHtml = remember(defaultLat, defaultLon, lang, activeProvider) {
        when (activeProvider.engine) {
            MapTileEngine.CUSTOM_RASTER_LEAFLET -> buildLeafletMapHtml(
                defaultLat = defaultLat,
                defaultLon = defaultLon,
                tileUrl = activeProvider.url,
                attribution = activeProvider.attribution
            )
            MapTileEngine.OPENFREEMAP_VECTOR -> buildOpenFreeMapHtml(
                defaultLat = defaultLat,
                defaultLon = defaultLon,
                styleUrl = activeProvider.url,
                lang = lang
            )
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                // Compliant dynamic application User-Agent reading BuildConfig.VERSION_NAME
                settings.userAgentString = buildMapUserAgent()

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url ?: return null
                        if (url.scheme == "https" && url.host == MAP_ASSET_HOST) {
                            val assetSpec = when (url.path) {
                                "/assets/leaflet/leaflet.css" -> "leaflet/leaflet.css" to "text/css"
                                "/assets/leaflet/leaflet.js" -> "leaflet/leaflet.js" to "application/javascript"
                                "/assets/maplibre/maplibre-gl.css" -> "maplibre/maplibre-gl.css" to "text/css"
                                "/assets/maplibre/maplibre-gl.js" -> "maplibre/maplibre-gl.js" to "application/javascript"
                                else -> null
                            }
                            if (assetSpec != null) {
                                return try {
                                    val stream = ctx.assets.open(assetSpec.first)
                                    WebResourceResponse(assetSpec.second, "UTF-8", stream)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to load bundled map asset: ${assetSpec.first}", e)
                                    WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                                }
                            }
                            return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url ?: return true
                        if (url.scheme == MAP_PICK_SCHEME && url.host == "pick") {
                            val lat = url.getQueryParameter("lat")?.toDoubleOrNull()
                            val lon = url.getQueryParameter("lon")?.toDoubleOrNull()
                            if (lat != null && lon != null) {
                                onCoordinatesPicked(lat, lon)
                            }
                            return true
                        }
                        return url.host != MAP_ASSET_HOST
                    }
                }

                loadDataWithBaseURL(MAP_ASSET_BASE_URL, mapHtml, "text/html", "UTF-8", null)
                onWebViewReady(this)
            }
        },
        modifier = modifier
    )
}
