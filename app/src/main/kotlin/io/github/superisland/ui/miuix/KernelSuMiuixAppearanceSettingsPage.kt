package io.github.superisland.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.github.superisland.appearance.LocalAppAppearanceSettingsViewModel
import io.github.superisland.appearance.toKernelSuColorMode
import io.github.superisland.model.AppAppearanceSettings
import io.github.superisland.model.AppThemeMode
import me.weishu.kernelsu.ui.screen.colorpalette.ColorPaletteScreenActions
import me.weishu.kernelsu.ui.screen.colorpalette.ColorPaletteScreenMiuix
import me.weishu.kernelsu.ui.screen.colorpalette.ColorPaletteUiState
import me.weishu.kernelsu.ui.screen.settings.SettingsUiState

/** Product-data boundary around KernelSU's unmodified ColorPaletteScreenMiuix. */
@Composable
fun KernelSuMiuixAppearanceSettingsPage(
    onBack: () -> Unit,
) {
    val appearanceViewModel = LocalAppAppearanceSettingsViewModel.current
    val appearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
    val onAppearanceChange = appearanceViewModel::update
    val colorMode = appearance.toKernelSuColorMode()
    val paletteStyle = runCatching { PaletteStyle.valueOf(appearance.paletteStyle) }.getOrDefault(PaletteStyle.TonalSpot)
    val colorSpec = runCatching { ColorSpec.SpecVersion.valueOf(appearance.colorSpec) }.getOrDefault(ColorSpec.SpecVersion.SPEC_2025)
    ColorPaletteScreenMiuix(
        state = ColorPaletteUiState(
            uiState =
                SettingsUiState(
                    themeMode = appearance.themeMode.value,
                    miuixMonet = appearance.miuixMonet,
                    keyColor = appearance.keyColor,
                    colorStyle = appearance.paletteStyle,
                    colorSpec = appearance.colorSpec,
                    enableBlur = appearance.enableBlur,
                    enableFloatingBottomBar = appearance.enableFloatingBottomBar,
                    enableFloatingBottomBarBlur = appearance.enableFloatingBottomBarBlur,
                    enableNavigationBadge = false,
                    enablePredictiveBack = appearance.enablePredictiveBack,
                    pageScale = appearance.pageScale,
                ),
            currentColorMode = colorMode,
            currentPaletteStyle = paletteStyle,
            currentColorSpec = colorSpec,
        ),
        actions =
            ColorPaletteScreenActions(
                onBack = onBack,
                onSetThemeMode = { selected -> onAppearanceChange { it.withBaseThemeMode(selected) } },
                onSetMiuixMonet = { enabled -> onAppearanceChange { it.withMiuixMonet(enabled) } },
                onSetKeyColor = { color -> onAppearanceChange { it.copy(keyColor = color) } },
                onSetColorMode = { selected -> onAppearanceChange { it.withThemeMode(AppThemeMode.fromValue(selected.value)) } },
                onSetColorStyle = { style -> onAppearanceChange { it.copy(paletteStyle = style) } },
                onSetColorSpec = { spec -> onAppearanceChange { it.copy(colorSpec = spec) } },
                onSetEnableBlur = { enabled -> onAppearanceChange { it.copy(enableBlur = enabled) } },
                onSetEnableFloatingBottomBar = { enabled -> onAppearanceChange { it.copy(enableFloatingBottomBar = enabled) } },
                onSetEnableFloatingBottomBarBlur = { enabled -> onAppearanceChange { it.copy(enableFloatingBottomBarBlur = enabled) } },
                onSetEnableNavigationBadge = {},
                onSetEnablePredictiveBack = { enabled -> onAppearanceChange { it.copy(enablePredictiveBack = enabled) } },
                onSetPageScale = { scale -> onAppearanceChange { it.copy(pageScale = scale) } },
            ),
    )
}
