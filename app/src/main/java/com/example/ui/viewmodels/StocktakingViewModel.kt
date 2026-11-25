package com.example.ui.viewmodels

import com.example.data.InventoryAdjustmentEntity
import com.example.data.InventoryEntity
import com.example.data.InventoryItemEntity
import com.example.data.PosRepository
import com.example.data.UserEntity
import com.example.domain.stocktaking.BarcodeScanOutcome
import com.example.domain.stocktaking.CategoryValuationRow
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.InventoryApprovalPreview
import com.example.domain.stocktaking.InventoryComparisonSummary
import com.example.domain.stocktaking.InventoryDashboardStats
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import com.example.domain.stocktaking.StockMonitoringItem
import com.example.domain.stocktaking.StocktakingCalculator
import com.example.domain.stocktaking.SupplierValuationRow
import com.example.ui.theme.Bilingual
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StocktakingFeedbackType {
    SUCCESS,
    WARNING,
    ERROR
}

data class StocktakingFeedback(
    val message: String,
    val type: StocktakingFeedbackType,
    val highlightedProductId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalCoroutinesApi::class)
class StocktakingViewModel(
    private val repository: PosRepository,
    private val scope: CoroutineScope,
    private val currentUserProvider: () -> UserEntity?,
    private val languageProvider: () -> String,
    private val onBeep: () -> Unit = {}
) {
    private val _selectedInventoryId = MutableStateFlow<Long?>(null)
    val selectedInventoryId: StateFlow<Long?> = _selectedInventoryId.asStateFlow()

    private val _slowMovingDays = MutableStateFlow(30)
    val slowMovingDays: StateFlow<Int> = _slowMovingDays.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedback = MutableStateFlow<StocktakingFeedback?>(null)
    val feedback: StateFlow<StocktakingFeedback?> = _feedback.asStateFlow()

    private val _pendingDuplicateScan = MutableStateFlow<BarcodeScanOutcome.PromptDuplicate?>(null)
    val pendingDuplicateScan: StateFlow<BarcodeScanOutcome.PromptDuplicate?> = _pendingDuplicateScan.asStateFlow()

    private val _pendingNotInSessionScan = MutableStateFlow<BarcodeScanOutcome.NotInSession?>(null)
    val pendingNotInSessionScan: StateFlow<BarcodeScanOutcome.NotInSession?> = _pendingNotInSessionScan.asStateFlow()

    private val _approvalPreview = MutableStateFlow<InventoryApprovalPreview?>(null)
    val approvalPreview: StateFlow<InventoryApprovalPreview?> = _approvalPreview.asStateFlow()

    private val _historicalComparison = MutableStateFlow<InventoryComparisonSummary?>(null)
    val historicalComparison: StateFlow<InventoryComparisonSummary?> = _historicalComparison.asStateFlow()

    // --- REACTIVE DATABASE STREAMS ---
    val dashboardStats: StateFlow<InventoryDashboardStats> = repository.getInventoryDashboardStatsFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), InventoryDashboardStats())

    val allInventories: StateFlow<List<InventoryEntity>> = repository.allInventories
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeInventories: StateFlow<List<InventoryEntity>> = repository.activeInventories
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeInventoriesCount: StateFlow<Int> = activeInventories
        .map { it.size }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    val completedInventories: StateFlow<List<InventoryEntity>> = repository.completedInventories
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAdjustments: StateFlow<List<InventoryAdjustmentEntity>> = repository.allInventoryAdjustments
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryValuations: StateFlow<List<CategoryValuationRow>> = repository.getCategoryValuationsFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supplierValuations: StateFlow<List<SupplierValuationRow>> = repository.getSupplierValuationsFlow()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockMonitoringItems: StateFlow<List<StockMonitoringItem>> = _slowMovingDays
        .flatMapLatest { days -> repository.getStockMonitoringFlow(slowMovingDays = days) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedInventory: StateFlow<InventoryEntity?> = _selectedInventoryId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.getInventoryByIdFlow(id)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    val selectedInventoryItems: StateFlow<List<InventoryItemEntity>> = _selectedInventoryId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getInventoryItemsFlow(id)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedInventoryAdjustments: StateFlow<List<InventoryAdjustmentEntity>> = _selectedInventoryId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getInventoryAdjustmentsFlow(id)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- NAVIGATION & FILTERS ---
    fun openInventory(inventoryId: Long) {
        _selectedInventoryId.value = inventoryId
        _pendingDuplicateScan.value = null
        _pendingNotInSessionScan.value = null
        _approvalPreview.value = null
        _historicalComparison.value = null
    }

    fun closeSelectedInventory() {
        _selectedInventoryId.value = null
        _pendingDuplicateScan.value = null
        _pendingNotInSessionScan.value = null
        _approvalPreview.value = null
        _historicalComparison.value = null
    }

    fun setSlowMovingDays(days: Int) {
        _slowMovingDays.value = days.coerceIn(7, 365)
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun dismissDuplicateScan() {
        _pendingDuplicateScan.value = null
    }

    fun dismissNotInSessionScan() {
        _pendingNotInSessionScan.value = null
    }

    fun dismissApprovalPreview() {
        _approvalPreview.value = null
    }

    // --- SESSION CREATION & LIFECYCLE ---
    fun createInventorySession(
        type: InventoryType,
        title: String = "",
        notes: String = "",
        categoryIds: List<Long> = emptyList(),
        productIds: List<Long> = emptyList(),
        supplierIds: List<Long> = emptyList(),
        duplicateScanMode: DuplicateScanMode = DuplicateScanMode.INCREMENT,
        startImmediately: Boolean = true,
        onCreated: (InventoryEntity) -> Unit = {}
    ) {
        if (_isBusy.value) return
        scope.launch {
            _isBusy.value = true
            try {
                val created = repository.createInventorySession(
                    type = type,
                    title = title,
                    notes = notes,
                    categoryIds = categoryIds,
                    productIds = productIds,
                    supplierIds = supplierIds,
                    duplicateScanMode = duplicateScanMode,
                    startImmediately = startImmediately,
                    currentUser = currentUserProvider()
                )
                _selectedInventoryId.value = created.id
                val lang = languageProvider()
                _feedback.value = StocktakingFeedback(
                    message = Bilingual.tr(
                        lang,
                        "تم إنشاء جلسة الجرد ${created.reference} (${created.totalItemsCount} منتج)",
                        "Inventaire ${created.reference} créé (${created.totalItemsCount} produits)"
                    ),
                    type = StocktakingFeedbackType.SUCCESS
                )
                onCreated(created)
            } catch (e: Exception) {
                emitError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun togglePauseResumeSession(inventory: InventoryEntity) {
        val current = InventoryStatus.fromCode(inventory.status)
        if (!current.isEditable) return
        val next = if (current == InventoryStatus.IN_PROGRESS) InventoryStatus.DRAFT else InventoryStatus.IN_PROGRESS
        scope.launch {
            try {
                repository.updateInventorySessionStatus(
                    inventoryId = inventory.id,
                    newStatus = next,
                    currentUser = currentUserProvider()
                )
                val lang = languageProvider()
                _feedback.value = StocktakingFeedback(
                    message = if (next == InventoryStatus.DRAFT) {
                        Bilingual.tr(lang, "تم إيقاف الجرد مؤقتاً وحفظ التقدم", "Inventaire mis en pause et sauvegardé")
                    } else {
                        Bilingual.tr(lang, "تم استئناف جلسة الجرد", "Session d'inventaire reprise")
                    },
                    type = StocktakingFeedbackType.SUCCESS
                )
            } catch (e: Exception) {
                emitError(e)
            }
        }
    }

    fun updateDuplicateScanMode(inventoryId: Long, mode: DuplicateScanMode) {
        scope.launch {
            try {
                repository.updateInventoryDuplicateScanMode(
                    inventoryId = inventoryId,
                    mode = mode,
                    currentUser = currentUserProvider()
                )
            } catch (e: Exception) {
                emitError(e)
            }
        }
    }

    fun updateInventoryNotes(inventoryId: Long, notes: String) {
        scope.launch {
            try {
                repository.updateInventoryNotes(
                    inventoryId = inventoryId,
                    notes = notes,
                    currentUser = currentUserProvider()
                )
                val lang = languageProvider()
                _feedback.value = StocktakingFeedback(
                    message = Bilingual.tr(lang, "تم حفظ ملاحظات الجرد", "Notes d'inventaire enregistrées"),
                    type = StocktakingFeedbackType.SUCCESS
                )
            } catch (e: Exception) {
                emitError(e)
            }
        }
    }

    // --- PHYSICAL COUNTING & BARCODE SCANNING ---
    fun recordItemCount(
        inventoryId: Long,
        productId: Long,
        countedQuantity: Double,
        notes: String? = null,
        onSaved: (InventoryItemEntity) -> Unit = {}
    ) {
        scope.launch {
            try {
                val updated = repository.recordInventoryItemCount(
                    inventoryId = inventoryId,
                    productId = productId,
                    countedQuantity = countedQuantity,
                    notes = notes,
                    currentUser = currentUserProvider()
                )
                onSaved(updated)
            } catch (e: Exception) {
                emitError(e)
            }
        }
    }

    fun clearItemCount(inventoryId: Long, productId: Long) {
        scope.launch {
            try {
                repository.clearInventoryItemCount(
                    inventoryId = inventoryId,
                    productId = productId,
                    currentUser = currentUserProvider()
                )
            } catch (e: Exception) {
                emitError(e)
            }
        }
    }

    fun handleBarcodeScan(
        inventoryId: Long,
        rawBarcode: String,
        incrementBy: Double = 1.0,
        overrideMode: DuplicateScanMode? = null
    ) {
        val code = rawBarcode.trim()
        if (code.isEmpty()) return
        scope.launch {
            try {
                val outcome = repository.handleInventoryBarcodeScan(
                    inventoryId = inventoryId,
                    rawBarcode = code,
                    incrementBy = incrementBy,
                    overrideMode = overrideMode,
                    currentUser = currentUserProvider()
                )
                val lang = languageProvider()
                when (outcome) {
                    is BarcodeScanOutcome.Updated -> {
                        onBeep()
                        val qtyStr = StocktakingCalculator.formatQuantity(outcome.newCountedQuantity)
                        _feedback.value = StocktakingFeedback(
                            message = Bilingual.tr(
                                lang,
                                "تم المسح: ${outcome.item.productName} ← $qtyStr ${outcome.item.unit}",
                                "Scanné : ${outcome.item.productName} → $qtyStr ${outcome.item.unit}"
                            ),
                            type = StocktakingFeedbackType.SUCCESS,
                            highlightedProductId = outcome.item.productId
                        )
                    }
                    is BarcodeScanOutcome.PromptDuplicate -> {
                        onBeep()
                        _pendingDuplicateScan.value = outcome
                    }
                    is BarcodeScanOutcome.NotInSession -> {
                        _pendingNotInSessionScan.value = outcome
                    }
                    is BarcodeScanOutcome.ProductNotFound -> {
                        _feedback.value = StocktakingFeedback(
                            message = Bilingual.tr(
                                lang,
                                "الباركود غير مسجل في الكتالوج: ${outcome.barcode}",
                                "Code-barres introuvable dans le catalogue : ${outcome.barcode}"
                            ),
                            type = StocktakingFeedbackType.ERROR
                        )
                    }
                    is BarcodeScanOutcome.SessionReadOnly -> {
                        _feedback.value = StocktakingFeedback(
                            message = Bilingual.tr(
                                lang,
                                "جلسة الجرد مغلقة ولا تقبل تعديلاً جديداً",
                                "Cet inventaire est verrouillé en lecture seule"
                            ),
                            type = StocktakingFeedbackType.WARNING
                        )
                    }
                }
            } catch (e: Exception) {
                emitError(e)
            }
        }
    }

    fun resolveDuplicateScan(inventoryId: Long, item: InventoryItemEntity, newQuantity: Double) {
        _pendingDuplicateScan.value = null
        recordItemCount(
            inventoryId = inventoryId,
            productId = item.productId,
            countedQuantity = newQuantity,
            onSaved = { updated ->
                val lang = languageProvider()
                val qtyStr = StocktakingCalculator.formatQuantity(updated.countedQuantity ?: newQuantity)
                _feedback.value = StocktakingFeedback(
                    message = Bilingual.tr(
                        lang,
                        "تم تحديث العدّ: ${updated.productName} ← $qtyStr",
                        "Comptage mis à jour : ${updated.productName} → $qtyStr"
                    ),
                    type = StocktakingFeedbackType.SUCCESS,
                    highlightedProductId = updated.productId
                )
            }
        )
    }

    fun addNotInSessionProductAndCount(
        inventoryId: Long,
        productId: Long,
        initialQuantity: Double = 1.0
    ) {
        _pendingNotInSessionScan.value = null
        scope.launch {
            try {
                val user = currentUserProvider()
                repository.addProductToActiveInventory(inventoryId, productId, user)
                val updated = repository.recordInventoryItemCount(
                    inventoryId = inventoryId,
                    productId = productId,
                    countedQuantity = initialQuantity,
                    currentUser = user
                )
                val lang = languageProvider()
                _feedback.value = StocktakingFeedback(
                    message = Bilingual.tr(
                        lang,
                        "تمت إضافة وعدّ: ${updated.productName} (${StocktakingCalculator.formatQuantity(initialQuantity)})",
                        "Ajouté et compté : ${updated.productName} (${StocktakingCalculator.formatQuantity(initialQuantity)})"
                    ),
                    type = StocktakingFeedbackType.SUCCESS,
                    highlightedProductId = updated.productId
                )
            } catch (e: Exception) {
                emitError(e)
            }
        }
    }

    // --- APPROVAL, CANCELLATION & COMPARISON (USED IN PHASES 4 & 5) ---
    fun loadApprovalPreview(inventoryId: Long) {
        scope.launch {
            _isBusy.value = true
            try {
                _approvalPreview.value = repository.buildInventoryApprovalPreview(inventoryId)
            } catch (e: Exception) {
                emitError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun approveAndApplyInventory(
        inventoryId: Long,
        reason: String,
        onApproved: (InventoryEntity) -> Unit = {}
    ) {
        if (_isBusy.value) return
        scope.launch {
            _isBusy.value = true
            try {
                val completed = repository.approveAndApplyInventory(
                    inventoryId = inventoryId,
                    reason = reason,
                    currentUser = currentUserProvider()
                )
                _approvalPreview.value = null
                val lang = languageProvider()
                _feedback.value = StocktakingFeedback(
                    message = Bilingual.tr(
                        lang,
                        "تم اعتماد الجرد ${completed.reference} وتطبيق التسويات بنجاح",
                        "Inventaire ${completed.reference} validé et stock ajusté avec succès"
                    ),
                    type = StocktakingFeedbackType.SUCCESS
                )
                onApproved(completed)
            } catch (e: Exception) {
                emitError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun cancelInventorySession(
        inventoryId: Long,
        reason: String,
        onCancelled: (InventoryEntity) -> Unit = {}
    ) {
        if (_isBusy.value) return
        scope.launch {
            _isBusy.value = true
            try {
                val cancelled = repository.cancelInventorySession(
                    inventoryId = inventoryId,
                    reason = reason,
                    currentUser = currentUserProvider()
                )
                val lang = languageProvider()
                _feedback.value = StocktakingFeedback(
                    message = Bilingual.tr(
                        lang,
                        "تم إلغاء جلسة الجرد ${cancelled.reference}",
                        "Session d'inventaire ${cancelled.reference} annulée"
                    ),
                    type = StocktakingFeedbackType.WARNING
                )
                onCancelled(cancelled)
            } catch (e: Exception) {
                emitError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateInventorySession(
        inventoryId: Long,
        title: String,
        notes: String,
        duplicateScanMode: DuplicateScanMode? = null,
        onUpdated: (InventoryEntity) -> Unit = {}
    ) {
        if (_isBusy.value) return
        scope.launch {
            _isBusy.value = true
            try {
                val updated = repository.updateInventorySession(
                    inventoryId = inventoryId,
                    title = title,
                    notes = notes,
                    duplicateScanMode = duplicateScanMode,
                    currentUser = currentUserProvider()
                )
                val lang = languageProvider()
                _feedback.value = StocktakingFeedback(
                    message = Bilingual.tr(
                        lang,
                        "تم تعديل الجرد ${updated.reference} بنجاح",
                        "Inventaire ${updated.reference} mis à jour avec succès"
                    ),
                    type = StocktakingFeedbackType.SUCCESS
                )
                onUpdated(updated)
            } catch (e: Exception) {
                emitError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteInventorySession(
        inventory: InventoryEntity,
        revertStockAdjustments: Boolean = false,
        reason: String = "",
        onDeleted: () -> Unit = {}
    ) {
        if (_isBusy.value) return
        scope.launch {
            _isBusy.value = true
            try {
                val revertedCount = repository.deleteInventorySession(
                    inventoryId = inventory.id,
                    revertStockAdjustments = revertStockAdjustments,
                    reason = reason,
                    currentUser = currentUserProvider()
                )
                if (_selectedInventoryId.value == inventory.id) {
                    closeSelectedInventory()
                }
                val lang = languageProvider()
                val msg = if (revertedCount > 0) {
                    Bilingual.tr(
                        lang,
                        "تم حذف الجرد ${inventory.reference} والتراجع عن $revertedCount تسوية مخزون",
                        "Inventaire ${inventory.reference} supprimé ($revertedCount ajustement(s) annulé(s))"
                    )
                } else {
                    Bilingual.tr(
                        lang,
                        "تم حذف جلسة الجرد ${inventory.reference} نهائياً",
                        "Session d'inventaire ${inventory.reference} supprimée"
                    )
                }
                _feedback.value = StocktakingFeedback(
                    message = msg,
                    type = StocktakingFeedbackType.WARNING
                )
                onDeleted()
            } catch (e: Exception) {
                emitError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun loadHistoricalComparison(inventoryId: Long) {
        scope.launch {
            try {
                _historicalComparison.value = repository.getInventoryHistoricalComparison(inventoryId)
            } catch (e: Exception) {
                emitError(e)
            }
        }
    }

    private fun emitError(e: Exception) {
        val lang = languageProvider()
        val msg = e.localizedMessage?.takeIf { it.isNotBlank() }
            ?: Bilingual.tr(lang, "حدث خطأ غير متوقع أثناء العملية", "Une erreur inattendue s'est produite")
        _feedback.value = StocktakingFeedback(
            message = msg,
            type = StocktakingFeedbackType.ERROR
        )
    }
}
