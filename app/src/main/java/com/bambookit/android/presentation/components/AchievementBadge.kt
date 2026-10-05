package com.bambookit.android.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PestControl
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Textsms
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.AchievementEvent
import com.bambookit.android.data.AchievementIcon
import com.bambookit.android.data.StatsFormat
import com.bambookit.android.presentation.theme.BambooSurface
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.LocalTierPalette
import com.bambookit.android.presentation.theme.NeutralTint
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.Transparent

/** Material Outlined where there is a close match, otherwise a BambooKit line icon ([BkIcons]). */
fun AchievementIcon.vector(): ImageVector = when (this) {
    AchievementIcon.Flame -> Icons.Outlined.LocalFireDepartment
    AchievementIcon.Code -> Icons.Outlined.Code
    AchievementIcon.Pencil -> Icons.Outlined.Edit
    AchievementIcon.Files -> Icons.Outlined.FileCopy
    AchievementIcon.Hammer -> Icons.Outlined.Handyman
    AchievementIcon.Bot -> Icons.Outlined.SmartToy
    AchievementIcon.Prompt -> Icons.Outlined.Textsms
    AchievementIcon.TaskDone -> Icons.Outlined.TaskAlt
    AchievementIcon.Bug -> Icons.Outlined.BugReport
    AchievementIcon.BugHunter -> Icons.Outlined.PestControl
    AchievementIcon.Flask -> Icons.Outlined.Science
    AchievementIcon.BadgeCheck -> Icons.Outlined.Verified
    AchievementIcon.Rocket -> Icons.Outlined.RocketLaunch
    AchievementIcon.CloudUpload -> Icons.Outlined.CloudUpload
    AchievementIcon.Commit -> Icons.Outlined.Commit
    AchievementIcon.Branch -> BkIcons.GitBranch
    AchievementIcon.Merge -> BkIcons.GitMerge
    AchievementIcon.ShieldCheck -> Icons.Outlined.VerifiedUser
    AchievementIcon.Wrench -> Icons.Outlined.Build
    AchievementIcon.AgentBot -> Icons.Outlined.Assistant
    AchievementIcon.Users -> Icons.Outlined.Group
    AchievementIcon.Puzzle -> Icons.Outlined.Extension
    AchievementIcon.Plug -> Icons.Outlined.Power
    AchievementIcon.Terminal -> Icons.Outlined.Terminal
    AchievementIcon.Package -> Icons.Outlined.Inventory2
    AchievementIcon.Hourglass -> Icons.Outlined.HourglassTop
    AchievementIcon.Timer -> Icons.Outlined.Timer
    AchievementIcon.MoonStar -> Icons.Outlined.Bedtime
    AchievementIcon.Zap -> Icons.Outlined.Bolt
    AchievementIcon.Crosshair -> Icons.Outlined.GpsFixed
    AchievementIcon.Trophy -> Icons.Outlined.EmojiEvents
    AchievementIcon.TrendingUp -> Icons.AutoMirrored.Outlined.TrendingUp
    AchievementIcon.Broom -> Icons.Outlined.CleaningServices
    AchievementIcon.Eraser -> BkIcons.Eraser
    AchievementIcon.FilePlus -> Icons.AutoMirrored.Outlined.NoteAdd
    AchievementIcon.FileMinus -> BkIcons.FileMinus
    AchievementIcon.Refresh -> Icons.Outlined.Autorenew
    AchievementIcon.FolderKanban -> BkIcons.FolderKanban
    AchievementIcon.Globe -> Icons.Outlined.Public
    AchievementIcon.Star -> Icons.Outlined.Star
    AchievementIcon.Handshake -> Icons.Outlined.Handshake
    AchievementIcon.PullRequest -> BkIcons.GitPullRequest
    AchievementIcon.Siren -> BkIcons.Siren
    AchievementIcon.Smartphone -> Icons.Outlined.Smartphone
    AchievementIcon.LockKeyhole -> Icons.Outlined.Lock
    AchievementIcon.ScanEye -> BkIcons.ScanEye
    AchievementIcon.BookOpen -> Icons.Outlined.AutoStories
    AchievementIcon.TestTube -> BkIcons.TestTube
    AchievementIcon.Gauge -> Icons.Outlined.Speed
    AchievementIcon.Crown -> BkIcons.Crown
    AchievementIcon.Award -> Icons.Outlined.WorkspacePremium
}

/** Line icons (24 × 24, 2 px round strokes, after Lucide) for achievements Material has no close icon for. */
object BkIcons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M ${cx - r} $cy a $r $r 0 1 0 ${2 * r} 0 a $r $r 0 1 0 ${-2 * r} 0"

    private fun line(name: String, vararg paths: String): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        for (d in paths) addPath(
            pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    val GitBranch: ImageVector by lazy {
        line("BkGitBranch", "M 6 3 L 6 15", circle(18f, 6f, 3f), circle(6f, 18f, 3f), "M 18 9 a 9 9 0 0 1 -9 9")
    }
    val GitMerge: ImageVector by lazy {
        line("BkGitMerge", circle(18f, 18f, 3f), circle(6f, 6f, 3f), "M 6 21 V 9 a 9 9 0 0 0 9 9")
    }
    val GitPullRequest: ImageVector by lazy {
        line("BkGitPullRequest", circle(18f, 18f, 3f), circle(6f, 6f, 3f), "M 13 6 h 3 a 2 2 0 0 1 2 2 v 7", "M 6 9 L 6 21")
    }
    val Eraser: ImageVector by lazy {
        line(
            "BkEraser",
            "M 7 21 l -4.3 -4.3 c -1 -1 -1 -2.5 0 -3.4 l 9.6 -9.6 c 1 -1 2.5 -1 3.4 0 l 5.6 5.6 c 1 1 1 2.5 0 3.4 L 13 21",
            "M 22 21 H 7",
            "M 5 11 l 9 9",
        )
    }
    val FileMinus: ImageVector by lazy {
        line(
            "BkFileMinus",
            "M 15 2 H 6 a 2 2 0 0 0 -2 2 v 16 a 2 2 0 0 0 2 2 h 12 a 2 2 0 0 0 2 -2 V 7 Z",
            "M 14 2 v 4 a 2 2 0 0 0 2 2 h 4",
            "M 9 15 h 6",
        )
    }
    val FolderKanban: ImageVector by lazy {
        line(
            "BkFolderKanban",
            "M 4 20 h 16 a 2 2 0 0 0 2 -2 V 8 a 2 2 0 0 0 -2 -2 h -7.93 a 2 2 0 0 1 -1.66 -0.9 l -0.82 -1.2 " +
                "A 2 2 0 0 0 7.93 3 H 4 a 2 2 0 0 0 -2 2 v 13 c 0 1.1 0.9 2 2 2 Z",
            "M 8 10 v 4",
            "M 12 10 v 2",
            "M 16 10 v 6",
        )
    }
    val Siren: ImageVector by lazy {
        line(
            "BkSiren",
            "M 7 18 v -6 a 5 5 0 1 1 10 0 v 6",
            "M 5 21 a 1 1 0 0 0 1 1 h 12 a 1 1 0 0 0 1 -1 v -1 a 2 2 0 0 0 -2 -2 H 7 a 2 2 0 0 0 -2 2 z",
            "M 21 12 h 1",
            "M 18.5 4.5 L 18 5",
            "M 2 12 h 1",
            "M 12 2 v 1",
            "M 4.929 4.929 l 0.707 0.707",
            "M 12 12 v 6",
        )
    }
    val ScanEye: ImageVector by lazy {
        line(
            "BkScanEye",
            "M 3 7 V 5 a 2 2 0 0 1 2 -2 h 2",
            "M 17 3 h 2 a 2 2 0 0 1 2 2 v 2",
            "M 21 17 v 2 a 2 2 0 0 1 -2 2 h -2",
            "M 7 21 H 5 a 2 2 0 0 1 -2 -2 v -2",
            circle(12f, 12f, 1f),
            "M 18.944 12.33 a 1 1 0 0 0 0 -0.66 a 7.5 7.5 0 0 0 -13.888 0 a 1 1 0 0 0 0 0.66 a 7.5 7.5 0 0 0 13.888 0",
        )
    }
    val TestTube: ImageVector by lazy {
        line(
            "BkTestTube",
            "M 21 7 L 6.82 21.18 a 2.83 2.83 0 0 1 -3.99 -0.01 a 2.83 2.83 0 0 1 0 -4 L 17 3",
            "M 16 2 l 6 6",
            "M 12 16 H 4",
        )
    }
    val Crown: ImageVector by lazy {
        line(
            "BkCrown",
            "M 11.562 3.266 a 0.5 0.5 0 0 1 0.876 0 L 15.39 8.87 a 1 1 0 0 0 1.516 0.294 L 21.183 5.5 " +
                "a 0.5 0.5 0 0 1 0.798 0.519 l -2.834 10.246 a 1 1 0 0 1 -0.956 0.734 H 5.81 a 1 1 0 0 1 -0.957 -0.734 " +
                "L 2.02 6.02 a 0.5 0.5 0 0 1 0.798 -0.519 l 4.276 3.664 a 1 1 0 0 0 1.516 -0.294 z",
            "M 5 21 h 14",
        )
    }
}

enum class BadgeState { Unlocked, Locked, NotTracked }

/**
 * Round achievement badge: [icon] in a ring and soft background tinted by the current [tier], a medal pip at the
 * bottom right; diamond gets a gradient ring and a faint glow. Locked: muted ring and icon at 50 %. Not tracked:
 * dashed ring and a muted icon. Unlocked without a tier (older servers) uses gold without a pip.
 * [cutout] is the colour behind the badge (the pip's outline).
 */
@Composable
fun AchievementBadge(
    icon: ImageVector,
    tier: String?,
    state: BadgeState,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    cutout: Color = BambooSurface,
    contentDescription: String? = null,
) {
    val palette = LocalTierPalette.current
    val unlocked = state == BadgeState.Unlocked
    val accent = if (unlocked) palette.of(tier) ?: palette.gold else palette.locked
    val diamond = unlocked && tier.equals("diamond", ignoreCase = true)
    val ringBrush: Brush = if (diamond) Brush.sweepGradient(listOf(palette.diamond, palette.diamondGlow, palette.platinum, palette.diamond))
    else SolidColor(if (unlocked) accent.copy(alpha = 0.85f) else palette.locked)
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Box(modifier.size(size).alpha(if (state == BadgeState.Locked) 0.5f else 1f).then(semantics)) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 2.dp.toPx()
            val radius = this.size.minDimension / 2f - stroke / 2f
            if (diamond) drawCircle(
                Brush.radialGradient(listOf(palette.diamond.copy(alpha = 0.32f), Transparent), center = center, radius = radius + 5.dp.toPx()),
                radius = radius + 5.dp.toPx(),
            )
            drawCircle(if (unlocked) accent.copy(alpha = 0.15f) else NeutralTint, radius = radius)
            val ringStyle = if (state == BadgeState.NotTracked) {
                Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            } else Stroke(width = stroke)
            drawCircle(ringBrush, radius = radius, style = ringStyle)
        }
        Icon(icon, null, tint = if (unlocked) accent else TextMuted, modifier = Modifier.align(Alignment.Center).size(size * 0.5f))
        if (unlocked && palette.of(tier) != null) {
            val pip = size * 0.32f
            val fill: Brush = if (diamond) Brush.linearGradient(listOf(palette.diamondGlow, palette.diamond), Offset.Zero, Offset.Infinite)
            else SolidColor(accent)
            Box(
                Modifier.align(Alignment.BottomEnd).size(pip).clip(CircleShape).background(cutout).padding(2.dp)
                    .clip(CircleShape).background(fill),
            )
        }
    }
}

/** The badge for an achievement id (unknown ids get the award icon). */
@Composable
fun AchievementBadge(
    id: String?, tier: String?, state: BadgeState, modifier: Modifier = Modifier, size: Dp = 44.dp, cutout: Color = BambooSurface,
    contentDescription: String? = null,
) = AchievementBadge(AchievementIcon.forId(id).vector(), tier, state, modifier, size, cutout, contentDescription)

/** Snackbar content for the achievement.unlocked event (shown with a badge by the app's SnackbarHost). */
class AchievementSnackbarVisuals(val event: AchievementEvent, override val message: String) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Long
}

/** "Achievement unlocked" with the badge: one tier (its icon and medal) or, for many at once, a trophy. */
@Composable
fun AchievementSnackbar(event: AchievementEvent, modifier: Modifier = Modifier) {
    val palette = LocalTierPalette.current
    val summary = event.count != null || event.id == "summary"
    val accent = (if (summary) null else palette.of(event.tier)) ?: palette.gold
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.padding(12.dp).fillMaxWidth().clip(shape).background(BambooSurfaceElevated)
            .border(1.dp, accent.copy(alpha = 0.5f), shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AchievementBadge(
            if (summary) AchievementIcon.Trophy.vector() else AchievementIcon.forId(event.id).vector(),
            if (summary) null else event.tier, BadgeState.Unlocked, size = 40.dp, cutout = BambooSurfaceElevated,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (summary) "Achievements unlocked" else listOfNotNull("Achievement unlocked", event.tier?.let { StatsFormat.tierLabel(it) }).joinToString(" · "),
                color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(StatsFormat.plainTitle(event.title, if (summary) null else event.tier), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
