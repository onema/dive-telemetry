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

class UnifiedTelemetryConverterTest {
    private fun makeSample(
        timeSeconds: Long = 0L,
        depth: Double = 0.0,
        avgPpo2: Double = 0.21,
        fractionO2: Double = 0.21,
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
        batteryVoltage: String = "0",
        tankPressure1: String = "N/A",
        tankPressure2: String = "N/A",
        tankPressure3: String = "N/A",
        tankPressure4: String = "N/A",
        gasTimeRemaining: String = "N/A",
        sacRate: String = "N/A",
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

    private fun makeLog(
        samples: List<DiveSample>,
        depthUnit: DepthUnit = DepthUnit.M,
        tempUnit: TempUnit = TempUnit.CELSIUS,
        pressureUnit: PressureUnit = PressureUnit.BAR,
        startTime: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): DiveLog = DiveLog(
        metadata = DiveMetadata(
            depthUnit = depthUnit,
            tempUnit = tempUnit,
            pressureUnit = pressureUnit,
            startTime = startTime,
            extra = extra,
        ),
        samples = samples,
    )

    private fun convert(log: DiveLog) = UnifiedTelemetryConverter().convert(log)

    // --- Header tests ---

    @Test
    fun `output has 24 headers`() {
        val output = convert(makeLog(listOf(makeSample())))
        assertEquals(24, output.headers.size)
    }

    @Test
    fun `metric depth header uses meters`() {
        val output = convert(makeLog(listOf(makeSample()), depthUnit = DepthUnit.M))
        assertTrue("Actual Depth (m)" in output.headers)
        assertTrue("Actual Depth (ft)" !in output.headers)
    }

    @Test
    fun `imperial depth header uses feet`() {
        val output = convert(makeLog(listOf(makeSample()), depthUnit = DepthUnit.FT))
        assertTrue("Actual Depth (ft)" in output.headers)
        assertTrue("Actual Depth (m)" !in output.headers)
    }

    @Test
    fun `celsius temp header`() {
        val output = convert(makeLog(listOf(makeSample()), tempUnit = TempUnit.CELSIUS))
        assertTrue("Water Temp (\u00b0C)" in output.headers)
        assertTrue("Water Temp (\u00b0F)" !in output.headers)
    }

    @Test
    fun `fahrenheit temp header`() {
        val output = convert(makeLog(listOf(makeSample()), tempUnit = TempUnit.FAHRENHEIT))
        assertTrue("Water Temp (\u00b0F)" in output.headers)
        assertTrue("Water Temp (\u00b0C)" !in output.headers)
    }

    @Test
    fun `computed columns appended`() {
        val output = convert(makeLog(listOf(makeSample())))
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
        val output = convert(makeLog(listOf(makeSample())))
        val absent = listOf(
            "External O2 Sensor 1 (mV)", "External O2 Sensor 2 (mV)", "External O2 Sensor 3 (mV)",
            "Battery Voltage", "Gas Time Remaining", "SAC Rate (2 minute avg)",
            "Ascent Rate", "Safe Ascent Depth", "CO2mbar",
            "Current CCR Mode", "Set Point Type", "Circuit Switch Type",
        )
        absent.forEach { col -> assertTrue(col !in output.headers, "Expected $col to be absent") }
    }

    @Test
    fun `core headers present`() {
        val output = convert(makeLog(listOf(makeSample())))
        assertTrue("Time (s)" in output.headers)
        assertTrue("Average PP02 (text)" in output.headers)
        assertTrue("Fraction O2 (text)" in output.headers)
        assertTrue("Fraction He (text)" in output.headers)
        assertTrue("Seconds" in output.headers)
        assertTrue("Display Minutes (text)" in output.headers)
        assertTrue("Display Seconds (text)" in output.headers)
        assertTrue("Max Depth (text)" in output.headers)
        assertTrue("Actual Time (text)" in output.headers)
        assertTrue("AM/PM Indicator (text)" in output.headers)
    }

    // --- Depth tests ---

    @Test
    fun `depth negated in meters`() {
        val output = convert(makeLog(listOf(makeSample(depth = 2.64))))
        assertEquals("-2.64", output.rows[0].values["Actual Depth (m)"])
    }

    @Test
    fun `depth negated in feet`() {
        val output = convert(makeLog(listOf(makeSample(depth = 6.4)), depthUnit = DepthUnit.FT))
        assertEquals("-6.4", output.rows[0].values["Actual Depth (ft)"])
    }

    @Test
    fun `depth zero stays zero`() {
        val output = convert(makeLog(listOf(makeSample(depth = 0.0))))
        assertEquals("0", output.rows[0].values["Actual Depth (m)"])
    }

    // --- Temperature tests ---

    @Test
    fun `temperature passed through`() {
        val output = convert(makeLog(listOf(makeSample(waterTemp = 25.0))))
        assertEquals("25", output.rows[0].values["Water Temp (\u00b0C)"])
    }

    @Test
    fun `null temperature produces empty string`() {
        val output = convert(makeLog(listOf(makeSample(waterTemp = null))))
        assertEquals("", output.rows[0].values["Water Temp (\u00b0C)"])
    }

    // --- Gas fraction tests ---

    @Test
    fun `fraction O2 converted to percent`() {
        val output = convert(makeLog(listOf(makeSample(fractionO2 = 0.32))))
        assertEquals("32", output.rows[0].values["Fraction O2 (text)"])
    }

    @Test
    fun `fraction He converted to percent`() {
        val output = convert(makeLog(listOf(makeSample(fractionHe = 0.10))))
        assertEquals("10", output.rows[0].values["Fraction He (text)"])
    }

    @Test
    fun `ppo2 rounded to two decimals`() {
        val output = convert(makeLog(listOf(makeSample(avgPpo2 = 0.64))))
        assertEquals("0.64", output.rows[0].values["Average PP02 (text)"])
    }

    @Test
    fun `gas mixture Air for 21 percent O2`() {
        val output = convert(makeLog(listOf(makeSample(fractionO2 = 0.21, fractionHe = 0.0))))
        assertEquals("Air", output.rows[0].values["Gas Mixture (text)"])
    }

    // --- Time tests ---

    @Test
    fun `time in seconds`() {
        val output = convert(makeLog(listOf(makeSample(timeSeconds = 65L))))
        assertEquals("65", output.rows[0].values["Time (s)"])
        assertEquals("5", output.rows[0].values["Seconds"])
    }

    @Test
    fun `seconds computed from time`() {
        val output = convert(
            makeLog(
                listOf(
                    makeSample(timeSeconds = 65L),
                    makeSample(timeSeconds = 120L),
                    makeSample(timeSeconds = 3995L),
                )
            )
        )
        assertEquals("5", output.rows[0].values["Seconds"])
        assertEquals("0", output.rows[1].values["Seconds"])
        assertEquals("35", output.rows[2].values["Seconds"])
    }

    @Test
    fun `display minutes computed from time`() {
        val output = convert(
            makeLog(
                listOf(
                    makeSample(timeSeconds = 0L),
                    makeSample(timeSeconds = 65L),
                    makeSample(timeSeconds = 3995L),
                )
            )
        )
        assertEquals("0", output.rows[0].values["Display Minutes (text)"])
        assertEquals("1", output.rows[1].values["Display Minutes (text)"])
        assertEquals("66", output.rows[2].values["Display Minutes (text)"])
    }

    @Test
    fun `display seconds computed from time`() {
        val output = convert(
            makeLog(
                listOf(
                    makeSample(timeSeconds = 0L),
                    makeSample(timeSeconds = 65L),
                    makeSample(timeSeconds = 3995L),
                )
            )
        )
        assertEquals("00", output.rows[0].values["Display Seconds (text)"])
        assertEquals("05", output.rows[1].values["Display Seconds (text)"])
        assertEquals("35", output.rows[2].values["Display Seconds (text)"])
    }

    // --- Max depth ---

    @Test
    fun `max depth tracks running maximum`() {
        val output = convert(
            makeLog(
                listOf(
                    makeSample(depth = 0.0),
                    makeSample(depth = 6.4),
                    makeSample(depth = 12.3),
                    makeSample(depth = 8.0),
                    makeSample(depth = 15.5),
                )
            )
        )
        assertEquals("0", output.rows[0].values["Max Depth (text)"])
        assertEquals("6.4", output.rows[1].values["Max Depth (text)"])
        assertEquals("12.3", output.rows[2].values["Max Depth (text)"])
        assertEquals("12.3", output.rows[3].values["Max Depth (text)"])
        assertEquals("15.5", output.rows[4].values["Max Depth (text)"])
    }

    // --- Static defaults ---

    @Test
    fun `booleans uppercased`() {
        val output = convert(makeLog(listOf(makeSample(gasSwitchNeeded = false, externalPpo2 = true))))
        assertEquals("FALSE", output.rows[0].values["Gas Switch Needed"])
        assertEquals("TRUE", output.rows[0].values["External PPO2"])
    }

    @Test
    fun `static defaults present`() {
        val output = convert(makeLog(listOf(makeSample())))
        val row = output.rows[0].values
        assertEquals("1", row["Current Circuit Mode"])
        assertEquals("FALSE", row["Gas Switch Needed"])
        assertEquals("FALSE", row["External PPO2"])
    }

    @Test
    fun `next stop depth passed through in meters`() {
        val output = convert(makeLog(listOf(makeSample(firstStopDepth = 3.0))))
        assertEquals("3", output.rows[0].values["First Stop Depth"])
    }

    // --- Wall clock time ---

    @Test
    fun `wall clock time computed from start time`() {
        val output = convert(
            makeLog(
                listOf(
                    makeSample(timeSeconds = 0L),
                    makeSample(timeSeconds = 60L),
                    makeSample(timeSeconds = 3600L),
                ),
                startTime = "12/7/2025 8:39:59 PM",
            )
        )
        assertEquals("8:39", output.rows[0].values["Actual Time (text)"])
        assertEquals("pm", output.rows[0].values["AM/PM Indicator (text)"])
        assertEquals("8:40", output.rows[1].values["Actual Time (text)"])
        assertEquals("pm", output.rows[1].values["AM/PM Indicator (text)"])
        assertEquals("9:39", output.rows[2].values["Actual Time (text)"])
        assertEquals("pm", output.rows[2].values["AM/PM Indicator (text)"])
    }

    @Test
    fun `wall clock time empty when no start time`() {
        val output = convert(makeLog(listOf(makeSample(timeSeconds = 60L)), startTime = null))
        assertEquals("", output.rows[0].values["Actual Time (text)"])
        assertEquals("", output.rows[0].values["AM/PM Indicator (text)"])
    }

    // --- Factory ---

    @Test
    fun `factory creates unified converter`() {
        val converter = TelemetryConverter.create()
        assertTrue(converter is UnifiedTelemetryConverter)
    }
}
