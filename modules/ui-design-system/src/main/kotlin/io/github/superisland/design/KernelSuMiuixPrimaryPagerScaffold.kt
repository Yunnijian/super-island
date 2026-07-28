package io.github.superisland.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import me.weishu.kernelsu.ui.component.FloatingBottomBar
import me.weishu.kernelsu.ui.component.FloatingBottomBarItem
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LocalKernelSuFloatingBarDarkTheme = staticCompositionLocalOf { false }

// Geometry belongs to the product adapter; drag springs, lens effects, liquid glass and press
// animation remain in the pinned KernelSU FloatingBottomBar source. Five 70.dp slots produce a
// roughly 350.dp capsule on the 394.dp/520-dpi reference device, matching AstraFlow's proportions.
private val ProductFloatingBarItemMinWidth = 70.dp
private val ProductFloatingBarBottomMargin = 12.dp

/** Compatibility import target for KernelSU's unmodified FloatingBottomBar.kt. */
@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean = LocalKernelSuFloatingBarDarkTheme.current

/**
 * Five-tab product adapter around KernelSU's unmodified floating navigation implementation.
 * Only destinations are product data; pill animation, blur, lens, liquid glass, and drag handling
 * compile directly from KernelSU source.
 */
@Composable
fun KernelSuMiuixPrimaryPagerScaffold(
    selectedIndex: () -> Int,
    onTabSelected: (AppPrimaryTab) -> Unit,
    enableBlur: Boolean,
    enableFloatingBottomBar: Boolean,
    enableFloatingBottomBarBlur: Boolean,
    darkTheme: Boolean,
    content: @Composable (bottomInnerPadding: Dp) -> Unit,
) {
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop =
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    val tabs = AppPrimaryTab.entries
    val onSelectIndex: (Int) -> Unit = { index -> onTabSelected(tabs[index]) }
    val bottomBar: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (enableFloatingBottomBar) {
                CompositionLocalProvider(LocalKernelSuFloatingBarDarkTheme provides darkTheme) {
                    FloatingBottomBar(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                )
                                .padding(
                                    bottom =
                                        ProductFloatingBarBottomMargin +
                                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                                ),
                        selectedIndex = selectedIndex,
                        onSelected = onSelectIndex,
                        backdrop = backdrop,
                        tabsCount = tabs.size,
                        isBlurEnabled = enableFloatingBottomBarBlur,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            FloatingBottomBarItem(
                                onClick = { onSelectIndex(index) },
                                modifier = Modifier.defaultMinSize(minWidth = ProductFloatingBarItemMinWidth),
                            ) {
                                Icon(imageVector = tab.miuixIcon(), contentDescription = tab.label)
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                )
                            }
                        }
                    }
                }
            } else {
                BlurredBar(blurBackdrop) {
                    NavigationBar(
                        // Keep Miuix's 64.dp item row and let its own bottom spacer consume the
                        // gesture/navigation inset exactly once (the KernelSU contract).
                        modifier = Modifier.fillMaxWidth(),
                        color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                        defaultWindowInsetsPadding = true,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                modifier = Modifier.weight(1f),
                                selected = tab == tabs.getOrNull(selectedIndex()),
                                onClick = { onSelectIndex(index) },
                                icon = tab.miuixIcon(),
                                label = tab.label,
                            )
                        }
                    }
                }
            }
        }
    }
    Scaffold(bottomBar = bottomBar) { innerPadding ->
        Box(
            modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
        ) {
            Box(
                modifier =
                    if (enableFloatingBottomBar && enableFloatingBottomBarBlur) {
                        Modifier.layerBackdrop(backdrop)
                    } else {
                        Modifier
                    },
            ) {
                content(innerPadding.calculateBottomPadding())
            }
        }
    }
}

private fun AppPrimaryTab.miuixIcon() =
    when (this) {
        AppPrimaryTab.HOME -> MiuixIcons.Home
        AppPrimaryTab.SUPER_ISLAND -> MiuixIcons.Layers
        AppPrimaryTab.EXTENSIONS -> MiuixIcons.GridView
        AppPrimaryTab.SETTINGS -> MiuixIcons.Settings
        AppPrimaryTab.PROFILE -> MiuixIcons.Contacts
    }
