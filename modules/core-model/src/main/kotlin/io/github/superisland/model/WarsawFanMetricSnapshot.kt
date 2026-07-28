package io.github.superisland.model

/**
 * Read-only fan telemetry for the supported REDMI K90 Ultra (`warsaw`) adapter.
 *
 * This model deliberately excludes controls. Root access may observe these values, but it must
 * never use this API to set fan level or PWM duty.
 */
data class WarsawFanMetricSnapshot(
    val rpm: Int,
    val targetLevel: Int,
    val pwmDutyPercent: Int,
    val capturedAtMillis: Long,
) {
    init {
        require(rpm in 0..50_000) { "rpm must be between 0 and 50000" }
        require(targetLevel in 0..255) { "targetLevel must be between 0 and 255" }
        require(pwmDutyPercent in 0..100) { "pwmDutyPercent must be between 0 and 100" }
        require(capturedAtMillis >= 0) { "capturedAtMillis must not be negative" }
    }
}
