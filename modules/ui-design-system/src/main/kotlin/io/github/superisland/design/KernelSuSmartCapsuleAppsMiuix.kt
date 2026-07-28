package io.github.superisland.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.ui.component.ListPopupDefaults
import me.weishu.kernelsu.ui.component.ScrollToTopOnChange
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.miuix.SearchBarFake
import me.weishu.kernelsu.ui.component.miuix.SearchBox
import me.weishu.kernelsu.ui.component.miuix.SearchPager
import me.weishu.kernelsu.ui.component.statustag.StatusTagMiuix
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Product adapter for KernelSU SuperUserMiuix.kt at b6e50f9a4f5fa7a14b68e7945d172ddbeae36415.
 * The page shell, search, menus, list geometry and row indication stay aligned with upstream;
 * only the navigation icon and Superuser-specific fields are mapped to Smart Capsule state.
 */
@Composable
fun KernelSuSmartCapsuleAppsMiuix(
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
    var searchStatus by remember { mutableStateOf(SearchStatus("搜索应用")) }
    val searchResults =
        rememberSmartCapsuleAppSearchResults(
            apps = apps,
            query = searchStatus.searchText,
        )
    LaunchedEffect(searchResults, searchStatus.searchText) {
        val resultStatus =
            when {
                searchStatus.searchText.isBlank() -> SearchStatus.ResultStatus.DEFAULT
                searchResults.isEmpty() -> SearchStatus.ResultStatus.EMPTY
                else -> SearchStatus.ResultStatus.SHOW
            }
        if (searchStatus.resultStatus != resultStatus) {
            searchStatus = searchStatus.copy(resultStatus = resultStatus)
        }
    }

    val enableBlur = LocalDesignEnableBlur.current
    val density = LocalDensity.current
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = "超级岛通知",
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
                            if (enabled) {
                                SortMenu(sortConfig, onSortConfigChange)
                                FilterMenu(
                                    showSystemApps = showSystemApps,
                                    onToggleSystemApps = onToggleSystemApps,
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
                            if (enabled) {
                                Box(
                                    modifier =
                                        Modifier
                                            .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                            .onGloballyPositioned { coordinates ->
                                                with(density) {
                                                    val offset = coordinates.positionInWindow().y.toDp()
                                                    if (searchStatus.offsetY != offset) {
                                                        searchStatus = searchStatus.copy(offsetY = offset)
                                                    }
                                                }
                                            }.then(
                                                if (searchStatus.isCollapsed()) {
                                                    Modifier.pointerInput(Unit) {
                                                        detectTapGestures {
                                                            searchStatus =
                                                                searchStatus.copy(
                                                                    current = SearchStatus.Status.EXPANDING,
                                                                )
                                                        }
                                                    }
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                ) {
                                    SearchBarFake(searchStatus.label, dynamicTopPadding)
                                }
                            }
                        },
                    )
                }
            }
        },
        popupHost = {
            if (enabled) {
                searchStatus.SearchPager(
                    onSearchStatusChange = { searchStatus = it },
                    defaultResult = {},
                    searchBarTopPadding = dynamicTopPadding,
                ) {
                    SmartCapsuleSearchResults(
                        apps = searchResults,
                        onSelectApp = onSelectApp,
                        appIcon = appIcon,
                    )
                }
            }
        },
        contentWindowInsets =
            WindowInsets.systemBars
                .add(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        searchStatus.SearchBox {
            val listState = rememberLazyListState()
            val refreshTick = remember { mutableIntStateOf(0) }
            val latestApps = rememberUpdatedState(apps)
            val latestRefreshing = rememberUpdatedState(isRefreshing)
            ScrollToTopOnChange(
                listState,
                sortConfig,
                showSystemApps,
                refreshTick.intValue,
                isBusy = { latestRefreshing.value },
            ) { latestApps.value }
            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefresh(
                isRefreshing = isRefreshing,
                pullToRefreshState = pullToRefreshState,
                onRefresh = {
                    if (enabled) {
                        onRefresh()
                        refreshTick.intValue++
                    }
                },
                refreshTexts = listOf("下拉刷新", "释放刷新", "正在刷新", "刷新完成"),
                contentPadding =
                    PaddingValues(
                        top = innerPadding.calculateTopPadding() + 6.dp,
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                    ),
            ) {
                Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .scrollEndHaptic()
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding =
                            PaddingValues(
                                top = innerPadding.calculateTopPadding() + 6.dp,
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                                bottom =
                                    with(density) {
                                        WindowInsets.navigationBars.getBottom(this).toDp()
                                    } + 12.dp,
                            ),
                        overscrollEffect = null,
                    ) {
                        item(key = "master-switch", contentType = "master-switch") {
                            Card(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                SwitchPreference(
                                    title = "启用超级岛通知",
                                    summary = if (enabled) "选择允许显示到超级岛的应用" else "应用通知保持系统原样",
                                    checked = enabled,
                                    onCheckedChange = onEnabledChange,
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Rounded.Notifications,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 6.dp),
                                            tint = colorScheme.onBackground,
                                        )
                                    },
                                )
                            }
                        }
                        if (enabled) {
                            items(
                                items = apps,
                                key = NotificationSourceOptionUi::id,
                                contentType = { "smart-capsule-app" },
                            ) { app ->
                                SmartCapsuleAppRow(app, onSelectApp, appIcon)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartCapsuleSearchResults(
    apps: List<NotificationSourceOptionUi>,
    onSelectApp: (String) -> Unit,
    appIcon: @Composable (NotificationSourceOptionUi) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().overScrollVertical(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
    ) {
        items(apps, key = NotificationSourceOptionUi::id) { app ->
            SmartCapsuleAppRow(app, onSelectApp, appIcon)
        }
    }
}

@Composable
private fun SmartCapsuleAppRow(
    app: NotificationSourceOptionUi,
    onSelectApp: (String) -> Unit,
    appIcon: @Composable (NotificationSourceOptionUi) -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
        onClick = { onSelectApp(app.id) },
        showIndication = true,
        insideMargin = PaddingValues(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.padding(end = 10.dp).size(48.dp)) {
                appIcon(app)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.title,
                    modifier = Modifier.basicMarquee(),
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = app.id,
                    modifier = Modifier.basicMarquee(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (app.selected) {
                StatusTagMiuix(
                    label = "已上岛",
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
        }
    }
}

@Composable
private fun SortMenu(
    config: SmartCapsuleAppSortConfig,
    onChange: (SmartCapsuleAppSortConfig) -> Unit,
) {
    Box {
        val show = remember { mutableStateOf(false) }
        OverlayListPopup(
            show = show.value,
            popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { show.value = false },
        ) {
            ListPopupColumn {
                val entries =
                    listOf(
                        SmartCapsuleAppSortType.NAME to "按名称排序",
                        SmartCapsuleAppSortType.PACKAGE_NAME to "按包名排序",
                        SmartCapsuleAppSortType.INSTALL_TIME to "按安装时间排序",
                        SmartCapsuleAppSortType.UPDATE_TIME to "按更新时间排序",
                    )
                entries.forEachIndexed { index, (type, title) ->
                    DropdownImpl(
                        text = title,
                        optionSize = entries.size + 1,
                        isSelected = config.type == type,
                        index = index,
                        onSelectedIndexChange = {
                            onChange(config.copy(type = type))
                            show.value = false
                        },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    thickness = 1.5.dp,
                )
                DropdownImpl(
                    text = "倒序",
                    optionSize = entries.size + 1,
                    isSelected = config.reversed,
                    index = entries.size,
                    onSelectedIndexChange = {
                        onChange(config.copy(reversed = !config.reversed))
                        show.value = false
                    },
                )
            }
        }
        IconButton(onClick = { show.value = true }, holdDownState = show.value) {
            Icon(MiuixIcons.Sort, contentDescription = "排序", tint = colorScheme.onSurface)
        }
    }
}

@Composable
private fun FilterMenu(
    showSystemApps: Boolean,
    onToggleSystemApps: () -> Unit,
) {
    Box {
        val show = remember { mutableStateOf(false) }
        OverlayListPopup(
            show = show.value,
            popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { show.value = false },
        ) {
            ListPopupColumn {
                DropdownImpl(
                    text = "显示系统 App",
                    isSelected = showSystemApps,
                    optionSize = 1,
                    index = 0,
                    onSelectedIndexChange = {
                        onToggleSystemApps()
                        show.value = false
                    },
                )
            }
        }
        IconButton(onClick = { show.value = true }, holdDownState = show.value) {
            Icon(MiuixIcons.MoreCircle, contentDescription = "更多", tint = colorScheme.onSurface)
        }
    }
}
