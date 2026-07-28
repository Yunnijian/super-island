package io.github.superisland.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.ui.component.statustag.StatusTagMiuix
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Product-field adapter of KernelSU AppProfileMiuix at the pinned manager commit. */
@Composable
internal fun KernelSuSmartCapsuleAppProfileMiuix(
    state: SmartCapsuleAppProfileUi,
    onEnabledChange: (Boolean) -> Unit,
    onFocusOnlyChange: (Boolean) -> Unit,
    onSelectPriority: (String) -> Unit,
    onAllChannelsChange: (Boolean) -> Unit,
    onOpenChannel: (String) -> Unit,
    onRefreshChannels: () -> Unit,
    onBack: () -> Unit,
    appIcon: (@Composable () -> Unit)?,
) {
    val enableBlur = LocalDesignEnableBlur.current
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = "应用配置",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier =
                                    Modifier.graphicsLayer {
                                        if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                    },
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onRefreshChannels,
                            enabled = state.enabled,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新 Channel",
                                tint =
                                    if (state.enabled) {
                                        colorScheme.onBackground
                                    } else {
                                        colorScheme.onSurfaceVariantActions
                                    },
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = {},
        contentWindowInsets =
            WindowInsets.systemBars
                .add(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier =
                if (backdrop != null) {
                    Modifier.layerBackdrop(backdrop)
                } else {
                    Modifier
                },
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .padding(top = 16.dp)
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item(key = "app-info", contentType = "app-info") {
                    AppInformationCard(state = state, appIcon = appIcon)
                }
                item(key = "allow-island", contentType = "preference") {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                    ) {
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            title = "超级岛通知",
                            summary = state.enabledSummary,
                            checked = state.enabled,
                            onCheckedChange = onEnabledChange,
                        )
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = colorScheme.onBackground,
                                )
                            },
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
                        SwitchPreference(
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.SelectAll,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = colorScheme.onBackground,
                                )
                            },
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
                    }
                }
                item(key = "default-priority", contentType = "preference") {
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                    ) {
                        OverlayDropdownPreference(
                            title = "默认岛优先级",
                            summary =
                                if (state.focusOnly) {
                                    "仅在显示到超级岛时生效"
                                } else {
                                    "未单独设置的 Channel 使用此优先级"
                                },
                            items = state.priorityOptions.map(IslandPriorityOptionUi::title),
                            selectedIndex =
                                state.priorityOptions
                                    .indexOfFirst { option -> option.id == state.selectedPriorityId }
                                    .coerceAtLeast(0),
                            enabled =
                                state.enabled &&
                                    !state.focusOnly &&
                                    state.priorityOptions.isNotEmpty(),
                            startAction = {
                                Icon(
                                    imageVector = Icons.Rounded.PriorityHigh,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            onSelectedIndexChange = { index ->
                                state.priorityOptions.getOrNull(index)?.let { option ->
                                    onSelectPriority(option.id)
                                }
                            },
                        )
                    }
                }
                item(key = "channel-title", contentType = "section-title") {
                    SmallTitle(
                        text = "通知 Channel",
                        modifier = Modifier.padding(top = 4.dp),
                        insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
                    )
                }
                if (state.channels.isEmpty()) {
                    item(key = "channel-empty", contentType = "channel-empty") {
                        ChannelEmptyCard(ready = state.channelsReady)
                    }
                } else {
                    items(
                        items = state.channels,
                        key = SmartCapsuleAppProfileChannelUi::id,
                        contentType = { "channel" },
                    ) { channel ->
                        ChannelCard(channel = channel, onClick = { onOpenChannel(channel.id) })
                    }
                }
                item(key = "bottom-inset", contentType = "inset") {
                    Spacer(
                        Modifier.height(
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppInformationCard(
    state: SmartCapsuleAppProfileUi,
    appIcon: (@Composable () -> Unit)?,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
        insideMargin = PaddingValues(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (appIcon != null) {
                    appIcon()
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .padding(start = 12.dp, end = 8.dp)
                        .weight(1f),
            ) {
                Text(
                    text = state.appLabel,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight(550),
                    modifier = Modifier.basicMarquee(),
                    maxLines = 1,
                    softWrap = false,
                )
                if (state.appVersionName.isNotBlank() || state.appVersionCode > 0L) {
                    Text(
                        text = "${state.appVersionName} (${state.appVersionCode})",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.basicMarquee(),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Text(
                    text = state.packageName,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.basicMarquee(),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (state.appUid >= 0) {
                val userId = state.appUid / 100000
                val appId = state.appUid % 100000
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (userId != 0) {
                        StatusTagMiuix(
                            label = "USER $userId",
                            backgroundColor = colorScheme.primary.copy(alpha = 0.8f),
                            contentColor = colorScheme.onPrimary,
                        )
                    }
                    StatusTagMiuix(
                        label = "UID $appId",
                        backgroundColor = colorScheme.primary.copy(alpha = 0.8f),
                        contentColor = colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelEmptyCard(ready: Boolean) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
    ) {
        BasicComponent(
            title = if (ready) "未读取到 Channel" else "尚未读取 Channel",
            summary =
                if (ready) {
                    "该应用当前没有可配置的通知 Channel"
                } else {
                    "点击右上角刷新图标从 SystemUI 读取"
                },
        )
    }
}

@Composable
private fun ChannelCard(
    channel: SmartCapsuleAppProfileChannelUi,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
        onClick = { if (channel.enabled) onClick() },
        showIndication = channel.enabled,
    ) {
        BasicComponent(
            title = channel.title,
            summary = channel.summary,
            enabled = channel.enabled,
            startAction = {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            endActions = {
                channel.status?.let { status ->
                    StatusTagMiuix(
                        label = status,
                        backgroundColor = colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        contentColor = colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    )
                }
                val layoutDirection = LocalLayoutDirection.current
                Image(
                    modifier =
                        Modifier
                            .graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            }.padding(start = 8.dp)
                            .size(width = 10.dp, height = 16.dp),
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariantActions),
                )
            },
        )
    }
}
