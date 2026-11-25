package com.example.ui.viewmodels

import android.content.SharedPreferences
import com.example.data.GlobalSettingsEntity

/**
 * SharedPreferences backup/restore mirror for GlobalSettingsEntity so store identity
 * survives database migrations or resets.
 */
internal object SettingsPrefsPersistence {
    fun saveToPrefs(prefs: SharedPreferences, settings: GlobalSettingsEntity) {
        prefs.edit().apply {
            putString("saved_shop_name", settings.shopName)
            putString("saved_shop_address", settings.shopAddress)
            putString("saved_shop_phone", settings.shopPhone)
            putString("saved_tax_number", settings.taxNumber)
            putString("saved_receipt_header", settings.receiptHeader)
            putString("saved_receipt_footer", settings.receiptFooter)
            putBoolean("saved_show_shop_name", settings.showShopName)
            putBoolean("saved_show_address", settings.showAddress)
            putBoolean("saved_show_phone", settings.showPhone)
            putBoolean("saved_show_tax_number", settings.showTaxNumber)
            putBoolean("saved_show_logo", settings.showLogo)
            putBoolean("saved_show_cashier_name", settings.showCashierName)
            putBoolean("saved_show_customer_info", settings.showCustomerInfo)
            putBoolean("saved_show_date_time", settings.showDateTime)
            putBoolean("saved_show_barcode", settings.showBarcode)
            putBoolean("saved_show_payment_method", settings.showPaymentMethod)
            putBoolean("saved_show_receipt_footer", settings.showReceiptFooter)
            putInt("saved_logo_width", settings.logoWidth)
            putInt("saved_logo_height", settings.logoHeight)
            putInt("saved_logo_quality", settings.logoQuality)
            putInt("saved_header_height", settings.headerHeight)
            putInt("saved_header_font_size", settings.headerFontSize)
            putString("saved_header_content_type", settings.headerContentType)
            putInt("saved_header_width", settings.headerWidth)
            putString("saved_footer_content_type", settings.footerContentType)
            putInt("saved_footer_width", settings.footerWidth)
            putInt("saved_footer_height", settings.footerHeight)
            putInt("saved_document_font_size", settings.documentFontSize)
            putBoolean("saved_allow_negative_stock", settings.allowNegativeStock)
            putString("saved_language", settings.language)
            putString("app_language", settings.language)
            putString("saved_currency", settings.currency)
            putString("app_currency", settings.currency)
            putBoolean("has_user_saved_settings", true)
            apply()
        }
    }

    fun loadFromPrefs(prefs: SharedPreferences): GlobalSettingsEntity? {
        if (!prefs.getBoolean("has_user_saved_settings", false)) return null
        val savedLang = prefs.getString("saved_language", prefs.getString("app_language", "AR")) ?: "AR"
        val savedCurrency = prefs.getString("saved_currency", prefs.getString("app_currency", "MAD"))?.trim().orEmpty().ifBlank { "MAD" }
        return GlobalSettingsEntity(
            shopName = prefs.getString("saved_shop_name", "") ?: "",
            shopAddress = prefs.getString("saved_shop_address", "") ?: "",
            shopPhone = prefs.getString("saved_shop_phone", "") ?: "",
            taxNumber = prefs.getString("saved_tax_number", "") ?: "",
            receiptHeader = prefs.getString("saved_receipt_header", "") ?: "",
            receiptFooter = prefs.getString("saved_receipt_footer", "") ?: "",
            language = savedLang,
            showShopName = prefs.getBoolean("saved_show_shop_name", true),
            showAddress = prefs.getBoolean("saved_show_address", true),
            showPhone = prefs.getBoolean("saved_show_phone", true),
            showTaxNumber = prefs.getBoolean("saved_show_tax_number", true),
            showLogo = prefs.getBoolean("saved_show_logo", true),
            showCashierName = prefs.getBoolean("saved_show_cashier_name", true),
            showCustomerInfo = prefs.getBoolean("saved_show_customer_info", true),
            showDateTime = prefs.getBoolean("saved_show_date_time", true),
            showBarcode = prefs.getBoolean("saved_show_barcode", true),
            showPaymentMethod = prefs.getBoolean("saved_show_payment_method", true),
            showReceiptFooter = prefs.getBoolean("saved_show_receipt_footer", true),
            logoWidth = prefs.getInt("saved_logo_width", 110),
            logoHeight = prefs.getInt("saved_logo_height", 70),
            logoQuality = prefs.getInt("saved_logo_quality", 90).coerceIn(40, 100),
            headerHeight = prefs.getInt("saved_header_height", 30),
            headerFontSize = prefs.getInt("saved_header_font_size", 13),
            headerContentType = prefs.getString("saved_header_content_type", "TEXT") ?: "TEXT",
            headerWidth = prefs.getInt("saved_header_width", 520),
            footerContentType = prefs.getString("saved_footer_content_type", "TEXT") ?: "TEXT",
            footerWidth = prefs.getInt("saved_footer_width", 520),
            footerHeight = prefs.getInt("saved_footer_height", 50),
            documentFontSize = prefs.getInt("saved_document_font_size", 12).coerceIn(8, 20),
            allowNegativeStock = prefs.getBoolean("saved_allow_negative_stock", false),
            currency = savedCurrency
        )
    }

    fun hasSignificantDiff(db: GlobalSettingsEntity, cached: GlobalSettingsEntity): Boolean =
        db.shopName != cached.shopName ||
            db.shopAddress != cached.shopAddress ||
            db.shopPhone != cached.shopPhone ||
            db.taxNumber != cached.taxNumber ||
            db.receiptHeader != cached.receiptHeader ||
            db.receiptFooter != cached.receiptFooter ||
            db.logoWidth != cached.logoWidth ||
            db.logoHeight != cached.logoHeight ||
            db.logoQuality != cached.logoQuality ||
            db.headerHeight != cached.headerHeight ||
            db.headerWidth != cached.headerWidth ||
            db.headerFontSize != cached.headerFontSize ||
            db.headerContentType != cached.headerContentType ||
            db.footerHeight != cached.footerHeight ||
            db.footerWidth != cached.footerWidth ||
            db.footerContentType != cached.footerContentType ||
            db.documentFontSize != cached.documentFontSize ||
            db.language != cached.language ||
            db.currency != cached.currency ||
            db.allowNegativeStock != cached.allowNegativeStock
}
