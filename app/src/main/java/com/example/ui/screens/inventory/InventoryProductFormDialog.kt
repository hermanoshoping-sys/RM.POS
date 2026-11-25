package com.example.ui.screens

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSPrimary
import com.example.ui.viewmodels.PosViewModel
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import kotlinx.coroutines.delay

internal const val PRODUCT_FORM_DOUBLE_PRESS_EXIT_WINDOW_MS = 2500L

@Composable
internal fun InventoryProductFormDialog(
    initialProduct: ProductEntity?,
    catsList: List<CategoryEntity>,
    scanner: GmsBarcodeScanner,
    lang: String,
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    clockMs: () -> Long = { SystemClock.elapsedRealtime() }
) {
    val context = LocalContext.current
    val firstCat = catsList.firstOrNull()
    val pIdEditing = initialProduct?.id ?: 0L

    var pBarcode by remember(initialProduct?.id) { mutableStateOf(initialProduct?.barcode ?: "") }
    var pName by remember(initialProduct?.id) { mutableStateOf(initialProduct?.name ?: "") }
    var pDescription by remember(initialProduct?.id) { mutableStateOf(initialProduct?.description ?: "") }
    var pCatId by remember(initialProduct?.id) { mutableStateOf(initialProduct?.categoryId ?: (firstCat?.id ?: 0L)) }
    var pFamilyName by remember(initialProduct?.id) {
        mutableStateOf(
            initialProduct?.categoryName?.ifBlank { catsList.find { it.id == initialProduct.categoryId }?.name ?: "" }
                ?: (firstCat?.name ?: "")
        )
    }
    var pUnit by remember(initialProduct?.id) { mutableStateOf(initialProduct?.unit ?: "قطعة / Unité") }
    var pPurchasePrice by remember(initialProduct?.id) { mutableStateOf((initialProduct?.purchasePriceDouble ?: 0.0).toString()) }
    var pSellPrice by remember(initialProduct?.id) { mutableStateOf((initialProduct?.sellPriceDouble ?: 0.0).toString()) }
    var pWholesalePrice by remember(initialProduct?.id) { mutableStateOf((initialProduct?.wholesalePriceDouble ?: 0.0).toString()) }
    var pSpecialPrice by remember(initialProduct?.id) { mutableStateOf((initialProduct?.specialPriceDouble ?: 0.0).toString()) }
    var pCurrentStock by remember(initialProduct?.id) { mutableStateOf((initialProduct?.currentStock ?: 10.0).toString()) }
    var pMinStock by remember(initialProduct?.id) { mutableStateOf((initialProduct?.minStock ?: 2.0).toString()) }
    var pSelectedImages by remember(initialProduct?.id) { mutableStateOf(initialProduct?.imageList ?: emptyList()) }
    var expandedFamilyDropdown by remember { mutableStateOf(false) }

    // Double-press guard so the dialog stays fixed and cannot be closed accidentally by a single tap/back press
    var lastExitAttemptMs by remember { mutableLongStateOf(0L) }
    var exitConfirmPending by remember { mutableStateOf(false) }
    var exitAttemptTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(exitAttemptTick) {
        if (exitAttemptTick > 0) {
            delay(PRODUCT_FORM_DOUBLE_PRESS_EXIT_WINDOW_MS)
            exitConfirmPending = false
            lastExitAttemptMs = 0L
        }
    }

    val handleExitRequest: () -> Unit = {
        val now = clockMs()
        val withinWindow = exitConfirmPending &&
            lastExitAttemptMs > 0L &&
            (now - lastExitAttemptMs) in 0L..PRODUCT_FORM_DOUBLE_PRESS_EXIT_WINDOW_MS
        if (withinWindow) {
            exitConfirmPending = false
            lastExitAttemptMs = 0L
            onDismiss()
        } else {
            lastExitAttemptMs = now
            exitConfirmPending = true
            exitAttemptTick++
            Toast.makeText(
                context,
                if (lang == "AR") "اضغط مرة أخرى للخروج" else "Appuyez une 2ème fois pour quitter",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val multiImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.w("InventoryForm", "Persistable URI permission not supported for $uri", e)
            }
        }
        pSelectedImages = (pSelectedImages + uris.map { it.toString() }).distinct()
    }

    Dialog(
        onDismissRequest = handleExitRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(vertical = 8.dp)
                .testTag("product_form_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Pinned Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (pIdEditing == 0L) Bilingual.get("add_prod_btn", lang) else "تعديل المنتج / Éditer Produit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (lang == "AR") "ثابتة" else "Fixe",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = handleExitRequest,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("product_form_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = if (lang == "AR") "إغلاق" else "Fermer",
                            tint = if (exitConfirmPending) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Inline double-press exit confirmation banner (always visible while pinned)
                AnimatedVisibility(visible = exitConfirmPending) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .testTag("product_form_exit_confirm_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (lang == "AR") {
                                    "اضغط مرة أخرى للتأكيد والخروج من النافذة"
                                } else {
                                    "Appuyez une 2ème fois pour confirmer et quitter"
                                },
                                color = Color(0xFFEF4444),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lang == "AR") "صور المنتج (1080x1350):" else "Photos du produit (1080x1350):",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (pSelectedImages.isNotEmpty()) {
                            TextButton(
                                onClick = { pSelectedImages = emptyList() },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    if (lang == "AR") "إزالة الكل (${pSelectedImages.size})" else "Tout supprimer",
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(64.dp)
                                .aspectRatio(1080f / 1350f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    multiImagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Add Images", tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(if (lang == "AR") "+ صورة" else "+ Photo", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("1080x1350", color = Color(0xFF94A3B8), fontSize = 7.5.sp)
                            }
                        }

                        pSelectedImages.forEachIndexed { index, uriStr ->
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .aspectRatio(1080f / 1350f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                            ) {
                                AsyncImage(
                                    model = uriStr,
                                    contentDescription = "Image ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.align(Alignment.BottomStart).padding(3.dp)
                                ) {
                                    Text("${index + 1}", color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                }
                                IconButton(
                                    onClick = { pSelectedImages = pSelectedImages.filterIndexed { i, _ -> i != index } },
                                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp).background(Color(0xFFEF4444), CircleShape)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Text(
                        text = if (lang == "AR")
                            "يمكنك إضافة حتى 10 صور بمقاس متناسق (1080x1350) مع إمكانية التمرير بينها مباشرة في الكاتالوج"
                        else
                            "Jusqu'à 10 photos au format (1080x1350) avec défilement direct dans le catalogue",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pBarcode,
                    onValueChange = { pBarcode = it },
                    label = { Text(Bilingual.get("barcode", lang)) },
                    trailingIcon = {
                        IconButton(onClick = {
                            scanner.startScan().addOnSuccessListener { b -> b.rawValue?.let { pBarcode = it } }
                        }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan", tint = Color(0xFF38BDF8))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("product_form_barcode_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = pName, onValueChange = { pName = it }, label = { Text(Bilingual.get("name_ar", lang)) }, modifier = Modifier.fillMaxWidth().testTag("product_form_name_input"))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = pDescription, onValueChange = { pDescription = it }, label = { Text(Bilingual.get("description", lang)) }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Icon(Icons.Filled.Category, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == "AR") "عائلة المنتج" else "Famille du produit", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(if (lang == "AR") "أدخل أو اختر" else "Saisir ou choisir", fontSize = 11.sp, color = Color.Gray)
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = pFamilyName,
                            onValueChange = { input ->
                                pFamilyName = input
                                expandedFamilyDropdown = true
                                catsList.find { it.name.trim().equals(input.trim(), ignoreCase = true) }?.let {
                                    pCatId = it.id
                                }
                            },
                            label = { Text(if (lang == "AR") "عائلة المنتج (الفئة)" else "Famille de produit (Catégorie)") },
                            placeholder = { Text(if (lang == "AR") "أدخل اسم العائلة أو اختر من القائمة" else "Saisir ou choisir la famille") },
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (pFamilyName.isNotEmpty()) {
                                        IconButton(onClick = { pFamilyName = ""; pCatId = 0L }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    IconButton(onClick = { expandedFamilyDropdown = !expandedFamilyDropdown }, modifier = Modifier.size(28.dp)) {
                                        Icon(if (expandedFamilyDropdown) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, contentDescription = "Toggle families", tint = Color(0xFF38BDF8))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = Color(0xFF475569)),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val matchingFamilies = remember(catsList, pFamilyName) {
                            if (pFamilyName.isBlank()) catsList else catsList.filter { it.name.contains(pFamilyName.trim(), ignoreCase = true) }
                        }

                        DropdownMenu(
                            expanded = expandedFamilyDropdown && (matchingFamilies.isNotEmpty() || pFamilyName.isNotBlank()),
                            onDismissRequest = { expandedFamilyDropdown = false },
                            modifier = Modifier.width(360.dp).background(Color(0xFF1E293B))
                        ) {
                            matchingFamilies.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(cat.name, color = Color.White, fontSize = 13.sp)
                                            if (cat.name.trim().equals(pFamilyName.trim(), ignoreCase = true) || cat.id == pCatId) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        pFamilyName = cat.name
                                        pCatId = cat.id
                                        expandedFamilyDropdown = false
                                    }
                                )
                            }
                            if (pFamilyName.isNotBlank() && catsList.none { it.name.trim().equals(pFamilyName.trim(), ignoreCase = true) }) {
                                HorizontalDivider(color = Color(0xFF334155))
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.AddCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (lang == "AR") "إضافة عائلة جديدة: \"${pFamilyName.trim()}\"" else "Créer famille : \"${pFamilyName.trim()}\"",
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    },
                                    onClick = { expandedFamilyDropdown = false }
                                )
                            }
                        }
                    }

                    if (catsList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (lang == "AR") "العائلات المسجلة:" else "Familles enregistrées :", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            items(catsList, key = { it.id }, contentType = { "family_chip" }) { cat ->
                                val isSelected = cat.name.trim().equals(pFamilyName.trim(), ignoreCase = true) || (pCatId == cat.id && pFamilyName.isBlank())
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        pFamilyName = cat.name
                                        pCatId = cat.id
                                        expandedFamilyDropdown = false
                                    },
                                    label = { Text(cat.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF2563EB),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFFCBD5E1)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = pUnit, onValueChange = { pUnit = it }, label = { Text(Bilingual.get("unit", lang)) }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = pPurchasePrice, onValueChange = { pPurchasePrice = it }, label = { Text(Bilingual.get("purchase_price", lang)) }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = pSellPrice, onValueChange = { pSellPrice = it }, label = { Text(Bilingual.get("selling_price", lang)) }, modifier = Modifier.fillMaxWidth().testTag("product_form_sell_price_input"))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = pWholesalePrice, onValueChange = { pWholesalePrice = it }, label = { Text(Bilingual.get("wholesale_price", lang)) }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = pCurrentStock, onValueChange = { pCurrentStock = it }, label = { Text(Bilingual.get("initial_stock", lang)) }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = pMinStock, onValueChange = { pMinStock = it }, label = { Text(Bilingual.get("min_stock_alert", lang)) }, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Pinned Footer
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = handleExitRequest,
                        border = BorderStroke(
                            1.dp,
                            if (exitConfirmPending) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (exitConfirmPending) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("product_form_cancel_button")
                    ) {
                        Text(
                            text = when {
                                exitConfirmPending && lang == "AR" -> "اضغط مجدداً للخروج"
                                exitConfirmPending -> "Confirmer sortie"
                                lang == "AR" -> "إلغاء"
                                else -> "Annuler"
                            },
                            fontWeight = if (exitConfirmPending) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            val targetCatName = pFamilyName.trim().ifEmpty {
                                catsList.find { it.id == pCatId }?.name ?: if (lang == "AR") "عام" else "Général"
                            }
                            viewModel.saveProduct(
                                id = pIdEditing,
                                barcode = pBarcode,
                                name = pName,
                                description = pDescription,
                                categoryId = pCatId,
                                categoryName = targetCatName,
                                unit = pUnit,
                                purchasePrice = pPurchasePrice.toDoubleOrNull() ?: 0.0,
                                sellPrice = pSellPrice.toDoubleOrNull() ?: 0.0,
                                wholesalePrice = pWholesalePrice.toDoubleOrNull() ?: 0.0,
                                specialPrice = pSpecialPrice.toDoubleOrNull() ?: 0.0,
                                currentStock = pCurrentStock.toDoubleOrNull() ?: 0.0,
                                minStock = pMinStock.toDoubleOrNull() ?: 2.0,
                                imageUri = if (pSelectedImages.isEmpty()) null else pSelectedImages.joinToString("||")
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("product_form_save_button")
                    ) {
                        Text(Bilingual.get("save", lang), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
