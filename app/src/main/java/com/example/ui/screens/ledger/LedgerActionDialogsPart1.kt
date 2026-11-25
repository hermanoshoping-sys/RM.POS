package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun InvoiceActionDialog(
    isSale: Boolean,
    partyName: String,
    invoiceNumber: String,
    dateStr: String,
    totalStr: String,
    paidStr: String,
    paymentMethodStr: String,
    onEdit: () -> Unit,
    onValidate: () -> Unit,
    onConvertToDebt: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onEditDate: (() -> Unit)? = null,
    onEditInvoiceNumber: (() -> Unit)? = null,
    onEditParty: (() -> Unit)? = null,
    sellerName: String? = null,
    onPartialPay: (() -> Unit)? = null,
    remainingAmountDouble: Double = 0.0
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD6D9DE)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFD6D9DE))
                            .border(BorderStroke(1.5.dp, Color.Black))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .drawBehind {
                                    drawLine(
                                        color = Color.Black,
                                        start = Offset(0f, size.height),
                                        end = Offset(size.width, size.height),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSale) "فاتورة بيع" else "فاتورة شراء",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        val rows = buildList {
                            add((if (isSale) "الزبون" else "المورد") to partyName)
                            if (!sellerName.isNullOrBlank()) {
                                add("البائع" to sellerName)
                            }
                            add("رقم الفاتورة" to invoiceNumber)
                            add("التاريخ" to dateStr)
                            add("المجموع" to totalStr)
                            add("المبلغ المسدد" to paidStr)
                            add("طريقة الدفع" to paymentMethodStr)
                        }

                        rows.forEachIndexed { index, (label, value) ->
                            val isPartyRow = (label == "الزبون" || label == "المورد") && onEditParty != null
                            val isInvRow = label == "رقم الفاتورة" && onEditInvoiceNumber != null
                            val isDateRow = label == "التاريخ" && onEditDate != null
                            val isEditableRow = isPartyRow || isInvRow || isDateRow

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .then(
                                        if (isEditableRow) {
                                            Modifier.clickable {
                                                when {
                                                    isPartyRow -> onEditParty?.invoke()
                                                    isInvRow -> onEditInvoiceNumber?.invoke()
                                                    isDateRow -> onEditDate?.invoke()
                                                }
                                            }
                                        } else Modifier
                                    )
                                    .drawBehind {
                                        if (index < rows.size - 1) {
                                            drawLine(
                                                color = Color.Black,
                                                start = Offset(0f, size.height),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .fillMaxHeight()
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = value,
                                            color = if (isEditableRow) Color(0xFF0369A1) else Color.Black,
                                            fontWeight = if (isEditableRow) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        if (isEditableRow) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("✏️", fontSize = 11.sp)
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(Color.Black)
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (onPartialPay != null && remainingAmountDouble > 0.001) {
                            Button(
                                onClick = onPartialPay,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("invoice_action_partial_pay_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Payments,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "تسديد جزء من الفاتورة",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ActionDialogButton(
                                text = "تعديل",
                                bgColor = Color(0xFF00A0E9),
                                modifier = Modifier.weight(1f),
                                onClick = onEdit
                            )
                            ActionDialogButton(
                                text = "valider",
                                bgColor = Color(0xFF7CB342),
                                modifier = Modifier.weight(1f),
                                onClick = onValidate
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ActionDialogButton(
                                text = "تحويل لدين",
                                bgColor = Color(0xFFFF0000),
                                modifier = Modifier.weight(1f),
                                onClick = onConvertToDebt
                            )
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "خروج",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "❌",
                                        color = Color(0xFFDC2626),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Button(
                                onClick = onPrint,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091EA)),
                                shape = RoundedCornerShape(10.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Print,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "طباعة",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                }
                            }
                            ActionDialogButton(
                                text = if (isSale) "مسح الفاتورة" else "حذف الفاتورة",
                                bgColor = Color(0xFFB71C1C),
                                modifier = Modifier.weight(1f),
                                onClick = onDelete
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
