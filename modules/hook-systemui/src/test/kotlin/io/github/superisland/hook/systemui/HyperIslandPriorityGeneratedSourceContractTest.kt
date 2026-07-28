package io.github.superisland.hook.systemui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HyperIslandPriorityGeneratedSourceContractTest {
    @Test
    fun generatedSourcesCarryTypedPriorityThroughBothRenderers() {
        val generatedRoot =
            sourceFile(
                "modules/hook-systemui/build/generated/source/hyperisland/io/github/hyperisland",
            )
        val notificationData =
            generatedRoot.resolve("xposed/template/core/models/NotifData.kt").readText()
        val viewModel =
            generatedRoot.resolve("xposed/template/core/models/IslandViewModel.kt").readText()
        val template =
            generatedRoot.resolve("xposed/template/NotificationIslandNotification.kt").readText()
        val buttons =
            generatedRoot.resolve(
                "xposed/template/renderer/image_text_with_buttons/ImageTextWithButtonsRenderer.kt",
            ).readText()
        val progress =
            generatedRoot.resolve(
                "xposed/template/renderer/image_text_with_progress/ImageTextWithProgressRenderer.kt",
            ).readText()
        val sharedRenderer =
            generatedRoot.resolve("xposed/template/renderer/IslandRenderer.kt").readText()

        listOf(notificationData, viewModel).forEach { modelSource ->
            assertTrue("val islandPriority: IslandPriority = IslandPriority.LOW" in modelSource)
            assertTrue("val islandEnabled: Boolean = true" in modelSource)
        }
        assertTrue("islandPriority = data.islandPriority" in template)
        assertTrue("islandEnabled = data.islandEnabled" in template)
        assertTrue("if (vm.islandEnabled)" in buttons)
        listOf(buttons, progress).forEach { rendererSource ->
            assertTrue("priority = vm.islandPriority.wireValue" in rendererSource)
            assertTrue("jsonParam = forceIslandOrderFalse(jsonParam)" in rendererSource)
            assertTrue(
                "Focus-only output must explicitly remove the kit's synthesized island object",
                "if (!vm.islandEnabled) jsonParam = removeIslandParam(jsonParam)" in rendererSource,
            )
        }
        assertTrue("paramV2.remove(\"param_island\")" in sharedRenderer)
        assertTrue("check(!paramV2.has(\"param_island\"))" in sharedRenderer)
        assertTrue(
            "The progress renderer must not recreate param_island after focus-only removal",
            "if (vm.islandEnabled) jsonParam = injectHighlightColor(jsonParam, vm.highlightColor)" in progress,
        )
    }

    @Test
    fun generatedJsonAlwaysOverwritesIslandOrderToFalse() {
        val renderer =
            sourceFile(
                "modules/hook-systemui/build/generated/source/hyperisland/io/github/hyperisland/" +
                    "xposed/template/renderer/IslandRenderer.kt",
            ).readText()

        assertTrue("paramIsland.put(\"islandOrder\", false)" in renderer)
        assertFalse("paramIsland.put(\"islandOrder\", true)" in renderer)
    }

}
