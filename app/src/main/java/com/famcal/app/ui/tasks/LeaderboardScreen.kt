package com.famcal.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.Member
import com.famcal.app.data.model.TaskCompletion
import com.famcal.app.data.task.TaskRepository
import com.famcal.app.util.toLocalDate
import com.famcal.app.util.toTimestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class BoardPeriod(val label: String) { WEEK("This week"), MONTH("This month"), YEAR("This year") }

data class BoardRow(val member: Member?, val uid: String, val points: Int, val count: Int)

data class LeaderboardUiState(
    val period: BoardPeriod = BoardPeriod.WEEK,
    val rows: List<BoardRow> = emptyList(),
    val recent: List<TaskCompletion> = emptyList(),
    val membersByUid: Map<String, Member> = emptyMap(),
    val currentUid: String = "",
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    familyRepository: FamilyRepository,
    authRepository: AuthRepository,
    taskRepository: TaskRepository,
) : ViewModel() {
    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }
    private val period = MutableStateFlow(BoardPeriod.WEEK)
    private val currentUid = authRepository.currentUser?.uid.orEmpty()

    // One listener for the whole year; shorter periods are filtered here.
    private val yearStart = LocalDate.now().withDayOfYear(1)

    val uiState: StateFlow<LeaderboardUiState> = combine(
        taskRepository.observeCompletionsSince(familyId, yearStart.toTimestamp()),
        familyRepository.observeMembers(familyId),
        period,
    ) { completions, members, p ->
        val today = LocalDate.now()
        val from = when (p) {
            BoardPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            BoardPeriod.MONTH -> today.withDayOfMonth(1)
            BoardPeriod.YEAR -> yearStart
        }
        val inPeriod = completions.filter { !it.doneAt.toLocalDate().isBefore(from) }
        val byMember = members.associateBy { it.uid }
        // Everyone in the family appears, even with zero points.
        val uids = (members.map { it.uid } + inPeriod.map { it.doneBy }).distinct()
        val rows = uids.map { uid ->
            val mine = inPeriod.filter { it.doneBy == uid }
            BoardRow(byMember[uid], uid, mine.sumOf { it.points }, mine.size)
        }.sortedWith(compareByDescending<BoardRow> { it.points }.thenBy { it.member?.displayName.orEmpty() })
        LeaderboardUiState(p, rows, inPeriod.take(RECENT_LIMIT), byMember, currentUid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaderboardUiState())

    fun setPeriod(p: BoardPeriod) {
        period.value = p
    }

    private companion object {
        const val RECENT_LIMIT = 25
    }
}

private val recentFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(onBack: () -> Unit, viewModel: LeaderboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val maxPoints = state.rows.maxOfOrNull { it.points }?.coerceAtLeast(1) ?: 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Points") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = state.period.ordinal) {
                BoardPeriod.entries.forEach { p ->
                    Tab(
                        selected = state.period == p,
                        onClick = { viewModel.setPeriod(p) },
                        text = { Text(p.label) },
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.rows, key = { it.uid }) { row ->
                    val name = when {
                        row.uid == state.currentUid -> "You"
                        else -> row.member?.displayName ?: "Former member"
                    }
                    val rank = state.rows.indexOf(row)
                    val medal = if (row.points > 0) listOf("🥇", "🥈", "🥉").getOrNull(rank).orEmpty() else ""
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(memberColor(row.member)))
                            Spacer(Modifier.width(10.dp))
                            Text("$medal $name".trim(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("⭐ ${row.points}", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(row.points / maxPoints.toFloat())
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(memberColor(row.member)),
                        )
                        Text(
                            "${row.count} ${if (row.count == 1) "task" else "tasks"} done",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                if (state.recent.isEmpty()) {
                    item {
                        Text(
                            "No tasks done yet in this period.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                items(state.recent, key = { "recent-" + it.id }) { c ->
                    val who = if (c.doneBy == state.currentUid) "You" else state.membersByUid[c.doneBy]?.displayName ?: "Someone"
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text("$who · ${c.taskTitle}", modifier = Modifier.weight(1f))
                        Text(
                            "${c.doneAt.toLocalDate().format(recentFormatter)}  ⭐${c.points}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
