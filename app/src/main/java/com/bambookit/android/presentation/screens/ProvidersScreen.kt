package com.bambookit.android.presentation.screens

import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bambookit.android.data.AiProvider
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.Device
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusRunningTint
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary

/** Profile section: one row per PC, leading to its AI providers. */
@Composable
internal fun AiProvidersSection(store: BambooStore, onOpen: (String) -> Unit) {
    val devices by store.devices.collectAsState()
    val me by store.myDeviceId.collectAsState()
    val pcs = devices.filter { d -> d.kind == "desktop" && d.revokedAt == null }.sortedByDescending { d -> d.linkedDevices.any { it.id == me } }
    BkCard {
        if (pcs.isEmpty()) Text("Pair a PC first. AI providers and their keys live on your PC.", color = TextSecondary, fontSize = 13.sp)
        pcs.forEachIndexed { i, pc ->
            if (i > 0) Spacer(Modifier.height(Space.s))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Filled.Memory, tint = if (pc.online) TextPrimary else TextMuted, size = 34.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(pc.name, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (pc.online) "Online · models and keys" else "Offline", color = if (pc.online) TextSecondary else TextMuted, fontSize = 12.sp)
                }
                TextButton(onClick = { onOpen(pc.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open")
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}


/**
 * One PC's AI providers (GET /v1/devices/:id/providers): configured state, models, and a field to add,
 * replace or remove a key for the providers the PC can connect. Keys are encrypted on this phone for that PC
 * only, wiped from memory and from the field once sent, never stored or shown again.
 */
@Composable
fun ProvidersScreen(store: BambooStore, deviceId: String, onClose: () -> Unit) {
    if (LocalAppLocked.current) return
    val devices by store.devices.collectAsState()
    val all by store.providers.collectAsState()
    val pc = devices.firstOrNull { it.id == deviceId }
    val view = all[deviceId]
    LaunchedEffect(deviceId) { store.loadProviders(deviceId, force = true) }
    var keyFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var removing by remember { mutableStateOf<Pair<String, String>?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onClose)
        Column(Modifier.fillMaxSize().background(BambooObsidian)) {
            ScreenTopBar(
                "AI providers", pc?.name,
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { store.loadProviders(deviceId, force = true) }, enabled = view?.loading != true) {
                        if (view?.loading == true) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, "Reload providers")
                    }
                },
            )
            val pcName = pc?.name ?: "your PC"
            val info = view?.info
            val err = view?.error
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
                if (pc != null && !pc.online) item {
                    Banner(
                        "Providers are read live from $pcName. Open BambooKit Desktop there; keys you add now are delivered if it reconnects within 5 minutes.",
                        Icons.Filled.CloudOff, color = StatusWarning, tint = StatusWarningTint, title = "$pcName is offline",
                        modifier = Modifier.padding(top = Space.s),
                    )
                }
                if (pc != null && pc.encryptionKey.isNullOrBlank()) item {
                    Banner(
                        "This version of BambooKit Desktop can't receive encrypted keys. Update it on $pcName to add keys from your phone.",
                        Icons.Filled.SystemUpdate, color = StatusWarning, tint = StatusWarningTint, title = "Update BambooKit Desktop",
                        modifier = Modifier.padding(top = Space.s),
                    )
                }
                when {
                    info == null && (view == null || view.loading) -> item { LoadingState("Reading the AI providers on $pcName…") }
                    info == null && err != null -> item {
                        ErrorState(
                            errorTitle(err, pcName, "Couldn't read the AI providers"), errorMessage(err, pcName),
                            if (err.desktopOutdated) Icons.Filled.SystemUpdate else Icons.Filled.CloudOff,
                            onRetry = { store.loadProviders(deviceId, force = true) }, retrying = view.loading,
                            color = if (err.desktopUnavailable || err.desktopOutdated) StatusWarning else StatusFailed,
                        )
                    }
                }
                if (info != null) {
                    if (err != null) item { StaleBanner(err, view.loading) { store.loadProviders(deviceId, force = true) } }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.m)) {
                            Icon(Icons.Filled.Lock, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Keys are encrypted on this phone for $pcName only. BambooKit never sees or stores them, and this phone never shows them again.",
                                color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                            )
                        }
                    }
                    info.default?.let { def -> item { Text("Default model: ${modelLabel(def, info)}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s)) } }
                    val connectable = info.connectable.associateBy { it.id }
                    val listed = info.providers.map { it.id }.toSet()
                    val rows: List<AiProvider> = info.providers.sortedWith(compareByDescending<AiProvider> { it.configured }.thenBy { (it.name ?: it.id).lowercase() }) +
                        info.connectable.filter { it.id !in listed }.map { AiProvider(it.id, it.name, configured = false) }
                    item { SectionTitle("Providers (${rows.size})") }
                    if (rows.isEmpty()) item { EmptyState("No providers", "BambooKit Desktop on $pcName reported no AI providers.", Icons.Filled.Memory) }
                    items(rows, key = { it.id }) { p ->
                        ProviderRow(
                            p, canSetKey = p.id in connectable, busy = p.id in view.busy, canEncrypt = !pc?.encryptionKey.isNullOrBlank(),
                            onKey = { keyFor = p.id to (brandModel(p.name ?: p.id) ?: p.id) }, onRemove = { removing = p.id to (brandModel(p.name ?: p.id) ?: p.id) },
                        )
                        Spacer(Modifier.height(Space.s))
                    }
                    item {
                        Banner(
                            "Only the providers above can be set up from your phone, and custom endpoints aren't supported here. If your provider isn't listed, set it up in BambooKit Desktop on $pcName.",
                            Icons.Filled.Info, color = TextSecondary, tint = BambooSurfaceElevated, modifier = Modifier.padding(top = Space.m),
                        )
                    }
                }
                item { BottomSpacer() }
            }
        }
    }
    keyFor?.let { (id, name) ->
        if (pc != null) KeyDialog(store, pc, id, name, onDone = { keyFor = null })
    }
    removing?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = BambooSurfaceElevated,
            icon = { Icon(Icons.Filled.Key, null, tint = StatusFailed) },
            title = { Text("Remove the $name key?") },
            text = { Text("BambooKit Desktop on ${pc?.name ?: "your PC"} deletes its saved key. Sessions can't use $name until a key is added again.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { pc?.let { store.removeProviderKey(it, id) }; removing = null }) { Text("Remove", color = StatusFailed) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProviderRow(p: AiProvider, canSetKey: Boolean, busy: Boolean, canEncrypt: Boolean, onKey: () -> Unit, onRemove: () -> Unit) {
    BkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(brandModel(p.name ?: p.id) ?: p.id, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(
                        if (p.configured) "Configured ✓" else "Not configured",
                        p.models.size.takeIf { it > 0 }?.let { plural(it, "model") },
                        p.source?.takeIf { it.isNotBlank() }?.let { "from $it" },
                    ).joinToString(" · "),
                    color = if (p.configured) StatusSuccess else TextMuted, fontSize = 12.sp,
                )
            }
            if (busy) Chip("Saving on PC…", StatusRunning, StatusRunningTint)
        }
        if (canSetKey) Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.padding(top = Space.s)) {
            Button(onClick = onKey, enabled = !busy && canEncrypt, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Key, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (p.configured) "Replace key" else "Add key")
            }
            if (p.configured) OutlinedButton(
                onClick = onRemove, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFailed),
            ) { Text("Remove") }
        } else if (!p.configured) Text("Set up on the PC (this provider can't be connected from the phone).", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Key entry: hidden by default, no suggestions, cleared from the field as soon as it is sent. */
@Composable
private fun KeyDialog(store: BambooStore, pc: Device, providerId: String, name: String, onDone: () -> Unit) {
    // Not rememberSaveable: the key must never be written to the saved instance state.
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun close() { value = ""; onDone() }
    AlertDialog(
        onDismissRequest = { if (!sending) close() },
        containerColor = BambooSurfaceElevated,
        icon = { Icon(Icons.Filled.Key, null) },
        title = { Text("$name API key") },
        text = {
            Column {
                OutlinedTextField(
                    value = value, onValueChange = { value = it.trim(); error = null }, enabled = !sending, singleLine = true,
                    label = { Text("API key") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Hide key" else "Show key")
                        }
                    },
                    isError = error != null,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BambooBorderStrong, unfocusedBorderColor = BambooBorder),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    error ?: "Encrypted on this phone for ${pc.name}; only that PC can read it.",
                    color = if (error != null) StatusFailed else TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank() && !sending,
                onClick = {
                    val chars = value.toCharArray()
                    value = ""
                    sending = true
                    store.setProviderKey(pc, providerId, chars) { problem ->
                        sending = false
                        if (problem == null) close() else error = problem
                    }
                },
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = { close() }, enabled = !sending) { Text("Cancel") } },
    )
}
