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

    private fun sample(time: Long, depth: Double) = DiveSample(
        timeSeconds = time,
        depth = depth,
        avgPpo2 = 0.99,
        fractionO2 = 0.21,
        fractionHe = 0.0,
        waterTemp = 77.0,
        firstStopDepth = 0.0,
        firstStopTime = 0,
        timeToSurface = 0,
        currentNdl = null,
        currentCircuitMode = 0,
        currentCcrMode = 0,
        gasSwitchNeeded = false,
        externalPpo2 = false,
    )

    private fun runPlugin(
        diveLog: DiveLog,
    ): Either<PluginError, DiveLog> = either {
        with(InterpolationPlugin) { transform(diveLog) }
    }

    @Test
    fun `interpolates samples to 1-second intervals`() {
        val log = DiveLog(metadata, listOf(sample(0, 0.0), sample(5, 10.0)))
        val result = runPlugin(log)

        assertIs<Either.Right<DiveLog>>(result)
        val samples = result.value.samples
        assertEquals(6, samples.size)
        for (i in samples.indices) {
            assertEquals(i.toLong(), samples[i].timeSeconds)
        }
    }

    @Test
    fun `preserves metadata`() {
        val log = DiveLog(metadata, listOf(sample(0, 0.0), sample(3, 9.0)))
        val result = runPlugin(log)

        assertIs<Either.Right<DiveLog>>(result)
        assertEquals(metadata, result.value.metadata)
    }

    @Test
    fun `fails with single sample`() {
        val log = DiveLog(metadata, listOf(sample(0, 10.0)))
        val result = runPlugin(log)

        assertIs<Either.Left<PluginError>>(result)
        assertIs<PluginError.ExecutionError>(result.value)
        assertTrue(result.value.message.contains("at least two samples"))
    }

    @Test
    fun `fails with empty samples`() {
        val log = DiveLog(metadata, emptyList())
        val result = runPlugin(log)

        assertIs<Either.Left<PluginError>>(result)
        assertIs<PluginError.ExecutionError>(result.value)
    }

    @Test
    fun `plugin has expected metadata`() {
        assertEquals("core.interpolation", InterpolationPlugin.id)
        assertEquals("1-Second Interpolation", InterpolationPlugin.name)
        assertTrue(InterpolationPlugin.description.isNotBlank())
        assertEquals(1, InterpolationPlugin.parameters.size)
        val param = InterpolationPlugin.parameters.first()
        assertIs<BooleanParameter>(param)
        assertEquals("enabled", param.key)
        assertEquals(false, param.defaultValue)
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
    fun `configure returns this when no enabled key in config`() {
        // Arrange
        val config = mapOf<String, Any>("other" to "value")

        // Act
        val result = InterpolationPlugin.configure(config)

        // Assert
        assertSame(InterpolationPlugin, result)
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
}
