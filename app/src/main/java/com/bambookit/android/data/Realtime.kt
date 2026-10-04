package com.bambookit.android.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.min

enum class LinkState { Disconnected, Connecting, Connected }

/**
 * Realtime stream from bambookit-api (Server-Sent Events). Resumes with ?after=<last seq> so
 * events published while the phone was offline are replayed instead of silently lost.
 * Ephemeral events (no SSE id, seq -1) are delivered live only and never change the resume point.
 */
class RealtimeClient(
    baseHttp: OkHttpClient,
    private val auth: AuthRepository,
    private val store: SecureStore,
    private val json: Json,
    private val scope: CoroutineScope,
) {
    // Streams stay open indefinitely: no call timeout; the server pings every 20 s.
    private val http = baseHttp.newBuilder().readTimeout(90, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 2048)
    val events: SharedFlow<RealtimeEvent> = _events
    private val _state = MutableStateFlow(LinkState.Disconnected)
    val state: StateFlow<LinkState> = _state
    /** Emits each time the stream (re)connects; listeners refresh their snapshots. */
    private val _ready = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val ready: SharedFlow<Unit> = _ready
    private var job: Job? = null
    private var source: EventSource? = null

    /** The server's event sequence when the current connection became ready (null before the first "ready"). */
    @Volatile
    var connectionStartSeq: Long? = null
        private set

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var attempt = 0
            while (isActive && auth.session.value != null) {
                _state.value = LinkState.Connecting
                val ok = runCatching { connectOnce() }.onFailure { Log.w(TAG, "realtime error: ${it.message}") }.getOrDefault(false)
                if (ok) attempt = 0
                _state.value = LinkState.Disconnected
                if (!isActive || auth.session.value == null) break
                delay(min(30_000L, 1000L shl min(attempt++, 5)))
            }
        }
    }

    fun stop() {
        job?.cancel()
        source?.cancel()
        _state.value = LinkState.Disconnected
    }

    /** Returns true if the stream reached "ready" before closing. */
    private suspend fun connectOnce(): Boolean {
        val token = auth.accessToken()
        val after = store.lastSeq
        val request = Request.Builder()
            .url("${Config.apiUrl}/v1/realtime/stream" + (after?.let { "?after=$it" } ?: ""))
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .apply { store.deviceId?.let { header("X-BK-Device-Id", it) } }
            .build()
        var reachedReady = false
        suspendCancellableCoroutine { cont ->
            val listener = object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    when (type) {
                        "ready" -> {
                            reachedReady = true
                            _state.value = LinkState.Connected
                            val seq = runCatching { json.parseToJsonElement(data) }.getOrNull()
                                ?.let { (it as? kotlinx.serialization.json.JsonObject)?.get("seq")?.toString()?.toLongOrNull() }
                            // Stored events up to this sequence were published before this connection started;
                            // they may be replayed now (?after=) but must not ring as new notifications.
                            connectionStartSeq = seq
                            // First connection: start from now.
                            if (store.lastSeq == null && seq != null) store.lastSeq = seq
                            _ready.tryEmit(Unit)
                        }
                        "ping" -> Unit
                        else -> runCatching { json.decodeFromString(RealtimeEvent.serializer(), data) }
                            .onSuccess { event ->
                                // Only stored events have a seq >= 0 (and an SSE id). Ephemeral events (live chat
                                // parts, diffs, relay requests) have seq -1 and no id of their own — OkHttp hands them
                                // the previous id — so they must not move the resume point used for ?after=.
                                if (event.seq >= 0) store.lastSeq = event.seq
                                _events.tryEmit(event)
                            }
                            .onFailure { Log.w(TAG, "bad event $type: ${it.message}") }
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    if (response?.code == 401) scope.launch { runCatching { auth.accessToken(forceRefresh = true) } }
                    Log.w(TAG, "stream failure ${response?.code}: ${t?.message}")
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            source = EventSources.createFactory(http).newEventSource(request, listener)
            cont.invokeOnCancellation { source?.cancel() }
        }
        return reachedReady
    }

    companion object {
        private const val TAG = "BambooRealtime"
    }
}
