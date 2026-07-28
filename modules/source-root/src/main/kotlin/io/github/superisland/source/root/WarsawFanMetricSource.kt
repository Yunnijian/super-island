package io.github.superisland.source.root

import io.github.superisland.model.WarsawFanMetricSnapshot
import java.util.concurrent.TimeUnit

/**
 * Fixed, read-only Root source for the `warsaw` fan adapter.
 *
 * It accepts no caller-supplied command, path, arguments, or writable control. Root is requested
 * only when [read] is called by the Root-mode capability gate.
 */
class WarsawFanMetricSource private constructor(
    private val nodeReader: WarsawFanNodeReader = ProcessWarsawFanNodeReader(),
) {
    constructor() : this(ProcessWarsawFanNodeReader())

    internal constructor(
        nodeReader: WarsawFanNodeReader,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit,
    ) : this(nodeReader)

    fun read(
        adapter: RootDeviceAdapterCapability,
        capturedAtMillis: Long = System.currentTimeMillis(),
    ): Result<WarsawFanMetricSnapshot> {
        if (!adapter.supports(RootDeviceFeature.FAN_TELEMETRY)) {
            return Result.failure(RootMetricReadException("warsaw fan adapter is unavailable"))
        }
        return nodeReader.read().mapCatching { output ->
            WarsawFanOutputParser.parse(output, capturedAtMillis)
        }
    }
}

internal fun interface WarsawFanNodeReader {
    fun read(): Result<String>
}

private class ProcessWarsawFanNodeReader : WarsawFanNodeReader {
    override fun read(): Result<String> =
        runCatching {
            val process =
                ProcessBuilder("su", "-c", FAN_READ_COMMAND)
                    .redirectErrorStream(true)
                    .start()
            if (!process.waitFor(ROOT_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                throw RootMetricReadException("Root fan read timed out")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.exitValue() != 0) {
                throw RootMetricReadException("Root fan read failed")
            }
            output
        }

    private companion object {
        const val ROOT_COMMAND_TIMEOUT_MILLIS = 1_200L

        // The command and four sysfs paths are compile-time constants; there is no user input.
        val FAN_READ_COMMAND =
            """
            for node in real_speed target_level pwm_duty fan_support; do
              printf '%s=' "${'$'}node"
              cat "/sys/devices/platform/soc/soc:xiaomi_fan/${'$'}node"
            done
            """.trimIndent()
    }
}

internal object WarsawFanOutputParser {
    private val expectedKeys = setOf("real_speed", "target_level", "pwm_duty", "fan_support")

    fun parse(output: String, capturedAtMillis: Long): WarsawFanMetricSnapshot {
        val values = mutableMapOf<String, Int>()
        output.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0 || separator == line.lastIndex) {
                throw RootMetricReadException("Malformed Root fan output")
            }
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1).toIntOrNull()
                ?: throw RootMetricReadException("Non-numeric Root fan output")
            if (key !in expectedKeys || values.put(key, value) != null) {
                throw RootMetricReadException("Unexpected Root fan output")
            }
        }
        if (values.keys != expectedKeys || values.getValue("fan_support") != 1) {
            throw RootMetricReadException("Supported warsaw fan telemetry is unavailable")
        }
        return try {
            WarsawFanMetricSnapshot(
                rpm = values.getValue("real_speed"),
                targetLevel = values.getValue("target_level"),
                pwmDutyPercent = values.getValue("pwm_duty"),
                capturedAtMillis = capturedAtMillis,
            )
        } catch (error: IllegalArgumentException) {
            throw RootMetricReadException("Root fan values are outside the supported range", error)
        }
    }
}

internal class RootMetricReadException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
