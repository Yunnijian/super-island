package io.github.superisland.ui.resident

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.superisland.model.BatteryChargeState
import io.github.superisland.model.BatteryMetricSnapshot
import io.github.superisland.model.ResidentExpandedContentMode
import io.github.superisland.model.ResidentExpandedAction
import io.github.superisland.model.ResidentExpandedActionType
import io.github.superisland.model.ResidentExpandedContentTemplate
import io.github.superisland.model.ResidentMetricKey
import io.github.superisland.model.ResidentMonitorConfig
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidentExpandedContentModelsTest {
    @Test
    fun tokenInsertionUsesTheCurrentCursorAndReplacesASelection() {
        val atCursor =
            insertResidentExpandedToken(
                TextFieldValue("电量：", selection = TextRange(3)),
                "{battery}",
            )
        assertEquals("电量：{battery}", atCursor.text)
        assertEquals(TextRange(12), atCursor.selection)

        val replacing =
            insertResidentExpandedToken(
                TextFieldValue("功耗：旧值 W", selection = TextRange(3, 5)),
                "{power}",
            )
        assertEquals("功耗：{power} W", replacing.text)
        assertEquals(TextRange(10), replacing.selection)
    }

    @Test
    fun unknownLegacyPresetIsShownAndSavedWithoutDataLoss() {
        val original =
            ResidentMonitorConfig(
                expandedMetrics = listOf(ResidentMetricKey.BATTERY_PERCENT, ResidentMetricKey.FAN_RPM),
            )

        assertEquals(ResidentExpandedContentPresets.CURRENT_PRESET, ResidentExpandedContentPresets.selectedId(original))
        assertTrue(ResidentExpandedContentPresets.options(original).any { it.id == ResidentExpandedContentPresets.CURRENT_PRESET })

        val saved =
            ResidentExpandedContentPresets.saveDraft(
                original = original,
                selectedId = ResidentExpandedContentPresets.CURRENT_PRESET,
                customTemplate = ResidentExpandedContentTemplate.DEFAULT_TEMPLATE,
            )
        assertEquals(original.expandedMetrics, saved.expandedMetrics)
        assertEquals(ResidentExpandedContentMode.PRESET, saved.expandedContentMode)
    }

    @Test
    fun customDraftCommitsModeAndTemplateTogether() {
        val template = "{time}\n电量 {battery}"
        val saved =
            ResidentExpandedContentPresets.saveDraft(
                original = ResidentMonitorConfig(),
                selectedId = ResidentExpandedContentPresets.CUSTOM,
                customTemplate = template,
            )

        assertEquals(ResidentExpandedContentMode.CUSTOM, saved.expandedContentMode)
        assertEquals(template, saved.expandedContentTemplate)
    }

    @Test
    fun invalidDraftNeverReplacesTheLastValidCustomTemplate() {
        val original =
            ResidentMonitorConfig(
                expandedContentTemplate = "电量 {battery}",
            )

        val savedPreset =
            ResidentExpandedContentPresets.saveDraft(
                original = original,
                selectedId = ResidentExpandedContentPresets.COMPLETE_STATUS,
                customTemplate = "  ",
            )
        val rejectedCustom =
            ResidentExpandedContentPresets.saveDraft(
                original = original,
                selectedId = ResidentExpandedContentPresets.CUSTOM,
                customTemplate = "{unknown}",
            )

        assertEquals(original.expandedContentTemplate, savedPreset.expandedContentTemplate)
        assertEquals(original, rejectedCustom)
    }

    @Test
    fun previewUsesSystemUiFormattingAndUnavailableFallbacks() {
        val snapshot =
            BatteryMetricSnapshot(
                levelPercent = 85,
                chargeState = BatteryChargeState.DISCHARGING,
                currentMicroAmps = -1_250_000,
                voltageMillivolts = 4_100,
                temperatureTenthsCelsius = 320,
                capturedAtMillis = 1L,
            )
        val values =
            residentExpandedPreviewValues(
                snapshot = snapshot,
                fanRpm = null,
                now = ZonedDateTime.of(2026, 7, 19, 14, 30, 5, 0, ZoneId.of("UTC")),
                locale = Locale.US,
            )
        val preview =
            residentExpandedPreview(
                selectedId = ResidentExpandedContentPresets.CUSTOM,
                template = "{time} {battery} {current} {voltage} {power} {battery_temp} {fan_rpm}",
                values = values,
            )

        assertTrue(preview.isValid)
        assertEquals("14:30:05 85% -1250 mA 4100mV -5.13 W 32.0°C --", preview.text)
    }

    @Test
    fun emptyOrInvalidCustomTemplateCannotBeSaved() {
        val empty = residentExpandedPreview(ResidentExpandedContentPresets.CUSTOM, "  ", emptyMap())
        val invalid = residentExpandedPreview(ResidentExpandedContentPresets.CUSTOM, "{cpu_load}", emptyMap())

        assertFalse(empty.isValid)
        assertFalse(invalid.isValid)
        assertEquals("--", empty.text)
        assertTrue(invalid.validationMessage!!.contains("不支持"))
    }

    @Test
    fun negativeSubzeroTemperatureKeepsItsSign() {
        val values =
            residentExpandedPreviewValues(
                snapshot =
                    BatteryMetricSnapshot(
                        levelPercent = 50,
                        chargeState = BatteryChargeState.DISCHARGING,
                        currentMicroAmps = -500,
                        voltageMillivolts = 4_000,
                        temperatureTenthsCelsius = -5,
                        capturedAtMillis = 1L,
                    ),
                fanRpm = null,
            )

        assertEquals("-0.5°C", values["battery_temp"])
        assertEquals("-500 µA", values["current"])
    }

    @Test
    fun expandedActionsCanBeInsertedEditedAndReorderedWithoutChangingTheirIds() {
        val refresh =
            ResidentExpandedAction("refresh", "刷新", ResidentExpandedActionType.REFRESH_NOW)
        val battery =
            ResidentExpandedAction("battery", "电池", ResidentExpandedActionType.OPEN_BATTERY_SETTINGS)
        val inserted = upsertResidentExpandedAction(emptyList(), refresh)
        val withSecond = upsertResidentExpandedAction(inserted, battery)
        val edited = upsertResidentExpandedAction(withSecond, refresh.copy(label = "立即刷新"))
        val reordered = moveResidentExpandedAction(edited, "battery", -1)

        assertEquals(listOf("battery", "refresh"), reordered.map(ResidentExpandedAction::id))
        assertEquals("立即刷新", reordered.last().label)
        assertEquals(reordered, moveResidentExpandedAction(reordered, "battery", -1))
    }
}
