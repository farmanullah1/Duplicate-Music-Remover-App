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
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

class SafaiApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Crash Detection & Self-Debugging Local Logger
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val crashFile = File(filesDir, "crash.log")
                if (crashFile.length() > 200_000) crashFile.writeText("")
                crashFile.appendText("--- ${Date()} thread=${t.name}\n${android.util.Log.getStackTraceString(e)}\n")

                val prefs = getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean(KEY_LAST_RUN_CRASHED, true)
                    .putString(KEY_LAST_CRASH_MSG, e.localizedMessage ?: e.javaClass.simpleName)
                    .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())
                    .apply()
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

    companion object {
        const val PREFS_CRASH = "safai_crash_prefs"
        const val KEY_LAST_RUN_CRASHED = "last_run_crashed"
        const val KEY_LAST_CRASH_MSG = "last_crash_msg"
        const val KEY_LAST_CRASH_TIME = "last_crash_time"

        fun checkAndClearCrashMarker(context: Context): Pair<Boolean, String?> {
            val prefs = context.getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
            val crashed = prefs.getBoolean(KEY_LAST_RUN_CRASHED, false)
            val msg = prefs.getString(KEY_LAST_CRASH_MSG, null)
            if (crashed) {
                prefs.edit().putBoolean(KEY_LAST_RUN_CRASHED, false).apply()
            }
            return (crashed to msg)
        }
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
