# Creating Plugins

> **New to `DiveLog`?** Both plugin types operate on the typed domain model produced by the parser. See [Parsing and the domain model](parsing-and-domain-model.md) before implementing your first plugin.

## Writing a `DiveLogPlugin`

Pre-conversion plugins transform the typed `DiveLog` before it reaches the converter.

### Example — keep only the deepest half of the dive

```kotlin
package io.onema.divetelemetry.plugins

import arrow.core.raise.Raise
import io.onema.divetelemetry.domain.DiveLog

object DeepHalfPlugin : DiveLogPlugin {
    override val id          = "example.deep-half"
    override val name        = "Deep Half"
    override val description = "Keeps only samples from the deepest half of the dive."
    override val parameters: List<PluginParameter<*>> = emptyList()

    override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
        val maxDepth  = diveLog.samples.maxOfOrNull { it.depth }
            ?: raise(PluginError.ExecutionError("No samples found"))
        val threshold = maxDepth / 2
        return diveLog.copy(samples = diveLog.samples.filter { it.depth >= threshold })
    }
}
```

Key rules:
- Implement as an `object` (singleton) unless the plugin requires instance state.
- Call `raise(PluginError.ExecutionError("reason"))` to signal failure — do not throw exceptions.
- Return a new `DiveLog`; `DiveSample` and `DiveLog` are immutable data classes, use `copy()`.

---

## Writing an `OutputPlugin`

Post-conversion plugins append columns to the `TelemetryOutput`.

### Example — elapsed time in decimal minutes

```kotlin
package io.onema.divetelemetry.plugins

import arrow.core.raise.Raise
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata

object ElapsedMinutesPlugin : OutputPlugin {
    override val id          = "example.elapsed-minutes"
    override val name        = "Elapsed Minutes"
    override val description = "Adds elapsed dive time in decimal minutes."
    override val parameters: List<PluginParameter<*>> = emptyList()

    override fun additionalHeaders(metadata: DiveMetadata): List<String> =
        listOf("Elapsed Minutes (text)")

    override fun Raise<PluginError>.computeRows(log: DiveLog): List<Map<String, String>> =
        log.samples.map { sample ->
            mapOf("Elapsed Minutes (text)" to "%.2f".format(sample.timeSeconds / 60.0))
        }
}
```

Key rules:
- `additionalHeaders()` and `computeRows()` must use identical column name strings.
- `computeRows()` **must** return exactly `log.samples.size` maps — the pipeline validates this and raises `PluginError.ExecutionError` on mismatch.

---

## Adding configurable parameters

Use `BooleanParameter`, `IntParameter`, or `StringParameter` to expose options. The UI generates controls automatically; the CLI maps them to flags or `.choice()` options (see [adding-plugins-to-ui-cli.md](adding-plugins-to-ui-cli.md)).

```kotlin
object ThresholdPlugin : DiveLogPlugin {
    override val id          = "example.threshold"
    override val name        = "Depth Threshold"
    override val description = "Removes samples shallower than a configurable depth."

    private val thresholdParam = IntParameter(
        key          = "minDepth",
        name         = "Min Depth",
        description  = "Minimum depth in source units to retain.",
        defaultValue = 5,
    )

    override val parameters: List<PluginParameter<*>> = listOf(thresholdParam)

    override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
        val threshold = thresholdParam.defaultValue.toDouble()
        return diveLog.copy(samples = diveLog.samples.filter { it.depth >= threshold })
    }
}
```

For `StringParameter` with a discrete set of choices, override `configure()` to return a configured instance (or `null` to exclude the plugin from the pipeline). The `object` itself acts as the descriptor; the inner class holds the resolved configuration:

```kotlin
object ModePlugin : DiveLogPlugin {
    override val id          = "example.mode"
    override val name        = "Mode"
    override val description = "Example plugin with a string choice parameter."

    override val parameters: List<PluginParameter<*>> = listOf(
        StringParameter(
            key          = "mode",
            name         = "Mode",
            description  = description,
            defaultValue = "off",
            options      = listOf("off", "a", "b"),
        )
    )

    // Return null for the default/no-op value; return a Configured instance otherwise.
    override fun configure(config: Map<String, Any>): DiveLogPlugin? = when (config["mode"] as? String) {
        "a"  -> Configured(useA = true)
        "b"  -> Configured(useA = false)
        else -> null  // "off" or missing — exclude from pipeline
    }

    // Unreachable on the descriptor object itself.
    override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog = diveLog

    private class Configured(private val useA: Boolean) : DiveLogPlugin {
        override val id          = ModePlugin.id
        override val name        = ModePlugin.name
        override val description = ModePlugin.description
        override val parameters  = ModePlugin.parameters

        override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
            // apply transformation based on useA
            return diveLog
        }
    }
}
```
