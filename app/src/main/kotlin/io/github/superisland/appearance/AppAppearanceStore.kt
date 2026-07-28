package io.github.superisland.appearance

import android.content.Context
import android.content.SharedPreferences
import io.github.superisland.model.AppAppearanceSettings
import io.github.superisland.model.AppThemeMode
import io.github.superisland.model.AppUiMode

/**
 * Small, synchronous preference store for renderer selection and visual theme options.
 *
 * It intentionally stores no feature state. Keeping this independent from the pages allows the
 * Miuix and Material renderers to observe one shared source of truth, exactly as KernelSU's
 * SettingsRepository drives its UiMode and theme controller.
 */
class AppAppearanceStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AppAppearanceSettings =
        AppAppearanceSettings(
            uiMode = AppUiMode.fromStorage(preferences.getString(KEY_UI_MODE, null)),
            themeMode = loadThemeMode(),
            miuixMonet = loadMiuixMonet(),
            keyColor = preferences.getInt(KEY_KEY_COLOR, 0),
            paletteStyle = preferences.getString(KEY_PALETTE_STYLE, "TonalSpot") ?: "TonalSpot",
            colorSpec = preferences.getString(KEY_COLOR_SPEC, "SPEC_2025") ?: "SPEC_2025",
            enableBlur = preferences.getBoolean(KEY_ENABLE_BLUR, true),
            enableFloatingBottomBar = preferences.getBoolean(KEY_FLOATING_BOTTOM_BAR, false),
            enableFloatingBottomBarBlur = preferences.getBoolean(KEY_FLOATING_BOTTOM_BAR_BLUR, false),
            enablePredictiveBack = preferences.getBoolean(KEY_PREDICTIVE_BACK, false),
            pageScale = preferences.getFloat(KEY_PAGE_SCALE, 1f),
        ).normalized()

    private fun loadThemeMode(): AppThemeMode {
        val storedMode = preferences.all[KEY_THEME_MODE]
        if (storedMode is Number) return AppThemeMode.fromValue(storedMode.toInt())

        val legacyMode = preferences.all[LEGACY_KEY_COLOR_MODE] as? String
        return AppThemeMode.fromLegacyStorage(legacyMode)
    }

    private fun loadMiuixMonet(): Boolean {
        val storedMonet = preferences.all[KEY_MIUIX_MONET]
        if (storedMonet is Boolean) return storedMonet
        return preferences.all[LEGACY_KEY_DYNAMIC_COLOR] as? Boolean ?: false
    }

    /**
     * Persists just the preferences changed by one UI action.
     *
     * KernelSU's settings repository likewise writes one preference per setter. Apart from doing
     * less work on the main thread, this avoids dispatching a burst of preference callbacks when
     * a single toggle is pressed.
     */
    fun save(previous: AppAppearanceSettings, updated: AppAppearanceSettings) {
        val normalizedPrevious = previous.normalized()
        val normalizedUpdated = updated.normalized()
        preferences.edit().apply {
            if (normalizedPrevious.uiMode != normalizedUpdated.uiMode) putString(KEY_UI_MODE, normalizedUpdated.uiMode.storageValue)
            if (normalizedPrevious.themeMode != normalizedUpdated.themeMode) putInt(KEY_THEME_MODE, normalizedUpdated.themeMode.value)
            if (normalizedPrevious.miuixMonet != normalizedUpdated.miuixMonet) putBoolean(KEY_MIUIX_MONET, normalizedUpdated.miuixMonet)
            if (normalizedPrevious.keyColor != normalizedUpdated.keyColor) putInt(KEY_KEY_COLOR, normalizedUpdated.keyColor)
            if (normalizedPrevious.paletteStyle != normalizedUpdated.paletteStyle) putString(KEY_PALETTE_STYLE, normalizedUpdated.paletteStyle)
            if (normalizedPrevious.colorSpec != normalizedUpdated.colorSpec) putString(KEY_COLOR_SPEC, normalizedUpdated.colorSpec)
            if (normalizedPrevious.enableBlur != normalizedUpdated.enableBlur) putBoolean(KEY_ENABLE_BLUR, normalizedUpdated.enableBlur)
            if (normalizedPrevious.enableFloatingBottomBar != normalizedUpdated.enableFloatingBottomBar) {
                putBoolean(KEY_FLOATING_BOTTOM_BAR, normalizedUpdated.enableFloatingBottomBar)
            }
            if (normalizedPrevious.enableFloatingBottomBarBlur != normalizedUpdated.enableFloatingBottomBarBlur) {
                putBoolean(KEY_FLOATING_BOTTOM_BAR_BLUR, normalizedUpdated.enableFloatingBottomBarBlur)
            }
            if (normalizedPrevious.enablePredictiveBack != normalizedUpdated.enablePredictiveBack) {
                putBoolean(KEY_PREDICTIVE_BACK, normalizedUpdated.enablePredictiveBack)
            }
            if (normalizedPrevious.pageScale != normalizedUpdated.pageScale) {
                putFloat(KEY_PAGE_SCALE, normalizedUpdated.pageScale)
            }
        }.apply()
    }

    fun observe(onChanged: (AppAppearanceSettings) -> Unit): () -> Unit {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key in APPEARANCE_KEYS) onChanged(load())
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val PREFERENCES_NAME = "app_appearance"
        const val KEY_UI_MODE = "ui_mode"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_MIUIX_MONET = "miuix_monet"
        const val LEGACY_KEY_COLOR_MODE = "color_mode"
        const val LEGACY_KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_KEY_COLOR = "key_color"
        const val KEY_PALETTE_STYLE = "palette_style"
        const val KEY_COLOR_SPEC = "color_spec"
        const val KEY_ENABLE_BLUR = "enable_blur"
        const val KEY_FLOATING_BOTTOM_BAR = "floating_bottom_bar"
        const val KEY_FLOATING_BOTTOM_BAR_BLUR = "floating_bottom_bar_blur"
        const val KEY_PREDICTIVE_BACK = "predictive_back"
        const val KEY_PAGE_SCALE = "page_scale"
        val APPEARANCE_KEYS =
            setOf(
                KEY_UI_MODE,
                KEY_THEME_MODE,
                KEY_MIUIX_MONET,
                LEGACY_KEY_COLOR_MODE,
                LEGACY_KEY_DYNAMIC_COLOR,
                KEY_KEY_COLOR,
                KEY_PALETTE_STYLE,
                KEY_COLOR_SPEC,
                KEY_ENABLE_BLUR,
                KEY_FLOATING_BOTTOM_BAR,
                KEY_FLOATING_BOTTOM_BAR_BLUR,
                KEY_PREDICTIVE_BACK,
                KEY_PAGE_SCALE,
            )
    }
}
