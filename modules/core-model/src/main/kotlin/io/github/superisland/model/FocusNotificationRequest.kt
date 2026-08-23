package io.github.superisland.model

/**
 * The app-owned content rendered by HyperOS through Xiaomi's focus-notification protocol.
 *
 * This is intentionally separate from Android's Live Update model: a focus notification uses
 * `miui.focus.param` and its island payload, rather than a promoted-ongoing notification.
 */
data class FocusNotificationRequest(
    val title: String,
    val text: String,
    val progress: Int = 0,
    val progressIndeterminate: Boolean = false,
    val shortStatusText: String? = null,
    /** Whether the island payload should render a progress ring for this event. */
    val showProgress: Boolean = false,
) {
    /** Java/Xposed callers compiled against the pre-progress constructor. */
    constructor(
        title: String,
        text: String,
        progress: Int,
        progressIndeterminate: Boolean,
        shortStatusText: String?,
    ) : this(title, text, progress, progressIndeterminate, shortStatusText, false)

    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(text.isNotBlank()) { "text must not be blank" }
        require(progress in 0..100) { "progress must be between 0 and 100" }
        require(shortStatusText == null || shortStatusText.isNotBlank()) {
            "shortStatusText must not be blank"
        }
    }

    val shortStatus: String
        get() = shortStatusText ?: if (progressIndeterminate) "进行中" else "$progress%"
}
