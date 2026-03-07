package io.onema.divetelemetry.domain

enum class DepthUnit { FT, M }

enum class TempUnit { FAHRENHEIT, CELSIUS }

enum class PressureUnit { PSI, BAR }

/**
 * Dive-level metadata extracted from the source file header.
 *
 * @property depthUnit Unit used for all depth values in [DiveSample].
 * @property tempUnit Unit used for all temperature values in [DiveSample].
 * @property pressureUnit Unit used for all tank pressure values in [DiveSample].
 * @property startTime Raw wall-clock start time from the source (e.g. "12/7/2025 9:00:00 AM").
 * @property extra Format-specific fields not covered by the typed schema.
 */
data class DiveMetadata(
    val depthUnit: DepthUnit,
    val tempUnit: TempUnit,
    val pressureUnit: PressureUnit,
    val startTime: String?,
    val extra: Map<String, String> = emptyMap(),
)

/**
 * A single timestamped sample from the dive log.
 * All numeric fields use source units as declared in [DiveMetadata].
 * Depth is stored as a positive value; negation for display is applied during conversion.
 */
data class DiveSample(
    val timeSeconds: Long,
    val depth: Double,
    val avgPpo2: Double,
    val fractionO2: Double,
    val fractionHe: Double,
    val waterTemp: Double?,
    val firstStopDepth: Double,
    val firstStopTime: Long,
    val timeToSurface: Long,
    val currentNdl: Long?,
    val currentCircuitMode: Int,
    val currentCcrMode: Int,
    val gasSwitchNeeded: Boolean,
    val externalPpo2: Boolean,
    val setPointType: Int = 0,
    val circuitSwitchType: Int = 0,
    val externalO2Sensor1Mv: String = "0",
    val externalO2Sensor2Mv: String = "0",
    val externalO2Sensor3Mv: String = "0",
    val batteryVoltage: String = "0",
    val tankPressure1: String = "0",
    val tankPressure2: String = "0",
    val tankPressure3: String = "0",
    val tankPressure4: String = "0",
    val gasTimeRemaining: String = "0",
    val sacRate: String = "0",
    val ascentRate: String = "0",
    val safeAscentDepth: String = "0",
    val co2mbar: String = "0",
)

/**
 * Typed domain model produced by parsing. Passed through [DiveLogPlugin] transforms before conversion.
 *
 * @property metadata Dive-level metadata.
 * @property samples Ordered list of timestamped dive samples.
 */
data class DiveLog(
    val metadata: DiveMetadata,
    val samples: List<DiveSample>,
)

/**
 * A single output row keyed by column header name, ready for CSV serialization.
 *
 * @property values Map of header name to formatted string value.
 */
data class TelemetryRow(
    val values: Map<String, String>
)

/**
 * String-only structure produced by [TelemetryConverter] and enriched by [OutputPlugin]s before writing.
 *
 * @property headers Ordered column names.
 * @property rows One [TelemetryRow] per dive sample, in order.
 */
data class TelemetryOutput(
    val headers: List<String>,
    val rows: List<TelemetryRow>
)

/**
 * A decoded FIT protocol message.
 *
 * @property globalMessageNumber Identifies the message type (e.g. 20 = record, 18 = session).
 * @property fields Field values keyed by field definition number.
 */
data class FitMessage(
    val globalMessageNumber: Int,
    val fields: Map<Int, Any?>
)
