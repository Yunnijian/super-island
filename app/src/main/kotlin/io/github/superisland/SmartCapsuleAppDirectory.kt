package io.github.superisland

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import io.github.superisland.design.NotificationSourceOptionUi
import io.github.superisland.design.SmartCapsuleAppSortConfig
import io.github.superisland.design.SmartCapsuleAppSortType
import io.github.superisland.model.AppRule
import io.github.superisland.model.FocusDisplayMode
import java.text.Collator

data class SmartCapsuleAppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
    val packageInfo: PackageInfo? = null,
)

/** Lists installed applications for the current Android user. */
class SmartCapsuleAppDirectory(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val entryCache = SmartCapsuleAppEntryCache()

    fun load(forceRefresh: Boolean = false): List<SmartCapsuleAppEntry> =
        entryCache.getOrLoad(forceRefresh) {
            val entries =
                runCatching { loadInstalledApplications() }
                    .getOrNull()
                    ?.takeIf { applications -> applications.isNotEmpty() }
                    ?: loadLauncherApplications()
            normalizeSmartCapsuleAppEntries(entries, appContext.packageName)
        }

    fun cachedEntries(): List<SmartCapsuleAppEntry> = entryCache.peek().orEmpty()

    fun isMiuiInstalledAppsPermissionSupported(): Boolean =
        runCatching {
            packageManager
                .getPermissionInfo(
                    MIUI_GET_INSTALLED_APPS_PERMISSION,
                    0,
                ).packageName == MIUI_PERMISSION_OWNER_PACKAGE
        }.getOrDefault(false)

    fun needsMiuiInstalledAppsPermission(): Boolean =
        isMiuiInstalledAppsPermissionSupported() &&
            appContext.checkSelfPermission(MIUI_GET_INSTALLED_APPS_PERMISSION) !=
            PackageManager.PERMISSION_GRANTED

    private fun loadInstalledApplications(): List<SmartCapsuleAppEntry> =
        packageManager
            .getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            .mapNotNull { packageInfo ->
                val applicationInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                SmartCapsuleAppEntry(
                    packageName = applicationInfo.packageName.orEmpty().trim(),
                    label =
                        runCatching {
                            packageManager.getApplicationLabel(applicationInfo).toString().trim()
                        }.getOrDefault(""),
                    isSystem = applicationInfo.isSystemApplication(),
                    packageInfo = packageInfo,
                )
            }

    private fun loadLauncherApplications(): List<SmartCapsuleAppEntry> {
        val launcherApps = appContext.getSystemService(LauncherApps::class.java)
        return runCatching {
            launcherApps
                .getActivityList(null, Process.myUserHandle())
                .asSequence()
                .map { activity ->
                    val packageName = activity.componentName.packageName
                    SmartCapsuleAppEntry(
                        packageName = packageName,
                        label = activity.label.toString().trim(),
                        isSystem = activity.applicationInfo.isSystemApplication(),
                        packageInfo = packageManager.packageInfoOrNull(packageName),
                    )
                }.toList()
        }.getOrElse {
            val launcherIntent =
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager
                .queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0L),
                ).mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
                    if (packageName.isEmpty()) return@mapNotNull null
                    SmartCapsuleAppEntry(
                        packageName = packageName,
                        label = resolveInfo.loadLabel(packageManager).toString().trim(),
                        isSystem =
                            resolveInfo.activityInfo
                                ?.applicationInfo
                                ?.isSystemApplication()
                                ?: false,
                        packageInfo = packageManager.packageInfoOrNull(packageName),
                    )
                }
        }
    }

    companion object {
        const val MIUI_GET_INSTALLED_APPS_PERMISSION =
            "com.android.permission.GET_INSTALLED_APPS"
        private const val MIUI_PERMISSION_OWNER_PACKAGE = "com.lbe.security.miui"
    }
}

internal class SmartCapsuleAppEntryCache {
    private val lock = Any()
    private var cachedEntries: List<SmartCapsuleAppEntry>? = null

    fun peek(): List<SmartCapsuleAppEntry>? = synchronized(lock) { cachedEntries }

    fun getOrLoad(
        forceRefresh: Boolean,
        loader: () -> List<SmartCapsuleAppEntry>,
    ): List<SmartCapsuleAppEntry> {
        if (!forceRefresh) {
            synchronized(lock) { cachedEntries }?.let { return it }
        }

        // PackageManager enumeration and label lookup can be slow. Never hold the cache monitor
        // while the loader runs, otherwise a main-thread peek can wait behind the full directory.
        val loaded = loader().toList()
        return synchronized(lock) {
            if (forceRefresh) {
                cachedEntries = loaded
                loaded
            } else {
                cachedEntries ?: loaded.also { cachedEntries = it }
            }
        }
    }
}

internal data class SmartCapsuleDirectoryUiModel(
    val appOptions: List<NotificationSourceOptionUi> = emptyList(),
    val selectedApp: SmartCapsuleAppEntry? = null,
)

/** Builds the complete APPS/CHANNELS presentation snapshot in one background-friendly pass. */
internal fun buildSmartCapsuleDirectoryUiModel(
    entries: List<SmartCapsuleAppEntry>,
    rules: List<AppRule>,
    showSystemApps: Boolean,
    selectedPackage: String?,
    includeAppOptions: Boolean,
    sortConfig: SmartCapsuleAppSortConfig = SmartCapsuleAppSortConfig(),
): SmartCapsuleDirectoryUiModel {
    val rulesByPackage = rules.associateBy(AppRule::packageName)
    val listedPackages = entries.mapTo(mutableSetOf(), SmartCapsuleAppEntry::packageName)
    val missingSelectedApps =
        (
            rules.asSequence().map(AppRule::packageName) +
                (selectedPackage?.let(::sequenceOf) ?: emptySequence())
        )
            .filterNot(listedPackages::contains)
            .distinct()
            .map { packageName -> SmartCapsuleAppEntry(packageName, packageName) }
            .toList()
    val selectableApps =
        if (missingSelectedApps.isEmpty()) {
            entries
        } else {
            (entries + missingSelectedApps).sortedWith(
                compareBy<SmartCapsuleAppEntry> { entry -> entry.label.lowercase() }
                    .thenBy(SmartCapsuleAppEntry::packageName),
            )
        }
    val selectedApp = selectableApps.firstOrNull { app -> app.packageName == selectedPackage }
    if (!includeAppOptions) {
        return SmartCapsuleDirectoryUiModel(selectedApp = selectedApp)
    }

    val visibleApps =
        filterSmartCapsuleAppEntries(
            entries = selectableApps,
            selectedPackageNames = rulesByPackage.keys,
            showSystemApps = showSystemApps,
        ).sortedWith(
            smartCapsuleAppComparator(
                config = sortConfig,
                selectedPackageNames = rulesByPackage.keys,
            ),
        )
    return SmartCapsuleDirectoryUiModel(
        appOptions =
            visibleApps.map { app ->
                val rule = rulesByPackage[app.packageName]
                NotificationSourceOptionUi(
                    id = app.packageName,
                    title = app.label,
                    summary =
                        when {
                            rule == null -> app.packageName
                            rule.channels.allChannels ->
                                "${app.packageName} · 全部 Channel" +
                                    if (rule.focusDisplayMode == FocusDisplayMode.FOCUS_ONLY) {
                                        " · 仅焦点"
                                    } else {
                                        ""
                                    }
                            else ->
                                "${app.packageName} · ${rule.channels.channelIds.size} 个 Channel" +
                                    if (rule.focusDisplayMode == FocusDisplayMode.FOCUS_ONLY) {
                                        " · 仅焦点"
                                    } else {
                                        ""
                                    }
                        },
                    selected = rule != null,
                    packageInfo = app.packageInfo,
                    isSystem = app.isSystem,
                )
            },
        selectedApp = selectedApp,
    )
}

private fun smartCapsuleAppComparator(
    config: SmartCapsuleAppSortConfig,
    selectedPackageNames: Set<String>,
): Comparator<SmartCapsuleAppEntry> {
    val base =
        when (config.type) {
            SmartCapsuleAppSortType.NAME -> {
                val collator = Collator.getInstance()
                Comparator { first, second -> collator.compare(first.label, second.label) }
            }
            SmartCapsuleAppSortType.PACKAGE_NAME -> compareBy(SmartCapsuleAppEntry::packageName)
            SmartCapsuleAppSortType.INSTALL_TIME ->
                compareBy { entry -> entry.packageInfo?.firstInstallTime ?: 0L }
            SmartCapsuleAppSortType.UPDATE_TIME ->
                compareBy { entry -> entry.packageInfo?.lastUpdateTime ?: 0L }
        }
    val secondary = if (config.reversed) base.reversed() else base
    return Comparator { first, second ->
        val firstRank = if (first.packageName in selectedPackageNames) 0 else 1
        val secondRank = if (second.packageName in selectedPackageNames) 0 else 1
        if (firstRank != secondRank) {
            firstRank - secondRank
        } else {
            secondary.compare(first, second)
        }
    }
}

internal fun normalizeSmartCapsuleAppEntries(
    entries: Iterable<SmartCapsuleAppEntry>,
    selfPackageName: String,
): List<SmartCapsuleAppEntry> =
    entries
        .asSequence()
        .map { entry -> entry.copy(packageName = entry.packageName.trim(), label = entry.label.trim()) }
        .filter { entry ->
            entry.packageName.isNotEmpty() && entry.packageName != selfPackageName
        }.map { entry ->
            entry.copy(label = entry.label.ifEmpty { entry.packageName })
        }.sortedWith(
            compareBy<SmartCapsuleAppEntry> { entry -> entry.label.lowercase() }
                .thenBy(SmartCapsuleAppEntry::packageName)
                .thenBy(SmartCapsuleAppEntry::label),
        ).distinctBy(SmartCapsuleAppEntry::packageName)
        .toList()

internal fun filterSmartCapsuleAppEntries(
    entries: List<SmartCapsuleAppEntry>,
    selectedPackageNames: Set<String>,
    showSystemApps: Boolean,
): List<SmartCapsuleAppEntry> =
    if (showSystemApps) {
        entries
    } else {
        entries.filter { entry ->
            !entry.isSystem || entry.packageName in selectedPackageNames
        }
    }

internal fun isSystemApplicationFlags(flags: Int): Boolean =
    flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

private fun ApplicationInfo.isSystemApplication(): Boolean = isSystemApplicationFlags(flags)

private fun PackageManager.packageInfoOrNull(packageName: String): PackageInfo? =
    runCatching {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
    }.getOrNull()
