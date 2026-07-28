package io.github.superisland.appearance

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.github.superisland.model.AppAppearanceSettings
import io.github.superisland.model.AppUiMode
import me.weishu.kernelsu.ui.theme.AppSettings
import me.weishu.kernelsu.ui.theme.ColorMode
import me.weishu.kernelsu.ui.UiMode

/** Maps product preferences onto KernelSU's unchanged root-theme contract. */
fun AppAppearanceSettings.toKernelSuAppSettings(): AppSettings {
    return AppSettings(
        colorMode = toKernelSuColorMode(),
        keyColor = keyColor,
        paletteStyle = runCatching { PaletteStyle.valueOf(paletteStyle) }.getOrDefault(PaletteStyle.TonalSpot),
        colorSpec = runCatching { ColorSpec.SpecVersion.valueOf(colorSpec) }.getOrDefault(ColorSpec.SpecVersion.SPEC_2025),
    )
}

fun AppAppearanceSettings.toKernelSuColorMode(): ColorMode = ColorMode.fromValue(normalized().themeMode.value)

fun AppUiMode.toKernelSuUiMode(): UiMode =
    when (this) {
        AppUiMode.MIUIX -> UiMode.Miuix
        AppUiMode.MATERIAL -> UiMode.Material
    }
