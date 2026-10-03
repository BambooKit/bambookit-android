package com.bambookit.android.data

import android.util.Log
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

/** A pending remote action shown in the UI until the desktop reports its result. */
data class PendingCommand(val id: String, val type: String, val status: String, val error: String? = null, val deviceOnline: Boolean = true)

/** Why the chat / changed files could not be read from the PC (e.g. DESKTOP_OFFLINE, DESKTOP_TIMEOUT). */
data class ContentError(val code: String, val message: String) {
    val desktopUnavailable get() = code == "DESKTOP_OFFLINE" || code == "DESKTOP_TIMEOUT"
}

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
) {
    /** The phone may chat, continue, retry, rewind and edit files only in sessions continued on the PC. */
    val canChat get() = session?.remote == true
}

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
)

data class DiffView(val file: String, val loading: Boolean = true, val result: DiffFile? = null, val error: String? = null)

/** A file opened from the phone, read from (and saved back to) the PC through the desktop. */
data class FileView(
    val path: String,
    val loading: Boolean = true,
    val content: String? = null,
    val sha256: String? = null,
    val saving: Boolean = false,
    val savedAt: Long? = null,
    val error: String? = null,
)

data class ShareView(val loading: Boolean = true, val url: String? = null, val error: String? = null)

/** Commands that continue or change the conversation; the API answers 409 SESSION_NOT_CONTINUED otherwise. */
val CONTINUE_COMMANDS = setOf("SEND_MESSAGE", "CONTINUE", "RETRY", "REVERT", "UNREVERT", "WRITE_FILE")
const val NOT_CONTINUED_MESSAGE = "Continue this session on your PC first, then you can chat from your phone."

/**
 * Single source of truth for the UI. All data comes from bambookit-api (session content relayed
 * live from the PC); realtime events keep it current. Nothing is changed locally to "pretend" an
 * action worked — remote actions appear as pending commands until the desktop reports the outcome.
 */
class BambooStore(
    private val api: ApiClient,
    private val auth: AuthRepository,
    private val realtime: RealtimeClient,
    private val store: SecureStore,
    private val json: Json,
    private val scope: CoroutineScope,
    private val notifier: (NotificationItem) -> Unit,
) {
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
    private val _diff = MutableStateFlow<DiffView?>(null)
    val diff: StateFlow<DiffView?> = _diff
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
    private val _share = MutableStateFlow<ShareView?>(null)
    val share: StateFlow<ShareView?> = _share
    private var presenceJob: Job? = null
    private var contentJob: Job? = null
    private var fileMapJob: Job? = null
    private var diagramJob: Job? = null

    fun start() {
        if (started || auth.session.value == null) return
        started = true
        scope.launch {
            runCatching { api.registerPhone() }
                .onSuccess { store.deviceId = it.id; _myDeviceId.value = it.id }
                .onFailure { report("Could not register this phone", it) }
            realtime.start()
        }
        scope.launch { realtime.ready.collect { refreshAll() } }
        scope.launch { realtime.events.collect { handle(it) } }
        // Keep last-seen fresh so the desktop can show this phone as online.
        presenceJob = scope.launch {
            while (true) {
                delay(60_000)
                if (auth.session.value == null) break
                runCatching { api.me() }
            }
        }
    }

    suspend fun signOut() {
        realtime.stop()
        presenceJob?.cancel()
        contentJob?.cancel()
        started = false
        auth.signOut()
        store.deviceId = null
        store.lastSeq = null
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
    }

    /** The PC (desktop device) a session lives on, from the devices list. */
    fun desktopOf(session: Session?): Device? = session?.let { s -> _devices.value.firstOrNull { it.id == s.deviceId } }

    private fun pcName(deviceId: String?): String = _devices.value.firstOrNull { it.id == deviceId }?.name ?: "Your PC"

    // ---------------------------------------------------------------- session detail

    fun openSession(id: String) {
        if (_detail.value?.sessionId != id) _detail.value = SessionDetail(sessionId = id)
        loadSession(id, keep = true)
    }

    fun closeSession() {
        contentJob?.cancel()
        fileMapJob?.cancel()
        diagramJob?.cancel()
        _detail.value = null
        _diff.value = null
        _file.value = null
        _share.value = null
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

    /** The session's project drawn as components; computed fresh on the PC on every call (Rescan). */
    fun loadDiagram() {
        val id = _detail.value?.sessionId ?: return
        diagramJob?.cancel()
        _detail.update { d -> if (d?.sessionId == id) d.copy(diagram = (d.diagram ?: DiagramView()).copy(loading = true)) else d }
        diagramJob = scope.launch {
            try {
                val diagram = api.diagram(id)
                _detail.update { d -> if (d?.sessionId == id) d.copy(diagram = DiagramView(loading = false, loaded = true, diagram = diagram)) else d }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val err = contentError(e, "Could not scan the project on ${pcName(_detail.value?.session?.deviceId)}.")
                _detail.update { d -> if (d?.sessionId == id) d.copy(diagram = (d.diagram ?: DiagramView()).copy(loading = false, error = err)) else d }
            }
        }
    }

    private fun contentError(e: Exception, fallback: String): ContentError =
        (e as? ApiException)?.let { ContentError(it.code, it.message ?: fallback) } ?: ContentError("UNKNOWN", e.message ?: fallback)

    /** Retry reading the chat and changed files from the PC. */
    fun retryContent() {
        _detail.value?.let { loadContent(it.sessionId) }
    }

    private fun loadSession(id: String, keep: Boolean) = scope.launch {
        runCatching {
            val session = api.session(id)
            val approvals = api.approvals().filter { it.sessionId == id }
            _detail.update { d ->
                if (d?.sessionId != id) d
                else d.copy(session = session, approvals = approvals, loading = false, error = null, commands = if (keep) d.commands else emptyList())
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

    private fun refreshSessionMeta(id: String) = scope.launch {
        runCatching { api.session(id) }.onSuccess { s -> _detail.update { d -> if (d?.sessionId == id) d.copy(session = s) else d } }
    }

    /** Refuses commands that need a continued session before they reach the API. */
    private fun guardContinued(type: String): Boolean {
        if (type !in CONTINUE_COMMANDS || _detail.value?.canChat == true) return true
        _messages.tryEmit(NOT_CONTINUED_MESSAGE)
        return false
    }

    fun send(type: String, text: String? = null) {
        val d = _detail.value ?: return
        if (!guardContinued(type)) return
        scope.launch {
            runCatching {
                api.sendCommand(d.sessionId, type, if (text != null) buildJsonObject { put("text", text) } else buildJsonObject { })
            }.onSuccess { res ->
                track(PendingCommand(res.data.id, type, res.data.status, deviceOnline = res.deviceOnline))
                if (!res.deviceOnline) _messages.tryEmit("${pcName(d.session?.deviceId)} is offline — the request expires in 5 minutes if it does not reconnect.")
            }.onFailure { report("Could not send", it) }
        }
    }

    fun respond(approval: Approval, reply: String) = scope.launch {
        runCatching { api.respondApproval(approval.id, reply) }
            .onSuccess { refreshApprovals() }
            .onFailure { report("Could not answer approval", it) }
    }

    /**
     * Sends a command for the open session and waits for the desktop's result.
     * Polls the command (1 s) so a result is never missed, even across reconnects.
     */
    private suspend fun runCommand(type: String, payload: JsonObject = buildJsonObject { }): Command {
        val d = _detail.value ?: throw IllegalStateException("No session open")
        if (type in CONTINUE_COMMANDS && !d.canChat) throw IllegalStateException(NOT_CONTINUED_MESSAGE)
        val res = api.sendCommand(d.sessionId, type, payload)
        track(PendingCommand(res.data.id, type, res.data.status, deviceOnline = res.deviceOnline))
        if (!res.deviceOnline) throw IllegalStateException("${pcName(d.session?.deviceId)} is offline. Open BambooKit Desktop there and try again.")
        repeat(90) {
            delay(1000)
            val c = api.command(res.data.id)
            if (c.status != "PENDING") {
                track(PendingCommand(c.id, type, c.status, c.error))
                if (c.status == "FAILED") throw IllegalStateException(c.error ?: "The desktop could not do that")
                return c
            }
        }
        throw IllegalStateException("No answer from your PC yet. It may be busy; try again.")
    }

    private fun JsonElement?.str(key: String): String? =
        (this as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.content }

    fun openDiff(file: String) {
        _diff.value = DiffView(file)
        scope.launch {
            runCatching { runCommand("GET_DIFF", buildJsonObject { put("file", file) }) }
                .onSuccess { cmd ->
                    val result = cmd.result?.let { json.decodeFromJsonElement<DiffResult>(it) }
                    val diff = result?.files?.firstOrNull { it.file == file } ?: result?.files?.firstOrNull()
                    _diff.value = if (diff == null) DiffView(file, loading = false, error = "No changes recorded for this file") else DiffView(file, loading = false, result = diff)
                }
                .onFailure { e -> _diff.value = DiffView(file, loading = false, error = e.message) }
        }
    }

    fun closeDiff() {
        _diff.value = null
    }

    fun openFile(path: String) {
        _file.value = FileView(path)
        scope.launch {
            runCatching { runCommand("READ_FILE", buildJsonObject { put("path", path) }) }
                .onSuccess { cmd -> _file.value = FileView(cmd.result.str("path") ?: path, loading = false, content = cmd.result.str("content") ?: "", sha256 = cmd.result.str("sha256")) }
                .onFailure { e -> _file.value = FileView(path, loading = false, error = e.message) }
        }
    }

    fun saveFile(content: String) {
        val current = _file.value ?: return
        _file.value = current.copy(saving = true, error = null)
        scope.launch {
            runCatching {
                runCommand("WRITE_FILE", buildJsonObject {
                    put("path", current.path)
                    put("content", content)
                    put("baseSha256", current.sha256)
                })
            }
                .onSuccess { cmd ->
                    _file.value = current.copy(content = content, sha256 = cmd.result.str("sha256"), saving = false, savedAt = System.currentTimeMillis())
                    _messages.tryEmit("Saved ${current.path} on your PC")
                }
                .onFailure { e -> _file.value = current.copy(saving = false, error = e.message) }
        }
    }

    fun closeFile() {
        _file.value = null
    }

    /** Rewind the conversation (and files, where the engine has snapshots) to before this message. */
    fun rewind(messageId: String) = scope.launch {
        runCatching { runCommand("REVERT", buildJsonObject { put("messageId", messageId) }) }
            .onSuccess { _messages.tryEmit("Rewound to before that message. Undo with \"Undo rewind\".") }
            .onFailure { report("Rewind failed", it) }
    }

    fun undoRewind() = scope.launch {
        runCatching { runCommand("UNREVERT") }
            .onSuccess { _messages.tryEmit("Rewind undone") }
            .onFailure { report("Undo failed", it) }
    }

    fun shareSession() {
        _share.value = ShareView()
        scope.launch {
            runCatching { runCommand("SHARE") }
                .onSuccess { cmd -> _share.value = ShareView(loading = false, url = cmd.result.str("url")) }
                .onFailure { e -> _share.value = ShareView(loading = false, error = e.message) }
        }
    }

    fun unshareSession() = scope.launch {
        runCatching { runCommand("UNSHARE") }
            .onSuccess { _share.value = null; _messages.tryEmit("Session unpublished") }
            .onFailure { e -> _share.value = ShareView(loading = false, error = e.message) }
    }

    fun closeShare() {
        _share.value = null
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

    fun markAllRead() = scope.launch {
        runCatching { api.markAllRead(); _notifications.value = api.notifications() }.onFailure { report("Could not mark read", it) }
        refreshOverviewSoon()
    }

    private suspend fun refreshApprovals() {
        runCatching { _approvals.value = api.approvals() }
        _detail.value?.let { d -> _detail.update { it?.copy(approvals = _approvals.value.filter { a -> a.sessionId == d.sessionId }) } }
    }

    // ---------------------------------------------------------------- realtime

    private suspend fun handle(event: RealtimeEvent) {
        try {
            when (event.type) {
                "session.updated" -> {
                    val s = json.decodeFromJsonElement<Session>(event.payload)
                    _sessions.update { list -> (listOf(s) + list.filterNot { it.id == s.id }).sortedByDescending { it.updatedAt } }
                    _detail.update { d -> if (d?.sessionId == s.id) d.copy(session = s) else d }
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
                        if (_detail.value?.fileMap != null) loadFileMap(delayMs = 800)
                    }
                }
                "session.diff" -> {
                    val files = json.decodeFromJsonElement<DiffPayload>(event.payload).files
                    _detail.update { d -> if (d != null && d.sessionId == event.sessionId) d.copy(changes = files) else d }
                    if (_detail.value?.sessionId == event.sessionId && _detail.value?.fileMap != null) loadFileMap(delayMs = 800)
                    refreshOverviewSoon()
                }
                "approval.created", "approval.updated" -> refreshApprovals().also { refreshOverviewSoon() }
                "command.updated" -> {
                    val c = json.decodeFromJsonElement<CommandUpdate>(event.payload)
                    _detail.update { d ->
                        if (d == null || d.commands.none { it.id == c.id }) d
                        else d.copy(commands = d.commands.map { if (it.id == c.id) it.copy(status = c.status, error = c.error) else it })
                    }
                    if (c.status == "FAILED" && _detail.value?.commands?.any { it.id == c.id } == true) _messages.tryEmit("Desktop could not run ${c.type}: ${c.error ?: "failed"}")
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
                        if (d.fileMap?.error?.desktopUnavailable == true && !d.fileMap.loading) loadFileMap()
                        if (d.diagram?.error?.desktopUnavailable == true && !d.diagram.loading) loadDiagram()
                    }
                }
                "notification" -> {
                    val n = json.decodeFromJsonElement<NotificationItem>(event.payload)
                    _notifications.update { listOf(n) + it.filterNot { x -> x.id == n.id } }
                    notifier(n)
                    refreshOverviewSoon()
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
        if (e is ApiException && e.code == "SESSION_NOT_CONTINUED") {
            // Not (or no longer) continued on the PC: say so plainly and re-read the session's state.
            _messages.tryEmit(e.message ?: NOT_CONTINUED_MESSAGE)
            _detail.value?.let { refreshSessionMeta(it.sessionId) }
            return
        }
        val msg = "$prefix: ${e.message}"
        Log.w("BambooStore", msg)
        if (prefix == "Refresh failed") _error.value = e.message
        _messages.tryEmit(msg)
    }
}

@kotlinx.serialization.Serializable
private data class DiffPayload(val files: List<ChangedFile> = emptyList())

@kotlinx.serialization.Serializable
private data class CommandUpdate(val id: String, val type: String, val status: String, val error: String? = null)
