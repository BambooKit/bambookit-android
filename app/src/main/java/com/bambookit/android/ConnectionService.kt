package com.bambookit.android

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bambookit.android.data.BambooNotifier
import com.bambookit.android.data.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps BambooKit's realtime connection open while the app is in the background or closed, so the phone
 * is notified when the agent asks something (approval or question) or a session finishes or fails.
 * There is no FCM push, so this is the only way such events reach a phone that is not in use.
 *
 * It does not open a stream of its own: it starts the app's single [com.bambookit.android.data.RealtimeClient]
 * (through BambooStore.start(), which is a no-op when the UI already started it) and keeps the process
 * alive with a silent ongoing notification.
 *
 * Foreground service type: `remoteMessaging` (Android 14+, permission FOREGROUND_SERVICE_REMOTE_MESSAGING).
 * The service relays messages from another device (the agent on the user's PC: its questions, approval
 * requests and session results) to this phone, which is what remoteMessaging is defined for ("continue
 * messaging tasks from one device on another"). It also has no daily time limit (unlike `dataSync`, which
 * Android 15 caps at 6 hours a day and does not allow to start from BOOT_COMPLETED), and needs no special
 * Play policy declaration (unlike `specialUse`).
 */
class ConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as BambooKitApp
        if (!startInForeground(app.notifier.connectionNotification(connected = app.store.link.value == LinkState.Connected))) {
            stopSelf()
            return
        }
        // Keep the ongoing notification's text in step with the connection.
        scope.launch {
            app.store.link.map { it == LinkState.Connected }.distinctUntilChanged().collect { connected ->
                if (app.notifier.canPost()) {
                    runCatching { NotificationManagerCompat.from(this@ConnectionService).notify(BambooNotifier.CONNECTION_NOTIFICATION_ID, app.notifier.connectionNotification(connected)) }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as BambooKitApp
        if (app.auth.session.value == null || !app.secure.backgroundNotify) {
            stopSelf()
            return START_NOT_STICKY
        }
        app.store.start()
        return START_STICKY
    }

    private fun startInForeground(notification: android.app.Notification): Boolean = try {
        ServiceCompat.startForeground(
            this, BambooNotifier.CONNECTION_NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0,
        )
        true
    } catch (e: Exception) {
        // E.g. ForegroundServiceStartNotAllowedException when started from the background without an exemption.
        Log.w(TAG, "could not start in foreground: ${e.message}")
        false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BambooConnection"

        /** Starts the service when signed in and "Notify me in the background" is on; stops it otherwise. */
        fun sync(context: Context) {
            val app = context.applicationContext as BambooKitApp
            val wanted = app.auth.session.value != null && app.secure.backgroundNotify
            val intent = Intent(context, ConnectionService::class.java)
            if (!wanted) {
                context.stopService(intent)
                return
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Not allowed from the background on Android 12+ (outside exemptions such as boot); the next app
                // start retries.
                Log.w(TAG, "could not start: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }
}

/** Restarts the background connection after the phone boots or the app is updated, when signed in. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ConnectionService.sync(context)
        }
    }
}
