package io.github.superisland

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.superisland.publisher.focus.LyricIslandContract

/**
 * Owns the lyric-island switch: local SharedPreferences for UI echo, libxposed
 * RemotePreferences for the injected SystemUI host.
 */
object LyricIslandHostConfigSync {
    private const val TAG = "SuperIslandLyricConfig"
    private const val LOCAL_FILE = "lyric_island_config"
    private const val LOCAL_KEY_ENABLED = "enabled"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            appContext = context.applicationContext
            XposedRuntimeController.start()
            started = true
        }
    }

    fun isEnabled(): Boolean {
        val context = appContext ?: return false
        return localPreferences(context).getBoolean(LOCAL_KEY_ENABLED, false)
    }

    fun sync(enabled: Boolean): Result<Unit> =
        runCatching {
            val context = checkNotNull(appContext) { "Lyric host config sync has not started" }
            // Local echo first so re-entering the page shows the persisted value immediately.
            localPreferences(context).edit().putBoolean(LOCAL_KEY_ENABLED, enabled).commit()
            val preferences =
                checkNotNull(
                    XposedRuntimeController.remotePreferences(
                        LyricIslandContract.REMOTE_PREFERENCES,
                    ),
                ) { "LSPosed SystemUI scope is not active" }
            check(
                preferences.edit()
                    .putBoolean(LyricIslandContract.KEY_ENABLED, enabled)
                    .commit(),
            ) { "Lyric island RemotePreferences write failed" }
        }.onFailure { error ->
            Log.w(TAG, "Could not sync lyric-island config", error)
        }

    private fun localPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(LOCAL_FILE, Context.MODE_PRIVATE)
}
