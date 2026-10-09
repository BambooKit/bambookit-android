package com.bambookit.android.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Contract13Test {
    private val json = Json { ignoreUnknownKeys = true }

    // ---- §3 approvals filter parsing

    @Test
    fun `approval filter maps to the status query the API expects`() {
        assertEquals("pending", ApprovalFilter.Pending.status)
        assertEquals("resolved", ApprovalFilter.Resolved.status)
        assertEquals("all", ApprovalFilter.All.status)
        // Pending is the default (keeps the old pending-only behaviour).
        assertEquals(ApprovalFilter.Pending, ApprovalsHistoryView().filter)
    }

    @Test
    fun `a resolved approval decodes status, who and when`() {
        val a = json.decodeFromString(
            Approval.serializer(),
            """{"id":"a1","status":"APPROVED","resolvedBy":"phone","resolvedAt":"2026-10-08T10:00:00Z","kind":"permission"}""",
        )
        assertFalse(a.isPending)
        assertEquals("phone", a.resolvedBy)
        assertFalse(a.isAuto)
        assertEquals("2026-10-08T10:00:00Z", a.resolvedAt)
    }

    @Test
    fun `an auto-approved request is marked auto`() {
        val a = json.decodeFromString(Approval.serializer(), """{"id":"a2","status":"APPROVED","resolvedBy":"auto"}""")
        assertTrue(a.isAuto)
    }

    // ---- §1 / §2 / §4 settings and device.updated

    @Test
    fun `device settings decode with defaults and real values`() {
        val d = json.decodeFromString(Device.serializer(), """{"id":"d1","kind":"desktop","name":"PC","platform":"windows"}""")
        assertNull(d.settings)
        val withSettings = json.decodeFromString(
            Device.serializer(),
            """{"id":"d1","kind":"desktop","name":"PC","platform":"windows","settings":{"approvalMode":"all","keepAwake":true}}""",
        )
        assertEquals("all", withSettings.settings?.approvalMode)
        assertEquals(true, withSettings.settings?.keepAwake)
        // Defaults when the PC reports an empty settings object.
        val empty = json.decodeFromString(DeviceSettings.serializer(), "{}")
        assertEquals("ask", empty.approvalMode)
        assertFalse(empty.keepAwake)
    }

    @Test
    fun `device updated event carries the device id and settings`() {
        val u = json.decodeFromString(DeviceUpdated.serializer(), """{"deviceId":"d1","settings":{"approvalMode":"edits","keepAwake":false}}""")
        assertEquals("d1", u.deviceId)
        assertEquals("edits", u.settings?.approvalMode)
    }

    // ---- §5 files changed (24h)

    @Test
    fun `profile stats parse filesChanged24h`() {
        val s = json.decodeFromString(ProfileStats.serializer(), """{"filesChanged24h":42}""")
        assertEquals(42, s.filesChanged24h)
        // Missing field defaults to 0 (older server).
        assertEquals(0, json.decodeFromString(ProfileStats.serializer(), "{}").filesChanged24h)
    }

    // ---- §7 note: error-message gating

    @Test
    fun `a plain desktop error is not a version gap`() {
        assertFalse(ContentError("DESKTOP_ERROR", "Something broke on the PC").versionGap)
    }

    @Test
    fun `a desktop update required is a version gap`() {
        assertTrue(ContentError("DESKTOP_UPDATE_REQUIRED", "update needed").versionGap)
        assertTrue(ContentError("DESKTOP_OUTDATED", "too old").versionGap)
    }

    // ---- §7 research key storage

    @Test
    fun `gemini key is trimmed and blank clears it`() {
        assertEquals("AIzaKEY", ResearchRepository.cleanKey("  AIzaKEY  "))
        assertNull(ResearchRepository.cleanKey("   "))
        assertNull(ResearchRepository.cleanKey(null))
    }

    @Test
    fun `wikipedia excerpt html is stripped for the reader`() {
        assertEquals("Kotlin is a language", ResearchRepository.stripHtml("<span class=\"m\">Kotlin</span> is a <b>language</b>"))
        assertEquals("a & b", ResearchRepository.stripHtml("a &amp; b"))
    }

    // ---- §7 remote terminal

    @Test
    fun `remote terminal is a known desktop capability`() {
        assertEquals("remote-terminal", DesktopFeature.RemoteTerminal.capability)
    }
}
