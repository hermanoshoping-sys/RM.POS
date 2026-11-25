package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.UsersScreen
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

/**
 * 5. Security & Passwords Settings Subscreen.
 */
@Composable
fun SecuritySettingsScreen(
    viewModel: PosViewModel,
    lang: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val requirePasswordOnLogin by viewModel.requirePasswordOnLogin.collectAsStateWithLifecycle()
    val storeSettings by viewModel.settings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var currentUsernameInput by remember(currentUser?.id, currentUser?.username) {
        mutableStateOf(currentUser?.username.orEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        SettingsSubHeader(
            title = Bilingual.tr(lang, "الأمان وكلمات المرور", "Sécurité & Mot de Passe", "Security & Passwords"),
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
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Badge, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "اسم المستخدم (اسم البائع في الفواتير)",
                                    "Nom d'utilisateur (Nom du vendeur)",
                                    "Username (Seller Name on Invoices)"
                                ),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "عند تغييره يظهر تلقائياً في خانة البائع بالفواتير والسجل",
                                    "Apparaît automatiquement comme vendeur sur les factures",
                                    "Automatically appears as Seller on invoices and history"
                                ),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = currentUsernameInput,
                        onValueChange = { currentUsernameInput = it },
                        label = {
                            Text(
                                Bilingual.tr(
                                    lang,
                                    "اسم المستخدم / البائع",
                                    "Nom d'utilisateur / Vendeur",
                                    "Username / Seller"
                                )
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF38BDF8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth().testTag("security_username_input")
                    )

                    Button(
                        onClick = {
                            viewModel.updateCurrentUsername(currentUsernameInput) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("security_save_username_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            Bilingual.tr(
                                lang,
                                "حفظ اسم المستخدم والبائع",
                                "Enregistrer le nom",
                                "Save Username & Seller"
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            ProgramLoginPasswordCard(
                context = context,
                viewModel = viewModel,
                lang = lang,
                requirePasswordOnLogin = requirePasswordOnLogin
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color(0xFF3B82F6).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Inventory, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Bilingual.tr(lang, "البيع عند نفاد المخزون (0) والبيع بالسالب", "Vente Stock à 0 & Stock Négatif", "Zero & Negative Stock Sales"),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "📦 عند تفعيل هذا الخيار يُسمح بإتمام البيع حتى وإن كان المخزون 0 أو أقل من الكمية المطلوبة، وعند إيقافه يمنع النظام البيع عند نفاد المخزون.",
                                "Lorsque cette option est activée, la vente est autorisée même si le stock est à 0 ou insuffisant. Désactivez-la pour bloquer la vente hors stock.",
                                "When enabled, sales can be completed even when stock is 0 or insufficient. Disable it to block out-of-stock sales."
                            ),
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    SettingsSwitchRow(
                        title = Bilingual.tr(
                            lang,
                            "السماح بالبيع حتى وإن كان المخزون 0",
                            "Autoriser la vente même si le stock est à 0",
                            "Allow selling even when stock is 0"
                        ),
                        checked = storeSettings.allowNegativeStock,
                        onCheckedChange = { isAllowed ->
                            viewModel.updateAllowNegativeStock(isAllowed)
                            Toast.makeText(
                                context,
                                Bilingual.tr(
                                    lang,
                                    if (isAllowed) "تم تفعيل البيع حتى وإن كان المخزون 0" else "تم إيقاف البيع عند نفاد المخزون",
                                    if (isAllowed) "Vente avec stock à 0 autorisée" else "Vente avec stock à 0 désactivée",
                                    if (isAllowed) "Zero-stock sales enabled" else "Zero-stock sales disabled"
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }

            DeleteAndEditProtectionCard(viewModel = viewModel, lang = lang)

            ChangeCurrentPasswordCard(context = context, viewModel = viewModel, lang = lang)

            com.example.ui.components.SecureLicenseManagerCard(
                viewModel = viewModel,
                lang = lang,
                onActivationSuccess = {
                    Toast.makeText(
                        context,
                        Bilingual.tr(lang, "تم تفعيل الترخيص بنجاح!", "Licence activée avec succès!", "License activated successfully!"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }
}

/**
 * 6. Users and Cashiers Subscreen.
 */
@Composable
fun UsersSettingsSubScreen(
    viewModel: PosViewModel,
    lang: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        SettingsSubHeader(
            title = Bilingual.tr(lang, "إدارة المستخدمين", "Gestion Utilisateurs", "User Management"),
            onBack = onBack
        )

        Box(modifier = Modifier.fillMaxSize()) {
            UsersScreen(
                viewModel = viewModel,
                lang = lang,
                onNavigate = { onBack() }
            )
        }
    }
}
