package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AiChatMessage
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.FriendRequest
import com.example.data.model.IceCandidateSignal
import com.example.data.model.PublicProfile
import com.example.data.model.StoryItem
import com.example.data.model.UserAccount
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val STORY_TTL_MILLIS = 24L * 60L * 60L * 1000L

class LinkoraRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun sanitizeUsername(raw: String): String {
        return raw.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
    }

    fun buildChatId(uidA: String, uidB: String): String {
        return if (uidA < uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"
    }

    // --- 1. PROFILE & UNIQUE USERNAME ---

    fun observeUserAccount(uid: String): Flow<UserAccount?> = flow {
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun checkUsernameAvailable(rawHandle: String): Result<Boolean> {
        val handle = sanitizeUsername(rawHandle)
        if (handle.length !in 3..30) return Result.success(false)
        val path = "usernames/$handle"
        return try {
            val uid = requireUserId()
            val snap = db.collection("usernames").document(handle).get().await()
            if (!snap.exists()) {
                Result.success(true)
            } else {
                val ownerUid = snap.getString("userId")
                Result.success(ownerUid == uid)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun completeProfileSetup(
        fullName: String,
        rawUsername: String,
        bio: String,
        avatarData: String?
    ): Result<Unit> {
        val cleanName = fullName.trim().take(80)
        val cleanHandle = sanitizeUsername(rawUsername)
        val cleanBio = bio.trim().take(250)
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Please enter your name."))
        if (cleanHandle.length !in 3..30) {
            return Result.failure(IllegalArgumentException("Username must be 3–30 characters (letters, numbers, underscore)."))
        }

        var path = "users"
        return try {
            val uid = requireUserId()
            val email = auth.currentUser?.email
            path = "usernames/$cleanHandle"

            val existingClaim = db.collection("usernames").document(cleanHandle).get().await()
            if (existingClaim.exists() && existingClaim.getString("userId") != uid) {
                return Result.failure(IllegalStateException("Username @$cleanHandle is already taken."))
            }

            val existingUserDoc = db.collection("users").document(uid).get().await()
            val previousUsername = existingUserDoc.getString("username")

            val batch = db.batch()

            // Reserve username if not already claimed by this user
            if (!existingClaim.exists()) {
                val claimRef = db.collection("usernames").document(cleanHandle)
                batch.set(
                    claimRef,
                    mapOf(
                        "userId" to uid,
                        "username" to cleanHandle,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // Release old username if changed
            if (!previousUsername.isNullOrBlank() && previousUsername != cleanHandle) {
                batch.delete(db.collection("usernames").document(previousUsername))
            }

            val userRef = db.collection("users").document(uid)
            val publicRef = db.collection("publicProfiles").document(uid)

            if (existingUserDoc.exists()) {
                val userUpdate = mutableMapOf<String, Any>(
                    "fullName" to cleanName,
                    "username" to cleanHandle,
                    "bio" to cleanBio,
                    "online" to true,
                    "profileCompleted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (avatarData != null) userUpdate["avatarData"] = avatarData
                batch.update(userRef, userUpdate)

                val pubUpdate = mutableMapOf<String, Any>(
                    "fullName" to cleanName,
                    "username" to cleanHandle,
                    "bio" to cleanBio,
                    "online" to true,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (avatarData != null) pubUpdate["avatarData"] = avatarData
                batch.update(publicRef, pubUpdate)
            } else {
                val userPayload = mapOf<String, Any?>(
                    "userId" to uid,
                    "fullName" to cleanName,
                    "username" to cleanHandle,
                    "email" to email,
                    "bio" to cleanBio,
                    "avatarData" to avatarData,
                    "online" to true,
                    "discoverable" to true,
                    "profileCompleted" to true,
                    "notificationsEnabled" to true,
                    "friendUids" to emptyList<String>(),
                    "blockedUids" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }

                val publicPayload = mapOf<String, Any?>(
                    "userId" to uid,
                    "fullName" to cleanName,
                    "username" to cleanHandle,
                    "bio" to cleanBio,
                    "avatarData" to avatarData,
                    "online" to true,
                    "discoverable" to true,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }

                batch.set(userRef, userPayload)
                batch.set(publicRef, publicPayload)
            }

            path = "users/$uid"
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfileDetails(
        fullName: String,
        bio: String,
        avatarData: String?
    ): Result<Unit> {
        val cleanName = fullName.trim().take(80)
        val cleanBio = bio.trim().take(250)
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Name cannot be empty."))
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val batch = db.batch()
            val userUpdate = mutableMapOf<String, Any>(
                "fullName" to cleanName,
                "bio" to cleanBio,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val pubUpdate = mutableMapOf<String, Any>(
                "fullName" to cleanName,
                "bio" to cleanBio,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarData != null) {
                userUpdate["avatarData"] = avatarData
                pubUpdate["avatarData"] = avatarData
            }
            batch.update(db.collection("users").document(uid), userUpdate)
            batch.update(db.collection("publicProfiles").document(uid), pubUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setOnlineStatus(online: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "publicProfiles/$uid"
        return try {
            val snap = db.collection("publicProfiles").document(uid).get().await()
            if (!snap.exists()) return Result.success(Unit)
            val batch = db.batch()
            batch.update(
                db.collection("publicProfiles").document(uid),
                mapOf(
                    "online" to online,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "online" to online,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAccountPreferences(
        discoverable: Boolean,
        notificationsEnabled: Boolean
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val batch = db.batch()
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "discoverable" to discoverable,
                    "notificationsEnabled" to notificationsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("publicProfiles").document(uid),
                mapOf(
                    "discoverable" to discoverable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setGlobalDisappearingTimer(durationMs: Long): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "defaultDisappearingMs" to durationMs.coerceIn(0L, 3_000_000_000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUid: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "blockedUids" to FieldValue.arrayUnion(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun unblockUser(targetUid: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            db.collection("users").document(uid).update(
                mapOf(
                    "blockedUids" to FieldValue.arrayRemove(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 2. PEOPLE DISCOVERY & FRIEND REQUESTS ---

    fun observeDiscoverableProfiles(uid: String): Flow<List<PublicProfile>> = flow {
        val path = "publicProfiles"
        emitAll(
            db.collection("publicProfiles")
                .whereEqualTo("discoverable", true)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.online }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeFriendRequests(uid: String): Flow<List<FriendRequest>> = flow {
        val path = "friendRequests"
        emitAll(
            db.collection("friendRequests")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FriendRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun sendFriendRequest(
        me: UserAccount,
        target: PublicProfile
    ): Result<Unit> {
        val reqId = "${me.userId}_${target.userId}"
        val path = "friendRequests/$reqId"
        return try {
            val uid = requireUserId()
            check(uid == me.userId) { "Authenticated user mismatch" }
            require(target.userId.isNotBlank() && target.userId != uid) { "Invalid friend target" }

            val payload = mapOf<String, Any?>(
                "fromUid" to uid,
                "toUid" to target.userId,
                "memberUids" to listOf(uid, target.userId),
                "fromName" to me.fullName.take(80),
                "fromUsername" to me.username,
                "fromAvatar" to me.avatarData,
                "toName" to target.fullName.take(80),
                "toUsername" to target.username,
                "toAvatar" to target.avatarData,
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("friendRequests").document(reqId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun respondToFriendRequest(
        request: FriendRequest,
        accept: Boolean,
        myProfile: UserAccount
    ): Result<String?> {
        val path = "friendRequests/${request.id}"
        return try {
            val uid = requireUserId()
            check(uid == request.toUid) { "Only recipient can respond to friend request" }

            val reqRef = db.collection("friendRequests").document(request.id)
            if (!accept) {
                reqRef.update(
                    mapOf(
                        "status" to "declined",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return Result.success(null)
            }

            val chatId = buildChatId(request.fromUid, request.toUid)
            val chatRef = db.collection("chats").document(chatId)
            val existingChat = chatRef.get().await()

            val batch = db.batch()
            batch.update(
                reqRef,
                mapOf(
                    "status" to "accepted",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "friendUids" to FieldValue.arrayUnion(request.fromUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (!existingChat.exists()) {
                val avatars = mutableMapOf<String, String>()
                request.fromAvatar?.let { avatars[request.fromUid] = it }
                myProfile.avatarData?.let { avatars[uid] = it }
                val chatPayload = mapOf(
                    "isGroup" to false,
                    "groupName" to "",
                    "memberUids" to listOf(request.fromUid, uid).sorted(),
                    "memberNames" to mapOf(
                        request.fromUid to request.fromName,
                        uid to myProfile.fullName
                    ),
                    "memberUsernames" to mapOf(
                        request.fromUid to request.fromUsername,
                        uid to myProfile.username
                    ),
                    "memberAvatars" to avatars,
                    "typingUsers" to emptyMap<String, Boolean>(),
                    "archivedBy" to emptyMap<String, Boolean>(),
                    "lockedBy" to emptyMap<String, Boolean>(),
                    "hiddenBy" to emptyMap<String, Boolean>(),
                    "clearedAtMillis" to emptyMap<String, Long>(),
                    "disappearingDurationMs" to myProfile.defaultDisappearingMs,
                    "lastMessageText" to "You are now connected on Linkora!",
                    "lastMessageType" to "text",
                    "lastMessageSenderId" to uid,
                    "lastMessageStatus" to "sent",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(chatRef, chatPayload)
            } else {
                batch.update(
                    chatRef,
                    mapOf(
                        "hiddenBy.$uid" to false,
                        "hiddenBy.${request.fromUid}" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeFriend(friendUid: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val chatId = buildChatId(uid, friendUid)
            val chatRef = db.collection("chats").document(chatId)
            val chatSnap = chatRef.get().await()

            val batch = db.batch()
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "friendUids" to FieldValue.arrayRemove(friendUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (chatSnap.exists()) {
                batch.update(
                    chatRef,
                    mapOf(
                        "hiddenBy.$uid" to true,
                        "archivedBy.$uid" to false,
                        "lockedBy.$uid" to false,
                        "clearedAtMillis.$uid" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncAcceptedFriendsIntoMyList(friendUidsToAdd: List<String>): Result<Unit> {
        if (friendUidsToAdd.isEmpty()) return Result.success(Unit)
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "friendUids" to FieldValue.arrayUnion(*friendUidsToAdd.toTypedArray()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 3. CHATS, GROUPS, ARCHIVE/LOCK/DISAPPEARING & REAL-TIME RICH MESSAGES ---

    fun observeChats(uid: String): Flow<List<ChatThread>> = flow {
        val path = "chats"
        emitAll(
            db.collection("chats")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeMessages(chatId: String, uid: String): Flow<List<ChatMessage>> = flow {
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createGroupChat(
        me: UserAccount,
        groupName: String,
        selectedMembers: List<PublicProfile>
    ): Result<String> {
        val cleanName = groupName.trim().take(80)
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Enter a group name."))
        val path = "chats"
        return try {
            val uid = requireUserId()
            val memberUids = (listOf(uid) + selectedMembers.map { it.userId }).distinct().take(50)
            if (memberUids.size < 2) {
                return Result.failure(IllegalArgumentException("Select at least 1 friend for the group."))
            }
            val names = mutableMapOf(uid to me.fullName)
            val usernames = mutableMapOf(uid to me.username)
            val avatars = mutableMapOf<String, String>()
            me.avatarData?.let { avatars[uid] = it }
            selectedMembers.forEach { p ->
                names[p.userId] = p.fullName
                usernames[p.userId] = p.username
                p.avatarData?.let { avatars[p.userId] = it }
            }
            val chatRef = db.collection("chats").document()
            val payload = mapOf(
                "isGroup" to true,
                "groupName" to cleanName,
                "memberUids" to memberUids,
                "memberNames" to names,
                "memberUsernames" to usernames,
                "memberAvatars" to avatars,
                "typingUsers" to emptyMap<String, Boolean>(),
                "archivedBy" to emptyMap<String, Boolean>(),
                "lockedBy" to emptyMap<String, Boolean>(),
                "hiddenBy" to emptyMap<String, Boolean>(),
                "clearedAtMillis" to emptyMap<String, Long>(),
                "disappearingDurationMs" to me.defaultDisappearingMs,
                "lastMessageText" to "${me.fullName} created group \"$cleanName\"",
                "lastMessageType" to "text",
                "lastMessageSenderId" to uid,
                "lastMessageStatus" to "sent",
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            chatRef.set(payload).await()
            Result.success(chatRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun setChatArchived(chatId: String, archived: Boolean): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>(
                "archivedBy.$uid" to archived,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (archived) {
                updates["lockedBy.$uid"] = false
                updates["hiddenBy.$uid"] = false
            }
            db.collection("chats").document(chatId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setChatLocked(chatId: String, locked: Boolean): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>(
                "lockedBy.$uid" to locked,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (locked) {
                updates["archivedBy.$uid"] = false
                updates["hiddenBy.$uid"] = false
            }
            db.collection("chats").document(chatId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteChatForMe(chatId: String): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            db.collection("chats").document(chatId).update(
                mapOf(
                    "hiddenBy.$uid" to true,
                    "archivedBy.$uid" to false,
                    "lockedBy.$uid" to false,
                    "clearedAtMillis.$uid" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreHiddenChat(chatId: String): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            val uid = requireUserId()
            db.collection("chats").document(chatId).update(
                mapOf(
                    "hiddenBy.$uid" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setChatDisappearingTimer(chatId: String, durationMs: Long): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            requireUserId()
            db.collection("chats").document(chatId).update(
                mapOf(
                    "disappearingDurationMs" to durationMs.coerceIn(0L, 3_000_000_000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendTextMessage(
        chat: ChatThread,
        text: String,
        senderName: String = ""
    ): Result<Unit> {
        val cleanText = text.trim().take(2000)
        if (cleanText.isBlank()) return Result.success(Unit)
        val path = "chats/${chat.id}/messages"
        return try {
            val uid = requireUserId()
            val receiverId = chat.memberUids.firstOrNull { it != uid }
                ?: return Result.failure(IllegalStateException("Invalid chat recipient"))

            val msgRef = db.collection("chats").document(chat.id).collection("messages").document()
            val chatRef = db.collection("chats").document(chat.id)

            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "chatId" to chat.id,
                    "senderId" to uid,
                    "senderName" to senderName.take(80),
                    "receiverId" to receiverId,
                    "memberUids" to chat.memberUids,
                    "messageType" to "text",
                    "text" to cleanText,
                    "audioDurationSec" to 0L,
                    "status" to "sent",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val chatUpdate = mutableMapOf<String, Any>(
                "lastMessageText" to cleanText.take(500),
                "lastMessageType" to "text",
                "lastMessageSenderId" to uid,
                "lastMessageStatus" to "sent",
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "typingUsers.$uid" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            chat.memberUids.forEach { memberUid ->
                chatUpdate["hiddenBy.$memberUid"] = false
            }
            batch.update(chatRef, chatUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendVoiceMessage(
        chat: ChatThread,
        audioBase64: String,
        durationSec: Int,
        senderName: String = ""
    ): Result<Unit> {
        val path = "chats/${chat.id}/messages"
        return try {
            val uid = requireUserId()
            val receiverId = chat.memberUids.firstOrNull { it != uid }
                ?: return Result.failure(IllegalStateException("Invalid chat recipient"))

            val clampedDuration = durationSec.coerceIn(1, 120).toLong()
            val msgRef = db.collection("chats").document(chat.id).collection("messages").document()
            val chatRef = db.collection("chats").document(chat.id)
            val preview = "Voice message (${clampedDuration}s)"

            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "chatId" to chat.id,
                    "senderId" to uid,
                    "senderName" to senderName.take(80),
                    "receiverId" to receiverId,
                    "memberUids" to chat.memberUids,
                    "messageType" to "voice",
                    "text" to preview,
                    "audioData" to audioBase64,
                    "audioDurationSec" to clampedDuration,
                    "status" to "sent",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val chatUpdate = mutableMapOf<String, Any>(
                "lastMessageText" to preview,
                "lastMessageType" to "voice",
                "lastMessageSenderId" to uid,
                "lastMessageStatus" to "sent",
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "typingUsers.$uid" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            chat.memberUids.forEach { memberUid ->
                chatUpdate["hiddenBy.$memberUid"] = false
            }
            batch.update(chatRef, chatUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendRichAttachmentMessage(
        chat: ChatThread,
        senderName: String,
        messageType: String,
        previewText: String,
        mediaData: String? = null,
        fileData: String? = null,
        fileName: String? = null,
        fileMimeType: String? = null,
        fileSizeBytes: Long = 0L,
        locationLat: Double? = null,
        locationLng: Double? = null,
        locationLabel: String? = null
    ): Result<Unit> {
        val path = "chats/${chat.id}/messages"
        return try {
            val uid = requireUserId()
            val receiverId = chat.memberUids.firstOrNull { it != uid }
                ?: return Result.failure(IllegalStateException("Invalid chat recipient"))

            val msgRef = db.collection("chats").document(chat.id).collection("messages").document()
            val chatRef = db.collection("chats").document(chat.id)

            val payload = mutableMapOf<String, Any?>(
                "chatId" to chat.id,
                "senderId" to uid,
                "senderName" to senderName.take(80),
                "receiverId" to receiverId,
                "memberUids" to chat.memberUids,
                "messageType" to messageType,
                "text" to previewText.take(2000),
                "audioDurationSec" to 0L,
                "mediaData" to mediaData,
                "fileData" to fileData,
                "fileName" to fileName?.take(200),
                "fileMimeType" to fileMimeType?.take(120),
                "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
                "locationLat" to locationLat,
                "locationLng" to locationLng,
                "locationLabel" to locationLabel?.take(200),
                "status" to "sent",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val batch = db.batch()
            batch.set(msgRef, payload)
            val chatUpdate = mutableMapOf<String, Any>(
                "lastMessageText" to previewText.take(500),
                "lastMessageType" to messageType,
                "lastMessageSenderId" to uid,
                "lastMessageStatus" to "sent",
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "typingUsers.$uid" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            chat.memberUids.forEach { memberUid ->
                chatUpdate["hiddenBy.$memberUid"] = false
            }
            batch.update(chatRef, chatUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markMessagesStatus(
        chatId: String,
        messagesToUpdate: List<ChatMessage>,
        newStatus: String
    ): Result<Unit> {
        if (messagesToUpdate.isEmpty()) return Result.success(Unit)
        val path = "chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            var count = 0
            messagesToUpdate.forEach { msg ->
                if (msg.senderId != uid && msg.status != "seen" && msg.status != newStatus) {
                    val ref = db.collection("chats").document(chatId)
                        .collection("messages").document(msg.id)
                    batch.update(
                        ref,
                        mapOf(
                            "status" to newStatus,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    count++
                }
            }
            if (count > 0) {
                batch.update(
                    db.collection("chats").document(chatId),
                    mapOf(
                        "lastMessageStatus" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setTypingStatus(chatId: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("chats").document(chatId).update(
                mapOf(
                    "typingUsers.$uid" to isTyping,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-fatal transient typing status failure
        }
    }

    suspend fun deleteMessageForMe(chatId: String, messageId: String): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "deletedForUids" to FieldValue.arrayUnion(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForEveryone(
        chatId: String,
        messageId: String,
        isLatestMessage: Boolean
    ): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            val batch = db.batch()
            val msgRef = db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            batch.update(
                msgRef,
                mapOf(
                    "deletedForEveryone" to true,
                    "text" to "This message was deleted",
                    "messageType" to "text",
                    "audioData" to null,
                    "audioDurationSec" to 0L,
                    "mediaData" to null,
                    "fileData" to null,
                    "fileName" to null,
                    "fileMimeType" to null,
                    "fileSizeBytes" to 0L,
                    "locationLat" to null,
                    "locationLng" to null,
                    "locationLabel" to null,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (isLatestMessage) {
                batch.update(
                    db.collection("chats").document(chatId),
                    mapOf(
                        "lastMessageText" to "This message was deleted",
                        "lastMessageType" to "text",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessagePermanently(
        chatId: String,
        messageId: String,
        isLatestMessage: Boolean,
        replacementPreview: ChatMessage?
    ): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            val batch = db.batch()
            val msgRef = db.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            batch.delete(msgRef)
            if (isLatestMessage) {
                val previewText = replacementPreview?.let {
                    if (it.deletedForEveryone) "This message was deleted" else it.text
                }?.take(500) ?: "No messages yet"
                val previewType = replacementPreview?.messageType ?: "text"
                batch.update(
                    db.collection("chats").document(chatId),
                    mapOf(
                        "lastMessageText" to previewText,
                        "lastMessageType" to previewType,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 4. 24-HOUR STORIES (PUBLIC & FRIENDS) ---

    fun observeActiveStories(uid: String): Flow<List<StoryItem>> {
        val publicFlow = db.collection("stories")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snap -> snap.toObjects(StoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, "stories(public)", uid)
                throw error
            }

        val friendsAndOwnFlow = db.collection("stories")
            .whereArrayContains("allowedViewerUids", uid)
            .snapshots()
            .map { snap -> snap.toObjects(StoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, "stories(friends)", uid)
                throw error
            }

        return combine(publicFlow, friendsAndOwnFlow) { pub, friends ->
            val now = System.currentTimeMillis()
            (pub + friends)
                .distinctBy { it.id }
                .filter { it.expiresAtMillis > now }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
    }

    suspend fun postStory(
        author: UserAccount,
        storyType: String,
        caption: String,
        mediaData: String?,
        musicTitle: String?,
        musicArtist: String?,
        backgroundHex: String,
        visibility: String
    ): Result<Unit> {
        val path = "stories"
        return try {
            val uid = requireUserId()
            check(uid == author.userId)
            val allowedViewers = (listOf(uid) + author.friendUids).distinct().take(501)
            val expiresAt = System.currentTimeMillis() + STORY_TTL_MILLIS

            val payload = mapOf<String, Any?>(
                "authorId" to uid,
                "authorName" to author.fullName.take(80),
                "authorUsername" to author.username,
                "authorAvatar" to author.avatarData,
                "storyType" to storyType,
                "caption" to caption.trim().take(500),
                "mediaData" to mediaData,
                "musicTitle" to musicTitle?.trim()?.take(120)?.ifBlank { null },
                "musicArtist" to musicArtist?.trim()?.take(120)?.ifBlank { null },
                "backgroundHex" to backgroundHex.take(16),
                "visibility" to if (visibility == "public") "public" else "friends",
                "allowedViewerUids" to allowedViewers,
                "expiresAtMillis" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("stories").document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        val path = "stories/$storyId"
        return try {
            requireUserId()
            db.collection("stories").document(storyId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun purgeExpiredOwnStories(uid: String) {
        try {
            val now = System.currentTimeMillis()
            val snap = db.collection("stories")
                .whereArrayContains("allowedViewerUids", uid)
                .get()
                .await()
            val expired = snap.documents.filter { doc ->
                doc.getString("authorId") == uid && (doc.getLong("expiresAtMillis") ?: 0L) <= now
            }
            if (expired.isNotEmpty()) {
                val batch = db.batch()
                expired.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (_: Exception) {
            // Non-fatal background cleanup
        }
    }

    // --- 5. REAL-TIME AUDIO & VIDEO CALLS + CALL HISTORY ---

    fun observeCalls(uid: String): Flow<List<CallSession>> = flow {
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun startCall(
        caller: UserAccount,
        calleeUid: String,
        calleeName: String,
        calleeAvatar: String?,
        callType: String
    ): Result<String> {
        val path = "calls"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("calls").document()
            val payload = mapOf<String, Any?>(
                "callerId" to uid,
                "calleeId" to calleeUid,
                "memberUids" to listOf(uid, calleeUid),
                "callerName" to caller.fullName.take(80),
                "callerAvatar" to caller.avatarData,
                "calleeName" to calleeName.take(80),
                "calleeAvatar" to calleeAvatar,
                "callType" to if (callType == "video") "video" else "audio",
                "status" to "ringing",
                "durationSeconds" to 0L,
                "webrtcEnabled" to true,
                "callerAudioSeq" to 0L,
                "calleeAudioSeq" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun answerCall(callId: String, accept: Boolean): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to if (accept) "connected" else "declined",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun endCall(callId: String, wasRinging: Boolean, durationSeconds: Long): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            val finalStatus = if (wasRinging) "missed" else "ended"
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to finalStatus,
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun pushCallMediaFrame(
        callId: String,
        isCaller: Boolean,
        videoFrameBase64: String? = null,
        audioChunkBase64: String? = null,
        audioSeq: Long? = null
    ) {
        try {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isCaller) {
                if (videoFrameBase64 != null) updates["callerVideoFrame"] = videoFrameBase64
                if (audioChunkBase64 != null) updates["callerAudioChunk"] = audioChunkBase64
                if (audioSeq != null) updates["callerAudioSeq"] = audioSeq
            } else {
                if (videoFrameBase64 != null) updates["calleeVideoFrame"] = videoFrameBase64
                if (audioChunkBase64 != null) updates["calleeAudioChunk"] = audioChunkBase64
                if (audioSeq != null) updates["calleeAudioSeq"] = audioSeq
            }
            if (updates.size > 1) {
                db.collection("calls").document(callId).update(updates).await()
            }
        } catch (_: Exception) {
            // Transient frame drop during call teardown is harmless
        }
    }

    suspend fun publishWebRtcOffer(callId: String, offerSdp: String) {
        try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "offerSdp" to offerSdp.take(40000),
                    "webrtcEnabled" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Fallback media relay remains active
        }
    }

    suspend fun publishWebRtcAnswer(callId: String, answerSdp: String) {
        try {
            requireUserId()
            db.collection("calls").document(callId).update(
                mapOf(
                    "answerSdp" to answerSdp.take(40000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Fallback media relay remains active
        }
    }

    suspend fun sendIceCandidate(
        callId: String,
        memberUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        try {
            val uid = requireUserId()
            db.collection("calls").document(callId)
                .collection("iceCandidates").document()
                .set(
                    mapOf(
                        "senderId" to uid,
                        "memberUids" to memberUids,
                        "sdpMid" to sdpMid.take(64),
                        "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 32),
                        "candidate" to candidate.take(2048),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (_: Exception) {
            // Ignore transient ICE write errors
        }
    }

    fun observeIceCandidates(callId: String, uid: String): Flow<List<IceCandidateSignal>> = flow {
        val path = "calls/$callId/iceCandidates"
        emitAll(
            db.collection("calls").document(callId).collection("iceCandidates")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(IceCandidateSignal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    emit(emptyList())
                }
        )
    }

    // --- 6. LINKORA AI CONVERSATION HISTORY ---

    fun observeAiMessages(uid: String): Flow<List<AiChatMessage>> = flow {
        val path = "users/$uid/aiMessages"
        emitAll(
            db.collection("users").document(uid).collection("aiMessages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AiChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun saveAiMessage(role: String, text: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/aiMessages"
            val trimmed = text.trim().take(12000)
            if (trimmed.isBlank()) return Result.success(Unit)
            db.collection("users").document(uid).collection("aiMessages")
                .document()
                .set(
                    mapOf(
                        "role" to if (role == "model") "model" else "user",
                        "text" to trimmed,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAiHistory(): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid/aiMessages"
            val snap = db.collection("users").document(uid).collection("aiMessages").get().await()
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
