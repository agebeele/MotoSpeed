package com.example.location

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
import com.example.MainActivity

class MotoTrackingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_TRACKING) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val speedText = intent?.getStringExtra(EXTRA_SPEED) ?: "0 km/h"
        val distText = intent?.getStringExtra(EXTRA_DISTANCE) ?: "0.0 km"

        val notification = buildNotification(speedText, distText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Grabación de Ruta en Moto",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene el GPS activo grabando la ruta aunque bloquees la pantalla"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(speed: String, distance: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🏍️ MotoSpeed: Grabando Ruta GPS")
            .setContentText("Velocidad: $speed  •  Distancia: $distance")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "moto_speed_tracking_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START_TRACKING = "com.example.motospeed.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.motospeed.STOP_TRACKING"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_DISTANCE = "extra_distance"

        fun startService(context: Context, speed: String = "0 km/h", distance: String = "0.0 km") {
            try {
                val intent = Intent(context, MotoTrackingService::class.java).apply {
                    action = ACTION_START_TRACKING
                    putExtra(EXTRA_SPEED, speed)
                    putExtra(EXTRA_DISTANCE, distance)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, MotoTrackingService::class.java).apply {
                    action = ACTION_STOP_TRACKING
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}
