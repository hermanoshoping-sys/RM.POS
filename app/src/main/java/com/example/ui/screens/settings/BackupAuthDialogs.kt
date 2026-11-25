package com.example.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

@Composable
internal fun BackupDataWipeCard(
    lang: String,
    onWipeClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444))
                Text(
                    text = Bilingual.tr(lang, "مسح كافة البيانات (تصفير البرنامج)", "Effacer toutes les données", "Wipe All Data (Factory Reset)"),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444),
                    fontSize = 15.sp
                )
            }
            Text(
                text = Bilingual.tr(
                    lang,
                    "مسح كافة المنتجات، والزبائن، والموردين، وعائلات المنتجات المتواجدة في البرنامج للبدء بقاعدة بيانات نظيفة تماماً.",
                    "Supprime tous les produits, clients, fournisseurs et catégories pour démarrer avec une base vide.",
                    "Wipes all products, customers, suppliers, and categories to start with a clean database."
                ),
                color = Color.LightGray,
                fontSize = 12.sp
            )
            Button(
                onClick = onWipeClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Bilingual.tr(lang, "مسح المنتجات والزبائن والموردين الآن", "Effacer les données maintenant", "Erase Data Now"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun BackupWipeConfirmDialog(
    context: Context,
    lang: String,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    var wipePasswordInput by remember { mutableStateOf("") }
    var wipePasswordErrorMessage by remember { mutableStateOf<String?>(null) }
    var isWipingInProgress by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!isWipingInProgress) onDismiss() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Bilingual.tr(lang, "حماية وتأكيد المسح الشامل", "Sécurité Réinitialisation", "Confirm Data Wipe"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
                Text(
                    text = Bilingual.tr(
                        lang,
                        "تحذير: سيتم مسح كافة المنتجات، العائلات، الزبائن، الموردين، والمبيعات.\n\nلحماية بياناتك، يلزم إدخال كلمة مرور المدير وسيتم أخذ نسخة احتياطية أمان تلقائية وإلزامية قبل المسح.",
                        "Attention: suppression de tous les produits, clients, fournisseurs et ventes. Mot de passe requis et sauvegarde obligatoire avant exécution.",
                        "Warning: All products, categories, customers, suppliers, and sales will be deleted. Admin password and mandatory backup are required first."
                    ),
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                OutlinedTextField(
                    value = wipePasswordInput,
                    onValueChange = {
                        wipePasswordInput = it
                        wipePasswordErrorMessage = null
                    },
                    label = { Text(Bilingual.tr(lang, "كلمة مرور المدير", "Mot de passe Admin", "Admin Password")) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !isWipingInProgress,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                wipePasswordErrorMessage?.let { errMsg ->
                    Text(text = errMsg, color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (isWipingInProgress) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFFEF4444), strokeWidth = 2.dp)
                        Text(
                            text = Bilingual.tr(lang, "جاري أخذ النسخة الاحتياطية ومسح البيانات...", "Sauvegarde et suppression en cours...", "Creating backup & wiping data..."),
                            color = Color(0xFFF59E0B),
                            fontSize = 12.sp
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isWipingInProgress,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"))
                    }
                    Button(
                        onClick = {
                            if (wipePasswordInput.isBlank()) {
                                wipePasswordErrorMessage = Bilingual.tr(lang, "الرجاء إدخال كلمة مرور المدير", "Veuillez entrer le mot de passe", "Please enter admin password")
                                return@Button
                            }
                            isWipingInProgress = true
                            wipePasswordErrorMessage = null
                            viewModel.verifyBackupPassword(wipePasswordInput) { isValid ->
                                if (isValid) {
                                    viewModel.performAutoBackup(context) { success, backupMsg ->
                                        if (success) {
                                            viewModel.wipeAllEntitiesData {
                                                isWipingInProgress = false
                                                onDismiss()
                                                Toast.makeText(
                                                    context,
                                                    Bilingual.tr(lang, "تم أخذ نسخة احتياطية ومسح كافة البيانات بنجاح", "Sauvegarde créée et données effacées avec succès", "Backup created and data wiped successfully"),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } else {
                                            isWipingInProgress = false
                                            wipePasswordErrorMessage = Bilingual.tr(lang, "فشل أخذ النسخة الاحتياطية الإلزامية: $backupMsg", "Échec de sauvegarde : $backupMsg", "Mandatory backup failed: $backupMsg")
                                        }
                                    }
                                } else {
                                    isWipingInProgress = false
                                    wipePasswordErrorMessage = Bilingual.tr(lang, "كلمة مرور المدير غير صحيحة!", "Mot de passe administrateur incorrect !", "Incorrect admin password!")
                                }
                            }
                        },
                        enabled = !isWipingInProgress && wipePasswordInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(Bilingual.tr(lang, "تأكيد ومسح", "Confirmer", "Confirm & Wipe"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun BackupPasswordAuthDialog(
    action: String,
    lang: String,
    context: Context,
    viewModel: PosViewModel,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    errorMessage: String?,
    onErrorChange: (String?) -> Unit,
    onDismiss: () -> Unit,
    onConfirmedImport: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Bilingual.tr(lang, "أمان النسخة الاحتياطية", "Sécurité Sauvegarde", "Backup Security"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (action == "EXPORT") {
                        Bilingual.tr(lang, "أدخل كلمة السر لتأكيد تصدير النسخة الاحتياطية:", "Entrez le mot de passe pour exporter :", "Enter password to confirm export:")
                    } else {
                        Bilingual.tr(lang, "أدخل كلمة السر لتأكيد استيراد واسترجاع النسخة الاحتياطية:", "Entrez le mot de passe pour restaurer :", "Enter password to confirm restore:")
                    },
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        onPasswordChange(it)
                        onErrorChange(null)
                    },
                    label = {
                        Text(
                            if (action == "EXPORT") {
                                Bilingual.tr(
                                    lang,
                                    "كلمة السر (${com.example.security.PasswordPolicy.hintLabel("AR")})",
                                    "Mot de passe (${com.example.security.PasswordPolicy.hintLabel("FR")})",
                                    "Password (${com.example.security.PasswordPolicy.hintLabel("EN")})"
                                )
                            } else {
                                Bilingual.tr(lang, "كلمة السر", "Mot de passe", "Password")
                            }
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let { errMsg ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = errMsg, color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(Bilingual.tr(lang, "إلغاء", "Annuler", "Cancel"))
                    }
                    Button(
                        onClick = {
                            if (action == "EXPORT") {
                                val policyCheck = com.example.security.PasswordPolicy.validate(
                                    password = passwordInput,
                                    lang = lang
                                )
                                if (!policyCheck.isValid) {
                                    onErrorChange(policyCheck.errorMessage)
                                    return@Button
                                }
                            }
                            viewModel.verifyBackupPassword(passwordInput) { isValid ->
                                if (isValid) {
                                    onDismiss()
                                    if (action == "EXPORT") {
                                        viewModel.performGoogleDriveBackup(
                                            encryptionPassword = passwordInput,
                                            onReadyToShare = { file ->
                                                try {
                                                    val contentUri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        file
                                                    )
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        putExtra(Intent.EXTRA_STREAM, contentUri)
                                                        putExtra(Intent.EXTRA_SUBJECT, "RM.POS Full Migration Backup")
                                                        putExtra(
                                                            Intent.EXTRA_TEXT,
                                                            Bilingual.tr(lang, "حزمة نقل بيانات المتجر إلى الهاتف الجديد", "Package de transfert des données", "Store migration backup package")
                                                        )
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        type = "application/json"
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            sendIntent,
                                                            Bilingual.tr(lang, "إرسال حزمة النقل إلى هاتفك الجديد", "Envoyer le package de transfert", "Send migration package")
                                                        )
                                                    )
                                                } catch (e: Exception) {
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                                                        type = "application/json"
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            sendIntent,
                                                            Bilingual.tr(lang, "مشاركة حزمة نقل البيانات", "Partager le package", "Share transfer package")
                                                        )
                                                    )
                                                }
                                            },
                                            onComplete = { _, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    } else if (action == "IMPORT") {
                                        onConfirmedImport()
                                    }
                                } else {
                                    onErrorChange(Bilingual.tr(lang, "كلمة السر غير صحيحة!", "Mot de passe incorrect !", "Incorrect password!"))
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(Bilingual.tr(lang, "تأكيد", "Confirmer", "Confirm"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
