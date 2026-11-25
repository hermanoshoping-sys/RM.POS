package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.settings.*
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

/**
 * Supermarket POS Settings Root Screen.
 * Presents language selector, program login password controls, and a 6-tile grid dashboard with subscreen navigation.
 */
@Composable
fun SettingsScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: ((String) -> Unit)? = null
) {
    var activeSubScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }

    BackHandler {
        if (activeSubScreen != null) {
            activeSubScreen = null
        } else {
            onNavigate?.invoke("DASHBOARD")
        }
    }

    AnimatedContent(
        targetState = activeSubScreen,
        label = "SettingsNavTransition"
    ) { currentSubScreen ->
        if (currentSubScreen == null) {
            SettingsMainDashboard(
                viewModel = viewModel,
                lang = lang,
                onBackClick = { onNavigate?.invoke("DASHBOARD") },
                onTileClick = { subScreen -> activeSubScreen = subScreen }
            )
        } else {
            when (currentSubScreen) {
                SettingsSubScreen.STORE_INFO -> {
                    StoreInfoSettingsScreen(
                        viewModel = viewModel,
                        lang = lang,
                        onBack = { activeSubScreen = null }
                    )
                }
                SettingsSubScreen.INVOICES -> {
                    InvoicesAndDocsSettingsScreen(
                        viewModel = viewModel,
                        lang = lang,
                        onBack = { activeSubScreen = null }
                    )
                }
                SettingsSubScreen.PRINTER -> {
                    PrinterSettingsSubScreen(
                        viewModel = viewModel,
                        lang = lang,
                        onBack = { activeSubScreen = null }
                    )
                }
                SettingsSubScreen.BACKUP -> {
                    BackupDataSettingsScreen(
                        viewModel = viewModel,
                        lang = lang,
                        onBack = { activeSubScreen = null }
                    )
                }
                SettingsSubScreen.SECURITY -> {
                    SecuritySettingsScreen(
                        viewModel = viewModel,
                        lang = lang,
                        onBack = { activeSubScreen = null }
                    )
                }
                SettingsSubScreen.USERS -> {
                    UsersSettingsSubScreen(
                        viewModel = viewModel,
                        lang = lang,
                        onBack = { activeSubScreen = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsMainDashboard(
    viewModel: PosViewModel,
    lang: String,
    onBackClick: () -> Unit,
    onTileClick: (SettingsSubScreen) -> Unit
) {
    val context = LocalContext.current
    val requirePasswordOnLogin by viewModel.requirePasswordOnLogin.collectAsStateWithLifecycle()
    val storeSettings by viewModel.settings.collectAsStateWithLifecycle()
    var showLoginPasswordDialog by remember { mutableStateOf(false) }

    val headerBlue = Color(0xFF1D6AE5)
    val bgColor = Color(0xFFFBFBFB)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Curved Blue Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            color = headerBlue,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = Bilingual.tr(lang, "الإعدادات", "Paramètres", "Settings"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = { onTileClick(SettingsSubScreen.SECURITY) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Content: Quick Language + Login Password Cards + 6-Tile Grid
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Language Switcher Card (Arabic / French / English)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = headerBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "لغة التطبيق",
                                "Langue de l'application",
                                "App Language"
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageChoicePill(
                            label = "العربية",
                            code = "AR",
                            selected = lang == "AR",
                            accentColor = headerBlue,
                            testTag = "lang_option_ar",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setLanguage("AR") }
                        )
                        LanguageChoicePill(
                            label = "Français",
                            code = "FR",
                            selected = lang == "FR",
                            accentColor = headerBlue,
                            testTag = "lang_option_fr",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setLanguage("FR") }
                        )
                        LanguageChoicePill(
                            label = "English",
                            code = "EN",
                            selected = lang == "EN",
                            accentColor = headerBlue,
                            testTag = "lang_option_en",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setLanguage("EN") }
                        )
                    }
                }
            }

            // 2. Program Login Password Card (كلمة سر الدخول إلى البرنامج)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = headerBlue,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = Bilingual.tr(
                                        lang,
                                        "كلمة سر الدخول إلى البرنامج",
                                        "Mot de passe d'accès au programme",
                                        "Program Login Password"
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = Bilingual.tr(
                                        lang,
                                        if (requirePasswordOnLogin) "مفعّلة عند فتح التطبيق" else "الدخول مباشر بدون كلمة سر",
                                        if (requirePasswordOnLogin) "Demandé à l'ouverture" else "Accès direct sans mot de passe",
                                        if (requirePasswordOnLogin) "Required on startup" else "Direct entry without password"
                                    ),
                                    fontSize = 12.sp,
                                    color = if (requirePasswordOnLogin) Color(0xFF16A34A) else Color(0xFF64748B)
                                )
                            }
                        }

                        Switch(
                            checked = requirePasswordOnLogin,
                            onCheckedChange = { enabled ->
                                viewModel.updateLoginPasswordSecurity(enabled)
                                Toast.makeText(
                                    context,
                                    Bilingual.tr(
                                        lang,
                                        if (enabled) "تم تفعيل طلب كلمة سر الدخول عند فتح البرنامج" else "تم إلغاء طلب كلمة سر الدخول عند فتح البرنامج",
                                        if (enabled) "Mot de passe requis à l'ouverture" else "Accès direct activé sans mot de passe",
                                        if (enabled) "Login password enabled on startup" else "Direct startup login enabled"
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.testTag("settings_login_password_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = headerBlue
                            )
                        )
                    }

                    Button(
                        onClick = { showLoginPasswordDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEFF6FF),
                            contentColor = headerBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("settings_change_login_password_button")
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "تعيين / تغيير كلمة سر الدخول للبرنامج",
                                "Définir / Modifier le mot de passe d'accès",
                                "Set / Change Login Password"
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }

            // 3. Zero-Stock / Negative-Stock Sale Control Card (البيع عند نفاد المخزون = 0)
            ZeroStockSaleSettingsCard(
                allowZeroStockSale = storeSettings.allowNegativeStock,
                lang = lang,
                accentColor = headerBlue,
                onToggle = { enabled ->
                    viewModel.updateAllowNegativeStock(enabled)
                    Toast.makeText(
                        context,
                        Bilingual.tr(
                            lang,
                            if (enabled) "تم تفعيل البيع حتى وإن كان المخزون 0" else "تم إيقاف البيع عند نفاد المخزون (المخزون = 0)",
                            if (enabled) "Vente avec stock à 0 activée" else "Vente avec stock à 0 désactivée",
                            if (enabled) "Selling with 0 stock enabled" else "Selling with 0 stock disabled"
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            // Row 1: Store Info | Invoices & Docs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Top
            ) {
                SettingsGridTile(
                    title = Bilingual.tr(lang, "بيانات السوير مارشي", "Infos Magasin", "Store Info"),
                    icon = { SupermarketStoreIcon3D() },
                    onClick = { onTileClick(SettingsSubScreen.STORE_INFO) }
                )

                SettingsGridTile(
                    title = Bilingual.tr(lang, "الفواتير والوثائق", "Factures & Docs", "Invoices & Docs"),
                    icon = { InvoicesDocsIcon3D() },
                    onClick = { onTileClick(SettingsSubScreen.INVOICES) }
                )
            }

            // Row 2: Printer | Backup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Top
            ) {
                SettingsGridTile(
                    title = Bilingual.tr(lang, "الطباعة", "Impression", "Printing"),
                    icon = { PrinterIcon3D() },
                    onClick = { onTileClick(SettingsSubScreen.PRINTER) }
                )

                SettingsGridTile(
                    title = Bilingual.tr(lang, "نسخ البيانات", "Sauvegarde", "Backup"),
                    icon = { DataBackupIcon3D() },
                    onClick = { onTileClick(SettingsSubScreen.BACKUP) }
                )
            }

            // Row 3: Security | Users
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Top
            ) {
                SettingsGridTile(
                    title = Bilingual.tr(lang, "الأمان", "Sécurité", "Security"),
                    icon = { SecurityIcon3D() },
                    onClick = { onTileClick(SettingsSubScreen.SECURITY) }
                )

                SettingsGridTile(
                    title = Bilingual.tr(lang, "المستخدمون", "Utilisateurs", "Users"),
                    icon = { UsersAvatarsIcon3D() },
                    onClick = { onTileClick(SettingsSubScreen.USERS) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showLoginPasswordDialog) {
        ProgramLoginPasswordDialog(
            lang = lang,
            onDismiss = { showLoginPasswordDialog = false },
            onConfirm = { newPass, confirmPass, onError ->
                viewModel.setProgramLoginPassword(newPass, confirmPass) { success, msg ->
                    if (success) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        showLoginPasswordDialog = false
                    } else {
                        onError(msg)
                    }
                }
            }
        )
    }
}
