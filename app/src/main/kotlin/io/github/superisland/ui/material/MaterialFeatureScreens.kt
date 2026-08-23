package io.github.superisland.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.superisland.design.DirectoryGroupUi
import io.github.superisland.design.DirectoryIcon
import io.github.superisland.design.InformationEntryUi
import io.github.superisland.design.IslandPriorityOptionUi
import io.github.superisland.design.NotificationSourceOptionUi
import io.github.superisland.design.SmartCapsuleAppProfileUi
import io.github.superisland.design.SmartCapsuleAppSortConfig
import io.github.superisland.design.ResidentMetricOptionUi
import io.github.superisland.design.ResidentSlotOptionUi
import kotlin.math.roundToInt
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedRadioItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaterialFeatureScreen(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    scrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior =
        androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            androidx.compose.material3.rememberTopAppBarState(),
        )
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Column {
                        Text(title)
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    onBack?.let { navigateBack ->
                        TopBarBackButton(onClick = navigateBack, contentDescription = "返回")
                    }
                },
                actions = actions,
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        val contentModifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        Column(
            modifier =
                if (scrollContent) {
                    contentModifier.verticalScroll(rememberScrollState())
                } else {
                    contentModifier
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private data class MaterialInformationRow(
    val title: String,
    val summary: String,
)

@Composable
private fun MaterialInformationGroup(
    rows: List<MaterialInformationRow>,
    title: String = "",
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = title,
        content =
            rows.map { row ->
                {
                    SegmentedListItem(
                        headlineContent = { Text(row.title) },
                        supportingContent = { Text(row.summary) },
                    )
                }
            },
    )
}

@Composable
private fun MaterialSelectableInformationGroup(
    rows: List<MaterialSelectableRow>,
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        content =
            rows.map { row ->
                {
                    SegmentedRadioItem(
                        title = row.title,
                        summary = row.summary,
                        selected = row.selected,
                        onClick = row.onClick,
                    )
                }
            },
    )
}

private data class MaterialSelectableRow(
    val title: String,
    val summary: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun MaterialPrimaryAction(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(text)
    }
}

@Composable
private fun MaterialSecondaryAction(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(text)
    }
}

@Composable
private fun MaterialTextAction(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Text(text)
    }
}

@Composable
private fun MaterialProgressInformation(
    title: String,
    summary: String,
    progress: Float,
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        content =
            listOf(
                {
                    SegmentedListItem(
                        headlineContent = { Text(title) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(summary)
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                    )
                },
            ),
    )
}

@Composable
fun MaterialAppFeatureMasterSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        content =
            listOf(
                {
                    SegmentedSwitchItem(
                        title = title,
                        summary = summary,
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        enabled = enabled,
                    )
                },
            ),
    )
}

@Composable
fun MaterialSmartCapsulePriorityPreference(
    title: String,
    summary: String,
    options: List<IslandPriorityOptionUi>,
    selectedOptionId: String,
    enabled: Boolean,
    onSelectOption: (String) -> Unit,
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        content =
            listOf(
                {
                    SegmentedDropdownItem(
                        title = title,
                        summary = summary,
                        items = options.map(IslandPriorityOptionUi::title),
                        selectedIndex =
                            options.indexOfFirst { it.id == selectedOptionId }.coerceAtLeast(0),
                        enabled = enabled && options.isNotEmpty(),
                        onItemSelected = { index ->
                            options.getOrNull(index)?.let { option ->
                                onSelectOption(option.id)
                            }
                        },
                    )
                },
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialAppDirectoryDetailScreen(
    title: String,
    subtitle: String,
    groups: List<DirectoryGroupUi>,
    header: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onEntrySelected: (String) -> Unit,
) {
    val scrollBehavior =
        androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            androidx.compose.material3.rememberTopAppBarState(),
        )
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Column {
                        Text(title)
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    onBack?.let { navigateBack ->
                        TopBarBackButton(onClick = navigateBack, contentDescription = "返回")
                    }
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            header?.let { headerContent ->
                item(key = "detail-header") { headerContent() }
            }
            itemsIndexed(
                items = groups,
                key = { _, group -> group.title },
            ) { _, group ->
                SegmentedColumn(
                    modifier = Modifier.fillMaxWidth(),
                    title = group.title,
                    content =
                        group.entries.map { entry ->
                            {
                                SegmentedListItem(
                                    onClick = if (entry.enabled) ({ onEntrySelected(entry.id) }) else null,
                                    enabled = entry.enabled,
                                    headlineContent = { Text(entry.title) },
                                    supportingContent = { Text(entry.summary) },
                                    leadingContent = {
                                        Icon(
                                            imageVector = entry.icon.materialFeatureIcon(),
                                            contentDescription = entry.title,
                                        )
                                    },
                                    trailingContent = {
                                        Column {
                                            entry.status?.let { Text(it) }
                                            if (entry.enabled && entry.showChevron) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        },
                )
            }
        }
    }
}

@Composable
fun MaterialAppInformationDetailScreen(
    title: String,
    subtitle: String,
    entries: List<InformationEntryUi>,
    backLabel: String,
    onBack: () -> Unit,
) {
    MaterialFeatureScreen(title = title, subtitle = subtitle, onBack = onBack) {
        MaterialInformationGroup(
            entries.map { entry -> MaterialInformationRow(entry.title, entry.summary) },
        )
        MaterialTextAction(backLabel, onClick = onBack)
    }
}

private fun DirectoryIcon.materialFeatureIcon(): ImageVector =
    when (this) {
        DirectoryIcon.HOME -> Icons.Filled.Home
        DirectoryIcon.SUPER_ISLAND,
        DirectoryIcon.NOTIFICATION,
        -> Icons.Filled.Notifications
        DirectoryIcon.EXTENSIONS,
        DirectoryIcon.LAB,
        -> Icons.Filled.Extension
        DirectoryIcon.SETTINGS -> Icons.Filled.Settings
        DirectoryIcon.PROFILE -> Icons.Filled.Person
        DirectoryIcon.MEDIA -> Icons.Filled.MusicNote
        DirectoryIcon.MONITOR -> Icons.Filled.Timer
        DirectoryIcon.APPEARANCE -> Icons.Filled.Palette
        DirectoryIcon.PRIVACY -> Icons.Filled.Lock
        DirectoryIcon.DEVICE -> Icons.Filled.Dashboard
        DirectoryIcon.INFO -> Icons.Filled.Info
    }

@Composable
fun MaterialSmartCapsuleAppsScreen(
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
) {
    KernelSuSmartCapsuleAppsMaterial(
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
    )
}

@Composable
fun MaterialSmartCapsuleAppProfileScreen(
    state: SmartCapsuleAppProfileUi,
    onEnabledChange: (Boolean) -> Unit,
    onFocusOnlyChange: (Boolean) -> Unit,
    onSelectPriority: (String) -> Unit,
    onAllChannelsChange: (Boolean) -> Unit,
    onOpenChannel: (String) -> Unit,
    onRefreshChannels: () -> Unit,
    onBack: () -> Unit,
) {
    KernelSuSmartCapsuleAppProfileMaterial(
        state = state,
        onEnabledChange = onEnabledChange,
        onFocusOnlyChange = onFocusOnlyChange,
        onSelectPriority = onSelectPriority,
        onAllChannelsChange = onAllChannelsChange,
        onOpenChannel = onOpenChannel,
        onRefreshChannels = onRefreshChannels,
        onBack = onBack,
    )
}

@Composable
fun MaterialBatteryRealtimeScreen(
    modeLabel: String,
    batterySummary: String,
    currentSummary: String,
    powerSummary: String,
    temperatureSummary: String,
    batteryLevelPercent: Int?,
    onRefresh: () -> Unit,
    onBackToMonitor: () -> Unit,
) {
    MaterialFeatureScreen(title = "实时指标", subtitle = modeLabel, onBack = onBackToMonitor) {
        MaterialProgressInformation(
            title = "电池状态",
            summary = batterySummary,
            progress = (batteryLevelPercent ?: 0) / 100f,
        )
        MaterialInformationGroup(
            listOf(
                MaterialInformationRow("实时电流", currentSummary),
                MaterialInformationRow("功耗", powerSummary),
                MaterialInformationRow("电池温度", temperatureSummary),
            ),
        )
        MaterialSecondaryAction("只刷新读取", onClick = onRefresh)
        MaterialTextAction("返回常驻超级岛", onClick = onBackToMonitor)
    }
}

@Composable
fun MaterialBatteryContinuousMonitorScreen(
    modeLabel: String,
    operationStatus: String,
    systemEventSummary: String,
    monitoringActive: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onBackToMonitor: () -> Unit,
) {
    MaterialFeatureScreen(title = "持续监控", subtitle = modeLabel, onBack = onBackToMonitor) {
        MaterialInformationGroup(
            listOf(
                MaterialInformationRow("监控岛状态", operationStatus),
                MaterialInformationRow("最近系统事件", systemEventSummary),
            ),
        )
        MaterialPrimaryAction(
            text = if (monitoringActive) "持续监控已运行" else "开始持续监控",
            enabled = !monitoringActive,
            onClick = onStart,
        )
        if (monitoringActive) {
            MaterialSecondaryAction("结束监控岛", onClick = onCancel)
        }
        MaterialTextAction("返回常驻超级岛", onClick = onBackToMonitor)
    }
}

@Composable
fun MaterialBatteryMonitorEventsScreen(
    modeLabel: String,
    networkEventStatus: String,
    bluetoothEventStatus: String,
    showBluetoothPermissionAction: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onBackToMonitor: () -> Unit,
) {
    MaterialFeatureScreen(title = "事件与权限", subtitle = modeLabel, onBack = onBackToMonitor) {
        MaterialInformationGroup(
            listOf(
                MaterialInformationRow("网络事件", networkEventStatus),
                MaterialInformationRow("蓝牙设备事件", bluetoothEventStatus),
            ),
        )
        if (showBluetoothPermissionAction) {
            MaterialSecondaryAction("允许蓝牙设备访问", onClick = onRequestBluetoothPermission)
        }
        MaterialTextAction("返回常驻超级岛", onClick = onBackToMonitor)
    }
}

@Composable
fun MaterialBatteryMonitorDiagnosticsScreen(
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
    MaterialFeatureScreen(title = "设备诊断", subtitle = modeLabel, onBack = onBackToMonitor) {
        MaterialInformationGroup(
            buildList {
                add(MaterialInformationRow("数据来源", sourceSummary))
                rootThermalSummary?.let { add(MaterialInformationRow("Root 温度自检", it)) }
                rootPerformanceSummary?.let { add(MaterialInformationRow("Root 频率自检", it)) }
                rootFanSummary?.let { add(MaterialInformationRow("Root 风扇自检", it)) }
            },
        )
        MaterialSecondaryAction("读取 Root 温度", onClick = onReadRootThermal)
        MaterialSecondaryAction("读取 Root 频率", onClick = onReadRootPerformance)
        MaterialSecondaryAction("读取 Root 风扇", onClick = onReadRootFan)
        MaterialTextAction("返回常驻超级岛", onClick = onBackToMonitor)
    }
}

@Composable
fun MaterialResidentMonitorConfigurationScreen(
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
    // Match KernelSU's Material slider state ownership: render a local draft while dragging and
    // commit the final value once, after the gesture finishes.
    var refreshIntervalDraft by remember(refreshIntervalSeconds) {
        mutableIntStateOf(refreshIntervalSeconds)
    }
    MaterialFeatureScreen(
        title = "常驻超级岛",
        subtitle = "",
        onBack = onBackToMonitor,
    ) {
        SegmentedColumn(
            modifier = Modifier.fillMaxWidth(),
            title = "常驻超级岛",
            content =
                listOf(
                    {
                        SegmentedSwitchItem(
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
                    },
                ),
        )
        SegmentedColumn(
            modifier = Modifier.fillMaxWidth(),
            title = "超级岛图标",
            content =
                listOf(
                    {
                        SegmentedDropdownItem(
                            title = "左侧图标",
                            summary = "默认图标随左侧岛标题变化",
                            items = leftIconOptions.map(ResidentSlotOptionUi::title),
                            selectedIndex = leftIconOptions.indexOfFirst { it.selected }.coerceAtLeast(0),
                            enabled = leftIconOptions.isNotEmpty(),
                            onItemSelected = { index ->
                                leftIconOptions.getOrNull(index)?.let { onSelectLeftIcon(it.id) }
                            },
                        )
                    },
                    {
                        SegmentedDropdownItem(
                            title = "右侧图标",
                            summary = "默认图标随右侧岛标题变化",
                            items = rightIconOptions.map(ResidentSlotOptionUi::title),
                            selectedIndex = rightIconOptions.indexOfFirst { it.selected }.coerceAtLeast(0),
                            enabled = rightIconOptions.isNotEmpty(),
                            onItemSelected = { index ->
                                rightIconOptions.getOrNull(index)?.let { onSelectRightIcon(it.id) }
                            },
                        )
                    },
                ),
        )
        SegmentedColumn(
            modifier = Modifier.fillMaxWidth(),
            title = "显示内容",
            content =
                listOf(
                    {
                        SegmentedDropdownItem(
                            title = "左侧岛标题",
                            summary = "设置超级岛左侧显示的实时指标",
                            items = leftTitleOptions.map(ResidentMetricOptionUi::title),
                            selectedIndex = leftTitleOptions.indexOfFirst { it.selected }.coerceAtLeast(0),
                            enabled = leftTitleOptions.isNotEmpty(),
                            onItemSelected = { index ->
                                leftTitleOptions.getOrNull(index)?.let { onSelectLeftTitle(it.id) }
                            },
                        )
                    },
                    {
                        SegmentedDropdownItem(
                            title = "右侧岛标题",
                            summary = "设置超级岛右侧显示的实时指标",
                            items = rightTitleOptions.map(ResidentMetricOptionUi::title),
                            selectedIndex = rightTitleOptions.indexOfFirst { it.selected }.coerceAtLeast(0),
                            enabled = rightTitleOptions.isNotEmpty(),
                            onItemSelected = { index ->
                                rightTitleOptions.getOrNull(index)?.let { onSelectRightTitle(it.id) }
                            },
                        )
                    },
                    {
                        SegmentedListItem(
                            headlineContent = { Text("标题刷新间隔") },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("$refreshIntervalDraft 秒；间隔越短耗电越高")
                                    Slider(
                                        value = refreshIntervalDraft.toFloat().coerceIn(1f, 60f),
                                        onValueChange = { value -> refreshIntervalDraft = value.roundToInt() },
                                        onValueChangeFinished = {
                                            if (refreshIntervalDraft != refreshIntervalSeconds) {
                                                onRefreshIntervalChange(refreshIntervalDraft)
                                            }
                                        },
                                        valueRange = 1f..60f,
                                        steps = 58,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = onOpenExpandedContent,
                            headlineContent = { Text("展开内容") },
                            supportingContent = { Text("设置常驻胶囊展开后显示的详细信息") },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                )
                            },
                        )
                    },
                ),
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
