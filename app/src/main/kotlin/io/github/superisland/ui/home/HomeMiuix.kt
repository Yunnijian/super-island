package io.github.superisland.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.superisland.design.AppConfirmationDialog
import io.github.superisland.design.AppFiveTabDirectoryScreen
import io.github.superisland.design.AppHomeOverview
import io.github.superisland.design.AppKernelInfoUi
import io.github.superisland.design.AppMaintenanceActionUi
import io.github.superisland.design.AppPrimaryTab
import io.github.superisland.design.AppRuntimeStatusCardUi

private enum class HomeMaintenanceAction {
    RESTART_SCOPES,
    RESET_ALL_SETTINGS,
}

/** Miuix-only Home skin. Material counterpart will consume the same [HomeUiState]/[HomeActions]. */
@Composable
fun HomeMiuix(
    state: HomeUiState,
    actions: HomeActions,
    onTabSelected: (AppPrimaryTab) -> Unit,
    showBottomBar: Boolean = true,
    bottomInnerPadding: Dp = 0.dp,
) {
    var pendingAction by remember { mutableStateOf<HomeMaintenanceAction?>(null) }
    AppFiveTabDirectoryScreen(
        selectedTab = AppPrimaryTab.HOME,
        title = "首页",
        subtitle = "",
        header = {
            AppHomeOverview(
                statusCards =
                    listOf(
                        AppRuntimeStatusCardUi(
                            title =
                                when {
                                    state.isLoading -> "正在检查环境…"
                                    state.environment.lsposedActive -> "模块已激活"
                                    else -> "模块未激活"
                                },
                            summary = state.moduleSummary,
                            active = state.environment.environmentAcceptable,
                            isLoading = state.isLoading,
                        ),
                    ),
                informationEntries =
                    listOf(
                        AppKernelInfoUi("设备权限", state.environment.rootSummary),
                        AppKernelInfoUi("设备型号", state.deviceModel),
                        AppKernelInfoUi("系统指纹", state.fingerprint),
                    ),
                maintenanceActions =
                    listOf(
                        AppMaintenanceActionUi(
                            id = "restart_scopes",
                            title = "重启作用域",
                            summary = "重启相关作用域，使模块配置立即生效",
                            enabled = state.environment.rootAvailable,
                        ),
                        AppMaintenanceActionUi(
                            id = "reset_all_settings",
                            title = "重置所有设置",
                            summary = "清除自定义配置并恢复默认值；不会撤销系统授权",
                        ),
                    ),
                onMaintenanceAction = { entryId ->
                    pendingAction =
                        when (entryId) {
                            "restart_scopes" -> HomeMaintenanceAction.RESTART_SCOPES
                            "reset_all_settings" -> HomeMaintenanceAction.RESET_ALL_SETTINGS
                            else -> null
                        }
                },
            )
        },
        groups = emptyList(),
        overlay = {
            pendingAction?.let { action ->
                AppConfirmationDialog(
                    visible = true,
                    title = if (action == HomeMaintenanceAction.RESTART_SCOPES) "重启作用域" else "重置所有设置",
                    summary =
                        if (action == HomeMaintenanceAction.RESTART_SCOPES) {
                            "重启相关作用域，使模块配置立即生效。"
                        } else {
                            "将清除超级岛通知、音乐、常驻超级岛与焦点测试配置，并结束当前事件；不会撤销通知访问、Root 或 LSPosed 授权。"
                        },
                    confirmLabel = if (action == HomeMaintenanceAction.RESTART_SCOPES) "确认重启" else "确认重置",
                    onDismiss = { pendingAction = null },
                    onConfirm = {
                        pendingAction = null
                        if (action == HomeMaintenanceAction.RESTART_SCOPES) {
                            actions.restartScopes()
                        } else {
                            actions.resetAllSettings()
                        }
                    },
                )
            }
        },
        showBottomBar = showBottomBar,
        bottomInnerPadding = bottomInnerPadding,
        onTabSelected = onTabSelected,
        onEntrySelected = {},
    )
}
