package io.github.superisland.model

import org.junit.Assert.assertThrows
import org.junit.Test

class WarsawFanMetricSnapshotTest {
    @Test
    fun rejectsOutOfRangeFanValues() {
        assertThrows(IllegalArgumentException::class.java) {
            WarsawFanMetricSnapshot(
                rpm = 50_001,
                targetLevel = 2,
                pwmDutyPercent = 65,
                capturedAtMillis = 1L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WarsawFanMetricSnapshot(
                rpm = 14_487,
                targetLevel = 2,
                pwmDutyPercent = 101,
                capturedAtMillis = 1L,
            )
        }
    }
}
