package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.NotificationChannels
import com.example.R
import com.example.SafaiApp
import com.example.core.StorageAccess
import com.example.data.scan.ScanProgress
import com.example.data.scan.ScanState
import com.example.data.scan.ScanStateHolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ScanService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val EXTRA_ROOT = "EXTRA_ROOT"

        fun start(ctx: android.content.Context, root: String? = null) {
            val intent = Intent(ctx, ScanService::class.java).apply {
                action = ACTION_START
                root?.let { putExtra(EXTRA_ROOT, it) }
            }
            androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as SafaiApp
        val scanUseCase = app.container.scanUseCase
        val settingsRepository = app.container.settingsRepository

        if (intent?.action == ACTION_CANCEL) {
            scanJob?.cancel()
            ScanStateHolder.state.value = ScanState.Idle
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val rootOverride = intent?.getStringExtra(EXTRA_ROOT)

        startForegroundServiceWithNotification()

        scanJob?.cancel()
        scanJob = serviceScope.launch {
            val settings = settingsRepository.flow.first()
            val mode = StorageAccess.mode(applicationContext)

            try {
                ScanStateHolder.state.value = ScanState.Running(ScanProgress())
                val summary = scanUseCase(settings, mode, rootOverride) { progress ->
                    ScanStateHolder.state.value = ScanState.Running(progress)
                    updateNotification(progress)
                }
                ScanStateHolder.state.value = ScanState.Finished(
                    groups = summary.groupsFound,
                    reclaimableBytes = summary.reclaimableBytes,
                    filesScanned = summary.filesScanned,
                    unreadable = summary.unreadableFiles
                )
            } catch (e: CancellationException) {
                ScanStateHolder.state.value = ScanState.Idle
            } catch (e: Exception) {
                ScanStateHolder.state.value = ScanState.Failed(e.message ?: "Scan failed")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val initialNotif = buildNotification(ScanProgress())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }
    }

    private fun updateNotification(progress: ScanProgress) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification(progress))
    }

    private fun buildNotification(progress: ScanProgress) =
        NotificationCompat.Builder(this, NotificationChannels.SCAN_PROGRESS)
            .setSmallIcon(R.drawable.ic_w_scan)
            .setContentTitle(getString(R.string.shortcut_scan_long))
            .setContentText(
                if (progress.total > 0) {
                    "Scanning… ${progress.processed} / ${progress.total} files"
                } else {
                    "Scanning storage…"
                }
            )
            .setProgress(progress.total, progress.processed, progress.total == 0)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        putExtra("route", "home")
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                0,
                getString(R.string.home_cancel),
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, ScanService::class.java).apply { action = ACTION_CANCEL },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    override fun onDestroy() {
        scanJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
