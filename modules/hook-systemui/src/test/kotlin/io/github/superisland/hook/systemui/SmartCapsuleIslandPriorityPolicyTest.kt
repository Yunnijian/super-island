package io.github.superisland.hook.systemui

import io.github.superisland.model.IslandPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartCapsuleIslandPriorityPolicyTest {
    @Test
    fun exactWarsawFingerprintPreservesEveryConfiguredPriority() {
        IslandPriority.entries.forEach { configured ->
            assertEquals(
                configured,
                SmartCapsuleIslandPriorityPolicy.effective(
                    configured = configured,
                    fingerprint = SmartCapsuleIslandPriorityPolicy.SUPPORTED_FINGERPRINT,
                ),
            )
        }
    }

    @Test
    fun everyUnknownOrNearMatchFingerprintDowngradesToLow() {
        listOf(
            "",
            "unknown",
            SmartCapsuleIslandPriorityPolicy.SUPPORTED_FINGERPRINT + ".1",
            SmartCapsuleIslandPriorityPolicy.SUPPORTED_FINGERPRINT.replace(
                ":user/release-keys",
                ":userdebug/test-keys",
            ),
        ).forEach { fingerprint ->
            assertEquals(
                IslandPriority.HIGH,
                SmartCapsuleIslandPriorityPolicy.effective(
                    configured = IslandPriority.HIGH,
                    fingerprint = fingerprint,
                ),
            )
        }
    }

    @Test
    fun lowRemainsLowOnEveryFingerprint() {
        assertEquals(
            IslandPriority.LOW,
            SmartCapsuleIslandPriorityPolicy.effective(IslandPriority.LOW, "unknown"),
        )
        assertEquals(
            IslandPriority.LOW,
            SmartCapsuleIslandPriorityPolicy.effective(
                IslandPriority.LOW,
                SmartCapsuleIslandPriorityPolicy.SUPPORTED_FINGERPRINT,
            ),
        )
    }
}
