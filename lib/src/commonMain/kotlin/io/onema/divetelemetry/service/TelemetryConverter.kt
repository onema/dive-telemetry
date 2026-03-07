package io.onema.divetelemetry.service

import io.onema.divetelemetry.domain.DiveLog
import io.onema.divetelemetry.domain.TelemetryOutput

/** Converts a typed [DiveLog] into the string-based [TelemetryOutput] required by Telemetry . */
sealed interface TelemetryConverter {
    /**
     * Produces the 36-column core output, applying unit conversion and value formatting.
     *
     * @param log The typed dive log to convert.
     * @return The formatted [TelemetryOutput].
     */
    fun convert(log: DiveLog): TelemetryOutput

    companion object {
        fun create(): TelemetryConverter = UnifiedTelemetryConverter()
    }
}
