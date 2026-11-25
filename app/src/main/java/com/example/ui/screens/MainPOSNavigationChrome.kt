package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.pos.SuspendedSalesDialog
import com.example.ui.theme.*
import com.example.ui.viewmodels.PosViewModel

@Composable
fun POSMasterHeader(titleKey: String, lang: String, viewModel: PosViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.3f)))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val user = viewModel.currentUser.value
            val initial = if (user != null && user.username.isNotEmpty()) {
                user.username.first().uppercase()
            } else {
                "P"
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        brush = Brush.linearGradient(colors = listOf(POSPrimary, POSTertiary)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (lang == "AR") "أ" else initial,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Column {
                Text(
                    text = Bilingual.tr(lang, "مرحباً بك،", "Bienvenue,", "Welcome,"),
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (user != null) {
                        "${user.username} (${if (user.isManager) Bilingual.get("manager", lang) else Bilingual.get("cashier", lang)})"
                    } else {
                        Bilingual.get(titleKey, lang)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { viewModel.toggleLanguage() },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF334155).copy(alpha = 0.8f), CircleShape)
            ) {
                Text(text = lang, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.logout() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = POSDanger, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun Modifier.size(size: Dp) = this.size(size, size)

@Composable
fun SidebarNavigationRail(
    activeScreen: String,
    onScreenSelected: (String) -> Unit,
    lang: String,
    viewModel: PosViewModel,
    modifier: Modifier = Modifier
) {
    var isCollapsed by remember { mutableStateOf(false) }
    var showSuspendedSales by remember { mutableStateOf(false) }

    val railWidth by animateDpAsState(targetValue = if (isCollapsed) 80.dp else 220.dp, label = "railWidth")

    Column(
        modifier = modifier
            .width(railWidth)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            if (!isCollapsed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = POSPrimary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("M-POS", fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
            IconButton(onClick = { isCollapsed = !isCollapsed }, modifier = Modifier.size(48.dp)) {
                Icon(if (isCollapsed) Icons.Filled.Menu else Icons.Filled.MenuOpen, contentDescription = "Toggle Sidebar", tint = Color.LightGray)
            }
        }

        val elements = remember {
            listOf(
                "POS" to Icons.Filled.AddShoppingCart,
                "DASHBOARD" to Icons.Filled.Dashboard,
                "INVENTORY" to Icons.Filled.Inventory,
                "CLIENTS" to Icons.Filled.People,
                "SUPPLIERS" to Icons.Filled.LocalShipping,
                "PAYMENTS" to Icons.Filled.Payments,
                "PURCHASES" to Icons.Filled.Storefront,
                "EXPENSES" to Icons.Filled.Receipt,
                "REPORTS" to Icons.Filled.Analytics,
                "SETTINGS" to Icons.Filled.Settings
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(elements, key = { it.first }) { (screen, icon) ->
                val active = activeScreen == screen
                Surface(
                    color = if (active) POSPrimary else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onScreenSelected(screen) },
                    border = if (active) null else BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isCollapsed) Arrangement.Center else Arrangement.Start
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        if (!isCollapsed) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = Bilingual.get(screen.lowercase() + "_btn", lang).split(" ")[0],
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isCollapsed) {
            Button(
                onClick = { showSuspendedSales = true },
                colors = ButtonDefaults.buttonColors(containerColor = POSWarning),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    Bilingual.tr(lang, "فواتير انتظار", "Attente", "Held"),
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        } else {
            IconButton(
                onClick = { showSuspendedSales = true },
                modifier = Modifier.background(POSWarning, RoundedCornerShape(8.dp)).size(48.dp)
            ) {
                Icon(Icons.Filled.ReceiptLong, contentDescription = "Held invoices", tint = Color.Black)
            }
        }
    }

    if (showSuspendedSales) {
        SuspendedSalesDialog(viewModel, lang, onDismiss = { showSuspendedSales = false })
    }
}

@Composable
fun BottomNavigationBar(
    activeScreen: String,
    onScreenSelected: (String) -> Unit,
    lang: String,
    viewModel: PosViewModel
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier
            .graphicsLayer {
                clip = true
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            }
            .border(
                border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        val items = listOf(
            Triple("DASHBOARD", Icons.Filled.Home, Bilingual.tr(lang, "نظرة عامة", "Accueil", "Home")),
            Triple("POS", Icons.Filled.ShoppingCart, Bilingual.tr(lang, "البيع", "Vente", "Sale")),
            Triple("INVENTORY", Icons.Filled.Inventory, Bilingual.tr(lang, "المنتجات", "Produits", "Products")),
            Triple("QUICK", Icons.Filled.Menu, Bilingual.tr(lang, "المزيد", "Plus", "More"))
        )

        items.forEach { (screen, icon, label) ->
            val active = activeScreen == screen
            val tintColor = if (active) POSPrimary else Color(0xFF94A3B8)
            NavigationBarItem(
                selected = active,
                onClick = {
                    if (screen == "QUICK") {
                        onScreenSelected("SETTINGS")
                    } else {
                        onScreenSelected(screen)
                    }
                },
                icon = { Icon(icon, contentDescription = label, tint = tintColor, modifier = Modifier.size(24.dp)) },
                label = { Text(label, fontSize = 10.sp, color = tintColor, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = POSPrimary,
                    unselectedIconColor = Color(0xFF94A3B8),
                    selectedTextColor = POSPrimary,
                    unselectedTextColor = Color(0xFF94A3B8)
                )
            )
        }
    }
}

data class NavItem(
    val title: String,
    val icon: ImageVector,
    val route: String? = null,
    val subItems: List<NavItem> = emptyList()
)

@Composable
fun ExpandableNavGroup(
    item: NavItem,
    activeScreen: String,
    onScreenSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isActiveGroup = item.subItems.any { it.route == activeScreen } || item.route == activeScreen

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = if (isActiveGroup && item.subItems.isEmpty()) POSPrimary else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (item.subItems.isNotEmpty()) {
                        expanded = !expanded
                    } else if (item.route != null) {
                        onScreenSelected(item.route)
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(item.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = if (isActiveGroup) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (item.subItems.isNotEmpty()) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp)) {
                item.subItems.forEach { subItem ->
                    val isSubActive = activeScreen == subItem.route
                    Text(
                        text = subItem.title,
                        fontSize = 12.sp,
                        fontWeight = if (isSubActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSubActive) POSPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { subItem.route?.let { onScreenSelected(it) } }
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                }
            }
        }
    }
}
