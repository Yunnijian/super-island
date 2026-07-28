package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidentExpandedContentTemplateTest {
    @Test
    fun exposesStableWhitelistedTokenMetadata() {
        assertEquals(
            listOf(
                "time",
                "date",
                "weekday",
                "battery",
                "charge_state",
                "battery_temp",
                "current",
                "voltage",
                "power",
                "fan_rpm",
            ),
            ResidentExpandedContentTemplate.tokens().map(ResidentExpandedToken::name),
        )
        assertTrue(ResidentExpandedContentTemplate.tokens().all { it.displayName.isNotBlank() })
        assertTrue(ResidentExpandedContentTemplate.tokens().all { it.placeholder == "{${it.name}}" })
    }

    @Test
    fun interpolatesLiteralTextTokensAndNewlines() {
        val result =
            ResidentExpandedContentTemplate.render(
                "{time} {weekday}\n电量 {battery}",
                mapOf("time" to "14:30", "weekday" to "星期日", "battery" to "85%"),
            )

        assertTrue(result.isSuccess)
        assertEquals("14:30 星期日\n电量 85%", result.text)
    }

    @Test
    fun rendersRepeatedTokensWithoutMutatingValues() {
        val result =
            ResidentExpandedContentTemplate.render(
                "{battery}/{battery} {current}",
                mapOf("battery" to "80%", "current" to "{power}"),
            )

        assertEquals("80%/80% {power}", result.text)
    }

    @Test
    fun missingNullAndBlankValuesUseUnavailableMarker() {
        val result =
            ResidentExpandedContentTemplate.render(
                "{battery} {current} {fan_rpm}",
                mapOf("battery" to null, "current" to ""),
            )

        assertEquals("-- -- --", result.text)
    }

    @Test
    fun rejectsUnknownAndBrokenTokens() {
        val unknown = ResidentExpandedContentTemplate.validate("{cpu_load}")
        val unclosed = ResidentExpandedContentTemplate.validate("{battery")
        val strayClose = ResidentExpandedContentTemplate.validate("battery}")
        val malformed = ResidentExpandedContentTemplate.validate("{battery-status}")

        assertEquals(ResidentExpandedTemplateErrorCode.UNKNOWN_TOKEN, unknown.errors.single().code)
        assertEquals(ResidentExpandedTemplateErrorCode.BROKEN_TOKEN, unclosed.errors.single().code)
        assertEquals(ResidentExpandedTemplateErrorCode.BROKEN_TOKEN, strayClose.errors.single().code)
        assertEquals(ResidentExpandedTemplateErrorCode.BROKEN_TOKEN, malformed.errors.single().code)
        assertNull(ResidentExpandedContentTemplate.render("{cpu_load}", emptyMap()).text)
    }

    @Test
    fun enforcesTemplateAndRenderedLengthLimit() {
        val exact = "x".repeat(ResidentExpandedContentTemplate.MAX_LENGTH)
        val tooLong = "$exact!"
        val renderedTooLong =
            ResidentExpandedContentTemplate.render(
                "{battery}",
                mapOf("battery" to tooLong),
            )

        assertTrue(ResidentExpandedContentTemplate.validate(exact).isValid)
        assertEquals(
            ResidentExpandedTemplateErrorCode.TEMPLATE_TOO_LONG,
            ResidentExpandedContentTemplate.validate(tooLong).errors.single().code,
        )
        assertEquals(
            ResidentExpandedTemplateErrorCode.RENDERED_TEXT_TOO_LONG,
            renderedTooLong.errors.single().code,
        )
        assertNull(renderedTooLong.text)
    }

    @Test
    fun defaultTemplateIsSafeValidAndRenderable() {
        val validation =
            ResidentExpandedContentTemplate.validate(ResidentExpandedContentTemplate.DEFAULT_TEMPLATE)
        val rendered =
            ResidentExpandedContentTemplate.render(
                ResidentExpandedContentTemplate.DEFAULT_TEMPLATE,
                emptyMap(),
            )

        assertTrue(validation.isValid)
        assertTrue(validation.referencedTokens.isNotEmpty())
        assertTrue(rendered.isSuccess)
        assertFalse(rendered.text.isNullOrBlank())
        assertTrue(rendered.text!!.contains(ResidentExpandedContentTemplate.UNAVAILABLE_VALUE))
    }
}
