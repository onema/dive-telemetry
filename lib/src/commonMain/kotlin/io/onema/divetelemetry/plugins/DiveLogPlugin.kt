package io.onema.divetelemetry.plugins

import arrow.core.raise.Raise
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.error.PipelineError

/** Error raised by a plugin; surfaces directly as a [PipelineError] to the pipeline caller. */
sealed interface PluginError : PipelineError {
    data class ExecutionError(override val message: String) : PluginError
}

/**
 * Descriptor for a user-configurable plugin option.
 * The UI generates controls from these; the CLI maps them to command-line flags.
 *
 * @param T The value type of the option.
 * @property key Unique identifier used to look up the value at runtime.
 * @property name Human-readable label shown in the UI.
 * @property description Shown in UI tooltips and CLI help text.
 * @property defaultValue Value used when the option is not explicitly configured.
 */
sealed interface PluginParameter<T> {
    val key: String
    val name: String
    val description: String
    val defaultValue: T
}

data class BooleanParameter(
    override val key: String,
    override val name: String,
    override val description: String,
    override val defaultValue: Boolean,
) : PluginParameter<Boolean>

data class IntParameter(
    override val key: String,
    override val name: String,
    override val description: String,
    override val defaultValue: Int,
) : PluginParameter<Int>

data class StringParameter(
    override val key: String,
    override val name: String,
    override val description: String,
    override val defaultValue: String,
    val options: List<String>,
) : PluginParameter<String>

/** Base contract shared by [DiveLogPlugin] and [OutputPlugin]. */
interface Plugin {
    val id: String
    val name: String
    val description: String
    val parameters: List<PluginParameter<*>>
}

/**
 * Pre-conversion plugin that transforms the typed [DiveLog] before it reaches the converter.
 * Runs in phase 1 of the pipeline.
 */
interface DiveLogPlugin : Plugin {
    /**
     * @receiver Raise context for signalling a [PluginError] on failure.
     * @param diveLog The dive log to transform.
     * @return The transformed dive log.
     */
    fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog

    /**
     * Returns a configured instance of this plugin based on [config], or `null` if the plugin
     * should be excluded from the pipeline (e.g. when "enabled" is false or the option is "default").
     *
     * The default implementation handles the common [BooleanParameter] "enabled" pattern.
     * Override to support richer configuration (e.g. [StringParameter] with multiple choices).
     */
    fun configure(config: Map<String, Any>): DiveLogPlugin? {
        val enabledParam = parameters.filterIsInstance<BooleanParameter>().find { it.key == "enabled" }
        return if (enabledParam == null || config["enabled"] == true) this else null
    }
}
