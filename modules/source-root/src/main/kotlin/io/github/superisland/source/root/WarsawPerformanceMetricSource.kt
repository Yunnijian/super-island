package io.github.superisland.source.root

import io.github.superisland.model.WarsawPerformanceMetricSnapshot
import kotlin.math.roundToInt
import java.util.concurrent.TimeUnit

/**
 * Fixed, read-only `warsaw` CPU/GPU frequency source.
 *
 * The device gate is checked before the reader is invoked. The sole Root command has three
 * compile-time node paths and accepts no caller-supplied command, path, or argument.
 */
class WarsawPerformanceMetricSource private constructor(
    private val outputReader: WarsawPerformanceOutputReader = ProcessWarsawPerformanceOutputReader(),
) {
    constructor() : this(ProcessWarsawPerformanceOutputReader())

    internal constructor(
        outputReader: WarsawPerformanceOutputReader,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit,
    ) : this(outputReader)

    fun read(
        adapter: RootDeviceAdapterCapability,
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): Result<WarsawPerformanceMetricSnapshot> {
        if (!adapter.supports(RootDeviceFeature.PERFORMANCE_TELEMETRY)) {
            return Result.failure(RootMetricReadException("warsaw performance adapter is unavailable"))
        }
        return outputReader.read().mapCatching { output ->
            WarsawPerformanceOutputParser.parse(output, capturedAtMillis)
        }
    }
}

internal fun interface WarsawPerformanceOutputReader {
    fun read(): Result<String>
}

private class ProcessWarsawPerformanceOutputReader : WarsawPerformanceOutputReader {
    override fun read(): Result<String> =
        runCatching {
            val process =
                ProcessBuilder("su", "-c", PERFORMANCE_READ_COMMAND)
                    .redirectErrorStream(true)
                    .start()
            try {
                if (!process.waitFor(ROOT_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    throw RootMetricReadException("Root performance read timed out")
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (output.length > MAX_OUTPUT_CHARS) {
                    throw RootMetricReadException("Root performance output exceeded its limit")
                }
                if (process.exitValue() != 0) {
                    throw RootMetricReadException("Root performance read failed")
                }
                output
            } finally {
                process.destroy()
            }
        }

    private companion object {
        const val ROOT_COMMAND_TIMEOUT_MILLIS = 1_200L
        const val MAX_OUTPUT_CHARS = 4 * 1024

        // Exactly three compile-time read-only nodes; no user input reaches `su`.
        val PERFORMANCE_READ_COMMAND =
            """
            printf 'cpu_policy0_khz='
            cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq
            printf 'cpu_policy6_khz='
            cat /sys/devices/system/cpu/cpufreq/policy6/scaling_cur_freq
            printf 'gpu_hz='
            cat /sys/class/kgsl/kgsl-3d0/gpuclk
            """.trimIndent()
    }
}

internal object WarsawPerformanceOutputParser {
    private val expectedKeys = setOf("cpu_policy0_khz", "cpu_policy6_khz", "gpu_hz")

    fun parse(output: String, capturedAtMillis: Long): WarsawPerformanceMetricSnapshot {
        val values = mutableMapOf<String, Long>()
        output.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0 || separator == line.lastIndex) {
                throw RootMetricReadException("Malformed Root performance output")
            }
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1).toLongOrNull()
                ?: throw RootMetricReadException("Non-numeric Root performance output")
            if (key !in expectedKeys || values.put(key, value) != null) {
                throw RootMetricReadException("Unexpected Root performance output")
            }
        }
        if (values.keys != expectedKeys) {
            throw RootMetricReadException("Root performance output is incomplete")
        }
        return try {
            WarsawPerformanceMetricSnapshot(
                cpuPolicy0Mhz = (values.getValue("cpu_policy0_khz") / 1_000.0).roundToInt(),
                cpuPolicy6Mhz = (values.getValue("cpu_policy6_khz") / 1_000.0).roundToInt(),
                gpuMhz = (values.getValue("gpu_hz") / 1_000_000.0).roundToInt(),
                capturedAtMillis = capturedAtMillis,
            )
        } catch (error: IllegalArgumentException) {
            throw RootMetricReadException("Root performance values are outside the supported range", error)
        }
    }
}
