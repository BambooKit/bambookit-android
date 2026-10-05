package com.bambookit.android.data

import java.text.NumberFormat
import java.util.Locale

/** Formatting of profile statistics and achievement progress (unit tested). */
object StatsFormat {
    /** 16 020 000 ms → "4h 27m"; 2 h → "2h"; 12 min → "12m"; under a minute → "0m". Never negative. */
    fun duration(ms: Long): String {
        val totalMin = (ms.coerceAtLeast(0) / 60_000)
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** 1234 → "1,234". */
    fun count(n: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(n)

    /** Fraction of the target reached, 0..1. Unlocked achievements are always full. */
    fun fraction(a: Achievement): Float = when {
        a.unlocked -> 1f
        a.target <= 0.0 -> 0f
        else -> (a.progress / a.target).coerceIn(0.0, 1.0).toFloat()
    }

    /** "3 / 5", "1,000 / 1,000", or for time "1h 12m / 2h". Progress never shows more than the target. */
    fun progress(a: Achievement): String {
        val p = if (a.unlocked) maxOf(a.progress, a.target) else a.progress
        val shown = minOf(p, a.target).coerceAtLeast(0.0)
        return if (a.unit == "ms") "${duration(shown.toLong())} / ${duration(a.target.toLong())}"
        else "${count(shown.toLong())} / ${count(a.target.toLong())}"
    }

    /** "3 of 15 unlocked". */
    fun unlockedSummary(list: List<Achievement>): String = "${list.count { it.unlocked }} of ${list.size} unlocked"

    // ------------------------------------------------------------------ tiered achievements

    val TIERS = listOf("bronze", "silver", "gold", "platinum", "diamond")

    /** BambooKit Master: counts the other achievements at Bronze or better. */
    const val MASTER_ID = "bambookit-master"

    /** 🥉 🥈 🥇 💎 💠, or null for no tier. */
    fun medal(tier: String?): String? = when (tier?.lowercase()) {
        "bronze" -> "🥉"
        "silver" -> "🥈"
        "gold" -> "🥇"
        "platinum" -> "💎"
        "diamond" -> "💠"
        else -> null
    }

    /** "gold" → "Gold". */
    fun tierLabel(tier: String?): String = tier.orEmpty().lowercase().replaceFirstChar { it.uppercase() }

    /** 0 (none) … 5 (diamond). */
    fun tierRank(tier: String?): Int = TIERS.indexOf(tier?.lowercase()) + 1

    fun isTiered(a: Achievement): Boolean = a.tiers.isNotEmpty()

    /** A value in its unit: hours keep one decimal ("12.5"), everything else is a whole number ("1,240"). */
    fun amount(v: Double, unit: String): String {
        val n = v.coerceAtLeast(0.0)
        return if (unit == "hours" && n % 1.0 != 0.0) String.format(Locale.US, "%,.1f", n) else count(n.toLong())
    }

    /** The unit as shown after a number: "count" (and an empty unit) shows nothing. */
    fun unitLabel(unit: String): String = when (unit) {
        "count", "" -> ""
        else -> unit
    }

    /** The current value of a tiered achievement (value, or progress on servers that send only that). */
    fun valueOf(a: Achievement): Double = a.value ?: a.progress

    /**
     * Progress bar fraction. Tiered: towards the next tier (full once all are reached); untrackable: 0.
     * Older shape: [fraction].
     */
    fun tierFraction(a: Achievement): Float = when {
        !a.trackable -> 0f
        !isTiered(a) -> fraction(a)
        a.nextTier == null && a.tiers.all { it.unlocked } -> 1f
        a.target <= 0.0 -> 0f
        else -> (valueOf(a) / a.target).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * "1,240 / 10,000 lines → Gold"; "120,345 lines · all tiers reached"; the reason for untrackable ones;
     * the older shape keeps [progress] ("3 / 5", "1h 12m / 2h").
     */
    fun tierProgress(a: Achievement): String {
        if (!a.trackable) return a.reason?.takeIf { it.isNotBlank() } ?: "Not tracked yet"
        if (!isTiered(a)) return progress(a)
        val unit = unitLabel(a.unit).let { if (it.isEmpty()) "" else " $it" }
        val next = a.nextTier
        return if (next == null) "${amount(valueOf(a), a.unit)}$unit · all tiers reached"
        else "${amount(minOf(valueOf(a), a.target), a.unit)} / ${amount(a.target, a.unit)}$unit → ${tierLabel(next)}"
    }

    /** The summary to show: the server's, or one counted from the list (older servers). */
    fun summaryOf(list: List<Achievement>, server: AchievementSummary?): AchievementSummary = server ?: AchievementSummary(
        unlocked = list.count { it.unlocked }, total = list.size,
        tiersUnlocked = list.sumOf { a -> a.tiers.count { it.unlocked } }, tiersTotal = list.sumOf { it.tiers.size },
    )

    /** "23 of 50 · 61/250 tiers · 🔥 4-day streak (best 12)"; parts the server didn't send are left out. */
    fun summaryLine(s: AchievementSummary, server: Boolean = true): String = listOfNotNull(
        "${s.unlocked} of ${s.total}",
        if (s.tiersTotal > 0) "${s.tiersUnlocked}/${s.tiersTotal} tiers" else null,
        if (server) streakText(s.currentStreak, s.longestStreak) else null,
    ).joinToString(" · ")

    /** "🔥 4-day streak (best 12)", "🔥 No streak today (best 12)", or null when there never was one. */
    fun streakText(current: Int, longest: Int): String? = when {
        current <= 0 && longest <= 0 -> null
        current <= 0 -> "🔥 No streak today (best $longest)"
        else -> "🔥 $current-day streak" + if (longest > current) " (best $longest)" else ""
    }

    /** "143 points". */
    fun pointsText(points: Int): String = if (points == 1) "1 point" else "${count(points.toLong())} points"

    enum class Filter(val label: String) { All("All"), Unlocked("Unlocked"), InProgress("In progress"), NotTracked("Not tracked") }

    /** Unlocked: at least one tier. In progress: trackable with a tier still to reach. Not tracked: can't be measured yet. */
    fun matches(a: Achievement, f: Filter): Boolean = when (f) {
        Filter.All -> true
        Filter.Unlocked -> a.unlocked
        Filter.InProgress -> a.trackable && if (isTiered(a)) a.nextTier != null else !a.unlocked
        Filter.NotTracked -> !a.trackable
    }

    /** Highest tier first, then closest to the next tier; untrackable ones last. */
    fun sorted(list: List<Achievement>): List<Achievement> = list.sortedWith(
        compareByDescending<Achievement> { it.trackable }
            .thenByDescending { if (isTiered(it)) tierRank(it.tier) else if (it.unlocked) 1 else 0 }
            .thenByDescending { tierFraction(it) },
    )

    /** In-app message for the achievement.unlocked event: "🥇 Achievement unlocked: 💻 Code Written — Gold". */
    fun eventMessage(e: AchievementEvent): String? {
        val title = e.title.takeIf { it.isNotBlank() } ?: return null
        return when {
            e.count != null || e.id == "summary" -> "🏆 $title"
            e.tier != null -> "${medal(e.tier)?.let { "$it " } ?: ""}Achievement unlocked: $title"
            else -> "Achievement unlocked: $title"
        }
    }

    /**
     * An event title without the emoji in front (the badge shows the icon) and, when [tier] is given, without the
     * trailing " — Gold": "💻 Code Written — Gold" → "Code Written".
     */
    fun plainTitle(title: String, tier: String? = null): String {
        var t = title.trim().dropWhile { !it.isLetterOrDigit() }.trim()
        if (tier != null) {
            val suffix = " — ${tierLabel(tier)}"
            if (t.endsWith(suffix, ignoreCase = true)) t = t.dropLast(suffix.length).trim()
        }
        return t.ifEmpty { title.trim() }
    }

    /**
     * Title of an achievement notification (Recent activity and the phone notification): "🥇 Gold achievement unlocked"
     * when it carries a tier, "🏆 Achievements unlocked" for a summary; other notifications keep their title.
     */
    fun notificationTitle(type: String, title: String, data: Map<String, String>): String {
        if (type != "achievement.unlocked") return title
        val tier = data["tier"]
        return when {
            tier != null && medal(tier) != null -> "${medal(tier)} ${tierLabel(tier)} achievement unlocked"
            data["count"] != null -> "🏆 ${title.ifBlank { "Achievements unlocked" }}"
            else -> title
        }
    }
}
