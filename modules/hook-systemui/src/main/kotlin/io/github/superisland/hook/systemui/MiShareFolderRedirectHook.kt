package io.github.superisland.hook.systemui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.superisland.model.MiShareFolderRedirectContract
import java.util.IdentityHashMap

data class MiShareFolderRedirectInstallResult(
    val installed: Boolean,
    val failure: String? = null,
)

/**
 * Redirects only Mi Share's verified received-folder Intent builder to the known MT Manager
 * shortcut entry. It deliberately returns Xiaomi's original Intent for every unknown state.
 */
object MiShareFolderRedirectHook {
    private const val TAG = "SuperIslandMiShare"

    private val installLock = Any()
    private val runtimes = IdentityHashMap<ClassLoader, Runtime>()

    @JvmStatic
    fun install(
        module: XposedModule,
        classLoader: ClassLoader,
        applicationInfo: ApplicationInfo,
        versionCode: Long,
        preferences: SharedPreferences,
    ): MiShareFolderRedirectInstallResult =
        synchronized(installLock) {
            if (runtimes.containsKey(classLoader)) return@synchronized MiShareFolderRedirectInstallResult(true)

            var pendingRuntime: Runtime? = null
            try {
                val resolution =
                    MiShareTargetResolver.resolve(
                        classLoader = classLoader,
                        applicationInfo = applicationInfo,
                        versionCode = versionCode,
                        forceSkipExact = BuildConfig.FORCE_MISHARE_DEXKIT_SCAN,
                    )
                val target = resolution.method
                val runtime = Runtime(module, preferences)
                pendingRuntime = runtime
                module.deoptimize(target)
                runtime.hookHandle =
                    module
                        .hook(target)
                        .setExceptionMode(
                            io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE,
                        ).intercept { chain ->
                            val original = chain.proceed()
                            val context = chain.args.firstOrNull() as? Context
                            if (context == null || original !is Intent) {
                                original
                            } else {
                                runtime.redirectOrOriginal(context, original)
                            }
                        }
                runtimes[classLoader] = runtime
                pendingRuntime = null
                module.log(
                    Log.INFO,
                    TAG,
                    "Installed Mi Share received-folder redirect resolver=${resolution.source.logLabel}",
                )
                MiShareFolderRedirectInstallResult(installed = true)
            } catch (error: Throwable) {
                pendingRuntime?.close()
                val failure = "${error.javaClass.simpleName}: ${error.message ?: "unknown"}"
                module.log(Log.WARN, TAG, "Mi Share received-folder redirect unavailable", error)
                MiShareFolderRedirectInstallResult(installed = false, failure = failure)
            }
        }

    private class Runtime(
        private val module: XposedModule,
        private val preferences: SharedPreferences,
    ) {
        @Volatile
        private var enabled = preferences.getBoolean(MiShareFolderRedirectContract.KEY_ENABLED, false)

        private val preferenceListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == MiShareFolderRedirectContract.KEY_ENABLED) {
                    enabled = preferences.getBoolean(MiShareFolderRedirectContract.KEY_ENABLED, false)
                }
            }

        var hookHandle: HookHandle? = null

        init {
            preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        }

        fun close() {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        }

        fun redirectOrOriginal(
            context: Context,
            original: Intent,
        ): Intent {
            if (!enabled) return original
            if (!MiShareFolderRedirectContract.isMiShareFolderIntentAction(original.action)) return original

            return runCatching {
                val receiveDirectory =
                    original
                        .getStringExtra(MiShareFolderRedirectContract.OEM_LEGACY_EXPLORER_PATH_EXTRA)
                        ?.takeIf(MiShareFolderRedirectContract::isCanonicalReceiveDirectory)
                        ?: defaultReceiveDirectory()
                if (!MiShareFolderRedirectContract.isCanonicalReceiveDirectory(receiveDirectory)) {
                    return@runCatching original
                }
                mtShortcutIntentOrNull(context, receiveDirectory) ?: original
            }.getOrElse { error ->
                module.log(Log.WARN, TAG, "Mi Share redirect failed closed", error)
                original
            }
        }

        private fun defaultReceiveDirectory(): String =
            Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
                .trimEnd('/') + "/MiShare"

        private fun mtShortcutIntentOrNull(
            context: Context,
            receiveDirectory: String,
        ): Intent? {
            val intent =
                Intent(MiShareFolderRedirectContract.MT_MANAGER_SHORTCUT_ACTION)
                    .setComponent(
                        ComponentName(
                            MiShareFolderRedirectContract.MT_MANAGER_PACKAGE,
                            MiShareFolderRedirectContract.MT_MANAGER_SHORTCUT_ACTIVITY,
                        ),
                    ).putExtra(MiShareFolderRedirectContract.MT_MANAGER_EXTRA_PATH, receiveDirectory)
                    .putExtra(
                        MiShareFolderRedirectContract.MT_MANAGER_EXTRA_OPERATION,
                        MiShareFolderRedirectContract.MT_MANAGER_OPERATION_GOTO,
                    ).putExtra(
                        MiShareFolderRedirectContract.MT_MANAGER_EXTRA_FOLDER_COLOR_ICON,
                        MiShareFolderRedirectContract.MT_MANAGER_FOLDER_ICON,
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val target =
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo
                    ?: return null
            return intent.takeIf {
                target.exported &&
                    target.packageName == MiShareFolderRedirectContract.MT_MANAGER_PACKAGE &&
                    target.name == MiShareFolderRedirectContract.MT_MANAGER_SHORTCUT_ACTIVITY
            }
        }
    }
}
