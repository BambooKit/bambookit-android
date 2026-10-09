package com.bambookit.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** One Research search result. [source]: "Wikipedia" or "DuckDuckGo". */
data class ResearchResult(val title: String, val snippet: String, val url: String, val source: String, val wikiTitle: String? = null)

/**
 * Research is entirely local to the phone: free Wikipedia + DuckDuckGo search and reader, and an optional
 * "Ask AI" that calls Google's Generative Language API directly with the user's own Gemini key. None of this
 * goes through BambooKit, and the Gemini key is never logged. A bare HTTP client is used (no BambooKit auth).
 */
class ResearchRepository(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private fun enc(q: String) = URLEncoder.encode(q, "UTF-8")

    /** Wikipedia search + DuckDuckGo instant answer, merged. Failures of one source don't fail the other. */
    suspend fun search(query: String): List<ResearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val wiki = runCatching { wikipedia(q) }.getOrDefault(emptyList())
        val ddg = runCatching { duckDuckGo(q) }.getOrDefault(emptyList())
        (wiki + ddg)
    }

    private fun wikipedia(q: String): List<ResearchResult> {
        val body = get("https://en.wikipedia.org/w/rest.php/v1/search/page?q=${enc(q)}&limit=8") ?: return emptyList()
        val pages = (json.parseToJsonElement(body) as? JsonObject)?.get("pages") as? JsonArray ?: return emptyList()
        return pages.mapNotNull { it as? JsonObject }.mapNotNull { p ->
            val title = p.str("title") ?: return@mapNotNull null
            val key = p.str("key") ?: title.replace(' ', '_')
            ResearchResult(
                title = title,
                snippet = stripHtml(p.str("excerpt") ?: p.str("description") ?: ""),
                url = "https://en.wikipedia.org/wiki/${enc(key)}",
                source = "Wikipedia",
                wikiTitle = key,
            )
        }
    }

    private fun duckDuckGo(q: String): List<ResearchResult> {
        val body = get("https://api.duckduckgo.com/?q=${enc(q)}&format=json&no_html=1&t=bambookit") ?: return emptyList()
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
        val out = mutableListOf<ResearchResult>()
        val abstract = root.str("AbstractText").orEmpty()
        if (abstract.isNotBlank()) out += ResearchResult(
            root.str("Heading")?.ifBlank { q } ?: q, abstract, root.str("AbstractURL").orEmpty(), "DuckDuckGo",
        )
        (root["RelatedTopics"] as? JsonArray)?.forEach { t ->
            val o = t as? JsonObject ?: return@forEach
            val text = o.str("Text") ?: return@forEach
            val url = o.str("FirstURL") ?: return@forEach
            out += ResearchResult(text.take(80), text, url, "DuckDuckGo")
        }
        return out.take(8)
    }

    /** The full Wikipedia summary extract for a page key, for the reader view. */
    suspend fun wikipediaSummary(wikiTitle: String): String = withContext(Dispatchers.IO) {
        val body = get("https://en.wikipedia.org/api/rest_v1/page/summary/${enc(wikiTitle)}") ?: return@withContext ""
        (json.parseToJsonElement(body) as? JsonObject)?.str("extract").orEmpty()
    }

    /**
     * Ask Google's Gemini (gemini-2.5-flash) directly with the user's own key. Returns the model's text or throws
     * a readable error. The key only ever goes to Google; it is never logged.
     */
    suspend fun askAi(key: String, prompt: String): String = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", prompt) }) } })
            }
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${enc(key)}")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = http.newCall(request).execute()
        response.use {
            val text = it.body?.string().orEmpty()
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (!it.isSuccessful) {
                val msg = ((root?.get("error") as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
                throw Exception(
                    when {
                        it.code == 400 && msg?.contains("API key", true) == true -> "That Gemini API key was rejected. Check it in Google AI Studio."
                        it.code == 429 -> "Gemini rate limit reached. Try again in a moment."
                        else -> msg ?: "Gemini request failed (${it.code})."
                    },
                )
            }
            val parts = ((((root?.get("candidates") as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.get("content") as? JsonObject)?.get("parts") as? JsonArray)
            val answer = parts?.mapNotNull { p -> (p as? JsonObject)?.str("text") }?.joinToString("")?.trim()
            answer?.takeIf { a -> a.isNotBlank() } ?: "Gemini returned no answer."
        }
    }

    private fun get(url: String): String? {
        val response = http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute()
        return response.use { if (it.isSuccessful) it.body?.string() else null }
    }

    companion object {
        /** Normalizes a stored API key: trimmed, or null when blank (so an empty box clears the key). */
        fun cleanKey(key: String?): String? = key?.trim()?.takeIf { it.isNotEmpty() }

        /** Removes HTML tags (Wikipedia excerpts come with <span> highlight markup) and collapses whitespace. */
        fun stripHtml(s: String): String = s.replace(Regex("<[^>]*>"), "")
            .replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'")
            .replace(Regex("\\s+"), " ").trim()
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
