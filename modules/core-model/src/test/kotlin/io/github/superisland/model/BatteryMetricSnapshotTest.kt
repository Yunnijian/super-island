package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryMetricSnapshotTest {
    @Test
    fun powerUsesMicroampereAndMillivoltUnits() {
        val snapshot =
            BatteryMetricSnapshot(
                levelPercent = 80,
                chargeState = BatteryChargeState.CHARGING,
                currentMicroAmps = 1_500_000,
                voltageMillivolts = 4_000,
                temperatureTenthsCelsius = 320,
                capturedAtMillis = 1L,
            )

        assertEquals(6_000L, snapshot.powerMilliwatts)
        assertEquals(6_000_000L, snapshot.powerMicrowatts)
    }

    @Test
    fun lowCurrentPowerRetainsMicrowattPrecision() {
        val snapshot =
            BatteryMetricSnapshot(
                levelPercent = 80,
                chargeState = BatteryChargeState.CHARGING,
                currentMicroAmps = 8,
                voltageMillivolts = 4_408,
                temperatureTenthsCelsius = 320,
                capturedAtMillis = 1L,
            )

        assertEquals(35L, snapshot.powerMicrowatts)
        assertEquals(0L, snapshot.powerMilliwatts)
    }

    @Test
    fun unavailableCurrentOrVoltageKeepsPowerUnavailable() {
        val snapshot =
            BatteryMetricSnapshot(
                levelPercent = null,
                chargeState = BatteryChargeState.UNKNOWN,
                currentMicroAmps = null,
                voltageMillivolts = 4_000,
                temperatureTenthsCelsius = null,
                capturedAtMillis = 0L,
            )

        assertNull(snapshot.powerMilliwatts)
        assertNull(snapshot.powerMicrowatts)
    }
}
