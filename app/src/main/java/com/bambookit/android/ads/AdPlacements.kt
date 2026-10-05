package com.bambookit.android.ads

import com.bambookit.android.data.Plan
import kotlin.math.ceil

/**
 * Where ads may appear, in one place so the placements can be tuned later. Pure functions (unit tested).
 *
 * AdMob policy rules this table follows (breaking them gets the AdMob account banned):
 * - Pro: no ads anywhere. Free: at most one fixed banner per screen, plus inline banners in long lists spaced at
 *   least one screen height apart.
 * - A banner never sits directly against buttons or inputs (accidental clicks): on screens whose bottom has a chat
 *   box, Continue on PC, approval buttons or a mode switch the fixed banner goes at the TOP of the content, under
 *   the top bar / tabs, with a gap; at the bottom only where the scrolling content ends above it.
 * - No ads on screens without publisher content: sign-in, App lock, pairing / QR scanner, the request (permission
 *   and question) answers, the PLAN_LIMIT dialog (only its rewarded button), error-only, loading and empty screens.
 * - Interstitials only at natural breaks ([Transition]), all sharing one frequency cap ([AdPolicy]).
 */
object AdPlacements {
    /** Screens (and parts of screens) of the app. */
    enum class Screen {
        Home, Projects, Devices, Approvals,
        SessionSummary, SessionTodos, SessionPrompts, SessionTimeline, SessionChanges, SessionFiles,
        SessionProject, SessionDiagram, SessionChat,
        FileViewer, DiffViewer, Profile, AppUpdates,

        // Never any ad (see [NEVER]).
        SignIn, AppLock, Pairing, RequestDialog, ApprovalDetail, PlanLimit, ErrorOnly,
    }

    /** Where the fixed banner sits on a screen. */
    enum class Position { Top, Bottom }

    /**
     * One screen's placement. [position] null: no fixed banner. [inlineEvery] > 0: an inline banner after every
     * that many list items (raised so there is never more than one per screen height, see [inlineInterval]).
     */
    data class Config(val enabled: Boolean = true, val position: Position? = null, val inlineEvery: Int = 0)

    /** Screens that never show an ad of any kind, whatever [config] says. */
    val NEVER: Set<Screen> = setOf(
        Screen.SignIn, Screen.AppLock, Screen.Pairing, Screen.RequestDialog, Screen.ApprovalDetail, Screen.PlanLimit, Screen.ErrorOnly,
    )

    /** Inline banners in lists: after every 8th item. */
    const val INLINE_EVERY = 8

    /** The smallest list row (a session card) in dp, used to keep inline banners a screen height apart. */
    const val LIST_ITEM_MIN_DP = 72

    /** Gap between a top banner and the controls above / below it, in dp. */
    const val TOP_GAP_DP = 8

    val config: Map<Screen, Config> = mapOf(
        // Lists that scroll above the bottom navigation: the banner sits at the bottom, under the list.
        Screen.Home to Config(position = Position.Bottom, inlineEvery = INLINE_EVERY),
        Screen.Projects to Config(position = Position.Bottom, inlineEvery = INLINE_EVERY),
        Screen.Devices to Config(position = Position.Bottom),
        Screen.Profile to Config(position = Position.Bottom),
        Screen.AppUpdates to Config(position = Position.Bottom),
        // Approve / Deny buttons on every card: top, away from the thumb.
        Screen.Approvals to Config(position = Position.Top),
        // Session screens: chat box, Continue on PC or the live activity bar at the bottom → top, under the tabs.
        Screen.SessionSummary to Config(position = Position.Top),
        Screen.SessionTodos to Config(position = Position.Top),
        Screen.SessionPrompts to Config(position = Position.Top),
        Screen.SessionTimeline to Config(position = Position.Top),
        Screen.SessionChanges to Config(position = Position.Top),
        Screen.SessionFiles to Config(position = Position.Top),
        Screen.SessionProject to Config(position = Position.Top),
        Screen.SessionDiagram to Config(position = Position.Top),
        Screen.SessionChat to Config(position = Position.Top),
        // File and diff viewers: top, under the header (the bottom is the code, which is selectable / scrollable).
        Screen.FileViewer to Config(position = Position.Top),
        Screen.DiffViewer to Config(position = Position.Top),
    )

    fun configOf(screen: Screen): Config? = if (screen in NEVER) null else config[screen]?.takeIf { it.enabled }

    /** Ads of any kind on this plan: Free only (a known plan that is not Pro). House banners count too. */
    fun freePlan(plan: Plan?): Boolean = plan != null && !plan.isPro

    /**
     * The fixed banner's position on [screen], or null for none: Pro or unknown plan, a forbidden screen,
     * no publisher content ([hasContent] false: loading, error-only or empty), App lock, or [suppressed]
     * (a dialog such as PLAN_LIMIT covers the screen).
     */
    fun banner(screen: Screen, plan: Plan?, hasContent: Boolean, locked: Boolean = false, suppressed: Boolean = false): Position? {
        if (!freePlan(plan) || !hasContent || locked || suppressed) return null
        return configOf(screen)?.position
    }

    /**
     * Items between inline banners on [screen] (0 = none): at least [Config.inlineEvery], raised so that inline
     * banners are at least one screen height ([screenHeightDp]) apart.
     */
    fun inlineInterval(screen: Screen, plan: Plan?, screenHeightDp: Int, itemMinDp: Int = LIST_ITEM_MIN_DP): Int {
        if (!freePlan(plan)) return 0
        val every = configOf(screen)?.inlineEvery ?: 0
        if (every <= 0) return 0
        val perScreen = if (itemMinDp > 0) ceil(screenHeightDp.toDouble() / itemMinDp).toInt() else 0
        return maxOf(every, perScreen)
    }

    /**
     * An inline banner after the item at [index] (0-based, of [count]): after every [interval]th item, never after
     * the last one (so it never ends up against the fixed banner or the bottom navigation).
     */
    fun inlineAfter(index: Int, count: Int, interval: Int): Boolean =
        interval > 0 && index in 0 until count - 1 && (index + 1) % interval == 0

    // ------------------------------------------------------------------ interstitials

    /** Natural breaks where an interstitial may be shown (all share [AdPolicy]'s frequency cap). */
    enum class Transition { LeftSession, LeftFileViewer, ProjectsToHome }

    val interstitials: Map<Transition, Boolean> = mapOf(
        Transition.LeftSession to true,
        Transition.LeftFileViewer to true,
        Transition.ProjectsToHome to true,
    )

    fun interstitialEnabled(t: Transition): Boolean = interstitials[t] == true

    /** Session tab name (SessionTabId.name) → screen. */
    fun sessionScreen(tab: String): Screen = when (tab) {
        "Summary" -> Screen.SessionSummary
        "Todos" -> Screen.SessionTodos
        "Prompts" -> Screen.SessionPrompts
        "Timeline" -> Screen.SessionTimeline
        "Changes" -> Screen.SessionChanges
        "Files" -> Screen.SessionFiles
        "Project" -> Screen.SessionProject
        "Diagram" -> Screen.SessionDiagram
        "Chat" -> Screen.SessionChat
        else -> Screen.ErrorOnly
    }
}
