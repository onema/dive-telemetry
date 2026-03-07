# Parsing and the Domain Model

The parser is the boundary between raw, device-specific input and the typed domain model. All format-specific complexity (Shearwater CSV
column layouts, Garmin FIT binary encoding, DAN DL7 pipe-delimited segments) is absorbed by the parser. Everything downstream (plugins,
converter, writer) operates exclusively on the typed `DiveLog` OR `TelemetryData` and never sees raw bytes.
Is
---

## The `DiveLog` ADT

Three data classes make up the core domain model, defined in `lib/src/commonMain/kotlin/io/onema/divetelemetry/domain/DiveData.kt`.

### `DiveMetadata`

Dive-level metadata extracted from the source file header.

```kotlin
data class DiveMetadata(
    val depthUnit: DepthUnit,
    val tempUnit: TempUnit,
    val pressureUnit: PressureUnit,
    val startTime: String?,
    val extra: Map<String, String> = emptyMap(),
)
```

| Field          | Description                                                                                      |
|----------------|--------------------------------------------------------------------------------------------------|
| `depthUnit`    | `FT` or `M` -- governs all depth values in `DiveSample` and the converter's column header suffix |
| `tempUnit`     | `FAHRENHEIT` or `CELSIUS` -- governs all temperature values                                      |
| `pressureUnit` | `PSI` or `BAR` -- governs all tank pressure values                                               |
| `startTime`    | Raw wall-clock start time from the source (e.g. `"12/7/2025 9:00:00 AM"`), or `null` if absent   |
| `extra`        | Format-specific fields that have no typed equivalent (e.g. firmware version, serial number)      |

### `DiveSample`

A DiveSample represents a single instant in time during the dive: a complete snapshot of every sensor measurement recorded by the dive
computer at that moment (depth, temperature, PPO2, NDL, etc.).

All numeric fields use source units as declared in `DiveMetadata`.

```kotlin
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
```

Key fields:

| Field                        | Notes                                                                                      |
|------------------------------|--------------------------------------------------------------------------------------------|
| `timeSeconds`                | Elapsed time from dive start, in whole seconds                                             |
| `depth`                      | Always **positive** -- negation for display is applied by the converter, not the parser    |
| `fractionO2`/`fractionHe`    | Normalized 0.0--1.0 (e.g. 0.32 for Nitrox 32)                                              |
| `waterTemp`                  | Nullable -- use `null`, not a sentinel, when the value is absent                           |
| `currentNdl`                 | Nullable -- `null` when the computer is not reporting NDL (e.g. during deco)               |
| `tankPressure1` to `4`       | Stored as `String` because Shearwater emits non-numeric sentinels (`"AI is off"`, `"N/A"`) |
| `externalO2Sensor1` to `3Mv` | Millivolt readings from external O2 sensors, also `String` for sentinel handling           |

### `DiveLog`

The top-level type produced by parsing all the data from the source file.

```kotlin
data class DiveLog(
    val metadata: DiveMetadata,
    val samples: List<DiveSample>,
)
```

`samples` is ordered by `timeSeconds`. The list is never empty, parsers may raise `ParseError` if no valid samples are found.

### Invariants

1. **Depth is positive.** The parser stores depth as a positive number. The converter negates it for the output CSV column (`-6.4`). Plugins
   that filter or transform by depth can compare directly against positive thresholds.

2. **Units match metadata.** All numeric values in `DiveSample` use the units declared in `DiveMetadata` (`depthUnit`, `tempUnit`,
   `pressureUnit`). The converter reads these to choose column header suffixes (`(ft)` vs `(m)`) and apply any needed formatting.

3. **Absent values are `null`.** `waterTemp` and `currentNdl` are nullable. Parsers must produce `null` -- not `0`, `-1`, or any other
   sentinel -- when the source data does not contain a value.

4. **`extra` is for leftovers.** Format-specific header fields that have no typed equivalent in `DiveMetadata` go into `extra`. This
   prevents the domain model from growing unbounded while still preserving data.

---

## The `DiveLogParser` interface

Defined in `lib/src/commonMain/kotlin/io/onema/divetelemetry/service/DiveLogParser.kt`:

```kotlin
sealed interface DiveLogParser {
    fun Raise<ParseError>.parse(source: BufferedSource): DiveLog
}
```

- **`Raise<ParseError>` context** -- the parser raises typed errors via Arrow-kt's `Raise` DSL rather than throwing exceptions. Callers
  receive a `ParseError` on the `Left` side of an `Either`.
- **`BufferedSource`** -- Okio's multiplatform byte-stream abstraction. The parser owns how it reads (text lines, binary protocol, etc.) but
  never how the file was opened.
- **Stateless** -- parsers hold no mutable state. Each call to `parse` is independent. Implementations are typically `class` instances
  created by a `DiveComputerFormat` factory.

---

## `ParseError` types

Defined in `lib/src/commonMain/kotlin/io/onema/divetelemetry/error/PipelineError.kt`:

| Type                        | When raised                                                  |
|-----------------------------|--------------------------------------------------------------|
| `ParseError.UnexpectedEof`  | The source ended before a required row or segment was found  |
| `ParseError.InvalidFitFile` | The FIT binary header or data records are malformed          |
| `ParseError.MissingFitData` | A required field (e.g. dive samples, session data) is absent |
| `ParseError.MissingColumns` | Expected CSV columns are not present in the header row       |

All subtypes carry a human-readable `message` and implement `PipelineError`, the top-level error interface for the entire pipeline.

---

## `DiveComputerFormat`

Defined in `lib/src/commonMain/kotlin/io/onema/divetelemetry/service/DiveComputerFormat.kt`. This interface connects a format identifier and
file extensions to its parser:

```kotlin
interface DiveComputerFormat {
    val id: String
    val name: String
    val extensions: List<String>

    fun createParser(): DiveLogParser
}
```

| Property     | Purpose                                                                  |
|--------------|--------------------------------------------------------------------------|
| `id`         | Stable identifier used as the CLI `--format` value (e.g. `"shearwater"`) |
| `name`       | Human-readable label shown in the UI dropdown                            |
| `extensions` | File extensions accepted by the file picker (e.g. `[".csv"]`)            |

### Built-in formats

```kotlin
val defaultComputerFormats: List<DiveComputerFormat> = listOf(
    ShearwaterFormat,   // id = "shearwater",  extensions = [".csv"],  parser = ShearwaterDiveLogParser
    GarminFormat,       // id = "garmin",       extensions = [".fit"],  parser = GarminDiveLogParser
    Dl7Format,          // id = "DL7",          extensions = [".zxu"],  parser = Dl7DiveLogParser
)
```

The desktop app dropdown and the CLI `--format` choice are both driven by this list. To add a new format, implement `DiveComputerFormat` and
add the object to `defaultComputerFormats` -- no other files need to change.

---

## See also

- [Adding a new dive computer format](adding-dive-computers.md) -- step-by-step guide for implementing a new parser
- [Plugin system](plugin-system.md) -- how the `DiveLog` flows through plugins after parsing
- [Error handling](error-handling.md) -- the full `PipelineError` hierarchy and `Raise` usage patterns
