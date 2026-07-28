package io.github.superisland

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.superisland.model.ResidentExpandedContentMode
import io.github.superisland.model.ResidentExpandedContentTemplate
import io.github.superisland.model.ResidentExpandedActionCodec
import io.github.superisland.model.ResidentIslandIcon
import io.github.superisland.model.ResidentMetricKey
import io.github.superisland.model.ResidentMonitorConfig

/**
 * Android persistence boundary for [ResidentMonitorConfig].
 *
 * The core model stays Android-free and validates all values after decoding. Version 1 stored a
 * pair of overloaded collapsed slots; version 2 split icons and titles; version 3 makes the
 * default icon follow its title and retires voltage as a title choice; version 4 adds the
 * expanded-content mode and safe custom template; version 5 adds an ordered, bounded action list.
 */
class ResidentMonitorConfigStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): ResidentMonitorConfig {
        val storedVersion = preferences.getInt(KEY_SCHEMA_VERSION, LEGACY_SCHEMA_VERSION)
        if (storedVersion >= CURRENT_SCHEMA_VERSION) {
            return loadCurrent().normalizedFromStorage()
        }

        val migrated =
            if (storedVersion >= SPLIT_SLOT_SCHEMA_VERSION) {
                // expanded-actions did not exist before v5. Ignore any stray value attached to an
                // older schema instead of treating an unowned field as trusted configuration.
                loadCurrent(includeExpandedActions = false).normalizedFromStorage()
            } else {
                loadLegacy().normalizedFromStorage()
            }
        persist(migrated)
        return migrated
    }

    private fun loadCurrent(includeExpandedActions: Boolean = true): ResidentMonitorConfig =
        ResidentMonitorConfig(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            leftIcon = iconOrDefault(preferences.getString(KEY_LEFT_ICON, null), ResidentIslandIcon.FOLLOW_TITLE),
            rightIcon = iconOrDefault(preferences.getString(KEY_RIGHT_ICON, null), ResidentIslandIcon.NONE),
            leftTitleMetric = metricOrDefault(preferences.getString(KEY_LEFT_TITLE_METRIC, null)),
            rightTitleMetric = metricOrDefault(preferences.getString(KEY_RIGHT_TITLE_METRIC, null)),
            titleRefreshIntervalMillis =
                preferences.getLong(
                    KEY_REFRESH_INTERVAL,
                    ResidentMonitorConfig.DEFAULT_REFRESH_INTERVAL_MILLIS,
                ),
            expandedMetrics = expandedMetrics(),
            expandedContentMode = expandedContentMode(),
            expandedContentTemplate =
                preferences.getString(
                    KEY_EXPANDED_CONTENT_TEMPLATE,
                    ResidentExpandedContentTemplate.DEFAULT_TEMPLATE,
                ) ?: ResidentExpandedContentTemplate.DEFAULT_TEMPLATE,
            expandedActions = if (includeExpandedActions) expandedActions() else emptyList(),
        )

    private fun loadLegacy(): ResidentMonitorConfig {
        val legacyLeadingKind = legacySlotKind(preferences.getString(KEY_LEGACY_LEADING_KIND, null))
        val legacyTrailingKind = legacySlotKind(preferences.getString(KEY_LEGACY_TRAILING_KIND, null))
        val legacyTitleMetric = metricOrDefault(preferences.getString(KEY_LEGACY_TITLE_METRIC, null))
        val migratedRightTitle =
            if (legacyTrailingKind == LegacyResidentSlotKind.METRIC_SHORT) {
                metricOrDefault(preferences.getString(KEY_LEGACY_TRAILING_METRIC, null))
            } else {
                // Every other legacy trailing choice was rendered as battery percentage by the
                // SystemUI host, including NONE and the old, non-functional APP_ICON default.
                ResidentMetricKey.BATTERY_PERCENT
            }

        return ResidentMonitorConfig(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            leftIcon =
                if (legacyLeadingKind == LegacyResidentSlotKind.NONE) {
                    ResidentIslandIcon.NONE
                } else {
                    ResidentIslandIcon.FOLLOW_TITLE
                },
            // The old Store defaulted the trailing slot to APP_ICON even though no right icon was
            // ever published. Keep the visual result stable instead of inventing an enabled icon.
            rightIcon = ResidentIslandIcon.NONE,
            leftTitleMetric = legacyTitleMetric,
            rightTitleMetric = migratedRightTitle,
            titleRefreshIntervalMillis =
                preferences.getLong(
                    KEY_REFRESH_INTERVAL,
                    ResidentMonitorConfig.DEFAULT_REFRESH_INTERVAL_MILLIS,
                ),
            expandedMetrics = expandedMetrics(),
        )
    }

    fun save(config: ResidentMonitorConfig): Result<Unit> {
        if (!config.hasValidCustomContent()) {
            return Result.failure(IllegalArgumentException("Custom expanded content must be non-blank and valid"))
        }
        val durableCandidate =
            if (config.hasValidExpandedContentTemplate()) {
                config
            } else {
                // PRESET mode may carry an unfinished editor draft. It is not active content and
                // must not overwrite the last valid custom template when another setting saves.
                config.copy(expandedContentTemplate = load().expandedContentTemplate)
            }
        val normalized = durableCandidate.normalized()
        persist(normalized)
        // This repository is the single owner of the cross-process mirror. Callers that need an
        // activation result can consume it; ordinary edits may rely on the reconnect retry path.
        return ResidentIslandHostConfigSync.sync(normalized)
    }

    /** Keeps retained Navigation3 entries aligned when another detail route saves the config. */
    fun observe(
        emitInitial: Boolean = true,
        onChanged: (ResidentMonitorConfig) -> Unit,
    ): () -> Unit {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key in OBSERVED_KEYS) onChanged(load())
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        // A retained Navigation3 entry may have been out of composition while another detail
        // page saved. Emit the repository value on every new subscription before waiting for the
        // next preference callback, otherwise returning could briefly restore a stale summary.
        if (emitInitial) onChanged(load())
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Invalidates a root owner without decoding or migrating configuration on the caller thread. */
    fun observeInvalidations(onChanged: () -> Unit): () -> Unit {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key in OBSERVED_KEYS) onChanged()
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun persist(config: ResidentMonitorConfig) {
        preferences.edit {
            putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            putBoolean(KEY_ENABLED, config.enabled)
            putString(KEY_LEFT_ICON, config.leftIcon.name)
            putString(KEY_RIGHT_ICON, config.rightIcon.name)
            putString(KEY_LEFT_TITLE_METRIC, config.leftTitleMetric.name)
            putString(KEY_RIGHT_TITLE_METRIC, config.rightTitleMetric.name)
            putLong(KEY_REFRESH_INTERVAL, config.titleRefreshIntervalMillis)
            putString(KEY_EXPANDED_METRICS, config.expandedMetrics.joinToString(METRIC_SEPARATOR) { it.name })
            putString(KEY_EXPANDED_CONTENT_MODE, config.expandedContentMode.name)
            putString(KEY_EXPANDED_CONTENT_TEMPLATE, config.expandedContentTemplate)
            putString(KEY_EXPANDED_ACTIONS, ResidentExpandedActionCodec.encode(config.expandedActions))
            remove(KEY_LEGACY_TITLE_METRIC)
            remove(KEY_LEGACY_LEADING_KIND)
            remove(KEY_LEGACY_LEADING_METRIC)
            remove(KEY_LEGACY_LEADING_STATIC)
            remove(KEY_LEGACY_TRAILING_KIND)
            remove(KEY_LEGACY_TRAILING_METRIC)
            remove(KEY_LEGACY_TRAILING_STATIC)
        }
    }

    /** Restores display defaults and explicitly withdraws the SystemUI-owned resident island. */
    fun reset() {
        save(ResidentMonitorConfig())
    }

    private fun expandedMetrics(): List<ResidentMetricKey> =
        preferences
            .getString(KEY_EXPANDED_METRICS, null)
            ?.split(METRIC_SEPARATOR)
            ?.mapNotNull(::metricOrNull)
            .orEmpty()

    private fun expandedContentMode(): ResidentExpandedContentMode =
        preferences
            .getString(KEY_EXPANDED_CONTENT_MODE, null)
            ?.let { stored ->
                ResidentExpandedContentMode.entries.firstOrNull { it.name == stored }
            } ?: ResidentExpandedContentMode.PRESET

    private fun expandedActions() =
        runCatching { preferences.getString(KEY_EXPANDED_ACTIONS, null) }
            .getOrNull()
            .let(ResidentExpandedActionCodec::decodeOrEmpty)

    private fun ResidentMonitorConfig.normalizedFromStorage(): ResidentMonitorConfig {
        val normalized = normalized()
        return if (normalized.hasValidExpandedContentTemplate()) {
            normalized
        } else {
            normalized.copy(expandedContentTemplate = ResidentExpandedContentTemplate.DEFAULT_TEMPLATE)
        }
    }

    private fun metricOrDefault(value: String?): ResidentMetricKey =
        metricOrNull(value) ?: ResidentMetricKey.BATTERY_PERCENT

    private fun metricOrNull(value: String?): ResidentMetricKey? =
        value?.let { runCatching { ResidentMetricKey.valueOf(it) }.getOrNull() }

    private fun iconOrDefault(
        value: String?,
        default: ResidentIslandIcon,
    ): ResidentIslandIcon =
        when (value) {
            LEGACY_SUPER_ISLAND_ICON -> ResidentIslandIcon.FOLLOW_TITLE
            else -> value?.let { runCatching { ResidentIslandIcon.valueOf(it) }.getOrNull() } ?: default
        }

    private fun legacySlotKind(value: String?): LegacyResidentSlotKind =
        value?.let { runCatching { LegacyResidentSlotKind.valueOf(it) }.getOrNull() }
            ?: LegacyResidentSlotKind.APP_ICON

    private companion object {
        const val FILE_NAME = "resident-monitor-config"
        const val KEY_SCHEMA_VERSION = "schema-version"
        const val LEGACY_SCHEMA_VERSION = 1
        const val SPLIT_SLOT_SCHEMA_VERSION = 2
        const val CURRENT_SCHEMA_VERSION = 5
        const val LEGACY_SUPER_ISLAND_ICON = "SUPER_ISLAND"
        const val KEY_ENABLED = "enabled"
        const val KEY_LEFT_ICON = "left-icon"
        const val KEY_RIGHT_ICON = "right-icon"
        const val KEY_LEFT_TITLE_METRIC = "left-title-metric"
        const val KEY_RIGHT_TITLE_METRIC = "right-title-metric"
        const val KEY_REFRESH_INTERVAL = "refresh-interval"
        const val KEY_LEGACY_TITLE_METRIC = "title-metric"
        const val KEY_LEGACY_LEADING_KIND = "leading-kind"
        const val KEY_LEGACY_LEADING_METRIC = "leading-metric"
        const val KEY_LEGACY_LEADING_STATIC = "leading-static"
        const val KEY_LEGACY_TRAILING_KIND = "trailing-kind"
        const val KEY_LEGACY_TRAILING_METRIC = "trailing-metric"
        const val KEY_LEGACY_TRAILING_STATIC = "trailing-static"
        const val KEY_EXPANDED_METRICS = "expanded-metrics"
        const val KEY_EXPANDED_CONTENT_MODE = "expanded-content-mode"
        const val KEY_EXPANDED_CONTENT_TEMPLATE = "expanded-content-template"
        const val KEY_EXPANDED_ACTIONS = "expanded-actions"
        const val METRIC_SEPARATOR = ","
        val OBSERVED_KEYS =
            setOf(
                KEY_SCHEMA_VERSION,
                KEY_ENABLED,
                KEY_LEFT_ICON,
                KEY_RIGHT_ICON,
                KEY_LEFT_TITLE_METRIC,
                KEY_RIGHT_TITLE_METRIC,
                KEY_REFRESH_INTERVAL,
                KEY_EXPANDED_METRICS,
                KEY_EXPANDED_CONTENT_MODE,
                KEY_EXPANDED_CONTENT_TEMPLATE,
                KEY_EXPANDED_ACTIONS,
            )
    }

    private enum class LegacyResidentSlotKind {
        NONE,
        APP_ICON,
        METRIC_SHORT,
        PROGRESS,
        STATIC_TEXT,
    }
}
