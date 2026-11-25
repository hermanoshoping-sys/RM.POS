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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
@Composable
internal fun ExternalExportReminderCard(
    lang: String,
    daysSinceLastExternalExport: Int,
    onDismissReminder: () -> Unit,
    onExportNowClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("external_export_reminder_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFF59E0B))
                Text(
                    text = Bilingual.tr(
                        lang,
                        "تذكير دوري: احفظ نسخة احتياطية خارج الهاتف",
                        "Rappel périodique : exportez une sauvegarde externe",
                        "Periodic Reminder: Export an Off-Device Backup"
                    ),
                    color = Color(0xFFFDE68A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Text(
                text = if (daysSinceLastExternalExport < 0) {
                    Bilingual.tr(
                        lang,
                        "لم يتم تصدير أي نسخة مشفرة خارج الهاتف بعد. يُنصح بتصديرها أسبوعياً إلى Google Drive أو هاتف آخر.",
                        "Aucune sauvegarde chiffrée n'a encore été exportée hors de l'appareil. Exportez-la chaque semaine.",
                        "No encrypted backup has been exported off-device yet. Export weekly to Google Drive or external storage."
                    )
                } else {
                    Bilingual.tr(
                        lang,
                        "مرّ $daysSinceLastExternalExport يوماً على آخر تصدير خارجي للبيانات. قم بتصدير نسخة مشفرة لحماية بيانات المتجر.",
                        "Cela fait $daysSinceLastExternalExport jours depuis le dernier export externe. Exportez une copie chiffrée.",
                        "It has been $daysSinceLastExternalExport days since your last off-device export. Export an encrypted backup now."
                    )
                },
                color = Color(0xFFFEF3C7),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismissReminder,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Text(
                        Bilingual.tr(lang, "تذكير لاحقاً", "Plus tard", "Remind Later"),
                        color = Color(0xFFFDE68A),
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = onExportNowClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        Bilingual.tr(lang, "تصدير الآن", "Exporter", "Export Now"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun BackupDataMigrationCard(
    lang: String,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
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
                    Icon(Icons.Filled.PhonelinkSetup, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Bilingual.tr(lang, "نقل التطبيق والبيانات إلى هاتف آخر", "Transférer vers un autre téléphone", "Transfer App & Data to Another Phone"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
                Surface(
                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = Bilingual.tr(lang, "شامل بنقرة واحدة", "Complet 1-Clic", "Full 1-Click"),
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = Bilingual.tr(
                    lang,
                    "يتيح لك هذا الخيار أخذ نسخة ترحيل كاملة لجميع المنتجات، الفواتير، الديون، العملاء، الموردين، وإعدادات المتجر ونقلها فوراً لهاتفك الجديد.",
                    "Exportez toutes les données (produits, factures, crédits, clients) pour les transférer sur votre nouveau téléphone.",
                    "Export all data (products, invoices, credits, customers, suppliers, and settings) to transfer immediately to your new phone."
                ),
                color = Color.LightGray,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Button(
                onClick = onExportClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.SendToMobile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Bilingual.tr(lang, "تصدير حزمة النقل الشاملة للهاتف الجديد", "Exporter le package de transfert", "Export Full Transfer Package"), fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF38BDF8))
            ) {
                Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(Bilingual.tr(lang, "استيراد البيانات على الهاتف الجديد", "Restaurer sur le nouveau téléphone", "Restore Data on New Phone"), color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
            }

            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Bilingual.tr(lang, "📋 خطوات نقل البرنامج وبياناته لهاتف آخر:", "📋 Étapes de transfert vers un autre téléphone :", "📋 Steps to transfer app & data to another phone:"),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp
                    )
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "1️⃣ في هاتفك القديم: اضغط 'تصدير ومشاركة النسخة المشفّرة' (يفتح نافذة مشاركة أندرويد لـ Google Drive أو WhatsApp أو البريد، كما يحفظ نسخة تلقائياً في المجلد الخارجي إن كان محدداً).\n" +
                            "2️⃣ في هاتفك الجديد: قم بتثبيت التطبيق وافتحه.\n" +
                            "3️⃣ في هاتفك الجديد: اذهب للإعدادات > نسخ ونقل البيانات > اضغط 'استيراد البيانات على الهاتف الجديد' واختر الملف المشفّر.",
                            "1️⃣ Ancien tél : Cliquez sur 'Exporter' (ouvre le partage Android vers Drive/WhatsApp/e-mail et copie dans le dossier externe si configuré).\n" +
                            "2️⃣ Nouveau tél : Installez et lancez l'application.\n" +
                            "3️⃣ Nouveau tél : Paramètres > Données > 'Restaurer sur le nouveau téléphone'.",
                            "1️⃣ Old phone: Tap 'Export Full Transfer Package' (opens Android Share for Drive/WhatsApp/email and mirrors to your external folder if set).\n" +
                            "2️⃣ New phone: Install and open the app.\n" +
                            "3️⃣ New phone: Go to Settings > Backup & Transfer > 'Restore Data on New Phone' and select the encrypted file."
                        ),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExternalAutoBackupFolderCard(
    lang: String,
    hasExternalFolder: Boolean,
    folderLabel: String,
    lastExternalSyncTime: Long,
    lastExternalError: String?,
    onSelectFolderClick: () -> Unit,
    onClearFolderClick: () -> Unit
) {
    val borderColor = if (hasExternalFolder && lastExternalError.isNullOrBlank()) {
        Color(0xFF10B981)
    } else {
        Color(0xFFF59E0B)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("external_saf_backup_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Icon(
                        imageVector = if (hasExternalFolder) Icons.Filled.CloudDone else Icons.Filled.CreateNewFolder,
                        contentDescription = null,
                        tint = borderColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "النسخ التلقائي خارج الجهاز (SAF)",
                            "Sauvegarde auto hors-appareil (SAF)",
                            "Off-Device Auto-Backup Folder (SAF)"
                        ),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Surface(
                    color = borderColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (hasExternalFolder) {
                            Bilingual.tr(lang, "مربوط", "Configuré", "Active")
                        } else {
                            Bilingual.tr(lang, "محلي فقط", "Local seul", "Local Only")
                        },
                        color = borderColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("external_saf_status_badge")
                    )
                }
            }

            if (hasExternalFolder) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "المجلد الخارجي المعتمد: $folderLabel",
                        "Dossier externe configuré : $folderLabel",
                        "Configured external folder: $folderLabel"
                    ),
                    color = Color(0xFF34D399),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("external_saf_folder_label")
                )
                if (lastExternalSyncTime > 0L) {
                    val formatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                        .format(java.util.Date(lastExternalSyncTime))
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "آخر نسخ تلقائي خارج الجهاز: $formatted",
                            "Dernière copie hors-appareil : $formatted",
                            "Last off-device sync: $formatted"
                        ),
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp
                    )
                }
                if (!lastExternalError.isNullOrBlank()) {
                    Text(
                        text = Bilingual.tr(
                            lang,
                            "تنبيه آخر مزامنة: $lastExternalError",
                            "Avertissement synchronisation : $lastExternalError",
                            "Last sync warning: $lastExternalError"
                        ),
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp
                    )
                }
            } else {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "النسخ التلقائي الحالي يُحفظ داخل ذاكرة التطبيق فقط (مع تعطيل allowBackup) وسيُفقد عند إلغاء التثبيت أو ضياع الهاتف. اختر مجلداً خارجياً (Google Drive عبر SAF أو بطاقة SD أو USB) لحفظ النسخ التلقائية تلقائياً خارج التطبيق.",
                        "Les sauvegardes automatiques actuelles restent dans l'application (allowBackup=false) et sont perdues en cas de désinstallation. Choisissez un dossier externe (Drive SAF, carte SD, USB).",
                        "Auto-backups are currently local-only (allowBackup=false) and lost on uninstall/device loss. Pick an external folder (Drive SAF, SD card, USB) for automatic off-device copies."
                    ),
                    color = Color(0xFFFEF3C7),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.testTag("external_saf_local_warning")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSelectFolderClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("select_external_backup_folder_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasExternalFolder) Color(0xFF0284C7) else Color(0xFFD97706)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasExternalFolder) {
                            Bilingual.tr(lang, "تغيير المجلد الخارجي", "Changer le dossier", "Change Folder")
                        } else {
                            Bilingual.tr(lang, "اختيار مجلد خارجي (Drive / SD / USB)", "Choisir dossier externe", "Pick External Folder")
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                if (hasExternalFolder) {
                    OutlinedButton(
                        onClick = onClearFolderClick,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("clear_external_backup_folder_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Text(
                            text = Bilingual.tr(lang, "فصل", "Retirer", "Unlink"),
                            color = Color(0xFFF87171),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

