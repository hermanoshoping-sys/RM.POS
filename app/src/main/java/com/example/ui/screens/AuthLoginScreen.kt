package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.security.PasswordPolicy
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import com.example.ui.viewmodels.AuthSecurityViewModel

@Composable
fun LoginScreen(
    viewModel: PosViewModel,
    lang: String,
    authViewModel: AuthSecurityViewModel = viewModel.authSecurityViewModel
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val textToolbar = LocalTextToolbar.current
    var username by remember { mutableStateOf("") }
    var pinPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val serverError by authViewModel.loginError.collectAsStateWithLifecycle()

    var showRecoveryDialog by remember { mutableStateOf(false) }

    val displayError = localError ?: if (serverError != null) {
        if (serverError == "invalid_credentials") {
            Bilingual.tr(lang, "اسم المستخدم أو كلمة السر غير صحيحة!", "Nom d'utilisateur ou mot de passe incorrect !", "Invalid username or password!")
        } else {
            serverError
        }
    } else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030A1C))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_pos_auth_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF040C22).copy(alpha = 0.55f),
                            Color(0xFF030A1C).copy(alpha = 0.78f),
                            Color(0xFF020612).copy(alpha = 0.94f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                    Box(
                        modifier = Modifier.size(110.dp).background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF00A3FF).copy(alpha = 0.45f), Color(0xFF0055D4).copy(alpha = 0.15f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(26.dp), color = Color.Transparent, shadowElevation = 18.dp,
                        border = BorderStroke(2.dp, Color(0xFF00A3FF)), modifier = Modifier.size(88.dp)
                    ) {
                        Image(painter = painterResource(id = R.drawable.ic_pos_asset), contentDescription = "RM.POS Logo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "RM.POS", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Box(modifier = Modifier.width(32.dp).height(1.5.dp).background(Color(0xFF38BDF8).copy(alpha = 0.6f)))
                    Text(
                        text = Bilingual.tr(lang, "  تسيير التجارة بكل سهولة  ", "  Gestion commerciale simplifiée  ", "  Simplified business management  "),
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFBAE6FD)
                    )
                    Box(modifier = Modifier.width(32.dp).height(1.5.dp).background(Color(0xFF38BDF8).copy(alpha = 0.6f)))
                }
                Spacer(modifier = Modifier.height(32.dp))

                if (displayError != null) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.22f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = displayError,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("login_error_banner"),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                RMPosPillInputField(
                    value = username,
                    onValueChange = {
                        username = it
                        localError = null
                    },
                    placeholder = Bilingual.tr(lang, "اسم المستخدم", "Nom d'utilisateur", "Username"),
                    leadingIcon = Icons.Outlined.Person,
                    testTag = "login_username_input"
                )

                Spacer(modifier = Modifier.height(14.dp))

                RMPosPillInputField(
                    value = pinPassword,
                    onValueChange = {
                        pinPassword = it
                        localError = null
                    },
                    placeholder = Bilingual.tr(lang, "كلمة سر", "Mot de passe", "Password"),
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    testTag = "login_password_input"
                )

                Spacer(modifier = Modifier.height(26.dp))

                Button(
                    onClick = {
                        val user = username.trim()
                        val pass = pinPassword.trim()

                        if (user.isEmpty()) {
                            localError = Bilingual.tr(lang, "يرجى إدخال اسم المستخدم", "Veuillez saisir le nom d'utilisateur", "Please enter username")
                            return@Button
                        }
                        if (pass.isEmpty()) {
                            localError = Bilingual.tr(lang, "يرجى إدخال كلمة السر", "Veuillez saisir le mot de passe", "Please enter password")
                            return@Button
                        }

                        textToolbar.hide()
                        focusManager.clearFocus(force = true)
                        authViewModel.login(user, pass) {
                            val welcomeMsg = Bilingual.tr(lang, "تم تسجيل الدخول بنجاح!", "Connexion réussie !", "Signed in successfully!")
                            Toast.makeText(context, welcomeMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF00E676), Color(0xFF00C853))
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .testTag("login_confirm_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF00C853),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = Bilingual.tr(lang, "تأكيد", "Confirmer", "Confirm"),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showRecoveryDialog = true }) {
                        Icon(
                            Icons.Filled.Key,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Bilingual.tr(lang, "استرجاع برمز الطوارئ", "Récupération", "Emergency Recovery"),
                            fontSize = 11.5.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (showRecoveryDialog) {
            EmergencyRecoveryDialog(
                viewModel = viewModel,
                authViewModel = authViewModel,
                lang = lang,
                onDismiss = { showRecoveryDialog = false }
            )
        }
    }
}

@Composable
private fun EmergencyRecoveryDialog(
    viewModel: PosViewModel,
    authViewModel: AuthSecurityViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dialogTextToolbar = com.example.ui.components.rememberSafeComposeTextToolbar()
    val focusManager = LocalFocusManager.current
    val deviceId = remember { viewModel.getDeviceId() }
    var recoveryTokenInput by remember { mutableStateOf("") }
    var recoveryNewPassInput by remember { mutableStateOf("") }
    var recoveryConfirmPassInput by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }

    val safeDismiss = {
        dialogTextToolbar.hide()
        focusManager.clearFocus(force = true)
        onDismiss()
    }

    CompositionLocalProvider(LocalTextToolbar provides dialogTextToolbar) {
    AlertDialog(
        onDismissRequest = safeDismiss,
        containerColor = Color(0xFF0F172A),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Bilingual.tr(lang, "استرجاع الحساب الآمن", "Récupération Sécurisée", "Secure Account Recovery"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                com.example.ui.components.DeviceIdBannerCard(
                    deviceId = deviceId,
                    lang = lang,
                    subtitle = Bilingual.tr(
                        lang,
                        "معرّف هذا الجهاز للتزويد برمز الاسترجاع الموقّع:",
                        "Identifiant appareil pour le code de récupération :",
                        "Device ID for signed recovery token:"
                    ),
                    showAppVersion = false
                )

                if (recoveryError != null) {
                    Text(
                        text = recoveryError ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = recoveryTokenInput,
                    onValueChange = { recoveryTokenInput = it; recoveryError = null },
                    label = { Text(Bilingual.tr(lang, "رمز الاسترجاع الموقّع", "Code de récupération", "Signed recovery code")) },
                    trailingIcon = {
                        IconButton(onClick = {
                            dialogTextToolbar.hide()
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val pasted = cb?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (pasted.isNotBlank()) {
                                recoveryTokenInput = com.example.security.LicenseSecurityManager.sanitizePastedToken(pasted)
                                recoveryError = null
                            }
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = Bilingual.tr(lang, "لصق", "Coller", "Paste"), tint = Color(0xFF38BDF8))
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = recoveryNewPassInput,
                    onValueChange = { recoveryNewPassInput = it; recoveryError = null },
                    label = {
                        Text(
                            Bilingual.tr(
                                lang,
                                "كلمة المرور الجديدة (${com.example.security.PasswordPolicy.hintLabel("AR")})",
                                "Nouveau mot de passe (${com.example.security.PasswordPolicy.hintLabel("FR")})",
                                "New password (${com.example.security.PasswordPolicy.hintLabel("EN")})"
                            )
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = recoveryConfirmPassInput,
                    onValueChange = { recoveryConfirmPassInput = it; recoveryError = null },
                    label = { Text(Bilingual.tr(lang, "تأكيد كلمة المرور الجديدة", "Confirmer mot de passe", "Confirm new password")) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val token = recoveryTokenInput.trim()
                    val newPass = recoveryNewPassInput.trim()
                    val conf = recoveryConfirmPassInput.trim()

                    if (token.isEmpty()) {
                        recoveryError = Bilingual.tr(lang, "يرجى إدخال رمز الاسترجاع", "Veuillez saisir le code", "Please enter the recovery code")
                        return@Button
                    }
                    val passValidation = PasswordPolicy.validate(newPass, conf, lang)
                    if (!passValidation.isValid) {
                        recoveryError = passValidation.errorMessage
                        return@Button
                    }

                    dialogTextToolbar.hide()
                    focusManager.clearFocus(force = true)
                    authViewModel.executeEmergencyRecovery(
                        recoveryToken = token,
                        newPassword = newPass,
                        onSuccess = {
                            safeDismiss()
                            Toast.makeText(
                                context,
                                Bilingual.tr(lang, "تم استرجاع الحساب وتحديث كلمة المرور والدخول بنجاح!", "Compte récupéré avec succès !", "Account recovered successfully!"),
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onError = { err ->
                            recoveryError = err
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0075FF))
            ) {
                Text(Bilingual.tr(lang, "تأكيد واسترجاع", "Valider", "Confirm & Recover"))
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) {
                Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"), color = Color(0xFF94A3B8))
            }
        }
    )
    }
}
