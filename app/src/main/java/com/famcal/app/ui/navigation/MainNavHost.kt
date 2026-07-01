package com.famcal.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.famcal.app.ui.event.EventEditorScreen
import com.famcal.app.ui.family.FamiliesScreen
import com.famcal.app.ui.family.FamilySetupScreen
import com.famcal.app.ui.home.HomeScreen
import com.famcal.app.ui.lists.ListDetailScreen
import com.famcal.app.ui.settings.SettingsScreen
import java.time.ZoneId

/**
 * Navigation for a signed-in user who belongs to a family: the calendar and the
 * event editor. [familyId] is baked into the routes so each screen's view model can
 * read it from its SavedStateHandle.
 */
@Composable
fun MainNavHost(familyId: String) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home/$familyId",
    ) {
        composable(
            route = "home/{familyId}",
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) {
            HomeScreen(
                onAddEvent = { date ->
                    val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    navController.navigate("event/$familyId?dateMillis=$millis")
                },
                onEventClick = { eventId ->
                    navController.navigate("event/$familyId?eventId=$eventId")
                },
                onOpenSettings = { navController.navigate("settings/$familyId") },
                onOpenList = { listId -> navController.navigate("list/$familyId/$listId") },
            )
        }

        composable(
            route = "list/{familyId}/{listId}",
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("listId") { type = NavType.StringType },
            ),
        ) {
            ListDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "settings/{familyId}",
            arguments = listOf(navArgument("familyId") { type = NavType.StringType }),
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onManageFamilies = { navController.navigate("families") },
            )
        }

        composable("families") {
            FamiliesScreen(
                onBack = { navController.popBackStack() },
                onAddFamily = { navController.navigate("addFamily") },
            )
        }

        composable("addFamily") {
            FamilySetupScreen(onClose = { navController.popBackStack() })
        }

        composable(
            route = "event/{familyId}?eventId={eventId}&dateMillis={dateMillis}",
            arguments = listOf(
                navArgument("familyId") { type = NavType.StringType },
                navArgument("eventId") { type = NavType.StringType; defaultValue = "" },
                navArgument("dateMillis") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            EventEditorScreen(onClose = { navController.popBackStack() })
        }
    }
}
