package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ImageCodec
import com.example.core.LiveCallAudioEngine
import com.example.core.WebRtcCallManager
import com.example.data.model.CallQualityMode
import com.example.data.model.CallSession
import com.example.data.model.FriendRequest
import com.example.data.model.IceCandidateSignal
import com.example.data.model.PublicProfile
import com.example.data.model.UserAccount
import com.example.ui.components.GlassSurface
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatDuration
import com.example.ui.theme.AquaAccent
import com.example.ui.theme.AquaBright
import com.example.ui.theme.CallGreen
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.MissedRed
import com.example.ui.theme.OnlineGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun PeopleTabScreen(
    currentUser: UserAccount,
    allProfiles: List<PublicProfile>,
    friendRequests: List<FriendRequest>,
    onSendRequest: (PublicProfile) -> Unit,
    onRespondRequest: (FriendRequest, Boolean) -> Unit,
    onStartChatWithFriend: (String) -> Unit,
    onRemoveFriend: (uid: String, name: String) -> Unit,
    onBlockUser: (uid: String, name: String) -> Unit,
    onUnblockUser: (uid: String, name: String) -> Unit,
    onCallUser: (uid: String, name: String, avatar: String?, callType: String) -> Unit,
    onViewAvatar: (name: String, username: String, avatar: String?) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var friendPendingRemoval by remember { mutableStateOf<PublicProfile?>(null) }
    var userPendingBlock by remember { mutableStateOf<PublicProfile?>(null) }

    val blockedSet = remember(currentUser.blockedUids) { currentUser.blockedUids.toSet() }

    val incomingPending = remember(friendRequests, currentUser.userId, blockedSet) {
        friendRequests.filter {
            it.toUid == currentUser.userId && it.status == "pending" && it.fromUid !in blockedSet
        }
    }
    val sentPendingUids = remember(friendRequests, currentUser.userId) {
        friendRequests.filter { it.fromUid == currentUser.userId && it.status == "pending" }
            .map { it.toUid }.toSet()
    }
    val acceptedUids = remember(currentUser.friendUids, friendRequests, currentUser.userId) {
        val fromReqs = friendRequests.filter { it.status == "accepted" }
            .map { if (it.fromUid == currentUser.userId) it.toUid else it.fromUid }
        (currentUser.friendUids + fromReqs).toSet()
    }

    val otherUsers = remember(allProfiles, currentUser.userId, searchQuery) {
        val q = searchQuery.trim().lowercase()
        allProfiles
            .filter { it.userId != currentUser.userId }
            .filter {
                q.isBlank() ||
                    it.fullName.lowercase().contains(q) ||
                    it.username.lowercase().contains(q)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        GlassSurface(
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search Linkora people by name or @username...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search people",
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
                    .testTag("people_search_bar")
            )
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (incomingPending.isNotEmpty()) {
                item {
                    Text(
                        text = "Friend Requests (${incomingPending.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(incomingPending, key = { it.id }) { req ->
                    GlassSurface(
                        shape = RoundedCornerShape(22.dp),
                        strong = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(
                                    name = req.fromName,
                                    avatarData = req.fromAvatar,
                                    size = 50.dp,
                                    onClick = { onViewAvatar(req.fromName, req.fromUsername, req.fromAvatar) }
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.fromName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "@${req.fromUsername} wants to connect",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onRespondRequest(req, true) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CobaltPrimary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("accept_request_${req.id}")
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Accept")
                                }
                                OutlinedButton(
                                    onClick = { onRespondRequest(req, false) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("decline_request_${req.id}")
                                ) {
                                    Text("Decline")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            item {
                Text(
                    text = "People on Linkora (${otherUsers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (otherUsers.isEmpty()) {
                item {
                    GlassSurface(
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No other users found yet. Invite friends to sign in to Linkora so you can connect, chat, and place WebRTC calls!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(otherUsers, key = { it.userId }) { person ->
                    val isFriend = person.userId in acceptedUids
                    val isRequested = person.userId in sentPendingUids
                    val isBlocked = person.userId in blockedSet
                    val hasIncoming = incomingPending.any { it.fromUid == person.userId }

                    GlassSurface(
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("person_card_${person.userId}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = person.fullName,
                                avatarData = person.avatarData,
                                size = 52.dp,
                                showOnlineIndicator = !isBlocked,
                                isOnline = person.online,
                                onClick = { onViewAvatar(person.fullName, person.username, person.avatarData) }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = person.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
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
                                }
                                Text(
                                    text = "@${person.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (person.bio.isNotBlank()) {
                                    Text(
                                        text = person.bio,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))

                            var menuOpen by remember(person.userId) { mutableStateOf(false) }

                            when {
                                isBlocked -> {
                                    OutlinedButton(
                                        onClick = { onUnblockUser(person.userId, person.fullName) },
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("unblock_person_${person.userId}")
                                    ) {
                                        Text("Unblock", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                                isFriend -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { onStartChatWithFriend(person.userId) }) {
                                            Icon(
                                                imageVector = Icons.Rounded.ChatBubble,
                                                contentDescription = "Open chat",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                onCallUser(person.userId, person.fullName, person.avatarData, "audio")
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Call,
                                                contentDescription = "Audio call",
                                                tint = OnlineGreen
                                            )
                                        }
                                    }
                                }
                                isRequested -> {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.HourglassTop,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "Sent",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                hasIncoming -> {
                                    val req = incomingPending.first { it.fromUid == person.userId }
                                    Button(
                                        onClick = { onRespondRequest(req, true) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CobaltPrimary,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text("Accept")
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = { onSendRequest(person) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CobaltPrimary,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier.testTag("add_friend_${person.userId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PersonAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text("Add")
                                    }
                                }
                            }

                            if (!isBlocked) {
                                Box {
                                    IconButton(
                                        onClick = { menuOpen = true },
                                        modifier = Modifier.testTag("person_menu_${person.userId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "User options",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Block User", color = MissedRed) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Block,
                                                    contentDescription = null,
                                                    tint = MissedRed
                                                )
                                            },
                                            onClick = {
                                                menuOpen = false
                                                userPendingBlock = person
                                            }
                                        )
                                        if (isFriend) {
                                            DropdownMenuItem(
                                                text = { Text("Remove Friend", color = MissedRed) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PersonRemove,
                                                        contentDescription = null,
                                                        tint = MissedRed
                                                    )
                                                },
                                                onClick = {
                                                    menuOpen = false
                                                    friendPendingRemoval = person
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    userPendingBlock?.let { target ->
        AlertDialog(
            onDismissRequest = { userPendingBlock = null },
            title = { Text("Block ${target.fullName}?") },
            text = {
                Text("They won't be able to ring you or trigger notifications. You can unblock @${target.username} anytime from Settings → Blocked Users.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBlockUser(target.userId, target.fullName)
                        userPendingBlock = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MissedRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("Block User")
                }
            },
            dismissButton = {
                TextButton(onClick = { userPendingBlock = null }) { Text("Cancel") }
            }
        )
    }

    friendPendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { friendPendingRemoval = null },
            title = { Text("Remove Friend?") },
            text = {
                Text("Are you sure you want to remove ${target.fullName} (@${target.username}) from your friends? You will need to send a new friend request to connect again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveFriend(target.userId, target.fullName)
                        friendPendingRemoval = null
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
                TextButton(onClick = { friendPendingRemoval = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CallsTabScreen(
    currentUserId: String,
    calls: List<CallSession>,
    onRedial: (uid: String, name: String, avatar: String?, callType: String) -> Unit,
    onViewAvatar: (name: String, username: String, avatar: String?) -> Unit
) {
    if (calls.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
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
                                imageVector = Icons.Rounded.Call,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No Call History Yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Start a real-time Kotlin + WebRTC audio or video call from any chat or friend profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Calls (${calls.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "Kotlin • WebRTC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        items(calls, key = { it.id }) { call ->
            val isOutgoing = call.callerId == currentUserId
            val otherUid = if (isOutgoing) call.calleeId else call.callerId
            val otherName = if (isOutgoing) call.calleeName else call.callerName
            val otherAvatar = if (isOutgoing) call.calleeAvatar else call.callerAvatar
            val isMissed = !isOutgoing && (call.status == "missed" || call.status == "declined")
            val statusIcon = when {
                isMissed -> Icons.AutoMirrored.Rounded.CallMissed
                isOutgoing -> Icons.AutoMirrored.Rounded.CallMade
                else -> Icons.AutoMirrored.Rounded.CallReceived
            }
            val statusTint = if (isMissed) MissedRed else OnlineGreen
            val statusLabel = when {
                call.status == "missed" -> if (isOutgoing) "Unanswered" else "Missed call"
                call.status == "declined" -> "Declined"
                call.status == "connected" -> "In progress"
                call.status == "ringing" -> "Ringing"
                else -> if (call.durationSeconds > 0) "Duration ${formatDuration(call.durationSeconds)}" else "Ended"
            }

            GlassSurface(
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("call_history_${call.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        name = otherName,
                        avatarData = otherAvatar,
                        size = 50.dp,
                        onClick = { onViewAvatar(otherName, "", otherAvatar) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = otherName,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isMissed) MissedRed else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusTint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "${if (call.callType == "video") "Video" else "Audio"} • $statusLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isMissed) MissedRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatDateTime(call.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onRedial(otherUid, otherName, otherAvatar, "audio") }) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Audio call $otherName",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { onRedial(otherUid, otherName, otherAvatar, "video") }) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = "Video call $otherName",
                            tint = AquaAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingCallOverlay(
    call: CallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "incoming_call_pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ring"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0A1128), IndigoDeep, Color(0xFF070B14)))
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            ) {
                Text(
                    text = if (call.callType == "video") "INCOMING WEBRTC VIDEO CALL" else "INCOMING WEBRTC AUDIO CALL",
                    style = MaterialTheme.typography.labelLarge,
                    color = AquaBright,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .background(AquaBright.copy(alpha = 0.18f))
                )
                UserAvatar(
                    name = call.callerName,
                    avatarData = call.callerAvatar,
                    size = 128.dp
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = call.callerName,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Linkora End-to-End WebRTC Call",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = onDecline,
                    shape = CircleShape,
                    color = MissedRed,
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("decline_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "Decline call",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Decline", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    onClick = onAccept,
                    shape = CircleShape,
                    color = CallGreen,
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("accept_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (call.callType == "video") Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = "Accept call",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Accept", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    currentUserId: String,
    call: CallSession,
    callQualityMode: CallQualityMode = CallQualityMode.BALANCED,
    lowDataCalls: Boolean = false,
    iceCandidatesFlow: Flow<List<IceCandidateSignal>> = emptyFlow(),
    onPublishOffer: (String) -> Unit = {},
    onPublishAnswer: (String) -> Unit = {},
    onSendIceCandidate: (sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit = { _, _, _ -> },
    onEndCall: (elapsedSeconds: Long) -> Unit,
    onPushMedia: (videoFrame: String?, audioChunk: String?, audioSeq: Long?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isCaller = call.callerId == currentUserId
    val isVideo = call.callType == "video"
    val otherName = if (isCaller) call.calleeName else call.callerName
    val otherAvatar = if (isCaller) call.calleeAvatar else call.callerAvatar

    var muted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(isVideo) }
    var cameraEnabled by remember { mutableStateOf(isVideo) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        audioGranted = results[Manifest.permission.RECORD_AUDIO] ?: audioGranted
        cameraGranted = results[Manifest.permission.CAMERA] ?: cameraGranted
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (!audioGranted) needed.add(Manifest.permission.RECORD_AUDIO)
        if (isVideo && !cameraGranted) needed.add(Manifest.permission.CAMERA)
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
    }

    // Connected duration timer
    LaunchedEffect(call.status) {
        if (call.status == "connected") {
            while (true) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    // Real Kotlin + WebRTC PeerConnection Manager
    val webRtcManager = remember(call.id) {
        WebRtcCallManager(
            context = context,
            onLocalOfferReady = onPublishOffer,
            onLocalAnswerReady = onPublishAnswer,
            onLocalIceCandidate = onSendIceCandidate
        )
    }
    val webrtcState by webRtcManager.connectionState.collectAsStateWithLifecycle()
    val isP2pConnected by webRtcManager.isP2pConnected.collectAsStateWithLifecycle()
    val localWebRtcVideo by webRtcManager.localVideoTrack.collectAsStateWithLifecycle()
    val remoteWebRtcVideo by webRtcManager.remoteVideoTrack.collectAsStateWithLifecycle()
    val remoteIceSignals by iceCandidatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var webrtcStarted by remember(call.id) { mutableStateOf(false) }

    LaunchedEffect(audioGranted, cameraGranted, call.id) {
        val canStartAudio = audioGranted
        val canStartVideo = !isVideo || cameraGranted
        if (!webrtcStarted && canStartAudio && canStartVideo) {
            webrtcStarted = true
            webRtcManager.start(
                isCaller = isCaller,
                enableVideo = isVideo && cameraGranted,
                qualityMode = callQualityMode,
                lowDataMode = lowDataCalls
            )
        }
    }

    // Feed remote SDP offer / answer into WebRTC peer connection
    LaunchedEffect(webrtcStarted, call.offerSdp, call.answerSdp) {
        if (!webrtcStarted) return@LaunchedEffect
        if (!isCaller && !call.offerSdp.isNullOrBlank()) {
            webRtcManager.applyRemoteOfferAndCreateAnswer(call.offerSdp)
        } else if (isCaller && !call.answerSdp.isNullOrBlank()) {
            webRtcManager.applyRemoteAnswer(call.answerSdp)
        }
    }

    // Feed remote ICE candidates into WebRTC peer connection
    LaunchedEffect(webrtcStarted, remoteIceSignals) {
        if (!webrtcStarted) return@LaunchedEffect
        remoteIceSignals
            .filter { it.senderId != currentUserId }
            .forEach { sig ->
                webRtcManager.addRemoteIceCandidate(sig.sdpMid, sig.sdpMLineIndex, sig.candidate)
            }
    }

    LaunchedEffect(muted) {
        webRtcManager.setMuted(muted)
    }
    LaunchedEffect(cameraEnabled) {
        webRtcManager.setVideoEnabled(cameraEnabled)
    }

    // Fallback cloud relay audio engine (runs only when WebRTC P2P is not yet connected)
    val audioEngine = remember(context) { LiveCallAudioEngine(context) }
    var localAudioSeq by remember { mutableLongStateOf(0L) }
    var lastPlayedRemoteSeq by remember { mutableLongStateOf(0L) }

    LaunchedEffect(muted) {
        audioEngine.muted = muted
    }
    LaunchedEffect(speakerOn) {
        audioEngine.setSpeakerphone(speakerOn)
    }

    LaunchedEffect(call.status, audioGranted, isP2pConnected) {
        if (call.status == "connected" && audioGranted && !isP2pConnected) {
            audioEngine.start(scope, speakerOn) { chunkBase64 ->
                localAudioSeq++
                onPushMedia(null, chunkBase64, localAudioSeq)
            }
        } else if (isP2pConnected) {
            audioEngine.stop()
        }
    }

    val remoteAudioSeq = if (isCaller) call.calleeAudioSeq else call.callerAudioSeq
    val remoteAudioChunk = if (isCaller) call.calleeAudioChunk else call.callerAudioChunk
    LaunchedEffect(remoteAudioSeq, remoteAudioChunk, isP2pConnected) {
        if (call.status == "connected" && !isP2pConnected && remoteAudioSeq > lastPlayedRemoteSeq && !remoteAudioChunk.isNullOrBlank()) {
            lastPlayedRemoteSeq = remoteAudioSeq
            audioEngine.playRemoteChunk(remoteAudioChunk)
        }
    }

    DisposableEffect(call.id) {
        onDispose {
            audioEngine.stop()
            webRtcManager.release()
        }
    }

    val remoteVideoFrame = if (isCaller) call.calleeVideoFrame else call.callerVideoFrame
    val remoteBitmap = remember(remoteVideoFrame) { ImageCodec.decodeBase64Bitmap(remoteVideoFrame) }
    val eglBase = webRtcManager.eglBase

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF060A16), Color(0xFF101935), IndigoDeep)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Remote Live Video: Prefer real WebRTC VideoTrack, fallback to Firestore frame relay, else Voice UI
        when {
            isVideo && remoteWebRtcVideo != null && eglBase != null -> {
                WebRtcVideoSurface(
                    track = remoteWebRtcVideo!!,
                    eglBase = eglBase,
                    mirror = false,
                    isOverlay = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isVideo && remoteBitmap != null && call.status == "connected" -> {
                Image(
                    bitmap = remoteBitmap.asImageBitmap(),
                    contentDescription = "Remote live video feed from $otherName",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.60f)
                                )
                            )
                        )
                )
            }
            else -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UserAvatar(
                        name = otherName,
                        avatarData = otherAvatar,
                        size = 124.dp,
                        storyRingBrush = Brush.linearGradient(listOf(AquaBright, OnlineGreen))
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = otherName,
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (call.status == "connected") formatDuration(elapsedSeconds) else "Calling...",
                        style = MaterialTheme.typography.titleMedium,
                        color = AquaBright
                    )
                }
            }
        }

        // Top Frosted Glass Caller & WebRTC Telemetry Pill
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xA6091124))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(
                text = "$otherName • ${if (call.status == "connected") formatDuration(elapsedSeconds) else "Ringing..."}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isP2pConnected) OnlineGreen else AquaBright)
                )
                Text(
                    text = webrtcState,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isP2pConnected) OnlineGreen else AquaBright
                )
            }
        }

        // Local Video PiP: Real WebRTC Camera Surface when available, else CameraX fallback
        if (isVideo && cameraEnabled && cameraGranted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(width = 112.dp, height = 160.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                if (localWebRtcVideo != null && eglBase != null) {
                    WebRtcVideoSurface(
                        track = localWebRtcVideo!!,
                        eglBase = eglBase,
                        mirror = useFrontCamera,
                        isOverlay = true,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LiveCameraPreviewRelay(
                        useFrontCamera = useFrontCamera,
                        shouldStreamFrames = call.status == "connected" && !isP2pConnected,
                        onFrameEncoded = { frameBase64 ->
                            onPushMedia(frameBase64, null, null)
                        }
                    )
                }
            }
        }

        // Bottom Frosted Glass Call Control Bar
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xB3091124),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 24.dp)
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(32.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlCircle(
                    icon = if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDesc = if (muted) "Unmute microphone" else "Mute microphone",
                    active = muted,
                    onClick = { muted = !muted }
                )
                CallControlCircle(
                    icon = if (speakerOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDesc = if (speakerOn) "Disable speaker" else "Enable speaker",
                    active = speakerOn,
                    onClick = { speakerOn = !speakerOn }
                )
                if (isVideo) {
                    CallControlCircle(
                        icon = if (cameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        contentDesc = if (cameraEnabled) "Turn camera off" else "Turn camera on",
                        active = !cameraEnabled,
                        onClick = { cameraEnabled = !cameraEnabled }
                    )
                    CallControlCircle(
                        icon = Icons.Rounded.Cameraswitch,
                        contentDesc = "Switch camera",
                        active = false,
                        onClick = {
                            useFrontCamera = !useFrontCamera
                            webRtcManager.switchCamera()
                        }
                    )
                }
                Surface(
                    onClick = { onEndCall(elapsedSeconds) },
                    shape = CircleShape,
                    color = MissedRed,
                    modifier = Modifier
                        .size(60.dp)
                        .testTag("end_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = "End call",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WebRtcVideoSurface(
    track: VideoTrack,
    eglBase: EglBase,
    mirror: Boolean,
    isOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(eglBase) {
        SurfaceViewRenderer(context).apply {
            runCatching {
                init(eglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                setZOrderMediaOverlay(isOverlay)
            }
        }
    }

    LaunchedEffect(mirror) {
        runCatching { renderer.setMirror(mirror) }
    }

    DisposableEffect(track, renderer) {
        runCatching { track.addSink(renderer) }
        onDispose {
            runCatching { track.removeSink(renderer) }
            runCatching { renderer.release() }
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

@Composable
private fun CallControlCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) Color.White else Color.White.copy(alpha = 0.18f),
        modifier = Modifier
            .size(52.dp)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = if (active) IndigoDeep else Color.White
            )
        }
    }
}

@Composable
private fun LiveCameraPreviewRelay(
    useFrontCamera: Boolean,
    shouldStreamFrames: Boolean,
    onFrameEncoded: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    LaunchedEffect(useFrontCamera, shouldStreamFrames) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                var lastSentAt = 0L
                analysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        val now = System.currentTimeMillis()
                        if (shouldStreamFrames && now - lastSentAt >= 750L) {
                            lastSentAt = now
                            imageProxyToBitmap(imageProxy)?.let { bmp ->
                                val base64 = ImageCodec.compressBitmapToBase64(bmp, maxDimension = 180, quality = 38)
                                bmp.recycle()
                                onFrameEncoded(base64)
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        imageProxy.close()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 45, out)
        val bytes = out.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}
