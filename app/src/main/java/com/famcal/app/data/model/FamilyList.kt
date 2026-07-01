package com.famcal.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/** A shared list (e.g. "Groceries", "To-do") at `families/{familyId}/lists/{id}`. */
data class FamilyList(
    @DocumentId val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Date? = null,
)

/** An item within a list at `families/{familyId}/lists/{listId}/items/{id}`. */
data class ListItem(
    @DocumentId val id: String = "",
    val text: String = "",
    val checked: Boolean = false,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Date? = null,
)
