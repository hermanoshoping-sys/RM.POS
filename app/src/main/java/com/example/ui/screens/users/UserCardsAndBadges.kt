package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserEntity
import com.example.data.UserRole

@Composable
internal fun UserStatCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) accentColor.copy(alpha = 0.18f) else Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) accentColor else Color(0xFF334155)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(34.dp).background(accentColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(text = "$count", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                Text(
                    text = title,
                    fontSize = 9.5.sp,
                    color = if (isSelected) accentColor else Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun EnhancedUserCard(
    user: UserEntity,
    isCurrentUser: Boolean,
    lang: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isManager = user.role == UserRole.MANAGER
    val initial = user.username.firstOrNull()?.uppercase() ?: "U"

    Card(
        modifier = Modifier.fillMaxWidth().testTag("user_card_${user.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(
            1.dp,
            if (isCurrentUser) Color(0xFF0284C7).copy(alpha = 0.6f)
            else if (isManager) Color(0xFFF59E0B).copy(alpha = 0.35f)
            else Color(0xFF334155)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = if (isManager) listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                    else listOf(Color(0xFF10B981), Color(0xFF059669))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = user.username, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            if (isCurrentUser) {
                                Surface(
                                    color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = if (lang == "AR") "أنت" else "Vous",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = if (isManager) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isManager) Icons.Filled.Shield else Icons.Filled.PointOfSale,
                                        contentDescription = null,
                                        tint = if (isManager) Color(0xFFFBBF24) else Color(0xFF34D399),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = if (isManager) (if (lang == "AR") "مدير النظام" else "Gérant") else (if (lang == "AR") "كاشير" else "Caissier"),
                                        color = if (isManager) Color(0xFFFBBF24) else Color(0xFF34D399),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text("•", color = Color.Gray, fontSize = 11.sp)
                            Text("PIN: ••••", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(34.dp).background(Color(0xFF334155), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    if (!isCurrentUser) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(34.dp).background(Color(0xFFEF4444).copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(34.dp).background(Color(0xFF334155).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = "Current User Protected", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.6f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (user.canManageClients) {
                    val (bgColor, borderColor, textColor, iconEmoji, label) = if (user.canEditCustomers) {
                        Tuple5(
                            Color(0xFF10B981).copy(alpha = 0.15f),
                            Color(0xFF10B981).copy(alpha = 0.4f),
                            Color(0xFF34D399),
                            "✏️",
                            if (lang == "AR") "تعديل الزبائن: مسموح" else "Modif clients: OK"
                        )
                    } else {
                        Tuple5(
                            Color(0xFFEF4444).copy(alpha = 0.18f),
                            Color(0xFFEF4444).copy(alpha = 0.5f),
                            Color(0xFFF87171),
                            "🔒",
                            if (lang == "AR") "منع تعديل الزبائن بعد الإنشاء" else "Modif clients interdite"
                        )
                    }
                    Surface(color = bgColor, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, borderColor)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(iconEmoji, fontSize = 10.sp)
                            Text(text = label, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (user.enforceCustomerGeoLocation) {
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("📍", fontSize = 10.sp)
                            Text(
                                text = if (lang == "AR") "موقع الزبائن: مفروض" else "GPS Obligatoire",
                                color = Color(0xFFFBBF24),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (user.canEditInvoice) PermissionChip("📝 " + (if (lang == "AR") "تعديل الفواتير" else "Modif Factures"))
                if (user.canDeleteInvoice) PermissionChip("🗑️ " + (if (lang == "AR") "حذف الفواتير" else "Suppr Factures"))
                if (user.canModifyPrice) PermissionChip("🏷️ " + (if (lang == "AR") "تعديل الأسعار" else "Modif Prix"))
                if (user.canSell) PermissionChip("🛒 POS")
                if (user.canManageInventory) PermissionChip("📦 " + (if (lang == "AR") "المخزون" else "Stock"))
                if (user.canManageClients) PermissionChip("👥 " + (if (lang == "AR") "العملاء" else "Clients"))
                if (user.canManageSuppliers) PermissionChip("🚚 " + (if (lang == "AR") "الموردين" else "Fournisseurs"))
                if (user.canPurchase) PermissionChip("📥 " + (if (lang == "AR") "المشتريات" else "Achats"))
                if (user.canReports) PermissionChip("📊 " + (if (lang == "AR") "التقارير" else "Rapports"))
                if (user.canManageExpenses) PermissionChip("💸 " + (if (lang == "AR") "المصاريف" else "Dépenses"))
                if (user.canManagePayments) PermissionChip("💳 " + (if (lang == "AR") "المدفوعات" else "Paiements"))
            }
        }
    }
}

private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

@Composable
fun PermissionChip(text: String) {
    Surface(color = Color(0xFF334155).copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            color = Color(0xFFCBD5E1),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
