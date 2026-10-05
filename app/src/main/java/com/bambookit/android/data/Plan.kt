package com.bambookit.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Daily limits of a plan. Null = unlimited. */
@Serializable
data class PlanLimits(
    val phoneMessagesPerDay: Int? = null,
    val phoneSessionsPerDay: Int? = null,
    val desktops: Int = 0,
)

/** What this account used today (phone messages and new sessions) and its paired desktops. */
@Serializable
data class PlanUsage(
    val phoneMessagesToday: Int = 0,
    val phoneSessionsToday: Int = 0,
    val desktops: Int = 0,
)

/** Rewarded ads: [todayCount] watched today out of [maxPerDay]; each one gives [hours] of Pro. */
@Serializable
data class PlanRewards(
    val todayCount: Int = 0,
    val maxPerDay: Int = 0,
    val hours: Int = 24,
)

/**
 * GET /v1/me/plan (also the payload of the realtime event plan.updated). [ads] is the server's decision:
 * ads are shown only when it is true (the Free plan). Missing fields decode to "no ads", so an older or
 * newer API never makes the app show ads by mistake.
 */
@Serializable
data class Plan(
    val plan: String = "free",
    /** How Pro was obtained: payment, reward or admin (null on the Free plan). */
    val source: String? = null,
    val proUntil: String? = null,
    val limits: PlanLimits = PlanLimits(),
    val usage: PlanUsage = PlanUsage(),
    val resetsAt: String? = null,
    val ads: Boolean = false,
    val rewards: PlanRewards = PlanRewards(),
) {
    val isPro get() = plan == "pro"
    /** Rewarded ads that can still be watched today. */
    val rewardsLeft get() = (rewards.maxPerDay - rewards.todayCount).coerceAtLeast(0)
    val canWatchReward get() = !isPro && ads && rewardsLeft > 0

    /** The day's counters were reset: [resetsAt] is in the past at [nowMs] (the plan is then re-read). */
    fun resetDue(nowMs: Long): Boolean = resetsAtMs()?.let { nowMs >= it } ?: false

    fun resetsAtMs(): Long? = resetsAt?.let { v ->
        runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli() }.getOrNull()
    }

    /** Phone messages left today (null = unlimited). After the reset time the full limit is assumed until re-read. */
    fun messagesLeft(nowMs: Long = System.currentTimeMillis()): Int? =
        Quota.left(limits.phoneMessagesPerDay, usage.phoneMessagesToday, isPro, resetDue(nowMs))

    /** New sessions from the phone left today (null = unlimited). */
    fun sessionsLeft(nowMs: Long = System.currentTimeMillis()): Int? =
        Quota.left(limits.phoneSessionsPerDay, usage.phoneSessionsToday, isPro, resetDue(nowMs))

    /** Sending from the phone is locked until the reset (Free plan, daily messages used up). */
    fun messagesLocked(nowMs: Long = System.currentTimeMillis()): Boolean = messagesLeft(nowMs) == 0
    fun sessionsLocked(nowMs: Long = System.currentTimeMillis()): Boolean = sessionsLeft(nowMs) == 0

    /** Optimistic usage after a message the API accepted (reconciled with GET /v1/me/plan and plan.updated). */
    fun withMessageSent(): Plan = copy(usage = usage.copy(phoneMessagesToday = usage.phoneMessagesToday + 1))
    fun withSessionCreated(): Plan = copy(usage = usage.copy(phoneSessionsToday = usage.phoneSessionsToday + 1))

    /** A local PLAN_LIMIT (the dialog) for a locked button, without asking the API first. */
    fun localLimit(sessions: Boolean): PlanLimitError {
        val max = if (sessions) limits.phoneSessionsPerDay else limits.phoneMessagesPerDay
        val used = if (sessions) usage.phoneSessionsToday else usage.phoneMessagesToday
        return PlanLimitError(
            "Daily free limit reached", if (sessions) "phoneSessionsPerDay" else "phoneMessagesPerDay", max, if (max != null) used.coerceAtMost(max) else used, resetsAt,
        )
    }
}

/** Remaining-count rules shared by the composer, the new-session buttons and Profile. Pure (unit tested). */
object Quota {
    /** At or below this many left, the count is shown in the warning color. */
    const val WARN_AT = 3

    /** Left of [limit] after [used] (never negative); null = unlimited (Pro, or no limit on this plan). */
    fun left(limit: Int?, used: Int, pro: Boolean, resetDue: Boolean = false): Int? = when {
        pro || limit == null -> null
        resetDue -> limit
        else -> (limit - used).coerceAtLeast(0)
    }

    fun warn(left: Int?): Boolean = left != null && left <= WARN_AT

    /** "5 of 20 free messages left today". */
    fun messagesText(left: Int, limit: Int): String = "$left of $limit free ${if (limit == 1) "message" else "messages"} left today"

    /** "2 of 3 free new sessions left today". */
    fun sessionsText(left: Int, limit: Int): String = "$left of $limit free new ${if (limit == 1) "session" else "sessions"} left today"
}

/** One Pro product on the website (GET /v1/billing/plans). */
@Serializable
data class BillingProduct(
    val id: String = "",
    val name: String = "",
    val amount: Long = 0,
    val currency: String = "",
    val period: String? = null,
    val days: Int? = null,
)

@Serializable
data class BillingPayments(val configured: Boolean = false, val environment: String? = null)

/** GET /v1/billing/plans (public): Pro products, the limits of each plan and the rewarded-ad rules. */
@Serializable
data class BillingPlans(
    val products: List<BillingProduct> = emptyList(),
    val limits: JsonObject? = null,
    val payments: BillingPayments = BillingPayments(),
    val rewards: PlanRewards = PlanRewards(),
)

/** POST /v1/rewards/token: set as the rewarded ad's server-side verification options. */
@Serializable
data class RewardToken(val customData: String, val userId: String)

/**
 * 402 PLAN_LIMIT: the Free plan's daily limit of phone messages or new sessions was reached.
 * [limit] names the limit (e.g. phoneMessagesPerDay); [resetsAt] is an ISO time.
 */
data class PlanLimitError(
    val message: String,
    val limit: String? = null,
    val max: Int? = null,
    val used: Int? = null,
    val resetsAt: String? = null,
    val upgradeUrl: String? = null,
) {
    /** True when the limit is about new sessions (otherwise messages). */
    val isSessions get() = limit?.contains("session", ignoreCase = true) == true

    companion object {
        const val CODE = "PLAN_LIMIT"

        /** The PLAN_LIMIT details of [e], or null when [e] is anything else. */
        fun from(e: Throwable?): PlanLimitError? {
            val api = e as? ApiException ?: return null
            if (api.code != CODE) return null
            return fromDetails(api.message ?: "Daily free limit reached", api.details)
        }

        fun fromDetails(message: String, details: JsonElement?): PlanLimitError {
            val d = details as? JsonObject
            fun str(k: String) = (d?.get(k) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            fun int(k: String) = (d?.get(k) as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
            return PlanLimitError(message, str("limit"), int("max"), int("used"), str("resetsAt"), str("upgradeUrl"))
        }
    }
}

/** The plan section of Profile. */
data class PlanView(
    val loading: Boolean = false,
    val plan: Plan? = null,
    val error: ContentError? = null,
    /** The cheapest Pro product on the website, for "Upgrade to Pro" (null when unknown). */
    val products: List<BillingProduct> = emptyList(),
)
