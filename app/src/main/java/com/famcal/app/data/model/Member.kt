package com.famcal.app.data.model

import com.google.firebase.firestore.DocumentId

/**
 * A member of a family. Stored at `families/{familyId}/members/{uid}`.
 * [colorIndex] points into the per-member palette so each person's events render
 * in a consistent color across everyone's devices.
 */
data class Member(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = ROLE_MEMBER,
    val colorIndex: Int = 0,
) {
    companion object {
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"
    }
}
