package io.onema.divetelemetry.util

import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.domain.TempUnit
import io.onema.divetelemetry.plugins.InterpolationPlugin.interpolateDiveLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterpolationTest {
    private val emptyMetadata = DiveMetadata(
        depthUnit = DepthUnit.FT,
        tempUnit = TempUnit.FAHRENHEIT,
        pressureUnit = PressureUnit.PSI,
        startTime = null,
    )

    private fun sample(
        timeSeconds: Long,
        depth: Double = 0.0,
        waterTemp: Double? = 70.0,
        gasSwitchNeeded: Boolean = false,
        currentNdl: Long? = 0L,
    ) = DiveSample(
        timeSeconds = timeSeconds,
        depth = depth,
        avgPpo2 = 0.21,
        fractionO2 = 0.21,
        fractionHe = 0.0,
        waterTemp = waterTemp,
        firstStopDepth = 0.0,
        firstStopTime = 0L,
        timeToSurface = 0L,
        currentNdl = currentNdl,
        currentCircuitMode = 1,
        currentCcrMode = 0,
        gasSwitchNeeded = gasSwitchNeeded,
        externalPpo2 = false,
    )

    private fun diveLog(vararg samples: DiveSample) = DiveLog(
        metadata = emptyMetadata,
        samples = samples.toList(),
    )

    // --- No-op cases ---

    @Test
    fun alreadyOneSecondIntervals() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, depth = 10.0),
            sample(timeSeconds = 1, depth = 12.0),
            sample(timeSeconds = 2, depth = 14.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert
        assertEquals(3, result.samples.size)
        assertEquals(0L, result.samples[0].timeSeconds)
        assertEquals(1L, result.samples[1].timeSeconds)
        assertEquals(2L, result.samples[2].timeSeconds)
    }

    @Test
    fun singleRowReturnsUnchanged() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, depth = 10.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert
        assertEquals(1, result.samples.size)
    }

    @Test
    fun twoRowsWithOneSecondGap() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, depth = 5.0),
            sample(timeSeconds = 1, depth = 10.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert
        assertEquals(2, result.samples.size)
    }

    // --- 5-second gap (Shearwater style) ---

    @Test
    fun fiveSecondGapInsertsFourRows() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, depth = 10.0),
            sample(timeSeconds = 5, depth = 20.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert -- original 2 + 4 interpolated = 6
        assertEquals(6, result.samples.size)
        assertEquals(0L, result.samples[0].timeSeconds)
        assertEquals(1L, result.samples[1].timeSeconds)
        assertEquals(2L, result.samples[2].timeSeconds)
        assertEquals(3L, result.samples[3].timeSeconds)
        assertEquals(4L, result.samples[4].timeSeconds)
        assertEquals(5L, result.samples[5].timeSeconds)
    }

    @Test
    fun fiveSecondGapNumericInterpolation() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, depth = 10.0),
            sample(timeSeconds = 5, depth = 20.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert -- linear: 10, 12, 14, 16, 18, 20
        assertEquals(10.0, result.samples[0].depth)
        assertEquals(12.0, result.samples[1].depth)
        assertEquals(14.0, result.samples[2].depth)
        assertEquals(16.0, result.samples[3].depth)
        assertEquals(18.0, result.samples[4].depth)
        assertEquals(20.0, result.samples[5].depth)
    }

    @Test
    fun multipleConsecutiveFiveSecondGaps() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, depth = 0.0),
            sample(timeSeconds = 5, depth = 50.0),
            sample(timeSeconds = 10, depth = 100.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert -- 11 samples total (0..10)
        assertEquals(11, result.samples.size)
        for (i in 0..10) {
            assertEquals(i.toLong(), result.samples[i].timeSeconds)
        }
    }

    // --- Irregular gaps ---

    @Test
    fun irregularGaps() {
        // Arrange -- gaps of 1s, 3s, 7s
        val log = diveLog(
            sample(timeSeconds = 0, depth = 0.0),
            sample(timeSeconds = 1, depth = 10.0),
            sample(timeSeconds = 4, depth = 40.0),
            sample(timeSeconds = 11, depth = 110.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert -- 12 samples: 0..11
        assertEquals(12, result.samples.size)
        for (i in 0..11) {
            assertEquals(i.toLong(), result.samples[i].timeSeconds)
        }
    }

    // --- Numeric interpolation accuracy ---

    @Test
    fun numericInterpolationAccuracy() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, depth = 0.0, waterTemp = 70.0),
            sample(timeSeconds = 4, depth = 100.0, waterTemp = 66.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert
        assertEquals(5, result.samples.size)
        // depth: 0, 25, 50, 75, 100
        assertEquals(0.0, result.samples[0].depth)
        assertEquals(25.0, result.samples[1].depth)
        assertEquals(50.0, result.samples[2].depth)
        assertEquals(75.0, result.samples[3].depth)
        assertEquals(100.0, result.samples[4].depth)
        // temp: 70, 69, 68, 67, 66
        assertEquals(70.0, result.samples[0].waterTemp)
        assertEquals(69.0, result.samples[1].waterTemp)
        assertEquals(68.0, result.samples[2].waterTemp)
        assertEquals(67.0, result.samples[3].waterTemp)
        assertEquals(66.0, result.samples[4].waterTemp)
    }

    // --- Non-numeric fields ---

    @Test
    fun booleanFieldsCopiedFromPreviousRow() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, gasSwitchNeeded = false),
            sample(timeSeconds = 3, gasSwitchNeeded = true),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert
        assertEquals(4, result.samples.size)
        assertEquals(false, result.samples[0].gasSwitchNeeded)
        assertEquals(false, result.samples[1].gasSwitchNeeded)
        assertEquals(false, result.samples[2].gasSwitchNeeded)
        assertEquals(true, result.samples[3].gasSwitchNeeded)
    }

    // --- Edge cases ---

    @Test
    fun emptySamples() {
        // Arrange
        val log = DiveLog(
            metadata = emptyMetadata,
            samples = emptyList(),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert
        assertEquals(0, result.samples.size)
    }

    @Test
    fun twoRowsThreeSecondGap() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 10, depth = 0.0),
            sample(timeSeconds = 13, depth = 30.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert -- 4 samples: t=10, 11, 12, 13
        assertEquals(4, result.samples.size)
        assertEquals(10L, result.samples[0].timeSeconds)
        assertEquals(0.0, result.samples[0].depth)
        assertEquals(11L, result.samples[1].timeSeconds)
        assertEquals(10.0, result.samples[1].depth)
        assertEquals(12L, result.samples[2].timeSeconds)
        assertEquals(20.0, result.samples[2].depth)
        assertEquals(13L, result.samples[3].timeSeconds)
        assertEquals(30.0, result.samples[3].depth)
    }

    @Test
    fun zeroValueFields() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, depth = 0.0),
            sample(timeSeconds = 3, depth = 0.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert -- all depths should be 0
        assertEquals(4, result.samples.size)
        result.samples.forEach { assertEquals(0.0, it.depth) }
    }

    @Test
    fun metadataPreserved() {
        // Arrange
        val metadata = DiveMetadata(
            depthUnit = DepthUnit.FT,
            tempUnit = TempUnit.FAHRENHEIT,
            pressureUnit = PressureUnit.PSI,
            startTime = "12/7/2025 8:39:59 PM",
            extra = mapOf("dive_number" to "42"),
        )
        val log = DiveLog(
            metadata = metadata,
            samples = listOf(
                sample(timeSeconds = 0, depth = 10.0),
                sample(timeSeconds = 3, depth = 40.0),
            ),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert
        assertEquals(metadata, result.metadata)
    }

    @Test
    fun nullableWaterTempCarriesForward() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, waterTemp = null),
            sample(timeSeconds = 3, waterTemp = 25.0),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert
        assertEquals(4, result.samples.size)
        assertNull(result.samples[0].waterTemp)
        assertNull(result.samples[1].waterTemp)
        assertNull(result.samples[2].waterTemp)
        assertEquals(25.0, result.samples[3].waterTemp)
    }

    @Test
    fun nullableNdlInterpolatesWhenBothPresent() {
        // Arrange
        val log = diveLog(
            sample(timeSeconds = 0, currentNdl = 100L),
            sample(timeSeconds = 4, currentNdl = 96L),
        )

        // Act
        val result = interpolateDiveLog(log)

        // Assert
        assertEquals(5, result.samples.size)
        assertEquals(100L, result.samples[0].currentNdl)
        assertEquals(99L, result.samples[1].currentNdl)
        assertEquals(98L, result.samples[2].currentNdl)
        assertEquals(97L, result.samples[3].currentNdl)
        assertEquals(96L, result.samples[4].currentNdl)
    }
}
