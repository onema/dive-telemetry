package io.onema.divetelemetry.plugins

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.util.formatTwoDecimals

object TechnicalCCRPlugin : OutputPlugin {
    override val id: String = "core.technical-ccr"
    override val name: String = "Technical Closed Circuit Rebreather"
    override val description: String =
        "Adds per-sensor PPO2, excessive PO2 detection, diluent PPO2, and abnormal dilPO2 columns."

    override val parameters: List<PluginParameter<*>> = listOf(
        BooleanParameter(
            key = "enabled",
            name = "Enable Technical CCR",
            description = "Add per-sensor PPO2 and diluent PPO2 columns.",
            defaultValue = false,
        )
    )

    override fun additionalHeaders(metadata: DiveMetadata): List<String> = listOf(
        "PPO2-1 (text)",
        "PPO2-2 (text)",
        "PPO2-3 (text)",
        "Excessive PO2-1 (text)",
        "Excessive PO2-2 (text)",
        "Excessive PO2-3 (text)",
        "dilPO2 (text)",
        "Abnormal dilPO2 (text)",
    )

    override fun Raise<PluginError>.computeRows(log: DiveLog): List<Map<String, String>> {
        ensure(log.samples.isNotEmpty()) {
            PluginError.ExecutionError("TechnicalCCRPlugin requires at least one sample.")
        }

        val seawaterFactor = when (log.metadata.depthUnit) {
            DepthUnit.FT -> 33.0
            DepthUnit.M -> 10.0
        }

        val calibrationFactors = computeSensorCalibrationFactors(log.samples)

        return log.samples.map { sample ->
            val sensorPpo2s = listOf(
                computeSensorPpo2(sample.externalO2Sensor1Mv, calibrationFactors.sensor1),
                computeSensorPpo2(sample.externalO2Sensor2Mv, calibrationFactors.sensor2),
                computeSensorPpo2(sample.externalO2Sensor3Mv, calibrationFactors.sensor3),
            )

            val ppo2Columns = sensorPpo2s.map { classifySensorPpo2(it) }
            val diluentColumns = computeDiluentPpo2(sample.depth, sample.fractionO2, seawaterFactor)

            mapOf(
                "PPO2-1 (text)" to ppo2Columns[0].first,
                "PPO2-2 (text)" to ppo2Columns[1].first,
                "PPO2-3 (text)" to ppo2Columns[2].first,
                "Excessive PO2-1 (text)" to ppo2Columns[0].second,
                "Excessive PO2-2 (text)" to ppo2Columns[1].second,
                "Excessive PO2-3 (text)" to ppo2Columns[2].second,
                "dilPO2 (text)" to diluentColumns.first,
                "Abnormal dilPO2 (text)" to diluentColumns.second,
            )
        }
    }

    private fun computeSensorCalibrationFactors(samples: List<DiveSample>): SensorCalibrationFactors {
        val first = samples.firstOrNull() ?: return SensorCalibrationFactors(null, null, null)
        val avgPpo2 = first.avgPpo2
        return SensorCalibrationFactors(
            sensor1 = calibrationFactor(avgPpo2, first.externalO2Sensor1Mv),
            sensor2 = calibrationFactor(avgPpo2, first.externalO2Sensor2Mv),
            sensor3 = calibrationFactor(avgPpo2, first.externalO2Sensor3Mv),
        )
    }

    private fun calibrationFactor(avgPpo2: Double, sensorMvStr: String): Double? {
        val mv = sensorMvStr.toDoubleOrNull() ?: return null
        if (mv == 0.0) return null
        return avgPpo2 / mv
    }

    private fun computeSensorPpo2(sensorMvStr: String, calibrationFactor: Double?): Double? {
        if (calibrationFactor == null) return null
        val mv = sensorMvStr.toDoubleOrNull() ?: return null
        if (mv == 0.0) return null
        return calibrationFactor * mv
    }

    private fun classifySensorPpo2(ppo2: Double?): Pair<String, String> {
        if (ppo2 == null) return "" to ""
        val formatted = formatTwoDecimals(ppo2)
        return if (ppo2 in 0.4..1.6) {
            formatted to ""
        } else {
            "" to formatted
        }
    }

    private fun computeDiluentPpo2(depth: Double, fractionO2: Double, seawaterFactor: Double): Pair<String, String> {
        val dilPo2 = (depth / seawaterFactor + 1.0) * fractionO2
        val formatted = formatTwoDecimals(dilPo2)
        return if (dilPo2 in 0.19..1.60) {
            formatted to ""
        } else {
            "" to formatted
        }
    }

    private data class SensorCalibrationFactors(
        val sensor1: Double?,
        val sensor2: Double?,
        val sensor3: Double?,
    )
}




