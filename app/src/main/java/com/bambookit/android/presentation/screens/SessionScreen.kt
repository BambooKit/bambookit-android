package com.bambookit.android.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.R
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.ContentError
import com.bambookit.android.data.Part
import com.bambookit.android.data.SessionDetail
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import com.bambookit.android.presentation.theme.UserBubble

/** Segments of the session screen, in display order. */
internal enum class SessionTabId(val label: String) {
    Summary("Summary"), Prompts("Prompts"), Timeline("Timeline"), Changes("Changes"),
    Files("Files"), Project("Project"), Diagram("Diagram"), Chat("Chat"),
}

/**
 * One session, view only. Session content lives on the user's PC; the API relays it live and keeps a
 * 7-day copy of the history. The phone can Stop a running agent and answer approvals — nothing else.
 */
@Composable
fun SessionScreen(store: BambooStore, sessionId: String, onBack: () -> Unit) {
    LaunchedEffect(sessionId) { store.openSession(sessionId) }
    val detail by store.detail.collectAsState()
    val devices by store.devices.collectAsState()
    val file by store.file.collectAsState()
    val versions by store.versions.collectAsState()
    var tab by rememberSaveable { mutableStateOf(SessionTabId.Summary) }
    var focusPrompt by remember { mutableStateOf<String?>(null) }
    var openChange by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    val d = detail?.takeIf { it.sessionId == sessionId }
    val s = d?.session
    val pc = s?.let { ss -> devices.firstOrNull { it.id == ss.deviceId } }
    val pcTitle = pc?.name ?: "Your PC"
    val h = d?.history?.data?.history
    val approvals = remember(d?.history?.data, d?.approvals) { d?.let { mergedApprovals(it) }.orEmpty() }
    val close = { store.closeSession(); onBack() }
    BackHandler(onBack = close)

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = s?.title ?: h?.title ?: "Session",
            subtitle = listOfNotNull(s?.projectName ?: h?.projectName, pc?.name).joinToString(" · ").ifBlank { null },
            navigationIcon = { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = { store.reloadSession() }, enabled = d != null) { Icon(Icons.Filled.Refresh, "Reload") } },
        )
        ScrollableTabRow(
            selectedTabIndex = tab.ordinal, containerColor = BambooObsidian, contentColor = TextPrimary, edgePadding = Space.s,
            divider = { HorizontalDivider(color = BambooBorder) },
        ) {
            SessionTabId.entries.forEach { t ->
                val count = when (t) {
                    SessionTabId.Prompts -> h?.prompts?.size
                    SessionTabId.Changes -> h?.changes?.size
                    SessionTabId.Timeline -> approvals.count { it.isPending }
                    else -> null
                }?.takeIf { it > 0 }
                Tab(
                    selected = tab == t, onClick = { tab = t }, unselectedContentColor = TextSecondary,
                    text = { Text(t.label + (count?.let { " ($it)" } ?: ""), maxLines = 1) },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                d == null -> LoadingState("Loading session…")
                s == null && d.history.data == null && d.error != null && !d.loading && !d.history.loading ->
                    ErrorState("Couldn't open this session", d.error, Icons.Filled.CloudOff, onRetry = { store.reloadSession() })
                else -> when (tab) {
                    SessionTabId.Summary -> SummaryPane(
                        d, pc, pcTitle,
                        approvalCard = { ApprovalCard(it, store, pcName = pc?.name) },
                        onStop = { confirmStop = true }, onRetry = store::reloadSession, onOpen = { tab = it },
                    )
                    SessionTabId.Prompts -> HistoryGate(d, pcTitle, store::reloadSession) { data ->
                        PromptsPane(data, onJump = { id -> focusPrompt = id; tab = SessionTabId.Timeline })
                    }
                    SessionTabId.Timeline -> HistoryGate(d, pcTitle, store::reloadSession) { data ->
                        TimelinePane(
                            data, approvals, focusPrompt, onFocusDone = { focusPrompt = null }, onOpenFile = store::openFile,
                            approvalActions = { a -> ApprovalButtons { reply -> store.respond(a, reply) } },
                        )
                    }
                    SessionTabId.Changes -> HistoryGate(d, pcTitle, store::reloadSession) { data ->
                        ChangesPane(data, onOpen = { openChange = it.file })
                    }
                    SessionTabId.Files -> HistoryGate(d, pcTitle, store::reloadSession) { data ->
                        val entries = remember(data) { touchedFiles(data.history) }
                        FilesTreePane(entries) { e ->
                            val change = data.history.changes.firstOrNull { normPath(it.file) == normPath(e.path) }
                            // A deleted file is no longer on the PC: show what the session did to it instead.
                            if (change != null && change.status == "deleted") openChange = change.file else store.openFile(e.path)
                        }
                    }
                    SessionTabId.Project -> if (s == null) LoadingState("Loading session…") else ProjectPane(
                        d, pcTitle, projectName = s.projectName ?: s.directory.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\'),
                        onLoad = { store.loadTree(it) }, onOpenFile = store::openFile,
                    )
                    SessionTabId.Diagram -> if (s == null) LoadingState("Loading session…")
                    else DiagramPane(d, pcTitle, onLoad = { store.loadDiagram() }, onLoadFileMap = { store.loadFileMap() }, onOpenFile = store::openFile)
                    SessionTabId.Chat -> ChatTab(d, pcTitle, onRetry = store::retryContent, onRefresh = store::reloadSession)
                }
            }
        }
        if (tab != SessionTabId.Summary && s != null && s.isActive && d != null) {
            HorizontalDivider(color = BambooBorder)
            LiveActivity(s, d.commands.lastOrNull(), onStop = { confirmStop = true }, compact = true)
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = BambooSurfaceElevated,
            icon = { Icon(Icons.Filled.Stop, null, tint = StatusFailed) },
            title = { Text("Stop the agent?") },
            text = { Text("BambooKit on $pcTitle stops what it is doing now. The session and the changes made so far stay on the PC.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { confirmStop = false; store.stop() }) { Text("Stop", color = StatusFailed) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Cancel") } },
        )
    }
    openChange?.let { path ->
        h?.changes?.firstOrNull { it.file == path }?.let { change ->
            FileChangeScreen(
                change, versions, pcTitle,
                onClose = { openChange = null; store.clearVersions() },
                onLoadVersions = { store.loadVersions(change.file) },
            )
        }
    }
    file?.let { CodeViewer(it, pcTitle, onClose = store::closeFile, onRetry = { store.openFile(it.path) }) }
}

// ------------------------------------------------------------------ content from the PC

@Composable
internal fun ContentUnavailable(err: ContentError, pcTitle: String, retrying: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ErrorState(
            title = when (err.code) {
                "DESKTOP_OFFLINE" -> "$pcTitle is offline"
                "DESKTOP_TIMEOUT" -> "$pcTitle didn't answer"
                "NETWORK" -> "Can't reach BambooKit"
                else -> "Couldn't read this session"
            },
            message = err.message,
            icon = if (err.desktopUnavailable || err.code == "NETWORK") Icons.Filled.CloudOff else Icons.Filled.ErrorOutline,
            onRetry = onRetry,
            retrying = retrying,
            color = if (err.desktopUnavailable) StatusWarning else StatusFailed,
        )
    }
}

/** Shown above content that was read earlier when a later read from the PC failed. */
@Composable
internal fun StaleBanner(err: ContentError, retrying: Boolean, onRetry: () -> Unit) {
    Banner(
        err.message, Icons.Filled.CloudOff, color = StatusWarning, tint = StatusWarningTint, title = "Showing what was last read from your PC",
        actionLabel = "Retry", busy = retrying, onAction = onRetry,
        modifier = Modifier.padding(horizontal = Space.m).padding(top = Space.s),
    )
}

/** Pull-to-refresh that only spins for a refresh the user started (live refetches stay quiet). */
@Composable
internal fun SessionRefresh(loading: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (!loading) pulled = false }
    RefreshBox(refreshing = pulled && loading, onRefresh = { pulled = true; onRefresh() }) { content() }
}

@Composable
private fun ChatTab(d: SessionDetail, pcTitle: String, onRetry: () -> Unit, onRefresh: () -> Unit) {
    val err = d.contentError
    when {
        err != null && d.parts.isEmpty() -> ContentUnavailable(err, pcTitle, d.contentLoading, onRetry)
        !d.contentLoaded && d.contentLoading -> LoadingState("Reading the chat from $pcTitle…")
        else -> SessionRefresh(d.contentLoading, onRefresh) {
            Column(Modifier.fillMaxSize()) {
                if (err != null) StaleBanner(err, d.contentLoading, onRetry)
                Transcript(d.parts, d.session?.agent)
            }
        }
    }
}

@Composable
private fun Transcript(parts: List<Part>, agent: String?) {
    val visible = remember(parts) { parts.filter { it.type == "tool" || !it.text.isNullOrBlank() } }
    val listState = rememberLazyListState()
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }
    }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(visible.size, visible.lastOrNull()?.text?.length) {
        if (visible.isEmpty()) return@LaunchedEffect
        if (first) listState.scrollToItem(visible.size + 1) else if (nearBottom) listState.animateScrollToItem(visible.size + 1)
        first = false
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.m)) {
        item { Spacer(Modifier.height(Space.s)) }
        if (visible.isEmpty()) item { EmptyState("No messages yet", "Messages in this session will appear here.", Icons.Filled.SmartToy) }
        itemsIndexed(visible, key = { _, p -> p.id }) { i, p ->
            val prev = visible.getOrNull(i - 1)
            val newMessage = prev == null || prev.messageId != p.messageId
            val roleChanged = prev == null || prev.role != p.role
            if (newMessage && i > 0) Spacer(Modifier.height(if (roleChanged) 14.dp else 6.dp))
            if (p.role != "user" && roleChanged) AgentHeader(agent)
            PartView(p)
            Spacer(Modifier.height(5.dp))
        }
        item { Spacer(Modifier.height(Space.m)) }
    }
}

@Composable
private fun AgentHeader(agent: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)) {
        Image(painterResource(R.drawable.bambookit_mark), null, modifier = Modifier.size(18.dp).clip(RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(8.dp))
        Text(agentLabel(agent), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PartView(p: Part) {
    when {
        p.type == "tool" -> ToolRow(p)
        p.type == "reasoning" -> ReasoningRow(p.text.orEmpty())
        p.role == "user" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                    .background(UserBubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                SelectionContainer { Text(p.text.orEmpty(), color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp) }
            }
        }
        else -> Box(Modifier.fillMaxWidth().padding(start = 2.dp, end = 4.dp)) { SelectionContainer { MarkdownText(p.text.orEmpty()) } }
    }
}

@Composable
private fun ToolRow(p: Part) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(BambooSurface).border(1.dp, BambooBorder, shape).padding(horizontal = 10.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (p.toolStatus) {
                "running", "pending" -> CircularProgressIndicator(Modifier.size(13.dp), color = StatusRunning, strokeWidth = 1.5.dp)
                "completed" -> Icon(Icons.Filled.CheckCircle, "Done", tint = StatusSuccess, modifier = Modifier.size(14.dp))
                "error" -> Icon(Icons.Filled.ErrorOutline, "Failed", tint = StatusFailed, modifier = Modifier.size(14.dp))
                else -> Icon(Icons.Filled.Build, null, tint = TextMuted, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(p.tool ?: "tool", color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            p.toolTitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.width(8.dp))
                Mono(it, color = TextSecondary, size = 12, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        if (p.toolStatus == "error" && !p.text.isNullOrBlank()) Mono(p.text, color = StatusFailed, size = 11, maxLines = 4, modifier = Modifier.padding(top = 4.dp, start = 22.dp))
    }
}

@Composable
private fun ReasoningRow(text: String) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Psychology, null, tint = TextMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Thinking", color = TextMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Collapse" else "Expand", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
        Text(
            text, color = TextMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic, lineHeight = 17.sp,
            maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp, top = 2.dp),
        )
    }
}
