package com.famcal.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A family group. Stored at `families/{id}`. Membership is tracked both as a flat
 * [memberUids] list (so a single `arrayContains` query can find a user's families
 * and security rules can check membership cheaply) and as richer documents in the
 * `families/{id}/members` subcollection ([Member]).
 */
data class Family(
    @DocumentId val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val inviteCode: String = "",
    /** Secret token for the read-only iCal subscription feed (see Cloud Function). */
    val feedToken: String = "",
    @ServerTimestamp val createdAt: Date? = null,
)
