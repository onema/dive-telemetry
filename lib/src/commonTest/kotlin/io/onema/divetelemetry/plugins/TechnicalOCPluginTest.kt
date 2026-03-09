package io.onema.divetelemetry.plugins

import arrow.core.Either
import arrow.core.raise.either
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.domain.TempUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TechnicalOCPluginTest {
    private val metadata = DiveMetadata(
        depthUnit = DepthUnit.FT,
        tempUnit = TempUnit.FAHRENHEIT,
        pressureUnit = PressureUnit.PSI,
        startTime = null,
    )

    private fun sample(
        time: Long = 0L,
        depth: Double = 0.0,
        currentNdl: Long? = null,
        firstStopDepth: Double = 0.0,
        fractionO2: Double = 0.21,
        fractionHe: Double = 0.0,
    ) = DiveSample(
        timeSeconds = time,
        depth = depth,
        avgPpo2 = 0.99,
        fractionO2 = fractionO2,
        fractionHe = fractionHe,
        waterTemp = 77.0,
        firstStopDepth = firstStopDepth,
        firstStopTime = 0,
        timeToSurface = 0,
        currentNdl = currentNdl,
        currentCircuitMode = 1,
        currentCcrMode = 0,
        gasSwitchNeeded = false,
        externalPpo2 = false,
    )

    private fun runPlugin(log: DiveLog): Either<PluginError, List<Map<String, String>>> = either {
        with(TechnicalOCPlugin) { computeRows(log) }
    }

    @Test
    fun `plugin has expected metadata`() {
        // Arrange / Act / Assert
        assertEquals("core.technical-oc", TechnicalOCPlugin.id)
        assertEquals("Technical Open Circuit", TechnicalOCPlugin.name)
        assertTrue(TechnicalOCPlugin.description.isNotBlank())
        assertEquals(1, TechnicalOCPlugin.parameters.size)
        val enabledParam = TechnicalOCPlugin.parameters.first()
        assertIs<BooleanParameter>(enabledParam)
        assertEquals("enabled", enabledParam.key)
        assertEquals(true, enabledParam.defaultValue)
    }

    @Test
    fun `additional headers contain 16 columns`() {
        // Arrange / Act
        val headers = TechnicalOCPlugin.additionalHeaders(metadata)

        // Assert
        assertEquals(16, headers.size)
        assertTrue("White NDL (text)" in headers)
        assertTrue("Cleared TTS Label (text)" in headers)
        assertTrue("NDL Before Clear (text)" in headers)
        assertTrue("NDL Before Clear Time (text)" in headers)
    }

    @Test
    fun `beginning phase suppresses all columns`() {
        // Arrange
        val log = DiveLog(metadata, listOf(sample(time = 0, currentNdl = 10)))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[0]
        assertEquals("", row["White NDL (text)"])
        assertEquals("", row["NDL Label (text)"])
        assertEquals("", row["Decompression Ceiling"])
    }

    @Test
    fun `NDL above 5 shows white`() {
        // Arrange
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = 30),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[2]
        assertEquals("30", row["White NDL (text)"])
        assertEquals("", row["Yellow NDL (text)"])
        assertEquals("", row["Red NDL (text)"])
        assertEquals("NDL", row["NDL Label (text)"])
    }

    @Test
    fun `NDL 1-5 shows yellow`() {
        // Arrange
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = 3),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[2]
        assertEquals("", row["White NDL (text)"])
        assertEquals("3", row["Yellow NDL (text)"])
        assertEquals("", row["Red NDL (text)"])
        assertEquals("NDL", row["NDL Label (text)"])
    }

    @Test
    fun `NDL 0 shows red`() {
        // Arrange
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = 0),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[2]
        assertEquals("", row["White NDL (text)"])
        assertEquals("", row["Yellow NDL (text)"])
        assertEquals("0", row["Red NDL (text)"])
        assertEquals("", row["NDL Label (text)"])
    }

    @Test
    fun `deco columns populated when in deco`() {
        // Arrange
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = 0, firstStopDepth = 10.0),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[2]
        assertEquals("10", row["Decompression Ceiling"])
        assertEquals("STOP", row["First Stop Depth Label (text)"])
        assertEquals("TIME", row["Stop Time Label (text)"])
        assertEquals("DECO", row["Deco Label (text)"])
        assertEquals("ft", row["Deco Measurement (text)"])
        assertEquals("min", row["Deco Minute Indicator (text)"])
    }

    @Test
    fun `clear columns populated after deco clears`() {
        // Arrange
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = 0, firstStopDepth = 10.0),
                sample(time = 15, currentNdl = 10),
                sample(time = 30, currentNdl = 20),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val clearRow = result.value[3]
        assertEquals("CLEAR", clearRow["Clear Label (text)"])
        assertEquals("0:00", clearRow["Clear Time (text)"])
        assertEquals("Air", clearRow["Cleared Gas Mix Label (text)"])
        assertEquals("TTS", clearRow["Cleared TTS Label (text)"])

        val laterRow = result.value[4]
        assertEquals("CLEAR", laterRow["Clear Label (text)"])
        assertEquals("0:15", laterRow["Clear Time (text)"])
    }

    @Test
    fun `NDL before clear captures NDL at deco entry`() {
        // Arrange -- NDL is 8 at t=5, deco starts at t=10, clears at t=15
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 8),
                sample(time = 10, currentNdl = 0, firstStopDepth = 10.0),
                sample(time = 15, currentNdl = 20),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        // During deco: NDL before clear columns are empty
        assertEquals("", result.value[2]["NDL Before Clear (text)"])
        assertEquals("", result.value[2]["NDL Before Clear Time (text)"])
        // After clearing: frozen NDL and time of deco entry are shown
        assertEquals("8", result.value[3]["NDL Before Clear (text)"])
        assertEquals("0:10", result.value[3]["NDL Before Clear Time (text)"])
    }

    @Test
    fun `NDL before clear empty until deco clears`() {
        // Arrange -- never enters deco
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = 30),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        result.value.forEach { row ->
            assertEquals("", row["NDL Before Clear (text)"])
            assertEquals("", row["NDL Before Clear Time (text)"])
        }
    }

    @Test
    fun `returns one row per sample`() {
        // Arrange
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0),
                sample(time = 5),
                sample(time = 10),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals(3, result.value.size)
    }

    @Test
    fun `fails with empty samples`() {
        // Arrange
        val log = DiveLog(metadata, emptyList())

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Left<PluginError>>(result)
        assertIs<PluginError.ExecutionError>(result.value)
    }

    @Test
    fun `metric deco measurement uses m`() {
        // Arrange
        val metricMetadata = metadata.copy(depthUnit = DepthUnit.M, tempUnit = TempUnit.CELSIUS)
        val log = DiveLog(
            metricMetadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = 0, firstStopDepth = 3.0),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("m", result.value[2]["Deco Measurement (text)"])
    }

    @Test
    fun `NDL 49 keeps beginning phase active`() {
        // Arrange — NDL 49 is below the >= 50 threshold, so beginning phase stays active
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 49),
                sample(time = 5, currentNdl = 49),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("", result.value[0]["White NDL (text)"])
        assertEquals("", result.value[0]["NDL Label (text)"])
        assertEquals("", result.value[1]["White NDL (text)"])
        assertEquals("", result.value[1]["NDL Label (text)"])
    }

    @Test
    fun `NDL 50 exits beginning phase`() {
        // Arrange — NDL 50 meets the >= 50 threshold, so beginning phase ends
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = 30),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("30", result.value[2]["White NDL (text)"])
        assertEquals("NDL", result.value[2]["NDL Label (text)"])
    }

    @Test
    fun `deco detected via firstStopDepth alone with NDL greater than 0`() {
        // Arrange — firstStopDepth > 0 triggers deco even when NDL > 0
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = 5, firstStopDepth = 10.0),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[2]
        assertEquals("10", row["Decompression Ceiling"])
        assertEquals("DECO", row["Deco Label (text)"])
    }

    @Test
    fun `null NDL after beginning phase shows empty NDL columns`() {
        // Arrange — after beginning phase ends, null NDL should produce empty columns
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = null),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[2]
        assertEquals("", row["White NDL (text)"])
        assertEquals("", row["Yellow NDL (text)"])
        assertEquals("", row["Red NDL (text)"])
        assertEquals("", row["NDL Label (text)"])
    }

    @Test
    fun `clear columns show nitrox gas mixture label`() {
        // Arrange — use Nx32 gas
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99, fractionO2 = 0.32),
                sample(time = 5, currentNdl = 50, fractionO2 = 0.32),
                sample(time = 10, currentNdl = 0, firstStopDepth = 10.0, fractionO2 = 0.32),
                sample(time = 15, currentNdl = 10, fractionO2 = 0.32),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("Nx32", result.value[3]["Cleared Gas Mix Label (text)"])
    }

    @Test
    fun `clear columns show empty for trimix gas mixture label`() {
        // Arrange — use trimix (He > 0)
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99, fractionO2 = 0.18, fractionHe = 0.45),
                sample(time = 5, currentNdl = 50, fractionO2 = 0.18, fractionHe = 0.45),
                sample(time = 10, currentNdl = 0, firstStopDepth = 10.0, fractionO2 = 0.18, fractionHe = 0.45),
                sample(time = 15, currentNdl = 10, fractionO2 = 0.18, fractionHe = 0.45),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("", result.value[3]["Cleared Gas Mix Label (text)"])
    }

    @Test
    fun `deco columns empty when not in deco after beginning phase`() {
        // Arrange — after beginning phase, NDL > 0, firstStopDepth = 0
        val log = DiveLog(
            metadata,
            listOf(
                sample(time = 0, currentNdl = 99),
                sample(time = 5, currentNdl = 50),
                sample(time = 10, currentNdl = 30),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[2]
        assertEquals("", row["Decompression Ceiling"])
        assertEquals("", row["First Stop Depth Label (text)"])
        assertEquals("", row["Deco Label (text)"])
    }
}
