package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserEntity
import com.example.ui.theme.Bilingual

/**
 * Reusable AppBottomNavigationBar matching image_d2cf105f.png:
 * [ الرئيسية | فاتورة بيع | التقارير | المنتجات | الإعدادات ]
 * Supports safe area navigationBarsPadding for devices with 3-button or gesture navigation.
 */
@Composable
fun AppBottomNavigationBar(
    activeScreen: String,
    onNavigate: (String) -> Unit,
    currentUser: UserEntity?,
    lang: String = "AR",
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        containerColor = Color(0xFF070D1E),
        tonalElevation = 8.dp
    ) {
        val navItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = Color(0xFF38BDF8),
            unselectedIconColor = Color(0xFF94A3B8),
            unselectedTextColor = Color(0xFF94A3B8),
            indicatorColor = Color(0xFF1E293B)
        )

        // 1. الرئيسية (Home)
        val isHomeSelected = activeScreen == "DASHBOARD"
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Filled.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    text = Bilingual.tr(lang, "الرئيسية", "Accueil", "Home"),
                    fontSize = 10.sp,
                    fontWeight = if (isHomeSelected) FontWeight.Bold else FontWeight.Normal
                )
            },
            selected = isHomeSelected,
            colors = navItemColors,
            onClick = { onNavigate("DASHBOARD") }
        )

        // 2. فاتورة بيع (POS / Cart)
        val isSellAllowed = currentUser?.hasSellAccess() == true
        if (isSellAllowed) {
            val isPosSelected = activeScreen == "POS" || activeScreen == "CART"
            NavigationBarItem(
                icon = {
                    Icon(
                        Icons.Filled.ShoppingCart,
                        contentDescription = "POS",
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = Bilingual.tr(lang, "فاتورة بيع", "Vente", "Sale"),
                        fontSize = 10.sp,
                        fontWeight = if (isPosSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = isPosSelected,
                colors = navItemColors,
                onClick = { onNavigate("POS") }
            )
        }

        // 3. التقارير (Reports)
        val isReportsAllowed = currentUser?.hasReportsAccess() == true
        if (isReportsAllowed) {
            val isReportsSelected = activeScreen == "REPORTS" || activeScreen.startsWith("REPORTS_")
            NavigationBarItem(
                icon = {
                    Icon(
                        Icons.Filled.BarChart,
                        contentDescription = "Reports",
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = Bilingual.tr(lang, "التقارير", "Rapports", "Reports"),
                        fontSize = 10.sp,
                        fontWeight = if (isReportsSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = isReportsSelected,
                colors = navItemColors,
                onClick = { onNavigate("REPORTS") }
            )
        }

        // 4. المنتجات (Products / Inventory)
        val isInventoryAllowed = currentUser?.hasInventoryAccess() == true
        if (isInventoryAllowed) {
            val isInventorySelected = activeScreen == "INVENTORY" || activeScreen == "STOCKTAKING" || activeScreen == "PRODUCT_FAMILIES" || activeScreen == "CATEGORIES"
            NavigationBarItem(
                icon = {
                    Icon(
                        Icons.Filled.Inventory,
                        contentDescription = "Products",
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = Bilingual.tr(lang, "المنتجات", "Produits", "Products"),
                        fontSize = 10.sp,
                        fontWeight = if (isInventorySelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = isInventorySelected,
                colors = navItemColors,
                onClick = { onNavigate("INVENTORY") }
            )
        }

        // 5. الإعدادات (Settings)
        val isSettingsAllowed = currentUser?.hasSettingsAccess() == true
        if (isSettingsAllowed) {
            val isSettingsSelected = activeScreen == "SETTINGS"
            NavigationBarItem(
                icon = {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = Bilingual.tr(lang, "الإعدادات", "Paramètres", "Settings"),
                        fontSize = 10.sp,
                        fontWeight = if (isSettingsSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = isSettingsSelected,
                colors = navItemColors,
                onClick = { onNavigate("SETTINGS") }
            )
        }
    }
}
