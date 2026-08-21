package io.github.superisland.source.lyric

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager

object LyricResolver {
    fun resolve(context: Context, controllers: List<MediaController>): LyricLine? {
        // Priority: SuperLyric first, then MediaSession fallback
        SuperLyricBridge.query(context).getOrNull()?.let { return it }
        // MediaSession fallback: try lyricInfo from metadata (LyricInfo)
        controllers.forEach { controller ->
            val metadata = controller.metadata ?: return@forEach
            val lyric = metadata.getString("lyricInfo") ?: return@forEach
            LrcParser.parseLine(lyric)?.let { return it }
        }
        return null
    }
}
