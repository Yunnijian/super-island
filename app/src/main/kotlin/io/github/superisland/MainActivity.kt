package io.github.superisland

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.superisland.design.AppPrimaryTab
import io.github.superisland.design.KernelSuMiuixPrimaryPagerScaffold
import io.github.superisland.design.KernelSuMiuixRootHost
import io.github.superisland.design.LocalDesignEnableBlur
import io.github.superisland.design.DirectoryEntryUi
import io.github.superisland.design.DirectoryGroupUi
import io.github.superisland.design.DirectoryIcon
import io.github.superisland.design.InformationEntryUi
import io.github.superisland.design.IslandPriorityOptionUi
import io.github.superisland.design.SmartCapsuleAppProfileChannelUi
import io.github.superisland.design.SmartCapsuleAppProfileUi
import io.github.superisland.design.ResidentMetricOptionUi
import io.github.superisland.design.ResidentSlotOptionUi
import io.github.superisland.model.AppRule
import io.github.superisland.model.BatteryChargeState
import io.github.superisland.model.AppAppearanceSettings
import io.github.superisland.model.AppUiMode
import io.github.superisland.model.BatteryMetricSnapshot
import io.github.superisland.model.BatterySystemEvent
import io.github.superisland.model.ChannelSelection
import io.github.superisland.model.FocusNotificationRequest
import io.github.superisland.model.FocusDisplayMode
import io.github.superisland.model.IslandPriority
import io.github.superisland.model.RuntimeEnvironmentSnapshot
import io.github.superisland.model.ResidentIslandIcon
import io.github.superisland.model.ResidentMetricFormatter
import io.github.superisland.model.ResidentMetricKey
import io.github.superisland.model.ResidentMonitorConfig
import io.github.superisland.model.ThermalDiagnosticSnapshot
import io.github.superisland.model.WarsawFanMetricSnapshot
import io.github.superisland.model.WarsawPerformanceMetricSnapshot
import io.github.superisland.source.notification.NotificationProxyController
import io.github.superisland.source.root.RootDeviceAdapterRegistry
import io.github.superisland.source.root.RootDeviceFeature
import io.github.superisland.source.root.WarsawFanMetricSource
import io.github.superisland.source.root.RootThermalMetricSource
import io.github.superisland.source.root.WarsawPerformanceMetricSource
import io.github.superisland.source.system.BatteryMetricSource
import io.github.superisland.ui.extensions.ExtensionsMiuix
import io.github.superisland.ui.extensions.MiShareFolderExtensionScreen
import io.github.superisland.ui.extensions.ScreenRecordingExtensionScreen
import io.github.superisland.ui.home.HomeScreen
import io.github.superisland.ui.lyric.LyricScreen
import io.github.superisland.ui.profile.ProfileMiuix
import io.github.superisland.ui.settings.SettingsMiuix
import io.github.superisland.ui.superisland.SuperIslandMiuix
import io.github.superisland.ui.superisland.CapsuleAppearanceScreen
import io.github.superisland.ui.adaptive.AppDirectoryDetailScreen
import io.github.superisland.ui.adaptive.AppFeatureMasterSwitch
import io.github.superisland.ui.adaptive.AppInformationDetailScreen
import io.github.superisland.ui.adaptive.BatteryContinuousMonitorScreen
import io.github.superisland.ui.adaptive.BatteryMonitorDiagnosticsScreen
import io.github.superisland.ui.adaptive.BatteryMonitorEventsScreen
import io.github.superisland.ui.adaptive.BatteryRealtimeScreen
import io.github.superisland.ui.adaptive.ResidentMonitorConfigurationScreen
import io.github.superisland.ui.adaptive.SmartCapsuleAppsScreen
import io.github.superisland.ui.adaptive.SmartCapsuleAppProfileScreen
import io.github.superisland.ui.adaptive.SmartCapsulePriorityPreference
import io.github.superisland.ui.navigation.AppDestination
import io.github.superisland.ui.navigation.AppRoute
import io.github.superisland.ui.navigation.SmartCapsuleChannelDetailDestination
import io.github.superisland.ui.navigation.SmartCapsuleChannelsDestination
import io.github.superisland.ui.navigation.isDetail
import io.github.superisland.ui.navigation.isPrimaryTab
import io.github.superisland.ui.navigation.rememberMainPagerState
import io.github.superisland.ui.navigation.rememberAppNavigator
import io.github.superisland.appearance.AppAppearanceViewModel
import io.github.superisland.appearance.AppAppearanceSettingsViewModel
import io.github.superisland.appearance.LocalAppAppearanceViewModel
import io.github.superisland.appearance.LocalAppAppearanceSettingsViewModel
import io.github.superisland.appearance.toKernelSuAppSettings
import io.github.superisland.appearance.toKernelSuUiMode
import io.github.superisland.ui.miuix.KernelSuMiuixAppearanceSettingsPage
import io.github.superisland.ui.material.MaterialAppearanceSettingsPage
import io.github.superisland.ui.material.KernelSuMaterialRootHost
import io.github.superisland.ui.material.MaterialPrimaryPage
import io.github.superisland.ui.material.MaterialPrimaryPagerScaffold
import io.github.superisland.ui.resident.ResidentExpandedContentScreen
import io.github.superisland.ui.resident.residentExpandedPreviewValues
import me.weishu.kernelsu.ui.theme.KernelSUTheme
import me.weishu.kernelsu.ui.theme.LocalEnableBlur
import me.weishu.kernelsu.ui.theme.LocalEnableFloatingBottomBar
import me.weishu.kernelsu.ui.theme.LocalEnableFloatingBottomBarBlur
import me.weishu.kernelsu.ui.theme.LocalColorMode
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.util.rememberContentReady
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ROOT_MODE_LABEL = "Root + LSPosed"

class MainActivity : ComponentActivity() {
    private var resumeGeneration by mutableIntStateOf(0)
    private var launchDestination by mutableStateOf(AppDestination.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchDestination = intent.debugDestination()
        runDebugBatteryCommand(intent)
        setContent {
            val appearanceViewModel: AppAppearanceViewModel = viewModel()
            val appearanceSettingsViewModel: AppAppearanceSettingsViewModel = viewModel()
            val appearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()
            val kernelSuAppSettings = appearance.toKernelSuAppSettings()
            val kernelSuUiMode = appearance.uiMode.toKernelSuUiMode()
            val darkMode = kernelSuAppSettings.colorMode.isDark || (kernelSuAppSettings.colorMode.isSystem && systemDarkTheme)
            val onAppearanceChange =
                remember(appearanceSettingsViewModel) {
                    appearanceSettingsViewModel::update
                }
            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle =
                        SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ) { darkMode },
                    navigationBarStyle =
                        SystemBarStyle.auto(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }
            val systemDensity = LocalDensity.current
            val scaledDensity =
                remember(systemDensity, appearance.pageScale) {
                    Density(
                        density = systemDensity.density * appearance.pageScale,
                        fontScale = systemDensity.fontScale,
                    )
                }
            // Hoist navigation state above the skin host so a uiMode switch (Miuix<->Material)
            // does not dispose and recreate the backStack. SuperIslandApp's remember* would
            // otherwise be torn down when KernelSuRootHost switches branches.
            val navigator = rememberAppNavigator(AppDestination.MAIN)
            val pagerState = rememberPagerState(
                initialPage = this@MainActivity.launchDestination.primaryTab.pageIndex,
                pageCount = { AppPrimaryTab.entries.size },
            )
            val mainPagerState = rememberMainPagerState(pagerState)
            CompositionLocalProvider(
                LocalDensity provides scaledDensity,
                LocalAppAppearanceViewModel provides appearanceViewModel,
                LocalAppAppearanceSettingsViewModel provides appearanceSettingsViewModel,
                LocalEnableBlur provides appearance.enableBlur,
                LocalDesignEnableBlur provides appearance.enableBlur,
                LocalEnableFloatingBottomBar provides appearance.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides appearance.enableFloatingBottomBarBlur,
                LocalColorMode provides kernelSuAppSettings.colorMode.value,
                LocalUiMode provides kernelSuUiMode,
            ) {
                KernelSUTheme(
                    appSettings = kernelSuAppSettings,
                    uiMode = kernelSuUiMode,
                ) {
                    SuperIslandApp(
                        uiMode = kernelSuUiMode,
                        resumeGeneration = this@MainActivity.resumeGeneration,
                        initialDestination = this@MainActivity.launchDestination,
                        onAppearanceChange = onAppearanceChange,
                        navigator = navigator,
                        mainPagerState = mainPagerState,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchDestination = intent.debugDestination()
        runDebugBatteryCommand(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeGeneration += 1
    }

    private fun runDebugBatteryCommand(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        when (intent?.action) {
            DEBUG_START_CONTINUOUS_BATTERY_MONITOR_ACTION -> BatteryMonitorService.start(this)
            DEBUG_STOP_CONTINUOUS_BATTERY_MONITOR_ACTION -> BatteryMonitorService.stop(this)
            DEBUG_REFRESH_MEDIA_ACTION -> NotificationProxyController(this).refreshMedia()
            DEBUG_ENABLE_TEST_MEDIA_ACTION -> enableTestMediaForDebug()
            DEBUG_ENABLE_TEST_SMART_CAPSULE_ACTION -> enableTestSmartCapsuleForDebug()
            DEBUG_DISABLE_TEST_SMART_CAPSULE_ACTION -> disableTestSmartCapsuleForDebug()
            DEBUG_SIMULATE_WIRED_HEADSET_CONNECTED_ACTION -> simulateWiredHeadsetForDebug(true)
            DEBUG_SIMULATE_WIRED_HEADSET_DISCONNECTED_ACTION -> simulateWiredHeadsetForDebug(false)
            DEBUG_SIMULATE_BLUETOOTH_CONNECTED_ACTION -> simulateBluetoothDeviceForDebug(true)
            DEBUG_SIMULATE_BLUETOOTH_DISCONNECTED_ACTION -> simulateBluetoothDeviceForDebug(false)
        }
    }

    private fun enableTestMediaForDebug() {
        if (!BuildConfig.DEBUG) return
        val controller = NotificationProxyController(this)
        val snapshot = controller.mediaSnapshot()
        val candidate = snapshot.candidates.firstOrNull { it.packageName == TEST_SOURCE_PACKAGE } ?: return
        if (snapshot.enabledSources.none { it.id == candidate.id }) {
            controller.toggleMediaCandidate(candidate.id)
        } else {
            controller.refreshMedia()
        }
    }

    private fun enableTestSmartCapsuleForDebug() {
        if (!BuildConfig.DEBUG) return
        SmartCapsuleConfigMutationQueue.submit(
            store = SmartCapsuleConfigStore(this),
            transform = { current ->
                current.copy(
                    enabled = true,
                    rules =
                        current.rules.filterNot { rule -> rule.packageName == TEST_SOURCE_PACKAGE } +
                            AppRule(TEST_SOURCE_PACKAGE, ChannelSelection.ALL),
                )
            },
        )
    }

    private fun disableTestSmartCapsuleForDebug() {
        if (!BuildConfig.DEBUG) return
        SmartCapsuleConfigMutationQueue.submit(
            store = SmartCapsuleConfigStore(this),
            transform = { current ->
                val remainingRules =
                    current.rules.filterNot { rule -> rule.packageName == TEST_SOURCE_PACKAGE }
                current.copy(
                    enabled = current.enabled && remainingRules.isNotEmpty(),
                    rules = remainingRules,
                )
            },
        )
    }

    private fun simulateWiredHeadsetForDebug(connected: Boolean) {
        if (!BuildConfig.DEBUG) return
        BatteryMonitorService.debugSimulateWiredHeadset(connected)
    }

    private fun simulateBluetoothDeviceForDebug(connected: Boolean) {
        if (!BuildConfig.DEBUG) return
        BatteryMonitorService.debugSimulateBluetoothDevice(connected)
    }
}

/** Keeps the navigation state owner at one stable composition site while the skin changes. */
@Composable
private fun KernelSuRootHost(
    uiMode: UiMode,
    content: @Composable () -> Unit,
) {
    // Keep NavDisplay/navigator alive when the skin switches by hoisting the navigation
    // state above the skin host. A plain `when (uiMode)` would dispose the previous
    // branch's content and recreate SuperIslandApp, resetting the backStack to MAIN.
    // movableContentOf is retained for the theme container itself, but navigation is hoisted.
    when (uiMode) {
        UiMode.Material -> KernelSuMaterialRootHost(content)
        UiMode.Miuix -> KernelSuMiuixRootHost(content)
    }
}

@Composable
private fun SuperIslandApp(
    uiMode: UiMode,
    resumeGeneration: Int,
    initialDestination: AppDestination,
    onAppearanceChange: ((AppAppearanceSettings) -> AppAppearanceSettings) -> Unit,
    navigator: io.github.superisland.ui.navigation.AppNavigator,
    mainPagerState: io.github.superisland.ui.navigation.MainPagerState,
) {
    val context = LocalContext.current
    val smartCapsuleStateOwner =
        remember(context.applicationContext) {
            SmartCapsuleDashboardStateOwner(context.applicationContext)
        }
    val mediaIslandStateOwner =
        remember(context.applicationContext) {
            MediaIslandDashboardStateOwner(context.applicationContext)
        }
    val residentMonitorStateOwner =
        remember(context.applicationContext) {
            ResidentMonitorDashboardStateOwner(context.applicationContext)
        }
    val islandAppearanceStateOwner =
        remember(context.applicationContext) {
            IslandAppearanceDashboardStateOwner(context.applicationContext)
        }
    var environment by remember {
        mutableStateOf(RuntimeEnvironmentController.current())
    }
    DisposableEffect(smartCapsuleStateOwner) {
        onDispose(smartCapsuleStateOwner::close)
    }
    DisposableEffect(mediaIslandStateOwner) {
        onDispose(mediaIslandStateOwner::close)
    }
    DisposableEffect(residentMonitorStateOwner) {
        onDispose(residentMonitorStateOwner::close)
    }
    DisposableEffect(islandAppearanceStateOwner) {
        onDispose(islandAppearanceStateOwner::close)
    }
    DisposableEffect(Unit) {
        val unregister =
            RuntimeEnvironmentController.observe { snapshot ->
                environment = snapshot
            }
        onDispose(unregister)
    }
    LaunchedEffect(residentMonitorStateOwner) {
        // Start the resident probe after the first frame, but let the root owner receive the
        // result. Warming only the process cache leaves this already-created owner stuck on its
        // default state until a destination is opened, which is the source of the empty-shell
        // transition seen on cold navigation.
        withFrameNanos { }
        residentMonitorStateOwner.activate()
    }
    LaunchedEffect(resumeGeneration) {
        // Application startup already begins both probes. Repeating them during the first resume
        // competes with Compose class loading and the first navigation frames; only subsequent
        // foreground returns need a fresh Root/config handshake.
        if (resumeGeneration > FIRST_RESUME_GENERATION) {
            RuntimeEnvironmentController.refreshRoot()
            withContext(Dispatchers.IO) {
                SmartCapsuleHostConfigSync.syncStoredAndReload()
            }
        }
        smartCapsuleStateOwner.refreshConfigAndRuntime()
    }

    LaunchedEffect(initialDestination) {
        // Debug entry points still land on their detail destination, above the root pager.
        if (initialDestination != AppDestination.MAIN && !initialDestination.isPrimaryTab) {
            navigator.navigate(initialDestination)
        }
    }
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val navigateDestination: (AppRoute) -> Unit = { destination ->
        if (destination == AppDestination.SMART_CAPSULE_APPS) {
            // Match KernelSU's SuperUser flow: enter the refreshing state before Navigation3
            // starts the transition, then publish the complete list in one owner update.
            smartCapsuleStateOwner.activateAppPage()
        }
        if (
            destination == AppDestination.BATTERY_CONFIGURATION ||
            destination == AppDestination.BATTERY_EXPANDED_CONTENT
        ) {
            residentMonitorStateOwner.activate()
        }
        if (destination is AppDestination && destination.isPrimaryTab) {
            navigator.popToRoot()
            mainPagerState.animateToPage(destination.primaryTab.pageIndex)
        } else {
            navigator.navigate(destination)
        }
    }
    BackHandler(
        enabled = navigator.current.isDetail,
        onBack = navigator::pop,
    )

    // This is the same Navigation3 renderer used by KernelSU Manager.  Its Miuix default
    // transition spec supplies forward, back, and predictive-back motion without a custom spec.
    KernelSuRootHost(uiMode = uiMode) {
        NavDisplay(
            backStack = navigator.backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            onBack = navigator::pop,
            // Static and parameterized routes are both direct content keys, matching Navigation3's
            // typed-route model and retaining the outgoing page during transitions.
            entryProvider = { destination ->
                NavEntry(
                    key = destination,
                    contentKey = destination,
                ) { route ->
                    if (route == AppDestination.MAIN) {
                    SuperIslandMainPager(
                        environment = environment,
                        mainPagerState = mainPagerState,
                        onAppearanceChange = onAppearanceChange,
                        onOpenDestination = navigateDestination,
                        )
                    } else {
                        SuperIslandDestinationContent(
                            destination = route,
                            environment = environment,
                            resumeGeneration = resumeGeneration,
                            onAppearanceChange = onAppearanceChange,
                            onNavigate = navigateDestination,
                            smartCapsuleStateOwner = smartCapsuleStateOwner,
                            mediaIslandStateOwner = mediaIslandStateOwner,
                            residentMonitorStateOwner = residentMonitorStateOwner,
                            islandAppearanceStateOwner = islandAppearanceStateOwner,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SuperIslandMainPager(
    environment: RuntimeEnvironmentSnapshot,
    mainPagerState: io.github.superisland.ui.navigation.MainPagerState,
    onAppearanceChange: ((AppAppearanceSettings) -> AppAppearanceSettings) -> Unit,
    onOpenDestination: (AppDestination) -> Unit,
) {
    // A retained Navigation3 entry owns a direct subscription, never a snapshot captured when it
    // entered the back stack. This is the same live ViewModel page-state contract as KernelSU.
    val appearance by
        LocalAppAppearanceViewModel.current.appearance.collectAsStateWithLifecycle()
    val systemDarkTheme = isSystemInDarkTheme()
    val contentReady = rememberContentReady()
    val settledPage = mainPagerState.pagerState.settledPage
    val pagerContent: @Composable (Dp) -> Unit = { bottomInnerPadding ->
        HorizontalPager(
            modifier =
                Modifier
                    .fillMaxSize(),
            state = mainPagerState.pagerState,
            beyondViewportPageCount = if (contentReady) AppPrimaryTab.entries.size - 2 else 0,
        ) { page ->
            val tab = AppPrimaryTab.entries[page]
            val isCurrentPage = page == settledPage
            if (!isCurrentPage && !contentReady) return@HorizontalPager
            when (appearance.uiMode) {
                AppUiMode.MIUIX ->
                    when (tab) {
                        AppPrimaryTab.HOME ->
                            HomeScreen(
                                environment = environment,
                                onTabSelected = { selectedTab -> mainPagerState.animateToPage(selectedTab.pageIndex) },
                                showBottomBar = false,
                                bottomInnerPadding = bottomInnerPadding,
                            )
                        AppPrimaryTab.SUPER_ISLAND ->
                            SuperIslandMiuix(
                                onTabSelected = { selectedTab -> mainPagerState.animateToPage(selectedTab.pageIndex) },
                                onOpenSmartCapsule = { onOpenDestination(AppDestination.SMART_CAPSULE_APPS) },
                                onOpenBatteryMonitor = { onOpenDestination(AppDestination.BATTERY_CONFIGURATION) },
                                onOpenCapsuleAppearance = { onOpenDestination(AppDestination.CAPSULE_APPEARANCE) },
                                showBottomBar = false,
                                bottomInnerPadding = bottomInnerPadding,
                            )
                        AppPrimaryTab.EXTENSIONS ->
                            ExtensionsMiuix(
                                onTabSelected = { selectedTab -> mainPagerState.animateToPage(selectedTab.pageIndex) },
                                onOpenMiShareFolder = {
                                    onOpenDestination(AppDestination.EXTENSION_MISHARE_FOLDER)
                                },
                                onOpenScreenRecording = {
                                    onOpenDestination(AppDestination.EXTENSION_SCREEN_RECORDING)
                                },
                                showBottomBar = false,
                                bottomInnerPadding = bottomInnerPadding,
                            )
                        AppPrimaryTab.SETTINGS ->
                            SettingsMiuix(
                                appearance = appearance,
                                onTabSelected = { selectedTab -> mainPagerState.animateToPage(selectedTab.pageIndex) },
                                onUiModeChange = { mode -> onAppearanceChange { it.withUiMode(mode) } },
                                onOpenTheme = { onOpenDestination(AppDestination.SETTINGS_THEME) },
                                showBottomBar = false,
                                bottomInnerPadding = bottomInnerPadding,
                            )
                        AppPrimaryTab.PROFILE ->
                            ProfileMiuix(
                                onTabSelected = { selectedTab -> mainPagerState.animateToPage(selectedTab.pageIndex) },
                                onOpenUsageGuide = { onOpenDestination(AppDestination.PROFILE_USAGE_GUIDE) },
                                onOpenAbout = { onOpenDestination(AppDestination.PROFILE_ABOUT) },
                                showBottomBar = false,
                                bottomInnerPadding = bottomInnerPadding,
                            )
                    }
                AppUiMode.MATERIAL ->
                    MaterialPrimaryPage(
                        tab = tab,
                        environment = environment,
                        appearance = appearance,
                        onTabSelected = { selectedTab -> mainPagerState.animateToPage(selectedTab.pageIndex) },
                        onUiModeChange = { mode -> onAppearanceChange { it.withUiMode(mode) } },
                        onOpenTheme = { onOpenDestination(AppDestination.SETTINGS_THEME) },
                        onOpenDestination = onOpenDestination,
                        bottomInnerPadding = bottomInnerPadding,
                    )
            }
        }
    }
    when (appearance.uiMode) {
        AppUiMode.MIUIX ->
            KernelSuMiuixPrimaryPagerScaffold(
                selectedIndex = { mainPagerState.selectedPage },
                onTabSelected = { tab -> mainPagerState.animateToPage(tab.pageIndex) },
                enableBlur = appearance.enableBlur,
                enableFloatingBottomBar = appearance.enableFloatingBottomBar,
                enableFloatingBottomBarBlur = appearance.enableFloatingBottomBarBlur,
                darkTheme =
                    appearance.toKernelSuAppSettings().colorMode.isDark ||
                        (appearance.toKernelSuAppSettings().colorMode.isSystem && systemDarkTheme),
                content = pagerContent,
            )
        AppUiMode.MATERIAL ->
            MaterialPrimaryPagerScaffold(
                selectedTab = AppPrimaryTab.entries[mainPagerState.selectedPage],
                onTabSelected = { tab -> mainPagerState.animateToPage(tab.pageIndex) },
                content = pagerContent,
            )
    }
}

@Composable
private fun SuperIslandDestinationContent(
    destination: AppRoute,
    environment: RuntimeEnvironmentSnapshot,
    resumeGeneration: Int,
    onAppearanceChange: ((AppAppearanceSettings) -> AppAppearanceSettings) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    smartCapsuleStateOwner: SmartCapsuleDashboardStateOwner,
    mediaIslandStateOwner: MediaIslandDashboardStateOwner,
    residentMonitorStateOwner: ResidentMonitorDashboardStateOwner,
    islandAppearanceStateOwner: IslandAppearanceDashboardStateOwner,
) {
    when (destination) {
        is SmartCapsuleChannelsDestination -> {
            SmartCapsuleDashboard(
                resumeGeneration = resumeGeneration,
                page = SmartCapsulePage.CHANNELS,
                selectedPackage = destination.packageName,
                selectedChannel = null,
                stateOwner = smartCapsuleStateOwner,
                onOpenApp = {},
                onOpenChannel = { channel ->
                    onNavigate(
                        SmartCapsuleChannelDetailDestination(
                            packageName = destination.packageName,
                            channelId = channel.id,
                            channelName = channel.name,
                            channelImportance = channel.importance,
                        ),
                    )
                },
                onBack = { onNavigate(AppDestination.SMART_CAPSULE_APPS) },
            )
            return
        }
        is SmartCapsuleChannelDetailDestination -> {
            SmartCapsuleDashboard(
                resumeGeneration = resumeGeneration,
                page = SmartCapsulePage.CHANNEL_DETAIL,
                selectedPackage = destination.packageName,
                selectedChannel =
                    SmartCapsuleChannelEntry(
                        id = destination.channelId,
                        name = destination.channelName,
                        importance = destination.channelImportance,
                    ),
                stateOwner = smartCapsuleStateOwner,
                onOpenApp = {},
                onOpenChannel = {},
                onBack = {
                    onNavigate(SmartCapsuleChannelsDestination(destination.packageName))
                },
            )
            return
        }
        is AppDestination -> Unit
    }

    val staticDestination = destination
    // Detail entries use the same live appearance owner as the root theme.
    val appearance by
        LocalAppAppearanceViewModel.current.appearance.collectAsStateWithLifecycle()
    // The route remains stable for the outgoing NavDisplay entry while callbacks append or pop
    // the shared navigation stack above.
    when (staticDestination) {
        AppDestination.MAIN -> Unit
        AppDestination.HOME ->
            HomeScreen(
                environment = environment,
                onTabSelected = { tab -> onNavigate(tab.destination) },
            )
        AppDestination.HOME_SERVICE_STATUS ->
            HomeServiceStatus(
                resumeGeneration = resumeGeneration,
                stateOwner = mediaIslandStateOwner,
                onBack = { onNavigate(AppDestination.HOME) },
            )
        AppDestination.HOME_USAGE_GUIDE ->
            HomeUsageGuide(
                onBack = { onNavigate(AppDestination.HOME) },
            )
        AppDestination.HOME_ABOUT ->
            HomeAbout(
                onBack = { onNavigate(AppDestination.HOME) },
            )
        AppDestination.SUPER_ISLAND ->
            SuperIslandMiuix(
                onTabSelected = { tab -> onNavigate(tab.destination) },
                onOpenSmartCapsule = { onNavigate(AppDestination.SMART_CAPSULE_APPS) },
                onOpenBatteryMonitor = { onNavigate(AppDestination.BATTERY_CONFIGURATION) },
                onOpenCapsuleAppearance = { onNavigate(AppDestination.CAPSULE_APPEARANCE) },
                onOpenLyric = { onNavigate(AppDestination.LYRIC) },
            )
        AppDestination.EXTENSIONS ->
            ExtensionsMiuix(
                onTabSelected = { tab -> onNavigate(tab.destination) },
                onOpenMiShareFolder = { onNavigate(AppDestination.EXTENSION_MISHARE_FOLDER) },
                onOpenScreenRecording = { onNavigate(AppDestination.EXTENSION_SCREEN_RECORDING) },
            )
        AppDestination.EXTENSION_MISHARE_FOLDER ->
            MiShareFolderExtensionScreen(
                onBack = { onNavigate(AppDestination.EXTENSIONS) },
            )
        AppDestination.EXTENSION_SCREEN_RECORDING ->
            ScreenRecordingExtensionScreen(
                onBack = { onNavigate(AppDestination.EXTENSIONS) },
            )
        AppDestination.SETTINGS ->
            SettingsMiuix(
                appearance = appearance,
                onTabSelected = { tab -> onNavigate(tab.destination) },
                onUiModeChange = { mode -> onAppearanceChange { it.withUiMode(mode) } },
                onOpenTheme = { onNavigate(AppDestination.SETTINGS_THEME) },
            )
        AppDestination.SETTINGS_THEME ->
            when (appearance.uiMode) {
                AppUiMode.MIUIX ->
                    KernelSuMiuixAppearanceSettingsPage(
                        onBack = { onNavigate(AppDestination.SETTINGS) },
                    )
                AppUiMode.MATERIAL ->
                    MaterialAppearanceSettingsPage(
                        onBack = { onNavigate(AppDestination.SETTINGS) },
                    )
            }
        AppDestination.PROFILE ->
            ProfileMiuix(
                onTabSelected = { tab -> onNavigate(tab.destination) },
                onOpenUsageGuide = { onNavigate(AppDestination.PROFILE_USAGE_GUIDE) },
                onOpenAbout = { onNavigate(AppDestination.PROFILE_ABOUT) },
            )
        AppDestination.PROFILE_USAGE_GUIDE ->
            HomeUsageGuide(
                backLabel = "返回我的",
                onBack = { onNavigate(AppDestination.PROFILE) },
            )
        AppDestination.PROFILE_ABOUT ->
            HomeAbout(
                backLabel = "返回我的",
                onBack = { onNavigate(AppDestination.PROFILE) },
            )
        AppDestination.SMART_CAPSULE_APPS ->
            SmartCapsuleDashboard(
                resumeGeneration = resumeGeneration,
                page = SmartCapsulePage.APPS,
                selectedPackage = null,
                selectedChannel = null,
                stateOwner = smartCapsuleStateOwner,
                onOpenApp = { packageName ->
                    onNavigate(SmartCapsuleChannelsDestination(packageName))
                },
                onOpenChannel = {},
                onBack = { onNavigate(AppDestination.SUPER_ISLAND) },
            )
        AppDestination.CAPSULE_APPEARANCE ->
            CapsuleAppearanceScreen(
                stateOwner = islandAppearanceStateOwner,
                onBack = { onNavigate(AppDestination.SUPER_ISLAND) },
            )
        AppDestination.BATTERY_MONITOR ->
            BatteryMonitor(
                page = BatteryMonitorPage.OVERVIEW,
                stateOwner = residentMonitorStateOwner,
                onOpenPage = { page -> onNavigate(page.destination) },
                onBack = { onNavigate(AppDestination.SUPER_ISLAND) },
            )
        AppDestination.BATTERY_REALTIME ->
            BatteryMonitor(
                page = BatteryMonitorPage.REALTIME,
                stateOwner = residentMonitorStateOwner,
                onOpenPage = { page -> onNavigate(page.destination) },
                onBack = { onNavigate(AppDestination.BATTERY_MONITOR) },
            )
        AppDestination.BATTERY_CONTINUOUS ->
            BatteryMonitor(
                page = BatteryMonitorPage.CONTINUOUS,
                stateOwner = residentMonitorStateOwner,
                onOpenPage = { page -> onNavigate(page.destination) },
                onBack = { onNavigate(AppDestination.BATTERY_MONITOR) },
            )
        AppDestination.BATTERY_EVENTS ->
            BatteryMonitor(
                page = BatteryMonitorPage.EVENTS,
                stateOwner = residentMonitorStateOwner,
                onOpenPage = { page -> onNavigate(page.destination) },
                onBack = { onNavigate(AppDestination.BATTERY_MONITOR) },
            )
        AppDestination.BATTERY_DIAGNOSTICS ->
            BatteryMonitor(
                page = BatteryMonitorPage.DIAGNOSTICS,
                stateOwner = residentMonitorStateOwner,
                onOpenPage = { page -> onNavigate(page.destination) },
                onBack = { onNavigate(AppDestination.BATTERY_MONITOR) },
            )
        AppDestination.BATTERY_CONFIGURATION ->
            BatteryMonitor(
                page = BatteryMonitorPage.CONFIGURATION,
                stateOwner = residentMonitorStateOwner,
                onOpenPage = { page -> onNavigate(page.destination) },
                onBack = { onNavigate(AppDestination.SUPER_ISLAND) },
            )
        AppDestination.BATTERY_EXPANDED_CONTENT ->
            BatteryMonitor(
                page = BatteryMonitorPage.EXPANDED_CONTENT,
                stateOwner = residentMonitorStateOwner,
                onOpenPage = { page -> onNavigate(page.destination) },
                onBack = { onNavigate(AppDestination.BATTERY_CONFIGURATION) },
            )
        AppDestination.LYRIC ->
            LyricScreen(
                onBack = { onNavigate(AppDestination.SUPER_ISLAND) },
            )
    }
}

@Composable
private fun HomeServiceStatus(
    resumeGeneration: Int,
    stateOwner: MediaIslandDashboardStateOwner,
    onBack: () -> Unit,
) {
    val contentReady = rememberContentReady()
    val mediaState by stateOwner.state.collectAsStateWithLifecycle()
    val snapshot = mediaState.snapshot
    LaunchedEffect(contentReady, resumeGeneration) {
        if (!contentReady) return@LaunchedEffect
        stateOwner.onVisible(resumeGeneration)
    }

    AppInformationDetailScreen(
        title = "服务状态",
        subtitle = ROOT_MODE_LABEL,
        entries =
            listOf(
                InformationEntryUi("运行路径", "LSPosed 产品路径 · Root allowlist 增强"),
                InformationEntryUi(
                    "媒体通知访问",
                    if (snapshot.notificationAccessGranted) "已允许" else "未允许；歌词媒体读取不可用",
                ),
                InformationEntryUi(
                    "媒体监听服务",
                    if (snapshot.listenerConnected) "已连接" else "未连接；请在系统通知访问设置中处理",
                ),
            ),
        backLabel = "返回首页",
        onBack = onBack,
    )
}

@Composable
private fun HomeUsageGuide(
    backLabel: String = "返回首页",
    onBack: () -> Unit,
) {
    AppInformationDetailScreen(
        title = "使用指南",
        subtitle = ROOT_MODE_LABEL,
        entries =
            listOf(
                InformationEntryUi("1. 确认模块状态", "需要 Root、已激活的 LSPosed 模块与 SystemUI 作用域；首页状态卡会显示当前状态。"),
                InformationEntryUi("2. 配置上岛来源", "在超级岛中进入超级岛通知，只允许你主动选择的来源；歌词按已配置的媒体源读取。"),
                InformationEntryUi("3. 检查系统权限", "通知权限、通知访问与焦点通知状态均在对应功能详情中处理。"),
                InformationEntryUi("4. 使用持续监控", "常驻超级岛可细拆显示指标；Root 增强只在已验证设备上按需读取。"),
            ),
        backLabel = backLabel,
        onBack = onBack,
    )
}

@Composable
private fun HomeAbout(
    backLabel: String = "返回首页",
    onBack: () -> Unit,
) {
    AppInformationDetailScreen(
        title = "关于超级岛",
        subtitle = ROOT_MODE_LABEL,
        entries =
            listOf(
                InformationEntryUi("版本", BuildConfig.VERSION_NAME),
                InformationEntryUi("界面", "Compose · Miuix 0.9.3 / Material 3"),
                InformationEntryUi("隐私", "通知正文、PendingIntent 和原始诊断输出不作为应用历史持久化。"),
                InformationEntryUi("发布状态", "当前为开发构建；账号、激活与付费权益功能尚未开放。"),
            ),
        backLabel = backLabel,
        onBack = onBack,
    )
}

@Composable
private fun SmartCapsuleDashboard(
    resumeGeneration: Int,
    page: SmartCapsulePage,
    selectedPackage: String?,
    selectedChannel: SmartCapsuleChannelEntry?,
    stateOwner: SmartCapsuleDashboardStateOwner,
    onOpenApp: (String) -> Unit,
    onOpenChannel: (SmartCapsuleChannelEntry) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dashboardState by stateOwner.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val channelCatalogStore = stateOwner.channelCatalogStore
    val contentReady = rememberContentReady()
    var channelCatalogGeneration by rememberSaveable { mutableIntStateOf(0) }
    var appListPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var forceRefreshAfterPermission by rememberSaveable { mutableStateOf(false) }
    val showSystemApps = dashboardState.showSystemApps
    val appSortConfig = dashboardState.appSortConfig

    val appListPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            if (forceRefreshAfterPermission) {
                stateOwner.refreshAppEntries(forceRefresh = true)
            } else {
                stateOwner.continueAppPageAfterPermissionRequest()
            }
            forceRefreshAfterPermission = false
        }

    DisposableEffect(channelCatalogStore, page, selectedPackage) {
        val unregister =
            if (page == SmartCapsulePage.CHANNELS || page == SmartCapsulePage.CHANNEL_DETAIL) {
                channelCatalogStore.observe(selectedPackage) { channelCatalogGeneration += 1 }
            } else {
                {}
            }
        onDispose(unregister)
    }
    LaunchedEffect(
        page,
        dashboardState.snapshot.enabled,
        dashboardState.appPagePrepared,
        dashboardState.isAppDirectoryRefreshing,
        contentReady,
    ) {
        if (page != SmartCapsulePage.APPS) {
            appListPermissionRequested = false
            forceRefreshAfterPermission = false
        } else if (
            dashboardState.snapshot.enabled &&
            contentReady &&
            !dashboardState.appPagePrepared &&
            !appListPermissionRequested
        ) {
            if (dashboardState.appListPermissionRequired) {
                appListPermissionRequested = true
                appListPermissionLauncher.launch(
                    SmartCapsuleAppDirectory.MIUI_GET_INSTALLED_APPS_PERMISSION,
                )
            } else if (!dashboardState.isAppDirectoryRefreshing) {
                stateOwner.activateAppPage()
            }
        }
    }

    val snapshot = dashboardState.snapshot
    val configAwaitingConsumers =
        dashboardState.configSyncFailed || !dashboardState.configAccepted

    fun updateConfig(transform: (io.github.superisland.model.SmartCapsuleConfigSnapshot) -> io.github.superisland.model.SmartCapsuleConfigSnapshot) {
        stateOwner.updateConfig(transform)
    }

    // Like KernelSU's ViewModel-owned groupedApps, this root-owned projection survives Navigation3
    // disposal and is already available on the first frame when returning from App Profile.
    val selectedApp =
        remember(dashboardState.appEntries, selectedPackage) {
            selectedPackage?.let { packageName ->
                dashboardState.appEntries.firstOrNull { app -> app.packageName == packageName }
                    ?: SmartCapsuleAppEntry(packageName = packageName, label = packageName)
            }
        }
    val selectedRule = snapshot.rules.firstOrNull { rule -> rule.packageName == selectedPackage }
    var selectedChannelCatalog by
        remember(page, selectedPackage) {
            mutableStateOf(
                selectedPackage?.let(channelCatalogStore::cachedSnapshot)
                    ?: SmartCapsuleChannelCatalogSnapshot.NOT_LOADED,
            )
        }
    LaunchedEffect(page, selectedPackage, channelCatalogGeneration, contentReady) {
        if (
            (page == SmartCapsulePage.CHANNELS || page == SmartCapsulePage.CHANNEL_DETAIL) &&
            selectedPackage != null &&
            contentReady
        ) {
            val loaded = withContext(Dispatchers.IO) { channelCatalogStore.loadSnapshot(selectedPackage) }
            selectedChannelCatalog =
                if (
                    loaded.status == SmartCapsuleChannelCatalogStatus.NOT_LOADED &&
                    selectedChannelCatalog.status == SmartCapsuleChannelCatalogStatus.LOADING
                ) {
                    loaded.copy(status = SmartCapsuleChannelCatalogStatus.LOADING)
                } else {
                    loaded
                }
        }
    }
    val selectedAppEnabled = selectedRule != null
    LaunchedEffect(page, selectedPackage, selectedAppEnabled, resumeGeneration, contentReady) {
        if (
            (page == SmartCapsulePage.CHANNELS || page == SmartCapsulePage.CHANNEL_DETAIL) &&
            selectedPackage != null &&
            selectedAppEnabled &&
            contentReady
        ) {
            if (selectedChannelCatalog.status == SmartCapsuleChannelCatalogStatus.NOT_LOADED) {
                selectedChannelCatalog =
                    selectedChannelCatalog.copy(status = SmartCapsuleChannelCatalogStatus.LOADING)
            }
            SmartCapsuleChannelCatalogController.request(context, selectedPackage)
        }
    }
    val selectedChannels = selectedChannelCatalog.entries
    val appOptions = dashboardState.appOptions
    when (page) {
        SmartCapsulePage.APPS ->
            SmartCapsuleAppsScreen(
                enabled = snapshot.enabled,
                apps = if (snapshot.enabled && dashboardState.appPagePrepared) appOptions else emptyList(),
                isRefreshing = dashboardState.isAppDirectoryRefreshing,
                showSystemApps = showSystemApps,
                sortConfig = appSortConfig,
                onEnabledChange = { enabled ->
                    updateConfig { current -> current.copy(enabled = enabled) }
                },
                onSelectApp = onOpenApp,
                onRefresh = {
                    coroutineScope.launch {
                        val needsPermission =
                            withContext(Dispatchers.IO) {
                                stateOwner.needsMiuiInstalledAppsPermission()
                            }
                        if (needsPermission) {
                            appListPermissionRequested = true
                            forceRefreshAfterPermission = true
                            appListPermissionLauncher.launch(
                                SmartCapsuleAppDirectory.MIUI_GET_INSTALLED_APPS_PERMISSION,
                            )
                        } else {
                            stateOwner.refreshAppEntries(forceRefresh = true)
                        }
                    }
                },
                onToggleSystemApps = { stateOwner.setShowSystemApps(!showSystemApps) },
                onSortConfigChange = stateOwner::setAppSortConfig,
                onBack = onBack,
            )
        SmartCapsulePage.CHANNELS -> {
            val app = selectedApp
            if (app == null) {
                AppInformationDetailScreen(
                    title = "Channel 设置",
                    subtitle = ROOT_MODE_LABEL,
                    entries =
                        listOf(
                            InformationEntryUi("未选择应用", "请返回上岛应用列表后重新选择"),
                        ),
                    backLabel = "返回上岛应用",
                    onBack = onBack,
                )
            } else {
                val allChannels = selectedRule?.channels?.allChannels == true
                val exactChannels = selectedRule?.channels?.channelIds.orEmpty().toSet()
                val packageInfo = app.packageInfo
                val profileState =
                    SmartCapsuleAppProfileUi(
                        packageName = app.packageName,
                        appLabel = app.label,
                        packageInfo = packageInfo,
                        appUid = packageInfo?.applicationInfo?.uid ?: -1,
                        appVersionName = packageInfo?.versionName.orEmpty(),
                        appVersionCode = packageInfo?.longVersionCode ?: 0L,
                        enabled = selectedRule != null,
                        enabledSummary =
                            when {
                                configAwaitingConsumers -> "等待 LSPosed 同步"
                                selectedRule != null -> "已上岛"
                                else -> "未上岛"
                            },
                        focusOnly =
                            selectedRule?.focusDisplayMode == FocusDisplayMode.FOCUS_ONLY,
                        priorityOptions = appIslandPriorityOptions,
                        selectedPriorityId =
                            (selectedRule?.islandPriority ?: IslandPriority.LOW).name,
                        allChannels = allChannels,
                        allChannelsEnabled =
                            selectedRule != null &&
                                selectedChannelCatalog.status == SmartCapsuleChannelCatalogStatus.LOADED &&
                                selectedChannels.isNotEmpty(),
                        channels =
                            selectedChannels.map { channel ->
                                val allowed = allChannels || channel.id in exactChannels
                                val priority =
                                    selectedRule
                                        ?.takeIf { allowed }
                                        ?.effectiveIslandPriority(channel.id)
                                val displayMode =
                                    selectedRule
                                        ?.takeIf { allowed }
                                        ?.effectiveFocusDisplayMode(channel.id)
                                SmartCapsuleAppProfileChannelUi(
                                    id = channel.id,
                                    title = channel.name,
                                    summary = "${channel.id} · ${channel.importance.channelImportanceLabel()}",
                                    status =
                                        if (allowed) {
                                            if (displayMode == FocusDisplayMode.FOCUS_ONLY) {
                                                "仅焦点"
                                            } else {
                                                "已允许 · ${priority?.displayName ?: IslandPriority.LOW.displayName}"
                                            }
                                        } else {
                                            "未允许"
                                        },
                                    enabled = selectedRule != null,
                                )
                            },
                        channelsReady =
                            selectedChannelCatalog.status == SmartCapsuleChannelCatalogStatus.LOADED,
                    )
                SmartCapsuleAppProfileScreen(
                    state = profileState,
                    onEnabledChange = { enabled ->
                        updateConfig { current ->
                            current.copy(
                                rules =
                                    if (enabled) {
                                        current.rules.filterNot { it.packageName == app.packageName } +
                                            AppRule(app.packageName, ChannelSelection.ALL)
                                    } else {
                                        current.rules.filterNot { it.packageName == app.packageName }
                                    },
                            )
                        }
                    },
                    onFocusOnlyChange = { focusOnly ->
                        updateConfig { current ->
                            current.copy(
                                rules =
                                    current.rules.map { rule ->
                                        if (rule.packageName == app.packageName) {
                                            rule.copy(
                                                focusDisplayMode =
                                                    if (focusOnly) {
                                                        FocusDisplayMode.FOCUS_ONLY
                                                    } else {
                                                        FocusDisplayMode.ISLAND_AND_FOCUS
                                                    },
                                            )
                                        } else {
                                            rule
                                        }
                                    },
                            )
                        }
                    },
                    onSelectPriority = { optionId ->
                        val priority =
                            optionId.toIslandPriorityNameOrNull()
                                ?: return@SmartCapsuleAppProfileScreen
                        updateConfig { current ->
                            current.copy(
                                rules =
                                    current.rules.map { rule ->
                                        if (rule.packageName == app.packageName) {
                                            rule.copy(islandPriority = priority)
                                        } else {
                                            rule
                                        }
                                    },
                            )
                        }
                    },
                    onAllChannelsChange = { enabled ->
                        updateConfig { current ->
                            val currentRule =
                                current.rules.firstOrNull { rule ->
                                    rule.packageName == app.packageName
                                } ?: return@updateConfig current
                            val replacement =
                                currentRule.copy(
                                    channels =
                                        if (enabled) {
                                            ChannelSelection(
                                                allChannels = true,
                                                islandPriorityOverrides =
                                                    currentRule.channels.islandPriorityOverrides,
                                                focusDisplayModeOverrides =
                                                    currentRule.channels.focusDisplayModeOverrides,
                                            )
                                        } else {
                                            ChannelSelection(
                                                channelIds = selectedChannels.map { it.id },
                                                islandPriorityOverrides =
                                                    currentRule.channels.islandPriorityOverrides,
                                                focusDisplayModeOverrides =
                                                    currentRule.channels.focusDisplayModeOverrides,
                                            )
                                        },
                                )
                            current.copy(
                                rules =
                                    current.rules.filterNot { it.packageName == app.packageName } + replacement,
                            )
                        }
                    },
                    onOpenChannel = { channelId ->
                        selectedChannels
                            .firstOrNull { channel -> channel.id == channelId }
                            ?.let(onOpenChannel)
                    },
                    onRefreshChannels = {
                        if (selectedRule != null) {
                            SmartCapsuleChannelCatalogController.request(context, app.packageName)
                        }
                    },
                    onBack = onBack,
                )
            }
        }
        SmartCapsulePage.CHANNEL_DETAIL -> {
            val app = selectedApp
            val channel =
                selectedChannel?.let { cached ->
                    selectedChannels.firstOrNull { current -> current.id == cached.id } ?: cached
                }
            if (app == null || channel == null) {
                AppInformationDetailScreen(
                    title = "Channel 详情",
                    subtitle = ROOT_MODE_LABEL,
                    entries =
                        listOf(
                            InformationEntryUi("未选择 Channel", "请返回 Channel 列表后重新选择"),
                        ),
                    backLabel = "返回 Channel 列表",
                    onBack = onBack,
                )
            } else {
                val allChannels = selectedRule?.channels?.allChannels == true
                val channelAllowed =
                    allChannels || channel.id in selectedRule?.channels?.channelIds.orEmpty()
                val priorityOverride =
                    selectedRule?.channels?.islandPriorityOverrides?.get(channel.id)
                val displayModeOverride =
                    selectedRule?.channels?.focusDisplayModeOverrides?.get(channel.id)
                val effectiveDisplayMode =
                    selectedRule?.takeIf { channelAllowed }?.effectiveFocusDisplayMode(channel.id)
                AppDirectoryDetailScreen(
                    title = channel.name,
                    subtitle = app.label,
                    onBack = onBack,
                    header = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppFeatureMasterSwitch(
                                title = "允许上岛",
                                summary =
                                    if (channelAllowed) {
                                        "该 Channel 通知可以进入超级岛"
                                    } else {
                                        "该 Channel 通知保持系统原样"
                                    },
                                checked = channelAllowed,
                                enabled =
                                    selectedRule != null &&
                                        (!allChannels || selectedChannels.isNotEmpty()),
                                onCheckedChange = { enabled ->
                                    updateConfig { current ->
                                        val currentRule =
                                            current.rules.firstOrNull { rule ->
                                                rule.packageName == app.packageName
                                            } ?: return@updateConfig current
                                        val selectedIds =
                                            if (currentRule.channels.allChannels) {
                                                selectedChannels.mapTo(mutableSetOf()) { entry -> entry.id }
                                            } else {
                                                currentRule.channels.channelIds.toMutableSet()
                                            }
                                        if (enabled) {
                                            selectedIds += channel.id
                                        } else {
                                            selectedIds -= channel.id
                                        }
                                        val replacement =
                                            currentRule
                                                .takeIf { selectedIds.isNotEmpty() }
                                                ?.copy(
                                                    channels =
                                                        ChannelSelection(
                                                            channelIds = selectedIds.toList(),
                                                            islandPriorityOverrides =
                                                                currentRule.channels.islandPriorityOverrides,
                                                            focusDisplayModeOverrides =
                                                                currentRule.channels.focusDisplayModeOverrides,
                                                        ),
                                                )
                                        current.copy(
                                            rules =
                                                current.rules.filterNot { rule ->
                                                    rule.packageName == app.packageName
                                                } + listOfNotNull(replacement),
                                        )
                                    }
                                },
                            )
                            AppFeatureMasterSwitch(
                                title = "仅显示焦点通知",
                                summary =
                                    if (displayModeOverride == null) {
                                        "跟随应用默认设置"
                                    } else {
                                        "覆盖应用默认显示方式"
                                    },
                                checked = effectiveDisplayMode == FocusDisplayMode.FOCUS_ONLY,
                                enabled = selectedRule != null && channelAllowed,
                                onCheckedChange = { focusOnly ->
                                    updateConfig { current ->
                                        val currentRule =
                                            current.rules.firstOrNull { rule ->
                                                rule.packageName == app.packageName
                                            } ?: return@updateConfig current
                                        val requestedMode =
                                            if (focusOnly) {
                                                FocusDisplayMode.FOCUS_ONLY
                                            } else {
                                                FocusDisplayMode.ISLAND_AND_FOCUS
                                            }
                                        val overrides =
                                            currentRule.channels.focusDisplayModeOverrides.toMutableMap()
                                        if (requestedMode == currentRule.focusDisplayMode) {
                                            overrides.remove(channel.id)
                                        } else {
                                            overrides[channel.id] = requestedMode
                                        }
                                        val replacement =
                                            currentRule.copy(
                                                channels =
                                                    currentRule.channels.copy(
                                                        focusDisplayModeOverrides = overrides,
                                                    ),
                                            )
                                        current.copy(
                                            rules =
                                                current.rules.filterNot { rule ->
                                                    rule.packageName == app.packageName
                                                } + replacement,
                                        )
                                    }
                                },
                            )
                            SmartCapsulePriorityPreference(
                                title = "岛优先级",
                                summary =
                                    if (priorityOverride == null) {
                                        "跟随应用默认值：${selectedRule?.islandPriority?.displayName ?: IslandPriority.LOW.displayName}"
                                    } else {
                                        "覆盖应用默认优先级"
                                    },
                                options = channelIslandPriorityOptions,
                                selectedOptionId =
                                    priorityOverride?.name ?: INHERIT_ISLAND_PRIORITY_ID,
                                enabled =
                                    selectedRule != null &&
                                        channelAllowed &&
                                        effectiveDisplayMode != FocusDisplayMode.FOCUS_ONLY,
                                onSelectOption = { optionId ->
                                    val override =
                                        if (optionId == INHERIT_ISLAND_PRIORITY_ID) {
                                            null
                                        } else {
                                            optionId.toIslandPriorityNameOrNull()
                                                ?: return@SmartCapsulePriorityPreference
                                        }
                                    updateConfig { current ->
                                        val currentRule =
                                            current.rules.firstOrNull { rule ->
                                                rule.packageName == app.packageName
                                            } ?: return@updateConfig current
                                        val overrides =
                                            currentRule.channels.islandPriorityOverrides.toMutableMap()
                                        if (override == null || override == currentRule.islandPriority) {
                                            overrides.remove(channel.id)
                                        } else {
                                            overrides[channel.id] = override
                                        }
                                        val replacement =
                                            currentRule.copy(
                                                channels =
                                                    currentRule.channels.copy(
                                                        islandPriorityOverrides = overrides,
                                                    ),
                                            )
                                        current.copy(
                                            rules =
                                                current.rules.filterNot { rule ->
                                                    rule.packageName == app.packageName
                                                } + replacement,
                                        )
                                    }
                                },
                            )
                        }
                    },
                    groups = emptyList(),
                    onEntrySelected = {},
                )
            }
        }
    }
}

@Composable
private fun ResidentMonitorSettings(
    page: BatteryMonitorPage,
    stateOwner: ResidentMonitorDashboardStateOwner,
    onOpenPage: (BatteryMonitorPage) -> Unit,
    onBack: () -> Unit,
) {
    val dashboardState by stateOwner.state.collectAsStateWithLifecycle()
    LaunchedEffect(stateOwner) {
        // Deep-link/debug destinations bypass the normal destination callback. Keep activation
        // local to the retained owner as a fallback; it only schedules the existing single-flight
        // background load and never blocks the first draw.
        stateOwner.activate()
    }

    val residentConfig = dashboardState.config
    when (page) {
        BatteryMonitorPage.CONFIGURATION -> {
            val titleMetrics =
                ResidentMonitorConfig.TITLE_METRICS.filter { metric ->
                    metric != ResidentMetricKey.FAN_RPM || dashboardState.fanAvailable
                }
            fun titleOptions(selectedMetric: ResidentMetricKey) =
                titleMetrics.map { metric ->
                    ResidentMetricOptionUi(
                        id = metric.name,
                        title = metric.displayName,
                        summary =
                            if (metric == ResidentMetricKey.FAN_RPM) {
                                "当前机型风扇实时转速"
                            } else {
                                "系统实时指标"
                            },
                        selected = metric == selectedMetric,
                    )
                }
            fun iconOptions(
                current: ResidentIslandIcon,
                side: String,
            ) =
                listOf(
                    ResidentIslandIcon.FOLLOW_TITLE to "默认",
                    ResidentIslandIcon.NONE to "不显示",
                ).map { (icon, title) ->
                    ResidentSlotOptionUi(
                        id = icon.name,
                        title = title,
                        summary = "立即控制超级岛${side}侧图标",
                        selected = current == icon,
                    )
                }
            ResidentMonitorConfigurationScreen(
                featureEnabled = dashboardState.monitoringActive,
                leftIconOptions = iconOptions(residentConfig.leftIcon, "左"),
                rightIconOptions = iconOptions(residentConfig.rightIcon, "右"),
                leftTitleOptions = titleOptions(residentConfig.leftTitleMetric),
                rightTitleOptions = titleOptions(residentConfig.rightTitleMetric),
                refreshIntervalSeconds = (residentConfig.titleRefreshIntervalMillis / 1_000).toInt(),
                onSelectLeftIcon = { id ->
                    ResidentIslandIcon.entries.firstOrNull { it.name == id }?.let { icon ->
                        stateOwner.saveConfig(residentConfig.copy(leftIcon = icon))
                    }
                },
                onSelectRightIcon = { id ->
                    ResidentIslandIcon.entries.firstOrNull { it.name == id }?.let { icon ->
                        stateOwner.saveConfig(residentConfig.copy(rightIcon = icon))
                    }
                },
                onSelectLeftTitle = { id ->
                    ResidentMetricKey.entries.firstOrNull { it.name == id }?.let { metric ->
                        stateOwner.saveConfig(residentConfig.copy(leftTitleMetric = metric))
                    }
                },
                onSelectRightTitle = { id ->
                    ResidentMetricKey.entries.firstOrNull { it.name == id }?.let { metric ->
                        stateOwner.saveConfig(residentConfig.copy(rightTitleMetric = metric))
                    }
                },
                onRefreshIntervalChange = { seconds ->
                    stateOwner.saveConfig(
                        residentConfig.copy(titleRefreshIntervalMillis = seconds * 1_000L),
                    )
                },
                onOpenExpandedContent = { onOpenPage(BatteryMonitorPage.EXPANDED_CONTENT) },
                onFeatureEnabledChange = stateOwner::setFeatureEnabled,
                onBackToMonitor = onBack,
            )
        }
        BatteryMonitorPage.EXPANDED_CONTENT ->
            ResidentExpandedContentScreen(
                initialConfig = residentConfig,
                configReady = dashboardState.loaded,
                previewValues =
                    residentExpandedPreviewValues(
                        dashboardState.battery,
                        dashboardState.fanPreviewRpm,
                    ),
                fanTokenAvailable = dashboardState.fanAvailable,
                onSave = stateOwner::saveConfig,
                onBack = onBack,
            )
        else -> Unit
    }
}

@Composable
private fun BatteryMonitor(
    page: BatteryMonitorPage,
    stateOwner: ResidentMonitorDashboardStateOwner,
    onOpenPage: (BatteryMonitorPage) -> Unit,
    onBack: () -> Unit,
) {
    if (
        page == BatteryMonitorPage.CONFIGURATION ||
        page == BatteryMonitorPage.EXPANDED_CONTENT
    ) {
        ResidentMonitorSettings(
            page = page,
            stateOwner = stateOwner,
            onOpenPage = onOpenPage,
            onBack = onBack,
        )
        return
    }
    val context = LocalContext.current
    val source = remember { BatteryMetricSource(context) }
    val residentConfigStore = remember { ResidentMonitorConfigStore(context) }
    val contentReady = rememberContentReady()
    var residentConfig by
        remember {
            mutableStateOf(ResidentMonitorUiWarmCache.config() ?: ResidentMonitorConfig())
        }
    DisposableEffect(residentConfigStore, contentReady) {
        val unregister =
            if (contentReady) {
                residentConfigStore.observe(emitInitial = false) { updatedConfig ->
                    ResidentMonitorUiWarmCache.updateConfig(updatedConfig)
                    residentConfig = updatedConfig
                }
            } else {
                {}
            }
        onDispose(unregister)
    }
    val initialRuntime = remember { BatteryMonitorRuntime.current() }
    var snapshot by
        remember {
            mutableStateOf(
                initialRuntime.latestSnapshot
                    ?: ResidentMonitorUiWarmCache.snapshot()
                    ?: EMPTY_BATTERY_METRIC_SNAPSHOT,
            )
        }
    LaunchedEffect(contentReady) {
        if (contentReady) {
            val (loadedConfig, freshSnapshot) =
                withContext(Dispatchers.IO) {
                    residentConfigStore.load() to source.read()
                }
            ResidentMonitorUiWarmCache.updateConfig(loadedConfig)
            ResidentMonitorUiWarmCache.updateSnapshot(freshSnapshot)
            residentConfig = loadedConfig
            snapshot = freshSnapshot
        }
    }
    var operationStatus by rememberSaveable {
        mutableStateOf(
            if (residentConfig.enabled && !initialRuntime.running) {
                "常驻超级岛已开启"
            } else {
                initialRuntime.message
            },
        )
    }
    var monitoringActive by rememberSaveable { mutableStateOf(initialRuntime.running || residentConfig.enabled) }
    LaunchedEffect(residentConfig.enabled) {
        monitoringActive = BatteryMonitorRuntime.current().running || residentConfig.enabled
    }
    var systemEventSummary by rememberSaveable { mutableStateOf(initialRuntime.lastSystemEvent) }
    val rootDeviceAdapter =
        remember {
            RootDeviceAdapterRegistry.capability(
                device = Build.DEVICE,
                fingerprint = Build.FINGERPRINT,
            )
        }
    var miuiFanTitleAvailable by remember { mutableStateOf(false) }
    var miuiFanPreviewRpm by remember { mutableStateOf<Int?>(null) }
    var miuiFanCapabilityChecked by remember { mutableStateOf(false) }
    LaunchedEffect(contentReady) {
        if (!contentReady) return@LaunchedEffect
        val (available, currentRpm) =
            withContext(Dispatchers.IO) {
                supportsMiuiFanTitle() to readMiuiFanRpm()
            }
        miuiFanTitleAvailable = available
        miuiFanPreviewRpm = currentRpm
        miuiFanCapabilityChecked = true
    }
    var rootFanSummary by rememberSaveable {
        mutableStateOf<String?>("${rootDeviceAdapter.summary}\n$ROOT_FAN_NOT_READ_MESSAGE")
    }
    var rootTelemetryMonitoringEnabled by remember {
        mutableStateOf(initialRuntime.rootTelemetryMonitoringEnabled)
    }
    var rootFanMonitoringEnabled by remember { mutableStateOf(initialRuntime.rootFanMonitoringEnabled) }
    var monitoredRootFanSnapshot by remember { mutableStateOf(initialRuntime.latestRootFanSnapshot) }
    var monitoredRootThermalSnapshot by remember { mutableStateOf(initialRuntime.latestRootThermalSnapshot) }
    var monitoredRootPerformanceSnapshot by remember {
        mutableStateOf(initialRuntime.latestRootPerformanceSnapshot)
    }
    var rootThermalSummary by rememberSaveable { mutableStateOf<String?>(null) }
    var rootPerformanceSummary by rememberSaveable { mutableStateOf<String?>(null) }
    val rootFanSource = remember { WarsawFanMetricSource() }
    val rootThermalSource = remember { RootThermalMetricSource() }
    val rootPerformanceSource = remember { WarsawPerformanceMetricSource() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var bluetoothPermissionGeneration by rememberSaveable { mutableIntStateOf(0) }
    val bluetoothPermissionGranted =
        remember(bluetoothPermissionGeneration) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
    val bluetoothPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                BatteryMonitorService.onBluetoothPermissionChanged()
                operationStatus = "蓝牙设备访问已允许"
            } else {
                operationStatus = "蓝牙设备访问未允许；不影响基础监控"
            }
            bluetoothPermissionGeneration += 1
        }

    DisposableEffect(Unit) {
        val unregister =
            BatteryMonitorRuntime.observe { runtime ->
                runtime.latestSnapshot?.let { snapshot = it }
                // SystemUI owns the monitor after activation. Its app process may be absent, so a
                // false in-process runtime must not turn off the persisted feature switch.
                monitoringActive = runtime.running || residentConfig.enabled
                operationStatus = runtime.message
                systemEventSummary = runtime.lastSystemEvent
                rootTelemetryMonitoringEnabled = runtime.rootTelemetryMonitoringEnabled
                rootFanMonitoringEnabled = runtime.rootFanMonitoringEnabled
                monitoredRootFanSnapshot = runtime.latestRootFanSnapshot
                monitoredRootThermalSnapshot = runtime.latestRootThermalSnapshot
                monitoredRootPerformanceSnapshot = runtime.latestRootPerformanceSnapshot
            }
        onDispose(unregister)
    }

    fun refresh() {
        snapshot = source.read()
        operationStatus = "已刷新公开电池指标"
    }

    fun saveResidentConfig(config: ResidentMonitorConfig): Boolean {
        val normalized = config.normalized()
        if (normalized == residentConfig) return true
        residentConfig = normalized
        ResidentMonitorUiWarmCache.updateConfig(normalized)
        val applied = residentConfigStore.save(normalized).isSuccess
        BatteryMonitorService.onResidentMonitorConfigurationChanged(normalized)
        return applied
    }

    LaunchedEffect(miuiFanCapabilityChecked, miuiFanTitleAvailable) {
        if (
            miuiFanCapabilityChecked &&
            !miuiFanTitleAvailable &&
            (residentConfig.leftTitleMetric == ResidentMetricKey.FAN_RPM ||
                residentConfig.rightTitleMetric == ResidentMetricKey.FAN_RPM)
        ) {
            saveResidentConfig(
                residentConfig.copy(
                    leftTitleMetric =
                        residentConfig.leftTitleMetric.takeUnless { it == ResidentMetricKey.FAN_RPM }
                            ?: ResidentMetricKey.BATTERY_PERCENT,
                    rightTitleMetric =
                        residentConfig.rightTitleMetric.takeUnless { it == ResidentMetricKey.FAN_RPM }
                            ?: ResidentMetricKey.BATTERY_PERCENT,
                ),
            )
        }
    }

    fun startContinuousMonitoring() {
        saveResidentConfig(residentConfig.copy(enabled = true))
        BatteryMonitorService.start(context = context).onSuccess {
            operationStatus = "常驻超级岛已开启"
        }.onFailure { error ->
            operationStatus = error.message ?: error.javaClass.simpleName
        }
    }

    fun readRootFan() {
        if (!rootDeviceAdapter.supports(RootDeviceFeature.FAN_TELEMETRY)) {
            rootFanSummary = rootDeviceAdapter.summary
            return
        }
        rootFanSummary = "正在读取固定 Root 风扇节点…"
        Thread {
            val result = rootFanSource.read(rootDeviceAdapter)
            mainHandler.post {
                rootFanSummary =
                    result.fold(
                        onSuccess = { fan ->
                            "只读节点 · ${fan.rpm} RPM · 档位 ${fan.targetLevel} · PWM ${fan.pwmDutyPercent}%"
                        },
                        onFailure = {
                            "读取失败 · 请确认已在 KernelSU/Magisk 中授予本应用 Root 权限"
                        },
                    )
            }
        }.start()
    }

    fun readRootThermal() {
        rootThermalSummary = "正在读取固定 Root thermalservice 诊断…"
        Thread {
            val result = rootThermalSource.read()
            mainHandler.post {
                result.fold(
                    onSuccess = { thermal ->
                        rootThermalSummary = thermal.rootSummary()
                        operationStatus = "已完成 Root 只读温度诊断"
                    },
                    onFailure = {
                        rootThermalSummary = "读取失败 · 仅允许固定 thermalservice 诊断"
                        operationStatus = "Root 温度诊断未完成"
                    },
                )
            }
        }.start()
    }

    fun readRootPerformance() {
        if (!rootDeviceAdapter.supports(RootDeviceFeature.PERFORMANCE_TELEMETRY)) {
            rootPerformanceSummary = rootDeviceAdapter.summary
            return
        }
        rootPerformanceSummary = "正在读取固定 Root CPU/GPU 频率节点…"
        Thread {
            val result = rootPerformanceSource.read(rootDeviceAdapter)
            mainHandler.post {
                result.fold(
                    onSuccess = { performance ->
                        rootPerformanceSummary = performance.rootSummary()
                        operationStatus = "已完成 Root 只读频率诊断"
                    },
                    onFailure = {
                        rootPerformanceSummary = "读取失败 · 仅允许固定 warsaw CPU/GPU 频率节点"
                        operationStatus = "Root 频率诊断未完成"
                    },
                )
            }
        }.start()
    }

    val sourceSummary =
        "公开 BatteryManager + 固定 Root thermalservice、warsaw 风扇与 CPU/GPU 频率诊断"
    val networkEventStatus =
        "无需运行时权限 · 监控运行时监听默认网络切换；不读取 Wi-Fi 名称、地址或网络内容"
    val bluetoothEventStatus =
        if (bluetoothPermissionGranted) {
            "已允许 · 监控运行时监听设备连接、断开及蓝牙关闭"
        } else {
            "未允许 · 可选权限；不影响基础监控、媒体岛或有线耳机事件"
        }
    val rootThermalDisplaySummary =
        when {
            rootTelemetryMonitoringEnabled && monitoredRootThermalSnapshot != null ->
                "持续监控 · ${monitoredRootThermalSnapshot!!.rootSummary()}"
            rootTelemetryMonitoringEnabled ->
                "持续监控已启用 · 本轮未取得 Root 温度数据，不显示伪造数值"
            else -> rootThermalSummary
        }
    val rootPerformanceDisplaySummary =
        when {
            rootTelemetryMonitoringEnabled && monitoredRootPerformanceSnapshot != null ->
                "持续监控 · ${monitoredRootPerformanceSnapshot!!.rootSummary()}"
            rootTelemetryMonitoringEnabled &&
                rootDeviceAdapter.supports(RootDeviceFeature.PERFORMANCE_TELEMETRY) ->
                "持续监控已启用 · 本轮未取得 Root CPU/GPU 频率，不显示伪造数值"
            else -> rootPerformanceSummary
        }
    val rootFanDisplaySummary =
        when {
            rootFanMonitoringEnabled && monitoredRootFanSnapshot != null ->
                "持续监控 · ${monitoredRootFanSnapshot!!.monitorSummary()}"
            rootFanMonitoringEnabled ->
                "持续监控已启用 · 本轮未取得 Root 风扇数据，不显示 RPM"
            else -> rootFanSummary
        }
    val cancelMonitoring = {
        saveResidentConfig(residentConfig.copy(enabled = false))
        BatteryMonitorService.stop(context)
        monitoringActive = false
        rootTelemetryMonitoringEnabled = false
        rootFanMonitoringEnabled = false
        monitoredRootFanSnapshot = null
        monitoredRootThermalSnapshot = null
        monitoredRootPerformanceSnapshot = null
        operationStatus = "正在结束监控岛"
    }

    when (page) {
        BatteryMonitorPage.OVERVIEW ->
            AppDirectoryDetailScreen(
                title = "常驻超级岛",
                subtitle = ROOT_MODE_LABEL,
                header = {
                    AppFeatureMasterSwitch(
                        title = "启用常驻超级岛",
                        summary =
                            if (monitoringActive) {
                                "正在发布真实系统状态与已允许的事件"
                            } else {
                                "已关闭；不会发布新的常驻超级岛内容"
                            },
                        checked = monitoringActive,
                        onCheckedChange = { enabled ->
                            if (enabled) startContinuousMonitoring() else cancelMonitoring()
                        },
                    )
                },
                onBack = onBack,
                groups =
                    listOf(
                        DirectoryGroupUi(
                            title = "监控",
                            entries =
                                listOf(
                                    DirectoryEntryUi(
                                        id = "realtime",
                                        title = "实时指标",
                                        summary = "电量、电流、功耗和电池温度",
                                        icon = DirectoryIcon.MONITOR,
                                        status = snapshot.batterySummary(),
                                    ),
                                    DirectoryEntryUi(
                                        id = "continuous",
                                        title = "持续监控",
                                        summary = "将系统状态和事件持续显示到超级岛",
                                        icon = DirectoryIcon.SUPER_ISLAND,
                                        status = if (monitoringActive) "运行中" else "未启动",
                                    ),
                                    DirectoryEntryUi(
                                        id = "configuration",
                                        title = "显示与布局",
                                        summary = "标题、刷新间隔、展开内容与左右显示",
                                        icon = DirectoryIcon.APPEARANCE,
                                        status = "${residentConfig.titleRefreshIntervalMillis / 1_000} 秒",
                                    ),
                                    DirectoryEntryUi(
                                        id = "events",
                                        title = "事件与权限",
                                        summary = "网络切换、蓝牙设备和可选访问权限",
                                        icon = DirectoryIcon.INFO,
                                        status = if (bluetoothPermissionGranted) "蓝牙已允许" else "可选权限",
                                    ),
                                ),
                        ),
                        DirectoryGroupUi(
                            title = "设备",
                            entries =
                                listOf(
                                    DirectoryEntryUi(
                                        id = "diagnostics",
                                        title = "设备诊断",
                                        summary = "固定只读温度、频率和机型增强指标",
                                        icon = DirectoryIcon.DEVICE,
                                    ),
                                ),
                        ),
                    ),
                onEntrySelected = { id ->
                    when (id) {
                        "realtime" -> onOpenPage(BatteryMonitorPage.REALTIME)
                        "continuous" -> onOpenPage(BatteryMonitorPage.CONTINUOUS)
                        "configuration" -> onOpenPage(BatteryMonitorPage.CONFIGURATION)
                        "events" -> onOpenPage(BatteryMonitorPage.EVENTS)
                        "diagnostics" -> onOpenPage(BatteryMonitorPage.DIAGNOSTICS)
                    }
                },
            )
        BatteryMonitorPage.REALTIME ->
            BatteryRealtimeScreen(
                modeLabel = ROOT_MODE_LABEL,
                batterySummary = snapshot.batterySummary(),
                currentSummary = snapshot.currentSummary(),
                powerSummary = snapshot.powerSummary(),
                temperatureSummary = snapshot.temperatureSummary(),
                batteryLevelPercent = snapshot.levelPercent,
                onRefresh = ::refresh,
                onBackToMonitor = onBack,
            )
        BatteryMonitorPage.CONTINUOUS ->
            BatteryContinuousMonitorScreen(
                modeLabel = ROOT_MODE_LABEL,
                operationStatus = operationStatus,
                systemEventSummary = systemEventSummary,
                monitoringActive = monitoringActive,
                onStart = ::startContinuousMonitoring,
                onCancel = cancelMonitoring,
                onBackToMonitor = onBack,
            )
        BatteryMonitorPage.EVENTS ->
            BatteryMonitorEventsScreen(
                modeLabel = ROOT_MODE_LABEL,
                networkEventStatus = networkEventStatus,
                bluetoothEventStatus = bluetoothEventStatus,
                showBluetoothPermissionAction = !bluetoothPermissionGranted,
                onRequestBluetoothPermission = {
                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                },
                onBackToMonitor = onBack,
            )
        BatteryMonitorPage.DIAGNOSTICS ->
            BatteryMonitorDiagnosticsScreen(
                modeLabel = ROOT_MODE_LABEL,
                sourceSummary = sourceSummary,
                rootThermalSummary = rootThermalDisplaySummary,
                rootPerformanceSummary = rootPerformanceDisplaySummary,
                rootFanSummary = rootFanDisplaySummary,
                onReadRootThermal = ::readRootThermal,
                onReadRootPerformance = ::readRootPerformance,
                onReadRootFan = ::readRootFan,
                onBackToMonitor = onBack,
            )
        BatteryMonitorPage.CONFIGURATION -> {
            val titleMetrics =
                ResidentMonitorConfig.TITLE_METRICS.filter { metric ->
                    metric != ResidentMetricKey.FAN_RPM || miuiFanTitleAvailable
                }
            fun titleOptions(selectedMetric: ResidentMetricKey) =
                titleMetrics.map { metric ->
                    ResidentMetricOptionUi(
                        id = metric.name,
                        title = metric.displayName,
                        summary =
                            if (metric == ResidentMetricKey.FAN_RPM) {
                                "当前机型风扇实时转速"
                            } else {
                                "系统实时指标"
                            },
                        selected = metric == selectedMetric,
                    )
                }
            fun iconOptions(
                current: ResidentIslandIcon,
                side: String,
            ) =
                listOf(
                    ResidentIslandIcon.FOLLOW_TITLE to "默认",
                    ResidentIslandIcon.NONE to "不显示",
                ).map { (icon, title) ->
                    ResidentSlotOptionUi(
                        id = icon.name,
                        title = title,
                        summary = "立即控制超级岛${side}侧图标",
                        selected = current == icon,
                    )
                }
            ResidentMonitorConfigurationScreen(
                featureEnabled = monitoringActive,
                leftIconOptions = iconOptions(residentConfig.leftIcon, "左"),
                rightIconOptions = iconOptions(residentConfig.rightIcon, "右"),
                leftTitleOptions = titleOptions(residentConfig.leftTitleMetric),
                rightTitleOptions = titleOptions(residentConfig.rightTitleMetric),
                refreshIntervalSeconds = (residentConfig.titleRefreshIntervalMillis / 1_000).toInt(),
                onSelectLeftIcon = { id ->
                    ResidentIslandIcon.entries.firstOrNull { it.name == id }?.let { icon ->
                        saveResidentConfig(residentConfig.copy(leftIcon = icon))
                    }
                },
                onSelectRightIcon = { id ->
                    ResidentIslandIcon.entries.firstOrNull { it.name == id }?.let { icon ->
                        saveResidentConfig(residentConfig.copy(rightIcon = icon))
                    }
                },
                onSelectLeftTitle = { id ->
                    ResidentMetricKey.entries.firstOrNull { it.name == id }?.let { metric ->
                        saveResidentConfig(residentConfig.copy(leftTitleMetric = metric))
                    }
                },
                onSelectRightTitle = { id ->
                    ResidentMetricKey.entries.firstOrNull { it.name == id }?.let { metric ->
                        saveResidentConfig(residentConfig.copy(rightTitleMetric = metric))
                    }
                },
                onRefreshIntervalChange = { seconds ->
                    saveResidentConfig(residentConfig.copy(titleRefreshIntervalMillis = seconds * 1_000L))
                },
                onOpenExpandedContent = { onOpenPage(BatteryMonitorPage.EXPANDED_CONTENT) },
                onFeatureEnabledChange = { enabled ->
                    if (enabled) startContinuousMonitoring() else cancelMonitoring()
                },
                onBackToMonitor = onBack,
            )
        }
        BatteryMonitorPage.EXPANDED_CONTENT ->
            ResidentExpandedContentScreen(
                initialConfig = residentConfig,
                previewValues = residentExpandedPreviewValues(snapshot, miuiFanPreviewRpm),
                fanTokenAvailable = miuiFanTitleAvailable,
                onSave = ::saveResidentConfig,
                onBack = onBack,
            )
    }
}

fun BatteryMetricSnapshot.monitorFocusNotificationRequest(
    config: ResidentMonitorConfig = ResidentMonitorConfig(),
    systemEvent: BatterySystemEvent? = null,
    rootFanSnapshot: WarsawFanMetricSnapshot? = null,
    rootThermalSnapshot: ThermalDiagnosticSnapshot? = null,
    rootPerformanceSnapshot: WarsawPerformanceMetricSnapshot? = null,
): FocusNotificationRequest =
    FocusNotificationRequest(
        title =
            config.leftTitleMetric.metricValue(
                snapshot = this,
                rootFanSnapshot = rootFanSnapshot,
                rootThermalSnapshot = rootThermalSnapshot,
                rootPerformanceSnapshot = rootPerformanceSnapshot,
            ) ?: ResidentMetricKey.BATTERY_PERCENT.metricValue(
                snapshot = this,
                rootFanSnapshot = rootFanSnapshot,
                rootThermalSnapshot = rootThermalSnapshot,
                rootPerformanceSnapshot = rootPerformanceSnapshot,
            ) ?: "设备状态",
        text =
            listOfNotNull(
                systemEvent?.displayName,
                *config.expandedMetrics
                    .mapNotNull { metric ->
                        metric.metricValue(this, rootFanSnapshot, rootThermalSnapshot, rootPerformanceSnapshot)
                            ?.let { value -> "${metric.displayName}: $value" }
                    }
                    .toTypedArray(),
            ).joinToString(" · ").ifBlank { "设备状态正在更新" },
        progress = levelPercent ?: 0,
        shortStatusText =
            config.rightTitleMetric.metricValue(
                snapshot = this,
                rootFanSnapshot = rootFanSnapshot,
                rootThermalSnapshot = rootThermalSnapshot,
                rootPerformanceSnapshot = rootPerformanceSnapshot,
            ) ?: ResidentMetricKey.BATTERY_PERCENT.metricValue(
                snapshot = this,
                rootFanSnapshot = rootFanSnapshot,
                rootThermalSnapshot = rootThermalSnapshot,
                rootPerformanceSnapshot = rootPerformanceSnapshot,
            ) ?: "系统状态",
    )

private fun ResidentMetricKey.metricValue(
    snapshot: BatteryMetricSnapshot,
    rootFanSnapshot: WarsawFanMetricSnapshot?,
    rootThermalSnapshot: ThermalDiagnosticSnapshot?,
    rootPerformanceSnapshot: WarsawPerformanceMetricSnapshot?,
): String? =
    when (this) {
        ResidentMetricKey.BATTERY_PERCENT -> snapshot.levelPercent?.let { "$it%" }
        ResidentMetricKey.CHARGE_STATE -> snapshot.chargeState.displayName
        ResidentMetricKey.CURRENT -> snapshot.currentSummary()
        ResidentMetricKey.VOLTAGE -> snapshot.voltageMillivolts?.let { "${it}mV" }
        ResidentMetricKey.POWER -> ResidentMetricFormatter.powerWattsFromMicrowatts(snapshot.powerMicrowatts)
        ResidentMetricKey.BATTERY_TEMPERATURE -> snapshot.temperatureSummary()
        ResidentMetricKey.CPU_TEMPERATURE -> rootThermalSnapshot?.cpuMaxCelsius?.let { "${it.roundedCelsius()}" }
        ResidentMetricKey.GPU_TEMPERATURE -> rootThermalSnapshot?.gpuMaxCelsius?.let { "${it.roundedCelsius()}" }
        ResidentMetricKey.SKIN_TEMPERATURE -> rootThermalSnapshot?.skinCelsius?.let { "${it.roundedCelsius()}" }
        ResidentMetricKey.FAN_RPM -> ResidentMetricFormatter.fanRpm(rootFanSnapshot?.rpm)
        ResidentMetricKey.FAN_LEVEL -> rootFanSnapshot?.targetLevel?.let { "档位 $it" }
        ResidentMetricKey.CPU_FREQUENCY -> rootPerformanceSnapshot?.let { "${it.cpuPolicy0Mhz}/${it.cpuPolicy6Mhz}MHz" }
        ResidentMetricKey.GPU_FREQUENCY -> rootPerformanceSnapshot?.gpuMhz?.let { "$it MHz" }
        ResidentMetricKey.CUSTOM_TEMPLATE -> null
    }

private fun BatteryMetricSnapshot.batterySummary(): String =
    "${levelPercent?.let { "$it%" } ?: "未知"} · ${chargeState.displayName}"

fun BatteryMetricSnapshot.currentSummary(): String =
    ResidentMetricFormatter.currentMicroAmps(currentMicroAmps)
        ?: "当前设备未公开电流属性"

fun BatteryMetricSnapshot.powerSummary(): String =
    ResidentMetricFormatter.powerWattsFromMicrowatts(powerMicrowatts)
        ?: "缺少电流或电压，无法计算功耗"

fun BatteryMetricSnapshot.temperatureSummary(): String =
    ResidentMetricFormatter.temperatureTenthsCelsius(temperatureTenthsCelsius)
        ?: "设备未提供电池温度"

private fun ThermalDiagnosticSnapshot.rootSummary(): String {
    return thermalSummary("Root thermalservice")
}

private fun ThermalDiagnosticSnapshot.thermalSummary(sourceLabel: String): String {
    val metrics =
        listOfNotNull(
            cpuMaxCelsius?.let { "CPU 最高 ${it.roundedCelsius()}" },
            gpuMaxCelsius?.let { "GPU 最高 ${it.roundedCelsius()}" },
            skinCelsius?.let { "皮温 ${it.roundedCelsius()}" },
            batteryCelsius?.let { "电池 ${it.roundedCelsius()}" },
        )
    return buildString {
        append("$sourceLabel · $sensorCount 项 HAL 温度")
        if (metrics.isNotEmpty()) {
            append(" · ")
            append(metrics.joinToString(" · "))
        }
        append("\n仅显示本次聚合结果，不保存原始输出")
    }
}

private fun WarsawFanMetricSnapshot.monitorSummary(): String =
    "${rpm} RPM · 档位 $targetLevel · PWM ${pwmDutyPercent}%"

private fun WarsawFanMetricSnapshot.compactMonitorStatus(): String = "风扇 ${rpm}RPM"

private fun ThermalDiagnosticSnapshot.compactMonitorStatus(): String? =
    listOfNotNull(
        cpuMaxCelsius?.let { "CPU ${it.roundedCelsius()}" },
        gpuMaxCelsius?.let { "GPU ${it.roundedCelsius()}" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

private fun WarsawPerformanceMetricSnapshot.rootSummary(): String =
    "CPU0-5 ${cpuPolicy0Mhz} MHz · CPU6-7 ${cpuPolicy6Mhz} MHz · GPU ${gpuMhz} MHz"

private fun WarsawPerformanceMetricSnapshot.compactMonitorStatus(): String =
    "CPU ${cpuPolicy0Mhz}/${cpuPolicy6Mhz}MHz · GPU ${gpuMhz}MHz"

private fun Double.roundedCelsius(): String =
    "${kotlin.math.round(this * 10) / 10.0} °C"

private fun Int.compactCurrentStatus(): String =
    if (kotlin.math.abs(this) >= 1_000) {
        "${signPrefix()}${kotlin.math.abs(this) / 1_000}mA"
    } else {
        "${signPrefix()}${kotlin.math.abs(this)}µA"
    }

private fun Int.signPrefix(): String =
    when {
        this > 0 -> "+"
        this < 0 -> "-"
        else -> ""
    }

private fun Int.channelImportanceLabel(): String =
    when (this) {
        NotificationManager.IMPORTANCE_NONE -> "已关闭"
        NotificationManager.IMPORTANCE_MIN -> "最低"
        NotificationManager.IMPORTANCE_LOW -> "低"
        NotificationManager.IMPORTANCE_DEFAULT -> "默认"
        NotificationManager.IMPORTANCE_HIGH -> "高"
        NotificationManager.IMPORTANCE_MAX -> "最高"
        else -> "系统默认"
    }

internal fun supportsMiuiFanTitle(): Boolean {
    val reportedByMiuiInterface =
        runCatching {
            val chargeClass = Class.forName("miui.util.IMiCharge")
            val instance = chargeClass.getDeclaredMethod("getInstance").apply { isAccessible = true }.invoke(null)
            val readMethod = instance.javaClass.getDeclaredMethod("getMiChargePath", String::class.java).apply {
                isAccessible = true
            }
            readMethod.invoke(instance, "fan_support")?.toString()?.trim() == "1"
        }.getOrDefault(false)
    // This allowlist only exposes the picker on known fan-equipped products. RPM itself is still
    // accepted exclusively from SystemUI's MIUI interface; no sysfs path is read as a fallback.
    return reportedByMiuiInterface || Build.DEVICE in MIUI_FAN_TITLE_DEVICES
}

/** Reads only Xiaomi's public-to-SystemUI MIUI charge abstraction; no device sysfs fallback. */
internal fun readMiuiFanRpm(): Int? =
    runCatching {
        val chargeClass = Class.forName("miui.util.IMiCharge")
        val instance = chargeClass.getDeclaredMethod("getInstance").apply { isAccessible = true }.invoke(null)
        val readMethod =
            instance.javaClass.getDeclaredMethod("getMiChargePath", String::class.java).apply {
                isAccessible = true
            }
        readMethod
            .invoke(instance, "fan_real_speed")
            ?.toString()
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { rpm -> rpm in 0..50_000 }
    }.getOrNull()

val BatteryChargeState.displayName: String
    get() =
        when (this) {
            BatteryChargeState.CHARGING -> "充电中"
            BatteryChargeState.DISCHARGING -> "放电中"
            BatteryChargeState.FULL -> "已充满"
            BatteryChargeState.NOT_CHARGING -> "未充电"
            BatteryChargeState.UNKNOWN -> "状态未知"
        }

private fun Context.contentIntent(): PendingIntent =
    PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

private fun Context.openHyperOsAutostartSettings() {
    val autostartIntent =
        Intent(HYPEROS_AUTOSTART_ACTION)
            .setPackage(HYPEROS_SECURITY_CENTER_PACKAGE)
    if (autostartIntent.resolveActivity(packageManager) != null) {
        startActivity(autostartIntent)
    } else {
        openAppDetailsSettings()
    }
}

private fun Context.openAppDetailsSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        ),
    )
}

private const val HYPEROS_AUTOSTART_ACTION = "miui.intent.action.OP_AUTO_START"
private const val HYPEROS_SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
const val BATTERY_MONITOR_NOTIFICATION_ID = 0x424d
const val DEBUG_BATTERY_MONITOR_ACTION = "io.github.superisland.DEBUG_BATTERY_MONITOR"
private const val DEBUG_PUBLISH_BATTERY_MONITOR_ACTION =
    "io.github.superisland.DEBUG_PUBLISH_BATTERY_MONITOR"
private const val DEBUG_CANCEL_BATTERY_MONITOR_ACTION =
    "io.github.superisland.DEBUG_CANCEL_BATTERY_MONITOR"
private const val DEBUG_START_CONTINUOUS_BATTERY_MONITOR_ACTION =
    "io.github.superisland.DEBUG_START_CONTINUOUS_BATTERY_MONITOR"
private const val DEBUG_STOP_CONTINUOUS_BATTERY_MONITOR_ACTION =
    "io.github.superisland.DEBUG_STOP_CONTINUOUS_BATTERY_MONITOR"
private const val DEBUG_REFRESH_MEDIA_ACTION = "io.github.superisland.DEBUG_REFRESH_MEDIA"
private const val DEBUG_ENABLE_TEST_MEDIA_ACTION = "io.github.superisland.DEBUG_ENABLE_TEST_MEDIA"
private const val DEBUG_ENABLE_TEST_SMART_CAPSULE_ACTION =
    "io.github.superisland.DEBUG_ENABLE_TEST_SMART_CAPSULE"
private const val DEBUG_DISABLE_TEST_SMART_CAPSULE_ACTION =
    "io.github.superisland.DEBUG_DISABLE_TEST_SMART_CAPSULE"
private const val DEBUG_SIMULATE_WIRED_HEADSET_CONNECTED_ACTION =
    "io.github.superisland.DEBUG_SIMULATE_WIRED_HEADSET_CONNECTED"
private const val DEBUG_SIMULATE_WIRED_HEADSET_DISCONNECTED_ACTION =
    "io.github.superisland.DEBUG_SIMULATE_WIRED_HEADSET_DISCONNECTED"
private const val DEBUG_SIMULATE_BLUETOOTH_CONNECTED_ACTION =
    "io.github.superisland.DEBUG_SIMULATE_BLUETOOTH_CONNECTED"
private const val DEBUG_SIMULATE_BLUETOOTH_DISCONNECTED_ACTION =
    "io.github.superisland.DEBUG_SIMULATE_BLUETOOTH_DISCONNECTED"
private const val TEST_SOURCE_PACKAGE = "io.github.superisland.testsource"
private const val ROOT_FAN_NOT_READ_MESSAGE = "固定 warsaw 只读节点；尚未请求 Root 授权"
private val MIUI_FAN_TITLE_DEVICES = setOf("warsaw", "prague")
internal val EMPTY_BATTERY_METRIC_SNAPSHOT =
    BatteryMetricSnapshot(
        levelPercent = null,
        chargeState = BatteryChargeState.UNKNOWN,
        currentMicroAmps = null,
        voltageMillivolts = null,
        temperatureTenthsCelsius = null,
        capturedAtMillis = 0L,
    )

private fun Intent?.debugDestination(): AppDestination =
    if (
        BuildConfig.DEBUG &&
        this?.action in
        setOf(
            DEBUG_BATTERY_MONITOR_ACTION,
            DEBUG_START_CONTINUOUS_BATTERY_MONITOR_ACTION,
            DEBUG_STOP_CONTINUOUS_BATTERY_MONITOR_ACTION,
            DEBUG_REFRESH_MEDIA_ACTION,
            DEBUG_ENABLE_TEST_MEDIA_ACTION,
            DEBUG_ENABLE_TEST_SMART_CAPSULE_ACTION,
            DEBUG_DISABLE_TEST_SMART_CAPSULE_ACTION,
            DEBUG_SIMULATE_WIRED_HEADSET_CONNECTED_ACTION,
            DEBUG_SIMULATE_WIRED_HEADSET_DISCONNECTED_ACTION,
            DEBUG_SIMULATE_BLUETOOTH_CONNECTED_ACTION,
            DEBUG_SIMULATE_BLUETOOTH_DISCONNECTED_ACTION,
        )
    ) {
        if (
            this?.action == DEBUG_ENABLE_TEST_SMART_CAPSULE_ACTION ||
            this?.action == DEBUG_DISABLE_TEST_SMART_CAPSULE_ACTION
        ) {
            AppDestination.SMART_CAPSULE_APPS
        } else if (
            this?.action == DEBUG_REFRESH_MEDIA_ACTION ||
            this?.action == DEBUG_ENABLE_TEST_MEDIA_ACTION
        ) {
            AppDestination.SUPER_ISLAND
        } else {
            AppDestination.BATTERY_MONITOR
        }
    } else {
        AppDestination.HOME
    }

private enum class SmartCapsulePage {
    APPS,
    CHANNELS,
    CHANNEL_DETAIL,
}

private const val FIRST_RESUME_GENERATION = 1
private const val INHERIT_ISLAND_PRIORITY_ID = "inherit"

private val appIslandPriorityOptions =
    IslandPriority.entries.map { priority ->
        IslandPriorityOptionUi(priority.name, priority.displayName)
    }

private val channelIslandPriorityOptions =
    listOf(IslandPriorityOptionUi(INHERIT_ISLAND_PRIORITY_ID, "跟随应用")) +
        appIslandPriorityOptions

private val IslandPriority.displayName: String
    get() =
        when (this) {
            IslandPriority.HIGH -> "高"
            IslandPriority.MEDIUM -> "中"
            IslandPriority.LOW -> "低"
        }

private fun String.toIslandPriorityNameOrNull(): IslandPriority? =
    IslandPriority.entries.firstOrNull { priority -> priority.name == this }

private enum class BatteryMonitorPage {
    OVERVIEW,
    REALTIME,
    CONTINUOUS,
    EVENTS,
    DIAGNOSTICS,
    CONFIGURATION,
    EXPANDED_CONTENT,
}

private val BatteryMonitorPage.destination: AppDestination
    get() =
        when (this) {
            BatteryMonitorPage.OVERVIEW -> AppDestination.BATTERY_MONITOR
            BatteryMonitorPage.REALTIME -> AppDestination.BATTERY_REALTIME
            BatteryMonitorPage.CONTINUOUS -> AppDestination.BATTERY_CONTINUOUS
            BatteryMonitorPage.EVENTS -> AppDestination.BATTERY_EVENTS
            BatteryMonitorPage.DIAGNOSTICS -> AppDestination.BATTERY_DIAGNOSTICS
            BatteryMonitorPage.CONFIGURATION -> AppDestination.BATTERY_CONFIGURATION
            BatteryMonitorPage.EXPANDED_CONTENT -> AppDestination.BATTERY_EXPANDED_CONTENT
        }

private val AppPrimaryTab.destination: AppDestination
    get() =
        when (this) {
            AppPrimaryTab.HOME -> AppDestination.HOME
            AppPrimaryTab.SUPER_ISLAND -> AppDestination.SUPER_ISLAND
            AppPrimaryTab.EXTENSIONS -> AppDestination.EXTENSIONS
            AppPrimaryTab.SETTINGS -> AppDestination.SETTINGS
            AppPrimaryTab.PROFILE -> AppDestination.PROFILE
        }

private val AppPrimaryTab.pageIndex: Int
    get() = ordinal

private val AppDestination.primaryTab: AppPrimaryTab
    get() =
        when (this) {
            AppDestination.SUPER_ISLAND,
            AppDestination.SMART_CAPSULE_APPS,
            AppDestination.CAPSULE_APPEARANCE,
            AppDestination.BATTERY_MONITOR,
            AppDestination.BATTERY_REALTIME,
            AppDestination.BATTERY_CONTINUOUS,
            AppDestination.BATTERY_EVENTS,
            AppDestination.BATTERY_DIAGNOSTICS,
            AppDestination.BATTERY_CONFIGURATION,
            AppDestination.BATTERY_EXPANDED_CONTENT,
            AppDestination.LYRIC,
            -> AppPrimaryTab.SUPER_ISLAND
            AppDestination.EXTENSIONS,
            AppDestination.EXTENSION_MISHARE_FOLDER,
            AppDestination.EXTENSION_SCREEN_RECORDING,
            -> AppPrimaryTab.EXTENSIONS
            AppDestination.SETTINGS,
            AppDestination.SETTINGS_THEME,
            -> AppPrimaryTab.SETTINGS
            AppDestination.PROFILE,
            AppDestination.PROFILE_USAGE_GUIDE,
            AppDestination.PROFILE_ABOUT,
            -> AppPrimaryTab.PROFILE
            AppDestination.MAIN,
            AppDestination.HOME,
            AppDestination.HOME_SERVICE_STATUS,
            AppDestination.HOME_USAGE_GUIDE,
            AppDestination.HOME_ABOUT,
            -> AppPrimaryTab.HOME
        }
