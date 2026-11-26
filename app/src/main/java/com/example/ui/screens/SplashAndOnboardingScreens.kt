package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.LinkoraLogoMark
import com.example.ui.components.OnboardingArtwork
import com.example.ui.theme.AquaBright
import com.example.ui.theme.IndigoDeep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LinkoraSplashScreen(
    onFinished: () -> Unit
) {
    val logoScale = remember { Animatable(0.45f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(24f) }
    val subtitleAlpha = remember { Animatable(0f) }

    val pulseTransition = rememberInfiniteTransition(label = "splash_pulse")
    val ringScale by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    LaunchedEffect(Unit) {
        launch {
            logoScale.animateTo(1f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 260f))
        }
        delay(220L)
        launch {
            titleAlpha.animateTo(1f, animationSpec = tween(550))
        }
        launch {
            titleOffset.animateTo(0f, animationSpec = tween(550, easing = FastOutSlowInEasing))
        }
        delay(320L)
        subtitleAlpha.animateTo(1f, animationSpec = tween(450))
        delay(1050L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E3A8A), IndigoDeep, Color(0xFF0B1120))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(ringScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AquaBright.copy(alpha = 0.30f), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            LinkoraLogoMark(
                size = 98.dp,
                modifier = Modifier.scale(logoScale.value)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Linkora",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .padding(top = titleOffset.value.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Chat • Calls • Stories • AI",
                style = MaterialTheme.typography.titleMedium,
                color = AquaBright,
                modifier = Modifier.alpha(subtitleAlpha.value)
            )
        }
    }
}

private data class OnboardingSlide(
    val badge: String,
    val title: String,
    val description: String
)

/**
 * Full-screen edge-to-edge classic onboarding where every illustration fills 100% of the screen,
 * paired with a clean translucent top header and a smooth dark bottom scrim for crisp readability.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val slides = remember {
        listOf(
            OnboardingSlide(
                badge = "REAL-TIME CHAT & VOICE",
                title = "Instant Messaging & Voice Notes",
                description = "Chat live with typing indicators, crystal-clear voice notes, green online status, and instant delivered/seen ticks."
            ),
            OnboardingSlide(
                badge = "AUDIO & VIDEO CALLS",
                title = "Face-to-Face Anywhere",
                description = "Start real audio and video calls directly from any chat with smooth controls and complete call history."
            ),
            OnboardingSlide(
                badge = "24H STORIES & LINKORA AI",
                title = "Stories & Smart Multilingual AI",
                description = "Share public or friends-only 24-hour stories and chat naturally with Linkora AI in Roman Urdu or any language."
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // 100% Full-Screen Edge-to-Edge Pager Artwork
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingArtwork(
                pageIndex = page,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Smooth bottom editorial scrim so text & CTA pop cleanly over full-screen artwork
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.28f),
                        0.28f to Color.Transparent,
                        0.52f to Color(0x99070B17),
                        0.76f to Color(0xE8070B17),
                        1.0f to Color(0xFA060914)
                    )
                )
        )

        // Top Bar overlay (Brand + Skip pill)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinkoraLogoMark(size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Linkora",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                onClick = onComplete,
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.16f),
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Bottom Content Overlay (Badge, Title, Description, Indicators, Primary CTA)
        val slide = slides[pagerState.currentPage]
        val isLastPage = pagerState.currentPage == slides.lastIndex

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .navigationBarsPadding()
                .padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AquaBright.copy(alpha = 0.18f)
            ) {
                Text(
                    text = slide.badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = AquaBright,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = slide.title,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = slide.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.84f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Animated Page Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(slides.size) { index ->
                    val selected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (selected) 32.dp else 8.dp,
                        label = "indicator_width"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (selected) AquaBright else Color.White.copy(alpha = 0.35f))
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = {
                    if (isLastPage) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0F172A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_next_button")
            ) {
                Text(
                    text = if (isLastPage) "Get Started" else "Next",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null
                )
            }
        }
    }
}
