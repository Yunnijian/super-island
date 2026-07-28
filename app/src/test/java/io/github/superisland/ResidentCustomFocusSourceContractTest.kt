package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ResidentCustomFocusSourceContractTest {
    @Test
    fun residentCustomFocusUsesTheExclusiveXiaomiRemoteViewsProtocol() {
        val source =
            sourceFile(
                "modules/publisher-focus/src/main/kotlin/io/github/superisland/publisher/focus/FocusNotificationPublisher.kt",
            ).readText()
        val buildNotification = kotlinFunction(source, "buildNotification")
        val customPayload = kotlinFunction(source, "customFocusParamJson")

        listOf(
            "miui.focus.param.custom",
            "miui.focus.rv",
            "miui.focus.rvNight",
            "miui.focus.rv.island.expand",
            "miui.focus.ticker",
            "miui.targetPkg",
        ).forEach { key ->
            assertTrue("The custom resident protocol must declare $key", key in source)
        }
        listOf(
            "EXTRA_FOCUS_CUSTOM_PARAM",
            "EXTRA_FOCUS_REMOTE_VIEW",
            "EXTRA_FOCUS_REMOTE_VIEW_NIGHT",
            "EXTRA_FOCUS_ISLAND_EXPANDED_VIEW",
        ).forEach { extra ->
            assertTrue("The custom branch must write $extra", extra in buildNotification)
        }
        assertTrue(
            "The standard payload must be selected only when no custom layouts are supplied",
            "if (customRemoteViews == null)" in buildNotification,
        )
        assertTrue("The custom payload must keep the collapsed island model", "\"param_island\"" in customPayload)
        assertFalse("Custom Focus JSON must not be wrapped in param_v2", "\"param_v2\"" in customPayload)
        assertFalse("Custom Focus must not fall back to the one-line system template", "iconTextInfo" in customPayload)
        listOf(
            "\"enableFloat\", false",
            "\"islandFirstFloat\", false",
            "\"updatable\", true",
            "\"reopen\", FOCUS_REOPEN_CLOSE",
        ).forEach { field ->
            assertTrue("The resident custom payload must retain $field", field in customPayload)
        }
    }

    @Test
    fun systemUiHostBuildsModuleOwnedLayoutsAndKeepsANativeFallback() {
        val host =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val remoteViewsFactory = javaMethod(host, "residentFocusRemoteViews")
        val singleViewFactory = javaMethod(host, "residentFocusRemoteView")
        val actionResolver = javaMethod(host, "resolveExpandedActions")
        val singleActionResolver = javaMethod(host, "resolveExpandedAction")
        val iconResolver = javaMethod(host, "applicationIcon")
        val publish = javaMethod(host, "publishCurrentSnapshot", "Context context, boolean refreshFanMetric")

        listOf("focus_resident_expanded", "focus_resident_expanded_with_actions").forEach { layout ->
            assertTrue("The host must resolve the module-owned $layout", layout in remoteViewsFactory)
        }
        assertTrue(
            "Only successfully resolved actions may select the four-line action layout",
            "boolean hasActions = !actions.isEmpty()" in remoteViewsFactory,
        )
        assertTrue(
            "RemoteViews must use the installed module package, not com.android.systemui resources",
            "new RemoteViews(MODULE_PACKAGE, layoutId)" in singleViewFactory,
        )
        assertTrue("The rendered template text must be assigned to the RemoteViews body", "setTextViewText" in singleViewFactory)
        assertTrue("Resolved actions must bind their app icon", "setImageViewIcon" in singleViewFactory)
        assertTrue("Resolved buttons must receive their PendingIntent", "setOnClickPendingIntent" in singleViewFactory)
        assertTrue("Unresolved button slots must stay hidden", "setViewVisibility(buttonId, View.GONE)" in singleViewFactory)
        assertTrue(
            "The host must use the shared strict action codec",
            "ResidentExpandedActionCodec.decodeOrEmpty" in actionResolver,
        )
        assertTrue(
            "Refresh and settings must use their catalog icons while app targets use their resolved package",
            "iconPackage = MODULE_PACKAGE" in singleActionResolver &&
                "private static final String SETTINGS_PACKAGE = \"com.android.settings\"" in host &&
                Regex("iconPackage = SETTINGS_PACKAGE").findAll(singleActionResolver).count() == 2 &&
                Regex("iconPackage = explicitTargetPackage\\(").findAll(singleActionResolver).count() == 2,
        )
        assertTrue(
            "A missing app icon must fail closed before the action is exposed",
            "Icon appIcon = applicationIcon(context, iconPackage)" in singleActionResolver &&
                "if (appIcon == null) return null" in singleActionResolver,
        )
        assertTrue(
            "App icons must be package-owned resource Icons from enabled installed apps",
            "getApplicationInfo" in iconResolver &&
                "applicationInfo.enabled" in iconResolver &&
                "applicationInfo.icon == 0" in iconResolver &&
                "Icon.createWithResource(applicationInfo.packageName, applicationInfo.icon)" in iconResolver,
        )
        assertTrue("The resident notification must render the real expanded text", "request.getText()" in publish)
        assertTrue(
            "Every resident publication must re-resolve configured targets",
            "resolveExpandedActions(context)" in publish,
        )
        assertTrue("The substitute target must remain package-scoped", "MODULE_PACKAGE" in publish)
        assertTrue(
            "A layout/resource failure must return null so the publisher can use native param_v2",
            "customFocusRemoteViewsUnavailable = true" in remoteViewsFactory && "return null" in remoteViewsFactory,
        )
        assertFalse(
            "The host must not construct a RemoteViews owned by SystemUI",
            "new RemoteViews(\"com.android.systemui\"" in host,
        )
    }

    @Test
    fun islandLayoutsKeepSixLinesWithoutActionsAndFourLinesWithUpToThreeButtons() {
        val plainDocument =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(sourceFile("app/src/main/res/layout/focus_resident_expanded.xml").toFile())
        val plainText = elementWithId(plainDocument.documentElement, "@+id/focus_resident_expanded_content")

        assertEquals("false", androidAttribute(plainText, "singleLine"))
        assertEquals("false", androidAttribute(plainText, "scrollHorizontally"))
        assertEquals("simple", androidAttribute(plainText, "breakStrategy"))
        assertEquals("none", androidAttribute(plainText, "hyphenationFrequency"))
        assertEquals("6", androidAttribute(plainText, "maxLines"))
        assertEquals("end", androidAttribute(plainText, "ellipsize"))
        assertEquals(
            "The six-line layout must not reserve action-row height",
            0,
            plainDocument.documentElement.getElementsByTagName("Button").length,
        )

        val actionDocument =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(sourceFile("app/src/main/res/layout/focus_resident_expanded_with_actions.xml").toFile())
        val actionText = elementWithId(actionDocument.documentElement, "@+id/focus_resident_expanded_content")
        val actions = elementWithId(actionDocument.documentElement, "@+id/focus_resident_expanded_actions")
        val slots =
            (1..3).map { index ->
                elementWithId(actionDocument.documentElement, "@+id/focus_resident_action_$index")
            }
        val icons =
            (1..3).map { index ->
                elementWithId(actionDocument.documentElement, "@+id/focus_resident_action_icon_$index")
            }
        val labels =
            (1..3).map { index ->
                elementWithId(actionDocument.documentElement, "@+id/focus_resident_action_label_$index")
            }

        assertEquals("4", androidAttribute(actionText, "maxLines"))
        assertEquals("end", androidAttribute(actionText, "ellipsize"))
        assertFalse("The real action row must not be statically hidden", androidAttribute(actions, "visibility") == "gone")
        assertTrue("Action slots must use RemoteViews-supported LinearLayouts", slots.all { it.tagName == "LinearLayout" })
        assertTrue("Every action slot must contain an ImageView", icons.all { it.tagName == "ImageView" })
        assertTrue("Every action slot must contain a TextView", labels.all { it.tagName == "TextView" })
        assertTrue("Action icons keep a stable 18dp square", icons.all {
            androidAttribute(it, "layout_width") == "18dp" && androidAttribute(it, "layout_height") == "18dp"
        })
        assertTrue("Action labels remain single-line and ellipsized", labels.all {
            androidAttribute(it, "maxLines") == "1" && androidAttribute(it, "ellipsize") == "end"
        })
        assertTrue(
            "Buttons start hidden until the host binds a successfully resolved action",
            slots.all { androidAttribute(it, "visibility") == "gone" },
        )
    }

    @Test
    fun expandedActionsAreExplicitImmutableAndFailClosed() {
        val host =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val registerReceiver = javaMethod(host, "registerHostReceiver")
        val refreshReceiver = blockDeclaration(host, Regex("private static final BroadcastReceiver REFRESH_RECEIVER"))
        val actionResolver = javaMethod(host, "resolveExpandedAction")
        val launcherResolver = javaMethod(host, "explicitLauncherIntent")
        val activityResolver = javaMethod(host, "explicitResolvedActivityIntent")
        val explicitIntent = javaMethod(host, "explicitActivityIntent")
        val safeActivity = javaMethod(host, "isSafeActivity")
        val publisher =
            sourceFile(
                "modules/publisher-focus/src/main/kotlin/io/github/superisland/publisher/focus/FocusNotificationPublisher.kt",
            ).readText()
        val residentBuilder = kotlinFunction(publisher, "buildSystemUiResidentNotification")

        assertTrue(
            "The in-SystemUI refresh receiver must be dynamically registered as private",
            "REFRESH_RECEIVER" in registerReceiver && "Context.RECEIVER_NOT_EXPORTED" in registerReceiver,
        )
        assertTrue("The refresh PendingIntent must be package scoped", ".setPackage(context.getPackageName())" in actionResolver)
        assertTrue("Every action PendingIntent must be immutable", "PendingIntent.FLAG_IMMUTABLE" in host)
        assertTrue("Stable action request codes must allow in-place updates", "PendingIntent.FLAG_UPDATE_CURRENT" in host)
        assertTrue("Refresh must publish immediately inside SystemUI", "publishCurrentSnapshot(context)" in refreshReceiver)

        listOf("Intent.ACTION_MAIN", "Intent.CATEGORY_LAUNCHER", ".setPackage(normalizedPackage)").forEach { token ->
            assertTrue("Launcher resolution must retain $token", token in launcherResolver)
        }
        assertTrue("Launcher targets must be enumerated on every publication", "queryIntentActivities" in launcherResolver)
        assertTrue("Only exported activities are accepted", "activityInfo.exported" in safeActivity)
        assertTrue("Disabled activities and apps are rejected", "activityInfo.enabled" in safeActivity && "applicationInfo.enabled" in safeActivity)
        assertTrue("Settings actions must be resolved before use", "resolveActivity" in activityResolver)
        assertTrue("Resolved activities must be pinned to a ComponentName", "setComponent(new ComponentName" in explicitIntent)
        assertTrue("Explicit components must be verified again", "isSameResolvedActivity" in launcherResolver && "isSameResolvedActivity" in activityResolver)
        listOf(
            "android.intent.action.POWER_USAGE_SUMMARY",
            "android.settings.NOTIFICATION_SETTINGS",
        ).forEach { action ->
            assertTrue("Only the approved settings action $action may be exposed", action in actionResolver)
        }
        listOf("Intent.parseUri", "Runtime.getRuntime", "ProcessBuilder", "su -c", "setData(").forEach { forbidden ->
            assertFalse("Expanded actions must not accept arbitrary execution through $forbidden", forbidden in host)
        }
        assertTrue("The resident card itself must remain non-clickable", "contentIntent = null" in residentBuilder)
    }

    @Test
    fun systemUiUsesTheSharedCurrentAndTemperatureFormattingContract() {
        val host =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val metricValue = javaMethod(host, "metricValue")

        assertTrue(
            "SystemUI current text must use the same formatter as the app",
            "ResidentMetricFormatter.currentMicroAmps(snapshot.currentMicroAmps)" in metricValue,
        )
        assertTrue(
            "SystemUI temperature text must preserve negative tenths through the shared formatter",
            "ResidentMetricFormatter.temperatureTenthsCelsius" in metricValue,
        )
        assertFalse(
            "SystemUI must not round negative tenths through an independent floating-point formatter",
            "String.format" in metricValue,
        )
    }

    private fun androidAttribute(element: Element, name: String): String =
        element.getAttributeNS(ANDROID_NAMESPACE, name)

    private fun elementWithId(root: Element, id: String): Element {
        if (androidAttribute(root, "id") == id) return root
        val descendants = root.getElementsByTagName("*")
        for (index in 0 until descendants.length) {
            val element = descendants.item(index) as Element
            if (androidAttribute(element, "id") == id) return element
        }
        error("Missing XML element $id")
    }

    private fun kotlinFunction(source: String, name: String): String =
        blockDeclaration(source, Regex("(?m)^\\s*(?:private\\s+)?fun\\s+${Regex.escape(name)}\\s*\\("))

    private fun javaMethod(
        source: String,
        name: String,
        signatureFragment: String? = null,
    ): String {
        val matches =
            Regex("(?m)^\\s*private\\s+static[^\\n]*\\b${Regex.escape(name)}\\s*\\(")
                .findAll(source)
                .toList()
        val match =
            matches.firstOrNull { candidate ->
                signatureFragment == null || signatureFragment in source.substring(candidate.range.first, source.indexOf('{', candidate.range.first))
            } ?: error("Missing Java method $name")
        return blockAt(source, match.range.first)
    }

    private fun blockDeclaration(source: String, declaration: Regex): String {
        val start = declaration.find(source)?.range?.first ?: error("Missing declaration ${declaration.pattern}")
        return blockAt(source, start)
    }

    private fun blockAt(source: String, start: Int): String {
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
        error("Unclosed declaration")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
