package com.example.ui.screens.stocktaking

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import com.example.data.SupplierEntity
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.InventoryType
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.domain.stocktaking.SupplierValuationRow
import com.example.ui.theme.Bilingual
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
internal fun CreateStocktakingScreen(
    initialType: InventoryType,
    products: List<ProductEntity>,
    categories: List<CategoryEntity>,
    suppliers: List<SupplierEntity>,
    supplierValuations: List<SupplierValuationRow>,
    isBusy: Boolean,
    lang: String,
    onBack: () -> Unit,
    onCreateSession: (
        type: InventoryType,
        title: String,
        notes: String,
        categoryIds: List<Long>,
        productIds: List<Long>,
        supplierIds: List<Long>,
        duplicateScanMode: DuplicateScanMode,
        startImmediately: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val scanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    var selectedType by remember(initialType) { mutableStateOf(initialType) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var duplicateScanMode by remember { mutableStateOf(DuplicateScanMode.INCREMENT) }
    var startImmediately by remember { mutableStateOf(true) }

    var selectedCategoryIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedProductIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedSupplierIds by remember { mutableStateOf(setOf<Long>()) }
    var scopeSearch by remember { mutableStateOf("") }

    val activeProducts = remember(products) { products.filter { it.isActive } }
    val productCountByCategory = remember(activeProducts) {
        activeProducts.groupingBy { it.categoryId }.eachCount()
    }
    val productCountBySupplier = remember(supplierValuations) {
        supplierValuations.associate { it.supplierId to it.productCount }
    }

    val estimatedSelectedCount = remember(
        selectedType,
        activeProducts,
        selectedCategoryIds,
        selectedProductIds,
        selectedSupplierIds,
        productCountBySupplier
    ) {
        when (selectedType) {
            InventoryType.GENERAL -> activeProducts.size
            InventoryType.PARTIAL_CATEGORY -> activeProducts.count { it.categoryId in selectedCategoryIds }
            InventoryType.PARTIAL_PRODUCTS -> selectedProductIds.size
            InventoryType.PARTIAL_SUPPLIER -> selectedSupplierIds.sumOf { productCountBySupplier[it] ?: 0 }
        }
    }

    val filteredCategories = remember(categories, scopeSearch) {
        val q = scopeSearch.trim().lowercase()
        if (q.isEmpty()) categories else categories.filter { it.name.lowercase().contains(q) }
    }
    val filteredProducts = remember(activeProducts, scopeSearch) {
        val q = scopeSearch.trim().lowercase()
        if (q.isEmpty()) activeProducts else activeProducts.filter {
            it.name.lowercase().contains(q) ||
                it.barcode.lowercase().contains(q) ||
                it.sku.lowercase().contains(q) ||
                it.categoryName.lowercase().contains(q)
        }
    }
    val filteredSuppliers = remember(suppliers, scopeSearch) {
        val q = scopeSearch.trim().lowercase()
        if (q.isEmpty()) suppliers else suppliers.filter {
            it.name.lowercase().contains(q) || it.phone.lowercase().contains(q)
        }
    }

    val canSubmit = !isBusy && when (selectedType) {
        InventoryType.GENERAL -> activeProducts.isNotEmpty()
        InventoryType.PARTIAL_CATEGORY -> selectedCategoryIds.isNotEmpty() && estimatedSelectedCount > 0
        InventoryType.PARTIAL_PRODUCTS -> selectedProductIds.isNotEmpty()
        InventoryType.PARTIAL_SUPPLIER -> selectedSupplierIds.isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StocktakingPalette.PageBg)
    ) {
        StocktakingHeaderBar(
            title = Bilingual.tr(lang, "إنشاء جلسة جرد جديدة", "Créer une session d'inventaire"),
            subtitle = Bilingual.tr(
                lang,
                "اختر نوع ونطاق الجرد وإعدادات المسح",
                "Choisissez le type, le périmètre et le mode de scan"
            ),
            onBack = onBack,
            backTestTag = "create_stocktaking_back_btn"
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Inventory Type Selector
            item(key = "type_selector") {
                SectionCard(
                    title = Bilingual.tr(lang, "1. نوع الجرد", "1. Type d'inventaire"),
                    icon = Icons.Filled.Category
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InventoryTypeOptionCard(
                                type = InventoryType.GENERAL,
                                title = Bilingual.tr(lang, "جرد شامل", "Général"),
                                subtitle = Bilingual.tr(lang, "كل المنتجات (${activeProducts.size})", "Tous (${activeProducts.size})"),
                                icon = Icons.Filled.Inventory,
                                selected = selectedType == InventoryType.GENERAL,
                                onClick = { selectedType = InventoryType.GENERAL; scopeSearch = "" },
                                modifier = Modifier.weight(1f)
                            )
                            InventoryTypeOptionCard(
                                type = InventoryType.PARTIAL_CATEGORY,
                                title = Bilingual.tr(lang, "حسب الفئات", "Par catégories"),
                                subtitle = Bilingual.tr(lang, "${categories.size} فئة", "${categories.size} familles"),
                                icon = Icons.Filled.FolderCopy,
                                selected = selectedType == InventoryType.PARTIAL_CATEGORY,
                                onClick = { selectedType = InventoryType.PARTIAL_CATEGORY; scopeSearch = "" },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InventoryTypeOptionCard(
                                type = InventoryType.PARTIAL_PRODUCTS,
                                title = Bilingual.tr(lang, "منتجات محددة", "Par produits"),
                                subtitle = Bilingual.tr(lang, "اختيار أو مسح", "Sélection / Scan"),
                                icon = Icons.Filled.Checklist,
                                selected = selectedType == InventoryType.PARTIAL_PRODUCTS,
                                onClick = { selectedType = InventoryType.PARTIAL_PRODUCTS; scopeSearch = "" },
                                modifier = Modifier.weight(1f)
                            )
                            InventoryTypeOptionCard(
                                type = InventoryType.PARTIAL_SUPPLIER,
                                title = Bilingual.tr(lang, "حسب الموردين", "Par fournisseurs"),
                                subtitle = Bilingual.tr(lang, "${suppliers.size} مورد", "${suppliers.size} fourn."),
                                icon = Icons.Filled.LocalShipping,
                                selected = selectedType == InventoryType.PARTIAL_SUPPLIER,
                                onClick = { selectedType = InventoryType.PARTIAL_SUPPLIER; scopeSearch = "" },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Session Details & Scan Behavior
            item(key = "session_details") {
                SectionCard(
                    title = Bilingual.tr(lang, "2. تفاصيل الجلسة وإعدادات الباركود", "2. Détails & mode de scan"),
                    icon = Icons.Filled.Tune
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = {
                                Text(Bilingual.tr(lang, "عنوان الجرد (اختياري)", "Titre de l'inventaire (optionnel)"))
                            },
                            placeholder = {
                                Text(Bilingual.tr(lang, "مثال: جرد نهاية الشهر", "Ex : Inventaire fin de mois"))
                            },
                            singleLine = true,
                            colors = stocktakingTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_stocktaking_title_input")
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = {
                                Text(Bilingual.tr(lang, "ملاحظات (اختياري)", "Notes (optionnel)"))
                            },
                            maxLines = 2,
                            colors = stocktakingTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_stocktaking_notes_input")
                        )

                        Text(
                            text = Bilingual.tr(lang, "عند مسح نفس الباركود مرة أخرى:", "En cas de scan répété :"),
                            color = StocktakingPalette.TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DuplicateScanMode.entries.forEach { mode ->
                                val isSelected = duplicateScanMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { duplicateScanMode = mode },
                                    label = {
                                        Text(
                                            text = duplicateScanModeLabel(mode, lang),
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("create_scan_mode_${mode.code.lowercase()}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Bilingual.tr(lang, "البدء الفوري بالعدّ", "Démarrer le comptage immédiatement"),
                                    color = StocktakingPalette.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (startImmediately) {
                                        Bilingual.tr(lang, "الحالة الأولية: قيد الجرد", "Statut initial : En cours")
                                    } else {
                                        Bilingual.tr(lang, "الحالة الأولية: مسودة", "Statut initial : Brouillon")
                                    },
                                    color = StocktakingPalette.TextMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                            Switch(
                                checked = startImmediately,
                                onCheckedChange = { startImmediately = it },
                                modifier = Modifier.testTag("create_start_immediately_switch")
                            )
                        }
                    }
                }
            }

            // 3. Dynamic Scope Selection
            if (selectedType == InventoryType.GENERAL) {
                item(key = "general_summary") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StocktakingPalette.EmeraldSoft,
                        border = BorderStroke(1.dp, StocktakingPalette.Emerald.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = StocktakingPalette.Emerald,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "سيشمل الجرد الشامل جميع المنتجات النشطة في المتجر (${activeProducts.size} منتج) مع حفظ رصيد النظام تلقائياً.",
                                    "L'inventaire général inclut tous les produits actifs (${activeProducts.size} produits) avec capture automatique du stock théorique."
                                ),
                                color = StocktakingPalette.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                item(key = "scope_search_header") {
                    SectionCard(
                        title = when (selectedType) {
                            InventoryType.PARTIAL_CATEGORY -> Bilingual.tr(
                                lang,
                                "3. اختر الفئات (${selectedCategoryIds.size})",
                                "3. Sélectionnez les catégories (${selectedCategoryIds.size})"
                            )
                            InventoryType.PARTIAL_PRODUCTS -> Bilingual.tr(
                                lang,
                                "3. اختر المنتجات (${selectedProductIds.size})",
                                "3. Sélectionnez les produits (${selectedProductIds.size})"
                            )
                            else -> Bilingual.tr(
                                lang,
                                "3. اختر الموردين (${selectedSupplierIds.size})",
                                "3. Sélectionnez les fournisseurs (${selectedSupplierIds.size})"
                            )
                        },
                        icon = Icons.Filled.FactCheck
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = scopeSearch,
                                    onValueChange = { scopeSearch = it },
                                    placeholder = {
                                        Text(Bilingual.tr(lang, "بحث...", "Rechercher..."))
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    singleLine = true,
                                    colors = stocktakingTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("create_scope_search_input")
                                )

                                if (selectedType == InventoryType.PARTIAL_PRODUCTS) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            scanner.startScan().addOnSuccessListener { barcode ->
                                                val raw = barcode.rawValue?.trim().orEmpty()
                                                if (raw.isNotEmpty()) {
                                                    val found = activeProducts.firstOrNull {
                                                        it.barcode.equals(raw, ignoreCase = true)
                                                    }
                                                    if (found != null) {
                                                        selectedProductIds = selectedProductIds + found.id
                                                        Toast.makeText(
                                                            context,
                                                            Bilingual.tr(lang, "تمت إضافة: ${found.name}", "Ajouté : ${found.name}"),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            Bilingual.tr(lang, "المنتج غير موجود: $raw", "Produit introuvable : $raw"),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(52.dp)
                                            .testTag("create_scope_scan_barcode_btn")
                                    ) {
                                        Icon(
                                            Icons.Filled.QrCodeScanner,
                                            contentDescription = Bilingual.tr(lang, "مسح باركود", "Scanner")
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        when (selectedType) {
                                            InventoryType.PARTIAL_CATEGORY -> selectedCategoryIds = filteredCategories.map { it.id }.toSet()
                                            InventoryType.PARTIAL_PRODUCTS -> selectedProductIds = filteredProducts.map { it.id }.toSet()
                                            InventoryType.PARTIAL_SUPPLIER -> selectedSupplierIds = filteredSuppliers.map { it.id }.toSet()
                                            else -> {}
                                        }
                                    }
                                ) {
                                    Text(Bilingual.tr(lang, "تحديد الكل", "Tout sélectionner"), fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = {
                                        when (selectedType) {
                                            InventoryType.PARTIAL_CATEGORY -> selectedCategoryIds = emptySet()
                                            InventoryType.PARTIAL_PRODUCTS -> selectedProductIds = emptySet()
                                            InventoryType.PARTIAL_SUPPLIER -> selectedSupplierIds = emptySet()
                                            else -> {}
                                        }
                                    }
                                ) {
                                    Text(Bilingual.tr(lang, "إلغاء التحديد", "Effacer"), color = StocktakingPalette.Rose)
                                }
                            }
                        }
                    }
                }

                when (selectedType) {
                    InventoryType.PARTIAL_CATEGORY -> {
                        items(filteredCategories, key = { "cat_${it.id}" }) { cat ->
                            val count = productCountByCategory[cat.id] ?: 0
                            val checked = cat.id in selectedCategoryIds
                            ScopeSelectRow(
                                title = cat.name,
                                subtitle = Bilingual.tr(lang, "$count منتج نشط", "$count produits actifs"),
                                checked = checked,
                                testTag = "scope_category_${cat.id}",
                                onToggle = {
                                    selectedCategoryIds = if (checked) selectedCategoryIds - cat.id else selectedCategoryIds + cat.id
                                }
                            )
                        }
                    }
                    InventoryType.PARTIAL_PRODUCTS -> {
                        items(filteredProducts.take(150), key = { "prod_${it.id}" }) { prod ->
                            val checked = prod.id in selectedProductIds
                            ScopeSelectRow(
                                title = prod.name,
                                subtitle = "${prod.barcode} • ${prod.categoryName} • ${Bilingual.tr(lang, "المخزون", "Stock")}: ${StocktakingCalculator.formatQuantity(prod.currentStock)} ${prod.unit}",
                                checked = checked,
                                testTag = "scope_product_${prod.id}",
                                onToggle = {
                                    selectedProductIds = if (checked) selectedProductIds - prod.id else selectedProductIds + prod.id
                                }
                            )
                        }
                    }
                    InventoryType.PARTIAL_SUPPLIER -> {
                        items(filteredSuppliers, key = { "sup_${it.id}" }) { sup ->
                            val linkedCount = productCountBySupplier[sup.id] ?: 0
                            val checked = sup.id in selectedSupplierIds
                            ScopeSelectRow(
                                title = sup.name,
                                subtitle = Bilingual.tr(
                                    lang,
                                    "$linkedCount منتج مرتبط بفواتير الشراء",
                                    "$linkedCount produits liés aux achats"
                                ),
                                checked = checked,
                                testTag = "scope_supplier_${sup.id}",
                                onToggle = {
                                    selectedSupplierIds = if (checked) selectedSupplierIds - sup.id else selectedSupplierIds + sup.id
                                }
                            )
                        }
                    }
                    else -> {}
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }

        // Sticky Bottom Create Bar
        Surface(
            color = StocktakingPalette.CardBg,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Bilingual.tr(lang, "المنتجات المشمولة", "Produits inclus"),
                        color = StocktakingPalette.TextMuted,
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = if (selectedType == InventoryType.PARTIAL_SUPPLIER && estimatedSelectedCount == 0 && selectedSupplierIds.isNotEmpty()) {
                            Bilingual.tr(lang, "${selectedSupplierIds.size} مورد محدد", "${selectedSupplierIds.size} fournisseur(s)")
                        } else {
                            Bilingual.tr(lang, "$estimatedSelectedCount منتج", "$estimatedSelectedCount produit(s)")
                        },
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }

                Button(
                    onClick = {
                        onCreateSession(
                            selectedType,
                            title,
                            notes,
                            selectedCategoryIds.toList(),
                            selectedProductIds.toList(),
                            selectedSupplierIds.toList(),
                            duplicateScanMode,
                            startImmediately
                        )
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.AccentBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 50.dp)
                        .testTag("stocktaking_confirm_create_btn")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (startImmediately) {
                            Bilingual.tr(lang, "بدء الجرد الآن", "Démarrer")
                        } else {
                            Bilingual.tr(lang, "حفظ كمسودة", "Créer brouillon")
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = StocktakingPalette.AccentBlue, modifier = Modifier.size(20.dp))
                Text(
                    text = title,
                    color = StocktakingPalette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            content()
        }
    }
}

@Composable
private fun InventoryTypeOptionCard(
    type: InventoryType,
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) StocktakingPalette.SelectedCardBg else StocktakingPalette.SurfaceSubtle,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) StocktakingPalette.AccentBlue else StocktakingPalette.CardBorder
        ),
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("create_type_${type.code.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) StocktakingPalette.AccentBlue else StocktakingPalette.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    color = if (selected) StocktakingPalette.AccentBlue else StocktakingPalette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = subtitle,
                color = StocktakingPalette.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScopeSelectRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) StocktakingPalette.SelectedCardBg else StocktakingPalette.CardBg,
        border = BorderStroke(
            width = if (checked) 1.5.dp else 1.dp,
            color = if (checked) StocktakingPalette.AccentBlue else StocktakingPalette.CardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = StocktakingPalette.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = StocktakingPalette.TextMuted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
