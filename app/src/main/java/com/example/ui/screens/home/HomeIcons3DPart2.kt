package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PurchaseHistoryIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 40.dp)
                .align(Alignment.Center)
                .offset(y = (-2).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(2.dp, Color(0xFF60A5FA), RoundedCornerShape(8.dp))
        ) {
            Icon(
                imageVector = Icons.Filled.ShoppingCart,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(20.dp).align(Alignment.Center).offset(y = (-2).dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(2.dp)
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 4.dp)
                    .background(Color(0xFF93C5FD))
            )
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = 2.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A))), CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
internal fun SalesHistoryIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 40.dp)
                .align(Alignment.Center)
                .offset(y = (-2).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(2.dp, Color(0xFF60A5FA), RoundedCornerShape(8.dp))
        ) {
            Icon(
                imageVector = Icons.Filled.ShoppingCart,
                contentDescription = null,
                tint = Color(0xFFF97316),
                modifier = Modifier.size(20.dp).align(Alignment.Center).offset(y = (-2).dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(2.dp)
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 4.dp)
                    .background(Color(0xFF93C5FD))
            )
        }
        Box(
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = 2.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF38BDF8), Color(0xFF2563EB))), CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
internal fun CustomersIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(24.dp).align(Alignment.CenterStart).offset(x = 4.dp, y = 3.dp)) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.size(24.dp).align(Alignment.CenterEnd).offset(x = (-4).dp, y = 3.dp)) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.size(34.dp).align(Alignment.Center).offset(y = (-2).dp)) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun SuppliersIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(36.dp).align(Alignment.CenterStart).offset(x = 2.dp)) {
            Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.size(width = 30.dp, height = 20.dp).align(Alignment.CenterEnd).offset(x = 2.dp, y = 4.dp)) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.size(width = 18.dp, height = 16.dp).clip(RoundedCornerShape(topStart = 3.dp)).background(Color(0xFF3B82F6)))
                Box(modifier = Modifier.size(width = 12.dp, height = 13.dp).clip(RoundedCornerShape(topEnd = 3.dp)).background(Color(0xFFF59E0B)))
            }
            Box(modifier = Modifier.size(5.dp).align(Alignment.BottomStart).offset(x = 3.dp, y = 1.dp).background(Color(0xFF0F172A), CircleShape))
            Box(modifier = Modifier.size(5.dp).align(Alignment.BottomEnd).offset(x = (-3).dp, y = 1.dp).background(Color(0xFF0F172A), CircleShape))
        }
    }
}

@Composable
internal fun ExpensesIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 14.dp)
                .align(Alignment.TopCenter)
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A))))
                .border(0.5.dp, Color(0xFF15803D), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
        )
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 28.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-4).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))))
                .border(1.dp, Color(0xFF60A5FA), RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 4.dp)
                    .padding(top = 2.dp)
                    .background(Color(0xFF93C5FD))
            )
        }
        Box(
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-2).dp)
                .background(Brush.radialGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706))), CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ReportsIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 38.dp)
                .align(Alignment.Center)
                .offset(x = (-2).dp, y = (-2).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.5.dp, Color(0xFF60A5FA), RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                Box(modifier = Modifier.width(6.dp).fillMaxHeight(0.45f).background(Color(0xFFF97316), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                Box(modifier = Modifier.width(6.dp).fillMaxHeight(0.85f).background(Color(0xFF22C55E), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                Box(modifier = Modifier.width(6.dp).fillMaxHeight(0.65f).background(Color(0xFF2563EB), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
            }
        }
        Box(modifier = Modifier.size(24.dp).align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun SettingsIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(34.dp).align(Alignment.TopStart).offset(x = 4.dp, y = 2.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.size(22.dp).align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-2).dp)) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun UsersIcon3D() {
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(32.dp).align(Alignment.CenterStart).offset(x = 4.dp, y = (-2).dp)) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.size(26.dp).align(Alignment.CenterEnd).offset(x = (-8).dp, y = 2.dp)) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-1).dp)
                .background(Color(0xFF2563EB), CircleShape)
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
    }
}
