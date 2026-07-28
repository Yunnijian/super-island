package io.github.superisland

import android.content.Context
import io.github.superisland.model.BatteryMetricSnapshot
import io.github.superisland.model.ResidentMonitorConfig
import io.github.superisland.source.system.BatteryMetricSource
import java.util.concurrent.FutureTask

/** Process cache populated before a user opens the resident-island settings for the first time. */
internal object ResidentMonitorUiWarmCache {
    data class Snapshot(
        val config: ResidentMonitorConfig,
        val battery: BatteryMetricSnapshot,
    )

    private val lock = Any()

    @Volatile
    private var residentConfig: ResidentMonitorConfig? = null

    @Volatile
    private var batterySnapshot: BatteryMetricSnapshot? = null

    private var inFlight: FutureTask<Snapshot>? = null

    /** Must be called away from the main thread. Concurrent callers share one in-flight read. */
    fun prewarm(context: Context): Snapshot {
        val appContext = context.applicationContext
        synchronized(lock) {
            residentConfig?.let { config ->
                batterySnapshot?.let { battery -> return Snapshot(config, battery) }
            }
        }
        val task =
            synchronized(lock) {
                inFlight
                    ?: FutureTask {
                        Snapshot(
                            config = ResidentMonitorConfigStore(appContext).load(),
                            battery = BatteryMetricSource(appContext).read(),
                        )
                    }.also { inFlight = it }
            }
        task.run()
        return try {
            task.get().also { loaded ->
                synchronized(lock) {
                    residentConfig = loaded.config
                    batterySnapshot = loaded.battery
                }
            }
        } finally {
            synchronized(lock) {
                if (inFlight === task && task.isDone) inFlight = null
            }
        }
    }

    fun config(): ResidentMonitorConfig? = residentConfig

    fun snapshot(): BatteryMetricSnapshot? = batterySnapshot

    fun updateConfig(config: ResidentMonitorConfig) {
        residentConfig = config
    }

    fun updateSnapshot(snapshot: BatteryMetricSnapshot) {
        batterySnapshot = snapshot
    }
}
