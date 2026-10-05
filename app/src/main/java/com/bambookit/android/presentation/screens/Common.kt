package com.bambookit.android.presentation.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooGreenSubtle
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.NeutralTint
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusFailedTint
import com.bambookit.android.presentation.theme.StatusRunning
import com.bambookit.android.presentation.theme.StatusRunningTint
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusSuccessTint
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.StatusWarningTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import java.time.Duration
import java.time.Instant

/** Shared spacing scale. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val screen = 16.dp
}

val CardShape = RoundedCornerShape(14.dp)
val ChipShape = RoundedCornerShape(50)

// ------------------------------------------------------------------ status helpers

fun statusColor(status: String?): Color = when (status) {
    "busy" -> StatusRunning
    "retry" -> StatusWarning
    "error" -> StatusFailed
    else -> TextMuted
}

fun statusTint(status: String?): Color = when (status) {
    "busy" -> StatusRunningTint
    "retry" -> StatusWarningTint
    "error" -> StatusFailedTint
    else -> NeutralTint
}

fun statusLabel(status: String?): String = when (status) {
    "busy" -> "Working"
    "retry" -> "Retrying"
    "error" -> "Error"
    "idle" -> "Idle"
    else -> status?.replaceFirstChar { it.uppercase() } ?: "Unknown"
}

fun relative(iso: String?): String {
    if (iso == null) return ""
    return runCatching {
        val d = Duration.between(Instant.parse(iso), Instant.now())
        when {
            d.seconds < 60 -> "just now"
            d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
            d.toHours() < 24 -> "${d.toHours()}h ago"
            else -> "${d.toDays()}d ago"
        }
    }.getOrDefault("")
}

/** "just now", "5m ago"… for an epoch-millisecond time. */
fun relativeMillis(epochMs: Long): String = relative(Instant.ofEpochMilli(epochMs).toString())

fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

// ------------------------------------------------------------------ primitives

@Composable
fun Dot(color: Color, size: Int = 8, pulse: Boolean = false) {
    val alpha = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha")
        a
    } else 1f
    Box(Modifier.size(size.dp).alpha(alpha).clip(CircleShape).background(color))
}

@Composable
fun Mono(text: String, color: Color = TextSecondary, size: Int = 12, maxLines: Int = 1, modifier: Modifier = Modifier) {
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = size.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Rounded surface with a hairline border. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    container: Color = BambooSurface,
    border: Color = BambooBorder,
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(container)
            .border(1.dp, border, CardShape)
            .let { if (onClick != null || onLongClick != null) it.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick) else it }
            .padding(padding),
        content = content,
    )
}

/** Small pill: optional dot or icon + label. */
@Composable
fun Chip(text: String, color: Color = TextSecondary, tint: Color = NeutralTint, icon: ImageVector? = null, dot: Boolean = false, pulse: Boolean = false) {
    Row(
        Modifier.clip(ChipShape).background(tint).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Dot(color, 6, pulse)
            Spacer(Modifier.width(5.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun StatusChip(status: String?) {
    Chip(statusLabel(status), statusColor(status), statusTint(status), dot = true, pulse = status == "busy" || status == "retry")
}

@Composable
fun OnlineChip(online: Boolean, name: String? = null) {
    val label = (name?.let { "$it · " } ?: "") + if (online) "Online" else "Offline"
    if (online) Chip(label, StatusSuccess, StatusSuccessTint, dot = true)
    else Chip(label, StatusFailed, StatusFailedTint, dot = true)
}

/** Rounded square holding an icon — used as the leading visual of list cards. */
@Composable
fun IconTile(icon: ImageVector, tint: Color = TextPrimary, background: Color = BambooGreenSubtle, size: Dp = 38.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Full-width notice strip (offline PC, not continued, connection lost). */
@Composable
fun Banner(
    text: String,
    icon: ImageVector,
    color: Color = StatusWarning,
    tint: Color = StatusWarningTint,
    title: String? = null,
    actionLabel: String? = null,
    busy: Boolean = false,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Set on error banners: adds the ⓘ button with what happened, why and the technical details. */
    diagnosis: com.bambookit.android.data.Diagnosis? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tint).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            title?.let { Text(it, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Text(text, color = if (title != null) TextSecondary else color, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (diagnosis != null) InfoButton(diagnosis, onAction, tint = color, title = title)
        if (busy) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(Modifier.size(16.dp), color = color, strokeWidth = 2.dp)
        } else if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = color, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ------------------------------------------------------------------ states

@Composable
fun EmptyState(
    title: String,
    body: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            IconTile(icon, tint = TextSecondary, background = BambooSurfaceElevated, size = 52.dp)
            Spacer(Modifier.height(14.dp))
        }
        Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        body?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(28.dp), color = StatusRunning, strokeWidth = 2.5.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorState(
    title: String,
    message: String,
    icon: ImageVector,
    onRetry: (() -> Unit)?,
    retrying: Boolean = false,
    color: Color = StatusFailed,
    modifier: Modifier = Modifier,
    /** What the ⓘ sheet shows; built from [message] when the failure carries no request details. */
    diagnosis: com.bambookit.android.data.Diagnosis? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(icon, tint = color, background = if (color == StatusFailed) StatusFailedTint else StatusWarningTint, size = 52.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry, enabled = !retrying) {
                    if (retrying) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Retrying…")
                    } else Text("Retry")
                }
                Spacer(Modifier.width(4.dp))
            }
            InfoButton(diagnosis ?: com.bambookit.android.data.Diagnosis(message), onRetry, title = title)
        }
    }
}

// ------------------------------------------------------------------ scaffolding

/** Top app bar used by every screen. Insets are handled by the outer Scaffold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleLeading: (@Composable () -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
) {
    TopAppBar(
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BambooObsidian, titleContentColor = TextPrimary, actionIconContentColor = TextPrimary, navigationIconContentColor = TextPrimary),
        navigationIcon = navigationIcon,
        actions = actions,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                titleLeading?.let { it(); Spacer(Modifier.width(10.dp)) }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        titleTrailing?.let { Spacer(Modifier.width(Space.s)); it() }
                    }
                    subtitle?.let { Text(it, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        },
    )
}

/** Pull-to-refresh container for lists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize(), content = content)
}

/** Big number tile for the home overview. */
@Composable
fun StatTile(label: String, value: Int?, icon: ImageVector, modifier: Modifier = Modifier, highlight: Boolean = false, onClick: (() -> Unit)? = null) {
    BkCard(
        modifier,
        onClick = onClick,
        border = if (highlight) StatusWarning.copy(alpha = 0.5f) else BambooBorder,
        padding = PaddingValues(12.dp),
    ) {
        Icon(icon, null, tint = if (highlight) StatusWarning else TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(value?.toString() ?: "—", color = if (highlight) StatusWarning else TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp)
    }
}

@Composable
fun BottomSpacer() = Spacer(Modifier.height(Space.xl))
