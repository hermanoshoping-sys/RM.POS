package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.PasswordPolicy
import com.example.ui.theme.Bilingual

@Composable
internal fun LanguageChoicePill(
    label: String,
    code: String,
    selected: Boolean,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) accentColor else Color(0xFFF1F5F9),
        contentColor = if (selected) Color.White else Color(0xFF334155),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accentColor else Color(0xFFCBD5E1)
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ProgramLoginPasswordDialog(
    lang: String,
    onDismiss: () -> Unit,
    onConfirm: (newPass: String, confirmPass: String, onError: (String) -> Unit) -> Unit
) {
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showNewPass by remember { mutableStateOf(false) }
    var showConfirmPass by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF1D6AE5))
        },
        title = {
            Text(
                text = Bilingual.tr(
                    lang,
                    "كلمة سر الدخول إلى البرنامج",
                    "Mot de passe d'accès",
                    "Program Login Password"
                ),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "أدخل كلمة السر الجديدة لتأمين الدخول إلى البرنامج:",
                        "Saisissez le nouveau mot de passe d'accès :",
                        "Enter the new password to secure program startup:"
                    ),
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )

                errorMsg?.let { err ->
                    Text(
                        text = err,
                        color = Color(0xFFDC2626),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it; errorMsg = null },
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
                    singleLine = true,
                    visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showNewPass = !showNewPass }) {
                            Icon(
                                if (showNewPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_login_new_password")
                )

                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = { confirmPass = it; errorMsg = null },
                    label = { Text(Bilingual.tr(lang, "تأكيد كلمة السر", "Confirmer mot de passe", "Confirm password")) },
                    singleLine = true,
                    visualTransformation = if (showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPass = !showConfirmPass }) {
                            Icon(
                                if (showConfirmPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle confirm password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_login_confirm_password")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newPass, confirmPass) { errorMsg = it } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D6AE5)),
                modifier = Modifier.testTag("dialog_login_save_password")
            ) {
                Text(Bilingual.tr(lang, "حفظ وتفعيل", "Enregistrer", "Save & Enable"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"))
            }
        }
    )
}

@Composable
internal fun SettingsGridTile(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            color = Color(0xFF0F172A),
            fontWeight = FontWeight.Bold,
            fontSize = 17.5.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ZeroStockSaleSettingsCard(
    allowZeroStockSale: Boolean,
    lang: String,
    accentColor: Color,
    onToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (allowZeroStockSale) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_zero_stock_sale_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = if (allowZeroStockSale) Color(0xFF16A34A) else accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "البيع عند نفاد المخزون (المخزون = 0)",
                            "Vente avec stock à 0 (stock négatif)",
                            "Sell When Stock is 0"
                        ),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = Bilingual.tr(
                            lang,
                            if (allowZeroStockSale) {
                                "مفعّل · يمكن إتمام البيع حتى وإن كان المخزون 0 أو بالسالب"
                            } else {
                                "معطّل · يُمنع البيع عند نفاد المخزون أو عدم كفايته"
                            },
                            if (allowZeroStockSale) {
                                "Activé · Vente autorisée même si le stock est à 0"
                            } else {
                                "Désactivé · Vente bloquée si le stock est insuffisant"
                            },
                            if (allowZeroStockSale) {
                                "Enabled · Sales allowed even when stock is 0"
                            } else {
                                "Disabled · Sales blocked when stock is insufficient"
                            }
                        ),
                        fontSize = 12.sp,
                        color = if (allowZeroStockSale) Color(0xFF16A34A) else Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = allowZeroStockSale,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("settings_allow_zero_stock_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF16A34A)
                )
            )
        }
    }
}

