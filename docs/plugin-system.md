# Plugin System

The plugin system has two phases, each with its own interface. Both extend the `Plugin` base interface.

```
Source -> parse -> [DiveLogPlugin chain] -> convert (24 cols) -> [OutputPlugin chain] -> write -> Sink
```

Both plugin lists are passed as ordered arguments to `transformDiveLog()` — there is no global registry or state.

The `parse` step produces a `DiveLog` — the typed domain model that both plugin phases operate on. See [Parsing and the domain model](parsing-and-domain-model.md) for the full type reference and parser contract.

---

## Phase 1 — `DiveLogPlugin` (pre-conversion)

Transforms the typed `DiveLog` before it reaches the converter. Use this phase for operations that require typed field access, such as interpolation or sample filtering.

```kotlin
interface DiveLogPlugin : Plugin {
    fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog

    // Returns a configured instance of this plugin, or null to exclude it from the pipeline.
    // Default implementation handles BooleanParameter("enabled"): returns null when unchecked.
    // Override for richer configuration (e.g. StringParameter with multiple choices).
    fun configure(config: Map<String, Any>): DiveLogPlugin?
}
```

Plugins in this phase receive and return a full `DiveLog`. They run in list order, each receiving the output of the previous one.

| Plugin                        | Description                                                       |
|-------------------------------|-------------------------------------------------------------------|
| `InterpolationPlugin`         | Resamples dive samples to 1-second intervals                      |
| `EnforcePressureUnitPlugin`   | Forces tank pressure columns to PSI or BAR regardless of source   |

---

## Phase 2 — `OutputPlugin` (post-conversion)

Appends columns to the `TelemetryOutput` after the core converter runs. Use this phase for computed display columns that don't need to modify the domain model.

```kotlin
interface OutputPlugin : Plugin {
    fun additionalHeaders(metadata: DiveMetadata): List<String>
    fun Raise<PluginError>.computeRows(log: DiveLog): List<Map<String, String>>
}
```

Each output plugin declares which column headers it adds and computes a value map for every sample. The pipeline merges columns from all output plugins into the base output in list order.

`computeRows()` **must** return exactly `log.samples.size` maps. The pipeline raises a `PluginError.ExecutionError` on mismatch.

| Plugin               | Columns | Description                                                              |
|----------------------|---------|--------------------------------------------------------------------------|
| `TechnicalOCPlugin`  | +16     | NDL state machine (White/Yellow/Red), deco tracking, clear, NDL snapshot |
| `TechnicalCCRPlugin` | +8      | Per-sensor PPO2 with calibration, excessive PO2 detection, diluent PPO2  |
| `SafetyStopPlugin`   | +1      | Safety stop countdown timer (3:00 or 5:00 depending on deco history)     |

With all output plugins enabled: **24 + 16 + 8 + 1 = 49 columns**.

---

## Plugin base interface

```kotlin
interface Plugin {
    val id: String                            // Stable unique identifier (e.g. "core.interpolation")
    val name: String                          // Human-readable label for UI and logs
    val description: String                   // Shown in UI tooltips and CLI --help text
    val parameters: List<PluginParameter<*>>  // Configurable options; UI generates controls from these
}
```

### PluginParameter types

| Type               | UI control   | CLI mapping                                 |
|--------------------|--------------|---------------------------------------------|
| `BooleanParameter` | Checkbox     | Flag (e.g. `--interpolate`)                 |
| `IntParameter`     | Number input | Option with value                           |
| `StringParameter`  | Dropdown     | `.choice()` option (e.g. `--pressure-unit`) |

`StringParameter` requires an `options: List<String>` of allowed values and a `defaultValue`. The UI renders a dropdown; the CLI uses Clikt's `.choice()`. Plugins with a `StringParameter` must override `configure()` to return a configured instance (or `null` for the no-op default).

---

## Error handling

Plugin errors surface as `PluginError`, which implements `PipelineError`:

```kotlin
sealed interface PluginError : PipelineError {
    data class ExecutionError(override val message: String) : PluginError
}
```

Call `raise(PluginError.ExecutionError("reason"))` to signal failure. The pipeline propagates it as `Either.Left<PipelineError>` to the caller. See [error-handling.md](error-handling.md) for the full hierarchy.

---

## Example — `EnforceBarPlugin`

A complete `DiveLogPlugin` that converts tank pressure from PSI to BAR regardless of what the source computer recorded. This illustrates the two key responsibilities of a pre-conversion plugin: modifying `DiveMetadata` so the converter emits the right column headers, and transforming the sample values to match.

Tank pressure is stored as a `String` in `DiveSample` because Shearwater emits non-numeric sentinels like `"AI is off"` or `"N/A"`. The helper returns the original string unchanged when no numeric value is present.

```kotlin
object EnforceBarPlugin : DiveLogPlugin {
    override val id          = "example.enforce-bar"
    override val name        = "Enforce BAR"
    override val description = "Converts tank pressure from PSI to BAR regardless of the source unit."

    override val parameters: List<PluginParameter<*>> = listOf(
        BooleanParameter(
            key          = "enabled",
            name         = "Enforce BAR",
            description  = "Convert tank pressure columns to BAR.",
            defaultValue = false,
        )
    )

    private const val PSI_TO_BAR = 0.0689476

    override fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
        // No-op if the source is already in BAR.
        if (diveLog.metadata.pressureUnit == PressureUnit.BAR) {
            return diveLog
        } else {
            val convertedSamples = diveLog.samples.map { sample ->
                sample.copy(
                    tankPressure1 = psiToBar(sample.tankPressure1),
                    tankPressure2 = psiToBar(sample.tankPressure2),
                    tankPressure3 = psiToBar(sample.tankPressure3),
                    tankPressure4 = psiToBar(sample.tankPressure4),
                )
            }

            return diveLog.copy(
                metadata = diveLog.metadata.copy(pressureUnit = PressureUnit.BAR),
                samples = convertedSamples,
            )
        }
    }

    // Non-numeric sentinels (e.g. "AI is off", "N/A") pass through unchanged.
    private fun psiToBar(value: String): String {
        val psi = value.toDoubleOrNull() ?: return value
        return formatTwoDecimals(psi * PSI_TO_BAR)
    }
}
```

Key points:
- Modifying `metadata.pressureUnit` changes the column header from `Tank N pressure (psi)` to `Tank N pressure (bar)` — no converter code needs to change.
- `formatTwoDecimals` is the shared formatting utility that produces correct output on all KMP targets (plain `Double.toString()` is not safe on Kotlin/Native).
- The early return when `pressureUnit == BAR` makes the plugin safe to run unconditionally — Garmin logs are always metric/BAR and will pass through untouched.

To register this plugin in the desktop app and CLI, see [Adding plugins to the UI and CLI](adding-plugins-to-ui-cli.md).

---

## Using plugins with the library

```kotlin
transformDiveLog(
    source = source,
    sink = sink,
    computerType = ComputerType.SHEARWATER,
    plugins = listOf(InterpolationPlugin),
    outputPlugins = listOf(TechnicalOCPlugin, TechnicalCCRPlugin, SafetyStopPlugin),
).fold(
    ifLeft  = { error -> println("Failed: ${error.message}") },
    ifRight = { println("Success") },
)
```
