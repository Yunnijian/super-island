package io.github.superisland.ui.extensions

import android.content.Context
import android.content.SharedPreferences
import io.github.superisland.model.ScreenRecordingConfig
import io.github.superisland.source.screenrecord.ScreenRecordingConfigStore
import io.github.superisland.source.screenrecord.ScreenRecordingProjectMedia
import io.github.superisland.source.screenrecord.ScreenRecordingRootCapability
import io.github.superisland.source.screenrecord.ScreenRecordingRootSettings
import io.github.superisland.source.screenrecord.ScreenRecordingRuntimeHub
import io.github.superisland.source.screenrecord.ScreenRecordingRuntimeState
import io.github.superisland.source.screenrecord.ScreenRecordingRuntimeStore
import io.github.superisland.source.screenrecord.ScreenRecordingService
import io.github.superisland.source.screenrecord.ScreenRecordingStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ScreenRecordingExtensionUiState(
    val config: ScreenRecordingConfig = ScreenRecordingConfig(),
    val rootCapability: ScreenRecordingRootCapability =
        ScreenRecordingRootCapability(
            supported = false,
            summary = "正在检查录屏 Root 设置能力",
        ),
    val projectMediaAllowed: Boolean = false,
    val projectMediaBusy: Boolean = false,
    val runtime: ScreenRecordingRuntimeState = ScreenRecordingRuntimeState(),
    val contentLoaded: Boolean = false,
) {
    val storageSummary: String
        get() =
            if (config.storageTreeUri.isBlank()) {
                "默认保存到 ${ScreenRecordingStorage.DEFAULT_STORAGE_PATH}"
            } else {
                "已选择自定义目录"
            }
}

/**
 * The only configuration owner for the recording detail. Background preference reads never block
 * the navigation transition; both visual skins receive the same immediate update and rollback.
 */
internal object ScreenRecordingExtensionUiStateOwner {
    private val lock = Any()
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveRequests = Channel<SaveRequest>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(ScreenRecordingExtensionUiState())

    val state: StateFlow<ScreenRecordingExtensionUiState> = mutableState.asStateFlow()

    private var prepareInFlight = false
    private var prepared = false
    private var configStore: ScreenRecordingConfigStore? = null
    private var runtimeStore: ScreenRecordingRuntimeStore? = null
    private var configListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var runtimeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var hubListener: ScreenRecordingRuntimeHub.Listener? = null
    private var persistedConfig = ScreenRecordingConfig()
    private var latestGeneration = 0L
    private var pendingWriteCount = 0

    init {
        workerScope.launch {
            for (request in saveRequests) persist(request)
        }
    }

    /**
     * Re-read runtime after start/stop so the detail page does not wait solely on
     * SharedPreferences listeners (which can race the Compose frame after an in-process stop).
     */
    fun refreshRuntime(context: Context) {
        val applicationContext = context.applicationContext
        workerScope.launch {
            val runtime = ScreenRecordingService.reconcileRuntime(applicationContext)
            ScreenRecordingRuntimeHub.publish(runtime)
            applyRuntime(runtime)
        }
    }

    fun prepare(context: Context) {
        val applicationContext = context.applicationContext
        synchronized(lock) {
            if (prepared) {
                // Re-enter detail: heal disk phase left active after process/service death.
                workerScope.launch {
                    val runtime = ScreenRecordingService.reconcileRuntime(applicationContext)
                    ScreenRecordingRuntimeHub.publish(runtime)
                    applyRuntime(runtime)
                }
                return
            }
            if (prepareInFlight) return
            prepareInFlight = true
        }
        workerScope.launch {
            var candidateConfigStore: ScreenRecordingConfigStore? = null
            var candidateRuntimeStore: ScreenRecordingRuntimeStore? = null
            var candidateConfigListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
            var candidateRuntimeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
            val result =
                runCatching {
                    candidateConfigStore = ScreenRecordingConfigStore(applicationContext)
                    candidateRuntimeStore = ScreenRecordingRuntimeStore(applicationContext)
                    candidateConfigListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                        observeConfig(candidateConfigStore!!)
                    }
                    candidateRuntimeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                        observeRuntime(candidateRuntimeStore!!)
                    }
                    candidateConfigStore!!.registerListener(candidateConfigListener!!)
                    candidateRuntimeStore!!.observe(candidateRuntimeListener!!)
                    // Clear disk phase stuck on preparing/recording after process death so the UI
                    // shows「开始录制」instead of a dead「停止录制」button.
                    val runtime = ScreenRecordingService.reconcileRuntime(applicationContext)
                    ScreenRecordingRuntimeHub.publish(runtime)
                    LoadedState(
                        config = candidateConfigStore!!.load(),
                        runtime = runtime,
                        rootCapability = ScreenRecordingRootSettings(applicationContext).capability(),
                        projectMediaAllowed = ScreenRecordingProjectMedia.isAllowed(applicationContext),
                    )
                }
            result.fold(
                onSuccess = { loaded ->
                    val hub =
                        ScreenRecordingRuntimeHub.Listener { runtime ->
                            applyRuntime(runtime)
                        }
                    ScreenRecordingRuntimeHub.addListener(hub)
                    synchronized(lock) {
                        configStore = candidateConfigStore
                        runtimeStore = candidateRuntimeStore
                        configListener = candidateConfigListener
                        runtimeListener = candidateRuntimeListener
                        hubListener = hub
                        persistedConfig = loaded.config
                        prepared = true
                        prepareInFlight = false
                        mutableState.value =
                            mutableState.value.copy(
                                config =
                                    if (pendingWriteCount == 0) loaded.config else mutableState.value.config,
                                runtime = loaded.runtime,
                                rootCapability = loaded.rootCapability,
                                projectMediaAllowed = loaded.projectMediaAllowed,
                                contentLoaded = true,
                            )
                    }
                },
                onFailure = {
                    candidateConfigStore?.let { store -> candidateConfigListener?.let(store::unregisterListener) }
                    candidateRuntimeStore?.let { store -> candidateRuntimeListener?.let(store::stopObserving) }
                    synchronized(lock) { prepareInFlight = false }
                },
            )
        }
    }

    private fun applyRuntime(runtime: ScreenRecordingRuntimeState) {
        synchronized(lock) {
            if (!prepared) return
            if (mutableState.value.runtime == runtime) return
            mutableState.value = mutableState.value.copy(runtime = runtime)
        }
    }

    fun update(
        context: Context,
        transform: (ScreenRecordingConfig) -> ScreenRecordingConfig,
    ) {
        val request =
            synchronized(lock) {
                val current = mutableState.value.config
                val next = transform(current).normalized()
                if (next == current) return
                latestGeneration += 1
                pendingWriteCount += 1
                mutableState.value = mutableState.value.copy(config = next)
                SaveRequest(
                    applicationContext = context.applicationContext,
                    generation = latestGeneration,
                    config = next,
                )
            }
        check(saveRequests.trySend(request).isSuccess) { "录屏设置保存器不可用" }
    }

    /**
     * Synchronously persists the current (or transformed) config so CaptureActivity cannot race a
     * still-queued async save when the user confirms the start dialog.
     */
    fun commitNow(
        context: Context,
        transform: (ScreenRecordingConfig) -> ScreenRecordingConfig = { it },
    ): ScreenRecordingConfig {
        val applicationContext = context.applicationContext
        val next =
            synchronized(lock) {
                val transformed = transform(mutableState.value.config).normalized()
                mutableState.value = mutableState.value.copy(config = transformed)
                persistedConfig = transformed
                transformed
            }
        val store = synchronized(lock) { configStore } ?: ScreenRecordingConfigStore(applicationContext)
        check(store.save(next)) { "录屏设置写入失败" }
        return next
    }

    /**
     * Root-gated PROJECT_MEDIA AppOps write. Updates live [ScreenRecordingExtensionUiState.projectMediaAllowed]
     * and persists [ScreenRecordingConfig.projectMediaEnabled] only after a successful write.
     */
    fun setProjectMediaAllowed(
        context: Context,
        allowed: Boolean,
    ) {
        val applicationContext = context.applicationContext
        synchronized(lock) {
            if (mutableState.value.projectMediaBusy) return
            mutableState.value = mutableState.value.copy(projectMediaBusy = true)
        }
        workerScope.launch {
            val result =
                runCatching {
                    // Prefer closed Root cmd appops (reliable on warsaw). SystemUI AppOps setMode
                    // is not used: SystemUI lacks a durable identity for package-mode writes.
                    ScreenRecordingProjectMedia.setAllowed(applicationContext, allowed).getOrThrow()
                    ScreenRecordingProjectMedia.isAllowed(applicationContext)
                }
            synchronized(lock) {
                val actual = result.getOrDefault(ScreenRecordingProjectMedia.isAllowed(applicationContext))
                val failureMessage =
                    result.exceptionOrNull()?.message?.take(120)
                val nextConfig =
                    mutableState.value.config.copy(projectMediaEnabled = actual).normalized()
                val previousRuntime = mutableState.value.runtime
                mutableState.value =
                    mutableState.value.copy(
                        projectMediaAllowed = actual,
                        projectMediaBusy = false,
                        config = nextConfig,
                        runtime =
                            if (result.isFailure && failureMessage != null) {
                                previousRuntime.copy(message = "投影媒体权限：$failureMessage")
                            } else {
                                previousRuntime
                            },
                    )
                if (result.isSuccess) {
                    latestGeneration += 1
                    pendingWriteCount += 1
                    saveRequests.trySend(
                        SaveRequest(
                            applicationContext = applicationContext,
                            generation = latestGeneration,
                            config = nextConfig,
                        ),
                    )
                }
            }
        }
    }

    private fun persist(request: SaveRequest) {
        val result =
            runCatching {
                val store = synchronized(lock) { configStore } ?: ScreenRecordingConfigStore(request.applicationContext)
                check(store.save(request.config)) { "录屏设置写入失败" }
            }
        synchronized(lock) {
            pendingWriteCount = (pendingWriteCount - 1).coerceAtLeast(0)
            if (result.isSuccess) {
                persistedConfig = request.config
            } else if (request.generation == latestGeneration) {
                mutableState.value = mutableState.value.copy(config = persistedConfig)
            }
        }
    }

    private fun observeConfig(store: ScreenRecordingConfigStore) {
        workerScope.launch {
            val observed = store.load()
            synchronized(lock) {
                persistedConfig = observed
                if (prepared && pendingWriteCount == 0) {
                    mutableState.value = mutableState.value.copy(config = observed)
                }
            }
        }
    }

    private fun observeRuntime(store: ScreenRecordingRuntimeStore) {
        workerScope.launch {
            // Preference path remains as a secondary channel; hub publish is the primary UI path.
            val observed = reconcileLoadedRuntime(store)
            ScreenRecordingRuntimeHub.publish(observed)
            applyRuntime(observed)
        }
    }

    private fun reconcileLoadedRuntime(store: ScreenRecordingRuntimeStore): ScreenRecordingRuntimeState {
        val current = store.load()
        if (current.phase.isActive && !ScreenRecordingService.hasActiveSession()) {
            val cleared =
                ScreenRecordingRuntimeState(
                    phase = io.github.superisland.source.screenrecord.ScreenRecordingPhase.IDLE,
                    message = "上一次录制已中断",
                )
            store.save(cleared)
            return cleared
        }
        return current
    }

    private data class LoadedState(
        val config: ScreenRecordingConfig,
        val runtime: ScreenRecordingRuntimeState,
        val rootCapability: ScreenRecordingRootCapability,
        val projectMediaAllowed: Boolean,
    )

    private data class SaveRequest(
        val applicationContext: Context,
        val generation: Long,
        val config: ScreenRecordingConfig,
    )
}
