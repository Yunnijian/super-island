package io.github.superisland.publisher.focus

internal const val MAX_FOCUS_PARAM_BYTES = 3_072

/** Validates Xiaomi's focus payload limit in the UTF-8 encoding used on the wire. */
internal fun checkFocusPayloadSize(payload: String) {
    check(payload.toByteArray(Charsets.UTF_8).size <= MAX_FOCUS_PARAM_BYTES) {
        "Focus payload exceeds Xiaomi's $MAX_FOCUS_PARAM_BYTES-byte limit"
    }
}
