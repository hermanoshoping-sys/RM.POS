package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils

/**
 * Deep Navy Top App Bar with settings button, capsule search field, and notification bell.
 */
@Composable
internal fun HomeTopBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    hasNotifications: Boolean,
    onNotificationClick: () -> Unit,
    lang: String,
    hasSettingsAccess: Boolean = true
) {
    Surface(
        color = Color(0xFF0C325B),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenDrawer)
                    .testTag("home_settings_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hasSettingsAccess) Color(0xFF174271) else Color(0xFF1E293B).copy(alpha = 0.6f))
                        .border(1.dp, if (hasSettingsAccess) Color(0xFF21548E) else Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasSettingsAccess) Icons.Filled.Settings else Icons.Filled.Lock,
                        contentDescription = if (hasSettingsAccess) "Settings" else "Locked",
                        tint = if (hasSettingsAccess) Color.White else Color.Gray,
                        modifier = Modifier.size(if (hasSettingsAccess) 24.dp else 20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF082240))
                    .border(1.dp, Color(0xFF174271), CircleShape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF86A5C9),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = Bilingual.tr(lang, "ابحث عن قسم أو وظيفة...", "Rechercher une fonction...", "Search section or feature..."),
                                color = Color(0xFF86A5C9),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_search_input")
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF86A5C9),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNotificationClick)
                    .testTag("home_notifications_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )

                if (hasNotifications) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(8.dp)
                            .background(Color(0xFFEF4444), CircleShape)
                            .border(1.5.dp, Color(0xFF0C325B), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeNotificationsDialog(
    lang: String,
    lowStockCount: Int,
    todaySalesCount: Int,
    todaySalesSum: Double,
    currency: String = "MAD",
    onDismiss: () -> Unit,
    onOpenInventory: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = Bilingual.tr(lang, "إشعارات المتجر والنظام", "Notifications du système", "System Notifications"),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (lowStockCount > 0) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenInventory)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "تنبيه: يوجد $lowStockCount منتجات أوشكت على النفاد بالمخزن",
                                    "Alerte: $lowStockCount produits proches de l'épuisement",
                                    "Alert: $lowStockCount products running low in stock"
                                ),
                                fontSize = 13.sp,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A))
                        Spacer(Modifier.width(8.dp))
                        val formattedSum = MoneyUtils.format(todaySalesSum, currency)
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "مبيعات اليوم: $todaySalesCount فاتورة بإجمالي $formattedSum",
                                "Ventes du jour: $todaySalesCount factures ($formattedSum)",
                                "Today's sales: $todaySalesCount invoices ($formattedSum)"
                            ),
                            fontSize = 13.sp,
                            color = Color(0xFF166534)
                        )
                    }
                }

                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, contentDescription = null, tint = Color(0xFF2563EB))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Bilingual.tr(
                                lang,
                                "قاعدة البيانات المحلية تعمل بكفاءة (بدون إنترنت)",
                                "Base de données locale active (Hors ligne)",
                                "Local database active (Offline-First)"
                            ),
                            fontSize = 13.sp,
                            color = Color(0xFF1E40AF)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text(Bilingual.tr(lang, "إغلاق", "Fermer", "Close"))
            }
        },
        containerColor = Color.White,
        titleContentColor = Color(0xFF0F172A),
        textContentColor = Color(0xFF334155)
    )
}
