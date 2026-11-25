package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.DatabaseInitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StockMonitorWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = executeLowStockCheck(applicationContext)

    companion object {
        internal suspend fun executeLowStockCheck(appContext: Context): Result = withContext(Dispatchers.IO) {
            try {
                val db = when (val initResult = AppDatabase.initializeDatabase(appContext)) {
                    is DatabaseInitResult.Ready -> initResult.database
                    is DatabaseInitResult.Failed -> {
                        Log.w(
                            "StockMonitorWorker",
                            "Skipping stock check because database is unavailable (${initResult.reasonCode}): ${initResult.technicalDetails}"
                        )
                        return@withContext Result.retry()
                    }
                }
                val lowStockProducts = db.appDao().getLowStockProductsWorker()

                if (lowStockProducts.isNotEmpty()) {
                    val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val channelId = "stock_alerts"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(channelId, "Stock Alerts", NotificationManager.IMPORTANCE_DEFAULT)
                        notificationManager.createNotificationChannel(channel)
                    }

                    val productNames = lowStockProducts.take(3).joinToString { it.name } + if (lowStockProducts.size > 3) " and more..." else ""

                    val notification = NotificationCompat.Builder(appContext, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Low Stock Alert")
                        .setContentText("${lowStockProducts.size} items are low on stock: $productNames")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()

                    notificationManager.notify(1001, notification)
                    Log.d("StockMonitorWorker", "Triggered low stock notification for ${lowStockProducts.size} items.")
                }
                Result.success()
            } catch (e: Exception) {
                Log.e("StockMonitorWorker", "Error checking stock", e)
                Result.retry()
            }
        }
    }
}
