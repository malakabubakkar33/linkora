package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.core.AttachmentCodec
import com.example.core.ImageCodec
import com.example.core.LocationHelper
import com.example.core.PickedFileAttachment
import com.example.core.ResolvedLocation
import com.example.core.VoiceNotePlayer
import com.example.core.VoiceNoteRecorder
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.ChatWallpaperStyle
import com.example.data.model.DisappearingOption
import com.example.data.model.PublicProfile
import com.example.ui.components.GlassSurface
import com.example.ui.components.LinkoraGlassBackdrop
import com.example.ui.components.MessageStatusTicks
import com.example.ui.components.TypingDotsInline
import com.example.ui.components.TypingIndicatorBubble
import com.example.ui.components.UserAvatar
import com.example.ui.components.VoiceNoteBubbleContent
import com.example.ui.components.formatDuration
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.AquaAccent
import com.example.ui.theme.AquaBright
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralWarm
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.MissedRed
import com.example.ui.theme.OnlineGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class ChatFilterChip {
    ALL,
    GROUPS
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatsTabScreen(
    currentUserId: String,
    chats: List<ChatThread>,
    profilesByUid: Map<String, PublicProfile>,
    archivedCount: Int,
    lockedCount: Int,
    blockedUids: Set<String> = emptySet(),
    onOpenChat: (ChatThread) -> Unit,
    onRestoreAndOpenChat: (ChatThread) -> Unit,
    onArchiveChat: (ChatThread) -> Unit,
    onDeleteChat: (ChatThread) -> Unit,
    onLongPressChat: (ChatThread) -> Unit,
    onOpenArchivedPage: () -> Unit,
    onOpenLockedPage: () -> Unit,
    onOpenCreateGroup: () -> Unit,
    onOpenAddPeople: () -> Unit,
    onOpenLinkoraAi: () -> Unit,
    onViewAvatar: (name: String, username: String, avatar: String?) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(ChatFilterChip.ALL) }
    var swipedChatId by remember { mutableStateOf<String?>(null) }

    // Visible chats on main home list exclude archived, locked, and deleted/hidden
    val visibleMainChats = remember(chats, currentUserId, activeFilter) {
        chats.filter { chat ->
            !chat.isArchivedFor(currentUserId) &&
                !chat.isLockedFor(currentUserId) &&
                !chat.isHiddenFor(currentUserId) &&
                (activeFilter == ChatFilterChip.ALL || chat.isGroup)
        }
    }

    // When searching, also surface hidden/deleted friend chats so the user can jump back without re-adding
    val searchResults = remember(chats, visibleMainChats, searchQuery, profilesByUid, currentUserId) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) {
            visibleMainChats
        } else {
            chats.filter { chat ->
                !chat.isLockedFor(currentUserId) && matchesQuery(chat, currentUserId, profilesByUid, q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Frosted Glass Search Bar directly under top header
        GlassSurface(
            shape = RoundedCornerShape(22.dp),
            strong = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search chats, groups or friends...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chats_search_bar")
            )
        }

        Spacer(Modifier.height(10.dp))

        // Compact frosted glass filter pills under the search bar
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                CompactFilterPill(
                    label = "All",
                    icon = null,
                    selected = activeFilter == ChatFilterChip.ALL,
                    testTag = "filter_pill_all",
                    onClick = { activeFilter = ChatFilterChip.ALL }
                )
            }
            item {
                CompactFilterPill(
                    label = if (archivedCount > 0) "Archived ($archivedCount)" else "Archived",
                    icon = Icons.Rounded.Archive,
                    selected = false,
                    testTag = "filter_pill_archived",
                    onClick = onOpenArchivedPage
                )
            }
            item {
                CompactFilterPill(
                    label = if (lockedCount > 0) "Locked ($lockedCount)" else "Locked",
                    icon = Icons.Rounded.Lock,
                    selected = false,
                    testTag = "filter_pill_locked",
                    onClick = onOpenLockedPage
                )
            }
            item {
                CompactFilterPill(
                    label = "Groups",
                    icon = Icons.Rounded.Group,
                    selected = activeFilter == ChatFilterChip.GROUPS,
                    testTag = "filter_pill_groups",
                    onClick = {
                        activeFilter = if (activeFilter == ChatFilterChip.GROUPS) ChatFilterChip.ALL else ChatFilterChip.GROUPS
                    }
                )
            }
            item {
                CompactFilterPill(
                    label = "New Group",
                    icon = Icons.Rounded.GroupAdd,
                    selected = false,
                    accent = true,
                    testTag = "filter_pill_new_group",
                    onClick = onOpenCreateGroup
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Linkora AI Companion Glass-Rim Banner Card
        Card(
            onClick = onOpenLinkoraAi,
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = if (isDark) 0.22f else 0.45f), Color.White.copy(alpha = 0.05f))
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .testTag("open_linkora_ai_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(IndigoDeep, CobaltPrimary, AquaAccent)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Linkora AI",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Linkora AI",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Roman Urdu, English & all languages • Smart Assistant",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.22f)
                ) {
                    Text(
                        text = "Ask",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (activeFilter == ChatFilterChip.GROUPS) Icons.Rounded.GroupAdd else Icons.Rounded.PersonAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "No matching chats found"
                                activeFilter == ChatFilterChip.GROUPS -> "No Groups Yet"
                                else -> "Start Connecting on Linkora"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "Try another name or @username, or search in the People tab to add new friends."
                                activeFilter == ChatFilterChip.GROUPS -> "Create a group with your friends to chat, send voice notes, photos, and files together."
                                else -> "Discover real Linkora users, send a friend request, and start encrypted chats or WebRTC calls."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = if (activeFilter == ChatFilterChip.GROUPS) onOpenCreateGroup else onOpenAddPeople,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CobaltPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("add_people_button")
                        ) {
                            Icon(
                                imageVector = if (activeFilter == ChatFilterChip.GROUPS) Icons.Rounded.GroupAdd else Icons.Rounded.PersonAdd,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = if (activeFilter == ChatFilterChip.GROUPS) "Create Group" else "Add People",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(searchResults, key = { it.id }) { chat ->
                    val otherUid = chat.memberUids.firstOrNull { it != currentUserId }.orEmpty()
                    val liveProfile = profilesByUid[otherUid]
                    val title = if (chat.isGroup) {
                        chat.groupName.ifBlank { "Linkora Group" }
                    } else {
                        liveProfile?.fullName ?: chat.memberNames[otherUid] ?: "Linkora User"
                    }
                    val username = if (chat.isGroup) {
                        "${chat.memberUids.size} members"
                    } else {
                        liveProfile?.username ?: chat.memberUsernames[otherUid].orEmpty()
                    }
                    val avatar = if (chat.isGroup) null else (liveProfile?.avatarData ?: chat.memberAvatars[otherUid])
                    val isOnline = !chat.isGroup && liveProfile?.online == true && otherUid !in blockedUids
                    val isOtherTyping = chat.typingUsers.any { (uid, typing) -> uid != currentUserId && typing } && otherUid !in blockedUids
                    val isMine = chat.lastMessageSenderId == currentUserId
                    val isHiddenDeleted = chat.isHiddenFor(currentUserId)
                    val isBlocked = !chat.isGroup && otherUid in blockedUids
                    val isSwiped = swipedChatId == chat.id

                    SwipeableChatRow(
                        chat = chat,
                        title = title,
                        subtitleHandle = username,
                        avatar = avatar,
                        isOnline = isOnline,
                        isOtherTyping = isOtherTyping,
                        isMine = isMine,
                        isHiddenDeleted = isHiddenDeleted,
                        isBlocked = isBlocked,
                        isSwipedOpen = isSwiped,
                        onSwipeChanged = { open -> swipedChatId = if (open) chat.id else null },
                        onClick = {
                            swipedChatId = null
                            if (isHiddenDeleted) onRestoreAndOpenChat(chat) else onOpenChat(chat)
                        },
                        onLongClick = {
                            swipedChatId = null
                            onLongPressChat(chat)
                        },
                        onArchive = {
                            swipedChatId = null
                            onArchiveChat(chat)
                        },
                        onDelete = {
                            swipedChatId = null
                            onDeleteChat(chat)
                        },
                        onViewAvatar = {
                            if (!chat.isGroup) onViewAvatar(title, username, avatar)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactFilterPill(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    accent: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    val accentTint = when {
        selected -> CobaltPrimary
        accent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        else -> null
    }
    val contentColor = when {
        selected -> Color.White
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    GlassSurface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        accentTint = accentTint,
        modifier = Modifier
            .height(36.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (selected || accent) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableChatRow(
    chat: ChatThread,
    title: String,
    subtitleHandle: String,
    avatar: String?,
    isOnline: Boolean,
    isOtherTyping: Boolean,
    isMine: Boolean,
    isHiddenDeleted: Boolean,
    isBlocked: Boolean,
    isSwipedOpen: Boolean,
    onSwipeChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onViewAvatar: () -> Unit
) {
    val revealWidthDp = 136.dp
    var dragDelta by remember(chat.id) { mutableFloatStateOf(0f) }
    val targetOffset by animateDpAsState(
        targetValue = if (isSwipedOpen) -revealWidthDp else 0.dp,
        label = "chat_swipe_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
    ) {
        // Revealed right-side Archive + Delete actions
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(revealWidthDp)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onArchive,
                shape = RoundedCornerShape(14.dp),
                color = OnlineGreen,
                modifier = Modifier
                    .weight(1f)
                    .height(62.dp)
                    .testTag("swipe_archive_${chat.id}")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.Archive, contentDescription = "Archive", tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("Archive", style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                }
            }
            Surface(
                onClick = onDelete,
                shape = RoundedCornerShape(14.dp),
                color = MissedRed,
                modifier = Modifier
                    .weight(1f)
                    .height(62.dp)
                    .testTag("swipe_delete_${chat.id}")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("Delete", style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                }
            }
        }

        // Foreground Frosted Glass Chat Row
        GlassSurface(
            shape = RoundedCornerShape(22.dp),
            strong = false,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(targetOffset.roundToPx(), 0) }
                .pointerInput(chat.id, isSwipedOpen) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragDelta = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragDelta += dragAmount
                        },
                        onDragEnd = {
                            if (dragDelta < -55f) {
                                onSwipeChanged(true)
                            } else if (dragDelta > 45f) {
                                onSwipeChanged(false)
                            }
                            dragDelta = 0f
                        }
                    )
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .testTag("chat_item_${chat.id}")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    name = title,
                    avatarData = avatar,
                    size = 54.dp,
                    showOnlineIndicator = !chat.isGroup && !isBlocked,
                    isOnline = isOnline,
                    onClick = onViewAvatar
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (chat.isGroup) {
                                Icon(
                                    imageVector = Icons.Rounded.Group,
                                    contentDescription = "Group",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isBlocked) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MissedRed.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "Blocked",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MissedRed,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (chat.disappearingDurationMs > 0L) {
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = "Disappearing messages active",
                                    tint = AquaAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatTimeShort(chat.lastMessageAt ?: chat.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (isHiddenDeleted) {
                        Text(
                            text = "Tap to resume chat with @$subtitleHandle (already friends)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (isOtherTyping) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TypingDotsInline(dotColor = OnlineGreen, dotSize = 5.dp)
                            Text(
                                text = "typing...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnlineGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isMine) {
                                MessageStatusTicks(
                                    status = chat.lastMessageStatus,
                                    tintDefault = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            if (chat.lastMessageType == "voice") {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = "Voice message",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = chat.lastMessageText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun matchesQuery(
    chat: ChatThread,
    currentUserId: String,
    profilesByUid: Map<String, PublicProfile>,
    q: String
): Boolean {
    if (chat.isGroup && chat.groupName.lowercase().contains(q)) return true
    val otherUid = chat.memberUids.firstOrNull { it != currentUserId }.orEmpty()
    val profile = profilesByUid[otherUid]
    val name = (profile?.fullName ?: chat.memberNames[otherUid].orEmpty()).lowercase()
    val handle = (profile?.username ?: chat.memberUsernames[otherUid].orEmpty()).lowercase()
    return name.contains(q) || handle.contains(q) || chat.lastMessageText.lowercase().contains(q)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    currentUserId: String,
    chat: ChatThread,
    otherProfile: PublicProfile?,
    messages: List<ChatMessage>,
    effectiveDisappearingMs: Long,
    isOtherBlocked: Boolean = false,
    chatWallpaper: ChatWallpaperStyle = ChatWallpaperStyle.CLASSIC_GLASS,
    enterKeySends: Boolean = false,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onSendVoice: (base64: String, durationSec: Int) -> Unit,
    onSendPhoto: (base64: String) -> Unit,
    onSendVideo: (thumbnailBase64: String) -> Unit,
    onSendFileOrDocument: (PickedFileAttachment, Boolean) -> Unit,
    onSendLocation: (ResolvedLocation) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onFeedback: (String) -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onOpenDisappearingPicker: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeleteChat: () -> Unit,
    onDeleteMessageForMe: (ChatMessage) -> Unit,
    onDeleteMessageForEveryone: (ChatMessage) -> Unit,
    onDeleteMessagePermanently: (ChatMessage) -> Unit,
    onBlockUser: ((uid: String, name: String) -> Unit)? = null,
    onUnblockUser: ((uid: String, name: String) -> Unit)? = null,
    onRemoveFriend: ((uid: String, name: String) -> Unit)?,
    onViewAvatar: (name: String, username: String, avatar: String?) -> Unit
) {
    BackHandler {
        onTypingChanged(false)
        onBack()
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isDark = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    val otherUid = chat.memberUids.firstOrNull { it != currentUserId }.orEmpty()
    val headerTitle = if (chat.isGroup) {
        chat.groupName.ifBlank { "Linkora Group" }
    } else {
        otherProfile?.fullName ?: chat.memberNames[otherUid] ?: "Friend"
    }
    val otherUsername = otherProfile?.username ?: chat.memberUsernames[otherUid].orEmpty()
    val otherAvatar = if (chat.isGroup) null else (otherProfile?.avatarData ?: chat.memberAvatars[otherUid])
    val isOnline = !chat.isGroup && !isOtherBlocked && otherProfile?.online == true
    val isOtherTyping = !isOtherBlocked && chat.typingUsers.any { (uid, typing) -> uid != currentUserId && typing }

    var messageText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSec by remember { mutableIntStateOf(0) }
    var playingMsgId by remember { mutableStateOf<String?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var confirmRemoveFriend by remember { mutableStateOf(false) }
    var confirmBlockUser by remember { mutableStateOf(false) }
    var fullScreenPhotoData by remember { mutableStateOf<String?>(null) }
    var longPressedMessage by remember { mutableStateOf<ChatMessage?>(null) }

    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }
    val voicePlayer = remember(context) { VoiceNotePlayer(context) }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) {
        onDispose {
            onTypingChanged(false)
            voiceRecorder.stopAndDiscard()
            voicePlayer.stop()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSec = 0
            while (isRecording) {
                delay(1000L)
                recordingSec++
            }
        }
    }

    LaunchedEffect(messages.size, isOtherTyping) {
        val total = messages.size + if (isOtherTyping) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    // Mic permission for direct user-to-user Voice Note recording
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (voiceRecorder.startRecording()) {
                isRecording = true
            } else {
                onFeedback("Could not start microphone. Please try again.")
            }
        } else {
            onFeedback("Microphone permission is needed to send voice messages.")
        }
    }

    // Photo picker (zero-permission Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = ImageCodec.compressUriToBase64(context, uri, maxDimension = 640, quality = 74)
                if (encoded != null) {
                    onSendPhoto(encoded)
                } else {
                    onFeedback("Could not process selected photo.")
                }
            }
        }
    }

    // Video picker (zero-permission Android Photo Picker)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val thumb = ImageCodec.extractVideoThumbnailBase64(context, uri, maxDimension = 560)
                if (thumb != null) {
                    onSendVideo(thumb)
                } else {
                    onFeedback("Could not read selected video.")
                }
            }
        }
    }

    // Generic File picker (SAF)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val picked = AttachmentCodec.readFileAttachment(context, uri)
                if (picked != null) {
                    onSendFileOrDocument(picked, false)
                } else {
                    onFeedback("Could not read selected file.")
                }
            }
        }
    }

    // Document picker (PDF / DOC / TXT / Office via SAF)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val picked = AttachmentCodec.readFileAttachment(context, uri)
                if (picked != null) {
                    onSendFileOrDocument(picked, true)
                } else {
                    onFeedback("Could not read selected document.")
                }
            }
        }
    }

    // Location permission + live GPS fetch
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                val loc = LocationHelper.fetchCurrentLocation(context)
                if (loc != null) {
                    onSendLocation(loc)
                } else {
                    onFeedback("Enable GPS/Location on your device and try again.")
                }
            }
        } else {
            onFeedback("Location permission is needed to share your location.")
        }
    }

    fun dispatchTextIfReady() {
        val outgoing = messageText.trim()
        if (outgoing.isNotBlank()) {
            messageText = ""
            onTypingChanged(false)
            onSendText(outgoing)
        }
    }

    LinkoraGlassBackdrop(wallpaper = chatWallpaper) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Clean Classic Solid Chat Header
            GlassSurface(
                shape = RoundedCornerShape(0.dp),
                strong = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to chats",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    UserAvatar(
                        name = headerTitle,
                        avatarData = otherAvatar,
                        size = 42.dp,
                        showOnlineIndicator = !chat.isGroup && !isOtherBlocked,
                        isOnline = isOnline,
                        onClick = {
                            if (!chat.isGroup) onViewAvatar(headerTitle, otherUsername, otherAvatar)
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (!chat.isGroup) onViewAvatar(headerTitle, otherUsername, otherAvatar)
                            }
                    ) {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isOtherBlocked) {
                            Text(
                                text = "Blocked user",
                                style = MaterialTheme.typography.labelMedium,
                                color = MissedRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (isOtherTyping) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                TypingDotsInline(dotColor = OnlineGreen, dotSize = 5.dp)
                                Text(
                                    text = "typing...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OnlineGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            val subtitle = if (chat.isGroup) {
                                "${chat.memberUids.size} members • Encrypted"
                            } else if (isOnline) {
                                "Online • WebRTC Ready"
                            } else {
                                "Offline • @$otherUsername"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (!chat.isGroup && !isOtherBlocked) {
                        IconButton(
                            onClick = onStartAudioCall,
                            modifier = Modifier.testTag("chat_audio_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Start WebRTC audio call",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onStartVideoCall,
                            modifier = Modifier.testTag("chat_video_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Start WebRTC video call",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { showHeaderMenu = true },
                            modifier = Modifier.testTag("chat_detail_overflow")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Chat options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showHeaderMenu,
                            onDismissRequest = { showHeaderMenu = false }
                        ) {
                            val activeOpt = DisappearingOption.fromDurationMs(effectiveDisappearingMs)
                            DropdownMenuItem(
                                text = { Text("Disappearing (${activeOpt.shortLabel})") },
                                leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                                onClick = {
                                    showHeaderMenu = false
                                    onOpenDisappearingPicker()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (chat.isLockedFor(currentUserId)) "Unlock Chat" else "Lock Chat") },
                                leadingIcon = {
                                    Icon(
                                        if (chat.isLockedFor(currentUserId)) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    onToggleLock()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (chat.isArchivedFor(currentUserId)) "Unarchive Chat" else "Archive Chat") },
                                leadingIcon = {
                                    Icon(
                                        if (chat.isArchivedFor(currentUserId)) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showHeaderMenu = false
                                    onToggleArchive()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Chat") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = {
                                    showHeaderMenu = false
                                    onDeleteChat()
                                }
                            )
                            if (!chat.isGroup && (onBlockUser != null || onUnblockUser != null)) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (isOtherBlocked) "Unblock User" else "Block User",
                                            color = if (isOtherBlocked) OnlineGreen else MissedRed
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Block,
                                            contentDescription = null,
                                            tint = if (isOtherBlocked) OnlineGreen else MissedRed
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        if (isOtherBlocked) {
                                            onUnblockUser?.invoke(otherUid, headerTitle)
                                        } else {
                                            confirmBlockUser = true
                                        }
                                    }
                                )
                            }
                            if (!chat.isGroup && onRemoveFriend != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove Friend", color = MissedRed) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.PersonRemove, contentDescription = null, tint = MissedRed)
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        confirmRemoveFriend = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Active Disappearing Messages indicator pill
            if (effectiveDisappearingMs > 0L) {
                val opt = DisappearingOption.fromDurationMs(effectiveDisappearingMs)
                GlassSurface(
                    onClick = onOpenDisappearingPicker,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Disappearing messages ON (${opt.label}) • Older chats auto-refresh",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Messages list with hold-tap (long-press) deletion menu on every bubble
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == currentUserId
                    val bubbleShape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isMine) 20.dp else 6.dp,
                        bottomEnd = if (isMine) 6.dp else 20.dp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        val bubbleContent: @Composable () -> Unit = {
                            Column(
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { longPressedMessage = msg }
                                    )
                                    .padding(horizontal = 13.dp, vertical = 10.dp)
                                    .testTag("message_bubble_${msg.id}")
                            ) {
                                if (chat.isGroup && !isMine && !msg.deletedForEveryone) {
                                    val senderDisplay = msg.senderName.ifBlank { chat.memberNames[msg.senderId] ?: "Member" }
                                    Text(
                                        text = senderDisplay,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(3.dp))
                                }

                                if (msg.deletedForEveryone) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Block,
                                            contentDescription = "Deleted message",
                                            tint = if (isMine) Color.White.copy(alpha = 0.72f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "This message was deleted",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                            color = if (isMine) Color.White.copy(alpha = 0.78f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    when (msg.messageType) {
                                        "voice" -> {
                                            if (!msg.audioData.isNullOrBlank()) {
                                                VoiceNoteBubbleContent(
                                                    durationSec = msg.audioDurationSec,
                                                    isPlaying = playingMsgId == msg.id,
                                                    isMine = isMine,
                                                    onTogglePlay = {
                                                        voicePlayer.playOrToggle(msg.id, msg.audioData) { active ->
                                                            playingMsgId = active
                                                        }
                                                    }
                                                )
                                            } else {
                                                Text(
                                                    text = msg.text,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        "photo" -> {
                                            val bmp = remember(msg.mediaData) { ImageCodec.decodeBase64Bitmap(msg.mediaData) }
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = "Shared photo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(190.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .combinedClickable(
                                                            onClick = { fullScreenPhotoData = msg.mediaData },
                                                            onLongClick = { longPressedMessage = msg }
                                                        )
                                                )
                                                if (msg.text.isNotBlank() && msg.text != "📷 Photo") {
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(
                                                        text = msg.text,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = msg.text,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        "video" -> {
                                            val bmp = remember(msg.mediaData) { ImageCodec.decodeBase64Bitmap(msg.mediaData) }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(175.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color.Black.copy(alpha = 0.75f))
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (!msg.mediaData.isNullOrBlank()) fullScreenPhotoData = msg.mediaData
                                                        },
                                                        onLongClick = { longPressedMessage = msg }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (bmp != null) {
                                                    Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "Shared video preview",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.Black.copy(alpha = 0.55f),
                                                    modifier = Modifier.size(52.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.PlayCircle,
                                                            contentDescription = "Video",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(34.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isMine) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        "file", "document" -> {
                                            val isDoc = msg.messageType == "document"
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (isMine) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.combinedClickable(
                                                    onClick = {
                                                        val b64 = msg.fileData
                                                        if (!b64.isNullOrBlank()) {
                                                            val saved = AttachmentCodec.saveBase64ToCacheFile(
                                                                context,
                                                                msg.fileName ?: "attachment",
                                                                b64
                                                            )
                                                            if (saved != null) {
                                                                onFeedback("Saved ${msg.fileName ?: "file"} (${AttachmentCodec.formatFileSize(msg.fileSizeBytes)})")
                                                            }
                                                        } else {
                                                            onFeedback("${msg.fileName ?: "File"} (${AttachmentCodec.formatFileSize(msg.fileSizeBytes)})")
                                                        }
                                                    },
                                                    onLongClick = { longPressedMessage = msg }
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = if (isDoc) CoralWarm else AquaAccent,
                                                        modifier = Modifier.size(38.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = if (isDoc) Icons.Rounded.Description else Icons.AutoMirrored.Rounded.InsertDriveFile,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = msg.fileName ?: msg.text,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${if (isDoc) "Document" else "File"} • ${AttachmentCodec.formatFileSize(msg.fileSizeBytes)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isMine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        "location" -> {
                                            val lat = msg.locationLat ?: 0.0
                                            val lng = msg.locationLng ?: 0.0
                                            val label = msg.locationLabel ?: msg.text
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (isMine) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.combinedClickable(
                                                    onClick = {
                                                        try {
                                                            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
                                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                                        } catch (_: Exception) {
                                                            onFeedback("Location: $lat, $lng")
                                                        }
                                                    },
                                                    onLongClick = { longPressedMessage = msg }
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = OnlineGreen,
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.LocationOn,
                                                                contentDescription = "Shared location",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "Tap to open in Maps",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isMine) AquaBright else MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        else -> {
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = formatTimeShort(msg.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMine) Color.White.copy(alpha = 0.78f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isMine && !msg.deletedForEveryone) {
                                        MessageStatusTicks(
                                            status = msg.status,
                                            tintDefault = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }

                        if (isMine) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 296.dp)
                                    .clip(bubbleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = if (isDark) {
                                                listOf(Color(0xFF3B5BDB), Color(0xFF4F46E5))
                                            } else {
                                                listOf(CobaltPrimary, Color(0xFF4338CA))
                                            }
                                        )
                                    )
                                    .border(
                                        width = 0.8.dp,
                                        color = Color.White.copy(alpha = 0.22f),
                                        shape = bubbleShape
                                    )
                            ) {
                                bubbleContent()
                            }
                        } else {
                            GlassSurface(
                                shape = bubbleShape,
                                strong = true,
                                modifier = Modifier.widthIn(max = 296.dp)
                            ) {
                                bubbleContent()
                            }
                        }
                    }
                }
                if (isOtherTyping) {
                    item(key = "typing_indicator") {
                        TypingIndicatorBubble(senderName = headerTitle)
                    }
                }
            }

            // Frosted Glass Drop-Up Attachment Panel
            AnimatedVisibility(
                visible = showAttachSheet && !isRecording && !isOtherBlocked,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(24.dp),
                    strong = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("chat_attachment_dropup")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AttachmentGridItem(
                            icon = Icons.Rounded.Image,
                            label = "Photos",
                            color = MaterialTheme.colorScheme.primary,
                            testTag = "attach_photos",
                            onClick = {
                                showAttachSheet = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        AttachmentGridItem(
                            icon = Icons.Rounded.Videocam,
                            label = "Video",
                            color = CoralWarm,
                            testTag = "attach_video",
                            onClick = {
                                showAttachSheet = false
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                        )
                        AttachmentGridItem(
                            icon = Icons.Rounded.AttachFile,
                            label = "Files",
                            color = AquaAccent,
                            testTag = "attach_files",
                            onClick = {
                                showAttachSheet = false
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        AttachmentGridItem(
                            icon = Icons.Rounded.Description,
                            label = "Docs",
                            color = Color(0xFF8B5CF6),
                            testTag = "attach_documents",
                            onClick = {
                                showAttachSheet = false
                                documentPickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "text/plain",
                                        "application/msword",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    )
                                )
                            }
                        )
                        AttachmentGridItem(
                            icon = Icons.Rounded.LocationOn,
                            label = "Location",
                            color = OnlineGreen,
                            testTag = "attach_location",
                            onClick = {
                                showAttachSheet = false
                                val hasFine = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasFine || hasCoarse) {
                                    scope.launch {
                                        val loc = LocationHelper.fetchCurrentLocation(context)
                                        if (loc != null) {
                                            onSendLocation(loc)
                                        } else {
                                            onFeedback("Enable GPS/Location on your device to share location.")
                                        }
                                    }
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Composer OR Blocked User Frosted Banner
            if (isOtherBlocked && !chat.isGroup) {
                GlassSurface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    strong = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blocked_chat_banner")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Block, contentDescription = null, tint = MissedRed, modifier = Modifier.size(20.dp))
                            Text(
                                text = "You blocked $headerTitle",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Unblock this contact to resume messaging and WebRTC calls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onUnblockUser?.invoke(otherUid, headerTitle) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CobaltPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("unblock_user_banner_button")
                        ) {
                            Text("Unblock $headerTitle")
                        }
                    }
                }
            } else {
                GlassSurface(
                    shape = RoundedCornerShape(0.dp),
                    strong = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRecording) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    voiceRecorder.stopAndDiscard()
                                    isRecording = false
                                }
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Cancel recording", tint = MissedRed)
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MissedRed)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Recording voice note  ${formatDuration(recordingSec.toLong())}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                onClick = {
                                    val encoded = voiceRecorder.stopAndEncode()
                                    isRecording = false
                                    if (encoded != null) {
                                        onSendVoice(encoded.first, encoded.second)
                                    } else {
                                        onFeedback("Voice note was too short. Record for at least 1 second.")
                                    }
                                },
                                shape = CircleShape,
                                color = CobaltPrimary,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("send_voice_note_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Send voice note",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // '+' Attachment Button
                            Surface(
                                onClick = { showAttachSheet = !showAttachSheet },
                                shape = CircleShape,
                                color = if (showAttachSheet) CobaltPrimary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("chat_plus_attach_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (showAttachSheet) Icons.Rounded.Close else Icons.Rounded.Add,
                                        contentDescription = "Attach photos, video, files, documents or location",
                                        tint = if (showAttachSheet) Color.White else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { next ->
                                    if (next.length <= 2000) {
                                        messageText = next
                                        onTypingChanged(next.isNotBlank())
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = "Message...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = if (enterKeySends) ImeAction.Send else ImeAction.Default
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (enterKeySends) dispatchTextIfReady()
                                    }
                                ),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.65f else 0.52f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.50f else 0.42f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_message_input")
                            )

                            Spacer(Modifier.width(6.dp))

                            // Voice Note Recorder Icon RIGHT BESIDE Send button
                            Surface(
                                onClick = {
                                    showAttachSheet = false
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        if (voiceRecorder.startRecording()) {
                                            isRecording = true
                                        } else {
                                            onFeedback("Could not access microphone right now.")
                                        }
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                shape = CircleShape,
                                color = AquaAccent,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("record_voice_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "Record voice message",
                                        tint = Color.White,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(6.dp))

                            // Send Message Button right next to Voice Note Mic
                            Surface(
                                onClick = { dispatchTextIfReady() },
                                shape = CircleShape,
                                color = if (messageText.isNotBlank()) CobaltPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("send_message_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Send message",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Message Long-Press (Hold Tap) Action Dialog — Delete for Me / Delete for Everyone / Delete Permanently
    longPressedMessage?.let { targetMsg ->
        val isMine = targetMsg.senderId == currentUserId
        AlertDialog(
            onDismissRequest = { longPressedMessage = null },
            title = {
                Text(
                    text = "Message Options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!targetMsg.deletedForEveryone && targetMsg.text.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = targetMsg.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        MessageOptionActionRow(
                            icon = Icons.Rounded.ContentCopy,
                            title = "Copy Message",
                            subtitle = "Copy text to clipboard",
                            tint = MaterialTheme.colorScheme.primary,
                            testTag = "msg_action_copy",
                            onClick = {
                                clipboardManager.setText(AnnotatedString(targetMsg.text))
                                longPressedMessage = null
                                onFeedback("Message copied")
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    }

                    MessageOptionActionRow(
                        icon = Icons.Rounded.DeleteOutline,
                        title = "Delete for Me",
                        subtitle = "Removes this message from your view only",
                        tint = MaterialTheme.colorScheme.onSurface,
                        testTag = "msg_action_delete_for_me",
                        onClick = {
                            longPressedMessage = null
                            onDeleteMessageForMe(targetMsg)
                        }
                    )

                    if (isMine && !targetMsg.deletedForEveryone) {
                        MessageOptionActionRow(
                            icon = Icons.Rounded.Delete,
                            title = "Delete for Everyone",
                            subtitle = "Replaces content with 'This message was deleted'",
                            tint = CoralWarm,
                            testTag = "msg_action_delete_for_everyone",
                            onClick = {
                                longPressedMessage = null
                                onDeleteMessageForEveryone(targetMsg)
                            }
                        )
                    }

                    if (isMine) {
                        MessageOptionActionRow(
                            icon = Icons.Rounded.DeleteForever,
                            title = "Delete Permanently",
                            subtitle = "Completely erases this message with zero trace",
                            tint = MissedRed,
                            testTag = "msg_action_delete_permanently",
                            onClick = {
                                longPressedMessage = null
                                onDeleteMessagePermanently(targetMsg)
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { longPressedMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Block User Confirmation Dialog
    if (confirmBlockUser && onBlockUser != null) {
        AlertDialog(
            onDismissRequest = { confirmBlockUser = false },
            title = { Text("Block $headerTitle?") },
            text = {
                Text("Blocked users cannot ring your device or send you notifications. You can unblock them anytime from Settings → Blocked Users or this chat.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmBlockUser = false
                        onBlockUser(otherUid, headerTitle)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MissedRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_block_user_button")
                ) {
                    Text("Block User")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlockUser = false }) { Text("Cancel") }
            }
        )
    }

    // Remove Friend Confirmation Dialog
    if (confirmRemoveFriend && onRemoveFriend != null) {
        AlertDialog(
            onDismissRequest = { confirmRemoveFriend = false },
            title = { Text("Remove Friend?") },
            text = {
                Text("Remove $headerTitle from your friends? You will need to send a new friend request to connect again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemoveFriend = false
                        onRemoveFriend(otherUid, headerTitle)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MissedRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("Remove Friend")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveFriend = false }) { Text("Cancel") }
            }
        )
    }

    // Full-Screen Photo Lightbox
    fullScreenPhotoData?.let { photoB64 ->
        val bmp = remember(photoB64) { ImageCodec.decodeBase64Bitmap(photoB64) }
        Dialog(
            onDismissRequest = { fullScreenPhotoData = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullScreenPhotoData = null },
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Full screen shared image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                IconButton(
                    onClick = { fullScreenPhotoData = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun MessageOptionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = tint,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AttachmentGridItem(
    icon: ImageVector,
    label: String,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.16f),
            modifier = Modifier.size(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
