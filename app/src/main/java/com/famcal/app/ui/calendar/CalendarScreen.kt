package com.famcal.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famcal.app.data.model.Recurrence
import com.famcal.app.data.model.Reminders
import com.famcal.app.ui.theme.MemberColors
import com.famcal.app.util.EventOccurrence
import com.famcal.app.util.formatDate
import com.famcal.app.util.formatTime
import com.famcal.app.util.formatTitle
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onAddEvent: (LocalDate) -> Unit,
    onEventClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<EventOccurrence?>(null) }

    val currentMonth = remember { YearMonth.now() }
    val calendarState = rememberCalendarState(
        startMonth = remember { currentMonth.minusMonths(24) },
        endMonth = remember { currentMonth.plusMonths(24) },
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = remember { daysOfWeek().first() },
    )
    val scope = rememberCoroutineScope()
    val visibleMonth = calendarState.firstVisibleMonth.yearMonth

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.familyName.ifBlank { "FamCal" }) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddEvent(state.selectedDate) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add event")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            MonthHeader(
                month = visibleMonth,
                onPrevious = { scope.launch { calendarState.animateScrollToMonth(visibleMonth.minusMonths(1)) } },
                onNext = { scope.launch { calendarState.animateScrollToMonth(visibleMonth.plusMonths(1)) } },
                onToday = {
                    scope.launch { calendarState.animateScrollToMonth(YearMonth.now()) }
                    viewModel.selectDate(LocalDate.now())
                },
            )
            MemberLegend(state)
            WeekdayHeader()
            HorizontalCalendar(
                state = calendarState,
                dayContent = { day ->
                    DayCell(
                        day = day,
                        isSelected = day.date == state.selectedDate,
                        eventColors = eventColorsFor(state, day.date),
                        onClick = { viewModel.selectDate(day.date) },
                    )
                },
            )
            HorizontalDivider()
            AgendaSection(
                date = state.selectedDate,
                occurrences = state.selectedDayEvents,
                colorFor = { occ -> memberColor(state, occ.event.colorUid) },
                onOccurrenceClick = { selected = it },
            )
        }
    }

    selected?.let { occurrence ->
        EventDetailSheet(
            occurrence = occurrence,
            assigneeName = occurrence.event.assignedTo
                .takeIf { it.isNotBlank() }
                ?.let { state.membersByUid[it]?.displayName },
            onEdit = {
                onEventClick(occurrence.event.id)
                selected = null
            },
            onDelete = {
                viewModel.deleteEvent(occurrence.event.id)
                selected = null
            },
            onDismiss = { selected = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(
    occurrence: EventOccurrence,
    assigneeName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val event = occurrence.event
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(event.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            DetailLine(occurrence.date.formatDate() + "  ·  " + timeLabel(occurrence))
            if (event.location.isNotBlank()) DetailLine("📍  ${event.location}")
            if (assigneeName != null) DetailLine("👤  For $assigneeName")
            if (event.reminderMinutes != Reminders.NONE) DetailLine("🔔  ${Reminders.label(event.reminderMinutes)}")
            if (event.recurrence != Recurrence.NONE) DetailLine("🔁  ${Recurrence.label(event.recurrence)}")
            if (event.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(event.notes, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("Edit")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun eventColorsFor(state: CalendarUiState, date: LocalDate): List<Color> =
    state.eventsByDay[date].orEmpty().map { memberColor(state, it.event.colorUid) }

private fun memberColor(state: CalendarUiState, uid: String): Color {
    val index = state.membersByUid[uid]?.colorIndex ?: 0
    return MemberColors[index % MemberColors.size]
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = month.formatTitle(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToday) {
            Icon(Icons.Filled.Today, contentDescription = "Jump to today")
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun MemberLegend(state: CalendarUiState) {
    val members = state.membersByUid.values.toList()
    if (members.size < 2) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        members.forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MemberColors[member.colorIndex % MemberColors.size]),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = member.displayName.ifBlank { member.email.substringBefore("@") },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        for (dayOfWeek in daysOfWeek()) {
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    isSelected: Boolean,
    eventColors: List<Color>,
    onClick: () -> Unit,
) {
    val inMonth = day.position == DayPosition.MonthDate
    val isToday = day.date == LocalDate.now()

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(enabled = inMonth, onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (inMonth && eventColors.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    eventColors.take(4).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaSection(
    date: LocalDate,
    occurrences: List<EventOccurrence>,
    colorFor: (EventOccurrence) -> Color,
    onOccurrenceClick: (EventOccurrence) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = date.formatDate(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (occurrences.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Nothing planned. Tap + to add an event.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(occurrences, key = { it.event.id + it.start.toString() }) { occ ->
                    AgendaRow(
                        occurrence = occ,
                        color = colorFor(occ),
                        onClick = { onOccurrenceClick(occ) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(
    occurrence: EventOccurrence,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = occurrence.event.title.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = timeLabel(occurrence),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (occurrence.event.recurrence != Recurrence.NONE) {
            Icon(
                Icons.Filled.Repeat,
                contentDescription = "Repeats",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (occurrence.event.reminderMinutes != Reminders.NONE) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = "Has a reminder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun timeLabel(occurrence: EventOccurrence): String {
    if (occurrence.event.allDay) return "All day"
    return "${occurrence.start.formatTime()} – ${occurrence.end.formatTime()}"
}
