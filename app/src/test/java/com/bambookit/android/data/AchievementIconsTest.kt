package com.bambookit.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AchievementIconsTest {
    /** The 50 ids of ACHIEVEMENTS in bambookit-api src/modules/stats.ts. */
    private val apiIds = listOf(
        "coding-streak", "code-written", "code-changes", "files-changed", "projects-built", "ai-sessions", "prompts-sent",
        "tasks-completed", "bugs-fixed", "bug-hunter", "tests-run", "tests-passed", "deployments", "cloud-builder", "commits",
        "branches-created", "merges", "approvals", "tool-calls", "agent-tasks", "multi-agent", "integrations", "mcp-tools",
        "terminal-commands", "packages-installed", "long-sessions", "coding-time", "night-coder", "fast-fix", "one-shot-fix",
        "tasks-without-retry", "successful-sessions", "code-cleanup", "code-deleted", "files-created", "files-deleted",
        "refactors", "projects-managed", "open-source", "github-stars", "contributions", "pull-requests", "production-fixes",
        "devices-connected", "secure-actions", "code-reviews", "documentation", "experiments", "speed-builder", "bambookit-master",
    )

    @Test fun `all 50 API achievements have their own icon`() {
        assertEquals(50, apiIds.toSet().size)
        for (id in apiIds) assertNotEquals("no icon for $id", AchievementIcon.Award, AchievementIcon.forId(id))
        assertEquals(apiIds.toSet(), AchievementIcon.BY_ID.keys)
        // Every id has a different icon, and every icon but the fallback is used.
        assertEquals(50, apiIds.map { AchievementIcon.forId(it) }.toSet().size)
        assertEquals(AchievementIcon.entries.toSet() - AchievementIcon.Award, AchievementIcon.BY_ID.values.toSet())
    }

    @Test fun `shared mapping spot checks`() {
        assertEquals(AchievementIcon.Flame, AchievementIcon.forId("coding-streak"))
        assertEquals(AchievementIcon.Crown, AchievementIcon.forId("bambookit-master"))
        assertEquals(AchievementIcon.Trophy, AchievementIcon.forId("tasks-without-retry"))
        assertEquals(AchievementIcon.PullRequest, AchievementIcon.forId("pull-requests"))
        assertEquals(AchievementIcon.Code, AchievementIcon.forId(" Code-Written "))
    }

    @Test fun `unknown ids fall back to the award icon`() {
        assertEquals(AchievementIcon.Award, AchievementIcon.forId("first-session"))
        assertEquals(AchievementIcon.Award, AchievementIcon.forId("summary"))
        assertEquals(AchievementIcon.Award, AchievementIcon.forId(""))
        assertEquals(AchievementIcon.Award, AchievementIcon.forId(null))
    }

    /** When the API repo sits next to this one, its list must match (catches ids added there later). */
    @Test fun `matches the API source when it is checked out`() {
        val src = listOf("../bambookit-api/src/modules/stats.ts", "../../bambookit-api/src/modules/stats.ts").map(::File).firstOrNull { it.isFile }
            ?: return
        val text = src.readText()
        val block = text.substringAfter("export const ACHIEVEMENTS").substringBefore("\n];")
        val ids = Regex("""\bid:\s*'([a-z0-9-]+)'""").findAll(block).map { it.groupValues[1] }.toMutableList()
        if (Regex("""\bid:\s*MASTER_ID""").containsMatchIn(block)) {
            ids += Regex("""MASTER_ID\s*=\s*'([a-z0-9-]+)'""").find(text)!!.groupValues[1]
        }
        assertEquals(apiIds.toSet(), ids.toSet())
        for (id in ids) assertTrue("no icon for $id", AchievementIcon.forId(id) != AchievementIcon.Award)
    }

    @Test fun `event titles lose the emoji and the tier`() {
        assertEquals("Code Written", StatsFormat.plainTitle("💻 Code Written — Gold", "gold"))
        assertEquals("Code Reviews — Silver", StatsFormat.plainTitle("🧑‍💻 Code Reviews — Silver"))
        assertEquals("You unlocked 5 achievement tiers", StatsFormat.plainTitle("You unlocked 5 achievement tiers"))
        assertEquals("🏆", StatsFormat.plainTitle("🏆"))
    }
}
