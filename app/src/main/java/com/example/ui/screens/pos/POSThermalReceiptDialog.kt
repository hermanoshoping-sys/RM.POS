package com.example.ui.screens.pos

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.ui.viewmodels.PosViewModel
import com.example.util.DocumentDesignHelper
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ThermalReceiptDialog(
    viewModel: PosViewModel,
    sale: SaleEntity,
    items: List<SaleItemEntity>,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val storeSettings by viewModel.settings.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val companyLogoUri by viewModel.companyLogoUri.collectAsStateWithLifecycle()
    val headerImageUri by viewModel.headerImageUri.collectAsStateWithLifecycle()
    val footerImageUri by viewModel.footerImageUri.collectAsStateWithLifecycle()
    val btState by viewModel.btConnectionState.collectAsStateWithLifecycle()
    var showBtPicker by remember { mutableStateOf(false) }

    val receiptLogoBitmap = remember(companyLogoUri, storeSettings.logoQuality) {
        DocumentDesignHelper.decodeUiBitmap(context, companyLogoUri, DocumentDesignHelper.LOGO_FILE_NAME)?.asImageBitmap()
    }
    val headerImageBitmap = remember(headerImageUri, storeSettings.headerContentType) {
        if (DocumentDesignHelper.headerShowsImage(storeSettings)) {
            DocumentDesignHelper.decodeUiBitmap(context, headerImageUri, DocumentDesignHelper.HEADER_IMAGE_FILE_NAME)?.asImageBitmap()
        } else null
    }
    val footerImageBitmap = remember(footerImageUri, storeSettings.footerContentType, storeSettings.showReceiptFooter) {
        if (DocumentDesignHelper.footerShowsImage(storeSettings)) {
            DocumentDesignHelper.decodeUiBitmap(context, footerImageUri, DocumentDesignHelper.FOOTER_IMAGE_FILE_NAME)?.asImageBitmap()
        } else null
    }

    val fontScale = DocumentDesignHelper.fontScale(storeSettings)
    fun scaledSp(base: Float) = (base * fontScale).coerceIn(7.5f, 22f).sp

    val dialogMaxImgW = 310f
    val logoDrawW = (storeSettings.logoWidth.coerceIn(40, 320) * 0.85f).coerceAtMost(dialogMaxImgW)
    val logoDrawH = (storeSettings.logoHeight.coerceIn(30, 220) * 0.85f).coerceAtMost(170f)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .width(360.dp)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Custom Header Image (if enabled)
                if (headerImageBitmap != null) {
                    val hImgW = (storeSettings.headerWidth.coerceIn(120, 538) * 0.6f).coerceAtMost(dialogMaxImgW)
                    val hImgH = (storeSettings.headerHeight.coerceIn(24, 220) * 0.7f).coerceAtMost(140f)
                    Image(
                        bitmap = headerImageBitmap,
                        contentDescription = "Document Header Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(hImgW.dp)
                            .height(hImgH.dp)
                            .padding(bottom = 4.dp)
                    )
                }

                // Company Logo (honoring user logoWidth & logoHeight)
                if (storeSettings.showLogo && receiptLogoBitmap != null) {
                    Image(
                        bitmap = receiptLogoBitmap,
                        contentDescription = "Company Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(logoDrawW.dp)
                            .height(logoDrawH.dp)
                            .padding(bottom = 6.dp)
                    )
                }

                // Custom Header Text (below logo)
                if (DocumentDesignHelper.headerShowsText(storeSettings)) {
                    Text(
                        text = storeSettings.receiptHeader,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (storeSettings.headerFontSize.coerceIn(9, 26) * 0.85f).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Shop Name
                if (storeSettings.showShopName && storeSettings.shopName.isNotBlank()) {
                    Text(
                        text = storeSettings.shopName,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = scaledSp(16f),
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center
                    )
                }

                // Address & Phone
                val contactInfo = mutableListOf<String>()
                if (storeSettings.showPhone && storeSettings.shopPhone.isNotBlank()) {
                    contactInfo.add("الهاتف: ${storeSettings.shopPhone}")
                }
                if (storeSettings.showAddress && storeSettings.shopAddress.isNotBlank()) {
                    contactInfo.add(storeSettings.shopAddress)
                }
                if (contactInfo.isNotEmpty()) {
                    Text(
                        text = contactInfo.joinToString(" • "),
                        color = Color.DarkGray,
                        fontSize = scaledSp(9f),
                        textAlign = TextAlign.Center
                    )
                }

                // Tax Number
                if (storeSettings.showTaxNumber && storeSettings.taxNumber.isNotBlank()) {
                    Text(
                        text = "الرقم الضريبي: ${storeSettings.taxNumber}",
                        color = Color.DarkGray,
                        fontSize = scaledSp(9f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Divider below Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF1E293B))
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Lock paper geometry to explicit visual coordinates (matches PDF/A4 template even in RTL)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(modifier = Modifier.fillMaxWidth()) {
                // Right-aligned "الفاتورة" heading
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "الفاتورة",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = scaledSp(17f),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 4-Row Split Metadata Box (فاتورة رقم / الزبون / البائع / التاريخ)
                val dateStr = if (storeSettings.showDateTime) {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(sale.timestamp))
                } else ""
                val clientName = if (storeSettings.showCustomerInfo) {
                    if (!sale.customerName.isNullOrBlank()) sale.customerName else "زبون عادي"
                } else ""
                val cashierDisplay = if (storeSettings.showCashierName) {
                    sale.cashierName.ifBlank { viewModel.currentUser.value?.username.orEmpty() }
                } else ""

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color.Black)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(sale.invoiceNumber, fontSize = scaledSp(10.5f), fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                        HorizontalDivider(color = Color.Black, thickness = 1.dp)
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(clientName, fontSize = scaledSp(10f), color = Color.Black)
                        }
                        if (cashierDisplay.isNotBlank()) {
                            HorizontalDivider(color = Color.Black, thickness = 1.dp)
                            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.CenterEnd) {
                                Text(cashierDisplay, fontSize = scaledSp(10f), color = Color.Black)
                            }
                        }
                        HorizontalDivider(color = Color.Black, thickness = 1.dp)
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(dateStr, fontSize = scaledSp(10f), color = Color.Black)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .width(82.dp)
                            .border(1.dp, Color.Black)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.CenterEnd) {
                            Text("فاتورة رقم :", fontSize = scaledSp(10f), fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                        HorizontalDivider(color = Color.Black, thickness = 1.dp)
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.CenterEnd) {
                            Text("الزبون :", fontSize = scaledSp(10f), fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                        if (cashierDisplay.isNotBlank()) {
                            HorizontalDivider(color = Color.Black, thickness = 1.dp)
                            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.CenterEnd) {
                                Text("البائع :", fontSize = scaledSp(10f), fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                        HorizontalDivider(color = Color.Black, thickness = 1.dp)
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.CenterEnd) {
                            Text("التاريخ :", fontSize = scaledSp(10f), fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Items Table (المجموع | ثمن | وحدة | كمية | المنتج)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Black)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 24.dp)
                            .background(Color(0xFFCFD4DA)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("المجموع", fontSize = scaledSp(9.5f), fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(0.15f))
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Black))
                        Text("ثمن", fontSize = scaledSp(9.5f), fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(0.12f))
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Black))
                        Text("وحدة", fontSize = scaledSp(9.5f), fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(0.12f))
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Black))
                        Text("كمية", fontSize = scaledSp(9.5f), fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(0.13f))
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.Black))
                        Text("المنتج", fontSize = scaledSp(9.5f), fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(0.48f))
                    }

                    val minDisplayRows = maxOf(items.size, 12)
                    for (idx in 0 until minDisplayRows) {
                        HorizontalDivider(color = Color.Black, thickness = 1.dp)
                        val item = if (idx < items.size) items[idx] else null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 22.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item?.let { String.format(Locale.US, "%.2f", it.totalLinePriceDouble) } ?: "",
                                fontSize = scaledSp(9f),
                                color = Color.Black,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(0.15f)
                            )
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(Color.Black))
                            Text(
                                text = item?.let { String.format(Locale.US, "%.2f", it.unitPriceDouble) } ?: "",
                                fontSize = scaledSp(9f),
                                color = Color.Black,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(0.12f)
                            )
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(Color.Black))
                            Text(
                                text = if (item != null) "قطعة" else "",
                                fontSize = scaledSp(9f),
                                color = Color.Black,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(0.12f)
                            )
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(Color.Black))
                            val qStr = item?.let { if (it.quantity % 1.0 == 0.0) String.format(Locale.US, "%.0f", it.quantity) else String.format(Locale.US, "%.2f", it.quantity) } ?: ""
                            Text(
                                text = qStr,
                                fontSize = scaledSp(9f),
                                color = Color.Black,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(0.13f)
                            )
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(Color.Black))
                            Text(
                                text = item?.productName ?: "",
                                fontSize = scaledSp(8.5f),
                                lineHeight = scaledSp(11f),
                                color = Color.Black,
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(0.48f)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Left Total Box (~62% width, light blue-gray fill matching template)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.64f)
                            .heightIn(min = 26.dp)
                            .background(Color(0xFFD6DCE5))
                            .border(1.dp, Color.Black),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = MoneyUtils.format(sale.totalAmount, currency),
                            fontSize = scaledSp(10f),
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.58f)
                        )
                        Box(modifier = Modifier.width(1.dp).height(26.dp).background(Color.Black))
                        Text(
                            text = "المجموع",
                            fontSize = scaledSp(10f),
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.42f)
                        )
                    }
                }
                }
                }

                // Optional paid/change details (remaining debt omitted from printed invoices)
                val hasPartialOrChange = sale.paymentMethod.uppercase() == "CREDIT" || sale.remainingDueCents > 0L || sale.changeAmount > 0L
                if (hasPartialOrChange) {
                    val footerDetails = mutableListOf<String>()
                    if (sale.paidAmount > 0L) footerDetails.add("المدفوع: ${MoneyUtils.format(sale.paidAmount, currency)}")
                    if (sale.changeAmount > 0L) footerDetails.add("الباقي: ${MoneyUtils.format(sale.changeAmount, currency)}")
                    if (footerDetails.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = footerDetails.joinToString(" • "),
                            color = Color.Black,
                            fontSize = scaledSp(9.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Divider above Footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color(0xFF1E293B))
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Footer Text & Image
                if (DocumentDesignHelper.footerShowsText(storeSettings)) {
                    val footerText = if (storeSettings.receiptFooter.isNotBlank()) storeSettings.receiptFooter else "شكراً لزيارتكم - يرجى الاحتفاظ بالفاتورة"
                    Text(
                        text = footerText,
                        color = Color.Black,
                        fontSize = scaledSp(10f),
                        textAlign = TextAlign.Center
                    )
                }
                if (footerImageBitmap != null) {
                    val fImgW = (storeSettings.footerWidth.coerceIn(120, 538) * 0.6f).coerceAtMost(dialogMaxImgW)
                    val fImgH = (storeSettings.footerHeight.coerceIn(24, 220) * 0.7f).coerceAtMost(140f)
                    Image(
                        bitmap = footerImageBitmap,
                        contentDescription = "Document Footer Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(fImgW.dp)
                            .height(fImgH.dp)
                    )
                }

                if (storeSettings.showBarcode) {
                    Text(
                        text = "* ${sale.invoiceNumber} *",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = scaledSp(11f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ThermalReceiptActionsSection(
                    viewModel = viewModel,
                    sale = sale,
                    items = items,
                    storeSettings = storeSettings,
                    btState = btState,
                    lang = lang,
                    onOpenBtPicker = { showBtPicker = true },
                    onDismiss = onDismiss
                )
            }
        }
    }

    if (showBtPicker) {
        CheckoutBluetoothPrinterPickerDialog(
            viewModel = viewModel,
            lang = lang,
            onDismiss = { showBtPicker = false },
            onConnectedAndReady = { showBtPicker = false }
        )
    }
}
