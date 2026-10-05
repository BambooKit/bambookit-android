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
data class ContentError(
    val code: String,
    val message: String,
    /** Everything the ⓘ sheet shows (method, path, status, request id, versions). */
    val diagnosis: Diagnosis = Diagnosis(message, code),
    /** Set when the PC lacks the feature (decided on the phone or reported by the API as 426). */
    val update: DesktopRequirement? = null,
) {
    val desktopUnavailable get() = code == "DESKTOP_OFFLINE" || code == "DESKTOP_TIMEOUT"
    val timedOut get() = code == "DESKTOP_TIMEOUT" || code == "TIMEOUT"
    val desktopOutdated get() = code == "DESKTOP_OUTDATED" || code == "DESKTOP_UPDATE_REQUIRED"
    /** The BambooKit server is older than this app and doesn't have the route. */
    val serverOutdated get() = Diagnostics.isRouteMissing(code, diagnosis.status, diagnosis.message)

    companion object {
        /** The PC lacks [r]: shown as an "Update BambooKit Desktop" card; no request was sent. */
        fun needsDesktop(r: DesktopRequirement): ContentError {
            val msg = "Update BambooKit Desktop on ${r.device} to ${r.requiredVersion} or newer. ${r.reason}".trim()
            return ContentError("DESKTOP_UPDATE_REQUIRED", msg, Diagnosis(msg, "DESKTOP_UPDATE_REQUIRED", desktop = r, desktopVersion = r.currentVersion), r)
        }
    }
}

/** Maps an API failure to a [ContentError], recognising an older PC that does not know the request kind. */
fun contentErrorOf(e: Throwable, fallback: String): ContentError {
    val api = e as? ApiException ?: return ContentError("UNKNOWN", e.message ?: fallback, Diagnosis(e.message ?: fallback))
    val msg = api.message ?: fallback
    val diag = api.diagnosis
    if (api.code == "DESKTOP_ERROR" && Regex("unsupported request|unknown (request|kind)|not supported", RegexOption.IGNORE_CASE).containsMatchIn(msg)) {
        return ContentError("DESKTOP_OUTDATED", "Update BambooKit Desktop on your PC to see this from your phone.", diag.copy(code = "DESKTOP_OUTDATED"))
    }
    api.desktopRequirement?.let { return ContentError(api.code, msg, diag, it) }
    if (api.isRouteMissing) {
        return ContentError(api.code, "The BambooKit server is older than this app and doesn't have this feature yet. The server needs to be updated.", diag)
    }
    return ContentError(api.code, msg, diag)
}

/**
 * "Continue on PC" in progress for the open session: sent at [sentAt] (elapsed realtime ms) and waiting for
 * session.updated with remote = true. [error] is set when it failed or timed out.
 */
data class ContinueRequest(val sentAt: Long, val error: String? = null)

/** A rename sent to the PC (RENAME_SESSION), shown as "Renaming on your PC..." until session.updated changes the title. */
data class RenameRequest(val sessionId: String, val oldTitle: String, val newTitle: String, val sentAt: Long)

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
    val errorDiagnosis: Diagnosis? = null,
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
    /** The send button's state: idle, sending, sent, or failed with Retry. */
    val send: SendState = SendState.Idle,
    /** The agent's todo list, live from the PC and session.todos events. */
    val todos: TodosView = TodosView(),
    /** The model picked on this phone for this session (null = the session's / PC's default). */
    val model: ModelRef? = null,
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
    val errorDiagnosis: Diagnosis? = null,
    /** The last photo upload failure (with STORAGE_NOT_CONFIGURED details when the server reported them). */
    val photoError: Diagnosis? = null,
    val photoBusy: Boolean = false,
    val deleting: Boolean = false,
    val savingName: Boolean = false,
)

/** GET /v1/me/stats: statistics, projects managed and achievements. */
data class StatsView(
    val loading: Boolean = false,
    val stats: ProfileStats? = null,
    val error: ContentError? = null,
    /** Project ids with a status change in flight. */
    val saving: Set<String> = emptySet(),
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

/** The AI providers of one PC (GET /v1/devices/:id/providers). [busy]: provider ids with a key change in flight. */
data class ProvidersView(
    val deviceId: String,
    val loading: Boolean = true,
    val info: ProvidersInfo? = null,
    val error: ContentError? = null,
    val busy: Set<String> = emptySet(),
    val loadedAt: Long = 0,
)

/** The full detail of one request (GET /v1/approvals/:id/detail). */
data class ApprovalDetailView(val loading: Boolean = true, val detail: ApprovalDetail? = null, val error: ContentError? = null)

/** "New session" from the phone: sent, waiting for the PC to create it, failed, or created (then opened). */
sealed interface NewSessionState {
    data object Sending : NewSessionState
    data class Waiting(val projectId: String, val known: Set<String>, val commandId: String, val sentAt: Long, val deviceOnline: Boolean, val model: ModelRef? = null) : NewSessionState
    data class Failed(val message: String, val diagnosis: Diagnosis = Diagnosis(message)) : NewSessionState
    data class Created(val sessionId: String) : NewSessionState
}

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
    /** Removes the BambooKit notifications posted on the phone (not the ongoing "connected" one). */
    private val cancelPostedNotifications: () -> Unit = {},
) {
    /** Recent activity cleared through a sequence: replayed events at or below it don't come back. */
    private val activityClear = ActivityClearFilter()
    private val _clearActivity = MutableStateFlow(ClearActivityView())
    /** The Clear button of Recent activity: in progress, or the last failure (shown with ⓘ). */
    val clearActivity: StateFlow<ClearActivityView> = _clearActivity
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
    private val _stats = MutableStateFlow(StatsView())
    val stats: StateFlow<StatsView> = _stats
    private val _plan = MutableStateFlow(PlanView())
    /** The account's plan (Free / Pro), today's usage and whether ads are shown (GET /v1/me/plan, plan.updated). */
    val plan: StateFlow<PlanView> = _plan
    private val _planLimit = MutableStateFlow<PlanLimitError?>(null)
    /** Set when a message or new session was refused with 402 PLAN_LIMIT: the "Daily free limit reached" dialog. */
    val planLimit: StateFlow<PlanLimitError?> = _planLimit
    private val _errorDiagnosis = MutableStateFlow<Diagnosis?>(null)
    /** Details of the last refresh failure, for the ⓘ sheet. */
    val errorDiagnosis: StateFlow<Diagnosis?> = _errorDiagnosis
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

    private val _providers = MutableStateFlow<Map<String, ProvidersView>>(emptyMap())
    /** AI providers per PC (device id). */
    val providers: StateFlow<Map<String, ProvidersView>> = _providers
    private val _approvalDetails = MutableStateFlow<Map<String, ApprovalDetailView>>(emptyMap())
    val approvalDetails: StateFlow<Map<String, ApprovalDetailView>> = _approvalDetails
    private val _webOnline = MutableStateFlow<Boolean?>(null)
    /** The website is open (presence.changed). */
    val webOnline: StateFlow<Boolean?> = _webOnline
    private val _newSession = MutableStateFlow<NewSessionState?>(null)
    val newSession: StateFlow<NewSessionState?> = _newSession
    /** Provider key commands in flight: command id to (device id, provider id, type). */
    private val providerOps = HashMap<String, Triple<String, String, String>>()
    private val sendGuard = SendGuard()

    // Realtime-driven reloads are coalesced (about 1 s) and never cancelled by newer events.
    private val historyCo = Coalescer(scope, 1000) { _detail.value?.sessionId?.let { fetchHistory(it) } }
    private val fileMapCo = Coalescer(scope, 1000) { _detail.value?.takeIf { it.fileMap != null }?.sessionId?.let { fetchFileMap(it) } }
    private val transcriptCo = Coalescer(scope, 600) { _detail.value?.sessionId?.let { fetchContent(it, partsOnly = true) } }
    private var lastRefresh = 0L

    private var started = false
    private val _file = MutableStateFlow<FileView?>(null)
    val file: StateFlow<FileView?> = _file
    private var presenceJob: Job? = null
    private var listenJobs: List<Job> = emptyList()
    private var historyJob: Job? = null
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
        scope.launch {
            // Once per app start: the phone's time zone for "this week" and night-time statistics, and the
            // server's API version and protocol. Older servers don't know either; that is fine.
            runCatching { api.setTimeZone(java.time.ZoneId.systemDefault().id) }
            runCatching { api.meta() }.onSuccess { m ->
                m.apiVersion?.let { Diagnostics.apiVersion = it }
                Diagnostics.apiProtocol = m.protocol
            }
        }
        loadProfile()
        loadPlan()
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
        activityClear.reset()
        _clearActivity.value = ClearActivityView()
        diagramCache.clear()
        _profile.value = ProfileView()
        _stats.value = StatsView()
        planJob?.cancel()
        _plan.value = PlanView()
        _planLimit.value = null
        _errorDiagnosis.value = null
        Diagnostics.clear()
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

    /**
     * The app came to the foreground: make sure realtime is running (it resumes with ?after=<seq>), and re-read
     * the authoritative state when it may be stale (after the app was in the background).
     */
    fun onForeground() {
        if (!started || auth.session.value == null) return
        if (!realtime.isRunning) realtime.start()
        if (android.os.SystemClock.elapsedRealtime() - lastRefresh > FOREGROUND_REFRESH_MS) refreshAll()
        // Pro may have been bought on the website (or expired) while the app was in the background.
        else loadPlan()
    }

    fun refreshAll() = scope.launch {
        lastRefresh = android.os.SystemClock.elapsedRealtime()
        _refreshing.value = true
        runCatching {
            _overview.value = api.overview()
            _devices.value = api.devices()
            _projects.value = api.projects()
            _sessions.value = api.sessions()
            _approvals.value = api.approvals()
            _notifications.value = api.notifications()
            // Request details of requests that are no longer pending are dropped (re-read when shown again).
            _approvalDetails.update { m -> m.filterKeys { id -> _approvals.value.any { it.id == id } } }
            _error.value = null
            _errorDiagnosis.value = null
            _loaded.value = true
        }.onFailure {
            _errorDiagnosis.value = it.diagnosis()
            report("Refresh failed", it)
        }
        _refreshing.value = false
        _detail.value?.let { loadSession(it.sessionId, keep = true) }
        // Providers shown on screen are re-read too (a reconnect may follow a PC update).
        _providers.value.keys.forEach { loadProviders(it, force = true) }
        if (_profile.value.account == null) loadProfile()
        loadPlan()
    }

    /** Restarts the live connection (refreshing the sign-in) and re-reads everything. */
    fun reconnect() {
        if (auth.session.value == null) return
        scope.launch {
            runCatching { auth.accessToken(forceRefresh = true) }
            realtime.stop()
            realtime.start()
            refreshAll()
        }
    }

    /** What [deviceId]'s PC lacks for [feature], or null when it has it (or isn't known yet). */
    fun desktopMissing(deviceId: String?, feature: DesktopFeature): DesktopRequirement? =
        DesktopCapabilities.missing(_devices.value.firstOrNull { it.id == deviceId }, feature)

    /** The PC (desktop device) a session lives on, from the devices list. */
    fun desktopOf(session: Session?): Device? = session?.let { s -> _devices.value.firstOrNull { it.id == s.deviceId } }

    private fun pcName(deviceId: String?): String = _devices.value.firstOrNull { it.id == deviceId }?.name ?: "Your PC"

    // ---------------------------------------------------------------- session detail

    fun openSession(id: String) {
        val d = _detail.value
        // Re-opening right after a load (e.g. the phone was rotated) keeps what is shown.
        if (d?.sessionId == id && d.loadedAt > 0 && android.os.SystemClock.elapsedRealtime() - d.loadedAt < REOPEN_FRESH_MS) return
        if (d?.sessionId != id) _detail.value = SessionDetail(sessionId = id, model = store.sessionModel(id))
        loadSession(id, keep = true)
    }

    fun closeSession() {
        historyCo.cancel()
        fileMapCo.cancel()
        transcriptCo.cancel()
        contentJob?.cancel()
        fileMapJob?.cancel()
        diagramJob?.cancel()
        treeJob?.cancel()
        fileJob?.cancel()
        historyJob?.cancel()
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
        if (delayMs > 0) {
            if (_detail.value?.fileMap != null) fileMapCo.request()
            return
        }
        fileMapJob?.cancel()
        _detail.update { d -> if (d?.sessionId == id) d.copy(fileMap = (d.fileMap ?: FileMapView()).copy(loading = true)) else d }
        fileMapJob = scope.launch { fetchFileMap(id) }
    }

    private suspend fun fetchFileMap(id: String) {
        _detail.update { d -> if (d?.sessionId == id) d.copy(fileMap = (d.fileMap ?: FileMapView()).copy(loading = true)) else d }
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
        if (delayMs > 0) {
            historyCo.request()
            return
        }
        historyJob?.cancel()
        _detail.update { d -> if (d?.sessionId == id) d.copy(history = d.history.copy(loading = true)) else d }
        historyJob = scope.launch { fetchHistory(id) }
    }

    /** Reads the open session's todo list from the PC (on open, reconnect and when the PC comes online). */
    fun loadTodos() {
        val id = _detail.value?.sessionId ?: return
        scope.launch { fetchTodos(id) }
    }

    private suspend fun fetchTodos(id: String) {
        val startedAt = _detail.value?.takeIf { it.sessionId == id }?.todos?.version ?: return
        desktopMissing(_detail.value?.session?.deviceId, DesktopFeature.Todos)?.let { r ->
            _detail.update { d -> if (d?.sessionId == id) d.copy(todos = TodoReducer.failed(TodoReducer.loading(d.todos), ContentError.needsDesktop(r))) else d }
            return
        }
        _detail.update { d -> if (d?.sessionId == id) d.copy(todos = TodoReducer.loading(d.todos)) else d }
        try {
            val list = api.todos(id).todos
            _detail.update { d -> if (d?.sessionId == id) d.copy(todos = TodoReducer.fetched(d.todos, list, startedAt)) else d }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val err = contentError(e, "Could not read the todo list from ${pcName(_detail.value?.session?.deviceId)}.")
            _detail.update { d -> if (d?.sessionId == id) d.copy(todos = TodoReducer.failed(d.todos, err)) else d }
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
        loadTodos()
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
        }.onFailure { e -> _detail.update { d -> if (d?.sessionId == id) d.copy(loading = false, error = e.message, errorDiagnosis = e.diagnosis()) else d } }
        loadContent(id)
    }

    /**
     * Chat parts and changed files are read live from the PC through the API relay. When the PC is
     * offline (503 DESKTOP_OFFLINE / DESKTOP_TIMEOUT) nothing is shown in their place — the screen
     * explains why and offers a retry. Content already read from the PC earlier is kept.
     */
    private fun loadContent(id: String, partsOnly: Boolean = false) {
        contentJob?.cancel()
        contentJob = scope.launch { fetchContent(id, partsOnly) }
    }

    private suspend fun fetchContent(id: String, partsOnly: Boolean) {
        _detail.update { d -> if (d?.sessionId == id) d.copy(contentLoading = true) else d }
        try {
            val (parts, changes) = coroutineScope {
                val p = async { api.parts(id) }
                val c = async { if (partsOnly) null else api.changes(id) }
                p.await() to c.await()
            }
            _detail.update { d ->
                if (d?.sessionId != id) d
                else d.copy(parts = mergeTranscript(d.parts, parts), changes = changes ?: d.changes, contentLoading = false, contentLoaded = true, contentError = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val err = contentError(e, "Could not read this session from ${pcName(_detail.value?.session?.deviceId)}.")
            _detail.update { d -> if (d?.sessionId == id) d.copy(contentLoading = false, contentError = err) else d }
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

    /**
     * Sends a chat message to the agent (with the model picked for this session, if any). Duplicate sends are
     * refused: one at a time, and the same text not again within a moment. [onResult] gets true when the API
     * accepted it (the box is then cleared).
     */
    fun sendMessage(text: String, onResult: (Boolean) -> Unit) {
        val d = _detail.value ?: return onResult(false)
        val body = text.trim()
        if (body.isEmpty()) return onResult(false)
        if (!sendGuard.tryBegin(d.sessionId + "\u0000" + body)) return onResult(false)
        _detail.update { if (it?.sessionId == d.sessionId) it.copy(sending = true, send = SendState.Sending) else it }
        scope.launch {
            val model = _detail.value?.takeIf { it.sessionId == d.sessionId }?.model
            runCatching { api.sendCommand(d.sessionId, "SEND_MESSAGE", ApiClient.messagePayload(body, model)) }
                .onSuccess { res ->
                    sendGuard.finish(true)
                    countUsage(sessions = false)
                    track(PendingCommand(res.data.id, "SEND_MESSAGE", res.data.status, deviceOnline = res.deviceOnline))
                    val sent = SendState.Sent(android.os.SystemClock.elapsedRealtime(), res.deviceOnline)
                    _detail.update { if (it?.sessionId == d.sessionId) it.copy(sending = false, send = sent) else it }
                    if (!res.deviceOnline) _messages.tryEmit("${pcName(d.session?.deviceId)} is offline. The message is sent if it reconnects within 5 minutes.")
                    onResult(true)
                    delay(SENT_SHOWN_MS)
                    _detail.update { if (it?.send == sent) it.copy(send = SendState.Idle) else it }
                }
                .onFailure { e ->
                    sendGuard.finish(false)
                    PlanLimitError.from(e)?.let { limit ->
                        // The typed message stays in the box (and in Failed) so it can be sent later.
                        _planLimit.value = limit
                        _detail.update { if (it?.sessionId == d.sessionId) it.copy(sending = false, send = SendState.Failed("Daily free limit reached. Your message was not sent.", body)) else it }
                        loadPlan()
                        onResult(false)
                        return@onFailure
                    }
                    val msg = if ((e as? ApiException)?.code == "SESSION_NOT_CONTINUED") "Continue this session on your PC first." else commandError("Not sent", e)
                    _detail.update { if (it?.sessionId == d.sessionId) it.copy(sending = false, send = SendState.Failed(msg, body)) else it }
                    handleCommandFailure(d.sessionId, "Could not send the message", e)
                    onResult(false)
                }
        }
    }

    fun clearSendError() {
        _detail.update { if (it?.send is SendState.Failed) it.copy(send = SendState.Idle) else it }
    }

    /** Picks the model for the open session; remembered on this phone for that session. */
    fun selectModel(model: ModelRef?) {
        val id = _detail.value?.sessionId ?: return
        store.setSessionModel(id, model)
        _detail.update { if (it?.sessionId == id) it.copy(model = model) else it }
    }

    // ---------------------------------------------------------------- new session, providers, request details

    /**
     * Starts a new session in [project] on its PC. The PC creates it and reports it with session.updated;
     * the first session of that project that was not known before is the new one (its server id is used).
     */
    fun startSession(project: Project, text: String, model: ModelRef?) {
        val body = text.trim()
        val cur = _newSession.value
        if (body.isEmpty() || cur == NewSessionState.Sending || cur is NewSessionState.Waiting) return
        desktopMissing(project.deviceId, DesktopFeature.CreateSession)?.let { r ->
            _newSession.value = ContentError.needsDesktop(r).let { NewSessionState.Failed(it.message, it.diagnosis) }
            return
        }
        _newSession.value = NewSessionState.Sending
        val known = _sessions.value.filter { it.projectId == project.id }.map { it.id }.toSet()
        scope.launch {
            runCatching { api.createSession(project.id, body, model) }
                .onSuccess { res ->
                    countUsage(sessions = true)
                    val sentAt = android.os.SystemClock.elapsedRealtime()
                    _newSession.value = NewSessionState.Waiting(project.id, known, res.data.id, sentAt, res.deviceOnline, model)
                    if (!res.deviceOnline) _messages.tryEmit("${pcName(project.deviceId)} is offline. The session starts if it reconnects within 5 minutes.")
                    delay(NEW_SESSION_TIMEOUT_MS)
                    val now = _newSession.value
                    if (now is NewSessionState.Waiting && now.sentAt == sentAt) {
                        // Re-read once in case the event was missed.
                        val fresh = runCatching { api.sessions(project.id) }.getOrNull().orEmpty().firstOrNull { it.id !in known }
                        _newSession.value = if (fresh != null) created(now, fresh.id)
                        else NewSessionState.Failed("${pcName(project.deviceId)} didn't start the session within a minute. Make sure BambooKit Desktop is open and up to date, then try again.")
                    }
                }
                .onFailure {
                    val limit = PlanLimitError.from(it)
                    if (limit != null) {
                        _planLimit.value = limit
                        _newSession.value = NewSessionState.Failed("Daily free limit of new sessions reached.", it.diagnosis())
                        loadPlan()
                    } else _newSession.value = NewSessionState.Failed(commandError("Could not start the session", it), it.diagnosis())
                }
        }
    }

    /** The new session arrived: the model picked for it is remembered for that session. */
    private fun created(w: NewSessionState.Waiting, id: String): NewSessionState {
        w.model?.let { store.setSessionModel(id, it) }
        return NewSessionState.Created(id)
    }

    fun clearNewSession() {
        _newSession.value = null
    }

    /** Reads a PC's providers and models. [force] re-reads even when a recent list is shown. */
    fun loadProviders(deviceId: String, force: Boolean = false) {
        val cur = _providers.value[deviceId]
        if (!force && cur != null && (cur.loading || (cur.info != null && android.os.SystemClock.elapsedRealtime() - cur.loadedAt < 60_000))) return
        desktopMissing(deviceId, DesktopFeature.Providers)?.let { r ->
            _providers.update { it + (deviceId to (cur ?: ProvidersView(deviceId)).copy(loading = false, info = null, error = ContentError.needsDesktop(r))) }
            return
        }
        _providers.update { it + (deviceId to (cur ?: ProvidersView(deviceId)).copy(loading = true)) }
        scope.launch {
            try {
                val info = api.providers(deviceId)
                _providers.update { m -> m + (deviceId to (m[deviceId] ?: ProvidersView(deviceId)).copy(loading = false, info = info, error = null, loadedAt = android.os.SystemClock.elapsedRealtime())) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val err = contentError(e, "Could not read the AI providers from ${pcName(deviceId)}.")
                _providers.update { m -> m + (deviceId to (m[deviceId] ?: ProvidersView(deviceId)).copy(loading = false, error = err)) }
            }
        }
    }

    private fun providerBusy(deviceId: String, providerId: String, busy: Boolean) {
        _providers.update { m ->
            val v = m[deviceId] ?: ProvidersView(deviceId, loading = false)
            m + (deviceId to v.copy(busy = if (busy) v.busy + providerId else v.busy - providerId))
        }
    }

    /**
     * Encrypts [apiKey] for the PC (RSA-OAEP-256 + AES-256-GCM) and sends it. The characters are wiped here
     * whatever happens; the key is never logged or stored on the phone. [onDone] gets null when BambooKit
     * accepted the command, else a readable reason.
     */
    fun setProviderKey(pc: Device, providerId: String, apiKey: CharArray, onDone: (String?) -> Unit) {
        val pem = pc.encryptionKey
        if (pem.isNullOrBlank() || DesktopCapabilities.missing(pc, DesktopFeature.ProviderKeys) != null) {
            apiKey.fill('\u0000')
            onDone("Update BambooKit Desktop on ${pc.name} to set provider keys from your phone.")
            return
        }
        providerBusy(pc.id, providerId, true)
        scope.launch {
            val envelope = try {
                withContext(Dispatchers.Default) { KeyEnvelopes.seal(apiKey, pem) }
            } catch (e: Exception) {
                null
            } finally {
                apiKey.fill('\u0000')
            }
            if (envelope == null) {
                providerBusy(pc.id, providerId, false)
                onDone("Could not encrypt the key for ${pc.name}. Refresh the PC's details and try again.")
                return@launch
            }
            val payload = buildJsonObject {
                put("providerID", providerId)
                put("envelope", buildJsonObject { put("alg", envelope.alg); put("key", envelope.key); put("iv", envelope.iv); put("data", envelope.data) })
            }
            runCatching { api.deviceCommand(pc.id, "SET_PROVIDER_KEY", payload) }
                .onSuccess { res ->
                    providerOps[res.data.id] = Triple(pc.id, providerId, "SET_PROVIDER_KEY")
                    if (!res.deviceOnline) _messages.tryEmit("${pc.name} is offline. The key is saved there if it reconnects within 5 minutes.")
                    onDone(null)
                }
                .onFailure { e ->
                    providerBusy(pc.id, providerId, false)
                    onDone(
                        when ((e as? ApiException)?.code) {
                            "ENCRYPTION_KEY_MISSING" -> "Update BambooKit Desktop on ${pc.name} to set provider keys from your phone."
                            else -> commandError("Could not send the key", e)
                        },
                    )
                }
        }
    }

    fun removeProviderKey(pc: Device, providerId: String) {
        providerBusy(pc.id, providerId, true)
        scope.launch {
            runCatching { api.deviceCommand(pc.id, "REMOVE_PROVIDER_KEY", buildJsonObject { put("providerID", providerId) }) }
                .onSuccess { res ->
                    providerOps[res.data.id] = Triple(pc.id, providerId, "REMOVE_PROVIDER_KEY")
                    if (!res.deviceOnline) _messages.tryEmit("${pc.name} is offline. The key is removed if it reconnects within 5 minutes.")
                }
                .onFailure { e ->
                    providerBusy(pc.id, providerId, false)
                    _messages.tryEmit(commandError("Could not remove the key", e))
                }
        }
    }

    /** Reads (or re-reads) a request's full detail from the PC. */
    fun loadApprovalDetail(id: String, force: Boolean = false) {
        val cur = _approvalDetails.value[id]
        if (!force && cur != null && (cur.loading || cur.detail != null)) return
        desktopMissing(_approvals.value.firstOrNull { it.id == id }?.deviceId, DesktopFeature.Approval)?.let { r ->
            _approvalDetails.update { it + (id to ApprovalDetailView(loading = false, error = ContentError.needsDesktop(r))) }
            return
        }
        _approvalDetails.update { it + (id to (cur ?: ApprovalDetailView()).copy(loading = true)) }
        scope.launch {
            try {
                val detail = api.approvalDetail(id)
                _approvalDetails.update { it + (id to ApprovalDetailView(loading = false, detail = detail)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val err = contentError(e, "Could not read this request from your PC.")
                _approvalDetails.update { it + (id to (it[id] ?: ApprovalDetailView()).copy(loading = false, error = err)) }
            }
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
        "DESKTOP_UPDATE_REQUIRED" -> (e as ApiException).desktopRequirement?.let { ContentError.needsDesktop(it).message } ?: "$prefix: ${e.message}"
        "ROUTE_NOT_FOUND", "NOT_FOUND" -> if ((e as ApiException).isRouteMissing) "$prefix: the BambooKit server is older than this app and doesn't support this yet. The server needs to be updated." else "$prefix: ${e.message}"
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

    // ---------------------------------------------------------------- like and rename

    private val _renames = MutableStateFlow<Map<String, RenameRequest>>(emptyMap())
    /** Renames waiting for the PC, by session id. */
    val renames: StateFlow<Map<String, RenameRequest>> = _renames

    /** Applies [f] to the session wherever it is shown (lists, overview, open session). */
    private fun updateSessionEverywhere(id: String, f: (Session) -> Session) {
        _sessions.update { list -> list.map { if (it.id == id) f(it) else it } }
        _overview.update { o -> o?.copy(activeSessions = o.activeSessions.map { if (it.id == id) f(it) else it }) }
        _detail.update { d -> val cur = d?.session; if (cur != null && cur.id == id) d.copy(session = f(cur)) else d }
    }

    /** Like / unlike: shown at once, rolled back if the API refuses. */
    fun toggleStar(session: Session) {
        val target = !session.starred
        updateSessionEverywhere(session.id) { it.copy(starred = target) }
        scope.launch {
            runCatching { api.setStarred(session.id, target) }
                .onSuccess { s -> updateSessionEverywhere(s.id) { it.copy(starred = s.starred) } }
                .onFailure { e ->
                    updateSessionEverywhere(session.id) { it.copy(starred = !target) }
                    _messages.tryEmit("Could not ${if (target) "like" else "unlike"} the session: ${e.message}")
                }
        }
    }

    /** Asks the session's PC to rename it; the new title arrives with session.updated. [title] is already validated. */
    fun renameSession(session: Session, title: String) {
        if (title == session.title) return
        val sentAt = android.os.SystemClock.elapsedRealtime()
        scope.launch {
            runCatching { api.sendCommand(session.id, "RENAME_SESSION", buildJsonObject { put("title", title) }) }
                .onSuccess {
                    _renames.update { it + (session.id to RenameRequest(session.id, session.title, title, sentAt)) }
                    delay(RENAME_TIMEOUT_MS)
                    if (_renames.value[session.id]?.sentAt == sentAt) {
                        _renames.update { it - session.id }
                        _messages.tryEmit("${pcName(session.deviceId)} didn't rename the session. Make sure BambooKit Desktop is open and up to date, then try again.")
                    }
                }
                .onFailure { _messages.tryEmit(commandError("Could not rename", it)) }
        }
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

    /** Clears recent activity and notifications for this account (all devices), then empties the list here. */
    fun clearRecentActivity() = scope.launch {
        if (_clearActivity.value.clearing) return@launch
        _clearActivity.value = ClearActivityView(clearing = true)
        runCatching { api.clearActivity() }
            .onSuccess { r ->
                applyActivityCleared(r.clearedThroughSeq)
                _clearActivity.value = ClearActivityView()
            }
            .onFailure { e -> _clearActivity.value = ClearActivityView(error = "Couldn't clear recent activity: ${e.message}", errorDiagnosis = e.diagnosis()) }
    }

    fun dismissClearActivityError() {
        _clearActivity.value = ClearActivityView()
    }

    /** Recent activity was cleared (here or on another device): empty the list, the unread count and posted notifications. */
    private fun applyActivityCleared(throughSeq: Long) {
        activityClear.cleared(throughSeq)
        // The server deleted every notification of the account.
        _notifications.value = emptyList()
        _overview.update { it?.copy(unreadNotifications = 0) }
        cancelPostedNotifications()
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
            .onSuccess { a -> _profile.update { it.copy(account = mergeAccount(it.account, a), loading = false, error = null, errorDiagnosis = null) } }
            .onFailure { e -> _profile.update { it.copy(loading = false, error = e.message, errorDiagnosis = e.diagnosis()) } }
    }

    private var planJob: Job? = null

    /** Reads the plan (GET /v1/me/plan); the website's Pro products are read once alongside it. */
    fun loadPlan() {
        if (auth.session.value == null || planJob?.isActive == true) return
        planJob = scope.launch {
            _plan.update { it.copy(loading = true) }
            try {
                val p = api.plan()
                _plan.update { it.copy(loading = false, plan = p, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _plan.update { it.copy(loading = false, error = contentErrorOf(e, "Could not load your plan.")) }
            }
            if (_plan.value.products.isEmpty()) runCatching { api.billingPlans() }.onSuccess { b -> _plan.update { it.copy(products = b.products) } }
        }
    }

    /**
     * A rewarded ad was watched. The server grants Pro through AdMob's server-side verification and then sends
     * plan.updated; that can lag, so the plan is also re-read after about 3 s and a couple more times until it changes.
     */
    fun refreshPlanAfterReward() {
        val before = _plan.value.plan
        scope.launch {
            for (wait in REWARD_REFETCH_MS) {
                delay(wait)
                val p = runCatching { api.plan() }.getOrNull() ?: continue
                _plan.update { it.copy(plan = p, error = null) }
                if (p.isPro || p.rewards.todayCount > (before?.rewards?.todayCount ?: 0)) {
                    _messages.tryEmit("Pro is on for ${p.rewards.hours} hours. Thanks for watching!")
                    return@launch
                }
            }
        }
    }

    /** One-time token for a rewarded ad's server-side verification (POST /v1/rewards/token). */
    suspend fun rewardToken(): RewardToken = api.rewardToken()

    private var reconcileJob: Job? = null

    /**
     * A message or new session was accepted: today's usage goes up at once (so the remaining count is right
     * without waiting), then the plan is re-read a moment later to reconcile with the server's count.
     */
    private fun countUsage(sessions: Boolean) {
        _plan.update { v -> v.plan?.let { p -> v.copy(plan = if (sessions) p.withSessionCreated() else p.withMessageSent()) } ?: v }
        reconcileJob?.cancel()
        reconcileJob = scope.launch {
            delay(PLAN_RECONCILE_MS)
            loadPlan()
        }
    }

    /** A locked button (daily limit used up) was tapped: the "Daily free limit reached" dialog, without an API call. */
    fun showLocalPlanLimit(sessions: Boolean) {
        val p = _plan.value.plan ?: return
        _planLimit.value = p.localLimit(sessions)
    }

    fun dismissPlanLimit() {
        _planLimit.value = null
    }

    /** Statistics, projects managed and achievements (GET /v1/me/stats). */
    fun loadStats() = scope.launch {
        _stats.update { it.copy(loading = true) }
        try {
            val s = api.stats()
            _stats.update { it.copy(loading = false, stats = s, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _stats.update { it.copy(loading = false, error = contentErrorOf(e, "Could not load your statistics.")) }
        }
    }

    /** Marks a project active, completed or archived (PATCH /v1/projects/:id), then re-reads the statistics. */
    fun setProjectStatus(projectId: String, status: String) = scope.launch {
        _stats.update { it.copy(saving = it.saving + projectId) }
        runCatching { api.setProjectStatus(projectId, status) }
            .onSuccess { r ->
                _stats.update { v -> v.copy(stats = v.stats?.withProjectStatus(r.id, r.status)) }
                _projects.update { list -> list.map { if (it.id == r.id) it.copy(status = r.status) else it } }
                loadStats()
            }
            .onFailure { e -> _messages.tryEmit(commandError("Could not change the project status", e)) }
        _stats.update { it.copy(saving = it.saving - projectId) }
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

    /**
     * Uploads a prepared JPEG (at most 2 MB) as the profile photo: signed upload URL, PUT, then attach.
     * [jpeg] is final before the URL is requested: the URL is signed for exactly its size.
     */
    fun uploadAvatar(jpeg: ByteArray) = scope.launch {
        val bytes = jpeg.copyOf()
        _profile.update { it.copy(photoBusy = true, photoError = null) }
        runCatching {
            val slot = api.avatarUpload("image/jpeg", bytes.size)
            api.putSigned(slot.url, slot.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value ?: "image/jpeg", bytes, slot.headers)
            api.setAvatar(slot.key)
        }.onSuccess { res ->
            // The photo now comes from the server everywhere (top bar, profile) and after signing in again.
            _profile.update { p -> p.copy(photoBusy = false, photoError = null, account = res.profile ?: p.account?.copy(avatarUrl = res.avatarUrl, avatarStored = true)) }
            _messages.tryEmit("Profile photo updated")
        }.onFailure { e ->
            _profile.update { it.copy(photoBusy = false, photoError = e.diagnosis()) }
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
        "STORAGE_NOT_CONFIGURED" -> "Cloud storage isn't set up on the BambooKit server yet, so photos can't be saved. Tap ⓘ for details."
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
                    _renames.value[s.id]?.let { r -> if (s.title != r.oldTitle) _renames.update { it - s.id } }
                    _sessions.update { list -> (listOf(s) + list.filterNot { it.id == s.id }).sortedByDescending { it.updatedAt } }
                    _detail.update { d ->
                        if (d?.sessionId != s.id) d
                        // remote = true confirms "Continue on PC".
                        else d.copy(session = s, continueRequest = d.continueRequest?.takeUnless { s.remote })
                    }
                    // Busy sessions send many updates (current action); the history is re-read at most about once a second.
                    if (_detail.value?.sessionId == s.id) loadHistory(delayMs = 1000)
                    // A session started from this phone: open it once the PC reports it.
                    (_newSession.value as? NewSessionState.Waiting)?.let { w ->
                        if (s.projectId == w.projectId && s.id !in w.known) _newSession.value = created(w, s.id)
                    }
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
                        else {
                            val old = d.parts.firstOrNull { it.id == p.id }
                            d.copy(parts = (d.parts.filterNot { it.id == p.id } + (old?.mergeLive(p) ?: p)).sortedBy { it.sortKey })
                        }
                    }
                    // A live part means the PC is reachable again: read the whole chat rather than show a fragment.
                    if (before?.sessionId == p.sessionId && before.contentError != null && !before.contentLoading) loadContent(p.sessionId)
                }
                "session.transcript" -> {
                    val id = event.sessionId
                    if (id != null && _detail.value?.sessionId == id) {
                        transcriptCo.request()
                        loadHistory(delayMs = 1000)
                        loadFileMap(delayMs = 1000)
                    }
                }
                "session.diff" -> {
                    val files = json.decodeFromJsonElement<DiffPayload>(event.payload).files
                    _detail.update { d -> if (d != null && d.sessionId == event.sessionId) d.copy(changes = files) else d }
                    if (_detail.value?.sessionId == event.sessionId) {
                        loadHistory(delayMs = 1000)
                        loadFileMap(delayMs = 1000)
                    }
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
                    scope.launch { refreshApprovals() }
                    refreshOverviewSoon()
                }
                "session.todos" -> {
                    val t = json.decodeFromJsonElement<TodosEvent>(event.payload).let { if (it.sessionId.isBlank()) it.copy(sessionId = event.sessionId.orEmpty()) else it }
                    _detail.update { d -> if (d == null) d else d.copy(todos = TodoReducer.live(d.todos, d.sessionId, t)) }
                }
                "presence.changed" -> {
                    val o = event.payload
                    if (o.str("kind") == "web") _webOnline.value = o.bool("online")
                }
                "command.updated" -> {
                    val c = json.decodeFromJsonElement<CommandUpdate>(event.payload)
                    _detail.update { d ->
                        if (d == null || d.commands.none { it.id == c.id }) d
                        else d.copy(commands = d.commands.map { if (it.id == c.id) it.copy(status = c.status, error = c.error) else it })
                    }
                    providerOps[c.id]?.let { (deviceId, providerId, type) ->
                        if (c.status != "PENDING") {
                            providerOps.remove(c.id)
                            providerBusy(deviceId, providerId, false)
                            if (c.status == "SUCCEEDED") {
                                _messages.tryEmit(if (type == "SET_PROVIDER_KEY") "Key saved on ${pcName(deviceId)}" else "Key removed from ${pcName(deviceId)}")
                                loadProviders(deviceId, force = true)
                            } else _messages.tryEmit("${pcName(deviceId)} could not ${if (type == "SET_PROVIDER_KEY") "save" else "remove"} the key: ${c.error ?: "failed"}")
                        }
                    }
                    (_newSession.value as? NewSessionState.Waiting)?.let { w ->
                        if (c.id == w.commandId && c.status == "FAILED") _newSession.value = NewSessionState.Failed("Your PC could not start the session: ${c.error ?: "failed"}")
                    }
                    if (c.type == "RENAME_SESSION" && c.status == "FAILED") {
                        event.sessionId?.let { id -> _renames.update { it - id } }
                        _messages.tryEmit("Your PC could not rename the session: ${c.error ?: "failed"}")
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
                    val p = runCatching { json.decodeFromJsonElement<Project>(event.payload) }.getOrNull()
                    if (p != null) _projects.update { list -> (listOf(p) + list.filterNot { it.id == p.id }) }
                    else {
                        // A status change ({ id, status }) from PATCH /v1/projects/:id on this or another device.
                        val o = event.payload
                        val id = o.str("id")
                        val status = o.str("status")
                        if (id != null && status != null) {
                            _projects.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
                            _stats.update { v -> v.copy(stats = v.stats?.withProjectStatus(id, status)) }
                        }
                    }
                }
                "achievement.unlocked" -> {
                    val a = runCatching { json.decodeFromJsonElement<AchievementEvent>(event.payload) }.getOrNull()
                    a?.title?.takeIf { it.isNotBlank() }?.let { _messages.tryEmit("Achievement unlocked: $it") }
                    if (_stats.value.stats != null || _stats.value.loading) loadStats()
                }
                "device.status", "device.registered", "device.updated", "device.revoked", "device.unlinked", "pairing.completed" -> scope.launch {
                    // Off the event collector: a slow request must never hold up (and drop) later events.
                    val wasOnline = _devices.value.associate { it.id to it.online }
                    runCatching { _devices.value = api.devices() }
                    refreshOverviewSoon()
                    // A PC came online: its providers can be read again.
                    _devices.value.filter { it.online && wasOnline[it.id] != true && _providers.value[it.id] != null }.forEach { loadProviders(it.id, force = true) }
                    // The session's PC came back online: read the chat that could not be read before.
                    val d = _detail.value
                    if (d != null && desktopOf(d.session)?.online == true) {
                        if (d.contentError?.desktopUnavailable == true && !d.contentLoading) loadContent(d.sessionId)
                        if (!d.todos.loading && (d.todos.error != null || wasOnline[d.session?.deviceId] != true)) loadTodos()
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
                "plan.updated" -> {
                    val p = json.decodeFromJsonElement<Plan>(event.payload)
                    _plan.update { it.copy(plan = p, loading = false, error = null) }
                    // Pro arrived (bought or rewarded): the limit dialog is no longer needed.
                    if (p.isPro) _planLimit.value = null
                }
                "activity.cleared" -> {
                    val r = json.decodeFromJsonElement<ActivityCleared>(event.payload)
                    applyActivityCleared(r.clearedThroughSeq)
                    _clearActivity.value = ClearActivityView()
                }
                "notification" -> {
                    // Cleared activity stays cleared when stored events are replayed after a reconnect.
                    if (!activityClear.accepts(event.seq)) return
                    val n = json.decodeFromJsonElement<NotificationItem>(event.payload)
                    _notifications.update { listOf(n) + it.filterNot { x -> x.id == n.id } }
                    if (notificationGate.shouldNotify(n.id, event.seq, realtime.connectionStartSeq)) notifier(n)
                    refreshOverviewSoon()
                }
                "profile.updated" -> {
                    val a = json.decodeFromJsonElement<Account>(event.payload)
                    _profile.update { it.copy(account = mergeAccount(it.account, a)) }
                    if (_stats.value.stats != null) loadStats()
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

    /** A short message for the snackbar. */
    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    private fun report(prefix: String, e: Throwable) {
        val msg = "$prefix: ${e.message}"
        Log.w("BambooStore", msg)
        if (prefix == "Refresh failed") _error.value = e.message
        _messages.tryEmit(msg)
    }
}

/** State of the Recent activity Clear button. */
data class ClearActivityView(
    val clearing: Boolean = false,
    val error: String? = null,
    val errorDiagnosis: Diagnosis? = null,
)

/** The plan is re-read this long after a send / new session (optimistic usage is reconciled with the server). */
private const val PLAN_RECONCILE_MS = 4_000L

/** Re-reads of the plan after a rewarded ad (server-side verification can lag). */
private val REWARD_REFETCH_MS = listOf(3_000L, 5_000L, 10_000L)

/** How long "Sent" stays on the send button. */
private const val SENT_SHOWN_MS = 2000L

/** How long "Starting on your PC..." waits for a new session. */
const val NEW_SESSION_TIMEOUT_MS = 60_000L

/** Coming back to the app after this long re-reads everything. */
private const val FOREGROUND_REFRESH_MS = 20_000L

/**
 * A transcript read merged with what is shown: the read is authoritative for every part it contains, but live
 * parts newer than it (updatedAt) keep their state, and live parts after the end of the read stay.
 */
internal fun mergeTranscript(shown: List<Part>, fetched: List<Part>): List<Part> {
    val byId = shown.associateBy { it.id }
    val fetchedIds = fetched.map { it.id }.toSet()
    val lastKey = fetched.maxOfOrNull { it.sortKey } ?: ""
    val merged = fetched.map { p -> byId[p.id]?.takeIf { (it.updatedAt ?: "") > (p.updatedAt ?: "") }?.let { p.mergeLive(it) } ?: p } +
        shown.filter { it.id !in fetchedIds && it.sortKey > lastKey }
    return merged.sortedBy { it.sortKey }
}

/** How long "Renaming on your PC..." waits for the new title. */
const val RENAME_TIMEOUT_MS = 30_000L

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
    "RENAME_SESSION" -> "Rename"
    "CREATE_SESSION" -> "New session"
    else -> type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@kotlinx.serialization.Serializable
private data class DiffPayload(val files: List<ChangedFile> = emptyList())

@kotlinx.serialization.Serializable
private data class CommandUpdate(val id: String, val type: String, val status: String, val error: String? = null)

/** The statistics with one project's status changed (counts follow). */
fun ProfileStats.withProjectStatus(id: String, status: String): ProfileStats {
    if (projects.list.none { it.id == id }) return this
    val list = projects.list.map { if (it.id == id) it.copy(status = status) else it }
    return copy(
        projects = projects.copy(
            list = list,
            active = list.count { it.status == "active" },
            completed = list.count { it.status == "completed" },
            archived = list.count { it.status == "archived" },
        ),
    )
}
