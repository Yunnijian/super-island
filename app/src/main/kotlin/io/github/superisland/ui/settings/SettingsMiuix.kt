package io.github.superisland.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.superisland.design.AppMiuixSettingsDirectory
import io.github.superisland.design.AppPrimaryTab
import io.github.superisland.model.AppAppearanceSettings
import io.github.superisland.model.AppUiMode

@Composable
fun SettingsMiuix(
    appearance: AppAppearanceSettings,
    onTabSelected: (AppPrimaryTab) -> Unit,
    onUiModeChange: (AppUiMode) -> Unit,
    onOpenTheme: () -> Unit,
    showBottomBar: Boolean = true,
    bottomInnerPadding: Dp = 0.dp,
) {
    AppMiuixSettingsDirectory(
        selectedTab = AppPrimaryTab.SETTINGS,
        appearance = appearance,
        showBottomBar = showBottomBar,
        bottomInnerPadding = bottomInnerPadding,
        onTabSelected = onTabSelected,
        onUiModeChange = onUiModeChange,
        onOpenTheme = onOpenTheme,
    )
}
