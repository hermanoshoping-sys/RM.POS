package com.example.security

import java.util.Locale

/**
 * Unified Password Policy across all authentication, setup, recovery,
 * user management, delete/edit action protection, and encrypted backup creation flows.
 *
 * Rules:
 * - Minimum length: 8 characters (after trimming leading/trailing whitespace).
 * - Rejects common/dictionary weak passwords (e.g. 12345678, 00000000, admin123, qwerty123, azerty123).
 * - Rejects all-identical character passwords (e.g. 11111111, aaaaaaaa).
 * - Rejects pure ascending/descending character sequences (e.g. 23456789, 87654321, abcdefgh).
 *
 * Note: Enforced on password creation and change only; existing persisted hashes remain valid for sign-in/restore.
 */
object PasswordPolicy {

    const val MIN_LENGTH: Int = 8

    private val COMMON_WEAK_PASSWORDS: Set<String> = setOf(
        "1234", "12345", "123456", "1234567", "12345678", "123456789", "1234567890",
        "012345", "0123456", "01234567", "012345678",
        "654321", "987654", "87654321", "98765432", "987654321",
        "0000", "000000", "00000000", "1111", "111111", "11111111", "222222", "22222222",
        "112233", "11223344", "121212", "12121212", "123123", "12341234",
        "password", "password1", "password123", "pass123", "pass1234",
        "admin", "admin123", "admin1234", "manager", "manager1",
        "qwerty", "qwerty12", "qwerty123", "azerty", "azerty12", "azerty123",
        "abcdef", "abcdefgh", "abc123", "abc12345", "rmpos", "rmpos123", "rmpos1234"
    )

    enum class FailureReason {
        EMPTY,
        TOO_SHORT,
        TOO_WEAK,
        MISMATCH
    }

    data class ValidationResult(
        val isValid: Boolean,
        val failureReason: FailureReason? = null,
        val errorMessage: String? = null
    )

    fun isWeak(password: String): Boolean {
        val p = password.trim()
        if (p.isEmpty()) return true
        val lower = p.lowercase(Locale.ROOT)
        if (lower in COMMON_WEAK_PASSWORDS) return true
        if (p.all { it == p[0] }) return true
        if (p.length >= 4 && isSequential(lower)) return true
        return false
    }

    private fun isSequential(value: String): Boolean {
        var ascending = true
        var descending = true
        for (i in 1 until value.length) {
            val diff = value[i].code - value[i - 1].code
            if (diff != 1) ascending = false
            if (diff != -1) descending = false
            if (!ascending && !descending) return false
        }
        return ascending || descending
    }

    fun validate(
        password: String,
        confirmPassword: String? = null,
        lang: String = "AR"
    ): ValidationResult {
        val trimmed = password.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult(
                isValid = false,
                failureReason = FailureReason.EMPTY,
                errorMessage = when (lang) {
                    "AR" -> "يرجى إدخال كلمة المرور"
                    "EN" -> "Please enter a password"
                    else -> "Veuillez saisir un mot de passe"
                }
            )
        }
        if (trimmed.length < MIN_LENGTH) {
            return ValidationResult(
                isValid = false,
                failureReason = FailureReason.TOO_SHORT,
                errorMessage = when (lang) {
                    "AR" -> "كلمة المرور يجب ألا تقل عن $MIN_LENGTH أحرف أو أرقام"
                    "EN" -> "Password must be at least $MIN_LENGTH characters"
                    else -> "Le mot de passe doit contenir au moins $MIN_LENGTH caractères"
                }
            )
        }
        if (isWeak(trimmed)) {
            return ValidationResult(
                isValid = false,
                failureReason = FailureReason.TOO_WEAK,
                errorMessage = when (lang) {
                    "AR" -> "كلمة المرور ضعيفة جداً! تجنب التسلسلات والتكرارات مثل (12345678 / 00000000 / admin123)"
                    "EN" -> "Password is too weak! Avoid sequences or repeated characters (12345678 / 00000000)"
                    else -> "Mot de passe trop faible ! Évitez les suites simples (12345678 / 00000000)"
                }
            )
        }
        if (confirmPassword != null && trimmed != confirmPassword.trim()) {
            return ValidationResult(
                isValid = false,
                failureReason = FailureReason.MISMATCH,
                errorMessage = when (lang) {
                    "AR" -> "كلمتا المرور غير متطابقتين!"
                    "EN" -> "Passwords do not match!"
                    else -> "Les mots de passe ne correspondent pas !"
                }
            )
        }
        return ValidationResult(isValid = true)
    }

    fun hintLabel(lang: String): String = when (lang) {
        "AR" -> "$MIN_LENGTH أحرف أو أرقام فأكثر"
        "EN" -> "$MIN_LENGTH+ characters"
        else -> "$MIN_LENGTH+ caractères"
    }
}
