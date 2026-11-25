package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.data.AuditLogEntity
import com.example.data.PosRepository
import com.example.data.TopProductRecord
import com.example.data.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Modular ViewModel for Dashboard KPIs, Aggregated Metrics, Users, and Audit Logs.
 */
class AnalyticsDashboardViewModel(
    private val repository: PosRepository,
    private val scope: CoroutineScope
) : ViewModel() {

    private val startOfDay: Long
        get() = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val endOfDay: Long
        get() = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

    val profitTodaySum: StateFlow<Double?> = repository.getProfitSumForDay(startOfDay, endOfDay)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0)
    val salesTodaySum: StateFlow<Double?> = repository.getSalesSumForDay(startOfDay, endOfDay)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0)
    val invoiceTodayCount: StateFlow<Int?> = repository.getSalesCountForDay(startOfDay, endOfDay)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)
    val cashTodaySum: StateFlow<Double?> = repository.getCashSalesSumForDay(startOfDay, endOfDay)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0)
    val cardTodaySum: StateFlow<Double?> = repository.getCardSalesSumForDay(startOfDay, endOfDay)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0)
    val expensesTodaySum: StateFlow<Double?> = repository.getExpensesSumForDay(startOfDay, endOfDay)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0.0)
    val lowStockAlertCount: StateFlow<Int?> = repository.getLowStockCountFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)
    val topProducts: StateFlow<List<TopProductRecord>> = repository.getTopProductsFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val users: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.auditLogs
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveUser(
        user: UserEntity,
        actor: UserEntity?,
        onUserSaved: (savedUser: UserEntity, oldUsername: String?) -> Unit = { _, _ -> }
    ) {
        scope.launch {
            val cleanedUser = user.copy(username = user.username.trim())
            val existing = repository.getAllUsersList().find { it.id == cleanedUser.id && cleanedUser.id > 0L }
            val savedId = repository.saveUser(cleanedUser)
            val savedEntity = if (cleanedUser.id > 0L) cleanedUser else cleanedUser.copy(id = savedId)
            val oldUsername = existing?.username?.trim()
            if (!oldUsername.isNullOrBlank() && oldUsername != savedEntity.username) {
                repository.renameSellerReferences(oldUsername, savedEntity.username)
            }
            onUserSaved(savedEntity, oldUsername)
            if (existing != null) {
                repository.recordAuditLog(
                    actionType = "PERMISSION_CHANGE",
                    entityType = "USER",
                    entityId = savedEntity.id.toString(),
                    details = "تعديل صلاحيات/بيانات المستخدم: ${savedEntity.username} (دور: ${savedEntity.role}, canSell=${savedEntity.canSell}, canPurchase=${savedEntity.canPurchase}, canReports=${savedEntity.canReports}, canModifyPrice=${savedEntity.canModifyPrice}, canDeleteInvoice=${savedEntity.canDeleteInvoice})",
                    username = actor?.username.orEmpty(),
                    userRole = actor?.roleName ?: "MANAGER",
                    isCritical = true
                )
            } else {
                repository.recordAuditLog(
                    actionType = "USER_MANAGEMENT",
                    entityType = "USER",
                    entityId = savedEntity.username,
                    details = "إضافة مستخدم جديد: ${savedEntity.username} (دور: ${savedEntity.role})",
                    username = actor?.username.orEmpty(),
                    userRole = actor?.roleName ?: "MANAGER"
                )
            }
        }
    }

    fun deleteUser(user: UserEntity, actor: UserEntity?) {
        scope.launch {
            repository.deleteUser(user)
            repository.recordAuditLog(
                actionType = "USER_MANAGEMENT",
                entityType = "USER",
                entityId = user.id.toString(),
                details = "حذف المستخدم: ${user.username} (دور: ${user.role})",
                username = actor?.username.orEmpty(),
                userRole = actor?.roleName ?: "MANAGER",
                isCritical = true
            )
        }
    }
}
