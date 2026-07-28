package io.github.superisland

import android.content.Context
import io.github.superisland.design.NotificationSourceOptionUi
import io.github.superisland.design.SmartCapsuleAppSortConfig
import io.github.superisland.model.SmartCapsuleConfigSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SmartCapsuleDashboardState(
    val snapshot: SmartCapsuleConfigSnapshot =
        SmartCapsuleConfigSnapshot.disabled(SmartCapsuleConfigStore.currentProcessUserId()),
    val configAccepted: Boolean = false,
    val configSyncFailed: Boolean = false,
    val runtime: SmartCapsuleRuntimeSnapshot = SmartCapsuleRuntimeSnapshot(),
    val appEntries: List<SmartCapsuleAppEntry> = emptyList(),
    val appOptions: List<NotificationSourceOptionUi> = emptyList(),
    val appPagePrepared: Boolean = false,
    val isAppDirectoryRefreshing: Boolean = false,
    val appListPermissionRequired: Boolean = false,
    val showSystemApps: Boolean = false,
    val appSortConfig: SmartCapsuleAppSortConfig = SmartCapsuleAppSortConfig(),
)

/**
 * Root-stable owner for every Smart Capsule Navigation3 destination.
 *
 * Store observers emit invalidations only. Conflated channels merge SharedPreferences key bursts,
 * and all JSON/digest/package work runs away from the main thread before one immutable state is
 * published to Compose.
 */
internal class SmartCapsuleDashboardStateOwner(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    internal val configStore = SmartCapsuleConfigStore(appContext)
    internal val channelCatalogStore = SmartCapsuleChannelCatalogStore(appContext)
    private val acceptanceStore = SmartCapsuleConsumerAcceptanceStore(appContext)
    private val runtimeStore = SmartCapsuleRuntimeStore(appContext)
    private val appDirectory = SmartCapsuleAppDirectory(appContext)
    private val appListPreferences = SmartCapsuleAppListPreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val configReloadRequests = Channel<Unit>(Channel.CONFLATED)
    private val runtimeReloadRequests = Channel<Unit>(Channel.CONFLATED)
    private val appReloadRequests = Channel<Unit>(Channel.CONFLATED)
    private val appProjectionRequests = Channel<Unit>(Channel.CONFLATED)
    private val forceAppRefresh = AtomicBoolean(false)
    private var configUpdateGeneration = 0
    private var appProjectionGeneration = 0
    private var pendingConfigMutations = 0
    private var appInitialRefreshPending = false
    private var appPermissionCheckInFlight = false
    private var completeInitialRefreshOnNextProjection = false

    private val _state =
        MutableStateFlow(
            SmartCapsuleDashboardState(
                appEntries = appDirectory.cachedEntries(),
                showSystemApps = appListPreferences.showSystemApps(),
                appSortConfig = appListPreferences.sortConfig(),
            ),
        )
    val state: StateFlow<SmartCapsuleDashboardState> = _state.asStateFlow()

    private val unregisterConfig = configStore.observeChanges(::requestConfigReload)
    private val unregisterAcceptance = acceptanceStore.observe(::requestConfigReload)
    private val unregisterRuntime = runtimeStore.observeChanges(::requestRuntimeReload)

    init {
        scope.launch {
            for (ignored in configReloadRequests) {
                coalesce(configReloadRequests)
                val loaded =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val snapshot = configStore.load()
                            snapshot to acceptanceStore.isAccepted(snapshot)
                        }
                    }.getOrNull() ?: continue
                val publishPersistedSnapshot = pendingConfigMutations == 0
                _state.value =
                    _state.value.copy(
                        snapshot =
                            if (publishPersistedSnapshot) {
                                loaded.first
                            } else {
                                _state.value.snapshot
                            },
                        configAccepted = publishPersistedSnapshot && loaded.second,
                        configSyncFailed =
                            _state.value.configSyncFailed &&
                                !(publishPersistedSnapshot && loaded.second),
                    )
                if (publishPersistedSnapshot) requestAppProjection()
            }
        }
        scope.launch {
            for (ignored in runtimeReloadRequests) {
                coalesce(runtimeReloadRequests)
                val runtime =
                    runCatching { withContext(Dispatchers.IO) { runtimeStore.snapshot() } }
                        .getOrNull() ?: continue
                _state.value = _state.value.copy(runtime = runtime)
            }
        }
        scope.launch {
            for (ignored in appReloadRequests) {
                coalesce(appReloadRequests)
                val forceRefresh = forceAppRefresh.getAndSet(false)
                val entries =
                    runCatching {
                        withContext(Dispatchers.IO) { appDirectory.load(forceRefresh) }
                    }.getOrNull()
                _state.value =
                    if (entries == null) {
                        val initialRefreshFailed = appInitialRefreshPending
                        appInitialRefreshPending = false
                        completeInitialRefreshOnNextProjection = false
                        _state.value.copy(
                            isAppDirectoryRefreshing = false,
                            appPagePrepared = _state.value.appPagePrepared || initialRefreshFailed,
                            appListPermissionRequired = false,
                        )
                    } else {
                        completeInitialRefreshOnNextProjection = appInitialRefreshPending
                        _state.value.copy(
                            appEntries = entries,
                            isAppDirectoryRefreshing = appInitialRefreshPending,
                        )
                    }
                if (entries != null) requestAppProjection()
            }
        }
        scope.launch {
            for (ignored in appProjectionRequests) {
                coalesce(appProjectionRequests)
                val generation = appProjectionGeneration
                val current = _state.value
                val options =
                    withContext(Dispatchers.Default) {
                        buildSmartCapsuleDirectoryUiModel(
                            entries = current.appEntries,
                            rules = current.snapshot.rules,
                            showSystemApps = current.showSystemApps,
                            selectedPackage = null,
                            includeAppOptions = true,
                            sortConfig = current.appSortConfig,
                        ).appOptions
                    }
                if (generation == appProjectionGeneration) {
                    val initialRefreshCompleted = completeInitialRefreshOnNextProjection
                    if (initialRefreshCompleted) {
                        appInitialRefreshPending = false
                        completeInitialRefreshOnNextProjection = false
                    }
                    _state.value =
                        _state.value.copy(
                            appOptions = options,
                            appPagePrepared =
                                _state.value.appPagePrepared || initialRefreshCompleted,
                            isAppDirectoryRefreshing =
                                if (initialRefreshCompleted) {
                                    false
                                } else {
                                    _state.value.isAppDirectoryRefreshing
                                },
                            appListPermissionRequired = false,
                        )
                }
            }
        }
        refreshConfigAndRuntime()
    }

    fun refreshConfigAndRuntime() {
        requestConfigReload()
        requestRuntimeReload()
    }

    fun refreshAppEntries(forceRefresh: Boolean = false) {
        if (forceRefresh) forceAppRefresh.set(true)
        _state.value = _state.value.copy(isAppDirectoryRefreshing = true)
        if (appReloadRequests.trySend(Unit).isFailure) {
            _state.value = _state.value.copy(isAppDirectoryRefreshing = false)
        }
    }

    fun setShowSystemApps(show: Boolean) {
        if (_state.value.showSystemApps == show) return
        appListPreferences.setShowSystemApps(show)
        _state.value = _state.value.copy(showSystemApps = show)
        requestAppProjection()
    }

    fun setAppSortConfig(config: SmartCapsuleAppSortConfig) {
        if (_state.value.appSortConfig == config) return
        appListPreferences.setSortConfig(config)
        _state.value = _state.value.copy(appSortConfig = config)
        requestAppProjection()
    }

    fun activateAppPage() {
        if (
            !_state.value.snapshot.enabled ||
            _state.value.appPagePrepared ||
            appInitialRefreshPending ||
            appPermissionCheckInFlight ||
            _state.value.isAppDirectoryRefreshing
        ) {
            return
        }
        appInitialRefreshPending = true
        appPermissionCheckInFlight = true
        _state.value =
            _state.value.copy(
                isAppDirectoryRefreshing = true,
                appListPermissionRequired = false,
            )
        scope.launch {
            val permissionRequired =
                runCatching {
                    withContext(Dispatchers.IO) {
                        appDirectory.needsMiuiInstalledAppsPermission()
                    }
                }.getOrDefault(false)
            appPermissionCheckInFlight = false
            if (permissionRequired) {
                _state.value = _state.value.copy(appListPermissionRequired = true)
            } else {
                requestInitialAppReload()
            }
        }
    }

    fun continueAppPageAfterPermissionRequest() {
        if (_state.value.appPagePrepared) return
        if (!appInitialRefreshPending) appInitialRefreshPending = true
        _state.value =
            _state.value.copy(
                appListPermissionRequired = false,
                isAppDirectoryRefreshing = true,
            )
        requestInitialAppReload()
    }

    fun needsMiuiInstalledAppsPermission(): Boolean =
        appDirectory.needsMiuiInstalledAppsPermission()

    fun updateConfig(
        transform: (SmartCapsuleConfigSnapshot) -> SmartCapsuleConfigSnapshot,
    ) {
        val optimistic = transform(_state.value.snapshot)
        if (optimistic == _state.value.snapshot) return
        val updateGeneration = ++configUpdateGeneration
        pendingConfigMutations += 1
        _state.value =
            _state.value.copy(
                snapshot = optimistic,
                configAccepted = false,
                configSyncFailed = false,
            )
        requestAppProjection()
        SmartCapsuleConfigMutationQueue.submit(configStore, transform) { result ->
            pendingConfigMutations = (pendingConfigMutations - 1).coerceAtLeast(0)
            if (updateGeneration == configUpdateGeneration) {
                _state.value =
                    _state.value.copy(
                        snapshot = result.getOrNull() ?: _state.value.snapshot,
                        configSyncFailed = result.isFailure,
                    )
            }
            // Store.update persists before remote synchronization. Reloading resolves the durable
            // snapshot on both local-write failure (rollback) and remote-only failure (keep saved).
            if (pendingConfigMutations == 0) requestConfigReload()
        }
    }

    override fun close() {
        unregisterConfig()
        unregisterAcceptance()
        unregisterRuntime()
        scope.cancel()
    }

    private fun requestConfigReload() {
        configReloadRequests.trySend(Unit)
    }

    private fun requestRuntimeReload() {
        runtimeReloadRequests.trySend(Unit)
    }

    private fun requestAppProjection() {
        appProjectionGeneration += 1
        appProjectionRequests.trySend(Unit)
    }

    private fun requestInitialAppReload() {
        if (appReloadRequests.trySend(Unit).isSuccess) return
        appInitialRefreshPending = false
        completeInitialRefreshOnNextProjection = false
        _state.value =
            _state.value.copy(
                appPagePrepared = true,
                isAppDirectoryRefreshing = false,
                appListPermissionRequired = false,
            )
    }

    private suspend fun coalesce(channel: Channel<Unit>) {
        delay(RELOAD_COALESCE_MILLIS)
        while (channel.tryReceive().isSuccess) {
            // Drain a single SharedPreferences commit's per-key callbacks into one load.
        }
    }

    private companion object {
        const val RELOAD_COALESCE_MILLIS = 16L
    }
}
