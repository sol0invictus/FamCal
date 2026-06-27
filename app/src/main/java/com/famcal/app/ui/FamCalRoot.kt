package com.famcal.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famcal.app.ui.auth.AuthScreen
import com.famcal.app.ui.family.FamilySetupScreen
import com.famcal.app.ui.navigation.MainNavHost

/**
 * Chooses the top-level screen from [AppViewModel]'s state: loading splash while
 * auth resolves, then sign-in, family setup, or the calendar home.
 */
@Composable
fun FamCalRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        AppUiState.Loading -> LoadingScreen()
        AppUiState.SignedOut -> AuthScreen()
        AppUiState.NeedsFamily -> FamilySetupScreen()
        is AppUiState.Ready ->
            // Rebuild the nav graph when the active family changes so each family gets
            // its own fresh back stack starting at its calendar.
            key(current.activeFamilyId) {
                MainNavHost(familyId = current.activeFamilyId)
            }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
