package com.bambookit.android.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class KeyEnvelopeTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val pem = "-----BEGIN PUBLIC KEY-----\n" +
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.public.encoded) + "\n-----END PUBLIC KEY-----\n"

    /** Opens an envelope the way the PC does (RSA-OAEP SHA-256/MGF1-SHA-256, then AES-256-GCM with the tag appended). */
    private fun open(e: KeyEnvelope): String {
        val dec = Base64.getDecoder()
        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.DECRYPT_MODE, pair.private, KeyEnvelopes.oaep)
        val aes = rsa.doFinal(dec.decode(e.key))
        assertEquals(32, aes.size)
        val iv = dec.decode(e.iv)
        assertEquals(12, iv.size)
        val gcm = Cipher.getInstance("AES/GCM/NoPadding")
        gcm.init(Cipher.DECRYPT_MODE, SecretKeySpec(aes, "AES"), GCMParameterSpec(128, iv))
        return String(gcm.doFinal(dec.decode(e.data)), Charsets.UTF_8)
    }

    @Test
    fun roundTripsTheApiKey() {
        val key = "sk-test-123\"quote\\slash".toCharArray()
        val e = KeyEnvelopes.seal(key.copyOf(), pem)
        assertEquals("RSA-OAEP-256+A256GCM", e.alg)
        val json = Json.parseToJsonElement(open(e)).jsonObject
        assertEquals("sk-test-123\"quote\\slash", json["apiKey"]!!.jsonPrimitive.content)
        assertEquals(setOf("apiKey"), json.keys)
    }

    @Test
    fun everyEnvelopeIsFresh() {
        val a = KeyEnvelopes.seal("k".toCharArray(), pem)
        val b = KeyEnvelopes.seal("k".toCharArray(), pem)
        assertNotEquals(a.iv, b.iv)
        assertNotEquals(a.data, b.data)
        assertFalse(a.toString().contains(a.data))
    }

    @Test
    fun tamperingIsDetected() {
        val e = KeyEnvelopes.seal("secret".toCharArray(), pem)
        val bytes = Base64.getDecoder().decode(e.data).also { it[0] = (it[0].toInt() xor 1).toByte() }
        val broken = e.copy(data = Base64.getEncoder().encodeToString(bytes))
        assertTrue(runCatching { open(broken) }.isFailure)
    }

    @Test
    fun escapesControlCharacters() {
        assertEquals("{\"apiKey\":\"a\\u000ab\"}", String(KeyEnvelopes.jsonApiKey("a\nb".toCharArray()), Charsets.UTF_8))
    }
}

class UnifiedDiffTest {
    @Test
    fun crlfAndNoNewlineAndMultipleHunks() {
        val patch = "--- a/x.txt\r\n+++ b/x.txt\r\n@@ -1,2 +1,2 @@\r\n a\r\n-b\r\n\\ No newline at end of file\r\n+b2\r\n\\ No newline at end of file\r\n@@ -10,3 +10,4 @@ fun f()\r\n c\r\n+d\r\n e\r\n f\r\n"
        val lines = parseUnifiedDiff(patch)
        val code = lines.filterIsInstance<DiffCode>()
        assertEquals(listOf(' ', '-', '+', ' ', '+', ' ', ' '), code.map { it.type })
        assertEquals(DiffCode('-', 2, null, "b"), code[1])
        assertEquals(DiffCode('+', null, 2, "b2"), code[2])
        assertEquals(DiffCode('+', null, 11, "d"), code[4])
        assertEquals(DiffCode(' ', 12, 13, "f"), code[6])
        assertEquals(2, lines.count { it is DiffNote })
        assertEquals(2, lines.count { it is DiffHunkHeader })
        assertTrue(code.none { it.text.endsWith("\r") })
        assertEquals(DiffStats(2, 1), diffStats(patch))
    }

    @Test
    fun linesThatLookLikeHeadersInsideAHunkAreCode() {
        // Removing a SQL comment "-- x" gives "--- x"; adding "++ y" gives "+++ y".
        val patch = "--- a/q.sql\n+++ b/q.sql\n@@ -1,2 +1,2 @@\n--- x\n++++ y\n keep\n"
        val code = parseUnifiedDiff(patch).filterIsInstance<DiffCode>()
        assertEquals(DiffCode('-', 1, null, "-- x"), code[0])
        assertEquals(DiffCode('+', null, 1, "+++ y"), code[1])
        assertEquals(DiffStats(1, 1), diffStats(patch))
    }

    @Test
    fun newAndDeletedFiles() {
        val added = "--- /dev/null\n+++ b/n.ts\n@@ -0,0 +1,3 @@\n+a\n+b\n+c\n"
        assertEquals(listOf(1, 2, 3), parseUnifiedDiff(added).filterIsInstance<DiffCode>().map { it.newNo })
        assertEquals(DiffStats(3, 0), diffStats(added))
        val deleted = "--- a/d.ts\n+++ /dev/null\n@@ -1,2 +0,0 @@\n-a\n-b\n"
        assertEquals(DiffStats(0, 2), diffStats(deleted))
    }

    @Test
    fun blankContextLineWithoutSpaceAndSeveralFiles() {
        val patch = "diff --git a/a b/a\n--- a/a\n+++ b/a\n@@ -1,3 +1,3 @@\n x\n\n-y\n+z\ndiff --git a/b b/b\n--- a/b\n+++ b/b\n@@ -5 +5 @@\n-p\n+q\n"
        val code = parseUnifiedDiff(patch).filterIsInstance<DiffCode>()
        assertEquals(DiffCode(' ', 2, 2, ""), code[1])
        assertEquals(DiffCode('-', 3, null, "y"), code[2])
        assertEquals(DiffCode('-', 5, null, "p"), code[4])
        assertEquals(DiffStats(2, 2), diffStats(patch))
    }

    @Test
    fun explicitFileStates() {
        assertEquals(FileState.Binary, fileState("modified", listOf("Binary files a/x.png and b/x.png differ")))
        assertEquals(FileState.TooLarge, fileState("modified", emptyList(), truncated = true))
        assertEquals(FileState.New, fileState("added", emptyList()))
        assertEquals(FileState.Deleted, fileState("deleted", emptyList()))
        assertEquals(FileState.Renamed, fileState("renamed", emptyList()))
        assertEquals(FileState.Modified, fileState(null, listOf("@@ -1 +1 @@\n-a\n+b")))
    }
}

class TodoReducerTest {
    private val a = Todo("1", "Write tests", "completed")
    private val b = Todo("2", "Fix bug", "in_progress")

    @Test
    fun liveEventsReplaceTheListAndBeatOlderReads() {
        var v = TodosView()
        val started = v.version
        v = TodoReducer.loading(v)
        v = TodoReducer.live(v, "ses_1", TodosEvent("ses_1", listOf(a, b)))
        // A read that began before the live update finishes later with an older list: it is dropped.
        v = TodoReducer.fetched(v, listOf(a), started)
        assertEquals(listOf(a, b), v.todos)
        assertFalse(v.loading)
        // Another session's event is ignored.
        v = TodoReducer.live(v, "ses_1", TodosEvent("ses_2", emptyList()))
        assertEquals(2, v.todos.size)
        // A fresh read applies.
        v = TodoReducer.fetched(v, listOf(b), v.version)
        assertEquals(listOf(b), v.todos)
    }

    @Test
    fun statesAndCounts() {
        assertEquals(TodoState.Done, todoState("completed"))
        assertEquals(TodoState.InProgress, todoState("in_progress"))
        assertEquals(TodoState.Cancelled, todoState("cancelled"))
        assertEquals(TodoState.Pending, todoState("pending"))
        val c = TodoReducer.counts(listOf(a, b, Todo("3", "x", "pending"), Todo("4", "y", "cancelled")))
        assertEquals(TodoReducer.Counts(1, 1, 1, 1), c)
        assertEquals("✓", TodoState.Done.mark)
    }
}

class SendGuardTest {
    @Test
    fun blocksWhileInFlightAndDebouncesRepeats() {
        var now = 0L
        val g = SendGuard(debounceMs = 1000, clock = { now })
        assertTrue(g.tryBegin("hi"))
        assertFalse(g.tryBegin("other"))
        g.finish(true)
        now = 500
        assertFalse(g.tryBegin("hi"))
        assertTrue(g.tryBegin("other"))
        g.finish(true)
        now = 2000
        assertTrue(g.tryBegin("hi"))
        g.finish(false)
        // A failed send may be retried at once.
        assertTrue(g.tryBegin("hi"))
    }
}

class LiveDiagramTest {
    private val pc = Device("dsk_1", "desktop", "Work PC", "windows", online = true)
    private val phone = Device("mob_1", "mobile", "Pixel", "android", online = true)
    private val session = Session("ses_1", "dsk_1", projectId = "prj_1", projectName = "app", directory = "/w/app", title = "Fix", status = "busy", agent = "build", model = "anthropic/claude-x")
    private val history = SessionHistory(
        branch = "main", baseCommit = "abcdef123456",
        timeline = listOf(
            TimelineEntry("t1", "2026-10-01T10:00:00Z", "read", "a.ts"),
            TimelineEntry("t2", "2026-10-01T10:05:00Z", "edit", "a.ts"),
            TimelineEntry("t3", "2026-10-01T10:10:00Z", "test", "npm test"),
        ),
        changes = listOf(
            FileChange("src/a.ts", "modified", edits = listOf(FileEdit("2026-10-01T10:05:00Z", "edit", 2, 1))),
            FileChange("src\\A.ts", "added", edits = listOf(FileEdit("2026-10-01T10:20:00Z", "write", 3, 0))),
        ),
        tests = listOf(TestRun("2026-10-01T10:10:00Z", "npm test", "passed", 0), TestRun("2026-10-01T10:10:00Z", "npm test", "failed", 1)),
    )
    private val input = LiveInput(
        account = Account("u1", "me@x.io", name = "Me"), devices = listOf(pc, phone), myDeviceId = "mob_1", webOnline = true,
        session = session, history = history, todos = listOf(Todo("td1", "Do it", "in_progress")),
        approvals = listOf(Approval("apr_1", sessionId = "ses_1", permission = "bash", status = "PENDING", createdAt = "2026-10-01T10:06:00Z")),
    )

    @Test
    fun buildsEveryTierWithUniqueIds() {
        val g = buildLiveGraph(input)
        assertEquals(g.nodes.size, g.nodes.map { it.id }.toSet().size)
        val tiers = g.tiers.map { it.first }.toSet()
        assertTrue(tiers.containsAll(listOf(LiveTier.Account, LiveTier.Devices, LiveTier.Project, LiveTier.Session, LiveTier.Agent, LiveTier.Model, LiveTier.Tools, LiveTier.Files, LiveTier.Git, LiveTier.Tests, LiveTier.Todos, LiveTier.Approvals)))
        // Two test runs of the same command at the same time stay two nodes.
        assertEquals(2, g.nodes.count { it.tier == LiveTier.Tests })
        assertTrue(g.nodes.any { it.id == "device:mob_1" && it.label == "This phone" })
        assertTrue(g.edges.all { e -> g.nodes.any { it.id == e.from } && g.nodes.any { it.id == e.to } })
    }

    @Test
    fun filtersKeepOnlyTheirTiers() {
        val files = buildLiveGraph(input, LiveFilter.Files)
        assertTrue(files.nodes.all { it.tier == LiveTier.Files || it.tier == LiveTier.Project })
        assertEquals(2, files.nodes.count { it.tier == LiveTier.Files })
        val git = buildLiveGraph(input, LiveFilter.Git)
        assertTrue(git.nodes.any { it.tier == LiveTier.Git })
        assertTrue(git.nodes.none { it.tier == LiveTier.Tests })
    }

    @Test
    fun historyModeShowsThePast() {
        val g = buildLiveGraph(input, until = Instant.parse("2026-10-01T10:06:00Z"))
        assertEquals(listOf("read", "edit"), g.nodes.filter { it.tier == LiveTier.Tools }.map { it.label })
        assertEquals(1, g.nodes.count { it.tier == LiveTier.Files })
        assertEquals(0, g.nodes.count { it.tier == LiveTier.Tests })
        assertEquals(0, g.nodes.count { it.tier == LiveTier.Todos })
        assertEquals(1, g.nodes.count { it.tier == LiveTier.Approvals })
        assertEquals(4, historyMoments(history).size)
    }

    @Test
    fun rebuildingWithNewStateKeepsUpdating() {
        var state = input
        repeat(50) { i ->
            val h = state.history!!
            state = state.copy(history = h.copy(changes = h.changes + FileChange("f$i.ts", "added", edits = listOf(FileEdit("2026-10-01T11:00:00Z", "write", 1, 0)))))
            val g = buildLiveGraph(state)
            assertTrue(g.nodes.any { it.id == "file:f$i.ts" })
        }
    }
}
