package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatThread
import com.example.data.model.DisappearingOption
import com.example.data.model.PublicProfile
import com.example.ui.components.GlassSurface
import com.example.ui.components.LinkoraGlassBackdrop
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.AquaAccent
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.MissedRed
import com.example.ui.theme.OnlineGreen
import kotlinx.coroutines.launch

enum class PinDialogMode {
    CREATE,
    UNLOCK,
    RESET_AND_CREATE
}

@Composable
fun PinLockDialog(
    mode: PinDialogMode,
    hint: String,
    onVerifyPin: suspend (String) -> Boolean,
    onSaveNewPin: (pin: String, hint: String) -> Unit,
    onResetPin: () -> Unit,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var activeMode by remember(mode) { mutableStateOf(mode) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var hintInput by remember { mutableStateOf(hint) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val title = when (activeMode) {
        PinDialogMode.CREATE -> "Set Chat Lock PIN"
        PinDialogMode.UNLOCK -> "Unlock Locked Chats"
        PinDialogMode.RESET_AND_CREATE -> "Reset Chat Lock PIN"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = when (activeMode) {
                        PinDialogMode.UNLOCK -> "Enter your 4-digit PIN to open your private Locked Chats."
                        PinDialogMode.CREATE -> "Choose a 4-digit PIN to protect your Locked Chats. You can change or reset it anytime in Settings."
                        PinDialogMode.RESET_AND_CREATE -> "Create a new 4-digit PIN for your Locked Chats."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (activeMode == PinDialogMode.UNLOCK && hint.isNotBlank()) {
                    Text(
                        text = "Hint: $hint",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { next ->
                        if (next.length <= 4 && next.all { it.isDigit() }) {
                            pinInput = next
                            errorMsg = null
                        }
                    },
                    label = { Text(if (activeMode == PinDialogMode.UNLOCK) "4-Digit PIN" else "New 4-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_lock_input")
                )

                if (activeMode != PinDialogMode.UNLOCK) {
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { next ->
                            if (next.length <= 4 && next.all { it.isDigit() }) {
                                confirmPin = next
                                errorMsg = null
                            }
                        },
                        label = { Text("Confirm 4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_lock_confirm")
                    )
                    OutlinedTextField(
                        value = hintInput,
                        onValueChange = { if (it.length <= 60) hintInput = it },
                        label = { Text("Recovery Hint (Optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                errorMsg?.takeIf { it.isNotBlank() }?.let { errText ->
                    Text(
                        text = errText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MissedRed
                    )
                }

                if (activeMode == PinDialogMode.UNLOCK) {
                    TextButton(
                        onClick = {
                            onResetPin()
                            pinInput = ""
                            confirmPin = ""
                            errorMsg = null
                            activeMode = PinDialogMode.RESET_AND_CREATE
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Forgot PIN? Reset", color = MissedRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinInput.length != 4) {
                        errorMsg = "Please enter exactly 4 digits."
                        return@Button
                    }
                    if (activeMode == PinDialogMode.UNLOCK) {
                        scope.launch {
                            val ok = onVerifyPin(pinInput)
                            if (ok) {
                                onUnlocked()
                            } else {
                                errorMsg = "Incorrect PIN. Try again or tap Forgot PIN."
                                pinInput = ""
                            }
                        }
                    } else {
                        if (pinInput != confirmPin) {
                            errorMsg = "PINs do not match."
                            return@Button
                        }
                        onSaveNewPin(pinInput, hintInput)
                        onUnlocked()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CobaltPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("pin_lock_submit_button")
            ) {
                Text(if (activeMode == PinDialogMode.UNLOCK) "Unlock" else "Save PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun LockedChatsScreen(
    currentUserId: String,
    lockedChats: List<ChatThread>,
    profilesByUid: Map<String, PublicProfile>,
    onBack: () -> Unit,
    onOpenChat: (ChatThread) -> Unit,
    onUnlockChat: (ChatThread) -> Unit,
    onDeleteChat: (ChatThread) -> Unit,
    onOpenPinSettings: () -> Unit
) {
    BackHandler { onBack() }

    LinkoraGlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            GlassSurface(
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                strong = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("locked_chats_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Locked Chats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "PIN-protected • Notifications hidden",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenPinSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Manage PIN", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (lockedChats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassSurface(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("No Locked Chats Yet", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Long-press (hold) any chat on your home screen and choose \"Add to Locked Chats\" to hide it behind your PIN.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(lockedChats, key = { it.id }) { chat ->
                        val otherUid = chat.memberUids.firstOrNull { it != currentUserId }.orEmpty()
                        val profile = profilesByUid[otherUid]
                        val title = if (chat.isGroup) chat.groupName.ifBlank { "Group" }
                        else (profile?.fullName ?: chat.memberNames[otherUid] ?: "Friend")
                        val avatar = if (chat.isGroup) null else (profile?.avatarData ?: chat.memberAvatars[otherUid])

                        GlassSurface(
                            onClick = { onOpenChat(chat) },
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = title, avatarData = avatar, size = 50.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = chat.lastMessageText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onUnlockChat(chat) }) {
                                    Icon(Icons.Rounded.LockOpen, contentDescription = "Unlock chat", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onDeleteChat(chat) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete chat", tint = MissedRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchivedChatsScreen(
    currentUserId: String,
    archivedChats: List<ChatThread>,
    profilesByUid: Map<String, PublicProfile>,
    onBack: () -> Unit,
    onOpenChat: (ChatThread) -> Unit,
    onUnarchiveChat: (ChatThread) -> Unit,
    onDeleteChat: (ChatThread) -> Unit
) {
    BackHandler { onBack() }

    LinkoraGlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            GlassSurface(
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                strong = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("archived_chats_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Icon(
                        imageVector = Icons.Rounded.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Archived Chats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Notifications muted • New messages arrive silently",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Frosted Info banner
            GlassSurface(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Archived chats stay muted. Messages still arrive in real time without alert sounds or popups.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (archivedChats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassSurface(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No Archived Chats", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Swipe any chat left or long-press it to archive and mute notifications.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(archivedChats, key = { it.id }) { chat ->
                        val otherUid = chat.memberUids.firstOrNull { it != currentUserId }.orEmpty()
                        val profile = profilesByUid[otherUid]
                        val title = if (chat.isGroup) chat.groupName.ifBlank { "Group" }
                        else (profile?.fullName ?: chat.memberNames[otherUid] ?: "Friend")
                        val avatar = if (chat.isGroup) null else (profile?.avatarData ?: chat.memberAvatars[otherUid])

                        GlassSurface(
                            onClick = { onOpenChat(chat) },
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = title, avatarData = avatar, size = 50.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = formatTimeShort(chat.lastMessageAt ?: chat.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = chat.lastMessageText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onUnarchiveChat(chat) }) {
                                    Icon(Icons.Rounded.Unarchive, contentDescription = "Unarchive", tint = OnlineGreen)
                                }
                                IconButton(onClick = { onDeleteChat(chat) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MissedRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DisappearingTimerDialog(
    title: String = "Disappearing Messages",
    currentDurationMs: Long,
    onSelect: (DisappearingOption) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = DisappearingOption.fromDurationMs(currentDurationMs)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Messages, photos, videos & files older than the chosen duration are automatically cleared so your chat stays fresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                DisappearingOption.entries.forEach { option ->
                    val isSelected = option == selected
                    Surface(
                        onClick = {
                            onSelect(option)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("disappearing_opt_${option.shortLabel.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ChatLongPressActionDialog(
    chatTitle: String,
    isGroup: Boolean,
    isLocked: Boolean,
    isArchived: Boolean,
    disappearingOption: DisappearingOption,
    onToggleLock: () -> Unit,
    onOpenDisappearing: () -> Unit,
    onToggleArchive: () -> Unit,
    onOpenChatSettings: () -> Unit,
    onDeleteChat: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = chatTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionMenuItem(
                    icon = if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    title = if (isLocked) "Unlock Chat" else "Add to Locked Chats",
                    subtitle = if (isLocked) "Move back to main chats" else "Hide behind PIN lock",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        onDismiss()
                        onToggleLock()
                    }
                )
                ActionMenuItem(
                    icon = Icons.Rounded.Timer,
                    title = "Disappearing Messages",
                    subtitle = "Current: ${disappearingOption.label}",
                    tint = AquaAccent,
                    onClick = {
                        onDismiss()
                        onOpenDisappearing()
                    }
                )
                ActionMenuItem(
                    icon = if (isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                    title = if (isArchived) "Unarchive Chat" else "Archive Chat (Mute)",
                    subtitle = if (isArchived) "Restore to main chat list" else "Mute notifications silently",
                    tint = OnlineGreen,
                    onClick = {
                        onDismiss()
                        onToggleArchive()
                    }
                )
                ActionMenuItem(
                    icon = Icons.Rounded.Settings,
                    title = "Chat Settings",
                    subtitle = if (isGroup) "Group info & privacy controls" else "Privacy, timer & friend controls",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        onDismiss()
                        onOpenChatSettings()
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ActionMenuItem(
                    icon = Icons.Rounded.Delete,
                    title = "Delete Chat",
                    subtitle = "Clear chat from screen (friend stays added)",
                    tint = MissedRed,
                    onClick = {
                        onDismiss()
                        onDeleteChat()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ChatSettingsDialog(
    chatTitle: String,
    isGroup: Boolean,
    isLocked: Boolean,
    isArchived: Boolean,
    disappearingOption: DisappearingOption,
    onToggleLock: () -> Unit,
    onToggleArchive: () -> Unit,
    onOpenDisappearing: () -> Unit,
    onRemoveFriend: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat Settings • $chatTitle", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionMenuItem(
                    icon = Icons.Rounded.Timer,
                    title = "Disappearing Timer",
                    subtitle = disappearingOption.label,
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        onDismiss()
                        onOpenDisappearing()
                    }
                )
                ActionMenuItem(
                    icon = if (isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                    title = if (isLocked) "Remove from Locked Chats" else "Lock this Chat",
                    subtitle = "Protect with your 4-digit PIN",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        onDismiss()
                        onToggleLock()
                    }
                )
                ActionMenuItem(
                    icon = if (isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                    title = if (isArchived) "Unarchive Chat" else "Archive & Mute Notifications",
                    subtitle = "No notification sound or popup while archived",
                    tint = OnlineGreen,
                    onClick = {
                        onDismiss()
                        onToggleArchive()
                    }
                )
                if (!isGroup && onRemoveFriend != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ActionMenuItem(
                        icon = Icons.Rounded.PersonRemove,
                        title = "Remove Friend",
                        subtitle = "Unfriend and remove connection permanently",
                        tint = MissedRed,
                        onClick = {
                            onDismiss()
                            onRemoveFriend()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun CreateGroupDialog(
    friends: List<PublicProfile>,
    onCreateGroup: (groupName: String, members: List<PublicProfile>) -> Unit,
    onDismiss: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedUids = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Create Linkora Group", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { if (it.length <= 80) groupName = it },
                    label = { Text("Group Name") },
                    placeholder = { Text("e.g. Friends Squad, Family...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_group_name_input")
                )
                if (friends.isEmpty()) {
                    Text(
                        text = "Add friends in the People tab first to create a group chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Select Members (${selectedUids.size} selected)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyColumn(
                        modifier = Modifier.height(210.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(friends, key = { it.userId }) { friend ->
                            val checked = friend.userId in selectedUids
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (checked) selectedUids.remove(friend.userId)
                                        else selectedUids.add(friend.userId)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            if (friend.userId !in selectedUids) selectedUids.add(friend.userId)
                                        } else {
                                            selectedUids.remove(friend.userId)
                                        }
                                    }
                                )
                                UserAvatar(name = friend.fullName, avatarData = friend.avatarData, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(friend.fullName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("@${friend.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = friends.filter { it.userId in selectedUids }
                    onCreateGroup(groupName, chosen)
                },
                enabled = groupName.trim().isNotBlank() && selectedUids.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CobaltPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_create_group_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = if (tint == MissedRed) MissedRed else MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
