package com.bambookit.android.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bambookit.android.BuildConfig
import kotlinx.coroutines.CancellationException
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
import java.io.IOException

/** A published BambooKit for Android release (GET /v1/releases/latest, or GitHub Releases directly). */
@Serializable
data class ReleaseInfo(
    val version: String,
    val tag: String = "",
    val name: String? = null,
    val publishedAt: String? = null,
    val notes: String = "",
    val url: String = "",
    val download: ReleaseDownload? = null,
    /** "api" or "github". */
    val source: String = "api",
)

@Serializable data class ReleaseDownload(val name: String = "", val url: String, val size: Long = 0)

/** Why a check or download failed. */
enum class UpdateFailure { NoInternet, ServerUnavailable, InvalidResponse, NoRelease, DownloadFailed, Other }

/** What the update banner and the App updates screen show. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val release: ReleaseInfo?) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState {
        val version get() = release.version
        val notes get() = release.notes
        val size get() = release.download?.size ?: 0L
    }
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Ready(val version: String, val file: File, val note: String? = null) : UpdateState
    /** Android's installer was opened; the app only says "installed" once the package actually changed. */
    data class Installing(val version: String, val file: File) : UpdateState
    data class Failed(val kind: UpdateFailure, val message: String, val diagnosis: Diagnosis) : UpdateState
}

private class UpdateCheckException(val kind: UpdateFailure, message: String, val diagnosis: Diagnosis) : Exception(message)

@Serializable
private data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val html_url: String = "",
    val published_at: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(val name: String, val browser_download_url: String, val size: Long = 0)

@Serializable private data class ReleaseEnvelope(val data: ReleaseInfo)

/**
 * Updates the app from BambooKit releases: GET /v1/releases/latest?platform=android on the BambooKit API,
 * or GitHub Releases directly when the server is older and lacks that route. Release builds only: the APK is
 * downloaded into the app's cache, checked, and handed to Android's installer, which always asks the user to
 * confirm. Updates install over the current app because every release is signed with the same BambooKit key.
 */
class AppUpdater(private val context: Context, private val http: OkHttpClient, private val json: Json, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Debug builds are a separate app (com.bambookit.android.debug) and are not updated from releases. */
    val enabled: Boolean get() = !BuildConfig.DEBUG

    private var job: Job? = null
    private val prefs = context.getSharedPreferences("bambookit.updates", Context.MODE_PRIVATE)

    private val _lastCheckedAt = MutableStateFlow(prefs.getLong(KEY_LAST_CHECK, 0L).takeIf { it > 0 })
    /** When the last successful check finished (epoch ms), kept across restarts. */
    val lastCheckedAt: StateFlow<Long?> = _lastCheckedAt
    private val _latest = MutableStateFlow(prefs.getString(KEY_LATEST, null)?.let { runCatching { json.decodeFromString(ReleaseInfo.serializer(), it) }.getOrNull() })
    /** The latest release from the last successful check (cached). */
    val latest: StateFlow<ReleaseInfo?> = _latest

    private val _installed = MutableStateFlow<String?>(null)
    /** "BambooKit 1.0.6 was installed" once, after an update really replaced the app. */
    val installed: StateFlow<String?> = _installed

    init {
        // An install was started before: did the package actually change?
        prefs.getString(KEY_PENDING, null)?.let { pending ->
            val fromCode = prefs.getLong(KEY_PENDING_FROM_CODE, -1)
            if (BuildConfig.VERSION_CODE.toLong() != fromCode && compareVersions(BuildConfig.VERSION_NAME, pending) >= 0) {
                _installed.value = "BambooKit ${BuildConfig.VERSION_NAME} was installed."
            }
            prefs.edit().remove(KEY_PENDING).remove(KEY_PENDING_FROM_CODE).apply()
        }
        _latest.value?.let { r ->
            _state.value = if (isNewer(r.version, BuildConfig.VERSION_NAME) && r.download != null && prefs.getString(KEY_SKIPPED, null) != r.version) UpdateState.Available(r)
            else UpdateState.UpToDate(r)
        }
    }

    /** Check at most every [minIntervalMs] (the last check is kept across restarts); [force] for "Check for updates". */
    fun check(force: Boolean = false, minIntervalMs: Long = AUTO_CHECK_MS) {
        if (!enabled) return
        if (job?.isActive == true) return
        val s = _state.value
        if (s is UpdateState.Downloading || s is UpdateState.Installing) return
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
        if (!force && now - last < minIntervalMs) return
        prefs.edit().putLong(KEY_LAST_ATTEMPT, now).apply()
        job = scope.launch {
            if (force) _state.value = UpdateState.Checking
            try {
                val release = latestRelease()
                val t = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_CHECK, t).putString(KEY_LATEST, json.encodeToString(ReleaseInfo.serializer(), release)).apply()
                _lastCheckedAt.value = t
                _latest.value = release
                val cur = _state.value
                _state.value = when {
                    // Keep a download that is ready for the same version.
                    cur is UpdateState.Ready && cur.version == release.version -> cur
                    !isNewer(release.version, BuildConfig.VERSION_NAME) -> UpdateState.UpToDate(release)
                    release.download == null -> if (force) UpdateState.Failed(UpdateFailure.NoRelease, "Version ${release.version} has no Android app (APK) attached.", Diagnosis("The release has no APK", "NO_RELEASE")) else UpdateState.UpToDate(release)
                    !force && prefs.getString(KEY_SKIPPED, null) == release.version -> UpdateState.UpToDate(release)
                    else -> UpdateState.Available(release)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateCheckException) {
                if (force) _state.value = UpdateState.Failed(e.kind, e.message ?: "Could not check for updates", e.diagnosis)
                else if (_state.value is UpdateState.Checking) _state.value = UpdateState.Idle
            } catch (e: Exception) {
                if (force) _state.value = UpdateState.Failed(UpdateFailure.Other, "Could not check for updates: ${e.message}", Diagnosis(e.message ?: "Update check failed"))
            }
        }
    }

    /** The latest release: the BambooKit API first, GitHub when the server is older than API 1.1.0. */
    private suspend fun latestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        val path = "/v1/releases/latest"
        val req = Request.Builder().url("${Config.apiUrl}$path?platform=android").header("Accept", "application/json").build()
        val res = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw networkFailure("GET", path)
        }
        res.use {
            val text = it.body?.string().orEmpty()
            if (it.isSuccessful) {
                return@withContext runCatching { json.decodeFromString(ReleaseEnvelope.serializer(), text).data }
                    .getOrNull()?.takeIf { r -> r.version.isNotBlank() }
                    ?: throw invalid("GET", path, it.code, it.header("X-Request-Id"))
            }
            val err = ApiClient.errorFrom(json, "GET", path, it.code, text, it.header("X-Request-Id"), it.header(ApiClient.API_VERSION_HEADER))
            Diagnostics.record(err.diagnosis)
            when {
                err.code == "NO_RELEASE" -> throw UpdateCheckException(UpdateFailure.NoRelease, "No BambooKit for Android release has been published yet.", err.diagnosis)
                // Older servers: the route is missing (404 "Route not found"), or unknown routes ask for a sign-in (401).
                err.isRouteMissing || it.code == 401 -> return@withContext fromGitHub()
                err.code == "UPDATE_SOURCE_UNAVAILABLE" || it.code >= 500 ->
                    return@withContext runCatching { fromGitHub() }.getOrElse { _ ->
                        throw UpdateCheckException(UpdateFailure.ServerUnavailable, "The update server is unavailable right now. Try again later.", err.diagnosis)
                    }
                err.code == "INVALID_RELEASE" -> throw UpdateCheckException(UpdateFailure.InvalidResponse, "The latest release is missing its version. Try again later.", err.diagnosis)
                else -> throw UpdateCheckException(UpdateFailure.Other, err.message ?: "Could not check for updates", err.diagnosis)
            }
        }
    }

    private fun fromGitHub(): ReleaseInfo {
        val path = "github releases/latest"
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BambooKit-Android/${BuildConfig.VERSION_NAME}")
            .build()
        val res = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw networkFailure("GET", path)
        }
        res.use {
            if (it.code == 404) throw UpdateCheckException(UpdateFailure.NoRelease, "No BambooKit for Android release has been published yet.", Diagnosis("No release", "NO_RELEASE", 404, "GET", path))
            if (!it.isSuccessful) {
                val d = Diagnosis("GitHub answered ${it.code}", "SERVER_UNAVAILABLE", it.code, "GET", path)
                Diagnostics.record(d)
                throw UpdateCheckException(UpdateFailure.ServerUnavailable, "The update server is unavailable right now (${it.code}). Try again later.", d)
            }
            val g = runCatching { json.decodeFromString(GitHubRelease.serializer(), it.body!!.string()) }.getOrNull() ?: throw invalid("GET", path, it.code, null)
            if (g.draft || g.prerelease) throw UpdateCheckException(UpdateFailure.NoRelease, "No BambooKit for Android release has been published yet.", Diagnosis("Only a draft or pre-release exists", "NO_RELEASE", it.code, "GET", path))
            val apk = g.assets.firstOrNull { a -> a.name.endsWith(".apk", ignoreCase = true) }
            return ReleaseInfo(
                version = g.tag_name.removePrefix("v").removePrefix("V"), tag = g.tag_name, name = g.name, publishedAt = g.published_at,
                notes = g.body.orEmpty().trim(), url = g.html_url, download = apk?.let { a -> ReleaseDownload(a.name, a.browser_download_url, a.size) }, source = "github",
            )
        }
    }

    private fun invalid(method: String, path: String, status: Int, requestId: String?): UpdateCheckException {
        val d = Diagnosis("The release information couldn't be read", "INVALID_RESPONSE", status, method, path, requestId)
        Diagnostics.record(d)
        return UpdateCheckException(UpdateFailure.InvalidResponse, "The update server sent a response this app couldn't read.", d)
    }

    private fun networkFailure(method: String, path: String): UpdateCheckException {
        val online = isOnline()
        val d = Diagnosis(if (online) "The update server couldn't be reached" else "No internet connection", if (online) "SERVER_UNAVAILABLE" else "NETWORK", 0, method, path)
        Diagnostics.record(d)
        return if (online) UpdateCheckException(UpdateFailure.ServerUnavailable, "The update server couldn't be reached. It may be waking up; try again in a minute.", d)
        else UpdateCheckException(UpdateFailure.NoInternet, "No internet connection. Connect to Wi-Fi or mobile data and try again.", d)
    }

    private fun isOnline(): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    /** Hide the banner for this version until a newer one is released. */
    fun skip(version: String) {
        prefs.edit().putString(KEY_SKIPPED, version).apply()
        _state.value = UpdateState.UpToDate(_latest.value)
    }

    fun download(update: UpdateState.Available) {
        if (job?.isActive == true) return
        val dl = update.release.download ?: return
        job = scope.launch {
            _state.value = UpdateState.Downloading(update.version, 0f)
            _state.value = try {
                UpdateState.Ready(update.version, fetch(update.version, dl))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val online = isOnline()
                val msg = if (!online) "Download failed: no internet connection." else "Download failed: ${e.message ?: "the connection was interrupted"}."
                val d = Diagnosis(msg, if (online) "DOWNLOAD_FAILED" else "NETWORK", (e as? DownloadException)?.status ?: 0, "GET", "release download (${dl.name.ifBlank { "APK" }})")
                Diagnostics.record(d)
                UpdateState.Failed(UpdateFailure.DownloadFailed, msg, d)
            }
            (_state.value as? UpdateState.Ready)?.let { install(it) }
        }
    }

    private class DownloadException(message: String, val status: Int = 0) : Exception(message)

    private suspend fun fetch(version: String, dl: ReleaseDownload): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "BambooKit-$version.apk")
        val req = Request.Builder().url(dl.url).header("User-Agent", "BambooKit-Android/${BuildConfig.VERSION_NAME}").build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw DownloadException("the server answered ${res.code}", res.code)
            val body = res.body ?: throw DownloadException("empty response", res.code)
            val total = body.contentLength().takeIf { it > 0 } ?: dl.size
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
                                _state.value = UpdateState.Downloading(version, p)
                            }
                        }
                    }
                }
            }
            if (dl.size > 0 && file.length() != dl.size) throw DownloadException("incomplete download")
        }
        // The file must be a BambooKit APK of that version before the installer sees it.
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
        if (info == null || info.packageName != context.packageName) throw DownloadException("the file is not a BambooKit app")
        file
    }

    /** Opens Android's installer for a downloaded update (asks for "install unknown apps" once). */
    fun install(ready: UpdateState.Ready) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            _state.value = ready.copy(note = "Allow BambooKit to install apps, then tap Install.")
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready.file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        prefs.edit().putString(KEY_PENDING, ready.version).putLong(KEY_PENDING_FROM_CODE, BuildConfig.VERSION_CODE.toLong()).apply()
        context.startActivity(intent)
        _state.value = UpdateState.Installing(ready.version, ready.file)
    }

    /**
     * The app came back to the foreground. A successful update replaces (and restarts) the app, so still running
     * the old version after the installer means it wasn't installed (cancelled or failed).
     */
    fun onResume() {
        val s = _state.value
        if (s is UpdateState.Installing && BuildConfig.VERSION_NAME != s.version) {
            _state.value = UpdateState.Ready(s.version, s.file, note = "BambooKit ${s.version} wasn't installed yet. Tap Install to try again.")
        }
        check()
    }

    fun dismissInstalled() {
        _installed.value = null
    }

    fun dismissError() {
        if (_state.value is UpdateState.Failed) _state.value = _latest.value?.let { UpdateState.UpToDate(it) } ?: UpdateState.Idle
    }

    companion object {
        const val AUTO_CHECK_MS = 6 * 60 * 60 * 1000L
        private const val KEY_LAST_CHECK = "lastCheckedAt"
        private const val KEY_LAST_ATTEMPT = "lastAttemptAt"
        private const val KEY_LATEST = "latestRelease"
        private const val KEY_SKIPPED = "skipped"
        private const val KEY_PENDING = "pendingInstall"
        private const val KEY_PENDING_FROM_CODE = "pendingInstallFromCode"

        /** Compares dotted versions numerically ("1.0.10" > "1.0.2", "v1.0.6" = "1.0.6"); returns <0, 0 or >0. Pre-release suffixes are ignored. */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
                .split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            val pa = parts(a)
            val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }

        /** True when [latest] is a newer release than [current]. */
        fun isNewer(latest: String, current: String): Boolean = latest.isNotBlank() && compareVersions(latest, current) > 0
    }
}
