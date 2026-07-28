package io.github.superisland.ui.material

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.superisland.design.AppKernelInfoUi
import io.github.superisland.design.AppMaintenanceActionUi
import io.github.superisland.ui.home.HomeActions
import io.github.superisland.ui.home.HomeUiState
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors

private enum class MaterialHomeMaintenanceAction {
    RESTART_SCOPES,
    RESET_ALL_SETTINGS,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeMaterial(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp = 0.dp,
) {
    var pendingAction by remember { mutableStateOf<MaterialHomeMaintenanceAction?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val active = state.environment.lsposedActive && state.environment.rootAvailable
    val statusContainer = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val statusContent = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val statusIcon = if (active) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline
    val informationEntries =
        listOf(
            AppKernelInfoUi("设备权限", state.environment.rootSummary),
            AppKernelInfoUi("设备型号", state.deviceModel),
            AppKernelInfoUi("系统指纹", state.fingerprint),
        )
    val maintenanceActions =
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
        )

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("首页") },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + bottomInnerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().height(142.dp),
                    colors = CardDefaults.cardColors(containerColor = statusContainer, contentColor = statusContent),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusContent.copy(alpha = 0.28f),
                            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 34.dp, y = 28.dp).size(170.dp),
                        )
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (state.environment.lsposedActive) "模块已激活" else "模块未激活",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(text = state.moduleSummary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    informationEntries.forEachIndexed { index, entry ->
                        ListItem(
                            headlineContent = { Text(entry.title) },
                            supportingContent = { Text(entry.summary) },
                        )
                        if (index != informationEntries.lastIndex) HorizontalDivider()
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    maintenanceActions.forEachIndexed { index, action ->
                        ListItem(
                            headlineContent = { Text(action.title) },
                            supportingContent = { Text(action.summary) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = action.enabled) {
                                        pendingAction =
                                            if (action.id == "restart_scopes") {
                                                MaterialHomeMaintenanceAction.RESTART_SCOPES
                                            } else {
                                                MaterialHomeMaintenanceAction.RESET_ALL_SETTINGS
                                            }
                                    },
                            colors =
                                if (action.enabled) {
                                    androidx.compose.material3.ListItemDefaults.colors()
                                } else {
                                    androidx.compose.material3.ListItemDefaults.colors(
                                        headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    )
                                },
                        )
                        if (index != maintenanceActions.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingAction?.let { action ->
        val restarting = action == MaterialHomeMaintenanceAction.RESTART_SCOPES
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (restarting) "重启作用域" else "重置所有设置") },
            text = {
                Text(
                    if (restarting) {
                        "重启相关作用域，使模块配置立即生效。"
                    } else {
                        "将清除超级岛通知、音乐、常驻超级岛与焦点测试配置，并结束当前事件；不会撤销通知访问、Root 或 LSPosed 授权。"
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingAction = null
                        if (restarting) actions.restartScopes() else actions.resetAllSettings()
                    },
                ) {
                    Text(if (restarting) "确认重启" else "确认重置")
                }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("取消") } },
        )
    }
}
