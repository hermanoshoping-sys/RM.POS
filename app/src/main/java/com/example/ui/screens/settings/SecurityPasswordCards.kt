package com.example.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.PasswordPolicy
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

@Composable
internal fun ProgramLoginPasswordCard(
    context: Context,
    viewModel: PosViewModel,
    lang: String,
    requirePasswordOnLogin: Boolean
) {
    var loginNewPassInput by remember { mutableStateOf("") }
    var loginConfirmPassInput by remember { mutableStateOf("") }
    var showLoginNewPass by remember { mutableStateOf(false) }
    var showLoginConfirmPass by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Login, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Bilingual.tr(
                        lang,
                        "كلمة سر الدخول إلى البرنامج",
                        "Mot de passe d'entrée au programme",
                        "Program Login Password"
                    ),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }

            SettingsSwitchRow(
                title = Bilingual.tr(
                    lang,
                    "طلب كلمة سر عند الدخول إلى البرنامج",
                    "Demander un mot de passe à l'ouverture",
                    "Require password on program launch"
                ),
                checked = requirePasswordOnLogin,
                onCheckedChange = { enabled ->
                    viewModel.updateLoginPasswordSecurity(enabled)
                    Toast.makeText(
                        context,
                        Bilingual.tr(
                            lang,
                            if (enabled) "تم تفعيل كلمة سر الدخول إلى البرنامج" else "تم إلغاء كلمة سر الدخول (دخول مباشر)",
                            if (enabled) "Mot de passe d'entrée activé" else "Connexion directe activée",
                            if (enabled) "Program login password enabled" else "Direct startup login enabled"
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            OutlinedTextField(
                value = loginNewPassInput,
                onValueChange = { loginNewPassInput = it },
                label = {
                    Text(
                        Bilingual.tr(
                            lang,
                            "كلمة سر الدخول الجديدة (${PasswordPolicy.hintLabel(lang)})",
                            "Nouveau mot de passe (${PasswordPolicy.hintLabel(lang)})",
                            "New login password (${PasswordPolicy.hintLabel(lang)})"
                        )
                    )
                },
                visualTransformation = if (showLoginNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showLoginNewPass = !showLoginNewPass }) {
                        Icon(
                            if (showLoginNewPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle password",
                            tint = Color.Gray
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.fillMaxWidth().testTag("security_login_password_input")
            )

            OutlinedTextField(
                value = loginConfirmPassInput,
                onValueChange = { loginConfirmPassInput = it },
                label = {
                    Text(
                        Bilingual.tr(
                            lang,
                            "تأكيد كلمة سر الدخول",
                            "Confirmer mot de passe d'accès",
                            "Confirm program login password"
                        )
                    )
                },
                visualTransformation = if (showLoginConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showLoginConfirmPass = !showLoginConfirmPass }) {
                        Icon(
                            if (showLoginConfirmPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle confirm password",
                            tint = Color.Gray
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.fillMaxWidth().testTag("security_login_confirm_password_input")
            )

            Button(
                onClick = {
                    viewModel.setProgramLoginPassword(loginNewPassInput, loginConfirmPassInput) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            loginNewPassInput = ""
                            loginConfirmPassInput = ""
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("security_save_login_password_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    Bilingual.tr(
                        lang,
                        "حفظ وتفعيل كلمة سر الدخول",
                        "Enregistrer et activer",
                        "Save & Enable Login Password"
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun ChangeCurrentPasswordCard(
    context: Context,
    viewModel: PosViewModel,
    lang: String
) {
    var oldPassInput by remember { mutableStateOf("") }
    var newPassInput by remember { mutableStateOf("") }
    var confirmPassInput by remember { mutableStateOf("") }
    var showOldPass by remember { mutableStateOf(false) }
    var showNewPass by remember { mutableStateOf(false) }
    var showConfirmPass by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = Bilingual.tr(
                    lang,
                    "تغيير كلمة السر الحالية للمستخدم",
                    "Modifier le mot de passe actuel",
                    "Change Current Account Password"
                ),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp
            )

            OutlinedTextField(
                value = oldPassInput,
                onValueChange = { oldPassInput = it },
                label = { Text(Bilingual.tr(lang, "كلمة السر الحالية", "Mot de passe actuel", "Current password")) },
                visualTransformation = if (showOldPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showOldPass = !showOldPass }) {
                        Icon(
                            if (showOldPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle password",
                            tint = Color.Gray
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = newPassInput,
                onValueChange = { newPassInput = it },
                label = {
                    Text(
                        Bilingual.tr(
                            lang,
                            "كلمة السر الجديدة (${PasswordPolicy.hintLabel(lang)})",
                            "Nouveau mot de passe (${PasswordPolicy.hintLabel(lang)})",
                            "New password (${PasswordPolicy.hintLabel(lang)})"
                        )
                    )
                },
                visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showNewPass = !showNewPass }) {
                        Icon(
                            if (showNewPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle password",
                            tint = Color.Gray
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmPassInput,
                onValueChange = { confirmPassInput = it },
                label = { Text(Bilingual.tr(lang, "تأكيد كلمة السر الجديدة", "Confirmer mot de passe", "Confirm new password")) },
                visualTransformation = if (showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showConfirmPass = !showConfirmPass }) {
                        Icon(
                            if (showConfirmPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Toggle confirm password",
                            tint = Color.Gray
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.changePassword(oldPassInput, newPassInput, confirmPassInput) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) {
                            oldPassInput = ""
                            newPassInput = ""
                            confirmPassInput = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D6AE5)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    Bilingual.tr(lang, "تحديث كلمة السر", "Mettre à jour", "Update Password"),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
