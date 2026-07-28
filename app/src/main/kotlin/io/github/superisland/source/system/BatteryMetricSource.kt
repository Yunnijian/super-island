package io.github.superisland.source.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.github.superisland.model.BatteryCurrentNormalizer
import io.github.superisland.model.BatteryChargeState
import io.github.superisland.model.BatteryMetricSnapshot

class BatteryMetricSource(context: Context) {
    private val appContext = context.applicationContext
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)

    fun read(): BatteryMetricSnapshot {
        val intent =
            appContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val chargeState = intent?.chargeState() ?: BatteryChargeState.UNKNOWN
        val rawCurrentMicroAmps =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                .takeUnless { it == Int.MIN_VALUE }
        val currentMicroAmps = BatteryCurrentNormalizer.normalize(rawCurrentMicroAmps, chargeState)

        return BatteryMetricSnapshot(
            levelPercent =
                if (level >= 0 && scale > 0) {
                    (level * 100f / scale).toInt().coerceIn(0, 100)
                } else {
                    null
                },
            chargeState = chargeState,
            currentMicroAmps = currentMicroAmps,
            voltageMillivolts = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it >= 0 },
            temperatureTenthsCelsius =
                intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                    ?.takeUnless { it == Int.MIN_VALUE },
            capturedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun Intent.chargeState(): BatteryChargeState =
        when (getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryChargeState.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryChargeState.DISCHARGING
            BatteryManager.BATTERY_STATUS_FULL -> BatteryChargeState.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryChargeState.NOT_CHARGING
            else -> BatteryChargeState.UNKNOWN
        }
}
