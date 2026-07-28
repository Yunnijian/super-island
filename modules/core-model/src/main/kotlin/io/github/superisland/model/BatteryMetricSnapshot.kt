package io.github.superisland.model

enum class BatteryChargeState {
    CHARGING,
    DISCHARGING,
    FULL,
    NOT_CHARGING,
    UNKNOWN,
}

data class BatteryMetricSnapshot(
    val levelPercent: Int?,
    val chargeState: BatteryChargeState,
    val currentMicroAmps: Int?,
    val voltageMillivolts: Int?,
    val temperatureTenthsCelsius: Int?,
    val capturedAtMillis: Long,
) {
    init {
        require(levelPercent == null || levelPercent in 0..100) {
            "levelPercent must be between 0 and 100"
        }
        require(voltageMillivolts == null || voltageMillivolts >= 0) {
            "voltageMillivolts must not be negative"
        }
        require(capturedAtMillis >= 0) { "capturedAtMillis must not be negative" }
    }

    val powerMilliwatts: Long?
        get() =
            powerMicrowatts?.div(MICROWATT_PER_MILLIWATT)

    /**
     * Exact-to-the-microwatt power estimate retained for low-current hardware states where
     * milliwatt integer division would otherwise misleadingly display zero.
     */
    val powerMicrowatts: Long?
        get() =
            currentMicroAmps?.let { current ->
                voltageMillivolts?.let { voltage ->
                    current.toLong() * voltage.toLong() / MICROAMPERE_MILLIVOLT_PER_MICROWATT
                }
            }

    private companion object {
        const val MICROAMPERE_MILLIVOLT_PER_MICROWATT = 1_000L
        const val MICROWATT_PER_MILLIWATT = 1_000L
    }
}
