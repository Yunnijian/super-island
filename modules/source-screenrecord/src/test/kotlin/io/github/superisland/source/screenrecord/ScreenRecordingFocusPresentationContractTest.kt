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
        val completionAction =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/" +
                    "ScreenRecordingCompletionActionActivity.kt",
            ).readText()

        assertTrue("Expanded card must use Xiaomi custom Focus RemoteViews", "FocusCustomRemoteViews" in focus)
        assertTrue("Pause control must have a PendingIntent", "screen_recording_focus_pause, pauseResumeIntent" in focus)
        assertTrue("Stop control must have a PendingIntent", "screen_recording_focus_stop, stopIntent" in focus)
        assertTrue(
            "Visible Focus row must bind its real pause and finish actions",
            "screen_recording_focus_notification_pause" in focus &&
                "screen_recording_focus_notification_stop" in focus,
        )
        assertTrue(
            "Completed Focus row must bind view and share separately from the completed island",
            "screen_recording_focus_notification_complete_root" in focus &&
                "screen_recording_focus_notification_complete_share" in focus &&
                "islandExpanded = islandExpanded" in focus,
        )
        assertFalse("Unsupported live audio toggles must not be exposed", "audioSysClick" in focus || "audioMicClick" in focus)
        assertTrue("Recording must preempt the medium resident island", "IslandPriority.HIGH" in focus)
        assertTrue("Completion must time out after four seconds", "COMPLETION_TIMEOUT_MILLIS = 4_000L" in focus)
        val targetAction =
            completionAction
                .substringAfter("val target =")
                .substringBefore("val chooserTitle")
        val chooserAction =
            completionAction
                .substringAfter("Intent.createChooser(target, chooserTitle)")
                .substringBefore("companion object")
        assertTrue(
            "View/share target must receive a read-only URI grant",
            ".addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)" in targetAction,
        )
        assertTrue(
            "View/share target must carry the URI in ClipData",
            "clipData = ClipData.newRawUri" in targetAction,
        )
        assertTrue(
            "View/share chooser must propagate the read-only URI grant",
            ".addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)" in chooserAction,
        )
        assertTrue("Every completion PendingIntent must be immutable", "PendingIntent.FLAG_IMMUTABLE" in completionAction)
        assertTrue("The detached completion notification must survive service stop", "STOP_FOREGROUND_DETACH" in service)
        assertTrue("A stale pause action must stop a cold-started service", "stopSelf(startId)" in service)
        val completionFlow =
            service
                .substringAfter("val completionState =")
                .substringBefore("mainHandler.removeCallbacks(focusRowReveal)")
        assertTrue(
            "Completion state must be committed before publication is considered",
            "val completionStatePersisted = runtimeStore.save(completionState)" in completionFlow,
        )
        assertTrue(
            "The real persistence result must reach the tested completion policy",
            "statePersisted = completionStatePersisted" in completionFlow,
        )
        assertTrue(
            "The completion notification must stay behind the policy result",
            "if (shouldPublishCompletion && savedOutput != null)" in completionFlow,
        )
        assertTrue(
            "Only a successfully posted completion notification may detach from the FGS",
            "if (completionPosted)" in service &&
                "STOP_FOREGROUND_DETACH" in service &&
                "STOP_FOREGROUND_REMOVE" in service,
        )
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

    @Test
    fun colorOsFocusRowsUseAuditedDayNightNotificationGeometry() {
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
        val activeDay =
            sourceFile(
                "modules/source-screenrecord/src/main/res/layout/screen_recording_focus_notification.xml",
            ).readText()
        val activeNight =
            sourceFile(
                "modules/source-screenrecord/src/main/res/layout/" +
                    "screen_recording_focus_notification_night.xml",
            ).readText()
        val completeDay =
            sourceFile(
                "modules/source-screenrecord/src/main/res/layout/" +
                    "screen_recording_focus_notification_complete.xml",
            ).readText()
        val completeNight =
            sourceFile(
                "modules/source-screenrecord/src/main/res/layout/" +
                    "screen_recording_focus_notification_complete_night.xml",
            ).readText()
        val actionBackground =
            sourceFile(
                "modules/source-screenrecord/src/main/res/drawable/" +
                    "screen_recording_focus_notification_action_background.xml",
            ).readText()
        val actionBackgroundNight =
            sourceFile(
                "modules/source-screenrecord/src/main/res/drawable/" +
                    "screen_recording_focus_notification_action_background_night.xml",
            ).readText()

        assertTrue(
            "Focus day/night rows must be independent from the dark island card",
            "R.layout.screen_recording_focus_notification" in focus &&
                "R.layout.screen_recording_focus_notification_night" in focus &&
                "islandExpanded = expanded" in focus,
        )
        assertTrue(
            "Completion day/night rows must remain independent from the completion island",
            "R.layout.screen_recording_focus_notification_complete" in focus &&
                "R.layout.screen_recording_focus_notification_complete_night" in focus,
        )
        listOf(activeDay, activeNight).forEach { layout ->
            assertTrue("ColorOS notification text must stay 14sp", "android:textSize=\"14sp\"" in layout)
            assertTrue("ColorOS action row must start after 16dp", "android:layout_marginTop=\"16dp\"" in layout)
            assertTrue("ColorOS action targets must remain 28dp high", "android:layout_height=\"28dp\"" in layout)
            assertTrue("ColorOS actions must keep their 8dp separation", "android:layout_marginStart=\"8dp\"" in layout)
            assertTrue("Both real active actions must be present", "screen_recording_focus_notification_pause" in layout && "screen_recording_focus_notification_stop" in layout)
        }
        assertTrue("Day row must use ColorOS neutral text colors", "#E6000000" in activeDay && "#8A000000" in activeDay)
        assertTrue("Night row must use ColorOS neutral text colors", "#E6FFFFFF" in activeNight && "#8AFFFFFF" in activeNight)
        assertTrue("Day action fill must match the audited ColorOS resource", "#14000000" in actionBackground)
        assertTrue("Night action fill must match the audited ColorOS resource", "#26FFFFFF" in actionBackgroundNight)
        assertTrue("ColorOS action pills must retain their 30dp radius", "android:radius=\"30dp\"" in actionBackground && "android:radius=\"30dp\"" in actionBackgroundNight)
        listOf(completeDay, completeNight).forEach { layout ->
            assertTrue("Completed row must expose the real share action", "screen_recording_focus_notification_complete_share" in layout)
            assertTrue("Completed share action must stay 28dp high", "android:layout_height=\"28dp\"" in layout)
            assertTrue("Completed share action must start after 12dp", "android:layout_marginTop=\"12dp\"" in layout)
        }

        val initialForeground =
            service.substringAfter("startForeground(").substringBefore("foregroundServiceType(config)")
        val stableNotification =
            service.substringAfter("private fun activeFocusNotification").substringBefore("private fun postActiveFocusNotification")
        assertTrue("Initial FGS must remain hidden while projection UI settles", "showInNotificationShade = false" in initialForeground)
        assertTrue(
            "Every active update must use the shared delayed visibility gate",
            "showInNotificationShade = focusRowVisible.get()" in stableNotification,
        )
        val recordingStart =
            service
                .substringAfter("private fun startOnWorker")
                .substringBefore("private fun handlePauseResumeRequest")
        assertTrue(
            "The first visible row must wait for the stable one-second reveal",
            "postDelayed(focusRowReveal, ISLAND_TICK_MILLIS)" in recordingStart,
        )
        assertFalse(
            "The recording start path must not schedule a visible ticker before the reveal",
            "postDelayed(islandTicker, ISLAND_TICK_MILLIS)" in recordingStart,
        )
        assertTrue(
            "Only the delayed reveal may open the notification-shade gate",
            service.split("focusRowVisible.set(true)").size == 2,
        )
        assertTrue("Completion Focus row must be visible", "showInNotificationShade = true" in focus.substringAfter("fun buildCompleted"))
    }
}
