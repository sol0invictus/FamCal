package com.famcal.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.famcal.app.MainActivity
import com.famcal.app.di.AppEntryPoint
import com.famcal.app.notifications.EXTRA_FAMILY_ID
import com.famcal.app.notifications.EXTRA_OPEN_TASKS
import com.famcal.app.notifications.EXTRA_TASK_ID
import com.famcal.app.ui.tasks.isDone
import com.famcal.app.util.TaskSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class WidgetTask(val id: String, val title: String, val dueText: String, val overdue: Boolean)

private data class WidgetData(
    val signedIn: Boolean,
    val familyId: String? = null,
    val familyName: String = "",
    val tasks: List<WidgetTask> = emptyList(),
)

/**
 * Home-screen widget: the signed-in user's overdue, today's and this week's tasks (theirs or
 * unassigned) in the active family, with a checkbox to mark each done.
 */
class TasksWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(context, data) } }
    }

    private suspend fun load(context: Context): WidgetData {
        val entry = AppEntryPoint.get(context)
        val uid = entry.authRepository().currentUser?.uid ?: return WidgetData(signedIn = false)
        val families = entry.familyRepository().getFamiliesOnce(uid).getOrNull().orEmpty()
        val activeId = entry.activeFamilyStore().activeFamilyId.value
        val family = families.firstOrNull { it.id == activeId } ?: families.firstOrNull()
            ?: return WidgetData(signedIn = true)
        val today = LocalDate.now()
        val tasks = entry.taskRepository().getTasksOnce(family.id).getOrNull().orEmpty()
            .filter { !it.isDone() && (it.assignedTo.isBlank() || it.assignedTo == uid) }
            .mapNotNull { task -> TaskSchedule.parse(task.dueDate)?.let { task to it } }
            .filter { (_, due) -> due.isBefore(today.plusDays(7)) }
            .sortedBy { it.second }
            .take(MAX_ROWS)
            .map { (task, due) ->
                WidgetTask(task.id, task.title, TaskSchedule.dueLabel(due, today), due.isBefore(today))
            }
        return WidgetData(signedIn = true, familyId = family.id, familyName = family.name, tasks = tasks)
    }

    @Composable
    private fun Content(context: Context, data: WidgetData) {
        val openTasks = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_TASKS, true)
            putExtra(EXTRA_FAMILY_ID, data.familyId)
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(openTasks)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tasks",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    data.familyName,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            when {
                !data.signedIn -> Message("Sign in to FamCal to see your tasks.")
                data.tasks.isEmpty() -> Message("All caught up 🎉")
                else -> LazyColumn {
                    items(data.tasks, itemId = { it.id.hashCode().toLong() }) { task ->
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CheckBox(
                                checked = false,
                                onCheckedChange = actionRunCallback<CompleteTaskAction>(
                                    actionParametersOf(
                                        CompleteTaskAction.FAMILY to data.familyId.orEmpty(),
                                        CompleteTaskAction.TASK to task.id,
                                    ),
                                ),
                            )
                            Column(
                                modifier = GlanceModifier.defaultWeight().clickable(
                                    actionStartActivity(
                                        Intent(openTasks).putExtra(EXTRA_TASK_ID, task.id),
                                    ),
                                ),
                            ) {
                                Text(
                                    task.title,
                                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                                    maxLines = 1,
                                )
                                Text(
                                    task.dueText,
                                    style = TextStyle(
                                        color = if (task.overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Message(text: String) {
        Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
    }

    companion object {
        private const val MAX_ROWS = 8

        /** Refreshes every placed widget (fire-and-forget; safe to call often). */
        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                runCatching { TasksWidget().updateAll(appContext) }
            }
        }
    }
}

/** The widget checkbox: marks the task done, then refreshes the widget. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val familyId = parameters[FAMILY] ?: return
        val taskId = parameters[TASK] ?: return
        val repository = AppEntryPoint.get(context).taskRepository()
        repository.getTask(familyId, taskId).onSuccess { repository.complete(familyId, it) }
        TasksWidget().update(context, glanceId)
    }

    companion object {
        val FAMILY = ActionParameters.Key<String>("familyId")
        val TASK = ActionParameters.Key<String>("taskId")
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}
