package com.example.domain.stocktaking

/**
 * Scope type for a physical stocktaking (inventory) session.
 */
enum class InventoryType(val code: String) {
    GENERAL("GENERAL"),
    PARTIAL_CATEGORY("PARTIAL_CATEGORY"),
    PARTIAL_PRODUCTS("PARTIAL_PRODUCTS"),
    PARTIAL_SUPPLIER("PARTIAL_SUPPLIER");

    companion object {
        fun fromCode(code: String?): InventoryType =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: GENERAL
    }
}

/**
 * Lifecycle state of an inventory session.
 * COMPLETED and CANCELLED sessions are strictly immutable.
 */
enum class InventoryStatus(val code: String) {
    DRAFT("DRAFT"),
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED");

    val isEditable: Boolean
        get() = this == DRAFT || this == IN_PROGRESS

    companion object {
        fun fromCode(code: String?): InventoryStatus =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DRAFT
    }
}

/**
 * Per-product counting classification inside an inventory session.
 */
enum class InventoryItemStatus(val code: String) {
    UNCOUNTED("UNCOUNTED"),
    MATCHING("MATCHING"),
    SHORTAGE("SHORTAGE"),
    SURPLUS("SURPLUS");

    companion object {
        fun fromCode(code: String?): InventoryItemStatus =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: UNCOUNTED
    }
}

/**
 * Configurable behavior when scanning a barcode that was already counted in the current session.
 */
enum class DuplicateScanMode(val code: String) {
    INCREMENT("INCREMENT"),
    PROMPT("PROMPT"),
    REPLACE("REPLACE");

    companion object {
        fun fromCode(code: String?): DuplicateScanMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: INCREMENT
    }
}

/**
 * Supported inventory reports for PDF export, Excel export, and printing.
 */
enum class InventoryReportType(val code: String) {
    GENERAL("GENERAL"),
    SHORTAGE("SHORTAGE"),
    SURPLUS("SURPLUS"),
    MATCHING("MATCHING"),
    UNCOUNTED("UNCOUNTED"),
    VALUATION("VALUATION"),
    HISTORICAL_COMPARISON("HISTORICAL_COMPARISON");

    companion object {
        fun fromCode(code: String?): InventoryReportType =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: GENERAL
    }
}

/**
 * Filter choices for discrepancy review screens.
 */
enum class DiscrepancyFilter {
    ALL,
    DISCREPANCIES_ONLY,
    SHORTAGE,
    SURPLUS,
    MATCHING,
    UNCOUNTED
}

/**
 * Sort choices for discrepancy review screens.
 */
enum class DiscrepancySort {
    ABS_VALUE_DESC,
    DIFFERENCE_ASC,
    DIFFERENCE_DESC,
    NAME_ASC,
    CATEGORY_ASC
}

/**
 * Stock monitoring alert classification.
 */
enum class StockAlertType {
    OUT_OF_STOCK,
    LOW_STOCK,
    SLOW_MOVING
}
