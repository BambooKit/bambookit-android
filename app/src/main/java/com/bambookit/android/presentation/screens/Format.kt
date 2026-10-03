package com.bambookit.android.presentation.screens

import androidx.compose.runtime.compositionLocalOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** True while the App lock screen is showing: full-screen dialogs (separate windows) are not drawn over it. */
val LocalAppLocked = compositionLocalOf { false }

/** Parses API timestamps: ISO-8601 instants or offsets, or "yyyy-MM-dd HH:mm:ss" (UTC). Null if unknown. */
fun parseInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    val v = value.trim()
    return runCatching { Instant.parse(v) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(v).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(v.replace(' ', 'T')).toInstant(ZoneOffset.UTC) }.getOrNull()
        ?: v.toLongOrNull()?.let { if (it > 100_000_000_000L) Instant.ofEpochMilli(it) else Instant.ofEpochSecond(it) }
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private val clockFmt = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
private val shortClockFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

/** "14:05:09" in the phone's time zone. */
fun clock(instant: Instant?): String = instant?.atZone(zone)?.format(clockFmt) ?: "--:--:--"

/** "14:05". */
fun shortClock(instant: Instant?): String = instant?.atZone(zone)?.format(shortClockFmt) ?: ""

/** "3 Oct 2026". */
fun dateOnly(value: String?): String? = parseInstant(value)?.atZone(zone)?.format(dateFmt)

fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

/** "Today", "Yesterday" or "Sat 3 Oct 2026". */
fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayFmt)
    }
}

/** "Today 14:05", "Yesterday 09:12" or "3 Oct 2026 14:05". */
fun dateTime(value: String?): String? {
    val i = parseInstant(value) ?: return null
    val d = localDate(i)
    val day = dayLabel(d).let { if (it == "Today" || it == "Yesterday") it else d.format(dateFmt) }
    return "$day ${shortClock(i)}"
}

/** "45s", "12m 30s", "1h 04m". */
fun formatDuration(ms: Long?): String? {
    if (ms == null || ms < 0) return null
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${(s % 60).toString().padStart(2, '0')}s"
        else -> "${s / 3600}h ${((s % 3600) / 60).toString().padStart(2, '0')}m"
    }
}

/** The agent is always shown under the BambooKit name with its mode, e.g. "BambooKit · build". */
fun agentLabel(agent: String?): String {
    val mode = agent?.trim()?.replace(Regex("(?i)opencode"), "BambooKit")?.takeIf { it.isNotEmpty() && !it.equals("BambooKit", true) }
    return if (mode == null) "BambooKit" else "BambooKit · $mode"
}
