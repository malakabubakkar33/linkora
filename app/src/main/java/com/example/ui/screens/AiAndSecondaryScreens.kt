package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.ImageCodec
import com.example.core.LinkoraMessageService
import com.example.core.NotificationHelper
import com.example.data.model.AiChatMessage
import com.example.data.model.CallQualityMode
import com.example.data.model.ChatWallpaperStyle
import com.example.data.model.DisappearingOption
import com.example.data.model.FontScaleOption
import com.example.data.model.LinkoraNotification
import com.example.data.model.PublicProfile
import com.example.data.model.UserAccount
import com.example.ui.components.GlassSurface
import com.example.ui.components.LinkoraGlassBackdrop
import com.example.ui.components.UserAvatar
import com.example.ui.theme.AquaAccent
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralWarm
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.MissedRed
import com.example.ui.theme.OnlineGreen
import com.example.viewmodel.AiStreamState
import kotlinx.coroutines.launch

@Composable
fun LinkoraAiScreen(
    messages: List<AiChatMessage>,
    streamState: AiStreamState,
    chatWallpaper: ChatWallpaperStyle = ChatWallpaperStyle.CLASSIC_GLASS,
    onBack: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onRetryLastPrompt: () -> Unit,
    onClearHistory: () -> Unit,
    onDismissError: () -> Unit
) {
    BackHandler { onBack() }

    val isDark = LocalIsDarkTheme.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isBusy = streamState is AiStreamState.Thinking || streamState is AiStreamState.Streaming

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input = if (input.isBlank()) spoken else "$input $spoken"
            }
        }
    }

    LaunchedEffect(messages.size, streamState) {
        val total = messages.size + if (streamState is AiStreamState.Streaming) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
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
            // Frosted Glass Header
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ai_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Surface(
                        shape = CircleShape,
                        color = CobaltPrimary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
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
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Roman Urdu • English • All Languages",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnlineGreen
                        )
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear AI chat")
                        }
                    }
                }
            }

            if (streamState is AiStreamState.Error) {
                Surface(
                    color = MissedRed.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MissedRed)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = streamState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MissedRed,
                            modifier = Modifier.weight(1f)
                        )
                        if (streamState.canRetry) {
                            TextButton(
                                onClick = onRetryLastPrompt,
                                modifier = Modifier.testTag("ai_retry_button")
                            ) {
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                        TextButton(onClick = onDismissError) { Text("OK") }
                    }
                }
            }

            if (messages.isEmpty() && streamState is AiStreamState.Idle) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(80.dp)
                            .background(Brush.linearGradient(listOf(IndigoDeep, CobaltPrimary, AquaAccent)), CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Assalam-o-Alaikum! Main Linkora AI hoon",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Kisi bhi language mein friendly baat karein, research karwayein, ya sawal poochein — bilkul real dost ki tarah!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    val suggestions = listOf(
                        "Yar AI aur future tech pe short research batao",
                        "Ek dost ko pyara birthday wish Roman Urdu mein likho",
                        "How does WebRTC P2P calling work?",
                        "Daily productivity routine Roman Urdu mein banao"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions) { prompt ->
                            AssistChip(
                                onClick = { onSendPrompt(prompt) },
                                label = { Text(prompt) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        AiMessageBubble(isUser = msg.role == "user", text = msg.text)
                    }
                    when (streamState) {
                        AiStreamState.Thinking -> {
                            item(key = "thinking") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Linkora AI is thinking...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        is AiStreamState.Streaming -> {
                            item(key = "streaming") {
                                AiMessageBubble(isUser = false, text = streamState.partialText)
                            }
                        }
                        else -> Unit
                    }
                }
            }

            // Clean Solid Input Row
            GlassSurface(
                shape = RoundedCornerShape(0.dp),
                strong = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Linkora AI...")
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (_: Exception) {
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardVoice,
                            contentDescription = "Voice input for AI",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Roman Urdu ya kisi bhi zuban mein poochein...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.65f else 0.55f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.50f else 0.45f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_prompt_input")
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            val q = input.trim()
                            if (q.isNotBlank() && !isBusy) {
                                input = ""
                                onSendPrompt(q)
                            }
                        },
                        shape = CircleShape,
                        color = if (!isBusy && input.isNotBlank()) CobaltPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send to Linkora AI",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMessageBubble(isUser: Boolean, text: String) {
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 20.dp
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Box(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(CobaltPrimary, Color(0xFF4338CA))))
                    .border(0.8.dp, Color.White.copy(alpha = 0.22f), shape)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }
        } else {
            GlassSurface(
                shape = shape,
                strong = true,
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                )
            }
        }
    }
}

/**
 * Unified Settings & Profile Tab featuring Profile Hero Card, Appearance & Glass UI,
 * Privacy & Blocked Users, Chats & Notifications, Voice/Video Kotlin+WebRTC Controls, Support & Sign Out.
 */
@Composable
fun SettingsTabScreen(
    account: UserAccount,
    chatsCount: Int,
    storiesCount: Int,
    sessionModeLabel: String? = null,
    darkMode: Boolean,
    hasChatLockPin: Boolean,
    globalDisappearingMs: Long,
    soundEnabled: Boolean,
    vibrateEnabled: Boolean,
    readReceiptsEnabled: Boolean,
    onlinePresenceVisible: Boolean,
    enterKeySends: Boolean,
    lowDataCalls: Boolean,
    callQualityMode: CallQualityMode,
    chatWallpaper: ChatWallpaperStyle,
    fontScaleOption: FontScaleOption,
    onUpdateProfile: (fullName: String, bio: String, avatarData: String?) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onSelectWallpaper: (ChatWallpaperStyle) -> Unit,
    onSelectFontScale: (FontScaleOption) -> Unit,
    onUpdateAccountPrefs: (discoverable: Boolean, notificationsEnabled: Boolean) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleVibrate: (Boolean) -> Unit,
    onToggleReadReceipts: (Boolean) -> Unit,
    onToggleOnlinePresence: (Boolean) -> Unit,
    onToggleEnterKeySends: (Boolean) -> Unit,
    onToggleLowDataCalls: (Boolean) -> Unit,
    onSelectCallQuality: (CallQualityMode) -> Unit,
    onConfigureChatLockPin: () -> Unit,
    onResetChatLockPin: () -> Unit,
    onOpenGlobalDisappearing: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenHelp: () -> Unit,
    onTestMessageAlert: () -> Unit = {},
    onSignOut: () -> Unit,
    onViewAvatar: (name: String, username: String, avatar: String?) -> Unit
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var notifPermissionGranted by remember { mutableStateOf(NotificationHelper.canPostNotifications(context)) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifPermissionGranted = granted
        if (granted && account.notificationsEnabled) {
            LinkoraMessageService.start(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Frosted Glass Profile Hero Card
        GlassSurface(
            shape = RoundedCornerShape(28.dp),
            strong = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatar(
                    name = account.fullName,
                    avatarData = account.avatarData,
                    size = 94.dp,
                    showOnlineIndicator = true,
                    isOnline = account.online && onlinePresenceVisible,
                    storyRingBrush = Brush.linearGradient(listOf(CobaltPrimary, AquaAccent)),
                    onClick = { onViewAvatar(account.fullName, account.username, account.avatarData) },
                    modifier = Modifier.testTag("profile_avatar_fullscreen_trigger")
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = account.fullName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "@${account.username}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!sessionModeLabel.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = sessionModeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
                if (account.bio.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = account.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatColumn("Friends", account.friendUids.size.toString())
                    StatColumn("Chats", chatsCount.toString())
                    StatColumn("Stories", storiesCount.toString())
                    StatColumn("Blocked", account.blockedUids.size.toString())
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showEditDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_button")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Profile & Photo")
                }
            }
        }

        // 2. Appearance & Theme
        SettingsSectionHeader("Appearance & Theme")
        GlassSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsInlineSwitchRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "Dark Mode",
                    subtitle = if (darkMode) "Dark theme active" else "Classic Light theme active",
                    checked = darkMode,
                    testTag = "settings_dark_mode_switch",
                    onCheckedChange = onToggleDarkMode
                )
                SettingsCardDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Chat Background", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = chatWallpaper.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ChatWallpaperStyle.entries) { style ->
                            val selected = style == chatWallpaper
                            GlassChoiceChip(
                                label = style.label,
                                selected = selected,
                                testTag = "wallpaper_${style.key}",
                                onClick = { onSelectWallpaper(style) }
                            )
                        }
                    }
                }
                SettingsCardDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("App Text Size", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Current scale: ${fontScaleOption.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FontScaleOption.entries.forEach { opt ->
                            GlassChoiceChip(
                                label = opt.label,
                                selected = opt == fontScaleOption,
                                testTag = "font_scale_${opt.key}",
                                onClick = { onSelectFontScale(opt) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Privacy, Safety & Blocking
        SettingsSectionHeader("Privacy, Safety & Blocking")
        GlassSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            val globalOpt = DisappearingOption.fromDurationMs(globalDisappearingMs)
            Column {
                SettingsNavigationRow(
                    icon = Icons.Rounded.Block,
                    iconTint = MissedRed,
                    title = "Blocked Users",
                    subtitle = if (account.blockedUids.isEmpty()) "No blocked users" else "${account.blockedUids.size} user(s) blocked — tap to manage",
                    badgeText = if (account.blockedUids.isNotEmpty()) account.blockedUids.size.toString() else null,
                    testTag = "settings_blocked_users_row",
                    onClick = onOpenBlockedUsers
                )
                SettingsCardDivider()
                SettingsNavigationRow(
                    icon = Icons.Rounded.Timer,
                    iconTint = AquaAccent,
                    title = "Default Disappearing Messages",
                    subtitle = "Auto-refresh timer for all chats: ${globalOpt.label}",
                    testTag = "settings_global_disappearing",
                    onClick = onOpenGlobalDisappearing
                )
                SettingsCardDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Locked Chats 4-Digit PIN", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (hasChatLockPin) "PIN protection is active" else "Protect private chats behind a 4-digit PIN",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onConfigureChatLockPin,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CobaltPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_set_pin_button")
                        ) {
                            Text(if (hasChatLockPin) "Change PIN" else "Set Lock PIN")
                        }
                        if (hasChatLockPin) {
                            OutlinedButton(
                                onClick = onResetChatLockPin,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_reset_pin_button")
                            ) {
                                Text("Reset PIN", color = MissedRed)
                            }
                        }
                    }
                }
                SettingsCardDivider()
                SettingsInlineSwitchRow(
                    icon = Icons.Rounded.DoneAll,
                    title = "Read Receipts",
                    subtitle = "Send blue seen ticks when you open messages",
                    checked = readReceiptsEnabled,
                    onCheckedChange = onToggleReadReceipts
                )
                SettingsCardDivider()
                SettingsInlineSwitchRow(
                    icon = Icons.Rounded.Visibility,
                    title = "Show Online & Typing Status",
                    subtitle = "Let friends see when you are online or typing",
                    checked = onlinePresenceVisible,
                    onCheckedChange = onToggleOnlinePresence
                )
                SettingsCardDivider()
                SettingsInlineSwitchRow(
                    icon = Icons.Rounded.Public,
                    title = "Always Discoverable in People",
                    subtitle = "Keep your @username searchable even when offline",
                    checked = account.discoverable,
                    onCheckedChange = { disc ->
                        onUpdateAccountPrefs(disc, account.notificationsEnabled)
                    }
                )
            }
        }

        // 4. Chats & Notifications
        SettingsSectionHeader("Chats & Notifications")
        GlassSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsInlineSwitchRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Heads-Up & Lock-Screen Alerts",
                    subtitle = "Show messages at top of screen & on mobile lock screen even when app is closed",
                    checked = account.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onUpdateAccountPrefs(account.discoverable, enabled)
                    }
                )
                SettingsCardDivider()
                SettingsInlineSwitchRow(
                    icon = Icons.Rounded.VolumeUp,
                    title = "Message Notification Sound",
                    subtitle = "Play crisp alert tone when messages arrive (in-app & background)",
                    checked = soundEnabled,
                    onCheckedChange = onToggleSound
                )
                SettingsCardDivider()
                SettingsInlineSwitchRow(
                    icon = Icons.Rounded.Vibration,
                    title = "Haptic Vibration",
                    subtitle = "Vibrate on incoming messages and calls",
                    checked = vibrateEnabled,
                    onCheckedChange = onToggleVibrate
                )
                SettingsCardDivider()
                SettingsInlineSwitchRow(
                    icon = Icons.Rounded.KeyboardReturn,
                    title = "Enter Key Sends Message",
                    subtitle = "Press keyboard Send/Enter to immediately dispatch chat messages",
                    checked = enterKeySends,
                    onCheckedChange = onToggleEnterKeySends
                )
                SettingsCardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = if (notifPermissionGranted) OnlineGreen else MissedRed
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (notifPermissionGranted) "Background Delivery Ready" else "Allow Screen Popups",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (notifPermissionGranted) {
                                "Real-time service active for closed-app & lock-screen messages"
                            } else {
                                "Grant notification permission so alerts show when app is closed"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!notifPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(
                            onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("settings_grant_notif_button")
                        ) {
                            Text("Allow")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onTestMessageAlert,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("settings_test_alert_button")
                        ) {
                            Text("Test")
                        }
                    }
                }
            }
        }

        // 5. Voice, Video & Kotlin WebRTC
        SettingsSectionHeader("Voice, Video & Kotlin WebRTC")
        GlassSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SettingsInputAntenna,
                        contentDescription = null,
                        tint = OnlineGreen
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kotlin + WebRTC Engine", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "STUN P2P audio/video with AEC/NS + encrypted relay fallback",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = OnlineGreen.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnlineGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                SettingsCardDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("WebRTC Video Call Quality", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Selected: ${callQualityMode.label} @ ${callQualityMode.fps}fps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CallQualityMode.entries) { mode ->
                            GlassChoiceChip(
                                label = mode.label,
                                selected = mode == callQualityMode,
                                testTag = "call_quality_${mode.key}",
                                onClick = { onSelectCallQuality(mode) }
                            )
                        }
                    }
                }
                SettingsCardDivider()
                SettingsInlineSwitchRow(
                    icon = Icons.Rounded.DataSaverOn,
                    title = "Low Data Mode for Calls",
                    subtitle = "Reduces audio/video WebRTC bitrate on slower mobile networks",
                    checked = lowDataCalls,
                    onCheckedChange = onToggleLowDataCalls
                )
            }
        }

        // 6. Support, Help & Account
        SettingsSectionHeader("Support & Account")
        GlassSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsNavigationRow(
                    icon = Icons.Rounded.SupportAgent,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Support",
                    subtitle = "Contact Linkora support team",
                    onClick = onOpenSupport
                )
                SettingsCardDivider()
                SettingsNavigationRow(
                    icon = Icons.AutoMirrored.Rounded.HelpOutline,
                    iconTint = AquaAccent,
                    title = "Help & FAQ",
                    subtitle = "Guides for WebRTC calls, Blocking, Delete & AI",
                    onClick = onOpenHelp
                )
            }
        }

        Button(
            onClick = onSignOut,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MissedRed.copy(alpha = 0.16f),
                contentColor = MissedRed
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sign Out", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = "Linkora • Classic Light & Dark • Kotlin + WebRTC",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            account = account,
            onDismiss = { showEditDialog = false },
            onSave = { name, bio, avatar ->
                onUpdateProfile(name, bio, avatar)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 2.dp)
    )
}

@Composable
private fun SettingsCardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    )
}

@Composable
private fun GlassChoiceChip(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) CobaltPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SettingsInlineSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = iconTint.copy(alpha = 0.14f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!badgeText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MissedRed.copy(alpha = 0.16f)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MissedRed,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BlockedUsersScreen(
    blockedUids: List<String>,
    profilesByUid: Map<String, PublicProfile>,
    chatWallpaper: ChatWallpaperStyle = ChatWallpaperStyle.CLASSIC_GLASS,
    onUnblockUser: (uid: String, name: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    SecondaryPageScaffold(
        title = "Blocked Users",
        chatWallpaper = chatWallpaper,
        onBack = onBack
    ) {
        if (blockedUids.isEmpty()) {
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
                            color = MissedRed.copy(alpha = 0.14f),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Block,
                                    contentDescription = null,
                                    tint = MissedRed,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "No Blocked Users",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Users you block from any Chat or People 3-dot menu appear here. Blocked contacts cannot ring you or send notifications.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(blockedUids, key = { it }) { uid ->
                    val profile = profilesByUid[uid]
                    val name = profile?.fullName ?: "Linkora User"
                    val handle = profile?.username ?: uid.take(8)
                    GlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blocked_user_row_$uid")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = name,
                                avatarData = profile?.avatarData,
                                size = 48.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@$handle",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { onUnblockUser(uid, name) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("unblock_user_btn_$uid")
                            ) {
                                Text("Unblock")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditProfileDialog(
    account: UserAccount,
    onDismiss: () -> Unit,
    onSave: (fullName: String, bio: String, avatarData: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(account.fullName) }
    var bio by remember { mutableStateOf(account.bio) }
    var avatarData by remember { mutableStateOf(account.avatarData) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = ImageCodec.compressUriToBase64(context, uri, maxDimension = 300, quality = 70)
                if (encoded != null) avatarData = encoded
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clickable {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                ) {
                    UserAvatar(name = name.ifBlank { "L" }, avatarData = avatarData, size = 84.dp)
                    Surface(
                        shape = CircleShape,
                        color = CobaltPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 80) name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 250) bio = it },
                    label = { Text("Bio (Optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, bio, avatarData) },
                enabled = name.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CobaltPrimary,
                    contentColor = Color.White
                )
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun NotificationsScreen(
    notifications: List<LinkoraNotification>,
    chatWallpaper: ChatWallpaperStyle = ChatWallpaperStyle.CLASSIC_GLASS,
    onBack: () -> Unit,
    onNotificationClick: (LinkoraNotification) -> Unit
) {
    BackHandler { onBack() }
    SecondaryPageScaffold(
        title = "Notifications",
        chatWallpaper = chatWallpaper,
        onBack = onBack
    ) {
        if (notifications.isEmpty()) {
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
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("You're All Caught Up", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "New friend requests, real-time messages, and missed calls appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    val icon = when (item.type) {
                        "request" -> Icons.Rounded.PersonAdd
                        "missed_call" -> Icons.Rounded.PhoneMissed
                        else -> Icons.Rounded.ChatBubble
                    }
                    val tint = when (item.type) {
                        "request" -> MaterialTheme.colorScheme.primary
                        "missed_call" -> MissedRed
                        else -> OnlineGreen
                    }
                    GlassSurface(
                        onClick = { onNotificationClick(item) },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = tint.copy(alpha = 0.14f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, contentDescription = null, tint = tint)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupportScreen(
    chatWallpaper: ChatWallpaperStyle = ChatWallpaperStyle.CLASSIC_GLASS,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    SecondaryPageScaffold(
        title = "Support",
        chatWallpaper = chatWallpaper,
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassSurface(shape = RoundedCornerShape(24.dp), strong = true, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.SupportAgent,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Linkora Care Team", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Need help with your account, Kotlin + WebRTC call permissions, blocked contacts, or privacy settings? Reach our support desk anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Email, contentDescription = null, tint = CoralWarm, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "support@linkora.app",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HelpScreen(
    chatWallpaper: ChatWallpaperStyle = ChatWallpaperStyle.CLASSIC_GLASS,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    SecondaryPageScaffold(
        title = "Help & FAQ",
        chatWallpaper = chatWallpaper,
        onBack = onBack
    ) {
        val faqs = listOf(
            "How do I delete a single message?" to "Open any chat and hold-tap (long-press) any message bubble. You can choose Delete for Me (hides on your phone), Delete for Everyone (replaces content with 'This message was deleted'), or Delete Permanently (completely erases your message with zero trace).",
            "How do Kotlin + WebRTC Audio & Video Calls work?" to "Open any chat or Calls tab and tap the Audio or Video call icon. Linkora establishes a real Kotlin + WebRTC peer-to-peer connection using STUN + SDP/ICE signaling, with automatic encrypted relay fallback.",
            "How do I Block or Unblock a user?" to "Open the 3-dot menu inside any chat or next to any person in the People tab and tap Block User. Manage or unblock contacts anytime under Settings → Blocked Users.",
            "How do Archive, Locked Chats & Disappearing Messages work?" to "Swipe any chat left to Archive (mutes notifications silently) or Delete. Long-press a chat to Lock it behind your 4-digit PIN or set a Disappearing Timer (24h, 44h, 7d, 1m, or Off).",
            "How do I send Voice Notes, Photos, Files & Location?" to "Inside any chat, tap '+' on the left to attach Photos, Video, Files, Documents, or GPS Location. Tap the Mic button beside Send to record direct user-to-user voice notes."
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(faqs) { (q, a) ->
                GlassSurface(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(q, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryPageScaffold(
    title: String,
    chatWallpaper: ChatWallpaperStyle = ChatWallpaperStyle.CLASSIC_GLASS,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    LinkoraGlassBackdrop(wallpaper = chatWallpaper) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(title, style = MaterialTheme.typography.titleLarge)
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                content()
            }
        }
    }
}
