package com.example.ui.screens.catalog

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import com.example.ui.viewmodels.PosViewModel

private val FamilyCardTopGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF62A4E0), Color(0xFF4F8EC9))
)
private val FamilyCardFooterColor = Color(0xFF4D8CC7)
private val FamilyGoldenDividerColor = Color(0xFFF5A623)

/**
 * Level 1: Product Families Grid ("عائلات المنتجات")
 * Large rounded 2-column cards with top photo area, golden accent divider bar,
 * and centered family title below.
 */
@Composable
internal fun CatalogCategoriesGrid(
    catsList: List<CategoryEntity>,
    productsList: List<ProductEntity>,
    viewModel: PosViewModel,
    lang: String,
    onSelectCategory: (Long) -> Unit,
    onSelectAllProducts: () -> Unit
) {
    val context = LocalContext.current
    val isAr = lang == "AR"
    var targetCategoryForImage by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryForOptionsDialog by remember { mutableStateOf<CategoryEntity?>(null) }

    val productsByCategory = remember(productsList) {
        productsList.groupBy { it.categoryId }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val targetCat = targetCategoryForImage
        if (uri != null && targetCat != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Ignore if picker returns a temporary URI
            }
            viewModel.updateCategoryImage(targetCat.id, uri.toString())
            Toast.makeText(
                context,
                if (isAr) "تم تعيين صورة العائلة بنجاح ✅" else "Photo de famille enregistrée ✅",
                Toast.LENGTH_SHORT
            ).show()
        }
        targetCategoryForImage = null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060A13))
    ) {
        val gridColumns = if (maxWidth < 600.dp) GridCells.Fixed(2) else GridCells.Adaptive(minSize = 185.dp)

        LazyVerticalGrid(
            columns = gridColumns,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Centered Section Title: "عائلات المنتجات"
            item(
                key = "catalog_families_section_title",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "catalog_section_header"
            ) {
                Text(
                    text = if (isAr) "عائلات المنتجات" else if (lang == "FR") "Familles de produits" else "Product Families",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                        .testTag("catalog_families_header")
                )
            }

            // First Card: All Products ("جميع المنتجات")
            item(key = "all_products_card", contentType = "catalog_all_card") {
                val representativeImage = remember(productsList) {
                    productsList.firstOrNull { !it.imageUri.isNullOrBlank() }?.imageList?.firstOrNull()
                }
                FamilyVisualCard(
                    title = if (isAr) "جميع المنتجات" else if (lang == "FR") "Tous les produits" else "All Products",
                    imageUri = representativeImage,
                    productCount = productsList.size,
                    isAllProductsCard = true,
                    onCardClick = onSelectAllProducts,
                    onPhotoActionClick = null,
                    modifier = Modifier.testTag("catalog_all_products_card")
                )
            }

            // Product Family Cards
            items(catsList, key = { it.id }, contentType = { "catalog_category_card" }) { cat ->
                val catProducts = productsByCategory[cat.id].orEmpty()
                val fallbackProductImg = remember(cat.imageUri, catProducts) {
                    cat.imageUri?.takeIf { it.isNotBlank() }
                        ?: catProducts.firstOrNull { !it.imageUri.isNullOrBlank() }?.imageList?.firstOrNull()
                }

                FamilyVisualCard(
                    title = cat.name,
                    imageUri = fallbackProductImg,
                    productCount = catProducts.size,
                    isAllProductsCard = false,
                    hasExplicitFamilyPhoto = !cat.imageUri.isNullOrBlank(),
                    onCardClick = { onSelectCategory(cat.id) },
                    onPhotoActionClick = {
                        if (cat.imageUri.isNullOrBlank()) {
                            targetCategoryForImage = cat
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            categoryForOptionsDialog = cat
                        }
                    },
                    modifier = Modifier.testTag("catalog_category_card_${cat.id}")
                )
            }
        }
    }

    categoryForOptionsDialog?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryForOptionsDialog = null },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(
                    text = "${if (isAr) "صورة العائلة للكاتالوج: " else "Photo de famille : "} ${cat.name}",
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
                    if (!cat.imageUri.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .width(130.dp)
                                .aspectRatio(0.85f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, FamilyGoldenDividerColor)
                        ) {
                            AsyncImage(
                                model = cat.imageUri,
                                contentDescription = cat.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Text(
                        text = if (isAr) "تظهر هذه الصورة في بطاقة العائلة داخل الكاتالوج" else "Cette photo apparaît sur la carte de famille du catalogue",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        targetCategoryForImage = cat
                        categoryForOptionsDialog = null
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isAr) "تغيير الصورة" else "Changer la photo")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.updateCategoryImage(cat.id, null)
                            categoryForOptionsDialog = null
                            Toast.makeText(
                                context,
                                if (isAr) "تمت إزالة صورة العائلة" else "Photo supprimée",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text(if (isAr) "إزالة الصورة" else "Supprimer", color = Color(0xFFEF4444))
                    }
                    TextButton(onClick = { categoryForOptionsDialog = null }) {
                        Text(if (isAr) "إلغاء" else "Annuler", color = Color.Gray)
                    }
                }
            }
        )
    }
}

@Composable
private fun FamilyVisualCard(
    title: String,
    imageUri: String?,
    productCount: Int,
    isAllProductsCard: Boolean,
    hasExplicitFamilyPhoto: Boolean = false,
    onCardClick: () -> Unit,
    onPhotoActionClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = FamilyCardFooterColor,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.84f)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FamilyCardTopGradient)
        ) {
            // Top Photo Area (~75% of card)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUri.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Soft bottom scrim so golden bar pops cleanly over any photo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f))
                                )
                            )
                    )
                } else {
                    Icon(
                        imageVector = if (isAllProductsCard) Icons.Filled.Apps else Icons.Filled.Category,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(52.dp)
                    )
                }

                // Compact Product Count Badge (Top-End)
                Surface(
                    color = Color.Black.copy(alpha = 0.42f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "$productCount",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Subtle Camera Button to set/change family image (Top-Start)
                if (onPhotoActionClick != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.38f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .size(34.dp)
                            .clickable(onClick = onPhotoActionClick)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Edit Family Image",
                                tint = if (hasExplicitFamilyPhoto) Color(0xFF34D399) else Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // Golden-Amber Accent Divider Line (matches mockup separator)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FamilyCardFooterColor)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(FamilyGoldenDividerColor)
                )
            }

            // Bottom Family Name Label Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FamilyCardFooterColor)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
