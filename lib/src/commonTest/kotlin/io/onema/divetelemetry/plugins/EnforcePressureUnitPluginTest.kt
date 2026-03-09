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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnforcePressureUnitPluginTest {
    private fun metadata(pressureUnit: PressureUnit = PressureUnit.PSI) = DiveMetadata(
        depthUnit = DepthUnit.FT,
        tempUnit = TempUnit.FAHRENHEIT,
        pressureUnit = pressureUnit,
        startTime = null,
    )

    private fun sample(
        tankPressure1: String = "3000",
        tankPressure2: String = "2500",
        tankPressure3: String = "0",
        tankPressure4: String = "0",
    ) = DiveSample(
        timeSeconds = 0L,
        depth = 30.0,
        avgPpo2 = 0.99,
        fractionO2 = 0.21,
        fractionHe = 0.0,
        waterTemp = 77.0,
        firstStopDepth = 0.0,
        firstStopTime = 0,
        timeToSurface = 0,
        currentNdl = null,
        currentCircuitMode = 1,
        currentCcrMode = 0,
        gasSwitchNeeded = false,
        externalPpo2 = false,
        tankPressure1 = tankPressure1,
        tankPressure2 = tankPressure2,
        tankPressure3 = tankPressure3,
        tankPressure4 = tankPressure4,
    )

    private fun runPlugin(plugin: DiveLogPlugin, log: DiveLog): Either<PluginError, DiveLog> = either {
        with(plugin) { transform(log) }
    }

    @Test
    fun `plugin has expected metadata`() {
        // Arrange / Act / Assert
        assertEquals("core.enforce-pressure-unit", EnforcePressureUnitPlugin.id)
        assertEquals("Pressure Unit", EnforcePressureUnitPlugin.name)
        assertTrue(EnforcePressureUnitPlugin.description.isNotBlank())
        assertEquals(1, EnforcePressureUnitPlugin.parameters.size)
        val param = EnforcePressureUnitPlugin.parameters.first()
        assertIs<StringParameter>(param)
        assertEquals("unit", param.key)
        assertEquals("default", param.defaultValue)
        assertEquals(listOf("default", "psi", "bar"), param.options)
    }

    @Test
    fun `configure returns null for default`() {
        // Arrange
        val config = mapOf<String, Any>("unit" to "default")

        // Act
        val result = EnforcePressureUnitPlugin.configure(config)

        // Assert
        assertNull(result)
    }

    @Test
    fun `configure returns null for empty config`() {
        // Arrange
        val config = emptyMap<String, Any>()

        // Act
        val result = EnforcePressureUnitPlugin.configure(config)

        // Assert
        assertNull(result)
    }

    @Test
    fun `configure returns plugin for psi`() {
        // Arrange
        val config = mapOf<String, Any>("unit" to "psi")

        // Act
        val result = EnforcePressureUnitPlugin.configure(config)

        // Assert
        assertIs<DiveLogPlugin>(result)
    }

    @Test
    fun `configure returns plugin for bar`() {
        // Arrange
        val config = mapOf<String, Any>("unit" to "bar")

        // Act
        val result = EnforcePressureUnitPlugin.configure(config)

        // Assert
        assertIs<DiveLogPlugin>(result)
    }

    @Test
    fun `PSI to BAR conversion applies correct factor`() {
        // Arrange
        val plugin = EnforcePressureUnitPlugin.configure(mapOf("unit" to "bar"))!!
        val log = DiveLog(metadata(PressureUnit.PSI), listOf(sample(tankPressure1 = "3000")))

        // Act
        val result = runPlugin(plugin, log)

        // Assert
        assertIs<Either.Right<DiveLog>>(result)
        val converted = result.value.samples[0]
            .tankPressure1
            .toDouble()
        val expected = 3000.0 * 0.0689476
        assertTrue(
            kotlin.math.abs(converted - expected) < 0.1,
            "Expected ~$expected, got $converted"
        )
    }

    @Test
    fun `BAR to PSI conversion applies correct factor`() {
        // Arrange
        val plugin = EnforcePressureUnitPlugin.configure(mapOf("unit" to "psi"))!!
        val log = DiveLog(metadata(PressureUnit.BAR), listOf(sample(tankPressure1 = "200")))

        // Act
        val result = runPlugin(plugin, log)

        // Assert
        assertIs<Either.Right<DiveLog>>(result)
        val converted = result.value.samples[0]
            .tankPressure1
            .toDouble()
        val expected = 200.0 * 14.5038
        assertTrue(
            kotlin.math.abs(converted - expected) < 0.1,
            "Expected ~$expected, got $converted"
        )
    }

    @Test
    fun `no-op when source equals target`() {
        // Arrange
        val plugin = EnforcePressureUnitPlugin.configure(mapOf("unit" to "psi"))!!
        val log = DiveLog(metadata(PressureUnit.PSI), listOf(sample(tankPressure1 = "3000")))

        // Act
        val result = runPlugin(plugin, log)

        // Assert
        assertIs<Either.Right<DiveLog>>(result)
        assertEquals("3000", result.value.samples[0].tankPressure1)
        assertEquals(PressureUnit.PSI, result.value.metadata.pressureUnit)
    }

    @Test
    fun `all four tank pressures converted`() {
        // Arrange
        val plugin = EnforcePressureUnitPlugin.configure(mapOf("unit" to "bar"))!!
        val log = DiveLog(
            metadata(PressureUnit.PSI),
            listOf(
                sample(
                    tankPressure1 = "3000",
                    tankPressure2 = "2500",
                    tankPressure3 = "1000",
                    tankPressure4 = "500",
                )
            )
        )

        // Act
        val result = runPlugin(plugin, log)

        // Assert
        assertIs<Either.Right<DiveLog>>(result)
        val s = result.value.samples[0]
        assertTrue(s.tankPressure1.toDouble() > 200.0)
        assertTrue(s.tankPressure2.toDouble() > 170.0)
        assertTrue(s.tankPressure3.toDouble() > 60.0)
        assertTrue(s.tankPressure4.toDouble() > 30.0)
    }

    @Test
    fun `non-numeric pressure values pass through unchanged`() {
        // Arrange
        val plugin = EnforcePressureUnitPlugin.configure(mapOf("unit" to "bar"))!!
        val log = DiveLog(
            metadata(PressureUnit.PSI),
            listOf(
                sample(
                    tankPressure1 = "AI is off",
                    tankPressure2 = "N/A",
                    tankPressure3 = "No comms for 90s +",
                    tankPressure4 = "",
                )
            )
        )

        // Act
        val result = runPlugin(plugin, log)

        // Assert
        assertIs<Either.Right<DiveLog>>(result)
        val s = result.value.samples[0]
        assertEquals("AI is off", s.tankPressure1)
        assertEquals("N/A", s.tankPressure2)
        assertEquals("No comms for 90s +", s.tankPressure3)
        assertEquals("", s.tankPressure4)
    }

    @Test
    fun `metadata pressureUnit updated after conversion`() {
        // Arrange
        val plugin = EnforcePressureUnitPlugin.configure(mapOf("unit" to "bar"))!!
        val log = DiveLog(metadata(PressureUnit.PSI), listOf(sample()))

        // Act
        val result = runPlugin(plugin, log)

        // Assert
        assertIs<Either.Right<DiveLog>>(result)
        assertEquals(PressureUnit.BAR, result.value.metadata.pressureUnit)
    }

    @Test
    fun `configured plugin inherits id and name`() {
        // Arrange
        val plugin = EnforcePressureUnitPlugin.configure(mapOf("unit" to "bar"))!!

        // Act / Assert
        assertEquals(EnforcePressureUnitPlugin.id, plugin.id)
        assertEquals(EnforcePressureUnitPlugin.name, plugin.name)
        assertEquals(EnforcePressureUnitPlugin.description, plugin.description)
    }

    @Test
    fun `unconfigured object transform is passthrough`() {
        // Arrange
        val log = DiveLog(metadata(PressureUnit.PSI), listOf(sample()))

        // Act
        val result = runPlugin(EnforcePressureUnitPlugin, log)

        // Assert
        assertIs<Either.Right<DiveLog>>(result)
        assertEquals(log, result.value)
    }

    @Test
    fun `multiple samples all converted`() {
        // Arrange
        val plugin = EnforcePressureUnitPlugin.configure(mapOf("unit" to "bar"))!!
        val log = DiveLog(
            metadata(PressureUnit.PSI),
            listOf(
                sample(tankPressure1 = "3000"),
                sample(tankPressure1 = "2800"),
                sample(tankPressure1 = "2600"),
            )
        )

        // Act
        val result = runPlugin(plugin, log)

        // Assert
        assertIs<Either.Right<DiveLog>>(result)
        assertEquals(3, result.value.samples.size)
        assertTrue(
            result.value.samples[0]
                .tankPressure1
                .toDouble() > result.value.samples[1]
                .tankPressure1
                .toDouble()
        )
        assertTrue(
            result.value.samples[1]
                .tankPressure1
                .toDouble() > result.value.samples[2]
                .tankPressure1
                .toDouble()
        )
    }
}
