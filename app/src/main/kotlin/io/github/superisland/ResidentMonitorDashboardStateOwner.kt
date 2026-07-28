package io.github.superisland

import android.content.Context
import io.github.superisland.model.BatteryMetricSnapshot
import io.github.superisland.model.ResidentMetricKey
import io.github.superisland.model.ResidentMonitorConfig
import io.github.superisland.source.system.BatteryMetricSource
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
import java.util.concurrent.atomic.AtomicLong

internal data class ResidentMonitorDashboardState(
    val config: ResidentMonitorConfig = ResidentMonitorUiWarmCache.config() ?: ResidentMonitorConfig(),
    val battery: BatteryMetricSnapshot =
        BatteryMonitorRuntime.current().latestSnapshot
            ?: ResidentMonitorUiWarmCache.snapshot()
            ?: EMPTY_BATTERY_METRIC_SNAPSHOT,
    val runtime: BatteryMonitorRuntimeState = BatteryMonitorRuntime.current(),
    val fanAvailable: Boolean = false,
    val fanPreviewRpm: Int? = null,
    val loaded: Boolean = false,
    val isLoading: Boolean = false,
) {
    val monitoringActive: Boolean
        get() = runtime.running || config.enabled
}

/** Process-root owner shared by the resident configuration and expanded-content destinations. */
internal class ResidentMonitorDashboardStateOwner(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val configStore = ResidentMonitorConfigStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val reloadRequests = Channel<Unit>(Channel.CONFLATED)
    private val configGeneration = AtomicLong(0L)
    private val _state = MutableStateFlow(ResidentMonitorDashboardState())
    val state: StateFlow<ResidentMonitorDashboardState> = _state.asStateFlow()

    private val unregisterConfig =
        configStore.observeInvalidations {
            configGeneration.incrementAndGet()
            reloadRequests.trySend(Unit)
        }
    private val unregisterRuntime =
        BatteryMonitorRuntime.observe { runtime ->
            _state.value =
                _state.value.copy(
                    runtime = runtime,
                    battery = runtime.latestSnapshot ?: _state.value.battery,
                )
        }

    init {
        scope.launch {
            for (ignored in reloadRequests) {
                val requestedGeneration = configGeneration.get()
                val config =
                    runCatching { withContext(Dispatchers.IO) { configStore.load() } }
                        .getOrNull() ?: continue
                if (configGeneration.get() != requestedGeneration) continue
                ResidentMonitorUiWarmCache.updateConfig(config)
                _state.value = _state.value.copy(config = config)
            }
        }
    }

    fun activate() {
        if (_state.value.loaded || _state.value.isLoading) return
        val requestedGeneration = configGeneration.get()
        _state.value = _state.value.copy(isLoading = true)
        scope.launch {
            val loaded =
                runCatching {
                    withContext(Dispatchers.IO) {
                        val warm = ResidentMonitorUiWarmCache.prewarm(appContext)
                        val battery =
                            if (System.currentTimeMillis() - warm.battery.capturedAtMillis > BATTERY_CACHE_MAX_AGE_MILLIS) {
                                BatteryMetricSource(appContext).read()
                            } else {
                                warm.battery
                            }
                        val fanAvailable = supportsMiuiFanTitle()
                        val fanRpm = if (fanAvailable) readMiuiFanRpm() else null
                        val config = warm.config.withoutUnavailableFanTitle(fanAvailable)
                        LoadedState(config, battery, fanAvailable, fanRpm)
                    }
            }
            loaded.onSuccess { result ->
                val current = _state.value
                val resolvedConfig =
                    result.config.takeIf {
                        configGeneration.get() == requestedGeneration
                    } ?: current.config
                ResidentMonitorUiWarmCache.updateConfig(resolvedConfig)
                ResidentMonitorUiWarmCache.updateSnapshot(result.battery)
                _state.value =
                    current.copy(
                        // A user save or repository invalidation that happened while the slow
                        // probe was running owns the newer configuration. The probe may still
                        // publish telemetry/capability, but must never restore its stale snapshot.
                        config = resolvedConfig,
                        battery = result.battery,
                        fanAvailable = result.fanAvailable,
                        fanPreviewRpm = result.fanPreviewRpm,
                        loaded = true,
                        isLoading = false,
                    )
            }.onFailure {
                _state.value = _state.value.copy(loaded = true, isLoading = false)
            }
        }
    }

    fun refreshBattery() {
        scope.launch {
            val refreshed =
                runCatching {
                    withContext(Dispatchers.IO) {
                        BatteryMetricSource(appContext).read()
                    }
                }.getOrNull() ?: return@launch
            ResidentMonitorUiWarmCache.updateSnapshot(refreshed)
            _state.value = _state.value.copy(battery = refreshed)
        }
    }

    /** Explicit editor saves retain their existing applied/waiting result contract. */
    fun saveConfig(config: ResidentMonitorConfig): Boolean {
        val normalized = config.normalized()
        if (normalized == _state.value.config) return true
        configGeneration.incrementAndGet()
        _state.value = _state.value.copy(config = normalized)
        ResidentMonitorUiWarmCache.updateConfig(normalized)
        val applied = configStore.save(normalized).isSuccess
        BatteryMonitorService.onResidentMonitorConfigurationChanged(normalized)
        return applied
    }

    fun setFeatureEnabled(enabled: Boolean) {
        val updated = _state.value.config.copy(enabled = enabled).normalized()
        saveConfig(updated)
        if (enabled) {
            BatteryMonitorService.start(appContext)
        } else {
            BatteryMonitorService.stop(appContext)
        }
    }

    override fun close() {
        unregisterConfig()
        unregisterRuntime()
        scope.cancel()
    }

    private data class LoadedState(
        val config: ResidentMonitorConfig,
        val battery: BatteryMetricSnapshot,
        val fanAvailable: Boolean,
        val fanPreviewRpm: Int?,
    )

    private companion object {
        const val BATTERY_CACHE_MAX_AGE_MILLIS = 5_000L
    }
}

private fun ResidentMonitorConfig.withoutUnavailableFanTitle(fanAvailable: Boolean): ResidentMonitorConfig {
    if (fanAvailable) return this
    if (leftTitleMetric != ResidentMetricKey.FAN_RPM && rightTitleMetric != ResidentMetricKey.FAN_RPM) {
        return this
    }
    return copy(
        leftTitleMetric =
            leftTitleMetric.takeUnless { it == ResidentMetricKey.FAN_RPM }
                ?: ResidentMetricKey.BATTERY_PERCENT,
        rightTitleMetric =
            rightTitleMetric.takeUnless { it == ResidentMetricKey.FAN_RPM }
                ?: ResidentMetricKey.BATTERY_PERCENT,
    )
}
