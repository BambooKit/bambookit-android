package com.bambookit.android.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.Approval
import com.bambookit.android.data.Device
import com.bambookit.android.data.FileChange
import com.bambookit.android.data.FileMapEntry
import com.bambookit.android.data.HistoryResponse
import com.bambookit.android.data.PendingCommand
import com.bambookit.android.data.Session
import com.bambookit.android.data.SessionDetail
import com.bambookit.android.data.SessionHistory
import com.bambookit.android.data.TestRun
import com.bambookit.android.data.TimelineEntry
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.ChangeAdded
import com.bambookit.android.presentation.theme.ChangeModified
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.NeutralTint
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusFailedTint
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusRunningTint
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusSuccessTint
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import com.bambookit.android.presentation.theme.TimelineRail
import com.bambookit.android.presentation.theme.QuestionAccent
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max
import kotlinx.coroutines.delay

// ================================================================== shared

/** Approvals of the session: the history's full list, updated with the fresher pending list from /approvals. */
internal fun mergedApprovals(d: SessionDetail): List<Approval> {
    val byId = LinkedHashMap<String, Approval>()
    d.history.data?.approvals?.forEach { byId[it.id] = it }
    d.approvals.forEach { byId[it.id] = it }
    return byId.values.sortedBy { parseInstant(it.createdAt) ?: Instant.MAX }
}

/** "Live from Office-PC" or "Saved copy · Today 14:05 · kept 7 days". */
internal fun sourceLabel(h: HistoryResponse, pcTitle: String): String =
    if (h.live) "Live from $pcTitle"
    else listOfNotNull("Saved copy", dateTime(h.savedAt), "kept 7 days").joinToString(" · ")

@Composable
internal fun SourceBadge(h: HistoryResponse, pcTitle: String) {
    if (h.live) Chip(sourceLabel(h, pcTitle), StatusSuccess, StatusSuccessTint, icon = Icons.Filled.Wifi)
    else Chip(sourceLabel(h, pcTitle), StatusWarning, StatusWarningTint, icon = Icons.Filled.Cloud)
}

/**
 * Wraps a history-based tab: loading, "not available" (PC offline and no 7-day copy) with Retry,
 * pull-to-refresh, and a notice when showing an older read or the saved copy.
 */
@Composable
internal fun HistoryGate(d: SessionDetail, pcTitle: String, onRetry: () -> Unit, content: @Composable (HistoryResponse) -> Unit) {
    val h = d.history
    val data = h.data
    when {
        data == null && h.error != null -> ContentUnavailable(h.error, pcTitle, h.loading, onRetry)
        data == null -> LoadingState("Reading this session from $pcTitle…")
        else -> SessionRefresh(h.loading, onRetry) {
            Column(Modifier.fillMaxSize()) {
                if (h.error != null) StaleBanner(h.error, h.loading, onRetry)
                else if (!data.live) Banner(
                    "$pcTitle is offline. This is the copy it saved${dateTime(data.savedAt)?.let { " ($it)" } ?: ""}; copies are kept for 7 days.",
                    Icons.Filled.Cloud, color = StatusWarning, tint = StatusWarningTint,
                    modifier = Modifier.padding(horizontal = Space.m).padding(top = Space.s),
                )
                Box(Modifier.weight(1f)) { content(data) }
            }
        }
    }
}

// ================================================================== live activity

/** "Agent working… Editing src/auth.ts" with Stop, for a busy or retrying session. */
@Composable
internal fun LiveActivity(s: Session, command: PendingCommand?, onStop: () -> Unit, compact: Boolean) {
    val retry = s.status == "retry"
    val color = if (retry) StatusWarning else StatusRunning
    val headline = if (retry) "Retrying…" else "Agent working…"
    val action = s.currentAction?.takeIf { it.isNotBlank() } ?: s.statusMessage?.takeIf { retry && it.isNotBlank() }
    val stopping = command?.type == "ABORT" && command.status == "PENDING"
    val body: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color, 8, pulse = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                action?.let { Text(it, color = TextSecondary, fontSize = 12.sp, maxLines = if (compact) 1 else 3, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.width(Space.s))
            OutlinedButton(
                onClick = onStop, enabled = !stopping, contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFailed),
            ) {
                if (stopping) CircularProgressIndicator(Modifier.size(14.dp), color = StatusFailed, strokeWidth = 2.dp)
                else Icon(Icons.Filled.Stop, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (stopping) "Stopping" else "Stop")
            }
        }
    }
    if (compact) Column(Modifier.fillMaxWidth().background(BambooSurface).padding(horizontal = Space.m, vertical = Space.s)) { body() }
    else BkCard(border = color.copy(alpha = 0.45f)) { body() }
}

@Composable
internal fun CommandStatus(c: PendingCommand) {
    val label = com.bambookit.android.data.commandLabel(c.type)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        when (c.status) {
            "PENDING" -> {
                if (c.deviceOnline) CircularProgressIndicator(Modifier.size(12.dp), color = TextSecondary, strokeWidth = 1.5.dp)
                else Icon(Icons.Filled.CloudOff, null, tint = StatusWarning, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (c.deviceOnline) "$label: sending to your PC…" else "$label: PC offline, queued (expires in 5 min)",
                    color = if (c.deviceOnline) TextSecondary else StatusWarning, fontSize = 12.sp,
                )
            }
            "SUCCEEDED" -> {
                Icon(Icons.Filled.CheckCircle, null, tint = StatusSuccess, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("$label: done on your PC", color = StatusSuccess, fontSize = 12.sp)
            }
            else -> {
                Icon(Icons.Filled.ErrorOutline, null, tint = StatusFailed, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("$label failed: ${c.error ?: "unknown error"}", color = StatusFailed, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ================================================================== Summary

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummaryPane(
    d: SessionDetail,
    pc: Device?,
    pcTitle: String,
    approvalCard: @Composable (Approval) -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (SessionTabId) -> Unit,
    onRetryTodos: () -> Unit,
) {
    val s = d.session
    val hv = d.history
    val data = hv.data
    val h: SessionHistory? = data?.history
    val sum = h?.summary
    val approvals = remember(d.history.data, d.approvals) { mergedApprovals(d) }
    val pending = approvals.filter { it.isPending }
    val status = s?.status ?: h?.status

    SessionRefresh(hv.loading || d.loading, onRetry) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
            item {
                Column(Modifier.padding(top = Space.xs)) {
                    Text(
                        s?.title ?: h?.title ?: "Session", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp,
                    )
                    (h?.projectName ?: s?.projectName)?.let { Mono(it, color = TextSecondary, size = 12, modifier = Modifier.padding(top = 2.dp)) }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = Space.s),
                    ) {
                        status?.let { StatusChip(it) }
                        data?.let { SourceBadge(it, pcTitle) }
                        pc?.let { OnlineChip(it.online, it.name) }
                    }
                }
            }
            if (s != null && s.isActive) item {
                Spacer(Modifier.height(Space.m))
                LiveActivity(s, d.commands.lastOrNull(), onStop, compact = false)
            }
            d.commands.lastOrNull()?.takeIf { s?.isActive != true }?.let { c -> item { CommandStatus(c) } }
            s?.statusMessage?.takeIf { it.isNotBlank() && s.status == "error" }?.let { msg ->
                item {
                    Spacer(Modifier.height(Space.m))
                    Banner(msg, Icons.Filled.ErrorOutline, color = StatusFailed, tint = StatusFailedTint, title = "The session stopped with an error")
                }
            }
            if (data == null && hv.error != null) item {
                Spacer(Modifier.height(Space.m))
                Banner(
                    hv.error.message, Icons.Filled.CloudOff, color = StatusWarning, tint = StatusWarningTint, title = "History not available",
                    actionLabel = "Retry", busy = hv.loading, onAction = onRetry,
                )
            } else if (data == null && hv.loading) item { LoadingState("Reading this session from $pcTitle…") }
            if (d.todos.todos.isNotEmpty() || d.todos.error != null || s?.isActive == true) item {
                SectionTitle("Todos") {
                    if (d.todos.todos.isNotEmpty()) androidx.compose.material3.TextButton(onClick = { onOpen(SessionTabId.Todos) }) { Text("Open", fontSize = 12.sp) }
                }
                TodoSection(d.todos, pcTitle, onRetryTodos)
            }
            if (pending.isNotEmpty()) {
                item { SectionTitle("Waiting for you") }
                items(pending, key = { "p:" + it.id }) {
                    approvalCard(it)
                    Spacer(Modifier.height(Space.s))
                }
            }

            item {
                SectionTitle("Summary")
                val files = sum?.filesChanged ?: h?.changes?.size ?: s?.changes?.files
                val additions = sum?.additions ?: h?.changes?.sumOf { it.additions } ?: s?.changes?.additions
                val deletions = sum?.deletions ?: h?.changes?.sumOf { it.deletions } ?: s?.changes?.deletions
                val passed = sum?.testsPassed ?: h?.tests?.count { it.status == "passed" }
                val failed = sum?.testsFailed ?: h?.tests?.count { it.status == "failed" }
                val prompts = sum?.prompts ?: h?.prompts?.size
                val duration = formatDuration(h?.durationMs ?: sum?.durationMs)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s), maxItemsInEachRow = 2) {
                    val cell = Modifier.weight(1f)
                    duration?.let { Metric("Duration", it, modifier = cell) }
                    prompts?.let { Metric("Prompts", it.toString(), modifier = cell, onClick = { onOpen(SessionTabId.Prompts) }) }
                    files?.let { Metric("Files changed", it.toString(), modifier = cell, onClick = { onOpen(SessionTabId.Changes) }) }
                    if (additions != null || deletions != null) Metric("Lines", null, modifier = cell, onClick = { onOpen(SessionTabId.Changes) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("+${additions ?: 0}", color = StatusSuccess, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(8.dp))
                            Text("−${deletions ?: 0}", color = StatusFailed, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                        }
                    }
                    if (h != null && (h.tests.isNotEmpty() || (passed ?: 0) + (failed ?: 0) > 0)) Metric("Tests", null, modifier = cell, onClick = { onOpen(SessionTabId.Timeline) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${passed ?: 0} passed", color = StatusSuccess, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Text("${failed ?: 0} failed", color = if ((failed ?: 0) > 0) StatusFailed else TextMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (data != null || approvals.isNotEmpty()) Metric(
                        "Approvals", approvals.size.toString() + if (pending.isNotEmpty()) " · ${pending.size} waiting" else "",
                        modifier = cell, onClick = { onOpen(SessionTabId.Timeline) },
                    )
                }
            }

            item {
                SectionTitle("Details")
                BkCard {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val branch = h?.branch?.takeIf { it.isNotBlank() }
                            if (data != null) Detail("Branch", branch ?: "Branch unavailable", mono = branch != null, muted = branch == null)
                            h?.baseCommit?.takeIf { it.isNotBlank() }?.let { Detail("Base commit", it.take(7), mono = true) }
                            Detail("Session", d.sessionId.take(8), mono = true)
                            Detail("Agent", agentLabel(s?.agent ?: h?.agent))
                            brandModel(s?.model ?: h?.model)?.takeIf { it.isNotBlank() }?.let { Detail("Model", it, mono = true) }
                            pc?.let { Detail("PC", it.name) }
                            (h?.directory ?: s?.directory)?.takeIf { it.isNotBlank() }?.let { Detail("Folder", it, mono = true) }
                            dateTime(h?.createdAt ?: s?.createdAt)?.let { Detail("Started", it) }
                            dateTime(h?.updatedAt ?: s?.updatedAt)?.let { Detail("Last activity", it) }
                        }
                    }
                }
            }
            item { BottomSpacer() }
        }
    }
}

@Composable
private fun Metric(label: String, value: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: (@Composable () -> Unit)? = null) {
    BkCard(modifier, onClick = onClick, padding = PaddingValues(12.dp)) {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        if (content != null) content()
        else Text(value.orEmpty(), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Detail(label: String, value: String, mono: Boolean = false, muted: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(96.dp))
        Text(
            value, color = if (muted) TextMuted else TextPrimary, fontSize = if (mono) 12.sp else 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, modifier = Modifier.weight(1f),
        )
    }
}

// ================================================================== Prompts

@Composable
internal fun PromptsPane(data: HistoryResponse, onJump: (String) -> Unit) {
    val prompts = data.history.prompts
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
        if (prompts.isEmpty()) item { EmptyState("No prompts yet", "What you ask the agent on your PC appears here.", Icons.Filled.Person) }
        else item { SectionTitle(plural(prompts.size, "prompt")) }
        itemsIndexed(prompts, key = { i, p -> "$i:${p.messageId}" }) { i, p ->
            BkCard(padding = PaddingValues(0.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { onJump(p.messageId) }.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Prompt ${i + 1}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(Space.s))
                    parseInstant(p.time)?.let { t ->
                        Mono(clock(t), color = TextMuted, size = 11)
                        if (localDate(t) != LocalDate.now()) Text("  ${dayLabel(localDate(t))}", color = TextMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Timeline", color = TextSecondary, fontSize = 12.sp)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Show in timeline", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                SelectionContainer {
                    Text(
                        p.text.ifBlank { "(empty prompt)" }, color = if (p.text.isBlank()) TextMuted else TextPrimary, fontSize = 14.sp, lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(Space.s))
        }
        item { BottomSpacer() }
    }
}

// ================================================================== Timeline

private sealed interface TimelineRow { val key: String }
private data class DayRow(override val key: String, val label: String) : TimelineRow
private data class EventRow(override val key: String, val e: TimelineEntry, val at: Instant?, val prompt: Int?, val test: TestRun?) : TimelineRow
private data class ApprovalRow(override val key: String, val a: Approval, val at: Instant?) : TimelineRow

private fun timelineRows(h: SessionHistory, approvals: List<Approval>): List<TimelineRow> {
    val promptNumber = h.prompts.withIndex().associate { (i, p) -> p.messageId to i + 1 }
    val testsByTime = h.tests.groupBy { it.time }
    val dated = mutableListOf<Pair<Instant?, TimelineRow>>()
    var last: Instant? = null
    var promptSeen = 0
    h.timeline.forEachIndexed { i, e ->
        val at = parseInstant(e.time) ?: last
        last = at
        val prompt = if (e.kind == "prompt") (e.messageId?.let { promptNumber[it] } ?: (promptSeen + 1)).also { promptSeen = it } else null
        val test = if (e.kind == "test") testsByTime[e.time]?.let { list -> list.firstOrNull { it.command == e.detail } ?: list.first() } else null
        dated += at to EventRow("e:${e.id.ifEmpty { "$i" }}:$i", e, at, prompt, test)
    }
    approvals.forEach { a -> dated += parseInstant(a.createdAt) to ApprovalRow("a:" + a.id, a, parseInstant(a.createdAt)) }
    // Stable sort: timeline order is kept for equal or missing times; approvals fall in by time.
    val sorted = dated.withIndex().sortedWith(compareBy({ it.value.first ?: Instant.MAX }, { it.index })).map { it.value }
    val out = mutableListOf<TimelineRow>()
    var day: LocalDate? = null
    for ((at, row) in sorted) {
        if (at != null) {
            val d = localDate(at)
            if (d != day) {
                day = d
                out += DayRow("d:$d", dayLabel(d))
            }
        }
        out += row
    }
    return out
}

private fun kindStyle(kind: String, status: String?): Triple<ImageVector, Color, String> {
    if (status == "error" && kind != "error") return Triple(iconFor(kind), StatusFailed, labelFor(kind))
    return Triple(iconFor(kind), colorFor(kind), labelFor(kind))
}

private fun iconFor(kind: String): ImageVector = when (kind) {
    "prompt" -> Icons.Filled.Person
    "response" -> Icons.Filled.SmartToy
    "read" -> Icons.Filled.Visibility
    "edit" -> Icons.Filled.Edit
    "write" -> Icons.Filled.PostAdd
    "patch" -> Icons.Filled.Difference
    "command" -> Icons.Filled.Terminal
    "test" -> Icons.Filled.Science
    "search" -> Icons.Filled.Search
    "web" -> Icons.Filled.Language
    "agent" -> Icons.Filled.AccountTree
    "plan" -> Icons.Filled.Checklist
    "error" -> Icons.Filled.ErrorOutline
    "completed" -> Icons.Filled.CheckCircle
    else -> Icons.Filled.Build
}

private fun colorFor(kind: String): Color = when (kind) {
    "prompt" -> TextPrimary
    "response" -> TextSecondary
    "edit", "patch" -> ChangeModified
    "write" -> ChangeAdded
    "command", "test", "agent" -> StatusRunning
    "error" -> StatusFailed
    "completed" -> StatusSuccess
    else -> TextSecondary
}

private fun labelFor(kind: String): String = when (kind) {
    "prompt" -> "Prompt"
    "response" -> "BambooKit replied"
    "read" -> "Read"
    "edit" -> "Edit"
    "write" -> "Write"
    "patch" -> "Patch"
    "command" -> "Command"
    "test" -> "Test"
    "search" -> "Search"
    "web" -> "Web"
    "agent" -> "Sub-agent"
    "plan" -> "Plan"
    "error" -> "Error"
    "completed" -> "Finished"
    else -> "Tool"
}

/**
 * Everything that happened in the session, oldest first: prompts, replies, tool calls (with files,
 * commands and test results) and approvals. [focusPrompt] scrolls to that prompt once.
 */
@Composable
internal fun TimelinePane(
    data: HistoryResponse,
    approvals: List<Approval>,
    focusPrompt: String?,
    onFocusDone: () -> Unit,
    onOpenFile: (String) -> Unit,
    approvalActions: @Composable (Approval) -> Unit,
) {
    val rows = remember(data, approvals) { timelineRows(data.history, approvals) }
    val listState = rememberLazyListState()
    var highlighted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusPrompt, rows) {
        val id = focusPrompt ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is EventRow && it.e.kind == "prompt" && (it.e.messageId == id || it.e.id == "prompt:$id") }
        if (index >= 0) listState.scrollToItem(max(0, index - 1))
        highlighted = id
        onFocusDone()
    }
    LaunchedEffect(highlighted) {
        if (highlighted != null) {
            delay(2500)
            highlighted = null
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.m, end = Space.screen, bottom = Space.xl)) {
        if (rows.isEmpty()) item { EmptyState("Nothing recorded yet", "Prompts, tool calls, file changes and approvals appear here as they happen.", Icons.Filled.Bolt) }
        items(rows, key = { it.key }) { row ->
            when (row) {
                is DayRow -> Text(
                    row.label.uppercase(), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
                )
                is EventRow -> {
                    val (icon, color, label) = kindStyle(row.e.kind, row.e.status)
                    TimelineItem(row.at, icon, color, highlight = row.e.kind == "prompt" && highlighted != null && row.e.messageId == highlighted) {
                        EventBody(row, label, color, onOpenFile)
                    }
                }
                is ApprovalRow -> TimelineItem(
                    row.at,
                    if (row.a.isQuestion) Icons.AutoMirrored.Filled.HelpOutline else Icons.Filled.Shield,
                    if (row.a.isQuestion) QuestionAccent else StatusWarning, highlight = false,
                ) {
                    ApprovalBody(row.a)
                    if (row.a.status == "PENDING") {
                        Spacer(Modifier.height(Space.s))
                        approvalActions(row.a)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineItem(at: Instant?, icon: ImageVector, color: Color, highlight: Boolean, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(clock(at), color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(62.dp).padding(top = 9.dp))
        Box(Modifier.width(30.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(TimelineRail))
            Box(
                Modifier.padding(top = 4.dp).size(26.dp).clip(CircleShape).background(BambooObsidian).border(1.5.dp, color.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(14.dp)) }
        }
        Column(
            Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 12.dp)
                .let { if (highlight) it.clip(RoundedCornerShape(10.dp)).background(BambooGreenSubtle).padding(8.dp) else it },
        ) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventBody(row: EventRow, label: String, color: Color, onOpenFile: (String) -> Unit) {
    val e = row.e
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            if (e.kind == "prompt" && row.prompt != null) "Prompt ${row.prompt}" else label,
            color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
        when (e.status) {
            "error" -> if (e.kind != "error") { Spacer(Modifier.width(6.dp)); Chip("failed", StatusFailed, StatusFailedTint) }
            "running", "pending" -> { Spacer(Modifier.width(6.dp)); Chip("running", StatusRunning, StatusRunningTint, dot = true, pulse = true) }
        }
    }
    val title = e.title.takeIf { it.isNotBlank() && it != e.file }
    title?.let {
        Text(
            it, color = if (e.kind == "error") StatusFailed else TextPrimary, fontSize = 13.sp, lineHeight = 18.sp,
            maxLines = if (e.kind == "prompt") 4 else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
        )
    }
    e.file?.takeIf { it.isNotBlank() }?.let { f ->
        Row(
            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(BambooSurfaceElevated).clickable { onOpenFile(f) }.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Description, null, tint = TextSecondary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Mono(f, color = TextSecondary, size = 11, modifier = Modifier.widthIn(max = 260.dp))
        }
    }
    e.detail?.takeIf { it.isNotBlank() && it != e.title }?.let { detail ->
        Mono(
            detail, color = if (e.status == "error") StatusFailed else TextSecondary, size = 11, maxLines = 4,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(CodeBlockBackground).padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
    row.test?.let { t ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 5.dp)) {
            val exit = t.exitCode?.let { " · exit $it" } ?: ""
            when (t.status) {
                "passed" -> Chip("Tests passed$exit", StatusSuccess, StatusSuccessTint, icon = Icons.Filled.CheckCircle)
                "failed" -> Chip("Tests failed$exit", StatusFailed, StatusFailedTint, icon = Icons.Filled.ErrorOutline)
                "running" -> Chip("Tests running", StatusRunning, StatusRunningTint, dot = true, pulse = true)
                else -> Chip("Result unknown$exit", TextMuted, NeutralTint)
            }
        }
    }
}

private fun approvalOutcome(a: Approval): Pair<String, Color> = when {
    a.status == "PENDING" -> "Waiting for you" to StatusWarning
    a.status == "RESPONDING" -> "Answer sent, waiting for the PC" to StatusRunning
    a.status == "EXPIRED" -> "No longer needed" to TextMuted
    a.isQuestion && a.reply == "reject" -> "Dismissed" to TextSecondary
    a.isQuestion -> "Answered" to StatusSuccess
    a.reply == "once" -> "Approved once" to StatusSuccess
    a.reply == "always" -> "Always allowed" to StatusSuccess
    a.reply == "reject" -> "Rejected" to StatusFailed
    else -> a.status.lowercase().replaceFirstChar { it.uppercase() }.ifBlank { "Answered" } to TextSecondary
}

@Composable
private fun ApprovalBody(a: Approval) {
    val (outcome, color) = approvalOutcome(a)
    RequestSummary(a)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(outcome, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        parseInstant(a.resolvedAt)?.let { Text("  ·  ${clock(it)}", color = TextMuted, fontSize = 11.sp) }
    }
}

// ================================================================== Changes

private val ChangeGroups = listOf("modified", "added", "deleted", "renamed")

@Composable
internal fun ChangesPane(data: HistoryResponse, onOpen: (FileChange) -> Unit) {
    val changes = data.history.changes
    val sum = data.history.summary
    val additions = sum?.additions ?: changes.sumOf { it.additions }
    val deletions = sum?.deletions ?: changes.sumOf { it.deletions }
    val groups = remember(changes) {
        changes.groupBy { if (it.status in ChangeGroups) it.status else "modified" }
            .toSortedMap(compareBy { ChangeGroups.indexOf(it) })
            .mapValues { (_, list) -> list.sortedBy { it.file.lowercase() } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
        if (changes.isEmpty()) {
            item { EmptyState("No file changes", "Files the agent creates, edits or deletes in this session appear here.", Icons.Filled.Description) }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(top = Space.m, bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text("${plural(sum?.filesChanged ?: changes.size, "file")} changed", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(Space.m))
                    PlusMinus(additions, deletions, size = 13)
                }
            }
            groups.forEach { (status, list) ->
                item(key = "h:$status") { SectionTitle("${changeStyle(status).label} (${list.size})") }
                items(list.size, key = { i -> "c:$status:$i:" + list[i].file }) { i ->
                    val c = list[i]
                    ChangeRow(c) { onOpen(c) }
                    Spacer(Modifier.height(Space.s))
                }
            }
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun ChangeRow(c: FileChange, onClick: () -> Unit) {
    BkCard(onClick = onClick, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChangeBadge(c.status)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName(c.file), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val name = fileName(c.file)
                val dir = c.file.removeSuffix(name).trimEnd('/', '\\')
                if (dir.isNotEmpty()) Mono(dir, color = TextMuted, size = 11)
                if (c.status == "renamed" && c.oldPath != null) Mono("from ${c.oldPath}", color = TextMuted, size = 11)
                val tools = c.edits.mapNotNull { it.tool?.takeIf(String::isNotBlank) }.distinct()
                val last = c.edits.mapNotNull { parseInstant(it.time) }.maxOrNull()
                val meta = listOfNotNull(
                    plural(c.edits.size, "edit").takeIf { c.edits.isNotEmpty() },
                    tools.takeIf { it.isNotEmpty() }?.joinToString(", "),
                    last?.let { shortClock(it) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            val (adds, dels) = changeCounts(c)
            PlusMinus(adds, dels)
        }
    }
}

// ================================================================== Files

/** Every file the session touched, from the history's changes and the timeline's read/edit/write entries. */
internal fun touchedFiles(h: SessionHistory): List<FileMapEntry> {
    val promptTimes = h.prompts.mapNotNull { parseInstant(it.time) }.sorted()
    fun turnOf(time: String?): Int = parseInstant(time)?.let { t -> promptTimes.count { it <= t } } ?: 0

    class Acc(val path: String) {
        val actions = LinkedHashSet<String>()
        var first = 0
        var last = 0
        var additions = 0
        var deletions = 0
        var failed = false
        fun turn(t: Int) {
            if (t <= 0) return
            if (first == 0 || t < first) first = t
            if (t > last) last = t
        }
    }
    val byKey = LinkedHashMap<String, Acc>()
    fun acc(path: String) = byKey.getOrPut(normPath(path)) { Acc(path) }

    val changed = h.changes.associateBy { normPath(it.file) }
    for (e in h.timeline) {
        val f = e.file?.takeIf { it.isNotBlank() } ?: continue
        val a = acc(f)
        when (e.kind) {
            "read" -> a.actions += "read"
            "edit", "write", "patch" -> if (normPath(f) !in changed) a.actions += "edited"
        }
        if (e.status == "error") a.failed = true
        a.turn(turnOf(e.time))
    }
    for (c in h.changes) {
        val a = acc(c.file)
        a.actions += when (c.status) {
            "added" -> "created"
            "deleted" -> "deleted"
            "renamed" -> "renamed"
            else -> "edited"
        }
        a.additions = c.additions
        a.deletions = c.deletions
        c.edits.forEach { a.turn(turnOf(it.time)) }
    }
    return byKey.values.map {
        val actions = it.actions.sortedBy { x -> listOf("created", "edited", "renamed", "deleted", "read").indexOf(x) }
        FileMapEntry(it.path, actions, it.first, it.last, it.additions, it.deletions, it.failed)
    }
}
