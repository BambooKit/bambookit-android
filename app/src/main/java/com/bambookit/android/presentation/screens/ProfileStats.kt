package com.bambookit.android.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.Achievement
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.Project
import com.bambookit.android.data.ProjectStat
import com.bambookit.android.data.StatsFormat
import com.bambookit.android.data.StatsView
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.NeutralTint
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusRunningTint
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusSuccessTint
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import java.time.Duration
import java.time.Instant

private val PROJECT_STATUSES = listOf("active", "completed", "archived")

private fun projectStatusLabel(s: String) = s.replaceFirstChar { it.uppercase() }

private fun projectStatusColors(s: String): Pair<Color, Color> = when (s) {
    "completed" -> StatusSuccess to StatusSuccessTint
    "archived" -> TextMuted to NeutralTint
    else -> StatusRunning to StatusRunningTint
}

/** Updated or active within the last 7 days. */
private fun recent(iso: String?): Boolean = runCatching { Duration.between(Instant.parse(iso), Instant.now()).toDays() < 7 }.getOrDefault(false)

/**
 * Statistics (coding time, code, tasks) and achievements on the Profile screen — real numbers from
 * GET /v1/me/stats only (0 when there is nothing yet). Projects managed are shown separately, at the bottom
 * of the screen ([profileProjects]).
 */
fun LazyListScope.profileStats(view: StatsView, store: BambooStore) {
    val st = view.stats
    val err = view.error
    if (st == null) {
        when {
            err != null -> item {
                SectionTitle("Statistics")
                if (err.serverOutdated) Banner(
                    "Statistics, coding time and achievements come from the BambooKit server, which is older than this app. They appear once the server is updated.",
                    Icons.Filled.SystemUpdate, color = StatusWarning, tint = StatusWarningTint, title = "The BambooKit server needs an update",
                    actionLabel = "Retry", busy = view.loading, onAction = { store.loadStats() }, diagnosis = err.diagnosis,
                ) else Banner(
                    errorMessage(err, "your PC"), Icons.Filled.Info, color = StatusFailed, tint = com.bambookit.android.presentation.theme.StatusFailedTint, title = "Couldn't load your statistics",
                    actionLabel = "Retry", busy = view.loading, onAction = { store.loadStats() }, diagnosis = err.diagnosis,
                )
            }
            else -> item { LoadingState("Loading your statistics…") }
        }
        return
    }

    if (err != null) item {
        Banner(errorMessage(err, "your PC"), Icons.Filled.Info, title = "Showing the statistics read earlier", actionLabel = "Retry", busy = view.loading, onAction = { store.loadStats() }, diagnosis = err.diagnosis, modifier = Modifier.padding(top = Space.s))
    }

    // ---- coding time
    item {
        SectionTitle("Coding time")
        BkCard {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Dur("Total", st.codingTime.totalMs, Modifier.weight(1f))
                Dur("This week", st.codingTime.thisWeekMs, Modifier.weight(1f))
                Dur("This month", st.codingTime.thisMonthMs, Modifier.weight(1f))
            }
            st.rules["codingTime"]?.let { rule ->
                Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = TextMuted, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "How it's counted: $rule" + (st.rules["nightHours"]?.let { " Night hours: $it." } ?: ""),
                        color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp,
                    )
                }
            }
        }
    }

    // ---- code
    item {
        SectionTitle("Code")
        val c = st.code
        BkCard {
            StatGrid(
                listOf(
                    "Files created" to c.filesCreated, "Files modified" to c.filesModified, "Files deleted" to c.filesDeleted,
                    "Files renamed" to c.filesRenamed, "Lines added" to c.linesAdded, "Lines deleted" to c.linesDeleted,
                    "Edits" to c.edits, "Tests run" to c.testsRun, "Tests passed" to c.testsPassed,
                    "Tests failed" to c.testsFailed, "Commits" to c.commits, "Deployments" to c.deployments,
                ),
            )
            st.rules["files"]?.let { Text(it, color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = Space.s)) }
        }
    }

    // ---- tasks
    item {
        SectionTitle("Tasks")
        BkCard {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Num("Completed", st.tasks.completed.toLong(), Modifier.weight(1f), StatusSuccess)
                Num("Failed", st.tasks.failed.toLong(), Modifier.weight(1f), if (st.tasks.failed > 0) StatusFailed else TextPrimary)
                Num("Sessions", st.sessions.total.toLong(), Modifier.weight(1f))
            }
        }
    }

    // ---- achievements
    item {
        SectionTitle("Achievements", trailing = { Text(StatsFormat.unlockedSummary(st.achievements), color = TextMuted, fontSize = 11.sp) })
        if (st.achievements.isEmpty()) Text("No achievements yet.", color = TextSecondary, fontSize = 13.sp)
    }
    items(st.achievements.sortedWith(compareByDescending<Achievement> { it.unlocked }.thenByDescending { StatsFormat.fraction(it) }), key = { "a:" + it.id }) { a ->
        AchievementRow(a)
        Spacer(Modifier.height(Space.s))
    }
    item {
        st.timeZone?.let { Text("Times are counted in $it.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = Space.xs)) }
    }
}

/**
 * Projects managed: counts and the project list with status changes, shown at the bottom of the Profile screen.
 * With a server older than API 1.1.0 (no statistics) the projects known from the project list are still shown.
 */
fun LazyListScope.profileProjects(view: StatsView, projects: List<Project>, store: BambooStore, onOpenProject: (String) -> Unit) {
    val st = view.stats
    if (st == null) {
        if (projects.isNotEmpty()) {
            item {
                SectionTitle("Projects managed")
                BkCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Num("Total", projects.size.toLong(), Modifier.weight(1f))
                        Num("Recently updated", projects.count { recent(it.updatedAt) }.toLong(), Modifier.weight(1f))
                    }
                    Text("Status, coding time and per-project totals need the updated server.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = Space.s))
                }
            }
            items(projects.sortedByDescending { it.updatedAt }, key = { "pf:" + it.id }) { p ->
                Spacer(Modifier.height(Space.s))
                BkCard(onClick = { onOpenProject(p.id) }) {
                    Text(p.name, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(p.branch?.let { "⎇ $it" }, plural(p.totalSessions, "session"), p.updatedAt?.let { "updated ${relative(it)}" }).joinToString(" · "),
                        color = TextSecondary, fontSize = 12.sp,
                    )
                }
            }
        }
        return
    }

    // ---- projects managed
    item {
        SectionTitle("Projects managed")
        BkCard {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Num("Total", st.projects.total.toLong(), Modifier.weight(1f))
                Num("Active", st.projects.active.toLong(), Modifier.weight(1f))
                Num("Completed", st.projects.completed.toLong(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Num("Archived", st.projects.archived.toLong(), Modifier.weight(1f))
                Num("Recently updated", st.projects.list.count { recent(it.lastActivityAt ?: it.updatedAt) }.toLong(), Modifier.weight(2f))
            }
        }
    }
    if (st.projects.list.isEmpty()) item {
        Text("No projects yet. Open a project in BambooKit Desktop and it appears here.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = Space.s))
    }
    items(st.projects.list, key = { "ps:" + it.id }) { p ->
        Spacer(Modifier.height(Space.s))
        ProjectStatRow(p, saving = p.id in view.saving, onStatus = { store.setProjectStatus(p.id, it) }, onOpen = { onOpenProject(p.id) })
    }
}

@Composable
private fun Num(label: String, value: Long, modifier: Modifier = Modifier, color: Color = TextPrimary) {
    Column(modifier) {
        Text(StatsFormat.count(value), color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun Dur(label: String, ms: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(StatsFormat.duration(ms), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun StatGrid(cells: List<Pair<String, Long>>) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        cells.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { (label, v) -> Num(label, v, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectStatRow(p: ProjectStat, saving: Boolean, onStatus: (String) -> Unit, onOpen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    BkCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name.ifBlank { "Untitled project" }, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                p.branch?.let { Text("⎇ $it", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Box {
                val (fg, bg) = projectStatusColors(p.status)
                TextButton(onClick = { menu = true }, enabled = !saving, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    if (saving) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    else Chip(projectStatusLabel(p.status), fg, bg, dot = true)
                    Icon(Icons.Filled.ArrowDropDown, "Change status", tint = TextSecondary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = BambooSurfaceElevated) {
                    PROJECT_STATUSES.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(projectStatusLabel(s), color = if (s == p.status) BambooGreen else TextPrimary) },
                            onClick = { menu = false; if (s != p.status) onStatus(s) },
                        )
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = Space.xs)) {
            Meta(plural(p.sessions, "session"))
            Meta(plural(p.tasks, "task"))
            Meta(plural(p.filesChanged, "file") + " changed")
            Meta(StatsFormat.duration(p.codingMs) + " coding")
        }
        Text(
            listOfNotNull(dateOnly(p.createdAt)?.let { "Created $it" }, (p.lastActivityAt ?: p.updatedAt)?.let { "last activity ${relative(it).ifBlank { dateOnly(it) ?: "" }}" }).joinToString(" · "),
            color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun Meta(text: String) = Text(text, color = TextSecondary, fontSize = 12.sp)

@Composable
private fun AchievementRow(a: Achievement) {
    val fraction = StatsFormat.fraction(a)
    BkCard(border = if (a.unlocked) com.bambookit.android.presentation.theme.AchievementUnlockedBorder else BambooBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                if (a.unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                tint = if (a.unlocked) StatusWarning else TextMuted,
                background = if (a.unlocked) StatusWarningTint else NeutralTint,
                size = 34.dp,
            )
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(a.title, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(a.description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(Space.s))
        LinearProgressIndicator(
            progress = { fraction }, modifier = Modifier.fillMaxWidth(),
            color = if (a.unlocked) StatusSuccess else BambooGreen, trackColor = BambooBorder,
        )
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(StatsFormat.progress(a), color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (a.unlocked) {
                Text("✓ Unlocked${dateOnly(a.unlockedAt)?.let { " $it" } ?: ""}", color = StatusSuccess, fontSize = 11.sp)
            }
        }
    }
}
