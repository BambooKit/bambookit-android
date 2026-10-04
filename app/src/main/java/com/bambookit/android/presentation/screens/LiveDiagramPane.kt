package com.bambookit.android.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.LiveFilter
import com.bambookit.android.data.LiveInput
import com.bambookit.android.data.LiveNode
import com.bambookit.android.data.LiveStatus
import com.bambookit.android.data.SessionDetail
import com.bambookit.android.data.buildLiveGraph
import com.bambookit.android.data.historyMoments
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.DiagramArrow
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusRunningTint
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext
import java.time.Instant

private val DiagramModes = listOf("Live", "History", "Project map")

/** Diagram tab: the live state, the same diagram at a point in the session, or the project map (components). */
@Composable
internal fun DiagramTab(store: BambooStore, d: SessionDetail, pcTitle: String, projectMap: @Composable () -> Unit, onOpenChange: (String) -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.s)) {
            DiagramModes.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i, onClick = { mode = i }, shape = SegmentedButtonDefaults.itemShape(i, DiagramModes.size), icon = {},
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = BambooGreenSubtle, activeContentColor = TextPrimary, activeBorderColor = BambooBorderStrong,
                        inactiveContainerColor = BambooObsidian, inactiveContentColor = TextSecondary, inactiveBorderColor = BambooBorder,
                    ),
                ) { Text(label, fontSize = 13.sp) }
            }
        }
        Box(Modifier.weight(1f)) {
            when (mode) {
                0 -> LiveDiagramPane(store, d, pcTitle, history = false, onOpenChange)
                1 -> LiveDiagramPane(store, d, pcTitle, history = true, onOpenChange)
                else -> projectMap()
            }
        }
    }
}

/**
 * The live diagram: account → devices → project → session → agent → model → tools → files → git → tests →
 * todos → approvals, built from real state. It is rebuilt at most once a second from the latest state
 * (sampled, so a steady stream of events can never stop it). History mode shows it at a point of the timeline.
 */
@OptIn(FlowPreview::class, ExperimentalLayoutApi::class)
@Composable
private fun LiveDiagramPane(store: BambooStore, d: SessionDetail, pcTitle: String, history: Boolean, onOpenChange: (String) -> Unit) {
    val profile by store.profile.collectAsState()
    val devices by store.devices.collectAsState()
    val me by store.myDeviceId.collectAsState()
    val web by store.webOnline.collectAsState()
    val projects by store.projects.collectAsState()
    val h = d.history.data?.history
    val approvals = remember(d.history.data, d.approvals) { mergedApprovals(d) }
    val input = LiveInput(
        account = profile.account, devices = devices, myDeviceId = me, webOnline = web,
        project = projects.firstOrNull { it.id == d.session?.projectId }, session = d.session, history = h,
        todos = d.todos.todos, approvals = approvals,
    )
    var filter by rememberSaveable { mutableStateOf(LiveFilter.All) }
    val moments = remember(h) { historyMoments(h) }
    var position by rememberSaveable { mutableFloatStateOf(1f) }
    val until: Instant? = if (history && moments.isNotEmpty()) moments[((moments.size - 1) * position).toInt().coerceIn(0, moments.size - 1)] else null
    var graph by remember(filter, until, history) { mutableStateOf(buildLiveGraph(input, filter, if (history) until ?: Instant.EPOCH else null)) }
    var updates by remember { mutableIntStateOf(0) }
    var updatedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val latest by rememberUpdatedState(input)
    LaunchedEffect(filter, until, history) {
        snapshotFlow { latest }.distinctUntilChanged().drop(1).sample(LIVE_REBUILD_MS).collect { state ->
            graph = withContext(Dispatchers.Default) { buildLiveGraph(state, filter, if (history) until ?: Instant.EPOCH else null) }
            updates++
            updatedAt = System.currentTimeMillis()
        }
    }
    var selected by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                LiveFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f, onClick = { filter = f }, label = { Text(f.label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BambooGreenSubtle, selectedLabelColor = TextPrimary, containerColor = BambooSurface, labelColor = TextSecondary),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = filter == f, borderColor = BambooBorder, selectedBorderColor = BambooBorderStrong),
                    )
                }
            }
            if (history) {
                if (moments.isEmpty()) Text(
                    if (d.history.loading) "Reading the session timeline from $pcTitle…" else "This session has no timeline yet, so there is nothing to replay.",
                    color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = Space.s),
                ) else Column(Modifier.padding(top = Space.s)) {
                    Text("As it was at ${until?.let { "${shortClock(it)} (${relative(it.toString())})" } ?: "the start"}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = position, onValueChange = { position = it }, steps = (moments.size - 2).coerceIn(0, 200),
                        colors = SliderDefaults.colors(thumbColor = BambooGreen, activeTrackColor = BambooGreen, inactiveTrackColor = BambooBorderStrong),
                    )
                    Row {
                        Text(shortClock(moments.first()), color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(shortClock(moments.last()), color = TextMuted, fontSize = 11.sp)
                    }
                }
            } else Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.s)) {
                Dot(if (d.session?.isActive == true) StatusRunning else StatusSuccess, 7, pulse = d.session?.isActive == true)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Live · ${plural(graph.nodes.size, "item")} · updated ${relativeMillis(updatedAt).ifBlank { "now" }}" + if (updates > 0) " · $updates updates" else "",
                    color = TextSecondary, fontSize = 12.sp,
                )
            }
        }
        if (graph.nodes.isEmpty()) item {
            EmptyState("Nothing to draw yet", if (filter == LiveFilter.All) "The diagram fills in as the session runs." else "Nothing in this filter yet. Try All.", Icons.Filled.AccountTree)
        }
        val tiers = graph.tiers
        tiers.forEachIndexed { i, (tier, nodes) ->
            item(key = "tier:" + tier.name) {
                if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowDownward, null, tint = DiagramArrow, modifier = Modifier.size(16.dp))
                }
                Text(tier.label.uppercase(), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 4.dp))
                val linked = selected?.let { sel -> graph.edges.filter { it.from == sel || it.to == sel }.flatMap { listOf(it.from, it.to) }.toSet() }.orEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    nodes.forEach { n ->
                        NodeBox(n, selected = n.id == selected, linked = n.id in linked) {
                            selected = if (selected == n.id) null else n.id
                            if (n.path != null) onOpenChange(n.path)
                        }
                    }
                }
            }
        }
        selected?.let { id -> graph.nodes.firstOrNull { it.id == id } }?.let { n ->
            item(key = "sel") {
                HorizontalDivider(color = BambooBorder, modifier = Modifier.padding(vertical = Space.s))
                val ins = graph.edges.filter { it.to == n.id }.mapNotNull { e -> graph.nodes.firstOrNull { it.id == e.from }?.label }
                val outs = graph.edges.filter { it.from == n.id }.mapNotNull { e -> graph.nodes.firstOrNull { it.id == e.to }?.label }
                Text(n.label, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                n.detail?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
                if (ins.isNotEmpty()) Text("From: " + ins.joinToString(", "), color = TextMuted, fontSize = 12.sp)
                if (outs.isNotEmpty()) Text("To: " + outs.joinToString(", "), color = TextMuted, fontSize = 12.sp)
                TextButton(onClick = { selected = null }) { Text("Clear selection") }
            }
        }
        item { BottomSpacer() }
    }
}

private const val LIVE_REBUILD_MS = 1000L

private fun statusColor(s: LiveStatus): Color = when (s) {
    LiveStatus.Ok -> StatusSuccess
    LiveStatus.Active -> StatusRunning
    LiveStatus.Warning -> StatusWarning
    LiveStatus.Failed -> StatusFailed
    LiveStatus.Muted -> TextMuted
}

@Composable
private fun NodeBox(n: LiveNode, selected: Boolean, linked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val c = statusColor(n.status)
    Column(
        Modifier.widthIn(max = 220.dp).heightIn(min = 48.dp).clip(shape)
            .background(if (selected) StatusRunningTint else BambooSurface)
            .border(if (selected || linked) 2.dp else 1.dp, if (selected || linked) StatusRunning else BambooBorderStrong, shape)
            .clickable(onClickLabel = if (n.path != null) "Open file change" else "Show links", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(c))
            Spacer(Modifier.width(6.dp))
            Text(brandModel(n.label) ?: n.label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        n.detail?.takeIf { it.isNotBlank() }?.let { Text(brandModel(it) ?: it, color = TextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

