package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecordingConfigTest {
    @Test
    fun `resolution preserves orientation and encoder-safe even dimensions`() {
        assertEquals(1080 to 2340, ScreenRecordingResolution.FHD.resolveDimensions(1080, 2340))
        assertEquals(1080 to 1920, ScreenRecordingResolution.FHD.resolveDimensions(1440, 2560))
        assertEquals(1920 to 1080, ScreenRecordingResolution.FHD.resolveDimensions(2560, 1440))
        assertEquals(1080 to 2340, ScreenRecordingResolution.NATIVE.resolveDimensions(1081, 2341))
    }

    @Test
    fun `orientation chooses the requested dimension ordering`() {
        assertEquals(1080 to 2400, ScreenRecordingOrientation.PORTRAIT.applyTo(2400, 1080))
        assertEquals(2400 to 1080, ScreenRecordingOrientation.LANDSCAPE.applyTo(1080, 2400))
        assertEquals(1080 to 2400, ScreenRecordingOrientation.AUTO.applyTo(1080, 2400))
    }

    @Test
    fun `automatic bitrate and frame rate are bounded`() {
        assertEquals(1_000_000, ScreenRecordingBitrate.AUTO.resolveBitsPerSecond(360, 640, 15))
        assertEquals(100_000_000, ScreenRecordingBitrate.AUTO.resolveBitsPerSecond(4320, 7680, 120))
        assertEquals(60, ScreenRecordingFrameRate.AUTO.resolveFramesPerSecond(60))
        assertEquals(120, ScreenRecordingFrameRate.AUTO.resolveFramesPerSecond(240))
        assertEquals(15, ScreenRecordingFrameRate.AUTO.resolveFramesPerSecond(1))
    }

    @Test
    fun `unknown wire values fail closed`() {
        assertNull(ScreenRecordingResolution.fromWire("4k"))
        assertNull(ScreenRecordingVideoCodec.fromWire("av1"))
        assertNull(ScreenRecordingAudioSource.fromWire("line_in"))
    }

    @Test
    fun `storage uri accepts only bounded content uris`() {
        assertTrue(ScreenRecordingConfig.isSafeStorageTreeUri(""))
        assertTrue(ScreenRecordingConfig.isSafeStorageTreeUri("content://com.android.externalstorage.documents/tree/primary%3AMovies"))
        assertFalse(ScreenRecordingConfig.isSafeStorageTreeUri("file:///storage/emulated/0/Movies"))
        assertFalse(ScreenRecordingConfig.isSafeStorageTreeUri("content://bad uri"))
    }

    @Test
    fun `normalization removes malformed storage uri and keeps opt-in protection disabled`() {
        val config =
            ScreenRecordingConfig(
                schemaVersion = 99,
                storageTreeUri = "file:///not-allowed",
            ).normalized()

        assertEquals(SCREEN_RECORDING_CONFIG_SCHEMA_VERSION, config.schemaVersion)
        assertEquals("", config.storageTreeUri)
        assertFalse(config.bypassScreenShareProtection)
        assertTrue(config.confirmBeforeStart)
        assertFalse(config.projectMediaEnabled)
    }

    @Test
    fun `schema v2 defaults prefer confirm dialog and project media off`() {
        val defaults = ScreenRecordingConfig()
        assertEquals(2, SCREEN_RECORDING_CONFIG_SCHEMA_VERSION)
        assertTrue(defaults.confirmBeforeStart)
        assertFalse(defaults.projectMediaEnabled)
    }
}
