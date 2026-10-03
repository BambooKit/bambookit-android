package com.bambookit.android.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
data class CachedDiagram(val savedAt: Long, val diagram: ProjectDiagram)

/**
 * The last project diagram per project, so the Diagram tab shows something immediately while the PC
 * rebuilds it (which can take up to a minute on large projects). Kept in memory and in a small disk
 * cache inside the app's cache folder (the system may clear it; nothing is uploaded).
 */
class DiagramCache(private val dir: File, private val json: Json) {
    private val memory = HashMap<String, CachedDiagram>()

    private fun fileFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
        return File(dir, "$hash.json")
    }

    suspend fun get(key: String): CachedDiagram? {
        synchronized(memory) { memory[key] }?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val f = fileFor(key)
                if (!f.isFile) null else json.decodeFromString(CachedDiagram.serializer(), f.readText())
            }.getOrNull()
        }?.also { synchronized(memory) { memory[key] = it } }
    }

    suspend fun put(key: String, diagram: ProjectDiagram) {
        val entry = CachedDiagram(System.currentTimeMillis(), diagram)
        synchronized(memory) { memory[key] = entry }
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                val text = json.encodeToString(CachedDiagram.serializer(), entry)
                if (text.length > MAX_BYTES) return@runCatching
                fileFor(key).writeText(text)
                // Keep the cache small: only the most recent diagrams.
                dir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() }?.drop(MAX_FILES)?.forEach { it.delete() }
            }.onFailure { Log.w("DiagramCache", "could not save: ${it.message}") }
        }
    }

    /** Sign-out: forget every saved diagram (they belong to the signed-in account's projects). */
    fun clear() {
        synchronized(memory) { memory.clear() }
        runCatching { dir.listFiles { f -> f.extension == "json" }?.forEach { it.delete() } }
    }

    companion object {
        private const val MAX_FILES = 12
        private const val MAX_BYTES = 2_000_000
    }
}
