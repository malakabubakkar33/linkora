package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.AppNotificationState
import com.example.core.LinkoraMessageService
import com.example.core.LocalAccountStore
import com.example.core.LocalRegisteredUser
import com.example.core.NotificationHelper
import com.example.core.PreferencesManager
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.core.signOutUser
import com.example.data.model.FontScaleOption
import com.example.data.model.UiState
import com.example.data.repository.LinkoraAiRepository
import com.example.data.repository.LinkoraRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LinkoraHomeScreen
import com.example.ui.screens.LinkoraSplashScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.theme.LinkoraTheme
import com.example.viewmodel.LinkoraAiViewModel
import com.example.viewmodel.LinkoraViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingOpenChatIdFlow = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure App Check with launch intent before any UI, ViewModels, or Firebase operations.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        NotificationHelper.createChannels(this)
        extractNotificationChatId(intent)

        setContent {
            val prefs = remember { PreferencesManager(applicationContext) }
            val darkMode by prefs.darkMode.collectAsStateWithLifecycle(initialValue = false)
            val onboardingDone by prefs.onboardingDone.collectAsStateWithLifecycle(initialValue = false)
            val fontScaleKey by prefs.fontScale.collectAsStateWithLifecycle(initialValue = "standard")
            val fontScale = remember(fontScaleKey) { FontScaleOption.fromKey(fontScaleKey).scale }
            val pendingOpenChatId by pendingOpenChatIdFlow.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            LaunchedEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkMode) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkMode) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
            }

            LinkoraTheme(darkTheme = darkMode, fontScaleMultiplier = fontScale) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LinkoraAppRoot(
                        onboardingDone = onboardingDone,
                        darkMode = darkMode,
                        pendingOpenChatId = pendingOpenChatId,
                        onConsumePendingOpenChatId = { pendingOpenChatIdFlow.value = null },
                        onCompleteOnboarding = {
                            scope.launch { prefs.setOnboardingDone(true) }
                        },
                        onToggleDarkMode = { enabled ->
                            scope.launch { prefs.setDarkMode(enabled) }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractNotificationChatId(intent)
    }

    override fun onResume() {
        super.onResume()
        AppNotificationState.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        AppNotificationState.isAppInForeground = false
    }

    private fun extractNotificationChatId(intent: Intent?) {
        val chatId = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_CHAT_ID)
        if (!chatId.isNullOrBlank()) {
            pendingOpenChatIdFlow.value = chatId
            intent.removeExtra(NotificationHelper.EXTRA_OPEN_CHAT_ID)
        }
    }
}

private sealed interface LocalSessionMode {
    data object None : LocalSessionMode
    data class RegisteredLocal(val user: LocalRegisteredUser) : LocalSessionMode
    data object DeveloperDemo : LocalSessionMode
}

@Composable
private fun LinkoraAppRoot(
    onboardingDone: Boolean,
    darkMode: Boolean,
    pendingOpenChatId: String?,
    onConsumePendingOpenChatId: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val localStore = remember(context) { LocalAccountStore(context) }

    var splashCompleted by remember { mutableStateOf(false) }
    var localSession by remember {
        val activeLocal = localStore.getActiveLocalUser()
        mutableStateOf<LocalSessionMode>(
            when {
                localStore.isDeveloperDemoActive() -> LocalSessionMode.DeveloperDemo
                activeLocal != null -> LocalSessionMode.RegisteredLocal(activeLocal)
                else -> LocalSessionMode.None
            }
        )
    }

    if (!splashCompleted) {
        LinkoraSplashScreen(onFinished = { splashCompleted = true })
        return
    }

    if (!onboardingDone) {
        OnboardingScreen(onComplete = onCompleteOnboarding)
        return
    }

    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    when {
        user != null -> {
            key("cloud_${user.uid}") {
                CloudAuthenticatedSession(
                    firebaseUser = user,
                    darkMode = darkMode,
                    pendingOpenChatId = pendingOpenChatId,
                    onConsumePendingOpenChatId = onConsumePendingOpenChatId,
                    onToggleDarkMode = onToggleDarkMode
                )
            }
        }
        localSession is LocalSessionMode.RegisteredLocal -> {
            val reg = (localSession as LocalSessionMode.RegisteredLocal).user
            key("local_${reg.uid}") {
                LocalOrDemoLinkoraSession(
                    sessionKey = reg.uid,
                    localUser = reg,
                    isDeveloperDemo = false,
                    darkMode = darkMode,
                    pendingOpenChatId = pendingOpenChatId,
                    onConsumePendingOpenChatId = onConsumePendingOpenChatId,
                    onToggleDarkMode = onToggleDarkMode,
                    onSignOut = {
                        localStore.clearSession()
                        localSession = LocalSessionMode.None
                    }
                )
            }
        }
        localSession is LocalSessionMode.DeveloperDemo -> {
            key("developer_demo_session") {
                LocalOrDemoLinkoraSession(
                    sessionKey = "demo_dev_user",
                    localUser = null,
                    isDeveloperDemo = true,
                    darkMode = darkMode,
                    pendingOpenChatId = pendingOpenChatId,
                    onConsumePendingOpenChatId = onConsumePendingOpenChatId,
                    onToggleDarkMode = onToggleDarkMode,
                    onSignOut = {
                        localStore.clearSession()
                        localSession = LocalSessionMode.None
                    }
                )
            }
        }
        else -> {
            AuthScreen(
                onGoogleAuthSuccess = {
                    localStore.clearSession()
                    localSession = LocalSessionMode.None
                },
                onLocalAuthSuccess = { registered ->
                    localSession = LocalSessionMode.RegisteredLocal(registered)
                },
                onEnterDeveloperDemo = {
                    localStore.enterDeveloperDemo()
                    localSession = LocalSessionMode.DeveloperDemo
                }
            )
        }
    }
}

@Composable
private fun CloudAuthenticatedSession(
    firebaseUser: FirebaseUser,
    darkMode: Boolean,
    pendingOpenChatId: String?,
    onConsumePendingOpenChatId: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val uid = firebaseUser.uid

    val linkoraViewModel: LinkoraViewModel = viewModel(
        key = "linkora_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                LinkoraViewModel(LinkoraRepository(db), uid, app.applicationContext)
            }
        }
    )

    val aiViewModel: LinkoraAiViewModel = viewModel(
        key = "linkora_ai_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                LinkoraAiViewModel(LinkoraRepository(db), LinkoraAiRepository(), uid)
            }
        }
    )

    // Keep online/offline presence synced with Android lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, uid) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> linkoraViewModel.setOnlinePresence(true)
                Lifecycle.Event.ON_STOP -> linkoraViewModel.setOnlinePresence(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val accountState by linkoraViewModel.userAccountState.collectAsStateWithLifecycle()
    val usernameCheckState by linkoraViewModel.usernameCheckState.collectAsStateWithLifecycle()
    val isSavingProfile by linkoraViewModel.isSavingProfile.collectAsStateWithLifecycle()

    when (val state = accountState) {
        UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading your Linkora profile...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Connection Issue",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            signOutUser(credentialManager = credentialManager, scope = scope)
                        }
                    ) {
                        Text("Sign Out & Retry")
                    }
                }
            }
        }
        is UiState.Success -> {
            val account = state.data
            if (account == null || !account.profileCompleted) {
                ProfileSetupScreen(
                    initialFullName = account?.fullName?.ifBlank { null }
                        ?: firebaseUser.displayName.orEmpty(),
                    initialAvatarUrl = account?.avatarData ?: firebaseUser.photoUrl?.toString(),
                    presetUsername = null,
                    usernameCheckState = usernameCheckState,
                    isSaving = isSavingProfile,
                    onCheckUsername = linkoraViewModel::checkUsernameLive,
                    onCompleteSetup = linkoraViewModel::saveInitialProfile
                )
            } else {
                LaunchedEffect(account.userId, account.notificationsEnabled) {
                    if (account.notificationsEnabled) {
                        LinkoraMessageService.start(context)
                    } else {
                        LinkoraMessageService.stop(context)
                    }
                }
                LinkoraHomeScreen(
                    account = account,
                    sessionModeLabel = "Google Cloud Sync",
                    viewModel = linkoraViewModel,
                    aiViewModel = aiViewModel,
                    darkMode = darkMode,
                    pendingOpenChatId = pendingOpenChatId,
                    onConsumePendingOpenChatId = onConsumePendingOpenChatId,
                    onToggleDarkMode = onToggleDarkMode,
                    onSignOut = {
                        LinkoraMessageService.stop(context)
                        AppNotificationState.clearAll(context)
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            beforeSignOut = {
                                val db = FirebaseFirestore.getInstance(
                                    context.applicationContext.getString(R.string.firestore_database_id)
                                )
                                LinkoraRepository(db).setOnlineStatus(false)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LocalOrDemoLinkoraSession(
    sessionKey: String,
    localUser: LocalRegisteredUser?,
    isDeveloperDemo: Boolean,
    darkMode: Boolean,
    pendingOpenChatId: String?,
    onConsumePendingOpenChatId: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val linkoraViewModel: LinkoraViewModel = viewModel(
        key = "local_linkora_$sessionKey",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                LinkoraViewModel(
                    repository = null,
                    currentUserId = sessionKey,
                    appContext = app.applicationContext,
                    localUser = localUser,
                    isDeveloperDemo = isDeveloperDemo
                )
            }
        }
    )

    // Real Firebase AI Logic works seamlessly here via App Check (no Firebase Auth required)
    val aiViewModel: LinkoraAiViewModel = viewModel(
        key = "local_linkora_ai_$sessionKey",
        factory = viewModelFactory {
            initializer {
                LinkoraAiViewModel(
                    repository = null,
                    aiRepository = LinkoraAiRepository(),
                    currentUserId = sessionKey
                )
            }
        }
    )

    val accountState by linkoraViewModel.userAccountState.collectAsStateWithLifecycle()
    val usernameCheckState by linkoraViewModel.usernameCheckState.collectAsStateWithLifecycle()
    val isSavingProfile by linkoraViewModel.isSavingProfile.collectAsStateWithLifecycle()

    val account = (accountState as? UiState.Success)?.data ?: return

    if (!account.profileCompleted) {
        ProfileSetupScreen(
            initialFullName = account.fullName,
            initialAvatarUrl = account.avatarData,
            presetUsername = account.username.ifBlank { null },
            usernameCheckState = usernameCheckState,
            isSaving = isSavingProfile,
            onCheckUsername = linkoraViewModel::checkUsernameLive,
            onCompleteSetup = linkoraViewModel::saveInitialProfile
        )
    } else {
        LinkoraHomeScreen(
            account = account,
            sessionModeLabel = if (isDeveloperDemo) "Developer Demo Mode" else "Local Account",
            viewModel = linkoraViewModel,
            aiViewModel = aiViewModel,
            darkMode = darkMode,
            pendingOpenChatId = pendingOpenChatId,
            onConsumePendingOpenChatId = onConsumePendingOpenChatId,
            onToggleDarkMode = onToggleDarkMode,
            onSignOut = onSignOut
        )
    }
}
