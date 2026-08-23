package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPerformanceSourceContractTest {
    @Test
    fun productUiHasNoLegacyWorkModeSelector() {
        val activity = sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val screens = sourceFile("modules/ui-design-system/src/main/kotlin/io/github/superisland/design/Screens.kt").readText()
        val components = sourceFile("modules/ui-design-system/src/main/kotlin/io/github/superisland/design/Components.kt").readText()

        assertTrue("Root-only pages must share one explicit runtime label", "ROOT_MODE_LABEL" in activity)
        assertFalse("The Activity must not carry a selectable work-mode state", "WorkMode" in activity)
        assertFalse("The design system must not expose a mode selector", "ModeSelectionScreen" in screens)
        assertFalse("The design system must not expose mode options", "ModeOptionUi" in screens)
        assertFalse("The design system must not invite mode changes", "更改工作模式" in screens)
        assertFalse("The design system must not retain the legacy mode card", "AppModeCard" in components)
    }

    @Test
    fun navigationFamiliesUseRootStableOwnersAndDeferredSystemWork() {
        val activity = sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val resident =
            activity.substringAfter("private fun BatteryMonitor(")
                .substringBefore("private fun ResidentMetricKey.metricValue(")

        listOf(
            "SmartCapsuleDashboardStateOwner",
            "MediaIslandDashboardStateOwner",
            "ResidentMonitorDashboardStateOwner",
        ).forEach { owner ->
            assertTrue("$owner must be created above NavDisplay", "remember(context.applicationContext)" in activity && owner in activity)
            assertTrue("$owner must be disposed with the root host", "onDispose" in activity && "::close" in activity)
        }
        assertTrue("The first Activity resume must not duplicate startup sync", "resumeGeneration > FIRST_RESUME_GENERATION" in activity)
        assertFalse("The removed media island must not retain a page renderer", "private fun MediaIsland(" in activity)
        assertFalse("The removed media island must not retain media routes", "AppDestination.MEDIA" in activity)
        assertTrue("Current resident settings must leave the legacy state tree before it is built", resident.indexOf("ResidentMonitorSettings(") < resident.indexOf("val context = LocalContext.current"))
    }

    @Test
    fun smartCapsuleInitialRefreshPublishesTheListOnlyWhenComplete() {
        val activity = sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val owner = sourceFile("app/src/main/kotlin/io/github/superisland/SmartCapsuleDashboardStateOwner.kt").readText()
        val dashboard =
            activity.substringAfter("private fun SmartCapsuleDashboard(")
                .substringBefore("private fun MediaIsland(")

        assertTrue("Navigation must enter refresh state before pushing the app list", "smartCapsuleStateOwner.activateAppPage()" in activity)
        assertTrue("Permission probing must stay off the UI thread", "withContext(Dispatchers.IO)" in owner && "needsMiuiInstalledAppsPermission" in owner)
        assertTrue("The owner must expose a permission handoff instead of enumerating prematurely", "appListPermissionRequired" in owner && "continueAppPageAfterPermissionRequest" in owner)
        assertTrue("The first list must remain hidden until the complete projection is ready", "dashboardState.appPagePrepared) appOptions else emptyList()" in dashboard)
        assertTrue("Initial loading must end only after the projection is published", "completeInitialRefreshOnNextProjection" in owner)
    }

    @Test
    fun residentPrewarmIsSingleFlightAndBenchmarkVariantMatchesRelease() {
        val cache = sourceFile("app/src/main/kotlin/io/github/superisland/ResidentMonitorUiWarmCache.kt").readText()
        val appBuild = sourceFile("app/build.gradle.kts").readText()
        val benchmarkManifest = sourceFile("app/src/benchmark/AndroidManifest.xml").readText()

        assertTrue("Resident prewarm callers must share one in-flight task", "FutureTask<Snapshot>" in cache && "private var inFlight" in cache)
        assertTrue("The installable performance variant must inherit release", "create(\"benchmark\")" in appBuild && "initWith(getByName(\"release\"))" in appBuild)
        assertTrue("Multi-module benchmark resolution must use release fallbacks", "matchingFallbacks += listOf(\"release\")" in appBuild)
        assertTrue("Benchmark builds must be profileable by shell", "<profileable android:shell=\"true\"" in benchmarkManifest)
    }

    @Test
    fun releaseShrinkerKeepsTheLibxposedPackageCallback() {
        val appBuild = sourceFile("app/build.gradle.kts").readText()
        val appRules = sourceFile("app/proguard-rules.pro").readText()
        val hookConsumerRules = sourceFile("modules/hook-systemui/consumer-rules.pro").readText()
        val callback =
            "public void onPackageLoaded(" +
                "io.github.libxposed.api.XposedModuleInterface\$PackageLoadedParam);"
        val hookerBoundary =
            "class * implements io.github.libxposed.api.XposedInterface\$Hooker"
        val hookerCallback =
            "public java.lang.Object intercept(" +
                "io.github.libxposed.api.XposedInterface\$Chain);"

        assertTrue(
            "The final APK shrinker must resolve the libxposed Hooker ABI",
            "compileOnly(libs.libxposed.api)" in appBuild,
        )
        assertTrue("The final APK shrinker must retain the libxposed package callback", callback in appRules)
        assertTrue("The hook library must export the same callback keep contract", callback in hookConsumerRules)
        assertTrue("The final APK must retain every externally invoked Hooker", hookerBoundary in appRules && hookerCallback in appRules)
        assertTrue("The hook library must export the Hooker keep contract", hookerBoundary in hookConsumerRules && hookerCallback in hookConsumerRules)
    }

}
