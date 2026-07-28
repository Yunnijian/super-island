package io.github.superisland

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import io.github.superisland.design.SmartCapsuleAppSortConfig
import io.github.superisland.design.SmartCapsuleAppSortType
import io.github.superisland.model.AppRule
import io.github.superisland.model.ChannelSelection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCapsuleAppDirectoryTest {
    @Test
    fun normalizationKeepsSystemAndServicePackagesButExcludesSelfAndBlankPackages() {
        val normalized =
            normalizeSmartCapsuleAppEntries(
                entries =
                    listOf(
                        SmartCapsuleAppEntry("com.android.systemui", "System UI"),
                        SmartCapsuleAppEntry("com.xiaomi.xmsf", ""),
                        SmartCapsuleAppEntry("io.github.superisland", "SuperIsland"),
                        SmartCapsuleAppEntry("  ", "Invalid"),
                    ),
                selfPackageName = "io.github.superisland",
            )

        assertEquals(
            listOf(
                SmartCapsuleAppEntry("com.xiaomi.xmsf", "com.xiaomi.xmsf"),
                SmartCapsuleAppEntry("com.android.systemui", "System UI"),
            ),
            normalized,
        )
    }

    @Test
    fun normalizationTrimsDeduplicatesAndSortsDeterministically() {
        val normalized =
            normalizeSmartCapsuleAppEntries(
                entries =
                    listOf(
                        SmartCapsuleAppEntry("com.example.z", " beta "),
                        SmartCapsuleAppEntry("com.example.duplicate", "Zulu"),
                        SmartCapsuleAppEntry(" com.example.a ", "alpha"),
                        SmartCapsuleAppEntry("com.example.duplicate", "Alpha"),
                    ),
                selfPackageName = "io.github.superisland",
            )

        assertEquals(
            listOf(
                SmartCapsuleAppEntry("com.example.a", "alpha"),
                SmartCapsuleAppEntry("com.example.duplicate", "Alpha"),
                SmartCapsuleAppEntry("com.example.z", "beta"),
            ),
            normalized,
        )
    }

    @Test
    fun systemClassificationUsesAndroidApplicationFlags() {
        assertFalse(isSystemApplicationFlags(0))
        assertTrue(isSystemApplicationFlags(ApplicationInfo.FLAG_SYSTEM))
        assertTrue(isSystemApplicationFlags(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
    }

    @Test
    fun defaultFilterHidesOnlyUnselectedSystemApps() {
        val entries =
            listOf(
                SmartCapsuleAppEntry("com.example.user", "User", isSystem = false),
                SmartCapsuleAppEntry("com.android.hidden", "Hidden", isSystem = true),
                SmartCapsuleAppEntry("com.android.selected", "Selected", isSystem = true),
            )

        assertEquals(
            listOf(entries[0], entries[2]),
            filterSmartCapsuleAppEntries(
                entries = entries,
                selectedPackageNames = setOf("com.android.selected"),
                showSystemApps = false,
            ),
        )
    }

    @Test
    fun enabledSystemFilterReturnsTheCompleteDirectoryWithoutChangingOrder() {
        val entries =
            listOf(
                SmartCapsuleAppEntry("com.example.user", "User", isSystem = false),
                SmartCapsuleAppEntry("com.android.system", "System", isSystem = true),
            )

        assertEquals(
            entries,
            filterSmartCapsuleAppEntries(
                entries = entries,
                selectedPackageNames = emptySet(),
                showSystemApps = true,
            ),
        )
    }

    @Test
    fun directoryCacheLoadsOnceUntilAnExplicitRefresh() {
        val cache = SmartCapsuleAppEntryCache()
        var loadCount = 0
        val loader = {
            loadCount += 1
            listOf(SmartCapsuleAppEntry("com.example.$loadCount", "Load $loadCount"))
        }

        val first = cache.getOrLoad(forceRefresh = false, loader = loader)
        assertEquals(first, cache.peek())
        val cached = cache.getOrLoad(forceRefresh = false, loader = loader)
        val refreshed = cache.getOrLoad(forceRefresh = true, loader = loader)

        assertEquals(2, loadCount)
        assertEquals(first, cached)
        assertEquals("com.example.2", refreshed.single().packageName)
    }

    @Test
    fun directoryCacheNeverHoldsItsMonitorWhileTheLoaderRuns() {
        val cache = SmartCapsuleAppEntryCache()
        val initial = listOf(SmartCapsuleAppEntry("com.example.initial", "Initial"))
        cache.getOrLoad(forceRefresh = false) { initial }
        val loaderEntered = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val refreshExecutor = Executors.newSingleThreadExecutor()
        val peekExecutor = Executors.newSingleThreadExecutor()

        try {
            val refresh =
                refreshExecutor.submit<List<SmartCapsuleAppEntry>> {
                    cache.getOrLoad(forceRefresh = true) {
                        loaderEntered.countDown()
                        releaseLoader.await(2, TimeUnit.SECONDS)
                        listOf(SmartCapsuleAppEntry("com.example.refreshed", "Refreshed"))
                    }
                }
            assertTrue(loaderEntered.await(1, TimeUnit.SECONDS))

            val peek = peekExecutor.submit<List<SmartCapsuleAppEntry>?> { cache.peek() }
            assertEquals(initial, peek.get(1, TimeUnit.SECONDS))

            releaseLoader.countDown()
            assertEquals("com.example.refreshed", refresh.get(1, TimeUnit.SECONDS).single().packageName)
        } finally {
            releaseLoader.countDown()
            refreshExecutor.shutdownNow()
            peekExecutor.shutdownNow()
        }
    }

    @Test
    fun directoryUiModelAddsMissingRulesFiltersSystemAppsAndBuildsRowsOnce() {
        val entries =
            listOf(
                SmartCapsuleAppEntry("com.example.user", "User", isSystem = false),
                SmartCapsuleAppEntry("com.android.hidden", "Hidden", isSystem = true),
                SmartCapsuleAppEntry("com.android.selected", "Selected", isSystem = true),
            )
        val rules =
            listOf(
                AppRule("com.android.selected", ChannelSelection.ALL),
                AppRule("com.example.missing", ChannelSelection.exact("messages")),
            )

        val model =
            buildSmartCapsuleDirectoryUiModel(
                entries = entries,
                rules = rules,
                showSystemApps = false,
                selectedPackage = "com.example.missing",
                includeAppOptions = true,
            )

        assertEquals("com.example.missing", model.selectedApp?.packageName)
        assertEquals(
            setOf("com.example.user", "com.android.selected", "com.example.missing"),
            model.appOptions.mapTo(mutableSetOf()) { option -> option.id },
        )
        assertTrue(model.appOptions.first { it.id == "com.android.selected" }.selected)
        assertEquals(
            "com.example.missing · 1 个 Channel",
            model.appOptions.first { it.id == "com.example.missing" }.summary,
        )
        assertFalse(model.appOptions.any { it.id == "com.android.hidden" })
    }

    @Test
    fun channelDirectoryModelSkipsAppRowProjection() {
        val model =
            buildSmartCapsuleDirectoryUiModel(
                entries = listOf(SmartCapsuleAppEntry("com.example.selected", "Selected")),
                rules = listOf(AppRule("com.example.selected")),
                showSystemApps = true,
                selectedPackage = "com.example.selected",
                includeAppOptions = false,
            )

        assertTrue(model.appOptions.isEmpty())
        assertEquals("Selected", model.selectedApp?.label)
    }

    @Test
    fun channelDirectoryKeepsAJustSelectedPackageBeforeTheAsyncCatalogRefresh() {
        val model =
            buildSmartCapsuleDirectoryUiModel(
                entries = emptyList(),
                rules = emptyList(),
                showSystemApps = false,
                selectedPackage = "com.example.justselected",
                includeAppOptions = false,
            )

        assertEquals("com.example.justselected", model.selectedApp?.packageName)
        assertEquals("com.example.justselected", model.selectedApp?.label)
    }

    @Test
    fun directoryUiModelUsesThePersistedKernelSuSortContract() {
        val older = PackageInfo().apply {
            firstInstallTime = 10L
            lastUpdateTime = 100L
        }
        val newer = PackageInfo().apply {
            firstInstallTime = 20L
            lastUpdateTime = 50L
        }
        val entries =
            listOf(
                SmartCapsuleAppEntry("com.example.z", "Alpha", packageInfo = newer),
                SmartCapsuleAppEntry("com.example.a", "Zulu", packageInfo = older),
            )

        fun ids(config: SmartCapsuleAppSortConfig): List<String> =
            buildSmartCapsuleDirectoryUiModel(
                entries = entries,
                rules = emptyList(),
                showSystemApps = true,
                selectedPackage = null,
                includeAppOptions = true,
                sortConfig = config,
            ).appOptions.map { option -> option.id }

        assertEquals(
            listOf("com.example.z", "com.example.a"),
            ids(SmartCapsuleAppSortConfig(SmartCapsuleAppSortType.NAME)),
        )
        assertEquals(
            listOf("com.example.a", "com.example.z"),
            ids(SmartCapsuleAppSortConfig(SmartCapsuleAppSortType.PACKAGE_NAME)),
        )
        assertEquals(
            listOf("com.example.a", "com.example.z"),
            ids(SmartCapsuleAppSortConfig(SmartCapsuleAppSortType.INSTALL_TIME)),
        )
        assertEquals(
            listOf("com.example.z", "com.example.a"),
            ids(SmartCapsuleAppSortConfig(SmartCapsuleAppSortType.UPDATE_TIME)),
        )
        assertEquals(
            listOf("com.example.z", "com.example.a"),
            ids(
                SmartCapsuleAppSortConfig(
                    type = SmartCapsuleAppSortType.PACKAGE_NAME,
                    reversed = true,
                ),
            ),
        )
    }

    @Test
    fun enabledAppsStayAboveDisabledAppsForEverySortTypeAndDirection() {
        val entries =
            listOf(
                SmartCapsuleAppEntry("com.example.a", "Alpha"),
                SmartCapsuleAppEntry("com.example.z", "Zulu"),
                SmartCapsuleAppEntry("com.example.selected", "Selected"),
                SmartCapsuleAppEntry("com.example.selected2", "Also selected"),
            )
        val selectedIds = setOf("com.example.selected", "com.example.selected2")
        val rules = selectedIds.map { packageName -> AppRule(packageName, ChannelSelection.ALL) }

        SmartCapsuleAppSortType.entries.forEach { sortType ->
            listOf(false, true).forEach { reversed ->
                val ids =
                    buildSmartCapsuleDirectoryUiModel(
                        entries = entries,
                        rules = rules,
                        showSystemApps = true,
                        selectedPackage = null,
                        includeAppOptions = true,
                        sortConfig =
                            SmartCapsuleAppSortConfig(
                                type = sortType,
                                reversed = reversed,
                            ),
                    ).appOptions.map { option -> option.id }

                assertEquals(selectedIds, ids.take(selectedIds.size).toSet())
                assertTrue(ids.drop(selectedIds.size).none(selectedIds::contains))
            }
        }
    }
}
