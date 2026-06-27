package com.famcal.app.ui.settings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.family.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName: String = "",
    val email: String = "",
    val familyName: String = "",
    val inviteCode: String = "",
    val feedUrl: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val familyRepository: FamilyRepository,
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }
    private val displayName = authRepository.currentUser?.displayName.orEmpty()
    private val email = authRepository.currentUser?.email.orEmpty()
    private val projectId = readProjectId(context)

    val uiState: StateFlow<SettingsUiState> = familyRepository.observeFamily(familyId)
        .map { family ->
            SettingsUiState(
                displayName = displayName,
                email = email,
                familyName = family?.name.orEmpty(),
                inviteCode = family?.inviteCode.orEmpty(),
                feedUrl = feedUrl(family?.feedToken),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(displayName = displayName, email = email),
        )

    init {
        // Generate the subscription token if this family doesn't have one yet.
        viewModelScope.launch { familyRepository.ensureFeedToken(familyId) }
    }

    fun signOut() = authRepository.signOut()

    private fun feedUrl(token: String?): String? {
        if (token.isNullOrBlank() || projectId.isNullOrBlank()) return null
        return "https://$FUNCTION_REGION-$projectId.cloudfunctions.net/calendarFeed" +
            "?familyId=$familyId&token=$token"
    }

    private fun readProjectId(context: Context): String? {
        val resId = context.resources.getIdentifier("project_id", "string", context.packageName)
        return if (resId == 0) null else context.getString(resId)
    }

    companion object {
        // Default region for HTTPS Cloud Functions; change if you deploy elsewhere.
        private const val FUNCTION_REGION = "us-central1"
    }
}
