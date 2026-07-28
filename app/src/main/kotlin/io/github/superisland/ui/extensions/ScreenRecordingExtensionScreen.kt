package io.github.superisland.ui.extensions

import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.superisland.design.ScreenRecordingConfirmUi
import io.github.superisland.design.ScreenRecordingDetailUi
import io.github.superisland.design.ScreenRecordingDropdownUi
import io.github.superisland.design.ScreenRecordingMiuixConfirmDialog
import io.github.superisland.design.ScreenRecordingMiuixScreen
import io.github.superisland.design.ScreenRecordingSwitchUi
import io.github.superisland.model.ScreenRecordingAudioSource
import io.github.superisland.model.ScreenRecordingBitrate
import io.github.superisland.model.ScreenRecordingConfig
import io.github.superisland.model.ScreenRecordingFrameRate
import io.github.superisland.model.ScreenRecordingOrientation
import io.github.superisland.model.ScreenRecordingResolution
import io.github.superisland.model.ScreenRecordingTileStyle
import io.github.superisland.model.ScreenRecordingVideoCodec
import io.github.superisland.source.screenrecord.ScreenRecordingCaptureActivity
import io.github.superisland.source.screenrecord.ScreenRecordingFocusNotification
import io.github.superisland.source.screenrecord.ScreenRecordingPhase
import io.github.superisland.source.screenrecord.ScreenRecordingService
import io.github.superisland.ui.material.ScreenRecordingMaterialConfirmDialog
import io.github.superisland.ui.material.ScreenRecordingMaterialScreen
import kotlinx.coroutines.delay
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.util.rememberContentReady

private data class RecordingChoice<T>(
    val value: T,
    val label: String,
)

private val resolutionChoices =
    listOf(
        RecordingChoice(ScreenRecordingResolution.NATIVE, "原始分辨率"),
        RecordingChoice(ScreenRecordingResolution.FHD, "1080p"),
        RecordingChoice(ScreenRecordingResolution.HD, "720p"),
        RecordingChoice(ScreenRecordingResolution.SD, "480p"),
        RecordingChoice(ScreenRecordingResolution.LOW, "360p"),
    )

private val bitrateChoices =
    listOf(
        RecordingChoice(ScreenRecordingBitrate.AUTO, "自动"),
        RecordingChoice(ScreenRecordingBitrate.MBPS_1, "1 Mbps"),
        RecordingChoice(ScreenRecordingBitrate.MBPS_4, "4 Mbps"),
        RecordingChoice(ScreenRecordingBitrate.MBPS_6, "6 Mbps"),
        RecordingChoice(ScreenRecordingBitrate.MBPS_8, "8 Mbps"),
        RecordingChoice(ScreenRecordingBitrate.MBPS_16, "16 Mbps"),
        RecordingChoice(ScreenRecordingBitrate.MBPS_24, "24 Mbps"),
        RecordingChoice(ScreenRecordingBitrate.MBPS_32, "32 Mbps"),
        RecordingChoice(ScreenRecordingBitrate.MBPS_50, "50 Mbps"),
        RecordingChoice(ScreenRecordingBitrate.MBPS_100, "100 Mbps"),
    )

private val orientationChoices =
    listOf(
        RecordingChoice(ScreenRecordingOrientation.AUTO, "自动"),
        RecordingChoice(ScreenRecordingOrientation.PORTRAIT, "竖屏"),
        RecordingChoice(ScreenRecordingOrientation.LANDSCAPE, "横屏"),
    )

private val audioChoices =
    listOf(
        RecordingChoice(ScreenRecordingAudioSource.NONE, "无声音"),
        RecordingChoice(ScreenRecordingAudioSource.INTERNAL, "内部声音"),
        RecordingChoice(ScreenRecordingAudioSource.MICROPHONE, "麦克风"),
        RecordingChoice(ScreenRecordingAudioSource.BOTH, "内部声音与麦克风"),
    )

private val frameRateChoices =
    listOf(
        RecordingChoice(ScreenRecordingFrameRate.AUTO, "自动"),
        RecordingChoice(ScreenRecordingFrameRate.FPS_15, "15 fps"),
        RecordingChoice(ScreenRecordingFrameRate.FPS_24, "24 fps"),
        RecordingChoice(ScreenRecordingFrameRate.FPS_30, "30 fps"),
        RecordingChoice(ScreenRecordingFrameRate.FPS_48, "48 fps"),
        RecordingChoice(ScreenRecordingFrameRate.FPS_60, "60 fps"),
        RecordingChoice(ScreenRecordingFrameRate.FPS_90, "90 fps"),
        RecordingChoice(ScreenRecordingFrameRate.FPS_120, "120 fps"),
    )

private val videoCodecChoices =
    listOf(
        RecordingChoice(ScreenRecordingVideoCodec.H264, "H.264"),
        RecordingChoice(ScreenRecordingVideoCodec.H265, "H.265"),
    )

private val tileStyleChoices =
    listOf(
        RecordingChoice(ScreenRecordingTileStyle.RECORDING, "录制图标"),
        RecordingChoice(ScreenRecordingTileStyle.APP_ICON, "模块图标"),
    )

@Composable
fun ScreenRecordingExtensionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val contentReady = rememberContentReady()
    val state by ScreenRecordingExtensionUiStateOwner.state.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }
    val treePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null || uri.scheme != "content" || !DocumentsContract.isTreeUri(uri)) {
                return@rememberLauncherForActivityResult
            }
            val flags =
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }.onSuccess {
                ScreenRecordingExtensionUiStateOwner.update(applicationContext) { config ->
                    config.copy(storageTreeUri = uri.toString())
                }
            }
        }

    LaunchedEffect(contentReady, applicationContext) {
        if (contentReady) ScreenRecordingExtensionUiStateOwner.prepare(applicationContext)
    }

    var nowElapsedRealtime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.runtime.phase, state.runtime.startedAtElapsedRealtime) {
        if (
            state.runtime.phase == ScreenRecordingPhase.RECORDING &&
                state.runtime.startedAtElapsedRealtime > 0L
        ) {
            while (true) {
                nowElapsedRealtime = SystemClock.elapsedRealtime()
                delay(1_000L)
            }
        } else {
            nowElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }
    // Backup poll while active: if prefs listeners miss the IDLE publish after stop, recover UI.
    LaunchedEffect(state.runtime.phase, contentReady) {
        if (!contentReady) return@LaunchedEffect
        if (!state.runtime.phase.isActive) return@LaunchedEffect
        while (true) {
            delay(500L)
            ScreenRecordingExtensionUiStateOwner.refreshRuntime(applicationContext)
            // Exit when phase becomes non-active via state collection.
            if (!ScreenRecordingExtensionUiStateOwner.state.value.runtime.phase.isActive) break
        }
    }

    fun launchCapture() {
        // Flush dialog/preference edits before CaptureActivity reloads SharedPreferences.
        ScreenRecordingExtensionUiStateOwner.commitNow(applicationContext)
        context.startActivity(ScreenRecordingCaptureActivity.captureIntent(context))
    }

    val actions =
        ScreenRecordingScreenActions(
            onStart = {
                if (state.config.confirmBeforeStart) {
                    showConfirmDialog = true
                } else {
                    launchCapture()
                }
            },
            onStop = {
                // Safe path: clear stale phase or deliver STOP to a live in-process session.
                // Force UI refresh immediately so we do not depend only on preference listeners.
                android.util.Log.i(
                    "SuperIslandScreenRecord",
                    "UI onStop click phase=${state.runtime.phase} " +
                        "hasSession=${ScreenRecordingService.hasActiveSession()}",
                )
                ScreenRecordingService.requestStop(context)
                ScreenRecordingExtensionUiStateOwner.refreshRuntime(applicationContext)
            },
            onOpenStorage = { treePicker.launch(null) },
            onUseDefaultStorage = {
                ScreenRecordingExtensionUiStateOwner.update(applicationContext) { config ->
                    config.copy(storageTreeUri = "")
                }
            },
            onUpdate = { transform ->
                ScreenRecordingExtensionUiStateOwner.update(applicationContext, transform)
            },
            onProjectMediaChange = { allowed ->
                ScreenRecordingExtensionUiStateOwner.setProjectMediaAllowed(applicationContext, allowed)
            },
        )
    val elapsedMillis =
        if (
            state.runtime.phase == ScreenRecordingPhase.RECORDING &&
                state.runtime.startedAtElapsedRealtime > 0L
        ) {
            (nowElapsedRealtime - state.runtime.startedAtElapsedRealtime).coerceAtLeast(0L)
        } else {
            0L
        }
    val detail = state.toDetailUi(actions, elapsedMillis)
    when (LocalUiMode.current) {
        UiMode.Miuix -> ScreenRecordingMiuixScreen(state = detail, onBack = onBack)
        UiMode.Material -> ScreenRecordingMaterialScreen(state = detail, onBack = onBack)
    }

    if (showConfirmDialog) {
        val confirm =
            ScreenRecordingConfirmUi(
                audioLabels = audioChoices.map { it.label },
                selectedAudioIndex =
                    audioChoices.indexOfFirst { it.value == state.config.audioSource }.coerceAtLeast(0),
                showTouches = state.config.showTouches,
                showTouchesEnabled = state.rootCapability.supported,
                showTouchesSummary =
                    if (state.rootCapability.supported) {
                        null
                    } else {
                        "需要 Root 权限"
                    },
                onAudioSelected = { index ->
                    audioChoices.getOrNull(index)?.let { choice ->
                        ScreenRecordingExtensionUiStateOwner.update(applicationContext) {
                            it.copy(audioSource = choice.value)
                        }
                    }
                },
                onShowTouchesChange = { checked ->
                    ScreenRecordingExtensionUiStateOwner.update(applicationContext) {
                        it.copy(showTouches = checked)
                    }
                },
                onCancel = { showConfirmDialog = false },
                onStart = {
                    showConfirmDialog = false
                    launchCapture()
                },
            )
        when (LocalUiMode.current) {
            UiMode.Miuix -> ScreenRecordingMiuixConfirmDialog(confirm)
            UiMode.Material -> ScreenRecordingMaterialConfirmDialog(confirm)
        }
    }
}

private data class ScreenRecordingScreenActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onOpenStorage: () -> Unit,
    val onUseDefaultStorage: () -> Unit,
    val onUpdate: ((ScreenRecordingConfig) -> ScreenRecordingConfig) -> Unit,
    val onProjectMediaChange: (Boolean) -> Unit,
)

private fun ScreenRecordingExtensionUiState.toDetailUi(
    actions: ScreenRecordingScreenActions,
    elapsedMillis: Long,
): ScreenRecordingDetailUi {
    val canEdit = contentLoaded && !runtime.phase.isActive
    val systemSettingSummary =
        if (rootCapability.supported) {
            "仅在录制期间生效，停止后恢复原设置"
        } else {
            "${rootCapability.summary}；不会改动系统设置"
        }
    val projectMediaSummary =
        when {
            projectMediaBusy -> "正在写入权限…"
            !rootCapability.supported ->
                "需要 Root；失败时仍走系统投影授权"
            projectMediaAllowed ->
                "权限已授予；系统投影页通常可跳过或自动通过"
            else ->
                "通过 Root 授予投影媒体权限，尽量跳过系统授权页"
        }
    val durationLabel = ScreenRecordingFocusNotification.formatDuration(elapsedMillis)
    return ScreenRecordingDetailUi(
        statusTitle = recordingStatusTitle(runtime.phase, durationLabel),
        statusSummary = statusSummary(durationLabel),
        active = runtime.phase.isActive,
        canStart = contentLoaded && !runtime.phase.isActive,
        showStopAction =
            runtime.phase == ScreenRecordingPhase.RECORDING ||
                runtime.phase == ScreenRecordingPhase.PREPARING,
        showFinalizingAction = runtime.phase == ScreenRecordingPhase.FINALIZING,
        videoPreferences =
            listOf(
                dropdown("分辨率", "录制画面的输出尺寸", resolutionChoices, config.resolution, canEdit) { value ->
                    actions.onUpdate { it.copy(resolution = value) }
                },
                dropdown("视频码率", "自动会按分辨率和帧率选择范围内码率", bitrateChoices, config.bitrate, canEdit) { value ->
                    actions.onUpdate { it.copy(bitrate = value) }
                },
                dropdown("屏幕方向", "固定输出方向不会改变设备当前旋转", orientationChoices, config.orientation, canEdit) { value ->
                    actions.onUpdate { it.copy(orientation = value) }
                },
                dropdown("最大帧率", "受当前显示模式和编码器能力限制", frameRateChoices, config.frameRate, canEdit) { value ->
                    actions.onUpdate { it.copy(frameRate = value) }
                },
                dropdown("视频编码", "H.265 不支持时不会开始录制", videoCodecChoices, config.videoCodec, canEdit) { value ->
                    actions.onUpdate { it.copy(videoCodec = value) }
                },
            ),
        audioPreferences =
            listOf(
                dropdown("声音来源", "录制声音时会由系统请求录音权限", audioChoices, config.audioSource, canEdit) { value ->
                    actions.onUpdate { it.copy(audioSource = value) }
                },
            ),
        behaviorPreferences =
            listOf(
                ScreenRecordingSwitchUi(
                    title = "开始前确认",
                    summary = "开启后先显示确认弹窗，再进入投影授权",
                    checked = config.confirmBeforeStart,
                    enabled = canEdit,
                    onCheckedChange = { checked ->
                        actions.onUpdate { it.copy(confirmBeforeStart = checked) }
                    },
                ),
                ScreenRecordingSwitchUi(
                    title = "授予投影媒体权限",
                    summary = projectMediaSummary,
                    checked = projectMediaAllowed,
                    enabled = canEdit && rootCapability.supported && !projectMediaBusy,
                    onCheckedChange = actions.onProjectMediaChange,
                ),
                ScreenRecordingSwitchUi(
                    title = "显示触控反馈",
                    summary = systemSettingSummary,
                    checked = config.showTouches,
                    enabled = canEdit && rootCapability.supported,
                    onCheckedChange = { checked -> actions.onUpdate { it.copy(showTouches = checked) } },
                ),
                ScreenRecordingSwitchUi(
                    title = "熄屏时停止录制",
                    summary = "检测到屏幕熄灭时完成当前录制",
                    checked = config.stopOnLockScreen,
                    enabled = canEdit,
                    onCheckedChange = { checked -> actions.onUpdate { it.copy(stopOnLockScreen = checked) } },
                ),
                ScreenRecordingSwitchUi(
                    title = "暂时关闭屏幕共享保护",
                    summary = systemSettingSummary,
                    checked = config.bypassScreenShareProtection,
                    enabled = canEdit && rootCapability.supported,
                    onCheckedChange = { checked ->
                        actions.onUpdate { it.copy(bypassScreenShareProtection = checked) }
                    },
                ),
            ),
        tilePreferences =
            listOf(
                dropdown("图标样式", "添加到系统快捷设置后的图标", tileStyleChoices, config.tileStyle, canEdit) { value ->
                    actions.onUpdate { it.copy(tileStyle = value) }
                },
            ),
        storageSummary = storageSummary,
        storageEnabled = canEdit,
        showDefaultStorageAction = config.storageTreeUri.isNotBlank(),
        onStart = actions.onStart,
        onStop = actions.onStop,
        onOpenStorage = actions.onOpenStorage,
        onUseDefaultStorage = actions.onUseDefaultStorage,
    )
}

private fun ScreenRecordingExtensionUiState.statusSummary(durationLabel: String): String =
    when (runtime.phase) {
        ScreenRecordingPhase.RECORDING ->
            if (durationLabel.isNotBlank()) {
                "已录制 $durationLabel"
            } else {
                runtime.message.ifBlank { "正在录制屏幕" }
            }
        ScreenRecordingPhase.PREPARING -> runtime.message.ifBlank { "正在准备录制" }
        ScreenRecordingPhase.FINALIZING -> runtime.message.ifBlank { "正在完成文件" }
        ScreenRecordingPhase.ERROR -> runtime.message.ifBlank { "上一次录制未完成" }
        ScreenRecordingPhase.IDLE -> runtime.message.ifBlank { "尚未开始录制" }
    }

private fun recordingStatusTitle(
    phase: ScreenRecordingPhase,
    durationLabel: String,
): String =
    when (phase) {
        ScreenRecordingPhase.IDLE -> "准备就绪"
        ScreenRecordingPhase.PREPARING -> "正在准备"
        ScreenRecordingPhase.RECORDING ->
            if (durationLabel.isNotBlank()) "正在录制 · $durationLabel" else "正在录制"
        ScreenRecordingPhase.FINALIZING -> "正在完成"
        ScreenRecordingPhase.ERROR -> "录制失败"
    }

private fun <T> dropdown(
    title: String,
    summary: String,
    choices: List<RecordingChoice<T>>,
    selected: T,
    enabled: Boolean,
    onSelected: (T) -> Unit,
): ScreenRecordingDropdownUi {
    val index = choices.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    return ScreenRecordingDropdownUi(
        title = title,
        summary = summary,
        items = choices.map { it.label },
        selectedIndex = index,
        enabled = enabled,
        onItemSelected = { selectedIndex ->
            choices.getOrNull(selectedIndex)?.let { onSelected(it.value) }
        },
    )
}
