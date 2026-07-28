package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperIslandDirectorySourceContractTest {
    @Test
    fun directorySkinsRenderSharedModelsWithoutLifecycleSideEffects() {
        val sources =
            listOf(
                "Miuix" to
                    sourceFile(
                        "app/src/main/kotlin/io/github/superisland/ui/superisland/SuperIslandMiuix.kt",
                    ).readText(),
                "Material" to
                    sourceFile(
                        "app/src/main/kotlin/io/github/superisland/ui/material/MaterialPages.kt",
                    ).readText().substringBefore("private fun MaterialDirectoryPage("),
            )

        sources.forEach { (skin, source) ->
            assertTrue(
                "$skin Super Island directory must consume the shared directory model",
                "superIslandDirectoryGroups()" in source,
            )
            listOf(
                "NotificationProxyController",
                "refreshMedia()",
                "resumeGeneration",
            ).forEach { forbiddenToken ->
                assertFalse(
                    "$skin Super Island directory must remain free of $forbiddenToken",
                    forbiddenToken in source,
                )
            }
        }
    }

}
