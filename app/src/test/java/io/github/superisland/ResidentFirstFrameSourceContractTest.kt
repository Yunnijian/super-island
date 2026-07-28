package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidentFirstFrameSourceContractTest {
    @Test
    fun residentSettingsRenderRealDestinationsOnTheFirstFrame() {
        val activity = sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val settings = kotlinFunction(activity, "ResidentMonitorSettings")

        assertFalse(
            "Resident settings must not replace their first frame with an empty directory shell",
            "AppDirectoryDetailScreen(" in settings || "groups = emptyList()" in settings,
        )
        assertFalse(
            "Background owner loading may refine the page, but must not gate destination rendering",
            "if (!contentReady || !dashboardState.loaded)" in settings ||
                "if (!dashboardState.loaded)" in settings,
        )
        assertTrue(
            "The configuration destination must render its real settings content immediately",
            "BatteryMonitorPage.CONFIGURATION ->" in settings &&
                "ResidentMonitorConfigurationScreen(" in settings,
        )
        assertTrue(
            "The expanded-content destination must render its real editor immediately",
            "BatteryMonitorPage.EXPANDED_CONTENT ->" in settings &&
                "ResidentExpandedContentScreen(" in settings,
        )
        assertTrue(
            "Both first-frame destinations must consume the root owner's already-valid config",
            "val residentConfig = dashboardState.config" in settings,
        )
        assertTrue(
            "The editor may use readiness only to protect persistence, not to hide its layout",
            "configReady = dashboardState.loaded" in settings,
        )
    }

    @Test
    fun residentOwnerExposesValidFallbackStateBeforeAsynchronousRefresh() {
        val owner =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ResidentMonitorDashboardStateOwner.kt",
            ).readText()

        assertTrue(
            "The first frame must have a valid cached or default configuration",
            "ResidentMonitorUiWarmCache.config() ?: ResidentMonitorConfig()" in owner,
        )
        assertTrue(
            "The first frame must have a valid runtime, cached, or empty battery snapshot",
            "BatteryMonitorRuntime.current().latestSnapshot" in owner &&
                "ResidentMonitorUiWarmCache.snapshot()" in owner &&
                "EMPTY_BATTERY_METRIC_SNAPSHOT" in owner,
        )
    }

    @Test
    fun rootPrewarmHydratesTheStableOwnerAndLateResultsCannotRollbackEdits() {
        val activity = sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val owner =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ResidentMonitorDashboardStateOwner.kt",
            ).readText()
        val editor =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/resident/ResidentExpandedContentScreen.kt",
            ).readText()

        assertTrue(
            "Root prewarm must publish through the already-created owner",
            "LaunchedEffect(residentMonitorStateOwner)" in activity &&
                "residentMonitorStateOwner.activate()" in activity,
        )
        assertTrue(
            "A slow activation must not restore a configuration older than a user save",
            "configGeneration" in owner &&
                "configGeneration.get() == requestedGeneration" in owner,
        )
        assertTrue(
            "An untouched editor must hydrate when the authoritative configuration arrives",
            "LaunchedEffect(initialConfig, configReady)" in editor &&
                "!draftDirty" in editor &&
                "baseConfig = initialConfig" in editor,
        )
        assertTrue(
            "The fallback editor must not save before repository hydration",
            "configReady && !draftSaved" in editor,
        )
    }

    private fun kotlinFunction(source: String, name: String): String {
        val start =
            Regex("""(?m)^private fun ${Regex.escape(name)}\s*\(""")
                .find(source)?.range?.first
                ?: error("Missing Kotlin function $name")
        val open = source.indexOf('{', start)
        require(open >= 0) { "Missing body for Kotlin function $name" }
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
        error("Unclosed Kotlin function $name")
    }

}
