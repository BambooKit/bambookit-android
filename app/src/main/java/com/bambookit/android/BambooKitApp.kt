package com.bambookit.android

import android.app.Application
import com.bambookit.android.data.AppLock
import com.bambookit.android.data.AppUpdater
import com.bambookit.android.data.ApiClient
import com.bambookit.android.data.AuthRepository
import com.bambookit.android.data.BambooNotifier
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.BambooClientInterceptor
import com.bambookit.android.data.DiagramCache
import com.bambookit.android.data.RealtimeClient
import com.bambookit.android.data.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
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
    lateinit var secure: SecureStore
        private set
    lateinit var notifier: BambooNotifier
        private set

    override fun onCreate() {
        super.onCreate()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        // Generous timeouts: the hosted API can take 20-50 s to wake from sleep on the free plan.
        // Relay calls (files, diagram) use a longer read timeout in ApiClient.
        val http = OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(BambooClientInterceptor())
            .build()
        secure = SecureStore(this)
        lock = AppLock(this, secure)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        auth = AuthRepository(http, secure, json)
        val api = ApiClient(http, auth, secure, json)
        val realtime = RealtimeClient(http, auth, secure, json, scope)
        notifier = BambooNotifier(this).also { it.createChannels() }
        store = BambooStore(api, auth, realtime, secure, json, scope, DiagramCache(File(cacheDir, "diagrams"), json), notifier::post)
        updater = AppUpdater(this, http, json, scope)
        // Signing out (here or because the sign-in expired) stops the background connection.
        scope.launch {
            auth.session.map { it != null }.distinctUntilChanged().collect { signedIn -> if (!signedIn) ConnectionService.stop(this@BambooKitApp) }
        }
    }
}
