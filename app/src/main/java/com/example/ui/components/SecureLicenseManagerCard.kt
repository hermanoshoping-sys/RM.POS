package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.LicenseSecurityManager
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Cryptographic License Manager UI Component enforcing ECDSA P-256 signed tokens
 * with `kid` key rotation metadata and signed `grace_until` offline lease renewal.
 */
@Composable
fun SecureLicenseManagerCard(
    viewModel: PosViewModel,
    lang: String = "AR",
    modifier: Modifier = Modifier,
    onActivationSuccess: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    val isActivated by viewModel.isLicenseActivated.collectAsStateWithLifecycle()
    val isClockRollbackSuspended by viewModel.isClockRollbackSuspended.collectAsStateWithLifecycle()
    val isExpirySuspended by viewModel.isExpirySuspended.collectAsStateWithLifecycle()
    val licenseExpires by viewModel.licenseExpires.collectAsStateWithLifecycle()
    val licenseGraceUntil by viewModel.licenseGraceUntil.collectAsStateWithLifecycle()
    val licenseKeyId by viewModel.licenseKeyId.collectAsStateWithLifecycle()
    val licenseType by viewModel.licenseType.collectAsStateWithLifecycle()
    val needsOnlineRenewal by viewModel.needsOnlineRenewal.collectAsStateWithLifecycle()
    val bootLicenseError by viewModel.licenseError.collectAsStateWithLifecycle()
    val deviceId = remember { viewModel.getDeviceId() }

    var activationKeyInput by remember { mutableStateOf("") }
    var errorMessage by remember(bootLicenseError) { mutableStateOf(bootLicenseError) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isRenewingOnline by remember { mutableStateOf(false) }

    val liveValidation = remember(activationKeyInput, deviceId) {
        val trimmed = activationKeyInput.trim()
        if (trimmed.isEmpty()) null else LicenseSecurityManager.verifyLicenseToken(trimmed, currentDeviceId = deviceId)
    }
    val liveClockResetValidation = remember(activationKeyInput, deviceId) {
        val trimmed = activationKeyInput.trim()
        if (trimmed.isEmpty()) null else LicenseSecurityManager.verifyClockResetToken(trimmed, currentDeviceId = deviceId, context = context)
    }
    val isClockResetTokenValid = liveClockResetValidation?.isValid == true
    val isKeyFormatValid = liveValidation?.isValid == true || isClockResetTokenValid

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("license_manager_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(
            width = 1.5.dp,
            brush = if (isActivated) {
                Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF2563EB)))
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (isActivated) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFF38BDF8).copy(alpha = 0.18f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isActivated) Icons.Filled.Verified else Icons.Filled.VpnKey,
                            contentDescription = Bilingual.tr(lang, "حالة الرخصة", "Statut de la licence", "License Status"),
                            tint = if (isActivated) Color(0xFF10B981) else Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "أدخل الرمز الرقمي الموقّع المرتبط بمعرّف جهازك",
                            "Entrez le jeton signé lié à l'identifiant de cet appareil",
                            "Enter the signed token bound to this device ID"
                        ),
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    color = if (isActivated) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFFEF4444).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isActivated) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isActivated) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isActivated) {
                                Bilingual.tr(lang, "مفعّل", "Actif", "Active")
                            } else {
                                Bilingual.tr(lang, "غير مفعّل", "Inactif", "Inactive")
                            },
                            color = if (isActivated) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            DeviceIdBannerCard(deviceId = deviceId, lang = lang)

            if (isClockRollbackSuspended || isExpirySuspended) {
                ClockRollbackRecoveryBox(
                    lang = lang,
                    onOpenDateSettings = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_DATE_SETTINGS)) }
                    },
                    onRecheckNow = {
                        viewModel.checkLicenseExpirationWithAntiRollback()
                        if (viewModel.isLicenseActivated.value) {
                            successMessage = Bilingual.tr(lang, "تمت استعادة الترخيص تلقائياً!", "Licence restaurée automatiquement !", "License automatically restored!")
                            errorMessage = null
                            onActivationSuccess?.invoke()
                        } else {
                            errorMessage = viewModel.licenseError.value
                        }
                    }
                )
            }

            if (isActivated) {
                ActiveLicenseDetailsBox(
                    lang = lang,
                    licenseType = licenseType,
                    licenseKeyId = licenseKeyId,
                    licenseExpires = licenseExpires,
                    licenseGraceUntil = licenseGraceUntil,
                    remainingDays = viewModel.getRemainingLicenseDays(),
                    remainingGraceDays = viewModel.getRemainingGraceDays()
                )
            }

            if (isActivated || needsOnlineRenewal || isExpirySuspended || isClockRollbackSuspended) {
                OutlinedButton(
                    onClick = {
                        isRenewingOnline = true
                        errorMessage = null
                        successMessage = null
                        viewModel.verifyOnlineSubscription { ok, msg ->
                            isRenewingOnline = false
                            if (ok) {
                                successMessage = msg
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onActivationSuccess?.invoke()
                            } else {
                                errorMessage = msg
                            }
                        }
                    },
                    enabled = !isRenewingOnline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("renew_license_grace_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isRenewingOnline -> Bilingual.tr(lang, "جاري التحقق ومزامنة الوقت...", "Vérification en ligne...", "Verifying online...")
                            isClockRollbackSuspended || isExpirySuspended -> Bilingual.tr(
                                lang,
                                "التحقق عبر الإنترنت ومزامنة الوقت",
                                "Vérifier en ligne et synchroniser l'heure",
                                "Verify Online & Sync Clock"
                            )
                            else -> Bilingual.tr(lang, "تجديد فترة السماح عبر الإنترنت", "Renouveler la période hors-ligne", "Renew Offline Grace Online")
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = activationKeyInput,
                    onValueChange = {
                        activationKeyInput = it
                        errorMessage = null
                        successMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("activation_key_input"),
                    shape = RoundedCornerShape(14.dp),
                    label = {
                        Text(
                            text = Bilingual.tr(lang, "رمز التفعيل الرقمي الموقّع", "Jeton d'activation signé", "Signed Activation Token"),
                            color = Color(0xFF94A3B8)
                        )
                    },
                    placeholder = {
                        Text(
                            text = Bilingual.tr(lang, "ألصق رمز التفعيل الموقّع هنا...", "Collez le jeton signé ici...", "Paste signed token here..."),
                            color = Color(0xFF475569),
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = if (isKeyFormatValid) Color(0xFF10B981) else Color(0xFF38BDF8)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    textToolbar.hide()
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val pasted = cb?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    if (pasted.isNotBlank()) {
                                        activationKeyInput = LicenseSecurityManager.sanitizePastedToken(pasted)
                                        errorMessage = null
                                        successMessage = null
                                    }
                                },
                                modifier = Modifier.testTag("paste_activation_key_button")
                            ) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = Bilingual.tr(lang, "لصق", "Coller", "Paste"),
                                    tint = Color(0xFF38BDF8)
                                )
                            }
                            if (activationKeyInput.isNotEmpty()) {
                                IconButton(onClick = { textToolbar.hide(); activationKeyInput = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = Bilingual.tr(lang, "مسح", "Effacer", "Clear"), tint = Color.Gray)
                                }
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (activationKeyInput.isNotBlank()) {
                                textToolbar.hide()
                                focusManager.clearFocus(force = true)
                                performLicenseActivation(
                                    input = activationKeyInput, viewModel = viewModel, context = context, lang = lang,
                                    onError = { errorMessage = it },
                                    onSuccess = { successMessage = it; activationKeyInput = ""; onActivationSuccess?.invoke() }
                                )
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = if (isKeyFormatValid) Color(0xFF10B981) else Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B)
                    )
                )

                if (isClockResetTokenValid && liveClockResetValidation != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "رمز إعادة ضبط الساعة (CLOCK_RESET) صالح (${liveClockResetValidation.keyId})",
                                "Jeton CLOCK_RESET valide (${liveClockResetValidation.keyId})",
                                "Valid CLOCK_RESET token (${liveClockResetValidation.keyId})"
                            ),
                            color = Color(0xFF34D399),
                            fontSize = 11.sp
                        )
                    }
                } else if (liveValidation?.isValid == true) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val expStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(liveValidation.expiryTimestamp ?: 0L))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "توقيع رقمي صالح (${liveValidation.keyId}) • ينتهي في $expStr",
                                "Signature valide (${liveValidation.keyId}) • Expire le $expStr",
                                "Valid signature (${liveValidation.keyId}) • Expires $expStr"
                            ),
                            color = Color(0xFF34D399),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            errorMessage?.let { err ->
                LicenseStatusBannerBox(text = err, isError = true)
            }

            successMessage?.let { msg ->
                LicenseStatusBannerBox(text = msg, isError = false)
            }

            Button(
                onClick = {
                    textToolbar.hide()
                    focusManager.clearFocus(force = true)
                    val input = activationKeyInput.trim()
                    if (input.isBlank()) {
                        errorMessage = Bilingual.tr(lang, "يرجى إدخال رمز التفعيل الرقمي الموقّع", "Veuillez entrer le jeton d'activation signé", "Please enter the signed activation token")
                        return@Button
                    }
                    performLicenseActivation(
                        input = input, viewModel = viewModel, context = context, lang = lang,
                        onError = { errorMessage = it },
                        onSuccess = { successMessage = it; activationKeyInput = ""; onActivationSuccess?.invoke() }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("activate_license_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(Bilingual.tr(lang, "تفعيل الرخصة والمتابعة", "Activer la licence", "Activate License & Continue"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            if (isActivated) {
                OutlinedButton(
                    onClick = {
                        textToolbar.hide()
                        focusManager.clearFocus(force = true)
                        viewModel.deactivateLicense()
                        Toast.makeText(context, Bilingual.tr(lang, "تم إلغاء التفعيل بنجاح", "Licence désactivée", "License deactivated"), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("deactivate_license_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Bilingual.tr(lang, "إلغاء تفعيل الترخيص", "Désactiver la licence", "Deactivate License"))
                }
            }
        }
    }
}
