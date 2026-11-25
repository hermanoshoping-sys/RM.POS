package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AuditLogEntity
import com.example.ui.viewmodels.PosViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * شاشة سجل التدقيق الرقمي (Audit Log Screen)
 * تعرض جميع العمليات الحساسة في النظام:
 * - حذف / تعديل الفواتير
 * - تغيير الأسعار
 * - الاسترجاعات والاسترداد
 * - تغيير الصلاحيات وإدارة المستخدمين
 * - محاولات الدخول الفاشلة
 */
@Composable
fun AuditLogsScreen(
    viewModel: PosViewModel,
    lang: String = "AR",
    onNavigate: (String) -> Unit
) {
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(auditLogs, selectedFilter) {
        if (selectedFilter == "ALL") {
            auditLogs
        } else {
            auditLogs.filter { it.actionType == selectedFilter }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // --- 1. Top App Bar ---
        Surface(
            color = Color(0xFF1E293B),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF334155))
                            .clickable { onNavigate("DASHBOARD") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (lang == "AR") "سجل تدقيق العمليات (Audit Log)" else "Journal d'audit du système",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (lang == "AR") "مراقبة العمليات الحساسة والأمنية" else "Surveillance des opérations critiques",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Surface(
                    color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6))
                ) {
                    Text(
                        text = "${filteredLogs.size} ${if (lang == "AR") "عملية" else "logs"}",
                        color = Color(0xFF60A5FA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // --- 2. Filter Chips ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "ALL" to (if (lang == "AR") "الكل" else "Tous"),
                "INVOICE_DELETE" to (if (lang == "AR") "حذف فواتير" else "Suppression"),
                "PRICE_CHANGE" to (if (lang == "AR") "تغيير أسعار" else "Prix"),
                "RETURN_REFUND" to (if (lang == "AR") "استرجاع" else "Retours"),
                "PERMISSION_CHANGE" to (if (lang == "AR") "صلاحيات" else "Droits"),
                "FAILED_LOGIN" to (if (lang == "AR") "دخول فاشل" else "Connexion")
            )

            filters.forEach { (type, label) ->
                val isSelected = selectedFilter == type
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF60A5FA) else Color(0xFF334155)
                    ),
                    modifier = Modifier.clickable { selectedFilter = type }
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // --- 3. Logs List ---
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (lang == "AR") "لا توجد سجلات تدقيق مسجلة حتى الآن" else "Aucun journal d'audit enregistré",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    AuditLogItemCard(log = log, lang = lang)
                }
            }
        }
    }
}

@Composable
private fun AuditLogItemCard(log: AuditLogEntity, lang: String) {
    val (badgeBg, badgeBorder, badgeText, badgeIcon) = when (log.actionType) {
        "INVOICE_DELETE" -> Quad(Color(0xFF450A0A), Color(0xFFDC2626), if (lang == "AR") "حذف فاتورة" else "Facture Supprimée", Icons.Filled.DeleteForever)
        "INVOICE_EDIT" -> Quad(Color(0xFF3F2C04), Color(0xFFF59E0B), if (lang == "AR") "تعديل فاتورة" else "Facture Modifiée", Icons.Filled.EditNote)
        "PRICE_CHANGE" -> Quad(Color(0xFF1E1B4B), Color(0xFF818CF8), if (lang == "AR") "تغيير سعر" else "Changement Prix", Icons.Filled.PriceChange)
        "RETURN_REFUND" -> Quad(Color(0xFF064E3B), Color(0xFF10B981), if (lang == "AR") "استرجاع / رد" else "Retour Vente", Icons.Filled.AssignmentReturn)
        "PERMISSION_CHANGE" -> Quad(Color(0xFF311042), Color(0xFFA855F7), if (lang == "AR") "تغيير صلاحيات" else "Droits Utilisateur", Icons.Filled.AdminPanelSettings)
        "FAILED_LOGIN" -> Quad(Color(0xFF450A0A), Color(0xFFEF4444), if (lang == "AR") "دخول فاشل" else "Échec Connexion", Icons.Filled.Lock)
        "CUSTOMER_DELETE" -> Quad(Color(0xFF450A0A), Color(0xFFDC2626), if (lang == "AR") "حذف زبون" else "Client Supprimé", Icons.Filled.PersonRemove)
        "SUPPLIER_DELETE" -> Quad(Color(0xFF450A0A), Color(0xFFDC2626), if (lang == "AR") "حذف مورد" else "Fournisseur Supprimé", Icons.Filled.PersonRemove)
        else -> Quad(Color(0xFF1E293B), Color(0xFF64748B), log.actionType, Icons.Filled.Info)
    }

    val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val dateStr = timeFormat.format(Date(log.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (log.isCritical) badgeBorder else Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(badgeIcon, contentDescription = null, tint = badgeBorder, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(badgeText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = dateStr,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Text(
                text = log.details,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${log.username} (${log.userRole})",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                if (log.entityId.isNotBlank()) {
                    Text(
                        text = "#${log.entityId}",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
