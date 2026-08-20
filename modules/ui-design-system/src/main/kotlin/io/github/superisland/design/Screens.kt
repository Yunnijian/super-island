package io.github.superisland.design

import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class NotificationSourceOptionUi(
    val id: String,
    val title: String,
    val summary: String,
    val selected: Boolean,
    val packageInfo: PackageInfo? = null,
    val isSystem: Boolean = false,
)

enum class SmartCapsuleAppSortType {
    NAME,
    PACKAGE_NAME,
    INSTALL_TIME,
    UPDATE_TIME,
}

data class SmartCapsuleAppSortConfig(
    val type: SmartCapsuleAppSortType = SmartCapsuleAppSortType.NAME,
    val reversed: Boolean = false,
)

data class IslandPriorityOptionUi(
    val id: String,
    val title: String,
)

data class MediaSourceOptionUi(
    val id: String,
    val title: String,
    val summary: String,
    val selected: Boolean,
)

data class InformationEntryUi(
    val title: String,
    val summary: String,
)

data class ResidentMetricOptionUi(
    val id: String,
    val title: String,
    val summary: String,
    val selected: Boolean,
)

data class ResidentSlotOptionUi(
    val id: String,
    val title: String,
    val summary: String,
    val selected: Boolean,
)

@Composable
fun SmartCapsulePriorityPreference(
    title: String,
    summary: String,
    options: List<IslandPriorityOptionUi>,
    selectedOptionId: String,
    enabled: Boolean,
    onSelectOption: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = options.map(IslandPriorityOptionUi::title),
            selectedIndex = options.indexOfFirst { it.id == selectedOptionId }.coerceAtLeast(0),
            enabled = enabled && options.isNotEmpty(),
            onSelectedIndexChange = { index ->
                options.getOrNull(index)?.let { option -> onSelectOption(option.id) }
            },
        )
    }
}

/** Configuration for the SystemUI-owned resident Super Island. */
@Composable
fun ResidentMonitorConfigurationScreen(
    featureEnabled: Boolean,
    leftIconOptions: List<ResidentSlotOptionUi>,
    rightIconOptions: List<ResidentSlotOptionUi>,
    leftTitleOptions: List<ResidentMetricOptionUi>,
    rightTitleOptions: List<ResidentMetricOptionUi>,
    refreshIntervalSeconds: Int,
    onSelectLeftIcon: (String) -> Unit,
    onSelectRightIcon: (String) -> Unit,
    onSelectLeftTitle: (String) -> Unit,
    onSelectRightTitle: (String) -> Unit,
    onRefreshIntervalChange: (Int) -> Unit,
    onOpenExpandedContent: () -> Unit,
    onFeatureEnabledChange: (Boolean) -> Unit,
    onBackToMonitor: () -> Unit,
) {
    // Keep the drag gesture local to this page. Persisting every pointer sample would synchronously
    // mirror preferences into SystemUI and republish the focus notification dozens of times.
    // This is the same draft-then-commit contract used by KernelSU's Miuix scale slider.
    var refreshIntervalDraft by remember(refreshIntervalSeconds) {
        mutableIntStateOf(refreshIntervalSeconds)
    }
    AppScaffold(
        title = "常驻超级岛",
        largeTitle = "常驻超级岛",
        subtitle = "",
        onBack = onBackToMonitor,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallTitle(
                text = "常驻超级岛",
                insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
            )
            AppFeatureMasterSwitch(
                title = "启用常驻超级岛",
                summary =
                    if (featureEnabled) {
                        "正在通过超级岛显示设备实时状态"
                    } else {
                        "关闭后不会采集或发布新的常驻超级岛内容"
                    },
                checked = featureEnabled,
                onCheckedChange = onFeatureEnabledChange,
            )
            SmallTitle(
                text = "超级岛图标",
                insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = "左侧图标",
                    summary = "默认图标随左侧岛标题变化",
                    items = leftIconOptions.map(ResidentSlotOptionUi::title),
                    selectedIndex = leftIconOptions.indexOfFirst { it.selected }.coerceAtLeast(0),
                    onSelectedIndexChange = { index -> onSelectLeftIcon(leftIconOptions[index].id) },
                )
                OverlayDropdownPreference(
                    title = "右侧图标",
                    summary = "默认图标随右侧岛标题变化",
                    items = rightIconOptions.map(ResidentSlotOptionUi::title),
                    selectedIndex = rightIconOptions.indexOfFirst { it.selected }.coerceAtLeast(0),
                    onSelectedIndexChange = { index -> onSelectRightIcon(rightIconOptions[index].id) },
                )
            }
            SmallTitle(
                text = "显示内容",
                insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = "左侧岛标题",
                    summary = "设置超级岛左侧显示的实时指标",
                    items = leftTitleOptions.map(ResidentMetricOptionUi::title),
                    selectedIndex = leftTitleOptions.indexOfFirst { it.selected }.coerceAtLeast(0),
                    onSelectedIndexChange = { index -> onSelectLeftTitle(leftTitleOptions[index].id) },
                )
                OverlayDropdownPreference(
                    title = "右侧岛标题",
                    summary = "设置超级岛右侧显示的实时指标",
                    items = rightTitleOptions.map(ResidentMetricOptionUi::title),
                    selectedIndex = rightTitleOptions.indexOfFirst { it.selected }.coerceAtLeast(0),
                    onSelectedIndexChange = { index -> onSelectRightTitle(rightTitleOptions[index].id) },
                )
                SliderPreference(
                    value = refreshIntervalDraft.toFloat(),
                    onValueChange = { refreshIntervalDraft = it.roundToInt() },
                    onValueChangeFinished = {
                        if (refreshIntervalDraft != refreshIntervalSeconds) {
                            onRefreshIntervalChange(refreshIntervalDraft)
                        }
                    },
                    title = "标题刷新间隔",
                    summary = "$refreshIntervalDraft 秒；间隔越短耗电越高",
                    valueRange = 1f..60f,
                    steps = 58,
                )
                ArrowPreference(
                    title = "展开内容",
                    summary = "设置常驻胶囊展开后显示的详细信息",
                    onClick = onOpenExpandedContent,
                )
            }
        }
    }
}

/** Read-only details for help, service state and device-capability pages. */
@Composable
fun AppInformationDetailScreen(
    title: String,
    subtitle: String,
    entries: List<InformationEntryUi>,
    backLabel: String,
    onBack: () -> Unit,
) {
    AppScaffold(
        title = title,
        largeTitle = title,
        subtitle = subtitle,
        onBack = onBack,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            entries.forEach { entry ->
                AppInfoCard(entry.title, entry.summary)
            }
            AppTextButton(backLabel, onClick = onBack)
        }
    }
}

@Composable
fun SmartCapsuleAppsScreen(
    enabled: Boolean,
    apps: List<NotificationSourceOptionUi>,
    isRefreshing: Boolean,
    showSystemApps: Boolean,
    sortConfig: SmartCapsuleAppSortConfig,
    onEnabledChange: (Boolean) -> Unit,
    onSelectApp: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleSystemApps: () -> Unit,
    onSortConfigChange: (SmartCapsuleAppSortConfig) -> Unit,
    onBack: () -> Unit,
    appIcon: @Composable (NotificationSourceOptionUi) -> Unit,
) {
    KernelSuSmartCapsuleAppsMiuix(
        enabled = enabled,
        apps = apps,
        isRefreshing = isRefreshing,
        showSystemApps = showSystemApps,
        sortConfig = sortConfig,
        onEnabledChange = onEnabledChange,
        onSelectApp = onSelectApp,
        onRefresh = onRefresh,
        onToggleSystemApps = onToggleSystemApps,
        onSortConfigChange = onSortConfigChange,
        onBack = onBack,
        appIcon = appIcon,
    )
}

@Composable
fun SmartCapsuleAppProfileScreen(
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
    KernelSuSmartCapsuleAppProfileMiuix(
        state = state,
        onEnabledChange = onEnabledChange,
        onFocusOnlyChange = onFocusOnlyChange,
        onSelectPriority = onSelectPriority,
        onAllChannelsChange = onAllChannelsChange,
        onOpenChannel = onOpenChannel,
        onRefreshChannels = onRefreshChannels,
        onBack = onBack,
        appIcon = appIcon,
    )
}

/** Focused repair page for media-island notification access and listener connection. */
@Composable
fun MediaIslandConnectionScreen(
    modeLabel: String,
    notificationAccessStatus: String,
    listenerStatus: String,
    showNotificationAccessAction: Boolean,
    showAutostartSettingsAction: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
    onRefresh: () -> Unit,
    onBackToMediaIsland: () -> Unit,
) {
    AppScaffold(
        title = "媒体岛",
        largeTitle = "通知访问与连接",
        subtitle = modeLabel,
        onBack = onBackToMediaIsland,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppInfoCard("通知访问", notificationAccessStatus)
            AppInfoCard("监听服务", listenerStatus)
            AppInfoCard("连接说明", "媒体岛只读取已允许播放器的公开媒体会话，暂停后会立即结束媒体岛。")
            if (showNotificationAccessAction) {
                AppPrimaryButton("打开通知访问", onClick = onOpenNotificationAccess)
            }
            if (showAutostartSettingsAction) {
                AppPrimaryButton("打开 HyperOS 自启动", onClick = onOpenAutostartSettings)
            }
            AppSecondaryButton("重新连接并刷新", onClick = onRefresh)
            AppTextButton("返回媒体岛", onClick = onBackToMediaIsland)
        }
    }
}

/** The source allow-list is isolated from connection and status information. */
@Composable
fun MediaIslandSourcesScreen(
    modeLabel: String,
    enabledSummary: String,
    candidates: List<MediaSourceOptionUi>,
    hasEnabledSources: Boolean,
    onSelectCandidate: (String) -> Unit,
    onClearRules: () -> Unit,
    onRefresh: () -> Unit,
    onBackToMediaIsland: () -> Unit,
) {
    AppScaffold(
        title = "媒体岛",
        largeTitle = "播放器来源",
        subtitle = modeLabel,
        onBack = onBackToMediaIsland,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppInfoCard("允许的播放器", enabledSummary)
            if (candidates.isEmpty()) {
                AppInfoCard("可选播放器", "尚未发现活动 MediaSession；开始播放后点击刷新")
            } else {
                candidates.forEach { candidate ->
                    AppSelectableInfoCard(
                        title = candidate.title,
                        summary = candidate.summary,
                        selected = candidate.selected,
                        onClick = { onSelectCandidate(candidate.id) },
                    )
                }
            }
            if (hasEnabledSources) {
                AppSecondaryButton("清空播放器允许列表", onClick = onClearRules)
            }
            AppSecondaryButton("刷新媒体会话", onClick = onRefresh)
            AppTextButton("返回媒体岛", onClick = onBackToMediaIsland)
        }
    }
}

/** Presents transient media-island state without mixing it with source configuration. */
@Composable
fun MediaIslandStatusScreen(
    modeLabel: String,
    activeMediaCount: Int,
    operationStatus: String,
    onRefresh: () -> Unit,
    onBackToMediaIsland: () -> Unit,
) {
    AppScaffold(
        title = "媒体岛",
        largeTitle = "运行状态",
        subtitle = modeLabel,
        onBack = onBackToMediaIsland,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppInfoCard("媒体岛状态 · $activeMediaCount 个活动事件", operationStatus)
            AppInfoCard("隐私说明", "只读取允许播放器的公开会话元数据；不会保存播放内容或历史。")
            AppSecondaryButton("刷新媒体会话", onClick = onRefresh)
            AppTextButton("返回媒体岛", onClick = onBackToMediaIsland)
        }
    }
}

/** Public, live battery values are kept separate from monitoring controls and permissions. */
@Composable
fun BatteryRealtimeScreen(
    modeLabel: String,
    batterySummary: String,
    currentSummary: String,
    powerSummary: String,
    temperatureSummary: String,
    batteryLevelPercent: Int?,
    onRefresh: () -> Unit,
    onBackToMonitor: () -> Unit,
) {
    AppScaffold(
        title = "常驻超级岛",
        largeTitle = "实时指标",
        subtitle = modeLabel,
        onBack = onBackToMonitor,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppProgressCard(
                title = "电池状态",
                summary = batterySummary,
                progress = (batteryLevelPercent ?: 0) / 100f,
            )
            AppInfoCard("实时电流", currentSummary)
            AppInfoCard("功耗", powerSummary)
            AppInfoCard("电池温度", temperatureSummary)
            AppSecondaryButton("只刷新读取", onClick = onRefresh)
            AppTextButton("返回常驻超级岛", onClick = onBackToMonitor)
        }
    }
}

/** Start and stop are isolated so all persistent-monitor actions are visible together. */
@Composable
fun BatteryContinuousMonitorScreen(
    modeLabel: String,
    operationStatus: String,
    systemEventSummary: String,
    monitoringActive: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onBackToMonitor: () -> Unit,
) {
    AppScaffold(
        title = "常驻超级岛",
        largeTitle = "持续监控",
        subtitle = modeLabel,
        onBack = onBackToMonitor,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppInfoCard("监控岛状态", operationStatus)
            AppInfoCard("最近系统事件", systemEventSummary)
            AppPrimaryButton(
                text = if (monitoringActive) "持续监控已运行" else "开始持续监控",
                enabled = !monitoringActive,
                onClick = onStart,
            )
            if (monitoringActive) {
                AppSecondaryButton("结束监控岛", onClick = onCancel)
            }
            AppTextButton("返回常驻超级岛", onClick = onBackToMonitor)
        }
    }
}

/** Optional device-event permission is separated from battery telemetry and Root diagnostics. */
@Composable
fun BatteryMonitorEventsScreen(
    modeLabel: String,
    networkEventStatus: String,
    bluetoothEventStatus: String,
    showBluetoothPermissionAction: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onBackToMonitor: () -> Unit,
) {
    AppScaffold(
        title = "常驻超级岛",
        largeTitle = "事件与权限",
        subtitle = modeLabel,
        onBack = onBackToMonitor,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppInfoCard("网络事件", networkEventStatus)
            AppInfoCard("蓝牙设备事件", bluetoothEventStatus)
            if (showBluetoothPermissionAction) {
                AppSecondaryButton("允许蓝牙设备访问", onClick = onRequestBluetoothPermission)
            }
            AppTextButton("返回常驻超级岛", onClick = onBackToMonitor)
        }
    }
}

/** Shows only approved, read-only Root diagnostics. */
@Composable
fun BatteryMonitorDiagnosticsScreen(
    modeLabel: String,
    sourceSummary: String,
    rootThermalSummary: String?,
    rootPerformanceSummary: String?,
    rootFanSummary: String?,
    onReadRootThermal: () -> Unit,
    onReadRootPerformance: () -> Unit,
    onReadRootFan: () -> Unit,
    onBackToMonitor: () -> Unit,
) {
    AppScaffold(
        title = "常驻超级岛",
        largeTitle = "设备诊断",
        subtitle = modeLabel,
        onBack = onBackToMonitor,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppInfoCard("数据来源", sourceSummary)
            rootThermalSummary?.let { AppInfoCard("Root 温度自检", it) }
            rootPerformanceSummary?.let { AppInfoCard("Root 频率自检", it) }
            rootFanSummary?.let { AppInfoCard("Root 风扇自检", it) }
            AppSecondaryButton("读取 Root 温度", onClick = onReadRootThermal)
            AppSecondaryButton("读取 Root 频率", onClick = onReadRootPerformance)
            AppSecondaryButton("读取 Root 风扇", onClick = onReadRootFan)
            AppTextButton("返回常驻超级岛", onClick = onBackToMonitor)
        }
    }
}
