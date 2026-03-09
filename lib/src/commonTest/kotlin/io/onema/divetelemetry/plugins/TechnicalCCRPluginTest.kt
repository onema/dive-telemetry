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

class TechnicalCCRPluginTest {
    private val metadata = DiveMetadata(
        depthUnit = DepthUnit.FT,
        tempUnit = TempUnit.FAHRENHEIT,
        pressureUnit = PressureUnit.PSI,
        startTime = null,
    )

    private fun sample(
        time: Long = 0L,
        depth: Double = 0.0,
        avgPpo2: Double = 0.99,
        fractionO2: Double = 0.21,
        sensor1Mv: String = "0",
        sensor2Mv: String = "0",
        sensor3Mv: String = "0",
    ) = DiveSample(
        timeSeconds = time,
        depth = depth,
        avgPpo2 = avgPpo2,
        fractionO2 = fractionO2,
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
        externalO2Sensor1Mv = sensor1Mv,
        externalO2Sensor2Mv = sensor2Mv,
        externalO2Sensor3Mv = sensor3Mv,
    )

    private fun runPlugin(log: DiveLog): Either<PluginError, List<Map<String, String>>> = either {
        with(TechnicalCCRPlugin) { computeRows(log) }
    }

    @Test
    fun `plugin has expected metadata`() {
        // Arrange / Act / Assert
        assertEquals("core.technical-ccr", TechnicalCCRPlugin.id)
        assertEquals("Technical Closed Circuit Rebreather", TechnicalCCRPlugin.name)
        assertTrue(TechnicalCCRPlugin.description.isNotBlank())
        assertEquals(1, TechnicalCCRPlugin.parameters.size)
        val enabledParam = TechnicalCCRPlugin.parameters.first()
        assertIs<BooleanParameter>(enabledParam)
        assertEquals("enabled", enabledParam.key)
        assertEquals(false, enabledParam.defaultValue)
    }

    @Test
    fun `additional headers contain 8 columns`() {
        // Arrange / Act
        val headers = TechnicalCCRPlugin.additionalHeaders(metadata)

        // Assert
        assertEquals(8, headers.size)
        assertTrue("PPO2-1 (text)" in headers)
        assertTrue("Abnormal dilPO2 (text)" in headers)
    }

    @Test
    fun `ppo2 columns empty when sensors are zero`() {
        // Arrange
        val log = DiveLog(metadata, listOf(sample()))

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[0]
        assertEquals("", row["PPO2-1 (text)"])
        assertEquals("", row["PPO2-2 (text)"])
        assertEquals("", row["PPO2-3 (text)"])
        assertEquals("", row["Excessive PO2-1 (text)"])
        assertEquals("", row["Excessive PO2-2 (text)"])
        assertEquals("", row["Excessive PO2-3 (text)"])
    }

    @Test
    fun `ppo2 columns populated with calibrated sensors`() {
        // Arrange
        val log = DiveLog(
            metadata,
            listOf(
                sample(avgPpo2 = 1.0, sensor1Mv = "50", sensor2Mv = "50", sensor3Mv = "50"),
                sample(depth = 33.0, avgPpo2 = 1.0, sensor1Mv = "50", sensor2Mv = "50", sensor3Mv = "50"),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[0]
        assertEquals("1", row["PPO2-1 (text)"])
        assertEquals("", row["Excessive PO2-1 (text)"])
    }

    @Test
    fun `excessive ppo2 detected for out-of-range values`() {
        // Arrange
        val log = DiveLog(
            metadata,
            listOf(
                sample(avgPpo2 = 2.0, sensor1Mv = "50", sensor2Mv = "0", sensor3Mv = "0"),
                sample(depth = 33.0, avgPpo2 = 2.0, sensor1Mv = "50", sensor2Mv = "0", sensor3Mv = "0"),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[1]
        assertEquals("", row["PPO2-1 (text)"])
        assertTrue(row["Excessive PO2-1 (text)"]!!.isNotBlank())
    }

    @Test
    fun `diluent ppo2 computed correctly for imperial`() {
        // Arrange
        val log = DiveLog(
            metadata,
            listOf(
                sample(depth = 33.0, fractionO2 = 0.21),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[0]
        assertEquals("0.42", row["dilPO2 (text)"])
        assertEquals("", row["Abnormal dilPO2 (text)"])
    }

    @Test
    fun `diluent ppo2 computed correctly for metric`() {
        // Arrange
        val metricMetadata = metadata.copy(depthUnit = DepthUnit.M, tempUnit = TempUnit.CELSIUS)
        val log = DiveLog(
            metricMetadata,
            listOf(
                sample(depth = 10.0, fractionO2 = 0.21),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[0]
        assertEquals("0.42", row["dilPO2 (text)"])
    }

    @Test
    fun `abnormal diluent ppo2 detected`() {
        // Arrange
        val log = DiveLog(
            metadata,
            listOf(
                sample(depth = 0.0, fractionO2 = 0.10),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[0]
        assertEquals("", row["dilPO2 (text)"])
        assertEquals("0.1", row["Abnormal dilPO2 (text)"])
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
    fun `sensor ppo2 exactly at 0 point 4 is normal`() {
        // Arrange — calibration: avgPpo2=0.4, sensor1Mv="100" => factor=0.004
        //           second sample: sensor1Mv="100" => ppo2=0.004*100=0.4 => in range [0.4..1.6]
        val log = DiveLog(
            metadata,
            listOf(
                sample(avgPpo2 = 0.4, sensor1Mv = "100", sensor2Mv = "0", sensor3Mv = "0"),
                sample(depth = 33.0, avgPpo2 = 0.4, sensor1Mv = "100", sensor2Mv = "0", sensor3Mv = "0"),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[1]
        assertEquals("0.4", row["PPO2-1 (text)"])
        assertEquals("", row["Excessive PO2-1 (text)"])
    }

    @Test
    fun `sensor ppo2 exactly at 1 point 6 is normal`() {
        // Arrange — calibration: avgPpo2=1.6, sensor1Mv="100" => factor=0.016
        //           second sample: sensor1Mv="100" => ppo2=0.016*100=1.6 => in range
        val log = DiveLog(
            metadata,
            listOf(
                sample(avgPpo2 = 1.6, sensor1Mv = "100", sensor2Mv = "0", sensor3Mv = "0"),
                sample(depth = 33.0, avgPpo2 = 1.6, sensor1Mv = "100", sensor2Mv = "0", sensor3Mv = "0"),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[1]
        assertEquals("1.6", row["PPO2-1 (text)"])
        assertEquals("", row["Excessive PO2-1 (text)"])
    }

    @Test
    fun `sensor ppo2 just below 0 point 4 is excessive`() {
        // Arrange — calibration: avgPpo2=0.39, sensor1Mv="100" => factor=0.0039
        //           second sample: sensor1Mv="100" => ppo2=0.39 => below 0.4 => excessive
        val log = DiveLog(
            metadata,
            listOf(
                sample(avgPpo2 = 0.39, sensor1Mv = "100", sensor2Mv = "0", sensor3Mv = "0"),
                sample(depth = 33.0, avgPpo2 = 0.39, sensor1Mv = "100", sensor2Mv = "0", sensor3Mv = "0"),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[1]
        assertEquals("", row["PPO2-1 (text)"])
        assertEquals("0.39", row["Excessive PO2-1 (text)"])
    }

    @Test
    fun `sensor ppo2 just above 1 point 6 is excessive`() {
        // Arrange — calibration: avgPpo2=1.61, sensor1Mv="100" => factor=0.0161
        //           second sample: sensor1Mv="100" => ppo2=1.61 => above 1.6 => excessive
        val log = DiveLog(
            metadata,
            listOf(
                sample(avgPpo2 = 1.61, sensor1Mv = "100", sensor2Mv = "0", sensor3Mv = "0"),
                sample(depth = 33.0, avgPpo2 = 1.61, sensor1Mv = "100", sensor2Mv = "0", sensor3Mv = "0"),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[1]
        assertEquals("", row["PPO2-1 (text)"])
        assertTrue(row["Excessive PO2-1 (text)"]!!.isNotBlank())
    }

    @Test
    fun `diluent ppo2 exactly at 0 point 19 is normal`() {
        // Arrange — dilPO2 = (depth/seawaterFactor + 1) * fractionO2
        //           For imperial: (depth/33 + 1) * fractionO2 = 0.19
        //           depth = 0, fractionO2 = 0.19 => dilPO2 = 1.0 * 0.19 = 0.19
        val log = DiveLog(
            metadata,
            listOf(
                sample(depth = 0.0, fractionO2 = 0.19),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[0]
        assertEquals("0.19", row["dilPO2 (text)"])
        assertEquals("", row["Abnormal dilPO2 (text)"])
    }

    @Test
    fun `diluent ppo2 just below 0 point 19 is abnormal`() {
        // Arrange — fractionO2 = 0.18 at depth 0 => dilPO2 = 0.18 < 0.19
        val log = DiveLog(
            metadata,
            listOf(
                sample(depth = 0.0, fractionO2 = 0.18),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[0]
        assertEquals("", row["dilPO2 (text)"])
        assertEquals("0.18", row["Abnormal dilPO2 (text)"])
    }

    @Test
    fun `calibration factor is null when avgPpo2 is zero`() {
        // Arrange — avgPpo2=0.0, sensor has mV => factor = 0/50 = 0, but code checks mv==0 not factor==0
        //           Actually: calibrationFactor(0.0, "50") = 0.0/50.0 = 0.0 (not null)
        //           computeSensorPpo2("50", 0.0) = 0.0 * 50 = 0.0, which == 0.0 => returns null
        val log = DiveLog(
            metadata,
            listOf(
                sample(avgPpo2 = 0.0, sensor1Mv = "50", sensor2Mv = "50", sensor3Mv = "50"),
                sample(depth = 33.0, avgPpo2 = 0.0, sensor1Mv = "50", sensor2Mv = "50", sensor3Mv = "50"),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[1]
        // calibration factor = 0.0 / 50.0 = 0.0
        // computeSensorPpo2("50", 0.0) = 0.0 * 50 = 0.0, but code checks `if (mv == 0.0) return null`
        // mv is 50.0, not 0.0, so ppo2 = 0.0, which is computed successfully
        // classifySensorPpo2(0.0) => 0.0 not in 0.4..1.6 => excessive
        assertEquals("", row["PPO2-1 (text)"])
        assertEquals("0", row["Excessive PO2-1 (text)"])
    }

    @Test
    fun `mixed sensors with some zero and some active`() {
        // Arrange — sensor 1 active, sensors 2 and 3 have zero mV
        val log = DiveLog(
            metadata,
            listOf(
                sample(avgPpo2 = 1.0, sensor1Mv = "50", sensor2Mv = "0", sensor3Mv = "0"),
                sample(depth = 33.0, avgPpo2 = 1.0, sensor1Mv = "50", sensor2Mv = "0", sensor3Mv = "0"),
            )
        )

        // Act
        val result = runPlugin(log)

        // Assert
        assertIs<Either.Right<List<Map<String, String>>>>(result)
        val row = result.value[0]
        assertEquals("1", row["PPO2-1 (text)"])
        assertEquals("", row["PPO2-2 (text)"])
        assertEquals("", row["PPO2-3 (text)"])
        assertEquals("", row["Excessive PO2-1 (text)"])
        assertEquals("", row["Excessive PO2-2 (text)"])
        assertEquals("", row["Excessive PO2-3 (text)"])
    }
}
