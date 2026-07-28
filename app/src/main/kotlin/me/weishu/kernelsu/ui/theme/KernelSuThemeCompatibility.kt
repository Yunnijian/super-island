/* Compatibility boundary for the unmodified KernelSU theme and color-palette sources. */
package me.weishu.kernelsu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.ui.UiMode

enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6),
    ;

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem get() = value == 0 || value == 3
    val isDark get() = value == 2 || value == 5 || value == 6
    val isAmoled get() = value == 6
    val isMonet get() = value >= 3

    fun toNonMonetMode(): Int =
        when (this) {
            MONET_SYSTEM -> SYSTEM.value
            MONET_LIGHT -> LIGHT.value
            MONET_DARK,
            DARK_AMOLED,
            -> DARK.value
            else -> value
        }

    fun toMonetMode(): Int =
        when (this) {
            SYSTEM -> MONET_SYSTEM.value
            LIGHT -> MONET_LIGHT.value
            DARK -> MONET_DARK.value
            else -> value
        }
}

data class AppSettings(
    val colorMode: ColorMode,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
)

val PaletteStyle.supportsSpec2025: Boolean
    get() = this in setOf(PaletteStyle.TonalSpot, PaletteStyle.Neutral, PaletteStyle.Vibrant, PaletteStyle.Expressive)

fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) ColorSpec.SpecVersion.SPEC_2021 else this

val LocalEnableBlur = staticCompositionLocalOf { false }
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { false }
val LocalColorMode = staticCompositionLocalOf { ColorMode.SYSTEM.value }

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean =
    when (LocalColorMode.current) {
        ColorMode.LIGHT.value,
        ColorMode.MONET_LIGHT.value,
        -> false
        ColorMode.DARK.value,
        ColorMode.MONET_DARK.value,
        ColorMode.DARK_AMOLED.value,
        -> true
        else -> isSystemInDarkTheme()
    }

/** KernelSU Theme.kt's renderer switch with its repository default removed. */
@Composable
fun KernelSUTheme(
    appSettings: AppSettings,
    uiMode: UiMode,
    content: @Composable () -> Unit,
) {
    when (uiMode) {
        UiMode.Miuix -> MiuixKernelSUTheme(appSettings = appSettings, content = content)
        UiMode.Material -> MaterialKernelSUTheme(appSettings = appSettings, content = content)
    }
}
