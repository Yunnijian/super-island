package io.github.superisland.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import io.github.superisland.model.AppAppearanceSettings
import io.github.superisland.model.AppUiMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

/** KernelSU-style appearance section for the Miuix settings directory. */
@Composable
fun AppMiuixSettingsDirectory(
    selectedTab: AppPrimaryTab,
    appearance: AppAppearanceSettings,
    showBottomBar: Boolean,
    bottomInnerPadding: Dp = 0.dp,
    onTabSelected: (AppPrimaryTab) -> Unit,
    onUiModeChange: (AppUiMode) -> Unit,
    onOpenTheme: () -> Unit,
) {
    AppFiveTabDirectoryScreen(
        selectedTab = selectedTab,
        title = "设置",
        subtitle = "",
        header = {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = "界面风格",
                    summary = "选择应用的界面风格",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.GridView,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    },
                    items = listOf("Miuix", "Material"),
                    selectedIndex = if (appearance.uiMode == AppUiMode.MATERIAL) 1 else 0,
                    onSelectedIndexChange = { index ->
                        onUiModeChange(if (index == 1) AppUiMode.MATERIAL else AppUiMode.MIUIX)
                    },
                )
                ArrowPreference(
                    title = "主题设置",
                    summary = "颜色模式、动态配色与界面缩放",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Theme,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    },
                    onClick = onOpenTheme,
                )
            }
        },
        groups = emptyList(),
        showBottomBar = showBottomBar,
        bottomInnerPadding = bottomInnerPadding,
        onTabSelected = onTabSelected,
        onEntrySelected = {},
    )
}
