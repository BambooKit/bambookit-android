package com.bambookit.android.ads

import com.bambookit.android.ads.AdPolicy.BannerSlot
import com.bambookit.android.ads.AdPolicy.Placement
import com.bambookit.android.data.BillingProduct
import com.bambookit.android.data.Plan
import com.bambookit.android.presentation.screens.houseBannerPrice
import com.bambookit.android.presentation.screens.houseBannerText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseAdTest {
    private val free = Plan(plan = "free", ads = true)
    private val pro = Plan(plan = "pro", ads = false)
    private val late = AdPolicy.HOUSE_FALLBACK_MS

    private fun slot(
        plan: Plan? = free, placement: Placement? = Placement.Home, ready: Boolean = true,
        loaded: Boolean = false, failed: Boolean = false, elapsed: Long = 0,
    ) = AdPolicy.bannerSlot(plan, placement, ready, loaded, failed, elapsed)

    @Test
    fun `pro, unknown plan or no placement shows nothing`() {
        assertEquals(BannerSlot.None, slot(plan = pro, elapsed = late))
        assertEquals(BannerSlot.None, slot(plan = null, elapsed = late))
        assertEquals(BannerSlot.None, slot(placement = null, elapsed = late))
    }

    @Test
    fun `loaded admob banner wins`() {
        assertEquals(BannerSlot.AdMob, slot(loaded = true))
        assertEquals(BannerSlot.AdMob, slot(loaded = true, elapsed = late))
    }

    @Test
    fun `no fill switches to the house banner at once`() {
        assertEquals(BannerSlot.House, slot(failed = true))
    }

    @Test
    fun `slow admob waits five seconds then shows the house banner`() {
        assertEquals(BannerSlot.Pending, slot(elapsed = 0))
        assertEquals(BannerSlot.Pending, slot(elapsed = late - 1))
        assertEquals(BannerSlot.House, slot(elapsed = late))
    }

    @Test
    fun `sdk not started or no consent falls back to house after the grace period`() {
        assertEquals(BannerSlot.Pending, slot(ready = false, elapsed = 0))
        assertEquals(BannerSlot.House, slot(ready = false, elapsed = late))
    }

    @Test
    fun `free plan without server ads still gets the house banner`() {
        assertEquals(BannerSlot.House, slot(plan = free.copy(ads = false), elapsed = 0))
    }

    @Test
    fun `no house interstitial`() {
        assertFalse(AdPolicy.showHouseInterstitial())
    }

    @Test
    fun `house banner text and price`() {
        assertEquals("₹199/mo", houseBannerPrice(emptyList()))
        assertEquals("Go Pro — unlimited phone chat, 5 PCs, no ads · ₹199/mo", houseBannerText(emptyList()))
        val products = listOf(
            BillingProduct("y", "Pro yearly", 199_000, "INR", "year"),
            BillingProduct("m", "Pro monthly", 19_900, "INR", "month"),
        )
        assertEquals("₹199/mo", houseBannerPrice(products))
        assertEquals("$4.99/mo", houseBannerPrice(listOf(BillingProduct("m", "Pro", 499, "usd", "month"))))
    }

    @Test
    fun `diagnostics line`() {
        val d = AdPolicy.AdDiag(
            consent = AdPolicy.Consent.Allowed, sdkStarted = true,
            banner = AdPolicy.SlotStatus.failed(3), interstitial = AdPolicy.SlotStatus(AdPolicy.LoadState.Loading),
        )
        assertEquals(
            "Ads: banner not available (AdMob no fill) · interstitial loading · rewarded not requested",
            AdPolicy.describe(free, d),
        )
        assertEquals("Ads: off (Pro)", AdPolicy.describe(pro, d))
        assertEquals("Ads: banner error 0 · interstitial loading · rewarded not requested · consent form not set up in AdMob",
            AdPolicy.describe(free, d.copy(banner = AdPolicy.SlotStatus.failed(0), consentNote = "consent form not set up in AdMob")))
        assertTrue(AdPolicy.describe(free, AdPolicy.AdDiag(consent = AdPolicy.Consent.Gathering)).contains("starting"))
        assertTrue(AdPolicy.describe(free, AdPolicy.AdDiag(consent = AdPolicy.Consent.Denied)).contains("privacy"))
        assertEquals(AdPolicy.LoadState.NoFill, AdPolicy.SlotStatus.failed(AdPolicy.NO_FILL).state)
    }
}
