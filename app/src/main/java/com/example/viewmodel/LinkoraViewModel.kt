package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppNotificationState
import com.example.core.AttachmentCodec
import com.example.core.LinkoraMessageService
import com.example.core.LocalAccountStore
import com.example.core.LocalRegisteredUser
import com.example.core.NotificationHelper
import com.example.core.PickedFileAttachment
import com.example.core.PreferencesManager
import com.example.core.ResolvedLocation
import com.example.data.model.CallQualityMode
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.ChatWallpaperStyle
import com.example.data.model.DisappearingOption
import com.example.data.model.FontScaleOption
import com.example.data.model.FriendRequest
import com.example.data.model.FullScreenAvatarPayload
import com.example.data.model.IceCandidateSignal
import com.example.data.model.LinkoraNotification
import com.example.data.model.PublicProfile
import com.example.data.model.StoryItem
import com.example.data.model.UiState
import com.example.data.model.UserAccount
import com.example.data.repository.LinkoraRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

private const val VM_TAG = "LinkoraVM"
private const val STOP_TIMEOUT_MS = 5000L
private const val STORY_TTL_MS = 24L * 60L * 60L * 1000L

sealed interface UsernameCheckState {
    data object Idle : UsernameCheckState
    data object Checking : UsernameCheckState
    data class Available(val username: String) : UsernameCheckState
    data class Taken(val username: String) : UsernameCheckState
    data class Invalid(val reason: String) : UsernameCheckState
}

data class InAppBannerAlert(
    val id: String,
    val chatId: String?,
    val senderName: String,
    val previewText: String,
    val avatarData: String? = null,
    val type: String = "message"
)

/**
 * Unified ViewModel supporting:
 * 1) Cloud Sync Mode (`repository != null`): Real-time Firebase Firestore streams & writes gated behind Google Sign-In.
 *    Always starts clean with ZERO fake chats; registered accounts remain discoverable even offline.
 * 2) Local Account Mode (`repository == null && !isDeveloperDemo`): Clean real account with ZERO fake chats.
 *    Discovers other locally registered accounts permanently even when offline.
 * 3) Developer Demo Mode (`repository == null && isDeveloperDemo`): Seeded interactive sandbox ONLY for demo testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkoraViewModel(
    private val repository: LinkoraRepository?,
    val currentUserId: String,
    private val appContext: Context,
    private val localUser: LocalRegisteredUser? = null,
    val isDeveloperDemo: Boolean = false
) : ViewModel() {

    val isCloudMode: Boolean = repository != null
    private val localStore = LocalAccountStore(appContext)
    private val preferencesManager = PreferencesManager(appContext)

    // --- Local/Demo reactive stores (unused in Cloud mode; fake data seeded ONLY in Developer Demo) ---
    private val localAccountFlow = MutableStateFlow<UserAccount?>(buildInitialLocalAccount())
    private val localProfilesFlow = MutableStateFlow<List<PublicProfile>>(seedLocalOrDemoProfiles())
    private val localFriendRequestsFlow = MutableStateFlow<List<FriendRequest>>(seedDemoRequestsOnly())
    private val localChatsFlow = MutableStateFlow<List<ChatThread>>(seedDemoChatsOnly())
    private val localMessagesByChat = MutableStateFlow<Map<String, List<ChatMessage>>>(seedDemoMessagesOnly())
    private val localStoriesFlow = MutableStateFlow<List<StoryItem>>(seedDemoStoriesOnly())
    private val localCallsFlow = MutableStateFlow<List<CallSession>>(seedDemoCallsOnly())

    // --- App Preferences (Chat Lock, Disappearing, Appearance, Privacy, Calling) ---
    val hasChatLockPin: StateFlow<Boolean> = preferencesManager.hasChatLockPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val chatLockHint: StateFlow<String> = preferencesManager.chatLockHint
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    val globalDisappearingMs: StateFlow<Long> = preferencesManager.globalDisappearingMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0L)

    val soundEnabled: StateFlow<Boolean> = preferencesManager.soundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    val vibrateEnabled: StateFlow<Boolean> = preferencesManager.vibrateEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    val readReceiptsEnabled: StateFlow<Boolean> = preferencesManager.readReceiptsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    val onlinePresenceVisible: StateFlow<Boolean> = preferencesManager.onlinePresenceVisible
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    val enterKeySends: StateFlow<Boolean> = preferencesManager.enterKeySends
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val lowDataCalls: StateFlow<Boolean> = preferencesManager.lowDataCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val callQualityMode: StateFlow<CallQualityMode> = preferencesManager.callQualityMode
        .map { CallQualityMode.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CallQualityMode.BALANCED)

    val chatWallpaper: StateFlow<ChatWallpaperStyle> = preferencesManager.chatWallpaper
        .map { ChatWallpaperStyle.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatWallpaperStyle.CLASSIC_GLASS)

    val fontScaleOption: StateFlow<FontScaleOption> = preferencesManager.fontScale
        .map { FontScaleOption.fromKey(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FontScaleOption.STANDARD)

    // --- Unified Public StateFlows ---
    val userAccountState: StateFlow<UiState<UserAccount?>> = if (repository != null) {
        repository.observeUserAccount(currentUserId)
            .map<UserAccount?, UiState<UserAccount?>> { UiState.Success(it) }
            .catch { error -> emit(unwrapFlowError("profile", error)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)
    } else {
        localAccountFlow
            .map<UserAccount?, UiState<UserAccount?>> { UiState.Success(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Success(localAccountFlow.value))
    }

    val discoverableProfiles: StateFlow<List<PublicProfile>> = if (repository != null) {
        repository.observeDiscoverableProfiles(currentUserId)
            .catch { error ->
                unwrapFlowError("publicProfiles", error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    } else {
        localProfilesFlow.asStateFlow()
    }

    val friendRequests: StateFlow<List<FriendRequest>> = if (repository != null) {
        repository.observeFriendRequests(currentUserId)
            .catch { error ->
                unwrapFlowError("friendRequests", error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    } else {
        localFriendRequestsFlow.asStateFlow()
    }

    val chats: StateFlow<List<ChatThread>> = if (repository != null) {
        repository.observeChats(currentUserId)
            .catch { error ->
                unwrapFlowError("chats", error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    } else {
        localChatsFlow.asStateFlow()
    }

    val stories: StateFlow<List<StoryItem>> = if (repository != null) {
        repository.observeActiveStories(currentUserId)
            .catch { error ->
                unwrapFlowError("stories", error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    } else {
        localStoriesFlow
            .map { list ->
                val now = System.currentTimeMillis()
                list.filter { it.expiresAtMillis > now }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), localStoriesFlow.value)
    }

    val calls: StateFlow<List<CallSession>> = if (repository != null) {
        repository.observeCalls(currentUserId)
            .catch { error ->
                unwrapFlowError("calls", error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    } else {
        localCallsFlow.asStateFlow()
    }

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val rawSelectedChatMessages: StateFlow<List<ChatMessage>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(emptyList())
            } else if (repository != null) {
                repository.observeMessages(chatId, currentUserId)
                    .catch { error ->
                        unwrapFlowError("messages", error)
                        emit(emptyList())
                    }
            } else {
                localMessagesByChat.map { map -> map[chatId].orEmpty() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // Automatically enforces cleared history timestamps and active disappearing message timers!
    val selectedChatMessages: StateFlow<List<ChatMessage>> = combine(
        rawSelectedChatMessages,
        chats,
        _selectedChatId,
        userAccountState,
        globalDisappearingMs
    ) { rawMsgs, chatList, chatId, accState, localGlobalMs ->
        if (chatId.isNullOrBlank()) return@combine emptyList()
        val chat = chatList.firstOrNull { it.id == chatId }
        val clearedBefore = chat?.clearedBeforeFor(currentUserId) ?: 0L
        val accountGlobalMs = (accState as? UiState.Success)?.data?.defaultDisappearingMs ?: 0L
        val fallbackGlobalMs = if (accountGlobalMs > 0L) accountGlobalMs else localGlobalMs
        val chatDuration = chat?.disappearingDurationMs ?: 0L
        val effectiveDisappearingMs = if (chatDuration > 0L) {
            chatDuration
        } else {
            fallbackGlobalMs
        }
        val now = System.currentTimeMillis()
        rawMsgs.filter { msg ->
            val notDeletedForMe = currentUserId !in msg.deletedForUids
            val msgTime = msg.createdAt?.toDate()?.time ?: now
            val passesClear = clearedBefore == 0L || msgTime > clearedBefore
            val passesDisappear = effectiveDisappearingMs == 0L || (now - msgTime) <= effectiveDisappearingMs
            notDeletedForMe && passesClear && passesDisappear
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val activeCall: StateFlow<CallSession?> = calls.map { list ->
        list.firstOrNull { call ->
            call.status == "connected" || (call.status == "ringing" && call.callerId == currentUserId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val incomingRingingCall: StateFlow<CallSession?> = combine(calls, userAccountState) { list, accState ->
        val blocked = (accState as? UiState.Success)?.data?.blockedUids?.toSet().orEmpty()
        list.firstOrNull { call ->
            call.status == "ringing" && call.calleeId == currentUserId && call.callerId !in blocked
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // Archived & Locked chats are strictly muted from notifications!
    val notifications: StateFlow<List<LinkoraNotification>> = combine(
        friendRequests,
        chats,
        calls
    ) { reqs, chatList, callList ->
        val items = mutableListOf<LinkoraNotification>()
        reqs.filter { it.toUid == currentUserId && it.status == "pending" }.forEach { req ->
            items.add(
                LinkoraNotification(
                    id = "req_${req.id}",
                    title = "${req.fromName} sent a friend request",
                    subtitle = "@${req.fromUsername} wants to connect on Linkora",
                    type = "request",
                    timestampMs = req.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            )
        }
        chatList.filter { chat ->
            !chat.isArchivedFor(currentUserId) &&
                !chat.isLockedFor(currentUserId) &&
                !chat.isHiddenFor(currentUserId) &&
                chat.lastMessageSenderId.isNotBlank() &&
                chat.lastMessageSenderId != currentUserId &&
                chat.lastMessageStatus != "seen"
        }.forEach { chat ->
            val senderName = if (chat.isGroup) {
                "${chat.memberNames[chat.lastMessageSenderId] ?: "Member"} • ${chat.groupName}"
            } else {
                chat.memberNames[chat.lastMessageSenderId] ?: "Friend"
            }
            items.add(
                LinkoraNotification(
                    id = "chat_${chat.id}_${chat.lastMessageAt?.seconds ?: 0}",
                    title = "New message from $senderName",
                    subtitle = chat.lastMessageText,
                    type = "message",
                    timestampMs = chat.lastMessageAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            )
        }
        callList.filter {
            it.calleeId == currentUserId && it.status == "missed"
        }.take(10).forEach { call ->
            items.add(
                LinkoraNotification(
                    id = "call_${call.id}",
                    title = "Missed ${call.callType} call",
                    subtitle = "From ${call.callerName}",
                    type = "missed_call",
                    timestampMs = call.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            )
        }
        items.sortedByDescending { it.timestampMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _usernameCheckState = MutableStateFlow<UsernameCheckState>(UsernameCheckState.Idle)
    val usernameCheckState: StateFlow<UsernameCheckState> = _usernameCheckState.asStateFlow()

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _fullScreenAvatar = MutableStateFlow<FullScreenAvatarPayload?>(null)
    val fullScreenAvatar: StateFlow<FullScreenAvatarPayload?> = _fullScreenAvatar.asStateFlow()

    private val _inAppBanner = MutableStateFlow<InAppBannerAlert?>(null)
    val inAppBanner: StateFlow<InAppBannerAlert?> = _inAppBanner.asStateFlow()

    private var usernameCheckJob: Job? = null
    private var typingIdleJob: Job? = null
    private var lastSentTypingChatId: String? = null
    private var lastSentTypingValue: Boolean = false

    private val notifiedChatTimestamps = mutableMapOf<String, Long>()
    private val notifiedCallIds = mutableSetOf<String>()
    private val notifiedRequestIds = mutableSetOf<String>()
    private var initialSnapshotsPrimed = false

    init {
        if (repository != null) {
            viewModelScope.launch {
                repository.purgeExpiredOwnStories(currentUserId)
            }
            viewModelScope.launch {
                combine(userAccountState, friendRequests) { accState, reqs ->
                    val account = (accState as? UiState.Success)?.data ?: return@combine emptyList<String>()
                    val existing = account.friendUids.toSet()
                    reqs.filter { it.status == "accepted" }
                        .mapNotNull { req ->
                            val other = if (req.fromUid == currentUserId) req.toUid else req.fromUid
                            other.takeIf { it !in existing }
                        }
                        .distinct()
                }.collect { missing ->
                    if (missing.isNotEmpty()) {
                        repository.syncAcceptedFriendsIntoMyList(missing)
                    }
                }
            }
            viewModelScope.launch {
                selectedChatMessages.collect { messages ->
                    val openChatId = _selectedChatId.value ?: return@collect
                    if (!readReceiptsEnabled.value) return@collect
                    val unseen = messages.filter { it.senderId != currentUserId && it.status != "seen" }
                    if (unseen.isNotEmpty()) {
                        repository.markMessagesStatus(openChatId, unseen, "seen")
                    }
                }
            }
        }

        // Real-time in-app heads-up banners + sound + background/lock-screen notifications (muted for Archived, Locked & Blocked)
        viewModelScope.launch {
            delay(1600L)
            initialSnapshotsPrimed = true
        }
        viewModelScope.launch {
            chats.collect { list ->
                val account = (userAccountState.value as? UiState.Success)?.data
                val notificationsAllowed = account?.notificationsEnabled ?: true
                val blockedSet = account?.blockedUids?.toSet().orEmpty()
                list.forEach { chat ->
                    val ts = chat.lastMessageAt?.toDate()?.time ?: 0L
                    val prev = notifiedChatTimestamps[chat.id]
                    notifiedChatTimestamps[chat.id] = ts
                    val trackerKey = "chat_${chat.id}"
                    if (!initialSnapshotsPrimed) {
                        AppNotificationState.primeTimestampIfUnset(appContext, trackerKey, ts)
                    }
                    val isMuted = chat.isArchivedFor(currentUserId) ||
                        chat.isLockedFor(currentUserId) ||
                        chat.lastMessageSenderId in blockedSet
                    val isNewIncoming = initialSnapshotsPrimed &&
                        notificationsAllowed &&
                        !isMuted &&
                        prev != null &&
                        ts > prev &&
                        chat.lastMessageSenderId.isNotBlank() &&
                        chat.lastMessageSenderId != currentUserId

                    if (isNewIncoming && AppNotificationState.shouldNotify(appContext, trackerKey, ts)) {
                        val sender = if (chat.isGroup) {
                            "${chat.memberNames[chat.lastMessageSenderId] ?: "Member"} • ${chat.groupName}"
                        } else {
                            chat.memberNames[chat.lastMessageSenderId] ?: "Linkora"
                        }
                        val avatar = chat.memberAvatars[chat.lastMessageSenderId]
                        val preview = chat.lastMessageText.ifBlank { "New message" }

                        if (AppNotificationState.isAppInForeground) {
                            // Always play crisp notification sound + vibration when a new message arrives
                            NotificationHelper.playInAppMessageAlert(
                                context = appContext,
                                soundEnabled = soundEnabled.value,
                                vibrateEnabled = vibrateEnabled.value
                            )
                            // Show floating frosted-glass top-of-screen banner when user is not already viewing this chat
                            if (_selectedChatId.value != chat.id) {
                                _inAppBanner.value = InAppBannerAlert(
                                    id = "${chat.id}_$ts",
                                    chatId = chat.id,
                                    senderName = sender,
                                    previewText = preview,
                                    avatarData = avatar,
                                    type = "message"
                                )
                            }
                        } else {
                            // App is backgrounded / screen off: fire real Android heads-up + lock-screen popup with sound
                            NotificationHelper.showMessageNotification(
                                context = appContext,
                                notificationId = chat.id.hashCode(),
                                title = sender,
                                body = preview,
                                chatId = chat.id,
                                soundEnabled = soundEnabled.value,
                                vibrateEnabled = vibrateEnabled.value,
                                wakeScreen = true
                            )
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            incomingRingingCall.collect { call ->
                val notificationsAllowed = ((userAccountState.value as? UiState.Success)?.data?.notificationsEnabled) ?: true
                if (call != null && call.id !in notifiedCallIds) {
                    notifiedCallIds.add(call.id)
                    val ts = call.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    if (notificationsAllowed && AppNotificationState.shouldNotify(appContext, "call_${call.id}", ts)) {
                        NotificationHelper.showIncomingCallNotification(
                            context = appContext,
                            notificationId = call.id.hashCode(),
                            callerName = call.callerName,
                            isVideo = call.callType == "video"
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            friendRequests.collect { reqs ->
                val account = (userAccountState.value as? UiState.Success)?.data
                val notificationsAllowed = account?.notificationsEnabled ?: true
                val blockedSet = account?.blockedUids?.toSet().orEmpty()
                reqs.filter {
                    it.toUid == currentUserId &&
                        it.status == "pending" &&
                        it.fromUid !in blockedSet
                }.forEach { req ->
                    val ts = req.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    val trackerKey = "req_${req.id}"
                    if (req.id !in notifiedRequestIds) {
                        notifiedRequestIds.add(req.id)
                        if (!initialSnapshotsPrimed) {
                            AppNotificationState.primeTimestampIfUnset(appContext, trackerKey, ts)
                        } else if (notificationsAllowed && AppNotificationState.shouldNotify(appContext, trackerKey, ts)) {
                            val subtitle = "${req.fromName} (@${req.fromUsername}) wants to connect"
                            if (AppNotificationState.isAppInForeground) {
                                NotificationHelper.playInAppMessageAlert(
                                    context = appContext,
                                    soundEnabled = soundEnabled.value,
                                    vibrateEnabled = vibrateEnabled.value
                                )
                                _inAppBanner.value = InAppBannerAlert(
                                    id = "req_${req.id}",
                                    chatId = null,
                                    senderName = "Friend Request",
                                    previewText = subtitle,
                                    avatarData = req.fromAvatar,
                                    type = "request"
                                )
                            } else {
                                NotificationHelper.showMessageNotification(
                                    context = appContext,
                                    notificationId = req.id.hashCode(),
                                    title = "Friend Request",
                                    body = subtitle,
                                    chatId = null,
                                    soundEnabled = soundEnabled.value,
                                    vibrateEnabled = vibrateEnabled.value,
                                    wakeScreen = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun dismissInAppBanner() {
        _inAppBanner.value = null
    }

    fun sendTestMessageAlert() {
        val sampleChatId = chats.value.firstOrNull { !it.isHiddenFor(currentUserId) }?.id
        _inAppBanner.value = InAppBannerAlert(
            id = "test_${System.currentTimeMillis()}",
            chatId = sampleChatId,
            senderName = "Linkora Alert",
            previewText = "Sound, top-screen popup & lock-screen notifications are active!",
            avatarData = null,
            type = "message"
        )
        // Fire real Android heads-up notification (handles sound + vibrate + top bar / lock screen)
        if (NotificationHelper.canPostNotifications(appContext)) {
            NotificationHelper.showMessageNotification(
                context = appContext,
                notificationId = (System.currentTimeMillis() % 100000).toInt(),
                title = "Linkora Alert",
                body = "New message alerts with sound & top-screen popup are ready!",
                chatId = sampleChatId,
                soundEnabled = soundEnabled.value,
                vibrateEnabled = vibrateEnabled.value,
                wakeScreen = false
            )
        } else {
            NotificationHelper.playInAppMessageAlert(
                context = appContext,
                soundEnabled = soundEnabled.value,
                vibrateEnabled = vibrateEnabled.value
            )
            _snackbarMessage.value = "Allow Notification permission to show alerts when app is closed."
        }
    }

    // --- APP PREFERENCES SETTERS ---

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setSoundEnabled(enabled) }
    }

    fun setVibrateEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setVibrateEnabled(enabled) }
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setReadReceiptsEnabled(enabled) }
    }

    fun setOnlinePresenceVisible(visible: Boolean) {
        viewModelScope.launch {
            preferencesManager.setOnlinePresenceVisible(visible)
            setOnlinePresence(visible)
        }
    }

    fun setEnterKeySends(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setEnterKeySends(enabled) }
    }

    fun setLowDataCalls(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setLowDataCalls(enabled) }
    }

    fun setCallQualityMode(mode: CallQualityMode) {
        viewModelScope.launch { preferencesManager.setCallQualityMode(mode.key) }
    }

    fun setChatWallpaper(style: ChatWallpaperStyle) {
        viewModelScope.launch { preferencesManager.setChatWallpaper(style.key) }
    }

    fun setFontScaleOption(option: FontScaleOption) {
        viewModelScope.launch { preferencesManager.setFontScale(option.key) }
    }

    // --- BLOCK / UNBLOCK USER ---

    fun blockUser(targetUid: String, displayName: String) {
        if (targetUid.isBlank() || targetUid == currentUserId) return
        if (repository == null) {
            localAccountFlow.update { acc ->
                acc?.copy(blockedUids = (acc.blockedUids + targetUid).distinct())
            }
            _snackbarMessage.value = "$displayName blocked"
            return
        }
        viewModelScope.launch {
            val res = repository.blockUser(targetUid)
            res.onSuccess {
                _snackbarMessage.value = "$displayName blocked"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not block user"
            }
        }
    }

    fun unblockUser(targetUid: String, displayName: String) {
        if (targetUid.isBlank()) return
        if (repository == null) {
            localAccountFlow.update { acc ->
                acc?.copy(blockedUids = acc.blockedUids.filterNot { it == targetUid })
            }
            _snackbarMessage.value = "$displayName unblocked"
            return
        }
        viewModelScope.launch {
            val res = repository.unblockUser(targetUid)
            res.onSuccess {
                _snackbarMessage.value = "$displayName unblocked"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not unblock user"
            }
        }
    }

    // --- INDIVIDUAL MESSAGE DELETION (DELETE FOR ME / FOR EVERYONE / PERMANENTLY) ---

    fun deleteMessageForMe(chatId: String, message: ChatMessage) {
        if (repository == null) {
            localMessagesByChat.update { map ->
                val updated = map[chatId].orEmpty().map { m ->
                    if (m.id == message.id) {
                        m.copy(deletedForUids = (m.deletedForUids + currentUserId).distinct())
                    } else m
                }
                map + (chatId to updated)
            }
            _snackbarMessage.value = "Message deleted for you"
            return
        }
        viewModelScope.launch {
            val res = repository.deleteMessageForMe(chatId, message.id)
            res.onSuccess {
                _snackbarMessage.value = "Message deleted for you"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not delete message"
            }
        }
    }

    fun deleteMessageForEveryone(chatId: String, message: ChatMessage) {
        if (message.senderId != currentUserId) {
            _snackbarMessage.value = "You can only delete your own messages for everyone"
            return
        }
        val currentMsgs = selectedChatMessages.value
        val isLatest = currentMsgs.lastOrNull()?.id == message.id
        if (repository == null) {
            localMessagesByChat.update { map ->
                val updated = map[chatId].orEmpty().map { m ->
                    if (m.id == message.id) {
                        m.copy(
                            deletedForEveryone = true,
                            text = "This message was deleted",
                            messageType = "text",
                            audioData = null,
                            audioDurationSec = 0L,
                            mediaData = null,
                            fileData = null,
                            fileName = null,
                            fileMimeType = null,
                            fileSizeBytes = 0L,
                            locationLat = null,
                            locationLng = null,
                            locationLabel = null
                        )
                    } else m
                }
                map + (chatId to updated)
            }
            if (isLatest) {
                localChatsFlow.update { list ->
                    list.map { c ->
                        if (c.id == chatId) {
                            c.copy(lastMessageText = "This message was deleted", lastMessageType = "text")
                        } else c
                    }
                }
            }
            _snackbarMessage.value = "Message deleted for everyone"
            return
        }
        viewModelScope.launch {
            val res = repository.deleteMessageForEveryone(chatId, message.id, isLatest)
            res.onSuccess {
                _snackbarMessage.value = "Message deleted for everyone"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not delete for everyone"
            }
        }
    }

    fun deleteMessagePermanently(chatId: String, message: ChatMessage) {
        if (message.senderId != currentUserId) {
            // For received messages, remove permanently from this user's view
            deleteMessageForMe(chatId, message)
            return
        }
        val currentMsgs = selectedChatMessages.value
        val isLatest = currentMsgs.lastOrNull()?.id == message.id
        val replacementPreview = currentMsgs.filterNot { it.id == message.id }.lastOrNull()
        if (repository == null) {
            localMessagesByChat.update { map ->
                val remaining = map[chatId].orEmpty().filterNot { it.id == message.id }
                map + (chatId to remaining)
            }
            if (isLatest) {
                localChatsFlow.update { list ->
                    list.map { c ->
                        if (c.id == chatId) {
                            c.copy(
                                lastMessageText = replacementPreview?.text ?: "No messages yet",
                                lastMessageType = replacementPreview?.messageType ?: "text"
                            )
                        } else c
                    }
                }
            }
            _snackbarMessage.value = "Message permanently deleted"
            return
        }
        viewModelScope.launch {
            val res = repository.deleteMessagePermanently(chatId, message.id, isLatest, replacementPreview)
            res.onSuccess {
                _snackbarMessage.value = "Message permanently deleted"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not permanently delete message"
            }
        }
    }

    // --- CHAT LOCK PIN ---

    suspend fun verifyChatLockPin(pin: String): Boolean = preferencesManager.verifyChatLockPin(pin)

    fun saveChatLockPin(pin: String, hint: String) {
        viewModelScope.launch {
            preferencesManager.setChatLockPin(pin, hint)
            _snackbarMessage.value = "Chat Lock PIN saved"
        }
    }

    fun resetChatLockPin() {
        viewModelScope.launch {
            preferencesManager.resetChatLockPin()
            _snackbarMessage.value = "Chat Lock PIN reset. You can set a new PIN anytime."
        }
    }

    // --- CHAT MANAGEMENT (ARCHIVE / LOCK / DELETE / RESTORE / DISAPPEARING / GROUPS) ---

    fun setChatArchived(chat: ChatThread, archived: Boolean) {
        if (repository == null) {
            localChatsFlow.update { list ->
                list.map { c ->
                    if (c.id == chat.id) {
                        val arch = c.archivedBy.toMutableMap().apply { put(currentUserId, archived) }
                        val lock = if (archived) c.lockedBy.toMutableMap().apply { put(currentUserId, false) } else c.lockedBy
                        val hid = if (archived) c.hiddenBy.toMutableMap().apply { put(currentUserId, false) } else c.hiddenBy
                        c.copy(archivedBy = arch, lockedBy = lock, hiddenBy = hid)
                    } else c
                }
            }
            _snackbarMessage.value = if (archived) "Chat archived (notifications muted)" else "Chat unarchived"
            return
        }
        viewModelScope.launch {
            val res = repository.setChatArchived(chat.id, archived)
            res.onSuccess {
                _snackbarMessage.value = if (archived) "Chat archived (notifications muted)" else "Chat unarchived"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update archive"
            }
        }
    }

    fun setChatLocked(chat: ChatThread, locked: Boolean) {
        if (repository == null) {
            localChatsFlow.update { list ->
                list.map { c ->
                    if (c.id == chat.id) {
                        val lock = c.lockedBy.toMutableMap().apply { put(currentUserId, locked) }
                        val arch = if (locked) c.archivedBy.toMutableMap().apply { put(currentUserId, false) } else c.archivedBy
                        val hid = if (locked) c.hiddenBy.toMutableMap().apply { put(currentUserId, false) } else c.hiddenBy
                        c.copy(lockedBy = lock, archivedBy = arch, hiddenBy = hid)
                    } else c
                }
            }
            _snackbarMessage.value = if (locked) "Moved to Locked Chats" else "Removed from Locked Chats"
            return
        }
        viewModelScope.launch {
            val res = repository.setChatLocked(chat.id, locked)
            res.onSuccess {
                _snackbarMessage.value = if (locked) "Moved to Locked Chats" else "Removed from Locked Chats"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update chat lock"
            }
        }
    }

    /**
     * Deletes chat from the home screen & clears previous messages WITHOUT removing the friend.
     * Searching the friend's name/username brings the chat right back with zero re-add needed.
     */
    fun deleteChatForMe(chat: ChatThread) {
        if (_selectedChatId.value == chat.id) {
            _selectedChatId.value = null
        }
        val now = System.currentTimeMillis()
        if (repository == null) {
            localChatsFlow.update { list ->
                list.map { c ->
                    if (c.id == chat.id) {
                        val hid = c.hiddenBy.toMutableMap().apply { put(currentUserId, true) }
                        val arch = c.archivedBy.toMutableMap().apply { put(currentUserId, false) }
                        val lock = c.lockedBy.toMutableMap().apply { put(currentUserId, false) }
                        val clr = c.clearedAtMillis.toMutableMap().apply { put(currentUserId, now) }
                        c.copy(hiddenBy = hid, archivedBy = arch, lockedBy = lock, clearedAtMillis = clr)
                    } else c
                }
            }
            _snackbarMessage.value = "Chat deleted. Search friend anytime to chat again."
            return
        }
        viewModelScope.launch {
            val res = repository.deleteChatForMe(chat.id)
            res.onSuccess {
                _snackbarMessage.value = "Chat deleted. Search friend anytime to chat again."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not delete chat"
            }
        }
    }

    fun restoreAndOpenChat(chatId: String) {
        if (repository == null) {
            localChatsFlow.update { list ->
                list.map { c ->
                    if (c.id == chatId) {
                        val hid = c.hiddenBy.toMutableMap().apply { put(currentUserId, false) }
                        c.copy(hiddenBy = hid)
                    } else c
                }
            }
            openChat(chatId)
            return
        }
        viewModelScope.launch {
            repository.restoreHiddenChat(chatId)
            openChat(chatId)
        }
    }

    fun setChatDisappearing(chat: ChatThread, option: DisappearingOption) {
        if (repository == null) {
            localChatsFlow.update { list ->
                list.map { c ->
                    if (c.id == chat.id) c.copy(disappearingDurationMs = option.durationMs) else c
                }
            }
            _snackbarMessage.value = if (option == DisappearingOption.OFF) {
                "Disappearing messages turned off"
            } else {
                "Disappearing messages set to ${option.label}"
            }
            return
        }
        viewModelScope.launch {
            val res = repository.setChatDisappearingTimer(chat.id, option.durationMs)
            res.onSuccess {
                _snackbarMessage.value = if (option == DisappearingOption.OFF) {
                    "Disappearing messages turned off"
                } else {
                    "Disappearing messages set to ${option.label}"
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not update timer"
            }
        }
    }

    fun setGlobalDisappearing(option: DisappearingOption) {
        viewModelScope.launch {
            preferencesManager.setGlobalDisappearingMs(option.durationMs)
            if (repository == null) {
                localAccountFlow.update { it?.copy(defaultDisappearingMs = option.durationMs) }
                _snackbarMessage.value = "All chats disappearing timer: ${option.label}"
            } else {
                val res = repository.setGlobalDisappearingTimer(option.durationMs)
                res.onSuccess {
                    _snackbarMessage.value = "All chats disappearing timer: ${option.label}"
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Could not save global timer"
                }
            }
        }
    }

    fun removeFriend(friendUid: String, friendName: String) {
        val chatId = if (currentUserId < friendUid) "${currentUserId}_$friendUid" else "${friendUid}_$currentUserId"
        if (_selectedChatId.value == chatId) {
            _selectedChatId.value = null
        }
        if (repository == null) {
            localAccountFlow.update { acc ->
                acc?.copy(friendUids = acc.friendUids.filterNot { it == friendUid })
            }
            localFriendRequestsFlow.update { list ->
                list.filterNot {
                    (it.fromUid == currentUserId && it.toUid == friendUid) ||
                        (it.fromUid == friendUid && it.toUid == currentUserId)
                }
            }
            localChatsFlow.update { list ->
                list.map { c ->
                    if (c.id == chatId) {
                        val hid = c.hiddenBy.toMutableMap().apply { put(currentUserId, true) }
                        val clr = c.clearedAtMillis.toMutableMap().apply { put(currentUserId, System.currentTimeMillis()) }
                        c.copy(hiddenBy = hid, clearedAtMillis = clr)
                    } else c
                }
            }
            _snackbarMessage.value = "$friendName removed from friends"
            return
        }
        viewModelScope.launch {
            val res = repository.removeFriend(friendUid)
            res.onSuccess {
                _snackbarMessage.value = "$friendName removed from friends"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not remove friend"
            }
        }
    }

    fun createGroupChat(groupName: String, members: List<PublicProfile>, onCreated: (String) -> Unit) {
        val me = (userAccountState.value as? UiState.Success)?.data ?: return
        val cleanName = groupName.trim().take(80)
        if (cleanName.isBlank()) {
            _snackbarMessage.value = "Please enter a group name"
            return
        }
        if (members.isEmpty()) {
            _snackbarMessage.value = "Select at least 1 friend"
            return
        }
        if (repository == null) {
            val chatId = "grp_${UUID.randomUUID().toString().take(12)}"
            val memberUids = (listOf(currentUserId) + members.map { it.userId }).distinct()
            val names = mutableMapOf(currentUserId to me.fullName)
            val usernames = mutableMapOf(currentUserId to me.username)
            val avatars = mutableMapOf<String, String>()
            me.avatarData?.let { avatars[currentUserId] = it }
            members.forEach { p ->
                names[p.userId] = p.fullName
                usernames[p.userId] = p.username
                p.avatarData?.let { avatars[p.userId] = it }
            }
            val newGroup = ChatThread(
                id = chatId,
                isGroup = true,
                groupName = cleanName,
                memberUids = memberUids,
                memberNames = names,
                memberUsernames = usernames,
                memberAvatars = avatars,
                disappearingDurationMs = me.defaultDisappearingMs,
                lastMessageText = "${me.fullName} created group \"$cleanName\"",
                lastMessageType = "text",
                lastMessageSenderId = currentUserId,
                lastMessageStatus = "seen",
                lastMessageAt = Timestamp.now(),
                createdAt = Timestamp.now()
            )
            localChatsFlow.update { listOf(newGroup) + it }
            _snackbarMessage.value = "Group \"$cleanName\" created!"
            onCreated(chatId)
            return
        }
        viewModelScope.launch {
            val res = repository.createGroupChat(me, cleanName, members)
            res.onSuccess { chatId ->
                _snackbarMessage.value = "Group \"$cleanName\" created!"
                onCreated(chatId)
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to create group"
            }
        }
    }

    fun openChat(chatId: String?) {
        val prev = _selectedChatId.value
        if (prev != null && prev != chatId) {
            setTyping(prev, false)
        }
        _selectedChatId.value = chatId
        AppNotificationState.activeChatId = chatId
        if (chatId != null && _inAppBanner.value?.chatId == chatId) {
            _inAppBanner.value = null
        }
        if (chatId != null && repository == null) {
            localMessagesByChat.update { map ->
                val updated = map[chatId].orEmpty().map { m ->
                    if (m.senderId != currentUserId) m.copy(status = "seen") else m
                }
                map + (chatId to updated)
            }
            localChatsFlow.update { list ->
                list.map { c ->
                    if (c.id == chatId && c.lastMessageSenderId != currentUserId) {
                        c.copy(lastMessageStatus = "seen")
                    } else c
                }
            }
        }
    }

    fun setTyping(chatId: String, isTyping: Boolean) {
        typingIdleJob?.cancel()
        if (lastSentTypingChatId == chatId && lastSentTypingValue == isTyping && !isTyping) {
            return
        }
        lastSentTypingChatId = chatId
        lastSentTypingValue = isTyping

        if (repository != null) {
            viewModelScope.launch {
                repository.setTypingStatus(chatId, isTyping)
            }
        } else {
            updateLocalTyping(chatId, currentUserId, isTyping)
        }

        if (isTyping) {
            typingIdleJob = viewModelScope.launch {
                delay(3200L)
                lastSentTypingValue = false
                if (repository != null) {
                    repository.setTypingStatus(chatId, false)
                } else {
                    updateLocalTyping(chatId, currentUserId, false)
                }
            }
        }
    }

    private fun updateLocalTyping(chatId: String, uid: String, isTyping: Boolean) {
        localChatsFlow.update { list ->
            list.map { c ->
                if (c.id == chatId) {
                    val map = c.typingUsers.toMutableMap()
                    map[uid] = isTyping
                    c.copy(typingUsers = map)
                } else c
            }
        }
    }

    fun showFullScreenAvatar(displayName: String, username: String, avatarData: String?) {
        _fullScreenAvatar.value = FullScreenAvatarPayload(displayName, username, avatarData)
    }

    fun dismissFullScreenAvatar() {
        _fullScreenAvatar.value = null
    }

    fun showFeedback(message: String) {
        _snackbarMessage.value = message
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun setOnlinePresence(online: Boolean) {
        val effectiveOnline = online && onlinePresenceVisible.value
        if (repository != null) {
            viewModelScope.launch { repository.setOnlineStatus(effectiveOnline) }
        } else {
            localAccountFlow.update { it?.copy(online = effectiveOnline) }
        }
    }

    fun refreshLocalProfiles() {
        if (repository == null && !isDeveloperDemo) {
            localProfilesFlow.value = localStore.getAllPublicProfiles(excludeUid = currentUserId)
        }
    }

    fun checkUsernameLive(rawInput: String) {
        usernameCheckJob?.cancel()
        val sanitized = localStore.sanitizeUsername(rawInput)
        if (rawInput.isBlank()) {
            _usernameCheckState.value = UsernameCheckState.Idle
            return
        }
        if (sanitized.length < 3) {
            _usernameCheckState.value = UsernameCheckState.Invalid("At least 3 characters (a-z, 0-9, _)")
            return
        }
        if (sanitized.length > 30) {
            _usernameCheckState.value = UsernameCheckState.Invalid("Max 30 characters")
            return
        }
        if (repository == null) {
            val available = localStore.isUsernameAvailable(sanitized)
            _usernameCheckState.value = if (available) {
                UsernameCheckState.Available(sanitized)
            } else {
                UsernameCheckState.Taken(sanitized)
            }
            return
        }
        _usernameCheckState.value = UsernameCheckState.Checking
        usernameCheckJob = viewModelScope.launch {
            delay(350L)
            val result = repository.checkUsernameAvailable(sanitized)
            result.onSuccess { available ->
                _usernameCheckState.value = if (available) {
                    UsernameCheckState.Available(sanitized)
                } else {
                    UsernameCheckState.Taken(sanitized)
                }
            }.onFailure { err ->
                _usernameCheckState.value = UsernameCheckState.Invalid(err.localizedMessage ?: "Check failed")
            }
        }
    }

    fun saveInitialProfile(
        fullName: String,
        username: String,
        bio: String,
        avatarData: String?
    ) {
        if (repository == null) {
            val current = localAccountFlow.value ?: return
            val updated = current.copy(
                fullName = fullName.trim().ifBlank { current.fullName },
                username = username.ifBlank { current.username },
                bio = bio.trim().take(250),
                avatarData = avatarData ?: current.avatarData,
                discoverable = true,
                profileCompleted = true
            )
            localAccountFlow.value = updated
            if (!isDeveloperDemo) {
                localStore.updateProfile(
                    uid = currentUserId,
                    fullName = updated.fullName,
                    bio = updated.bio,
                    avatarData = updated.avatarData,
                    profileCompleted = true
                )
            }
            return
        }

        viewModelScope.launch {
            _isSavingProfile.value = true
            val result = repository.completeProfileSetup(fullName, username, bio, avatarData)
            _isSavingProfile.value = false
            result.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not save profile"
            }
        }
    }

    fun updateProfile(fullName: String, bio: String, avatarData: String?) {
        if (repository == null) {
            localAccountFlow.update { curr ->
                curr?.copy(
                    fullName = fullName.trim().ifBlank { curr.fullName },
                    bio = bio.trim().take(250),
                    avatarData = avatarData ?: curr.avatarData
                )
            }
            if (!isDeveloperDemo) {
                localStore.updateProfile(
                    uid = currentUserId,
                    fullName = fullName,
                    bio = bio,
                    avatarData = avatarData,
                    profileCompleted = true
                )
            }
            _snackbarMessage.value = "Profile updated"
            return
        }

        viewModelScope.launch {
            _isSavingProfile.value = true
            val result = repository.updateProfileDetails(fullName, bio, avatarData)
            _isSavingProfile.value = false
            result.onSuccess {
                _snackbarMessage.value = "Profile updated"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Update failed"
            }
        }
    }

    fun updatePreferences(discoverable: Boolean, notificationsEnabled: Boolean) {
        if (repository == null) {
            localAccountFlow.update {
                it?.copy(discoverable = discoverable, notificationsEnabled = notificationsEnabled)
            }
            return
        }
        if (notificationsEnabled) {
            LinkoraMessageService.start(appContext)
        } else {
            LinkoraMessageService.stop(appContext)
        }
        viewModelScope.launch {
            val result = repository.updateAccountPreferences(discoverable, notificationsEnabled)
            result.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not update settings"
            }
        }
    }

    fun sendFriendRequest(target: PublicProfile) {
        val me = (userAccountState.value as? UiState.Success)?.data ?: return
        if (repository == null) {
            val req = FriendRequest(
                id = "${me.userId}_${target.userId}",
                fromUid = me.userId,
                toUid = target.userId,
                memberUids = listOf(me.userId, target.userId),
                fromName = me.fullName,
                fromUsername = me.username,
                fromAvatar = me.avatarData,
                toName = target.fullName,
                toUsername = target.username,
                toAvatar = target.avatarData,
                status = "pending",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            localFriendRequestsFlow.update { list -> list.filterNot { it.id == req.id } + req }
            _snackbarMessage.value = "Friend request sent to ${target.fullName}"
            if (isDeveloperDemo) {
                viewModelScope.launch {
                    delay(1400L)
                    localFriendRequestsFlow.update { list ->
                        list.map { if (it.id == req.id) it.copy(status = "accepted") else it }
                    }
                    localAccountFlow.update { acc ->
                        acc?.copy(friendUids = (acc.friendUids + target.userId).distinct())
                    }
                    val chatId = if (currentUserId < target.userId) "${currentUserId}_${target.userId}" else "${target.userId}_${currentUserId}"
                    if (localChatsFlow.value.none { it.id == chatId }) {
                        val newChat = ChatThread(
                            id = chatId,
                            memberUids = listOf(currentUserId, target.userId).sorted(),
                            memberNames = mapOf(currentUserId to me.fullName, target.userId to target.fullName),
                            memberUsernames = mapOf(currentUserId to me.username, target.userId to target.username),
                            lastMessageText = "Assalam-o-Alaikum! Thanks for connecting on Linkora!",
                            lastMessageType = "text",
                            lastMessageSenderId = target.userId,
                            lastMessageStatus = "delivered",
                            lastMessageAt = Timestamp.now(),
                            createdAt = Timestamp.now()
                        )
                        localChatsFlow.update { listOf(newChat) + it }
                        localMessagesByChat.update { map ->
                            map + (chatId to listOf(
                                ChatMessage(
                                    id = UUID.randomUUID().toString(),
                                    chatId = chatId,
                                    senderId = target.userId,
                                    senderName = target.fullName,
                                    receiverId = currentUserId,
                                    memberUids = newChat.memberUids,
                                    messageType = "text",
                                    text = "Assalam-o-Alaikum! Thanks for connecting on Linkora!",
                                    status = "delivered",
                                    createdAt = Timestamp.now()
                                )
                            ))
                        }
                        _snackbarMessage.value = "${target.fullName} accepted your friend request!"
                    }
                }
            }
            return
        }

        viewModelScope.launch {
            val res = repository.sendFriendRequest(me, target)
            res.onSuccess {
                _snackbarMessage.value = "Friend request sent to ${target.fullName}"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to send request"
            }
        }
    }

    fun respondToRequest(request: FriendRequest, accept: Boolean) {
        val me = (userAccountState.value as? UiState.Success)?.data ?: return
        if (repository == null) {
            localFriendRequestsFlow.update { list ->
                list.map {
                    if (it.id == request.id) it.copy(status = if (accept) "accepted" else "declined") else it
                }
            }
            if (accept) {
                localAccountFlow.update { acc ->
                    acc?.copy(friendUids = (acc.friendUids + request.fromUid).distinct())
                }
                val chatId = if (currentUserId < request.fromUid) "${currentUserId}_${request.fromUid}" else "${request.fromUid}_${currentUserId}"
                if (localChatsFlow.value.none { it.id == chatId }) {
                    val newChat = ChatThread(
                        id = chatId,
                        memberUids = listOf(currentUserId, request.fromUid).sorted(),
                        memberNames = mapOf(currentUserId to me.fullName, request.fromUid to request.fromName),
                        memberUsernames = mapOf(currentUserId to me.username, request.fromUid to request.fromUsername),
                        lastMessageText = "You are now connected on Linkora!",
                        lastMessageType = "text",
                        lastMessageSenderId = currentUserId,
                        lastMessageStatus = "seen",
                        lastMessageAt = Timestamp.now(),
                        createdAt = Timestamp.now()
                    )
                    localChatsFlow.update { listOf(newChat) + it }
                }
                _snackbarMessage.value = "Connected with ${request.fromName}! You can chat now."
            } else {
                _snackbarMessage.value = "Request declined"
            }
            return
        }

        viewModelScope.launch {
            val res = repository.respondToFriendRequest(request, accept, me)
            res.onSuccess {
                _snackbarMessage.value = if (accept) {
                    "Connected with ${request.fromName}! You can chat now."
                } else {
                    "Request declined"
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Action failed"
            }
        }
    }

    // --- MESSAGING (TEXT, VOICE NOTE, PHOTO, VIDEO, FILE, DOCUMENT, LOCATION) ---

    private fun currentSenderName(): String {
        return (userAccountState.value as? UiState.Success)?.data?.fullName.orEmpty()
    }

    fun sendTextMessage(chat: ChatThread, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        setTyping(chat.id, false)
        val senderName = currentSenderName()

        if (repository == null) {
            val otherUid = chat.memberUids.firstOrNull { it != currentUserId }.orEmpty()
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                chatId = chat.id,
                senderId = currentUserId,
                senderName = senderName,
                receiverId = otherUid,
                memberUids = chat.memberUids,
                messageType = "text",
                text = clean,
                status = "sent",
                createdAt = Timestamp.now()
            )
            appendLocalMessage(chat.id, msg, clean, "text")
            if (isDeveloperDemo) {
                simulateDemoFriendReply(chat, otherUid, clean, isVoice = false)
            }
            return
        }

        viewModelScope.launch {
            val res = repository.sendTextMessage(chat, clean, senderName)
            res.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Message failed to send"
            }
        }
    }

    fun sendVoiceMessage(chat: ChatThread, audioBase64: String, durationSec: Int) {
        setTyping(chat.id, false)
        val senderName = currentSenderName()
        if (repository == null) {
            val otherUid = chat.memberUids.firstOrNull { it != currentUserId }.orEmpty()
            val dur = durationSec.coerceIn(1, 120).toLong()
            val preview = "Voice message (${dur}s)"
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                chatId = chat.id,
                senderId = currentUserId,
                senderName = senderName,
                receiverId = otherUid,
                memberUids = chat.memberUids,
                messageType = "voice",
                text = preview,
                audioData = audioBase64,
                audioDurationSec = dur,
                status = "sent",
                createdAt = Timestamp.now()
            )
            appendLocalMessage(chat.id, msg, preview, "voice")
            if (isDeveloperDemo) {
                simulateDemoFriendReply(chat, otherUid, preview, isVoice = true)
            }
            return
        }

        viewModelScope.launch {
            val res = repository.sendVoiceMessage(chat, audioBase64, durationSec, senderName)
            res.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Voice note failed to send"
            }
        }
    }

    fun sendPhotoMessage(chat: ChatThread, imageBase64: String, caption: String = "") {
        val preview = caption.trim().ifBlank { "📷 Photo" }
        sendAttachmentInternal(
            chat = chat,
            messageType = "photo",
            previewText = preview,
            mediaData = imageBase64
        )
    }

    fun sendVideoMessage(chat: ChatThread, thumbnailBase64: String, caption: String = "") {
        val preview = caption.trim().ifBlank { "🎬 Video" }
        sendAttachmentInternal(
            chat = chat,
            messageType = "video",
            previewText = preview,
            mediaData = thumbnailBase64
        )
    }

    fun sendFileOrDocumentMessage(chat: ChatThread, picked: PickedFileAttachment, isDocument: Boolean) {
        val type = if (isDocument) "document" else "file"
        val icon = if (isDocument) "📄" else "📎"
        val sizeStr = AttachmentCodec.formatFileSize(picked.sizeBytes)
        val preview = "$icon ${picked.fileName} ($sizeStr)"
        sendAttachmentInternal(
            chat = chat,
            messageType = type,
            previewText = preview,
            fileData = picked.base64Data,
            fileName = picked.fileName,
            fileMimeType = picked.mimeType,
            fileSizeBytes = picked.sizeBytes
        )
    }

    fun sendLocationMessage(chat: ChatThread, location: ResolvedLocation) {
        val preview = "📍 ${location.label}"
        sendAttachmentInternal(
            chat = chat,
            messageType = "location",
            previewText = preview,
            locationLat = location.latitude,
            locationLng = location.longitude,
            locationLabel = location.label
        )
    }

    private fun sendAttachmentInternal(
        chat: ChatThread,
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
    ) {
        setTyping(chat.id, false)
        val senderName = currentSenderName()
        if (repository == null) {
            val otherUid = chat.memberUids.firstOrNull { it != currentUserId }.orEmpty()
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                chatId = chat.id,
                senderId = currentUserId,
                senderName = senderName,
                receiverId = otherUid,
                memberUids = chat.memberUids,
                messageType = messageType,
                text = previewText,
                mediaData = mediaData,
                fileData = fileData,
                fileName = fileName,
                fileMimeType = fileMimeType,
                fileSizeBytes = fileSizeBytes,
                locationLat = locationLat,
                locationLng = locationLng,
                locationLabel = locationLabel,
                status = "sent",
                createdAt = Timestamp.now()
            )
            appendLocalMessage(chat.id, msg, previewText, messageType)
            if (isDeveloperDemo) {
                simulateDemoFriendReply(chat, otherUid, previewText, isVoice = false)
            }
            return
        }

        viewModelScope.launch {
            val res = repository.sendRichAttachmentMessage(
                chat = chat,
                senderName = senderName,
                messageType = messageType,
                previewText = previewText,
                mediaData = mediaData,
                fileData = fileData,
                fileName = fileName,
                fileMimeType = fileMimeType,
                fileSizeBytes = fileSizeBytes,
                locationLat = locationLat,
                locationLng = locationLng,
                locationLabel = locationLabel
            )
            res.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Attachment failed to send"
            }
        }
    }

    private fun appendLocalMessage(chatId: String, msg: ChatMessage, preview: String, type: String) {
        localMessagesByChat.update { map ->
            map + (chatId to (map[chatId].orEmpty() + msg))
        }
        localChatsFlow.update { list ->
            val updated = list.map { c ->
                if (c.id == chatId) {
                    val unhidden = c.hiddenBy.toMutableMap().apply {
                        c.memberUids.forEach { put(it, false) }
                    }
                    c.copy(
                        hiddenBy = unhidden,
                        lastMessageText = preview,
                        lastMessageType = type,
                        lastMessageSenderId = msg.senderId,
                        lastMessageStatus = msg.status,
                        lastMessageAt = Timestamp.now()
                    )
                } else c
            }
            updated.sortedByDescending { it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0) }
        }
    }

    private fun simulateDemoFriendReply(chat: ChatThread, otherUid: String, userText: String, isVoice: Boolean) {
        if (!isDeveloperDemo || otherUid.isBlank()) return
        viewModelScope.launch {
            delay(450L)
            updateLastLocalMessageStatus(chat.id, "delivered")
            delay(450L)
            updateLastLocalMessageStatus(chat.id, "seen")
            updateLocalTyping(chat.id, otherUid, true)

            delay(1500L)
            updateLocalTyping(chat.id, otherUid, false)

            val replyText = when {
                isVoice -> "Sun liya aapka voice note! Bilkul clear aawaz aa rahi hai 🎙️✨"
                userText.startsWith("📷") -> "Wah, zabardast photo bheji hai! 🔥"
                userText.startsWith("🎬") -> "Video preview mil gaya, mast hai! 🎬"
                userText.startsWith("📍") -> "Location mil gayi! Map pe check kar liya 📍"
                userText.startsWith("📄") || userText.startsWith("📎") -> "File receive ho gayi hai, thanks! 📄"
                userText.contains("salam", ignoreCase = true) || userText.contains("hi", ignoreCase = true) || userText.contains("hello", ignoreCase = true) ->
                    "Wa Alaikum Assalam! Kaise ho? Linkora pe baat kar ke acha laga 😊"
                userText.contains("call", ignoreCase = true) ->
                    "Bilkul! Upar Audio ya Video call icon tap karo, main ready hoon 📞"
                else ->
                    "Bilkul sahi kaha! Aur sunao kya chal raha hai aaj kal?"
            }
            val reply = ChatMessage(
                id = UUID.randomUUID().toString(),
                chatId = chat.id,
                senderId = otherUid,
                senderName = chat.memberNames[otherUid].orEmpty(),
                receiverId = currentUserId,
                memberUids = chat.memberUids,
                messageType = "text",
                text = replyText,
                status = if (_selectedChatId.value == chat.id) "seen" else "delivered",
                createdAt = Timestamp.now()
            )
            appendLocalMessage(chat.id, reply, replyText, "text")
        }
    }

    private fun updateLastLocalMessageStatus(chatId: String, status: String) {
        localMessagesByChat.update { map ->
            val msgs = map[chatId].orEmpty().map { m ->
                if (m.senderId == currentUserId && m.status != "seen") m.copy(status = status) else m
            }
            map + (chatId to msgs)
        }
        localChatsFlow.update { list ->
            list.map { c ->
                if (c.id == chatId && c.lastMessageSenderId == currentUserId) {
                    c.copy(lastMessageStatus = status)
                } else c
            }
        }
    }

    // --- STORIES ---

    fun postStory(
        storyType: String,
        caption: String,
        mediaData: String?,
        musicTitle: String?,
        musicArtist: String?,
        backgroundHex: String,
        visibility: String,
        onComplete: () -> Unit
    ) {
        val me = (userAccountState.value as? UiState.Success)?.data ?: return
        if (repository == null) {
            val item = StoryItem(
                id = UUID.randomUUID().toString(),
                authorId = me.userId,
                authorName = me.fullName,
                authorUsername = me.username,
                authorAvatar = me.avatarData,
                storyType = storyType,
                caption = caption.trim().take(500),
                mediaData = mediaData,
                musicTitle = musicTitle?.trim()?.ifBlank { null },
                musicArtist = musicArtist?.trim()?.ifBlank { null },
                backgroundHex = backgroundHex,
                visibility = if (visibility == "public") "public" else "friends",
                allowedViewerUids = (listOf(me.userId) + me.friendUids).distinct(),
                expiresAtMillis = System.currentTimeMillis() + STORY_TTL_MS,
                createdAt = Timestamp.now()
            )
            localStoriesFlow.update { listOf(item) + it }
            _snackbarMessage.value = "Story shared for 24 hours!"
            onComplete()
            return
        }

        viewModelScope.launch {
            val res = repository.postStory(
                author = me,
                storyType = storyType,
                caption = caption,
                mediaData = mediaData,
                musicTitle = musicTitle,
                musicArtist = musicArtist,
                backgroundHex = backgroundHex,
                visibility = visibility
            )
            res.onSuccess {
                _snackbarMessage.value = "Story shared for 24 hours!"
                onComplete()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not publish story"
            }
        }
    }

    fun deleteStory(storyId: String) {
        if (repository == null) {
            localStoriesFlow.update { list -> list.filterNot { it.id == storyId } }
            _snackbarMessage.value = "Story deleted"
            return
        }
        viewModelScope.launch {
            val res = repository.deleteStory(storyId)
            res.onSuccess {
                _snackbarMessage.value = "Story deleted"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not delete story"
            }
        }
    }

    // --- CALLS ---

    fun startCall(
        calleeUid: String,
        calleeName: String,
        calleeAvatar: String?,
        callType: String
    ) {
        val me = (userAccountState.value as? UiState.Success)?.data ?: return
        if (calleeUid in me.blockedUids) {
            _snackbarMessage.value = "Unblock $calleeName first to start a call"
            return
        }
        if (repository == null) {
            val callId = UUID.randomUUID().toString()
            val call = CallSession(
                id = callId,
                callerId = me.userId,
                calleeId = calleeUid,
                memberUids = listOf(me.userId, calleeUid),
                callerName = me.fullName,
                callerAvatar = me.avatarData,
                calleeName = calleeName,
                calleeAvatar = calleeAvatar,
                callType = if (callType == "video") "video" else "audio",
                status = "ringing",
                durationSeconds = 0L,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            localCallsFlow.update { listOf(call) + it }
            if (isDeveloperDemo) {
                viewModelScope.launch {
                    delay(1800L)
                    localCallsFlow.update { list ->
                        list.map { if (it.id == callId && it.status == "ringing") it.copy(status = "connected") else it }
                    }
                }
            }
            return
        }

        viewModelScope.launch {
            val res = repository.startCall(me, calleeUid, calleeName, calleeAvatar, callType)
            res.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not start call"
            }
        }
    }

    fun answerCall(callId: String, accept: Boolean) {
        if (repository == null) {
            localCallsFlow.update { list ->
                list.map { if (it.id == callId) it.copy(status = if (accept) "connected" else "declined") else it }
            }
            return
        }
        viewModelScope.launch {
            val res = repository.answerCall(callId, accept)
            res.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Call response failed"
            }
        }
    }

    fun endCall(call: CallSession, elapsedSeconds: Long) {
        if (repository == null) {
            val finalStatus = if (call.status == "ringing") "missed" else "ended"
            localCallsFlow.update { list ->
                list.map {
                    if (it.id == call.id) it.copy(status = finalStatus, durationSeconds = elapsedSeconds) else it
                }
            }
            return
        }
        viewModelScope.launch {
            repository.endCall(
                callId = call.id,
                wasRinging = call.status == "ringing",
                durationSeconds = elapsedSeconds
            )
        }
    }

    fun pushCallMedia(
        callId: String,
        isCaller: Boolean,
        videoFrameBase64: String? = null,
        audioChunkBase64: String? = null,
        audioSeq: Long? = null
    ) {
        if (repository == null) return
        viewModelScope.launch {
            repository.pushCallMediaFrame(
                callId = callId,
                isCaller = isCaller,
                videoFrameBase64 = videoFrameBase64,
                audioChunkBase64 = audioChunkBase64,
                audioSeq = audioSeq
            )
        }
    }

    // --- KOTLIN + WEBRTC SIGNALING ---

    fun publishWebRtcOffer(callId: String, offerSdp: String) {
        if (repository == null) {
            localCallsFlow.update { list ->
                list.map { if (it.id == callId) it.copy(offerSdp = offerSdp, webrtcEnabled = true) else it }
            }
            return
        }
        viewModelScope.launch {
            repository.publishWebRtcOffer(callId, offerSdp)
        }
    }

    fun publishWebRtcAnswer(callId: String, answerSdp: String) {
        if (repository == null) {
            localCallsFlow.update { list ->
                list.map { if (it.id == callId) it.copy(answerSdp = answerSdp) else it }
            }
            return
        }
        viewModelScope.launch {
            repository.publishWebRtcAnswer(callId, answerSdp)
        }
    }

    fun sendWebRtcIceCandidate(
        callId: String,
        memberUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        if (repository == null) return
        viewModelScope.launch {
            repository.sendIceCandidate(callId, memberUids, sdpMid, sdpMLineIndex, candidate)
        }
    }

    fun observeWebRtcIceCandidates(callId: String): kotlinx.coroutines.flow.Flow<List<IceCandidateSignal>> {
        return repository?.observeIceCandidates(callId, currentUserId) ?: flowOf(emptyList())
    }

    private fun unwrapFlowError(label: String, error: Throwable): UiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(VM_TAG, "Error observing $label", rootError)
        return UiState.Error(rootError.localizedMessage ?: "Failed to load $label")
    }

    // --- Seed data STRICTLY FOR DEVELOPER DEMO MODE ONLY ---
    private fun buildInitialLocalAccount(): UserAccount? {
        if (repository != null) return null
        return if (localUser != null && !isDeveloperDemo) {
            // Real local registered user: ZERO fake friends or fake chats!
            UserAccount(
                userId = localUser.uid,
                fullName = localUser.fullName,
                username = localUser.username,
                email = localUser.email,
                bio = localUser.bio,
                avatarData = localUser.avatarData,
                online = true,
                discoverable = true,
                profileCompleted = localUser.profileCompleted,
                notificationsEnabled = true,
                friendUids = emptyList()
            )
        } else {
            UserAccount(
                userId = currentUserId,
                fullName = "Linkora Developer",
                username = "linkora_dev",
                email = "dev@linkora.app",
                bio = "Testing Linkora in Developer Demo Mode 🚀",
                online = true,
                discoverable = true,
                profileCompleted = true,
                notificationsEnabled = true,
                friendUids = listOf("demo_zara", "demo_hamza")
            )
        }
    }

    private fun seedLocalOrDemoProfiles(): List<PublicProfile> {
        if (repository != null) return emptyList()
        if (!isDeveloperDemo) {
            // Real local users only discover real registered accounts on this device (even when offline)
            return localStore.getAllPublicProfiles(excludeUid = currentUserId)
        }
        return listOf(
            PublicProfile(
                userId = "demo_zara",
                fullName = "Zara Ali",
                username = "zara_ali",
                bio = "UI Designer • Coffee & Travel",
                online = true,
                discoverable = true
            ),
            PublicProfile(
                userId = "demo_hamza",
                fullName = "Hamza Khan",
                username = "hamza_dev",
                bio = "Android Engineer • Building cool apps",
                online = true,
                discoverable = true
            ),
            PublicProfile(
                userId = "demo_ayesha",
                fullName = "Ayesha Noor",
                username = "ayesha_art",
                bio = "Digital Illustrator & Photographer ✨",
                online = false,
                discoverable = true
            )
        )
    }

    private fun seedDemoRequestsOnly(): List<FriendRequest> {
        if (repository != null || !isDeveloperDemo) return emptyList()
        val me = buildInitialLocalAccount() ?: return emptyList()
        return listOf(
            FriendRequest(
                id = "demo_ayesha_${me.userId}",
                fromUid = "demo_ayesha",
                toUid = me.userId,
                memberUids = listOf("demo_ayesha", me.userId),
                fromName = "Ayesha Noor",
                fromUsername = "ayesha_art",
                toName = me.fullName,
                toUsername = me.username,
                status = "pending",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 1800_000L))
            )
        )
    }

    private fun seedDemoChatsOnly(): List<ChatThread> {
        if (repository != null || !isDeveloperDemo) return emptyList()
        val me = buildInitialLocalAccount() ?: return emptyList()
        val zaraChatId = if (me.userId < "demo_zara") "${me.userId}_demo_zara" else "demo_zara_${me.userId}"
        val hamzaChatId = if (me.userId < "demo_hamza") "${me.userId}_demo_hamza" else "demo_hamza_${me.userId}"
        return listOf(
            ChatThread(
                id = zaraChatId,
                memberUids = listOf(me.userId, "demo_zara").sorted(),
                memberNames = mapOf(me.userId to me.fullName, "demo_zara" to "Zara Ali"),
                memberUsernames = mapOf(me.userId to me.username, "demo_zara" to "zara_ali"),
                lastMessageText = "Assalam-o-Alaikum! Linkora demo ready hai — swipe left ya hold press try karo ✨",
                lastMessageType = "text",
                lastMessageSenderId = "demo_zara",
                lastMessageStatus = "delivered",
                lastMessageAt = Timestamp(Date(System.currentTimeMillis() - 120_000L)),
                createdAt = Timestamp(Date(System.currentTimeMillis() - 600_000L))
            ),
            ChatThread(
                id = hamzaChatId,
                memberUids = listOf(me.userId, "demo_hamza").sorted(),
                memberNames = mapOf(me.userId to me.fullName, "demo_hamza" to "Hamza Khan"),
                memberUsernames = mapOf(me.userId to me.username, "demo_hamza" to "hamza_dev"),
                lastMessageText = "Voice notes, + attachments aur WebRTC HD calls try karo!",
                lastMessageType = "text",
                lastMessageSenderId = "demo_hamza",
                lastMessageStatus = "seen",
                lastMessageAt = Timestamp(Date(System.currentTimeMillis() - 900_000L)),
                createdAt = Timestamp(Date(System.currentTimeMillis() - 3600_000L))
            )
        )
    }

    private fun seedDemoMessagesOnly(): Map<String, List<ChatMessage>> {
        if (repository != null || !isDeveloperDemo) return emptyMap()
        val me = buildInitialLocalAccount() ?: return emptyMap()
        val zaraChatId = if (me.userId < "demo_zara") "${me.userId}_demo_zara" else "demo_zara_${me.userId}"
        val hamzaChatId = if (me.userId < "demo_hamza") "${me.userId}_demo_hamza" else "demo_hamza_${me.userId}"
        return mapOf(
            zaraChatId to listOf(
                ChatMessage(
                    id = "demo_msg_1",
                    chatId = zaraChatId,
                    senderId = "demo_zara",
                    senderName = "Zara Ali",
                    receiverId = me.userId,
                    memberUids = listOf(me.userId, "demo_zara").sorted(),
                    messageType = "text",
                    text = "Assalam-o-Alaikum! Message pe hold tap kar ke Delete for me ya Delete permanently try karo ✨",
                    status = "delivered",
                    createdAt = Timestamp(Date(System.currentTimeMillis() - 120_000L))
                )
            ),
            hamzaChatId to listOf(
                ChatMessage(
                    id = "demo_msg_2",
                    chatId = hamzaChatId,
                    senderId = "demo_hamza",
                    senderName = "Hamza Khan",
                    receiverId = me.userId,
                    memberUids = listOf(me.userId, "demo_hamza").sorted(),
                    messageType = "text",
                    text = "Voice notes, + attachments aur WebRTC HD calls try karo!",
                    status = "seen",
                    createdAt = Timestamp(Date(System.currentTimeMillis() - 900_000L))
                )
            )
        )
    }

    private fun seedDemoStoriesOnly(): List<StoryItem> {
        if (repository != null || !isDeveloperDemo) return emptyList()
        return listOf(
            StoryItem(
                id = "demo_story_1",
                authorId = "demo_zara",
                authorName = "Zara Ali",
                authorUsername = "zara_ali",
                storyType = "text",
                caption = "Exploring Islamabad sunset vibes today 🌅✨",
                backgroundHex = "#4338CA",
                visibility = "public",
                allowedViewerUids = listOf("demo_zara", currentUserId),
                expiresAtMillis = System.currentTimeMillis() + STORY_TTL_MS,
                createdAt = Timestamp(Date(System.currentTimeMillis() - 1800_000L))
            )
        )
    }

    private fun seedDemoCallsOnly(): List<CallSession> {
        if (repository != null || !isDeveloperDemo) return emptyList()
        val me = buildInitialLocalAccount() ?: return emptyList()
        return listOf(
            CallSession(
                id = "demo_call_1",
                callerId = "demo_zara",
                calleeId = me.userId,
                memberUids = listOf("demo_zara", me.userId),
                callerName = "Zara Ali",
                calleeName = me.fullName,
                callType = "video",
                status = "ended",
                durationSeconds = 142L,
                createdAt = Timestamp(Date(System.currentTimeMillis() - 7200_000L))
            )
        )
    }
}
