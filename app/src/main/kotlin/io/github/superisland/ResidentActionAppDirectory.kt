package io.github.superisland

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.os.Process
import java.util.Locale

data class ResidentActionAppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
    val packageInfo: PackageInfo? = null,
)

/**
 * Lists launchable applications for resident-island actions.
 *
 * This API is synchronous by design. Callers own background dispatch and error presentation.
 */
class ResidentActionAppDirectory internal constructor(
    private val entryLoader: () -> Iterable<ResidentActionAppEntry>,
) {
    constructor(context: Context) : this(currentUserLauncherEntryLoader(context.applicationContext))

    fun load(): List<ResidentActionAppEntry> = normalizeResidentActionAppEntries(entryLoader())
}

private fun currentUserLauncherEntryLoader(
    appContext: Context,
): () -> Iterable<ResidentActionAppEntry> = {
    val launcherApps =
        checkNotNull(appContext.getSystemService(LauncherApps::class.java)) {
            "LauncherApps service is unavailable"
        }
    launcherApps
        .getActivityList(null, Process.myUserHandle())
        .map { activity ->
            val applicationInfo = activity.applicationInfo
            ResidentActionAppEntry(
                packageName = activity.componentName.packageName,
                label = activity.label.toString(),
                isSystem = isSystemApplicationFlags(applicationInfo.flags),
                packageInfo =
                    runCatching {
                        appContext.packageManager.getPackageInfo(
                            activity.componentName.packageName,
                            PackageManager.PackageInfoFlags.of(0L),
                        )
                    }.getOrNull(),
            )
        }
}

internal fun normalizeResidentActionAppEntries(
    entries: Iterable<ResidentActionAppEntry>,
): List<ResidentActionAppEntry> {
    val normalized =
        entries
            .asSequence()
            .map { entry ->
                entry.copy(
                    packageName = entry.packageName.trim(),
                    label = entry.label.trim(),
                )
            }.filter { entry -> entry.packageName.isNotEmpty() }
            .map { entry ->
                entry.copy(label = entry.label.ifEmpty { entry.packageName })
            }

    // LauncherApps can return multiple launcher activities for one package. Pick the same label
    // regardless of framework enumeration order, then sort the package-level result for display.
    return normalized
        .sortedWith(
            compareBy<ResidentActionAppEntry> { entry -> entry.packageName.lowercase(Locale.ROOT) }
                .thenBy(ResidentActionAppEntry::packageName)
                .thenBy { entry -> entry.label.lowercase(Locale.ROOT) }
                .thenBy(ResidentActionAppEntry::label),
        ).distinctBy(ResidentActionAppEntry::packageName)
        .sortedWith(
            compareBy<ResidentActionAppEntry> { entry -> entry.label.lowercase(Locale.ROOT) }
                .thenBy(ResidentActionAppEntry::label)
                .thenBy { entry -> entry.packageName.lowercase(Locale.ROOT) }
                .thenBy(ResidentActionAppEntry::packageName),
        ).toList()
}
