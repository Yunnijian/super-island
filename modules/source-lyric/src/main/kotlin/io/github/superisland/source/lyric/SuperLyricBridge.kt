package io.github.superisland.source.lyric

import android.content.Context
import android.net.Uri
import android.os.Bundle

/**
 * Non-invasive SuperLyric reader (HChenX). No Hook into player, only ContentResolver.call with timeout.
 */
object SuperLyricBridge {
    fun query(context: Context): Result<LyricLine?> = runCatching {
        val resolver = context.contentResolver
        val uri = Uri.parse("content://com.hchen.superlyric")
        val bundle = Bundle().apply { putString("package", context.packageName) }
        val result = resolver.call(uri, "getLyric", null, bundle)
        result?.getString("lyric")?.let { raw -> LrcParser.parseLine(raw) }
    }
}
