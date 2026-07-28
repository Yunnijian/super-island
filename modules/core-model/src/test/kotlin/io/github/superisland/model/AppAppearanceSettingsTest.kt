package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppAppearanceSettingsTest {
    @Test
    fun themeModeValuesMatchKernelSuColorModeContract() {
        assertEquals(0, AppThemeMode.SYSTEM.value)
        assertEquals(1, AppThemeMode.LIGHT.value)
        assertEquals(2, AppThemeMode.DARK.value)
        assertEquals(3, AppThemeMode.MONET_SYSTEM.value)
        assertEquals(4, AppThemeMode.MONET_LIGHT.value)
        assertEquals(5, AppThemeMode.MONET_DARK.value)
        assertEquals(6, AppThemeMode.DARK_AMOLED.value)
    }

    @Test
    fun miuixThemeTabsPreserveMonetSelection() {
        val state = AppAppearanceSettings(miuixMonet = true).normalized()

        assertEquals(AppThemeMode.MONET_SYSTEM, state.themeMode)
        assertEquals(AppThemeMode.MONET_LIGHT, state.withBaseThemeMode(1).themeMode)
        assertEquals(AppThemeMode.MONET_DARK, state.withBaseThemeMode(2).themeMode)
    }

    @Test
    fun switchingToMaterialRemovesMiuixOnlyMonetMode() {
        val state =
            AppAppearanceSettings(
                uiMode = AppUiMode.MIUIX,
                themeMode = AppThemeMode.MONET_DARK,
                miuixMonet = true,
            )

        val material = state.withUiMode(AppUiMode.MATERIAL)

        assertEquals(AppUiMode.MATERIAL, material.uiMode)
        assertEquals(AppThemeMode.DARK, material.themeMode)
    }

    @Test
    fun materialNormalizationPreservesAmoledButRejectsWallpaperMonetModes() {
        assertEquals(
            AppThemeMode.LIGHT,
            AppAppearanceSettings(
                uiMode = AppUiMode.MATERIAL,
                themeMode = AppThemeMode.MONET_LIGHT,
            ).normalized().themeMode,
        )
        assertEquals(
            AppThemeMode.DARK_AMOLED,
            AppAppearanceSettings(
                uiMode = AppUiMode.MATERIAL,
                themeMode = AppThemeMode.DARK_AMOLED,
            ).normalized().themeMode,
        )
    }

    @Test
    fun switchingBackToMiuixRestoresItsMonetPreference() {
        val material =
            AppAppearanceSettings(
                uiMode = AppUiMode.MATERIAL,
                themeMode = AppThemeMode.LIGHT,
                miuixMonet = true,
            )

        val miuix = material.withUiMode(AppUiMode.MIUIX)

        assertEquals(AppUiMode.MIUIX, miuix.uiMode)
        assertEquals(AppThemeMode.MONET_LIGHT, miuix.themeMode)
    }

    @Test
    fun amoledRemainsSelectableInMaterialAndBecomesDarkInMiuix() {
        val material =
            AppAppearanceSettings(uiMode = AppUiMode.MATERIAL)
                .withThemeMode(AppThemeMode.DARK_AMOLED)

        assertEquals(AppThemeMode.DARK_AMOLED, material.themeMode)
        assertEquals(AppThemeMode.DARK, material.withUiMode(AppUiMode.MIUIX).themeMode)
    }

    @Test
    fun pageScaleUsesKernelSuRange() {
        assertEquals(0.8f, AppAppearanceSettings(pageScale = 0.7f).normalized().pageScale)
        assertEquals(1.1f, AppAppearanceSettings(pageScale = 1.2f).normalized().pageScale)
        assertEquals(0.8f, AppAppearanceSettings.MIN_PAGE_SCALE)
        assertEquals(1.1f, AppAppearanceSettings.MAX_PAGE_SCALE)
    }
}
