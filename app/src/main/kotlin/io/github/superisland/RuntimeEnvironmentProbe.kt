package io.github.superisland

import java.util.concurrent.TimeUnit

/**
 * Root permission probe only.
 *
 * LSPosed activation is provided by [XposedRuntimeController] through the official libxposed
 * service binder. This class never inspects manager packages, Magisk modules, or properties.
 */
object RuntimeEnvironmentProbe {
    private const val PROBE_TIMEOUT_MS = 1_200L
    private const val LSPOSED_VERSION_COMMAND =
        "file=\$(ls -t /data/adb/lspd/log/verbose_*.log 2>/dev/null | head -n 1); " +
            "[ -n \"\$file\" ] && grep -m 1 'LSPosedService.*version ' \"\$file\""

    fun probeRootPermission(
        commandRunner: (List<String>) -> CommandResult = ::runCommand,
    ): Boolean {
        val result = commandRunner(listOf("su", "-c", "id -u"))
        if (result.timedOut || result.exitCode != 0) {
            return false
        }
        val uid = result.stdout.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        return uid == "0"
    }

    /**
     * Reads the Root implementation's own version output only after the UID-0 permission probe.
     * The result is deliberately concise so visual status cards never turn into diagnostics.
     */
    fun probeRootSummary(
        commandRunner: (List<String>) -> CommandResult = ::runCommand,
    ): String {
        if (!probeRootPermission(commandRunner)) return "未授权"
        val version = commandRunner(listOf("su", "-c", "su -v"))
        return rootImplementationSummary(version.stdout)
    }

    fun rootImplementationSummary(stdout: String): String {
        val line = stdout.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty) ?: return "Root 已授权"
        val kernelSuMarker = ":KernelSU"
        if (line.endsWith(kernelSuMarker, ignoreCase = true)) {
            val version = line.removeSuffix(kernelSuMarker).trim()
            return if (version.isEmpty()) "KernelSU" else "KernelSU $version"
        }
        return when {
            line.contains("magisk", ignoreCase = true) -> line.replace(Regex("\\s+"), " ")
            line.contains("apatch", ignoreCase = true) -> line.replace(Regex("\\s+"), " ")
            else -> "Root 已授权"
        }
    }

    /** Reads the LSPosed daemon's own startup record, which is available even without Manager. */
    fun probeLsposedVersion(
        commandRunner: (List<String>) -> CommandResult = ::runCommand,
    ): String? =
        lsposedVersionSummary(commandRunner(listOf("su", "-c", LSPOSED_VERSION_COMMAND)).stdout)

    fun lsposedVersionSummary(stdout: String): String? =
        Regex("""LSPosedService.*version\s+([0-9]+(?:\.[0-9]+)*(?:\s+\(\d+\))?)""")
            .find(stdout)
            ?.groupValues
            ?.getOrNull(1)

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val timedOut: Boolean = false,
    )

    private fun runCommand(command: List<String>): CommandResult =
        try {
            val process =
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
            val finished = process.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                CommandResult(exitCode = -1, stdout = "", timedOut = true)
            } else {
                CommandResult(
                    exitCode = process.exitValue(),
                    stdout = process.inputStream.bufferedReader().use { it.readText() },
                )
            }
        } catch (_: Exception) {
            CommandResult(exitCode = -1, stdout = "", timedOut = false)
        }
}
