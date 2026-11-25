package com.example.ui.screens.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import com.example.util.MoneyUtils
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun CatalogPassPriceSection(
    product: ProductEntity,
    isAr: Boolean,
    isPurchaseMode: Boolean,
    currency: String,
    defaultPrice: Double,
    priceText: String,
    parsedPrice: Double,
    canModifyPrice: Boolean,
    isPriceEditUnlocked: Boolean,
    isPriceModified: Boolean,
    isBelowCost: Boolean,
    saveAsDefaultPrice: Boolean,
    onTogglePriceEdit: () -> Unit,
    onUnlockPriceEdit: () -> Unit,
    onPriceTextChange: (String) -> Unit,
    onResetPrice: () -> Unit,
    onSaveAsDefaultChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (isPurchaseMode) {
                        if (isAr) "ثمن الشراء ($currency)" else "Prix d'achat ($currency)"
                    } else {
                        if (isAr) "الثمن ($currency)" else "Prix ($currency)"
                    },
                    color = Color(0xFFCBD5E1),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                if (isPriceModified) {
                    Text(
                        text = MoneyUtils.format(defaultPrice, currency),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            }

            if (canModifyPrice) {
                Surface(
                    onClick = onTogglePriceEdit,
                    color = if (isPriceEditUnlocked) Color(0xFFD97706) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isPriceEditUnlocked) Color(0xFFF59E0B) else Color(0xFF475569)),
                    modifier = Modifier.testTag("catalog_toggle_edit_price_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = if (isPriceEditUnlocked) Color.White else Color(0xFFFBBF24),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isAr) {
                                if (isPriceEditUnlocked) "تعديل الثمن مفعّل" else "تغيير الثمن"
                            } else {
                                if (isPriceEditUnlocked) "Modification active" else "Modifier prix"
                            },
                            color = if (isPriceEditUnlocked) Color.White else Color(0xFFFBBF24),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isAr) "الثمن مقفل" else "Prix verrouillé",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        }

        OutlinedTextField(
            value = priceText,
            onValueChange = { raw ->
                if (canModifyPrice) {
                    onUnlockPriceEdit()
                    onPriceTextChange(raw.filter { it.isDigit() || it == '.' || it == ',' })
                }
            },
            enabled = canModifyPrice && isPriceEditUnlocked,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center,
                textDirection = TextDirection.Ltr,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = if (isPriceEditUnlocked) Color(0xFFFDE68A) else Color(0xFF34D399)
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = if (canModifyPrice && isPriceModified) {
                {
                    IconButton(onClick = onResetPrice) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = if (isAr) "استعادة الثمن الأصلي" else "Reset price",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFF59E0B),
                unfocusedBorderColor = if (isPriceEditUnlocked) Color(0xFFF59E0B).copy(alpha = 0.7f) else Color(0xFF334155),
                disabledBorderColor = Color(0xFF334155),
                focusedContainerColor = Color(0xFF0B1324),
                unfocusedContainerColor = Color(0xFF0B1324),
                disabledContainerColor = Color(0xFF0B1324).copy(alpha = 0.7f),
                disabledTextColor = Color(0xFF34D399)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canModifyPrice && !isPriceEditUnlocked) Modifier.clickable { onUnlockPriceEdit() }
                    else Modifier
                )
                .testTag("catalog_pass_price_input")
        )

        val hasTiers = !isPurchaseMode && (product.wholesalePriceDouble > 0.0 || product.specialPriceDouble > 0.0)
        if (canModifyPrice && hasTiers) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceTierChip(
                    label = if (isAr) "بيع" else "Détail",
                    price = product.sellPriceDouble,
                    isSelected = abs(parsedPrice - product.sellPriceDouble) < 0.001,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onUnlockPriceEdit()
                        onPriceTextChange(String.format(Locale.US, "%.2f", product.sellPriceDouble))
                    }
                )
                if (product.wholesalePriceDouble > 0.0) {
                    PriceTierChip(
                        label = if (isAr) "جملة" else "Gros",
                        price = product.wholesalePriceDouble,
                        isSelected = abs(parsedPrice - product.wholesalePriceDouble) < 0.001,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUnlockPriceEdit()
                            onPriceTextChange(String.format(Locale.US, "%.2f", product.wholesalePriceDouble))
                        }
                    )
                }
                if (product.specialPriceDouble > 0.0) {
                    PriceTierChip(
                        label = if (isAr) "خاص" else "Spécial",
                        price = product.specialPriceDouble,
                        isSelected = abs(parsedPrice - product.specialPriceDouble) < 0.001,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUnlockPriceEdit()
                            onPriceTextChange(String.format(Locale.US, "%.2f", product.specialPriceDouble))
                        }
                    )
                }
            }
        }

        if (isBelowCost) {
            Surface(
                color = Color(0xFF7F1D1D).copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isAr)
                            "تنبيه: الثمن أقل من سعر الشراء (${MoneyUtils.format(product.purchasePriceDouble, currency)})"
                        else
                            "Attention : prix inférieur au prix d'achat (${MoneyUtils.format(product.purchasePriceDouble, currency)})",
                        color = Color(0xFFFECACA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (canModifyPrice && isPriceModified && parsedPrice >= 0.0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                    .clickable { onSaveAsDefaultChange(!saveAsDefaultPrice) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Checkbox(
                    checked = saveAsDefaultPrice,
                    onCheckedChange = onSaveAsDefaultChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFFF59E0B),
                        uncheckedColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.testTag("catalog_save_new_price_checkbox")
                )
                Text(
                    text = if (isPurchaseMode) {
                        if (isAr) "حفظ ثمن الشراء الجديد كسعر افتراضي للمنتج" else "Enregistrer comme prix d'achat par défaut"
                    } else {
                        if (isAr) "حفظ الثمن الجديد كسعر افتراضي للمنتج" else "Enregistrer comme prix par défaut"
                    },
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PriceTierChip(
    label: String,
    price: Double,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) Color(0xFFD97706) else Color(0xFF1E293B),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFFFBBF24) else Color(0xFF334155)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format(Locale.US, "%.2f", price),
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
