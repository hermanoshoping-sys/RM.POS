package com.example.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.SupplierEntity
import com.example.ui.screens.pos.GreenCartActionButton
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils

@Composable
internal fun PurchasesTopHeaderBar(
    lang: String,
    onBack: () -> Unit,
    onToggleLang: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        Text(
            text = if (lang == "AR") "انشاء فاتورة المشتريات" else "Créer Facture d'Achat",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 17.5.sp,
            textAlign = TextAlign.Center
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                color = Color(0xFF1E2B4D),
                shape = CircleShape,
                modifier = Modifier.size(36.dp).clickable(onClick = onToggleLang)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (lang == "AR") "FR" else "AR",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                }
            }

            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Exit", tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun PurchasesSupplierSelectionRow(
    selectedSupplier: SupplierEntity?,
    cyanPrimary: Color,
    lang: String,
    onOpenSupplierPicker: () -> Unit,
    onOpenAddSupplier: () -> Unit,
    onOpenInvoicesLog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            color = cyanPrimary,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(0.7f).height(46.dp).clickable(onClick = onOpenSupplierPicker)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, contentDescription = "Search Supplier", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        Surface(
            color = cyanPrimary,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1.3f).height(46.dp).clickable(onClick = onOpenSupplierPicker)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = selectedSupplier?.name ?: (if (lang == "AR") "اختيار مورد" else "Choisir Fournisseur"),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }

        Surface(
            color = Color(0xFF10B981),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(46.dp).clickable(onClick = onOpenAddSupplier)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Add Supplier", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        Surface(
            color = Color(0xFF6366F1),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(46.dp).clickable(onClick = onOpenInvoicesLog)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = "Purchase Invoices", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun PurchasesSearchAndScanRow(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    focusRequester: FocusRequester,
    cyanPrimary: Color,
    searchBg: Color,
    cartItemCount: Int,
    lang: String,
    onScanBarcode: () -> Unit,
    onOpenCart: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .focusRequester(focusRequester)
                .testTag("purchases_product_search_input"),
            shape = RoundedCornerShape(24.dp),
            placeholder = {
                Text(
                    if (lang == "AR") "البحث بالاسم أو الباركود" else "Recherche nom ou code-barres",
                    fontSize = 12.5.sp,
                    color = Color(0xFF94A3B8)
                )
            },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    }
                } else {
                    IconButton(
                        onClick = {
                            try {
                                focusRequester.requestFocus()
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG) Log.e("PurchasesScreen", "Keyboard focus request error", e)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Keyboard, contentDescription = "Keyboard", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = searchBg,
                unfocusedContainerColor = searchBg,
                focusedBorderColor = cyanPrimary,
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2563EB))
                .clickable(onClick = onScanBarcode)
                .testTag("purchases_scan_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        GreenCartActionButton(
            itemCount = cartItemCount,
            contentDescription = Bilingual.tr(lang, "فتح سلة المشتريات", "Ouvrir le panier d'achat", "Open purchase cart"),
            onClick = onOpenCart,
            testTag = "purchases_green_cart_button"
        )
    }
}

@Composable
internal fun PurchasesBottomTotalsSection(
    totalAmount: Double,
    lightBlueBox: Color,
    cyanPrimary: Color,
    lang: String,
    currency: String = "MAD",
    onConfirmInvoice: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = lightBlueBox,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF64748B)),
                    modifier = Modifier.weight(0.75f).height(46.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (lang == "AR") "المجموع" else "TOTAL", color = Color(0xFF0F172A), fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }

                Surface(
                    color = lightBlueBox,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF64748B)),
                    modifier = Modifier.weight(1.2f).height(46.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (totalAmount > 0) MoneyUtils.format(totalAmount, currency) else currency,
                            color = Color(0xFF0F172A),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Button(
            onClick = onConfirmInvoice,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("purchases_confirm_invoice_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = cyanPrimary)
        ) {
            Text(
                text = if (lang == "AR") "تأكيد الفاتورة" else "Confirmer la Facture",
                color = Color.White,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun PurchaseLedgerHeaderCell(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .border(BorderStroke(0.5.dp, Color.Black)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Black,
            fontSize = 11.5.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PurchaseLedgerBodyCell(
    text: String,
    modifier: Modifier = Modifier,
    isBold: Boolean = false,
    align: TextAlign = TextAlign.Center
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .border(BorderStroke(0.5.dp, Color.Black))
            .padding(horizontal = 4.dp),
        contentAlignment = when (align) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Text(
            text = text,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp,
            color = Color.Black,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
