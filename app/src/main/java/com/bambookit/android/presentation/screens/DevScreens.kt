package com.bambookit.android.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.Device
import com.bambookit.android.data.powerLabel
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.DangerTint
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusFailedTint
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import kotlinx.coroutines.launch

/** The developer sub-screens, opened from Profile → Developer options. */
enum class DevScreen { Power, Terminal, Research }

@Composable
fun DeveloperOptionsSection(store: BambooStore, onPower: () -> Unit, onTerminal: () -> Unit, onResearch: () -> Unit) {
    val on by store.developerOptions.collectAsState()
    BkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Code)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("Developer options", color = TextPrimary, fontWeight = FontWeight.Medium)
                Text("Power, Terminal and Research tools. Off by default.", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Switch(
                checked = on, onCheckedChange = { store.setDeveloperOptions(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = BambooGreen, checkedThumbColor = BambooObsidian),
            )
        }
        if (on) {
            Spacer(Modifier.height(Space.s))
            HorizontalDivider(color = BambooBorder)
            DevRow("Power", "Sleep, shut down or lock an online PC", Icons.Filled.PowerSettingsNew, onPower)
            DevRow("Terminal", "Open a terminal on an online PC", Icons.Filled.Terminal, onTerminal)
            DevRow("Research", "Wikipedia & DuckDuckGo search, optional Ask AI", Icons.Filled.Science, onResearch)
            Text(
                "Power and Terminal also need \"Allow this PC to be controlled remotely\" turned on at the PC itself.",
                color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = Space.s),
            )
        }
    }
}

@Composable
private fun DevRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

// ================================================================== Power

@Composable
fun PowerScreen(store: BambooStore, onBack: () -> Unit) {
    val devices by store.devices.collectAsState()
    val power by store.power.collectAsState()
    val desktops = devices.filter { it.kind == "desktop" }
    var confirm by remember { mutableStateOf<Pair<Device, String>?>(null) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Power", "Sleep, shut down or lock your PC", navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
            item {
                Banner(
                    "These go only to your own paired PC over the signed device relay. The PC runs the action after a 10-second cancelable countdown, and only if \"Allow remote control\" is on there.",
                    Icons.Filled.PowerSettingsNew, color = TextSecondary, tint = BambooSurfaceElevated, modifier = Modifier.padding(top = Space.xs),
                )
            }
            if (desktops.isEmpty()) item {
                EmptyState("No PCs", "Pair a PC first to control it from here.", Icons.Filled.DesktopWindows)
            }
            items(desktops, key = { it.id }) { d ->
                Spacer(Modifier.height(Space.s))
                PowerCard(d, power[d.id], onAction = { confirm = d to it }, onDismissError = { store.clearPower(d.id) })
            }
            item { BottomSpacer() }
        }
    }
    confirm?.let { (d, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = BambooSurfaceElevated,
            icon = { Icon(Icons.Filled.PowerSettingsNew, null, tint = if (action == "shutdown") StatusFailed else StatusWarning) },
            title = { Text("${powerLabel(action)} ${d.name}?") },
            text = {
                Text(
                    when (action) {
                        "shutdown" -> "${d.name} will shut down after a 10-second countdown you can cancel at the PC. Unsaved work there may be lost."
                        "lock" -> "${d.name}'s screen will lock after a 10-second countdown you can cancel at the PC."
                        else -> "${d.name} will go to sleep after a 10-second countdown you can cancel at the PC."
                    },
                    color = TextSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = { store.power(d, action); confirm = null },
                    colors = if (action == "shutdown") ButtonDefaults.buttonColors(containerColor = StatusFailed, contentColor = BambooObsidian) else ButtonDefaults.buttonColors(),
                ) { Text(powerLabel(action)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PowerCard(d: Device, result: com.bambookit.android.data.PowerResult?, onAction: (String) -> Unit, onDismissError: () -> Unit) {
    BkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.DesktopWindows, tint = if (d.online) TextPrimary else TextMuted)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(d.name, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (d.online) "Online" else "Offline — last seen ${relative(d.lastSeenAt).ifBlank { "a while ago" }}", color = TextSecondary, fontSize = 12.sp)
            }
            OnlineChip(d.online)
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            PowerButton("Sleep", Icons.Filled.PowerSettingsNew, enabled = d.online && result?.busy != true, Modifier.weight(1f)) { onAction("sleep") }
            PowerButton("Lock", Icons.Filled.Lock, enabled = d.online && result?.busy != true, Modifier.weight(1f)) { onAction("lock") }
            PowerButton("Shut down", Icons.Filled.PowerSettingsNew, enabled = d.online && result?.busy != true, Modifier.weight(1.2f), danger = true) { onAction("shutdown") }
        }
        if (result?.busy == true) Text("Sending ${powerLabel(result.action).lowercase()}…", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s))
        result?.error?.let {
            Banner(it, Icons.Filled.PowerSettingsNew, color = StatusFailed, tint = DangerTint, title = "Couldn't ${powerLabel(result.action).lowercase()}", actionLabel = "Dismiss", onAction = onDismissError, modifier = Modifier.padding(top = Space.s))
        }
        if (result?.done == true && result.error == null) Text("✓ ${powerLabel(result.action)} sent to ${d.name}", color = StatusSuccess, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s))
    }
}

@Composable
private fun PowerButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = PaddingValues(horizontal = 6.dp),
        colors = if (danger) ButtonDefaults.outlinedButtonColors(contentColor = StatusFailed) else ButtonDefaults.outlinedButtonColors(),
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, maxLines = 1)
    }
}

// ================================================================== Terminal

/** Strips the common ANSI/VT control sequences so a basic monospace view stays legible. */
internal fun stripAnsi(s: String): String = s
    .replace(Regex("\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)"), "") // OSC (e.g. window title)
    .replace(Regex("\u001B[\\[\\]][0-9;?=]*[ -/]*[@-~]"), "")           // CSI / control sequences
    .replace(Regex("\u001B[@-Z\\\\-_]"), "")                            // 2-char escapes
    .replace("\u0007", "")                                               // bell
    .replace("\r\n", "\n").replace('\r', '\n')

@Composable
fun TerminalScreen(store: BambooStore, onBack: () -> Unit) {
    val devices by store.devices.collectAsState()
    val online = devices.filter { it.kind == "desktop" && it.online }
    var pc by remember { mutableStateOf<Device?>(null) }
    BackHandler(onBack = onBack)
    val selected = pc
    if (selected == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("Terminal", "Choose a PC", navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
                item {
                    Banner(
                        "A terminal runs one shell in the project directory on your PC, over the signed device relay. It needs \"Allow remote control\" on at the PC.",
                        Icons.Filled.Terminal, color = TextSecondary, tint = BambooSurfaceElevated, modifier = Modifier.padding(top = Space.xs),
                    )
                }
                if (online.isEmpty()) item { EmptyState("No online PCs", "Open BambooKit Desktop on a PC to use its terminal.", Icons.Filled.DesktopWindows) }
                items(online, key = { it.id }) { d ->
                    Spacer(Modifier.height(Space.s))
                    BkCard(onClick = { pc = d }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Filled.Terminal)
                            Spacer(Modifier.width(Space.m))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text("Open a terminal", color = TextSecondary, fontSize = 12.sp)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item { BottomSpacer() }
            }
        }
    } else {
        TerminalSession(store, selected, onBack = { pc = null })
    }
}

@Composable
private fun TerminalSession(store: BambooStore, pc: Device, onBack: () -> Unit) {
    var termId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Opening a terminal on ${pc.name}…") }
    var error by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    BackHandler(onBack = onBack)

    // Open the terminal once, and close it when leaving.
    DisposableEffect(pc.id) {
        store.terminalOpen(pc, cols = 80, rows = 24) { res ->
            res.onSuccess { termId = it; status = "Connected" }
                .onFailure { error = it.message; status = "" }
        }
        onDispose { termId?.let { store.terminalClose(pc, it) } }
    }
    // Stream terminal.data for this terminal.
    LaunchedEffect(termId) {
        val id = termId ?: return@LaunchedEffect
        store.terminalData.collect { if (it.termId == id) output = (output + stripAnsi(it.data)).takeLast(60_000) }
    }
    LaunchedEffect(output) { if (output.isNotEmpty()) runCatching { listState.scrollToItem(0) } }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            pc.name, if (error != null) "Terminal error" else status,
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close terminal") } },
        )
        error?.let {
            Banner(it, Icons.Filled.Terminal, color = StatusFailed, tint = StatusFailedTint, title = "Couldn't open the terminal", modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.s))
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.screen).clip(RoundedCornerShape(10.dp)).background(CodeBlockBackground)) {
            if (output.isBlank() && error == null) {
                Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = StatusWarning)
                    Spacer(Modifier.width(Space.s))
                    Text(status, color = TextSecondary, fontSize = 12.sp)
                }
            } else {
                // reverseLayout keeps the newest output in view without manual measuring.
                LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize().padding(Space.s)) {
                    item {
                        SelectionContainer {
                            Text(output, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = BambooBorder)
        Row(Modifier.fillMaxWidth().padding(Space.s), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Type a command", fontSize = 13.sp) }, enabled = termId != null,
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextPrimary),
                modifier = Modifier.weight(1f), maxLines = 2,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                    termId?.let { store.terminalInput(pc, it, input + "\n"); input = "" }
                }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BambooBorder, unfocusedBorderColor = BambooBorder),
            )
            Spacer(Modifier.width(Space.s))
            IconButton(onClick = { termId?.let { scope.launch { store.terminalInput(pc, it, input + "\n"); input = "" } } }, enabled = termId != null) {
                Icon(Icons.Filled.Send, "Send", tint = if (termId != null) BambooGreen else TextMuted)
            }
        }
    }
}
