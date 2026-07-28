package io.github.superisland.source.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarsawFanMetricSourceTest {
    private val supportedAdapter =
        RootDeviceAdapterRegistry.capability(
            device = "warsaw",
            fingerprint = "Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys",
        )

    @Test
    fun parsesCompleteReadOnlyWarsawFanOutput() {
        val snapshot =
            WarsawFanOutputParser.parse(
                output =
                    """
                    real_speed=14487
                    target_level=2
                    pwm_duty=65
                    fan_support=1
                    """.trimIndent(),
                capturedAtMillis = 4L,
            )

        assertEquals(14_487, snapshot.rpm)
        assertEquals(2, snapshot.targetLevel)
        assertEquals(65, snapshot.pwmDutyPercent)
    }

    @Test
    fun rejectsUnsupportedOrMalformedRootOutput() {
        val unsupported =
            runCatching {
                WarsawFanOutputParser.parse(
                    "real_speed=0\ntarget_level=0\npwm_duty=0\nfan_support=0",
                    0L,
                )
            }
        val duplicate =
            runCatching {
                WarsawFanOutputParser.parse(
                    "real_speed=1\nreal_speed=2\ntarget_level=0\npwm_duty=0\nfan_support=1",
                    0L,
                )
            }

        assertTrue(unsupported.exceptionOrNull() is RootMetricReadException)
        assertTrue(duplicate.exceptionOrNull() is RootMetricReadException)
    }

    @Test
    fun sourceDoesNotExposeArbitraryRootCommands() {
        val source =
            WarsawFanMetricSource(
                nodeReader = WarsawFanNodeReader {
                    Result.success("real_speed=14363\ntarget_level=2\npwm_duty=65\nfan_support=1")
                },
                testOnly = Unit,
            )

        assertEquals(14_363, source.read(supportedAdapter, capturedAtMillis = 9L).getOrThrow().rpm)
    }

    @Test
    fun adapterRequiresWarsawAndKnownHyperOsFingerprintShape() {
        val wrongDevice =
            RootDeviceAdapterRegistry.capability(
                device = "other",
                fingerprint = "Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys",
            )
        val unknownBuild =
            RootDeviceAdapterRegistry.capability(
                device = "warsaw",
                fingerprint = "Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/custom:userdebug/test-keys",
            )

        assertTrue(supportedAdapter.isSupported)
        assertTrue(!wrongDevice.isSupported)
        assertTrue(!unknownBuild.isSupported)
    }

    @Test
    fun unsupportedAdapterDoesNotInvokeTheRootReader() {
        var invoked = false
        val source =
            WarsawFanMetricSource(
                nodeReader = WarsawFanNodeReader {
                    invoked = true
                    Result.success("real_speed=14363\ntarget_level=2\npwm_duty=65\nfan_support=1")
                },
                testOnly = Unit,
            )
        val unsupported = RootDeviceAdapterRegistry.capability("other", "not-a-fingerprint")

        assertTrue(source.read(unsupported, capturedAtMillis = 9L).isFailure)
        assertTrue(!invoked)
    }
}
