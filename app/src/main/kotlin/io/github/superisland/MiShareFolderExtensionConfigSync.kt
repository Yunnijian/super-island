package io.github.superisland

import android.content.Context
import android.util.Log
import io.github.superisland.model.MiShareFolderRedirectConfig
import io.github.superisland.model.MiShareFolderRedirectContract

/**
 * Mirrors one boolean into module-owned RemotePreferences for the scoped Mi Share process.
 *
 * This remains separate from the smart-capsule A/B configuration because the extension has no
 * Focus-notification ownership or XMSF authorization role.
 */
object MiShareFolderExtensionConfigSync {
    private const val TAG = "SuperIslandMiShare"

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
            XposedRuntimeController.observe { status ->
                if (status.active) AppBackgroundWork.execute(::syncStored)
            }
            started = true
        }
    }

    fun sync(config: MiShareFolderRedirectConfig): Result<Unit> =
        runCatching {
            checkNotNull(appContext) { "Mi Share folder extension sync has not started" }
            val preferences =
                checkNotNull(
                    XposedRuntimeController.remotePreferences(
                        MiShareFolderRedirectContract.REMOTE_PREFERENCES,
                    ),
                ) { "LSPosed module service is not active" }
            check(
                preferences
                    .edit()
                    .putBoolean(MiShareFolderRedirectContract.KEY_ENABLED, config.enabled)
                    .commit(),
            ) { "Could not mirror Mi Share folder extension settings to LSPosed" }
        }

    fun syncStored() {
        val context = appContext ?: return
        sync(MiShareFolderExtensionStore(context).load()).onFailure { error ->
            Log.w(TAG, "Mi Share folder extension sync deferred", error)
        }
    }
}
