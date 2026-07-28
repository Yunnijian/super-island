package io.github.superisland.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.superisland.design.SmartCapsuleAppProfileChannelUi
import io.github.superisland.design.SmartCapsuleAppProfileUi
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.ExpressiveToggleButton
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.statustag.StatusTag

/** Material adapter of KernelSU AppProfileMaterial.kt at the pinned manager commit. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun KernelSuSmartCapsuleAppProfileMaterial(
    state: SmartCapsuleAppProfileUi,
    onEnabledChange: (Boolean) -> Unit,
    onFocusOnlyChange: (Boolean) -> Unit,
    onSelectPriority: (String) -> Unit,
    onAllChannelsChange: (Boolean) -> Unit,
    onOpenChannel: (String) -> Unit,
    onRefreshChannels: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val haptic = LocalHapticFeedback.current

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("应用配置") },
                navigationIcon = {
                    TopBarBackButton(onClick = onBack, contentDescription = "返回")
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onRefreshChannels()
                        },
                        enabled = state.enabled,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新 Channel",
                        )
                    }
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 24.dp),
        ) {
            item(key = "app-profile", contentType = "profile") {
                SegmentedColumn(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    content =
                        listOf<@Composable () -> Unit>(
                            { AppInformationItem(state) },
                            {
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.Notifications,
                                    title = "超级岛通知",
                                    summary = state.enabledSummary,
                                    checked = state.enabled,
                                    onCheckedChange = onEnabledChange,
                                )
                            },
                            {
                                SegmentedSwitchItem(
                                    icon = Icons.Filled.VisibilityOff,
                                    title = "仅显示焦点通知",
                                    summary =
                                        if (state.focusOnly) {
                                            "保留焦点通知，不显示到超级岛"
                                        } else {
                                            "焦点通知同时显示到超级岛"
                                        },
                                    checked = state.focusOnly,
                                    enabled = state.enabled,
                                    onCheckedChange = onFocusOnlyChange,
                                )
                            },
                            {
                                SegmentedSwitchItem(
                                    title = "全部 Channel",
                                    summary =
                                        if (state.allChannels) {
                                            "包括之后新增的 Channel"
                                        } else {
                                            "只允许下方选中的 Channel"
                                        },
                                    checked = state.allChannels,
                                    enabled = state.enabled && state.allChannelsEnabled,
                                    onCheckedChange = onAllChannelsChange,
                                )
                            },
                        ),
                )
            }

            item(key = "priority", contentType = "priority") {
                PriorityButtonGroup(
                    modifier = Modifier.padding(bottom = 6.dp),
                    state = state,
                    onSelectPriority = onSelectPriority,
                )
            }

            if (state.channels.isEmpty()) {
                item(key = "channel-empty", contentType = "channel-empty") {
                    SegmentedColumn(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        title = "通知 Channel",
                        content =
                            listOf<@Composable () -> Unit>(
                                {
                                    SegmentedListItem(
                                        headlineContent = {
                                            Text(
                                                if (state.channelsReady) {
                                                    "未读取到 Channel"
                                                } else {
                                                    "尚未读取 Channel"
                                                },
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                if (state.channelsReady) {
                                                    "该应用当前没有可配置的通知 Channel"
                                                } else {
                                                    "点击右上角刷新图标从 SystemUI 读取"
                                                },
                                            )
                                        },
                                    )
                                },
                            ),
                    )
                }
            } else {
                item(key = "channel-title", contentType = "channel-title") {
                    SegmentedColumn(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        title = "通知 Channel",
                        content = listOf({}),
                    )
                }
                itemsIndexed(
                    items = state.channels,
                    key = { _, channel -> channel.id },
                    contentType = { _, _ -> "channel" },
                ) { index, channel ->
                    SegmentedItem(index = index, count = state.channels.size) {
                        ChannelItem(
                            channel = channel,
                            enabled = state.enabled,
                            onClick = { onOpenChannel(channel.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppInformationItem(state: SmartCapsuleAppProfileUi) {
    val userId = state.appUid / 100000
    val appId = state.appUid % 100000

    SegmentedListItem(
        headlineContent = {
            Text(state.appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    text = "${state.appVersionName} (${state.appVersionCode})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            state.packageInfo?.let { packageInfo ->
                AppIconImage(
                    packageInfo = packageInfo,
                    label = state.appLabel,
                    modifier = Modifier.padding(top = 4.dp).size(48.dp),
                )
            } ?: Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = null,
                modifier = Modifier.padding(top = 4.dp).size(48.dp),
            )
        },
        trailingContent = {
            if (state.appUid >= 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (userId != 0) {
                        StatusTag(
                            label = "USER $userId",
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                        StatusTag(
                            label = "UID $appId",
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    } else {
                        StatusTag(
                            label = "UID ${state.appUid}",
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun PriorityButtonGroup(
    state: SmartCapsuleAppProfileUi,
    onSelectPriority: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    SegmentedColumn(
        modifier = modifier.fillMaxWidth(),
        title = "默认岛优先级",
        content =
            listOf(
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        state.priorityOptions.forEachIndexed { index, option ->
                            ExpressiveToggleButton(
                                checked = state.selectedPriorityId == option.id,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        onSelectPriority(option.id)
                                    }
                                },
                                enabled = state.enabled && !state.focusOnly,
                                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                                shapes =
                                    when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        state.priorityOptions.lastIndex ->
                                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                            ) {
                                Text(option.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                },
            ),
    )
}

@Composable
private fun ChannelItem(
    channel: SmartCapsuleAppProfileChannelUi,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        headlineContent = {
            Text(channel.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                channel.summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            channel.status?.let { status ->
                StatusTag(
                    label = status,
                    backgroundColor =
                        if (channel.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    contentColor =
                        if (channel.enabled) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        },
    )
}
