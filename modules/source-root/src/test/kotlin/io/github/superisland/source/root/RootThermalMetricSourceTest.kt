package io.github.superisland.source.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootThermalMetricSourceTest {
    @Test
    fun usesOnlyItsFixedRootThermalDiagnosticAndReturnsAggregates() {
        val source =
            RootThermalMetricSource(
                outputReader =
                    RootThermalOutputReader {
                        Result.success(
                            """
                            Current temperatures from HAL:
                            Temperature{mValue=38.2, mType=0, mName=CPU0, mStatus=0}
                            Temperature{mValue=35.1, mType=1, mName=GPU0, mStatus=0}
                            Current cooling devices from HAL:
                            """.trimIndent(),
                        )
                    },
                testOnly = Unit,
            )

        val snapshot = source.read(capturedAtMillis = 7L).getOrThrow()

        assertEquals(2, snapshot.sensorCount)
        assertEquals(38.2, snapshot.cpuMaxCelsius!!, 0.001)
        assertEquals(35.1, snapshot.gpuMaxCelsius!!, 0.001)
    }

    @Test
    fun convertsMalformedOutputToAnUnavailableRootRead() {
        val source =
            RootThermalMetricSource(
                outputReader = RootThermalOutputReader { Result.success("not thermalservice") },
                testOnly = Unit,
            )

        assertTrue(source.read(capturedAtMillis = 7L).exceptionOrNull() is RootMetricReadException)
    }
}
