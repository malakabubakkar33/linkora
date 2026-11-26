package com.example.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class LocalRegisteredUser(
    val uid: String,
    val fullName: String,
    val username: String,
    val email: String,
    val passwordHash: String,
    val bio: String = "",
    val avatarData: String? = null,
    val profileCompleted: Boolean = false
)

/**
 * Local credential store enabling Email/Username + Password Login, Sign Up with unique
 * username checks, and Developer Demo sessions without calling unprovisioned Firebase Auth
 * providers. Cloud multi-user sync uses Google Sign-In via Credential Manager.
 */
class LocalAccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "linkora_local_accounts",
        Context.MODE_PRIVATE
    )

    private val reservedDemoUsernames = setOf("zara_ali", "hamza_dev", "ayesha_art", "linkora_dev")

    fun sanitizeUsername(raw: String): String {
        return raw.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
    }

    fun isUsernameAvailable(rawUsername: String): Boolean {
        val clean = sanitizeUsername(rawUsername)
        if (clean.length !in 3..30) return false
        if (clean in reservedDemoUsernames) return false
        return loadAllUsers().none { it.username.equals(clean, ignoreCase = true) }
    }

    fun register(
        fullName: String,
        rawUsername: String,
        email: String,
        password: String
    ): Result<LocalRegisteredUser> {
        val cleanName = fullName.trim().take(80)
        val cleanUser = sanitizeUsername(rawUsername)
        val cleanEmail = email.trim().lowercase()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Please enter your full name."))
        if (cleanUser.length !in 3..30) {
            return Result.failure(IllegalArgumentException("Username must be 3–30 characters (a-z, 0-9, _)."))
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        val users = loadAllUsers().toMutableList()
        if (cleanUser in reservedDemoUsernames || users.any { it.username.equals(cleanUser, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("Username @$cleanUser is already taken."))
        }
        if (users.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("An account with this email already exists."))
        }

        val newUser = LocalRegisteredUser(
            uid = "local_${UUID.randomUUID().toString().replace("-", "").take(16)}",
            fullName = cleanName,
            username = cleanUser,
            email = cleanEmail,
            passwordHash = sha256("$cleanUser::$password"),
            bio = "",
            avatarData = null,
            profileCompleted = false
        )
        users.add(newUser)
        saveAllUsers(users)
        setActiveSession(newUser.uid)
        return Result.success(newUser)
    }

    fun login(identifier: String, password: String): Result<LocalRegisteredUser> {
        val id = identifier.trim().lowercase().removePrefix("@")
        if (id.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your email/username and password."))
        }
        val users = loadAllUsers()
        val match = users.firstOrNull {
            it.email.equals(id, ignoreCase = true) || it.username.equals(id, ignoreCase = true)
        } ?: return Result.failure(IllegalArgumentException("No account found for \"$identifier\". Tap Sign Up to create one."))

        val expectedHash = sha256("${match.username}::$password")
        if (match.passwordHash != expectedHash) {
            return Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
        }
        setActiveSession(match.uid)
        return Result.success(match)
    }

    fun updateProfile(
        uid: String,
        fullName: String,
        bio: String,
        avatarData: String?,
        profileCompleted: Boolean = true
    ): LocalRegisteredUser? {
        val users = loadAllUsers().toMutableList()
        val idx = users.indexOfFirst { it.uid == uid }
        if (idx < 0) return null
        val updated = users[idx].copy(
            fullName = fullName.trim().take(80).ifBlank { users[idx].fullName },
            bio = bio.trim().take(250),
            avatarData = avatarData ?: users[idx].avatarData,
            profileCompleted = profileCompleted
        )
        users[idx] = updated
        saveAllUsers(users)
        return updated
    }

    fun getActiveLocalUser(): LocalRegisteredUser? {
        val activeUid = prefs.getString(KEY_ACTIVE_UID, null) ?: return null
        return loadAllUsers().firstOrNull { it.uid == activeUid }
    }

    fun getAllPublicProfiles(excludeUid: String? = null): List<com.example.data.model.PublicProfile> {
        val activeUid = prefs.getString(KEY_ACTIVE_UID, null)
        return loadAllUsers()
            .filter { it.uid != excludeUid }
            .map { u ->
                com.example.data.model.PublicProfile(
                    id = u.uid,
                    userId = u.uid,
                    fullName = u.fullName,
                    username = u.username,
                    bio = u.bio,
                    avatarData = u.avatarData,
                    online = u.uid == activeUid,
                    discoverable = true
                )
            }
    }

    fun isDeveloperDemoActive(): Boolean = prefs.getBoolean(KEY_DEMO_ACTIVE, false)

    fun enterDeveloperDemo() {
        prefs.edit()
            .remove(KEY_ACTIVE_UID)
            .putBoolean(KEY_DEMO_ACTIVE, true)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACTIVE_UID)
            .putBoolean(KEY_DEMO_ACTIVE, false)
            .apply()
    }

    private fun setActiveSession(uid: String) {
        prefs.edit()
            .putString(KEY_ACTIVE_UID, uid)
            .putBoolean(KEY_DEMO_ACTIVE, false)
            .apply()
    }

    private fun loadAllUsers(): List<LocalRegisteredUser> {
        val raw = prefs.getString(KEY_USERS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                LocalRegisteredUser(
                    uid = obj.getString("uid"),
                    fullName = obj.getString("fullName"),
                    username = obj.getString("username"),
                    email = obj.getString("email"),
                    passwordHash = obj.getString("passwordHash"),
                    bio = obj.optString("bio", ""),
                    avatarData = obj.optString("avatarData", "").ifBlank { null },
                    profileCompleted = obj.optBoolean("profileCompleted", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAllUsers(users: List<LocalRegisteredUser>) {
        val arr = JSONArray()
        users.forEach { u ->
            arr.put(
                JSONObject().apply {
                    put("uid", u.uid)
                    put("fullName", u.fullName)
                    put("username", u.username)
                    put("email", u.email)
                    put("passwordHash", u.passwordHash)
                    put("bio", u.bio)
                    put("avatarData", u.avatarData.orEmpty())
                    put("profileCompleted", u.profileCompleted)
                }
            )
        }
        prefs.edit().putString(KEY_USERS_JSON, arr.toString()).apply()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEY_USERS_JSON = "registered_users_json"
        const val KEY_ACTIVE_UID = "active_local_uid"
        const val KEY_DEMO_ACTIVE = "developer_demo_active"
    }
}
