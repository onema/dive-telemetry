# Dive Telemetry

> NOTE: This project was inspired and to an extent ported over from the work done by [Eric Stott](https://www.youtube.com/@EricStott)

A Kotlin Multiplatform project that converts [Shearwater](https://www.shearwater.com/)
and [Garmin](https://www.garmin.com/) dive computer exports into the CSV format that can be used
by [Telemetry ](https://telemetry.com/) for videos.

## Purpose

This project was created to convert my dive computer data from various sources, like Shearwater and Garmin, into a universal CSV format. I
needed a standardized format to use my dive data with Telemetry Overlay for creating detailed video overlays.
The CSV files I generate use specific header names that enable automatic unit conversions and ensure gauge compatibility within Telemetry
Overlay. While my tool provides the necessary data foundation, you'll still need to create and configure your own "patterns" and gauges in
the
Telemetry Overlay application. I've designed the output to be compatible with a wide range of Telemetry Overlay patterns, including those
developed by Eric Stott, and the ones I've developed myself.

The project includes:

- A Compose Desktop GUI for easy conversion.
- A native command-line (CLI) tool for automated workflows.
- A multiplatform library with a two-phase plugin system, which I designed to allow for extensible dive log transformations and the addition
  of custom output columns.

## How it works

The pipeline reads a dive computer export, parses it into a typed [`DiveLog`](docs/parsing-and-domain-model.md), runs it through any
configured plugins,
converts it to Telemetry 's CSV format, appends columns from output plugins, and writes the output with UTF-8 BOM
and CRLF line endings.

```
Source -> parse -> [DiveLogPlugin chain] -> convert (24 cols) -> [OutputPlugin chain] -> write -> Sink
```

### Design Philosophy: The Two-Phase Plugin System

The plugin system is intentionally split into two distinct phases to create a clear and robust data processing workflow. While both plugin
types operate on the same `DiveLog` data, they serve fundamentally different purposes:

1. **Phase 1: `DiveLogPlugin` (Pre-conversion) - Modifying the Source of Truth**
   These plugins are for cleaning, correcting, or transforming the fundamental dive data itself. They can add, remove, or change
   `DiveSample`s in a sequential chain, where the output of one plugin becomes the input for the next. This phase is about preparing the
   final, authoritative version of the dive log *before* any output is generated.
    * **Analogy:** Preparing raw ingredients before baking a cake (e.g., sifting flour, filtering water).
    * **Example:** The `InterpolationPlugin` fills in missing data points, creating a new, denser "source of truth" that all subsequent
      steps will see.

2. **Phase 2: `OutputPlugin` (Post-conversion) - Creating New Views of the Data**
   These plugins are for computing and adding new columns to the final CSV output *without* changing the underlying `DiveLog`. They run in
   parallel, all looking at the same final, cleaned-up `DiveLog` produced by Phase 1. This ensures that all output calculations are based on
   a consistent and stable dataset.
    * **Analogy:** Decorating the finished cake (e.g., adding frosting, writing a message).
    * **Example:** The `ElapsedMinutesPlugin` adds a new column by calculating a value from the `DiveLog`'s `timeSeconds` field, but it
      doesn't alter the `DiveLog` itself.

This two-phase design guarantees that all display-oriented calculations in the second phase operate on a predictable and finalized dataset,
preventing complex and unpredictable interactions between plugins.

### Supported formats

| Format         | Input               | Parser                    |
|----------------|---------------------|---------------------------|
| Shearwater CSV | `.csv` (29 columns) | `ShearwaterDiveLogParser` |
| Garmin FIT     | `.fit` (binary)     | `GarminDiveLogParser`     |
| DAN DL7        | `.zxu` (binary)     | `Dl7DiveLogParser`        |

### Core transformations (always applied)

| Transform                      | Example                                                                        |
|--------------------------------|--------------------------------------------------------------------------------|
| Strip metadata rows            | Shearwater rows 1-2 removed                                                    |
| Rename headers                 | `Depth` -> `Actual Depth (ft)`, `Time (sec)` -> `Time (s)`                     |
| Negate depth values            | `6.4` -> `-6.4` (0 stays 0)                                                    |
| Fractions to integer percent   | `0.18` -> `18`                                                                 |
| Booleans uppercased            | `True` -> `TRUE`, `False` -> `FALSE`                                           |
| Clean floating-point artifacts | `0.9899999` -> `0.99`                                                          |
| Compute Seconds column         | Time modulo 60                                                                 |
| Display Minutes / Seconds      | `"5"` / `"03"` (zero-padded)                                                   |
| Max Depth tracking             | Running maximum depth reached during the dive                                  |
| Wall clock time                | `Actual Time (text)` + `AM/PM Indicator (text)` from dive start time + elapsed |
| Imperial / metric awareness    | Depth in ft or m, temperature in F or C from metadata                          |
| Gas mixture label              | "Air", "Nx32", or blank for trimix                                             |
| UTF-8 BOM + CRLF line endings  | Output format requirement                                                      |

## Project structure

Three Gradle modules:

- **`:lib`** -- Kotlin Multiplatform library (JVM, macosArm64, macosX64, linuxX64, mingwX64). Parsing, conversion,
  plugin system, and I/O.
- **`:app`** -- JVM Compose Desktop application. GUI for file selection, plugin configuration, and conversion.
- **`:cli`** -- Kotlin/Native CLI tool using [Clikt](https://ajalt.github.io/clikt/). No JVM required at runtime.

```
lib/src/commonMain/kotlin/io/onema/divetelemetry/
├── domain/
│   └── DiveData.kt                # Typed ADTs: DiveMetadata, DiveSample, DiveLog, TelemetryOutput
├── error/
│   └── PipelineError.kt           # Error hierarchy: PipelineError, ParseError, WriteError
├── plugins/
│   ├── DiveLogPlugin.kt              # Plugin base interface, PluginError, PluginParameter hierarchy
│   ├── OutputPlugin.kt               # OutputPlugin interface for post-conversion column additions
│   ├── InterpolationPlugin.kt        # 1-second interval interpolation (DiveLogPlugin)
│   ├── EnforcePressureUnitPlugin.kt  # Force tank pressure to PSI or BAR (DiveLogPlugin)
│   ├── TechnicalOCPlugin.kt          # NDL, deco tracking, deco clear columns (OutputPlugin)
│   ├── TechnicalCCRPlugin.kt         # Per-sensor PPO2, diluent PPO2 columns (OutputPlugin)
│   └── SafetyStopPlugin.kt           # Safety stop timer column (OutputPlugin)
├── service/
│   ├── DiveLogParser.kt           # Sealed parser interface + factory
│   ├── ShearwaterDiveLogParser.kt # Shearwater CSV -> DiveLog
│   ├── GarminDiveLogParser.kt     # Garmin FIT binary -> DiveLog
│   ├── TelemetryConverter.kt      # Sealed converter interface + factory
│   ├── UnifiedTelemetryConverter.kt # DiveLog -> TelemetryOutput (24 core columns)
│   ├── CsvWriter.kt               # TelemetryOutput -> CSV (BOM + CRLF)
│   └── Pipeline.kt                # transformDiveLog() orchestration + two-phase plugin chain
└── util/
    ├── CsvTokenizer.kt            # RFC 4180 CSV split/join
    ├── FitDecoder.kt              # Pure Kotlin FIT binary protocol decoder
    ├── Formatting.kt              # Shared formatting functions (Kotlin/Native safe)
    └── ValueTransforms.kt         # Wall clock time parsing and formatting

app/src/main/kotlin/io/onema/divetelemetry/app/
├── App.kt    # Compose Desktop UI with dynamic plugin controls
└── Main.kt   # Application entry point

cli/src/commonMain/kotlin/io/onema/divetelemetry/cli/
├── ConvertCommand.kt  # Clikt command with plugin options
└── Main.kt            # Entry point
```

## Plugin system

- [Parsing and the domain model](docs/parsing-and-domain-model.md) — `DiveLog` ADT reference, parser interface, and `DiveComputerFormat`

See **[docs/plugin-system.md](docs/plugin-system.md)** for the full plugin system reference, including interface definitions, built-in
plugin table, and library usage examples.

- [Creating plugins](docs/creating-plugins.md) — `DiveLogPlugin` and `OutputPlugin` implementation guides with sample code
- [Adding plugins to the UI and CLI](docs/adding-plugins-to-ui-cli.md) — how to register a new plugin in `App.kt` and `ConvertCommand.kt`

## Desktop app

```bash
./gradlew :app:run
```

The app provides:

- Source type dropdown (Shearwater CSV or Garmin FIT)
- File picker filtered by the selected format's extension
- Dynamic plugin controls generated from each plugin's parameter descriptors
- Convert button that runs the full two-phase pipeline with configured plugins

## CLI tool

See **[docs/cli-usage.md](docs/cli-usage.md)** for usage, options, and build commands.

```bash
# Quick start
dive-telemetry --format shearwater dive-log.csv
dive-telemetry --format shearwater --technical-oc --technical-ccr --safety-stop dive-log.csv
```

## Error handling

See **[docs/error-handling.md](docs/error-handling.md)** for the full `PipelineError` hierarchy and Arrow-kt `Raise` usage patterns.

## Adding a new dive computer format

See **[docs/adding-dive-computers.md](docs/adding-dive-computers.md)** for the step-by-step guide, including a complete parser skeleton for
the DL7/ZXU format used by Divers Alert Network.

## Gap analysis: VBA macro parity

See **[docs/gap-analysis.md](docs/gap-analysis.md)** for a comparison with the original `Format Shearwater.xlsm` VBA macros.

## Building

### Prerequisites

- macOS with Xcode command-line tools
- JDK 17+

### Desktop app

```bash
./gradlew :app:run                # Run during development
./gradlew :app:packageDmg         # macOS installer
./gradlew :app:packageDeb         # Linux installer
./gradlew :app:packageMsi         # Windows installer
```

### Tests

```bash
./gradlew :lib:jvmTest :lib:macosArm64Test   # JVM + Apple Silicon
./gradlew :lib:allTests                       # All targets
```

Extensive tests across most files covering parsing, conversion, interpolation, CSV tokenization, FIT decoding, plugin behavior
(DiveLogPlugin and OutputPlugin), pipeline integration, and end-to-end byte-for-byte output validation.

## Dependencies

| Library                                                                   | Version | Purpose                                  |
|---------------------------------------------------------------------------|---------|------------------------------------------|
| [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)    | 2.3.10  | Cross-platform compilation               |
| [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) | 1.10.1  | Desktop GUI framework                    |
| [Arrow Core](https://arrow-kt.io/)                                        | 2.2.1   | Typed error handling (`Raise`, `Either`) |
| [Okio](https://square.github.io/okio/)                                    | 3.9.1   | Multiplatform buffered I/O               |
| [Clikt](https://ajalt.github.io/clikt/)                                   | 5.0.3   | CLI argument parsing                     |
| [kotlin-test](https://kotlinlang.org/api/latest/kotlin.test/)             | bundled | Test framework                           |

## Native targets

`:lib` and `:cli` target: `jvm`, `macosArm64`, `macosX64`, `linuxX64`, `mingwX64`.
Linux and Windows binaries are cross-compiled from macOS -- not CI-validated.
