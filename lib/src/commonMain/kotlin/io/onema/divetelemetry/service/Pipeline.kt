package io.onema.divetelemetry.service

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.TelemetryOutput
import io.onema.divetelemetry.domain.TelemetryRow
import io.onema.divetelemetry.error.PipelineError
import io.onema.divetelemetry.plugins.DiveLogPlugin
import io.onema.divetelemetry.plugins.OutputPlugin
import io.onema.divetelemetry.plugins.Plugin
import io.onema.divetelemetry.plugins.PluginError
import okio.BufferedSink
import okio.BufferedSource

/**
 * Entry point for the full two-phase conversion pipeline.
 *
 * @param source Input stream of the dive computer export file.
 * @param sink Output stream to write the resulting CSV to.
 * @param format The source format descriptor; supplies the parser and drives the UI/CLI.
 * @param plugins Pre-conversion [DiveLogPlugin]s applied in order before the converter.
 * @param outputPlugins Post-conversion [OutputPlugin]s applied in order to append columns.
 * @return [Either.Right] on success, [Either.Left] with a [PipelineError] on failure.
 */
fun transformDiveLog(
    source: BufferedSource,
    sink: BufferedSink,
    format: DiveComputerFormat,
    plugins: List<DiveLogPlugin> = emptyList(),
    outputPlugins: List<OutputPlugin> = emptyList(),
): Either<PipelineError, Unit> = either {

    val parser = format.createParser()
    val converter = TelemetryConverter.create()
    val writer = CsvWriter()

    val initialLog = parser.run { parse(source) }

    // These plugins make transformations on the DiveLog data, e.g. adding additional rows for interpolation
    val finalLog: DiveLog = runPluginChain(initialLog, plugins) { acc, plugin -> diveLogPlugins(acc, plugin) }
    val baseOutput: TelemetryOutput = converter.convert(finalLog)

    // These plugins make transformations to the output before is saved to the file
    val enrichedOutput: TelemetryOutput = runPluginChain(baseOutput, outputPlugins) { acc, plugin -> outputPlugins(acc, plugin, finalLog) }

    writer.run { write(enrichedOutput, sink) }
}

/**
 * Folds [plugins] over [initial], applying [execute] for each.
 * Any [PluginError] is re-raised as a [PluginError.ExecutionError], short-circuiting the chain.
 *
 * @param Acc The accumulator type threaded through the chain.
 * @param P The plugin type.
 * @param initial Starting accumulator value.
 * @param plugins Ordered list of plugins to apply.
 * @param execute Extension on [Raise] that applies a single plugin to the accumulator.
 * @return The final accumulated value after all plugins have run.
 */
private fun <Acc, P : Plugin> Raise<PipelineError>.runPluginChain(
    initial: Acc,
    plugins: List<P>,
    execute: Raise<PluginError>.(acc: Acc, plugin: P) -> Acc,
): Acc = plugins.fold(initial) { acc, plugin -> execute(acc, plugin) }

private fun Raise<PluginError>.diveLogPlugins(acc: DiveLog, plugin: DiveLogPlugin): DiveLog {
    return plugin.run { transform(acc) }
}

private fun Raise<PluginError>.outputPlugins(
    acc: TelemetryOutput,
    plugin: OutputPlugin,
    diveLog: DiveLog,
): TelemetryOutput {
    val rows = plugin.run { computeRows(diveLog) }
    if (rows.size != acc.rows.size) {
        raise(PluginError.ExecutionError("[${plugin.name}] produced ${rows.size} rows but expected ${acc.rows.size}"))
    }
    val mergedHeaders = acc.headers + plugin.additionalHeaders(diveLog.metadata)
    val mergedRows = acc.rows.zip(rows) { existingRow, newColumns ->
        TelemetryRow(existingRow.values + newColumns)
    }
    return TelemetryOutput(headers = mergedHeaders, rows = mergedRows)
}
