package com.famcal.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Shared notification constants and channel setup. */
const val CHANNEL_REMINDERS = "reminders"

const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
const val EXTRA_TITLE = "extra_title"
const val EXTRA_TEXT = "extra_text"

fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        CHANNEL_REMINDERS,
        "Event reminders",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Reminders for upcoming family calendar events"
    }
    manager.createNotificationChannel(channel)
}
