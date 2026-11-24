package com.famcal.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famcal.app.data.OpenRequest
import com.famcal.app.ui.calendar.CalendarScreen
import com.famcal.app.ui.lists.ListsScreen
import com.famcal.app.ui.tasks.TasksScreen
import com.famcal.app.ui.tasks.TasksViewModel
import java.time.LocalDate

private const val TAB_CALENDAR = 0
private const val TAB_TASKS = 1
private const val TAB_LISTS = 2

/**
 * Signed-in home shell with a bottom nav switching between the Calendar, Tasks and Lists tabs.
 * Full-screen destinations (editors, details, settings) are separate routes in
 * [com.famcal.app.ui.navigation.MainNavHost].
 */
@Composable
fun HomeScreen(
    /** A tapped notification/widget: switches to the Calendar or Tasks tab it targets. */
    openRequest: OpenRequest?,
    onAddEvent: (LocalDate) -> Unit,
    /** Opens the editor; a non-null date edits just that occurrence of a recurring event. */
    onEventClick: (eventId: String, occurrenceDate: LocalDate?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenList: (String) -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenStarterPacks: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_CALENDAR) }
    // Shared with the Tasks tab (same nav entry), so the badge stays live on other tabs.
    val tasksViewModel: TasksViewModel = hiltViewModel()
    val tasksState by tasksViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(openRequest) {
        when (openRequest?.target) {
            OpenRequest.Target.CALENDAR -> tab = TAB_CALENDAR
            OpenRequest.Target.TASKS -> tab = TAB_TASKS
            null -> Unit
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                TAB_CALENDAR -> CalendarScreen(
                    onAddEvent = onAddEvent,
                    onEventClick = onEventClick,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onOpenTask = onOpenTask,
                )
                TAB_TASKS -> TasksScreen(
                    viewModel = tasksViewModel,
                    onAddTask = onAddTask,
                    onOpenTask = onOpenTask,
                    onOpenLeaderboard = onOpenLeaderboard,
                    onOpenStarterPacks = onOpenStarterPacks,
                )
                else -> ListsScreen(onOpenList = onOpenList)
            }
        }
        NavigationBar {
            NavigationBarItem(
                selected = tab == TAB_CALENDAR,
                onClick = { tab = TAB_CALENDAR },
                icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                label = { Text("Calendar") },
            )
            NavigationBarItem(
                selected = tab == TAB_TASKS,
                onClick = { tab = TAB_TASKS },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tasksState.myDueCount > 0) Badge { Text(tasksState.myDueCount.toString()) }
                        },
                    ) { Icon(Icons.Filled.TaskAlt, contentDescription = null) }
                },
                label = { Text("Tasks") },
            )
            NavigationBarItem(
                selected = tab == TAB_LISTS,
                onClick = { tab = TAB_LISTS },
                icon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null) },
                label = { Text("Lists") },
            )
        }
    }
}
