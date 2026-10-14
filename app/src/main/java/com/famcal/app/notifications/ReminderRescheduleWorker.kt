package com.famcal.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.event.EventRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.util.RecurrenceExpander
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Re-arms near-term reminder alarms for all of the user's families without the app being
 * open, so events added by other members still get reminders. Runs periodically, after a
 * reboot (alarms don't survive restarts), and when a push says a family's events changed.
 */
@HiltWorker
class ReminderRescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val eventRepository: EventRepository,
    private val reminderScheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = authRepository.currentUser?.uid
        if (uid == null) {
            reminderScheduler.retainFamilies(emptySet())
            return Result.success()
        }
        val families = familyRepository.getFamiliesOnce(uid).getOrElse { return Result.retry() }

        val today = LocalDate.now()
        for (family in families) {
            // On failure keep that family's existing alarms rather than clearing them.
            val events = eventRepository.getEventsOnce(family.id).getOrNull() ?: continue
            reminderScheduler.sync(family.id, RecurrenceExpander.expand(events, today, today.plusDays(8)))
        }
        reminderScheduler.retainFamilies(families.map { it.id }.toSet())
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK = "reminder-refresh-periodic"
        private const val ONE_TIME_WORK = "reminder-refresh"

        /** Keeps reminders fresh even if the app isn't opened (safety net for missed pushes). */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderRescheduleWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Refreshes reminders now (after a reboot or an event-change push). */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderRescheduleWorker>().build(),
            )
        }
    }
}
