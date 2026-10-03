package com.bambookit.android.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.bambookit.android.data.UpdateState
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.StatusFailed
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
                    Text(n.notes.ifBlank { "No release notes." }, color = TextSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = { Button(onClick = { notes = null; updater.download(n) }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { notes = null; updater.skip(n.version) }) { Text("Skip this version", color = TextSecondary) } },
        )
    }
}

/** "App updates" card for the Devices tab: current version and a manual check. */
@Composable
fun UpdateCard(updater: AppUpdater) {
    val state by updater.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("BambooKit for Android ${BuildConfig.VERSION_NAME}", color = TextPrimary, fontWeight = FontWeight.Medium)
        if (!updater.enabled) {
            Text("Development build — updates come from Android Studio / Gradle, not GitHub Releases.", color = TextSecondary, fontSize = 12.sp)
            return@Column
        }
        Text(
            when (val s = state) {
                UpdateState.Checking -> "Checking GitHub for updates…"
                UpdateState.UpToDate -> "You have the latest version."
                is UpdateState.Available -> "Version ${s.version} is available."
                is UpdateState.Downloading -> "Downloading ${s.version}… ${(s.progress * 100).toInt()}%"
                is UpdateState.Ready -> "Version ${s.version} is downloaded and ready to install."
                is UpdateState.Failed -> s.message
                UpdateState.Idle -> "Updates are checked automatically when the app opens."
            },
            color = if (state is UpdateState.Failed) StatusFailed else TextSecondary,
            fontSize = 12.sp,
        )
        Row {
            when (val s = state) {
                is UpdateState.Available -> Button(onClick = { updater.download(s) }) { Text("Update to ${s.version}") }
                is UpdateState.Ready -> Button(onClick = { updater.install(s) }) { Text("Install ${s.version}") }
                is UpdateState.Downloading, UpdateState.Checking -> {}
                else -> OutlinedButton(onClick = { updater.dismissError(); updater.check(force = true) }) { Text("Check for updates") }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}
