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

class ShearwaterConverterTest {

    private fun makeSample(
        timeSeconds: Long = 0L,
        depth: Double = 0.0,
        avgPpo2: Double = 0.19,
        fractionO2: Double = 0.18,
        fractionHe: Double = 0.45,
        waterTemp: Double? = 63.0,
        firstStopDepth: Double = 0.0,
        firstStopTime: Long = 0L,
        timeToSurface: Long = 0L,
        currentNdl: Long? = 0L,
        currentCircuitMode: Int = 1,
        currentCcrMode: Int = 0,
        gasSwitchNeeded: Boolean = false,
        externalPpo2: Boolean = true,
        batteryVoltage: String = "1.45",
        tankPressure1: String = "AI is off",
        tankPressure2: String = "No comms for 90s +",
        tankPressure3: String = "N/A",
        tankPressure4: String = "N/A",
        gasTimeRemaining: String = "N/A",
        sacRate: String = "GTR and SAC are off",
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
        batteryVoltage = batteryVoltage,
        tankPressure1 = tankPressure1,
        tankPressure2 = tankPressure2,
        tankPressure3 = tankPressure3,
        tankPressure4 = tankPressure4,
        gasTimeRemaining = gasTimeRemaining,
        sacRate = sacRate,
    )

    private fun makeDiveLog(
        samples: List<DiveSample>,
        imperial: Boolean = true,
        startTime: String? = null,
    ): DiveLog {
        val depthUnit = if (imperial) DepthUnit.FT else DepthUnit.M
        val tempUnit = if (imperial) TempUnit.FAHRENHEIT else TempUnit.CELSIUS
        val pressureUnit = if (imperial) PressureUnit.PSI else PressureUnit.BAR
        return DiveLog(
            metadata = DiveMetadata(
                depthUnit = depthUnit,
                tempUnit = tempUnit,
                pressureUnit = pressureUnit,
                startTime = startTime,
                extra = mapOf("Product" to "Perdix 2"),
            ),
            samples = samples,
        )
    }

    @Test
    fun `output has 24 headers`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample()))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals(24, output.headers.size)
    }

    @Test
    fun `headers are renamed`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample()))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertTrue("Time (s)" in output.headers)
        assertTrue("Actual Depth (ft)" in output.headers)
        assertTrue("Average PP02 (text)" in output.headers)
        assertTrue("Fraction O2 (text)" in output.headers)
        assertTrue("Fraction He (text)" in output.headers)
        assertTrue("Water Temp (\u00b0F)" in output.headers)
        assertTrue("Time To Surface" in output.headers)
    }

    @Test
    fun `computed columns appended`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample()))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        val last7 = output.headers.takeLast(7)
        assertEquals(
            listOf(
                "Seconds",
                "Display Minutes (text)",
                "Display Seconds (text)",
                "Max Depth (text)",
                "Actual Time (text)",
                "AM/PM Indicator (text)",
                "Gas Mixture (text)",
            ),
            last7
        )
    }

    @Test
    fun `removed columns not present`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample()))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        val absent = listOf(
            "External O2 Sensor 1 (mV)", "External O2 Sensor 2 (mV)", "External O2 Sensor 3 (mV)",
            "Battery Voltage", "Gas Time Remaining", "SAC Rate (2 minute avg)",
            "Ascent Rate", "Safe Ascent Depth", "CO2mbar",
            "Current CCR Mode", "Set Point Type", "Circuit Switch Type",
        )
        absent.forEach { col -> assertTrue(col !in output.headers, "Expected $col to be absent") }
    }

    @Test
    fun `depth is negated`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample(depth = 6.4)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("-6.4", output.rows[0].values["Actual Depth (ft)"])
    }

    @Test
    fun `depth zero stays zero`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample(depth = 0.0)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("0", output.rows[0].values["Actual Depth (ft)"])
    }

    @Test
    fun `fraction O2 converted to percent`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample(fractionO2 = 0.18)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("18", output.rows[0].values["Fraction O2 (text)"])
    }

    @Test
    fun `fraction He converted to percent`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample(fractionHe = 0.45)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("45", output.rows[0].values["Fraction He (text)"])
    }

    @Test
    fun `booleans uppercased`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample(gasSwitchNeeded = false, externalPpo2 = true)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("FALSE", output.rows[0].values["Gas Switch Needed"])
        assertEquals("TRUE", output.rows[0].values["External PPO2"])
    }

    @Test
    fun `seconds computed from time`() {
        // Arrange
        val log = makeDiveLog(listOf(
            makeSample(timeSeconds = 65L),
            makeSample(timeSeconds = 120L),
            makeSample(timeSeconds = 3995L),
        ))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("5", output.rows[0].values["Seconds"])
        assertEquals("0", output.rows[1].values["Seconds"])
        assertEquals("35", output.rows[2].values["Seconds"])
    }

    @Test
    fun `gas mixture column present`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample(fractionO2 = 0.21, fractionHe = 0.0)))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("Air", output.rows[0].values["Gas Mixture (text)"])
    }

    @Test
    fun `factory creates unified converter`() {
        // Arrange / Act
        val converter = TelemetryConverter.create()

        // Assert
        assertTrue(converter is UnifiedTelemetryConverter)
    }

    @Test
    fun `metric depth header uses meters`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample()), imperial = false)

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertTrue("Actual Depth (m)" in output.headers)
        assertTrue("Actual Depth (ft)" !in output.headers)
    }

    @Test
    fun `metric temp header uses celsius`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample()), imperial = false)

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertTrue("Water Temp (\u00b0C)" in output.headers)
        assertTrue("Water Temp (\u00b0F)" !in output.headers)
    }

    @Test
    fun `metric depth is negated`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample(depth = 18.5)), imperial = false)

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("-18.5", output.rows[0].values["Actual Depth (m)"])
    }

    @Test
    fun `imperial depth header uses feet`() {
        // Arrange
        val log = makeDiveLog(listOf(makeSample()), imperial = true)

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertTrue("Actual Depth (ft)" in output.headers)
        assertTrue("Actual Depth (m)" !in output.headers)
    }

    @Test
    fun `display minutes computed from time`() {
        // Arrange
        val log = makeDiveLog(listOf(
            makeSample(timeSeconds = 0L),
            makeSample(timeSeconds = 65L),
            makeSample(timeSeconds = 3995L),
        ))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("0", output.rows[0].values["Display Minutes (text)"])
        assertEquals("1", output.rows[1].values["Display Minutes (text)"])
        assertEquals("66", output.rows[2].values["Display Minutes (text)"])
    }

    @Test
    fun `display seconds computed from time`() {
        // Arrange
        val log = makeDiveLog(listOf(
            makeSample(timeSeconds = 0L),
            makeSample(timeSeconds = 65L),
            makeSample(timeSeconds = 3995L),
        ))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("00", output.rows[0].values["Display Seconds (text)"])
        assertEquals("05", output.rows[1].values["Display Seconds (text)"])
        assertEquals("35", output.rows[2].values["Display Seconds (text)"])
    }

    @Test
    fun `max depth tracks running maximum`() {
        // Arrange
        val log = makeDiveLog(listOf(
            makeSample(depth = 0.0),
            makeSample(depth = 6.4),
            makeSample(depth = 12.3),
            makeSample(depth = 8.0),
            makeSample(depth = 15.5),
        ))

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("0", output.rows[0].values["Max Depth (text)"])
        assertEquals("6.4", output.rows[1].values["Max Depth (text)"])
        assertEquals("12.3", output.rows[2].values["Max Depth (text)"])
        assertEquals("12.3", output.rows[3].values["Max Depth (text)"])
        assertEquals("15.5", output.rows[4].values["Max Depth (text)"])
    }

    @Test
    fun `wall clock time computed from start time`() {
        // Arrange
        val log = makeDiveLog(
            listOf(
                makeSample(timeSeconds = 0L),
                makeSample(timeSeconds = 60L),
                makeSample(timeSeconds = 3600L),
            ),
            startTime = "12/7/2025 8:39:59 PM",
        )

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("8:39", output.rows[0].values["Actual Time (text)"])
        assertEquals("pm", output.rows[0].values["AM/PM Indicator (text)"])
        assertEquals("8:40", output.rows[1].values["Actual Time (text)"])
        assertEquals("pm", output.rows[1].values["AM/PM Indicator (text)"])
        assertEquals("9:39", output.rows[2].values["Actual Time (text)"])
        assertEquals("pm", output.rows[2].values["AM/PM Indicator (text)"])
    }

    @Test
    fun `wall clock time empty when no start time`() {
        // Arrange
        val log = makeDiveLog(
            listOf(makeSample(timeSeconds = 60L)),
            startTime = null,
        )

        // Act
        val output = UnifiedTelemetryConverter().convert(log)

        // Assert
        assertEquals("", output.rows[0].values["Actual Time (text)"])
        assertEquals("", output.rows[0].values["AM/PM Indicator (text)"])
    }
}
