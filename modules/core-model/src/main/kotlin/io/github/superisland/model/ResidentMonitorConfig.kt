package io.github.superisland.model

/** Metrics that can be selected for the resident Super Island. */
enum class ResidentMetricKey(
    val displayName: String,
) {
    BATTERY_PERCENT("电量"),
    CHARGE_STATE("充电状态"),
    CURRENT("电流"),
    VOLTAGE("电压"),
    POWER("功耗"),
    BATTERY_TEMPERATURE("电池温度"),
    CPU_TEMPERATURE("CPU 温度"),
    GPU_TEMPERATURE("GPU 温度"),
    SKIN_TEMPERATURE("皮温"),
    FAN_RPM("风扇转速"),
    FAN_LEVEL("风扇档位"),
    CPU_FREQUENCY("CPU 频率"),
    GPU_FREQUENCY("GPU 频率"),
    CUSTOM_TEMPLATE("自定义模板"),
}

/** Icon displayed in either side of the expanded resident island. */
enum class ResidentIslandIcon {
    NONE,
    FOLLOW_TITLE,
}

/** Determines whether expanded content uses the metric preset or a user-authored safe template. */
enum class ResidentExpandedContentMode {
    PRESET,
    CUSTOM,
}

/**
 * Persisted configuration for the "常驻超级岛" feature.
 *
 * Icons and titles are independent because HyperOS' type-2 island template supports text and an
 * optional picture on both sides. Keeping four explicit fields prevents a title choice from
 * silently changing icon placement, which happened with the previous collapsed-slot model.
 */
data class ResidentMonitorConfig(
    val enabled: Boolean = false,
    val leftIcon: ResidentIslandIcon = ResidentIslandIcon.FOLLOW_TITLE,
    val rightIcon: ResidentIslandIcon = ResidentIslandIcon.NONE,
    val leftTitleMetric: ResidentMetricKey = ResidentMetricKey.BATTERY_PERCENT,
    val rightTitleMetric: ResidentMetricKey = ResidentMetricKey.BATTERY_PERCENT,
    val titleRefreshIntervalMillis: Long = DEFAULT_REFRESH_INTERVAL_MILLIS,
    val expandedMetrics: List<ResidentMetricKey> = DEFAULT_EXPANDED_METRICS,
    val expandedContentMode: ResidentExpandedContentMode = ResidentExpandedContentMode.PRESET,
    val expandedContentTemplate: String = ResidentExpandedContentTemplate.DEFAULT_TEMPLATE,
    val expandedActions: List<ResidentExpandedAction> = emptyList(),
) {
    fun normalized(): ResidentMonitorConfig {
        val templateIsValid =
            expandedContentTemplate.isNotBlank() &&
                ResidentExpandedContentTemplate.validate(expandedContentTemplate).isValid
        return copy(
            leftTitleMetric = leftTitleMetric.normalizedTitleMetric(),
            rightTitleMetric = rightTitleMetric.normalizedTitleMetric(),
            titleRefreshIntervalMillis = titleRefreshIntervalMillis.coerceIn(MIN_REFRESH_INTERVAL_MILLIS, MAX_REFRESH_INTERVAL_MILLIS),
            expandedMetrics = expandedMetrics.distinct().ifEmpty { DEFAULT_EXPANDED_METRICS },
            expandedContentMode =
                expandedContentMode.takeUnless {
                    it == ResidentExpandedContentMode.CUSTOM && !templateIsValid
                } ?: ResidentExpandedContentMode.PRESET,
            expandedContentTemplate =
                when {
                    templateIsValid -> expandedContentTemplate
                    expandedContentMode == ResidentExpandedContentMode.CUSTOM ->
                        ResidentExpandedContentTemplate.DEFAULT_TEMPLATE
                    // In PRESET mode this field is an inactive editor draft. Keep it long enough
                    // for the persistence owner to retain the last valid stored custom template.
                    else -> expandedContentTemplate
                },
            expandedActions = ResidentExpandedAction.normalize(expandedActions),
        )
    }

    fun hasValidCustomContent(): Boolean =
        expandedContentMode != ResidentExpandedContentMode.CUSTOM ||
            hasValidExpandedContentTemplate()

    fun hasValidExpandedContentTemplate(): Boolean =
        expandedContentTemplate.isNotBlank() &&
            ResidentExpandedContentTemplate.validate(expandedContentTemplate).isValid

    companion object {
        const val MIN_REFRESH_INTERVAL_MILLIS = 1_000L
        const val MAX_REFRESH_INTERVAL_MILLIS = 60_000L
        const val DEFAULT_REFRESH_INTERVAL_MILLIS = 10_000L

        val TITLE_METRICS =
            listOf(
                ResidentMetricKey.BATTERY_PERCENT,
                ResidentMetricKey.CHARGE_STATE,
                ResidentMetricKey.CURRENT,
                ResidentMetricKey.POWER,
                ResidentMetricKey.BATTERY_TEMPERATURE,
                ResidentMetricKey.FAN_RPM,
            )

        val DEFAULT_EXPANDED_METRICS =
            listOf(
                ResidentMetricKey.BATTERY_PERCENT,
                ResidentMetricKey.CURRENT,
                ResidentMetricKey.POWER,
                ResidentMetricKey.BATTERY_TEMPERATURE,
            )

        private fun ResidentMetricKey.normalizedTitleMetric(): ResidentMetricKey =
            takeIf(TITLE_METRICS::contains) ?: ResidentMetricKey.BATTERY_PERCENT
    }
}
