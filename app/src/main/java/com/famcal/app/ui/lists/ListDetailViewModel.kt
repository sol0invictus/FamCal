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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val listRepository: ListRepository,
    familyRepository: FamilyRepository,
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }
    private val listId: String = checkNotNull(savedStateHandle["listId"]) { "listId required" }

    val membersByUid: StateFlow<Map<String, Member>> = familyRepository.observeMembers(familyId)
        .map { members -> members.associateBy { it.uid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val listName: StateFlow<String> = listRepository.observeList(familyId, listId)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val items: StateFlow<List<ListItem>> = listRepository.observeItems(familyId, listId)
        // Unchecked first, then by creation time.
        .map { list -> list.sortedWith(compareBy({ it.checked }, { it.createdAt?.time ?: Long.MAX_VALUE })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addItem(text: String) {
        val uid = authRepository.currentUser?.uid ?: return
        if (text.isBlank()) return
        viewModelScope.launch { listRepository.addItem(familyId, listId, text, uid) }
    }

    fun toggle(item: ListItem) {
        viewModelScope.launch {
            listRepository.setItemChecked(familyId, listId, item.id, !item.checked)
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { listRepository.deleteItem(familyId, listId, itemId) }
    }

    fun clearChecked() {
        val checked = items.value.filter { it.checked }
        viewModelScope.launch {
            checked.forEach { listRepository.deleteItem(familyId, listId, it.id) }
        }
    }

    /** Re-adds a deleted item (for Undo). */
    fun restoreItem(item: ListItem) {
        viewModelScope.launch { listRepository.addItem(familyId, listId, item.text, item.createdBy) }
    }
}
