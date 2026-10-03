package com.bambookit.android.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.ChangedFile
import com.bambookit.android.data.DiagramNode
import com.bambookit.android.data.FileMapEntry
import com.bambookit.android.data.ProjectDiagram
import com.bambookit.android.data.SessionDetail
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.DiagramArrow
import com.bambookit.android.presentation.theme.DiagramArrowUsed
import com.bambookit.android.presentation.theme.DividerSubtle
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Paths are compared case-insensitively with "/" separators (Windows projects). */
internal fun normPath(path: String) = path.replace('\\', '/').removePrefix("./").trim('/').lowercase()

internal fun actionColor(action: String): Pair<Color, Color> = when (action) {
    "created" -> StatusSuccess to StatusSuccessTint
    "edited" -> StatusRunning to StatusRunningTint
    "deleted" -> StatusFailed to StatusFailedTint
    else -> TextSecondary to NeutralTint
}

private fun turnLabel(e: FileMapEntry): String? = when {
    e.lastTurn <= 0 -> null
    e.firstTurn <= 0 || e.firstTurn == e.lastTurn -> "turn ${e.lastTurn}"
    else -> "turns ${e.firstTurn}–${e.lastTurn}"
}

// ================================================================== Files (file map)

private enum class FileFilter(val label: String) { All("All"), Changed("Changed"), ReadOnly("Read only") }

private sealed interface TreeRow { val key: String; val depth: Int }
private data class FolderRow(override val key: String, val name: String, override val depth: Int, val files: Int, val additions: Int, val deletions: Int, val collapsed: Boolean) : TreeRow
private data class FileRow(override val key: String, val entry: FileMapEntry, override val depth: Int) : TreeRow

private class TreeFolder(val path: String, val name: String) {
    val folders = java.util.TreeMap<String, TreeFolder>(String.CASE_INSENSITIVE_ORDER)
    val files = mutableListOf<FileMapEntry>()
    var count = 0
    var additions = 0
    var deletions = 0
}

private fun buildTree(entries: List<FileMapEntry>): TreeFolder {
    val root = TreeFolder("", "")
    for (e in entries) {
        val segments = e.path.replace('\\', '/').trim('/').split('/').filter { it.isNotEmpty() }
        var f = root
        for (seg in segments.dropLast(1)) {
            val parent = f
            f = parent.folders.getOrPut(seg) { TreeFolder(if (parent.path.isEmpty()) seg else "${parent.path}/$seg", seg) }
            f.count++
            f.additions += e.additions
            f.deletions += e.deletions
        }
        f.files += e
    }
    return root
}

private fun flatten(folder: TreeFolder, depth: Int, collapsed: Set<String>, out: MutableList<TreeRow>) {
    for (child in folder.folders.values) {
        // Single-child folder chains (src/main/java/…) collapse into one row.
        var c = child
        var name = c.name
        while (c.files.isEmpty() && c.folders.size == 1) {
            c = c.folders.values.first()
            name += "/" + c.name
        }
        val isCollapsed = c.path in collapsed
        out += FolderRow("d:" + c.path, name, depth, c.count, c.additions, c.deletions, isCollapsed)
        if (!isCollapsed) flatten(c, depth + 1, collapsed, out)
    }
    for (file in folder.files.sortedBy { it.path.substringAfterLast('/').lowercase() }) out += FileRow("f:" + file.path, file, depth)
}

/** Maps a file map path onto the session's changed-file path, which GET_DIFF matches exactly. */
private fun diffPathFor(entry: FileMapEntry, changes: List<ChangedFile>): String {
    val n = normPath(entry.path)
    return changes.firstOrNull { normPath(it.file) == n || normPath(it.file).endsWith("/$n") }?.file ?: entry.path
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileMapPane(d: SessionDetail, pcTitle: String, onLoad: () -> Unit, onOpenDiff: (String) -> Unit, onOpenFile: (String) -> Unit) {
    LaunchedEffect(d.sessionId) { if (d.fileMap == null) onLoad() }
    val fm = d.fileMap
    var filter by rememberSaveable { mutableStateOf(FileFilter.All) }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    when {
        fm == null || (!fm.loaded && fm.loading) -> LoadingState("Reading the file map from $pcTitle…")
        fm.error != null && !fm.loaded -> ContentUnavailable(fm.error, pcTitle, fm.loading, onLoad)
        else -> SessionRefresh(fm.loading, onLoad) {
            val entries = fm.entries
            val shown = remember(entries, filter) {
                when (filter) {
                    FileFilter.All -> entries
                    FileFilter.Changed -> entries.filter { it.changed }
                    FileFilter.ReadOnly -> entries.filter { !it.changed }
                }
            }
            val rows = remember(shown, collapsed) { mutableListOf<TreeRow>().also { flatten(buildTree(shown), 0, collapsed, it) } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xl)) {
                if (fm.error != null) item { StaleBanner(fm.error, fm.loading, onLoad) }
                item {
                    Column(Modifier.padding(horizontal = Space.screen).padding(top = Space.s)) {
                        val created = entries.count { "created" in it.actions }
                        val edited = entries.count { "edited" in it.actions }
                        val deleted = entries.count { "deleted" in it.actions }
                        val readOnly = entries.count { !it.changed }
                        Text(
                            listOfNotNull(
                                plural(entries.size, "file"),
                                created.takeIf { it > 0 }?.let { "$it created" },
                                edited.takeIf { it > 0 }?.let { "$it edited" },
                                deleted.takeIf { it > 0 }?.let { "$it deleted" },
                                readOnly.takeIf { it > 0 }?.let { "$it read only" },
                            ).joinToString(" · "),
                            color = TextSecondary, fontSize = 13.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.padding(vertical = Space.s).horizontalScroll(rememberScrollState())) {
                            FileFilter.entries.forEach { f ->
                                FilterChip(
                                    selected = filter == f, onClick = { filter = f }, label = { Text(f.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BambooGreenSubtle, selectedLabelColor = TextPrimary,
                                        containerColor = BambooSurface, labelColor = TextSecondary,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = filter == f, borderColor = BambooBorder, selectedBorderColor = BambooBorderStrong),
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = BambooBorder)
                }
                if (entries.isEmpty()) item {
                    EmptyState("No files yet", "Files the agent reads or changes in this session appear here.", Icons.Filled.Description)
                } else if (shown.isEmpty()) item {
                    EmptyState(if (filter == FileFilter.Changed) "No changed files" else "No read-only files", "Try another filter.", Icons.Filled.Description)
                }
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is FolderRow -> FolderTreeRow(row) {
                            val path = row.key.removePrefix("d:")
                            collapsed = if (row.collapsed) collapsed - path else collapsed + path
                        }
                        is FileRow -> FileTreeRow(row) {
                            if (row.entry.changed) onOpenDiff(diffPathFor(row.entry, d.changes)) else onOpenFile(row.entry.path)
                        }
                    }
                    HorizontalDivider(color = DividerSubtle)
                }
            }
        }
    }
}

@Composable
private fun FolderTreeRow(row: FolderRow, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = (Space.screen.value + row.depth * 16).dp, end = Space.screen, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (row.collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
            if (row.collapsed) "Expand" else "Collapse", tint = TextMuted, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(2.dp))
        Icon(if (row.collapsed) Icons.Filled.Folder else Icons.Filled.FolderOpen, null, tint = TextSecondary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(row.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Text(plural(row.files, "file"), color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        if (row.additions > 0 || row.deletions > 0) {
            Mono("+${row.additions}", color = StatusSuccess, size = 11)
            Spacer(Modifier.width(5.dp))
            Mono("−${row.deletions}", color = StatusFailed, size = 11)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileTreeRow(row: FileRow, onClick: () -> Unit) {
    val e = row.entry
    val primary = e.actions.firstOrNull { it != "read" } ?: "read"
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = (Space.screen.value + row.depth * 16 + 20).dp, end = Space.screen, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (e.failed) Icons.Filled.ErrorOutline else Icons.Filled.Description, if (e.failed) "A tool call on this file failed" else null,
            tint = if (e.failed) StatusFailed else actionColor(primary).first, modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(e.path.substringAfterLast('/').substringAfterLast('\\'), color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                e.actions.forEach { a ->
                    val (fg, bg) = actionColor(a)
                    Text(a, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(ChipShape).background(bg).padding(horizontal = 7.dp, vertical = 1.dp))
                }
                if (e.failed) Text("failed", color = StatusFailed, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(ChipShape).background(StatusFailedTint).padding(horizontal = 7.dp, vertical = 1.dp))
                turnLabel(e)?.let { Text(it, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 1.dp)) }
            }
        }
        if (e.changed && (e.additions > 0 || e.deletions > 0)) {
            Spacer(Modifier.width(8.dp))
            Mono("+${e.additions}", color = StatusSuccess, size = 11)
            Spacer(Modifier.width(5.dp))
            Mono("−${e.deletions}", color = StatusFailed, size = 11)
        }
    }
}

// ================================================================== Diagram

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagramPane(d: SessionDetail, pcTitle: String, onLoad: () -> Unit, onLoadFileMap: () -> Unit, onOpenFile: (String) -> Unit) {
    LaunchedEffect(d.sessionId) {
        if (d.diagram == null) onLoad()
        if (d.fileMap == null) onLoadFileMap()
    }
    val view = d.diagram
    val used = remember(d.fileMap?.entries) { d.fileMap?.entries.orEmpty().map { normPath(it.path) }.toSet() }
    var sheetNode by remember { mutableStateOf<DiagramNode?>(null) }
    var fitRequest by remember { mutableIntStateOf(0) }
    val dg = view?.diagram

    when {
        view == null || (!view.loaded && view.loading) -> LoadingState("Scanning the project on $pcTitle…")
        view.error != null && !view.loaded -> ContentUnavailable(view.error, pcTitle, view.loading, onLoad)
        dg == null || dg.nodes.isEmpty() -> Column(Modifier.fillMaxSize()) {
            EmptyState("No source files found", "The project folder on $pcTitle has no source files to draw.", Icons.Filled.AccountTree, actionLabel = "Rescan", onAction = onLoad)
        }
        else -> Column(Modifier.fillMaxSize()) {
            val usedNodes = dg.nodes.count { n -> n.files.any { normPath(it) in used } }
            Row(Modifier.fillMaxWidth().padding(start = Space.screen, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${plural(dg.files, "source file")} · ${plural(dg.nodes.size, "component")}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (usedNodes > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(StatusRunning, 7)
                        Spacer(Modifier.width(5.dp))
                        Text("${plural(usedNodes, "component")} used in this session", color = TextSecondary, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = { fitRequest++ }) { Icon(Icons.Filled.FitScreen, "Fit to screen", tint = TextSecondary) }
                TextButton(onClick = onLoad, enabled = !view.loading) {
                    if (view.loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rescan")
                }
            }
            if (dg.truncated) {
                Banner(
                    "Large project: showing the first ${dg.files} source files", Icons.Filled.Info, color = StatusWarning, tint = StatusWarningTint,
                    modifier = Modifier.padding(horizontal = Space.screen).padding(bottom = Space.s),
                )
            }
            if (view.error != null) {
                Banner(
                    view.error.message, Icons.Filled.ErrorOutline, color = StatusWarning, tint = StatusWarningTint, title = "Rescan failed — showing the last scan",
                    actionLabel = "Retry", busy = view.loading, onAction = onLoad,
                    modifier = Modifier.padding(horizontal = Space.screen).padding(bottom = Space.s),
                )
            }
            DiagramCanvas(
                dg, used, fitRequest,
                onTap = { n -> if (n.folder) sheetNode = n else n.files.firstOrNull()?.let(onOpenFile) },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.m)
                    .clip(RoundedCornerShape(14.dp)).border(1.dp, BambooBorder, RoundedCornerShape(14.dp)),
            )
            Text(
                "Pinch to zoom · drag to pan · double-tap to fit · tap a box to open it",
                color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.s),
            )
        }
    }

    sheetNode?.let { node ->
        ModalBottomSheet(onDismissRequest = { sheetNode = null }, containerColor = BambooSurfaceElevated) {
            Column(Modifier.padding(horizontal = Space.screen)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Filled.FolderOpen, size = 34.dp)
                    Spacer(Modifier.width(Space.m))
                    Column {
                        Text(node.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(plural(node.files.size, "file"), color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(Space.s))
            }
            HorizontalDivider(color = BambooBorder)
            LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
                items(node.files, key = { it }) { path ->
                    val isUsed = normPath(path) in used
                    Row(
                        Modifier.fillMaxWidth().clickable { sheetNode = null; onOpenFile(path) }.padding(horizontal = Space.screen, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Description, null, tint = if (isUsed) StatusRunning else TextSecondary, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(path.substringAfterLast('/'), color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { Mono(it, color = TextMuted, size = 11) }
                        }
                        if (isUsed) Chip("used", StatusRunning, StatusRunningTint)
                    }
                }
            }
        }
    }
}

private class NodeText(val label: TextLayoutResult, val sub: TextLayoutResult)

/**
 * The project diagram drawn on a Canvas. Diagram units are treated as dp; the layout (top-left of
 * each node) comes from the PC. Pan/pinch-zoom with two fingers, double-tap to fit, tap to open.
 */
@Composable
private fun DiagramCanvas(dg: ProjectDiagram, used: Set<String>, fitRequest: Int, onTap: (DiagramNode) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val nodeW = dg.nodeWidth.toFloat()
    val nodeH = dg.nodeHeight.toFloat()
    val byId = remember(dg) { dg.nodes.associateBy { it.id } }
    val usedIds = remember(dg, used) { dg.nodes.filter { n -> n.files.any { normPath(it) in used } }.map { it.id }.toSet() }
    val texts = remember(dg, measurer, density) {
        val maxW = ((nodeW - 24f) * density).toInt().coerceAtLeast(1)
        dg.nodes.associate { n ->
            n.id to NodeText(
                measurer.measure(
                    AnnotatedString(n.label), style = TextStyle(color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxW),
                ),
                measurer.measure(
                    AnnotatedString(if (n.folder) plural(n.files.size, "file") else if (n.where.isNotEmpty()) "${n.where}/" else "project root"),
                    style = TextStyle(color = TextSecondary, fontSize = 11.sp),
                    overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = maxW),
                ),
            )
        }
    }
    // Content bounds in diagram units (falls back to the PC's width/height).
    val bounds = remember(dg) {
        if (dg.nodes.isEmpty()) floatArrayOf(0f, 0f, dg.width.toFloat(), dg.height.toFloat())
        else floatArrayOf(
            dg.nodes.minOf { it.x }.toFloat() - 24f, dg.nodes.minOf { it.y }.toFloat() - 24f,
            dg.nodes.maxOf { it.x }.toFloat() + nodeW + 24f, dg.nodes.maxOf { it.y }.toFloat() + nodeH + 24f,
        )
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(dg) { mutableFloatStateOf(1f) }
    var offset by remember(dg) { mutableStateOf(Offset.Zero) }
    var fitted by remember(dg) { mutableStateOf(false) }

    fun fit() {
        if (size.width == 0 || size.height == 0) return
        val w = (bounds[2] - bounds[0]) * density
        val h = (bounds[3] - bounds[1]) * density
        val s = min(size.width / w, size.height / h).coerceIn(0.08f, 1.5f)
        scale = s
        offset = Offset((size.width - w * s) / 2f - bounds[0] * density * s, max(0f, (size.height - h * s) / 2f) - bounds[1] * density * s)
    }
    LaunchedEffect(size, dg) { if (!fitted && size != IntSize.Zero) { fit(); fitted = true } }
    LaunchedEffect(fitRequest) { if (fitRequest > 0) fit() }

    Box(
        modifier
            .background(CodeBlockBackground)
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(dg) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(0.05f, 4f)
                    offset = centroid - (centroid - offset) * (next / scale) + pan
                    scale = next
                }
            }
            .pointerInput(dg) {
                detectTapGestures(
                    onDoubleTap = { fit() },
                    onTap = { p ->
                        val ux = (p.x - offset.x) / scale / density
                        val uy = (p.y - offset.y) / scale / density
                        dg.nodes.lastOrNull { ux >= it.x && ux <= it.x + nodeW && uy >= it.y && uy <= it.y + nodeH }?.let(onTap)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                val u = density
                for (e in dg.edges) {
                    val a = byId[e.from] ?: continue
                    val b = byId[e.to] ?: continue
                    val hot = a.id in usedIds && b.id in usedIds
                    drawEdge(a, b, nodeW * u, nodeH * u, u, e.count, if (hot) DiagramArrowUsed else DiagramArrow)
                }
                for (n in dg.nodes) {
                    val isUsed = n.id in usedIds
                    val tl = Offset(n.x.toFloat() * u, n.y.toFloat() * u)
                    val sz = Size(nodeW * u, nodeH * u)
                    val r = CornerRadius(10f * u)
                    drawRoundRect(BambooSurface, tl, sz, r)
                    if (isUsed) drawRoundRect(StatusRunningTint, tl, sz, r)
                    if (n.folder) drawRoundRect(BambooSurfaceElevated, tl, Size(5f * u, sz.height), CornerRadius(10f * u))
                    drawRoundRect(if (isUsed) StatusRunning else BambooBorderStrong, tl, sz, r, style = Stroke(width = (if (isUsed) 2f else 1f) * u))
                    texts[n.id]?.let { t ->
                        val textTop = tl.y + (sz.height - t.label.size.height - t.sub.size.height - 2f * u) / 2f
                        drawText(t.label, topLeft = Offset(tl.x + 12f * u, textTop))
                        drawText(t.sub, topLeft = Offset(tl.x + 12f * u, textTop + t.label.size.height + 2f * u))
                    }
                    if (isUsed) drawCircle(StatusRunning, radius = 3.5f * u, center = Offset(tl.x + sz.width - 10f * u, tl.y + 10f * u))
                }
            }
        }
    }
}

/** Cubic bezier from `from` to `to` with an arrowhead: bottom→top going down, top→bottom going up, sideways within a row. */
private fun DrawScope.drawEdge(a: DiagramNode, b: DiagramNode, w: Float, h: Float, u: Float, count: Int, color: Color) {
    val ax = a.x.toFloat() * u
    val ay = a.y.toFloat() * u
    val bx = b.x.toFloat() * u
    val by = b.y.toFloat() * u
    val start: Offset
    val end: Offset
    val c1: Offset
    val c2: Offset
    if (abs(by - ay) < h / 2f) {
        val right = bx > ax
        start = Offset(if (right) ax + w else ax, ay + h / 2f)
        end = Offset(if (right) bx else bx + w, by + h / 2f)
        val dx = (end.x - start.x) / 2f
        c1 = Offset(start.x + dx, start.y)
        c2 = Offset(end.x - dx, end.y)
    } else {
        val down = by > ay
        start = Offset(ax + w / 2f, if (down) ay + h else ay)
        end = Offset(bx + w / 2f, if (down) by else by + h)
        val dy = (end.y - start.y) / 2f
        c1 = Offset(start.x, start.y + dy)
        c2 = Offset(end.x, end.y - dy)
    }
    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
    }
    drawPath(path, color, style = Stroke(width = (1.1f + min(count, 6) * 0.3f) * u))
    var dir = end - c2
    val len = sqrt(dir.x * dir.x + dir.y * dir.y)
    dir = if (len < 0.01f) Offset(0f, 1f) else dir / len
    val perp = Offset(-dir.y, dir.x)
    val head = 8f * u
    val arrow = Path().apply {
        moveTo(end.x, end.y)
        val back = end - dir * head
        lineTo(back.x + perp.x * head * 0.5f, back.y + perp.y * head * 0.5f)
        lineTo(back.x - perp.x * head * 0.5f, back.y - perp.y * head * 0.5f)
        close()
    }
    drawPath(arrow, color)
}
