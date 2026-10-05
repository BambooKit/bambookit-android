package com.bambookit.android.ads

import com.bambookit.android.data.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {
    private val free = Plan(plan = "free", ads = true)
    private val pro = Plan(plan = "pro", source = "payment", ads = false)
    private val now = 1_800_000_000_000L
    private val min = 60_000L

    private fun ctx(
        plan: Plan? = free, canRequest: Boolean = true, pending: Int = 0, locked: Boolean = false,
        sinceStart: Long = 10 * min, shown: List<Long> = emptyList(),
    ) = AdPolicy.InterstitialContext(plan, canRequest, pending, locked, sinceStart, now, shown)

    @Test
    fun `pro gets no ads at all`() {
        assertFalse(AdPolicy.adsEnabled(pro))
        assertFalse(AdPolicy.bannerAllowed(pro, true, AdPlacements.Screen.Home))
        assertEquals(AdPolicy.Block.NoAds, AdPolicy.interstitialBlock(ctx(plan = pro)))
        // Even if a server said ads = true for a Pro plan, Pro wins.
        assertFalse(AdPolicy.adsEnabled(pro.copy(ads = true)))
    }

    @Test
    fun `unknown plan or ads false means no ads`() {
        assertFalse(AdPolicy.adsEnabled(null))
        assertFalse(AdPolicy.adsEnabled(free.copy(ads = false)))
        assertFalse(AdPolicy.bannerAllowed(null, true, AdPlacements.Screen.Projects))
    }

    @Test
    fun `free plan banners need consent and an allowed placement`() {
        assertTrue(AdPolicy.bannerAllowed(free, true, AdPlacements.Screen.Home))
        assertTrue(AdPolicy.bannerAllowed(free, true, AdPlacements.Screen.Projects))
        assertFalse(AdPolicy.bannerAllowed(free, false, AdPlacements.Screen.Home))
        assertFalse(AdPolicy.bannerAllowed(free, true, null))
    }

    @Test
    fun `no interstitial while an approval or question is pending`() {
        assertEquals(AdPolicy.Block.RequestPending, AdPolicy.interstitialBlock(ctx(pending = 1)))
        assertNull(AdPolicy.interstitialBlock(ctx(pending = 0)))
    }

    @Test
    fun `no interstitial while locked, without consent or right after start`() {
        assertEquals(AdPolicy.Block.Locked, AdPolicy.interstitialBlock(ctx(locked = true)))
        assertEquals(AdPolicy.Block.NoConsent, AdPolicy.interstitialBlock(ctx(canRequest = false)))
        assertEquals(AdPolicy.Block.AppJustStarted, AdPolicy.interstitialBlock(ctx(sinceStart = 5_000)))
    }

    @Test
    fun `at most one interstitial every 15 minutes`() {
        assertEquals(AdPolicy.Block.TooSoon, AdPolicy.interstitialBlock(ctx(shown = listOf(now - 14 * min))))
        assertNull(AdPolicy.interstitialBlock(ctx(shown = listOf(now - 15 * min))))
    }

    @Test
    fun `at most four interstitials a day`() {
        val four = listOf(now - 20 * 60 * min, now - 10 * 60 * min, now - 5 * 60 * min, now - 60 * min)
        assertEquals(AdPolicy.Block.DailyCap, AdPolicy.interstitialBlock(ctx(shown = four)))
        assertNull(AdPolicy.interstitialBlock(ctx(shown = four.drop(1))))
        // The oldest one is more than a day old: it no longer counts.
        val old = listOf(now - 25 * 60 * min) + four.drop(1)
        assertNull(AdPolicy.interstitialBlock(ctx(shown = old)))
    }

    @Test
    fun `history survives encoding and drops entries older than a day`() {
        val list = listOf(now - 25 * 60 * min, now - 60 * min, now)
        val decoded = AdPolicy.decode(AdPolicy.encode(list))
        assertEquals(list, decoded)
        assertEquals(listOf(now - 60 * min, now), AdPolicy.prune(now, decoded))
        assertEquals(emptyList<Long>(), AdPolicy.decode(null))
        assertEquals(listOf(5L), AdPolicy.decode("x,5,"))
    }
}
