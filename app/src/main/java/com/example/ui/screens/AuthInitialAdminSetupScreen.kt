package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.security.PasswordPolicy
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

@Composable
fun InitialAdminSetupScreen(viewModel: PosViewModel, lang: String) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var backupEmail by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var adminPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var isConfirmPinVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFF38BDF8),
        unfocusedBorderColor = Color(0xFF334155),
        focusedLabelColor = Color(0xFF38BDF8),
        unfocusedLabelColor = Color(0xFF94A3B8),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color(0xFF0A152B),
        unfocusedContainerColor = Color(0xFF0A152B)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF040A18), Color(0xFF071434), Color(0xFF040A1A))
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(26.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B36)),
            border = BorderStroke(1.5.dp, Color(0xFF0075FF).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    shadowElevation = 12.dp,
                    border = BorderStroke(2.dp, Color(0xFF00A3FF)),
                    modifier = Modifier.size(82.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_pos_asset),
                        contentDescription = "RM.POS Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                ) {
                    Text(
                        text = Bilingual.tr(lang, "تم تفعيل الرخصة بنجاح • إعداد المتجر", "Licence Validée • Configuration Magasin", "License Activated • Store Setup"),
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = Bilingual.tr(lang, "إدخال معلومات المتجر", "Informations du Magasin", "Store Information"),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = Bilingual.tr(
                        lang,
                        "أدخل بيانات متجرك وكلمة السر لتأمين النظام والنسخ الاحتياطي",
                        "Renseignez les informations du magasin et votre mot de passe",
                        "Enter your store details and password to secure the system and backups"
                    ),
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                errorMessage?.let { error ->
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = null },
                    label = {
                        Text(
                            Bilingual.tr(
                                lang,
                                "اسم المستخدم (يظهر كاسم البائع)",
                                "Nom d'utilisateur (Vendeur)",
                                "Username (Seller Name)"
                            )
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("admin_username_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it; errorMessage = null },
                    label = { Text(Bilingual.tr(lang, "اسم المتجر", "Nom du magasin", "Store Name")) },
                    leadingIcon = { Icon(Icons.Filled.Store, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("admin_store_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = backupEmail,
                    onValueChange = { backupEmail = it; errorMessage = null },
                    label = { Text(Bilingual.tr(lang, "البريد الإلكتروني للنسخ الاحتياطي", "E-mail de sauvegarde", "Backup Email")) },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("admin_backup_email_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMessage = null },
                    label = { Text(Bilingual.tr(lang, "رقم الهاتف", "Numéro de téléphone", "Phone Number")) },
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("admin_phone_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; errorMessage = null },
                    label = { Text(Bilingual.tr(lang, "العنوان", "Adresse", "Address")) },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("admin_address_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = adminPin,
                    onValueChange = { adminPin = it; errorMessage = null },
                    label = {
                        Text(
                            Bilingual.tr(
                                lang,
                                "كلمة السر (${PasswordPolicy.hintLabel(lang)})",
                                "Mot de passe (${PasswordPolicy.hintLabel(lang)})",
                                "Password (${PasswordPolicy.hintLabel(lang)})"
                            )
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                if (isPinVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle password",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("admin_password_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it; errorMessage = null },
                    label = { Text(Bilingual.tr(lang, "تأكيد كلمة سر", "Confirmer le mot de passe", "Confirm Password")) },
                    leadingIcon = { Icon(Icons.Filled.LockReset, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    trailingIcon = {
                        IconButton(onClick = { isConfirmPinVisible = !isConfirmPinVisible }) {
                            Icon(
                                if (isConfirmPinVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle confirm password",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    visualTransformation = if (isConfirmPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("admin_confirm_password_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val trimmedUsername = username.trim()
                        val trimmedStore = storeName.trim()
                        val trimmedEmail = backupEmail.trim()
                        val trimmedPhone = phone.trim()
                        val trimmedAddress = address.trim()
                        val pass = adminPin.trim()
                        val confPass = confirmPin.trim()

                        if (trimmedUsername.isEmpty()) {
                            errorMessage = Bilingual.tr(lang, "يرجى إدخال اسم المستخدم", "Veuillez saisir le nom d'utilisateur", "Please enter your username")
                            return@Button
                        }
                        if (trimmedStore.isEmpty()) {
                            errorMessage = Bilingual.tr(lang, "يرجى إدخال اسم المتجر", "Veuillez saisir le nom du magasin", "Please enter the store name")
                            return@Button
                        }
                        if (trimmedEmail.isEmpty()) {
                            errorMessage = Bilingual.tr(lang, "يرجى إدخال البريد الإلكتروني للنسخ الاحتياطي", "Veuillez saisir l'e-mail de sauvegarde", "Please enter the backup email")
                            return@Button
                        }
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                            errorMessage = Bilingual.tr(lang, "صيغة البريد الإلكتروني غير صحيحة", "Adresse e-mail invalide", "Invalid email format")
                            return@Button
                        }
                        if (trimmedPhone.isEmpty()) {
                            errorMessage = Bilingual.tr(lang, "يرجى إدخال رقم الهاتف", "Veuillez saisir le numéro de téléphone", "Please enter phone number")
                            return@Button
                        }
                        if (trimmedAddress.isEmpty()) {
                            errorMessage = Bilingual.tr(lang, "يرجى إدخال العنوان", "Veuillez saisir l'adresse", "Please enter address")
                            return@Button
                        }
                        val passValidation = PasswordPolicy.validate(pass, confPass, lang)
                        if (!passValidation.isValid) {
                            errorMessage = passValidation.errorMessage
                            return@Button
                        }

                        viewModel.setupInitialAdmin(
                            username = trimmedUsername,
                            pin = pass,
                            storeName = trimmedStore,
                            phone = trimmedPhone,
                            address = trimmedAddress,
                            backupEmail = trimmedEmail
                        ) {
                            Toast.makeText(
                                context,
                                Bilingual.tr(lang, "مرحباً بك! تم حفظ معلومات المتجر وفتح البرنامج بنجاح", "Bienvenue ! Magasin configuré avec succès", "Welcome! Store info saved successfully"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0075FF)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("create_admin_account_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Bilingual.tr(lang, "حفظ المعلومات ودخول البرنامج", "Enregistrer et démarrer", "Save Info & Enter Program"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
