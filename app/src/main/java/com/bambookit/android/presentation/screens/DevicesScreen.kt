package com.bambookit.android.presentation.screens

import com.bambookit.android.data.AppLock
import com.bambookit.android.data.AppUpdater
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.BuildConfig
import com.bambookit.android.data.ApiClient
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.Device
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary

@Composable
fun DevicesScreen(store: BambooStore, updater: AppUpdater, appLock: AppLock, pairStatus: String?, onScan: () -> Unit, onProfile: () -> Unit, onSignOut: () -> Unit) {
    val devices by store.devices.collectAsState()
    val me by store.myDeviceId.collectAsState()
    val account by store.session.collectAsState()
    val profile by store.profile.collectAsState()
    val loaded by store.loaded.collectAsState()
    val refreshing by store.refreshing.collectAsState()
    var renaming by remember { mutableStateOf<Device?>(null) }
    var confirmRevoke by remember { mutableStateOf<Device?>(null) }
    var confirmUnlink by remember { mutableStateOf<Device?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val myDesktops = devices.filter { d -> d.kind == "desktop" && d.linkedDevices.any { it.id == me } }
    val otherDesktops = devices.filter { d -> d.kind == "desktop" && d.linkedDevices.none { it.id == me } }

    RefreshBox(refreshing = refreshing, onRefresh = { store.refreshAll() }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen)) {
            item {
                BkCard(modifier = Modifier.padding(top = Space.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.QrCodeScanner)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text("Pair a PC", color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("In BambooKit Desktop choose Add mobile device and scan the QR code it shows.", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(Space.m))
                    Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s))
                        Text("Scan QR code")
                    }
                    pairStatus?.let {
                        Text(it, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = Space.s))
                    }
                }
            }

            item { SectionTitle("Paired with this phone") }
            if (!loaded && devices.isEmpty()) item { LoadingState("Loading devices…") }
            else if (myDesktops.isEmpty()) item {
                BkCard { Text("No PC is paired with this phone yet.", color = TextSecondary, fontSize = 13.sp) }
            }
            items(myDesktops, key = { it.id }) { d ->
                DesktopCard(d, onRename = { renaming = d }, onDisconnect = { confirmUnlink = d }, onRevoke = { confirmRevoke = d })
                Spacer(Modifier.height(Space.s))
            }
            if (otherDesktops.isNotEmpty()) {
                item { SectionTitle("Other PCs on this account") }
                items(otherDesktops, key = { it.id }) { d ->
                    DesktopCard(d, onRename = { renaming = d }, onDisconnect = null, onRevoke = { confirmRevoke = d })
                    Spacer(Modifier.height(Space.s))
                }
            }

            item {
                SectionTitle("This phone")
                BkCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.PhoneAndroid)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(devices.firstOrNull { it.id == me }?.name ?: ApiClient.deviceName(), color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("BambooKit for Android ${BuildConfig.VERSION_NAME}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
                SectionTitle("App updates")
                BkCard { UpdateCard(updater) }
                SectionTitle("Settings")
                NotificationSettings()
                Spacer(Modifier.height(Space.s))
                AppLockSetting(appLock)
                SectionTitle("Account")
                BkCard(onClick = onProfile) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val name = profile.account?.name ?: account?.name
                        val email = profile.account?.email ?: account?.email
                        Avatar(store, profile.account?.avatarUrl, name ?: email, 38.dp)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(name ?: email ?: "Signed in", color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (name != null) email?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
                        }
                        Text("Profile", color = TextSecondary, fontSize = 12.sp)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(Space.m))
                    OutlinedButton(
                        onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFailed),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.s))
                        Text("Sign out")
                    }
                }
                BottomSpacer()
            }
        }
    }

    renaming?.let { d ->
        var name by remember(d.id) { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = BambooSurfaceElevated,
            title = { Text("Rename device") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { store.rename(d, name.trim()); renaming = null }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    confirmUnlink?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmUnlink = null },
            containerColor = BambooSurfaceElevated,
            title = { Text("Disconnect ${d.name}?") },
            text = { Text("This phone stops being paired with ${d.name}. You can pair again by scanning a new QR code.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { store.unlink(d); confirmUnlink = null }) { Text("Disconnect", color = StatusFailed) } },
            dismissButton = { TextButton(onClick = { confirmUnlink = null }) { Text("Cancel") } },
        )
    }
    confirmRevoke?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmRevoke = null },
            containerColor = BambooSurfaceElevated,
            title = { Text("Revoke ${d.name}?") },
            text = { Text("The PC will be disconnected from your BambooKit account and must sign in again to reconnect.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { store.revoke(d); confirmRevoke = null }) { Text("Revoke", color = StatusFailed) } },
            dismissButton = { TextButton(onClick = { confirmRevoke = null }) { Text("Cancel") } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = BambooSurfaceElevated,
            title = { Text("Sign out?") },
            text = { Text("This phone stops receiving updates and approvals until you sign in again.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out", color = StatusFailed) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DesktopCard(d: Device, onRename: () -> Unit, onDisconnect: (() -> Unit)?, onRevoke: () -> Unit) {
    BkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.DesktopWindows, tint = if (d.online) TextPrimary else TextMuted)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(d.name, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("BambooKit Desktop${d.appVersion?.let { " · v$it" } ?: ""}", color = TextSecondary, fontSize = 12.sp)
            }
            OnlineChip(d.online)
        }
        d.activeSession?.let {
            Spacer(Modifier.height(Space.s))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BambooSurfaceElevated).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(it.title, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    it.projectName?.let { p -> Mono(p, size = 11) }
                }
                StatusChip(it.status)
            }
        }
        if (!d.online && d.lastSeenAt != null) Text("Last seen ${relative(d.lastSeenAt)}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s))
        Spacer(Modifier.height(Space.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CardAction("Rename", Icons.Filled.Edit, onClick = onRename)
            if (onDisconnect != null) CardAction("Disconnect", Icons.Filled.LinkOff, onClick = onDisconnect)
            CardAction("Revoke", Icons.Filled.Block, danger = true, onClick = onRevoke)
        }
    }
}

@Composable
private fun CardAction(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    TextButton(
        onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp),
        colors = if (danger) ButtonDefaults.textButtonColors(contentColor = StatusFailed) else ButtonDefaults.textButtonColors(),
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp)
    }
}
