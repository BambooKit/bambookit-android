package com.bambookit.android.presentation.screens

import com.bambookit.android.data.FileChange
import com.bambookit.android.data.HistoryPrompt
import com.bambookit.android.data.HistoryResponse
import com.bambookit.android.data.SessionHistory
import com.bambookit.android.data.TimelineEntry
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHistoryTest {
    private val patch = """
        Index: /work/app/src/a.ts
        ===================================================================
        --- /work/app/src/a.ts
        +++ /work/app/src/a.ts
        @@ -1,3 +1,4 @@ function a()
         line1
        -line2
        +line2b
        +line3
         line4
        \ No newline at end of file
        @@ -10 +11 @@
        -old
        +new

    """.trimIndent()

    @Test
    fun parsesHunksWithLineNumbers() {
        val lines = parseUnifiedDiff(patch)
        assertTrue(lines[0] is DiffHunkHeader)
        assertEquals(DiffCode(' ', 1, 1, "line1"), lines[1])
        assertEquals(DiffCode('-', 2, null, "line2"), lines[2])
        assertEquals(DiffCode('+', null, 2, "line2b"), lines[3])
        assertEquals(DiffCode('+', null, 3, "line3"), lines[4])
        assertEquals(DiffCode(' ', 3, 4, "line4"), lines[5])
        assertTrue(lines[6] is DiffNote)
        assertTrue(lines[7] is DiffHunkHeader)
        assertEquals(DiffCode('-', 10, null, "old"), lines[8])
        assertEquals(DiffCode('+', null, 11, "new"), lines[9])
        assertEquals(10, lines.size)
    }

    @Test
    fun skipsFileHeadersAndEmptyPatches() {
        assertTrue(parseUnifiedDiff("").isEmpty())
        assertTrue(parseUnifiedDiff("--- a\n+++ b\n").isEmpty())
    }

    @Test
    fun decodesSparseHistoryLeniently() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val h = json.decodeFromString(
            HistoryResponse.serializer(),
            """{"source":"cloud","savedAt":null,"history":{"branch":null,"prompts":null,"timeline":[{"id":"x","time":"2026-10-03T10:41:00.000Z","kind":"read","title":"a.ts","file":"src/a.ts","status":null,"messageId":null}],"changes":[{"file":"src/b.ts","status":"added","oldPath":null,"additions":3,"deletions":0,"edits":[{"time":"2026-10-03T10:42:00.000Z","tool":"write","additions":3,"deletions":0,"patch":""}]}],"summary":{"durationMs":null},"extra":1},"approvals":[{"id":"ap1","sessionId":"s","permission":"bash","status":"PENDING","patterns":null}]}""",
        )
        assertEquals(false, h.live)
        assertNull(h.history.branch)
        assertTrue(h.history.prompts.isEmpty())
        assertEquals("added", h.history.changes.single().status)
        assertEquals("ap1", h.approvals.single().id)
        assertTrue(h.approvals.single().isPending)
    }

    @Test
    fun touchedFilesMergesTimelineAndChanges() {
        val h = SessionHistory(
            prompts = listOf(HistoryPrompt("m1", "2026-10-03T10:00:00Z", "do it")),
            timeline = listOf(
                TimelineEntry("r", "2026-10-03T10:01:00Z", "read", "a", file = "src/a.ts"),
                TimelineEntry("e", "2026-10-03T10:02:00Z", "edit", "b", file = "src/b.ts", status = "error"),
            ),
            changes = listOf(FileChange("src/b.ts", "modified", additions = 2, deletions = 1)),
        )
        val files = touchedFiles(h).associateBy { it.path }
        assertEquals(listOf("read"), files.getValue("src/a.ts").actions)
        assertEquals(listOf("edited"), files.getValue("src/b.ts").actions)
        assertTrue(files.getValue("src/b.ts").failed)
        assertEquals(1, files.getValue("src/a.ts").firstTurn)
    }

    @Test
    fun agentAlwaysShownAsBambooKit() {
        assertEquals("BambooKit · build", agentLabel("build"))
        assertEquals("BambooKit", agentLabel(null))
        assertEquals("BambooKit", agentLabel("opencode"))
        assertEquals("BambooKit/big-pickle", brandModel("opencode/big-pickle"))
    }

    @Test
    fun durations() {
        assertEquals("45s", formatDuration(45_000))
        assertEquals("12m 30s", formatDuration(750_000))
        assertEquals("1h 04m", formatDuration(3_840_000))
        assertNull(formatDuration(null))
    }
}
