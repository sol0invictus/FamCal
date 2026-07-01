package com.famcal.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.famcal.app.ui.calendar.CalendarScreen
import com.famcal.app.ui.lists.ListsScreen
import java.time.LocalDate

/**
 * Signed-in home shell with a bottom nav switching between the Calendar and Lists tabs.
 * Full-screen destinations (event editor, list detail, settings) are separate routes in
 * [com.famcal.app.ui.navigation.MainNavHost].
 */
@Composable
fun HomeScreen(
    onAddEvent: (LocalDate) -> Unit,
    onEventClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenList: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> CalendarScreen(
                    onAddEvent = onAddEvent,
                    onEventClick = onEventClick,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                )
                else -> ListsScreen(onOpenList = onOpenList)
            }
        }
        NavigationBar {
            NavigationBarItem(
                selected = tab == 0,
                onClick = { tab = 0 },
                icon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                label = { Text("Calendar") },
            )
            NavigationBarItem(
                selected = tab == 1,
                onClick = { tab = 1 },
                icon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null) },
                label = { Text("Lists") },
            )
        }
    }
}
