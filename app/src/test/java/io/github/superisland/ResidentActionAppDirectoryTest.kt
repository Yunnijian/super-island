package io.github.superisland

import org.junit.Assert.assertEquals
import org.junit.Test

class ResidentActionAppDirectoryTest {
    @Test
    fun normalizationTrimsDropsBlankPackagesAndUsesPackageAsAnEmptyLabelFallback() {
        val result =
            normalizeResidentActionAppEntries(
                listOf(
                    ResidentActionAppEntry(" com.example.beta ", " Beta "),
                    ResidentActionAppEntry(" ", "Invalid"),
                    ResidentActionAppEntry("com.example.no_label", " "),
                ),
            )

        assertEquals(
            listOf(
                ResidentActionAppEntry("com.example.beta", "Beta"),
                ResidentActionAppEntry("com.example.no_label", "com.example.no_label"),
            ),
            result,
        )
    }

    @Test
    fun normalizationDeduplicatesMultipleLauncherActivitiesDeterministically() {
        val entries =
            listOf(
                ResidentActionAppEntry("com.example.zulu", "Zulu"),
                ResidentActionAppEntry("com.example.multi", "Secondary"),
                ResidentActionAppEntry("com.example.alpha", "alpha"),
                ResidentActionAppEntry("com.example.multi", "Main"),
            )

        val forward = normalizeResidentActionAppEntries(entries)
        val reversed = normalizeResidentActionAppEntries(entries.reversed())

        assertEquals(forward, reversed)
        assertEquals(
            listOf(
                ResidentActionAppEntry("com.example.alpha", "alpha"),
                ResidentActionAppEntry("com.example.multi", "Main"),
                ResidentActionAppEntry("com.example.zulu", "Zulu"),
            ),
            forward,
        )
    }

    @Test
    fun directoryEnumeratesOnlyWhenLoadIsCalledAndDoesNotCacheResults() {
        var loadCount = 0
        val directory =
            ResidentActionAppDirectory {
                loadCount += 1
                listOf(
                    ResidentActionAppEntry(
                        packageName = "com.example.$loadCount",
                        label = "Load $loadCount",
                    ),
                )
            }

        assertEquals(0, loadCount)
        assertEquals("com.example.1", directory.load().single().packageName)
        assertEquals("com.example.2", directory.load().single().packageName)
        assertEquals(2, loadCount)
    }

    @Test
    fun normalizationRetainsTheSystemApplicationClassificationForTheTargetPickerFilter() {
        val result =
            normalizeResidentActionAppEntries(
                listOf(
                    ResidentActionAppEntry(
                        packageName = "com.example.system",
                        label = "System app",
                        isSystem = true,
                    ),
                ),
            )

        assertEquals(true, result.single().isSystem)
    }
}
