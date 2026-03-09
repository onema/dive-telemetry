package io.onema.divetelemetry.service

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.domain.TempUnit
import io.onema.divetelemetry.error.ParseError
import io.onema.divetelemetry.util.splitCsvLine
import okio.BufferedSource

class ShearwaterDiveLogParser : DiveLogParser {
    private val requiredColumns = setOf(
        "Time (sec)", "Depth", "Average PPO2",
        "Fraction O2", "Fraction He", "Water Temp",
        "Time To Surface (min)", "Gas Switch Needed", "External PPO2",
    )

    override fun Raise<ParseError>.parse(source: BufferedSource): DiveLog {
        val metadataHeaderLine = source.readUtf8Line()
        ensureNotNull(metadataHeaderLine) { ParseError.UnexpectedEof("metadata headers (row 1)") }
        val metadataHeaders = splitCsvLine(metadataHeaderLine)

        val metadataValueLine = source.readUtf8Line()
        ensureNotNull(metadataValueLine) { ParseError.UnexpectedEof("metadata values (row 2)") }
        val metadataValues = splitCsvLine(metadataValueLine)
        val rawMetadata = metadataHeaders.zip(metadataValues).toMap()

        // Defaults to imperial if mising.
        val imperial = rawMetadata["Imperial Units"]?.lowercase() != "false"
        val depthUnit = if (imperial) DepthUnit.FT else DepthUnit.M
        val tempUnit = if (imperial) TempUnit.FAHRENHEIT else TempUnit.CELSIUS
        val pressureUnit = if (imperial) PressureUnit.PSI else PressureUnit.BAR
        val startTime = rawMetadata["Start Date"]

        val metadata = DiveMetadata(
            depthUnit = depthUnit,
            tempUnit = tempUnit,
            pressureUnit = pressureUnit,
            startTime = startTime,
            extra = rawMetadata,
        )

        val dataHeaderLine = source.readUtf8Line()
        ensureNotNull(dataHeaderLine) { ParseError.UnexpectedEof("data headers (row 3)") }
        val csvHeaders = splitCsvLine(dataHeaderLine)

        val missing = requiredColumns - csvHeaders.toSet()
        ensure(missing.isEmpty()) { ParseError.MissingColumns(missing.sorted()) }

        val headerIndexMap = csvHeaders.withIndex().associate { (index, name) -> name to index }

        val samples = generateSequence { source.readUtf8Line() }
            .filter { it.isNotBlank() }
            .map { line ->
                val fields = splitCsvLine(line)
                parseSample(fields, headerIndexMap)
            }.toList()

        return DiveLog(
            metadata = metadata,
            samples = samples,
        )
    }

    private fun parseSample(fields: List<String>, headerIndexMap: Map<String, Int>): DiveSample {
        fun field(name: String): String = headerIndexMap[name]?.let { fields.getOrNull(it) } ?: ""

        return DiveSample(
            timeSeconds = field("Time (sec)").toLongOrNull() ?: 0L,
            depth = field("Depth").toDoubleOrNull() ?: 0.0,
            avgPpo2 = field("Average PPO2").toDoubleOrNull() ?: 0.0,
            fractionO2 = field("Fraction O2").toDoubleOrNull() ?: 0.0,
            fractionHe = field("Fraction He").toDoubleOrNull() ?: 0.0,
            waterTemp = field("Water Temp").toDoubleOrNull(),
            firstStopDepth = field("First Stop Depth").toDoubleOrNull() ?: 0.0,
            firstStopTime = field("First Stop Time").toLongOrNull() ?: 0L,
            timeToSurface = field("Time To Surface (min)").toLongOrNull() ?: 0L,
            currentNdl = field("Current NDL").toLongOrNull(),
            currentCircuitMode = field("Current Circuit Mode").toIntOrNull() ?: 1,
            currentCcrMode = field("Current CCR Mode").toIntOrNull() ?: 0,
            gasSwitchNeeded = field("Gas Switch Needed") == "True",
            externalPpo2 = field("External PPO2") == "True",
            setPointType = field("Set Point Type").toIntOrNull() ?: 0,
            circuitSwitchType = field("Circuit Switch Type").toIntOrNull() ?: 0,
            externalO2Sensor1Mv = field("External O2 Sensor 1 (mV)").ifEmpty { "0" },
            externalO2Sensor2Mv = field("External O2 Sensor 2 (mV)").ifEmpty { "0" },
            externalO2Sensor3Mv = field("External O2 Sensor 3 (mV)").ifEmpty { "0" },
            batteryVoltage = field("Battery Voltage").ifEmpty { "0" },
            tankPressure1 = field("Tank 1 pressure (PSI)").ifEmpty { "0" },
            tankPressure2 = field("Tank 2 pressure (PSI)").ifEmpty { "0" },
            tankPressure3 = field("Tank 3 pressure (PSI)").ifEmpty { "0" },
            tankPressure4 = field("Tank 4 pressure (PSI)").ifEmpty { "0" },
            gasTimeRemaining = field("Gas Time Remaining").ifEmpty { "0" },
            sacRate = field("SAC Rate (2 minute avg)").ifEmpty { "0" },
            ascentRate = field("Ascent Rate").ifEmpty { "0" },
            safeAscentDepth = field("Safe Ascent Depth").ifEmpty { "0" },
            co2mbar = field("CO2mbar").ifEmpty { "0" },
        )
    }
}
