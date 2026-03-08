package io.onema.divetelemetry.plugins

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.domain.TempUnit
import io.onema.divetelemetry.plugins.InterpolationPlugin.interpolateDiveLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InterpolationPluginTest {
    private val metadata = DiveMetadata(
        depthUnit = DepthUnit.FT,
        tempUnit = TempUnit.FAHRENHEIT,
        pressureUnit = PressureUnit.PSI,
        startTime = null,
    )

    private fun sample(
        time: Long,
        depth: Double = 0.0,
        waterTemp: Double? = 77.0,
        gasSwitchNeeded: Boolean = false,
        currentNdl: Long? = null,
    ) = DiveSample(
        timeSeconds = time,
        depth = depth,
        avgPpo2 = 0.21,
        fractionO2 = 0.21,
        fractionHe = 0.0,
        waterTemp = waterTemp,
        firstStopDepth = 0.0,
        firstStopTime = 0,
        timeToSurface = 0,
        currentNdl = currentNdl,
        currentCircuitMode = 1,
        currentCcrMode = 0,
        gasSwitchNeeded = gasSwitchNeeded,
        externalPpo2 = false,
    )

    private fun diveLog(vararg samples: DiveSample) = DiveLog(
        metadata = metadata,
        samples = samples.toList(),
    )

    private fun runPlugin(
        diveLog: DiveLog,
    ): Either<PluginError, DiveLog> = either {
        with(InterpolationPlugin) { transform(diveLog) }
    }

    // --- Plugin wrapper tests ---

    @Test
    fun `interpolates samples to 1-second intervals`() {
        // Arrange
        val log = diveLog(sample(0, 0.0), sample(5, 10.0))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<DiveLog>>(result)
        val samples = result.value.samples
        assertEquals(6, samples.size)
        for (i in samples.indices) {
            assertEquals(i.toLong(), samples[i].timeSeconds)
        }
    }

    @Test
    fun `fails with single sample`() {
        // Arrange
        val log = diveLog(sample(0, depth = 10.0))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Left<PluginError>>(result)
        assertIs<PluginError.ExecutionError>(result.value)
        assertTrue(result.value.message.contains("at least two samples"))
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
    fun `plugin has expected metadata`() {
        // Arrange / Act / Assert
        assertEquals("core.interpolation", InterpolationPlugin.id)
        assertEquals("1-Second Interpolation", InterpolationPlugin.name)
        assertTrue(InterpolationPlugin.description.isNotBlank())
        assertEquals(1, InterpolationPlugin.parameters.size)
        val enabledParam = InterpolationPlugin.parameters.first()
        assertIs<BooleanParameter>(enabledParam)
        assertEquals("enabled", enabledParam.key)
        assertEquals(false, enabledParam.defaultValue)
    }

    // --- DiveLogPlugin.configure() default implementation tests ---

    @Test
    fun `configure returns this when enabled is true`() {
        // Arrange
        val config = mapOf<String, Any>("enabled" to true)

        // Act
        val result = InterpolationPlugin.configure(config)

        // Assert
        assertSame(InterpolationPlugin, result)
    }

    @Test
    fun `configure returns null when enabled is false`() {
        // Arrange
        val config = mapOf<String, Any>("enabled" to false)

        // Act
        val result = InterpolationPlugin.configure(config)

        // Assert
        assertNull(result)
    }

    @Test
    fun `configure returns null when no enabled key and parameter default is false`() {
        // Arrange
        val config = mapOf<String, Any>("other" to "value")

        // Act
        val result = InterpolationPlugin.configure(config)

        // Assert
        assertNull(result)
    }

    @Test
    fun `configure returns this for plugin without enabled parameter`() {
        // Arrange
        val noParamPlugin = object : DiveLogPlugin {
            override val id = "test.no-params"
            override val name = "No Params"
            override val description = "Plugin with no parameters"
            override val parameters: List<PluginParameter<*>> = emptyList()

            override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog = diveLog
        }

        // Act
        val result = noParamPlugin.configure(emptyMap())

        // Assert
        assertSame(noParamPlugin, result)
    }

    // --- Pure function (interpolateDiveLog) tests ---

    @Test
    fun `already one-second intervals unchanged`() {
        // Arrange
        val log = diveLog(
            sample(0, depth = 10.0),
            sample(1, depth = 12.0),
            sample(2, depth = 14.0),
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
    fun `single row returns unchanged`() {
        // Arrange / Act
        val result = interpolateDiveLog(diveLog(sample(0, depth = 10.0)))

        // Assert
        assertEquals(1, result.samples.size)
    }

    @Test
    fun `empty samples returns empty`() {
        // Arrange / Act
        val result = interpolateDiveLog(DiveLog(metadata, emptyList()))

        // Assert
        assertEquals(0, result.samples.size)
    }

    @Test
    fun `five-second gap inserts four rows`() {
        // Arrange / Act
        val result = interpolateDiveLog(diveLog(sample(0, depth = 10.0), sample(5, depth = 20.0)))

        // Assert
        assertEquals(6, result.samples.size)
        for (i in 0..5) assertEquals(i.toLong(), result.samples[i].timeSeconds)
    }

    @Test
    fun `five-second gap numeric interpolation`() {
        // Arrange / Act
        val result = interpolateDiveLog(diveLog(sample(0, depth = 10.0), sample(5, depth = 20.0)))

        // Assert
        assertEquals(10.0, result.samples[0].depth)
        assertEquals(12.0, result.samples[1].depth)
        assertEquals(14.0, result.samples[2].depth)
        assertEquals(16.0, result.samples[3].depth)
        assertEquals(18.0, result.samples[4].depth)
        assertEquals(20.0, result.samples[5].depth)
    }

    @Test
    fun `multiple consecutive five-second gaps`() {
        // Arrange / Act
        val result = interpolateDiveLog(
            diveLog(sample(0, depth = 0.0), sample(5, depth = 50.0), sample(10, depth = 100.0))
        )

        // Assert
        assertEquals(11, result.samples.size)
        for (i in 0..10) assertEquals(i.toLong(), result.samples[i].timeSeconds)
    }

    @Test
    fun `irregular gaps`() {
        // Arrange / Act
        val result = interpolateDiveLog(
            diveLog(
                sample(0, depth = 0.0), sample(1, depth = 10.0),
                sample(4, depth = 40.0), sample(11, depth = 110.0),
            )
        )

        // Assert
        assertEquals(12, result.samples.size)
        for (i in 0..11) assertEquals(i.toLong(), result.samples[i].timeSeconds)
    }

    @Test
    fun `numeric interpolation accuracy`() {
        // Arrange / Act
        val result = interpolateDiveLog(
            diveLog(sample(0, depth = 0.0, waterTemp = 70.0), sample(4, depth = 100.0, waterTemp = 66.0))
        )

        // Assert
        assertEquals(5, result.samples.size)
        assertEquals(0.0, result.samples[0].depth)
        assertEquals(25.0, result.samples[1].depth)
        assertEquals(50.0, result.samples[2].depth)
        assertEquals(75.0, result.samples[3].depth)
        assertEquals(100.0, result.samples[4].depth)
        assertEquals(70.0, result.samples[0].waterTemp)
        assertEquals(69.0, result.samples[1].waterTemp)
        assertEquals(68.0, result.samples[2].waterTemp)
        assertEquals(67.0, result.samples[3].waterTemp)
        assertEquals(66.0, result.samples[4].waterTemp)
    }

    @Test
    fun `boolean fields copied from previous row`() {
        // Arrange / Act
        val result = interpolateDiveLog(
            diveLog(sample(0, gasSwitchNeeded = false), sample(3, gasSwitchNeeded = true))
        )

        // Assert
        assertEquals(4, result.samples.size)
        assertEquals(false, result.samples[0].gasSwitchNeeded)
        assertEquals(false, result.samples[1].gasSwitchNeeded)
        assertEquals(false, result.samples[2].gasSwitchNeeded)
        assertEquals(true, result.samples[3].gasSwitchNeeded)
    }

    @Test
    fun `two rows three-second gap`() {
        // Arrange / Act
        val result = interpolateDiveLog(diveLog(sample(10, depth = 0.0), sample(13, depth = 30.0)))

        // Assert
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
    fun `zero value fields`() {
        // Arrange / Act
        val result = interpolateDiveLog(diveLog(sample(0, depth = 0.0), sample(3, depth = 0.0)))

        // Assert
        assertEquals(4, result.samples.size)
        result.samples.forEach { assertEquals(0.0, it.depth) }
    }

    @Test
    fun `metadata preserved`() {
        // Arrange
        val customMetadata = DiveMetadata(
            depthUnit = DepthUnit.FT,
            tempUnit = TempUnit.FAHRENHEIT,
            pressureUnit = PressureUnit.PSI,
            startTime = "12/7/2025 8:39:59 PM",
            extra = mapOf("dive_number" to "42"),
        )
        val log = DiveLog(customMetadata, listOf(sample(0, depth = 10.0), sample(3, depth = 40.0)))

        // Act
        val result = interpolateDiveLog(log)

        // Assert
        assertEquals(customMetadata, result.metadata)
    }

    @Test
    fun `nullable water temp carries forward`() {
        // Arrange / Act
        val result = interpolateDiveLog(
            diveLog(sample(0, waterTemp = null), sample(3, waterTemp = 25.0))
        )

        // Assert
        assertEquals(4, result.samples.size)
        assertNull(result.samples[0].waterTemp)
        assertNull(result.samples[1].waterTemp)
        assertNull(result.samples[2].waterTemp)
        assertEquals(25.0, result.samples[3].waterTemp)
    }

    @Test
    fun `nullable NDL interpolates when both present`() {
        // Arrange / Act
        val result = interpolateDiveLog(
            diveLog(sample(0, currentNdl = 100L), sample(4, currentNdl = 96L))
        )

        // Assert
        assertEquals(5, result.samples.size)
        assertEquals(100L, result.samples[0].currentNdl)
        assertEquals(99L, result.samples[1].currentNdl)
        assertEquals(98L, result.samples[2].currentNdl)
        assertEquals(97L, result.samples[3].currentNdl)
        assertEquals(96L, result.samples[4].currentNdl)
    }
}
