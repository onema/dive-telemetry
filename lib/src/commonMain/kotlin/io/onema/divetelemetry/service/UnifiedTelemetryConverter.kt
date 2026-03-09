package io.onema.divetelemetry.service

import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.domain.TelemetryOutput
import io.onema.divetelemetry.domain.TelemetryRow
import io.onema.divetelemetry.domain.TempUnit
import io.onema.divetelemetry.util.formatBoolean
import io.onema.divetelemetry.util.formatNegatedDepth
import io.onema.divetelemetry.util.formatTwoDecimals
import io.onema.divetelemetry.util.formatWaterTemp
import io.onema.divetelemetry.util.fractionToPercentStr
import io.onema.divetelemetry.util.gasMixture
import io.onema.divetelemetry.util.parseStartTimeSeconds
import io.onema.divetelemetry.util.wallClockTime
import kotlin.math.abs

class UnifiedTelemetryConverter : TelemetryConverter {
    override fun convert(log: DiveLog): TelemetryOutput {
        val meta = log.metadata
        val depthSuffix = when (meta.depthUnit) {
            DepthUnit.FT -> "ft"
            DepthUnit.M -> "m"
        }
        val tempSuffix = when (meta.tempUnit) {
            TempUnit.FAHRENHEIT -> "\u00b0F"
            TempUnit.CELSIUS -> "\u00b0C"
        }
        val pressureSuffix = when (meta.pressureUnit) {
            PressureUnit.PSI -> "psi"
            PressureUnit.BAR -> "bar"
        }
        val startTimeSeconds = meta.startTime?.let { parseStartTimeSeconds(it) }

        val outputHeaders = listOf(
            "Time (s)",
            "Actual Depth ($depthSuffix)",
            "First Stop Depth",
            "Time To Surface",
            "Average PP02 (text)",
            "Fraction O2 (text)",
            "Fraction He (text)",
            "First Stop Time",
            "Current NDL",
            "Current Circuit Mode",
            "Water Temp ($tempSuffix)",
            "Gas Switch Needed",
            "External PPO2",
            "Tank 1 pressure ($pressureSuffix)",
            "Tank 2 pressure ($pressureSuffix)",
            "Tank 3 pressure ($pressureSuffix)",
            "Tank 4 pressure ($pressureSuffix)",
            "Seconds",
            "Display Minutes (text)",
            "Display Seconds (text)",
            "Max Depth (text)",
            "Actual Time (text)",
            "AM/PM Indicator (text)",
            "Gas Mixture (text)",
        )

        val maxDepthStates = computeMaxDepthStates(log.samples)

        val outputRows = log.samples.zip(maxDepthStates) { sample, maxDepthFormatted ->

            val (wallTime, amPm) = wallClockTime(startTimeSeconds, sample.timeSeconds)

            val gasMixtureStr = gasMixture(sample.fractionO2, sample.fractionHe)

            val values = mapOf(
                "Time (s)" to sample.timeSeconds.toString(),
                "Actual Depth ($depthSuffix)" to formatNegatedDepth(sample.depth),
                "First Stop Depth" to formatTwoDecimals(sample.firstStopDepth),
                "Time To Surface" to sample.timeToSurface.toString(),
                "Average PP02 (text)" to formatTwoDecimals(sample.avgPpo2),
                "Fraction O2 (text)" to fractionToPercentStr(sample.fractionO2),
                "Fraction He (text)" to fractionToPercentStr(sample.fractionHe),
                "First Stop Time" to sample.firstStopTime.toString(),
                "Current NDL" to (sample.currentNdl?.toString() ?: ""),
                "Current Circuit Mode" to sample.currentCircuitMode.toString(),
                "Water Temp ($tempSuffix)" to (sample.waterTemp?.let { formatWaterTemp(it) } ?: ""),
                "Gas Switch Needed" to formatBoolean(sample.gasSwitchNeeded),
                "External PPO2" to formatBoolean(sample.externalPpo2),
                "Tank 1 pressure ($pressureSuffix)" to sample.tankPressure1,
                "Tank 2 pressure ($pressureSuffix)" to sample.tankPressure2,
                "Tank 3 pressure ($pressureSuffix)" to sample.tankPressure3,
                "Tank 4 pressure ($pressureSuffix)" to sample.tankPressure4,
                "Seconds" to (sample.timeSeconds % 60).toString(),
                "Display Minutes (text)" to (sample.timeSeconds / 60).toString(),
                "Display Seconds (text)" to (sample.timeSeconds % 60).toString().padStart(2, '0'),
                "Max Depth (text)" to maxDepthFormatted,
                "Actual Time (text)" to wallTime,
                "AM/PM Indicator (text)" to amPm,
                "Gas Mixture (text)" to gasMixtureStr,
            )

            TelemetryRow(values)
        }

        return TelemetryOutput(
            headers = outputHeaders,
            rows = outputRows,
        )
    }
}

private fun computeMaxDepthStates(samples: List<DiveSample>): List<String> {
    val initial = 0.0 to "0"

    return samples
        .runningFold(initial) { (maxDepthAbs, maxDepthFormatted), sample ->
            val depthAbs = abs(sample.depth)
            if (depthAbs > maxDepthAbs) {
                depthAbs to formatTwoDecimals(sample.depth)
            } else {
                maxDepthAbs to maxDepthFormatted
            }
        }.drop(1)
        .map { it.second }
}
