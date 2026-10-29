package com.example.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.NotificationChannels
import com.example.R
import com.example.SafaiApp
import com.example.core.Formatters
import kotlinx.coroutines.flow.first

class CleanupReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as SafaiApp
            val settingsRepo = app.container.settingsRepository
            val statsRepo = app.container.statsRepository

            val settings = settingsRepo.flow.first()
            if (!settings.cleanupReminder) return Result.success()

            val stats = statsRepo.snapshot()
            val now = System.currentTimeMillis()
            val lastScan = stats.lastScanAt ?: 0L
            val lastReminder = settings.lastReminderMs

            val thirtyDaysMs = 30L * 86_400_000L
            if ((now - lastScan > thirtyDaysMs) && (now - lastReminder > thirtyDaysMs)) {
                val nm = applicationContext.getSystemService(NotificationManager::class.java)
                val text = if (stats.reclaimableBytes > 0) {
                    "It's been a while — Safai can free about ${Formatters.formatBytes(stats.reclaimableBytes)}"
                } else {
                    "Run a scan to see what you can free"
                }

                val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.CLEANUP)
                    .setSmallIcon(R.drawable.ic_w_clean)
                    .setContentTitle("Safai Monthly Reminder")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build()

                nm?.notify(3001, notif)
                settingsRepo.update { it.copy(lastReminderMs = now) }
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
