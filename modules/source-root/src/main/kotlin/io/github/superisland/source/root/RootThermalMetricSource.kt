package io.github.superisland.source.root

import io.github.superisland.model.ThermalDiagnosticSnapshot
import io.github.superisland.model.ThermalServiceDiagnosticException
import io.github.superisland.model.ThermalServiceOutputParser
import java.util.concurrent.TimeUnit

/**
 * Fixed, read-only Root diagnostic for live thermalservice aggregates.
 *
 * There are no caller-provided command, argument, path, or output APIs. Raw output is bounded,
 * parsed immediately, and never retained by this source.
 */
class RootThermalMetricSource private constructor(
    private val outputReader: RootThermalOutputReader = ProcessRootThermalOutputReader(),
) {
    constructor() : this(ProcessRootThermalOutputReader())

    internal constructor(
        outputReader: RootThermalOutputReader,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit,
    ) : this(outputReader)

    fun read(capturedAtMillis: Long = System.currentTimeMillis()): Result<ThermalDiagnosticSnapshot> =
        outputReader.read().mapCatching { output ->
            try {
                ThermalServiceOutputParser.parse(output, capturedAtMillis)
            } catch (error: ThermalServiceDiagnosticException) {
                throw RootMetricReadException("Root thermalservice output is unavailable", error)
            }
        }
}

internal fun interface RootThermalOutputReader {
    fun read(): Result<String>
}

private class ProcessRootThermalOutputReader : RootThermalOutputReader {
    override fun read(): Result<String> =
        runCatching {
            val process =
                ProcessBuilder("su", "-c", THERMAL_SERVICE_COMMAND)
                    .redirectErrorStream(true)
                    .start()
            try {
                if (!process.waitFor(ROOT_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    throw RootMetricReadException("Root thermalservice diagnostic timed out")
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (output.length > MAX_OUTPUT_CHARS) {
                    throw RootMetricReadException("Root thermalservice diagnostic output exceeded its limit")
                }
                if (process.exitValue() != 0) {
                    throw RootMetricReadException("Root thermalservice diagnostic failed")
                }
                output
            } finally {
                process.destroy()
            }
        }

    private companion object {
        const val ROOT_COMMAND_TIMEOUT_MILLIS = 2_000L
        const val MAX_OUTPUT_CHARS = 48 * 1024

        // Compile-time fixed read-only command. No caller input reaches `su`.
        const val THERMAL_SERVICE_COMMAND = "/system/bin/dumpsys thermalservice"
    }
}
