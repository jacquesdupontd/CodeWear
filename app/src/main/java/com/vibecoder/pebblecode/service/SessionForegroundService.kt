package com.vibecoder.pebblecode.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vibecoder.pebblecode.MainActivity
import com.vibecoder.pebblecode.R
import com.vibecoder.pebblecode.notification.SessionNotification
import kotlinx.coroutines.*

/**
 * Foreground Service that keeps the WebSocket connection alive
 * and posts notifications even when the Activity is destroyed.
 */
class SessionForegroundService : Service() {
    companion object {
        private const val TAG = "FgService"
        private const val NOTIFICATION_ID = 1
    }

    private val bridge get() = BridgeHolder.instance
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var sessionNotification: SessionNotification

    override fun onCreate() {
        super.onCreate()
        sessionNotification = SessionNotification(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Starting foreground service")

        val notification = buildForegroundNotification("Session active")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        bridge.connect()
        observeData()

        return START_STICKY
    }

    private fun buildForegroundNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SessionNotification.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("PebbleCode")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    private fun observeData() {
        var wasWorking = false

        scope.launch {
            bridge.cleanData.collect { data ->
                // Update foreground notification text (silent, no vibration)
                val statusText = data.status.ifEmpty { "Connected" }
                val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                mgr.notify(NOTIFICATION_ID, buildForegroundNotification(statusText))

                // Only post event notifications when app is in BACKGROUND
                if (BridgeHolder.isForeground) {
                    wasWorking = !data.isIdle && !data.isQuestion
                    return@collect
                }

                // QUESTION only
                if (data.isQuestion) {
                    sessionNotification.showQuestion(data.questionText)
                }

                // Truly finished: was working, now ready
                if (wasWorking && data.isReady) {
                    sessionNotification.notifyEvent(
                        title = "Done",
                        text = data.summary.take(80).ifEmpty { "Task complete" }
                    )
                }

                wasWorking = !data.isIdle && !data.isQuestion
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "Foreground service stopping")
        scope.cancel()
        sessionNotification.dismiss()
        super.onDestroy()
    }
}
