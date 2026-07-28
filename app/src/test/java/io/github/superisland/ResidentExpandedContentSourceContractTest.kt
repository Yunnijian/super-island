package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidentExpandedContentSourceContractTest {
    @Test
    fun actionFieldMigrationAndCorruptionFailClosedWithoutOverwritingInvalidCustomDrafts() {
        val store = sourceFile("app/src/main/kotlin/io/github/superisland/ResidentMonitorConfigStore.kt").readText()
        val sync = sourceFile("app/src/main/kotlin/io/github/superisland/ResidentIslandHostConfigSync.kt").readText()
        val contract =
            sourceFile(
                "modules/publisher-focus/src/main/kotlin/io/github/superisland/publisher/focus/SystemUiResidentIslandPublisher.kt",
            ).readText()

        assertTrue("The local resident schema must migrate to v5", "CURRENT_SCHEMA_VERSION = 5" in store)
        assertTrue("The shared host schema must migrate to v5", "HOST_SCHEMA_VERSION = 5" in contract)
        assertTrue(
            "v4 has no action field and must migrate to an empty list even if a stray value exists",
            "loadCurrent(includeExpandedActions = false)" in store &&
                "expandedActions = if (includeExpandedActions) expandedActions() else emptyList()" in store,
        )
        assertTrue("A mistyped preference value must fail closed", "runCatching { preferences.getString(KEY_EXPANDED_ACTIONS, null) }" in store)
        assertTrue("The complete action list must use one local field", "KEY_EXPANDED_ACTIONS = \"expanded-actions\"" in store)
        assertTrue("The complete action list must use one host field", "KEY_EXPANDED_ACTIONS = \"expanded_actions\"" in contract)
        assertTrue("The host mirror must use the strict shared codec", "ResidentExpandedActionCodec.encode(normalized.expandedActions)" in sync)

        val validation = store.indexOf("if (!config.hasValidCustomContent())")
        val persistence = store.indexOf("persist(normalized)", startIndex = validation.coerceAtLeast(0))
        assertTrue(
            "An invalid CUSTOM draft must be rejected before it can overwrite the last valid value",
            validation >= 0 && persistence > validation,
        )
        assertTrue(
            "An invalid inactive PRESET draft must retain the last valid stored custom template",
            "config.copy(expandedContentTemplate = load().expandedContentTemplate)" in store &&
                store.indexOf("config.copy(expandedContentTemplate = load().expandedContentTemplate)") < persistence,
        )
    }

    @Test
    fun storeAndRemoteMirrorPersistModeAndTemplateAsOneConfiguration() {
        val store = sourceFile("app/src/main/kotlin/io/github/superisland/ResidentMonitorConfigStore.kt").readText()
        val sync = sourceFile("app/src/main/kotlin/io/github/superisland/ResidentIslandHostConfigSync.kt").readText()
        val contract =
            sourceFile(
                "modules/publisher-focus/src/main/kotlin/io/github/superisland/publisher/focus/SystemUiResidentIslandPublisher.kt",
            ).readText()

        listOf(
            "putString(KEY_EXPANDED_CONTENT_MODE, config.expandedContentMode.name)",
            "putString(KEY_EXPANDED_CONTENT_TEMPLATE, config.expandedContentTemplate)",
            "putString(KEY_EXPANDED_ACTIONS, ResidentExpandedActionCodec.encode(config.expandedActions))",
            "KEY_EXPANDED_CONTENT_MODE",
            "KEY_EXPANDED_CONTENT_TEMPLATE",
            "KEY_EXPANDED_ACTIONS",
        ).forEach { token ->
            assertTrue("The app repository must persist and observe $token", token in store)
        }
        listOf(
            "normalized.expandedContentMode.name",
            "normalized.expandedContentTemplate",
            "ResidentExpandedActionCodec.encode(normalized.expandedActions)",
        ).forEach { token ->
            assertTrue("The RemotePreferences mirror must use the same normalized config for $token", token in sync)
        }
        assertTrue("The host mode key must stay stable", "expanded_content_mode" in contract)
        assertTrue("The host template key must stay stable", "expanded_content_template" in contract)
        assertTrue("The host action key must stay stable", "expanded_actions" in contract)
    }

    @Test
    fun sharedEditorOwnsOneDraftAndCommitsAtMostOnce() {
        val screen =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/resident/ResidentExpandedContentScreen.kt",
            ).readText()
        val saveGuard = screen.indexOf("draftSaved = true")
        val persistenceCrossing = screen.indexOf("onSave(", startIndex = saveGuard)

        assertTrue("The shared screen must own a retained TextFieldValue draft", "TextFieldValue.Saver" in screen)
        assertTrue("The shared screen must own the cross-skin save guard", "var draftSaved by rememberSaveable" in screen)
        assertTrue("The duplicate-tap guard must flip before persistence", saveGuard >= 0 && persistenceCrossing > saveGuard)
        assertTrue(
            "Only a hydrated, valid unsaved draft may save",
            "if (configReady && canSave && !draftSaved)" in screen,
        )
        val templateActions =
            screen.substring(screen.indexOf("ResidentExpandedContentEditorActions("))
        val saveAction =
            templateActions.substring(templateActions.indexOf("save = {")).substringBefore("back = ::navigateBack")
        assertFalse(
            "Save must stay on the editor; only nested/top back may navigate out",
            "onBack()" in saveAction,
        )
        assertTrue("The shared state owner must dispatch both skins", "UiMode.Miuix" in screen && "UiMode.Material" in screen)
        assertTrue(
            "The three fixed slots must render directly in the expanded-content page",
            "actionSlots = slots" in screen && "openActionSlot" in screen,
        )
        assertTrue(
            "Slot and target pages must use typed Navigation3 entries so both skins receive native transitions",
            "NavDisplay(" in screen &&
                "ResidentExpandedRoute.SlotEditor" in screen &&
                "ResidentExpandedRoute.TargetPicker" in screen,
        )
        assertFalse(
            "The retired standalone shortcut-button list must not remain in the flow",
            "ResidentExpandedSubPage" in screen || "openExpandedActions" in screen,
        )
        assertTrue(
            "Known shortcuts must stay allowlisted",
            "OPEN_KNOWN_SHORTCUT" in screen && "ResidentKnownShortcut" in screen,
        )
        assertFalse(
            "Editing callbacks must not auto-save like KernelSU's unrelated profile template editor",
            Regex("""changeEditorValue\s*=\s*\{[\s\S]*?onSave\(""")
                .containsMatchIn(screen.substringBefore("insertToken =")),
        )
    }

    @Test
    fun bothSkinsConsumeTheSameStateAndUseTheirNativeTextFields() {
        val screen =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/resident/ResidentExpandedContentScreen.kt",
            ).readText()
        val miuix =
            sourceFile(
                "modules/ui-design-system/src/main/kotlin/io/github/superisland/design/ResidentExpandedContentEditor.kt",
            ).readText()
        val material =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/material/MaterialResidentExpandedContentScreen.kt",
            ).readText()
        val host =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val resolver =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/ResidentKnownShortcutResolver.java",
            ).readText()
        val actionModel =
            sourceFile(
                "modules/core-model/src/main/kotlin/io/github/superisland/model/ResidentExpandedAction.kt",
            ).readText()
        val search =
            sourceFile(
                "modules/ui-design-system/src/main/kotlin/io/github/superisland/design/SmartCapsuleAppSearch.kt",
            ).readText()
        val miuixTargetPicker = miuix.substring(miuix.indexOf("fun ResidentExpandedTargetPickerMiuix"))
        val materialTargetPicker =
            material.substring(material.indexOf("internal fun MaterialResidentExpandedTargetPicker"))

        listOf("ResidentExpandedContentEditorUiState", "ResidentExpandedContentEditorActions").forEach { sharedType ->
            assertTrue("Miuix must consume $sharedType", sharedType in miuix)
            assertTrue("Material must consume $sharedType", sharedType in material)
        }
        listOf(
            "selectOption",
            "changeEditorValue",
            "insertToken",
            "openActionSlot",
            "restoreDefault",
            "save",
            "back",
        ).forEach { action ->
            assertTrue("Miuix must expose $action", action in miuix)
            assertTrue("Material must expose $action", action in material)
        }
        listOf(
            "ResidentExpandedActionSlotEditorMiuix",
            "ResidentExpandedTargetPickerMiuix",
            "展开卡片快捷按钮",
        ).forEach { token ->
            assertTrue("Miuix nested action UI must include $token", token in miuix)
        }
        listOf(
            "MaterialResidentExpandedActionSlotEditor",
            "MaterialResidentExpandedTargetPicker",
            "展开卡片快捷按钮",
        ).forEach { token ->
            assertTrue("Material nested action UI must include $token", token in material)
        }
        assertFalse("Inline add-button dialog path is retired", "addExpandedAction" in miuix)
        assertTrue("Miuix must use its TextFieldValue-capable native control", "top.yukonga.miuix.kmp.basic.TextField" in miuix)
        assertTrue("Material must use native Material OutlinedTextField", "androidx.compose.material3.OutlinedTextField" in material)
        assertTrue("Miuix target list must be virtualized", "LazyColumn" in miuix)
        assertTrue("Material target list must be virtualized", "LazyColumn" in material)
        listOf("SearchStatus", "SearchBarFake", "SearchPager", "SearchBox").forEach { component ->
            assertTrue(
                "Miuix target picker must use the same KernelSU search component $component as the notification list",
                component in miuixTargetPicker,
            )
        }
        assertTrue(
            "Material target picker must use the same KernelSU SearchAppBar as the notification list",
            "SearchAppBar(" in materialTargetPicker,
        )
        assertFalse(
            "Miuix target picker must not fall back to an inline search text field",
            "TextField(" in miuixTargetPicker,
        )
        assertFalse(
            "Material target picker must not fall back to an inline search text field",
            "OutlinedTextField(" in materialTargetPicker,
        )
        assertTrue(
            "Both skins must share background app filtering",
            "rememberResidentExpandedAppSearchResults" in miuixTargetPicker &&
                "rememberResidentExpandedAppSearchResults" in materialTargetPicker &&
                "delay(SEARCH_DEBOUNCE_MILLIS)" in search &&
                "withContext(Dispatchers.Default)" in search,
        )
        assertTrue(
            "The target picker must provide exactly the application and shortcut tabs",
            "tabs = listOf(\"应用\", \"快捷方式\")" in screen &&
                "listOf(\"第三方\", \"系统\", \"全部\")" !in screen,
        )
        assertTrue(
            "System-app visibility must use the same persisted menu setting as the Super Island app list",
            "SmartCapsuleAppListPreferences" in screen &&
                "toggleShowSystemApps" in screen &&
                "showSystemApps" in screen,
        )
        assertTrue("Miuix must use a native menu for the system-app filter", "OverlayListPopup" in miuix)
        assertTrue("Material must use a native menu for the system-app filter", "DropdownMenuPopup" in material)
        assertTrue(
            "Shortcut choices must preload their owning package icon off the UI thread",
            "shortcutPackageInfoById" in screen &&
                "PackageManager.PackageInfoFlags.of(0L)" in screen &&
                "withContext(Dispatchers.IO)" in screen,
        )
        assertTrue(
            "Miuix shortcut rows must receive their owning app icon",
            "shortcutIcon" in miuixTargetPicker && "shortcut.packageInfo" in screen,
        )
        assertTrue(
            "Material shortcut rows must render their owning package with AppIconImage",
            "shortcut.packageInfo" in materialTargetPicker && "AppIconImage(" in materialTargetPicker,
        )
        assertTrue(
            "Unavailable allowlisted shortcuts must fail closed before rendering",
            ".filter { choice -> choice.isAvailable(installedPackages) }" in screen,
        )
        assertFalse(
            "The shared state owner must not synchronously filter the launcher directory during composition",
            "launcherApps\n                                .asSequence()" in screen,
        )
        assertFalse("Miuix slot editor must not expose an action-type dropdown", "title = \"动作\"" in miuix)
        assertFalse("Material slot editor must not expose an action-type dropdown", "title = \"动作\"" in material)
        assertTrue("Material must retain KernelSU's editor scaffold", "ExpressiveScaffold" in material)
        assertTrue("Miuix must retain scroll haptics", "scrollEndHaptic()" in miuix)
        assertTrue("Miuix must retain native overscroll", "overScrollVertical()" in miuix)
        assertTrue("Host must resolve known shortcuts", "OPEN_KNOWN_SHORTCUT" in host)
        assertTrue("Shortcut resolver must fail closed on ambiguity", "Ambiguous" in resolver || "return null" in resolver)
        assertFalse("Shortcut path must not parse user Intent URIs", "Intent.parseUri" in resolver)
        listOf(
            "alipay_pay",
            "alipay_scan",
            "alipay_collect",
            "alipay_shortcut_settings",
            "wechat_pay",
            "wechat_scan",
            "wechat_my_qr_code",
        ).forEach { shortcutId ->
            assertTrue("Known shortcut $shortcutId must exist in the model", shortcutId in actionModel)
            assertTrue("Known shortcut $shortcutId must have a fixed host resolver", shortcutId in resolver)
        }
        assertTrue(
            "WeChat shortcuts must use the app-published package-scoped dispatch action",
            "com.tencent.mm.ui.ShortCutDispatchAction" in resolver &&
                "LauncherUI.Shortcut.LaunchType" in resolver &&
                ".setPackage(WECHAT_PACKAGE)" in resolver,
        )
        listOf(
            "launch_type_offline_wallet",
            "launch_type_scan_qrcode",
            "launch_type_my_qrcode",
        ).forEach { launchType ->
            assertTrue("WeChat must retain the published launch type $launchType", launchType in resolver)
        }
        listOf(
            "appId=20000056",
            "appId=10000007&sourceId=scan3dtouch",
            "appId=20000123",
            "appId=2060090000284409",
            "other-shortcuts.html",
        ).forEach { publishedSchemePart ->
            assertTrue(
                "Alipay must retain its published fixed shortcut scheme: $publishedSchemePart",
                publishedSchemePart in resolver,
            )
        }
        assertFalse(
            "Unavailable known shortcuts must hide instead of opening the owning app home page",
            "Intent.CATEGORY_LAUNCHER" in resolver || "Intent.ACTION_MAIN" in resolver,
        )
        assertFalse(
            "SystemUI must not target Alipay's unexported ShortcutManager bridge",
            "ShortcutsLauncherActivity" in resolver,
        )
        assertFalse(
            "Retired WeChat schemes must not win resolution and immediately finish",
            "weixin://wap/pay" in resolver || "weixin://scanqrcode" in resolver,
        )
        assertFalse(
            "Unexported WeChat implementation activities must not be targeted across packages",
            "WalletOfflineEntranceUI" in resolver || "BaseScanUI" in resolver,
        )
        assertFalse("Resident host must not set contentIntent for capsule jump", "contentIntent =" in host)
    }

    @Test
    fun configurationUsesASecondLevelRouteAndRetainedEntriesObserveSaves() {
        val main = sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val adaptive =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/adaptive/AdaptiveFeatureScreens.kt",
            ).readText()
        val miuix = sourceFile("modules/ui-design-system/src/main/kotlin/io/github/superisland/design/Screens.kt").readText()
        val material =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/material/MaterialFeatureScreens.kt",
            ).readText()

        assertTrue("The destination must render the real shared editor", "ResidentExpandedContentScreen(" in main)
        assertTrue("The configuration entry must navigate to the editor", "onOpenPage(BatteryMonitorPage.EXPANDED_CONTENT)" in main)
        assertTrue("Retained Navigation3 entries must subscribe to repository changes", "residentConfigStore.observe" in main)
        val store = sourceFile("app/src/main/kotlin/io/github/superisland/ResidentMonitorConfigStore.kt").readText()
        assertTrue("A resumed retained entry must receive the latest value immediately", "onChanged(load())" in store)
        listOf(adaptive, miuix, material).forEach { renderer ->
            assertTrue("Each renderer must expose the second-level action", "onOpenExpandedContent" in renderer)
            assertFalse("The outer entry must not repeat the selected preset", "expandedContentSummary" in renderer)
            assertFalse("The old immediate preset callback is retired", "onSelectExpandedContent" in renderer)
            assertFalse("The old configuration-page dropdown model is retired", "expandedContentOptions" in renderer)
        }
    }

    @Test
    fun systemUiFanDemandIsIsolatedByExpandedContentMode() {
        val host =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val method = javaMethod(host, "fanMetricRequested")
        val customBranch = method.indexOf("\"CUSTOM\".equals(expandedContentModeSetting())")
        val presetMetrics = method.indexOf("KEY_EXPANDED_METRICS")

        assertTrue("CUSTOM mode must be handled before reading preset metrics", customBranch >= 0 && presetMetrics > customBranch)
        assertTrue("CUSTOM fan demand must require a valid template", "validation.isValid()" in method)
        assertTrue("CUSTOM fan demand must inspect only the fan token", "contains(\"fan_rpm\")" in method)
        assertTrue("PRESET fan demand must still inspect expanded metrics", "FAN_RPM" in method.substring(presetMetrics))
    }

    private fun javaMethod(source: String, name: String): String {
        val start = Regex("""(?m)^\s*private\s+static[^\n]*\b${Regex.escape(name)}\s*\(""")
            .find(source)?.range?.first ?: error("Missing Java method $name")
        val open = source.indexOf('{', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        error("Unclosed Java method $name")
    }

}
