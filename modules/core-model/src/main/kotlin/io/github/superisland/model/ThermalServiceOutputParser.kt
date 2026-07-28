package io.github.superisland.model

/**
 * Parses the live HAL section of a fixed `dumpsys thermalservice` result.
 *
 * The parser deliberately drops raw text and sensor identifiers. Root callers receive only this
 * bounded aggregate model.
 */
object ThermalServiceOutputParser {
    private val temperaturePattern =
        Regex(
            """Temperature\{mValue=([-+]?\d+(?:\.\d+)?), mType=\d+, mName=([^,}]+), mStatus=\d+\}""",
        )

    fun parse(output: String, capturedAtMillis: Long): ThermalDiagnosticSnapshot {
        if (output.isBlank() || output.contains("Permission Denial", ignoreCase = true)) {
            throw ThermalServiceDiagnosticException("thermalservice returned no readable diagnostic data")
        }
        val header = "Current temperatures from HAL:"
        if (!output.contains(header)) {
            throw ThermalServiceDiagnosticException("thermalservice did not expose a HAL temperature section")
        }
        val halSection = output.substringAfter(header).substringBefore("Current cooling devices from HAL:")
        val readings =
            temperaturePattern.findAll(halSection).map { match ->
                val value = match.groupValues[1].toDoubleOrNull()
                    ?: throw ThermalServiceDiagnosticException("thermalservice returned a malformed temperature")
                match.groupValues[2].lowercase() to value
            }.toList()

        fun highestMatching(prefix: String): Double? =
            readings.filter { (name, _) -> name.startsWith(prefix) }.maxOfOrNull { (_, value) -> value }

        fun exact(name: String): Double? =
            readings.lastOrNull { (sensorName, _) -> sensorName == name }?.second

        return ThermalDiagnosticSnapshot(
            sensorCount = readings.size,
            cpuMaxCelsius = highestMatching("cpu"),
            gpuMaxCelsius = highestMatching("gpu"),
            skinCelsius = exact("skin"),
            batteryCelsius = exact("battery"),
            capturedAtMillis = capturedAtMillis,
        )
    }
}

class ThermalServiceDiagnosticException(message: String) : IllegalStateException(message)
