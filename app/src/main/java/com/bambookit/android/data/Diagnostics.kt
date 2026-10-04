package com.bambookit.android.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * What a PC needs for a feature it lacks (426 DESKTOP_UPDATE_REQUIRED details, or the same worked out on the
 * phone from the PC's capabilities before any request is sent).
 */
data class DesktopRequirement(
    val device: String,
    val currentVersion: String?,
    val requiredVersion: String,
    val capability: String,
    val reason: String,
    val desktopProtocol: Int? = null,
    val apiProtocol: Int? = null,
) {
    companion object {
        fun fromDetails(d: JsonObject?): DesktopRequirement? {
            if (d == null) return null
            fun s(k: String) = (d[k] as? JsonPrimitive)?.contentOrNull
            fun i(k: String) = (d[k] as? JsonPrimitive)?.intOrNull
            return DesktopRequirement(
                device = s("device") ?: "your PC",
                currentVersion = s("currentVersion"),
                requiredVersion = s("requiredVersion") ?: return null,
                capability = s("capability").orEmpty(),
                reason = s("reason").orEmpty(),
                desktopProtocol = i("desktopProtocol"),
                apiProtocol = i("apiProtocol"),
            )
        }
    }
}

/**
 * One failure, with everything the ⓘ sheet shows. Never holds tokens, signed URLs or query strings:
 * [path] is the API path without its query, and storage uploads are recorded as "storage upload".
 */
data class Diagnosis(
    val message: String,
    val code: String = "UNKNOWN",
    val status: Int = 0,
    val method: String? = null,
    val path: String? = null,
    val requestId: String? = null,
    val apiVersion: String? = null,
    val protocol: Int? = null,
    val desktop: DesktopRequirement? = null,
    /** BambooKit Desktop version of the PC involved, when known. */
    val desktopVersion: String? = null,
    /** Extra non-secret details the server reported (e.g. a missing server setting). */
    val details: Map<String, String> = emptyMap(),
    val at: Long = System.currentTimeMillis(),
)

/** Actions the ⓘ sheet can offer. */
enum class DiagAction(val label: String) {
    Retry("Retry"),
    Refresh("Refresh"),
    UpdateDesktop("Update Desktop info"),
    Reconnect("Reconnect"),
    OpenSettings("Open Settings"),
}

data class Explanation(val why: String, val actions: List<DiagAction>)

/** Process-wide diagnostics: the API version seen in responses and a small ring of recent failures. */
object Diagnostics {
    /** X-BambooKit-API of the last API response (null on servers older than 1.1.0, which don't send it). */
    @Volatile var apiVersion: String? = null
    /** Protocol from GET /v1/meta. */
    @Volatile var apiProtocol: Int? = null
    /** True once the server answered that a route this app uses does not exist (the server is older). */
    @Volatile var serverOutdated: Boolean = false

    private const val SIZE = 20
    private val ring = ArrayDeque<Diagnosis>(SIZE)

    fun record(d: Diagnosis) = synchronized(ring) {
        if (ring.size == SIZE) ring.removeFirst()
        ring.addLast(d)
        if (isRouteMissing(d.code, d.status, d.message)) serverOutdated = true
    }

    /** Newest first. */
    fun recent(): List<Diagnosis> = synchronized(ring) { ring.reversed() }

    fun clear() = synchronized(ring) { ring.clear() }

    /** API path without the query string (values could be file paths or ids the user typed). */
    fun safePath(path: String): String = path.substringBefore('?').let { if (path.contains('?')) "$it?…" else it }

    /** "Route not found" in both the new (ROUTE_NOT_FOUND) and the pre-1.1.0 (NOT_FOUND "Route not found") formats. */
    fun isRouteMissing(code: String?, status: Int, message: String?): Boolean =
        code == "ROUTE_NOT_FOUND" || (status == 404 && code == "NOT_FOUND" && message?.contains("route not found", ignoreCase = true) == true)

    /** Why it may have happened, and what the user can do: mapped from the code and status. */
    fun explain(d: Diagnosis): Explanation {
        val api = d.apiVersion ?: apiVersion
        val apiText = api?.let { "API $it" } ?: "an API version older than 1.1.0"
        val req = d.desktop
        return when {
            isRouteMissing(d.code, d.status, d.message) -> Explanation(
                "The BambooKit server is older than this app ($apiText) and doesn't have ${d.method ?: "this"} ${d.path ?: "request"} yet. " +
                    "The server has to be updated (redeployed); nothing is wrong with your phone or your PC.",
                listOf(DiagAction.Retry, DiagAction.Refresh),
            )
            d.code == "DESKTOP_UPDATE_REQUIRED" || req != null -> Explanation(
                buildString {
                    append("BambooKit Desktop on ${req?.device ?: "your PC"} is ")
                    append(req?.currentVersion?.let { "version $it" } ?: "an older version")
                    append("; this needs ${req?.requiredVersion?.let { "version $it or newer" } ?: "a newer version"}.")
                    req?.reason?.takeIf { it.isNotBlank() }?.let { append(" $it") }
                },
                listOf(DiagAction.UpdateDesktop, DiagAction.Retry),
            )
            d.code == "DESKTOP_OUTDATED" -> Explanation(
                "BambooKit Desktop on your PC answered that it doesn't know this request: it is older than this phone app.",
                listOf(DiagAction.UpdateDesktop, DiagAction.Retry),
            )
            d.code == "DESKTOP_OFFLINE" -> Explanation(
                "Your PC is offline or BambooKit Desktop isn't running there, so nothing can be read from it right now.",
                listOf(DiagAction.Retry, DiagAction.Reconnect),
            )
            d.code == "DESKTOP_TIMEOUT" -> Explanation(
                "Your PC is online but didn't answer in time. Large projects can take up to a minute, or the PC may be busy or asleep.",
                listOf(DiagAction.Retry),
            )
            d.code == "NETWORK" -> Explanation(
                "The phone couldn't reach the BambooKit server: no internet connection, or the hosted server is waking up (this can take up to a minute).",
                listOf(DiagAction.Retry, DiagAction.OpenSettings),
            )
            d.code == "TIMEOUT" -> Explanation(
                "The server took too long to answer. The connection may be slow or the server is busy.",
                listOf(DiagAction.Retry),
            )
            d.status == 401 || d.code == "UNAUTHORIZED" -> Explanation(
                "Your sign-in was not accepted (it may have expired). Reconnecting refreshes it; if that fails, sign out and sign in again.",
                listOf(DiagAction.Reconnect, DiagAction.Refresh),
            )
            d.code == "UPLOAD_FAILED" && d.details["storageCode"] == "AccessDenied" -> Explanation(
                "The upload link was valid, but cloud storage refused to store the file: the server's storage (R2) API token doesn't have write access to the bucket. " +
                    "Only the server owner can fix this, by giving that token Object Read & Write permission for the bucket.",
                listOf(DiagAction.Retry),
            )
            d.status == 403 && d.code == "UPLOAD_FAILED" -> Explanation(
                "Cloud storage rejected the upload. The upload link is signed for an exact size and type; a mismatch or an expired link gives 403.",
                listOf(DiagAction.Retry),
            )
            d.status == 403 || d.code == "FORBIDDEN" -> Explanation("This account isn't allowed to do this.", listOf(DiagAction.Refresh))
            d.code == "STORAGE_NOT_CONFIGURED" -> Explanation(
                "Cloud storage isn't set up on the BambooKit server, so photos can't be stored. The server owner has to add the storage settings." +
                    (d.details["missing"]?.let { " Missing: $it." } ?: ""),
                listOf(DiagAction.Refresh),
            )
            d.code == "UPDATE_SOURCE_UNAVAILABLE" || d.code == "SERVER_UNAVAILABLE" -> Explanation(
                "The release server couldn't be reached. Try again later.",
                listOf(DiagAction.Retry, DiagAction.OpenSettings),
            )
            d.code == "NO_RELEASE" -> Explanation("No BambooKit for Android release has been published yet.", listOf(DiagAction.Retry))
            d.code == "INVALID_RESPONSE" || d.code == "INVALID_RELEASE" -> Explanation(
                "The server answered with something this app couldn't read. The server and the app may be different versions.",
                listOf(DiagAction.Retry),
            )
            d.code == "DOWNLOAD_FAILED" -> Explanation("The download stopped or was incomplete.", listOf(DiagAction.Retry, DiagAction.OpenSettings))
            d.status == 400 || d.code == "VALIDATION_ERROR" -> Explanation(
                "The server didn't accept the request. If the server is older than this app it may expect a different request.",
                listOf(DiagAction.Retry),
            )
            d.status == 404 -> Explanation("What you asked for no longer exists (it may have been removed on another device).", listOf(DiagAction.Refresh))
            d.status == 429 -> Explanation("Too many requests in a short time. Wait a moment and try again.", listOf(DiagAction.Retry))
            d.status >= 500 -> Explanation("The BambooKit server had a problem. This is usually temporary.", listOf(DiagAction.Retry, DiagAction.Refresh))
            else -> Explanation("BambooKit couldn't finish this. The technical details below show the exact reason.", listOf(DiagAction.Retry, DiagAction.Refresh))
        }
    }

    /** Copyable technical details. Contains no tokens, signed URLs or keys. */
    fun technical(d: Diagnosis, appVersion: String): String = buildList {
        if (d.method != null || d.path != null) add("Request: ${d.method.orEmpty()} ${d.path.orEmpty()}".trim())
        add("HTTP status: ${if (d.status > 0) d.status.toString() else "none (no response)"}")
        add("Code: ${d.code}")
        add("Message: ${d.message}")
        d.requestId?.let { add("Request ID: $it") }
        add("Android app: $appVersion")
        add("API version: ${d.apiVersion ?: apiVersion ?: "unknown (older than 1.1.0)"}")
        (d.protocol ?: apiProtocol)?.let { add("Protocol: $it") }
        d.desktop?.let { r ->
            add("PC: ${r.device}")
            add("Desktop version: ${r.currentVersion ?: "unknown"} (needs ${r.requiredVersion})")
            if (r.capability.isNotBlank()) add("Capability: ${r.capability}")
            r.desktopProtocol?.let { add("Desktop protocol: $it") }
        } ?: d.desktopVersion?.let { add("Desktop version: $it") }
        d.details.forEach { (k, v) -> add("$k: $v") }
        add("Time: ${java.time.Instant.ofEpochMilli(d.at)}")
    }.joinToString("\n")

    /** Non-secret string details from an error's `details` object (nested values are skipped). */
    fun flatDetails(details: JsonElement?): Map<String, String> {
        val o = details as? JsonObject ?: return emptyMap()
        val skip = setOf("device", "currentVersion", "requiredVersion", "capability", "reason", "desktopProtocol", "apiProtocol", "method", "path", "apiVersion", "protocol")
        return o.filterKeys { it !in skip && !it.contains("token", true) && !it.contains("secret", true) && !it.contains("key", true) }
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it.take(200) } }
            .toMap()
    }
}

/** The ⓘ details of any failure. */
fun Throwable.diagnosis(): Diagnosis = (this as? ApiException)?.diagnosis ?: Diagnosis(message ?: "Something went wrong", code = "UNKNOWN")
