package io.github.superisland.publisher.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusPayloadSizeLimitTest {
    @Test
    fun `accepts ASCII payload at exact byte limit`() {
        checkFocusPayloadSize("a".repeat(MAX_FOCUS_PARAM_BYTES))
    }

    @Test
    fun `rejects ASCII payload one byte over limit`() {
        val error =
            runCatching {
                checkFocusPayloadSize("a".repeat(MAX_FOCUS_PARAM_BYTES + 1))
            }.exceptionOrNull()

        assertEquals(IllegalStateException::class.java, error?.javaClass)
        assertEquals(
            "Focus payload exceeds Xiaomi's 3072-byte limit",
            error?.message,
        )
    }

    @Test
    fun `accepts Chinese payload at exact UTF-8 byte limit`() {
        // U+7535 encodes to three bytes in UTF-8: 1024 characters == 3072 bytes.
        checkFocusPayloadSize("电".repeat(MAX_FOCUS_PARAM_BYTES / 3))
    }

    @Test
    fun `rejects Chinese payload whose character count fits but UTF-8 bytes do not`() {
        val payload = "电".repeat(MAX_FOCUS_PARAM_BYTES / 3 + 1)

        check(payload.length < MAX_FOCUS_PARAM_BYTES)
        val error = runCatching { checkFocusPayloadSize(payload) }.exceptionOrNull()

        assertEquals(IllegalStateException::class.java, error?.javaClass)
        assertEquals(
            "Focus payload exceeds Xiaomi's 3072-byte limit",
            error?.message,
        )
    }

    @Test
    fun `counts mixed ASCII and Chinese by encoded bytes`() {
        val exactLimit = "a".repeat(MAX_FOCUS_PARAM_BYTES - 3) + "岛"
        val overLimit = exactLimit + "a"

        checkFocusPayloadSize(exactLimit)
        assertEquals(
            IllegalStateException::class.java,
            runCatching { checkFocusPayloadSize(overLimit) }.exceptionOrNull()?.javaClass,
        )
    }

    @Test
    fun `counts supplementary emoji as four UTF-8 bytes`() {
        val exactLimit = "a".repeat(MAX_FOCUS_PARAM_BYTES - 4) + "🔋"
        val overLimit = exactLimit + "a"

        assertEquals(MAX_FOCUS_PARAM_BYTES, exactLimit.toByteArray(Charsets.UTF_8).size)
        checkFocusPayloadSize(exactLimit)
        assertEquals(
            IllegalStateException::class.java,
            runCatching { checkFocusPayloadSize(overLimit) }.exceptionOrNull()?.javaClass,
        )
    }
}
