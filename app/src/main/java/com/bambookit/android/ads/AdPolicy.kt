package com.bambookit.android.ads

import com.bambookit.android.data.Plan

/**
 * When ads may be shown. Pure functions (unit tested): the SDK is only touched by [AdsManager].
 *
 * - Ads only when the server says so (plan.ads, the Free plan). Pro: nothing is loaded at all.
 * - Banners only on Home and Projects.
 * - Interstitials only when leaving a session back to the list, at most once every 15 minutes and 4 times a
 *   day, never while an approval or question is waiting, never while App lock is shown, and never right after
 *   the app started.
 */
object AdPolicy {
    const val INTERSTITIAL_MIN_INTERVAL_MS = 15 * 60_000L
    const val INTERSTITIAL_MAX_PER_DAY = 4
    const val DAY_MS = 24 * 60 * 60_000L
    /** No interstitial in the first minute after the app started. */
    const val APP_START_QUIET_MS = 60_000L

    /** Where a banner may appear. */
    enum class Placement { Home, Projects }

    /** Ads (and any ad loading) are allowed only on a plan the server marks with ads = true, and never on Pro. */
    fun adsEnabled(plan: Plan?): Boolean = plan != null && plan.ads && !plan.isPro

    fun bannerAllowed(plan: Plan?, canRequestAds: Boolean, placement: Placement?): Boolean =
        placement != null && canRequestAds && adsEnabled(plan)

    /** Why an interstitial is not shown (null = it may be shown). */
    enum class Block { NoAds, NoConsent, RequestPending, Locked, AppJustStarted, TooSoon, DailyCap }

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
    )

    fun interstitialBlock(c: InterstitialContext): Block? = when {
        !adsEnabled(c.plan) -> Block.NoAds
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
}
