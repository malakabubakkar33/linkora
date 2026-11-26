package com.example.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Process-wide coordination between foreground UI (LinkoraViewModel) and background
 * LinkoraMessageService so every new message plays sound & pops up ONCE with zero duplicates.
 */
object AppNotificationState {
    private const val PREFS_NAME = "linkora_notif_tracker"

    @Volatile
    var isAppInForeground: Boolean = false

    @Volatile
    var activeChatId: String? = null

    @Synchronized
    fun primeTimestampIfUnset(context: Context, key: String, timestampMs: Long) {
        if (timestampMs <= 0L) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(key)) {
            prefs.edit().putLong(key, timestampMs).apply()
        }
    }

    @Synchronized
    fun recordTimestamp(context: Context, key: String, timestampMs: Long) {
        if (timestampMs <= 0L) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prev = prefs.getLong(key, 0L)
        if (timestampMs > prev) {
            prefs.edit().putLong(key, timestampMs).apply()
        }
    }

    @Synchronized
    fun shouldNotify(context: Context, key: String, timestampMs: Long): Boolean {
        val effectiveTs = if (timestampMs > 0L) timestampMs else System.currentTimeMillis()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prev = prefs.getLong(key, 0L)
        if (effectiveTs <= prev) return false
        prefs.edit().putLong(key, effectiveTs).apply()
        return true
    }

    @Synchronized
    fun clearAll(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

object NotificationHelper {
    const val EXTRA_OPEN_CHAT_ID = "extra_open_chat_id"
    const val SERVICE_NOTIFICATION_ID = 40991

    // Fresh versioned channels so Android 8+ applies sound, vibration, heads-up & lockscreen settings cleanly
    private const val CHANNEL_MESSAGES_SOUND_VIB = "linkora_messages_headsup_v3"
    private const val CHANNEL_MESSAGES_SOUND_ONLY = "linkora_messages_sound_only_v3"
    private const val CHANNEL_MESSAGES_VIB_ONLY = "linkora_messages_vib_only_v3"
    private const val CHANNEL_MESSAGES_SILENT = "linkora_messages_silent_v3"
    private const val CHANNEL_CALLS = "linkora_calls_v3"
    private const val CHANNEL_BG_SYNC = "linkora_background_sync_v1"

    private val MESSAGE_VIBRATION_PATTERN = longArrayOf(0L, 200L, 110L, 200L)
    private val CALL_VIBRATION_PATTERN = longArrayOf(0L, 550L, 320L, 550L)

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val msgSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val msgAudioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val callRingtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: msgSoundUri
        val callAudioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val msgSoundVib = NotificationChannel(
            CHANNEL_MESSAGES_SOUND_VIB,
            "Messages (Sound & Vibrate)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up chat message popups with notification sound and vibration"
            setSound(msgSoundUri, msgAudioAttrs)
            enableVibration(true)
            vibrationPattern = MESSAGE_VIBRATION_PATTERN
            enableLights(true)
            lightColor = 0xFF3B6EF6.toInt()
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val msgSoundOnly = NotificationChannel(
            CHANNEL_MESSAGES_SOUND_ONLY,
            "Messages (Sound Only)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up chat message popups with notification sound"
            setSound(msgSoundUri, msgAudioAttrs)
            enableVibration(false)
            enableLights(true)
            lightColor = 0xFF3B6EF6.toInt()
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val msgVibOnly = NotificationChannel(
            CHANNEL_MESSAGES_VIB_ONLY,
            "Messages (Vibrate Only)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up chat message popups with vibration"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = MESSAGE_VIBRATION_PATTERN
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val msgSilent = NotificationChannel(
            CHANNEL_MESSAGES_SILENT,
            "Messages (Silent Heads-Up)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Silent heads-up chat message popups"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val callChannel = NotificationChannel(
            CHANNEL_CALLS,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming Linkora WebRTC audio and video calls"
            setSound(callRingtoneUri, callAudioAttrs)
            enableVibration(true)
            vibrationPattern = CALL_VIBRATION_PATTERN
            enableLights(true)
            lightColor = 0xFF10B981.toInt()
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val bgSyncChannel = NotificationChannel(
            CHANNEL_BG_SYNC,
            "Background Message Connection",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Keeps Linkora connected for real-time messages when the app is closed"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }

        manager.createNotificationChannels(
            listOf(
                msgSoundVib,
                msgSoundOnly,
                msgVibOnly,
                msgSilent,
                callChannel,
                bgSyncChannel
            )
        )
    }

    fun buildBackgroundServiceNotification(context: Context): Notification {
        createChannels(context)
        val launchIntent = buildLaunchIntent(context, chatId = null, requestCode = SERVICE_NOTIFICATION_ID)
        return NotificationCompat.Builder(context, CHANNEL_BG_SYNC)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Linkora Connected")
            .setContentText("Listening for real-time messages and calls")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(launchIntent)
            .build()
    }

    /**
     * Posts a high-priority Android Heads-Up (top-of-screen) & Lock-Screen notification
     * with notification sound, vibration, and optional brief screen wake-up when phone screen is off.
     */
    fun showMessageNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        chatId: String? = null,
        soundEnabled: Boolean = true,
        vibrateEnabled: Boolean = true,
        wakeScreen: Boolean = true
    ) {
        if (!canPostNotifications(context)) return
        createChannels(context)

        val channelId = when {
            soundEnabled && vibrateEnabled -> CHANNEL_MESSAGES_SOUND_VIB
            soundEnabled && !vibrateEnabled -> CHANNEL_MESSAGES_SOUND_ONLY
            !soundEnabled && vibrateEnabled -> CHANNEL_MESSAGES_VIB_ONLY
            else -> CHANNEL_MESSAGES_SILENT
        }

        val pendingIntent = buildLaunchIntent(context, chatId = chatId, requestCode = notificationId)
        val senderPerson = Person.Builder()
            .setName(title.ifBlank { "Linkora" })
            .build()
        val messagingStyle = NotificationCompat.MessagingStyle(senderPerson)
            .addMessage(body.ifBlank { "New message" }, System.currentTimeMillis(), senderPerson)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title.ifBlank { "Linkora" })
            .setContentText(body.ifBlank { "New message" })
            .setStyle(messagingStyle)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setColor(0xFF3B6EF6.toInt())
            .setContentIntent(pendingIntent)

        // Pre-Android 8 explicit sound & vibration so heads-up triggers on every API level
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (soundEnabled) {
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
            if (vibrateEnabled) {
                builder.setVibrate(MESSAGE_VIBRATION_PATTERN)
            }
        }

        if (wakeScreen) {
            wakeScreenBriefly(context)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }

    fun showIncomingCallNotification(
        context: Context,
        notificationId: Int,
        callerName: String,
        isVideo: Boolean
    ) {
        if (!canPostNotifications(context)) return
        createChannels(context)

        val pendingIntent = buildLaunchIntent(context, chatId = null, requestCode = notificationId)
        val callLabel = if (isVideo) "Incoming Video Call" else "Incoming Audio Call"
        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(callerName.ifBlank { "Linkora Call" })
            .setContentText(callLabel)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setColor(0xFF10B981.toInt())
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(ringtone)
            builder.setVibrate(CALL_VIBRATION_PATTERN)
        }

        wakeScreenBriefly(context)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked at runtime
        }
    }

    /**
     * Plays an immediate notification sound + gentle haptic pulse when a new message arrives
     * while the user is actively inside the app.
     */
    fun playInAppMessageAlert(
        context: Context,
        soundEnabled: Boolean,
        vibrateEnabled: Boolean
    ) {
        if (soundEnabled) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = if (uri != null) RingtoneManager.getRingtone(context.applicationContext, uri) else null
                if (ringtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    ringtone.play()
                } else {
                    playFallbackTone()
                }
            } catch (_: Exception) {
                playFallbackTone()
            }
        }

        if (vibrateEnabled) {
            triggerHapticPulse(context)
        }
    }

    private fun playFallbackTone() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    toneGen.release()
                } catch (_: Exception) {
                }
            }, 320L)
        } catch (_: Exception) {
        }
    }

    private fun triggerHapticPulse(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0L, 120L, 70L, 140L), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0L, 120L, 70L, 140L), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0L, 120L, 70L, 140L), -1)
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Briefly lights up the mobile screen (3.5 seconds) when a new message or call arrives
     * while the device screen is off / locked so the user immediately sees the alert on screen.
     */
    @SuppressLint("WakelockTimeout")
    private fun wakeScreenBriefly(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (pm.isInteractive) return
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Linkora:IncomingMessageWake"
            )
            wakeLock.acquire(3500L)
        } catch (_: Exception) {
            // Safe fallback if OEM blocks screen wakeup
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun buildLaunchIntent(
        context: Context,
        chatId: String?,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            if (!chatId.isNullOrBlank()) {
                putExtra(EXTRA_OPEN_CHAT_ID, chatId)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
