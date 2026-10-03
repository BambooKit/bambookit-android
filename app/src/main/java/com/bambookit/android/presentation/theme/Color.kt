package com.bambookit.android.presentation.theme

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
val Transparent = Color.Transparent

/** Chat bubble for messages you sent; assistant replies sit on the plain ground. */
val UserBubble = BambooGreenSubtle
val CodeBlockBackground = Color(0xFF0D0E0C)

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
