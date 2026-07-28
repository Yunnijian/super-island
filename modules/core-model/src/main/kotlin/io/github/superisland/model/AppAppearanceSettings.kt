package io.github.superisland.model

/**
 * User-selectable presentation skin. Business state deliberately never depends on this value.
 *
 * The names and the default follow KernelSU Manager's dual-skin model. Miuix stays the product
 * default; Material is a complete alternative renderer rather than a collection of mixed widgets.
 */
enum class AppUiMode(
    val storageValue: String,
) {
    MIUIX("miuix"),
    MATERIAL("material"),
    ;

    companion object {
        fun fromStorage(value: String?): AppUiMode =
            entries.firstOrNull { it.storageValue == value } ?: MIUIX
    }
}

/**
 * Complete KernelSU theme-mode state. Values intentionally match KernelSU's `ColorMode.value`.
 * Keeping AMOLED and Monet modes lossless is required for its unmodified theme controls.
 */
enum class AppThemeMode(
    val value: Int,
) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6),
    ;

    companion object {
        fun fromValue(value: Int): AppThemeMode = entries.firstOrNull { it.value == value } ?: SYSTEM

        fun fromLegacyStorage(value: String?): AppThemeMode =
            when (value) {
                "light" -> LIGHT
                "dark" -> DARK
                else -> SYSTEM
            }
    }

    /** Matches KernelSU's `ColorMode.isMonet`, which also includes `DARK_AMOLED`. */
    val isMonet: Boolean get() = value >= MONET_SYSTEM.value

    internal val isWallpaperMonet: Boolean get() = value in MONET_SYSTEM.value..MONET_DARK.value

    fun toNonMonet(): AppThemeMode =
        when (this) {
            MONET_SYSTEM -> SYSTEM
            MONET_LIGHT -> LIGHT
            MONET_DARK,
            DARK_AMOLED,
            -> DARK
            else -> this
        }

    fun toMonet(): AppThemeMode =
        when (this) {
            SYSTEM -> MONET_SYSTEM
            LIGHT -> MONET_LIGHT
            DARK -> MONET_DARK
            else -> this
        }
}

/**
 * Pure UI preferences shared by both renderers. Values in this class must correspond to an
 * observable UI effect; unsupported visual toggles are intentionally not exposed.
 */
data class AppAppearanceSettings(
    val uiMode: AppUiMode = AppUiMode.MIUIX,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val paletteStyle: String = "TonalSpot",
    val colorSpec: String = "SPEC_2025",
    /** KernelSU's SettingsUiState defaults blur to enabled on a fresh install. */
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = false,
    val enableFloatingBottomBarBlur: Boolean = false,
    val enablePredictiveBack: Boolean = false,
    val pageScale: Float = 1f,
) {
    fun normalized(): AppAppearanceSettings {
        val normalizedThemeMode =
            when (uiMode) {
                AppUiMode.MATERIAL -> if (themeMode.isWallpaperMonet) themeMode.toNonMonet() else themeMode
                AppUiMode.MIUIX ->
                    when {
                        miuixMonet && !themeMode.isMonet -> themeMode.toMonet()
                        !miuixMonet && themeMode.isMonet -> themeMode.toNonMonet()
                        else -> themeMode
                    }
            }
        return copy(
            themeMode = normalizedThemeMode,
            pageScale = pageScale.coerceIn(MIN_PAGE_SCALE, MAX_PAGE_SCALE),
        )
    }

    /** Matches KernelSU SettingsViewModel.setUiMode, including Monet/AMOLED conversion. */
    fun withUiMode(mode: AppUiMode): AppAppearanceSettings {
        if (mode == uiMode) return this
        val convertedThemeMode =
            when {
                uiMode == AppUiMode.MATERIAL && mode == AppUiMode.MIUIX -> {
                    val baseMode = if (themeMode == AppThemeMode.DARK_AMOLED) AppThemeMode.DARK else themeMode
                    when {
                        miuixMonet && !baseMode.isMonet -> baseMode.toMonet()
                        !miuixMonet && baseMode.isMonet -> baseMode.toNonMonet()
                        else -> baseMode
                    }
                }
                uiMode == AppUiMode.MIUIX && mode == AppUiMode.MATERIAL -> {
                    if (themeMode.isMonet) themeMode.toNonMonet() else themeMode
                }
                else -> themeMode
            }
        return copy(uiMode = mode, themeMode = convertedThemeMode).normalized()
    }

    /** Matches KernelSU SettingsViewModel.setThemeMode for the three Miuix tabs. */
    fun withBaseThemeMode(index: Int): AppAppearanceSettings {
        val baseMode = AppThemeMode.fromValue(index.coerceIn(0, 2))
        return copy(themeMode = if (uiMode == AppUiMode.MIUIX && miuixMonet) baseMode.toMonet() else baseMode).normalized()
    }

    /** Matches KernelSU SettingsViewModel.setMiuixMonet. */
    fun withMiuixMonet(enabled: Boolean): AppAppearanceSettings {
        val convertedThemeMode =
            when {
                enabled && !themeMode.isMonet -> themeMode.toMonet()
                !enabled && themeMode.isMonet -> themeMode.toNonMonet()
                else -> themeMode
            }
        return copy(miuixMonet = enabled, themeMode = convertedThemeMode).normalized()
    }

    fun withThemeMode(mode: AppThemeMode): AppAppearanceSettings = copy(themeMode = mode).normalized()

    companion object {
        const val MIN_PAGE_SCALE = 0.8f
        const val MAX_PAGE_SCALE = 1.1f
    }
}
