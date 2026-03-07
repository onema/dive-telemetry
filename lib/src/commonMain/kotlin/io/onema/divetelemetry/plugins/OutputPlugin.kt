package io.onema.divetelemetry.plugins

import arrow.core.raise.Raise
import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.DiveMetadata

/**
 * Post-conversion plugin that appends columns to [TelemetryOutput] after the core converter runs.
 * Runs in phase 2 of the pipeline.
 */
interface OutputPlugin : Plugin {
    /**
     * @param metadata Dive metadata, used to determine column names (e.g. based on depth unit).
     * @return Column headers this plugin adds, in order.
     */
    fun additionalHeaders(metadata: DiveMetadata): List<String>

    /**
     * Computes column values for every sample in the dive log.
     * Must return exactly `log.samples.size` maps keyed by [additionalHeaders].
     *
     * @receiver Raise context for signalling a [PluginError] on failure.
     * @param log The full dive log.
     * @return One map per sample, ordered to match [DiveLog.samples].
     */
    fun Raise<PluginError>.computeRows(log: DiveLog): List<Map<String, String>>
}
