package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.core.ImageCodec
import com.example.core.LocalAccountStore
import com.example.core.LocalRegisteredUser
import com.example.core.attemptAutoSignIn
import com.example.core.onGoogleSignInClicked
import com.example.ui.components.GlassSurface
import com.example.ui.components.LinkoraGlassBackdrop
import com.example.ui.components.LinkoraLogoMark
import com.example.ui.components.UserAvatar
import com.example.ui.theme.AquaAccent
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.IndigoDeep
import com.example.ui.theme.MissedRed
import com.example.ui.theme.OnlineGreen
import com.example.viewmodel.UsernameCheckState
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onGoogleAuthSuccess: () -> Unit,
    onLocalAuthSuccess: (LocalRegisteredUser) -> Unit,
    onEnterDeveloperDemo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val localStore = remember(context) { LocalAccountStore(context) }

    var showSignUp by remember { mutableStateOf(false) }

    if (showSignUp) {
        SignUpScreen(
            localStore = localStore,
            onBackToLogin = { showSignUp = false },
            onSignUpSuccess = onLocalAuthSuccess
        )
        return
    }

    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onGoogleAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    LinkoraGlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                LinkoraLogoMark(size = 76.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Linkora",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Real-time chat, WebRTC calls, 24h stories & Linkora AI",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                GlassSurface(
                    shape = RoundedCornerShape(28.dp),
                    strong = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Log In",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Enter your email or @username and password",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        AuthErrorBanner(message = errorMessage.orEmpty())
                    }

                    Spacer(Modifier.height(18.dp))

                    OutlinedTextField(
                        value = identifier,
                        onValueChange = {
                            identifier = it
                            errorMessage = null
                        },
                        label = { Text("Email or Username") },
                        placeholder = { Text("name@email.com or ali_khan") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_identifier_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            localStore.login(identifier, password)
                                .onSuccess { user -> onLocalAuthSuccess(user) }
                                .onFailure { err -> errorMessage = err.localizedMessage ?: "Login failed." }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CobaltPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_submit_button")
                    ) {
                        Text(
                            text = "Log In",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "  OR  ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            errorMessage = null
                            isGoogleLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isGoogleLoading = false
                                    onGoogleAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isGoogleLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isGoogleLoading = false
                                }
                            )
                        },
                        enabled = !isGoogleLoading,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isGoogleLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Continue with Google",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Don't have an account?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                errorMessage = null
                                showSignUp = true
                            },
                            modifier = Modifier.testTag("open_signup_button")
                        ) {
                            Text(
                                text = "Sign Up",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Developer Demo Mode Frosted Glass Button
            GlassSurface(
                onClick = onEnterDeveloperDemo,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("developer_demo_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Developer Demo (Skip Login)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun SignUpScreen(
    localStore: LocalAccountStore,
    onBackToLogin: () -> Unit,
    onSignUpSuccess: (LocalRegisteredUser) -> Unit
) {
    BackHandler { onBackToLogin() }

    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cleanUsername = remember(username) { localStore.sanitizeUsername(username) }
    val usernameState = remember(username) {
        when {
            username.isBlank() -> UsernameCheckState.Idle
            cleanUsername.length < 3 -> UsernameCheckState.Invalid("At least 3 characters (a-z, 0-9, _)")
            localStore.isUsernameAvailable(cleanUsername) -> UsernameCheckState.Available(cleanUsername)
            else -> UsernameCheckState.Taken(cleanUsername)
        }
    }

    LinkoraGlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToLogin,
                        modifier = Modifier.testTag("signup_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Login")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(10.dp))

                LinkoraLogoMark(size = 64.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Join Linkora",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose your unique @username — we check availability instantly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))

                GlassSurface(
                    shape = RoundedCornerShape(28.dp),
                    strong = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (!errorMessage.isNullOrBlank()) {
                        AuthErrorBanner(message = errorMessage.orEmpty())
                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { if (it.length <= 80) fullName = it; errorMessage = null },
                        label = { Text("Full Name") },
                        placeholder = { Text("e.g. Ali Khan") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_fullname_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { raw ->
                            username = raw.lowercase().replace(Regex("[^a-z0-9_]"), "").take(30)
                            errorMessage = null
                        },
                        label = { Text("Unique Username") },
                        placeholder = { Text("e.g. ali_khan") },
                        leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                        trailingIcon = {
                            when (usernameState) {
                                is UsernameCheckState.Available -> Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Available",
                                    tint = OnlineGreen
                                )
                                is UsernameCheckState.Taken, is UsernameCheckState.Invalid -> Icon(
                                    imageVector = Icons.Rounded.Error,
                                    contentDescription = "Unavailable",
                                    tint = MissedRed
                                )
                                else -> Unit
                            }
                        },
                        supportingText = {
                            when (usernameState) {
                                is UsernameCheckState.Available -> Text(
                                    text = "@${usernameState.username} is available!",
                                    color = OnlineGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                                is UsernameCheckState.Taken -> Text(
                                    text = "@${usernameState.username} is already taken. Try another.",
                                    color = MissedRed
                                )
                                is UsernameCheckState.Invalid -> Text(
                                    text = usernameState.reason,
                                    color = MissedRed
                                )
                                else -> Text("Lowercase letters, numbers, and underscores only")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_username_input")
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text("Email Address") },
                        placeholder = { Text("you@example.com") },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_email_input")
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Create Password") },
                        placeholder = { Text("At least 6 characters") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_password_input")
                    )

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            localStore.register(fullName, username, email, password)
                                .onSuccess { newUser -> onSignUpSuccess(newUser) }
                                .onFailure { err -> errorMessage = err.localizedMessage ?: "Sign up failed." }
                        },
                        enabled = usernameState is UsernameCheckState.Available &&
                            fullName.isNotBlank() &&
                            email.isNotBlank() &&
                            password.length >= 6,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CobaltPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("signup_submit_button")
                    ) {
                        Text(
                            text = "Sign Up & Continue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Already have an account?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onBackToLogin) {
                            Text("Log In", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun AuthErrorBanner(message: String) {
    Surface(
        color = MissedRed.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Error,
                contentDescription = null,
                tint = MissedRed
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MissedRed
            )
        }
    }
}

@Composable
fun ProfileSetupScreen(
    initialFullName: String,
    initialAvatarUrl: String?,
    presetUsername: String? = null,
    usernameCheckState: UsernameCheckState,
    isSaving: Boolean,
    onCheckUsername: (String) -> Unit,
    onCompleteSetup: (fullName: String, username: String, bio: String, avatarData: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fullName by remember { mutableStateOf(initialFullName) }
    var username by remember { mutableStateOf(presetUsername.orEmpty()) }
    var bio by remember { mutableStateOf("") }
    var avatarData by remember { mutableStateOf(initialAvatarUrl) }
    var isCompressingPhoto by remember { mutableStateOf(false) }

    val hasPresetUsername = !presetUsername.isNullOrBlank()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isCompressingPhoto = true
                val encoded = ImageCodec.compressUriToBase64(context, uri, maxDimension = 300, quality = 70)
                if (encoded != null) {
                    avatarData = encoded
                }
                isCompressingPhoto = false
            }
        }
    }

    val usernameReady = hasPresetUsername || usernameCheckState is UsernameCheckState.Available
    val canSubmit = fullName.trim().isNotBlank() &&
        usernameReady &&
        !isSaving &&
        !isCompressingPhoto

    LinkoraGlassBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Set Up Your Profile",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (hasPresetUsername) {
                    "Upload a profile photo and add an optional bio (you can also skip bio)."
                } else {
                    "Add a photo, pick your unique Linkora @username, and write an optional bio."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .size(124.dp)
                    .testTag("upload_profile_photo_button")
                    .clickable {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(
                    name = fullName.ifBlank { "Linkora" },
                    avatarData = avatarData,
                    size = 114.dp,
                    storyRingBrush = Brush.linearGradient(listOf(CobaltPrimary, AquaAccent))
                )
                if (isCompressingPhoto) {
                    Box(
                        modifier = Modifier
                            .size(114.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = CobaltPrimary,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Upload profile photo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap to upload profile photo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (hasPresetUsername) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "@$presetUsername",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = { if (it.length <= 80) fullName = it },
                label = { Text("Full Name") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_fullname_input")
            )

            if (!hasPresetUsername) {
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { raw ->
                        val cleaned = raw.lowercase().replace(Regex("[^a-z0-9_]"), "").take(30)
                        username = cleaned
                        onCheckUsername(cleaned)
                    },
                    label = { Text("Unique Username") },
                    placeholder = { Text("e.g. ali_khan") },
                    leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                    trailingIcon = {
                        when (usernameCheckState) {
                            UsernameCheckState.Checking -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            is UsernameCheckState.Available -> Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Username available",
                                tint = OnlineGreen
                            )
                            is UsernameCheckState.Taken, is UsernameCheckState.Invalid -> Icon(
                                imageVector = Icons.Rounded.Error,
                                contentDescription = "Username unavailable",
                                tint = MissedRed
                            )
                            UsernameCheckState.Idle -> Unit
                        }
                    },
                    supportingText = {
                        when (usernameCheckState) {
                            UsernameCheckState.Checking -> Text("Checking availability...")
                            is UsernameCheckState.Available -> Text(
                                text = "@${usernameCheckState.username} is available!",
                                color = OnlineGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            is UsernameCheckState.Taken -> Text(
                                text = "@${usernameCheckState.username} is already taken. Try another.",
                                color = MissedRed
                            )
                            is UsernameCheckState.Invalid -> Text(
                                text = usernameCheckState.reason,
                                color = MissedRed
                            )
                            UsernameCheckState.Idle -> Text("Letters, numbers, and underscores only (unique on Linkora)")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_username_input")
                )
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 250) bio = it },
                label = { Text("Bio (Optional)") },
                placeholder = { Text("Write something about yourself, or leave blank...") },
                minLines = 3,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_bio_input")
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    onCompleteSetup(fullName, username, bio, avatarData)
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CobaltPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("complete_profile_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "Enter Linkora",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
    }
}
