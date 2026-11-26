package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class HomeTab {
    CHATS,
    STORIES,
    PEOPLE,
    CALLS,
    SETTINGS
}

enum class SecondaryPage {
    NONE,
    AI_CHAT,
    NOTIFICATIONS,
    LOCKED_CHATS,
    ARCHIVED_CHATS,
    BLOCKED_USERS,
    SUPPORT,
    HELP
}

enum class DisappearingOption(val label: String, val shortLabel: String, val durationMs: Long) {
    OFF("Off (Keep messages)", "Off", 0L),
    HOURS_24("24 Hours", "24h", 24L * 60L * 60L * 1000L),
    HOURS_44("44 Hours", "44h", 44L * 60L * 60L * 1000L),
    DAYS_7("7 Days", "7d", 7L * 24L * 60L * 60L * 1000L),
    MONTH_1("1 Month (30 Days)", "1m", 30L * 24L * 60L * 60L * 1000L);

    companion object {
        fun fromDurationMs(ms: Long): DisappearingOption {
            return entries.firstOrNull { it.durationMs == ms } ?: OFF
        }
    }
}

enum class ChatWallpaperStyle(val key: String, val label: String, val description: String) {
    CLASSIC_GLASS("classic_glass", "Classic Light", "Clean white & light slate background"),
    MIDNIGHT_MIST("midnight_mist", "Soft Blue", "Subtle cool sky & cobalt tint"),
    AURORA_SILK("aurora_silk", "Fresh Mint", "Soft fresh mint background"),
    OBSIDIAN_VELVET("obsidian_velvet", "Pure White", "Ultra-clean minimal flat canvas");

    companion object {
        fun fromKey(key: String): ChatWallpaperStyle {
            return entries.firstOrNull { it.key == key } ?: CLASSIC_GLASS
        }
    }
}

enum class CallQualityMode(val key: String, val label: String, val videoWidth: Int, val videoHeight: Int, val fps: Int) {
    HD("hd", "HD 720p", 1280, 720, 30),
    BALANCED("balanced", "Balanced 480p", 640, 480, 24),
    SAVER("saver", "Data Saver 360p", 480, 360, 15);

    companion object {
        fun fromKey(key: String): CallQualityMode {
            return entries.firstOrNull { it.key == key } ?: BALANCED
        }
    }
}

enum class FontScaleOption(val key: String, val label: String, val scale: Float) {
    COMPACT("compact", "Compact", 0.92f),
    STANDARD("standard", "Standard", 1.0f),
    LARGE("large", "Large", 1.12f);

    companion object {
        fun fromKey(key: String): FontScaleOption {
            return entries.firstOrNull { it.key == key } ?: STANDARD
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val username: String = "",
    val email: String? = null,
    val bio: String = "",
    val avatarData: String? = null,
    val online: Boolean = false,
    val discoverable: Boolean = true,
    val profileCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val defaultDisappearingMs: Long = 0L,
    val friendUids: List<String> = emptyList(),
    val blockedUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val username: String = "",
    val bio: String = "",
    val avatarData: String? = null,
    val online: Boolean = false,
    val discoverable: Boolean = true,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendRequest(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val memberUids: List<String> = emptyList(),
    val fromName: String = "",
    val fromUsername: String = "",
    val fromAvatar: String? = null,
    val toName: String = "",
    val toUsername: String = "",
    val toAvatar: String? = null,
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val isGroup: Boolean = false,
    val groupName: String = "",
    val memberUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val memberUsernames: Map<String, String> = emptyMap(),
    val memberAvatars: Map<String, String> = emptyMap(),
    val typingUsers: Map<String, Boolean> = emptyMap(),
    val archivedBy: Map<String, Boolean> = emptyMap(),
    val lockedBy: Map<String, Boolean> = emptyMap(),
    val hiddenBy: Map<String, Boolean> = emptyMap(),
    val clearedAtMillis: Map<String, Long> = emptyMap(),
    val disappearingDurationMs: Long = 0L,
    val lastMessageText: String = "",
    val lastMessageType: String = "text",
    val lastMessageSenderId: String = "",
    val lastMessageStatus: String = "sent",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isArchivedFor(uid: String): Boolean = archivedBy[uid] == true
    fun isLockedFor(uid: String): Boolean = lockedBy[uid] == true
    fun isHiddenFor(uid: String): Boolean = hiddenBy[uid] == true
    fun clearedBeforeFor(uid: String): Long = clearedAtMillis[uid] ?: 0L
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val memberUids: List<String> = emptyList(),
    val messageType: String = "text", // text, voice, photo, video, file, document, location
    val text: String = "",
    val audioData: String? = null,
    val audioDurationSec: Long = 0L,
    val mediaData: String? = null,
    val fileData: String? = null,
    val fileName: String? = null,
    val fileMimeType: String? = null,
    val fileSizeBytes: Long = 0L,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationLabel: String? = null,
    val deletedForUids: List<String> = emptyList(),
    val deletedForEveryone: Boolean = false,
    val status: String = "sent",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoryItem(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorAvatar: String? = null,
    val storyType: String = "text",
    val caption: String = "",
    val mediaData: String? = null,
    val musicTitle: String? = null,
    val musicArtist: String? = null,
    val backgroundHex: String = "#4338CA",
    val visibility: String = "friends",
    val allowedViewerUids: List<String> = emptyList(),
    val expiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val calleeId: String = "",
    val memberUids: List<String> = emptyList(),
    val callerName: String = "",
    val callerAvatar: String? = null,
    val calleeName: String = "",
    val calleeAvatar: String? = null,
    val callType: String = "audio",
    val status: String = "ringing",
    val durationSeconds: Long = 0L,
    val webrtcEnabled: Boolean = true,
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val callerVideoFrame: String? = null,
    val calleeVideoFrame: String? = null,
    val callerAudioChunk: String? = null,
    val calleeAudioChunk: String? = null,
    val callerAudioSeq: Long = 0L,
    val calleeAudioSeq: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class IceCandidateSignal(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val memberUids: List<String> = emptyList(),
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val candidate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AiChatMessage(
    @DocumentId val id: String = "",
    val role: String = "user",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class FullScreenAvatarPayload(
    val displayName: String,
    val username: String,
    val avatarData: String?
)

data class LinkoraNotification(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String, // "request", "message", "missed_call"
    val timestampMs: Long
)
