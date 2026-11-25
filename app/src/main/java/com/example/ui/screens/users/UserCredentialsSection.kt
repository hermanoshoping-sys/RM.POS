package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserRole
import com.example.security.PasswordPolicy

@Composable
internal fun UserCredentialsAndRoleSection(
    state: UserPermissionFormState,
    isEditing: Boolean,
    lang: String,
    passwordError: String? = null,
    onStateChange: (UserPermissionFormState) -> Unit
) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Badge, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                Text(
                    text = if (lang == "AR") "بيانات الحساب الأساسية" else "Informations du Compte",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF38BDF8)
                )
            }

            OutlinedTextField(
                value = state.username,
                onValueChange = { onStateChange(state.copy(username = it)) },
                label = { Text(if (lang == "AR") "اسم المستخدم" else "Nom d'utilisateur", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0284C7),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("user_form_username_input")
            )

            OutlinedTextField(
                value = state.pin,
                onValueChange = { onStateChange(state.copy(pin = it)) },
                label = {
                    Text(
                        if (!isEditing) {
                            if (lang == "AR") "كلمة المرور للدخول (${PasswordPolicy.hintLabel(lang)})"
                            else "Mot de passe (${PasswordPolicy.hintLabel(lang)})"
                        } else {
                            if (lang == "AR") "تغيير كلمة المرور (${PasswordPolicy.hintLabel(lang)} - اختياري)"
                            else "Nouveau mot de passe (optionnel)"
                        },
                        fontSize = 11.5.sp
                    )
                },
                isError = passwordError != null,
                supportingText = passwordError?.let { err ->
                    {
                        Text(
                            text = err,
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    IconButton(onClick = { onStateChange(state.copy(showPin = !state.showPin)) }) {
                        Icon(
                            imageVector = if (state.showPin) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle PIN",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                visualTransformation = if (state.showPin) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0284C7),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("user_form_pin_input")
            )

            Text(
                text = if (lang == "AR") "نماذج سريعة للإعداد المسبق:" else "Modèles rapides prédéfinis :",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SuggestionChip(
                    onClick = {
                        onStateChange(
                            state.copy(
                                role = UserRole.MANAGER,
                                canSell = true, canPurchase = true, canReports = true, canModifyPrice = true,
                                canDeleteInvoice = true, canEditInvoice = true, canManageInventory = true,
                                canManageClients = true, canEditCustomers = true, canManageSuppliers = true,
                                canManageExpenses = true, canManagePayments = true, enforceCustomerGeoLocation = false
                            )
                        )
                    },
                    label = { Text(if (lang == "AR") "👑 مدير كامل الصلاحيات" else "👑 Gérant Complet", fontSize = 10.5.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.15f), labelColor = Color(0xFFFBBF24)),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                )
                SuggestionChip(
                    onClick = {
                        onStateChange(
                            state.copy(
                                role = UserRole.CASHIER,
                                canSell = true, canPurchase = false, canReports = false, canModifyPrice = false,
                                canDeleteInvoice = false, canEditInvoice = false, canManageInventory = true,
                                canManageClients = true, canEditCustomers = true, canManageSuppliers = false,
                                canManageExpenses = false, canManagePayments = false, enforceCustomerGeoLocation = false
                            )
                        )
                    },
                    label = { Text(if (lang == "AR") "🛒 كاشير متجر عادي" else "🛒 Caissier Standard", fontSize = 10.5.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f), labelColor = Color(0xFF34D399)),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                )
                SuggestionChip(
                    onClick = {
                        onStateChange(
                            state.copy(
                                role = UserRole.CASHIER,
                                canSell = true, canPurchase = false, canReports = false, canModifyPrice = false,
                                canDeleteInvoice = false, canEditInvoice = false, canManageInventory = false,
                                canManageClients = true, canEditCustomers = false, canManageSuppliers = false,
                                canManageExpenses = false, canManagePayments = false, enforceCustomerGeoLocation = true
                            )
                        )
                    },
                    label = { Text(if (lang == "AR") "🛡️ بائع مقيد (حظر تعديل + GPS)" else "🛡️ Restreint (Bloqué + GPS)", fontSize = 10.5.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f), labelColor = Color(0xFFF87171)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Text(
                text = if (lang == "AR") "نوع الحساب والدور الوظيفي:" else "Rôle de l'utilisateur :",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoleSelectionCard(
                    title = if (lang == "AR") "مدير (Manager)" else "Gérant",
                    subtitle = if (lang == "AR") "كامل الصلاحيات" else "Accès complet",
                    icon = Icons.Filled.Shield,
                    selected = state.role == UserRole.MANAGER,
                    activeColor = Color(0xFFF59E0B),
                    activeTint = Color(0xFFFBBF24),
                    subTint = Color(0xFFFDE68A),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onStateChange(
                            state.copy(
                                role = UserRole.MANAGER,
                                canSell = true, canPurchase = true, canReports = true, canModifyPrice = true,
                                canDeleteInvoice = true, canEditInvoice = true, canManageInventory = true,
                                canManageClients = true, canEditCustomers = true, canManageSuppliers = true,
                                canManageExpenses = true, canManagePayments = true
                            )
                        )
                    }
                )
                RoleSelectionCard(
                    title = if (lang == "AR") "كاشير (Cashier)" else "Caissier",
                    subtitle = if (lang == "AR") "صلاحيات مخصصة" else "Accès contrôlé",
                    icon = Icons.Filled.PointOfSale,
                    selected = state.role == UserRole.CASHIER,
                    activeColor = Color(0xFF10B981),
                    activeTint = Color(0xFF34D399),
                    subTint = Color(0xFFA7F3D0),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onStateChange(
                            state.copy(
                                role = UserRole.CASHIER,
                                canSell = true, canPurchase = false, canReports = false, canModifyPrice = false,
                                canDeleteInvoice = false, canEditInvoice = false, canManageInventory = true,
                                canManageClients = true, canEditCustomers = false, canManageSuppliers = false,
                                canManageExpenses = false, canManagePayments = false
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun RoleSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    activeColor: Color,
    activeTint: Color,
    subTint: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        color = if (selected) activeColor.copy(alpha = 0.18f) else Color(0xFF0F172A),
        border = BorderStroke(1.5.dp, if (selected) activeColor else Color(0xFF334155)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) activeTint else Color.Gray, modifier = Modifier.size(24.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selected) Color.White else Color.Gray)
            Text(subtitle, fontSize = 9.5.sp, color = if (selected) subTint else Color.DarkGray)
        }
    }
}
