package com.example.ui.viewmodels

import android.content.SharedPreferences
import com.example.data.UserEntity
import com.example.security.PasswordHasher
import com.example.security.PasswordPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Focused delegate managing PIN/password gates for sensitive Delete and Edit actions,
 * including automatic migration of legacy plaintext SharedPreferences keys to PBKDF2 hashes.
 */
internal class ActionPasswordSecurityDelegate(
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val currentUserProvider: () -> UserEntity?,
    private val languageProvider: () -> String = { "AR" }
) {
    private val _requirePasswordOnDelete = MutableStateFlow(prefs.getBoolean("sec_require_pass_delete", false))
    val requirePasswordOnDelete: StateFlow<Boolean> = _requirePasswordOnDelete.asStateFlow()

    private val _requirePasswordOnEdit = MutableStateFlow(prefs.getBoolean("sec_require_pass_edit", false))
    val requirePasswordOnEdit: StateFlow<Boolean> = _requirePasswordOnEdit.asStateFlow()

    @Volatile
    private var deletePasswordHash: String = prefs.getString("sec_delete_pass_hash", null) ?: ""

    @Volatile
    private var editPasswordHash: String = prefs.getString("sec_edit_pass_hash", null) ?: ""

    val migrationJob: Job = scope.launch(ioDispatcher) {
        migrateLegacySecurityHash("sec_delete_pass", "sec_delete_pass_hash") { deletePasswordHash = it }
        migrateLegacySecurityHash("sec_edit_pass", "sec_edit_pass_hash") { editPasswordHash = it }
    }

    private suspend fun migrateLegacySecurityHash(
        legacyKey: String,
        hashKey: String,
        onMigrated: (String) -> Unit
    ) = withContext(ioDispatcher) {
        val hashed = prefs.getString(hashKey, null)
        val legacy = prefs.getString(legacyKey, null)
        when {
            hashed != null -> onMigrated(hashed)
            legacy != null && legacy.isNotBlank() && legacy != "1234" -> {
                val h = PasswordHasher.hashAsync(legacy, dispatcher = ioDispatcher)
                prefs.edit().putString(hashKey, h).remove(legacyKey).apply()
                onMigrated(h)
            }
            else -> {
                if (legacy != null) prefs.edit().remove(legacyKey).apply()
                onMigrated("")
            }
        }
    }

    fun updateDeleteSecurity(
        require: Boolean,
        newPass: String,
        onError: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        val trimmed = newPass.trim()
        if (trimmed.isNotEmpty()) {
            val validation = PasswordPolicy.validate(trimmed, lang = languageProvider())
            if (!validation.isValid) {
                onError(validation.errorMessage ?: "Invalid password")
                return
            }
        }
        _requirePasswordOnDelete.value = require
        scope.launch {
            withContext(ioDispatcher) {
                if (trimmed.isNotEmpty()) {
                    val h = PasswordHasher.hashAsync(trimmed, dispatcher = ioDispatcher)
                    deletePasswordHash = h
                    prefs.edit()
                        .putBoolean("sec_require_pass_delete", require)
                        .putString("sec_delete_pass_hash", h)
                        .remove("sec_delete_pass")
                        .apply()
                } else {
                    prefs.edit().putBoolean("sec_require_pass_delete", require).apply()
                }
            }
            onComplete()
        }
    }

    fun updateEditSecurity(
        require: Boolean,
        newPass: String,
        onError: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        val trimmed = newPass.trim()
        if (trimmed.isNotEmpty()) {
            val validation = PasswordPolicy.validate(trimmed, lang = languageProvider())
            if (!validation.isValid) {
                onError(validation.errorMessage ?: "Invalid password")
                return
            }
        }
        _requirePasswordOnEdit.value = require
        scope.launch {
            withContext(ioDispatcher) {
                if (trimmed.isNotEmpty()) {
                    val h = PasswordHasher.hashAsync(trimmed, dispatcher = ioDispatcher)
                    editPasswordHash = h
                    prefs.edit()
                        .putBoolean("sec_require_pass_edit", require)
                        .putString("sec_edit_pass_hash", h)
                        .remove("sec_edit_pass")
                        .apply()
                } else {
                    prefs.edit().putBoolean("sec_require_pass_edit", require).apply()
                }
            }
            onComplete()
        }
    }

    fun verifyDeletePassword(input: String, allUsers: List<UserEntity>, onResult: (Boolean) -> Unit) {
        if (!_requirePasswordOnDelete.value) {
            onResult(true)
            return
        }
        scope.launch {
            migrationJob.join()
            onResult(verifyActionPasswordAsync(input, deletePasswordHash, allUsers))
        }
    }

    fun verifyEditPassword(input: String, allUsers: List<UserEntity>, onResult: (Boolean) -> Unit) {
        if (!_requirePasswordOnEdit.value) {
            onResult(true)
            return
        }
        scope.launch {
            migrationJob.join()
            onResult(verifyActionPasswordAsync(input, editPasswordHash, allUsers))
        }
    }

    private suspend fun verifyActionPasswordAsync(
        input: String,
        customHash: String,
        allUsers: List<UserEntity>
    ): Boolean = withContext(ioDispatcher) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return@withContext false
        val user = currentUserProvider()
        if (user != null && PasswordHasher.verifyAsync(trimmed, user.passwordHash, dispatcher = ioDispatcher)) return@withContext true
        if (customHash.isNotBlank() && PasswordHasher.verifyAsync(trimmed, customHash, dispatcher = ioDispatcher)) return@withContext true
        val manager = allUsers.firstOrNull { it.isManager }
        if (manager != null && PasswordHasher.verifyAsync(trimmed, manager.passwordHash, dispatcher = ioDispatcher)) return@withContext true
        false
    }
}
