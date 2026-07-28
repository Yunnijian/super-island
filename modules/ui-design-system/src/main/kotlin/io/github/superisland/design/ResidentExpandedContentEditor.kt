package io.github.superisland.design

import android.content.pm.PackageInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.ui.component.ListPopupDefaults
import me.weishu.kernelsu.ui.component.SearchStatus
import me.weishu.kernelsu.ui.component.miuix.SearchBarFake
import me.weishu.kernelsu.ui.component.miuix.SearchBox
import me.weishu.kernelsu.ui.component.miuix.SearchPager
import me.weishu.kernelsu.ui.component.statustag.StatusTagMiuix
import me.weishu.kernelsu.ui.util.BlurredBar
import me.weishu.kernelsu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

data class ResidentExpandedContentOptionUi(
    val id: String,
    val title: String,
    val summary: String,
)

data class ResidentExpandedTokenUi(
    val title: String,
    val placeholder: String,
)

data class ResidentExpandedTokenGroupUi(
    val title: String,
    val tokens: List<ResidentExpandedTokenUi>,
)

data class ResidentExpandedActionRowUi(
    val id: String,
    val title: String,
    val summary: String,
)

data class ResidentExpandedLaunchAppUi(
    val packageName: String,
    val title: String,
    val isSystem: Boolean = false,
    val packageInfo: PackageInfo? = null,
    val selected: Boolean = false,
)

data class ResidentExpandedContentEditorUiState(
    val options: List<ResidentExpandedContentOptionUi>,
    val selectedOptionId: String,
    val editorValue: TextFieldValue,
    val showTemplateEditor: Boolean,
    val tokenGroups: List<ResidentExpandedTokenGroupUi>,
    val preview: String,
    val previewMaxLines: Int,
    val validationMessage: String?,
    val characterCount: Int,
    val maxLength: Int,
    /** The three fixed action slots shown directly below expanded-content settings. */
    val actionSlots: List<ResidentExpandedActionRowUi>,
    val saveStatus: String?,
    val canSave: Boolean,
)

class ResidentExpandedContentEditorActions(
    val selectOption: (String) -> Unit,
    val changeEditorValue: (TextFieldValue) -> Unit,
    val insertToken: (String) -> Unit,
    val openActionSlot: (String) -> Unit,
    val restoreDefault: () -> Unit,
    val save: () -> Unit,
    val back: () -> Unit,
)

data class ResidentExpandedActionSlotEditorUiState(
    val slotTitle: String,
    val label: String,
    val labelValidationMessage: String?,
    val targetSummary: String,
    val canSave: Boolean,
    val canClear: Boolean,
)

class ResidentExpandedActionSlotEditorActions(
    val changeLabel: (String) -> Unit,
    val openTargetPicker: () -> Unit,
    val save: () -> Unit,
    val clear: () -> Unit,
    val back: () -> Unit,
)

data class ResidentExpandedTargetPickerUiState(
    val title: String,
    val tabs: List<String>,
    val selectedTabIndex: Int,
    val query: String,
    val shortcuts: List<ResidentExpandedShortcutUi>,
    val apps: List<ResidentExpandedLaunchAppUi>,
    val loading: Boolean,
    val emptyMessage: String?,
    val showSystemApps: Boolean,
)

data class ResidentExpandedShortcutUi(
    val id: String,
    val title: String,
    val summary: String,
    val available: Boolean,
    val selected: Boolean,
    val packageInfo: PackageInfo? = null,
)

class ResidentExpandedTargetPickerActions(
    val selectTab: (Int) -> Unit,
    val changeQuery: (String) -> Unit,
    val selectShortcut: (String) -> Unit,
    val selectApp: (String) -> Unit,
    val toggleShowSystemApps: () -> Unit,
    val back: () -> Unit,
)

/** Miuix renderer for the resident-island expanded-content template editor. */
@Composable
fun ResidentExpandedContentEditorMiuix(
    state: ResidentExpandedContentEditorUiState,
    actions: ResidentExpandedContentEditorActions,
) {
    val selectedOption = state.options.firstOrNull { it.id == state.selectedOptionId }
    val bottomInset =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
    AppScaffold(
        title = "展开内容",
        largeTitle = "展开内容",
        onBack = actions.back,
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .imePadding()
                    .padding(horizontal = 18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 12.dp,
                bottom = bottomInset + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            item(key = "expanded-mode-and-template") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        title = "展开内容",
                        summary = selectedOption?.summary ?: "选择展开胶囊显示的详细信息",
                        items = state.options.map(ResidentExpandedContentOptionUi::title),
                        selectedIndex = state.options.indexOfFirst { it.id == state.selectedOptionId }.coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            state.options.getOrNull(index)?.let { actions.selectOption(it.id) }
                        },
                    )
                    if (state.showTemplateEditor) {
                        TextField(
                            value = state.editorValue,
                            onValueChange = actions.changeEditorValue,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            label = "自定义展开内容",
                            useLabelAsPlaceholder = true,
                            singleLine = false,
                            minLines = 5,
                            maxLines = 10,
                        )
                    }
                }
            }

            if (state.showTemplateEditor) {
                item(key = "expanded-token-heading") {
                    SmallTitle(
                        text = "占位符（点击插入光标位置）",
                        insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
                    )
                }
                state.tokenGroups.forEach { group ->
                    item(key = "expanded-token-${group.title}") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(group.title)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    group.tokens.forEach { token ->
                                        Button(
                                            onClick = { actions.insertToken(token.placeholder) },
                                        ) {
                                            Text(token.title)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "expanded-preview-heading") {
                SmallTitle(
                    text = "预览",
                    insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
                )
            }
            item(key = "expanded-preview") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = state.preview,
                            maxLines = state.previewMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.showTemplateEditor) {
                            Text(
                                text = "${state.characterCount} / ${state.maxLength}",
                                color =
                                    if (state.validationMessage == null) {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    } else {
                                        MiuixTheme.colorScheme.error
                                    },
                            )
                            state.validationMessage?.let { message ->
                                Text(message, color = MiuixTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item(key = "expanded-action-heading") {
                SmallTitle(
                    text = "展开卡片快捷按钮",
                    insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp),
                )
            }
            item(key = "expanded-action-slots") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    state.actionSlots.forEachIndexed { index, slot ->
                        ArrowPreference(
                            title = slot.title,
                            summary = slot.summary,
                            onClick = { actions.openActionSlot(slot.id) },
                        )
                        if (index < state.actionSlots.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            item(key = "expanded-actions") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = actions.restoreDefault,
                    ) {
                        Text("恢复默认")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = state.canSave,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        onClick = actions.save,
                    ) {
                        Text("保存")
                    }
                }
            }
            state.saveStatus?.let { status ->
                item(key = "expanded-save-status") {
                    Text(
                        text = status,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/** 单槽位快捷按钮配置页。 */
@Composable
fun ResidentExpandedActionSlotEditorMiuix(
    state: ResidentExpandedActionSlotEditorUiState,
    actions: ResidentExpandedActionSlotEditorActions,
) {
    val bottomInset =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding()
    AppScaffold(
        title = state.slotTitle,
        largeTitle = state.slotTitle,
        onBack = actions.back,
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .imePadding()
                    .padding(horizontal = 18.dp),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    top = 12.dp,
                    bottom = bottomInset + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "应用或快捷方式",
                        summary = state.targetSummary,
                        onClick = actions.openTargetPicker,
                    )
                }
            }
            item {
                TextField(
                    value = state.label,
                    onValueChange = actions.changeLabel,
                    modifier = Modifier.fillMaxWidth(),
                    label = "按钮文字",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                state.labelValidationMessage?.let { message ->
                    Text(message, color = MiuixTheme.colorScheme.error)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = state.canClear,
                        onClick = actions.clear,
                    ) { Text("清除") }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = state.canSave,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        onClick = actions.save,
                    ) { Text("保存") }
                }
            }
        }
    }
}

/** 应用与快捷方式共用的安全目标选择页。 */
@Composable
fun ResidentExpandedTargetPickerMiuix(
    state: ResidentExpandedTargetPickerUiState,
    actions: ResidentExpandedTargetPickerActions,
    appIcon: @Composable (ResidentExpandedLaunchAppUi) -> Unit,
    shortcutIcon: @Composable (ResidentExpandedShortcutUi) -> Unit,
) {
    val initialSearchLabel = residentTargetSearchLabel(state.selectedTabIndex)
    var searchStatus by remember {
        mutableStateOf(SearchStatus(label = initialSearchLabel, searchText = state.query))
    }
    val visibleApps =
        rememberResidentExpandedAppSearchResults(
            apps = state.apps,
            showSystemApps = state.showSystemApps,
            query = "",
        )
    val visibleShortcuts =
        rememberResidentExpandedShortcutSearchResults(
            shortcuts = state.shortcuts,
            query = "",
        )
    val searchApps =
        rememberResidentExpandedAppSearchResults(
            apps = state.apps,
            showSystemApps = state.showSystemApps,
            query = searchStatus.searchText,
        )
    val searchShortcuts =
        rememberResidentExpandedShortcutSearchResults(
            shortcuts = state.shortcuts,
            query = searchStatus.searchText,
        )

    LaunchedEffect(state.selectedTabIndex) {
        val nextLabel = residentTargetSearchLabel(state.selectedTabIndex)
        if (searchStatus.label != nextLabel || searchStatus.searchText.isNotEmpty()) {
            searchStatus = searchStatus.copy(label = nextLabel, searchText = "")
            actions.changeQuery("")
        }
    }
    LaunchedEffect(
        searchApps,
        searchShortcuts,
        searchStatus.searchText,
        state.loading,
        state.selectedTabIndex,
    ) {
        val activeResultsEmpty =
            if (state.selectedTabIndex == 0) searchApps.isEmpty() else searchShortcuts.isEmpty()
        val resultStatus =
            when {
                searchStatus.searchText.isBlank() -> SearchStatus.ResultStatus.DEFAULT
                state.selectedTabIndex == 0 && state.loading -> SearchStatus.ResultStatus.LOAD
                activeResultsEmpty -> SearchStatus.ResultStatus.EMPTY
                else -> SearchStatus.ResultStatus.SHOW
            }
        if (searchStatus.resultStatus != resultStatus) {
            searchStatus = searchStatus.copy(resultStatus = resultStatus)
        }
    }

    val onSearchStatusChange: (SearchStatus) -> Unit = { nextStatus ->
        searchStatus = nextStatus
        if (state.query != nextStatus.searchText) actions.changeQuery(nextStatus.searchText)
    }
    val enableBlur = LocalDesignEnableBlur.current
    val density = LocalDensity.current
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicTopPadding by remember {
        derivedStateOf { 12.dp * (1f - scrollBehavior.state.collapsedFraction) }
    }
    val backdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = state.title,
                        navigationIcon = {
                            IconButton(onClick = actions.back) {
                                val layoutDirection = LocalLayoutDirection.current
                                Icon(
                                    modifier =
                                        Modifier.graphicsLayer {
                                            if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                        },
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "返回",
                                    tint = MiuixTheme.colorScheme.onBackground,
                                )
                            }
                        },
                        actions = {
                            if (state.selectedTabIndex == 0) {
                                ResidentTargetAppFilterMenu(
                                    showSystemApps = state.showSystemApps,
                                    onToggleSystemApps = actions.toggleShowSystemApps,
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        bottomContent = {
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
                        },
                    )
                }
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = onSearchStatusChange,
                defaultResult = {},
                searchBarTopPadding = dynamicTopPadding,
            ) {
                ResidentTargetMiuixSearchResults(
                    selectedTabIndex = state.selectedTabIndex,
                    apps = searchApps,
                    shortcuts = searchShortcuts,
                    actions = actions,
                    appIcon = appIcon,
                    shortcutIcon = shortcutIcon,
                )
            }
        },
        contentWindowInsets =
            WindowInsets.systemBars
                .add(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        searchStatus.SearchBox {
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
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
                    item(key = "target-tabs", contentType = "target-tabs") {
                        ResidentTargetMiuixTabs(
                            tabs = state.tabs,
                            selectedTabIndex = state.selectedTabIndex,
                            onSelectTab = actions.selectTab,
                        )
                    }
                    if (state.selectedTabIndex == 0) {
                        when {
                            state.loading ->
                                item(key = "loading") {
                                    ResidentTargetMiuixMessage("正在读取应用")
                                }
                            visibleApps.isEmpty() ->
                                item(key = "empty") {
                                    ResidentTargetMiuixMessage(
                                        state.emptyMessage ?: "没有可启动的应用",
                                    )
                                }
                            else ->
                                items(
                                    items = visibleApps,
                                    key = ResidentExpandedLaunchAppUi::packageName,
                                    contentType = { "resident-target-app" },
                                ) { app ->
                                    ResidentTargetMiuixAppRow(app, actions.selectApp, appIcon)
                                }
                        }
                    } else if (visibleShortcuts.isEmpty()) {
                        item(key = "empty") {
                            ResidentTargetMiuixMessage(
                                state.emptyMessage ?: "没有可用的快捷方式",
                            )
                        }
                    } else {
                        items(
                            items = visibleShortcuts,
                            key = ResidentExpandedShortcutUi::id,
                            contentType = { "resident-target-shortcut" },
                        ) { shortcut ->
                            ResidentTargetMiuixShortcutRow(
                                shortcut,
                                actions.selectShortcut,
                                shortcutIcon,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResidentTargetMiuixTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            Button(
                modifier = Modifier.weight(1f),
                colors =
                    if (index == selectedTabIndex) {
                        ButtonDefaults.buttonColorsPrimary()
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                onClick = { onSelectTab(index) },
            ) {
                Text(tab, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ResidentTargetMiuixSearchResults(
    selectedTabIndex: Int,
    apps: List<ResidentExpandedLaunchAppUi>,
    shortcuts: List<ResidentExpandedShortcutUi>,
    actions: ResidentExpandedTargetPickerActions,
    appIcon: @Composable (ResidentExpandedLaunchAppUi) -> Unit,
    shortcutIcon: @Composable (ResidentExpandedShortcutUi) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().overScrollVertical(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
    ) {
        if (selectedTabIndex == 0) {
            items(apps, key = ResidentExpandedLaunchAppUi::packageName) { app ->
                ResidentTargetMiuixAppRow(app, actions.selectApp, appIcon)
            }
        } else {
            items(shortcuts, key = ResidentExpandedShortcutUi::id) { shortcut ->
                ResidentTargetMiuixShortcutRow(shortcut, actions.selectShortcut, shortcutIcon)
            }
        }
    }
}

@Composable
private fun ResidentTargetMiuixAppRow(
    app: ResidentExpandedLaunchAppUi,
    onSelectApp: (String) -> Unit,
    appIcon: @Composable (ResidentExpandedLaunchAppUi) -> Unit,
) {
    ResidentTargetMiuixRow(
        title = app.title,
        summary = app.packageName,
        selected = app.selected,
        onClick = { onSelectApp(app.packageName) },
        icon = { appIcon(app) },
    )
}

@Composable
private fun ResidentTargetMiuixShortcutRow(
    shortcut: ResidentExpandedShortcutUi,
    onSelectShortcut: (String) -> Unit,
    shortcutIcon: @Composable (ResidentExpandedShortcutUi) -> Unit,
) {
    ResidentTargetMiuixRow(
        title = shortcut.title,
        summary = shortcut.summary,
        selected = shortcut.selected,
        onClick = { if (shortcut.available) onSelectShortcut(shortcut.id) },
        icon = { shortcutIcon(shortcut) },
    )
}

@Composable
private fun ResidentTargetMiuixRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
        onClick = onClick,
        showIndication = true,
        insideMargin = PaddingValues(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.padding(end = 10.dp).size(48.dp)) { icon() }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    modifier = Modifier.basicMarquee(),
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = summary,
                    modifier = Modifier.basicMarquee(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (selected) {
                StatusTagMiuix(
                    label = "已选择",
                    backgroundColor = MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    contentColor = MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
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
                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantActions),
            )
        }
    }
}

@Composable
private fun ResidentTargetMiuixMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

private fun residentTargetSearchLabel(selectedTabIndex: Int): String =
    if (selectedTabIndex == 0) "搜索应用" else "搜索快捷方式"

@Composable
private fun ResidentTargetAppFilterMenu(
    showSystemApps: Boolean,
    onToggleSystemApps: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    Box {
        OverlayListPopup(
            show = show,
            popupPositionProvider = ListPopupDefaults.MenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { show = false },
        ) {
            ListPopupColumn {
                DropdownImpl(
                    text = "显示系统 App",
                    isSelected = showSystemApps,
                    optionSize = 1,
                    index = 0,
                    onSelectedIndexChange = {
                        onToggleSystemApps()
                        show = false
                    },
                )
            }
        }
        IconButton(onClick = { show = true }, holdDownState = show) {
            Icon(MiuixIcons.MoreCircle, contentDescription = "更多")
        }
    }
}
