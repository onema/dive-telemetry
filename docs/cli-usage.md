# CLI Usage

The `cli` module produces a self-contained native binary — no JVM required at runtime.

## Basic usage

```bash
# Convert a Shearwater CSV (24 core columns)
dive-telemetry --format shearwater dive-log.csv

# Convert a Garmin FIT file with a custom output path
dive-telemetry --format garmin --output my-dive.csv ACTIVITY.fit

# Enable interpolation for smoother video 
dive-telemetry --format shearwater --interpolate dive-log.csv

# Enable all output plugins (49 columns)
dive-telemetry --format shearwater --technical-oc --technical-ccr --safety-stop dive-log.csv
```

The output file defaults to `<input-basename>-telemetry.csv` in the same directory as the input.

## Options

| Option                  | Description                                               |
|-------------------------|-----------------------------------------------------------|
| `INPUT`                 | Path to the input dive log file (required)                |
| `-f`, `--format`        | Dive computer format: `shearwater` or `garmin` (required) |
| `-o`, `--output`        | Output file path (default: `<input>-telemetry.csv`)       |
| `-i`, `--interpolate`   | Resample to 1-second intervals                            |
| `-t`, `--technical-oc`  | Enable Technical Open Circuit columns (NDL, deco, clear)  |
| `-c`, `--technical-ccr` | Enable Technical CCR columns (PPO2, dilPO2)               |
| `-s`, `--safety-stop`   | Enable Safety Stop Timer column                           |
| `-p`, `--pressure-unit` | Tank pressure unit: `default`, `psi`, or `bar`            |
| `-h`, `--help`          | Show help message and exit                                |

## Building native binaries

| Platform              | Command                             | Output                                                |
|-----------------------|-------------------------------------|-------------------------------------------------------|
| macOS (Apple Silicon) | `./gradlew :cli:macosArm64Binaries` | `cli/build/bin/macosArm64/releaseExecutable/cli.kexe` |
| macOS (Intel)         | `./gradlew :cli:macosX64Binaries`   | `cli/build/bin/macosX64/releaseExecutable/cli.kexe`   |
| Linux (x86_64)        | `./gradlew :cli:linuxX64Binaries`   | `cli/build/bin/linuxX64/releaseExecutable/cli.kexe`   |
| Windows (x86_64)      | `./gradlew :cli:mingwX64Binaries`   | `cli/build/bin/mingwX64/releaseExecutable/cli.exe`    |

Linux and Windows binaries are cross-compiled from macOS and are not CI-validated.

## Running on JVM (without a native binary)

```bash
./gradlew :cli:jvmRun --args="--format shearwater dive-log.csv"
```
