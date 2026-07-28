package io.github.superisland.ui.resident

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.superisland.displayName
import io.github.superisland.model.BatteryMetricSnapshot
import io.github.superisland.model.ResidentExpandedContentMode
import io.github.superisland.model.ResidentExpandedAction
import io.github.superisland.model.ResidentExpandedContentTemplate
import io.github.superisland.model.ResidentExpandedTemplateError
import io.github.superisland.model.ResidentExpandedTemplateErrorCode
import io.github.superisland.model.ResidentMetricFormatter
import io.github.superisland.model.ResidentMetricKey
import io.github.superisland.model.ResidentMonitorConfig
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class ResidentExpandedContentPreset(
    val id: String,
    val title: String,
    val summary: String,
    val metrics: List<ResidentMetricKey>,
)

internal object ResidentExpandedContentPresets {
    const val BATTERY_CURRENT = "BATTERY_CURRENT"
    const val POWER_STATUS = "POWER_STATUS"
    const val BATTERY_TEMPERATURE = "BATTERY_TEMPERATURE"
    const val COMPLETE_STATUS = "COMPLETE_STATUS"
    const val CURRENT_PRESET = "CURRENT_PRESET"
    const val CUSTOM = "CUSTOM"

    val items =
        listOf(
            ResidentExpandedContentPreset(
                id = BATTERY_CURRENT,
                title = "电量与电流",
                summary = "电量百分比和实时电流",
                metrics = listOf(ResidentMetricKey.BATTERY_PERCENT, ResidentMetricKey.CURRENT),
            ),
            ResidentExpandedContentPreset(
                id = POWER_STATUS,
                title = "用电状态",
                summary = "充电状态、实时电流和功耗",
                metrics =
                    listOf(
                        ResidentMetricKey.CHARGE_STATE,
                        ResidentMetricKey.CURRENT,
                        ResidentMetricKey.POWER,
                    ),
            ),
            ResidentExpandedContentPreset(
                id = BATTERY_TEMPERATURE,
                title = "电量与温度",
                summary = "电量百分比和电池温度",
                metrics =
                    listOf(
                        ResidentMetricKey.BATTERY_PERCENT,
                        ResidentMetricKey.BATTERY_TEMPERATURE,
                    ),
            ),
            ResidentExpandedContentPreset(
                id = COMPLETE_STATUS,
                title = "完整设备状态",
                summary = "电量、电流、功耗和电池温度",
                metrics = ResidentMonitorConfig.DEFAULT_EXPANDED_METRICS,
            ),
        )

    fun selectedId(config: ResidentMonitorConfig): String =
        if (config.expandedContentMode == ResidentExpandedContentMode.CUSTOM) {
            CUSTOM
        } else {
            items.firstOrNull { it.metrics == config.expandedMetrics }?.id ?: CURRENT_PRESET
        }

    fun options(config: ResidentMonitorConfig): List<ResidentExpandedContentPreset> =
        buildList {
            addAll(items)
            if (
                config.expandedContentMode == ResidentExpandedContentMode.PRESET &&
                items.none { it.metrics == config.expandedMetrics }
            ) {
                add(
                    ResidentExpandedContentPreset(
                        id = CURRENT_PRESET,
                        title = "当前指标组合",
                        summary = config.expandedMetrics.joinToString("、") { it.displayName },
                        metrics = config.expandedMetrics,
                    ),
                )
            }
        }

    fun saveDraft(
        original: ResidentMonitorConfig,
        selectedId: String,
        customTemplate: String,
    ): ResidentMonitorConfig {
        val validCustomTemplate =
            customTemplate.takeIf { template ->
                template.isNotBlank() && ResidentExpandedContentTemplate.validate(template).isValid
            }
        if (selectedId == CUSTOM) {
            return validCustomTemplate?.let { template ->
                original
                    .copy(
                        expandedContentMode = ResidentExpandedContentMode.CUSTOM,
                        expandedContentTemplate = template,
                    ).normalized()
            } ?: original
        }

        val preset =
            if (selectedId == CURRENT_PRESET) {
                ResidentExpandedContentPreset(
                    id = CURRENT_PRESET,
                    title = "当前指标组合",
                    summary = "",
                    metrics = original.expandedMetrics,
                )
            } else {
                items.firstOrNull { it.id == selectedId } ?: items.last()
            }
        return original
            .copy(
                expandedContentMode = ResidentExpandedContentMode.PRESET,
                expandedMetrics = preset.metrics,
                // Keep the last valid custom draft so switching back to custom does not erase it.
                expandedContentTemplate = validCustomTemplate ?: original.expandedContentTemplate,
            ).normalized()
    }
}

internal fun insertResidentExpandedToken(
    value: TextFieldValue,
    token: String,
): TextFieldValue {
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(start, value.text.length)
    val updated = value.text.replaceRange(start, end, token)
    val cursor = start + token.length
    return TextFieldValue(
        text = updated,
        selection = TextRange(cursor),
    )
}

internal fun upsertResidentExpandedAction(
    actions: List<ResidentExpandedAction>,
    candidate: ResidentExpandedAction,
): List<ResidentExpandedAction> {
    if (!candidate.isValid()) return actions
    val index = actions.indexOfFirst { action -> action.id == candidate.id }
    return if (index >= 0) {
        actions.toMutableList().apply { set(index, candidate) }
    } else if (actions.size < ResidentExpandedAction.MAX_ACTIONS) {
        actions + candidate
    } else {
        actions
    }
}

internal fun moveResidentExpandedAction(
    actions: List<ResidentExpandedAction>,
    actionId: String,
    offset: Int,
): List<ResidentExpandedAction> {
    val fromIndex = actions.indexOfFirst { action -> action.id == actionId }
    val toIndex = fromIndex + offset
    if (fromIndex !in actions.indices || toIndex !in actions.indices) return actions
    return actions.toMutableList().apply {
        val moved = removeAt(fromIndex)
        add(toIndex, moved)
    }
}

internal data class ResidentExpandedPreview(
    val text: String,
    val validationMessage: String?,
) {
    val isValid: Boolean
        get() = validationMessage == null
}

internal fun residentExpandedPreview(
    selectedId: String,
    template: String,
    values: Map<String, String?>,
    currentPresetMetrics: List<ResidentMetricKey> = ResidentMonitorConfig.DEFAULT_EXPANDED_METRICS,
): ResidentExpandedPreview {
    if (selectedId != ResidentExpandedContentPresets.CUSTOM) {
        val preset =
            if (selectedId == ResidentExpandedContentPresets.CURRENT_PRESET) {
                ResidentExpandedContentPreset(
                    id = ResidentExpandedContentPresets.CURRENT_PRESET,
                    title = "当前指标组合",
                    summary = "",
                    metrics = currentPresetMetrics,
                )
            } else {
                ResidentExpandedContentPresets.items.firstOrNull { it.id == selectedId }
                    ?: ResidentExpandedContentPresets.items.last()
            }
        val rendered =
            preset.metrics.mapNotNull { metric ->
                val value = values[metric.templateToken()].takeUnless { it.isNullOrBlank() }
                value?.let { "${metric.displayName}: $it" }
            }.joinToString(" · ")
        return ResidentExpandedPreview(
            text = rendered.ifBlank { "设备状态正在更新" },
            validationMessage = null,
        )
    }

    if (template.isBlank()) {
        return ResidentExpandedPreview(text = "--", validationMessage = "自定义展开内容不能为空")
    }
    val validation = ResidentExpandedContentTemplate.validate(template)
    if (!validation.isValid) {
        return ResidentExpandedPreview(
            text = "--",
            validationMessage = validation.errors.first().userMessage(),
        )
    }
    val rendered = ResidentExpandedContentTemplate.render(template, values)
    return if (rendered.isSuccess) {
        ResidentExpandedPreview(text = rendered.text.orEmpty().trim(), validationMessage = null)
    } else {
        ResidentExpandedPreview(
            text = "--",
            validationMessage = rendered.errors.first().userMessage(),
        )
    }
}

internal fun residentExpandedPreviewValues(
    snapshot: BatteryMetricSnapshot,
    fanRpm: Int?,
    now: ZonedDateTime = ZonedDateTime.now(),
    locale: Locale = Locale.getDefault(),
): Map<String, String?> =
    linkedMapOf(
        "time" to now.format(DateTimeFormatter.ofPattern("HH:mm:ss", locale)),
        "date" to now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
        "weekday" to now.format(DateTimeFormatter.ofPattern("EEEE", locale)),
        "battery" to snapshot.levelPercent?.let { "$it%" },
        "charge_state" to snapshot.chargeState.displayName,
        "battery_temp" to
            ResidentMetricFormatter.temperatureTenthsCelsius(snapshot.temperatureTenthsCelsius),
        "current" to ResidentMetricFormatter.currentMicroAmps(snapshot.currentMicroAmps),
        "voltage" to snapshot.voltageMillivolts?.let { "${it}mV" },
        "power" to ResidentMetricFormatter.powerWattsFromMicrowatts(snapshot.powerMicrowatts),
        "fan_rpm" to ResidentMetricFormatter.fanRpm(fanRpm),
    )

private fun ResidentMetricKey.templateToken(): String =
    when (this) {
        ResidentMetricKey.BATTERY_PERCENT -> "battery"
        ResidentMetricKey.CHARGE_STATE -> "charge_state"
        ResidentMetricKey.CURRENT -> "current"
        ResidentMetricKey.VOLTAGE -> "voltage"
        ResidentMetricKey.POWER -> "power"
        ResidentMetricKey.BATTERY_TEMPERATURE -> "battery_temp"
        ResidentMetricKey.FAN_RPM -> "fan_rpm"
        else -> ""
    }

private fun ResidentExpandedTemplateError.userMessage(): String =
    when (code) {
        ResidentExpandedTemplateErrorCode.TEMPLATE_TOO_LONG ->
            "模板最多 ${ResidentExpandedContentTemplate.MAX_LENGTH} 个字符"
        ResidentExpandedTemplateErrorCode.RENDERED_TEXT_TOO_LONG ->
            "代入真实数据后最多 ${ResidentExpandedContentTemplate.MAX_LENGTH} 个字符"
        ResidentExpandedTemplateErrorCode.UNKNOWN_TOKEN -> "不支持的占位符 ${token.orEmpty()}"
        ResidentExpandedTemplateErrorCode.BROKEN_TOKEN -> "占位符格式不完整（位置 ${position + 1}）"
    }
