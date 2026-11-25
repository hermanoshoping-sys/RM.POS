package com.example.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.PhonePrinterSettingsCard
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 3. Printer Settings Subscreen.
 */
@Composable
fun PrinterSettingsSubScreen(
    viewModel: PosViewModel,
    lang: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070D1E))
    ) {
        SettingsSubHeader(
            title = Bilingual.tr(lang, "إعدادات الطباعة", "Configuration Imprimante", "Printer Settings"),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            PhonePrinterSettingsCard(
                viewModel = viewModel,
                lang = lang
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 4. Backup & Data Migration Subscreen.
 */
@Composable
fun BackupDataSettingsScreen(
    viewModel: PosViewModel,
    lang: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val linkedEmail by viewModel.linkedEmail.collectAsStateWithLifecycle()
    val linkedPhone by viewModel.linkedPhone.collectAsStateWithLifecycle()
    val isCloudSyncEnabled by viewModel.isCloudSyncEnabled.collectAsStateWithLifecycle()
    val lastBackupTime by viewModel.lastBackupTime.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val autoBackupFreq by viewModel.autoBackupFrequency.collectAsStateWithLifecycle()
    val lastAutoBackupTime by viewModel.lastAutoBackupTime.collectAsStateWithLifecycle()
    val hasExternalBackupFolder by viewModel.hasExternalBackupFolder.collectAsStateWithLifecycle()
    val externalBackupFolderLabel by viewModel.externalBackupFolderLabel.collectAsStateWithLifecycle()
    val lastExternalAutoBackupTime by viewModel.lastExternalAutoBackupTime.collectAsStateWithLifecycle()
    val lastExternalAutoBackupError by viewModel.lastExternalAutoBackupError.collectAsStateWithLifecycle()
    val isExternalExportReminderDue by viewModel.isExternalExportReminderDue.collectAsStateWithLifecycle()
    val daysSinceLastExternalExport by viewModel.daysSinceLastExternalExport.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshExternalExportReminder()
    }

    var emailInput by remember(linkedEmail) { mutableStateOf(linkedEmail ?: "") }
    var phoneInput by remember(linkedPhone) { mutableStateOf(linkedPhone) }
    var isRunningAutoBackup by remember { mutableStateOf(false) }

    var pendingBackupAuthAction by remember { mutableStateOf<String?>(null) }
    var backupAuthPasswordInput by remember { mutableStateOf("") }
    var backupAuthErrorMessage by remember { mutableStateOf<String?>(null) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val json = stream.bufferedReader().use { r -> r.readText() }
                    viewModel.restoreFromJson(json, decryptionPassword = backupAuthPasswordInput) { res ->
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    Bilingual.tr(lang, "فشل استيراد النسخة: ${e.localizedMessage}", "Échec d'importation : ${e.localizedMessage}", "Failed to import backup: ${e.localizedMessage}"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val externalFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        treeUri?.let {
            val saved = viewModel.configureExternalBackupFolder(it)
            Toast.makeText(
                context,
                if (saved) {
                    Bilingual.tr(lang, "تم ربط المجلد الخارجي للنسخ التلقائي بنجاح!", "Dossier externe configuré avec succès !", "External auto-backup folder linked!")
                } else {
                    Bilingual.tr(lang, "تعذر ربط المجلد الخارجي", "Impossible de lier le dossier externe", "Failed to link external folder")
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        SettingsSubHeader(
            title = Bilingual.tr(lang, "نسخ ونقل البيانات", "Sauvegarde & Transfert", "Backup & Transfer"),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isExternalExportReminderDue) {
                ExternalExportReminderCard(
                    lang = lang,
                    daysSinceLastExternalExport = daysSinceLastExternalExport,
                    onDismissReminder = { viewModel.dismissExternalExportReminderForToday() },
                    onExportNowClick = {
                        backupAuthPasswordInput = ""
                        backupAuthErrorMessage = null
                        pendingBackupAuthAction = "EXPORT"
                    }
                )
            }

            ExternalAutoBackupFolderCard(
                lang = lang,
                hasExternalFolder = hasExternalBackupFolder,
                folderLabel = externalBackupFolderLabel,
                lastExternalSyncTime = lastExternalAutoBackupTime,
                lastExternalError = lastExternalAutoBackupError,
                onSelectFolderClick = { externalFolderLauncher.launch(null) },
                onClearFolderClick = {
                    viewModel.clearExternalBackupFolder()
                    Toast.makeText(
                        context,
                        Bilingual.tr(lang, "تم فصل المجلد الخارجي؛ النسخ التلقائي محلي فقط الآن", "Dossier externe retiré", "External backup folder unlinked"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            BackupDataMigrationCard(
                lang = lang,
                onExportClick = {
                    backupAuthPasswordInput = ""
                    backupAuthErrorMessage = null
                    pendingBackupAuthAction = "EXPORT"
                },
                onImportClick = {
                    backupAuthPasswordInput = ""
                    backupAuthErrorMessage = null
                    pendingBackupAuthAction = "IMPORT"
                }
            )

            // 2. Link Email & Phone Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Bilingual.tr(lang, "ربط البرنامج برقم الهاتف والبريد الإلكتروني", "Liaison Compte & Numéro", "Link Phone & Backup Email"),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }
                        if (!linkedEmail.isNullOrBlank() || linkedPhone.isNotBlank()) {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(Bilingual.tr(lang, "مربوط ومزامن", "Lié & Synchronisé", "Linked & Synced"), color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text(Bilingual.tr(lang, "البريد الإلكتروني للتزامن والنسخ السحابي", "E-mail de synchronisation cloud", "Backup & Cloud Sync Email")) },
                        leadingIcon = { Icon(Icons.Filled.Mail, contentDescription = null, tint = Color(0xFFEF4444)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text(Bilingual.tr(lang, "رقم الهاتف المرتبط بالبرنامج", "Numéro de téléphone associé", "Linked Phone Number")) },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = Color(0xFF10B981)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            viewModel.updateLinkedAccount(emailInput.trim(), phoneInput.trim())
                            Toast.makeText(
                                context,
                                Bilingual.tr(lang, "تم ربط وتحديث البريد الإلكتروني ورقم الهاتف بنجاح!", "Compte et téléphone liés avec succès!", "Email and phone linked successfully!"),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Bilingual.tr(lang, "حفظ وتأكيد ربط الحساب", "Enregistrer & Lier", "Save & Link Account"), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. Auto Backup Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Sync, contentDescription = null, tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = Bilingual.tr(lang, "النسخ الاحتياطي التلقائي", "Sauvegarde Automatique", "Automatic Backup"),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (autoBackupEnabled) {
                                        Bilingual.tr(lang, "مفعل ويعمل في الخلفية", "Activé en arrière-plan", "Enabled & Running")
                                    } else {
                                        Bilingual.tr(lang, "معطل حالياً", "Désactivé", "Currently Disabled")
                                    },
                                    color = if (autoBackupEnabled) Color(0xFF10B981) else Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = { viewModel.updateAutoBackupConfig(it, autoBackupFreq) }
                        )
                    }

                    if (autoBackupEnabled) {
                        Text(Bilingual.tr(lang, "تكرار النسخ الاحتياطي التلقائي:", "Fréquence de sauvegarde :", "Auto Backup Frequency:"), color = Color.LightGray, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "DAILY" to Bilingual.tr(lang, "يومي", "Quotidien", "Daily"),
                                "WEEKLY" to Bilingual.tr(lang, "أسبوعي", "Hebdomadaire", "Weekly"),
                                "ON_TRANSACTION" to Bilingual.tr(lang, "عند كل حركة", "À chaque opération", "Every Action")
                            ).forEach { (freqKey, label) ->
                                OutlinedButton(
                                    onClick = { viewModel.updateAutoBackupConfig(true, freqKey) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (autoBackupFreq == freqKey) Color(0xFFF59E0B) else Color(0xFF334155)
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        color = if (autoBackupFreq == freqKey) Color(0xFFF59E0B) else Color.White
                                    )
                                }
                            }
                        }

                        if (lastAutoBackupTime > 0) {
                            val formattedAutoDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(lastAutoBackupTime))
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "آخر نسخ احتياطي تلقائي: $formattedAutoDate",
                                    "Dernière sauvegarde auto : $formattedAutoDate",
                                    "Last auto backup: $formattedAutoDate"
                                ),
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                isRunningAutoBackup = true
                                viewModel.performAutoBackup(context) { _, msg ->
                                    isRunningAutoBackup = false
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isRunningAutoBackup,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRunningAutoBackup) {
                                    Bilingual.tr(lang, "جاري النسخ...", "Sauvegarde en cours...", "Backing up...")
                                } else {
                                    Bilingual.tr(lang, "تشغيل النسخ الاحتياطي التلقائي الآن", "Lancer sauvegarde auto", "Run Auto Backup Now")
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. Backup Summary & Share Notes Card (transparent: Android Share + SAF, no direct Drive REST API)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Bilingual.tr(lang, "حالة النسخ والمشاركة السحابية", "Statut Sauvegarde & Partage", "Backup & Share Status"),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "المشاركة اليدوية تستخدم قائمة مشاركة أندرويد (Drive/WhatsApp/بريد)، والنسخ التلقائي الخارجي عبر SAF.",
                                    "Le partage manuel utilise Android Share ; la copie auto externe utilise SAF.",
                                    "Manual export uses Android Share; automatic off-device copies use Android SAF."
                                ),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isCloudSyncEnabled,
                            onCheckedChange = { viewModel.toggleCloudSync(it) }
                        )
                    }

                    if (!backupStatus.isNullOrBlank()) {
                        Text(
                            Bilingual.tr(lang, "الحالة: $backupStatus", "Statut : $backupStatus", "Status: $backupStatus"),
                            color = Color(0xFFF59E0B),
                            fontSize = 12.sp
                        )
                    }
                    if (lastBackupTime > 0) {
                        val formattedBackupDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(lastBackupTime))
                        Text(
                            Bilingual.tr(
                                lang,
                                "آخر نسخة احتياطية: $formattedBackupDate",
                                "Dernière sauvegarde : $formattedBackupDate",
                                "Last backup: $formattedBackupDate"
                            ),
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 5. Data Wipe Card
            BackupDataWipeCard(
                lang = lang,
                onWipeClick = { showWipeConfirmDialog = true }
            )

            if (showWipeConfirmDialog) {
                BackupWipeConfirmDialog(
                    context = context,
                    lang = lang,
                    viewModel = viewModel,
                    onDismiss = { showWipeConfirmDialog = false }
                )
            }
        }

        pendingBackupAuthAction?.let { action ->
            BackupPasswordAuthDialog(
                action = action,
                lang = lang,
                context = context,
                viewModel = viewModel,
                passwordInput = backupAuthPasswordInput,
                onPasswordChange = { backupAuthPasswordInput = it },
                errorMessage = backupAuthErrorMessage,
                onErrorChange = { backupAuthErrorMessage = it },
                onDismiss = { pendingBackupAuthAction = null },
                onConfirmedImport = { restoreFileLauncher.launch("*/*") }
            )
        }
    }
}
