package com.famcal.app.data.family

import com.famcal.app.data.model.Family
import com.famcal.app.data.model.Member
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Reads and writes families, their members, and user profile documents in Firestore.
 */
@Singleton
class FamilyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val families get() = firestore.collection(COLLECTION_FAMILIES)
    private val users get() = firestore.collection(COLLECTION_USERS)
    private val invites get() = firestore.collection(COLLECTION_INVITES)

    /** Creates/updates `users/{uid}` with the latest profile, leaving familyIds intact. */
    suspend fun ensureUser(user: FirebaseUser): Result<Unit> = runCatching {
        val data = mapOf(
            "displayName" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
            "photoUrl" to (user.photoUrl?.toString() ?: ""),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        users.document(user.uid).set(data, SetOptions.merge()).await()
    }

    /** Streams a single family document, updating live. */
    fun observeFamily(familyId: String): Flow<Family?> = callbackFlow {
        val registration = families.document(familyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Family::class.java))
            }
        awaitClose { registration.remove() }
    }

    /** Streams the members of a family, updating live. */
    fun observeMembers(familyId: String): Flow<List<Member>> = callbackFlow {
        val registration = families.document(familyId).collection(SUBCOLLECTION_MEMBERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Member::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    /** Streams the families the user belongs to, updating live as they change. */
    fun observeFamilies(uid: String): Flow<List<Family>> = callbackFlow {
        val registration = families
            .whereArrayContains("memberUids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Family::class.java).orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun createFamily(name: String, user: FirebaseUser): Result<Family> = runCatching {
        val docRef = families.document()
        val family = Family(
            id = docRef.id,
            name = name.trim(),
            ownerUid = user.uid,
            memberUids = listOf(user.uid),
            inviteCode = generateInviteCode(),
        )
        val member = Member(
            uid = user.uid,
            displayName = user.displayName ?: "",
            email = user.email ?: "",
            role = Member.ROLE_OWNER,
            colorIndex = 0,
        )

        firestore.runBatch { batch ->
            batch.set(docRef, family)
            batch.set(docRef.collection(SUBCOLLECTION_MEMBERS).document(user.uid), member)
            // Invite lookup doc keyed by the code, so joiners can resolve it by id
            // without being able to read or enumerate family data.
            batch.set(
                invites.document(family.inviteCode),
                mapOf(
                    "familyId" to family.id,
                    "familyName" to family.name,
                    "ownerUid" to user.uid,
                ),
            )
            batch.set(
                users.document(user.uid),
                mapOf("familyIds" to FieldValue.arrayUnion(docRef.id)),
                SetOptions.merge(),
            )
        }.await()

        family
    }

    /** Joins the family matching [code]. Returns failure if no family has that code. */
    suspend fun joinFamily(code: String, user: FirebaseUser): Result<Family> = runCatching {
        val normalized = code.trim().uppercase()
        val invite = invites.document(normalized).get().await()
        val familyId = invite.getString("familyId")
            ?.takeIf { invite.exists() }
            ?: error("No family found for code \"$normalized\"")
        val familyRef = families.document(familyId)

        val member = Member(
            uid = user.uid,
            displayName = user.displayName ?: "",
            email = user.email ?: "",
            role = Member.ROLE_MEMBER,
            // Derive a stable color from the uid so we don't need to read the (not-yet
            // visible) member list before joining.
            colorIndex = (user.uid.hashCode() and Int.MAX_VALUE) % MEMBER_COLOR_COUNT,
        )

        // Blind writes (no prior read) — a non-member can only add themselves, which the
        // security rules enforce. After this the user is a member and can read the family.
        firestore.runBatch { batch ->
            batch.update(familyRef, "memberUids", FieldValue.arrayUnion(user.uid))
            batch.set(familyRef.collection(SUBCOLLECTION_MEMBERS).document(user.uid), member)
            batch.set(
                users.document(user.uid),
                mapOf("familyIds" to FieldValue.arrayUnion(familyId)),
                SetOptions.merge(),
            )
        }.await()

        familyRef.get().await().toObject(Family::class.java) ?: error("Family is unreadable")
    }

    /** Renames a family. */
    suspend fun renameFamily(familyId: String, name: String): Result<Unit> = runCatching {
        families.document(familyId).update("name", name.trim()).await()
    }

    /** Removes the current user from a family (and their member doc + familyIds entry). */
    suspend fun leaveFamily(familyId: String, user: FirebaseUser): Result<Unit> = runCatching {
        val ref = families.document(familyId)
        firestore.runBatch { batch ->
            batch.update(ref, "memberUids", FieldValue.arrayRemove(user.uid))
            batch.delete(ref.collection(SUBCOLLECTION_MEMBERS).document(user.uid))
            batch.set(
                users.document(user.uid),
                mapOf("familyIds" to FieldValue.arrayRemove(familyId)),
                SetOptions.merge(),
            )
        }.await()
    }

    /** Ensures the family has a feed token for the iCal subscription URL; creates one if missing. */
    suspend fun ensureFeedToken(familyId: String): Result<String> = runCatching {
        val ref = families.document(familyId)
        val existing = ref.get().await().getString("feedToken")
        if (!existing.isNullOrBlank()) return@runCatching existing
        val token = java.util.UUID.randomUUID().toString().replace("-", "")
        ref.set(mapOf("feedToken" to token), SetOptions.merge()).await()
        token
    }

    private fun generateInviteCode(): String =
        (1..INVITE_CODE_LENGTH)
            .map { INVITE_CODE_ALPHABET[Random.nextInt(INVITE_CODE_ALPHABET.length)] }
            .joinToString("")

    companion object {
        private const val COLLECTION_FAMILIES = "families"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_INVITES = "invites"
        private const val SUBCOLLECTION_MEMBERS = "members"
        private const val INVITE_CODE_LENGTH = 6
        // Omit easily-confused characters (0/O, 1/I) for codes people read aloud.
        private const val INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val MEMBER_COLOR_COUNT = 6
    }
}
