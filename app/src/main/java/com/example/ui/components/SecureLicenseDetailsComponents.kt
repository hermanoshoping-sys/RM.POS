package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.LicenseSecurityManager
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ActiveLicenseDetailsBox(
    lang: String,
    licenseType: String,
    licenseKeyId: String,
    licenseExpires: Long,
    licenseGraceUntil: Long,
    remainingDays: Long,
    remainingGraceDays: Long
) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val expiryStr = if (licenseExpires > 0L) dateFmt.format(Date(licenseExpires)) else "-"
    val graceStr = if (licenseGraceUntil > 0L) dateFmt.format(Date(licenseGraceUntil)) else "-"

    Surface(
        color = Color(0xFF064E3B).copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = Bilingual.tr(lang, "تفاصيل الرخصة الموقّعة", "Détails de la licence signée", "Signed License Details"),
                    color = Color(0xFF34D399),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            LicenseDetailRow(
                label = Bilingual.tr(lang, "الفئة / المفتاح:", "Type / Clé :", "Tier / Key ID:"),
                value = "$licenseType • ${licenseKeyId.ifBlank { LicenseSecurityManager.DEFAULT_KEY_ID }}",
                valueColor = Color.White
            )
            LicenseDetailRow(
                label = Bilingual.tr(lang, "انتهاء الاشتراك:", "Expiration :", "Subscription Expiry:"),
                value = Bilingual.tr(lang, "$expiryStr ($remainingDays يوم)", "$expiryStr ($remainingDays j)", "$expiryStr ($remainingDays d)"),
                valueColor = if (remainingDays in 1..7) Color(0xFFF59E0B) else Color(0xFF38BDF8)
            )
            LicenseDetailRow(
                label = Bilingual.tr(lang, "السماح دون إنترنت:", "Grâce hors-ligne :", "Offline Grace:"),
                value = Bilingual.tr(lang, "$graceStr ($remainingGraceDays يوم)", "$graceStr ($remainingGraceDays j)", "$graceStr ($remainingGraceDays d)"),
                valueColor = if (remainingGraceDays in 1..7) Color(0xFFF59E0B) else Color(0xFF6EE7B7)
            )
        }
    }
}

@Composable
private fun LicenseDetailRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 12.sp, color = Color.LightGray)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
internal fun ClockRollbackRecoveryBox(
    lang: String,
    onOpenDateSettings: () -> Unit,
    onRecheckNow: () -> Unit
) {
    Surface(
        color = Color(0xFF451A03).copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clock_rollback_recovery_box")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                Text(
                    text = Bilingual.tr(
                        lang,
                        "ترخيصك محفوظ — صحّح تاريخ الجهاز، أو تحقق عبر الإنترنت، أو ألصق رمز CLOCK_RESET الموقّع",
                        "Licence conservée — corrigez la date, vérifiez en ligne ou collez un jeton CLOCK_RESET",
                        "License preserved — fix device date, verify online, or paste a signed CLOCK_RESET token"
                    ),
                    color = Color(0xFFFDE68A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenDateSettings,
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(Bilingual.tr(lang, "ضبط الوقت", "Heure", "Date & Time"), color = Color(0xFFFDE68A), fontSize = 11.5.sp)
                }
                Button(
                    onClick = onRecheckNow,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("recheck_clock_license_button")
                ) {
                    Text(Bilingual.tr(lang, "إعادة التحقق", "Revérifier", "Re-check"), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun LicenseStatusBannerBox(text: String, isError: Boolean) {
    val accent = if (isError) Color(0xFFEF4444) else Color(0xFF10B981)
    Surface(
        color = accent.copy(alpha = 0.2f), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, color = if (isError) Color(0xFFFCA5A5) else Color(0xFF6EE7B7), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

internal fun performLicenseActivation(
    input: String,
    viewModel: PosViewModel,
    context: Context,
    lang: String,
    onError: (String) -> Unit,
    onSuccess: (String) -> Unit
) {
    val trimmed = input.trim()
    if (LicenseSecurityManager.isClockResetTokenPayload(trimmed)) {
        viewModel.applyClockResetToken(
            resetToken = trimmed,
            onSuccess = { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                onSuccess(msg)
            },
            onError = { err -> onError(err) }
        )
        return
    }
    val ok = viewModel.activateLicense(
        activationCode = trimmed,
        onSuccess = {
            val msg = Bilingual.tr(lang, "تم تفعيل الرخصة الموقّعة بنجاح!", "Licence signée activée !", "Signed license activated!")
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onSuccess(msg)
        },
        onError = { err -> onError(err) }
    )
    if (!ok && viewModel.licenseError.value != null) {
        onError(viewModel.licenseError.value ?: Bilingual.tr(lang, "فشل التفعيل", "Échec de l'activation", "Activation failed"))
    }
}
