package com.example.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DatabaseInitResult
import com.example.data.ExternalSafBackupManager
import com.example.data.DatabaseBackupManager
import com.example.data.PosRepository
import com.example.data.RestoreResult
import com.example.data.UserEntity
import com.example.security.BackupEncryptionManager
import com.example.security.PasswordHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Consolidated Modular ViewModel for Encrypted Backups, Off-Device SAF Auto-Backups,
 * Restore (Staging DB + Atomic Replacement), Linked Account Metadata, and Full Data Wipe.
 */
class BackupSyncViewModel(
    private val repository: PosRepository,
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE),
    externalScope: CoroutineScope? = null,
    private val languageProvider: () -> String = { "AR" },
    private val onBeep: () -> Unit = {}
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private fun resolveDatabaseOrFailure(targetContext: Context = context): DatabaseInitResult =
        AppDatabase.initializeDatabase(targetContext)

    private fun localizedDbFailureMessage(failure: DatabaseInitResult.Failed): String =
        when (languageProvider().uppercase(Locale.US)) {
            "FR" -> failure.messageFr
            "EN" -> failure.messageEn
            else -> failure.messageAr
        }

    private suspend fun reportFailure(targetContext: Context, msg: String, onComplete: ((Boolean, String) -> Unit)?) {
        _backupStatus.value = "error"
        if (onComplete != null) onComplete.invoke(false, msg)
        else withContext(Dispatchers.Main) { Toast.makeText(targetContext, msg, Toast.LENGTH_LONG).show() }
    }

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    private val _linkedEmail = MutableStateFlow(prefs.getString("linked_email", "") ?: "")
    val linkedEmail: StateFlow<String> = _linkedEmail.asStateFlow()

    private val _linkedPhone = MutableStateFlow(prefs.getString("linked_phone", "") ?: "")
    val linkedPhone: StateFlow<String> = _linkedPhone.asStateFlow()

    private val _isCloudSyncEnabled = MutableStateFlow(prefs.getBoolean("cloud_sync_enabled", true))
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(prefs.getLong("last_backup_time", 0L))
    val lastBackupTime: StateFlow<Long> = _lastBackupTime.asStateFlow()

    private val _lastBackupFileName = MutableStateFlow(prefs.getString("last_backup_file_name", "") ?: "")
    val lastBackupFileName: StateFlow<String> = _lastBackupFileName.asStateFlow()

    private val _hasExternalBackupFolder = MutableStateFlow(ExternalSafBackupManager.isConfigured(prefs))
    val hasExternalBackupFolder: StateFlow<Boolean> = _hasExternalBackupFolder.asStateFlow()

    // Reflects real configured external SAF folder state (never hardcoded true)
    val driveConnected: StateFlow<Boolean> = _hasExternalBackupFolder.asStateFlow()

    private val _externalBackupFolderLabel = MutableStateFlow(ExternalSafBackupManager.getFolderLabel(prefs))
    val externalBackupFolderLabel: StateFlow<String> = _externalBackupFolderLabel.asStateFlow()

    private val _lastExternalAutoBackupTime = MutableStateFlow(ExternalSafBackupManager.getLastSyncTime(prefs))
    val lastExternalAutoBackupTime: StateFlow<Long> = _lastExternalAutoBackupTime.asStateFlow()

    private val _lastExternalAutoBackupError = MutableStateFlow(ExternalSafBackupManager.getLastSyncError(prefs))
    val lastExternalAutoBackupError: StateFlow<String?> = _lastExternalAutoBackupError.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(prefs.getBoolean("auto_backup_enabled", true))
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _autoBackupFrequency = MutableStateFlow(prefs.getString("auto_backup_freq", "DAILY") ?: "DAILY")
    val autoBackupFrequency: StateFlow<String> = _autoBackupFrequency.asStateFlow()

    private val _lastAutoBackupTime = MutableStateFlow(prefs.getLong("last_auto_backup_time", 0L))
    val lastAutoBackupTime: StateFlow<Long> = _lastAutoBackupTime.asStateFlow()

    private val _lastExternalExportTime = MutableStateFlow(prefs.getLong(KEY_LAST_EXTERNAL_EXPORT_TS, 0L))
    val lastExternalExportTime: StateFlow<Long> = _lastExternalExportTime.asStateFlow()

    private val _isExternalExportReminderDue = MutableStateFlow(computeIsExternalExportReminderDue())
    val isExternalExportReminderDue: StateFlow<Boolean> = _isExternalExportReminderDue.asStateFlow()

    private val _daysSinceLastExternalExport = MutableStateFlow(computeDaysSinceExternalExport())
    val daysSinceLastExternalExport: StateFlow<Int> = _daysSinceLastExternalExport.asStateFlow()

    private fun computeDaysSinceExternalExport(nowMs: Long = System.currentTimeMillis()): Int {
        val last = prefs.getLong(KEY_LAST_EXTERNAL_EXPORT_TS, 0L)
        if (last <= 0L) return -1
        return ((nowMs - last).coerceAtLeast(0L) / MILLIS_PER_DAY).toInt()
    }

    private fun computeIsExternalExportReminderDue(nowMs: Long = System.currentTimeMillis()): Boolean {
        val snoozeUntil = prefs.getLong(KEY_EXPORT_REMINDER_SNOOZE_UNTIL, 0L)
        if (snoozeUntil > nowMs) return false
        val last = prefs.getLong(KEY_LAST_EXTERNAL_EXPORT_TS, 0L)
        if (last <= 0L) return true
        val intervalDays = prefs.getInt(KEY_EXPORT_REMINDER_INTERVAL_DAYS, DEFAULT_EXPORT_REMINDER_DAYS)
            .coerceAtLeast(1)
        return (nowMs - last) >= intervalDays * MILLIS_PER_DAY
    }

    fun refreshExternalExportReminder(nowMs: Long = System.currentTimeMillis()) {
        _daysSinceLastExternalExport.value = computeDaysSinceExternalExport(nowMs)
        _isExternalExportReminderDue.value = computeIsExternalExportReminderDue(nowMs)
    }

    fun markExternalExportCompleted(timestampMs: Long = System.currentTimeMillis()) {
        _lastExternalExportTime.value = timestampMs
        prefs.edit()
            .putLong(KEY_LAST_EXTERNAL_EXPORT_TS, timestampMs)
            .remove(KEY_EXPORT_REMINDER_SNOOZE_UNTIL)
            .apply()
        refreshExternalExportReminder(timestampMs)
    }

    fun dismissExternalExportReminderForToday(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(KEY_EXPORT_REMINDER_SNOOZE_UNTIL, nowMs + MILLIS_PER_DAY)
            .apply()
        refreshExternalExportReminder(nowMs)
    }

    fun configureExternalBackupFolder(treeUri: Uri): Boolean {
        return try {
            val label = ExternalSafBackupManager.saveTreeUri(context, prefs, treeUri)
            _hasExternalBackupFolder.value = true
            _externalBackupFolderLabel.value = label
            _lastExternalAutoBackupError.value = null
            true
        } catch (e: Exception) {
            Log.e("BackupSyncViewModel", "Failed to save SAF backup folder URI", e)
            _lastExternalAutoBackupError.value = e.localizedMessage ?: "تعذر ربط المجلد الخارجي"
            false
        }
    }

    fun clearExternalBackupFolder() {
        ExternalSafBackupManager.clearTreeUri(context, prefs)
        _hasExternalBackupFolder.value = false
        _externalBackupFolderLabel.value = ""
        _lastExternalAutoBackupError.value = null
    }

    companion object {
        const val KEY_LAST_EXTERNAL_EXPORT_TS = "last_external_backup_export_ts"
        const val KEY_EXPORT_REMINDER_SNOOZE_UNTIL = "external_export_reminder_snooze_until"
        const val KEY_EXPORT_REMINDER_INTERVAL_DAYS = "external_export_reminder_interval_days"
        const val DEFAULT_EXPORT_REMINDER_DAYS = 7
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }

    fun updateLinkedAccount(email: String, phone: String) {
        _linkedEmail.value = email.trim()
        _linkedPhone.value = phone.trim()
        prefs.edit()
            .putString("linked_email", email.trim())
            .putString("linked_phone", phone.trim())
            .apply()
    }

    fun setLinkedAccountInfo(email: String, phone: String) = updateLinkedAccount(email, phone)

    fun toggleCloudSync(enabled: Boolean) {
        _isCloudSyncEnabled.value = enabled
        prefs.edit().putBoolean("cloud_sync_enabled", enabled).apply()
    }

    /**
     * Creates a passphrase-encrypted AES-256-GCM backup, saves locally, mirrors to the configured
     * external SAF folder (if configured), and hands the file to Android Share via [onReadyToShare].
     */
    fun performGoogleDriveBackup(
        encryptionPassword: String,
        onReadyToShare: ((File) -> Unit)? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        scope.launch {
            _backupStatus.value = "uploading"
            try {
                val policyResult = com.example.security.PasswordPolicy.validate(
                    password = encryptionPassword,
                    lang = languageProvider()
                )
                require(policyResult.isValid) {
                    policyResult.errorMessage ?: "Invalid backup password"
                }
                val readyDb = when (val dbInit = resolveDatabaseOrFailure(context)) {
                    is DatabaseInitResult.Ready -> dbInit.database
                    is DatabaseInitResult.Failed -> {
                        Log.w("BackupSyncViewModel", "Backup aborted: database unavailable (${dbInit.reasonCode})")
                        reportFailure(context, localizedDbFailureMessage(dbInit), onComplete)
                        return@launch
                    }
                }
                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val effectivePassword = encryptionPassword.trim()
                val fileName = "pos_backup_${dateStr}.enc.json"
                val rawJson = DatabaseBackupManager.createBackupJson(
                    db = readyDb,
                    linkedEmail = _linkedEmail.value,
                    linkedPhone = _linkedPhone.value
                )

                val encryptedContent = BackupEncryptionManager.encryptBackup(rawJson, effectivePassword)
                val (localFile, externalWrite) = withContext(Dispatchers.IO) {
                    val file = DatabaseBackupManager.saveBackupToLocalFile(
                        context = context,
                        backupJson = encryptedContent,
                        fileName = fileName
                    )
                    val extRes = ExternalSafBackupManager.writeEncryptedBackup(
                        context = context,
                        prefs = prefs,
                        fileName = fileName,
                        encryptedContent = encryptedContent,
                        prunePrefix = "pos_backup_"
                    )
                    file to extRes
                }

                val now = System.currentTimeMillis()
                _lastBackupTime.value = now
                _lastBackupFileName.value = fileName
                prefs.edit()
                    .putLong("last_backup_time", now)
                    .putString("last_backup_file_name", fileName)
                    .apply()

                if (externalWrite.configured) {
                    _lastExternalAutoBackupTime.value = ExternalSafBackupManager.getLastSyncTime(prefs)
                    _lastExternalAutoBackupError.value = ExternalSafBackupManager.getLastSyncError(prefs)
                    if (externalWrite.success) {
                        markExternalExportCompleted(now)
                    }
                }

                _backupStatus.value = "backup_success"
                onBeep()
                if (onReadyToShare != null) {
                    markExternalExportCompleted(now)
                    onReadyToShare.invoke(localFile)
                }
                val sizeKb = String.format(Locale.US, "%.1f KB", localFile.length() / 1024.0)
                val statusMsg = if (externalWrite.configured && externalWrite.success) {
                    "تم إنشاء النسخة المشفرة (AES-256-GCM • $sizeKb) وحفظ نسخة تلقائية في المجلد الخارجي (${_externalBackupFolderLabel.value})"
                } else {
                    "تم إنشاء النسخة الاحتياطية المشفرة بـ AES-256-GCM بنجاح ($sizeKb)"
                }
                onComplete?.invoke(true, statusMsg)
            } catch (e: Exception) {
                Log.e("BackupSyncViewModel", "Encrypted backup export failed", e)
                reportFailure(context, "حدث خطأ أثناء النسخ الاحتياطي: ${e.localizedMessage ?: e.javaClass.simpleName}", onComplete)
            } finally {
                delay(2500)
                _backupStatus.value = null
            }
        }
    }

    fun restoreFromJson(
        backupString: String,
        decryptionPassword: String? = null,
        currentManager: UserEntity? = null,
        onResult: (RestoreResult) -> Unit
    ) {
        scope.launch {
            _backupStatus.value = "restoring"

            if (currentManager == null || !currentManager.isManager) {
                _backupStatus.value = "error"
                onResult(RestoreResult(false, "عذراً، استعادة النسخة الاحتياطية تتطلب تسجيل دخول مدير مصادق عليه"))
                delay(2000)
                _backupStatus.value = null
                return@launch
            }

            if (decryptionPassword.isNullOrBlank() || !PasswordHasher.verifyAsync(decryptionPassword, currentManager.passwordHash)) {
                _backupStatus.value = "error"
                onResult(RestoreResult(false, "كلمة المرور غير صحيحة. يجب إعادة إدخال كلمة سر المدير للتأكيد وفك تشفير النسخة."))
                delay(2000)
                _backupStatus.value = null
                return@launch
            }

            if (!BackupEncryptionManager.isEncryptedBackup(backupString)) {
                _backupStatus.value = "error"
                onResult(RestoreResult(false, "تم رفض النسخة الاحتياطية: استعادة النسخ غير المشفرة (النص العادي) محظورة لأسباب أمنية."))
                delay(2000)
                _backupStatus.value = null
                return@launch
            }

            val decRes = BackupEncryptionManager.decryptBackup(backupString, decryptionPassword)
            val jsonToRestore = when (decRes) {
                is BackupEncryptionManager.DecryptionResult.Success -> decRes.jsonContent
                is BackupEncryptionManager.DecryptionResult.Error -> {
                    _backupStatus.value = "error"
                    onResult(RestoreResult(false, decRes.message))
                    delay(2000)
                    _backupStatus.value = null
                    return@launch
                }
            }

            val readyDb = when (val dbInit = resolveDatabaseOrFailure(context)) {
                is DatabaseInitResult.Ready -> dbInit.database
                is DatabaseInitResult.Failed -> {
                    Log.w("BackupSyncViewModel", "Restore aborted: database unavailable (${dbInit.reasonCode})")
                    _backupStatus.value = "error"
                    onResult(RestoreResult(false, localizedDbFailureMessage(dbInit)))
                    delay(2000)
                    _backupStatus.value = null
                    return@launch
                }
            }

            try {
                DatabaseBackupManager.createSafetyBackupBeforeRestore(context, readyDb)
            } catch (e: Exception) {
                Log.e("BackupSyncViewModel", "Failed pre-restore safety backup", e)
            }

            val result = DatabaseBackupManager.restoreDatabaseFromJson(readyDb, jsonToRestore, context)
            if (result.success) {
                _backupStatus.value = "restore_success"
                onBeep()

                result.linkedEmail?.takeIf { it.isNotBlank() }?.let { email ->
                    _linkedEmail.value = email
                    prefs.edit().putString("linked_email", email).apply()
                }
                result.linkedPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                    _linkedPhone.value = phone
                    prefs.edit().putString("linked_phone", phone).apply()
                }
                result.lastInvoiceSequence?.takeIf { it > 0L }?.let { seq ->
                    val currentSeq = prefs.getLong("last_generated_sale_invoice_num", 0L)
                    if (seq > currentSeq) {
                        prefs.edit().putLong("last_generated_sale_invoice_num", seq).commit()
                    }
                }
                val now = System.currentTimeMillis()
                _lastBackupTime.value = now
                prefs.edit().putLong("last_backup_time", now).apply()
            } else {
                _backupStatus.value = "error"
            }

            onResult(result)
            delay(2500)
            _backupStatus.value = null
        }
    }

    fun createLocalBackup(encryptionPassword: String) {
        performGoogleDriveBackup(encryptionPassword = encryptionPassword)
    }

    fun wipeAllEntitiesData(currentUser: UserEntity?, onComplete: () -> Unit = {}) {
        scope.launch {
            repository.wipeAllEntitiesData(currentUser)
            onComplete()
        }
    }

    fun updateAutoBackupConfig(enabled: Boolean, freq: String) {
        _autoBackupEnabled.value = enabled
        _autoBackupFrequency.value = freq
        prefs.edit()
            .putBoolean("auto_backup_enabled", enabled)
            .putString("auto_backup_freq", freq)
            .apply()
    }

    /**
     * Runs an automatic encrypted backup to local `filesDir/auto_backups` AND, whenever the user
     * has selected an external SAF folder (Google Drive SAF / SD card / USB OTG / Documents),
     * automatically writes a mirrored encrypted copy outside app storage.
     */
    fun performAutoBackup(targetContext: Context = context, onComplete: ((Boolean, String) -> Unit)? = null) {
        scope.launch {
            try {
                val readyDb = when (val dbInit = resolveDatabaseOrFailure(targetContext)) {
                    is DatabaseInitResult.Ready -> dbInit.database
                    is DatabaseInitResult.Failed -> {
                        Log.w("BackupSyncViewModel", "Auto-backup aborted: database unavailable (${dbInit.reasonCode})")
                        reportFailure(targetContext, localizedDbFailureMessage(dbInit), onComplete)
                        return@launch
                    }
                }

                val backupDir = File(targetContext.filesDir, "auto_backups")
                if (!backupDir.exists()) backupDir.mkdirs()

                val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "auto_backup_${timeStr}.enc.json"
                val backupFile = File(backupDir, fileName)

                val json = DatabaseBackupManager.createBackupJson(
                    db = readyDb,
                    linkedEmail = _linkedEmail.value,
                    linkedPhone = _linkedPhone.value
                )
                val encryptedAutoBackup = BackupEncryptionManager.encryptSystemBackup(targetContext, json)
                val externalResult = withContext(Dispatchers.IO) {
                    backupFile.writeText(encryptedAutoBackup, Charsets.UTF_8)

                    val files = backupDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                    if (files.size > 7) {
                        files.drop(7).forEach { it.delete() }
                    }

                    ExternalSafBackupManager.writeEncryptedBackup(
                        context = targetContext,
                        prefs = prefs,
                        fileName = fileName,
                        encryptedContent = encryptedAutoBackup,
                        prunePrefix = "auto_backup_"
                    )
                }

                val now = System.currentTimeMillis()
                _lastAutoBackupTime.value = now
                _lastBackupTime.value = now
                prefs.edit()
                    .putLong("last_auto_backup_time", now)
                    .putLong("last_backup_time", now)
                    .apply()

                if (externalResult.configured) {
                    _lastExternalAutoBackupTime.value = ExternalSafBackupManager.getLastSyncTime(prefs)
                    _lastExternalAutoBackupError.value = ExternalSafBackupManager.getLastSyncError(prefs)
                    if (externalResult.success) {
                        markExternalExportCompleted(now)
                    }
                }
                refreshExternalExportReminder(now)

                val completionMsg = when {
                    externalResult.configured && externalResult.success ->
                        "تم إنشاء النسخة التلقائية المشفرة محلياً وفي المجلد الخارجي (${_externalBackupFolderLabel.value}) بنجاح!"
                    externalResult.configured && !externalResult.success ->
                        "تم حفظ النسخة التلقائية محلياً، لكن تعذّر النسخ للمجلد الخارجي: ${externalResult.errorMessage}"
                    else ->
                        "تم إنشاء النسخة التلقائية محلياً فقط (اختر مجلداً خارجياً للنسخ التلقائي خارج الجهاز)."
                }
                onComplete?.invoke(true, completionMsg)
            } catch (e: Exception) {
                Log.e("BackupSyncViewModel", "Encrypted auto-backup failed", e)
                reportFailure(targetContext, "فشل النسخ الاحتياطي التلقائي: ${e.localizedMessage ?: e.javaClass.simpleName}", onComplete)
            }
        }
    }
}
