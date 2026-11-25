package com.example.printer

import android.content.Context
import android.util.Log
import com.example.data.GlobalSettingsEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.ui.theme.Bilingual
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BatchPrintItemStatus {
    QUEUED,
    PRINTING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class BatchPrintQueueItem(
    val sale: SaleEntity,
    val items: List<SaleItemEntity>,
    val status: BatchPrintItemStatus = BatchPrintItemStatus.QUEUED,
    val errorMessage: String? = null,
    val attempts: Int = 0,
    val jobId: String? = null
)

data class BatchPrintQueueState(
    val isActive: Boolean = false,
    val isRunning: Boolean = false,
    val items: List<BatchPrintQueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val targetPrinterName: String = "",
    val transport: PrinterTransport = PrinterTransport.BLUETOOTH,
    val startedAtMs: Long = 0L,
    val finishedAtMs: Long? = null
) {
    val totalCount: Int get() = items.size
    val successCount: Int get() = items.count { it.status == BatchPrintItemStatus.SUCCESS }
    val failedCount: Int get() = items.count { it.status == BatchPrintItemStatus.FAILED }
    val cancelledCount: Int get() = items.count { it.status == BatchPrintItemStatus.CANCELLED }
    val pendingCount: Int get() = items.count {
        it.status == BatchPrintItemStatus.QUEUED || it.status == BatchPrintItemStatus.PRINTING
    }
    val completedCount: Int get() = successCount + failedCount + cancelledCount
    val progressFraction: Float
        get() = if (totalCount <= 0) 0f else (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    val hasRetryableItems: Boolean
        get() = !isRunning && items.any {
            it.status == BatchPrintItemStatus.FAILED || it.status == BatchPrintItemStatus.CANCELLED
        }
}

/**
 * Reusable sequential batch print queue manager for RM.POS.
 *
 * Prints multiple selected invoices in order using [PosThermalPrinterServiceProvider]:
 * - Thermal transports (`BLUETOOTH`, `USB`, `WIFI`): prints one invoice at a time with pacing
 *   between receipts so printer buffers never overflow, tracking live per-invoice status.
 * - Android System Print (`SYSTEM`): merges all selected receipts into a single combined print job
 *   so the Android Print Spooler opens once cleanly instead of spamming multiple dialogs.
 */
object BatchPrintQueueManager {
    private const val TAG = "BatchPrintQueueMgr"

    internal var serviceProvider: () -> PosThermalPrinterService = { PosThermalPrinterServiceProvider.instance }
    internal var workerDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var defaultInterJobDelayMs: Long = 450L

    private val _state = MutableStateFlow(BatchPrintQueueState())
    val state: StateFlow<BatchPrintQueueState> = _state.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    fun startBatch(
        scope: CoroutineScope,
        context: Context,
        entries: List<Pair<SaleEntity, List<SaleItemEntity>>>,
        settings: GlobalSettingsEntity,
        profile: ThermalPrintProfile,
        target: ThermalPrinterDevice,
        interJobDelayMs: Long = defaultInterJobDelayMs
    ) {
        if (entries.isEmpty()) return
        activeJob?.cancel()

        val initialItems = entries.map { (sale, lineItems) ->
            BatchPrintQueueItem(
                sale = sale,
                items = lineItems,
                status = BatchPrintItemStatus.QUEUED
            )
        }

        _state.value = BatchPrintQueueState(
            isActive = true,
            isRunning = true,
            items = initialItems,
            currentIndex = 0,
            targetPrinterName = target.name.ifBlank { target.address.ifBlank { target.transport.name } },
            transport = target.transport,
            startedAtMs = System.currentTimeMillis(),
            finishedAtMs = null
        )

        activeJob = launchQueueProcessing(
            scope = scope,
            context = context,
            settings = settings,
            profile = profile,
            target = target,
            interJobDelayMs = interJobDelayMs
        )
    }

    fun retryFailed(
        scope: CoroutineScope,
        context: Context,
        settings: GlobalSettingsEntity,
        profile: ThermalPrintProfile,
        target: ThermalPrinterDevice,
        interJobDelayMs: Long = defaultInterJobDelayMs
    ) {
        val current = _state.value
        if (current.isRunning || current.items.none {
                it.status == BatchPrintItemStatus.FAILED || it.status == BatchPrintItemStatus.CANCELLED
            }
        ) {
            return
        }
        activeJob?.cancel()

        val updatedItems = current.items.map { item ->
            if (item.status == BatchPrintItemStatus.FAILED || item.status == BatchPrintItemStatus.CANCELLED) {
                item.copy(status = BatchPrintItemStatus.QUEUED, errorMessage = null)
            } else {
                item
            }
        }
        val firstPending = updatedItems.indexOfFirst { it.status == BatchPrintItemStatus.QUEUED }

        _state.value = current.copy(
            isActive = true,
            isRunning = true,
            items = updatedItems,
            currentIndex = firstPending,
            targetPrinterName = target.name.ifBlank { target.address.ifBlank { target.transport.name } },
            transport = target.transport,
            finishedAtMs = null
        )

        activeJob = launchQueueProcessing(
            scope = scope,
            context = context,
            settings = settings,
            profile = profile,
            target = target,
            interJobDelayMs = interJobDelayMs
        )
    }

    fun cancelQueue() {
        activeJob?.cancel()
        activeJob = null
        val current = _state.value
        if (!current.isRunning) return

        val updated = current.items.map { item ->
            if (item.status == BatchPrintItemStatus.QUEUED || item.status == BatchPrintItemStatus.PRINTING) {
                item.copy(status = BatchPrintItemStatus.CANCELLED)
            } else {
                item
            }
        }
        _state.value = current.copy(
            isRunning = false,
            items = updated,
            currentIndex = -1,
            finishedAtMs = System.currentTimeMillis()
        )
    }

    fun dismissQueue() {
        if (_state.value.isRunning) {
            cancelQueue()
        }
        _state.value = BatchPrintQueueState()
    }

    private fun launchQueueProcessing(
        scope: CoroutineScope,
        context: Context,
        settings: GlobalSettingsEntity,
        profile: ThermalPrintProfile,
        target: ThermalPrinterDevice,
        interJobDelayMs: Long
    ): Job = scope.launch(workerDispatcher) {
        val service = serviceProvider()
        val lang = profile.language.ifBlank { settings.language.ifBlank { "AR" } }.uppercase(Locale.ROOT)

        try {
            if (target.transport == PrinterTransport.SYSTEM) {
                processCombinedSystemBatch(context, service, settings, profile, target, lang)
                return@launch
            }

            val snapshot = _state.value.items
            for (index in snapshot.indices) {
                val currentItem = _state.value.items.getOrNull(index) ?: continue
                if (currentItem.status != BatchPrintItemStatus.QUEUED) continue

                updateItemAt(index) {
                    it.copy(
                        status = BatchPrintItemStatus.PRINTING,
                        errorMessage = null
                    )
                }
                _state.value = _state.value.copy(currentIndex = index)

                val result = try {
                    service.printSaleInvoice(
                        context = context,
                        sale = currentItem.sale,
                        items = currentItem.items,
                        settings = settings,
                        lang = lang,
                        target = target,
                        profileOverride = profile
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.e(TAG, "Batch print threw on invoice ${currentItem.sale.invoiceNumber}", t)
                    PrintExecutionResult(
                        jobId = "BATCH-ERR-${currentItem.sale.id}",
                        success = false,
                        message = t.localizedMessage ?: "Print error",
                        attempts = 1,
                        errorCause = t.localizedMessage
                    )
                }

                updateItemAt(index) {
                    it.copy(
                        status = if (result.success) BatchPrintItemStatus.SUCCESS else BatchPrintItemStatus.FAILED,
                        errorMessage = if (result.success) null else result.message,
                        attempts = it.attempts + result.attempts.coerceAtLeast(1),
                        jobId = result.jobId
                    )
                }

                val hasMoreQueued = _state.value.items
                    .drop(index + 1)
                    .any { it.status == BatchPrintItemStatus.QUEUED }
                if (hasMoreQueued && interJobDelayMs > 0L) {
                    delay(interJobDelayMs)
                }
            }

            _state.value = _state.value.copy(
                isRunning = false,
                currentIndex = -1,
                finishedAtMs = System.currentTimeMillis()
            )
        } catch (_: CancellationException) {
            val current = _state.value
            val cancelledItems = current.items.map { item ->
                if (item.status == BatchPrintItemStatus.QUEUED || item.status == BatchPrintItemStatus.PRINTING) {
                    item.copy(status = BatchPrintItemStatus.CANCELLED)
                } else {
                    item
                }
            }
            _state.value = current.copy(
                isRunning = false,
                items = cancelledItems,
                currentIndex = -1,
                finishedAtMs = System.currentTimeMillis()
            )
        }
    }

    private suspend fun processCombinedSystemBatch(
        context: Context,
        service: PosThermalPrinterService,
        settings: GlobalSettingsEntity,
        profile: ThermalPrintProfile,
        target: ThermalPrinterDevice,
        lang: String
    ) {
        val pendingIndices = _state.value.items.mapIndexedNotNull { idx, item ->
            if (item.status == BatchPrintItemStatus.QUEUED) idx else null
        }
        if (pendingIndices.isEmpty()) {
            _state.value = _state.value.copy(isRunning = false, currentIndex = -1, finishedAtMs = System.currentTimeMillis())
            return
        }

        // Mark all pending as PRINTING while building the combined batch document
        pendingIndices.forEach { idx ->
            updateItemAt(idx) { it.copy(status = BatchPrintItemStatus.PRINTING, errorMessage = null) }
        }
        _state.value = _state.value.copy(currentIndex = pendingIndices.first())

        val combinedText = buildString {
            pendingIndices.forEachIndexed { orderIdx, itemIdx ->
                val item = _state.value.items[itemIdx]
                val receipt = DefaultPosThermalPrinterService.buildSaleReceiptData(
                    sale = item.sale,
                    items = item.items,
                    settings = settings,
                    lang = lang,
                    openCashDrawer = false
                )
                append(service.formatPlainTextReceipt(receipt, profile))
                if (orderIdx < pendingIndices.lastIndex) {
                    append("\n\n================================\n\n")
                }
            }
        }

        val jobTitle = Bilingual.tr(
            lang,
            "طباعة مجمّعة (${pendingIndices.size} فواتير)",
            "Impression groupée (${pendingIndices.size} factures)",
            "Batch Print (${pendingIndices.size} invoices)"
        )

        val result = if (DefaultPosThermalPrinterService.transportSendOverrideForTest != null) {
            val firstItem = _state.value.items[pendingIndices.first()]
            service.printSaleInvoice(
                context = context,
                sale = firstItem.sale,
                items = firstItem.items,
                settings = settings,
                lang = lang,
                target = target,
                profileOverride = profile
            )
        } else {
            try {
                BluetoothPrinterService.printReceiptSystem(
                    context = context,
                    jobTitle = jobTitle,
                    textToPrint = combinedText
                )
                val record = PrintJobRecord(
                    id = "PJ-BATCH-${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    title = jobTitle,
                    invoiceNumber = _state.value.items[pendingIndices.first()].sale.invoiceNumber,
                    transport = PrinterTransport.SYSTEM,
                    printerName = target.name.ifBlank { "Android System Print" },
                    printerAddress = "SYSTEM",
                    paperWidthMm = profile.paperWidth.mm,
                    language = lang,
                    status = PrintJobStatus.SUCCESS,
                    attemptCount = 1,
                    errorMessage = null,
                    byteSize = combinedText.toByteArray(Charsets.UTF_8).size
                )
                PrintJobHistoryStore.upsertRecord(context, record, null)
                PrintExecutionResult(
                    jobId = record.id,
                    success = true,
                    message = Bilingual.tr(
                        lang,
                        "تم فتح شاشة طباعة أندرويد لـ ${pendingIndices.size} فواتير",
                        "Impression Android ouverte pour ${pendingIndices.size} factures",
                        "Android print opened for ${pendingIndices.size} invoices"
                    ),
                    attempts = 1
                )
            } catch (t: Throwable) {
                PrintExecutionResult(
                    jobId = "PJ-BATCH-ERR-${System.currentTimeMillis()}",
                    success = false,
                    message = t.localizedMessage ?: "System batch print error",
                    attempts = 1,
                    errorCause = t.localizedMessage
                )
            }
        }

        pendingIndices.forEach { idx ->
            updateItemAt(idx) {
                it.copy(
                    status = if (result.success) BatchPrintItemStatus.SUCCESS else BatchPrintItemStatus.FAILED,
                    errorMessage = if (result.success) null else result.message,
                    attempts = it.attempts + 1,
                    jobId = result.jobId
                )
            }
        }
        _state.value = _state.value.copy(
            isRunning = false,
            currentIndex = -1,
            finishedAtMs = System.currentTimeMillis()
        )
    }

    private fun updateItemAt(index: Int, transform: (BatchPrintQueueItem) -> BatchPrintQueueItem) {
        val current = _state.value
        if (index !in current.items.indices) return
        val mutable = current.items.toMutableList()
        mutable[index] = transform(mutable[index])
        _state.value = current.copy(items = mutable)
    }

    internal fun resetForTesting() {
        activeJob?.cancel()
        activeJob = null
        serviceProvider = { PosThermalPrinterServiceProvider.instance }
        workerDispatcher = Dispatchers.IO
        defaultInterJobDelayMs = 450L
        _state.value = BatchPrintQueueState()
    }
}
