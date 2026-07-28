package io.github.superisland

import java.util.concurrent.TimeUnit

/**
 * Restarts every process listed in this module's LSPosed [scope.list].
 *
 * Commands are fixed package names with no user-controlled data. Unrelated Xposed targets,
 * launchers, and other apps are never touched.
 *
 * Order is intentional for HyperOS Dynamic Island stability:
 * 1. SystemUI first (owns Focus / resident island).
 * 2. Wait until SystemUI is back so the host can re-register cleanly.
 * 3. XMSF next (Focus auth adapter).
 * 4. MiShare last via force-stop.
 *
 * Killing SystemUI and XMSF at the same instant races Focus auth and can leave a frozen
 * island surface that no longer accepts updates until a full reboot.
 */
object ModuleScopeController {
    /**
     * Must stay identical to `app/src/main/resources/META-INF/xposed/scope.list`.
     */
    val SCOPE_PACKAGES: List<String> =
        listOf(
            "com.android.systemui",
            "com.xiaomi.xmsf",
            "com.miui.mishare.connectivity",
        )

    private const val COMMAND_TIMEOUT_MILLIS = 8_000L
    private const val SYSTEM_UI_RECOVERY_TIMEOUT_MILLIS = 8_000L
    private const val SYSTEM_UI_SETTLE_MILLIS = 700L
    private const val BETWEEN_KILL_MILLIS = 250L

    fun restart(): Result<Unit> =
        runCatching {
            // SystemUI owns the Focus island. Missing match is OK after a prior kill.
            runRoot("pkill -f com.android.systemui", tolerateNoMatch = true)
            waitForPackageProcess("com.android.systemui", shouldExist = false, SYSTEM_UI_RECOVERY_TIMEOUT_MILLIS)
            waitForPackageProcess("com.android.systemui", shouldExist = true, SYSTEM_UI_RECOVERY_TIMEOUT_MILLIS)
            // Let Application.attach + LSPosed host register + first resident post settle before
            // tearing down XMSF, which participates in Focus auth.
            Thread.sleep(SYSTEM_UI_SETTLE_MILLIS)

            runRoot("pkill -f com.xiaomi.xmsf", tolerateNoMatch = true)
            Thread.sleep(BETWEEN_KILL_MILLIS)
            runRoot("am force-stop com.miui.mishare.connectivity", tolerateNoMatch = false)
        }

    private fun waitForPackageProcess(
        packageName: String,
        shouldExist: Boolean,
        timeoutMillis: Long,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val running = isPackageProcessRunning(packageName)
            if (running == shouldExist) {
                return
            }
            Thread.sleep(100L)
        }
        // Do not hard-fail: a slow SystemUI boot should still proceed to XMSF/MiShare so the
        // user is not stuck mid-restart. Host re-register remains best-effort.
    }

    private fun isPackageProcessRunning(packageName: String): Boolean {
        return try {
            val process =
                ProcessBuilder("su", "-c", "pidof $packageName")
                    .redirectErrorStream(true)
                    .start()
            val finished = process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return false
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            process.exitValue() == 0 && output.isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    private fun runRoot(
        command: String,
        tolerateNoMatch: Boolean,
    ) {
        val process =
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
        check(process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            "重启作用域超时：$command"
        }
        val code = process.exitValue()
        if (code == 0) {
            return
        }
        // pkill: 1 = no process matched. Safe when the target is already dead.
        if (tolerateNoMatch && code == 1) {
            return
        }
        error("重启作用域失败（退出码 $code）：$command")
    }
}
