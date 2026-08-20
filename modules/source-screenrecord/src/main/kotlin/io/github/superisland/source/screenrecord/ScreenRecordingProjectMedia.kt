package io.github.superisland.source.screenrecord

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.util.Log
import io.github.superisland.model.ScreenRecordingRootControlContract
import java.util.concurrent.TimeUnit

/**
 * Local probe + Root grant for [OPSTR_PROJECT_MEDIA].
 *
 * SystemUI AppOps [setMode] is not reliable for a non-system caller identity on HyperOS. The
 * closed Root path mirrors [io.github.superisland.source.root.RootThermalMetricSource]: fixed
 * `su -c` commands only, timeout, no caller-provided package/op strings.
 */
object ScreenRecordingProjectMedia {
    const val OPSTR_PROJECT_MEDIA = "android:project_media"
    private const val TAG = "SuperIslandScreenRecord"
    private const val ROOT_TIMEOUT_MILLIS = 2_000L

    fun isAllowed(context: Context): Boolean {
        val appContext = context.applicationContext
        return runCatching {
            val appOps = appContext.getSystemService(AppOpsManager::class.java) ?: return false
            appOps.checkOpNoThrow(
                OPSTR_PROJECT_MEDIA,
                Process.myUid(),
                ScreenRecordingRootControlContract.MODULE_PACKAGE,
            ) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    fun setAllowed(context: Context, allowed: Boolean): Result<Unit> =
        runCatching {
            val command = if (allowed) FIXED_CMD_ALLOW else FIXED_CMD_IGNORE
            val process =
                ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
            try {
                if (!process.waitFor(ROOT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    error("投影媒体权限写入超时")
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (process.exitValue() != 0) {
                    Log.w(TAG, "appops set failed: $output")
                    error("投影媒体权限写入失败")
                }
            } finally {
                process.destroy()
            }

            val actual = isAllowed(context)
            check(actual == allowed) { "投影媒体权限读回失败" }
        }

    // Compile-time fixed commands. Package and op name never come from UI input.
    private const val FIXED_CMD_ALLOW =
        "cmd appops set ${ScreenRecordingRootControlContract.MODULE_PACKAGE} PROJECT_MEDIA allow"
    private const val FIXED_CMD_IGNORE =
        "cmd appops set ${ScreenRecordingRootControlContract.MODULE_PACKAGE} PROJECT_MEDIA ignore"
}
