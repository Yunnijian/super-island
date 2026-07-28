package io.github.superisland.model

/** User-facing group for a supported expanded-content placeholder. */
enum class ResidentExpandedTokenCategory(
    val displayName: String,
) {
    TIME_DATE("时间·日期"),
    BATTERY("电量"),
    POWER("功耗·充电"),
    FAN("风扇"),
}

/** Metadata used by both UI skins to present the same placeholder catalogue. */
data class ResidentExpandedToken(
    val name: String,
    val displayName: String,
    val category: ResidentExpandedTokenCategory,
) {
    val placeholder: String
        get() = "{$name}"
}

enum class ResidentExpandedTemplateErrorCode {
    TEMPLATE_TOO_LONG,
    RENDERED_TEXT_TOO_LONG,
    UNKNOWN_TOKEN,
    BROKEN_TOKEN,
}

/** A position is a zero-based UTF-16 character offset, matching Kotlin and Java String indexing. */
data class ResidentExpandedTemplateError(
    val code: ResidentExpandedTemplateErrorCode,
    val position: Int,
    val token: String? = null,
)

data class ResidentExpandedTemplateValidation(
    val errors: List<ResidentExpandedTemplateError>,
    val referencedTokens: List<String>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

data class ResidentExpandedTemplateRenderResult(
    val text: String?,
    val errors: List<ResidentExpandedTemplateError>,
) {
    val isSuccess: Boolean
        get() = errors.isEmpty()
}

/**
 * Android-free parser and renderer for resident-island expanded content.
 *
 * The language intentionally supports only literal text and whitelisted `{token}` placeholders.
 * Values are inserted once as plain text, so neither templates nor values can execute code or
 * recursively expand another placeholder.
 */
object ResidentExpandedContentTemplate {
    const val MAX_LENGTH = 256
    const val UNAVAILABLE_VALUE = "--"
    const val DEFAULT_TEMPLATE =
        "电量 {battery} · {charge_state}\n电流 {current} · 功耗 {power}\n温度 {battery_temp}"

    private val supportedTokens =
        listOf(
            ResidentExpandedToken("time", "时间", ResidentExpandedTokenCategory.TIME_DATE),
            ResidentExpandedToken("date", "日期", ResidentExpandedTokenCategory.TIME_DATE),
            ResidentExpandedToken("weekday", "星期", ResidentExpandedTokenCategory.TIME_DATE),
            ResidentExpandedToken("battery", "电量", ResidentExpandedTokenCategory.BATTERY),
            ResidentExpandedToken("charge_state", "充电状态", ResidentExpandedTokenCategory.BATTERY),
            ResidentExpandedToken("battery_temp", "电池温度", ResidentExpandedTokenCategory.BATTERY),
            ResidentExpandedToken("current", "电流", ResidentExpandedTokenCategory.POWER),
            ResidentExpandedToken("voltage", "电压", ResidentExpandedTokenCategory.POWER),
            ResidentExpandedToken("power", "功耗", ResidentExpandedTokenCategory.POWER),
            ResidentExpandedToken("fan_rpm", "风扇转速", ResidentExpandedTokenCategory.FAN),
        )
    private val supportedTokenNames = supportedTokens.mapTo(linkedSetOf(), ResidentExpandedToken::name)
    private val validTokenName = Regex("[a-z][a-z0-9_]*")

    @JvmStatic
    fun tokens(): List<ResidentExpandedToken> = supportedTokens

    @JvmStatic
    fun validate(template: String): ResidentExpandedTemplateValidation {
        val parsed = parse(template)
        return ResidentExpandedTemplateValidation(
            errors = parsed.errors,
            referencedTokens = parsed.segments.mapNotNull(Segment::token).distinct(),
        )
    }

    @JvmStatic
    fun render(
        template: String,
        values: Map<String, String?>,
    ): ResidentExpandedTemplateRenderResult {
        val parsed = parse(template)
        if (parsed.errors.isNotEmpty()) {
            return ResidentExpandedTemplateRenderResult(text = null, errors = parsed.errors)
        }

        val rendered =
            buildString {
                parsed.segments.forEach { segment ->
                    segment.literal?.let(::append)
                    segment.token?.let { token ->
                        append(values[token].takeUnless { it.isNullOrBlank() } ?: UNAVAILABLE_VALUE)
                    }
                }
            }
        if (rendered.length > MAX_LENGTH) {
            return ResidentExpandedTemplateRenderResult(
                text = null,
                errors =
                    listOf(
                        ResidentExpandedTemplateError(
                            code = ResidentExpandedTemplateErrorCode.RENDERED_TEXT_TOO_LONG,
                            position = MAX_LENGTH,
                        ),
                    ),
            )
        }
        return ResidentExpandedTemplateRenderResult(text = rendered, errors = emptyList())
    }

    private fun parse(template: String): ParsedTemplate {
        val errors = mutableListOf<ResidentExpandedTemplateError>()
        if (template.length > MAX_LENGTH) {
            errors +=
                ResidentExpandedTemplateError(
                    code = ResidentExpandedTemplateErrorCode.TEMPLATE_TOO_LONG,
                    position = MAX_LENGTH,
                )
        }

        val segments = mutableListOf<Segment>()
        var literalStart = 0
        var index = 0
        while (index < template.length) {
            when (template[index]) {
                '{' -> {
                    if (literalStart < index) segments += Segment(literal = template.substring(literalStart, index))
                    val closeIndex = template.indexOf('}', startIndex = index + 1)
                    val nestedOpenIndex = template.indexOf('{', startIndex = index + 1)
                    if (closeIndex == -1 || nestedOpenIndex in (index + 1)..<closeIndex) {
                        errors +=
                            ResidentExpandedTemplateError(
                                code = ResidentExpandedTemplateErrorCode.BROKEN_TOKEN,
                                position = index,
                            )
                        // Preserve the remainder as literal for deterministic scanning; rendering
                        // remains disabled whenever any syntax error is present.
                        segments += Segment(literal = template.substring(index))
                        index = template.length
                        literalStart = index
                    } else {
                        val token = template.substring(index + 1, closeIndex)
                        val errorCode =
                            when {
                                !validTokenName.matches(token) -> ResidentExpandedTemplateErrorCode.BROKEN_TOKEN
                                token !in supportedTokenNames -> ResidentExpandedTemplateErrorCode.UNKNOWN_TOKEN
                                else -> null
                            }
                        if (errorCode == null) {
                            segments += Segment(token = token)
                        } else {
                            errors +=
                                ResidentExpandedTemplateError(
                                    code = errorCode,
                                    position = index,
                                    token = token.takeIf(String::isNotEmpty),
                                )
                        }
                        index = closeIndex + 1
                        literalStart = index
                    }
                }

                '}' -> {
                    errors +=
                        ResidentExpandedTemplateError(
                            code = ResidentExpandedTemplateErrorCode.BROKEN_TOKEN,
                            position = index,
                        )
                    index += 1
                }

                else -> index += 1
            }
        }
        if (literalStart < template.length) segments += Segment(literal = template.substring(literalStart))
        return ParsedTemplate(segments = segments, errors = errors.distinct())
    }

    private data class Segment(
        val literal: String? = null,
        val token: String? = null,
    )

    private data class ParsedTemplate(
        val segments: List<Segment>,
        val errors: List<ResidentExpandedTemplateError>,
    )
}
