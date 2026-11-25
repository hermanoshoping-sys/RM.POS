package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CategoryEntity
import com.example.ui.viewmodels.PosViewModel
import com.example.util.ActiveSort
import com.example.util.SortDirection
import com.example.util.SortingComparators

enum class FamilySortCol { NAME, ID }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFamiliesScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: (String) -> Unit
) {
    BackHandler { onNavigate("DASHBOARD") }

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    var inspectedFamily by remember { mutableStateOf<CategoryEntity?>(null) }
    var showQuickAddProductDialogForFamily by remember { mutableStateOf<CategoryEntity?>(null) }

    val productsByCategory = remember(products) { products.groupBy { it.categoryId } }

    val filteredCategories = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) categories
        else {
            val q = searchQuery.trim().lowercase()
            categories.filter { it.name.lowercase().contains(q) }
        }
    }
    var familySortState by remember { mutableStateOf(ActiveSort(FamilySortCol.NAME, SortDirection.ASCENDING)) }

    val sortedCategories = remember(filteredCategories, familySortState) {
        filteredCategories.sortedWith { a, b ->
            val cmp = when (familySortState.column) {
                FamilySortCol.NAME -> SortingComparators.compareNames(a.name, b.name)
                FamilySortCol.ID -> SortingComparators.compareLongs(a.id, b.id)
            }
            if (familySortState.direction == SortDirection.ASCENDING) cmp else -cmp
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00A3E0))
                            .clickable {
                                editingCategory = null
                                showAddEditDialog = true
                            }
                            .testTag("add_family_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Family", tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF00A3E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lang == "AR") "عائلة المنتجات" else "Familles de Produits",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B))
                            .clickable { onNavigate("DASHBOARD") }
                            .testTag("families_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = Color(0xFFDADEE4),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.Black, fontSize = 14.sp),
                            modifier = Modifier.weight(1f).testTag("family_search_input"),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(if (lang == "AR") "بحث عن عائلة..." else "Rechercher...", color = Color(0xFF6B7280), fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                border = BorderStroke(1.dp, Color.Black)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val minTableWidth = 480.dp
                    val tableWidth = if (maxWidth > minTableWidth) maxWidth else minTableWidth
                    Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        Column(modifier = Modifier.width(tableWidth).fillMaxHeight()) {
                            val familyTitle = if (lang == "AR") "العائلة" else "Famille"
                            val dispFamily = if (familySortState.column == FamilySortCol.NAME) "$familyTitle ${familySortState.direction.symbol}" else familyTitle

                            Row(
                                modifier = Modifier.fillMaxWidth().height(32.dp).background(Color(0xFF00A3E0)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (lang == "AR") "حذف" else "Suppr.", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.11f))
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                Text(if (lang == "AR") "تعديل" else "Éditer", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.11f))
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                Text(if (lang == "AR") "عرض المنتجات" else "Voir Produits", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.21f))
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                Text(if (lang == "AR") "اضافة منتج للعائلة" else "Ajouter Produit", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.22f))
                                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                Text(
                                    text = dispFamily,
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .fillMaxHeight()
                                        .clickable { familySortState = familySortState.toggleOrSwitch(FamilySortCol.NAME) }
                                        .wrapContentHeight(Alignment.CenterVertically)
                                )
                            }
                            HorizontalDivider(color = Color.Black, thickness = 1.dp)

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(sortedCategories, key = { it.id }, contentType = { "family_row" }) { cat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(34.dp).background(Color.White),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(0.11f)
                                                .fillMaxHeight()
                                                .clickable { categoryToDelete = cat }
                                                .testTag("delete_family_button_${cat.id}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = if (lang == "AR") "حذف ${cat.name}" else "Supprimer ${cat.name}",
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))

                                        Box(
                                            modifier = Modifier
                                                .weight(0.11f)
                                                .fillMaxHeight()
                                                .clickable { editingCategory = cat; showAddEditDialog = true }
                                                .testTag("edit_family_button_${cat.id}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(if (lang == "AR") "تعديل" else "Éditer", color = Color(0xFF0284C7), fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                                        }
                                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))

                                        Box(
                                            modifier = Modifier.weight(0.21f).fillMaxHeight().clickable { inspectedFamily = cat },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(if (lang == "AR") "عرض المنتجات" else "Voir", color = Color(0xFF0F766E), fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                                        }
                                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))

                                        Box(
                                            modifier = Modifier.weight(0.22f).fillMaxHeight().clickable { showQuickAddProductDialogForFamily = cat },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(if (lang == "AR") "+ إضافة منتج" else "+ Ajouter", color = Color(0xFF1D4ED8), fontSize = 10.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                                        }
                                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))

                                        Box(
                                            modifier = Modifier.weight(0.35f).fillMaxHeight().padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Text(
                                                text = cat.name,
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Color.Black, thickness = 1.dp)
                                }

                                val fillerRows = (35 - sortedCategories.size).coerceAtLeast(18)
                                items(fillerRows, contentType = { "family_filler_row" }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(28.dp)
                                            .background(Color.White)
                                            .clickable {
                                                editingCategory = null
                                                showAddEditDialog = true
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(0.11f).fillMaxHeight())
                                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                        Box(modifier = Modifier.weight(0.11f).fillMaxHeight())
                                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                        Box(modifier = Modifier.weight(0.21f).fillMaxHeight())
                                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                        Box(modifier = Modifier.weight(0.22f).fillMaxHeight())
                                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                                        Box(modifier = Modifier.weight(0.35f).fillMaxHeight())
                                    }
                                    HorizontalDivider(color = Color.Black, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditFamilyDialog(
            category = editingCategory,
            lang = lang,
            onDismiss = { showAddEditDialog = false },
            onConfirm = { name, imageUri ->
                val targetCat = editingCategory
                if (targetCat == null) {
                    viewModel.addNewCategory(name, imageUri)
                } else {
                    viewModel.updateCategory(targetCat.id, name, imageUri)
                }
                showAddEditDialog = false
            },
            onDelete = editingCategory?.let { targetCat ->
                {
                    showAddEditDialog = false
                    categoryToDelete = targetCat
                }
            }
        )
    }

    categoryToDelete?.let { cat ->
        val count = productsByCategory[cat.id]?.size ?: 0
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            containerColor = Color(0xFF1E293B),
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp)) },
            title = {
                Text(if (lang == "AR") "تأكيد حذف عائلة المنتجات" else "Supprimer la Famille", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (lang == "AR") "هل أنت متأكد من حذف العائلة: \"${cat.name}\"؟" else "Êtes-vous sûr de vouloir supprimer la famille \"${cat.name}\" ?",
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp
                    )
                    if (count > 0) {
                        Surface(color = Color(0x33EF4444), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color(0xFFEF4444))) {
                            Text(
                                text = if (lang == "AR") "⚠️ تنبيه: توجد $count منتجات مرتبطة بهذه العائلة (ستبقى المنتجات محفوظة في المخزون بدون عائلة)." else "⚠️ Attention: $count produits sont associés à cette famille (les produits resteront conservés en stock).",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteCategory(cat); categoryToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.testTag("confirm_delete_family_button")
                ) { Text(if (lang == "AR") "حذف نهائي" else "Supprimer", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { categoryToDelete = null }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))) {
                    Text(if (lang == "AR") "إلغاء" else "Annuler")
                }
            }
        )
    }

    inspectedFamily?.let { cat ->
        FamilyProductsInspectionModal(
            category = cat,
            products = productsByCategory[cat.id].orEmpty(),
            currency = currency,
            lang = lang,
            onDismiss = { inspectedFamily = null },
            onAddProduct = { showQuickAddProductDialogForFamily = cat },
            onDeleteProduct = { product -> viewModel.deleteProduct(product) }
        )
    }

    showQuickAddProductDialogForFamily?.let { cat ->
        QuickAddProductToFamilyDialog(
            category = cat,
            lang = lang,
            onDismiss = { showQuickAddProductDialogForFamily = null },
            onConfirm = { name, barcode, purchasePrice, sellPrice, stock, minStock, unit, familyName ->
                viewModel.saveProduct(
                    barcode = barcode,
                    name = name,
                    description = "",
                    categoryId = cat.id,
                    categoryName = familyName.ifBlank { cat.name },
                    unit = unit,
                    purchasePrice = purchasePrice,
                    sellPrice = sellPrice,
                    wholesalePrice = sellPrice,
                    specialPrice = sellPrice,
                    currentStock = stock,
                    minStock = minStock
                )
                showQuickAddProductDialogForFamily = null
            }
        )
    }
}
