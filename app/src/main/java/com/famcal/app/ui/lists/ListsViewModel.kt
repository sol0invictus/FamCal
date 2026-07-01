package com.famcal.app.ui.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.list.ListRepository
import com.famcal.app.data.model.FamilyList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val listRepository: ListRepository,
) : ViewModel() {

    private val familyId: String = checkNotNull(savedStateHandle["familyId"]) { "familyId required" }

    val lists: StateFlow<List<FamilyList>> = listRepository.observeLists(familyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addList(name: String) {
        val uid = authRepository.currentUser?.uid ?: return
        if (name.isBlank()) return
        viewModelScope.launch { listRepository.createList(familyId, name, uid) }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch { listRepository.deleteList(familyId, listId) }
    }
}
