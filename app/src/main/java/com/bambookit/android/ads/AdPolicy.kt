package com.bambookit.android.ads

import com.bambookit.android.data.Plan

/**
 * When ads may be shown. Pure functions (unit tested): the SDK is only touched by [AdsManager].
 *
 * - Ads only when the server says so (plan.ads, the Free plan). Pro: nothing is loaded at all.
 * - Banners where [AdPlacements] allows them (never on sign-in, App lock, pairing, request answers, PLAN_LIMIT).
 * - Interstitials only at the natural breaks in [AdPlacements.Transition] (leaving a session, leaving the file or
 *   diff viewer, Projects → Home), all sharing one cap: at most once every 15 minutes and 4 times a day, never
 *   while an approval or question is waiting, never while App lock is shown, and never right after the app started.
 */
object AdPolicy {
    const val INTERSTITIAL_MIN_INTERVAL_MS = 15 * 60_000L
    const val INTERSTITIAL_MAX_PER_DAY = 4
    const val DAY_MS = 24 * 60 * 60_000L
    /** No interstitial in the first minute after the app started. */
    const val APP_START_QUIET_MS = 60_000L

    /** Ads (and any ad loading) are allowed only on a plan the server marks with ads = true, and never on Pro. */
    fun adsEnabled(plan: Plan?): Boolean = plan != null && plan.ads && !plan.isPro

    fun bannerAllowed(plan: Plan?, canRequestAds: Boolean, placement: AdPlacements.Screen?): Boolean =
        placement != null && AdPlacements.configOf(placement) != null && canRequestAds && adsEnabled(plan)

    /** Why an interstitial is not shown (null = it may be shown). */
    enum class Block { NoAds, PlacementOff, NoConsent, RequestPending, Locked, AppJustStarted, TooSoon, DailyCap }

    data class InterstitialContext(
        val plan: Plan?,
        val canRequestAds: Boolean,
        /** Approvals and questions waiting for the user. */
        val pendingRequests: Int,
        val locked: Boolean,
        /** Time since the app process started (ms). */
        val sinceAppStartMs: Long,
        /** Wall-clock now and earlier interstitials (epoch ms). */
        val now: Long,
        val shownAt: List<Long>,
        /** Where it would be shown. */
        val transition: AdPlacements.Transition = AdPlacements.Transition.LeftSession,
    )

    fun interstitialBlock(c: InterstitialContext): Block? = when {
        !adsEnabled(c.plan) -> Block.NoAds
        !AdPlacements.interstitialEnabled(c.transition) -> Block.PlacementOff
        !c.canRequestAds -> Block.NoConsent
        c.pendingRequests > 0 -> Block.RequestPending
        c.locked -> Block.Locked
        c.sinceAppStartMs < APP_START_QUIET_MS -> Block.AppJustStarted
        else -> frequencyBlock(c.now, c.shownAt)
    }

    fun canShowInterstitial(c: InterstitialContext): Boolean = interstitialBlock(c) == null

    /** The frequency cap alone: 15 minutes between interstitials and at most 4 in any 24 hours. */
    fun frequencyBlock(now: Long, shownAt: List<Long>): Block? {
        val last = shownAt.maxOrNull()
        if (last != null && now - last in 0 until INTERSTITIAL_MIN_INTERVAL_MS) return Block.TooSoon
        if (shownAt.count { now - it in 0 until DAY_MS } >= INTERSTITIAL_MAX_PER_DAY) return Block.DailyCap
        return null
    }

    /** The stored history with entries older than a day dropped. */
    fun prune(now: Long, shownAt: List<Long>): List<Long> = shownAt.filter { now - it in 0 until DAY_MS }.sorted()

    fun encode(shownAt: List<Long>): String = shownAt.joinToString(",")
    fun decode(raw: String?): List<Long> = raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    // ------------------------------------------------------------------ house ads (fallback)

    /** An AdMob banner that hasn't loaded within this time is replaced by the BambooKit house banner. */
    const val HOUSE_FALLBACK_MS = 5_000L
    /** AdMob's "no fill" (ERROR_CODE_NO_FILL): nothing to show, e.g. while the AdMob account is being verified. */
    const val NO_FILL = 3

    const val REWARD_NO_FILL_MESSAGE =
        "No ad available right now — ads are limited while BambooKit's AdMob account is being verified. Try later."

    /** What the banner slot on Home / Projects shows. */
    enum class BannerSlot {
        /** Nothing (Pro, plan unknown, or another tab). */
        None,
        /** The AdMob banner is loading: the slot keeps its height, empty, for up to [HOUSE_FALLBACK_MS]. */
        Pending,
        /** A loaded AdMob banner. */
        AdMob,
        /** BambooKit's own "Go Pro" banner: AdMob had nothing (no fill, error, no consent or too slow). */
        House,
    }

    /**
     * The banner slot for the Free plan. House banners are BambooKit's own promotion (no tracking), so they are
     * shown even when AdMob ads are not allowed (no consent, ads = false); AdMob is used only when [bannerAllowed].
     */
    fun bannerSlot(
        plan: Plan?, placement: AdPlacements.Screen?, canRequestAds: Boolean,
        admobLoaded: Boolean, admobFailed: Boolean, elapsedMs: Long,
    ): BannerSlot = when {
        plan == null || plan.isPro || placement == null || AdPlacements.configOf(placement) == null -> BannerSlot.None
        !bannerAllowed(plan, canRequestAds, placement) ->
            // The SDK may still be starting (consent check): give it the same grace period.
            if (adsEnabled(plan) && elapsedMs < HOUSE_FALLBACK_MS) BannerSlot.Pending else BannerSlot.House
        admobLoaded -> BannerSlot.AdMob
        admobFailed || elapsedMs >= HOUSE_FALLBACK_MS -> BannerSlot.House
        else -> BannerSlot.Pending
    }

    /** Interstitials have no house fallback: when AdMob has none at the allowed moment, nothing is shown. */
    fun showHouseInterstitial(): Boolean = false

    // ------------------------------------------------------------------ diagnostics

    /** One AdMob slot's last state, for the diagnostics line in Profile. */
    enum class LoadState { Idle, Loading, Loaded, Shown, NoFill, Failed }

    data class SlotStatus(val state: LoadState = LoadState.Idle, val code: Int? = null) {
        companion object {
            fun failed(code: Int) = SlotStatus(if (code == NO_FILL) LoadState.NoFill else LoadState.Failed, code)
        }
    }

    enum class Consent { Unknown, Gathering, Allowed, Denied }

    data class AdDiag(
        val consent: Consent = Consent.Unknown,
        /** A non-fatal consent problem, e.g. no consent form set up in AdMob. */
        val consentNote: String? = null,
        val sdkStarted: Boolean = false,
        val banner: SlotStatus = SlotStatus(),
        val interstitial: SlotStatus = SlotStatus(),
        val rewarded: SlotStatus = SlotStatus(),
    )

    fun slotText(s: SlotStatus): String = when (s.state) {
        LoadState.Idle -> "not requested"
        LoadState.Loading -> "loading"
        LoadState.Loaded -> "ready"
        LoadState.Shown -> "shown"
        LoadState.NoFill -> "not available (AdMob no fill)"
        LoadState.Failed -> "error ${s.code ?: "?"}"
    }

    /** "Ads: banner not available (AdMob no fill) · interstitial loading · rewarded not requested". */
    fun describe(plan: Plan?, d: AdDiag): String {
        val body = when {
            plan == null -> "plan not loaded"
            plan.isPro -> "off (Pro)"
            !plan.ads -> "off for this account"
            d.consent == Consent.Denied -> "not allowed by your privacy choices (BambooKit banner shown instead)"
            !d.sdkStarted -> if (d.consent == Consent.Gathering || d.consent == Consent.Unknown) "starting (checking consent)" else "starting"
            else -> "banner ${slotText(d.banner)} · interstitial ${slotText(d.interstitial)} · rewarded ${slotText(d.rewarded)}"
        }
        return "Ads: $body" + (d.consentNote?.let { " · $it" } ?: "")
    }
}
