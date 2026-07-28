package io.github.superisland

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.superisland.model.MiShareFolderRedirectConfig
import io.github.superisland.model.MiShareFolderRedirectContract

/** App-owned persistence for the optional Mi Share received-folder redirect. */
class MiShareFolderExtensionStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): MiShareFolderRedirectConfig =
        MiShareFolderRedirectConfig(
            enabled = preferences.getBoolean(MiShareFolderRedirectContract.KEY_ENABLED, false),
        )

    /**
     * Persists first, then mirrors to the LSPosed RemotePreferences channel. A temporarily absent
     * module service must not discard an intentional user setting; [MiShareFolderExtensionConfigSync]
     * retries it when the service reconnects.
     */
    fun save(config: MiShareFolderRedirectConfig): Result<Unit> =
        runCatching {
            check(
                preferences
                    .edit()
                    .putBoolean(MiShareFolderRedirectContract.KEY_ENABLED, config.enabled)
                    .commit(),
            ) { "Could not persist Mi Share folder extension settings" }
            MiShareFolderExtensionConfigSync.sync(config).onFailure { error ->
                Log.w(TAG, "Mi Share folder extension will retry after LSPosed reconnects", error)
            }
        }

    fun reset(): Result<Unit> = save(MiShareFolderRedirectConfig())

    fun observe(onChanged: (MiShareFolderRedirectConfig) -> Unit): () -> Unit {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == MiShareFolderRedirectContract.KEY_ENABLED) onChanged(load())
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onChanged(load())
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val TAG = "SuperIslandMiShare"
        const val FILE_NAME = "mishare-folder-extension"
    }
}
