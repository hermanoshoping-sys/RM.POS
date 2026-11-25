package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-fidelity 3D-styled icon renderer for home navigation tiles.
 */
@Composable
internal fun Render3DIcon(type: HomeTileType) {
    when (type) {
        HomeTileType.HOME -> HouseIcon3D()
        HomeTileType.POS -> PosTerminalIcon3D()
        HomeTileType.SUPPLIER_INVOICES -> SupplierInvoicesIcon3D()
        HomeTileType.PRODUCTS_INVENTORY -> ProductsInventoryIcon3D()
        HomeTileType.PURCHASE_HISTORY -> PurchaseHistoryIcon3D()
        HomeTileType.SALES_HISTORY -> SalesHistoryIcon3D()
        HomeTileType.CUSTOMERS -> CustomersIcon3D()
        HomeTileType.SUPPLIERS -> SuppliersIcon3D()
        HomeTileType.EXPENSES -> ExpensesIcon3D()
        HomeTileType.REPORTS -> ReportsIcon3D()
        HomeTileType.SETTINGS -> SettingsIcon3D()
        HomeTileType.USERS -> UsersIcon3D()
        HomeTileType.PRODUCT_FAMILIES -> ProductFamiliesIcon3D()
        HomeTileType.PRODUCT_SALES_SEARCH -> ProductSalesSearchIcon3D()
        HomeTileType.PAYMENTS -> PaymentsIcon3D()
        HomeTileType.STOCKTAKING -> StocktakingIcon3D()
    }
}

@Composable
internal fun StocktakingIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 42.dp, height = 8.dp)
                .background(Color(0x18000000), CircleShape)
        )
        // Clipboard body
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 40.dp)
                .align(Alignment.Center)
                .offset(x = (-2).dp, y = 1.dp)
                .shadow(3.dp, RoundedCornerShape(7.dp))
                .clip(RoundedCornerShape(7.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0F766E), Color(0xFF115E59))))
                .border(1.2.dp, Color(0xFF2DD4BF), RoundedCornerShape(7.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.size(4.dp).background(Color(0xFF10B981), CircleShape))
                        Box(Modifier.weight(1f).height(2.5.dp).background(Color(0xFF334155), RoundedCornerShape(1.dp)))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.size(4.dp).background(Color(0xFF10B981), CircleShape))
                        Box(Modifier.weight(0.8f).height(2.5.dp).background(Color(0xFF64748B), RoundedCornerShape(1.dp)))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.size(4.dp).background(Color(0xFFF59E0B), CircleShape))
                        Box(Modifier.weight(0.65f).height(2.5.dp).background(Color(0xFF94A3B8), RoundedCornerShape(1.dp)))
                    }
                    // Mini barcode bars
                    Row(
                        modifier = Modifier.fillMaxWidth().height(5.dp).padding(top = 1.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Box(Modifier.fillMaxHeight().width(2.dp).background(Color(0xFF0F172A)))
                        Box(Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF0F172A)))
                        Box(Modifier.fillMaxHeight().width(2.5.dp).background(Color(0xFF0F172A)))
                        Box(Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF0F172A)))
                        Box(Modifier.fillMaxHeight().width(2.dp).background(Color(0xFF0F172A)))
                    }
                }
            }
        }
        // Top metallic clip
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 7.dp)
                .align(Alignment.TopCenter)
                .offset(x = (-2).dp, y = 3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706))))
                .border(0.8.dp, Color.White, RoundedCornerShape(3.dp))
        )
        // Emerald verified badge
        Surface(
            shape = CircleShape,
            color = Color(0xFF10B981),
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-3).dp, y = (-3).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.FactCheck, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
internal fun PaymentsIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 42.dp, height = 8.dp)
                .background(Color(0x15000000), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFB923C), Color(0xFFEA580C))))
                .border(1.2.dp, Color(0xFFFDBA74), RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 5.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF9A3412)))
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(width = 7.dp, height = 5.dp).background(Color(0xFFFEF08A), RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.size(width = 16.dp, height = 3.dp).background(Color(0xFFFFEDD5), RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

@Composable
internal fun ProductSalesSearchIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 44.dp, height = 8.dp)
                .background(Color(0x15000000), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 40.dp)
                .graphicsLayer { rotationZ = -6f }
                .shadow(4.dp, RoundedCornerShape(4.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0))), RoundedCornerShape(4.dp))
                .padding(4.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF0284C7), RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(2.5.dp).background(Color(0xFF94A3B8), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.5.dp).background(Color(0xFF94A3B8), RoundedCornerShape(1.dp)))
                Box(modifier = Modifier.fillMaxWidth().height(2.5.dp).background(Color(0xFF10B981), RoundedCornerShape(1.dp)))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 2.dp)
                .size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF0284C7),
                shadowElevation = 4.dp,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
internal fun ProductFamiliesIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 44.dp, height = 8.dp)
                .background(Color(0x15000000), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 32.dp)
                .align(Alignment.Center)
                .offset(y = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))))
                .border(1.dp, Color(0xFFA78BFA), RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp, topStart = 4.dp, topEnd = 4.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF7C3AED), Color(0xFF5B21B6))))
                    .border(0.5.dp, Color(0xFFC4B5FD), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp, topStart = 4.dp, topEnd = 4.dp))
            )
        }
        Box(
            modifier = Modifier
                .size(width = 13.dp, height = 18.dp)
                .align(Alignment.TopStart)
                .offset(x = 8.dp, y = 6.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(Color(0xFF10B981))
                .border(0.5.dp, Color(0xFF6EE7B7), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        )
        Box(
            modifier = Modifier
                .size(width = 13.dp, height = 22.dp)
                .align(Alignment.TopCenter)
                .offset(y = 2.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(Color(0xFF38BDF8))
                .border(0.5.dp, Color.White, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        )
        Box(
            modifier = Modifier
                .size(width = 13.dp, height = 18.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = 6.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(Color(0xFFF59E0B))
                .border(0.5.dp, Color(0xFFFDE68A), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.Center)
                .offset(y = 4.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0xFF8B5CF6), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Category, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
internal fun HouseIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 44.dp, height = 8.dp)
                .background(Color(0x15000000), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(38.dp, 32.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
                .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFEBF5FF), Color(0xFFD0E7FE))))
                .border(1.dp, Color(0xFF93C5FD), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.TopStart)
                    .padding(start = 3.dp, top = 3.dp)
                    .background(Color(0xFF38BDF8), RoundedCornerShape(2.dp))
                    .border(0.5.dp, Color.White, RoundedCornerShape(2.dp))
            )
            Box(
                modifier = Modifier
                    .size(width = 11.dp, height = 18.dp)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(Color(0xFF2563EB))
            ) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .align(Alignment.CenterEnd)
                        .padding(end = 1.dp)
                        .background(Color(0xFFFBBF24), CircleShape)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(46.dp, 22.dp)
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 10.dp)
                    .align(Alignment.TopEnd)
                    .padding(end = 6.dp)
                    .background(Color(0xFF1D4ED8), RoundedCornerShape(2.dp))
            )
            Icon(Icons.Filled.Roofing, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .size(12.dp)
                .align(Alignment.BottomStart)
                .offset(x = 1.dp, y = (-1).dp)
                .background(Brush.radialGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A))), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-1).dp)
                .background(Brush.radialGradient(listOf(Color(0xFF4ADE80), Color(0xFF15803D))), CircleShape)
        )
    }
}

@Composable
internal fun PosTerminalIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.align(Alignment.BottomCenter).size(width = 30.dp, height = 6.dp).background(Color(0xFF1E3A8A), RoundedCornerShape(3.dp)))
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(width = 12.dp, height = 8.dp).background(Color(0xFF2563EB), RoundedCornerShape(2.dp)))
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 28.dp)
                .align(Alignment.Center)
                .offset(x = (-4).dp, y = (-3).dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))))
                .border(1.5.dp, Color(0xFF3B82F6), RoundedCornerShape(6.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8))), RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 10.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = (-6).dp)
                        .background(Color.White, RoundedCornerShape(2.dp))
                        .border(0.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(1.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        Box(Modifier.fillMaxWidth(0.8f).height(1.dp).background(Color(0xFF94A3B8)))
                        Box(Modifier.fillMaxWidth(0.6f).height(1.dp).background(Color(0xFF94A3B8)))
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 28.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 2.dp, y = (-2).dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 10.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6))), RoundedCornerShape(4.dp))
            ) {
                Box(modifier = Modifier.size(width = 3.dp, height = 8.dp).align(Alignment.CenterStart).background(Color(0xFFFACC15), RoundedCornerShape(1.dp)))
            }
            Box(
                modifier = Modifier
                    .size(width = 7.dp, height = 18.dp)
                    .align(Alignment.BottomCenter)
                    .offset(x = 1.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1E40AF), Color(0xFF0F172A))), RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
internal fun SupplierInvoicesIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 36.dp)
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.fillMaxWidth(0.9f).height(3.dp).background(Color(0xFF60A5FA), RoundedCornerShape(1.dp)))
                Box(Modifier.fillMaxWidth(0.7f).height(2.dp).background(Color(0xFF93C5FD), RoundedCornerShape(1.dp)))
                Box(Modifier.fillMaxWidth(0.8f).height(2.dp).background(Color(0xFFBFDBFE), RoundedCornerShape(1.dp)))
                Box(Modifier.fillMaxWidth(0.5f).height(2.dp).background(Color(0xFFBFDBFE), RoundedCornerShape(1.dp)))
            }
        }
        Box(
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = 4.dp)
                .background(Brush.radialGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706))), CircleShape)
                .border(1.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 22.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = (-2).dp)
        ) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.size(width = 22.dp, height = 18.dp).clip(RoundedCornerShape(topStart = 4.dp)).background(Color(0xFF2563EB)))
                Box(modifier = Modifier.size(width = 14.dp, height = 14.dp).clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 2.dp)).background(Color(0xFFF59E0B))) {
                    Box(modifier = Modifier.size(6.dp).align(Alignment.TopStart).padding(top = 2.dp, start = 2.dp).background(Color(0xFFE0F2FE), RoundedCornerShape(1.dp)))
                }
            }
            Box(modifier = Modifier.size(6.dp).align(Alignment.BottomStart).offset(x = 4.dp, y = 2.dp).background(Color(0xFF1E293B), CircleShape))
            Box(modifier = Modifier.size(6.dp).align(Alignment.BottomEnd).offset(x = (-4).dp, y = 2.dp).background(Color(0xFF1E293B), CircleShape))
        }
    }
}

@Composable
internal fun ProductsInventoryIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 26.dp, height = 32.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0284C7))
                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(4.dp))
        ) {
            Box(modifier = Modifier.size(width = 20.dp, height = 24.dp).align(Alignment.Center).background(Color.White, RoundedCornerShape(2.dp))) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(12.dp).align(Alignment.Center))
            }
        }
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 26.dp)
                .align(Alignment.BottomStart)
                .offset(x = 2.dp, y = (-2).dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = 22.dp)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706))))
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center).background(Color(0xFFFEF08A)))
            }
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 16.dp)
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF59E0B), Color(0xFFB45309))))
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.Center).background(Color(0xFFFEF08A)))
            }
        }
        Box(modifier = Modifier.size(18.dp).align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.fillMaxSize())
        }
    }
}
