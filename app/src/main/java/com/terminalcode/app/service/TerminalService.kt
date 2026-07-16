package com.terminalcode.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.terminalcode.app.MainActivity
import com.terminalcode.app.R
import com.terminalcode.app.TerminalCodeApp

/**
 * Foreground service for keeping terminal sessions alive
 * even when the app is in the background.
 *
 * Android can kill background processes, but foreground services
 * with visible notifications are much less likely to be killed.
 * This service ensures terminal commands continue running.
 */
class TerminalService : Service() {

    companion object {
        const val TAG = "TerminalService"
        const val ACTION_START = "com.terminalcode.app.action.START"
        const val ACTION_STOP = "com.terminalcode.app.action.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Terminal service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(
                    TerminalCodeApp.TERMINAL_SERVICE_NOTIFICATION_ID,
                    createNotification()
                )
                Log.d(TAG, "Terminal service started as foreground")
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "Terminal service stopped")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Terminal service destroyed")
    }

    /**
     * Creates the persistent notification shown while the service is running.
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, TerminalCodeApp.TERMINAL_SERVICE_CHANNEL_ID)
            .setContentTitle("TerminalCode")
            .setContentText("Terminal session is running")
            .setSmallIcon(android.R.drawable.ic_menu_console)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }
}
