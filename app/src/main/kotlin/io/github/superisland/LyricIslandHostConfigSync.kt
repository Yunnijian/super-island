package io.github.superisland

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.superisland.publisher.focus.LyricIslandContract
import io.github.superisland.source.lyric.LyricIslandConfig
import io.github.superisland.source.lyric.LyricIslandConfigCodec

/**
 * Owns the lyric-island switch: local SharedPreferences for UI echo, libxposed
 * RemotePreferences for the injected SystemUI host.
 */
object LyricIslandHostConfigSync {
    private const val TAG = "SuperIslandLyricConfig"
    private const val LOCAL_FILE = "lyric_island_config"
    private const val LOCAL_KEY_ENABLED = "enabled"
    private const val LOCAL_KEY_JSON = "config_json"

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

    fun loadConfig(): LyricIslandConfig {
        val context = appContext ?: return LyricIslandConfig()
        val preferences = localPreferences(context)
        val raw = preferences.getString(LOCAL_KEY_JSON, null)
        return if (raw.isNullOrBlank()) {
            LyricIslandConfig(enabled = preferences.getBoolean(LOCAL_KEY_ENABLED, false))
        } else {
            LyricIslandConfigCodec.decode(raw).copy(
                enabled = preferences.getBoolean(LOCAL_KEY_ENABLED, false),
            )
        }
    }

    fun sync(enabled: Boolean): Result<Unit> =
        sync(loadConfig().copy(enabled = enabled))

    fun sync(config: LyricIslandConfig): Result<Unit> =
        runCatching {
            val context = checkNotNull(appContext) { "Lyric host config sync has not started" }
            val normalized = config.normalized()
            val encoded = LyricIslandConfigCodec.encode(normalized)
            val local = localPreferences(context)
            val previousEnabled = local.getBoolean(LOCAL_KEY_ENABLED, false)
            val previousJson = local.getString(LOCAL_KEY_JSON, null)
            try {
                // Commit the local echo only as part of the same logical transaction as the
                // protected SystemUI write. A missing LSPosed bridge must not leave stale UI state.
                check(
                    local.edit()
                        .putBoolean(LOCAL_KEY_ENABLED, normalized.enabled)
                        .putString(LOCAL_KEY_JSON, encoded)
                        .commit(),
                ) { "Lyric island local config write failed" }
                val preferences =
                    checkNotNull(
                        XposedRuntimeController.remotePreferences(
                            LyricIslandContract.REMOTE_PREFERENCES,
                        ),
                    ) { "LSPosed SystemUI scope is not active" }
                check(
                    preferences.edit()
                        .putBoolean(LyricIslandContract.KEY_ENABLED, normalized.enabled)
                        .putString(LyricIslandContract.KEY_CONFIG_JSON, encoded)
                        .commit(),
                ) { "Lyric island RemotePreferences write failed" }
            } catch (error: Throwable) {
                val rollback = local.edit().putBoolean(LOCAL_KEY_ENABLED, previousEnabled)
                if (previousJson == null) rollback.remove(LOCAL_KEY_JSON) else rollback.putString(LOCAL_KEY_JSON, previousJson)
                rollback.commit()
                throw error
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not sync lyric-island config", error)
        }

    private fun localPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(LOCAL_FILE, Context.MODE_PRIVATE)
}
