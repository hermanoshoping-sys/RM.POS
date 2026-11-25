package com.example.ui.screens.catalog

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.ProductEntity
import com.example.ui.screens.pos.POSCartLedgerRow
import com.example.ui.viewmodels.CartItem
import com.example.ui.viewmodels.PosViewModel
import java.util.Locale

/**
 * 4-Column Bottom Summary Table:
 * Bordered Card: [المجموع | عنصر | السلة | استخراج الفاتورة]
 */
@Composable
internal fun CatalogBottomStructuredTable(
    cartTotalCount: Double,
    cartTotalPrice: Double,
    cartLinesCount: Int,
    lang: String,
    onOpenCart: () -> Unit,
    onExtractInvoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(2.dp, Color(0xFF70C038)),
            shadowElevation = 12.dp
        ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .background(Color(0xFFF1F5F9))
                            .drawBehind {
                                drawLine(
                                    color = Color.Black,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight()
                                .drawBehind {
                                    drawLine(color = Color.Black, start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "AR") "استخراج الفاتورة" else "Facturation",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.0f)
                                .fillMaxHeight()
                                .drawBehind {
                                    drawLine(color = Color.Black, start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "AR") "السلة" else "Panier",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                                .drawBehind {
                                    drawLine(color = Color.Black, start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "AR") "عنصر" else "Article",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lang == "AR") "المجموع" else "Total",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color.White),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight()
                                .background(Color(0xFFE8F5E9))
                                .clickable { onExtractInvoice() }
                                .testTag("catalog_extract_invoice_button")
                                .drawBehind {
                                    drawLine(color = Color.Black, start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (lang == "AR") "فاتورة ⚡" else "Facture ⚡",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.0f)
                                .fillMaxHeight()
                                .background(Color(0xFFE0F2FE))
                                .clickable { onOpenCart() }
                                .testTag("catalog_open_cart_button")
                                .drawBehind {
                                    drawLine(color = Color.Black, start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "($cartLinesCount)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF0369A1)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                                .drawBehind {
                                    drawLine(color = Color.Black, start = Offset(size.width, 0f), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.0f", cartTotalCount),
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color.Black,
                                textAlign = TextAlign.Center
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.2f", cartTotalPrice),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.5.sp,
                                color = Color(0xFFB45309),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }



/**
 * Modal to manage Product 1080x1350 Catalog Images (View, Change, or Clear)
 */
@Composable
internal fun ProductImageOptionsDialog(
    product: ProductEntity,
    lang: String,
    onDismiss: () -> Unit,
    onChangePhotos: () -> Unit,
    onClearPhotos: () -> Unit
) {
    val images = remember(product.imageUri) { product.imageList }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = "${if (lang == "AR") "صور المنتج للكاتالوج: " else "Photos du produit : "} ${product.name}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (images.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        images.forEachIndexed { idx, uriStr ->
                            Surface(
                                modifier = Modifier
                                    .width(96.dp)
                                    .aspectRatio(1080f / 1350f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8))
                            ) {
                                Box {
                                    AsyncImage(
                                        model = uriStr,
                                        contentDescription = product.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    text = if (lang == "AR")
                        "المقاس: 1080 × 1350 (نسبة 4:5 مع إمكانية التمرير مباشرة في الكاتالوج)"
                    else
                        "Format : 1080 x 1350 (ratio 4:5 avec défilement direct dans le catalogue)",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onChangePhotos,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (lang == "AR") "إضافة / تغيير الصور (1080x1350)" else "Modifier les photos (1080x1350)")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (images.isNotEmpty()) {
                    TextButton(onClick = onClearPhotos) {
                        Text(if (lang == "AR") "إزالة الصور" else "Supprimer", color = Color(0xFFEF4444))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(if (lang == "AR") "إلغاء" else "Annuler", color = Color.Gray)
                }
            }
        }
    )
}
