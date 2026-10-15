package com.famcal.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.famcal.app.MainActivity
import com.famcal.app.R
import java.time.LocalDate

/** Shared notification constants, channels, and helpers. */
const val CHANNEL_REMINDERS = "reminders"
const val CHANNEL_UPDATES = "updates"

const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
const val EXTRA_TITLE = "extra_title"
const val EXTRA_TEXT = "extra_text"
const val EXTRA_FAMILY_ID = "extra_family_id"
const val EXTRA_DATE = "extra_date"

/** Opens the app on [date] in [familyId]'s calendar (handled by [MainActivity]). */
fun openCalendarIntent(context: Context, requestCode: Int, familyId: String?, date: LocalDate): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_FAMILY_ID, familyId)
        putExtra(EXTRA_DATE, date.toString())
    }
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

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
fun postNotification(
    context: Context,
    channelId: String,
    id: Int,
    title: String,
    text: String,
    contentIntent: PendingIntent? = null,
) {
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
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(id, notification)
}
