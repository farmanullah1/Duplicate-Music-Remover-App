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
import com.example.core.StorageAccess
import kotlinx.coroutines.flow.first

class AutoScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as SafaiApp
            val settings = app.container.settingsRepository.flow.first()
            if (!settings.autoScanWeekly) return Result.success()

            val mode = StorageAccess.mode(applicationContext)
            val summary = app.container.scanUseCase(settings, mode) { _ -> }

            if (summary.groupsFound > 0) {
                val nm = applicationContext.getSystemService(NotificationManager::class.java)
                val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.CLEANUP)
                    .setSmallIcon(R.drawable.ic_w_clean)
                    .setContentTitle("Safai Scan Results")
                    .setContentText("Found ${summary.groupsFound} duplicate groups · ${Formatters.formatBytes(summary.reclaimableBytes)} can be freed")
                    .setAutoCancel(true)
                    .build()
                nm?.notify(2001, notif)
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
