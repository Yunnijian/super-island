package io.github.superisland.source.lyric

/**
 * HyperLyric media-card preference contract.
 *
 * The values intentionally mirror the fixed HyperLyric release used by :hyperlyric-port. This
 * file is only the host-side data contract; the media-card runtime remains owned by the imported
 * upstream implementation.
 */
object MediaCardConstants {
    const val CARD_SWITCHER_SINGLE = 0
    const val CARD_SWITCHER_MULTI = 1
    const val CARD_SWITCHER_MIN_COUNT = 2
    const val CARD_SWITCHER_MAX_COUNT = 6

    // These two upstream cards intentionally use different wire values for their default and
    // disabled ambient-flow modes. Keep them separate so host configuration never translates
    // between the two contracts.
    const val NOTIFICATION_AMBIENT_DISABLED = 0
    const val NOTIFICATION_AMBIENT_DYNAMIC = 1
    const val NOTIFICATION_AMBIENT_COVER_COLOR = 2
    const val NOTIFICATION_AMBIENT_CUSTOM_FULL = 3

    const val ISLAND_EXPANDED_AMBIENT_DYNAMIC = 0
    const val ISLAND_EXPANDED_AMBIENT_DISABLED = 1
    const val ISLAND_EXPANDED_AMBIENT_COVER_COLOR = 2
    const val ISLAND_EXPANDED_AMBIENT_CUSTOM_FULL = 3

    const val THEME_FOLLOW_SYSTEM = 0
    const val THEME_ALWAYS_LIGHT = 1
    const val THEME_ALWAYS_DARK = 2

    const val LAYOUT_SYSTEM = 0
    const val LAYOUT_IOS = 1
    const val LAYOUT_COLOROS = 2
    const val LAYOUT_ONE_UI = 3
    const val LAYOUT_MIUI = 4
    const val LAYOUT_PIXEL = 5

    const val COVER_DEFAULT = 0
    const val COVER_CIRCLE = 1
    const val COVER_ROTATING_CIRCLE = 2
    const val COVER_HIDDEN = 3

    const val PROGRESS_DEFAULT = 0
    const val PROGRESS_WAVE = 1
    const val THUMB_DEFAULT = 0
    const val THUMB_VERTICAL = 1
    const val THUMB_HIDDEN = 2

    const val ACTION_ORDER_DEFAULT = 0
    const val ACTION_ORDER_CUSTOM_RIGHT = 1
    const val ACTION_ORDER_PLAY_LEFT = 2

    const val BACKGROUND_DEFAULT = 0
    const val BACKGROUND_COVER_ART = 1
    const val BACKGROUND_BLURRED_COVER = 2
    const val BACKGROUND_RADIAL_GRADIENT = 3
    const val BACKGROUND_LINEAR_GRADIENT = 4
    const val BACKGROUND_SOFT_COVER = 5

    const val SOFT_COVER_LIGHT = 0
    const val SOFT_COVER_DARK = 1
    const val SOFT_COVER_FOLLOW_SYSTEM = 2

}

data class MediaCardConfig(
    /** HyperLyric's media-island mini-window whitelist switch. */
    val removeIslandWhitelist: Boolean = false,
    val notification: NotificationMediaCardConfig = NotificationMediaCardConfig.defaults(),
    val islandExpanded: IslandExpandedMediaCardConfig = IslandExpandedMediaCardConfig.defaults(),
    val alwaysOnDisplay: AlwaysOnDisplayMediaCardConfig = AlwaysOnDisplayMediaCardConfig.defaults(),
) {
    fun normalized(): MediaCardConfig = copy(
        notification = notification.normalized(),
        islandExpanded = islandExpanded.normalized(),
    )
}

data class AlwaysOnDisplayMediaCardConfig(
    val disableMediaCardCollapsing: Boolean = false,
) {
    companion object {
        fun defaults() = AlwaysOnDisplayMediaCardConfig()
    }
}

data class NotificationMediaCardConfig(
    val cardSwitcherEnabled: Boolean = false,
    val cardSwitcherMode: Int = MediaCardConstants.CARD_SWITCHER_MULTI,
    val cardSwitcherMaxCount: Int = 3,
    val layoutStyle: Int = MediaCardConstants.LAYOUT_SYSTEM,
    val ambientFlowMode: Int = MediaCardConstants.NOTIFICATION_AMBIENT_DISABLED,
    val cardTheme: Int = MediaCardConstants.THEME_FOLLOW_SYSTEM,
    val coverStyle: Int = MediaCardConstants.COVER_DEFAULT,
    val progressStyle: Int = MediaCardConstants.PROGRESS_DEFAULT,
    val progressHeadGlow: Boolean = false,
    val thumbStyle: Int = MediaCardConstants.THUMB_DEFAULT,
    val hideCoverSource: Boolean = false,
    val hideCoverShadow: Boolean = false,
    val disableCoverFlip: Boolean = false,
    val hideDeviceSwitch: Boolean = false,
    val hideCustomActions: Boolean = false,
    val hideTime: Boolean = false,
    val actionAlignLeft: Boolean = false,
    val actionOrder: Int = MediaCardConstants.ACTION_ORDER_DEFAULT,
    val backgroundStyle: Int = MediaCardConstants.BACKGROUND_DEFAULT,
    val backgroundBlur: Int = 10,
    val backgroundColorAnimation: Boolean = false,
    val backgroundAutoInvert: Boolean = false,
    val softCoverTone: Int = MediaCardConstants.SOFT_COVER_DARK,
) {
    fun normalized() = copy(
        cardSwitcherMode = cardSwitcherMode.coerceIn(
            MediaCardConstants.CARD_SWITCHER_SINGLE,
            MediaCardConstants.CARD_SWITCHER_MULTI,
        ),
        cardSwitcherMaxCount = cardSwitcherMaxCount.coerceIn(
            MediaCardConstants.CARD_SWITCHER_MIN_COUNT,
            MediaCardConstants.CARD_SWITCHER_MAX_COUNT,
        ),
        layoutStyle = layoutStyle.takeIf {
            it in MediaCardConstants.LAYOUT_SYSTEM..MediaCardConstants.LAYOUT_PIXEL
        } ?: MediaCardConstants.LAYOUT_SYSTEM,
        ambientFlowMode = ambientFlowMode.coerceIn(
            MediaCardConstants.NOTIFICATION_AMBIENT_DISABLED,
            MediaCardConstants.NOTIFICATION_AMBIENT_CUSTOM_FULL,
        ),
        cardTheme = cardTheme.coerceIn(MediaCardConstants.THEME_FOLLOW_SYSTEM, MediaCardConstants.THEME_ALWAYS_DARK),
        coverStyle = coverStyle.coerceIn(MediaCardConstants.COVER_DEFAULT, MediaCardConstants.COVER_HIDDEN),
        progressStyle = if (progressStyle == MediaCardConstants.PROGRESS_WAVE) {
            MediaCardConstants.PROGRESS_WAVE
        } else {
            MediaCardConstants.PROGRESS_DEFAULT
        },
        thumbStyle = thumbStyle.coerceIn(MediaCardConstants.THUMB_DEFAULT, MediaCardConstants.THUMB_HIDDEN),
        actionOrder = actionOrder.coerceIn(MediaCardConstants.ACTION_ORDER_DEFAULT, MediaCardConstants.ACTION_ORDER_PLAY_LEFT),
        backgroundStyle = backgroundStyle.coerceIn(MediaCardConstants.BACKGROUND_DEFAULT, MediaCardConstants.BACKGROUND_SOFT_COVER),
        backgroundBlur = backgroundBlur.coerceIn(1, 20),
        softCoverTone = softCoverTone.coerceIn(MediaCardConstants.SOFT_COVER_LIGHT, MediaCardConstants.SOFT_COVER_FOLLOW_SYSTEM),
    )

    companion object {
        fun defaults() = NotificationMediaCardConfig()
    }
}

data class IslandExpandedMediaCardConfig(
    val layoutStyle: Int = MediaCardConstants.LAYOUT_SYSTEM,
    val ambientFlowMode: Int = MediaCardConstants.ISLAND_EXPANDED_AMBIENT_DYNAMIC,
    val cardTheme: Int = MediaCardConstants.THEME_ALWAYS_DARK,
    val coverStyle: Int = MediaCardConstants.COVER_DEFAULT,
    val progressStyle: Int = MediaCardConstants.PROGRESS_DEFAULT,
    val progressHeadGlow: Boolean = true,
    val thumbStyle: Int = MediaCardConstants.THUMB_DEFAULT,
    val hideCoverSource: Boolean = false,
    val disableCoverFlip: Boolean = false,
    val hideDeviceSwitch: Boolean = false,
    val hideCustomActions: Boolean = false,
    val hideTime: Boolean = false,
    val actionAlignLeft: Boolean = false,
    val actionOrder: Int = MediaCardConstants.ACTION_ORDER_DEFAULT,
    val backgroundStyle: Int = MediaCardConstants.BACKGROUND_DEFAULT,
    val backgroundBlur: Int = 10,
    val backgroundColorAnimation: Boolean = false,
    val backgroundAutoInvert: Boolean = false,
    val softCoverTone: Int = MediaCardConstants.SOFT_COVER_DARK,
) {
    fun normalized() = copy(
        layoutStyle = layoutStyle.takeIf {
            it in MediaCardConstants.LAYOUT_SYSTEM..MediaCardConstants.LAYOUT_PIXEL
        } ?: MediaCardConstants.LAYOUT_SYSTEM,
        ambientFlowMode = ambientFlowMode.coerceIn(
            MediaCardConstants.ISLAND_EXPANDED_AMBIENT_DYNAMIC,
            MediaCardConstants.ISLAND_EXPANDED_AMBIENT_CUSTOM_FULL,
        ),
        cardTheme = cardTheme.coerceIn(MediaCardConstants.THEME_FOLLOW_SYSTEM, MediaCardConstants.THEME_ALWAYS_DARK),
        coverStyle = coverStyle.coerceIn(MediaCardConstants.COVER_DEFAULT, MediaCardConstants.COVER_HIDDEN),
        progressStyle = progressStyle.coerceIn(MediaCardConstants.PROGRESS_DEFAULT, MediaCardConstants.PROGRESS_WAVE),
        thumbStyle = thumbStyle.coerceIn(MediaCardConstants.THUMB_DEFAULT, MediaCardConstants.THUMB_HIDDEN),
        actionOrder = actionOrder.coerceIn(MediaCardConstants.ACTION_ORDER_DEFAULT, MediaCardConstants.ACTION_ORDER_PLAY_LEFT),
        backgroundStyle = backgroundStyle.coerceIn(MediaCardConstants.BACKGROUND_DEFAULT, MediaCardConstants.BACKGROUND_SOFT_COVER),
        backgroundBlur = backgroundBlur.coerceIn(1, 20),
        softCoverTone = softCoverTone.coerceIn(MediaCardConstants.SOFT_COVER_LIGHT, MediaCardConstants.SOFT_COVER_FOLLOW_SYSTEM),
    )

    companion object {
        fun defaults() = IslandExpandedMediaCardConfig()
    }
}
