package com.bambookit.android.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * An API error. [code] is the API's machine-readable error code (e.g. DESKTOP_OFFLINE,
 * SESSION_NOT_CONTINUED), "NETWORK" when the API could not be reached, or HTTP_<status>.
 */
class ApiException(message: String, val status: Int, val code: String) : Exception(message) {
    /** The PC that holds the data is offline or did not answer the relay in time. */
    val isDesktopUnavailable get() = code == "DESKTOP_OFFLINE" || code == "DESKTOP_TIMEOUT"
}

/** Typed client for bambookit-api. Every call is authenticated with the user's session. */
class ApiClient(
    private val http: OkHttpClient,
    private val auth: AuthRepository,
    private val store: SecureStore,
    private val json: Json,
) {
    private suspend fun <T> call(method: String, path: String, body: JsonElement?, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val text = execute(method, path, body, refreshed = false) ?: execute(method, path, body, refreshed = true)!!
        json.decodeFromString(serializer, text)
    }

    /** Returns the response body, or null if the token was rejected and a refresh should be tried. */
    private suspend fun execute(method: String, path: String, body: JsonElement?, refreshed: Boolean): String? {
        val attempt = if (refreshed) 1 else 0
        run {
            val token = auth.accessToken(forceRefresh = attempt > 0)
            val builder = Request.Builder().url("${Config.apiUrl}$path").header("Authorization", "Bearer $token")
            store.deviceId?.let { builder.header("X-BK-Device-Id", it) }
            val requestBody = body?.toString()?.toRequestBody("application/json".toMediaType())
            builder.method(method, requestBody ?: if (method == "POST" || method == "PATCH") "{}".toRequestBody("application/json".toMediaType()) else null)
            val response = try {
                http.newCall(builder.build()).execute()
            } catch (e: Exception) {
                throw ApiException(unreachableMessage(), 0, "NETWORK")
            }
            return response.use {
                val text = it.body?.string().orEmpty()
                if (it.code == 401 && attempt == 0) return@use null
                if (!it.isSuccessful) {
                    val err = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject }.getOrNull()
                    throw ApiException(
                        err?.get("message")?.jsonPrimitive?.contentOrNull ?: "Request failed (${it.code})",
                        it.code,
                        err?.get("code")?.jsonPrimitive?.contentOrNull ?: "HTTP_${it.code}",
                    )
                }
                text
            }
        }
    }

    private suspend inline fun <reified T> get(path: String): T = call("GET", path, null, Envelope.serializer(kotlinx.serialization.serializer<T>())).data
    private suspend inline fun <reified T> post(path: String, body: JsonElement = JsonObject(emptyMap())): T =
        call("POST", path, body, Envelope.serializer(kotlinx.serialization.serializer<T>())).data

    suspend fun me(): Account = get("/v1/me")
    suspend fun overview(): Overview = get("/v1/overview")
    suspend fun devices(): List<Device> = get("/v1/devices")
    suspend fun projects(): List<Project> = get("/v1/projects")
    suspend fun sessions(projectId: String? = null): List<Session> = get("/v1/sessions" + (projectId?.let { "?projectId=$it" } ?: ""))
    suspend fun session(id: String): Session = get("/v1/sessions/$id")
    suspend fun parts(sessionId: String): List<Part> = get("/v1/sessions/$sessionId/parts")
    suspend fun changes(sessionId: String): List<ChangedFile> = get("/v1/sessions/$sessionId/changes")
    suspend fun fileMap(sessionId: String): List<FileMapEntry> = get("/v1/sessions/$sessionId/filemap")
    /** Null when the PC answered without a diagram. */
    suspend fun diagram(sessionId: String): ProjectDiagram? = get("/v1/sessions/$sessionId/diagram")
    suspend fun approvals(pendingOnly: Boolean = true): List<Approval> = get("/v1/approvals" + if (pendingOnly) "?status=PENDING" else "")
    suspend fun notifications(): List<NotificationItem> = get("/v1/notifications")
    suspend fun command(id: String): Command = get("/v1/commands/$id")

    suspend fun registerPhone(): Device = post(
        "/v1/devices/register",
        buildJsonObject {
            put("kind", "mobile")
            put("name", deviceName())
            put("platform", "android")
            put("appVersion", com.bambookit.android.BuildConfig.VERSION_NAME)
            put("installationId", store.installationId)
        },
    )

    suspend fun claimPairing(token: String): PairingResult = post(
        "/v1/pairing/claim",
        buildJsonObject {
            put("token", token)
            putJsonObject("mobile") {
                put("installationId", store.installationId)
                put("name", deviceName())
                put("platform", "android")
                put("appVersion", com.bambookit.android.BuildConfig.VERSION_NAME)
            }
        },
    )

    suspend fun sendCommand(sessionId: String, type: String, payload: JsonObject = JsonObject(emptyMap())): CommandAccepted =
        call("POST", "/v1/sessions/$sessionId/commands", buildJsonObject { put("type", type); put("payload", payload) }, CommandAccepted.serializer())

    suspend fun respondApproval(id: String, reply: String) {
        call("POST", "/v1/approvals/$id/respond", buildJsonObject { put("reply", reply) }, JsonObject.serializer())
    }

    suspend fun unlink(desktopId: String, phoneId: String) {
        post<JsonObject>("/v1/devices/$desktopId/unlink", buildJsonObject { put("otherDeviceId", phoneId) })
    }

    suspend fun revoke(deviceId: String) {
        post<JsonObject>("/v1/devices/$deviceId/revoke")
    }

    suspend fun rename(deviceId: String, name: String) {
        call("PATCH", "/v1/devices/$deviceId", buildJsonObject { put("name", name) }, JsonObject.serializer())
    }

    suspend fun markAllRead() {
        post<JsonObject>("/v1/notifications/read-all")
    }

    companion object {
        /** Human-readable reason the API could not be reached. */
        fun unreachableMessage(): String =
            if (Config.apiUrl.contains("127.0.0.1") || Config.apiUrl.contains("localhost"))
                "Can't reach BambooKit on your PC. Keep the phone connected by USB and run start-bambookit.ps1 on the PC."
            else "Can't reach BambooKit (${Config.apiUrl}). It may be waking up — try again in a minute, and check your internet connection."

        fun deviceName(): String {
            val model = Build.MODEL ?: "Android"
            val maker = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
            return (if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model").trim().take(100)
        }
    }
}

