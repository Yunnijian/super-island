package io.github.superisland.source.lyric

import android.content.Context
import android.media.session.MediaController

/**
 * MediaSession lyricInfo fallback. SuperLyric pushes via Binder listener directly.
 */
object LyricResolver {
    fun resolve(context: Context, controllers: List<MediaController>): LyricLine? {
        controllers.forEach { controller ->
            val metadata = controller.metadata ?: return@forEach
            val lyric = metadata.getString("lyricInfo") ?: return@forEach
            LrcParser.parseLine(lyric)?.let { return it }
        }
        return null
    }
}
