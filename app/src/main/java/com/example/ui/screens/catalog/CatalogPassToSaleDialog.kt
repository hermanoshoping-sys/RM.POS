package com.example.ui.screens.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ProductEntity
import com.example.util.MoneyUtils
import java.util.Locale
import kotlin.math.abs

/**
 * Dialog shown when tapping a product / "مرر للبيع" in the Catalog:
 * Allows entering quantity, viewing/modifying unit price (with price-tier shortcuts
 * and optional permanent product price update), and immediately passing to POS cart.
 */
@Composable
fun CatalogPassToSaleDialog(
    product: ProductEntity,
    existingCartQuantity: Double,
    existingCartPrice: Double?,
    canModifyPrice: Boolean,
    currency: String,
    lang: String,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, unitPrice: Double, saveAsDefaultPrice: Boolean) -> Unit,
    isPurchaseMode: Boolean = false
) {
    val isAr = lang == "AR"
    val isInCart = abs(existingCartQuantity) > 0.0001
    val defaultPrice = if (isPurchaseMode) product.purchasePriceDouble else product.sellPriceDouble
    val initialPrice = existingCartPrice ?: defaultPrice
    val initialQty = if (isInCart) existingCartQuantity else 1.0

    var quantityText by remember(product.id, existingCartQuantity) {
        mutableStateOf(formatCleanQty(initialQty))
    }
    var priceText by remember(product.id, existingCartPrice, isPurchaseMode) {
        mutableStateOf(String.format(Locale.US, "%.2f", initialPrice))
    }
    var isPriceEditUnlocked by remember(product.id, existingCartPrice, canModifyPrice, isPurchaseMode) {
        mutableStateOf(canModifyPrice && existingCartPrice != null && abs(existingCartPrice - defaultPrice) > 0.001)
    }
    var saveAsDefaultPrice by remember(product.id) { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val parsedQty = quantityText.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
    val isReturnMode = parsedQty < 0.0 || quantityText.trim().startsWith("-")
    val parsedPrice = priceText.replace(',', '.').trim().toDoubleOrNull() ?: defaultPrice
    val isPriceModified = abs(parsedPrice - defaultPrice) > 0.001
    val lineTotal = parsedQty * (parsedPrice.coerceAtLeast(0.0))
    val isBelowCost = !isPurchaseMode && !isReturnMode && product.purchasePriceDouble > 0.0 && parsedPrice < product.purchasePriceDouble

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF111C33),
            border = BorderStroke(1.2.dp, if (isReturnMode) Color(0xFFEF4444) else Color(0xFF334155)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CatalogPassHeaderAndStockBanner(
                    product = product,
                    isAr = isAr,
                    onDismiss = onDismiss
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                CatalogPassQuantitySection(
                    isAr = isAr,
                    isPurchaseMode = isPurchaseMode,
                    quantityText = quantityText,
                    parsedQty = parsedQty,
                    onQuantityTextChange = {
                        quantityText = it
                        errorMessage = null
                    }
                )

                CatalogPassPriceSection(
                    product = product,
                    isAr = isAr,
                    isPurchaseMode = isPurchaseMode,
                    currency = currency,
                    defaultPrice = defaultPrice,
                    priceText = priceText,
                    parsedPrice = parsedPrice,
                    canModifyPrice = canModifyPrice,
                    isPriceEditUnlocked = isPriceEditUnlocked,
                    isPriceModified = isPriceModified,
                    isBelowCost = isBelowCost,
                    saveAsDefaultPrice = saveAsDefaultPrice,
                    onTogglePriceEdit = { isPriceEditUnlocked = !isPriceEditUnlocked },
                    onUnlockPriceEdit = { isPriceEditUnlocked = true },
                    onPriceTextChange = {
                        priceText = it
                        errorMessage = null
                    },
                    onResetPrice = {
                        priceText = String.format(Locale.US, "%.2f", defaultPrice)
                        saveAsDefaultPrice = false
                    },
                    onSaveAsDefaultChange = { saveAsDefaultPrice = it }
                )

                Surface(
                    color = if (isReturnMode) Color(0xFF450A0A) else Color(0xFF0F2942),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isReturnMode) Color(0xFFEF4444).copy(alpha = 0.75f) else Color(0xFF0284C7).copy(alpha = 0.65f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isReturnMode) {
                                if (isAr) "مبلغ الاسترجاع" else "Montant retour"
                            } else {
                                if (isAr) "المجموع" else "Sous-total"
                            },
                            color = if (isReturnMode) Color(0xFFFCA5A5) else Color(0xFF93C5FD),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = MoneyUtils.format(lineTotal, currency),
                            color = if (isReturnMode) Color(0xFFF87171) else Color(0xFF34D399),
                            fontWeight = FontWeight.Black,
                            fontSize = 19.sp
                        )
                    }
                }

                errorMessage?.let { errText ->
                    Text(
                        text = errText,
                        color = Color(0xFFF87171),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .weight(0.38f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (isAr) "إلغاء" else "Annuler",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    Button(
                        onClick = {
                            val qty = quantityText.replace(',', '.').trim().toDoubleOrNull()
                            val price = priceText.replace(',', '.').trim().toDoubleOrNull()
                            when {
                                qty == null || !qty.isFinite() || abs(qty) < 0.0001 -> {
                                    errorMessage = if (isAr) "يرجى إدخال كمية غير صفرية (+ أو -)" else "Veuillez saisir une quantité non nulle (+ ou -)"
                                }
                                price == null || !price.isFinite() || price < 0.0 -> {
                                    errorMessage = if (isAr) "يرجى إدخال ثمن صحيح" else "Veuillez saisir un prix valide"
                                }
                                else -> {
                                    val finalPrice = if (canModifyPrice) price else defaultPrice
                                    onConfirm(qty, finalPrice, canModifyPrice && isPriceModified && saveAsDefaultPrice)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isReturnMode) Color(0xFFDC2626) else Color(0xFF16A34A)
                        ),
                        modifier = Modifier
                            .weight(0.62f)
                            .height(48.dp)
                            .testTag("catalog_pass_confirm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isPurchaseMode && isReturnMode -> {
                                    if (isAr) "استرجاع للمورد (-)" else "Retour fourn. (-)"
                                }
                                isPurchaseMode -> {
                                    if (isInCart) {
                                        if (isAr) "تحديث في سلة المشتريات ⚡" else "Mettre à jour ⚡"
                                    } else {
                                        if (isAr) "إضافة لسلة المشتريات ⚡" else "Ajouter à l'achat ⚡"
                                    }
                                }
                                isReturnMode -> {
                                    if (isAr) "إضافة كمرتجع (-)" else "Ajouter retour (-)"
                                }
                                else -> {
                                    if (isInCart) {
                                        if (isAr) "تحديث في السلة ⚡" else "Mettre à jour ⚡"
                                    } else {
                                        if (isAr) "مرر للبيع ⚡" else "Passer en vente ⚡"
                                    }
                                }
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
