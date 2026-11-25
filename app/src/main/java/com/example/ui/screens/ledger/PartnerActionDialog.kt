package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.UserEntity
import com.example.ui.components.AppBottomNavigationBar
import com.example.util.BarcodeGenerator

@Composable
fun PartnerActionDialog(
    isClient: Boolean = true,
    partyName: String,
    address: String,
    phone: String,
    balanceStr: String,
    isDebt: Boolean = false,
    locationStr: String? = null,
    customerNumber: Long = 0L,
    barcode: String? = null,
    canEdit: Boolean = true,
    onEdit: () -> Unit,
    onStatement: () -> Unit,
    onPayDebt: () -> Unit,
    onInvoicesHistory: () -> Unit,
    onDelete: () -> Unit,
    onPrintBarcode: (() -> Unit)? = null,
    onNavigateRoute: (() -> Unit)? = null,
    onNavigate: ((String) -> Unit)? = null,
    currentUser: UserEntity? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .background(Color(0xFFC00000), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isClient) "compte client" else "compte fournisseur",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                                    .border(BorderStroke(1.2.dp, Color.Black))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val rows = buildList {
                                        if (isClient && customerNumber > 0L) {
                                            add("رقم الزبون" to "#$customerNumber")
                                        }
                                        add((if (isClient) "الزبون" else "المورد") to partyName)
                                        if (isClient && !barcode.isNullOrBlank()) {
                                            add("الباركود" to barcode)
                                        }
                                        add("العنوان" to address.ifBlank { "-" })
                                        add("الهاتف" to phone.ifBlank { "-" })
                                        if (locationStr != null) {
                                            add("الموقع الجغرافي" to locationStr)
                                        }
                                        add("الرصيد المستحق" to balanceStr)
                                    }

                                    rows.forEachIndexed { index, (label, value) ->
                                        val isBalanceRow = index == rows.size - 1
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(34.dp)
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
                                                Text(
                                                    text = value,
                                                    color = if (isBalanceRow && isDebt) Color(0xFFDC2626) else Color.Black,
                                                    fontWeight = if (isBalanceRow) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 13.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
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
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isClient && !barcode.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                val barcodeBitmap = remember(barcode) {
                                    BarcodeGenerator.generateCode128Bitmap(
                                        content = barcode,
                                        width = 520,
                                        height = 150,
                                        showText = true
                                    )
                                }
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.2.dp, Color.Black),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp, horizontal = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            bitmap = barcodeBitmap.asImageBitmap(),
                                            contentDescription = "Code 128 Barcode: $barcode",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(75.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Text(
                                            text = "باركود الزبون (Code 128): $barcode",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF334155),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    ActionDialogButton(
                                        text = if (canEdit) "تعديل" else "🔒 تعديل",
                                        bgColor = if (canEdit) Color(0xFF00A0E9) else Color(0xFF64748B),
                                        modifier = Modifier.weight(1f),
                                        onClick = onEdit
                                    )
                                    ActionDialogButton(
                                        text = "كشف الحساب",
                                        bgColor = Color(0xFF7CB342),
                                        modifier = Modifier.weight(1f),
                                        onClick = onStatement
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    ActionDialogButton(
                                        text = "تسديد دين",
                                        bgColor = Color(0xFFFF0000),
                                        modifier = Modifier.weight(1f),
                                        onClick = onPayDebt
                                    )
                                    ActionDialogButton(
                                        text = "خروج",
                                        bgColor = Color(0xFFFBC02D),
                                        modifier = Modifier.weight(1f),
                                        onClick = onDismiss
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    ActionDialogButton(
                                        text = "سجل فواتير",
                                        bgColor = Color(0xFF0284C7),
                                        modifier = Modifier.weight(1f),
                                        onClick = onInvoicesHistory
                                    )
                                    ActionDialogButton(
                                        text = if (isClient) "مسح الزبون" else "مسح المورد",
                                        bgColor = Color(0xFFB71C1C),
                                        modifier = Modifier.weight(1f),
                                        onClick = onDelete
                                    )
                                }

                                if (isClient && (onPrintBarcode != null || onNavigateRoute != null)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        if (onPrintBarcode != null) {
                                            ActionDialogButton(
                                                text = "🖨️ طباعة كودبار",
                                                bgColor = Color(0xFF4F46E5),
                                                modifier = Modifier.weight(1f),
                                                onClick = onPrintBarcode
                                            )
                                        }
                                        if (onNavigateRoute != null) {
                                            ActionDialogButton(
                                                text = "📍 تتبع المسار",
                                                bgColor = Color(0xFF0D9488),
                                                modifier = Modifier.weight(1f),
                                                onClick = onNavigateRoute
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }

            AppBottomNavigationBar(
                activeScreen = if (isClient) "CLIENTS" else "SUPPLIERS",
                onNavigate = { screen ->
                    onDismiss()
                    onNavigate?.invoke(screen)
                },
                currentUser = currentUser,
                lang = "AR"
            )
        }
    }
}
