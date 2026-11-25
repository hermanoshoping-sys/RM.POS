package com.example.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Resource-backed multilingual helper for Arabic (`values-ar`), French (`values-fr`),
 * and English (`values`), always enforcing Western/Latin digits (`0-9`).
 */
object Bilingual {
    private const val TAG = "Bilingual"

    const val AR = "AR"
    const val FR = "FR"
    const val EN = "EN"

    @Volatile
    private var appContext: Context? = null
    private val localizedResourcesCache = ConcurrentHashMap<String, Resources>()

    fun init(context: Context) {
        val ctx = context.applicationContext ?: context
        if (appContext !== ctx) {
            appContext = ctx
            localizedResourcesCache.clear()
        }
    }

    fun localeFor(lang: String): Locale {
        return when (lang.uppercase(Locale.US)) {
            AR -> Locale.forLanguageTag("ar-u-nu-latn")
            EN -> Locale.forLanguageTag("en-u-nu-latn")
            else -> Locale.forLanguageTag("fr-u-nu-latn")
        }
    }

    fun formatNumber(value: Double, decimals: Int = 2): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    fun toWesternDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                in '\u06F0'..'\u06F9' -> sb.append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun tr(lang: String, ar: String, fr: String, en: String = fr): String {
        return when (lang.uppercase(Locale.US)) {
            AR -> ar
            EN -> en
            else -> fr
        }
    }

    @Composable
    fun res(@StringRes resId: Int): String = stringResource(id = resId)

    @StringRes
    fun resId(key: String): Int? = keyToResId[key]

    fun getString(context: Context, @StringRes resId: Int, language: String): String {
        init(context)
        return resolveByResId(resId, language) ?: context.getString(resId)
    }

    fun get(key: String, language: String): String {
        val id = keyToResId[key] ?: return key
        return resolveByResId(id, language) ?: key
    }

    fun getOrFallback(
        @StringRes resId: Int,
        language: String,
        arFallback: String,
        frFallback: String,
        enFallback: String
    ): String {
        return resolveByResId(resId, language) ?: tr(language, arFallback, frFallback, enFallback)
    }

    private fun resolveByResId(@StringRes resId: Int, language: String): String? {
        val ctx = appContext ?: return null
        val normLang = when (language.uppercase(Locale.US)) {
            AR -> AR
            EN -> EN
            else -> FR
        }
        return try {
            val res = localizedResourcesCache.getOrPut(normLang) {
                val config = Configuration(ctx.resources.configuration)
                config.setLocale(localeFor(normLang))
                ctx.createConfigurationContext(config).resources
            }
            res.getString(resId)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to resolve string resource id=$resId for lang=$normLang", e)
            null
        }
    }

    private val keyToResId: Map<String, Int> = mapOf(
        "app_title" to R.string.dict_app_title,
        "login" to R.string.dict_login,
        "username" to R.string.dict_username,
        "password" to R.string.dict_password,
        "remember_me" to R.string.dict_remember_me,
        "fingerprint" to R.string.dict_fingerprint,
        "manager" to R.string.dict_manager,
        "cashier" to R.string.dict_cashier,
        "login_error" to R.string.dict_login_error,
        "sales_today" to R.string.dict_sales_today,
        "profit_today" to R.string.dict_profit_today,
        "invoice_count" to R.string.dict_invoice_count,
        "customer_count" to R.string.dict_customer_count,
        "product_count" to R.string.dict_product_count,
        "low_stock" to R.string.dict_low_stock,
        "recent_ops" to R.string.dict_recent_ops,
        "new_sale_btn" to R.string.dict_new_sale_btn,
        "add_prod_btn" to R.string.dict_add_prod_btn,
        "inventory_btn" to R.string.dict_inventory_btn,
        "customers_btn" to R.string.dict_customers_btn,
        "reports_btn" to R.string.dict_reports_btn,
        "pos_btn" to R.string.dict_pos_btn,
        "clients_btn" to R.string.dict_clients_btn,
        "expenses_btn" to R.string.dict_expenses_btn,
        "settings_btn" to R.string.dict_settings_btn,
        "dashboard_btn" to R.string.dict_dashboard_btn,
        "dashboard" to R.string.dict_dashboard,
        "suppliers_btn" to R.string.dict_suppliers_btn,
        "purchases_btn" to R.string.dict_purchases_btn,
        "payments_btn" to R.string.dict_payments_btn,
        "search_by_name" to R.string.dict_search_by_name,
        "scan_barcode" to R.string.dict_scan_barcode,
        "product" to R.string.dict_product,
        "quantity" to R.string.dict_quantity,
        "unit_price" to R.string.dict_unit_price,
        "total" to R.string.dict_total,
        "subtotal" to R.string.dict_subtotal,
        "discount" to R.string.dict_discount,
        "discount_type" to R.string.dict_discount_type,
        "suspend_sale" to R.string.dict_suspend_sale,
        "recalled_sales" to R.string.dict_recalled_sales,
        "open_drawer" to R.string.dict_open_drawer,
        "drawer_opened" to R.string.dict_drawer_opened,
        "checkout" to R.string.dict_checkout,
        "clear_cart" to R.string.dict_clear_cart,
        "product_added" to R.string.dict_product_added,
        "payment_method" to R.string.dict_payment_method,
        "cash" to R.string.dict_cash,
        "card" to R.string.dict_card,
        "bank" to R.string.dict_bank,
        "mixed" to R.string.dict_mixed,
        "received_amount" to R.string.dict_received_amount,
        "change_amount" to R.string.dict_change_amount,
        "finalize" to R.string.dict_finalize,
        "client_debit_added" to R.string.dict_client_debit_added,
        "barcode" to R.string.dict_barcode,
        "name_ar" to R.string.dict_name_ar,
        "description" to R.string.dict_description,
        "category" to R.string.dict_category,
        "unit" to R.string.dict_unit,
        "purchase_price" to R.string.dict_purchase_price,
        "selling_price" to R.string.dict_selling_price,
        "wholesale_price" to R.string.dict_wholesale_price,
        "special_price" to R.string.dict_special_price,
        "initial_stock" to R.string.dict_initial_stock,
        "min_stock_alert" to R.string.dict_min_stock_alert,
        "save" to R.string.dict_save,
        "select_category" to R.string.dict_select_category,
        "new_category" to R.string.dict_new_category,
        "calc_title" to R.string.dict_calc_title,
        "retrieve_invoices" to R.string.dict_retrieve_invoices,
        "invoice_no" to R.string.dict_invoice_no,
        "void_invoice" to R.string.dict_void_invoice,
        "reprint" to R.string.dict_reprint,
        "status_success" to R.string.dict_status_success,
        "status_voided" to R.string.dict_status_voided,
        "status_debt" to R.string.dict_status_debt,
        "status_paid" to R.string.dict_status_paid,
        "settle_debt" to R.string.dict_settle_debt,
        "stock_movements" to R.string.dict_stock_movements,
        "type" to R.string.dict_type,
        "notes" to R.string.dict_notes,
        "inflow" to R.string.dict_inflow,
        "outflow" to R.string.dict_outflow,
        "phone" to R.string.dict_phone,
        "address" to R.string.dict_address,
        "email" to R.string.dict_email,
        "balance_debit" to R.string.dict_balance_debit,
        "total_spend" to R.string.dict_total_spend,
        "add_customer" to R.string.dict_add_customer,
        "add_supplier" to R.string.dict_add_supplier,
        "supplier_debt" to R.string.dict_supplier_debt,
        "supplier" to R.string.dict_supplier,
        "paid_amount" to R.string.dict_paid_amount,
        "record_purchase" to R.string.dict_record_purchase,
        "sales_period_report" to R.string.dict_sales_period_report,
        "weekly_report" to R.string.dict_weekly_report,
        "monthly_report" to R.string.dict_monthly_report,
        "best_sellers" to R.string.dict_best_sellers,
        "expenses" to R.string.dict_expenses,
        "add_expense" to R.string.dict_add_expense,
        "expense_title" to R.string.dict_expense_title,
        "amount" to R.string.dict_amount,
        "total_profits" to R.string.dict_total_profits,
        "export_pdf" to R.string.dict_export_pdf,
        "export_excel" to R.string.dict_export_excel,
        "backup_btn" to R.string.dict_backup_btn,
        "restore_btn" to R.string.dict_restore_btn,
        "backup_success" to R.string.dict_backup_success,
        "restore_success" to R.string.dict_restore_success,
        "settings" to R.string.dict_settings,
        "shop_name" to R.string.dict_shop_name,
        "receipt_header" to R.string.dict_receipt_header,
        "receipt_footer" to R.string.dict_receipt_footer,
        "users_management" to R.string.dict_users_management
    )
}
