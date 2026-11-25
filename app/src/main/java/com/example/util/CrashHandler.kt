package com.example.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global Uncaught Exception Handler and persistent local error logger.
 * Catches application-level unexpected crashes, appends diagnostic logs
 * to a local file, and provides query interfaces for debugging and support.
 */
class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            logCrash(thread, throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log: ${e.message}", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun logCrash(thread: Thread, throwable: Throwable) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val timestamp = dateFormat.format(Date())

        val logFile = File(context.filesDir, CRASH_LOG_FILE_NAME)
        val stackTraceString = Log.getStackTraceString(throwable)

        val logEntry = buildString {
            append("====================================================\n")
            append("CRASH REPORT - $timestamp\n")
            append("Thread: ${thread.name} (ID: ${thread.id})\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
            append("Exception: ${throwable.javaClass.name}\n")
            append("Message: ${throwable.message ?: "No message"}\n")
            append("StackTrace:\n$stackTraceString\n")
            append("====================================================\n\n")
        }

        logFile.appendText(logEntry)
        Log.e(TAG, "Uncaught exception logged locally to ${logFile.absolutePath}")
    }

    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_LOG_FILE_NAME = "crash_logs.txt"

        fun initialize(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Log.i(TAG, "CrashHandler successfully initialized.")
        }

        fun logManualError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                val timestamp = dateFormat.format(Date())
                val logFile = File(context.filesDir, CRASH_LOG_FILE_NAME)
                val errorEntry = buildString {
                    append("[$timestamp] [ERROR] [$tag] $message\n")
                    if (throwable != null) {
                        append(Log.getStackTraceString(throwable))
                        append("\n")
                    }
                }
                logFile.appendText(errorEntry)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write manual error: ${e.message}")
            }
        }

        fun getCrashLogs(context: Context): String {
            val logFile = File(context.filesDir, CRASH_LOG_FILE_NAME)
            return if (logFile.exists()) logFile.readText() else "لا توجد سجلات أخطاء مسجلة."
        }

        fun clearCrashLogs(context: Context): Boolean {
            val logFile = File(context.filesDir, CRASH_LOG_FILE_NAME)
            return if (logFile.exists()) logFile.delete() else true
        }
    }
}
