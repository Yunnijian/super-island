package io.github.superisland.model

import org.junit.Assert.assertThrows
import org.junit.Test

class WarsawPerformanceMetricSnapshotTest {
    @Test
    fun rejectsFrequenciesOutsideTheValidatedMobileRange() {
        assertThrows(IllegalArgumentException::class.java) {
            WarsawPerformanceMetricSnapshot(
                cpuPolicy0Mhz = 0,
                cpuPolicy6Mhz = 3_514,
                gpuMhz = 222,
                capturedAtMillis = 1L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WarsawPerformanceMetricSnapshot(
                cpuPolicy0Mhz = 749,
                cpuPolicy6Mhz = 3_514,
                gpuMhz = 3_001,
                capturedAtMillis = 1L,
            )
        }
    }
}
