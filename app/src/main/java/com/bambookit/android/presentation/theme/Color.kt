package com.bambookit.android.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// BambooKit brand: #121212 ground, #E5EDD5 bamboo.
val BambooObsidian = Color(0xFF121212)
val BambooSurface = Color(0xFF181A17)
val BambooSurfaceElevated = Color(0xFF20231E)
val BambooSurfaceHigh = Color(0xFF262A23)
val BambooBorder = Color(0xFF2B3028)
val BambooBorderStrong = Color(0xFF3A4135)

val BambooGreen = Color(0xFFE5EDD5)
val BambooGreenSubtle = Color(0xFF2A3322)
val BambooGreenBright = Color(0xFFF2F6EA)

val TextPrimary = Color(0xFFE5EDD5)
val TextSecondary = Color(0xFFA7B09C)
val TextMuted = Color(0xFF6E7767)

val StatusRunning = Color(0xFFB7CF8E)
val StatusSuccess = Color(0xFF9FD39A)
val StatusWarning = Color(0xFFE8C26A)
val StatusFailed = Color(0xFFEF8A80)

// Derived tokens (tinted backgrounds for chips, banners and diff lines).
val StatusRunningTint = StatusRunning.copy(alpha = 0.14f)
val StatusSuccessTint = StatusSuccess.copy(alpha = 0.14f)
val StatusWarningTint = StatusWarning.copy(alpha = 0.13f)
val StatusFailedTint = StatusFailed.copy(alpha = 0.13f)
val NeutralTint = TextMuted.copy(alpha = 0.18f)

val DiffAddedLine = StatusSuccess.copy(alpha = 0.13f)
val DiffRemovedLine = StatusFailed.copy(alpha = 0.13f)

// GitHub-style unified diff (File change screen).
val DiffAddedBg = DiffAddedLine
val DiffAddedText = StatusSuccess
val DiffAddedGutter = StatusSuccess.copy(alpha = 0.22f)
val DiffRemovedBg = DiffRemovedLine
val DiffRemovedText = StatusFailed
val DiffRemovedGutter = StatusFailed.copy(alpha = 0.22f)
val DiffContextText = TextSecondary
val DiffHunk = StatusRunning
val DiffHunkBg = StatusRunning.copy(alpha = 0.10f)

// Change-status badges (M / A / D / R).
val ChangeModified = StatusWarning
val ChangeModifiedTint = StatusWarningTint
val ChangeAdded = StatusSuccess
val ChangeAddedTint = StatusSuccessTint
val ChangeDeleted = StatusFailed
val ChangeDeletedTint = StatusFailedTint
val ChangeRenamed = Color(0xFF93C9C4)
val ChangeRenamedTint = ChangeRenamed.copy(alpha = 0.14f)

// Session timeline rail.
val TimelineRail = BambooBorderStrong

// App lock and profile.
val LockScrim = BambooObsidian
val AvatarRing = BambooBorderStrong
/** Liked (hearted) sessions. */
val LikedHeart = StatusFailed
val DangerTint = StatusFailedTint
val Transparent = Color.Transparent

/** Chat bubble for messages you sent; assistant replies sit on the plain ground. */
val UserBubble = BambooGreenSubtle
val CodeBlockBackground = Color(0xFF0D0E0C)

// Requests from the agent: permission approvals (warning) and questions (teal).
val RequestBorder = StatusWarning.copy(alpha = 0.45f)
val QuestionAccent = ChangeRenamed
val QuestionAccentTint = ChangeRenamedTint
val QuestionBorder = ChangeRenamed.copy(alpha = 0.45f)
val SelectedOption = BambooGreenSubtle

// Session chat composer and "Continue on PC".
val ComposerBackground = BambooSurface
val ContinueCardBorder = StatusRunning.copy(alpha = 0.45f)

// Project diagram and file tree.
val DiagramArrow = TextMuted.copy(alpha = 0.75f)
val DiagramArrowUsed = StatusRunning.copy(alpha = 0.85f)
val DividerSubtle = BambooBorder.copy(alpha = 0.5f)

// Read-only code viewer: syntax highlighting and find-in-file.
val SyntaxPlain = TextPrimary
val SyntaxKeyword = StatusRunning
val SyntaxString = StatusWarning
val SyntaxComment = TextMuted
val SyntaxNumber = Color(0xFFCDA8E6)
val SyntaxType = Color(0xFF93C9C4)
val LineNumber = TextMuted
val SearchMatch = StatusWarning.copy(alpha = 0.28f)
val SearchMatchCurrent = StatusWarning.copy(alpha = 0.62f)

// Profile statistics, achievements and the "Update BambooKit Desktop" card.
val AchievementUnlockedBorder = StatusSuccess.copy(alpha = 0.45f)
// Achievement tiers (badges, medal pips and tier dots). Shared across BambooKit apps; dark is the app's theme,
// the light values are for a light scheme (deeper, so they keep contrast on a light ground).
val TierBronze = Color(0xFFCD7F32)
val TierSilver = Color(0xFFC0C7D0)
val TierGold = Color(0xFFF2C94C)
val TierPlatinum = Color(0xFF7FD1C7)
val TierDiamond = Color(0xFF7AB8FF)
/** Second stop of the diamond ring's gradient. */
val TierDiamondGlow = Color(0xFFC9B8FF)
val TierLocked = BambooBorderStrong
val TierBronzeLight = Color(0xFF9C5B1F)
val TierSilverLight = Color(0xFF6F7884)
val TierGoldLight = Color(0xFFA67C00)
val TierPlatinumLight = Color(0xFF2E8A7E)
val TierDiamondLight = Color(0xFF2F6FC4)
val TierDiamondGlowLight = Color(0xFF7A5CD6)
val TierLockedLight = Color(0xFFB8BEB2)

/** The tier colours of the current theme (read with [LocalTierPalette]). */
@Immutable
data class TierPalette(
    val bronze: Color,
    val silver: Color,
    val gold: Color,
    val platinum: Color,
    val diamond: Color,
    val diamondGlow: Color,
    /** Ring and dots of tiers not reached yet. */
    val locked: Color,
) {
    /** "gold" → gold; null for no tier or an unknown name. */
    fun of(tier: String?): Color? = when (tier?.lowercase()) {
        "bronze" -> bronze
        "silver" -> silver
        "gold" -> gold
        "platinum" -> platinum
        "diamond" -> diamond
        else -> null
    }
}

val TierPaletteDark = TierPalette(TierBronze, TierSilver, TierGold, TierPlatinum, TierDiamond, TierDiamondGlow, TierLocked)
val TierPaletteLight = TierPalette(
    TierBronzeLight, TierSilverLight, TierGoldLight, TierPlatinumLight, TierDiamondLight, TierDiamondGlowLight, TierLockedLight,
)
val LocalTierPalette = staticCompositionLocalOf { TierPaletteDark }
val DesktopUpdateBorder = StatusWarning.copy(alpha = 0.5f)
