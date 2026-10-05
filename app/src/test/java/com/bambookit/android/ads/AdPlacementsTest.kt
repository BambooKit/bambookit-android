package com.bambookit.android.ads

import com.bambookit.android.ads.AdPlacements.Position
import com.bambookit.android.ads.AdPlacements.Screen
import com.bambookit.android.ads.AdPlacements.Transition
import com.bambookit.android.data.Plan
import com.bambookit.android.presentation.screens.rewardChipLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPlacementsTest {
    private val free = Plan(plan = "free", ads = true)
    private val pro = Plan(plan = "pro", source = "payment", ads = false)
    private val now = 1_800_000_000_000L
    private val min = 60_000L

    private val contentScreens = listOf(
        Screen.Home, Screen.Projects, Screen.Devices, Screen.Approvals,
        Screen.SessionSummary, Screen.SessionTodos, Screen.SessionPrompts, Screen.SessionTimeline, Screen.SessionChanges,
        Screen.SessionFiles, Screen.SessionProject, Screen.SessionDiagram, Screen.SessionChat,
        Screen.FileViewer, Screen.DiffViewer, Screen.Profile, Screen.AppUpdates,
    )

    @Test
    fun `pro never gets a banner, inline banner or house banner`() {
        for (s in Screen.entries) {
            assertNull(s.name, AdPlacements.banner(s, pro, hasContent = true))
            assertEquals(s.name, 0, AdPlacements.inlineInterval(s, pro, 800))
            assertEquals(AdPolicy.BannerSlot.None, AdPolicy.bannerSlot(pro, s, true, admobLoaded = true, admobFailed = false, elapsedMs = 0))
        }
        // Even a Pro plan the server marks with ads = true.
        assertNull(AdPlacements.banner(Screen.Home, pro.copy(ads = true), hasContent = true))
        assertNull(AdPlacements.banner(Screen.Home, null, hasContent = true))
    }

    @Test
    fun `every content screen has a banner on the free plan`() {
        for (s in contentScreens) assertTrue(s.name, AdPlacements.banner(s, free, hasContent = true) != null)
    }

    @Test
    fun `forbidden screens never show an ad`() {
        val forbidden = listOf(Screen.SignIn, Screen.AppLock, Screen.Pairing, Screen.RequestDialog, Screen.ApprovalDetail, Screen.PlanLimit, Screen.ErrorOnly)
        assertEquals(forbidden.toSet(), AdPlacements.NEVER)
        for (s in forbidden) {
            assertNull(s.name, AdPlacements.banner(s, free, hasContent = true))
            assertEquals(s.name, 0, AdPlacements.inlineInterval(s, free, 800))
            assertFalse(s.name, AdPolicy.bannerAllowed(free, true, s))
            assertEquals(s.name, AdPolicy.BannerSlot.None, AdPolicy.bannerSlot(free, s, true, admobLoaded = true, admobFailed = false, elapsedMs = 0))
        }
    }

    @Test
    fun `no ad on empty, loading or error screens, while locked or behind PLAN_LIMIT`() {
        assertNull(AdPlacements.banner(Screen.Approvals, free, hasContent = false))
        assertNull(AdPlacements.banner(Screen.Home, free, hasContent = true, locked = true))
        assertNull(AdPlacements.banner(Screen.SessionChat, free, hasContent = true, suppressed = true))
    }

    @Test
    fun `banners go to the top where the bottom has buttons or inputs`() {
        for (s in listOf(Screen.SessionChat, Screen.SessionSummary, Screen.SessionTimeline, Screen.Approvals, Screen.DiffViewer, Screen.FileViewer)) {
            assertEquals(s.name, Position.Top, AdPlacements.banner(s, free, hasContent = true))
        }
        for (s in listOf(Screen.Home, Screen.Projects, Screen.Devices, Screen.Profile, Screen.AppUpdates)) {
            assertEquals(s.name, Position.Bottom, AdPlacements.banner(s, free, hasContent = true))
        }
    }

    @Test
    fun `inline banners after every 8th item, at most one per screen height, never after the last`() {
        assertEquals(8, AdPlacements.inlineInterval(Screen.Projects, free, screenHeightDp = 500))
        // A tall screen fits more rows: the interval grows so two inline banners are never on one screen.
        assertEquals(12, AdPlacements.inlineInterval(Screen.Projects, free, screenHeightDp = 864))
        assertEquals(0, AdPlacements.inlineInterval(Screen.Devices, free, 800))
        val after = (0 until 20).filter { AdPlacements.inlineAfter(it, 20, 8) }
        assertEquals(listOf(7, 15), after)
        // Exactly 8 items: no banner after the last one.
        assertFalse(AdPlacements.inlineAfter(7, 8, 8))
        assertFalse(AdPlacements.inlineAfter(7, 20, 0))
    }

    @Test
    fun `session tabs map to their screens`() {
        assertEquals(Screen.SessionChat, AdPlacements.sessionScreen("Chat"))
        assertEquals(Screen.SessionDiagram, AdPlacements.sessionScreen("Diagram"))
        assertEquals(Screen.ErrorOnly, AdPlacements.sessionScreen("Nope"))
    }

    private fun ctx(t: Transition, shown: List<Long> = emptyList(), pending: Int = 0, locked: Boolean = false, sinceStart: Long = 10 * min) =
        AdPolicy.InterstitialContext(free, true, pending, locked, sinceStart, now, shown, t)

    @Test
    fun `every transition point shares one frequency cap`() {
        for (t in Transition.entries) {
            assertTrue(AdPlacements.interstitialEnabled(t))
            assertNull(t.name, AdPolicy.interstitialBlock(ctx(t)))
        }
        // One shown when leaving the diff viewer blocks leaving the session and Projects → Home for 15 minutes.
        val justShown = listOf(now - 2 * min)
        assertEquals(AdPolicy.Block.TooSoon, AdPolicy.interstitialBlock(ctx(Transition.LeftSession, justShown)))
        assertEquals(AdPolicy.Block.TooSoon, AdPolicy.interstitialBlock(ctx(Transition.ProjectsToHome, justShown)))
        assertNull(AdPolicy.interstitialBlock(ctx(Transition.LeftFileViewer, listOf(now - 15 * min))))
        // Four today, from any transition: the daily cap applies to all of them.
        val four = listOf(now - 10 * 60 * min, now - 6 * 60 * min, now - 3 * 60 * min, now - 60 * min)
        for (t in Transition.entries) assertEquals(t.name, AdPolicy.Block.DailyCap, AdPolicy.interstitialBlock(ctx(t, four)))
    }

    @Test
    fun `no interstitial at any transition with a request waiting, while locked or at launch`() {
        for (t in Transition.entries) {
            assertEquals(AdPolicy.Block.RequestPending, AdPolicy.interstitialBlock(ctx(t, pending = 1)))
            assertEquals(AdPolicy.Block.Locked, AdPolicy.interstitialBlock(ctx(t, locked = true)))
            assertEquals(AdPolicy.Block.AppJustStarted, AdPolicy.interstitialBlock(ctx(t, sinceStart = 3_000)))
        }
    }

    @Test
    fun `reward chip label fits the top bar`() {
        assertEquals("Watch ad · 24 h Pro", rewardChipLabel(24, 411))
        assertEquals("24 h Pro", rewardChipLabel(24, 360))
    }
}
