package com.bambookit.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaTest {
    private val resetAt = "2026-10-06T00:00:00Z"
    private val resetMs = java.time.Instant.parse(resetAt).toEpochMilli()
    private val before = resetMs - 3_600_000L
    private val after = resetMs + 1_000L

    private fun free(messages: Int, sessions: Int) = Plan(
        plan = "free", ads = true, resetsAt = resetAt,
        limits = PlanLimits(phoneMessagesPerDay = 20, phoneSessionsPerDay = 3, desktops = 1),
        usage = PlanUsage(phoneMessagesToday = messages, phoneSessionsToday = sessions),
    )

    @Test
    fun `remaining counts on the free plan`() {
        val p = free(messages = 7, sessions = 1)
        assertEquals(13, p.messagesLeft(before))
        assertEquals(2, p.sessionsLeft(before))
        assertFalse(p.messagesLocked(before))
        assertFalse(p.sessionsLocked(before))
        assertEquals("13 of 20 free messages left today", Quota.messagesText(13, 20))
        assertEquals("2 of 3 free new sessions left today", Quota.sessionsText(2, 3))
    }

    @Test
    fun `remaining never goes negative and locks at zero`() {
        val p = free(messages = 25, sessions = 3)
        assertEquals(0, p.messagesLeft(before))
        assertEquals(0, p.sessionsLeft(before))
        assertTrue(p.messagesLocked(before))
        assertTrue(p.sessionsLocked(before))
    }

    @Test
    fun `lock lifts once the reset time has passed`() {
        val p = free(messages = 20, sessions = 3)
        assertTrue(p.messagesLocked(before))
        assertFalse(p.messagesLocked(after))
        assertEquals(20, p.messagesLeft(after))
        assertEquals(3, p.sessionsLeft(after))
        // Unknown reset time: the counters are trusted as they are.
        assertTrue(p.copy(resetsAt = null).messagesLocked(after))
    }

    @Test
    fun `pro and unlimited plans are never locked`() {
        val pro = free(messages = 99, sessions = 99).copy(plan = "pro", ads = false)
        assertNull(pro.messagesLeft(before))
        assertNull(pro.sessionsLeft(before))
        assertFalse(pro.messagesLocked(before))
        assertFalse(pro.sessionsLocked(before))
        val noLimit = Plan(plan = "free", limits = PlanLimits(phoneMessagesPerDay = null))
        assertNull(noLimit.messagesLeft(before))
        assertFalse(noLimit.messagesLocked(before))
    }

    @Test
    fun `warning color at three or fewer`() {
        assertFalse(Quota.warn(null))
        assertFalse(Quota.warn(4))
        assertTrue(Quota.warn(3))
        assertTrue(Quota.warn(0))
    }

    @Test
    fun `optimistic usage counts sends and new sessions`() {
        val p = free(messages = 18, sessions = 2)
        val sent = p.withMessageSent().withMessageSent()
        assertEquals(20, sent.usage.phoneMessagesToday)
        assertTrue(sent.messagesLocked(before))
        val created = p.withSessionCreated()
        assertEquals(3, created.usage.phoneSessionsToday)
        assertTrue(created.sessionsLocked(before))
        // Other counters untouched.
        assertEquals(2, sent.usage.phoneSessionsToday)
        assertEquals(18, created.usage.phoneMessagesToday)
    }

    @Test
    fun `local limit dialog for a locked button`() {
        val l = free(messages = 20, sessions = 4).localLimit(sessions = true)
        assertTrue(l.isSessions)
        assertEquals(3, l.max)
        assertEquals(3, l.used)
        assertEquals(resetAt, l.resetsAt)
        val m = free(messages = 20, sessions = 0).localLimit(sessions = false)
        assertFalse(m.isSessions)
        assertEquals(20, m.max)
        assertEquals(20, m.used)
    }

    @Test
    fun `offset reset times are understood`() {
        val p = free(0, 0).copy(resetsAt = "2026-10-06T05:30:00+05:30")
        assertEquals(resetMs, p.resetsAtMs())
    }
}
