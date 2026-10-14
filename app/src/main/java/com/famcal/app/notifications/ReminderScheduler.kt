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
 * reminder set. Only schedules within a short horizon so the alarm count stays small.
 * It's refreshed while the calendar is open and in the background by
 * [ReminderRescheduleWorker] (periodically, on boot, and when a push says events changed).
 *
 * Scheduled alarms are remembered per family so a refresh also cancels reminders for
 * events that were deleted, moved, or had their reminder removed.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Makes [familyId]'s alarms match [occurrences] exactly. */
    @Synchronized
    fun sync(familyId: String, occurrences: List<EventOccurrence>) {
        val manager = alarmManager ?: return
        val now = System.currentTimeMillis()
        val horizon = now + HORIZON_MILLIS

        val scheduled = mutableSetOf<Int>()
        for (occ in occurrences) {
            val minutes = occ.event.reminderMinutes
            if (minutes == Reminders.NONE) continue

            val startMillis = occ.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val triggerAt = startMillis - minutes * 60_000L
            if (triggerAt < now || triggerAt > horizon) continue

            scheduled += schedule(manager, occ, triggerAt)
        }

        (storedCodes(familyId) - scheduled).forEach { cancel(manager, it) }
        storeCodes(familyId, scheduled)
    }

    /** Cancels alarms for any family not in [familyIds] (left the family, signed out). */
    @Synchronized
    fun retainFamilies(familyIds: Set<String>) {
        val manager = alarmManager ?: return
        for (key in prefs.all.keys) {
            if (key !in familyIds) {
                storedCodes(key).forEach { cancel(manager, it) }
                prefs.edit().remove(key).apply()
            }
        }
    }

    private fun schedule(manager: AlarmManager, occ: EventOccurrence, triggerAt: Long): Int {
        val requestCode = (occ.event.id + "@" + occ.start.toString()).hashCode()
        val intent = alarmIntent().apply {
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
        return requestCode
    }

    private fun cancel(manager: AlarmManager, requestCode: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        manager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun alarmIntent() = Intent(context, AlarmReceiver::class.java)

    private fun storedCodes(familyId: String): Set<Int> =
        prefs.getStringSet(familyId, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    private fun storeCodes(familyId: String, codes: Set<Int>) {
        prefs.edit().putStringSet(familyId, codes.map { it.toString() }.toSet()).apply()
    }

    companion object {
        private const val PREFS_NAME = "reminder_alarms"
        private const val HORIZON_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
