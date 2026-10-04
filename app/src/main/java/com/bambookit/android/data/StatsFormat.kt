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
}
