package com.example.ui.screens.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import java.util.Locale

@Composable
internal fun CatalogTopHeaderBar(
    selectedCategoryId: Long?,
    catsList: List<CategoryEntity>,
    productsList: List<ProductEntity>,
    cartTotalCount: Double,
    hasCartItems: Boolean,
    lang: String,
    onBackToCategories: () -> Unit,
    onOpenCartPreview: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = Color(0xFF1E293B),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (selectedCategoryId != null) {
                    IconButton(
                        onClick = onBackToCategories,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF334155), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Surface(
                        color = Color(0xFFD97706).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.5f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                val currentCatName = remember(selectedCategoryId, catsList, lang) {
                    if (selectedCategoryId == null) {
                        if (lang == "AR") "📖 كاتالوج المنتجات ونقطة البيع" else "📖 Catalogue & POS"
                    } else if (selectedCategoryId == -1L) {
                        if (lang == "AR") "جميع المنتجات" else "Tous les produits"
                    } else {
                        catsList.find { it.id == selectedCategoryId }?.name
                            ?: (if (lang == "AR") "عائلة منتجات" else "Famille")
                    }
                }

                val catProductCount = remember(productsList, selectedCategoryId) {
                    if (selectedCategoryId == null || selectedCategoryId == -1L) {
                        productsList.size
                    } else {
                        productsList.count { it.categoryId == selectedCategoryId }
                    }
                }

                Column {
                    Text(
                        text = if (selectedCategoryId == null) currentCatName else "$currentCatName ($catProductCount)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (selectedCategoryId == null) {
                            if (lang == "AR") "اختر عائلة أو ابحث لتمرير المنتجات فوراً للسلة" else "Sélectionnez une catégorie pour vendre"
                        } else {
                            if (lang == "AR") "مرر المنتجات مباشرة لنقطة البيع واستخرج الفاتورة" else "Ajoutez au panier et facturez directement"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenCartPreview,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasCartItems) Color(0xFF0284C7) else Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("catalog_header_cart_button")
                ) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = String.format(Locale.US, "%.0f", cartTotalCount),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape)
                        .testTag("catalog_close_button")
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CatalogSearchBarRow(
    searchQuery: String,
    selectedCategoryId: Long?,
    lang: String,
    onSearchQueryChange: (String) -> Unit,
    onResetFilters: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    if (lang == "AR") "بحث في المنتجات (الاسم، الباركود...)" else "Rechercher produit...",
                    color = Color.Gray,
                    fontSize = 12.5.sp
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("catalog_search_input")
        )

        if (selectedCategoryId != null || searchQuery.isNotEmpty()) {
            Button(
                onClick = onResetFilters,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (lang == "AR") "العائلات" else "Familles", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
