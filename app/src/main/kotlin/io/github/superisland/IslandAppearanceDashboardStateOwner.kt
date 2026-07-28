package io.github.superisland

import android.content.Context
import io.github.superisland.model.IslandAppearanceConfig
import io.github.superisland.model.SystemUiIslandAppearanceContract
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

internal data class IslandAppearanceDashboardState(
    val config: IslandAppearanceConfig = IslandAppearanceConfig(),
    val loaded: Boolean = false,
)

/** Root-level owner shared by Miuix and Material while the navigation entry is retained. */
internal class IslandAppearanceDashboardStateOwner(context: Context) : AutoCloseable {
    private val store = IslandAppearanceStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val saveRequests = Channel<SaveRequest>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(IslandAppearanceDashboardState())
    val state: StateFlow<IslandAppearanceDashboardState> = _state.asStateFlow()

    private var generation = 0L
    private var pendingWrites = 0
    private var persisted = IslandAppearanceConfig()
    private val unregister =
        store.observe { observed ->
            persisted = observed
            if (pendingWrites == 0) {
                _state.value = IslandAppearanceDashboardState(observed, loaded = true)
            }
        }

    init {
        scope.launch {
            for (request in saveRequests) persist(request)
        }
    }

    fun setColorOsFluidCloudStyleEnabled(enabled: Boolean) {
        if (enabled && !SystemUiIslandAppearanceContract.COLOR_OS_FLUID_CLOUD_RUNTIME_AVAILABLE) {
            return
        }
        val current = _state.value.config
        if (current.capsule.colorOsFluidCloudStyleEnabled == enabled) return
        generation += 1L
        pendingWrites += 1
        val updated =
            current.copy(
                capsule = current.capsule.copy(colorOsFluidCloudStyleEnabled = enabled),
            )
        _state.value = IslandAppearanceDashboardState(updated, loaded = true)
        check(saveRequests.trySend(SaveRequest(generation, updated)).isSuccess) {
            "Island appearance settings writer is unavailable"
        }
    }

    private suspend fun persist(request: SaveRequest) {
        val result =
            withContext(Dispatchers.IO) {
                store.save(request.config).onSuccess {
                    IslandAppearanceConfigSync.sync(request.config)
                }
            }
        pendingWrites = (pendingWrites - 1).coerceAtLeast(0)
        if (result.isSuccess) {
            persisted = request.config
        } else if (request.generation == generation) {
            _state.value = IslandAppearanceDashboardState(persisted, loaded = true)
        }
    }

    override fun close() {
        unregister()
        scope.cancel()
    }

    private data class SaveRequest(
        val generation: Long,
        val config: IslandAppearanceConfig,
    )
}
