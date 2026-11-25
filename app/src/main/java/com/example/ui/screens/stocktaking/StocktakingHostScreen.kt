package com.example.ui.screens.stocktaking

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.stocktaking.InventoryType
import com.example.ui.viewmodels.PosViewModel
import kotlinx.coroutines.delay

private enum class StocktakingSubScreen {
    DASHBOARD,
    CREATE,
    COUNTING,
    DISCREPANCIES,
    VALUATION_MONITORING,
    ADJUSTMENTS,
    HISTORY,
    REPORT,
    CART
}

@Composable
fun StocktakingHostScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: (String) -> Unit
) {
    val stockVm = viewModel.stocktakingViewModel

    val dashboardStats by stockVm.dashboardStats.collectAsStateWithLifecycle()
    val allInventories by stockVm.allInventories.collectAsStateWithLifecycle()
    val activeInventories by stockVm.activeInventories.collectAsStateWithLifecycle()
    val categoryValuations by stockVm.categoryValuations.collectAsStateWithLifecycle()
    val supplierValuations by stockVm.supplierValuations.collectAsStateWithLifecycle()
    val stockMonitoringItems by stockVm.stockMonitoringItems.collectAsStateWithLifecycle()
    val slowMovingDays by stockVm.slowMovingDays.collectAsStateWithLifecycle()
    val allAdjustments by stockVm.allAdjustments.collectAsStateWithLifecycle()
    val selectedInventory by stockVm.selectedInventory.collectAsStateWithLifecycle()
    val selectedItems by stockVm.selectedInventoryItems.collectAsStateWithLifecycle()
    val selectedAdjustments by stockVm.selectedInventoryAdjustments.collectAsStateWithLifecycle()
    val approvalPreview by stockVm.approvalPreview.collectAsStateWithLifecycle()
    val historicalComparison by stockVm.historicalComparison.collectAsStateWithLifecycle()
    val pendingDuplicateScan by stockVm.pendingDuplicateScan.collectAsStateWithLifecycle()
    val pendingNotInSessionScan by stockVm.pendingNotInSessionScan.collectAsStateWithLifecycle()
    val feedback by stockVm.feedback.collectAsStateWithLifecycle()
    val isBusy by stockVm.isBusy.collectAsStateWithLifecycle()

    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val isManager = currentUser?.isManager == true
    val shopName = settings.shopName

    var currentSubScreen by rememberSaveable { mutableStateOf(StocktakingSubScreen.DASHBOARD) }
    var createInitialTypeCode by rememberSaveable { mutableStateOf(InventoryType.GENERAL.code) }
    var adjustmentsForSelectedOnly by rememberSaveable { mutableStateOf(false) }
    var adjustmentsReturnTarget by rememberSaveable { mutableStateOf(StocktakingSubScreen.DASHBOARD) }
    var reportReturnTarget by rememberSaveable { mutableStateOf(StocktakingSubScreen.DASHBOARD) }
    var sessionToEdit by remember { mutableStateOf<com.example.data.InventoryEntity?>(null) }
    var sessionToDelete by remember { mutableStateOf<com.example.data.InventoryEntity?>(null) }

    val liveProductsById = remember(products) { products.associateBy { it.id } }

    // Auto-dismiss transient feedback banner after 4 seconds
    LaunchedEffect(feedback?.timestamp) {
        if (feedback != null) {
            delay(4000L)
            stockVm.clearFeedback()
        }
    }

    val openReportForSession: (Long, StocktakingSubScreen) -> Unit = { sessionId, returnTo ->
        if (selectedInventory?.id != sessionId) {
            stockVm.openInventory(sessionId)
        }
        stockVm.loadHistoricalComparison(sessionId)
        reportReturnTarget = returnTo
        currentSubScreen = StocktakingSubScreen.REPORT
    }

    val navigateBackFromReport: () -> Unit = {
        val target = reportReturnTarget
        if (target == StocktakingSubScreen.DASHBOARD || target == StocktakingSubScreen.HISTORY) {
            stockVm.closeSelectedInventory()
        }
        currentSubScreen = target
    }

    // Properly handle Android back button inside sub-screens
    BackHandler {
        when (currentSubScreen) {
            StocktakingSubScreen.DASHBOARD -> onNavigate("DASHBOARD")
            StocktakingSubScreen.CREATE,
            StocktakingSubScreen.VALUATION_MONITORING,
            StocktakingSubScreen.HISTORY -> {
                currentSubScreen = StocktakingSubScreen.DASHBOARD
            }
            StocktakingSubScreen.REPORT -> {
                navigateBackFromReport()
            }
            StocktakingSubScreen.ADJUSTMENTS -> {
                if (adjustmentsForSelectedOnly && selectedInventory != null) {
                    currentSubScreen = adjustmentsReturnTarget
                } else {
                    adjustmentsForSelectedOnly = false
                    currentSubScreen = StocktakingSubScreen.DASHBOARD
                }
            }
            StocktakingSubScreen.DISCREPANCIES -> {
                stockVm.dismissApprovalPreview()
                currentSubScreen = if (selectedInventory != null) {
                    StocktakingSubScreen.COUNTING
                } else {
                    StocktakingSubScreen.DASHBOARD
                }
            }
            StocktakingSubScreen.CART -> {
                currentSubScreen = if (selectedInventory != null) {
                    StocktakingSubScreen.COUNTING
                } else {
                    StocktakingSubScreen.DASHBOARD
                }
            }
            StocktakingSubScreen.COUNTING -> {
                stockVm.closeSelectedInventory()
                currentSubScreen = StocktakingSubScreen.DASHBOARD
            }
        }
    }

    val openDashboard: () -> Unit = {
        stockVm.closeSelectedInventory()
        adjustmentsForSelectedOnly = false
        currentSubScreen = StocktakingSubScreen.DASHBOARD
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentSubScreen) {
            StocktakingSubScreen.DASHBOARD -> {
                StocktakingDashboardView(
                    stats = dashboardStats,
                    allInventories = allInventories,
                    activeInventories = activeInventories,
                    isManager = isManager,
                    currency = currency,
                    lang = lang,
                    onBack = { onNavigate("DASHBOARD") },
                    onStartGeneral = {
                        createInitialTypeCode = InventoryType.GENERAL.code
                        currentSubScreen = StocktakingSubScreen.CREATE
                    },
                    onStartPartial = {
                        createInitialTypeCode = InventoryType.PARTIAL_CATEGORY.code
                        currentSubScreen = StocktakingSubScreen.CREATE
                    },
                    onOpenValuationMonitoring = {
                        currentSubScreen = StocktakingSubScreen.VALUATION_MONITORING
                    },
                    onOpenAdjustments = {
                        adjustmentsForSelectedOnly = false
                        adjustmentsReturnTarget = StocktakingSubScreen.DASHBOARD
                        currentSubScreen = StocktakingSubScreen.ADJUSTMENTS
                    },
                    onOpenHistory = {
                        currentSubScreen = StocktakingSubScreen.HISTORY
                    },
                    onOpenSession = { session ->
                        stockVm.openInventory(session.id)
                        currentSubScreen = StocktakingSubScreen.COUNTING
                    },
                    onOpenSessionReport = { session ->
                        openReportForSession(session.id, StocktakingSubScreen.DASHBOARD)
                    },
                    onEditSession = { session ->
                        sessionToEdit = session
                    },
                    onDeleteSession = { session ->
                        sessionToDelete = session
                    }
                )
            }

            StocktakingSubScreen.CREATE -> {
                CreateStocktakingScreen(
                    initialType = InventoryType.fromCode(createInitialTypeCode),
                    products = products,
                    categories = categories,
                    suppliers = suppliers,
                    supplierValuations = supplierValuations,
                    isBusy = isBusy,
                    lang = lang,
                    onBack = { currentSubScreen = StocktakingSubScreen.DASHBOARD },
                    onCreateSession = { type, title, notes, catIds, prodIds, supIds, scanMode, startNow ->
                        stockVm.createInventorySession(
                            type = type,
                            title = title,
                            notes = notes,
                            categoryIds = catIds,
                            productIds = prodIds,
                            supplierIds = supIds,
                            duplicateScanMode = scanMode,
                            startImmediately = startNow,
                            onCreated = { created ->
                                stockVm.openInventory(created.id)
                                currentSubScreen = StocktakingSubScreen.COUNTING
                            }
                        )
                    }
                )
            }

            StocktakingSubScreen.COUNTING -> {
                val inv = selectedInventory
                if (inv != null) {
                    StocktakingCountingScreen(
                        inventory = inv,
                        items = selectedItems,
                        liveProductsById = liveProductsById,
                        highlightedProductId = feedback?.highlightedProductId,
                        pendingDuplicateScan = pendingDuplicateScan,
                        pendingNotInSessionScan = pendingNotInSessionScan,
                        currency = currency,
                        lang = lang,
                        onBack = openDashboard,
                        onTogglePauseResume = { stockVm.togglePauseResumeSession(inv) },
                        onUpdateScanMode = { mode -> stockVm.updateDuplicateScanMode(inv.id, mode) },
                        onRecordItemCount = { productId, qty, notes ->
                            stockVm.recordItemCount(inv.id, productId, qty, notes)
                        },
                        onClearItemCount = { productId ->
                            stockVm.clearItemCount(inv.id, productId)
                        },
                        onBarcodeScanned = { code ->
                            stockVm.handleBarcodeScan(inv.id, code)
                        },
                        onResolveDuplicateScan = { item, newQty ->
                            stockVm.resolveDuplicateScan(inv.id, item, newQty)
                        },
                        onDismissDuplicateScan = { stockVm.dismissDuplicateScan() },
                        onAddNotInSessionProduct = { productId ->
                            stockVm.addNotInSessionProductAndCount(inv.id, productId)
                        },
                        onDismissNotInSessionScan = { stockVm.dismissNotInSessionScan() },
                        onOpenDiscrepancies = {
                            currentSubScreen = StocktakingSubScreen.DISCREPANCIES
                        },
                        onOpenSessionAdjustments = {
                            adjustmentsForSelectedOnly = true
                            adjustmentsReturnTarget = StocktakingSubScreen.COUNTING
                            currentSubScreen = StocktakingSubScreen.ADJUSTMENTS
                        },
                        onOpenReport = {
                            openReportForSession(inv.id, StocktakingSubScreen.COUNTING)
                        },
                        onOpenCart = {
                            currentSubScreen = StocktakingSubScreen.CART
                        },
                        onEditSession = if (isManager || com.example.domain.stocktaking.InventoryStatus.fromCode(inv.status).isEditable) {
                            { sessionToEdit = inv }
                        } else null,
                        onDeleteSession = if (isManager) {
                            { sessionToDelete = inv }
                        } else null
                    )
                } else {
                    LaunchedEffect(Unit) { currentSubScreen = StocktakingSubScreen.DASHBOARD }
                }
            }

            StocktakingSubScreen.DISCREPANCIES -> {
                val inv = selectedInventory
                if (inv != null) {
                    StocktakingDiscrepanciesScreen(
                        inventory = inv,
                        items = selectedItems,
                        liveProductsById = liveProductsById,
                        approvalPreview = approvalPreview,
                        isManager = isManager,
                        isBusy = isBusy,
                        currency = currency,
                        lang = lang,
                        onBack = {
                            stockVm.dismissApprovalPreview()
                            currentSubScreen = StocktakingSubScreen.COUNTING
                        },
                        onRecordItemCount = { productId, qty, notes ->
                            stockVm.recordItemCount(inv.id, productId, qty, notes)
                        },
                        onClearItemCount = { productId ->
                            stockVm.clearItemCount(inv.id, productId)
                        },
                        onRequestApprovalPreview = {
                            stockVm.loadApprovalPreview(inv.id)
                        },
                        onConfirmApprove = { reason ->
                            stockVm.approveAndApplyInventory(
                                inventoryId = inv.id,
                                reason = reason,
                                onApproved = { completed ->
                                    openReportForSession(completed.id, StocktakingSubScreen.DASHBOARD)
                                }
                            )
                        },
                        onDismissApprovalPreview = { stockVm.dismissApprovalPreview() },
                        onConfirmCancelSession = { reason ->
                            stockVm.cancelInventorySession(
                                inventoryId = inv.id,
                                reason = reason,
                                onCancelled = {
                                    openDashboard()
                                }
                            )
                        },
                        onOpenReport = {
                            openReportForSession(inv.id, StocktakingSubScreen.DISCREPANCIES)
                        }
                    )
                } else {
                    LaunchedEffect(Unit) { currentSubScreen = StocktakingSubScreen.DASHBOARD }
                }
            }

            StocktakingSubScreen.VALUATION_MONITORING -> {
                StocktakingValuationMonitoringScreen(
                    stats = dashboardStats,
                    categoryValuations = categoryValuations,
                    supplierValuations = supplierValuations,
                    monitoringItems = stockMonitoringItems,
                    slowMovingDays = slowMovingDays,
                    currency = currency,
                    lang = lang,
                    onBack = { currentSubScreen = StocktakingSubScreen.DASHBOARD },
                    onChangeSlowMovingDays = { days -> stockVm.setSlowMovingDays(days) },
                    onStartPartialInventoryForProducts = { title, productIds ->
                        stockVm.createInventorySession(
                            type = InventoryType.PARTIAL_PRODUCTS,
                            title = title,
                            productIds = productIds,
                            startImmediately = true,
                            onCreated = { created ->
                                stockVm.openInventory(created.id)
                                currentSubScreen = StocktakingSubScreen.COUNTING
                            }
                        )
                    }
                )
            }

            StocktakingSubScreen.ADJUSTMENTS -> {
                val activeInv = selectedInventory
                val showScoped = adjustmentsForSelectedOnly && activeInv != null
                StocktakingAdjustmentHistoryScreen(
                    adjustments = if (showScoped) selectedAdjustments else allAdjustments,
                    currency = currency,
                    lang = lang,
                    subtitle = if (showScoped) {
                        "${activeInv!!.reference} • ${activeInv.title}"
                    } else null,
                    onBack = {
                        if (showScoped) {
                            currentSubScreen = adjustmentsReturnTarget
                        } else {
                            adjustmentsForSelectedOnly = false
                            currentSubScreen = StocktakingSubScreen.DASHBOARD
                        }
                    }
                )
            }

            StocktakingSubScreen.HISTORY -> {
                StocktakingHistoryScreen(
                    allInventories = allInventories,
                    isManager = isManager,
                    currency = currency,
                    lang = lang,
                    onBack = { currentSubScreen = StocktakingSubScreen.DASHBOARD },
                    onOpenReport = { session ->
                        openReportForSession(session.id, StocktakingSubScreen.HISTORY)
                    },
                    onOpenSession = { session ->
                        stockVm.openInventory(session.id)
                        currentSubScreen = StocktakingSubScreen.COUNTING
                    },
                    onEditSession = { session ->
                        sessionToEdit = session
                    },
                    onDeleteSession = { session ->
                        sessionToDelete = session
                    }
                )
            }

            StocktakingSubScreen.REPORT -> {
                val inv = selectedInventory
                if (inv != null) {
                    StocktakingReportScreen(
                        inventory = inv,
                        items = selectedItems,
                        historicalComparison = historicalComparison,
                        currency = currency,
                        lang = lang,
                        shopName = shopName,
                        onBack = navigateBackFromReport,
                        onOpenAdjustments = {
                            adjustmentsForSelectedOnly = true
                            adjustmentsReturnTarget = StocktakingSubScreen.REPORT
                            currentSubScreen = StocktakingSubScreen.ADJUSTMENTS
                        },
                        onEditSession = if (isManager || com.example.domain.stocktaking.InventoryStatus.fromCode(inv.status).isEditable) {
                            { sessionToEdit = inv }
                        } else null,
                        onDeleteSession = if (isManager) {
                            { sessionToDelete = inv }
                        } else null
                    )
                } else {
                    LaunchedEffect(Unit) { currentSubScreen = StocktakingSubScreen.DASHBOARD }
                }
            }

            StocktakingSubScreen.CART -> {
                val inv = selectedInventory
                if (inv != null) {
                    StocktakingCartScreen(
                        inventory = inv,
                        items = selectedItems,
                        liveProductsById = liveProductsById,
                        currency = currency,
                        lang = lang,
                        shopName = shopName,
                        onBack = { currentSubScreen = StocktakingSubScreen.COUNTING },
                        onRecordItemCount = { productId, qty, notes ->
                            stockVm.recordItemCount(inv.id, productId, qty, notes)
                        },
                        onClearItemCount = { productId ->
                            stockVm.clearItemCount(inv.id, productId)
                        },
                        onOpenDiscrepancies = {
                            currentSubScreen = StocktakingSubScreen.DISCREPANCIES
                        }
                    )
                } else {
                    LaunchedEffect(Unit) { currentSubScreen = StocktakingSubScreen.DASHBOARD }
                }
            }
        }

        sessionToEdit?.let { target ->
            EditInventorySessionDialog(
                inventory = target,
                isBusy = isBusy,
                lang = lang,
                onDismiss = { sessionToEdit = null },
                onConfirm = { newTitle, newNotes, newScanMode ->
                    stockVm.updateInventorySession(
                        inventoryId = target.id,
                        title = newTitle,
                        notes = newNotes,
                        duplicateScanMode = newScanMode,
                        onUpdated = { sessionToEdit = null }
                    )
                }
            )
        }

        sessionToDelete?.let { target ->
            DeleteInventorySessionDialog(
                inventory = target,
                isBusy = isBusy,
                lang = lang,
                onDismiss = { sessionToDelete = null },
                onConfirmDelete = { revertStock, reason ->
                    val wasSelected = selectedInventory?.id == target.id
                    stockVm.deleteInventorySession(
                        inventory = target,
                        revertStockAdjustments = revertStock,
                        reason = reason,
                        onDeleted = {
                            sessionToDelete = null
                            if (wasSelected && currentSubScreen != StocktakingSubScreen.HISTORY) {
                                currentSubScreen = if (reportReturnTarget == StocktakingSubScreen.HISTORY && currentSubScreen == StocktakingSubScreen.REPORT) {
                                    StocktakingSubScreen.HISTORY
                                } else {
                                    StocktakingSubScreen.DASHBOARD
                                }
                            }
                        }
                    )
                }
            )
        }

        AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            feedback?.let { fb ->
                StocktakingFeedbackBanner(
                    feedback = fb,
                    onDismiss = { stockVm.clearFeedback() }
                )
            }
        }
    }
}
