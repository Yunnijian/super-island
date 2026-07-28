package io.github.superisland

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.superisland.publisher.focus.FocusNotificationCapability
import io.github.superisland.publisher.focus.FocusNotificationPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class FocusLabDashboardState(
    val featureEnabled: Boolean = true,
    val notificationPermissionGranted: Boolean = false,
    val capability: FocusNotificationCapability = EMPTY_FOCUS_CAPABILITY,
    val progress: Int = 20,
    val operationStatus: String = "尚未发送测试事件",
    val loaded: Boolean = false,
    val isRefreshing: Boolean = false,
) {
    val canPublish: Boolean
        get() = featureEnabled && notificationPermissionGranted && capability.notificationsEnabled
}

/** Shared state and serialized notification work for every focus-test destination. */
internal class FocusLabDashboardStateOwner(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val channelName = appContext.getString(R.string.focus_notification_channel_name)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<Command>(Channel.BUFFERED)
    private val _state = MutableStateFlow(FocusLabDashboardState())
    val state: StateFlow<FocusLabDashboardState> = _state.asStateFlow()
    private var lastVisibleResumeGeneration = Int.MIN_VALUE

    init {
        scope.launch {
            for (command in commands) {
                val result = runCatching { withContext(Dispatchers.IO) { execute(command) } }
                result.onSuccess { completed ->
                    _state.value =
                        _state.value.copy(
                            featureEnabled = completed.featureEnabled,
                            notificationPermissionGranted = completed.permissionGranted,
                            capability = completed.capability,
                            progress = completed.progress,
                            operationStatus = completed.operationStatus,
                            loaded = true,
                            isRefreshing = false,
                        )
                }.onFailure { error ->
                    _state.value =
                        _state.value.copy(
                            loaded = true,
                            isRefreshing = false,
                            operationStatus = error.message ?: error.javaClass.simpleName,
                        )
                }
            }
        }
    }

    fun activate() {
        if (_state.value.loaded || _state.value.isRefreshing) return
        refresh()
    }

    fun onVisible(resumeGeneration: Int) {
        if (resumeGeneration == lastVisibleResumeGeneration) return
        val hadVisibleGeneration = lastVisibleResumeGeneration != Int.MIN_VALUE
        lastVisibleResumeGeneration = resumeGeneration
        if (!_state.value.loaded) {
            activate()
        } else if (hadVisibleGeneration) {
            refresh()
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(isRefreshing = true)
        if (commands.trySend(Command.Load).isFailure) {
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    fun setFeatureEnabled(enabled: Boolean) {
        _state.value =
            _state.value.copy(
                featureEnabled = enabled,
                operationStatus = if (enabled) "已启用焦点通知测试" else "已关闭焦点通知并结束测试事件",
            )
        commands.trySend(Command.SetFeatureEnabled(enabled))
    }

    fun publish() {
        if (!_state.value.canPublish) return
        commands.trySend(Command.Publish(_state.value.progress))
    }

    fun advance() {
        if (!_state.value.canPublish) return
        val next = if (_state.value.progress == 100) 0 else (_state.value.progress + 20).coerceAtMost(100)
        _state.value = _state.value.copy(progress = next)
        commands.trySend(Command.Publish(next))
    }

    fun cancelEvent() {
        _state.value = _state.value.copy(operationStatus = "测试事件已结束")
        commands.trySend(Command.Cancel)
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _state.value =
            _state.value.copy(
                notificationPermissionGranted = granted,
                operationStatus = if (granted) "通知权限已授予" else "通知权限被拒绝",
            )
        refresh()
    }

    override fun close() {
        scope.cancel()
    }

    private fun execute(command: Command): CompletedState {
        val publisher = FocusNotificationPublisher(appContext, channelName)
        val featureStore = FocusNotificationFeatureStore(appContext)
        var featureEnabled = featureStore.isEnabled()
        var progress = _state.value.progress
        var status = _state.value.operationStatus
        when (command) {
            Command.Load -> Unit
            is Command.SetFeatureEnabled -> {
                featureStore.setEnabled(command.enabled)
                featureEnabled = command.enabled
                if (!command.enabled) publisher.cancel()
                status = if (command.enabled) "已启用焦点通知测试" else "已关闭焦点通知并结束测试事件"
            }
            is Command.Publish -> {
                progress = command.progress
                publisher
                    .post(
                        request = focusNotificationRequest(progress),
                        smallIconResId = R.drawable.ic_stat_island,
                        contentIntent = contentIntent(),
                    ).getOrThrow()
                status = "已发布 $progress% 测试事件"
            }
            Command.Cancel -> {
                publisher.cancel()
                status = "测试事件已结束"
            }
        }
        return CompletedState(
            featureEnabled = featureEnabled,
            permissionGranted =
                appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED,
            capability = publisher.capability(),
            progress = progress,
            operationStatus = status,
        )
    }

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private sealed interface Command {
        data object Load : Command

        data class SetFeatureEnabled(val enabled: Boolean) : Command

        data class Publish(val progress: Int) : Command

        data object Cancel : Command
    }

    private data class CompletedState(
        val featureEnabled: Boolean,
        val permissionGranted: Boolean,
        val capability: FocusNotificationCapability,
        val progress: Int,
        val operationStatus: String,
    )
}

private val EMPTY_FOCUS_CAPABILITY =
    FocusNotificationCapability(
        notificationsEnabled = false,
        focusProtocolEnabled = false,
        systemPermissionGranted = false,
    )
