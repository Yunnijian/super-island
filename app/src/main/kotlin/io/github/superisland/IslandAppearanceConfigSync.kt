package io.github.superisland

import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.superisland.model.IslandAppearanceConfig
import io.github.superisland.model.SystemUiIslandAppearanceContract

/** Mirrors the app-owned island appearance configuration into scoped RemotePreferences. */
object IslandAppearanceConfigSync {
    private const val TAG = "SuperIslandAppearance"

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
                if (status.active) AppBackgroundWork.execute(::syncStoredAndReload)
            }
            started = true
        }
    }

    fun sync(config: IslandAppearanceConfig): Result<Unit> =
        runCatching {
            val context = checkNotNull(appContext) { "Island appearance sync has not started" }
            val preferences =
                checkNotNull(
                    XposedRuntimeController.remotePreferences(
                        SystemUiIslandAppearanceContract.REMOTE_PREFERENCES,
                    ),
                ) { "LSPosed SystemUI scope is not active" }
            check(
                preferences
                    .edit()
                    .putInt(
                        SystemUiIslandAppearanceContract.KEY_SCHEMA_VERSION,
                        SystemUiIslandAppearanceContract.SCHEMA_VERSION,
                    ).putBoolean(
                        SystemUiIslandAppearanceContract.KEY_COLOR_OS_FLUID_CLOUD_STYLE_ENABLED,
                        SystemUiIslandAppearanceContract.COLOR_OS_FLUID_CLOUD_RUNTIME_AVAILABLE &&
                            config.capsule.colorOsFluidCloudStyleEnabled,
                    ).commit(),
            ) { "Could not publish island appearance settings" }
            context.sendBroadcast(
                Intent(SystemUiIslandAppearanceContract.ACTION_RELOAD)
                    .setPackage(SystemUiIslandAppearanceContract.SYSTEM_UI_PACKAGE),
            )
        }

    fun syncStoredAndReload() {
        val context = appContext ?: return
        sync(IslandAppearanceStore(context).load()).onFailure { error ->
            Log.w(TAG, "Island appearance settings will retry after LSPosed reconnects", error)
        }
    }
}
