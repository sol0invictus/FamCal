package com.famcal.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.famcal.app.R

/** Shared notification constants, channels, and helpers. */
const val CHANNEL_REMINDERS = "reminders"
const val CHANNEL_UPDATES = "updates"

const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
const val EXTRA_TITLE = "extra_title"
const val EXTRA_TEXT = "extra_text"

fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_REMINDERS,
            "Event reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Reminders for upcoming family calendar events" },
    )
    manager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_UPDATES,
            "Family updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "When a family member adds or changes an event" },
    )
}

/** Posts a notification, respecting the Android 13+ runtime permission. */
fun postNotification(context: Context, channelId: String, id: Int, title: String, text: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(id, notification)
}
