package com.bambookit.android.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

/** A pending remote action shown in the UI until the desktop reports its result. */
data class PendingCommand(val id: String, val type: String, val status: String, val error: String? = null, val deviceOnline: Boolean = true)

/**
 * Why something could not be read from the PC. [code]: DESKTOP_OFFLINE, DESKTOP_TIMEOUT, DESKTOP_OUTDATED
 * (the PC's BambooKit Desktop does not know this request yet), DESKTOP_ERROR (the PC answered with an error),
 * TIMEOUT (no answer in time), NETWORK, or an API error code.
 */
data class ContentError(val code: String, val message: String) {
    val desktopUnavailable get() = code == "DESKTOP_OFFLINE" || code == "DESKTOP_TIMEOUT"
    val timedOut get() = code == "DESKTOP_TIMEOUT" || code == "TIMEOUT"
    val desktopOutdated get() = code == "DESKTOP_OUTDATED"
}

/** Maps an API failure to a [ContentError], recognising an older PC that does not know the request kind. */
fun contentErrorOf(e: Throwable, fallback: String): ContentError {
    val api = e as? ApiException ?: return ContentError("UNKNOWN", e.message ?: fallback)
    val msg = api.message ?: fallback
    if (api.code == "DESKTOP_ERROR" && Regex("unsupported request|unknown (request|kind)|not supported", RegexOption.IGNORE_CASE).containsMatchIn(msg)) {
        return ContentError("DESKTOP_OUTDATED", "Update BambooKit Desktop on your PC to see this from your phone.")
    }
    return ContentError(api.code, msg)
}

/**
 * "Continue on PC" in progress for the open session: sent at [sentAt] (elapsed realtime ms) and waiting for
 * session.updated with remote = true. [error] is set when it failed or timed out.
 */
data class ContinueRequest(val sentAt: Long, val error: String? = null)

data class SessionDetail(
    val sessionId: String,
    val session: Session? = null,
    /** Chat and changed files are relayed live from the PC; the API never stores them. */
    val parts: List<Part> = emptyList(),
    val changes: List<ChangedFile> = emptyList(),
    val approvals: List<Approval> = emptyList(),
    val commands: List<PendingCommand> = emptyList(),
    /** Session metadata (title, status, model) from the API. */
    val loading: Boolean = true,
    val error: String? = null,
    /** Chat content read from the PC. */
    val contentLoading: Boolean = true,
    val contentLoaded: Boolean = false,
    val contentError: ContentError? = null,
    /** File map and project diagram, read live from the PC the first time their view is opened (null = not requested). */
    val fileMap: FileMapView? = null,
    val diagram: DiagramView? = null,
    val tree: TreeView? = null,
    /** Prompts, timeline, changes, tests and summary: live from the PC, or its saved copy (7 days). */
    val history: HistoryView = HistoryView(),
    /** "Continue on PC" sent and not yet confirmed (null when none is in progress). */
    val continueRequest: ContinueRequest? = null,
    /** A message is being sent (SEND_MESSAGE). */
    val sending: Boolean = false,
    /** When this session was last read from the API (elapsed realtime ms), to avoid re-reading on rotation. */
    val loadedAt: Long = 0,
)

data class HistoryView(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val data: HistoryResponse? = null,
    val error: ContentError? = null,
)

/** Before/after of one changed file, read live from the PC (GET /file-versions). */
data class VersionsView(val path: String, val loading: Boolean = true, val versions: FileVersions? = null, val error: ContentError? = null)

/** The signed-in user's profile (GET /v1/me) and the state of profile actions. */
data class ProfileView(
    val account: Account? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val photoBusy: Boolean = false,
    val deleting: Boolean = false,
    val savingName: Boolean = false,
)

data class FileMapView(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val entries: List<FileMapEntry> = emptyList(),
    val error: ContentError? = null,
)

data class DiagramView(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val diagram: ProjectDiagram? = null,
    val error: ContentError? = null,
    /** The diagram shown is the last one saved on this phone (time in epoch ms) while a fresh one is built. */
    val cachedAt: Long? = null,
)

/** A project file opened on the phone, read live from the PC. View only — the phone never edits files. */
data class FileView(
    val path: String,
    val loading: Boolean = true,
    val content: String? = null,
    val size: Long? = null,
    val error: ContentError? = null,
)

/** The project folder browser: [path] is the folder shown ("" = project root). */
data class TreeView(
    val path: String = "",
    val loading: Boolean = true,
    val listing: TreeListing? = null,
    val error: ContentError? = null,
)

/**
 * Single source of truth for the UI. All data comes from bambookit-api (session content relayed
 * live from the PC, or the PC's 7-day cloud copy); realtime events keep it current.
 *
 * The phone is view-only: the only actions it sends are Stop (ABORT) for a running session and
 * answers to approvals. Nothing is changed locally to "pretend" an action worked — Stop appears as a
 * pending command until the desktop reports the outcome.
 */
class BambooStore(
    private val api: ApiClient,
    private val auth: AuthRepository,
    private val realtime: RealtimeClient,
    private val store: SecureStore,
    private val json: Json,
    private val scope: CoroutineScope,
    private val diagramCache: DiagramCache,
    private val notifier: (NotificationItem) -> Unit,
) {
    /** Each BambooKit notification rings once, and never for events from before the connection started. */
    private val notificationGate = NotificationGate()
    val session get() = auth.session
    val link get() = realtime.state

    private val _overview = MutableStateFlow<Overview?>(null)
    val overview: StateFlow<Overview?> = _overview
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions
    private val _approvals = MutableStateFlow<List<Approval>>(emptyList())
    val approvals: StateFlow<List<Approval>> = _approvals
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications
    private val _detail = MutableStateFlow<SessionDetail?>(null)
    val detail: StateFlow<SessionDetail?> = _detail
    private val _versions = MutableStateFlow<VersionsView?>(null)
    val versions: StateFlow<VersionsView?> = _versions
    private val _profile = MutableStateFlow(ProfileView())
    val profile: StateFlow<ProfileView> = _profile
    private val _error = MutableStateFlow<String?>(null)
    /** Last failed full refresh (cleared by the next successful one). */
    val error: StateFlow<String?> = _error
    private val _loaded = MutableStateFlow(false)
    /** True once a full refresh has succeeded at least once. */
    val loaded: StateFlow<Boolean> = _loaded
    private val _refreshing = MutableStateFlow(false)
    /** True while a full (pull-to-refresh) refresh is running. */
    val refreshing: StateFlow<Boolean> = _refreshing
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages
    private val _myDeviceId = MutableStateFlow(store.deviceId)
    val myDeviceId: StateFlow<String?> = _myDeviceId

    private var started = false
    private val _file = MutableStateFlow<FileView?>(null)
    val file: StateFlow<FileView?> = _file
    private var presenceJob: Job? = null
    private var listenJobs: List<Job> = emptyList()
    private var historyJob: Job? = null
    private var historyQueued = false
    private var versionsJob: Job? = null
    private var contentJob: Job? = null
    private var fileMapJob: Job? = null
    private var diagramJob: Job? = null
    private var treeJob: Job? = null
    private var fileJob: Job? = null

    fun start() {
        if (started || auth.session.value == null) return
        started = true
        scope.launch {
            runCatching { api.registerPhone() }
                .onSuccess { store.deviceId = it.id; _myDeviceId.value = it.id }
                .onFailure { report("Could not register this phone", it) }
            realtime.start()
        }
        loadProfile()
        // Collectors live until sign-out, so signing in again never adds a second set.
        listenJobs = listOf(
            scope.launch { realtime.ready.collect { refreshAll() } },
            scope.launch { realtime.events.collect { handle(it) } },
        )
        // Keep last-seen fresh so the desktop can show this phone as online.
        presenceJob = scope.launch {
            while (true) {
                delay(60_000)
                if (auth.session.value == null) break
                runCatching { api.me() }
            }
        }
    }

    /**
     * Signs out on this phone: stops realtime, forgets the auth session, this phone's device id and the
     * realtime resume point, and clears everything shown in memory. Nothing on the server or on the PCs
     * is deleted; the installation id and app settings (App lock) stay. [keepPendingSignIn] keeps a
     * browser sign-in that is in progress (used when the sign-in screen tidies up after an expired session).
     */
    suspend fun signOut(keepPendingSignIn: Boolean = false) {
        realtime.stop()
        presenceJob?.cancel()
        listenJobs.forEach { it.cancel() }
        listenJobs = emptyList()
        closeSession()
        started = false
        store.clearSignedInState(keepPendingSignIn)
        auth.signOut()
        imageCache.evictAll()
        notificationGate.clear()
        diagramCache.clear()
        _profile.value = ProfileView()
        _myDeviceId.value = null
        _overview.value = null
        _devices.value = emptyList()
        _projects.value = emptyList()
        _sessions.value = emptyList()
        _approvals.value = emptyList()
        _notifications.value = emptyList()
        _detail.value = null
        _error.value = null
        _loaded.value = false
    }

    fun refreshAll() = scope.launch {
        _refreshing.value = true
        runCatching {
            _overview.value = api.overview()
            _devices.value = api.devices()
            _projects.value = api.projects()
            _sessions.value = api.sessions()
            _approvals.value = api.approvals()
            _notifications.value = api.notifications()
            _error.value = null
            _loaded.value = true
        }.onFailure { report("Refresh failed", it) }
        _refreshing.value = false
        _detail.value?.let { loadSession(it.sessionId, keep = true) }
        if (_profile.value.account == null) loadProfile()
    }

    /** The PC (desktop device) a session lives on, from the devices list. */
    fun desktopOf(session: Session?): Device? = session?.let { s -> _devices.value.firstOrNull { it.id == s.deviceId } }

    private fun pcName(deviceId: String?): String = _devices.value.firstOrNull { it.id == deviceId }?.name ?: "Your PC"

    // ---------------------------------------------------------------- session detail

    fun openSession(id: String) {
        val d = _detail.value
        // Re-opening right after a load (e.g. the phone was rotated) keeps what is shown.
        if (d?.sessionId == id && d.loadedAt > 0 && android.os.SystemClock.elapsedRealtime() - d.loadedAt < REOPEN_FRESH_MS) return
        if (d?.sessionId != id) _detail.value = SessionDetail(sessionId = id)
        loadSession(id, keep = true)
    }

    fun closeSession() {
        contentJob?.cancel()
        fileMapJob?.cancel()
        diagramJob?.cancel()
        treeJob?.cancel()
        fileJob?.cancel()
        historyJob?.cancel()
        historyQueued = false
        versionsJob?.cancel()
        _detail.value = null
        _versions.value = null
        _file.value = null
    }

    /** Re-reads the open session: metadata from the API, chat and changes live from the PC. */
    fun reloadSession() {
        val d = _detail.value ?: return
        loadSession(d.sessionId, keep = true)
        if (d.fileMap != null) loadFileMap()
    }

    /**
     * Every file the open session read, created, edited or deleted — read live from the PC.
     * [delayMs] debounces refreshes triggered by realtime events.
     */
    fun loadFileMap(delayMs: Long = 0) {
        val id = _detail.value?.sessionId ?: return
        fileMapJob?.cancel()
        _detail.update { d -> if (d?.sessionId == id) d.copy(fileMap = (d.fileMap ?: FileMapView()).copy(loading = true)) else d }
        fileMapJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            try {
                val entries = api.fileMap(id)
                _detail.update { d -> if (d?.sessionId == id) d.copy(fileMap = FileMapView(loading = false, loaded = true, entries = entries)) else d }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val err = contentError(e, "Could not read the file map from ${pcName(_detail.value?.session?.deviceId)}.")
                _detail.update { d -> if (d?.sessionId == id) d.copy(fileMap = (d.fileMap ?: FileMapView()).copy(loading = false, error = err)) else d }
            }
        }
    }

    /** Cache key of the open session's project diagram: the project, or its folder on that PC. */
    private fun diagramKey(d: SessionDetail): String? {
        val s = d.session ?: return null
        return s.projectId?.let { "p:$it" } ?: "d:${s.deviceId}:${s.directory}"
    }

    /**
     * The session's project drawn as components; computed fresh on the PC on every call (Rescan), which can
     * take up to a minute on large projects. The last diagram of the project (memory + small disk cache) is
     * shown meanwhile.
     */
    fun loadDiagram() {
        val id = _detail.value?.sessionId ?: return
        diagramJob?.cancel()
        _detail.update { d -> if (d?.sessionId == id) d.copy(diagram = (d.diagram ?: DiagramView()).copy(loading = true, error = null)) else d }
        diagramJob = scope.launch {
            val key = _detail.value?.let { diagramKey(it) }
            if (key != null && _detail.value?.diagram?.diagram == null) {
                diagramCache.get(key)?.let { cached ->
                    _detail.update { d ->
                        if (d?.sessionId == id && d.diagram?.diagram == null) d.copy(diagram = DiagramView(loading = true, loaded = true, diagram = cached.diagram, cachedAt = cached.savedAt)) else d
                    }
                }
            }
            try {
                val diagram = api.diagram(id)
                _detail.update { d -> if (d?.sessionId == id) d.copy(diagram = DiagramView(loading = false, loaded = true, diagram = diagram)) else d }
                if (key != null && diagram != null && diagram.nodes.isNotEmpty()) diagramCache.put(key, diagram)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val err = contentError(e, "Could not scan the project on ${pcName(_detail.value?.session?.deviceId)}.")
                _detail.update { d -> if (d?.sessionId == id) d.copy(diagram = (d.diagram ?: DiagramView()).copy(loading = false, error = err)) else d }
            }
        }
    }

    /** Lists one folder of the open session's project ("" = root), live from the PC. */
    fun loadTree(path: String) {
        val id = _detail.value?.sessionId ?: return
        treeJob?.cancel()
        _detail.update { d ->
            if (d?.sessionId != id) d
            else d.copy(tree = TreeView(path = path, loading = true, listing = d.tree?.listing?.takeIf { d.tree.path == path }))
        }
        treeJob = scope.launch {
            try {
                val listing = api.tree(id, path)
                _detail.update { d -> if (d?.sessionId == id) d.copy(tree = TreeView(path = path, loading = false, listing = listing)) else d }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val err = contentError(e, "Could not list this folder on ${pcName(_detail.value?.session?.deviceId)}.")
                _detail.update { d -> if (d?.sessionId == id && d.tree?.path == path) d.copy(tree = d.tree.copy(loading = false, error = err)) else d }
            }
        }
    }

    private fun contentError(e: Exception, fallback: String): ContentError = contentErrorOf(e, fallback)

    /** Retry reading the chat and changed files from the PC. */
    fun retryContent() {
        _detail.value?.let { loadContent(it.sessionId) }
    }

    /**
     * Reads the session history (prompts, timeline, changes, tests, summary): live from the PC while it is
     * online, otherwise the PC's saved copy from the last 7 days. 503 DESKTOP_OFFLINE = neither is available.
     * With [delayMs] (realtime refreshes) a read already waiting or running is never restarted: further
     * triggers queue one more read after it, so a busy session is re-read every few seconds at most and
     * a read in flight always completes.
     */
    fun loadHistory(delayMs: Long = 0) {
        val id = _detail.value?.sessionId ?: return
        if (delayMs > 0 && historyJob?.isActive == true) {
            historyQueued = true
            return
        }
        historyJob?.cancel()
        historyQueued = false
        if (delayMs == 0L) _detail.update { d -> if (d?.sessionId == id) d.copy(history = d.history.copy(loading = true)) else d }
        historyJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            while (true) {
                historyQueued = false
                fetchHistory(id)
                if (!historyQueued || _detail.value?.sessionId != id) break
                delay(HISTORY_REFRESH_MS)
            }
        }
    }

    private suspend fun fetchHistory(id: String) {
        _detail.update { d -> if (d?.sessionId == id) d.copy(history = d.history.copy(loading = true)) else d }
        try {
            val h = api.history(id)
            _detail.update { d -> if (d?.sessionId == id) d.copy(history = HistoryView(loading = false, loaded = true, data = h)) else d }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val err = contentError(e, "Could not read this session's history.")
            _detail.update { d -> if (d?.sessionId == id) d.copy(history = d.history.copy(loading = false, error = err)) else d }
        }
    }

    /** Before/after of one changed file, live from the PC. */
    fun loadVersions(path: String) {
        val id = _detail.value?.sessionId ?: return
        versionsJob?.cancel()
        _versions.value = VersionsView(path)
        versionsJob = scope.launch {
            try {
                val v = api.fileVersions(id, path)
                if (_versions.value?.path == path) _versions.value = VersionsView(path, loading = false, versions = v)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val err = contentError(e, "Could not read this file from ${pcName(_detail.value?.session?.deviceId)}.")
                if (_versions.value?.path == path) _versions.value = VersionsView(path, loading = false, error = err)
            }
        }
    }

    fun clearVersions() {
        versionsJob?.cancel()
        _versions.value = null
    }

    private fun loadSession(id: String, keep: Boolean) = scope.launch {
        loadHistory()
        runCatching {
            val session = api.session(id)
            val approvals = api.approvals().filter { it.sessionId == id }
            _detail.update { d ->
                if (d?.sessionId != id) d
                else d.copy(
                    session = session, approvals = approvals, loading = false, error = null, commands = if (keep) d.commands else emptyList(),
                    loadedAt = android.os.SystemClock.elapsedRealtime(),
                    continueRequest = d.continueRequest?.takeUnless { session.remote },
                )
            }
        }.onFailure { e -> _detail.update { d -> if (d?.sessionId == id) d.copy(loading = false, error = e.message) else d } }
        loadContent(id)
    }

    /**
     * Chat parts and changed files are read live from the PC through the API relay. When the PC is
     * offline (503 DESKTOP_OFFLINE / DESKTOP_TIMEOUT) nothing is shown in their place — the screen
     * explains why and offers a retry. Content already read from the PC earlier is kept.
     */
    private fun loadContent(id: String, partsOnly: Boolean = false) {
        contentJob?.cancel()
        _detail.update { d -> if (d?.sessionId == id) d.copy(contentLoading = true) else d }
        contentJob = scope.launch {
            try {
                val (parts, changes) = coroutineScope {
                    val p = async { api.parts(id) }
                    val c = async { if (partsOnly) null else api.changes(id) }
                    p.await() to c.await()
                }
                _detail.update { d ->
                    if (d?.sessionId != id) d
                    else d.copy(parts = parts, changes = changes ?: d.changes, contentLoading = false, contentLoaded = true, contentError = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val err = contentError(e, "Could not read this session from ${pcName(_detail.value?.session?.deviceId)}.")
                _detail.update { d -> if (d?.sessionId == id) d.copy(contentLoading = false, contentError = err) else d }
            }
        }
    }

    /** Stop: asks the PC to abort the running agent in the open session (always allowed). */
    fun stop() = sessionCommand("ABORT", "Could not stop")

    /** Continue: asks the agent to keep going (only after "Continue on PC"). */
    fun continueSession() = sessionCommand("CONTINUE", "Could not continue")

    /** Retry: asks the agent to retry its last step (only after "Continue on PC"). */
    fun retrySession() = sessionCommand("RETRY", "Could not retry")

    private fun sessionCommand(type: String, failure: String) {
        val d = _detail.value ?: return
        scope.launch {
            runCatching { api.sendCommand(d.sessionId, type) }
                .onSuccess { res ->
                    track(PendingCommand(res.data.id, type, res.data.status, deviceOnline = res.deviceOnline))
                    if (!res.deviceOnline) _messages.tryEmit("${pcName(d.session?.deviceId)} is offline. ${commandLabel(type)} runs if it reconnects within 5 minutes.")
                }
                .onFailure { handleCommandFailure(d.sessionId, failure, it) }
        }
    }

    /** Sends a chat message to the agent. [onResult] gets true when the API accepted it (the box is then cleared). */
    fun sendMessage(text: String, onResult: (Boolean) -> Unit) {
        val d = _detail.value ?: return onResult(false)
        val body = text.trim()
        if (body.isEmpty()) return onResult(false)
        _detail.update { it?.copy(sending = true) }
        scope.launch {
            runCatching { api.sendCommand(d.sessionId, "SEND_MESSAGE", buildJsonObject { put("text", body) }) }
                .onSuccess { res ->
                    track(PendingCommand(res.data.id, "SEND_MESSAGE", res.data.status, deviceOnline = res.deviceOnline))
                    if (!res.deviceOnline) _messages.tryEmit("${pcName(d.session?.deviceId)} is offline. The message is sent if it reconnects within 5 minutes.")
                    onResult(true)
                }
                .onFailure {
                    handleCommandFailure(d.sessionId, "Could not send the message", it)
                    onResult(false)
                }
            _detail.update { it?.copy(sending = false) }
        }
    }

    /**
     * Asks the PC to open this session ("Continue on PC"). The PC sets remote = true and a session.updated
     * event follows; until then the screen shows "Opening on your PC..." (timing out after [CONTINUE_TIMEOUT_MS]).
     */
    fun continueOnPc() {
        val d = _detail.value ?: return
        val sentAt = android.os.SystemClock.elapsedRealtime()
        _detail.update { if (it?.sessionId == d.sessionId) it.copy(continueRequest = ContinueRequest(sentAt)) else it }
        scope.launch {
            runCatching { api.sendCommand(d.sessionId, "CONTINUE_ON_PC") }
                .onSuccess { res ->
                    track(PendingCommand(res.data.id, "CONTINUE_ON_PC", res.data.status, deviceOnline = res.deviceOnline))
                    delay(CONTINUE_TIMEOUT_MS)
                    val now = _detail.value
                    if (now?.sessionId == d.sessionId && now.continueRequest?.sentAt == sentAt && now.session?.remote != true) {
                        // Re-read once in case the confirmation was missed, then explain.
                        val fresh = runCatching { api.session(d.sessionId) }.getOrNull()
                        _detail.update { cur ->
                            val req = cur?.continueRequest
                            if (cur?.sessionId != d.sessionId || req?.sentAt != sentAt) cur
                            else if (fresh?.remote == true) cur.copy(session = fresh, continueRequest = null)
                            else cur.copy(
                                continueRequest = req.copy(
                                    error = "${pcName(d.session?.deviceId)} didn't open the session within 30 seconds. Make sure BambooKit Desktop is open and up to date on your PC, then try again.",
                                ),
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _detail.update { cur ->
                        if (cur?.sessionId != d.sessionId) cur
                        else cur.copy(continueRequest = ContinueRequest(sentAt, error = commandError("Could not continue on your PC", e)))
                    }
                }
        }
    }

    fun dismissContinueError() {
        _detail.update { it?.copy(continueRequest = null) }
    }

    private fun handleCommandFailure(sessionId: String, prefix: String, e: Throwable) {
        if ((e as? ApiException)?.code == "SESSION_NOT_CONTINUED") {
            // The session is not (or no longer) continued on the PC: back to view only.
            _detail.update { d -> if (d?.sessionId == sessionId) d.copy(session = d.session?.copy(remote = false)) else d }
            _messages.tryEmit("Continue this session on your PC first, then you can chat in it from here.")
            scope.launch { runCatching { api.session(sessionId) }.onSuccess { s -> _detail.update { d -> if (d?.sessionId == sessionId) d.copy(session = s) else d } } }
            return
        }
        _messages.tryEmit(commandError(prefix, e))
    }

    private fun commandError(prefix: String, e: Throwable): String = when ((e as? ApiException)?.code) {
        "DEVICE_NOT_PAIRED" -> "$prefix: this phone is not paired with that PC. Pair it again from Devices."
        "DEVICE_REVOKED" -> "$prefix: that PC was removed from your account."
        else -> "$prefix: ${e.message}"
    }

    private val _answering = MutableStateFlow<Set<String>>(emptySet())
    /** Approvals whose answer is being sent. */
    val answering: StateFlow<Set<String>> = _answering

    fun respond(approval: Approval, reply: String) = sendAnswer(approval) { api.respondApproval(approval.id, reply) }

    /** Answers a question request (one list of chosen labels / typed text per question). */
    fun answer(approval: Approval, answers: List<List<String>>) = sendAnswer(approval) { api.answerApproval(approval.id, answers) }

    private fun sendAnswer(approval: Approval, send: suspend () -> Unit) = scope.launch {
        _answering.update { it + approval.id }
        runCatching { send() }
            .onFailure { e ->
                when ((e as? ApiException)?.code) {
                    "APPROVAL_NOT_PENDING" -> _messages.tryEmit("This request was already answered.")
                    else -> _messages.tryEmit(commandError(if (approval.isQuestion) "Could not send your answer" else "Could not answer the request", e))
                }
            }
        refreshApprovals()
        _answering.update { it - approval.id }
    }

    /** Opens a project file in the read-only code viewer, read live from the PC (GET /file). */
    fun openFile(path: String) {
        val id = _detail.value?.sessionId ?: return
        fileJob?.cancel()
        _file.value = FileView(path)
        fileJob = scope.launch {
            try {
                val f = api.file(id, path)
                if (_file.value?.path == path) _file.value = FileView(f.path.ifBlank { path }, loading = false, content = f.content, size = f.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_file.value?.path == path) _file.value = FileView(path, loading = false, error = contentError(e, "Could not open this file on ${pcName(_detail.value?.session?.deviceId)}."))
            }
        }
    }

    fun closeFile() {
        fileJob?.cancel()
        _file.value = null
    }

    private fun track(cmd: PendingCommand) {
        _detail.update { d -> d?.copy(commands = (d.commands.filterNot { it.id == cmd.id } + cmd).takeLast(10)) }
    }

    // ---------------------------------------------------------------- devices

    fun pair(token: String, onDone: (Result<PairingResult>) -> Unit) = scope.launch {
        val result = runCatching { api.claimPairing(token) }
        result.onSuccess {
            store.deviceId = it.mobile.id
            _myDeviceId.value = it.mobile.id
            refreshAll()
        }
        onDone(result)
    }

    fun unlink(desktop: Device) = scope.launch {
        val me = store.deviceId ?: return@launch
        runCatching { api.unlink(desktop.id, me) }.onSuccess { refreshAll() }.onFailure { report("Could not disconnect", it) }
    }

    fun revoke(device: Device) = scope.launch {
        runCatching { api.revoke(device.id) }.onSuccess { refreshAll() }.onFailure { report("Could not revoke", it) }
    }

    fun rename(device: Device, name: String) = scope.launch {
        runCatching { api.rename(device.id, name) }.onSuccess { refreshAll() }.onFailure { report("Could not rename", it) }
    }

    /** A notification was tapped: mark it read. */
    fun markRead(notificationId: String) = scope.launch {
        runCatching { api.markRead(notificationId) }
            .onSuccess { _notifications.update { list -> list.map { if (it.id == notificationId && it.readAt == null) it.copy(readAt = java.time.Instant.now().toString()) else it } } }
        refreshOverviewSoon()
    }

    fun markAllRead() = scope.launch {
        runCatching { api.markAllRead(); _notifications.value = api.notifications() }.onFailure { report("Could not mark read", it) }
        refreshOverviewSoon()
    }

    private suspend fun refreshApprovals() {
        runCatching { _approvals.value = api.approvals() }
        _detail.value?.let { d -> _detail.update { it?.copy(approvals = _approvals.value.filter { a -> a.sessionId == d.sessionId }) } }
        // The history carries every approval of the session (answered ones too) for the timeline.
        if (_detail.value != null) loadHistory(delayMs = 1500)
    }

    // ---------------------------------------------------------------- profile

    /** Saves the nickname (already validated). [onDone] gets null on success or a readable error. */
    fun saveNickname(name: String, onDone: (String?) -> Unit = {}) = scope.launch {
        _profile.update { it.copy(savingName = true) }
        runCatching { api.updateNickname(name) }
            .onSuccess { a ->
                _profile.update { it.copy(account = mergeAccount(it.account, a), savingName = false) }
                onDone(null)
            }
            .onFailure { e ->
                _profile.update { it.copy(savingName = false) }
                onDone(
                    when ((e as? ApiException)?.status) {
                        400 -> e.message ?: "That nickname can't be used"
                        404, 405 -> "The BambooKit server doesn't support nicknames yet. Try again later."
                        else -> "Could not save the nickname: ${e.message}"
                    },
                )
            }
    }

    /** A fresh profile from the API; keeps the cached photo URL when it points at the same stored photo. */
    private fun mergeAccount(old: Account?, new: Account): Account =
        if (old?.avatarUrl != null && new.avatarUrl != null && old.avatarUrl.substringBefore('?') == new.avatarUrl.substringBefore('?')) new.copy(avatarUrl = old.avatarUrl) else new

    private val imageCache = LruCache<String, Bitmap>(8)

    fun loadProfile() = scope.launch {
        _profile.update { it.copy(loading = true) }
        runCatching { api.me() }
            .onSuccess { a -> _profile.update { it.copy(account = a, loading = false, error = null) } }
            .onFailure { e -> _profile.update { it.copy(loading = false, error = e.message) } }
    }

    /** A profile photo, downloaded once per URL (signed URLs are cached by their path, which changes with every new photo). */
    suspend fun image(url: String): Bitmap? {
        val key = url.substringBefore('?')
        imageCache.get(key)?.let { return it }
        val bytes = runCatching { api.download(url) }.getOrNull() ?: return null
        val bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } ?: return null
        imageCache.put(key, bmp)
        return bmp
    }

    /** Uploads a prepared JPEG (at most 2 MB) as the profile photo: signed upload URL, PUT, then attach. */
    fun uploadAvatar(jpeg: ByteArray) = scope.launch {
        _profile.update { it.copy(photoBusy = true) }
        runCatching {
            val slot = api.avatarUpload("image/jpeg", jpeg.size)
            api.putSigned(slot.url, slot.headers["Content-Type"] ?: "image/jpeg", jpeg, slot.headers)
            api.setAvatar(slot.key)
        }.onSuccess { res ->
            _profile.update { p -> p.copy(photoBusy = false, account = p.account?.copy(avatarUrl = res.avatarUrl, avatarStored = true)) }
            _messages.tryEmit("Profile photo updated")
        }.onFailure { e ->
            _profile.update { it.copy(photoBusy = false) }
            _messages.tryEmit(profileError("Could not update the photo", e))
        }
    }

    fun removeAvatar() = scope.launch {
        _profile.update { it.copy(photoBusy = true) }
        runCatching { api.removeAvatar(); api.me() }
            .onSuccess { a ->
                _profile.update { it.copy(photoBusy = false, account = a) }
                _messages.tryEmit("Profile photo removed")
            }
            .onFailure { e ->
                _profile.update { it.copy(photoBusy = false) }
                _messages.tryEmit(profileError("Could not remove the photo", e))
            }
    }

    /** Deletes the account on the server, then signs out on this phone. [onError] gets a readable reason. */
    fun deleteAccount(onError: (String) -> Unit) = scope.launch {
        _profile.update { it.copy(deleting = true) }
        runCatching { api.deleteAccount() }
            .onSuccess {
                signOut()
                _messages.tryEmit("Your BambooKit account was deleted")
            }
            .onFailure { e ->
                _profile.update { it.copy(deleting = false) }
                onError(
                    when ((e as? ApiException)?.code) {
                        "DELETION_NOT_CONFIGURED" -> "Account deletion isn't set up on the server yet. Nothing was deleted."
                        else -> e.message ?: "Could not delete the account"
                    },
                )
            }
    }

    private fun profileError(prefix: String, e: Throwable): String = when ((e as? ApiException)?.code) {
        "STORAGE_NOT_CONFIGURED" -> "Cloud storage isn't set up on the server yet"
        "IMAGE_TOO_LARGE" -> "Profile photos must be 2 MB or smaller"
        "UNSUPPORTED_IMAGE" -> "Profile photos must be JPEG, PNG or WebP"
        else -> "$prefix: ${e.message}"
    }

    // ---------------------------------------------------------------- realtime

    private suspend fun handle(event: RealtimeEvent) {
        try {
            when (event.type) {
                "session.updated" -> {
                    val s = json.decodeFromJsonElement<Session>(event.payload)
                    _sessions.update { list -> (listOf(s) + list.filterNot { it.id == s.id }).sortedByDescending { it.updatedAt } }
                    _detail.update { d ->
                        if (d?.sessionId != s.id) d
                        // remote = true confirms "Continue on PC".
                        else d.copy(session = s, continueRequest = d.continueRequest?.takeUnless { s.remote })
                    }
                    // Busy sessions send many updates (current action); the history is re-read at most every few seconds.
                    if (_detail.value?.sessionId == s.id) loadHistory(delayMs = 1000)
                    refreshOverviewSoon()
                }
                "session.removed" -> {
                    _sessions.update { list -> list.filterNot { it.id == event.sessionId } }
                    refreshOverviewSoon()
                }
                "session.part" -> {
                    val p = json.decodeFromJsonElement<Part>(event.payload)
                    val before = _detail.value
                    _detail.update { d ->
                        if (d?.sessionId != p.sessionId) d
                        else d.copy(parts = (d.parts.filterNot { it.id == p.id } + p).sortedBy { it.sortKey })
                    }
                    // A live part means the PC is reachable again: read the whole chat rather than show a fragment.
                    if (before?.sessionId == p.sessionId && before.contentError != null && !before.contentLoading) loadContent(p.sessionId)
                }
                "session.transcript" -> {
                    val id = event.sessionId
                    if (id != null && _detail.value?.sessionId == id) {
                        loadContent(id, partsOnly = true)
                        loadHistory(delayMs = 1500)
                        if (_detail.value?.fileMap != null) loadFileMap(delayMs = 800)
                    }
                }
                "session.diff" -> {
                    val files = json.decodeFromJsonElement<DiffPayload>(event.payload).files
                    _detail.update { d -> if (d != null && d.sessionId == event.sessionId) d.copy(changes = files) else d }
                    if (_detail.value?.sessionId == event.sessionId) loadHistory(delayMs = 1500)
                    if (_detail.value?.sessionId == event.sessionId && _detail.value?.fileMap != null) loadFileMap(delayMs = 800)
                    refreshOverviewSoon()
                }
                "approval.created", "approval.updated" -> {
                    // Apply the event right away; the full list is re-read too, as the source of truth.
                    runCatching { json.decodeFromJsonElement<Approval>(event.payload) }.getOrNull()?.let { a ->
                        _approvals.update { list ->
                            if (a.isPending) (listOf(a) + list.filterNot { it.id == a.id }).sortedByDescending { it.createdAt }
                            else list.filterNot { it.id == a.id }
                        }
                        _detail.update { d ->
                            if (d?.sessionId != a.sessionId) d
                            else d.copy(approvals = if (a.isPending) listOf(a) + d.approvals.filterNot { it.id == a.id } else d.approvals.filterNot { it.id == a.id })
                        }
                    }
                    refreshApprovals()
                    refreshOverviewSoon()
                }
                "command.updated" -> {
                    val c = json.decodeFromJsonElement<CommandUpdate>(event.payload)
                    _detail.update { d ->
                        if (d == null || d.commands.none { it.id == c.id }) d
                        else d.copy(commands = d.commands.map { if (it.id == c.id) it.copy(status = c.status, error = c.error) else it })
                    }
                    val mine = _detail.value?.commands?.any { it.id == c.id } == true
                    if (c.status == "FAILED" && mine) {
                        if (c.type == "CONTINUE_ON_PC") {
                            _detail.update { d ->
                                val req = d?.continueRequest
                                if (req == null) d
                                else d.copy(continueRequest = req.copy(error = "Your PC couldn't open the session: ${c.error ?: "failed"}. Update BambooKit Desktop if this keeps happening."))
                            }
                        } else _messages.tryEmit("Your PC could not run ${commandLabel(c.type)}: ${c.error ?: "failed"}")
                    }
                }
                "project.updated" -> {
                    val p = json.decodeFromJsonElement<Project>(event.payload)
                    _projects.update { list -> (listOf(p) + list.filterNot { it.id == p.id }) }
                }
                "device.status", "device.registered", "device.updated", "device.revoked", "device.unlinked", "pairing.completed" -> {
                    _devices.value = api.devices()
                    refreshOverviewSoon()
                    // The session's PC came back online: read the chat that could not be read before.
                    val d = _detail.value
                    if (d != null && desktopOf(d.session)?.online == true) {
                        if (d.contentError?.desktopUnavailable == true && !d.contentLoading) loadContent(d.sessionId)
                        if ((d.history.error != null || d.history.data?.live == false) && !d.history.loading) loadHistory()
                        if (d.fileMap?.error?.desktopUnavailable == true && !d.fileMap.loading) loadFileMap()
                        if (d.diagram?.error?.desktopUnavailable == true && !d.diagram.loading) loadDiagram()
                        if (d.tree?.error?.desktopUnavailable == true && !d.tree.loading) loadTree(d.tree.path)
                    }
                }
                "account.deleted" -> {
                    // The account was deleted (on another device or the website): sign out here too.
                    // Launched separately: signing out cancels the collector this handler runs in.
                    scope.launch {
                        signOut()
                        _messages.tryEmit("This BambooKit account was deleted")
                    }
                }
                "notification" -> {
                    val n = json.decodeFromJsonElement<NotificationItem>(event.payload)
                    _notifications.update { listOf(n) + it.filterNot { x -> x.id == n.id } }
                    if (notificationGate.shouldNotify(n.id, event.seq, realtime.connectionStartSeq)) notifier(n)
                    refreshOverviewSoon()
                }
                "profile.updated" -> {
                    val a = json.decodeFromJsonElement<Account>(event.payload)
                    _profile.update { it.copy(account = mergeAccount(it.account, a)) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("BambooStore", "event ${event.type} failed: ${e.message}")
        }
    }

    private var overviewJob: Job? = null
    private fun refreshOverviewSoon() {
        if (overviewJob?.isActive == true) return
        overviewJob = scope.launch {
            delay(500)
            runCatching { _overview.value = api.overview() }
        }
    }

    private fun report(prefix: String, e: Throwable) {
        val msg = "$prefix: ${e.message}"
        Log.w("BambooStore", msg)
        if (prefix == "Refresh failed") _error.value = e.message
        _messages.tryEmit(msg)
    }
}

/** Pause between history re-reads while realtime events keep arriving for the open session. */
private const val HISTORY_REFRESH_MS = 3000L

/** How long "Opening on your PC..." waits for the PC to confirm "Continue on PC". */
const val CONTINUE_TIMEOUT_MS = 30_000L

/** Re-opening the same session within this time (e.g. after rotating the phone) does not re-read it. */
private const val REOPEN_FRESH_MS = 15_000L

fun commandLabel(type: String): String = when (type) {
    "ABORT" -> "Stop"
    "CONTINUE" -> "Continue"
    "RETRY" -> "Retry"
    "SEND_MESSAGE" -> "Message"
    "CONTINUE_ON_PC" -> "Continue on PC"
    else -> type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@kotlinx.serialization.Serializable
private data class DiffPayload(val files: List<ChangedFile> = emptyList())

@kotlinx.serialization.Serializable
private data class CommandUpdate(val id: String, val type: String, val status: String, val error: String? = null)
