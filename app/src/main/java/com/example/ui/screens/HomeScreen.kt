package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LinkoraMessageService
import com.example.data.model.ChatThread
import com.example.data.model.DisappearingOption
import com.example.data.model.HomeTab
import com.example.data.model.SecondaryPage
import com.example.data.model.UserAccount
import com.example.ui.components.FullScreenAvatarDialog
import com.example.ui.components.GlassSurface
import com.example.ui.components.LinkoraGlassBackdrop
import com.example.ui.components.LinkoraLogoMark
import com.example.ui.components.UserAvatar
import com.example.ui.theme.CoralWarm
import com.example.viewmodel.InAppBannerAlert
import com.example.viewmodel.LinkoraAiViewModel
import com.example.viewmodel.LinkoraViewModel
import kotlinx.coroutines.delay

@Composable
fun LinkoraHomeScreen(
    account: UserAccount,
    sessionModeLabel: String,
    viewModel: LinkoraViewModel,
    aiViewModel: LinkoraAiViewModel,
    darkMode: Boolean,
    pendingOpenChatId: String? = null,
    onConsumePendingOpenChatId: () -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val discoverableProfiles by viewModel.discoverableProfiles.collectAsStateWithLifecycle()
    val friendRequests by viewModel.friendRequests.collectAsStateWithLifecycle()
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val hasChatLockPin by viewModel.hasChatLockPin.collectAsStateWithLifecycle()
    val chatLockHint by viewModel.chatLockHint.collectAsStateWithLifecycle()
    val localGlobalDisappearingMs by viewModel.globalDisappearingMs.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val vibrateEnabled by viewModel.vibrateEnabled.collectAsStateWithLifecycle()
    val readReceiptsEnabled by viewModel.readReceiptsEnabled.collectAsStateWithLifecycle()
    val onlinePresenceVisible by viewModel.onlinePresenceVisible.collectAsStateWithLifecycle()
    val enterKeySends by viewModel.enterKeySends.collectAsStateWithLifecycle()
    val lowDataCalls by viewModel.lowDataCalls.collectAsStateWithLifecycle()
    val callQualityMode by viewModel.callQualityMode.collectAsStateWithLifecycle()
    val chatWallpaper by viewModel.chatWallpaper.collectAsStateWithLifecycle()
    val fontScaleOption by viewModel.fontScaleOption.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val selectedChatMessages by viewModel.selectedChatMessages.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val incomingRingingCall by viewModel.incomingRingingCall.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val fullScreenAvatar by viewModel.fullScreenAvatar.collectAsStateWithLifecycle()
    val inAppBanner by viewModel.inAppBanner.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val aiMessages by aiViewModel.messages.collectAsStateWithLifecycle()
    val aiStreamState by aiViewModel.streamState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(HomeTab.CHATS) }
    var secondaryPage by remember { mutableStateOf(SecondaryPage.NONE) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Interactive modal states
    var longPressedChat by remember { mutableStateOf<ChatThread?>(null) }
    var settingsTargetChat by remember { mutableStateOf<ChatThread?>(null) }
    var disappearingTargetChat by remember { mutableStateOf<ChatThread?>(null) }
    var showGlobalDisappearingDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var pinDialogMode by remember { mutableStateOf<PinDialogMode?>(null) }
    var pendingPinSuccessAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val profilesByUid = remember(discoverableProfiles) {
        discoverableProfiles.associateBy { it.userId }
    }
    val blockedSet = remember(account.blockedUids) { account.blockedUids.toSet() }
    val effectiveGlobalDisappearingMs = if (account.defaultDisappearingMs > 0L) {
        account.defaultDisappearingMs
    } else {
        localGlobalDisappearingMs
    }

    val archivedChats = remember(chats, account.userId) {
        chats.filter { it.isArchivedFor(account.userId) && !it.isHiddenFor(account.userId) }
    }
    val lockedChats = remember(chats, account.userId) {
        chats.filter { it.isLockedFor(account.userId) && !it.isHiddenFor(account.userId) }
    }
    val connectedFriendProfiles = remember(discoverableProfiles, account.friendUids, friendRequests, account.userId) {
        val fromReqs = friendRequests.filter { it.status == "accepted" }
            .map { if (it.fromUid == account.userId) it.toUid else it.fromUid }
        val allFriendUids = (account.friendUids + fromReqs).toSet()
        discoverableProfiles.filter { it.userId in allFriendUids }
    }

    // Request Android 13+ notification permission once when entering home & start background listener
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && account.notificationsEnabled && viewModel.isCloudMode) {
            LinkoraMessageService.start(context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLocalProfiles()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && account.notificationsEnabled) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (account.notificationsEnabled && viewModel.isCloudMode) {
            LinkoraMessageService.start(context)
        }
    }

    // Open target chat when user taps a system heads-up or lock-screen notification
    LaunchedEffect(pendingOpenChatId, chats) {
        val targetChatId = pendingOpenChatId
        if (!targetChatId.isNullOrBlank() && chats.any { it.id == targetChatId }) {
            secondaryPage = SecondaryPage.NONE
            currentTab = HomeTab.CHATS
            viewModel.restoreAndOpenChat(targetChatId)
            onConsumePendingOpenChatId()
        }
    }

    // Auto-dismiss in-app top heads-up banner after 4.5 seconds
    LaunchedEffect(inAppBanner?.id) {
        val banner = inAppBanner ?: return@LaunchedEffect
        delay(4500L)
        if (viewModel.inAppBanner.value?.id == banner.id) {
            viewModel.dismissInAppBanner()
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    val selectedChat = remember(chats, selectedChatId) {
        chats.firstOrNull { it.id == selectedChatId }
    }

    fun requestLockOrUnlockChat(chat: ChatThread) {
        val currentlyLocked = chat.isLockedFor(account.userId)
        if (currentlyLocked) {
            viewModel.setChatLocked(chat, false)
        } else if (!hasChatLockPin) {
            pendingPinSuccessAction = { viewModel.setChatLocked(chat, true) }
            pinDialogMode = PinDialogMode.CREATE
        } else {
            viewModel.setChatLocked(chat, true)
        }
    }

    fun requestOpenLockedChatsPage() {
        pendingPinSuccessAction = { secondaryPage = SecondaryPage.LOCKED_CHATS }
        pinDialogMode = if (hasChatLockPin) PinDialogMode.UNLOCK else PinDialogMode.CREATE
    }

    LinkoraGlassBackdrop(wallpaper = chatWallpaper) {
        // Main Home Scaffold
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinkoraLogoMark(size = 34.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = when (currentTab) {
                                        HomeTab.CHATS -> "Linkora"
                                        HomeTab.STORIES -> "Stories"
                                        HomeTab.PEOPLE -> "People"
                                        HomeTab.CALLS -> "Calls"
                                        HomeTab.SETTINGS -> "Settings"
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { secondaryPage = SecondaryPage.AI_CHAT },
                                    modifier = Modifier.testTag("header_ai_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Open Linkora AI",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { secondaryPage = SecondaryPage.NOTIFICATIONS },
                                    modifier = Modifier.testTag("header_notifications_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (notifications.isNotEmpty()) {
                                                Badge(containerColor = CoralWarm) {
                                                    Text(notifications.size.coerceAtMost(99).toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Notifications,
                                            contentDescription = "Notifications",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier.testTag("header_overflow_menu")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "More options",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Settings & Profile") },
                                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                currentTab = HomeTab.SETTINGS
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Blocked Users (${account.blockedUids.size})") },
                                            leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                secondaryPage = SecondaryPage.BLOCKED_USERS
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Support") },
                                            leadingIcon = { Icon(Icons.Rounded.SupportAgent, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                secondaryPage = SecondaryPage.SUPPORT
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Help & FAQ") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null) },
                                            onClick = {
                                                menuExpanded = false
                                                secondaryPage = SecondaryPage.HELP
                                            }
                                        )
                                    }
                                }
                            }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        ClassicBottomNavBar(
                            currentTab = currentTab,
                            pendingRequestCount = friendRequests.count {
                                it.toUid == account.userId && it.status == "pending" && it.fromUid !in blockedSet
                            },
                            onSelectTab = { currentTab = it }
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .navigationBarsPadding(),
                            containerColor = Color.Transparent
                        ) {
                            HomeTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = tabIcon(tab, selected),
                                            contentDescription = tab.name
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (currentTab) {
                            HomeTab.CHATS -> ChatsTabScreen(
                                currentUserId = account.userId,
                                chats = chats,
                                profilesByUid = profilesByUid,
                                archivedCount = archivedChats.size,
                                lockedCount = lockedChats.size,
                                blockedUids = blockedSet,
                                onOpenChat = { viewModel.openChat(it.id) },
                                onRestoreAndOpenChat = { viewModel.restoreAndOpenChat(it.id) },
                                onArchiveChat = { viewModel.setChatArchived(it, true) },
                                onDeleteChat = { viewModel.deleteChatForMe(it) },
                                onLongPressChat = { longPressedChat = it },
                                onOpenArchivedPage = { secondaryPage = SecondaryPage.ARCHIVED_CHATS },
                                onOpenLockedPage = { requestOpenLockedChatsPage() },
                                onOpenCreateGroup = { showCreateGroupDialog = true },
                                onOpenAddPeople = { currentTab = HomeTab.PEOPLE },
                                onOpenLinkoraAi = { secondaryPage = SecondaryPage.AI_CHAT },
                                onViewAvatar = viewModel::showFullScreenAvatar
                            )
                            HomeTab.STORIES -> StoriesTabScreen(
                                currentUser = account,
                                stories = stories,
                                onPostStory = viewModel::postStory,
                                onDeleteStory = viewModel::deleteStory,
                                onViewAvatar = viewModel::showFullScreenAvatar
                            )
                            HomeTab.PEOPLE -> PeopleTabScreen(
                                currentUser = account,
                                allProfiles = discoverableProfiles,
                                friendRequests = friendRequests,
                                onSendRequest = viewModel::sendFriendRequest,
                                onRespondRequest = viewModel::respondToRequest,
                                onStartChatWithFriend = { friendUid ->
                                    val chat = chats.firstOrNull { !it.isGroup && friendUid in it.memberUids }
                                    if (chat != null) {
                                        viewModel.restoreAndOpenChat(chat.id)
                                    } else {
                                        currentTab = HomeTab.CHATS
                                    }
                                },
                                onRemoveFriend = viewModel::removeFriend,
                                onBlockUser = viewModel::blockUser,
                                onUnblockUser = viewModel::unblockUser,
                                onCallUser = viewModel::startCall,
                                onViewAvatar = viewModel::showFullScreenAvatar
                            )
                            HomeTab.CALLS -> CallsTabScreen(
                                currentUserId = account.userId,
                                calls = calls,
                                onRedial = viewModel::startCall,
                                onViewAvatar = viewModel::showFullScreenAvatar
                            )
                            HomeTab.SETTINGS -> SettingsTabScreen(
                                account = account,
                                chatsCount = chats.count { !it.isHiddenFor(account.userId) },
                                storiesCount = stories.count { it.authorId == account.userId },
                                sessionModeLabel = sessionModeLabel,
                                darkMode = darkMode,
                                hasChatLockPin = hasChatLockPin,
                                globalDisappearingMs = effectiveGlobalDisappearingMs,
                                soundEnabled = soundEnabled,
                                vibrateEnabled = vibrateEnabled,
                                readReceiptsEnabled = readReceiptsEnabled,
                                onlinePresenceVisible = onlinePresenceVisible,
                                enterKeySends = enterKeySends,
                                lowDataCalls = lowDataCalls,
                                callQualityMode = callQualityMode,
                                chatWallpaper = chatWallpaper,
                                fontScaleOption = fontScaleOption,
                                onUpdateProfile = viewModel::updateProfile,
                                onToggleDarkMode = onToggleDarkMode,
                                onSelectWallpaper = viewModel::setChatWallpaper,
                                onSelectFontScale = viewModel::setFontScaleOption,
                                onUpdateAccountPrefs = viewModel::updatePreferences,
                                onToggleSound = viewModel::setSoundEnabled,
                                onToggleVibrate = viewModel::setVibrateEnabled,
                                onToggleReadReceipts = viewModel::setReadReceiptsEnabled,
                                onToggleOnlinePresence = viewModel::setOnlinePresenceVisible,
                                onToggleEnterKeySends = viewModel::setEnterKeySends,
                                onToggleLowDataCalls = viewModel::setLowDataCalls,
                                onSelectCallQuality = viewModel::setCallQualityMode,
                                onConfigureChatLockPin = {
                                    pendingPinSuccessAction = null
                                    pinDialogMode = if (hasChatLockPin) PinDialogMode.RESET_AND_CREATE else PinDialogMode.CREATE
                                },
                                onResetChatLockPin = viewModel::resetChatLockPin,
                                onOpenGlobalDisappearing = { showGlobalDisappearingDialog = true },
                                onOpenBlockedUsers = { secondaryPage = SecondaryPage.BLOCKED_USERS },
                                onOpenSupport = { secondaryPage = SecondaryPage.SUPPORT },
                                onOpenHelp = { secondaryPage = SecondaryPage.HELP },
                                onTestMessageAlert = viewModel::sendTestMessageAlert,
                                onSignOut = onSignOut,
                                onViewAvatar = viewModel::showFullScreenAvatar
                            )
                        }
                    }
                }
            }
        }

        // Secondary Pages
        when (secondaryPage) {
            SecondaryPage.AI_CHAT -> LinkoraAiScreen(
                messages = aiMessages,
                streamState = aiStreamState,
                chatWallpaper = chatWallpaper,
                onBack = { secondaryPage = SecondaryPage.NONE },
                onSendPrompt = { prompt -> aiViewModel.sendPrompt(prompt) },
                onRetryLastPrompt = aiViewModel::retryLastPrompt,
                onClearHistory = aiViewModel::clearConversation,
                onDismissError = aiViewModel::dismissError
            )
            SecondaryPage.NOTIFICATIONS -> NotificationsScreen(
                notifications = notifications,
                chatWallpaper = chatWallpaper,
                onBack = { secondaryPage = SecondaryPage.NONE },
                onNotificationClick = { item ->
                    secondaryPage = SecondaryPage.NONE
                    when (item.type) {
                        "request" -> currentTab = HomeTab.PEOPLE
                        "missed_call" -> currentTab = HomeTab.CALLS
                        else -> currentTab = HomeTab.CHATS
                    }
                }
            )
            SecondaryPage.LOCKED_CHATS -> LockedChatsScreen(
                currentUserId = account.userId,
                lockedChats = lockedChats,
                profilesByUid = profilesByUid,
                onBack = { secondaryPage = SecondaryPage.NONE },
                onOpenChat = { viewModel.openChat(it.id) },
                onUnlockChat = { viewModel.setChatLocked(it, false) },
                onDeleteChat = { viewModel.deleteChatForMe(it) },
                onOpenPinSettings = {
                    pendingPinSuccessAction = null
                    pinDialogMode = PinDialogMode.RESET_AND_CREATE
                }
            )
            SecondaryPage.ARCHIVED_CHATS -> ArchivedChatsScreen(
                currentUserId = account.userId,
                archivedChats = archivedChats,
                profilesByUid = profilesByUid,
                onBack = { secondaryPage = SecondaryPage.NONE },
                onOpenChat = { viewModel.openChat(it.id) },
                onUnarchiveChat = { viewModel.setChatArchived(it, false) },
                onDeleteChat = { viewModel.deleteChatForMe(it) }
            )
            SecondaryPage.BLOCKED_USERS -> BlockedUsersScreen(
                blockedUids = account.blockedUids,
                profilesByUid = profilesByUid,
                chatWallpaper = chatWallpaper,
                onUnblockUser = viewModel::unblockUser,
                onBack = { secondaryPage = SecondaryPage.NONE }
            )
            SecondaryPage.SUPPORT -> SupportScreen(
                chatWallpaper = chatWallpaper,
                onBack = { secondaryPage = SecondaryPage.NONE }
            )
            SecondaryPage.HELP -> HelpScreen(
                chatWallpaper = chatWallpaper,
                onBack = { secondaryPage = SecondaryPage.NONE }
            )
            SecondaryPage.NONE -> Unit
        }

        // Chat Detail Full Screen
        if (selectedChat != null) {
            val otherUid = selectedChat.memberUids.firstOrNull { it != account.userId }.orEmpty()
            val otherProfile = profilesByUid[otherUid]
            val otherName = otherProfile?.fullName ?: selectedChat.memberNames[otherUid] ?: "Friend"
            val otherAvatar = otherProfile?.avatarData ?: selectedChat.memberAvatars[otherUid]
            val effectiveDisappearing = if (selectedChat.disappearingDurationMs > 0L) {
                selectedChat.disappearingDurationMs
            } else {
                effectiveGlobalDisappearingMs
            }

            ChatDetailScreen(
                currentUserId = account.userId,
                chat = selectedChat,
                otherProfile = otherProfile,
                messages = selectedChatMessages,
                effectiveDisappearingMs = effectiveDisappearing,
                isOtherBlocked = !selectedChat.isGroup && otherUid in blockedSet,
                chatWallpaper = chatWallpaper,
                enterKeySends = enterKeySends,
                onBack = { viewModel.openChat(null) },
                onSendText = { text -> viewModel.sendTextMessage(selectedChat, text) },
                onSendVoice = { b64, dur -> viewModel.sendVoiceMessage(selectedChat, b64, dur) },
                onSendPhoto = { b64 -> viewModel.sendPhotoMessage(selectedChat, b64) },
                onSendVideo = { thumb -> viewModel.sendVideoMessage(selectedChat, thumb) },
                onSendFileOrDocument = { picked, isDoc ->
                    viewModel.sendFileOrDocumentMessage(selectedChat, picked, isDoc)
                },
                onSendLocation = { loc -> viewModel.sendLocationMessage(selectedChat, loc) },
                onTypingChanged = { typing -> viewModel.setTyping(selectedChat.id, typing) },
                onFeedback = viewModel::showFeedback,
                onStartAudioCall = { viewModel.startCall(otherUid, otherName, otherAvatar, "audio") },
                onStartVideoCall = { viewModel.startCall(otherUid, otherName, otherAvatar, "video") },
                onOpenDisappearingPicker = { disappearingTargetChat = selectedChat },
                onToggleLock = { requestLockOrUnlockChat(selectedChat) },
                onToggleArchive = {
                    viewModel.setChatArchived(selectedChat, !selectedChat.isArchivedFor(account.userId))
                },
                onDeleteChat = { viewModel.deleteChatForMe(selectedChat) },
                onDeleteMessageForMe = { msg -> viewModel.deleteMessageForMe(selectedChat.id, msg) },
                onDeleteMessageForEveryone = { msg -> viewModel.deleteMessageForEveryone(selectedChat.id, msg) },
                onDeleteMessagePermanently = { msg -> viewModel.deleteMessagePermanently(selectedChat.id, msg) },
                onBlockUser = if (selectedChat.isGroup) null else viewModel::blockUser,
                onUnblockUser = if (selectedChat.isGroup) null else viewModel::unblockUser,
                onRemoveFriend = if (selectedChat.isGroup) null else viewModel::removeFriend,
                onViewAvatar = viewModel::showFullScreenAvatar
            )
        }

        // Real-time Active Kotlin + WebRTC Call Full-Screen Overlay
        activeCall?.let { liveCall ->
            val iceFlow = remember(liveCall.id) {
                viewModel.observeWebRtcIceCandidates(liveCall.id)
            }
            ActiveCallOverlay(
                currentUserId = account.userId,
                call = liveCall,
                callQualityMode = callQualityMode,
                lowDataCalls = lowDataCalls,
                iceCandidatesFlow = iceFlow,
                onPublishOffer = { sdp -> viewModel.publishWebRtcOffer(liveCall.id, sdp) },
                onPublishAnswer = { sdp -> viewModel.publishWebRtcAnswer(liveCall.id, sdp) },
                onSendIceCandidate = { mid, idx, cand ->
                    viewModel.sendWebRtcIceCandidate(
                        callId = liveCall.id,
                        memberUids = liveCall.memberUids,
                        sdpMid = mid,
                        sdpMLineIndex = idx,
                        candidate = cand
                    )
                },
                onEndCall = { elapsed -> viewModel.endCall(liveCall, elapsed) },
                onPushMedia = { videoFrame, audioChunk, audioSeq ->
                    viewModel.pushCallMedia(
                        callId = liveCall.id,
                        isCaller = liveCall.callerId == account.userId,
                        videoFrameBase64 = videoFrame,
                        audioChunkBase64 = audioChunk,
                        audioSeq = audioSeq
                    )
                }
            )
        }

        // Incoming Ringing Call Full-Screen Overlay
        if (activeCall == null) {
            incomingRingingCall?.let { ringing ->
                IncomingCallOverlay(
                    call = ringing,
                    onAccept = { viewModel.answerCall(ringing.id, true) },
                    onDecline = { viewModel.answerCall(ringing.id, false) }
                )
            }
        }

        // Full-Screen Profile Image Viewer Dialog
        fullScreenAvatar?.let { payload ->
            FullScreenAvatarDialog(
                payload = payload,
                onDismiss = viewModel::dismissFullScreenAvatar
            )
        }

        // Long-Press Chat Options Dialog
        longPressedChat?.let { target ->
            val otherUid = target.memberUids.firstOrNull { it != account.userId }.orEmpty()
            val title = if (target.isGroup) target.groupName.ifBlank { "Group" }
            else (profilesByUid[otherUid]?.fullName ?: target.memberNames[otherUid] ?: "Friend")
            val opt = DisappearingOption.fromDurationMs(
                if (target.disappearingDurationMs > 0L) target.disappearingDurationMs else effectiveGlobalDisappearingMs
            )
            ChatLongPressActionDialog(
                chatTitle = title,
                isGroup = target.isGroup,
                isLocked = target.isLockedFor(account.userId),
                isArchived = target.isArchivedFor(account.userId),
                disappearingOption = opt,
                onToggleLock = { requestLockOrUnlockChat(target) },
                onOpenDisappearing = { disappearingTargetChat = target },
                onToggleArchive = {
                    viewModel.setChatArchived(target, !target.isArchivedFor(account.userId))
                },
                onOpenChatSettings = { settingsTargetChat = target },
                onDeleteChat = { viewModel.deleteChatForMe(target) },
                onDismiss = { longPressedChat = null }
            )
        }

        // Chat Settings Dialog
        settingsTargetChat?.let { target ->
            val otherUid = target.memberUids.firstOrNull { it != account.userId }.orEmpty()
            val title = if (target.isGroup) target.groupName.ifBlank { "Group" }
            else (profilesByUid[otherUid]?.fullName ?: target.memberNames[otherUid] ?: "Friend")
            val opt = DisappearingOption.fromDurationMs(
                if (target.disappearingDurationMs > 0L) target.disappearingDurationMs else effectiveGlobalDisappearingMs
            )
            ChatSettingsDialog(
                chatTitle = title,
                isGroup = target.isGroup,
                isLocked = target.isLockedFor(account.userId),
                isArchived = target.isArchivedFor(account.userId),
                disappearingOption = opt,
                onToggleLock = { requestLockOrUnlockChat(target) },
                onToggleArchive = {
                    viewModel.setChatArchived(target, !target.isArchivedFor(account.userId))
                },
                onOpenDisappearing = { disappearingTargetChat = target },
                onRemoveFriend = if (target.isGroup) null else {
                    { viewModel.removeFriend(otherUid, title) }
                },
                onDismiss = { settingsTargetChat = null }
            )
        }

        // Per-Chat Disappearing Messages Picker
        disappearingTargetChat?.let { target ->
            DisappearingTimerDialog(
                title = "Chat Disappearing Messages",
                currentDurationMs = target.disappearingDurationMs,
                onSelect = { opt -> viewModel.setChatDisappearing(target, opt) },
                onDismiss = { disappearingTargetChat = null }
            )
        }

        // Global Disappearing Messages Picker (Settings)
        if (showGlobalDisappearingDialog) {
            DisappearingTimerDialog(
                title = "All Chats Disappearing Timer",
                currentDurationMs = effectiveGlobalDisappearingMs,
                onSelect = { opt -> viewModel.setGlobalDisappearing(opt) },
                onDismiss = { showGlobalDisappearingDialog = false }
            )
        }

        // Create Group Dialog
        if (showCreateGroupDialog) {
            CreateGroupDialog(
                friends = connectedFriendProfiles,
                onCreateGroup = { name, members ->
                    showCreateGroupDialog = false
                    viewModel.createGroupChat(name, members) { newChatId ->
                        viewModel.openChat(newChatId)
                    }
                },
                onDismiss = { showCreateGroupDialog = false }
            )
        }

        // PIN Lock Dialog (Set / Unlock / Reset)
        pinDialogMode?.let { mode ->
            PinLockDialog(
                mode = mode,
                hint = chatLockHint,
                onVerifyPin = { pin -> viewModel.verifyChatLockPin(pin) },
                onSaveNewPin = { pin, hint -> viewModel.saveChatLockPin(pin, hint) },
                onResetPin = viewModel::resetChatLockPin,
                onUnlocked = {
                    val action = pendingPinSuccessAction
                    pinDialogMode = null
                    pendingPinSuccessAction = null
                    action?.invoke()
                },
                onDismiss = {
                    pinDialogMode = null
                    pendingPinSuccessAction = null
                }
            )
        }
        // Floating Frosted-Glass Top-of-Screen Heads-Up Message Banner
        AnimatedVisibility(
            visible = inAppBanner != null && activeCall == null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            inAppBanner?.let { banner ->
                InAppHeadsUpBanner(
                    banner = banner,
                    onClick = {
                        viewModel.dismissInAppBanner()
                        if (!banner.chatId.isNullOrBlank()) {
                            secondaryPage = SecondaryPage.NONE
                            currentTab = HomeTab.CHATS
                            viewModel.restoreAndOpenChat(banner.chatId)
                        } else if (banner.type == "request") {
                            secondaryPage = SecondaryPage.NONE
                            currentTab = HomeTab.PEOPLE
                        }
                    },
                    onDismiss = viewModel::dismissInAppBanner
                )
            }
        }
    }
}

@Composable
private fun InAppHeadsUpBanner(
    banner: InAppBannerAlert,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassSurface(
        shape = RoundedCornerShape(22.dp),
        strong = true,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("in_app_heads_up_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = banner.senderName,
                avatarData = banner.avatarData,
                size = 44.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = banner.senderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = "NOW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = banner.previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_heads_up_banner")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss notification banner",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Classic clean edge-to-edge Material 3 solid bottom navigation bar with 5 tabs:
 * Chats, Stories, People, Calls, and Settings (48dp+ touch targets).
 */
@Composable
private fun ClassicBottomNavBar(
    currentTab: HomeTab,
    pendingRequestCount: Int,
    onSelectTab: (HomeTab) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    val label = when (tab) {
                        HomeTab.CHATS -> "Chats"
                        HomeTab.STORIES -> "Stories"
                        HomeTab.PEOPLE -> "People"
                        HomeTab.CALLS -> "Calls"
                        HomeTab.SETTINGS -> "Settings"
                    }
                    Surface(
                        onClick = { onSelectTab(tab) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .size(width = 64.dp, height = 50.dp)
                            .testTag("nav_tab_${tab.name.lowercase()}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (tab == HomeTab.PEOPLE && pendingRequestCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = CoralWarm) {
                                            Text(pendingRequestCount.toString())
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = label,
                                        tint = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = tabIcon(tab, selected),
                                    contentDescription = label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: HomeTab, selected: Boolean): ImageVector {
    return when (tab) {
        HomeTab.CHATS -> if (selected) Icons.Rounded.ChatBubble else Icons.Outlined.ChatBubbleOutline
        HomeTab.STORIES -> if (selected) Icons.Rounded.AutoStories else Icons.Outlined.AutoStories
        HomeTab.PEOPLE -> if (selected) Icons.Rounded.Group else Icons.Outlined.Group
        HomeTab.CALLS -> if (selected) Icons.Rounded.Call else Icons.Outlined.Call
        HomeTab.SETTINGS -> if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings
    }
}
