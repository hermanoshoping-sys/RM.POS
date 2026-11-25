package com.example.ui.components

import com.example.R
import com.example.domain.OperationErrorCode
import com.example.domain.OperationType
import com.example.domain.PosErrorMapper
import com.example.domain.PosOperationError
import com.example.domain.PosOperationResult
import com.example.ui.theme.Bilingual

/**
 * UI-layer translator that converts pure domain [PosOperationError] objects into
 * localized AR/FR/EN messages backed by Android `strings.xml` resources.
 */
object PosErrorUiTranslator {

    fun failure(
        code: OperationErrorCode,
        operationType: OperationType,
        lang: String,
        customMessage: String? = null,
        cause: Throwable? = null
    ): PosOperationResult.Failure {
        val message = customMessage?.takeIf { it.isNotBlank() } ?: defaultMessage(code, lang)
        return PosOperationResult.Failure(
            PosOperationError(
                code = code,
                message = message,
                operationType = operationType,
                cause = cause,
                detail = customMessage.orEmpty()
            )
        )
    }

    fun fromThrowable(
        throwable: Throwable,
        operationType: OperationType,
        lang: String = "AR"
    ): PosOperationError {
        val domainError = PosErrorMapper.fromThrowable(throwable, operationType)
        return localizeError(domainError, lang)
    }

    fun localizeError(error: PosOperationError, lang: String): PosOperationError {
        val raw = error.detail.ifBlank { error.cause?.message.orEmpty() }
        val localized = when {
            error.code == OperationErrorCode.INSUFFICIENT_STOCK && raw.isNotBlank() -> raw
            error.code == OperationErrorCode.PERMISSION_DENIED && raw.isNotBlank() -> raw
            error.code == OperationErrorCode.GEO_FENCE_BLOCKED && raw.isNotBlank() -> raw
            error.code == OperationErrorCode.INVALID_AMOUNT && raw.isNotBlank() -> raw
            raw.isNotBlank() && error.code == OperationErrorCode.UNKNOWN ->
                "${defaultMessage(error.code, lang)}: $raw"
            else -> defaultMessage(error.code, lang)
        }
        return error.copy(message = localized)
    }

    fun defaultMessage(code: OperationErrorCode, lang: String): String {
        return when (code) {
            OperationErrorCode.PERMISSION_DENIED -> Bilingual.getOrFallback(
                R.string.err_permission_denied,
                lang,
                "عذراً، ليس لديك الصلاحية اللازمة لتنفيذ هذه العملية",
                "Accès refusé : autorisation insuffisante pour cette opération",
                "Permission denied for this operation"
            )
            OperationErrorCode.INSUFFICIENT_STOCK -> Bilingual.getOrFallback(
                R.string.err_insufficient_stock,
                lang,
                "الكمية المطلوبة تتجاوز المخزون المتوفر",
                "La quantité demandée dépasse le stock disponible",
                "Requested quantity exceeds available stock"
            )
            OperationErrorCode.INVALID_AMOUNT -> Bilingual.getOrFallback(
                R.string.err_invalid_amount,
                lang,
                "القيمة أو الكمية المدخلة غير صالحة",
                "Montant ou quantité invalide",
                "Invalid amount or quantity"
            )
            OperationErrorCode.EMPTY_ITEMS -> Bilingual.getOrFallback(
                R.string.err_empty_items,
                lang,
                "لا يمكن إتمام العملية لأن القائمة فارغة",
                "Impossible de valider une liste vide",
                "Cannot complete operation because the items list is empty"
            )
            OperationErrorCode.RECORD_NOT_FOUND -> Bilingual.getOrFallback(
                R.string.err_record_not_found,
                lang,
                "السجل أو الفاتورة المطلوبة غير موجودة",
                "Enregistrement ou facture introuvable",
                "Requested record was not found"
            )
            OperationErrorCode.DUPLICATE_INVOICE_NUMBER -> Bilingual.getOrFallback(
                R.string.err_duplicate_invoice_number,
                lang,
                "رقم الفاتورة مستخدم بالفعل في فاتورة أخرى",
                "Ce numéro de facture est déjà utilisé",
                "Invoice number is already in use"
            )
            OperationErrorCode.GEO_FENCE_BLOCKED -> Bilingual.getOrFallback(
                R.string.err_geo_fence_blocked,
                lang,
                "لا يمكن إتمام الفاتورة خارج النطاق الجغرافي المحدد للزبون",
                "Opération bloquée hors zone géographique du client",
                "Operation blocked outside customer geofence"
            )
            OperationErrorCode.DATABASE_CONSTRAINT -> Bilingual.getOrFallback(
                R.string.err_database_constraint,
                lang,
                "تعارض في بيانات القاعدة منع حفظ العملية",
                "Contrainte de base de données détectée",
                "Database constraint prevented saving changes"
            )
            OperationErrorCode.UNKNOWN -> Bilingual.getOrFallback(
                R.string.err_unknown,
                lang,
                "حدث خطأ غير متوقع أثناء تنفيذ العملية",
                "Une erreur inattendue est survenue",
                "An unexpected error occurred"
            )
        }
    }
}
