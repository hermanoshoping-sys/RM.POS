package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.UserEntity
import com.example.data.UserRole
import com.example.ui.theme.POSPrimary
import com.example.ui.viewmodels.PosViewModel

@Composable
fun UsersScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: ((String) -> Unit)? = null
) {
    val usersList by viewModel.users.collectAsStateWithLifecycle()
    var userToEdit by remember { mutableStateOf<UserEntity?>(null) }
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<UserEntity?>(null) }
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val hasAccess = currentUser?.hasUsersAccess() ?: true

    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf("ALL") }

    val filteredUsers = remember(usersList, searchQuery, selectedRoleFilter) {
        usersList.filter { u ->
            val matchesQuery = searchQuery.isBlank() || u.username.contains(searchQuery, ignoreCase = true)
            val matchesRole = when (selectedRoleFilter) {
                "MANAGER" -> u.role == UserRole.MANAGER
                "CASHIER" -> u.role == UserRole.CASHIER
                else -> true
            }
            matchesQuery && matchesRole
        }
    }

    val managerCount = remember(usersList) { usersList.count { it.role == UserRole.MANAGER } }
    val cashierCount = remember(usersList) { usersList.count { it.role == UserRole.CASHIER } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        if (!hasAccess) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(60.dp).background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = Color(0xFFEF4444), modifier = Modifier.size(34.dp))
                        }
                        Text(if (lang == "AR") "صلاحية محجوبة" else "Accès Refusé", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFEF4444))
                        Text(
                            text = if (lang == "AR")
                                "عذراً، الوصول لشاشة إدارة المستخدمين مقتصر على مدير النظام فقط. تم حجب الصلاحية لحماية إعدادات وبيانات المتجر."
                            else "Accès réservé au gérant. Vous n'avez pas la permission de gérer les utilisateurs.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Button(
                            onClick = { onNavigate?.invoke("DASHBOARD") },
                            colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (lang == "AR") "العودة للرئيسية" else "Retour à l'accueil")
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(if (lang == "AR") "إدارة المستخدمين والصلاحيات" else "Gestion des Utilisateurs", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        Text(if (lang == "AR") "تخصيص أدوار الكاشير والمدراء والتحكم في القيود" else "Contrôle d'accès et permissions", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }

                    Button(
                        onClick = { isAddDialogOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp).testTag("users_add_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == "AR") "إضافة مستخدم" else "Ajouter", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserStatCard(
                        title = if (lang == "AR") "المستخدمون" else "Total",
                        count = usersList.size,
                        icon = Icons.Filled.Group,
                        accentColor = Color(0xFF38BDF8),
                        isSelected = selectedRoleFilter == "ALL",
                        onClick = { selectedRoleFilter = "ALL" },
                        modifier = Modifier.weight(1f)
                    )
                    UserStatCard(
                        title = if (lang == "AR") "المدراء" else "Gérants",
                        count = managerCount,
                        icon = Icons.Filled.Shield,
                        accentColor = Color(0xFFFBBF24),
                        isSelected = selectedRoleFilter == "MANAGER",
                        onClick = { selectedRoleFilter = if (selectedRoleFilter == "MANAGER") "ALL" else "MANAGER" },
                        modifier = Modifier.weight(1f)
                    )
                    UserStatCard(
                        title = if (lang == "AR") "الكاشير" else "Caissiers",
                        count = cashierCount,
                        icon = Icons.Filled.PointOfSale,
                        accentColor = Color(0xFF34D399),
                        isSelected = selectedRoleFilter == "CASHIER",
                        onClick = { selectedRoleFilter = if (selectedRoleFilter == "CASHIER") "ALL" else "CASHIER" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (lang == "AR") "ابحث عن اسم المستخدم..." else "Recherche...", fontSize = 12.sp, color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0284C7),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(46.dp).testTag("users_search_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedRoleFilter == "ALL",
                            onClick = { selectedRoleFilter = "ALL" },
                            label = { Text(if (lang == "AR") "الكل (${usersList.size})" else "Tous (${usersList.size})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF0284C7), selectedLabelColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        )
                        FilterChip(
                            selected = selectedRoleFilter == "CASHIER",
                            onClick = { selectedRoleFilter = "CASHIER" },
                            label = { Text(if (lang == "AR") "كاشير ($cashierCount)" else "Caissier ($cashierCount)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF10B981), selectedLabelColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        )
                        FilterChip(
                            selected = selectedRoleFilter == "MANAGER",
                            onClick = { selectedRoleFilter = "MANAGER" },
                            label = { Text(if (lang == "AR") "مدير ($managerCount)" else "Gérant ($managerCount)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFF59E0B), selectedLabelColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredUsers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(54.dp).background(Color(0xFF1E293B), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PersonOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                            }
                            Text(if (lang == "AR") "لم يتم العثور على أي مستخدمين مطابقة للبحث" else "Aucun utilisateur trouvé", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(items = filteredUsers, key = { it.id }, contentType = { "user_card" }) { user ->
                            EnhancedUserCard(
                                user = user,
                                isCurrentUser = (user.id > 0L && user.id == currentUser?.id) ||
                                    user.username.equals(currentUser?.username, ignoreCase = true),
                                lang = lang,
                                onEdit = { userToEdit = user },
                                onDelete = { userToDelete = user }
                            )
                        }
                    }
                }
            }
        }
    }

    if (isAddDialogOpen) {
        UserAddEditDialog(
            userToEdit = null,
            onDismiss = { isAddDialogOpen = false },
            onSave = { newUser ->
                viewModel.saveUser(newUser)
                Toast.makeText(context, if (lang == "AR") "تمت إضافة المستخدم بنجاح" else "Utilisateur ajouté", Toast.LENGTH_SHORT).show()
            },
            lang = lang
        )
    }

    if (userToEdit != null) {
        UserAddEditDialog(
            userToEdit = userToEdit,
            onDismiss = { userToEdit = null },
            onSave = { updatedUser ->
                viewModel.saveUser(updatedUser)
                Toast.makeText(context, if (lang == "AR") "تم تحديث بيانات المستخدم" else "Utilisateur mis à jour", Toast.LENGTH_SHORT).show()
            },
            lang = lang
        )
    }

    userToDelete?.let { targetUser ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text(if (lang == "AR") "تأكيد حذف المستخدم" else "Confirmer la suppression", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (lang == "AR") "هل أنت متأكد من حذف حساب '${targetUser.username}' نهائياً؟ لن يتمكن من تسجيل الدخول بعد الآن."
                    else "Voulez-vous vraiment supprimer le compte '${targetUser.username}' ?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUser(targetUser)
                        userToDelete = null
                        Toast.makeText(context, if (lang == "AR") "تم حذف المستخدم" else "Utilisateur supprimé", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (lang == "AR") "تأكيد الحذف" else "Supprimer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) {
                    Text(if (lang == "AR") "إلغاء" else "Annuler")
                }
            }
        )
    }
}
