package io.github.superisland.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.superisland.design.AppPrimaryTab
import io.github.superisland.design.DirectoryGroupUi
import io.github.superisland.design.DirectoryIcon
import io.github.superisland.model.AppAppearanceSettings
import io.github.superisland.model.AppThemeMode
import io.github.superisland.model.AppUiMode
import io.github.superisland.appearance.toKernelSuColorMode
import io.github.superisland.appearance.LocalAppAppearanceSettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.superisland.model.RuntimeEnvironmentSnapshot
import io.github.superisland.ui.navigation.AppDestination
import io.github.superisland.ui.extensionsDirectoryGroups
import io.github.superisland.ui.home.HomeScreen
import io.github.superisland.ui.profileDirectoryGroups
import io.github.superisland.ui.superIslandDirectoryGroups
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import me.weishu.kernelsu.ui.screen.colorpalette.ColorPaletteScreenActions
import me.weishu.kernelsu.ui.screen.colorpalette.ColorPaletteScreenMaterial as KernelSuColorPaletteScreenMaterial
import me.weishu.kernelsu.ui.screen.colorpalette.ColorPaletteUiState
import me.weishu.kernelsu.ui.screen.settings.SettingsUiState
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors

/**
 * Material 3 root shell. Its shape deliberately mirrors the KernelSU root pager contract: only
 * the persistent bottom bar height is given to the pager; every page owns its own top insets.
 */
@Composable
fun MaterialPrimaryPagerScaffold(
    selectedTab: AppPrimaryTab,
    onTabSelected: (AppPrimaryTab) -> Unit,
    content: @Composable (bottomInnerPadding: Dp) -> Unit,
) {
    val navigationItems: @Composable () -> Unit = {
        AppPrimaryTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            ShortNavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.materialIcon(selected = selected),
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        bottomBar = {
            ShortNavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                // This is KernelSU's Material bottom-bar inset contract.
                windowInsets =
                    WindowInsets.systemBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                content = navigationItems,
            )
        },
    ) { innerPadding ->
        content(innerPadding.calculateBottomPadding())
    }
}

@Composable
fun MaterialPrimaryPage(
    tab: AppPrimaryTab,
    environment: RuntimeEnvironmentSnapshot,
    appearance: AppAppearanceSettings,
    onTabSelected: (AppPrimaryTab) -> Unit,
    onUiModeChange: (AppUiMode) -> Unit,
    onOpenTheme: () -> Unit,
    onOpenDestination: (AppDestination) -> Unit,
    bottomInnerPadding: Dp = 0.dp,
) {
    when (tab) {
        AppPrimaryTab.HOME ->
            HomeScreen(
                environment = environment,
                onTabSelected = onTabSelected,
                showBottomBar = false,
                bottomInnerPadding = bottomInnerPadding,
            )
        AppPrimaryTab.SUPER_ISLAND ->
            MaterialSuperIslandDirectory(
                onOpenDestination = onOpenDestination,
                bottomInnerPadding = bottomInnerPadding,
            )
        AppPrimaryTab.EXTENSIONS ->
            MaterialDirectoryPage(
                title = "拓展",
                groups = extensionsDirectoryGroups(),
                onEntrySelected = { entryId ->
                    onOpenDestination(
                        when (entryId) {
                            "mishare_folder" -> AppDestination.EXTENSION_MISHARE_FOLDER
                            "screen_recording" -> AppDestination.EXTENSION_SCREEN_RECORDING
                            else -> AppDestination.EXTENSIONS
                        },
                    )
                },
                bottomInnerPadding = bottomInnerPadding,
            )
        AppPrimaryTab.SETTINGS ->
            MaterialSettingsPage(
                appearance = appearance,
                onUiModeChange = onUiModeChange,
                onOpenTheme = onOpenTheme,
                bottomInnerPadding = bottomInnerPadding,
            )
        AppPrimaryTab.PROFILE ->
            MaterialDirectoryPage(
                title = "我的",
                groups = profileDirectoryGroups(),
                onEntrySelected = { entryId ->
                    onOpenDestination(
                        if (entryId == "about") AppDestination.PROFILE_ABOUT else AppDestination.PROFILE_USAGE_GUIDE,
                    )
                },
                bottomInnerPadding = bottomInnerPadding,
            )
    }
}

@Composable
private fun MaterialSuperIslandDirectory(
    onOpenDestination: (AppDestination) -> Unit,
    bottomInnerPadding: Dp,
) {
    MaterialDirectoryPage(
        title = "超级岛",
        groups = superIslandDirectoryGroups(),
        onEntrySelected = { entryId ->
            when (entryId) {
                "monitor" -> onOpenDestination(AppDestination.BATTERY_CONFIGURATION)
                "smart_capsule" -> onOpenDestination(AppDestination.SMART_CAPSULE_APPS)
                "media_island" -> onOpenDestination(AppDestination.MEDIA)
                "capsule_appearance" -> onOpenDestination(AppDestination.CAPSULE_APPEARANCE)
            }
        },
        bottomInnerPadding = bottomInnerPadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaterialDirectoryPage(
    title: String,
    groups: List<DirectoryGroupUi>,
    onEntrySelected: (String) -> Unit,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
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
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            itemsIndexed(groups, key = { _, group -> group.title }) { _, group ->
                MaterialDirectoryGroup(group = group, onEntrySelected = onEntrySelected)
            }
        }
    }
}

@Composable
private fun MaterialDirectoryGroup(
    group: DirectoryGroupUi,
    onEntrySelected: (String) -> Unit,
) {
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
                                imageVector = entry.icon.materialIcon(),
                                contentDescription = entry.title,
                            )
                        },
                        trailingContent = {
                            entry.status?.let { status -> Text(status) }
                            if (entry.enabled && entry.showChevron) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        },
                    )
                }
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaterialSettingsPage(
    appearance: AppAppearanceSettings,
    onUiModeChange: (AppUiMode) -> Unit,
    onOpenTheme: () -> Unit,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("设置") },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState()),
        ) {
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                content =
                    listOf(
                        {
                            SegmentedDropdownItem(
                                icon = Icons.Filled.Dashboard,
                                title = "界面风格",
                                summary = "选择应用的界面风格",
                                items = AppUiMode.entries.map { mode -> mode.materialLabel() },
                                selectedIndex = AppUiMode.entries.indexOf(appearance.uiMode).coerceAtLeast(0),
                                onItemSelected = { index ->
                                    AppUiMode.entries.getOrNull(index)?.let(onUiModeChange)
                                },
                            )
                        },
                        {
                            SegmentedListItem(
                                onClick = onOpenTheme,
                                headlineContent = { Text("主题设置") },
                                supportingContent = { Text("颜色模式、动态配色与界面缩放") },
                                leadingContent = { Icon(Icons.Filled.Palette, "主题设置") },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                },
                            )
                        },
                    ),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Spacer(modifier = Modifier.size(bottomInnerPadding))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialAppearanceSettingsPage(
    onBack: () -> Unit,
) {
    val appearanceViewModel = LocalAppAppearanceSettingsViewModel.current
    val appearance by appearanceViewModel.appearance.collectAsStateWithLifecycle()
    val onAppearanceChange = appearanceViewModel::update
    val paletteStyle = runCatching { PaletteStyle.valueOf(appearance.paletteStyle) }.getOrDefault(PaletteStyle.TonalSpot)
    val colorSpec = runCatching { ColorSpec.SpecVersion.valueOf(appearance.colorSpec) }.getOrDefault(ColorSpec.SpecVersion.SPEC_2025)
    val mode = appearance.toKernelSuColorMode()
    KernelSuColorPaletteScreenMaterial(
        state = ColorPaletteUiState(
            uiState = SettingsUiState(
                themeMode = appearance.themeMode.value,
                miuixMonet = appearance.miuixMonet,
                keyColor = appearance.keyColor,
                colorStyle = appearance.paletteStyle,
                colorSpec = appearance.colorSpec,
                enableBlur = appearance.enableBlur,
                enableFloatingBottomBar = appearance.enableFloatingBottomBar,
                enableFloatingBottomBarBlur = appearance.enableFloatingBottomBarBlur,
                enableNavigationBadge = false,
                enablePredictiveBack = appearance.enablePredictiveBack,
                pageScale = appearance.pageScale,
            ),
            currentColorMode = mode,
            currentPaletteStyle = paletteStyle,
            currentColorSpec = colorSpec,
        ),
        actions = ColorPaletteScreenActions(
            onBack = onBack,
            onSetThemeMode = { selected -> onAppearanceChange { it.withBaseThemeMode(selected) } },
            onSetMiuixMonet = { enabled -> onAppearanceChange { it.withMiuixMonet(enabled) } },
            onSetKeyColor = { color -> onAppearanceChange { it.copy(keyColor = color) } },
            onSetColorMode = { selected -> onAppearanceChange { it.withThemeMode(AppThemeMode.fromValue(selected.value)) } },
            onSetColorStyle = { style -> onAppearanceChange { it.copy(paletteStyle = style) } },
            onSetColorSpec = { spec -> onAppearanceChange { it.copy(colorSpec = spec) } },
            onSetEnableBlur = { enabled -> onAppearanceChange { it.copy(enableBlur = enabled) } },
            onSetEnableFloatingBottomBar = { enabled -> onAppearanceChange { it.copy(enableFloatingBottomBar = enabled) } },
            onSetEnableFloatingBottomBarBlur = { enabled -> onAppearanceChange { it.copy(enableFloatingBottomBarBlur = enabled) } },
            onSetEnableNavigationBadge = {},
            onSetEnablePredictiveBack = { enabled -> onAppearanceChange { it.copy(enablePredictiveBack = enabled) } },
            onSetPageScale = { scale -> onAppearanceChange { it.copy(pageScale = scale) } },
        ),
    )
}

private fun AppPrimaryTab.materialIcon(selected: Boolean): ImageVector =
    when (this) {
        AppPrimaryTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        AppPrimaryTab.SUPER_ISLAND ->
            if (selected) Icons.Filled.Notifications else Icons.Outlined.Notifications
        AppPrimaryTab.EXTENSIONS -> if (selected) Icons.Filled.Extension else Icons.Outlined.Extension
        AppPrimaryTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
        AppPrimaryTab.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    }

private fun AppUiMode.materialLabel(): String =
    when (this) {
        AppUiMode.MIUIX -> "Miuix"
        AppUiMode.MATERIAL -> "Material"
    }

private fun DirectoryIcon.materialIcon(): ImageVector =
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
        DirectoryIcon.MEDIA -> Icons.Filled.Notifications
        DirectoryIcon.MONITOR,
        DirectoryIcon.DEVICE,
        -> Icons.Filled.Dashboard
        DirectoryIcon.APPEARANCE -> Icons.Filled.Palette
        DirectoryIcon.PRIVACY -> Icons.Filled.Security
        DirectoryIcon.INFO -> Icons.Filled.Info
    }
