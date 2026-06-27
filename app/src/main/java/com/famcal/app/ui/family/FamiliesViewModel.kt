package com.famcal.app.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.ActiveFamilyStore
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.model.Family
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamiliesUiState(
    val families: List<Family> = emptyList(),
    val activeFamilyId: String? = null,
)

@HiltViewModel
class FamiliesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
    private val activeFamilyStore: ActiveFamilyStore,
) : ViewModel() {

    private val uid = authRepository.currentUser?.uid

    val uiState: StateFlow<FamiliesUiState> = combine(
        if (uid != null) familyRepository.observeFamilies(uid) else flowOf(emptyList()),
        activeFamilyStore.activeFamilyId,
    ) { families, active ->
        FamiliesUiState(families = families, activeFamilyId = active)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FamiliesUiState(),
    )

    fun switchTo(familyId: String) {
        activeFamilyStore.setActiveFamilyId(familyId)
    }

    fun leave(familyId: String) {
        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            familyRepository.leaveFamily(familyId, user).onSuccess {
                // If we left the active family, clear the selection so the app falls back.
                if (activeFamilyStore.activeFamilyId.value == familyId) {
                    activeFamilyStore.setActiveFamilyId(null)
                }
            }
        }
    }
}
