package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidentMonitorConfigTest {
    @Test
    fun defaultsAreBoundedAndContainExpandedMetrics() {
        val config = ResidentMonitorConfig()

        assertEquals(ResidentMonitorConfig.DEFAULT_REFRESH_INTERVAL_MILLIS, config.titleRefreshIntervalMillis)
        assertTrue(config.expandedMetrics.isNotEmpty())
        assertEquals(ResidentIslandIcon.FOLLOW_TITLE, config.leftIcon)
        assertEquals(ResidentIslandIcon.NONE, config.rightIcon)
        assertEquals(ResidentMetricKey.BATTERY_PERCENT, config.leftTitleMetric)
        assertEquals(ResidentMetricKey.BATTERY_PERCENT, config.rightTitleMetric)
        assertEquals(ResidentExpandedContentMode.PRESET, config.expandedContentMode)
        assertEquals(ResidentExpandedContentTemplate.DEFAULT_TEMPLATE, config.expandedContentTemplate)
        assertTrue(config.expandedActions.isEmpty())
    }

    @Test
    fun titleMetricsContainSupportedStatusChoicesButExcludeVoltage() {
        assertEquals(
            listOf(
                ResidentMetricKey.BATTERY_PERCENT,
                ResidentMetricKey.CHARGE_STATE,
                ResidentMetricKey.CURRENT,
                ResidentMetricKey.POWER,
                ResidentMetricKey.BATTERY_TEMPERATURE,
                ResidentMetricKey.FAN_RPM,
            ),
            ResidentMonitorConfig.TITLE_METRICS,
        )
        assertFalse(ResidentMetricKey.VOLTAGE in ResidentMonitorConfig.TITLE_METRICS)
    }

    @Test
    fun normalizationBoundsIntervalRepairsTitlesAndKeepsExpandedVoltage() {
        val normalized =
            ResidentMonitorConfig(
                titleRefreshIntervalMillis = 250L,
                leftIcon = ResidentIslandIcon.NONE,
                rightIcon = ResidentIslandIcon.FOLLOW_TITLE,
                leftTitleMetric = ResidentMetricKey.VOLTAGE,
                rightTitleMetric = ResidentMetricKey.VOLTAGE,
                expandedMetrics =
                    listOf(
                        ResidentMetricKey.VOLTAGE,
                        ResidentMetricKey.CURRENT,
                    ),
            ).normalized()

        assertEquals(ResidentMonitorConfig.MIN_REFRESH_INTERVAL_MILLIS, normalized.titleRefreshIntervalMillis)
        assertEquals(ResidentIslandIcon.NONE, normalized.leftIcon)
        assertEquals(ResidentIslandIcon.FOLLOW_TITLE, normalized.rightIcon)
        assertEquals(ResidentMetricKey.BATTERY_PERCENT, normalized.leftTitleMetric)
        assertEquals(ResidentMetricKey.BATTERY_PERCENT, normalized.rightTitleMetric)
        assertEquals(
            listOf(ResidentMetricKey.VOLTAGE, ResidentMetricKey.CURRENT),
            normalized.expandedMetrics,
        )
    }

    @Test
    fun normalizationRepairsEmptyExpandedMetrics() {
        val normalized = ResidentMonitorConfig(expandedMetrics = emptyList()).normalized()

        assertEquals(ResidentMonitorConfig.DEFAULT_EXPANDED_METRICS, normalized.expandedMetrics)
    }

    @Test
    fun normalizationDeduplicatesExpandedMetricsInSelectionOrder() {
        val normalized =
            ResidentMonitorConfig(
                expandedMetrics =
                    listOf(
                        ResidentMetricKey.CURRENT,
                        ResidentMetricKey.BATTERY_PERCENT,
                        ResidentMetricKey.CURRENT,
                    ),
            ).normalized()

        assertEquals(
            listOf(ResidentMetricKey.CURRENT, ResidentMetricKey.BATTERY_PERCENT),
            normalized.expandedMetrics,
        )
    }

    @Test
    fun normalizationKeepsValidTemplateAndRetainsInactiveInvalidPresetDraft() {
        val valid = ResidentMonitorConfig(expandedContentTemplate = "电量 {battery}").normalized()
        val invalid = ResidentMonitorConfig(expandedContentTemplate = "{unknown}").normalized()

        assertEquals("电量 {battery}", valid.expandedContentTemplate)
        assertEquals("{unknown}", invalid.expandedContentTemplate)
        assertFalse(invalid.hasValidExpandedContentTemplate())
    }

    @Test
    fun normalizationRejectsBlankOrInvalidCustomContent() {
        val blank =
            ResidentMonitorConfig(
                expandedContentMode = ResidentExpandedContentMode.CUSTOM,
                expandedContentTemplate = " \n ",
            )
        val invalid =
            ResidentMonitorConfig(
                expandedContentMode = ResidentExpandedContentMode.CUSTOM,
                expandedContentTemplate = "{unknown}",
            )

        assertFalse(blank.hasValidCustomContent())
        assertFalse(invalid.hasValidCustomContent())
        listOf(blank.normalized(), invalid.normalized()).forEach { normalized ->
            assertEquals(ResidentExpandedContentMode.PRESET, normalized.expandedContentMode)
            assertEquals(ResidentExpandedContentTemplate.DEFAULT_TEMPLATE, normalized.expandedContentTemplate)
        }
    }

    @Test
    fun normalizationKeepsAtMostThreeValidUniqueActionsInOrder() {
        fun action(id: String) =
            ResidentExpandedAction(
                id = id,
                label = id,
                type = ResidentExpandedActionType.REFRESH_NOW,
            )

        val normalized =
            ResidentMonitorConfig(
                expandedActions =
                    listOf(
                        action("first"),
                        action("invalid id"),
                        action("second"),
                        action("first"),
                        action("third"),
                        action("fourth"),
                    ),
            ).normalized()

        assertEquals(listOf("first", "second", "third"), normalized.expandedActions.map { it.id })
    }
}
