# Adding a New Dive Computer Format

The pipeline is designed to accept new source formats without changes to the converter or plugins. All format-specific logic lives in the parser.

## Steps

1. **Implement `DiveLogParser`** for the new format. The parser reads raw bytes and produces a typed `DiveLog`. See [Parsing and the domain model](parsing-and-domain-model.md) for the full `DiveLog` type contract and parser interface. See the [DL7 example](#example-dl7zxu-parser) below.

2. **Add a `DiveComputerFormat` object** in `service/DiveComputerFormat.kt`:
   ```kotlin
   object Dl7Format : DiveComputerFormat {
       override val id         = "dl7"
       override val name       = "DL7/ZXU"
       override val extensions = listOf(".zxu")
       override fun createParser() = Dl7DiveLogParser()
   }
   ```

3. **Add to `defaultComputerFormats`** in the same file:
   ```kotlin
   val defaultComputerFormats: List<DiveComputerFormat> = listOf(
       ShearwaterFormat,
       GarminFormat,
       Dl7Format,   // ← add here
   )
   ```

That's it. The app source-type dropdown and the CLI `--format` option are both driven by `defaultComputerFormats` automatically — no other files need to change.

The `UnifiedTelemetryConverter` handles all formats based on typed `DiveMetadata` — no converter changes are needed.

---

## Example: DL7/ZXU parser

The DL7 format (`.zxu`) is produced by [Divers Alert Network](https://www.diversalertnetwork.org/) export tools and by Shearwater computers
when exporting via DAN. It is an HL7-inspired pipe-delimited text format.

### File structure

```
FSH|^~<>{}|SWRa02^SWC2.12.6^C|ZXU|20260306220328|
ZRH|^~<>{}|Perdix 2|A3B2A90E|MFWG|ThM|C|bar|L|
ZAR{}
ZDH|1|423|I|Q10S|20260131160820||||||
ZDP{
|0|0|||||0|-17|||||0|||
|0.08333334|2.77439|||||0|17|||||12|||
|0.1666667|3.47561|||||0|17|||||12|||
...
ZDP}
ZDT|1|423|32.04268|20260131170425|||
```

| Segment       | Description                                                                      |
|---------------|----------------------------------------------------------------------------------|
| `FSH`         | File header — software version, format identifier (`ZXU`), export timestamp      |
| `ZRH`         | Record header — device name, serial, gas mix, temp unit (`C`/`F`), pressure unit |
| `ZAR`         | Archive record (no dive data; skip)                                              |
| `ZDH`         | Dive header — dive index, sample count, start time (`yyyyMMddHHmmss`)            |
| `ZDP{`…`ZDP}` | Dive data points — one pipe-delimited row per sample                             |
| `ZDT`         | Dive trailer — dive index, sample count, max depth, end time                     |

### ZDP row fields

Each data row starts with `|` and contains 16 pipe-separated fields:

| Index | Content                 | Example    |
|-------|-------------------------|------------|
| 1     | Time (minutes, decimal) | `2.5`      |
| 2     | Depth (meters)          | `15.45732` |
| 3–7   | Reserved / empty        |            |
| 8     | Temperature (°C or °F)  | `16`       |
| 9–15  | Reserved / empty        |            |
| 16    | NDL or deco time        | `13`       |

### Parser skeleton

```kotlin
package io.onema.divetelemetry.service

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import io.onema.divetelemetry.domain.DepthUnit
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata
import io.onema.divetelemetry.domain.DiveSample
import io.onema.divetelemetry.domain.PressureUnit
import io.onema.divetelemetry.domain.TempUnit
import io.onema.divetelemetry.error.ParseError
import okio.BufferedSource

class Dl7DiveLogParser : DiveLogParser {

    override fun Raise<ParseError>.parse(source: BufferedSource): DiveLog {
        val lines = generateSequence { source.readUtf8Line() }.toList()

        val zrhFields = lines.firstOrNull { it.startsWith("ZRH|") }?.split("|")
        val zdhFields = lines.firstOrNull { it.startsWith("ZDH|") }?.split("|")

        // field[6]: temperature unit — "C" or "F"
        val tempUnit = if (zrhFields?.getOrNull(6) == "F") TempUnit.FAHRENHEIT else TempUnit.CELSIUS
        // field[7]: pressure unit — "bar" or "psi"
        val pressureUnit = if (zrhFields?.getOrNull(7)?.lowercase() == "psi") PressureUnit.PSI else PressureUnit.BAR
        // field[5]: start time in "yyyyMMddHHmmss" format
        val startTime = zdhFields?.getOrNull(5)?.let { formatDl7Time(it) }

        val samples = lines
            .dropWhile { it != "ZDP{" }
            .drop(1)
            .takeWhile { it != "ZDP}" }
            .filter { it.startsWith("|") }
            .mapNotNull { parseSample(it) }

        ensure(samples.isNotEmpty()) { ParseError.MissingFitData("dive samples") }

        return DiveLog(
            metadata = DiveMetadata(
                depthUnit = DepthUnit.M,   // DL7 always stores depth in meters
                tempUnit = tempUnit,
                pressureUnit = pressureUnit,
                startTime = startTime,
            ),
            samples = samples,
        )
    }

    private fun parseSample(line: String): DiveSample? {
        val fields = line.split("|")
        // fields[0] is empty because the line starts with "|"
        val timeMins = fields.getOrNull(1)?.toDoubleOrNull() ?: return null
        val depthM = fields.getOrNull(2)?.toDoubleOrNull() ?: return null
        val tempRaw = fields.getOrNull(8)?.toIntOrNull()

        // -17 is a sentinel value indicating a pre-dive surface / no-data row
        if (depthM == 0.0 && tempRaw == -17) return null

        return DiveSample(
            timeSeconds = (timeMins * 60).toLong(),
            depth = depthM,
            avgPpo2 = 0.0,
            fractionO2 = 0.21,
            fractionHe = 0.0,
            waterTemp = tempRaw?.takeIf { it != -17 }?.toDouble(),
            firstStopDepth = 0.0,
            firstStopTime = 0L,
            timeToSurface = 0L,
            currentNdl = null,
            currentCircuitMode = 0,
            currentCcrMode = 0,
            gasSwitchNeeded = false,
            externalPpo2 = false,
        )
    }

    /**
     * Converts "yyyyMMddHHmmss" to "M/d/yyyy h:mm:ss AM/PM" for wall-clock time computation.
     */
    private fun formatDl7Time(raw: String): String? {
        if (raw.length != 14) return null
        val year = raw.substring(0, 4)
        val month = raw.substring(4, 6).trimStart('0').ifEmpty { "0" }
        val day = raw.substring(6, 8).trimStart('0').ifEmpty { "0" }
        val hour = raw.substring(8, 10).toIntOrNull() ?: return null
        val min = raw.substring(10, 12)
        val sec = raw.substring(12, 14)
        val amPm = if (hour >= 12) "PM" else "AM"
        val hour12 = when {
            hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour
        }
        return "$month/$day/$year $hour12:$min:$sec $amPm"
    }
}
```

### Notes

- **Temperature sentinel**: rows with temperature field `-17` are pre-dive surface readings and should be skipped.
- **Depth unit**: DL7 files always store depth in meters regardless of the `ZRH` pressure unit field.
- **Duplicate timestamps**: the format sometimes emits multiple rows with the same timestamp (e.g. during a surface interval). The skeleton
  above skips zero-depth sentinel rows; additional deduplication may be needed depending on the source computer.
- **Gas data**: this skeleton defaults to air (21% O₂, 0% He). Full gas support requires parsing the `ZRH` gas mix field and any per-sample
  gas switch events if the format includes them.
