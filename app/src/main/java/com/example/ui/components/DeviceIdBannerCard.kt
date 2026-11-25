package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Bilingual

/**
 * Reusable card displaying the app-scoped Device ID (SHA-256 of ANDROID_ID + applicationId)
 * with one-tap clipboard copy for license activation and emergency recovery.
 */
@Composable
fun DeviceIdBannerCard(
    deviceId: String,
    lang: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @Suppress("UNUSED_PARAMETER")
    showAppVersion: Boolean = false
) {
    val context = LocalContext.current
    val copyDeviceIdToClipboard = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("RM.POS Device ID", deviceId))
        Toast.makeText(
            context,
            Bilingual.tr(
                lang,
                "تم نسخ معرّف الجهاز: $deviceId",
                "ID appareil copié : $deviceId",
                "Device ID copied: $deviceId"
            ),
            Toast.LENGTH_SHORT
        ).show()
    }

    Surface(
        color = Color(0xFF0B1324),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_id_banner_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.PermDeviceInformation,
                    contentDescription = Bilingual.tr(lang, "معرّف الجهاز", "ID de l'appareil", "Device ID"),
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = subtitle ?: Bilingual.tr(
                            lang,
                            "معرّف الجهاز (Device ID)",
                            "ID de l'appareil (Device ID)",
                            "Device ID"
                        ),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = deviceId,
                        color = Color(0xFF38BDF8),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clickable { copyDeviceIdToClipboard() }
                            .testTag("device_id_value")
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = copyDeviceIdToClipboard,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF38BDF8)
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("copy_device_id_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = Bilingual.tr(lang, "نسخ معرّف الجهاز", "Copier l'ID appareil", "Copy Device ID"),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
