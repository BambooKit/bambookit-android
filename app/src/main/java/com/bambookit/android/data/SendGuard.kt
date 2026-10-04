package com.bambookit.android.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Stops duplicate sends: only one send at a time (in-flight guard), and the same message is not sent
 * again within [debounceMs] of the previous attempt (double taps). [clock] is injectable for tests.
 */
class SendGuard(private val debounceMs: Long = 1500, private val clock: () -> Long = { System.currentTimeMillis() }) {
    private var inFlight = false
    private var lastKey: String? = null
    private var lastAt = Long.MIN_VALUE / 2

    /** True when a send of [key] may start now; the caller must call [finish] afterwards. */
    @Synchronized
    fun tryBegin(key: String): Boolean {
        if (inFlight) return false
        val now = clock()
        if (key == lastKey && now - lastAt < debounceMs) return false
        inFlight = true
        lastKey = key
        lastAt = now
        return true
    }

    /** The send finished. A failed send may be retried at once ([ok] = false clears the debounce). */
    @Synchronized
    fun finish(ok: Boolean) {
        inFlight = false
        if (!ok) lastKey = null
    }

    val busy: Boolean @Synchronized get() = inFlight
}

/** What the send button shows. */
sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState
    /** Accepted by BambooKit at [at] (elapsed ms); shown briefly as "Sent". */
    data class Sent(val at: Long, val deviceOnline: Boolean) : SendState
    /** Not sent; [text] is kept so Retry sends it again. */
    data class Failed(val message: String, val text: String) : SendState
}

/**
 * Coalescing refresher for realtime-driven reloads. [request] marks the data stale; the first request starts a
 * run after [debounceMs], later requests during the wait or while [block] runs are merged into one more run.
 * A run in flight is never cancelled by new events, so a steady stream of events cannot starve the reload
 * (the reason the file map and diagram used to stop updating).
 */
class Coalescer(private val scope: CoroutineScope, private val debounceMs: Long, private val block: suspend () -> Unit) {
    private var job: Job? = null
    @Volatile private var dirty = false

    fun request() {
        dirty = true
        if (job?.isActive == true) return
        job = scope.launch {
            delay(debounceMs)
            while (dirty) {
                dirty = false
                runCatching { block() }
                if (dirty) delay(debounceMs)
            }
        }
    }

    fun cancel() {
        dirty = false
        job?.cancel()
        job = null
    }
}
