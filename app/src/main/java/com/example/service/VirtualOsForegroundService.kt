package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.InstanceState
import com.example.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VirtualOsForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) {
            serviceScope.launch {
                AppContainer.get(applicationContext).runtimeEngine.stopAllInstances()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val notification = buildNotification(
            runningNames = listOf("Menyiapkan instance Android 10..."),
            totalAllocatedMb = 0
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startStatusUpdates()
        return START_STICKY
    }

    private fun startStatusUpdates() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val container = AppContainer.get(applicationContext)
            val nm = getSystemService(NotificationManager::class.java)
            while (isActive) {
                val all = container.repository.getInstancesOnce()
                val active = all.filter {
                    it.state == InstanceState.RUNNING || it.state == InstanceState.BOOTING
                }
                if (active.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                val names = active.map { it.name }
                val totalMb = active.sumOf { it.ramLimitMb }
                nm.notify(NOTIFICATION_ID, buildNotification(names, totalMb))
                delay(3000)
            }
        }
    }

    private fun buildNotification(runningNames: List<String>, totalAllocatedMb: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAllIntent = Intent(this, VirtualOsForegroundService::class.java).apply {
            action = ACTION_STOP_ALL
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = if (runningNames.size == 1) {
            "${runningNames.first()} berjalan • RAM alokasi ${totalAllocatedMb} MB"
        } else {
            "${runningNames.size} instance Android 10 aktif • RAM ${totalAllocatedMb} MB"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LitedirtVM — Android 10 (API 29)")
            .setContentText(summary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$summary\nAktif: ${runningNames.joinToString(", ")}")
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Hentikan Semua",
                stopPending
            )
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_vm_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_vm_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "virtualos_vm_service"
        const val NOTIFICATION_ID = 1029
        const val ACTION_STOP_ALL = "com.example.action.STOP_ALL_VMS"

        fun start(context: Context) {
            val intent = Intent(context, VirtualOsForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VirtualOsForegroundService::class.java))
        }
    }
}
