package com.example.ui.screens

import java.io.File
import androidx.core.content.FileProvider
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
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import com.example.ui.viewmodels.PosViewModel
import java.util.*

// --- 3. DASHBOARD SCREEN IMPLEMENTATION ---

@Composable
fun DashboardScreen(viewModel: PosViewModel, lang: String, onNavigate: (String) -> Unit) {
    val salesVal by viewModel.salesTodaySum.collectAsStateWithLifecycle()
    val invCount by viewModel.invoiceTodayCount.collectAsStateWithLifecycle()
    val cashVal by viewModel.cashTodaySum.collectAsStateWithLifecycle()
    val cardVal by viewModel.cardTodaySum.collectAsStateWithLifecycle()
    val expToday by viewModel.expensesTodaySum.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockAlertCount.collectAsStateWithLifecycle()
    val topProductsList by viewModel.topProducts.collectAsStateWithLifecycle()
    val recentSalesList by viewModel.recentSales.collectAsStateWithLifecycle()
    val storeCurrency by viewModel.currency.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val linkedEmail by viewModel.linkedEmail.collectAsStateWithLifecycle()
        val lastBackupTime by viewModel.lastBackupTime.collectAsStateWithLifecycle()

        DashboardCloudSyncBanner(
            lang = lang,
            linkedEmail = linkedEmail,
            lastBackupTime = lastBackupTime,
            onOpenSettings = { onNavigate("SETTINGS") }
        )

        DashboardCreateInvoicesSection(onNavigate = onNavigate)

        // 2. Financial Metrics
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardMetricCard(
                modifier = Modifier.weight(1f),
                title = Bilingual.tr(lang, "مبيعات اليوم", "Ventes du jour", "Today's Sales"),
                value = String.format(java.util.Locale.US, "%.2f", salesVal ?: 0.0),
                icon = Icons.Filled.TrendingUp,
                color = Color(0xFF4ADE80)
            )
            DashboardMetricCard(
                modifier = Modifier.weight(1f),
                title = Bilingual.tr(lang, "الأرباح", "Bénéfices", "Profits"),
                value = String.format(java.util.Locale.US, "%.2f", (salesVal ?: 0.0) * 0.3),
                icon = Icons.Filled.AccountBalanceWallet,
                color = Color(0xFF60A5FA)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardMetricCard(
                modifier = Modifier.weight(1f),
                title = Bilingual.tr(lang, "المصاريف", "Dépenses", "Expenses"),
                value = String.format(java.util.Locale.US, "%.2f", expToday ?: 0.0),
                icon = Icons.Filled.MoneyOff,
                color = Color(0xFFF87171)
            )
            DashboardMetricCard(
                modifier = Modifier.weight(1f),
                title = Bilingual.tr(lang, "ديون مستحقة", "Créances", "Pending Debts"),
                value = "0.00",
                icon = Icons.Filled.ReceiptLong,
                color = Color(0xFFFBBF24)
            )
        }

        // Quick Invoices Access
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f).clickable { onNavigate("SALES_INVOICES") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2E22))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Receipt, contentDescription = null, tint = Color(0xFF10B981))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            Bilingual.tr(lang, "سجل فواتير المبيعات", "Factures de ventes", "Sales Invoices Log"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            Bilingual.tr(lang, "طباعة وتعديل الفواتير", "Imprimer et modifier", "Print & edit invoices"),
                            fontSize = 10.sp,
                            color = Color(0xFF6EE7B7)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f).clickable { onNavigate("PURCHASE_INVOICES") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A153E))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color(0xFFA855F7).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = Color(0xFFA855F7))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            Bilingual.tr(lang, "سجل فواتير الشراء", "Factures d'achats", "Purchase Invoices Log"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            Bilingual.tr(lang, "طباعة وتعديل المشتريات", "Imprimer et modifier", "Print & edit purchases"),
                            fontSize = 10.sp,
                            color = Color(0xFFD8B4FE)
                        )
                    }
                }
            }
        }

        // 3. Mini Sales Chart (Placeholder)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    Bilingual.tr(lang, "مبيعات آخر 7 أيام", "Ventes des 7 derniers jours", "Last 7 Days Sales"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Simple bar chart representation
                Row(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val heights = listOf(0.3f, 0.5f, 0.2f, 0.8f, 0.6f, 0.9f, 1.0f)
                    heights.forEach { h ->
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight(h)
                                .background(Color(0xFF3B82F6), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        )
                    }
                }
            }
        }

        // 4. Alerts & Tasks
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    Bilingual.tr(lang, "🔔 التنبيهات والمهام", "🔔 Alertes et Tâches", "🔔 Alerts & Tasks"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                AlertItem(
                    icon = "⚠️",
                    text = Bilingual.tr(
                        lang,
                        "$lowStockCount منتجات مخزونها منخفض",
                        "$lowStockCount produits en stock faible",
                        "$lowStockCount products low on stock"
                    ),
                    color = Color(0xFFFBBF24)
                )
                AlertItem(
                    icon = "⏳",
                    text = Bilingual.tr(lang, "3 فواتير مستحقة", "3 factures dues", "3 pending invoices"),
                    color = Color(0xFFF87171)
                )
                AlertItem(
                    icon = "📦",
                    text = Bilingual.tr(lang, "2 طلبات شراء قيد الانتظار", "2 bons d'achat en attente", "2 purchase orders pending"),
                    color = Color(0xFF60A5FA)
                )
            }
        }

        // 5. Best Sellers
        Text(
            Bilingual.tr(lang, "🏆 الأكثر مبيعًا", "🏆 Meilleures Ventes", "🏆 Best Sellers"),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (topProductsList.isEmpty()) {
                    Text(Bilingual.tr(lang, "لا توجد بيانات", "Aucune donnée", "No data available"), color = Color.Gray)
                } else {
                    topProductsList.take(3).forEach { p ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.productName)
                            Text("${String.format(java.util.Locale.US, "%.2f", p.totalRevenue)} $storeCurrency", color = POSPrimary)
                        }
                        HorizontalDivider(color = Color(0xFF1E293B))
                    }
                }
            }
        }

        // 6. Recent Transactions
        Text(
            Bilingual.tr(lang, "🧾 آخر العمليات", "🧾 Dernières Opérations", "🧾 Recent Transactions"),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        recentSalesList.take(3).forEach { sale ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("#${sale.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            if (sale.paymentMethod == "CASH") {
                                Bilingual.tr(lang, "🟢 مكتملة · نقدًا", "🟢 Payée · Espèces", "🟢 Completed · Cash")
                            } else {
                                Bilingual.tr(lang, "🔵 مكتملة · بطاقة", "🔵 Payée · Carte", "🔵 Completed · Card")
                            },
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Text(
                        "${String.format(java.util.Locale.US, "%.2f", sale.totalAmountDouble)} $storeCurrency",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4ADE80)
                    )
                }
            }
        }

        // 7. Shortcuts Center
        Text(
            Bilingual.tr(lang, "⚡ مركز الاختصارات", "⚡ Raccourcis Rapides", "⚡ Quick Shortcuts"),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(180.dp),
            userScrollEnabled = false
        ) {
            val shortcuts = listOf(
                Pair(Bilingual.tr(lang, "منتج", "Produit", "Product"), Icons.Filled.AddBox) to "INVENTORY",
                Pair(Bilingual.tr(lang, "عميل", "Client", "Customer"), Icons.Filled.PersonAdd) to "CLIENTS",
                Pair(Bilingual.tr(lang, "شراء", "Achat", "Purchase"), Icons.Filled.ShoppingBag) to "PURCHASES",
                Pair(Bilingual.tr(lang, "مصروف", "Dépense", "Expense"), Icons.Filled.MoneyOff) to "EXPENSES",
                Pair(Bilingual.tr(lang, "جرد", "Stock", "Stock"), Icons.Filled.Checklist) to "STOCK",
                Pair(Bilingual.tr(lang, "مورد", "Fournisseur", "Supplier"), Icons.Filled.LocalShipping) to "SUPPLIERS",
                Pair(Bilingual.tr(lang, "تقرير", "Rapport", "Report"), Icons.Filled.Analytics) to "REPORTS",
                Pair(Bilingual.tr(lang, "إعدادات", "Paramètres", "Settings"), Icons.Filled.Settings) to "SETTINGS"
            )
            items(shortcuts.size) { i ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onNavigate(shortcuts[i].second) }.padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).background(Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(shortcuts[i].first.second, contentDescription = null, tint = Color.LightGray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(shortcuts[i].first.first, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
        
        
    }
}


@Composable
fun DashboardMetricCard(modifier: Modifier = Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, color = Color.Gray, fontSize = 12.sp)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        }
    }
}


@Composable
fun AlertItem(icon: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = color, fontSize = 14.sp)
    }
}

