package com.example.printer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap

internal data class CachedRetryPayload(
    val jobRecord: PrintJobRecord,
    val bytes: ByteArray,
    val target: ThermalPrinterDevice,
    val plainTextFallback: String = ""
)

/**
 * Persists thermal print job history (success, failure, attempts, timestamps, transport)
 * and keeps an in-memory retry buffer so failed print jobs can be retried in one tap.
 */
object PrintJobHistoryStore {
    private const val TAG = "PrintJobHistoryStore"
    private const val PREFS_NAME = "pos_print_job_history"
    private const val KEY_ENTRIES = "entries_json"
    private const val MAX_HISTORY_SIZE = 100
    private const val MAX_RETRY_CACHE_SIZE = 25

    private val lock = Any()
    private val _history = MutableStateFlow<List<PrintJobRecord>>(emptyList())
    val history: StateFlow<List<PrintJobRecord>> = _history.asStateFlow()

    private val retryCache = object : LinkedHashMap<String, CachedRetryPayload>(MAX_RETRY_CACHE_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedRetryPayload>?): Boolean {
            return size > MAX_RETRY_CACHE_SIZE
        }
    }

    @Volatile
    private var loadedFromDisk = false

    fun ensureLoaded(context: Context?) {
        if (loadedFromDisk || context == null) return
        synchronized(lock) {
            if (loadedFromDisk) return
            loadedFromDisk = true
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY_ENTRIES, null) ?: return
                val arr = JSONArray(raw)
                val list = mutableListOf<PrintJobRecord>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(
                        PrintJobRecord(
                            id = obj.optString("id"),
                            timestamp = obj.optLong("timestamp"),
                            title = obj.optString("title"),
                            invoiceNumber = obj.optString("invoiceNumber"),
                            transport = PrinterTransport.fromString(obj.optString("transport")),
                            printerName = obj.optString("printerName"),
                            printerAddress = obj.optString("printerAddress"),
                            paperWidthMm = obj.optInt("paperWidthMm", 58),
                            language = obj.optString("language", "AR"),
                            status = runCatching { PrintJobStatus.valueOf(obj.optString("status", "FAILED")) }
                                .getOrDefault(PrintJobStatus.FAILED),
                            attemptCount = obj.optInt("attemptCount", 1),
                            errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                            byteSize = obj.optInt("byteSize", 0)
                        )
                    )
                }
                _history.value = list.sortedByDescending { it.timestamp }.take(MAX_HISTORY_SIZE)
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading persisted print job history", e)
            }
        }
    }

    internal fun upsertRecord(
        context: Context?,
        record: PrintJobRecord,
        retryPayload: CachedRetryPayload? = null
    ) {
        ensureLoaded(context)
        synchronized(lock) {
            val current = _history.value.toMutableList()
            val existingIdx = current.indexOfFirst { it.id == record.id }
            if (existingIdx >= 0) {
                current[existingIdx] = record
            } else {
                current.add(0, record)
            }
            val trimmed = current.sortedByDescending { it.timestamp }.take(MAX_HISTORY_SIZE)
            _history.value = trimmed
            if (retryPayload != null) {
                retryCache[record.id] = retryPayload.copy(jobRecord = record)
            } else {
                retryCache[record.id]?.let { cached ->
                    retryCache[record.id] = cached.copy(jobRecord = record)
                }
            }
            persistLocked(context, trimmed)
        }
    }

    internal fun getRetryPayload(jobId: String): CachedRetryPayload? = synchronized(lock) {
        retryCache[jobId]
    }

    fun clear(context: Context?) {
        synchronized(lock) {
            _history.value = emptyList()
            retryCache.clear()
            loadedFromDisk = true
            if (context != null) {
                runCatching {
                    context.applicationContext
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .remove(KEY_ENTRIES)
                        .apply()
                }
            }
        }
    }

    private fun persistLocked(context: Context?, entries: List<PrintJobRecord>) {
        if (context == null) return
        try {
            val arr = JSONArray()
            for (r in entries) {
                arr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("timestamp", r.timestamp)
                        put("title", r.title)
                        put("invoiceNumber", r.invoiceNumber)
                        put("transport", r.transport.name)
                        put("printerName", r.printerName)
                        put("printerAddress", r.printerAddress)
                        put("paperWidthMm", r.paperWidthMm)
                        put("language", r.language)
                        put("status", r.status.name)
                        put("attemptCount", r.attemptCount)
                        put("errorMessage", r.errorMessage.orEmpty())
                        put("byteSize", r.byteSize)
                    }
                )
            }
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES, arr.toString())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed persisting print job history", e)
        }
    }
}
