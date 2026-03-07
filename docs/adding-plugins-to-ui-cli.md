# Adding Plugins to the UI and CLI

After implementing a plugin, register it in the desktop app and CLI. This guide uses `EnforceBarPlugin` from the [plugin system example](plugin-system.md#example--enforcebarplugin) to illustrate each step. The same pattern applies to any plugin — see [creating-plugins.md](creating-plugins.md) for implementation guides.

---

## Desktop app (`App.kt`)

The UI builds plugin controls dynamically from each plugin's `parameters` list — no UI code is required. You only need to add the plugin to the appropriate list.

In `app/src/main/kotlin/io/onema/divetelemetry/app/App.kt`:

```kotlin
// Phase 1 — DiveLogPlugin:
val availablePlugins: List<DiveLogPlugin> = listOf(
    InterpolationPlugin,
    EnforceBarPlugin,       // ← add here
)

// Phase 2 — OutputPlugin:
val availableOutputPlugins: List<OutputPlugin> = listOf(
    TechnicalOCPlugin,
    TechnicalCCRPlugin,
    SafetyStopPlugin,
    YourNewOutputPlugin,    // ← add here
)
```

The UI generates controls automatically from the plugin's `parameters` list:

| Parameter type     | UI control   |
|--------------------|--------------|
| `BooleanParameter` | Checkbox     |
| `IntParameter`     | Number input |
| `StringParameter`  | Dropdown     |

The plugin's `name` is used as the control label and `description` as the info tooltip.

---

## CLI (`ConvertCommand.kt`)

The CLI uses [Clikt](https://ajalt.github.io/clikt/) with one explicit flag per plugin.

In `cli/src/commonMain/kotlin/io/onema/divetelemetry/cli/ConvertCommand.kt`:

### 1. Declare the flag

```kotlin
private val enforceBar: Boolean by option(
    "-b", "--enforce-bar",
    help = EnforceBarPlugin.description,   // reuse the plugin's own description string
).flag(default = false)
```

### 2. Add to the plugin list in `run()`

```kotlin
// Phase 1:
val configuredPlugins: List<DiveLogPlugin> = buildList {
    if (interpolate) add(InterpolationPlugin)
    if (enforceBar)  add(EnforceBarPlugin)     // ← add here
}

// Phase 2:
val configuredOutputPlugins: List<OutputPlugin> = buildList {
    if (technicalOc)  add(TechnicalOCPlugin)
    if (technicalCcr) add(TechnicalCCRPlugin)
    if (safetyStop)   add(SafetyStopPlugin)
    if (yourFlag)     add(YourNewOutputPlugin) // ← add here
}
```

Always source the `help` text from the plugin's own `description` property so the UI tooltip and the CLI help stay in sync.

---

## Plugins with `StringParameter` (dropdown / `.choice()`)

For plugins that use `StringParameter` and override `configure()` (such as `EnforcePressureUnitPlugin`), the CLI pattern is slightly different: declare a nullable option with `.choice()` and delegate to `configure()` in `run()`.

### 1. Declare the option

```kotlin
private val pressureUnit: String? by option(
    "-p", "--pressure-unit",
    help = EnforcePressureUnitPlugin.description,
).choice("default", "psi", "bar")
```

### 2. Add to the plugin list via `configure()`

```kotlin
val configuredPlugins: List<DiveLogPlugin> = buildList {
    if (interpolate) add(InterpolationPlugin)
    EnforcePressureUnitPlugin.configure(mapOf("unit" to (pressureUnit ?: "default")))?.let { add(it) }
}
```

`configure()` returns `null` when the value is `"default"` (or when the flag is omitted), so the plugin is simply excluded from the pipeline.
