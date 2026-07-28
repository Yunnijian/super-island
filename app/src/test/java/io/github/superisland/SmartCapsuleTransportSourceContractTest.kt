package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCapsuleTransportSourceContractTest {
    @Test
    fun smartCapsuleUiUsesSystemUiConfigInsteadOfNotificationListenerProxy() {
        val source = sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val dashboard =
            source.substringAfter("private fun SmartCapsuleDashboard(")
                .substringBefore("private fun MediaIsland(")
        val stateOwner =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/SmartCapsuleDashboardStateOwner.kt",
            ).readText()

        listOf(
            "NotificationProxyController",
            "ACTION_NOTIFICATION_LISTENER_SETTINGS",
            "FocusNotificationPublisher",
            "activeProxyCount",
            "通知代理",
        ).forEach { retiredToken ->
            assertFalse(
                "Smart Capsule must not depend on the retired listener proxy token $retiredToken",
                retiredToken in dashboard,
            )
        }
        listOf(
            "SmartCapsuleDashboardStateOwner",
            "SmartCapsuleAppsScreen",
            "SmartCapsuleAppProfileScreen",
        ).forEach { requiredToken ->
            assertTrue("Smart Capsule must use $requiredToken", requiredToken in dashboard)
        }
        listOf(
            "SmartCapsuleConfigStore",
            "SmartCapsuleConsumerAcceptanceStore",
            "SmartCapsuleRuntimeStore",
            "SmartCapsuleAppDirectory",
        ).forEach { requiredToken ->
            assertTrue("The root-stable owner must use $requiredToken", requiredToken in stateOwner)
        }
        assertFalse("新路线不存在影子会话数", "runtime.activeSessionCount" in dashboard)
    }

    @Test
    fun smartCapsuleUsesThePinnedHyperIslandSystemUiSourceFlow() {
        val module =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SuperIslandXposedModule.java",
            ).readText()
        val mapper =
            sourceFile(
                "modules/hook-systemui/src/main/kotlin/io/github/superisland/hook/systemui/HyperIslandLocalNotificationAdapter.kt",
            ).readText()
        val focusOnlySuppressor =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiFocusOnlyIslandSuppressor.java",
            ).readText()
        val remoteRules =
            sourceFile(
                "modules/hook-systemui/src/main/kotlin/io/github/superisland/hook/systemui/RemoteSmartCapsuleRuleSnapshot.kt",
            ).readText()
        val selection =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/XmsfSmartCapsuleSelection.java",
            ).readText()
        val configBridge =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiSmartCapsuleConfigBridge.java",
            ).readText()
        val acceptance =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/SmartCapsuleConsumerAcceptance.kt",
            ).readText()
        val runtime = sourceFile("app/src/main/kotlin/io/github/superisland/XposedRuntimeController.kt").readText()
        val configSync =
            sourceFile("app/src/main/kotlin/io/github/superisland/SmartCapsuleHostConfigSync.kt").readText()
        val scope = sourceFile("app/src/main/resources/META-INF/xposed/scope.list").readText()
        val build = sourceFile("modules/hook-systemui/build.gradle.kts").readText()

        assertEquals(
            setOf(
                "com.android.systemui",
                "com.xiaomi.xmsf",
                "com.miui.mishare.connectivity",
            ),
            scope.lineSequence().filter(String::isNotBlank).toSet(),
        )
        assertTrue("SystemUI must install the local source-SBN mapper", "HyperIslandLocalNotificationAdapter.install(" in module)
        assertTrue("The mapper must hook Xiaomi before InnerNotifBean is generated", "generateInnerNotifBean" in mapper)
        assertTrue("The mapper must use the audited HyperIsland renderer chain", "TemplateRegistry.dispatch(" in mapper)
        assertTrue(
            "Focus-only rules must keep Focus rendering while omitting the island model and marking the source",
            "resolveFocusDisplayMode" in mapper &&
                "islandEnabled = !focusOnly" in mapper &&
                "workingExtras.putBoolean(FOCUS_ONLY_MARKER, true)" in mapper,
        )
        assertTrue(
            "Focus-only mapping must stay ordinary if the plugin-side island suppressor is unavailable",
            "isFocusOnlyIslandSuppressionAvailable()" in mapper &&
                "focus_only_suppressor_unavailable" in mapper,
        )
        assertTrue(
            "The plugin bridge must suppress both OEM island add and update paths",
            "addDynamicIslandView" in module &&
                "updateDynamicIslandView" in module &&
                "suppressFocusOnlyIsland" in module,
        )
        assertTrue(
            "Island suppression must require both mapper ownership and the Focus-only marker",
            "LOCAL_ADAPTER_NAME.equals(extras.getString(EXTRA_LOCAL_ADAPTER))" in focusOnlySuppressor &&
                "extras.getBoolean(EXTRA_FOCUS_ONLY, false)" in focusOnlySuppressor,
        )
        assertTrue(
            "Switching to Focus-only must remove a stale island without removing the source notification",
            "removeDynamicIslandView.invoke(controller, key, noFloat)" in focusOnlySuppressor &&
                "islands.remove(key)" in focusOnlySuppressor,
        )
        assertFalse("Focus-only suppression must not cancel the source notification", ".cancel(" in focusOnlySuppressor)
        assertFalse("Focus-only suppression must not hide the source notification", "setHidden" in focusOnlySuppressor)
        assertTrue("Focus output must be copied back onto the same source extras", "extras.putAll(workingExtras)" in mapper)
        assertTrue(
            "The local owner marker must be written only after payload generation",
            "if (!generated)" in mapper && "extras.putString(LOCAL_MARKER" in mapper,
        )
        assertFalse("The mapper must never publish a proxy notification", "NotificationManager" in mapper)
        assertFalse("The mapper must never clone the source SBN", ".clone(" in mapper)
        assertFalse("The mapper must never cancel the source notification", ".cancel(" in mapper)
        assertFalse("Framework injection must be absent", "onSystemServerStarting" in module)
        assertFalse("NMS private structures must be absent", "NotificationManagerService" in module)
        assertTrue("XMSF authorization must require the selected package/user", "selection.matches(" in module)
        assertTrue(
            "Only module-owned Focus may bypass Xiaomi's final visibility gate",
            "MODULE_PACKAGE.equals(packageName)" in module,
        )
        assertTrue(
            "Every third-party App must execute Xiaomi's original canShowFocus implementation",
            "return chain.proceed();" in module,
        )
        assertFalse(
            "App selection must not become a package-wide canShowFocus bypass",
            "HyperIslandLocalNotificationAdapter.isSelectedPackage" in module,
        )
        assertFalse(
            "The selected-App visibility fix must not add HyperIsland's unrelated custom-Focus bypass",
            "canCustomFocus" in module,
        )
        assertTrue("XMSF selection must consume the immutable remote snapshot", "SmartCapsuleRemoteSnapshotSelector" in selection)
        assertTrue(
            "SystemUI must acknowledge its exact accepted revision and digest through the Provider",
            "METHOD_REPORT_CONFIG_ACCEPTANCE" in configBridge &&
                "EXTRA_CONFIG_REVISION" in configBridge &&
                "EXTRA_CONFIG_DIGEST" in configBridge &&
                "ACCEPTANCE_CONSUMER_SYSTEM_UI" in acceptance,
        )
        assertTrue(
            "XMSF must acknowledge its exact accepted revision and digest through verified IPC",
            "ACTION_REPORT_XMSF_ACCEPTANCE" in module &&
                "setShareIdentityEnabled(true)" in module &&
                "ACCEPTANCE_CONSUMER_XMSF" in acceptance &&
                "sentFromUid" in acceptance &&
                "sentFromPackage" in acceptance,
        )
        assertTrue(
            "XMSF must register an explicit protected config reload receiver",
            "installXmsfRuntimeAttachHook" in module &&
                "RELOAD_SENDER_PERMISSION" in module,
        )
        assertTrue(
            "Only the XMSF AuthService process may acknowledge configuration",
            "resolveXmsfAuthProcessName" in module &&
                "XMSF_AUTH_SERVICE_ACTION" in module &&
                "XMSF_AUTH_PROCESS_METADATA" in module,
        )
        assertTrue(
            "XMSF config reload must leave its process main thread",
            "selection.reloadAsync(" in module && "RELOAD_EXECUTOR.execute" in selection,
        )
        assertTrue(
            "Config publication must notify both SystemUI and XMSF",
            "SystemUiSmartCapsuleContract.XMSF_PACKAGE" in configSync,
        )
        assertTrue(
            "Config publication must prepare exact consumer acceptance before reload",
            "SmartCapsuleConsumerAcceptanceStore(context).prepare(normalized)" in configSync,
        )
        assertFalse("SystemUI RemotePreferences must remain read-only", ".edit(" in remoteRules)
        assertFalse("XMSF RemotePreferences must remain read-only", ".edit(" in selection)
        assertTrue(
            "SystemUI must fail closed while a newer published revision reloads in the background",
            "currentIfPublished()" in mapper &&
                "snapshot.isStale(activeRevision)" in remoteRules &&
                "scheduleReload()" in remoteRules,
        )
        assertTrue("Activation must require SystemUI", "SYSTEM_UI_PACKAGE !in service.scope" in runtime)
        assertTrue("Activation must require XMSF", "XMSF_PACKAGE !in service.scope" in runtime)
        assertFalse("Activation must not require Framework", "SYSTEM_SERVER_SCOPE_ALIASES" in runtime)
        assertTrue(
            "The app must remove its retired Framework scope through libxposed API 102",
            "service.removeScope(listOf(RETIRED_SYSTEM_SCOPE))" in runtime,
        )
        assertTrue("HyperIsland source must remain pinned", "286bc4ce69b0924cd0ca623eb525b3b0e37afd9d" in build)
        assertTrue("The generated source set must remain an exact allowlist", "size == 22" in build)

        listOf(
            "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemServerFocusInjectionBridge.java",
            "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiSmartCapsuleBridge.java",
            "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SmartCapsulePayloadFactory.java",
            "modules/core-model/src/main/kotlin/io/github/superisland/model/SmartCapsuleAttestation.kt",
            "app/src/main/kotlin/io/github/superisland/SmartCapsuleAttestationKeyStore.kt",
        ).forEach { retiredPath ->
            assertFalse("Retired transport must stay deleted: $retiredPath", Files.exists(projectPath(retiredPath)))
        }
    }

    @Test
    fun smartCapsuleTestSourceKeepsOrdinaryLifecycleSeparateFromProgressExclusion() {
        val source =
            sourceFile(
                "samples/test-source/src/main/kotlin/io/github/superisland/testsource/TestSourceReceiver.kt",
            ).readText()
        val manifest = sourceFile("samples/test-source/src/main/AndroidManifest.xml").readText()
        val ordinaryBuilder =
            source.substringAfter("fun buildOrdinary(")
                .substringBefore("private fun testAvatar(")

        listOf(
            "POST_ORDINARY",
            "UPDATE_ORDINARY",
            "CANCEL_ORDINARY",
            "NOTIFICATION_ACTION_UPDATE_ORDINARY",
            "NOTIFICATION_ACTION_CANCEL_ORDINARY",
        ).forEach { action ->
            assertTrue("The ordinary lifecycle must expose $action", action in source && action in manifest)
        }
        assertTrue(
            "The ordinary lifecycle must use its own notification Channel",
            "smart_capsule_test_ordinary" in source,
        )
        listOf("setProgress(", "CATEGORY_PROGRESS", "setOngoing(true)", "miui.focus.").forEach { forbidden ->
            assertFalse("The ordinary notification must not contain $forbidden", forbidden in ordinaryBuilder)
        }
        assertTrue(
            "The original progress notification must remain a negative policy test",
            "setProgress(PROGRESS_MAX, progress, false)" in source &&
                "setCategory(Notification.CATEGORY_PROGRESS)" in source,
        )
    }

    @Test
    fun runtimeProviderRequiresThePrivilegedSystemUiCaller() {
        val runtime =
            sourceFile("app/src/main/kotlin/io/github/superisland/SmartCapsuleRuntimeStatus.kt")
                .readText()
        val channels =
            sourceFile("app/src/main/kotlin/io/github/superisland/SmartCapsuleChannelCatalog.kt")
                .readText()
        val provider =
            sourceFile("app/src/main/kotlin/io/github/superisland/SmartCapsuleReportProvider.kt")
                .readText()
        val manifest = sourceFile("app/src/main/AndroidManifest.xml").readText()
        val bridge =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiFocusSupportBridge.java",
            ).readText()
        val module =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SuperIslandXposedModule.java",
            ).readText()
        val configBridge =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiSmartCapsuleConfigBridge.java",
            ).readText()
        val reportPolicy =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/ModuleReportDeliveryPolicy.java",
            ).readText()
        val screenRecordingBridge =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiScreenRecordingRootBridge.java",
            ).readText()

        listOf(
            ".SmartCapsuleReportProvider",
            "android:authorities=\"io.github.superisland.smartcapsule.reports\"",
            "android:permission=\"android.permission.STATUS_BAR_SERVICE\"",
            "android:grantUriPermissions=\"false\"",
        ).forEach { requiredManifestToken ->
            assertTrue("Manifest provider contract must include $requiredManifestToken", requiredManifestToken in manifest)
        }
        assertTrue("Provider call must enforce STATUS_BAR_SERVICE itself", "enforceCallingPermission(" in provider)
        assertTrue("Provider call must verify the Binder caller uid", "Binder.getCallingUid()" in provider)
        assertTrue("Provider call must resolve packages for the caller uid", "getPackagesForUid(senderUid)" in provider)
        assertTrue("Provider call must require the SystemUI package", "SYSTEM_UI_PACKAGE !in senderPackages" in provider)
        assertTrue("Provider call must reject cross-user reports", "getUserHandleForUid(senderUid)" in provider)
        assertTrue("Channel payloads must continue validating their Android user", "EXTRA_CHANNEL_USER_ID" in channels)
        assertTrue("Runtime handshake and Provider writes must share one process lock", "RUNTIME_STORE_LOCK" in runtime)
        assertTrue("Runtime write paths must synchronize on the process lock", "synchronized(RUNTIME_STORE_LOCK)" in runtime)
        assertTrue("SystemUI reports must use provider IPC", "getContentResolver().call(" in bridge)
        assertTrue("Runtime reports must use the runtime provider method", "METHOD_REPORT_RUNTIME" in configBridge)
        assertTrue("Channel reports must use the Channel provider method", "METHOD_REPORT_CHANNELS" in bridge)
        assertTrue("Provider IPC must stay off the SystemUI main thread", "SuperIslandFocusReports" in bridge)
        assertTrue("Runtime IPC must use a bounded queue", "new ArrayBlockingQueue<>(32)" in bridge)
        assertTrue("Config handshakes must trigger a fresh mapper report", "ACTION_RELOAD_CONFIG" in configBridge)
        assertTrue(
            "Config handshakes must reload mapper rules before reporting runtime state",
            configBridge.indexOf("HyperIslandLocalNotificationAdapter.reloadRules();") in
                0 until configBridge.indexOf("reportCurrentRuntime(context, logger);"),
        )
        assertFalse(
            "A rejected config must not suppress the independent runtime reply",
            "if (acceptance == null) return;" in configBridge,
        )
        assertTrue(
            "Plugin lifecycle must feed the process-level runtime reporter",
            "SystemUiSmartCapsuleConfigBridge.focusPluginActivated(pluginEpoch);" in bridge &&
                "SystemUiSmartCapsuleConfigBridge.focusPluginDeactivated(pluginEpoch, reason);" in bridge,
        )
        assertTrue(
            "SystemUI config reload must leave its process main thread",
            "RELOAD_EXECUTOR.execute" in configBridge,
        )
        assertTrue(
            "SystemUI config acceptance must not depend on the optional Focus plugin bridge",
            "SystemUiSmartCapsuleConfigBridge.register" in module,
        )
        assertTrue("Capability must reflect mapper installation", "HyperIslandLocalNotificationAdapter.isInstalled()" in configBridge)
        assertTrue("Provider IPC must use the plugin's host SystemUI Context", "getSysuiContext" in module)
        assertTrue("Host Context must match the SystemUI process uid", "context.getApplicationInfo().uid" in module)
        assertTrue(
            "Host reports must not revive a force-stopped module app",
            "ApplicationInfo.FLAG_STOPPED" in reportPolicy &&
                "ModuleReportDeliveryPolicy.canDeliver(context)" in configBridge &&
                "ModuleReportDeliveryPolicy.canDeliver(runtimeContext)" in bridge &&
                "ModuleReportDeliveryPolicy.canDeliver(context)" in screenRecordingBridge &&
                "ModuleReportDeliveryPolicy.canDeliver(context)" in module,
        )
    }

    @Test
    fun appVisibilityCoversInstalledApplicationsWithTheMiuiPermissionGate() {
        val manifest = sourceFile("app/src/main/AndroidManifest.xml").readText()
        val directory = sourceFile("app/src/main/kotlin/io/github/superisland/SmartCapsuleAppDirectory.kt").readText()
        val activity = sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val stateOwner =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/SmartCapsuleDashboardStateOwner.kt",
            ).readText()
        val mutationQueue =
            sourceFile("app/src/main/kotlin/io/github/superisland/SmartCapsuleConfigMutationQueue.kt").readText()

        assertTrue("Android package visibility must cover installed applications", "android.permission.QUERY_ALL_PACKAGES" in manifest)
        assertTrue("HyperOS must expose its installed-app directory", "com.android.permission.GET_INSTALLED_APPS" in manifest)
        assertTrue("The primary directory must enumerate PackageInfo-backed applications", "getInstalledPackages" in directory)
        assertTrue("Application labels must work for service-only packages", "getApplicationLabel" in directory)
        assertTrue("LauncherApps must remain as the denied-permission fallback", "loadLauncherApplications" in directory)
        assertTrue("System classification must use ApplicationInfo flags", "ApplicationInfo.FLAG_SYSTEM" in directory)
        assertTrue("Updated system applications must remain classified as system apps", "FLAG_UPDATED_SYSTEM_APP" in directory)
        assertTrue("The app list must detect the optional HyperOS permission gate", "needsMiuiInstalledAppsPermission" in directory)
        assertTrue("Entering the app page must use the activity-result permission contract", "appListPermissionLauncher" in activity)
        assertTrue("A denied permission must not repeat during one visit", "appListPermissionRequested" in activity)
        assertTrue(
            "App rule mutations must use one process-wide ordered queue",
            "SmartCapsuleConfigMutationQueue.submit(configStore, transform)" in stateOwner &&
                "ThreadPoolExecutor" in mutationQueue &&
                "ArrayBlockingQueue" in mutationQueue &&
                "AbortPolicy" in mutationQueue &&
                "RejectedExecutionException" in mutationQueue,
        )
        assertTrue(
            "Navigation destinations must share one root-stable Smart Capsule owner",
            "val smartCapsuleStateOwner" in activity &&
                "SmartCapsuleDashboardStateOwner" in activity &&
                "stateOwner = smartCapsuleStateOwner" in activity,
        )
        assertTrue(
            "The UI must wait for both consumer acknowledgements instead of trusting publication",
            "acceptanceStore.observe(::requestConfigReload)" in stateOwner &&
                "acceptanceStore.isAccepted(snapshot)" in stateOwner &&
                "configAwaitingConsumers" in activity,
        )
        assertTrue(
            "Config decoding and acceptance digest work must stay off the main thread",
            "withContext(Dispatchers.IO)" in stateOwner &&
                "configStore.load()" in stateOwner &&
                "Channel.CONFLATED" in stateOwner,
        )
        assertTrue(
            "APPS must reuse one root-owned background-built directory UI model",
            "withContext(Dispatchers.Default)" in stateOwner &&
                "buildSmartCapsuleDirectoryUiModel(" in stateOwner &&
                "val appOptions = dashboardState.appOptions" in activity &&
                "SmartCapsuleDirectoryUiModel" in directory,
        )
    }

    @Test
    fun channelCatalogUsesAnOptionalApiCapability() {
        val catalog = sourceFile("app/src/main/kotlin/io/github/superisland/SmartCapsuleChannelCatalog.kt").readText()
        val module =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SuperIslandXposedModule.java",
            ).readText()
        val bridge =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiFocusSupportBridge.java",
            ).readText()

        assertFalse("Channel discovery must not parse notification_policy.xml", "notification_policy.xml" in catalog)
        assertTrue("Hidden Channel methods must be probed separately", "resolveChannelCatalogMethods" in module)
        assertTrue("A signature mismatch must disable only Channel catalog", "ChannelCatalogMethods.unavailable()" in module)
        assertTrue("The bridge must gate Channel requests on the optional capability", "!channelCatalogAvailable()" in bridge)
        assertTrue("Cross-user package lookup must use a public SystemUI API", "LauncherApps service" in bridge)
        assertFalse("UserHandle.of is unavailable in production SDK stubs", "UserHandle.of(" in bridge)
        assertTrue("Channel reports must enter the shared report provider", "SmartCapsuleChannelCatalogReportHandler" in catalog)
        assertTrue("Channel sender must validate every ID", "ChannelSelection.isValidChannelId" in bridge)
        assertTrue("Channel names must use code-point-safe bounds", "result.length() + width > MAX_CHANNEL_NAME_CODE_UNITS" in bridge)
        assertTrue("Channel Binder work must not block runtime reports", "SuperIslandChannelQuery" in bridge)
    }

}
