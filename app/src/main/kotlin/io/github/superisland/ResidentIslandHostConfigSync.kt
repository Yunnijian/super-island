package io.github.superisland

import android.content.Context
import android.util.Log
import io.github.superisland.model.ResidentIslandIcon
import io.github.superisland.model.ResidentExpandedActionCodec
import io.github.superisland.model.ResidentMetricKey
import io.github.superisland.model.ResidentMonitorConfig
import io.github.superisland.publisher.focus.SystemUiResidentIslandContract
import io.github.superisland.publisher.focus.SystemUiResidentIslandPublisher

/**
 * Mirrors the user's resident-island settings into libxposed RemotePreferences.
 *
 * SystemUI cannot and must not read this app's private SharedPreferences directly. RemotePreferences
 * is the framework-supported, module-private configuration channel used by HyperIsland-style
 * SystemUI-owned features, and lets the host continue polling after the app process is gone.
 */
object ResidentIslandHostConfigSync {
    private const val TAG = "SuperIslandResidentConfig"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            appContext = context.applicationContext
            XposedRuntimeController.start()
            XposedRuntimeController.observe { status ->
                if (status.active) {
                    AppBackgroundWork.execute(::syncStoredAndReload)
                }
            }
            started = true
        }
    }

    fun sync(config: ResidentMonitorConfig): Result<Unit> =
        runCatching {
            val context = checkNotNull(appContext) { "Resident host config sync has not started" }
            val preferences =
                checkNotNull(
                    XposedRuntimeController.remotePreferences(
                        SystemUiResidentIslandContract.REMOTE_PREFERENCES,
                    ),
                ) { "LSPosed SystemUI scope is not active" }
            val normalized = config.normalized()
            check(
                preferences.edit()
                    .putInt(SystemUiResidentIslandContract.KEY_SCHEMA_VERSION, HOST_SCHEMA_VERSION)
                    .putBoolean(SystemUiResidentIslandContract.KEY_ENABLED, normalized.enabled)
                    .putString(SystemUiResidentIslandContract.KEY_LEFT_ICON, normalized.leftIcon.name)
                    .putString(SystemUiResidentIslandContract.KEY_RIGHT_ICON, normalized.rightIcon.name)
                    .putString(
                        SystemUiResidentIslandContract.KEY_LEFT_TITLE_METRIC,
                        normalized.leftTitleMetric.name,
                    ).putString(
                        SystemUiResidentIslandContract.KEY_RIGHT_TITLE_METRIC,
                        normalized.rightTitleMetric.name,
                    )
                    .putLong(
                        SystemUiResidentIslandContract.KEY_REFRESH_INTERVAL_MILLIS,
                        normalized.titleRefreshIntervalMillis,
                    ).putString(
                        SystemUiResidentIslandContract.KEY_EXPANDED_METRICS,
                        normalized.expandedMetrics.joinToString(",") { it.name },
                    ).putString(
                        SystemUiResidentIslandContract.KEY_EXPANDED_CONTENT_MODE,
                        normalized.expandedContentMode.name,
                    ).putString(
                        SystemUiResidentIslandContract.KEY_EXPANDED_CONTENT_TEMPLATE,
                        normalized.expandedContentTemplate,
                    ).putString(
                        SystemUiResidentIslandContract.KEY_EXPANDED_ACTIONS,
                        ResidentExpandedActionCodec.encode(normalized.expandedActions),
                    ).putInt(
                        SystemUiResidentIslandContract.KEY_APP_ICON_RES_ID,
                        normalized.leftTitleMetric.residentIconResId(),
                    )
                    // Compatibility mirror for a pre-update host that is still loaded in SystemUI.
                    .putString(SystemUiResidentIslandContract.KEY_TITLE_METRIC, normalized.leftTitleMetric.name)
                    .putString(
                        SystemUiResidentIslandContract.KEY_LEADING_KIND,
                        if (normalized.leftIcon == ResidentIslandIcon.NONE) {
                            "NONE"
                        } else {
                            "APP_ICON"
                        },
                    ).remove(SystemUiResidentIslandContract.KEY_LEADING_METRIC)
                    .remove(SystemUiResidentIslandContract.KEY_LEADING_STATIC_TEXT)
                    .putString(SystemUiResidentIslandContract.KEY_TRAILING_KIND, "METRIC_SHORT")
                    .putString(
                        SystemUiResidentIslandContract.KEY_TRAILING_METRIC,
                        normalized.rightTitleMetric.name,
                    ).remove(SystemUiResidentIslandContract.KEY_TRAILING_STATIC_TEXT)
                    .commit(),
            ) { "Could not persist resident-island settings to LSPosed" }
            SystemUiResidentIslandPublisher(context).reloadSettings().getOrThrow()
        }

    fun syncStoredAndReload() {
        val context = appContext ?: return
        sync(ResidentMonitorConfigStore(context).load()).onFailure { error ->
            Log.w(TAG, "Resident-island settings will retry when the LSPosed service reconnects", error)
        }
    }

    private fun ResidentMetricKey.residentIconResId(): Int =
        when (this) {
            ResidentMetricKey.BATTERY_PERCENT -> R.drawable.ic_resident_battery
            ResidentMetricKey.CHARGE_STATE -> R.drawable.ic_resident_charging
            ResidentMetricKey.CURRENT -> R.drawable.ic_resident_current
            ResidentMetricKey.POWER -> R.drawable.ic_resident_power
            ResidentMetricKey.BATTERY_TEMPERATURE,
            ResidentMetricKey.CPU_TEMPERATURE,
            ResidentMetricKey.GPU_TEMPERATURE,
            ResidentMetricKey.SKIN_TEMPERATURE,
            -> R.drawable.ic_resident_temperature
            ResidentMetricKey.FAN_RPM,
            ResidentMetricKey.FAN_LEVEL,
            -> R.drawable.ic_resident_fan
            ResidentMetricKey.VOLTAGE,
            ResidentMetricKey.CPU_FREQUENCY,
            ResidentMetricKey.GPU_FREQUENCY,
            ResidentMetricKey.CUSTOM_TEMPLATE,
            -> R.drawable.ic_resident_battery
        }

    private const val HOST_SCHEMA_VERSION = SystemUiResidentIslandContract.HOST_SCHEMA_VERSION
}
