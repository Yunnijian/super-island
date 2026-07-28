package io.github.superisland.model

import java.util.Locale

/** Shared, Android-free formatting rules used by both the App and the injected SystemUI host. */
object ResidentMetricFormatter {
    private const val MAX_FAN_RPM = 50_000

    @JvmStatic
    fun powerWatts(
        currentMicroAmps: Int?,
        voltageMillivolts: Int?,
    ): String? {
        if (currentMicroAmps == null || voltageMillivolts == null) return null
        val microWatts = currentMicroAmps.toLong() * voltageMillivolts / 1_000L
        return powerWattsFromMicrowatts(microWatts)
    }

    @JvmStatic
    fun powerWattsFromMicrowatts(microWatts: Long?): String? {
        if (microWatts == null) return null
        if (microWatts == 0L) return "0.00 W"
        return String.format(Locale.ROOT, "%+.2f W", microWatts / 1_000_000.0)
    }

    @JvmStatic
    fun fanRpm(rpm: Int?): String? =
        rpm?.takeIf { it in 0..MAX_FAN_RPM }?.let { "$it RPM" }

    @JvmStatic
    fun currentMicroAmps(currentMicroAmps: Int?): String? =
        currentMicroAmps?.let { current ->
            val absolute = kotlin.math.abs(current.toLong())
            val sign = when {
                current > 0 -> "+"
                current < 0 -> "-"
                else -> ""
            }
            if (absolute >= 1_000L) {
                "$sign${absolute / 1_000L} mA"
            } else {
                "$sign$absolute µA"
            }
        }

    @JvmStatic
    fun temperatureTenthsCelsius(temperatureTenthsCelsius: Int?): String? =
        temperatureTenthsCelsius?.let { temperature ->
            String.format(Locale.ROOT, "%.1f°C", temperature / 10.0)
        }
}
