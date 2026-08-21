package io.github.superisland.source.lyric

import android.content.Context

/**
 * Host for lyric island, reuse SystemUiResidentIslandHost pattern.
 * OS3/OS4 single miui.focus path per 223ab0e.
 */
object LyricIslandHost {
    fun publish(context: Context, line: LyricLine) {
        // TODO: use LyricPayloadBuilder + FocusNotificationPublisher
    }
}
