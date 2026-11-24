package com.famcal.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.famcal.app.data.model.HouseholdTask
import com.famcal.app.data.model.Member
import com.famcal.app.data.model.TaskArea
import com.famcal.app.data.model.TaskRepeat
import com.famcal.app.ui.theme.MemberColors
import com.famcal.app.util.TaskSchedule
import com.famcal.app.util.TaskStatus
import com.famcal.app.util.toLocalDate
import java.time.LocalDate

/** A member's palette color (gray for unassigned / unknown). */
fun memberColor(member: Member?): Color =
    member?.let { MemberColors[it.colorIndex.mod(MemberColors.size)] } ?: Color(0xFF8A8A8A)

fun HouseholdTask.isDone(): Boolean = repeatMode == TaskRepeat.NONE && completedAt != null

fun HouseholdTask.status(today: LocalDate): TaskStatus =
    TaskSchedule.status(TaskSchedule.parse(dueDate), isDone(), today)

/** "You", "Alex", "Anyone", or "Alex's turn" for a rotating chore. */
fun assigneeLabel(task: HouseholdTask, membersByUid: Map<String, Member>, currentUid: String): String {
    if (task.assignedTo.isBlank()) return "Anyone"
    val name = if (task.assignedTo == currentUid) "You" else
        membersByUid[task.assignedTo]?.displayName?.ifBlank { null } ?: "Someone"
    return if (task.rotation.size > 1) {
        if (name == "You") "Your turn" else "$name's turn"
    } else {
        name
    }
}

/** "Every 3 months · last done 47 days ago by Alex" (or null when there's nothing to say). */
fun historyLine(task: HouseholdTask, membersByUid: Map<String, Member>, currentUid: String, today: LocalDate): String? {
    val parts = mutableListOf<String>()
    if (task.repeatMode != TaskRepeat.NONE) {
        parts += TaskSchedule.repeatLabel(task.repeatMode, task.repeatUnit, task.repeatInterval)
    }
    task.lastDoneAt?.let { at ->
        val who = when (task.lastDoneBy) {
            currentUid -> " by you"
            "" -> ""
            else -> membersByUid[task.lastDoneBy]?.displayName?.let { " by $it" }.orEmpty()
        }
        parts += "last done ${TaskSchedule.sinceLabel(at.toLocalDate(), today)}$who"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.replaceFirstChar { it.uppercase() }
}

/** Snackbar text after completing: "Done! Next: in 3 months" or "Done: Fix the fence". */
fun completionMessage(task: HouseholdTask, today: LocalDate = LocalDate.now()): String {
    val next = TaskSchedule.nextDue(
        TaskSchedule.parse(task.dueDate), task.repeatMode, task.repeatUnit, task.repeatInterval, today,
    ) ?: return "Done: ${task.title}"
    return "Done! Next: ${TaskSchedule.dueLabel(next, today).removePrefix("Due ")}"
}

@Composable
fun CompleteButton(done: Boolean, color: Color, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (done) "Done" else "Mark done",
            tint = color,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** One task in a list: check circle, title, due/assignee line, repeat/history line, Claim. */
@Composable
fun TaskRow(
    task: HouseholdTask,
    today: LocalDate,
    membersByUid: Map<String, Member>,
    currentUid: String,
    onCheck: () -> Unit,
    onClick: () -> Unit,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = task.isDone()
    val status = task.status(today)
    val color = memberColor(membersByUid[task.assignedTo])
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompleteButton(done = done, color = color, onClick = onCheck, enabled = !done)
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = task.title.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val dueText = if (done) "Done" else TaskSchedule.dueLabel(TaskSchedule.parse(task.dueDate), today)
            val points = if (task.points > 1) " · ⭐ ${task.points}" else ""
            Text(
                text = "${TaskArea.emoji(task.area)} $dueText · ${assigneeLabel(task, membersByUid, currentUid)}$points",
                style = MaterialTheme.typography.bodySmall,
                color = if (status == TaskStatus.OVERDUE) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            historyLine(task, membersByUid, currentUid, today)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (task.assignedTo.isBlank() && !done) {
            TextButton(onClick = onClaim) { Text("Claim") }
        }
    }
}
