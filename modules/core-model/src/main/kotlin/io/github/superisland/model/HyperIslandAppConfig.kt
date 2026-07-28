package io.github.superisland.model

/**
 * Product-owned representation of HyperIsland's per-application configuration shape.
 *
 * An empty [Channels.enabled] list means every Channel is enabled. This is intentionally
 * different from an empty allowlist and mirrors HyperIsland's notification matching semantics.
 */
data class HyperIslandAppConfig(
    val packageName: String,
    val channels: Channels = Channels(),
    val islandPriority: IslandPriority = IslandPriority.LOW,
    val focusDisplayMode: FocusDisplayMode = FocusDisplayMode.ISLAND_AND_FOCUS,
) {
    data class Channels(
        val enabled: List<String> = emptyList(),
        val settings: Map<String, Settings> = emptyMap(),
    ) {
        data class Settings(
            val islandPriority: IslandPriority,
            val focusDisplayMode: FocusDisplayMode,
        )

        fun normalizedOrNull(
            appDefaultPriority: IslandPriority,
            appDefaultDisplayMode: FocusDisplayMode,
        ): Channels? {
            if (enabled.size > ChannelSelection.MAX_CHANNELS_PER_APP) return null
            if (enabled.any { channelId -> !ChannelSelection.isValidChannelId(channelId) }) return null
            if (settings.size > ChannelSelection.MAX_CHANNELS_PER_APP) return null
            val normalizedEnabled = enabled.distinct().sorted()
            val selectedChannelIds = normalizedEnabled.toHashSet()
            if (
                settings.keys.any { channelId ->
                    !ChannelSelection.isValidChannelId(channelId) ||
                        (normalizedEnabled.isNotEmpty() && channelId !in selectedChannelIds)
                }
            ) {
                return null
            }
            val normalizedSettings =
                settings.entries
                    .asSequence()
                    .filter { (_, setting) ->
                        setting.islandPriority != appDefaultPriority ||
                            setting.focusDisplayMode != appDefaultDisplayMode
                    }
                    .sortedBy(Map.Entry<String, Settings>::key)
                    .associateTo(linkedMapOf()) { entry -> entry.key to entry.value }
            return Channels(
                enabled = normalizedEnabled,
                settings = normalizedSettings,
            )
        }

        fun matches(channelId: String): Boolean =
            enabled.isEmpty() || enabled.binarySearch(channelId) >= 0

        fun effectiveIslandPriority(
            channelId: String,
            appDefaultPriority: IslandPriority,
        ): IslandPriority = settings[channelId]?.islandPriority ?: appDefaultPriority
    }

    fun normalizedOrNull(): HyperIslandAppConfig? {
        val normalizedPackage = AppRule.normalizePackageName(packageName) ?: return null
        val normalizedChannels =
            channels.normalizedOrNull(islandPriority, focusDisplayMode) ?: return null
        return HyperIslandAppConfig(
            packageName = normalizedPackage,
            channels = normalizedChannels,
            islandPriority = islandPriority,
            focusDisplayMode = focusDisplayMode,
        )
    }

    fun matches(
        sourcePackage: String,
        channelId: String,
    ): Boolean = packageName == sourcePackage && channels.matches(channelId)

    fun effectiveIslandPriority(channelId: String): IslandPriority =
        channels.effectiveIslandPriority(channelId, islandPriority)

    fun effectiveFocusDisplayMode(channelId: String): FocusDisplayMode =
        channels.settings[channelId]?.focusDisplayMode ?: focusDisplayMode

    fun toAppRule(): AppRule =
        AppRule(
            packageName = packageName,
            channels =
                if (channels.enabled.isEmpty()) {
                    ChannelSelection(
                        allChannels = true,
                        islandPriorityOverrides = channels.priorityOverrides(islandPriority),
                        focusDisplayModeOverrides = channels.displayModeOverrides(focusDisplayMode),
                    )
                } else {
                    ChannelSelection(
                        channelIds = channels.enabled,
                        islandPriorityOverrides = channels.priorityOverrides(islandPriority),
                        focusDisplayModeOverrides = channels.displayModeOverrides(focusDisplayMode),
                    )
                },
            islandPriority = islandPriority,
            focusDisplayMode = focusDisplayMode,
        )

    private fun Channels.priorityOverrides(
        appDefaultPriority: IslandPriority,
    ): Map<String, IslandPriority> =
        settings.mapNotNull { (channelId, setting) ->
            setting.islandPriority
                .takeIf { priority -> priority != appDefaultPriority }
                ?.let { priority -> channelId to priority }
        }.toMap(linkedMapOf())

    private fun Channels.displayModeOverrides(
        appDefaultDisplayMode: FocusDisplayMode,
    ): Map<String, FocusDisplayMode> =
        settings.mapNotNull { (channelId, setting) ->
            setting.focusDisplayMode
                .takeIf { displayMode -> displayMode != appDefaultDisplayMode }
                ?.let { displayMode -> channelId to displayMode }
        }.toMap(linkedMapOf())

    companion object {
        @JvmStatic
        fun from(rule: AppRule): HyperIslandAppConfig? {
            val normalized = rule.normalizedOrNull() ?: return null
            return HyperIslandAppConfig(
                packageName = normalized.packageName,
                channels =
                    Channels(
                        enabled =
                            if (normalized.channels.allChannels) {
                                emptyList()
                            } else {
                                normalized.channels.channelIds
                            },
                        settings =
                            (
                                normalized.channels.islandPriorityOverrides.keys +
                                    normalized.channels.focusDisplayModeOverrides.keys
                            ).sorted().associateWithTo(linkedMapOf()) { channelId ->
                                Channels.Settings(
                                    islandPriority =
                                        normalized.channels.islandPriorityOverrides[channelId]
                                            ?: normalized.islandPriority,
                                    focusDisplayMode =
                                        normalized.channels.focusDisplayModeOverrides[channelId]
                                            ?: normalized.focusDisplayMode,
                                )
                            },
                    ),
                islandPriority = normalized.islandPriority,
                focusDisplayMode = normalized.focusDisplayMode,
            )
        }
    }
}
