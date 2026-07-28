package io.github.superisland.model

/**
 * Aggregated, non-persistent output from a privileged thermal diagnostic.
 *
 * Raw `dumpsys thermalservice` text and individual sensor names are deliberately not included in
 * this model. The UI receives only useful aggregate values for the active diagnostic session.
 */
data class ThermalDiagnosticSnapshot(
    val sensorCount: Int,
    val cpuMaxCelsius: Double?,
    val gpuMaxCelsius: Double?,
    val skinCelsius: Double?,
    val batteryCelsius: Double?,
    val capturedAtMillis: Long,
) {
    init {
        require(sensorCount >= 0) { "sensorCount must not be negative" }
        require(capturedAtMillis >= 0) { "capturedAtMillis must not be negative" }
        listOfNotNull(cpuMaxCelsius, gpuMaxCelsius, skinCelsius, batteryCelsius).forEach { value ->
            require(value in -273.15..1_000.0) { "temperature is outside the supported range" }
        }
    }
}
