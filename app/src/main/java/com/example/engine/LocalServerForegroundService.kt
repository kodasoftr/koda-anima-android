package com.example.engine

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
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridge connecting the persistent Local Server notification action ("Encerrar Servidor")
 * with KodanimaViewModel so the user can cleanly terminate the hosted server from the notification shade.
 */
object LocalServerBridge {
    private val _stopRequestedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val stopRequestedEvents = _stopRequestedEvents.asSharedFlow()

    @Volatile
    var onStopCallback: (() -> Unit)? = null

    fun requestStopFromNotification() {
        onStopCallback?.invoke()
        _stopRequestedEvents.tryEmit(Unit)
    }
}

/**
 * Foreground Service that keeps the local Kodanima 2D collaborative server alive
 * even when the user switches apps or turns off the screen briefly, displaying a
 * persistent notification with an "Encerrar Servidor" action button.
 */
class LocalServerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "kodanima_local_server_channel"
        const val NOTIFICATION_ID = 4207
        const val ACTION_START_OR_UPDATE = "com.example.action.START_LOCAL_SERVER"
        const val ACTION_STOP_SERVER = "com.example.action.STOP_LOCAL_SERVER"

        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_PROJECT_NAME = "extra_project_name"
        const val EXTRA_TRANSPORT_LABEL = "extra_transport_label"
        const val EXTRA_PEER_COUNT = "extra_peer_count"

        fun startOrUpdate(
            context: Context,
            roomId: String,
            projectName: String,
            transportLabel: String,
            peerCount: Int
        ) {
            try {
                val intent = Intent(context, LocalServerForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_ROOM_ID, roomId)
                    putExtra(EXTRA_PROJECT_NAME, projectName)
                    putExtra(EXTRA_TRANSPORT_LABEL, transportLabel)
                    putExtra(EXTRA_PEER_COUNT, peerCount)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Ignore background start restriction if app is not foregrounded
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LocalServerForegroundService::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVER) {
            LocalServerBridge.requestStopFromNotification()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        val roomId = intent?.getStringExtra(EXTRA_ROOM_ID) ?: "KD-ROOM"
        val projectName = intent?.getStringExtra(EXTRA_PROJECT_NAME) ?: "Projeto 2D"
        val transportLabel = intent?.getStringExtra(EXTRA_TRANSPORT_LABEL) ?: "Wi-Fi"
        val peerCount = intent?.getIntExtra(EXTRA_PEER_COUNT, 1) ?: 1

        val notification = buildNotification(roomId, projectName, transportLabel, peerCount)
        val fgsType = resolveSupportedFgsType()

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        } catch (_: SecurityException) {
            // Fallback strictly to DATA_SYNC if OEM enforces runtime connectedDevice check unexpectedly
            try {
                val fallbackType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else 0
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fallbackType)
            } catch (_: Exception) {
                stopSelf()
            }
        } catch (_: Exception) {
            stopSelf()
        }

        return START_STICKY
    }

    private fun resolveSupportedFgsType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var mask = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (PermissionHelper.hasConnectedDeviceFgsPrerequisite(this)) {
                mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
        } else {
            mask = mask or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        return mask
    }

    private fun stopForegroundCompat() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Servidor Local Kodanima 2D",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Mantém a sala colaborativa aberta e permite encerrar o servidor pela notificação."
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(
        roomId: String,
        projectName: String,
        transportLabel: String,
        peerCount: Int
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocalServerForegroundService::class.java).apply {
            action = ACTION_STOP_SERVER
        }
        val stopPending = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val peerText = if (peerCount <= 1) "Aguardando amigos" else "$peerCount colaboradores conectados"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Servidor Local Ativo • Sala $roomId")
            .setContentText("$projectName ($transportLabel) • $peerText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Projeto: $projectName\nModo: $transportLabel • $peerText\nToque em 'Encerrar Servidor' para fechar a sala a qualquer momento.")
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Encerrar Servidor",
                stopPending
            )
            .build()
    }
}
