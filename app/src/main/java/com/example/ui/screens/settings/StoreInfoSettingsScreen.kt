package com.example.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.MapAddressPickerDialog
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSPrimary
import com.example.ui.viewmodels.PosViewModel
import com.example.util.DocumentDesignHelper

/**
 * 1. Store & Company Info subscreen.
 */
@Composable
fun StoreInfoSettingsScreen(
    viewModel: PosViewModel,
    lang: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storeSettings by viewModel.settings.collectAsStateWithLifecycle()
    val companyLogoUri by viewModel.companyLogoUri.collectAsStateWithLifecycle()
    val currentCurrency by viewModel.currency.collectAsStateWithLifecycle()

    var shopNameInput by remember(storeSettings.shopName) { mutableStateOf(storeSettings.shopName) }
    var shopAddressInput by remember(storeSettings.shopAddress) { mutableStateOf(storeSettings.shopAddress) }
    var showShopMapPicker by remember { mutableStateOf(false) }
    var shopPhoneInput by remember(storeSettings.shopPhone) { mutableStateOf(storeSettings.shopPhone) }
    var taxNumberInput by remember(storeSettings.taxNumber) { mutableStateOf(storeSettings.taxNumber) }
    var currencyInput by remember(currentCurrency) { mutableStateOf(currentCurrency) }
    var logoWidthInput by remember(storeSettings.logoWidth) { mutableFloatStateOf(storeSettings.logoWidth.toFloat().coerceIn(40f, 320f)) }
    var logoHeightInput by remember(storeSettings.logoHeight) { mutableFloatStateOf(storeSettings.logoHeight.toFloat().coerceIn(30f, 220f)) }
    var logoQualityInput by remember(storeSettings.logoQuality) { mutableFloatStateOf(storeSettings.logoQuality.toFloat().coerceIn(40f, 100f)) }
    var logoVersion by remember { mutableIntStateOf(0) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedUri ->
            val savedUri = DocumentDesignHelper.savePickedImage(
                context = context,
                uri = selectedUri,
                fileName = DocumentDesignHelper.LOGO_FILE_NAME,
                quality = logoQualityInput.toInt()
            )
            if (savedUri != null) {
                viewModel.updateCompanyLogo(savedUri)
                logoVersion++
                Toast.makeText(
                    context,
                    Bilingual.tr(lang, "تم حفظ شعار المتجر بنجاح!", "Logo enregistré !", "Store logo saved!"),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    Bilingual.tr(lang, "تعذر تحميل صورة الشعار", "Erreur chargement logo", "Failed to load logo"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val logoBitmap = remember(companyLogoUri, logoVersion) {
        DocumentDesignHelper.decodeUiBitmap(context, companyLogoUri, DocumentDesignHelper.LOGO_FILE_NAME)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        SettingsSubHeader(
            title = Bilingual.tr(lang, "بيانات الشركة والمحل", "Informations Entreprise", "Store & Company Info"),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo Upload Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = Bilingual.tr(lang, "شعار الشركة / المتجر", "Logo de l'Entreprise", "Store / Company Logo"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap.asImageBitmap(),
                            contentDescription = Bilingual.tr(lang, "شعار الشركة", "Logo", "Company Logo"),
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF475569), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Storefront, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                logoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("upload_logo_button")
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Bilingual.tr(lang, "تحميل الشعار من الهاتف", "Choisir une image", "Upload Logo from Phone"))
                        }

                        if (logoBitmap != null) {
                            OutlinedButton(
                                onClick = {
                                    DocumentDesignHelper.deleteInternalImage(context, DocumentDesignHelper.LOGO_FILE_NAME)
                                    viewModel.updateCompanyLogo(null)
                                    logoVersion++
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                                border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("remove_logo_button")
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = Bilingual.tr(lang, "حذف الشعار", "Supprimer logo", "Remove Logo"))
                            }
                        }
                    }
                }
            }

            // Logo Dimensions & Quality Card
            LogoDimensionsAndQualityCard(
                lang = lang,
                logoBitmap = logoBitmap,
                logoWidth = logoWidthInput,
                onLogoWidthChange = { logoWidthInput = it },
                logoHeight = logoHeightInput,
                onLogoHeightChange = { logoHeightInput = it },
                logoQuality = logoQualityInput,
                onLogoQualityChange = { logoQualityInput = it }
            )

            // Input fields
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = shopNameInput,
                        onValueChange = { shopNameInput = it },
                        label = { Text(Bilingual.tr(lang, "اسم الشركة المسجلة / المتجر", "Nom de l'Entreprise / Magasin", "Registered Company / Store Name")) },
                        leadingIcon = { Icon(Icons.Filled.Store, contentDescription = null, tint = POSPrimary) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = shopAddressInput,
                        onValueChange = { shopAddressInput = it },
                        label = { Text(Bilingual.tr(lang, "العنوان الكامل", "Adresse Complète", "Full Address")) },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8)) },
                        trailingIcon = {
                            IconButton(onClick = { showShopMapPicker = true }) {
                                Icon(Icons.Filled.Map, contentDescription = "Pick on map", tint = Color(0xFF38BDF8))
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { showShopMapPicker = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                Bilingual.tr(lang, "تحديد عنوان المتجر من الخريطة", "Depuis la carte", "Pick Store on Map"),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = shopPhoneInput,
                        onValueChange = { shopPhoneInput = it },
                        label = { Text(Bilingual.tr(lang, "رقم الهاتف للتواصل", "Téléphone", "Contact Phone")) },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = Color(0xFF10B981)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = taxNumberInput,
                        onValueChange = { taxNumberInput = it },
                        label = { Text(Bilingual.tr(lang, "الرقم الضريبي / السجل التجاري", "Identifiant Fiscal / ICE", "Tax ID / Business Reg.")) },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = Color(0xFFF59E0B)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = currencyInput,
                        onValueChange = { currencyInput = it },
                        label = { Text(Bilingual.tr(lang, "رمز العملة (مثال: د.م)", "Devise (Ex: DH)", "Currency Symbol (e.g. DH)")) },
                        leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = Color(0xFFEAB308)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val deviceId = remember { viewModel.getDeviceId() }
            com.example.ui.components.DeviceIdBannerCard(deviceId = deviceId, lang = lang)

            // Save Button
            Button(
                onClick = {
                    val savedW = logoWidthInput.toInt().coerceIn(40, 320)
                    val savedH = logoHeightInput.toInt().coerceIn(30, 220)
                    val savedQ = logoQualityInput.toInt().coerceIn(40, 100)
                    viewModel.updateSettings(
                        storeSettings.copy(
                            shopName = shopNameInput.trim(),
                            shopAddress = shopAddressInput.trim(),
                            shopPhone = shopPhoneInput.trim(),
                            taxNumber = taxNumberInput.trim(),
                            logoWidth = savedW,
                            logoHeight = savedH,
                            logoQuality = savedQ
                        )
                    )
                    if (currencyInput.isNotBlank()) {
                        viewModel.setCurrency(currencyInput.trim())
                    }
                    Toast.makeText(
                        context,
                        Bilingual.tr(lang, "تم حفظ بيانات وأبعاد وجودة الشعار بنجاح!", "Enregistré avec succès!", "Store info & logo saved!"),
                        Toast.LENGTH_SHORT
                    ).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_store_info_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D6AE5))
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Bilingual.tr(lang, "حفظ بيانات الشركة", "Enregistrer", "Save Company Info"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }

    if (showShopMapPicker) {
        MapAddressPickerDialog(
            initialAddress = shopAddressInput,
            lang = lang,
            onAddressConfirmed = { addr: String, _: Double, _: Double ->
                shopAddressInput = addr
                showShopMapPicker = false
            },
            onDismiss = { showShopMapPicker = false }
        )
    }
}
