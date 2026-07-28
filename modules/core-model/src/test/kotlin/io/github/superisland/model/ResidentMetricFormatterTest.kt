package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResidentMetricFormatterTest {
    @Test
    fun powerWattsFormatsPositiveNegativeZeroAndMissingInputs() {
        assertEquals("+6.00 W", ResidentMetricFormatter.powerWatts(1_500_000, 4_000))
        assertEquals("-6.00 W", ResidentMetricFormatter.powerWatts(-1_500_000, 4_000))
        assertEquals("0.00 W", ResidentMetricFormatter.powerWatts(0, 4_000))
        assertNull(ResidentMetricFormatter.powerWatts(null, 4_000))
        assertNull(ResidentMetricFormatter.powerWatts(1_500_000, null))
    }

    @Test
    fun microwattAndRawMetricEntryPointsUseTheSameWattContract() {
        assertEquals("+6.00 W", ResidentMetricFormatter.powerWattsFromMicrowatts(6_000_000L))
        assertEquals("-6.00 W", ResidentMetricFormatter.powerWattsFromMicrowatts(-6_000_000L))
        assertEquals("0.00 W", ResidentMetricFormatter.powerWattsFromMicrowatts(0L))
        assertNull(ResidentMetricFormatter.powerWattsFromMicrowatts(null))

        assertEquals(
            ResidentMetricFormatter.powerWattsFromMicrowatts(6_000_000L),
            ResidentMetricFormatter.powerWatts(1_500_000, 4_000),
        )
        assertEquals(
            ResidentMetricFormatter.powerWattsFromMicrowatts(-6_000_000L),
            ResidentMetricFormatter.powerWatts(-1_500_000, 4_000),
        )
    }

    @Test
    fun fanRpmAcceptsDocumentedBoundsAndRejectsMissingOrInvalidValues() {
        assertEquals("0 RPM", ResidentMetricFormatter.fanRpm(0))
        assertEquals("50000 RPM", ResidentMetricFormatter.fanRpm(50_000))
        assertNull(ResidentMetricFormatter.fanRpm(-1))
        assertNull(ResidentMetricFormatter.fanRpm(50_001))
        assertNull(ResidentMetricFormatter.fanRpm(null))
    }

    @Test
    fun fanRpmPreservesTheExactFiveDigitReadingAndUnitSpacing() {
        assertEquals("14487 RPM", ResidentMetricFormatter.fanRpm(14_487))
    }

    @Test
    fun currentFormattingIsSharedSignedAndSafeForIntMinimum() {
        assertEquals("+1500 mA", ResidentMetricFormatter.currentMicroAmps(1_500_999))
        assertEquals("-1500 mA", ResidentMetricFormatter.currentMicroAmps(-1_500_999))
        assertEquals("+999 µA", ResidentMetricFormatter.currentMicroAmps(999))
        assertEquals("-999 µA", ResidentMetricFormatter.currentMicroAmps(-999))
        assertEquals("0 µA", ResidentMetricFormatter.currentMicroAmps(0))
        assertEquals("-2147483 mA", ResidentMetricFormatter.currentMicroAmps(Int.MIN_VALUE))
        assertNull(ResidentMetricFormatter.currentMicroAmps(null))
    }

    @Test
    fun temperatureFormattingPreservesNegativeFractionalSign() {
        assertEquals("-0.5°C", ResidentMetricFormatter.temperatureTenthsCelsius(-5))
        assertEquals("0.0°C", ResidentMetricFormatter.temperatureTenthsCelsius(0))
        assertEquals("36.5°C", ResidentMetricFormatter.temperatureTenthsCelsius(365))
        assertNull(ResidentMetricFormatter.temperatureTenthsCelsius(null))
    }
}
