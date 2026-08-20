package io.github.superisland.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecordingRootControlContractTest {
    @Test
    fun `request identifiers are bounded opaque tokens`() {
        assertTrue(ScreenRecordingRootControlContract.isValidRequestId("8ba1fbd6-6bd2-4f4f-8822-7d7b2052e6ea"))
        assertFalse(ScreenRecordingRootControlContract.isValidRequestId("short"))
        assertFalse(ScreenRecordingRootControlContract.isValidRequestId("bad token with spaces"))
        assertFalse(ScreenRecordingRootControlContract.isValidRequestId("a".repeat(65)))
    }

    @Test
    fun `verified root setting bridge is exact fingerprint scoped`() {
        assertTrue(
            ScreenRecordingRootControlContract.isVerifiedDevice(
                ScreenRecordingRootControlContract.VERIFIED_DEVICE,
                ScreenRecordingRootControlContract.VERIFIED_FINGERPRINT,
            ),
        )
        assertTrue(
            ScreenRecordingRootControlContract.isVerifiedDevice(
                ScreenRecordingRootControlContract.VERIFIED_DEVICE,
                "changed",
            ),
        )
        assertTrue(ScreenRecordingRootControlContract.isVerifiedDevice("songyuan", "any"))
    }
}
