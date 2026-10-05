package com.bambookit.android.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementTiersTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun tiers(vararg unlocked: Boolean, thresholds: List<Double> = listOf(100.0, 1000.0, 10000.0, 50000.0, 100000.0)) =
        StatsFormat.TIERS.mapIndexed { i, n -> AchievementTier(n, thresholds[i], unlocked.getOrElse(i) { false }) }

    private fun tiered(value: Double, unit: String = "lines", unlocked: Int = 0, next: String? = "gold", target: Double = 10000.0) = Achievement(
        id = "code-written", title = "Code Written", emoji = "💻", unit = unit, value = value, progress = value, target = target,
        tiers = tiers(*BooleanArray(5) { it < unlocked }), tier = StatsFormat.TIERS.getOrNull(unlocked - 1), nextTier = next, unlocked = unlocked > 0,
    )

    @Test fun `decodes the tiered API shape`() {
        val raw = """{"timeZone":"UTC","achievements":[{"id":"code-written","emoji":"💻","title":"Code Written","description":"Lines","unit":"lines","trackable":true,
            "value":1240,"tiers":[{"name":"bronze","threshold":100,"unlocked":true,"unlockedAt":"2026-10-01T10:00:00.000Z"},{"name":"silver","threshold":1000,"unlocked":true,"unlockedAt":"2026-10-02T10:00:00.000Z"},
            {"name":"gold","threshold":10000,"unlocked":false,"unlockedAt":null},{"name":"platinum","threshold":50000,"unlocked":false,"unlockedAt":null},{"name":"diamond","threshold":100000,"unlocked":false,"unlockedAt":null}],
            "tier":"silver","nextTier":"gold","progress":1240,"target":10000,"unlocked":true,"unlockedAt":"2026-10-02T10:00:00.000Z"},
            {"id":"github-stars","emoji":"⭐","title":"GitHub Stars Earned","description":"Stars","unit":"stars","trackable":false,"reason":"Needs a GitHub connection — coming later","value":0,
            "tiers":[{"name":"bronze","threshold":1,"unlocked":false,"unlockedAt":null}],"tier":null,"nextTier":"bronze","progress":0,"target":1,"unlocked":false,"unlockedAt":null}],
            "achievementSummary":{"unlocked":23,"total":50,"tiersUnlocked":61,"tiersTotal":250,"points":143,"currentStreak":4,"longestStreak":12}}"""
        val s = json.decodeFromString(ProfileStats.serializer(), raw)
        val a = s.achievements[0]
        assertEquals("silver", a.tier)
        assertEquals(5, a.tiers.size)
        assertTrue(a.tiers[1].unlocked)
        assertEquals("💻", a.emoji)
        assertEquals("1,240 / 10,000 lines → Gold", StatsFormat.tierProgress(a))
        val gh = s.achievements[1]
        assertFalse(gh.trackable)
        assertEquals("Needs a GitHub connection — coming later", StatsFormat.tierProgress(gh))
        assertEquals(0f, StatsFormat.tierFraction(gh), 0f)
        val sum = StatsFormat.summaryOf(s.achievements, s.achievementSummary)
        assertEquals("23 of 50 · 61/250 tiers · 🔥 4-day streak (best 12)", StatsFormat.summaryLine(sum))
        assertEquals("143 points", StatsFormat.pointsText(sum.points))
    }

    @Test fun `old shape without tiers still decodes and renders`() {
        val raw = """{"achievements":[{"id":"first","title":"First session","description":"x","progress":1,"target":1,"unit":"count","unlocked":true,"unlockedAt":null},
            {"id":"marathon","title":"Marathon","progress":3600000,"target":86400000,"unit":"ms","unlocked":false}]}"""
        val s = json.decodeFromString(ProfileStats.serializer(), raw)
        assertNull(s.achievementSummary)
        val old = s.achievements[1]
        assertFalse(StatsFormat.isTiered(old))
        assertTrue(old.trackable)
        assertEquals("1h / 24h", StatsFormat.tierProgress(old))
        assertEquals(1f / 24f, StatsFormat.tierFraction(old), 0.0001f)
        val sum = StatsFormat.summaryOf(s.achievements, null)
        assertEquals(1, sum.unlocked)
        assertEquals("1 of 2", StatsFormat.summaryLine(sum, server = false))
        // Filters work on the old shape too.
        assertEquals(1, s.achievements.count { StatsFormat.matches(it, StatsFormat.Filter.Unlocked) })
        assertEquals(1, s.achievements.count { StatsFormat.matches(it, StatsFormat.Filter.InProgress) })
        assertEquals(0, s.achievements.count { StatsFormat.matches(it, StatsFormat.Filter.NotTracked) })
    }

    @Test fun `medals and tier labels`() {
        assertEquals(listOf("🥉", "🥈", "🥇", "💎", "💠"), StatsFormat.TIERS.map { StatsFormat.medal(it) })
        assertNull(StatsFormat.medal(null))
        assertNull(StatsFormat.medal("wood"))
        assertEquals("Platinum", StatsFormat.tierLabel("platinum"))
        assertEquals(0, StatsFormat.tierRank(null))
        assertEquals(5, StatsFormat.tierRank("diamond"))
    }

    @Test fun `units format as lines, hours, days, nights and counts`() {
        assertEquals("1,240 / 10,000 lines → Gold", StatsFormat.tierProgress(tiered(1240.0, unlocked = 2)))
        assertEquals("12.5 / 25 hours → Silver", StatsFormat.tierProgress(tiered(12.5, "hours", 1, "silver", 25.0)))
        assertEquals("3 / 7 days → Silver", StatsFormat.tierProgress(tiered(3.0, "days", 1, "silver", 7.0)))
        assertEquals("0 / 1 nights → Bronze", StatsFormat.tierProgress(tiered(0.0, "nights", 0, "bronze", 1.0)))
        assertEquals("4 / 10 → Bronze", StatsFormat.tierProgress(tiered(4.0, "count", 0, "bronze", 10.0)))
        assertEquals("1,234.5", StatsFormat.amount(1234.5, "hours"))
        assertEquals("5", StatsFormat.amount(5.0, "hours"))
        assertEquals("0", StatsFormat.amount(-3.0, "lines"))
    }

    @Test fun `all tiers reached shows the value and a full bar`() {
        val done = tiered(120345.0, unlocked = 5, next = null, target = 100000.0)
        assertEquals("120,345 lines · all tiers reached", StatsFormat.tierProgress(done))
        assertEquals(1f, StatsFormat.tierFraction(done), 0f)
        // Progress to the next tier never overflows.
        assertEquals(1f, StatsFormat.tierFraction(tiered(20000.0, unlocked = 2)), 0f)
        assertEquals(0.124f, StatsFormat.tierFraction(tiered(1240.0, unlocked = 2)), 0.0001f)
    }

    @Test fun `filters and sorting`() {
        val gold = tiered(10500.0, unlocked = 3, next = "platinum", target = 50000.0)
        val none = tiered(10.0, unlocked = 0, next = "bronze", target = 100.0)
        val done = tiered(120000.0, unlocked = 5, next = null, target = 100000.0)
        val untracked = Achievement("gh", trackable = false, reason = "GitHub", tiers = tiers())
        val all = listOf(untracked, none, gold, done)
        assertEquals(listOf(gold, done), all.filter { StatsFormat.matches(it, StatsFormat.Filter.Unlocked) })
        assertEquals(listOf(none, gold), all.filter { StatsFormat.matches(it, StatsFormat.Filter.InProgress) })
        assertEquals(listOf(untracked), all.filter { StatsFormat.matches(it, StatsFormat.Filter.NotTracked) })
        assertEquals(listOf(done, gold, none, untracked), StatsFormat.sorted(all))
    }

    @Test fun `streak text`() {
        assertEquals("🔥 4-day streak (best 12)", StatsFormat.streakText(4, 12))
        assertEquals("🔥 12-day streak", StatsFormat.streakText(12, 12))
        assertEquals("🔥 No streak today (best 3)", StatsFormat.streakText(0, 3))
        assertNull(StatsFormat.streakText(0, 0))
        assertEquals("1 point", StatsFormat.pointsText(1))
        assertEquals("23 of 50 · 61/250 tiers", StatsFormat.summaryLine(AchievementSummary(23, 50, 61, 250)))
    }

    @Test fun `achievement events and notifications carry the tier`() {
        val e = json.decodeFromString(AchievementEvent.serializer(), """{"id":"code-written","tier":"gold","title":"💻 Code Written — Gold","unlockedAt":"x"}""")
        assertEquals("🥇 Achievement unlocked: 💻 Code Written — Gold", StatsFormat.eventMessage(e))
        val summary = json.decodeFromString(AchievementEvent.serializer(), """{"id":"summary","title":"You unlocked 12 achievement tiers","count":12}""")
        assertEquals("🏆 You unlocked 12 achievement tiers", StatsFormat.eventMessage(summary))
        // Older events (no tier) keep the plain text; an empty title shows nothing.
        assertEquals("Achievement unlocked: First session", StatsFormat.eventMessage(AchievementEvent("first", "First session")))
        assertNull(StatsFormat.eventMessage(AchievementEvent("x", "")))

        assertEquals("💎 Platinum achievement unlocked", StatsFormat.notificationTitle("achievement.unlocked", "Achievement unlocked", mapOf("achievementId" to "commits", "tier" to "platinum")))
        assertEquals("🏆 Achievements unlocked", StatsFormat.notificationTitle("achievement.unlocked", "Achievements unlocked", mapOf("count" to "12")))
        assertEquals("Achievement unlocked", StatsFormat.notificationTitle("achievement.unlocked", "Achievement unlocked", emptyMap()))
        assertEquals("Session finished", StatsFormat.notificationTitle("session.completed", "Session finished", mapOf("tier" to "gold")))
    }
}
