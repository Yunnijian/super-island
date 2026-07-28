package io.github.superisland

import io.github.superisland.ui.extensionsDirectoryGroups
import io.github.superisland.ui.navigation.AppDestination
import io.github.superisland.ui.navigation.backDestination
import io.github.superisland.ui.navigation.isDetail
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecordingExtensionSourceContractTest {
    @Test
    fun directoryAndRouteExposeOneRealScreenRecordingExtension() {
        val entry =
            extensionsDirectoryGroups()
                .flatMap { group -> group.entries }
                .single { item -> item.id == "screen_recording" }

        assertEquals("超级岛录屏", entry.title)
        assertEquals(AppDestination.EXTENSIONS, AppDestination.EXTENSION_SCREEN_RECORDING.backDestination)
        assertTrue(AppDestination.EXTENSION_SCREEN_RECORDING.isDetail)
    }

    @Test
    fun libraryManifestDeclaresCaptureServiceAndProjectionPermissions() {
        val libraryManifest = sourceFile("modules/source-screenrecord/src/main/AndroidManifest.xml").readText()
        val appManifest = sourceFile("app/src/main/AndroidManifest.xml").readText()
        val proguard = sourceFile("app/proguard-rules.pro").readText()
        val appBuild = sourceFile("app/build.gradle.kts").readText()

        // Empty self-closing root would silently drop every component from the merge.
        assertFalse(libraryManifest.contains("""<manifest xmlns:android="http://schemas.android.com/apk/res/android" />"""))
        assertEquals(1, Regex("""<\?xml version="1\.0" encoding="utf-8"\?>""").findAll(libraryManifest).count())
        assertEquals(1, Regex("""<manifest\b""").findAll(libraryManifest).count())

        assertTrue(libraryManifest.contains(".ScreenRecordingCaptureActivity"))
        assertTrue(libraryManifest.contains(".ScreenRecordingTileCaptureActivity"))
        assertTrue(libraryManifest.contains(".ScreenRecordingService"))
        assertTrue(libraryManifest.contains(".ScreenRecordingTileService"))
        val inModuleCaptureBlock =
            libraryManifest
                .substringAfter("""android:name=".ScreenRecordingCaptureActivity"""")
                .substringBefore("/>")
        val tileCaptureBlock =
            libraryManifest
                .substringAfter("""android:name=".ScreenRecordingTileCaptureActivity"""")
                .substringBefore("/>")
        // In-module capture must NOT use singleInstance (causes status-bar task flash).
        assertFalse(inModuleCaptureBlock.contains("singleInstance"))
        assertFalse(inModuleCaptureBlock.contains("taskAffinity"))
        assertTrue(tileCaptureBlock.contains("singleInstance"))
        assertTrue(tileCaptureBlock.contains("taskAffinity"))
        assertTrue(libraryManifest.contains("FOREGROUND_SERVICE_MEDIA_PROJECTION"))
        assertTrue(libraryManifest.contains("FOREGROUND_SERVICE_MICROPHONE"))
        assertTrue(libraryManifest.contains("RECORD_AUDIO"))
        assertTrue(libraryManifest.contains("""android:foregroundServiceType="mediaProjection|microphone""""))

        assertTrue(appManifest.contains("SEND_SCREEN_RECORDING_CONTROL"))
        assertTrue(appBuild.contains("""project(":source-screenrecord")"""))
        assertTrue(proguard.contains("ScreenRecordingCaptureActivity"))
        assertTrue(proguard.contains("ScreenRecordingService"))
        assertTrue(proguard.contains("ScreenRecordingTileService"))
    }

    @Test
    fun rootBridgeIsOptionalFailClosedAndWarsawGated() {
        val module =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SuperIslandXposedModule.java",
            ).readText()
        val bridge =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiScreenRecordingRootBridge.java",
            ).readText()
        val contract =
            sourceFile(
                "modules/core-model/src/main/kotlin/io/github/superisland/model/ScreenRecordingRootControlContract.kt",
            ).readText()
        val rootSettings =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingRootSettings.kt",
            ).readText()

        assertTrue(module.contains("SystemUiScreenRecordingRootBridge.register"))
        assertTrue(module.contains("Screen-recording Root settings bridge unavailable"))
        assertTrue(bridge.contains("SENDER_PERMISSION") || bridge.contains("ScreenRecordingRootControlContract"))
        assertTrue(bridge.contains("isAuthorizedModuleSender"))
        assertTrue(contract.contains("VERIFIED_DEVICE = \"warsaw\""))
        assertTrue(contract.contains("OS3.0.306.0.WHPCNXM"))
        assertTrue(rootSettings.contains("isVerifiedDevice"))
        assertTrue(rootSettings.contains("recording continues without Root settings"))
        assertTrue(bridge.contains("isAuthorizedModuleSender"))
        assertTrue(rootSettings.contains("setProjectMediaAllowed"))
        val projectMedia =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingProjectMedia.kt",
            ).readText()
        assertTrue(projectMedia.contains("FIXED_CMD_ALLOW") || projectMedia.contains("PROJECT_MEDIA allow"))
        assertTrue(projectMedia.contains("cmd appops set"))
        assertFalse(projectMedia.contains("Runtime.getRuntime().exec(\""))
        assertFalse(rootSettings.contains("Runtime.getRuntime().exec"))
        assertFalse(rootSettings.contains("su -c"))
        assertFalse(bridge.contains("Runtime.getRuntime().exec"))
        // registerReceiver(broadcastPermission=SEND_*) gates senders; sendBroadcast must not also
        // require the same permission on SystemUI (which cannot hold the module signature perm).
        assertTrue(
            "applicationContext.sendBroadcast(request)" in rootSettings ||
                "sendBroadcast(request)" in rootSettings,
        )
        assertFalse(
            rootSettings.contains(
                """sendBroadcast(
                request,
                ScreenRecordingRootControlContract.SENDER_PERMISSION,
            )""",
            ),
        )
    }

    @Test
    fun confirmDialogAndFocusForegroundAreWired() {
        val extension =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/extensions/ScreenRecordingExtensionScreen.kt",
            ).readText()
        val focus =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingFocusNotification.kt",
            ).readText()
        val service =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingService.kt",
            ).readText()
        val captureSource =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingCaptureActivity.kt",
            ).readText()

        assertTrue(extension.contains("开始前确认"))
        assertTrue(extension.contains("授予投影媒体权限"))
        assertTrue(extension.contains("confirmBeforeStart"))
        assertTrue(extension.contains("ScreenRecordingMiuixConfirmDialog"))
        assertTrue(extension.contains("ScreenRecordingMaterialConfirmDialog"))
        assertTrue(focus.contains("FocusNotificationPublisher"))
        assertTrue(focus.contains("正在录制"))
        assertTrue(service.contains("ScreenRecordingFocusNotification"))
        assertTrue(service.contains("islandTicker"))
        assertTrue(captureSource.contains("createConfigForDefaultDisplay"))
        val encoder =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingEncoder.kt",
            ).readText()
        assertTrue(encoder.contains("releaseOutputBuffer(outputIndex, false)"))
        assertTrue(encoder.contains("Copy + release the codec buffer BEFORE muxer.writeSample") || encoder.contains("sampleCopy"))
        assertTrue(encoder.contains("presentationClockUs") || encoder.contains("normalizePresentationTimeUs"))
        assertTrue(encoder.contains("recordingEpochNanos") || encoder.contains("Ignore Surface/codec PTS"))
        assertTrue(extension.contains("commitNow"))
        assertTrue(extension.contains("showStopAction") || extension.contains("停止录制"))
        assertTrue(extension.contains("elapsedMillis"))
        assertTrue(extension.contains("ScreenRecordingService.requestStop"))
        // Stop path must not promote a dead session into an FGS start.
        assertFalse(extension.contains("ContextCompat.startForegroundService"))
        assertTrue(service.contains("fun reconcileRuntime"))
        assertTrue(service.contains("fun hasActiveSession"))
        assertTrue(service.contains("fun requestStop(context: Context"))
        assertTrue(service.contains("liveInstance") || service.contains("handleStopRequest"))
        assertTrue(service.contains("上一次录制已中断") || service.contains("hasActiveSession"))
        val hub =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingRuntimeHub.kt",
            ).readText()
        assertTrue(hub.contains("publish"))
        assertTrue(hub.contains("addListener"))
        val runtimeStore =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingRuntimeStore.kt",
            ).readText()
        assertTrue(runtimeStore.contains("ScreenRecordingRuntimeHub.publish"))
        val owner =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/extensions/ScreenRecordingExtensionUiStateOwner.kt",
            ).readText()
        assertTrue(owner.contains("reconcileRuntime"))
        assertTrue(owner.contains("refreshRuntime"))
        assertTrue(owner.contains("ScreenRecordingRuntimeHub"))
        assertTrue(extension.contains("showFinalizingAction") || extension.contains("正在完成"))
        val tile =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingTileService.kt",
            ).readText()
        assertTrue(tile.contains("tileCaptureIntent") || tile.contains("ScreenRecordingTileCaptureActivity"))
        assertTrue(tile.contains("observe(runtimeListener)"))
        assertTrue(tile.contains("ScreenRecordingService.requestStop") || tile.contains("reconcileRuntime"))
        assertFalse(tile.contains("startForegroundService"))
        assertTrue(captureSource.contains("EXTRA_KEEP_CALLER_IN_FRONT") || captureSource.contains("keepCallerInFront"))
        assertTrue(captureSource.contains("tileCaptureIntent"))
        assertTrue(captureSource.contains("overridePendingTransition"))
        assertTrue(
            captureSource.contains("finishAndRemoveTask") ||
                captureSource.contains("moveTaskToBack"),
        )
        val focusNotif =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingFocusNotification.kt",
            ).readText()
        assertTrue(focusNotif.contains("showInNotificationShade = false"))
        assertTrue(focusNotif.contains("silent = true") || focusNotif.contains("silent=true"))
        val captureManifest =
            sourceFile("modules/source-screenrecord/src/main/AndroidManifest.xml").readText()
        assertTrue(captureManifest.contains("ScreenRecordingTileCaptureActivity"))
        assertTrue(captureManifest.contains("taskAffinity"))
        assertTrue(captureManifest.contains("noHistory"))
        // Attribute must stay off: QS collapse can close system dialogs mid-consent.
        assertFalse(captureManifest.contains("android:finishOnCloseSystemDialogs"))
        val miuixConfirm =
            sourceFile(
                "modules/ui-design-system/src/main/kotlin/io/github/superisland/design/ScreenRecordingMiuix.kt",
            ).readText()
        assertTrue(miuixConfirm.contains("要开始屏幕录制吗？"))
        assertTrue(miuixConfirm.contains("WindowSpinnerPreference"))
        assertTrue(miuixConfirm.contains("DropdownItem"))
        assertTrue(miuixConfirm.contains("AppDangerButton"))
        val materialConfirm =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/material/ScreenRecordingMaterialScreen.kt",
            ).readText()
        assertTrue(materialConfirm.contains("SegmentedDropdownItem"))
        assertTrue(materialConfirm.contains("SegmentedSwitchItem"))
        assertTrue(materialConfirm.contains("colorScheme.error") || materialConfirm.contains("error"))
        assertFalse(materialConfirm.contains("top.yukonga.miuix"))
    }

}
