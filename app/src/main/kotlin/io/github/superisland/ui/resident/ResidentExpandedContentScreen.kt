package io.github.superisland.ui.resident

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.superisland.ResidentActionAppDirectory
import io.github.superisland.SmartCapsuleAppListPreferences
import io.github.superisland.design.ResidentExpandedActionRowUi
import io.github.superisland.design.ResidentExpandedActionSlotEditorActions
import io.github.superisland.design.ResidentExpandedActionSlotEditorMiuix
import io.github.superisland.design.ResidentExpandedActionSlotEditorUiState
import io.github.superisland.design.ResidentExpandedContentEditorActions
import io.github.superisland.design.ResidentExpandedContentEditorMiuix
import io.github.superisland.design.ResidentExpandedContentEditorUiState
import io.github.superisland.design.ResidentExpandedContentOptionUi
import io.github.superisland.design.ResidentExpandedLaunchAppUi
import io.github.superisland.design.ResidentExpandedShortcutUi
import io.github.superisland.design.ResidentExpandedTargetPickerActions
import io.github.superisland.design.ResidentExpandedTargetPickerMiuix
import io.github.superisland.design.ResidentExpandedTargetPickerUiState
import io.github.superisland.design.ResidentExpandedTokenGroupUi
import io.github.superisland.design.ResidentExpandedTokenUi
import io.github.superisland.model.ResidentExpandedAction
import io.github.superisland.model.ResidentExpandedActionType
import io.github.superisland.model.ResidentExpandedContentTemplate
import io.github.superisland.model.ResidentExpandedTokenCategory
import io.github.superisland.model.ResidentKnownShortcut
import io.github.superisland.model.ResidentMonitorConfig
import io.github.superisland.ui.material.MaterialResidentExpandedActionSlotEditor
import io.github.superisland.ui.material.MaterialResidentExpandedContentEditor
import io.github.superisland.ui.material.MaterialResidentExpandedTargetPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.AppIconImage

private sealed interface ResidentExpandedRoute : NavKey, java.io.Serializable {
    data object Template : ResidentExpandedRoute

    data class SlotEditor(
        val slotIndex: Int,
    ) : ResidentExpandedRoute

    data class TargetPicker(
        val slotIndex: Int,
    ) : ResidentExpandedRoute
}

/**
 * A small, typed Navigation3 stack scoped to the expanded-content flow.
 *
 * The parent route owns the draft above this stack, so NavDisplay can retain outgoing pages for
 * KernelSU's native forward/back transition without losing unsaved template or slot edits.
 */
private class ResidentExpandedNavigator {
    val backStack = mutableStateListOf<ResidentExpandedRoute>(ResidentExpandedRoute.Template)

    val current: ResidentExpandedRoute
        get() = backStack.last()

    val canPop: Boolean
        get() = backStack.size > 1

    fun navigate(destination: ResidentExpandedRoute) {
        if (destination != current) backStack += destination
    }

    fun pop() {
        if (canPop) backStack.removeAt(backStack.lastIndex)
    }

    fun popToTemplate() {
        while (canPop) pop()
    }

    companion object {
        val Saver: Saver<ResidentExpandedNavigator, Any> =
            listSaver(
                save = { navigator -> navigator.backStack.toList() },
                restore = { saved ->
                    ResidentExpandedNavigator().also { navigator ->
                        val routes = saved.filterIsInstance<ResidentExpandedRoute>()
                        if (routes.firstOrNull() == ResidentExpandedRoute.Template) {
                            navigator.backStack.clear()
                            navigator.backStack.addAll(routes)
                        }
                    }
                },
            )
    }
}

@Composable
private fun rememberResidentExpandedNavigator(): ResidentExpandedNavigator =
    rememberSaveable(saver = ResidentExpandedNavigator.Saver) { ResidentExpandedNavigator() }

private data class ResidentExpandedShortcutChoice(
    val id: String,
    val title: String,
    val summary: String,
    val type: ResidentExpandedActionType,
    val iconPackageName: String,
    val targetPackage: String? = null,
    val requiredPackage: String? = null,
) {
    fun isAvailable(installedPackages: Set<String>): Boolean =
        requiredPackage == null || requiredPackage in installedPackages

    fun matches(type: ResidentExpandedActionType?, targetPackage: String?): Boolean =
        this.type == type && this.targetPackage == targetPackage
}

private val residentExpandedShortcutChoices: List<ResidentExpandedShortcutChoice> =
    buildList {
        add(
            ResidentExpandedShortcutChoice(
                id = "refresh_now",
                title = "立即刷新",
                summary = "刷新常驻超级岛数据",
                type = ResidentExpandedActionType.REFRESH_NOW,
                iconPackageName = RESIDENT_MODULE_PACKAGE,
            ),
        )
        add(
            ResidentExpandedShortcutChoice(
                id = "battery_settings",
                title = "电池设置",
                summary = "打开系统电池设置",
                type = ResidentExpandedActionType.OPEN_BATTERY_SETTINGS,
                iconPackageName = ANDROID_SETTINGS_PACKAGE,
            ),
        )
        add(
            ResidentExpandedShortcutChoice(
                id = "notification_settings",
                title = "通知设置",
                summary = "打开系统通知设置",
                type = ResidentExpandedActionType.OPEN_NOTIFICATION_SETTINGS,
                iconPackageName = ANDROID_SETTINGS_PACKAGE,
            ),
        )
        ResidentKnownShortcut.entries.forEach { shortcut ->
            add(
                ResidentExpandedShortcutChoice(
                    id = shortcut.id,
                    title = shortcut.displayName,
                    summary = "打开 ${shortcut.displayName}",
                    type = ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT,
                    iconPackageName = shortcut.packageName,
                    targetPackage = shortcut.id,
                    requiredPackage = shortcut.packageName,
                ),
            )
        }
    }

private fun residentExpandedShortcutChoice(id: String): ResidentExpandedShortcutChoice? =
    residentExpandedShortcutChoices.firstOrNull { it.id == id }

/**
 * Single state owner shared by both visual skins.
 *
 * Template edits stay local until Save. Slot edits use fixed ids and save immediately after a
 * valid target is chosen, while the inner typed Navigation3 stack supplies native transitions.
 */
@Composable
internal fun ResidentExpandedContentScreen(
    initialConfig: ResidentMonitorConfig,
    configReady: Boolean = true,
    previewValues: Map<String, String?>,
    fanTokenAvailable: Boolean,
    onSave: (ResidentMonitorConfig) -> Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appDirectory =
        remember(context.applicationContext) {
            ResidentActionAppDirectory(context.applicationContext)
        }
    val appListPreferences =
        remember(context.applicationContext) {
            SmartCapsuleAppListPreferences(context.applicationContext)
        }
    val navigator = rememberResidentExpandedNavigator()
    var selectedOptionId by rememberSaveable {
        mutableStateOf(ResidentExpandedContentPresets.selectedId(initialConfig))
    }
    var editorValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = initialConfig.expandedContentTemplate,
                selection = TextRange(initialConfig.expandedContentTemplate.length),
            ),
        )
    }
    var draftSaved by rememberSaveable { mutableStateOf(false) }
    var draftDirty by rememberSaveable { mutableStateOf(false) }
    var draftSourceHash by rememberSaveable { mutableIntStateOf(initialConfig.hashCode()) }
    var baseConfig by remember { mutableStateOf(initialConfig) }
    var expandedActions by remember { mutableStateOf(initialConfig.expandedActions) }
    var slotTypeId by rememberSaveable { mutableStateOf<String?>(null) }
    var slotLabel by rememberSaveable { mutableStateOf("") }
    var slotTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var targetTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var targetQuery by rememberSaveable { mutableStateOf("") }
    var showSystemApps by rememberSaveable { mutableStateOf(appListPreferences.showSystemApps()) }
    var launcherAppsLoading by remember { mutableStateOf(false) }
    var launcherApps by remember { mutableStateOf(emptyList<ResidentExpandedLaunchAppUi>()) }
    var installedPackages by remember { mutableStateOf(emptySet<String>()) }
    var shortcutPackageInfoById by remember { mutableStateOf(emptyMap<String, PackageInfo>()) }
    var saveStatus by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(initialConfig, configReady) {
        baseConfig = initialConfig
        val incomingHash = initialConfig.hashCode()
        if (configReady && !draftDirty && incomingHash != draftSourceHash) {
            selectedOptionId = ResidentExpandedContentPresets.selectedId(initialConfig)
            editorValue =
                TextFieldValue(
                    text = initialConfig.expandedContentTemplate,
                    selection = TextRange(initialConfig.expandedContentTemplate.length),
                )
            expandedActions = initialConfig.expandedActions
            draftSaved = false
            saveStatus = null
            draftSourceHash = incomingHash
            navigator.popToTemplate()
        }
    }

    LaunchedEffect(navigator.current) {
        if (navigator.current !is ResidentExpandedRoute.TargetPicker) return@LaunchedEffect
        launcherAppsLoading = true
        val selectedAppPackage =
            slotTarget.takeIf { slotTypeId == ResidentExpandedActionType.LAUNCH_APP.name }
        val loaded =
            withContext(Dispatchers.IO) {
                val apps =
                    runCatching { appDirectory.load() }
                        .getOrDefault(emptyList())
                        .map { app ->
                            ResidentExpandedLaunchAppUi(
                                packageName = app.packageName,
                                title = app.label,
                                isSystem = app.isSystem,
                                packageInfo = app.packageInfo,
                                selected = app.packageName == selectedAppPackage,
                            )
                        }
                val pm = context.packageManager
                val shortcutPackageInfoByPackage =
                    residentExpandedShortcutChoices
                        .asSequence()
                        .map(ResidentExpandedShortcutChoice::iconPackageName)
                        .distinct()
                        .mapNotNull { packageName ->
                            runCatching {
                                pm.getPackageInfo(
                                    packageName,
                                    PackageManager.PackageInfoFlags.of(0L),
                                )
                            }.getOrNull()?.let { packageInfo -> packageName to packageInfo }
                        }.toMap()
                val installed =
                    buildSet {
                        addAll(apps.map(ResidentExpandedLaunchAppUi::packageName))
                        ResidentKnownShortcut.entries.forEach { shortcut ->
                            if (shortcutPackageInfoByPackage.containsKey(shortcut.packageName)) {
                                add(shortcut.packageName)
                            }
                        }
                    }
                val shortcutInfoById =
                    residentExpandedShortcutChoices.mapNotNull { choice ->
                        shortcutPackageInfoByPackage[choice.iconPackageName]
                            ?.let { packageInfo -> choice.id to packageInfo }
                    }.toMap()
                Triple(apps, installed, shortcutInfoById)
            }
        launcherApps = loaded.first
        installedPackages = loaded.second
        shortcutPackageInfoById = loaded.third
        launcherAppsLoading = false
    }

    fun markDraftChanged() {
        if (!configReady) return
        draftDirty = true
        draftSaved = false
        saveStatus = null
    }

    fun persistActions(next: List<ResidentExpandedAction>): Boolean {
        if (!configReady) return false
        val normalized = ResidentExpandedAction.normalize(next)
        expandedActions = normalized
        val nextConfig = baseConfig.copy(expandedActions = normalized).normalized()
        val saved = onSave(nextConfig)
        if (saved) {
            baseConfig = nextConfig
            draftSourceHash = nextConfig.hashCode()
            markDraftChanged()
            draftSaved = false
            saveStatus = "快捷按钮已保存"
        } else {
            saveStatus = "快捷按钮保存失败"
        }
        return saved
    }

    fun openSlot(slotIndex: Int) {
        val index = slotIndex.coerceIn(0, ResidentExpandedAction.MAX_ACTIONS - 1)
        val existing = ResidentExpandedAction.toSlots(expandedActions).getOrNull(index)
        slotTypeId = existing?.type?.name
        slotLabel = existing?.label.orEmpty()
        slotTarget = existing?.targetPackage
        navigator.navigate(ResidentExpandedRoute.SlotEditor(index))
    }

    fun navigateBack() {
        if (navigator.canPop) navigator.pop() else onBack()
    }

    BackHandler(enabled = navigator.canPop, onBack = navigator::pop)
    NavDisplay(
        backStack = navigator.backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        onBack = ::navigateBack,
        entryProvider = { route ->
            NavEntry(
                key = route,
                contentKey = route,
            ) {
                when (route) {
                    ResidentExpandedRoute.Template -> {
                        val options =
                            ResidentExpandedContentPresets.options(initialConfig).map { preset ->
                                ResidentExpandedContentOptionUi(
                                    id = preset.id,
                                    title = preset.title,
                                    summary = preset.summary,
                                )
                            } +
                                ResidentExpandedContentOptionUi(
                                    id = ResidentExpandedContentPresets.CUSTOM,
                                    title = "自定义",
                                    summary = "使用安全占位符自由组合多行内容",
                                )
                        val tokenGroups =
                            ResidentExpandedTokenCategory.entries.mapNotNull { category ->
                                val tokens =
                                    ResidentExpandedContentTemplate.tokens()
                                        .filter { token ->
                                            token.category == category &&
                                                (category != ResidentExpandedTokenCategory.FAN || fanTokenAvailable)
                                        }.map { token ->
                                            ResidentExpandedTokenUi(
                                                title = token.displayName,
                                                placeholder = token.placeholder,
                                            )
                                        }
                                tokens.takeIf(List<*>::isNotEmpty)?.let {
                                    ResidentExpandedTokenGroupUi(title = category.displayName, tokens = tokens)
                                }
                            }
                        val preview =
                            residentExpandedPreview(
                                selectedId = selectedOptionId,
                                template = editorValue.text,
                                values = previewValues,
                                currentPresetMetrics = initialConfig.expandedMetrics,
                            )
                        val showTemplateEditor = selectedOptionId == ResidentExpandedContentPresets.CUSTOM
                        val canSave = configReady && !draftSaved && (!showTemplateEditor || preview.isValid)
                        val slots =
                            ResidentExpandedAction.toSlots(expandedActions).mapIndexed { index, action ->
                                ResidentExpandedActionRowUi(
                                    id = ResidentExpandedAction.slotId(index),
                                    title = "按钮 ${index + 1}",
                                    summary = action?.listSummary() ?: "未设置",
                                )
                            }
                        val state =
                            ResidentExpandedContentEditorUiState(
                                options = options,
                                selectedOptionId = selectedOptionId,
                                editorValue = editorValue,
                                showTemplateEditor = showTemplateEditor,
                                tokenGroups = tokenGroups,
                                preview = preview.text,
                                previewMaxLines = if (expandedActions.isEmpty()) 6 else 4,
                                validationMessage = preview.validationMessage,
                                characterCount = editorValue.text.length,
                                maxLength = ResidentExpandedContentTemplate.MAX_LENGTH,
                                actionSlots = slots,
                                saveStatus = saveStatus,
                                canSave = canSave,
                            )
                        val actions =
                            ResidentExpandedContentEditorActions(
                                selectOption = { id ->
                                    if (configReady && options.any { it.id == id } && selectedOptionId != id) {
                                        selectedOptionId = id
                                        markDraftChanged()
                                    }
                                },
                                changeEditorValue = { value ->
                                    if (configReady && editorValue != value) {
                                        editorValue = value
                                        markDraftChanged()
                                    }
                                },
                                insertToken = { token ->
                                    if (configReady) {
                                        editorValue = insertResidentExpandedToken(editorValue, token)
                                        markDraftChanged()
                                    }
                                },
                                openActionSlot = { slotId ->
                                    if (configReady) {
                                        val index = ResidentExpandedAction.SLOT_IDS.indexOf(slotId)
                                        if (index >= 0) openSlot(index)
                                    }
                                },
                                restoreDefault = {
                                    if (configReady) {
                                        selectedOptionId = ResidentExpandedContentPresets.COMPLETE_STATUS
                                        editorValue =
                                            TextFieldValue(
                                                text = ResidentExpandedContentTemplate.DEFAULT_TEMPLATE,
                                                selection =
                                                    TextRange(
                                                        ResidentExpandedContentTemplate.DEFAULT_TEMPLATE.length,
                                                    ),
                                            )
                                        expandedActions = emptyList()
                                        markDraftChanged()
                                    }
                                },
                                save = {
                                    if (configReady && canSave && !draftSaved) {
                                        draftSaved = true
                                        val next =
                                            ResidentExpandedContentPresets.saveDraft(
                                                original = baseConfig.copy(expandedActions = expandedActions),
                                                selectedId = selectedOptionId,
                                                customTemplate = editorValue.text,
                                            ).copy(expandedActions = expandedActions).normalized()
                                        val ok = onSave(next)
                                        if (ok) {
                                            baseConfig = next
                                            draftDirty = false
                                            draftSourceHash = next.hashCode()
                                            saveStatus = "已保存"
                                        } else {
                                            draftSaved = false
                                            saveStatus = "保存失败"
                                        }
                                    }
                                },
                                back = ::navigateBack,
                            )
                        when (LocalUiMode.current) {
                            UiMode.Miuix -> ResidentExpandedContentEditorMiuix(state, actions)
                            UiMode.Material -> MaterialResidentExpandedContentEditor(state, actions)
                        }
                    }

                    is ResidentExpandedRoute.SlotEditor -> {
                        val slotIndex = route.slotIndex.coerceIn(0, ResidentExpandedAction.MAX_ACTIONS - 1)
                        val selectedType =
                            slotTypeId
                                ?.let { typeId ->
                                    ResidentExpandedActionType.entries.firstOrNull { it.name == typeId }
                                }
                        val labelError = residentExpandedActionLabelError(slotLabel)
                        val selectedChoice =
                            residentExpandedShortcutChoices.firstOrNull { choice ->
                                choice.matches(selectedType, slotTarget)
                            }
                        val targetSummary =
                            when (selectedType) {
                                null -> "请选择应用或快捷方式"
                                ResidentExpandedActionType.LAUNCH_APP -> {
                                    val title =
                                        launcherApps.firstOrNull { it.packageName == slotTarget }?.title
                                    when {
                                        slotTarget == null -> "请选择应用"
                                        title != null -> title
                                        else -> slotTarget.orEmpty()
                                    }
                                }

                                else -> selectedChoice?.title ?: "请选择应用或快捷方式"
                            }
                        val candidate =
                            selectedType?.let { type ->
                                ResidentExpandedAction(
                                    id = ResidentExpandedAction.slotId(slotIndex),
                                    label = slotLabel.trim(),
                                    type = type,
                                    targetPackage =
                                        slotTarget.takeIf {
                                            type == ResidentExpandedActionType.LAUNCH_APP ||
                                                type == ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT
                                        },
                                )
                            }
                        val editorState =
                            ResidentExpandedActionSlotEditorUiState(
                                slotTitle = "按钮 ${slotIndex + 1}",
                                label = slotLabel,
                                labelValidationMessage = labelError,
                                targetSummary = targetSummary,
                                canSave = candidate?.isValid() == true,
                                canClear =
                                    ResidentExpandedAction.toSlots(expandedActions)
                                        .getOrNull(slotIndex) != null,
                            )
                        val editorActions =
                            ResidentExpandedActionSlotEditorActions(
                                changeLabel = { slotLabel = it },
                                openTargetPicker = {
                                    targetTabIndex =
                                        if (selectedType == ResidentExpandedActionType.LAUNCH_APP ||
                                            selectedType == null
                                        ) {
                                            0
                                        } else {
                                            1
                                        }
                                    targetQuery = ""
                                    navigator.navigate(ResidentExpandedRoute.TargetPicker(slotIndex))
                                },
                                save = {
                                    val validCandidate = candidate ?: return@ResidentExpandedActionSlotEditorActions
                                    if (!validCandidate.isValid()) return@ResidentExpandedActionSlotEditorActions
                                    val slots =
                                        ResidentExpandedAction.toSlots(expandedActions).toMutableList()
                                    slots[slotIndex] = validCandidate
                                    if (persistActions(ResidentExpandedAction.fromSlots(slots))) {
                                        navigator.popToTemplate()
                                    }
                                },
                                clear = {
                                    val slots =
                                        ResidentExpandedAction.toSlots(expandedActions).toMutableList()
                                    slots[slotIndex] = null
                                    if (persistActions(ResidentExpandedAction.fromSlots(slots))) {
                                        navigator.popToTemplate()
                                    }
                                },
                                back = ::navigateBack,
                            )
                        when (LocalUiMode.current) {
                            UiMode.Miuix ->
                                ResidentExpandedActionSlotEditorMiuix(editorState, editorActions)
                            UiMode.Material ->
                                MaterialResidentExpandedActionSlotEditor(editorState, editorActions)
                        }
                    }

                    is ResidentExpandedRoute.TargetPicker -> {
                        val slotIndex = route.slotIndex.coerceIn(0, ResidentExpandedAction.MAX_ACTIONS - 1)
                        val selectedType =
                            slotTypeId
                                ?.let { typeId ->
                                    ResidentExpandedActionType.entries.firstOrNull { it.name == typeId }
                                }
                        val shortcuts =
                            residentExpandedShortcutChoices
                                .asSequence()
                                .filter { choice -> choice.isAvailable(installedPackages) }
                                .map { choice ->
                                    ResidentExpandedShortcutUi(
                                        id = choice.id,
                                        title = choice.title,
                                        summary = choice.summary,
                                        available = true,
                                        selected = choice.matches(selectedType, slotTarget),
                                        packageInfo = shortcutPackageInfoById[choice.id],
                                    )
                                }.toList()
                        val pickerState =
                            ResidentExpandedTargetPickerUiState(
                                title = "选择目标",
                                tabs = listOf("应用", "快捷方式"),
                                selectedTabIndex = targetTabIndex,
                                query = targetQuery,
                                shortcuts = shortcuts,
                                apps = launcherApps,
                                loading = targetTabIndex == 0 && launcherAppsLoading,
                                emptyMessage =
                                    if (targetTabIndex == 0) {
                                        if (showSystemApps) "没有可启动的应用" else "没有可启动的第三方应用"
                                    } else {
                                        "没有可用的快捷方式"
                                    },
                                showSystemApps = showSystemApps,
                            )
                        val pickerActions =
                            ResidentExpandedTargetPickerActions(
                                selectTab = { index ->
                                    targetTabIndex = index.coerceIn(0, 1)
                                    targetQuery = ""
                                },
                                changeQuery = { targetQuery = it },
                                selectShortcut = { id ->
                                    val choice = residentExpandedShortcutChoice(id)
                                        ?: return@ResidentExpandedTargetPickerActions
                                    if (!choice.isAvailable(installedPackages)) {
                                        return@ResidentExpandedTargetPickerActions
                                    }
                                    slotTypeId = choice.type.name
                                    slotTarget = choice.targetPackage
                                    if (slotLabel.isBlank() || slotLabel in residentExpandedDefaultSlotLabels) {
                                        slotLabel =
                                            choice.title.takeCodePoints(
                                                ResidentExpandedAction.MAX_LABEL_CODE_POINTS,
                                            )
                                    }
                                    navigator.pop()
                                },
                                selectApp = { packageName ->
                                    val app =
                                        launcherApps.firstOrNull { it.packageName == packageName }
                                            ?: return@ResidentExpandedTargetPickerActions
                                    slotTypeId = ResidentExpandedActionType.LAUNCH_APP.name
                                    slotTarget = app.packageName
                                    if (slotLabel.isBlank() || slotLabel in residentExpandedDefaultSlotLabels) {
                                        slotLabel =
                                            app.title.takeCodePoints(
                                                ResidentExpandedAction.MAX_LABEL_CODE_POINTS,
                                            )
                                    }
                                    navigator.pop()
                                },
                                toggleShowSystemApps = {
                                    showSystemApps = !showSystemApps
                                    appListPreferences.setShowSystemApps(showSystemApps)
                                },
                                back = ::navigateBack,
                            )
                        when (LocalUiMode.current) {
                            UiMode.Miuix ->
                                ResidentExpandedTargetPickerMiuix(
                                    state = pickerState,
                                    actions = pickerActions,
                                    appIcon = { app ->
                                        app.packageInfo?.let { packageInfo ->
                                            AppIconImage(
                                                modifier = Modifier.fillMaxSize(),
                                                packageInfo = packageInfo,
                                                label = app.title,
                                            )
                                        }
                                    },
                                    shortcutIcon = { shortcut ->
                                        shortcut.packageInfo?.let { packageInfo ->
                                            AppIconImage(
                                                modifier = Modifier.fillMaxSize(),
                                                packageInfo = packageInfo,
                                                label = shortcut.title,
                                            )
                                        }
                                    },
                                )
                            UiMode.Material ->
                                MaterialResidentExpandedTargetPicker(pickerState, pickerActions)
                        }
                    }
                }
            }
        },
    )
}

private const val RESIDENT_MODULE_PACKAGE = "io.github.superisland"
private const val ANDROID_SETTINGS_PACKAGE = "com.android.settings"

private val ResidentExpandedActionType.displayName: String
    get() =
        when (this) {
            ResidentExpandedActionType.REFRESH_NOW -> "立即刷新"
            ResidentExpandedActionType.LAUNCH_APP -> "启动应用"
            ResidentExpandedActionType.OPEN_BATTERY_SETTINGS -> "电池设置"
            ResidentExpandedActionType.OPEN_NOTIFICATION_SETTINGS -> "通知设置"
            ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT -> "系统快捷"
        }

private val residentExpandedDefaultSlotLabels: Set<String> by lazy {
    buildSet {
        addAll(ResidentExpandedActionType.entries.map(ResidentExpandedActionType::displayName))
        addAll(residentExpandedShortcutChoices.map(ResidentExpandedShortcutChoice::title))
    }
}

private fun ResidentExpandedAction.listSummary(): String =
    when (type) {
        ResidentExpandedActionType.REFRESH_NOW -> "$label · 立即刷新"
        ResidentExpandedActionType.LAUNCH_APP -> "$label · 启动应用"
        ResidentExpandedActionType.OPEN_BATTERY_SETTINGS -> "$label · 电池设置"
        ResidentExpandedActionType.OPEN_NOTIFICATION_SETTINGS -> "$label · 通知设置"
        ResidentExpandedActionType.OPEN_KNOWN_SHORTCUT -> {
            val shortcut = ResidentKnownShortcut.fromId(targetPackage)
            "$label · ${shortcut?.displayName ?: "系统快捷"}"
        }
    }

private fun residentExpandedActionLabelError(label: String): String? =
    when {
        label.isBlank() -> "按钮文字不能为空"
        label.codePointCount(0, label.length) > ResidentExpandedAction.MAX_LABEL_CODE_POINTS ->
            "按钮文字最多 ${ResidentExpandedAction.MAX_LABEL_CODE_POINTS} 个字符"
        label.codePoints().anyMatch(Character::isISOControl) -> "按钮文字包含不可用字符"
        else -> null
    }

private fun String.takeCodePoints(maxCodePoints: Int): String {
    if (codePointCount(0, length) <= maxCodePoints) return this
    val end = offsetByCodePoints(0, maxCodePoints)
    return substring(0, end)
}
