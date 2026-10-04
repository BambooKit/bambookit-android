package com.bambookit.android.presentation.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.BuildConfig
import com.bambookit.android.data.ContentError
import com.bambookit.android.data.DesktopRequirement
import com.bambookit.android.data.DiagAction
import com.bambookit.android.data.Diagnosis
import com.bambookit.android.data.Diagnostics
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.CodeBlockBackground
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary

/** App-wide actions the ⓘ sheet can run (provided once in the app shell). */
data class DiagHandlers(val refresh: () -> Unit = {}, val reconnect: () -> Unit = {})

val LocalDiagHandlers = compositionLocalOf { DiagHandlers() }

/** Releases page of BambooKit Desktop (Windows), shown by "Update Desktop info". */
private const val DESKTOP_RELEASES = "https://github.com/BambooKit/bambookit-application/releases/latest"

val APP_VERSION_LABEL = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

/** The ⓘ button that opens the diagnostics of a failure. */
@Composable
fun InfoButton(diagnosis: Diagnosis, onRetry: (() -> Unit)?, tint: Color = TextSecondary, title: String? = null) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Info, "What happened (details)", tint = tint, modifier = Modifier.size(20.dp)) }
    if (open) DiagnosticsSheet(diagnosis, title, onRetry, onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsSheet(diagnosis: Diagnosis, title: String?, onRetry: (() -> Unit)?, onDismiss: () -> Unit) {
    val handlers = LocalDiagHandlers.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val explanation = remember(diagnosis) { Diagnostics.explain(diagnosis) }
    val technical = remember(diagnosis) { Diagnostics.technical(diagnosis, APP_VERSION_LABEL) }
    val recent = remember(diagnosis) { Diagnostics.recent().filter { it.at != diagnosis.at || it.path != diagnosis.path }.take(8) }
    var copied by remember { mutableStateOf(false) }
    var desktopInfo by remember { mutableStateOf(false) }
    var showRecent by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BambooSurfaceElevated) {
        Column(Modifier.padding(horizontal = Space.screen).padding(bottom = Space.xl).verticalScroll(rememberScrollState())) {
            Heading("What happened?")
            title?.let { Text(it, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            Text(diagnosis.message, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)

            Heading("Why it may have happened")
            Text(explanation.why, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)

            Heading("What you can do")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                explanation.actions.forEach { a ->
                    when (a) {
                        DiagAction.Retry -> if (onRetry != null) Button(onClick = { onDismiss(); onRetry() }) { Text(a.label) }
                        DiagAction.Refresh -> OutlinedButton(onClick = { onDismiss(); handlers.refresh() }) { Text(a.label) }
                        DiagAction.Reconnect -> OutlinedButton(onClick = { onDismiss(); handlers.reconnect() }) { Text(a.label) }
                        DiagAction.UpdateDesktop -> OutlinedButton(onClick = { desktopInfo = !desktopInfo }) { Text(a.label) }
                        DiagAction.OpenSettings -> OutlinedButton(onClick = {
                            val network = diagnosis.code == "NETWORK" || diagnosis.code == "SERVER_UNAVAILABLE" || diagnosis.code == "DOWNLOAD_FAILED" || diagnosis.code == "UPDATE_SOURCE_UNAVAILABLE"
                            val intent = if (network) Intent(Settings.ACTION_WIRELESS_SETTINGS)
                            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text(a.label) }
                    }
                }
            }
            if (desktopInfo) {
                Spacer(Modifier.height(Space.s))
                DesktopUpdateSteps(diagnosis.desktop, diagnosis.desktopVersion)
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DESKTOP_RELEASES)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                    Text("Open the BambooKit Desktop download page")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Heading("Technical details") }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(technical + if (recent.isNotEmpty()) "\n\nRecent failed requests:\n" + recent.joinToString("\n") { recentLine(it) } else ""))
                    copied = true
                }) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copied" else "Copy")
                }
            }
            SelectionContainer {
                Text(
                    technical, color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CodeBlockBackground).padding(Space.m),
                )
            }
            if (recent.isNotEmpty()) {
                TextButton(onClick = { showRecent = !showRecent }) { Text(if (showRecent) "Hide recent failed requests" else "Recent failed requests (${recent.size})") }
                if (showRecent) SelectionContainer {
                    Text(
                        recent.joinToString("\n") { recentLine(it) }, color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CodeBlockBackground).padding(Space.m),
                    )
                }
            }
            Text("No sign-in tokens, keys or signed links are included.", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = Space.s))
        }
    }
}

private fun recentLine(d: Diagnosis): String =
    "${java.time.Instant.ofEpochMilli(d.at).toString().substring(11, 19)} ${d.method.orEmpty()} ${d.path.orEmpty()} → ${if (d.status > 0) d.status else "-"} ${d.code}${d.requestId?.let { " ($it)" } ?: ""}"

@Composable
private fun Heading(text: String) {
    Text(text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.l, bottom = Space.xs))
}

@Composable
private fun DesktopUpdateSteps(req: DesktopRequirement?, version: String?) {
    val pc = req?.device ?: "your PC"
    Text(
        "On $pc, open BambooKit Desktop and install the latest version" +
            (req?.requiredVersion?.let { " ($it or newer)" } ?: "") + ". " +
            "Installed there now: ${req?.currentVersion ?: version ?: "unknown"}. Once it restarts it reports its new version and this works without anything else to do on the phone.",
        color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
    )
}

/**
 * "Update BambooKit Desktop" card: shown instead of a feature the PC lacks (no request is sent),
 * with the installed and required versions and the reason.
 */
@Composable
fun DesktopUpdateCard(req: DesktopRequirement, diagnosis: Diagnosis, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    BkCard(modifier.padding(top = Space.m), border = com.bambookit.android.presentation.theme.DesktopUpdateBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.SystemUpdate, tint = StatusWarning, background = StatusWarningTint)
            Spacer(Modifier.width(Space.m))
            Text("Update BambooKit Desktop", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            InfoButton(diagnosis, onRetry, title = "Update BambooKit Desktop on ${req.device}")
        }
        Spacer(Modifier.height(Space.s))
        Text("${req.device} has BambooKit Desktop ${req.currentVersion ?: "(version unknown)"}. This needs ${req.requiredVersion} or newer.", color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        if (req.reason.isNotBlank()) Text(req.reason, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = Space.xs))
        Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            VersionFact("Installed", req.currentVersion ?: "unknown")
            VersionFact("Required", req.requiredVersion)
        }
        if (Diagnostics.serverOutdated) Text(
            "The BambooKit server is also older than this app and needs to be updated.",
            color = StatusWarning, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s),
        )
        if (onRetry != null) OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = Space.s)) { Text("Check again") }
    }
}

@Composable
private fun VersionFact(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}

/**
 * A [ContentError] as a full state: the desktop-update card when the PC lacks the feature, else an error state
 * whose title says what is wrong (PC offline, server older than the app…), always with ⓘ.
 */
@Composable
fun ContentErrorState(err: ContentError, pcName: String, fallbackTitle: String, onRetry: (() -> Unit)?, retrying: Boolean = false) {
    val req = err.update
    if (req != null) {
        Column(Modifier.padding(horizontal = Space.m)) { DesktopUpdateCard(req, err.diagnosis, onRetry) }
        return
    }
    ErrorState(
        errorTitle(err, pcName, fallbackTitle), errorMessage(err, pcName),
        if (err.desktopOutdated || err.serverOutdated) Icons.Filled.SystemUpdate else if (err.desktopUnavailable || err.code == "NETWORK") Icons.Filled.CloudOff else Icons.Filled.ErrorOutline,
        onRetry = onRetry, retrying = retrying,
        color = if (err.desktopUnavailable || err.desktopOutdated || err.timedOut || err.serverOutdated) StatusWarning else StatusFailed,
        diagnosis = err.diagnosis,
    )
}
