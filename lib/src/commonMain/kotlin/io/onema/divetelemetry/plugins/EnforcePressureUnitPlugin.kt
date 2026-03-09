package io.onema.divetelemetry.plugins

import arrow.core.raise.Raise
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.util.formatTwoDecimals

/**
 * Converts tank pressure columns to a specific unit regardless of the source computer's unit.
 * Selecting "default" keeps the source unit and excludes the plugin from the pipeline.
 */
object EnforcePressureUnitPlugin : DiveLogPlugin {
    override val id = "core.enforce-pressure-unit"
    override val name = "Pressure Unit"
    override val description = "Output tank pressure in a specific unit regardless of what the source computer recorded."
    override val parameters: List<PluginParameter<*>> = listOf(
        StringParameter(
            key = "unit",
            name = "Pressure Unit",
            description = description,
            defaultValue = "default",
            options = listOf("default", "psi", "bar"),
        )
    )

    /** Returns a [Configured] instance for "psi"/"bar", or `null` for "default". */
    override fun configure(config: Map<String, Any>): DiveLogPlugin? = when (config["unit"] as? String) {
        "psi" -> Configured(PressureUnit.PSI)
        "bar" -> Configured(PressureUnit.BAR)
        else -> null
    }

    // Unreachable — configure() always returns a Configured instance or null.
    override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog = diveLog

    private class Configured(
        private val target: PressureUnit
    ) : DiveLogPlugin {
        override val id = EnforcePressureUnitPlugin.id
        override val name = EnforcePressureUnitPlugin.name
        override val description = EnforcePressureUnitPlugin.description
        override val parameters = EnforcePressureUnitPlugin.parameters

        override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
            val source = diveLog.metadata.pressureUnit
            if (source == target) return diveLog

            val factor = when (source to target) {
                PressureUnit.PSI to PressureUnit.BAR -> PSI_TO_BAR
                PressureUnit.BAR to PressureUnit.PSI -> BAR_TO_PSI
                else -> return diveLog
            }

            val convertedSamples = diveLog.samples.map { sample ->
                sample.copy(
                    tankPressure1 = convert(sample.tankPressure1, factor),
                    tankPressure2 = convert(sample.tankPressure2, factor),
                    tankPressure3 = convert(sample.tankPressure3, factor),
                    tankPressure4 = convert(sample.tankPressure4, factor),
                )
            }

            return diveLog.copy(
                metadata = diveLog.metadata.copy(pressureUnit = target),
                samples = convertedSamples,
            )
        }

        private fun convert(value: String, factor: Double): String {
            val v = value.toDoubleOrNull() ?: return value
            return formatTwoDecimals(v * factor)
        }
    }

    private const val PSI_TO_BAR = 0.0689476
    private const val BAR_TO_PSI = 14.5038
}
