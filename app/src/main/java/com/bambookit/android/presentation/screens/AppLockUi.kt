package com.bambookit.android.presentation.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.bambookit.android.R
import com.bambookit.android.data.AppLock
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.LockScrim
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import kotlinx.coroutines.flow.first

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Shows Android's biometric prompt with the screen lock (PIN, pattern, password) as fallback.
 * [onError] gets a message for real failures and null when the user simply cancelled.
 */
fun authenticate(activity: FragmentActivity, lock: AppLock, title: String, subtitle: String?, onSuccess: () -> Unit, onError: (String?) -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                lock.authenticating = false
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                lock.authenticating = false
                val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED
                onError(if (cancelled) null else errString.toString())
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .apply { subtitle?.let { setSubtitle(it) } }
        .setAllowedAuthenticators(lock.authenticators)
        .build()
    lock.authenticating = true
    runCatching { prompt.authenticate(info) }.onFailure {
        lock.authenticating = false
        onError(it.message ?: "Could not start the unlock prompt")
    }
}

private const val NO_SCREEN_LOCK = "Set a screen lock (PIN, pattern or password) in your phone's Settings first. App lock uses it, together with your fingerprint or face if you have one."

/** Full-screen BambooKit lock. Nothing behind it is shown or reachable until the user unlocks. */
@Composable
fun AppLockScreen(lock: AppLock) {
    val activity = LocalContext.current.findActivity() as? FragmentActivity
    var message by remember { mutableStateOf<String?>(null) }
    val secure = remember { lock.deviceSecure() }
    fun unlock() {
        val act = activity ?: return
        message = null
        authenticate(act, lock, "Unlock BambooKit", "Use your fingerprint, face or screen lock", onSuccess = { lock.unlock() }, onError = { message = it })
    }
    // Ask once as soon as the screen is in front; after a cancel the user taps Unlock.
    LaunchedEffect(activity, secure) {
        val act = activity ?: return@LaunchedEffect
        if (!secure) return@LaunchedEffect
        act.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        unlock()
    }
    BackHandler { activity?.moveTaskToBack(true) }
    Box(
        Modifier.fillMaxSize().background(LockScrim)
            // Swallow every touch so nothing behind the lock can be used.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 360.dp).padding(horizontal = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.bambookit_mark), null, Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
            Spacer(Modifier.height(Space.l))
            Text("BambooKit", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Locked", color = TextSecondary, fontSize = 14.sp)
            }
            Spacer(Modifier.height(Space.xl))
            if (secure) {
                Button(onClick = ::unlock, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.Fingerprint, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Space.s))
                    Text("Unlock")
                }
            } else {
                Text(
                    "This phone no longer has a screen lock, so App lock can't protect BambooKit. Turn App lock off to continue, or set a screen lock first.",
                    color = StatusWarning, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(Space.m))
                OutlinedButton(onClick = { lock.setEnabled(false) }, modifier = Modifier.fillMaxWidth()) { Text("Turn off App lock") }
            }
            message?.let {
                Spacer(Modifier.height(Space.m))
                Text(it, color = StatusWarning, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        Text(
            "Your sessions stay on your PC", color = TextMuted, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.l),
        )
    }
}

/** Settings row: App lock on/off. Turning it on needs a screen lock and one successful unlock. */
@Composable
fun AppLockSetting(lock: AppLock) {
    val enabled by lock.enabled.collectAsState()
    val activity = LocalContext.current.findActivity() as? FragmentActivity
    var info by remember { mutableStateOf<String?>(null) }
    BkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Lock)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("App lock", color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    "Ask for your fingerprint, face or screen lock when BambooKit opens and after 30 seconds in the background.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                )
            }
            Spacer(Modifier.width(Space.s))
            Switch(
                checked = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BambooObsidian, checkedTrackColor = BambooGreen,
                    uncheckedThumbColor = TextMuted, uncheckedTrackColor = BambooGreenSubtle, uncheckedBorderColor = BambooBorderStrong,
                ),
                onCheckedChange = { on ->
                    info = null
                    when {
                        !on -> lock.setEnabled(false)
                        !lock.deviceSecure() -> info = NO_SCREEN_LOCK
                        activity == null -> info = "App lock is not available here."
                        else -> authenticate(
                            activity, lock, "Turn on App lock", "Confirm it's you",
                            onSuccess = { lock.setEnabled(true); lock.unlock() },
                            onError = { info = it },
                        )
                    }
                },
            )
        }
        info?.let { Text(it, color = StatusWarning, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = Space.s)) }
    }
}
