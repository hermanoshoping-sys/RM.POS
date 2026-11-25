package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 1. 3D Supermarket Storefront Icon ("بيانات السوير مارشي"):
 * Blue & white striped awning, building facade, door with shopping cart badge, green gear badge
 */
@Composable
fun SupermarketStoreIcon3D() {
    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft bottom drop shadow
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(width = 86.dp, height = 14.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x30000000), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        // Store Building Facade
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 8.dp)
                .size(width = 72.dp, height = 54.dp)
                .shadow(6.dp, RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD))
                    ),
                    RoundedCornerShape(8.dp)
                )
                .border(1.dp, Color(0xFF7DD3FC), RoundedCornerShape(8.dp))
        ) {
            // Store Door
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = 28.dp, height = 36.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                        ),
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                    )
            )

            // Shopping Cart Badge on the door
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .shadow(3.dp, CircleShape)
                    .background(Color(0xFF2563EB), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Striped Awning (Roof) with Blue and White scallops
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 14.dp)
                .size(width = 86.dp, height = 28.dp)
                .shadow(8.dp, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB))
                    ),
                    RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
                )
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                val stripeColors = listOf(
                    Color(0xFF3B82F6), Color(0xFFFFFFFF),
                    Color(0xFF3B82F6), Color(0xFFFFFFFF),
                    Color(0xFF3B82F6), Color(0xFFFFFFFF),
                    Color(0xFF3B82F6)
                )
                stripeColors.forEach { col ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(col)
                    )
                }
            }
        }

        // Green Grass Foundation Base
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-2).dp)
                .size(width = 82.dp, height = 6.dp)
                .background(Color(0xFF10B981), RoundedCornerShape(3.dp))
        )

        // Green Gear Badge (Bottom-Right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-2).dp)
                .size(26.dp)
                .shadow(4.dp, CircleShape)
                .background(Color(0xFF10B981), CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 2. 3D Invoices & Documents Icon ("الفواتير والوثائق"):
 * Blue back sheet, front white paper with spreadsheet table grid, green gear badge
 */
@Composable
fun InvoicesDocsIcon3D() {
    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft bottom drop shadow
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(width = 80.dp, height = 14.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x30000000), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        // Back blue sheet
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-10).dp, y = (-2).dp)
                .size(width = 54.dp, height = 70.dp)
                .shadow(4.dp, RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                    ),
                    RoundedCornerShape(8.dp)
                )
        )

        // Front white sheet with folded top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 4.dp, y = 2.dp)
                .size(width = 58.dp, height = 74.dp)
                .shadow(6.dp, RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top blue header bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(6.dp)
                        .background(Color(0xFF38BDF8), RoundedCornerShape(3.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(4.dp)
                        .background(Color(0xFF94A3B8), RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Spreadsheet table grid lines
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(4.dp))
                        .padding(2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(modifier = Modifier.size(width = 12.dp, height = 5.dp).background(Color(0xFF60A5FA), RoundedCornerShape(1.dp)))
                                Box(modifier = Modifier.size(width = 14.dp, height = 5.dp).background(Color(0xFF93C5FD), RoundedCornerShape(1.dp)))
                                Box(modifier = Modifier.size(width = 12.dp, height = 5.dp).background(Color(0xFF3B82F6), RoundedCornerShape(1.dp)))
                            }
                        }
                    }
                }
            }
        }

        // Green Gear Badge (Bottom-Right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-6).dp)
                .size(28.dp)
                .shadow(4.dp, CircleShape)
                .background(Color(0xFF10B981), CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/**
 * 3. 3D Printer Icon ("الطباعة"):
 * Glossy royal blue printer with paper emerging, feed tray, green gear badge
 */
@Composable
fun PrinterIcon3D() {
    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft bottom drop shadow
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(width = 86.dp, height = 14.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x30000000), Color.Transparent)
                    ),
                    CircleShape
                )
        )

        // Top Paper sticking out from input slot
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 12.dp)
                .size(width = 44.dp, height = 24.dp)
                .shadow(2.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(Color.White, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(2.5.dp).background(Color(0xFF93C5FD)))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.5.dp).background(Color(0xFF93C5FD)))
            }
        }

        // Main Printer Blue Body
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 6.dp)
                .size(width = 82.dp, height = 48.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                    ),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, Color(0xFF60A5FA), RoundedCornerShape(12.dp))
        ) {
            // Center paper exit slot
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 4.dp)
                    .size(width = 54.dp, height = 14.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
            )

            // Power LED
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 8.dp)
                    .size(6.dp)
                    .background(Color(0xFF38BDF8), CircleShape)
            )
        }

        // Printed Output Paper coming out of front
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(width = 46.dp, height = 26.dp)
                .shadow(4.dp, RoundedCornerShape(6.dp))
                .background(Color.White, RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(3.5.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(2.5.dp).background(Color(0xFF60A5FA)))
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(2.5.dp).background(Color(0xFF93C5FD)))
                Box(modifier = Modifier.fillMaxWidth(0.5f).height(2.5.dp).background(Color(0xFF60A5FA)))
            }
        }

        // Green Gear Badge (Bottom-Right)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-4).dp)
                .size(26.dp)
                .shadow(4.dp, CircleShape)
                .background(Color(0xFF10B981), CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}



