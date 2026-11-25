package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserEntity
import com.example.data.UserRole

@Composable
fun PermissionSwitchRow(
    label: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            if (checked) Color(0xFF0284C7).copy(alpha = 0.2f) else Color(0xFF334155).copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) Color(0xFF38BDF8) else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column {
                Text(
                    text = label,
                    fontSize = 12.5.sp,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                    color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        lineHeight = 13.sp
                    )
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0284C7),
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color(0xFF334155)
            )
        )
    }
}

internal data class UserPermissionFormState(
    val username: String = "",
    val pin: String = "",
    val showPin: Boolean = false,
    val role: UserRole = UserRole.CASHIER,
    val canSell: Boolean = true,
    val canPurchase: Boolean = false,
    val canReports: Boolean = false,
    val canModifyPrice: Boolean = false,
    val canDeleteInvoice: Boolean = false,
    val canEditInvoice: Boolean = false,
    val canManageInventory: Boolean = true,
    val canManageClients: Boolean = true,
    val canEditCustomers: Boolean = true,
    val canManageSuppliers: Boolean = false,
    val canManageExpenses: Boolean = false,
    val canManagePayments: Boolean = false,
    val enforceCustomerGeoLocation: Boolean = false
) {
    companion object {
        fun fromUser(userToEdit: UserEntity?): UserPermissionFormState = UserPermissionFormState(
            username = userToEdit?.username ?: "",
            role = userToEdit?.role ?: UserRole.CASHIER,
            canSell = userToEdit?.canSell ?: true,
            canPurchase = userToEdit?.canPurchase ?: false,
            canReports = userToEdit?.canReports ?: false,
            canModifyPrice = userToEdit?.canModifyPrice ?: false,
            canDeleteInvoice = userToEdit?.canDeleteInvoice ?: false,
            canEditInvoice = userToEdit?.canEditInvoice ?: false,
            canManageInventory = userToEdit?.canManageInventory ?: true,
            canManageClients = userToEdit?.canManageClients ?: true,
            canEditCustomers = userToEdit?.canEditCustomers ?: (userToEdit?.isManager == true || userToEdit == null),
            canManageSuppliers = userToEdit?.canManageSuppliers ?: false,
            canManageExpenses = userToEdit?.canManageExpenses ?: false,
            canManagePayments = userToEdit?.canManagePayments ?: false,
            enforceCustomerGeoLocation = userToEdit?.enforceCustomerGeoLocation ?: false
        )
    }
}


@Composable
internal fun UserSecurityControlsSection(
    state: UserPermissionFormState,
    lang: String,
    onStateChange: (UserPermissionFormState) -> Unit
) {
    Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                Text(
                    text = if (lang == "AR") "الضوابط الأمنية وحماية البيانات" else "Contrôles et Sécurité",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFFF59E0B)
                )
            }

            Surface(
                color = if (state.canEditCustomers) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (state.canEditCustomers) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (state.canEditCustomers) "✏️" else "🔒", fontSize = 16.sp)
                            Column {
                                Text(if (lang == "AR") "تعديل بيانات العملاء بعد إنشائهم" else "Modifier clients après création", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text(
                                    text = if (state.canEditCustomers) (if (lang == "AR") "مسموح بتعديل العملاء" else "Modification autorisée")
                                    else (if (lang == "AR") "محظور: إنشاء العملاء فقط دون تعديلهم" else "Création uniquement, modif bloquée"),
                                    fontSize = 10.sp,
                                    color = if (state.canEditCustomers) Color(0xFF34D399) else Color(0xFFF87171)
                                )
                            }
                        }
                        Switch(
                            checked = state.canEditCustomers,
                            onCheckedChange = { onStateChange(state.copy(canEditCustomers = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color(0xFFEF4444).copy(alpha = 0.6f)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (lang == "AR")
                            "عند الإلغاء: يستطيع المستخدم إضافة عميل جديد، لكنه يُمنع نهائياً من تعديل الاسم أو الهاتف أو العنوان أو الإحداثيات لأي عميل تم إنشاؤه مسبقاً."
                        else "Si désactivé: l'utilisateur peut ajouter un nouveau client, mais ne peut modifier aucun client existant.",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 13.sp
                    )
                }
            }

            Surface(
                color = if (state.enforceCustomerGeoLocation) Color(0xFF0284C7).copy(alpha = 0.1f) else Color(0xFF0F172A),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (state.enforceCustomerGeoLocation) Color(0xFF0284C7).copy(alpha = 0.5f) else Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📍", fontSize = 16.sp)
                            Column {
                                Text(if (lang == "AR") "فرض التواجد الجغرافي عند الفوترة (GPS)" else "Exiger présence GPS du client", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text(
                                    text = if (state.enforceCustomerGeoLocation) (if (lang == "AR") "مفروض إجبارياً على هذا المستخدم" else "Obligatoire")
                                    else (if (lang == "AR") "غير مفروض" else "Optionnel"),
                                    fontSize = 10.sp,
                                    color = if (state.enforceCustomerGeoLocation) Color(0xFF38BDF8) else Color.Gray
                                )
                            }
                        }
                        Switch(
                            checked = state.enforceCustomerGeoLocation,
                            onCheckedChange = { onStateChange(state.copy(enforceCustomerGeoLocation = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0284C7))
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (lang == "AR")
                            "يمنع المستخدم من فتح أو إنشاء فاتورة باسم أي زبون محدد موقعه إلا عند تواجده الفعلي في نطاق موقعه الجغرافي."
                        else "Empêche la facturation d'un client géolocalisé si l'utilisateur n'est pas sur place.",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 13.sp
                    )
                }
            }

            PermissionSwitchRow(
                label = if (lang == "AR") "تعديل الفواتير بعد تسجيلها" else "Modifier factures",
                subtitle = if (lang == "AR") "تغيير بنود أو أسعار الفواتير السابقة" else "Modifier lignes de factures existantes",
                icon = Icons.Filled.Edit,
                checked = state.canEditInvoice
            ) { onStateChange(state.copy(canEditInvoice = it)) }

            PermissionSwitchRow(
                label = if (lang == "AR") "حذف وإلغاء الفواتير" else "Supprimer factures",
                subtitle = if (lang == "AR") "حذف الفواتير نهائياً وإرجاع الكميات" else "Suppression définitive",
                icon = Icons.Filled.DeleteForever,
                checked = state.canDeleteInvoice
            ) { onStateChange(state.copy(canDeleteInvoice = it)) }

            PermissionSwitchRow(
                label = if (lang == "AR") "تعديل أسعار البيع أثناء الفوترة" else "Modifier prix au POS",
                subtitle = if (lang == "AR") "السماح للكاشير بتغيير سعر الوحدة للزبون" else "Changer le prix unitaire",
                icon = Icons.Filled.PriceChange,
                checked = state.canModifyPrice
            ) { onStateChange(state.copy(canModifyPrice = it)) }
        }
    }
}

@Composable
internal fun UserScreenVisibilitySection(
    state: UserPermissionFormState,
    lang: String,
    onStateChange: (UserPermissionFormState) -> Unit
) {
    Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.ViewQuilt, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                Text(
                    text = if (lang == "AR") "القوائم والشاشات المتاحة للمستخدم" else "Écrans et Menus Autorisés",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF10B981)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            PermissionSwitchRow(if (lang == "AR") "نقطة البيع وفواتير المبيعات (POS)" else "Point de vente (POS)", icon = Icons.Filled.ShoppingCart, checked = state.canSell) { onStateChange(state.copy(canSell = it)) }
            PermissionSwitchRow(if (lang == "AR") "المنتجات والمخزون وعائلات الأصناف" else "Stock & Produits", icon = Icons.Filled.Inventory, checked = state.canManageInventory) { onStateChange(state.copy(canManageInventory = it)) }
            PermissionSwitchRow(if (lang == "AR") "قائمة وسجل العملاء والزبائن" else "Clients", icon = Icons.Filled.People, checked = state.canManageClients) { onStateChange(state.copy(canManageClients = it)) }
            PermissionSwitchRow(if (lang == "AR") "قائمة وسجل الموردين" else "Fournisseurs", icon = Icons.Filled.LocalShipping, checked = state.canManageSuppliers) { onStateChange(state.copy(canManageSuppliers = it)) }
            PermissionSwitchRow(if (lang == "AR") "فواتير الشراء وسجل المشتريات" else "Factures d'achat", icon = Icons.Filled.ShoppingBag, checked = state.canPurchase) { onStateChange(state.copy(canPurchase = it)) }
            PermissionSwitchRow(if (lang == "AR") "شاشة التقارير والإحصائيات والأرباح" else "Rapports & Statistiques", icon = Icons.Filled.BarChart, checked = state.canReports) { onStateChange(state.copy(canReports = it)) }
            PermissionSwitchRow(if (lang == "AR") "قائمة وتسجيل المصاريف" else "Dépenses", icon = Icons.Filled.ReceiptLong, checked = state.canManageExpenses) { onStateChange(state.copy(canManageExpenses = it)) }
            PermissionSwitchRow(if (lang == "AR") "قائمة المدفوعات والمقبوضات" else "Paiements", icon = Icons.Filled.Payments, checked = state.canManagePayments) { onStateChange(state.copy(canManagePayments = it)) }
        }
    }
}
