package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.ProductEntity
import java.io.File

@Composable
fun ExcelIconBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(38.dp, 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF107C41))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = "X",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(modifier = Modifier.width(3.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                repeat(3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                        Box(modifier = Modifier.size(4.dp).background(Color.White))
                        Box(modifier = Modifier.size(4.dp).background(Color.White))
                    }
                }
            }
        }
    }
}

@Composable
fun BarcodeScannerSearchBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(46.dp, 34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.fillMaxHeight().width(2.5.dp).background(Color(0xFF0284C7)))
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF0284C7)))
            Box(modifier = Modifier.fillMaxHeight().width(3.dp).background(Color(0xFF0284C7)))
            Box(modifier = Modifier.fillMaxHeight().width(1.5.dp).background(Color(0xFF0284C7)))
            Box(modifier = Modifier.fillMaxHeight().width(2.dp).background(Color(0xFF0284C7)))
            Box(modifier = Modifier.fillMaxHeight().width(3.5.dp).background(Color(0xFF0284C7)))
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF0284C7)))
        }
    }
}

enum class ProductSortCol {
    BARCODE,
    NAME,
    PRICE,
    STOCK,
    ID
}

enum class ProductSortOption(val titleAr: String, val titleFr: String) {
    ID_ASC("الرقم (تصاعدي)", "Numéro (Croissant)"),
    ID_DESC("الرقم (تنازلي)", "Numéro (Décroissant)"),
    NAME_ASC("الاسم (أ - ي)", "Nom (A - Z)"),
    NAME_DESC("الاسم (ي - أ)", "Nom (Z - A)"),
    PRICE_ASC("الثمن (الأقل)", "Prix (Croissant)"),
    PRICE_DESC("الثمن (الأعلى)", "Prix (Décroissant)"),
    BARCODE_ASC("الكود بار", "Code-barres"),
    STOCK_ASC("المخزون (الأقل)", "Stock (Faible)")
}

@Composable
fun InventoryLedgerTableRow(
    colStock: String,
    colPrice: String,
    colProduct: String,
    colBarcode: String,
    isHeader: Boolean = false,
    isNegativeStock: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onStockClick: (() -> Unit)? = null,
    onPriceClick: (() -> Unit)? = null,
    onProductClick: (() -> Unit)? = null,
    onBarcodeClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val rowHeight = if (isHeader) 34.dp else 30.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (isHeader) Color(0xFFF9CF58) else if (isMultiSelectMode && isSelected) Color(0xFFEFF6FF) else Color.White)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
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
        if (isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight()
                    .background(if (isHeader) Color(0xFFF9CF58) else if (isSelected) Color(0xFFDBEAFE) else Color.White)
                    .then(if (onToggleSelect != null) Modifier.clickable { onToggleSelect() } else Modifier)
                    .drawBehind {
                        drawLine(
                            color = Color.Black,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = if (isHeader) "Select All" else "Select",
                    tint = if (isSelected) Color(0xFF2563EB) else if (isHeader) Color(0xFF1E293B) else Color(0xFF94A3B8),
                    modifier = Modifier.size(if (isHeader) 18.dp else 17.dp)
                )
            }
        }

        val stockBoxMod = if (isHeader && onStockClick != null) Modifier.clickable(onClick = onStockClick) else Modifier
        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .background(if (!isHeader && isNegativeStock) Color(0xFFFFE4E6) else Color.Transparent)
                .then(stockBoxMod)
                .drawBehind {
                    drawLine(
                        color = Color.Black,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colStock,
                color = if (!isHeader && isNegativeStock) Color(0xFFDC2626) else Color.Black,
                fontWeight = if (isHeader || isNegativeStock) FontWeight.Bold else FontWeight.Medium,
                fontSize = if (isHeader) 13.sp else 12.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        val priceBoxMod = if (isHeader && onPriceClick != null) Modifier.clickable(onClick = onPriceClick) else Modifier
        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .then(priceBoxMod)
                .drawBehind {
                    drawLine(
                        color = Color.Black,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colPrice,
                color = Color.Black,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium,
                fontSize = if (isHeader) 13.sp else 12.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }

        val productBoxMod = if (isHeader && onProductClick != null) Modifier.clickable(onClick = onProductClick) else Modifier
        Box(
            modifier = Modifier
                .weight(2.4f)
                .fillMaxHeight()
                .then(productBoxMod)
                .drawBehind {
                    drawLine(
                        color = Color.Black,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colProduct,
                color = Color.Black,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isHeader) 13.sp else 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        val barcodeBoxMod = if (isHeader && onBarcodeClick != null) Modifier.clickable(onClick = onBarcodeClick) else Modifier
        Box(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .then(barcodeBoxMod)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = colBarcode,
                color = Color.Black,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isHeader) 13.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun exportProductsToCsv(context: Context, products: List<ProductEntity>, lang: String) {
    try {
        val file = File(context.cacheDir, "products_inventory_${System.currentTimeMillis()}.csv")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            val header = if (lang == "AR") "كود بار,المنتج,ثمن البيع,سعر الشراء,المخزون,الوحدة\n" else "Code-barres,Produit,Prix Vente,Prix Achat,Stock,Unité\n"
            writer.write(header)
            products.forEach { p ->
                val line = "\"${p.barcode}\",\"${p.name.replace("\"", "\"\"")}\",${p.sellPriceDouble},${p.purchasePriceDouble},${p.currentStock},\"${p.unit}\"\n"
                writer.write(line)
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, if (lang == "AR") "قائمة المنتجات والمخزون" else "Inventaire des produits")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, if (lang == "AR") "مشاركة ملف الإكسل" else "Partager Excel/CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
