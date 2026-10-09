package com.bambookit.android.presentation.screens

import com.bambookit.android.data.Approval
import com.bambookit.android.data.ContentError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Contract13UiTest {

    // ---- §9 session tabs: Chat first, then Summary, then the rest; default to Chat with chat content

    @Test
    fun `chat is the first tab, summary second`() {
        assertEquals(SessionTabId.Chat, SessionTabId.entries.first())
        assertEquals(SessionTabId.Summary, SessionTabId.entries[1])
    }

    @Test
    fun `a session with chat content opens on chat, without it opens on summary`() {
        assertEquals(SessionTabId.Chat, defaultSessionTab(hasChatContent = true))
        assertEquals(SessionTabId.Summary, defaultSessionTab(hasChatContent = false))
    }

    // ---- §2 approval modes

    @Test
    fun `approval mode labels`() {
        assertEquals("Ask", approvalModeLabel("ask"))
        assertEquals("Auto edits", approvalModeLabel("edits"))
        assertEquals("Auto-approve", approvalModeLabel("all"))
        assertEquals("Ask", approvalModeLabel("something-unknown"))
    }

    // ---- §3 resolved rows

    @Test
    fun `resolved approval shows the right status and who`() {
        assertEquals("Approved", approvalStatusLabel(Approval("1", status = "APPROVED")))
        assertEquals("Rejected", approvalStatusLabel(Approval("1", status = "REJECTED")))
        assertEquals("Answered", approvalStatusLabel(Approval("1", status = "ANSWERED")))
        assertEquals("Auto", approvalStatusLabel(Approval("1", status = "APPROVED", resolvedBy = "auto")))
        assertEquals("from your phone", resolvedByLabel(Approval("1", status = "APPROVED", resolvedBy = "phone")))
        assertEquals("automatically", resolvedByLabel(Approval("1", status = "APPROVED", resolvedBy = "auto")))
    }

    // ---- §7 note: scary message gating

    @Test
    fun `a plain PC error does not say couldn't do this`() {
        val title = errorTitle(ContentError("DESKTOP_ERROR", "boom"), "Work PC", "fallback")
        assertFalse(title.contains("couldn't do this", ignoreCase = true))
    }

    // ---- §7 developer options sub-screens

    @Test
    fun `developer options expose power, terminal and research`() {
        assertEquals(setOf(DevScreen.Power, DevScreen.Terminal, DevScreen.Research), DevScreen.entries.toSet())
    }

    // ---- §7 terminal: control sequences are stripped gracefully

    @Test
    fun `ansi control sequences are stripped from terminal output`() {
        assertEquals("hello", stripAnsi("\u001B[31mhello\u001B[0m"))
        assertEquals("ls\n", stripAnsi("ls\r\n"))
        assertTrue(stripAnsi("\u001B]0;title\u0007done").endsWith("done"))
    }
}
