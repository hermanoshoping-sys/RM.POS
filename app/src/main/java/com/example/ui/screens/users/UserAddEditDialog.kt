package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.UserEntity
import com.example.data.UserRole
import com.example.security.PasswordHasher
import com.example.security.PasswordPolicy
import com.example.ui.theme.POSPrimary
import kotlinx.coroutines.launch

@Composable
fun UserAddEditDialog(
    userToEdit: UserEntity?,
    onDismiss: () -> Unit,
    onSave: (UserEntity) -> Unit,
    lang: String
) {
    var formState by remember(userToEdit) { mutableStateOf(UserPermissionFormState.fromUser(userToEdit)) }
    var passwordError by remember(userToEdit) { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 10.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (formState.role == UserRole.MANAGER) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (userToEdit == null) Icons.Filled.PersonAdd else Icons.Filled.ManageAccounts,
                                contentDescription = null,
                                tint = if (formState.role == UserRole.MANAGER) Color(0xFFFBBF24) else Color(0xFF34D399),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (userToEdit == null) (if (lang == "AR") "إضافة مستخدم جديد" else "Nouvel Utilisateur")
                                else (if (lang == "AR") "تعديل المستخدم والصلاحيات" else "Modifier Utilisateur"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (lang == "AR") "تحديد حساب الدخول ومستويات الصلاحيات والقيود" else "Compte et autorisations d'accès",
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserCredentialsAndRoleSection(
                        state = formState,
                        isEditing = userToEdit != null,
                        lang = lang,
                        passwordError = passwordError,
                        onStateChange = {
                            if (it.pin != formState.pin) passwordError = null
                            formState = it
                        }
                    )
                    UserSecurityControlsSection(
                        state = formState,
                        lang = lang,
                        onStateChange = { formState = it }
                    )
                    UserScreenVisibilitySection(
                        state = formState,
                        lang = lang,
                        onStateChange = { formState = it }
                    )
                }

                Surface(
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text(if (lang == "AR") "إلغاء" else "Annuler", color = Color.LightGray)
                        }

                        val isFormValid = formState.username.isNotBlank() && (userToEdit != null || formState.pin.isNotBlank())
                        Button(
                            onClick = {
                                if (isSaving || !isFormValid) return@Button
                                val trimmedPin = formState.pin.trim()
                                if (userToEdit == null || trimmedPin.isNotEmpty()) {
                                    val validation = PasswordPolicy.validate(trimmedPin, lang = lang)
                                    if (!validation.isValid) {
                                        passwordError = validation.errorMessage
                                        return@Button
                                    }
                                }
                                passwordError = null
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        val finalHash = if (trimmedPin.isNotEmpty()) {
                                            PasswordHasher.hashAsync(trimmedPin)
                                        } else {
                                            userToEdit?.passwordHash ?: ""
                                        }
                                        if (formState.username.isNotBlank() && finalHash.isNotBlank()) {
                                            onSave(
                                                UserEntity(
                                                    id = userToEdit?.id ?: 0L,
                                                    username = formState.username.trim(),
                                                    passwordHash = finalHash,
                                                    role = formState.role,
                                                    canManageUsers = formState.role == UserRole.MANAGER,
                                                    canManageSettings = formState.role == UserRole.MANAGER,
                                                    canSell = formState.canSell,
                                                    canPurchase = formState.canPurchase,
                                                    canReports = formState.canReports,
                                                    canModifyPrice = formState.canModifyPrice,
                                                    canDeleteInvoice = formState.canDeleteInvoice,
                                                    canEditInvoice = formState.canEditInvoice,
                                                    canManageInventory = formState.canManageInventory,
                                                    canManageClients = formState.canManageClients,
                                                    canEditCustomers = formState.canEditCustomers,
                                                    canManageSuppliers = formState.canManageSuppliers,
                                                    canManageExpenses = formState.canManageExpenses,
                                                    canManagePayments = formState.canManagePayments,
                                                    enforceCustomerGeoLocation = formState.enforceCustomerGeoLocation
                                                )
                                            )
                                            onDismiss()
                                        }
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.5f).height(46.dp).testTag("user_form_save_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
                            shape = RoundedCornerShape(10.dp),
                            enabled = isFormValid && !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (lang == "AR") "حفظ وتفعيل الصلاحيات" else "Enregistrer", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}
