package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel

/**
 * Data representation for the 14 main dashboard navigation tiles.
 */
data class HomeNavTile(
    val id: String,
    val titleAr: String,
    val titleFr: String,
    val titleEn: String,
    val route: String,
    val type: HomeTileType
)

enum class HomeTileType {
    HOME,
    POS,
    SUPPLIER_INVOICES,
    PRODUCTS_INVENTORY,
    PURCHASE_HISTORY,
    SALES_HISTORY,
    CUSTOMERS,
    SUPPLIERS,
    EXPENSES,
    REPORTS,
    SETTINGS,
    USERS,
    PRODUCT_FAMILIES,
    PRODUCT_SALES_SEARCH,
    PAYMENTS,
    STOCKTAKING
}

/**
 * Main Home Screen displaying top navigation bar and 14-item navigation grid.
 */
@Composable
fun MainHomeScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showNotificationDialog by remember { mutableStateOf(false) }

    val lowStockCount by viewModel.lowStockAlertCount.collectAsStateWithLifecycle()
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()
    val todaySalesCount by viewModel.invoiceTodayCount.collectAsStateWithLifecycle()
    val todaySalesSum by viewModel.salesTodaySum.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val activeStocktakingCount by viewModel.stocktakingViewModel.activeInventoriesCount.collectAsStateWithLifecycle()

    val allTiles = remember {
        listOf(
            HomeNavTile("purchases", "فواتير الموردين", "Factures Fournisseurs", "Supplier Invoices", "PURCHASES", HomeTileType.SUPPLIER_INVOICES),
            HomeNavTile("pos", "نقطة البيع", "Caisse POS", "POS", "POS", HomeTileType.POS),
            HomeNavTile("product_families", "عائلة المنتجات", "Familles Produits", "Product Families", "PRODUCT_FAMILIES", HomeTileType.PRODUCT_FAMILIES),
            HomeNavTile("inventory", "المنتجات والمخزون", "Stock & Produits", "Inventory & Stock", "INVENTORY", HomeTileType.PRODUCTS_INVENTORY),
            HomeNavTile("purchase_invoices", "سجل فواتير الشراء", "Achats", "Purchase History", "PURCHASE_INVOICES", HomeTileType.PURCHASE_HISTORY),
            HomeNavTile("sales_invoices", "سجل فواتير المبيعات", "Ventes", "Sales History", "SALES_INVOICES", HomeTileType.SALES_HISTORY),
            HomeNavTile("suppliers", "الموردون", "Fournisseurs", "Suppliers", "SUPPLIERS", HomeTileType.SUPPLIERS),
            HomeNavTile("clients", "العملاء", "Clients", "Customers", "CLIENTS", HomeTileType.CUSTOMERS),
            HomeNavTile("payments", "المدفوعات", "Paiements", "Payments", "PAYMENTS", HomeTileType.PAYMENTS),
            HomeNavTile("expenses", "المصاريف", "Dépenses", "Expenses", "EXPENSES", HomeTileType.EXPENSES),
            HomeNavTile("product_sales_search", "سجل بيع المنتجات", "Audit Ventes", "Product Sales Log", "PRODUCT_SALES_SEARCH", HomeTileType.PRODUCT_SALES_SEARCH),
            HomeNavTile("reports", "التقارير", "Rapports", "Reports", "REPORTS", HomeTileType.REPORTS),
            HomeNavTile("users", "المستخدمون", "Utilisateurs", "Users", "USERS", HomeTileType.USERS),
            HomeNavTile("settings", "الإعدادات", "Paramètres", "Settings", "SETTINGS", HomeTileType.SETTINGS),
            HomeNavTile("stocktaking", "الجرد والمخزون", "Inventaire", "Stocktaking", "STOCKTAKING", HomeTileType.STOCKTAKING)
        )
    }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val allowedTiles = remember(allTiles, currentUser) {
        val user = currentUser
        if (user == null) {
            emptyList()
        } else {
            allTiles.filter { tile ->
                when (tile.type) {
                    HomeTileType.POS, HomeTileType.SALES_HISTORY -> user.hasSellAccess()
                    HomeTileType.SUPPLIER_INVOICES, HomeTileType.PURCHASE_HISTORY -> user.hasPurchaseAccess()
                    HomeTileType.PRODUCTS_INVENTORY, HomeTileType.PRODUCT_FAMILIES, HomeTileType.STOCKTAKING -> user.hasInventoryAccess()
                    HomeTileType.CUSTOMERS -> user.hasClientsAccess()
                    HomeTileType.SUPPLIERS -> user.hasSuppliersAccess()
                    HomeTileType.EXPENSES -> user.hasExpensesAccess()
                    HomeTileType.PAYMENTS -> user.hasPaymentsAccess()
                    HomeTileType.REPORTS, HomeTileType.PRODUCT_SALES_SEARCH -> user.hasReportsAccess()
                    HomeTileType.USERS -> user.hasUsersAccess()
                    HomeTileType.SETTINGS -> user.hasSettingsAccess()
                    else -> true
                }
            }
        }
    }

    val filteredTiles = remember(searchQuery, allowedTiles) {
        if (searchQuery.isBlank()) {
            allowedTiles
        } else {
            val q = searchQuery.trim().lowercase()
            allowedTiles.filter {
                it.titleAr.contains(q, ignoreCase = true) ||
                    it.titleFr.lowercase().contains(q) ||
                    it.titleEn.lowercase().contains(q) ||
                    it.route.lowercase().contains(q)
            }
        }
    }

    val effectiveLowStockCount = lowStockCount ?: lowStockProducts.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
    ) {
        HomeTopBar(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onOpenDrawer = {
                if (currentUser?.hasSettingsAccess() == true) {
                    onOpenDrawer()
                } else {
                    Toast.makeText(
                        context,
                        Bilingual.tr(
                            lang,
                            "عذراً، الوصول للإعدادات محجوب لحسابك. مقتصر على المدير.",
                            "Accès aux paramètres réservé au gérant.",
                            "Settings access is restricted to managers."
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            hasNotifications = effectiveLowStockCount > 0,
            onNotificationClick = { showNotificationDialog = true },
            lang = lang,
            hasSettingsAccess = currentUser?.hasSettingsAccess() == true
        )

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp)
            ) {
                items(
                    items = filteredTiles,
                    key = { it.id },
                    span = { tile ->
                        if (
                            tile.type == HomeTileType.STOCKTAKING &&
                            filteredTiles.lastOrNull()?.id == tile.id &&
                            filteredTiles.size % 2 == 1
                        ) {
                            GridItemSpan(maxLineSpan)
                        } else {
                            GridItemSpan(1)
                        }
                    },
                    contentType = { "home_tile" }
                ) { tile ->
                    HomeGridCard(
                        tile = tile,
                        lang = lang,
                        badgeCount = when (tile.type) {
                            HomeTileType.PRODUCTS_INVENTORY -> effectiveLowStockCount
                            HomeTileType.STOCKTAKING -> activeStocktakingCount
                            else -> 0
                        },
                        badgeLabel = if (tile.type == HomeTileType.STOCKTAKING && activeStocktakingCount > 0) {
                            Bilingual.tr(lang, "نشط $activeStocktakingCount", "$activeStocktakingCount actif")
                        } else null,
                        badgeColor = if (tile.type == HomeTileType.STOCKTAKING) Color(0xFF2563EB) else Color(0xFFDC2626),
                        onClick = {
                            if (tile.route != "DASHBOARD") {
                                onNavigate(tile.route)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showNotificationDialog) {
        HomeNotificationsDialog(
            lang = lang,
            lowStockCount = effectiveLowStockCount,
            todaySalesCount = todaySalesCount ?: 0,
            todaySalesSum = todaySalesSum ?: 0.0,
            currency = currency,
            onDismiss = { showNotificationDialog = false },
            onOpenInventory = {
                showNotificationDialog = false
                viewModel.requestOpenLowStockFilter()
                onNavigate("INVENTORY")
            }
        )
    }
}

@Composable
private fun HomeGridCard(
    tile: HomeNavTile,
    lang: String,
    badgeCount: Int = 0,
    badgeLabel: String? = null,
    badgeColor: Color = Color(0xFFDC2626),
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0x15000000),
                spotColor = Color(0x200F2942)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("home_tile_${tile.id}"),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (badgeCount > 0) badgeColor.copy(alpha = 0.45f) else Color(0xFFE5EDF5)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Render3DIcon(tile.type)
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = Bilingual.tr(lang, tile.titleAr, tile.titleFr, tile.titleEn),
                    color = Color(0xFF0B2545),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (badgeCount > 0) {
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(bottomStart = 10.dp, topEnd = 14.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .testTag("home_tile_${tile.id}_badge")
                ) {
                    Text(
                        text = badgeLabel ?: Bilingual.tr(lang, "نواقص $badgeCount", "$badgeCount bas", "$badgeCount low"),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
