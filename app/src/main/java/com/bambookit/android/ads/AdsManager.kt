package com.bambookit.android.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.bambookit.android.BuildConfig
import com.bambookit.android.data.Plan
import com.bambookit.android.data.RewardToken
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The rewarded ad ("Watch an ad for 24 h of Pro"). */
sealed interface RewardState {
    data object Idle : RewardState
    data object Loading : RewardState
    data object Showing : RewardState
    /** The reward was earned; the server grants Pro (AdMob server-side verification) and sends plan.updated. */
    data object Earned : RewardState
    data class Failed(val message: String) : RewardState
}

/**
 * Google Mobile Ads for the Free plan. Nothing here runs unless the plan has ads: consent (UMP) is gathered the
 * first time a Free plan is seen, the SDK starts only after that, and ads load only while [AdPolicy.adsEnabled].
 * Debug builds use Google's test ad units (BuildConfig.ADMOB_*), so real ads are never requested while testing.
 */
class AdsManager(private val app: Application, private val scope: CoroutineScope) {
    private val prefs = app.getSharedPreferences("bambookit_ads", Context.MODE_PRIVATE)
    private val startedAt = SystemClock.elapsedRealtime()
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(app)

    private val _ready = MutableStateFlow(false)
    /** Consent allows ad requests and the SDK has started. */
    val ready: StateFlow<Boolean> = _ready
    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** The consent form must stay reachable (EEA/UK): Profile shows "Privacy options". */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired
    private val _reward = MutableStateFlow<RewardState>(RewardState.Idle)
    val reward: StateFlow<RewardState> = _reward

    private var gathering = false
    private var gathered = false
    private var sdkStarted = false
    private var plan: Plan? = null

    private var interstitial: InterstitialAd? = null
    private var interstitialLoadedAt = 0L
    private var interstitialLoading = false

    /** The latest plan. Leaving the Free plan drops any loaded ad and loads nothing more. */
    fun onPlan(p: Plan?) {
        plan = p
        if (!AdPolicy.adsEnabled(p)) {
            interstitial = null
            if (_reward.value !is RewardState.Showing) _reward.value = RewardState.Idle
        } else if (_ready.value) preloadInterstitial()
    }

    // ------------------------------------------------------------------ consent

    /**
     * Gathers consent once per run: updates the consent information and shows Google's consent form when it is
     * required (EEA/UK). The SDK starts only when consent allows ad requests. Called for the Free plan only.
     */
    fun gatherConsent(activity: Activity) {
        if (gathering || gathered) return
        gathering = true
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                    err?.let { Log.w(TAG, "consent form: ${it.message}") }
                    consentGathered()
                }
            },
            { err ->
                Log.w(TAG, "consent update: ${err.message}")
                consentGathered()
            },
        )
        // Consent given in an earlier run: ads can start while the information is refreshed.
        if (consent.canRequestAds()) startSdk()
    }

    private fun consentGathered() {
        gathering = false
        gathered = true
        _privacyOptionsRequired.value = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (consent.canRequestAds()) startSdk()
    }

    /** Profile → Privacy options: Google's consent form again, to change the choice. */
    fun showPrivacyOptions(activity: Activity, onDone: (String?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { err ->
            if (consent.canRequestAds()) startSdk() else _ready.value = false
            onDone(err?.message)
        }
    }

    private fun startSdk() {
        if (sdkStarted) return
        sdkStarted = true
        scope.launch {
            // Initialisation does disk and network work: off the main thread.
            withContext(Dispatchers.IO) { MobileAds.initialize(app) {} }
            _ready.value = true
            if (AdPolicy.adsEnabled(plan)) preloadInterstitial()
        }
    }

    // ------------------------------------------------------------------ interstitial

    private fun history(): List<Long> = AdPolicy.decode(prefs.getString(KEY_SHOWN, null))

    private fun recordShown(now: Long) {
        prefs.edit().putString(KEY_SHOWN, AdPolicy.encode(AdPolicy.prune(now, history() + now))).apply()
    }

    private fun preloadInterstitial() {
        if (!_ready.value || !AdPolicy.adsEnabled(plan) || interstitialLoading) return
        if (interstitial != null && SystemClock.elapsedRealtime() - interstitialLoadedAt < AD_MAX_AGE_MS) return
        // Nothing to show today: don't load one.
        if (AdPolicy.frequencyBlock(System.currentTimeMillis(), history()) == AdPolicy.Block.DailyCap) return
        interstitialLoading = true
        InterstitialAd.load(app, BuildConfig.ADMOB_INTERSTITIAL, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialLoading = false
                if (!AdPolicy.adsEnabled(plan)) return
                interstitial = ad
                interstitialLoadedAt = SystemClock.elapsedRealtime()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitialLoading = false
                Log.i(TAG, "interstitial not loaded: ${error.code} ${error.message}")
            }
        })
    }

    /**
     * The user left a session back to the list: shows a loaded interstitial when [AdPolicy] allows it
     * (no pending request, not locked, frequency cap). Returns true when one is shown.
     */
    fun onLeftSession(activity: Activity, pendingRequests: Int, locked: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val ctx = AdPolicy.InterstitialContext(
            plan = plan, canRequestAds = _ready.value, pendingRequests = pendingRequests, locked = locked,
            sinceAppStartMs = SystemClock.elapsedRealtime() - startedAt, now = now, shownAt = history(),
        )
        if (!AdPolicy.canShowInterstitial(ctx)) return false
        val ad = interstitial?.takeIf { SystemClock.elapsedRealtime() - interstitialLoadedAt < AD_MAX_AGE_MS }
        interstitial = null
        if (ad == null) {
            preloadInterstitial()
            return false
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = recordShown(System.currentTimeMillis())
            override fun onAdDismissedFullScreenContent() = preloadInterstitial()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = preloadInterstitial()
        }
        ad.show(activity)
        return true
    }

    // ------------------------------------------------------------------ rewarded

    /**
     * Loads and shows a rewarded ad. Right before it is shown a one-time token is read from the API ([token]) and set
     * as the ad's server-side verification options, so the server can grant Pro when AdMob confirms the reward.
     * [onEarned] runs when the user earned the reward.
     */
    fun watchRewarded(activity: Activity, token: suspend () -> RewardToken, onEarned: () -> Unit) {
        val cur = _reward.value
        if (cur == RewardState.Loading || cur == RewardState.Showing) return
        if (!AdPolicy.adsEnabled(plan)) return
        if (!_ready.value) {
            gatherConsent(activity)
            _reward.value = RewardState.Failed(
                if (gathered && !consent.canRequestAds()) "Ads can't be shown with your current privacy choices. Change them in Privacy options."
                else "Ads are still starting. Try again in a moment.",
            )
            return
        }
        _reward.value = RewardState.Loading
        RewardedAd.load(activity, BuildConfig.ADMOB_REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                scope.launch {
                    val t = runCatching { token() }.getOrElse {
                        _reward.value = RewardState.Failed("Couldn't start the ad: ${it.message ?: "BambooKit didn't answer"}")
                        return@launch
                    }
                    if (!AdPolicy.adsEnabled(plan) || activity.isFinishing || activity.isDestroyed) {
                        _reward.value = RewardState.Idle
                        return@launch
                    }
                    ad.setServerSideVerificationOptions(ServerSideVerificationOptions.Builder().setUserId(t.userId).setCustomData(t.customData).build())
                    var earned = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            _reward.value = if (earned) RewardState.Earned else RewardState.Idle
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            _reward.value = RewardState.Failed("The ad couldn't be shown (${error.code}). Try again later.")
                        }
                    }
                    _reward.value = RewardState.Showing
                    ad.show(activity) {
                        earned = true
                        onEarned()
                    }
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                _reward.value = RewardState.Failed(
                    if (error.code == AdRequest.ERROR_CODE_NO_FILL) "No ad is available right now. Try again later."
                    else "The ad couldn't be loaded (${error.code}). Check your connection and try again.",
                )
            }
        })
    }

    fun clearReward() {
        if (_reward.value is RewardState.Failed || _reward.value == RewardState.Earned) _reward.value = RewardState.Idle
    }

    companion object {
        private const val TAG = "BambooAds"
        private const val KEY_SHOWN = "interstitial_shown"
        /** Google drops loaded ads after an hour. */
        private const val AD_MAX_AGE_MS = 55 * 60_000L
    }
}
