# Dive Telemetry

A Kotlin Multiplatform library, desktop app, and CLI tool that converts
[Shearwater](https://www.shearwater.com/) and [Garmin](https://www.garmin.com/) dive computer
exports into the CSV format used by [Telemetry ](https://telemetry.com/) for video overlays.

```
Source → parse → [DiveLogPlugin chain] → convert (24 cols) → [OutputPlugin chain] → write → Sink
```

The core converter produces **24 columns**. Output plugins add additional columns on demand —
up to **49 columns** when all three built-in output plugins are enabled.


![Dive Telemetry](img/dive-telemetry-ui.png)]

## Supported formats

| Format         | Input               | Parser                    |
|----------------|---------------------|---------------------------|
| Shearwater CSV | `.csv` (29 columns) | `ShearwaterDiveLogParser` |
| Garmin FIT     | `.fit` (binary)     | `GarminDiveLogParser`     |
| DAN DL7        | `.zxu` (text)       | `Dl7DiveLogParser`        |

## Built-in plugins

| Plugin                      | Phase  | Columns | Description                                          |
|-----------------------------|--------|---------|------------------------------------------------------|
| `InterpolationPlugin`       | Pre    | —       | Resamples dive samples to 1-second intervals         |
| `EnforcePressureUnitPlugin` | Pre    | —       | Forces tank pressure to PSI or BAR                   |
| `TechnicalOCPlugin`         | Post   | +16     | NDL state machine, deco tracking, clear, NDL snapshot |
| `TechnicalCCRPlugin`        | Post   | +8      | Per-sensor PPO2, calibration, diluent PPO2           |
| `SafetyStopPlugin`          | Post   | +1      | Safety stop countdown timer                          |

## Quick start

```bash
# CLI
dive-telemetry --format shearwater dive-log.csv
dive-telemetry --format garmin --technical-oc --technical-ccr --safety-stop dive-log.fit

# Desktop app
./gradlew :app:run
```

## Project structure

Three Gradle modules:

- **`:lib`** — Kotlin Multiplatform library (JVM, macosArm64, macosX64, linuxX64, mingwX64)
- **`:app`** — Compose Desktop GUI
- **`:cli`** — Kotlin/Native CLI tool (no JVM required at runtime)

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| Kotlin Multiplatform | 2.3.10 | Cross-platform compilation |
| Compose Multiplatform | 1.10.1 | Desktop GUI |
| Arrow Core | 2.2.1 | Typed error handling (`Raise`, `Either`) |
| Okio | 3.9.1 | Multiplatform buffered I/O |
| Clikt | 5.0.3 | CLI argument parsing |
