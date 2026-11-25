package com.example.domain

/**
 * Pure domain exception representing a persistence/database constraint violation
 * translated by the data/repository layer without leaking Android SQLite types into domain.
 */
class DomainDatabaseConstraintException(
    message: String = "Database constraint prevented saving changes",
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Unified error categories across Sales, Purchases, Orders, and Returns.
 */
enum class OperationErrorCode {
    PERMISSION_DENIED,
    INSUFFICIENT_STOCK,
    INVALID_AMOUNT,
    EMPTY_ITEMS,
    RECORD_NOT_FOUND,
    DUPLICATE_INVOICE_NUMBER,
    GEO_FENCE_BLOCKED,
    DATABASE_CONSTRAINT,
    UNKNOWN
}

/**
 * Identifies the exact business operation that succeeded or failed.
 */
enum class OperationType {
    SALE_CHECKOUT,
    SALE_CREATE,
    SALE_UPDATE,
    SALE_DELETE,
    SALE_VOID,
    SALE_SETTLE,
    SALE_MARK_DEBT,
    SALE_RETURN,
    SALE_DATE_UPDATE,
    SALE_NUMBER_UPDATE,
    SALE_CUSTOMER_UPDATE,
    PURCHASE_CREATE,
    PURCHASE_UPDATE,
    PURCHASE_DELETE,
    PURCHASE_VOID,
    PURCHASE_SETTLE,
    PURCHASE_MARK_DEBT,
    PURCHASE_RETURN,
    PURCHASE_DATE_UPDATE,
    PURCHASE_NUMBER_UPDATE,
    PURCHASE_SUPPLIER_UPDATE,
    ORDER_CREATE,
    ORDER_VOID
}

/**
 * Structured error payload emitted by domain/ViewModels for Sales & Purchases transactions.
 * Localization happens in the UI layer ([com.example.ui.components.PosErrorUiTranslator]).
 */
data class PosOperationError(
    val code: OperationErrorCode,
    val message: String,
    val operationType: OperationType,
    val timestamp: Long = System.currentTimeMillis(),
    val cause: Throwable? = null,
    val detail: String = ""
)

/**
 * Unified result wrapper for asynchronous Sales & Purchases transactions.
 */
sealed class PosOperationResult<out T> {
    data class Success<out T>(
        val data: T,
        val message: String = ""
    ) : PosOperationResult<T>()

    data class Failure(
        val error: PosOperationError
    ) : PosOperationResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun dataOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): PosOperationError? = (this as? Failure)?.error
}

/**
 * Pure domain error classifier mapping domain exceptions into [PosOperationError].
 * UI translation via `Bilingual` is handled exclusively in the UI layer.
 */
object PosErrorMapper {

    fun failure(
        code: OperationErrorCode,
        operationType: OperationType,
        customMessage: String? = null,
        cause: Throwable? = null
    ): PosOperationResult.Failure {
        val message = customMessage?.takeIf { it.isNotBlank() } ?: defaultMessage(code)
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
        operationType: OperationType
    ): PosOperationError {
        val raw = throwable.message.orEmpty()
        val lower = raw.lowercase()

        val code = when {
            throwable is SecurityException ||
                lower.contains("صلاحية") ||
                lower.contains("permission") ||
                lower.contains("accè") -> OperationErrorCode.PERMISSION_DENIED

            lower.contains("الموقع") ||
                lower.contains("geo") ||
                lower.contains("gps") ||
                lower.contains("نطاق") -> OperationErrorCode.GEO_FENCE_BLOCKED

            lower.contains("المتوفر") ||
                lower.contains("المخزون") ||
                lower.contains("stock") ||
                lower.contains("البيع بالسالب") -> OperationErrorCode.INSUFFICIENT_STOCK

            lower.contains("فارغ") ||
                lower.contains("vide") ||
                lower.contains("empty") ||
                lower.contains("no items") -> OperationErrorCode.EMPTY_ITEMS

            lower.contains("مستخدم بالفعل") ||
                lower.contains("déjà utilisé") ||
                lower.contains("duplicate") ||
                lower.contains("unique") -> OperationErrorCode.DUPLICATE_INVOICE_NUMBER

            throwable is NoSuchElementException ||
                lower.contains("غير موجود") ||
                lower.contains("not found") ||
                lower.contains("introuvable") -> OperationErrorCode.RECORD_NOT_FOUND

            throwable is IllegalArgumentException ||
                lower.contains("مبلغ") ||
                lower.contains("كمية") ||
                lower.contains("amount") ||
                lower.contains("quantity") -> OperationErrorCode.INVALID_AMOUNT

            throwable is DomainDatabaseConstraintException -> OperationErrorCode.DATABASE_CONSTRAINT

            throwable is IllegalStateException -> OperationErrorCode.INSUFFICIENT_STOCK

            else -> OperationErrorCode.UNKNOWN
        }

        val domainMessage = when {
            code == OperationErrorCode.INSUFFICIENT_STOCK && raw.isNotBlank() -> raw
            code == OperationErrorCode.PERMISSION_DENIED && raw.isNotBlank() -> raw
            code == OperationErrorCode.GEO_FENCE_BLOCKED && raw.isNotBlank() -> raw
            code == OperationErrorCode.INVALID_AMOUNT && raw.isNotBlank() -> raw
            raw.isNotBlank() && code == OperationErrorCode.UNKNOWN ->
                "${defaultMessage(code)}: $raw"
            else -> defaultMessage(code)
        }

        return PosOperationError(
            code = code,
            message = domainMessage,
            operationType = operationType,
            cause = throwable,
            detail = raw
        )
    }

    fun defaultMessage(code: OperationErrorCode): String {
        return when (code) {
            OperationErrorCode.PERMISSION_DENIED -> "Permission denied for this operation"
            OperationErrorCode.INSUFFICIENT_STOCK -> "Requested quantity exceeds available stock"
            OperationErrorCode.INVALID_AMOUNT -> "Invalid amount or quantity"
            OperationErrorCode.EMPTY_ITEMS -> "Cannot complete operation because the items list is empty"
            OperationErrorCode.RECORD_NOT_FOUND -> "Requested record was not found"
            OperationErrorCode.DUPLICATE_INVOICE_NUMBER -> "Invoice number is already in use"
            OperationErrorCode.GEO_FENCE_BLOCKED -> "Operation blocked outside customer geofence"
            OperationErrorCode.DATABASE_CONSTRAINT -> "Database constraint prevented saving changes"
            OperationErrorCode.UNKNOWN -> "An unexpected error occurred"
        }
    }
}
