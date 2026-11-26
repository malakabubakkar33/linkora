package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.linkoraDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "linkora_preferences"
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DARK_MODE = booleanPreferencesKey("dark_mode_v2")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATE_ENABLED = booleanPreferencesKey("vibrate_enabled")
        val READ_RECEIPTS_ENABLED = booleanPreferencesKey("read_receipts_enabled")
        val ONLINE_PRESENCE_VISIBLE = booleanPreferencesKey("online_presence_visible")
        val ENTER_KEY_SENDS = booleanPreferencesKey("enter_key_sends")
        val LOW_DATA_CALLS = booleanPreferencesKey("low_data_calls")
        val CALL_QUALITY_MODE = stringPreferencesKey("call_quality_mode")
        val CHAT_WALLPAPER = stringPreferencesKey("chat_wallpaper")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val CHAT_LOCK_PIN_HASH = stringPreferencesKey("chat_lock_pin_hash")
        val CHAT_LOCK_HINT = stringPreferencesKey("chat_lock_hint")
        val GLOBAL_DISAPPEARING_MS = longPreferencesKey("global_disappearing_ms")
    }

    val onboardingDone: Flow<Boolean> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DONE] ?: false
    }

    val darkMode: Flow<Boolean> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    val soundEnabled: Flow<Boolean> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.SOUND_ENABLED] ?: true
    }

    val vibrateEnabled: Flow<Boolean> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.VIBRATE_ENABLED] ?: true
    }

    val readReceiptsEnabled: Flow<Boolean> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.READ_RECEIPTS_ENABLED] ?: true
    }

    val onlinePresenceVisible: Flow<Boolean> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.ONLINE_PRESENCE_VISIBLE] ?: true
    }

    val enterKeySends: Flow<Boolean> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.ENTER_KEY_SENDS] ?: false
    }

    val lowDataCalls: Flow<Boolean> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.LOW_DATA_CALLS] ?: false
    }

    val callQualityMode: Flow<String> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.CALL_QUALITY_MODE] ?: "balanced"
    }

    val chatWallpaper: Flow<String> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.CHAT_WALLPAPER] ?: "classic_glass"
    }

    val fontScale: Flow<String> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.FONT_SCALE] ?: "standard"
    }

    val chatLockPinHash: Flow<String> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.CHAT_LOCK_PIN_HASH].orEmpty()
    }

    val hasChatLockPin: Flow<Boolean> = chatLockPinHash.map { it.isNotBlank() }

    val chatLockHint: Flow<String> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.CHAT_LOCK_HINT].orEmpty()
    }

    val globalDisappearingMs: Flow<Long> = context.linkoraDataStore.data.map { prefs ->
        prefs[Keys.GLOBAL_DISAPPEARING_MS] ?: 0L
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.linkoraDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.linkoraDataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.linkoraDataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setVibrateEnabled(enabled: Boolean) {
        context.linkoraDataStore.edit { it[Keys.VIBRATE_ENABLED] = enabled }
    }

    suspend fun setReadReceiptsEnabled(enabled: Boolean) {
        context.linkoraDataStore.edit { it[Keys.READ_RECEIPTS_ENABLED] = enabled }
    }

    suspend fun setOnlinePresenceVisible(visible: Boolean) {
        context.linkoraDataStore.edit { it[Keys.ONLINE_PRESENCE_VISIBLE] = visible }
    }

    suspend fun setEnterKeySends(enabled: Boolean) {
        context.linkoraDataStore.edit { it[Keys.ENTER_KEY_SENDS] = enabled }
    }

    suspend fun setLowDataCalls(enabled: Boolean) {
        context.linkoraDataStore.edit { it[Keys.LOW_DATA_CALLS] = enabled }
    }

    suspend fun setCallQualityMode(modeKey: String) {
        context.linkoraDataStore.edit { it[Keys.CALL_QUALITY_MODE] = modeKey }
    }

    suspend fun setChatWallpaper(wallpaperKey: String) {
        context.linkoraDataStore.edit { it[Keys.CHAT_WALLPAPER] = wallpaperKey }
    }

    suspend fun setFontScale(scaleKey: String) {
        context.linkoraDataStore.edit { it[Keys.FONT_SCALE] = scaleKey }
    }

    suspend fun setChatLockPin(pin: String, hint: String = "") {
        val clean = pin.trim()
        if (clean.length != 4 || !clean.all { it.isDigit() }) return
        val hashed = hashPin(clean)
        context.linkoraDataStore.edit {
            it[Keys.CHAT_LOCK_PIN_HASH] = hashed
            it[Keys.CHAT_LOCK_HINT] = hint.trim().take(80)
        }
    }

    suspend fun verifyChatLockPin(pin: String): Boolean {
        val stored = chatLockPinHash.first()
        if (stored.isBlank()) return false
        return stored == hashPin(pin.trim())
    }

    suspend fun resetChatLockPin() {
        context.linkoraDataStore.edit {
            it.remove(Keys.CHAT_LOCK_PIN_HASH)
            it.remove(Keys.CHAT_LOCK_HINT)
        }
    }

    suspend fun setGlobalDisappearingMs(durationMs: Long) {
        context.linkoraDataStore.edit {
            it[Keys.GLOBAL_DISAPPEARING_MS] = durationMs.coerceAtLeast(0L)
        }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("linkora_chat_lock::$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
