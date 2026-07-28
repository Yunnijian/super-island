package io.github.superisland.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.superisland.design.ResidentExpandedActionSlotEditorActions
import io.github.superisland.design.ResidentExpandedActionSlotEditorUiState
import io.github.superisland.design.ResidentExpandedContentEditorActions
import io.github.superisland.design.ResidentExpandedContentEditorUiState
import io.github.superisland.design.ResidentExpandedLaunchAppUi
import io.github.superisland.design.ResidentExpandedTargetPickerActions
import io.github.superisland.design.ResidentExpandedTargetPickerUiState
import io.github.superisland.design.ResidentExpandedShortcutUi
import io.github.superisland.design.rememberResidentExpandedAppSearchResults
import io.github.superisland.design.rememberResidentExpandedShortcutSearchResults
import me.weishu.kernelsu.ui.component.AppIconImage
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SearchAppBar
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.statustag.StatusTag

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MaterialResidentExpandedContentEditor(
    state: ResidentExpandedContentEditorUiState,
    actions: ResidentExpandedContentEditorActions,
) {
    val selectedOption = state.options.firstOrNull { it.id == state.selectedOptionId }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("展开内容") },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.back, contentDescription = "返回")
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(top = 13.dp)
                    .imePadding()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                content =
                    listOf(
                        {
                            SegmentedDropdownItem(
                                title = "展开内容",
                                summary = selectedOption?.summary ?: "设置常驻胶囊展开后显示的详细信息",
                                items = state.options.map { it.title },
                                selectedIndex =
                                    state.options.indexOfFirst { it.id == state.selectedOptionId }
                                        .coerceAtLeast(0),
                                enabled = state.options.isNotEmpty(),
                                onItemSelected = { index ->
                                    state.options.getOrNull(index)?.let { actions.selectOption(it.id) }
                                },
                            )
                        },
                    ),
            )

            if (state.showTemplateEditor) {
                OutlinedTextField(
                    value = state.editorValue,
                    onValueChange = actions.changeEditorValue,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义展开内容") },
                    supportingText = {
                        Column {
                            Text("${state.characterCount} / ${state.maxLength}")
                            state.validationMessage?.let { message ->
                                Text(message, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    isError = state.validationMessage != null,
                    singleLine = false,
                    minLines = 5,
                    maxLines = 10,
                )
                SegmentedColumn(
                    modifier = Modifier.fillMaxWidth(),
                    title = "占位符（点击插入光标位置）",
                    content =
                        listOf(
                            {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    state.tokenGroups.forEach { group ->
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(group.title, style = MaterialTheme.typography.titleSmall)
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                group.tokens.forEach { token ->
                                                    androidx.compose.material3.AssistChip(
                                                        onClick = { actions.insertToken(token.placeholder) },
                                                        label = { Text(token.title) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        ),
                )
            }

            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                title = "预览",
                content =
                    listOf(
                        {
                            SegmentedListItem(
                                headlineContent = {
                                    Text(
                                        text = state.preview.ifBlank { "--" },
                                        maxLines = state.previewMaxLines,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        },
                    ),
            )

            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                title = "展开卡片快捷按钮",
                content =
                    state.actionSlots.map { slot ->
                        {
                            SegmentedListItem(
                                onClick = { actions.openActionSlot(slot.id) },
                                headlineContent = { Text(slot.title) },
                                supportingContent = { Text(slot.summary) },
                            )
                        }
                    },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = actions.restoreDefault,
                ) { Text("恢复默认") }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = state.canSave,
                    onClick = actions.save,
                ) { Text("保存") }
            }
            state.saveStatus?.let { status ->
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(
                modifier =
                    Modifier.height(
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MaterialResidentExpandedActionSlotEditor(
    state: ResidentExpandedActionSlotEditorUiState,
    actions: ResidentExpandedActionSlotEditorActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(state.slotTitle) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.back, contentDescription = "返回")
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .imePadding()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                content =
                    listOf(
                        {
                            SegmentedListItem(
                                onClick = actions.openTargetPicker,
                                headlineContent = { Text("应用或快捷方式") },
                                supportingContent = { Text(state.targetSummary) },
                            )
                        },
                    ),
            )
            OutlinedTextField(
                value = state.label,
                onValueChange = actions.changeLabel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("按钮文字") },
                isError = state.labelValidationMessage != null,
                supportingText =
                    state.labelValidationMessage?.let { message ->
                        { Text(message, color = MaterialTheme.colorScheme.error) }
                    },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    enabled = state.canClear,
                    onClick = actions.clear,
                ) { Text("清除") }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = state.canSave,
                    onClick = actions.save,
                ) { Text("保存") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MaterialResidentExpandedTargetPicker(
    state: ResidentExpandedTargetPickerUiState,
    actions: ResidentExpandedTargetPickerActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
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
            query = state.query,
        )
    val searchShortcuts =
        rememberResidentExpandedShortcutSearchResults(
            shortcuts = state.shortcuts,
            query = state.query,
        )

    ExpressiveScaffold(
        topBar = {
            SearchAppBar(
                snackbarHostState = snackbarHostState,
                title = { Text(state.title) },
                searchText = state.query,
                onSearchTextChange = actions.changeQuery,
                onClearClick = { actions.changeQuery("") },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.back, contentDescription = "返回")
                },
                actions = {
                    if (state.selectedTabIndex == 0) {
                        MaterialResidentTargetFilterMenu(
                            showSystemApps = state.showSystemApps,
                            onToggleSystemApps = actions.toggleShowSystemApps,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                defaultContent = { _, _ -> },
                searchContent = { bottomPadding, closeSearch ->
                    LaunchedEffect(state.query, state.selectedTabIndex) {
                        searchListState.scrollToItem(0)
                    }
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
                        if (state.selectedTabIndex == 0) {
                            itemsIndexed(
                                items = searchApps,
                                key = { _, app -> app.packageName },
                            ) { index, app ->
                                SegmentedItem(index = index, count = searchApps.size) {
                                    ResidentTargetMaterialAppRow(app) {
                                        closeSearch()
                                        actions.selectApp(app.packageName)
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = searchShortcuts,
                                key = { _, shortcut -> shortcut.id },
                            ) { index, shortcut ->
                                SegmentedItem(index = index, count = searchShortcuts.size) {
                                    ResidentTargetMaterialShortcutRow(shortcut) {
                                        closeSearch()
                                        actions.selectShortcut(shortcut.id)
                                    }
                                }
                            }
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item(key = "target-tabs", contentType = "target-tabs") {
                ResidentTargetMaterialTabs(
                    tabs = state.tabs,
                    selectedTabIndex = state.selectedTabIndex,
                    onSelectTab = actions.selectTab,
                )
            }
            if (state.selectedTabIndex == 0) {
                when {
                    state.loading ->
                        item(key = "loading") {
                            ResidentTargetMaterialMessage("正在读取应用")
                        }
                    visibleApps.isEmpty() ->
                        item(key = "empty") {
                            ResidentTargetMaterialMessage(
                                state.emptyMessage ?: "没有可启动的应用",
                            )
                        }
                    else ->
                        itemsIndexed(
                            items = visibleApps,
                            key = { _, app -> app.packageName },
                        ) { index, app ->
                            SegmentedItem(index = index, count = visibleApps.size) {
                                ResidentTargetMaterialAppRow(
                                    app = app,
                                    onClick = { actions.selectApp(app.packageName) },
                                )
                            }
                        }
                }
            } else {
                if (visibleShortcuts.isEmpty()) {
                    item(key = "empty") {
                        ResidentTargetMaterialMessage(
                            state.emptyMessage ?: "没有可用的快捷方式",
                        )
                    }
                } else {
                    itemsIndexed(
                        items = visibleShortcuts,
                        key = { _, shortcut -> shortcut.id },
                    ) { index, shortcut ->
                        SegmentedItem(index = index, count = visibleShortcuts.size) {
                            ResidentTargetMaterialShortcutRow(shortcut) {
                                actions.selectShortcut(shortcut.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResidentTargetMaterialTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onSelectTab: (Int) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                selected = index == selectedTabIndex,
                onClick = { onSelectTab(index) },
                label = { Text(tab, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun ResidentTargetMaterialAppRow(
    app: ResidentExpandedLaunchAppUi,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(app.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                app.packageName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            app.packageInfo?.let { packageInfo ->
                AppIconImage(
                    modifier = Modifier.size(48.dp),
                    packageInfo = packageInfo,
                    label = app.title,
                )
            } ?: Icon(Icons.Filled.Android, contentDescription = null)
        },
        trailingContent = {
            if (app.selected) {
                StatusTag(
                    label = "已选择",
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    )
}

@Composable
private fun ResidentTargetMaterialShortcutRow(
    shortcut: ResidentExpandedShortcutUi,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = false,
        enabled = shortcut.available,
        onClick = onClick,
        headlineContent = {
            Text(shortcut.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                shortcut.summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            shortcut.packageInfo?.let { packageInfo ->
                AppIconImage(
                    modifier = Modifier.size(48.dp),
                    packageInfo = packageInfo,
                    label = shortcut.title,
                )
            } ?: Icon(Icons.Filled.Android, contentDescription = null)
        },
        trailingContent = {
            if (shortcut.selected) {
                StatusTag(
                    label = "已选择",
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    )
}

@Composable
private fun ResidentTargetMaterialMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(vertical = 16.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MaterialResidentTargetFilterMenu(
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
                        Icon(Icons.Filled.Check, contentDescription = null)
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
