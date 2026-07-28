package io.github.superisland.source.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootDeviceAdapterRegistryTest {
    @Test
    fun warsawCapabilityDeclaresOnlyItsValidatedTelemetryFeatures() {
        val capability =
            RootDeviceAdapterRegistry.capability(
                device = "warsaw",
                fingerprint = "Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys",
            )

        assertEquals("warsaw", capability.adapterId)
        assertTrue(capability.supports(RootDeviceFeature.FAN_TELEMETRY))
        assertTrue(capability.supports(RootDeviceFeature.PERFORMANCE_TELEMETRY))
    }

    @Test
    fun unknownDeviceDeclaresNoRootHardwareFeatures() {
        val capability = RootDeviceAdapterRegistry.capability("other", "not-a-fingerprint")

        assertFalse(capability.isSupported)
        assertFalse(capability.supports(RootDeviceFeature.FAN_TELEMETRY))
        assertFalse(capability.supports(RootDeviceFeature.PERFORMANCE_TELEMETRY))
    }
}
