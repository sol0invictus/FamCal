package com.famcal.app.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-device notification switches (Settings → Notifications). Both default to on.
 *
 * Read directly via [remindersEnabled]/[updatesEnabled] from places Hilt doesn't reach
 * (the alarm receiver and FCM service), so keep the storage simple SharedPreferences.
 */
@Singleton
class NotificationPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = prefs(context)

    private val _reminders = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS, true))
    val reminders: StateFlow<Boolean> = _reminders.asStateFlow()

    private val _updates = MutableStateFlow(prefs.getBoolean(KEY_UPDATES, true))
    val updates: StateFlow<Boolean> = _updates.asStateFlow()

    private val _taskReminders = MutableStateFlow(prefs.getBoolean(KEY_TASK_REMINDERS, true))
    /** Morning summary of today's/overdue tasks with Done buttons. */
    val taskReminders: StateFlow<Boolean> = _taskReminders.asStateFlow()

    private val _taskUpdates = MutableStateFlow(prefs.getBoolean(KEY_TASK_UPDATES, true))
    /** "Assigned to you" / "finished a chore" pushes. */
    val taskUpdates: StateFlow<Boolean> = _taskUpdates.asStateFlow()

    private val _digestMinutes = MutableStateFlow(prefs.getInt(KEY_DIGEST_MINUTES, DEFAULT_DIGEST_MINUTES))
    /** Time of day for the task summary, as minutes after midnight. */
    val digestMinutes: StateFlow<Int> = _digestMinutes.asStateFlow()

    fun setTaskReminders(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TASK_REMINDERS, enabled).apply()
        _taskReminders.value = enabled
    }

    fun setTaskUpdates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TASK_UPDATES, enabled).apply()
        _taskUpdates.value = enabled
    }

    fun setDigestMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DIGEST_MINUTES, minutes).apply()
        _digestMinutes.value = minutes
    }

    fun setReminders(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS, enabled).apply()
        _reminders.value = enabled
    }

    fun setUpdates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATES, enabled).apply()
        _updates.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "famcal_notifications"
        private const val KEY_REMINDERS = "reminders_enabled"
        private const val KEY_UPDATES = "updates_enabled"
        private const val KEY_TASK_REMINDERS = "task_reminders_enabled"
        private const val KEY_TASK_UPDATES = "task_updates_enabled"
        private const val KEY_DIGEST_MINUTES = "task_digest_minutes"
        private const val DEFAULT_DIGEST_MINUTES = 8 * 60

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun remindersEnabled(context: Context): Boolean =
            prefs(context).getBoolean(KEY_REMINDERS, true)

        fun updatesEnabled(context: Context): Boolean =
            prefs(context).getBoolean(KEY_UPDATES, true)

        fun taskRemindersEnabled(context: Context): Boolean =
            prefs(context).getBoolean(KEY_TASK_REMINDERS, true)

        fun taskUpdatesEnabled(context: Context): Boolean =
            prefs(context).getBoolean(KEY_TASK_UPDATES, true)

        fun digestMinutes(context: Context): Int =
            prefs(context).getInt(KEY_DIGEST_MINUTES, DEFAULT_DIGEST_MINUTES)
    }
}
