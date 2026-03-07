package io.onema.divetelemetry.service

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.domain.TempUnit
import io.onema.divetelemetry.domain.FitMessage
import io.onema.divetelemetry.error.ParseError
import io.onema.divetelemetry.util.decodeFitFile
import okio.BufferedSource

class GarminDiveLogParser : DiveLogParser {

    companion object {
        private const val MSG_RECORD = 20
        private const val MSG_DIVE_GAS = 259

        private const val FIELD_TIMESTAMP = 253
        private const val FIELD_TEMPERATURE = 13
        private const val FIELD_DEPTH = 92
        private const val FIELD_NEXT_STOP_DEPTH = 93
        private const val FIELD_NEXT_STOP_TIME = 94
        private const val FIELD_TIME_TO_SURFACE = 95
        private const val FIELD_NDL_TIME = 96

        private const val FIELD_OXYGEN = 1
        private const val FIELD_HELIUM = 3
    }

    override fun Raise<ParseError>.parse(source: BufferedSource): DiveLog {
        val fitMessages = decodeFitFile(source)

        val gasMessages = fitMessages.filter { it.globalMessageNumber == MSG_DIVE_GAS }
        val oxygenPercent = gasMessages.firstNotNullOfOrNull { msg ->
            (msg.fields[FIELD_OXYGEN] as? Long)
        } ?: 21L
        val heliumPercent = gasMessages.firstNotNullOfOrNull { msg ->
            (msg.fields[FIELD_HELIUM] as? Long)
        } ?: 0L

        val o2Fraction = oxygenPercent.toDouble() / 100.0
        val heFraction = heliumPercent.toDouble() / 100.0

        val metadata = DiveMetadata(
            depthUnit = DepthUnit.M,
            tempUnit = TempUnit.CELSIUS,
            pressureUnit = PressureUnit.BAR,
            startTime = null,
            extra = mapOf(
                "oxygen_percent" to oxygenPercent.toString(),
                "helium_percent" to heliumPercent.toString(),
            ),
        )

        val recordMessages = fitMessages
            .filter { it.globalMessageNumber == MSG_RECORD }
            .filter { it.fields.containsKey(FIELD_DEPTH) }

        ensure(recordMessages.isNotEmpty()) {
            ParseError.MissingFitData("dive record samples")
        }

        val firstTimestamp = extractTimestamp(recordMessages.first())
            ?: run { raise(ParseError.MissingFitData("timestamp in first record")) }

        val samples = recordMessages.map { msg ->
            val timestamp = extractTimestamp(msg) ?: firstTimestamp
            val elapsedSeconds = timestamp - firstTimestamp

            val depthRaw = msg.fields[FIELD_DEPTH] as? Long
            val depthMeters = if (depthRaw != null) depthRaw.toDouble() / 1000.0 else 0.0

            val temperature = (msg.fields[FIELD_TEMPERATURE] as? Long)?.toDouble()

            val nextStopDepthRaw = msg.fields[FIELD_NEXT_STOP_DEPTH] as? Long
            val nextStopDepthM = if (nextStopDepthRaw != null) nextStopDepthRaw.toDouble() / 1000.0 else 0.0

            val nextStopTime = (msg.fields[FIELD_NEXT_STOP_TIME] as? Long) ?: 0L
            val timeToSurface = (msg.fields[FIELD_TIME_TO_SURFACE] as? Long) ?: 0L
            val ndlTime = msg.fields[FIELD_NDL_TIME] as? Long

            val ppo2 = (depthMeters / 10.0 + 1.0) * o2Fraction

            DiveSample(
                timeSeconds = elapsedSeconds,
                depth = depthMeters,
                avgPpo2 = ppo2,
                fractionO2 = o2Fraction,
                fractionHe = heFraction,
                waterTemp = temperature,
                firstStopDepth = nextStopDepthM,
                firstStopTime = nextStopTime,
                timeToSurface = timeToSurface,
                currentNdl = ndlTime,
                currentCircuitMode = 1,
                currentCcrMode = 0,
                gasSwitchNeeded = false,
                externalPpo2 = false,
            )
        }

        return DiveLog(
            metadata = metadata,
            samples = samples,
        )
    }

    private fun extractTimestamp(msg: FitMessage): Long? {
        return msg.fields[FIELD_TIMESTAMP] as? Long
    }
}
