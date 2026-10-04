package com.bambookit.android.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant

private val pretty = Json { prettyPrint = true }

/** A JSON value as readable text: strings as-is, everything else pretty-printed. Null/empty gives null. */
fun jsonText(e: JsonElement?): String? = when (e) {
    null, JsonNull -> null
    is JsonPrimitive -> e.contentOrNull?.takeIf { it.isNotEmpty() }
    is JsonObject -> if (e.isEmpty()) null else pretty.encodeToString(JsonElement.serializer(), e)
    is JsonArray -> if (e.isEmpty()) null else pretty.encodeToString(JsonElement.serializer(), e)
}

/** A string field of a JSON object, trying several names (desktops differ: filePath / filepath / path). */
fun JsonElement?.str(vararg names: String): String? {
    val o = this as? JsonObject ?: return null
    for (n in names) (o[n] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
    return null
}

fun JsonElement?.int(vararg names: String): Int? {
    val o = this as? JsonObject ?: return null
    for (n in names) (o[n] as? JsonPrimitive)?.intOrNull?.let { return it }
    return null
}

fun JsonElement?.bool(name: String): Boolean = ((this as? JsonObject)?.get(name) as? JsonPrimitive)?.contentOrNull == "true"

/** A time given as epoch milliseconds or an ISO-8601 string. */
private fun instantOf(e: JsonElement?): Instant? {
    val p = e as? JsonPrimitive ?: return null
    p.longOrNull?.let { return if (it > 100_000_000_000L) Instant.ofEpochMilli(it) else Instant.ofEpochSecond(it) }
    return p.contentOrNull?.let { runCatching { Instant.parse(it) }.getOrNull() }
}

/** Start and end of a tool call ({start, end} in ms or ISO). */
fun Part.startedAt(): Instant? = instantOf((time as? JsonObject)?.get("start"))
fun Part.endedAt(): Instant? = instantOf((time as? JsonObject)?.get("end"))

/** The command a shell tool ran, from its input or metadata. */
fun Part.command(): String? = input.str("command", "cmd") ?: metadata.str("command")

/** The file a tool worked on. */
fun Part.filePath(): String? = input.str("filePath", "filepath", "path", "file") ?: metadata.str("filePath", "filepath", "path")

/** The exit code of a shell tool. */
fun Part.exit(): Int? = exitCode ?: metadata.int("exit", "exitCode", "exit_code")

/** A unified diff produced by the tool, if any. */
fun Part.patch(): String? = diff?.takeIf { it.isNotBlank() } ?: metadata.str("diff", "patch")

/** The tool's error text: the error field, or the text of a failed tool part (older desktops). */
fun Part.errorText(): String? = jsonText(error) ?: text?.takeIf { toolStatus == "error" && it.isNotBlank() }

/** The tool's output text. */
fun Part.outputText(): String? = jsonText(output) ?: text?.takeIf { type == "tool" && toolStatus != "error" && it.isNotBlank() }

/**
 * A live part (small, from session.part) applied over what the transcript returned: text and status come
 * from the live part; the complete tool details are kept when the live part does not carry them.
 */
fun Part.mergeLive(live: Part): Part = live.copy(
    input = live.input ?: input,
    output = live.output ?: output,
    error = live.error ?: error,
    diff = live.diff ?: diff,
    exitCode = live.exitCode ?: exitCode,
    metadata = live.metadata ?: metadata,
    time = live.time ?: time,
    // The API cuts live texts; a complete transcript text that continues the live one is kept.
    text = mergedText(text, live.text),
)

internal fun mergedText(old: String?, live: String?): String? = when {
    live == null -> old
    old != null && old.length > live.length && old.startsWith(live) -> old
    else -> live
}

/** Approval detail helpers: the full command, the file, and the proposed diff. */
fun ApprovalDetail.command(): String? = metadata.str("command", "cmd")
fun ApprovalDetail.filePath(): String? = metadata.str("filepath", "filePath", "path", "file")
fun ApprovalDetail.diff(): String? = metadata.str("diff", "patch")
fun ApprovalDetail.diffTruncated(): Boolean = truncated || metadata.bool("truncated")

/** Metadata entries not shown elsewhere (as key/value text). */
fun ApprovalDetail.otherMetadata(): List<Pair<String, String>> {
    val o = metadata as? JsonObject ?: return emptyList()
    val shown = setOf("command", "cmd", "filepath", "filePath", "path", "file", "diff", "patch", "truncated")
    return o.filterKeys { it !in shown }.mapNotNull { (k, v) -> jsonText(v)?.let { k to it } }
}
