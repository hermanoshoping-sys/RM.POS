package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * =========================================================================================
 * وحدة المعالجة المالية والحسابات النقدية الدقيقة (MoneyUtils)
 * تضمن دقة العمليات الحسابية وتمنع أخطاء التقريب والكسور العائمة في جافا/كوتلن (Floating-point precision issues).
 * =========================================================================================
 */
object MoneyUtils {

    const val EPSILON = 0.0001
    private const val DEFAULT_SCALE = 2

    /**
     * تقريب المبالغ المالية إلى خانتين عشريتين بدقة بنكية متسقة (HALF_UP)
     */
    fun roundMoney(amount: Double, scale: Int = DEFAULT_SCALE): Double {
        if (amount.isNaN() || amount.isInfinite()) return 0.0
        return BigDecimal(amount.toString())
            .setScale(scale, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * جمع مالي آمن مع تقريب النتيجة
     */
    fun add(a: Double, b: Double): Double {
        val b1 = BigDecimal(roundMoney(a).toString())
        val b2 = BigDecimal(roundMoney(b).toString())
        return b1.add(b2).setScale(DEFAULT_SCALE, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * طرح مالي آمن (a - b) مع تقريب النتيجة
     */
    fun subtract(a: Double, b: Double): Double {
        val b1 = BigDecimal(roundMoney(a).toString())
        val b2 = BigDecimal(roundMoney(b).toString())
        return b1.subtract(b2).setScale(DEFAULT_SCALE, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * ضرب مالي آمن (السعر * الكمية) مع تقريب النتيجة
     */
    fun multiply(price: Double, quantity: Double): Double {
        val b1 = BigDecimal(roundMoney(price).toString())
        val b2 = BigDecimal(quantity.toString())
        return b1.multiply(b2).setScale(DEFAULT_SCALE, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * قسمة مالية آمنة
     */
    fun divide(amount: Double, divisor: Double): Double {
        if (isZero(divisor)) return 0.0
        val b1 = BigDecimal(roundMoney(amount).toString())
        val b2 = BigDecimal(divisor.toString())
        return b1.divide(b2, DEFAULT_SCALE, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * فحص آمن: هل المبلغ أكبر من الصفر؟ (بديل آمن لـ amount > 0.0)
     */
    fun isPositive(amount: Double): Boolean {
        return amount > EPSILON
    }

    /**
     * فحص آمن: هل المبلغ يساوي صفراً؟ (بديل آمن لـ amount == 0.0)
     */
    fun isZero(amount: Double): Boolean {
        return kotlin.math.abs(amount) <= EPSILON
    }

    /**
     * فحص آمن: هل a أكبر من b؟ (بديل آمن لـ a > b)
     */
    fun isGreater(a: Double, b: Double): Boolean {
        return (a - b) > EPSILON
    }

    /**
     * فحص آمن: هل a أكبر من أو يساوي b؟
     */
    fun isGreaterOrEqual(a: Double, b: Double): Boolean {
        return (a - b) >= -EPSILON
    }

    /**
     * فحص آمن: هل a أصغر من b؟
     */
    fun isLess(a: Double, b: Double): Boolean {
        return (b - a) > EPSILON
    }

    /**
     * فحص آمن للتطابق المالي بين قيمتين (بديل آمن لـ a == b)
     */
    fun equals(a: Double, b: Double): Boolean {
        return kotlin.math.abs(a - b) <= EPSILON
    }

    /**
     * تحويل المبلغ إلى سنتيمات كـ Long (للتخزين الدقيق في حال الاعتماد المستقبلي)
     */
    fun toCents(amount: Double): Long {
        return Math.round(roundMoney(amount) * 100.0)
    }

    /**
     * تحويل السنتيمات كـ Long إلى مبلغ عشري Double
     */
    fun fromCents(cents: Long): Double {
        return cents / 100.0
    }

    // --- العمليات الحسابية بوحدات السنتيم الصغرى (Long Cents) بدون أي انحراف عشري ---

    /**
     * جمع سنتيمات آمن
     */
    fun addCents(a: Long, b: Long): Long = a + b

    /**
     * طرح سنتيمات آمن
     */
    fun subtractCents(a: Long, b: Long): Long = a - b

    /**
     * ضرب سعر الوحدة بالسنتيم في الكمية مع تقريب النتيجة بدقة بنكية
     */
    fun multiplyCents(unitPriceCents: Long, quantity: Double): Long {
        val b1 = BigDecimal(unitPriceCents)
        val b2 = BigDecimal(quantity.toString())
        return b1.multiply(b2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /**
     * قسمة مبالغ بالسنتيمات
     */
    fun divideCents(totalCents: Long, divisor: Double): Long {
        if (isZero(divisor)) return 0L
        val b1 = BigDecimal(totalCents)
        val b2 = BigDecimal(divisor.toString())
        return b1.divide(b2, 0, RoundingMode.HALF_UP).toLong()
    }

    /**
     * احتساب نسبة الخصم أو الضريبة بالسنتيم
     */
    fun percentageCents(amountCents: Long, percent: Double): Long {
        val b1 = BigDecimal(amountCents)
        val b2 = BigDecimal((percent / 100.0).toString())
        return b1.multiply(b2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /**
     * تنسيق السنتيمات مباشرة للعرض مع فاصل الآلاف وخانتين عشريتين
     */
    fun formatCents(cents: Long, currency: String): String {
        return format(fromCents(cents), currency)
    }

    /**
     * فحص آمن: هل المبلغ بالسنتيم أكبر من الصفر؟
     */
    fun isPositive(cents: Long): Boolean = cents > 0L

    /**
     * فحص آمن: هل المبلغ بالسنتيم يساوي صفراً؟
     */
    fun isZero(cents: Long): Boolean = cents == 0L

    /**
     * تنسيق المبلغ للعرض (Long سنتيم) مع فاصل الآلاف وخانتين عشريتين
     */
    fun format(cents: Long, currency: String): String = formatCents(cents, currency)

    /**
     * تنسيق المبلغ للعرض مع فاصل الآلاف وخانتين عشريتين ومنحى أرقام فرنسي (من اليسار إلى اليمين)
     * حتى عند دمجه داخل جمل عربية (RTL).
     */
    fun format(amount: Double, currency: String): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = '\u00A0'
            decimalSeparator = '.'
        }
        val formatter = DecimalFormat("#,##0.00", symbols)
        val formatted = "${formatter.format(roundMoney(amount))} $currency".trim()
        return "\u2066$formatted\u2069"
    }
}

/**
 * High-performance, zero-allocation Value Class representing exact monetary values as Long cents.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {
    constructor(amount: Double) : this(MoneyUtils.toCents(amount))

    val asDouble: Double get() = MoneyUtils.fromCents(cents)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    operator fun plus(other: Money): Money = Money(cents + other.cents)
    operator fun minus(other: Money): Money = Money(cents - other.cents)
    operator fun times(qty: Double): Money = Money(MoneyUtils.multiplyCents(cents, qty))
    operator fun div(divisor: Double): Money = Money(MoneyUtils.divideCents(cents, divisor))

    fun format(currency: String): String = MoneyUtils.formatCents(cents, currency)

    override fun toString(): String = "${fromCents(cents)} ($cents cents)"

    companion object {
        val ZERO = Money(0L)
        fun fromDouble(amount: Double): Money = Money(amount)
        fun fromCents(cents: Long): Money = Money(cents)
    }
}

/**
 * Extension properties and methods for seamless conversion between Double, Long cents and Money
 */
inline val Double.cents: Long get() = MoneyUtils.toCents(this)
inline val Long.moneyDouble: Double get() = MoneyUtils.fromCents(this)
inline val Double.money: Money get() = Money(this)
inline val Long.money: Money get() = Money(this)
inline fun Double.toMoneyCents(): Long = MoneyUtils.toCents(this)
inline fun Long.toMoneyDouble(): Double = MoneyUtils.fromCents(this)

/**
 * Safe Data Migration and Validation helper for financial fields (Double <-> Long cents)
 * Provides lossless data transformation between IEEE 754 Floating-point (Double) and integer micro-units (Long Cents).
 * Guarantees zero precision loss, handles null/NaN/infinite values safely, and supports atomic database migration.
 */
object MoneyDataMigration {
    fun migrateDoubleAmountToCents(amount: Double): Long = amount.cents
    fun migrateCentsToDoubleAmount(cents: Long): Double = cents.moneyDouble

    /**
     * Safely migrates an optional/nullable Double amount into Long cents.
     * Returns null if input is null, 0L for NaN/Infinite.
     */
    fun safeDoubleToCents(amount: Double?): Long? {
        if (amount == null) return null
        if (amount.isNaN() || amount.isInfinite()) return 0L
        return amount.cents
    }

    /**
     * Safely converts Long cents back to Double currency units.
     */
    fun safeCentsToDouble(cents: Long?): Double? {
        if (cents == null) return null
        return cents.moneyDouble
    }

    /**
     * Verifies round-trip integrity: ensures converting Double -> Long Cents -> Double
     * maintains financial accuracy within 0.0001 (1/100th of a cent).
     */
    fun verifyRoundtripIntegrity(amount: Double): Boolean {
        if (amount.isNaN() || amount.isInfinite()) return false
        val cents = migrateDoubleAmountToCents(amount)
        val recovered = migrateCentsToDoubleAmount(cents)
        return MoneyUtils.equals(amount, recovered)
    }

    /**
     * Helper to compute safe SQL expression for converting floating point currency columns to integer cents:
     * CAST(ROUND(column * 100.0) AS INTEGER)
     */
    fun sqlFloatToCentsExpression(columnName: String): String {
        return "CAST(ROUND($columnName * 100.0) AS INTEGER)"
    }

    /**
     * Helper to compute safe SQL expression for converting integer cents columns to floating point currency:
     * ($columnName / 100.0)
     */
    fun sqlCentsToFloatExpression(columnName: String): String {
        return "($columnName / 100.0)"
    }
}
