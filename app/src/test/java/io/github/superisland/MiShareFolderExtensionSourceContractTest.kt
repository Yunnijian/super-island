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

class MiShareFolderExtensionSourceContractTest {
    @Test
    fun directoryAndRouteExposeOneRealMiShareFolderExtension() {
        val entry =
            extensionsDirectoryGroups()
                .flatMap { group -> group.entries }
                .single { item -> item.id == "mishare_folder" }

        assertEquals("小米互传文件夹", entry.title)
        assertEquals(AppDestination.EXTENSIONS, AppDestination.EXTENSION_MISHARE_FOLDER.backDestination)
        assertTrue(AppDestination.EXTENSION_MISHARE_FOLDER.isDetail)
    }

    @Test
    fun hookIsScopedToTheVerifiedIntentBuilderAndFailsClosed() {
        val scope = sourceFile("app/src/main/resources/META-INF/xposed/scope.list").readText()
        val module =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SuperIslandXposedModule.java",
            ).readText()
        val hook =
            sourceFile(
                "modules/hook-systemui/src/main/kotlin/io/github/superisland/hook/systemui/MiShareFolderRedirectHook.kt",
            ).readText()
        val resolver =
            sourceFile(
                "modules/hook-systemui/src/main/kotlin/io/github/superisland/hook/systemui/MiShareTargetResolver.kt",
            ).readText()
        val cache =
            sourceFile(
                "modules/hook-systemui/src/main/kotlin/io/github/superisland/hook/systemui/MiShareDescriptorCache.kt",
            ).readText()
        val hookBuild = sourceFile("modules/hook-systemui/build.gradle.kts").readText()
        val dexKitNotice =
            sourceFile(
                "modules/hook-systemui/src/main/resources/META-INF/NOTICE-DexKit.txt",
            ).readText()
        val sync = sourceFile("app/src/main/kotlin/io/github/superisland/MiShareFolderExtensionConfigSync.kt").readText()
        val contract =
            sourceFile(
                "modules/core-model/src/main/kotlin/io/github/superisland/model/MiShareFolderRedirectConfig.kt",
            ).readText()
        val screen =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/extensions/MiShareFolderExtensionScreen.kt",
            ).readText()
        val stateOwner =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/extensions/MiShareFolderExtensionUiStateOwner.kt",
            ).readText()
        val material = sourceFile("app/src/main/kotlin/io/github/superisland/ui/material/MaterialPages.kt").readText()

        assertEquals(
            setOf("com.android.systemui", "com.xiaomi.xmsf", "com.miui.mishare.connectivity"),
            scope.lineSequence().filter(String::isNotBlank).toSet(),
        )
        assertTrue("Mi Share must have an isolated module branch", "MISHARE_PACKAGE.equals" in module)
        assertTrue("Only the audited MIShare Intent builder may be hooked", "com.miui.mishare.view.d" in resolver)
        assertTrue("Only the audited method signature may be hooked", "TARGET_METHOD = \"g\"" in resolver)
        assertTrue("DexKit must keep a bounded package search", "searchPackages(SEARCH_PACKAGE)" in resolver)
        assertTrue("DexKit must require all audited string anchors", "usingEqStrings" in resolver)
        assertTrue("DexKit ambiguity must fail closed", "requireUniqueDescriptor" in resolver)
        assertTrue("DexKit native loading must stay inside the bounded scanner", "System.loadLibrary(\"dexkit\")" in resolver)
        assertFalse("The resolver must not scan the full ClassLoader", "DexKitBridge.create(classLoader" in resolver)
        assertFalse("The known DexKit 2.2.0 Class matcher bug must be avoided", "ClassMatcher(" in resolver)
        assertTrue("Descriptor cache writes must use AtomicFile", "AtomicFile(" in cache)
        assertFalse("Failures must not be persisted in the descriptor cache", "failure" in cache.lowercase())
        assertTrue("Release must hard-disable the forced scan switch", "FORCE_MISHARE_DEXKIT_SCAN\", \"false\"" in hookBuild)
        assertTrue(
            "The pinned DexKit AAR hash must be recorded",
            "cf4488da9f721750ce5ff4311e88356cbfcb1d4444c2b519b12d8bdb5f33f2c6" in dexKitNotice,
        )
        assertTrue("The OEM method must run before any redirect", "val original = chain.proceed()" in hook)
        assertTrue("Unknown OEM intents must retain their original behavior", "return original" in hook)
        assertTrue("The original action must be verified", "isMiShareFolderIntentAction" in hook)
        assertTrue("The receive path must be constrained", "isCanonicalReceiveDirectory" in hook)
        assertTrue("MT must be capability-probed before redirect", "resolveActivity" in hook)
        assertTrue(
            "The toggle must use a separate RemotePreferences channel",
            "MiShareFolderRedirectContract.REMOTE_PREFERENCES" in sync &&
                "SuperIslandMiShareFolder" in contract,
        )
        assertTrue("The detail page must use the adaptive feature controls", "AppFeatureMasterSwitch" in screen)
        assertTrue("Heavy state must wait for the KernelSU transition contract", "rememberContentReady()" in screen)
        assertTrue("Deferred state preparation must be transition-gated", "if (contentReady)" in screen)
        assertFalse("The composable first frame must not read preferences", "store.load()" in screen)
        assertFalse("The composable first frame must not query PackageManager", "getPackageInfo" in screen)
        assertTrue("Mi Share UI state must be cached for the process", "object MiShareFolderExtensionUiStateOwner" in stateOwner)
        assertTrue("Preference and package checks must run off the main thread", "Dispatchers.IO" in stateOwner)
        assertTrue("Repeated entries must share one preparation", "prepareInFlight" in stateOwner)
        assertTrue("The observer's initial snapshot must replace a duplicate load", "sole initial preference snapshot" in stateOwner)
        assertFalse("The state owner must not duplicate observe's initial load", ".load()" in stateOwner)
        assertTrue("Toggle writes must be FIFO", "Channel<SaveRequest>" in stateOwner)
        assertTrue("A failed latest write must roll the switch back", "request.generation == latestRequestGeneration" in stateOwner)
        assertTrue("Material must route the same entry id", "\"mishare_folder\" -> AppDestination.EXTENSION_MISHARE_FOLDER" in material)
        assertFalse("The extension must not invoke a shell", "Runtime.getRuntime" in hook)
        assertFalse("The extension must not accept arbitrary user paths", "TextField" in screen)
    }

}
