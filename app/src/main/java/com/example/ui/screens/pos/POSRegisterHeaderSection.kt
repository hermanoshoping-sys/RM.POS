package com.example.ui.screens.pos

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CategoryEntity
import com.example.data.CustomerEntity
import com.example.ui.theme.Bilingual
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner

@Composable
fun POSRegisterTopHeader(
    onOpenCatalog: () -> Unit,
    onOpenInvoices: () -> Unit,
    onBackDashboard: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(0xFF1E3A8A).copy(alpha = 0.6f), CircleShape)
                    .border(1.dp, Color(0xFF3B82F6), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.pos_new_sale_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.pos_new_sale_subtitle),
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onOpenCatalog,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.pos_tab_catalog), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onOpenInvoices,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.pos_tab_invoices), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .clickable(onClick = onBackDashboard),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun POSRegisterCustomerAndFilterSection(
    selectedCustomer: CustomerEntity?,
    searchQuery: String,
    catsList: List<CategoryEntity>,
    selectedCatId: Long?,
    cartItemCount: Int,
    scanner: GmsBarcodeScanner?,
    lang: String,
    onChangeCustomerClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onOpenManualBarcode: () -> Unit,
    onOpenCart: () -> Unit,
    onSelectCategory: (Long?) -> Unit,
    onOpenFamilies: () -> Unit
) {
    val context = LocalContext.current
    val guestClientName = stringResource(R.string.pos_guest_client)
    val currentCustName = selectedCustomer?.name ?: guestClientName
    val isGeoRestricted = selectedCustomer?.isGeoLocationRequired == true && selectedCustomer.hasGeoLocation

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                .border(1.dp, if (isGeoRestricted) Color(0xFF0D9488) else Color(0xFF1E293B), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (isGeoRestricted) Color(0xFF14B8A6) else Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${stringResource(R.string.pos_client_label)} $currentCustName",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isGeoRestricted) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFF0D9488).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF0D9488))
                    ) {
                        Text(
                            text = "📍 ${(selectedCustomer?.geoRadiusMeters ?: 10.0).toInt()}m",
                            color = Color(0xFF2DD4BF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onChangeCustomerClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(stringResource(R.string.pos_change_client), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        stringResource(R.string.pos_search_placeholder),
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                    } else {
                        IconButton(onClick = onOpenManualBarcode) {
                            Icon(Icons.Filled.Keyboard, contentDescription = "Manual Barcode", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1E293B),
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2563EB))
                    .clickable {
                        val activeScanner = scanner
                        if (activeScanner == null) {
                            Toast.makeText(
                                context,
                                Bilingual.tr(lang, "الماسح غير متاح على هذا الجهاز", "Scanner indisponible", "Scanner unavailable"),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            activeScanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    val rawValue = barcode.rawValue
                                    if (!rawValue.isNullOrBlank()) {
                                        onBarcodeScanned(rawValue)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        context,
                                        Bilingual.tr(lang, "فشل مسح الباركود: ${e.localizedMessage}", "Échec scan: ${e.localizedMessage}", "Scan failed: ${e.localizedMessage}"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan Barcode", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            GreenCartActionButton(
                itemCount = cartItemCount,
                contentDescription = Bilingual.tr(lang, "فتح سلة المبيعات", "Ouvrir le panier", "Open sales cart"),
                onClick = onOpenCart,
                testTag = "pos_green_cart_button"
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "all_cats") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedCatId == null) Color(0xFF2563EB) else Color(0xFF0F172A),
                    border = BorderStroke(1.dp, if (selectedCatId == null) Color(0xFF2563EB) else Color(0xFF1E293B)),
                    modifier = Modifier.clickable { onSelectCategory(null) }
                ) {
                    Text(
                        text = Bilingual.tr(lang, "الكل", "Tous", "All"),
                        color = Color.White,
                        fontWeight = if (selectedCatId == null) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
            items(catsList, key = { it.id }) { cat ->
                val isSelected = selectedCatId == cat.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B)),
                    modifier = Modifier.clickable { onSelectCategory(cat.id) }
                ) {
                    Text(
                        text = cat.name,
                        color = Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
            item(key = "add_families") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF581C87),
                    border = BorderStroke(1.dp, Color(0xFFA855F7)),
                    modifier = Modifier.clickable(onClick = onOpenFamilies)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.Category, contentDescription = null, tint = Color(0xFFD8B4FE), modifier = Modifier.size(14.dp))
                        Text(
                            text = Bilingual.tr(lang, "+ عائلات المنتجات", "+ Familles", "+ Families"),
                            color = Color(0xFFF3E8FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}
