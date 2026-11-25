package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.PasswordPolicy
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

@Composable
internal fun DeleteAndEditProtectionCard(
    viewModel: PosViewModel,
    lang: String
) {
    val context = LocalContext.current
    val requirePasswordOnDelete by viewModel.requirePasswordOnDelete.collectAsStateWithLifecycle()
    val requirePasswordOnEdit by viewModel.requirePasswordOnEdit.collectAsStateWithLifecycle()

    var deletePassInput by remember { mutableStateOf("") }
    var showDeletePass by remember { mutableStateOf(false) }

    var editPassInput by remember { mutableStateOf("") }
    var showEditPass by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LockPerson, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Bilingual.tr(lang, "حماية الحذف والتعديل بكلمة سر", "Protection Suppression & Modification", "Delete & Edit Password Protection"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }

            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "🔒 تتيح لك هذه الميزة منع أي مستخدم أو كاشير من حذف أو تعديل أي منتج، سعر، أو فاتورة إلا بإدخال كلمة السر المحددة.",
                        "Empêche toute suppression ou modification non autorisée sans mot de passe dédié.",
                        "Prevents any user or cashier from deleting or editing items, prices, or invoices without the dedicated password."
                    ),
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Bilingual.tr(lang, "طلب كلمة سر عند حذف أي شيء", "Mot de passe à la suppression", "Require password on delete"),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                        Switch(
                            checked = requirePasswordOnDelete,
                            onCheckedChange = { viewModel.updateDeleteSecurity(it, deletePassInput) }
                        )
                    }

                    if (requirePasswordOnDelete) {
                        OutlinedTextField(
                            value = deletePassInput,
                            onValueChange = { deletePassInput = it },
                            label = {
                                Text(
                                    Bilingual.tr(
                                        lang,
                                        "كلمة سر الحذف (${PasswordPolicy.hintLabel(lang)})",
                                        "Mot de passe de suppression (${PasswordPolicy.hintLabel(lang)})",
                                        "Delete Password (${PasswordPolicy.hintLabel(lang)})"
                                    )
                                )
                            },
                            visualTransformation = if (showDeletePass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showDeletePass = !showDeletePass }) {
                                    Icon(
                                        if (showDeletePass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = "Toggle password",
                                        tint = Color.Gray
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val validation = PasswordPolicy.validate(deletePassInput, lang = lang)
                                if (!validation.isValid) {
                                    Toast.makeText(context, validation.errorMessage ?: "Invalid password", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.updateDeleteSecurity(
                                    require = true,
                                    newPass = deletePassInput,
                                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() },
                                    onComplete = {
                                        deletePassInput = ""
                                        Toast.makeText(
                                            context,
                                            Bilingual.tr(lang, "تم حفظ وتفعيل كلمة سر الحذف بنجاح!", "Mot de passe de suppression enregistré!", "Delete password saved!"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Bilingual.tr(lang, "حفظ كلمة سر الحذف", "Enregistrer mot de passe", "Save Delete Password"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EditNote, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Bilingual.tr(lang, "طلب كلمة سر عند تعديل أي شيء", "Mot de passe à la modification", "Require password on edit"),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                        Switch(
                            checked = requirePasswordOnEdit,
                            onCheckedChange = { viewModel.updateEditSecurity(it, editPassInput) }
                        )
                    }

                    if (requirePasswordOnEdit) {
                        OutlinedTextField(
                            value = editPassInput,
                            onValueChange = { editPassInput = it },
                            label = {
                                Text(
                                    Bilingual.tr(
                                        lang,
                                        "كلمة سر التعديل (${PasswordPolicy.hintLabel(lang)})",
                                        "Mot de passe de modification (${PasswordPolicy.hintLabel(lang)})",
                                        "Edit Password (${PasswordPolicy.hintLabel(lang)})"
                                    )
                                )
                            },
                            visualTransformation = if (showEditPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showEditPass = !showEditPass }) {
                                    Icon(
                                        if (showEditPass) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = "Toggle password",
                                        tint = Color.Gray
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val validation = PasswordPolicy.validate(editPassInput, lang = lang)
                                if (!validation.isValid) {
                                    Toast.makeText(context, validation.errorMessage ?: "Invalid password", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.updateEditSecurity(
                                    require = true,
                                    newPass = editPassInput,
                                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() },
                                    onComplete = {
                                        editPassInput = ""
                                        Toast.makeText(
                                            context,
                                            Bilingual.tr(lang, "تم حفظ وتفعيل كلمة سر التعديل بنجاح!", "Mot de passe de modification enregistré!", "Edit password saved!"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Bilingual.tr(lang, "حفظ كلمة سر التعديل", "Enregistrer mot de passe", "Save Edit Password"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
