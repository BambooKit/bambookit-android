package com.bambookit.android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bambookit.android.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** What the update banner and the Devices screen show. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val notes: String, val url: String, val size: Long, val page: String) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Ready(val version: String, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Serializable
private data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val html_url: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(val name: String, val browser_download_url: String, val size: Long = 0)

/**
 * Updates the app from GitHub Releases of the public BambooKit Android repository. Release builds
 * only: the APK is downloaded into the app's cache and handed to Android's installer, which always
 * asks the user to confirm. Updates install over the current app because every release is signed
 * with the same BambooKit key.
 */
class AppUpdater(private val context: Context, private val http: OkHttpClient, private val json: Json, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Debug builds are a separate app (com.bambookit.android.debug) and are not updated from releases. */
    val enabled: Boolean get() = !BuildConfig.DEBUG

    private var lastCheck = 0L
    private var job: Job? = null
    private val prefs = context.getSharedPreferences("bambookit.updates", Context.MODE_PRIVATE)

    /** Check at most every [minIntervalMs]; [force] for the "Check for updates" button. */
    fun check(force: Boolean = false, minIntervalMs: Long = 6 * 60 * 60 * 1000L) {
        if (!enabled) return
        if (job?.isActive == true) return
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < minIntervalMs) return
        lastCheck = now
        job = scope.launch {
            if (force) _state.value = UpdateState.Checking
            _state.value = runCatching { latest() }.fold(
                onSuccess = { it },
                onFailure = { if (force) UpdateState.Failed("Could not check for updates: ${it.message}") else _state.value },
            )
        }
    }

    private suspend fun latest(): UpdateState = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BambooKit-Android/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(req).execute().use { res ->
            if (res.code == 404) return@withContext UpdateState.UpToDate
            if (!res.isSuccessful) error("GitHub answered ${res.code}")
            val release = json.decodeFromString(GitHubRelease.serializer(), res.body!!.string())
            val version = release.tag_name.removePrefix("v")
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            when {
                release.draft || release.prerelease || apk == null -> UpdateState.UpToDate
                compareVersions(version, BuildConfig.VERSION_NAME) <= 0 -> UpdateState.UpToDate
                prefs.getString("skipped", null) == version && _state.value !is UpdateState.Checking -> UpdateState.UpToDate
                else -> UpdateState.Available(version, release.body.orEmpty().trim(), apk.browser_download_url, apk.size, release.html_url)
            }
        }
    }

    /** Hide the banner for this version until a newer one is released. */
    fun skip(version: String) {
        prefs.edit().putString("skipped", version).apply()
        _state.value = UpdateState.UpToDate
    }

    fun download(update: UpdateState.Available) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(update.version, 0f)
            _state.value = runCatching { fetch(update) }.fold(
                onSuccess = { UpdateState.Ready(update.version, it) },
                onFailure = { UpdateState.Failed("Download failed: ${it.message}") },
            )
            (_state.value as? UpdateState.Ready)?.let { install(it) }
        }
    }

    private suspend fun fetch(update: UpdateState.Available): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "BambooKit-${update.version}.apk")
        val req = Request.Builder().url(update.url).header("User-Agent", "BambooKit-Android/${BuildConfig.VERSION_NAME}").build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("server answered ${res.code}")
            val body = res.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: update.size
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = done.toFloat() / total
                            if (p - last >= 0.01f) {
                                last = p
                                _state.value = UpdateState.Downloading(update.version, p)
                            }
                        }
                    }
                }
            }
            if (update.size > 0 && file.length() != update.size) error("incomplete download")
        }
        file
    }

    /** Opens Android's installer for a downloaded update (asks for "install unknown apps" once). */
    fun install(ready: UpdateState.Ready) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready.file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun dismissError() {
        if (_state.value is UpdateState.Failed) _state.value = UpdateState.Idle
    }

    companion object {
        /** Compares dotted versions numerically ("1.0.10" > "1.0.2"); returns <0, 0 or >0. */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }
    }
}
