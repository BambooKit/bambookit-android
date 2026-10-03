package com.bambookit.android.data

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App lock: when enabled, BambooKit asks for the phone's biometrics or screen lock (PIN, pattern,
 * password) on a cold start and when it returns after more than [GRACE_MS] in the background.
 * Only the on/off preference is stored; Android does the authentication.
 */
class AppLock(private val context: Context, private val secure: SecureStore) {
    private val _enabled = MutableStateFlow(secure.appLockEnabled)
    val enabled: StateFlow<Boolean> = _enabled

    // A cold start begins locked when the lock is on.
    private val _locked = MutableStateFlow(secure.appLockEnabled)
    val locked: StateFlow<Boolean> = _locked

    private var backgroundedAt: Long? = null

    /**
     * True while Android's unlock prompt is up. The screen-lock (PIN) prompt can cover the app; that is
     * not "leaving" it, so it never starts the background timer.
     */
    var authenticating = false

    /** True when the phone has a screen lock (PIN, pattern or password), which App lock requires. */
    fun deviceSecure(): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * Biometrics with the screen lock as fallback. Android 9-10 cannot combine strong biometrics with
     * the screen lock, so the weak class (which still includes fingerprint) is used there.
     */
    val authenticators: Int
        get() = if (Build.VERSION.SDK_INT == 28 || Build.VERSION.SDK_INT == 29) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun setEnabled(on: Boolean) {
        secure.appLockEnabled = on
        _enabled.value = on
        if (!on) _locked.value = false
    }

    fun unlock() {
        _locked.value = false
    }

    fun onBackground() {
        if (authenticating) return
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val at = backgroundedAt ?: return
        backgroundedAt = null
        if (_enabled.value && SystemClock.elapsedRealtime() - at > GRACE_MS) _locked.value = true
    }

    companion object {
        const val GRACE_MS = 30_000L
    }
}
