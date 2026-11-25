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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 4. 3D Backup & Cloud Icon ("نسخ البيانات"):
 * Royal blue cylindrical database disks stack, 3D white cloud, green up & blue down arrows
 */
@Composable
fun DataBackupIcon3D() {
    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(width = 82.dp, height = 14.dp)
                .background(
                    Brush.radialGradient(colors = listOf(Color(0x30000000), Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 10.dp, y = (-2).dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 18.dp)
                        .shadow(4.dp, RoundedCornerShape(10.dp))
                        .background(
                            Brush.verticalGradient(colors = listOf(Color(0xFF3B82F6), Color(0xFF1E40AF))),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, Color(0xFF60A5FA), RoundedCornerShape(10.dp))
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-6).dp, y = (-8).dp)
                .size(width = 62.dp, height = 40.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(colors = listOf(Color.White, Color(0xFFF1F5F9))),
                    RoundedCornerShape(20.dp)
                )
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = "Upload",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(20.dp)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = "Download",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 5. 3D Security Shield Icon ("الأمان"):
 * Vibrant royal blue protective shield with light cyan border and white glowing padlock in center
 */
@Composable
fun SecurityIcon3D() {
    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(width = 76.dp, height = 14.dp)
                .background(
                    Brush.radialGradient(colors = listOf(Color(0x30000000), Color.Transparent)),
                    CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(width = 74.dp, height = 86.dp)
                .shadow(8.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 38.dp, bottomEnd = 38.dp))
                .background(
                    Brush.verticalGradient(colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB))),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 38.dp, bottomEnd = 38.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 62.dp, height = 74.dp)
                    .background(
                        Brush.verticalGradient(colors = listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A))),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 32.dp, bottomEnd = 32.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 14.dp)
                            .border(3.5.dp, Color.White, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 26.dp, height = 20.dp)
                            .shadow(2.dp, RoundedCornerShape(4.dp))
                            .background(Color.White, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 7.dp)
                                .background(Color(0xFF1E3A8A), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

/**
 * 6. 3D Users Avatars Icon ("المستخدمون"):
 * Front royal blue user avatar, green back avatar peeking to the right, blue gear badge
 */
@Composable
fun UsersAvatarsIcon3D() {
    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .size(width = 82.dp, height = 14.dp)
                .background(
                    Brush.radialGradient(colors = listOf(Color(0x30000000), Color.Transparent)),
                    CircleShape
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-14).dp, y = (-4).dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .shadow(3.dp, CircleShape)
                        .background(
                            Brush.verticalGradient(colors = listOf(Color(0xFF34D399), Color(0xFF059669))),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 22.dp)
                        .shadow(3.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(
                            Brush.verticalGradient(colors = listOf(Color(0xFF34D399), Color(0xFF059669))),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 10.dp, y = 6.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .shadow(6.dp, CircleShape)
                        .background(
                            Brush.verticalGradient(colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB))),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 28.dp)
                        .shadow(6.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(
                            Brush.verticalGradient(colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))),
                            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                        )
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-2).dp)
                .size(26.dp)
                .shadow(4.dp, CircleShape)
                .background(Color(0xFF2563EB), CircleShape)
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
