package com.bambookit.android.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearActivityTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test
    fun `nothing is filtered before a clear`() {
        val f = ActivityClearFilter()
        assertTrue(f.accepts(0))
        assertTrue(f.accepts(42))
        assertTrue(f.accepts(-1))
    }

    @Test
    fun `replayed events at or below the cleared sequence are dropped`() {
        val f = ActivityClearFilter()
        f.cleared(100)
        assertFalse(f.accepts(1))
        assertFalse(f.accepts(100))
        assertTrue(f.accepts(101))
        // Live-only events have no stored sequence.
        assertTrue(f.accepts(-1))
    }

    @Test
    fun `the cleared sequence only moves forward and resets on sign out`() {
        val f = ActivityClearFilter()
        f.cleared(100)
        f.cleared(50)
        assertEquals(100, f.clearedThroughSeq)
        f.cleared(120)
        assertFalse(f.accepts(110))
        f.reset()
        assertTrue(f.accepts(110))
    }

    @Test
    fun `posted request and session notifications are cancelled but not the connection one`() {
        val posted = listOf(
            1 to BambooNotifier.CHANNEL_REQUESTS,
            2 to BambooNotifier.CHANNEL_SESSIONS,
            BambooNotifier.CONNECTION_NOTIFICATION_ID to BambooNotifier.CHANNEL_CONNECTION,
            3 to BambooNotifier.CHANNEL_CONNECTION,
            4 to "bambookit_agents",
            5 to "app_updates",
            6 to null,
        )
        assertEquals(listOf(1, 2, 4), postedNotificationsToCancel(posted))
    }

    @Test
    fun `the connection notification id is kept even on an activity channel`() {
        assertEquals(emptyList<Int>(), postedNotificationsToCancel(listOf(BambooNotifier.CONNECTION_NOTIFICATION_ID to BambooNotifier.CHANNEL_SESSIONS)))
    }

    @Test
    fun `clear response and realtime payload parse`() {
        val r = json.decodeFromString(Envelope.serializer(ActivityCleared.serializer()), """{"data":{"clearedThroughSeq":812,"notificationsRemoved":5}}""").data
        assertEquals(812, r.clearedThroughSeq)
        assertEquals(5, r.notificationsRemoved)
        val e = json.decodeFromString(
            RealtimeEvent.serializer(),
            """{"seq":813,"id":"e1","timestamp":"2026-10-05T09:00:00Z","type":"activity.cleared","payload":{"clearedThroughSeq":812}}""",
        )
        assertEquals("activity.cleared", e.type)
        assertEquals(812, json.decodeFromJsonElement(ActivityCleared.serializer(), e.payload).clearedThroughSeq)
    }

    @Test
    fun `notification text never shows the engine name`() {
        assertEquals("that lives in your BambooKit client", brandText("that lives in your opencode client"))
        assertEquals("BambooKit and BambooKit", brandText("OpenCode and OPENCODE"))
    }
}
