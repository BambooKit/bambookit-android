package com.bambookit.android.presentation.screens

import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.itemsIndexed
import com.bambookit.android.ads.AdPlacements
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.Approval
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.Device
import com.bambookit.android.data.LinkState
import com.bambookit.android.data.NotificationItem
import com.bambookit.android.data.Project
import com.bambookit.android.data.Session
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusFailedTint
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusRunningTint
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary

// ================================================================== connection banner

/**
 * Shown under the top bar on every tab: the phone's link to BambooKit, and whether the paired
 * PCs are reachable (chats live on the PC, so an offline PC means no chat content).
 */
@Composable
fun ConnectionBanner(store: BambooStore) {
    val link by store.link.collectAsState()
    val overview by store.overview.collectAsState()
    val loaded by store.loaded.collectAsState()
    val desktops = overview?.desktops.orEmpty()
    val offline = desktops.filterNot { it.online }
    Column(Modifier.padding(horizontal = Space.screen)) {
        when {
            // Before the first successful load the stream has simply not been opened yet.
            link == LinkState.Connecting || (link == LinkState.Disconnected && !loaded) -> Banner(
                "Connecting to BambooKit…", Icons.Filled.SyncProblem, color = StatusRunning, tint = StatusRunningTint, busy = true,
                modifier = Modifier.padding(bottom = Space.s),
            )
            link == LinkState.Disconnected -> Banner(
                "Live updates paused — reconnecting automatically.", Icons.Filled.CloudOff, color = StatusFailed, tint = StatusFailedTint,
                title = "Not connected to BambooKit",
                actionLabel = "Refresh", onAction = { store.refreshAll() }, modifier = Modifier.padding(bottom = Space.s),
                diagnosis = com.bambookit.android.data.Diagnosis("The live connection to BambooKit dropped; it reconnects automatically.", "NETWORK", method = "GET", path = "/v1/realtime/stream"),
            )
            desktops.isNotEmpty() && offline.size == desktops.size -> Banner(
                "Chats are stored on your PC. Open BambooKit Desktop there to see and control sessions.",
                Icons.Filled.CloudOff, color = StatusFailed, tint = StatusFailedTint,
                title = if (offline.size == 1) "${offline[0].name} is offline" else "Your PCs are offline",
                modifier = Modifier.padding(bottom = Space.s),
                diagnosis = com.bambookit.android.data.Diagnosis(
                    if (offline.size == 1) "${offline[0].name} is offline" else "Your PCs are offline", "DESKTOP_OFFLINE",
                ),
            )
        }
    }
}

// ================================================================== home

@Composable
fun HomeScreen(store: BambooStore, onOpenSession: (String) -> Unit, onPair: () -> Unit, onApprovals: () -> Unit) {
    val overview by store.overview.collectAsState()
    val error by store.error.collectAsState()
    val refreshing by store.refreshing.collectAsState()
    val notifications by store.notifications.collectAsState()
    val clear by store.clearActivity.collectAsState()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val inlineEvery = inlineAdInterval(AdPlacements.Screen.Home)

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = BambooSurfaceElevated,
            title = { Text("Clear recent activity?") },
            text = { Text("Recent activity and notifications are removed for your account on all your devices. Sessions and approvals are not affected.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { confirmClear = false; store.clearRecentActivity() }) { Text("Clear", color = StatusFailed) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    RefreshBox(refreshing = refreshing, onRefresh = { store.refreshAll() }) {
        val o = overview
        if (o == null) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    if (error != null) ErrorState("Couldn't load your overview", error ?: "", Icons.Filled.CloudOff, onRetry = { store.refreshAll() }, retrying = refreshing, diagnosis = store.errorDiagnosis.collectAsState().value)
                    else LoadingState("Loading your workspace…")
                }
            }
            return@RefreshBox
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth().padding(top = Space.xs)) {
                    StatTile("Approvals waiting", o.pendingApprovals, Icons.Filled.VerifiedUser, Modifier.weight(1f), highlight = o.pendingApprovals > 0, onClick = onApprovals)
                    StatTile("Agents working", o.activeSessions.size, Icons.Filled.Bolt, Modifier.weight(1f))
                    StatTile("Files changed (24h)", o.recentChangedFiles, Icons.Filled.Description, Modifier.weight(1f))
                }
            }

            item { SectionTitle("Your PCs") }
            if (o.desktops.isEmpty()) item {
                BkCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.QrCodeScanner)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text("No PC connected yet", color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("In BambooKit Desktop choose Add mobile device, then scan the QR code.", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(Space.m))
                    Button(onClick = onPair, modifier = Modifier.fillMaxWidth()) { Text("Scan QR code") }
                }
            }
            items(o.desktops, key = { "pc:" + it.id }) { d ->
                PcCard(d, onOpenSession = onOpenSession)
                Spacer(Modifier.height(Space.s))
            }

            item { SectionTitle("Active sessions") }
            if (o.activeSessions.isEmpty()) item {
                BkCard { Text("No agent is working right now.", color = TextSecondary, fontSize = 13.sp) }
            }
            itemsIndexed(o.activeSessions, key = { _, it -> "s:" + it.id }) { i, s ->
                SessionRow(store, s, onOpenSession)
                Spacer(Modifier.height(Space.s))
                // Free plan: an inline banner after every 8th session (at least a screen height apart).
                if (AdPlacements.inlineAfter(i, o.activeSessions.size, inlineEvery)) InlineAd(AdPlacements.Screen.Home)
            }

            item {
                SectionTitle("Recent activity") {
                    val unread = notifications.count { it.readAt == null }
                    if (unread > 0) TextButton(onClick = { store.markAllRead() }, enabled = !clear.clearing) { Text("Mark $unread read", fontSize = 12.sp) }
                    if (notifications.isNotEmpty() || clear.clearing) {
                        TextButton(onClick = { confirmClear = true }, enabled = !clear.clearing) {
                            if (clear.clearing) {
                                CircularProgressIndicator(Modifier.size(12.dp), color = TextSecondary, strokeWidth = 1.5.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Clearing…", fontSize = 12.sp)
                            } else Text("Clear", fontSize = 12.sp)
                        }
                    }
                }
            }
            clear.error?.let { message ->
                item {
                    Banner(
                        message, Icons.Filled.SyncProblem, color = StatusFailed, tint = StatusFailedTint,
                        title = "Recent activity not cleared",
                        actionLabel = "Retry", onAction = { store.clearRecentActivity() },
                        diagnosis = clear.errorDiagnosis ?: com.bambookit.android.data.Diagnosis(message),
                        modifier = Modifier.padding(bottom = Space.s),
                    )
                }
            }
            if (notifications.isEmpty()) item {
                BkCard { Text("Approvals, finished and failed sessions will show up here.", color = TextSecondary, fontSize = 13.sp) }
            }
            items(notifications.take(8), key = { "n:" + it.id }) { n ->
                NotificationRow(n, onClick = n.data["sessionId"]?.let { id -> { onOpenSession(id) } })
                Spacer(Modifier.height(Space.s))
            }
            item { BottomSpacer() }
        }
    }
}

@Composable
private fun PcCard(d: Device, onOpenSession: (String) -> Unit) {
    BkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.DesktopWindows, tint = if (d.online) TextPrimary else TextMuted)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(d.name, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (d.online) "BambooKit Desktop${d.appVersion?.let { " $it" } ?: ""}" else "Last seen ${relative(d.lastSeenAt).ifBlank { "a while ago" }}",
                    color = TextSecondary, fontSize = 12.sp,
                )
            }
            OnlineChip(d.online)
        }
        d.activeSession?.let { a ->
            Spacer(Modifier.height(Space.m))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BambooSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    a.projectName?.let { Mono(it, size = 11) }
                }
                Spacer(Modifier.width(Space.s))
                StatusChip(a.status)
                Spacer(Modifier.width(Space.xs))
                TextButton(onClick = { onOpenSession(a.id) }) { Text("Open", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem, onClick: (() -> Unit)?) {
    BkCard(onClick = onClick, padding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Notifications, null, tint = if (n.readAt == null) StatusRunning else TextMuted, modifier = Modifier.size(18.dp).padding(top = 1.dp))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(com.bambookit.android.data.brandText(n.title), color = TextPrimary, fontSize = 13.sp, fontWeight = if (n.readAt == null) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(relative(n.createdAt), color = TextMuted, fontSize = 11.sp)
                }
                if (n.body.isNotBlank()) Text(com.bambookit.android.data.brandText(n.body), color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ================================================================== session card

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionCard(
    s: Session,
    onClick: () -> Unit,
    onToggleStar: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    renamingTo: String? = null,
) {
    BkCard(onClick = onClick, onLongClick = onLongClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.title, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Space.s))
            StatusChip(s.status)
            if (onToggleStar != null) StarButton(s.starred, onToggle = onToggleStar)
        }
        renamingTo?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                CircularProgressIndicator(Modifier.size(11.dp), color = StatusRunning, strokeWidth = 1.5.dp)
                Spacer(Modifier.width(6.dp))
                Text("Renaming on your PC to \"$it\"…", color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val meta = listOfNotNull(s.projectName, s.agent?.let { agentLabel(it) }, brandModel(s.model)).joinToString(" · ").ifBlank { s.directory }
        Mono(meta, size = 11, modifier = Modifier.padding(top = 3.dp))
        s.currentAction?.takeIf { it.isNotBlank() }?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.Filled.Bolt, null, tint = StatusRunning, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(it, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val hasChips = s.changes.files > 0 || s.pendingApprovals > 0 || s.updatedAt != null
        if (hasChips) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (s.pendingApprovals > 0) Chip(plural(s.pendingApprovals, "approval"), StatusWarning, StatusWarningTint, icon = Icons.Filled.Shield)
                if (s.changes.files > 0) Chip("${plural(s.changes.files, "file")}  +${s.changes.additions} −${s.changes.deletions}", TextSecondary, icon = Icons.Filled.Description)
                s.updatedAt?.let { relative(it).takeIf { r -> r.isNotBlank() }?.let { r -> Chip(r, TextMuted) } }
            }
        }
    }
}

// ================================================================== projects

@Composable
fun ProjectsScreen(store: BambooStore, onOpenSession: (String) -> Unit, focusProjectId: String? = null, onFocused: () -> Unit = {}) {
    val projects by store.projects.collectAsState()
    val sessions by store.sessions.collectAsState()
    val devices by store.devices.collectAsState()
    val loaded by store.loaded.collectAsState()
    val error by store.error.collectAsState()
    val refreshing by store.refreshing.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var newIn by rememberSaveable { mutableStateOf<String?>(null) }
    val planView by store.plan.collectAsState()
    // Free plan with today's new sessions used up: the buttons show a lock and open the limit dialog.
    val sessionsLocked = planView.plan?.sessionsLocked() == true
    val projectIds = projects.map { it.id }.toSet()
    val orphans = sessions.filter { it.projectId == null || it.projectId !in projectIds }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val inlineEvery = inlineAdInterval(AdPlacements.Screen.Projects)
    // Opened from Profile → Projects managed: scroll to that project and show all of its sessions.
    LaunchedEffect(focusProjectId, projects.size) {
        val id = focusProjectId ?: return@LaunchedEffect
        if (projects.none { it.id == id }) return@LaunchedEffect
        expanded = expanded + id
        // Items before it: the intro banner, then per project its header, sessions and "Show all".
        var index = 1
        for (p in projects) {
            if (p.id == id) break
            val n = sessions.count { it.projectId == p.id }
            index += 1 + (if (p.id in expanded) n else minOf(n, 3)) + (if (n > 3) 1 else 0)
        }
        runCatching { listState.animateScrollToItem(index) }
        onFocused()
    }

    RefreshBox(refreshing = refreshing, onRefresh = { store.refreshAll() }) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = Space.screen)) {
            item {
                Banner(
                    "Sessions run in BambooKit Desktop on your PC. Start one with New session, open one to follow it live and answer the agent's requests, or tap Continue on PC to chat in an existing one.",
                    Icons.Filled.DesktopWindows, color = TextSecondary, tint = BambooSurfaceElevated,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
            when {
                !loaded && projects.isEmpty() && error != null -> item { ErrorState("Couldn't load projects", error ?: "", Icons.Filled.CloudOff, onRetry = { store.refreshAll() }, retrying = refreshing, diagnosis = store.errorDiagnosis.collectAsState().value) }
                !loaded && projects.isEmpty() -> item { LoadingState("Loading projects…") }
                projects.isEmpty() && orphans.isEmpty() -> item {
                    EmptyState("No projects yet", "Open a project in BambooKit Desktop on your PC and it shows up here.", Icons.Filled.Folder)
                }
            }
            // Session rows shown, counted across projects, for the inline banners (inside a row's item, so the
            // item indexes used to scroll to a project stay the same).
            val shownRows = projects.sumOf { p -> sessions.count { it.projectId == p.id }.let { n -> if (p.id in expanded) n else minOf(n, 3) } } + orphans.size
            var row = 0
            projects.forEach { p ->
                val list = sessions.filter { it.projectId == p.id }
                val open = p.id in expanded
                val first = row
                row += if (open) list.size else minOf(list.size, 3)
                item(key = "p:" + p.id) {
                    Spacer(Modifier.height(Space.m))
                    ProjectHeader(p, devices.firstOrNull { it.id == p.deviceId }, locked = sessionsLocked, onNewSession = { if (sessionsLocked) store.showLocalPlanLimit(sessions = true) else newIn = p.id })
                    Spacer(Modifier.height(Space.s))
                    if (list.isEmpty()) Text("No sessions in this project yet.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                }
                itemsIndexed(if (open) list else list.take(3), key = { _, it -> "ps:" + it.id }) { i, s ->
                    SessionRow(store, s, onOpenSession)
                    Spacer(Modifier.height(Space.s))
                    if (AdPlacements.inlineAfter(first + i, shownRows, inlineEvery)) InlineAd(AdPlacements.Screen.Projects)
                }
                if (list.size > 3) item(key = "pm:" + p.id) {
                    TextButton(onClick = { expanded = if (open) expanded - p.id else expanded + p.id }) {
                        Text(if (open) "Show fewer" else "Show all ${list.size} sessions", fontSize = 12.sp)
                    }
                }
            }
            if (orphans.isNotEmpty()) {
                item { SectionTitle("Other sessions") }
                val first = row
                itemsIndexed(orphans, key = { _, it -> "o:" + it.id }) { i, s ->
                    SessionRow(store, s, onOpenSession)
                    Spacer(Modifier.height(Space.s))
                    if (AdPlacements.inlineAfter(first + i, shownRows, inlineEvery)) InlineAd(AdPlacements.Screen.Projects)
                }
            }
            item { BottomSpacer() }
        }
    }
    newIn?.let { id ->
        projects.firstOrNull { it.id == id }?.let { p ->
            NewSessionSheet(store, p, onOpenSession = { sid -> newIn = null; onOpenSession(sid) }, onDismiss = { newIn = null })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectHeader(p: Project, pc: Device?, locked: Boolean = false, onNewSession: () -> Unit) {
    BkCard(container = BambooSurfaceElevated) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Folder, size = 34.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Mono(p.directory, size = 11)
            }
            Spacer(Modifier.width(Space.s))
            Button(onClick = onNewSession, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(if (locked) Icons.Filled.Lock else Icons.Filled.Add, if (locked) "Daily free limit reached" else null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New session", fontSize = 13.sp)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
            if (pc != null) OnlineChip(pc.online, pc.name) else p.deviceName?.let { Chip(it, TextSecondary, icon = Icons.Filled.Computer) }
            p.branch?.let { Chip(it, TextSecondary, icon = Icons.AutoMirrored.Filled.CallSplit) }
            if (p.activeSessions > 0) Chip("${p.activeSessions} working", StatusRunning, StatusRunningTint, dot = true, pulse = true)
            Chip(plural(p.totalSessions, "session"), TextMuted)
        }
    }
}

// ================================================================== approvals

@Composable
fun ApprovalsScreen(store: BambooStore, onOpenSession: (String) -> Unit) {
    val all by store.approvals.collectAsState()
    val approvals = all.filter { it.isPending }
    val devices by store.devices.collectAsState()
    val loaded by store.loaded.collectAsState()
    val error by store.error.collectAsState()
    val refreshing by store.refreshing.collectAsState()
    RefreshBox(refreshing = refreshing, onRefresh = { store.refreshAll() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
            if (!loaded && approvals.isEmpty() && error != null) item {
                ErrorState("Couldn't load requests", error ?: "", Icons.Filled.CloudOff, onRetry = { store.refreshAll() }, retrying = refreshing, diagnosis = store.errorDiagnosis.collectAsState().value)
            } else if (!loaded && approvals.isEmpty()) item { LoadingState("Loading requests…") }
            else if (approvals.isEmpty()) item {
                EmptyState(
                    "Nothing waiting for you",
                    "Requests appear here when the agent asks before running commands or editing files, or asks you a question. " +
                        "You'll also get a notification.",
                    Icons.Filled.VerifiedUser,
                )
            }
            items(approvals, key = { it.id }) { a ->
                Spacer(Modifier.height(Space.s))
                ApprovalCard(a, store, pcName = devices.firstOrNull { it.id == a.deviceId }?.name, onOpen = { onOpenSession(a.sessionId) })
            }
            item { BottomSpacer() }
        }
    }
}
