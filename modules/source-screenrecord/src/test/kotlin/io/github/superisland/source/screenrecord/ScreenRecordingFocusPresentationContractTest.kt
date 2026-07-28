package io.github.superisland.source.screenrecord

import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecordingFocusPresentationContractTest {
    @Test
    fun pauseResumeChangesEncodedOutputInsteadOfOnlyFreezingTheLabel() {
        val encoder =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/" +
                    "ScreenRecordingEncoder.kt",
            ).readText()

        assertTrue("Encoder must expose a real paused state", "PAUSED" in encoder)
        assertTrue("Video output must be dropped while paused", "kind == MuxerTrack.VIDEO && pauseRequested.get()" in encoder)
        assertTrue("Audio input must stop advancing while paused", "if (pauseRequested.get())" in encoder)
        assertTrue("Video PTS must use the pause-aware clock", "recordingClock.elapsed() / 1_000L" in encoder)
        assertTrue("Resume must request a sync frame", "PARAMETER_KEY_REQUEST_SYNC_FRAME" in encoder)
        assertTrue(
            "Resume must reject key frames queued before the resumed Surface boundary",
            "resumeKeyFrameGate.awaitNextKeyFrameAtOrAfter" in encoder,
        )
        assertTrue("Pause must detach the display before draining old frames", "display.setSurface(null)" in encoder)
        assertTrue("Resume must wait for the detached video queue to drain", "pausedVideoDrain" in encoder)

        val resumeTransition =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/" +
                    "ScreenRecordingService.kt",
            ).readText()
                .substringAfter("private fun resumeOnWorker")
                .substringBefore("private fun transitionFailure")
        assertTrue(
            "Runtime state must not count the encoder resume wait as recorded time",
            resumeTransition.indexOf("activeEncoder.resume()") <
                resumeTransition.indexOf("phase = ScreenRecordingPhase.RECORDING"),
        )
    }

    @Test
    fun focusCardUsesOnlyImplementedControlsAndExpiresItsCompletionEvent() {
        val focus =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/" +
                    "ScreenRecordingFocusNotification.kt",
            ).readText()
        val service =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/" +
                    "ScreenRecordingService.kt",
            ).readText()

        assertTrue("Expanded card must use Xiaomi custom Focus RemoteViews", "FocusCustomRemoteViews" in focus)
        assertTrue("Pause control must have a PendingIntent", "screen_recording_focus_pause, pauseResumeIntent" in focus)
        assertTrue("Stop control must have a PendingIntent", "screen_recording_focus_stop, stopIntent" in focus)
        assertFalse("Unsupported live audio toggles must not be exposed", "audioSysClick" in focus || "audioMicClick" in focus)
        assertTrue("Recording must preempt the medium resident island", "IslandPriority.HIGH" in focus)
        assertTrue("Completion must time out after four seconds", "COMPLETION_TIMEOUT_MILLIS = 4_000L" in focus)
        assertTrue("View/share grants must remain read-only", "FLAG_GRANT_READ_URI_PERMISSION" in focus)
        assertTrue("Every recording PendingIntent must be immutable", "PendingIntent.FLAG_IMMUTABLE" in focus)
        assertTrue("The detached completion notification must survive service stop", "STOP_FOREGROUND_DETACH" in service)
        assertTrue("A stale pause action must stop a cold-started service", "stopSelf(startId)" in service)
        assertTrue("A saved file must keep its completion card even when Root restore fails", "failure == null && savedOutput != null" in service)
        assertTrue(
            "Pause and resume PendingIntents must encode a target state instead of toggling late",
            "targetPaused: Boolean" in service &&
                "recordingPaused.get() == targetPaused" in service &&
                "ACTION_PAUSE" in service &&
                "ACTION_RESUME" in service,
        )
        assertTrue(
            "Worker notification updates must serialize behind an in-flight ticker",
            "private fun postActiveFocusNotification" in service && "mainHandler.post" in service,
        )
    }

    @Test
    fun pmbStyleResourcesStayModuleOwnedAndUseTheMeasuredPalette() {
        val expanded =
            sourceFile(
                "modules/source-screenrecord/src/main/res/layout/screen_recording_focus_expanded.xml",
            ).readText()
        val card =
            sourceFile(
                "modules/source-screenrecord/src/main/res/drawable/screen_recording_focus_card_background.xml",
            ).readText()
        val stop =
            sourceFile(
                "modules/source-screenrecord/src/main/res/drawable/screen_recording_focus_stop_background.xml",
            ).readText()
        val control =
            sourceFile(
                "modules/source-screenrecord/src/main/res/drawable/screen_recording_focus_control_background.xml",
            ).readText()
        val dot =
            sourceFile(
                "modules/source-screenrecord/src/main/res/drawable/ic_screen_recording_dot.xml",
            ).readText()
        val completed =
            sourceFile(
                "modules/source-screenrecord/src/main/res/drawable/ic_screen_recording_complete.xml",
            ).readText()

        assertTrue("Expanded layout must include both real controls", "screen_recording_focus_pause" in expanded && "screen_recording_focus_stop" in expanded)
        assertTrue("Expanded card must retain the measured 118dp vertical geometry", "android:paddingVertical=\"14dp\"" in expanded && "android:layout_marginTop=\"14dp\"" in expanded)
        assertTrue("Visible Seedling controls must remain 42dp inside 52dp touch targets", "android:layout_width=\"42dp\"" in expanded && "android:layout_width=\"52dp\"" in expanded)
        assertFalse("Unsupported audio controls must not reserve an extra text row", "screen_recording_focus_expanded_audio" in expanded)
        assertTrue("Card must keep the PMB-style dark surface", "#F21D1D1F" in card)
        assertTrue("Pause must use the Seedling 16-percent white background", "#29FFFFFF" in control)
        assertTrue("Stop must keep the Seedling red background", "#FFDF3F3A" in stop)
        assertTrue("Compact icon must keep the audited recording red", "#FFFF6C61" in dot)
        assertTrue("Compact dot must keep the ColorOS five-unit radius", "M10,10m-5,0" in dot)
        assertTrue("Completion icon must use the ColorOS green outline", "strokeColor=\"#FF24B232\"" in completed)
        assertTrue("Decorative recording icon must not announce a stale state", "android:contentDescription=\"@null\"" in expanded)
    }
}
