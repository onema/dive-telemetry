package io.onema.divetelemetry.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import io.onema.divetelemetry.plugins.DiveLogPlugin
import io.onema.divetelemetry.plugins.EnforcePressureUnitPlugin
import io.onema.divetelemetry.plugins.InterpolationPlugin
import io.onema.divetelemetry.plugins.OutputPlugin
import io.onema.divetelemetry.plugins.SafetyStopPlugin
import io.onema.divetelemetry.plugins.TechnicalCCRPlugin
import io.onema.divetelemetry.plugins.TechnicalOCPlugin
import io.onema.divetelemetry.service.DiveComputerFormat
import io.onema.divetelemetry.service.defaultComputerFormats
import io.onema.divetelemetry.service.transformDiveLog
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

class ConvertCommand : CliktCommand(
    name = "dive-telemetry",
) {

    init {
        versionOption(AppVersion.version)
    }

    private val input: String by argument(
        help = "Path to the input dive log file",
    )

    private val output: String? by option(
        "-o", "--output",
        help = "Output file path (defaults to <input>-telemetry.csv)",
    )

    private val format: DiveComputerFormat by option(
        "-f", "--format",
        help = "Dive computer format",
    ).choice(*defaultComputerFormats.map { it.id to it }.toTypedArray()).required()

    private val interpolate: Boolean by option(
        "-i", "--interpolate",
        help = InterpolationPlugin.description,
    ).flag(default = false)

    private val technicalOc: Boolean by option(
        "-t", "--technical-oc",
        help = TechnicalOCPlugin.description,
    ).flag(default = false)

    private val technicalCcr: Boolean by option(
        "-c", "--technical-ccr",
        help = TechnicalCCRPlugin.description,
    ).flag(default = false)

    private val safetyStop: Boolean by option(
        "-s", "--safety-stop",
        help = SafetyStopPlugin.description,
    ).flag(default = false)

    private val pressureUnit: String? by option(
        "-p", "--pressure-unit",
        help = EnforcePressureUnitPlugin.description,
    ).choice("default", "psi", "bar")

    override fun run() {
        val configuredPlugins: List<DiveLogPlugin> = buildList {
            if (interpolate) add(InterpolationPlugin)
            EnforcePressureUnitPlugin.configure(mapOf("unit" to (pressureUnit ?: "default")))?.let { add(it) }
        }

        val configuredOutputPlugins: List<OutputPlugin> = buildList {
            if (technicalOc) add(TechnicalOCPlugin)
            if (technicalCcr) add(TechnicalCCRPlugin)
            if (safetyStop) add(SafetyStopPlugin)
        }

        val inputPath = input.toPath()
        val outputPath = output?.toPath() ?: run {
            val baseName = inputPath.name.substringBeforeLast(".")
            val outputName = "$baseName-telemetry.csv"
            inputPath.parent?.resolve(outputName) ?: outputName.toPath()
        }

        val source = FileSystem.SYSTEM.source(inputPath).buffer()
        val sink = FileSystem.SYSTEM.sink(outputPath).buffer()
        try {
            val result = transformDiveLog(
                source = source,
                sink = sink,
                format = format,
                plugins = configuredPlugins,
                outputPlugins = configuredOutputPlugins,
            )
            result.fold(
                ifLeft = { error ->
                    echo("Error: ${error.message}", err = true)
                    throw ProgramResult(1)
                },
                ifRight = {
                    echo("Converted $inputPath -> $outputPath")
                },
            )
        } finally {
            source.close()
            sink.close()
        }
    }
}
