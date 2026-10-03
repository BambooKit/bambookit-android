package com.bambookit.android

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bambookit.android.data.AppLock
import com.bambookit.android.data.AppUpdater
import com.bambookit.android.data.ApiClient
import com.bambookit.android.data.AuthRepository
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.NotificationItem
import com.bambookit.android.data.RealtimeClient
import com.bambookit.android.data.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class BambooKitApp : Application() {
    lateinit var store: BambooStore
        private set
    lateinit var auth: AuthRepository
        private set
    lateinit var updater: AppUpdater
        private set
    lateinit var lock: AppLock
        private set

    override fun onCreate() {
        super.onCreate()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        // Generous timeouts: the hosted API can take 20-50 s to wake from sleep on the free plan.
        val http = OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
        val secure = SecureStore(this)
        lock = AppLock(this, secure)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        auth = AuthRepository(http, secure, json)
        val api = ApiClient(http, auth, secure, json)
        val realtime = RealtimeClient(http, auth, secure, json, scope)
        createChannel()
        store = BambooStore(api, auth, realtime, secure, json, scope, ::showNotification)
        updater = AppUpdater(this, http, json, scope)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "Agent activity", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Approvals, finished and failed agent sessions"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Local notification for a realtime BambooKit notification. Payload carries only ids and short text. */
    private fun showNotification(n: NotificationItem) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            n.data["sessionId"]?.let { putExtra("sessionId", it) }
        }
        val pending = PendingIntent.getActivity(this, n.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.bambookit_mark)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(n.id.hashCode(), notification) }
    }

    companion object {
        const val CHANNEL = "bambookit_agents"
    }
}
