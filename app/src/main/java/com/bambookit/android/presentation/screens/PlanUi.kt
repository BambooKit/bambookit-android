package com.bambookit.android.presentation.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
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
import com.bambookit.android.ads.AdPolicy
import com.bambookit.android.ads.AdsManager
import com.bambookit.android.ads.RewardState
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.BillingProduct
import com.bambookit.android.data.Plan
import com.bambookit.android.data.PlanLimitError
import com.bambookit.android.data.PlanView
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.BambooSurfaceHigh
import com.bambookit.android.presentation.theme.NeutralTint
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusRunning
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

/**
 * Adaptive banner at the bottom of Home and Projects (above the bottom navigation). Only on the Free plan
 * (plan.ads) and after consent; on Pro nothing is created or loaded.
 */
@Composable
fun AdBanner(ads: AdsManager, plan: Plan?, placement: AdPolicy.Placement) {
    val ready by ads.ready.collectAsState()
    if (!AdPolicy.bannerAllowed(plan, ready, placement)) return
    Column(Modifier.fillMaxWidth().background(BambooSurface)) {
        HorizontalDivider(color = BambooBorder)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val width = maxWidth.value.toInt()
            val context = LocalContext.current
            val view = remember(width) {
                AdView(context).apply {
                    setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, width))
                    adUnitId = BuildConfig.ADMOB_BANNER
                    loadAd(AdRequest.Builder().build())
                }
            }
            DisposableEffect(view) { onDispose { view.destroy() } }
            AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth())
        }
    }
}

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
    if (plan != null && !plan.isPro) Text(
        "The Free plan shows ads from Google AdMob on Home and Projects and occasionally between sessions. " +
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
    fun watch() {
        val activity = context.findActivity() ?: return
        ads.watchRewarded(activity, token = { store.rewardToken() }, onEarned = { store.refreshPlanAfterReward() })
    }
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
        when (val st = state) {
            is RewardState.Failed -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.s)) {
                Text(st.message, color = StatusFailed, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { ads.clearReward() }) { Text("Close") }
            }
            RewardState.Earned -> Text(
                "Thanks! Pro turns on in a few seconds.", color = StatusSuccess, fontSize = 12.sp, modifier = Modifier.padding(top = Space.s),
            )
            else -> Unit
        }
        Spacer(Modifier.height(Space.s))
        OutlinedButton(onClick = { ads.clearReward(); watch() }, enabled = left > 0 && !busy, modifier = Modifier.fillMaxWidth()) {
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
