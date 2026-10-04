package com.bambookit.android.presentation.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bambookit.android.BambooKitApp
import com.bambookit.android.ConnectionService
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary

/** Bumps a counter every time the screen resumes, so system settings changed elsewhere are re-read. */
@Composable
internal fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

internal fun notificationsAllowed(context: Context): Boolean = (context.applicationContext as BambooKitApp).notifier.canPost()

/** Opens BambooKit's notification settings in Android Settings. */
internal fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Asks for notification permission (Android 13+). If Android won't show the prompt any more (denied twice),
 * opens the app's notification settings instead.
 */
@Composable
internal fun rememberNotificationEnabler(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= 33) {
            val activity = context as? Activity
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) openNotificationSettings(context)
        }
    }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openNotificationSettings(context)
    }
}

/**
 * Home: a first-time reminder while notifications are off. It goes away for good once the user taps
 * "Turn on" or "Not now"; after that, notifications are managed in Profile only.
 */
@Composable
fun NotificationsOffBanner() {
    val context = LocalContext.current
    val app = context.applicationContext as BambooKitApp
    val tick = rememberResumeTick()
    val allowed = remember(tick) { notificationsAllowed(context) }
    var done by remember { mutableStateOf(app.secure.notificationBannerDone) }
    val enable = rememberNotificationEnabler()
    if (allowed || done) return
    fun finish() { app.secure.notificationBannerDone = true; done = true }
    BkCard(modifier = Modifier.padding(horizontal = Space.screen).padding(bottom = Space.s), border = StatusWarning.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.NotificationsOff, tint = StatusWarning, background = StatusWarningTint, size = 34.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("Notifications are off", color = StatusWarning, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "Turn them on to hear when the agent asks for approval or a question, or a session finishes. You can change this later in Profile.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.s), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            androidx.compose.material3.TextButton(onClick = { finish() }) { Text("Not now", color = TextSecondary) }
            androidx.compose.material3.Button(onClick = { finish(); enable() }) { Text("Turn on") }
        }
    }
}

/** Settings: notifications, "Notify me in the background" and battery optimization guidance. */
@Composable
fun NotificationSettings() {
    val context = LocalContext.current
    val app = context.applicationContext as BambooKitApp
    val tick = rememberResumeTick()
    var background by remember { mutableStateOf(app.secure.backgroundNotify) }
    val allowed = remember(tick) { notificationsAllowed(context) }
    val power = context.getSystemService(PowerManager::class.java)
    val unrestricted = remember(tick) { power?.isIgnoringBatteryOptimizations(context.packageName) == true }
    val enable = rememberNotificationEnabler()
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = BambooObsidian, checkedTrackColor = BambooGreen,
        uncheckedThumbColor = TextMuted, uncheckedTrackColor = BambooGreenSubtle, uncheckedBorderColor = BambooBorderStrong,
    )

    BkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (allowed) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff, tint = if (allowed) TextPrimary else StatusWarning)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("Notifications", color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    if (allowed) "On. You're notified every time the agent asks for approval or asks a question, and when a session finishes or fails."
                    else "Off. Turn them on to hear when the agent needs you.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                )
            }
        }
        if (!allowed) OutlinedButton(onClick = enable, modifier = Modifier.fillMaxWidth().padding(top = Space.s)) { Text("Turn on notifications") }
        else OutlinedButton(onClick = { openNotificationSettings(context) }, modifier = Modifier.fillMaxWidth().padding(top = Space.s)) { Text("Sound and notification settings") }

        HorizontalDivider(color = BambooBorder, modifier = Modifier.padding(vertical = Space.m))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notify me in the background", color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    "Stays connected after you leave the app or restart the phone, so notifications still arrive. " +
                        "Android shows a silent \"BambooKit is connected\" notification while this is on.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.width(Space.s))
            Switch(
                checked = background, colors = switchColors,
                onCheckedChange = { on ->
                    background = on
                    app.secure.backgroundNotify = on
                    ConnectionService.sync(context)
                },
            )
        }

        if (background) {
            Spacer(Modifier.height(Space.m))
            if (unrestricted) Text(
                "Battery: unrestricted. Android won't pause BambooKit's connection to save battery.",
                color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp,
            ) else Column {
                Banner(
                    "Android may pause the connection to save battery, which delays notifications. For reliable notifications, " +
                        "open BambooKit's settings, tap Battery and choose Unrestricted.",
                    Icons.Filled.BatteryAlert, color = StatusWarning, tint = StatusWarningTint, title = "Battery optimization",
                )
                OutlinedButton(onClick = { openBatterySettings(context) }, modifier = Modifier.fillMaxWidth().padding(top = Space.s)) { Text("Open battery settings") }
            }
        }
    }
}

/** BambooKit's app settings page (Battery → Unrestricted), or the system's battery optimization list. */
private fun openBatterySettings(context: Context) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(details) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}
