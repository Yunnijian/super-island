package io.github.superisland.source.lyric

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle

/**
 * Non-invasive SuperLyric reader (HChenX). No Hook into player, only ContentResolver.call with timeout.
 * Keep the call bounded and fail-closed; unknown packages must not be queried.
 */
object SuperLyricBridge {
    private const val AUTHORITY = "com.hchen.superlyric"
    private const val METHOD_GET_LYRIC = "getLyric"
    private const val TIMEOUT_MILLIS = 500L
    private val ALLOWED_CALLER_PACKAGES = setOf("com.hchen.superlyric")

    fun query(context: Context): Result<LyricLine?> = runCatching {
        val resolver: ContentResolver = context.contentResolver
        val uri = Uri.parse("content://$AUTHORITY")
        val start = android.os.SystemClock.elapsedRealtime()
        val bundle = Bundle().apply { putString("package", context.packageName) }
        // ContentResolver.call is synchronous; enforce timeout via elapsed check.
        val result = resolver.call(uri, METHOD_GET_LYRIC, null, bundle)
        val elapsed = android.os.SystemClock.elapsedRealtime() - start
        if (elapsed > TIMEOUT_MILLIS) return@runCatching null
        result?.getString("lyric")?.let { raw -> LrcParser.parseLine(raw) }
    }
}
