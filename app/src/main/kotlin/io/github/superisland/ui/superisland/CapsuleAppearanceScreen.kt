package io.github.superisland.ui.superisland

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.superisland.IslandAppearanceDashboardStateOwner
import io.github.superisland.model.SystemUiIslandAppearanceContract
import io.github.superisland.ui.adaptive.AppDirectoryDetailScreen
import io.github.superisland.ui.adaptive.AppFeatureMasterSwitch

@Composable
internal fun CapsuleAppearanceScreen(
    stateOwner: IslandAppearanceDashboardStateOwner,
    onBack: () -> Unit,
) {
    val state by stateOwner.state.collectAsStateWithLifecycle()
    val runtimeAvailable = SystemUiIslandAppearanceContract.COLOR_OS_FLUID_CLOUD_RUNTIME_AVAILABLE
    val enabled = runtimeAvailable && state.config.capsule.colorOsFluidCloudStyleEnabled
    AppDirectoryDetailScreen(
        title = "胶囊定制",
        subtitle = "",
        groups = emptyList(),
        header = {
            AppFeatureMasterSwitch(
                title = "ColorOS流体云样式",
                summary =
                    if (!runtimeAvailable) {
                        "当前运行时方案已停用，使用 HyperOS 默认逻辑"
                    } else if (enabled) {
                        "使用 ColorOS 流体云展示与最小化/收起逻辑"
                    } else {
                        "使用 HyperOS 默认展示与最小化/收起逻辑"
                    },
                checked = enabled,
                enabled = state.loaded && runtimeAvailable,
                onCheckedChange = stateOwner::setColorOsFluidCloudStyleEnabled,
            )
        },
        onBack = onBack,
        onEntrySelected = {},
    )
}
