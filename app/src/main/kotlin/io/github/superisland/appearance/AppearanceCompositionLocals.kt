package io.github.superisland.appearance

import androidx.compose.runtime.staticCompositionLocalOf

/** The activity-scoped owner used directly by retained Navigation3 entries. */
val LocalAppAppearanceViewModel =
    staticCompositionLocalOf<AppAppearanceViewModel> {
        error("LocalAppAppearanceViewModel was not provided")
    }

/** Settings-page owner kept separate from the root theme observer. */
val LocalAppAppearanceSettingsViewModel =
    staticCompositionLocalOf<AppAppearanceSettingsViewModel> {
        error("LocalAppAppearanceSettingsViewModel was not provided")
    }
