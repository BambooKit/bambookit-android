package com.bambookit.android.presentation.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bambookit.android.BuildConfig
import com.bambookit.android.ads.AdPlacements
import com.bambookit.android.ads.AdPolicy
import com.bambookit.android.ads.AdsManager
import com.bambookit.android.ads.RewardState
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.BillingProduct
import com.bambookit.android.data.Plan
import com.bambookit.android.data.PlanLimitError
import com.bambookit.android.data.PlanView
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.BambooSurfaceHigh
import com.bambookit.android.presentation.theme.NeutralTint
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusRunningTint
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusSuccessTint
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** The pricing page on the website (Pro is bought there, never in the app), in a Custom Tab or the browser. */
fun openPricing(context: Context, url: String? = null) {
    val target = url?.takeIf { it.startsWith("https://") } ?: BuildConfig.PRICING_URL
    val uri = Uri.parse(target)
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri) }
        .recoverCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** "Payment", "Rewarded ad", "Granted by BambooKit". */
fun planSourceLabel(source: String?): String? = when (source) {
    "payment" -> "Paid on the website"
    "reward" -> "Rewarded ad"
    "admin" -> "Granted by BambooKit"
    else -> null
}

/** "From 4.99 USD / month" for the cheapest product, or null. Amounts are in minor units (cents). */
fun cheapestPrice(products: List<BillingProduct>): String? {
    val p = products.filter { it.amount > 0 }.minByOrNull { it.amount } ?: return null
    val major = p.amount / 100.0
    val amount = if (p.amount % 100 == 0L) "%.0f".format(major) else "%.2f".format(major)
    return "From $amount ${p.currency.uppercase()}" + (p.period?.let { " / $it" } ?: "")
}

/** "Resets at 02:00" (local time) or null. */
fun resetsText(resetsAt: String?): String? = parseInstant(resetsAt)?.let { "Resets at ${shortClock(it)}" }

/** Price shown on the house banner: the cheapest monthly product on the website, or ₹199/mo. */
fun houseBannerPrice(products: List<BillingProduct>): String {
    val p = products.filter { it.amount > 0 && it.period?.startsWith("month", ignoreCase = true) == true }.minByOrNull { it.amount }
        ?: return "₹199/mo"
    val major = p.amount / 100.0
    val amount = if (p.amount % 100 == 0L) "%.0f".format(major) else "%.2f".format(major)
    val symbol = when (p.currency.uppercase()) { "INR" -> "₹"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; else -> p.currency.uppercase() + " " }
    return "$symbol$amount/mo"
}

/** "Go Pro — unlimited phone chat, 5 PCs, no ads · ₹199/mo". */
fun houseBannerText(products: List<BillingProduct>): String = "Go Pro — unlimited phone chat, 5 PCs, no ads · ${houseBannerPrice(products)}"

/** "Daily limit reached — resets at 05:30" (or "resets tomorrow" when the time is unknown). */
fun limitReachedText(resetsAt: String?): String =
    "Daily limit reached — " + (parseInstant(resetsAt)?.let { "resets at ${shortClock(it)}" } ?: "resets tomorrow")

/**
 * What every screen needs to place ads: the ads manager, the plan, and whether a dialog that must stay ad-free
 * (PLAN_LIMIT) is open. Provided once by the app shell; screens call [ScreenAd] / [InlineAd] without plumbing.
 */
data class AdEnv(val ads: AdsManager, val planView: PlanView, val suppressed: Boolean = false)

val LocalAdEnv = compositionLocalOf<AdEnv?> { null }

/**
 * The fixed banner of [screen] when [AdPlacements] puts it at [at] (call it once at the top and once at the
 * bottom of a screen; only the configured one draws). Nothing on Pro, forbidden screens, while locked, while
 * PLAN_LIMIT is open, or when the screen has no content ([hasContent] false: loading, error-only or empty).
 */
@Composable
fun ScreenAd(screen: AdPlacements.Screen, at: AdPlacements.Position, hasContent: Boolean = true) {
    val env = LocalAdEnv.current ?: return
    val pos = AdPlacements.banner(screen, env.planView.plan, hasContent, locked = LocalAppLocked.current, suppressed = env.suppressed)
    if (pos != at) return
    AdBanner(env.ads, env.planView, screen, at)
}

/** Items between inline banners in a list on [screen] (0 = none), at least one screen height apart. */
@Composable
fun inlineAdInterval(screen: AdPlacements.Screen): Int {
    val env = LocalAdEnv.current ?: return 0
    if (LocalAppLocked.current || env.suppressed) return 0
    return AdPlacements.inlineInterval(screen, env.planView.plan, LocalConfiguration.current.screenHeightDp)
}

/** An inline banner between list items (Free plan only), labelled so it can't be mistaken for content. */
@Composable
fun InlineAd(screen: AdPlacements.Screen) {
    val env = LocalAdEnv.current ?: return
    if (!AdPlacements.freePlan(env.planView.plan) || LocalAppLocked.current || env.suppressed) return
    AdBanner(env.ads, env.planView, screen, position = null)
}

/**
 * One banner slot, Free plan only: [position] Bottom (above the bottom navigation, divider above), Top (under the
 * top bar / tabs, with a gap on both sides and a divider below) or null (inline in a list, labelled).
 * An AdMob adaptive banner when one loads; otherwise (no fill, an error, no consent, or nothing within
 * [AdPolicy.HOUSE_FALLBACK_MS]) BambooKit's own "Go Pro" banner of the same height. Pro: nothing at all.
 */
@Composable
fun AdBanner(ads: AdsManager, planView: PlanView, placement: AdPlacements.Screen, position: AdPlacements.Position? = AdPlacements.Position.Bottom) {
    val plan = planView.plan
    val ready by ads.ready.collectAsState()
    if (plan == null || plan.isPro) return
    val context = LocalContext.current
    // AdMob wants the activity, also inside dialogs (file / diff viewer).
    val adContext = remember(context) { context.findActivity() ?: context }
    val inline = position == null
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(AdPolicy.HOUSE_FALLBACK_MS)
        timedOut = true
    }
    val useAdMob = AdPolicy.bannerAllowed(plan, ready, placement)
    val gap = AdPlacements.TOP_GAP_DP.dp
    Column(
        Modifier.fillMaxWidth().background(if (inline) BambooObsidian else BambooSurface)
            .then(if (inline) Modifier.padding(vertical = Space.s) else Modifier),
    ) {
        if (position == AdPlacements.Position.Bottom) HorizontalDivider(color = BambooBorder)
        if (position == AdPlacements.Position.Top) Spacer(Modifier.height(gap))
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val width = maxWidth.value.toInt()
            val adSize = remember(width, inline) {
                if (inline) AdSize.getInlineAdaptiveBannerAdSize(width, INLINE_MAX_DP)
                else AdSize.getLargeAnchoredAdaptiveBannerAdSize(adContext, width)
            }
            var loaded by remember(width, useAdMob) { mutableStateOf(false) }
            var loadedHeight by remember(width, useAdMob) { mutableIntStateOf(0) }
            var failures by remember(width, useAdMob) { mutableIntStateOf(0) }
            val view = remember(width, useAdMob) {
                if (!useAdMob) null else AdView(adContext).apply {
                    setAdSize(adSize)
                    adUnitId = BuildConfig.ADMOB_BANNER
                    val self = this
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            loaded = true
                            loadedHeight = self.adSize?.height ?: 0
                            ads.reportBanner(AdPolicy.SlotStatus(AdPolicy.LoadState.Loaded))
                        }

                        override fun onAdImpression() = ads.reportBanner(AdPolicy.SlotStatus(AdPolicy.LoadState.Shown))

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            android.util.Log.i("BambooAds", "banner not loaded: ${error.code} ${error.message}")
                            if (!loaded) failures++
                            ads.reportBanner(AdPolicy.SlotStatus.failed(error.code))
                        }
                    }
                    ads.reportBanner(AdPolicy.SlotStatus(AdPolicy.LoadState.Loading))
                    loadAd(AdRequest.Builder().build())
                }
            }
            // No fill: try AdMob again now and then while the house banner is shown.
            LaunchedEffect(view, failures) {
                if (view != null && failures in 1..BANNER_RETRIES) {
                    delay(BANNER_RETRY_MS)
                    view.loadAd(AdRequest.Builder().build())
                }
            }
            DisposableEffect(view) { onDispose { view?.destroy() } }
            val slot = AdPolicy.bannerSlot(
                plan, placement, ready, admobLoaded = loaded, admobFailed = failures > 0,
                elapsedMs = if (timedOut) AdPolicy.HOUSE_FALLBACK_MS else 0L,
            )
            val height = when {
                // An inline adaptive banner reports its real height once loaded.
                inline && slot == AdPolicy.BannerSlot.AdMob && loadedHeight > 0 -> loadedHeight
                inline -> INLINE_HOUSE_DP
                else -> adSize.height
            }.coerceAtLeast(50).dp
            Column(Modifier.fillMaxWidth()) {
                if (inline && (slot == AdPolicy.BannerSlot.AdMob || slot == AdPolicy.BannerSlot.House)) Text(
                    if (slot == AdPolicy.BannerSlot.House) "From BambooKit" else "Advertisement",
                    color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp),
                )
                Box(
                    Modifier.fillMaxWidth().height(height).then(if (inline) Modifier.clip(RoundedCornerShape(12.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    // The AdView stays attached while it loads; the house banner covers the slot only while it has no ad.
                    if (view != null) AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth())
                    if (slot == AdPolicy.BannerSlot.House) HouseBanner(planView.products, Modifier.fillMaxSize())
                }
            }
        }
        if (position == AdPlacements.Position.Top) {
            Spacer(Modifier.height(gap))
            HorizontalDivider(color = BambooBorder)
        }
    }
}

/** BambooKit's own banner (no tracking, not an AdMob ad): promotes Pro and opens the pricing page. */
@Composable
fun HouseBanner(products: List<BillingProduct>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier.background(BambooSurface).clickable { openPricing(context) }.padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Filled.WorkspacePremium, tint = StatusSuccess, background = StatusSuccessTint, size = 34.dp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(houseBannerText(products), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("BambooKit Pro · bought on the website", color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.width(Space.s))
        Button(onClick = { openPricing(context) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Go Pro", fontSize = 13.sp) }
    }
}

/** The plan next to the title on Home: "Free" (tap → Profile → Plan) or a small "Pro" badge. */
@Composable
fun PlanChip(plan: Plan?, onClick: () -> Unit) {
    if (plan == null) return
    Box(Modifier.clip(ChipShape).clickable(onClick = onClick)) {
        if (plan.isPro) Chip("Pro", StatusSuccess, StatusSuccessTint, icon = Icons.Filled.WorkspacePremium)
        else Chip("Free", StatusWarning, StatusWarningTint)
    }
}

/** "Watch ad · 24 h Pro" on wide screens, "24 h Pro" (with the video icon) where the top bar is narrow. */
fun rewardChipLabel(hours: Int, screenWidthDp: Int): String = if (screenWidthDp >= 400) "Watch ad · $hours h Pro" else "$hours h Pro"

/**
 * Next to the Free chip on Home: a rewarded ad for 24 h of Pro (Free plan with rewards left today only).
 * Failures and the reward are reported in the snackbar.
 */
@Composable
fun RewardChip(store: BambooStore, ads: AdsManager, plan: Plan?) {
    if (plan == null || !plan.canWatchReward) return
    val context = LocalContext.current
    val state by ads.reward.collectAsState()
    var mine by remember { mutableStateOf(false) }
    val busy = state == RewardState.Loading || state == RewardState.Showing
    LaunchedEffect(state) {
        if (!mine) return@LaunchedEffect
        when (val s = state) {
            is RewardState.Failed -> { store.notify(s.message); ads.clearReward(); mine = false }
            RewardState.Earned -> { store.notify("Thanks! Pro turns on in a few seconds."); ads.clearReward(); mine = false }
            RewardState.Idle -> mine = false
            else -> Unit
        }
    }
    val hours = plan.rewards.hours
    val label = if (busy) "Loading ad…" else rewardChipLabel(hours, LocalConfiguration.current.screenWidthDp)
    Spacer(Modifier.width(6.dp))
    Box(
        Modifier.clip(ChipShape).clickable(enabled = !busy, onClickLabel = "Watch an ad for $hours hours of Pro") {
            mine = true
            watchReward(context, store, ads)
        },
    ) { Chip(label, StatusRunning, StatusRunningTint, icon = Icons.Filled.OndemandVideo) }
}

/** Starts the rewarded ad (24 h of Pro). */
fun watchReward(context: Context, store: BambooStore, ads: AdsManager) {
    val activity = context.findActivity() ?: return
    ads.clearReward()
    ads.watchRewarded(activity, token = { store.rewardToken() }, onEarned = { store.refreshPlanAfterReward() })
}

/** Rewarded-ad failure (with Retry) or "earned" under a "Watch ad" button. */
@Composable
private fun RewardStatus(state: RewardState, onRetry: () -> Unit, onClose: () -> Unit) {
    when (state) {
        is RewardState.Failed -> Column(Modifier.fillMaxWidth().padding(top = Space.s)) {
            Text(state.message, color = StatusFailed, fontSize = 12.sp, lineHeight = 16.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Close") }
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
        RewardState.Earned -> Text("Thanks! Pro turns on in a few seconds.", color = StatusSuccess, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s))
        else -> Unit
    }
}

/**
 * Chat composer on the Free plan with today's messages used up: input locked until the reset, with
 * "Watch ad (24 h Pro)" and "Upgrade".
 */
@Composable
fun ComposerLimitPanel(store: BambooStore, ads: AdsManager, plan: Plan) {
    val context = LocalContext.current
    val state by ads.reward.collectAsState()
    val busy = state == RewardState.Loading || state == RewardState.Showing
    Column(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = StatusWarning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
            Text(limitReachedText(plan.resetsAt), color = StatusWarning, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        }
        Text(
            "You've sent all ${plan.limits.phoneMessagesPerDay ?: 0} free messages from the phone today. You can still chat on the PC.",
            color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = Space.xs),
        )
        RewardStatus(state, onRetry = { watchReward(context, store, ads) }, onClose = { ads.clearReward() })
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth().padding(top = Space.s)) {
            if (plan.canWatchReward) OutlinedButton(onClick = { watchReward(context, store, ads) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.OndemandVideo, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Watch ad (${plan.rewards.hours} h Pro)", fontSize = 13.sp, maxLines = 1)
            }
            Button(onClick = { openPricing(context) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Upgrade", fontSize = 13.sp)
            }
        }
    }
}

/** "N of 20 free messages left today" (warning color at 3 or fewer). Nothing when [text] is null. */
@Composable
fun QuotaLine(text: String?, warn: Boolean, modifier: Modifier = Modifier) {
    if (text == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.WorkspacePremium, null, tint = if (warn) StatusWarning else TextMuted, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = if (warn) StatusWarning else TextMuted, fontSize = 11.sp, fontWeight = if (warn) FontWeight.Medium else FontWeight.Normal)
    }
}

/** What Pro adds; on the Free plan each item has a lock. */
@Composable
private fun ProFeatures(plan: Plan) {
    val free = plan.limits
    val items = listOf(
        "Unlimited phone chat" to free.phoneMessagesPerDay?.let { "Free: $it a day" },
        "Unlimited new sessions from phone" to free.phoneSessionsPerDay?.let { "Free: $it a day" },
        "Up to 5 PCs" to "Free: ${if (free.desktops > 0) free.desktops else 1} PC",
        "No ads" to "Free: with ads",
    )
    Text(if (plan.isPro) "INCLUDED WITH PRO" else "WHAT YOU GET WITH PRO", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    Spacer(Modifier.height(Space.xs))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, freeNote) in items) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (plan.isPro) Icons.Filled.Check else Icons.Filled.Lock, if (plan.isPro) "Included" else "Pro only",
                tint = if (plan.isPro) StatusSuccess else StatusWarning, modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Space.s))
            Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (!plan.isPro && freeNote != null) Text(freeNote, color = TextMuted, fontSize = 11.sp)
        }
    }
}

private const val BANNER_RETRY_MS = 60_000L
private const val BANNER_RETRIES = 5
/** Inline banners in lists: at most this tall (inline adaptive size); the house banner there is this tall. */
private const val INLINE_MAX_DP = 100
private const val INLINE_HOUSE_DP = 64

@Composable
private fun UsageBar(label: String, used: Int, max: Int?) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(if (max == null) "$used · unlimited" else "$used / $max", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        if (max != null && max > 0) {
            val fraction = (used.toFloat() / max).coerceIn(0f, 1f)
            val color = when {
                used >= max -> StatusFailed
                fraction >= 0.8f -> StatusWarning
                else -> StatusRunning
            }
            Spacer(Modifier.height(Space.xs))
            LinearProgressIndicator(
                progress = { fraction }, color = color, trackColor = BambooSurfaceHigh,
                modifier = Modifier.fillMaxWidth().height(6.dp), drawStopIndicator = {},
            )
        }
    }
}

/** Profile → Plan: Free / Pro, Pro until and how it was obtained, today's usage, Upgrade and the rewarded ad. */
@Composable
fun PlanSection(store: BambooStore, ads: AdsManager, view: PlanView) {
    val context = LocalContext.current
    val plan = view.plan
    val privacyRequired by ads.privacyOptionsRequired.collectAsState()
    SectionTitle("Plan")
    when {
        plan == null && view.loading -> BkCard { Text("Loading your plan…", color = TextMuted, fontSize = 13.sp) }
        plan == null -> {
            val err = view.error
            Banner(
                if (err?.serverOutdated == true) "The BambooKit server doesn't offer plans yet." else err?.message ?: "Your plan couldn't be loaded.",
                Icons.Filled.WorkspacePremium, color = StatusWarning, tint = StatusWarningTint, title = "Plan unavailable",
                actionLabel = "Retry", onAction = { store.loadPlan() }, diagnosis = err?.diagnosis,
            )
        }
        else -> BkCard(border = if (plan.isPro) StatusSuccess.copy(alpha = 0.45f) else BambooBorder) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.WorkspacePremium, tint = if (plan.isPro) StatusSuccess else TextSecondary, background = if (plan.isPro) StatusSuccessTint else NeutralTint)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BambooKit", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(Space.s))
                        if (plan.isPro) Chip("Pro", StatusSuccess, StatusSuccessTint) else Chip("Free", TextSecondary, NeutralTint)
                    }
                    if (plan.isPro) {
                        val until = plan.proUntil?.let { dateTime(it) }
                        Text(listOfNotNull(until?.let { "Until $it" }, planSourceLabel(plan.source)).joinToString(" · ").ifBlank { "Pro" }, color = TextSecondary, fontSize = 12.sp)
                    } else Text("Daily limits for chatting from the phone. Ads keep it free.", color = TextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(Space.m))
            Text("TODAY", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(Space.xs))
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                UsageBar("Messages from the phone", plan.usage.phoneMessagesToday, plan.limits.phoneMessagesPerDay)
                UsageBar("New sessions from the phone", plan.usage.phoneSessionsToday, plan.limits.phoneSessionsPerDay)
            }
            resetsText(plan.resetsAt)?.let { Text(it, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = Space.xs)) }
            Spacer(Modifier.height(Space.m))
            ProFeatures(plan)
            if (!plan.isPro) {
                Spacer(Modifier.height(Space.m))
                Button(onClick = { openPricing(context) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s))
                    Text("Upgrade to Pro")
                }
                Text(
                    (cheapestPrice(view.products)?.let { "$it. " } ?: "") + "Pro is bought on the BambooKit website: no limits and no ads.",
                    color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = Space.xs),
                )
            }
        }
    }
    if (plan != null && !plan.isPro && plan.ads) {
        Spacer(Modifier.height(Space.s))
        RewardedAdCard(store, ads, plan)
    }
    // Diagnostics for the owner: what AdMob did, and a manual re-read of the plan.
    val adDiag by ads.diag.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Space.s)) {
        Text(AdPolicy.describe(plan, adDiag), color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { store.loadPlan() }, enabled = !view.loading) {
            if (view.loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Refresh plan", fontSize = 12.sp)
        }
    }
    if (plan != null && !plan.isPro) Text(
        "The Free plan shows ads from Google AdMob on most screens and now and then between screens (at most every 15 minutes, 4 a day). " +
            "Google may use your device's advertising ID; in the EEA and UK you choose this in the consent form. Pro has no ads.",
        color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = Space.s),
    )
    if (privacyRequired) {
        Spacer(Modifier.height(Space.s))
        BkCard(onClick = { context.findActivity()?.let { ads.showPrivacyOptions(it) } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Filled.PrivacyTip)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("Privacy options", color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("Change your ad consent choices", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

/** "Watch an ad for 24 h of Pro": loading, failed and daily-limit states. */
@Composable
fun RewardedAdCard(store: BambooStore, ads: AdsManager, plan: Plan, compact: Boolean = false) {
    val context = LocalContext.current
    val state by ads.reward.collectAsState()
    val hours = plan.rewards.hours
    val left = plan.rewardsLeft
    val busy = state == RewardState.Loading || state == RewardState.Showing
    fun watch() = watchReward(context, store, ads)
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.OndemandVideo, tint = StatusRunning)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("Watch an ad for $hours h of Pro", color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        left <= 0 -> "You've used today's ${plan.rewards.maxPerDay} rewarded ads. Try again tomorrow."
                        else -> "No limits and no ads for $hours hours. $left left today."
                    },
                    color = TextSecondary, fontSize = 12.sp,
                )
            }
        }
        RewardStatus(state, onRetry = { watch() }, onClose = { ads.clearReward() })
        Spacer(Modifier.height(Space.s))
        OutlinedButton(onClick = { watch() }, enabled = left > 0 && !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Space.s))
                Text(if (state == RewardState.Loading) "Loading the ad…" else "Showing the ad…")
            } else Text(if (left > 0) "Watch ad" else "No rewarded ads left today")
        }
    }
    if (compact) Column(Modifier.fillMaxWidth().background(BambooSurface, RoundedCornerShape(12.dp)).padding(Space.m)) { content() }
    else BkCard { content() }
}

/**
 * 402 PLAN_LIMIT on a message or a new session: what was reached, when it resets, and the ways out
 * (a rewarded ad for 24 h of Pro, Upgrade to Pro on the website, or Close). The typed text is kept.
 */
@Composable
fun PlanLimitDialog(store: BambooStore, ads: AdsManager, limit: PlanLimitError, plan: Plan?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BambooSurfaceElevated,
        icon = { Icon(Icons.Filled.WorkspacePremium, null, tint = StatusWarning) },
        title = { Text("Daily free limit reached") },
        text = {
            Column {
                val what = if (limit.isSessions) "new sessions" else "messages"
                val usedMax = if (limit.used != null && limit.max != null) "You've used ${limit.used} of ${limit.max} $what from the phone today." else limit.message
                Text(usedMax, color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp)
                resetsText(limit.resetsAt)?.let { Text("$it.", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = Space.xs)) }
                Text("What you typed is kept, so you can send it once the limit is lifted.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s))
                if (plan != null && plan.canWatchReward) {
                    Spacer(Modifier.height(Space.m))
                    RewardedAdCard(store, ads, plan, compact = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { openPricing(context, limit.upgradeUrl) }) { Text("Upgrade to Pro", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
