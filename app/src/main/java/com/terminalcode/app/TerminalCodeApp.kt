package com.terminalcode.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log

/**
 * TerminalCode Application class.
 *
 * Handles app-wide initialization including:
 * - Notification channels for the terminal foreground service
 * - Global configuration
 * - Logging setup
 */
class TerminalCodeApp : Application() {

    companion object {
        const val TAG = "TerminalCode"
        const val TERMINAL_SERVICE_CHANNEL_ID = "terminal_service"
        const val TERMINAL_SERVICE_CHANNEL_NAME = "Terminal Sessions"
        const val TERMINAL_SERVICE_NOTIFICATION_ID = 1001

        lateinit var instance: TerminalCodeApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        Log.d(TAG, "TerminalCode initialized")
    }

    /**
     * Creates notification channels required for foreground service.
     * This is necessary for Android 13+ (API 33+) to show notifications
     * for the terminal service running in the background.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TERMINAL_SERVICE_CHANNEL_ID,
                TERMINAL_SERVICE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for terminal sessions running in the background"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "TerminalCode terminating")
    }

}
