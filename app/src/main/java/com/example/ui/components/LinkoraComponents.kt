package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.core.ImageCodec
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.Shape
import com.example.data.model.ChatWallpaperStyle
import com.example.data.model.FullScreenAvatarPayload
import com.example.ui.theme.AquaAccent
import com.example.ui.theme.AquaBright
import com.example.ui.theme.CobaltLight
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.CoralWarm
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.LightBackground
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.OfflineGray
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.SeenCyan
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clean solid Material 3 screen backdrop honoring Light Theme (default) and Dark Theme.
 */
@Composable
fun LinkoraGlassBackdrop(
    modifier: Modifier = Modifier,
    wallpaper: ChatWallpaperStyle = ChatWallpaperStyle.CLASSIC_GLASS,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val backgroundColor = when (wallpaper) {
        ChatWallpaperStyle.CLASSIC_GLASS -> if (isDark) DarkBackground else LightBackground
        ChatWallpaperStyle.MIDNIGHT_MIST -> if (isDark) Color(0xFF0A1226) else Color(0xFFF3F6FD)
        ChatWallpaperStyle.AURORA_SILK -> if (isDark) Color(0xFF09161B) else Color(0xFFF2FAF7)
        ChatWallpaperStyle.OBSIDIAN_VELVET -> if (isDark) Color(0xFF090D16) else Color(0xFFFFFFFF)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        content = content
    )
}

/**
 * Clean opaque Material 3 card/bar surface with crisp subtle border and soft elevation.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    strong: Boolean = false,
    accentTint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val fill = accentTint ?: MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (isDark) 0.55f else 0.72f
    )
    val elevation = when {
        isDark -> 0.dp
        strong -> 2.dp
        else -> 1.dp
    }

    val borderedModifier = modifier
        .clip(shape)
        .border(width = 1.dp, color = borderColor, shape = shape)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = fill,
            tonalElevation = 0.dp,
            shadowElevation = elevation,
            modifier = borderedModifier
        ) {
            content()
        }
    } else {
        Surface(
            shape = shape,
            color = fill,
            tonalElevation = 0.dp,
            shadowElevation = elevation,
            modifier = borderedModifier
        ) {
            content()
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        shape = shape,
        strong = strong,
        onClick = onClick
    ) {
        Column(content = content)
    }
}

/**
 * Brand-new Linkora Chat Bubble logo mark matching the adaptive launcher icon:
 * electric blue-indigo-cyan gradient squircle with a crisp layered white chat bubble and 3 colorful dots.
 */
@Composable
fun LinkoraLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2563EB), Color(0xFF4F46E5), Color(0xFF06B6D4))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.14f)) {
            val w = this.size.width
            val h = this.size.height

            // Subtle back reply bubble
            drawRoundRect(
                color = Color(0xFF38BDF8).copy(alpha = 0.55f),
                topLeft = Offset(w * 0.34f, h * 0.08f),
                size = Size(w * 0.58f, h * 0.44f),
                cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
            )

            // Main foreground white speech bubble
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.06f, h * 0.22f),
                size = Size(w * 0.78f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.22f, w * 0.22f)
            )

            // Speech bubble pointer tail (bottom-left)
            val tail = Path().apply {
                moveTo(w * 0.18f, h * 0.72f)
                lineTo(w * 0.08f, h * 0.90f)
                lineTo(w * 0.36f, h * 0.76f)
                close()
            }
            drawPath(tail, color = Color.White)

            // Three colorful animated-style chat dots inside the bubble
            val dotY = h * 0.50f
            val dotRadius = w * 0.07f
            drawCircle(color = Color(0xFF2563EB), radius = dotRadius, center = Offset(w * 0.28f, dotY))
            drawCircle(color = Color(0xFF4F46E5), radius = dotRadius, center = Offset(w * 0.45f, dotY))
            drawCircle(color = Color(0xFF06B6D4), radius = dotRadius, center = Offset(w * 0.62f, dotY))
        }
    }
}

/**
 * Animated bouncing 3-dot typing indicator bubble displayed inside the chat conversation.
 */
@Composable
fun TypingIndicatorBubble(
    senderName: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("typing_indicator_bubble"),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassSurface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 6.dp,
                bottomEnd = 20.dp
            ),
            strong = true
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypingDotsInline(dotColor = MaterialTheme.colorScheme.primary, dotSize = 7.dp)
                Text(
                    text = "${senderName.substringBefore(" ")} is typing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TypingDotsInline(
    dotColor: Color = OnlineGreen,
    dotSize: Dp = 6.dp
) {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val bounce by transition.animateFloat(
                initialValue = 0f,
                targetValue = -4.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "dot_$index"
            )
            val alpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150)
                ),
                label = "dot_alpha_$index"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .offset(y = bounce.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

@Composable
fun UserAvatar(
    name: String,
    avatarData: String?,
    size: Dp = 50.dp,
    showOnlineIndicator: Boolean = false,
    isOnline: Boolean = false,
    storyRingBrush: Brush? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(avatarData) { ImageCodec.decodeBase64Bitmap(avatarData) }
    val initials = remember(name) {
        name.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "L" }
    }
    val avatarGradient = remember(name) {
        val palettes = listOf(
            listOf(CobaltPrimary, AquaAccent),
            listOf(Color(0xFF7C3AED), Color(0xFFEC4899)),
            listOf(Color(0xFF0F766E), Color(0xFF10B981)),
            listOf(Color(0xFFEA580C), Color(0xFFF59E0B)),
            listOf(IndigoDeep, CobaltLight)
        )
        val idx = (name.hashCode() and Int.MAX_VALUE) % palettes.size
        Brush.linearGradient(palettes[idx])
    }

    Box(
        modifier = modifier
            .size(size)
            .let { base ->
                if (onClick != null) base.clickable(onClick = onClick) else base
            },
        contentAlignment = Alignment.Center
    ) {
        val ringMod = if (storyRingBrush != null) {
            Modifier
                .fillMaxSize()
                .border(2.5.dp, storyRingBrush, CircleShape)
                .padding(3.dp)
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = ringMod
                .clip(CircleShape)
                .background(avatarGradient),
            contentAlignment = Alignment.Center
        ) {
            when {
                bitmap != null -> {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "$name profile photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                !avatarData.isNullOrBlank() && (avatarData.startsWith("http://") || avatarData.startsWith("https://")) -> {
                    AsyncImage(
                        model = avatarData,
                        contentDescription = "$name profile photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    Text(
                        text = initials,
                        style = if (size >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (showOnlineIndicator) {
            val dotSize = (size * 0.28f).coerceIn(11.dp, 18.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) OnlineGreen else OfflineGray)
            )
        }
    }
}

@Composable
fun FullScreenAvatarDialog(
    payload: FullScreenAvatarPayload,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0070B14))
                .clickable(onClick = onDismiss)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = payload.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (payload.username.isNotBlank()) {
                        Text(
                            text = "@${payload.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AquaBright
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_fullscreen_avatar")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close full-screen photo",
                        tint = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(enabled = false) {}
            ) {
                val bitmap = remember(payload.avatarData) { ImageCodec.decodeBase64Bitmap(payload.avatarData) }
                when {
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "${payload.displayName} full profile image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    !payload.avatarData.isNullOrBlank() && payload.avatarData.startsWith("http") -> {
                        AsyncImage(
                            model = payload.avatarData,
                            contentDescription = "${payload.displayName} full profile image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(listOf(IndigoDeep, CobaltPrimary, AquaAccent))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = payload.displayName.take(2).uppercase(),
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusTicks(
    status: String,
    tintDefault: Color
) {
    when (status) {
        "seen" -> Icon(
            imageVector = Icons.Rounded.DoneAll,
            contentDescription = "Seen",
            tint = SeenCyan,
            modifier = Modifier.size(16.dp)
        )
        "delivered" -> Icon(
            imageVector = Icons.Rounded.DoneAll,
            contentDescription = "Delivered",
            tint = tintDefault,
            modifier = Modifier.size(16.dp)
        )
        else -> Icon(
            imageVector = Icons.Rounded.Done,
            contentDescription = "Sent",
            tint = tintDefault,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun VoiceNoteBubbleContent(
    durationSec: Long,
    isPlaying: Boolean,
    isMine: Boolean,
    onTogglePlay: () -> Unit
) {
    val contentColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val accentBg = if (isMine) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Surface(
            onClick = onTogglePlay,
            shape = CircleShape,
            color = accentBg,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice message" else "Play voice message",
                    tint = contentColor
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Canvas(
            modifier = Modifier
                .width(116.dp)
                .height(26.dp)
        ) {
            val bars = listOf(0.35f, 0.7f, 0.5f, 0.95f, 0.6f, 0.85f, 0.4f, 0.75f, 0.9f, 0.5f, 0.65f, 0.35f)
            val barWidth = size.width / (bars.size * 1.8f)
            val gap = (size.width - bars.size * barWidth) / (bars.size - 1).coerceAtLeast(1)
            bars.forEachIndexed { idx, frac ->
                val barH = size.height * frac
                val left = idx * (barWidth + gap)
                val top = (size.height - barH) / 2f
                drawRoundRect(
                    color = if (isPlaying && idx % 2 == 0) AquaBright else contentColor.copy(alpha = 0.85f),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(durationSec),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

/**
 * Full-screen edge-to-edge editorial onboarding artwork filling 100% of the screen.
 * Key graphic storytelling elements sit cleanly in the upper 60% so the bottom text overlay never clips them.
 */
@Composable
fun OnboardingArtwork(pageIndex: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                when (pageIndex) {
                    0 -> Brush.verticalGradient(
                        listOf(Color(0xFF0F172A), Color(0xFF1D4ED8), Color(0xFF06B6D4), Color(0xFF090E1E))
                    )
                    1 -> Brush.verticalGradient(
                        listOf(Color(0xFF042F2E), Color(0xFF0F766E), Color(0xFF0284C7), Color(0xFF07131D))
                    )
                    else -> Brush.verticalGradient(
                        listOf(Color(0xFF1E1B4B), Color(0xFF6D28D9), Color(0xFFDB2777), Color(0xFF0D0A1F))
                    )
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Expansive atmospheric glowing halos
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.82f, h * 0.16f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.82f, h * 0.16f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AquaBright.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(w * 0.14f, h * 0.46f),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.14f, h * 0.46f)
            )

            // Subtle architectural grid lines
            for (i in 1..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = w * (0.22f * i),
                    center = Offset(w * 0.5f, h * 0.34f),
                    style = Stroke(width = 2f)
                )
            }

            when (pageIndex) {
                0 -> {
                    // Full-screen Real-Time Messaging + Typing + Voice Notes scene
                    // Floating incoming chat card
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.96f),
                        topLeft = Offset(w * 0.08f, h * 0.18f),
                        size = Size(w * 0.66f, h * 0.13f),
                        cornerRadius = CornerRadius(38f, 38f)
                    )
                    drawCircle(
                        color = Color(0xFF2563EB),
                        radius = w * 0.062f,
                        center = Offset(w * 0.18f, h * 0.245f)
                    )
                    drawCircle(
                        color = OnlineGreen,
                        radius = w * 0.018f,
                        center = Offset(w * 0.225f, h * 0.275f)
                    )
                    drawRoundRect(
                        color = IndigoDeep.copy(alpha = 0.22f),
                        topLeft = Offset(w * 0.28f, h * 0.22f),
                        size = Size(w * 0.36f, h * 0.018f),
                        cornerRadius = CornerRadius(14f, 14f)
                    )
                    drawRoundRect(
                        color = IndigoDeep.copy(alpha = 0.12f),
                        topLeft = Offset(w * 0.28f, h * 0.255f),
                        size = Size(w * 0.25f, h * 0.015f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Outgoing vibrant voice note bubble
                    drawRoundRect(
                        color = Color(0xFF22D3EE),
                        topLeft = Offset(w * 0.24f, h * 0.34f),
                        size = Size(w * 0.68f, h * 0.125f),
                        cornerRadius = CornerRadius(38f, 38f)
                    )
                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = w * 0.045f,
                        center = Offset(w * 0.33f, h * 0.402f)
                    )
                    val heights = listOf(0.028f, 0.062f, 0.040f, 0.072f, 0.050f, 0.068f, 0.036f, 0.056f, 0.030f)
                    heights.forEachIndexed { idx, hf ->
                        val bx = w * 0.42f + idx * (w * 0.048f)
                        val bh = h * hf
                        drawRoundRect(
                            color = Color(0xFF0F172A),
                            topLeft = Offset(bx, h * 0.402f - bh / 2f),
                            size = Size(w * 0.022f, bh),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                    }

                    // Live typing indicator pill floating below
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.92f),
                        topLeft = Offset(w * 0.12f, h * 0.50f),
                        size = Size(w * 0.34f, h * 0.056f),
                        cornerRadius = CornerRadius(32f, 32f)
                    )
                    drawCircle(color = CobaltPrimary, radius = w * 0.016f, center = Offset(w * 0.21f, h * 0.528f))
                    drawCircle(color = Color(0xFF4F46E5), radius = w * 0.016f, center = Offset(w * 0.29f, h * 0.528f))
                    drawCircle(color = Color(0xFF06B6D4), radius = w * 0.016f, center = Offset(w * 0.37f, h * 0.528f))

                    // Decorative warm spark
                    drawCircle(color = CoralWarm, radius = w * 0.042f, center = Offset(w * 0.84f, h * 0.21f))
                }

                1 -> {
                    // Full-screen HD Audio & Video Calls scene
                    // Large primary video card
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.95f),
                        topLeft = Offset(w * 0.10f, h * 0.16f),
                        size = Size(w * 0.52f, h * 0.36f),
                        cornerRadius = CornerRadius(44f, 44f)
                    )
                    drawCircle(
                        color = CobaltPrimary,
                        radius = w * 0.11f,
                        center = Offset(w * 0.36f, h * 0.29f)
                    )
                    drawRoundRect(
                        color = AquaAccent.copy(alpha = 0.28f),
                        topLeft = Offset(w * 0.21f, h * 0.39f),
                        size = Size(w * 0.30f, h * 0.085f),
                        cornerRadius = CornerRadius(32f, 32f)
                    )

                    // Floating Picture-in-Picture caller card
                    drawRoundRect(
                        color = Color(0xFF0F172A),
                        topLeft = Offset(w * 0.50f, h * 0.25f),
                        size = Size(w * 0.40f, h * 0.31f),
                        cornerRadius = CornerRadius(40f, 40f)
                    )
                    drawCircle(
                        color = CoralWarm,
                        radius = w * 0.088f,
                        center = Offset(w * 0.70f, h * 0.365f)
                    )
                    drawCircle(
                        color = OnlineGreen,
                        radius = w * 0.044f,
                        center = Offset(w * 0.70f, h * 0.50f)
                    )

                    // Acoustic wave arcs
                    for (r in 1..3) {
                        drawArc(
                            color = AquaBright.copy(alpha = 0.80f - r * 0.18f),
                            startAngle = -42f,
                            sweepAngle = 84f,
                            useCenter = false,
                            topLeft = Offset(w * 0.56f - r * 20f, h * 0.12f - r * 14f),
                            size = Size(w * 0.22f + r * 40f, h * 0.12f + r * 28f),
                            style = Stroke(width = 7f, cap = StrokeCap.Round)
                        )
                    }
                }

                else -> {
                    // Full-screen 24h Stories + Radiant Linkora AI Constellation
                    val cx = w * 0.50f
                    val cy = h * 0.35f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.24f),
                        radius = w * 0.34f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 8f)
                    )
                    drawCircle(
                        color = AquaBright.copy(alpha = 0.65f),
                        radius = w * 0.22f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 6f)
                    )

                    // Luminous 4-point AI star
                    val outer = w * 0.18f
                    val inner = w * 0.052f
                    val starPath = Path().apply {
                        moveTo(cx, cy - outer)
                        lineTo(cx + inner, cy - inner)
                        lineTo(cx + outer, cy)
                        lineTo(cx + inner, cy + inner)
                        lineTo(cx, cy + outer)
                        lineTo(cx - inner, cy + inner)
                        lineTo(cx - outer, cy)
                        lineTo(cx - inner, cy - inner)
                        close()
                    }
                    drawPath(starPath, color = Color.White)

                    // Orbiting story rings
                    drawCircle(color = Color(0xFF34D399), radius = w * 0.075f, center = Offset(w * 0.18f, h * 0.22f))
                    drawCircle(color = Color(0xFF38BDF8), radius = w * 0.068f, center = Offset(w * 0.84f, h * 0.24f))
                    drawCircle(color = Color(0xFFFDE047), radius = w * 0.062f, center = Offset(w * 0.78f, h * 0.49f))
                    drawCircle(color = Color(0xFFF472B6), radius = w * 0.058f, center = Offset(w * 0.22f, h * 0.48f))
                }
            }
        }
    }
}

fun formatTimeShort(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return ""
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}

fun formatDateTime(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", mins, secs)
}
