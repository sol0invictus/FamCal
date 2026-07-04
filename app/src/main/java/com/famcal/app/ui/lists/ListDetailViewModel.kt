package com.famcal.app.ui.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.list.ListRepository
import com.famcal.app.data.model.ListItem
import com.famcal.app.data.model.Member
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

data class ListDetailUiState(
    val items: List<ListItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val listRepository: ListRepository,
    familyRepository: FamilyRepository,
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }
    private val listId: String = checkNotNull(savedStateHandle["listId"]) { "listId required" }

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val membersByUid: StateFlow<Map<String, Member>> = familyRepository.observeMembers(familyId)
        .map { members -> members.associateBy { it.uid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val listName: StateFlow<String> = listRepository.observeList(familyId, listId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val uiState: StateFlow<ListDetailUiState> = listRepository.observeItems(familyId, listId)
        // Unchecked first, then by creation time.
        .map { list ->
            val sorted = list.sortedWith(compareBy({ it.checked }, { it.createdAt?.time ?: Long.MAX_VALUE }))
            ListDetailUiState(items = sorted, isLoading = false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDetailUiState())

    fun addItem(text: String) {
        val uid = authRepository.currentUser?.uid ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            listRepository.addItem(familyId, listId, text, uid)
                .onFailure { _messages.emit("Couldn't add the item.") }
        }
    }

    fun toggle(item: ListItem) {
        viewModelScope.launch {
            listRepository.setItemChecked(familyId, listId, item.id, !item.checked)
                .onFailure { _messages.emit("Couldn't update the item.") }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            listRepository.deleteItem(familyId, listId, itemId)
                .onFailure { _messages.emit("Couldn't delete the item.") }
        }
    }

    fun clearChecked() {
        val checked = uiState.value.items.filter { it.checked }
        viewModelScope.launch {
            checked.forEach { listRepository.deleteItem(familyId, listId, it.id) }
        }
    }

    /** Re-adds a deleted item (for Undo). */
    fun restoreItem(item: ListItem) {
        viewModelScope.launch { listRepository.addItem(familyId, listId, item.text, item.createdBy) }
    }
}
