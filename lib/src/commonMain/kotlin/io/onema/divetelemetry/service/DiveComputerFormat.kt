package io.onema.divetelemetry.service

/**
 * Descriptor for a supported dive computer export format.
 *
 * Implementing this interface is the only step required to support a new format:
 * provide the metadata the UI and CLI need ([id], [name], [extensions]) and a
 * factory for the format's parser ([createParser]).  Add the object to the list
 * passed to [transformDiveLog] — or to [defaultComputerFormats] to make it
 * available everywhere automatically.
 *
 * @property id   Stable identifier used as the CLI `--format` value (e.g. `"shearwater"`).
 * @property name Human-readable label shown in the UI source-type dropdown.
 * @property extensions File extensions accepted by the file picker (e.g. `[".csv"]`).
 */
interface DiveComputerFormat {
    val id: String
    val name: String
    val extensions: List<String>

    /** Returns a fresh parser instance for this format. */
    fun createParser(): DiveLogParser
}

object ShearwaterFormat : DiveComputerFormat {
    override val id = "shearwater"
    override val name = "Shearwater CSV"
    override val extensions = listOf(".csv")

    override fun createParser() = ShearwaterDiveLogParser()
}

object GarminFormat : DiveComputerFormat {
    override val id = "garmin"
    override val name = "Garmin FIT"
    override val extensions = listOf(".fit")

    override fun createParser() = GarminDiveLogParser()
}

object Dl7Format : DiveComputerFormat {
    override val id: String = "DL7"
    override val name: String = "DAN-DL7 ZXU"
    override val extensions: List<String> = listOf(".zxu")

    override fun createParser(): DiveLogParser = Dl7DiveLogParser()
}

/**
 * All formats built into the library, in display order.
 * Pass a subset — or a superset with custom formats — to [transformDiveLog] as needed.
 */
val defaultComputerFormats: List<DiveComputerFormat> = listOf(
    ShearwaterFormat,
    GarminFormat,
    Dl7Format,
)
