package io.github.superisland.source.screenrecord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecordingCompletionPolicyTest {
    @Test
    fun successfulOutputProducesIdleStateAndCanPublishAfterPersistence() {
        val state =
            ScreenRecordingCompletionPolicy.finishedState(
                recordingFailure = null,
                outputUri = OUTPUT_URI,
                rootRestoreFailure = null,
                successMessage = "录屏已保存",
                elapsedDurationMillis = 12_345L,
            )

        assertEquals(ScreenRecordingPhase.IDLE, state.phase)
        assertEquals("录屏已保存", state.message)
        assertEquals(OUTPUT_URI, state.outputUri)
        assertEquals(12_345L, state.elapsedDurationMillis)
        assertTrue(
            ScreenRecordingCompletionPolicy.shouldPublish(
                recordingFailure = null,
                outputUri = state.outputUri,
                statePersisted = true,
            ),
        )
    }

    @Test
    fun persistenceFailureSuppressesCompletionCard() {
        assertFalse(
            ScreenRecordingCompletionPolicy.shouldPublish(
                recordingFailure = null,
                outputUri = OUTPUT_URI,
                statePersisted = false,
            ),
        )
    }

    @Test
    fun rootRestoreFailureKeepsTheSameOutputAvailable() {
        val state =
            ScreenRecordingCompletionPolicy.finishedState(
                recordingFailure = null,
                outputUri = OUTPUT_URI,
                rootRestoreFailure = IllegalStateException("restore failed"),
                successMessage = "录屏已保存",
                elapsedDurationMillis = 9_876L,
            )

        assertEquals(ScreenRecordingPhase.ERROR, state.phase)
        assertEquals("录屏已保存，但系统设置恢复失败", state.message)
        assertEquals(OUTPUT_URI, state.outputUri)
        assertTrue(
            ScreenRecordingCompletionPolicy.shouldPublish(
                recordingFailure = null,
                outputUri = state.outputUri,
                statePersisted = true,
            ),
        )
    }

    @Test
    fun recordingFailureClearsOutputAndSuppressesCompletionCard() {
        val failure = IllegalStateException("muxer failed")
        val state =
            ScreenRecordingCompletionPolicy.finishedState(
                recordingFailure = failure,
                outputUri = OUTPUT_URI,
                rootRestoreFailure = null,
                successMessage = "录屏已保存",
                elapsedDurationMillis = 4_321L,
            )

        assertEquals(ScreenRecordingPhase.ERROR, state.phase)
        assertEquals("录屏失败：muxer failed", state.message)
        assertNull(state.outputUri)
        assertFalse(
            ScreenRecordingCompletionPolicy.shouldPublish(
                recordingFailure = failure,
                outputUri = OUTPUT_URI,
                statePersisted = true,
            ),
        )
    }

    @Test
    fun missingOrInvalidOutputCannotProduceAnActionableCompletion() {
        val invalidOutputUris =
            listOf(null, "", "file:///sdcard/recording.mp4", "content:///recording.mp4")
        invalidOutputUris.forEach { outputUri ->
            val state =
                ScreenRecordingCompletionPolicy.finishedState(
                    recordingFailure = null,
                    outputUri = outputUri,
                    rootRestoreFailure = null,
                    successMessage = "录屏已保存",
                    elapsedDurationMillis = -1L,
                )

            assertEquals(ScreenRecordingPhase.ERROR, state.phase)
            assertEquals("录屏失败，未保留不完整文件", state.message)
            assertNull(state.outputUri)
            assertEquals(0L, state.elapsedDurationMillis)
            assertFalse(
                ScreenRecordingCompletionPolicy.shouldPublish(
                    recordingFailure = null,
                    outputUri = outputUri,
                    statePersisted = true,
                ),
            )
        }
    }

    private companion object {
        const val OUTPUT_URI = "content://media/external/video/media/42"
    }
}
