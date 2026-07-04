package com.famcal.app.ui.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.list.ListRepository
import com.famcal.app.data.model.FamilyList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListsUiState(
    val lists: List<FamilyList> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ListsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val listRepository: ListRepository,
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<ListsUiState> = listRepository.observeLists(familyId)
        .map { ListsUiState(lists = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListsUiState())

    fun addList(name: String) {
        val uid = authRepository.currentUser?.uid ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            listRepository.createList(familyId, name, uid)
                .onFailure { _messages.emit("Couldn't create the list.") }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            listRepository.deleteList(familyId, listId)
                .onFailure { _messages.emit("Couldn't delete the list.") }
        }
    }
}
