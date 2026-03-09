# Adding Plugins to the UI and CLI

After implementing a plugin, register it in the desktop app and CLI. This guide uses `EnforceBarPlugin` from
the [plugin system example](plugin-system.md#example--enforcebarplugin) to illustrate each step. The same pattern applies to any plugin —
see [creating-plugins.md](creating-plugins.md) for implementation guides.

---

## Desktop app (`App.kt`)

The UI builds plugin controls dynamically from each plugin's `parameters` list — no UI code is required. You only need to add the plugin to
the appropriate list.

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

The UI generates controls automatically from the plugin's `parameters` list — it is the **single source of truth** for all configuration:

| Parameter type     | UI control   |
|--------------------|--------------|
| `BooleanParameter` | Checkbox     |
| `IntParameter`     | Number input |
| `StringParameter`  | Dropdown     |

On/off plugins declare a `BooleanParameter(key = "enabled", ...)` which renders as a checkbox. Multi-choice plugins use a `StringParameter` which renders as a dropdown — the `defaultValue` serves as the "disabled/no-op" state. The parameter's `name` is used as the control label and `description` as the info tooltip.

---

## CLI (`ConvertCommand.kt`)

The CLI uses [Clikt](https://ajalt.github.io/clikt/) with one explicit flag per plugin. Each flag maps to a config entry that is passed to `configure()`, the same path the UI uses.

In `cli/src/commonMain/kotlin/io/onema/divetelemetry/cli/ConvertCommand.kt`:

### 1. Declare the flag

```kotlin
private val enforceBar: Boolean by option(
    "-b", "--enforce-bar",
    help = EnforceBarPlugin.description,   // reuse the plugin's own description string
).flag(default = false)
```

### 2. Add to the plugin list via `configure()`

Build a config map from the flag value and call `configure()`. This ensures the CLI and UI go through the same code path:

```kotlin
// Phase 1:
val pluginConfigs = mapOf(
    InterpolationPlugin.id to mapOf("enabled" to interpolate),
    EnforceBarPlugin.id to mapOf("enabled" to enforceBar),   // ← add here
)
val configuredPlugins = listOf(InterpolationPlugin, EnforceBarPlugin)
    .mapNotNull { it.configure(pluginConfigs[it.id] ?: emptyMap()) }

// Phase 2:
val outputPluginConfigs = mapOf(
    TechnicalOCPlugin.id to mapOf("enabled" to technicalOc),
    TechnicalCCRPlugin.id to mapOf("enabled" to technicalCcr),
    SafetyStopPlugin.id to mapOf("enabled" to safetyStop),
    YourNewOutputPlugin.id to mapOf("enabled" to yourFlag),  // ← add here
)
val configuredOutputPlugins = listOf(TechnicalOCPlugin, TechnicalCCRPlugin, SafetyStopPlugin, YourNewOutputPlugin)
    .mapNotNull { it.configure(outputPluginConfigs[it.id] ?: emptyMap()) }
```

Always source the `help` text from the plugin's own `description` property so the UI tooltip and the CLI help stay in sync.

---

## Plugins with `StringParameter` (dropdown / `.choice()`)

A `StringParameter` exposes a fixed set of string choices. The UI renders it as a dropdown — no `BooleanParameter("enabled")` is needed
because the `defaultValue` acts as the "disabled" state. The CLI maps it to a `.choice()` option. Use it when the plugin has more than two
meaningful states — for example, a unit selector where "default", "psi", and "bar" are all valid distinct choices (a `BooleanParameter` can
only express on/off).

### Defining a `StringParameter`

```kotlin
override val parameters: List<PluginParameter<*>> = listOf(
    StringParameter(
        key = "unit",
        name = "Pressure Unit",         // label shown in UI and CLI help
        description = description,             // tooltip text
        defaultValue = "default",               // selected when no config is provided
        options = listOf("default", "psi", "bar"),
    )
)
```

Because a `StringParameter` can represent a no-op choice (here `"default"`), the plugin must override `configure()` to return `null` for
that value — which excludes it from the pipeline entirely — and a configured instance for the active choices:

```kotlin
override fun configure(config: Map<String, Any>): DiveLogPlugin? = when (config["unit"] as? String) {
    "psi" -> Configured(PressureUnit.PSI)
    "bar" -> Configured(PressureUnit.BAR)
    else -> null   // "default" or missing — excluded from pipeline
}
```

See [creating-plugins.md](creating-plugins.md#adding-configurable-parameters) for the full `object` + inner `Configured` class pattern.

---

### Registering in the UI

No extra UI code is needed — the dropdown is generated automatically from `options` and no separate checkbox is shown. Add the plugin to
`availablePlugins` exactly as with any other plugin (see [Desktop app](#desktop-app-appkt) above).

### Registering in the CLI

For plugins that use `StringParameter` and override `configure()`, declare a nullable option with `.choice()` and add the config entry to the plugin config map:

### 1. Declare the option

```kotlin
private val pressureUnit: String? by option(
    "-p", "--pressure-unit",
    help = EnforcePressureUnitPlugin.description,
).choice("default", "psi", "bar")
```

### 2. Add to the plugin config map

```kotlin
val pluginConfigs = mapOf(
    InterpolationPlugin.id to mapOf("enabled" to interpolate),
    EnforcePressureUnitPlugin.id to mapOf("unit" to (pressureUnit ?: "default")),
)
val configuredPlugins = listOf(InterpolationPlugin, EnforcePressureUnitPlugin)
    .mapNotNull { it.configure(pluginConfigs[it.id] ?: emptyMap()) }
```

`configure()` returns `null` when the value is `"default"` (or when the flag is omitted), so the plugin is simply excluded from the
pipeline.
