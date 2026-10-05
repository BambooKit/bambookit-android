package com.bambookit.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.bambookit.android.presentation.screens.AppLockScreen
import com.bambookit.android.presentation.screens.ProfileButton
import com.bambookit.android.presentation.screens.LocalAppLocked
import com.bambookit.android.presentation.screens.LocalDiagHandlers
import com.bambookit.android.presentation.screens.DiagHandlers
import com.bambookit.android.presentation.screens.ProfileScreen
import com.bambookit.android.presentation.screens.AdBanner
import com.bambookit.android.presentation.screens.PlanChip
import com.bambookit.android.presentation.screens.PlanLimitDialog
import com.bambookit.android.presentation.screens.findActivity
import com.bambookit.android.ads.AdPolicy
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
import com.bambookit.android.data.BambooNotifier
import com.bambookit.android.presentation.screens.NotificationsOffBanner
import com.bambookit.android.presentation.screens.ProfileSetupSheet
import androidx.compose.ui.platform.LocalContext
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

/** A FragmentActivity so Android's BiometricPrompt (App lock) can attach to it. */
class MainActivity : FragmentActivity() {
    private val incoming = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming.value = intent
        val app = application as BambooKitApp
        setContent { BambooKitTheme { AppShell(app, incoming) } }
    }

    override fun onStart() {
        super.onStart()
        (application as BambooKitApp).lock.onForeground()
        // Back from the background: resume realtime and re-read anything that may be stale.
        (application as BambooKitApp).store.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Rotation and other configuration changes are not "leaving the app".
        if (!isChangingConfigurations) (application as BambooKitApp).lock.onBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming.value = intent
    }

    override fun onResume() {
        super.onResume()
        // New releases on GitHub: checked when the app opens, at most every few hours.
        (application as BambooKitApp).updater.onResume()
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

/** The app plus the App lock overlay, which covers everything (and hides it from accessibility) while locked. */
@Composable
private fun AppShell(app: BambooKitApp, incoming: MutableStateFlow<Intent?>) {
    val locked by app.lock.locked.collectAsState()
    val session by app.auth.session.collectAsState()
    var sawSignedOut by rememberSaveable { mutableStateOf(false) }
    val signedIn = session != null
    // Signing in is itself proof of identity: don't ask again right after a sign-in in this run.
    LaunchedEffect(signedIn) {
        if (!signedIn) sawSignedOut = true
        else if (sawSignedOut) {
            app.lock.unlock()
            sawSignedOut = false
        }
    }
    val showLock = locked && signedIn
    CompositionLocalProvider(
        LocalAppLocked provides showLock,
        LocalDiagHandlers provides DiagHandlers(refresh = { app.store.refreshAll() }, reconnect = { app.store.reconnect() }),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (showLock) Modifier.clearAndSetSemantics { } else Modifier)) { Root(app, incoming) }
            if (showLock) AppLockScreen(app.lock)
        }
    }
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
    var showProfile by rememberSaveable { mutableStateOf(false) }
    /** Profile opened from the plan chip: scroll to the plan section. */
    var focusPlan by rememberSaveable { mutableStateOf(false) }
    var focusProject by rememberSaveable { mutableStateOf<String?>(null) }
    val profile by store.profile.collectAsState()
    var pairStatus by remember { mutableStateOf<String?>(null) }
    var pendingPairToken by remember { mutableStateOf<String?>(null) }
    val approvals by store.approvals.collectAsState()
    val planView by store.plan.collectAsState()
    val plan = planView.plan
    val planLimit by store.planLimit.collectAsState()
    val locked = LocalAppLocked.current

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val context = LocalContext.current
    var setupDone by rememberSaveable { mutableStateOf(false) }

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
            // Keeps the connection (and notifications) going after the app is left, when enabled in Settings.
            ConnectionService.sync(context)
        }
    }
    // The one-time "Set up your profile" sheet, for accounts without a name.
    val account = profile.account
    val userId = session?.userId
    val showSetup = !setupDone && userId != null && account != null && account.name.isNullOrBlank() && !app.secure.profileSetupOffered(userId)
    // Ask for notification permission once (Android 13+), after the profile sheet, when the workspace has loaded.
    val loaded by store.loaded.collectAsState()
    LaunchedEffect(loaded, showSetup, userId) {
        if (Build.VERSION.SDK_INT >= 33 && loaded && userId != null && !showSetup && account != null && !app.secure.notificationPromptShown && !app.notifier.canPost()) {
            app.secure.notificationPromptShown = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(intent) {
        val i = intent ?: return@LaunchedEffect
        // Notification taps: open the session (its pending requests are on the Summary), or the Approvals tab.
        i.getStringExtra(BambooNotifier.EXTRA_NOTIFICATION_ID)?.let { store.markRead(it) }
        val sessionId = i.getStringExtra(BambooNotifier.EXTRA_SESSION_ID)
        val approvalId = i.getStringExtra(BambooNotifier.EXTRA_APPROVAL_ID)
        when {
            sessionId != null -> { openSession = sessionId; showProfile = false }
            approvalId != null -> { openSession = null; showProfile = false; tab = Tab.Approvals }
        }
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
    // Free plan: gather ad consent (UMP; the form shows only where required, e.g. EEA/UK) once the plan is known
    // and the app is unlocked. The ads SDK starts only after that. Pro never gets here.
    val adsOn = AdPolicy.adsEnabled(plan)
    LaunchedEffect(adsOn, locked, session?.userId) {
        if (adsOn && !locked && session != null) context.findActivity()?.let { app.ads.gatherConsent(it) }
    }
    // The daily free counters reset at plan.resetsAt: re-read the plan then, so locks lift without a restart.
    LaunchedEffect(plan?.resetsAt, session?.userId) {
        val at = plan?.resetsAtMs() ?: return@LaunchedEffect
        val wait = at - System.currentTimeMillis()
        if (wait > 0) kotlinx.coroutines.delay(wait + 2_000)
        store.loadPlan()
    }
    LaunchedEffect(Unit) { store.messages.collect { snackbar.showSnackbar(it) } }
    // An update really replaced the app: say so once.
    LaunchedEffect(Unit) {
        app.updater.installed.collect { msg -> if (msg != null) snackbar.showSnackbar(msg) }
    }

    if (session == null) {
        LaunchedEffect(Unit) {
            showProfile = false
            // Also when the sign-in expired on its own: stop realtime and clear this phone's signed-in state,
            // so the next sign-in starts cleanly.
            store.signOut(keepPendingSignIn = true)
        }
        LoginScreen(app.auth, onGoogle = {}, googleAvailable = false)
        return
    }
    val accountName = profile.account?.name ?: session?.name
    val accountEmail = profile.account?.email ?: session?.email

    Scaffold(
        containerColor = BambooObsidian,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (openSession == null && !showProfile) Column {
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
            if (showProfile) {
                ProfileScreen(
                    store, app.updater, app.lock, app.ads, focusPlan = focusPlan, onFocused = { focusPlan = false }, onBack = { showProfile = false }, onSignOut = { showProfile = false; scope.launch { store.signOut() } },
                    onOpenProject = { id -> showProfile = false; tab = Tab.Projects; focusProject = id },
                )
            } else if (current != null) {
                SessionScreen(store, current, app.ads, onBack = {
                    openSession = null
                    // A natural break (back to the list): maybe an interstitial, within AdPolicy's limits.
                    context.findActivity()?.let { app.ads.onLeftSession(it, pendingRequests = approvals.count { a -> a.isPending }, locked = locked) }
                })
            } else Column(Modifier.fillMaxSize()) {
                TabTopBar(tab, store, account = accountName ?: accountEmail, onScan = ::scan, planChip = { PlanChip(plan) { focusPlan = true; showProfile = true } }) {
                    ProfileButton(store, profile.account?.avatarUrl, accountName ?: accountEmail) { showProfile = true }
                }
                ConnectionBanner(store)
                if (tab == Tab.Home) NotificationsOffBanner()
                UpdateBanner(app.updater)
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.Home -> HomeScreen(store, onOpenSession = { openSession = it }, onPair = ::scan, onApprovals = { tab = Tab.Approvals })
                        Tab.Projects -> ProjectsScreen(store, onOpenSession = { openSession = it }, focusProjectId = focusProject, onFocused = { focusProject = null })
                        Tab.Approvals -> ApprovalsScreen(store, onOpenSession = { openSession = it })
                        Tab.Devices -> DevicesScreen(store, pairStatus, onScan = ::scan, onProfile = { showProfile = true })
                    }
                }
                // Free plan only: an adaptive banner above the bottom navigation, on Home and Projects only.
                when (tab) {
                    Tab.Home -> AdBanner(app.ads, planView, AdPolicy.Placement.Home)
                    Tab.Projects -> AdBanner(app.ads, planView, AdPolicy.Placement.Projects)
                    else -> Unit
                }
            }
        }
    }
    planLimit?.let { limit ->
        if (!locked) PlanLimitDialog(store, app.ads, limit, plan, onDismiss = { store.dismissPlanLimit(); app.ads.clearReward() })
    }
    if (showSetup && account != null && userId != null) {
        ProfileSetupSheet(store, account, onClose = {
            app.secure.markProfileSetupOffered(userId)
            setupDone = true
        })
    }
}

@Composable
private fun TabTopBar(tab: Tab, store: BambooStore, account: String?, onScan: () -> Unit, planChip: @Composable () -> Unit, profileButton: @Composable () -> Unit) {
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
                titleTrailing = planChip,
                actions = { profileButton() },
            )
        }
        Tab.Projects -> ScreenTopBar(
            "Projects", if (loaded) "${plural(projects.size, "project")} · ${plural(sessions.size, "session")}" else "From your PCs",
            actions = { profileButton() },
        )
        Tab.Approvals -> {
            val pending = approvals.count { it.isPending }
            ScreenTopBar("Approvals", if (!loaded) "Approvals and questions from the agent" else if (pending > 0) "$pending waiting for you" else "Nothing waiting", actions = { profileButton() })
        }
        Tab.Devices -> ScreenTopBar(
            "Devices", account,
            actions = {
                IconButton(onClick = onScan) { Icon(Icons.Filled.QrCodeScanner, "Scan QR code") }
                profileButton()
            },
        )
    }
}
