package io.github.superisland.source.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarsawPerformanceMetricSourceTest {
    private val supportedAdapter =
        RootDeviceAdapterRegistry.capability(
            device = "warsaw",
            fingerprint = "Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys",
        )

    @Test
    fun parsesOnlyTheThreeFixedWarsawFrequencyNodes() {
        val source =
            WarsawPerformanceMetricSource(
                outputReader =
                    WarsawPerformanceOutputReader {
                        Result.success("cpu_policy0_khz=748800\ncpu_policy6_khz=3513600\ngpu_hz=222000000")
                    },
                testOnly = Unit,
            )

        val snapshot = source.read(supportedAdapter, capturedAtMillis = 8L).getOrThrow()

        assertEquals(749, snapshot.cpuPolicy0Mhz)
        assertEquals(3_514, snapshot.cpuPolicy6Mhz)
        assertEquals(222, snapshot.gpuMhz)
    }

    @Test
    fun unsupportedAdapterDoesNotRequestTheRootReader() {
        var invoked = false
        val source =
            WarsawPerformanceMetricSource(
                outputReader = WarsawPerformanceOutputReader {
                    invoked = true
                    Result.success("cpu_policy0_khz=748800\ncpu_policy6_khz=3513600\ngpu_hz=222000000")
                },
                testOnly = Unit,
            )

        assertTrue(source.read(RootDeviceAdapterRegistry.capability("other", "not-a-fingerprint")).isFailure)
        assertTrue(!invoked)
    }

    @Test
    fun rejectsMissingOrOutOfRangeFrequencyOutput() {
        val missing = runCatching { WarsawPerformanceOutputParser.parse("cpu_policy0_khz=748800", 0L) }
        val invalid =
            runCatching {
                WarsawPerformanceOutputParser.parse(
                    "cpu_policy0_khz=748800\ncpu_policy6_khz=3513600\ngpu_hz=3001000000",
                    0L,
                )
            }

        assertTrue(missing.exceptionOrNull() is RootMetricReadException)
        assertTrue(invalid.exceptionOrNull() is RootMetricReadException)
    }
}
