package io.github.superisland.ui.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.superisland.design.ScreenRecordingConfirmUi
import io.github.superisland.design.ScreenRecordingDetailUi
import io.github.superisland.design.ScreenRecordingDropdownUi
import io.github.superisland.design.ScreenRecordingSwitchUi
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScreenRecordingMaterialScreen(
    state: ScreenRecordingDetailUi,
    onBack: () -> Unit,
) {
    val scrollBehavior =
        androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            androidx.compose.material3.rememberTopAppBarState(),
        )
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("超级岛录屏") },
                navigationIcon = {
                    TopBarBackButton(onClick = onBack, contentDescription = "返回")
                },
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
                    .padding(paddingValues)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                title = "录制",
                content =
                    listOf(
                        {
                            SegmentedListItem(
                                headlineContent = { Text(state.statusTitle) },
                                supportingContent = { Text(state.statusSummary) },
                            )
                        },
                    ),
            )
            when {
                state.showFinalizingAction ->
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        onClick = {},
                    ) {
                        Text("正在完成…")
                    }
                state.showStopAction ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.showPauseAction) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = state.onPauseResume,
                            ) {
                                Icon(
                                    imageVector =
                                        if (state.isPaused) {
                                            Icons.Filled.PlayCircle
                                        } else {
                                            Icons.Filled.PauseCircle
                                        },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(state.pauseActionLabel, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = state.onStop,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                        ) {
                            Icon(Icons.Filled.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("停止录制", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                else ->
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.canStart,
                        onClick = state.onStart,
                    ) {
                        Icon(Icons.Filled.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("开始录制", modifier = Modifier.padding(start = 8.dp))
                    }
            }
            MaterialDropdownSection("视频", state.videoPreferences)
            MaterialDropdownSection("声音", state.audioPreferences)
            MaterialSwitchSection("行为", state.behaviorPreferences)
            MaterialDropdownSection("快捷磁贴", state.tilePreferences)
            SegmentedColumn(
                modifier = Modifier.fillMaxWidth(),
                title = "存储",
                content =
                    buildList {
                        add {
                            SegmentedListItem(
                                onClick = state.onOpenStorage,
                                enabled = state.storageEnabled,
                                headlineContent = { Text("保存位置") },
                                supportingContent = { Text(state.storageSummary) },
                                trailingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                        if (state.showDefaultStorageAction) {
                            add {
                                SegmentedListItem(
                                    onClick = state.onUseDefaultStorage,
                                    enabled = state.storageEnabled,
                                    headlineContent = { Text("使用默认目录") },
                                    supportingContent = {
                                        Text("恢复保存到 /storage/emulated/0/DCIM/screenrecorder")
                                    },
                                )
                            }
                        }
                    },
            )
        }
    }
}

@Composable
private fun MaterialDropdownSection(
    title: String,
    preferences: List<ScreenRecordingDropdownUi>,
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = title,
        content =
            preferences.map { preference ->
                {
                    SegmentedDropdownItem(
                        title = preference.title,
                        summary = preference.summary,
                        items = preference.items,
                        selectedIndex = preference.selectedIndex.coerceAtLeast(0),
                        enabled = preference.enabled && preference.items.isNotEmpty(),
                        onItemSelected = preference.onItemSelected,
                    )
                }
            },
    )
}

@Composable
private fun MaterialSwitchSection(
    title: String,
    preferences: List<ScreenRecordingSwitchUi>,
) {
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = title,
        content =
            preferences.map { preference ->
                {
                    SegmentedSwitchItem(
                        title = preference.title,
                        summary = preference.summary,
                        checked = preference.checked,
                        enabled = preference.enabled,
                        onCheckedChange = preference.onCheckedChange,
                    )
                }
            },
    )
}

/**
 * Material twin of the IslandRecorder confirm dialog: same information architecture as Miuix
 * (title → summary → audio dropdown → show-touches → cancel/start), using only Material / KernelSU
 * Material segment components — never Miuix widgets.
 */
@Composable
fun ScreenRecordingMaterialConfirmDialog(state: ScreenRecordingConfirmUi) {
    Dialog(
        onDismissRequest = state.onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
            ) {
                Text(
                    text = "要开始屏幕录制吗？",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "屏幕录制会访问应用内容，请录制应用页面时注意保护隐私信息。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                SegmentedColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    content =
                        listOf(
                            {
                                SegmentedDropdownItem(
                                    title = "声音来源",
                                    summary =
                                        state.audioLabels
                                            .getOrNull(state.selectedAudioIndex)
                                            .orEmpty(),
                                    items = state.audioLabels,
                                    selectedIndex =
                                        state.selectedAudioIndex.coerceAtLeast(0),
                                    enabled = state.audioLabels.isNotEmpty(),
                                    onItemSelected = state.onAudioSelected,
                                )
                            },
                            {
                                SegmentedSwitchItem(
                                    title = "显示点按操作反馈",
                                    summary = state.showTouchesSummary.orEmpty(),
                                    checked = state.showTouches,
                                    enabled = state.showTouchesEnabled,
                                    onCheckedChange = state.onShowTouchesChange,
                                )
                            },
                        ),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = state.onCancel,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = state.onStart,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("开始录制")
                    }
                }
            }
        }
    }
}
