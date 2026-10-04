package com.bambookit.android.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
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
import java.util.concurrent.TimeUnit

/**
 * An API error. [code] is the API's machine-readable error code (e.g. DESKTOP_OFFLINE,
 * DESKTOP_TIMEOUT), "NETWORK" when the API could not be reached, or HTTP_<status>.
 * The other fields feed the ⓘ diagnostics; none of them ever holds a token or a signed URL.
 */
class ApiException(
    message: String,
    val status: Int,
    val code: String,
    val method: String? = null,
    val path: String? = null,
    val requestId: String? = null,
    val apiVersion: String? = null,
    val details: JsonElement? = null,
) : Exception(message) {
    /** The PC that holds the data is offline or did not answer the relay in time. */
    val isDesktopUnavailable get() = code == "DESKTOP_OFFLINE" || code == "DESKTOP_TIMEOUT"
    /** The server is older than this app and doesn't have the route. */
    val isRouteMissing get() = Diagnostics.isRouteMissing(code, status, message)
    val desktopRequirement: DesktopRequirement?
        get() = if (code == "DESKTOP_UPDATE_REQUIRED") DesktopRequirement.fromDetails(details as? JsonObject) else null

    val diagnosis: Diagnosis
        get() = Diagnosis(
            message = message ?: "Request failed",
            code = code,
            status = status,
            method = method,
            path = path,
            requestId = requestId,
            apiVersion = apiVersion,
            protocol = ((details as? JsonObject)?.get("protocol") as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            desktop = desktopRequirement,
            details = Diagnostics.flatDetails(details),
        )
}

/**
 * Relay calls (tree, file, diagram, file map, history, file versions) wait for the PC: the API allows up to
 * 60 s for whole-project walks (diagram, tree), plus time for the hosted API to wake. The read timeout must
 * stay above that or OkHttp gives up before the API answers with the PC's result or DESKTOP_TIMEOUT.
 */
const val RELAY_READ_TIMEOUT_S = 75L
const val RELAY_CALL_TIMEOUT_S = 120L

/** Typed client for bambookit-api. Every call is authenticated with the user's session. */
class ApiClient(
    private val http: OkHttpClient,
    private val auth: AuthRepository,
    private val store: SecureStore,
    private val json: Json,
) {
    private val relayHttp: OkHttpClient = http.newBuilder()
        .readTimeout(RELAY_READ_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(RELAY_CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    private suspend fun <T> call(method: String, path: String, body: JsonElement?, serializer: KSerializer<T>, client: OkHttpClient = http): T = withContext(Dispatchers.IO) {
        val text = execute(client, method, path, body, refreshed = false) ?: execute(client, method, path, body, refreshed = true)!!
        json.decodeFromString(serializer, text)
    }

    /** Returns the response body, or null if the token was rejected and a refresh should be tried. */
    private suspend fun execute(client: OkHttpClient, method: String, path: String, body: JsonElement?, refreshed: Boolean): String? {
        val attempt = if (refreshed) 1 else 0
        val safePath = Diagnostics.safePath(path)
        fun fail(e: ApiException): Nothing {
            Diagnostics.record(e.diagnosis)
            throw e
        }
        val token = auth.accessToken(forceRefresh = attempt > 0)
        val builder = Request.Builder().url("${Config.apiUrl}$path").header("Authorization", "Bearer $token")
        store.deviceId?.let { builder.header("X-BK-Device-Id", it) }
        val requestBody = body?.toString()?.toRequestBody("application/json".toMediaType())
        builder.method(method, requestBody ?: if (method == "POST" || method == "PATCH") "{}".toRequestBody("application/json".toMediaType()) else null)
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: java.io.InterruptedIOException) {
            // OkHttp read/call timeout: the API (or the PC behind it) took too long.
            fail(ApiException("BambooKit took too long to answer. Try again.", 0, "TIMEOUT", method, safePath))
        } catch (e: Exception) {
            fail(ApiException(unreachableMessage(), 0, "NETWORK", method, safePath))
        }
        return response.use {
            val text = it.body?.string().orEmpty()
            if (it.code == 401 && attempt == 0) return@use null
            if (!it.isSuccessful) fail(errorFrom(json, method, safePath, it.code, text, it.header("X-Request-Id"), it.header(API_VERSION_HEADER)))
            text
        }
    }

    private suspend inline fun <reified T> get(path: String): T = call("GET", path, null, Envelope.serializer(kotlinx.serialization.serializer<T>())).data
    /** GET through the long-timeout client: the API waits for the PC to answer. */
    private suspend inline fun <reified T> relayGet(path: String): T =
        call("GET", path, null, Envelope.serializer(kotlinx.serialization.serializer<T>()), relayHttp).data
    private suspend inline fun <reified T> post(path: String, body: JsonElement = JsonObject(emptyMap())): T =
        call("POST", path, body, Envelope.serializer(kotlinx.serialization.serializer<T>())).data

    suspend fun me(): Account = get("/v1/me")
    /** Public service information (API 1.1.0+). */
    suspend fun meta(): ApiMeta = get("/v1/meta")
    /** Profile statistics, projects managed and achievements (API 1.1.0+). */
    suspend fun stats(): ProfileStats = get("/v1/me/stats")
    suspend fun achievements(): List<Achievement> = get("/v1/me/achievements")
    suspend fun setProjectStatus(id: String, status: String): ProjectStatusResult =
        call("PATCH", "/v1/projects/$id", buildJsonObject { put("status", status) }, Envelope.serializer(ProjectStatusResult.serializer())).data
    /** The phone's IANA time zone, for "this week" and night-time statistics. */
    suspend fun setTimeZone(zone: String) {
        call("PATCH", "/v1/me", buildJsonObject { put("timeZone", zone) }, JsonObject.serializer())
    }
    suspend fun overview(): Overview = get("/v1/overview")
    suspend fun devices(): List<Device> = get("/v1/devices")
    suspend fun projects(): List<Project> = get("/v1/projects")
    suspend fun sessions(projectId: String? = null): List<Session> = get("/v1/sessions" + (projectId?.let { "?projectId=$it" } ?: ""))
    suspend fun session(id: String): Session = get("/v1/sessions/$id")
    /** Like / unlike a session; returns the updated session. */
    suspend fun setStarred(id: String, starred: Boolean): Session =
        call("PATCH", "/v1/sessions/$id", buildJsonObject { put("starred", starred) }, Envelope.serializer(Session.serializer())).data
    suspend fun parts(sessionId: String): List<Part> = relayGet("/v1/sessions/$sessionId/parts")
    suspend fun changes(sessionId: String): List<ChangedFile> = relayGet("/v1/sessions/$sessionId/changes")
    suspend fun fileMap(sessionId: String): List<FileMapEntry> = relayGet("/v1/sessions/$sessionId/filemap")
    /** Null when the PC answered without a diagram. Can take up to a minute for large projects. */
    suspend fun diagram(sessionId: String): ProjectDiagram? = relayGet("/v1/sessions/$sessionId/diagram")
    /** One folder of the session's project; "" is the project root. */
    suspend fun tree(sessionId: String, path: String): TreeListing = relayGet("/v1/sessions/$sessionId/tree?path=${query(relayPath(path))}")
    /** A project file's text (view only). */
    suspend fun file(sessionId: String, path: String): FileContent = relayGet("/v1/sessions/$sessionId/file?path=${query(relayPath(path))}")

    /** Prompts, timeline, changed files with patches, tests and summary: live from the PC or its 7-day cloud copy. */
    suspend fun history(sessionId: String): HistoryResponse = relayGet("/v1/sessions/$sessionId/history")
    /** One file before and after the session (live from the PC only). The path must match the history's change entry exactly. */
    suspend fun fileVersions(sessionId: String, path: String): FileVersions = relayGet("/v1/sessions/$sessionId/file-versions?path=${query(path)}")

    private fun query(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
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

    /** The agent's current todo list, live from the PC. */
    suspend fun todos(sessionId: String): TodoList = relayGet("/v1/sessions/$sessionId/todos")

    /** The PC's AI providers and models (never any keys). Relayed live; can take several seconds. */
    suspend fun providers(deviceId: String): ProvidersInfo = relayGet("/v1/devices/$deviceId/providers")

    /** Everything the agent attached to a request (full command, proposed diff, question context), live from the PC. */
    suspend fun approvalDetail(id: String): ApprovalDetail = relayGet("/v1/approvals/$id/detail")

    /** Starts a new session on the PC that owns the project (202: the CREATE_SESSION command). */
    suspend fun createSession(projectId: String, text: String, model: ModelRef?, agent: String? = null): CommandAccepted =
        call("POST", "/v1/projects/$projectId/sessions", messagePayload(text, model, agent), CommandAccepted.serializer())

    /** A command for the PC itself (SET_PROVIDER_KEY, REMOVE_PROVIDER_KEY). */
    suspend fun deviceCommand(deviceId: String, type: String, payload: JsonObject): CommandAccepted =
        call("POST", "/v1/devices/$deviceId/commands", buildJsonObject { put("type", type); put("payload", payload) }, CommandAccepted.serializer())

    suspend fun respondApproval(id: String, reply: String) {
        call("POST", "/v1/approvals/$id/respond", buildJsonObject { put("reply", reply) }, JsonObject.serializer())
    }

    /** Answers a question request: one list of chosen labels (or typed text) per question. */
    suspend fun answerApproval(id: String, answers: List<List<String>>) {
        call("POST", "/v1/approvals/$id/answer", answerPayload(answers), JsonObject.serializer())
    }

    /** Sets the BambooKit nickname (1-40 characters) and returns the updated profile. */
    suspend fun updateNickname(name: String): Account =
        call("PATCH", "/v1/me", buildJsonObject { put("name", name) }, Envelope.serializer(Account.serializer())).data

    suspend fun markRead(notificationId: String) {
        post<JsonObject>("/v1/notifications/$notificationId/read")
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

    // ---------------------------------------------------------------- profile

    suspend fun avatarUpload(contentType: String, size: Int): AvatarUpload =
        post("/v1/me/avatar-upload", buildJsonObject { put("contentType", contentType); put("size", size) })

    suspend fun setAvatar(key: String): AvatarSet = post("/v1/me/avatar", buildJsonObject { put("key", key) })

    suspend fun removeAvatar() {
        call("DELETE", "/v1/me/avatar", null, JsonObject.serializer())
    }

    /** Permanently deletes the account on the server. Files and sessions on the user's PCs are not touched. */
    suspend fun deleteAccount() {
        call("DELETE", "/v1/me", buildJsonObject { put("confirm", "DELETE MY ACCOUNT") }, JsonObject.serializer())
    }

    /**
     * Uploads bytes to a signed storage URL. No BambooKit auth header is sent; Content-Type and
     * Content-Length must match what the URL was signed for (see [signedPutRequest]).
     * Only the status is ever recorded: the URL carries a signature.
     */
    suspend fun putSigned(url: String, contentType: String, bytes: ByteArray, headers: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val request = signedPutRequest(url, contentType, bytes, headers)
        val response = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            val ex = ApiException("Could not upload the photo. Check your connection and try again.", 0, "NETWORK", "PUT", "storage upload")
            Diagnostics.record(ex.diagnosis)
            throw ex
        }
        response.use {
            if (!it.isSuccessful) {
                // S3-style XML error: only its code is kept (never the URL or the request details it echoes).
                val storageCode = runCatching { Regex("<Code>([A-Za-z]+)</Code>").find(it.body?.string().orEmpty())?.groupValues?.get(1) }.getOrNull()
                val ex = ApiException(
                    when {
                        storageCode == "AccessDenied" -> "Cloud storage refused the photo (403 AccessDenied). The BambooKit server's storage key isn't allowed to write to its bucket; the server owner has to fix the storage permissions."
                        storageCode == "SignatureDoesNotMatch" -> "Cloud storage rejected the photo (403 SignatureDoesNotMatch): the upload didn't match what the link was signed for."
                        it.code == 403 -> "Cloud storage rejected the photo (403). The upload link didn't match the photo's size or type, or it expired."
                        else -> "Photo upload failed (${it.code})"
                    },
                    it.code, "UPLOAD_FAILED", "PUT", "storage upload",
                    details = storageCode?.let { c -> kotlinx.serialization.json.buildJsonObject { put("storageCode", c) } },
                )
                Diagnostics.record(ex.diagnosis)
                throw ex
            }
        }
    }

    /** Downloads a public or pre-signed image URL (profile photos). No BambooKit auth header is sent. */
    suspend fun download(url: String, maxBytes: Long = 5L * 1024 * 1024): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use {
            if (!it.isSuccessful) throw ApiException("Download failed (${it.code})", it.code, "HTTP_${it.code}")
            val body = it.body ?: throw ApiException("Empty response", it.code, "EMPTY")
            if (body.contentLength() > maxBytes) throw ApiException("Image too large", it.code, "TOO_LARGE")
            body.bytes()
        }
    }

    companion object {
        const val API_VERSION_HEADER = "X-BambooKit-API"

        /**
         * The PUT for a signed storage URL. The URL signs host and content-length, so the body is the exact final
         * bytes with a fixed length (OkHttp then sends Content-Length, never chunked transfer encoding) and the
         * Content-Type is exactly the signed one, without a charset.
         */
        fun signedPutRequest(url: String, contentType: String, bytes: ByteArray, headers: Map<String, String> = emptyMap()): Request {
            val type = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value ?: contentType
            val media = type.toMediaType()
            val body = object : okhttp3.RequestBody() {
                override fun contentType() = media
                override fun contentLength() = bytes.size.toLong()
                override fun writeTo(sink: okio.BufferedSink) {
                    sink.write(bytes)
                }
            }
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) ->
                if (!k.equals("Content-Type", true) && !k.equals("Content-Length", true) && !k.equals("Transfer-Encoding", true)) builder.header(k, v)
            }
            return builder.header("Content-Type", type).put(body).build()
        }

        /** An [ApiException] from an error response: `{ error: { code, message, details? }, requestId }`. */
        fun errorFrom(json: Json, method: String, path: String, status: Int, text: String, requestIdHeader: String?, apiVersion: String?): ApiException {
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            val err = root?.get("error") as? JsonObject
            return ApiException(
                (err?.get("message") as? JsonPrimitive)?.contentOrNull ?: "Request failed ($status)",
                status,
                (err?.get("code") as? JsonPrimitive)?.contentOrNull ?: "HTTP_$status",
                method, path,
                (root?.get("requestId") as? JsonPrimitive)?.contentOrNull ?: requestIdHeader,
                apiVersion,
                err?.get("details"),
            )
        }

        /** Body of SEND_MESSAGE and of a new session: {text, model?: {providerID, modelID}, agent?}. */
        fun messagePayload(text: String, model: ModelRef?, agent: String? = null): JsonObject = buildJsonObject {
            put("text", text)
            if (model != null) putJsonObject("model") { put("providerID", model.providerID); put("modelID", model.modelID) }
            if (!agent.isNullOrBlank()) put("agent", agent)
        }

        /** Body of POST /v1/approvals/:id/answer. */
        fun answerPayload(answers: List<List<String>>): JsonObject = buildJsonObject {
            put("answers", JsonArray(answers.map { list -> JsonArray(list.map { JsonPrimitive(it) }) }))
        }

        /**
         * Project paths go to the PC with "/" separators (Windows paths from the session history may use
         * backslashes; the PC accepts "/" on every OS). A leading "./" and trailing "/" are dropped.
         * Absolute paths ("/home/me/app/x.ts", "C:/proj/x.ts") stay absolute: the PC maps them into the project.
         */
        fun relayPath(path: String): String =
            path.trim().replace('\\', '/').removePrefix("./").let { if (it.length > 1) it.trimEnd('/') else it }

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

