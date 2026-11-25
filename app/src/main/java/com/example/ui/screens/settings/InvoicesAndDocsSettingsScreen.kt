package com.example.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import com.example.util.DocumentDesignHelper

/**
 * 2. Invoices & Documents Customization Subscreen:
 * - Logo dimensions & print quality
 * - Document font size
 * - Header & Footer content mode (Text / Image / Both / None) and dimensions
 * - Visible receipt elements (without Payment Method in footer)
 */
@Composable
fun InvoicesAndDocsSettingsScreen(
    viewModel: PosViewModel,
    lang: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storeSettings by viewModel.settings.collectAsStateWithLifecycle()
    val companyLogoUri by viewModel.companyLogoUri.collectAsStateWithLifecycle()
    val headerImageUri by viewModel.headerImageUri.collectAsStateWithLifecycle()
    val footerImageUri by viewModel.footerImageUri.collectAsStateWithLifecycle()

    var showShopName by remember(storeSettings.showShopName) { mutableStateOf(storeSettings.showShopName) }
    var showAddress by remember(storeSettings.showAddress) { mutableStateOf(storeSettings.showAddress) }
    var showPhone by remember(storeSettings.showPhone) { mutableStateOf(storeSettings.showPhone) }
    var showTaxNumber by remember(storeSettings.showTaxNumber) { mutableStateOf(storeSettings.showTaxNumber) }
    var showLogo by remember(storeSettings.showLogo) { mutableStateOf(storeSettings.showLogo) }
    var showCashierName by remember(storeSettings.showCashierName) { mutableStateOf(storeSettings.showCashierName) }
    var showCustomerInfo by remember(storeSettings.showCustomerInfo) { mutableStateOf(storeSettings.showCustomerInfo) }
    var showDateTime by remember(storeSettings.showDateTime) { mutableStateOf(storeSettings.showDateTime) }
    var showBarcode by remember(storeSettings.showBarcode) { mutableStateOf(storeSettings.showBarcode) }
    var showReceiptFooter by remember(storeSettings.showReceiptFooter) { mutableStateOf(storeSettings.showReceiptFooter) }

    // Logo controls
    var logoWidthInput by remember(storeSettings.logoWidth) { mutableFloatStateOf(storeSettings.logoWidth.toFloat().coerceIn(40f, 320f)) }
    var logoHeightInput by remember(storeSettings.logoHeight) { mutableFloatStateOf(storeSettings.logoHeight.toFloat().coerceIn(30f, 220f)) }
    var logoQualityInput by remember(storeSettings.logoQuality) { mutableFloatStateOf(storeSettings.logoQuality.toFloat().coerceIn(40f, 100f)) }

    // Document font size
    var documentFontSizeInput by remember(storeSettings.documentFontSize) {
        mutableFloatStateOf(storeSettings.documentFontSize.toFloat().coerceIn(8f, 20f))
    }

    // Header controls
    var headerContentType by remember(storeSettings.headerContentType) {
        mutableStateOf(DocumentDesignHelper.normalizeContentType(storeSettings.headerContentType))
    }
    var receiptHeaderInput by remember(storeSettings.receiptHeader) { mutableStateOf(storeSettings.receiptHeader) }
    var headerHeightInput by remember(storeSettings.headerHeight) { mutableFloatStateOf(storeSettings.headerHeight.toFloat().coerceIn(24f, 220f)) }
    var headerWidthInput by remember(storeSettings.headerWidth) { mutableFloatStateOf(storeSettings.headerWidth.toFloat().coerceIn(120f, 538f)) }
    var headerFontSizeInput by remember(storeSettings.headerFontSize) { mutableFloatStateOf(storeSettings.headerFontSize.toFloat().coerceIn(9f, 26f)) }
    var headerImageVersion by remember { mutableIntStateOf(0) }

    // Footer controls
    var footerContentType by remember(storeSettings.footerContentType) {
        mutableStateOf(DocumentDesignHelper.normalizeContentType(storeSettings.footerContentType))
    }
    var receiptFooterInput by remember(storeSettings.receiptFooter) { mutableStateOf(storeSettings.receiptFooter) }
    var footerHeightInput by remember(storeSettings.footerHeight) { mutableFloatStateOf(storeSettings.footerHeight.toFloat().coerceIn(24f, 220f)) }
    var footerWidthInput by remember(storeSettings.footerWidth) { mutableFloatStateOf(storeSettings.footerWidth.toFloat().coerceIn(120f, 538f)) }
    var footerImageVersion by remember { mutableIntStateOf(0) }

    val headerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selected ->
            val saved = DocumentDesignHelper.savePickedImage(
                context = context,
                uri = selected,
                fileName = DocumentDesignHelper.HEADER_IMAGE_FILE_NAME,
                quality = logoQualityInput.toInt()
            )
            if (saved != null) {
                viewModel.updateHeaderImage(saved)
                headerImageVersion++
                Toast.makeText(context, Bilingual.tr(lang, "تم حفظ صورة الهيدر بنجاح!", "Image d'en-tête enregistrée !", "Header image saved!"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val footerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selected ->
            val saved = DocumentDesignHelper.savePickedImage(
                context = context,
                uri = selected,
                fileName = DocumentDesignHelper.FOOTER_IMAGE_FILE_NAME,
                quality = logoQualityInput.toInt()
            )
            if (saved != null) {
                viewModel.updateFooterImage(saved)
                footerImageVersion++
                Toast.makeText(context, Bilingual.tr(lang, "تم حفظ صورة الفوتر بنجاح!", "Image de pied de page enregistrée !", "Footer image saved!"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val logoBitmap = remember(companyLogoUri) {
        DocumentDesignHelper.decodeUiBitmap(context, companyLogoUri, DocumentDesignHelper.LOGO_FILE_NAME)
    }
    val headerBitmap = remember(headerImageUri, headerImageVersion) {
        DocumentDesignHelper.decodeUiBitmap(context, headerImageUri, DocumentDesignHelper.HEADER_IMAGE_FILE_NAME)
    }
    val footerBitmap = remember(footerImageUri, footerImageVersion) {
        DocumentDesignHelper.decodeUiBitmap(context, footerImageUri, DocumentDesignHelper.FOOTER_IMAGE_FILE_NAME)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        SettingsSubHeader(
            title = Bilingual.tr(lang, "الفواتير والوثائق", "Factures & Documents", "Invoices & Documents"),
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
            // 1. Logo Dimensions & Quality
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

            // 2. Document Font Size
            DocumentFontSizeCard(
                lang = lang,
                documentFontSize = documentFontSizeInput,
                onDocumentFontSizeChange = { documentFontSizeInput = it }
            )

            // 3. Header Controls (Dimensions + Text / Image / Both / None)
            HeaderFooterDesignCard(
                lang = lang,
                title = Bilingual.tr(lang, "تخصيص الهيدر (الترويسة)", "Personnaliser l'En-tête", "Header Customization"),
                accentColor = Color(0xFF38BDF8),
                icon = Icons.Filled.VerticalAlignTop,
                contentType = headerContentType,
                onContentTypeChange = { headerContentType = it },
                textValue = receiptHeaderInput,
                onTextValueChange = { receiptHeaderInput = it },
                textFieldLabel = Bilingual.tr(lang, "نص الهيدر أعلى الوثيقة", "Texte d'en-tête", "Header Text"),
                heightValue = headerHeightInput,
                onHeightChange = { headerHeightInput = it },
                widthValue = headerWidthInput,
                onWidthChange = { headerWidthInput = it },
                fontSizeValue = headerFontSizeInput,
                onFontSizeChange = { headerFontSizeInput = it },
                imageBitmap = headerBitmap,
                onPickImage = {
                    headerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveImage = {
                    DocumentDesignHelper.deleteInternalImage(context, DocumentDesignHelper.HEADER_IMAGE_FILE_NAME)
                    viewModel.updateHeaderImage(null)
                    headerImageVersion++
                },
                tagPrefix = "header"
            )

            // 4. Footer Controls (Dimensions + Text / Image / Both / None)
            HeaderFooterDesignCard(
                lang = lang,
                title = Bilingual.tr(lang, "تخصيص الفوتر (التذييل)", "Personnaliser le Pied de page", "Footer Customization"),
                accentColor = Color(0xFF10B981),
                icon = Icons.Filled.VerticalAlignBottom,
                contentType = footerContentType,
                onContentTypeChange = { footerContentType = it },
                textValue = receiptFooterInput,
                onTextValueChange = { receiptFooterInput = it },
                textFieldLabel = Bilingual.tr(lang, "نص الفوتر أسفل الوثيقة", "Texte de pied de page", "Footer Text"),
                heightValue = footerHeightInput,
                onHeightChange = { footerHeightInput = it },
                widthValue = footerWidthInput,
                onWidthChange = { footerWidthInput = it },
                imageBitmap = footerBitmap,
                onPickImage = {
                    footerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemoveImage = {
                    DocumentDesignHelper.deleteInternalImage(context, DocumentDesignHelper.FOOTER_IMAGE_FILE_NAME)
                    viewModel.updateFooterImage(null)
                    footerImageVersion++
                },
                tagPrefix = "footer"
            )

            // 5. Visible Elements
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = Bilingual.tr(lang, "العناصر الظاهرة في الفاتورة والوصل", "Éléments à afficher", "Visible Receipt Elements"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )

                    SettingsSwitchRow(Bilingual.tr(lang, "إظهار اسم المتجر", "Afficher nom magasin", "Show Store Name"), showShopName) { showShopName = it }
                    SettingsSwitchRow(Bilingual.tr(lang, "إظهار الشعار", "Afficher logo", "Show Logo"), showLogo) { showLogo = it }
                    SettingsSwitchRow(Bilingual.tr(lang, "إظهار العنوان", "Afficher adresse", "Show Address"), showAddress) { showAddress = it }
                    SettingsSwitchRow(Bilingual.tr(lang, "إظهار رقم الهاتف", "Afficher téléphone", "Show Phone"), showPhone) { showPhone = it }
                    SettingsSwitchRow(Bilingual.tr(lang, "إظهار الرقم الضريبي", "Afficher ICE / IF", "Show Tax ID"), showTaxNumber) { showTaxNumber = it }
                    SettingsSwitchRow(Bilingual.tr(lang, "إظهار اسم البائع في أسفل الفاتورة", "Afficher nom du vendeur en bas de facture", "Show Seller Name at Bottom"), showCashierName) { showCashierName = it }
                    SettingsSwitchRow(Bilingual.tr(lang, "إظهار بيانات الزبون", "Afficher client", "Show Customer Info"), showCustomerInfo) { showCustomerInfo = it }
                    SettingsSwitchRow(Bilingual.tr(lang, "إظهار التاريخ والوقت", "Afficher date/heure", "Show Date & Time"), showDateTime) { showDateTime = it }
                    SettingsSwitchRow(Bilingual.tr(lang, "إظهار باركود الفاتورة", "Afficher code-barres", "Show Invoice Barcode"), showBarcode) { showBarcode = it }
                    SettingsSwitchRow(Bilingual.tr(lang, "إظهار الفوتر (التذييل)", "Afficher pied de page", "Show Footer"), showReceiptFooter) { showReceiptFooter = it }
                }
            }

            Button(
                onClick = {
                    viewModel.updateSettings(
                        storeSettings.copy(
                            showShopName = showShopName,
                            showAddress = showAddress,
                            showPhone = showPhone,
                            showTaxNumber = showTaxNumber,
                            showLogo = showLogo,
                            showCashierName = showCashierName,
                            showCustomerInfo = showCustomerInfo,
                            showDateTime = showDateTime,
                            showBarcode = showBarcode,
                            showPaymentMethod = false,
                            showReceiptFooter = showReceiptFooter && footerContentType != DocumentDesignHelper.MODE_NONE,
                            logoWidth = logoWidthInput.toInt().coerceIn(40, 320),
                            logoHeight = logoHeightInput.toInt().coerceIn(30, 220),
                            logoQuality = logoQualityInput.toInt().coerceIn(40, 100),
                            documentFontSize = documentFontSizeInput.toInt().coerceIn(8, 20),
                            headerContentType = headerContentType,
                            receiptHeader = receiptHeaderInput.trim(),
                            headerHeight = headerHeightInput.toInt().coerceIn(24, 220),
                            headerWidth = headerWidthInput.toInt().coerceIn(120, 538),
                            headerFontSize = headerFontSizeInput.toInt().coerceIn(9, 26),
                            footerContentType = footerContentType,
                            receiptFooter = receiptFooterInput.trim(),
                            footerHeight = footerHeightInput.toInt().coerceIn(24, 220),
                            footerWidth = footerWidthInput.toInt().coerceIn(120, 538)
                        )
                    )
                    Toast.makeText(
                        context,
                        Bilingual.tr(lang, "تم حفظ إعدادات الفواتير والوثائق بنجاح!", "Paramètres enregistrés !", "Invoice & document settings saved!"),
                        Toast.LENGTH_SHORT
                    ).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_invoice_doc_settings_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D6AE5))
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Bilingual.tr(lang, "حفظ إعدادات الفواتير والوثائق", "Enregistrer", "Save Invoice Settings"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
