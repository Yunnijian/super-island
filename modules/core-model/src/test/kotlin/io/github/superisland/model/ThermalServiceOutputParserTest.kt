package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalServiceOutputParserTest {
    @Test
    fun aggregatesOnlyTheCurrentHalTemperatureSection() {
        val snapshot =
            ThermalServiceOutputParser.parse(
                output =
                    """
                    Cached temperatures:
                      Temperature{mValue=92.0, mType=0, mName=CPU0, mStatus=0}
                    Current temperatures from HAL:
                      Temperature{mValue=32.8, mType=0, mName=CPU0, mStatus=0}
                      Temperature{mValue=31.0, mType=1, mName=GPU0, mStatus=0}
                      Temperature{mValue=30.9, mType=3, mName=skin, mStatus=0}
                      Temperature{mValue=29.4, mType=2, mName=battery, mStatus=0}
                    Current cooling devices from HAL:
                    """.trimIndent(),
                capturedAtMillis = 9L,
            )

        assertEquals(4, snapshot.sensorCount)
        assertEquals(32.8, snapshot.cpuMaxCelsius!!, 0.001)
        assertEquals(31.0, snapshot.gpuMaxCelsius!!, 0.001)
        assertEquals(30.9, snapshot.skinCelsius!!, 0.001)
        assertEquals(29.4, snapshot.batteryCelsius!!, 0.001)
    }

    @Test
    fun rejectsMissingOrDeniedThermalServiceOutput() {
        assertTrue(
            runCatching { ThermalServiceOutputParser.parse("Permission Denial", 0L) }.exceptionOrNull()
                is ThermalServiceDiagnosticException,
        )
        assertTrue(
            runCatching { ThermalServiceOutputParser.parse("HAL Ready: false", 0L) }.exceptionOrNull()
                is ThermalServiceDiagnosticException,
        )
    }
}
