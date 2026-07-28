package io.github.superisland.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import io.github.superisland.design.AppDirectoryDetailScreen as MiuixAppDirectoryDetailScreen
import io.github.superisland.design.AppFeatureMasterSwitch as MiuixAppFeatureMasterSwitch
import io.github.superisland.design.AppInformationDetailScreen as MiuixAppInformationDetailScreen
import io.github.superisland.design.BatteryContinuousMonitorScreen as MiuixBatteryContinuousMonitorScreen
import io.github.superisland.design.BatteryMonitorDiagnosticsScreen as MiuixBatteryMonitorDiagnosticsScreen
import io.github.superisland.design.BatteryMonitorEventsScreen as MiuixBatteryMonitorEventsScreen
import io.github.superisland.design.BatteryRealtimeScreen as MiuixBatteryRealtimeScreen
import io.github.superisland.design.DirectoryGroupUi
import io.github.superisland.design.FocusNotificationCapabilityScreen as MiuixFocusNotificationCapabilityScreen
import io.github.superisland.design.FocusNotificationEventScreen as MiuixFocusNotificationEventScreen
import io.github.superisland.design.InformationEntryUi
import io.github.superisland.design.IslandPriorityOptionUi
import io.github.superisland.design.MediaIslandConnectionScreen as MiuixMediaIslandConnectionScreen
import io.github.superisland.design.MediaIslandSourcesScreen as MiuixMediaIslandSourcesScreen
import io.github.superisland.design.MediaIslandStatusScreen as MiuixMediaIslandStatusScreen
import io.github.superisland.design.MediaSourceOptionUi
import io.github.superisland.design.NotificationSourceOptionUi
import io.github.superisland.design.SmartCapsuleAppSortConfig
import io.github.superisland.design.SmartCapsuleAppProfileUi
import io.github.superisland.design.ResidentMetricOptionUi
import io.github.superisland.design.ResidentMonitorConfigurationScreen as MiuixResidentMonitorConfigurationScreen
import io.github.superisland.design.ResidentSlotOptionUi
import io.github.superisland.design.SmartCapsuleAppsScreen as MiuixSmartCapsuleAppsScreen
import io.github.superisland.design.SmartCapsuleAppProfileScreen as MiuixSmartCapsuleAppProfileScreen
import io.github.superisland.design.SmartCapsulePriorityPreference as MiuixSmartCapsulePriorityPreference
import io.github.superisland.ui.material.MaterialAppDirectoryDetailScreen
import io.github.superisland.ui.material.MaterialAppFeatureMasterSwitch
import io.github.superisland.ui.material.MaterialAppInformationDetailScreen
import io.github.superisland.ui.material.MaterialBatteryContinuousMonitorScreen
import io.github.superisland.ui.material.MaterialBatteryMonitorDiagnosticsScreen
import io.github.superisland.ui.material.MaterialBatteryMonitorEventsScreen
import io.github.superisland.ui.material.MaterialBatteryRealtimeScreen
import io.github.superisland.ui.material.MaterialFocusNotificationCapabilityScreen
import io.github.superisland.ui.material.MaterialFocusNotificationEventScreen
import io.github.superisland.ui.material.MaterialMediaIslandConnectionScreen
import io.github.superisland.ui.material.MaterialMediaIslandSourcesScreen
import io.github.superisland.ui.material.MaterialMediaIslandStatusScreen
import io.github.superisland.ui.material.MaterialResidentMonitorConfigurationScreen
import io.github.superisland.ui.material.MaterialSmartCapsuleAppsScreen
import io.github.superisland.ui.material.MaterialSmartCapsuleAppProfileScreen
import io.github.superisland.ui.material.MaterialSmartCapsulePriorityPreference
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.AppIconImage

@Composable
fun AppFeatureMasterSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixAppFeatureMasterSwitch(
                title = title,
                summary = summary,
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        UiMode.Material ->
            MaterialAppFeatureMasterSwitch(
                title = title,
                summary = summary,
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
    }
}

@Composable
fun SmartCapsulePriorityPreference(
    title: String,
    summary: String,
    options: List<IslandPriorityOptionUi>,
    selectedOptionId: String,
    enabled: Boolean,
    onSelectOption: (String) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixSmartCapsulePriorityPreference(
                title = title,
                summary = summary,
                options = options,
                selectedOptionId = selectedOptionId,
                enabled = enabled,
                onSelectOption = onSelectOption,
            )
        UiMode.Material ->
            MaterialSmartCapsulePriorityPreference(
                title = title,
                summary = summary,
                options = options,
                selectedOptionId = selectedOptionId,
                enabled = enabled,
                onSelectOption = onSelectOption,
            )
    }
}

@Composable
fun AppDirectoryDetailScreen(
    title: String,
    subtitle: String,
    groups: List<DirectoryGroupUi>,
    header: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onEntrySelected: (String) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixAppDirectoryDetailScreen(
                title = title,
                subtitle = subtitle,
                groups = groups,
                header = header,
                onBack = onBack,
                onEntrySelected = onEntrySelected,
            )
        UiMode.Material ->
            MaterialAppDirectoryDetailScreen(
                title = title,
                subtitle = subtitle,
                groups = groups,
                header = header,
                onBack = onBack,
                onEntrySelected = onEntrySelected,
            )
    }
}

@Composable
fun AppInformationDetailScreen(
    title: String,
    subtitle: String,
    entries: List<InformationEntryUi>,
    backLabel: String,
    onBack: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixAppInformationDetailScreen(title, subtitle, entries, backLabel, onBack)
        UiMode.Material -> MaterialAppInformationDetailScreen(title, subtitle, entries, backLabel, onBack)
    }
}

@Composable
fun FocusNotificationCapabilityScreen(
    modeLabel: String,
    notificationStatus: String,
    focusProtocolStatus: String,
    showNotificationPermissionAction: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRefresh: () -> Unit,
    onBackToFocusTest: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixFocusNotificationCapabilityScreen(
                modeLabel,
                notificationStatus,
                focusProtocolStatus,
                showNotificationPermissionAction,
                onRequestNotificationPermission,
                onRefresh,
                onBackToFocusTest,
            )
        UiMode.Material ->
            MaterialFocusNotificationCapabilityScreen(
                modeLabel,
                notificationStatus,
                focusProtocolStatus,
                showNotificationPermissionAction,
                onRequestNotificationPermission,
                onRefresh,
                onBackToFocusTest,
            )
    }
}

@Composable
fun FocusNotificationEventScreen(
    modeLabel: String,
    operationStatus: String,
    progress: Int,
    canPublish: Boolean,
    onPublish: () -> Unit,
    onAdvance: () -> Unit,
    onCancel: () -> Unit,
    onBackToFocusTest: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixFocusNotificationEventScreen(
                modeLabel,
                operationStatus,
                progress,
                canPublish,
                onPublish,
                onAdvance,
                onCancel,
                onBackToFocusTest,
            )
        UiMode.Material ->
            MaterialFocusNotificationEventScreen(
                modeLabel,
                operationStatus,
                progress,
                canPublish,
                onPublish,
                onAdvance,
                onCancel,
                onBackToFocusTest,
            )
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
) {
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixSmartCapsuleAppsScreen(
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
                appIcon = { app ->
                    app.packageInfo?.let { packageInfo ->
                        AppIconImage(
                            packageInfo = packageInfo,
                            label = app.title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
            )
        UiMode.Material ->
            MaterialSmartCapsuleAppsScreen(
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
) {
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixSmartCapsuleAppProfileScreen(
                state = state,
                onEnabledChange = onEnabledChange,
                onFocusOnlyChange = onFocusOnlyChange,
                onSelectPriority = onSelectPriority,
                onAllChannelsChange = onAllChannelsChange,
                onOpenChannel = onOpenChannel,
                onRefreshChannels = onRefreshChannels,
                onBack = onBack,
                appIcon =
                    state.packageInfo?.let { packageInfo ->
                        @Composable {
                            AppIconImage(
                                packageInfo = packageInfo,
                                label = state.appLabel,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
            )
        UiMode.Material ->
            MaterialSmartCapsuleAppProfileScreen(
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
}

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
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixMediaIslandConnectionScreen(
                modeLabel,
                notificationAccessStatus,
                listenerStatus,
                showNotificationAccessAction,
                showAutostartSettingsAction,
                onOpenNotificationAccess,
                onOpenAutostartSettings,
                onRefresh,
                onBackToMediaIsland,
            )
        UiMode.Material ->
            MaterialMediaIslandConnectionScreen(
                modeLabel,
                notificationAccessStatus,
                listenerStatus,
                showNotificationAccessAction,
                showAutostartSettingsAction,
                onOpenNotificationAccess,
                onOpenAutostartSettings,
                onRefresh,
                onBackToMediaIsland,
            )
    }
}

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
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixMediaIslandSourcesScreen(
                modeLabel,
                enabledSummary,
                candidates,
                hasEnabledSources,
                onSelectCandidate,
                onClearRules,
                onRefresh,
                onBackToMediaIsland,
            )
        UiMode.Material ->
            MaterialMediaIslandSourcesScreen(
                modeLabel,
                enabledSummary,
                candidates,
                hasEnabledSources,
                onSelectCandidate,
                onClearRules,
                onRefresh,
                onBackToMediaIsland,
            )
    }
}

@Composable
fun MediaIslandStatusScreen(
    modeLabel: String,
    activeMediaCount: Int,
    operationStatus: String,
    onRefresh: () -> Unit,
    onBackToMediaIsland: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixMediaIslandStatusScreen(
                modeLabel,
                activeMediaCount,
                operationStatus,
                onRefresh,
                onBackToMediaIsland,
            )
        UiMode.Material ->
            MaterialMediaIslandStatusScreen(
                modeLabel,
                activeMediaCount,
                operationStatus,
                onRefresh,
                onBackToMediaIsland,
            )
    }
}

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
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixBatteryRealtimeScreen(
                modeLabel,
                batterySummary,
                currentSummary,
                powerSummary,
                temperatureSummary,
                batteryLevelPercent,
                onRefresh,
                onBackToMonitor,
            )
        UiMode.Material ->
            MaterialBatteryRealtimeScreen(
                modeLabel,
                batterySummary,
                currentSummary,
                powerSummary,
                temperatureSummary,
                batteryLevelPercent,
                onRefresh,
                onBackToMonitor,
            )
    }
}

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
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixBatteryContinuousMonitorScreen(
                modeLabel,
                operationStatus,
                systemEventSummary,
                monitoringActive,
                onStart,
                onCancel,
                onBackToMonitor,
            )
        UiMode.Material ->
            MaterialBatteryContinuousMonitorScreen(
                modeLabel,
                operationStatus,
                systemEventSummary,
                monitoringActive,
                onStart,
                onCancel,
                onBackToMonitor,
            )
    }
}

@Composable
fun BatteryMonitorEventsScreen(
    modeLabel: String,
    networkEventStatus: String,
    bluetoothEventStatus: String,
    showBluetoothPermissionAction: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onBackToMonitor: () -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixBatteryMonitorEventsScreen(
                modeLabel,
                networkEventStatus,
                bluetoothEventStatus,
                showBluetoothPermissionAction,
                onRequestBluetoothPermission,
                onBackToMonitor,
            )
        UiMode.Material ->
            MaterialBatteryMonitorEventsScreen(
                modeLabel,
                networkEventStatus,
                bluetoothEventStatus,
                showBluetoothPermissionAction,
                onRequestBluetoothPermission,
                onBackToMonitor,
            )
    }
}

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
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixBatteryMonitorDiagnosticsScreen(
                modeLabel,
                sourceSummary,
                rootThermalSummary,
                rootPerformanceSummary,
                rootFanSummary,
                onReadRootThermal,
                onReadRootPerformance,
                onReadRootFan,
                onBackToMonitor,
            )
        UiMode.Material ->
            MaterialBatteryMonitorDiagnosticsScreen(
                modeLabel = modeLabel,
                sourceSummary = sourceSummary,
                rootThermalSummary = rootThermalSummary,
                rootPerformanceSummary = rootPerformanceSummary,
                rootFanSummary = rootFanSummary,
                onReadRootThermal = onReadRootThermal,
                onReadRootPerformance = onReadRootPerformance,
                onReadRootFan = onReadRootFan,
                onBackToMonitor = onBackToMonitor,
            )
    }
}

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
    when (LocalUiMode.current) {
        UiMode.Miuix ->
            MiuixResidentMonitorConfigurationScreen(
                featureEnabled = featureEnabled,
                leftIconOptions = leftIconOptions,
                rightIconOptions = rightIconOptions,
                leftTitleOptions = leftTitleOptions,
                rightTitleOptions = rightTitleOptions,
                refreshIntervalSeconds = refreshIntervalSeconds,
                onSelectLeftIcon = onSelectLeftIcon,
                onSelectRightIcon = onSelectRightIcon,
                onSelectLeftTitle = onSelectLeftTitle,
                onSelectRightTitle = onSelectRightTitle,
                onRefreshIntervalChange = onRefreshIntervalChange,
                onOpenExpandedContent = onOpenExpandedContent,
                onFeatureEnabledChange = onFeatureEnabledChange,
                onBackToMonitor = onBackToMonitor,
            )
        UiMode.Material ->
            MaterialResidentMonitorConfigurationScreen(
                featureEnabled = featureEnabled,
                leftIconOptions = leftIconOptions,
                rightIconOptions = rightIconOptions,
                leftTitleOptions = leftTitleOptions,
                rightTitleOptions = rightTitleOptions,
                refreshIntervalSeconds = refreshIntervalSeconds,
                onSelectLeftIcon = onSelectLeftIcon,
                onSelectRightIcon = onSelectRightIcon,
                onSelectLeftTitle = onSelectLeftTitle,
                onSelectRightTitle = onSelectRightTitle,
                onRefreshIntervalChange = onRefreshIntervalChange,
                onOpenExpandedContent = onOpenExpandedContent,
                onFeatureEnabledChange = onFeatureEnabledChange,
                onBackToMonitor = onBackToMonitor,
            )
    }
}
