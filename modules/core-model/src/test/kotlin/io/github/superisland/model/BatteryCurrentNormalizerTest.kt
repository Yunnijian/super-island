package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryCurrentNormalizerTest {
    @Test
    fun correctsInvertedVendorCurrentAgainstKnownChargeState() {
        assertEquals(
            2_500_000,
            BatteryCurrentNormalizer.normalize(-2_500_000, BatteryChargeState.CHARGING),
        )
        assertEquals(
            -420_000,
            BatteryCurrentNormalizer.normalize(420_000, BatteryChargeState.DISCHARGING),
        )
    }

    @Test
    fun preservesCorrectSignsAndUnknownStates() {
        assertEquals(
            2_500_000,
            BatteryCurrentNormalizer.normalize(2_500_000, BatteryChargeState.CHARGING),
        )
        assertEquals(
            -420_000,
            BatteryCurrentNormalizer.normalize(-420_000, BatteryChargeState.DISCHARGING),
        )
        assertEquals(
            -10,
            BatteryCurrentNormalizer.normalize(-10, BatteryChargeState.UNKNOWN),
        )
        assertNull(BatteryCurrentNormalizer.normalize(null, BatteryChargeState.FULL))
    }
}
