package io.github.superisland.hook.systemui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import io.github.superisland.model.SystemUiIslandAppearanceContract
import org.junit.Assert.assertFalse
import org.junit.Test

class ColorOsFluidCloudFailClosedSourceContractTest {
    @Test
    fun `failed projection runtime is absent from the SystemUI entry path`() {
        val module =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/" +
                    "SuperIslandXposedModule.java",
            ).readText()

        assertFalse("The failed runtime must not be installed", "ColorOsFluidCloudHook" in module)
        assertFalse(
            "SystemUI must not register the retired appearance reload bridge",
            "SystemUiIslandAppearanceConfigBridge" in module,
        )
        assertFalse(
            "The frozen runtime capability must remain unavailable",
            SystemUiIslandAppearanceContract.COLOR_OS_FLUID_CLOUD_RUNTIME_AVAILABLE,
        )
        val productionSources =
            sourceFilesUnder("modules/hook-systemui/src/main")
                .filter { path -> path.toString().endsWith(".kt") || path.toString().endsWith(".java") }
                .associateWith(Path::readText)
        listOf(
            "ColorOsFluidCloudHook",
            "ColorOsFluidCloudController",
            "SystemUiIslandAppearanceConfigBridge",
        ).forEach { retiredType ->
            assertFalse(
                "The frozen runtime type $retiredType must be absent from all production sources",
                productionSources.values.any { source -> retiredType in source },
            )
        }
    }

}
