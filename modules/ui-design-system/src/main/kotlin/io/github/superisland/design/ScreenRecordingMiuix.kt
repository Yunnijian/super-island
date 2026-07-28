package io.github.superisland.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** Skin-neutral recording controls rendered with the pinned KernelSU/Miuix component set. */
data class ScreenRecordingDetailUi(
    val statusTitle: String,
    val statusSummary: String,
    val active: Boolean,
    val canStart: Boolean,
    /** When true, primary control is a red stop button rather than start. */
    val showStopAction: Boolean,
    val showPauseAction: Boolean = false,
    val isPaused: Boolean = false,
    val pauseActionLabel: String = "暂停录制",
    /** When true, primary control is a disabled completing label (stop already accepted). */
    val showFinalizingAction: Boolean = false,
    val videoPreferences: List<ScreenRecordingDropdownUi>,
    val audioPreferences: List<ScreenRecordingDropdownUi>,
    val behaviorPreferences: List<ScreenRecordingSwitchUi>,
    val tilePreferences: List<ScreenRecordingDropdownUi>,
    val storageSummary: String,
    val storageEnabled: Boolean,
    val showDefaultStorageAction: Boolean,
    val onStart: () -> Unit,
    val onPauseResume: () -> Unit = {},
    val onStop: () -> Unit,
    val onOpenStorage: () -> Unit,
    val onUseDefaultStorage: () -> Unit,
)

data class ScreenRecordingDropdownUi(
    val title: String,
    val summary: String,
    val items: List<String>,
    val selectedIndex: Int,
    val enabled: Boolean,
    val onItemSelected: (Int) -> Unit,
)

data class ScreenRecordingSwitchUi(
    val title: String,
    val summary: String,
    val checked: Boolean,
    val enabled: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

/** Shared confirm-dialog state for both skins (IslandRecorder 图一 style). */
data class ScreenRecordingConfirmUi(
    val audioLabels: List<String>,
    val selectedAudioIndex: Int,
    val showTouches: Boolean,
    val showTouchesEnabled: Boolean,
    val showTouchesSummary: String?,
    val onAudioSelected: (Int) -> Unit,
    val onShowTouchesChange: (Boolean) -> Unit,
    val onCancel: () -> Unit,
    val onStart: () -> Unit,
)

@Composable
fun ScreenRecordingMiuixScreen(
    state: ScreenRecordingDetailUi,
    onBack: () -> Unit,
) {
    AppScaffold(
        title = "超级岛录屏",
        largeTitle = "超级岛录屏",
        subtitle = "",
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
            SmallTitle(text = "录制")
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(title = state.statusTitle, summary = state.statusSummary)
            }
            when {
                state.showFinalizingAction ->
                    AppSecondaryButton(text = "正在完成…", enabled = false, onClick = {})
                state.showStopAction ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.showPauseAction) {
                            AppSecondaryButton(
                                text = state.pauseActionLabel,
                                onClick = state.onPauseResume,
                            )
                        }
                        AppDangerButton(text = "停止录制", onClick = state.onStop)
                    }
                else ->
                    AppPrimaryButton(
                        text = "开始录制",
                        enabled = state.canStart,
                        onClick = state.onStart,
                    )
            }
            PreferenceSection("视频", state.videoPreferences)
            PreferenceSection("声音", state.audioPreferences)
            SmallTitle(text = "行为")
            Card(modifier = Modifier.fillMaxWidth()) {
                state.behaviorPreferences.forEach { preference ->
                    SwitchPreference(
                        title = preference.title,
                        summary = preference.summary,
                        checked = preference.checked,
                        enabled = preference.enabled,
                        onCheckedChange = preference.onCheckedChange,
                    )
                }
            }
            PreferenceSection("快捷磁贴", state.tilePreferences)
            SmallTitle(text = "存储")
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "保存位置",
                    summary = state.storageSummary,
                    enabled = state.storageEnabled,
                    onClick = state.onOpenStorage,
                )
                if (state.showDefaultStorageAction) {
                    ArrowPreference(
                        title = "使用默认目录",
                        summary = "恢复保存到 /storage/emulated/0/DCIM/screenrecorder",
                        enabled = state.storageEnabled,
                        onClick = state.onUseDefaultStorage,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceSection(
    title: String,
    preferences: List<ScreenRecordingDropdownUi>,
) {
    SmallTitle(text = title)
    Card(modifier = Modifier.fillMaxWidth()) {
        preferences.forEach { preference ->
            OverlayDropdownPreference(
                title = preference.title,
                summary = preference.summary,
                items = preference.items,
                selectedIndex = preference.selectedIndex.coerceAtLeast(0),
                enabled = preference.enabled && preference.items.isNotEmpty(),
                onSelectedIndexChange = preference.onItemSelected,
            )
        }
    }
}

/**
 * IslandRecorder [RecordingShortcutActivity] portrait/landscape layout, adapted for Super Island.
 * Uses WindowSpinnerPreference so the audio menu can host above the dialog (OverlayDropdown cannot).
 */
@Composable
fun ScreenRecordingMiuixConfirmDialog(state: ScreenRecordingConfirmUi) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val containerSize = windowInfo.containerSize
    val isLandscape = containerSize.width > containerSize.height
    val windowWidth = with(density) { containerSize.width.toDp() }
    // Match IslandRecorder landscape max width when the host API lacks WindowDialog.maxWidth.
    val contentMaxWidth =
        if (isLandscape) {
            (windowWidth - 48.dp).coerceAtLeast(520.dp).coerceAtMost(640.dp)
        } else {
            windowWidth
        }
    val audioItems = state.audioLabels.map { DropdownItem(text = it) }
    val selectedIndex = state.selectedAudioIndex.coerceIn(0, (audioItems.size - 1).coerceAtLeast(0))

    WindowDialog(
        show = true,
        onDismissRequest = state.onCancel,
        title = null,
    ) {
        if (isLandscape) {
            Column(
                modifier =
                    Modifier
                        .width(contentMaxWidth)
                        .padding(vertical = DialogContentVerticalPadding),
            ) {
                ConfirmTitleBlock()
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        WindowSpinnerPreference(
                            title = "声音来源",
                            summary = null,
                            items = audioItems,
                            selectedIndex = selectedIndex,
                            insideMargin = ConfirmPreferenceInsideMargin,
                            enabled = audioItems.isNotEmpty(),
                            onSelectedIndexChange = state.onAudioSelected,
                        )
                        SwitchPreference(
                            title = "显示点按操作反馈",
                            summary = state.showTouchesSummary,
                            checked = state.showTouches,
                            insideMargin = ConfirmPreferenceInsideMargin,
                            enabled = state.showTouchesEnabled,
                            onCheckedChange = state.onShowTouchesChange,
                        )
                    }
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = DialogContentHorizontalPadding),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = state.onCancel,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = "开始录制",
                            onClick = state.onStart,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        } else {
            // Portrait: IslandRecorder original column layout
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = DialogContentVerticalPadding),
            ) {
                ConfirmTitleBlock()
                Spacer(modifier = Modifier.height(12.dp))
                WindowSpinnerPreference(
                    title = "声音来源",
                    summary = null,
                    items = audioItems,
                    selectedIndex = selectedIndex,
                    insideMargin = ConfirmPreferenceInsideMargin,
                    enabled = audioItems.isNotEmpty(),
                    onSelectedIndexChange = state.onAudioSelected,
                )
                SwitchPreference(
                    title = "显示点按操作反馈",
                    summary = state.showTouchesSummary,
                    checked = state.showTouches,
                    insideMargin = ConfirmPreferenceInsideMargin,
                    enabled = state.showTouchesEnabled,
                    onCheckedChange = state.onShowTouchesChange,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DialogContentHorizontalPadding),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = state.onCancel,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    TextButton(
                        text = "开始录制",
                        onClick = state.onStart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmTitleBlock() {
    Text(
        text = "要开始屏幕录制吗？",
        style = MiuixTheme.textStyles.title4,
        color = MiuixTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DialogContentHorizontalPadding),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "屏幕录制会访问应用内容，请录制应用页面时注意保护隐私信息。",
        style = MiuixTheme.textStyles.body1,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DialogContentHorizontalPadding),
    )
}

private val DialogContentHorizontalPadding = 24.dp
private val DialogContentVerticalPadding = 24.dp
private val ConfirmPreferenceInsideMargin = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
