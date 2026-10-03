package com.bambookit.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bambookit.android.data.ApiException
import com.bambookit.android.data.BambooStore
import com.bambookit.android.presentation.screens.ApprovalsScreen
import com.bambookit.android.presentation.screens.ConnectionBanner
import com.bambookit.android.presentation.screens.UpdateBanner
import com.bambookit.android.presentation.screens.DevicesScreen
import com.bambookit.android.presentation.screens.HomeScreen
import com.bambookit.android.presentation.screens.LoginScreen
import com.bambookit.android.presentation.screens.ProjectsScreen
import com.bambookit.android.presentation.screens.ScreenTopBar
import com.bambookit.android.presentation.screens.SessionScreen
import com.bambookit.android.presentation.screens.plural
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooKitTheme
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.TextSecondary
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val incoming = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming.value = intent
        val app = application as BambooKitApp
        setContent { BambooKitTheme { Root(app, incoming) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming.value = intent
    }

    override fun onResume() {
        super.onResume()
        // New releases on GitHub: checked when the app opens, at most every few hours.
        (application as BambooKitApp).updater.check()
    }
}

private enum class Tab(val label: String) { Home("Home"), Projects("Projects"), Approvals("Approvals"), Devices("Devices") }

/** Extracts the one-time token from a bambookit://pair?t=<token> QR payload. */
fun pairingToken(raw: String?): String? {
    if (raw == null) return null
    val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "bambookit" || uri.host != "pair") return null
    return uri.getQueryParameter("t")?.takeIf { it.length >= 20 }
}

@Composable
private fun Root(app: BambooKitApp, incoming: MutableStateFlow<Intent?>) {
    val store = app.store
    val session by app.auth.session.collectAsState()
    val intent by incoming.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var openSession by rememberSaveable { mutableStateOf<String?>(null) }
    var pairStatus by remember { mutableStateOf<String?>(null) }
    var pendingPairToken by remember { mutableStateOf<String?>(null) }
    val approvals by store.approvals.collectAsState()

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun claim(token: String) {
        pairStatus = "Pairing…"
        store.pair(token) { result ->
            result.onSuccess {
                pairStatus = "Connected to ${it.desktop.name}"
                tab = Tab.Home
                scope.launch { snackbar.showSnackbar("Paired with ${it.desktop.name}") }
            }.onFailure {
                pairStatus = when ((it as? ApiException)?.code) {
                    "PAIRING_TOKEN_EXPIRED" -> "That QR code expired. A new one is shown on the desktop — scan again."
                    "PAIRING_TOKEN_USED" -> "That QR code was already used. Scan the new code on the desktop."
                    "ACCOUNT_MISMATCH" -> "The desktop is signed in to a different BambooKit account."
                    else -> "Pairing failed: ${it.message}"
                }
            }
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val token = pairingToken(result.contents)
        when {
            result.contents == null -> pairStatus = null
            token == null -> pairStatus = "That is not a BambooKit pairing code."
            else -> claim(token)
        }
    }
    fun scan() {
        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the QR code shown in BambooKit Desktop").setBeepEnabled(false).setOrientationLocked(true))
    }

    LaunchedEffect(session?.userId) {
        if (session != null) {
            store.start()
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(intent) {
        val i = intent ?: return@LaunchedEffect
        i.getStringExtra("sessionId")?.let { openSession = it }
        pairingToken(i.dataString)?.let { pendingPairToken = it }
        incoming.value = null
    }
    LaunchedEffect(pendingPairToken, session?.userId) {
        val token = pendingPairToken ?: return@LaunchedEffect
        if (session != null) {
            pendingPairToken = null
            tab = Tab.Devices
            claim(token)
        }
    }
    LaunchedEffect(Unit) { store.messages.collect { snackbar.showSnackbar(it) } }

    if (session == null) {
        LoginScreen(app.auth, onGoogle = {}, googleAvailable = false)
        return
    }

    Scaffold(
        containerColor = BambooObsidian,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (openSession == null) Column {
                HorizontalDivider(color = BambooBorder)
                NavigationBar(containerColor = BambooSurface) {
                    Tab.entries.forEach { t ->
                        val selected = tab == t
                        NavigationBarItem(
                            selected = selected,
                            onClick = { tab = t },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BambooGreen, selectedTextColor = BambooGreen, indicatorColor = BambooGreenSubtle,
                                unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary,
                            ),
                            icon = {
                                val icon = when (t) {
                                    Tab.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                                    Tab.Projects -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
                                    Tab.Approvals -> if (selected) Icons.Filled.VerifiedUser else Icons.Outlined.VerifiedUser
                                    Tab.Devices -> if (selected) Icons.Filled.Computer else Icons.Outlined.Computer
                                }
                                val count = if (t == Tab.Approvals) approvals.count { it.isPending } else 0
                                if (count > 0) BadgedBox(badge = { Badge { Text("$count") } }) { Icon(icon, t.label) } else Icon(icon, t.label)
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val current = openSession
            if (current != null) {
                SessionScreen(store, current, onBack = { openSession = null })
            } else Column(Modifier.fillMaxSize()) {
                TabTopBar(tab, store, account = session?.name ?: session?.email, onScan = ::scan)
                ConnectionBanner(store)
                UpdateBanner(app.updater)
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.Home -> HomeScreen(store, onOpenSession = { openSession = it }, onPair = ::scan, onApprovals = { tab = Tab.Approvals })
                        Tab.Projects -> ProjectsScreen(store, onOpenSession = { openSession = it })
                        Tab.Approvals -> ApprovalsScreen(store, onOpenSession = { openSession = it })
                        Tab.Devices -> DevicesScreen(store, app.updater, pairStatus, onScan = ::scan, onSignOut = { scope.launch { store.signOut() } })
                    }
                }
            }
        }
    }
}

@Composable
private fun TabTopBar(tab: Tab, store: BambooStore, account: String?, onScan: () -> Unit) {
    val projects by store.projects.collectAsState()
    val sessions by store.sessions.collectAsState()
    val approvals by store.approvals.collectAsState()
    val loaded by store.loaded.collectAsState()
    when (tab) {
        Tab.Home -> {
            val hour = LocalTime.now().hour
            val greeting = when { hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
            ScreenTopBar(
                "BambooKit", greeting + (account?.substringBefore('@')?.let { ", $it" } ?: ""),
                titleLeading = { Image(painterResource(R.drawable.bambookit_mark), null, Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))) },
            )
        }
        Tab.Projects -> ScreenTopBar("Projects", if (loaded) "${plural(projects.size, "project")} · ${plural(sessions.size, "session")}" else "From your PCs")
        Tab.Approvals -> {
            val pending = approvals.count { it.isPending }
            ScreenTopBar("Approvals", if (!loaded) "Permission requests" else if (pending > 0) "$pending waiting for you" else "Nothing waiting")
        }
        Tab.Devices -> ScreenTopBar("Devices", account, actions = { IconButton(onClick = onScan) { Icon(Icons.Filled.QrCodeScanner, "Scan QR code") } })
    }
}
