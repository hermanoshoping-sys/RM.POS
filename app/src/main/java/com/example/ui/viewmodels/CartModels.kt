package com.example.ui.viewmodels

import com.example.data.ProductEntity
import com.example.util.MoneyUtils
import com.example.util.cents
import com.example.util.moneyDouble

/**
 * Cart Item Model for POS register & checkout calculations.
 */
data class CartItem(
    val product: ProductEntity,
    val quantity: Double,
    val customPrice: Double? = null,
    val lineDiscount: Double = 0.0
) {
    val unitPriceCents: Long
        get() = customPrice?.cents ?: product.sellPriceCents

    val unitPrice: Double
        get() = unitPriceCents.moneyDouble

    val totalBeforeDiscountCents: Long
        get() = MoneyUtils.multiplyCents(unitPriceCents, quantity)

    val totalBeforeDiscount: Double
        get() = totalBeforeDiscountCents.moneyDouble

    val lineDiscountCents: Long
        get() = lineDiscount.cents

    val totalAfterDiscountCents: Long
        get() = if (quantity < 0.0) {
            MoneyUtils.multiplyCents(unitPriceCents, quantity)
        } else {
            (totalBeforeDiscountCents - lineDiscountCents).coerceAtLeast(0L)
        }

    val totalAfterDiscount: Double
        get() = totalAfterDiscountCents.moneyDouble
}

/**
 * Suspended Sale Holder.
 */
data class SuspendedSale(
    val timestamp: Long,
    val items: List<CartItem>,
    val description: String
)
