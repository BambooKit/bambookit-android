package com.bambookit.android.data

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String? = null,
    val name: String? = null,
)

class AuthException(message: String, val code: String) : Exception(message)

/**
 * Supabase Auth (GoTrue REST). Same project as BambooKit Desktop and Web, so one account works
 * everywhere. The session is stored encrypted; tokens are refreshed shortly before expiry.
 */
class AuthRepository(private val http: OkHttpClient, private val store: SecureStore, private val json: Json) {
    private val _session = MutableStateFlow(store.sessionJson?.let { runCatching { json.decodeFromString<AuthSession>(it) }.getOrNull() })
    val session: StateFlow<AuthSession?> = _session
    private val refreshLock = Mutex()

    suspend fun signIn(email: String, password: String): AuthSession =
        accept(post("/token?grant_type=password", buildJsonObject { put("email", email.trim()); put("password", password) }))

    /** Returns null when Supabase requires email confirmation before the first sign-in. */
    suspend fun signUp(email: String, password: String): AuthSession? {
        val body = post("/signup", buildJsonObject { put("email", email.trim()); put("password", password) })
        return if (body["access_token"] != null) accept(body) else null
    }

    suspend fun resetPassword(email: String) {
        post("/recover", buildJsonObject { put("email", email.trim()) })
    }

    /** Google via Supabase OAuth with PKCE; the browser returns to bambookit://auth-callback. */
    fun googleAuthorizeUrl(): Uri {
        val verifier = randomUrlSafe(32)
        store.pkceVerifier = verifier
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return Uri.parse("${Config.supabaseUrl}/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", "bambookit://auth-callback")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "s256")
            .build()
    }

    suspend fun completeOAuth(callback: Uri): AuthSession {
        callback.getQueryParameter("error_description")?.let { throw AuthException(it, "OAUTH_FAILED") }
        val code = callback.getQueryParameter("code") ?: throw AuthException("Sign-in was cancelled", "OAUTH_FAILED")
        val verifier = store.pkceVerifier ?: throw AuthException("Sign-in session expired, try again", "OAUTH_FAILED")
        store.pkceVerifier = null
        return accept(post("/token?grant_type=pkce", buildJsonObject { put("auth_code", code); put("code_verifier", verifier) }))
    }

    suspend fun signOut() {
        val token = _session.value?.accessToken
        clear()
        if (token != null) runCatching {
            withContext(Dispatchers.IO) {
                http.newCall(
                    Request.Builder().url("${Config.supabaseUrl}/auth/v1/logout")
                        .header("apikey", Config.supabaseAnonKey).header("Authorization", "Bearer $token")
                        .post("".toRequestBody()).build(),
                ).execute().close()
            }
        }
    }

    fun clear() {
        store.sessionJson = null
        _session.value = null
    }

    /** A valid access token, refreshing first if it expires within a minute. */
    suspend fun accessToken(forceRefresh: Boolean = false): String {
        val current = _session.value ?: throw AuthException("Not signed in", "NOT_SIGNED_IN")
        if (!forceRefresh && current.expiresAt - System.currentTimeMillis() > 60_000) return current.accessToken
        return refreshLock.withLock {
            val latest = _session.value ?: throw AuthException("Not signed in", "NOT_SIGNED_IN")
            if (!forceRefresh && latest.expiresAt - System.currentTimeMillis() > 60_000) return@withLock latest.accessToken
            try {
                accept(post("/token?grant_type=refresh_token", buildJsonObject { put("refresh_token", latest.refreshToken) })).accessToken
            } catch (e: AuthException) {
                if (e.code != "NETWORK") clear()
                throw e
            }
        }
    }

    private fun accept(body: JsonObject): AuthSession {
        val user = body["user"]?.jsonObject
        val meta = user?.get("user_metadata")?.jsonObject
        val expiresAt = body["expires_at"]?.jsonPrimitive?.longOrNull?.times(1000)
            ?: (System.currentTimeMillis() + (body["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600) * 1000)
        val session = AuthSession(
            accessToken = body["access_token"]!!.jsonPrimitive.content,
            refreshToken = body["refresh_token"]!!.jsonPrimitive.content,
            expiresAt = expiresAt,
            userId = user?.get("id")?.jsonPrimitive?.content ?: "",
            email = user?.get("email")?.jsonPrimitive?.contentOrNull,
            name = meta?.get("full_name")?.jsonPrimitive?.contentOrNull ?: meta?.get("name")?.jsonPrimitive?.contentOrNull,
        )
        store.sessionJson = json.encodeToString(session)
        _session.value = session
        return session
    }

    private suspend fun post(path: String, payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        if (!Config.authConfigured) throw AuthException("Sign-in is not configured in this build", "AUTH_NOT_CONFIGURED")
        val request = Request.Builder()
            .url("${Config.supabaseUrl}/auth/v1$path")
            .header("apikey", Config.supabaseAnonKey)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            throw AuthException("Cannot reach the sign-in service: ${e.message}", "NETWORK")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { JsonObject(emptyMap()) }
            if (!it.isSuccessful) {
                val message = listOf("error_description", "msg", "message").firstNotNullOfOrNull { k -> obj[k]?.jsonPrimitive?.contentOrNull }
                    ?: "Sign-in failed (${it.code})"
                val code = obj["error_code"]?.jsonPrimitive?.contentOrNull ?: obj["error"]?.jsonPrimitive?.contentOrNull ?: "HTTP_${it.code}"
                throw AuthException(message, code)
            }
            obj
        }
    }

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
