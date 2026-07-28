package io.github.superisland.model

/**
 * Read-only current-frequency telemetry for the verified `warsaw` CPU policies and KGSL GPU.
 *
 * The model contains observations only; it deliberately has no governors, clocks, min/max, or
 * other performance-control fields.
 */
data class WarsawPerformanceMetricSnapshot(
    val cpuPolicy0Mhz: Int,
    val cpuPolicy6Mhz: Int,
    val gpuMhz: Int,
    val capturedAtMillis: Long,
) {
    init {
        require(cpuPolicy0Mhz in 1..5_000) { "CPU policy0 frequency is outside the supported range" }
        require(cpuPolicy6Mhz in 1..5_000) { "CPU policy6 frequency is outside the supported range" }
        require(gpuMhz in 1..2_000) { "GPU frequency is outside the supported range" }
        require(capturedAtMillis >= 0) { "capturedAtMillis must not be negative" }
    }
}
