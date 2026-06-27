package com.famcal.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.famcal.app.data.model.Reminders
import com.famcal.app.util.EventOccurrence
import com.famcal.app.util.formatTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules local-notification alarms for upcoming event occurrences that have a
 * reminder set. Only schedules within a short horizon so the alarm count stays small;
 * it's refreshed whenever the calendar is observed. (Reminders further out are picked
 * up next time the app is opened — a deliberate MVP simplification.)
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun sync(occurrences: List<EventOccurrence>) {
        val manager = alarmManager ?: return
        val now = System.currentTimeMillis()
        val horizon = now + HORIZON_MILLIS

        for (occ in occurrences) {
            val minutes = occ.event.reminderMinutes
            if (minutes == Reminders.NONE) continue

            val startMillis = occ.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val triggerAt = startMillis - minutes * 60_000L
            if (triggerAt < now || triggerAt > horizon) continue

            schedule(manager, occ, triggerAt)
        }
    }

    private fun schedule(manager: AlarmManager, occ: EventOccurrence, triggerAt: Long) {
        val requestCode = (occ.event.id + "@" + occ.start.toString()).hashCode()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, requestCode)
            putExtra(EXTRA_TITLE, occ.event.title.ifBlank { "Event reminder" })
            putExtra(EXTRA_TEXT, "Starts at ${occ.start.formatTime()}")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            // Inexact-but-doze-friendly; avoids needing the exact-alarm permission.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (_: SecurityException) {
            // Ignore: scheduling can fail in restricted states; reminder simply won't fire.
        }
    }

    companion object {
        private const val HORIZON_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
