package io.github.superisland.model

import org.junit.Assert.assertThrows
import org.junit.Test

class ThermalDiagnosticSnapshotTest {
    @Test
    fun rejectsInvalidThermalValues() {
        assertThrows(IllegalArgumentException::class.java) {
            ThermalDiagnosticSnapshot(
                sensorCount = 1,
                cpuMaxCelsius = 1_001.0,
                gpuMaxCelsius = null,
                skinCelsius = null,
                batteryCelsius = null,
                capturedAtMillis = 0L,
            )
        }
    }
}
