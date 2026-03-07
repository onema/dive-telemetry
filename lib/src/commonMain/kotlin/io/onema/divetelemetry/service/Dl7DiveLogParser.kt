/**
 * This file is part of the ONEMA dive-telemetry-overlay Package.
 * For the full copyright and license information,
 * please view the LICENSE file that was distributed
 * with this source code.
 *
 * copyright (c) 2026, Juan Manuel Torres (http://onema.io)
 * 
 * @author Juan Manuel Torres <software@onema.io>
 */

package io.onema.divetelemetry.service

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.domain.TempUnit
import io.onema.divetelemetry.error.ParseError
import okio.BufferedSource

/**
 * EXPERIMENTAL
 */
class Dl7DiveLogParser :  DiveLogParser {
    override fun Raise<ParseError>.parse(source: BufferedSource): DiveLog {
        val lines = generateSequence { source.readUtf8Line() }.toList()

        val zrhFields = lines.firstOrNull { it.startsWith("ZRH|") }?.split("|")
        val zdhFields = lines.firstOrNull { it.startsWith("ZDH|") }?.split("|")

        // field[6]: temperature unit — "C" or "F"
        val tempUnit = if (zrhFields?.getOrNull(6) == "F") TempUnit.FAHRENHEIT else TempUnit.CELSIUS
        // field[7]: pressure unit — "bar" or "psi"
        val pressureUnit = if (zrhFields?.getOrNull(7)?.lowercase() == "psi") PressureUnit.PSI else PressureUnit.BAR
        // field[5]: start time in "yyyyMMddHHmmss" format
        val startTime = zdhFields?.getOrNull(5)?.let { formatDl7Time(it) }

        val samples = lines
            .dropWhile { it != "ZDP{" }
            .drop(1)
            .takeWhile { it != "ZDP}" }
            .filter { it.startsWith("|") }
            .mapNotNull { parseSample(it) }

        ensure(samples.isNotEmpty()) { ParseError.MissingFitData("dive samples") }

        return DiveLog(
            metadata = DiveMetadata(
                depthUnit = DepthUnit.M,   // DL7 always stores depth in meters
                tempUnit = tempUnit,
                pressureUnit = pressureUnit,
                startTime = startTime,
            ),
            samples = samples,
        )
    }

    private fun parseSample(line: String): DiveSample? {
        val fields = line.split("|")
        // fields[0] is empty because the line starts with "|"
        val timeMins = fields.getOrNull(1)?.toDoubleOrNull() ?: return null
        val depthM = fields.getOrNull(2)?.toDoubleOrNull() ?: return null
        val tempRaw = fields.getOrNull(8)?.toIntOrNull()

        // -17 is a sentinel value indicating a pre-dive surface / no-data row
        if (depthM == 0.0 && tempRaw == -17) return null

        return DiveSample(
            timeSeconds = (timeMins * 60).toLong(),
            depth = depthM,
            avgPpo2 = 0.0,
            fractionO2 = 0.21,
            fractionHe = 0.0,
            waterTemp = tempRaw?.takeIf { it != -17 }?.toDouble(),
            firstStopDepth = 0.0,
            firstStopTime = 0L,
            timeToSurface = 0L,
            currentNdl = null,
            currentCircuitMode = 0,
            currentCcrMode = 0,
            gasSwitchNeeded = false,
            externalPpo2 = false,
        )
    }

    /**
     * Converts "yyyyMMddHHmmss" to "M/d/yyyy h:mm:ss AM/PM" for wall-clock time computation.
     */
    private fun formatDl7Time(raw: String): String? {
        if (raw.length != 14) return null
        val year = raw.substring(0, 4)
        val month = raw.substring(4, 6).trimStart('0').ifEmpty { "0" }
        val day = raw.substring(6, 8).trimStart('0').ifEmpty { "0" }
        val hour = raw.substring(8, 10).toIntOrNull() ?: return null
        val min = raw.substring(10, 12)
        val sec = raw.substring(12, 14)
        val amPm = if (hour >= 12) "PM" else "AM"
        val hour12 = when {
            hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour
        }
        return "$month/$day/$year $hour12:$min:$sec $amPm"
    }
}