package com.example.core

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.LinkoraRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val BG_TAG = "LinkoraMsgService"

/**
 * Persistent foreground service (`remoteMessaging`) that keeps Firestore snapshot listeners
 * active even when the Linkora app UI is closed, backgrounded, or the phone screen is off.
 * Immediately delivers incoming messages, calls, and friend requests to the mobile screen
 * with sound, vibration, and lock-screen heads-up popups.
 */
class LinkoraMessageService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenersJob: Job? = null
    private var activeUid: String? = null

    @Volatile
    private var notificationsAllowed: Boolean = true

    @Volatile
    private var blockedUids: Set<String> = emptySet()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentUser = try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        }

        if (currentUser == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Promote to foreground immediately within the Android 5-second budget
        val fgNotification = NotificationHelper.buildBackgroundServiceNotification(this)
        try {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NotificationHelper.SERVICE_NOTIFICATION_ID,
                fgNotification,
                fgsType
            )
        } catch (e: Exception) {
            Log.w(BG_TAG, "Unable to enter foreground mode: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        if (activeUid != currentUser.uid || listenersJob?.isActive != true) {
            activeUid = currentUser.uid
            attachRealtimeListeners(currentUser.uid)
        }

        return START_STICKY
    }

    private fun attachRealtimeListeners(uid: String) {
        listenersJob?.cancel()
        val databaseId = getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val repository = LinkoraRepository(db)
        val prefs = PreferencesManager(applicationContext)

        listenersJob = serviceScope.launch {
            // 1. Track user's notification preference & blocked list in real time
            launch {
                repository.observeUserAccount(uid)
                    .catch { Log.w(BG_TAG, "Account listener error: ${it.message}") }
                    .collect { account ->
                        if (account != null) {
                            notificationsAllowed = account.notificationsEnabled
                            blockedUids = account.blockedUids.toSet()
                            if (!account.notificationsEnabled) {
                                stopSelf()
                            }
                        }
                    }
            }

            // 2. Listen for incoming real-time chat messages while app is closed or backgrounded
            launch {
                var firstChatsBatch = true
                repository.observeChats(uid)
                    .catch { Log.w(BG_TAG, "Chats listener error: ${it.message}") }
                    .collect { chats ->
                        chats.forEach { chat ->
                            val ts = chat.lastMessageAt?.toDate()?.time ?: 0L
                            val key = "chat_${chat.id}"
                            if (firstChatsBatch) {
                                // Only seed if never recorded before, so messages received during downtime still notify
                                AppNotificationState.primeTimestampIfUnset(applicationContext, key, ts)
                            }
                            val isMuted = chat.isArchivedFor(uid) ||
                                chat.isLockedFor(uid) ||
                                chat.isHiddenFor(uid) ||
                                chat.lastMessageSenderId in blockedUids

                            val isIncoming = chat.lastMessageSenderId.isNotBlank() &&
                                chat.lastMessageSenderId != uid &&
                                chat.lastMessageStatus != "seen"

                            // Deliver system heads-up + sound + screen wake when app is closed or backgrounded
                            if (!firstChatsBatch &&
                                notificationsAllowed &&
                                !isMuted &&
                                isIncoming &&
                                !AppNotificationState.isAppInForeground &&
                                AppNotificationState.shouldNotify(applicationContext, key, ts)
                            ) {
                                val sender = if (chat.isGroup) {
                                    "${chat.memberNames[chat.lastMessageSenderId] ?: "Member"} • ${chat.groupName}"
                                } else {
                                    chat.memberNames[chat.lastMessageSenderId] ?: "Linkora"
                                }
                                val soundOn = prefs.soundEnabled.first()
                                val vibrateOn = prefs.vibrateEnabled.first()
                                NotificationHelper.showMessageNotification(
                                    context = applicationContext,
                                    notificationId = chat.id.hashCode(),
                                    title = sender,
                                    body = chat.lastMessageText.ifBlank { "New message" },
                                    chatId = chat.id,
                                    soundEnabled = soundOn,
                                    vibrateEnabled = vibrateOn,
                                    wakeScreen = true
                                )
                            }
                        }
                        firstChatsBatch = false
                    }
            }

            // 3. Listen for incoming ringing calls while app is closed or backgrounded
            launch {
                var firstCallsBatch = true
                repository.observeCalls(uid)
                    .catch { Log.w(BG_TAG, "Calls listener error: ${it.message}") }
                    .collect { calls ->
                        calls.filter {
                            it.status == "ringing" &&
                                it.calleeId == uid &&
                                it.callerId !in blockedUids
                        }.forEach { call ->
                            val ts = call.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                            val key = "call_${call.id}"
                            if (firstCallsBatch) {
                                AppNotificationState.primeTimestampIfUnset(applicationContext, key, ts)
                            } else if (notificationsAllowed &&
                                !AppNotificationState.isAppInForeground &&
                                AppNotificationState.shouldNotify(applicationContext, key, ts)
                            ) {
                                NotificationHelper.showIncomingCallNotification(
                                    context = applicationContext,
                                    notificationId = call.id.hashCode(),
                                    callerName = call.callerName,
                                    isVideo = call.callType == "video"
                                )
                            }
                        }
                        firstCallsBatch = false
                    }
            }

            // 4. Listen for incoming friend requests while app is closed or backgrounded
            launch {
                var firstReqBatch = true
                repository.observeFriendRequests(uid)
                    .catch { Log.w(BG_TAG, "Friend requests listener error: ${it.message}") }
                    .collect { reqs ->
                        reqs.filter {
                            it.toUid == uid &&
                                it.status == "pending" &&
                                it.fromUid !in blockedUids
                        }.forEach { req ->
                            val ts = req.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                            val key = "req_${req.id}"
                            if (firstReqBatch) {
                                AppNotificationState.primeTimestampIfUnset(applicationContext, key, ts)
                            } else if (notificationsAllowed &&
                                !AppNotificationState.isAppInForeground &&
                                AppNotificationState.shouldNotify(applicationContext, key, ts)
                            ) {
                                val soundOn = prefs.soundEnabled.first()
                                val vibrateOn = prefs.vibrateEnabled.first()
                                NotificationHelper.showMessageNotification(
                                    context = applicationContext,
                                    notificationId = req.id.hashCode(),
                                    title = "Friend Request",
                                    body = "${req.fromName} (@${req.fromUsername}) wants to connect",
                                    chatId = null,
                                    soundEnabled = soundOn,
                                    vibrateEnabled = vibrateOn,
                                    wakeScreen = true
                                )
                            }
                        }
                        firstReqBatch = false
                    }
            }
        }
    }

    override fun onDestroy() {
        listenersJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            if (!NotificationHelper.canPostNotifications(context)) return
            val user = try {
                Firebase.auth.currentUser
            } catch (_: Exception) {
                null
            } ?: return
            if (user.uid.isBlank()) return

            val intent = Intent(context.applicationContext, LinkoraMessageService::class.java)
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: Exception) {
                Log.w(BG_TAG, "Deferred FGS start (background restriction): ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, LinkoraMessageService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Automatically reconnects LinkoraMessageService after device reboot or app update
 * so real-time messages arrive on the mobile screen even before opening Linkora.
 */
class LinkoraBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LinkoraMessageService.start(context)
        }
    }
}
