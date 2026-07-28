package io.github.superisland

import io.github.superisland.model.BatteryChargeState
import io.github.superisland.model.BatteryMetricSnapshot
import io.github.superisland.model.ResidentMetricKey
import io.github.superisland.model.ResidentMonitorConfig
import io.github.superisland.model.WarsawFanMetricSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ResidentMonitorFocusNotificationRequestTest {
    private val snapshot =
        BatteryMetricSnapshot(
            levelPercent = 83,
            chargeState = BatteryChargeState.CHARGING,
            currentMicroAmps = 1_500_000,
            voltageMillivolts = 4_300,
            temperatureTenthsCelsius = 321,
            capturedAtMillis = 1L,
        )

    @Test
    fun powerUsesWattsInBothTitlesAndExpandedContent() {
        val request =
            snapshot.monitorFocusNotificationRequest(
                config =
                    ResidentMonitorConfig(
                        leftTitleMetric = ResidentMetricKey.POWER,
                        rightTitleMetric = ResidentMetricKey.POWER,
                        expandedMetrics = listOf(ResidentMetricKey.POWER),
                    ),
            )

        assertEquals("+6.45 W", request.title)
        assertEquals("功耗: +6.45 W", request.text)
        assertEquals("+6.45 W", request.shortStatusText)
    }

    @Test
    fun androidNotificationFanFieldsKeepTheCompleteRpmText() {
        val request =
            snapshot.monitorFocusNotificationRequest(
                config =
                    ResidentMonitorConfig(
                        leftTitleMetric = ResidentMetricKey.FAN_RPM,
                        rightTitleMetric = ResidentMetricKey.FAN_RPM,
                        expandedMetrics = listOf(ResidentMetricKey.FAN_RPM),
                    ),
                rootFanSnapshot =
                    WarsawFanMetricSnapshot(
                        rpm = 14_487,
                        targetLevel = 2,
                        pwmDutyPercent = 65,
                        capturedAtMillis = 1L,
                    ),
            )

        assertEquals("14487 RPM", request.title)
        assertEquals("风扇转速: 14487 RPM", request.text)
        assertEquals("14487 RPM", request.shortStatusText)
    }

    @Test
    fun missingFanSnapshotFallsBackToBatteryForBothTitles() {
        val request =
            snapshot.monitorFocusNotificationRequest(
                config =
                    ResidentMonitorConfig(
                        leftTitleMetric = ResidentMetricKey.FAN_RPM,
                        rightTitleMetric = ResidentMetricKey.FAN_RPM,
                        expandedMetrics = listOf(ResidentMetricKey.FAN_RPM),
                    ),
                rootFanSnapshot = null,
            )

        assertEquals("83%", request.title)
        assertEquals("设备状态正在更新", request.text)
        assertEquals("83%", request.shortStatusText)
    }
}
