package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandAppearanceSourceContractTest {
    @Test
    fun localStorePersistsTheVersionedBooleanAndResetRestoresTheDomainDefault() {
        val store =
            sourceFile("app/src/main/kotlin/io/github/superisland/IslandAppearanceStore.kt")
                .readText()
        val home =
            sourceFile("app/src/main/kotlin/io/github/superisland/ui/home/HomeScreen.kt")
                .readText()
        val save = declaration(store, "save")

        assertTrue(
            "An absent preference must decode to the feature's default-off value",
            Regex(
                """preferences\.getBoolean\(\s*KEY_COLOR_OS_FLUID_CLOUD_STYLE_ENABLED,\s*false,?\s*\)""",
            ).containsMatchIn(store),
        )
        assertTrue(
            "A retired runtime must normalize a stale true preference to false",
            "SystemUiIslandAppearanceContract.COLOR_OS_FLUID_CLOUD_RUNTIME_AVAILABLE &&" in store,
        )
        assertTrue(
            "Every save must persist the local schema before the feature value",
            "putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)" in save &&
                "putBoolean(" in save &&
                "KEY_COLOR_OS_FLUID_CLOUD_STYLE_ENABLED" in save &&
                "config.capsule.colorOsFluidCloudStyleEnabled" in save,
        )
        assertTrue("The local write must report commit failure", ".commit()" in save && "check(" in save)
        assertTrue(
            "Reset must persist the domain default rather than carrying over the current value",
            Regex("""fun\s+reset\s*\(\s*\)\s*:\s*Result<Unit>\s*=\s*save\(IslandAppearanceConfig\(\)\)""")
                .containsMatchIn(store),
        )
        assertTrue(
            "The global reset action must include island appearance and republish it",
            "islandAppearanceStore.reset().getOrThrow()" in home &&
                "IslandAppearanceConfigSync.syncStoredAndReload()" in home,
        )
    }

    @Test
    fun remoteSyncPublishesTheSameSchemaAndBooleanBeforeRequestingReload() {
        val sync =
            sourceFile("app/src/main/kotlin/io/github/superisland/IslandAppearanceConfigSync.kt")
                .readText()
        val publish = declaration(sync, "sync")

        val schemaWrite =
            "SystemUiIslandAppearanceContract.KEY_SCHEMA_VERSION" to
                "SystemUiIslandAppearanceContract.SCHEMA_VERSION"
        val featureWrite =
            "SystemUiIslandAppearanceContract.KEY_COLOR_OS_FLUID_CLOUD_STYLE_ENABLED" to
                "config.capsule.colorOsFluidCloudStyleEnabled"
        listOf(schemaWrite, featureWrite).forEach { (key, value) ->
            assertTrue("RemotePreferences publication must write $key", key in publish)
            assertTrue("RemotePreferences publication must derive $key from $value", value in publish)
        }
        assertTrue(
            "SystemUI publication must remain false while the failed runtime is retired",
            "SystemUiIslandAppearanceContract.COLOR_OS_FLUID_CLOUD_RUNTIME_AVAILABLE &&" in publish,
        )
        assertTrue(
            "The sync channel must stay isolated from other feature preferences",
            "SystemUiIslandAppearanceContract.REMOTE_PREFERENCES" in publish,
        )
        assertTrue("The remote document must be committed before reload", ".commit()" in publish)
        assertTrue(
            "Reload must be explicit and restricted to SystemUI",
            "Intent(SystemUiIslandAppearanceContract.ACTION_RELOAD)" in publish &&
                ".setPackage(SystemUiIslandAppearanceContract.SYSTEM_UI_PACKAGE)" in publish,
        )
        assertTrue(
            "The committed snapshot must precede the reload broadcast",
            publish.indexOf(".commit()") < publish.indexOf("sendBroadcast("),
        )
    }

    @Test
    fun bothDirectorySkinsRouteToOneRootStableAppearanceOwner() {
        val activity =
            sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val miuix =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/superisland/SuperIslandMiuix.kt",
            ).readText()
        val material =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/material/MaterialPages.kt",
            ).readText()
        val screen =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/superisland/CapsuleAppearanceScreen.kt",
            ).readText()

        assertTrue(
            "Miuix must route the shared directory entry",
            "\"capsule_appearance\" -> onOpenCapsuleAppearance()" in miuix,
        )
        assertTrue(
            "Material must route the same shared directory entry",
            "\"capsule_appearance\" -> onOpenDestination(AppDestination.CAPSULE_APPEARANCE)" in
                material,
        )
        assertEquals(
            "The navigation host must create one root-stable appearance owner",
            1,
            Regex("""IslandAppearanceDashboardStateOwner\(context\.applicationContext\)""")
                .findAll(activity)
                .count(),
        )
        assertTrue(
            "The root owner must be retained and disposed outside either skin",
            "remember(context.applicationContext)" in activity &&
                "DisposableEffect(islandAppearanceStateOwner)" in activity &&
                "onDispose(islandAppearanceStateOwner::close)" in activity,
        )

        val appearanceBranch =
            activity
                .substringAfter("AppDestination.CAPSULE_APPEARANCE ->")
                .substringBefore("AppDestination.BATTERY_MONITOR ->")
        assertTrue(
            "The typed destination must inject the same owner into the shared screen",
            "CapsuleAppearanceScreen(" in appearanceBranch &&
                "stateOwner = islandAppearanceStateOwner" in appearanceBranch,
        )
        assertFalse(
            "The business destination must not fork its state by visual skin",
            "AppUiMode." in appearanceBranch || "LocalUiMode" in appearanceBranch,
        )
        assertTrue(
            "The shared screen must observe the owner and use adaptive controls",
            "stateOwner.state.collectAsStateWithLifecycle()" in screen &&
                "AppDirectoryDetailScreen(" in screen &&
                "AppFeatureMasterSwitch(" in screen,
        )
        assertTrue(
            "The retired runtime must leave the switch disabled instead of claiming success",
            "enabled = state.loaded && runtimeAvailable" in screen,
        )
        assertFalse(
            "The screen must not create a second repository or skin-specific state owner",
            "IslandAppearanceStore(" in screen || "LocalUiMode" in screen,
        )
    }

    @Test
    fun optimisticSwitchStateIsOwnedBeforeTheSerializedWriteQueue() {
        val owner =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/IslandAppearanceDashboardStateOwner.kt",
            ).readText()
        val setter = declaration(owner, "setColorOsFluidCloudStyleEnabled")

        assertTrue("Both skins must observe one StateFlow", "val state: StateFlow<IslandAppearanceDashboardState>" in owner)
        assertTrue("Writes must stay serialized", "Channel<SaveRequest>(Channel.UNLIMITED)" in owner)
        assertTrue(
            "The switch must update its visible state before enqueueing persistence",
            setter.indexOf("_state.value =") < setter.indexOf("saveRequests.trySend("),
        )
        assertTrue(
            "A failed latest write must roll the shared switch back",
            "request.generation == generation" in owner &&
                "IslandAppearanceDashboardState(persisted, loaded = true)" in owner,
        )
    }

    private fun declaration(
        source: String,
        name: String,
    ): String {
        val signature =
            Regex("""(?m)^\s*(?:private\s+|internal\s+|public\s+)?(?:suspend\s+)?fun\s+${Regex.escape(name)}\s*\(""")
                .find(source)
                ?: error("Missing Kotlin function $name")
        val openBrace = source.indexOf('{', signature.range.first)
        require(openBrace >= 0) { "Missing body for Kotlin function $name" }
        var depth = 0
        for (index in openBrace until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(signature.range.first, index + 1)
                }
            }
        }
        error("Unclosed Kotlin function $name")
    }

}
