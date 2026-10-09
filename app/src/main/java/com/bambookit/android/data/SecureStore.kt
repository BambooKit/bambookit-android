package com.bambookit.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/** Encrypted (Android Keystore-backed) storage for the auth session and device identifiers. */
class SecureStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "bambookit_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var sessionJson: String?
        get() = prefs.getString("session", null)
        set(value) = prefs.edit().apply { if (value == null) remove("session") else putString("session", value) }.apply()

    var deviceId: String?
        get() = prefs.getString("device_id", null)
        set(value) = prefs.edit().apply { if (value == null) remove("device_id") else putString("device_id", value) }.apply()

    var lastSeq: Long?
        get() = if (prefs.contains("last_seq")) prefs.getLong("last_seq", 0) else null
        set(value) = prefs.edit().apply { if (value == null) remove("last_seq") else putLong("last_seq", value) }.apply()

    /** Stable per-install identifier used to derive this phone's device id on the server. */
    val installationId: String
        get() = prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation_id", it).apply()
        }

    /** App lock preference only (a boolean). No PIN, password or biometric data is ever stored by the app. */
    var appLockEnabled: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(value) = prefs.edit().putBoolean("app_lock", value).apply()

    /** "Notify me in the background": keep the realtime connection in a foreground service (default on). */
    var backgroundNotify: Boolean
        get() = prefs.getBoolean("background_notify", true)
        set(value) = prefs.edit().putBoolean("background_notify", value).apply()

    /** Whether the Android 13+ notification permission prompt was already shown once automatically. */
    var notificationPromptShown: Boolean
        get() = prefs.getBoolean("notification_prompt_shown", false)
        set(value) = prefs.edit().putBoolean("notification_prompt_shown", value).apply()

    /** The Home "Notifications are off" reminder is shown until the user acts on it once; later it's only in Profile. */
    var notificationBannerDone: Boolean
        get() = prefs.getBoolean("notification_banner_done", false)
        set(value) = prefs.edit().putBoolean("notification_banner_done", value).apply()

    /** The model picked for a session on this phone (provider + model id, never a key). */
    fun sessionModel(sessionId: String): ModelRef? =
        prefs.getString("model_$sessionId", null)?.split('|', limit = 2)?.takeIf { it.size == 2 && it.all(String::isNotBlank) }?.let { ModelRef(it[0], it[1]) }

    fun setSessionModel(sessionId: String, model: ModelRef?) =
        prefs.edit().apply { if (model == null) remove("model_$sessionId") else putString("model_$sessionId", "${model.providerID}|${model.modelID}") }.apply()

    /** The "Set up your profile" sheet is offered once per account on this phone. */
    fun profileSetupOffered(userId: String): Boolean = prefs.getBoolean("profile_setup_$userId", false)
    fun markProfileSetupOffered(userId: String) = prefs.edit().putBoolean("profile_setup_$userId", true).apply()

    /** Developer options (Power, Terminal, Research). Off by default; opt-in from Profile. */
    var developerOptions: Boolean
        get() = prefs.getBoolean("developer_options", false)
        set(value) = prefs.edit().putBoolean("developer_options", value).apply()

    /**
     * The user's own Google AI Studio (Gemini) API key for Research → Ask AI. Stored only here, in the
     * phone's encrypted prefs; it is never sent to BambooKit and never logged. Null when unset.
     */
    var geminiApiKey: String?
        get() = prefs.getString("gemini_api_key", null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().apply { if (value.isNullOrBlank()) remove("gemini_api_key") else putString("gemini_api_key", value.trim()) }.apply()

    /**
     * Sign-out: removes the auth session, this phone's device id, the realtime resume point and any
     * half-finished Google sign-in. Keeps the installation id and app settings (such as App lock).
     */
    fun clearSignedInState(keepPendingSignIn: Boolean = false) {
        val models = prefs.all.keys.filter { it.startsWith("model_") }
        prefs.edit().remove("session").remove("device_id").remove("last_seq")
            .apply { models.forEach { remove(it) } }
            .apply { if (!keepPendingSignIn) remove("pkce_verifier") }
            .apply()
    }

    var pkceVerifier: String?
        get() = prefs.getString("pkce_verifier", null)
        set(value) = prefs.edit().apply { if (value == null) remove("pkce_verifier") else putString("pkce_verifier", value) }.apply()
}
