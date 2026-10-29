package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import com.example.service.CleanupReminderWorker
import com.example.service.TrashPurgeWorker
import com.example.widget.WidgetRefreshWorker
import java.util.concurrent.TimeUnit

class SafaiApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                java.io.File(filesDir, "crash.log").apply {
                    if (length() > 200_000) writeText("")
                    appendText("--- ${java.util.Date()} thread=${t.name}\n${android.util.Log.getStackTraceString(e)}\n")
                }
            }
            prev?.uncaughtException(t, e)
        }

        container = DefaultAppContainer(this)
        NotificationChannels.create(this)

        val workManager = WorkManager.getInstance(this)

        // Trash purge daily
        val trashRequest = PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork("trash_purge", ExistingPeriodicWorkPolicy.KEEP, trashRequest)

        // Widget refresh every 30 minutes
        val widgetRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork("widget_refresh", ExistingPeriodicWorkPolicy.KEEP, widgetRequest)

        // Monthly cleanup reminder
        val reminderRequest = PeriodicWorkRequestBuilder<CleanupReminderWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork("cleanup_reminder", ExistingPeriodicWorkPolicy.KEEP, reminderRequest)
    }
}

object NotificationChannels {
    const val SCAN_PROGRESS = "scan_progress"
    const val CLEANUP = "cleanup"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val progressChannel = NotificationChannel(
                SCAN_PROGRESS,
                context.getString(R.string.channel_scan_progress_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_scan_progress_desc)
            }
            val cleanupChannel = NotificationChannel(
                CLEANUP,
                context.getString(R.string.channel_cleanup_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_cleanup_desc)
            }
            nm.createNotificationChannel(progressChannel)
            nm.createNotificationChannel(cleanupChannel)
        }
    }
}
