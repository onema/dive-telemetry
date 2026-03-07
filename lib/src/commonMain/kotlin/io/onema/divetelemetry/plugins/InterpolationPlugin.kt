package io.onema.divetelemetry.plugins

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveSample
import kotlin.math.roundToLong

object InterpolationPlugin : DiveLogPlugin {
    override val id: String = "core.interpolation"
    override val name: String = "1-Second Interpolation"
    override val description: String = "Generates smoother data by interpolating samples to 1-second intervals."

    override val parameters: List<PluginParameter<*>> = listOf(
        BooleanParameter(
            key = "enabled",
            name = "Enable Interpolation",
            description = "Interpolate samples to 1-second intervals.",
            defaultValue = false
        )
    )

    override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
        ensure(diveLog.samples.size > 1) {
            PluginError.ExecutionError("Interpolation requires at least two samples.")
        }
        return interpolateDiveLog(diveLog)
    }

    fun interpolateDiveLog(log: DiveLog): DiveLog {
        if (log.samples.size < 2) return log

        val interpolatedSamples = log.samples
            .zipWithNext()
            .flatMap { (current, next) -> interpolateSegment(current, next) }
            .plus(log.samples.last())

        return log.copy(samples = interpolatedSamples)
    }

    private fun interpolateSegment(
        current: DiveSample,
        next: DiveSample,
    ): List<DiveSample> {
        val t1 = current.timeSeconds
        val t2 = next.timeSeconds
        val gap = t2 - t1

        if (gap <= 1) return listOf(current)

        val filledSteps = (t1 + 1 until t2).map { t ->
            val fraction = (t - t1).toDouble() / gap.toDouble()
            interpolateSample(current, next, t, fraction)
        }

        return listOf(current) + filledSteps
    }

    private fun interpolateSample(
        current: DiveSample,
        next: DiveSample,
        timeSeconds: Long,
        fraction: Double,
    ): DiveSample =
        DiveSample(
            timeSeconds = timeSeconds,
            depth = lerp(current.depth, next.depth, fraction),
            avgPpo2 = lerp(current.avgPpo2, next.avgPpo2, fraction),
            fractionO2 = lerp(current.fractionO2, next.fractionO2, fraction),
            fractionHe = lerp(current.fractionHe, next.fractionHe, fraction),
            waterTemp = lerpNullable(current.waterTemp, next.waterTemp, fraction),
            firstStopDepth = lerp(current.firstStopDepth, next.firstStopDepth, fraction),
            firstStopTime = lerpLong(current.firstStopTime, next.firstStopTime, fraction),
            timeToSurface = lerpLong(current.timeToSurface, next.timeToSurface, fraction),
            currentNdl = lerpNullableLong(current.currentNdl, next.currentNdl, fraction),
            currentCircuitMode = current.currentCircuitMode,
            currentCcrMode = current.currentCcrMode,
            gasSwitchNeeded = current.gasSwitchNeeded,
            externalPpo2 = current.externalPpo2,
            setPointType = current.setPointType,
            circuitSwitchType = current.circuitSwitchType,
            externalO2Sensor1Mv = current.externalO2Sensor1Mv,
            externalO2Sensor2Mv = current.externalO2Sensor2Mv,
            externalO2Sensor3Mv = current.externalO2Sensor3Mv,
            batteryVoltage = current.batteryVoltage,
            tankPressure1 = current.tankPressure1,
            tankPressure2 = current.tankPressure2,
            tankPressure3 = current.tankPressure3,
            tankPressure4 = current.tankPressure4,
            gasTimeRemaining = current.gasTimeRemaining,
            sacRate = current.sacRate,
            ascentRate = current.ascentRate,
            safeAscentDepth = current.safeAscentDepth,
            co2mbar = current.co2mbar,
        )

    private fun lerp(a: Double, b: Double, fraction: Double): Double =
        a + (b - a) * fraction

    private fun lerpLong(a: Long, b: Long, fraction: Double): Long =
        (a + (b - a) * fraction).roundToLong()

    private fun lerpNullable(a: Double?, b: Double?, fraction: Double): Double? {
        if (a == null || b == null) return a
        return lerp(a, b, fraction)
    }

    private fun lerpNullableLong(a: Long?, b: Long?, fraction: Double): Long? {
        if (a == null || b == null) return a
        return lerpLong(a, b, fraction)
    }
}
