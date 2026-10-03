package com.bambookit.android.presentation.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bambookit.android.R
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.ChangedFile
import com.bambookit.android.data.ContentError
import com.bambookit.android.data.Device
import com.bambookit.android.data.DiffView
import com.bambookit.android.data.NOT_CONTINUED_MESSAGE
import com.bambookit.android.data.Part
import com.bambookit.android.data.PendingCommand
import com.bambookit.android.data.Session
import com.bambookit.android.data.SessionDetail
import com.bambookit.android.data.ShareView
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.DiffAddedLine
import com.bambookit.android.presentation.theme.DiffRemovedLine
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import com.bambookit.android.presentation.theme.Transparent
import com.bambookit.android.presentation.theme.UserBubble

private val SessionModes = listOf("Chat", "Files", "Project", "Diagram")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(store: BambooStore, sessionId: String, onBack: () -> Unit) {
    LaunchedEffect(sessionId) { store.openSession(sessionId) }
    val detail by store.detail.collectAsState()
    val devices by store.devices.collectAsState()
    val diff by store.diff.collectAsState()
    val file by store.file.collectAsState()
    val share by store.share.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var rewindTarget by remember { mutableStateOf<Part?>(null) }
    var menu by remember { mutableStateOf(false) }
    val d = detail?.takeIf { it.sessionId == sessionId }
    val s = d?.session
    val pc = s?.let { ss -> devices.firstOrNull { it.id == ss.deviceId } }
    val pcTitle = pc?.name ?: "Your PC"
    val canChat = d?.canChat == true
    val close = { store.closeSession(); onBack() }
    BackHandler(onBack = close)

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = s?.title ?: "Session",
            subtitle = listOfNotNull(s?.projectName, pc?.name).joinToString(" · ").ifBlank { null },
            navigationIcon = { IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { store.shareSession() }, enabled = s != null) { Icon(Icons.Filled.Share, "Share link") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = BambooSurfaceElevated) {
                        DropdownMenuItem(
                            text = { Text("Reload from PC") }, leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                            onClick = { menu = false; store.reloadSession() },
                        )
                        DropdownMenuItem(
                            text = { Text("Undo rewind") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null) }, enabled = canChat,
                            onClick = { menu = false; store.undoRewind() },
                        )
                    }
                }
            },
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.screen).padding(bottom = Space.s)) {
            SessionModes.forEachIndexed { i, label ->
                val count = when (i) {
                    1 -> d?.fileMap?.takeIf { it.loaded }?.entries?.size
                    else -> null
                }
                SegmentedButton(
                    selected = mode == i,
                    onClick = { mode = i },
                    shape = SegmentedButtonDefaults.itemShape(i, SessionModes.size),
                    icon = {},
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = BambooGreenSubtle, activeContentColor = TextPrimary, activeBorderColor = BambooBorderStrong,
                        inactiveContainerColor = BambooObsidian, inactiveContentColor = TextSecondary, inactiveBorderColor = BambooBorder,
                    ),
                ) { Text(label + (count?.let { " ($it)" } ?: ""), fontSize = 13.sp) }
            }
        }
        when {
            mode == 1 && d != null && s != null -> Box(Modifier.weight(1f)) {
                FileMapPane(d, pcTitle, onLoad = { store.loadFileMap() }, onOpenDiff = store::openDiff, onOpenFile = store::openFile)
            }
            mode == 2 && d != null && s != null -> Box(Modifier.weight(1f)) {
                ProjectPane(
                    d, pcTitle, projectName = s.projectName ?: s.directory.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\'),
                    onLoad = { store.loadTree(it) }, onOpenFile = store::openFile,
                )
            }
            mode == 3 && d != null && s != null -> Box(Modifier.weight(1f)) {
                DiagramPane(d, pcTitle, onLoad = { store.loadDiagram() }, onLoadFileMap = { store.loadFileMap() }, onOpenFile = store::openFile)
            }
            else -> {
                if (s != null) SessionInfo(s, pc)
                if (s != null && !canChat) {
                    Banner(
                        NOT_CONTINUED_MESSAGE, Icons.Filled.Lock, color = StatusWarning, tint = StatusWarningTint, title = "View only on this phone",
                        modifier = Modifier.padding(horizontal = Space.screen).padding(bottom = Space.s),
                    )
                }
                TabRow(selectedTabIndex = tab, containerColor = BambooObsidian, contentColor = TextPrimary, divider = { HorizontalDivider(color = BambooBorder) }) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Messages") }, unselectedContentColor = TextSecondary)
                    Tab(
                        selected = tab == 1, onClick = { tab = 1 }, unselectedContentColor = TextSecondary,
                        text = { Text("Changes" + (d?.changes?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: "")) },
                    )
                    Tab(
                        selected = tab == 2, onClick = { tab = 2 }, unselectedContentColor = TextSecondary,
                        text = { Text("Approvals" + (d?.approvals?.count { it.isPending }?.takeIf { it > 0 }?.let { " ($it)" } ?: "")) },
                    )
                }
                Box(Modifier.weight(1f)) {
                    when {
                        d == null || (d.loading && s == null) -> LoadingState("Loading session…")
                        d.error != null && s == null -> ErrorState("Couldn't open this session", d.error, Icons.Filled.CloudOff, onRetry = { store.reloadSession() })
                        tab == 0 -> ChatTab(d, pcTitle, canChat, onRewind = { rewindTarget = it }, onRetry = store::retryContent, onRefresh = store::reloadSession)
                        tab == 1 -> ChangesTab(d, pcTitle, onOpen = store::openDiff, onRetry = store::retryContent, onRefresh = store::reloadSession)
                        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.screen)) {
                            val pending = d.approvals.filter { it.isPending }
                            if (pending.isEmpty()) item { EmptyState("No pending approvals", "Permission requests from this session appear here.", Icons.Filled.VerifiedUser) }
                            items(pending, key = { it.id }) {
                                ApprovalCard(it, store, pcName = pc?.name)
                                Spacer(Modifier.height(Space.s))
                            }
                        }
                    }
                }
                if (d != null && s != null) Composer(store, d, canChat)
            }
        }
    }

    rewindTarget?.let { part ->
        AlertDialog(
            onDismissRequest = { rewindTarget = null },
            containerColor = BambooSurfaceElevated,
            icon = { Icon(Icons.AutoMirrored.Filled.Undo, null, tint = StatusWarning) },
            title = { Text("Rewind to before this message?") },
            text = { Text("This message and everything after it are undone in the session. File changes are undone too when the project folder is a git repository the agent can snapshot.\n\nYou can undo the rewind until you send a new message.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { store.rewind(part.messageId); rewindTarget = null }) { Text("Rewind", color = StatusWarning) } },
            dismissButton = { TextButton(onClick = { rewindTarget = null }) { Text("Cancel") } },
        )
    }
    diff?.let { DiffDialog(it, pcTitle, onClose = store::closeDiff, onRetry = { store.openDiff(it.file) }, onOpenFile = { path -> store.closeDiff(); store.openFile(path) }) }
    file?.let { CodeViewer(it, pcTitle, onClose = store::closeFile, onRetry = { store.openFile(it.path) }) }
    share?.let { ShareDialog(it, onClose = store::closeShare, onUnshare = { store.unshareSession() }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionInfo(s: Session, pc: Device?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.screen).padding(bottom = Space.s)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(s.status)
            pc?.let { OnlineChip(it.online, it.name) }
            s.agent?.let { Chip(it, TextSecondary, icon = Icons.Filled.SmartToy) }
            brandModel(s.model)?.let { Chip(it, TextSecondary, icon = Icons.Filled.Memory) }
        }
        s.currentAction?.takeIf { it.isNotBlank() }?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.Bolt, null, tint = StatusRunning, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(it, color = StatusRunning, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        s.statusMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = if (s.status == "error") StatusFailed else StatusWarning, fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(top = 6.dp))
        }
    }
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
private fun ChatTab(d: SessionDetail, pcTitle: String, canChat: Boolean, onRewind: (Part) -> Unit, onRetry: () -> Unit, onRefresh: () -> Unit) {
    val err = d.contentError
    when {
        err != null && d.parts.isEmpty() -> ContentUnavailable(err, pcTitle, d.contentLoading, onRetry)
        !d.contentLoaded && d.contentLoading -> LoadingState("Reading the chat from $pcTitle…")
        else -> SessionRefresh(d.contentLoading, onRefresh) {
            Column(Modifier.fillMaxSize()) {
                if (err != null) StaleBanner(err, d.contentLoading, onRetry)
                Transcript(d.parts, d.session?.agent, canRewind = canChat, emptyHint = if (canChat) "Send a message below to get the agent started." else "Messages in this session will appear here.", onRewind = onRewind)
            }
        }
    }
}

@Composable
private fun Transcript(parts: List<Part>, agent: String?, canRewind: Boolean, emptyHint: String, onRewind: (Part) -> Unit) {
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
        if (visible.isEmpty()) item { EmptyState("No messages yet", emptyHint, Icons.Filled.SmartToy) }
        itemsIndexed(visible, key = { _, p -> p.id }) { i, p ->
            val prev = visible.getOrNull(i - 1)
            val newMessage = prev == null || prev.messageId != p.messageId
            val roleChanged = prev == null || prev.role != p.role
            if (newMessage && i > 0) Spacer(Modifier.height(if (roleChanged) 14.dp else 6.dp))
            if (p.role != "user" && roleChanged) AgentHeader(agent)
            PartView(p, canRewind, onRewind)
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
        Text("Agent", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        agent?.let { Text("  ·  $it", color = TextMuted, fontSize = 12.sp) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PartView(p: Part, canRewind: Boolean, onRewind: (Part) -> Unit) {
    when {
        p.type == "tool" -> ToolRow(p)
        p.type == "reasoning" -> ReasoningRow(p.text.orEmpty())
        p.role == "user" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                    .background(UserBubble)
                    .combinedClickable(onClick = {}, onLongClick = if (canRewind) ({ onRewind(p) }) else null)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(p.text.orEmpty(), color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                if (canRewind) Text("Hold to rewind", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
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

// ------------------------------------------------------------------ changes

@Composable
private fun ChangesTab(d: SessionDetail, pcTitle: String, onOpen: (String) -> Unit, onRetry: () -> Unit, onRefresh: () -> Unit) {
    val err = d.contentError
    val files = d.changes
    when {
        err != null && files.isEmpty() && !d.contentLoaded -> ContentUnavailable(err, pcTitle, d.contentLoading, onRetry)
        !d.contentLoaded && d.contentLoading -> LoadingState("Reading changed files from $pcTitle…")
        else -> SessionRefresh(d.contentLoading, onRefresh) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
                if (err != null) item { StaleBanner(err, d.contentLoading, onRetry) }
                if (files.isEmpty()) item { EmptyState("No file changes yet", "Files the agent changes in this session appear here.", Icons.Filled.Description) }
                else item {
                    SectionTitle("${plural(files.size, "file")} changed") {
                        Mono("+${files.sumOf { it.additions }}", color = StatusSuccess, size = 12)
                        Spacer(Modifier.width(6.dp))
                        Mono("−${files.sumOf { it.deletions }}", color = StatusFailed, size = 12)
                    }
                }
                items(files, key = { it.file }) { f ->
                    ChangedFileRow(f) { onOpen(f.file) }
                    Spacer(Modifier.height(Space.s))
                }
                item { BottomSpacer() }
            }
        }
    }
}

@Composable
private fun ChangedFileRow(f: ChangedFile, onClick: () -> Unit) {
    val (icon, color) = when (f.status) {
        "added" -> Icons.Filled.AddCircleOutline to StatusSuccess
        "deleted" -> Icons.Filled.RemoveCircleOutline to StatusFailed
        else -> Icons.Filled.Edit to StatusRunning
    }
    BkCard(onClick = onClick, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, f.status, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(f.file.substringAfterLast('/').substringAfterLast('\\'), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dir = f.file.substringBeforeLast('/', "").ifEmpty { f.file.substringBeforeLast('\\', "") }
                if (dir.isNotEmpty()) Mono(dir, color = TextMuted, size = 11)
            }
            Spacer(Modifier.width(8.dp))
            Mono("+${f.additions}", color = StatusSuccess, size = 12)
            Spacer(Modifier.width(6.dp))
            Mono("−${f.deletions}", color = StatusFailed, size = 12)
        }
    }
}

// ------------------------------------------------------------------ composer

@Composable
private fun CommandStatus(c: PendingCommand) {
    val label = when (c.type) {
        "SEND_MESSAGE" -> "Message"; "ABORT" -> "Stop"; "CONTINUE" -> "Continue"; "RETRY" -> "Retry"; "REFRESH" -> "Refresh"
        "REVERT" -> "Rewind"; "UNREVERT" -> "Undo rewind"; "SHARE" -> "Share"; "UNSHARE" -> "Unpublish"
        "READ_FILE" -> "Open file"; "GET_DIFF" -> "Diff"; else -> c.type
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) {
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

@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
        shape = RoundedCornerShape(50),
        colors = AssistChipDefaults.assistChipColors(labelColor = color, leadingIconContentColor = color, containerColor = BambooSurfaceElevated),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = BambooBorderStrong),
    )
}

/** Sticky bottom bar: command status, session actions and the message box. */
@Composable
private fun Composer(store: BambooStore, d: SessionDetail, canChat: Boolean) {
    val busy = d.session?.isActive == true
    var text by rememberSaveable(d.sessionId) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(BambooSurface)) {
        HorizontalDivider(color = BambooBorder)
        Column(Modifier.padding(horizontal = Space.m, vertical = Space.s)) {
            d.commands.lastOrNull()?.let { CommandStatus(it) }
            if (busy || canChat) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
                    if (busy) ActionChip("Stop", Icons.Filled.Stop, StatusFailed) { store.send("ABORT") }
                    else {
                        ActionChip("Continue", Icons.Filled.PlayArrow, TextPrimary) { store.send("CONTINUE") }
                        ActionChip("Retry", Icons.Filled.Replay, TextPrimary) { store.send("RETRY") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, enabled = canChat,
                    placeholder = { Text(if (canChat) "Message the agent on your PC" else "Continue on your PC to chat from here", fontSize = 14.sp) },
                    shape = RoundedCornerShape(24.dp), maxLines = 5, modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BambooBorderStrong, unfocusedBorderColor = BambooBorder, disabledBorderColor = BambooBorder,
                        focusedContainerColor = BambooObsidian, unfocusedContainerColor = BambooObsidian, disabledContainerColor = BambooObsidian,
                        disabledPlaceholderColor = TextMuted, unfocusedPlaceholderColor = TextMuted, focusedPlaceholderColor = TextMuted,
                    ),
                )
                Spacer(Modifier.width(Space.s))
                FilledIconButton(
                    onClick = { store.send("SEND_MESSAGE", text.trim()); text = "" },
                    enabled = canChat && text.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(disabledContainerColor = BambooSurfaceElevated, disabledContentColor = TextMuted),
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    }
}

// ------------------------------------------------------------------ diff / file / share

@Composable
private fun FullScreen(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(BambooObsidian).imePadding()) { content() }
    }
}

private fun fileName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')

@Composable
private fun DiffDialog(view: DiffView, pcTitle: String, onClose: () -> Unit, onRetry: () -> Unit, onOpenFile: (String) -> Unit) {
    FullScreen(onClose) {
        ScreenTopBar(
            title = fileName(view.file),
            subtitle = view.result?.let { "+${it.additions} −${it.deletions}${if (it.truncated) " · truncated" else ""}  ·  ${view.file}" } ?: view.file,
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") } },
            actions = { if (view.result != null && view.result.status != "deleted") TextButton(onClick = { onOpenFile(view.file) }) { Text("View file") } },
        )
        HorizontalDivider(color = BambooBorder)
        when {
            view.loading -> LoadingState("Fetching the changes from $pcTitle…")
            view.error != null -> ErrorState("Couldn't load the diff", view.error, Icons.Filled.ErrorOutline, onRetry = onRetry)
            view.result != null && view.result.patch.isBlank() -> EmptyState("No textual diff", "Binary or empty change.", Icons.Filled.Description)
            view.result != null -> {
                val lines = remember(view.result.patch) { view.result.patch.lines() }
                Column(Modifier.fillMaxSize().background(CodeBlockBackground).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    lines.forEach { line ->
                        val (fg, bg) = when {
                            line.startsWith("+++") || line.startsWith("---") -> TextMuted to Transparent
                            line.startsWith("+") -> StatusSuccess to DiffAddedLine
                            line.startsWith("-") -> StatusFailed to DiffRemovedLine
                            line.startsWith("@@") -> StatusRunning to BambooSurfaceElevated
                            else -> TextSecondary to Transparent
                        }
                        Text(
                            line.ifEmpty { " " }, color = fg, fontFamily = FontFamily.Monospace, fontSize = 11.sp, softWrap = false,
                            modifier = Modifier.background(bg).padding(horizontal = 10.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareDialog(view: ShareView, onClose: () -> Unit, onUnshare: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = BambooSurfaceElevated,
        icon = { Icon(Icons.Filled.Share, null) },
        title = { Text("Share session") },
        text = {
            when {
                view.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Publishing through BambooKit…", color = TextSecondary)
                }
                view.error != null -> Text(view.error, color = StatusFailed)
                view.url != null -> Column {
                    Text("Anyone with this link can read the conversation and the list of changed files.", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    SelectionContainer {
                        Text(
                            view.url, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CodeBlockBackground).padding(10.dp),
                        )
                    }
                }
                else -> Text("The PC did not return a link.", color = TextSecondary)
            }
        },
        confirmButton = {
            if (view.url != null) Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(view.url)) }) { Text("Copy") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(view.url))) }) { Text("Open") }
            } else TextButton(onClick = onClose) { Text("Close") }
        },
        dismissButton = { if (view.url != null) TextButton(onClick = onUnshare) { Text("Unpublish", color = StatusFailed) } },
    )
}
