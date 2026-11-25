package com.example.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.GlobalSettingsEntity
import com.example.data.PosRepository
import com.example.data.UserEntity
import com.example.security.LicenseSecurityManager
import com.example.security.PasswordHasher
import com.example.security.PasswordPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modular delegate handling user password updates, username changes, initial admin setup,
 * backup password verification, and ECDSA emergency account recovery.
 */
internal class AccountCredentialsDelegate(
    private val repository: PosRepository,
    private val context: Context,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val languageProvider: () -> String,
    private val currentUserProvider: () -> UserEntity?,
    private val onCurrentUserUpdated: (UserEntity) -> Unit,
    private val onLoginError: (String?) -> Unit,
    private val onRequireLoginEnabled: () -> Unit,
    private val onBeep: () -> Unit,
    private val deviceIdProvider: () -> String
) {
    companion object {
        private const val TAG = "AccountCredentials"
    }

    fun setProgramLoginPassword(newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) {
        val trimmedNew = newPass.trim()
        val lang = languageProvider()
        val validation = PasswordPolicy.validate(newPass, confirmPass, lang)
        if (!validation.isValid) {
            onResult(false, validation.errorMessage ?: "Invalid password")
            return
        }

        scope.launch {
            try {
                val updatedUser = withContext(ioDispatcher) {
                    val allUsers = repository.getAllUsersList()
                    val target = currentUserProvider() ?: allUsers.firstOrNull { it.isManager } ?: allUsers.firstOrNull()
                        ?: return@withContext null
                    val newHash = PasswordHasher.hashAsync(trimmedNew, dispatcher = ioDispatcher)
                    val updated = target.copy(passwordHash = newHash)
                    repository.saveUser(updated, ioDispatcher)
                    updated
                }
                if (updatedUser == null) {
                    onResult(false, if (lang == "AR") "لم يتم العثور على حساب لتحديث كلمة السر" else "Aucun compte trouvé")
                    return@launch
                }
                onCurrentUserUpdated(updatedUser)
                onRequireLoginEnabled()
                onBeep()
                onResult(true, if (lang == "AR") "تم تعيين وتفعيل كلمة سر الدخول للبرنامج بنجاح!" else "Mot de passe d'accès enregistré !")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update program login password", e)
                onResult(false, e.localizedMessage ?: "Error")
            }
        }
    }

    fun updateCurrentUsername(newUsername: String, onResult: (Boolean, String) -> Unit) {
        val lang = languageProvider()
        val trimmed = newUsername.trim()
        if (trimmed.isBlank()) {
            onResult(
                false,
                when (lang) {
                    "AR" -> "يرجى إدخال اسم المستخدم الجديد"
                    "EN" -> "Please enter a new username"
                    else -> "Veuillez saisir un nouveau nom d'utilisateur"
                }
            )
            return
        }
        scope.launch {
            try {
                val outcome: Pair<UserEntity?, String?> = withContext(ioDispatcher) {
                    val allUsers = repository.getAllUsersList()
                    val target = currentUserProvider()?.let { cur ->
                        allUsers.find { it.id == cur.id }
                            ?: allUsers.find { it.username.equals(cur.username, ignoreCase = true) }
                    } ?: allUsers.firstOrNull { it.isManager } ?: allUsers.firstOrNull()
                        ?: return@withContext Pair(
                            null,
                            if (lang == "AR") "لم يتم العثور على حساب المستخدم" else "Aucun compte trouvé"
                        )

                    val duplicate = allUsers.any {
                        it.id != target.id && it.username.trim().equals(trimmed, ignoreCase = true)
                    }
                    if (duplicate) {
                        return@withContext Pair(
                            null,
                            when (lang) {
                                "AR" -> "اسم المستخدم هذا مستخدم بالفعل لحساب آخر"
                                "EN" -> "This username is already taken"
                                else -> "Ce nom d'utilisateur est déjà utilisé"
                            }
                        )
                    }

                    val oldUsername = target.username.trim()
                    val updated = target.copy(username = trimmed)
                    repository.saveUser(updated, ioDispatcher)
                    if (oldUsername != trimmed) {
                        repository.renameSellerReferences(oldUsername, trimmed)
                    }
                    Pair(updated, null)
                }

                val savedUser = outcome.first
                if (savedUser != null) {
                    onCurrentUserUpdated(savedUser)
                    onBeep()
                    onResult(
                        true,
                        when (lang) {
                            "AR" -> "تم تحديث اسم المستخدم والبائع إلى «${savedUser.username}» بنجاح"
                            "EN" -> "Username & seller name updated to '${savedUser.username}'"
                            else -> "Nom d'utilisateur et vendeur mis à jour : ${savedUser.username}"
                        }
                    )
                } else {
                    onResult(false, outcome.second ?: "Error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update username", e)
                onResult(false, e.localizedMessage ?: "Error")
            }
        }
    }

    fun executeEmergencyRecovery(
        recoveryToken: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            val validation = PasswordPolicy.validate(newPassword, lang = languageProvider())
            if (!validation.isValid) {
                onError(validation.errorMessage ?: "كلمة المرور غير صالحة")
                return@launch
            }
            val outcome: Pair<UserEntity?, String?> = withContext(ioDispatcher) {
                val deviceId = deviceIdProvider()
                val trimmedToken = recoveryToken.trim()
                val result = LicenseSecurityManager.verifyRecoveryToken(
                    tokenString = trimmedToken,
                    currentDeviceId = deviceId,
                    context = context
                )
                if (!result.isValid) {
                    return@withContext Pair(null, result.errorMessage ?: "رمز الاسترجاع غير صالح")
                }
                val targetUsername = result.targetUser?.trim().orEmpty()
                val allUsers = repository.getAllUsersList()
                val target = (if (targetUsername.isNotEmpty()) {
                    allUsers.find { it.username.equals(targetUsername, ignoreCase = true) }
                } else null)
                    ?: allUsers.firstOrNull { it.isManager }
                    ?: allUsers.firstOrNull()

                if (target != null) {
                    val hashed = PasswordHasher.hashAsync(newPassword, dispatcher = ioDispatcher)
                    val updated = target.copy(passwordHash = hashed)
                    repository.saveUser(updated, ioDispatcher)
                    LicenseSecurityManager.consumeRecoveryToken(context, trimmedToken)
                    PasswordHasher.resetFailedAttempts(context)

                    val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    val recoveryLog = "RECOVERY_EVENT: target=${target.username}, kid=${result.keyId}, date=$nowStr, device=$deviceId"
                    val existingLogs = prefs.getString("security_audit_logs", "") ?: ""
                    prefs.edit().putString("security_audit_logs", "$existingLogs\n$recoveryLog").apply()
                    Pair(updated, null)
                } else {
                    Pair(null, "لم يتم العثور على حساب مسؤول مؤهل للاسترجاع")
                }
            }
            val updatedUser = outcome.first
            if (updatedUser != null) {
                onCurrentUserUpdated(updatedUser)
                onLoginError(null)
                onSuccess()
            } else {
                onError(outcome.second ?: "رمز الاسترجاع غير صالح")
            }
        }
    }

    fun verifyBackupPassword(password: String, onResult: (Boolean) -> Unit) {
        scope.launch {
            val pass = password.trim()
            if (pass.isEmpty()) {
                onResult(false)
                return@launch
            }
            val current = currentUserProvider()
            val isVerified = withContext(ioDispatcher) {
                if (current != null && current.isManager &&
                    PasswordHasher.verifyAsync(pass, current.passwordHash, dispatcher = ioDispatcher)
                ) {
                    return@withContext true
                }
                val managers = repository.getAllUsersList().filter { it.isManager }
                managers.any { PasswordHasher.verifyAsync(pass, it.passwordHash, dispatcher = ioDispatcher) }
            }
            onResult(isVerified)
        }
    }

    fun setupInitialAdmin(
        username: String,
        pin: String,
        storeName: String = "",
        phone: String = "",
        address: String = "",
        currentSettings: GlobalSettingsEntity = GlobalSettingsEntity(),
        onSaveSettings: (GlobalSettingsEntity) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        scope.launch {
            try {
                val finalUsername = username.trim()
                if (finalUsername.isBlank()) {
                    onLoginError(
                        if (languageProvider() == "AR") "يرجى إدخال اسم المستخدم" else "Please enter a username"
                    )
                    return@launch
                }
                val passValidation = PasswordPolicy.validate(pin, lang = languageProvider())
                if (!passValidation.isValid) {
                    onLoginError(passValidation.errorMessage)
                    return@launch
                }
                val admin = withContext(ioDispatcher) {
                    val passwordHash = PasswordHasher.hashAsync(pin.trim(), dispatcher = ioDispatcher)
                    val entity = UserEntity(
                        username = finalUsername,
                        passwordHash = passwordHash,
                        role = "MANAGER",
                        canSell = true, canPurchase = true, canReports = true, canModifyPrice = true,
                        canDeleteInvoice = true, canManageUsers = true, canEditInvoice = true,
                        canManageInventory = true, canManageClients = true, canManageSuppliers = true,
                        canManageExpenses = true, canManagePayments = true, canManageSettings = true
                    )
                    val savedId = repository.saveUser(entity, ioDispatcher)
                    entity.copy(id = savedId)
                }
                if (storeName.isNotBlank() || phone.isNotBlank() || address.isNotBlank()) {
                    onSaveSettings(
                        currentSettings.copy(
                            shopName = if (storeName.isNotBlank()) storeName.trim() else currentSettings.shopName,
                            shopPhone = if (phone.isNotBlank()) phone.trim() else currentSettings.shopPhone,
                            shopAddress = if (address.isNotBlank()) address.trim() else currentSettings.shopAddress
                        )
                    )
                }
                onCurrentUserUpdated(admin)
                onLoginError(null)
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup initial user", e)
                onLoginError(e.message)
            }
        }
    }

    fun changePassword(currentPass: String, newPass: String, confirmPass: String, onResult: (Boolean, String) -> Unit) {
        val lang = languageProvider()
        val user = currentUserProvider()
        if (user == null) {
            onResult(false, if (lang == "AR") "لا يوجد مستخدم مسجل حالياً!" else "Aucun utilisateur connecté !")
            return
        }
        val validation = PasswordPolicy.validate(newPass, confirmPass, lang)
        if (!validation.isValid) {
            onResult(false, validation.errorMessage ?: "Invalid password")
            return
        }
        scope.launch {
            try {
                val updatedUser = withContext(ioDispatcher) {
                    if (!PasswordHasher.verifyAsync(currentPass, user.passwordHash, dispatcher = ioDispatcher)) {
                        return@withContext null
                    }
                    val newHash = PasswordHasher.hashAsync(newPass.trim(), dispatcher = ioDispatcher)
                    val updated = user.copy(passwordHash = newHash)
                    repository.saveUser(updated, ioDispatcher)
                    updated
                }
                if (updatedUser == null) {
                    onResult(false, "كلمة السر الحالية غير صحيحة!")
                    return@launch
                }
                onCurrentUserUpdated(updatedUser)
                onBeep()
                onResult(true, "تم تحديث وحفظ كلمة السر الجديدة بنجاح!")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to change password", e)
                onResult(false, "حدث خطأ: ${e.localizedMessage}")
            }
        }
    }
}
