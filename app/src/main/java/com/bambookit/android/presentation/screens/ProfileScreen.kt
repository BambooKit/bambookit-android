package com.bambookit.android.presentation.screens

import com.bambookit.android.ads.AdPlacements
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.ads.AdsManager
import com.bambookit.android.data.Account
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.AppLock
import com.bambookit.android.data.AppUpdater
import com.bambookit.android.presentation.theme.AvatarRing
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.DangerTint
import com.bambookit.android.presentation.theme.LockScrim
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val AVATAR_MAX_PX = 512
private const val AVATAR_MAX_BYTES = 2 * 1024 * 1024
const val DELETE_CONFIRMATION = "DELETE MY ACCOUNT"

/** Round profile photo (downloaded without auth, cached per URL) with an initial or icon as fallback. */
@Composable
fun Avatar(store: BambooStore, url: String?, name: String?, size: Dp) {
    val image by produceState<ImageBitmap?>(null, url) { value = url?.let { store.image(it)?.asImageBitmap() } }
    Box(
        Modifier.size(size).clip(CircleShape).background(BambooGreenSubtle).border(1.dp, AvatarRing, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        val initial = name?.trim()?.firstOrNull()?.uppercaseChar()
        when {
            img != null -> Image(img, "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            initial != null -> Text(initial.toString(), color = TextPrimary, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold)
            else -> Icon(Icons.Filled.AccountCircle, null, tint = TextSecondary, modifier = Modifier.size(size * 0.7f))
        }
    }
}

/**
 * Picked image → upright, at most 512 px on the long side, JPEG ~85 %, guaranteed at most 2 MB.
 * Null when the image can't be read.
 */
internal fun prepareAvatar(context: Context, uri: Uri): ByteArray? = runCatching { encodeAvatar(context, uri) }.getOrNull()

private fun encodeAvatar(context: Context, uri: Uri): ByteArray? {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_MAX_PX) sample *= 2
    val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val rotation = runCatching {
        cr.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)
    val scale = AVATAR_MAX_PX.toFloat() / max(decoded.width, decoded.height)
    val matrix = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        if (rotation != 0f) postRotate(rotation)
    }
    val upright = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    var quality = 85
    var bytes: ByteArray
    do {
        bytes = ByteArrayOutputStream().use { out -> upright.compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray() }
        quality -= 15
    } while (bytes.size > AVATAR_MAX_BYTES && quality >= 25)
    return if (bytes.size > AVATAR_MAX_BYTES) null else bytes
}

private fun providerLabel(provider: String?): String = when (provider) {
    "email" -> "Email & password"
    "google" -> "Google (one-tap)"
    null -> "Unknown"
    else -> "Other"
}

private const val PLAN_ITEM_KEY = "plan"

/** Sections of the Profile screen, top to bottom. */
enum class ProfileSection { Header, Achievements, Statistics, Plan, Notifications, AppLock, AppUpdates, AiProviders, DeveloperOptions, Account, SignOut, DangerZone, ProjectsManaged }

/**
 * Profile header (photo, nickname, email) first, then achievements right under it (the owner wants them "from up
 * side"), then the statistics, the plan (Free / Pro), then the settings and account actions. Projects managed
 * (counts and the project list) are at the very bottom.
 */
val PROFILE_SECTIONS: List<ProfileSection> = listOf(
    ProfileSection.Header,
    ProfileSection.Achievements,
    ProfileSection.Statistics,
    ProfileSection.Plan,
    ProfileSection.Notifications,
    ProfileSection.AppLock,
    ProfileSection.AppUpdates,
    ProfileSection.AiProviders,
    ProfileSection.DeveloperOptions,
    ProfileSection.Account,
    ProfileSection.SignOut,
    ProfileSection.DangerZone,
    ProfileSection.ProjectsManaged,
)

/** The signed-in user's profile: photo, statistics, settings, account actions and projects managed. */
@Composable
fun ProfileScreen(
    store: BambooStore, updater: AppUpdater, appLock: AppLock, ads: AdsManager,
    focusPlan: Boolean = false, onFocused: () -> Unit = {}, onBack: () -> Unit, onSignOut: () -> Unit, onOpenProject: (String) -> Unit = {}) {
    val profile by store.profile.collectAsState()
    val stats by store.stats.collectAsState()
    val planView by store.plan.collectAsState()
    val projects by store.projects.collectAsState()
    var showUpdates by remember { mutableStateOf(false) }
    var devScreen by remember { mutableStateOf<DevScreen?>(null) }
    var achievementFilter by rememberSaveable { mutableStateOf(com.bambookit.android.data.StatsFormat.Filter.All) }
    val session by store.session.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmSignOut by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var providersFor by remember { mutableStateOf<String?>(null) }
    val account: Account? = profile.account
    LaunchedEffect(Unit) {
        store.loadProfile()
        store.loadStats()
        store.loadPlan()
    }
    BackHandler(onBack = onBack)
    if (showUpdates) {
        UpdateScreen(updater, onBack = { showUpdates = false })
        return
    }
    when (devScreen) {
        DevScreen.Power -> { PowerScreen(store, onBack = { devScreen = null }); return }
        DevScreen.Terminal -> { TerminalScreen(store, onBack = { devScreen = null }); return }
        DevScreen.Research -> { ResearchScreen(store, onBack = { devScreen = null }); return }
        null -> Unit
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        preparing = true
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { prepareAvatar(context, uri) }
            preparing = false
            if (bytes == null) Toast.makeText(context, "That image can't be used. Pick a JPEG, PNG or WebP photo.", Toast.LENGTH_LONG).show()
            else store.uploadAvatar(bytes)
        }
    }

    providersFor?.let { id -> ProvidersScreen(store, id, onClose = { providersFor = null }) }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            "Profile", account?.email ?: session?.email,
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        RefreshBox(refreshing = profile.loading && account != null, onRefresh = { store.loadProfile(); store.loadStats(); store.loadPlan() }, modifier = Modifier.weight(1f)) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            // Opened from the plan chip on Home: scroll down until the plan section is laid out, then to it.
            LaunchedEffect(focusPlan) {
                if (!focusPlan) return@LaunchedEffect
                repeat(30) {
                    val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == PLAN_ITEM_KEY }
                    if (hit != null) {
                        listState.animateScrollToItem(hit.index)
                        onFocused()
                        return@LaunchedEffect
                    }
                    if (listState.layoutInfo.totalItemsCount > 0) listState.scrollBy(800f)
                    kotlinx.coroutines.delay(50)
                }
                onFocused()
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = Space.screen)) {
                // Order: who you are, what you did, settings, account actions; projects managed last.
                for (section in PROFILE_SECTIONS) when (section) {
                    ProfileSection.Header -> {
                        item {
                            Column(Modifier.fillMaxWidth().padding(top = Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(contentAlignment = Alignment.Center) {
                                    Avatar(store, account?.avatarUrl, account?.name ?: account?.email ?: session?.email, 104.dp)
                                    if (profile.photoBusy || preparing) Box(Modifier.size(104.dp).clip(CircleShape).background(LockScrim.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                    }
                                }
                                Spacer(Modifier.height(Space.m))
                                Text(account?.name ?: session?.name ?: "BambooKit user", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                (account?.email ?: session?.email)?.let { Text(it, color = TextSecondary, fontSize = 13.sp) }
                                dateOnly(stats.stats?.memberSince ?: account?.createdAt)?.let { Text("Member since $it", color = TextMuted, fontSize = 12.sp) }
                                Spacer(Modifier.height(Space.m))
                                val canUpload = account?.cloudStorage == true
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                    Button(
                                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        enabled = canUpload && !profile.photoBusy && !preparing,
                                    ) {
                                        Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(Space.s))
                                        Text("Change photo")
                                    }
                                    if (account?.avatarStored == true) OutlinedButton(onClick = { store.removeAvatar() }, enabled = !profile.photoBusy) { Text("Remove photo") }
                                }
                                val photoErr = profile.photoError
                                if (account != null && !canUpload && photoErr == null) Banner(
                                    "Cloud storage isn't set up on the BambooKit server, so profile photos can't be saved yet. " +
                                        "The server owner has to add the storage settings (R2_ENDPOINT or CLOUDFLARE_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET_NAME).",
                                    Icons.Filled.PhotoCamera, color = StatusWarning, tint = StatusWarningTint, title = "Profile photos are off on this server",
                                    modifier = Modifier.padding(top = Space.s),
                                    diagnosis = com.bambookit.android.data.Diagnosis("The server reports cloudStorage = false", "STORAGE_NOT_CONFIGURED", 503, "GET", "/v1/me"),
                                )
                                if (photoErr != null) Banner(
                                    if (photoErr.code == "STORAGE_NOT_CONFIGURED") {
                                        "Cloud storage isn't set up on the BambooKit server. " + (photoErr.details["missing"]?.let { "Missing server setting: $it." }
                                            ?: "The server didn't say which setting is missing; it needs R2_ENDPOINT (or CLOUDFLARE_ACCOUNT_ID), R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY and R2_BUCKET_NAME.")
                                    } else photoErr.message,
                                    Icons.Filled.PhotoCamera, color = StatusFailed, tint = DangerTint, title = "Couldn't update the photo",
                                    modifier = Modifier.padding(top = Space.s), diagnosis = photoErr,
                                )
                            }
                        }
                        when {
                            account == null && profile.loading -> item { LoadingState("Loading your profile…") }
                            account == null && profile.error != null -> item {
                                Banner(profile.error ?: "", Icons.Filled.AccountCircle, color = StatusFailed, tint = DangerTint, title = "Couldn't load your profile", actionLabel = "Retry", onAction = { store.loadProfile() }, modifier = Modifier.padding(top = Space.l), diagnosis = profile.errorDiagnosis)
                            }
                        }
                        if (account != null) item {
                            SectionTitle("Nickname")
                            BkCard { NicknameEditor(store, account) }
                        }
                    }
                    ProfileSection.Achievements -> {
                        profileAchievements(stats, store, achievementFilter) { achievementFilter = it }
                    }
                    ProfileSection.Statistics -> {
                        profileStats(stats, store)
                    }
                    ProfileSection.Plan -> {
                        item(key = PLAN_ITEM_KEY) { PlanSection(store, ads, planView) }
                    }
                    ProfileSection.Notifications -> {
                        // Settings live here only (not on the Devices tab): notifications, App lock and updates.
                        item {
                            SectionTitle("Notifications")
                            NotificationSettings()
                        }
                    }
                    ProfileSection.AppLock -> {
                        item {
                            SectionTitle("App lock")
                            AppLockSetting(appLock)
                        }
                    }
                    ProfileSection.AppUpdates -> {
                        item {
                            SectionTitle("App updates")
                            UpdateCard(updater, onOpen = { showUpdates = true })
                        }
                    }
                    ProfileSection.AiProviders -> {
                        item {
                            SectionTitle("AI providers")
                            AiProvidersSection(store, onOpen = { providersFor = it })
                        }
                    }
                    ProfileSection.DeveloperOptions -> {
                        item {
                            SectionTitle("Developer options")
                            DeveloperOptionsSection(
                                store,
                                onPower = { devScreen = DevScreen.Power },
                                onTerminal = { devScreen = DevScreen.Terminal },
                                onResearch = { devScreen = DevScreen.Research },
                            )
                        }
                    }
                    ProfileSection.Account -> {
                        if (account != null) item {
                            SectionTitle("Account")
                            BkCard {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Fact("Sign-in method", providerLabel(account.provider))
                                    when (account.emailVerified) {
                                        true -> Fact("Email", "Email verified", StatusSuccess)
                                        false -> Fact("Email", "Email verification required", StatusWarning)
                                        null -> Fact("Email", "Unknown", TextMuted)
                                    }
                                    dateOnly(account.createdAt)?.let { Fact("Member since", it) }
                                    account.lastActiveAt?.let { a -> (relative(a).ifBlank { null } ?: dateTime(a))?.let { Fact("Last active", it) } }
                                    account.devices?.let { Fact("Devices", it.toString()) }
                                    account.projects?.let { Fact("Projects", it.toString()) }
                                }
                            }
                        }
                    }
                    ProfileSection.SignOut -> {
                        item {
                            Spacer(Modifier.height(Space.l))
                            OutlinedButton(
                                onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusFailed),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Space.s))
                                Text("Sign out")
                            }
                            Text(
                                "Signing out only removes your sign-in from this phone. Nothing is deleted.",
                                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = Space.xs),
                            )
                        }
                    }
                    ProfileSection.DangerZone -> {
                        if (account != null) item {
                            SectionTitle("Danger zone")
                            BkCard(border = StatusFailed.copy(alpha = 0.45f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconTile(Icons.Filled.DeleteForever, tint = StatusFailed, background = DangerTint)
                                    Spacer(Modifier.width(Space.m))
                                    Text("Delete account", color = StatusFailed, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(Space.s))
                                Text(
                                    "Permanently deletes your BambooKit account, devices and pairings, approvals, the session index, shared links, " +
                                        "saved session copies and your profile photo. Files and sessions on your PCs are not touched.",
                                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                                )
                                Spacer(Modifier.height(Space.m))
                                if (account.accountDeletion) {
                                    Button(
                                        onClick = { deleting = true }, modifier = Modifier.fillMaxWidth(), enabled = !profile.deleting,
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusFailed, contentColor = LockScrim),
                                    ) { Text("Delete account…") }
                                } else {
                                    Text(
                                        "Account deletion isn't set up on the server yet. Ask the BambooKit administrator, or try again later.",
                                        color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp,
                                    )
                                }
                            }
                        }
                    }
                    ProfileSection.ProjectsManaged -> {
                        profileProjects(stats, projects, store, onOpenProject)
                    }
                }
                item { BottomSpacer() }
            }
        }
        // Free plan: a banner at the bottom, under the scrolling profile (no buttons fixed there).
        ScreenAd(AdPlacements.Screen.Profile, AdPlacements.Position.Bottom, hasContent = account != null)
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
    if (deleting) DeleteAccountDialog(store, busy = profile.deleting, onDismiss = { deleting = false })
}

@Composable
private fun Fact(label: String, value: String, color: androidx.compose.ui.graphics.Color = TextPrimary) {
    Row {
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(112.dp))
        Text(value, color = color, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DeleteAccountDialog(store: BambooStore, busy: Boolean, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = BambooSurfaceElevated,
        icon = { Icon(Icons.Filled.DeleteForever, null, tint = StatusFailed) },
        title = { Text("Delete your account?") },
        text = {
            Column {
                Text(
                    "This can't be undone. Your account, devices, pairings, approvals, session index, shared links, saved session copies and profile photo are deleted. Files on your PCs are not touched.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(Space.m))
                Text("Type $DELETE_CONFIRMATION to confirm", color = TextPrimary, fontSize = 13.sp)
                Spacer(Modifier.height(Space.xs))
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it; error = null }, singleLine = true, enabled = !busy,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = TextPrimary),
                    placeholder = { Text(DELETE_CONFIRMATION, fontFamily = FontFamily.Monospace, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = StatusFailed, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { store.deleteAccount { error = it } },
                enabled = typed.trim() == DELETE_CONFIRMATION && !busy,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = StatusFailed)
                else Text("Delete account", color = if (typed.trim() == DELETE_CONFIRMATION) StatusFailed else TextMuted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
