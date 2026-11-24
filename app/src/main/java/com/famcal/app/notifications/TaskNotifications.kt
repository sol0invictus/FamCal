package com.famcal.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.famcal.app.R
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.HouseholdTask
import com.famcal.app.data.model.TaskArea
import com.famcal.app.data.task.TaskRepository
import com.famcal.app.di.AppEntryPoint
import com.famcal.app.ui.tasks.isDone
import com.famcal.app.util.TaskSchedule
import com.famcal.app.widget.TasksWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The daily task summary: an alarm at the user's chosen time (Settings → Notifications) runs
 * [TaskDigestWorker], which posts one grouped notification per family listing the user's
 * overdue and due-today tasks (theirs or unassigned), each with Done / Tomorrow buttons.
 */
object TaskDigest {
    private const val REQUEST_CODE = 0x7a5c

    /** Arms (or, when switched off, cancels) the next summary alarm. Safe to call repeatedly. */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TaskDigestReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!NotificationPrefs.taskRemindersEnabled(context)) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val minutes = NotificationPrefs.digestMinutes(context)
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(minutes / 60, minutes % 60)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (_: SecurityException) {
            // Restricted state; the periodic reminder worker re-arms it later.
        }
    }
}

/** Fired by the [TaskDigest] alarm: posts today's summary and arms tomorrow's. */
class TaskDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskDigestWorker.runNow(context)
        TaskDigest.schedule(context)
    }
}

@HiltWorker
class TaskDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val taskRepository: TaskRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        TasksWidget.requestUpdate(applicationContext)
        if (!NotificationPrefs.taskRemindersEnabled(applicationContext)) return Result.success()
        val uid = authRepository.currentUser?.uid ?: return Result.success()
        val families = familyRepository.getFamiliesOnce(uid).getOrElse { return Result.retry() }
        val today = LocalDate.now()
        for (family in families) {
            val tasks = taskRepository.getTasksOnce(family.id).getOrNull() ?: continue
            val due = tasks
                .filter { !it.isDone() && (it.assignedTo.isBlank() || it.assignedTo == uid) }
                .mapNotNull { task -> TaskSchedule.parse(task.dueDate)?.takeIf { !it.isAfter(today) }?.let { task to it } }
                .sortedBy { it.second }
            if (due.isNotEmpty()) {
                postDigest(applicationContext, family.id, family.name.takeIf { families.size > 1 }, due, today)
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "task-digest"

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TaskDigestWorker>().build(),
            )
        }
    }
}

private const val MAX_TASK_NOTIFICATIONS = 6

fun taskNotificationId(familyId: String, taskId: String): Int = "task:$familyId:$taskId".hashCode()

private fun canPost(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun postDigest(
    context: Context,
    familyId: String,
    familyName: String?,
    due: List<Pair<HouseholdTask, LocalDate>>,
    today: LocalDate,
) {
    if (!canPost(context)) return
    val manager = NotificationManagerCompat.from(context)
    val group = "tasks:$familyId"
    val overdue = due.count { it.second.isBefore(today) }

    due.take(MAX_TASK_NOTIFICATIONS).forEach { (task, date) ->
        val id = taskNotificationId(familyId, task.id)
        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(task.title.ifBlank { "Task" })
            .setContentText("${TaskArea.emoji(task.area)} ${TaskSchedule.dueLabel(date, today)}")
            .setGroup(group)
            .setContentIntent(openTasksIntent(context, id, familyId, task.id))
            .addAction(0, "Done", taskActionIntent(context, TaskActionReceiver.ACTION_DONE, familyId, task.id, id))
            .addAction(0, "Tomorrow", taskActionIntent(context, TaskActionReceiver.ACTION_SNOOZE, familyId, task.id, id))
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    val title = buildString {
        append(if (due.size == 1) "1 task to do today" else "${due.size} tasks to do today")
        if (overdue > 0) append(" ($overdue overdue)")
    }
    val style = NotificationCompat.InboxStyle().setSummaryText(familyName)
    due.take(MAX_TASK_NOTIFICATIONS).forEach { (task, _) -> style.addLine(task.title) }
    if (due.size > MAX_TASK_NOTIFICATIONS) style.addLine("+${due.size - MAX_TASK_NOTIFICATIONS} more")
    val summaryId = "taskdigest:$familyId".hashCode()
    val summary = NotificationCompat.Builder(context, CHANNEL_TASKS)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(due.joinToString(", ") { it.first.title })
        .setStyle(style)
        .setGroup(group)
        .setGroupSummary(true)
        .setContentIntent(openTasksIntent(context, summaryId, familyId))
        .setAutoCancel(true)
        .build()
    manager.notify(summaryId, summary)
}

private fun taskActionIntent(
    context: Context,
    action: String,
    familyId: String,
    taskId: String,
    notificationId: Int,
): PendingIntent {
    val intent = Intent(context, TaskActionReceiver::class.java).apply {
        this.action = action
        putExtra(EXTRA_FAMILY_ID, familyId)
        putExtra(EXTRA_TASK_ID, taskId)
        putExtra(EXTRA_NOTIFICATION_ID, notificationId)
    }
    return PendingIntent.getBroadcast(
        context,
        (action + notificationId).hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Handles the Done / Tomorrow buttons on task notifications without opening the app. */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val familyId = intent.getStringExtra(EXTRA_FAMILY_ID) ?: return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val repository = AppEntryPoint.get(context).taskRepository()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Firestore queues the write locally even if the server ack takes longer.
                withTimeoutOrNull(ACTION_TIMEOUT_MS) {
                    when (intent.action) {
                        ACTION_DONE -> repository.getTask(familyId, taskId).onSuccess { repository.complete(familyId, it) }
                        ACTION_SNOOZE -> repository.snooze(familyId, taskId, 1)
                    }
                }
                NotificationManagerCompat.from(context).cancel(notificationId)
                TasksWidget.requestUpdate(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.famcal.app.action.TASK_DONE"
        const val ACTION_SNOOZE = "com.famcal.app.action.TASK_SNOOZE"
        private const val ACTION_TIMEOUT_MS = 8_000L
    }
}

/** Posts a pushed task update ("assigned to you", "finished a chore"). */
fun postTaskUpdate(context: Context, familyId: String, taskId: String?, title: String, body: String) {
    if (!canPost(context) || !NotificationPrefs.taskUpdatesEnabled(context)) return
    val id = System.currentTimeMillis().toInt()
    val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(body)
        .setContentIntent(openTasksIntent(context, id, familyId, taskId))
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(id, notification)
}
