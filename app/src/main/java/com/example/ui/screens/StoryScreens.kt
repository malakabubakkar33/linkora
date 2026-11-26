package com.example.ui.screens

import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.ImageCodec
import com.example.data.model.StoryItem
import com.example.data.model.UserAccount
import com.example.ui.components.GlassSurface
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.AquaAccent
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralWarm
import com.example.ui.theme.MissedRed
import com.example.ui.theme.OnlineGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StoriesTabScreen(
    currentUser: UserAccount,
    stories: List<StoryItem>,
    onPostStory: (
        storyType: String,
        caption: String,
        mediaData: String?,
        musicTitle: String?,
        musicArtist: String?,
        backgroundHex: String,
        visibility: String,
        onComplete: () -> Unit
    ) -> Unit,
    onDeleteStory: (String) -> Unit,
    onViewAvatar: (name: String, username: String, avatar: String?) -> Unit
) {
    var showComposer by remember { mutableStateOf(false) }
    var viewingStory by remember { mutableStateOf<StoryItem?>(null) }

    val myStories = remember(stories, currentUser.userId) {
        stories.filter { it.authorId == currentUser.userId }
    }
    val friendStories = remember(stories, currentUser.userId, currentUser.friendUids) {
        val friendSet = currentUser.friendUids.toSet()
        stories.filter { it.authorId != currentUser.userId && it.authorId in friendSet }
    }
    val publicDiscoverStories = remember(stories, currentUser.userId, currentUser.friendUids) {
        val friendSet = currentUser.friendUids.toSet()
        stories.filter {
            it.authorId != currentUser.userId &&
                it.authorId !in friendSet &&
                it.visibility == "public"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        // Top "Add Story" user avatar glass card
        item {
            GlassSurface(
                onClick = { showComposer = true },
                shape = RoundedCornerShape(24.dp),
                strong = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_story_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(62.dp)) {
                        UserAvatar(
                            name = currentUser.fullName,
                            avatarData = currentUser.avatarData,
                            size = 58.dp,
                            storyRingBrush = Brush.linearGradient(listOf(CobaltPrimary, AquaAccent, CoralWarm))
                        )
                        Surface(
                            shape = CircleShape,
                            color = CobaltPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add Story",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Story",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Share text, music, photo, or video • Auto-expires in 24h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // My Active Stories with Delete option
        if (myStories.isNotEmpty()) {
            item {
                Text(
                    text = "Your Stories (${myStories.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(myStories, key = { it.id }) { story ->
                StoryRowCard(
                    story = story,
                    isOwnStory = true,
                    onClick = { viewingStory = story },
                    onDelete = { onDeleteStory(story.id) },
                    onAvatarClick = {
                        onViewAvatar(story.authorName, story.authorUsername, story.authorAvatar)
                    }
                )
            }
        }

        // Friends' Stories
        item {
            Text(
                text = "Friends' Stories",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (friendStories.isEmpty()) {
            item {
                GlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No active stories from your friends yet. Stories automatically vanish after 24 hours.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(friendStories, key = { it.id }) { story ->
                StoryRowCard(
                    story = story,
                    isOwnStory = false,
                    onClick = { viewingStory = story },
                    onDelete = null,
                    onAvatarClick = {
                        onViewAvatar(story.authorName, story.authorUsername, story.authorAvatar)
                    }
                )
            }
        }

        // Public Stories (visible even to non-friends)
        if (publicDiscoverStories.isNotEmpty()) {
            item {
                Text(
                    text = "Public Stories on Linkora",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(publicDiscoverStories, key = { it.id }) { story ->
                StoryRowCard(
                    story = story,
                    isOwnStory = false,
                    onClick = { viewingStory = story },
                    onDelete = null,
                    onAvatarClick = {
                        onViewAvatar(story.authorName, story.authorUsername, story.authorAvatar)
                    }
                )
            }
        }
    }

    if (showComposer) {
        StoryComposerDialog(
            onDismiss = { showComposer = false },
            onPublish = { type, caption, media, musicTitle, musicArtist, bgHex, visibility ->
                onPostStory(type, caption, media, musicTitle, musicArtist, bgHex, visibility) {
                    showComposer = false
                }
            }
        )
    }

    viewingStory?.let { activeStory ->
        StoryViewerDialog(
            story = activeStory,
            isOwnStory = activeStory.authorId == currentUser.userId,
            onDismiss = { viewingStory = null },
            onDelete = {
                onDeleteStory(activeStory.id)
                viewingStory = null
            }
        )
    }
}

@Composable
private fun StoryRowCard(
    story: StoryItem,
    isOwnStory: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    onAvatarClick: () -> Unit
) {
    val remainingHours = remember(story.expiresAtMillis) {
        val diff = (story.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        ((diff / (1000L * 60L * 60L)) + 1L).coerceAtMost(24L)
    }

    GlassSurface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("story_card_${story.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = story.authorName,
                avatarData = story.authorAvatar,
                size = 54.dp,
                storyRingBrush = Brush.linearGradient(
                    if (story.visibility == "public") listOf(AquaAccent, OnlineGreen)
                    else listOf(CobaltPrimary, CoralWarm)
                ),
                onClick = onAvatarClick
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isOwnStory) "My Story" else story.authorName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (story.visibility == "public") AquaAccent.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (story.visibility == "public") Icons.Rounded.Public else Icons.Rounded.Group,
                                contentDescription = null,
                                tint = if (story.visibility == "public") AquaAccent else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = if (story.visibility == "public") "Public" else "Friends",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (story.visibility == "public") AquaAccent else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                val subtitle = when (story.storyType) {
                    "music" -> "Music: ${story.musicTitle ?: "Track"} ${story.musicArtist?.let { "• $it" }.orEmpty()}"
                    "photo" -> story.caption.ifBlank { "Shared a photo story" }
                    "video" -> story.caption.ifBlank { "Shared a video clip story" }
                    else -> story.caption.ifBlank { "Text story" }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatTimeShort(story.createdAt)} • ${remainingHours}h left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isOwnStory && onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_story_${story.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete story",
                        tint = MissedRed
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryComposerDialog(
    onDismiss: () -> Unit,
    onPublish: (
        storyType: String,
        caption: String,
        mediaData: String?,
        musicTitle: String?,
        musicArtist: String?,
        backgroundHex: String,
        visibility: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var storyType by remember { mutableStateOf("text") }
    var visibility by remember { mutableStateOf("friends") }
    var caption by remember { mutableStateOf("") }
    var musicTitle by remember { mutableStateOf("") }
    var musicArtist by remember { mutableStateOf("") }
    var mediaData by remember { mutableStateOf<String?>(null) }
    var isProcessingMedia by remember { mutableStateOf(false) }

    val colorSwatches = remember {
        listOf("#4338CA", "#0EA5E9", "#0F766E", "#EA580C", "#7C3AED", "#1E1B4B")
    }
    var selectedHex by remember { mutableStateOf(colorSwatches.first()) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isProcessingMedia = true
                mediaData = ImageCodec.compressUriToBase64(context, uri, maxDimension = 540, quality = 70)
                isProcessingMedia = false
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isProcessingMedia = true
                mediaData = ImageCodec.extractVideoThumbnailBase64(context, uri, maxDimension = 540)
                isProcessingMedia = false
            }
        }
    }

    val canPublish = !isProcessingMedia && when (storyType) {
        "text" -> caption.trim().isNotBlank()
        "music" -> musicTitle.trim().isNotBlank()
        "photo", "video" -> !mediaData.isNullOrBlank()
        else -> false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Create 24h Story",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close story composer")
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Story type selector (Text, Photo, Video, Music)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val types = listOf(
                        Triple("text", "Text", Icons.Rounded.TextFields),
                        Triple("photo", "Photo", Icons.Rounded.Image),
                        Triple("video", "Video", Icons.Rounded.Videocam),
                        Triple("music", "Music", Icons.Rounded.MusicNote)
                    )
                    items(types) { (key, label, icon) ->
                        FilterChip(
                            selected = storyType == key,
                            onClick = {
                                storyType = key
                                if (key == "text" || key == "music") mediaData = null
                            },
                            label = { Text(label) },
                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Visibility selector: Friends vs Public
                Text(
                    text = "Who can view this story?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = visibility == "friends",
                        onClick = { visibility = "friends" },
                        label = { Text("Friends Only") },
                        leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("story_visibility_friends")
                    )
                    FilterChip(
                        selected = visibility == "public",
                        onClick = { visibility = "public" },
                        label = { Text("Public (Everyone)") },
                        leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("story_visibility_public")
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (storyType == "photo" || storyType == "video") {
                    OutlinedButton(
                        onClick = {
                            if (storyType == "photo") {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            } else {
                                videoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (storyType == "photo") Icons.Rounded.Image else Icons.Rounded.Videocam,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (storyType == "photo") "Pick Photo from Gallery" else "Pick Video from Gallery")
                    }
                    Spacer(Modifier.height(10.dp))
                    if (isProcessingMedia) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (!mediaData.isNullOrBlank()) {
                        val bmp = remember(mediaData) { ImageCodec.decodeBase64Bitmap(mediaData) }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Selected story media",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.4f)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        }
                    }
                } else {
                    // Background color picker for Text / Music stories
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        colorSwatches.forEach { hex ->
                            val swatchColor = parseColorSafe(hex)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (selectedHex == hex) 3.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedHex = hex }
                            )
                        }
                    }
                }

                if (storyType == "music") {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = musicTitle,
                        onValueChange = { if (it.length <= 120) musicTitle = it },
                        label = { Text("Song / Track Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = musicArtist,
                        onValueChange = { if (it.length <= 120) musicArtist = it },
                        label = { Text("Artist (Optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 500) caption = it },
                    label = {
                        Text(if (storyType == "text") "What's on your mind?" else "Caption (Optional)")
                    },
                    minLines = 3,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story_caption_input")
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        onPublish(
                            storyType,
                            caption,
                            mediaData,
                            musicTitle.ifBlank { null },
                            musicArtist.ifBlank { null },
                            selectedHex,
                            visibility
                        )
                    },
                    enabled = canPublish,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CobaltPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("publish_story_button")
                ) {
                    Text("Share Story (24h)", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun StoryViewerDialog(
    story: StoryItem,
    isOwnStory: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(story.id) {
        val totalMs = 7000L
        val step = 50L
        var elapsed = 0L
        while (elapsed < totalMs) {
            delay(step)
            elapsed += step
            progress = (elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        }
        onDismiss()
    }

    val bgColor = remember(story.backgroundHex) { parseColorSafe(story.backgroundHex) }
    val mediaBmp = remember(story.mediaData) { ImageCodec.decodeBase64Bitmap(story.mediaData) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(bgColor, Color(0xFF0B1120))
                    )
                )
        ) {
            if (mediaBmp != null) {
                Image(
                    bitmap = mediaBmp.asImageBitmap(),
                    contentDescription = "Story media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            name = story.authorName,
                            avatarData = story.authorAvatar,
                            size = 42.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = story.authorName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "${if (story.visibility == "public") "Public" else "Friends"} • ${formatTimeShort(story.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        if (isOwnStory) {
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete story", tint = Color.White)
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close story", tint = Color.White)
                        }
                    }
                }

                // Center showcase
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (story.storyType == "music") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = CoralWarm,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = story.musicTitle ?: "Now Playing",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White
                                    )
                                    if (!story.musicArtist.isNullOrBlank()) {
                                        Text(
                                            text = story.musicArtist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (story.storyType == "video") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.55f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Video Story", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (story.caption.isNotBlank()) {
                        Text(
                            text = story.caption,
                            style = if (story.storyType == "text") MaterialTheme.typography.displayMedium
                            else MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun parseColorSafe(hex: String): Color {
    return try {
        Color(AndroidColor.parseColor(hex))
    } catch (_: Exception) {
        CobaltPrimary
    }
}
