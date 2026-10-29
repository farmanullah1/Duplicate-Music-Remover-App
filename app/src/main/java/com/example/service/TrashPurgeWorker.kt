package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.SafaiApp
import kotlinx.coroutines.flow.first

class TrashPurgeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as SafaiApp
            val settings = app.container.settingsRepository.flow.first()
            app.container.recycleBinManager.autoPurge(settings.trashRetentionDays)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
