package com.example.ui.screens

import java.io.File
import androidx.core.content.FileProvider
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import com.example.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.core.entry.FloatEntry
import java.util.Calendar
import java.text.SimpleDateFormat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import com.example.ui.viewmodels.PosViewModel
import com.example.ui.screens.pos.*
import java.util.*

/**
 * Supermarket POS Master Layout.
 * Supports Phone / Tablet layouts (with Responsive Sidebar navigation and Rail),
 * multi-user logins, Arabic-French on-the-fly toggling, interactive scanner simulation,
 * integrated calculator operations and detailed Room persistence logic.
 */
@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun MainPOSApplication(viewModel: PosViewModel) {
    val currentLang by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val scanFeedback by viewModel.scanFeedbackMessage.collectAsStateWithLifecycle()
    val activeReceipt by viewModel.printedReceipt.collectAsStateWithLifecycle()
    val activeReceiptItems by viewModel.printedReceiptItems.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val isLicenseActivated by viewModel.isLicenseActivated.collectAsStateWithLifecycle()
    val existingUsersList by viewModel.users.collectAsStateWithLifecycle()
    val requirePasswordOnLogin by viewModel.requirePasswordOnLogin.collectAsStateWithLifecycle()
    val latestOperationError by viewModel.latestOperationError.collectAsStateWithLifecycle()

    var activeScreen by rememberSaveable { mutableStateOf("DASHBOARD") } // Survives screen rotation and configuration changes
    var showSplash by rememberSaveable { mutableStateOf(true) }

    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val targetLocale = remember(currentLang) { Bilingual.localeFor(currentLang) }
    val localizedConfiguration = remember(baseConfiguration, targetLocale) {
        Configuration(baseConfiguration).apply {
            setLocale(targetLocale)
            setLayoutDirection(targetLocale)
        }
    }
    val localizedContext = remember(baseContext, localizedConfiguration) {
        val configContext = baseContext.createConfigurationContext(localizedConfiguration)
        object : ContextWrapper(baseContext) {
            override fun getResources(): Resources = configContext.resources
            override fun getAssets(): AssetManager = configContext.assets
        }
    }

    SideEffect {
        Locale.setDefault(targetLocale)
    }

    val context = localizedContext
    val safeTextToolbar = com.example.ui.components.rememberSafeComposeTextToolbar()
    val rootFocusManager = LocalFocusManager.current

    // Dismiss any open floating text selection ActionMode cleanly before unmounting screens
    LaunchedEffect(showSplash, isLicenseActivated, currentUser?.id, activeScreen) {
        safeTextToolbar.hide()
        rootFocusManager.clearFocus()
    }

    LaunchedEffect(showSplash, isLicenseActivated, existingUsersList, requirePasswordOnLogin, currentUser) {
        if (!showSplash && isLicenseActivated && existingUsersList.isNotEmpty() && !requirePasswordOnLogin && currentUser == null) {
            viewModel.autoLoginIfPasswordNotRequired(existingUsersList)
        }
    }

    // Navigation permission guard: instantly bounce unauthorized screens back to DASHBOARD
    LaunchedEffect(activeScreen, currentUser) {
        val user = currentUser ?: return@LaunchedEffect
        if (user.isManager) return@LaunchedEffect

        val isBlocked = when {
            activeScreen == "SETTINGS" && !user.hasSettingsAccess() -> true
            activeScreen == "USERS" && !user.hasUsersAccess() -> true
            (activeScreen == "AUDIT_LOGS" || activeScreen == "SECURITY_AUDIT") && !user.isManager -> true
            (activeScreen == "REPORTS" || activeScreen.startsWith("REPORTS_") || activeScreen.startsWith("PRODUCT_SALES_")) && !user.hasReportsAccess() -> true
            (activeScreen == "PURCHASES" || activeScreen == "PURCHASE_INVOICES") && !user.hasPurchaseAccess() -> true
            (activeScreen == "POS" || activeScreen == "CART" || activeScreen == "SALES_INVOICES") && !user.hasSellAccess() -> true
            (activeScreen == "INVENTORY" || activeScreen == "STOCKTAKING" || activeScreen == "PRODUCT_FAMILIES" || activeScreen == "CATEGORIES") && !user.hasInventoryAccess() -> true
            activeScreen == "CLIENTS" && !user.hasClientsAccess() -> true
            activeScreen == "SUPPLIERS" && !user.hasSuppliersAccess() -> true
            activeScreen == "EXPENSES" && !user.hasExpensesAccess() -> true
            activeScreen == "PAYMENTS" && !user.hasPaymentsAccess() -> true
            else -> false
        }

        if (isBlocked) {
            android.widget.Toast.makeText(
                context,
                Bilingual.tr(
                    currentLang,
                    "تم حجب هذه الشاشة بناءً على صلاحيات المستخدم المحددة",
                    "Accès non autorisé pour ce profil",
                    "Access restricted for this user profile"
                ),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            activeScreen = "DASHBOARD"
        }
    }

    // Handle back button: if not on DASHBOARD, pressing back returns to DASHBOARD
    BackHandler(enabled = activeScreen != "DASHBOARD") {
        activeScreen = "DASHBOARD"
    }

    // Scaffold master container with dynamic RTL/LTR direction & reactive localized resources
    CompositionLocalProvider(
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext,
        LocalTextToolbar provides safeTextToolbar,
        LocalLayoutDirection provides if (currentLang == "AR") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Scaffold(
            bottomBar = {
                if (!showSplash && isLicenseActivated && currentUser != null && activeScreen != "DASHBOARD") {
                    com.example.ui.components.AppBottomNavigationBar(
                        activeScreen = activeScreen,
                        onNavigate = { activeScreen = it },
                        currentUser = currentUser,
                        lang = currentLang
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                if (showSplash) {
                    RMPosSplashScreen(
                        lang = currentLang,
                        onTimeout = { showSplash = false }
                    )
                } else if (!isLicenseActivated) {
                    LicenseActivationScreen(viewModel = viewModel, lang = currentLang)
                } else if (existingUsersList.isEmpty()) {
                    InitialAdminSetupScreen(viewModel = viewModel, lang = currentLang)
                } else if (currentUser == null) {
                    LoginScreen(
                        viewModel = viewModel,
                        lang = currentLang,
                        authViewModel = viewModel.authSecurityViewModel
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = activeScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screen_transition"
                        ) { targetScreen ->
                            when (targetScreen) {
                                "DASHBOARD" -> MainHomeScreen(
                                    viewModel = viewModel,
                                    lang = currentLang,
                                    onNavigate = { activeScreen = it },
                                    onOpenDrawer = { activeScreen = "SETTINGS" }
                                )
                                "POS" -> POSRegisterScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "CART" -> CartMobileScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "INVENTORY" -> InventoryScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "STOCKTAKING" -> com.example.ui.screens.stocktaking.StocktakingHostScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "PRODUCT_FAMILIES", "CATEGORIES" -> ProductFamiliesScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "CLIENTS" -> ClientsScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "SUPPLIERS" -> SuppliersScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "PAYMENTS" -> PaymentsScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "PURCHASES" -> PurchasesScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "EXPENSES" -> ExpensesScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "REPORTS", "REPORTS_SALES", "REPORTS_PURCHASES", "REPORTS_PROFITS", "REPORTS_STOCK", "REPORTS_CLIENTS", "REPORTS_SUPPLIERS", "REPORTS_CASH" -> POSAnalyticsReportsScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "SETTINGS" -> SettingsScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "SALES_INVOICES" -> SalesInvoicesScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "PURCHASE_INVOICES" -> PurchaseInvoicesScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "USERS" -> UsersScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "AUDIT_LOGS", "SECURITY_AUDIT" -> AuditLogsScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "PRODUCT_SALES_SEARCH", "PRODUCT_SALES_HISTORY", "SALES_AUDIT" -> ProductSalesSearchScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it })
                                "SUSPENDED" -> POSRegisterScreen(viewModel = viewModel, lang = currentLang, onNavigate = { activeScreen = it }) // Fallback
                                else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        Bilingual.tr(currentLang, "شاشة قيد التطوير", "Écran en cours de développement", "Screen under development"),
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // Real-Time floating notifications or feedback
                // Scanner simulation indicator banner
            AnimatedVisibility(
                visible = scanFeedback != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(80.dp)
            ) {
                Surface(
                    color = if (scanFeedback?.contains("not found") == true) POSDanger else POSPrimary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = scanFeedback ?: "",
                        
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Backup & Restore processing banner
            AnimatedVisibility(
                visible = backupStatus != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xD90F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.width(320.dp).padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = POSPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            val statusText = when (backupStatus) {
                                "backing_up" -> Bilingual.tr(currentLang, "جاري النسخ الاحتياطي...", "Sauvegarde en cours...", "Backing up data...")
                                "backup_success" -> Bilingual.get("backup_success", currentLang)
                                "restoring" -> Bilingual.tr(currentLang, "جاري استعادة البيانات...", "Restauration en cours...", "Restoring data...")
                                "restore_success" -> Bilingual.get("restore_success", currentLang)
                                else -> ""
                            }
                            Text(
                                text = statusText,
                                
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Thermal Printer Sim Card Dialog
            activeReceipt?.let { currentReceipt ->
                ThermalReceiptDialog(viewModel = viewModel, 
                    sale = currentReceipt,
                    items = activeReceiptItems,
                    lang = currentLang,
                    onDismiss = { viewModel.dismissReceipt() }
                )
            }

            // Unified Global Sales/Purchases Error Dialog
            latestOperationError?.let { opError ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearOperationError() },
                    modifier = Modifier.testTag("global_operation_error_dialog"),
                    icon = {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = Bilingual.tr(currentLang, "خطأ", "Erreur", "Error"),
                            tint = POSDanger,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(
                            text = Bilingual.tr(
                                currentLang,
                                "تعذّر إتمام العملية",
                                "Échec de l'opération",
                                "Operation Failed"
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = opError.message,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.clearOperationError() },
                            colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("global_operation_error_ok_button")
                        ) {
                            Text(Bilingual.tr(currentLang, "حسناً", "OK", "OK"))
                        }
                    }
                )
            }

            // Built-in Print & PDF Preview Window (opens automatically when PrintSpooler is unavailable)
            com.example.ui.components.InAppPrintDialogHost(lang = currentLang)
        }
    }
    }
}


