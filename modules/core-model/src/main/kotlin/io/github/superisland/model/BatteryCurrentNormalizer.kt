package io.github.superisland.model

/**
 * Normalizes vendor battery gauges to Android's public convention: positive while charging and
 * negative while discharging. Some HyperOS kernels expose the hardware sign in the opposite
 * direction even though [android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW] documents the
 * public convention.
 */
object BatteryCurrentNormalizer {
    fun normalize(
        currentMicroAmps: Int?,
        chargeState: BatteryChargeState,
    ): Int? =
        currentMicroAmps?.let { current ->
            when (chargeState) {
                BatteryChargeState.CHARGING,
                BatteryChargeState.FULL,
                -> current.takeUnless { it < 0 } ?: -current
                BatteryChargeState.DISCHARGING -> current.takeUnless { it > 0 } ?: -current
                BatteryChargeState.NOT_CHARGING,
                BatteryChargeState.UNKNOWN,
                -> current
            }
        }
}
