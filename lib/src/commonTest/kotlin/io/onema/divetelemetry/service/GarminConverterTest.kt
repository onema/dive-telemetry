package io.onema.divetelemetry.service

import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.domain.TempUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GarminConverterTest {
    private fun makeSample(
        timeSeconds: Long = 0L,
        depth: Double = 0.0,
        avgPpo2: Double = 0.32,
        fractionO2: Double = 0.32,
        fractionHe: Double = 0.0,
        waterTemp: Double? = 25.0,
        firstStopDepth: Double = 0.0,
        firstStopTime: Long = 0L,
        timeToSurface: Long = 0L,
        currentNdl: Long? = null,
        currentCircuitMode: Int = 1,
        currentCcrMode: Int = 0,
        gasSwitchNeeded: Boolean = false,
        externalPpo2: Boolean = false,
    ): DiveSample = DiveSample(
        timeSeconds = timeSeconds,
        depth = depth,
        avgPpo2 = avgPpo2,
        fractionO2 = fractionO2,
        fractionHe = fractionHe,
        waterTemp = waterTemp,
        firstStopDepth = firstStopDepth,
        firstStopTime = firstStopTime,
        timeToSurface = timeToSurface,
        currentNdl = currentNdl,
        currentCircuitMode = currentCircuitMode,
        currentCcrMode = currentCcrMode,
        gasSwitchNeeded = gasSwitchNeeded,
        externalPpo2 = externalPpo2,
    )

    private fun makeLog(
        samples: List<DiveSample>,
    ): DiveLog = DiveLog(
        metadata = DiveMetadata(
            depthUnit = DepthUnit.M,
            tempUnit = TempUnit.CELSIUS,
            pressureUnit = PressureUnit.BAR,
            startTime = null,
            extra = mapOf(
                "oxygen_percent" to "32",
                "helium_percent" to "0",
            ),
        ),
        samples = samples,
    )

    @Test
    fun `output has 24 headers`() {
        // Arrange
        val log = makeLog(listOf(makeSample()))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals(24, output.headers.size)
    }

    @Test
    fun `output headers match expected`() {
        // Arrange
        val log = makeLog(listOf(makeSample()))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertTrue("Time (s)" in output.headers)
        assertTrue("Actual Depth (m)" in output.headers)
        assertTrue("Average PP02 (text)" in output.headers)
        assertTrue("Fraction O2 (text)" in output.headers)
        assertTrue("Fraction He (text)" in output.headers)
        assertTrue("Water Temp (\u00b0C)" in output.headers)
        assertTrue("Seconds" in output.headers)
        assertTrue("Display Minutes (text)" in output.headers)
        assertTrue("Display Seconds (text)" in output.headers)
        assertTrue("Max Depth (text)" in output.headers)
        assertTrue("Actual Time (text)" in output.headers)
        assertTrue("AM/PM Indicator (text)" in output.headers)
    }

    @Test
    fun `depth negated in native meters`() {
        // Arrange
        val log = makeLog(listOf(makeSample(depth = 2.64)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("-2.64", output.rows[0].values["Actual Depth (m)"])
    }

    @Test
    fun `depth zero stays zero`() {
        // Arrange
        val log = makeLog(listOf(makeSample(depth = 0.0)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("0", output.rows[0].values["Actual Depth (m)"])
    }

    @Test
    fun `temperature passed through in celsius`() {
        // Arrange
        val log = makeLog(listOf(makeSample(waterTemp = 25.0)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("25", output.rows[0].values["Water Temp (\u00b0C)"])
    }

    @Test
    fun `ppo2 rounded to two decimals`() {
        // Arrange
        val log = makeLog(listOf(makeSample(avgPpo2 = 0.64)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("0.64", output.rows[0].values["Average PP02 (text)"])
    }

    @Test
    fun `fraction O2 converted to percent`() {
        // Arrange
        val log = makeLog(listOf(makeSample(fractionO2 = 0.32)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("32", output.rows[0].values["Fraction O2 (text)"])
    }

    @Test
    fun `fraction He converted to percent`() {
        // Arrange
        val log = makeLog(listOf(makeSample(fractionHe = 0.10)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("10", output.rows[0].values["Fraction He (text)"])
    }

    @Test
    fun `time in seconds`() {
        // Arrange
        val log = makeLog(listOf(makeSample(timeSeconds = 65L)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("65", output.rows[0].values["Time (s)"])
        assertEquals("5", output.rows[0].values["Seconds"])
    }

    @Test
    fun `static defaults present`() {
        // Arrange
        val log = makeLog(listOf(makeSample()))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)
        val row = output.rows[0].values

        // Assert
        assertEquals("1", row["Current Circuit Mode"])
        assertEquals("FALSE", row["Gas Switch Needed"])
        assertEquals("FALSE", row["External PPO2"])
    }

    @Test
    fun `next stop depth passed through in meters`() {
        // Arrange
        val log = makeLog(listOf(makeSample(firstStopDepth = 3.0)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("3", output.rows[0].values["First Stop Depth"])
    }

    @Test
    fun `wall clock time empty for garmin`() {
        // Arrange
        val log = makeLog(listOf(makeSample(timeSeconds = 60L)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("", output.rows[0].values["Actual Time (text)"])
        assertEquals("", output.rows[0].values["AM/PM Indicator (text)"])
    }

    @Test
    fun `display minutes and seconds computed`() {
        // Arrange
        val log = makeLog(listOf(makeSample(timeSeconds = 65L)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("1", output.rows[0].values["Display Minutes (text)"])
        assertEquals("05", output.rows[0].values["Display Seconds (text)"])
    }

    @Test
    fun `max depth tracks running maximum`() {
        // Arrange
        val log = makeLog(
            listOf(
                makeSample(depth = 0.0),
                makeSample(depth = 2.64),
                makeSample(depth = 1.5),
                makeSample(depth = 3.0),
            )
        )

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("0", output.rows[0].values["Max Depth (text)"])
        assertEquals("2.64", output.rows[1].values["Max Depth (text)"])
        assertEquals("2.64", output.rows[2].values["Max Depth (text)"])
        assertEquals("3", output.rows[3].values["Max Depth (text)"])
    }
}
