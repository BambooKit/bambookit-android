package com.bambookit.android.presentation.screens

import com.bambookit.android.data.FileState
import com.bambookit.android.data.fileState
import com.bambookit.android.data.diffStats
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bambookit.android.data.FileChange
import com.bambookit.android.data.DiffCode
import com.bambookit.android.data.DiffHunkHeader
import com.bambookit.android.data.DiffLine
import com.bambookit.android.data.DiffNote
import com.bambookit.android.data.DiffSection
import com.bambookit.android.data.parseUnifiedDiff
import com.bambookit.android.data.FileEdit
import com.bambookit.android.data.VersionsView
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.ChangeAdded
import com.bambookit.android.presentation.theme.ChangeAddedTint
import com.bambookit.android.presentation.theme.ChangeDeleted
import com.bambookit.android.presentation.theme.ChangeDeletedTint
import com.bambookit.android.presentation.theme.ChangeModified
import com.bambookit.android.presentation.theme.ChangeModifiedTint
import com.bambookit.android.presentation.theme.ChangeRenamed
import com.bambookit.android.presentation.theme.ChangeRenamedTint
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.DiffAddedBg
import com.bambookit.android.presentation.theme.DiffAddedGutter
import com.bambookit.android.presentation.theme.DiffAddedText
import com.bambookit.android.presentation.theme.DiffContextText
import com.bambookit.android.presentation.theme.DiffHunk
import com.bambookit.android.presentation.theme.DiffHunkBg
import com.bambookit.android.presentation.theme.DiffRemovedBg
import com.bambookit.android.presentation.theme.DiffRemovedGutter
import com.bambookit.android.presentation.theme.DiffRemovedText
import com.bambookit.android.presentation.theme.LineNumber
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import com.bambookit.android.presentation.theme.Transparent
import kotlin.math.max

// ================================================================== change status

/** Badge letter, label and colors for a change status (modified, added, deleted, renamed). */
internal data class ChangeStyle(val letter: String, val label: String, val headline: String, val color: Color, val tint: Color)

internal fun changeStyle(status: String?): ChangeStyle = when (status) {
    "added" -> ChangeStyle("A", "Added", "NEW FILE", ChangeAdded, ChangeAddedTint)
    "deleted" -> ChangeStyle("D", "Deleted", "DELETED FILE", ChangeDeleted, ChangeDeletedTint)
    "renamed" -> ChangeStyle("R", "Renamed", "RENAMED", ChangeRenamed, ChangeRenamedTint)
    else -> ChangeStyle("M", "Modified", "MODIFIED", ChangeModified, ChangeModifiedTint)
}

@Composable
internal fun ChangeBadge(status: String?, size: Int = 20) {
    val st = changeStyle(status)
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(5.dp)).background(st.tint), contentAlignment = Alignment.Center) {
        Text(st.letter, color = st.color, fontSize = (size * 0.55f).sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun PlusMinus(additions: Int, deletions: Int, size: Int = 12) {
    Mono("+$additions", color = StatusSuccess, size = size)
    Spacer(Modifier.width(6.dp))
    Mono("−$deletions", color = StatusFailed, size = size)
}

// ================================================================== unified diff

private const val MAX_DIFF_LINE = 2000

internal fun editLabel(index: Int, e: FileEdit): String =
    listOfNotNull("Change ${index + 1}", shortClock(parseInstant(e.time)).ifEmpty { null }, e.tool, "(+${e.additions} −${e.deletions})").joinToString(" · ").replace(" · (", " (")

/** One edit, or all edits of the file one after another ([selected] = -1) with a header per change. */
private fun diffLinesFor(change: FileChange, selected: Int): List<DiffLine> {
    val edits = change.edits
    if (selected in edits.indices) return parseUnifiedDiff(edits[selected].patch)
    if (edits.size == 1) return parseUnifiedDiff(edits[0].patch)
    return buildList {
        edits.forEachIndexed { i, e ->
            val lines = parseUnifiedDiff(e.patch)
            if (lines.isNotEmpty()) {
                add(DiffSection(editLabel(i, e)))
                addAll(lines)
            }
        }
    }
}

// ================================================================== screen

/** Where a changed file belongs: shown in the file's header. */
data class ChangeContext(val project: String? = null, val branch: String? = null, val session: String? = null, val agent: String? = null)

/** Headline for the explicit file states (instead of an error or an empty diff). */
internal fun stateHeadline(state: FileState): Pair<String, Color> = when (state) {
    FileState.New -> "NEW FILE" to ChangeAdded
    FileState.Deleted -> "DELETED FILE" to ChangeDeleted
    FileState.Renamed -> "RENAMED" to ChangeRenamed
    FileState.Binary -> "BINARY FILE" to ChangeModified
    FileState.TooLarge -> "TOO LARGE" to ChangeModified
    FileState.Modified -> "MODIFIED" to ChangeModified
}

/** The +/− of a change: the PC's counts, or counted from the recorded patches when the PC sent none. */
internal fun changeCounts(change: FileChange): Pair<Int, Int> {
    if (change.additions > 0 || change.deletions > 0) return change.additions to change.deletions
    val st = change.edits.map { diffStats(it.patch) }
    return st.sumOf { it.additions } to st.sumOf { it.deletions }
}

internal fun fileName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')

private val FileModes = listOf("Diff", "Before", "After")

/**
 * A changed file: status, +/−, and a toggle between the GitHub-style diff (from the session's recorded
 * patches, available offline) and the full Before / After text (read live from the PC).
 */
@Composable
fun FileChangeScreen(
    change: FileChange,
    versions: VersionsView?,
    pcTitle: String,
    onClose: () -> Unit,
    onLoadVersions: () -> Unit,
    context: ChangeContext = ChangeContext(),
) {
    if (LocalAppLocked.current) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(BambooObsidian)) {
            // Saved across rotation.
            var mode by rememberSaveable(change.file) { mutableIntStateOf(0) }
            var selected by rememberSaveable(change.file) { mutableIntStateOf(-1) }
            var searching by rememberSaveable(change.file) { mutableStateOf(false) }
            val v = versions?.takeIf { it.path == change.file }
            val text = when (mode) {
                1 -> v?.versions?.before
                2 -> v?.versions?.after
                else -> null
            }
            LaunchedEffect(mode, change.file) {
                searching = false
                if (mode != 0 && (v == null || (v.error != null && !v.loading))) onLoadVersions()
            }

            ScreenTopBar(
                title = fileName(change.file),
                subtitle = change.file,
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") } },
                actions = {
                    if (text != null) IconButton(onClick = { searching = !searching }) { Icon(Icons.Filled.Search, "Find in file") }
                    CodeMenu(change.file, text)
                },
            )
            val state = fileState(change.status, change.edits.map { it.patch }, v?.versions?.truncated == true)
            val (headline, headColor) = stateHeadline(state)
            val (adds, dels) = remember(change) { changeCounts(change) }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.screen).padding(bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    headline, color = headColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                    modifier = Modifier.clip(ChipShape).background(headColor.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(Space.s))
                PlusMinus(adds, dels)
                Spacer(Modifier.weight(1f))
                if (change.edits.isNotEmpty()) Text(plural(change.edits.size, "edit"), color = TextMuted, fontSize = 12.sp)
            }
            ChangeFacts(change, context)
            if (change.status == "renamed" && change.oldPath != null) {
                Text(
                    "Renamed from ${change.oldPath}", color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = Space.screen).padding(bottom = Space.s),
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.screen).padding(bottom = Space.s)) {
                FileModes.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = mode == i, onClick = { mode = i },
                        shape = SegmentedButtonDefaults.itemShape(i, FileModes.size), icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = BambooGreenSubtle, activeContentColor = TextPrimary, activeBorderColor = BambooBorderStrong,
                            inactiveContainerColor = BambooObsidian, inactiveContentColor = TextSecondary, inactiveBorderColor = BambooBorder,
                        ),
                    ) { Text(label, fontSize = 13.sp) }
                }
            }
            HorizontalDivider(color = BambooBorder)
            Box(Modifier.weight(1f)) {
                when (mode) {
                    0 -> DiffPane(change, state, selected, onSelect = { selected = it }, onShowAfter = { mode = 2 })
                    else -> VersionPane(change, v, before = mode == 1, pcTitle, searching, onCloseSearch = { searching = false }, onRetry = onLoadVersions, onShowDiff = { mode = 0 })
                }
            }
        }
    }
}

@Composable
private fun DiffPane(change: FileChange, state: FileState, selected: Int, onSelect: (Int) -> Unit, onShowAfter: () -> Unit) {
    val lines = remember(change, selected) { diffLinesFor(change, selected) }
    Column(Modifier.fillMaxSize()) {
        if (change.edits.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                EditChip("All changes", selected == -1) { onSelect(-1) }
                change.edits.forEachIndexed { i, e -> EditChip(editLabel(i, e), selected == i) { onSelect(i) } }
            }
        }
        if (lines.isEmpty()) {
            val (title, body) = when (state) {
                FileState.Binary -> "Binary file" to "This file is binary, so there is no line-by-line diff."
                FileState.TooLarge -> "Too large to show" to "The file is too large for a line-by-line diff on the phone. Before and After show its first part."
                FileState.New -> "New file" to "The session created this file without a recorded patch. After shows its full content while the PC is online."
                FileState.Deleted -> "Deleted file" to "The file was deleted in this session. Before shows what it contained, when an earlier version is known."
                FileState.Renamed -> "Renamed without changes" to "The file was moved or renamed; its content did not change."
                FileState.Modified -> "No line-by-line diff recorded" to "The PC found this change in its snapshot without a patch. Before and After show the full file while the PC is online."
            }
            EmptyState(
                title, body, Icons.Filled.Description,
                actionLabel = when (state) { FileState.Deleted, FileState.Binary -> null; else -> "Show After" }, onAction = onShowAfter,
            )
        } else DiffLines(lines)
    }
}

/** Full path, project, branch, session, agent, last change time and the tools that changed the file. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangeFacts(change: FileChange, ctx: ChangeContext) {
    val last = change.edits.mapNotNull { parseInstant(it.time) }.maxOrNull()
    val tools = change.edits.mapNotNull { it.tool?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount()
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.screen).padding(bottom = Space.s)) {
        SelectionContainer { Text(change.file, color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            ctx.project?.let { Chip(it, TextSecondary, icon = Icons.Filled.Folder) }
            ctx.branch?.let { Chip(it, TextSecondary, icon = Icons.AutoMirrored.Filled.CallSplit) }
            ctx.session?.let { Chip(it, TextSecondary, icon = Icons.Filled.ChatBubbleOutline) }
            ctx.agent?.let { Chip(agentLabel(it), TextSecondary, icon = Icons.Filled.SmartToy) }
            last?.let { Chip("${shortClock(it)} · ${relative(it.toString())}", TextMuted, icon = Icons.Filled.Schedule) }
            tools.forEach { (t, n) -> Chip(if (n > 1) "$t ×$n" else t, TextSecondary, icon = Icons.Filled.Build) }
        }
    }
}

@Composable
private fun EditChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BambooGreenSubtle, selectedLabelColor = TextPrimary,
            containerColor = BambooSurface, labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = BambooBorder, selectedBorderColor = BambooBorderStrong),
    )
}

/** GitHub-style unified diff: two line-number gutters, +/− marker, colored rows, horizontal scroll. */
@Composable
private fun DiffLines(lines: List<DiffLine>) {
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charPx = remember(measurer) { measurer.measure(AnnotatedString("0".repeat(10)), style).size.width / 10f }
    val digits = remember(lines) {
        max(2, lines.maxOfOrNull { l -> if (l is DiffCode) max(l.oldNo ?: 0, l.newNo ?: 0) else 0 }?.toString()?.length ?: 2)
    }
    val longest = remember(lines) {
        lines.maxOfOrNull { l -> when (l) { is DiffCode -> l.text.length; is DiffHunkHeader -> l.text.length; is DiffSection -> l.text.length; is DiffNote -> l.text.length } }
            ?.coerceAtMost(MAX_DIFF_LINE) ?: 0
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(CodeBlockBackground)) {
        val viewportPx = with(density) { maxWidth.toPx() }
        // Two gutters (digits + padding each), marker column, text, and some end padding.
        val contentPx = kotlin.math.min(MAX_CONTENT_PX, max(viewportPx, (digits * 2 + 3 + longest) * charPx + with(density) { 56.dp.toPx() }))
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            SelectionContainer {
                LazyColumn(Modifier.width(with(density) { contentPx.toDp() }).fillMaxHeight(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(lines.size) { i ->
                        when (val l = lines[i]) {
                            is DiffSection -> Text(
                                l.text, style = style, color = TextSecondary, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                modifier = Modifier.fillMaxWidth().background(BambooSurfaceElevated).padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                            is DiffHunkHeader -> Text(
                                l.text.take(MAX_DIFF_LINE), style = style, color = DiffHunk, softWrap = false, maxLines = 1,
                                modifier = Modifier.fillMaxWidth().background(DiffHunkBg).padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                            is DiffNote -> Text(
                                l.text, style = style, color = TextMuted, softWrap = false, maxLines = 1,
                                modifier = Modifier.fillMaxWidth().padding(start = 10.dp + with(density) { ((digits * 2 + 3) * charPx).toDp() }),
                            )
                            is DiffCode -> {
                                val (bg, gutter, fg) = when (l.type) {
                                    '+' -> Triple(DiffAddedBg, DiffAddedGutter, DiffAddedText)
                                    '-' -> Triple(DiffRemovedBg, DiffRemovedGutter, DiffRemovedText)
                                    else -> Triple(Transparent, Transparent, DiffContextText)
                                }
                                Row(Modifier.fillMaxWidth().background(bg)) {
                                    Text(
                                        (l.oldNo?.toString() ?: "").padStart(digits) + " " + (l.newNo?.toString() ?: "").padStart(digits),
                                        style = style, color = LineNumber, maxLines = 1,
                                        modifier = Modifier.background(gutter).padding(horizontal = 6.dp),
                                    )
                                    Text(
                                        (if (l.type == ' ') " " else l.type.toString()) + " " + l.text.take(MAX_DIFF_LINE),
                                        style = style, color = fg, softWrap = false, maxLines = 1,
                                        modifier = Modifier.padding(start = 6.dp, end = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionPane(
    change: FileChange,
    view: VersionsView?,
    before: Boolean,
    pcTitle: String,
    searching: Boolean,
    onCloseSearch: () -> Unit,
    onRetry: () -> Unit,
    onShowDiff: () -> Unit,
) {
    val err = view?.error
    val v = view?.versions
    when {
        view == null || view.loading -> LoadingState("Reading ${if (before) "the earlier version" else "the current file"} from $pcTitle…")
        err != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (err.desktopUnavailable) ErrorState(
                "Before and After need $pcTitle online",
                "Full file versions are read live from the PC and never stored. The Diff tab shows the changes saved in the session history.",
                Icons.Filled.CloudOff, onRetry = onRetry, color = StatusWarning, diagnosis = err.diagnosis,
            ) else ErrorState(
                errorTitle(err, pcTitle, "Couldn't read this file"), errorMessage(err, pcTitle), Icons.Filled.ErrorOutline, onRetry = onRetry,
                color = if (err.desktopOutdated || err.timedOut) StatusWarning else StatusFailed,
                diagnosis = err.diagnosis,
            )
            TextButton(onClick = onShowDiff) { Text("Show diff") }
        }
        v == null -> Unit
        else -> Column(Modifier.fillMaxSize()) {
            val source = if (before) when (v.beforeSource) {
                "session" -> "Before this session"
                "git" -> "Before = last git commit"
                else -> "No earlier version available"
            } else "Current file on $pcTitle"
            Row(Modifier.fillMaxWidth().background(BambooSurface).padding(horizontal = Space.screen, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(source, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    v.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = TextSecondary, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    if (v.truncated) Text("Large file: only the first part is shown.", color = StatusWarning, fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = BambooBorder)
            val text = if (before) v.before else v.after
            if (text == null) {
                val (title, body) = when {
                    before && (v.status == "added" || change.status == "added") -> "New file" to "This file did not exist before this session."
                    before -> "No earlier version" to "Neither the session's own edits nor git have an earlier version of this file."
                    v.status == "deleted" || change.status == "deleted" -> "Deleted" to "This file was deleted in this session."
                    else -> "Not found" to "The file is not on $pcTitle any more."
                }
                EmptyState(title, body, Icons.Filled.Description)
            } else CodeContent(change.file, text, searching, onCloseSearch)
        }
    }
}
