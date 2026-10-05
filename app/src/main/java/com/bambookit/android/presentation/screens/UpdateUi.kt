package com.bambookit.android.presentation.screens

import com.bambookit.android.ads.AdPlacements
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.BuildConfig
import com.bambookit.android.data.AppUpdater
import com.bambookit.android.data.ReleaseInfo
import com.bambookit.android.data.UpdateFailure
import com.bambookit.android.data.UpdateState
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusFailedTint
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary

private fun mb(bytes: Long) = if (bytes > 0) " · ${"%.1f".format(bytes / 1_048_576.0)} MB" else ""

/** Shown under the top bar on every tab while an update is available, downloading or ready. */
@Composable
fun UpdateBanner(updater: AppUpdater) {
    val state by updater.state.collectAsState()
    var notes by remember { mutableStateOf<UpdateState.Available?>(null) }
    val s = state
    if (s !is UpdateState.Available && s !is UpdateState.Downloading && s !is UpdateState.Ready) return
    Surface(color = BambooSurface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    when (s) {
                        is UpdateState.Available -> {
                            Text("BambooKit ${s.version} is available", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("You have ${BuildConfig.VERSION_NAME}${mb(s.size)}", color = TextSecondary, fontSize = 12.sp)
                        }
                        is UpdateState.Downloading -> Text("Downloading BambooKit ${s.version}… ${(s.progress * 100).toInt()}%", color = TextPrimary, fontSize = 14.sp)
                        is UpdateState.Ready -> Text("BambooKit ${s.version} is ready to install", color = TextPrimary, fontSize = 14.sp)
                        else -> {}
                    }
                }
                when (s) {
                    is UpdateState.Available -> {
                        TextButton(onClick = { notes = s }) { Text("What's new", color = TextSecondary) }
                        Button(onClick = { updater.download(s) }) { Text("Update") }
                    }
                    is UpdateState.Ready -> Button(onClick = { updater.install(s) }) { Text("Install") }
                    else -> {}
                }
            }
            if (s is UpdateState.Downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = BambooGreen, trackColor = BambooBorder)
            }
        }
    }
    notes?.let { n ->
        AlertDialog(
            onDismissRequest = { notes = null },
            containerColor = BambooSurface,
            title = { Text("BambooKit ${n.version}", color = TextPrimary) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (n.notes.isBlank()) Text("No release notes.", color = TextSecondary, fontSize = 13.sp) else MarkdownText(n.notes, TextSecondary)
                }
            },
            confirmButton = { Button(onClick = { notes = null; updater.download(n) }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { notes = null; updater.skip(n.version) }) { Text("Skip this version", color = TextSecondary) } },
        )
    }
}

/** One-line status of the updater (Profile row and the App updates screen). */
fun updateStatus(state: UpdateState, enabled: Boolean): String = when {
    !enabled -> "Development build: updates come from Android Studio / Gradle."
    else -> when (state) {
        UpdateState.Idle -> "Updates are checked automatically every 6 hours."
        UpdateState.Checking -> "Checking for updates…"
        is UpdateState.UpToDate -> "You have the latest version."
        is UpdateState.Available -> "Version ${state.version} is available."
        is UpdateState.Downloading -> "Downloading ${state.version}… ${(state.progress * 100).toInt()}%"
        is UpdateState.Ready -> state.note ?: "Version ${state.version} is downloaded and ready to install."
        is UpdateState.Installing -> "Installing ${state.version}… confirm in Android's installer."
        is UpdateState.Failed -> state.message
    }
}

/** "App updates" row in Profile: current version and status; opens the App updates screen. */
@Composable
fun UpdateCard(updater: AppUpdater, onOpen: () -> Unit) {
    val state by updater.state.collectAsState()
    BkCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.SystemUpdate)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("BambooKit for Android $APP_VERSION_LABEL", color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    updateStatus(state, updater.enabled), fontSize = 12.sp, maxLines = 2,
                    color = when (state) { is UpdateState.Failed -> StatusFailed; is UpdateState.Available, is UpdateState.Ready -> StatusWarning; else -> TextSecondary },
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open App updates", tint = TextSecondary)
        }
    }
}

/** Profile → App updates: versions, release date and notes, status, check, download and install. */
@Composable
fun UpdateScreen(updater: AppUpdater, onBack: () -> Unit) {
    val state by updater.state.collectAsState()
    val latest by updater.latest.collectAsState()
    val lastChecked by updater.lastCheckedAt.collectAsState()
    val installed by updater.installed.collectAsState()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(BambooObsidian)) {
        ScreenTopBar("App updates", "BambooKit for Android", navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen)) {
            installed?.let { msg ->
                Banner(msg, Icons.Filled.CheckCircle, color = StatusSuccess, tint = com.bambookit.android.presentation.theme.StatusSuccessTint, actionLabel = "OK", onAction = { updater.dismissInstalled() }, modifier = Modifier.padding(top = Space.s))
            }
            BkCard(Modifier.padding(top = Space.m)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    UFact("Installed", APP_VERSION_LABEL)
                    UFact("Latest", latest?.version ?: if (state == UpdateState.Checking) "Checking…" else "Not checked yet")
                    latest?.publishedAt?.let { p -> UFact("Released", dateOnly(p) ?: p) }
                    UFact("Last checked", lastChecked?.let { relativeMillis(it).ifBlank { null } } ?: "Never")
                    latest?.let { r -> UFact("Source", if (r.source == "github") "GitHub Releases (server is older)" else "BambooKit server") }
                    val s = state
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Status", color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(104.dp))
                        Text(
                            updateStatus(s, updater.enabled), fontSize = 13.sp, modifier = Modifier.weight(1f),
                            color = when (s) { is UpdateState.Failed -> StatusFailed; is UpdateState.Available, is UpdateState.Ready -> StatusWarning; is UpdateState.UpToDate -> StatusSuccess; else -> TextPrimary },
                        )
                    }
                }
            }
            val s = state
            if (s is UpdateState.Downloading) {
                Spacer(Modifier.height(Space.m))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), color = BambooGreen, trackColor = BambooBorder)
            }
            if (s is UpdateState.Failed) {
                Banner(
                    s.message,
                    if (s.kind == UpdateFailure.NoInternet || s.kind == UpdateFailure.ServerUnavailable) Icons.Filled.CloudOff else Icons.Filled.SystemUpdate,
                    color = if (s.kind == UpdateFailure.NoRelease) StatusWarning else StatusFailed,
                    tint = if (s.kind == UpdateFailure.NoRelease) StatusWarningTint else StatusFailedTint,
                    title = failureTitle(s.kind),
                    modifier = Modifier.padding(top = Space.m),
                    diagnosis = s.diagnosis,
                    onAction = { updater.dismissError(); updater.check(force = true) },
                )
            }
            Row(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                if (updater.enabled) OutlinedButton(
                    onClick = { updater.dismissError(); updater.check(force = true) },
                    enabled = s != UpdateState.Checking && s !is UpdateState.Downloading && s !is UpdateState.Installing,
                ) {
                    if (s == UpdateState.Checking) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Space.s))
                    }
                    Text("Check for updates")
                }
                when (s) {
                    is UpdateState.Available -> Button(onClick = { updater.download(s) }) { Text("Update to ${s.version}${mb(s.size)}") }
                    is UpdateState.Ready -> Button(onClick = { updater.install(s) }) { Text("Install ${s.version}") }
                    is UpdateState.Installing -> Text("Waiting for the installer…", color = StatusRunning, fontSize = 12.sp)
                    else -> Unit
                }
            }
            Text(
                "Updates are checked automatically at most every 6 hours. Android always asks you to confirm before installing, " +
                    "and BambooKit only reports an update as installed once the app was actually replaced.",
                color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = Space.m),
            )
            latest?.let { r -> ReleaseNotes(r) }
            BottomSpacer()
        }
        // Free plan: a banner at the bottom, under the scrolling page (no buttons fixed there).
        ScreenAd(AdPlacements.Screen.AppUpdates, AdPlacements.Position.Bottom)
    }
}

private fun failureTitle(kind: UpdateFailure) = when (kind) {
    UpdateFailure.NoInternet -> "No internet connection"
    UpdateFailure.ServerUnavailable -> "Update server unavailable"
    UpdateFailure.InvalidResponse -> "Invalid response"
    UpdateFailure.NoRelease -> "No release yet"
    UpdateFailure.DownloadFailed -> "Download failed"
    UpdateFailure.Other -> "Couldn't check for updates"
}

@Composable
private fun ReleaseNotes(r: ReleaseInfo) {
    SectionTitle("Release notes · ${r.name?.takeIf { it.isNotBlank() } ?: r.version}")
    BkCard {
        if (r.notes.isBlank()) Text("No release notes.", color = TextSecondary, fontSize = 13.sp)
        else MarkdownText(r.notes, TextSecondary)
    }
}

@Composable
private fun UFact(label: String, value: String) {
    Row {
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(104.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
