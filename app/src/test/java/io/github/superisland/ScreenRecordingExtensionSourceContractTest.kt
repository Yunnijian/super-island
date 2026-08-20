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
        assertTrue(libraryManifest.contains(".ScreenRecordingCompletionActionActivity"))
        assertTrue(libraryManifest.contains(".ScreenRecordingService"))
        assertTrue(libraryManifest.contains(".ScreenRecordingTileService"))
        assertTrue(libraryManifest.contains("android.service.quicksettings.ACTIVE_TILE"))
        val inModuleCaptureBlock =
            libraryManifest
                .substringAfter("""android:name=".ScreenRecordingCaptureActivity"""")
                .substringBefore("/>")
        val tileCaptureBlock =
            libraryManifest
                .substringAfter("""android:name=".ScreenRecordingTileCaptureActivity"""")
                .substringBefore("/>")
        val completionActionBlock =
            libraryManifest
                .substringAfter("""android:name=".ScreenRecordingCompletionActionActivity"""")
                .substringBefore("/>")
        // In-module capture stays in MainActivity's task. QS uses an empty-affinity disposable
        // task: singleInstance creates a separate HyperOS status-bar container.
        assertFalse(inModuleCaptureBlock.contains("singleInstance"))
        assertFalse(inModuleCaptureBlock.contains("taskAffinity"))
        assertFalse(tileCaptureBlock.contains("android:launchMode"))
        assertTrue(tileCaptureBlock.contains("android:taskAffinity=\"\""))
        assertTrue(completionActionBlock.contains("android:exported=\"true\""))
        assertTrue(
            completionActionBlock.contains(
                "android:permission=\"android.permission.STATUS_BAR_SERVICE\"",
            ),
        )
        assertTrue(libraryManifest.contains("Theme.SuperIsland.ScreenRecording.Capture"))
        val captureStyles =
            sourceFile("modules/source-screenrecord/src/main/res/values/styles.xml").readText()
        assertTrue(captureStyles.contains("windowBackground"))
        assertTrue(captureStyles.contains("windowIsTranslucent"))
        assertTrue(captureStyles.contains("statusBarColor"))
        assertTrue(captureStyles.contains("navigationBarColor"))
        assertTrue(libraryManifest.contains("FOREGROUND_SERVICE_MEDIA_PROJECTION"))
        assertTrue(libraryManifest.contains("FOREGROUND_SERVICE_MICROPHONE"))
        assertTrue(libraryManifest.contains("RECORD_AUDIO"))
        assertTrue(libraryManifest.contains("""android:foregroundServiceType="mediaProjection|microphone""""))

        assertTrue(appManifest.contains("SEND_SCREEN_RECORDING_CONTROL"))
        assertTrue(appBuild.contains("""project(":source-screenrecord")"""))
        assertTrue(proguard.contains("ScreenRecordingCaptureActivity"))
        assertTrue(proguard.contains("ScreenRecordingCompletionActionActivity"))
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
    fun stoppedModuleTileRecoversOnlyFromAnExplicitWarsawQsClick() {
        val module =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SuperIslandXposedModule.java",
            ).readText()
        val guard =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiScreenRecordingTileForceStopGuard.java",
            ).readText()

        assertTrue(module.contains("installScreenRecordingTileForceStopGuard"))
        assertTrue(module.contains("Screen-recording QS stopped-package guard unavailable"))
        assertTrue(guard.contains("TileServiceManager"))
        assertTrue(guard.contains("ScreenRecordingTileService"))
        assertTrue(guard.contains("ApplicationInfo.FLAG_STOPPED"))
        assertTrue(guard.contains("WeakReference"))
        assertTrue(guard.contains("getTileWrapper"))
        assertTrue(guard.contains("setBindRequested"))
        assertTrue(guard.contains("setBindService"))
        assertTrue(guard.contains("mUnbindImmediate"))
        assertTrue(guard.contains("mQueuedMessages"))
        assertTrue(guard.contains("mClickBinder"))
        assertTrue(guard.contains("onClick"))
        assertTrue(guard.contains("mPackageManagerAdapter"))
        assertTrue(guard.contains("mIPackageManager"))
        assertTrue(guard.contains("setPackageStoppedState"))
        assertTrue(guard.contains("CustomTile"))
        assertTrue(guard.contains("handleClick"))
        assertTrue(guard.contains("ExplicitClickProvenance"))
        assertTrue(guard.contains("explicitClickProvenance.consume"))
        assertTrue(guard.contains("prepareExplicitClickRecovery"))
        assertTrue(guard.contains("canRecoverExplicitClick"))
        assertTrue(guard.contains("isRecoveredApplication"))
        assertTrue(guard.contains("recoveryBindingsAvailable"))
        assertTrue(guard.contains("requestBindingAfterRecoveredClick"))
        assertTrue(guard.contains("mExecutor"))
        assertTrue(guard.contains("RecoveryAttemptRegistry"))
        assertTrue(guard.contains("beginRecoveredServiceConnection"))
        assertTrue(guard.contains("noteRecoveredClickDispatch"))
        assertTrue(guard.contains("finishRecoveredServiceConnection"))
        assertTrue(guard.contains("executeDelayedMethod.invoke"))
        assertTrue(guard.contains("failRecoveredBinding"))
        assertTrue(guard.contains("abortRecoveredClick"))
        assertFalse(guard.contains("hasEstablishedLifecycleBinding"))
        assertFalse(guard.contains("\"mIsBound\""))
        assertFalse(guard.contains("FLAG_INCLUDE_STOPPED_PACKAGES"))
        assertFalse(guard.contains("setApplicationEnabledSetting"))
        assertTrue(module.contains("blockForceStoppedLifecycleBind"))
        assertTrue(module.contains("proceed(new Object[]{false})"))
        assertTrue(module.contains("Recovered stopped screen-recording tile after explicit QS click"))
        val clickHookStart = module.indexOf("ClickRecoveryDecision decision")
        val queueCurrentClick = module.indexOf("result = chain.proceed()", clickHookStart)
        val requestRecoveredBind = module.indexOf("requestBindingAfterRecoveredClick", clickHookStart)
        val registerRecoveryAttempt = guard.indexOf("recoveryAttempts.begin(")
        val requestOwnerBind = guard.indexOf(
            "setBindRequestedMethod.invoke(owner, true)",
            registerRecoveryAttempt,
        )
        val scheduleConnectionTimeout = guard.indexOf(
            "executeDelayedMethod.invoke(",
            requestOwnerBind,
        )
        assertTrue(clickHookStart >= 0)
        assertTrue(queueCurrentClick > clickHookStart)
        assertTrue(requestRecoveredBind > queueCurrentClick)
        assertTrue(registerRecoveryAttempt > 0)
        assertTrue(requestOwnerBind > registerRecoveryAttempt)
        assertTrue(scheduleConnectionTimeout > requestOwnerBind)
        assertTrue(guard.contains("clearForceStoppedManager"))
        assertTrue(guard.contains("resetUnbindImmediateBestEffort"))
        assertTrue(guard.contains("discardPendingClickBestEffort"))
        assertTrue(guard.contains("return false"))
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
        assertTrue(focus.contains("正在录屏"))
        assertTrue(service.contains("ScreenRecordingFocusNotification"))
        assertTrue(service.contains("islandTicker"))
        assertTrue(service.contains("requestPauseResume"))
        assertTrue(service.contains("ScreenRecordingPhase.PAUSED"))
        assertTrue(service.contains("STOP_FOREGROUND_DETACH"))
        assertTrue(captureSource.contains("createConfigForDefaultDisplay"))
        val encoder =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingEncoder.kt",
            ).readText()
        assertTrue(encoder.contains("releaseOutputBuffer(outputIndex, false)"))
        assertTrue(encoder.contains("Copy + release the codec buffer BEFORE muxer.writeSample") || encoder.contains("sampleCopy"))
        assertTrue(encoder.contains("presentationClockUs") || encoder.contains("normalizePresentationTimeUs"))
        assertTrue(encoder.contains("recordingClock.elapsed() / 1_000L"))
        assertTrue(extension.contains("commitNow"))
        assertTrue(extension.contains("showStopAction") || extension.contains("停止录制"))
        assertTrue(extension.contains("elapsedMillis"))
        assertTrue(extension.contains("ScreenRecordingService.requestStop"))
        assertTrue(extension.contains("ScreenRecordingService.requestPauseResume"))
        assertTrue(extension.contains("继续录制"))
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
        assertTrue(tile.contains("TileService.requestListeningState"))
        assertFalse(tile.contains("startForegroundService"))
        assertTrue(runtimeStore.contains("ScreenRecordingTileService.requestRefresh"))
        val configStore =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingConfigStore.kt",
            ).readText()
        assertTrue(configStore.contains("ScreenRecordingTileService.requestRefresh"))
        assertTrue(captureSource.contains("EXTRA_KEEP_CALLER_IN_FRONT") || captureSource.contains("keepCallerInFront"))
        assertTrue(captureSource.contains("tileCaptureIntent"))
        assertTrue(captureSource.contains("Intent.FLAG_ACTIVITY_NEW_TASK"))
        assertTrue(captureSource.contains("overrideActivityTransition"))
        assertTrue(captureSource.contains("setDecorFitsSystemWindows(false)"))
        assertTrue(captureSource.contains("Color.TRANSPARENT"))
        assertFalse(captureSource.contains("finishAndRemoveTask"))
        val focusNotif =
            sourceFile(
                "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingFocusNotification.kt",
            ).readText()
        val initialForeground =
            service.substringAfter("startForeground(").substringBefore("foregroundServiceType(config)")
        val stableFocus =
            service.substringAfter("private fun activeFocusNotification")
                .substringBefore("private fun postActiveFocusNotification")
        assertTrue(initialForeground.contains("showInNotificationShade = false"))
        assertTrue(stableFocus.contains("showInNotificationShade = focusRowVisible.get()"))
        assertTrue(service.contains("postDelayed(focusRowReveal, ISLAND_TICK_MILLIS)"))
        assertTrue(service.split("focusRowVisible.set(true)").size == 2)
        assertTrue(focusNotif.substringAfter("fun buildCompleted").contains("showInNotificationShade = true"))
        assertTrue(focusNotif.contains("silent = true") || focusNotif.contains("silent=true"))
        assertTrue(focusNotif.contains("FocusCustomRemoteViews"))
        assertTrue(focusNotif.contains("screen_recording_focus_notification_night"))
        assertTrue(focusNotif.contains("screen_recording_focus_notification_complete_night"))
        assertTrue(focusNotif.contains("buildCompleted"))
        assertTrue(focusNotif.contains("IslandPriority.HIGH"))
        assertTrue(focusNotif.contains("ScreenRecordingCompletionActionActivity.viewPendingIntent"))
        assertTrue(focusNotif.contains("ScreenRecordingCompletionActionActivity.sharePendingIntent"))
        val captureManifest =
            sourceFile("modules/source-screenrecord/src/main/AndroidManifest.xml").readText()
        assertTrue(captureManifest.contains("ScreenRecordingTileCaptureActivity"))
        assertTrue(captureManifest.contains("taskAffinity"))
        assertTrue(captureManifest.contains("noHistory"))
        val completionActionBlock =
            captureManifest
                .substringAfter("""android:name=".ScreenRecordingCompletionActionActivity"""")
                .substringBefore("/>")
        assertTrue(completionActionBlock.contains("android:exported=\"true\""))
        assertTrue(
            completionActionBlock.contains(
                "android:permission=\"android.permission.STATUS_BAR_SERVICE\"",
            ),
        )
        assertTrue(completionActionBlock.contains("android:taskAffinity=\"\""))
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
        assertTrue(miuixConfirm.contains("pauseActionLabel"))
        val materialConfirm =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/material/ScreenRecordingMaterialScreen.kt",
            ).readText()
        assertTrue(materialConfirm.contains("SegmentedDropdownItem"))
        assertTrue(materialConfirm.contains("SegmentedSwitchItem"))
        assertTrue(materialConfirm.contains("colorScheme.error") || materialConfirm.contains("error"))
        assertTrue(materialConfirm.contains("PauseCircle"))
        assertTrue(materialConfirm.contains("PlayCircle"))
        assertFalse(materialConfirm.contains("top.yukonga.miuix"))
    }

}
