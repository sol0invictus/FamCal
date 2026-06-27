package com.famcal.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.ActiveFamilyStore
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.Family
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Top-level app state that drives which screen the user sees. */
sealed interface AppUiState {
    data object Loading : AppUiState
    data object SignedOut : AppUiState
    data object NeedsFamily : AppUiState
    data class Ready(val families: List<Family>, val activeFamilyId: String) : AppUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val activeFamilyStore: ActiveFamilyStore,
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = authRepository.authState
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(AppUiState.SignedOut)
            } else {
                flow<AppUiState> {
                    // Make sure the user profile doc exists before we read families.
                    familyRepository.ensureUser(user)
                    emitAll(
                        combine(
                            familyRepository.observeFamilies(user.uid),
                            activeFamilyStore.activeFamilyId,
                        ) { families, active ->
                            if (families.isEmpty()) {
                                AppUiState.NeedsFamily
                            } else {
                                // Fall back to the first family if the stored choice is gone.
                                val chosen = families.firstOrNull { it.id == active } ?: families.first()
                                AppUiState.Ready(families, chosen.id)
                            }
                        },
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppUiState.Loading,
        )
}
