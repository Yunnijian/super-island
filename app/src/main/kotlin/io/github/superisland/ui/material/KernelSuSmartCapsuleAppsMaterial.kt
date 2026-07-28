package io.github.superisland.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.superisland.design.NotificationSourceOptionUi
import io.github.superisland.design.SmartCapsuleAppSortConfig
import io.github.superisland.design.SmartCapsuleAppSortType
import io.github.superisland.design.rememberSmartCapsuleAppSearchResults
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.statustag.StatusTag

/** Material adapter of KernelSU SuperUserMaterial.kt at the pinned manager commit. */
@Composable
fun KernelSuSmartCapsuleAppsMaterial(
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val refreshTick = remember { mutableIntStateOf(0) }
    val pullState = rememberPullToRefreshState()
    var searchText by remember { mutableStateOf("") }
    val searchResults = rememberSmartCapsuleAppSearchResults(apps = apps, query = searchText)
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    ExpressiveScaffold(
        topBar = {
            if (enabled) {
                SearchAppBar(
                    snackbarHostState = snackbarHostState,
                    title = { Text("超级岛通知") },
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    onClearClick = { searchText = "" },
                    navigationIcon = {
                        TopBarBackButton(onClick = onBack, contentDescription = "返回")
                    },
                    actions = {
                        MaterialSortMenu(sortConfig, onSortConfigChange)
                        MaterialFilterMenu(
                            showSystemApps = showSystemApps,
                            onToggleSystemApps = onToggleSystemApps,
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    defaultContent = { _, _ -> },
                    searchContent = { bottomPadding, closeSearch ->
                        LaunchedEffect(searchText) { searchListState.scrollToItem(0) }
                        LazyColumn(
                            state = searchListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    bottom = 16.dp + bottomPadding,
                                ),
                        ) {
                            itemsIndexed(searchResults, key = { _, app -> app.id }) { index, app ->
                                SegmentedItem(index = index, count = searchResults.size) {
                                    SmartCapsuleMaterialAppRow(app) {
                                        closeSearch()
                                        onSelectApp(app.id)
                                    }
                                }
                            }
                        }
                    },
                )
            } else {
                LargeFlexibleTopAppBar(
                    title = { Text("超级岛通知") },
                    navigationIcon = {
                        TopBarBackButton(onClick = onBack, contentDescription = "返回")
                    },
                    colors = expressiveTopAppBarColors(),
                    windowInsets =
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        contentWindowInsets =
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            isRefreshing = isRefreshing,
            onRefresh = {
                if (enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onRefresh()
                    refreshTick.intValue++
                }
            },
            state = pullState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullState,
                )
            },
        ) {
            val latestApps = rememberUpdatedState(apps)
            val latestRefreshing = rememberUpdatedState(isRefreshing)
            ScrollToTopOnChange(
                listState,
                sortConfig,
                showSystemApps,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestApps.value }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item(key = "master-switch", contentType = "master-switch") {
                    SegmentedColumn(
                        modifier = Modifier.padding(bottom = 12.dp),
                        content =
                            listOf<@Composable () -> Unit>(
                                {
                                    SegmentedSwitchItem(
                                        icon = Icons.Rounded.Notifications,
                                        title = "启用超级岛通知",
                                        summary =
                                            if (enabled) {
                                                "选择允许显示到超级岛的应用"
                                            } else {
                                                "应用通知保持系统原样"
                                            },
                                        checked = enabled,
                                        onCheckedChange = onEnabledChange,
                                    )
                                },
                            ),
                    )
                }
                if (enabled) {
                    itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                        SegmentedItem(index = index, count = apps.size) {
                            SmartCapsuleMaterialAppRow(app) { onSelectApp(app.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartCapsuleMaterialAppRow(
    app: NotificationSourceOptionUi,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = false,
        onClick = onClick,
        headlineContent = {
            Text(text = app.title, overflow = TextOverflow.Ellipsis, maxLines = 1)
        },
        supportingContent = {
            Text(
                text = app.id,
                color = colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        },
        leadingContent = {
            app.packageInfo?.let { packageInfo ->
                AppIconImage(
                    packageInfo = packageInfo,
                    label = app.title,
                    modifier = Modifier.size(48.dp),
                )
            }
        },
        trailingContent = {
            if (app.selected) {
                StatusTag(
                    label = "已上岛",
                    backgroundColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                )
            }
        },
    )
}

@Composable
private fun MaterialSortMenu(
    config: SmartCapsuleAppSortConfig,
    onChange: (SmartCapsuleAppSortConfig) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    IconButton(onClick = { show = true }) {
        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
        DropdownMenuPopup(expanded = show, onDismissRequest = { show = false }) {
            val entries =
                listOf(
                    SmartCapsuleAppSortType.NAME to "按名称排序",
                    SmartCapsuleAppSortType.PACKAGE_NAME to "按包名排序",
                    SmartCapsuleAppSortType.INSTALL_TIME to "按安装时间排序",
                    SmartCapsuleAppSortType.UPDATE_TIME to "按更新时间排序",
                )
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
                entries.forEachIndexed { index, (type, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        selected = config.type == type,
                        selectedLeadingIcon = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            )
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onChange(config.copy(type = type))
                            show = false
                        },
                        shapes = MenuDefaults.itemShape(index = index, count = entries.size),
                    )
                }
            }
            Spacer(Modifier.size(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
                DropdownMenuItem(
                    text = { Text("倒序") },
                    checked = config.reversed,
                    checkedLeadingIcon = {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        )
                    },
                    onCheckedChange = {
                        onChange(config.copy(reversed = !config.reversed))
                        show = false
                    },
                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                )
            }
        }
    }
}

@Composable
private fun MaterialFilterMenu(
    showSystemApps: Boolean,
    onToggleSystemApps: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    IconButton(onClick = { show = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
        DropdownMenuPopup(expanded = show, onDismissRequest = { show = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                DropdownMenuItem(
                    text = { Text("显示系统 App") },
                    checked = showSystemApps,
                    checkedLeadingIcon = {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        )
                    },
                    onCheckedChange = {
                        onToggleSystemApps()
                        show = false
                    },
                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                )
            }
        }
    }
}
