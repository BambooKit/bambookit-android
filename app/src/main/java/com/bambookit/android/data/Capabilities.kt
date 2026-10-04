package com.bambookit.android.data

/**
 * What a PC's BambooKit Desktop can do, decided before any request is sent. Mirrors bambookit-api
 * lib/compat.ts: desktops that report capabilities are taken at their word; older ones (or any desktop
 * behind a server older than API 1.1.0, which doesn't pass capabilities on) get the capabilities of their
 * app version. Encrypted provider keys additionally need the PC's public key on record.
 */
enum class DesktopFeature(val capability: String, val since: String, val reason: String) {
    Providers("relay.providers", "1.0.3", "Listing AI providers and models from the phone needs the newer desktop."),
    Models("relay.providers", "1.0.3", "Choosing a model from the phone needs the newer desktop."),
    Todos("relay.todos", "1.0.3", "Showing the agent's todo list needs the newer desktop."),
    Approval("relay.approval", "1.0.3", "Showing the full request (command, proposed diff) needs the newer desktop."),
    History("relay.history", "1.0.3", "Session history and before/after views need the newer desktop."),
    CreateSession("create-session", "1.0.3", "Starting a session with a first message from the phone needs the newer desktop."),
    ProviderKeys("provider-keys.encrypted", "1.0.3", "Encrypted provider keys need the newer secure credential protocol."),
    Tree("relay.tree", "1.0.2", "Browsing project files needs the newer desktop."),
    File("relay.file", "1.0.2", "Viewing project files needs the newer desktop."),
}

object DesktopCapabilities {
    private val LEGACY: List<Pair<String, List<String>>> = listOf(
        "0.0.0" to listOf("relay.transcript", "relay.changes", "relay.filemap", "relay.diagram"),
        "1.0.2" to listOf("relay.tree", "relay.file"),
        "1.0.3" to listOf(
            "relay.history", "relay.fileversions", "relay.providers", "relay.todos", "relay.approval", "transcript.full",
            "questions", "continue-on-pc", "rename", "models", "create-session", "provider-keys.encrypted",
        ),
    )

    fun of(device: Device): Set<String> {
        val caps = (device.capabilities?.toMutableSet() ?: LEGACY.filter { AppUpdater.compareVersions(device.appVersion ?: "0", it.first) >= 0 }.flatMap { it.second }.toMutableSet())
        if (device.encryptionKey.isNullOrBlank()) caps.remove("provider-keys.encrypted") else caps.add("provider-keys.encrypted")
        return caps
    }

    /** Null when [device] has [feature] (or isn't a known desktop); else what to tell the user. */
    fun missing(device: Device?, feature: DesktopFeature): DesktopRequirement? {
        if (device == null || device.kind != "desktop") return null
        if (feature.capability in of(device)) return null
        return DesktopRequirement(
            device = device.name,
            currentVersion = device.appVersion,
            requiredVersion = feature.since,
            capability = feature.capability,
            reason = feature.reason,
            desktopProtocol = device.protocol,
            apiProtocol = Diagnostics.apiProtocol,
        )
    }
}
