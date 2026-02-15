package com.vibecoder.pebblecode.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vibecoder.pebblecode.MainActivity
import com.vibecoder.pebblecode.R

class SessionNotification(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "pebblecode_session"
        const val CHANNEL_QUESTION = "pebblecode_question"
        const val CHANNEL_EVENT = "pebblecode_event"
        const val NOTIFICATION_ID = 1
        const val QUESTION_NOTIFICATION_ID = 2
        const val EVENT_NOTIFICATION_ID_BASE = 10
        const val MAX_STACKED_EVENTS = 5
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private var eventCounter = 0

    init {
        createChannels()
    }

    private fun createChannels() {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Active Session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when connected to Claude Code session"
            }
        )

        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_QUESTION, "Questions", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Claude is asking a question"
                enableVibration(true)
            }
        )

        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENT, "Events", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Claude Code session events"
                enableVibration(false)
            }
        )
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun pendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showSessionActive(sessionName: String, status: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("PebbleCode")
            .setContentText(status)
            .setOngoing(true)
            .setContentIntent(pendingIntent())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showQuestion(questionText: String) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_QUESTION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Claude asks:")
            .setContentText(questionText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(questionText))
            .setContentIntent(pendingIntent())
            .setFullScreenIntent(pendingIntent(), true) // Wake screen
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        notificationManager.notify(QUESTION_NOTIFICATION_ID, notification)
    }

    /** Rich event notification with prompt, task & status */
    fun notifyEvent(title: String, text: String, prompt: String = "", task: String = "", status: String = "") {
        if (!hasPermission()) return
        // Build detailed body
        val body = buildString {
            append(text)
            if (task.isNotEmpty()) append("\nTask: $task")
            if (prompt.isNotEmpty()) append("\nPrompt: $prompt")
            if (status.isNotEmpty()) append("\nStatus: $status")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(true)
            .build()
        val id = EVENT_NOTIFICATION_ID_BASE + (eventCounter++ % MAX_STACKED_EVENTS)
        notificationManager.notify(id, notification)
    }

    fun dismiss() {
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.cancel(QUESTION_NOTIFICATION_ID)
        for (i in 0 until MAX_STACKED_EVENTS) {
            notificationManager.cancel(EVENT_NOTIFICATION_ID_BASE + i)
        }
        eventCounter = 0
    }
}
