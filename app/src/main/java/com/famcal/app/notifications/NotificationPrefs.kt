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

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun remindersEnabled(context: Context): Boolean =
            prefs(context).getBoolean(KEY_REMINDERS, true)

        fun updatesEnabled(context: Context): Boolean =
            prefs(context).getBoolean(KEY_UPDATES, true)
    }
}
