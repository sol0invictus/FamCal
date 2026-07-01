package com.famcal.app.data.list

import com.famcal.app.data.model.FamilyList
import com.famcal.app.data.model.ListItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes shared lists and their items within a family. */
@Singleton
class ListRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun lists(familyId: String) =
        firestore.collection("families").document(familyId).collection("lists")

    private fun items(familyId: String, listId: String) =
        lists(familyId).document(listId).collection("items")

    fun observeLists(familyId: String): Flow<List<FamilyList>> = callbackFlow {
        val registration = lists(familyId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(FamilyList::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    fun observeList(familyId: String, listId: String): Flow<FamilyList?> = callbackFlow {
        val registration = lists(familyId).document(listId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(FamilyList::class.java))
            }
        awaitClose { registration.remove() }
    }

    fun observeItems(familyId: String, listId: String): Flow<List<ListItem>> = callbackFlow {
        val registration = items(familyId, listId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(ListItem::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun createList(familyId: String, name: String, uid: String): Result<Unit> = runCatching {
        lists(familyId).add(FamilyList(name = name.trim(), createdBy = uid)).await()
    }

    suspend fun deleteList(familyId: String, listId: String): Result<Unit> = runCatching {
        // Best-effort item cleanup so we don't orphan documents.
        val existing = items(familyId, listId).get().await()
        for (doc in existing.documents) doc.reference.delete().await()
        lists(familyId).document(listId).delete().await()
    }

    suspend fun addItem(familyId: String, listId: String, text: String, uid: String): Result<Unit> =
        runCatching {
            items(familyId, listId).add(ListItem(text = text.trim(), createdBy = uid)).await()
        }

    suspend fun setItemChecked(
        familyId: String,
        listId: String,
        itemId: String,
        checked: Boolean,
    ): Result<Unit> = runCatching {
        items(familyId, listId).document(itemId).update("checked", checked).await()
    }

    suspend fun deleteItem(familyId: String, listId: String, itemId: String): Result<Unit> =
        runCatching {
            items(familyId, listId).document(itemId).delete().await()
        }
}
