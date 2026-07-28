package io.github.superisland

import android.content.Context
import io.github.superisland.source.notification.MediaIslandSnapshot
import io.github.superisland.source.notification.NotificationProxyController
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

internal data class MediaIslandDashboardState(
    val snapshot: MediaIslandSnapshot = EMPTY_MEDIA_ISLAND_SNAPSHOT,
    val loaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val operationStatus: String = "",
)

/** Root-stable media owner. System-service and preference reads never run in a transition frame. */
internal class MediaIslandDashboardStateOwner(context: Context) : AutoCloseable {
    private val controller = NotificationProxyController(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<Command>(Channel.BUFFERED)
    private val _state = MutableStateFlow(MediaIslandDashboardState())
    val state: StateFlow<MediaIslandDashboardState> = _state.asStateFlow()
    private var lastVisibleResumeGeneration = Int.MIN_VALUE

    private val unregister =
        controller.observeSnapshotChanges {
            commands.trySend(Command.Load(refreshService = false))
        }

    init {
        scope.launch {
            for (command in commands) {
                val result =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            execute(command)
                        }
                    }
                result.onSuccess { completed ->
                    _state.value =
                        _state.value.copy(
                            snapshot = completed.snapshot,
                            loaded = true,
                            isRefreshing = false,
                            operationStatus = completed.operationStatus ?: _state.value.operationStatus,
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
        enqueueLoad(refreshService = true)
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
        enqueueLoad(refreshService = true, status = "已请求刷新媒体会话")
    }

    fun setFeatureEnabled(enabled: Boolean) {
        _state.value =
            _state.value.copy(
                snapshot = _state.value.snapshot.copy(featureEnabled = enabled),
                operationStatus = if (enabled) "已启用超级岛音乐" else "已关闭超级岛音乐并结束现有事件",
            )
        commands.trySend(Command.SetFeatureEnabled(enabled))
    }

    fun toggleCandidate(id: String) {
        val snapshot = _state.value.snapshot
        val candidate = snapshot.candidates.firstOrNull { it.id == id } ?: return
        val wasEnabled = snapshot.enabledSources.any { it.id == id }
        val enabledSources =
            if (wasEnabled) {
                snapshot.enabledSources.filterNot { it.id == id }
            } else {
                (snapshot.enabledSources + candidate).distinctBy { it.id }
            }
        _state.value =
            _state.value.copy(
                snapshot = snapshot.copy(enabledSources = enabledSources),
                operationStatus = "已${if (wasEnabled) "停止允许" else "允许"} ${candidate.appLabel} 的媒体岛",
            )
        commands.trySend(Command.ToggleCandidate(id))
    }

    fun clearRules() {
        _state.value =
            _state.value.copy(
                snapshot = _state.value.snapshot.copy(enabledSources = emptyList()),
                operationStatus = "已清空播放器允许列表",
            )
        commands.trySend(Command.ClearRules)
    }

    override fun close() {
        unregister()
        scope.cancel()
    }

    private fun enqueueLoad(
        refreshService: Boolean,
        status: String? = null,
    ) {
        _state.value =
            _state.value.copy(
                isRefreshing = true,
                operationStatus = status ?: _state.value.operationStatus,
            )
        if (commands.trySend(Command.Load(refreshService)).isFailure) {
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    private fun execute(command: Command): CompletedCommand {
        val status =
            when (command) {
                is Command.Load -> {
                    if (command.refreshService) controller.refreshMedia()
                    null
                }
                is Command.SetFeatureEnabled -> {
                    controller.setMediaFeatureEnabled(command.enabled)
                    if (command.enabled) "已启用超级岛音乐" else "已关闭超级岛音乐并结束现有事件"
                }
                is Command.ToggleCandidate ->
                    controller.toggleMediaCandidate(command.id)?.let { result ->
                        "已${if (result.enabled) "允许" else "停止允许"} ${result.source.appLabel} 的媒体岛"
                    }
                Command.ClearRules -> {
                    controller.clearMediaRules()
                    "已清空播放器允许列表"
                }
            }
        return CompletedCommand(controller.mediaSnapshot(), status)
    }

    private sealed interface Command {
        data class Load(val refreshService: Boolean) : Command

        data class SetFeatureEnabled(val enabled: Boolean) : Command

        data class ToggleCandidate(val id: String) : Command

        data object ClearRules : Command
    }

    private data class CompletedCommand(
        val snapshot: MediaIslandSnapshot,
        val operationStatus: String?,
    )
}

private val EMPTY_MEDIA_ISLAND_SNAPSHOT =
    MediaIslandSnapshot(
        featureEnabled = false,
        notificationAccessGranted = false,
        listenerConnected = false,
        enabledSources = emptyList(),
        candidates = emptyList(),
        activeMediaCount = 0,
        lastResult = "",
    )
