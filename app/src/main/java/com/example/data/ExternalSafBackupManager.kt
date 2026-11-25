package com.example.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.VisibleForTesting

data class ExternalBackupWriteResult(
    val configured: Boolean,
    val success: Boolean,
    val fileName: String? = null,
    val errorMessage: String? = null
)

/**
 * Manages real automatic off-device backups using Android's zero-permission
 * Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE` + persistable URI permissions).
 *
 * Users can pick any external or cloud-synced document provider folder (Google Drive SAF
 * provider, SD card, USB OTG drive, Nextcloud, or external Documents/Downloads) that survives
 * app uninstall and device data wipes — with zero broad storage permissions required.
 */
object ExternalSafBackupManager {
    private const val TAG = "ExternalSafBackup"
    const val KEY_TREE_URI = "ext_saf_backup_tree_uri"
    const val KEY_FOLDER_LABEL = "ext_saf_backup_folder_label"
    const val KEY_LAST_SYNC_TIME = "ext_saf_last_sync_time"
    const val KEY_LAST_SYNC_ERROR = "ext_saf_last_sync_error"
    const val DEFAULT_MAX_EXTERNAL_AUTO_BACKUPS = 10

    @Volatile
    @VisibleForTesting
    internal var testWriteOverride: ((treeUri: Uri, fileName: String, contentBytes: ByteArray) -> ExternalBackupWriteResult)? = null

    fun getSavedTreeUri(prefs: SharedPreferences): Uri? {
        val raw = prefs.getString(KEY_TREE_URI, null)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun getFolderLabel(prefs: SharedPreferences): String {
        return prefs.getString(KEY_FOLDER_LABEL, "")?.trim().orEmpty()
    }

    fun getLastSyncTime(prefs: SharedPreferences): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun getLastSyncError(prefs: SharedPreferences): String? {
        return prefs.getString(KEY_LAST_SYNC_ERROR, null)?.takeIf { it.isNotBlank() }
    }

    fun isConfigured(prefs: SharedPreferences): Boolean = getSavedTreeUri(prefs) != null

    /**
     * Persists the user-selected SAF tree URI and acquires persistable read/write permissions
     * so unattended automatic backups continue writing off-device across app restarts & reboots.
     */
    fun saveTreeUri(context: Context, prefs: SharedPreferences, treeUri: Uri): String {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }.onFailure { err ->
            Log.w(TAG, "takePersistableUriPermission warning for $treeUri: ${err.message}")
        }
        val label = resolveFriendlyFolderLabel(context, treeUri)
        prefs.edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .putString(KEY_FOLDER_LABEL, label)
            .remove(KEY_LAST_SYNC_ERROR)
            .apply()
        return label
    }

    fun clearTreeUri(context: Context, prefs: SharedPreferences) {
        val existing = getSavedTreeUri(prefs)
        if (existing != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.releasePersistableUriPermission(existing, flags)
            }
        }
        prefs.edit()
            .remove(KEY_TREE_URI)
            .remove(KEY_FOLDER_LABEL)
            .remove(KEY_LAST_SYNC_ERROR)
            .apply()
    }

    /**
     * Writes an encrypted backup payload into the persisted off-device SAF folder and prunes
     * older auto-backups in that folder beyond [maxFilesToKeep].
     */
    fun writeEncryptedBackup(
        context: Context,
        prefs: SharedPreferences,
        fileName: String,
        encryptedContent: String,
        prunePrefix: String? = "auto_backup_",
        maxFilesToKeep: Int = DEFAULT_MAX_EXTERNAL_AUTO_BACKUPS
    ): ExternalBackupWriteResult {
        val treeUri = getSavedTreeUri(prefs)
            ?: return ExternalBackupWriteResult(configured = false, success = false)

        val payloadBytes = encryptedContent.toByteArray(Charsets.UTF_8)

        testWriteOverride?.let { override ->
            val result = override(treeUri, fileName, payloadBytes)
            recordSyncOutcome(prefs, result)
            return result
        }

        return try {
            val resolver = context.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val createdUri = DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                "application/json",
                fileName
            ) ?: run {
                val fail = ExternalBackupWriteResult(
                    configured = true,
                    success = false,
                    fileName = fileName,
                    errorMessage = "تعذر إنشاء ملف النسخة في المجلد الخارجي المختار"
                )
                recordSyncOutcome(prefs, fail)
                return fail
            }

            resolver.openOutputStream(createdUri, "wt")?.use { out ->
                out.write(payloadBytes)
                out.flush()
            } ?: run {
                val fail = ExternalBackupWriteResult(
                    configured = true,
                    success = false,
                    fileName = fileName,
                    errorMessage = "تعذر فتح مسار الكتابة في المجلد الخارجي"
                )
                recordSyncOutcome(prefs, fail)
                return fail
            }

            if (!prunePrefix.isNullOrBlank() && maxFilesToKeep > 0) {
                pruneExternalDocuments(context, treeUri, prunePrefix, maxFilesToKeep)
            }

            val ok = ExternalBackupWriteResult(
                configured = true,
                success = true,
                fileName = fileName
            )
            recordSyncOutcome(prefs, ok)
            ok
        } catch (e: SecurityException) {
            Log.e(TAG, "External SAF folder permission revoked or unavailable", e)
            val fail = ExternalBackupWriteResult(
                configured = true,
                success = false,
                fileName = fileName,
                errorMessage = "انتهت صلاحية الوصول إلى المجلد الخارجي؛ يرجى إعادة اختياره"
            )
            recordSyncOutcome(prefs, fail)
            fail
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing encrypted backup to SAF folder", e)
            val fail = ExternalBackupWriteResult(
                configured = true,
                success = false,
                fileName = fileName,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName
            )
            recordSyncOutcome(prefs, fail)
            fail
        }
    }

    private fun recordSyncOutcome(prefs: SharedPreferences, result: ExternalBackupWriteResult) {
        if (!result.configured) return
        val editor = prefs.edit()
        if (result.success) {
            editor.putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
            editor.remove(KEY_LAST_SYNC_ERROR)
        } else {
            editor.putString(KEY_LAST_SYNC_ERROR, result.errorMessage ?: "فشل النسخ إلى المجلد الخارجي")
        }
        editor.apply()
    }

    private fun pruneExternalDocuments(
        context: Context,
        treeUri: Uri,
        prefix: String,
        maxFilesToKeep: Int
    ) {
        runCatching {
            val resolver = context.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            data class ChildDoc(val docId: String, val name: String, val modified: Long)
            val matching = mutableListOf<ChildDoc>()
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else null
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val mod = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                    if (!docId.isNullOrBlank() && !name.isNullOrBlank() &&
                        name.startsWith(prefix) && name.endsWith(".enc.json")
                    ) {
                        matching.add(ChildDoc(docId, name, mod))
                    }
                }
            }
            if (matching.size > maxFilesToKeep) {
                val sorted = matching.sortedWith(
                    compareByDescending<ChildDoc> { it.modified }.thenByDescending { it.name }
                )
                sorted.drop(maxFilesToKeep).forEach { stale ->
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, stale.docId)
                    DocumentsContract.deleteDocument(resolver, docUri)
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Non-fatal error pruning external SAF backups: ${err.message}")
        }
    }

    internal fun resolveFriendlyFolderLabel(context: Context, treeUri: Uri): String {
        val queriedName = runCatching {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
        if (!queriedName.isNullOrBlank()) return queriedName

        val decoded = Uri.decode(treeUri.lastPathSegment ?: treeUri.toString())
        val afterColon = decoded.substringAfterLast(':', decoded)
        return afterColon.substringAfterLast('/', afterColon).ifBlank { "External Folder" }
    }
}
