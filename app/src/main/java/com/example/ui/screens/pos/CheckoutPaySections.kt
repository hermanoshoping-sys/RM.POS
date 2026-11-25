package com.example.ui.screens.pos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Bilingual

@Composable
internal fun CheckoutAmountBreakdownRows(
    actualSubTotal: Double,
    discountAmount: Double,
    netTotal: Double,
    formatMoney: (Double) -> String,
    lang: String,
    isReturnInvoice: Boolean = false,
    onEditDiscount: () -> Unit
) {
    val netTotalBg = if (isReturnInvoice) Color(0xFFDC2626) else Color(0xFF00A8FF)

    // 1. Subtotal
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isReturnInvoice) Color(0xFFB91C1C) else Color(0xFF86D24A)),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Text(
                    text = formatMoney(actualSubTotal),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Box(
            modifier = Modifier
                .width(115.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isReturnInvoice) Color(0xFFB91C1C) else Color(0xFF86D24A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isReturnInvoice) {
                    Bilingual.tr(lang, "المسترجع", "Retour", "Refund")
                } else {
                    Bilingual.tr(lang, "المجموع", "Total", "Subtotal")
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    if (!isReturnInvoice) {
        Spacer(modifier = Modifier.height(10.dp))

        // 2. Discount
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEditDiscount),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF0000)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                        Text(
                            text = formatMoney(discountAmount),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = Bilingual.tr(lang, "تعديل الخصم", "Modifier remise", "Edit discount"),
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(115.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF0000)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Bilingual.tr(lang, "خصم ✏️", "Remise ✏️", "Discount ✏️"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 3. Net Total
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(netTotalBg),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Text(
                    text = formatMoney(netTotal),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Box(
            modifier = Modifier
                .width(115.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(netTotalBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isReturnInvoice) {
                    Bilingual.tr(lang, "صافي الاسترجاع", "Net retour", "Net Refund")
                } else {
                    Bilingual.tr(lang, "الصافي", "Net à payer", "Net Total")
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = if (isReturnInvoice) 15.sp else 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun CheckoutPaymentAndReceivedSection(
    chosenPaymentMethod: String,
    receivedAmountInput: String,
    receivedAmount: Double,
    changeDue: Double,
    formatMoney: (Double) -> String,
    lang: String,
    isReturnInvoice: Boolean = false,
    onSelectMethod: (String) -> Unit,
    onReceivedInputChanged: (String) -> Unit
) {
    Text(
        text = if (isReturnInvoice) {
            Bilingual.tr(lang, "طريقة الاسترجاع", "Mode de remboursement", "Refund Method")
        } else {
            Bilingual.tr(lang, "طريقة الدفع", "Mode de paiement", "Payment Method")
        },
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val bankActive = chosenPaymentMethod == "BANK"
        val creditActive = chosenPaymentMethod == "CREDIT"
        val cashActive = chosenPaymentMethod == "CASH"

        Box(
            modifier = Modifier
                .weight(1.2f)
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFFC000))
                .then(if (bankActive) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
                .clickable { onSelectMethod("BANK") },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = Bilingual.tr(lang, "تحويل بنكي", "Virement", "Bank Transfer"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .weight(1.1f)
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isReturnInvoice) Color(0xFF059669) else Color(0xFFFF0000))
                .then(if (creditActive) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
                .clickable { onSelectMethod("CREDIT") },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isReturnInvoice) {
                    Bilingual.tr(lang, "خصم من الدين", "Déduire Crédit", "Credit Debt")
                } else {
                    Bilingual.tr(lang, "دين", "Crédit", "Credit")
                },
                fontSize = if (isReturnInvoice) 13.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .weight(1.1f)
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF70C038))
                .then(if (cashActive) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
                .clickable { onSelectMethod("CASH") },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isReturnInvoice) {
                    Bilingual.tr(lang, "إرجاع نقدي", "Remb. Espèces", "Cash Refund")
                } else {
                    Bilingual.tr(lang, "نقدي", "Espèces", "Cash")
                },
                fontSize = if (isReturnInvoice) 13.5.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = if (isReturnInvoice) {
            Bilingual.tr(lang, "المبلغ المُرجَع نقداً", "Montant remboursé", "Cash Refunded")
        } else {
            Bilingual.tr(lang, "المبلغ المستلم", "Montant reçu", "Amount Received")
        },
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(10.dp))

    var isEditingReceived by remember { mutableStateOf(false) }
    if (!isEditingReceived) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isReturnInvoice) Color(0xFFFECACA) else Color(0xFFC5E1A5))
                .clickable { isEditingReceived = true },
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Text(
                    text = formatMoney(receivedAmount),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        OutlinedTextField(
            value = receivedAmountInput,
            onValueChange = { input ->
                val normalized = input.replace(',', '.').replace('−', '-').replace('–', '-')
                val filtered = if (isReturnInvoice) {
                    val sb = StringBuilder()
                    var hasDot = false
                    for (ch in normalized) {
                        when {
                            ch == '-' && sb.isEmpty() -> sb.append('-')
                            ch.isDigit() -> sb.append(ch)
                            ch == '.' && !hasDot -> {
                                sb.append('.')
                                hasDot = true
                            }
                        }
                    }
                    sb.toString()
                } else {
                    normalized.filter { it.isDigit() || it == '.' }
                }
                onReceivedInputChanged(filtered)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isReturnInvoice) KeyboardType.Text else KeyboardType.Decimal
            ),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isReturnInvoice) Color(0xFFFECACA) else Color(0xFFC5E1A5),
                unfocusedContainerColor = if (isReturnInvoice) Color(0xFFFECACA) else Color(0xFFC5E1A5),
                focusedBorderColor = if (isReturnInvoice) Color(0xFFDC2626) else Color(0xFF70C038),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            ),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            textStyle = TextStyle(
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        )
    }

    if (!isReturnInvoice) {
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF0000)),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Text(
                        text = formatMoney(changeDue),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(115.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF0000)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Bilingual.tr(lang, "الباقي", "Reste à rendre", "Change Due"),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun CheckoutFooterBrand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Canvas(modifier = Modifier.size(46.dp)) {
            val p = size.width / 5f
            val cyan = Color(0xFF00E5FF)
            val blue = Color(0xFF0066FF)

            drawRect(cyan, topLeft = Offset(p * 1.5f, 0f), size = Size(p, p))
            drawRect(cyan, topLeft = Offset(p * 2.7f, 0f), size = Size(p, p))
            drawRect(cyan, topLeft = Offset(0.5f * p, p * 1.2f), size = Size(p, p))
            drawRect(blue, topLeft = Offset(p * 1.8f, p * 1.2f), size = Size(p * 2f, p))
            drawRect(blue, topLeft = Offset(p * 1.2f, p * 2.4f), size = Size(p, p))
            drawRect(blue, topLeft = Offset(p * 2.4f, p * 2.4f), size = Size(p * 2.2f, p * 2f))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Rioual.app",
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF38BDF8)
        )
    }
}
