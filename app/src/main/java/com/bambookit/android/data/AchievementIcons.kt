package com.bambookit.android.data

/**
 * The icon of each achievement badge, from the shared BambooKit mapping (Lucide names in the comments). The
 * presentation layer turns each value into an ImageVector (Material Outlined or a BambooKit vector); this file is
 * plain Kotlin so the mapping is unit tested.
 */
enum class AchievementIcon {
    Flame, Code, Pencil, Files, Hammer, Bot, Prompt, TaskDone, Bug, BugHunter, Flask, BadgeCheck, Rocket, CloudUpload,
    Commit, Branch, Merge, ShieldCheck, Wrench, AgentBot, Users, Puzzle, Plug, Terminal, Package, Hourglass, Timer,
    MoonStar, Zap, Crosshair, Trophy, TrendingUp, Broom, Eraser, FilePlus, FileMinus, Refresh, FolderKanban, Globe,
    Star, Handshake, PullRequest, Siren, Smartphone, LockKeyhole, ScanEye, BookOpen, TestTube, Gauge, Crown,

    /** Unknown ids (older servers' flat achievements, ids added later). */
    Award,
    ;

    companion object {
        /** Achievement id (bambookit-api src/modules/stats.ts) → icon. */
        val BY_ID: Map<String, AchievementIcon> = mapOf(
            "coding-streak" to Flame, // flame
            "code-written" to Code, // code-xml
            "code-changes" to Pencil, // pencil-line
            "files-changed" to Files, // files
            "projects-built" to Hammer, // hammer
            "ai-sessions" to Bot, // bot
            "prompts-sent" to Prompt, // message-square-text
            "tasks-completed" to TaskDone, // circle-check-big
            "bugs-fixed" to Bug, // bug
            "bug-hunter" to BugHunter, // bug-play
            "tests-run" to Flask, // flask-conical
            "tests-passed" to BadgeCheck, // badge-check
            "deployments" to Rocket, // rocket
            "cloud-builder" to CloudUpload, // cloud-upload
            "commits" to Commit, // git-commit-horizontal
            "branches-created" to Branch, // git-branch
            "merges" to Merge, // git-merge
            "approvals" to ShieldCheck, // shield-check
            "tool-calls" to Wrench, // wrench
            "agent-tasks" to AgentBot, // bot-message-square
            "multi-agent" to Users, // users
            "integrations" to Puzzle, // puzzle
            "mcp-tools" to Plug, // plug
            "terminal-commands" to Terminal, // square-terminal
            "packages-installed" to Package, // package
            "long-sessions" to Hourglass, // hourglass
            "coding-time" to Timer, // timer
            "night-coder" to MoonStar, // moon-star
            "fast-fix" to Zap, // zap
            "one-shot-fix" to Crosshair, // crosshair
            "tasks-without-retry" to Trophy, // trophy
            "successful-sessions" to TrendingUp, // trending-up
            "code-cleanup" to Broom, // brush-cleaning
            "code-deleted" to Eraser, // eraser
            "files-created" to FilePlus, // file-plus
            "files-deleted" to FileMinus, // file-minus
            "refactors" to Refresh, // refresh-cw
            "projects-managed" to FolderKanban, // folder-kanban
            "open-source" to Globe, // globe
            "github-stars" to Star, // star
            "contributions" to Handshake, // handshake
            "pull-requests" to PullRequest, // git-pull-request
            "production-fixes" to Siren, // siren
            "devices-connected" to Smartphone, // smartphone
            "secure-actions" to LockKeyhole, // lock-keyhole
            "code-reviews" to ScanEye, // scan-eye
            "documentation" to BookOpen, // book-open
            "experiments" to TestTube, // test-tube-diagonal
            "speed-builder" to Gauge, // gauge
            "bambookit-master" to Crown, // crown
        )

        /** The icon for an achievement id; [Award] for anything unknown. */
        fun forId(id: String?): AchievementIcon = id?.trim()?.lowercase()?.let { BY_ID[it] } ?: Award
    }
}
