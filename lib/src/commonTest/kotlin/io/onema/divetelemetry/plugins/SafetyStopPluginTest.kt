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

class SafetyStopPluginTest {

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
    ) = DiveSample(
        timeSeconds = time,
        depth = depth,
        avgPpo2 = 0.99,
        fractionO2 = 0.21,
        fractionHe = 0.0,
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
        with(SafetyStopPlugin) { computeRows(log) }
    }

    @Test
    fun `plugin has expected metadata`() {
        // Arrange / Act / Assert
        assertEquals("core.safety-stop", SafetyStopPlugin.id)
        assertEquals("Safety Stop Timer", SafetyStopPlugin.name)
        assertTrue(SafetyStopPlugin.description.isNotBlank())
        assertEquals(1, SafetyStopPlugin.parameters.size)
        val param = SafetyStopPlugin.parameters.first()
        assertIs<BooleanParameter>(param)
        assertEquals("enabled", param.key)
        assertEquals(false, param.defaultValue)
    }

    @Test
    fun `additional headers contain 1 column`() {
        // Arrange / Act
        val headers = SafetyStopPlugin.additionalHeaders(metadata)

        // Assert
        assertEquals(1, headers.size)
        assertEquals("Safety Stop Timer (text)", headers[0])
    }

    @Test
    fun `empty timer during beginning phase`() {
        // Arrange
        val log = DiveLog(metadata, listOf(
            sample(time = 0, depth = 15.0, currentNdl = 10),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("", result.value[0]["Safety Stop Timer (text)"])
    }

    @Test
    fun `safety stop timer starts in zone after being deep`() {
        // Arrange
        val log = DiveLog(metadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 30.0, currentNdl = 40),
            sample(time = 15, depth = 15.0, currentNdl = 40),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("3:00", result.value[3]["Safety Stop Timer (text)"])
    }

    @Test
    fun `safety stop timer is 5 minutes after deco`() {
        // Arrange
        val log = DiveLog(metadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 60.0, currentNdl = 0, firstStopDepth = 10.0),
            sample(time = 15, depth = 30.0, currentNdl = 10),
            sample(time = 20, depth = 15.0, currentNdl = 20),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("5:00", result.value[4]["Safety Stop Timer (text)"])
    }

    @Test
    fun `timer counts down over intervals`() {
        // Arrange
        val log = DiveLog(metadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 30.0, currentNdl = 40),
            sample(time = 15, depth = 15.0, currentNdl = 40),
            sample(time = 20, depth = 15.0, currentNdl = 40),
            sample(time = 25, depth = 15.0, currentNdl = 40),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("3:00", result.value[3]["Safety Stop Timer (text)"])
        assertEquals("2:55", result.value[4]["Safety Stop Timer (text)"])
        assertEquals("2:50", result.value[5]["Safety Stop Timer (text)"])
    }

    @Test
    fun `timer empty when not in safety zone`() {
        // Arrange
        val log = DiveLog(metadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 30.0, currentNdl = 40),
            sample(time = 15, depth = 30.0, currentNdl = 40),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("", result.value[3]["Safety Stop Timer (text)"])
    }

    @Test
    fun `returns one row per sample`() {
        // Arrange
        val log = DiveLog(metadata, listOf(sample(), sample(time = 5), sample(time = 10)))

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
    fun `metric safety zone uses 3-6m`() {
        // Arrange
        val metricMetadata = metadata.copy(depthUnit = DepthUnit.M, tempUnit = TempUnit.CELSIUS)
        val log = DiveLog(metricMetadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 10.0, currentNdl = 40),
            sample(time = 15, depth = 5.0, currentNdl = 40),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("3:00", result.value[3]["Safety Stop Timer (text)"])
    }

    @Test
    fun `countdown reaches zero and stays empty`() {
        // Arrange — 180s countdown with 1-second intervals: after 180 steps it reaches 0
        val samples = buildList {
            add(sample(time = 0, currentNdl = 99))
            add(sample(time = 1, currentNdl = 50))
            add(sample(time = 2, depth = 30.0, currentNdl = 40))
            // Enter safety zone at t=3, timer starts at 180s
            for (t in 3L..183L) {
                add(sample(time = t, depth = 15.0, currentNdl = 40))
            }
        }
        val log = DiveLog(metadata, samples)

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        // At t=3, timer = 180; at t=4, timer = 179; ... at t=182, timer = 1; at t=183, timer = 0
        assertEquals("3:00", result.value[3]["Safety Stop Timer (text)"])
        assertEquals("2:59", result.value[4]["Safety Stop Timer (text)"])
        // At the final sample (index 183), remaining = maxOf(0, 1 - 1) = 0
        assertEquals("", result.value[183]["Safety Stop Timer (text)"])
    }

    @Test
    fun `timer resets when leaving and re-entering safety zone`() {
        // Arrange — enter zone, count down, leave zone, re-enter zone
        val log = DiveLog(metadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 30.0, currentNdl = 40),
            // Enter safety zone
            sample(time = 15, depth = 15.0, currentNdl = 40),
            sample(time = 20, depth = 15.0, currentNdl = 40),
            // Leave safety zone (go deeper)
            sample(time = 25, depth = 30.0, currentNdl = 40),
            // Re-enter safety zone — timer should restart
            sample(time = 30, depth = 15.0, currentNdl = 40),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        // First entry at index 3: timer = 180
        assertEquals("3:00", result.value[3]["Safety Stop Timer (text)"])
        // Counting down at index 4: timer = 175
        assertEquals("2:55", result.value[4]["Safety Stop Timer (text)"])
        // Out of zone at index 5: timer = 0
        assertEquals("", result.value[5]["Safety Stop Timer (text)"])
        // Re-enter zone at index 6: new 180s timer starts
        assertEquals("3:00", result.value[6]["Safety Stop Timer (text)"])
    }

    @Test
    fun `exact lower boundary of safety zone 10 ft triggers timer`() {
        // Arrange — depth exactly at 10.0 ft (lower boundary of 10..20 range)
        val log = DiveLog(metadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 30.0, currentNdl = 40),
            sample(time = 15, depth = 10.0, currentNdl = 40),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("3:00", result.value[3]["Safety Stop Timer (text)"])
    }

    @Test
    fun `exact upper boundary of safety zone 20 ft triggers timer`() {
        // Arrange — depth exactly at 20.0 ft (upper boundary of 10..20 range)
        val log = DiveLog(metadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 30.0, currentNdl = 40),
            sample(time = 15, depth = 20.0, currentNdl = 40),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("3:00", result.value[3]["Safety Stop Timer (text)"])
    }

    @Test
    fun `depth just below safety zone lower boundary does not trigger timer`() {
        // Arrange — depth 9.9 ft, just below 10.0 (outside safety zone)
        val log = DiveLog(metadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 30.0, currentNdl = 40),
            sample(time = 15, depth = 9.9, currentNdl = 40),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("", result.value[3]["Safety Stop Timer (text)"])
    }

    @Test
    fun `timer is zero during active deco`() {
        // Arrange — in deco (NDL=0, firstStopDepth>0) while in safety zone depth
        val log = DiveLog(metadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 30.0, currentNdl = 40),
            sample(time = 15, depth = 15.0, currentNdl = 0, firstStopDepth = 10.0),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("", result.value[3]["Safety Stop Timer (text)"])
    }

    @Test
    fun `timer not triggered when diver has not been deep`() {
        // Arrange — diver stays shallow (depth <= safety zone upper limit = 20ft)
        val log = DiveLog(metadata, listOf(
            sample(time = 0, currentNdl = 99),
            sample(time = 5, currentNdl = 50),
            sample(time = 10, depth = 15.0, currentNdl = 40),
            sample(time = 15, depth = 15.0, currentNdl = 40),
        ))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        assertEquals("", result.value[2]["Safety Stop Timer (text)"])
        assertEquals("", result.value[3]["Safety Stop Timer (text)"])
    }
}
