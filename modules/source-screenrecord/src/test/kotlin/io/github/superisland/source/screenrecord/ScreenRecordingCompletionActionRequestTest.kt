package io.github.superisland.source.screenrecord

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecordingCompletionActionRequestTest {
    private val outputUri = "content://media/external/video/media/42"

    @Test
    fun allowsOnlyCurrentContentOutputForKnownActions() {
        assertTrue(
            ScreenRecordingCompletionActionRequest.isAllowed(
                ScreenRecordingCompletionActionRequest.ACTION_VIEW,
                outputUri,
                outputUri,
            ),
        )
        assertTrue(
            ScreenRecordingCompletionActionRequest.isAllowed(
                ScreenRecordingCompletionActionRequest.ACTION_SHARE,
                outputUri,
                outputUri,
            ),
        )
    }

    @Test
    fun rejectsUnknownStaleAndNonContentRequests() {
        assertFalse(
            ScreenRecordingCompletionActionRequest.isAllowed(
                "io.github.superisland.action.DELETE_SCREEN_RECORDING",
                outputUri,
                outputUri,
            ),
        )
        assertFalse(
            ScreenRecordingCompletionActionRequest.isAllowed(
                ScreenRecordingCompletionActionRequest.ACTION_VIEW,
                "content://media/external/video/media/41",
                outputUri,
            ),
        )
        assertFalse(
            ScreenRecordingCompletionActionRequest.isAllowed(
                ScreenRecordingCompletionActionRequest.ACTION_SHARE,
                "file:///sdcard/Movies/recording.mp4",
                "file:///sdcard/Movies/recording.mp4",
            ),
        )
    }
}
